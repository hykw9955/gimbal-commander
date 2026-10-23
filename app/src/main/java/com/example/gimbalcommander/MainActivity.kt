package com.example.gimbalcommander

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.gimbalcommander.ui.screens.CameraScreen
import com.example.gimbalcommander.ui.screens.KeyTesterScreen
import com.example.gimbalcommander.ui.theme.GimbalCommanderTheme
import com.example.gimbalcommander.viewmodel.KeyCaptureViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: KeyCaptureViewModel by viewModels()

    private var hasCameraPermission by mutableStateOf(false)

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BLEマネージャーの初期化
        viewModel.initBleManager(this)

        // 必要なパーミッションリストの構築
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            hasCameraPermission = true
        } else {
            requestMultiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            GimbalCommanderTheme {
                MainAppScaffold(
                    viewModel = viewModel,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = {
                        requestMultiplePermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    /**
     * Bluetoothリモコン等のキーイベント（KeyEvent）を最前面でキャッチ
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewModel.onKeyEvent(event)
        if (handled) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * ジンバルのジョイスティックやダイヤル・スライダー等のアナログ入力（MotionEvent）をキャッチ
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        viewModel.onMotionEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: KeyCaptureViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "Key Tester (解析)" else "Camera (操作)") },
                actions = {
                    IconButton(onClick = onOpenAccessibilitySettings) {
                        Icon(Icons.Default.Settings, contentDescription = "ユーザー補助設定")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Keyboard, contentDescription = "キー解析") },
                    label = { Text("キー解析") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "カメラ") },
                    label = { Text("カメラ") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> KeyTesterScreen(viewModel = viewModel)
                1 -> {
                    if (hasCameraPermission) {
                        CameraScreen(viewModel = viewModel)
                    } else {
                        Button(onClick = onRequestCameraPermission) {
                            Text("カメラのパーミッションを許可してください")
                        }
                    }
                }
            }
        }
    }
}
