package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.util.List;

public class X11AppLaunchEnvTest {

    @Test public void scaleLinesDefaultInTheShell() {
        List<String> lines = X11AppLaunchEnv.scaleLines(false, "1.5");
        assertEquals(5, lines.size());
        assertEquals("GDK_SCALE=${GDK_SCALE:-1}", lines.get(0));
        assertEquals("GDK_DPI_SCALE=${GDK_DPI_SCALE:-1.5}", lines.get(1));
        assertEquals("QT_SCALE_FACTOR=${QT_SCALE_FACTOR:-1.5}", lines.get(2));
    }

    @Test public void followAndDesktopsGetNoScale() {
        assertTrue(X11AppLaunchEnv.scaleLines(false, null).isEmpty());
        assertTrue(X11AppLaunchEnv.scaleLines(true, "2").isEmpty());
    }

    @Test public void anAppsOwnGraphicsReplacesTheDisplaysPick() {
        assertEquals("virgl", X11AppLaunchEnv.gpuPick(false, "turnip-zink", "virgl"));
        assertEquals("turnip-zink", X11AppLaunchEnv.gpuPick(false, "turnip-zink", null));
        assertEquals("turnip-zink", X11AppLaunchEnv.gpuPick(false, "turnip-zink", "software"));
        assertEquals("auto", X11AppLaunchEnv.gpuPick(false, "auto", "nonsense"));
    }

    @Test public void aDesktopFollowsTheDisplayGraphics() {
        assertEquals("turnip-zink", X11AppLaunchEnv.gpuPick(true, "turnip-zink", "virgl"));
        assertTrue(X11AppLaunchEnv.drawsInSoftware(false, true));
        assertFalse(X11AppLaunchEnv.drawsInSoftware(true, true));
        assertFalse(X11AppLaunchEnv.drawsInSoftware(false, false));
    }

    @Test public void theScriptKeepsAValueAlreadySet() throws Exception {
        org.junit.Assume.assumeTrue("needs sh", new File("/bin/sh").canExecute());
        StringBuilder script = new StringBuilder();
        for (String line : X11AppLaunchEnv.scaleLines(false, "2")) {
            script.append("export ").append(line).append('\n');
        }
        script.append("echo \"$GDK_SCALE|$GDK_DPI_SCALE|$QT_SCALE_FACTOR\"");
        ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", script.toString());
        builder.environment().remove("GDK_SCALE");
        builder.environment().put("GDK_DPI_SCALE", "0.5");
        builder.environment().remove("QT_SCALE_FACTOR");
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8).trim();
        process.waitFor();
        assertEquals("2|0.5|2", out);
    }

    @Test public void scaleReachesAContainerOnlyWhenSet() throws Exception {
        org.junit.Assume.assumeTrue("needs sh", new File("/bin/sh").canExecute());
        String words = "set -- " + String.join(" ", ProotDistro.FORWARDED_SCALE_ENV)
            + "; echo \"$#|$*\"";
        ProcessBuilder none = new ProcessBuilder("/bin/sh", "-c", words);
        for (String name : X11AppLaunchEnv.SCALE_VARIABLES) none.environment().remove(name);
        assertEquals("0|", run(none));
        ProcessBuilder set = new ProcessBuilder("/bin/sh", "-c", words);
        for (String name : X11AppLaunchEnv.SCALE_VARIABLES) set.environment().remove(name);
        set.environment().put("QT_SCALE_FACTOR", "1.5");
        assertEquals("2|-e QT_SCALE_FACTOR=1.5", run(set));
    }

    private static String run(ProcessBuilder builder) throws Exception {
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8).trim();
        process.waitFor();
        return out;
    }
}
