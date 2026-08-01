package com.termux.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TermuxShellIntegrationInstallerTest {

    @Test
    public void installCreatesBothScriptsAndDoesNotRewriteMatchingFiles() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File destination = new File(context.getCacheDir(), "shell-integration-" + System.nanoTime());

        assertEquals(2, TermuxShellIntegrationInstaller.install(context, destination));

        File bash = new File(destination, "termux-launcher.bash");
        File zsh = new File(destination, "termux-launcher.zsh");
        assertTrue(bash.isFile());
        assertTrue(zsh.isFile());
        assertFalse(bash.canExecute());
        assertFalse(zsh.canExecute());
        assertTrue(readFile(bash).contains("OSC 133 shell integration for bash"));
        assertTrue(readFile(zsh).contains("OSC 133 shell integration for zsh"));

        long bashModified = bash.lastModified();
        long zshModified = zsh.lastModified();
        assertEquals(0, TermuxShellIntegrationInstaller.install(context, destination));
        assertEquals(bashModified, bash.lastModified());
        assertEquals(zshModified, zsh.lastModified());
    }

    @Test
    public void installRepairsManagedScriptAndPreservesUnrelatedFiles() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File destination = new File(context.getCacheDir(), "shell-integration-repair-" + System.nanoTime());
        assertTrue(destination.mkdirs());
        File bash = new File(destination, "termux-launcher.bash");
        File custom = new File(destination, "custom.sh");
        writeFile(bash, "stale");
        writeFile(custom, "user-owned");

        assertEquals(2, TermuxShellIntegrationInstaller.install(context, destination));

        assertTrue(readFile(bash).contains("PROMPT_COMMAND"));
        assertEquals("user-owned", readFile(custom));
    }

    private static String readFile(File file) throws IOException {
        byte[] content = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < content.length) {
                int count = input.read(content, offset, content.length - offset);
                if (count == -1) break;
                offset += count;
            }
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
