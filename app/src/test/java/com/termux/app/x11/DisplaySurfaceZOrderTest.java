package com.termux.app.x11;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.SurfaceView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

/**
 * The display's surface is a media overlay, so it stays above the wallpaper scene's plain
 * SurfaceView whatever order the two are recreated in, and still under the window's own chrome.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplaySurfaceZOrderTest {

    @Test
    public void displaySurfaceIsMediaOverlay() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SurfaceView view = new SurfaceView(activity);
        SurfaceView plain = new SurfaceView(activity);
        SurfaceView onTop = new SurfaceView(activity);
        onTop.setZOrderOnTop(true);
        X11PaneFrame.applyDisplaySurfaceZOrder(view);
        Field f = SurfaceView.class.getDeclaredField("mSubLayer");
        f.setAccessible(true);
        // Above a plain SurfaceView (the scene), below one on top of the window.
        assertTrue(f.getInt(view) > f.getInt(plain));
        assertTrue(f.getInt(view) < f.getInt(onTop));
    }

    @Test
    public void nullDisplayIsTolerated() {
        X11PaneFrame.applyDisplaySurfaceZOrder(null);
    }
}
