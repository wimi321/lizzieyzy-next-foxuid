package featurecat.lizzie.gui.web;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class WebBoardServer extends WebSocketServer {
  private final int maxConnections;
  private final AtomicReference<String> lastFullState = new AtomicReference<>();

  public WebBoardServer(InetSocketAddress address, int maxConnections) {
    super(address);
    this.maxConnections = maxConnections;
    setReuseAddr(true);
  }

  @Override
  public void onOpen(WebSocket conn, ClientHandshake handshake) {
    if (getConnections().size() > maxConnections) {
      conn.close(1013, "Max connections reached");
      return;
    }
    String state = lastFullState.get();
    if (state != null) {
      conn.send(state);
    }
  }

  @Override
  public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

  @Override
  public void onMessage(WebSocket conn, String message) {}

  @Override
  public void onError(WebSocket conn, Exception ex) {}

  @Override
  public void onStart() {}

  public void broadcastMessage(String json) {
    broadcast(json);
  }

  public void broadcastFullState(String json) {
    lastFullState.set(json);
    broadcast(json);
  }
}
