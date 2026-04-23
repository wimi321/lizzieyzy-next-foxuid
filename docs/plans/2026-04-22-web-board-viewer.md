# Web Board Viewer Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a LAN web viewer that lets browsers on the local network watch the LizzieYzy analysis board in real-time via WebSocket + Canvas.

**Architecture:** Java side embeds a WebSocket server (using existing Java-WebSocket 1.6.0 dependency) and a minimal HTTP file server. A data collector hooks into `Leelaz.parseInfoKatago()` results and `LizzieFrame.refresh()` calls, serializes board state + analysis to JSON, and broadcasts to all connected browsers. The browser renders a Go board with candidate moves, variations, territory heatmap, and winrate chart using HTML5 Canvas.

**Tech Stack:** Java 17 (WebSocket server via `org.java_websocket`), org.json for serialization, vanilla HTML/JS/CSS with Canvas for the frontend.

**Spec:** `docs/superpowers/specs/2026-04-22-web-board-viewer-design.md`

---

## File Structure

### New Java files (in `src/main/java/featurecat/lizzie/gui/web/`)

| File | Responsibility |
|---|---|
| `WebBoardServer.java` | Extends `WebSocketServer`. Manages connections (max 20), broadcasts JSON messages, sends `full_state` to new connections. |
| `WebBoardHttpServer.java` | Minimal HTTP server using `ServerSocket`. Serves static files from classpath `/web/` prefix. Path traversal protection. |
| `WebBoardDataCollector.java` | Single-threaded executor. Collects board state + analysis from `Lizzie.board` and `Leelaz`, serializes to JSON, hands to `WebBoardServer` for broadcast. Throttles to 10 updates/sec. |
| `WebBoardManager.java` | Lifecycle orchestrator. Start/stop the three components in correct order. Resolves LAN IP. Manages menu state. |

### New frontend files (in `src/main/resources/web/`)

| File | Responsibility |
|---|---|
| `index.html` | Page structure, responsive layout (left-right desktop / top-bottom mobile) |
| `board.js` | WebSocket client, Canvas board rendering, candidate moves, variation hover, heatmap, winrate chart |
| `board.css` | Styling, responsive breakpoints, connection overlay |

### Modified files

| File | Change |
|---|---|
| `src/main/java/featurecat/lizzie/gui/Menu.java` | Add "Web 旁观" submenu under the "同步" (live/sync) menu |
| `src/main/java/featurecat/lizzie/gui/LizzieFrame.java` | Add `WebBoardManager` field; hook `refresh()` to notify data collector |
| `src/main/java/featurecat/lizzie/Lizzie.java` | Add `public static WebBoardManager webBoardManager` field |
| `src/main/resources/l10n/DisplayStrings*.properties` (7 files) | Add i18n keys for menu items |

### New test files

| File | Tests |
|---|---|
| `src/test/java/featurecat/lizzie/gui/web/WebBoardDataCollectorTest.java` | JSON serialization of board state, analysis data, winrate history; throttling behavior |
| `src/test/java/featurecat/lizzie/gui/web/WebBoardHttpServerTest.java` | Static file serving, path traversal rejection, GET-only |
| `src/test/java/featurecat/lizzie/gui/web/WebBoardServerTest.java` | Connection limit, broadcast to multiple clients, full_state on connect |

---

## Task 1: WebBoardDataCollector — JSON Serialization

The data collector is the core logic that reads board state and produces JSON. No server dependency needed — pure data transformation that can be tested in isolation.

**Files:**
- Create: `src/main/java/featurecat/lizzie/gui/web/WebBoardDataCollector.java`
- Test: `src/test/java/featurecat/lizzie/gui/web/WebBoardDataCollectorTest.java`

**Context needed:**
- `BoardData` fields: `stones` (Stone[]), `bestMoves` (List\<MoveData\>), `winrate`, `scoreMean`, `scoreStdev`, `estimateArray`, `moveNumber`, `blackToPlay`, `lastMove` (Optional\<int[]\>), `playouts` — see `src/main/java/featurecat/lizzie/rules/BoardData.java`
- `MoveData` fields: `coordinate`, `winrate`, `playouts`, `scoreMean`, `scoreStdev`, `policy`, `lcb`, `order`, `variation` (List\<String\>) — see `src/main/java/featurecat/lizzie/analysis/MoveData.java`
- `Stone` enum: `BLACK`, `WHITE`, `EMPTY` (plus recursed/captured variants) — see `src/main/java/featurecat/lizzie/rules/Stone.java`
- `Board.getIndex(x, y)` returns `x * boardHeight + y`; `Board.getCoord(index)` returns `{x, y}` — see `src/main/java/featurecat/lizzie/rules/Board.java`
- GTP coordinate format: column letters A-T (skip I), row numbers 1-19. `MoveData.coordinate` is already in this format (e.g., "Q16")

- [ ] **Step 1: Write test for `buildStonesArray`**

```java
package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.*;

import featurecat.lizzie.rules.Stone;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

public class WebBoardDataCollectorTest {

  @Test
  void buildStonesArray_convertsStoneEnumToInts() {
    Stone[] stones = new Stone[9]; // 3x3
    stones[0] = Stone.EMPTY;
    stones[1] = Stone.BLACK;
    stones[2] = Stone.WHITE;
    stones[3] = Stone.BLACK_RECURSED; // should map to 1 (black)
    stones[4] = Stone.WHITE_RECURSED; // should map to 2 (white)
    stones[5] = Stone.BLACK_CAPTURED; // should map to 0 (empty)
    stones[6] = Stone.WHITE_CAPTURED; // should map to 0 (empty)
    stones[7] = Stone.EMPTY;
    stones[8] = Stone.BLACK;

    JSONArray arr = WebBoardDataCollector.buildStonesArray(stones);
    assertEquals(9, arr.length());
    assertEquals(0, arr.getInt(0)); // EMPTY
    assertEquals(1, arr.getInt(1)); // BLACK
    assertEquals(2, arr.getInt(2)); // WHITE
    assertEquals(1, arr.getInt(3)); // BLACK_RECURSED → 1
    assertEquals(2, arr.getInt(4)); // WHITE_RECURSED → 2
    assertEquals(0, arr.getInt(5)); // BLACK_CAPTURED → 0
    assertEquals(0, arr.getInt(6)); // WHITE_CAPTURED → 0
    assertEquals(0, arr.getInt(7)); // EMPTY
    assertEquals(1, arr.getInt(8)); // BLACK
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=WebBoardDataCollectorTest#buildStonesArray_convertsStoneEnumToInts -pl .`
Expected: FAIL — class `WebBoardDataCollector` does not exist

- [ ] **Step 3: Implement `buildStonesArray`**

```java
package featurecat.lizzie.gui.web;

import featurecat.lizzie.rules.Stone;
import org.json.JSONArray;

public class WebBoardDataCollector {

  static JSONArray buildStonesArray(Stone[] stones) {
    JSONArray arr = new JSONArray();
    for (Stone s : stones) {
      if (s.isBlack()) arr.put(1);
      else if (s.isEmpty()) arr.put(0);
      else arr.put(2); // white variants
    }
    return arr;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=WebBoardDataCollectorTest#buildStonesArray_convertsStoneEnumToInts -pl .`
Expected: PASS

- [ ] **Step 5: Write test for `buildMoveDataJson`**

```java
@Test
void buildMoveDataJson_serializesAllFields() {
  MoveData move = new MoveData();
  move.coordinate = "Q16";
  move.winrate = 56.3;
  move.playouts = 3200;
  move.scoreMean = 2.5;
  move.scoreStdev = 8.1;
  move.policy = 0.18;
  move.lcb = 55.8;
  move.order = 0;
  move.variation = List.of("Q16", "D4", "R14");

  JSONObject json = WebBoardDataCollector.buildMoveDataJson(move, 19, 19);
  assertEquals("Q16", json.getString("coordinate"));
  assertEquals(56.3, json.getDouble("winrate"), 0.01);
  assertEquals(3200, json.getInt("playouts"));
  assertEquals(2.5, json.getDouble("scoreMean"), 0.01);
  assertEquals(0, json.getInt("order"));
  assertEquals(3, json.getJSONArray("variation").length());
  // x, y should be derived from GTP coordinate "Q16" on 19x19
  assertTrue(json.has("x"));
  assertTrue(json.has("y"));
}
```

