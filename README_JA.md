<p align="center">
  <img src="assets/hero.svg" alt="LizzieYzy Next-FoxUID" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/v/release/wimi321/lizzieyzy-next-foxuid?display_name=tag&label=Release&color=1B4D3E" alt="Release"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml"><img src="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/stargazers"><img src="https://img.shields.io/github/stars/wimi321/lizzieyzy-next-foxuid?style=flat&color=7F4F24" alt="Stars"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/downloads/wimi321/lizzieyzy-next-foxuid/total?label=Downloads&color=2F4858" alt="Downloads"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-GPL%20v3-E7A23B" alt="License"></a>
</p>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a> · 日本語 · <a href="README_KO.md">한국어</a>
</p>

<p align="center">
  <strong>壊れてしまった野狐棋譜同期を、もう一度使える状態に戻すためのメンテ版です。</strong><br/>
  このフォークの目的は、ダウンロードして開き、<strong>野狐数字ID</strong> を入力して、すぐに棋譜取得・解析・復盤へ戻れる状態を作ることです。野狐数字ID は数字のみ入力でき、ニックネームは使えません。
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases">Releases</a>
  ·
  <a href="#ひと目でわかること">ひと目でわかる</a>
  ·
  <a href="#どのパッケージを選ぶか">パッケージ選択</a>
  ·
  <a href="#初回はこの順番で使う">最初の流れ</a>
  ·
  <a href="#クイックスタート">クイックスタート</a>
  ·
  <a href="#ドキュメントとサポート">ドキュメント</a>
  ·
  <a href="#参加方法">参加方法</a>
</p>

> [!IMPORTANT]
> すぐ使いたい人は、まず次の 3 点だけ見てください:
> - Windows は `windows64.with-katago.installer.exe` を先に選ぶ
> - 野狐棋譜取得では **野狐数字ID** を入力する。数字のみ、ニックネーム不可
> - 初回起動では、そのまま使えるように分析まわりの準備を自動で進める

## ひと目でわかること

| 気になること | このメンテ版の答え |
| --- | --- |
| ダウンロード後すぐ開けるか | Windows は `installer.exe`、macOS は `.dmg`、Linux は整合 zip を主推薦 |
| 野狐棋譜を今でも取れるか | 壊れていた公開棋譜取得の流れを修復して継続保守 |
| 何を入力すればよいか | 表記を **野狐数字ID** に統一し、数字のみ・ニックネーム不可と明記 |
| 初回起動で設定に止まらないか | まず分析まわりの自動準備を試みるので、多くの利用者はそのまま始められる |
| 今後も保守されるか | このリポジトリ自体が継続リリースと修正のために運用される |

## 実際の画面

![LizzieYzy Next-FoxUID Screenshot](screenshot_en.png)

## どのパッケージを選ぶか

> [!TIP]
> 迷ったら `with-katago` を選べば大丈夫です。`without.engine` は自分でエンジンを管理したい人向けです。

<p align="center">
  <img src="assets/package-guide.svg" alt="LizzieYzy Next-FoxUID Package Guide" width="100%" />
</p>

| 環境 | 推奨パッケージ | 実行環境 | すぐ解析できるか | 向いている人 |
| --- | --- | --- | --- | --- |
| Windows x64 | `windows64.with-katago.installer.exe` | 同梱 | 同梱 | ダウンロード後すぐ使いたい |
| Windows x64 | `windows64.with-katago.portable.zip` | 同梱 | 同梱 | インストーラを使わずに始めたい |
| Windows x64 | `windows64.without.engine.portable.zip` | 同梱 | なし | エンジンを自分で管理したい |
| macOS Apple Silicon | `mac-arm64.with-katago.dmg` | App 内蔵 | 同梱 | M シリーズ Mac |
| macOS Intel | `mac-amd64.with-katago.dmg` | App 内蔵 | 同梱 | Intel Mac |
| Linux x64 | `linux64.with-katago.zip` | 同梱 | 同梱 | Linux デスクトップ |


現在の公開 release では、この 6 種類を主推薦として整理しています。

関連リンク：

- [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases)
- [Package Overview (English)](docs/PACKAGES_EN.md)
- [Tested Platforms](docs/TESTED_PLATFORMS.md)

## 初回はこの順番で使う

<p align="center">
  <img src="assets/start-flow.svg" alt="LizzieYzy Next-FoxUID Start Flow" width="100%" />
</p>

多くの利用者は、だいたい次の流れで使います。

1. 自分の環境に合うパッケージをダウンロードする
2. アプリを起動して野狐棋譜の入口を開く
3. 数字の **野狐数字ID** を入力して公開棋譜を取得する
4. そのまま KataGo 解析とレビューを続ける

下の短いデモ画像は、その流れを視覚的にまとめたものです。

<p align="center">
  <img src="assets/fox-id-demo.gif" alt="LizzieYzy Next-FoxUID Demo" width="100%" />
</p>

## クイックスタート

1. [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases) から自分の環境に合うパッケージをダウンロードします。
2. 最短で始めたい場合は `with-katago` を選びます。
3. アプリを起動し、野狐棋譜の入口を開きます。
4. 数字だけの **野狐数字ID** を入力して、最新の公開棋譜を取得します。
5. 初回起動が OS にブロックされた場合は、[インストールガイド](docs/INSTALL_JA.md) を確認してください。

