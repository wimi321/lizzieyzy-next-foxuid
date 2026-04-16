package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SyncSnapshotRebuildPolicyTest {
  private static final int BOARD_WIDTH = 3;
  private static final int BOARD_AREA = BOARD_WIDTH * BOARD_WIDTH;

  @Test
  void findsAncestorWhenSnapshotMatchesExistingHistoryNode() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);
    BoardHistoryNode previousNode =
        root.add(
            createNode(
                stones(placement(1, 1, Stone.BLACK)), Optional.of(new int[] {1, 1}), Stone.BLACK));
    BoardHistoryNode currentNode =
        previousNode.add(
            createNode(
                stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.WHITE)),
                Optional.of(new int[] {0, 0}),
                Stone.WHITE));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode, snapshot(previousNode.getData().stones, Optional.of(new int[] {1, 1}), 3));

    assertTrue(matchedNode.isPresent());
    assertSame(previousNode, matchedNode.get());
  }

  @Test
  void doesNotMatchTransientIntermediateSnapshot() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);
    BoardHistoryNode previousNode =
        root.add(
            createNode(
                stones(
                    placement(1, 1, Stone.BLACK),
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE)),
                Optional.of(new int[] {2, 1}),
                Stone.WHITE));
    BoardHistoryNode currentNode =
        previousNode.add(
            createNode(
                stones(
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE),
                    placement(1, 2, Stone.WHITE)),
                Optional.of(new int[] {1, 2}),
                Stone.WHITE));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(
                stones(
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE)),
                Optional.empty(),
                0));

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void returnsEmptyWhenRoomSnapshotHasNoLocalHistoryMatch() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode currentNode = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(
                stones(placement(0, 0, Stone.BLACK), placement(2, 2, Stone.WHITE)),
                Optional.empty(),
                0));

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void rebuildsImmediatelyWhenCurrentPositionHasNoHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode currentNode = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);

    assertTrue(policy.shouldRebuildImmediatelyWithoutHistory(currentNode));
  }

  @Test
  void doesNotRebuildImmediatelyWhenCurrentPositionHasHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(
            createNode(
                stones(placement(1, 1, Stone.BLACK)), Optional.of(new int[] {1, 1}), Stone.BLACK));

    assertFalse(policy.shouldRebuildImmediatelyWithoutHistory(currentNode));
  }

  @Test
  void matchesCurrentNodeWhenSnapshotOmitsLastMoveMarker() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] currentStones = stones(placement(1, 1, Stone.WHITE));
    BoardHistoryNode root = createNode(new Stone[BOARD_AREA], Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(createNode(currentStones, Optional.of(new int[] {1, 1}), Stone.WHITE));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(currentNode, snapshot(currentStones, Optional.empty(), 0));

    assertTrue(matchedNode.isPresent());
    assertEquals(currentNode, matchedNode.get());
  }

  private BoardHistoryNode createNode(
      Stone[] stones, Optional<int[]> lastMove, Stone lastMoveColor) {
    return new BoardHistoryNode(
        new BoardData(
            stones,
            lastMove,
            lastMoveColor,
            true,
            new Zobrist(),
            0,
            new int[BOARD_AREA],
            0,
            0,
            50,
            0));
  }

  private Stone[] stones(Placement... placements) {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      stones[index] = Stone.EMPTY;
    }
    for (Placement placement : placements) {
      stones[stoneIndex(placement.x, placement.y)] = placement.stone;
    }
    return stones;
  }

  private int[] snapshot(Stone[] stones, Optional<int[]> marker, int markerCode) {
    int[] snapshot = new int[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      int x = index % BOARD_WIDTH;
      int y = index / BOARD_WIDTH;
      snapshot[index] = normalize(stones[stoneIndex(x, y)]);
    }
    marker.ifPresent(coords -> snapshot[coords[1] * BOARD_WIDTH + coords[0]] = markerCode);
    return snapshot;
  }

  private int normalize(Stone stone) {
    if (stone == Stone.BLACK || stone == Stone.BLACK_RECURSED) {
      return 1;
    }
    if (stone == Stone.WHITE || stone == Stone.WHITE_RECURSED) {
      return 2;
    }
    return 0;
  }

  private int stoneIndex(int x, int y) {
    return x * BOARD_WIDTH + y;
  }

  private Placement placement(int x, int y, Stone stone) {
    return new Placement(x, y, stone);
  }

  private static final class Placement {
    private final int x;
    private final int y;
    private final Stone stone;

    private Placement(int x, int y, Stone stone) {
      this.x = x;
      this.y = y;
      this.stone = stone;
    }
  }
}
