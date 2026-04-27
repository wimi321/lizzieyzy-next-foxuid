package featurecat.lizzie.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class YikeSgfMainlineTest {
  @Test
  void keepsSgfWithoutVariationsUnchanged() {
    String sgf = "(;GM[1]SZ[19];B[aa];W[bb])";

    assertEquals(sgf, YikeSgfMainline.withoutVariations(sgf));
  }

  @Test
  void keepsFirstVariationAsMainlineAndDropsSiblingBranches() {
    String sgf = "(;GM[1]SZ[19];B[aa](;W[bb];B[cc])(;W[dd];B[ee]);W[ff])";

    assertEquals("(;GM[1]SZ[19];B[aa];W[bb];B[cc];W[ff])", YikeSgfMainline.withoutVariations(sgf));
  }

  @Test
  void ignoresParenthesesInsidePropertyValues() {
    String sgf = "(;GM[1]C[text (not a tree) \\] ok];B[aa](;W[bb])(;W[cc]))";

    assertEquals(
        "(;GM[1]C[text (not a tree) \\] ok];B[aa];W[bb])", YikeSgfMainline.withoutVariations(sgf));
  }
}
