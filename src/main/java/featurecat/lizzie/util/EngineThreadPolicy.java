package featurecat.lizzie.util;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import featurecat.lizzie.gui.EngineData;
import featurecat.lizzie.util.KataGoAutoSetupHelper.SetupSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Transport-derived ownership of client-generated KataGo search-thread settings. */
public final class EngineThreadPolicy {
  private EngineThreadPolicy() {}

  public enum Source {
    CFG,
    BENCHMARK
  }

  public static Source source(EngineData entry) {
    return entry != null
            && entry.threadPolicy != null
            && "BENCHMARK".equals(entry.threadPolicy.optString("source"))
        ? Source.BENCHMARK
        : Source.CFG;
  }

  public static int recommendedThreads(EngineData entry) {
    if (entry == null || entry.threadPolicy == null) return 0;
    try {
      String value = String.valueOf(entry.threadPolicy.opt("katago-benchmark-threads"));
      int threads = Integer.parseInt(value);
      return Math.max(0, threads);
    } catch (NumberFormatException invalid) {
      return 0;
    }
  }

  public static EngineData findSavedEntry(String id) {
    if (id == null || id.isBlank() || Lizzie.config == null) return null;
    for (EngineData entry : Utils.getEngineData()) {
      if (id.equals(entry.id)) return entry;
    }
    return null;
  }

  public static String message(String key) {
    return Lizzie.resourceBundle.getString("EngineThreadPolicy." + key);
  }

  public static EngineData saveSource(String id, Source source) throws IOException {
    synchronized (Utils.class) {
      ArrayList<EngineData> entries = Utils.getEngineData();
      EngineData target = entries.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
      if (target == null) throw new IOException(message("targetDeleted"));
      if (source == Source.BENCHMARK && recommendedThreads(target) <= 0) {
        throw new IOException(message("invalidRecommendation"));
      }
      target.threadPolicy.put("source", source.name());
      target.threadPolicy.put("sourceRevision", target.threadPolicy.optLong("sourceRevision") + 1L);
      try {
        Utils.saveEngineSettings(entries);
      } catch (java.io.UncheckedIOException failure) {
        throw failure.getCause();
      }
      return target;
    }
  }

  /** Zero means CFG, remote-managed, or an explicit launch override; never consults global values. */
  public static int threadsForLaunch(EngineData entry, List<String> command) {
    if (entry == null
        || isRemoteManaged(entry.commands, entry.useJavaSSH)
        || !isLocalKataGoCommand(entry.commands, false)
        || KataGoRuntimeHelper.hasEffectiveNumSearchThreadsOverride(command)
        || source(entry) == Source.CFG) return 0;
    int threads = recommendedThreads(entry);
    if (threads <= 0) throw new IllegalStateException(message("invalidRecommendation"));
    return threads;
  }

  public static JSONObject environment(SetupSnapshot snapshot) throws IOException {
    if (snapshot == null) throw new IOException(message("unknownBenchmarkTarget"));
    JSONObject environment = new JSONObject();
    environment.put("engine", fileState(snapshot.enginePath));
    environment.put("config", fileState(snapshot.gtpConfigPath));
    environment.put("model", fileState(snapshot.activeWeightPath));
    environment.put("command", snapshot.discovery == null ? "" : snapshot.discovery.sourceCommand);
    environment.put("workingDirectory", snapshot.executionDirectory.toString());
    org.json.JSONArray additionalConfigs = new org.json.JSONArray();
    boolean firstConfig = true;
    for (int i = 2; i + 1 < snapshot.sourceArguments.size(); i++) {
      String option = snapshot.sourceArguments.get(i);
      if ("-config".equals(option) || "--config".equals(option)) {
        Path config = Path.of(snapshot.sourceArguments.get(++i));
        if (!firstConfig) additionalConfigs.put(fileState(config));
        firstConfig = false;
      }
    }
    if (!additionalConfigs.isEmpty()) environment.put("additionalConfigs", additionalConfigs);
    return environment;
  }

  private static JSONObject fileState(Path path) throws IOException {
    if (path == null || !Files.isRegularFile(path))
      throw new IOException(message("environmentUnknown"));
    return new JSONObject()
        .put("path", path.toAbsolutePath().normalize().toString())
        .put("size", Files.size(path))
        .put("modified", Files.getLastModifiedTime(path).toMillis());
  }

