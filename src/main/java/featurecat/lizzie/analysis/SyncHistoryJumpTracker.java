package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.BoardHistoryNode;
import java.util.Optional;
import java.util.function.Supplier;

final class SyncHistoryJumpTracker {
  private BoardHistoryNode pendingMatchedNode;

  synchronized void remember(BoardHistoryNode matchedNode, BoardHistoryNode syncEndNode) {
    pendingMatchedNode = matchedNode == syncEndNode ? null : matchedNode;
  }

  synchronized void remember(
      BoardHistoryNode currentNode, BoardHistoryNode matchedNode, BoardHistoryNode syncEndNode) {
    if (matchedNode == syncEndNode) {
      pendingMatchedNode = null;
      return;
    }
    if (currentNode == matchedNode) {
      if (pendingMatchedNode != matchedNode) {
        pendingMatchedNode = null;
      }
      return;
    }
    pendingMatchedNode = matchedNode;
  }

  synchronized Optional<BoardHistoryNode> consumeStableSnapshotTarget(
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

  synchronized void onLocalNavigation() {
    pendingMatchedNode = null;
  }

  synchronized void clear() {
    pendingMatchedNode = null;
  }
}
