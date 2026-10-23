package com.example.gimbalcommander.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Instrumentation
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.gimbalcommander.data.ble.GimbalBleManager
import com.example.gimbalcommander.data.ble.SonyRemoteEmulator
import com.example.gimbalcommander.model.CameraAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GimbalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GimbalAccessibility"

        var isServiceRunning: Boolean = false
            private set

        // 設定可能な座標・ジェスチャーパラメータ（デフォルト値）
        var shutterXRatio: Float = 0.50f
        var shutterYRatio: Float = 0.85f

        var focusXRatio: Float = 0.50f
        var focusYRatio: Float = 0.50f

        var switchCameraXRatio: Float = 0.85f
        var switchCameraYRatio: Float = 0.82f

        var zoomCenterXRatio: Float = 0.50f
        var zoomCenterYRatio: Float = 0.50f
        var pinchDistancePx: Float = 250f
        var pinchDurationMs: Long = 800L
        var pinchStartOffsetPx: Float = 80f

        // BLE HexValue -> CameraAction マッピング保存用
        var actionMappings: Map<String, CameraAction> = mapOf(
            "00-00-30" to CameraAction.TAP_SHUTTER
        )

        fun updateGestureConfig(
            sXRatio: Float = shutterXRatio,
            sYRatio: Float = shutterYRatio,
            fXRatio: Float = focusXRatio,
            fYRatio: Float = focusYRatio,
            swXRatio: Float = switchCameraXRatio,
            swYRatio: Float = switchCameraYRatio,
            zXRatio: Float = zoomCenterXRatio,
            zYRatio: Float = zoomCenterYRatio,
            distance: Float = pinchDistancePx,
            durationMs: Long = pinchDurationMs,
            startOffsetPx: Float = pinchStartOffsetPx
        ) {
            shutterXRatio = sXRatio
            shutterYRatio = sYRatio
            focusXRatio = fXRatio
            focusYRatio = fYRatio
            switchCameraXRatio = swXRatio
            switchCameraYRatio = swYRatio
            zoomCenterXRatio = zXRatio
            zoomCenterYRatio = zYRatio
            pinchDistancePx = distance
            pinchDurationMs = durationMs
            pinchStartOffsetPx = startOffsetPx
        }

        fun updateActionMappings(mappings: Map<String, CameraAction>) {
            actionMappings = mappings
        }

        /**
         * OS設定でこのユーザー補助サービスが有効化されているかを判定
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${GimbalAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bleManager: GimbalBleManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "GimbalAccessibilityService connected")

        // バックグラウンドでのBLE自動接続を開始
        bleManager = GimbalBleManager.getInstance(this).apply {
            startAutoConnect()
        }

        serviceScope.launch {
            bleManager?.bleDataFlow?.collectLatest { bleData ->
                Log.d(TAG, "Background BLE Data received: ${bleData.hexValue}")
                val mappedAction = actionMappings[bleData.hexValue] ?: CameraAction.NONE
                if (mappedAction != CameraAction.NONE) {
                    executeCameraControl(mappedAction)
                }
            }
        }
    }

    /**
     * リモコンのアクションに応じたカメラ制御
     */
    fun executeCameraControl(action: CameraAction) {
        Log.d(TAG, "Executing camera control for action: ${action.name}")

        // 自アプリが最前面で動作中の場合はタップ誤暴発を防ぐためジェスチャー実行をスキップ
        val activePackage = rootInActiveWindow?.packageName?.toString()
        if (activePackage == packageName) {
            Log.d(TAG, "Active window is own app ($packageName). Skipping gesture to prevent misfire.")
            return
        }

        when (action) {
            CameraAction.SONY_NATIVE_SHUTTER -> SonyRemoteEmulator.getInstance(this).sendSonyShutterCommand()
            CameraAction.SONY_NATIVE_ZOOM_IN -> SonyRemoteEmulator.getInstance(this).sendSonyZoomInCommand()
            CameraAction.SONY_NATIVE_ZOOM_OUT -> SonyRemoteEmulator.getInstance(this).sendSonyZoomOutCommand()
            CameraAction.TAP_SHUTTER -> dispatchScreenTap(shutterXRatio, shutterYRatio)
            CameraAction.FOCUS_AUTO -> dispatchScreenTap(focusXRatio, focusYRatio)
            CameraAction.SWITCH_CAMERA -> dispatchScreenTap(switchCameraXRatio, switchCameraYRatio)
            CameraAction.PINCH_ZOOM_IN -> dispatchPinchZoom(isZoomIn = true)
            CameraAction.PINCH_ZOOM_OUT -> dispatchPinchZoom(isZoomIn = false)
            CameraAction.VOLUME_DOWN -> emulateKeyCode(KeyEvent.KEYCODE_VOLUME_DOWN)
            CameraAction.VOLUME_UP -> emulateKeyCode(KeyEvent.KEYCODE_VOLUME_UP)
            CameraAction.CAMERA_KEY -> emulateKeyCode(KeyEvent.KEYCODE_CAMERA)
            CameraAction.SHUTTER -> dispatchScreenTap(shutterXRatio, shutterYRatio)
            CameraAction.ZOOM_IN -> dispatchPinchZoom(isZoomIn = true)
            CameraAction.ZOOM_OUT -> dispatchPinchZoom(isZoomIn = false)
            else -> {
                Log.d(TAG, "Action $action unhandled in accessibility service.")
            }
        }
    }

    /**
     * 指定座標 (X %, Y %) への単一画面タップジェスチャー
     */
    fun dispatchScreenTap(xRatio: Float, yRatio: Float) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val tapX = width * xRatio
        val tapY = height * yRatio

        val path = Path().apply {
            moveTo(tapX, tapY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Screen tap completed at ($tapX, $tapY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Screen tap cancelled")
            }
        }, null)

        Log.d(TAG, "Dispatched screen tap gesture result: $dispatched at ($tapX, $tapY)")
    }

    /**
     * マルチタッチピンチズームジェスチャー (2本指のゆっくりとしたピンチイン/ピンチアウト)
     */
    fun dispatchPinchZoom(isZoomIn: Boolean) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val cx = width * zoomCenterXRatio
        val cy = height * zoomCenterYRatio
        val dist = pinchDistancePx
        val startOffset = pinchStartOffsetPx

        // 中心付近に触れてタップ判定されないよう startOffset 分だけ離して開始
        val startDist = if (isZoomIn) startOffset else (startOffset + dist)
        val endDist = if (isZoomIn) (startOffset + dist) else startOffset

        // 指1: 中心から左上方向
        val path1 = Path().apply {
            moveTo(cx - startDist, cy - startDist)
            lineTo(cx - endDist, cy - endDist)
        }

        // 指2: 中心から右下方向
        val path2 = Path().apply {
            moveTo(cx + startDist, cy + startDist)
            lineTo(cx + endDist, cy + endDist)
        }

        val duration = pinchDurationMs

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Pinch zoom ${if (isZoomIn) "IN" else "OUT"} completed (${duration}ms) around ($cx, $cy)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Pinch zoom cancelled")
            }
        }, null)

        Log.d(TAG, "Dispatched pinch zoom result: $dispatched (${duration}ms) around ($cx, $cy)")
    }

    /**
     * キーエミュレーション送信（補助機能）
     */
    private fun emulateKeyCode(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val inst = Instrumentation()
                inst.sendKeyDownUpSync(keyCode)
            } catch (e: Exception) {
                Log.w(TAG, "Instrumentation failed for keyCode $keyCode", e)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "Captured KeyEvent in background: " +
                "keyCode=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}), " +
                "action=${event.action}, device=${event.device?.name}")

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d(TAG, "GimbalAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        bleManager?.userDisconnect()
        serviceScope.cancel()
        Log.d(TAG, "GimbalAccessibilityService destroyed")
    }
}
