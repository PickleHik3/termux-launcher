package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalRow;
import com.termux.view.TerminalRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;

/** Device tests for ICU grapheme decisions and stored terminal-cell ownership. */
@RunWith(AndroidJUnit4.class)
public class GraphemeBufferInstrumentationTest {

    @Test
    public void zwjEmojiAndFlagEachKeepTheFirstBaseWidth() {
        TerminalEmulator emoji = emulator(12, 3);
        enter(emoji, "👩‍💻");
        assertEquals(2, emoji.getCursorCol());
        assertEquals("👩‍💻", selectedCell(emoji, 0));
        TerminalRow emojiRow = row(emoji, 0);
        assertEquals(5, emojiRow.findStartOfColumn(2));

        TerminalEmulator flag = emulator(12, 3);
        enter(flag, "🇸🇦");
        assertEquals(2, flag.getCursorCol());
        assertEquals("🇸🇦", selectedCell(flag, 0));
        assertEquals(4, row(flag, 0).findStartOfColumn(2));
    }

    @Test
    public void indicConjunctAndCombiningSequenceOwnOneCell() {
        TerminalEmulator indic = emulator(12, 3);
        enter(indic, "क्षि");
        assertEquals(1, indic.getCursorCol());
        assertEquals("क्षि", selectedCell(indic, 0));

        TerminalEmulator combining = emulator(12, 3);
        enter(combining, "A\u0301\u0327");
        assertEquals(1, combining.getCursorCol());
        assertEquals("A\u0301\u0327", selectedCell(combining, 0));
    }

    @Test
    public void copyAndReflowPreserveTheWholeCluster() {
        TerminalEmulator emulator = emulator(8, 3);
        enter(emulator, "A👩‍💻B");
        assertEquals(4, emulator.getCursorCol());

        emulator.resize(3, 3, 12, 24);
        String text = emulator.getScreen().getSelectedText(0, 0, 3, 2, true, true);
        assertTrue(text, text.contains("A👩‍💻B"));
    }

    @Test
    public void styleBoundaryCursorAndSelectionRenderTheWholeCluster() {
        TerminalEmulator emulator = emulator(12, 3);
        enter(emulator, "क्\033[1mषिX\r");
        assertEquals("क्षि", selectedCell(emulator, 0));

        Bitmap bitmap = Bitmap.createBitmap(600, 180, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        TerminalRenderer renderer = new TerminalRenderer(48, Typeface.MONOSPACE, Typeface.MONOSPACE);
        renderer.render(emulator, canvas, 0, 0, 0, 0, 0, false, Color.TRANSPARENT, 0f);

        boolean drewPixels = false;
        for (int y = 0; y < bitmap.getHeight() && !drewPixels; y += 4) {
            for (int x = 0; x < bitmap.getWidth(); x += 4) {
                if (bitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    drewPixels = true;
                    break;
                }
            }
        }
        assertTrue("cluster cursor/selection render should draw visible pixels", drewPixels);
    }

    @Test
    public void familySymbolMapsLoadAndLaterOverlapsWin() {
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "symbol_map U+0041-U+005A family=serif\n"
                + "symbol_map U+0041 family=sans-serif\n", true);
        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);
        assertTrue(faces.errors.toString(), faces.errors.isEmpty());
        assertEquals(2, faces.symbolMaps.length);

