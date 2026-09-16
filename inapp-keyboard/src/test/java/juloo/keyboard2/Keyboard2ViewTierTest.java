package juloo.keyboard2;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Role-less layouts (the shipped launcher layout, user layouts) still get the Material tiers. */
@RunWith(RobolectricTestRunner.class)
public class Keyboard2ViewTierTest
{
  @Test
  public void roleLessKeysAreClassifiedByValue() throws Exception
  {
    KeyboardData layout = KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='enter'/><key c='ctrl'/><key c='shift'/><key c='backspace'/>"
        + "<key c='left'/><key c='config'/><key c='space'/><key c='a'/>"
        + "</row></keyboard>");
    List<KeyboardData.Key> keys = layout.rows.get(0).keys;
    assertEquals(Keyboard2View.KeyTier.ACTION, Keyboard2View.tierFor(keys.get(0)));
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(1)));
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(2)));
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(3)));
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(4)));
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(5)));
    assertEquals(Keyboard2View.KeyTier.SPACE_BAR, Keyboard2View.tierFor(keys.get(6)));
    assertEquals(Keyboard2View.KeyTier.LETTER, Keyboard2View.tierFor(keys.get(7)));
  }

  @Test
  public void explicitRolesStillWin() throws Exception
  {
    KeyboardData layout = KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key role='action' key0='ctrl'/><key role='space_bar' key0='space'/>"
        + "</row></keyboard>");
    List<KeyboardData.Key> keys = layout.rows.get(0).keys;
    assertEquals(Keyboard2View.KeyTier.FUNCTION, Keyboard2View.tierFor(keys.get(0)));
    assertEquals(Keyboard2View.KeyTier.SPACE_BAR, Keyboard2View.tierFor(keys.get(1)));
  }
}
