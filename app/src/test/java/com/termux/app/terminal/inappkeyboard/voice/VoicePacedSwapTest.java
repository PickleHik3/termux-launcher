package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The paced swap: each erase its own write, then the text in one, then done. */
public class VoicePacedSwapTest {

    @Test
    public void everyEraseIsItsOwnWriteThenTheTextInOne() {
        List<String> writes = new ArrayList<>();
        boolean[] done = {false};
        new VoicePacedSwap(3, "Run the tests.", writes::add, () -> done[0] = true, 0L).run();
        assertEquals(Arrays.asList("\u007f", "\u007f", "\u007f", "Run the tests."), writes);
        assertTrue(done[0]);
    }

    @Test
    public void nothingToEraseOrTypeStillFinishes() {
        List<String> writes = new ArrayList<>();
        boolean[] done = {false};
        new VoicePacedSwap(0, "", writes::add, () -> done[0] = true, 0L).run();
        assertTrue(writes.isEmpty());
        assertTrue(done[0]);
    }
}
