package com.termux.app;

import android.content.Context;
import android.graphics.RectF;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class LauncherAzGestureFxViewTest {

    @Test
    public void focusRingVisibility_tracksIconsOnlyAndDoesNotDependOnOverflow() {
        Context context = ApplicationProvider.getApplicationContext();
        LauncherAzGestureFxView view = new LauncherAzGestureFxView(context);
        view.layout(0, 0, 1080, 240);
        view.setFocusedIconRingEnabled(true);
        RectF bounds = new RectF(100, 40, 180, 120);

        view.updateDrag(true, 140, bounds,
            LauncherAzGestureFxView.InteractionMode.LETTER_TRACK);
        assertEquals(View.GONE, view.getVisibility());

        view.updateDrag(true, 140, bounds,
            LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED);
        assertEquals(View.VISIBLE, view.getVisibility());

        view.updateDrag(true, 140, null,
            LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED);
        assertEquals(View.GONE, view.getVisibility());
    }
}
