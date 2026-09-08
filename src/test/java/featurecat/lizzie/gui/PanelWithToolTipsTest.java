package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PanelWithToolTipsTest {
  @Test
  void deferredLabelLayoutPreservesItsNewContainer() throws Exception {
    PanelWithToolTips original = new PanelWithToolTips();
    JPanel destination = new JPanel();
    JLabel label = new JLabel("IP");
    SwingUtilities.invokeAndWait(
        () -> {
          original.add(label);
          destination.add(label);
        });
    SwingUtilities.invokeAndWait(() -> assertSame(destination, label.getParent()));
  }
}
