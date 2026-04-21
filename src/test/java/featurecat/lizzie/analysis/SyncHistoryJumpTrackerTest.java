package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SyncHistoryJumpTrackerTest {
  private static final int BOARD_SIZE = 3;
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
  void keepsPendingRestoreWhileTrackedRewindRemainsStable() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());
    BoardHistoryNode latestSyncEndNode = syncEndNode.add(createNode());

    tracker.remember(syncEndNode, previousNode, syncEndNode);
    tracker.remember(previousNode, previousNode, syncEndNode);
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> latestSyncEndNode);

    assertTrue(restoreTarget.isPresent());
    assertSame(latestSyncEndNode, restoreTarget.get());
  }

  @Test
  void clearsPendingRestoreWhenLocalNavigationOccurs() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());
    BoardHistoryNode latestSyncEndNode = syncEndNode.add(createNode());

    tracker.remember(syncEndNode, previousNode, syncEndNode);
    tracker.onLocalNavigation();
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
  void clearsPendingRewindsOnClear() {
    SyncHistoryJumpTracker tracker = new SyncHistoryJumpTracker();
    BoardHistoryNode root = createNode();
    BoardHistoryNode previousNode = root.add(createNode());
    BoardHistoryNode syncEndNode = previousNode.add(createNode());

    tracker.remember(previousNode, syncEndNode);
    tracker.clear();
    Optional<BoardHistoryNode> restoreTarget =
        tracker.consumeStableSnapshotTarget(previousNode, () -> syncEndNode);

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

  @Test
  void crossThreadStateAccessRemainsSynchronized() throws NoSuchMethodException {
    Method rememberByMatch =
        SyncHistoryJumpTracker.class.getDeclaredMethod(
            "remember", BoardHistoryNode.class, BoardHistoryNode.class);
    Method rememberByCurrent =
        SyncHistoryJumpTracker.class.getDeclaredMethod(
            "remember", BoardHistoryNode.class, BoardHistoryNode.class, BoardHistoryNode.class);
    Method consume =
        SyncHistoryJumpTracker.class.getDeclaredMethod(
            "consumeStableSnapshotTarget",
            BoardHistoryNode.class,
            java.util.function.Supplier.class);
    Method onLocalNavigation = SyncHistoryJumpTracker.class.getDeclaredMethod("onLocalNavigation");
    Method clear = SyncHistoryJumpTracker.class.getDeclaredMethod("clear");

    assertTrue(Modifier.isSynchronized(rememberByMatch.getModifiers()));
    assertTrue(Modifier.isSynchronized(rememberByCurrent.getModifiers()));
    assertTrue(Modifier.isSynchronized(consume.getModifiers()));
    assertTrue(Modifier.isSynchronized(onLocalNavigation.getModifiers()));
    assertTrue(Modifier.isSynchronized(clear.getModifiers()));
  }

  private BoardHistoryNode createNode() {
    return new BoardHistoryNode(BoardData.empty(BOARD_SIZE, BOARD_SIZE));
  }

  private Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      stones[index] = Stone.EMPTY;
    }
    return stones;
  }
}
