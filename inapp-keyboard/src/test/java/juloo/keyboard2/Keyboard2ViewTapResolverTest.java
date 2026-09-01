package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

/** The host tap-resolver hook: consulted at press time for real touches only, told about releases. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewTapResolverTest
{
  private final FakeHandler handler = new FakeHandler();
  private final RecordingResolver resolver = new RecordingResolver();
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
    builder.marginTopPx = 0f;
    builder.hapticEnabled = false;
    builder.swipeDistancePx = 20f;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    // Two rows of two unit keys; the view is 200px wide so a key is 100px square.
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='a'/><key c='b'/></row>"
        + "<row><key c='c'/><key c='enter'/></row>"
        + "</keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.setTapResolver(resolver);
    handler.events.clear();
  }

  @Test
  public void geometryDescribesTheGridInKeyUnits()
  {
    touch(MotionEvent.ACTION_DOWN, 150f, 50f);
    TapGeometry g = resolver.lastGeometry;
    assertNotNull(g);
    assertEquals(4, g.keyCount);
    assertEquals(1f, g.left[1], 1e-4f);
    assertEquals(2f, g.right[1], 1e-4f);
    assertEquals(0f, g.top[1], 1e-4f);
    assertEquals(1f, g.bottom[1], 1e-4f);
    assertEquals(1, g.row[2]);
    assertTrue(g.isChar[0]);
    assertFalse(g.isChar[3]);
    assertEquals(1, resolver.lastRawIndex);
    assertEquals(1.5f, resolver.lastX, 1e-4f);
    assertEquals(0.5f, resolver.lastY, 1e-4f);
  }

  @Test
  public void resolverMovesThePressAndTheObservationKeepsTheRawKey()
  {
    resolver.answer = 0; // Every press on 'b' becomes 'a'.
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(Arrays.asList("down:a:false", "up:a"), handler.keyEvents());
    assertEquals(1, resolver.observedRawIndex);
    assertFalse(resolver.observedSwiped);
  }

  @Test
  public void returningTheRawIndexLeavesThePressAlone()
  {
    resolver.answer = -2; // Means "echo rawIndex".
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
  }

  @Test
  public void outOfRangeAnswerIsIgnored()
  {
    resolver.answer = 99;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
  }

  @Test
  public void aSwipeIsReportedAsSwiped()
  {
    touch(MotionEvent.ACTION_DOWN, 150f, 50f);
    touch(MotionEvent.ACTION_MOVE, 150f, 10f);
    touch(MotionEvent.ACTION_UP, 150f, 10f);
    assertEquals(1, resolver.observedRawIndex);
    assertTrue(resolver.observedSwiped);
  }

  @Test
  public void removingTheResolverRestoresTheStaticGrid()
  {
    resolver.answer = 0;
    view.setTapResolver(null);
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
    assertEquals(-1, resolver.observedRawIndex);
  }

  @Test
  public void paintModeNeverConsultsTheResolver()
  {
    List<String> painted = new ArrayList<String>();
    view.setOnKeyPaintListener(painted::add);
    resolver.answer = 0;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(Arrays.asList("0:1"), painted);
    assertEquals(-1, resolver.lastRawIndex);
  }

  private void touch(int action, float x, float y)
  {
    MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
    view.onTouch(view, event);
    event.recycle();
  }

  private static final class RecordingResolver implements Keyboard2View.TapResolver
  {
    /** -2 echoes the raw index; anything else is returned as is. */
    int answer = -2;
    TapGeometry lastGeometry;
    int lastRawIndex = -1;
    float lastX, lastY;
    int observedRawIndex = -1;
    boolean observedSwiped;

    @Override
    public int resolveTap(TapGeometry geometry, int rawIndex, float x, float y)
    {
      lastGeometry = geometry;
      lastRawIndex = rawIndex;
      lastX = x;
      lastY = y;
      return answer == -2 ? rawIndex : answer;
    }

    @Override
    public void observeTap(TapGeometry geometry, int rawIndex, float x, float y,
        boolean swiped)
    {
      observedRawIndex = rawIndex;
      observedSwiped = swiped;
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
