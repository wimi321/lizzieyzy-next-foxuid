# Release Notes Template

下面这份模板可以直接作为 GitHub Release 文案的基础版本。建议每次发版时只替换日期、版本号和资产名，不要临时重写结构。

---

# 中文

**原版 `lizzieyzy` 的野狐棋谱同步很多人已经用不了了。这个维护版先把最常用的这条链路重新修好：下载安装后，继续抓谱、分析、复盘。现在统一通过“野狐数字ID”获取最新公开棋谱，只支持纯数字，不能输入昵称。**

## 下载前先看这 3 句

- Windows 用户先下载 `<date>-windows64.with-katago.installer.exe`
- 抓野狐棋谱时只输入“野狐数字ID”，只能输入纯数字，不能输入昵称
- 第一次启动会优先自动配置内置 KataGo、权重和默认引擎路径

## 这版最适合谁

- 以前在用 `lizzieyzy`，现在最头疼的是野狐棋谱同步失效
- 想要一个下载安装后就能继续使用的版本
- 不想再研究 UID、`.bat`、Java、引擎路径和权重文件

## 先下载哪个

| 你的系统 | 直接下载这个 | 说明 |
| --- | --- | --- |
| Windows 64 位 | `<date>-windows64.with-katago.installer.exe` | 主推荐，双击安装，普通用户先选这个 |
| Windows 64 位 | `<date>-windows64.with-katago.portable.zip` | 不想安装时使用，解压后运行 `.exe` |
| Windows 64 位 | `<date>-windows64.without.engine.portable.zip` | 想自己配置引擎时使用 |
| macOS Apple Silicon | `<date>-mac-arm64.with-katago.dmg` | M1 / M2 / M3 / M4 等机器 |
| macOS Intel | `<date>-mac-amd64.with-katago.dmg` | Intel 芯片 Mac |
| Linux 64 位 | `<date>-linux64.with-katago.zip` | Linux 64 位整合包 |

## 这次你会直接感受到什么

- 原版失效的野狐抓谱链路已经修好，并继续维护
- 界面和文档统一改成“野狐数字ID”，减少把昵称输进去的错误
- Windows 主推荐直接改成 `.installer.exe`
- 首次启动优先自动配置内置 KataGo、默认权重和引擎路径
- 发布页只保留 6 个主包，普通用户不用在历史资产里做选择

# English

**This maintained release restores the broken Fox kifu sync path. You now fetch the latest public games by entering a numeric Fox ID: digits only, no nickname.**

## Download quick guide

- Windows x64: choose `<date>-windows64.with-katago.installer.exe`
- Windows x64 portable: choose `<date>-windows64.with-katago.portable.zip`
- Windows x64 custom engine: choose `<date>-windows64.without.engine.portable.zip`
- macOS Apple Silicon: choose `<date>-mac-arm64.with-katago.dmg`
- macOS Intel: choose `<date>-mac-amd64.with-katago.dmg`
- Linux x64: choose `<date>-linux64.with-katago.zip`

## Highlights

- Fox sync restored
- numeric Fox ID only workflow, with digits-only wording
- First-launch bundled KataGo auto setup
- Windows release is now installer-first
- The public release page is now centered on a smaller, clearer asset set

# 日本語

**このメンテナンス版では、壊れていた野狐棋譜同期を復旧し、野狐数字IDで最新の公開棋譜を取得できるようにしました。**

- Windows x64 は `installer.exe` を優先配布
- 初回起動で内蔵 KataGo を自動設定
- UI と文書は野狐数字ID表記に統一

# 한국어

**이 유지보수 릴리스에서는 고장나 있던 Fox 기보 동기화를 복구했고, 이제 numeric Fox ID 로 최신 공개 기보를 가져올 수 있습니다.**

- Windows x64 는 `installer.exe` 를 우선 제공
- 첫 실행에서 내장 KataGo 자동 설정 시도
- UI 와 문서를 numeric Fox ID 기준으로 통일
