package juloo.keyboard2;

import android.view.KeyEvent;
import java.util.List;

/**
 * Snapshot of the rendered key grid in key-width units, handed to a host
 * {@link Keyboard2View.TapResolver}. Local addition, not upstream.
 *
 * <p>The origin is the top-left corner of the first row; x and y are both
 * divided by the rendered key width so a value of 1 is one standard key. The
 * snapshot is rebuilt whenever the layout or the measured geometry changes and
 * carries a {@link #signature} that identifies the layout's key structure
 * (rows, relative widths and heights, roles) but not its pixel size, so a
 * resize keeps the signature and an edit to the keys changes it.
 */
public final class TapGeometry
{
  /** A key the host never corrects: Enter, arrows, modifiers, actions. */
  public static final int ROLE_OTHER = 0;
  /** A plain character. */
  public static final int ROLE_CHAR = 1;
  public static final int ROLE_SPACE = 2;
  public static final int ROLE_ESC = 3;
  public static final int ROLE_TAB = 4;
  /** Never corrected, but the host reads it to recognise a take-back. */
  public static final int ROLE_BACKSPACE = 5;

  public final int keyCount;
  public final float[] left;
  public final float[] top;
  public final float[] right;
  public final float[] bottom;
  /** Row index of each key, top row first. */
  public final int[] row;
  /** What each key's centre value is, one of the [ROLE_*] constants. */
  public final int[] role;
  /**
   * Whether a tap may be corrected into or out of the key: characters, space,
   * Esc and Tab. Enter, Backspace, arrows and modifiers never are.
   */
  public final boolean[] correctable;
  /** Identifies the key structure this snapshot describes; never the pixel size. */
  public final String signature;
  /** Key objects by index, for the view to map an index back to a key. */
  final KeyboardData.Key[] keys;

  TapGeometry(int keyCount, float[] left, float[] top, float[] right,
      float[] bottom, int[] row, int[] role, String signature,
      KeyboardData.Key[] keys)
  {
    this.keyCount = keyCount;
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
    this.row = row;
    this.role = role;
    this.signature = signature;
    this.keys = keys;
    correctable = new boolean[keyCount];
    for (int i = 0; i < keyCount; i++)
      correctable[i] = isCorrectableRole(role[i]);
  }

  /** Test and host-side constructor with no key objects. */
  public TapGeometry(float[] left, float[] top, float[] right, float[] bottom,
      int[] row, int[] role, String signature)
  {
    this(left.length, left, top, right, bottom, row, role, signature,
        new KeyboardData.Key[left.length]);
  }

  /** Whether a key of [role] takes part in tap correction. */
  public static boolean isCorrectableRole(int role)
  {
    return role == ROLE_CHAR || role == ROLE_SPACE || role == ROLE_ESC
      || role == ROLE_TAB;
  }

  /** The role of a key whose centre value is [kv]. */
  static int roleOf(KeyValue kv)
  {
    if (kv == null)
      return ROLE_OTHER;
    switch (kv.getKind())
    {
      case Char:
        return kv.getChar() == ' ' ? ROLE_SPACE : ROLE_CHAR;
      case Editing:
        switch (kv.getEditing())
        {
          case SPACE_BAR: return ROLE_SPACE;
          case BACKSPACE: return ROLE_BACKSPACE;
          default: return ROLE_OTHER;
        }
      case Keyevent:
        switch (kv.getKeyevent())
        {
          case KeyEvent.KEYCODE_ESCAPE: return ROLE_ESC;
          case KeyEvent.KEYCODE_TAB: return ROLE_TAB;
          case KeyEvent.KEYCODE_DEL: return ROLE_BACKSPACE;
          default: return ROLE_OTHER;
        }
      default:
        return ROLE_OTHER;
    }
  }

  public float centerX(int i) { return (left[i] + right[i]) * 0.5f; }
  public float centerY(int i) { return (top[i] + bottom[i]) * 0.5f; }
  public float width(int i) { return right[i] - left[i]; }
  public float height(int i) { return bottom[i] - top[i]; }

  public boolean contains(int i, float x, float y)
  {
    return x >= left[i] && x < right[i] && y >= top[i] && y < bottom[i];
  }

  /** Index of [key], or -1. */
  int indexOf(KeyboardData.Key key)
  {
    for (int i = 0; i < keyCount; i++)
      if (keys[i] == key)
        return i;
    return -1;
  }

  /**
   * Builds the snapshot from a layout and its rendered metrics. Rows are
   * placed as [Keyboard2View] draws them: a row's shift is empty space above
   * its keys, a key's shift is empty space to its left.
   */
  static TapGeometry of(KeyboardData keyboard, float rowHeightPx, float keyWidthPx)
  {
    int n = 0;
    for (KeyboardData.Row r : keyboard.rows)
      n += r.keys.size();
    float[] left = new float[n], top = new float[n], right = new float[n],
      bottom = new float[n];
    int[] row = new int[n];
    int[] role = new int[n];
    KeyboardData.Key[] keys = new KeyboardData.Key[n];
    float rowHeight = rowHeightPx / keyWidthPx;
    float y = 0f;
    int i = 0;
    // Hashes only the layout's own units, never the pixel metrics, so the
    // signature survives a resize.
    long hash = 17;
    List<KeyboardData.Row> rows = keyboard.rows;
    for (int ri = 0; ri < rows.size(); ri++)
    {
      KeyboardData.Row r = rows.get(ri);
      float rowTop = y + r.shift * rowHeight;
      float rowBottom = rowTop + r.height * rowHeight;
      hash = hash * 31 + r.keys.size();
      hash = hash * 31 + Float.floatToIntBits(r.shift);
      hash = hash * 31 + Float.floatToIntBits(r.height);
      float x = 0f;
      for (KeyboardData.Key k : r.keys)
      {
        float xLeft = x + k.shift;
        float xRight = xLeft + k.width;
        left[i] = xLeft; right[i] = xRight; top[i] = rowTop; bottom[i] = rowBottom;
        row[i] = ri;
        keys[i] = k;
        role[i] = roleOf(k.keys[0]);
        hash = hash * 31 + Float.floatToIntBits(k.shift);
        hash = hash * 31 + Float.floatToIntBits(k.width);
        hash = hash * 31 + role[i];
        x = xRight;
        i++;
      }
      y = rowBottom;
    }
    String signature = n + ":" + rows.size() + ":" + Long.toHexString(hash);
    return new TapGeometry(n, left, top, right, bottom, row, role, signature, keys);
  }
}
