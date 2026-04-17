package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.Stone;
import java.util.Optional;

final class SyncSnapshotDiffChecker {
  private final int boardWidth;

  SyncSnapshotDiffChecker(int boardWidth) {
    this.boardWidth = boardWidth;
  }

  boolean isComparable(int[] snapshotCodes, Stone[] stones) {
    return snapshotCodes.length == stones.length && snapshotCodes.length % boardWidth == 0;
  }

  boolean hasDiff(
      int[] snapshotCodes,
      Stone[] stones,
      boolean ignoreCurrentLastLocalMove,
      Optional<int[]> currentLastMove) {
    if (!isComparable(snapshotCodes, stones)) {
      return true;
    }
    int boardHeight = snapshotCodes.length / boardWidth;
    for (int index = 0; index < snapshotCodes.length; index++) {
      int x = index % boardWidth;
      int y = index / boardWidth;
      if (isStoneDiff(
          snapshotCodes[index],
          stones[x * boardHeight + y],
          x,
          y,
          ignoreCurrentLastLocalMove,
          currentLastMove)) {
        return true;
      }
    }
    return false;
  }

  boolean shouldResyncAfterIncrementalSync(
      boolean alwaysSyncBoardState,
      boolean showInBoard,
      int[] snapshotCodes,
      Stone[] stones,
      boolean ignoreCurrentLastLocalMove,
      Optional<int[]> currentLastMove) {
    if (!alwaysSyncBoardState && !showInBoard) {
      return false;
    }
    return hasDiff(snapshotCodes, stones, ignoreCurrentLastLocalMove, currentLastMove);
  }

  private boolean isStoneDiff(
      int snapshotCode,
      Stone stone,
      int x,
      int y,
      boolean ignoreCurrentLastLocalMove,
      Optional<int[]> currentLastMove) {
    if (snapshotCode == 0 && stone != Stone.EMPTY) {
      if (ignoreCurrentLastLocalMove && currentLastMove.isPresent()) {
        int[] lastCoords = currentLastMove.get();
        if (lastCoords[0] == x && lastCoords[1] == y) {
          return false;
        }
      }
      return true;
    }
    if ((snapshotCode == 1 || snapshotCode == 3) && !stone.isBlack()) {
      return true;
    }
    if ((snapshotCode == 2 || snapshotCode == 4) && !stone.isWhite()) {
      return true;
    }
    return false;
  }
}
