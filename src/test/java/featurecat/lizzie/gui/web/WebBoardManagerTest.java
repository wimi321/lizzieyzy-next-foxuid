package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class WebBoardManagerTest {

  @Test
  void getLanIp_returnsNonNullAddress() {
    String ip = WebBoardManager.getLanIp();
    assertNotNull(ip);
    assertFalse(ip.isEmpty());
    assertTrue(ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), "Expected IPv4 format but got: " + ip);
  }
}
