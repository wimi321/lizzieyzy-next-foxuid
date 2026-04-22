# ReadBoard Sync High-Confidence Cleanup Implementation Plan

> **For agentic workers:** Choose the execution workflow explicitly. Use `superpowers:executing-plans` when higher-priority instructions prefer inline execution or tasks are tightly coupled. Use `superpowers:subagent-driven-development` only when tasks are truly independent and delegation is explicitly desired. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 `ReadBoard` 中当前全仓零引用、且不影响 `readboard -> lizzieyzy-next` 协议与同步边界的高置信度死代码，并用现有聚焦测试与构建确认行为零回退。

**Architecture:** 本轮只在 [src/main/java/featurecat/lizzie/analysis/ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java) 做保守清理，不调整 `parseLine(...)`、`syncBoardStones(...)`、`ResumeState`、`pendingRemoteContext`、主线窗口同步或 `FORCE_REBUILD` 逻辑。测试层默认不删场景，只复用现有聚焦测试矩阵证明这次删除是纯行为保持型清理。

**Tech Stack:** Java 8+, Maven Surefire, existing `ReadBoard` analysis tests, project build script `./scripts/build_jar.sh`

---

## File Map

- **Modify:** [src/main/java/featurecat/lizzie/analysis/ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java)
  - 删除零引用字段 `firstcount`、`numberofcount`
  - 删除零引用 helper `collectEngineSyncStones(...)`、`compareEngineSyncStonesByMoveNumber(...)`、`compareEngineSyncStonesByPosition(...)`、`turnColor(...)`
  - 仅做伴随 import / 空白清理，不改变同步行为
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java)
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java)
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java)
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java)
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/SyncSnapshotRebuildPolicyTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotRebuildPolicyTest.java)
- **Verify only:** [src/test/java/featurecat/lizzie/analysis/SyncSnapshotClassifierTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotClassifierTest.java)

## Guardrails

- 不修改 `D:\\dev\\weiqi\\readboard`
- 不修改 `parseLine(...)` 消息名与协议字段消费
- 不修改 `syncBoardStones(...)` 的决策顺序
- 不修改 `pendingRemoteContext` / `resumeState` / `syncAnalysisEpoch`
- 不主动删除测试文件或重写测试夹具
- 若任一聚焦测试暴露行为回退，立即停止，不把本轮清理升级为行为修复

### Task 1: Freeze the Cleanup Scope Before Editing

**Files:**
- Modify: none
- Verify: [src/main/java/featurecat/lizzie/analysis/ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java)

- [ ] **Step 1: Prove the candidate fields and helpers are zero-reference**

Run:

```bash
rg -n "collectEngineSyncStones\\(|compareEngineSyncStonesByMoveNumber\\(|compareEngineSyncStonesByPosition\\(|turnColor\\(" -S src/main/java src/test/java
rg -n "firstcount|numberofcount" -S src/main/java src/test/java
```

Expected:

```text
Only ReadBoard.java should be reported for these candidates.
No other production or test file should reference them.
```

- [ ] **Step 2: Capture the current working tree before cleanup**

Run:

```bash
git status --short
git diff --stat -- src/main/java/featurecat/lizzie/analysis/ReadBoard.java
```

Expected:

```text
You can see whether ReadBoard.java already has unrelated local edits.
Do not touch any file other than ReadBoard.java for the cleanup itself.
```

- [ ] **Step 3: Run the smallest protocol-level baseline test before editing**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.ReadBoardFoxMoveNumberParsingTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0.
This proves foxMoveNumber / recordAtEnd parsing is green before the dead-code removal.
```

### Task 2: Remove the Zero-Reference Dead Code From ReadBoard

**Files:**
- Modify: [src/main/java/featurecat/lizzie/analysis/ReadBoard.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/main/java/featurecat/lizzie/analysis/ReadBoard.java)

- [ ] **Step 1: Delete the unused ReadBoard fields**

Update the field block near the top of `ReadBoard` so it no longer contains `firstcount` or `numberofcount`.

Use this resulting shape:

```java
  public boolean isLoaded = false;
  private int version = 220430;
  public String currentEnginename = "";
  private int port = -1;

  public boolean firstSync = true;
  private ReadBoardStream readBoardStream;
  private Socket socket;
  private ServerSocket s;
```

- [ ] **Step 2: Delete the unused helper methods below snapshot rebuild**

After `syncEngineToRebuiltSnapshot(...)`, remove:

```java
  private List<EngineSyncStone> collectEngineSyncStones(BoardData data) { ... }

  private int compareEngineSyncStonesByMoveNumber(EngineSyncStone left, EngineSyncStone right) { ... }

  private int compareEngineSyncStonesByPosition(EngineSyncStone left, EngineSyncStone right) { ... }

  private Stone turnColor(boolean blackTurn) { ... }
