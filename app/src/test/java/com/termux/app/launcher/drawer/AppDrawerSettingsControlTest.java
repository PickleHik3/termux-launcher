package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import com.termux.R;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerSettingsControlTest {
    @Test public void controlIsAccessibleFortyEightDpAndOwnsFrozenRegion() {
        AppDrawerContentView content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        View control = find(content, RuntimeEnvironment.getApplication().getString(
            R.string.settings_app_drawer_settings_description));
        assertNotNull(control);
        float density = content.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(48 * density), control.getLayoutParams().width);
        assertEquals(Math.round(48 * density), control.getLayoutParams().height);
        AtomicInteger navigations = new AtomicInteger();
        content.setCallbacks(new AppDrawerContentView.Callbacks() {
            public void onContentCloseDragBegin(float y) {}
            public void onContentCloseDragUpdate(float y) {}
            public void onContentCloseDragEnd(float v) {}
            public void onContentCloseDragCancel() {}
            public void onDrawerSettingsRequested() { navigations.incrementAndGet(); }
        });
        control.performClick();
        assertEquals(1, navigations.get());
        assertEquals(AppDrawerTouchRegions.Region.CONTROL, AppDrawerTouchRegions.resolve(5, 5,
            new Frame(0, 0, 100, 100), null, new Frame(0, 0, 48, 48), true, false));
    }
    private static View find(View root, String description) {
        if (root.getContentDescription() != null
            && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View match = find(((ViewGroup) root).getChildAt(i), description);
            if (match != null) return match;
        }
        return null;
    }
}
