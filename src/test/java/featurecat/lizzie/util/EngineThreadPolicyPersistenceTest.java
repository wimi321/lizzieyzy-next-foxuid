package featurecat.lizzie.util;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.EngineData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineThreadPolicyPersistenceTest {
  @TempDir Path root;

  @Test
  void failedCatalogSavePreservesPublishedConfiguration() throws Exception {
    Config previous = Lizzie.config;
    try {
      Lizzie.config = ConfigTestHelper.createForTests(root);
      Lizzie.config.config = new JSONObject();
      Lizzie.config.uiConfig = new JSONObject();
      Lizzie.config.leelazConfig = new JSONObject();
      EngineData entry = new EngineData();
      entry.commands = "katago gtp";
      entry.name = "original";
      Utils.saveEngineSettings(new ArrayList<>(List.of(entry)));
      String stored = Files.readString(root.resolve("config.txt"));
      var edited = Utils.getEngineData();
      edited.get(0).name = "unsaved";
      Files.move(root.resolve("config.txt"), root.resolve("saved-config"));
      Files.createDirectory(root.resolve("config.txt"));
      Files.writeString(root.resolve("config.txt/blocker"), "occupied");

      assertThrows(java.io.UncheckedIOException.class, () -> Utils.saveEngineSettings(edited));
      assertEquals("original", Utils.getEngineData().get(0).name);
      assertEquals(stored, Files.readString(root.resolve("saved-config")));
    } finally {
      Lizzie.config = previous;
    }
  }

  @Test
  void identicalCommandsKeepIndependentIdentityAcrossReorderAndReload() throws Exception {
    Config previous = Lizzie.config;
    try {
      Lizzie.config = ConfigTestHelper.createForTests(root);
      Lizzie.config.config = new JSONObject();
      Lizzie.config.uiConfig = new JSONObject();
      Lizzie.config.leelazConfig = new JSONObject();
      EngineData first = new EngineData();
      first.commands = "katago gtp";
      first.name = "A";
      EngineData second = new EngineData();
      second.commands = first.commands;
      second.name = "B";
      Utils.saveEngineSettings(new ArrayList<>(List.of(first, second)));
      var initial =
          new JSONObject(Files.readString(root.resolve("config.txt")))
              .getJSONObject("leelaz")
              .getJSONArray("engine-settings-list");
      String firstId = initial.getJSONObject(0).getString("id");
      String secondId = initial.getJSONObject(1).getString("id");
      assertNotEquals(firstId, secondId);
      var entries = Utils.getEngineData();
      var moved = entries.remove(0);
      moved.name = "renamed A";
      moved.commands = "katago gtp -config changed.cfg";
      entries.add(moved);
      Utils.saveEngineSettings(entries);
      JSONObject disk = new JSONObject(Files.readString(root.resolve("config.txt")));
      Lizzie.config.leelazConfig = disk.getJSONObject("leelaz");
      var reloaded = Utils.getEngineData();
      assertEquals(secondId, Utils.engineDataToJson(reloaded.get(0)).getString("id"));
      assertEquals(firstId, Utils.engineDataToJson(reloaded.get(1)).getString("id"));
      assertEquals("renamed A", reloaded.get(1).name);
    } finally {
      Lizzie.config = previous;
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void benchmarkMergesOnlyFrozenEntryAndPreservesLatestChoice() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      EngineData a = env.entry("A");
      EngineData b = env.entry("B");
      Utils.saveEngineSettings(new ArrayList<>(List.of(a, b)));
      var target =
          KataGoRuntimeHelper.captureBenchmarkTarget(
              KataGoAutoSetupHelper.inspectSavedEngine(a), false);
      var result =
          KataGoRuntimeHelper.runBenchmarkAndApply(
              target,
              (status, count, total) -> {
                if (count == 90) {
                  var entries = Utils.getEngineData();
                  var moved = entries.remove(0);
                  moved.name = "renamed A";
                  entries.add(moved);
                  Utils.saveEngineSettings(entries);
                }
              },
              null);
      assertEquals(8, result.recommendedThreads);
      assertEquals(
          EngineThreadPolicy.Source.CFG,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(a.id)));
      assertEquals(
          8, EngineThreadPolicy.recommendedThreads(EngineThreadPolicy.findSavedEntry(a.id)));
      assertEquals(
          0, EngineThreadPolicy.recommendedThreads(EngineThreadPolicy.findSavedEntry(b.id)));
      assertFalse(Lizzie.config.uiConfig.has("katago-benchmark-threads"));
      EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.BENCHMARK);
      var retry =
          KataGoRuntimeHelper.captureBenchmarkTarget(
              KataGoAutoSetupHelper.inspectSavedEngine(EngineThreadPolicy.findSavedEntry(a.id)),
              false);
      KataGoRuntimeHelper.runBenchmarkAndApply(
          retry,
          (status, count, total) -> {
            if (count == 90) {
              try {
                EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.CFG);
              } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
              }
            }
          },
          null);
      assertEquals(
          EngineThreadPolicy.Source.CFG,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(a.id)));
      assertThrows(
          java.io.IOException.class,
          () -> KataGoRuntimeHelper.runBenchmarkAndApply(target, null, null));
      JSONObject disk = new JSONObject(Files.readString(root.resolve("config.txt")));
      Lizzie.config.leelazConfig = disk.getJSONObject("leelaz");
      assertEquals(
          8, EngineThreadPolicy.recommendedThreads(EngineThreadPolicy.findSavedEntry(a.id)));
      assertEquals("renamed A", EngineThreadPolicy.findSavedEntry(a.id).name);
      assertTrue(Files.readString(root.resolve("argv")).contains("benchmark"));
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void failedAndCancelledRerunsKeepRecommendationAndDeletedTargetsStayDeleted() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      EngineData a = env.entry("A");
      Utils.saveEngineSettings(new ArrayList<>(List.of(a)));
      KataGoRuntimeHelper.runBenchmarkAndApply(env.capture(a), null, null);
      EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.BENCHMARK);
      Files.writeString(root.resolve("fail"), "yes");
      var failure =
          assertThrows(
              java.io.IOException.class,
              () -> KataGoRuntimeHelper.runBenchmarkAndApply(env.capture(a), null, null));
      assertTrue(failure.getMessage().contains("controlled failure"), failure.getMessage());
      assertEquals(
          8, EngineThreadPolicy.recommendedThreads(EngineThreadPolicy.findSavedEntry(a.id)));
      assertEquals(
          EngineThreadPolicy.Source.BENCHMARK,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(a.id)));
      Files.delete(root.resolve("fail"));
      var session = new KataGoAutoSetupHelper.DownloadSession();
      assertThrows(
          KataGoAutoSetupHelper.DownloadCancelledException.class,
          () ->
              KataGoRuntimeHelper.runBenchmarkAndApply(
                  env.capture(a),
                  (status, count, total) -> {
                    if (count == 90) session.cancel();
                  },
                  session));
      assertEquals(
          8, EngineThreadPolicy.recommendedThreads(EngineThreadPolicy.findSavedEntry(a.id)));
      var target = env.capture(a);
      assertThrows(
          java.io.IOException.class,
          () ->
              KataGoRuntimeHelper.runBenchmarkAndApply(
                  target,
                  (status, count, total) -> {
                    if (count == 90) Utils.saveEngineSettings(new ArrayList<>());
                  },
                  null));
      assertTrue(Utils.getEngineData().isEmpty());
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void resultSaveFailureLeavesPreviousRecordAndSourceIntact() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      EngineData a = env.entry("A");
      Utils.saveEngineSettings(new ArrayList<>(List.of(a)));
      KataGoRuntimeHelper.runBenchmarkAndApply(env.capture(a), null, null);
      EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.BENCHMARK);
      var target = env.capture(a);
      String before = EngineThreadPolicy.findSavedEntry(a.id).threadPolicy.toString();
      Files.move(root.resolve("config.txt"), root.resolve("valid-config"));
      Files.createDirectory(root.resolve("config.txt"));
      Files.writeString(root.resolve("config.txt/occupied"), "occupied");
      assertThrows(
          java.io.IOException.class,
          () -> KataGoRuntimeHelper.runBenchmarkAndApply(target, null, null));
      assertEquals(before, EngineThreadPolicy.findSavedEntry(a.id).threadPolicy.toString());
      assertEquals(
          before,
          new JSONObject(Files.readString(root.resolve("valid-config")))
              .getJSONObject("leelaz")
              .getJSONArray("engine-settings-list")
              .getJSONObject(0)
              .getJSONObject("threadPolicy")
              .toString());
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void changedEnvironmentRetainsRecommendationWhileNewSetupHonorsSourceRevision() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      EngineData a = env.entry("A");
      a.threadPolicy =
          new JSONObject().put("source", "CFG").put("sourceRevision", 0).put("initialSetup", true);
      Utils.saveEngineSettings(new ArrayList<>(List.of(a)));
      var target =
          KataGoRuntimeHelper.captureBenchmarkTarget(
              KataGoAutoSetupHelper.inspectSavedEngine(a), true);
      EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.CFG);
      KataGoRuntimeHelper.runBenchmarkAndApply(target, null, null);
      assertEquals(
          EngineThreadPolicy.Source.CFG,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(a.id)));
      Files.writeString(root.resolve("gtp.cfg"), "numSearchThreads=3\n# changed environment");
      EngineData stored = EngineThreadPolicy.findSavedEntry(a.id);
      assertEquals(
          EngineThreadPolicy.message("environmentChanged"),
          EngineThreadPolicy.environmentStatus(stored));
      stored = EngineThreadPolicy.saveSource(a.id, EngineThreadPolicy.Source.BENCHMARK);
      var launch =
          KataGoRuntimeHelper.applyEntryLaunchPolicy(
              Utils.splitCommand(stored.commands), env.engine, stored);
      assertTrue(String.join(" ", launch).contains("numSearchThreads=8"));
      Files.delete(root.resolve("model.bin.gz"));
      assertEquals(
          EngineThreadPolicy.message("environmentUnknown"),
          EngineThreadPolicy.environmentStatus(stored));
      assertEquals(8, EngineThreadPolicy.recommendedThreads(stored));
    }
  }

  @Test
  void attributableLegacyResultMigratesOnceAndKeepsExistingPolicy() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      Files.writeString(root.resolve("analysis.cfg"), "numSearchThreads=3");
      EngineData a = env.entry("A");
      var snapshot = KataGoAutoSetupHelper.inspectSavedEngine(a);
      StringBuilder legacySignature = new StringBuilder();
      for (Path path :
          new Path[] {
            snapshot.enginePath,
            snapshot.gtpConfigPath,
            snapshot.analysisConfigPath,
            snapshot.activeWeightPath
          }) {
        if (path == null) legacySignature.append("|missing");
        else {
          legacySignature.append('|').append(path.toAbsolutePath().normalize());
          if (Files.exists(path))
            legacySignature
                .append(':')
                .append(Files.size(path))
                .append(':')
                .append(Files.getLastModifiedTime(path).toMillis());
          else legacySignature.append(":0:0");
        }
      }
      Lizzie.config
          .uiConfig
          .put("katago-benchmark-threads", 6)
          .put("katago-benchmark-signature", legacySignature.toString())
          .put("autoload-kata-engine-threads", true);
      Lizzie.config.leelazConfig.put(
          "engine-settings-list", new org.json.JSONArray().put(Utils.engineDataToJson(a)));
      EngineData migrated = Utils.normalizeEngineSettings().get(0);
      assertEquals(6, EngineThreadPolicy.recommendedThreads(migrated));
      assertEquals(EngineThreadPolicy.Source.BENCHMARK, EngineThreadPolicy.source(migrated));
      EngineThreadPolicy.saveSource(migrated.id, EngineThreadPolicy.Source.CFG);
      Lizzie.config.uiConfig.put("katago-benchmark-threads", 12);
      var reloaded = Utils.getEngineData().get(0);
      assertEquals(migrated.id, reloaded.id);
      assertEquals(6, EngineThreadPolicy.recommendedThreads(reloaded));
      assertEquals(EngineThreadPolicy.Source.CFG, EngineThreadPolicy.source(reloaded));
    }
  }

  @Test
  void unownedLegacyManualValueDoesNotBecomeBenchmarkHistory() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      EngineData a = env.entry("A");
      EngineData b = env.entry("B");
      Lizzie.config
          .uiConfig
          .put("txt-kata-engine-threads", "12")
          .put("autoload-kata-engine-threads", true);
      Lizzie.config.leelazConfig.put(
          "engine-settings-list",
          new org.json.JSONArray().put(Utils.engineDataToJson(a)).put(Utils.engineDataToJson(b)));
      for (EngineData migrated : Utils.normalizeEngineSettings()) {
        assertEquals(EngineThreadPolicy.Source.CFG, EngineThreadPolicy.source(migrated));
        assertEquals(0, EngineThreadPolicy.recommendedThreads(migrated));
        assertTrue(migrated.threadPolicy.getBoolean("legacyOverrideStopped"));
      }
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void explicitNewSetupEnablesOnlyItsFirstSuccessfulRecommendation() throws Exception {
    try (BenchmarkEnvironment env = new BenchmarkEnvironment()) {
      var snapshot =
          KataGoAutoSetupHelper.inspectSelectedLocalKataGo(
                  env.engine, root.resolve("gtp.cfg"), root.resolve("model.bin.gz"))
              .toSnapshot();
      Files.writeString(root.resolve("analysis.cfg"), "numSearchThreads=3");
      snapshot =
          KataGoAutoSetupHelper.inspectSelectedLocalKataGo(
                  env.engine, root.resolve("gtp.cfg"), root.resolve("model.bin.gz"))
              .toSnapshot();
      var setup = KataGoAutoSetupHelper.applyAutoSetup(snapshot, false);
      assertTrue(setup.createdEngine);
      var target = KataGoRuntimeHelper.captureBenchmarkTarget(setup.snapshot, true);
      KataGoRuntimeHelper.runBenchmarkAndApply(target, null, null);
      assertEquals(
          EngineThreadPolicy.Source.BENCHMARK,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(target.entryId)));
      EngineThreadPolicy.saveSource(target.entryId, EngineThreadPolicy.Source.CFG);
      KataGoRuntimeHelper.runBenchmarkAndApply(
          KataGoRuntimeHelper.captureBenchmarkTarget(setup.snapshot, true), null, null);
      assertEquals(
          EngineThreadPolicy.Source.CFG,
          EngineThreadPolicy.source(EngineThreadPolicy.findSavedEntry(target.entryId)));
    }
  }

  private final class BenchmarkEnvironment implements AutoCloseable {
    private final Config previous = Lizzie.config;
    private final featurecat.lizzie.analysis.EngineManager previousManager = Lizzie.engineManager;
    private final Path engine;

    private BenchmarkEnvironment() throws Exception {
      Lizzie.config = ConfigTestHelper.createForTests(root);
      Lizzie.config.config = new JSONObject();
      Lizzie.config.uiConfig = new JSONObject();
      Lizzie.config.leelazConfig = new JSONObject();
      Lizzie.engineManager = null;
      engine =
          Files.writeString(
              root.resolve("katago"),
              """
              #!/usr/bin/python3
              import pathlib,sys
              root=pathlib.Path(__file__).parent
              (root/'argv').write_text('\\n'.join(sys.argv[1:]))
              if (root/'fail').exists():
                  print('controlled failure',flush=True)
                  sys.exit(7)
              print('numSearchThreads = 8: (baseline) (recommended)',flush=True)
              """);
      assertTrue(engine.toFile().setExecutable(true));
      Files.writeString(root.resolve("gtp.cfg"), "numSearchThreads=3");
      Files.writeString(root.resolve("model.bin.gz"), "fixture model");
    }

    private EngineData entry(String name) {
      EngineData entry = new EngineData();
      entry.commands =
          "\""
              + engine
              + "\" gtp -config \""
              + root.resolve("gtp.cfg")
              + "\" -model \""
              + root.resolve("model.bin.gz")
              + "\"";
      entry.name = name;
      entry.width = entry.height = 19;
      return entry;
    }

    private KataGoRuntimeHelper.BenchmarkTarget capture(EngineData entry) throws Exception {
      return KataGoRuntimeHelper.captureBenchmarkTarget(
          KataGoAutoSetupHelper.inspectSavedEngine(EngineThreadPolicy.findSavedEntry(entry.id)),
          false);
    }

    @Override
    public void close() {
      Lizzie.config = previous;
      Lizzie.engineManager = previousManager;
    }
  }
}
