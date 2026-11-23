package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * A cancelled stream commits a press only where the cancel is a denied back swipe (the side
 * strips); anywhere else the stream was taken by a gesture that is not the keyboard's, and a
 * cursor slide on the space bar must never type a space when the system or the host takes it.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 29)
public class PointersCancelTest
{
  private static final KeyValue SPACE = KeyValue.getKeyByName("space");
  private final RecordingHandler handler = new RecordingHandler();
  private Pointers pointers;
  private KeyboardData.Key letter;
  private KeyboardData.Key space;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(),
        new NoKeyEvents());
    builder.swipeDistancePx = 10f;
    builder.hapticEnabled = false;
    pointers = new Pointers(handler, builder.build());
    KeyboardData data = KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'><row>"
        + "<key c='a'/>"
        + "<key width='4' c='space' w='cursor_left' e='cursor_right'/>"
        + "</row></keyboard>");
    letter = data.rows.get(0).keys.get(0);
    space = data.rows.get(0).keys.get(1);
  }

  @Test
  public void aCancelledPressOutsideTheSideStripsTypesNothing()
  {
    pointers.onTouchDown(300f, 20f, 0, space);
    handler.ups.clear();

    pointers.onTouchCancelCommit(40f, 960f);

    assertTrue(handler.ups.isEmpty());
    assertTrue(pointers.getModifiers().size() == 0);
  }

  @Test
  public void aSpaceBarSlideTakenBeforeItSlidesTypesNoSpace()
  {
    // Quickstep takes a sideways drag at its own slop, which can come before the key's swipe
    // distance: the press is still centred when the cancel arrives.
    pointers.onTouchDown(300f, 20f, 0, space);
    pointers.onTouchMove(305f, 20f, 0);

    pointers.onTouchCancelCommit(40f, 960f);

    assertTrue(handler.ups.toString(), handler.ups.isEmpty());
  }

  @Test
  public void aSpaceBarSlideTakenWhileSlidingTypesNoSpace()
  {
    pointers.onTouchDown(300f, 20f, 0, space);
    pointers.onTouchMove(340f, 20f, 0);
    pointers.onTouchMove(420f, 20f, 0);

    pointers.onTouchCancelCommit(40f, 960f);

    assertTrue(handler.ups.toString(), !handler.ups.contains("space"));
  }

  @Test
  public void aPressInASideStripIsCommittedSoADeniedBackSwipeKeepsTheKey()
  {
    pointers.onTouchDown(20f, 20f, 0, letter);
    pointers.onTouchCancelCommit(40f, 960f);
    assertEquals(Arrays.asList("a"), handler.ups);

    handler.ups.clear();
    pointers.onTouchDown(980f, 20f, 1, letter);
    pointers.onTouchCancelCommit(40f, 960f);
    assertEquals(Arrays.asList("a"), handler.ups);
  }

  @Test
  public void aPlainReleaseStillTypesTheSpace()
  {
    pointers.onTouchDown(300f, 20f, 0, space);
    pointers.onTouchUp(0);
    assertEquals(Arrays.asList("space"), handler.ups);
  }

  private static final class RecordingHandler implements Pointers.IPointerEventHandler
  {
    final List<String> ups = new ArrayList<String>();

    @Override
    public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
    {
      return k;
    }

    @Override
    public void onPointerDown(KeyValue k, boolean isSwipe) {}

    @Override
    public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
    {
      ups.add(k.equals(SPACE) ? "space" : k.getString());
    }

    @Override
    public void onPointerFlagsChanged(boolean shouldVibrate) {}

    @Override
    public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
    {
      ups.add("hold:" + (k.equals(SPACE) ? "space" : k.getString()));
    }
  }

  private static final class NoKeyEvents implements Config.IKeyEventHandler
  {
    @Override
    public void key_down(KeyValue value, boolean is_swipe) {}

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers mods) {}

    @Override
    public void mods_changed(Pointers.Modifiers mods) {}

    @Override
    public void suggestion_entered(String text) {}
  }
}
