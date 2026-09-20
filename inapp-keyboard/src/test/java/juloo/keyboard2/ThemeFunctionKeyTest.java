package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Upstream's Action role covers both the real enter/editor-action key and every other
 * modifier/function key (shift, ctrl, backspace, arrows, layout switch, config — see
 * bottom_row.xml). The launcher's palette needs to tell them apart visually, so
 * {@link Theme.Computed} carries a separate {@code key_function} alongside {@code key_action};
 * see inapp-keyboard/UPSTREAM.md for the deviation.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class ThemeFunctionKeyTest
{
  @Test
  public void functionKeyUsesItsOwnBackgroundAndLabelDistinctFromActionAndNormal()
      throws Exception
  {
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
        Color.CYAN);             // functionLabelColor

    Context context = RuntimeEnvironment.getApplication();
    Theme theme = new Theme(context, palette);
    Config config = new Config(context.getResources(), new NoOpHandler());
    Theme.Computed computed = new Theme.Computed(theme, config, 100f,
        KeyboardData.load_string_exn("<keyboard><row><key c='a'/></row></keyboard>"), 50f);

    assertNotEquals("function key bg differs from letter key bg",
        computed.key.bg_paint.getColor(), computed.key_function.bg_paint.getColor());
    assertNotEquals("function key bg differs from the action/enter key bg",
        computed.key_action.bg_paint.getColor(), computed.key_function.bg_paint.getColor());
    assertEquals("function key bg is the palette's dedicated function color",
        palette.functionKeyBackground, computed.key_function.bg_paint.getColor());

    assertEquals("function key label is the palette's dedicated function label",
        palette.functionLabelColor, computed.key_function.labelColor);
    assertNotEquals("function key label differs from the letter label",
        computed.key.labelColor, computed.key_function.labelColor);
    assertNotEquals("function key label differs from the action/enter label",
        computed.key_action.labelColor, computed.key_function.labelColor);

    // The space bar keeps its own role slot but, per the new mapping, no longer borrows the
    // action role's label the way it used to.
    assertEquals("space bar label matches the plain letter label",
        computed.key.labelColor, computed.key_space_bar.labelColor);
  }

  private static final class NoOpHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
    @Override public void mods_changed(Pointers.Modifiers modifiers) {}
    @Override public void suggestion_entered(String text) {}
  }
}
