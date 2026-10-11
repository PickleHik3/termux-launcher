package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * A program that asked for SGR-Pixels (DECSET 1016) is told where a finger or pointer is in pixels
 * of the text area, 1-based, in the cell size it was told - so the pixel divides back to the cell
 * the view picked - and a program that did not still gets cells, unchanged.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalViewMousePixelsTest {

    private final ByteArrayOutputStream mWritten = new ByteArrayOutputStream();
    private TerminalView mView;
    private TerminalEmulator mEmulator;

    @Before
    public void setUp() {
        mView = new TerminalView(RuntimeEnvironment.getApplication(), null);
        mView.setTerminalViewClient((TerminalViewClient) Proxy.newProxyInstance(
            TerminalViewClient.class.getClassLoader(), new Class<?>[] { TerminalViewClient.class },
            (proxy, method, args) -> defaultFor(method.getReturnType())));
        mView.setTextSize(24);
        mView.layout(0, 0, 1080, 1600);
        TerminalRenderer renderer = mView.mRenderer;
        // The grid and cell size the view itself would give the session (see updateSize).
        int columns = (int) (1080 / renderer.mFontWidth);
        int rows = (1600 - renderer.mFontLineSpacingAndAscent) / renderer.mFontLineSpacing;
        mEmulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { mWritten.write(data, offset, count); }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, false, columns, rows, (int) renderer.getFontWidth(), renderer.getFontLineSpacing(), 200, null);
        mView.setEmulatorForTest(mEmulator);
    }

    @Test
    public void aTapReportsItsPixel() {
        enter("\u001b[?1003h\u001b[?1006h\u001b[?1016h");
        float x = xInCell(7, 0.6f);
        float y = yInCell(5, 0.3f);
        tap(x, y);
        String at = expectedPixel(x, y);
        assertEquals(printable("\u001b[<0;" + at + "M\u001b[<0;" + at + "m"), printable(written()));
    }

    @Test
    public void aTapInMouseModeReportsItsPixel() {
        enter("\u001b[?1003h\u001b[?1006h\u001b[?1016h");
        mView.setTouchMouseMode(true);
        float x = xInCell(12, 0.25f);
        float y = yInCell(9, 0.8f);
        tap(x, y);
        String at = expectedPixel(x, y);
        assertEquals(printable("\u001b[<0;" + at + "M\u001b[<0;" + at + "m"), printable(written()));
    }

    @Test
    public void aCornerHeldClickInMouseModeReportsTheLandingPixel() {
        enter("\u001b[?1000h\u001b[?1006h\u001b[?1016h");
        mView.setTouchMouseMode(true);
        float x = xInCell(3, 0.5f);
        float y = yInCell(2, 0.5f);
        long down = SystemClock.uptimeMillis();
        mView.setHoldExempt(true);
        dispatch(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
        dispatch(MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, x, y, 0));
        String at = expectedPixel(x, y);
        assertEquals(printable("\u001b[<0;" + at + "M\u001b[<0;" + at + "m"), printable(written()));
    }

    @Test
    public void aMouseModeDragReportsEachNewPixelOnce() {
        enter("\u001b[?1002h\u001b[?1006h\u001b[?1016h");
        mView.setTouchMouseMode(true);
        float x = xInCell(4, 0.1f);
        float y = yInCell(4, 0.5f);
        long down = SystemClock.uptimeMillis();
        dispatch(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
        mWritten.reset();
        // Three pixels right, inside the same cell, then the same spot again.
        dispatch(MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_MOVE, x + 3f, y, 0));
        dispatch(MotionEvent.obtain(down, down + 16, MotionEvent.ACTION_MOVE, x + 3f, y, 0));
        assertEquals(printable("\u001b[<32;" + expectedPixel(x + 3f, y) + "M"), printable(written()));
    }

    @Test
    public void withoutSgrPixelsAMouseModeDragReportsPerCell() {
        enter("\u001b[?1002h\u001b[?1006h");
        mView.setTouchMouseMode(true);
        float x = xInCell(4, 0.1f);
        float y = yInCell(4, 0.5f);
        long down = SystemClock.uptimeMillis();
        dispatch(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
        assertEquals(printable("\u001b[<0;5;5M"), printable(written()));
        mWritten.reset();
        dispatch(MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_MOVE, x + 3f, y, 0));
        assertEquals("", written());
    }

    @Test
    public void withoutSgrPixelsATapReportsItsCell() {
        enter("\u001b[?1000h\u001b[?1006h");
        tap(xInCell(7, 0.6f), yInCell(5, 0.3f));
        assertEquals(printable("\u001b[<0;8;6M\u001b[<0;8;6m"), printable(written()));
    }

    @Test
    public void aHoveringPointerIsReportedOnlyUnderAnyEventTracking() {
        enter("\u001b[?1002h\u001b[?1006h");
        float x = xInCell(6, 0.5f);
        float y = yInCell(3, 0.5f);
        hover(x, y);
        assertEquals("", written());

        enter("\u001b[?1003h");
        hover(x, y);
        hover(x + 2f, y);
        assertEquals("a second hover in the same cell is no news",
            printable("\u001b[<35;7;4M"), printable(written()));

        enter("\u001b[?1016h");
        hover(x + 2f, y);
        assertEquals(printable("\u001b[<35;" + expectedPixel(x + 2f, y) + "M"), printable(written()));
    }

    @Test
    public void aPixelDividesBackToItsCellWhateverTheDrawnWidth() {
        // A 17.6 px advance told to the program as 17: column 55 starts at 968 drawn pixels.
        int pixel = TerminalView.textAreaPixel(973f, 17.6f, 17, 60);
        assertEquals(55, pixel / 17);
        assertEquals(55 * 17 + 4, pixel);
        // Where the drawn cell is the told cell, the pixel is the device pixel.
        assertEquals(37, TerminalView.textAreaPixel(37.9f, 10f, 10, 8));
        // Clamped to the text area on both sides.
        assertEquals(0, TerminalView.textAreaPixel(-3f, 10f, 10, 8));
        assertEquals(79, TerminalView.textAreaPixel(500f, 10f, 10, 8));
    }

    /** The view x a fraction of the way into a column. */
    private float xInCell(int column, float fraction) {
        return mView.getHorizontalContentOffset() + (column + fraction) * mView.mRenderer.mFontWidth;
    }

    /** The view y a fraction of the way into a row. */
    private float yInCell(int row, float fraction) {
        return mView.getVerticalContentOffset() + mView.mRenderer.mFontLineSpacingAndAscent
            + (row + fraction) * mView.mRenderer.mFontLineSpacing;
    }

    /**
     * The "x;y" a program should get for view position (x, y): rows are drawn the size the program
     * is told, so y is the device pixel; columns are re-counted in the told width, so x divides
     * back to the column under the finger.
     */
    private String expectedPixel(float x, float y) {
        TerminalRenderer renderer = mView.mRenderer;
        float offsetX = x - mView.getHorizontalContentOffset();
        float offsetY = y - mView.getVerticalContentOffset() - renderer.mFontLineSpacingAndAscent;
        int cellWidth = mEmulator.getCellWidthPixels();
        int column = (int) (offsetX / renderer.mFontWidth);
        int pixelX = TerminalView.textAreaPixel(offsetX, renderer.mFontWidth, cellWidth, mEmulator.mColumns);
        assertEquals("the pixel divides back to the column", column, pixelX / cellWidth);
        if (renderer.mFontWidth == cellWidth) assertEquals((int) Math.floor(offsetX), pixelX);
        int pixelY = (int) Math.floor(offsetY);
        assertEquals(renderer.mFontLineSpacing, mEmulator.getCellHeightPixels());
        assertTrue(pixelY >= 0);
        return (pixelX + 1) + ";" + (pixelY + 1);
    }

    private void tap(float x, float y) {
        long down = SystemClock.uptimeMillis();
        dispatch(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0));
        dispatch(MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, x, y, 0));
    }

    private void hover(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        mView.onHoverEvent(event);
        event.recycle();
    }

    private void enter(String sequence) {
        byte[] bytes = sequence.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
        mWritten.reset();
    }

    private void dispatch(MotionEvent event) {
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mView.onTouchEvent(event);
        event.recycle();
    }

    private String written() {
        return new String(mWritten.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String printable(String s) {
        return s.replace("\u001b", "ESC");
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