  public static String environmentStatus(EngineData entry) {
    if (recommendedThreads(entry) <= 0) return "";
    JSONObject previous = entry.threadPolicy.optJSONObject("environment");
    if (previous == null) return message("environmentUnknown");
    try {
      return previous.similar(environment(KataGoAutoSetupHelper.inspectSavedEngine(entry)))
          ? ""
          : message("environmentChanged");
    } catch (IOException | RuntimeException unavailable) {
      return message("environmentUnknown");
    }
  }

  static void migrateLegacyEntries(ArrayList<EngineData> entries, JSONObject legacyUi) {
    String signature = legacyUi.optString("katago-benchmark-signature", "");
    EngineData owner = null;
    int matches = 0;
    if (!legacyUi.optBoolean("engine-thread-policy-migrated", false)
        && !signature.isBlank()
        && legacyUi.optInt("katago-benchmark-threads", 0) > 0) {
      for (EngineData entry : entries) {
        if (!isLocalKataGoCommand(entry.commands, entry.useJavaSSH)) continue;
        SetupSnapshot snapshot = KataGoAutoSetupHelper.inspectSavedEngine(entry);
        if (snapshot != null
            && signature.equals(KataGoRuntimeHelper.buildBenchmarkSignature(snapshot))) {
          owner = entry;
          matches++;
        }
      }
    }
    for (EngineData entry : entries) {
      if (entry.threadPolicy != null) continue;
      entry.threadPolicy = new JSONObject().put("source", "CFG").put("sourceRevision", 0L);
      if (entry == owner && matches == 1) {
        for (String key : legacyUi.keySet()) {
          if (key.startsWith("katago-benchmark-")
              || key.equals(featurecat.lizzie.util.katago.tuning.KataGoTuningStore.KEY)) {
            entry.threadPolicy.put(key, legacyUi.get(key));
          }
        }
        if (legacyUi.optBoolean("autoload-kata-engine-threads", false)
            || legacyUi.optBoolean("chk-kata-engine-threads", false)) {
          entry.threadPolicy.put("source", "BENCHMARK");
        }
        try {
          entry.threadPolicy.put(
              "environment", environment(KataGoAutoSetupHelper.inspectSavedEngine(entry)));
        } catch (IOException unavailable) {
          // The attributable result remains usable; missing metadata only recommends retesting.
        }
      }
    }
  }

  public static boolean isRemoteManaged(String command, boolean useJavaSsh) {
    command = decodedCommand(command);
    return useJavaSsh
        || RemoteComputeConfig.isRemoteComputeEngineCommand(command)
        || isExternalSshCommand(command);
  }

  public static boolean isRemoteManaged(Leelaz engine) {
    return engine != null
        && (engine.useRemoteCompute || isRemoteManaged(engine.engineCommand(), engine.useJavaSSH));
  }

  public static boolean isRemoteManaged(List<String> command) {
    return command != null
        && !command.isEmpty()
        && (RemoteComputeConfig.isRemoteComputeEngineCommand(command.get(0))
            || isExternalSshExecutable(command.get(0)));
  }

  public static boolean isExternalSshCommand(String command) {
    List<String> tokens = Utils.splitCommand(decodedCommand(command));
    return tokens != null && !tokens.isEmpty() && isExternalSshExecutable(tokens.get(0));
  }

  private static boolean isExternalSshExecutable(String token) {
    String executable = token.replace('\\', '/');
    executable = executable.substring(executable.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    return executable.equals("ssh")
        || executable.equals("ssh.exe")
        || executable.equals("plink")
        || executable.equals("plink.exe");
  }

  public static boolean isLocalKataGoCommand(String command, boolean useJavaSsh) {
    if (isRemoteManaged(command, useJavaSsh)) {
      return false;
    }
    List<String> tokens = Utils.splitCommand(decodedCommand(command));
    return tokens != null
        && !tokens.isEmpty()
        && !tokens.get(0).contains("://")
        && KataGoAutoSetupHelper.looksLikeKataGoExecutable(tokens.get(0));
  }

  private static String decodedCommand(String command) {
    if (command == null) {
      return "";
    }
    return command.startsWith("encryption||") ? Utils.doDecrypt2(command.substring(12)) : command;
  }
}
