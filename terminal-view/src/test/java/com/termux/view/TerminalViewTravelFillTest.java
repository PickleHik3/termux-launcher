package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * A travel toward a taller grid draws the transcript the settle's resize will reveal above the
 * rows, as far as the transcript reaches, and only on the normal screen; the alternate screen's
 * travel is the one the caller frosts throughout instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewTravelFillTest {

    private static final int ROWS = 20;

    private TerminalView mView;
    private TerminalEmulator mEmulator;

    @Before
    public void setUp() {
        mView = new TerminalView(RuntimeEnvironment.getApplication(), null);
        mView.setTerminalViewClient((TerminalViewClient) Proxy.newProxyInstance(
            TerminalViewClient.class.getClassLoader(), new Class<?>[] { TerminalViewClient.class },
            (proxy, method, args) -> defaultFor(method.getReturnType())));
        mView.setTextSize(24);
        // Exactly the grid's rows, with a few pixels of slack, the way updateSize would size it.
        mView.layout(0, 0, 1080, ROWS * line() + mView.mRenderer.mFontLineSpacingAndAscent + 3);
        mEmulator = new TerminalEmulator(new TerminalOutput() {
            @Override public void write(byte[] data, int offset, int count) { }
            @Override public void titleChanged(String oldTitle, String newTitle) { }
            @Override public void onCopyTextToClipboard(String text) { }
            @Override public void onPasteTextFromClipboard() { }
            @Override public void onBell() { }
            @Override public void onColorsChanged() { }
        }, false, 40, ROWS, 10, 20, 200, null);
        mView.setEmulatorForTest(mEmulator);
    }

    @Test
    public void aGrowthUnderAPromptAtTheBottomDrawsTheRevealedTranscriptAlready() {
        printLines(60);
        mView.setTravelDisplacement(5 * line(), 0.5f);
        assertEquals("the resize reveals five rows above, and the travel draws them",
            mEmulator.predictRowsOnlyResizeShift(ROWS + 5, true), mView.currentTravelFillRows());
        assertEquals(5, mView.currentTravelFillRows());
    }

    @Test
    public void nearTheHistoryTopOnlyTheTranscriptThereIsIsDrawn() {
        printLines(ROWS + 2);
        int transcript = mEmulator.getScreen().getActiveTranscriptRows();
        assertTrue("some history, less than the room gained: " + transcript,
            transcript > 0 && transcript < 5);
        mView.setTravelDisplacement(5 * line(), 1f);
        assertEquals(transcript, mView.currentTravelFillRows());
    }

    @Test
    public void aShrinkRevealsNothing() {
        printLines(60);
        mView.setTravelDisplacement(-5 * line(), 0.5f);
        assertEquals(0, mView.currentTravelFillRows());
    }

    @Test
    public void theAlternateScreenIsNeitherFilledNorPlaceable() {
        printLines(60);
        assertTrue(mView.canPlaceTravelRows());
        enter("\u001b[?1049h");
        assertFalse(mView.canPlaceTravelRows());
        mView.setTravelDisplacement(5 * line(), 0.5f);
        assertEquals(0, mView.currentTravelFillRows());
    }

    @Test
    public void nothingIsFilledOutsideATravel() {
        printLines(60);
        assertEquals(0, mView.currentTravelFillRows());
        mView.setTravelDisplacement(5 * line(), 0.5f);
        // No resize is owed, so the settle ends the travel on the spot: a spring-back.
        mView.settleTravelDisplacement(false);
        assertEquals(0, mView.currentTravelFillRows());
    }

    @Test
    public void aHeldFrostLastsExactlyAsLongAsItsTravel() {
        enter("\u001b[?1049h");
        mView.holdTravelFrost();
        assertFalse("no travel, nothing to hold", mView.isTravelFrostHeld());
        mView.setTravelDisplacement(5 * line(), 0f);
        mView.holdTravelFrost();
        assertTrue(mView.isTravelFrostHeld());
        mView.setTravelDisplacement(5 * line(), 0.8f);
        assertTrue("held through the drag", mView.isTravelFrostHeld());
        mView.settleTravelDisplacement(true);
        assertFalse("a spring-back thaws it too", mView.isTravelFrostHeld());
    }

    private int line() {
        return mView.mRenderer.mFontLineSpacing;
    }

    private void printLines(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) text.append("line ").append(i).append("\r\n");
        enter(text.toString());
    }

    private void enter(String sequence) {
        byte[] bytes = sequence.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytes, bytes.length);
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
