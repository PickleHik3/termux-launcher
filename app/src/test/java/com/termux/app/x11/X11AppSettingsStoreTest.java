package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.shared.termux.TermuxConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11AppSettingsStoreTest {

    private static final String APP = "firefox.desktop";
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
        LauncherAppDataProvider.getInstance(context).hiddenApps()
            .setHidden(X11Apps.ref(APP).stableId(), false);
    }

    @Test public void followIsAbsent() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        assertNull(store.touchMode(APP));
        assertNull(store.scale(APP));
        assertTrue(store.appsWithOverrides().isEmpty());
        assertFalse(store.differsFromDisplay(APP));
    }

    @Test public void setAndClearReachANewInstance() {
        new X11AppSettingsStore(context).setTouchMode(APP, "2");
        new X11AppSettingsStore(context).setScale(APP, "1.5");
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        assertEquals("2", store.touchMode(APP));
        assertEquals("1.5", store.scale(APP));
        assertTrue(store.appsWithOverrides().contains(APP));

        store.setTouchMode(APP, null);
        assertNull(store.touchMode(APP));
        assertEquals("1.5", store.scale(APP));
        store.setScale(APP, "bogus");
        assertNull(store.scale(APP));
        assertTrue(store.appsWithOverrides().isEmpty());
    }

    @Test public void invalidValueDoesNotSet() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        store.setTouchMode(APP, "9");
        assertNull(store.touchMode(APP));
        assertTrue(store.appsWithOverrides().isEmpty());
    }

    @Test public void gpuProfileIsKeptBesideTheOtherOverrides() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        store.setScale(APP, "2");
        store.setGpuProfile(APP, "virgl");
        assertEquals("virgl", new X11AppSettingsStore(context).gpuProfile(APP));
        assertEquals("2", store.scale(APP));
        store.setGpuProfile(APP, "software");
        assertNull(store.gpuProfile(APP));
        store.setGpuProfile(APP, "nonsense");
        assertNull(store.gpuProfile(APP));
        store.setScale(APP, null);
        assertTrue(store.appsWithOverrides().isEmpty());
    }

    @Test public void softwareDelegatesToTheGpuStore() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        store.setDrawsInSoftware(APP, true);
        assertTrue(new X11GpuProfileStore(context).drawsInSoftware(APP));
        assertTrue(store.drawsInSoftware(APP));
        new X11GpuProfileStore(context).setDrawsInSoftware(APP, false);
        assertFalse(store.drawsInSoftware(APP));
        assertTrue(store.appsWithOverrides().isEmpty());
    }

    @Test public void hiddenDelegatesToTheDrawersStore() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        store.setHiddenInDrawer(APP, true);
        assertTrue(LauncherAppDataProvider.getInstance(context).hiddenApps()
            .isHidden(X11Apps.ref(APP).stableId()));
        assertTrue(store.hiddenInDrawer(APP));
        assertTrue(store.appsWithOverrides().isEmpty());
        store.setHiddenInDrawer(APP, false);
        assertFalse(store.hiddenInDrawer(APP));
    }

    @Test public void differsFromDisplayCountsAllFour() {
        X11AppSettingsStore store = new X11AppSettingsStore(context);
        store.setTouchMode(APP, "3");
        assertTrue(store.differsFromDisplay(APP));
        store.setTouchMode(APP, null);
        assertFalse(store.differsFromDisplay(APP));
        store.setScale(APP, "2");
        assertTrue(store.differsFromDisplay(APP));
        store.setScale(APP, null);
        store.setDrawsInSoftware(APP, true);
        assertTrue(store.differsFromDisplay(APP));
        store.setDrawsInSoftware(APP, false);
        store.setHiddenInDrawer(APP, true);
        assertTrue(store.differsFromDisplay(APP));
        store.setHiddenInDrawer(APP, false);
        assertFalse(store.differsFromDisplay(APP));
    }
}
