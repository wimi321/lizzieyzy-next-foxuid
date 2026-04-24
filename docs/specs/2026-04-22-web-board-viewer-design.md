# 局域网 Web 旁观端设计文档

## 概述

为 LizzieYzy Next 添加内嵌的局域网 Web 旁观功能。启动后，同一局域网内的设备可通过浏览器访问 `http://<主机IP>:<端口>` 实时查看棋盘、候选点分析、变化图、形势判断热力图和胜率曲线。

**第一版范围：只读旁观模式。** Web 端不能操作棋盘（落子、导航等），仅展示 LizzieYzy 桌面端当前的分析画面。后续版本可增加交互功能。

## 架构

### 技术方案

WebSocket 实时推送 + 前端 Canvas 渲染。

- **Java 端**：内嵌 WebSocket 服务器（利用已有 `Java-WebSocket` 依赖）+ 简易 HTTP 静态文件服务器
- **前端**：纯 HTML/JS/CSS，Canvas 渲染棋盘和分析覆盖层
- **零额外依赖**：不引入新的 Maven 依赖

### 数据流

```
KataGo 引擎 ─ GTP ─▶ Leelaz.java ─ 事件 ─▶ WebBoardDataCollector ─ JSON ─▶ WebBoardServer
                                                                                    │
                                                                             ws:// 广播
                                                                                    │
                                                              ┌─────────────────────┼─────────────────────┐
                                                              ▼                     ▼                     ▼
                                                        📱 手机浏览器         💻 平板浏览器         🖥 电脑浏览器
                                                        (Canvas 独立渲染)    (Canvas 独立渲染)    (Canvas 独立渲染)
```

### 新增 Java 类

| 类 | 包路径 | 职责 |
|---|---|---|
| `WebBoardServer` | `featurecat.lizzie.gui.web` | 继承 `WebSocketServer`，管理所有 WebSocket 连接，广播棋盘状态和分析数据 |
| `WebBoardHttpServer` | `featurecat.lizzie.gui.web` | 极简 HTTP 服务器，从 classpath 提供前端静态文件（`src/main/resources/web/`） |
| `WebBoardDataCollector` | `featurecat.lizzie.gui.web` | 监听引擎分析事件和棋盘变化，将数据序列化为 JSON 并交给 `WebBoardServer` 广播 |

### 接入点（最小侵入）

- `LizzieFrame.refresh(int mode)` — `mode=1`（来自引擎 info 输出）时通知 `WebBoardDataCollector` 发送分析增量更新
- `LizzieFrame.refresh()` — 棋盘状态变化时通知 `WebBoardDataCollector` 发送完整状态快照
- `LizzieFrame` 菜单 — 添加「同步 → Web 旁观」开关菜单项

选择 hook `LizzieFrame.refresh()` 而非分散在 `Leelaz` 和 `Board` 多处，因为 `refresh()` 是所有状态变化的汇聚点，只需修改一个文件。

### 生命周期

1. 用户在菜单点击「启动 Web 旁观」
2. 创建 `WebBoardServer`（WebSocket，默认端口 9999）和 `WebBoardHttpServer`（HTTP，默认端口 9998）
3. `WebBoardDataCollector` 注册到引擎分析回调，开始收集和广播数据
4. 用户点击「停止」时关闭两个服务器并清理资源

### 并发模型

- `WebBoardDataCollector` 内部持有一个单线程 `ScheduledExecutorService`
- 引擎回调（engine reader thread）和棋盘变化回调（EDT）仅向 collector 提交数据快照，不直接广播
- JSON 序列化和 WebSocket 广播在 collector 的单线程 executor 上执行，避免竞争
- `WebBoardServer` 的连接管理由 `Java-WebSocket` 内部线程处理，广播时对连接集合加锁
- `onAnalysisUpdated()` 和 `onBoardStateChanged()` 内部捕获 `RejectedExecutionException`，防止 shutdown 竞态导致异常传播到 Swing EDT
- `onBoardStateChanged()` 使用 `AtomicBoolean` coalescing：快速连续调用只排队一次 `doBroadcastFullState`，防止快速导航时 executor 队列堆积

### 菜单启动行为

`WebBoardManager.start()` 涉及串行端口探测（最多 20 次 socket 操作），在后台线程执行以避免冻结 EDT。启动期间菜单项禁用，启动完成后通过 `SwingUtilities.invokeLater` 更新菜单状态。

### 移动端布局

移动端（宽度 ≤ 768px）切换为纵向布局，`body` 允许纵向滚动（`overflow-y: auto`），棋盘容器限制最大高度 60vh，确保小屏手机上胜率曲线和状态栏不被裁剪。

### 连接上限

最大同时连接数默认 **20**，可在 `config.json` 中配置（`max-connections`）。超出上限时拒绝新连接并返回关闭帧。

### HTTP 安全

`WebBoardHttpServer` 仅提供 classpath `/web/` 前缀下的文件。请求路径包含 `..` 或绝对路径时返回 403。仅支持 GET 方法。

