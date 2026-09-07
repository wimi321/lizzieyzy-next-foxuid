package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ExtraMode;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.EngineManager;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.analysis.MoveRankEvaluationMode;
import featurecat.lizzie.analysis.ReadBoard;
import featurecat.lizzie.analysis.ReadBoardTrackingEligibilityAdapter;
import featurecat.lizzie.analysis.TrackingAnalysisController;
import featurecat.lizzie.rules.Board;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.rules.BoardHistoryList;
import featurecat.lizzie.rules.BoardHistoryNode;
import featurecat.lizzie.rules.Stone;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrackingProductionCutoverTest {
  @ParameterizedTest
  @ValueSource(strings = {"re=9,0", "re=0", "re=,0"})
  void malformedFrameCannotPublishStableAdmission(String malformedRow) throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      if (malformedRow.equals("re=0")) {
        environment.frame.readBoard.parseLine(malformedRow);
      } else {
        assertThrows(
            IllegalArgumentException.class,
            () -> environment.frame.readBoard.parseLine(malformedRow));
      }
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(
          TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE,
          environment.frame.addTrackingPoint("B2"));
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("A1").visits());
      environment.receiveEmptyFrame();
      assertTrue(environment.frame.canStartTrackingAnalysis());
    }
  }

  @Test
  void historyReplacementDuringAcceptedFrameProcessingCannotReopenAdmission() throws Exception {
    BoardRenderer previous = LizzieFrame.boardRenderer;
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame.boardRenderer = new BoardRenderer(false);
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      ((TrackingFrame) environment.frame).nextRefresh =
          () -> Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
      environment.frame.readBoard.parseLine("re=3,0");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(Stone.EMPTY, Lizzie.board.getHistory().getData().stones[Board.getIndex(0, 0)]);
      assertFalse(environment.frame.canStartTrackingAnalysis());
    } finally {
      LizzieFrame.boardRenderer = previous;
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n"})
  void pipeTerminatedFramesRetainEvaluationAndAcceptStableRequests(String ending) throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      for (int frame = 0; frame < 2; frame++) {
        environment.frame.readBoard.parseLine("re=0,0" + ending);
        environment.frame.readBoard.parseLine("re=0,0" + ending);
        environment.frame.readBoard.parseLine("end" + ending);
        if (frame == 0) {
          assertEquals(TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
          environment.completeInitialFence(800000000);
          environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
        }
      }
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("A1").visits());
      assertEquals(TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      assertFalse(environment.commands().contains("800000002 stop"));
    }
  }

  @Test
  void identicalReadBoardFramesRetainProgressAndRejectUnstableNewRequests() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      String commands = environment.commands();

      environment.frame.readBoard.parseLine("re=0,0");
      assertEquals(
          List.of("A1"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertEquals(
          TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE,
          environment.frame.addTrackingPoint("B2"));
      environment.sendTrackingInfo("info move A1 visits 50 winrate 0.52 scoreLead 3.5 pv A1");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      environment.receiveEmptyFrame();

      TrackingAnalysisController.PointResult result =
          environment.frame.trackingDisplaySnapshot().results().get("A1");
      assertEquals(50, result.visits());
      assertEquals(52.0, result.winrate());
      assertEquals(3.5, result.scoreLead());
      assertEquals(
          List.of("A1"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertEquals(commands, environment.commands());
      assertTrue(environment.frame.canStartTrackingAnalysis());
    }
  }

  @Test
  void completedAndWaitingPointsSurvivePendingAndResumeNewestFirst() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A2"));
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      environment.frame.readBoard.parseLine("re=0,0");
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.completeFinalFence(800000002);
      String pendingCommands = environment.commands();
      assertTrue(environment.frame.trackingDisplaySnapshot().results().get("A1").completed());
      assertEquals(
          List.of("A1", "A2", "B2"),
          List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertFalse(pendingCommands.contains("800000003 stop"), pendingCommands);
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      environment.completeInitialFence(800000003);
      assertTrue(
          environment.commands().endsWith("kata-analyze 10 allow B B2 1 allow W B2 1\n"),
          environment.commands());
      environment.sendTrackingInfo("info move B2 visits 40 winrate 0.52 scoreLead 3.5 pv B2");
      String activeCommands = environment.commands();
      environment.receiveEmptyFrame();
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("B2").visits());
      assertTrue(environment.frame.trackingDisplaySnapshot().results().get("A1").completed());
      assertEquals(activeCommands, environment.commands());
      environment.sendTrackingInfo("info move B2 visits 100 winrate 0.52 scoreLead 3.5 pv B2");
      environment.completeFinalFence(800000005);
      environment.completeInitialFence(800000006);
      assertTrue(
          environment.commands().endsWith("kata-analyze 10 allow B A2 1 allow W A2 1\n"),
          environment.commands());
      environment.sendTrackingInfo("info move A2 visits 100 winrate 0.53 scoreLead 4.5 pv A2");
      environment.completeFinalFence(800000008);
      String completedCommands = environment.commands();
      for (int i = 0; i < 3; i++) environment.receiveEmptyFrame();
      assertTrue(
          environment.frame.trackingDisplaySnapshot().results().values().stream()
              .allMatch(TrackingAnalysisController.PointResult::completed));
      assertEquals(3, environment.frame.trackingDisplaySnapshot().results().size());
      assertEquals(completedCommands, environment.commands());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"endsync", "stopsync", "helper-replacement"})
  void stoppingSynchronizationRetiresCompletedAndWaitingPointsWithoutPonderHandback(
      String lifecycle) throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      environment.engine.ponder();
      environment.processCommandResponse("=");
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      environment.frame.readBoard.parseLine("re=0,0");
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.completeFinalFence(800000002);
      if (lifecycle.equals("helper-replacement")) {
        environment.frame.readBoard.shutdown();
        environment.installParsingReadBoard();
      } else {
        environment.frame.readBoard.parseLine(lifecycle);
      }
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      String stoppedCommands = environment.commands();
      environment.receiveEmptyFrame();
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertEquals(stoppedCommands, environment.commands());
      assertFalse(environment.engine.isPondering());
    }
  }

  @Test
  void admittedInitialFenceCanBecomeReadyDuringPendingReception() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.frame.readBoard.parseLine("re=0,0");
      environment.completeInitialFence(800000000);
      assertTrue(environment.commands().endsWith("kata-analyze 10 allow B A1 1 allow W A1 1\n"));
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("A1").visits());
    }
  }

  @Test
  void acquisitionLosingAdmissionPreservesCompletedResultAndNeverRetriesRejectedPoint()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.completeFinalFence(800000002);
      environment.onNextEngineWrite(() -> environment.frame.readBoard.parseLine("re=0,0"));
      assertEquals(
          TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE,
          environment.frame.addTrackingPoint("B2"));
      assertEquals(
          List.of("A1"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertTrue(environment.frame.trackingDisplaySnapshot().results().get("A1").completed());
      environment.completeInitialFence(800000003);
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertFalse(environment.commands().contains("allow B B2"));
      assertEquals(
          List.of("A1"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
    }
  }

  @Test
  void completeIdenticalFrameDuringAcquisitionDoesNotHideAdmissionTransition() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      environment.onNextEngineWrite(environment::receiveEmptyFrame);
      assertEquals(
          TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE,
          environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertFalse(environment.commands().contains("allow B A1"));
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
    }
  }

  @Test
  void waitingPointLosingAdmissionDuringAcquisitionSurvivesUntilItsLeaseCloses() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.onNextEngineWrite(() -> environment.frame.readBoard.parseLine("re=0,0"));
      environment.completeFinalFence(800000002);
      assertEquals(
          List.of("A1", "B2"),
          List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertFalse(environment.commands().contains("allow B B2"));
      environment.completeInitialFence(800000003);
      environment.completeInitialFence(800000004);
      assertTrue(
          environment.commands().endsWith("kata-analyze 10 allow B B2 1 allow W B2 1\n"),
          environment.commands());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"rules", "komi", "turn", "interval", "visits", "history"})
  void acceptedFrameRevalidatesAllProductionContextFields(String component) throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      environment.frame.readBoard.parseLine("re=0,0");
      switch (component) {
        case "rules":
          Lizzie.config.currentKataGoRules = "japanese";
          break;
        case "komi":
          Lizzie.board.getHistory().getGameInfo().setKomi(6.5);
          break;
        case "turn":
          Lizzie.board.getHistory().getData().blackToPlay = false;
          break;
        case "interval":
          Lizzie.config.analyzeUpdateIntervalCentisec = 20;
          break;
        case "visits":
          Lizzie.config.trackingAnalysisMaxVisits = 200;
          break;
        case "history":
          Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
          break;
        default:
          throw new AssertionError(component);
      }
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty(), component);
      environment.completeFinalFence(800000002);
      environment.sendTrackingInfo("info move A1 visits 80 winrate 0.53 scoreLead 4.5 pv A1");
      assertTrue(environment.frame.trackingDisplaySnapshot().results().isEmpty(), component);
      assertFalse(environment.engine.isPondering());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"clear", "start 2 2", "incomplete"})
  void samplingBoundariesKeepAcceptedResultButDoNotOpenAdmission(String boundary) throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      String commands = environment.commands();
      if (boundary.equals("incomplete")) {
        environment.frame.readBoard.parseLine("re=0,0");
        environment.frame.readBoard.parseLine("end");
      } else {
        environment.frame.readBoard.parseLine(boundary);
      }
      assertFalse(environment.frame.canStartTrackingAnalysis());
      environment.frame.onMainEnginePonder();
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("A1").visits());
      environment.receiveEmptyFrame();
      assertTrue(environment.frame.canStartTrackingAnalysis());
      assertEquals(commands, environment.commands());
    }
  }

  @Test
  void realRemoteMoveClearsTrackingAndDoesNotAcceptOldProgress() throws Exception {
    BoardRenderer previous = LizzieFrame.boardRenderer;
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame.boardRenderer = new BoardRenderer(false);
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      BoardHistoryNode old = Lizzie.board.getHistory().getCurrentHistoryNode();
      environment.frame.readBoard.parseLine("re=3,0");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertFalse(old == Lizzie.board.getHistory().getCurrentHistoryNode());
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      environment.sendTrackingInfo("info move A1 visits 80 winrate 0.53 scoreLead 4.5 pv A1");
      assertTrue(environment.frame.trackingDisplaySnapshot().results().isEmpty());
      assertTrue(Lizzie.board.getHistory().getData().bestMoves.isEmpty());
    } finally {
      LizzieFrame.boardRenderer = previous;
    }
  }

  @Test
  void markerOnlyChangeRetainsAcceptedStonePositionAndStream() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.board.getHistory().place(0, 0, Stone.BLACK, true);
      environment.installParsingReadBoard();
      environment.frame.readBoard.parseLine("re=3,0");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      String commands = environment.commands();
      environment.frame.readBoard.parseLine("re=1,0");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("A1").visits());
      assertEquals(commands, environment.commands());
    }
  }

  @Test
  void unchangedRecoveryNavigatingToExistingNodeInvalidatesOldDisplayContext() throws Exception {
    BoardRenderer previous = LizzieFrame.boardRenderer;
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame.boardRenderer = new BoardRenderer(false);
      Lizzie.board.getHistory().place(0, 0, Stone.BLACK, true);
      BoardHistoryNode earlier = Lizzie.board.getHistory().getCurrentHistoryNode();
      Lizzie.board.getHistory().place(1, 0, Stone.WHITE, true);
      environment.installParsingReadBoard();
      environment.frame.readBoard.parseLine("syncPlatform fox");
      environment.frame.readBoard.parseLine("roomToken tracking-test");
      environment.frame.readBoard.parseLine("liveTitleMove 2");
      environment.frame.readBoard.parseLine("foxMoveNumber 2");
      environment.frame.readBoard.parseLine("re=1,4");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 40 winrate 0.51 scoreLead 2.5 pv A1");
      environment.frame.readBoard.parseLine("syncPlatform fox");
      environment.frame.readBoard.parseLine("roomToken tracking-test");
      environment.frame.readBoard.parseLine("liveTitleMove 1");
      environment.frame.readBoard.parseLine("foxMoveNumber 1");
      environment.frame.readBoard.parseLine("re=3,0");
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertSame(earlier, Lizzie.board.getHistory().getCurrentHistoryNode());
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
    } finally {
      LizzieFrame.boardRenderer = previous;
    }
  }

  @Test
  void delayedRetirementNotificationCannotClearReplacementContext() throws Exception {
    java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.completeFinalFence(800000002);
      javax.swing.SwingUtilities.invokeLater(
          () -> {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
      synchronized (environment.frame.readBoard) {
        environment.frame.readBoard.parseLine("endsync");
      }
      environment.receiveEmptyFrame();
      environment.frame.onMainEnginePonder();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      environment.completeInitialFence(800000003);
      environment.sendTrackingInfo("info move B2 visits 40 winrate 0.52 scoreLead 3.5 pv B2");
      release.countDown();
      javax.swing.SwingUtilities.invokeAndWait(() -> {});
      assertEquals(
          List.of("B2"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertEquals(40, environment.frame.trackingDisplaySnapshot().results().get("B2").visits());
    } finally {
      release.countDown();
      javax.swing.SwingUtilities.invokeAndWait(() -> {});
    }
  }

  @Test
  void acquisitionValidationDoesNotWaitForOuterReadBoardOwnerLock() throws Exception {
    java.util.concurrent.CountDownLatch writing = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch proceed = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      environment.onNextEngineWrite(
          () -> {
            writing.countDown();
            try {
              proceed.await();
            } catch (InterruptedException failure) {
              Thread.currentThread().interrupt();
            }
          });
      java.util.concurrent.Future<TrackingAnalysisController.AddResult> result =
          executor.submit(() -> environment.frame.addTrackingPoint("A1"));
      assertTrue(writing.await(3, java.util.concurrent.TimeUnit.SECONDS));
      synchronized (environment.frame.readBoard) {
        environment.frame.readBoard.parseLine("endsync");
        proceed.countDown();
        assertEquals(
            TrackingAnalysisController.AddResult.LEASE_UNAVAILABLE,
            result.get(3, java.util.concurrent.TimeUnit.SECONDS));
      }
      javax.swing.SwingUtilities.invokeAndWait(() -> {});
      assertTrue(environment.frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
    } finally {
      proceed.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS));
    }
  }

  @Test
  void incomingFramesDoNotRenewProgressTimeoutOrLetOldTimerReleaseNewWork() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      List<Runnable> timeouts = environment.installManualTimeouts();
      environment.installParsingReadBoard();
      environment.receiveEmptyFrame();
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 2.5 pv A1");
      environment.completeFinalFence(800000002);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("B2"));
      environment.completeInitialFence(800000003);
      Runnable beforeProgress = timeouts.get(timeouts.size() - 1);
      environment.sendTrackingInfo("info move B2 visits 40 winrate 0.52 scoreLead 3.5 pv B2");
      Runnable progressTimeout = timeouts.get(timeouts.size() - 1);
      environment.receiveEmptyFrame();
      environment.receiveEmptyFrame();
      environment.frame.readBoard.parseLine("re=0,0");
      String commands = environment.commands();
      beforeProgress.run();
      assertEquals(commands, environment.commands());
      progressTimeout.run();
      assertTrue(environment.commands().endsWith("800000005 stop\n"), environment.commands());
      environment.completeFinalFence(800000005);
      assertEquals(
          List.of("A1"), List.copyOf(environment.frame.trackingDisplaySnapshot().selectedPoints()));
      assertTrue(environment.frame.trackingDisplaySnapshot().results().get("A1").completed());
      environment.frame.readBoard.parseLine("re=0,0");
      environment.frame.readBoard.parseLine("end");
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A2"));
      environment.completeInitialFence(800000006);
      commands = environment.commands();
      progressTimeout.run();
      assertEquals(commands, environment.commands());
    }
  }

  @Test
  void localAndStableReadBoardEntriesUseTheSameCurrentEngineController() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      assertTrue(environment.commands().contains("800000000 stop\n"));
      TrackingAnalysisController controller = frame.trackingAnalysisController();
      assertSame(controller, frame.trackingAnalysisController());

      controller.clear();
      environment.completeInitialFence(800000000);
      environment.installStableReadBoard();

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("B2"));
      assertSame(controller, frame.trackingAnalysisController());
      assertTrue(
          controller.snapshot().context().readBoardContext().isPresent(),
          "stable ReadBoard entry must bind its accepted frame identity to the same controller");
      assertTrue(environment.commands().contains("800000001 stop\n"));
    }
  }

  @Test
  void productionEntryRemovesPendingAndClearsCurrentImmediately() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;

      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("B2"));

      assertTrue(frame.removeTrackingPoint("B2"));
      assertEquals(List.of("A1"), List.copyOf(frame.trackingDisplaySnapshot().selectedPoints()));

      frame.clearTrackingPoints();

      assertTrue(frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertTrue(environment.commands().contains("800000002 stop\n"));
    }
  }

  @Test
  void removingCurrentTrackingPointResumesPriorNormalAnalysis() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;
      environment.engine.ponder();
      environment.processCommandResponse("=");
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);

      assertTrue(frame.removeTrackingPoint("A1"));
      environment.completeFinalFence(800000002);

      assertTrue(environment.engine.isPondering());
      assertTrue(environment.commands().endsWith("kata-analyze B 10\n"), environment.commands());
      assertTrue(environment.engine.isResponseUpToDate());
      environment.sendOrdinaryInfo(
          "info move B2 visits 40 winrate 0.51 scoreLead 2.5 prior 0.2 pv B2");
      assertEquals(1, environment.engine.getBestMoves().size());
    }
  }

  @Test
  void clearingTrackingPointsResumesPriorNormalAnalysis() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;
      environment.engine.ponder();
      environment.processCommandResponse("=");
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);

      frame.clearTrackingPoints();
      environment.completeFinalFence(800000002);

      assertTrue(environment.engine.isPondering());
      assertTrue(environment.commands().endsWith("kata-analyze B 10\n"), environment.commands());
      assertTrue(environment.engine.isResponseUpToDate());
      environment.sendOrdinaryInfo(
          "info move B2 visits 40 winrate 0.51 scoreLead 2.5 prior 0.2 pv B2");
      assertEquals(1, environment.engine.getBestMoves().size());
    }
  }

  @Test
  void playingMoveDuringTrackingResumesNormalAnalysisThroughProductionController()
      throws Exception {
    assertPlayingMoveDuringTrackingAcceptsNormalAnalysisAfterResponse("=", true);
  }

  @Test
  void failedPlayDuringTrackingDoesNotAcceptNormalAnalysisForTheUnplayedPosition()
      throws Exception {
    assertPlayingMoveDuringTrackingAcceptsNormalAnalysisAfterResponse("? illegal move", false);
  }

  private void assertPlayingMoveDuringTrackingAcceptsNormalAnalysisAfterResponse(
      String playResponse, boolean expectNormalAnalysis) throws Exception {
    BoardRenderer previousBoardRenderer = LizzieFrame.boardRenderer;
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame.boardRenderer = new BoardRenderer(false);
      Lizzie.config.analyzeBlack = true;
      Lizzie.config.analyzeWhite = true;
      environment.engine.ponder();
      environment.processCommandResponse("=");
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED,
          environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);

      Lizzie.board.place(0, 0, Stone.BLACK);
      environment.completeFinalFence(800000002);
      environment.processCommandResponse(playResponse);
      assertEquals(expectNormalAnalysis, environment.engine.isResponseUpToDate());
      environment.sendOrdinaryInfo(
          "info move B2 visits 40 winrate 0.51 scoreLead 2.5 prior 0.2 pv B2");

      String commands = environment.commands();
      assertTrue(environment.engine.isPondering());
      assertTrue(commands.lastIndexOf("kata-analyze") > commands.lastIndexOf("play B A1"), commands);
      assertEquals(expectNormalAnalysis ? 1 : 0, environment.engine.getBestMoves().size());
    } finally {
      LizzieFrame.boardRenderer = previousBoardRenderer;
    }
  }

  @Test
  void rendererGateSuppressesStaleNodeAndPonderInvalidationDoesNotReacquire() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;
      environment.engine.Pondering();
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      TrackingAnalysisController.DisplaySnapshot original = frame.trackingDisplaySnapshot();
      assertTrue(frame.isTrackingDisplayCurrent(original));

      Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));

      assertFalse(frame.isTrackingDisplayCurrent(original));
      frame.onMainEnginePonder();

      assertTrue(frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertTrue(environment.commands().contains("800000002 stop\n"));
      assertFalse(environment.commands().contains("800000003 stop\n"));
      environment.completeFinalFence(800000002);
      assertFalse(environment.engine.isPondering());
    }
  }

  @Test
  void internalTrackingInvalidationDoesNotRestorePriorNormalAnalysis() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      LizzieFrame frame = environment.frame;
      environment.engine.ponder();
      environment.processCommandResponse("=");
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);

      frame.invalidateTrackingAnalysis();
      environment.completeFinalFence(800000002);

      assertFalse(environment.engine.isPondering());
      assertTrue(frame.trackingDisplaySnapshot().selectedPoints().isEmpty());
      assertFalse(environment.commands().endsWith("kata-analyze B 10\n"));
    }
  }

  @Test
  void trackingDisplayChangesRequestRefreshWithoutOrdinaryParserRepaint() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      TrackingFrame frame = (TrackingFrame) environment.frame;
      assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      int beforeInfo = frame.analysisRefreshRequests;

      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.51 scoreLead 1.5 pv A1");

      assertTrue(frame.analysisRefreshRequests > beforeInfo);
      int beforeCompletion = frame.analysisRefreshRequests;
      environment.completeFinalFence(800000002);
      assertTrue(frame.analysisRefreshRequests > beforeCompletion);
    }
  }

  @Test
  void trackingOverlayIsIndependentOfCandidateVisibilityAndSuppressedForBranchOrStaleNode()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      TrackingFrame frame = (TrackingFrame) environment.frame;
      Font previousWinrateFont = LizzieFrame.winrateFont;
      Font previousPlayoutsFont = LizzieFrame.playoutsFont;
      LizzieFrame.winrateFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      LizzieFrame.playoutsFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
      try {
        assertEquals(TrackingAnalysisController.AddResult.ADDED, frame.addTrackingPoint("A1"));
        environment.completeInitialFence(800000000);
        environment.sendTrackingInfo("info move A1 visits 10 winrate 0.51 scoreLead 1.5 pv A1");
        Lizzie.config.showBestMoves = false;
        frame.isShowingHeatmap = true;
        frame.isShowingPolicy = true;
        BoardRenderer renderer = configuredRenderer();

        assertTrue(hasVisiblePaint(renderTrackingOverlay(renderer)));

        setField(renderer, BoardRenderer.class, "isShowingBranch", true);
        assertFalse(hasVisiblePaint(renderTrackingOverlay(renderer)));
        setField(renderer, BoardRenderer.class, "isShowingBranch", false);
        Lizzie.board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
        assertFalse(hasVisiblePaint(renderTrackingOverlay(renderer)));
      } finally {
        LizzieFrame.winrateFont = previousWinrateFont;
        LizzieFrame.playoutsFont = previousPlayoutsFont;
      }
    }
  }

  @Test
  void trackingResultUsesFixedInteriorAndLiveQualityOutline() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.trackingPointInteriorColor = new Color(10, 20, 30);
      Lizzie.config.trackingPointInteriorOpacityPercent = 50;
      Lizzie.config.showTrackingPointOutline = true;
      Lizzie.config.trackingPointOutlineOpacityPercent = 100;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      environment.installOrdinaryBestMove("B2", 1000, 60.0, 5.0);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 10 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage rendered = renderTrackingOverlay(configuredRenderer());

      assertTrue(
          countArgb(rendered, new Color(10, 20, 30, 128)) > 200,
          "the tracking result disc should use the configured fixed interior");
      assertTrue(
          countArgb(rendered, new Color(200, 140, 50)) > 20,
          "a 10-point winrate and 3-point score loss should color the dashed outline");
    }
  }

  @Test
  void disablingTrackingOutlineKeepsTheResultInteriorWithoutQualityColor() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.trackingPointInteriorColor = new Color(10, 20, 30);
      Lizzie.config.trackingPointInteriorOpacityPercent = 100;
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.trackingPointOutlineOpacityPercent = 100;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      environment.installOrdinaryBestMove("B2", 1000, 60.0, 5.0);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 10 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage rendered = renderTrackingOverlay(configuredRenderer());

      assertTrue(countArgb(rendered, new Color(10, 20, 30)) > 200);
      assertEquals(0, countArgb(rendered, new Color(200, 140, 50)));
    }
  }

  @Test
  void trackingResultUsesNeutralGrayUntilAnOrdinaryBaselineExists() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = true;
      Lizzie.config.trackingPointOutlineOpacityPercent = 100;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 10 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage rendered = renderTrackingOverlay(configuredRenderer());

      assertTrue(
          countArgb(rendered, new Color(112, 118, 124)) > 20,
          "a tracking result without an ordinary best candidate should keep a neutral outline");
    }
  }

  @Test
  void trackingResultRecolorsWhenTheOrdinaryBaselineChanges() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = true;
      Lizzie.config.trackingPointOutlineOpacityPercent = 100;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      environment.installOrdinaryBestMove("B2", 1000, 60.0, 5.0);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 10 winrate 0.50 scoreLead 2.0 pv A1");
      assertTrue(
          countArgb(renderTrackingOverlay(configuredRenderer()), new Color(200, 140, 50)) > 20);

      environment.installOrdinaryBestMove("B2", 1000, 50.0, 2.0);

      assertTrue(
          countArgb(renderTrackingOverlay(configuredRenderer()), new Color(0, 180, 0)) > 20,
          "the existing tracking outline should be recolored from the current ordinary baseline");
    }
  }

  @Test
  void trackingResultOverridesOrdinaryCandidateAtTheSameCoordinate() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.trackingPointInteriorColor = new Color(33, 44, 55);
      Lizzie.config.trackingPointInteriorOpacityPercent = 100;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      environment.installOrdinaryBestMove("A1", 1000, 60.0, 5.0);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 10 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage rendered = renderMainBoard();

      assertTrue(
          countOpaqueRgb(rendered, new Color(33, 44, 55)) > 200,
          "the fixed tracking interior should replace the ordinary candidate at that coordinate");
    }
  }

  @Test
  void selectedTrackingPointSuppressesTheOrdinaryCandidateWhenOutlineIsDisabled()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      Lizzie.config.showBlueRing = true;
      environment.installOrdinaryBestMove("A1", 1000, 60.0, 5.0);

      assertTrue(countOpaqueRgb(renderMainBoard(), Color.BLUE) > 10);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));

      assertEquals(
          0,
          countOpaqueRgb(renderMainBoard(), Color.BLUE),
          "the selected tracking point should own the coordinate before its first result arrives");
    }
  }

  @Test
  void selectedTrackingPointUsesTheNeutralPendingOutline() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = true;
      Lizzie.config.trackingPointOutlineOpacityPercent = 100;
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));

      BufferedImage rendered = renderMainBoard();

      assertTrue(
          countOpaqueRgb(rendered, new Color(112, 118, 124)) > 20,
          "a selected point without a result should use the neutral dashed outline");
    }
  }

  @Test
  void transparentDarkInteriorUsesBlackTextAutomaticallyAndAllowsManualOverride() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.trackingPointInteriorColor = Color.BLACK;
      Lizzie.config.trackingPointInteriorOpacityPercent = 10;
      Lizzie.config.showWinrateInSuggestion = true;
      Lizzie.config.showPlayoutsInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      Lizzie.config.trackingPointTextAutoColor = false;
      Lizzie.config.trackingPointTextColor = Color.WHITE;
      environment.installOrdinaryBestMove("B2", 1000, 60.0, 5.0);
      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 100 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage manualWhite = renderMainBoard();
      Lizzie.config.trackingPointTextAutoColor = true;
      BufferedImage automatic = renderMainBoard();

      assertTrue(
          opaqueRgbMaskDifferences(manualWhite, automatic, Color.BLACK, 51, 129, 24).size() > 5,
          "a mostly transparent dark fill over the light board should automatically use black text");
    }
  }

  @Test
  void trackingResultReusesOrdinaryCandidateTextLayout() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.useDefaultInfoRowOrder = false;
      Lizzie.config.suggestionInfoPlayouts = 1;
      Lizzie.config.suggestionInfoScoreLead = 2;
      Lizzie.config.suggestionInfoWinrate = 3;
      Lizzie.config.showSuggestionOrder = false;
      Lizzie.config.showScoreAsDiff = true;
      environment.installOrdinaryMoves("B2", 1000000, 60.0, 5.0, "A1", 12345, 50.0, 2.0);

      BufferedImage ordinaryCandidate = renderMainBoard();

      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 12345 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage trackingResult = renderMainBoard();

      List<String> differences =
          opaqueRgbMaskDifferences(ordinaryCandidate, trackingResult, Color.BLACK, 51, 129, 30);
      assertTrue(
          differences.size() <= 2,
          "tracking text should reuse ordinary row order, positions, and score-difference baseline; "
              + "only antialiasing edge pixels may differ over the translucent interior: "
              + differences);
    }
  }

  @Test
  void trackingResultUsesTheOrdinaryCandidateInformationVisibility() throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      Lizzie.config.showTrackingPointOutline = false;
      Lizzie.config.showWinrateInSuggestion = false;
      Lizzie.config.showScoremeanInSuggestion = false;
      Lizzie.config.showSuggestionOrder = false;
      environment.installOrdinaryMoves("B2", 1000000, 60.0, 5.0, "A1", 12345, 50.0, 2.0);

      BufferedImage ordinaryCandidate = renderMainBoard();

      assertEquals(
          TrackingAnalysisController.AddResult.ADDED, environment.frame.addTrackingPoint("A1"));
      environment.completeInitialFence(800000000);
      environment.sendTrackingInfo("info move A1 visits 12345 winrate 0.50 scoreLead 2.0 pv A1");

      BufferedImage trackingResult = renderMainBoard();
      List<String> differences =
          opaqueRgbMaskDifferences(ordinaryCandidate, trackingResult, Color.BLACK, 51, 129, 30);

      assertTrue(
          differences.size() <= 2,
          "tracking text should honor the ordinary candidate information switches; only "
              + "antialiasing edge pixels may differ over the translucent interior: "
              + differences);
    }
  }

  @Test
  void unsupportedEngineModesAndMissingCapabilitiesAreHiddenBeforeLeaseAcquisition()
      throws Exception {
    try (TestEnvironment environment = TestEnvironment.open()) {
      environment.engine.useJavaSSH = true;
      assertFalse(environment.frame.canStartTrackingAnalysis());

      environment.engine.useJavaSSH = false;
      Lizzie.config.extraMode = ExtraMode.Double_Engine;
      assertFalse(environment.frame.canStartTrackingAnalysis());

      Lizzie.config.extraMode = ExtraMode.Normal;
      environment.engine.commandLists.remove("kata-analyze");
      assertFalse(environment.frame.canStartTrackingAnalysis());

      environment.engine.commandLists.add("kata-analyze");
      setField(environment.engine, Leelaz.class, "outputStream", null);
      assertFalse(environment.frame.canStartTrackingAnalysis());
    }
  }

  private static BoardRenderer configuredRenderer() throws Exception {
    BoardRenderer renderer = new BoardRenderer(false);
    setField(renderer, BoardRenderer.class, "x", 0);
    setField(renderer, BoardRenderer.class, "y", 0);
    setField(renderer, BoardRenderer.class, "scaledMarginWidth", 20);
    setField(renderer, BoardRenderer.class, "scaledMarginHeight", 20);
    setField(renderer, BoardRenderer.class, "squareWidth", 40);
    setField(renderer, BoardRenderer.class, "squareHeight", 40);
    setField(renderer, BoardRenderer.class, "stoneRadius", 16);
    setField(
        renderer,
        BoardRenderer.class,
        "bestMoves",
        new java.util.ArrayList<>(
            Lizzie.board.getHistory().getCurrentHistoryNode().getData().bestMoves));
    return renderer;
  }

  private static BufferedImage renderTrackingOverlay(BoardRenderer renderer) throws Exception {
    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      Method method =
          BoardRenderer.class.getDeclaredMethod("drawTrackingOverlay", Graphics2D.class);
      method.setAccessible(true);
      method.invoke(renderer, graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static boolean hasVisiblePaint(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static BufferedImage renderMainBoard() {
    Font previousUiFont = LizzieFrame.uiFont;
    Font previousWinrateFont = LizzieFrame.winrateFont;
    Font previousPlayoutsFont = LizzieFrame.playoutsFont;
    LizzieFrame.uiFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    LizzieFrame.winrateFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    LizzieFrame.playoutsFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    try {
      BoardRenderer renderer = new BoardRenderer(false);
      renderer.setLocation(0, 0);
      renderer.setBoardLength(180, 180);
      BufferedImage image = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = image.createGraphics();
      try {
        renderer.draw(graphics);
      } finally {
        graphics.dispose();
      }
      return image;
    } finally {
      LizzieFrame.uiFont = previousUiFont;
      LizzieFrame.winrateFont = previousWinrateFont;
      LizzieFrame.playoutsFont = previousPlayoutsFont;
    }
  }

  private static int countOpaqueRgb(BufferedImage image, Color color) {
    int expected = color.getRGB() & 0x00FFFFFF;
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getRGB(x, y);
        if ((pixel >>> 24) == 0xFF && (pixel & 0x00FFFFFF) == expected) {
          count++;
        }
      }
    }
    return count;
  }

  private static int countArgb(BufferedImage image, Color color) {
    int expected = color.getRGB();
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) == expected) count++;
      }
    }
    return count;
  }

  private static int countOpaqueRgbNear(BufferedImage image, Color color, int tolerance) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        Color pixel = new Color(image.getRGB(x, y), true);
        if (pixel.getAlpha() == 0xFF
            && Math.abs(pixel.getRed() - color.getRed()) <= tolerance
            && Math.abs(pixel.getGreen() - color.getGreen()) <= tolerance
            && Math.abs(pixel.getBlue() - color.getBlue()) <= tolerance) {
          count++;
        }
      }
    }
    return count;
  }

  private static List<String> opaqueRgbMaskDifferences(
      BufferedImage first,
      BufferedImage second,
      Color color,
      int centerX,
      int centerY,
      int radius) {
    int expected = color.getRGB() & 0x00FFFFFF;
    java.util.ArrayList<String> differences = new java.util.ArrayList<>();
    for (int y = centerY - radius; y <= centerY + radius; y++) {
      for (int x = centerX - radius; x <= centerX + radius; x++) {
        int firstPixel = first.getRGB(x, y);
        int secondPixel = second.getRGB(x, y);
        boolean firstMatches = (firstPixel >>> 24) == 0xFF && (firstPixel & 0x00FFFFFF) == expected;
        boolean secondMatches =
            (secondPixel >>> 24) == 0xFF && (secondPixel & 0x00FFFFFF) == expected;
        if (firstMatches != secondMatches) differences.add(x + "," + y);
      }
    }
    return differences;
  }

  private static void closeExclusiveSessionForTest(Leelaz engine) throws Exception {
    Field field = Leelaz.class.getDeclaredField("exclusiveGtpSession");
    field.setAccessible(true);
    Object session = field.get(engine);
    if (session == null) {
      return;
    }
    Method cancelInitial =
        Leelaz.class.getDeclaredMethod("cancelExclusiveGtpInitialStopTimeout", session.getClass());
    cancelInitial.setAccessible(true);
    cancelInitial.invoke(engine, session);
    Method cancelRelease =
        Leelaz.class.getDeclaredMethod("cancelExclusiveGtpReleaseStopTimeout", session.getClass());
    cancelRelease.setAccessible(true);
    cancelRelease.invoke(engine, session);
    Method close = Leelaz.class.getDeclaredMethod("closeExclusiveGtpSession", session.getClass());
    close.setAccessible(true);
    close.invoke(engine, session);
  }

  private static final class TestEnvironment implements AutoCloseable {
    private final Leelaz previousEngine;
    private final Board previousBoard;
    private final Config previousConfig;
    private final LizzieFrame previousFrame;
    private final Menu previousMenu;
    private final BottomToolbar previousToolbar;
    private final boolean previousEmpty;
    private final int previousWidth;
    private final int previousHeight;
    private final Leelaz engine;
    private final ByteArrayOutputStream output;
    private final LizzieFrame frame;

    private TestEnvironment(
        Leelaz previousEngine,
        Board previousBoard,
        Config previousConfig,
        LizzieFrame previousFrame,
        Menu previousMenu,
        BottomToolbar previousToolbar,
        boolean previousEmpty,
        int previousWidth,
        int previousHeight,
        Leelaz engine,
        ByteArrayOutputStream output,
        LizzieFrame frame) {
      this.previousEngine = previousEngine;
      this.previousBoard = previousBoard;
      this.previousConfig = previousConfig;
      this.previousFrame = previousFrame;
      this.previousMenu = previousMenu;
      this.previousToolbar = previousToolbar;
      this.previousEmpty = previousEmpty;
      this.previousWidth = previousWidth;
      this.previousHeight = previousHeight;
      this.engine = engine;
      this.output = output;
      this.frame = frame;
    }

    static TestEnvironment open() throws Exception {
      Leelaz previousEngine = Lizzie.leelaz;
      Board previousBoard = Lizzie.board;
      Config previousConfig = Lizzie.config;
      LizzieFrame previousFrame = Lizzie.frame;
      Menu previousMenu = LizzieFrame.menu;
      BottomToolbar previousToolbar = LizzieFrame.toolbar;
      boolean previousEmpty = EngineManager.isEmpty;
      int previousWidth = Board.boardWidth;
      int previousHeight = Board.boardHeight;

      Board.boardWidth = 2;
      Board.boardHeight = 2;
      Board board = allocate(TrackingBoard.class);
      board.setHistory(new BoardHistoryList(BoardData.empty(2, 2)));
      Config config = allocate(Config.class);
      config.analyzeUpdateIntervalCentisec = 10;
      config.trackingAnalysisMaxVisits = 100;
      config.showTrackingPointOutline = true;
      config.trackingPointInteriorColor = new Color(255, 156, 156);
      config.trackingPointInteriorOpacityPercent = 100;
      config.trackingPointOutlineOpacityPercent = 92;
      config.trackingPointTextAutoColor = true;
      config.trackingPointTextColor = Color.BLACK;
      config.currentKataGoRules = "chinese";
      config.extraMode = ExtraMode.Normal;
      config.boardStyle = Config.BOARD_STYLE_JAPANESE;
      config.usePureBoard = true;
      config.pureBoardColor = new Color(198, 178, 148);
      config.usePureStone = true;
      config.showBestMoves = true;
      config.showBlackCandidates = true;
      config.showWhiteCandidates = true;
      config.showWinrateInSuggestion = true;
      config.showPlayoutsInSuggestion = true;
      config.showScoremeanInSuggestion = true;
      config.suggestionInfoWinrate = 1;
      config.suggestionInfoPlayouts = 2;
      config.suggestionInfoScoreLead = 3;
      config.useDefaultInfoRowOrder = true;
      config.moveRankEvaluationMode = MoveRankEvaluationMode.AUTO;
      config.winLossThreshold1 = -1;
      config.winLossThreshold2 = -3;
      config.winLossThreshold3 = -6;
      config.winLossThreshold4 = -12;
      config.winLossThreshold5 = -24;
      config.scoreLossThreshold1 = -0.5;
      config.scoreLossThreshold2 = -1.5;
      config.scoreLossThreshold3 = -3;
      config.scoreLossThreshold4 = -6;
      config.scoreLossThreshold5 = -12;
      config.moveRankMarkLastMove = -1;
      Leelaz engine = new Leelaz("");
      engine.isLoaded = true;
      engine.started = true;
      engine.isKatago = true;
      engine.commandLists.addAll(List.of("stop", "kata-analyze"));
      setField(engine, Leelaz.class, "endGetCommandList", true);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      setField(engine, Leelaz.class, "outputStream", new BufferedOutputStream(output));
      LizzieFrame frame = allocate(TrackingFrame.class);
      frame.priorityMoveCoords = new java.util.ArrayList<>();
      frame.clickbadmove = LizzieFrame.outOfBoundCoordinate;
      frame.mouseOverCoordinate = LizzieFrame.outOfBoundCoordinate;

      EngineManager.isEmpty = false;
      Lizzie.board = board;
      Lizzie.config = config;
      Lizzie.leelaz = engine;
      Lizzie.frame = frame;
      LizzieFrame.menu = allocate(SilentMenu.class);
      LizzieFrame.toolbar = allocate(BottomToolbar.class);
      return new TestEnvironment(
          previousEngine,
          previousBoard,
          previousConfig,
          previousFrame,
          previousMenu,
          previousToolbar,
          previousEmpty,
          previousWidth,
          previousHeight,
          engine,
          output,
          frame);
    }

    List<Runnable> installManualTimeouts() throws Exception {
      List<Runnable> callbacks = new java.util.ArrayList<>();
      Class<?> schedulerType =
          Class.forName(TrackingAnalysisController.class.getName() + "$TimeoutScheduler");
      Class<?> cancellableType =
          Class.forName(TrackingAnalysisController.class.getName() + "$Cancellable");
      Object scheduler =
          java.lang.reflect.Proxy.newProxyInstance(
              schedulerType.getClassLoader(),
              new Class<?>[] {schedulerType},
              (proxy, method, arguments) -> {
                callbacks.add((Runnable) arguments[1]);
                return java.lang.reflect.Proxy.newProxyInstance(
                    cancellableType.getClassLoader(),
                    new Class<?>[] {cancellableType},
                    (ignored, cancel, unused) -> null);
              });
      java.lang.reflect.Constructor<TrackingAnalysisController> constructor =
          TrackingAnalysisController.class.getDeclaredConstructor(schedulerType);
      constructor.setAccessible(true);
      setField(
          frame,
          LizzieFrame.class,
          "trackingAnalysisController",
          constructor.newInstance(scheduler));
      return callbacks;
    }

    void onNextEngineWrite(Runnable action) throws Exception {
      setField(
          engine,
          Leelaz.class,
          "outputStream",
          new BufferedOutputStream(
              new java.io.FilterOutputStream(output) {
                private Runnable pending = action;

                @Override
                public void write(byte[] bytes, int offset, int length) throws java.io.IOException {
                  out.write(bytes, offset, length);
                  Runnable callback = pending;
                  pending = null;
                  if (callback != null) callback.run();
                }
              }));
    }

    void installParsingReadBoard() throws Exception {
      ReadBoard readBoard = allocate(ReadBoard.class);
      for (String name :
          List.of("conflictTracker", "historyJumpTracker", "localNavigationTracker")) {
        Field field = ReadBoard.class.getDeclaredField(name);
        field.setAccessible(true);
        java.lang.reflect.Constructor<?> constructor = field.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        field.set(readBoard, constructor.newInstance());
      }
      setField(readBoard, ReadBoard.class, "tempcount", new java.util.ArrayList<Integer>());
      frame.readBoard = readBoard;
    }

    void receiveEmptyFrame() {
      frame.readBoard.parseLine("re=0,0");
      frame.readBoard.parseLine("re=0,0");
      frame.readBoard.parseLine("end");
    }

    void installStableReadBoard() throws Exception {
      ReadBoard readBoard = allocate(ReadBoard.class);
      BoardHistoryNode node = Lizzie.board.getHistory().getCurrentHistoryNode();
      setField(readBoard, ReadBoard.class, "trackingEligibilityIdentity", new Object());
      setField(readBoard, ReadBoard.class, "trackingEligibilityRevision", 7L);
      setField(readBoard, ReadBoard.class, "trackingEligibilityNode", node);
      setField(
          readBoard,
          ReadBoard.class,
          "trackingEligibilityBoardRevision",
          Lizzie.board.getContextRevision());
      setField(
          readBoard,
          ReadBoard.class,
          "trackingEligibilityReason",
          ReadBoardTrackingEligibilityAdapter.Reason.STABLE);
      frame.readBoard = readBoard;
    }

    void installOrdinaryBestMove(String coordinate, int visits, double winrate, double scoreLead) {
      Lizzie.board.getHistory().getCurrentHistoryNode().getData().bestMoves =
          List.of(ordinaryMove(coordinate, visits, winrate, scoreLead, 0));
    }

    void installOrdinaryMoves(
        String bestCoordinate,
        int bestVisits,
        double bestWinrate,
        double bestScoreLead,
        String otherCoordinate,
        int otherVisits,
        double otherWinrate,
        double otherScoreLead) {
      Lizzie.board.getHistory().getCurrentHistoryNode().getData().bestMoves =
          List.of(
              ordinaryMove(bestCoordinate, bestVisits, bestWinrate, bestScoreLead, 0),
              ordinaryMove(otherCoordinate, otherVisits, otherWinrate, otherScoreLead, 1));
    }

    private static MoveData ordinaryMove(
        String coordinate, int visits, double winrate, double scoreLead, int order) {
      MoveData move = new MoveData();
      move.coordinate = coordinate;
      move.playouts = visits;
      move.winrate = winrate;
      move.scoreMean = scoreLead;
      move.order = order;
      move.isKataData = true;
      return move;
    }

    void completeInitialFence(int id) throws Exception {
      dispatch("=" + id);
      processCommandResponse("=" + id);
      dispatch("");
    }

    void completeFinalFence(int id) throws Exception {
      dispatch("");
      dispatch("=" + id);
      dispatch("");
    }

    void sendTrackingInfo(String line) throws Exception {
      dispatch(line);
    }

    private void dispatch(String line) throws Exception {
      java.lang.reflect.Method method =
          Leelaz.class.getDeclaredMethod("dispatchExclusiveGtpLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    private void processCommandResponse(String line) throws Exception {
      java.lang.reflect.Method method =
          Leelaz.class.getDeclaredMethod("processCommandResponseLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    private void sendOrdinaryInfo(String line) throws Exception {
      java.lang.reflect.Method method = Leelaz.class.getDeclaredMethod("parseLine", String.class);
      method.setAccessible(true);
      method.invoke(engine, line);
    }

    String commands() {
      return output.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws Exception {
      closeExclusiveSessionForTest(engine);
      Lizzie.leelaz = previousEngine;
      Lizzie.board = previousBoard;
      Lizzie.config = previousConfig;
      Lizzie.frame = previousFrame;
      LizzieFrame.menu = previousMenu;
      LizzieFrame.toolbar = previousToolbar;
      EngineManager.isEmpty = previousEmpty;
      Board.boardWidth = previousWidth;
      Board.boardHeight = previousHeight;
    }
  }

  private static final class TrackingFrame extends LizzieFrame {
    private int analysisRefreshRequests;
    private Runnable nextRefresh;

    @Override
    public void refresh() {
      Runnable action = nextRefresh;
      nextRefresh = null;
      if (action != null) action.run();
    }

    @Override
    public void requestAnalysisRefresh() {
      analysisRefreshRequests++;
    }

    @Override
    public void clearSelectImage() {}
  }

  private static final class TrackingBoard extends Board {
    @Override
    public void clearAfterMove() {}
  }

  private static final class SilentMenu extends Menu {
    @Override
    public void toggleEngineMenuStatus(boolean isPondering, boolean isThinking) {}
  }

  private static void setField(Object target, Class<?> owner, String name, Object value)
      throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
  }
}
