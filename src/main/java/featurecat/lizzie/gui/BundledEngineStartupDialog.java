package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

public class BundledEngineStartupDialog extends JDialog {
  private final JLabel lblStatus = new JFontLabel();
  private final JLabel lblHint = new JFontLabel();
  private final JProgressBar progressBar = new JProgressBar();

  public BundledEngineStartupDialog(boolean nvidiaPackage) {
    setModal(false);
    setResizable(false);
    setAlwaysOnTop(Lizzie.frame != null && Lizzie.frame.isAlwaysOnTop());
    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    setTitle(text("BundledEngineStartup.title", "Loading KataGo..."));

    JPanel content = new JPanel(new BorderLayout(0, 10));
    content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
    setContentPane(content);

    lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
    lblStatus.setText(
        text("BundledEngineStartup.status.checking", "Checking built-in engine files..."));
    content.add(lblStatus, BorderLayout.NORTH);

    progressBar.setMinimum(0);
    progressBar.setMaximum(1000);
    progressBar.setValue(120);
    progressBar.setStringPainted(true);
    progressBar.setString("1 / 4");
    content.add(progressBar, BorderLayout.CENTER);

    JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    lblHint.setHorizontalAlignment(SwingConstants.CENTER);
    lblHint.setText(
        text(
            nvidiaPackage ? "BundledEngineStartup.hint.nvidia" : "BundledEngineStartup.hint",
            nvidiaPackage
                ? "First launch on the NVIDIA package may take a little longer."
                : "First launch may take a little longer."));
    hintPanel.add(lblHint);
    content.add(hintPanel, BorderLayout.SOUTH);

    setMinimumSize(new Dimension(500, 150));
    pack();
    if (Lizzie.frame != null) {
      setLocationRelativeTo(Lizzie.frame);
    }
  }

  public void updateStage(int step, int totalSteps, String status, String hint) {
    int safeTotal = Math.max(1, totalSteps);
    int safeStep = Math.max(1, Math.min(step, safeTotal));
    lblStatus.setText(status);
    if (hint != null && !hint.trim().isEmpty()) {
      lblHint.setText(hint);
    }
    progressBar.setValue((safeStep * 1000) / safeTotal);
    progressBar.setString(safeStep + " / " + safeTotal);
  }

  public void closeDialog() {
    setVisible(false);
    dispose();
  }

  private static String text(String key, String fallback) {
    try {
      if (Lizzie.resourceBundle != null && Lizzie.resourceBundle.containsKey(key)) {
        return Lizzie.resourceBundle.getString(key);
      }
    } catch (Exception e) {
    }
    return fallback;
  }
}
