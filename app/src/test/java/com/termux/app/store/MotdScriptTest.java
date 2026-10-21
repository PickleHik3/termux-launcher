package com.termux.app.store;

import androidx.annotation.NonNull;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The welcome {@code motd.sh} prints, run by the host's {@code sh} at a terminal width: side by
 * side where that fits, else stacked in a box that spans the terminal (#103: a Home widget or a
 * phone's Workspace pane was left with an empty strip on the right), and never wider than the
 * terminal. Past the side-by-side threshold the box keeps its own width, however wide the screen.
 * Skipped where there is no {@code sh}.
 */
public class MotdScriptTest {

    /** The side-by-side box's outer width with the rows the script ships (all of them shown). */
    private static final int SIDE_BY_SIDE_WIDTH = 66;

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    private File script;
    private File home;
    private File bin;

    @Before public void install() throws IOException {
        Assume.assumeTrue("needs sh", new File("/bin/sh").canExecute());
        Path asset = Path.of("src/main/assets/motd.sh");
        if (!Files.exists(asset)) asset = Path.of("app/src/main/assets/motd.sh");
        script = folder.newFile("motd.sh");
        Files.copy(asset, script.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        home = folder.newFolder("home");
        // Every row the Termux edition shows: the commands it asks for are on the path.
        bin = folder.newFolder("bin");
        for (String tool : new String[] {"pkg", "termux-setup-storage", "termux-change-repo"}) {
            File stub = new File(bin, tool);
            Files.write(stub.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            assertTrue(stub.setExecutable(true));
        }
    }

    @Test public void at40TheStackedBoxSpansTheTerminal() throws Exception {
        assertBoxWidth(40, 40);
    }

    @Test public void at53TheStackedBoxSpansTheTerminal() throws Exception {
        assertBoxWidth(53, 53);
    }

    @Test public void at80TheSideBySideBoxKeepsItsOwnWidth() throws Exception {
        assertBoxWidth(80, SIDE_BY_SIDE_WIDTH);
    }

    @Test public void at200TheBoxDoesNotSprawl() throws Exception {
        assertBoxWidth(200, SIDE_BY_SIDE_WIDTH);
    }

    @Test public void justBelowTheSideBySideThresholdTheStackedBoxStopsShortOfIt() throws Exception {
        assertBoxWidth(SIDE_BY_SIDE_WIDTH - 1, SIDE_BY_SIDE_WIDTH - 1);
        assertBoxWidth(SIDE_BY_SIDE_WIDTH, SIDE_BY_SIDE_WIDTH);
    }

    /**
     * At {@code columns}, every line fits the terminal, and every line of the box and of the
     * guide's card is {@code expected} wide.
     */
    private void assertBoxWidth(int columns, int expected) throws Exception {
        List<String> lines = run(columns);
        assertFalse("printed something at " + columns, lines.isEmpty());
        int boxed = 0;
        for (String line : lines) {
            int width = line.codePointCount(0, line.length());
            assertTrue("fits " + columns + " columns: [" + line + "]", width <= columns);
            if (isBoxLine(line)) {
                boxed++;
                assertEquals("box line at " + columns + ": [" + line + "]", expected, width);
            }
        }
        assertTrue("a box at " + columns, boxed > 0);
    }

    private static boolean isBoxLine(@NonNull String line) {
        if (line.isEmpty()) return false;
        char first = line.charAt(0);
        return first == '╭' || first == '│' || first == '├' || first == '╰';
    }

    /** The script's lines at {@code columns}, its escapes (colours, the guide's link) taken out. */
    @NonNull private List<String> run(int columns) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", script.getAbsolutePath()).directory(home);
        Map<String, String> env = builder.environment();
        env.clear();
        env.put("PATH", bin.getAbsolutePath() + File.pathSeparator + "/usr/bin:/bin");
        env.put("HOME", home.getAbsolutePath());
        env.put("PREFIX", "/data/data/com.termux/files/usr");
        env.put("COLUMNS", Integer.toString(columns));
        env.put("NO_COLOR", "1");
        env.put("LANG", "C.UTF-8");
        env.put("LC_ALL", "C.UTF-8");
        builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(process.getInputStream(), out);
        assertTrue("motd.sh hung", process.waitFor(30, TimeUnit.SECONDS));
        assertEquals("motd.sh exit: " + out.toString("UTF-8"), 0, process.exitValue());
        String text = out.toString("UTF-8")
            .replaceAll("\u001b\\]8;;[^\u001b]*\u001b\\\\", "")
            .replaceAll("\u001b\\[[0-9;]*m", "");
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) lines.add(line);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
            lines.remove(lines.size() - 1);
        return lines;
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
    }
}
