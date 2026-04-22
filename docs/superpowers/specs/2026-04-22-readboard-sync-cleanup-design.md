# ReadBoard Sync High-Confidence Cleanup Design

日期：2026-04-22

分支：`fix-sync`

上位契约：

- [docs/SNAPSHOT_NODE_KIND.md](/mnt/d/dev/weiqi/lizzieyzy-next/docs/SNAPSHOT_NODE_KIND.md)
- [2026-04-21-readboard-sync-boundaries-design.md](/mnt/d/dev/weiqi/lizzieyzy-next/docs/superpowers/specs/2026-04-21-readboard-sync-boundaries-design.md)
- [2026-04-22-readboard-mainline-window-sync-design.md](/mnt/d/dev/weiqi/lizzieyzy-next/docs/superpowers/specs/2026-04-22-readboard-mainline-window-sync-design.md)
- [2026-04-22-readboard-early-game-history-design.md](/mnt/d/dev/weiqi/lizzieyzy-next/docs/superpowers/specs/2026-04-22-readboard-early-game-history-design.md)

## 1. 背景

`fix-sync` 经过多轮同步回归修复后，`lizzieyzy-next` 侧的 `ReadBoard`、同步策略与测试已经明显稳定下来，但也累积了一些高置信度冗余代码、死代码与“看起来像还能再清理”的实现残留。

本轮目标不是继续改同步行为，而是在**不影响 `D:\\dev\\weiqi\\readboard` 兼容性、不改同步边界、不引入新问题**的前提下，做一轮保守型清理。

这个清理只针对：

- 当前 `fix-sync` 分支这轮同步修复中涉及的 `lizzieyzy-next` 侧代码
- 能被静态引用关系和现有测试高置信度证明为冗余的代码

本轮不把“代码看起来乱”本身当作清理理由。

## 2. 目标与非目标

### 2.1 目标

- 删除 `ReadBoard` 当前同步链路里已不再被消费的高置信度死代码。
- 保持 `readboard -> lizzieyzy-next` 协议完全不变。
- 保持当前 docs 已确认的同步决策边界完全不变。
- 通过聚焦测试和构建验证，确认清理没有伤到：
  - 野狐房间 / 棋谱上下文恢复
  - 主线窗口命中与导航
  - `FORCE_REBUILD`
  - board-only rebuild
  - `ResumeState`
  - `recordAtEnd`

### 2.2 非目标

- 不修改 `D:\\dev\\weiqi\\readboard`。
- 不扩展或收缩任何同步行为边界。
- 不重构 `ReadBoard` 主流程。
- 不重新组织 `pendingRemoteContext`、`resumeState`、`syncAnalysisEpoch` 等状态结构。
- 不因为“代码观感更整齐”而进行结构性整理。
- 不主动删除大量测试、合并测试文件或重写测试夹具。

## 3. 设计原则

### 3.1 只清理“静态可证”的冗余

允许删除的对象必须同时满足：

1. 当前仓库内全局零引用，或在当前同步链路中静态不可达。
2. 不位于 `readboard` 协议面上。
3. 不承载 docs 已定义的边界语义。
4. 删除后不需要新增 fallback、兼容分支或行为改写。

### 3.2 协议兼容优先于代码整洁

本轮所有清理都必须默认服从 `readboard` 兼容边界：

- 不改 `parseLine(...)` 接收的消息名
- 不改消息消费时序假设
- 不改 `syncPlatform`、`roomToken`、`recordAtEnd`、`forceRebuild` 等字段语义
- 不改 `syncBoardStones(...)` 的对外决策结果集合

### 3.3 不把清理任务升级成行为修复任务

清理中若暴露出真实行为问题：

- 允许修复机械性后果，如 import、编译错误、局部未使用变量
- 不允许在同一轮里顺手改同步决策或兼容逻辑
- 一旦触碰行为边界，应停止清理并单独建模

## 4. 清理范围

### 4.1 第一批：高置信度可删除候选

文件：

- [ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java)

首批候选限定为以下对象：

- 字段
  - `firstcount`
  - `numberofcount`
- 私有 helper
  - `collectEngineSyncStones(...)`
  - `compareEngineSyncStonesByMoveNumber(...)`
  - `compareEngineSyncStonesByPosition(...)`
  - `turnColor(...)`

这些对象的共同特征是：

- 当前全仓无调用点
- 不参与本轮同步主流程
- 不属于 `readboard` 协议消费入口
- 不承载 `SNAPSHOT`、主线窗口、`ResumeState`、分析恢复等分支契约

### 4.2 第二批：只做伴随清理

