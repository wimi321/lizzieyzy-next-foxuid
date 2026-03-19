# インストールガイド

このガイドは、`LizzieYzy Next-FoxUID` をできるだけ早く使い始めたい人向けです。

## まずはパッケージを選ぶ

| 環境 | 推奨パッケージ | Java | KataGo |
| --- | --- | --- | --- |
| Windows x64 | `<date>-windows64.with-katago.zip` | 同梱 | 同梱 |
| Windows x64 | `<date>-windows64.without.engine.zip` | 同梱 | なし |
| Windows x86 | `<date>-windows32.without.engine.zip` | なし | なし |
| macOS Apple Silicon | `<date>-mac-arm64.with-katago.dmg` | App 内蔵 | 同梱 |
| macOS Intel | `<date>-mac-amd64.with-katago.dmg` | App 内蔵 | 同梱 |
| Linux x64 | `<date>-linux64.with-katago.zip` | 同梱 | 同梱 |

簡単に使い始めたい場合は `with-katago` を選んでください。

## Windows

1. `windows64.with-katago.zip` をダウンロードします。
2. 普通のフォルダに展開します。
3. `start-windows64.bat` を実行します。
4. 初回起動では内蔵 KataGo が自動検出されることが多く、手動設定は通常不要です。

## macOS

1. 自分のチップに合う dmg を選びます。
   - Apple Silicon: `mac-arm64.with-katago.dmg`
   - Intel: `mac-amd64.with-katago.dmg`
2. dmg を開いてアプリを `Applications` にドラッグします。
3. `Applications` から起動します。

現在の macOS パッケージは未署名 / 未公証です。最初の起動時にブロックされた場合は：

1. 一度開こうとします。
2. `システム設定 -> プライバシーとセキュリティ` を開きます。
3. `このまま開く` を選びます。
4. もう一度起動します。

## Linux

1. `linux64.with-katago.zip` をダウンロードします。
2. 展開します。
3. ターミナルから起動します。

```bash
chmod +x start-linux64.sh
./start-linux64.sh
```

## 野狐棋譜の取得

起動後は、野狐棋譜の入口から **数字のみの Fox ID** を入力してください。

- ユーザー名検索は現在サポートしていません。
- 最新の公開棋譜を取得する方式に統一しています。

## 関連ドキュメント

- [Installation Guide (English)](INSTALL_EN.md)
- [Troubleshooting (English)](TROUBLESHOOTING_EN.md)
- [Package Overview (English)](PACKAGES_EN.md)
