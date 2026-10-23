---
name: gimbal-key-capture
description: >-
  ジンバルのBluetoothリモコンの入力（KeyEvent, MotionEvent, BLE）の解析・マッピングおよびXperiaカメラ制御の実装・デバッグ手順
---

# ジンバルリモコンのキーコードキャプチャ & マッピング手順

このスキルは、スマートフォン用ジンバルのBluetoothリモコンから送信される入力イベントを特定し、カメラ操作へマッピングする際の開発・検証手順をまとめたものです。

## 1. Bluetoothリモコンの入力仕様の把握

ジンバルのリモコンには大きく2つの通信方式があります。

1. **Bluetooth HID (Human Interface Device) 方式**
   - OS上で通常のBluetoothキーボードやゲームコントローラーとして認識される（多くの汎用ジンバルやDJI/Insta360の互換モード）。
   - 送信イベント:
     - `KeyEvent`: シャッターボタン（例: `KEYCODE_VOLUME_UP`, `KEYCODE_CAMERA`, `KEYCODE_ENTER` など）
     - `MotionEvent`: ズームスライダーやダイヤル、ジョイスティック（例: `AXIS_X`, `AXIS_Y`, `AXIS_RZ`, `AXIS_HAT_X`, `AXIS_WHEEL` など）
2. **BLE (Bluetooth Low Energy) 独自サービス方式**
   - 専用アプリでのみ動作するように設計されており、HIDキーイベントを送信せず独自GATT CharacteristicのNotifyを送信するタイプ。
   - この場合はBluetoothGattでのスキャン・接続・Characteristic購読が必要。

## 2. キーイベントのキャプチャ実装

### フォアグラウンド（Activity）でのキャプチャ
```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val action = when (event.action) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        else -> "OTHER"
    }
    Log.d("KeyCapture", "Device: ${event.device?.name}, KeyCode: ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}), Action: $action, ScanCode: ${event.scanCode}")
    // イベントをViewModel等に送る
    return super.dispatchKeyEvent(event)
}

override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
    // スライダーやジョイスティックのアナログ値
    val axisX = event.getAxisValue(MotionEvent.AXIS_X)
    val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
    Log.d("MotionCapture", "Motion: axisX=$axisX, axisY=$axisY, source=${event.source}")
    return super.dispatchGenericMotionEvent(event)
}
```

### バックグラウンド（AccessibilityService）でのキャプチャ
標準カメラ（Xperia Photography Pro等）を操作する場合、自作アプリはバックグラウンドに回るため、`AccessibilityService` の `onKeyEvent` を使用する。
- `accessibility_service_config.xml` で `android:canRequestFilterKeyEvents="true"` を設定。
- `onKeyEvent(event: KeyEvent): Boolean` でキーを監視・インターセプト。

## 3. カメラ操作の実装アプローチ

### アプローチ1: 自前CameraXで直接制御
- ズーム: `cameraControl.setLinearZoom(ratio)` または `setZoomRatio(ratio)`
- フォーカス: `MeteringPointFactory` と `FocusMeteringAction`
- シャッター: `imageCapture.takePicture(...)`

### アプローチ2: Xperia標準カメラへのキーリダイレクト / シミュレーション
- リモコンが音量キー以外を送ってくる場合、標準カメラが反応しないことがある。
- AccessibilityServiceから `KeyEvent` を再ディスパッチ、またはMediaSession/Cameraイベントを発火させる。