```

The resulting transition in the file should look like:

```java
  private void syncEngineToRebuiltSnapshot(BoardHistoryNode rebuiltNode) {
    if (!isReadBoardAnalysisEngineAvailable()) {
      return;
    }
    BoardData data = rebuiltNode.getData();
    Lizzie.leelaz.clear();
    if (!ExactSnapshotEngineRestore.restoreIfNeeded(Lizzie.leelaz, data)) {
      throw new IllegalStateException("Snapshot rebuild must sync through snapshot data.");
    }
  }

  private void resetActiveSyncState() {
    conflictTracker.clear();
    historyJumpTracker.clear();
    waitingForReadBoardLocalMoveAck = false;
    pendingRemoteContext = SyncRemoteContext.generic(false);
    awaitingFirstSyncFrame = true;
    invalidatePendingSyncAnalysisResume();
  }
```

- [ ] **Step 3: Remove any imports or whitespace left behind by the deletion**

The import section should still contain only imports used by the remaining code. Do not add or remove anything else.

Target outcome:

```java
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.ExtraStones;
import featurecat.lizzie.rules.Movelist;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
```

- [ ] **Step 4: Compile immediately after the deletion**

Run:

```bash
mvn -q -DskipTests compile
```

Expected:

```text
Exit code 0.
No new compiler errors and no need to touch any file other than ReadBoard.java.
```

- [ ] **Step 5: Inspect the diff to confirm the cleanup stayed inside scope**

Run:

```bash
git diff -- src/main/java/featurecat/lizzie/analysis/ReadBoard.java
git diff --name-only
```

Expected:

```text
The functional cleanup diff should stay in ReadBoard.java.
The cleanup should not accidentally modify protocol tests or other production files.
```

### Task 3: Run Focused Regression Verification

**Files:**
- Modify: none
- Test: [src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardFoxMoveNumberParsingTest.java)
- Test: [src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardResumeLifecycleTest.java)
- Test: [src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardEngineResumeTest.java)
- Test: [src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/ReadBoardSyncDecisionTest.java)
- Test: [src/test/java/featurecat/lizzie/analysis/SyncSnapshotRebuildPolicyTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotRebuildPolicyTest.java)
- Test: [src/test/java/featurecat/lizzie/analysis/SyncSnapshotClassifierTest.java](/mnt/d/dev/weiqi/lizzieyzy-next/src/test/java/featurecat/lizzie/analysis/SyncSnapshotClassifierTest.java)

- [ ] **Step 1: Re-run the protocol parsing test**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.ReadBoardFoxMoveNumberParsingTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0.
This confirms foxMoveNumber / recordAtEnd parsing was untouched.
```

- [ ] **Step 2: Re-run the resume lifecycle test**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.ReadBoardResumeLifecycleTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0.
This confirms ResumeState invalidation and suppression behavior still work.
```

- [ ] **Step 3: Re-run the analysis resume test**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.ReadBoardEngineResumeTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0.
This confirms board-only rebuild and epoch-bound analysis resume were untouched.
```

- [ ] **Step 4: Re-run the full sync decision regression test**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.ReadBoardSyncDecisionTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0.
This confirms mainline window navigation, HOLD/FORCE_REBUILD, and FOX recovery behavior did not regress.
```

- [ ] **Step 5: Re-run the rebuild-policy and classifier tests**

Run:

```bash
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.SyncSnapshotRebuildPolicyTest -DfailIfNoTests=false surefire:test
timeout 60s mvn -q -Dtest=featurecat.lizzie.analysis.SyncSnapshotClassifierTest -DfailIfNoTests=false surefire:test
```

Expected:

```text
Exit code 0 for both commands.
This confirms marker jitter handling, conflict keys, and snapshot delta classification remain unchanged.
```

### Task 4: Final Build and Safety Review

**Files:**
- Modify: none

- [ ] **Step 1: Build the jar after tests are green**

Run:

```bash
./scripts/build_jar.sh
```

Expected:

```text
Build succeeds and produces the project jars under target/.
```

- [ ] **Step 2: Check for unrelated file churn after the build**

Run:

```bash
git status --short
git diff --stat
```

Expected:

```text
No new functional file changes outside the intended cleanup.
If the build introduces unrelated fmt-only churn, stop and inspect before keeping it.
```

- [ ] **Step 3: Manually inspect the cleanup boundaries before handing off**

Run:

```bash
git diff -- src/main/java/featurecat/lizzie/analysis/ReadBoard.java
```

Expected:

```text
Only the zero-reference fields/helpers and their direct import/whitespace fallout were removed.
No protocol branch, no sync decision branch, no resume-state logic, and no readboard compatibility path should be altered.
```

- [ ] **Step 4: Stop instead of expanding scope**

If every command above is green, stop here. Do not continue into “while we are here” refactors. Do not delete tests. Do not touch `D:\\dev\\weiqi\\readboard`.

The final acceptable state is:

```text
ReadBoard dead code removed.
Focused regression tests pass.
Jar build succeeds.
Cleanup scope stays narrow.
```
