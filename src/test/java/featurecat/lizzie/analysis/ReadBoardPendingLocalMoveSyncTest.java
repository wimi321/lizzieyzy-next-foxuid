package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadBoardPendingLocalMoveSyncTest {
  @Test
  void clearsPendingLocalMoveAfterSnapshotCatchesUpToLocalBoard() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Board previousBoard = Lizzie.board;
    LizzieFrame previousFrame = Lizzie.frame;
    try {
      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Zobrist.init();
      ReadBoard readBoard = allocate(ReadBoard.class);
      Board board = allocate(Board.class);
      board.setHistory(
          moveHistory(
              stones(Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY),
              new int[] {0, 0},
              Stone.BLACK,
              false,
              1));
      Lizzie.board = board;
      LizzieFrame frame = allocate(LizzieFrame.class);
      frame.bothSync = true;
      Lizzie.frame = frame;
      readBoard.lastMovePlayByLizzie = true;

      invokeAcknowledgeLocalMove(
          readBoard,
          new Stone[] {Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
          new int[] {1, 0, 0, 0});

      assertFalse(
          readBoard.lastMovePlayByLizzie,
          "once the remote snapshot matches the local board, pending local-move ignore state should be cleared.");
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.board = previousBoard;
      Lizzie.frame = previousFrame;
    }
  }

  @Test
  void keepsPendingLocalMoveWhileRemoteSnapshotIsStillBehind() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Board previousBoard = Lizzie.board;
    LizzieFrame previousFrame = Lizzie.frame;
    try {
      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Zobrist.init();
      ReadBoard readBoard = allocate(ReadBoard.class);
      Board board = allocate(Board.class);
      board.setHistory(
          moveHistory(
              stones(Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY),
              new int[] {0, 0},
              Stone.BLACK,
              false,
              1));
      Lizzie.board = board;
      LizzieFrame frame = allocate(LizzieFrame.class);
      frame.bothSync = true;
      Lizzie.frame = frame;
      readBoard.lastMovePlayByLizzie = true;

      invokeAcknowledgeLocalMove(
          readBoard,
          new Stone[] {Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY},
          new int[] {0, 0, 0, 0});

      assertTrue(
          readBoard.lastMovePlayByLizzie,
          "the ignore state must remain while the remote board still has not caught up to the local move.");
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.board = previousBoard;
      Lizzie.frame = previousFrame;
    }
  }

  @Test
  void placeCompleteStopsIgnoringMissingLastLocalMove() throws Exception {
    LizzieFrame previousFrame = Lizzie.frame;
    try {
      ReadBoard readBoard = allocate(ReadBoard.class);
      LizzieFrame frame = allocate(LizzieFrame.class);
      frame.bothSync = true;
      Lizzie.frame = frame;

      invokeStartTrackingLocalMove(readBoard);

      assertTrue(
          invokeShouldIgnoreCurrentLastLocalMove(readBoard),
          "before readboard confirms the injected move, a trailing snapshot may still be waiting to catch up.");

      invokeMarkLocalMoveCommandCompleted(readBoard);

      assertFalse(
          invokeShouldIgnoreCurrentLastLocalMove(readBoard),
          "once readboard reports placeComplete, a later snapshot missing that move should no longer be treated as pending lag.");
    } finally {
      Lizzie.frame = previousFrame;
    }
  }

  @Test
  void snapshotMarkerStillRequiresResyncWhenRemoteSnapshotMissesThatStone() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Config previousConfig = Lizzie.config;
    Board previousBoard = Lizzie.board;
    LizzieFrame previousFrame = Lizzie.frame;
    try {
      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Zobrist.init();

      ReadBoard readBoard = allocate(ReadBoard.class);
      Config config = allocate(Config.class);
      config.alwaysSyncBoardStat = false;
      Lizzie.config = config;

      Board board = allocate(Board.class);
      Stone[] localStones = stones(Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY);
      board.setHistory(snapshotMarkerHistory(localStones, new int[] {0, 0}, Stone.BLACK, false, 1));
      Lizzie.board = board;

      LizzieFrame frame = allocate(LizzieFrame.class);
      frame.bothSync = true;
      Lizzie.frame = frame;
      invokeStartTrackingLocalMove(readBoard);

      boolean shouldResync =
          invokeShouldResyncAfterIncrementalSync(readBoard, localStones, new int[] {0, 0, 0, 0});

      assertTrue(
          shouldResync,
          "a SNAPSHOT marker cannot stand in for a real pending local move, so a missing remote stone must still force resync.");
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.config = previousConfig;
      Lizzie.board = previousBoard;
      Lizzie.frame = previousFrame;
    }
  }

  @Test
  void snapshotMarkerDoesNotAcknowledgePendingLocalMove() throws Exception {
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;
    Board previousBoard = Lizzie.board;
    LizzieFrame previousFrame = Lizzie.frame;
    try {
      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Zobrist.init();

      ReadBoard readBoard = allocate(ReadBoard.class);
      Board board = allocate(Board.class);
      Stone[] localStones = stones(Stone.BLACK, Stone.EMPTY, Stone.EMPTY, Stone.EMPTY);
      board.setHistory(snapshotMarkerHistory(localStones, new int[] {0, 0}, Stone.BLACK, false, 1));
      Lizzie.board = board;

      LizzieFrame frame = allocate(LizzieFrame.class);
      frame.bothSync = true;
      Lizzie.frame = frame;
      invokeStartTrackingLocalMove(readBoard);

      invokeAcknowledgeLocalMove(readBoard, localStones, new int[] {1, 0, 0, 0});

      assertTrue(
          readBoard.lastMovePlayByLizzie,
          "snapshot metadata cannot acknowledge a pending local move before a real MOVE node exists.");
    } finally {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.board = previousBoard;
      Lizzie.frame = previousFrame;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static boolean invokeShouldResyncAfterIncrementalSync(
      ReadBoard readBoard, Stone[] stones, int[] snapshotCodes) throws Exception {
    Method method =
        ReadBoard.class.getDeclaredMethod(
            "shouldResyncAfterIncrementalSync", Stone[].class, int[].class);
    method.setAccessible(true);
    return (boolean) method.invoke(readBoard, stones, snapshotCodes);
  }

  private static void invokeAcknowledgeLocalMove(
      ReadBoard readBoard, Stone[] stones, int[] snapshotCodes) throws Exception {
    Method method =
        ReadBoard.class.getDeclaredMethod(
            "acknowledgeLocalMoveIfSnapshotCaughtUp", Stone[].class, int[].class);
    method.setAccessible(true);
    method.invoke(readBoard, stones, snapshotCodes);
  }

  private static void invokeStartTrackingLocalMove(ReadBoard readBoard) throws Exception {
    Method method = ReadBoard.class.getDeclaredMethod("startTrackingLocalMoveFromLizzie");
    method.setAccessible(true);
    method.invoke(readBoard);
  }

  private static void invokeMarkLocalMoveCommandCompleted(ReadBoard readBoard) throws Exception {
    Method method = ReadBoard.class.getDeclaredMethod("markLocalMoveCommandCompleted");
    method.setAccessible(true);
    method.invoke(readBoard);
  }

  private static boolean invokeShouldIgnoreCurrentLastLocalMove(ReadBoard readBoard)
      throws Exception {
    Method method = ReadBoard.class.getDeclaredMethod("shouldIgnoreCurrentLastLocalMove");
    method.setAccessible(true);
    return (boolean) method.invoke(readBoard);
  }

  private static BoardHistoryList snapshotMarkerHistory(
      Stone[] stones, int[] marker, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return new BoardHistoryList(
        BoardData.snapshot(
            stones.clone(),
            Optional.of(marker),
            lastMoveColor,
            blackToPlay,
            zobrist(stones),
            moveNumber,
            new int[stones.length],
            0,
            0,
            50,
            0));
  }

  private static BoardHistoryList moveHistory(
      Stone[] stones, int[] lastMove, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return new BoardHistoryList(
        BoardData.move(
            stones.clone(),
            lastMove,
            lastMoveColor,
            blackToPlay,
            zobrist(stones),
            moveNumber,
            new int[stones.length],
            0,
            0,
            50,
            0));
  }

  private static Stone[] stones(Stone... placements) {
    Stone[] stones = new Stone[placements.length];
    System.arraycopy(placements, 0, stones, 0, placements.length);
    return stones;
  }

  private static Zobrist zobrist(Stone[] stones) {
    Zobrist zobrist = new Zobrist();
    int boardArea = Board.boardWidth * Board.boardHeight;
    for (int index = 0; index < boardArea; index++) {
      Stone stone = stones[index];
      if (!stone.isEmpty()) {
        int x = index / Board.boardHeight;
        int y = index % Board.boardHeight;
        zobrist.toggleStone(x, y, stone);
      }
    }
    return zobrist;
  }

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE = loadUnsafe();

    private static sun.misc.Unsafe loadUnsafe() {
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("Failed to access Unsafe", ex);
      }
    }
  }
}
