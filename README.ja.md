# Clickify

<p align="center">
  <a href="README.md">English</a> | <a href="README.vi.md">Tiếng Việt</a> | <a href="README.zh-Hans.md">简体中文</a> | <b>日本語</b> | <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="docs/assets/hero_showcase.jpg" alt="Clickify クロスプラットフォーム ショーケース" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/phanbaohuy96/clickify/actions"><img src="https://img.shields.io/badge/プラットフォーム-macOS%2014%2B%20%7C%20Android%2011%2B-000000?style=for-the-badge&logo=apple&logoColor=white" alt="プラットフォーム" /></a>
  <a href="macos/"><img src="https://img.shields.io/badge/macOS-Swift%20%2F%20SwiftUI-F05138?style=for-the-badge&logo=swift&logoColor=white" alt="macOS Swift" /></a>
  <a href="android/"><img src="https://img.shields.io/badge/Android-Kotlin%20%2F%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Android Kotlin" /></a>
  <a href="docs/sdd/08-permissions-and-safety.md"><img src="https://img.shields.io/badge/タッチ安全性-SF--1%20完全解放保証-00C853?style=for-the-badge&logo=shield&logoColor=white" alt="タッチ安全性 SF-1" /></a>
  <a href="docs/sdd/09-localisation.md"><img src="https://img.shields.io/badge/言語-EN%20%7C%20VI%20%7C%20ZH%20%7C%20JA%20%7C%20ES-blue?style=for-the-badge" alt="言語" /></a>
</p>

合成入力を発行して単調な反復作業を自動化します。それぞれ**アクション（Action）**と**ターゲット（Target）**をペアにした操作の順序付きシーケンスを、手動作成または操作記録によって構築し、オンデマンドで再生します。

**1つのプロダクト、2つのプラットフォーム。** 設計コンセプトは共通ですが、実装は各OSに完全ネイティブです。

| プラットフォーム | インターフェースとステータス | 概要 |
|---|---|---|
| [**`macos/`**](macos/README.md) | **メニューバーアプリ**（macOS 14+）· *リリース中* | Swift / SwiftUI 製。ScreenCaptureKit と Apple Vision を活用。実機で手作業による検証済み — [結果を一件ずつ記録した手動 E2E テスト](docs/manual-e2e-tests.md)。 |
| [**`android/`**](android/README.md) | **フローティングオーバーレイサービス**（Android 11+）· *全4スライス完成* | AccessibilityService（ユーザー補助サービス）上で動作する Jetpack Compose オーバーレイ。エミュレータでのみ実行 — **実機テストは未実施**（[テスト計画](android/docs/testing.md)）。 |

---

## なぜ Clickify なのか？

市場にある既存のオートクリッカーの多くは、**不具合だらけの単純アプリ**（1億以上のDL数があるにもかかわらず、画面がフリーズしてスマホを再起動せざるを得ない致命的なバグを放置）か、**学習コストが異常に高いツール**（ボタンを1つ押すために難解なスクリプト言語の習得が必要）のどちらかです。Clickify はその中間に位置する理想的な選択肢を提供します。詳細な市場分析と証拠は [`android/docs/landscape.md`](android/docs/landscape.md) に記録されています。

```
                      ┌──────────────────────────────────────────────┐
                      │                 Clickify                   │
                      │   洗練されたUI · 画像認識 · クラッシュ防止   │
                      │      フリーズ完全防止 · 仕様主導の実装       │
                      └──────────────────────┬───────────────────────┘
                                             │
               ┌─────────────────────────────┴─────────────────────────────┐
               ▼                                                           ▼
┌──────────────────────────────┐                           ┌──────────────────────────────┐
│  単純クリッカー（1億+ DL）   │                           │     複雑なスクリプトツール   │
│  タッチフリーズバグ（要再起動）│                           │     極めて高い学習コスト     │
│  画像・文字認識は非対応      │                           │     難解なコード・トークン制 │
│  高額な週額サブスク請求      │                           │     バッテリー激消費・発熱   │
└──────────────────────────────┘                           └──────────────────────────────┘
```

