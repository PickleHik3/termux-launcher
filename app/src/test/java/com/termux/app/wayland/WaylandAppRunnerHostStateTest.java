package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A killed host leaves its {@code wayland-0} file behind. The runner must call the host running
 * only when the service reports it, never because that file exists.
 */
public class WaylandAppRunnerHostStateTest {

    private static String runnerSource() throws IOException {
        Path path = Paths.get("src/main/java/com/termux/app/wayland/WaylandAppRunner.java");
        if (!Files.exists(path)) path = Paths.get("app").resolve(path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theSocketFileIsNeverReadAsTheHostRunning() throws IOException {
        String source = runnerSource();
        assertFalse("the runner must not look the socket file up",
            Pattern.compile("(socketPath\\(\\)|SOCKET_NAME)[^;]*\\.(exists|canRead|isFile)\\(")
                .matcher(source).find());
        assertFalse(Pattern.compile("new File\\([^;]*(socketPath\\(\\)|SOCKET_NAME)").matcher(source).find());
    }

    @Test
    public void runningIsSetOnlyFromTheHostsReportedState() throws IOException {
        String source = runnerSource();
        Matcher m = Pattern.compile("\\brunning = true;").matcher(source);
        int count = 0;
        int at = -1;
        while (m.find()) {
            count++;
            at = m.start();
        }
        assertEquals(1, count);
        int caseRunning = source.lastIndexOf("case ILinuxWindows.STATE_RUNNING:", at);
        int method = source.lastIndexOf("private void hostState(", at);
        assertTrue(method >= 0 && caseRunning > method);
    }
}
