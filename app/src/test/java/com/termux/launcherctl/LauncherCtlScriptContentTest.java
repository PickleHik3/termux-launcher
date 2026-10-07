package com.termux.launcherctl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The CLI scripts are rewritten only when what is on disk differs from what this build would
 * write; this pins the content half of that test.
 */
@RunWith(RobolectricTestRunner.class)
public class LauncherCtlScriptContentTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final byte[] SCRIPT = "#!/bin/sh\necho tai\n".getBytes(StandardCharsets.UTF_8);

    @Test
    public void identicalBytesAreUpToDate() throws IOException {
        File file = write("tai", SCRIPT);
        assertTrue(LauncherCtlApiServer.hasContent(file, SCRIPT));
    }

    @Test
    public void aMissingFileIsNot() {
        assertFalse(LauncherCtlApiServer.hasContent(new File(folder.getRoot(), "absent"), SCRIPT));
    }

    @Test
    public void sameLengthDifferentBytesIsNot() throws IOException {
        byte[] other = SCRIPT.clone();
        other[other.length - 2] = 'x';
        File file = write("tai", other);
        assertFalse(LauncherCtlApiServer.hasContent(file, SCRIPT));
    }

    @Test
    public void aShorterOrLongerFileIsNot() throws IOException {
        assertFalse(LauncherCtlApiServer.hasContent(write("short", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8)), SCRIPT));
        byte[] longer = new byte[SCRIPT.length + 1];
        System.arraycopy(SCRIPT, 0, longer, 0, SCRIPT.length);
        longer[SCRIPT.length] = '\n';
        assertFalse(LauncherCtlApiServer.hasContent(write("long", longer), SCRIPT));
    }

    @Test
    public void aDirectoryIsNot() throws IOException {
        assertFalse(LauncherCtlApiServer.hasContent(folder.newFolder("dir"), new byte[0]));
    }

    private File write(String name, byte[] content) throws IOException {
        File file = new File(folder.getRoot(), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content);
        }
        return file;
    }
}
