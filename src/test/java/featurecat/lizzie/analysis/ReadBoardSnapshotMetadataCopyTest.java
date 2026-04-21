package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ReadBoardSnapshotMetadataCopyTest {
  private static final int BOARD_SIZE = 3;
  private static final int BOARD_AREA = BOARD_SIZE * BOARD_SIZE;
  private static final String SNAPSHOT_COMMENT = "setup snapshot comment";

  @Test
  void buildSnapshotHistoryKeepsSetupCommentForMetadataRebuild() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Board.boardWidth = BOARD_SIZE;
    Board.boardHeight = BOARD_SIZE;
    try {
      Stone[] target =
          stones(
              placement(0, 0, Stone.BLACK),
              placement(1, 0, Stone.WHITE),
              placement(2, 2, Stone.BLACK));
      BoardHistoryList previousHistory =
          new BoardHistoryList(snapshotNode(target, Optional.empty(), Stone.EMPTY, true, 2));
      BoardHistoryNode syncStartNode = previousHistory.getCurrentHistoryNode();
      seedSetupMetadata(syncStartNode);

      int[] snapshotCodes = snapshot(target, Optional.empty(), Stone.EMPTY);
      SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
      SyncSnapshotClassifier.SnapshotDelta delta =
          classifier.summarizeDelta(syncStartNode.getData().stones, snapshotCodes);

      BoardHistoryList rebuiltHistory =
          invokeBuildSnapshotHistory(
              allocate(ReadBoard.class),
              previousHistory,
              syncStartNode,
              snapshotCodes,
              delta,
              OptionalInt.of(58));

      BoardHistoryNode rebuiltSnapshotNode = rebuiltHistory.getCurrentHistoryNode();
      assertSetupMetadata(rebuiltSnapshotNode);
      assertEquals(58, rebuiltSnapshotNode.getData().moveNumber);
      assertTrue(rebuiltSnapshotNode.getData().blackToPlay);
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
    }
  }

  @Test
  void buildSnapshotHistoryKeepsSetupCommentForForceRebuild() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Board.boardWidth = BOARD_SIZE;
    Board.boardHeight = BOARD_SIZE;
    try {
      Stone[] anchorStones = stones(placement(0, 0, Stone.BLACK), placement(2, 2, Stone.BLACK));
      BoardHistoryList previousHistory =
          new BoardHistoryList(snapshotNode(anchorStones, Optional.empty(), Stone.EMPTY, false, 7));
      BoardHistoryNode snapshotAnchor = previousHistory.getCurrentHistoryNode();
      seedSetupMetadata(snapshotAnchor);
      previousHistory.add(
          moveNode(
              stones(
                  placement(0, 0, Stone.BLACK),
                  placement(1, 1, Stone.WHITE),
                  placement(2, 2, Stone.BLACK)),
              new int[] {1, 1},
              Stone.WHITE,
              true,
              8));
      BoardHistoryNode syncStartNode = previousHistory.getCurrentHistoryNode();

      Stone[] target =
          stones(
              placement(0, 0, Stone.BLACK),
              placement(1, 0, Stone.WHITE),
              placement(2, 2, Stone.BLACK));
      int[] snapshotCodes = snapshot(target, Optional.of(new int[] {1, 0}), Stone.WHITE);
      SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
      SyncSnapshotClassifier.SnapshotDelta delta =
          classifier.summarizeDelta(syncStartNode.getData().stones, snapshotCodes);

      BoardHistoryList rebuiltHistory =
          invokeBuildSnapshotHistory(
              allocate(ReadBoard.class),
              previousHistory,
              syncStartNode,
              snapshotCodes,
              delta,
              OptionalInt.empty());

      BoardHistoryNode rebuiltSnapshotNode = rebuiltHistory.getCurrentHistoryNode();
      assertSetupMetadata(rebuiltSnapshotNode);
      assertTrue(rebuiltSnapshotNode.getData().lastMove.isPresent());
      assertArrayEquals(new int[] {1, 0}, rebuiltSnapshotNode.getData().lastMove.get());
      assertEquals(Stone.WHITE, rebuiltSnapshotNode.getData().lastMoveColor);
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
    }
  }

  private static void seedSetupMetadata(BoardHistoryNode node) {
    BoardData data = node.getData();
    data.addProperty("AB", "aa");
    data.addProperty("AW", "ba");
    data.addProperty("AE", "cb");
    data.comment = SNAPSHOT_COMMENT;
    node.addExtraStones(2, 2, true);
    node.setRemovedStone();
  }

  private static void assertSetupMetadata(BoardHistoryNode node) {
    assertEquals("aa", node.getData().getProperty("AB"));
    assertEquals("ba", node.getData().getProperty("AW"));
    assertEquals("cb", node.getData().getProperty("AE"));
    assertEquals(SNAPSHOT_COMMENT, node.getData().comment);
    assertTrue(node.hasRemovedStone());
    assertNotNull(node.extraStones);
    assertEquals(1, node.extraStones.size());
    assertEquals(2, node.extraStones.get(0).x);
    assertEquals(2, node.extraStones.get(0).y);
    assertTrue(node.extraStones.get(0).isBlack);
  }

  private static BoardHistoryList invokeBuildSnapshotHistory(
      ReadBoard readBoard,
      BoardHistoryList previousHistory,
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber)
      throws Exception {
    Method method =
        ReadBoard.class.getDeclaredMethod(
            "buildSnapshotHistory",
            BoardHistoryList.class,
            BoardHistoryNode.class,
            int[].class,
            SyncSnapshotClassifier.SnapshotDelta.class,
            OptionalInt.class);
    method.setAccessible(true);
    return (BoardHistoryList)
        method.invoke(
            readBoard, previousHistory, syncStartNode, snapshotCodes, snapshotDelta, foxMoveNumber);
  }

  private static int[] snapshot(Stone[] stones, Optional<int[]> lastMove, Stone lastMoveColor) {
    int[] snapshot = new int[BOARD_AREA];
    for (int x = 0; x < BOARD_SIZE; x++) {
      for (int y = 0; y < BOARD_SIZE; y++) {
        Stone stone = stones[stoneIndex(x, y)];
        snapshot[y * BOARD_SIZE + x] = stone.isBlack() ? 1 : stone.isWhite() ? 2 : 0;
      }
    }
    if (lastMove.isPresent()) {
      int[] coords = lastMove.get();
      snapshot[coords[1] * BOARD_SIZE + coords[0]] = lastMoveColor.isBlack() ? 3 : 4;
    }
    return snapshot;
  }

  private static BoardData snapshotNode(
      Stone[] stones,
      Optional<int[]> lastMove,
      Stone lastMoveColor,
      boolean blackToPlay,
      int moveNumber) {
    return BoardData.snapshot(
        stones.clone(),
        lastMove,
        lastMoveColor,
        blackToPlay,
        zobrist(stones),
        moveNumber,
        new int[BOARD_AREA],
        0,
        0,
        50,
        0);
  }

  private static BoardData moveNode(
      Stone[] stones, int[] lastMove, Stone color, boolean blackToPlay, int moveNumber) {
    return BoardData.move(
        stones.clone(),
        lastMove,
        color,
        blackToPlay,
        zobrist(stones),
        moveNumber,
        new int[BOARD_AREA],
        0,
        0,
        50,
        0);
  }

  private static Zobrist zobrist(Stone[] stones) {
    Zobrist zobrist = new Zobrist();
    for (int x = 0; x < BOARD_SIZE; x++) {
      for (int y = 0; y < BOARD_SIZE; y++) {
        Stone stone = stones[stoneIndex(x, y)];
        if (!stone.isEmpty()) {
          zobrist.toggleStone(x, y, stone);
        }
      }
    }
    return zobrist;
  }

  private static Stone[] stones(Placement... placements) {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < stones.length; index++) {
      stones[index] = Stone.EMPTY;
    }
    for (Placement placement : placements) {
      stones[stoneIndex(placement.x, placement.y)] = placement.color;
    }
    return stones;
  }

  private static int stoneIndex(int x, int y) {
    return Board.getIndex(x, y);
  }

  private static Placement placement(int x, int y, Stone color) {
    return new Placement(x, y, color);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private record Placement(int x, int y, Stone color) {}

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE;

    static {
      try {
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        UNSAFE = (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }
}
