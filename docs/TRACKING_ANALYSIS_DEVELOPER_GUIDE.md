# 选点评估（Tracking Analysis）开发指南

本文面向继续维护“评估此点”功能的开发者，说明当前 single-stream 实现为什么这样设计、
各模块分别拥有哪部分状态、曾经踩过哪些坑，以及修改时需要守住哪些边界。

行为要求以 [Tracking Analysis Contract](TRACKING_ANALYSIS_CONTRACT.md) 为准；涉及棋局历史、
ReadBoard、`MOVE`、`PASS` 或 `SNAPSHOT` 时，还必须先读
[Snapshot Node Kind](SNAPSHOT_NODE_KIND.md)。本文负责解释实现和经验，不替代合同。

## 1. 术语与命名

- 用户界面统一称“选点评估”“评估此点”“评估结果”。
- Java 类、资源键、配置键和历史文档仍使用 `tracking`。不要为了统一文案机械重命名内部
  标识符，否则会扩大配置迁移、测试和兼容范围。
- **选中点（selected point）**：用户当前希望评估的坐标，包括正在计算、等待计算和已经完成
  的点。
- **当前尝试（current attempt）**：当前唯一占用 KataGo analysis stream 的点。
- **等待点（pending point）**：尚未取得 stream lease 的点；采用 newest-first 顺序。
- **stream-only lease**：只独占一段 GTP 输出流，不修改或恢复棋盘的独占会话。
- **initial/final fence**：由带编号的 `stop` 和完整响应边界建立的开始/结束隔离线。
- **ordinary command**：沿 `Leelaz` 原有普通 FIFO queue 发送的命令。
- **typed handoff**：把 tracking stream 交给已有的 foreground analysis 或 retained engine mode
  owner；它不是通用 callback 或用户意图队列。
- **clean handback**：final fence 成功后，在严格复验 receipt 的前提下恢复 tracking 前的普通
  ponder。

## 2. 为什么改成 single-stream

旧实现为选点评估启动第二个 KataGo 进程。它带来三个根本问题：

1. 第二个进程增加 GPU/显存和进程管理成本。
2. 它的棋盘、规则、贴目、ReadBoard 会话和前台引擎生命周期容易与真实前台状态脱节。
3. 每个普通操作都可能需要额外的“停止第二引擎并重试”适配，边界持续扩张。

当前实现只复用当时的前台、本地直连、已加载且支持 `kata-analyze` 的 KataGo。目标不是让
tracking 成为新的全局调度器，而是让它临时租用现有 stream，并由原 owner 在需要时安全取回。

## 3. 设计目标与明确非目标

### 目标

- 一个 KataGo 进程、一条前台 GTP stream。
- 用户一次点击即可继续落子、导航、分析、切换或重启，不保存第二次点击意图。
- 选点评估只产生 transient overlay，不写 `Board`、history、SGF、普通候选或胜率图。
- 每个点有独立 visits 预算；新加入的等待点优先。
- final fence 或 transport 状态不确定时 fail-closed，绝不让普通 parser 接收来源不明的
  analysis 输出。
- future owner 尽量通过已有 queue、foreground handoff 或 lifecycle reservation 自然继承
  tracking 交互。

### 非目标

- Remote Compute、WebSocket、Java SSH、外部 ssh/plink 或 double-engine tracking。
- 第二个 tracking 引擎、fallback runtime、dual-runtime feature flag。
- 跨点长期持有一个 lease、跨 context 恢复、结果持久化、PV overlay 或 SGF tracking 字段。
- 第二 ordinary queue、ordinary response ledger、ID stripping、通用 transaction、callback
  registry、rollback 或保存/重放用户意图。
- 由 controller 向前台引擎发送 `play`、`undo` 或 replay 来构造局面。
- 在本功能中实现围棋 suicide/ko/superko 合法性判断。

一旦需求必须引入上述能力，应先更新合同并重新设计，不能用局部 callback 或 fallback 偷渡。

## 4. 组件与所有权

