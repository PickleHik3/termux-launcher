package juloo.keyboard2;

import android.graphics.drawable.Drawable;

/**
 * Lets the host draw a key's symbol from a drawable instead of a font glyph (local addition, no
 * upstream counterpart; see UPSTREAM.md, "Symbol icons"). The keyboard module never depends on
 * the host's resources: it asks, and a null answer draws the label exactly as before.
 *
 * <p>Both methods run on the main thread, on the draw path: answer from a table, never allocate.
 */
public interface SymbolIcons
{
  /**
   * The label a launcher tool key draws. Return [symbol] unchanged when the host has no icon for
   * the tool; return the one-character label {@link #iconFor} resolves otherwise.
   */
  String toolLabel(String toolId, String symbol);

  /**
   * The drawable that draws [label], or null to draw it as text. [keyFont] is true when the label
   * is a glyph of the bundled key font (a code point that means something else elsewhere). The
   * result is cached by the view and tinted by it: return an instance that is safe to mutate.
   */
  Drawable iconFor(String label, boolean keyFont);
}