### 1. 🛡️ フリーズしないタッチ安全性（`SF-1` と「タッチの解放」）

このカテゴリで最悪のバグは**タッチ固着フリーズ（stuck-touch freeze）**です。自動クリックが発火した瞬間にユーザーが画面に触れると、最後の接触状態が保持されたままになり、端末を再起動するまで指での操作を一切受け付けなくなります。[XDAのレポートによると、テストされたすべての有名アプリでこの現象が再現しています](android/docs/landscape.md)。

- **`SF-1` 規準** — ランナーは、完了・停止・キャンセル・エラーの**いかなる終了時にも**、保持されているすべてのマウスボタンおよびタッチストロークを無条件で確実に解放します（[安全性設計ドキュメント](docs/sdd/08-permissions-and-safety.md)）。
- Android では、常駐通知およびクイック設定タイルの両方に**タッチの解放（Free the touch）**アクションが用意されており、再起動することなく1タップで固着したタッチを強制解除できます。

<p align="center">
  <img src="docs/assets/touch-safety.jpg" alt="左はラッチされたタッチ、右は解放後" width="78%" />
</p>
<p align="center"><em>左：固まったままのタッチ。このカテゴリ全体が抱えるバグです。右：解放後 — <code>SF-1</code> はどの終了経路でも必ず離し、すでに固まったものは「タッチを解放」で戻します。</em></p>

### 2. 👁️ 座標を盲信せず、ターゲットを自律探索

固定座標は、ウィンドウが移動したり、バナーがずれたり、画面レイアウトが変わった瞬間に誤作動します。

- **テンプレートマッチング** — 画面から直接画像を切り抜き、**ステップ（Step）**の目標として設定します。macOS では2段階スケールマッチングを採用しており、Retina ディスプレイと外部モニター間を移動しても**テンプレート**が正確に認識されます（[ADR-0008](docs/adr/0008-match-templates-at-two-scales.md)）。Android では画面プロファイルに固定されるため単一スケールで照合します（[ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)）。
- **テキスト認識（OCR）** — macOS では Apple Vision を通じて単語（*「保存」*、*「報酬獲得」*、*「送信」* など）を直接狙います。**Android は未搭載**です（詳細は [`10-recognition.md`](android/docs/sdd/10-recognition.md) を参照：ML Kit に依存し、Play ストア外配布のため）。
- **明確なタイムアウト処理（`DM-16`）** — すべての探索にはタイムアウト上限が設定され、見つからなかった場合の挙動（その**ステップ**をスキップするか、**シナリオ（Scenario）**全体を停止するか）を明示的に選択できます。

<p align="center">
  <img src="docs/assets/android-crop.png" alt="Android の画面から直接テンプレートを切り抜く様子" width="31%" />
  &nbsp;
  <img src="docs/assets/android-step-find.png" alt="生成されたステップ：テンプレート、しきい値、待機時間とタイムアウト挙動" width="31%" />
</p>
<p align="center"><em>Android エミュレータ実例：認識対象を切り抜き、それに応じたステップを設定。</em></p>

### 3. 🧩 直交する「アクション × ターゲット」モデル

1つの**ステップ**は、ちょうど1つの**アクション**と1つの**ターゲット**を組み合わせた構造です（[ADR-0002](docs/adr/0002-step-is-action-times-target.md)）：

- **アクション（Actions）** — macOS：クリック（単一/ダブル/トリプル/長押し）、スクロール、移動、ドラッグ、文字列入力、ショートカット。Android：タップ、スワイプ、マルチタッチ、グローバル操作、テキスト設定。
- **ターゲット（Targets）** — macOS：カーソル位置、絶対座標、ウィンドウの最近傍角からの相対オフセット、画像**テンプレート**、OCR テキスト。Android：指定座標点、または**テンプレート**検索による移動。
- **分岐のないシナリオ** — 各**ステップ**はタイムアウト時に自身の運命のみを決定し、他のステップの制御フローに干渉しません（[ADR-0011](docs/adr/0011-a-scenario-has-no-branches.md)）。複雑な `if`/`else` の絡み合いを排除しています。

