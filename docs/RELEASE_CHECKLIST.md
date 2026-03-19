# 发布检查清单

这份清单面向维护者，目标是让每次发版都尽量稳定、可复用、少返工。

## 一、发版前先确认这些目标

本项目当前发版的核心目标是：

- 用户能看懂该下载哪个包
- `with-katago` 包尽量开箱即用
- 野狐棋谱同步修复仍然可用
- 发布页与 README 的说法一致

## 二、发版前环境准备

### 本地工具链

仓库内当前已经有一套本地工具缓存，可优先使用：

- JDK: `.tools/jdk-21/jdk-21.0.10.jdk/Contents/Home`
- Maven: `.tools/apache-maven-3.9.10/bin/mvn`

示例：

```bash
export JAVA_HOME="$PWD/.tools/jdk-21/jdk-21.0.10.jdk/Contents/Home"
export PATH="$PWD/.tools/apache-maven-3.9.10/bin:$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

### 关键脚本

当前发版流程主要依赖这些脚本：

- `scripts/prepare_bundled_runtime.sh`
- `scripts/prepare_bundled_katago.sh`
- `scripts/package_release.sh`
- `scripts/package_macos_dmg.sh`

### GitHub Actions

当前仓库里已经有一个 Intel Mac 发布工作流：

- `.github/workflows/build-macos-amd64-release.yml`

它会在 GitHub Actions 上：

- 安装 Java 21
- 安装 Maven 和 KataGo
- 构建 shaded jar
- 准备 bundled KataGo assets
- 打出 `mac-amd64.with-katago.dmg`
- 上传到现有 release

## 三、构建前检查

发版前至少确认：

- `README.md` 的下载建议与当前计划上传的包一致
- `README_EN.md` 的包名没有过时
- 界面里仍然是 `Fox ID / 野狐ID` 口径
- `weights/default.bin.gz` 存在
- `engines/katago/` 下目标平台文件完整
- 如需 bundled Java，对应 `runtime/` 平台目录存在

## 四、建议构建顺序

### 1. 构建主程序

```bash
mvn -DskipTests package
```

预期输出：

- `target/lizzie-yzy2.5.3.jar`
- `target/lizzie-yzy2.5.3-shaded.jar`

### 2. 准备 bundled runtime

如果本次 Windows / Linux 包需要带 Java：

```bash
./scripts/prepare_bundled_runtime.sh
```

### 3. 准备 bundled KataGo

```bash
./scripts/prepare_bundled_katago.sh
```

当前脚本默认：

- KataGo 版本：`v1.16.4`
- 默认模型优先：`g170e-b20c256x2-s5303129600-d1228401921.bin.gz`

### 4. 打 Windows / Linux / 进阶 zip 包

```bash
./scripts/package_release.sh 2026-03-20 target/lizzie-yzy2.5.3-shaded.jar
```

### 5. 打 macOS dmg

在对应芯片机器上运行：

```bash
./scripts/package_macos_dmg.sh 2026-03-20 2.5.3 target/lizzie-yzy2.5.3-shaded.jar
```

注意：

- Apple Silicon 和 Intel 需要分别打包
- 当前 macOS 包是未签名 / 未公证包
- Intel Mac 的 GitHub Actions 流程已单独补上

## 五、当前建议保留的发布资产

每次 release，优先确认这些资产存在：

- `windows64.with-katago.zip`
- `windows64.without.engine.zip`
- `windows32.without.engine.zip`
- `mac-arm64.with-katago.dmg`
- `mac-amd64.with-katago.dmg`
- `linux64.with-katago.zip`
- `Macosx.amd64.Linux.amd64.without.engine.zip`

## 六、当前建议不要再上传的资产

为了让发布页更清楚，这些旧思路目前不建议恢复：

- macOS `.app.zip`
- `other-systems.without.engine.zip`
- 含义模糊、用户一眼看不懂用途的重复包

## 七、上传到 GitHub Release 前的检查

至少逐项确认：

- 文件名日期一致
- Windows 64 同时有 `with-katago` 和 `without.engine`
- macOS 同时有 `arm64` 与 `amd64` 的 `.dmg`
- Linux 64 有 `with-katago`
- 安装说明 `.txt` 与对应 macOS dmg 一起上传
- 没把旧包误传回去

## 八、Release Notes 最好这样写

建议把最重要的信息放最前面：

1. 原版野狐棋谱同步已失效，这个维护版已修复
2. 输入野狐ID即可获取最新公开棋谱
3. 本次发版包含哪些平台包
4. 如果包策略有变化，要明确写出来

## 九、上传后验收

上传完成后，至少再检查一次：

- Releases 页面顺序是否清楚
- README 里的包名是否都能在 release 找到
- 中文说明是否放在前面且够醒目
- 下载后第一次安装的关键步骤是否已有文档
- 没有把错误平台包放进推荐列表

## 十、建议长期保留的发版习惯

- 每次发版都保留一个明确日期前缀
- 每次发版先更新 README，再上传资产
- 每次发版后补一轮下载页人工复查
- 任何涉及包策略变化的改动，都同步改 README 和文档