- [ ] **Step 6: Run test to verify it fails, then implement `buildMoveDataJson`**

`buildMoveDataJson` should parse GTP coordinate to x,y indices. GTP coordinate parsing: column letter A-T (skip I) maps to x=0-18, row number maps to y = boardHeight - rowNumber.

```java
static JSONObject buildMoveDataJson(MoveData move, int boardWidth, int boardHeight) {
  JSONObject obj = new JSONObject();
  obj.put("coordinate", move.coordinate);
  int[] xy = gtpToXY(move.coordinate, boardHeight);
  if (xy != null) {
    obj.put("x", xy[0]);
    obj.put("y", xy[1]);
  }
  obj.put("winrate", move.winrate);
  obj.put("playouts", move.playouts);
  obj.put("scoreMean", move.scoreMean);
  obj.put("scoreStdev", move.scoreStdev);
  obj.put("policy", move.policy);
  obj.put("lcb", move.lcb);
  obj.put("order", move.order);
  JSONArray var = new JSONArray();
  if (move.variation != null) {
    for (String s : move.variation) var.put(s);
  }
  obj.put("variation", var);
  return obj;
}

static int[] gtpToXY(String coordinate, int boardHeight) {
  if (coordinate == null || coordinate.length() < 2) return null;
  char col = coordinate.toUpperCase().charAt(0);
  int x = col - 'A';
  if (col > 'I') x--; // GTP skips 'I'
  int row;
  try {
    row = Integer.parseInt(coordinate.substring(1));
  } catch (NumberFormatException e) {
    return null;
  }
  int y = boardHeight - row;
  return new int[] {x, y};
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `mvn test -Dtest=WebBoardDataCollectorTest -pl .`
Expected: PASS

- [ ] **Step 8: Write test for `buildAnalysisUpdateJson`**

This method builds the `analysis_update` message from a list of MoveData + board metadata.

```java
@Test
void buildAnalysisUpdateJson_createsCorrectStructure() {
  MoveData move1 = new MoveData();
  move1.coordinate = "Q16";
  move1.winrate = 56.3;
  move1.playouts = 3200;
  move1.scoreMean = 2.5;
  move1.scoreStdev = 8.1;
  move1.policy = 0.18;
  move1.lcb = 55.8;
  move1.order = 0;
  move1.variation = List.of("Q16", "D4");

  List<MoveData> moves = List.of(move1);
  ArrayList<Double> estimate = new ArrayList<>(List.of(0.9, -0.8, 0.0));

  JSONObject json = WebBoardDataCollector.buildAnalysisUpdateJson(
      moves, 56.3, 2.5, 12800, estimate, 19, 19);

  assertEquals("analysis_update", json.getString("type"));
  assertEquals(56.3, json.getDouble("winrate"), 0.01);
  assertEquals(2.5, json.getDouble("scoreMean"), 0.01);
  assertEquals(12800, json.getInt("playouts"));
  assertEquals(1, json.getJSONArray("bestMoves").length());
  assertEquals(3, json.getJSONArray("estimateArray").length());
}

@Test
void buildAnalysisUpdateJson_handlesNullEstimate() {
  JSONObject json = WebBoardDataCollector.buildAnalysisUpdateJson(
      List.of(), 50.0, 0.0, 0, null, 19, 19);
  assertTrue(json.isNull("estimateArray"));
}
```

- [ ] **Step 9: Implement `buildAnalysisUpdateJson` and run tests**

```java
static JSONObject buildAnalysisUpdateJson(
    List<MoveData> bestMoves, double winrate, double scoreMean,
    int playouts, ArrayList<Double> estimateArray, int boardWidth, int boardHeight) {
  JSONObject obj = new JSONObject();
  obj.put("type", "analysis_update");
  JSONArray movesArr = new JSONArray();
  for (MoveData m : bestMoves) {
    movesArr.put(buildMoveDataJson(m, boardWidth, boardHeight));
  }
  obj.put("bestMoves", movesArr);
  obj.put("winrate", winrate);
  obj.put("scoreMean", scoreMean);
  obj.put("playouts", playouts);
  if (estimateArray != null) {
    JSONArray est = new JSONArray();
    for (Double d : estimateArray) est.put(d);
    obj.put("estimateArray", est);
  } else {
    obj.put("estimateArray", JSONObject.NULL);
  }
  return obj;
}
```

Run: `mvn test -Dtest=WebBoardDataCollectorTest -pl .`
Expected: PASS

- [ ] **Step 10: Write test for `buildFullStateJson`**

```java
@Test
void buildFullStateJson_includesAllBoardFields() {
  Stone[] stones = new Stone[361];
  Arrays.fill(stones, Stone.EMPTY);
  stones[0] = Stone.BLACK;

  List<MoveData> bestMoves = new ArrayList<>();
  MoveData m = new MoveData();
  m.coordinate = "D4";
  m.winrate = 50.0;
  m.playouts = 100;
  m.order = 0;
  m.variation = List.of("D4");
  bestMoves.add(m);

  JSONObject json = WebBoardDataCollector.buildFullStateJson(
      19, 19, stones, new int[]{3, 15}, 10, true,
      bestMoves, 50.0, 0.0, 100, null);

  assertEquals("full_state", json.getString("type"));
  assertEquals(19, json.getInt("boardWidth"));
  assertEquals(19, json.getInt("boardHeight"));
  assertEquals(361, json.getJSONArray("stones").length());
  assertEquals(1, json.getJSONArray("stones").getInt(0)); // BLACK
  assertEquals(3, json.getJSONArray("lastMove").getInt(0));
  assertEquals(15, json.getJSONArray("lastMove").getInt(1));
  assertEquals(10, json.getInt("moveNumber"));
  assertEquals("B", json.getString("currentPlayer"));
  assertFalse(json.getJSONArray("bestMoves").isEmpty());
}
```

- [ ] **Step 11: Implement `buildFullStateJson` and run tests**

```java
static JSONObject buildFullStateJson(
    int boardWidth, int boardHeight, Stone[] stones, int[] lastMove,
    int moveNumber, boolean blackToPlay, List<MoveData> bestMoves,
    double winrate, double scoreMean, int playouts,
    ArrayList<Double> estimateArray) {
  JSONObject obj = new JSONObject();
  obj.put("type", "full_state");
  obj.put("boardWidth", boardWidth);
  obj.put("boardHeight", boardHeight);
  obj.put("stones", buildStonesArray(stones));
  if (lastMove != null) {
    obj.put("lastMove", new JSONArray(lastMove));
  } else {
    obj.put("lastMove", JSONObject.NULL);
  }
  obj.put("moveNumber", moveNumber);
  obj.put("currentPlayer", blackToPlay ? "B" : "W");
  JSONArray movesArr = new JSONArray();
  if (bestMoves != null) {
    for (MoveData m : bestMoves) {
      movesArr.put(buildMoveDataJson(m, boardWidth, boardHeight));
    }
  }
  obj.put("bestMoves", movesArr);
  obj.put("winrate", winrate);
  obj.put("scoreMean", scoreMean);
  obj.put("playouts", playouts);
  if (estimateArray != null) {
    JSONArray est = new JSONArray();
    for (Double d : estimateArray) est.put(d);
    obj.put("estimateArray", est);
  } else {
    obj.put("estimateArray", JSONObject.NULL);
  }
  return obj;
}
```

Run: `mvn test -Dtest=WebBoardDataCollectorTest -pl .`
Expected: PASS

- [ ] **Step 12: Write test for `buildWinrateHistoryJson`**

```java
@Test
void buildWinrateHistoryJson_traversesNodeChain() {
  // Create a chain: node0 (move 0) → node1 (move 1) → node2 (move 2)
  BoardData d0 = createBoardData(0, 50.0, 0.0, true);
  BoardData d1 = createBoardData(1, 55.0, 1.5, false);
  BoardData d2 = createBoardData(2, 48.0, -0.8, true);

  BoardHistoryNode node0 = new BoardHistoryNode(d0);
  BoardHistoryNode node1 = new BoardHistoryNode(d1);
  BoardHistoryNode node2 = new BoardHistoryNode(d2);
  node0.variations.add(node1);
  node1.variations.add(node2);

  JSONObject json = WebBoardDataCollector.buildWinrateHistoryJson(node0, node2);
  assertEquals("winrate_history", json.getString("type"));
  JSONArray data = json.getJSONArray("data");
  assertEquals(3, data.length());
  assertEquals(0, data.getJSONObject(0).getInt("moveNumber"));
  assertEquals(55.0, data.getJSONObject(1).getDouble("winrate"), 0.01);
}

