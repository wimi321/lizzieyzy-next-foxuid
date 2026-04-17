# 百度网盘镜像维护说明

这份文档只讲一件事：怎么把 GitHub release 里最适合国内用户的 2 个 Windows 免安装包，同步到一个固定的百度网盘共享目录里。

当前方案固定为：

- 百度网盘账号：个人网盘 / PCS
- 分享方式：手动创建一次固定共享文件夹
- 自动化：GitHub release 完成后，工作流自动上传 2 个 Windows 免安装主资产
- README / release notes：后续统一展示固定百度网盘链接和提取码

## 一、你现在需要准备什么

至少准备这 3 项：

- 百度网盘开放平台 `App Key`
- 百度网盘开放平台 `Secret Key`
- 一个可长期使用的固定共享根目录

建议固定共享目录结构直接建成：

- `/LizzieYzy Next 国内下载/最新版本/`
- `/LizzieYzy Next 国内下载/历史版本/`

共享根目录固定为：

- `/LizzieYzy Next 国内下载`

这个共享根目录只需要你手动创建和手动分享一次，后续不要删，不要改链接，不要重建。

## 二、先做一次百度授权，拿 refresh_token

### 1. 生成授权链接

在仓库根目录运行：

```bash
python3 scripts/baidu_pan_oauth.py authorize-url \
  --app-key "<你的 App Key>"
```

脚本会打印一个浏览器授权地址。

### 2. 浏览器授权

打开脚本打印出来的地址，确认授权。

当前脚本默认用：

- `response_type=code`
- `redirect_uri=oob`
- `scope=basic,netdisk`

授权完成后，百度会返回一个 `code`。

### 3. 用 code 换 refresh_token

```bash
python3 scripts/baidu_pan_oauth.py exchange-code \
  --app-key "<你的 App Key>" \
  --app-secret "<你的 Secret Key>" \
  --code "<浏览器返回的 code>"
```

输出里最重要的是：

- `refresh_token`

后续 GitHub Actions 自动同步主要依赖这个值。

### 4. 如果以后 refresh_token 还有效，只想临时拿 access_token

```bash
python3 scripts/baidu_pan_oauth.py refresh-token \
  --app-key "<你的 App Key>" \
  --app-secret "<你的 Secret Key>" \
  --refresh-token "<你的 refresh_token>"
```

## 三、把 GitHub Actions secrets 配好

仓库 `Settings -> Secrets and variables -> Actions` 里建议分两批加：

第一批是“先把镜像同步跑起来”就够用的：

- `BAIDU_APP_KEY`
- `BAIDU_APP_SECRET`
- `BAIDU_REFRESH_TOKEN`
- `BAIDU_ACCESS_TOKEN`
- `BAIDU_REMOTE_ROOT`

第二批是在你把固定分享链接做好以后再补：

- `BAIDU_SHARE_URL`
- `BAIDU_SHARE_CODE`
- `CHINA_MIRROR_URL`
- `CHINA_MIRROR_CODE`

推荐值：

- `BAIDU_REMOTE_ROOT=/LizzieYzy Next 国内下载`
- `CHINA_MIRROR_URL=<你的固定共享链接>`
- `CHINA_MIRROR_CODE=<你的固定提取码>`

说明：

- `BAIDU_ACCESS_TOKEN` 不是长期必需，但在刚授权完或百度临时限制 refresh 接口时，可以直接拿来跑当前这一次同步
- `BAIDU_SHARE_URL` / `BAIDU_SHARE_CODE` 主要用于写入 `当前版本.txt`
- `CHINA_MIRROR_URL` / `CHINA_MIRROR_CODE` 主要用于生成 release notes 里的固定国内镜像入口
- 如果固定分享链接还没准备好，脚本和工作流也可以先把文件同步到百度网盘目录，后面再补分享链接并重跑一次

## 四、本地先跑一次同步首验

如果你本地已经有一版 release 资产放在 `dist/release-gh/`，可以先 dry run：

```bash
python3 scripts/sync_baidu_pan.py \
  --local-release-dir dist/release-gh \
  --release-tag 2.5.3-next-2026-04-17.1 \
  --date-tag 2026-04-17 \
  --remote-root "/LizzieYzy Next 国内下载" \
  --dry-run
```

如果 dry run 输出的 2 个文件名都对，再去掉 `--dry-run` 真跑。

如果你不想先把大包下载到自己电脑，也可以直接让脚本从 GitHub release 读取资产信息做 dry run：

```bash
GH_TOKEN="<你的 GitHub token>" \
python3 scripts/sync_baidu_pan.py \
  --from-github-release \
  --repo wimi321/lizzieyzy-next \
  --release-tag 2.5.3-next-2026-04-17.1 \
  --date-tag 2026-04-17 \
  --dry-run
```

## 五、工作流现在会怎么同步

当前设计接在 `update-release-notes.yml` 后面：

1. 先生成并更新 GitHub release notes
2. 在 GitHub Actions runner 上下载当前 GitHub release 的 2 个 Windows 免安装包
3. 调用 `scripts/sync_baidu_pan.py`
4. 自动同步到：
   - `最新版本/`
   - `历史版本/<release_tag>/`

行为固定为：

- `最新版本` 只保留当前版本的 2 个 Windows 免安装包
- `历史版本/<release_tag>` 保留该版完整镜像
- `最新版本/当前版本.txt` 会写入当前 release 信息、GitHub release URL，以及当时已经配置好的百度分享信息

## 六、同步后怎么验收

至少确认这些点：

- 共享根目录链接可以打开
- `最新版本` 和 `历史版本` 两个目录都存在
- `最新版本` 里只有当前 release 的 2 个 Windows 免安装包
- `历史版本/<release_tag>` 保留同一批文件
- `当前版本.txt` 内容正确
- 提取码可用

## 七、README 什么时候接百度入口

只有在这两件事都已经固定之后，再改 README：

- 固定共享链接已经确认不会再换
- 固定提取码已经确认不会再换

然后再把：

- `README.md`
- `README_EN.md`
- `README_JA.md`
- `README_KO.md`

统一接上稳定百度镜像入口。
