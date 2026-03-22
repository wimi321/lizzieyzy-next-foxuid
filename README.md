<p align="center">
  <img src="assets/hero.svg" alt="LizzieYzy Next-FoxUID" width="100%" />
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/v/release/wimi321/lizzieyzy-next-foxuid?display_name=tag&label=Release&color=1B4D3E" alt="Release"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml"><img src="https://github.com/wimi321/lizzieyzy-next-foxuid/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/stargazers"><img src="https://img.shields.io/github/stars/wimi321/lizzieyzy-next-foxuid?style=flat&color=7F4F24" alt="Stars"></a>
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases"><img src="https://img.shields.io/github/downloads/wimi321/lizzieyzy-next-foxuid/total?label=Downloads&color=2F4858" alt="Downloads"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-GPL%20v3-E7A23B" alt="License"></a>
  <img src="https://img.shields.io/badge/Platforms-Windows%20%7C%20macOS%20%7C%20Linux-4A5D23" alt="Platforms">
</p>

<p align="center">
  中文 · <a href="README_EN.md">English</a> · <a href="README_JA.md">日本語</a> · <a href="README_KO.md">한국어</a>
</p>

<p align="center">
  <strong>把原版已经失效的野狐棋谱同步重新做回可用。</strong><br/>
  这个维护版不想让你再研究 UID、`.bat`、引擎路径和权重文件，而是希望你下载安装后就能继续抓谱、分析、复盘。现在统一通过 <strong>野狐数字ID</strong> 获取最新公开棋谱，只支持<strong>纯数字</strong>，不能输入昵称。
</p>

<p align="center">
  <a href="https://github.com/wimi321/lizzieyzy-next-foxuid/releases">下载发布包</a>
  ·
  <a href="#下载前只看这一段">先下载哪个</a>
  ·
  <a href="#三分钟上手">三分钟上手</a>
  ·
  <a href="#发布包说明">发布包说明</a>
  ·
  <a href="#文档与支持">文档与支持</a>
</p>

> [!IMPORTANT]
> 如果你只想下载安装后直接用，先记住这 3 句：
> - Windows 先下 `windows64.with-katago.installer.exe`
> - 抓野狐棋谱时只输入“野狐数字ID”，只能输入纯数字，不能输入昵称
> - 第一次启动会优先自动配置内置 KataGo、默认权重和引擎路径

## 这个版本适合谁

- 你以前在用 `lizzieyzy`，但现在野狐棋谱同步已经失效
- 你想要一个下载安装后就能继续使用的版本
- 你不想再研究 UID、`.bat`、Java、引擎路径和权重文件
- 你希望 Windows、macOS、Linux 都有明确可选的整合包

## 下载前只看这一段

| 你现在在用什么 | 直接下载这个 | 这是给谁的 |
| --- | --- | --- |
| Windows 64 位，想装完就用 | `windows64.with-katago.installer.exe` | 最省事，双击安装，首启自动优先配置内置 KataGo |
| Windows 64 位，想免安装 | `windows64.with-katago.portable.zip` | 想解压后直接运行，不走安装向导 |
| Windows 64 位，想自己配引擎 | `windows64.without.engine.portable.zip` | 保留程序主体和运行时，KataGo 自己配 |
| macOS Apple Silicon | `mac-arm64.with-katago.dmg` | M1 / M2 / M3 / M4 等机器 |
| macOS Intel | `mac-amd64.with-katago.dmg` | Intel 芯片 Mac |
| Linux 64 位 | `linux64.with-katago.zip` | 想直接开始分析和抓谱 |

> [!TIP]
> 当前维护版公开发布时，主推荐列表统一只保留这 6 个用户向主包。旧 tag 里如果还能看到兼容或历史资产，那属于历史格式，不再放进主推荐区。

## 为什么很多人会先看这个维护版

因为它先解决的不是“换一套界面”或者“再加一堆功能”，而是把大家最常用、也最容易坏的那条链路重新打通：

