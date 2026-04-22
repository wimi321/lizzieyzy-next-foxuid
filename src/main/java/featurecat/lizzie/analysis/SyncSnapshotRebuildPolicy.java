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
      BoardHistoryNode syncStartNode, int[] snapshotCodes, SyncRemoteContext remoteContext) {
    if (syncStartNode == null || snapshotCodes.length == 0) {
      return Optional.empty();
    }
    if (shouldUseAncestorScan(remoteContext)) {
      for (BoardHistoryNode cursor = syncStartNode;
          cursor != null;
          cursor = cursor.previous().orElse(null)) {
        if (matchesRemoteIdentity(cursor.getData(), snapshotCodes, remoteContext)) {
          return Optional.of(cursor);
        }
      }
      return Optional.empty();
    }
    return matchesRemoteIdentity(syncStartNode.getData(), snapshotCodes, remoteContext)
        ? Optional.of(syncStartNode)
        : Optional.empty();
  }

  Optional<BoardHistoryNode> findMatchingNodeInMainlineWindow(
      BoardHistoryNode currentNode,
      BoardHistoryNode mainEnd,
      int[] snapshotCodes,
      SyncRemoteContext remoteContext) {
    if (currentNode == null
        || mainEnd == null
        || snapshotCodes.length == 0
        || remoteContext == null
        || !remoteContext.supportsFoxRecovery()) {
      return Optional.empty();
    }

    Optional<BoardHistoryNode> currentOrAncestor =
        findMatchingHistoryNode(currentNode, snapshotCodes, remoteContext);
    if (currentOrAncestor.isPresent()) {
      return currentOrAncestor;
    }

    for (BoardHistoryNode cursor = nextMainlineNode(currentNode);
        cursor != null;
        cursor = cursor == mainEnd ? null : nextMainlineNode(cursor)) {
      if (matchesRemoteIdentity(cursor.getData(), snapshotCodes, remoteContext)) {
        return Optional.of(cursor);
      }
    }
    return Optional.empty();
  }

  Optional<BoardHistoryNode> findMatchingHistoryNode(
      BoardHistoryNode syncStartNode, int[] snapshotCodes, OptionalInt foxMoveNumber) {
    SyncRemoteContext remoteContext =
        foxMoveNumber.isPresent()
            ? SyncRemoteContext.forFoxLive(foxMoveNumber, null, foxMoveNumber, false)
            : SyncRemoteContext.generic(false);
    return findMatchingHistoryNode(syncStartNode, snapshotCodes, remoteContext);
  }

  Optional<BoardHistoryNode> findAdjacentMatchFromLastResolvedNode(
      SyncResumeState resumeState, int[] snapshotCodes, SyncRemoteContext remoteContext) {
    if (resumeState == null
        || resumeState.node == null
        || remoteContext == null
        || !remoteContext.supportsFoxRecovery()
        || resumeState.remoteContext == null
        || resumeState.remoteContext.conflictsWith(remoteContext)) {
      return Optional.empty();
    }
    BoardHistoryNode nextNode =
        resumeState.node.next().filter(BoardHistoryNode::isMainTrunk).orElse(null);
    if (nextNode == null) {
      return Optional.empty();
    }
    if (matchesRemoteIdentity(nextNode.getData(), snapshotCodes, remoteContext)) {
      return Optional.of(nextNode);
    }
    return Optional.empty();
  }

  Optional<BoardHistoryNode> findAdjacentMatchFromLastResolvedNode(
      BoardHistoryNode lastResolvedNode, int[] snapshotCodes, OptionalInt foxMoveNumber) {
    SyncRemoteContext remoteContext =
        foxMoveNumber.isPresent()
            ? SyncRemoteContext.forFoxLive(foxMoveNumber, null, foxMoveNumber, false)
            : SyncRemoteContext.generic(false);
    return findAdjacentMatchFromLastResolvedNode(
        new SyncResumeState(lastResolvedNode, remoteContext), snapshotCodes, remoteContext);
  }

  String buildConflictKey(int[] snapshotCodes, SyncRemoteContext remoteContext) {
    StringBuilder builder = new StringBuilder(snapshotCodes.length + 64);
    appendStoneIdentity(builder, snapshotCodes, remoteContext);
    if (remoteContext == null) {
      return builder.toString();
    }
    builder.append('|').append(remoteContext.platform.name());
    builder.append('|').append(remoteContext.windowKind.name());
    if (remoteContext.supportsFoxRecovery()) {
      builder.append('|').append(remoteContext.recoveryMoveNumber().getAsInt());
    }
    if (remoteContext.windowKind == SyncRemoteContext.WindowKind.LIVE_ROOM) {
      builder.append('|').append(remoteContext.roomToken.orElse(""));
    }
    if (remoteContext.windowKind == SyncRemoteContext.WindowKind.RECORD_VIEW) {
      builder
          .append('|')
          .append(
              remoteContext.recordTotalMove.isPresent()
                  ? remoteContext.recordTotalMove.getAsInt()
                  : -1);
      builder.append('|').append(remoteContext.titleFingerprint.orElse(""));
    }
    return builder.toString();
  }

  private boolean shouldUseAncestorScan(SyncRemoteContext remoteContext) {
    return remoteContext != null && remoteContext.supportsFoxRecovery();
  }

  private BoardHistoryNode nextMainlineNode(BoardHistoryNode node) {
    return node == null ? null : node.next().filter(BoardHistoryNode::isMainTrunk).orElse(null);
  }

  private boolean matchesRemoteIdentity(
      BoardData candidate, int[] snapshotCodes, SyncRemoteContext remoteContext) {
    SnapshotMarker marker = findSnapshotMarker(snapshotCodes);
    if (!marker.valid) {
      return false;
    }
    boolean foxRecovery = remoteContext != null && remoteContext.supportsFoxRecovery();
    if (!matchesStones(candidate.stones, snapshotCodes, remoteContext)) {
      return false;
    }
    if (foxRecovery) {
      return candidate.moveNumber == remoteContext.recoveryMoveNumber().getAsInt();
    }
    if (marker.present && !matchesMarker(candidate, marker)) {
      return false;
    }
    return true;
  }

  private boolean matchesStones(
      Stone[] stones, int[] snapshotCodes, SyncRemoteContext remoteContext) {
    if (stones.length != snapshotCodes.length || snapshotCodes.length % boardWidth != 0) {
      return false;
    }
    int boardHeight = snapshotCodes.length / boardWidth;
    for (int snapshotIndex = 0; snapshotIndex < snapshotCodes.length; snapshotIndex++) {
      int x = snapshotIndex % boardWidth;
      int y = snapshotIndex / boardWidth;
      int stoneIndex = x * boardHeight + y;
      if (normalizeConflictSnapshot(snapshotCodes[snapshotIndex], remoteContext)
          != normalizeStone(stones[stoneIndex])) {
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

  private void appendStoneIdentity(
      StringBuilder builder, int[] snapshotCodes, SyncRemoteContext remoteContext) {
    for (int snapshotCode : snapshotCodes) {
      builder.append(normalizeConflictSnapshot(snapshotCode, remoteContext));
    }
  }

  private int normalizeConflictSnapshot(int value, SyncRemoteContext remoteContext) {
    if ((value == 3 || value == 4)
        && remoteContext != null
        && remoteContext.supportsFoxRecovery()) {
      return inferredFoxLastMoveColor(remoteContext);
    }
    return normalizeSnapshot(value);
  }

  private int inferredFoxLastMoveColor(SyncRemoteContext remoteContext) {
    return remoteContext.recoveryMoveNumber().getAsInt() % 2 == 0 ? 2 : 1;
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