<p align="center">
  <img src="docs/assets/action-times-target.jpg" alt="どの Action も、どの Target とも組める" width="78%" />
</p>
<p align="center"><em>左が Action、右が Target — Step はその一組で、どちらも互いに独立して選べます。</em></p>

### 4. 📱 Android フローティングオーバーレイ

- 自動化対象のアプリの邪魔にならない、自由に移動可能なフローティングコントロール。
- 画面上の狙いたい場所に直接ドラッグして配置できる、番号付きの**マーカー（Marker）**。
- スワイプ時間の微調整や、手作業で組めるマルチタッチジェスチャーに対応。
- Activity とオーバーレイの両方で、アプリを再起動することなく**リアルタイムに切り替わる** 5 つの表示言語（[11-localisation.md](android/docs/sdd/11-localisation.md)）。

<p align="center">
  <img src="docs/assets/android-languages.png" alt="背景のリストと手前のオーバーレイパネルが同時にベトナム語に切り替わった状態" width="31%" />
</p>
<p align="center"><em>1つの選択で、両方のUIが即座に同期。再起動は不要です。</em></p>

### 5. 🎥 実際のユーザー操作を忠実に記録

- **macOS** — `⌥⌘R` で記録を開始・終了できます。定数間隔に平坦化せず、人間本来の操作タイミングをそのまま保持します（[ADR-0004](docs/adr/0004-recordings-keep-real-timing.md)）。操作が単一アプリ内で完結している場合、ウィンドウ相対座標を自動推論します。
- **Android** — 記録レイヤーが各タッチを記録しながら背後のアプリへと透過パスするため、普段通りアプリを操作するだけで自動記録できます（[08-recording.md](android/docs/sdd/08-recording.md)）。
- **マウスとタッチのみを対象とし、キーボード入力は記録しません。** キーボード監視は実質的にキーロガー（マルウェア）となってしまうため、プライバシー保護の観点から意図的に除外しています（[ADR-0003](docs/adr/0003-no-keyboard-capture-when-recording.md)）。

---

## 市場競合アプリとの比較

[`android/docs/landscape.md`](android/docs/landscape.md) の調査記録より：

| 評価項目 | 従来のクリッカー *(True Developers など)* | Macrorify | Klick'r / Smart AutoClicker | **Clickify（当プロジェクト）** |
|---|---|---|---|---|
| **タッチフリーズ復旧** | ❌ 業界共通の致命的バグ（再起動が必要） | ⚠️ 部分対応 | ⚠️ 特別な対策なし | ✅ **`SF-1` 規準 + 1タップ「タッチ解放」** |
| **画像検出** | ❌ なし | ✅ テンプレート + OCR | ✅ 画像トリガー | ✅ **両プラットフォームで画像対応；macOSでOCR** |
| **学習コスト** | 低（機能が極めて単純） | 非常に高い（論理図やスクリプト） | 高い（ドキュメント不足への不満多数） | **低〜中程度（直感的・ビジュアル）** |
| **クロスプラットフォーム** | ❌ Android のみ | ❌ Android のみ | ❌ Android のみ | ✅ **macOS と Android 双方ネイティブ対応** |
| **仕様書ドリブン** | — | — | — | ✅ **全挙動がコード内で引用された番号付き仕様** |

---

## 各プラットフォームの対応機能一覧

| 機能 | macOS (`macos/`) | Android (`android/`) |
|---|:---:|:---:|
| **ランタイム構成** | メニューバー + ScreenCaptureKit | フォアグラウンドサービス + ユーザー補助オーバーレイ |
| **実行アクション** | クリック、スクロール、移動、ドラッグ、文字入力、キー入力 | タップ、スワイプ、マルチタッチ、システム操作、テキスト設定 |
| **ターゲット：カーソル / 固定点** | ✅ 双方対応 | ✅ マーカー配置による固定点 |
| **ターゲット：ウィンドウ相対** | ✅ 最近傍の角を追尾 | N/A — モバイル環境には非該当 |
| **ターゲット：画像テンプレート** | ✅ 2段階スケールピラミッドマッチング | ✅ 単一スケール（[ADR-0013](android/docs/adr/0013-coordinates-are-raw-pixels-bound-to-a-screen-profile.md)） |
| **ターゲット：OCR テキスト** | ✅ Apple Vision フレームワーク | ❌ 未搭載（ML Kit 依存および野良配布のため） |
| **操作レコーディング** | ✅ マウスイベント・実時間保持 | ✅ タッチ透過記録（マルチタッチは非対応） |
| **インターフェース言語** | ✅ 5言語、リアルタイム切替 | ✅ 5言語、両UIで即座に同期切替 |
| **実機ハードウェア検証** | ✅ [手動 E2E テスト](docs/manual-e2e-tests.md) | ❌ エミュレータのみ（[テスト計画](android/docs/testing.md)） |

