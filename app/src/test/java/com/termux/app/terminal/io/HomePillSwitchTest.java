package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.place.PlaceOrientation;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomePillSwitchTest {

    private static final String KEY = TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME;

    private static final class Fake implements HomePillSwitch.Host {
        final Map<PlaceOrientation, Boolean> shown = new EnumMap<>(PlaceOrientation.class);
        boolean toolbar = true;
        final Map<String, String> properties = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        int reloads;

        @Override public boolean homeExtraKeysShown(PlaceOrientation o) {
            return Boolean.TRUE.equals(shown.get(o));
        }

        @Override public void setHomeExtraKeysShown(PlaceOrientation o, boolean s) {
            shown.put(o, s);
        }

        @Override public boolean toolbarOn() {
            return toolbar;
        }

        @Override public String rawLine(String key) {
            return properties.get(key);
        }

        @Override public void writeProperty(String key, String value) {
            writes.add(key);
            properties.put(key, value);
        }

        @Override public void reloadProperties() {
            reloads++;
        }
    }

    @Test
    public void itIsOnOnlyWhenTheKindKeyAndTheToolbarSwitchAreBoth() {
        Fake host = new Fake();
        HomePillSwitch pill = new HomePillSwitch(host);
        assertFalse(pill.isOn(PlaceOrientation.PORTRAIT));
        host.shown.put(PlaceOrientation.PORTRAIT, true);
        assertTrue(pill.isOn(PlaceOrientation.PORTRAIT));
        assertFalse(pill.isOn(PlaceOrientation.LANDSCAPE));
        host.toolbar = false;
        assertFalse(pill.isOn(PlaceOrientation.PORTRAIT));
    }

    @Test
    public void keysOnIgnoresTheToolbarSwitchAndEitherOrientationCounts() {
        Fake host = new Fake();
        HomePillSwitch pill = new HomePillSwitch(host);
        assertFalse(pill.keysOn());
        host.shown.put(PlaceOrientation.LANDSCAPE, true);
        host.toolbar = false;
        assertTrue(pill.keysOn());
    }

    @Test
    public void turningOnThenOffKeepsTheLineSoOnAgainShowsTheSameKeys() {
        Fake host = new Fake();
        host.properties.put(KEY, "[[F1]]");
        HomePillSwitch pill = new HomePillSwitch(host);
        pill.setOn(false);
        pill.setOn(true);
        assertEquals("[[F1]]", host.properties.get(KEY));
        assertTrue(pill.keysOn());
    }

    @Test
    public void turningOnWritesTheKeyInBothOrientationsAndTheStarterSetWhenHomeHasNone() {
        Fake host = new Fake();
        new HomePillSwitch(host).setOn(true);
        assertTrue(host.homeExtraKeysShown(PlaceOrientation.PORTRAIT));
        assertTrue(host.homeExtraKeysShown(PlaceOrientation.LANDSCAPE));
        assertEquals(TermuxPropertyConstants.HOME_PILL_STARTER_EXTRA_KEYS, host.properties.get(KEY));
        assertEquals(1, host.writes.size());
        assertEquals(1, host.reloads);
    }

    @Test
    public void aBlankLineCountsAsAbsent() {
        Fake host = new Fake();
        host.properties.put(KEY, "  ");
        new HomePillSwitch(host).setOn(true);
        assertEquals(TermuxPropertyConstants.HOME_PILL_STARTER_EXTRA_KEYS, host.properties.get(KEY));
    }

    @Test
    public void anExistingSetIsKept() {
        Fake host = new Fake();
        host.properties.put(KEY, "[[F1]]");
        new HomePillSwitch(host).setOn(true);
        assertEquals("[[F1]]", host.properties.get(KEY));
        assertTrue(host.writes.isEmpty());
        assertEquals(0, host.reloads);
        assertTrue(host.homeExtraKeysShown(PlaceOrientation.LANDSCAPE));
    }

    @Test
    public void turningOffWritesNothingToTheProperties() {
        Fake host = new Fake();
        host.shown.put(PlaceOrientation.PORTRAIT, true);
        host.shown.put(PlaceOrientation.LANDSCAPE, true);
        new HomePillSwitch(host).setOn(false);
        assertFalse(host.homeExtraKeysShown(PlaceOrientation.PORTRAIT));
        assertFalse(host.homeExtraKeysShown(PlaceOrientation.LANDSCAPE));
        assertTrue(host.writes.isEmpty());
        assertEquals(0, host.reloads);
    }

    @Test
    public void theStarterLineParsesAsThreeKeysOfRealTools() throws Exception {
        ExtraKeysInfo info = new ExtraKeysInfo(TermuxPropertyConstants.HOME_PILL_STARTER_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        assertEquals(1, info.getMatrix().length);
        assertEquals(3, info.getMatrix()[0].length);
        String[] tools = {LauncherToolRegistry.TOOL_APP_COMMAND_PALETTE,
            LauncherToolRegistry.TOOL_APP_OPEN_APP_DRAWER, LauncherToolRegistry.TOOL_APP_OPEN_SETTINGS};
        for (int i = 0; i < tools.length; i++) {
            assertEquals("tool:" + tools[i], info.getMatrix()[0][i].getKey());
            assertNotNull(LauncherToolRegistry.getInstance().getTool(tools[i]));
            assertFalse(info.getMatrix()[0][i].getDisplay().isEmpty());
        }
    }
}
