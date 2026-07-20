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

  /** Regression: the clockwise-circle / round-trip gesture must capitalize a
      letter even when the layout's modmap binds Fn for that letter, as our
      terminal layouts do. Local deviation from upstream (Shift beats Fn). */
  @Test
  public void gestureCapitalizesLettersEvenWhenModmapBindsFn()
  {
    KeyValue gesture = KeyValue.makeInternalModifier(KeyValue.Modifier.GESTURE);
    KeyValue g = KeyValue.makeCharKey('g');
    Modmap mm = new Modmap();
    mm.add(Modmap.M.Fn, g, KeyValue.getKeyByName("end"));
    KeyModifier.set_modmap(mm);
    assertEquals('G', KeyModifier.modify(g, gesture).getChar());
  }

  /** The Fn binding still wins for keys where Shift is a no-op (non-letters). */
  @Test
  public void gestureUsesModmapFnWhenShiftIsNoOp()
  {
    KeyValue gesture = KeyValue.makeInternalModifier(KeyValue.Modifier.GESTURE);
    KeyValue left = KeyValue.getKeyByName("left");
    KeyValue home = KeyValue.getKeyByName("home");
    Modmap mm = new Modmap();
    mm.add(Modmap.M.Fn, left, home);
    KeyModifier.set_modmap(mm);
    assertEquals(home, KeyModifier.modify(left, gesture));
  }
}