### 端口冲突处理

默认端口被占用时，自动尝试 +1 递增，最多尝试 10 次。端口可在 `config.json` 中配置。

WebSocket 端口在启动前通过 `ServerSocket` 预检可用性（`WebSocketServer.start()` 的 bind 是异步的，无法通过 try/catch 捕获端口冲突）。HTTP 端口由 `ServerSocket` 构造函数同步 bind，异常可直接捕获。

### 连接上限语义

`maxConnections` 表示允许的最大同时连接数。检查时机在 `onOpen` 回调中，此时新连接已计入 `getConnections()` 集合，因此使用 `size() > maxConnections` 而非 `>=`。

### 首屏盘面推送

`WebBoardManager.start()` 在服务就绪后必须立即触发一次 `WebBoardDataCollector.onBoardStateChanged()`，将当前盘面写入 `WebBoardServer.lastFullState`。这样首个浏览器连接的 `onOpen` 回放即可立即显示当前棋局，而不必等待下一次棋盘变化。

### HTTP 请求路径处理

请求路径在安全检查前先经过：URL 解码 → strip query string (`?` 后部分) → strip fragment (`#` 后部分) → 路径遍历检查（`..` 和 `\\`）。

### GTP 坐标解析

Java 和 JS 端的 `gtpToXY` 均处理以下边界：
- null / 空字符串 → 返回 null
- "pass" / 非标准坐标（字母后跟非数字）→ 返回 null
- 正常坐标（A1-T19，跳过 I 列）→ 返回 `[x, y]`

### 候选点颜色方案

Web 端与桌面端保持一致的颜色逻辑：
- **最佳手（bestMoves 列表首元素）**：青色 (cyan, rgba(0, 230, 230, 0.7))
- **其余候选点**：按 `playouts / maxPlayouts` 比例的平方根，从红色（低）渐变到绿色（高）

判定最佳手使用 **数组 index（i===0）**而非 `order` 字段，与桌面端 `BoardRenderer.drawLeelazSuggestions()` 行为一致。`order` 字段在非 KataGo 引擎中可能全部为 0，不可靠。

### 多入口 Toggle 同步

Web 旁观的启动/停止有两个 UI 入口（菜单栏 + 底部工具栏）。每个入口的菜单项在弹出时通过 listener（`MenuListener` / `PopupMenuListener`）检查 `WebBoardManager.isRunning()` 并刷新文字，确保无论从哪个入口操作，另一个入口下次打开时显示正确状态。

### 标题栏 Web 地址持久化

`LizzieFrame.webBoardSuffix`（volatile String）存储 ` | Web: http://...` 后缀。`updateTitle()` 所有分支的 `setTitle` 调用末尾追加该字段，防止引擎分析回调重建标题时丢失 Web 地址。启动时设置，停止时置空，均通过 `updateTitle()` 生效。

### 关闭顺序

停止时按以下顺序：
1. 注销 `WebBoardDataCollector` 的引擎/棋盘回调，停止 executor
2. 关闭 `WebBoardServer`（断开所有 WebSocket 连接）
3. 关闭 `WebBoardHttpServer`

## WebSocket JSON 消息协议

### 1. `full_state` — 完整状态快照

连接建立时和棋盘状态变化时发送。

```json
{
  "type": "full_state",
  "boardWidth": 19,
  "boardHeight": 19,
  "stones": [0, 0, 1, 2, 0, ...],
  "lastMove": [3, 3],
  "moveNumber": 42,
  "currentPlayer": "B",
  "bestMoves": [ ... ],
  "winrate": 56.3,
  "scoreMean": 2.5,
  "playouts": 12800,
  "estimateArray": [0.9, -0.8, ...]
}
```

字段说明：
- `stones`：长度 `boardWidth * boardHeight` 的数组，`0`=空、`1`=黑、`2`=白
- `lastMove`：`[x, y]` 坐标或 `null`
- `currentPlayer`：`"B"` 或 `"W"`
- `estimateArray`：形势判断，每点 `-1`（白方领地）到 `1`（黑方领地）；可为 `null`（引擎未启用形势判断时）

### 2. `analysis_update` — 分析增量更新

引擎每次输出分析结果时发送（高频，节流至最高 10 次/秒）。

```json
{
  "type": "analysis_update",
  "bestMoves": [
    {
      "coordinate": "Q16",
      "x": 15,
      "y": 3,
      "winrate": 56.3,
      "playouts": 3200,
      "scoreMean": 2.5,
      "scoreStdev": 8.1,
      "policy": 0.18,
      "lcb": 55.8,
      "order": 0,
      "variation": ["Q16", "D4", "R14", "C16", "Q10"]
    }
  ],
  "winrate": 56.3,
  "scoreMean": 2.5,
  "playouts": 12800,
  "estimateArray": [0.9, -0.8, ...]
}
```

字段说明：
- `coordinate`：GTP 格式坐标字符串（用于显示）
- `x`, `y`：数组索引坐标（用于 Canvas 渲染）
- `variation`：后续变化序列，悬停时在棋盘上绘制
- `order`：候选点排序序号（0 = 最佳）

