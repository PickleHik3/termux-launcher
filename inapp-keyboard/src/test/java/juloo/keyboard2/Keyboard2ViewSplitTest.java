package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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

/** What the split keyboard type changes in the view: its background and the gap's touches. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewSplitTest
{
  /** Four unit keys parted by one, measured 500px wide: keys of 100px and a gap of 200..300. */
  private static final String ROW =
      "<keyboard bottom_row='false'>"
      + "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row></keyboard>";

  private final FakeHandler handler = new FakeHandler();
  private Keyboard2View view;
  private int keyboardColor;

  @Before
  public void setUp()
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), handler);
    builder.rowHeightPx = 50f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    builder.hapticEnabled = false;
    keyboardColor = Color.BLACK;
    Theme.Palette palette = new Theme.Palette(
        keyboardColor, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
  }

  @Test
  public void aPressInTheGapIsRefusedSoItReachesWhatIsBeneath() throws Exception
  {
    split(1f);

    assertFalse("the gap is not the keyboard's", touch(MotionEvent.ACTION_DOWN, 250f, 25f));
    assertTrue(handler.events.isEmpty());
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
  }

  @Test
  public void theSplitKeyboardPaintsNoBackgroundOfItsOwn() throws Exception
  {
    split(1f);

    assertEquals(Color.TRANSPARENT, backgroundColor());
  }

  @Test
  public void unpartingRestoresTheDockedBackground() throws Exception
  {
    split(1f);
    view.setSplitGapUnits(0f);
    view.setKeyboard(KeyboardData.load_string_exn(ROW));

    assertEquals(keyboardColor, backgroundColor());
    assertTrue(touch(MotionEvent.ACTION_DOWN, 250f, 25f));
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
  public void theSlabRadiusIsTheShapeAPanelInThePartingTakes()
  {
    assertEquals(0f, Keyboard2View.splitSlabRadiusPx(), 1e-4f);
  }

  @Test
  public void theSlabsAreThePanelColourTheHostSet() throws Exception
  {
    split(1f);
    view.setSplitBackgroundColor(Color.MAGENTA);

    assertEquals(Color.MAGENTA, view.getSplitBackgroundColor());
    // The host's colour is the slabs' alone: the parting stays clear of it, so the view keeps
    // no background of its own and whatever the keyboard lies over still shows there.
    assertEquals(Color.TRANSPARENT, backgroundColor());
    Rect gap = new Rect();
    assertTrue(view.getSplitGapBounds(gap));
    assertEquals(200, gap.left);
    assertEquals(300, gap.right);
  }

  @Test
  public void withoutAHostColourTheSlabsKeepTheKeyboardsOwnBackground() throws Exception
  {
    split(1f);

    assertEquals(keyboardColor, view.getSplitBackgroundColor());
  }

  @Test
  public void clearingTheHostColourRestoresTheKeyboardsOwnBackground() throws Exception
  {
    split(1f);
    view.setSplitBackgroundColor(Color.MAGENTA);
    view.setSplitBackgroundColor(null);

    assertEquals(keyboardColor, view.getSplitBackgroundColor());
  }

  @Test
  public void theDockedKeyboardIgnoresTheHostColourEntirely() throws Exception
  {
    view.setSplitBackgroundColor(Color.MAGENTA);
    dock();

    // Nothing paints slabs at gap zero, and the view's own background is untouched by the
    // colour the host left set for the split it is not in.
    assertEquals(keyboardColor, backgroundColor());
  }

  private void dock() throws Exception
  {
    view.setKeyboard(KeyboardData.load_string_exn(ROW));
    measure();
  }

  private void split(float gapUnits) throws Exception
  {
    view.setSplitGapUnits(gapUnits);
    view.setKeyboard(LayoutModifier.split(
        KeyboardData.load_string_exn(ROW), gapUnits));
    measure();
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
