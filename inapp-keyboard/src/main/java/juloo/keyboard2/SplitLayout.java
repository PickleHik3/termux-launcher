package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Objects;

/**
 * The split keyboard type: every row parted at its midpoint so each thumb keeps its own half.
 *
 * <p>Pure geometry — a {@link KeyboardData} in, a {@link KeyboardData} out, plus the band the
 * parting leaves empty for the renderer and for whatever the host wants to put in the gap. The
 * gap is expressed in key-width units and is the same on every row, so the two halves line up.
 * Local addition, see UPSTREAM.md: upstream's own split modifier was not ported.
 */
public final class SplitLayout
{
  /** A parting thinner than this is not worth having; the layout stays as it was parsed. */
  public static final float MIN_GAP_UNITS = 0.1f;

  /** Only a key at least this wide is a bar the parting can be cut through. */
  public static final float MIN_CUT_WIDTH_UNITS = 1.5f;

  /** A half thinner than this is not cut off even a bar; the parting takes its edge. */
  private static final float MIN_HALF_UNITS = 0.25f;

  /**
   * The parting never takes more than this much of the width a host asks it to fill: both
   * halves have to stay wide enough to type on, whatever is standing in the gap.
   */
  public static final float MAX_GAP_FRACTION = 0.5f;

  private static final float EPS = 1e-3f;

  private SplitLayout() {}

