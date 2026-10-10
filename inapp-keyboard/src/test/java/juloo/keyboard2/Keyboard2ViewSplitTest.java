package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;

/** What the split keyboard type changes in the view: where its keys stand, and nothing else. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Keyboard2ViewSplitTest
{
  /** Four unit keys parted by one, measured 500px wide: keys of 100px and a gap of 200..300. */
  private static final String ROW =
      "<keyboard bottom_row='false'>"
      + "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row></keyboard>";

  /**
   * ROW over a three-key row that parts after its first key: halves of two, keys of 100px, the
   * band 200..300 on both rows and rows 50px tall.
   */
  private static final String STAGGERED =
      "<keyboard bottom_row='false'>"
      + "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>"
      + "<row><key c='e'/><key c='f'/><key c='g'/></row></keyboard>";

  private final FakeHandler handler = new FakeHandler();
  private Keyboard2View view;
  private int keyboardColor;

  @Before
  public void setUp()
  {
    view = newView(0f);
  }

  /** A view whose keys start [sideMarginPx] in from either edge. */
  private Keyboard2View newView(float sideMarginPx)
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), handler);
    builder.rowHeightPx = 50f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = sideMarginPx;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    builder.hapticEnabled = false;
    keyboardColor = Color.BLACK;
    Theme.Palette palette = new Theme.Palette(
        keyboardColor, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    return new Keyboard2View(context, builder.build(), palette);
  }

  @Test
  public void aPressInTheGapIsTheKeyboardsAndTypesNothing() throws Exception
  {
    split(1f);

    assertTrue("the parting is the keyboard's", touch(MotionEvent.ACTION_DOWN, 250f, 25f));
    touch(MotionEvent.ACTION_UP, 250f, 25f);
    assertTrue(handler.keys().isEmpty());
  }

  @Test
  public void bothHalvesKeepTyping() throws Exception
  {
    split(1f);

    assertTrue(touch(MotionEvent.ACTION_DOWN, 150f, 25f));
    touch(MotionEvent.ACTION_UP, 150f, 25f);
    assertTrue(touch(MotionEvent.ACTION_DOWN, 350f, 25f));
    touch(MotionEvent.ACTION_UP, 350f, 25f);

    assertEquals(Arrays.asList("down:b", "up:b", "down:c", "up:c"), handler.keys());
  }

  @Test
  public void theDockedKeyboardKeepsEveryPressAndItsOwnBackground() throws Exception
  {
    dock();

    // 250px is the third key of an unparted row, and there is no gap to fall through.
    assertTrue(touch(MotionEvent.ACTION_DOWN, 250f, 25f));
    touch(MotionEvent.ACTION_UP, 250f, 25f);

    assertEquals(Arrays.asList("down:c", "up:c"), handler.keys());
    assertEquals(keyboardColor, backgroundColor());
    assertEquals(0f, view.getSplitGapUnits(), 1e-4f);
    assertFalse(view.getSplitGapBounds(new Rect()));
    assertFalse(view.getSplitGapKeyBounds(new Rect()));
  }

  @Test
  public void theSplitKeyboardKeepsTheDockedBackground() throws Exception
  {
    split(1f);

    assertEquals(keyboardColor, backgroundColor());
  }

  @Test
  public void theBackgroundFillsTheParting() throws Exception
  {
    split(STAGGERED, 1f);

    Bitmap drawn = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
        Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(drawn));
    int middle = view.getHeight() / 2;
    assertEquals("the parting shows the keyboard, not what lies beneath it", keyboardColor,
        drawn.getPixel(250, middle));
    assertEquals(keyboardColor, drawn.getPixel(250, 0));
    assertEquals(keyboardColor, drawn.getPixel(250, view.getHeight() - 1));
  }

  @Test
  public void unpartingKeepsTheBackgroundAndGivesTheBandBackToItsKey() throws Exception
  {
    split(1f);
    view.setSplitGapUnits(0f);
    view.setKeyboard(KeyboardData.load_string_exn(ROW));
    measure();

    assertEquals(keyboardColor, backgroundColor());
    assertTrue(touch(MotionEvent.ACTION_DOWN, 250f, 25f));
    touch(MotionEvent.ACTION_UP, 250f, 25f);
    assertEquals(Arrays.asList("down:c", "up:c"), handler.keys());
  }

  @Test
  public void theGapBoundsAreTheBandTheRowsShare() throws Exception
  {
    split(1f);

    Rect gap = new Rect();
    assertTrue(view.getSplitGapBounds(gap));
    assertEquals(200, gap.left);
    assertEquals(300, gap.right);
    assertEquals(view.getHeight(), gap.height());
  }

  @Test
  public void aGapTooThinToSeeLeavesTheViewDocked() throws Exception
  {
    view.setSplitGapUnits(0.05f);
    view.setKeyboard(KeyboardData.load_string_exn(ROW));
    measure();

    assertEquals(0f, view.getSplitGapUnits(), 1e-4f);
    assertEquals(keyboardColor, backgroundColor());
  }

  @Test
  public void theContentWidthIsWhatTheKeysAreLaidOutAcross() throws Exception
  {
    assertEquals("nothing is known before the first measure",
        0f, view.getKeyContentWidthPx(), 1e-4f);

    split(1f);

    assertEquals(500f, view.getKeyContentWidthPx(), 1e-4f);
    // It is the parting's own denominator: the gap is a fraction of it, not of the key width.
    assertEquals(500f / 5f, view.getKeyContentWidthPx() / 5f, 1e-4f);
  }

  @Test
  public void aSeatInThePartingKeepsTheSpacingKeysKeepFromEachOther() throws Exception
  {
    view = newView(10f);
    view.setKeyCornerRadiusOverride(6f);
    assertEquals("nothing is known before the first measure",
        Color.TRANSPARENT, view.getKeyCapColor());
    split(1f);

    Rect a = keyRect("a");
    Rect b = keyRect("b");
    Rect c = keyRect("c");
    Rect seat = new Rect();
    assertTrue(view.getSplitGapKeyBounds(seat));
    // Two neighbouring caps stand a whole key margin apart; so does the seat from either run.
    int keySpacing = b.left - a.right;
    assertEquals(keySpacing, seat.left - b.right, 1);
    assertEquals(keySpacing, c.left - seat.right, 1);
    // And it spans the caps' own height, as one more key in the row.
    assertEquals(b.top, seat.top, 1);
    assertEquals(b.bottom, seat.bottom, 1);
    // Square caps of 6px with no stroke: the seat takes their corner and their fill.
    assertEquals(6f, view.getKeyCapRadiusPx(), 1e-3f);
    assertTrue(Color.alpha(view.getKeyCapColor()) > 0);
  }

  @Test
  public void aHostsAskIsTheStripBetweenTheKeyRuns() throws Exception
  {
    view = newView(10f);
    dock();
    int ask = 120;
    float units = LayoutModifier.gapUnitsForPx(KeyboardData.load_string_exn(ROW),
        view.getKeyContentWidthPx(), ask);
    split(units);

    Rect gap = new Rect();
    assertTrue(view.getSplitGapBounds(gap));
    assertTrue("the strip measures the ask: " + gap.width(), gap.width() >= ask);
    Rect b = keyRect("b");
    Rect c = keyRect("c");
    assertTrue("the strip starts past b's cap", gap.left >= b.right);
    assertTrue("and ends before c's", gap.right <= c.left);
    float middle = view.getHeight() / 2f;
    assertTrue("the strip is the keyboard's", touch(MotionEvent.ACTION_DOWN,
        gap.exactCenterX(), middle));
    touch(MotionEvent.ACTION_UP, gap.exactCenterX(), middle);
    assertTrue("and types nothing", handler.keys().isEmpty());
  }

  @Test
  public void staggeredRowsShareOneBandAndEveryPressBesideAKeyIsTheKeyboards()
      throws Exception
  {
    // The second row parts after e: its right run f g ends on the right edge, so it starts at
    // 300px and the band is 200..300 on both rows; 100..200 of that row holds no key either.
    split(STAGGERED, 1f);

    Rect gap = new Rect();
    assertTrue(view.getSplitGapBounds(gap));
    assertEquals(200, gap.left);
    assertEquals(300, gap.right);
    assertTrue(touch(MotionEvent.ACTION_DOWN, 250f, 75f));
    touch(MotionEvent.ACTION_UP, 250f, 75f);
    assertTrue(touch(MotionEvent.ACTION_DOWN, 150f, 75f));
    touch(MotionEvent.ACTION_UP, 150f, 75f);
    assertTrue(touch(MotionEvent.ACTION_DOWN, 350f, 75f));
    touch(MotionEvent.ACTION_UP, 350f, 75f);

    assertEquals(Arrays.asList("down:f", "up:f"), handler.keys());
  }

  private void dock() throws Exception
  {
    view.setKeyboard(KeyboardData.load_string_exn(ROW));
    measure();
  }

  private void split(float gapUnits) throws Exception
  {
    split(ROW, gapUnits);
  }

  private void split(String layout, float gapUnits) throws Exception
  {
    view.setSplitGapUnits(gapUnits);
    view.setKeyboard(LayoutModifier.split(
        KeyboardData.load_string_exn(layout), gapUnits));
    measure();
  }

  private Rect keyRect(String name)
  {
    Rect out = new Rect();
    assertTrue(name, view.getKeyRectOnScreen(name, out));
    return out;
  }

  private void measure()
  {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    handler.events.clear();
  }

  private int backgroundColor()
  {
    return ((ColorDrawable) view.getBackground()).getColor();
  }

  private boolean touch(int action, float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
    boolean handled = view.onTouch(view, event);
    event.recycle();
    return handled;
  }

  private static final class FakeHandler implements Config.IKeyEventHandler
  {
    final List<String> events = new ArrayList<String>();

    /** The key events alone, without the modifier bookkeeping around them. */
    List<String> keys()
    {
      List<String> out = new ArrayList<String>();
      for (String event : events)
        if (!event.startsWith("mods:"))
          out.add(event);
      return out;
    }

    @Override
    public void key_down(KeyValue value, boolean is_swipe)
    {
      events.add("down:" + value.getString());
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers mods)
    {
      events.add("up:" + value.getString());
    }

    @Override
    public void mods_changed(Pointers.Modifiers mods)
    {
      events.add("mods:" + mods.size());
    }

    @Override
    public void suggestion_entered(String text)
    {
      events.add("suggestion:" + text);
    }
  }
}
