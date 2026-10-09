package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A finger dragged up or down a pane whose program asked for the mouse reaches the program as the
 * wheel, one notch per row travelled, in the direction the content follows the finger — and a pane
 * whose program did not ask gets nothing typed at it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewTouchScrollTest {

    private static final Pattern SGR_MOUSE = Pattern.compile("\u001b\\[<(\\d+);(\\d+);(\\d+)([Mm])");

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
        mEmulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { mWritten.write(data, offset, count); }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, false, 40, 20, 10, 20, 200, null);
        mView.setEmulatorForTest(mEmulator);
    }

    @Test
    public void aDragUpWithSgrMouseTrackingTurnsTheWheelDownOnceARow() {
        enter("\u001b[?1049h\u001b[?1000h\u001b[?1002h\u001b[?1006h");
        int rows = 5;
        drag(300f, 900f, 900f - rows * lineSpacing() - lineSpacing() / 2f);

        String sent = written();
        Matcher matcher = SGR_MOUSE.matcher(sent);
        int wheels = 0;
        while (matcher.find()) {
            assertEquals("only wheel-down notches for a drag up: " + printable(sent),
                TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, Integer.parseInt(matcher.group(1)));
            assertEquals("M", matcher.group(4));
            wheels++;
        }
        assertEquals("one notch per row travelled: " + printable(sent), rows, wheels);
    }

    @Test
    public void aDragDownTurnsTheWheelUp() {
        enter("\u001b[?1000h\u001b[?1006h");
        drag(300f, 400f, 400f + 3 * lineSpacing() + lineSpacing() / 2f);
        Matcher matcher = SGR_MOUSE.matcher(written());
        int wheels = 0;
        while (matcher.find()) {
            assertEquals(TerminalEmulator.MOUSE_WHEELUP_BUTTON, Integer.parseInt(matcher.group(1)));
            wheels++;
        }
        assertEquals(3, wheels);
    }

    @Test
    public void aMostlyVerticalDragDoesNotAlsoTurnTheSidewaysWheel() {
        enter("\u001b[?1000h\u001b[?1002h\u001b[?1006h");
        // A thumb never travels straight: a few cells of sideways drift on a long vertical drag.
        dragDiagonal(300f, 900f, 300f + 3 * fontWidth(), 900f - 6 * lineSpacing());
        Matcher matcher = SGR_MOUSE.matcher(written());
        while (matcher.find()) {
            int button = Integer.parseInt(matcher.group(1));
            assertTrue("sideways wheel " + button + " on a vertical drag",
                button != TerminalEmulator.MOUSE_WHEEL_LEFT && button != TerminalEmulator.MOUSE_WHEEL_RIGHT);
        }
    }

    @Test
    public void withoutMouseTrackingADragTypesNothing() {
        drag(300f, 900f, 900f - 5 * lineSpacing());
        assertFalse(printable(written()), SGR_MOUSE.matcher(written()).find());
        assertEquals("", written());
    }

    private void enter(String sequence) {
        byte[] bytes = sequence.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
        mWritten.reset();
    }

    private float lineSpacing() {
        return mView.mRenderer.mFontLineSpacing;
    }

    private float fontWidth() {
        return mView.mRenderer.mFontWidth;
    }

    private void drag(float x, float fromY, float toY) {
        dragDiagonal(x, fromY, x, toY);
    }

    /** A finger down, twenty moves 8 ms apart, and a slow lift (no fling). */
    private void dragDiagonal(float fromX, float fromY, float toX, float toY) {
        long down = SystemClock.uptimeMillis();
        long time = down;
        dispatch(MotionEvent.obtain(down, time, MotionEvent.ACTION_DOWN, fromX, fromY, 0));
        int steps = 20;
        for (int i = 1; i <= steps; i++) {
            time += 8;
            float x = fromX + (toX - fromX) * i / steps;
            float y = fromY + (toY - fromY) * i / steps;
            dispatch(MotionEvent.obtain(down, time, MotionEvent.ACTION_MOVE, x, y, 0));
        }
        // Rest before lifting so the lift carries no speed into a fling.
        for (int i = 0; i < 12; i++) {
            time += 16;
            dispatch(MotionEvent.obtain(down, time, MotionEvent.ACTION_MOVE, toX, toY, 0));
        }
        time += 16;
        dispatch(MotionEvent.obtain(down, time, MotionEvent.ACTION_UP, toX, toY, 0));
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
