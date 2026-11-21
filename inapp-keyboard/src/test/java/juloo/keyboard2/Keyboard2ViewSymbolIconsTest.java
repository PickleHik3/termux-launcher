package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * The host's symbol icons (SymbolIcons): a label the host answers for is drawn as its drawable,
 * tinted with the colour the label paint would have used; no host, or no answer, draws as text.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewSymbolIconsTest
{
  private static final String ENTER = KeyValue.getKeyByName("enter").getString();

  private Context context;
  private FakeIcons icons;

  @Before
  public void setUp() throws Exception
  {
    context = RuntimeEnvironment.getApplication();
    icons = new FakeIcons();
  }

  private Keyboard2View viewWith(SymbolIcons host, String layout) throws Exception
  {
    Config.Builder builder = new Config.Builder(context.getResources(), new NoopHandler());
    builder.hapticEnabled = false;
    builder.symbolIcons = host;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    Keyboard2View view = new Keyboard2View(context, builder.build(), palette);
    view.setKeyboard(KeyboardData.load_string_exn(layout));
    view.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    return view;
  }

  private static final String ENTER_KEY =
      "<keyboard bottom_row='false'><row><key c='enter' se='enter'/></row></keyboard>";

  private static void draw(Keyboard2View view)
  {
    Bitmap bitmap = Bitmap.createBitmap(Math.max(1, view.getWidth()),
        Math.max(1, view.getHeight()), Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(bitmap));
  }

  @Test
  public void aNullHostDrawsNoDrawableAndKeepsTheFontPath() throws Exception
  {
    Keyboard2View view = viewWith(null, ENTER_KEY);
    assertNull(view.symbolIcon(ENTER, true));
    assertEquals(0, view.symbolIconCacheSize());
    draw(view);
    assertEquals(ENTER, view.labelForDrawing(KeyValue.getKeyByName("enter")));
    assertEquals(LabelFace.KEY, view.labelFaceForDrawing(KeyValue.getKeyByName("enter")));
  }

  @Test
  public void aHostWithNoAnswerFallsBackToText() throws Exception
  {
    icons.answer = false;
    Keyboard2View view = viewWith(icons, ENTER_KEY);
    draw(view);
    assertTrue(icons.asked > 0);
    assertNull(view.symbolIcon(ENTER, true));
    assertEquals(0, icons.drawn.size());
  }

  @Test
  public void aThrowingHostFallsBackToText() throws Exception
  {
    icons.fail = true;
    Keyboard2View view = viewWith(icons, ENTER_KEY);
    draw(view);
    assertNull(view.symbolIcon(ENTER, true));
  }

  @Test
  public void aMainLabelAndACornerLabelAreDrawnAsTheDrawable() throws Exception
  {
    Keyboard2View view = viewWith(icons, ENTER_KEY);
    draw(view);
    // One cap, two slots: the centre and the south-east corner.
    assertEquals(2, icons.drawn.size());
    assertTrue(icons.keyFontAsked);
  }

  @Test
  public void theIconIsTintedWithTheColourTheLabelPaintWouldUse() throws Exception
  {
    Keyboard2View view = viewWith(icons,
        "<keyboard bottom_row='false'><row><key c='a' se='b'/></row></keyboard>");
    draw(view);
    Theme.Computed.Key tc = view.themeKeyForTest();
    // The main slot is not a modifier, not greyed: the plain label colour, alpha set by
    // the label brightness exactly as the label paint does.
    int expectedMain = tc.label_ink(tc.labelColor);
    int expectedSub = tc.label_ink(tc.subLabelColor);
    List<Integer> inks = icons.inks;
    assertTrue(inks + " main " + expectedMain + " sub " + expectedSub, inks.contains(expectedMain));
    assertTrue(inks.contains(expectedSub));
  }

  @Test
  public void theCacheIsBoundedAndClearedOnAThemeChange() throws Exception
  {
    Keyboard2View view = viewWith(icons, ENTER_KEY);
    for (int i = 0; i < 500; i++)
      view.symbolIcon("L" + i, false);
    assertTrue(view.symbolIconCacheSize() <= 96);
    int asked = icons.asked;
    view.symbolIcon("L499", false);
    assertEquals(asked, icons.asked);
    view.setPalette(new Theme.Palette(
        Color.WHITE, Color.LTGRAY, Color.LTGRAY, Color.LTGRAY,
        Color.GRAY, Color.BLACK, Color.DKGRAY, Color.BLUE, Color.BLACK,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f));
    assertEquals(0, view.symbolIconCacheSize());
  }

  @Test
  public void theHostIsAskedOncePerLabelAcrossDraws() throws Exception
  {
    Keyboard2View view = viewWith(icons, ENTER_KEY);
    draw(view);
    int asked = icons.asked;
    draw(view);
    draw(view);
    assertEquals(asked, icons.asked);
  }

  @Test
  public void aToolKeyAsksTheHostForItsLabel() throws Exception
  {
    icons.toolMap = "T";
    Keyboard2View view = viewWith(icons,
        "<keyboard bottom_row='false'><row><key c='a' n='tool:app.command_palette:x'/></row></keyboard>");
    assertEquals("T", view.labelForDrawing(KeyValue.getKeyByName("tool:app.command_palette:x")));
  }

  private static final class FakeIcons implements SymbolIcons
  {
    boolean answer = true;
    boolean fail;
    boolean keyFontAsked;
    int asked;
    String toolMap;
    final List<Integer> drawn = new ArrayList<>();
    final List<Integer> inks = new ArrayList<>();

    @Override public String toolLabel(String toolId, String symbol)
    {
      return toolMap == null ? symbol : toolMap;
    }

    @Override public Drawable iconFor(String label, boolean keyFont)
    {
      asked++;
      if (fail)
        throw new IllegalStateException("boom");
      if (!answer)
        return null;
      if (keyFont)
        keyFontAsked = true;
      return new Drawable()
      {
        private ColorFilter filter;
        @Override public void draw(Canvas canvas)
        {
          drawn.add(1);
          if (filter instanceof PorterDuffColorFilter)
          {
            try
            {
              inks.add((Integer)PorterDuffColorFilter.class.getMethod("getColor").invoke(filter));
            }
            catch (ReflectiveOperationException e)
            {
              throw new AssertionError(e);
            }
          }
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter f) { filter = f; }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
      };
    }
  }

  private static final class NoopHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers mods) {}
    @Override public void mods_changed(Pointers.Modifiers mods) {}
    @Override public void suggestion_entered(String text) {}
  }
}
