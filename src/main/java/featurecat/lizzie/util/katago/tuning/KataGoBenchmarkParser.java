package featurecat.lizzie.util.katago.tuning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parser for the stable human-readable output emitted by KataGo 1.17 and 1.18 benchmark. */
public final class KataGoBenchmarkParser {
  private static final String NUMBER =
      "(?:[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?|NaN|Infinity)";
  private static final Pattern BACKEND_PATTERN =
      Pattern.compile("You are currently using the (.+?) version of KataGo\\.");
  private static final Pattern CURRENT_THREADS_PATTERN =
      Pattern.compile("Your GTP config is currently set to use numSearchThreads\\s*=\\s*(\\d+)");
  private static final Pattern RECOMMENDED_THREADS_PATTERN =
      Pattern.compile("^numSearchThreads\\s*=\\s*(\\d+):.*\\(recommended\\)\\s*$");
  private static final Pattern DETAILED_METRICS_PATTERN =
      Pattern.compile(
          "^numSearchThreads\\s*=\\s*(\\d+):\\s*(\\d+)\\s*/\\s*(\\d+)\\s+positions,"
              + "\\s*visits/s\\s*=\\s*("
              + NUMBER
              + ")\\s+nnEvals/s\\s*=\\s*("
              + NUMBER
              + ")\\s+nnBatches/s\\s*=\\s*("
              + NUMBER
              + ")\\s+avgBatchSize\\s*=\\s*("
              + NUMBER
              + ")(?:\\s+.*)?$");
  private static final Pattern ADDITIONAL_SERVER_THREADS_PATTERN =
      Pattern.compile(
          "^ADDITIONAL RECOMMENDATION:.*\\bset\\s+numNNServerThreadsPerModel\\s*=\\s*(\\d+)\\b.*$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern ADDITIONAL_BATCH_SIZE_PATTERN =
      Pattern.compile(
          "^ADDITIONAL RECOMMENDATION:.*\\bset\\s+nnMaxBatchSize\\s*=\\s*(\\d+)\\b.*$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SERVER_THREADS_ASSIGNMENT_PATTERN =
      Pattern.compile("^numNNServerThreadsPerModel\\s*=\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

  private KataGoBenchmarkParser() {}

  /**
   * Parses one process output. When an explicit single thread count was requested, pass it in so a
   * successful fixed-thread benchmark need not contain KataGo's final {@code (recommended)} marker.
   */
  public static KataGoBenchmarkObservation parse(String output, int explicitThreads) {
    if (explicitThreads < 0 || explicitThreads > 4096) {
      throw new IllegalArgumentException("explicitThreads must be between 0 and 4096");
    }

    String backend = "";
    int currentThreads = 0;
    int recommendedThreads = 0;
    int recommendedNnServerThreadsPerModel = 0;
    int recommendedMaxBatchSize = 0;
    boolean mpsGraphInitialized = false;
    boolean coreMlInitialized = false;
    boolean failureDetected = false;
    boolean gpuTuningInProgress = false;
    boolean recoverableGpuTuningError = false;
    boolean additionalTuningStarted = false;
    boolean serverThreadRecommendationBlock = false;
    Map<Integer, KataGoBenchmarkObservation.ThreadMetrics> metricsByThread =
        new LinkedHashMap<Integer, KataGoBenchmarkObservation.ThreadMetrics>();

    String normalizedOutput = output == null ? "" : output;
    String[] lines = normalizedOutput.split("\\r\\n|[\\r\\n]", -1);
    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }

      Matcher backendMatcher = BACKEND_PATTERN.matcher(line);
      if (backendMatcher.find()) {
        backend = backendMatcher.group(1).trim();
      }

      Matcher currentMatcher = CURRENT_THREADS_PATTERN.matcher(line);
      if (currentMatcher.find()) {
        currentThreads = parseInteger(currentMatcher.group(1));
      }

      Matcher recommendedMatcher = RECOMMENDED_THREADS_PATTERN.matcher(line);
      if (recommendedMatcher.matches()) {
        recommendedThreads = parseInteger(recommendedMatcher.group(1));
      }

      if (line.startsWith("Running additional tests of a few other settings")) {
        additionalTuningStarted = true;
      }

      Matcher serverThreadsMatcher = ADDITIONAL_SERVER_THREADS_PATTERN.matcher(line);
      if (serverThreadsMatcher.matches()) {
        recommendedNnServerThreadsPerModel = parseInteger(serverThreadsMatcher.group(1));
        serverThreadRecommendationBlock = false;
      } else if (line.startsWith(
          "ADDITIONAL RECOMMENDATION: 2 NN server threads per GPU measured faster.")) {
        serverThreadRecommendationBlock = true;
      }
      if (serverThreadRecommendationBlock) {
        Matcher assignmentMatcher = SERVER_THREADS_ASSIGNMENT_PATTERN.matcher(line);
        if (assignmentMatcher.matches()) {
          recommendedNnServerThreadsPerModel = parseInteger(assignmentMatcher.group(1));
          serverThreadRecommendationBlock = false;
        }
      }

      Matcher batchSizeMatcher = ADDITIONAL_BATCH_SIZE_PATTERN.matcher(line);
      if (batchSizeMatcher.matches()) {
        recommendedMaxBatchSize = parseInteger(batchSizeMatcher.group(1));
      }

      Matcher metricsMatcher = DETAILED_METRICS_PATTERN.matcher(line);
      if (metricsMatcher.matches() && !additionalTuningStarted) {
        KataGoBenchmarkObservation.ThreadMetrics metrics = parseMetrics(metricsMatcher);
        if (metrics != null) {
          metricsByThread.put(metrics.numSearchThreads(), metrics);
        }
      }

      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.contains("gpu mode - using mpsgraph")
          || lower.contains("initialized mpsgraph gpu-only mode")) {
        mpsGraphInitialized = true;
      }
      if (lower.contains("mux ane mode - using coreml")) {
        coreMlInitialized = true;
      }

      if (line.startsWith("Beginning GPU tuning")) {
        if (gpuTuningInProgress && recoverableGpuTuningError) {
          failureDetected = true;
        }
        gpuTuningInProgress = true;
        recoverableGpuTuningError = false;
      }
      if (isFailureLine(lower)) {
        if (gpuTuningInProgress
            && lower.startsWith("error:")
            && !isFailureLine(lower.substring("error:".length()).trim())) {
          recoverableGpuTuningError = true;
        } else {
          failureDetected = true;
        }
      }
      if (gpuTuningInProgress && line.equals("Done tuning")) {
        gpuTuningInProgress = false;
        recoverableGpuTuningError = false;
      }
    }

    failureDetected |= recoverableGpuTuningError;

    if (recommendedThreads <= 0 && explicitThreads > 0) {
      recommendedThreads = explicitThreads;
    }

    return new KataGoBenchmarkObservation(
        backend,
        currentThreads,
        recommendedThreads,
        recommendedNnServerThreadsPerModel,
        recommendedMaxBatchSize,
        new ArrayList<KataGoBenchmarkObservation.ThreadMetrics>(metricsByThread.values()),
        mpsGraphInitialized,
        coreMlInitialized,
        failureDetected);
  }

  private static KataGoBenchmarkObservation.ThreadMetrics parseMetrics(Matcher matcher) {
    try {
      return new KataGoBenchmarkObservation.ThreadMetrics(
          Integer.parseInt(matcher.group(1)),
          Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)),
          Double.parseDouble(matcher.group(4)),
          Double.parseDouble(matcher.group(5)),
          Double.parseDouble(matcher.group(6)),
          Double.parseDouble(matcher.group(7)));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static int parseInteger(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static boolean isFailureLine(String lowerLine) {
    return lowerLine.startsWith("error:")
        || lowerLine.startsWith("fatal:")
        || lowerLine.contains("fatal error")
        || lowerLine.contains("segmentation fault")
        || lowerLine.contains("sigsegv")
        || lowerLine.contains("core dumped")
        || lowerLine.contains("uncaught exception")
        || lowerLine.contains("terminate called")
        || lowerLine.contains("failed to load model")
        || lowerLine.contains("invalid metaldevicetousethread");
  }
}
