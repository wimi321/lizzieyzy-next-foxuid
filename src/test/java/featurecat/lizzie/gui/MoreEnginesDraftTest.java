package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Config;
import featurecat.lizzie.ConfigTestHelper;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.EngineManager;
import featurecat.lizzie.util.Utils;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoreEnginesDraftTest {
  @TempDir Path directory;

  private Config previousConfig;
  private ResourceBundle previousBundle;
  private EngineManager previousManager;
  private JTable previousTable;
  private boolean previousNeedUpdate;
  private MoreEngines editor;

  @BeforeEach
  void setUp() throws Exception {
    previousConfig = Lizzie.config;
    previousBundle = Lizzie.resourceBundle;
    previousManager = Lizzie.engineManager;
    previousTable = MoreEngines.table;
    previousNeedUpdate = MoreEngines.needUpdateEngine;
    Files.createDirectories(directory.resolve("save"));
    Lizzie.config = ConfigTestHelper.createBootstrapped(directory);
    Lizzie.resourceBundle = ResourceBundle.getBundle("l10n.DisplayStrings", Locale.ENGLISH);
    Lizzie.engineManager = null;
    Utils.saveEngineSettings(new ArrayList<>());
    SwingUtilities.invokeAndWait(() -> editor = new MoreEngines());
  }

  @AfterEach
  void tearDown() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          if (editor != null && editor.threadPolicyRefreshTimer != null) {
            editor.threadPolicyRefreshTimer.stop();
          }
          Lizzie.config = previousConfig;
          Lizzie.resourceBundle = previousBundle;
          Lizzie.engineManager = previousManager;
          MoreEngines.table = previousTable;
          MoreEngines.needUpdateEngine = previousNeedUpdate;
        });
  }

  @Test
  void navigatingUntouchedEmptyRowsDoesNotPromptOrCreateAnEngine() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(0);
          clickRow(1);
          clickRow(0);
          assertTrue(Utils.getEngineData().isEmpty());
          editor.add.doClick();
          ArrayList<EngineData> entries = Utils.getEngineData();
          assertEquals(1, entries.size());
          assertEquals(
              Lizzie.resourceBundle.getString("ChooseMoreEngine.newEngine"), entries.get(0).name);
        });
  }

  @Test
  void savingUntouchedEmptyRowDoesNotCreateAnEngine() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(3);
          editor.save.doClick();
          assertTrue(Utils.getEngineData().isEmpty());
        });
  }

  @Test
  void savingEditedEmptyRowCreatesAnEntryThatCanBeReloaded() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(2);
          editor.txtName.setText("Local engine");
          editor.command.setText("leelaz --gtp");
          editor.txtInitialCommand.setText("boardsize 13");
          editor.txtWidth.setText("13");
          editor.save.doClick();
          assertEquals(1, Utils.getEngineData().size());
          clickRow(4);
          clickRow(0);
          assertEquals("Local engine", editor.txtName.getText());
          assertEquals("leelaz --gtp", editor.command.getText());
          assertEquals("boardsize 13", editor.txtInitialCommand.getText());
          assertEquals("13", editor.txtWidth.getText());
        });
  }

  @Test
  void emptyRowsAndEmptySavedEntriesDoNotShowBenchmarkWarnings() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(0);
          assertFalse(editor.threadPolicyStatus.isVisible());
          assertTrue(editor.threadPolicyCfg.isVisible());
          assertTrue(editor.threadPolicyCfg.isSelected());
          assertFalse(editor.threadPolicyCfg.isEnabled());
          assertTrue(editor.threadPolicyBenchmark.isVisible());
          assertFalse(editor.threadPolicyBenchmark.isEnabled());
          assertFalse(editor.benchmarkSelectedEngine.isEnabled());
          editor.add.doClick();
          assertFalse(editor.threadPolicyStatus.isVisible());
          assertNull(editor.threadPolicyStatus.getToolTipText());
        });
  }

  @Test
  void leavingBenchmarkedEntryClearsItsStatusTooltip() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(0);
          editor.txtName.setText("KataGo A");
          editor.command.setText("katago gtp");
          editor.save.doClick();
          ArrayList<EngineData> entries = Utils.getEngineData();
          entries.get(0).threadPolicy.put("katago-benchmark-threads", 16);
          Utils.saveEngineSettings(entries);
          clickRow(0);
          assertNotNull(editor.threadPolicyStatus.getToolTipText());
          clickRow(3);
          assertNull(editor.threadPolicyStatus.getToolTipText());
          assertFalse(editor.threadPolicyStatus.isVisible());
        });
  }

  @Test
  void unknownDraftLocksCfgWithoutChangingSavedThreadPreference() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          clickRow(0);
          editor.txtName.setText("KataGo A");
          editor.command.setText("katago gtp");
          editor.save.doClick();
          ArrayList<EngineData> entries = Utils.getEngineData();
          entries.get(0).threadPolicy.put("katago-benchmark-threads", 16).put("source", "BENCHMARK");
          Utils.saveEngineSettings(entries);
          editor.cancel.doClick();
          clickRow(0);
          assertTrue(editor.threadPolicyBenchmark.isSelected());
          editor.command.setText("unknown://target");
          assertTrue(editor.threadPolicyCfg.isVisible());
          assertTrue(editor.threadPolicyCfg.isSelected());
          assertFalse(editor.threadPolicyCfg.isEnabled());
          assertFalse(editor.threadPolicyBenchmark.isEnabled());
          assertFalse(editor.benchmarkSelectedEngine.isEnabled());
          assertFalse(editor.threadPolicyStatus.isVisible());
          assertNull(editor.threadPolicyStatus.getToolTipText());
          editor.command.setText("katago gtp");
          assertTrue(editor.threadPolicyBenchmark.isSelected());
          assertTrue(editor.threadPolicyBenchmark.isEnabled());
          clickRow(3);
          assertTrue(editor.threadPolicyCfg.isSelected());
          assertFalse(editor.threadPolicyBenchmark.isSelected());
        });
  }

  private void clickRow(int row) {
    JTable table = MoreEngines.table;
    table.dispatchEvent(
        new MouseEvent(
            table,
            MouseEvent.MOUSE_CLICKED,
            0,
            0,
            5,
            row * table.getRowHeight() + 5,
            1,
            false,
            MouseEvent.BUTTON1));
  }
}
