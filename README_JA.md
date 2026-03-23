<p align="center">
  <img src="assets/hero.svg" alt="LizzieYzy Next-FoxUID" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/v/release/wimi321/lizzieyzy-next-foxuid?display_name=tag&label=Release&color=111111" alt="Release"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/stargazers"><img src="https://img.shields.io/github/stars/wimi321/lizzieyzy-next-foxuid?style=flat&color=444444" alt="Stars"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/downloads/wimi321/lizzieyzy-next-foxuid/total?label=Downloads&color=666666" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Platforms-Windows%20%7C%20macOS%20%7C%20Linux-888888" alt="Platforms">
</p>

<p align="center">
  <a href="README.md">中文</a> · <a href="README_EN.md">English</a> · 日本語 · <a href="README_KO.md">한국어</a>
</p>

<p align="center">
  <strong>LizzieYzy を、もう一度わかりやすく使える状態に戻します。</strong><br/>
  元のプロジェクトでは、野狐棋譜を正常に取得できない利用者が増えていました。このメンテ版は、よく使われる流れを使える形に戻します。<br/>
  <strong>ダウンロードして、野狐のニックネームを入力し、棋譜取得と解析を続けられます。</strong>
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><strong>Releases</strong></a>
  ·
  <a href="docs/INSTALL_JA.md"><strong>インストールガイド</strong></a>
  ·
  <a href="docs/TROUBLESHOOTING_EN.md"><strong>トラブル対応</strong></a>
</p>

> [!IMPORTANT]
> まずはこの 3 点だけ見れば大丈夫です:
> - Windows 利用者の多くは `windows64.with-katago.installer.exe` を選べば始めやすいです
> - 野狐棋譜取得では **野狐のニックネーム** を入力します。アプリが対応するアカウントを自動で見つけます
> - 初回起動では、分析環境を自動で準備する流れを優先します

## まず多くの人はここだけ見れば大丈夫です

**Windows** を使っているなら、まずはこれをダウンロードしてください。

**`windows64.with-katago.installer.exe`**

いちばん簡単な主推薦版です。ダウンロードして、ダブルクリックして、インストールして、そのまま使い始められます。

## どれをダウンロードするか

| あなたの環境 | まず選ぶもの |
| --- | --- |
| Windows x64 | `windows64.with-katago.installer.exe` |
| Windows x64、インストーラ不要 | `windows64.with-katago.portable.zip` |
| Windows x64、自分でエンジンを設定したい | `windows64.without.engine.portable.zip` |
| macOS Apple Silicon | `mac-arm64.with-katago.dmg` |
| macOS Intel | `mac-amd64.with-katago.dmg` |
| Linux x64 | `linux64.with-katago.zip` |

迷ったときの目安:

- Windows: 迷ったら `installer.exe`
- Mac: Apple Silicon か Intel かを先に確認
- Linux: `with-katago.zip`

## このメンテ版で改善したこと

- **野狐棋譜取得がまた使えるようになりました**
  元プロジェクトで止まっていた公開棋譜取得の流れを、また使える形に戻しています。
- **先に数字のIDを調べる必要がなくなりました**
  いまは野狐のニックネームを入力すれば、アプリが対応するアカウントを見つけて公開棋譜を取得します。
- **初回起動が楽になりました**
  まず内蔵の解析環境を準備するので、多くの利用者は最初から細かい設定をしなくて済みます。

## 3 ステップで開始

1. [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases) から自分の環境に合うパッケージをダウンロードします。
2. **野狐棋譜（ニックネームで取得）** の入口を開きます。
3. 野狐のニックネームを入力し、最新の公開棋譜を取得して、そのまま解析を続けます。

<p align="center">
  <a href="assets/fox-id-demo.gif">
    <img src="assets/fox-id-demo-cover.png" alt="LizzieYzy Next-FoxUID Demo" width="100%" />
  </a>
</p>

<p align="center">
  GitHub 上で GIF の再生が遅い場合は、上の画像をクリックすると全体のアニメーションを開けます。
</p>

## 実際の画面

以下は、現在のメンテ版そのものの実画面です。下部には **野狐棋譜** と **公式重み更新** の入口が直接見えるようになっています。

<p align="center">
  <img src="assets/interface-overview.png" alt="LizzieYzy Next-FoxUID actual interface" width="100%" />
</p>

## 統合パッケージに最初から入っているもの

| 項目 | 現在の値 |
| --- | --- |
| KataGo バージョン | `v1.16.4` |
| 既定の重み | `g170e-b20c256x2-s5303129600-d1228401921.bin.gz` |
| 初回起動の自動設定 | 有効 |
| 公式重みダウンロード入口 | あり |

多くの利用者にとって大事なのは、この 1 点です。

**主推薦の統合パッケージには KataGo と既定の重みが含まれているので、すぐに使い始めやすくなっています。**

## よくある質問

<details>
<summary><strong>先にアカウント番号を知っておく必要はありますか？</strong></summary>

不要です。野狐のニックネームを入力すれば、アプリが対応するアカウントを自動で探します。取得後の一覧にはニックネームとアカウント番号の両方が表示されます。
</details>

<details>
<summary><strong>なぜニックネーム入力に変えたのですか？</strong></summary>

普通の利用者はアカウント番号よりニックネームを覚えていることが多いためです。面倒な検索手順をアプリの中に戻しました。
</details>

<details>
<summary><strong>棋譜が見つからないときは何を確認すればよいですか？</strong></summary>

ニックネームが正しいか、そのアカウントに最近の公開棋譜があるか、一時的なネットワーク問題がないかを確認してください。
</details>

<details>
<summary><strong>初回起動で KataGo を手動設定する必要はありますか？</strong></summary>

多くの `with-katago` 利用者には不要です。内蔵 KataGo、重み、設定パスを自動で見つける流れを優先します。
</details>

<details>
<summary><strong>macOS で最初にブロックされたらどうすればよいですか？</strong></summary>

現在の macOS ビルドはまだ署名と公証を行っていません。最初に止められた場合は、[インストールガイド](docs/INSTALL_JA.md) の手順を確認してください。
</details>

## さらに見る

- [インストールガイド](docs/INSTALL_JA.md)
- [Package Overview](docs/PACKAGES_EN.md)
- [Troubleshooting](docs/TROUBLESHOOTING_EN.md)
- [Tested Platforms](docs/TESTED_PLATFORMS.md)
- [Changelog](CHANGELOG.md)
- [Support](SUPPORT.md)

## クレジット

- Original project: [yzyray/lizzieyzy](https://github.com/yzyray/lizzieyzy)
- KataGo: [lightvector/KataGo](https://github.com/lightvector/KataGo)
- 野狐棋譜取得の参考:
  - [yzyray/FoxRequest](https://github.com/yzyray/FoxRequest)
  - [FuckUbuntu/Lizzieyzy-Helper](https://github.com/FuckUbuntu/Lizzieyzy-Helper)
