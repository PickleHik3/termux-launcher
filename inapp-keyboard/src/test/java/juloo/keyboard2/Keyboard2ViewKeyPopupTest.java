package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * What the pressed-key popup is told, and when.
 *
 * <p>The popup must show the value the keyboard would actually commit, so it is driven by
 * {@link Pointers} rather than by a second reading of the touch stream. These tests hold that
 * line: a press, a swipe onto a corner, a key with no corners at all, two fingers at once, and a
 * modifier latching on release.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewKeyPopupTest
{
  /** A letter with three corners, a modifier, and a key carrying nothing but itself. */
  private static final String LAYOUT =
      "<keyboard bottom_row='false'><row>"
      + "<key c='a' ne='1' sw='esc' n='+'/>"
      + "<key c='shift'/>"
      + "<key c='z'/>"
      + "</row></keyboard>";
  private static final String GLYPH_LAYOUT =
      "<keyboard bottom_row='false'><row>"
      + "<key c='backspace' ne='delete'/>"
      + "<key c='space' n='switch_forward'/>"
      + "</row></keyboard>";

  private final Recorder popup = new Recorder();
  private final RecordingHandler keys = new RecordingHandler();
  private Keyboard2View view;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), keys);
    builder.rowHeightPx = 100f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    builder.hapticEnabled = false;
    builder.longPressTimeoutMs = 10000L;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    view.setKeyboard(KeyboardData.load_string_exn(LAYOUT));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.setKeyPopupListener(popup);
  }

  @Test
  public void glyphFontKeysReportAPopupToo() throws Exception
  {
    view.setKeyboard(KeyboardData.load_string_exn(GLYPH_LAYOUT));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    // backspace is the 1st of 2 keys across 600px: x in [0,300); space the 2nd: [300,600)
    down(150f, 50f, 0);
    assertEquals("backspace shows a popup", 1, popup.shown.size());
    Keyboard2View.KeyPopupInfo bs = popup.shown.get(0);
    assertNotNull(bs.label);
    assertTrue("the label is drawn in the key font", bs.labelKeyFont);
    assertNotNull("its corner value rides along", bs.ringLabels[2]);
    up(150f, 50f, 0);
    down(450f, 50f, 1);
    assertEquals("space shows a popup", 2, popup.shown.size());
    assertNotNull(popup.shown.get(1).label);
  }

  @Test
  public void aPressReportsTheKeyItsBoundsAndOnlyTheCornersThatAreConfigured()
  {
    down(50f, 50f, 0);

    assertEquals(1, popup.shown.size());
    Keyboard2View.KeyPopupInfo info = popup.shown.get(0);
    assertEquals("a", info.label);
    assertFalse("a plain letter latches nothing", info.latchable);
    assertTrue("the cap has been measured", info.keyBounds.width() > 0f);
    assertTrue("the popup is anchored on the pressed cap", info.keyBounds.contains(50f, 50f));
    // 2 is ne, 3 is sw, 7 is n; nothing else is configured on this key.
    assertEquals("1", info.ringLabels[2]);
    assertNotNull(info.ringLabels[3]);
    assertEquals("+", info.ringLabels[7]);
    for (int empty : new int[] {1, 4, 5, 6, 8})
      assertNull("corner " + empty, info.ringLabels[empty]);
  }

  @Test
  public void aSwipeTargetsTheCornerAndTheKeyThatIsSentIsTheOneShown()
  {
    down(50f, 50f, 0);
    popup.targets.clear();
    move(110f, -10f, 0); // north-east, well past the swipe distance

    assertFalse("the swipe was reported", popup.targets.isEmpty());
    Target target = popup.targets.get(popup.targets.size() - 1);
    assertEquals("1", target.label);
    assertEquals("north-east is corner 2", 2, target.slot);

    up(110f, -10f, 0);
    assertEquals("what the popup showed is what was typed",
        target.label, keys.lastCommitted);
  }

  @Test
  public void aKeyWithNoCornersShowsNoRingAndNoSwipeEverTargetsOne()
  {
    down(450f, 50f, 0); // the "z" cap

    Keyboard2View.KeyPopupInfo info = popup.shown.get(0);
    assertEquals("z", info.label);
    for (int corner = 1; corner < 9; corner++)
      assertNull("corner " + corner, info.ringLabels[corner]);

    move(520f, -20f, 0);
    assertTrue("there is nothing to swipe to", popup.targets.isEmpty());
    up(520f, -20f, 0);
    assertEquals("z", keys.lastCommitted);
  }

  @Test
  public void twoFingersGetTwoPopupsAndEachGoesOnItsOwn()
  {
    down(50f, 50f, 0);
    pointerDown(250f, 50f, 1);

    assertEquals(2, popup.shown.size());
    assertEquals("a", popup.shown.get(0).label);
    assertTrue("the second popup is the modifier's", popup.shown.get(1).latchable);

    up(50f, 50f, 0);
    assertEquals(Collections.singletonList(0), popup.hidden);
    up(250f, 50f, 1);
    assertEquals(Arrays.asList(0, 1), popup.hidden);
  }

  @Test
  public void aModifierReportsItsLatchBeforeTheFingerLeaves()
  {
    down(250f, 50f, 0);
    up(250f, 50f, 0);

    assertEquals(Collections.singletonList(Boolean.TRUE), popup.latches);
    assertEquals("and the popup still goes away", Collections.singletonList(0), popup.hidden);
  }

  @Test
  public void resettingTheKeyboardTakesEveryPopupWithIt()
  {
    down(50f, 50f, 0);
    pointerDown(450f, 50f, 1);
    assertEquals(0, popup.hideAlls);

    view.resetInputState();

    assertEquals(1, popup.hideAlls);
  }

  @Test
  public void removingTheListenerStopsTheReportsAndClearsWhatIsUp()
  {
    down(50f, 50f, 0);
    view.setKeyPopupListener(null);
    assertEquals(1, popup.hideAlls);

    popup.shown.clear();
    pointerDown(450f, 50f, 1);
    assertTrue(popup.shown.isEmpty());
  }

  // ------------------------------------------------------------------ touch

  private void down(float x, float y, int pointerId)
  {
    touch(MotionEvent.ACTION_DOWN, x, y, pointerId);
  }

  private void pointerDown(float x, float y, int pointerId)
  {
    touch(MotionEvent.ACTION_POINTER_DOWN, x, y, pointerId);
  }

  private void move(float x, float y, int pointerId)
  {
    touch(MotionEvent.ACTION_MOVE, x, y, pointerId);
  }

  private void up(float x, float y, int pointerId)
  {
    touch(MotionEvent.ACTION_UP, x, y, pointerId);
  }

  /** One event carrying exactly one pointer, so its id is the one under test. */
  private void touch(int action, float x, float y, int pointerId)
  {
    MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
    props.id = pointerId;
    props.toolType = MotionEvent.TOOL_TYPE_FINGER;
    MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
    coords.x = x;
    coords.y = y;
    coords.pressure = 1f;
    coords.size = 1f;
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, 1,
        new MotionEvent.PointerProperties[] {props},
        new MotionEvent.PointerCoords[] {coords}, 0, 0, 1f, 1f, 0, 0, 0, 0);
    view.onTouch(view, event);
    event.recycle();
  }

  private static final class Target
  {
    final int pointerId;
    final String label;
    final int slot;

    Target(int pointerId, String label, int slot)
    {
      this.pointerId = pointerId;
      this.label = label;
      this.slot = slot;
    }
  }

  private static final class Recorder implements Keyboard2View.KeyPopupListener
  {
    final List<Keyboard2View.KeyPopupInfo> shown = new ArrayList<>();
    final List<Target> targets = new ArrayList<>();
    final List<Boolean> latches = new ArrayList<>();
    final List<Integer> hidden = new ArrayList<>();
    int hideAlls;

    @Override public void onKeyPopupShow(int pointerId, Keyboard2View.KeyPopupInfo info)
    {
      shown.add(info);
    }

    @Override public void onKeyPopupTarget(int pointerId, String label, boolean keyFont, int slot)
    {
      targets.add(new Target(pointerId, label, slot));
    }

    @Override public void onKeyPopupLatch(int pointerId, boolean latched)
    {
      latches.add(latched);
    }

    @Override public void onKeyPopupHide(int pointerId) { hidden.add(pointerId); }

    @Override public void onKeyPopupHideAll() { hideAlls++; }
  }

  private static final class RecordingHandler implements Config.IKeyEventHandler
  {
    String lastCommitted;

    @Override public void key_down(KeyValue value, boolean isSwipe) {}

    @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers)
    {
      lastCommitted = value == null ? null : value.getString();
    }

    @Override public void mods_changed(Pointers.Modifiers modifiers) {}

    @Override public void suggestion_entered(String text) {}
  }
}
