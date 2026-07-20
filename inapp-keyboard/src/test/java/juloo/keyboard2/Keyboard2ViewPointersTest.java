package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewPointersTest
{
  private final FakeHandler handler = new FakeHandler();
  private Keyboard2View view;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), handler);
    builder.rowHeightPx = 50f;
    builder.maxKeyboardHeightFraction = 1f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    builder.hapticEnabled = false;
    builder.longPressTimeoutMs = 20L;
    builder.repeatIntervalMs = 10L;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'><row><key c='a'/></row></keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    handler.events.clear();
  }

  @Test
  public void forwardsPointerEventsToConfigHandlerInOrder()
  {
    touch(MotionEvent.ACTION_DOWN, 150f, 25f);
    touch(MotionEvent.ACTION_UP, 150f, 25f);

    assertEquals(Arrays.asList(
        "mods:1", "down:a:false", "up:a:0", "mods:0"), handler.events);
  }

  @Test
  public void resetCancelsPendingLongPressAndRepeatCallbacks()
  {
    touch(MotionEvent.ACTION_DOWN, 150f, 25f);
    view.resetInputState();
    handler.events.clear();

    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));

    assertTrue(handler.events.isEmpty());
  }

  private void touch(int action, float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
    view.onTouch(view, event);
    event.recycle();
  }

  private static final class FakeHandler implements Config.IKeyEventHandler
  {
    final List<String> events = new ArrayList<String>();

    @Override
    public void key_down(KeyValue value, boolean is_swipe)
    {
      events.add("down:" + value.getString() + ":" + is_swipe);
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers mods)
    {
      events.add("up:" + value.getString() + ":" + mods.size());
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
