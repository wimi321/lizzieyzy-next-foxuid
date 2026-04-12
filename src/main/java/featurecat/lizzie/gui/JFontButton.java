package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.theme.MorandiPalette;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.border.LineBorder;

public class JFontButton extends JButton {
  private static final Color DEFAULT_BG = MorandiPalette.TOOLBAR_BUTTON_BG;
  private static final Color HOVER_BG = MorandiPalette.TOOLBAR_BUTTON_HOVER;
  private static final Color PRESSED_BG = MorandiPalette.TOOLBAR_BUTTON_PRESSED;
  private Color normalBackground = DEFAULT_BG;
  private boolean internalBackgroundChange = false;

  public JFontButton() {
    super();
    initStyle();
  }

  public JFontButton(String text) {
    super();
    this.setText(text);
    initStyle();
  }

  public JFontButton(ImageIcon icon) {
    super();
    this.setIcon(icon);
    initStyle();
  }

  private void initStyle() {
    this.setFont(new Font(Config.sysDefaultFontName, Font.PLAIN, Config.frameFontSize));
    this.setOpaque(true);
    this.setContentAreaFilled(true);
    this.setBackground(DEFAULT_BG);
    this.setForeground(MorandiPalette.TOOLBAR_TEXT);
    this.setBorder(new LineBorder(MorandiPalette.TOOLBAR_BUTTON_BORDER, 1));
    this.setMargin(new Insets(0, 0, 0, 0));
    this.setFocusPainted(false);
    this.setBorderPainted(true);

    this.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            if (isEnabled()) {
              setInteractionBackground(HOVER_BG);
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            setInteractionBackground(normalBackground);
          }

          @Override
          public void mousePressed(MouseEvent e) {
            if (isEnabled()) {
              setInteractionBackground(PRESSED_BG);
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (getModel().isRollover()) {
              setInteractionBackground(HOVER_BG);
            } else {
              setInteractionBackground(normalBackground);
            }
          }
        });
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    if (!internalBackgroundChange && bg != null) {
      normalBackground = bg;
    }
  }

  private void setInteractionBackground(Color bg) {
    internalBackgroundChange = true;
    try {
      super.setBackground(bg);
    } finally {
      internalBackgroundChange = false;
    }
  }
}
