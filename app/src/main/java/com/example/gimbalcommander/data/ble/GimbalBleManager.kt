package com.example.gimbalcommander.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.gimbalcommander.model.CapturedInputEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class BleConnectionState(val label: String) {
    DISCONNECTED("未接続"),
    SCANNING("スキャン中..."),
    CONNECTING("接続中..."),
    CONNECTED("接続完了")
}

@SuppressLint("MissingPermission", "StaticFieldLeak")
class GimbalBleManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "GimbalBleManager"

        @Volatile
        private var instance: GimbalBleManager? = null

        fun getInstance(context: Context): GimbalBleManager {
            return instance ?: synchronized(this) {
                instance ?: GimbalBleManager(context.applicationContext).also { instance = it }
            }
        }

        // KMRCリモコンのUUID
        val SERVICE_UUID: UUID = UUID.fromString("c6470001-b82b-4dfb-b0e4-964a62b0e1d6")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("c6470002-b82b-4dfb-b0e4-964a62b0e1d6")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastConnectedDevice: BluetoothDevice? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _bleDataFlow = MutableSharedFlow<CapturedInputEvent.BleData>(extraBufferCapacity = 64)
    val bleDataFlow: SharedFlow<CapturedInputEvent.BleData> = _bleDataFlow.asSharedFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // ユーザーが明示的に切断したかどうかのフラグ
    private var isUserDisconnected = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName
            Log.d(TAG, "Discovered device: $name (${device.address})")

            val serviceUuids = result.scanRecord?.serviceUuids
            val isTargetDevice = name?.contains("KMRC", ignoreCase = true) == true ||
                    serviceUuids?.any { it.uuid == SERVICE_UUID } == true

            if (isTargetDevice) {
                Log.d(TAG, "Target device found! $name (${device.address})")
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _connectionState.value = BleConnectionState.DISCONNECTED
            isScanning = false
            scheduleAutoReconnect()
        }
    }

    /**
     * 自動再接続モードの起動（アプリ起動時等に呼び出す）
     */
    fun startAutoConnect() {
        isUserDisconnected = false
        if (_connectionState.value == BleConnectionState.CONNECTED ||
            _connectionState.value == BleConnectionState.CONNECTING
        ) {
            return
        }

        if (lastConnectedDevice != null) {
            connectToDevice(lastConnectedDevice!!)
        } else {
            startScan()
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or not supported.")
            scheduleAutoReconnect()
            return
        }

        if (isScanning) return

        _connectionState.value = BleConnectionState.SCANNING
        isScanning = true

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null.")
            _connectionState.value = BleConnectionState.DISCONNECTED
            scheduleAutoReconnect()
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "BLE Scan started (listening for KMRC / ServiceUUID $SERVICE_UUID)")

            mainHandler.postDelayed({
                if (isScanning && _connectionState.value == BleConnectionState.SCANNING) {
                    stopScan()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    scheduleAutoReconnect()
                }
            }, 15000)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during startScan", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
            isScanning = false
            scheduleAutoReconnect()
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "BLE Scan stopped.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during stopScan", e)
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        _connectedDeviceName.value = device.name ?: "KMRC"
        lastConnectedDevice = device

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            Log.d(TAG, "Connecting GATT to ${device.address}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during connectGatt", e)
            _connectionState.value = BleConnectionState.DISCONNECTED
            scheduleAutoReconnect()
        }
    }

    /**
     * ユーザー手動による明示的切断
     */
    fun userDisconnect() {
        isUserDisconnected = true
        mainHandler.removeCallbacksAndMessages(null)
        disconnectInternal()
    }

    private fun disconnectInternal() {
        stopScan()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnectInternal", e)
        }
        _connectionState.value = BleConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
    }

    private fun scheduleAutoReconnect() {
        if (isUserDisconnected) return

        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isUserDisconnected && _connectionState.value == BleConnectionState.DISCONNECTED) {
                Log.d(TAG, "Auto reconnect triggered...")
                startAutoConnect()
            }
        }, 3000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status: $status, newState: $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.")
                _connectionState.value = BleConnectionState.CONNECTING
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException discoverServices", e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.")
                _connectionState.value = BleConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                try {
                    bluetoothGatt?.close()
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException close", e)
                }
                bluetoothGatt = null

                scheduleAutoReconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully.")

                var count = 0
                for (service in gatt.services) {
                    for (char in service.characteristics) {
                        val props = char.properties
                        val isNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        val isIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                        if (isNotify || isIndicate) {
                            enableNotificationOrIndication(gatt, char, isIndicate)
                            count++
                        }
                    }
                }

                if (count > 0) {
                    _connectionState.value = BleConnectionState.CONNECTED
                    Log.d(TAG, "Subscribed to $count notification/indication characteristics.")
                } else {
                    Log.e(TAG, "No notify/indicate characteristics found.")
                }
            } else {
                Log.w(TAG, "onServicesDiscovered failed received: $status")
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: byteArrayOf()
            handleCharacteristicData(bytes, gatt.device.name, characteristic.uuid.toString())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicData(value, gatt.device.name, characteristic.uuid.toString())
        }
    }

    private fun enableNotificationOrIndication(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        isIndicate: Boolean
    ) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                val value = if (isIndicate) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                Log.d(TAG, "Enabled ${if (isIndicate) "Indication" else "Notification"} for ${characteristic.uuid}")
            } else {
                Log.w(TAG, "CCCD Descriptor $CCCD_UUID not found for ${characteristic.uuid}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException enabling notification/indication", e)
        }
    }

    private fun handleCharacteristicData(bytes: ByteArray, deviceName: String?, uuidStr: String) {
        val hexValue = bytes.joinToString("-") { "%02X".format(it) }
        Log.d(TAG, "Received BLE Notification/Indication [$uuidStr]: $hexValue")

        val bleData = CapturedInputEvent.BleData(
            hexValue = hexValue,
            bytes = bytes,
            deviceName = deviceName ?: _connectedDeviceName.value ?: "KMRC",
            characteristicUuid = uuidStr
        )

        _bleDataFlow.tryEmit(bleData)
    }
}
