package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class SidebarHeaderPanelHitTest {

  @Test
  void classicProblemTabUsesTheWholeVisibleLabel() {
    FontMetrics metrics = headerMetrics();

    assertEquals(0, SidebarHeaderPanel.primarySegmentIndexAt(new Point(18, 25), false, metrics));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(58, 25), false, metrics),
        "clicking the first character of 问题手 should switch to the problem list.");
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(72, 25), false, metrics),
        "the old midpoint split cut through 问题手 and sent this click to 评论.");
    assertEquals(1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(90, 25), false, metrics));
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(58, 39), false, metrics),
        "the whole classic tab row should be clickable, not only the glyph pixels.");
    assertEquals(
        1,
        SidebarHeaderPanel.primarySegmentIndexAt(new Point(130, 25), false, metrics),
        "keep the forgiving right side of the classic tab hit area.");
  }

  @Test
  void classicSeparatorGapDoesNotSwitchTabs() {
    FontMetrics metrics = headerMetrics();

    assertEquals(-1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(45, 25), false, metrics));
  }

  @Test
  void appleSegmentedControlStillUsesFullHalves() {
    FontMetrics metrics = headerMetrics();

    assertEquals(0, SidebarHeaderPanel.primarySegmentIndexAt(new Point(20, 24), true, metrics));
    assertEquals(1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(96, 24), true, metrics));
    assertEquals(-1, SidebarHeaderPanel.primarySegmentIndexAt(new Point(150, 24), true, metrics));
  }

  @Test
  void sideFilterHitTestingMatchesVisibleLabels() {
    FontMetrics metrics = headerMetrics();

    assertEquals(0, SidebarHeaderPanel.sideSegmentIndexAt(new Point(18, 55), false, metrics));
    assertEquals(1, SidebarHeaderPanel.sideSegmentIndexAt(new Point(58, 55), false, metrics));
    assertEquals(1, SidebarHeaderPanel.sideSegmentIndexAt(new Point(110, 55), false, metrics));
    assertEquals(0, SidebarHeaderPanel.sideSegmentIndexAt(new Point(20, 62), true, metrics));
    assertEquals(1, SidebarHeaderPanel.sideSegmentIndexAt(new Point(88, 62), true, metrics));
  }

  private static FontMetrics headerMetrics() {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setFont(new Font("Dialog", Font.BOLD, 12));
      return graphics.getFontMetrics();
    } finally {
      graphics.dispose();
    }
  }
}