private BoardData createBoardData(int moveNum, double winrate, double scoreMean, boolean blackToPlay) {
  Stone[] stones = new Stone[361];
  Arrays.fill(stones, Stone.EMPTY);
  BoardData d = new BoardData(
      stones, Optional.empty(), Stone.EMPTY, blackToPlay,
      new Zobrist(), moveNum, new int[361], 0, 0, winrate, 0);
  d.scoreMean = scoreMean;
  return d;
}
```

Note: Check `BoardData` constructors before writing this test. The class has public fields, so if a no-arg constructor doesn't exist, use whatever constructor is available and set fields directly.

- [ ] **Step 13: Implement `buildWinrateHistoryJson` and run tests**

Traverse from the root node (`node0`) to the current node along the main line, collecting winrate/scoreMean per move.

```java
static JSONObject buildWinrateHistoryJson(BoardHistoryNode root, BoardHistoryNode current) {
  JSONObject obj = new JSONObject();
  obj.put("type", "winrate_history");
  JSONArray data = new JSONArray();

  // Walk from root forward to current along main variation
  BoardHistoryNode node = root;
  while (node != null) {
    BoardData d = node.getData();
    JSONObject entry = new JSONObject();
    entry.put("moveNumber", d.moveNumber);
    entry.put("winrate", d.winrate);
    entry.put("scoreMean", d.scoreMean);
    data.put(entry);
    if (node == current) break;
    Optional<BoardHistoryNode> next = node.next();
    node = next.isPresent() ? next.get() : null;
  }

  obj.put("data", data);
  return obj;
}
```

Run: `mvn test -Dtest=WebBoardDataCollectorTest -pl .`
Expected: PASS

- [ ] **Step 14: Add throttling logic to `WebBoardDataCollector`**

Add the executor, throttle fields, and `onAnalysisUpdated()` / `onBoardStateChanged()` public methods. These will be called from hooks in `LizzieFrame.refresh()`.

```java
// Add to WebBoardDataCollector:
private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
private volatile WebBoardServer server;
private volatile long lastBroadcastTime = 0;
private static final long MIN_BROADCAST_INTERVAL_MS = 100; // 10 updates/sec max
private volatile boolean pendingUpdate = false;

public WebBoardDataCollector() {}

public void setServer(WebBoardServer server) {
  this.server = server;
}

public void onAnalysisUpdated() {
  long now = System.currentTimeMillis();
  if (now - lastBroadcastTime < MIN_BROADCAST_INTERVAL_MS) {
    if (!pendingUpdate) {
      pendingUpdate = true;
      long delay = MIN_BROADCAST_INTERVAL_MS - (now - lastBroadcastTime);
      executor.schedule(this::doBroadcastAnalysis, delay, TimeUnit.MILLISECONDS);
    }
    return;
  }
  executor.execute(this::doBroadcastAnalysis);
}

public void onBoardStateChanged() {
  executor.execute(this::doBroadcastFullState);
}

private void doBroadcastAnalysis() {
  pendingUpdate = false;
  lastBroadcastTime = System.currentTimeMillis();
  if (server == null) return;
  // Collect current state from Lizzie.board
  // Build analysis_update JSON and broadcast
}

private void doBroadcastFullState() {
  lastBroadcastTime = System.currentTimeMillis();
  if (server == null) return;
  // Build full_state + winrate_history JSON and broadcast
}

public void shutdown() {
  executor.shutdownNow();
  server = null;
}
```

- [ ] **Step 15: Commit Task 1**

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardDataCollector.java \
        src/test/java/featurecat/lizzie/gui/web/WebBoardDataCollectorTest.java
git commit -m "feat(web): add WebBoardDataCollector with JSON serialization and throttling"
```

---

## Task 2: WebBoardServer — WebSocket Server

**Files:**
- Create: `src/main/java/featurecat/lizzie/gui/web/WebBoardServer.java`
- Test: `src/test/java/featurecat/lizzie/gui/web/WebBoardServerTest.java`

**Context needed:**
- `org.java_websocket.server.WebSocketServer` — extend this class
- Constructor takes `InetSocketAddress`
- Override: `onOpen`, `onClose`, `onMessage`, `onError`, `onStart`
- `getConnections()` returns all connected clients
- `broadcast(String)` sends to all connections
- Each connection is a `WebSocket` object with `send(String)` method

- [ ] **Step 1: Write test for connection limit enforcement**

```java
package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebBoardServerTest {
  private WebBoardServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    port = 19876; // Use a high port unlikely to conflict
    server = new WebBoardServer(new InetSocketAddress("127.0.0.1", port), 2);
    server.start();
    Thread.sleep(200); // Wait for server to start
  }

  @AfterEach
  void tearDown() throws Exception {
    server.stop(100);
  }

  @Test
  void rejectsConnectionsAboveLimit() throws Exception {
    CountDownLatch openLatch = new CountDownLatch(2);
    CountDownLatch closeLatch = new CountDownLatch(1);

    List<TestClient> clients = new ArrayList<>();
    // Connect 2 clients (the limit)
    for (int i = 0; i < 2; i++) {
      TestClient c = new TestClient(port, openLatch, null);
      c.connectBlocking(2, TimeUnit.SECONDS);
      clients.add(c);
    }
    assertTrue(openLatch.await(3, TimeUnit.SECONDS));

    // 3rd client should be rejected
    TestClient rejected = new TestClient(port, null, closeLatch);
    rejected.connectBlocking(2, TimeUnit.SECONDS);
    assertTrue(closeLatch.await(3, TimeUnit.SECONDS), "3rd connection should be closed");

    clients.forEach(TestClient::close);
  }

  private static class TestClient extends WebSocketClient {
    private final CountDownLatch openLatch;
    private final CountDownLatch closeLatch;

    TestClient(int port, CountDownLatch openLatch, CountDownLatch closeLatch) throws Exception {
      super(new URI("ws://127.0.0.1:" + port));
      this.openLatch = openLatch;
      this.closeLatch = closeLatch;
    }

    @Override public void onOpen(ServerHandshake h) { if (openLatch != null) openLatch.countDown(); }
    @Override public void onClose(int code, String reason, boolean remote) { if (closeLatch != null) closeLatch.countDown(); }
    @Override public void onMessage(String msg) {}
    @Override public void onError(Exception e) {}
  }
}
```

- [ ] **Step 2: Run test to verify it fails, then implement `WebBoardServer`**

```java
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
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -Dtest=WebBoardServerTest -pl .`
Expected: PASS

- [ ] **Step 4: Write test for full_state sent on connect**

