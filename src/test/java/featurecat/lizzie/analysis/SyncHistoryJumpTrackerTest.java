package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SyncHistoryJumpTrackerTest {
  private static final int BOARD_AREA = 9;

  @Test
  void restoresMainEndAfterMatchedRewind() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());

    tracker.remember(previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> syncEndNode);

    assertTrue(restoreTarget.isPresent());
    assertSame(syncEndNode, restoreTarget.get());
  }

  @Test
  void restoresLatestMainEndWhenHistoryExtendsDuringTrackedRewind() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());
    BoardHistoryNode latestSyncEndNode = syncEndNode.add(createNode());

    tracker.remember(previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> latestSyncEndNode);

    assertTrue(restoreTarget.isPresent());
    assertSame(latestSyncEndNode, restoreTarget.get());
  }

  @Test
  void doesNotArmRestoreWhenUserAlreadyViewingMatchedNode() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());
    BoardHistoryNode latestSyncEndNode = syncEndNode.add(createNode());

    tracker.remember(previousNode, previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> latestSyncEndNode);

    assertFalse(restoreTarget.isPresent());
  }

  @Test
  void doesNotRestoreWhenUserAlreadyReturnedToMainEnd() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());

    tracker.remember(previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(syncEndNode, () -> syncEndNode);

    assertFalse(restoreTarget.isPresent());
  }

  @Test
  void clearsPendingRewindWhenCurrentNodeChangedOutsideTrackedRewind() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());
    BoardHistoryNode otherNode = root;

    tracker.remember(previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(otherNode, () -> syncEndNode);
    Optional<BoardHistoryNode> staleRestoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> syncEndNode);

    assertFalse(restoreTarget.isPresent());
    assertFalse(staleRestoreTarget.isPresent());
  }

  private BoardHistoryNode createNode() {
    return new BoardHistoryNode(
        new BoardData(
            emptyStones(),
            Optional.empty(),
            Stone.EMPTY,
            true,
            new Zobrist(),
            0,
            new int[BOARD_AREA],
            0,
            0,
            50,
            0));
  }

  private Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      stones[index] = Stone.EMPTY;
    }
    return stones;
  }
}
