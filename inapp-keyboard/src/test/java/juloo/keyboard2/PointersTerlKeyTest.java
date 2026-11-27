package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * The Terl key, a north-east swipe on Ctrl (local addition, see UPSTREAM.md): swipe and let go
 * makes the next key a chord; swipe and hold reports the hold, a second finger fires a chord, and
 * letting go ends it without latching. A plain tap on Ctrl is still Ctrl.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 29)
public class PointersTerlKeyTest
{
  private static final long LONG_PRESS_MS = 300L;
  private final RecordingHandler handler = new RecordingHandler();
  private Pointers pointers;
  private KeyboardData.Key ctrl;
  private KeyboardData.Key letter;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoKeyEvents());
    builder.swipeDistancePx = 10f;
    builder.hapticEnabled = false;
    builder.longPressTimeoutMs = LONG_PRESS_MS;
    builder.keyRepeatEnabled = true;
    pointers = new Pointers(handler, builder.build());
    KeyboardData data = KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'><row>"
        + "<key c='ctrl' ne='terl'/>"
        + "<key c='a'/>"
        + "</row></keyboard>");
    ctrl = data.rows.get(0).keys.get(0);
    letter = data.rows.get(0).keys.get(1);
  }

  private void swipeToTerl(int pointerId)
  {
    pointers.onTouchDown(50f, 50f, pointerId, ctrl);
    pointers.onTouchMove(80f, 20f, pointerId);
  }

  private void tapLetter(int pointerId)
  {
    pointers.onTouchDown(150f, 50f, pointerId, letter);
    pointers.onTouchUp(pointerId);
  }

  @Test
  public void theTerlKeyIsCtrlsNorthEastCorner()
  {
    KeyValue terl = KeyValue.getKeyByName("terl");
    assertTrue(KeyValue.isTerlKey(terl));
    assertEquals(terl, ctrl.getKeyValue(2));
    assertFalse(KeyValue.isTerlKey(KeyValue.getKeyByName("ctrl")));
  }

  @Test
  public void swipeAndLetGoMakesTheNextKeyAChord()
  {
    swipeToTerl(0);
    pointers.onTouchUp(0);
    assertTrue(pointers.getModifiers().has(KeyValue.Modifier.TERL));

    tapLetter(1);
    assertEquals(Arrays.asList("a+terl"), handler.ups);
    assertFalse("one-shot", pointers.getModifiers().has(KeyValue.Modifier.TERL));
    assertTrue(handler.held.isEmpty());
  }

  @Test
  public void swipeAndHoldReportsTheHoldAndASecondFingerFiresAChord()
  {
    swipeToTerl(0);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONG_PRESS_MS + 50));
    assertEquals(Arrays.asList(true), handler.held);

    tapLetter(1);
    assertEquals(Arrays.asList("a+terl"), handler.ups);

    pointers.onTouchUp(0);
    assertEquals(Arrays.asList(true, false), handler.held);
    assertFalse("a hold never latches", pointers.getModifiers().has(KeyValue.Modifier.TERL));
  }

  @Test
  public void lettingGoOfAHoldWithoutAKeyLeavesNothingLatched()
  {
    swipeToTerl(0);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONG_PRESS_MS + 50));
    pointers.onTouchUp(0);
    assertEquals(Arrays.asList(true, false), handler.held);
    assertEquals(0, pointers.getModifiers().size());
  }

  @Test
  public void aPlainTapOnCtrlIsStillCtrl()
  {
    pointers.onTouchDown(50f, 50f, 0, ctrl);
    pointers.onTouchUp(0);
    assertTrue(pointers.getModifiers().has(KeyValue.Modifier.CTRL));
    assertFalse(pointers.getModifiers().has(KeyValue.Modifier.TERL));
  }

  /** A cancelled stream that drops the held Terl key ends the hold too. */
  @Test
  public void aDroppedHoldEndsAHold()
  {
    swipeToTerl(0);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONG_PRESS_MS + 50));
    pointers.onTouchCancelCommit(0f, 10000f);
    assertEquals(Arrays.asList(true, false), handler.held);
  }

  /** One Terl key chord runs its binding once: a held key under it does not repeat. */
  @Test
  public void aKeyHeldUnderTheTerlKeyDoesNotRepeat()
  {
    swipeToTerl(0);
    pointers.onTouchUp(0);
    pointers.onTouchDown(150f, 50f, 1, letter);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONG_PRESS_MS * 5));
    assertTrue(handler.holds.toString(), handler.holds.isEmpty());
    pointers.onTouchUp(1);
    assertEquals(Arrays.asList("a+terl"), handler.ups);
  }

  @Test
  public void aResetEndsAHold()
  {
    swipeToTerl(0);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONG_PRESS_MS + 50));
    pointers.onTouchCancel();
    assertEquals(Arrays.asList(true, false), handler.held);
  }

  private static final class RecordingHandler implements Pointers.IPointerEventHandler
  {
    final List<String> ups = new ArrayList<String>();
    final List<Boolean> held = new ArrayList<Boolean>();
    final List<String> holds = new ArrayList<String>();

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
      if (k.getKind() == KeyValue.Kind.Modifier)
        return;
      ups.add(k.getString() + (mods.has(KeyValue.Modifier.TERL) ? "+terl" : ""));
    }

    @Override
    public void onPointerFlagsChanged(boolean shouldVibrate) {}

    @Override
    public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
    {
      holds.add(k.getString());
    }

    @Override
    public void onLeaderHeld(boolean isHeld)
    {
      held.add(isHeld);
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
