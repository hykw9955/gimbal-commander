package com.example.gimbalcommander.viewmodel

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gimbalcommander.data.ble.BleConnectionState
import com.example.gimbalcommander.data.ble.GimbalBleManager
import com.example.gimbalcommander.model.CameraAction
import com.example.gimbalcommander.model.CapturedInputEvent
import com.example.gimbalcommander.service.GimbalAccessibilityService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeyCaptureViewModel : ViewModel() {

    private var bleManager: GimbalBleManager? = null

    // BLE接続状態
    private val _bleConnectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val bleConnectionState: StateFlow<BleConnectionState> = _bleConnectionState.asStateFlow()

    private val _connectedBleDeviceName = MutableStateFlow<String?>(null)
    val connectedBleDeviceName: StateFlow<String?> = _connectedBleDeviceName.asStateFlow()

    // 直近のBLE入力
    private val _latestBleData = MutableStateFlow<CapturedInputEvent.BleData?>(null)
    val latestBleData: StateFlow<CapturedInputEvent.BleData?> = _latestBleData.asStateFlow()

    // BLEデータマッピング (hexValue -> CameraAction)
    private val _bleMappings = MutableStateFlow<Map<String, CameraAction>>(
        mapOf(
            "00-00-30" to CameraAction.TAP_SHUTTER // デフォルトサンプル割り当て (画面タップシャッター)
        )
    )
    val bleMappings: StateFlow<Map<String, CameraAction>> = _bleMappings.asStateFlow()

    // 画面ジェスチャー座標設定
    private val _shutterXRatio = MutableStateFlow(0.50f)
    val shutterXRatio: StateFlow<Float> = _shutterXRatio.asStateFlow()

    private val _shutterYRatio = MutableStateFlow(0.85f)
    val shutterYRatio: StateFlow<Float> = _shutterYRatio.asStateFlow()

    private val _focusXRatio = MutableStateFlow(0.50f)
    val focusXRatio: StateFlow<Float> = _focusXRatio.asStateFlow()

    private val _focusYRatio = MutableStateFlow(0.50f)
    val focusYRatio: StateFlow<Float> = _focusYRatio.asStateFlow()

    private val _switchCameraXRatio = MutableStateFlow(0.85f)
    val switchCameraXRatio: StateFlow<Float> = _switchCameraXRatio.asStateFlow()

    private val _switchCameraYRatio = MutableStateFlow(0.82f)
    val switchCameraYRatio: StateFlow<Float> = _switchCameraYRatio.asStateFlow()

    private val _zoomCenterXRatio = MutableStateFlow(0.50f)
    val zoomCenterXRatio: StateFlow<Float> = _zoomCenterXRatio.asStateFlow()

    private val _zoomCenterYRatio = MutableStateFlow(0.50f)
    val zoomCenterYRatio: StateFlow<Float> = _zoomCenterYRatio.asStateFlow()

    private val _pinchDistancePx = MutableStateFlow(250f)
    val pinchDistancePx: StateFlow<Float> = _pinchDistancePx.asStateFlow()

    private val _pinchDurationMs = MutableStateFlow(800L)
    val pinchDurationMs: StateFlow<Long> = _pinchDurationMs.asStateFlow()

    // イベント履歴（最新が先頭）
    private val _eventLogs = MutableStateFlow<List<CapturedInputEvent>>(emptyList())
    val eventLogs: StateFlow<List<CapturedInputEvent>> = _eventLogs.asStateFlow()

    // 最後に押されたキー
    private val _latestKey = MutableStateFlow<CapturedInputEvent.Key?>(null)
    val latestKey: StateFlow<CapturedInputEvent.Key?> = _latestKey.asStateFlow()

    // キーマッピング (keyCode -> CameraAction)
    private val _keyMappings = MutableStateFlow<Map<Int, CameraAction>>(
        mapOf(
            KeyEvent.KEYCODE_VOLUME_UP to CameraAction.TAP_SHUTTER,
            KeyEvent.KEYCODE_VOLUME_DOWN to CameraAction.TAP_SHUTTER,
            KeyEvent.KEYCODE_CAMERA to CameraAction.TAP_SHUTTER
        )
    )
    val keyMappings: StateFlow<Map<Int, CameraAction>> = _keyMappings.asStateFlow()

    // カメラアクション発火イベント
    private val _triggeredAction = MutableSharedFlow<CameraAction>(extraBufferCapacity = 16)
    val triggeredAction: SharedFlow<CameraAction> = _triggeredAction.asSharedFlow()

    fun updateGestureConfig(
        sXRatio: Float = _shutterXRatio.value,
        sYRatio: Float = _shutterYRatio.value,
        fXRatio: Float = _focusXRatio.value,
        fYRatio: Float = _focusYRatio.value,
        swXRatio: Float = _switchCameraXRatio.value,
        swYRatio: Float = _switchCameraYRatio.value,
        zXRatio: Float = _zoomCenterXRatio.value,
        zYRatio: Float = _zoomCenterYRatio.value,
        distance: Float = _pinchDistancePx.value,
        durationMs: Long = _pinchDurationMs.value
    ) {
        _shutterXRatio.value = sXRatio
        _shutterYRatio.value = sYRatio
        _focusXRatio.value = fXRatio
        _focusYRatio.value = fYRatio
        _switchCameraXRatio.value = swXRatio
        _switchCameraYRatio.value = swYRatio
        _zoomCenterXRatio.value = zXRatio
        _zoomCenterYRatio.value = zYRatio
        _pinchDistancePx.value = distance
        _pinchDurationMs.value = durationMs

        GimbalAccessibilityService.updateGestureConfig(
            sXRatio = sXRatio,
            sYRatio = sYRatio,
            fXRatio = fXRatio,
            fYRatio = fYRatio,
            swXRatio = swXRatio,
            swYRatio = swYRatio,
            zXRatio = zXRatio,
            zYRatio = zYRatio,
            distance = distance,
            durationMs = durationMs
        )
    }

    fun initBleManager(context: Context) {
        if (bleManager != null) return
        val manager = GimbalBleManager.getInstance(context.applicationContext)
        bleManager = manager

        viewModelScope.launch {
            manager.connectionState.collect { state ->
                _bleConnectionState.value = state
            }
        }

        viewModelScope.launch {
            manager.connectedDeviceName.collect { name ->
                _connectedBleDeviceName.value = name
            }
        }

        viewModelScope.launch {
            manager.bleDataFlow.collect { bleData ->
                onBleDataReceived(bleData)
            }
        }

        // 初期化と同時に自動スキャン・接続をスタート
        manager.startAutoConnect()
    }

    fun toggleBleConnection() {
        val manager = bleManager ?: return
        when (_bleConnectionState.value) {
            BleConnectionState.DISCONNECTED -> manager.startAutoConnect()
            BleConnectionState.SCANNING, BleConnectionState.CONNECTING, BleConnectionState.CONNECTED -> manager.userDisconnect()
        }
    }

    fun onBleDataReceived(bleData: CapturedInputEvent.BleData) {
        _latestBleData.value = bleData
        _eventLogs.value = (listOf(bleData) + _eventLogs.value).take(100)

        val mappedAction = _bleMappings.value[bleData.hexValue]
        if (mappedAction != null && mappedAction != CameraAction.NONE) {
            viewModelScope.launch {
                _triggeredAction.emit(mappedAction)
            }
        }
    }

    fun assignActionToBleData(hexValue: String, action: CameraAction) {
        val updatedMap = _bleMappings.value.toMutableMap().apply {
            put(hexValue, action)
        }
        _bleMappings.value = updatedMap
        GimbalAccessibilityService.updateActionMappings(updatedMap)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val actionStr = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "OTHER"
        }
        val keyEvent = CapturedInputEvent.Key(
            keyCode = event.keyCode,
            keyCodeName = KeyEvent.keyCodeToString(event.keyCode),
            scanCode = event.scanCode,
            action = actionStr,
            deviceName = event.device?.name ?: "Unknown Device",
            deviceId = event.deviceId,
            repeatCount = event.repeatCount
        )

        _latestKey.value = keyEvent
        _eventLogs.value = (listOf(keyEvent) + _eventLogs.value).take(100)

        // DOWN時のみアクションをトリガー
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val mappedAction = _keyMappings.value[event.keyCode]
            if (mappedAction != null && mappedAction != CameraAction.NONE) {
                viewModelScope.launch {
                    _triggeredAction.emit(mappedAction)
                }
                return true // 処理済みとして消費
            }
        }
        return false
    }

    fun onMotionEvent(event: MotionEvent) {
        val axisMap = mutableMapOf<String, Float>()
        val axes = listOf(
            MotionEvent.AXIS_X to "AXIS_X",
            MotionEvent.AXIS_Y to "AXIS_Y",
            MotionEvent.AXIS_Z to "AXIS_Z",
            MotionEvent.AXIS_RZ to "AXIS_RZ",
            MotionEvent.AXIS_HAT_X to "HAT_X",
            MotionEvent.AXIS_HAT_Y to "HAT_Y",
            MotionEvent.AXIS_WHEEL to "WHEEL"
        )
        for ((axis, name) in axes) {
            val value = event.getAxisValue(axis)
            if (Math.abs(value) > 0.05f) {
                axisMap[name] = value
            }
        }

        if (axisMap.isNotEmpty()) {
            val motionEvent = CapturedInputEvent.Motion(
                deviceName = event.device?.name ?: "Unknown Device",
                deviceId = event.deviceId,
                axisValues = axisMap
            )
            _eventLogs.value = (listOf(motionEvent) + _eventLogs.value).take(100)
        }
    }

    fun assignActionToKey(keyCode: Int, action: CameraAction) {
        _keyMappings.value = _keyMappings.value.toMutableMap().apply {
            put(keyCode, action)
        }
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        _latestKey.value = null
        _latestBleData.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bleManager?.userDisconnect()
    }
}
