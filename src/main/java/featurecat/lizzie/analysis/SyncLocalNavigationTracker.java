package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.BoardHistoryNode;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.MenuComponent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.function.BooleanSupplier;

final class SyncLocalNavigationTracker {
  private BoardHistoryNode deferredNode;
  private final BooleanSupplier isUserNavigationEvent;
  private final ThreadLocal<Integer> readBoardNavigationDepth = ThreadLocal.withInitial(() -> 0);

  SyncLocalNavigationTracker() {
    this(SyncLocalNavigationTracker::isUserNavigationEvent);
  }

  SyncLocalNavigationTracker(BooleanSupplier isUserNavigationEvent) {
    this.isUserNavigationEvent = isUserNavigationEvent;
  }

  synchronized void startSyncPass(boolean isSecondTime) {
    if (!isSecondTime) {
      deferredNode = null;
    }
  }

  void beginReadBoardNavigation() {
    readBoardNavigationDepth.set(readBoardNavigationDepth.get() + 1);
  }

  void endReadBoardNavigation() {
    int depth = readBoardNavigationDepth.get();
    if (depth <= 1) {
      readBoardNavigationDepth.remove();
      return;
    }
    readBoardNavigationDepth.set(depth - 1);
  }

  boolean shouldProcessLocalNavigation() {
    return isUserNavigationEvent.getAsBoolean() && readBoardNavigationDepth.get() == 0;
  }

  synchronized void remember(BoardHistoryNode currentNode) {
    if (!shouldProcessLocalNavigation()) {
      return;
    }
    deferredNode = currentNode;
  }

  synchronized BoardHistoryNode resolve(BoardHistoryNode fallbackNode) {
    return deferredNode != null ? deferredNode : fallbackNode;
  }

  synchronized void clear() {
    deferredNode = null;
    readBoardNavigationDepth.remove();
  }

  static boolean isUserNavigationEvent() {
    return isUserNavigationEvent(EventQueue.getCurrentEvent());
  }

  static boolean isUserNavigationEvent(AWTEvent currentEvent) {
    if (currentEvent instanceof InputEvent) {
      return true;
    }
    if (!(currentEvent instanceof ActionEvent)) {
      return false;
    }
    Object source = currentEvent.getSource();
    return source instanceof Component || source instanceof MenuComponent;
  }
}