### 3. `winrate_history` — 胜率曲线数据

棋盘状态变化时随 `full_state` 一起发送。

```json
{
  "type": "winrate_history",
  "data": [
    {"moveNumber": 1, "winrate": 50.0, "scoreMean": 0.5, "blackToPlay": false},
    {"moveNumber": 2, "winrate": 48.2, "scoreMean": -0.3, "blackToPlay": true}
  ]
}
```

字段说明：
- `winrate`、`scoreMean` 已统一到**黑方视角**（黑方胜率 / 黑方目差）。白方走过的节点 Java 端做翻转后发送
- `blackToPlay` 表示该节点之后**轮到谁走**。客户端用 `!blackToPlay` 判定本节点是谁走的（用于问题手颜色归属）
- 未被引擎分析的节点（`playouts == 0`，例如中盘同步的早期节点）`winrate` 和 `scoreMean` 为 JSON `null`。客户端遇到 null 应跳过（断开折线、tooltip 显示「未分析」、问题手列表跳过）

### 问题手识别

客户端从 `winrate_history` 计算连续两节点的差值：
- **走子方视角**：`drop = moverIsBlack ? prev.winrate - curr.winrate : curr.winrate - prev.winrate`
- 当走子方胜率下跌 ≥5% 时进入问题手列表
- 必须按走子方视角计算，否则因数据已统一到黑方视角，白方失误会显示为黑方胜率上升而被漏检

### Tooltip 定位

`#chart-tooltip` 使用 `position: fixed` 配合 `clientX/clientY`（视口坐标），确保移动端 `overflow-y: auto` 滚动后位置仍然正确。定位时检测视口右/上边界并自动翻转到鼠标另一侧。

### 节流策略

引擎高频输出时（KataGo 每秒可输出数十次），`WebBoardDataCollector` 限制广播频率最高 **10 次/秒**，取最新一次的完整数据发送。

## 前端设计

### 布局

**桌面端（宽度 > 768px）：左右布局**
- 左侧：棋盘区（Canvas），占主体空间
- 右侧：信息面板 — 状态栏（手数、轮到谁、胜率、目差、计算量）+ 胜率曲线图

**移动端（宽度 ≤ 768px）：上下布局（响应式切换）**
- 顶部：状态栏（紧凑一行）
- 中部：棋盘（Canvas，占满宽度）
- 底部：胜率曲线图

### 棋盘 Canvas 渲染

层级从下到上：
1. 棋盘底色 + 网格线 + 星位
2. 棋子（黑/白实心圆）+ 最后一手标记
3. 形势判断热力图覆盖层（半透明色块，可开关）
4. 候选点（半透明圆圈 + 胜率/目差文字，颜色按排名区分）
5. 悬停变化图覆盖层（半透明棋子 + 手数编号）

### 交互

**悬停变化图（核心交互）：**
- 桌面端：鼠标移到候选点上 → 在棋盘上叠加绘制后续变化序列
- 移动端：长按候选点触发，点击空白处取消
- 数据来自 `bestMoves[i].variation`，纯客户端渲染

**形势判断热力图：**
- 按钮切换显示/隐藏
- 黑方领地偏蓝/绿色，白方领地偏红/橙色，中性区域透明
- 当 `estimateArray` 为 `null` 时，隐藏热力图开关按钮

**胜率曲线图：**
- Canvas 折线图，X 轴手数，Y 轴胜率 0%-100%
- 50% 基准虚线
- 可切换显示目差曲线（`scoreMean`）

### 连接状态处理

- WebSocket 断开时显示半透明遮罩「连接断开，正在重连...」
- 自动重连（指数退避：1s → 2s → 4s → 8s → 最大 30s）
- 重连成功后服务端推送 `full_state` 恢复完整画面

## 菜单集成

`LizzieFrame` 菜单栏 → 「同步」 → 「Web 旁观」子菜单：
- **启动/停止**：切换 Web 服务开关
- **复制访问地址**：将 `http://<局域网IP>:<HTTP端口>` 复制到剪贴板

启动时状态栏显示 `Web: http://192.168.x.x:9998`（显示局域网 IP，非 localhost）。

## 前端文件位置

```
src/main/resources/web/
├── index.html      主页面
├── board.js        Canvas 渲染 + WebSocket 通信
└── board.css       样式
```

打包进 jar，运行时由 `WebBoardHttpServer` 从 classpath 读取并提供。

## 配置项（config.json）

```json
{
  "web-board": {
    "http-port": 9998,
    "ws-port": 9999,
    "max-connections": 20
  }
}
```

## 不在第一版范围内

- Web 端棋盘交互（落子、前后手导航、变化选择）
- 用户认证 / 访问控制
- HTTPS 支持
- 棋谱加载/保存
- 多语言支持（前端）
- HTTP 与 WebSocket 合并为单端口（当前 Java-WebSocket 1.6.0 不原生支持）
