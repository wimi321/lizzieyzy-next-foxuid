package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.gui.LizzieFrame;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReadBoardStreamTest {
  @Test
  void streamKeepsUsingOwnerAfterFrameDetachesReadBoard() throws Exception {
    LizzieFrame previousFrame = Lizzie.frame;
    Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
    AtomicReference<Throwable> uncaughtFailure = new AtomicReference<>();
    TrackingReadBoard readBoard = allocate(TrackingReadBoard.class);
    readBoard.initialize();
    LizzieFrame frame = allocate(LizzieFrame.class);
    frame.readBoard = readBoard;
    Lizzie.frame = frame;

    try (ServerSocket serverSocket = new ServerSocket(0);
        Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket streamSocket = serverSocket.accept()) {
      Thread.setDefaultUncaughtExceptionHandler(
          (thread, throwable) -> uncaughtFailure.set(throwable));
      ReadBoardStream stream = new ReadBoardStream(readBoard, streamSocket);
      try {
        frame.readBoard = null;
        clientSocket.getOutputStream().write("noop\n".getBytes(StandardCharsets.UTF_8));
        clientSocket.getOutputStream().flush();

        assertTrue(
            readBoard.awaitParsedLine(), "socket reader should still dispatch to its owner.");
        assertEquals("noop", readBoard.lastParsedLine, "owner should receive the pending line.");
        assertNull(
            uncaughtFailure.get(), "detaching frame.readBoard should not crash the reader thread.");
      } finally {
        stream.close();
        stream.join(1000L);
        assertFalse(stream.isAlive(), "closing the reader should stop its thread.");
      }
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previousHandler);
      Lizzie.frame = previousFrame;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static final class UnsafeHolder {
    private static final sun.misc.Unsafe UNSAFE = loadUnsafe();

    private static sun.misc.Unsafe loadUnsafe() {
      try {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException("Failed to access Unsafe", ex);
      }
    }
  }

  private static final class TrackingReadBoard extends ReadBoard {
    private CountDownLatch parsedSignal;
    private String lastParsedLine;

    private TrackingReadBoard() throws Exception {
      super(true, true);
    }

    private void initialize() {
      parsedSignal = new CountDownLatch(1);
    }

    @Override
    public void parseLine(String line) {
      lastParsedLine = line.trim();
      parsedSignal.countDown();
    }

    private boolean awaitParsedLine() throws InterruptedException {
      return parsedSignal.await(2, TimeUnit.SECONDS);
    }
  }
}