```text
RightClickMenu
      |
      v
LizzieFrame -----------------------> ReadBoardTrackingEligibilityAdapter
      |                                           |
      v                                           | before/after snapshot
TrackingAnalysisController <----------------------+
      |
      | acquire/release stream-only lease
      v
Leelaz
  |- ExclusiveGtpSession / numbered fences
  |- existing ordinary command queue
  |- typed foreground/retained handoff
  |- lifecycle and restart ownership
  `- reader binding / transport terminal settlement

TrackingAnalysisController -- immutable DisplaySnapshot --> BoardRenderer
```

| 模块 | 唯一负责的状态 | 不应该负责 |
| --- | --- | --- |
| `TrackingAnalysisController` | selected/pending/current、context、generation、progress timeout、结果和 immutable snapshot | 启动/切换引擎，修改棋盘，发送普通命令 |
| `Leelaz` | stream arbitration、fence、queue、handoff、reader incarnation、terminal/rebind settlement | 决定 UI overlay 内容，保存业务参数 |
| `ReadBoard` | helper identity、stable eligibility、revision、accepted frame、GMA retirement | 直接控制 tracking lease，推导缺失的 `MOVE/PASS` |
| `LizzieFrame` | production entry、构造当前 context、选择 local 或 ReadBoard route | 持有 controller 内部可变集合 |
| `BoardRenderer` | 消费 immutable snapshot，并复验 display node | 读取 controller 内部状态或写回结果 |
| foreground/PLAY_MODE/GMA/lifecycle owner | 自己的参数、激活和失败清理 | 把 tracking 当作通用任务队列 |

关键原则是：每份可变状态只有一个 recovery authority。其他模块只能提交意图或报告结果，
不能再做一次补偿恢复。

## 5. 一次选点评估的生命周期

### 5.1 添加点

1. `RightClickMenu` 把棋盘坐标交给 `LizzieFrame.addTrackingPoint(...)`。
2. `LizzieFrame.currentTrackingContext()` 捕获 history/node、棋盘、行棋方、规则、贴目、引擎、
   reader incarnation、interval、visits，以及可选 ReadBoard context。
3. 若 ReadBoard 存在，adapter 检查 stable snapshot 并注册绑定完整 context 的 invalidated / settled
   通知。每次独立 acquisition 前捕获准入快照，获取后复验完整 context 与 admission epoch。
4. Controller 校验坐标与 context。已有 current attempt 时，新点进入 deque 头部；否则创建新的
   generation 并请求 lease。
5. 获取后复验成功即接受该 attempt；initial fence 完成后发送以下命令，之后的纯 pending
   不撤销已接受 attempt，也不释放或重建其 stream：

   ```text
   kata-analyze <interval> allow B <coord> 1 allow W <coord> 1
   ```

6. Controller 只接受目标坐标且 visits 严格增加的 `info`。首份有效结果立即进入 snapshot；
   达到目标 visits 后请求 release。
7. final fence 成功关闭后，结果才标记为 completed，然后处理最新的 pending point。

### 5.2 状态机

```text
IDLE
  -> ACQUIRING          initial fence 尚未完成
  -> ACTIVE             tracking kata-analyze 已取得 stream
  -> RELEASING          已请求 final fence
  -> CLOSED             final fence 或 terminal settlement 已完成
