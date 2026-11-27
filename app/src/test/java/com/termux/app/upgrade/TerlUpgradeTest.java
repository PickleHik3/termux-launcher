package com.termux.app.upgrade;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.place.layout.LayoutStore;
import com.termux.app.terminal.TerminalWorkspaceStore;
import com.termux.app.tiling.TilingDesktopStore;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerlUpgradeTest {

    @Rule public final TemporaryFolder home = new TemporaryFolder();

    private Application app;
    private TermuxAppSharedPreferences prefs;
    private SharedPreferences store;

    /** v1 values the upgrade keeps. */
    private static final Map<String, Object> CARRIED = new HashMap<>();
    static {
        CARRIED.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_LAYOUTS, "latn_qwerty_us");
        CARRIED.put(SettingDeclarations.KEYBOARD_KEY_SOUND.key, true);
        CARRIED.put(TERMUX_APP.KEY_TERMINAL_ENGINE, "ghostty");
        CARRIED.put(TERMUX_APP.KEY_X11_DISPLAY_DPI, 220);
        CARRIED.put(TERMUX_APP.KEY_APP_NOTIFICATION_HISTORY_PACKAGES, "a.b,c.d");
        CARRIED.put(TERMUX_APP.KEY_LAUNCHERCTL_ROOT_REBOOT, true);
        CARRIED.put(TERMUX_APP.KEY_APP_LAUNCHER_PINNED_ITEMS_V2, "[{\"pkg\":\"a.b\"}]");
        CARRIED.put(TERMUX_APP.KEY_APP_LAUNCHER_ICON_PACK_PACKAGE, "com.icons");
        CARRIED.put(TERMUX_APP.KEY_USE_AS, "launcher");
        CARRIED.put(TERMUX_APP.KEY_FIRST_RUN_CHAIN_DONE, true);
    }

    /** v1 values the upgrade removes: the look, the layout, retired rows, Terl's own look. */
    private static final Map<String, Object> REMOVED = new HashMap<>();
    static {
        REMOVED.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, 14);
        REMOVED.put(TERMUX_APP.KEY_SURFACE_MATERIAL, "glass");
        REMOVED.put(TERMUX_APP.KEY_APP_LAUNCHER_DOCK_STYLE, "floating");
        REMOVED.put(TERMUX_APP.KEY_APP_LAUNCHER_BAR_HEIGHT, 60);
        REMOVED.put(TERMUX_APP.KEY_CURRENT_SESSION, "a-session");
        REMOVED.put(SettingDeclarations.QUICK_REPLY_SWIPE.key, false);
        REMOVED.put(SettingDeclarations.STATUS_WEATHER.key, true);
        REMOVED.put(TERMUX_APP.KEY_TERMINAL_BODY_SWIPE, true);
        REMOVED.put("terl.look.id", "frost");
    }

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        LayoutStore.prefs(app).edit().clear().commit();
        LayoutStore.shared(app).load();
        prefs = TermuxAppSharedPreferences.build(app, false);
        assertNotNull(prefs);
        store = prefs.getSharedPreferences();
        SharedPreferences.Editor editor = store.edit().clear();
        for (Map<String, Object> values : java.util.Arrays.asList(CARRIED, REMOVED)) {
            for (Map.Entry<String, Object> entry : values.entrySet()) put(editor, entry);
        }
        editor.commit();
    }

    @Test
    public void anUpgradedInstallKeepsItsBehaviourAndStartsTheLookFresh() throws IOException {
        File autosave = write(TerminalWorkspaceStore.AUTOSAVE_FILE);
        File desktop = write(TilingDesktopStore.FILE_NAME);
        File saved = write("work.json");

        assertTrue(TerlUpgrade.run(app, prefs, home.getRoot()));

        Map<String, ?> after = store.getAll();
        for (Map.Entry<String, Object> entry : CARRIED.entrySet())
            assertEquals(entry.getKey(), entry.getValue(), after.get(entry.getKey()));
        for (String key : REMOVED.keySet())
            assertFalse(key + " is still stored", after.containsKey(key));
        assertEquals(TerlUpgradePlan.VERSION, after.get(TerlUpgradePlan.KEY_DONE));
        assertFalse(autosave.exists());
        assertFalse(desktop.exists());
        assertTrue("a saved file stays", saved.exists());
    }

    @Test
    public void aSecondStartChangesNothing() throws IOException {
        assertTrue(TerlUpgrade.run(app, prefs, home.getRoot()));
        Map<String, ?> once = new HashMap<>(store.getAll());
        // Terl writes its own look and desktop after the upgrade; a second start keeps them.
        store.edit().putString("terl.look.id", "clear").commit();
        File desktop = write(TilingDesktopStore.FILE_NAME);
        Map<String, ?> before = new HashMap<>(store.getAll());

        assertFalse(TerlUpgrade.run(app, prefs, home.getRoot()));

        assertEquals(before, store.getAll());
        assertTrue(desktop.exists());
        assertEquals(once.size() + 1, before.size());
    }

    private File write(String name) throws IOException {
        File dir = new File(new File(home.getRoot(), ".termux"), "workspaces");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File file = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static void put(SharedPreferences.Editor editor, Map.Entry<String, Object> entry) {
        Object value = entry.getValue();
        if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
        else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
        else editor.putString(entry.getKey(), (String) value);
    }
}
