package com.example.gimbalcommander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedInputEventTest {

    @Test
    fun testBleDataCreationAndFormatting() {
        val bytes = byteArrayOf(0x00, 0x00, 0x30)
        val bleData = CapturedInputEvent.BleData(
            hexValue = "00-00-30",
            bytes = bytes,
            deviceName = "KMRC",
            characteristicUuid = "c6470002-b82b-4dfb-b0e4-964a62b0e1d6"
        )

        assertEquals("00-00-30", bleData.hexValue)
        assertEquals("KMRC", bleData.deviceName)
        assertEquals("c6470002-b82b-4dfb-b0e4-964a62b0e1d6", bleData.characteristicUuid)
        assertNotNull(bleData.formattedTime)
        assertTrue(bleData.formattedTime.isNotEmpty())
    }

    @Test
    fun testBleDataEqualsAndHashCode() {
        val bytes1 = byteArrayOf(0x00, 0x00, 0x30)
        val bytes2 = byteArrayOf(0x00, 0x00, 0x30)
        val timestamp = 1000L

        val data1 = CapturedInputEvent.BleData("00-00-30", bytes1, "KMRC", "uuid-1", timestamp)
        val data2 = CapturedInputEvent.BleData("00-00-30", bytes2, "KMRC", "uuid-1", timestamp)
        val data3 = CapturedInputEvent.BleData("00-00-31", bytes1, "KMRC", "uuid-1", timestamp)

        assertEquals(data1, data2)
        assertEquals(data1.hashCode(), data2.hashCode())
        assertNotEquals(data1, data3)
    }

    @Test
    fun testKeyDataCreation() {
        val keyEvent = CapturedInputEvent.Key(
            keyCode = 24,
            keyCodeName = "KEYCODE_VOLUME_UP",
            scanCode = 115,
            action = "DOWN",
            deviceName = "Gimbal Remote",
            deviceId = 1,
            repeatCount = 0
        )

        assertEquals(24, keyEvent.keyCode)
        assertEquals("KEYCODE_VOLUME_UP", keyEvent.keyCodeName)
        assertEquals("DOWN", keyEvent.action)
        assertEquals("Gimbal Remote", keyEvent.deviceName)
        assertNotNull(keyEvent.formattedTime)
    }

    @Test
    fun testMotionDataCreation() {
        val axisValues = mapOf("AXIS_X" to 0.75f, "AXIS_Y" to -0.5f)
        val motionEvent = CapturedInputEvent.Motion(
            deviceName = "Joystick",
            deviceId = 2,
            axisValues = axisValues
        )

        assertEquals("Joystick", motionEvent.deviceName)
        assertEquals(2, motionEvent.axisValues.size)
        assertEquals(0.75f, motionEvent.axisValues["AXIS_X"])
        assertNotNull(motionEvent.formattedTime)
    }

    @Test
    fun testCameraActionValues() {
        assertEquals("Sony純正シャッター (RMT-P1BT)", CameraAction.SONY_NATIVE_SHUTTER.displayName)
        assertEquals("Sony純正ズームイン (+)", CameraAction.SONY_NATIVE_ZOOM_IN.displayName)
        assertEquals("シャッター (画面タップ)", CameraAction.TAP_SHUTTER.displayName)
        assertEquals("ズームイン (ピンチアウト)", CameraAction.PINCH_ZOOM_IN.displayName)
    }
}
