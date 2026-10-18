package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

/**
 * The host tap-resolver hook: the raw key is held from finger-down, only a plain tap is offered
 * for correction on release, and every release is reported.
 */
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
    builder.longPressTimeoutMs = 20L;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    // Two rows of two unit keys; the view is 200px wide so a key is 100px square. 'b' has
    // '1' as its upward swipe.
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='a'/><key c='b' n='1'/></row>"
        + "<row><key c='space'/><key c='backspace'/></row>"
        + "</keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.setTapResolver(resolver);
    handler.events.clear();
  }

  @Test
  public void geometryDescribesTheGridInKeyUnitsAndRoles()
  {
    tap(150f, 50f);
    TapGeometry g = resolver.lastGeometry;
    assertNotNull(g);
    assertEquals(4, g.keyCount);
    assertEquals(1f, g.left[1], 1e-4f);
    assertEquals(2f, g.right[1], 1e-4f);
    assertEquals(0f, g.top[1], 1e-4f);
    assertEquals(1f, g.bottom[1], 1e-4f);
    assertEquals(1, g.row[2]);
    assertEquals(TapGeometry.ROLE_CHAR, g.role[0]);
    assertEquals(TapGeometry.ROLE_SPACE, g.role[2]);
    assertEquals(TapGeometry.ROLE_BACKSPACE, g.role[3]);
    assertTrue(g.correctable[2]);
    assertFalse(g.correctable[3]);
    assertEquals(1, resolver.lastRawIndex);
    assertEquals(1.5f, resolver.lastX, 1e-4f);
    assertEquals(0.5f, resolver.lastY, 1e-4f);
  }

  @Test
  public void nothingIsResolvedOnFingerDown()
  {
    resolver.answer = 0;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    assertNull(resolver.lastGeometry);
    assertEquals(Arrays.asList("down:b:false"), handler.keyEvents());
  }

  @Test
  public void aPlainTapIsSentAsTheResolvedKeyAndReportsBoth()
  {
    resolver.answer = 0; // Every plain tap on 'b' becomes 'a'.
    tap(110f, 50f);
    // The press held 'b' until release; 'a' is what was sent.
    assertEquals(Arrays.asList("down:b:false", "up:a"), handler.keyEvents());
    Keyboard2View.TapRelease r = resolver.lastRelease;
    assertEquals(1, r.rawIndex);
    assertEquals(0, r.committedIndex);
    assertTrue(r.plain);
    assertFalse(r.modified);
    assertTrue(r.upAtMs >= r.downAtMs);
  }

  @Test
  public void returningTheRawIndexLeavesThePressAlone()
  {
    tap(110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
    assertEquals(1, resolver.lastRelease.committedIndex);
  }

  @Test
  public void outOfRangeAnswerIsIgnored()
  {
    resolver.answer = 99;
    tap(110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
    assertEquals(1, resolver.lastRelease.committedIndex);
  }

  @Test
  public void aSwipeKeepsItsOwnKeysLegendAndIsNeverResolved()
  {
    resolver.answer = 0;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_MOVE, 110f, 10f);
    touch(MotionEvent.ACTION_UP, 110f, 10f);
    assertEquals(-1, resolver.lastRawIndex);
    assertTrue(handler.keyEvents().contains("up:1"));
    assertFalse(resolver.lastRelease.plain);
    assertEquals(1, resolver.lastRelease.rawIndex);
    assertEquals(1, resolver.lastRelease.committedIndex);
  }

  @Test
  public void aLongPressKeepsItsRawKeyAndIsNeverResolved()
  {
    resolver.answer = 0;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    touch(MotionEvent.ACTION_UP, 110f, 50f);
    assertEquals(-1, resolver.lastRawIndex);
    assertFalse(resolver.lastRelease.plain);
    assertFalse(handler.keyEvents().contains("up:a"));
  }

  @Test
  public void aTapWithCtrlHeldIsNeverResolved() throws Exception
  {
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='a'/><key c='b'/></row>"
        + "<row><key c='ctrl'/><key c='space'/></row>"
        + "</keyboard>"));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    view.setTapResolver(resolver);
    handler.events.clear();
    resolver.answer = 0;
    tap(50f, 150f); // Ctrl latches.
    tap(110f, 50f); // 'b' with Ctrl held.
    assertEquals(-1, resolver.lastRawIndex);
    assertTrue(resolver.lastRelease.plain);
    assertTrue(resolver.lastRelease.modified);
    assertEquals(1, resolver.lastRelease.committedIndex);
    assertFalse(handler.keyEvents().contains("up:a"));
  }

  @Test
  public void aCancelledPressIsReportedAsNotPlain()
  {
    resolver.answer = 0;
    touch(MotionEvent.ACTION_DOWN, 110f, 50f);
    touch(MotionEvent.ACTION_CANCEL, 110f, 50f);
    assertEquals(-1, resolver.lastRawIndex);
    assertFalse(resolver.lastRelease.plain);
  }

  @Test
  public void removingTheResolverRestoresTheStaticGrid()
  {
    resolver.answer = 0;
    view.setTapResolver(null);
    tap(110f, 50f);
    assertEquals(Arrays.asList("down:b:false", "up:b"), handler.keyEvents());
    assertNull(resolver.lastRelease);
  }

  @Test
  public void paintModeNeverConsultsTheResolver()
  {
    List<String> painted = new ArrayList<String>();
    view.setOnKeyPaintListener(painted::add);
    resolver.answer = 0;
    tap(110f, 50f);
    assertEquals(Arrays.asList("0:1"), painted);
    assertEquals(-1, resolver.lastRawIndex);
    assertNull(resolver.lastRelease);
  }

  private void tap(float x, float y)
  {
    touch(MotionEvent.ACTION_DOWN, x, y);
    touch(MotionEvent.ACTION_UP, x, y);
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
    Keyboard2View.TapRelease lastRelease;

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
    public void observeTap(TapGeometry geometry, Keyboard2View.TapRelease release)
    {
      lastRelease = release;
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
