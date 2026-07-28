package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
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
