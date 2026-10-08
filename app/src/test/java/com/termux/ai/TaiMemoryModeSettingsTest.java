package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The memory limits live in a file both processes read, not in the per-process preferences cache:
 * a change written by one process must reach the other's next read.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiMemoryModeSettingsTest {
    private Context context;
    private TaiSettings settings;
    private File file;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        settings = new TaiSettings(context);
        file = new File(new File(context.getFilesDir(), "tai"), TaiSettings.MEMORY_MODE_FILE);
        Files.deleteIfExists(file.toPath());
    }

    @Test
    public void relaxedIsTheDefaultWithNoFile() {
        assertFalse(file.exists());
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, settings.getMemoryMode());
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, TaiMemInfo.conditions(context).mode);
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, TaiSettings.memoryMode(null));
    }

    @Test
    public void unrestrictedRoundTripsThroughTheFileAndRelaxedRemovesIt() throws Exception {
        settings.setMemoryMode(TaiLoadBudget.MemoryMode.UNRESTRICTED);
        assertTrue(file.isFile());
        assertEquals("unrestricted", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(TaiLoadBudget.MemoryMode.UNRESTRICTED, settings.getMemoryMode());
        assertEquals(TaiLoadBudget.MemoryMode.UNRESTRICTED, TaiMemInfo.conditions(context).mode);

        settings.setMemoryMode(TaiLoadBudget.MemoryMode.RELAXED);
        assertFalse(file.exists());
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, settings.getMemoryMode());
    }

    /** What the runtime process sees when the settings screen, in the main process, changes the file. */
    @Test
    public void aChangeMadeByAnotherProcessIsReadOnTheNextLook() throws Exception {
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, TaiSettings.memoryMode(context));
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), "unrestricted".getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(1_700_000_000_000L));
        assertEquals(TaiLoadBudget.MemoryMode.UNRESTRICTED, TaiSettings.memoryMode(context));

        Files.delete(file.toPath());
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, TaiSettings.memoryMode(context));
    }

    @Test
    public void anUnreadableValueIsRelaxed() throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), "turbo".getBytes(StandardCharsets.UTF_8));
        assertTrue(file.setLastModified(1_700_000_100_000L));
        assertEquals(TaiLoadBudget.MemoryMode.RELAXED, settings.getMemoryMode());
    }
}
