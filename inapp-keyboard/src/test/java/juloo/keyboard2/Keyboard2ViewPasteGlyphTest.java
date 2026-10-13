package juloo.keyboard2;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Color;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * While paste will put in a picture, paste draws a photo glyph in the symbols face instead of
 * its own key-font glyph, under a modifier too (pasteAsPlainText), a size down so it matches its
 * neighbours, and nothing else changes.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewPasteGlyphTest
{
  private static final String PHOTO = new String(Character.toChars(0xF02E9));
  private static final KeyValue PASTE = KeyValue.getKeyByName("paste");
  private static final KeyValue PASTE_PLAIN = KeyValue.getKeyByName("pasteAsPlainText");

  private Keyboard2View view;

  @Before
  public void setUp() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoopHandler());
    builder.hapticEnabled = false;
    Theme.Palette palette = new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.CYAN, Color.WHITE,
        Color.GREEN, Color.GRAY, false, 0f, 0f, 1f);
    view = new Keyboard2View(context, builder.build(), palette);
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'><row><key c='v' se='paste'/></row></keyboard>"));
  }

  @Test
  public void withNoPictureThePasteKeysKeepTheirOwnGlyph()
  {
    assertEquals("", view.labelForDrawing(PASTE));
    assertEquals(LabelFace.KEY, view.labelFaceForDrawing(PASTE));
    assertEquals("", view.labelForDrawing(PASTE_PLAIN));
    assertEquals(LabelFace.KEY, view.labelFaceForDrawing(PASTE_PLAIN));
  }

  @Test
  public void aPictureOnTheClipboardSwapsBothPasteKeysToThePhotoGlyph()
  {
    view.setPasteShowsPicture(true);

    assertEquals(PHOTO, Keyboard2View.PASTE_PICTURE_GLYPH);
    assertEquals(PHOTO, view.labelForDrawing(PASTE));
    assertEquals(LabelFace.SYMBOL, view.labelFaceForDrawing(PASTE));
    assertEquals(PHOTO, view.labelForDrawing(PASTE_PLAIN));
    assertEquals(LabelFace.SYMBOL, view.labelFaceForDrawing(PASTE_PLAIN));
  }

  @Test
  public void thePhotoGlyphIsDrawnSmallerThanTheKeysOwn()
  {
    float own = view.labelSizeForDrawing(PASTE, false);
    view.setPasteShowsPicture(true);

    assertEquals(own * Keyboard2View.PASTE_PICTURE_SCALE,
        view.labelSizeForDrawing(PASTE, false), 0.001f);
    assertEquals(view.labelSizeForDrawing(KeyValue.getKeyByName("copy"), false), own, 0.001f);
  }

  @Test
  public void otherKeysAndTheWayBackAreUntouched()
  {
    KeyValue copy = KeyValue.getKeyByName("copy");
    String copyGlyph = copy.getString();
    view.setPasteShowsPicture(true);
    assertEquals(copyGlyph, view.labelForDrawing(copy));
    assertEquals(LabelFace.KEY, view.labelFaceForDrawing(copy));
    assertEquals("v", view.labelForDrawing(KeyValue.getKeyByName("v")));

    view.setPasteShowsPicture(false);
    assertEquals("", view.labelForDrawing(PASTE));
    assertEquals(LabelFace.KEY, view.labelFaceForDrawing(PASTE));
  }

  private static final class NoopHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers mods) {}
    @Override public void mods_changed(Pointers.Modifiers mods) {}
    @Override public void suggestion_entered(String text) {}
  }
}
