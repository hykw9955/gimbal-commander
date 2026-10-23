# Gimbal Commander (Android) - Project Context & Rules

このプロジェクトは、スマホ用ジンバルに付属するBluetoothリモコンからのキー入力をキャプチャし、Xperiaのカメラ操作（シャッター、ズーム、フォーカスなど）を行うためのAndroidアプリケーションです。

## 1. プロジェクト目的と仕様

### 主な目的
1. **Bluetoothリモコンの入力解析（キャプチャ）**:
   - ジンバルリモコンから送信されるキーコード（`KeyEvent`）やジョイスティック/ダイヤル操作（`MotionEvent`）をリアルタイムに検知・可視化する。
   - ボタンごとの `keyCode`, `scanCode`, `deviceId`, アナログ値（`AXIS_X`, `AXIS_Y` など）を特定する。
2. **キーマッピング機能**:
   - 検出したキー・モーション入力をカメラ操作（シャッター、ズームイン/アウト、フォーカス調整等）に割り当てる設定。
3. **Xperiaカメラの操作**:
   - **アプローチA (内蔵CameraXモード)**: アプリ内にCameraXによるプレビュー・撮影機能を持ち、キー入力に応じてズーム倍率変更、フォーカス制御、撮影を実行。
   - **アプローチB (アクセシビリティサービスモード)**: バックグラウンド（ユーザー補助機能 `AccessibilityService`）でリモコンのキーをインターセプトし、Xperiaの標準カメラ（Photography Pro等）に対してカメラシャッター（`KEYCODE_CAMERA`）や音量キー操作を中継・エミュレート。

---

## 2. 技術スタック & アーキテクチャ

- **言語**: Kotlin (2.0+)
- **UI**: Jetpack Compose (Material3)
- **カメラ**: CameraX (`androidx.camera`)
- **非同期処理**: Kotlin Coroutines & Flow
- **アーキテクチャ**: MVVM + Clean Architecture 原則
  - `ui/`: Compose UI, Screen, Components
  - `viewmodel/`: UI状態管理 (StateFlow), UIイベントハンドリング
  - `data/`: キーマッピング設定保存 (DataStore Preferences)
  - `service/`: `GimbalAccessibilityService` (バックグラウンドキーキャプチャ用)
  - `receiver/` / `input/`: `KeyInputHandler`, `MotionInputHandler`

---

## 3. コーディング規約 & 実装ガイドライン

### Android / Kotlin
- 状態管理には `StateFlow` または Compose の `remember` / `derivedStateOf` を適切に使い、単一方向データフロー（UDF）を維持すること。
- ハードコードされた文字列は極力避け、`strings.xml` または適切な定数クラスにまとめること。
- AccessibilityService 実装時は、権限や設定画面への誘導をユーザーにわかりやすく案内するUIを作ること。

### キー・モーションイベントの扱い
- ジンバルリモコンは HID Keyboard / Gamepad として認識される場合が多い。
  - 通常キー: `onKeyDown` / `onKeyUp`, `KeyEvent`
  - レバー・ダイヤル・ジョイスティック: `onGenericMotionEvent`, `MotionEvent`
- キーコードの重複や長押し（repeatCount）の誤検知を防ぐため、Debounceや押し下げ/離しの状態管理を意識すること。

---

## 4. 開発・デバッグ時のagyコマンド・運用方針

- コード作成時は、まずキーコード検出・テスター機能から実装し、実際のデバイスでの動作確認を優先する。
- 差分編集時は既存の構造を崩さず、明確なコメントを残す。
- ユーザーに実機でのログ確認（`adb logcat`）やアクセシビリティサービスの許可手順を的確に案内する。

