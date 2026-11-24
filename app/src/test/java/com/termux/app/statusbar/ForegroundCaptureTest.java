package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Save reads a pane's program from the system at save time, not from the pills' cache (#366). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ForegroundCaptureTest {

    private static WindowForegroundResolver.ProcSource proc(String stat, String cmdline) {
        return new WindowForegroundResolver.ProcSource() {
            @Override public String read(String path) {
                if (path.equals("/proc/10/stat")) return stat;
                if (path.equals("/proc/500/cmdline")) return cmdline;
                return null;
            }

            @Override public int[] pids() { return new int[0]; }
        };
    }

    @Test
    public void aStaleCacheEntryIsNotSaved() {
        // The pills' cache still says nvim; the shell has been idle since.
        WindowForegroundResolver resolver = new WindowForegroundResolver(null);
        resolver.applyOutput("10|fg|500|nvim\tnotes.md\n", Collections.singletonList(10), 1000L);
        assertEquals("nvim", resolver.get(10).processName);

        List<String> saved = WindowForegroundResolver.captureCommand(10,
            proc("10 (bash) S 1 10 10 0 10 0 0 0 0 0 0 0 0 0 0", null));
        assertTrue(saved.isEmpty());
    }

    @Test
    public void theProgramRunningNowIsSavedWithItsArguments() {
        List<String> saved = WindowForegroundResolver.captureCommand(10,
            proc("10 (bash) S 1 10 10 0 500 0 0 0 0 0 0 0 0 0 0", "nvim\0/h/foo.md\0"));
        assertEquals(Arrays.asList("nvim", "/h/foo.md"), saved);
    }

    @Test
    public void anUnreadablePaneSavesNothing() {
        assertTrue(WindowForegroundResolver.captureCommand(10, proc(null, null)).isEmpty());
    }
}
