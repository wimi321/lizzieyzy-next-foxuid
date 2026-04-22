package featurecat.lizzie.analysis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ReadBoardFoxMoveNumberParsingTest {
  @Test
  void invalidFoxMoveNumberDoesNotThrowOrClearPendingMetadata() throws Exception {
    ReadBoard readBoard = allocate(ReadBoard.class);
    setField(
        readBoard,
        "pendingRemoteContext",
        SyncRemoteContext.forFoxLive(OptionalInt.of(18), "43581号", OptionalInt.empty(), false));

    assertDoesNotThrow(() -> readBoard.parseLine("foxMoveNumber nope"));

    SyncRemoteContext pendingRemoteContext =
        (SyncRemoteContext) getField(readBoard, "pendingRemoteContext");
    assertTrue(pendingRemoteContext.foxMoveNumber.isPresent());
    assertEquals(18, pendingRemoteContext.foxMoveNumber.getAsInt());
  }

  @Test
  void validFoxMoveNumberUpdatesPendingMetadata() throws Exception {
    ReadBoard readBoard = allocate(ReadBoard.class);

    readBoard.parseLine("foxMoveNumber 42");

    SyncRemoteContext pendingRemoteContext =
        (SyncRemoteContext) getField(readBoard, "pendingRemoteContext");
    assertTrue(pendingRemoteContext.foxMoveNumber.isPresent());
    assertEquals(42, pendingRemoteContext.foxMoveNumber.getAsInt());
  }

  @Test
  void recordAtEndFallsBackToTotalMoveForFoxRecovery() {
    SyncRemoteContext remoteContext =
        SyncRemoteContext.forFoxRecord(
            OptionalInt.of(256),
            OptionalInt.empty(),
            OptionalInt.of(256),
            true,
            "record-fingerprint",
            false);

    assertTrue(remoteContext.recoveryMoveNumber().isPresent());
    assertEquals(256, remoteContext.recoveryMoveNumber().getAsInt());
  }

  @SuppressWarnings("unchecked")
  private static <T> T allocate(Class<T> type) throws Exception {
    return (T) UnsafeHolder.UNSAFE.allocateInstance(type);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object getField(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
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
}
