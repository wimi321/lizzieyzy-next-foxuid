package featurecat.lizzie.analysis;

import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.SnapshotEngineRestore;

final class ExactSnapshotEngineRestore {
  private ExactSnapshotEngineRestore() {}

  static boolean restoreIfNeeded(Leelaz engine, BoardData snapshotData) {
    return SnapshotEngineRestore.restoreExactSnapshotIfNeeded(engine, snapshotData);
  }
}