```java
@Test
void sendsFullStateOnConnect() throws Exception {
  String fullState = "{\"type\":\"full_state\",\"boardWidth\":19}";
  server.broadcastFullState(fullState);

  CountDownLatch msgLatch = new CountDownLatch(1);
  AtomicReference<String> received = new AtomicReference<>();
  TestClient c = new TestClient(port, null, null) {
    @Override
    public void onMessage(String msg) {
      received.set(msg);
      msgLatch.countDown();
    }
  };
  c.connectBlocking(2, TimeUnit.SECONDS);
  assertTrue(msgLatch.await(3, TimeUnit.SECONDS));
  assertTrue(received.get().contains("full_state"));
  c.close();
}
```

- [ ] **Step 5: Run test, fix if needed, then commit**

Run: `mvn test -Dtest=WebBoardServerTest -pl .`
Expected: PASS

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardServer.java \
        src/test/java/featurecat/lizzie/gui/web/WebBoardServerTest.java
git commit -m "feat(web): add WebBoardServer with connection limit and state replay"
```

---

## Task 3: WebBoardHttpServer — Static File Server

**Files:**
- Create: `src/main/java/featurecat/lizzie/gui/web/WebBoardHttpServer.java`
- Test: `src/test/java/featurecat/lizzie/gui/web/WebBoardHttpServerTest.java`

**Context needed:**
- Uses `java.net.ServerSocket` and `java.net.Socket` for HTTP
- Reads files from classpath via `getClass().getResourceAsStream("/web/...")`
- Must handle: `GET /` → serve `index.html`, `GET /board.js` → serve `board.js`, etc.
- Content-Type mapping: `.html` → `text/html`, `.js` → `application/javascript`, `.css` → `text/css`
- Path traversal: reject any path containing `..` or starting with `/`

- [ ] **Step 1: Write test for path traversal rejection**

```java
package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebBoardHttpServerTest {
  private WebBoardHttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws Exception {
    port = 19877;
    server = new WebBoardHttpServer(port);
    server.start();
    Thread.sleep(200);
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  @Test
  void rejectsPathTraversal() throws Exception {
    String response = httpGet("/../../../etc/passwd");
    assertTrue(response.contains("403"));
  }

  @Test
  void rejectsNonGetMethod() throws Exception {
    String response = httpRequest("POST / HTTP/1.1\r\nHost: localhost\r\n\r\n");
    assertTrue(response.contains("405"));
  }

  @Test
  void servesIndexHtml() throws Exception {
    // This will return 404 until we create the web resources, but should not 403
    String response = httpGet("/");
    assertFalse(response.contains("403"));
  }

  private String httpGet(String path) throws Exception {
    return httpRequest("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
  }

  private String httpRequest(String raw) throws Exception {
    try (Socket s = new Socket("127.0.0.1", port);
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
      out.print(raw);
      out.flush();
      StringBuilder sb = new StringBuilder();
      String line;
      s.setSoTimeout(2000);
      try {
        while ((line = in.readLine()) != null) sb.append(line).append("\n");
      } catch (Exception ignored) {}
      return sb.toString();
    }
  }
}
```

- [ ] **Step 2: Implement `WebBoardHttpServer`**

```java
package featurecat.lizzie.gui.web;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;

public class WebBoardHttpServer {
  private final int port;
  private ServerSocket serverSocket;
  private Thread acceptThread;
  private volatile boolean running;

  private static final Map<String, String> MIME_TYPES = Map.of(
      "html", "text/html; charset=utf-8",
      "js", "application/javascript; charset=utf-8",
      "css", "text/css; charset=utf-8",
      "png", "image/png",
      "svg", "image/svg+xml"
  );

  public WebBoardHttpServer(int port) {
    this.port = port;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    running = true;
    acceptThread = new Thread(this::acceptLoop, "WebBoardHttp");
    acceptThread.setDaemon(true);
    acceptThread.start();
  }

  public void stop() {
    running = false;
    try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
  }

  private void acceptLoop() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        new Thread(() -> handleClient(client), "WebBoardHttp-client").start();
      } catch (SocketException e) {
        if (!running) break;
      } catch (IOException e) {
        break;
      }
    }
  }

  private void handleClient(Socket client) {
    try (client;
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        OutputStream out = client.getOutputStream()) {

      String requestLine = in.readLine();
      if (requestLine == null) return;

      String[] parts = requestLine.split(" ");
      if (parts.length < 2) return;

      if (!"GET".equalsIgnoreCase(parts[0])) {
        sendError(out, 405, "Method Not Allowed");
        return;
      }

      String path = parts[1];
      if (path.contains("..") || path.contains("\\")) {
        sendError(out, 403, "Forbidden");
        return;
      }

      if ("/".equals(path)) path = "/index.html";

      String resourcePath = "/web" + path;
      InputStream resource = getClass().getResourceAsStream(resourcePath);
      if (resource == null) {
        sendError(out, 404, "Not Found");
        return;
      }

      byte[] body = resource.readAllBytes();
      resource.close();
      String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "";
      String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

      String header = "HTTP/1.1 200 OK\r\n"
          + "Content-Type: " + contentType + "\r\n"
          + "Content-Length: " + body.length + "\r\n"
          + "Access-Control-Allow-Origin: *\r\n"
          + "\r\n";
      out.write(header.getBytes());
      out.write(body);
      out.flush();
    } catch (IOException ignored) {}
  }

  private void sendError(OutputStream out, int code, String message) throws IOException {
    String body = "<h1>" + code + " " + message + "</h1>";
    String response = "HTTP/1.1 " + code + " " + message + "\r\n"
        + "Content-Type: text/html\r\n"
        + "Content-Length: " + body.length() + "\r\n"
        + "\r\n"
        + body;
    out.write(response.getBytes());
    out.flush();
  }

  public int getPort() { return port; }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -Dtest=WebBoardHttpServerTest -pl .`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardHttpServer.java \
        src/test/java/featurecat/lizzie/gui/web/WebBoardHttpServerTest.java
git commit -m "feat(web): add WebBoardHttpServer with path traversal protection"
```

---

## Task 4: WebBoardManager — Lifecycle Orchestrator

**Files:**
- Create: `src/main/java/featurecat/lizzie/gui/web/WebBoardManager.java`
- Test: `src/test/java/featurecat/lizzie/gui/web/WebBoardManagerTest.java`

**Context needed:**
- Orchestrates startup/shutdown of `WebBoardServer`, `WebBoardHttpServer`, `WebBoardDataCollector`
- Resolves LAN IP via `NetworkInterface` enumeration
- Port-conflict retry logic (try port, port+1, ..., up to 10 times)
- Config access: `Lizzie.config.config.optJSONObject("web-board")` for port/maxConnections settings
- Shutdown order: collector → WebSocket → HTTP

- [ ] **Step 1: Implement `WebBoardManager`**

