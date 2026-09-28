package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
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

/**
 * The top-edge hook: a press in the strip above the first row is offered to the host and, taken,
 * owns its stream; a press on a key is never offered, so key gestures are exactly what they were.
 * A press that hit no key and lifted in place is reported as a background tap.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewTopEdgeTest
{
  private final FakeHandler handler = new FakeHandler();
  private final RecordingDelegate delegate = new RecordingDelegate();
  private Keyboard2View view;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), handler);
    builder.rowHeightPx = 100f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    // Ten pixels above the first row hit no key; the caps are drawn half a key margin lower.
    builder.marginTopPx = 10f;
    builder.hapticEnabled = false;
    builder.swipeDistancePx = 20f;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='a'/><key c='b'/></row>"
        + "<row><key c='c'/><key c='enter'/></row>"
        + "</keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.setTopEdgeTouchDelegate(delegate);
    handler.events.clear();
  }

  @Test
  public void theBandIsTheNullStripAndTheCapsStartHalfAMarginLower()
  {
    assertEquals(10f, view.topEdgeNullBandPx(), 1e-4f);
    // vertical margin = 0.015 * 100px row, half of it above the cap.
    assertEquals(10.75f, view.topEdgeFirstCapTopPx(), 1e-4f);
  }

  @Test
  public void aPressAboveTheKeysGoesToTheDelegateWithItsWholeStream()
  {
    delegate.take = true;
    assertTrue(touch(MotionEvent.ACTION_DOWN, 50f, 5f));
    assertEquals(Arrays.asList("down:5.0:10.0:10.75"), delegate.log);
    touch(MotionEvent.ACTION_MOVE, 50f, 60f);
    touch(MotionEvent.ACTION_UP, 50f, 60f);
    assertEquals(Arrays.asList("down:5.0:10.0:10.75", "move", "up"), delegate.log);
    assertTrue(handler.keyEvents().isEmpty());
    // The stream ended: the next press is a fresh offer, not a continuation.
    touch(MotionEvent.ACTION_DOWN, 50f, 50f);
    assertEquals(Arrays.asList("down:a:false"), handler.keyEvents());
    assertEquals(3, delegate.log.size());
  }

  @Test
  public void aPressOnAKeyIsNeverOffered()
  {
    delegate.take = true;
    touch(MotionEvent.ACTION_DOWN, 50f, 50f);
    touch(MotionEvent.ACTION_UP, 50f, 50f);
    assertTrue(delegate.log.isEmpty());
    assertEquals(Arrays.asList("down:a:false", "up:a"), handler.keyEvents());
  }

  @Test
  public void aDeclinedPressInTheSlopStillPressesTheFirstRow()
  {
    delegate.take = false;
    touch(MotionEvent.ACTION_DOWN, 50f, 10.5f);
    touch(MotionEvent.ACTION_UP, 50f, 10.5f);
    assertEquals(Arrays.asList("down:10.5:10.0:10.75"), delegate.log);
    assertEquals(Arrays.asList("down:a:false", "up:a"), handler.keyEvents());
  }

  @Test
  public void aDeclinedPressInTheNullStripDoesNothingAsBefore()
  {
    delegate.take = false;
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_MOVE, 50f, 60f);
    touch(MotionEvent.ACTION_UP, 50f, 60f);
    assertEquals(1, delegate.log.size());
    assertTrue(handler.keyEvents().isEmpty());
  }

  @Test
  public void aDeclinedPressInTheStripReleasedInPlaceIsABackgroundTap()
  {
    delegate.take = false;
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_UP, 52f, 6f);
    assertEquals(Arrays.asList("down:5.0:10.0:10.75", "tap:52.0:6.0"), delegate.log);
    assertTrue(handler.keyEvents().isEmpty());
  }

  @Test
  public void aBackgroundPressThatMovedIsNoTap()
  {
    delegate.take = false;
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_MOVE, 50f, 40f);
    touch(MotionEvent.ACTION_UP, 50f, 40f);
    assertEquals(Arrays.asList("down:5.0:10.0:10.75"), delegate.log);
  }

  @Test
  public void aCancelledBackgroundPressIsNoTap()
  {
    delegate.take = false;
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_CANCEL, 50f, 5f);
    assertEquals(Arrays.asList("down:5.0:10.0:10.75"), delegate.log);
  }

  @Test
  public void withoutADelegateTheStripIsInert()
  {
    view.setTopEdgeTouchDelegate(null);
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_UP, 50f, 5f);
    assertTrue(delegate.log.isEmpty());
    assertTrue(handler.keyEvents().isEmpty());
  }

  @Test
  public void aCancelEndsTheDelegatesStream()
  {
    delegate.take = true;
    touch(MotionEvent.ACTION_DOWN, 50f, 5f);
    touch(MotionEvent.ACTION_CANCEL, 50f, 5f);
    assertEquals(Arrays.asList("down:5.0:10.0:10.75", "cancel"), delegate.log);
    touch(MotionEvent.ACTION_DOWN, 50f, 50f);
    assertEquals(Arrays.asList("down:a:false"), handler.keyEvents());
    assertFalse(delegate.log.size() > 2);
  }

  private boolean touch(int action, float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
    boolean handled = view.onTouch(view, event);
    event.recycle();
    return handled;
  }

  private static final class RecordingDelegate implements Keyboard2View.TopEdgeTouchDelegate
  {
    boolean take;
    final List<String> log = new ArrayList<String>();

    @Override
    public boolean onTopEdgeTouchDown(MotionEvent event, float nullBandPx, float firstCapTopPx)
    {
      log.add("down:" + event.getY() + ":" + nullBandPx + ":" + firstCapTopPx);
      return take;
    }

    @Override
    public void onTopEdgeTouchEvent(MotionEvent event)
    {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_MOVE: log.add("move"); break;
        case MotionEvent.ACTION_UP: log.add("up"); break;
        case MotionEvent.ACTION_CANCEL: log.add("cancel"); break;
        default: log.add("other"); break;
      }
    }

    @Override
    public void onBackgroundTap(float x, float y)
    {
      log.add("tap:" + x + ":" + y);
    }
  }

  private static final class FakeHandler implements Config.IKeyEventHandler
  {
    final List<String> events = new ArrayList<String>();

    List<String> keyEvents()
    {
      List<String> out = new ArrayList<String>();
      for (String e : events)
        if (!e.startsWith("mods:"))
          out.add(e);
      return out;
    }

    @Override
    public void key_down(KeyValue value, boolean is_swipe)
    {
      events.add("down:" + value.getString() + ":" + is_swipe);
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
