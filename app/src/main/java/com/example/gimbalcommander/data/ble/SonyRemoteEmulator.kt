package com.example.gimbalcommander.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission", "StaticFieldLeak")
class SonyRemoteEmulator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SonyRemoteEmulator"

        @Volatile
        private var instance: SonyRemoteEmulator? = null

        fun getInstance(context: Context): SonyRemoteEmulator {
            return instance ?: synchronized(this) {
                instance ?: SonyRemoteEmulator(context.applicationContext).also { instance = it }
            }
        }

        // Sony純正 RMT-P1BT / GP-VPT2BT リモコンのUUID情報
        val SONY_SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
        val SONY_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Sony ベンダーID
        private const val SONY_MANUFACTURER_ID = 0x012D

        // Sony カメラコマンド
        val SONY_CMD_SHUTTER_PRESS = byteArrayOf(0x01, 0x02) // Focus & Shutter Down
        val SONY_CMD_SHUTTER_RELEASE = byteArrayOf(0x00, 0x00) // Shutter Up
        val SONY_CMD_ZOOM_IN = byteArrayOf(0x03, 0x01)
        val SONY_CMD_ZOOM_OUT = byteArrayOf(0x03, 0x02)
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var sonyCharacteristic: BluetoothGattCharacteristic? = null

    // 変更前のデバイス名の保持用
    private var originalDeviceName: String? = null

    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Sony BLE Advertising started successfully as RMT-P1BT.")
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Sony BLE Advertising failed with error code: $errorCode")
            _isAdvertising.value = false
        }
    }

    /**
     * Sony RMT-P1BT リモコンの偽装アドバタイズおよびGATTサーバーを開始
     */
    fun startEmulation() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter disabled.")
            return
        }

        if (_isAdvertising.value) return

        // 1. GATT サーバーの起動
        setupGattServer()

        // 2. 元のBluetooth名を保存して RMT-P1BT に変更
        try {
            if (originalDeviceName == null && bluetoothAdapter.name != "RMT-P1BT") {
                originalDeviceName = bluetoothAdapter.name
                Log.d(TAG, "Saved original Bluetooth device name: $originalDeviceName")
            }
            bluetoothAdapter.name = "RMT-P1BT"
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not set adapter name to RMT-P1BT", e)
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser is null.")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SONY_SERVICE_UUID))
            .addManufacturerData(SONY_MANUFACTURER_ID, byteArrayOf(0x01, 0x00, 0x00, 0x00))
            .build()

        try {
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting advertising", e)
        }
    }

    /**
     * エミュレーションの停止（元のデバイス名を自動復元）
     */
    fun stopEmulation() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException stopping advertising", e)
        }
        _isAdvertising.value = false

        // 元のデバイス名を復元
        if (originalDeviceName != null) {
            try {
                bluetoothAdapter?.name = originalDeviceName
                Log.d(TAG, "Restored original Bluetooth device name: $originalDeviceName")
                originalDeviceName = null
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not restore original Bluetooth device name", e)
            }
        }

        try {
            bluetoothGattServer?.close()
            bluetoothGattServer = null
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException closing gattServer", e)
        }

        registeredDevices.clear()
        _connectedDeviceCount.value = 0
    }

    private fun setupGattServer() {
        if (bluetoothGattServer != null) return

        try {
            bluetoothGattServer = bluetoothManager?.openGattServer(context, gattServerCallback)

            val service = BluetoothGattService(
                SONY_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val characteristic = BluetoothGattCharacteristic(
                SONY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(cccd)

            service.addCharacteristic(characteristic)
            bluetoothGattServer?.addService(service)

            sonyCharacteristic = characteristic
            Log.d(TAG, "Sony GATT Server setup completed.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException setupGattServer", e)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "GattServer onConnectionStateChange: ${device.address}, newState: $newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                registeredDevices.add(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                registeredDevices.remove(device)
            }
            _connectedDeviceCount.value = registeredDevices.size
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "Descriptor write request from ${device.address}")
            if (responseNeeded) {
                try {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException sendResponse", e)
                }
            }
        }
    }

    /**
     * Sony純正シャッターコマンド（Focus ➔ Shutter ➔ Release）を送信
     */
    fun sendSonyShutterCommand() {
        sendSonyCommand(SONY_CMD_SHUTTER_PRESS)
        mainHandler.postDelayed({
            sendSonyCommand(SONY_CMD_SHUTTER_RELEASE)
        }, 150)
    }

    fun sendSonyZoomInCommand() {
        sendSonyCommand(SONY_CMD_ZOOM_IN)
    }

    fun sendSonyZoomOutCommand() {
        sendSonyCommand(SONY_CMD_ZOOM_OUT)
    }

    private fun sendSonyCommand(commandBytes: ByteArray) {
        val server = bluetoothGattServer ?: return
        val characteristic = sonyCharacteristic ?: return

        for (device in registeredDevices) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    server.notifyCharacteristicChanged(device, characteristic, false, commandBytes)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = commandBytes
                    @Suppress("DEPRECATION")
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
                Log.d(TAG, "Sent Sony Native Command ${commandBytes.joinToString("-") { "%02X".format(it) }} to ${device.address}")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException notifyCharacteristicChanged", e)
            }
        }
    }
}
