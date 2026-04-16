package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.BoardHistoryNode;
import java.util.Optional;
import java.util.function.Supplier;

final class SyncHistoryJumpTracker {
  private BoardHistoryNode pendingMatchedNode;

  void remember(BoardHistoryNode matchedNode, BoardHistoryNode syncEndNode) {
    pendingMatchedNode = matchedNode == syncEndNode ? null : matchedNode;
  }

  void remember(
      BoardHistoryNode currentNode, BoardHistoryNode matchedNode, BoardHistoryNode syncEndNode) {
    pendingMatchedNode =
        currentNode == matchedNode || matchedNode == syncEndNode ? null : matchedNode;
  }

  Optional<BoardHistoryNode> consumeStableSnapshotTarget(
      BoardHistoryNode currentNode, Supplier<BoardHistoryNode> syncEndSupplier) {
    if (pendingMatchedNode == null) {
      return Optional.empty();
    }
    if (currentNode != pendingMatchedNode) {
      pendingMatchedNode = null;
      return Optional.empty();
    }
    BoardHistoryNode syncEndNode = syncEndSupplier.get();
    if (currentNode == syncEndNode) {
      pendingMatchedNode = null;
      return Optional.empty();
    }
    pendingMatchedNode = null;
    return Optional.of(syncEndNode);
  }

  void clear() {
    pendingMatchedNode = null;
  }
}
