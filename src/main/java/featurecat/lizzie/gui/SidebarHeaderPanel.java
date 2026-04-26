package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;

public class SidebarHeaderPanel extends JPanel {
  private final SidebarPanel parentPanel;
  private ProblemListSnapshot currentSnapshot;

  private static final Color TEXT_NORMAL = new Color(255, 255, 255, 128);
  private static final Color TEXT_SELECTED = new Color(255, 255, 255, 255);
  private static final Color BG_TRACK = new Color(255, 255, 255, 15);
  private static final Color BG_THUMB = new Color(255, 255, 255, 40);
  private static final Color PILL_BG = new Color(255, 255, 255, 20);
  private static final int NO_SEGMENT = -1;
  private static final int FIRST_SEGMENT = 0;
  private static final int SECOND_SEGMENT = 1;
  private static final int CONTROL_X = 10;
  private static final int APPLE_PRIMARY_Y = 14;
  private static final int APPLE_PRIMARY_WIDTH = 132;
  private static final int APPLE_PRIMARY_HEIGHT = 30;
  private static final int APPLE_SIDE_Y = 52;
  private static final int APPLE_SIDE_WIDTH = 112;
  private static final int APPLE_SIDE_HEIGHT = 24;
  private static final int CLASSIC_PRIMARY_Y = 12;
  private static final int CLASSIC_SIDE_Y = 44;
  private static final int CLASSIC_ROW_HEIGHT = 32;
  private static final int CLASSIC_PRIMARY_BASELINE = 25;
  private static final int CLASSIC_SIDE_BASELINE = 55;
  private static final int CLASSIC_PRIMARY_LEGACY_WIDTH = 132;
  private static final int CLASSIC_SIDE_LEGACY_WIDTH = 112;
  private static final int CLASSIC_SEPARATOR_OFFSET = 30;
  private static final int CLASSIC_SECOND_LABEL_OFFSET = 45;
  private static final int CLASSIC_HIT_PADDING_X = 8;

