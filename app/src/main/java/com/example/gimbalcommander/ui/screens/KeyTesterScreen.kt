package com.example.gimbalcommander.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gimbalcommander.data.ble.BleConnectionState
import com.example.gimbalcommander.model.CameraAction
import com.example.gimbalcommander.model.CapturedInputEvent
import com.example.gimbalcommander.service.GimbalAccessibilityService
import com.example.gimbalcommander.viewmodel.KeyCaptureViewModel

@Composable
fun KeyTesterScreen(
    viewModel: KeyCaptureViewModel,
    modifier: Modifier = Modifier
) {
    val bleState by viewModel.bleConnectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedBleDeviceName.collectAsState()
    val latestBleData by viewModel.latestBleData.collectAsState()
    val bleMappings by viewModel.bleMappings.collectAsState()

    val shutterXRatio by viewModel.shutterXRatio.collectAsState()
    val shutterYRatio by viewModel.shutterYRatio.collectAsState()
    val focusXRatio by viewModel.focusXRatio.collectAsState()
    val focusYRatio by viewModel.focusYRatio.collectAsState()
    val switchCameraXRatio by viewModel.switchCameraXRatio.collectAsState()
    val switchCameraYRatio by viewModel.switchCameraYRatio.collectAsState()
    val zoomCenterXRatio by viewModel.zoomCenterXRatio.collectAsState()
    val zoomCenterYRatio by viewModel.zoomCenterYRatio.collectAsState()
    val pinchDistancePx by viewModel.pinchDistancePx.collectAsState()
    val pinchDurationMs by viewModel.pinchDurationMs.collectAsState()

    val latestKey by viewModel.latestKey.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val keyMappings by viewModel.keyMappings.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceEnabled by remember { mutableStateOf(false) }

    // 画面復帰 (ON_RESUME) のたびにアクセシビリティサービスのステータスをリアルタイム更新
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = GimbalAccessibilityService.isAccessibilityServiceEnabled(context) ||
                        GimbalAccessibilityService.isServiceRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // ヘッダー案内
            Text(
                text = "Bluetooth リモコン キーキャプチャ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "リモコンのボタンを押すと、HIDキーイベントまたはBLE通信データが表示されます。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // ユーザー補助サービス状態確認カード
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = "Accessibility Service",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ユーザー補助: ${if (isServiceEnabled) "有効 (動作中)" else "無効 (OFF)"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "カメラアプリのバックグラウンド操作用",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    ) {
                        Text(if (isServiceEnabled) "設定確認" else "有効化")
                    }
                }
            }
        }

        item {
            // 画面ジェスチャー・座標カスタム設定パネル
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gesture, contentDescription = "Gesture Config")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "画面ジェスチャー・座標設定",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // シャッター位置 X/Y%
                    Text(
                        text = "シャッター位置: X=${(shutterXRatio * 100).toInt()}% , Y=${(shutterYRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X:", fontSize = 12.sp)
                        Slider(
                            value = shutterXRatio,
                            onValueChange = { viewModel.updateGestureConfig(sXRatio = it) },
                            valueRange = 0.10f..0.90f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Y:", fontSize = 12.sp)
                        Slider(
                            value = shutterYRatio,
                            onValueChange = { viewModel.updateGestureConfig(sYRatio = it) },
                            valueRange = 0.10f..0.95f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // フォーカス位置 X/Y%
                    Text(
                        text = "フォーカス位置 (タップ): X=${(focusXRatio * 100).toInt()}% , Y=${(focusYRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X:", fontSize = 12.sp)
                        Slider(
                            value = focusXRatio,
                            onValueChange = { viewModel.updateGestureConfig(fXRatio = it) },
                            valueRange = 0.10f..0.90f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Y:", fontSize = 12.sp)
                        Slider(
                            value = focusYRatio,
                            onValueChange = { viewModel.updateGestureConfig(fYRatio = it) },
                            valueRange = 0.10f..0.90f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // カメラ切替位置 X/Y%
                    Text(
                        text = "カメラ切替位置 (右下): X=${(switchCameraXRatio * 100).toInt()}% , Y=${(switchCameraYRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X:", fontSize = 12.sp)
                        Slider(
                            value = switchCameraXRatio,
                            onValueChange = { viewModel.updateGestureConfig(swXRatio = it) },
                            valueRange = 0.10f..0.95f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Y:", fontSize = 12.sp)
                        Slider(
                            value = switchCameraYRatio,
                            onValueChange = { viewModel.updateGestureConfig(swYRatio = it) },
                            valueRange = 0.10f..0.95f,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ズーム幅 & スピード (ms)
                    Text(
                        text = "ズーム幅: ${pinchDistancePx.toInt()}px | ピンチ速度: ${pinchDurationMs}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("幅:", fontSize = 12.sp)
                        Slider(
                            value = pinchDistancePx,
                            onValueChange = { viewModel.updateGestureConfig(distance = it) },
                            valueRange = 100f..500f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("時間:", fontSize = 12.sp)
                        Slider(
                            value = pinchDurationMs.toFloat(),
                            onValueChange = { viewModel.updateGestureConfig(durationMs = it.toLong()) },
                            valueRange = 400f..2000f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            // BLE接続パネル
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (bleState) {
                        BleConnectionState.CONNECTED -> MaterialTheme.colorScheme.secondaryContainer
                        BleConnectionState.SCANNING, BleConnectionState.CONNECTING -> MaterialTheme.colorScheme.tertiaryContainer
                        BleConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (bleState) {
                                BleConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                BleConnectionState.SCANNING, BleConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
                                BleConnectionState.DISCONNECTED -> Icons.Default.Bluetooth
                            },
                            contentDescription = "BLE Status",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BLE: ${bleState.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (connectedDeviceName != null) {
                                Text(
                                    text = "接続先: $connectedDeviceName",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    text = "KMRC 独自BLEリモコン用",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.toggleBleConnection() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bleState == BleConnectionState.CONNECTED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(
                            when (bleState) {
                                BleConnectionState.DISCONNECTED -> "BLE接続"
                                BleConnectionState.SCANNING -> "停止"
                                BleConnectionState.CONNECTING -> "接続中"
                                BleConnectionState.CONNECTED -> "切断"
                            }
                        )
                    }
                }
            }
        }

        item {
            // 最新のBLE入力カード
            if (latestBleData != null) {
                val bleData = latestBleData!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "最新の BLE データ (KMRC 独自入力)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bleData.hexValue,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "デバイス: ${bleData.deviceName}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val currentAction = bleMappings[bleData.hexValue] ?: CameraAction.NONE
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "割り当て: ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Button(onClick = { expanded = true }) {
                                    Text(currentAction.displayName)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    CameraAction.values().forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action.displayName) },
                                            onClick = {
                                                viewModel.assignActionToBleData(bleData.hexValue, action)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // 直近のHIDキー入力カード
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "最新の HID キー入力 (標準キーボード)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (latestKey != null) {
                        val key = latestKey!!
                        Text(
                            text = key.keyCodeName,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "KeyCode: ${key.keyCode} | ScanCode: ${key.scanCode} | Action: ${key.action}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "デバイス: ${key.deviceName} (ID: ${key.deviceId})",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val currentAction = keyMappings[key.keyCode] ?: CameraAction.NONE
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "割り当て: ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                Button(onClick = { expanded = true }) {
                                    Text(currentAction.displayName)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    CameraAction.values().forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action.displayName) },
                                            onClick = {
                                                viewModel.assignActionToKey(key.keyCode, action)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "キーボード型キー入力は未検知です。\n(BLEボタンの場合は上の「BLEデータ」に表示されます)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        item {
            // ログ一覧ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "イベントログ (最新100件)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = "ログ消去")
                }
            }
        }

        items(eventLogs) { event ->
            when (event) {
                is CapturedInputEvent.BleData -> {
                    BleDataRow(event)
                }
                is CapturedInputEvent.Key -> {
                    KeyEventRow(event)
                }
                is CapturedInputEvent.Motion -> {
                    MotionEventRow(event)
                }
            }
        }
    }
}

@Composable
fun BleDataRow(event: CapturedInputEvent.BleData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BLE: ${event.hexValue}",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Text(
                    text = "Dev: ${event.deviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = event.formattedTime,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun KeyEventRow(event: CapturedInputEvent.Key) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.action == "DOWN") {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${event.keyCodeName} (${event.keyCode})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Scan: ${event.scanCode} | Dev: ${event.deviceName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = event.action,
                    fontWeight = FontWeight.SemiBold,
                    color = if (event.action == "DOWN") Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 12.sp
                )
                Text(
                    text = event.formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MotionEventRow(event: CapturedInputEvent.Motion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MOTION (アナログ/スライダー)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = event.formattedTime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = event.axisValues.entries.joinToString(", ") { "${it.key}: ${"%.2f".format(it.value)}" },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
