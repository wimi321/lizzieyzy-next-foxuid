package featurecat.lizzie.analysis;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.FloatBoard;
import featurecat.lizzie.gui.LizzieFrame;
import featurecat.lizzie.gui.SMessage;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.ExtraStones;
import featurecat.lizzie.rules.Movelist;
import featurecat.lizzie.rules.Stone;
import featurecat.lizzie.rules.Zobrist;
import featurecat.lizzie.util.Utils;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReadBoard {
  private static final long PROCESS_EXIT_WAIT_TIMEOUT_MS = 1000L;
  private static final long PROCESS_DESTROY_WAIT_TIMEOUT_MS = 200L;

  public static boolean isLegacyNativeReadBoardAvailable() {
    File readBoardDir = new File("readboard");
    return new File(readBoardDir, "readboard.exe").canRead()
        || new File(readBoardDir, "readboard.bat").canRead();
  }

  public Process process;
  private InputStreamReader inputStream;
  private BufferedOutputStream outputStream;
  private ScheduledExecutorService executor;
  ArrayList<Integer> tempcount = new ArrayList<Integer>();
  // private long startSyncTime = 0;

  public boolean isLoaded = false;
  private int version = 220430;
  private String engineCommand;
  public String currentEnginename = "";
  private int port = -1;

  boolean firstcount = true;
  public int numberofcount = 0;
  public boolean firstSync = true;
  // public boolean syncBoth = Lizzie.config.syncBoth;
  private ReadBoardStream readBoardStream;
  private Socket socket;
  private ServerSocket s;
  private boolean noMsg = false;
  private boolean usePipe = true;
  private boolean needGenmove = false;
  private boolean showInBoard = false;
  private volatile boolean isSyncing = false;
  // private long startTime;
  private boolean javaReadBoard = false;
  private String javaReadBoardName = "readboard-1.6.2-shaded.jar";
  private boolean waitSocket = true;
  public boolean lastMovePlayByLizzie = false;
  private OptionalInt pendingFoxMoveNumber = OptionalInt.empty();
  private boolean waitingForReadBoardLocalMoveAck = false;
  private boolean hideFloadBoardBeforePlace = false;
  private boolean hideFromPlace = false;
  public boolean editMode = false;
  private final SyncConflictTracker conflictTracker = new SyncConflictTracker();
  private final SyncHistoryJumpTracker historyJumpTracker = new SyncHistoryJumpTracker();
  private final SyncLocalNavigationTracker localNavigationTracker =
      new SyncLocalNavigationTracker();
  private BoardHistoryNode lastResolvedSnapshotNode;

  private enum CompleteSnapshotRecovery {
    NO_CHANGE,
    SINGLE_MOVE_RECOVERY,
    HOLD,
    FORCE_REBUILD
  }

  private SyncSnapshotRebuildPolicy rebuildPolicy() {
    return new SyncSnapshotRebuildPolicy(Board.boardWidth);
  }

  private SyncSnapshotDiffChecker snapshotDiffChecker() {
    return new SyncSnapshotDiffChecker(Board.boardWidth);
  }

  public ReadBoard(boolean usePipe, boolean isJavaReadBoard) throws Exception {
    this.usePipe = usePipe;
    this.javaReadBoard = isJavaReadBoard;
    if (s != null && !s.isClosed()) {
      s.close();
    }
    if (usePipe) engineCommand = "readboard\\readboard.exe";
    else engineCommand = "readboard\\readboard.bat";
    startEngine(engineCommand, 0);
  }

  private void createSocketServer() {
    try {
      s = new ServerSocket(0);
      port = s.getLocalPort();
      waitSocket = false;
      while (true) {
        socket = s.accept();
        readBoardStream = new ReadBoardStream(this, socket);
        break;
      }
    } catch (Exception e) {
      if (!noMsg)
        Utils.showMsg(
            Lizzie.resourceBundle.getString("ReadBoard.port")
                + " "
                + port
                + " "
                + Lizzie.resourceBundle.getString("ReadBoard.portUsed")
                + e.getMessage());
      try {
        s.close();
      } catch (Exception e1) {
        e1.printStackTrace();
      }
      e.printStackTrace();
    }
  }

  public void startEngine(String engineCommand, int index) throws Exception {
    if (javaReadBoard) {
      File javaReadBoardJar = new File("readboard_java" + File.separator + javaReadBoardName);
      if (!javaReadBoardJar.exists()) {
        Utils.deleteDir(new File("readboard_java"));
        Utils.copyReadBoardJava(javaReadBoardName);
      }
      List<String> jvmArgs = new ArrayList<String>();
      jvmArgs.add("-Dsun.java2d.uiScale=1.0");
      if (Lizzie.javaVersion >= 17) {
        jvmArgs.add("--add-opens");
        jvmArgs.add("java.desktop/sun.awt=ALL-UNNAMED");
        jvmArgs.add("--add-opens");
        jvmArgs.add("java.desktop/java.awt=ALL-UNNAMED");
        jvmArgs.add("--add-opens");
        jvmArgs.add("java.base/java.lang=ALL-UNNAMED");
      }
      List<String> appArgs = new ArrayList<String>();
      appArgs.add(Lizzie.resourceBundle.getString("ReadBoard.language"));
      appArgs.add(Lizzie.config.useJavaLooks ? "true" : "false");
      appArgs.add(String.valueOf((int) Math.round(Config.frameFontSize * Lizzie.javaScaleFactor)));
      appArgs.add(String.valueOf(Board.boardWidth));
      appArgs.add(String.valueOf(Board.boardHeight));
      try {
        process = Utils.startJavaJar(javaReadBoardJar, appArgs, jvmArgs);
      } catch (Exception e) {
        Utils.showMsg(e.getLocalizedMessage());
      }
    } else {
      if (!usePipe) {
        waitSocket = true;
        noMsg = false;
        Runnable runnable2 =
            new Runnable() {
              public void run() {
                if (s == null || s.isClosed()) createSocketServer();
              }
            };
        Thread thread2 = new Thread(runnable2);
        thread2.start();
        int times = 300;
        while (waitSocket && times > 0) {
          Thread.sleep(10);
          times--;
        }
      }
      List<String> commands = new ArrayList<String>();
      commands.add(engineCommand);
      commands.add("yzy");
      commands.add(
          !LizzieFrame.toolbar.chkAutoPlayTime.isSelected()
                  || LizzieFrame.toolbar.txtAutoPlayTime.getText().equals("")
              ? " "
              : LizzieFrame.toolbar.txtAutoPlayTime.getText());
      commands.add(
          !LizzieFrame.toolbar.chkAutoPlayPlayouts.isSelected()
                  || LizzieFrame.toolbar.txtAutoPlayPlayouts.getText().equals("")
              ? " "
              : LizzieFrame.toolbar.txtAutoPlayPlayouts.getText());
      commands.add(
          !LizzieFrame.toolbar.chkAutoPlayFirstPlayouts.isSelected()
                  || LizzieFrame.toolbar.txtAutoPlayFirstPlayouts.getText().equals("")
              ? " "
              : LizzieFrame.toolbar.txtAutoPlayFirstPlayouts.getText());

      if (usePipe) commands.add("0");
      else commands.add("1");
      commands.add(Lizzie.resourceBundle.getString("ReadBoard.language"));
      if (usePipe) commands.add("-1");
      else commands.add(String.valueOf(port));
      ProcessBuilder processBuilder = new ProcessBuilder(commands);
      if (usePipe) processBuilder.directory(new File("readboard"));
      processBuilder.redirectErrorStream(true);
      try {
        process = processBuilder.start();
      } catch (IOException e) {
        if (!usePipe) {
          Utils.showMsg(e.getLocalizedMessage());
          SMessage msg = new SMessage();
          msg.setMessage(Lizzie.resourceBundle.getString("ReadBoard.loadFailed"), 2);
          s.close();
          return;
        } else {
          System.out.print(e.getLocalizedMessage());
          throw new Exception("Start pipe failed");
        }
      }
    }
    if (usePipe) {
      initializeStreams();
      executor = Executors.newSingleThreadScheduledExecutor();
      executor.execute(this::read);
    }
  }

  private void initializeStreams() throws UnsupportedEncodingException {
    inputStream = new InputStreamReader(process.getInputStream(), "UTF-8");
    outputStream = new BufferedOutputStream(process.getOutputStream());
  }

  private void read() {
    try {
      int c;
      StringBuilder line = new StringBuilder();
      // while ((c = inputStream.read()) != -1) {
      while ((c = inputStream.read()) != -1) {
        line.append((char) c);

        if ((c == '\n')) {
          try {
            parseLine(line.toString());
            if (!isLoaded) {
              isLoaded = true;
              if (!javaReadBoard) checkVersion();
            }
          } catch (Exception ex) {
            ex.printStackTrace();
          }
          line = new StringBuilder();
        }
      }
      // this line will be reached when BoardSync shuts down
      if (Lizzie.leelaz.isPondering()) Lizzie.leelaz.ponder();
      showInBoard = false;
      if (Lizzie.frame.floatBoard != null) {
        Lizzie.frame.floatBoard.setVisible(false);
      }
      System.out.println("Board synchronization tool process ended.");
      if (!javaReadBoard && !isLoaded) {
        try {
          new ProcessBuilder("powershell", "/c", "start", "readboard\\readboard.bat").start();
        } catch (IOException e) {
          try {
            new ProcessBuilder("powershell", "/c", "start", "readboard\\readboard.exe").start();
          } catch (Exception s) {
            s.printStackTrace();
          }
          e.printStackTrace();
        }
        SMessage msg = new SMessage();
        msg.setMessage(Lizzie.resourceBundle.getString("ReadBoard.loadFailed"), 2);
        shutdown();
      } else shutdown();
      // Do no exit for switching weights
      // System.exit(-1);
    } catch (IOException e) {
      e.printStackTrace();
      Lizzie.frame.bothSync = false;
      Lizzie.frame.syncBoard = false;
      // System.exit(-1);
    }
  }

  public void parseLine(String line) {
    // if (Lizzie.gtpConsole.isVisible())
    // Lizzie.gtpConsole.addLine(line);
    //  System.out.println(line);
    //    if (Lizzie.frame.isPlayingAgainstLeelaz) {
    //      if (Lizzie.frame.playerIsBlack && !Lizzie.board.getHistory().isBlacksTurn()) return;
    //      if (!Lizzie.frame.playerIsBlack && Lizzie.board.getHistory().isBlacksTurn()) return;
    //    }
    if (line.startsWith("playpon")) {
      String[] params = line.split(" ");
      if (params.length == 2) {
        if (params[1].startsWith("on")) {
          Lizzie.config.readBoardPonder = true;
        } else if (params[1].startsWith("off")) {
          Lizzie.config.readBoardPonder = false;
        }
      }
    }
    if (line.startsWith("re=")) {
      String[] params = line.substring(3).split(",");
      if (params.length == Board.boardWidth) {
        for (int i = 0; i < params.length; i++)
          tempcount.add(Integer.parseInt(params[i].substring(0, 1)));
      }
    }
    if (line.startsWith("foxMoveNumber")) {
      OptionalInt foxMoveNumber = parseFoxMoveNumber(line);
      if (foxMoveNumber.isPresent()) {
        pendingFoxMoveNumber = foxMoveNumber;
      }
    }
    if (line.startsWith("version")) {
      Lizzie.gtpConsole.addLineReadBoard("Board synchronization tool " + line + "\n");
      String[] params = line.trim().split(" ");
      if (Integer.parseInt(params[1]) < version) {
        SMessage msg = new SMessage();
        msg.setMessage(Lizzie.resourceBundle.getString("ReadBoard.versionCheckFaied"), 2);
      }
    }
    if (line.startsWith("error")) {
      Lizzie.gtpConsole.addLineReadBoard(line + (usePipe ? "" : "\n"));
    }
    if (line.startsWith("end")) {
      if (!isSyncing) syncBoardStones(false);
      clearPendingFoxMoveNumber();
      tempcount = new ArrayList<Integer>();
    }
    if (line.startsWith("clear")) {
      resetSyncTrackers();
      clearPendingFoxMoveNumber();
      Lizzie.board.clear(false);
      Lizzie.frame.refresh();
    }
    if (line.startsWith("start")) {
      clearPendingFoxMoveNumber();
      String[] params = line.trim().split(" ");
      if (params.length >= 3) {
        int boardWidth = Integer.parseInt(params[1]);
        int boardHeight = Integer.parseInt(params[2]);
        if (boardWidth != Board.boardWidth || boardHeight != Board.boardHeight) {
          resetSyncTrackers();
          Lizzie.board.reopen(boardWidth, boardHeight);
        } else {
          resetSyncTrackers();
          Lizzie.board.clear(false);
        }
      } else {
        resetSyncTrackers();
        Lizzie.board.clear(false);
      }
    }
    if (line.startsWith("sync")) {
      Lizzie.frame.syncBoard = true;
      if (!Lizzie.leelaz.isPondering()) Lizzie.leelaz.togglePonder();
    }
    if (line.startsWith("both")) {
      Lizzie.frame.bothSync = true;
      if (Lizzie.frame.floatBoard != null && Lizzie.frame.floatBoard.isVisible())
        Lizzie.frame.floatBoard.setEditButton();
    }
    if (line.startsWith("noboth")) {
      Lizzie.frame.bothSync = false;
      if (Lizzie.frame.floatBoard != null && Lizzie.frame.floatBoard.isVisible())
        Lizzie.frame.floatBoard.setEditButton();
    }
    if (line.startsWith("stopAutoPlay")) {
      LizzieFrame.toolbar.chkAutoPlay.setSelected(false);
      LizzieFrame.toolbar.isAutoPlay = false;
    }
    if (line.startsWith("endsync")) {
      noMsg = true;
      resetSyncTrackers();
      clearPendingFoxMoveNumber();
      tempcount = new ArrayList<Integer>();
      Lizzie.frame.syncBoard = false;
      if (Lizzie.frame.isAnaPlayingAgainstLeelaz) {
        Lizzie.frame.stopAiPlayingAndPolicy();
      }
      showInBoard = false;
      if (Lizzie.frame.floatBoard != null) {
        Lizzie.frame.floatBoard.setVisible(false);
      }
    }
    if (line.startsWith("stopsync")) {
      resetSyncTrackers();
      clearPendingFoxMoveNumber();
      tempcount = new ArrayList<Integer>();
      Lizzie.frame.syncBoard = false;
      if (Lizzie.frame.isAnaPlayingAgainstLeelaz) {
        Lizzie.frame.stopAiPlayingAndPolicy();
      }
      Lizzie.leelaz.nameCmd();
      showInBoard = false;
      if (Lizzie.frame.floatBoard != null) {
        Lizzie.frame.floatBoard.setVisible(false);
      }
    }
    if (line.startsWith("play")) {
      String[] params = line.trim().split(">");
      if (params.length == 3) {
        String[] playParams = params[2].trim().split(" ");
        int playouts = Integer.parseInt(playParams[1]);
        int firstPlayouts = Integer.parseInt(playParams[2]);
        int time = Integer.parseInt(playParams[0]);
        if (time > 0) {
          LizzieFrame.toolbar.txtAutoPlayTime.setText(String.valueOf(time));
          LizzieFrame.toolbar.chkAutoPlayTime.setSelected(true);
        } else {
          LizzieFrame.toolbar.txtAutoPlayTime.setText(
              String.valueOf(Lizzie.config.leelazConfig.getInt("max-game-thinking-time-seconds")));
          LizzieFrame.toolbar.chkAutoPlayTime.setSelected(true);
        }
        if (playouts > 0) {
          LizzieFrame.toolbar.txtAutoPlayPlayouts.setText(String.valueOf(playouts));
          LizzieFrame.toolbar.chkAutoPlayPlayouts.setSelected(true);
        } else LizzieFrame.toolbar.chkAutoPlayPlayouts.setSelected(false);
        if (firstPlayouts > 0) {
          LizzieFrame.toolbar.txtAutoPlayFirstPlayouts.setText(String.valueOf(firstPlayouts));
          LizzieFrame.toolbar.chkAutoPlayFirstPlayouts.setSelected(true);
        } else LizzieFrame.toolbar.chkAutoPlayFirstPlayouts.setSelected(false);
        if (params[1].equals("black")) {
          LizzieFrame.toolbar.chkAutoPlayBlack.setSelected(true);
          LizzieFrame.toolbar.chkAutoPlayWhite.setSelected(false);
          LizzieFrame.toolbar.chkAutoPlay.setSelected(true);
          LizzieFrame.toolbar.setChkShowBlack(true);
          LizzieFrame.toolbar.setChkShowWhite(true);
          Lizzie.config.UsePureNetInGame = false;
          Lizzie.frame.isAnaPlayingAgainstLeelaz = true;
          LizzieFrame.toolbar.isAutoPlay = true;
          Lizzie.frame.clearWRNforGame(false);
        } else if (params[1].equals("white")) {
          LizzieFrame.toolbar.chkAutoPlayBlack.setSelected(false);
          LizzieFrame.toolbar.chkAutoPlayWhite.setSelected(true);
          LizzieFrame.toolbar.chkAutoPlay.setSelected(true);
          LizzieFrame.toolbar.setChkShowBlack(true);
          LizzieFrame.toolbar.setChkShowWhite(true);
          Lizzie.config.UsePureNetInGame = false;
          Lizzie.frame.isAnaPlayingAgainstLeelaz = true;
          LizzieFrame.toolbar.isAutoPlay = true;
          Lizzie.frame.clearWRNforGame(false);
        }
        Lizzie.leelaz.ponder();
      }
    }
    if (line.startsWith("pass")) {
      Lizzie.board.changeNextTurn();
    }
    if (line.startsWith("firstchanged")) {
      String[] params = line.trim().split(" ");
      if (params.length == 2) {
        int firstPlayouts = Integer.parseInt(params[1]);
        if (firstPlayouts > 0) {
          LizzieFrame.toolbar.txtAutoPlayFirstPlayouts.setText(String.valueOf(firstPlayouts));
          LizzieFrame.toolbar.chkAutoPlayFirstPlayouts.setSelected(true);
        } else LizzieFrame.toolbar.chkAutoPlayFirstPlayouts.setSelected(false);
      }
    }
    if (line.startsWith("playoutschanged")) {
      String[] params = line.trim().split(" ");
      if (params.length == 2) {
        int playouts = Integer.parseInt(params[1]);
        if (playouts > 0) {
          LizzieFrame.toolbar.txtAutoPlayPlayouts.setText(String.valueOf(playouts));
          LizzieFrame.toolbar.chkAutoPlayPlayouts.setSelected(true);
        } else LizzieFrame.toolbar.chkAutoPlayPlayouts.setSelected(false);
      }
    }
    if (line.startsWith("timechanged")) {
      String[] params = line.trim().split(" ");
      if (params.length == 2) {
        int time = Integer.parseInt(params[1]);
        if (time > 0) {
          LizzieFrame.toolbar.txtAutoPlayTime.setText(String.valueOf(time));
          LizzieFrame.toolbar.chkAutoPlayTime.setSelected(true);
        } else {
          LizzieFrame.toolbar.txtAutoPlayTime.setText(
              String.valueOf(Lizzie.config.leelazConfig.getInt("max-game-thinking-time-seconds")));
          LizzieFrame.toolbar.chkAutoPlayTime.setSelected(true);
        }
      }
    }

    if (line.startsWith("noponder")) {
      if (Lizzie.frame.isPlayingAgainstLeelaz) {
        Lizzie.frame.isPlayingAgainstLeelaz = false;
        Lizzie.leelaz.isThinking = false;
      }
      if (Lizzie.frame.isAnaPlayingAgainstLeelaz) {
        Lizzie.frame.stopAiPlayingAndPolicy();
      }
      Lizzie.leelaz.togglePonder();
    }
    if (line.startsWith("noinboard")) {
      if (Lizzie.frame.floatBoard != null && Lizzie.frame.floatBoard.isVisible()) {
        Lizzie.frame.floatBoard.setVisible(false);
      }
    }
    if (line.startsWith("inboard")) {
      //	Lizzie.gtpConsole.addLine(line);
      if (hideFromPlace) return;
      showInBoard = true;
      String[] params = line.trim().split(" ");
      if (params.length == 6) {
        if (params[5].startsWith("99")) {
          String[] param = params[5].split("_");
          float factor = Float.parseFloat(param[1]);
          if (Lizzie.frame.floatBoard == null) {
            Lizzie.frame.floatBoard =
                new FloatBoard(
                    (int) Math.ceil(Integer.parseInt(params[1]) * factor),
                    (int) Math.ceil(Integer.parseInt(params[2]) * factor),
                    (int) Math.ceil(Integer.parseInt(params[3]) * factor),
                    (int) Math.ceil(Integer.parseInt(params[4]) * factor),
                    Integer.parseInt(param[2]),
                    true);
            // Lizzie.frame.floatBoard.setFactor(factor);
          } else {
            Lizzie.frame.floatBoard.setPos(
                (int) Math.ceil(Integer.parseInt(params[1]) * factor),
                (int) Math.ceil(Integer.parseInt(params[2]) * factor),
                (int) Math.ceil(Integer.parseInt(params[3]) * factor),
                (int) Math.ceil(Integer.parseInt(params[4]) * factor),
                Integer.parseInt(param[2]));
            //   Lizzie.frame.floatBoard.setFactor(factor);
          }
        } else {
          if (Lizzie.frame.floatBoard == null) {
            Lizzie.frame.floatBoard =
                new FloatBoard(
                    Integer.parseInt(params[1]),
                    Integer.parseInt(params[2]),
                    Integer.parseInt(params[3]),
                    Integer.parseInt(params[4]),
                    Integer.parseInt(params[5]),
                    false);
            Lizzie.frame.floatBoard.setBoardType();
          } else {
            Lizzie.frame.floatBoard.setPos(
                Integer.parseInt(params[1]),
                Integer.parseInt(params[2]),
                Integer.parseInt(params[3]),
                Integer.parseInt(params[4]),
                Integer.parseInt(params[5]));
          }
        }
      }
    }
    if (line.startsWith("notinboard")) {
      showInBoard = false;
      if (Lizzie.frame.floatBoard != null) {
        Lizzie.frame.floatBoard.setVisible(false);
      }
    }
    if (line.startsWith("foreFoxWithInBoard")) {
      hideFloadBoardBeforePlace = true;
    }
    if (line.startsWith("notForeFoxWithInBoard")) {
      hideFloadBoardBeforePlace = false;
    }
    if (line.startsWith("placeComplete")) {
      markLocalMoveCommandCompleted();
      if (hideFloadBoardBeforePlace && hideFromPlace) {
        hideFromPlace = false;
        if (Lizzie.frame.floatBoard != null) Lizzie.frame.floatBoard.setVisible(true);
      }
    }
  }

  private void syncBoardStones(boolean isSecondTime) {
    //    if (!this.javaReadBoard && !isSecondTime) {
    //      long thisTime = System.currentTimeMillis();
    //      if (thisTime - startSyncTime < Lizzie.config.readBoardArg2 / 2) return;
    //      startSyncTime = thisTime;
    //    }
    localNavigationTracker.startSyncPass(isSecondTime);
    if (tempcount.size() > Board.boardWidth * Board.boardHeight) {
      tempcount = new ArrayList<Integer>();
      resetSyncTrackers();
      return;
    }
    isSyncing = true;
    try {
      boolean needReSync = false;
      boolean played = false;
      boolean singleMoveRecovered = false;
      boolean holdLastMove = false;
      int lastX = 0;
      int lastY = 0;
      int playedMove = 0;
      boolean isLastBlack = false;
      BoardHistoryNode node = Lizzie.board.getHistory().getCurrentHistoryNode();
      BoardHistoryNode node2 = Lizzie.board.getHistory().getMainEnd();
      Stone[] syncStartStones = node2.getData().stones.clone();
      Stone[] stones = Lizzie.board.getHistory().getMainEnd().getData().stones;
      int[] currentSnapshotCodes = getSnapshotCodes();
      OptionalInt currentFoxMoveNumber = currentPendingFoxMoveNumber();
      SyncSnapshotClassifier classifier =
          new SyncSnapshotClassifier(Board.boardWidth, Board.boardHeight);
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta =
          classifier.summarizeDelta(syncStartStones, currentSnapshotCodes);
      if (!snapshotDiffChecker().isComparable(currentSnapshotCodes, stones)) {
        return;
      }
      acknowledgeLocalMoveIfSnapshotCaughtUp(stones, currentSnapshotCodes);

      boolean needRefresh = false;
      if (snapshotDelta.allowsIncrementalSync()) {
        for (int i = 0; i < tempcount.size(); i++) {
          int m = tempcount.get(i);
          int y = i / Board.boardWidth;
          int x = i % Board.boardWidth;
          if (((holdLastMove && m == 3) || m == 1) && !stones[Board.getIndex(x, y)].isBlack()) {
            if (stones[Board.getIndex(x, y)].isWhite()) {
              Lizzie.board.clear(false);
              needReSync = true;
              needRefresh = true;
              break;
            }
            if (!played) {
              moveToAnyPositionWithoutTracking(node2);
            }
            Lizzie.board.placeForSync(x, y, Stone.BLACK, true);
            if (node2.variations.size() > 0 && node2.variations.get(0).isEndDummay()) {
              node2.variations.add(0, node2.variations.get(node2.variations.size() - 1));
              node2.variations.remove(1);
              node2.variations.remove(node2.variations.size() - 1);
            }
            played = true;
            playedMove = playedMove + 1;
          }
          if (((holdLastMove && m == 4) || m == 2) && !stones[Board.getIndex(x, y)].isWhite()) {
            if (stones[Board.getIndex(x, y)].isBlack()) {
              Lizzie.board.clear(false);
              needReSync = true;
              needRefresh = true;
              break;
            }

            if (!played) {
              moveToAnyPositionWithoutTracking(node2);
            }
            Lizzie.board.placeForSync(x, y, Stone.WHITE, true);
            if (node2.variations.size() > 0 && node2.variations.get(0).isEndDummay()) {
              node2.variations.add(0, node2.variations.get(node2.variations.size() - 1));
              node2.variations.remove(1);
              node2.variations.remove(node2.variations.size() - 1);
            }
            played = true;
            playedMove = playedMove + 1;
          }

          if (!holdLastMove && m == 3 && !stones[Board.getIndex(x, y)].isBlack()) {
            if (stones[Board.getIndex(x, y)].isWhite()) {
              Lizzie.board.clear(false);
              needReSync = true;
              needRefresh = true;
              break;
            }
            holdLastMove = true;
            lastX = x;
            lastY = y;
            isLastBlack = true;
          }
          if (!holdLastMove && m == 4 && !stones[Board.getIndex(x, y)].isWhite()) {
            if (stones[Board.getIndex(x, y)].isBlack()) {
              Lizzie.board.clear(false);
              needReSync = true;
              needRefresh = true;
              break;
            }
            holdLastMove = true;
            lastX = x;
            lastY = y;
            isLastBlack = false;
          }
        }
        if (firstSync) {
          Lizzie.board.hasStartStone = true;
          Lizzie.board.addStartListAll();
          Lizzie.board.flatten();
        }
        // 落最后一步
        if (holdLastMove && !needReSync) {
          if (!played) {
            moveToAnyPositionWithoutTracking(node2);
          }
          Lizzie.board.placeForSync(lastX, lastY, isLastBlack ? Stone.BLACK : Stone.WHITE, true);
          if (node2.variations.size() > 0 && node2.variations.get(0).isEndDummay()) {
            node2.variations.add(0, node2.variations.get(node2.variations.size() - 1));
            node2.variations.remove(1);
            node2.variations.remove(node2.variations.size() - 1);
          }
          played = true;
          if (Lizzie.config.alwaysSyncBoardStat || showInBoard) lastMoveWithoutTracking();
        }
        stones = Lizzie.board.getHistory().getMainEnd().getData().stones;
        if (shouldResyncAfterIncrementalSync(stones, currentSnapshotCodes)) {
          needReSync = true;
        }
      } else {
        needReSync = true;
      }
      if (needReSync && !isSecondTime) {
        CompleteSnapshotRecovery recovery =
            resolveCompleteSnapshotRecovery(
                node2, syncStartStones, currentSnapshotCodes, snapshotDelta);
        if (recovery == CompleteSnapshotRecovery.HOLD) {
          return;
        }
        if (recovery == CompleteSnapshotRecovery.FORCE_REBUILD) {
          rebuildFromSnapshot(node2, currentSnapshotCodes, snapshotDelta, currentFoxMoveNumber);
          return;
        }
        needReSync = false;
        singleMoveRecovered = recovery == CompleteSnapshotRecovery.SINGLE_MOVE_RECOVERY;
        played = singleMoveRecovered;
        needRefresh = played;
      }
      if (!needReSync) {
        BoardHistoryNode currentSyncEndNode = Lizzie.board.getHistory().getMainEnd();
        if (singleMoveRecovered) {
          keepViewOnRecoveredMainEnd(currentSyncEndNode);
        }
        BoardHistoryNode currentNode =
            singleMoveRecovered ? currentSyncEndNode : resolveLocalNavigationTarget(node);
        if (shouldRebuildForFoxMetadataChange(
            currentSyncEndNode, currentFoxMoveNumber, currentSnapshotCodes)) {
          rebuildFromSnapshot(
              currentSyncEndNode, currentSnapshotCodes, snapshotDelta, currentFoxMoveNumber);
          return;
        }
        historyJumpTracker.clear();
        applySyncViewState(played, currentNode, currentSyncEndNode);
      }
      if (!needReSync) {
        conflictTracker.clear();
      }
      if (played || needRefresh) {
        Lizzie.frame.refresh();
      }
      if (firstSync) {
        firstSync = false;
        previousMoveWithoutTracking(true);
        new Thread() {
          public void run() {
            try {
              Thread.sleep(500);
            } catch (InterruptedException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }
            lastMoveWithoutTracking();
          }
        }.start();
      }
      if (Lizzie.frame.isPlayingAgainstLeelaz && needGenmove) {
        if (!Lizzie.board.getHistory().isBlacksTurn() && Lizzie.frame.playerIsBlack) {
          Lizzie.leelaz.genmove("W");
          needGenmove = false;
        } else if (!Lizzie.frame.playerIsBlack) {
          Lizzie.leelaz.genmove("B");
          needGenmove = false;
        }
      }
    } finally {
      localNavigationTracker.clear();
      isSyncing = false;
    }
    //	    if (played && Lizzie.config.alwaysGotoLastOnLive) {
    //	      int moveNumber = Lizzie.board.getHistory().getMainEnd().getData().moveNumber;
    //	      Lizzie.board.goToMoveNumberBeyondBranch(moveNumber);
    //	      Lizzie.frame.refresh();
    //	    }
  }

  private int[] getSnapshotCodes() {
    int[] codes = new int[tempcount.size()];
    for (int index = 0; index < tempcount.size(); index++) {
      codes[index] = tempcount.get(index);
    }
    return codes;
  }

  private OptionalInt parseFoxMoveNumber(String line) {
    String[] params = line.trim().split(" ");
    if (params.length != 2) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(params[1]));
    } catch (NumberFormatException ex) {
      return OptionalInt.empty();
    }
  }

  private OptionalInt currentPendingFoxMoveNumber() {
    if (pendingFoxMoveNumber == null) {
      return OptionalInt.empty();
    }
    return pendingFoxMoveNumber;
  }

  private void clearPendingFoxMoveNumber() {
    pendingFoxMoveNumber = OptionalInt.empty();
  }

  private boolean shouldRebuildForFoxMetadataChange(
      BoardHistoryNode syncEndNode, OptionalInt foxMoveNumber, int[] snapshotCodes) {
    if (!foxMoveNumber.isPresent()) {
      return false;
    }
    if (!syncEndMatchesSnapshot(syncEndNode, snapshotCodes)) {
      return false;
    }
    BoardData currentData = syncEndNode.getData();
    if (currentData.isHistoryActionNode()) {
      return false;
    }
    int moveNumber = foxMoveNumber.getAsInt();
    boolean expectedBlackToPlay = explicitPlayerOverride(currentData).orElse(moveNumber % 2 == 0);
    return currentData.moveNumber != moveNumber || currentData.blackToPlay != expectedBlackToPlay;
  }

  private Optional<Boolean> explicitPlayerOverride(BoardData data) {
    String player = data.getProperty("PL");
    if (player == null || player.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(!"W".equalsIgnoreCase(player));
  }

  private boolean syncEndMatchesSnapshot(BoardHistoryNode syncEndNode, int[] snapshotCodes) {
    return !snapshotDiffChecker()
        .hasDiff(snapshotCodes, syncEndNode.getData().stones, false, Optional.empty());
  }

  private CompleteSnapshotRecovery resolveCompleteSnapshotRecovery(
      BoardHistoryNode syncStartNode,
      Stone[] syncStartStones,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    OptionalInt foxMoveNumber = currentPendingFoxMoveNumber();
    if (isSyncAlreadyAtTarget(syncStartNode, snapshotCodes, foxMoveNumber)) {
      return CompleteSnapshotRecovery.NO_CHANGE;
    }
    if (snapshotDelta.hasMarker()
        && tryApplySingleMoveRecovery(syncStartNode, syncStartStones, snapshotCodes)) {
      return CompleteSnapshotRecovery.SINGLE_MOVE_RECOVERY;
    }
    if (shouldHoldConflictingSnapshot(syncStartNode, snapshotCodes)) {
      return CompleteSnapshotRecovery.HOLD;
    }
    return CompleteSnapshotRecovery.FORCE_REBUILD;
  }

  private boolean shouldHoldConflictingSnapshot(
      BoardHistoryNode syncStartNode, int[] snapshotCodes) {
    if (rebuildPolicy().shouldRebuildImmediatelyWithoutHistory(syncStartNode)) {
      return false;
    }
    return conflictTracker.evaluate(snapshotCodes) == SyncConflictTracker.Decision.HOLD;
  }

  private boolean isSyncAlreadyAtTarget(
      BoardHistoryNode syncStartNode, int[] snapshotCodes, OptionalInt foxMoveNumber) {
    return rebuildPolicy()
        .findMatchingHistoryNode(syncStartNode, snapshotCodes, foxMoveNumber)
        .isPresent();
  }

  private void rebuildFromSnapshot(
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    rebuildFromSnapshot(syncStartNode, snapshotCodes, snapshotDelta, currentPendingFoxMoveNumber());
  }

  private void rebuildFromSnapshot(
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    resetSyncTrackers();
    BoardHistoryList previousHistory = Lizzie.board.getHistory();
    RootStartSetupState preservedRootStartSetup = captureRootStartSetupState();
    BoardHistoryList rebuiltHistory =
        buildSnapshotHistory(
            previousHistory, syncStartNode, snapshotCodes, snapshotDelta, foxMoveNumber);
    if (rebuildPolicy().shouldRebuildImmediatelyWithoutHistory(syncStartNode)) {
      Lizzie.board.clear(false);
    }
    Lizzie.board.hasStartStone = false;
    Lizzie.board.startStonelist = new ArrayList<>();
    Lizzie.board.setHistory(rebuiltHistory);
    restoreRootStartSetupIfNoOrRootSnapshotAnchor(syncStartNode, preservedRootStartSetup);
    syncEngineToRebuiltSnapshot(rebuiltHistory.getCurrentHistoryNode());
    rememberResolvedSnapshotNode(rebuiltHistory.getCurrentHistoryNode());
    Lizzie.frame.renderVarTree(0, 0, false, false);
    Lizzie.frame.refresh();
    firstSync = false;
  }

  private BoardHistoryList buildSnapshotHistory(
      BoardHistoryList previousHistory,
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    return buildSnapshotHistory(
        previousHistory,
        syncStartNode,
        snapshotCodes,
        snapshotDelta,
        currentPendingFoxMoveNumber());
  }

  private BoardHistoryList buildSnapshotHistory(
      BoardHistoryList previousHistory,
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    BoardData snapshotData =
        buildSnapshotBoardData(syncStartNode, snapshotCodes, snapshotDelta, foxMoveNumber);
    BoardHistoryList rebuiltHistory = new BoardHistoryList(snapshotData);
    copySnapshotSetupMetadata(syncStartNode, rebuiltHistory.getCurrentHistoryNode());
    rebuiltHistory.setGameInfo(previousHistory.getGameInfo());
    return rebuiltHistory;
  }

  private void copySnapshotSetupMetadata(
      BoardHistoryNode syncStartNode, BoardHistoryNode rebuiltSnapshotNode) {
    BoardHistoryNode sourceSnapshotNode = findNearestSnapshotAnchor(syncStartNode);
    if (sourceSnapshotNode == null) {
      return;
    }
    BoardData rebuiltData = rebuiltSnapshotNode.getData();
    BoardData sourceData = sourceSnapshotNode.getData();
    rebuiltData.comment = sourceData.comment;
    rebuiltData.setProperties(sourceData.getProperties());
    applyExplicitSetupSnapshotSemantics(rebuiltData);
    rebuiltSnapshotNode.extraStones = cloneExtraStones(sourceSnapshotNode.extraStones);
    if (sourceSnapshotNode.hasRemovedStone()) {
      rebuiltSnapshotNode.setRemovedStone();
    }
  }

  private void applyExplicitSetupSnapshotSemantics(BoardData snapshotData) {
    String player = snapshotData.getProperty("PL");
    if (player != null && !player.isEmpty()) {
      snapshotData.blackToPlay = !"W".equalsIgnoreCase(player);
    }
    String moveNumber = snapshotData.getProperty("MN");
    if (moveNumber == null || moveNumber.isEmpty()) {
      return;
    }
    try {
      snapshotData.moveMNNumber = Integer.parseInt(moveNumber);
    } catch (NumberFormatException ex) {
      throw new IllegalStateException("Invalid MN property on rebuilt snapshot: " + moveNumber, ex);
    }
  }

  private BoardHistoryNode findNearestSnapshotAnchor(BoardHistoryNode syncStartNode) {
    BoardHistoryNode node = syncStartNode;
    while (node != null) {
      if (node.getData().isSnapshotNode()) {
        return node;
      }
      node = node.previous().orElse(null);
    }
    return null;
  }

  private ArrayList<ExtraStones> cloneExtraStones(ArrayList<ExtraStones> sourceStones) {
    if (sourceStones == null) {
      return null;
    }
    ArrayList<ExtraStones> clone = new ArrayList<>(sourceStones.size());
    for (ExtraStones sourceStone : sourceStones) {
      ExtraStones copy = new ExtraStones();
      copy.x = sourceStone.x;
      copy.y = sourceStone.y;
      copy.isBlack = sourceStone.isBlack;
      clone.add(copy);
    }
    return clone;
  }

  private RootStartSetupState captureRootStartSetupState() {
    if (!Lizzie.board.hasStartStone) {
      return RootStartSetupState.empty();
    }
    ArrayList<Movelist> startStones =
        Lizzie.board.startStonelist == null
            ? new ArrayList<>()
            : cloneStartStoneList(Lizzie.board.startStonelist);
    return new RootStartSetupState(true, startStones);
  }

  private void restoreRootStartSetupIfNoOrRootSnapshotAnchor(
      BoardHistoryNode syncStartNode, RootStartSetupState preservedRootStartSetup) {
    if (!preservedRootStartSetup.hasStartStone()) {
      return;
    }
    BoardHistoryNode snapshotAnchor = findNearestSnapshotAnchor(syncStartNode);
    if (snapshotAnchor != null && snapshotAnchor.previous().isPresent()) {
      return;
    }
    Lizzie.board.hasStartStone = true;
    Lizzie.board.startStonelist = cloneStartStoneList(preservedRootStartSetup.startStones());
  }

  private ArrayList<Movelist> cloneStartStoneList(ArrayList<Movelist> source) {
    ArrayList<Movelist> clone = new ArrayList<>(source.size());
    for (Movelist sourceMove : source) {
      Movelist copy = new Movelist();
      copy.x = sourceMove.x;
      copy.y = sourceMove.y;
      copy.isblack = sourceMove.isblack;
      copy.ispass = sourceMove.ispass;
      copy.movenum = sourceMove.movenum;
      clone.add(copy);
    }
    return clone;
  }

  private BoardData buildSnapshotBoardData(
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    return buildSnapshotBoardData(
        syncStartNode, snapshotCodes, snapshotDelta, currentPendingFoxMoveNumber());
  }

  private BoardData buildSnapshotBoardData(
      BoardHistoryNode syncStartNode,
      int[] snapshotCodes,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    Stone[] snapshotStones = buildSnapshotStones(snapshotCodes);
    SnapshotHistoryState historyState =
        inferSnapshotHistoryState(syncStartNode, snapshotStones, snapshotDelta, foxMoveNumber);
    int[] moveNumberList = buildSnapshotMoveNumberList(syncStartNode, snapshotStones, historyState);
    Zobrist zobrist = buildSnapshotZobrist(snapshotStones);
    SnapshotCaptures captures = inferSnapshotCaptures(syncStartNode, snapshotStones);
    return BoardData.snapshot(
        snapshotStones,
        historyState.lastMove,
        historyState.lastMoveColor,
        historyState.blackToPlay,
        zobrist,
        historyState.moveNumber,
        moveNumberList,
        captures.blackCaptures,
        captures.whiteCaptures,
        50,
        0);
  }

  private Stone[] buildSnapshotStones(int[] snapshotCodes) {
    Stone[] stones = new Stone[snapshotCodes.length];
    for (int x = 0; x < Board.boardWidth; x++) {
      for (int y = 0; y < Board.boardHeight; y++) {
        int snapshotIndex = y * Board.boardWidth + x;
        stones[Board.getIndex(x, y)] = snapshotStone(snapshotCodes[snapshotIndex]);
      }
    }
    return stones;
  }

  private Stone snapshotStone(int snapshotCode) {
    if (snapshotCode == 1 || snapshotCode == 3) {
      return Stone.BLACK;
    }
    if (snapshotCode == 2 || snapshotCode == 4) {
      return Stone.WHITE;
    }
    return Stone.EMPTY;
  }

  private Zobrist buildSnapshotZobrist(Stone[] stones) {
    Zobrist zobrist = new Zobrist();
    for (int x = 0; x < Board.boardWidth; x++) {
      for (int y = 0; y < Board.boardHeight; y++) {
        Stone stone = stones[Board.getIndex(x, y)];
        if (!stone.isEmpty()) {
          zobrist.toggleStone(x, y, stone);
        }
      }
    }
    return zobrist;
  }

  private int[] buildSnapshotMoveNumberList(
      BoardHistoryNode syncStartNode, Stone[] snapshotStones, SnapshotHistoryState historyState) {
    int[] moveNumberList = new int[Board.boardWidth * Board.boardHeight];
    copySnapshotMoveNumbersFromSyncStart(
        syncStartNode, snapshotStones, moveNumberList, historyState.moveNumber);
    historyState.lastMove.ifPresent(
        move -> moveNumberList[Board.getIndex(move[0], move[1])] = historyState.moveNumber);
    return moveNumberList;
  }

  private void copySnapshotMoveNumbersFromSyncStart(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      int[] moveNumberList,
      int maxMoveNumber) {
    if (syncStartNode == null) {
      return;
    }
    BoardData syncStartData = syncStartNode.getData();
    if (syncStartData.moveNumberList == null) {
      return;
    }
    for (int index = 0; index < snapshotStones.length; index++) {
      if (!sameStoneState(snapshotStones[index], syncStartData.stones[index])) {
        continue;
      }
      int moveNumber = syncStartData.moveNumberList[index];
      if (moveNumber > 0 && moveNumber <= maxMoveNumber) {
        moveNumberList[index] = moveNumber;
      }
    }
  }

  private boolean sameStoneState(Stone left, Stone right) {
    if (left.isEmpty() || right.isEmpty()) {
      return left.isEmpty() && right.isEmpty();
    }
    return (left.isBlack() && right.isBlack()) || (left.isWhite() && right.isWhite());
  }

  private SnapshotCaptures inferSnapshotCaptures(
      BoardHistoryNode syncStartNode, Stone[] snapshotStones) {
    if (syncStartNode == null) {
      return SnapshotCaptures.empty();
    }
    BoardData syncStartData = syncStartNode.getData();
    if (!sameStoneLayout(snapshotStones, syncStartData.stones)) {
      return SnapshotCaptures.empty();
    }
    return SnapshotCaptures.from(syncStartData);
  }

  private boolean sameStoneLayout(Stone[] left, Stone[] right) {
    if (left.length != right.length) {
      return false;
    }
    for (int index = 0; index < left.length; index++) {
      if (!sameStoneState(left[index], right[index])) {
        return false;
      }
    }
    return true;
  }

  private SnapshotHistoryState inferSnapshotHistoryState(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    int moveNumber =
        inferSnapshotMoveNumber(syncStartNode, snapshotStones, snapshotDelta, foxMoveNumber);
    boolean blackToPlay =
        inferSnapshotBlackToPlay(syncStartNode, snapshotStones, snapshotDelta, foxMoveNumber);
    if (snapshotDelta.hasMarker()) {
      return SnapshotHistoryState.fromMarker(
          snapshotDelta.markerX(),
          snapshotDelta.markerY(),
          snapshotDelta.markerColor(),
          blackToPlay,
          moveNumber);
    }
    return SnapshotHistoryState.markerlessSnapshot(blackToPlay, moveNumber);
  }

  private int inferSnapshotMoveNumber(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    if (foxMoveNumber.isPresent()) {
      return foxMoveNumber.getAsInt();
    }
    if (isEmptySnapshot(snapshotStones)) {
      return 0;
    }
    if (snapshotDelta.hasMarker()) {
      return inferMarkedSnapshotMoveNumber(syncStartNode, snapshotStones, snapshotDelta);
    }
    return inferMarkerlessSnapshotMoveNumber(syncStartNode, snapshotDelta);
  }

  private int inferMarkedSnapshotMoveNumber(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    if (syncStartNode != null) {
      int syncMoveNumber = syncStartNode.getData().moveNumber;
      if (snapshotDelta.changedStones() == 0) {
        return syncMoveNumber;
      }
      if (snapshotDelta.hasOnlyAdditions()) {
        return syncMoveNumber + snapshotDelta.additions();
      }
    }
    Stone markerColor = snapshotDelta.markerColor();
    int occupiedStones = countOccupiedStones(snapshotStones);
    boolean blackToPlay = markerColor == Stone.WHITE;
    if ((occupiedStones % 2 == 0) == blackToPlay) {
      return occupiedStones;
    }
    return occupiedStones + 1;
  }

  private int inferMarkerlessSnapshotMoveNumber(
      BoardHistoryNode syncStartNode, SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    return syncStartNode == null ? 0 : syncStartNode.getData().moveNumber;
  }

  private int countOccupiedStones(Stone[] snapshotStones) {
    int occupiedStones = 0;
    for (Stone stone : snapshotStones) {
      if (!stone.isEmpty()) {
        occupiedStones++;
      }
    }
    return occupiedStones;
  }

  private boolean inferSnapshotBlackToPlay(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta,
      OptionalInt foxMoveNumber) {
    if (foxMoveNumber.isPresent()) {
      return foxMoveNumber.getAsInt() % 2 == 0;
    }
    if (isEmptySnapshot(snapshotStones)) {
      return true;
    }
    if (snapshotDelta.hasMarker()) {
      return snapshotDelta.markerColor() == Stone.WHITE;
    }
    return inferBlackToPlayWithoutMarker(syncStartNode, snapshotStones, snapshotDelta);
  }

  private boolean inferBlackToPlayWithoutMarker(
      BoardHistoryNode syncStartNode,
      Stone[] snapshotStones,
      SyncSnapshotClassifier.SnapshotDelta snapshotDelta) {
    if (isEmptySnapshot(snapshotStones)) {
      return true;
    }
    return syncStartNode == null || syncStartNode.getData().blackToPlay;
  }

  private boolean isEmptySnapshot(Stone[] snapshotStones) {
    for (Stone stone : snapshotStones) {
      if (!stone.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private void syncEngineToRebuiltSnapshot(BoardHistoryNode rebuiltNode) {
    BoardData data = rebuiltNode.getData();
    Lizzie.leelaz.clear();
    if (!ExactSnapshotEngineRestore.restoreIfNeeded(Lizzie.leelaz, data)) {
      throw new IllegalStateException("Snapshot rebuild must sync through snapshot data.");
    }
  }

  private static final class SnapshotCaptures {
    private final int blackCaptures;
    private final int whiteCaptures;

    private SnapshotCaptures(int blackCaptures, int whiteCaptures) {
      this.blackCaptures = blackCaptures;
      this.whiteCaptures = whiteCaptures;
    }

    private static SnapshotCaptures empty() {
      return new SnapshotCaptures(0, 0);
    }

    private static SnapshotCaptures from(BoardData data) {
      return new SnapshotCaptures(data.blackCaptures, data.whiteCaptures);
    }
  }

  private static final class RootStartSetupState {
    private final boolean hasStartStone;
    private final ArrayList<Movelist> startStones;

    private RootStartSetupState(boolean hasStartStone, ArrayList<Movelist> startStones) {
      this.hasStartStone = hasStartStone;
      this.startStones = startStones;
    }

    private static RootStartSetupState empty() {
      return new RootStartSetupState(false, new ArrayList<>());
    }

    private boolean hasStartStone() {
      return hasStartStone;
    }

    private ArrayList<Movelist> startStones() {
      return startStones;
    }
  }

  private List<EngineSyncStone> collectEngineSyncStones(BoardData data) {
    List<EngineSyncStone> stones = new ArrayList<>();
    int knownMoves = 0;
    for (int x = 0; x < Board.boardWidth; x++) {
      for (int y = 0; y < Board.boardHeight; y++) {
        int index = Board.getIndex(x, y);
        Stone stone = data.stones[index];
        if (stone.isEmpty()) {
          continue;
        }
        int moveNumber = data.moveNumberList == null ? 0 : data.moveNumberList[index];
        stones.add(new EngineSyncStone(x, y, stone, moveNumber));
        if (moveNumber > 0) {
          knownMoves++;
        }
      }
    }
    if (knownMoves > 1) {
      stones.sort(this::compareEngineSyncStonesByMoveNumber);
    } else {
      stones.sort(this::compareEngineSyncStonesByPosition);
    }
    return stones;
  }

  private int compareEngineSyncStonesByMoveNumber(EngineSyncStone left, EngineSyncStone right) {
    boolean leftKnown = left.moveNumber > 0;
    boolean rightKnown = right.moveNumber > 0;
    if (leftKnown && rightKnown) {
      int moveNumberComparison = Integer.compare(left.moveNumber, right.moveNumber);
      if (moveNumberComparison != 0) {
        return moveNumberComparison;
      }
    }
    if (leftKnown != rightKnown) {
      return leftKnown ? -1 : 1;
    }
    return compareEngineSyncStonesByPosition(left, right);
  }

  private int compareEngineSyncStonesByPosition(EngineSyncStone left, EngineSyncStone right) {
    int yComparison = Integer.compare(left.y, right.y);
    if (yComparison != 0) {
      return yComparison;
    }
    return Integer.compare(left.x, right.x);
  }

  private Stone turnColor(boolean blackTurn) {
    return blackTurn ? Stone.BLACK : Stone.WHITE;
  }

  private void resetSyncTrackers() {
    conflictTracker.clear();
    historyJumpTracker.clear();
    lastResolvedSnapshotNode = null;
    waitingForReadBoardLocalMoveAck = false;
  }

  private void moveToAnyPositionWithoutTracking(BoardHistoryNode node) {
    runWithoutTrackingLocalHistoryNavigation(() -> Lizzie.board.moveToAnyPosition(node));
  }

  private void previousMoveWithoutTracking(boolean needRefresh) {
    runWithoutTrackingLocalHistoryNavigation(() -> Lizzie.board.previousMove(needRefresh));
  }

  private void lastMoveWithoutTracking() {
    runWithoutTrackingLocalHistoryNavigation(() -> Lizzie.frame.lastMove());
  }

  private void runWithoutTrackingLocalHistoryNavigation(Runnable navigation) {
    localNavigationTracker.beginReadBoardNavigation();
    try {
      navigation.run();
    } finally {
      localNavigationTracker.endReadBoardNavigation();
    }
  }

  private BoardHistoryNode resolveLocalNavigationTarget(BoardHistoryNode currentNode) {
    return localNavigationTracker.resolve(currentNode);
  }

  private boolean shouldResyncAfterIncrementalSync(Stone[] stones, int[] snapshotCodes) {
    Optional<int[]> currentPendingLocalMove = currentPendingLocalMoveCoordinates();
    return snapshotDiffChecker()
        .shouldResyncAfterIncrementalSync(
            Lizzie.config.alwaysSyncBoardStat,
            showInBoard,
            snapshotCodes,
            stones,
            shouldIgnoreCurrentLastLocalMove(currentPendingLocalMove),
            currentPendingLocalMove);
  }

  private void acknowledgeLocalMoveIfSnapshotCaughtUp(Stone[] stones, int[] snapshotCodes) {
    if (!Lizzie.frame.bothSync || !lastMovePlayByLizzie) {
      return;
    }
    if (!currentPendingLocalMoveCoordinates().isPresent()) {
      return;
    }
    if (!snapshotDiffChecker().hasDiff(snapshotCodes, stones, false, Optional.empty())) {
      lastMovePlayByLizzie = false;
      waitingForReadBoardLocalMoveAck = false;
    }
  }

  private void startTrackingLocalMoveFromLizzie() {
    lastMovePlayByLizzie = true;
    waitingForReadBoardLocalMoveAck = true;
  }

  private void markLocalMoveCommandCompleted() {
    waitingForReadBoardLocalMoveAck = false;
  }

  private boolean shouldIgnoreCurrentLastLocalMove() {
    return Lizzie.frame.bothSync && lastMovePlayByLizzie && waitingForReadBoardLocalMoveAck;
  }

  private boolean shouldIgnoreCurrentLastLocalMove(Optional<int[]> currentPendingLocalMove) {
    return shouldIgnoreCurrentLastLocalMove() && currentPendingLocalMove.isPresent();
  }

  private Optional<int[]> currentPendingLocalMoveCoordinates() {
    BoardData mainEndData = Lizzie.board.getHistory().getMainEnd().getData();
    if (!mainEndData.isMoveNode()) {
      return Optional.empty();
    }
    return mainEndData.lastMove;
  }

  private void restoreViewedNodeAfterSync(
      boolean played, BoardHistoryNode currentNode, BoardHistoryNode syncEndNode) {
    if (Lizzie.frame.bothSync
        || !played
        || Lizzie.config.alwaysGotoLastOnLive
        || (showInBoard
            && Lizzie.frame.floatBoard != null
            && !Lizzie.frame.floatBoard.hideSuggestion)
        || !Lizzie.board.getHistory().getCurrentHistoryNode().previous().isPresent()
        || currentNode == syncEndNode) {
      return;
    }
    moveToAnyPositionWithoutTracking(currentNode);
  }

  private void keepViewOnRecoveredMainEnd(BoardHistoryNode syncEndNode) {
    if (Lizzie.board.getHistory().getCurrentHistoryNode() == syncEndNode) {
      return;
    }
    moveToAnyPositionWithoutTracking(syncEndNode);
  }

  private boolean tryApplySingleMoveRecovery(
      BoardHistoryNode syncStartNode, Stone[] syncStartStones, int[] snapshotCodes) {
    SyncSnapshotClassifier classifier =
        new SyncSnapshotClassifier(Board.boardWidth, Board.boardHeight);
    Optional<SyncSnapshotClassifier.SingleMove> recoveredMove =
        classifier.findSingleMoveCapture(syncStartStones, snapshotCodes);
    if (!recoveredMove.isPresent()) {
      return false;
    }
    SyncSnapshotClassifier.SingleMove move = recoveredMove.get();
    if (!canApplySingleMoveRecovery(syncStartNode, move, snapshotCodes)) {
      return false;
    }
    historyJumpTracker.clear();
    moveToAnyPositionWithoutTracking(syncStartNode);
    Lizzie.board.placeForSync(move.x, move.y, move.color, false);
    if (Lizzie.config.alwaysSyncBoardStat || showInBoard) {
      lastMoveWithoutTracking();
    }
    rememberResolvedSnapshotNode(Lizzie.board.getHistory().getMainEnd());
    return true;
  }

  private void applySyncViewState(
      boolean played, BoardHistoryNode currentNode, BoardHistoryNode syncEndNode) {
    restoreViewedNodeAfterSync(played, currentNode, syncEndNode);
    if (editMode) moveToAnyPositionWithoutTracking(currentNode);
    if (played) Lizzie.frame.renderVarTree(0, 0, false, false);
  }

  private void rememberResolvedSnapshotNode(BoardHistoryNode resolvedNode) {
    lastResolvedSnapshotNode = resolvedNode;
  }

  private static final class SnapshotHistoryState {
    private final Optional<int[]> lastMove;
    private final Stone lastMoveColor;
    private final boolean blackToPlay;
    private final int moveNumber;

    private SnapshotHistoryState(
        Optional<int[]> lastMove, Stone lastMoveColor, boolean blackToPlay, int moveNumber) {
      this.lastMove = lastMove;
      this.lastMoveColor = lastMoveColor;
      this.blackToPlay = blackToPlay;
      this.moveNumber = moveNumber;
    }

    private static SnapshotHistoryState fromMarker(
        int x, int y, Stone color, boolean blackToPlay, int moveNumber) {
      return new SnapshotHistoryState(
          Optional.of(new int[] {x, y}), color, blackToPlay, moveNumber);
    }

    private static SnapshotHistoryState markerlessSnapshot(boolean blackToPlay, int moveNumber) {
      return new SnapshotHistoryState(Optional.empty(), Stone.EMPTY, blackToPlay, moveNumber);
    }
  }

  private static final class EngineSyncStone {
    private final int x;
    private final int y;
    private final Stone color;
    private final int moveNumber;

    private EngineSyncStone(int x, int y, Stone color, int moveNumber) {
      this.x = x;
      this.y = y;
      this.color = color;
      this.moveNumber = moveNumber;
    }
  }

  private boolean canApplySingleMoveRecovery(
      BoardHistoryNode syncStartNode, SyncSnapshotClassifier.SingleMove move, int[] snapshotCodes) {
    BoardData syncStartData = syncStartNode.getData();
    Stone[] stones = syncStartData.stones.clone();
    int moveIndex = Board.getIndex(move.x, move.y);
    if (!stones[moveIndex].isEmpty()) {
      return false;
    }
    Zobrist zobrist = syncStartData.zobrist.clone();
    stones[moveIndex] = move.color;
    zobrist.toggleStone(move.x, move.y, move.color);
    int isSuicidal = 0;
    if (!Lizzie.config.noCapture) {
      Board.removeDeadChain(move.x + 1, move.y, move.color.opposite(), stones, zobrist);
      Board.removeDeadChain(move.x, move.y + 1, move.color.opposite(), stones, zobrist);
      Board.removeDeadChain(move.x - 1, move.y, move.color.opposite(), stones, zobrist);
      Board.removeDeadChain(move.x, move.y - 1, move.color.opposite(), stones, zobrist);
      isSuicidal = Board.removeDeadChain(move.x, move.y, move.color, stones, zobrist);
    }
    if (violatesRecoveryKo(syncStartNode, zobrist) || violatesRecoverySuicide(isSuicidal)) {
      return false;
    }
    return !hasSnapshotDiff(stones, snapshotCodes);
  }

  private boolean violatesRecoveryKo(BoardHistoryNode syncStartNode, Zobrist zobrist) {
    return syncStartNode
        .previous()
        .map(previousNode -> previousNode != null && zobrist.equals(previousNode.getData().zobrist))
        .orElse(false);
  }

  private boolean violatesRecoverySuicide(int isSuicidal) {
    if (Lizzie.leelaz.canSuicidal) {
      return isSuicidal == 1;
    }
    return isSuicidal > 0;
  }

  private boolean hasSnapshotDiff(Stone[] stones, int[] snapshotCodes) {
    Optional<int[]> currentPendingLocalMove = currentPendingLocalMoveCoordinates();
    return snapshotDiffChecker()
        .hasDiff(
            snapshotCodes,
            stones,
            shouldIgnoreCurrentLastLocalMove(currentPendingLocalMove),
            currentPendingLocalMove);
  }

  private boolean isStoneDiff(int m, Stone[] stones, int x, int y) {
    // TODO Auto-generated method stub
    Stone stone = stones[Board.getIndex(x, y)];
    if (m == 0 && stone != Stone.EMPTY) {
      if (Lizzie.frame.bothSync && lastMovePlayByLizzie) {
        BoardHistoryNode curNode = Lizzie.board.getHistory().getMainEnd();
        if (curNode.getData().lastMove.isPresent()) {
          int[] lastCoords = curNode.getData().lastMove.get();
          if (lastCoords[0] == x && lastCoords[1] == y) {
            return false;
          }
        }
      }
      return true;
    }
    if ((m == 1 || m == 3) && !stone.isBlack()) {
      return true;
    }
    if ((m == 2 || m == 4) && !stone.isWhite()) {
      return true;
    }
    return false;
  }

  private int[] snapshotCodes() {
    int[] snapshotCodes = new int[tempcount.size()];
    for (int index = 0; index < tempcount.size(); index++) {
      snapshotCodes[index] = tempcount.get(index);
    }
    return snapshotCodes;
  }

  public void shutdown() {
    noMsg = true;
    resetSyncTrackers();
    clearPendingFoxMoveNumber();
    tempcount = new ArrayList<Integer>();
    if (Lizzie.frame != null) {
      Lizzie.frame.syncBoard = false;
      Lizzie.frame.bothSync = false;
    }
    this.sendCommand("quit");
    releaseHostedResources();
  }

  public void onLocalHistoryNavigation() {
    if (!localNavigationTracker.shouldProcessLocalNavigation()) {
      return;
    }
    historyJumpTracker.onLocalNavigation();
    if (isSyncing) {
      localNavigationTracker.remember(Lizzie.board.getHistory().getCurrentHistoryNode());
    }
  }

  public void sendCommandTo(String command) {
    // if (Lizzie.gtpConsole.isVisible() || Lizzie.config.alwaysGtp)
    // Lizzie.gtpConsole.addReadBoardCommand(command);
    BufferedOutputStream currentOutputStream = outputStream;
    if (currentOutputStream == null) {
      return;
    }
    try {
      currentOutputStream.write((command + "\n").getBytes());
      currentOutputStream.flush();
    } catch (IOException e) {
      // e.printStackTrace();
    }
  }

  public void sendCommand(String command) {
    if (command.startsWith("place")) {
      if (hideFloadBoardBeforePlace && Lizzie.frame.floatBoard != null) {
        Lizzie.frame.floatBoard.setVisible(false);
        hideFromPlace = true;
      }
      startTrackingLocalMoveFromLizzie();
      if (Lizzie.frame.isPlayingAgainstLeelaz) needGenmove = true;
    }
    if (usePipe) {
      sendCommandTo(command);
    } else if (readBoardStream != null) readBoardStream.sendCommand(command);
  }

  public void sendLossFocus() {
    // TODO Auto-generated method stub
    if (!Lizzie.config.readBoardGetFocus) return;
    sendCommand("loss");
  }

  public void checkVersion() {
    sendCommand("version");
  }

  private void releaseHostedResources() {
    InputStreamReader currentInputStream = inputStream;
    BufferedOutputStream currentOutputStream = outputStream;
    ScheduledExecutorService currentExecutor = executor;
    ReadBoardStream currentReadBoardStream = readBoardStream;
    Socket currentSocket = socket;
    ServerSocket currentServerSocket = s;
    Process currentProcess = process;

    inputStream = null;
    outputStream = null;
    executor = null;
    readBoardStream = null;
    socket = null;
    s = null;
    process = null;

    closeQuietly(currentReadBoardStream);
    detachFromFrame();
    closeQuietly(currentInputStream);
    closeQuietly(currentOutputStream);
    closeQuietly(currentSocket);
    closeQuietly(currentServerSocket);
    shutdownExecutor(currentExecutor);
    waitForHostedProcessExit(currentProcess);
  }

  private void detachFromFrame() {
    if (Lizzie.frame != null && Lizzie.frame.readBoard == this) {
      Lizzie.frame.readBoard = null;
    }
  }

  private static void shutdownExecutor(ScheduledExecutorService executor) {
    if (executor == null) {
      return;
    }
    executor.shutdownNow();
  }

  private static void waitForHostedProcessExit(Process process) {
    if (process == null) {
      return;
    }
    try {
      if (process.waitFor(PROCESS_EXIT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        return;
      }
      process.destroy();
      if (process.waitFor(PROCESS_DESTROY_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        return;
      }
      process.destroyForcibly();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private static void closeQuietly(Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException ex) {
      // Ignore close failures during shutdown.
    }
  }

  // public void sendStopInBoard() {
  //	// TODO Auto-generated method stub
  //	 sendCommand("notinboard");
  // }
}
