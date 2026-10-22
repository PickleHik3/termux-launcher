package juloo.keyboard2;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PressFxModeTest
{
  @Test
  public void namesParseAndAnythingElseIsLegacy()
  {
    assertEquals(PressFxMode.Mode.OVERLAY, PressFxMode.parse("overlay"));
    assertEquals(PressFxMode.Mode.NOFADE, PressFxMode.parse("nofade"));
    assertEquals(PressFxMode.Mode.LEGACY, PressFxMode.parse("legacy"));
    assertEquals(PressFxMode.Mode.LEGACY, PressFxMode.parse(""));
    assertEquals(PressFxMode.Mode.LEGACY, PressFxMode.parse(null));
    assertEquals(PressFxMode.Mode.LEGACY, PressFxMode.parse("Overlay "));
  }

  @Test
  public void propertyIsReadAtMostOncePerSecond()
  {
    AtomicInteger reads = new AtomicInteger();
    String[] value = {"overlay"};
    PressFxMode mode = new PressFxMode(key ->
    {
      assertEquals(PressFxMode.PROPERTY, key);
      reads.incrementAndGet();
      return value[0];
    });
    assertEquals(PressFxMode.Mode.OVERLAY, mode.current(10_000L));
    value[0] = "nofade";
    assertEquals("cached inside the second", PressFxMode.Mode.OVERLAY, mode.current(10_900L));
    assertEquals(1, reads.get());
    assertEquals(PressFxMode.Mode.NOFADE, mode.current(11_000L));
    assertEquals(2, reads.get());
  }
}