```

`TrackingReleaseDisposition` 是与上述阶段正交的显示/归还语义，不要混为一个状态机：

```text
ACTIVE -> FROZEN_BY_SAFE -> CLEARED
```

它只能单调升级：安全查询可冻结最后的有效结果；任何普通操作、typed handoff、lifecycle 或
transport invalidation 都清空显示。后到的安全查询不能把 `CLEARED` 降回 frozen。

### 5.3 remove、clear 与 invalidate 的区别

- remove pending：立即从 selected/pending 移除，不触碰 current lease。
- remove current：立即隐藏该点，标记 current cancelled，后台完成 final fence；clean close
  后允许恢复原 ponder。
- 用户 clear：立即清空所有显示和 pending，current 只负责安全关闭；clean close 后允许恢复
  原 ponder。
- context/lifecycle/transport invalidate：清空 generation、receipt 和结果，旧 callback 变成
  stale no-op；不得恢复旧 ponder。

这一区分不能被一个“通用清理方法”抹平。特别是 `clear()` 与
`contextChanged(null)` 的恢复语义不同。

### 5.4 clean handback

Controller 保存第一份 acquisition receipt，而不是保存一个 `Runnable`。最后一个点关闭后，
只有同时满足以下条件才调用 `restorePonderAfterTracking(...)`：

- natural completion，或用户显式 remove current / clear；
- final fence 无 failure；
- disposition 仍为 `ACTIVE`；
- receipt 的 engine identity 与 incarnation 仍匹配；
- 当前没有 lifecycle、ordinary writer、trial 或其他 owner 竞争。

失败只返回 false，不重试、不另建 owner、不恢复旧局面的 analysis。

## 6. Context 为什么这么“重”

Tracking context 绑定以下事实：

- history identity；
- current display/history node identity；
- board width/height 与完整 stones fingerprint；
- to-play、rules、komi；
- engine identity 与 reader incarnation；
- interval 与每点 target visits；
- 可选 ReadBoard helper identity、revision、node identity 与 board revision。

这些不是缓存优化，而是结果有效性的证明。只比较坐标、手数或 Zobrist 不足以区分 history
替换、规则/贴目变化、旧 reader 回调或 ReadBoard helper replacement。

Controller generation 同时隔离旧 line、旧 timer、旧 ready/closed callback。任何异步入口都应
先用 captured generation 和 exact lease identity 复验，再接触 current state。

ReadBoard semantic revision 只表示已接受 context 变化或退休。新帧首行、同尺寸 start/clear
只推进 owner-local admission epoch、关闭新准入；相同完整帧处理成功并完成最终导航后恢复
stable，保留语义 revision。`updateReadBoardTurnTrustFromAcceptedFrame` 只更新轮次可信度；
`syncBoardStones` 持有本次处理的局部接受结果，最终发布还在 eligibility lock 内复验进入
处理时捕获的 admission epoch，不能覆盖期间发生的 history/lifecycle 失效。异常、未收齐
或含非法 cell code 的帧不能发布 stable；非法帧只在下一采样/reset/end 边界清除拒绝状态。

当前点在 pending 中完成时，controller 留住已接受等待点与首个 receipt，不获取下一份 lease。
同 context 的 settled 通知复验 LizzieFrame 捕获的完整 context，再恢复 newest-first 调度。
等待点在 acquisition 前后遇到帧切换时，先完成该次 lease 的安全关闭，再继续等待；新用户
请求失去准入时则只删除自身，不保留 retry。不要将这两类意图合并。

ReadBoard 的 invalidation 走 `contextInvalidated(expected)`，不是用户 `clear()`；settled 也
先核对 expected context。迟到的旧通知不能清空或调度更新的 context。现有 observation 类
提供 tracking-eligibility 的 pending/settled/invalidated，以及 tracking progress-timeout；
只在相应 diagnostics 开启时输出。帧接收和 stable 发布都不修改 progress timeout。

## 7. Stream arbitration 与普通命令

### 7.1 锁序

引擎内部锁序保持：

```text
engineArbitrationLock -> commandQueue
```

callback、observer、target activation/failure 和 queue wakeup 应在 ownership locks 外执行。
不要为了方便在 callback 中反向取得 arbitration lock。

ReadBoard tracking 状态由独立 `trackingEligibilityLock` 保护；controller 持自身 monitor
查询 eligibility 时不能再取得 ReadBoard owner monitor。读取既有 Board revision 与 engine
eligibility facts 不触发恢复。状态锁内只更新 accepted facts / listener ownership，不调用
controller。外层持 ReadBoard monitor 的入口把通知异步交给 EDT；实际 frame/lease 验收使用
latch 控制这种交错，并验证下一 EDT event 能完成，不给整个 parser 加锁。

### 7.2 为什么 ordinary command 必须先入原 queue

普通操作的 first-winner 判定以既有 queue entry 为准：

1. 在原 queue 建立 entry；
2. 再 claim tracking release；
3. final fence 后由原 writer 按 FIFO 发送。

`QueuedCommand.beginOutputWrite()` 才是实际 writer acceptance。若先 release tracking、后创建
queue entry，typed handoff 或 lifecycle 可能插入空窗，用户的一次点击会丢失或错误变成 busy。

不要给 tracking 创建 command-name adapter。`komi`、`boardsize`、position estimate、manual
genmove 和未来 ordinary command 应继续走各自已有入口与同一 queue。

### 7.3 active tracking 中落子后的 ponder watermark

落子期间可能同时排队：

```text
play <color> <coord>
kata-analyze ...        # 新局面的普通 ponder
```

Tracking stream 不提供 ordinary parser 所需的单格 response watermark。只有 `play` 收到成功
响应后，才能结算后续 ponder 的 watermark；写出成功不等于落子成功。`play` 错误时不得接受
新局面的 `info`，也不得假装分析已经恢复。

### 7.4 safe raw GTP

Active tracking 期间只有无 caller ID、exact arity 的以下命令属于安全只读查询：

- `name`
- `version`
- `protocol_version`
- `list_commands`
- `known_command <name>`
- `showboard`

额外参数、caller ID、未知命令或看似只读但未证明安全的 KataGo 命令都不能静默穿过边界。
安全查询冻结已有结果但不自动 reacquire；普通操作把 disposition 升级为 `CLEARED`。

## 8. Typed handoff、lifecycle 与 Web trial

### Typed handoff

- 容量为 1，只支持已有的 `FOREGROUND_ANALYSIS` 与 `RETAINED_ENGINE_MODE`。
- target 自己持有业务参数；`Leelaz` 只捕获 target identity/kind。
- claim 时立即清 tracking display，但 target 不得提前改业务状态或发送 bytes。
- final fence 后先转交 gate，再在 locks 外复验并激活一次。
- terminal、rebind、context invalidation 或 target cancellation 只能 exactly-once fail，不能既 fail
  又 activate。

### Destructive lifecycle

restart、switch、close、benchmark/contribution 沿用已有 lifecycle reservation。winner 立即执行
原 destructive action，不等待 callback 保存的 continuation。销毁或 rebind transport 的原路径
负责退休 tracking。

Restart bootstrap receipt 只是绑定 existing lifecycle owner 与 exact reader/output 的私有凭据，
不是第三种 owner。startup、parser settings 和 board restore 仍走原 ordinary queue。

### Web trial

Web trial 与 tracking 严格互斥：

- tracking acquiring/active/closing 时，`enter_trial` 返回现有 `engine_busy`，且零 trial mutation；
- trial active 或 exit resync 未 settle 时，tracking、foreground、用户 lifecycle、GMA 和
  PLAY_MODE 都沿用 existing busy；
- 不保存 enter intent，不自动重试，不把 trial 做成 typed tracking handoff。

## 9. ReadBoard 边界

ReadBoard route 要求 current helper 的 stable accepted frame，同时没有 first-frame、HOLD、sync、
pending local move、GMA、unrestored engine 或 node mismatch。具体 reason 由
`ReadBoardTrackingEligibilityAdapter.Snapshot` 表达。

Adapter 必须做 acquisition 前后双检，因为以下竞态真实存在：

```text
读取 stable frame
    -> 开始取得 tracking lease
    -> helper/frame/node/revision 变化
    -> initial fence ready
