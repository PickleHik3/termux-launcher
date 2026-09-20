package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Pointing at one key of the rendered layout.
 *
 * <p>The host glows keys it can only name — the first-boot tour walks Ctrl, Alt and the key a
 * chord ends on — so the probe has to find a key by its name, land on the drawn cap, and say
 * plainly when the layout in front of the user does not carry that key at all.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewKeyRectTest
{
  /** Two rows in the shape of the launcher's own bottom row, `loc` prefix included. */
  private static final String LAYOUT =
      "<keyboard bottom_row='false'>"
      + "<row><key width='1.5' c='shift'/><key c='c' ne='&lt;'/><key c='v'/></row>"
      + "<row><key width='1.7' c='ctrl'/><key width='1.3' c='loc alt'/>"
      + "<key width='4.0' c='space' sw='cursor_left'/><key width='1.7' c='enter'/></row>"
      + "</keyboard>";

  private static Keyboard2View measuredView(String layout) throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoOpHandler());
    builder.rowHeightPx = 50f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    Keyboard2View view = new Keyboard2View(context, builder.build(), palette());
    view.setKeyboard(KeyboardData.load_string_exn(layout));
    view.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));
    view.layout(0, 0, 1080, view.getMeasuredHeight());
    return view;
  }

  @Test
  public void everyKeyOfAChordCanBePointedAt() throws Exception
  {
    Keyboard2View view = measuredView(LAYOUT);
    for (String name : new String[] {"ctrl", "alt", "shift", "enter", "space", "c"})
    {
      Rect rect = new Rect();
      assertTrue("no rect for " + name, view.getKeyRectOnScreen(name, rect));
      assertTrue("empty rect for " + name, rect.width() > 0 && rect.height() > 0);
    }
  }

  @Test
  public void theLocPrefixIsNotPartOfTheKeysIdentity() throws Exception
  {
    // The bottom row spells Alt as "loc alt"; the host asks for "alt" and must still find it.
    Keyboard2View view = measuredView(LAYOUT);
    Rect rect = new Rect();
    assertTrue(view.getKeyRectOnScreen("alt", rect));
  }

  @Test
  public void keysOfTheSameRowAreSideBySideAndRowsAreStacked() throws Exception
  {
    Keyboard2View view = measuredView(LAYOUT);
    Rect ctrl = new Rect();
    Rect alt = new Rect();
    Rect shift = new Rect();
    view.getKeyRectOnScreen("ctrl", ctrl);
    view.getKeyRectOnScreen("alt", alt);
    view.getKeyRectOnScreen("shift", shift);
    assertTrue("ctrl sits left of alt", ctrl.right <= alt.left + 1);
    assertEquals("both are on the same row", ctrl.top, alt.top);
    assertTrue("shift is on the row above", shift.top < ctrl.top);
  }

  @Test
  public void aLayoutWithoutTheKeyAnswersNoRatherThanAWrongKey() throws Exception
  {
    // No shift row and no enter: both are ordinary for a user's own layout file.
    Keyboard2View view = measuredView(
        "<keyboard bottom_row='false'><row><key c='a'/><key c='space'/></row></keyboard>");
    Rect rect = new Rect();
    assertFalse(view.getKeyRectOnScreen("shift", rect));
    assertFalse(view.getKeyRectOnScreen("enter", rect));
    assertTrue(view.getKeyRectOnScreen("space", rect));
  }

  @Test
  public void aCornerValueIsASwipeNotTheKeyBeingNamed() throws Exception
  {
    // "v" is the centre of its own key and "<" is only a corner of the c key; asking for the
    // corner must not answer with the key it hangs off.
    Keyboard2View view = measuredView(LAYOUT);
    Rect c = new Rect();
    Rect v = new Rect();
    assertTrue(view.getKeyRectOnScreen("c", c));
    assertTrue(view.getKeyRectOnScreen("v", v));
    assertTrue("c and v are different keys", c.left != v.left);
    assertFalse(view.getKeyRectOnScreen("cursor_left", new Rect()));
  }

  @Test
  public void aCornerGlyphCanBePointedAtWhereItIsDrawn() throws Exception
  {
    // Help boxes the settings cog, which is only ever a corner of some other key.
    Keyboard2View view = measuredView(
        "<keyboard bottom_row='false'><row><key c='fn' nw='loc alt' se='config'/>"
        + "<key width='4.0' c='space'/></row></keyboard>");
    Rect cap = new Rect();
    Rect cog = new Rect();
    assertTrue(view.getKeyRectOnScreen("fn", cap));
    assertTrue(view.getKeyCornerRectOnScreen("config", cog));
    assertTrue("the cog has a glyph to box", cog.width() > 0 && cog.height() > 0);
    assertTrue("the cog is on its cap", cap.contains(cog));
    assertTrue("and smaller than it", cog.width() < cap.width() && cog.height() < cap.height());
    // South-east: the glyph sits in the half of the cap the swipe goes toward.
    assertTrue(cog.centerX() > cap.centerX());
    assertTrue(cog.centerY() > cap.centerY());
  }

  @Test
  public void aValueOnNoCornerHasNoGlyphToPointAt() throws Exception
  {
    Keyboard2View view = measuredView(LAYOUT);
    assertFalse(view.getKeyCornerRectOnScreen("config", new Rect()));
    // A centre cap is not a corner: asking for one by the corner probe answers no.
    assertFalse(view.getKeyCornerRectOnScreen("ctrl", new Rect()));
    assertTrue(view.getKeyCornerRectOnScreen("cursor_left", new Rect()));
  }

  @Test
  public void theSpaceBarAliasIsTheSameAnswerAsAskingForItByName() throws Exception
  {
    Keyboard2View view = measuredView(LAYOUT);
    Rect byAlias = new Rect();
    Rect byName = new Rect();
    assertTrue(view.getSpaceBarRectOnScreen(byAlias));
    assertTrue(view.getKeyRectOnScreen("space", byName));
    assertEquals(byName, byAlias);
  }

  @Test
  public void anUnmeasuredKeyboardHasNothingToPointAt()
  {
    Context context = RuntimeEnvironment.getApplication();
    Keyboard2View view = new Keyboard2View(context,
        new Config.Builder(context.getResources(), new NoOpHandler()).build(), palette());
    assertFalse(view.getKeyRectOnScreen("ctrl", new Rect()));
  }

  private static Theme.Palette palette()
  {
    return new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.GRAY, false, 0f, 0f, 1f);
  }

  private static final class NoOpHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
    @Override public void mods_changed(Pointers.Modifiers modifiers) {}
    @Override public void suggestion_entered(String text) {}
  }
}
