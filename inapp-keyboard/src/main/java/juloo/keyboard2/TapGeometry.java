package juloo.keyboard2;

import java.util.List;

/**
 * Snapshot of the rendered key grid in key-width units, handed to a host
 * {@link Keyboard2View.TapResolver}. Local addition, not upstream.
 *
 * <p>The origin is the top-left corner of the first row; x and y are both
 * divided by the rendered key width so a value of 1 is one standard key. The
 * snapshot is rebuilt whenever the layout or the measured geometry changes and
 * carries a {@link #signature} that identifies that geometry.
 */
public final class TapGeometry
{
  public final int keyCount;
  public final float[] left;
  public final float[] top;
  public final float[] right;
  public final float[] bottom;
  /** Row index of each key, top row first. */
  public final int[] row;
  /** Whether the key's centre value is a plain character. */
  public final boolean[] isChar;
  /** Identifies the layout and rendered geometry this snapshot describes. */
  public final String signature;
  /** Key objects by index, for the view to map an index back to a key. */
  final KeyboardData.Key[] keys;

  TapGeometry(int keyCount, float[] left, float[] top, float[] right,
      float[] bottom, int[] row, boolean[] isChar, String signature,
      KeyboardData.Key[] keys)
  {
    this.keyCount = keyCount;
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
    this.row = row;
    this.isChar = isChar;
    this.signature = signature;
    this.keys = keys;
  }

  /** Test and host-side constructor with no key objects. */
  public TapGeometry(float[] left, float[] top, float[] right, float[] bottom,
      int[] row, boolean[] isChar, String signature)
  {
    this(left.length, left, top, right, bottom, row, isChar, signature,
        new KeyboardData.Key[left.length]);
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
    boolean[] isChar = new boolean[n];
    KeyboardData.Key[] keys = new KeyboardData.Key[n];
    float rowHeight = rowHeightPx / keyWidthPx;
    float y = 0f;
    int i = 0;
    int hash = 17;
    List<KeyboardData.Row> rows = keyboard.rows;
    for (int ri = 0; ri < rows.size(); ri++)
    {
      KeyboardData.Row r = rows.get(ri);
      float rowTop = y + r.shift * rowHeight;
      float rowBottom = rowTop + r.height * rowHeight;
      float x = 0f;
      for (KeyboardData.Key k : r.keys)
      {
        float xLeft = x + k.shift;
        float xRight = xLeft + k.width;
        left[i] = xLeft; right[i] = xRight; top[i] = rowTop; bottom[i] = rowBottom;
        row[i] = ri;
        keys[i] = k;
        KeyValue kv = k.keys[0];
        isChar[i] = kv != null && kv.getKind() == KeyValue.Kind.Char;
        hash = hash * 31 + Float.floatToIntBits(xLeft);
        hash = hash * 31 + Float.floatToIntBits(xRight);
        hash = hash * 31 + Float.floatToIntBits(rowTop);
        hash = hash * 31 + (isChar[i] ? 1 : 0);
        x = xRight;
        i++;
      }
      y = rowBottom;
    }
    String signature = n + ":" + Math.round(keyWidthPx) + ":" + Math.round(rowHeightPx)
      + ":" + Integer.toHexString(hash);
    return new TapGeometry(n, left, top, right, bottom, row, isChar, signature, keys);
  }
}
