package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The tap geometry's signature names a grid by its key structure, never its pixel size: the
 * host keeps one learned model per signature, so a resize must keep it and a key edit must not.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class TapGeometryTest
{
  private static final String LAYOUT =
      "<keyboard bottom_row='false'>"
      + "<row><key c='q'/><key c='w'/><key c='e'/></row>"
      + "<row><key c='a' shift='0.5'/><key c='s'/></row>"
      + "<row><key c='backspace'/><key c='space' width='2'/></row>"
      + "</keyboard>";

  private static TapGeometry geometry(String layout, float rowHeightPx, float keyWidthPx)
      throws Exception
  {
    return TapGeometry.of(KeyboardData.load_string_exn(layout), rowHeightPx, keyWidthPx);
  }

  @Test
  public void aResizeKeepsTheSignature() throws Exception
  {
    // pong's resize: the key width went from 106 px to 100 px, the row height stayed.
    TapGeometry before = geometry(LAYOUT, 150f, 106f);
    TapGeometry after = geometry(LAYOUT, 150f, 100f);
    assertEquals(before.signature, after.signature);
    // An edge-gap or height change is a resize too.
    assertEquals(before.signature, geometry(LAYOUT, 180f, 90f).signature);
    // The geometry itself follows the new size: rows are taller in key widths.
    assertNotEquals(before.height(0), after.height(0), 1e-4f);
    assertEquals(before.width(0), after.width(0), 1e-6f);
  }

  @Test
  public void editingTheKeysChangesTheSignature() throws Exception
  {
    String base = geometry(LAYOUT, 150f, 100f).signature;
    // A wider key.
    assertNotEquals(base, geometry(LAYOUT.replace("<key c='e'/>",
        "<key c='e' width='1.5'/>"), 150f, 100f).signature);
    // A key with another role: Esc in place of a character.
    assertNotEquals(base, geometry(LAYOUT.replace("<key c='w'/>",
        "<key c='esc'/>"), 150f, 100f).signature);
    // One key more.
    assertNotEquals(base, geometry(LAYOUT.replace("<key c='s'/>",
        "<key c='s'/><key c='d'/>"), 150f, 100f).signature);
    // A key moved along its row.
    assertNotEquals(base, geometry(LAYOUT.replace("<key c='a' shift='0.5'/>",
        "<key c='a'/>"), 150f, 100f).signature);
    // A taller row.
    assertNotEquals(base, geometry(LAYOUT.replace("<row><key c='backspace'/>",
        "<row height='1.2'><key c='backspace'/>"), 150f, 100f).signature);
  }
}
