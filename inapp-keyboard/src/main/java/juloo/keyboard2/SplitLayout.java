package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Objects;

/**
 * The split keyboard type: two rectangular halves, one against each edge, with a straight gap
 * of the same width on every row between them, the way a phone's own split keyboards look.
 *
 * <p>Pure geometry — a {@link KeyboardData} in, a {@link KeyboardData} out, plus the band the
 * parting leaves empty for the renderer and for whatever the host wants to put in the gap.
 *
 * <p>Every row is cut once into a left run and a right run. Where is the layout's to say: a key
 * marked {@code split_before="true"} starts the right run, and {@code split_at} cuts a key in two
 * at that offset (the space bar, usually). A row with no marker is cut at its midpoint, so a
 * user's own layout still parts. The halves are then equally wide: <i>C</i>, the widest run on
 * either side of any row. The keyboard is <i>2C+G</i> wide; every left run starts at the left
 * edge exactly as parsed, every right run is pushed right until it ends at <i>2C+G</i>, and the
 * band <i>[C, C+G]</i> is clear on every row. Keys keep their widths, so a letter is the same
 * size on every row of both halves; a run shorter than <i>C</i> leaves its slack on the gap side
 * of its half, which is still slab, not gap.
 *
 * <p>The gap is expressed in key-width units. Local addition, see UPSTREAM.md: upstream's own
 * split modifier was not ported.
 */
public final class SplitLayout
{
  /** A parting thinner than this is not worth having; the layout stays as it was parsed. */
  public static final float MIN_GAP_UNITS = 0.1f;

  /** Only a key at least this wide is a bar the midpoint rule can cut through. */
  public static final float MIN_CUT_WIDTH_UNITS = 1.5f;

  /** A piece thinner than this is not cut off a bar by the midpoint rule; it takes an edge. */
  private static final float MIN_HALF_UNITS = 0.25f;

  /**
   * The parting never takes more than this much of the width a host asks it to fill: both
   * halves have to stay wide enough to type on, whatever is standing in the gap.
   */
  public static final float MAX_GAP_FRACTION = 0.5f;

  /**
   * Values of a cut bar's left piece, as a {@link KeyboardData.Key#withValuesOnly} mask: the
   * centre value and the west-side swipes (nw 1, sw 3, w 5). The right piece takes the centre
   * value and the east-side ones (ne 2, se 4, e 6). North and south (7, 8) and the circle
   * gesture go to the wider piece, the left one when they are equal. So no swipe is drawn or
   * reachable twice; only the centre value is, because a space bar has to type on both thumbs.
   */
  private static final int WEST_VALUES = (1 << 0) | (1 << 1) | (1 << 3) | (1 << 5);
  private static final int EAST_VALUES = (1 << 0) | (1 << 2) | (1 << 4) | (1 << 6);
  private static final int VERTICAL_VALUES = (1 << 7) | (1 << 8);

  private static final float EPS = 1e-3f;

  private SplitLayout() {}

