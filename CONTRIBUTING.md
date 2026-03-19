# Contributing Guide

感谢你愿意帮助 `LizzieYzy Next-FoxUID` 变得更稳、更好用。

This project is a maintained fork. We care about practical fixes, clear packaging, and keeping the real user workflow working.

## Before You Open An Issue

请先确认下面几件事：

1. 你下载的是不是正确的发布包。
2. 你的问题是不是已经在 README、release notes 或现有 issue 里出现过。
3. 如果是野狐抓谱问题，请优先确认你输入的是 **野狐ID**，不是用户名。
4. 如果是 macOS 问题，请说明是 Apple Silicon 还是 Intel。
5. 如果是 Windows / Linux 问题，请说明你使用的是 `with-katago` 还是 `without.engine` 包。

## What Makes A Good Bug Report

请尽量带上这些信息：

- 操作系统和版本
- 下载的安装包文件名
- 是否内置引擎版本
- 问题出现时的操作步骤
- 截图或录屏
- 报错文本
- 如果能复现，请写出稳定复现步骤

A concise, reproducible report saves a lot of time for everyone.

## What Makes A Good Feature Request

功能建议最好回答这三个问题：

1. 你现在遇到的真实问题是什么。
2. 你希望软件怎么改。
3. 这个改动会帮助到哪一类用户。

## Pull Request Guidelines

如果你准备提交代码，请尽量遵守这些规则：

1. 改动尽量聚焦，一个 PR 解决一类问题。
2. 不要顺手混入无关重构。
3. 如果改了打包、引擎路径、野狐同步流程，请说明测试方式。打包相关改动建议同时参考 [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md)。
4. 如果改了文案或界面，请附截图。
5. 尽量保持中英文术语一致，尤其是 `Fox ID / 野狐ID`、`with-katago`、`without.engine`。

## Good First Contributions

这些方向都很欢迎：

- 实机安装测试
- README / 翻译优化
- 小的 UI 文案修正
- 打包脚本健壮性修复
- 野狐抓谱兼容性反馈

## Community Tone

请默认友好、具体、直接。

- 可以指出问题，但尽量附带上下文。
- 可以提出不同方案，但尽量说明取舍。
- 请避免嘲讽式反馈或无复现信息的情绪化报错。
- 讨论和协作默认遵循 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## Where To Contribute

- Bug reports: GitHub Issues
- Ideas and discussion: GitHub Discussions
- Code changes: Pull Requests