  /** The parting [fraction] of the keyboard's width asks for, in key-width units. */
  public static float gapUnits(KeyboardData keyboard, float fraction)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(fraction) || Float.isInfinite(fraction) || fraction <= 0f)
      return 0f;
    return keyboard.keysWidth * fraction;
  }

  /**
   * The parting, in key-width units, that measures [gapPx] across once [keyboard] has been
   * parted by it and laid out over [contentWidthPx] — what a host standing something in the
   * gap has to ask for.
   *
   * <p>Parting widens the keyboard by the gap, so the key width the gap is counted in shrinks
   * as the gap grows: over a content width <i>W</i> a parting of <i>g</i> units on a keyboard
   * of <i>K</i> units measures <i>W·g/(K+g)</i>, which inverts to <i>g = K·gapPx/(W−gapPx)</i>.
   * The ask is capped at {@link #MAX_GAP_FRACTION} of the width, so a keyboard too narrow to
   * give that many pixels returns the widest parting it can rather than swallowing its halves.
   * Zero when nothing can be parted. [keyboard] is the layout as parsed, never one already
   * parted, as with {@link #gapUnits}.
   */
  public static float gapUnitsForPx(KeyboardData keyboard, float contentWidthPx, float gapPx)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(gapPx) || Float.isNaN(contentWidthPx)
        || Float.isInfinite(gapPx) || Float.isInfinite(contentWidthPx)
        || gapPx <= 0f || contentWidthPx <= 0f)
      return 0f;
    float wanted = Math.min(gapPx, contentWidthPx * MAX_GAP_FRACTION);
    return keyboard.keysWidth * wanted / (contentWidthPx - wanted);
  }

  /**
   * The parting, in key-width units, whose <em>common band</em> — the strip
   * {@link #commonGap} reports, which is all of the parting every row leaves clear — measures
   * [gapPx] across. Rows part at a key boundary, so their partings are offset from one another
   * and the band is that much narrower than the parting; the offsets do not move with the gap,
   * so measuring them once and adding them back is exact. Falls back to {@link #gapUnitsForPx}
   * when the rows share no band at all, which the caller finds out for itself.
   */
  public static float commonGapUnitsForPx(KeyboardData keyboard, float contentWidthPx,
      float gapPx)
  {
    float base = gapUnitsForPx(keyboard, contentWidthPx, gapPx);
    if (base <= 0f)
      return 0f;
    float[] band = commonGap(split(keyboard, base), base);
    if (band == null)
      return base;
    float lost = base - (band[1] - band[0]);
    if (lost <= 0f)
      return base;
    // contentWidth * (g - lost) / (K + g) = gapPx, with K the unparted width, inverts to
    // g = (f·K + lost) / (1 − f). Capped as gapUnitsForPx is, so the halves keep their keys.
    float f = Math.min(gapPx, contentWidthPx * MAX_GAP_FRACTION) / contentWidthPx;
    float wanted = (f * keyboard.keysWidth + lost) / (1f - f);
    return Math.min(wanted, keyboard.keysWidth * MAX_GAP_FRACTION / (1f - MAX_GAP_FRACTION));
  }

  /**
   * Every row parted by [gapUnits] at its midpoint: the gap is added to the shift of the key
   * whose span crosses half the row. A key straddling the midpoint is cut into two keys of the
   * same value only when it is a bar — {@link #MIN_CUT_WIDTH_UNITS} wide or more, which is the
   * space bar and nothing else. A letter key keeps its shape and the parting snaps to whichever
   * of its edges is nearer, so the two halves may differ by a key. Returns [keyboard] itself
   * when there is nothing to part.
   */
  public static KeyboardData split(KeyboardData keyboard, float gapUnits)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(gapUnits) || gapUnits < MIN_GAP_UNITS)
      return keyboard;
    ArrayList<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>(keyboard.rows.size());
    boolean parted = false;
    for (KeyboardData.Row row : keyboard.rows)
    {
      KeyboardData.Row split = splitRow(row, gapUnits);
      parted |= split != row;
      rows.add(split);
    }
    return parted ? keyboard.with_rows(rows) : keyboard;
  }

  /** One row parted by [gapUnits]; the row itself when its midpoint is one of its ends. */
  public static KeyboardData.Row splitRow(KeyboardData.Row row, float gapUnits)
  {
    Objects.requireNonNull(row, "row");
    if (Float.isNaN(gapUnits) || gapUnits < MIN_GAP_UNITS || row.keys.size() < 2)
      return row;
    float half = row.keysWidth / 2f;
    // Index of the key that starts the right half, and the width the key at that index keeps
    // when the midpoint falls inside it and it is cut in two.
    int partAt = -1;
    float cutWidth = -1f;
    float x = 0f;
    for (int i = 0; i < row.keys.size(); i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      float left = x + key.shift;
      float right = left + key.width;
      if (half <= left + EPS)
      {
        partAt = i;
        break;
      }
      if (half < right - EPS)
      {
        if (key.width >= MIN_CUT_WIDTH_UNITS && half - left >= MIN_HALF_UNITS
            && right - half >= MIN_HALF_UNITS)
        {
          partAt = i;
          cutWidth = half - left;
        }
        else
          // Not a bar: the key stays whole and the parting takes its nearer edge, the left one
          // when the midpoint sits dead centre.
          partAt = half - left <= right - half ? i : i + 1;
        break;
      }
      x = right;
    }
    // A parting with nothing on its left is no parting: it would only pad the row.
    if (partAt < 0 || partAt >= row.keys.size() || (partAt == 0 && cutWidth <= 0f))
      return row;
    ArrayList<KeyboardData.Key> keys =
        new ArrayList<KeyboardData.Key>(row.keys.size() + 1);
    for (int i = 0; i < row.keys.size(); i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      if (i != partAt)
        keys.add(key);
      else if (cutWidth > 0f)
      {
        // Both halves carry every one of the key's nine values, so a swipe still works on
        // either of them.
        keys.add(key.withWidth(cutWidth));
        keys.add(key.withWidthAndShift(key.width - cutWidth, gapUnits));
      }
      else
        keys.add(key.withShift(key.shift + gapUnits));
    }
    return row.with_keys(keys);
  }

  /** Whether the parting sits on the left of [key], which therefore starts a run of keys. */
  public static boolean startsRun(KeyboardData.Key key, float gapUnits)
  {
    return gapUnits >= MIN_GAP_UNITS && key.shift >= gapUnits - EPS;
  }

  /**
   * The empty band nearest the centre of a parted [row], as {left, right} in key-width units, or
   * null when the row carries no parting.
   */
  public static float[] rowGap(KeyboardData.Row row, float gapUnits)
  {
    Objects.requireNonNull(row, "row");
    if (Float.isNaN(gapUnits) || gapUnits < MIN_GAP_UNITS)
      return null;
    float centre = row.keysWidth / 2f;
    float[] best = null;
    float bestDistance = Float.MAX_VALUE;
    float x = 0f;
    for (int i = 0; i < row.keys.size(); i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      float left = x + key.shift;
      if (i > 0 && startsRun(key, gapUnits))
      {
        float distance = Math.abs((left - gapUnits / 2f) - centre);
        if (distance < bestDistance)
        {
          bestDistance = distance;
          best = new float[]{ left - gapUnits, left };
        }
      }
      x = left + key.width;
    }
    return best;
  }

  /**
   * The band every row of a parted [keyboard] leaves empty, as {left, right} in key-width units.
   * Rows part at a key boundary, so their gaps are offset from one another by up to a key width;
   * what they share is what the host can safely stand something in. Null when they share nothing
   * or when any row is unparted.
   */
  public static float[] commonGap(KeyboardData keyboard, float gapUnits)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(gapUnits) || gapUnits < MIN_GAP_UNITS || keyboard.rows.isEmpty())
      return null;
    float left = Float.NEGATIVE_INFINITY;
    float right = Float.POSITIVE_INFINITY;
    for (KeyboardData.Row row : keyboard.rows)
    {
      float[] gap = rowGap(row, gapUnits);
      if (gap == null)
        return null;
      left = Math.max(left, gap[0]);
      right = Math.min(right, gap[1]);
    }
    return right - left < MIN_GAP_UNITS ? null : new float[]{ left, right };
  }
}