  /**
   * The width of one half, in key-width units, of [keyboard] once parted: the widest left or
   * right run of any row. [keyboard] is the layout as parsed, never one already parted. Zero
   * when no row has keys.
   */
  public static float halfUnits(KeyboardData keyboard)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    float half = 0f;
    for (KeyboardData.Row row : keyboard.rows)
    {
      Cut cut = cutOf(row);
      half = Math.max(half, Math.max(cut.leftUnits, cut.rightUnits));
    }
    return half;
  }

  /**
   * The parting [fraction] asks for, in key-width units: a fraction of the two halves' width
   * together, <i>fraction·2C</i>. [keyboard] is the layout as parsed.
   */
  public static float gapUnits(KeyboardData keyboard, float fraction)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(fraction) || Float.isInfinite(fraction) || fraction <= 0f)
      return 0f;
    return 2f * halfUnits(keyboard) * fraction;
  }

  /**
   * The parting, in key-width units, that measures [gapPx] across once [keyboard] has been
   * parted by it and laid out over [contentWidthPx] — what a host standing something in the
   * gap has to ask for.
   *
   * <p>Parting makes the keyboard <i>2C+G</i> units wide, so the key width the gap is counted in
   * shrinks as the gap grows: over a content width <i>W</i> a parting of <i>G</i> units measures
   * <i>W·G/(2C+G)</i>, which inverts to <i>G = 2C·gapPx/(W−gapPx)</i>. The band is the same on
   * every row, so that is the whole of it: nothing is lost to rows parting at different places.
   * The ask is capped at {@link #MAX_GAP_FRACTION} of the width, so a keyboard too narrow to
   * give that many pixels returns the widest parting it can rather than swallowing its halves.
   * Zero when nothing can be parted. [keyboard] is the layout as parsed, as with
   * {@link #gapUnits}.
   */
  public static float gapUnitsForPx(KeyboardData keyboard, float contentWidthPx, float gapPx)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(gapPx) || Float.isNaN(contentWidthPx)
        || Float.isInfinite(gapPx) || Float.isInfinite(contentWidthPx)
        || gapPx <= 0f || contentWidthPx <= 0f)
      return 0f;
    float wanted = Math.min(gapPx, contentWidthPx * MAX_GAP_FRACTION);
    return 2f * halfUnits(keyboard) * wanted / (contentWidthPx - wanted);
  }

  /**
   * [keyboard] parted by [gapUnits]: every row cut once (see the class comment), its left run
   * where it was and its right run moved to end at <i>2C+G</i>. A cut bar becomes two keys that
   * both type its centre value and share its swipes between them. Returns [keyboard] itself
   * when there is nothing to part: the gap is too thin, or no row has a right run.
   */
  public static KeyboardData split(KeyboardData keyboard, float gapUnits)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    if (Float.isNaN(gapUnits) || Float.isInfinite(gapUnits) || gapUnits < MIN_GAP_UNITS)
      return keyboard;
    Cut[] cuts = new Cut[keyboard.rows.size()];
    float half = 0f;
    boolean anyRight = false;
    for (int i = 0; i < cuts.length; i++)
    {
      Cut cut = cutOf(keyboard.rows.get(i));
      cuts[i] = cut;
      half = Math.max(half, Math.max(cut.leftUnits, cut.rightUnits));
      anyRight |= cut.rightUnits > 0f;
    }
    if (!anyRight)
      return keyboard;
    float total = 2f * half + gapUnits;
    ArrayList<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>(cuts.length);
    for (int i = 0; i < cuts.length; i++)
      rows.add(partRow(keyboard.rows.get(i), cuts[i], total));
    return keyboard.with_rows(rows);
  }

  /**
   * The band a keyboard {@link #split} by [gapUnits] leaves clear on every row, as {left, right}
   * in key-width units: <i>[C, C+G]</i>, read back from the parted width <i>2C+G</i>. Null when
   * [parted] carries no parting — the gap is too thin, or {@link #split} handed the layout back
   * whole, so a key stands in the band or none stands past it.
   */
  public static float[] gapBand(KeyboardData parted, float gapUnits)
  {
    Objects.requireNonNull(parted, "parted");
    if (Float.isNaN(gapUnits) || Float.isInfinite(gapUnits) || gapUnits < MIN_GAP_UNITS)
      return null;
    float half = (parted.keysWidth - gapUnits) / 2f;
    if (half <= 0f)
      return null;
    float right = half + gapUnits;
    boolean anyRight = false;
    for (KeyboardData.Row row : parted.rows)
    {
      float x = 0f;
      for (KeyboardData.Key key : row.keys)
      {
        float left = x + key.shift;
        x = left + key.width;
        if (left < right - EPS && x > half + EPS)
          return null;
        anyRight |= left >= right - EPS;
      }
    }
    return anyRight ? new float[]{ half, right } : null;
  }

  /**
   * Where a row is cut: [at] is the index of the key that starts the right run, and when
   * [leftPiece] is positive the key at [at] is cut in two, keeping that much of its width on
   * the left. [leftUnits] is the left run's width from the row's start, shifts included;
   * [rightUnits] the right run's from the left edge of its first key, whose own shift the gap
   * replaces. [at] equal to the key count is a row that stays whole in the left half.
   */
  private static final class Cut
  {
    final int at;
    final float leftPiece;
    final float leftUnits;
    final float rightUnits;

    Cut(KeyboardData.Row row, int at_, float leftPiece_)
    {
      at = at_;
      leftPiece = leftPiece_;
      float left = 0f;
      float right = 0f;
      for (int i = 0; i < row.keys.size(); i++)
      {
        KeyboardData.Key key = row.keys.get(i);
        if (i < at)
          left += key.shift + key.width;
        else if (i == at && leftPiece > 0f)
        {
          left += key.shift + leftPiece;
          right += key.width - leftPiece;
        }
        else if (i == at)
          right += key.width;
        else
          right += key.shift + key.width;
      }
      leftUnits = left;
      rightUnits = right;
    }
  }

  /** The row's cut: the first key carrying a split marker, else the midpoint rule. */
  private static Cut cutOf(KeyboardData.Row row)
  {
    int n = row.keys.size();
    for (int i = 0; i < n; i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      if (key.splitAt == KeyboardData.Key.NO_SPLIT)
        continue;
      // A marker is the layout's word and is taken as given, even on a letter key; an offset at
      // or past the key's right edge parts the row just after it.
      if (key.splitAt <= EPS)
        return new Cut(row, i, 0f);
      if (key.splitAt >= key.width - EPS)
        return new Cut(row, i + 1, 0f);
      return new Cut(row, i, key.splitAt);
    }
    return midpointCut(row);
  }

  /**
   * The cut of a row whose layout marks none: at the key edge nearest half the row. A key
   * straddling the midpoint is cut in two only when it is a bar, {@link #MIN_CUT_WIDTH_UNITS}
   * wide or more; a letter key keeps its shape and the cut takes whichever of its edges is
   * nearer, the left one when the midpoint is dead centre. A row that cannot be cut with
   * something on its left — a single narrow key, or a midpoint inside the first key's shift —
   * stays whole in the left half.
   */
  private static Cut midpointCut(KeyboardData.Row row)
  {
    int n = row.keys.size();
    float half = row.keysWidth / 2f;
    float x = 0f;
    for (int i = 0; i < n; i++)
    {
      KeyboardData.Key key = row.keys.get(i);
      float left = x + key.shift;
      float right = left + key.width;
      if (half <= left + EPS)
        return new Cut(row, i == 0 ? n : i, 0f);
      if (half < right - EPS)
      {
        if (key.width >= MIN_CUT_WIDTH_UNITS && half - left >= MIN_HALF_UNITS
            && right - half >= MIN_HALF_UNITS)
          return new Cut(row, i, half - left);
        int at = half - left <= right - half ? i : i + 1;
        return new Cut(row, at == 0 ? n : at, 0f);
      }
      x = right;
    }
    return new Cut(row, n, 0f);
  }

  /** [row] laid out by [cut] across a parted keyboard [total] units wide. */
  private static KeyboardData.Row partRow(KeyboardData.Row row, Cut cut, float total)
  {
    int n = row.keys.size();
    if (n == 0)
      return row;
    // The right run starts wherever it has to for its last key to end on the right edge; the
    // shift of its first key is whatever lies between there and the end of the left run.
    float rightShift = total - cut.rightUnits - cut.leftUnits;
    ArrayList<KeyboardData.Key> keys = new ArrayList<KeyboardData.Key>(n + 1);
    for (int i = 0; i < n; i++)
    {
      // The marker has done its work; a parted layout carries none, so it cannot part twice.
      KeyboardData.Key key = row.keys.get(i).withSplitAt(KeyboardData.Key.NO_SPLIT);
      if (i != cut.at)
        keys.add(key);
      else if (cut.leftPiece > 0f)
      {
        float rightPiece = key.width - cut.leftPiece;
        boolean verticalLeft = cut.leftPiece >= rightPiece - EPS;
        keys.add(key.withValuesOnly(WEST_VALUES | (verticalLeft ? VERTICAL_VALUES : 0),
            verticalLeft).withWidth(cut.leftPiece));
        keys.add(key.withValuesOnly(EAST_VALUES | (verticalLeft ? 0 : VERTICAL_VALUES),
            !verticalLeft).withWidthAndShift(rightPiece, rightShift));
      }
      else
        keys.add(key.withShift(rightShift));
    }
    return row.with_keys(keys);
  }
}