  public SidebarHeaderPanel(SidebarPanel parentPanel) {
    this.parentPanel = parentPanel;
    setOpaque(false);
    setPreferredSize(new Dimension(200, 88));

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            Point point = e.getPoint();
            FontMetrics metrics = getFontMetrics(headerFont());

            int primarySegment = primarySegmentIndexAt(point, Lizzie.config.isAppleStyle, metrics);
            if (primarySegment == FIRST_SEGMENT) {
              parentPanel.switchTo("COMMENTS");
              return;
            }
            if (primarySegment == SECOND_SEGMENT) {
              parentPanel.switchTo("BLUNDERS");
              return;
            }

            if (!Lizzie.config.isShowingBlunderTabel) {
              return;
            }

            int sideSegment = sideSegmentIndexAt(point, Lizzie.config.isAppleStyle, metrics);
            if (sideSegment == FIRST_SEGMENT) {
              Lizzie.frame.setProblemListSideFilter(ProblemListSideFilter.BLACK);
            } else if (sideSegment == SECOND_SEGMENT) {
              Lizzie.frame.setProblemListSideFilter(ProblemListSideFilter.WHITE);
            }
          }
        });
  }

  public void updateSnapshot(ProblemListSnapshot snapshot) {
    this.currentSnapshot = snapshot;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    boolean showBlunders = Lizzie.config.isShowingBlunderTabel;
    ProblemListSideFilter sideFilter = Lizzie.frame.getProblemListSideFilter();
    if (sideFilter == ProblemListSideFilter.ALL) sideFilter = ProblemListSideFilter.BLACK;

    g2.setFont(headerFont());
    FontMetrics fm = g2.getFontMetrics();

    if (!Lizzie.config.isAppleStyle) {
      g2.setColor(new Color(36, 38, 42));
      g2.fillRect(0, 0, getWidth(), getHeight());

      int x = CONTROL_X;
      int y = CLASSIC_PRIMARY_BASELINE;
      g2.setColor(showBlunders ? TEXT_NORMAL : TEXT_SELECTED);
      g2.drawString("评论", x, y);
      g2.setColor(TEXT_NORMAL);
      g2.drawString("｜", x + CLASSIC_SEPARATOR_OFFSET, y);
      g2.setColor(showBlunders ? TEXT_SELECTED : TEXT_NORMAL);
      g2.drawString("问题手", x + CLASSIC_SECOND_LABEL_OFFSET, y);

      if (currentSnapshot != null) {
        String pillText = currentSnapshot.analysisRunning ? "..." : "";
        pillText += currentSnapshot.analyzedMoves + "/" + currentSnapshot.totalMoves;
        g2.setColor(TEXT_NORMAL);
        g2.drawString(pillText, getWidth() - fm.stringWidth(pillText) - 10, y);
      }

      if (showBlunders) {
        y = CLASSIC_SIDE_BASELINE;
        g2.setColor(sideFilter == ProblemListSideFilter.BLACK ? TEXT_SELECTED : TEXT_NORMAL);
        g2.drawString("黑棋", x, y);
        g2.setColor(TEXT_NORMAL);
        g2.drawString("｜", x + CLASSIC_SEPARATOR_OFFSET, y);
        g2.setColor(sideFilter == ProblemListSideFilter.WHITE ? TEXT_SELECTED : TEXT_NORMAL);
        g2.drawString("白棋", x + CLASSIC_SECOND_LABEL_OFFSET, y);
      }
      g2.dispose();
      return;
    }

    Color accent = glassAccentColor();
    Color topWash = new Color(255, 255, 255, 18);
    g2.setPaint(
        new LinearGradientPaint(
            0,
            0,
            0,
            getHeight(),
            new float[] {0.0f, 0.55f, 1.0f},
            new Color[] {topWash, new Color(255, 255, 255, 8), new Color(255, 255, 255, 0)}));
    g2.fillRoundRect(0, 0, getWidth(), getHeight() + 12, 18, 18);

    g2.setColor(new Color(255, 255, 255, 36));
    g2.drawLine(4, getHeight() - 1, getWidth() - 8, getHeight() - 1);

    // 1. [ 评论 | 问题手 ] (Apple Style Segmented Control)
    int x = CONTROL_X;
    int y = APPLE_PRIMARY_Y;
    int segW = APPLE_PRIMARY_WIDTH;
    int segH = APPLE_PRIMARY_HEIGHT;
    int arc = 15;
    int baseline = y + segH / 2 + fm.getAscent() / 2 - 1;

    g2.setColor(new Color(255, 255, 255, 24));
    g2.fillRoundRect(x, y, segW, segH, arc, arc);
    g2.setColor(new Color(255, 255, 255, 18));
    g2.drawRoundRect(x, y, segW - 1, segH - 1, arc, arc);

    int halfW = segW / 2;
    g2.setColor(showBlunders ? withAlpha(accent, 132) : new Color(255, 255, 255, 58));
    if (!showBlunders) {
      g2.fillRoundRect(x + 2, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
    } else {
      g2.fillRoundRect(x + halfW, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
    }

    g2.setColor(!showBlunders ? TEXT_SELECTED : TEXT_NORMAL);
    String t1 = "评论";
    g2.drawString(t1, x + (halfW - fm.stringWidth(t1)) / 2, baseline);

    g2.setColor(showBlunders ? TEXT_SELECTED : TEXT_NORMAL);
    String t2 = "问题手";
    g2.drawString(t2, x + halfW + (halfW - fm.stringWidth(t2)) / 2, baseline);

    // 2. Progress pill
    if (currentSnapshot != null) {
      String pillText = currentSnapshot.analysisRunning ? "⏳ " : "☄️ ";
      pillText += currentSnapshot.analyzedMoves + "/" + currentSnapshot.totalMoves;

      int textWidth = fm.stringWidth(pillText);
      int pillX = getWidth() - textWidth - 24;
      int pillY = y;
      int pillWidth = textWidth + 16;
      int pillHeight = 28;

      g2.setColor(
          currentSnapshot.analysisRunning ? new Color(255, 184, 77, 48) : withAlpha(accent, 54));
      g2.fillRoundRect(pillX, pillY, pillWidth, pillHeight, arc, arc);
      g2.setColor(
          currentSnapshot.analysisRunning ? new Color(255, 213, 153, 72) : withAlpha(accent, 92));
      g2.drawRoundRect(pillX, pillY, pillWidth - 1, pillHeight - 1, arc, arc);

      g2.setColor(TEXT_SELECTED);
      g2.drawString(pillText, pillX + 8, baseline + 1);
    }

    // 3. [ 黑 | 白 ]
    if (showBlunders) {
      y = APPLE_SIDE_Y;
      segW = APPLE_SIDE_WIDTH;
      segH = APPLE_SIDE_HEIGHT;
      halfW = segW / 2;
      arc = 12;
      baseline = y + segH / 2 + fm.getAscent() / 2 - 1;

      g2.setColor(new Color(255, 255, 255, 20));
      g2.fillRoundRect(x, y, segW, segH, arc, arc);
      g2.setColor(new Color(255, 255, 255, 14));
      g2.drawRoundRect(x, y, segW - 1, segH - 1, arc, arc);

      g2.setColor(withAlpha(accent, 118));
      if (sideFilter == ProblemListSideFilter.BLACK) {
        g2.fillRoundRect(x + 2, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
      } else {
        g2.fillRoundRect(x + halfW, y + 2, halfW - 2, segH - 4, arc - 2, arc - 2);
      }

      g2.setColor(sideFilter == ProblemListSideFilter.BLACK ? TEXT_SELECTED : TEXT_NORMAL);
      String b1 = "⚫ 黑棋";
      g2.drawString(b1, x + (halfW - fm.stringWidth(b1)) / 2, baseline);

      g2.setColor(sideFilter == ProblemListSideFilter.WHITE ? TEXT_SELECTED : TEXT_NORMAL);
      String b2 = "⚪ 白棋";
      g2.drawString(b2, x + halfW + (halfW - fm.stringWidth(b2)) / 2, baseline);
    }

    g2.dispose();
  }

  static int primarySegmentIndexAt(Point point, boolean appleStyle, FontMetrics metrics) {
    if (appleStyle) {
      return segmentedIndexAt(
          point,
          new Rectangle(CONTROL_X, APPLE_PRIMARY_Y, APPLE_PRIMARY_WIDTH, APPLE_PRIMARY_HEIGHT));
    }
    return classicTextIndexAt(point, metrics, CLASSIC_PRIMARY_Y, "评论", "问题手");
  }

  static int sideSegmentIndexAt(Point point, boolean appleStyle, FontMetrics metrics) {
    if (appleStyle) {
      return segmentedIndexAt(
          point, new Rectangle(CONTROL_X, APPLE_SIDE_Y, APPLE_SIDE_WIDTH, APPLE_SIDE_HEIGHT));
    }
    return classicTextIndexAt(point, metrics, CLASSIC_SIDE_Y, "黑棋", "白棋");
  }

  private static int classicTextIndexAt(
      Point point, FontMetrics metrics, int rowY, String firstText, String secondText) {
    if (classicTextBounds(metrics, firstText, CONTROL_X, rowY).contains(point)) {
      return FIRST_SEGMENT;
    }
    if (classicSecondTextBounds(metrics, secondText, rowY).contains(point)) {
      return SECOND_SEGMENT;
    }
    return NO_SEGMENT;
  }

  private static Rectangle classicTextBounds(
      FontMetrics metrics, String text, int textX, int rowY) {
    int textWidth = Math.max(1, metrics.stringWidth(text));
    return new Rectangle(
        textX - CLASSIC_HIT_PADDING_X,
        rowY,
        textWidth + CLASSIC_HIT_PADDING_X * 2,
        CLASSIC_ROW_HEIGHT);
  }

  private static Rectangle classicSecondTextBounds(FontMetrics metrics, String text, int rowY) {
    int textX = CONTROL_X + CLASSIC_SECOND_LABEL_OFFSET;
    Rectangle textBounds = classicTextBounds(metrics, text, textX, rowY);
    int legacyRight =
        CONTROL_X
            + (rowY == CLASSIC_SIDE_Y ? CLASSIC_SIDE_LEGACY_WIDTH : CLASSIC_PRIMARY_LEGACY_WIDTH);
    int right = Math.max(textBounds.x + textBounds.width, legacyRight);
    textBounds.width = right - textBounds.x;
    return textBounds;
  }

  private static int segmentedIndexAt(Point point, Rectangle bounds) {
    if (!bounds.contains(point)) {
      return NO_SEGMENT;
    }
    return point.x < bounds.x + bounds.width / 2 ? FIRST_SEGMENT : SECOND_SEGMENT;
  }

  private Font headerFont() {
    String fontName =
        Lizzie.config != null && Lizzie.config.uiFontName != null
            ? Lizzie.config.uiFontName
            : getFont().getName();
    return new Font(fontName, Font.BOLD, 12);
  }

  private Color glassAccentColor() {
    return Lizzie.config != null && Lizzie.config.theme != null
        ? Lizzie.config.theme.glassAccentColor()
        : new Color(96, 165, 250);
  }

  private Color withAlpha(Color color, int alpha) {
    return new Color(
        color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
  }
}
