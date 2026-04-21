package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

public class SyncSnapshotRebuildPolicyTest {
  private static final int BOARD_WIDTH = 3;
  private static final int BOARD_AREA = BOARD_WIDTH * BOARD_WIDTH;

  @Test
  void matchesCurrentNodeWhenMarkedSnapshotMatchesSyncEnd() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode previousNode =
        root.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK)), new int[] {1, 1}, Stone.BLACK, false, 1));
    BoardHistoryNode currentNode =
        previousNode.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.WHITE)),
                new int[] {0, 0},
                Stone.WHITE,
                true,
                2));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(currentNode.getData().stones, Optional.of(new int[] {0, 0}), 4),
            OptionalInt.empty());

    assertTrue(matchedNode.isPresent());
    assertSame(currentNode, matchedNode.get());
  }

  @Test
  void matchesCurrentSnapshotNodeWhenMarkedSnapshotMatchesRebuiltRoot() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] currentStones = stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.WHITE));
    BoardHistoryNode currentNode =
        createNode(currentStones, Optional.of(new int[] {0, 0}), Stone.WHITE);

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(currentStones, Optional.of(new int[] {0, 0}), 4),
            OptionalInt.empty());

    assertTrue(matchedNode.isPresent());
    assertSame(currentNode, matchedNode.get());
  }

  @Test
  void doesNotMatchAncestorWhenMarkedSnapshotOnlyMatchesEarlierHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode previousNode =
        root.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK)), new int[] {1, 1}, Stone.BLACK, false, 1));
    BoardHistoryNode currentNode =
        previousNode.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.WHITE)),
                new int[] {0, 0},
                Stone.WHITE,
                true,
                2));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(previousNode.getData().stones, Optional.of(new int[] {1, 1}), 3),
            OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void doesNotMatchTransientIntermediateSnapshot() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode previousNode =
        root.add(
            createMoveHistoryNode(
                stones(
                    placement(1, 1, Stone.BLACK),
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE)),
                new int[] {2, 1},
                Stone.WHITE,
                true,
                4));
    BoardHistoryNode currentNode =
        previousNode.add(
            createMoveHistoryNode(
                stones(
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE),
                    placement(1, 2, Stone.WHITE)),
                new int[] {1, 2},
                Stone.WHITE,
                false,
                5));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(
                stones(
                    placement(0, 1, Stone.WHITE),
                    placement(1, 0, Stone.WHITE),
                    placement(2, 1, Stone.WHITE)),
                Optional.empty(),
                0),
            OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void returnsEmptyWhenRoomSnapshotHasNoCurrentNodeMatch() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode currentNode = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(
                stones(placement(0, 0, Stone.BLACK), placement(2, 2, Stone.WHITE)),
                Optional.empty(),
                0),
            OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void returnsEmptyWhenSnapshotContainsMultipleMarkers() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK)), new int[] {1, 1}, Stone.BLACK, false, 1));
    int[] snapshot = snapshot(currentNode.getData().stones, Optional.of(new int[] {1, 1}), 3);
    snapshot[0] = 4;

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(currentNode, snapshot, OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void returnsEmptyWhenSnapshotMarkerColorDoesNotMatchCurrentNode() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK)), new int[] {1, 1}, Stone.BLACK, false, 1));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode,
            snapshot(currentNode.getData().stones, Optional.of(new int[] {1, 1}), 4),
            OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void rebuildsImmediatelyWhenCurrentPositionHasNoHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode currentNode = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);

    assertTrue(policy.shouldRebuildImmediatelyWithoutHistory(currentNode));
  }

  @Test
  void doesNotRebuildImmediatelyWhenCurrentPositionHasHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK)), new int[] {1, 1}, Stone.BLACK, false, 1));

    assertFalse(policy.shouldRebuildImmediatelyWithoutHistory(currentNode));
  }

  @Test
  void matchesCurrentNodeWhenSnapshotOmitsLastMoveMarker() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] currentStones = stones(placement(1, 1, Stone.WHITE));
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode currentNode =
        root.add(createMoveHistoryNode(currentStones, new int[] {1, 1}, Stone.WHITE, true, 1));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode, snapshot(currentStones, Optional.empty(), 0), OptionalInt.empty());

    assertTrue(matchedNode.isPresent());
    assertSame(currentNode, matchedNode.get());
  }

  @Test
  void matchesCurrentPassNodeWhenMarkerlessSnapshotEqualsSamePosition() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] repeatedStones = stones(placement(1, 1, Stone.BLACK));
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode moveOne =
        root.add(createMoveHistoryNode(repeatedStones, new int[] {1, 1}, Stone.BLACK, false, 1));
    BoardHistoryNode passNode =
        moveOne.add(createPassHistoryNode(repeatedStones, Stone.WHITE, true, 2));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            passNode, snapshot(repeatedStones, Optional.empty(), 0), OptionalInt.of(9));

    assertTrue(matchedNode.isPresent());
    assertSame(passNode, matchedNode.get());
  }

  @Test
  void doesNotMatchEarlierNodeWhenMarkerlessSnapshotOnlyMatchesHistory() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] repeatedStones = stones(placement(1, 1, Stone.BLACK));
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode moveOne =
        root.add(createMoveHistoryNode(repeatedStones, new int[] {1, 1}, Stone.BLACK, false, 1));
    BoardHistoryNode currentNode =
        moveOne.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.WHITE)),
                new int[] {0, 0},
                Stone.WHITE,
                true,
                2));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode, snapshot(repeatedStones, Optional.empty(), 0), OptionalInt.empty());

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void doesNotUseFoxMoveNumberToRecoverEarlierMarkerlessNode() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] repeatedStones = stones(placement(1, 1, Stone.BLACK));
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode moveOne =
        root.add(createMoveHistoryNode(repeatedStones, new int[] {1, 1}, Stone.BLACK, false, 1));
    BoardHistoryNode currentNode =
        moveOne.add(
            createMoveHistoryNode(
                stones(placement(1, 1, Stone.BLACK), placement(0, 0, Stone.BLACK)),
                new int[] {0, 0},
                Stone.BLACK,
                false,
                3));

    Optional<BoardHistoryNode> matchedNode =
        policy.findMatchingHistoryNode(
            currentNode, snapshot(repeatedStones, Optional.empty(), 0), OptionalInt.of(1));

    assertFalse(matchedNode.isPresent());
  }

  @Test
  void neverResolvesAdjacentMatchFromLastResolvedNode() {
    SyncSnapshotRebuildPolicy policy = new SyncSnapshotRebuildPolicy(BOARD_WIDTH);
    Stone[] repeatedStones = stones(placement(1, 1, Stone.BLACK));
    BoardHistoryNode root = createNode(emptyStones(), Optional.empty(), Stone.EMPTY);
    BoardHistoryNode moveOne =
        root.add(createMoveHistoryNode(repeatedStones, new int[] {1, 1}, Stone.BLACK, false, 1));

    Optional<BoardHistoryNode> matchedNode =
        policy.findAdjacentMatchFromLastResolvedNode(
            moveOne, snapshot(repeatedStones, Optional.empty(), 0), OptionalInt.of(1));

    assertFalse(matchedNode.isPresent());
  }

  private BoardHistoryNode createNode(
      Stone[] stones, Optional<int[]> lastMove, Stone lastMoveColor) {
    return new BoardHistoryNode(
        BoardData.snapshot(
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

  private BoardData createMoveNode(
      Stone[] stones, int[] lastMove, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return BoardData.move(
        stones,
        lastMove,
        lastMoveColor,
        blackToPlay,
        new Zobrist(),
        moveNumber,
        new int[BOARD_AREA],
        0,
        0,
        50,
        0);
  }

  private BoardHistoryNode createMoveHistoryNode(
      Stone[] stones, int[] lastMove, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return new BoardHistoryNode(
        createMoveNode(stones, lastMove, lastMoveColor, blackToPlay, moveNumber));
  }

  private BoardData createPassNode(
      Stone[] stones, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return BoardData.pass(
        stones,
        lastMoveColor,
        blackToPlay,
        new Zobrist(),
        moveNumber,
        new int[BOARD_AREA],
        0,
        0,
        50,
        0);
  }

  private BoardHistoryNode createPassHistoryNode(
      Stone[] stones, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return new BoardHistoryNode(createPassNode(stones, lastMoveColor, blackToPlay, moveNumber));
  }

  private Stone[] stones(Placement... placements) {
    Stone[] stones = emptyStones();
    for (Placement placement : placements) {
      stones[stoneIndex(placement.x, placement.y)] = placement.stone;
    }
    return stones;
  }

  private Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      stones[index] = Stone.EMPTY;
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