```

第二次检查失败时不得发送 tracking request，也不得重试。EOF、`IOException`、shutdown 和 helper
replacement 还必须先退休旧 identity/generation，再清理 matching GMA；晚到 response 不能恢复
已退休会话。

Tracking 不拦截 ReadBoard helper protocol，也不从 snapshot 猜测 `MOVE/PASS`。如果 GMA 缺少
authoritative post-play frame，应修 ReadBoard producer，不能放宽 Lizzie 的 accepted-frame gate。

## 10. Display 与配置

Renderer 只读取 immutable `DisplaySnapshot`，并同时复验：

- snapshot history identity 等于 current history；
- snapshot display node identity 等于 current history node；
- `LizzieFrame.getDisplayNode()` 仍是该 current node；
- 主棋盘且没有 branch preview。

选中点立即压过同坐标普通候选。无结果时外框为中性灰；有结果后以当前普通最佳候选为动态
基准，用 `MoveRankDefinition` 的胜率/目差损失等级决定外框颜色。内部颜色固定、可配置，文字
复用普通候选布局，并按实际合成背景选择黑/白前景。

当前配置键：

```text
tracking-analysis-max-visits
show-tracking-point-outline
tracking-point-interior-color
tracking-point-interior-opacity
tracking-point-outline-opacity
tracking-point-text-auto-color
tracking-point-text-color
```

旧 `tracking-engine-max-visits` 只用于一次性迁移；旧 preload/engine command 不能再启动第二进程。
资源键和配置键仍保留 `tracking`，不要因用户文案改成“选点评估”而破坏兼容。

## 11. 主要设计取舍

| 选择 | 收益 | 代价 |
| --- | --- | --- |
| 每个点一个 lease | ownership 与结果提交边界封闭 | 每点多一次 initial/final fence 往返 |
| stream-only 而非 foreground restore | 不 replay 棋盘，不污染 Board | 需要严格 context fingerprint |
| newest-first pending | 新关注的点更快得到结果 | 不是传统 FIFO |
| final fence 后才 completed | 不把仍可能输出的 stream 当成已关闭 | UI 完成状态晚于首份结果 |
| invalidation fail-closed | 不恢复错误局面或旧 reader | 某些失败不会自动继续分析 |
| strict ReadBoard stable frame | 不消费不完整/过期棋盘 | Stop Sync 等状态下入口可能不可用 |
| Web trial 严格互斥 | owner 模型有限、可验证 | 用户必须先结束另一模式再重试 |
| 修 caller 而非泛化 rebind | 修改面小，保留既有语义 | 每个真实 caller 必须证明 identity/reservation |

曾讨论过跨点复用 lease 来减少延迟，但这会形成 cross-point persistent owner，并扩大 timeout、
cancel、context change 与普通抢占矩阵。没有新的性能证据和合同批准前不要实现。

## 12. 已踩过的坑

### 12.1 ordinary response ledger 会无限扩大维护面

早期方案试图给普通命令分配 tracking-private ID，再做 ID stripping 和 response 分类。它要求理解
所有旧 parser、streaming response、caller-supplied ID 和未来命令，因此被放弃。正确边界是复用
existing exclusive session 的 numbered fence，而不是重写所有 ordinary response。

### 12.2 方法级 rebind 修复不等于 production caller 修复

曾尝试让 `initializeStreams(...)` 结算任意 foreground owner，迅速演变成 gate promotion、callback
reentry 和跨 owner transaction。实际可达问题是 benchmark restore 可能重启了“当前引擎”而非
原 paused engine。最终修复 exact engine identity 和 caller reservation，而不是泛化底层 rebind。

### 12.3 first-winner 顺序写反会丢操作

普通操作必须先建立 queue entry；handoff 必须先建立 typed target gate；lifecycle 必须在 mutation
前取得 reservation。只检查“当前有没有 tracking session”不够，因为 closing session 与 pending
gate 之间也不能出现 admission 空窗。

### 12.4 callback 在锁内执行会造成死锁和丢 wakeup

observer、target fail/activate、state-reset callback 即使抛异常，也不能阻止 gate cleanup 或原
writer wakeup。先在锁内提交单一结果，再在锁外通知。

### 12.5 final fence 失败不能假装成功

如果 final stop 写失败、超时、reader EOF 或输出归属不确定，普通 queue 不能继续穿过。正确行为
是 fault/terminal 当前 transport，并让 owner exactly-once 失败，而不是清标记后继续发送。

### 12.6 “写出了 play”不等于“可以接受新局面分析”

只有成功 `play` response 才能结算后续 ponder watermark。否则错误局面的 `info` 可能被当作新
局面分析，表现为落子成功/失败状态和分析按钮不一致。

### 12.7 clear 与 invalidate 不能混用

用户 remove/clear 允许 clean handback；context、参数、engine 和 lifecycle invalidation 不允许。
调用方若只看到“都是清空显示”就复用同一个入口，会在错误局面恢复 ponder。

### 12.8 只做 acquisition 前 ReadBoard 检查存在 TOCTOU

Stable frame 可能在 initial fence 完成前失效。必须同时比较 helper identity、revision、node 和
board revision，并让旧 generation callback 失效。

### 12.9 generic loading 文案不能证明 tracking 失败

`WaitForAnalysis` 的“引擎加载中...”也可能来自 legacy flash 的 zero-request settlement 缺陷。
排障时要分别查看 request count、lease/handoff availability、final fence 和 dispatch completion，
不能仅凭 UI 文案归因。

### 12.10 测试中的异常输出通常是故障注入

大量测试会主动打印 EOF、timeout、partial write、parser exception 或 process destroy failure。
必须以 Maven exit code 和 `target/surefire-reports/TEST-*.xml` 的 failures/errors 为准。

### 12.11 WSL 与 Windows full suite 不能并行

WebBoard 固定端口会碰撞。两边必须串行运行。Windows temp cleanup 偶发失败需要 focused rerun，
再用一次 fresh serial full run 作为有效证据。

### 12.12 不要全仓格式化

仓库 `fmt:check` 有既存不合规基线，包含 `Leelaz.java` 等大文件。全仓 format 会制造与功能无关
的大 diff。只保持 touched hunk 风格，并运行 `git diff --check`。

## 13. 当前已知合同漂移

当前 `ReadBoardTrackingEligibilityAdapter` 将 helper/frame invalidation listener 注册为
`controller::clear`。但 `clear()` 在用户显式 clear 的 clean final fence 后允许恢复 ponder；合同
要求 ReadBoard/context invalidation fail-closed，不恢复旧 ponder。

现有测试证明 invalidation 会清 display、阻止 tracking request，却没有锁定 ponder handback。
在该语义得到明确修复或合同调整前：

- 不要把这条 listener 写法复制到新的 context source；
- 新 invalidation route 应调用无 handback 的 context invalidation 语义；
- 若要修复，先增加 production-entry RED，分别证明用户 clear 恢复、ReadBoard invalidation 不恢复；
- 不要在文档更新中顺手改变运行行为。

## 14. 扩展功能时怎么选入口

### 新增普通 GTP 操作

沿原 `sendCommand`/queue 入口入队，再 claim tracking release。需要 UI 状态的操作，应在
`beginOutputWrite()` 时 arm，并在 cancel/write failure/reset/rebind 中 exactly-once cleanup。

### 新增 foreground 或 retained owner

优先复用现有 typed category。若现有两类无法表达，不要把任意 `Runnable` 塞入 handoff；先证明
为什么需要新的 ownership category，并更新合同、first-winner 和 failure matrix。

### 新增 lifecycle 操作

继续使用 existing lifecycle reservation。reservation 成功后立即执行原 destructive action；
不要把 action 保存到 tracking closed callback。

### 新增 ReadBoard eligibility 条件

由 ReadBoard snapshot 增加明确 reason/revision，并在 adapter before/after acquisition 双检。
不要让 controller 读取 ReadBoard mutable state。

### 新增 overlay 或显示字段

把它放进 immutable `DisplaySnapshot`/`PointResult`，renderer 仍必须通过 stale-node gate。不要写入
普通 `bestMoves` 或 history node 来“借用”现有渲染。

### 修改文案或配置

文案可以本地化为“选点评估”，内部 key 默认保持兼容。配置变化必须使旧 context invalid，迁移
需要 `TrackingConfigMigrationTest` 和 localization parity 覆盖。

## 15. 测试地图

| 测试 | 主要职责 |
| --- | --- |
| `TrackingAnalysisControllerTest` | add/remove/clear、顺序、context/generation、timeout、snapshot、handback、ReadBoard adapter |
| `LeelazTrackingStreamLeaseTest` | fence、queue、handoff、lifecycle、safe GTP、rebind、timeout、write failure、ordinary state settlement |
| `TrackingProductionCutoverTest` | right-click/ReadBoard 生产入口、单 controller/engine、恢复分析、renderer 和 UI 行为 |
| `WebTrialSingleStreamExclusionTest` | tracking/trial first-winner、enter/exit 空窗、零副作用 busy |
| `Ticket07RestartBootstrapProductionEntryTest` | restart receipt、startup/restore/fence 与生产入口 |
| `EngineManagerLifecycleReservationTest` | switch/restart/close 等 lifecycle first-winner |
| `LeelazReadBoardGmaTest`、`ReadBoardShutdownTest`、`ReadBoardSyncDecisionTest` | stable eligibility、GMA、retirement、late response |
| `TrackingWindowsIntegrationHarnessTest` | public seam 的 controlled/real KataGo 计时与异常捕获 |
| `TrackingConfigMigrationTest`、localization parity tests | 配置迁移、设置入口和资源键一致性 |

推荐的 WSL/headless 命令：

```bash
unset DISPLAY && mvn -q -Dfmt.skip=true -Djava.awt.headless=true \
  -Dtest=TrackingAnalysisControllerTest,LeelazTrackingStreamLeaseTest,TrackingProductionCutoverTest test

