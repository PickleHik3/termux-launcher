package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.x11.X11AppSettingsStore;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowLookSettingsTest {

    private Context context;
    private TermuxAppSharedPreferences prefs;
    private X11AppSettingsStore apps;
    private WindowLookSettings look;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE).edit().clear().commit();
        prefs = TermuxAppSharedPreferences.build(context);
        apps = new X11AppSettingsStore(context);
        look = new WindowLookSettings(prefs, new StoredTerlLookStore(new MemoryLookStore()), apps, id -> "app");
    }

    @Test public void theDefaultsAreTheTicketsOwn() {
        assertEquals(8, look.gapDp());
        assertTrue(look.rimOn());
        assertEquals("accent", look.rimColour());
        assertEquals(0, look.unfocusedDimPercent());
        assertEquals(100, look.linuxOpacity("firefox"));
    }

    @Test public void anAppsOwnOpacityBeatsTheGlobalOne() {
        prefs.setTerlLinuxOpacity(70);
        assertEquals(70, look.linuxOpacity("firefox"));
        apps.setOpacity("firefox", 40);
        assertEquals(40, look.linuxOpacity("firefox"));
        assertEquals(70, look.linuxOpacity("gimp"));
    }

    @Test public void aWindowWithNoKnownAppFollowsTheGlobalOpacity() {
        prefs.setTerlLinuxOpacity(55);
        apps.setOpacity("firefox", 40);
        assertEquals(55, look.linuxOpacity(""));
    }

    @Test public void theNumbersAreClamped() {
        prefs.setTerlLinuxOpacity(1);
        assertEquals(20, prefs.getTerlLinuxOpacity());
        prefs.setTerlWindowsUnfocusedDim(99);
        assertEquals(60, prefs.getTerlWindowsUnfocusedDim());
        prefs.setTerlWindowsGapDp(99);
        assertEquals(24, prefs.getTerlWindowsGapDp());
    }

    @Test public void onlyTheWindowsKeysAreRead() {
        assertTrue(WindowLookSettings.readsKey("terl_windows_rim"));
        assertTrue(WindowLookSettings.readsKey("terl_linux_opacity"));
        assertTrue(WindowLookSettings.readsKey("x11_app_opacity_firefox"));
        assertFalse(WindowLookSettings.readsKey("terl_bar_margin"));
        assertFalse(WindowLookSettings.readsKey(null));
    }
}
