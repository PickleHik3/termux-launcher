package juloo.keyboard2;

import static org.junit.Assert.assertEquals;

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
 * Edge reach: a press on the keyboard's frame starts the outer key beside it, and a press between
 * two keys or past the frame still starts nothing.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewEdgeReachTest
{
  private final FakeHandler handler = new FakeHandler();
  private Keyboard2View view;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), handler);
    builder.rowHeightPx = 100f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = 10f;
    builder.marginTopPx = 8f;
    builder.bottomMarginPx = 12f;
    builder.hapticEnabled = false;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    // Three units wide in a 320px view with 10px side margins, so a unit is 100px.
    // Row 1, y 8..108: 'a' at x 10..110, a one-unit space, 'b' at x 210..310.
    // Row 2, y 108..208: half a unit of indent, 'c' at x 60..160, then a unit and a half of
    // nothing to the edge. Half a unit is how far a key reaches into its row's blank.
    // The bottom margin is y 208..220; the view is taller than that.
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='a'/><key shift='1' c='b'/></row>"
        + "<row><key shift='0.5' c='c'/></row>"
        + "</keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    handler.sent.clear();
  }

  @Test
  public void aPressInsideAKeyIsThatKey()
  {
    assertEquals("a", tap(60f, 58f));
    assertEquals("b", tap(260f, 58f));
    assertEquals("c", tap(110f, 158f));
  }

  @Test
  public void theSideMarginsBelongToTheOuterKeys()
  {
    assertEquals("a", tap(0f, 58f));
    assertEquals("a", tap(9f, 58f));
    assertEquals("b", tap(310f, 58f));
    assertEquals("b", tap(319f, 58f));
  }

  @Test
  public void aHalfKeyIndentAndItsMarginBelongToTheRowsOuterKey()
  {
    assertEquals("c", tap(3f, 158f));
    assertEquals("c", tap(59f, 158f));
    assertEquals("c", tap(160f, 158f));
    assertEquals("c", tap(209f, 158f));
  }

  @Test
  public void aBlankFurtherThanHalfAKeyFromTheOuterKeyStartsNothing()
  {
    assertEquals(null, tap(212f, 158f));
    assertEquals(null, tap(300f, 158f));
    // The margin beyond it is no nearer.
    assertEquals(null, tap(319f, 158f));
  }

  @Test
  public void theTopMarginBelongsToTheTopRow()
  {
    assertEquals("a", tap(60f, 0f));
    assertEquals("b", tap(260f, 7f));
    // The corner takes both steps.
    assertEquals("a", tap(2f, 2f));
  }

  @Test
  public void theBottomMarginBelongsToTheBottomRow()
  {
    assertEquals("c", tap(110f, 208f));
    assertEquals("c", tap(110f, 219f));
    assertEquals("c", tap(2f, 219f));
  }

  @Test
  public void spaceBetweenTwoKeysStartsNothing()
  {
    assertEquals(null, tap(160f, 58f));
    // Above it, in the top margin, too.
    assertEquals(null, tap(160f, 3f));
  }

  @Test
  public void pastTheBottomMarginStartsNothing()
  {
    assertEquals(null, tap(110f, 220f));
    assertEquals(null, tap(110f, 399f));
  }

  /** The key a tap at the point sent, or null when it sent nothing. */
  private String tap(float x, float y)
  {
    handler.sent.clear();
    touch(MotionEvent.ACTION_DOWN, x, y);
    touch(MotionEvent.ACTION_UP, x, y);
    assertEquals(handler.sent.toString(), true, handler.sent.size() <= 1);
    return handler.sent.isEmpty() ? null : handler.sent.get(0);
  }

  private void touch(int action, float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
    view.onTouch(view, event);
    event.recycle();
  }

  private static final class FakeHandler implements Config.IKeyEventHandler
  {
    final List<String> sent = new ArrayList<String>();

    @Override
    public void key_down(KeyValue value, boolean is_swipe)
    {
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers mods)
    {
      sent.add(value.getString());
    }

    @Override
    public void mods_changed(Pointers.Modifiers mods)
    {
    }

    @Override
    public void suggestion_entered(String text)
    {
    }
  }
}