```java
package featurecat.lizzie.gui.web;

import featurecat.lizzie.Lizzie;
import java.net.*;
import java.util.Enumeration;
import org.json.JSONObject;

public class WebBoardManager {
  private WebBoardServer wsServer;
  private WebBoardHttpServer httpServer;
  private WebBoardDataCollector collector;
  private boolean running;
  private String accessUrl;
  private int actualHttpPort;
  private int actualWsPort;

  public synchronized boolean start() {
    if (running) return true;
    JSONObject cfg = Lizzie.config.config.optJSONObject("web-board");
    int httpPort = cfg != null ? cfg.optInt("http-port", 9998) : 9998;
    int wsPort = cfg != null ? cfg.optInt("ws-port", 9999) : 9999;
    int maxConn = cfg != null ? cfg.optInt("max-connections", 20) : 20;

    // Start HTTP server with port retry
    httpServer = null;
    for (int i = 0; i < 10; i++) {
      try {
        httpServer = new WebBoardHttpServer(httpPort + i);
        httpServer.start();
        actualHttpPort = httpPort + i;
        break;
      } catch (Exception e) {
        httpServer = null;
      }
    }
    if (httpServer == null) return false;

    // Start WebSocket server with port retry
    wsServer = null;
    for (int i = 0; i < 10; i++) {
      try {
        wsServer = new WebBoardServer(
            new InetSocketAddress("0.0.0.0", wsPort + i), maxConn);
        wsServer.start();
        actualWsPort = wsPort + i;
        break;
      } catch (Exception e) {
        wsServer = null;
      }
    }
    if (wsServer == null) {
      httpServer.stop();
      return false;
    }

    collector = new WebBoardDataCollector();
    collector.setServer(wsServer);

    String ip = getLanIp();
    accessUrl = "http://" + ip + ":" + actualHttpPort;
    running = true;
    return true;
  }

  public synchronized void stop() {
    if (!running) return;
    if (collector != null) { collector.shutdown(); collector = null; }
    if (wsServer != null) { try { wsServer.stop(500); } catch (Exception ignored) {} wsServer = null; }
    if (httpServer != null) { httpServer.stop(); httpServer = null; }
    running = false;
    accessUrl = null;
  }

  public boolean isRunning() { return running; }
  public String getAccessUrl() { return accessUrl; }
  public int getWsPort() { return actualWsPort; }

  public WebBoardDataCollector getCollector() { return collector; }

  static String getLanIp() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if (ni.isLoopback() || !ni.isUp()) continue;
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
          InetAddress addr = addrs.nextElement();
          if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
            return addr.getHostAddress();
          }
        }
      }
    } catch (SocketException ignored) {}
    return "127.0.0.1";
  }
}
```

- [ ] **Step 2: Write test for `getLanIp` fallback**

```java
package featurecat.lizzie.gui.web;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class WebBoardManagerTest {

  @Test
  void getLanIp_returnsNonNullAddress() {
    String ip = WebBoardManager.getLanIp();
    assertNotNull(ip);
    assertFalse(ip.isEmpty());
    // Should be a valid IPv4 format
    assertTrue(ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), "Expected IPv4 format but got: " + ip);
  }
}
```

Run: `mvn test -Dtest=WebBoardManagerTest -pl .`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardManager.java \
        src/test/java/featurecat/lizzie/gui/web/WebBoardManagerTest.java
git commit -m "feat(web): add WebBoardManager lifecycle orchestrator"
```

---

## Task 5: Wire DataCollector to Live Data

Connect `WebBoardDataCollector.doBroadcastAnalysis()` and `doBroadcastFullState()` to real `Lizzie.board` data. Hook `LizzieFrame.refresh()` to trigger notifications.

**Files:**
- Modify: `src/main/java/featurecat/lizzie/gui/web/WebBoardDataCollector.java` — implement `doBroadcastAnalysis` and `doBroadcastFullState` using live `Lizzie.board` data
- Modify: `src/main/java/featurecat/lizzie/gui/LizzieFrame.java:5032-5061` — add notification calls to `refresh()` and `refresh(int)`
- Modify: `src/main/java/featurecat/lizzie/Lizzie.java` — add `public static WebBoardManager webBoardManager` field

- [ ] **Step 1: Implement live data collection in `doBroadcastAnalysis`**

In `WebBoardDataCollector.java`, update `doBroadcastAnalysis()`:

```java
private void doBroadcastAnalysis() {
  pendingUpdate = false;
  lastBroadcastTime = System.currentTimeMillis();
  if (server == null) return;
  try {
    BoardData data = Lizzie.board.getHistory().getCurrentHistoryNode().getData();
    if (data.bestMoves == null || data.bestMoves.isEmpty()) return;
    int bw = Board.boardWidth;
    int bh = Board.boardHeight;
    JSONObject json = buildAnalysisUpdateJson(
        data.bestMoves, data.winrate, data.scoreMean,
        data.getPlayouts(), data.estimateArray, bw, bh);
    server.broadcastMessage(json.toString());
  } catch (Exception ignored) {}
}
```

- [ ] **Step 2: Implement live data collection in `doBroadcastFullState`**

```java
private void doBroadcastFullState() {
  lastBroadcastTime = System.currentTimeMillis();
  if (server == null) return;
  try {
    BoardHistoryNode currentNode = Lizzie.board.getHistory().getCurrentHistoryNode();
    BoardData data = currentNode.getData();
    int bw = Board.boardWidth;
    int bh = Board.boardHeight;
    int[] lastMove = data.lastMove.isPresent() ? data.lastMove.get() : null;
    JSONObject fullState = buildFullStateJson(
        bw, bh, data.stones, lastMove, data.moveNumber, data.blackToPlay,
        data.bestMoves, data.winrate, data.scoreMean,
        data.getPlayouts(), data.estimateArray);
    server.broadcastFullState(fullState.toString());

    // Also send winrate history
    BoardHistoryNode root = Lizzie.board.getHistory().getStart();
    JSONObject history = buildWinrateHistoryJson(root, currentNode);
    server.broadcastMessage(history.toString());
  } catch (Exception ignored) {}
}
```

- [ ] **Step 3: Add `WebBoardManager` field to `Lizzie.java`**

Add after `public static EngineManager engineManager;` (around line 48 in `Lizzie.java`):

```java
public static WebBoardManager webBoardManager;
```

Initialize in main (after other components):

```java
webBoardManager = new WebBoardManager();
```

- [ ] **Step 4: Hook `LizzieFrame.refresh()` to notify collector**

In `LizzieFrame.java`, at the end of `refresh()` (line ~5042, before the closing `}`):

```java
if (Lizzie.webBoardManager != null && Lizzie.webBoardManager.isRunning()) {
  WebBoardDataCollector c = Lizzie.webBoardManager.getCollector();
  if (c != null) c.onBoardStateChanged();
}
```

In `refresh(int mode)` (line ~5061), inside `case 1:` after `repaint()`:

```java
if (Lizzie.webBoardManager != null && Lizzie.webBoardManager.isRunning()) {
  WebBoardDataCollector c = Lizzie.webBoardManager.getCollector();
  if (c != null) c.onAnalysisUpdated();
}
```

- [ ] **Step 5: Add necessary imports and verify build**

Run: `mvn -B -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardDataCollector.java \
        src/main/java/featurecat/lizzie/gui/LizzieFrame.java \
        src/main/java/featurecat/lizzie/Lizzie.java
git commit -m "feat(web): wire data collector to live board state and analysis"
```

---

## Task 6: Menu Integration

Add "Web 旁观" submenu to the "同步" (sync/live) menu in `Menu.java`, with i18n support.

**Files:**
- Modify: `src/main/java/featurecat/lizzie/gui/Menu.java:3994-3998` — add submenu items after the "live" menu creation
- Modify: `src/main/resources/l10n/DisplayStrings*.properties` (7 files) — add keys

- [ ] **Step 1: Add i18n keys to all locale files**

Keys to add to each `DisplayStrings*.properties`:

| Key | zh_CN | en_US | ja_JP | ko | zh_TW | zh_HK |
|-----|-------|-------|-------|-----|-------|-------|
| `Menu.webBoard` | Web 旁观 | Web Viewer | Web ビューア | Web 뷰어 | Web 旁觀 | Web 旁觀 |
| `Menu.webBoardStart` | 启动 Web 旁观 | Start Web Viewer | Web ビューアを開始 | Web 뷰어 시작 | 啟動 Web 旁觀 | 啟動 Web 旁觀 |
| `Menu.webBoardStop` | 停止 Web 旁观 | Stop Web Viewer | Web ビューアを停止 | Web 뷰어 중지 | 停止 Web 旁觀 | 停止 Web 旁觀 |
| `Menu.webBoardCopyUrl` | 复制访问地址 | Copy Access URL | アクセスURLをコピー | 접속 URL 복사 | 複製訪問地址 | 複製訪問地址 |

Note: `DisplayStrings.properties` (no locale) is the default — use the zh_CN values.

- [ ] **Step 2: Add menu items in `Menu.java`**

After `this.add(live);` (line 3998), add the web board submenu to the `live` menu:

```java
live.addSeparator();
final JFontMenu webBoardMenu = new JFontMenu(resourceBundle.getString("Menu.webBoard"));
live.add(webBoardMenu);

