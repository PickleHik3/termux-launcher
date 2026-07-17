package juloo.keyboard2;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;
import org.junit.After;
import org.junit.Test;

public class KeyModifierTest
{
  @After
  public void clearModmap()
  {
    KeyModifier.set_modmap(null);
  }

  @Test
  public void shiftTransformsCharactersAndStrings()
  {
    KeyValue shift = KeyValue.makeInternalModifier(KeyValue.Modifier.SHIFT);
    assertEquals('A', KeyModifier.modify(KeyValue.makeCharKey('a'), shift).getChar());
    assertEquals("Hello", KeyModifier.modify(
        KeyValue.makeStringKey("hello"), shift).getString());
  }

  @Test
  public void ctrlTransformsTerminalVocabularyIntoKeyevents()
  {
    KeyValue ctrl = KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL);
    assertEquals(KeyEvent.KEYCODE_C, KeyModifier.modify(
        KeyValue.makeCharKey('c'), ctrl).getKeyevent());
    assertEquals(KeyEvent.KEYCODE_SPACE, KeyModifier.modify(
        KeyValue.getKeyByName("space"), ctrl).getKeyevent());
  }
}
