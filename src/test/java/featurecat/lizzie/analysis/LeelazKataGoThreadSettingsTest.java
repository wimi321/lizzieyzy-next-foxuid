package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.remote.RemoteComputeConfig;
import featurecat.lizzie.gui.GtpConsolePane;
import featurecat.lizzie.util.KataGoAutoSetupHelper;
import featurecat.lizzie.util.KataGoRuntimeHelper;
import featurecat.lizzie.util.Utils;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeelazKataGoThreadSettingsTest {
  @TempDir Path root;

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void explicitLocalThreadOverrideSurvivesActualProcessInitialization() throws Exception {
    Path executable =
        Files.writeString(
            root.resolve("katago"),
            """
        #!/usr/bin/python3
        import sys,pathlib,re
        p=pathlib.Path(__file__).parent
        (p/'launch-args').write_text('\\n'.join(sys.argv[1:]))
        for line in sys.stdin:
            with (p/'gtp-commands').open('a') as f: f.write(line)
            match=re.match(r'(?:(\\d+) )?(.*)',line.strip())
            ident=match[1] or ''
            command=match[2]
            answer={'name':'KataGo','version':'1.15.3','list_commands':'protocol_version\\nname\\nversion'}.get(command,'')
            print('='+ident+' '+answer+'\\n',flush=True)
            if command=='quit': break
        """);
    assertTrue(executable.toFile().setExecutable(true));
    try (Environment env =
        new Environment("\"" + executable + "\" gtp -override-config numSearchThreads=12")) {
      var data = new featurecat.lizzie.gui.EngineData();
      data.commands = env.engine.engineCommand();
      data.name = "Controlled KataGo";
      data.width = data.height = 19;
      Utils.saveEngineSettings(new java.util.ArrayList<>(List.of(data)));
      env.engine.startEngine(0);
      Path log = root.resolve("gtp-commands");
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while ((!Files.exists(log) || !Files.readString(log).contains("analysisWideRootNoise 0.2"))
          && System.nanoTime() < deadline) Thread.sleep(10);
      String commands = Files.readString(log);
      assertTrue(commands.contains("analysisWideRootNoise 0.2"), commands);
      assertFalse(commands.contains("numSearchThreads"), commands);
      assertTrue(Files.readString(root.resolve("launch-args")).contains("numSearchThreads=12"));
    }
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
  void localBenchmarkResultAfterForegroundSwitchDoesNotRetuneRemote() throws Exception {
    try (Environment env = new Environment(RemoteComputeConfig.COMMAND_CUSTOM_WS)) {
      Path executable =
          Files.writeString(
              root.resolve("katago"),
              "#!/bin/sh\nprintf '%s\\n' \"$@\" > \""
                  + root.resolve("benchmark-argv")
                  + "\"\n"
                  + "echo 'numSearchThreads = 8: (baseline) (recommended)'\n");
      assertTrue(executable.toFile().setExecutable(true));
      Path cfg = Files.writeString(root.resolve("gtp.cfg"), "numSearchThreads=12");
      Path model = Files.writeString(root.resolve("model.bin.gz"), "fixture model");
      var snapshot =
          KataGoAutoSetupHelper.inspectSelectedLocalKataGo(executable, cfg, model).toSnapshot();
      env.engine.isLoaded = true;
      env.engine.isKatago = true;
      var result =
          KataGoRuntimeHelper.runBenchmarkAndApply(
              snapshot, (status, downloaded, total) -> Lizzie.leelaz = env.engine, null);
      assertEquals(8, result.recommendedThreads);
      assertSame(env.engine, Lizzie.leelaz);
      KataGoRuntimeHelper.applyBenchmarkResultToRunningEngines(result);
      assertEquals("", env.commands());
      assertEquals(8, KataGoRuntimeHelper.getStoredBenchmarkResult().recommendedThreads);
      assertTrue(Files.readString(root.resolve("benchmark-argv")).contains("benchmark\n"));
      assertEquals("numSearchThreads=12", Files.readString(cfg));
    }
  }

  @Test
  void remoteInitializationAndBenchmarkApplicationPreserveServerThreads() throws Exception {
    for (String command :
        List.of(
            RemoteComputeConfig.COMMAND_ZHIZI,
            RemoteComputeConfig.COMMAND_CUSTOM_WS,
            "katago gtp",
            "ssh host katago gtp -override-config numSearchThreads=12",
            "\"C:\\Program Files\\PuTTY\\plink.exe\" host katago gtp",
            "/usr/bin/ssh host katago gtp")) {
      try (Environment env = new Environment(command)) {
        env.engine.useJavaSSH = command.equals("katago gtp");
        env.initialize();
        String startup = env.commands();
        assertFalse(startup.contains("numSearchThreads"), command + ": " + startup);
        assertTrue(startup.contains("analysisWideRootNoise 0.2"), startup);
        assertEquals(command, env.engine.engineCommand());

        Lizzie.leelaz = env.engine;
        env.engine.isLoaded = true;
        Lizzie.config.uiConfig.put("katago-benchmark-threads", 8);
        KataGoRuntimeHelper.applyBenchmarkResultToRunningEngines(
            KataGoRuntimeHelper.getStoredBenchmarkResult());
        assertFalse(env.commands().contains("numSearchThreads"), env.commands());

        env.engine.sendCommand("kata-set-param numSearchThreads 12");
        assertTrue(env.commands().contains("kata-set-param numSearchThreads 12"));
      }
    }
  }

  @Test
  void localInitializationStillAppliesThreadsWithoutConfusingSshInPaths() throws Exception {
    try (Environment env = new Environment("/engines/ssh-data/katago gtp")) {
      env.initialize();
      assertTrue(env.commands().contains("kata-set-param numSearchThreads 6"), env.commands());
      assertTrue(env.commands().contains("analysisWideRootNoise 0.2"));
    }
  }

  @Test
  void legacyThreadSavingCannotChangeRemoteContext() throws Exception {
    try (Environment env = new Environment(RemoteComputeConfig.COMMAND_ZHIZI)) {
      String before = Lizzie.config.uiConfig.toString();
      Utils.saveLegacyKataGoThreadSettings(env.engine, false, false, "19");
      assertEquals("6", Lizzie.config.txtKataEngineThreads);
      assertTrue(Lizzie.config.chkKataEngineThreads);
      assertTrue(Lizzie.config.autoLoadKataEngineThreads);
      assertEquals(before, Lizzie.config.uiConfig.toString());
      assertEquals("", env.commands());
    }
    try (Environment env = new Environment("katago gtp")) {
      Utils.saveLegacyKataGoThreadSettings(env.engine, true, true, " 9 ");
      assertEquals("9", Lizzie.config.uiConfig.getString("txt-kata-engine-threads"));
      assertTrue(env.commands().contains("kata-set-param numSearchThreads 9"));
    }
  }

  @Test
  void manualBenchmarkRejectsSshAndUnknownExecutablesBeforeStartingProcess() throws Exception {
    try (Environment env = new Environment("katago gtp")) {
      for (String name : List.of("ssh", "plink.exe", "unknown-engine")) {
        Path engine =
            Files.writeString(
                root.resolve(name), "#!/bin/sh\ntouch \"" + root.resolve("started") + "\"\n");
        engine.toFile().setExecutable(true);
        Path cfg = Files.writeString(root.resolve("gtp.cfg"), "numSearchThreads=12");
        Path model = Files.writeString(root.resolve("model.bin.gz"), "fixture model");
        var snapshot =
            KataGoAutoSetupHelper.inspectSelectedLocalKataGo(engine, cfg, model).toSnapshot();
        assertThrows(
            java.io.IOException.class,
            () -> KataGoRuntimeHelper.runBenchmark(snapshot, null, null));
        assertFalse(Files.exists(root.resolve("started")), name);
      }
    }
  }

  private final class Environment implements AutoCloseable {
    final GtpConsolePane oldConsole = Lizzie.gtpConsole;
    final featurecat.lizzie.gui.LizzieFrame oldFrame = Lizzie.frame;
    final Config oldConfig = Lizzie.config;
    final Leelaz oldEngine = Lizzie.leelaz;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final Leelaz engine;

    Environment(String command) throws Exception {
      Lizzie.config = ConfigTestHelper.createForTests(root);
      Lizzie.config.uiConfig = new org.json.JSONObject();
      Lizzie.config.leelazConfig = new org.json.JSONObject();
      Lizzie.config.config = new org.json.JSONObject();
      java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      Lizzie.gtpConsole =
          (QuietConsole)
              ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(QuietConsole.class);
      Lizzie.frame =
          (QuietFrame) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(QuietFrame.class);
      Lizzie.config.firstLoadKataGo = false;
      Lizzie.config.chkKataEngineThreads = true;
      Lizzie.config.autoLoadKataEngineThreads = true;
      Lizzie.config.txtKataEngineThreads = "6";
      Lizzie.config.autoLoadKataEngineWRN = true;
      Lizzie.config.autoLoadTxtKataEngineWRN = "0.2";
      engine = new Leelaz(command);
      engine.installFreshCommandOutputForTest(output);
      engine.started = true;
    }

    void initialize() throws Exception {
      engine.dispatchReaderLineForTest("= protocol_version");
      engine.dispatchReaderLineForTest("=");
      engine.isCheckingName = true;
      engine.dispatchReaderLineForTest("= KataGo");
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
      while (!commands().contains("kata-get-rules") && System.nanoTime() < deadline) {
        Thread.sleep(5);
      }
      assertTrue(commands().contains("kata-get-rules"), commands());
      engine.cancelParameterRead();
    }

    String commands() {
      return output.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
      engine.isNormalEnd = true;
      engine.forceQuit();
      Lizzie.config = oldConfig;
      Lizzie.leelaz = oldEngine;
      Lizzie.gtpConsole = oldConsole;
      Lizzie.frame = oldFrame;
    }
  }

  private static final class QuietConsole extends GtpConsolePane {
    private QuietConsole() {
      super(null);
    }

    @Override
    public void addLine(String line) {}
  }

  private static final class QuietFrame extends featurecat.lizzie.gui.LizzieFrame {
    @Override
    public void refresh() {}

    @Override
    public void resetTitle() {}
  }
}
