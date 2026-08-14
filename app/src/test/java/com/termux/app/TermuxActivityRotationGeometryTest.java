package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * When the orientation geometry pass is allowed to run.
 *
 * <p>A rotation delivers {@code onConfigurationChanged} before the window is re-laid out, so the
 * decor view and the display metrics still describe the orientation being left. Running the pass
 * inline sized the accessory stack from that stale geometry and posted a terminal resize, then did
 * it again after the real layout — the two SIGWINCHes one rotation into landscape produced — and
 * left the accessory glass holding a crop of the outgoing frame.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityRotationGeometryTest {

    @Test
    public void aRotationDefersTheGeometryPassToTheNewLayout() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        assertFalse(activity.hasPendingOrientationGeometryPass());

        activity.onConfigurationChanged(landscape());

        assertTrue("the pass must wait for the layout that matches the new configuration",
            activity.hasPendingOrientationGeometryPass());
    }

    /**
     * Two configuration changes before a single layout — which a rotation through 180° produces —
     * must not queue two passes; the later one describes where the window ends up.
     */
    @Test
    public void backToBackRotationsCoalesceIntoOnePass() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);

        activity.onConfigurationChanged(landscape());
        activity.onConfigurationChanged(portrait());
        activity.onConfigurationChanged(landscape());

        assertTrue(activity.hasPendingOrientationGeometryPass());
        // The pre-draw listener clears the field as it fires; one run leaves nothing behind.
        activity.getWindow().getDecorView().getViewTreeObserver().dispatchOnPreDraw();
        assertFalse("a second queued pass would resize the terminal twice again",
            activity.hasPendingOrientationGeometryPass());
    }

    private static Configuration landscape() {
        Configuration configuration = new Configuration();
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        return configuration;
    }

    private static Configuration portrait() {
        Configuration configuration = new Configuration();
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        return configuration;
    }
}
