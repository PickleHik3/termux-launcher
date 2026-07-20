package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import org.junit.Test;

public class KeyValueParserTest
{
  @Test
  public void parsesRetainedVocabularyAndCompatibilityEvents() throws Exception
  {
    assertEquals(KeyValue.Modifier.CTRL,
        KeyValue.getKeyByName("ctrl").getModifier());
    assertEquals(KeyValue.Event.SWITCH_CLIPBOARD,
        KeyValue.getKeyByName("switch_clipboard").getEvent());
    assertEquals(KeyValue.Event.HIDE_SELF,
        KeyValue.getKeyByName("hide_self").getEvent());
    assertEquals(KeyValue.Editing.BACKSPACE,
        KeyValue.getKeyByName("backspace").getEditing());
    assertEquals(KeyEvent.KEYCODE_F12,
        KeyValue.getKeyByName("f12").getKeyevent());
    assertEquals(KeyValue.Kind.Stateful,
        KeyValue.getKeyByName("complete_first").getKind());
    assertEquals("", KeyValue.getKeyByName("complete_first").getString());
  }

  @Test
  public void parsesMacrosStringsKeyeventsAndLegacySyntax() throws Exception
  {
    KeyValue macro = KeyValueParser.parse("copy:ctrl,a,ctrl,c");
    assertEquals(KeyValue.Kind.Macro, macro.getKind());
    assertEquals(4, macro.getMacro().length);
    assertEquals("hello", KeyValueParser.parse(":str:'hello'").getString());
    assertEquals(85, KeyValueParser.parse("x:keyevent:85").getKeyevent());
    assertTrue(KeyValueParser.parse("arbitrary text").getKind()
        == KeyValue.Kind.String);
  }
}
