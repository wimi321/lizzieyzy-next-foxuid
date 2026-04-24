package featurecat.lizzie.analysis;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.TrackingConsolePane;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.util.CommandLaunchHelper;
import featurecat.lizzie.util.KataGoRuntimeHelper;
import featurecat.lizzie.util.Utils;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public class TrackingEngine {
  private Process process;
  private BufferedReader inputStream;
  private BufferedOutputStream outputStream;
  private ScheduledExecutorService executor;
  private volatile boolean isLoaded = false;
  private volatile List<MoveData> currentTrackedMoves = new ArrayList<>();
  private final AtomicInteger requestId = new AtomicInteger(0);
  private volatile TrackingConsolePane consolePane;
  private final java.util.Map<Integer, java.util.Set<String>> pendingTrackedCoords =
      new java.util.concurrent.ConcurrentHashMap<>();

  public void startEngine(String engineCommand) {
    String analysisCommand = toAnalysisCommand(engineCommand);
    CommandLaunchHelper.LaunchSpec launchSpec =
        CommandLaunchHelper.prepare(Utils.splitCommand(analysisCommand));
    List<String> commands = launchSpec.getCommandParts();
    Path engineExecutable = KataGoRuntimeHelper.resolveCommandExecutable(commands);
    if (Config.isBundledKataGoCommand(analysisCommand)) {
      try {
        KataGoRuntimeHelper.ensureBundledRuntimeReady(engineExecutable, Lizzie.frame);
      } catch (IOException e) {
        isLoaded = false;
        updateConsoleTitle(getStatusString("statusFailed"));
        return;
      }
    }
    List<String> launchCommands =
        KataGoRuntimeHelper.prepareBundledLaunchCommand(commands, engineExecutable);
    ProcessBuilder processBuilder = new ProcessBuilder(launchCommands);
    CommandLaunchHelper.configureProcessBuilder(processBuilder, launchSpec);
    KataGoRuntimeHelper.configureBundledProcessBuilder(processBuilder, engineExecutable);
    processBuilder.redirectErrorStream(true);
    try {
      process = processBuilder.start();
    } catch (IOException e) {
      process = null;
      isLoaded = false;
      updateConsoleTitle(getStatusString("statusFailed"));
      return;
    }
    try {
      inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
      outputStream = new BufferedOutputStream(process.getOutputStream());
      executor = Executors.newSingleThreadScheduledExecutor();
    } catch (Throwable t) {
      cleanupPartialStart();
      isLoaded = false;
      updateConsoleTitle(getStatusString("statusFailed"));
      return;
    }
    if (Lizzie.frame != null && Lizzie.frame.trackingEngine != this) {
      cleanupPartialStart();
      isLoaded = false;
      return;
    }
    isLoaded = true;
    try {
      executor.execute(this::readLoop);
    } catch (Throwable t) {
      isLoaded = false;
      cleanupPartialStart();
      updateConsoleTitle(getStatusString("statusFailed"));
    }
  }

  private void cleanupPartialStart() {
    try {
      if (outputStream != null) outputStream.close();
    } catch (IOException ignored) {
    }
    try {
      if (inputStream != null) inputStream.close();
    } catch (IOException ignored) {
    }
    if (process != null && process.isAlive()) process.destroyForcibly();
  }

  private String getStatusString(String key) {
    try {
      return Lizzie.resourceBundle.getString("TrackingConsolePane." + key);
    } catch (Exception e) {
      return key;
    }
  }

  private volatile boolean engineReady = false;

  private void readLoop() {
    try {
      String line;
      while ((line = inputStream.readLine()) != null) {
        if (line.startsWith("{")) {
          if (!engineReady) {
            engineReady = true;
            updateConsoleTitle(Lizzie.resourceBundle.getString("TrackingConsolePane.statusReady"));
          }
          parseResult(line);
        } else {
          TrackingConsolePane cp = consolePane;
          if (cp != null) cp.addLine(line);
          if (!engineReady) {
            updateConsoleStatus(line);
          }
        }
      }
    } catch (IOException e) {
      // engine closed
    }
    isLoaded = false;
    updateConsoleTitle(Lizzie.resourceBundle.getString("TrackingConsolePane.statusClosed"));
  }

  private void updateConsoleStatus(String line) {
    String status = null;
    if (line.contains("Loading model")) {
      status = Lizzie.resourceBundle.getString("TrackingConsolePane.statusLoadingModel");
    } else if (line.contains("Loading neural net")) {
      status = Lizzie.resourceBundle.getString("TrackingConsolePane.statusLoadingModel");
    } else if (line.startsWith("Tuning") || line.startsWith("Started OpenCL SGEMM")) {
      status = Lizzie.resourceBundle.getString("TrackingConsolePane.statusTuning");
    } else if (line.contains("ready to begin")) {
      engineReady = true;
      status = Lizzie.resourceBundle.getString("TrackingConsolePane.statusReady");
    }
    if (status != null) {
      updateConsoleTitle(status);
    }
  }

  private void updateConsoleTitle(String status) {
    TrackingConsolePane cp = consolePane;
    if (cp != null) {
      String baseTitle = Lizzie.resourceBundle.getString("TrackingConsolePane.title");
      javax.swing.SwingUtilities.invokeLater(() -> cp.setTitle(baseTitle + " - " + status));
    }
  }

  private void parseResult(String line) {
    try {
      featurecat.lizzie.rules.Board board = Lizzie.board;
      featurecat.lizzie.gui.LizzieFrame frame = Lizzie.frame;
      if (board == null || frame == null) return;
      int expectedId = requestId.get();
      JSONObject result = new JSONObject(line);
      if (!result.has("moveInfos")) return;
      if (!result.has("id")) return;
      String id = result.getString("id");
      if (!id.equals("track-" + expectedId)) return;
      JSONArray moveInfos = result.getJSONArray("moveInfos");
      boolean isBlack = board.getHistory().isBlacksTurn();
      List<MoveData> moves = Utils.getBestMovesFromJsonArray(moveInfos, true, isBlack);
      java.util.Set<String> allowed = pendingTrackedCoords.get(expectedId);
      if (allowed != null && !allowed.isEmpty()) {
        List<MoveData> filtered = new ArrayList<>(moves.size());
        for (MoveData mv : moves) {
          if (mv.coordinate != null && allowed.contains(mv.coordinate)) {
            filtered.add(mv);
          }
        }
        moves = filtered;
      }
      if (requestId.get() != expectedId) return;
      if (!pendingTrackedCoords.containsKey(expectedId)) return;
      currentTrackedMoves = moves;
      if (Lizzie.frame != null) frame.refresh(1);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void sendTrackingRequest(BoardHistoryNode node, Set<String> trackedCoords) {
    if (!isLoaded || trackedCoords.isEmpty() || Lizzie.board == null) {
      clearTrackedMoves();
      return;
    }
    boolean isBlack = Lizzie.board.getHistory().isBlacksTurn();
    int currentId = requestId.incrementAndGet();
    java.util.Set<String> snapshot = new java.util.LinkedHashSet<>(trackedCoords);
    pendingTrackedCoords.keySet().removeIf(k -> k < currentId);
    pendingTrackedCoords.put(currentId, snapshot);
    int totalVisits =
        Math.max(
            Lizzie.config.trackingEngineMaxVisits,
            Lizzie.config.trackingEngineMaxVisits * snapshot.size());
    JSONObject request =
        AnalysisRequestBuilder.buildRequest(
            "track-" + currentId, node, totalVisits, false, false, false);
    request.put("reportDuringSearchEvery", 0.1);
    request.put("allowMoves", buildAllowMoves(snapshot, isBlack));
    sendCommand(request.toString());
  }

  static JSONArray buildAllowMoves(Set<String> coords, boolean isBlackToPlay) {
    JSONArray allowMoves = new JSONArray();
    if (coords.isEmpty()) return allowMoves;
    JSONObject entry = new JSONObject();
    entry.put("player", isBlackToPlay ? "B" : "W");
    entry.put("untilDepth", 1);
    JSONArray movesArray = new JSONArray();
    for (String coord : coords) {
      movesArray.put(coord);
    }
    entry.put("moves", movesArray);
    allowMoves.put(entry);
    return allowMoves;
  }

  private void sendCommand(String command) {
    try {
      outputStream.write((command + "\n").getBytes());
      outputStream.flush();
    } catch (Exception e) {
      e.printStackTrace();
      isLoaded = false;
      updateConsoleTitle(getStatusString("statusClosed"));
    }
  }

  static String toAnalysisCommand(String cmd) {
    // Only replace the standalone 'gtp' subcommand token (preceded by whitespace,
    // followed by whitespace or end-of-string). Avoids touching paths that contain "gtp".
    String result = cmd.replaceFirst("(?i)(\\s)gtp(\\s|$)", "$1analysis$2");
    String analysisDefaults = "numAnalysisThreads=1,nnMaxBatchSize=8";
    if (!result.toLowerCase().contains("-override-config")) {
      result += " -override-config " + analysisDefaults;
    } else {
      for (String param : analysisDefaults.split(",")) {
        String key = param.split("=")[0];
        if (!result.contains(key)) {
          result = result.replaceFirst("(-override-config\\s+\"?)", "$1" + param + ",");
        }
      }
    }
    return result;
  }

  public List<MoveData> getCurrentTrackedMoves() {
    return new ArrayList<>(currentTrackedMoves);
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  public void clearTrackedMoves() {
    requestId.incrementAndGet();
    currentTrackedMoves = new ArrayList<>();
    pendingTrackedCoords.clear();
  }

  public void setConsolePane(TrackingConsolePane pane) {
    this.consolePane = pane;
  }

  public TrackingConsolePane getConsolePane() {
    return consolePane;
  }

  public void shutdown() {
    isLoaded = false;
    if (executor != null) executor.shutdownNow();
    try {
      if (outputStream != null) outputStream.close();
    } catch (IOException ignored) {
    }
    try {
      if (inputStream != null) inputStream.close();
    } catch (IOException ignored) {
    }
    if (executor != null) {
      try {
        executor.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (process != null && process.isAlive()) {
      process.destroyForcibly();
      try {
        process.waitFor(2, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    pendingTrackedCoords.clear();
  }
}
