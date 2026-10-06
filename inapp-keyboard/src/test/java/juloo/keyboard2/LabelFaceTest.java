package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A picked label font must not swallow the Nerd Font icons on the space bar and tool keys: any
 * label holding a private-use code point is drawn in the symbols face, whatever the label font.
 */
public class LabelFaceTest
{
  /** The palette glyph on the space bar's north swipe (bottom_row.xml). */
  private static final String PALETTE_GLYPH = cp(0xF13B1);
  /** The previous-window glyph on the space bar's north-west swipe. */
  private static final String WINDOW_GLYPH = cp(0xF0733);

  private static String cp(int codePoint)
  {
    return new String(Character.toChars(codePoint));
  }

  @Test
  public void keyFontFlagWinsOverEverything()
  {
    assertEquals(LabelFace.KEY, LabelFace.of(true, cp(0xE00D)));
    assertEquals(LabelFace.KEY, LabelFace.of(true, "Esc"));
    assertEquals(LabelFace.KEY, LabelFace.of(true, PALETTE_GLYPH));
  }

  @Test
  public void nerdIconsTakeTheSymbolsFace()
  {
    assertEquals(LabelFace.SYMBOL, LabelFace.of(false, PALETTE_GLYPH));
    assertEquals(LabelFace.SYMBOL, LabelFace.of(false, WINDOW_GLYPH));
    assertEquals("BMP private use", LabelFace.SYMBOL, LabelFace.of(false, cp(0xF015)));
    assertEquals("icon beside text", LabelFace.SYMBOL, LabelFace.of(false, "Go " + WINDOW_GLYPH));
    assertEquals("plane 16", LabelFace.SYMBOL, LabelFace.of(false, cp(0x100000)));
  }

  @Test
  public void ordinaryLabelsKeepTheLabelFont()
  {
    assertEquals(LabelFace.TEXT, LabelFace.of(false, "a"));
    assertEquals(LabelFace.TEXT, LabelFace.of(false, "Esc"));
    assertEquals("command sign", LabelFace.TEXT, LabelFace.of(false, cp(0x2318)));
    assertEquals("emoji is not private use", LabelFace.TEXT, LabelFace.of(false, cp(0x1F600)));
    assertEquals(LabelFace.TEXT, LabelFace.of(false, ""));
    assertEquals(LabelFace.TEXT, LabelFace.of(false, null));
  }

  @Test
  public void privateUseBoundaries()
  {
    assertFalse(LabelFace.hasIconGlyph(cp(0xD7FF)));
    assertTrue(LabelFace.hasIconGlyph(cp(0xE000)));
    assertTrue(LabelFace.hasIconGlyph(cp(0xF8FF)));
    assertFalse(LabelFace.hasIconGlyph(cp(0xF900)));
    assertFalse(LabelFace.hasIconGlyph(cp(0xEFFFF)));
    assertTrue(LabelFace.hasIconGlyph(cp(0xF0000)));
    assertTrue(LabelFace.hasIconGlyph(cp(0x10FFFD)));
  }
}
