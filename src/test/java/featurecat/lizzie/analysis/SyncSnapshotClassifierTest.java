package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.rules.Stone;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyncSnapshotClassifierTest {
  private static final int BOARD_SIZE = 3;
  private static final int BOARD_AREA = BOARD_SIZE * BOARD_SIZE;

  @Test
  void recognizesSingleOrdinaryMoveAsLegalSingleStep() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            emptyStones(),
            snapshot(stones(placement(1, 1, Stone.BLACK)), placement(1, 1, Stone.BLACK)));

    assertTrue(move.isPresent());
    assertEquals(1, move.get().x);
    assertEquals(1, move.get().y);
    assertEquals(Stone.BLACK, move.get().color);
  }

  @Test
  void recognizesSingleLegalCaptureAsLegalSingleStep() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] beforeCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 1, Stone.WHITE),
            placement(2, 1, Stone.BLACK));
    Stone[] afterCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 2, Stone.BLACK),
            placement(2, 1, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            beforeCapture, snapshot(afterCapture, placement(1, 2, Stone.BLACK)));

    assertTrue(move.isPresent());
    assertEquals(1, move.get().x);
    assertEquals(2, move.get().y);
    assertEquals(Stone.BLACK, move.get().color);
  }

  @Test
  void rejectsSingleCaptureWhenMarkerPointsToExistingOpponentStone() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] beforeCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 1, Stone.WHITE),
            placement(2, 1, Stone.BLACK));
    Stone[] afterCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 2, Stone.BLACK),
            placement(2, 1, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            beforeCapture, snapshotWithMarkers(afterCapture, placement(0, 0, Stone.WHITE)));

    assertFalse(move.isPresent());
  }

  @Test
  void rejectsSingleCaptureWhenMarkerPointsToDifferentFriendlyStone() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] beforeCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 1, Stone.WHITE),
            placement(2, 1, Stone.BLACK));
    Stone[] afterCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 2, Stone.BLACK),
            placement(2, 1, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            beforeCapture, snapshotWithMarkers(afterCapture, placement(0, 1, Stone.BLACK)));

    assertFalse(move.isPresent());
  }

  @Test
  void rejectsSingleCaptureWhenSnapshotContainsMultipleMarkers() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] beforeCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 1, Stone.WHITE),
            placement(2, 1, Stone.BLACK));
    Stone[] afterCapture =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(0, 1, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(1, 2, Stone.BLACK),
            placement(2, 1, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            beforeCapture,
            snapshotWithMarkers(
                afterCapture, placement(1, 2, Stone.BLACK), placement(0, 1, Stone.BLACK)));

    assertFalse(move.isPresent());
  }

  @Test
  void rejectsMultipleAddedStonesAsSingleStep() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            emptyStones(),
            snapshot(
                stones(placement(0, 0, Stone.BLACK), placement(2, 2, Stone.BLACK)),
                placement(2, 2, Stone.BLACK)));

    assertFalse(move.isPresent());
  }

  @Test
  void rejectsMultipleRemovedStonesAsSingleStep() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] before =
        stones(
            placement(0, 0, Stone.WHITE),
            placement(2, 2, Stone.WHITE),
            placement(1, 1, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(before, snapshot(stones(placement(1, 1, Stone.BLACK))));

    assertFalse(move.isPresent());
  }

  @Test
  void rejectsMixedAdditionsAndRemovalsWhenTheyDoNotFormLegalCapture() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);
    Stone[] before =
        stones(
            placement(0, 0, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(0, 1, Stone.BLACK),
            placement(1, 1, Stone.WHITE),
            placement(2, 2, Stone.WHITE));
    Stone[] impossibleSnapshot =
        stones(
            placement(0, 0, Stone.BLACK),
            placement(1, 0, Stone.BLACK),
            placement(0, 1, Stone.BLACK),
            placement(1, 2, Stone.BLACK));

    Optional<SyncSnapshotClassifier.SingleMove> move =
        classifier.findSingleMoveCapture(
            before, snapshot(impossibleSnapshot, placement(1, 2, Stone.BLACK)));

    assertFalse(move.isPresent());
  }

  @Test
  void markerlessSingleAdditionDoesNotQualifyForIncrementalMoveSync() {
    SyncSnapshotClassifier classifier = new SyncSnapshotClassifier(BOARD_SIZE, BOARD_SIZE);

    SyncSnapshotClassifier.SnapshotDelta markerlessDelta =
        classifier.summarizeDelta(
            emptyStones(), snapshot(stones(placement(1, 1, Stone.BLACK)), null));
    SyncSnapshotClassifier.SnapshotDelta markedDelta =
        classifier.summarizeDelta(
            emptyStones(),
            snapshot(stones(placement(1, 1, Stone.BLACK)), placement(1, 1, Stone.BLACK)));

    assertFalse(
        markerlessDelta.allowsIncrementalSync(),
        "markerless sync input must rebuild as SNAPSHOT instead of fabricating a MOVE node.");
    assertTrue(
        markedDelta.allowsIncrementalSync(),
        "a real marker still authorizes incremental MOVE sync for the added stone.");
  }

  private static Stone[] emptyStones() {
    Stone[] stones = new Stone[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
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

  private static int[] snapshot(Stone[] stones) {
    return snapshot(stones, null);
  }

  private static int[] snapshot(Stone[] stones, Placement lastMove) {
    int[] snapshot = new int[BOARD_AREA];
    for (int index = 0; index < BOARD_AREA; index++) {
      int x = index % BOARD_SIZE;
      int y = index / BOARD_SIZE;
      Stone stone = stones[stoneIndex(x, y)];
      snapshot[index] = stone.isBlack() ? 1 : stone.isWhite() ? 2 : 0;
    }
    if (lastMove != null) {
      snapshot[lastMove.y * BOARD_SIZE + lastMove.x] = lastMove.color.isBlack() ? 3 : 4;
    }
    return snapshot;
  }

  private static int[] snapshotWithMarkers(Stone[] stones, Placement... markers) {
    int[] snapshot = snapshot(stones);
    for (Placement marker : markers) {
      snapshot[marker.y * BOARD_SIZE + marker.x] = marker.color.isBlack() ? 3 : 4;
    }
    return snapshot;
  }

  private static int stoneIndex(int x, int y) {
    return x * BOARD_SIZE + y;
  }

  private static Placement placement(int x, int y, Stone color) {
    return new Placement(x, y, color);
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
}
