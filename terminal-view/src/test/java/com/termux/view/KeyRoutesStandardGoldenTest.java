package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.termux.terminal.CapturingSession;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The bytes the Standard engine (Java core) writes for every key route of {@link KeyRouteTable}, kept
 * in {@code src/test/resources/key-routes-standard.golden}. Standard's input path must not change, so
 * a difference here is a regression unless the change was meant to touch Standard.
 * <p>
 * To regenerate after an intended change, copy {@code build/key-routes/standard.actual} (written by
 * a failing run) over the golden file.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyRoutesStandardGoldenTest {

    static final String GOLDEN = "key-routes-standard.golden";

    @After
    public void restore() {
        CapturingSession.inputPropertyForTests(null);
    }

    static List<String> goldenLines() throws IOException {
        try (InputStream in = KeyRoutesStandardGoldenTest.class.getClassLoader().getResourceAsStream(GOLDEN)) {
            assertTrue(GOLDEN + " is missing from src/test/resources", in != null);
            List<String> lines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) lines.add(line);
            return lines;
        }
    }

    @Test
    public void standardWritesTheCommittedBytesForEveryRoute() throws IOException {
        List<String> actual = new ArrayList<>();
        for (KeyRouteTable.Result r : KeyRouteTable.run(false, null)) actual.add(r.line());
        List<String> golden = goldenLines();
        for (int i = 0; i < Math.min(actual.size(), golden.size()); i++) {
            if (!actual.get(i).equals(golden.get(i))) {
                writeActual(actual);
                assertEquals("row " + (i + 1) + " differs; if meant, copy build/key-routes/standard.actual over the golden",
                    golden.get(i), actual.get(i));
            }
        }
        if (actual.size() != golden.size()) {
            writeActual(actual);
            fail("golden has " + golden.size() + " rows, the table now has " + actual.size()
                + "; if meant, copy build/key-routes/standard.actual over the golden");
        }
    }

    /** Written on every run, so a first or intended regeneration is a copy. */
    private static void writeActual(List<String> lines) throws IOException {
        Path out = Paths.get("build", "key-routes", "standard.actual");
        Files.createDirectories(out.getParent());
        Files.write(out, lines, StandardCharsets.UTF_8);
    }
}