unset DISPLAY && mvn -q clean test -Dfmt.skip=true -Djava.awt.headless=true
```

完整测试后应统计 Surefire XML，而不是只看安静模式输出。

## 16. 排障顺序

1. 记录 exact worktree、branch、HEAD、engine 类型和是否有 ReadBoard/Web trial。
2. 判断问题属于 controller state、stream arbitration、business owner、renderer 还是 ReadBoard
   producer，不要先按 UI 症状归因。
3. 确认 current lease 阶段与 disposition；区分 ACQUIRING、ACTIVE、closing 和 frozen。
4. 查看 ordinary queue entry 是否先建立、writer 是否达到 `beginOutputWrite()`。
5. 核对 initial/final numbered fence、reader incarnation、output identity 和 terminal winner。
6. 核对 context 的 history/node/engine/parameters/ReadBoard revision 是否变化。
7. 用 latch/barrier/captured stream 建 deterministic RED，禁止用 sleep 猜时序。
8. 修复真正 owner；若需要第二 queue、callback registry、retry intent 或新 permit，Stop/Replan。
9. 跑 focused、tracking parity、fresh full WSL；桌面行为再到独立 Windows validation tree 串行验收。
10. 明确区分自动化、Windows GUI、真实 TensorRT/OpenCL、Remote/SSH 等已运行与未运行证据。

## 17. Review checklist

- [ ] 没有启动第二个 tracking KataGo。
- [ ] 没有新增 ordinary queue、response ledger、ID stripping 或任意 callback registry。
- [ ] 锁序仍为 `engineArbitrationLock -> commandQueue`，通知在锁外。
- [ ] ordinary/typed/lifecycle 的 first-winner 在 mutation 和 bytes 前建立。
- [ ] final fence 或 transport 不确定时 fail-closed。
- [ ] stale generation、reader incarnation、engine/output identity 都会拒绝晚到事件。
- [ ] 用户 clear 与内部 invalidation 的 ponder handback 语义没有混淆。
- [ ] ReadBoard 在 acquisition 前后复验 stable frame，且不推导 `MOVE/PASS`。
- [ ] renderer 只消费 immutable snapshot，并保留 stale-node/branch gate。
- [ ] tracking result 不进入 Board/history/SGF/普通候选。
- [ ] 配置和本地化 key 保持兼容，参数变化会 invalidate context。
- [ ] 测试覆盖 first-winner、timeout、terminal、rebind、callback exception 和 exactly-once cleanup。
- [ ] 验证报告明确列出 Windows/真实引擎/Remote 等未运行项。

## 18. 关键源码入口

- [`TrackingAnalysisController`](../src/main/java/featurecat/lizzie/analysis/TrackingAnalysisController.java)
- [`Leelaz`](../src/main/java/featurecat/lizzie/analysis/Leelaz.java)
- [`ReadBoardTrackingEligibilityAdapter`](../src/main/java/featurecat/lizzie/analysis/ReadBoardTrackingEligibilityAdapter.java)
- [`ReadBoard`](../src/main/java/featurecat/lizzie/analysis/ReadBoard.java)
- [`LizzieFrame`](../src/main/java/featurecat/lizzie/gui/LizzieFrame.java)
- [`RightClickMenu`](../src/main/java/featurecat/lizzie/gui/RightClickMenu.java)
- [`BoardRenderer`](../src/main/java/featurecat/lizzie/gui/BoardRenderer.java)
- [`WebBoardManager`](../src/main/java/featurecat/lizzie/gui/web/WebBoardManager.java)