伴随清理只允许包括：

- 删除第一批代码后产生的无用 import
- 删除由第一批删除直接导致的残留空白 / 注释
- 删除由第一批删除直接导致的局部未使用变量

第二批不允许扩展成：

- helper 抽取
- 状态字段重命名
- 分支重排
- `ReadBoard` 大函数拆分

### 4.3 测试层默认不清理

本轮默认不删除以下测试体系中的场景测试，不主动合并：

- [ReadBoardFoxMoveNumberParsingTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java)
- [ReadBoardResumeLifecycleTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java)
- [ReadBoardEngineResumeTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java)
- [ReadBoardSyncDecisionTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java)

原因：

- 这些测试覆盖的是协议解析、恢复生命周期、分析恢复、同步决策等不同层次，不是简单重复。
- 测试夹具中的空 override 很多是为了隔离真实 `Board` 副作用，不等于死代码。
- 当前分支仍处于同步边界收口期，保护网价值高于测试观感整洁。

因此，本轮测试层只允许做：

- 因生产死代码删除而产生的极小伴随清理

不允许做：

- 大量删测试
- 合并测试文件
- 重写夹具层次

## 5. 明确不清理的对象

以下对象即使“看起来旧”，本轮也明确不动：

- `ReadBoard.firstSync`
- `showInBoard`
- `needGenmove`
- `waitingForReadBoardLocalMoveAck`
- `hideFloadBoardBeforePlace`
- `hideFromPlace`
- `editMode`
- `parseLine(...)` 各类协议分支
- `pendingRemoteContext`
- `resumeState`
- `lastResolvedSnapshotNode`
- `syncAnalysisEpoch`
- `addStartListAll()` / `flatten()` 在其他 GUI / 引擎路径上的现有调用

这些对象要么仍在当前兼容路径中使用，要么需要结合外部交互和历史上下文判断，不能仅凭本轮同步修复已完成就当作冗余。

## 6. 执行顺序

本轮执行顺序固定为四步：

1. 只修改 [ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java)，删除第一批高置信度死代码。
2. 立即完成伴随清理，不顺手做结构整理。
3. 运行聚焦验证集。
4. 仅在验证全部通过后，复查是否还有由第一批删除暴露出来的零引用残骸；若没有则立即停止，不继续扩大范围。

## 7. 验证计划

### 7.1 验证顺序

先做编译，再跑聚焦测试，最后构建。

建议验证集：

1. 编译
2. [ReadBoardFoxMoveNumberParsingTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java)
3. [ReadBoardResumeLifecycleTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java)
4. [ReadBoardEngineResumeTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java)
5. [ReadBoardSyncDecisionTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java)
6. [SyncSnapshotRebuildPolicyTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotRebuildPolicyTest.java)
7. [SyncSnapshotClassifierTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotClassifierTest.java)
8. jar 构建

### 7.2 验证目标

验证必须证明以下行为未回退：

- `foxMoveNumber` / `recordAtEnd` 解析仍正确
- `ResumeState` 生命周期仍正确
- board-only rebuild 仍正确
- 分析恢复 epoch / target 绑定仍正确
- 主线窗口命中与导航仍正确
- `FORCE_REBUILD` 与 `HOLD` 决策仍正确

## 8. 止损与回滚规则

### 8.1 机械性问题可当场修

允许当场修复的问题：

- 编译失败
- import 未清干净
- 删除后出现的局部未使用变量
- 机械性的测试编译错误

### 8.2 行为边界问题立即停止

如果出现以下任一情况，本轮清理应立刻停止，不再扩大 diff：

- `parseLine(...)` 相关聚焦测试失败
- `syncBoardStones(...)` 决策级测试失败
- `ResumeState` / `forceRebuild` / `recordAtEnd` 相关行为回退
- 为通过测试而不得不修改同步行为

这类问题说明清理已经越过 `A` 档边界，应回退到本轮删除前状态，再单独建模。

## 9. 验收标准

本轮完成后，应满足：

- 变更主要集中在 [ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java) 的死代码删除
- `readboard` 协议字段、消息名、消费入口不变
- docs 已定义的同步边界不变
- 聚焦测试与构建全部通过
- 不需要同步更新行为契约 docs

## 10. 推荐结论

本轮采用保守型 `A1` 方案：

- 只删除静态可证的高置信度死代码
- 不做结构性整理
- 不主动清理测试体系
- 验证通过即收口

这样最符合当前目标：在保持 `readboard` 兼容与 sync 修复成果稳定的前提下，小步清理、降低后续维护噪音，而不把清理动作变成新的回归来源。
