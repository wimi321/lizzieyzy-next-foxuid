package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContributeEngineCompareListAndNodeTest {
  private static final int BOARD_SIZE = 3;
  private static final int BOARD_AREA = BOARD_SIZE * BOARD_SIZE;

  @Test
  void watchListsContinueAcrossSnapshotBoundaryWithRealPassAndMove() throws Exception {
    Board previousBoard = Lizzie.board;
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;

    Board.boardWidth = BOARD_SIZE;
    Board.boardHeight = BOARD_SIZE;
    try {
      BoardHistoryList history =
          new BoardHistoryList(snapshotNode(emptyStones(), Optional.empty(), Stone.EMPTY, true, 0));
      history.add(
          moveNode(stones(placement(0, 0, Stone.BLACK)), new int[] {0, 0}, Stone.BLACK, false, 1));
      history.add(
          snapshotNode(
              stones(placement(0, 0, Stone.BLACK)), Optional.empty(), Stone.EMPTY, false, 1));
      history.add(passNode(stones(placement(0, 0, Stone.BLACK)), Stone.WHITE, true, 2));
      history.add(
          moveNode(
              stones(placement(0, 0, Stone.BLACK), placement(1, 0, Stone.BLACK)),
              new int[] {1, 0},
              Stone.BLACK,
              false,
              3));
      Lizzie.board = createBoard(history);

      ContributeGameInfo watchGame = new ContributeGameInfo();
      watchGame.initMoveList = new ArrayList<>();
      watchGame.initMoveList.add(move(false, true, 0, 0));
      watchGame.moveList = new ArrayList<>();
      watchGame.moveList.add(move(true, false, 0, 0));
      watchGame.moveList.add(move(false, true, 1, 0));

      ArrayList<ContributeMoveInfo> remainList = new ArrayList<>();

      boolean same =
          invokeIsContributeGameAndCurrentBoardSame(
              allocate(ContributeEngine.class), watchGame, remainList);

      assertTrue(
          same,
          "watch-game move lists should keep matching real PASS/MOVE nodes after skipping a SNAPSHOT anchor.");
      assertTrue(remainList.isEmpty(), "matching moveList should leave no remaining moves.");
    } finally {
      Lizzie.board = previousBoard;
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
    }
  }

  @Test
  void watchListsSkipDummyPassBeforeMatchingRealMove() throws Exception {
    Board previousBoard = Lizzie.board;
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;

    Board.boardWidth = BOARD_SIZE;
    Board.boardHeight = BOARD_SIZE;
    try {
      BoardHistoryList history =
          new BoardHistoryList(snapshotNode(emptyStones(), Optional.empty(), Stone.EMPTY, true, 0));
      BoardData dummyPass = passNode(emptyStones(), Stone.BLACK, false, 1);
      dummyPass.dummy = true;
      history.add(dummyPass);
      history.add(
          moveNode(stones(placement(0, 0, Stone.WHITE)), new int[] {0, 0}, Stone.WHITE, true, 2));
      Lizzie.board = createBoard(history);

      ContributeGameInfo watchGame = new ContributeGameInfo();
      watchGame.initMoveList = new ArrayList<>();
      watchGame.moveList = new ArrayList<>();
      watchGame.moveList.add(move(false, false, 0, 0));

      ArrayList<ContributeMoveInfo> remainList = new ArrayList<>();

      boolean same =
          invokeIsContributeGameAndCurrentBoardSame(
              allocate(ContributeEngine.class), watchGame, remainList);

      assertTrue(same, "watch-game matcher should skip dummy PASS placeholders.");
      assertTrue(remainList.isEmpty(), "skipping dummy PASS should still consume the real MOVE.");
    } finally {
      Lizzie.board = previousBoard;
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
    }
  }

  @Test
  void moveListStartsFromNextRealActionAfterInitPrefixConsumesLocalHistory() throws Exception {
    Board previousBoard = Lizzie.board;
    int previousBoardWidth = Board.boardWidth;
    int previousBoardHeight = Board.boardHeight;

    Board.boardWidth = BOARD_SIZE;
    Board.boardHeight = BOARD_SIZE;
    try {
      BoardHistoryList history =
          new BoardHistoryList(snapshotNode(emptyStones(), Optional.empty(), Stone.EMPTY, true, 0));
      history.add(
          moveNode(stones(placement(0, 0, Stone.BLACK)), new int[] {0, 0}, Stone.BLACK, false, 1));
      Lizzie.board = createBoard(history);

      ContributeGameInfo watchGame = new ContributeGameInfo();
      watchGame.initMoveList = new ArrayList<>();
      watchGame.initMoveList.add(move(false, true, 0, 0));
      watchGame.moveList = new ArrayList<>();
      watchGame.moveList.add(move(false, false, 1, 0));

      ArrayList<ContributeMoveInfo> remainList = new ArrayList<>();

      boolean same =
          invokeIsContributeGameAndCurrentBoardSame(
              allocate(ContributeEngine.class), watchGame, remainList);

      assertTrue(same, "fully matched initMoveList should keep the local prefix accepted.");
      assertTrue(
          remainList.size() == 1 && !remainList.get(0).isPass && !remainList.get(0).isBlack,
          "moveList should start from the next real action after the matched init prefix.");
    } finally {
      Lizzie.board = previousBoard;
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
    }
  }

  @Test
  void moveComparisonRequiresMatchingColor() throws Exception {
    BoardData nodeData =
        moveNode(stones(placement(0, 0, Stone.BLACK)), new int[] {0, 0}, Stone.BLACK, false, 1);

    assertFalse(
        invokeNodeMatchesMove(allocate(ContributeEngine.class), nodeData, move(false, false, 0, 0)),
        "MOVE comparison should reject same-coordinate moves when the color differs.");
  }

  @Test
  void dummyPassDoesNotMatchRealPass() throws Exception {
    BoardData nodeData = passNode(stones(placement(0, 0, Stone.BLACK)), Stone.BLACK, true, 2);
    nodeData.dummy = true;

    assertFalse(
        invokeNodeMatchesMove(allocate(ContributeEngine.class), nodeData, move(true, true, 0, 0)),
        "dummy PASS placeholders should stay out of real PASS matching.");
  }

  @Test
  void passComparisonRequiresMatchingColor() throws Exception {
    BoardData nodeData = passNode(stones(placement(0, 0, Stone.BLACK)), Stone.BLACK, true, 2);

    assertFalse(
        invokeNodeMatchesMove(allocate(ContributeEngine.class), nodeData, move(true, false, 0, 0)),
        "PASS comparison should reject pass moves when the color differs.");
  }

  private static ContributeMoveInfo move(boolean isPass, boolean isBlack, int x, int y) {
    ContributeMoveInfo move = new ContributeMoveInfo();
    move.isPass = isPass;
    move.isBlack = isBlack;
    move.pos = new int[] {x, y};
    return move;
  }

  private static Board createBoard(BoardHistoryList history) throws Exception {
    Board board = allocate(Board.class);
    board.startStonelist = new ArrayList<>();
    board.hasStartStone = false;
    board.setHistory(history);
    return board;
  }

  private static BoardData moveNode(
      Stone[] stones, int[] lastMove, Stone color, boolean blackToPlay, int moveNumber) {
    return BoardData.move(
        stones,
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

  private static BoardData snapshotNode(
      Stone[] stones,
      Optional<int[]> lastMove,
      Stone lastMoveColor,
      boolean blackToPlay,
      int moveNumber) {
    return BoardData.snapshot(
        stones,
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

  private static BoardData passNode(
      Stone[] stones, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    return BoardData.pass(
        stones,
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

  private static Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < stones.length; index++) {
      stones[index] = Stone.EMPTY;
    }
    return stones;
  }

  private static Stone[] stones(Placement... placements) {
    Stone[] stones = emptyStones();
    for (Placement placement : placements) {
      stones[stoneIndex(placement.x, placement.y)] = placement.color;
    }
    return stones;
  }

  private static int stoneIndex(int x, int y) {
    return x * BOARD_SIZE + y;
  }

  private static Placement placement(int x, int y, Stone color) {
    return new Placement(x, y, color);
  }

  private static boolean invokeIsContributeGameAndCurrentBoardSame(
      ContributeEngine engine,
      ContributeGameInfo watchGame,
      ArrayList<ContributeMoveInfo> remainList)
      throws Exception {
    Method method =
        ContributeEngine.class.getDeclaredMethod(
            "isContributeGameAndCurrentBoardSame", ContributeGameInfo.class, ArrayList.class);
    method.setAccessible(true);
    return (boolean) method.invoke(engine, watchGame, remainList);
  }

  private static boolean invokeNodeMatchesMove(
      ContributeEngine engine, BoardData nodeData, ContributeMoveInfo move) throws Exception {
    Method method =
        ContributeEngine.class.getDeclaredMethod(
            "nodeMatchesMove", BoardData.class, ContributeMoveInfo.class);
    method.setAccessible(true);
    return (boolean) method.invoke(engine, nodeData, move);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static final class Placement {
    private final int x;
    private final int y;
    private final Stone color;

    private Placement(int x, int y, Stone color) {
      this.x = x;
      this.y = y;
      this.color = color;
    }
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
