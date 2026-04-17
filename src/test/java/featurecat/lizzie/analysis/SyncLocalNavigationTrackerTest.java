package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.BoardHistoryNode;
import java.awt.event.ActionEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class SyncLocalNavigationTrackerTest {
  @Test
  void detectsInputEventAsUserNavigation() {
    KeyEvent keyEvent =
        new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_RIGHT, ' ');

    assertTrue(SyncLocalNavigationTracker.isUserNavigationEvent(keyEvent));
  }

  @Test
  void detectsComponentActionEventAsUserNavigation() {
    ActionEvent actionEvent = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "nav");

    assertTrue(SyncLocalNavigationTracker.isUserNavigationEvent(actionEvent));
  }

  @Test
  void ignoresInvocationEventAsUserNavigation() {
    InvocationEvent invocationEvent = new InvocationEvent(new Object(), () -> {});

    assertFalse(SyncLocalNavigationTracker.isUserNavigationEvent(invocationEvent));
  }

  @Test
  void ignoresTimerActionEventAsUserNavigation() {
    ActionEvent timerEvent = new ActionEvent(new Timer(1, null), ActionEvent.ACTION_PERFORMED, "");

    assertFalse(SyncLocalNavigationTracker.isUserNavigationEvent(timerEvent));
  }

  @Test
  void resolveReturnsFallbackWhenNoDeferredNavigationExists() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    assertSame(fallback, tracker.resolve(fallback));
  }

  @Test
  void resolvePrefersLatestDeferredNavigationUntilCleared() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode firstNode = new BoardHistoryNode(null);
    BoardHistoryNode latestNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    tracker.remember(firstNode);
    tracker.remember(latestNode);

    assertSame(latestNode, tracker.resolve(fallback));

    tracker.clear();

    assertSame(fallback, tracker.resolve(fallback));
  }

  @Test
  void ignoresReadBoardOwnedNavigationOnSameThread() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode ownedNavigationNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    tracker.beginReadBoardNavigation();
    tracker.remember(ownedNavigationNode);
    tracker.endReadBoardNavigation();

    assertSame(fallback, tracker.resolve(fallback));
  }

  @Test
  void ignoresRepeatedReadBoardOwnedNavigationOnSameThread() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode firstOwnedNavigationNode = new BoardHistoryNode(null);
    BoardHistoryNode secondOwnedNavigationNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    tracker.beginReadBoardNavigation();
    tracker.remember(firstOwnedNavigationNode);
    tracker.remember(secondOwnedNavigationNode);
    tracker.endReadBoardNavigation();

    assertSame(fallback, tracker.resolve(fallback));
  }

  @Test
  void ignoresNavigationOutsideUserNavigationThread() throws InterruptedException {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> false);
    BoardHistoryNode backgroundNavigationNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    tracker.beginReadBoardNavigation();
    Thread navigationThread =
        new Thread(
            () -> {
              try {
                tracker.remember(backgroundNavigationNode);
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            });
    navigationThread.start();
    navigationThread.join();
    tracker.endReadBoardNavigation();

    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
    assertSame(fallback, tracker.resolve(fallback));
  }

  @Test
  void keepsDeferredNavigationAcrossRecursiveSyncPass() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode deferredNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    tracker.remember(deferredNode);
    tracker.startSyncPass(true);

    assertSame(deferredNode, tracker.resolve(fallback));
  }

  @Test
  void clearsDeferredNavigationAtOuterSyncPassStart() {
    SyncLocalNavigationTracker tracker = new SyncLocalNavigationTracker(() -> true);
    BoardHistoryNode deferredNode = new BoardHistoryNode(null);
    BoardHistoryNode fallback = new BoardHistoryNode(null);

    tracker.remember(deferredNode);
    tracker.startSyncPass(false);

    assertSame(fallback, tracker.resolve(fallback));
    assertTrue(tracker.shouldProcessLocalNavigation());
  }
}