---

## 開発ロードマップと商用化の展望

**以下の機能は現在企画・設計段階です。** 将来の方向性を示すために記載されています：

- **コア機能は永久無料。** 無制限クリック、複数ターゲット、操作記録、`SF-1` 安全機能など、同業他社が有料化したり不具合を起こしたりしている基本機能をすべて無料で提供します。
- **悪質な定期課金を廃止し、安価な買い切り制を採用。** ユーザーが最も歓迎する買い切りモデルを導入します。
- **有料 Pro 機能候補**：アンチBANジッター（ガウス分布による座標揺らぎとランダム遅延）、人間らしいベジェ曲線スワイプ、指定時刻スケジュール実行（タイムセール・ログインボーナス）、ピクセルカラー検知（Color Guard）、無制限のシナリオ保存スロット。

---

## クイックスタート

### macOS（14 Sonoma 以降）

Xcode Command Line Tools が必要です。

```bash
git clone https://github.com/phanbaohuy96/clickify.git
cd clickify/macos

swift test              # ユニットテストおよび仕様テストの実行
./scripts/install.sh    # ビルドして /Applications へインストール
```

> **初回起動時**：「システム設定 → プライバシーとセキュリティ → アクセシビリティ」で権限を許可してください。画像テンプレートや文字認識を使う場合は「画面収録」も許可が必要です。
> **緊急停止**：いつでも **`⌥⌘S`** を押せば、即座にすべての動作を中止し、保持中のキーやボタンを解放します。

### Android（11 以降）

```bash
cd clickify/android

./gradlew assembleDebug        # デバッグ版 APK のビルド
./gradlew testDebugUnitTest    # ユニットテストの実行
```

> **初回起動時**：オーバーレイ描画権限およびユーザー補助サービスの設定画面へ案内されます。Android 13 以降では制限付き設定を手動で解除する必要があります（詳細は [`02-permissions-and-onboarding.md`](android/docs/sdd/02-permissions-and-onboarding.md) を参照）。

---

## ドキュメントと技術仕様

両プラットフォームにおいて、仕様の策定は常に**コーディングに先行**します。すべての挙動は `sdd/` ドキュメントに番号付き要件として定義され、実装コードから引用されています。

| ドキュメント | 内容 |
|---|---|
| [`CONTEXT-MAP.md`](CONTEXT-MAP.md) | 各プラットフォームの用語マッピング対応表 |
| [`CONTEXT.md`](CONTEXT.md) | 共通ドメイン言語 — 各概念の名称と厳密な定義 |
| [`docs/adr/`](docs/adr/) | アーキテクチャ決定記録 — `0001`–`0011` は共通/macOS、`0012` 以降は Android |
| [`docs/sdd/`](docs/sdd/) | システム設計書および macOS の詳細機能仕様 |
| [`android/docs/`](android/docs/) | Android 専門用語、仕様書、決定事項、市場調査およびテスト計画 |
| [`docs/manual-e2e-tests.md`](docs/manual-e2e-tests.md) | 実機での手動検証結果および計測データ |

---

## 対応言語

アプリ再起動不要で、即座に切り替え可能な 5 言語に対応しています：

🇬🇧 **English** · 🇻🇳 **Tiếng Việt** · 🇨🇳 **中文（简体）** · 🇯🇵 **日本語** · 🇪🇸 **Español**

---

## ライセンス

本プロジェクトは [MIT](LICENSE) ライセンスの下で公開されています。
