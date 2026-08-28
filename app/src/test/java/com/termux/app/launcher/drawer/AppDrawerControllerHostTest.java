package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** The drawer's half of its seam, driven by a host with no activity behind it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerControllerHostTest {

    /**
     * The three intake channels route through the activity before the plane exists; a drawer whose
     * plane is not in the layout must claim nothing on any of them, and must not touch the dock.
     */
    @Test
    public void aDrawerWithNoPlaneInTheLayoutClaimsNothing() {
        FakeAppDrawerHost host = new FakeAppDrawerHost(RuntimeEnvironment.getApplication(), null);
        AppDrawerController controller = new AppDrawerController(host);

        controller.beginDrag(100f);
        controller.updateDrag(400f);
        controller.endDrag(2000f);

        assertFalse(controller.isOpen());
        assertFalse(controller.isSearchActive());
        assertNull("nothing was bound, so the interceptor slot was never touched",
            host.interceptorActive);
        assertEquals(0, host.flushes);
    }

    @Test
    public void theLayoutConfigIsReadOffTheHostsPreferences() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences raw = context.getSharedPreferences("drawer-host-test", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(context, raw, null);
        preferences.setAppLauncherDrawerViewType("horizontal");

        AppDrawerController controller = new AppDrawerController(
            new FakeAppDrawerHost(context, preferences));

        AppDrawerLayoutConfig config = ReflectionHelpers.getField(controller, "mLayoutConfig");
        assertEquals(AppDrawerViewType.HORIZONTAL, config.viewType);
    }

}
