package juloo.keyboard2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Geometry of the split keyboard type: two rectangular halves against the edges, one straight
 * band between them on every row, on synthetic rows and on the launcher's own layout.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class LayoutModifierSplitTest
{
  private static final float EPS = 1e-4f;

  @Test
  public void anEvenRowPartsBetweenItsMiddleKeys() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    KeyboardData.Row row = split.rows.get(0);
    assertEquals("no key is cut on an even row", 4, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 1f, 0f }, shifts(row), EPS);
    assertArrayEquals(new float[]{ 1f, 1f, 1f, 1f }, widths(row), EPS);
    assertEquals("two halves of two and the gap", 5f, split.keysWidth, EPS);
    assertArrayEquals(new float[]{ 2f, 3f }, SplitLayout.gapBand(split, 1f), EPS);
  }

  @Test
  public void everyRowEndsOnTheRightEdgeAndLeavesTheSameBand() throws Exception
  {
    // The second row is staggered and marked to part before its sixth key, so its left run is
    // 5.5 wide and its right run 4: C is 5.5, the keyboard 2C+G = 12, and the band [5.5, 6.5].
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/><key c='e'/>"
        + "<key c='f'/><key c='g'/><key c='h'/><key c='i'/><key c='j'/></row>"
        + "<row><key shift='0.5' c='k'/><key c='l'/><key c='m'/><key c='n'/><key c='o'/>"
        + "<key c='p' split_before='true'/><key c='q'/><key c='r'/><key c='s'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    assertEquals(5.5f, SplitLayout.halfUnits(keyboard), EPS);
    assertEquals(12f, split.keysWidth, EPS);
    assertRectangular(split, 1f);
    assertArrayEquals(new float[]{ 5.5f, 6.5f }, SplitLayout.gapBand(split, 1f), EPS);
    // The left run keeps its stagger against the left edge; the right run is pushed until it
    // ends on the right edge.
    assertArrayEquals(new float[]{ 0f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f },
        shifts(split.rows.get(0)), EPS);
    assertArrayEquals(new float[]{ 0.5f, 0f, 0f, 0f, 0f, 2.5f, 0f, 0f, 0f },
        shifts(split.rows.get(1)), EPS);
  }

  @Test
  public void aSplitBeforeMarkerStartsTheRightHalfAtThatKey() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b' split_before='true'/><key c='c'/><key c='d'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    // One key left, three right: C is 3, the keyboard 7, and b starts at 4.
    assertArrayEquals(new float[]{ 0f, 3f, 0f, 0f }, shifts(row), EPS);
    assertEquals(7f, row.keysWidth, EPS);
  }

  @Test
  public void aSplitAtMarkerCutsTheKeyThereAndSharesItsSwipes() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key width='4' c='space' split_at='3'"
        + " nw='1' ne='2' sw='3' se='4' w='5' e='6' n='7' s='8'/><key c='b'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    KeyboardData.Row row = split.rows.get(0);
    // Left: a and three units of space, 4 wide; right: one unit of space and b, 2 wide.
    assertArrayEquals(new float[]{ 1f, 3f, 1f, 1f }, widths(row), EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 3f, 0f }, shifts(row), EPS);
    KeyboardData.Key bar = keyboard.rows.get(0).keys.get(1);
    KeyboardData.Key left = row.keys.get(1);
    KeyboardData.Key right = row.keys.get(2);
    assertEquals("both pieces type", bar.getKeyValue(0), left.getKeyValue(0));
    assertEquals("both pieces type", bar.getKeyValue(0), right.getKeyValue(0));
    // West on the left, east on the right, north and south on the wider piece.
    for (int i : new int[]{ 1, 3, 5, 7, 8 })
    {
      assertEquals("value " + i + " on the left", bar.getKeyValue(i), left.getKeyValue(i));
      assertNull("value " + i + " not on the right", right.getKeyValue(i));
    }
    for (int i : new int[]{ 2, 4, 6 })
    {
      assertEquals("value " + i + " on the right", bar.getKeyValue(i), right.getKeyValue(i));
      assertNull("value " + i + " not on the left", left.getKeyValue(i));
    }
  }

  @Test
  public void northAndSouthGoToTheWiderPieceOfACutBar() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key width='4' c='space' split_at='1' n='7' s='8'/>"
        + "<key c='b'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertNull(row.keys.get(1).getKeyValue(7));
    assertNotNull(row.keys.get(2).getKeyValue(7));
    assertNotNull(row.keys.get(2).getKeyValue(8));
  }

  @Test
  public void aSplitAtPastTheKeysRightEdgePartsAfterIt() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a' split_at='1'/><key c='b'/><key c='c'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals("no key is cut", 3, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 2f, 0f }, shifts(row), EPS);
  }

  @Test
  public void bothMarkersOnOneKeyAreRefused()
  {
    try
    {
      keyboard("<row><key c='a' split_before='true' split_at='0.5'/><key c='b'/></row>");
      fail("a key cannot be marked twice");
    }
    catch (Exception expected)
    {
    }
  }

  @Test
  public void theMarkerSurvivesTheKeyBeingRebuiltAndScales() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key width='4' c='space' split_at='2.5'/></row>");
    KeyboardData.Key bar = keyboard.rows.get(0).keys.get(1);

    assertEquals(2.5f, bar.splitAt, EPS);
    assertEquals(2.5f, bar.withKeyValue(1, KeyValue.getKeyByName("x")).splitAt, EPS);
    assertEquals(2.5f, bar.withWidthAndShift(3f, 1f).splitAt, EPS);
    assertEquals(1.25f, bar.scaleWidth(0.5f).splitAt, EPS);
    assertEquals(KeyboardData.Key.NO_SPLIT, keyboard.rows.get(0).keys.get(0).splitAt, EPS);
  }

  @Test
  public void aPartedLayoutCarriesNoMarkers() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b' split_before='true'/></row>");

    for (KeyboardData.Key key : LayoutModifier.split(keyboard, 1f).rows.get(0).keys)
      assertEquals(KeyboardData.Key.NO_SPLIT, key.splitAt, EPS);
  }

  @Test
  public void anUnmarkedBarOnTheMidpointIsCutInTwo() throws Exception
  {
    // Half of 8 is 4, dead centre of the 4-unit bar, which is wide enough to be cut.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key width='4' c='space' w='5' e='6' n='7'/>"
        + "<key c='c'/><key c='d'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(6, row.keys.size());
    assertArrayEquals(new float[]{ 1f, 1f, 2f, 2f, 1f, 1f }, widths(row), EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 0f, 1f, 0f, 0f }, shifts(row), EPS);
    // Equal pieces: north stays on the left one.
    assertNotNull(row.keys.get(2).getKeyValue(7));
    assertNull(row.keys.get(3).getKeyValue(7));
    assertNull(row.keys.get(2).getKeyValue(6));
    assertNull(row.keys.get(3).getKeyValue(5));
  }

  @Test
  public void anUnmarkedLetterKeyOnTheMidpointIsNeverCut() throws Exception
  {
    // Half of 9 is 4.5, dead centre of the fifth key, which is not a bar: the parting takes
    // its left edge, so four keys sit left and five right, and both halves are five wide.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/><key c='e'/>"
        + "<key c='f'/><key c='g'/><key c='h'/><key c='i'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals("no letter key is cut in two", 9, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f }, shifts(row), EPS);
    assertEquals(11f, row.keysWidth, EPS);
  }

  @Test
  public void aRowThatCannotPartStaysInTheLeftHalfClearOfTheBand() throws Exception
  {
    // The first row's midpoint lands in its first key's shift: there is nothing to put on the
    // left, so the row stays whole and the left half is as wide as it.
    KeyboardData keyboard = keyboard(
        "<row><key shift='3' c='a'/><key c='b'/></row>"
        + "<row><key c='c'/><key c='d'/><key c='e'/><key c='f'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    assertArrayEquals(new float[]{ 3f, 0f }, shifts(split.rows.get(0)), EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 7f, 0f }, shifts(split.rows.get(1)), EPS);
    assertArrayEquals(new float[]{ 5f, 6f }, SplitLayout.gapBand(split, 1f), EPS);
    assertRectangular(split, 1f);
  }

  @Test
  public void aLayoutWithNothingToPutRightIsLeftWhole() throws Exception
  {
    KeyboardData keyboard = keyboard("<row><key shift='3' c='a'/><key c='b'/></row>");

    assertSame(keyboard, LayoutModifier.split(keyboard, 1f));
  }

  @Test
  public void aGapTooThinToSeeLeavesTheLayoutAsItWas() throws Exception
  {
    KeyboardData keyboard = keyboard("<row><key c='a'/><key c='b'/></row>");

    assertSame(keyboard, LayoutModifier.split(keyboard, 0.05f));
    assertSame(keyboard, LayoutModifier.split(keyboard, 0f));
    assertSame(keyboard, LayoutModifier.split(keyboard, Float.NaN));
  }

  @Test
  public void anUnpartedLayoutHasNoBand() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    assertNull(SplitLayout.gapBand(keyboard, 1f));
    assertNull(SplitLayout.gapBand(LayoutModifier.split(keyboard, 1f), 0f));
  }

  @Test
  public void theGapIsAFractionOfTheTwoHalvesTogether() throws Exception
  {
    // Halves of 5 each: 25% of 10 is 2.5.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/>"
        + "<key c='e'/><key c='f'/><key c='g'/><key c='h'/>"
        + "<key c='i'/><key c='j'/></row>"
        + "<row><key width='5' c='k'/><key width='5' c='l'/></row>");

    float gap = LayoutModifier.gapUnits(keyboard, 0.25f);

    assertEquals(2.5f, gap, EPS);
    KeyboardData split = LayoutModifier.split(keyboard, gap);
    assertEquals(12.5f, split.keysWidth, EPS);
    assertArrayEquals(new float[]{ 0f, 2.5f }, shifts(split.rows.get(1)), EPS);
    assertEquals(0f, LayoutModifier.gapUnits(keyboard, 0f), EPS);
  }

  @Test
  public void partingLeavesEveryKeyOfTheLayoutTypable() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>"
        + "<row><key c='e'/><key width='2' c='space'/><key c='f'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1.2f);

    for (KeyboardData.Row row : keyboard.rows)
      for (KeyboardData.Key key : row.keys)
        assertTrue("key " + key.getKeyValue(0).getString() + " survived the parting",
            split.getKeys().containsKey(key.getKeyValue(0)));
  }

  @Test
  public void theLauncherLayoutPartsInTheThumbGroups()
  {
    KeyboardData composed = launcherLayout();
    float gap = LayoutModifier.gapUnits(composed, 0.25f);

    KeyboardData split = LayoutModifier.split(composed, gap);

    int rows = split.rows.size();
    assertEquals(composed.rows.size(), rows);
    assertRun(split.rows.get(rows - 4), "t", "y");
    assertRun(split.rows.get(rows - 3), "g", "h");
    assertRun(split.rows.get(rows - 2), "v", "b");
    assertRun(split.rows.get(rows - 1), "space", "space");
    // ctrl 1.7 + alt 1.3 + 2.5 of space on the left, 1.5 of space + compose 1.3 + enter 1.7
    // on the right: halves of 5.5, which the staggered rows also fill.
    assertEquals(5.5f, SplitLayout.halfUnits(composed), EPS);
    assertEquals(11f + gap, split.keysWidth, EPS);
    assertRectangular(split, gap);
    for (int i = 0; i < rows; i++)
      assertEquals("row " + i + " ends on the right edge",
          split.keysWidth, split.rows.get(i).keysWidth, EPS);
    // The space bar is the only key cut in two: no letter key is.
    assertEquals(composed.rows.get(rows - 1).keys.size() + 1,
        split.rows.get(rows - 1).keys.size());
    for (int i = 0; i < rows - 1; i++)
      assertEquals("row " + i + " keeps every key whole",
          composed.rows.get(i).keys.size(), split.rows.get(i).keys.size());
  }

  @Test
  public void theLauncherSpaceBarSharesItsGlyphsWithNoneDrawnTwice()
  {
    KeyboardData composed = launcherLayout();
    KeyboardData split = LayoutModifier.split(composed, LayoutModifier.gapUnits(composed, 0.25f));

    KeyboardData.Row docked = composed.rows.get(composed.rows.size() - 1);
    KeyboardData.Row parted = split.rows.get(split.rows.size() - 1);
    KeyboardData.Key bar = docked.keys.get(2);
    KeyboardData.Key left = parted.keys.get(2);
    KeyboardData.Key right = parted.keys.get(3);
    assertEquals(2.5f, left.width, EPS);
    assertEquals(1.5f, right.width, EPS);
    assertEquals(bar.getKeyValue(0), left.getKeyValue(0));
    assertEquals(bar.getKeyValue(0), right.getKeyValue(0));
    for (int i = 1; i < 9; i++)
    {
      if (bar.getKeyValue(i) == null)
        continue;
      boolean onLeft = left.getKeyValue(i) != null;
      boolean onRight = right.getKeyValue(i) != null;
      assertTrue("value " + i + " is on exactly one piece", onLeft != onRight);
      assertEquals(bar.getKeyValue(i), onLeft ? left.getKeyValue(i) : right.getKeyValue(i));
    }
    // cursor_left and the previous window/session stay left; their next ones go right.
    assertNotNull(left.getKeyValue(5));
    assertNotNull(right.getKeyValue(6));
    assertEquals("only the centre value is new", values(composed) + 1, values(split));
  }

  @Test
  public void aLayoutWithoutMarkersStillPartsIntoRectangularHalves() throws Exception
  {
    // The launcher layout's shape with no markers: every row parts at its midpoint.
    KeyboardData keyboard = keyboard(
        "<row><key c='q'/><key c='w'/><key c='e'/><key c='r'/><key c='t'/>"
        + "<key c='y'/><key c='u'/><key c='i'/><key c='o'/><key c='p'/></row>"
        + "<row><key shift='0.5' c='a'/><key c='s'/><key c='d'/><key c='f'/><key c='g'/>"
        + "<key c='h'/><key c='j'/><key c='k'/><key c='l'/></row>"
        + "<row><key width='1.5' c='shift'/><key c='z'/><key c='x'/><key c='c'/><key c='v'/>"
        + "<key c='b'/><key c='n'/><key c='m'/><key width='1.5' c='backspace'/></row>"
        + "<row><key width='1.7' c='ctrl'/><key width='1.3' c='alt'/>"
        + "<key width='4' c='space'/><key width='1.3' c='compose'/>"
        + "<key width='1.7' c='enter'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 2f);

    assertRectangular(split, 2f);
    for (KeyboardData.Row row : split.rows)
      assertEquals(split.keysWidth, row.keysWidth, EPS);
    assertNotNull(SplitLayout.gapBand(split, 2f));
    for (int i = 0; i < 3; i++)
      assertEquals(keyboard.rows.get(i).keys.size(), split.rows.get(i).keys.size());
    for (KeyboardData.Row row : split.rows)
      for (KeyboardData.Key key : row.keys)
        if (key.width <= 1f + EPS)
          assertEquals("letter keys keep their width", 1f, key.width, EPS);
  }

  @Test
  public void aPartingAskedForInPixelsMeasuresThatManyOnce()
      throws Exception
  {
    // Four unit keys over 500px of content: halves of 2, so a gap of g units widens the
    // keyboard to 4+g, 100px of parting wants 4*100/(500-100) = 1 unit, and one unit is then
    // 100px — all of it clear, the band being the parting itself.
    KeyboardData four = keyboard("<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    float units = LayoutModifier.gapUnitsForPx(four, 500f, 100f);

    assertEquals(1f, units, 1e-3f);
    KeyboardData split = LayoutModifier.split(four, units);
    float[] band = SplitLayout.gapBand(split, units);
    assertEquals(100f, (band[1] - band[0]) * 500f / split.keysWidth, 1e-3f);
  }

  @Test
  public void aPartingAskedForInPixelsCountsTheHalvesNotTheWidestRow() throws Exception
  {
    // Halves of 3 (one key left, three right), so the keyboard is 6+g wide.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b' split_before='true'/><key c='c'/><key c='d'/></row>");

    float units = LayoutModifier.gapUnitsForPx(keyboard, 1000f, 200f);

    KeyboardData split = LayoutModifier.split(keyboard, units);
    float[] band = SplitLayout.gapBand(split, units);
    assertEquals(200f, (band[1] - band[0]) * 1000f / split.keysWidth, 1e-2f);
  }

  @Test
  public void aPartingWiderThanTheHalvesCanSpareIsCappedAtHalfTheWidth()
      throws Exception
  {
    KeyboardData four = keyboard("<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    // 400 of 500px would leave 50px a side; the cap gives the parting half the width instead.
    float units = SplitLayout.gapUnitsForPx(four, 500f, 400f);

    assertEquals(4f, units, 1e-3f);
    assertEquals(500f * units / LayoutModifier.split(four, units).keysWidth, 250f, 1e-3f);
  }

  @Test
  public void aPartingNothingCanBeAskedOfIsNoParting() throws Exception
  {
    KeyboardData four = keyboard("<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    assertEquals(0f, SplitLayout.gapUnitsForPx(four, 0f, 100f), EPS);
    assertEquals(0f, SplitLayout.gapUnitsForPx(four, 500f, 0f), EPS);
    assertEquals(0f, SplitLayout.gapUnitsForPx(four, 500f, Float.NaN), EPS);
    assertEquals(0f, LayoutModifier.gapUnitsForPx(four, 500f, 0f), EPS);
  }

  /**
   * Every row of [split] is a left run inside [0, C] and a right run inside [C+G, 2C+G], so the
   * halves are rectangles and the band between them is straight.
   */
  private static void assertRectangular(KeyboardData split, float gap)
  {
    float half = (split.keysWidth - gap) / 2f;
    for (int r = 0; r < split.rows.size(); r++)
    {
      float x = 0f;
      for (KeyboardData.Key key : split.rows.get(r).keys)
      {
        float left = x + key.shift;
        x = left + key.width;
        assertFalse("row " + r + " has a key in the band [" + left + ", " + x + "]",
            left < half + gap - EPS && x > half + EPS);
      }
      assertTrue("row " + r + " stays inside the keyboard", x <= split.keysWidth + EPS);
    }
  }

  /** The left run of [row] ends on [lastLeft] and the right run starts with [firstRight]. */
  private static void assertRun(KeyboardData.Row row, String lastLeft, String firstRight)
  {
    int at = firstRightIndex(row);
    assertTrue("the row parts", at > 0 && at < row.keys.size());
    assertEquals(KeyValue.getKeyByName(lastLeft), row.keys.get(at - 1).getKeyValue(0));
    assertEquals(KeyValue.getKeyByName(firstRight), row.keys.get(at).getKeyValue(0));
  }

  /** The first key whose shift is the parting: the one that starts the right run. */
  private static int firstRightIndex(KeyboardData.Row row)
  {
    int best = -1;
    float widest = 0f;
    for (int i = 1; i < row.keys.size(); i++)
      if (row.keys.get(i).shift > widest)
      {
        widest = row.keys.get(i).shift;
        best = i;
      }
    return best;
  }

  private static int values(KeyboardData keyboard)
  {
    int count = 0;
    for (KeyboardData.Row row : keyboard.rows)
      for (KeyboardData.Key key : row.keys)
        for (int i = 0; i < 9; i++)
          if (key.getKeyValue(i) != null)
            count++;
    return count;
  }

  private static KeyboardData launcherLayout()
  {
    android.content.res.Resources resources =
        org.robolectric.RuntimeEnvironment.getApplication().getResources();
    return LayoutModifier.modify(
        KeyboardData.load(resources, R.xml.termux_launcher_qwerty),
        new LayoutModifier.LayoutOptions(true, false, true), resources);
  }

  private static KeyboardData keyboard(String rows) throws Exception
  {
    return KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>" + rows + "</keyboard>");
  }

  private static float[] shifts(KeyboardData.Row row)
  {
    float[] out = new float[row.keys.size()];
    for (int i = 0; i < out.length; i++)
      out[i] = row.keys.get(i).shift;
    return out;
  }

  private static float[] widths(KeyboardData.Row row)
  {
    float[] out = new float[row.keys.size()];
    for (int i = 0; i < out.length; i++)
      out[i] = row.keys.get(i).width;
    return out;
  }
}
