package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A socket file is not a server: only one that answers counts, and a dead one is swept away with
 * its lock, while anything that is not an X display socket is never touched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11SocketsTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File tmp;
    private File dir;

    @Before public void prefix() throws IOException {
        tmp = temp.newFolder("usr", "tmp");
        dir = new File(tmp, ".X11-unix");
        assertTrue(dir.mkdirs());
    }

    private File socket(String name) throws IOException {
        File f = new File(dir, name);
        assertTrue(f.createNewFile());
        return f;
    }

    private File lock(int n) throws IOException {
        File f = new File(tmp, ".X" + n + "-lock");
        assertTrue(f.createNewFile());
        return f;
    }

    private static X11Sockets.Probe answering(String... names) {
        List<String> up = Arrays.asList(names);
        return s -> up.contains(s.getName());
    }

    @Test public void deadSocketIsRemovedWithItsLock() throws IOException {
        File x0 = socket("X0");
        File lock = lock(0);
        assertEquals(Collections.emptyList(), X11Sockets.liveDisplays(dir, answering()));
        assertFalse(x0.exists());
        assertFalse(lock.exists());
        assertFalse(X11Autostart.serverRunning(dir, answering()));
    }

    @Test public void liveSocketIsKeptAndDeadOneBesideItGoes() throws IOException {
        File x0 = socket("X0");
        File x1 = socket("X1");
        File lock0 = lock(0);
        File lock1 = lock(1);
        assertEquals(Collections.singletonList(1), X11Sockets.liveDisplays(dir, answering("X1")));
        assertFalse(x0.exists());
        assertFalse(lock0.exists());
        assertTrue(x1.exists());
        assertTrue(lock1.exists());
    }

    @Test public void answeringSocketDeletesNothing() throws IOException {
        File x0 = socket("X0");
        File lock = lock(0);
        assertEquals(Collections.singletonList(0), X11Sockets.liveDisplays(dir, answering("X0")));
        assertTrue(x0.exists());
        assertTrue(lock.exists());
        assertTrue(X11Autostart.serverRunning(dir, answering("X0")));
    }

    @Test public void otherNamesAreLeftAlone() throws IOException {
        File bus = socket("termux-launcher-bus");
        File odd = socket("Xorg");
        File unrelatedLock = new File(tmp, ".X9-lock");
        assertTrue(unrelatedLock.createNewFile());
        assertEquals(Collections.emptyList(), X11Sockets.liveDisplays(dir, answering()));
        assertTrue(bus.exists());
        assertTrue(odd.exists());
        assertTrue(unrelatedLock.exists());
    }

    @Test public void numbersComeBackSorted() throws IOException {
        socket("X10");
        socket("X2");
        assertEquals(Arrays.asList(2, 10),
            X11Sockets.liveDisplays(dir, answering("X10", "X2")));
    }
}
