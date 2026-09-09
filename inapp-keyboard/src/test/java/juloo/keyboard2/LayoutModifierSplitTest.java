package juloo.keyboard2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Geometry of the split keyboard type, on synthetic rows. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class LayoutModifierSplitTest
{
  private static final float EPS = 1e-4f;

  @Test
  public void gapIsAddedToTheKeyThatCrossesHalfTheRow() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    KeyboardData.Row row = split.rows.get(0);
    assertEquals("no key is cut on an even row", 4, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 1f, 0f }, shifts(row), EPS);
    assertArrayEquals(new float[]{ 1f, 1f, 1f, 1f }, widths(row), EPS);
    assertEquals("the row grew by exactly the gap", 5f, row.keysWidth, EPS);
    assertEquals(5f, split.keysWidth, EPS);
  }

  @Test
  public void everyRowPartsByTheSameGapWhateverItsKeyWidths() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>"
        + "<row><key width='0.5' c='e'/><key width='1.5' c='f'/>"
        + "<key width='1.5' c='g'/><key width='0.5' c='h'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 0.8f);

    for (KeyboardData.Row row : split.rows)
      assertEquals(4.8f, row.keysWidth, EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 0.8f, 0f }, shifts(split.rows.get(1)), EPS);
  }

  @Test
  public void aKeyStraddlingTheMidpointIsCutInTwoWithTheGapBetween() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key width='4' c='space' key1='esc'/><key c='b'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    KeyboardData.Row row = split.rows.get(0);
    assertEquals(4, row.keys.size());
    assertArrayEquals(new float[]{ 1f, 2f, 2f, 1f }, widths(row), EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 1f, 0f }, shifts(row), EPS);
    // Both halves are the same key: the same centre value and the same swipe.
    assertEquals(keyboard.rows.get(0).keys.get(1).getKeyValue(0),
        row.keys.get(1).getKeyValue(0));
    assertEquals(row.keys.get(1).getKeyValue(0), row.keys.get(2).getKeyValue(0));
    assertNotNull(row.keys.get(1).getKeyValue(1));
    assertEquals(row.keys.get(1).getKeyValue(1), row.keys.get(2).getKeyValue(1));
  }

  @Test
  public void aLetterKeyOnTheMidpointIsNeverCutAndTheHalvesDifferByOne() throws Exception
  {
    // Half of 9 is 4.5, dead centre of the fifth key: the halves cannot both have four and a
    // half keys, and a letter key is not a bar, so the parting takes its left edge.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/><key c='e'/>"
        + "<key c='f'/><key c='g'/><key c='h'/><key c='i'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals("no letter key is cut in two", 9, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f }, shifts(row), EPS);
    assertArrayEquals(new float[]{ 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f }, widths(row), EPS);
  }

  @Test
  public void aBarOnTheMidpointOfThatSameRowIsCutInTwo() throws Exception
  {
    // Half of 8 is 4, dead centre of the 4-unit bar, which is wide enough to be cut.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key width='4' c='space'/>"
        + "<key c='c'/><key c='d'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(6, row.keys.size());
    assertArrayEquals(new float[]{ 1f, 1f, 2f, 2f, 1f, 1f }, widths(row), EPS);
    assertArrayEquals(new float[]{ 0f, 0f, 0f, 1f, 0f, 0f }, shifts(row), EPS);
  }

  @Test
  public void aMidpointNearAKeyEdgeTakesThatEdge() throws Exception
  {
    // Half of 4.2 is 2.1: a tenth of a unit inside the third key, whose left edge is nearer.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key width='1.2' c='c'/><key c='d'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(4, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 1f, 0f }, shifts(row), EPS);
  }

  @Test
  public void aMidpointPastTheMiddleOfAKeyTakesItsRightEdge() throws Exception
  {
    // Half of 4.2 is 2.1, and the second key spans 1..2.2: its right edge is the nearer one.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key width='1.2' c='b'/><key c='c'/><key c='d'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(4, row.keys.size());
    assertArrayEquals(new float[]{ 0f, 0f, 1f, 0f }, shifts(row), EPS);
    assertArrayEquals(new float[]{ 2.2f, 3.2f }, SplitLayout.rowGap(row, 1f), EPS);
  }

  @Test
  public void aPartingWithNothingOnItsLeftLeavesTheRowWhole() throws Exception
  {
    // Half of 5 is 2.5, which lands in the first key's own shift: parting there would only pad
    // the row instead of splitting it.
    KeyboardData keyboard =
        keyboard("<row><key shift='3' c='a'/><key c='b'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(2, row.keys.size());
    assertArrayEquals(new float[]{ 3f, 0f }, shifts(row), EPS);
  }

  @Test
  public void aWideFirstKeyIsCutRatherThanLeftWhole() throws Exception
  {
    KeyboardData keyboard = keyboard("<row><key width='4' c='a'/><key c='b'/></row>");

    KeyboardData.Row row = LayoutModifier.split(keyboard, 1f).rows.get(0);

    assertEquals(3, row.keys.size());
    assertArrayEquals(new float[]{ 2.5f, 1.5f, 1f }, widths(row), EPS);
    assertArrayEquals(new float[]{ 0f, 1f, 0f }, shifts(row), EPS);
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
  public void theGapIsAFractionOfTheKeyboardWidthSoItIsOneGapForEveryRow() throws Exception
  {
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
  public void theBandTheRowsShareIsWhatTheirPartingsOverlap() throws Exception
  {
    // The first row parts at 2.0; the second's midpoint is a tenth of a unit past the middle of
    // its second key, so it snaps to that key's right edge at 2.2 and the two partings overlap
    // by 0.8 of the gap.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>"
        + "<row><key c='e'/><key width='1.2' c='f'/><key c='g'/><key c='h'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    assertArrayEquals(new float[]{ 2f, 3f },
        SplitLayout.rowGap(split.rows.get(0), 1f), EPS);
    assertArrayEquals(new float[]{ 2.2f, 3.2f },
        SplitLayout.rowGap(split.rows.get(1), 1f), EPS);
    assertArrayEquals(new float[]{ 2.2f, 3f }, SplitLayout.commonGap(split, 1f), EPS);
  }

  @Test
  public void rowsWhosePartingsMissEachOtherShareNoBand() throws Exception
  {
    // Five unit keys part at 2.0 — the midpoint is the centre of the third key, which is no bar,
    // so the parting takes its left edge; the second row's own shift pushes its parting to 3.0, a
    // whole unit away, which a 0.4 gap cannot bridge.
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/><key c='e'/></row>"
        + "<row><key c='f'/><key shift='2' c='g'/><key c='h'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 0.4f);

    assertNull(SplitLayout.commonGap(split, 0.4f));
  }

  @Test
  public void anUnpartedRowLeavesNoBandForTheHostToUse() throws Exception
  {
    KeyboardData keyboard = keyboard(
        "<row><key c='a'/><key c='b'/><key c='c'/><key c='d'/></row>"
        + "<row><key width='4' c='e'/></row>");

    KeyboardData split = LayoutModifier.split(keyboard, 1f);

    assertNull(SplitLayout.rowGap(split.rows.get(1), 1f));
    assertNull(SplitLayout.commonGap(split, 1f));
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
  public void theComposedLauncherLayoutPartsOnEveryRowAndSharesABand()
  {
    android.content.res.Resources resources =
        org.robolectric.RuntimeEnvironment.getApplication().getResources();
    KeyboardData composed = LayoutModifier.modify(
        KeyboardData.load(resources, R.xml.termux_launcher_qwerty),
        new LayoutModifier.LayoutOptions(true, false, true), resources);

    float gap = LayoutModifier.gapUnits(composed, 0.25f);
    KeyboardData split = LayoutModifier.split(composed, gap);

    assertEquals(composed.rows.size(), split.rows.size());
    for (int i = 0; i < split.rows.size(); i++)
    {
      KeyboardData.Row row = split.rows.get(i);
      assertNotNull("row " + i + " parts", SplitLayout.rowGap(row, gap));
      assertEquals("row " + i + " grew by exactly the gap",
          composed.rows.get(i).keysWidth + gap, row.keysWidth, EPS);
    }
    float[] band = SplitLayout.commonGap(split, gap);
    assertNotNull("the rows share a band", band);
    assertTrue("the shared band is worth pointing in", band[1] - band[0] > gap / 2f);
    // The space bar straddles the midpoint of the bottom row, so that row gains a key -- and it
    // is the only row that does: no letter key is ever cut in two.
    assertEquals(composed.rows.get(composed.rows.size() - 1).keys.size() + 1,
        split.rows.get(split.rows.size() - 1).keys.size());
    for (int i = 0; i < split.rows.size() - 1; i++)
      assertEquals("row " + i + " keeps every key whole",
          composed.rows.get(i).keys.size(), split.rows.get(i).keys.size());
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
