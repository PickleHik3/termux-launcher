package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class KeyboardDataTest
{
  private Resources resources;

  @Before
  public void setUp()
  {
    resources = RuntimeEnvironment.getApplication().getResources();
  }

  @Test
  public void parsesBundledQwertyGoldenAndComposesBottomRow()
  {
    KeyboardData keyboard = KeyboardData.load(resources, R.xml.latn_qwerty_us);

    assertNotNull(keyboard);
    assertEquals("QWERTY (US)", keyboard.name);
    assertEquals("latin", keyboard.script);
    assertEquals(3, keyboard.rows.size());
    assertEquals(Arrays.asList(10, 9, 9), Arrays.asList(
        keyboard.rows.get(0).keys.size(), keyboard.rows.get(1).keys.size(),
        keyboard.rows.get(2).keys.size()));
    assertEquals('q', keyboard.rows.get(0).keys.get(0).keys[0].getChar());
    assertEquals('1', keyboard.rows.get(0).keys.get(0).keys[2].getChar());

    KeyboardData modified = LayoutModifier.modify(keyboard,
        new LayoutModifier.LayoutOptions(true, false, true), resources);
    assertEquals(4, modified.rows.size());
    KeyboardData.Row bottom = modified.rows.get(3);
    assertEquals(5, bottom.keys.size());
    for (KeyboardData.Key key : bottom.keys)
      for (KeyValue value : key.keys)
        if (value != null && value.getKind() == KeyValue.Kind.Event)
        {
          assertFalse(value.getEvent() == KeyValue.Event.SWITCH_CLIPBOARD);
          assertFalse(value.getEvent() == KeyValue.Event.SWITCH_EMOJI);
          assertFalse(value.getEvent() == KeyValue.Event.SWITCH_VOICE_TYPING);
          assertFalse(value.getEvent() == KeyValue.Event.CHANGE_METHOD_PICKER);
        }
  }

  @Test
  public void parsesAllEightDirections() throws Exception
  {
    String xml = "<keyboard bottom_row='false'><row><key c='c' nw='1' ne='2'"
        + " sw='3' se='4' w='5' e='6' n='7' s='8' anticircle='9'/>"
        + "</row></keyboard>";

    KeyboardData.Key key = KeyboardData.load_string_exn(xml)
        .rows.get(0).keys.get(0);
    assertEquals('c', key.keys[0].getChar());
    for (int i = 1; i <= 8; i++)
      assertEquals((char)('0' + i), key.keys[i].getChar());
    assertEquals('9', key.anticircle.getChar());
  }

  @Test
  public void rejectsRowsAndKeysBeyondLimits()
  {
    StringBuilder tooManyRows = new StringBuilder("<keyboard>");
    for (int i = 0; i <= KeyboardData.MAX_ROWS; i++)
      tooManyRows.append("<row><key c='a'/></row>");
    tooManyRows.append("</keyboard>");
    Exception rowsError = assertThrows(Exception.class,
        () -> KeyboardData.load_string_exn(tooManyRows.toString()));
    assertTrue(rowsError.getMessage().contains("16 rows"));

    StringBuilder tooManyKeys = new StringBuilder("<keyboard><row>");
    for (int i = 0; i <= KeyboardData.MAX_KEYS_PER_ROW; i++)
      tooManyKeys.append("<key c='a'/>");
    tooManyKeys.append("</row></keyboard>");
    Exception keysError = assertThrows(Exception.class,
        () -> KeyboardData.load_string_exn(tooManyKeys.toString()));
    assertTrue(keysError.getMessage().contains("32 keys"));
  }

  @Test
  public void acceptsExactMaximumParsedLayoutSize() throws Exception
  {
    StringBuilder maximum = new StringBuilder("<keyboard bottom_row='false'>");
    for (int row = 0; row < KeyboardData.MAX_ROWS; row++)
    {
      maximum.append("<row>");
      for (int key = 0; key < KeyboardData.MAX_KEYS_PER_ROW; key++)
        maximum.append("<key c='a'/>");
      maximum.append("</row>");
    }
    maximum.append("</keyboard>");

    KeyboardData parsed = KeyboardData.load_string_exn(maximum.toString());

    assertEquals(KeyboardData.MAX_ROWS, parsed.rows.size());
    int totalKeys = 0;
    for (KeyboardData.Row row : parsed.rows)
      totalKeys += row.keys.size();
    assertEquals(KeyboardData.MAX_KEYS_TOTAL, totalKeys);
  }
}
