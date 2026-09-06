package featurecat.lizzie.analysis;

import java.util.Objects;

/** Bridge between a stable ReadBoard frame and the tracking controller. */
public final class ReadBoardTrackingEligibilityAdapter {
  public enum Reason {
    STABLE,
    HELPER_NOT_CURRENT,
    NO_ACCEPTED_FRAME,
    FIRST_FRAME,
    FRAME_PENDING,
    HOLD,
    SYNCING,
    PENDING_LOCAL_MOVE,
    GMA,
    ENGINE_UNRESTORED,
    ENGINE_UNAVAILABLE,
    NODE_MISMATCH,
    RETIRED
  }

  public static final class Snapshot {
    private final Object identity;
    private final long revision;
    private final Object nodeIdentity;
    private final long boardRevision;
    private final Reason reason;
    private final long admissionEpoch;

    public Snapshot(
        Object identity,
        long revision,
        Object nodeIdentity,
        long boardRevision,
        Reason reason,
        long admissionEpoch) {
      this.identity = Objects.requireNonNull(identity, "identity");
      this.revision = revision;
      this.nodeIdentity = nodeIdentity;
      this.boardRevision = boardRevision;
      this.reason = Objects.requireNonNull(reason, "reason");
      this.admissionEpoch = admissionEpoch;
    }

    public Object identity() {
      return identity;
    }

    public long revision() {
      return revision;
    }

    public Object nodeIdentity() {
      return nodeIdentity;
    }

    public long boardRevision() {
      return boardRevision;
    }

    public Reason reason() {
      return reason;
    }

    public boolean stable() {
      return reason == Reason.STABLE;
    }

    public boolean retainsContext() {
      return nodeIdentity != null
          && (stable()
              || reason == Reason.FRAME_PENDING
              || reason == Reason.SYNCING
              || reason == Reason.FIRST_FRAME);
    }

    boolean matches(TrackingAnalysisController.ReadBoardContext context) {
      return context != null
          && stable()
          && identity == context.identity()
          && revision == context.revision()
          && nodeIdentity == context.nodeIdentity()
          && boardRevision == context.boardRevision();
    }

    boolean sameFrame(Snapshot other) {
      return other != null
          && stable()
          && other.stable()
          && identity == other.identity
          && revision == other.revision
          && admissionEpoch == other.admissionEpoch
          && nodeIdentity == other.nodeIdentity
          && boardRevision == other.boardRevision;
    }
  }

  public interface EligibilitySource {
    Snapshot snapshot();

    boolean observeEligibility(Snapshot expected, Runnable invalidated, Runnable settled);
  }

  private final TrackingAnalysisController controller;
  private final EligibilitySource source;
  private final java.util.function.Supplier<TrackingAnalysisController.Context> currentContext;

  public ReadBoardTrackingEligibilityAdapter(
      TrackingAnalysisController controller,
      EligibilitySource source,
      java.util.function.Supplier<TrackingAnalysisController.Context> currentContext) {
    this.controller = Objects.requireNonNull(controller, "controller");
    this.source = Objects.requireNonNull(source, "source");
    this.currentContext = Objects.requireNonNull(currentContext, "currentContext");
  }

  public TrackingAnalysisController.AddResult addPoint(
      String coordinate, TrackingAnalysisController.Context context) {
    TrackingAnalysisController.ReadBoardContext readBoardContext =
        context.readBoardContext().orElse(null);
    Snapshot before = source.snapshot();
    if (!before.matches(readBoardContext)) {
      return TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE;
    }
    if (!source.observeEligibility(
        before,
        () -> controller.contextInvalidated(context),
        () -> controller.contextSettled(context, currentContext.get()))) {
      return TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE;
    }
    return controller.addPoint(
        coordinate,
        context,
        () -> {
          Snapshot admission = source.snapshot();
          if (!admission.matches(readBoardContext)) {
            return null;
          }
          return () ->
              context.matches(currentContext.get()) && admission.sameFrame(source.snapshot());
        });
  }
}
