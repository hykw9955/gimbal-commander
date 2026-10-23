package com.example.gimbalcommander.viewmodel

import com.example.gimbalcommander.model.CameraAction
import com.example.gimbalcommander.model.CapturedInputEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(manifest=org.robolectric.annotation.Config.NONE)
class KeyCaptureViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: KeyCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        viewModel = KeyCaptureViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testBleDataReceivedUpdatesLatestAndLogs() {
        val bleData = CapturedInputEvent.BleData(
            hexValue = "00-00-30",
            bytes = byteArrayOf(0x00, 0x00, 0x30),
            deviceName = "KMRC",
            characteristicUuid = "uuid-test"
        )

        viewModel.onBleDataReceived(bleData)

        assertEquals(bleData, viewModel.latestBleData.value)
        assertEquals(1, viewModel.eventLogs.value.size)
        assertEquals(bleData, viewModel.eventLogs.value.first())
    }

    @Test
    fun testAssignActionToBleDataAndTrigger() = runTest {
        val bleData = CapturedInputEvent.BleData(
            hexValue = "00-00-FF",
            bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte()),
            deviceName = "KMRC",
            characteristicUuid = "uuid-test"
        )

        // アクション割り当て (ピンチズームイン)
        viewModel.assignActionToBleData("00-00-FF", CameraAction.PINCH_ZOOM_IN)
        assertEquals(CameraAction.PINCH_ZOOM_IN, viewModel.bleMappings.value["00-00-FF"])

        var triggered: CameraAction? = null
        val job = launch(testDispatcher) {
            triggered = viewModel.triggeredAction.first()
        }

        viewModel.onBleDataReceived(bleData)

        assertEquals(CameraAction.PINCH_ZOOM_IN, triggered)
        job.cancel()
    }

    @Test
    fun testUpdateGestureConfig() {
        viewModel.updateGestureConfig(
            sXRatio = 0.40f,
            sYRatio = 0.90f,
            zXRatio = 0.50f,
            zYRatio = 0.50f,
            distance = 300f
        )

        assertEquals(0.40f, viewModel.shutterXRatio.value)
        assertEquals(0.90f, viewModel.shutterYRatio.value)
        assertEquals(300f, viewModel.pinchDistancePx.value)
    }

    @Test
    fun testClearLogsResetsState() {
        val bleData = CapturedInputEvent.BleData(
            hexValue = "00-00-30",
            bytes = byteArrayOf(0x00, 0x00, 0x30),
            deviceName = "KMRC",
            characteristicUuid = "uuid-test"
        )

        viewModel.onBleDataReceived(bleData)
        assertTrue(viewModel.eventLogs.value.isNotEmpty())

        viewModel.clearLogs()

        assertTrue(viewModel.eventLogs.value.isEmpty())
        assertNull(viewModel.latestBleData.value)
        assertNull(viewModel.latestKey.value)
    }
}