## 何ができるか

| 用途 | 現在利用できる機能 |
| --- | --- |
| 棋譜取得 | 野狐数字IDで最新の公開棋譜を取得 |
| 対局レビュー | 勝率推移、目差推移、失着統計、各種可視化 |
| 高速解析 | KataGo analysis mode を使った並列解析 |
| バッチ処理 | 複数棋譜のまとめ解析 |
| エンジン比較 | 双方エンジン比較、エンジン同士の対局 |
| 局所研究 | 詰碁・局所解析の補助 |
| その他 | 棋盤同期、形勢判断など元プロジェクトの主要機能 |

## 元プロジェクトとの違い

| 項目 | 元の LizzieYzy | Next-FoxUID |
| --- | --- | --- |
| 野狐同期 | 多くの利用者にとって壊れていた | 修復済み |
| 入力名称 | UID / ユーザー名表記が混在 | 野狐数字ID に統一 |
| 配布構成 | どれを選ぶか分かりにくい | 用途別・プラットフォーム別に整理 |
| macOS 配布 | 旧来の構成が分かりにくい | `.dmg` を中心に Apple Silicon / Intel を分離 |
| Windows x64 | 目的別の分かれ方が弱い | `with-katago` と `without.engine` を併存 |
| 保守状態 | ほぼ停滞 | 継続保守中 |

## 整合パッケージに入っているもの

| 項目 | 現在の値 |
| --- | --- |
| KataGo バージョン | `v1.16.4` |
| 既定の内蔵重み | `g170e-b20c256x2-s5303129600-d1228401921.bin.gz` |
| 初回起動の自動設定 | 有効 |
| 重みダウンロード補助 | 対応 |

一言で言えば、主推薦の整合パッケージには KataGo と既定重みが最初から入っているため、多くの利用者は最初に自分で重みを探す必要がありません。

## 元プロジェクトから移行する場合

- 野狐棋譜の入口は 数字のFox ID ベースの流れとして整理されています
- ユーザー名検索は現在のサポート対象ではありません
- Windows x64 では `with-katago` と `without.engine` の両方を維持しています
- macOS では追加の `.app.zip` より `.dmg` を中心にしています
- このリポジトリは一時的な修正置き場ではなく、継続保守のために使われます

## ドキュメントとサポート

- [インストールガイド](docs/INSTALL_JA.md)
- [Troubleshooting (English)](docs/TROUBLESHOOTING_EN.md)
- [Package Overview (English)](docs/PACKAGES_EN.md)
- [Development Guide (English)](docs/DEVELOPMENT_EN.md)
- [Tested Platforms](docs/TESTED_PLATFORMS.md)
- [Support](SUPPORT.md)
- [Changelog](CHANGELOG.md)

## よくある質問

<details>
<summary><strong>なぜユーザー名検索をやめたのですか？</strong></summary>

利用者にとって分かりにくく、保守もしにくかったためです。このフォークでは 数字のFox ID ベースに統一しています。
</details>

<details>
<summary><strong>なぜ macOS で app.zip をやめて dmg を中心にしたのですか？</strong></summary>

大半の利用者は直接インストールできる形式を必要としているためです。
</details>

<details>
<summary><strong>自分の環境が Tested Platforms に載っていない場合は？</strong></summary>

まず [Tested Platforms](docs/TESTED_PLATFORMS.md) と [インストールガイド](docs/INSTALL_JA.md) を確認してください。成功でも失敗でも報告してもらえると、このプロジェクトの品質向上に直結します。
</details>

## ロードマップ

- [x] 野狐棋譜同期の修復
- [x] 数字のFox ID ベースへの統一
- [x] マルチプラットフォーム配布の再整理
- [x] Intel Mac パッケージの復旧
- [x] インストール / トラブル対応文書の追加
- [ ] 実機インストール報告の拡充
- [ ] スクリーンショットや紹介素材の改善
- [ ] 日本語 / 韓国語の補助ドキュメント拡充

## 参加方法

今とくに歓迎しているのは次のような協力です。

- Windows / Linux / Intel Mac の実機インストール報告
- 野狐同期の互換性報告
- ドキュメントや翻訳の改善
- パッケージングやリリース周りの修正
- 小さくまとまったコード修正

リンク：

- [Contributing Guide](CONTRIBUTING.md)
- [Code Of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [Support](SUPPORT.md)
- [Issues](https://github.com/wimi321/lizzieyzy-next-foxuid/issues)
- [Discussions](https://github.com/wimi321/lizzieyzy-next-foxuid/discussions)

## クレジット

- Original project: [yzyray/lizzieyzy](https://github.com/yzyray/lizzieyzy)
- Upstream GUI base: [featurecat/lizzie](https://github.com/featurecat/lizzie)
- Engine: [lightvector/KataGo](https://github.com/lightvector/KataGo)

## ライセンス

元プロジェクトのライセンスを引き継いでいます。詳しくは [LICENSE.txt](LICENSE.txt) を参照してください。
