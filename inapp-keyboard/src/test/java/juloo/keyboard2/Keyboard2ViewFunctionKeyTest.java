package juloo.keyboard2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * Upstream layouts give the real enter/editor-action key and every other modifier key
 * (shift, ctrl, backspace, arrows, layout switch, config) the same Action role — see
 * bottom_row.xml. Keyboard2View.isEnterKey is how the theme still tells them apart at draw
 * time, from the key's own value rather than its role; see inapp-keyboard/UPSTREAM.md.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewFunctionKeyTest
{
  @Test
  public void onlyTheActualEnterKeyIsRecognizedAsTheActionKey() throws Exception
  {
    KeyboardData layout = KeyboardData.load_string_exn(
        "<keyboard>"
        + "<row><key role='action' key0='enter'/>"
        + "<key role='action' key0='ctrl'/>"
        + "<key role='action' key0='backspace'/>"
        + "<key role='action' key0='shift'/>"
        + "<key c='a'/>"
        + "</row></keyboard>");
    KeyboardData.Key enterKey = layout.rows.get(0).keys.get(0);
    KeyboardData.Key ctrlKey = layout.rows.get(0).keys.get(1);
    KeyboardData.Key backspaceKey = layout.rows.get(0).keys.get(2);
    KeyboardData.Key shiftKey = layout.rows.get(0).keys.get(3);
    KeyboardData.Key letterKey = layout.rows.get(0).keys.get(4);

    assertTrue("the enter key is the action key",
        isEnterKey(enterKey));
    assertFalse("ctrl is a function key, not the action key",
        isEnterKey(ctrlKey));
    assertFalse("backspace is a function key, not the action key",
        isEnterKey(backspaceKey));
    assertFalse("shift is a function key, not the action key",
        isEnterKey(shiftKey));
    assertFalse("a Normal-role letter key is never the action key",
        isEnterKey(letterKey));
  }

  private static boolean isEnterKey(KeyboardData.Key k)
  {
    return ReflectionHelpers.callStaticMethod(Keyboard2View.class, "isEnterKey",
        ClassParameter.from(KeyboardData.Key.class, k));
  }
}
