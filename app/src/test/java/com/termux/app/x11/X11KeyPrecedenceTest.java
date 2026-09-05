package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Who gets a hardware key while the Display page is showing.
 *
 * <p>The page's own view takes focus so an X client can be typed into, which means the terminal's
 * key pipeline never runs and the launcher's bindings would be lost. The page therefore offers
 * every key to the launcher first — but only its own chord space, both Ctrl and Alt held, is
 * claimed. Everything else is the display's, because a Linux desktop that cannot receive Ctrl+C
 * or Alt+Tab is not usable.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11KeyPrecedenceTest {

    /** Records what the page offered, and claims whatever the test says the launcher wants. */
    private static final class RecordingHost implements X11PaneFrame.Host {
        final List<KeyEvent> offered = new ArrayList<>();
        boolean claim;

        @Override public void startDisplay() { }
        @Override public boolean consumeLauncherKey(android.view.KeyEvent event) {
            offered.add(event);
            return claim;
        }
    }

    /**
     * The frame on its own, without the display view its layout carries: that view binds the
     * native server on construction, so it cannot exist on the JVM — and the precedence being
     * tested lives entirely in the frame's own dispatch.
     */
    private static X11PaneFrame page(Activity activity) {
        return new X11PaneFrame(activity);
    }

    private static KeyEvent stroke(int keyCode, int meta) {
        return new KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, meta);
    }

    @Test public void everyKeyIsOfferedToTheLauncherFirst() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame frame = page(activity);
        RecordingHost host = new RecordingHost();
        frame.setHost(host);

        frame.dispatchKeyEvent(stroke(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON));

        assertEquals(1, host.offered.size());
        assertEquals(KeyEvent.KEYCODE_C, host.offered.get(0).getKeyCode());
    }

    @Test public void aClaimedChordNeverReachesTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame frame = page(activity);
        RecordingHost host = new RecordingHost();
        host.claim = true;
        frame.setHost(host);

        assertTrue("a claimed chord is spent here",
            frame.dispatchKeyEvent(stroke(KeyEvent.KEYCODE_V,
                KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON)));
    }

    @Test public void anUnclaimedKeyFallsThroughToTheDisplay() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame frame = page(activity);
        RecordingHost host = new RecordingHost();
        host.claim = false;
        frame.setHost(host);

        // Not consumed here, so the view hierarchy below — the display's own view in a real
        // page — sees it. The page never swallows a key it was not given.
        assertFalse(frame.dispatchKeyEvent(stroke(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)));
        assertEquals(1, host.offered.size());
    }

    @Test public void aPageWithNoHostLetsEveryKeyThrough() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame frame = page(activity);

        assertFalse(frame.dispatchKeyEvent(stroke(KeyEvent.KEYCODE_V,
            KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON)));
    }
}
