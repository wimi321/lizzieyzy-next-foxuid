package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.Stone;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyncSnapshotDiffCheckerTest {
  private static final int BOARD_WIDTH = 2;

  @Test
  void doesNotRequireResyncWhenBoardStateSyncIsDisabled() {
    SyncSnapshotDiffChecker checker = new SyncSnapshotDiffChecker(BOARD_WIDTH);

    boolean shouldResync =
        checker.shouldResyncAfterIncrementalSync(
            false,
            false,
            new int[] {1, 0, 0, 0},
            new Stone[] {Stone.EMPTY, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
            false,
            Optional.empty());

    assertFalse(shouldResync);
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
}
