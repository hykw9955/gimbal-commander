package com.example.gimbalcommander.data

import android.content.Context
import android.content.SharedPreferences
import com.example.gimbalcommander.model.CameraAction

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gimbal_commander_prefs"

        private const val KEY_SHUTTER_X = "shutter_x_ratio"
        private const val KEY_SHUTTER_Y = "shutter_y_ratio"
        private const val KEY_FOCUS_X = "focus_x_ratio"
        private const val KEY_FOCUS_Y = "focus_y_ratio"
        private const val KEY_SWITCH_X = "switch_x_ratio"
        private const val KEY_SWITCH_Y = "switch_y_ratio"
        private const val KEY_ZOOM_CENTER_X = "zoom_center_x_ratio"
        private const val KEY_ZOOM_CENTER_Y = "zoom_center_y_ratio"
        private const val KEY_PINCH_DIST = "pinch_distance_px"
        private const val KEY_PINCH_DURATION = "pinch_duration_ms"

        private const val KEY_ORIGINAL_BT_NAME = "original_bt_name"
        private const val KEY_ACTION_MAPPING_PREFIX = "action_mapping_"

        // 縦画面（Portrait）を基準とした座標の初期値
        const val DEFAULT_SHUTTER_X = 0.50f
        const val DEFAULT_SHUTTER_Y = 0.82f
        const val DEFAULT_FOCUS_X = 0.50f
        const val DEFAULT_FOCUS_Y = 0.40f
        const val DEFAULT_SWITCH_X = 0.85f
        const val DEFAULT_SWITCH_Y = 0.82f
        const val DEFAULT_ZOOM_CENTER_X = 0.50f
        const val DEFAULT_ZOOM_CENTER_Y = 0.40f
        const val DEFAULT_PINCH_DIST = 250f
        const val DEFAULT_PINCH_DURATION = 800L
    }

    var shutterXRatio: Float
        get() = prefs.getFloat(KEY_SHUTTER_X, DEFAULT_SHUTTER_X)
        set(value) = prefs.edit().putFloat(KEY_SHUTTER_X, value).apply()

    var shutterYRatio: Float
        get() = prefs.getFloat(KEY_SHUTTER_Y, DEFAULT_SHUTTER_Y)
        set(value) = prefs.edit().putFloat(KEY_SHUTTER_Y, value).apply()

    var focusXRatio: Float
        get() = prefs.getFloat(KEY_FOCUS_X, DEFAULT_FOCUS_X)
        set(value) = prefs.edit().putFloat(KEY_FOCUS_X, value).apply()

    var focusYRatio: Float
        get() = prefs.getFloat(KEY_FOCUS_Y, DEFAULT_FOCUS_Y)
        set(value) = prefs.edit().putFloat(KEY_FOCUS_Y, value).apply()

    var switchCameraXRatio: Float
        get() = prefs.getFloat(KEY_SWITCH_X, DEFAULT_SWITCH_X)
        set(value) = prefs.edit().putFloat(KEY_SWITCH_X, value).apply()

    var switchCameraYRatio: Float
        get() = prefs.getFloat(KEY_SWITCH_Y, DEFAULT_SWITCH_Y)
        set(value) = prefs.edit().putFloat(KEY_SWITCH_Y, value).apply()

    var zoomCenterXRatio: Float
        get() = prefs.getFloat(KEY_ZOOM_CENTER_X, DEFAULT_ZOOM_CENTER_X)
        set(value) = prefs.edit().putFloat(KEY_ZOOM_CENTER_X, value).apply()

    var zoomCenterYRatio: Float
        get() = prefs.getFloat(KEY_ZOOM_CENTER_Y, DEFAULT_ZOOM_CENTER_Y)
        set(value) = prefs.edit().putFloat(KEY_ZOOM_CENTER_Y, value).apply()

    var pinchDistancePx: Float
        get() = prefs.getFloat(KEY_PINCH_DIST, DEFAULT_PINCH_DIST)
        set(value) = prefs.edit().putFloat(KEY_PINCH_DIST, value).apply()

    var pinchDurationMs: Long
        get() = prefs.getLong(KEY_PINCH_DURATION, DEFAULT_PINCH_DURATION)
        set(value) = prefs.edit().putLong(KEY_PINCH_DURATION, value).apply()

    var originalBluetoothName: String?
        get() = prefs.getString(KEY_ORIGINAL_BT_NAME, null)
        set(value) = prefs.edit().putString(KEY_ORIGINAL_BT_NAME, value).apply()

    fun saveActionMapping(hexValue: String, action: CameraAction) {
        prefs.edit().putString(KEY_ACTION_MAPPING_PREFIX + hexValue, action.name).apply()
    }

    fun getActionMapping(hexValue: String, defaultAction: CameraAction): CameraAction {
        val actionName = prefs.getString(KEY_ACTION_MAPPING_PREFIX + hexValue, null) ?: return defaultAction
        return try {
            CameraAction.valueOf(actionName)
        } catch (e: IllegalArgumentException) {
            defaultAction
        }
    }

    fun getAllActionMappings(): Map<String, CameraAction> {
        val map = mutableMapOf<String, CameraAction>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_ACTION_MAPPING_PREFIX) && value is String) {
                val hexValue = key.removePrefix(KEY_ACTION_MAPPING_PREFIX)
                try {
                    map[hexValue] = CameraAction.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    // Ignore invalid mappings
                }
            }
        }
        return map
    }

    fun resetToDefaults() {
        prefs.edit()
            .putFloat(KEY_SHUTTER_X, DEFAULT_SHUTTER_X)
            .putFloat(KEY_SHUTTER_Y, DEFAULT_SHUTTER_Y)
            .putFloat(KEY_FOCUS_X, DEFAULT_FOCUS_X)
            .putFloat(KEY_FOCUS_Y, DEFAULT_FOCUS_Y)
            .putFloat(KEY_SWITCH_X, DEFAULT_SWITCH_X)
            .putFloat(KEY_SWITCH_Y, DEFAULT_SWITCH_Y)
            .putFloat(KEY_ZOOM_CENTER_X, DEFAULT_ZOOM_CENTER_X)
            .putFloat(KEY_ZOOM_CENTER_Y, DEFAULT_ZOOM_CENTER_Y)
            .putFloat(KEY_PINCH_DIST, DEFAULT_PINCH_DIST)
            .putLong(KEY_PINCH_DURATION, DEFAULT_PINCH_DURATION)
            .apply()
    }
}
