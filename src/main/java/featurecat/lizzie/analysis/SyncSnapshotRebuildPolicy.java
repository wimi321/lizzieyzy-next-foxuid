package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import java.util.Optional;
import java.util.OptionalInt;

final class SyncSnapshotRebuildPolicy {
  private final int boardWidth;

  SyncSnapshotRebuildPolicy(int boardWidth) {
    this.boardWidth = boardWidth;
  }

  boolean shouldRebuildImmediatelyWithoutHistory(BoardHistoryNode syncStartNode) {
    return syncStartNode != null && !syncStartNode.previous().isPresent();
  }

  Optional<BoardHistoryNode> findMatchingHistoryNode(
      BoardHistoryNode syncStartNode, int[] snapshotCodes, OptionalInt foxMoveNumber) {
    if (syncStartNode == null || snapshotCodes.length == 0) {
      return Optional.empty();
    }
    SnapshotMarker marker = findSnapshotMarker(snapshotCodes);
    if (!marker.valid) {
      return Optional.empty();
    }
    BoardData currentData = syncStartNode.getData();
    if (marker.present) {
      return matchesSnapshot(currentData, snapshotCodes, marker)
          ? Optional.of(syncStartNode)
          : Optional.empty();
    }
    return matchesStones(currentData.stones, snapshotCodes)
        ? Optional.of(syncStartNode)
        : Optional.empty();
  }

  Optional<BoardHistoryNode> findAdjacentMatchFromLastResolvedNode(
      BoardHistoryNode lastResolvedNode, int[] snapshotCodes, OptionalInt foxMoveNumber) {
    return Optional.empty();
  }

  private boolean matchesSnapshot(BoardData candidate, int[] snapshotCodes, SnapshotMarker marker) {
    return matchesStones(candidate.stones, snapshotCodes) && matchesMarker(candidate, marker);
  }

  private boolean matchesStones(Stone[] stones, int[] snapshotCodes) {
    if (stones.length != snapshotCodes.length || snapshotCodes.length % boardWidth != 0) {
      return false;
    }
    int boardHeight = snapshotCodes.length / boardWidth;
    for (int snapshotIndex = 0; snapshotIndex < snapshotCodes.length; snapshotIndex++) {
      int x = snapshotIndex % boardWidth;
      int y = snapshotIndex / boardWidth;
      int stoneIndex = x * boardHeight + y;
      if (normalizeSnapshot(snapshotCodes[snapshotIndex]) != normalizeStone(stones[stoneIndex])) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesMarker(BoardData candidate, SnapshotMarker marker) {
    if (!marker.present) {
      return true;
    }
    if (!candidate.lastMove.isPresent()) {
      return false;
    }
    int[] coords = candidate.lastMove.get();
    return coords[0] == marker.x
        && coords[1] == marker.y
        && normalizeStone(candidate.lastMoveColor) == marker.color;
  }

  private SnapshotMarker findSnapshotMarker(int[] snapshotCodes) {
    SnapshotMarker marker = SnapshotMarker.none();
    for (int snapshotIndex = 0; snapshotIndex < snapshotCodes.length; snapshotIndex++) {
      int color = markerColor(snapshotCodes[snapshotIndex]);
      if (color == 0) {
        continue;
      }
      if (marker.present) {
        return SnapshotMarker.invalid();
      }
      marker = SnapshotMarker.at(snapshotIndex % boardWidth, snapshotIndex / boardWidth, color);
    }
    return marker;
  }

  private int markerColor(int value) {
    if (value == 3) {
      return 1;
    }
    if (value == 4) {
      return 2;
    }
    return 0;
  }

  private int normalizeSnapshot(int value) {
    if (value == 1 || value == 3) {
      return 1;
    }
    if (value == 2 || value == 4) {
      return 2;
    }
    return 0;
  }

  private int normalizeStone(Stone stone) {
    if (stone == Stone.BLACK || stone == Stone.BLACK_RECURSED) {
      return 1;
    }
    if (stone == Stone.WHITE || stone == Stone.WHITE_RECURSED) {
      return 2;
    }
    return 0;
  }

  private static final class SnapshotMarker {
    private final boolean valid;
    private final boolean present;
    private final int x;
    private final int y;
    private final int color;

    private SnapshotMarker(boolean valid, boolean present, int x, int y, int color) {
      this.valid = valid;
      this.present = present;
      this.x = x;
      this.y = y;
      this.color = color;
    }

    private static SnapshotMarker none() {
      return new SnapshotMarker(true, false, -1, -1, 0);
    }

    private static SnapshotMarker invalid() {
      return new SnapshotMarker(false, false, -1, -1, 0);
    }

    private static SnapshotMarker at(int x, int y, int color) {
      return new SnapshotMarker(true, true, x, y, color);
    }
  }
}
