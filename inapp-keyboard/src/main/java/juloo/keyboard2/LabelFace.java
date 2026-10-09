package juloo.keyboard2;

/**
 * Which of the keyboard's three faces draws one label (local addition, no upstream counterpart).
 *
 * <p>{@link #KEY} is the bundled {@code special_font.ttf}, for values flagged
 * {@link KeyValue#FLAG_KEY_FONT}. Every other label is text, except one that carries a
 * private-use code point: those are Nerd Font icons (the space bar's window, session and palette
 * swipes, any {@code tool:<id>:<glyph>} key) that only the symbols font holds. A font the user
 * picks for labels almost never has them, so drawing them in it renders nothing. They get
 * {@link #SYMBOL}; the symbols face has no letters, so any text beside the icon falls through to
 * the system font.
 */
public enum LabelFace
{
  KEY, SYMBOL, TEXT;

  public static LabelFace of(boolean keyFont, CharSequence label)
  {
    if (keyFont)
      return KEY;
    return hasIconGlyph(label) ? SYMBOL : TEXT;
  }

  /** Whether [label] holds a code point from a private-use area (BMP, plane 15 or plane 16). */
  public static boolean hasIconGlyph(CharSequence label)
  {
    if (label == null)
      return false;
    for (int i = 0; i < label.length(); )
    {
      int cp = Character.codePointAt(label, i);
      if ((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)
        return true;
      i += Character.charCount(cp);
    }
    return false;
  }
}
