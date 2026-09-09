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

  /** A half thinner than this is not cut off a straddling key; the parting takes its edge. */
  private static final float MIN_HALF_UNITS = 0.25f;

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
   * Every row parted by [gapUnits] at its midpoint: the gap is added to the shift of the key
   * whose span crosses half the row, and a key straddling the midpoint — the space bar — is cut
   * into two keys of the same value with the gap between them. Returns [keyboard] itself when
   * there is nothing to part.
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
        if (half - left < MIN_HALF_UNITS)
          partAt = i;
        else if (right - half < MIN_HALF_UNITS)
          partAt = i + 1;
        else
        {
          partAt = i;
          cutWidth = half - left;
        }
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