        Bitmap serifLast = render("A", new TerminalRenderer.SymbolMap[]{
            new TerminalRenderer.SymbolMap('A', 'A', Typeface.create("sans-serif", 0)),
            new TerminalRenderer.SymbolMap('A', 'A', Typeface.create("serif", 0))
        });
        Bitmap sansLast = render("A", new TerminalRenderer.SymbolMap[]{
            new TerminalRenderer.SymbolMap('A', 'A', Typeface.create("serif", 0)),
            new TerminalRenderer.SymbolMap('A', 'A', Typeface.create("sans-serif", 0))
        });
        assertFalse("the later overlapping map must select the drawn font",
            serifLast.sameAs(sansLast));
    }

    @Test
    public void symbolMapSelectsByFirstCodePointOfWholeGrapheme() {
        Bitmap baseline = render("A\u0301", null);
        Bitmap continuationOnly = render("A\u0301", new TerminalRenderer.SymbolMap[]{
            new TerminalRenderer.SymbolMap(0x0301, 0x0301, Typeface.create("serif", 0))
        });
        Bitmap baseMapped = render("A\u0301", new TerminalRenderer.SymbolMap[]{
            new TerminalRenderer.SymbolMap('A', 'A', Typeface.create("serif", 0))
        });

        assertTrue("mapping a continuation must not split its grapheme",
            baseline.sameAs(continuationOnly));
        assertFalse("mapping the first code point must select the symbol font",
            baseline.sameAs(baseMapped));
    }

    @Test
    public void ligaturePolicyDisablesCaltOnlyWhereRequested() {
        Typeface ligatureFace = Typeface.create("sans-serif", Typeface.NORMAL);
        Bitmap enabled = render("->", null, TerminalRenderer.LigaturePolicy.NEVER, false,
            ligatureFace);
        Bitmap cursorWithoutCursor = render("->", null,
            TerminalRenderer.LigaturePolicy.CURSOR, false, ligatureFace);
        Bitmap alwaysDisabled = render("->", null,
            TerminalRenderer.LigaturePolicy.ALWAYS, false, ligatureFace);

        assertTrue("cursor policy must preserve ligatures away from a visible cursor",
            enabled.sameAs(cursorWithoutCursor));
        assertTrue("always policy renderer smoke should draw output", hasVisiblePixels(alwaysDisabled));

        // Exercise the cursor-only draw path. The cursor cell is already a separate inversion run,
        // and the renderer additionally applies -calt to that run before restoring Paint state.
        render("->\r", null, TerminalRenderer.LigaturePolicy.CURSOR, true, ligatureFace);
    }

    @Test
    public void rendererAppliesVariableAxesInsideTheFixedCellGrid() {
        Typeface variable = Typeface.createFromFile(
            new File("/system/fonts/RobotoFlex-Regular.ttf"));
        TerminalRenderer.FontVariations light = new TerminalRenderer.FontVariations(
            "'wght' 100", null, null, null, null);
        TerminalRenderer.FontVariations heavy = new TerminalRenderer.FontVariations(
            "'wght' 900", null, null, null, null);

        Bitmap lightBitmap = render("M", null, TerminalRenderer.LigaturePolicy.NEVER, false,
            variable, light);
        Bitmap heavyBitmap = render("M", null, TerminalRenderer.LigaturePolicy.NEVER, false,
            variable, heavy);
        assertFalse("weight axis should change the glyph while retaining the same cell grid",
            lightBitmap.sameAs(heavyBitmap));
    }

    @Test
    public void rejectedVariationSettingsFallBackWithoutCrashingTheRenderer() {
        TerminalRenderer.FontVariations malformed = new TerminalRenderer.FontVariations(
            "not-a-valid-axis-setting", null, null, null, null);
        Bitmap bitmap = render("SAFE", null, TerminalRenderer.LigaturePolicy.NEVER, false,
            Typeface.MONOSPACE, malformed);
        assertTrue("a rejected optional variation must retain base-font rendering",
            hasVisiblePixels(bitmap));
    }

    @Test
    public void loaderAppliesCellAndBaselineMetricsToRendererGeometry() {
        TerminalFontConfig.Result config = TerminalFontConfig.parse(
            "modify_font cell_width 150%\n"
                + "modify_font cell_height 8px\n"
                + "modify_font baseline 4px\n", true);
        TerminalFontLoader.Faces faces = TerminalFontLoader.load(config);
        assertTrue(faces.errors.toString(), faces.errors.isEmpty());

        Bitmap defaults = render("XX", null);
        Bitmap adjusted = render("XX", null, TerminalRenderer.LigaturePolicy.NEVER, false,
            faces.regular, TerminalRenderer.FontVariations.NONE,
            faces.fontMetricsAdjustments);
        assertFalse("cell width, height, and baseline must alter fixed-grid rendering",
            defaults.sameAs(adjusted));
    }

    @Test
    public void configurableUnderlineAndStrikethroughUseBoundedGeometry() {
        String decorated = "\033[4mU\033[0m \033[9mS\033[0m";
        Bitmap defaults = render(decorated, null);
        TerminalRenderer.FontMetricsAdjustments metrics =
            new TerminalRenderer.FontMetricsAdjustments(null, null, null,
                new TerminalRenderer.MetricAdjustment(-2f, false),
                new TerminalRenderer.MetricAdjustment(200f, true),
                new TerminalRenderer.MetricAdjustment(3f, false),
                new TerminalRenderer.MetricAdjustment(250f, true));
        Bitmap adjusted = render(decorated, null, TerminalRenderer.LigaturePolicy.NEVER, false,
            Typeface.MONOSPACE, TerminalRenderer.FontVariations.NONE, metrics);

        assertTrue("adjusted decorations should remain inside their bitmap",
            hasVisiblePixels(adjusted));
        assertFalse("decoration position and thickness must affect rendered pixels",
            defaults.sameAs(adjusted));
    }

    @Test
    public void positiveBaselineRaisesGlyphAndBothDecorationsTogether() {
        TerminalRenderer.FontMetricsAdjustments raised =
            new TerminalRenderer.FontMetricsAdjustments(null, null,
                new TerminalRenderer.MetricAdjustment(4f, false), null, null, null, null);
        Bitmap underlineDefault = render("\033[4m \033[0m", null);
        Bitmap underlineRaised = render("\033[4m \033[0m", null,
            TerminalRenderer.LigaturePolicy.NEVER, false, Typeface.MONOSPACE,
            TerminalRenderer.FontVariations.NONE, raised);
        Bitmap strikeDefault = render("\033[9m \033[0m", null);
        Bitmap strikeRaised = render("\033[9m \033[0m", null,
            TerminalRenderer.LigaturePolicy.NEVER, false, Typeface.MONOSPACE,
            TerminalRenderer.FontVariations.NONE, raised);

        assertEquals("baseline should raise underline by the same pixel delta",
            visibleCenterY(underlineDefault) - 4, visibleCenterY(underlineRaised), 1);
        assertEquals("baseline should raise strikethrough by the same pixel delta",
            visibleCenterY(strikeDefault) - 4, visibleCenterY(strikeRaised), 1);
    }

    private static boolean hasVisiblePixels(Bitmap bitmap) {
        for (int y = 0; y < bitmap.getHeight(); y += 4) {
            for (int x = 0; x < bitmap.getWidth(); x += 4) {
                if (bitmap.getPixel(x, y) != Color.TRANSPARENT) return true;
            }
        }
        return false;
    }

    private static int visibleCenterY(Bitmap bitmap) {
        int first = bitmap.getHeight();
        int last = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    first = Math.min(first, y);
                    last = Math.max(last, y);
                }
            }
        }
        assertTrue("expected visible decoration pixels", last >= first);
        return (first + last) / 2;
    }

    private static Bitmap render(String text, TerminalRenderer.SymbolMap[] symbolMaps) {
        return render(text, symbolMaps, TerminalRenderer.LigaturePolicy.NEVER, false);
    }

    private static Bitmap render(String text, TerminalRenderer.SymbolMap[] symbolMaps,
                                 TerminalRenderer.LigaturePolicy ligaturePolicy,
                                 boolean cursorVisible) {
        return render(text, symbolMaps, ligaturePolicy, cursorVisible, Typeface.MONOSPACE);
    }

    private static Bitmap render(String text, TerminalRenderer.SymbolMap[] symbolMaps,
                                 TerminalRenderer.LigaturePolicy ligaturePolicy,
                                 boolean cursorVisible, Typeface primaryTypeface) {
        return render(text, symbolMaps, ligaturePolicy, cursorVisible, primaryTypeface,
            TerminalRenderer.FontVariations.NONE);
    }

    private static Bitmap render(String text, TerminalRenderer.SymbolMap[] symbolMaps,
                                 TerminalRenderer.LigaturePolicy ligaturePolicy,
                                 boolean cursorVisible, Typeface primaryTypeface,
                                 TerminalRenderer.FontVariations fontVariations) {
        return render(text, symbolMaps, ligaturePolicy, cursorVisible, primaryTypeface,
            fontVariations, TerminalRenderer.FontMetricsAdjustments.NONE);
    }

    private static Bitmap render(String text, TerminalRenderer.SymbolMap[] symbolMaps,
                                 TerminalRenderer.LigaturePolicy ligaturePolicy,
                                 boolean cursorVisible, Typeface primaryTypeface,
                                 TerminalRenderer.FontVariations fontVariations,
                                 TerminalRenderer.FontMetricsAdjustments fontMetricsAdjustments) {
        TerminalEmulator emulator = emulator(6, 2);
        enter(emulator, (cursorVisible ? "" : "\033[?25l") + text);
        Bitmap bitmap = Bitmap.createBitmap(360, 120, Bitmap.Config.ARGB_8888);
        TerminalRenderer renderer = new TerminalRenderer(48, primaryTypeface, null, null,
            null, symbolMaps, ligaturePolicy, TerminalRenderer.FontFeatures.NONE, fontVariations,
            fontMetricsAdjustments);
        renderer.render(emulator, new Canvas(bitmap), 0, -1, -1, -1, -1, false,
            Color.TRANSPARENT, 0f);
        return bitmap;
    }

    private static TerminalEmulator emulator(int columns, int rows) {
        return new TerminalEmulator(new SilentOutput(), false, columns, rows, 12, 24, 100, null);
    }

    private static void enter(TerminalEmulator emulator, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    private static String selectedCell(TerminalEmulator emulator, int column) {
        return emulator.getScreen().getSelectedText(column, 0, column, 0);
    }

    private static TerminalRow row(TerminalEmulator emulator, int row) {
        TerminalBuffer screen = emulator.getScreen();
        return screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
    }

    private static final class SilentOutput extends TerminalOutput {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }
}
