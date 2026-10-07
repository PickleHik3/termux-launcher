package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * resources/bin/tai and the script LauncherCtlApiServer installs are two copies of one program.
 * Other parts of the two have drifted (runtime tiers, logs); the speak command must not.
 */
public class TaiCliScriptTest {

    private static final String PREFIX = "/data/data/com.termux/files/usr";

    private static String resourceScript() throws Exception {
        String[] candidates = {
            "../resources/bin/tai",
            "resources/bin/tai",
            System.getProperty("user.dir") + "/../resources/bin/tai",
            System.getProperty("user.dir") + "/resources/bin/tai",
        };
        for (String path : candidates) {
            File file = new File(path);
            if (file.isFile()) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        }
        Assume.assumeTrue("resources/bin/tai not found from " + System.getProperty("user.dir"), false);
        return "";
    }

    /** The text from the line starting with {@code from} up to, not including, the line starting with {@code to}. */
    private static String section(String script, String from, String to) {
        int a = script.indexOf("\n" + from);
        int b = script.indexOf("\n" + to, a + 1);
        assertTrue("missing section " + from.trim(), a >= 0 && b > a);
        return script.substring(a, b);
    }

    @Test
    public void shebangUsesTheGivenBinPrefix() {
        assertTrue(LauncherCtlApiServer.taiCliScript(PREFIX).startsWith("#!" + PREFIX + "/sh\n"));
    }

    @Test
    public void speakCommandMatchesResourceCopy() throws Exception {
        String file = resourceScript();
        String java = LauncherCtlApiServer.taiCliScript(PREFIX);
        assertEquals(section(file, "  speak)", "  image)"), section(java, "  speak)", "  image)"));
    }

    @Test
    public void bothCopiesDocumentPipingInTheirHelp() throws Exception {
        for (String script : new String[] {resourceScript(), LauncherCtlApiServer.taiCliScript(PREFIX)}) {
            assertTrue(script.contains("  agent | tai speak [--whole|--stream]\n"));
            assertTrue(script.contains("speaks piped text sentence by sentence as the lines arrive"));
        }
    }
}
