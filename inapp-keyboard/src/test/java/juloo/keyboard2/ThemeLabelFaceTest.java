package juloo.keyboard2;

import static org.junit.Assert.assertSame;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * With a picked label font, the caps still draw Nerd Font icons in the symbols font: the space
 * bar's swipe glyphs went blank when every non-key-font label used the picked font.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class ThemeLabelFaceTest
{
  private static final String PALETTE_GLYPH = new String(Character.toChars(0xF13B1));

  @Test
  public void iconLabelsUseTheSymbolsFontAndTextUsesThePickedFont()
  {
    Typeface picked = Typeface.SERIF;
    Typeface symbols = Typeface.MONOSPACE;
    Theme.Computed.Key key = computedSpaceBar(picked, symbols);

    assertSame(symbols, key.sublabel_paint(false, PALETTE_GLYPH, Color.WHITE, 10f,
        Paint.Align.CENTER).getTypeface());
    assertSame(symbols, key.label_paint(false, PALETTE_GLYPH, Color.WHITE, 10f).getTypeface());
    assertSame(picked, key.label_paint(false, "a", Color.WHITE, 10f).getTypeface());
    assertSame(picked, key.sublabel_paint(false, "Esc", Color.WHITE, 10f,
        Paint.Align.LEFT).getTypeface());
  }

  @Test
  public void withoutASymbolsFontIconsFollowTheLabelFont()
  {
    Typeface picked = Typeface.SERIF;
    Theme.Computed.Key key = computedSpaceBar(picked, null);
    assertSame(picked, key.label_paint(false, PALETTE_GLYPH, Color.WHITE, 10f).getTypeface());
  }

  private static Theme.Computed.Key computedSpaceBar(Typeface labelFont, Typeface symbolFont)
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoOpHandler());
    builder.labelFont = labelFont;
    builder.symbolFont = symbolFont;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK,             // keyboardBackground
        0xFF222222,              // keyBackground (letters)
        0xFF3333FF,              // actionKeyBackground (enter)
        0xFF222222,              // spaceBarBackground
        0xFF888888,              // activatedKeyBackground
        Color.WHITE,             // labelColor (letters)
        Color.LTGRAY,            // subLabelColor
        Color.WHITE,             // activatedLabelColor
        Color.WHITE,             // pressedLabelColor
        Color.GREEN,             // lockedModifierColor
        Color.GRAY,              // borderColor
        false, 0f, 0f, 1f,       // no border, opacity
        0.25f, 0.5f,
        Color.YELLOW,            // actionLabelColor (onPrimary-ish)
        Color.YELLOW,            // actionSubLabelColor
        null,                    // indicatorColors
        0, 0,                    // gradient overlays
        0xFF444444,              // functionKeyBackground
        Color.CYAN);
    Theme theme = new Theme(context, palette);
    Theme.Computed computed = new Theme.Computed(theme, builder.build(), 100f,
        KeyboardData.load_string_exn("<keyboard><row><key c='a'/></row></keyboard>"), 50f);
    return computed.key_space_bar;
  }

  private static final class NoOpHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
    @Override public void mods_changed(Pointers.Modifiers modifiers) {}
    @Override public void suggestion_entered(String text) {}
  }
}