final JFontMenuItem webBoardToggle =
    new JFontMenuItem(resourceBundle.getString("Menu.webBoardStart"));
webBoardMenu.add(webBoardToggle);
webBoardToggle.addActionListener(e -> {
  if (Lizzie.webBoardManager.isRunning()) {
    Lizzie.webBoardManager.stop();
    webBoardToggle.setText(resourceBundle.getString("Menu.webBoardStart"));
    Lizzie.frame.setTitle(Lizzie.frame.getTitle().replaceAll(" \\| Web: .*", ""));
  } else {
    boolean ok = Lizzie.webBoardManager.start();
    if (ok) {
      webBoardToggle.setText(resourceBundle.getString("Menu.webBoardStop"));
      Lizzie.frame.setTitle(Lizzie.frame.getTitle() + " | Web: " + Lizzie.webBoardManager.getAccessUrl());
    }
  }
});

final JFontMenuItem webBoardCopyUrl =
    new JFontMenuItem(resourceBundle.getString("Menu.webBoardCopyUrl"));
webBoardMenu.add(webBoardCopyUrl);
webBoardCopyUrl.addActionListener(e -> {
  if (Lizzie.webBoardManager.isRunning()) {
    String url = Lizzie.webBoardManager.getAccessUrl();
    java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(url);
    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
  }
});
```

- [ ] **Step 3: Verify build**

Run: `mvn -B -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/featurecat/lizzie/gui/Menu.java \
        src/main/resources/l10n/DisplayStrings*.properties
git commit -m "feat(web): add Web 旁观 menu with start/stop and copy URL"
```

---

## Task 7: Frontend — HTML Structure and CSS

**Files:**
- Create: `src/main/resources/web/index.html`
- Create: `src/main/resources/web/board.css`

- [ ] **Step 1: Create `index.html`**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LizzieYzy Web Viewer</title>
  <link rel="stylesheet" href="board.css">
</head>
<body>
  <div id="app">
    <div id="board-container">
      <canvas id="board-canvas"></canvas>
    </div>
    <div id="info-panel">
      <div id="status-bar">
        <span id="move-number">-</span>
        <span id="current-player">-</span>
        <span id="winrate">-</span>
        <span id="score-mean">-</span>
        <span id="playouts">-</span>
      </div>
      <div id="controls">
        <button id="toggle-heatmap" style="display:none">形势判断</button>
        <button id="toggle-score">目差曲线</button>
      </div>
      <canvas id="winrate-chart"></canvas>
    </div>
  </div>
  <div id="connection-overlay" style="display:none">
    <div id="connection-message">连接断开，正在重连...</div>
  </div>
  <script src="board.js"></script>
</body>
</html>
```

- [ ] **Step 2: Create `board.css`**

```css
* { margin: 0; padding: 0; box-sizing: border-box; }
body { background: #1a1a2e; color: #ccc; font-family: -apple-system, sans-serif; overflow: hidden; height: 100vh; }

#app { display: flex; height: 100vh; }
#board-container { flex: 3; display: flex; align-items: center; justify-content: center; padding: 16px; }
#board-canvas { max-width: 100%; max-height: 100%; }

#info-panel { flex: 1.2; display: flex; flex-direction: column; padding: 16px; border-left: 1px solid #333; min-width: 200px; }
#status-bar { display: flex; flex-direction: column; gap: 8px; font-size: 14px; padding-bottom: 12px; border-bottom: 1px solid #333; }
#status-bar span { display: flex; justify-content: space-between; }
#controls { padding: 8px 0; display: flex; gap: 8px; }
#controls button { background: #2a2a3e; color: #ccc; border: 1px solid #444; padding: 4px 10px; border-radius: 4px; cursor: pointer; font-size: 12px; }
#controls button.active { background: #3a5a3a; border-color: #4a7a4a; }
#winrate-chart { flex: 1; min-height: 80px; }

#connection-overlay {
  position: fixed; top: 0; left: 0; width: 100%; height: 100%;
  background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 100;
}
#connection-message { color: #fff; font-size: 18px; }

/* Mobile: stack vertically */
@media (max-width: 768px) {
  #app { flex-direction: column; }
  #board-container { flex: none; padding: 8px; }
  #info-panel { flex: none; border-left: none; border-top: 1px solid #333; padding: 8px; min-width: auto; }
  #status-bar { flex-direction: row; flex-wrap: wrap; justify-content: space-around; }
  #status-bar span { flex-direction: row; gap: 4px; }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/web/index.html src/main/resources/web/board.css
git commit -m "feat(web): add frontend HTML structure and responsive CSS"
```

---

## Task 8: Frontend — Canvas Board Rendering (board.js)

The largest frontend task. Implements WebSocket connection, Canvas rendering of the Go board, candidate moves, variation hover, heatmap, and winrate chart.

**Files:**
- Create: `src/main/resources/web/board.js`

- [ ] **Step 1: Create `board.js` with WebSocket connection and reconnect logic**

```javascript
// board.js — LizzieYzy Web Board Viewer

(function () {
  'use strict';

  // --- State ---
  let boardState = null;   // full_state data
  let analysisData = null; // latest analysis_update
  let winrateHistory = []; // winrate_history data array
  let hoveredMove = null;  // MoveData being hovered
  let showHeatmap = false;
  let showScoreCurve = false;

  // --- WebSocket ---
  let ws = null;
  let reconnectDelay = 1000;
  const MAX_RECONNECT_DELAY = 30000;

  function connectWs() {
    const wsPort = parseInt(location.port) + 1; // HTTP port + 1 = WS port by convention
    const wsUrl = 'ws://' + location.hostname + ':' + wsPort;
    ws = new WebSocket(wsUrl);

    ws.onopen = function () {
      reconnectDelay = 1000;
      document.getElementById('connection-overlay').style.display = 'none';
    };

    ws.onmessage = function (event) {
      const msg = JSON.parse(event.data);
      if (msg.type === 'full_state') {
        boardState = msg;
        analysisData = msg;
        render();
      } else if (msg.type === 'analysis_update') {
        analysisData = msg;
        if (boardState) {
          boardState.bestMoves = msg.bestMoves;
          boardState.winrate = msg.winrate;
          boardState.scoreMean = msg.scoreMean;
          boardState.playouts = msg.playouts;
          boardState.estimateArray = msg.estimateArray;
        }
        render();
      } else if (msg.type === 'winrate_history') {
        winrateHistory = msg.data || [];
        renderWinrateChart();
      }
    };

    ws.onclose = function () {
      document.getElementById('connection-overlay').style.display = 'flex';
      setTimeout(function () {
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
        connectWs();
      }, reconnectDelay);
    };

    ws.onerror = function () { ws.close(); };
  }
```

- [ ] **Step 2: Add Canvas board rendering functions**

Continue in `board.js`:

