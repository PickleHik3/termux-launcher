package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Where the scene layer goes in the window: directly above the wallpaper backdrop, in the
 * backdrop's own parent. A layer under the backdrop's parent is painted over by the backdrop, and
 * the wallpaper stands still while the scene draws behind it.
 */
@RunWith(RobolectricTestRunner.class)
public class SceneLayerPlacementTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void aBackdropInsideTheGroundLayerPutsTheLayerInTheGroundLayerAboveIt() {
        FrameLayout container = new FrameLayout(mContext);
        FrameLayout ground = new FrameLayout(mContext);
        View backdrop = new View(mContext);
        View strip = new View(mContext);
        ground.addView(backdrop);
        ground.addView(strip);
        container.addView(ground);
        container.addView(new View(mContext));

        ViewGroup host = SceneController.hostFor(container, backdrop);

        assertSame(ground, host);
        assertEquals(1, SceneController.insertIndex(host, backdrop));
    }

    @Test
    public void aBackdropDirectlyInTheContainerKeepsTheLayerInTheContainerAboveIt() {
        FrameLayout container = new FrameLayout(mContext);
        View backdrop = new View(mContext);
        container.addView(new View(mContext));
        container.addView(backdrop);
        container.addView(new View(mContext));

        ViewGroup host = SceneController.hostFor(container, backdrop);

        assertSame(container, host);
        assertEquals(2, SceneController.insertIndex(host, backdrop));
    }

    @Test
    public void noBackdropPutsTheLayerAtTheBackOfTheContainer() {
        FrameLayout container = new FrameLayout(mContext);
        container.addView(new View(mContext));

        ViewGroup host = SceneController.hostFor(container, null);

        assertSame(container, host);
        assertEquals(0, SceneController.insertIndex(host, null));
    }
}
