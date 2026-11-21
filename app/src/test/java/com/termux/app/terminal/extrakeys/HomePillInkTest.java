package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.termux.app.chrome.OnGlass;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeyEligibility;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The pill's keys are inked against the pill's own plate, on light and dark alike. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomePillInkTest {

    private static final double TARGET = OnGlass.TARGET_BODY_TEXT;
    private static final int LIGHT_PLATE = Color.rgb(0xFA, 0xF9, 0xF6);
    private static final int DARK_PLATE = Color.rgb(0x14, 0x16, 0x1A);
    private static final int BLACK_PLATE = Color.BLACK;
    private static final int TEAL_PLATE = Color.rgb(0x2E, 0x5F, 0x5B);
    private static final int PALE_THEME_INK = Color.rgb(0xE6, 0xE6, 0xE6);
    private static final int DARK_THEME_INK = Color.rgb(0x1A, 0x1A, 0x1A);

    private static double contrast(int ink, int plate) {
        return ColorUtils.calculateContrast(ink, plate);
    }

    @Test
    public void aLightPlateGetsDarkInkWhateverTheThemeOffers() {
        int ink = HomePillInk.on(LIGHT_PLATE, PALE_THEME_INK, TARGET);
        assertTrue("dark ink", ColorUtils.calculateLuminance(ink) < 0.2);
        assertTrue(contrast(ink, LIGHT_PLATE) >= TARGET - 0.05);
    }

    @Test
    public void aDarkPlateGetsLightInkWhateverTheThemeOffers() {
        int[] plates = {DARK_PLATE, BLACK_PLATE, TEAL_PLATE};
        for (int plate : plates) {
            int ink = HomePillInk.on(plate, DARK_THEME_INK, TARGET);
            assertTrue("light ink on " + Integer.toHexString(plate),
                ColorUtils.calculateLuminance(ink) > ColorUtils.calculateLuminance(plate));
            assertTrue(contrast(ink, plate) >= TARGET - 0.05);
        }
    }

    @Test
    public void aThemeInkThatAlreadyReadsIsKept() {
        assertTrue(HomePillInk.on(LIGHT_PLATE, DARK_THEME_INK, TARGET) == DARK_THEME_INK);
        assertTrue(HomePillInk.on(DARK_PLATE, PALE_THEME_INK, TARGET) == PALE_THEME_INK);
    }

    @Test
    public void theStarterKeysAreLiveOnHome() {
        for (String key : new String[] {"tool:app.command_palette", "tool:app.open_app_drawer", "tool:app.open_settings"}) {
            // Keys that act on Home are never drawn dead; the pill dims only what the bar dims.
            assertTrue(key, ExtraKeyEligibility.isUsable(key, PageKind.HOME));
        }
    }

    @Test
    public void renderedGlyphPixelsDifferClearlyFromThePlate() throws Exception {
        int[] plates = {LIGHT_PLATE, DARK_PLATE, BLACK_PLATE, TEAL_PLATE};
        for (int plate : plates) {
            int ink = HomePillInk.on(plate, PALE_THEME_INK, TARGET);
            ExtraKeysInfo info = new ExtraKeysInfo("[['CTRL']]",
                ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            ExtraKeyButton key = info.getMatrix()[0][0];
            ExtraKeyCapView cap = new ExtraKeyCapView(RuntimeEnvironment.getApplication(), key,
                new KeyId(key.getKey(), null, 0));
            cap.setLabel("CTRL", true);
            cap.setLabelColor(ink);
            cap.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
            cap.layout(0, 0, 160, 100);
            // Robolectric rasterises no glyphs, so the armed mark (drawn in the label's own ink) stands for them.
            cap.setModifierState(ExtraKeyModifiers.State.ARMED);
            Bitmap bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(plate);
            cap.draw(canvas);
            double best = 0;
            for (int y = 0; y < 100; y++) {
                for (int x = 0; x < 160; x++) {
                    best = Math.max(best, contrast(bitmap.getPixel(x, y), plate));
                }
            }
            assertTrue("glyph pixels contrast with plate " + Integer.toHexString(plate)
                + ": " + best, best >= 3.0);
        }
    }
}
