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
    setField(readBoard, "pendingFoxMoveNumber", OptionalInt.of(18));

    assertDoesNotThrow(() -> readBoard.parseLine("foxMoveNumber nope"));

    OptionalInt pendingFoxMoveNumber = (OptionalInt) getField(readBoard, "pendingFoxMoveNumber");
    assertTrue(pendingFoxMoveNumber.isPresent());
    assertEquals(18, pendingFoxMoveNumber.getAsInt());
  }

  @Test
  void validFoxMoveNumberUpdatesPendingMetadata() throws Exception {
    ReadBoard readBoard = allocate(ReadBoard.class);

    readBoard.parseLine("foxMoveNumber 42");

    OptionalInt pendingFoxMoveNumber = (OptionalInt) getField(readBoard, "pendingFoxMoveNumber");
    assertTrue(pendingFoxMoveNumber.isPresent());
    assertEquals(42, pendingFoxMoveNumber.getAsInt());
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