- 下载之后能不能直接打开
- 输入后能不能真正抓到野狐公开棋谱
- 第一次启动还要不要手工折腾引擎和权重
- 发布页是不是还让人一眼看不懂该下哪个包

这个维护版给你的结果是：

- 原版失效的野狐抓谱链路已经修好，并继续维护
- 界面和文档统一改成“野狐数字ID”，减少把昵称输进去的错误
- Windows 主推荐直接改成 `.installer.exe`，普通用户不用先理解 `.bat`
- 首次启动优先自动配置内置 KataGo、默认权重和引擎路径
- 发布页只保留 6 个主包，下载决策更直接

## 三分钟上手

1. 去 [Releases](https://github.com/wimi321/lizzieyzy-next-foxuid/releases) 选对自己系统的包。
2. Windows 用户优先选 `windows64.with-katago.installer.exe`；macOS 用户按芯片选 `.dmg`；Linux 用户选 `linux64.with-katago.zip`。
3. 第一次启动时，程序会优先自动识别内置 KataGo、配置文件和默认权重。
4. 打开 **野狐棋谱（输入野狐数字ID获取）**，输入纯数字的野狐数字ID。不能输入昵称。
5. 继续用内置或自定义 KataGo 做分析和复盘。

## 首次启动现在会自动做什么

现在的首启流程，不再默认把用户丢进一堆手工设置里。

程序会优先尝试：

- 检测包内是否已经带好 KataGo、配置文件和默认权重
- 自动写入可用的默认引擎配置
- 如果本地缺少权重，提供下载推荐官方权重的入口
- 只有在自动配置仍然失败时，才退回到手工设置界面

这套逻辑的目标很明确：让大多数普通用户第一次打开就能直接开始用，而不是先研究引擎路径。

## 项目截图

![LizzieYzy Next-FoxUID Screenshot](screenshot.png)

## 发布包说明

> [!TIP]
> 对大多数人来说，记住一句话就够了：想省事就选 `with-katago`，想完全自定义再选 `without.engine`。

| 系统 | 推荐资产 | 是否内置 Java | 是否内置 KataGo | 安装方式 |
| --- | --- | --- | --- | --- |
| Windows 64 位 | `windows64.with-katago.installer.exe` | 是 | 是 | 双击安装，开始菜单和桌面快捷方式 |
| Windows 64 位 | `windows64.with-katago.portable.zip` | 是 | 是 | 解压后运行 `LizzieYzy Next-FoxUID.exe` |
| Windows 64 位 | `windows64.without.engine.portable.zip` | 是 | 否 | 解压后运行，自行配置引擎 |
| macOS Apple Silicon | `mac-arm64.with-katago.dmg` | App 自带运行时 | 是 | 拖入 Applications |
| macOS Intel | `mac-amd64.with-katago.dmg` | App 自带运行时 | 是 | 拖入 Applications |
| Linux 64 位 | `linux64.with-katago.zip` | 是 | 是 | 解压后运行 `start-linux64.sh` |

补充说明：

- Windows 现在把安装器放在最前面，是为了让普通用户不用再去理解 `.bat`。
- Windows 的无引擎包也改成 `.exe` 便携形式，不再把 `.bat` 作为主入口。
- macOS 继续以 `.dmg` 为主，不再把 `app.zip` 作为主推荐。
- Linux 继续保留可直接运行的整合包。
- 当前公开 release 主列表控制在 6 个主资产以内，避免历史兼容包重新混进首屏。

## 现在和原版有什么不同

| 项目 | 原版 LizzieYzy | Next-FoxUID |
| --- | --- | --- |
| 野狐棋谱同步 | 对很多用户已经失效 | 已修复并继续维护 |
| 输入方式 | UID / 用户名 / 其它叫法混在一起 | 统一为野狐数字ID |
| 首次启动 | 经常需要自己配引擎 | 优先自动配置内置引擎 |
| Windows 使用体验 | 主要依赖 zip + bat | 以 `.installer.exe` 和 `.exe` 便携包为主 |
| macOS 发布 | 历史包型偏杂 | 以 `.dmg` 为主，区分 Apple Silicon / Intel |
| 项目维护 | 基本停滞 | 持续发包、修文档、收反馈 |

## 整合包里自带什么

| 项目 | 当前值 |
| --- | --- |
| KataGo 版本 | `v1.16.4` |
| 默认内置权重 | `g170e-b20c256x2-s5303129600-d1228401921.bin.gz` |
| 首启自动配置 | 已启用 |
| 权重补全能力 | 支持下载推荐官方权重 |

如果你只想知道一句话：主推荐整合包已经把 KataGo 和默认权重一起带好，普通用户下载后不用先自己找权重。

常见路径：

- Windows / Linux 整合包权重：`Lizzieyzy/weights/default.bin.gz`
- macOS 整合包权重：`LizzieYzy Next-FoxUID.app/Contents/app/weights/default.bin.gz`
- macOS 整合包引擎：`LizzieYzy Next-FoxUID.app/Contents/app/engines/katago/`

## 文档与支持

| 你需要什么 | 入口 |
| --- | --- |
| 安装说明 | [安装指南](docs/INSTALL.md) |
| 看懂所有发布包 | [发布包说明](docs/PACKAGES.md) |
| 启动失败 / 抓谱无结果 / 引擎没连上 | [常见问题与排错](docs/TROUBLESHOOTING.md) |
| 看哪些平台已经有人实测 | [已验证平台](docs/TESTED_PLATFORMS.md) |
| 了解如何发版 | [发布检查清单](docs/RELEASE_CHECKLIST.md) |
| 获取帮助 | [Support](SUPPORT.md) |
| 查看更新历史 | [更新日志](CHANGELOG.md) |

## 常见问题

<details>
<summary><strong>为什么只支持野狐数字ID，不支持用户名搜索？</strong></summary>

因为用户名搜索更容易误判，也更难维护。这个维护版统一按野狐数字ID工作，界面、文档和问题反馈模板都按这个口径整理。
</details>

<details>
<summary><strong>什么是野狐数字ID？</strong></summary>

就是野狐账号对应的那串纯数字编号。这里不能输入昵称，也不能输入中文用户名，必须输入数字。
</details>

<details>
<summary><strong>第一次打开还需要自己设置引擎吗？</strong></summary>

大多数用户不需要。现在程序会优先自动识别内置 KataGo、默认权重和配置路径。只有自动配置仍然失败时，才需要你手工处理。
</details>

<details>
<summary><strong>Windows 为什么改成 installer.exe 作为主推荐？</strong></summary>

因为普通用户真正需要的是“下载、双击、装好、能打开”，而不是先理解 `.bat`、Java 路径和目录结构。便携包仍然保留，但不再作为主入口。
</details>

<details>
<summary><strong>macOS 为什么第一次可能会被拦住？</strong></summary>

因为当前维护版的 macOS 包还没有做签名 / 公证。第一次被 Gatekeeper 拦截是正常现象，按安装文档里的“仍要打开”步骤处理即可。
</details>

## 参与维护

当前最欢迎的反馈和贡献：

- Windows / Linux / Intel Mac 的真实安装反馈
- 野狐抓谱兼容性反馈
- 发布页、安装文档、翻译文案优化
- 打包、首启自动配置、引擎集成相关修复

相关入口：

- [Contributing Guide](CONTRIBUTING.md)
- [Code Of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)
- [Issues](https://github.com/wimi321/lizzieyzy-next-foxuid/issues)
- [Discussions](https://github.com/wimi321/lizzieyzy-next-foxuid/discussions)

## 致谢

- 原项目：[yzyray/lizzieyzy](https://github.com/yzyray/lizzieyzy)
- KataGo：[lightvector/KataGo](https://github.com/lightvector/KataGo)
- 野狐抓谱历史参考：
  - [yzyray/FoxRequest](https://github.com/yzyray/FoxRequest)
  - [FuckUbuntu/Lizzieyzy-Helper](https://github.com/FuckUbuntu/Lizzieyzy-Helper)
