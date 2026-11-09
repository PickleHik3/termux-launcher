package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.LayoutFixtures;
import com.termux.app.wall.PageRef;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Pair 3 of the motion audit: where the travel draws the terminal's rows while the grid waits
 * ({@link TerminalView#predictResizeDisplacementPx}) is where the settle's resize puts them, for
 * the height change a crossing from Home to the Terminal makes on every layout fixture (Home's
 * rest takes no room, the Terminal's rest with its keyboard up takes
 * {@code StackLayout.restReservationPx}).
 *
 * <p>The real resize is {@code updateSize}'s arithmetic without a session, which it needs to
 * reach the PTY: the rows {@link TerminalView#rowsFor} gives the new height, the buffer resized
 * to them (where the prompt's row lands), and the grid drawn at
 * {@link TerminalView#verticalContentOffsetPx}. In {@code com.termux.view} for the
 * package-private prediction; in the app's tests for the layout fixtures.</p>
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TerminalResizeParityTest {

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final int COLUMNS = 40;

    private final LayoutFixtures.Fixture f;

    public TerminalResizeParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private static TerminalView view() {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient((TerminalViewClient) Proxy.newProxyInstance(
            TerminalViewClient.class.getClassLoader(), new Class<?>[] {TerminalViewClient.class},
            (proxy, method, args) -> defaultFor(method.getReturnType())));
        view.setTextSize(28);
        return view;
    }

    private static TerminalEmulator emulator(int rows) {
        return new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, false, COLUMNS, rows, 10, 20, 2000, null);
    }

    /** {@code lines} numbered lines, the prompt left on the last row: a busy shell. */
    private static void fill(TerminalEmulator emulator, int lines) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines; i++) text.append(i == 0 ? "" : "\r\n").append("line ").append(i);
        byte[] bytes = text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    /**
     * Predicts the move a resize from {@code fromPx} to {@code toPx} makes, then makes it the
     * way the settle does, and answers {predicted, real}.
     */
    private static float[] predictedAndReal(int fromPx, int toPx, int textTopInsetPx) {
        TerminalView view = view();
        int line = view.mRenderer.mFontLineSpacing;
        int ascent = view.mRenderer.mFontLineSpacingAndAscent;
        view.setTextTopInsetPx(textTopInsetPx);
        int fromRows = TerminalView.rowsFor(fromPx, textTopInsetPx, ascent, line);
        int toRows = TerminalView.rowsFor(toPx, textTopInsetPx, ascent, line);
        TerminalEmulator emulator = emulator(fromRows);
        fill(emulator, 3 * fromRows);
        view.setEmulatorForTest(emulator);

        float predicted = view.predictResizeDisplacementPx(fromPx, toPx);

        int promptBefore = emulator.getCursorRow();
        int fromTop = TerminalView.verticalContentOffsetPx(fromPx, textTopInsetPx,
            fromRows * line + ascent);
        emulator.resize(COLUMNS, toRows, 10, 20);
        int shift = emulator.getCursorRow() - promptBefore;
        int toTop = TerminalView.verticalContentOffsetPx(toPx, textTopInsetPx,
            toRows * line + ascent);
        return new float[] {predicted, toTop + shift * line - fromTop};
    }

    /** The Terminal's height on Home's rest and on its own with the keyboard up. */
    private int[] crossingHeights(boolean dockFloating) {
        int from = f.heightPx - f.restReservationPx(PageRef.HOME, false, dockFloating);
        int to = f.heightPx - f.restReservationPx(PageRef.TERMINAL, true, dockFloating);
        return new int[] {from, to};
    }

    private void assertPredictionIsTheResize(int textTopInsetPx) {
        for (boolean dockFloating : new boolean[] {false, true}) {
            int[] heights = crossingHeights(dockFloating);
            String what = f + (dockFloating ? ", Floating" : ", Docked") + ", " + heights[0]
                + " -> " + heights[1] + " px, text inset " + textTopInsetPx;
            assertTrue(what + ": the crossing changes the height", heights[0] != heights[1]);
            float[] there = predictedAndReal(heights[0], heights[1], textTopInsetPx);
            assertEquals(what + " toward the Terminal", there[1], there[0], 0f);
            float[] back = predictedAndReal(heights[1], heights[0], textTopInsetPx);
            assertEquals(what + " back to Home", back[1], back[0], 0f);
        }
    }

    @Test
    public void theTravelDrawsTheRowsWhereTheSettlesResizePutsThem() {
        assertPredictionIsTheResize(0);
    }

    /**
     * A pane Full screen lifts under the camera keeps its text clear of it (pong: 92 px). The
     * prediction reads the same rows and offset as the resize below that inset (#190: it used to
     * ignore it, -872 px predicted against -921 px real on pong).
     */
    @Test
    public void underTheCamerasTextInsetToo() {
        assertPredictionIsTheResize(92);
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
