package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.Stone;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyncSnapshotDiffCheckerTest {
  private static final int BOARD_WIDTH = 2;

  @Test
  void requiresResyncWhenSnapshotStillDiffersAfterIncrementalSyncEvenIfBoardStateSyncIsDisabled() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            false,
            false,
            new int[] {1, 0, 0, 0},
            new Stone[] {Stone.EMPTY, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
            false,
            Optional.empty());

    assertTrue(shouldResync);
  }

  @Test
  void requiresResyncWhenBoardStateSyncIsEnabledAndSnapshotDiffExists() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            true,
            false,
            new int[] {1, 0, 0, 0},
            new Stone[] {Stone.EMPTY, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
            false,
            Optional.empty());

    assertTrue(shouldResync);
  }

  @Test
  void doesNotRequireResyncWhenIncrementalSyncAlreadyMatchesSnapshot() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            false,
            false,
            new int[] {1, 0, 0, 0},
            new Stone[] {Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
            false,
            Optional.empty());

    assertFalse(shouldResync);
  }

  @Test
  void doesNotRequireResyncWhenOnlyCurrentLastLocalMoveIsAheadOfSnapshot() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            false,
            false,
            new int[] {0, 0, 0, 0},
            new Stone[] {Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
            true,
            Optional.of(new int[] {0, 0}));

    assertFalse(shouldResync);
  }

  @Test
  void requiresResyncWhenSnapshotAlsoDiffersOutsideCurrentLastLocalMove() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            false,
            false,
            new int[] {0, 0, 0, 0},
            new Stone[] {Stone.BLACK, Stone.WHITE, Stone.EMPTY, Stone.EMPTY},
            true,
            Optional.of(new int[] {0, 0}));

    assertTrue(shouldResync);
  }
}
