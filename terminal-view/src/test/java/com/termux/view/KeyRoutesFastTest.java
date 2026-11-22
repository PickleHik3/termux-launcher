package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.terminal.CapturingSession;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Every key route on the Fast engine ends in the library encoder. Runs only in builds made with
 * {@code -Pghostty=true} (the host library is then on the test's system property); elsewhere it
 * returns at once, like the terminal-emulator tests that drive the same library.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyRoutesFastTest {

    private static boolean hostLibrary() {
        return System.getProperty("termux.ghostty.libDir") != null;
    }

    @After
    public void restore() {
        CapturingSession.inputPropertyForTests(null);
    }

    /**
     * The bytes each route writes equal what a second Fast emulator, in the same state, gives for the
     * library event the route is expected to build: each route builds the right event.
     */
    @Test
    public void everyRouteBuildsTheEventItShould() {
        if (!hostLibrary()) return;
        List<KeyRouteTable.Result> actual = KeyRouteTable.run(true, null);
        List<KeyRouteTable.Result> expected = KeyRouteTable.reference();
        assertEquals(expected.size(), actual.size());
        List<String> wrong = new ArrayList<>();
        int nonEmpty = 0;
        for (int i = 0; i < actual.size(); i++) {
            KeyRouteTable.Result a = actual.get(i), e = expected.get(i);
            assertEquals(e.id(), a.id());
            if (!a.bytes.isEmpty()) nonEmpty++;
            if (!a.bytes.equals(e.bytes)) wrong.add(a.id() + ": route wrote " + KeyRouteTable.printable(a.bytes)
                + ", expected event gives " + KeyRouteTable.printable(e.bytes));
        }
        assertTrue("table wrote almost nothing: " + nonEmpty, nonEmpty > actual.size() / 2);
        assertTrue(wrong.size() + " of " + actual.size() + " rows differ, first:\n"
            + String.join("\n", wrong.subList(0, Math.min(25, wrong.size()))), wrong.isEmpty());
    }

    /** debug.tl.terminal_input=java puts the Java encoders back: the bytes are Standard's, row for row. */
    @Test
    public void theJavaPropertyRestoresStandardsBytes() throws IOException {
        if (!hostLibrary()) return;
        List<KeyRouteTable.Result> actual = KeyRouteTable.run(true, "java");
        List<String> golden = KeyRoutesStandardGoldenTest.goldenLines();
        assertEquals(golden.size(), actual.size());
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < actual.size(); i++)
            if (!actual.get(i).line().equals(golden.get(i))) wrong.add(golden.get(i) + "  <>  " + actual.get(i).line());
        assertTrue(wrong.size() + " rows differ from Standard's golden, first:\n"
            + String.join("\n", wrong.subList(0, Math.min(25, wrong.size()))), wrong.isEmpty());
    }
}
