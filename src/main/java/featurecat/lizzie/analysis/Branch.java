package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardNodeKind;
import featurecat.lizzie.rules.Stone;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Branch {
  public BoardData data;
  //  public int branchLength;
  // 待完成
  //  public int pvVisits = -1;
  public boolean[] isNewStone;
  public int[] pvVisitsList;
  public int length;

  public Branch(
      Board board,
      List<String> variation,
      // 待完成
      List<String> pvVisits,
      int length,
      boolean fromSubboard,
      boolean blackToPlay,
      Stone[] stonesTemp,
      boolean forMouseOnStone,
      BoardData forMouseOnStoneData) {
    isNewStone = new boolean[Board.boardWidth * Board.boardHeight];
    pvVisitsList = new int[Board.boardWidth * Board.boardHeight];
    double winrate = 0.0;
    int playouts = 0;
    BoardData sourceData =
        resolveSourceData(board, fromSubboard, stonesTemp, forMouseOnStone, forMouseOnStoneData);
    Stone[] sourceStones =
        resolveSourceStones(board, stonesTemp, forMouseOnStone, forMouseOnStoneData);
    boolean branchBlackToPlay = fromSubboard ? blackToPlay : sourceData.blackToPlay;
    this.data =
        copySourceData(
            sourceData,
            sourceStones,
            branchBlackToPlay,
            new int[Board.boardWidth * Board.boardHeight],
            winrate,
            playouts);
    this.length = applyVariation(variation, pvVisits, length);
  }

  private static BoardData resolveSourceData(
      Board board,
      boolean fromSubboard,
      Stone[] stonesTemp,
      boolean forMouseOnStone,
      BoardData forMouseOnStoneData) {
    if (forMouseOnStone) {
      return forMouseOnStoneData;
    }
    if (fromSubboard || stonesTemp == null) {
      return board.getData();
    }
    return board.getHistory().getCurrentHistoryNode().previous().get().getData();
  }

  private static Stone[] resolveSourceStones(
      Board board, Stone[] stonesTemp, boolean forMouseOnStone, BoardData forMouseOnStoneData) {
    if (forMouseOnStone) {
      return forMouseOnStoneData.stones.clone();
    }
    return stonesTemp != null ? stonesTemp.clone() : board.getStones().clone();
  }

  private static BoardData copySourceData(
      BoardData sourceData,
      Stone[] stones,
      boolean blackToPlay,
      int[] moveNumberList,
      double winrate,
      int playouts) {
    BoardData branchData =
        createBoardData(
            sourceData.getNodeKind(),
            stones,
            copyLastMove(sourceData.lastMove),
            sourceData.lastMoveColor,
            blackToPlay,
            sourceData.zobrist.clone(),
            sourceData.moveNumber,
            moveNumberList,
            sourceData.blackCaptures,
            sourceData.whiteCaptures,
            winrate,
            playouts);
    copyRenderMetadata(sourceData, branchData);
    return branchData;
  }

  private int applyVariation(List<String> variation, List<String> pvVisits, int maxLength) {
    int processedMoves = 0;
    int limit = Math.min(variation.size(), maxLength);
    for (int i = 0; i < limit; i++) {
      String move = variation.get(i).trim();
      if (move.equalsIgnoreCase("resign")) {
        break;
      }
      int branchMoveNumber = processedMoves + 1;
      if (move.equalsIgnoreCase("pass")) {
        applyPass();
        processedMoves++;
        continue;
      }
      Optional<int[]> coordOpt = Board.asCoordinates(move);
      if (!coordOpt.isPresent() || !Board.isValid(coordOpt.get()[0], coordOpt.get()[1])) {
        break;
      }
      applyCoordinateMove(coordOpt.get(), branchMoveNumber, i, variation.size(), pvVisits);
      processedMoves++;
    }
    return processedMoves;
  }

  private void applyPass() {
    Stone moveColor = currentMoveColor();
    data =
        rebuildData(
            BoardNodeKind.PASS,
            Optional.empty(),
            moveColor,
            !data.blackToPlay,
            data.moveNumber + 1);
  }

  private void applyCoordinateMove(
      int[] coord,
      int branchMoveNumber,
      int variationIndex,
      int variationSize,
      List<String> pvVisits) {
    int x = coord[0];
    int y = coord[1];
    int boardIndex = Board.getIndex(x, y);
    Stone moveColor = currentMoveColor();
    data.lastMove = Optional.of(coord);
    data.stones[boardIndex] = moveColor;
    isNewStone[boardIndex] = true;
    if (Lizzie.config.removeDeadChainInVariation && !Lizzie.config.noCapture) {
      Board.removeDeadChainForBranch(x + 1, y, moveColor.opposite(), data.stones);
      Board.removeDeadChainForBranch(x, y + 1, moveColor.opposite(), data.stones);
      Board.removeDeadChainForBranch(x - 1, y, moveColor.opposite(), data.stones);
      Board.removeDeadChainForBranch(x, y - 1, moveColor.opposite(), data.stones);
    }
    data.moveNumberList[boardIndex] = branchMoveNumber;
    data =
        rebuildData(
            BoardNodeKind.MOVE,
            Optional.of(coord.clone()),
            moveColor,
            !data.blackToPlay,
            data.moveNumber + 1);
    recordPvVisits(boardIndex, variationIndex, variationSize, pvVisits);
  }

  private void recordPvVisits(
      int boardIndex, int variationIndex, int variationSize, List<String> pvVisits) {
    if (!(Lizzie.config.showPvVisitsAllMove || Lizzie.config.showPvVisitsLastMove)) {
      return;
    }
    if (pvVisits == null || pvVisits.size() != variationSize) {
      return;
    }
    try {
      pvVisitsList[boardIndex] = Integer.parseInt(pvVisits.get(variationIndex));
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
  }

  private Stone currentMoveColor() {
    return data.blackToPlay ? Stone.BLACK : Stone.WHITE;
  }

  private BoardData rebuildData(
      BoardNodeKind nodeKind,
      Optional<int[]> lastMove,
      Stone lastMoveColor,
      boolean blackToPlay,
      int moveNumber) {
    BoardData previousData = data;
    BoardData nextData =
        createBoardData(
            nodeKind,
            previousData.stones,
            lastMove,
            lastMoveColor,
            blackToPlay,
            previousData.zobrist.clone(),
            moveNumber,
            previousData.moveNumberList,
            previousData.blackCaptures,
            previousData.whiteCaptures,
            previousData.winrate,
            previousData.getPlayouts());
    copyRenderMetadata(previousData, nextData);
    return nextData;
  }

  private static BoardData createBoardData(
      BoardNodeKind nodeKind,
      Stone[] stones,
      Optional<int[]> lastMove,
      Stone lastMoveColor,
      boolean blackToPlay,
      featurecat.lizzie.rules.Zobrist zobrist,
      int moveNumber,
      int[] moveNumberList,
      int blackCaptures,
      int whiteCaptures,
      double winrate,
      int playouts) {
    switch (nodeKind) {
      case MOVE:
        return BoardData.move(
            stones,
            lastMove.orElseThrow(
                () -> new IllegalStateException("MOVE nodes require coordinates.")),
            lastMoveColor,
            blackToPlay,
            zobrist,
            moveNumber,
            moveNumberList,
            blackCaptures,
            whiteCaptures,
            winrate,
            playouts);
      case PASS:
        return BoardData.pass(
            stones,
            lastMoveColor,
            blackToPlay,
            zobrist,
            moveNumber,
            moveNumberList,
            blackCaptures,
            whiteCaptures,
            winrate,
            playouts);
      case SNAPSHOT:
        return BoardData.snapshot(
            stones,
            copyLastMove(lastMove),
            lastMoveColor,
            blackToPlay,
            zobrist,
            moveNumber,
            moveNumberList,
            blackCaptures,
            whiteCaptures,
            winrate,
            playouts);
      default:
        throw new IllegalStateException("Unsupported board node kind: " + nodeKind);
    }
  }

  private static void copyRenderMetadata(BoardData sourceData, BoardData targetData) {
    targetData.dummy = sourceData.dummy;
    targetData.moveMNNumber = sourceData.moveMNNumber;
    targetData.verify = sourceData.verify;
    targetData.comment = sourceData.comment;
    targetData.setProperties(new HashMap<>(sourceData.getProperties()));
  }

  private static Optional<int[]> copyLastMove(Optional<int[]> lastMove) {
    return lastMove.map(coords -> coords.clone());
  }
}
