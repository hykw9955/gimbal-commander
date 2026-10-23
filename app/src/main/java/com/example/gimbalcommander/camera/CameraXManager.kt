package com.example.gimbalcommander.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXManager(private val context: Context) {

    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onInitialized: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraControl = camera?.cameraControl
                cameraInfo = camera?.cameraInfo
                onInitialized()
            } catch (exc: Exception) {
                Log.e("CameraXManager", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(onSuccess: (File) -> Unit, onError: (ImageCaptureException) -> Unit) {
        val capture = imageCapture ?: return

        val photoFile = File(
            context.cacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onSuccess(photoFile)
                }

                override fun onError(exc: ImageCaptureException) {
                    onError(exc)
                }
            }
        )
    }

    fun zoomIn(step: Float = 0.1f) {
        val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        val maxZoomRatio = cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
        val newZoom = (currentZoomRatio + step).coerceAtMost(maxZoomRatio)
        cameraControl?.setZoomRatio(newZoom)
    }

    fun zoomOut(step: Float = 0.1f) {
        val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        val minZoomRatio = cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
        val newZoom = (currentZoomRatio - step).coerceAtLeast(minZoomRatio)
        cameraControl?.setZoomRatio(newZoom)
    }

    fun triggerAutoFocus() {
        val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val centerPoint = factory.createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(centerPoint).build()
        cameraControl?.startFocusAndMetering(action)
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera(lifecycleOwner, previewView)
    }

    fun release() {
        cameraExecutor.shutdown()
    }
}

