package com.termux.view;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.termux.terminal.KittyUnicodePlaceholder;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * The two draw paths, told apart by the canvas they are handed. A plain Robolectric canvas is the
 * software path, which also runs on a device whenever the pane is drawn without hardware
 * acceleration: it must keep drawing every row every frame — glyphs and images together, in one
 * walk — and keep reporting that it did. A canvas taken from a {@code RenderNode} is the node
 * path, where a row is only recorded again when something it draws from moved.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalRendererRowCacheTest {

    private static final int COLUMNS = 20;
    private static final int ROWS = 6;

    private static final TerminalSessionClient CLIENT = new TerminalSessionClient() {
        @Override public void onTextChanged(com.termux.terminal.TerminalSession changedSession) { }
        @Override public void onTitleChanged(com.termux.terminal.TerminalSession updatedSession) { }
        @Override public void onSessionFinished(com.termux.terminal.TerminalSession finishedSession) { }
        @Override public void onCopyTextToClipboard(com.termux.terminal.TerminalSession session, String text) { }
        @Override public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) { }
        @Override public void onBell(com.termux.terminal.TerminalSession session) { }
        @Override public void onColorsChanged(com.termux.terminal.TerminalSession session) { }
        @Override public void onTerminalCursorStateChange(boolean state) { }
        @Override public void setTerminalShellPid(com.termux.terminal.TerminalSession session, int pid) { }
        @Override public Integer getTerminalCursorStyle() { return null; }
        @Override public void logError(String tag, String message) { }
        @Override public void logWarn(String tag, String message) { }
        @Override public void logInfo(String tag, String message) { }
        @Override public void logDebug(String tag, String message) { }
        @Override public void logVerbose(String tag, String message) { }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { }
        @Override public void logStackTrace(String tag, Exception e) { }
    };

    private TerminalEmulator mEmulator;
    private TerminalRenderer mRenderer;
    private Canvas mCanvas;

    @Before
    public void setUp() {
        mEmulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, true, COLUMNS, ROWS, 10, 10, ROWS * 2, CLIENT);
        mRenderer = new TerminalRenderer(24, Typeface.MONOSPACE, Typeface.MONOSPACE);
        mCanvas = new Canvas(Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888));
    }

    private void enter(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
    }

    private void render() {
        mRenderer.render(mEmulator, mCanvas, 0, -1, -1, -1, -1, false, 0, 0f);
    }

    @Test
    public void aSoftwareCanvasKeepsDrawingEveryVisibleRow() {
        enter("hello\r\nworld\r\n");

        render();
        assertEquals(ROWS, mRenderer.rowsRecordedLastFrame());
        render();
        assertEquals("nothing on this path is replayed", ROWS,
            mRenderer.rowsRecordedLastFrame());
        assertEquals("and no row recordings are held", 0, mRenderer.cachedRowCount());
    }

    @Test
    public void aSoftwareCanvasDrawsAPlaceholderRowsImageInlineWithItsGlyphs() {
        // U+10EEEE, the kitty unicode placeholder, with the text of the row beside it.
        enter("\uD83B\uDEEEinfo\r\n");

        render();
        render();

        assertEquals("every row is still drawn in one pass", ROWS,
            mRenderer.rowsRecordedLastFrame());
        assertEquals("and none of them records images on their own", 0,
            mRenderer.imageRowsRecordedLastFrame());
    }

    @Test
    public void anExtraRowIsDrawnDuringASmoothScroll() {
        // Enough output to push a row into the transcript, so there is a row above the screen.
        for (int i = 0; i < ROWS + 2; i++) enter("row " + i + "\r\n");

        mRenderer.render(mEmulator, mCanvas, -1, -1, -1, -1, -1, false, 0, 0f, 1);

        assertEquals(ROWS + 1, mRenderer.rowsRecordedLastFrame());
    }

    /**
     * A placeholder cell addressing image 3, cell (0,0): the foreground colour carries the low
     * image-id bits and the two diacritics carry the row and the column, so this is the smallest
     * grapheme the renderer will decode.
     */
    private static final String PLACEHOLDER_CELL = "\033[38;5;3m"
        + new String(Character.toChars(KittyUnicodePlaceholder.CODE_POINT))
        + "̅̅";

    /** Draw through a real {@link android.graphics.RecordingCanvas}, which is the node path. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private void renderThroughNodes() {
        android.graphics.RenderNode node = new android.graphics.RenderNode("test");
        Canvas canvas = node.beginRecording(400, 400);
        try {
            mRenderer.render(mEmulator, canvas, 0, -1, -1, -1, -1, false, 0, 0f);
        } finally {
            node.endRecording();
        }
    }

    /**
     * The whole point of the third row node: an animation's row keeps its glyph recording, and it
     * keeps its image recording too on every frame the image did not actually move. Before the
     * image generation existed this row was re-recorded on every frame the display drew, which on
     * a 120 Hz panel is an order of magnitude more often than a GIF changes frame.
     */
    @Test
    @Config(sdk = Build.VERSION_CODES.Q)
    public void aPlaceholderRowIsRecordedAgainOnlyWhenItsImageMoves() {
        enter(PLACEHOLDER_CELL);

        renderThroughNodes();
        assertEquals("the first frame has to record it", 1,
            mRenderer.imageRowsRecordedLastFrame());

        renderThroughNodes();
        assertEquals("nothing moved, so the image node is replayed", 0,
            mRenderer.imageRowsRecordedLastFrame());
        assertEquals("and so is the glyph node", 0, mRenderer.rowsRecordedLastFrame());

        // The image the cell has been addressing all along arrives. Not one char of the row moved.
        enter("\033_Gi=3,a=t,q=2,f=24,s=2,v=2;AAAAAAAAAAAAAAAA\033\\");

        renderThroughNodes();
        assertEquals("the frame its image lands is the frame it is drawn", 1,
            mRenderer.imageRowsRecordedLastFrame());
        assertEquals("still without re-shaping its text", 0, mRenderer.rowsRecordedLastFrame());

        renderThroughNodes();
        assertEquals(0, mRenderer.imageRowsRecordedLastFrame());
    }

    @Test
    public void releasingARendererThatNeverDrewIsHarmless() {
        mRenderer.release();
        render();
        mRenderer.release();

        assertEquals(0, mRenderer.cachedRowCount());
        assertEquals(0, mRenderer.rowsRecordedLastFrame());
        assertEquals(0, mRenderer.imageRowsRecordedLastFrame());
    }
}
