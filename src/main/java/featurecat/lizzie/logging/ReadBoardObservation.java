package featurecat.lizzie.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReadBoardObservation {
  private static final Logger DIAG = LoggerFactory.getLogger(LogCategories.READBOARD);
  private static final Logger TRACE = LoggerFactory.getLogger(LogCategories.READBOARD_TRACE);

  private ReadBoardObservation() {}

  public static boolean diagnosticsEnabled() {
    try {
      return initialized() && DIAG.isDebugEnabled();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  public static boolean traceEnabled() {
    try {
      return initialized()
          && LoggingRuntime.current()
              .filter(LoggingRuntime::fullTraceActive)
              .isPresent()
          && TRACE.isInfoEnabled();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static boolean initialized() {
    try {
      return LoggingRuntime.current().filter(runtime -> !runtime.isShutdown()).isPresent();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  public static void inContext(
      String engineId, String gmaId, String sessionId, Runnable action) {
    if (action == null) {
      return;
    }
    String trace =
        LoggingRuntime.current().map(LoggingRuntime::currentTraceSessionId).orElse(null);
    try (CorrelationContext.Scope scope =
        CorrelationContext.openScope()
            .installEngine(engineId)
            .installGma(gmaId)
            .installSyncSession(
                sessionId == null || sessionId.isEmpty() ? null : normalizeSession(sessionId))) {
      if (trace != null) {
        scope.installTraceSession(trace);
      }
      action.run();
    }
  }

  public static void recordDecision(String result, String reason, long epoch, String platform) {
    try {
      if (result == null || result.isEmpty()) {
        return;
      }
      String safeReason = reason == null ? "unknown" : reason;
      String safePlatform = platform == null ? "unknown" : platform;
      if (diagnosticsEnabled()) {
        DIAG.debug(
            "readboard event=decision result={} reason={} epoch={} platform={}",
            result,
            safeReason,
            epoch,
            safePlatform);
      }
      if (traceEnabled()) {
        TRACE.info(
            "readboard raw decision result={} reason={} epoch={} platform={}",
            result,
            safeReason,
            epoch,
            safePlatform);
      }
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordGma(String phase, String outcome) {
    try {
      if (!diagnosticsEnabled()) {
        return;
      }
      DIAG.debug(
          "readboard event=gma phase={} outcome={}",
          phase == null || phase.isEmpty() ? "unknown" : phase,
          outcome == null || outcome.isEmpty() ? "unknown" : outcome);
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordLocalMove(String outcome, String reason) {
    try {
      if (!diagnosticsEnabled()) {
        return;
      }
      DIAG.debug(
          "readboard event=local-move outcome={} reason={}",
          outcome == null || outcome.isEmpty() ? "unknown" : outcome,
          reason == null || reason.isEmpty() ? "unknown" : reason);
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordTrackingEligibility(String event, String reason, boolean retained) {
    try {
      if (diagnosticsEnabled()) {
        DIAG.debug(
            "readboard event=tracking-eligibility phase={} reason={} retained={}",
            event,
            reason,
            retained);
      }
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordFailure(String reason, Throwable error) {
    try {
      if (!initialized() || !DIAG.isWarnEnabled()) {
        return;
      }
      String safeReason = reason == null || reason.isEmpty() ? "unknown" : reason;
      if (error == null) {
        DIAG.warn("readboard event=failed reason={}", safeReason);
        return;
      }
      DIAG.warn("readboard event=failed reason={}", safeReason, error);
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordLifecycle(String event, String detail) {
    try {
      if (!initialized() || !DIAG.isInfoEnabled()) {
        return;
      }
      DIAG.info(
          "readboard event={} detail={}",
          event == null || event.isEmpty() ? "lifecycle" : event,
          detail == null ? "" : detail);
    } catch (RuntimeException ignored) {
    }
  }

  public static void recordYikeSession(
      String reason,
      String eventSession,
      long eventTimeMillis,
      String activeSession,
      Boolean syncReady,
      Boolean geometryReady,
      String pendingSession) {
    try {
      if (!diagnosticsEnabled()) {
        return;
      }
      String safeEventSession = normalizeSession(eventSession);
      String safeActiveSession = normalizeSession(activeSession);
      String safePendingSession = normalizeSession(pendingSession);
      String safeReason = normalizeReason(reason);
      try (CorrelationContext.Scope scope =
          CorrelationContext.openScope().installSyncSession(safeEventSession)) {
        DIAG.debug(
            "readboard event=yike-session reason={} eventSession={} eventTimeMillis={} active={} "
                + "pending={} syncReady={} geometryReady={}",
            safeReason,
            safeEventSession,
            eventTimeMillis,
            safeActiveSession,
            safePendingSession,
            syncReady,
            geometryReady);
      }
    } catch (RuntimeException ignored) {
    }
  }

  private static String normalizeReason(String reason) {
    if (reason == null || reason.isEmpty() || reason.length() > 80) {
      return "unknown";
    }
    for (int index = 0; index < reason.length(); index++) {
      char current = reason.charAt(index);
      boolean safe =
          current >= 'A' && current <= 'Z'
              || current >= 'a' && current <= 'z'
              || current >= '0' && current <= '9'
              || current == '-'
              || current == '_'
              || current == '.'
              || current == ':';
      if (!safe) {
        return "unknown";
      }
    }
    return reason;
  }

  private static String normalizeSession(String session) {
    if (session == null || session.isEmpty() || "none".equals(session)) {
      return "none";
    }
    if (session.length() > 96) {
      return "invalid";
    }
    for (int index = 0; index < session.length(); index++) {
      char current = session.charAt(index);
      boolean safe =
          current >= 'A' && current <= 'Z'
              || current >= 'a' && current <= 'z'
              || current >= '0' && current <= '9'
              || current == '-'
              || current == '_'
              || current == '.'
              || current == ':';
      if (!safe) {
        return "invalid";
      }
    }
    return session;
  }

  public static void traceProtocol(String summary) {
    try {
      if (!traceEnabled() || summary == null) {
        return;
      }
      TRACE.info("readboard raw protocol={}", ObservationText.boundedRawEvent(summary));
    } catch (RuntimeException ignored) {
    }
  }

  public static void traceInbound(String summary) {
    try {
      if (!traceEnabled() || summary == null || summary.isEmpty()) {
        return;
      }
      TRACE.info("readboard {}", ObservationText.boundedRawEvent(summary));
    } catch (RuntimeException ignored) {
    }
  }
}
