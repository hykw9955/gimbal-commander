package com.example.gimbalcommander.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CapturedInputEvent {
    abstract val timestamp: Long
    abstract val formattedTime: String

    data class Key(
        val keyCode: Int,
        val keyCodeName: String,
        val scanCode: Int,
        val action: String, // "DOWN" or "UP"
        val deviceName: String,
        val deviceId: Int,
        val repeatCount: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CapturedInputEvent() {
        override val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    data class Motion(
        val deviceName: String,
        val deviceId: Int,
        val axisValues: Map<String, Float>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CapturedInputEvent() {
        override val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    data class BleData(
        val hexValue: String,
        val bytes: ByteArray,
        val deviceName: String,
        val characteristicUuid: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CapturedInputEvent() {
        override val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BleData
            if (hexValue != other.hexValue) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (deviceName != other.deviceName) return false
            if (characteristicUuid != other.characteristicUuid) return false
            if (timestamp != other.timestamp) return false
            return true
        }

        override fun hashCode(): Int {
            var result = hexValue.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + deviceName.hashCode()
            result = 31 * result + characteristicUuid.hashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
}

enum class CameraAction(val displayName: String) {
    SONY_NATIVE_SHUTTER("Sony純正シャッター (RMT-P1BT)"),
    SONY_NATIVE_ZOOM_IN("Sony純正ズームイン (+)"),
    SONY_NATIVE_ZOOM_OUT("Sony純正ズームアウト (-)"),
    TAP_SHUTTER("シャッター (画面タップ)"),
    PINCH_ZOOM_IN("ズームイン (ピンチアウト)"),
    PINCH_ZOOM_OUT("ズームアウト (ピンチイン)"),
    VOLUME_DOWN("音量キー- (KEYCODE_VOLUME_DOWN)"),
    VOLUME_UP("音量キー+ (KEYCODE_VOLUME_UP)"),
    CAMERA_KEY("カメラキー (KEYCODE_CAMERA)"),
    SHUTTER("シャッター (自動判別/CameraX)"),
    ZOOM_IN("ズームイン (CameraX)"),
    ZOOM_OUT("ズームアウト (CameraX)"),
    FOCUS_AUTO("オートフォーカス"),
    SWITCH_CAMERA("カメラ切替 (前面/背面)"),
    NONE("未割り当て")
}
