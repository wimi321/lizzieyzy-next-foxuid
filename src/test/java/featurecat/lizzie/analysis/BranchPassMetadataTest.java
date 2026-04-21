package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class BranchPassMetadataTest {
  private static final int BOARD_SIZE = 2;
  private static final int BOARD_AREA = BOARD_SIZE * BOARD_SIZE;

  @Test
  void branchWithoutVariationKeepsExplicitSnapshotKindAndDummy() throws Exception {
    TestEnvironment env = TestEnvironment.open();
    try {
      BoardData root = snapshotData(Optional.empty(), Stone.EMPTY, false, 58);
      root.dummy = true;
      Board board = boardWithRoot(root);

      Branch branch = createBranch(board, List.of());

      assertTrue(
          branch.data.isSnapshotNode(), "branch copy should preserve explicit snapshot kind.");
      assertTrue(branch.data.dummy, "branch copy should preserve dummy metadata.");
      assertEquals(0, branch.length, "empty variations should keep branch length at zero.");
    } finally {
      env.close();
    }
  }

  @Test
  void branchPassCreatesExplicitPassNode() throws Exception {
    TestEnvironment env = TestEnvironment.open();
    try {
      Board board = boardWithRoot(snapshotData(Optional.empty(), Stone.EMPTY, false, 58));

      Branch branch = createBranch(board, List.of("pass"));

      assertTrue(branch.data.isPassNode(), "branch pass should become an explicit PASS node.");
      assertEquals(59, branch.data.moveNumber, "branch pass should advance the total move number.");
      assertEquals(1, branch.length, "branch length should count the rendered pass.");
    } finally {
      env.close();
    }
  }

  @Test
  void branchPassThenMoveCreatesExplicitMoveAndKeepsVariationAlive() throws Exception {
    TestEnvironment env = TestEnvironment.open();
    try {
      Board board = boardWithRoot(snapshotData(Optional.empty(), Stone.EMPTY, false, 58));

      Branch branch = createBranch(board, List.of("pass", "A1"));

      assertTrue(branch.data.isMoveNode(), "branch should become an explicit MOVE after a stone.");
      assertTrue(branch.data.lastMove.isPresent(), "branch should continue after a pass.");
      assertArrayEquals(
          new int[] {0, 1},
          branch.data.lastMove.get(),
          "branch should keep the move that follows a pass.");
      assertEquals(
          60, branch.data.moveNumber, "branch should advance the move number for every step.");
      assertEquals(
          2,
          branch.data.moveNumberList[Board.getIndex(0, 1)],
          "post-pass moves should keep branch numbering.");
      assertEquals(2, branch.length, "branch length should include pass and follow-up moves.");
    } finally {
      env.close();
    }
  }

  private static Branch createBranch(Board board, List<String> variation) {
    return new Branch(
        board,
        variation,
        null,
        variation.size(),
        true,
        board.getData().blackToPlay,
        board.getStones(),
        false,
        null);
  }

  private static Board boardWithRoot(BoardData root) throws Exception {
    Board board = allocate(Board.class);
    board.setHistory(new BoardHistoryList(root));
    return board;
  }

  private static BoardData snapshotData(
      Optional<int[]> lastMove, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
    Stone[] stones = emptyStones();
    lastMove.ifPresent(coords -> stones[Board.getIndex(coords[0], coords[1])] = lastMoveColor);
    return BoardData.snapshot(
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

  private static Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      stones[index] = Stone.EMPTY;
    }
    return stones;
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static final class TestEnvironment implements AutoCloseable {
    private final int previousBoardWidth;
    private final int previousBoardHeight;
    private final Config previousConfig;

    private TestEnvironment(
        int previousBoardWidth, int previousBoardHeight, Config previousConfig) {
      this.previousBoardWidth = previousBoardWidth;
      this.previousBoardHeight = previousBoardHeight;
      this.previousConfig = previousConfig;
    }

    private static TestEnvironment open() throws Exception {
      int previousBoardWidth = Board.boardWidth;
      int previousBoardHeight = Board.boardHeight;
      Config previousConfig = Lizzie.config;

      Board.boardWidth = BOARD_SIZE;
      Board.boardHeight = BOARD_SIZE;
      Zobrist.init();

      Config config = allocate(Config.class);
      config.removeDeadChainInVariation = false;
      config.noCapture = false;
      config.showPvVisitsAllMove = false;
      config.showPvVisitsLastMove = false;
      config.showHeat = false;
      config.showHeatAfterCalc = false;
      config.persisted = new JSONObject().put("ui-persist", new JSONObject().put("max-alpha", 240));
      Lizzie.config = config;
      return new TestEnvironment(previousBoardWidth, previousBoardHeight, previousConfig);
    }

    @Override
    public void close() {
      Board.boardWidth = previousBoardWidth;
      Board.boardHeight = previousBoardHeight;
      Zobrist.init();
      Lizzie.config = previousConfig;
    }
  }

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE;

    static {
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        UNSAFE = (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }
}