```javascript
  // --- Canvas Setup ---
  const boardCanvas = document.getElementById('board-canvas');
  const ctx = boardCanvas.getContext('2d');

  function render() {
    if (!boardState) return;
    const bw = boardState.boardWidth || 19;
    const bh = boardState.boardHeight || 19;
    const container = document.getElementById('board-container');
    const size = Math.min(container.clientWidth - 32, container.clientHeight - 32);
    boardCanvas.width = size;
    boardCanvas.height = size;

    const margin = size * 0.04;
    const gridSize = (size - 2 * margin) / (bw - 1);

    drawBoard(ctx, size, margin, gridSize, bw, bh);
    drawStones(ctx, boardState.stones, margin, gridSize, bw, bh, boardState.lastMove);
    if (showHeatmap && boardState.estimateArray) {
      drawHeatmap(ctx, boardState.estimateArray, margin, gridSize, bw, bh);
    }
    if (analysisData && analysisData.bestMoves) {
      drawCandidates(ctx, analysisData.bestMoves, margin, gridSize, bw, bh);
    }
    if (hoveredMove && hoveredMove.variation) {
      drawVariation(ctx, hoveredMove, boardState.stones, margin, gridSize, bw, bh);
    }
    updateStatusBar();
  }

  function drawBoard(ctx, size, margin, gridSize, bw, bh) {
    // Board background
    ctx.fillStyle = '#c8a45c';
    ctx.fillRect(0, 0, size, size);

    // Grid lines
    ctx.strokeStyle = '#8B7355';
    ctx.lineWidth = 1;
    for (let i = 0; i < bw; i++) {
      const x = margin + i * gridSize;
      ctx.beginPath(); ctx.moveTo(x, margin); ctx.lineTo(x, margin + (bh - 1) * gridSize); ctx.stroke();
    }
    for (let i = 0; i < bh; i++) {
      const y = margin + i * gridSize;
      ctx.beginPath(); ctx.moveTo(margin, y); ctx.lineTo(margin + (bw - 1) * gridSize, y); ctx.stroke();
    }

    // Star points (for 19x19)
    if (bw === 19 && bh === 19) {
      const stars = [[3,3],[3,9],[3,15],[9,3],[9,9],[9,15],[15,3],[15,9],[15,15]];
      ctx.fillStyle = '#8B7355';
      for (const [sx, sy] of stars) {
        ctx.beginPath();
        ctx.arc(margin + sx * gridSize, margin + sy * gridSize, gridSize * 0.12, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }

  function drawStones(ctx, stones, margin, gridSize, bw, bh, lastMove) {
    if (!stones) return;
    const r = gridSize * 0.47;
    for (let i = 0; i < stones.length; i++) {
      if (stones[i] === 0) continue;
      const x = Math.floor(i / bh);
      const y = i % bh;
      const cx = margin + x * gridSize;
      const cy = margin + y * gridSize;

      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      if (stones[i] === 1) {
        ctx.fillStyle = '#222';
        ctx.fill();
        ctx.strokeStyle = '#111';
      } else {
        ctx.fillStyle = '#eee';
        ctx.fill();
        ctx.strokeStyle = '#ccc';
      }
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Last move marker
    if (lastMove) {
      const lx = margin + lastMove[0] * gridSize;
      const ly = margin + lastMove[1] * gridSize;
      const stoneVal = stones[lastMove[0] * bh + lastMove[1]];
      ctx.beginPath();
      ctx.arc(lx, ly, r * 0.35, 0, Math.PI * 2);
      ctx.strokeStyle = stoneVal === 1 ? '#fff' : '#333';
      ctx.lineWidth = 2;
      ctx.stroke();
    }
  }
```

- [ ] **Step 3: Add candidate moves, heatmap, and variation rendering**

Continue in `board.js`:

```javascript
  const CANDIDATE_COLORS = [
    'rgba(0, 180, 80, 0.6)',  // best
    'rgba(80, 120, 255, 0.5)', // 2nd
    'rgba(200, 100, 50, 0.5)', // 3rd
    'rgba(150, 150, 150, 0.4)' // rest
  ];

  function drawCandidates(ctx, moves, margin, gridSize, bw, bh) {
    const r = gridSize * 0.42;
    for (const move of moves) {
      if (move.x == null || move.y == null) continue;
      const cx = margin + move.x * gridSize;
      const cy = margin + move.y * gridSize;
      const colorIdx = Math.min(move.order, CANDIDATE_COLORS.length - 1);

      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = CANDIDATE_COLORS[colorIdx];
      ctx.fill();
      ctx.strokeStyle = CANDIDATE_COLORS[colorIdx].replace(/[\d.]+\)$/, '0.9)');
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Winrate text
      ctx.fillStyle = '#fff';
      ctx.font = 'bold ' + (gridSize * 0.28) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(move.winrate.toFixed(1) + '%', cx, cy - gridSize * 0.06);

      // Score text
      if (move.scoreMean != null) {
        ctx.font = (gridSize * 0.22) + 'px sans-serif';
        const sign = move.scoreMean >= 0 ? '+' : '';
        ctx.fillText(sign + move.scoreMean.toFixed(1), cx, cy + gridSize * 0.18);
      }
    }
  }

  function drawHeatmap(ctx, estimate, margin, gridSize, bw, bh) {
    const r = gridSize * 0.48;
    for (let i = 0; i < estimate.length; i++) {
      const val = estimate[i];
      if (Math.abs(val) < 0.05) continue;
      const x = Math.floor(i / bh);
      const y = i % bh;
      const cx = margin + x * gridSize;
      const cy = margin + y * gridSize;
      const alpha = Math.abs(val) * 0.5;
      ctx.fillStyle = val > 0
        ? 'rgba(40, 120, 200, ' + alpha + ')'   // black territory = blue
        : 'rgba(200, 80, 40, ' + alpha + ')';    // white territory = red
      ctx.fillRect(cx - r, cy - r, r * 2, r * 2);
    }
  }

  function drawVariation(ctx, move, stones, margin, gridSize, bw, bh) {
    if (!move.variation || move.variation.length === 0) return;
    const r = gridSize * 0.43;
    // Determine who plays first in variation
    // The first move in variation is the candidate itself
    let isBlack = boardState.currentPlayer === 'B';

    for (let i = 0; i < move.variation.length; i++) {
      const coord = move.variation[i];
      const xy = gtpToXY(coord, bh);
      if (!xy) continue;
      const cx = margin + xy[0] * gridSize;
      const cy = margin + xy[1] * gridSize;

      ctx.globalAlpha = 0.7;
      ctx.beginPath();
      ctx.arc(cx, cy, r, 0, Math.PI * 2);
      ctx.fillStyle = isBlack ? '#333' : '#ddd';
      ctx.fill();
      ctx.strokeStyle = isBlack ? '#111' : '#aaa';
      ctx.lineWidth = 1;
      ctx.stroke();
      ctx.globalAlpha = 1.0;

      // Move number
      ctx.fillStyle = isBlack ? '#fff' : '#222';
      ctx.font = 'bold ' + (gridSize * 0.32) + 'px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(i + 1), cx, cy);

      isBlack = !isBlack;
    }
    ctx.textBaseline = 'alphabetic';
  }

  function gtpToXY(coord, boardHeight) {
    if (!coord || coord.length < 2) return null;
    let col = coord.charCodeAt(0);
    if (col >= 97) col -= 32; // lowercase to uppercase
    let x = col - 65; // 'A' = 0
    if (col > 73) x--; // skip 'I'
    const row = parseInt(coord.substring(1));
    if (isNaN(row)) return null;
    const y = boardHeight - row;
    return [x, y];
  }
```

- [ ] **Step 4: Add status bar update, winrate chart, and mouse interaction**

Continue in `board.js`:

