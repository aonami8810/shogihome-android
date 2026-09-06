# ShogiHome Android版 アーキテクチャ・仕様・実装・TODO

本ドキュメントは、ShogiHome の Android アプリケーション版（Capacitor 経由）における、将棋エンジン（USI）通信機能の設計、実装、および今後の TODO についてまとめたものです。

## 1. 現時点の仕様

- **対象プラットフォーム**: Android
- **フレームワーク**: Vue.js + Capacitor (Electron との共通コードベースを利用)
- **エンジン連携仕様**: 
  - Android 上の他アプリとしてインストールされている将棋エンジンと連携可能。
  - **OEX (Open Engine Extension)** に準拠。jp.shogidokoro.oex.ENGINE または shogi.oex.ENGINE の Intent アクションを持つ Service をディスカバリして利用。
  - プロトコルは USI (Universal Shogi Interface) 標準を使用。

## 2. 設計 (アーキテクチャ)

1. **動的プラグインディスカバリ (未知のエンジンの自動検出)**
   - OEX に準拠することで、ハードコードされたエンジンに依存することなく、ユーザーがインストールした任意の将棋エンジン（水匠、やねうら王など）を動的に利用可能。
   - PackageManager.queryIntentServices() を用いて対象サービスをスキャン。
2. **Capacitor プラグイン (Java / AIDL)**
   - USIEnginePlugin.java: Android のネイティブ Service 接続とライフサイクルを管理する Capacitor プラグイン。
   - IEngineService.aidl, IEngineServiceCallback.aidl: OEX 標準の AIDL インターフェースを通じて、別プロセスの将棋エンジンと双方向通信（stdin / stdout 相当）を実施。
   - エンジンプロセスのクラッシュ時 (\DeadObjectException\) には、Web 層にエラーイベントを通知し、必要に応じて安全に再起動可能な設計（Safe Recovery）。
3. **Promise ベースの非同期 FIFO コマンドキュー (TypeScript / Web 側)**
   - src/renderer/ipc/android.ts 内で、Node.js /ブラウザ イベントループ上で動作する Promise ベースの FIFO キューを実装。
   - isready コマンド送信後、eadyok の受信まで非同期待機するなど、メッセージの順序性とスレッドセーフティを保証。
   - info 文字列による中間思考情報のパースと、フロントエンド（Vue.js）へのリアルタイムストリーミング通信を実現。

## 3. 現在の実装状況

- **Android 側ネイティブプラグイン**:
  - jp.shogihome.app.USIEnginePlugin: AIDL を利用した indService, sendData, メッセージ受信コールバックの実装が完了。
- **Web 側 IPC アダプタ**:
  - src/renderer/ipc/android.ts: window.api オブジェクトの実装。window.Bridge (Electron) の代替として機能し、Android Capacitor プラグインと通信。
- **UI 連携**:
  - OEXEngineSelectorDialog.vue: インストール済みエンジンの一覧を取得し、ユーザーが選択・追加できるダイアログを実装。
- **ビルド・設定**:
  - capacitor.config.ts および Android プロジェクトの Gradle ファイル設定。

## 4. 今後の TODO / 課題

- **エラーリカバリの強化**:
  - 端末のメモリ不足 (OOM) などにより、外部の将棋エンジンプロセスがキルされた場合の、UI 上でのユーザーフレンドリーな通知と自動リカバリの洗練。
- **パフォーマンスチューニング**:
  - 非常に高速に info メッセージが流れてきた場合の、Web 側へのコールバックのスロットリング処理（現状でも動作可能だが、UI のカクつき防止などのための最適化の余地あり）。
- **権限・Android バージョン互換性確認**:
  - Android 11+ における Package Visibility (\<queries>\ タグ等) の再確認と、様々な実機デバイスでのテスト。
- **バックグラウンド実行時の挙動**:
  - ShogiHome アプリがバックグラウンドに回った際の、エンジンサービスの生存状態の制御（不要な電力消費を抑止するか、思考を継続するかなどのポリシーの決定）。
