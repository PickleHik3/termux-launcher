package com.termux.app.fragments.settings.termux;

import static com.termux.app.fragments.settings.termux.ExtraKeysEditorHarness.findDescription;
import static com.termux.app.fragments.settings.termux.ExtraKeysEditorHarness.findText;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Draws the page editor and each sheet to PNGs under {@code app/build/extra-keys-editor-renders},
 * so the result can be compared with the wireframes without a device. The assertions only check
 * that every screen laid out; the pictures are for a person.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w393dp-h812dp-port-xhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeysEditorRenderTest {

    private static final File OUT = new File("build/extra-keys-editor-renders");

    private static void png(View view, String name) throws Exception {
        int width = Math.max(1, view.getWidth());
        int height = Math.max(1, view.getHeight());
        assertTrue(name + " laid out", view.getWidth() > 0 && view.getHeight() > 0);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        assertTrue(OUT.isDirectory() || OUT.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(OUT, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private void renderAll(String suffix) throws Exception {
        ExtraKeysEditorHarness h = new ExtraKeysEditorHarness();
        h.editDraft(draft -> {
            int row = draft.addRow(0);
            for (String key : new String[]{"ESC", "CTRL", "ALT", "TAB", "LEFT", "DOWN", "UP", "RIGHT"}) {
                draft.addKey(0, row, new ExtraKeysLayoutModel.Key(key));
            }
        });
        View decor = h.controller.get().getWindow().getDecorView();
        png(decor, "1-page-editor" + suffix);

        // Edit key: the first cap of the first row.
        h.click(findDescription(h.page(), "KEYBOARD"));
        png(h.sheet(), "2-edit-key" + suffix);
        close();

        // Choose a key, from the first row's +.
        h.click(findText(h.page(), "+"));
        png(h.sheet(), "3-choose-key" + suffix);
        close();

        h.click(findDescription(h.page(), h.fragment.getString(R.string.settings_extra_keys_row_options_cd)));
        png(h.sheet(), "4-row-options" + suffix);
        close();

        h.click(findDescription(h.page(), h.fragment.getString(R.string.settings_extra_keys_page_options_cd)));
        png(h.sheet(), "5-page-options" + suffix);
        close();

        h.click(findDescription(h.page(), h.fragment.getString(R.string.settings_extra_keys_add_page_cd)));
        png(decor, "6-empty-page-2" + suffix);
    }

    private static void close() {
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss();
        shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    @Config(qualifiers = "w393dp-h812dp-port-night-xhdpi")
    public void rendersDark() throws Exception {
        renderAll("-dark");
    }

    @Test
    @Config(qualifiers = "w393dp-h812dp-port-notnight-xhdpi")
    public void rendersLight() throws Exception {
        renderAll("-light");
    }
}