```javascript
  function updateStatusBar() {
    if (!boardState) return;
    document.getElementById('move-number').textContent = '手数: ' + (boardState.moveNumber || 0);
    document.getElementById('current-player').textContent = boardState.currentPlayer === 'B' ? '⚫ 黑方' : '⚪ 白方';
    document.getElementById('winrate').textContent = '胜率: ' + (boardState.winrate || 0).toFixed(1) + '%';
    const sm = boardState.scoreMean || 0;
    document.getElementById('score-mean').textContent = '目差: ' + (sm >= 0 ? '+' : '') + sm.toFixed(1);
    document.getElementById('playouts').textContent = '计算: ' + formatPlayouts(boardState.playouts || 0);

    // Show/hide heatmap button
    const heatBtn = document.getElementById('toggle-heatmap');
    heatBtn.style.display = boardState.estimateArray ? '' : 'none';
  }

  function formatPlayouts(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return String(n);
  }

  // --- Winrate Chart ---
  function renderWinrateChart() {
    const canvas = document.getElementById('winrate-chart');
    const cctx = canvas.getContext('2d');
    const w = canvas.parentElement.clientWidth - 32;
    const h = Math.max(canvas.parentElement.clientHeight * 0.3, 60);
    canvas.width = w; canvas.height = h;

    cctx.fillStyle = '#111';
    cctx.fillRect(0, 0, w, h);

    if (winrateHistory.length < 2) return;

    const pad = 4;
    const plotW = w - 2 * pad;
    const plotH = h - 2 * pad;
    const n = winrateHistory.length;

    // 50% baseline
    cctx.strokeStyle = '#333';
    cctx.setLineDash([4, 4]);
    cctx.beginPath();
    cctx.moveTo(pad, pad + plotH / 2);
    cctx.lineTo(pad + plotW, pad + plotH / 2);
    cctx.stroke();
    cctx.setLineDash([]);

    // Winrate line
    cctx.strokeStyle = '#4CAF50';
    cctx.lineWidth = 1.5;
    cctx.beginPath();
    for (let i = 0; i < n; i++) {
      const x = pad + (i / (n - 1)) * plotW;
      const wr = showScoreCurve ? (50 + winrateHistory[i].scoreMean) : winrateHistory[i].winrate;
      const y = pad + (1 - wr / 100) * plotH;
      if (i === 0) cctx.moveTo(x, y); else cctx.lineTo(x, y);
    }
    cctx.stroke();
  }

  // --- Mouse Interaction ---
  boardCanvas.addEventListener('mousemove', function (e) {
    if (!boardState || !analysisData || !analysisData.bestMoves) {
      hoveredMove = null;
      return;
    }
    const rect = boardCanvas.getBoundingClientRect();
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;
    const bw = boardState.boardWidth || 19;
    const bh = boardState.boardHeight || 19;
    const size = boardCanvas.width;
    const margin = size * 0.04;
    const gridSize = (size - 2 * margin) / (bw - 1);

    hoveredMove = null;
    for (const move of analysisData.bestMoves) {
      if (move.x == null || move.y == null) continue;
      const cx = margin + move.x * gridSize;
      const cy = margin + move.y * gridSize;
      const dist = Math.sqrt((mx - cx) ** 2 + (my - cy) ** 2);
      if (dist < gridSize * 0.45) {
        hoveredMove = move;
        break;
      }
    }
    render();
  });

  boardCanvas.addEventListener('mouseleave', function () {
    hoveredMove = null;
    render();
  });

  // Mobile: long press
  let longPressTimer = null;
  boardCanvas.addEventListener('touchstart', function (e) {
    longPressTimer = setTimeout(function () {
      const touch = e.touches[0];
      const fakeEvent = { clientX: touch.clientX, clientY: touch.clientY };
      boardCanvas.dispatchEvent(new MouseEvent('mousemove', fakeEvent));
    }, 500);
  });
  boardCanvas.addEventListener('touchend', function () {
    clearTimeout(longPressTimer);
    hoveredMove = null;
    render();
  });

  // --- Controls ---
  document.getElementById('toggle-heatmap').addEventListener('click', function () {
    showHeatmap = !showHeatmap;
    this.classList.toggle('active', showHeatmap);
    render();
  });

  document.getElementById('toggle-score').addEventListener('click', function () {
    showScoreCurve = !showScoreCurve;
    this.classList.toggle('active', showScoreCurve);
    renderWinrateChart();
  });

  // --- Resize ---
  window.addEventListener('resize', function () { render(); renderWinrateChart(); });

  // --- Init ---
  connectWs();
})();
```

- [ ] **Step 5: Verify build includes web resources**

Run: `mvn -B -DskipTests package`
Then verify: `jar tf target/lizzie-yzy2.5.3.jar | grep web/`
Expected: `web/index.html`, `web/board.js`, `web/board.css` should all appear.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/board.js
git commit -m "feat(web): add frontend Canvas rendering, WebSocket client, and interactions"
```

---

## Task 9: Frontend WS Port Discovery

The frontend needs to know the WebSocket port. Rather than hardcoding port+1 convention, inject it via the HTML page.

**Files:**
- Modify: `src/main/java/featurecat/lizzie/gui/web/WebBoardHttpServer.java` — inject WS port into `index.html` before serving
- Modify: `src/main/resources/web/index.html` — add `<script>` placeholder for WS port
- Modify: `src/main/resources/web/board.js` — read port from `window.WS_PORT`

- [ ] **Step 1: Add port placeholder to `index.html`**

Add before the `board.js` script tag:

```html
<script>window.WS_PORT = __WS_PORT__;</script>
```

- [ ] **Step 2: Update `WebBoardHttpServer` to inject WS port**

Add a `wsPort` field to `WebBoardHttpServer`, set via constructor or setter. In `handleClient`, when serving `index.html`, replace `__WS_PORT__` with the actual port number before sending.

```java
private int wsPort;

public void setWsPort(int wsPort) { this.wsPort = wsPort; }

// In handleClient, after reading resource bytes:
if (resourcePath.endsWith("index.html")) {
  String html = new String(body, java.nio.charset.StandardCharsets.UTF_8);
  html = html.replace("__WS_PORT__", String.valueOf(wsPort));
  body = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
}
```

- [ ] **Step 3: Update `board.js` to use `window.WS_PORT`**

Replace the WS port calculation in `connectWs()`:

```javascript
const wsPort = window.WS_PORT || (parseInt(location.port) + 1);
```

- [ ] **Step 4: Update `WebBoardManager` to set WS port on HTTP server**

In `WebBoardManager.start()`, after both servers are started:

```java
httpServer.setWsPort(actualWsPort);
```

- [ ] **Step 5: Verify build, commit**

Run: `mvn -B -DskipTests package`
Expected: BUILD SUCCESS

```bash
git add src/main/java/featurecat/lizzie/gui/web/WebBoardHttpServer.java \
        src/main/java/featurecat/lizzie/gui/web/WebBoardManager.java \
        src/main/resources/web/index.html \
        src/main/resources/web/board.js
git commit -m "feat(web): inject WebSocket port into frontend via HTML template"
```

---

## Task 10: Integration Test and Manual Verification

**Files:**
- No new files. Verify the full pipeline works end-to-end.

- [ ] **Step 1: Run all tests**

Run: `mvn test`
Expected: All tests pass (existing + new)

- [ ] **Step 2: Build the fat jar**

Run: `mvn -B -DskipTests package`
Expected: BUILD SUCCESS with `target/lizzie-yzy2.5.3-shaded.jar`

- [ ] **Step 3: Verify web resources are in the jar**

Run: `jar tf target/lizzie-yzy2.5.3-shaded.jar | grep "^web/"`
Expected output:
```
web/index.html
web/board.js
web/board.css
```

- [ ] **Step 4: Manual smoke test** (requires running the app)

1. Launch: `java -jar target/lizzie-yzy2.5.3-shaded.jar`
2. Go to 同步 menu → Web 旁观 → 启动 Web 旁观
3. Open the displayed URL in a browser
4. Verify: board renders, candidates show, hovering shows variation
5. Open on a second device on the same network
6. Stop via menu → Web 旁观 → 停止

- [ ] **Step 5: Commit any final fixes**

```bash
git add -A
git commit -m "fix(web): integration fixes from manual testing"
```

---

## Summary

| Task | Description | Estimated Complexity |
|------|-------------|---------------------|
| 1 | WebBoardDataCollector — JSON serialization + throttling | Medium |
| 2 | WebBoardServer — WebSocket server with connection limits | Medium |
| 3 | WebBoardHttpServer — Static file server | Small |
| 4 | WebBoardManager — Lifecycle orchestrator | Small |
| 5 | Wire DataCollector to live Lizzie data | Medium |
| 6 | Menu integration + i18n | Small |
| 7 | Frontend HTML + CSS | Small |
| 8 | Frontend Canvas rendering (board.js) | Large |
| 9 | WS port discovery | Small |
| 10 | Integration test + manual verification | Medium |

Total: ~10 tasks, dependencies: 1→2→4→5→6, 3→4, 7→8→9, all→10
