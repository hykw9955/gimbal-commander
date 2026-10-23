package com.example.gimbalcommander.ui.screens

import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gimbalcommander.camera.CameraXManager
import com.example.gimbalcommander.model.CameraAction
import com.example.gimbalcommander.viewmodel.KeyCaptureViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CameraScreen(
    viewModel: KeyCaptureViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraXManager(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var message by remember { mutableStateOf("カメラ準備完了") }

    // リモコンからのキー割り当てアクションを購読して実行
    LaunchedEffect(Unit) {
        viewModel.triggeredAction.collectLatest { action ->
            when (action) {
                CameraAction.SHUTTER,
                CameraAction.VOLUME_DOWN,
                CameraAction.VOLUME_UP,
                CameraAction.CAMERA_KEY,
                CameraAction.TAP_SHUTTER -> {
                    message = "撮影中..."
                    cameraManager.takePicture(
                        onSuccess = { file ->
                            message = "保存成功: ${file.name}"
                            Toast.makeText(context, "撮影しました: ${file.name}", Toast.LENGTH_SHORT).show()
                        },
                        onError = { exc ->
                            message = "エラー: ${exc.message}"
                        }
                    )
                }
                CameraAction.ZOOM_IN, CameraAction.PINCH_ZOOM_IN -> {
                    cameraManager.zoomIn(0.2f)
                    message = "ズームイン (+)"
                }
                CameraAction.ZOOM_OUT, CameraAction.PINCH_ZOOM_OUT -> {
                    cameraManager.zoomOut(0.2f)
                    message = "ズームアウト (-)"
                }
                CameraAction.FOCUS_AUTO -> {
                    cameraManager.triggerAutoFocus()
                    message = "オートフォーカス実行"
                }
                CameraAction.SWITCH_CAMERA -> {
                    previewView?.let { cameraManager.switchCamera(lifecycleOwner, it) }
                    message = "カメラ切り替え"
                }
                CameraAction.NONE -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // CameraXプレビュー表示
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also {
                    previewView = it
                    cameraManager.startCamera(lifecycleOwner, it)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ステータスメッセージ表示
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape
        ) {
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 画面上での手動コントロール（ボタンUI）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { cameraManager.zoomOut(0.2f) },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
            }

            // シャッターボタン
            Button(
                onClick = {
                    cameraManager.takePicture(
                        onSuccess = { file ->
                            Toast.makeText(context, "撮影しました: ${file.name}", Toast.LENGTH_SHORT).show()
                        },
                        onError = { exc ->
                            Toast.makeText(context, "撮影失敗: ${exc.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Shutter", tint = Color.Black, modifier = Modifier.size(36.dp))
            }

            IconButton(
                onClick = { cameraManager.zoomIn(0.2f) },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
            }

            IconButton(
                onClick = {
                    previewView?.let { cameraManager.switchCamera(lifecycleOwner, it) }
                },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
            }
        }
    }
}

