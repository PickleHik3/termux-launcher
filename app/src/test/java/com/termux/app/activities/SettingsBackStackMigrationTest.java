package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

/**
 * A Back stack saved before the 10-19 restructure names pages that moved or folded. Restored in
 * today's tree it must land on the page that holds those rows now, with the path to it rebuilt so
 * Back walks up the new tree; a name that no longer exists falls back to the root. Never a crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsBackStackMigrationTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    /** Saves a stack the way the old build did: no tree version, fresh enough to restore. */
    private static void saveOldStack(String... classAndKey) throws Exception {
        JSONArray entries = new JSONArray();
        for (String item : classAndKey) {
            String[] parts = item.split("#", 2);
            JSONObject entry = new JSONObject();
            entry.put("class_name", parts[0]);
            entry.put("title_res_id", 0x7f7fffff); // a resource id from the old build
            if (parts.length > 1) entry.put("scroll_to_key", parts[1]);
            entries.put(entry);
        }
        JSONObject root = new JSONObject();
        root.put("stopped_at_epoch_ms", System.currentTimeMillis() - 1000);
        root.put("entries", entries);
        RuntimeEnvironment.getApplication().getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE)
            .edit().putString(SettingsActivity.PREFS_KEY_BACK_STACK_STATE, root.toString()).commit();
    }

    private static SettingsActivity reopen() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class);
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        return activity;
    }

    private static String shown(SettingsActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        return fragment.getClass().getName();
    }

    @Test
    public void theOldStatusBarPageOpensUnderTerminal() throws Exception {
        saveOldStack(PAGES + "StatusBarPreferencesFragment");
        SettingsActivity activity = reopen();
        assertEquals(PAGES + "StatusBarPreferencesFragment", shown(activity));
        assertEquals("Terminal, then Status bar", 2, activity.getSupportFragmentManager().getBackStackEntryCount());
        activity.getSupportFragmentManager().popBackStackImmediate();
        assertEquals(PAGES + "TerminalPreferencesFragment", shown(activity));
    }

    @Test
    public void theOldAppBehaviorPageOpensGeneral() throws Exception {
        saveOldStack(PAGES + "AppBehaviorPreferencesFragment");
        assertEquals(PAGES + "GeneralPreferencesFragment", shown(reopen()));
    }

    @Test
    public void theOldDisplaySubpageOpensUnderLinuxApps() throws Exception {
        saveOldStack(PAGES + "X11DisplayPreferencesFragment", PAGES + "X11DisplayInputPreferencesFragment");
        SettingsActivity activity = reopen();
        assertEquals(PAGES + "X11DisplayInputPreferencesFragment", shown(activity));
        assertEquals("Linux apps, then Input", 2, activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    @Test
    public void theOldLookFontPageOpensUnderTerminalText() throws Exception {
        saveOldStack(PAGES + "TermuxStylePreferencesFragment", PAGES + "TermuxFontsPreferencesFragment");
        SettingsActivity activity = reopen();
        assertEquals(PAGES + "TermuxFontsPreferencesFragment", shown(activity));
        assertEquals("Terminal, Text & display, Fonts", 3,
            activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    @Test
    public void theOldDockPageCollapsesIntoDockAndDrawer() throws Exception {
        saveOldStack(PAGES + "LauncherPreferencesFragment", PAGES + "LauncherDockPreferencesFragment");
        SettingsActivity activity = reopen();
        assertEquals(PAGES + "LauncherPreferencesFragment", shown(activity));
        assertEquals(1, activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    @Test
    public void aRemovedPageFallsBackToTheRoot() throws Exception {
        saveOldStack("com.termux.app.fragments.settings.termux.GoneSinceTheUpdateFragment");
        SettingsActivity activity = reopen();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.settings)
            instanceof SettingsActivity.RootPreferencesFragment);
    }

    @Test
    public void migrationKeepsATodayStackAsIs() {
        List<SettingsBackStackState.Entry> today = Arrays.asList(
            new SettingsBackStackState.Entry(PAGES + "TaiPreferencesFragment", 0, null, null, null),
            new SettingsBackStackState.Entry(PAGES + "TaiModelCentreFragment", 0, null, null, null),
            new SettingsBackStackState.Entry(PAGES + "SpeechModelPreferencesFragment", 0, null, null, null));
        SettingsBackStackState state = new SettingsBackStackState(today, 1);
        SettingsBackStackState parsed = SettingsBackStackState.parse(state.serialize());
        assertEquals("saved against today's tree: replayed as it was", 3, parsed.entriesForCurrentTree().size());
        assertEquals(PAGES + "TaiPreferencesFragment", parsed.entriesForCurrentTree().get(0).className);
    }

    @Test
    public void aReopenNeverLandsInsideAnEditorButOnThePageThatOpenedIt() throws Exception {
        // Linux apps, then the Set up Linux apps guide (an editor), saved against today's tree.
        JSONArray entries = new JSONArray();
        for (String name : new String[]{PAGES + "X11DisplayPreferencesFragment",
            PAGES + "GuiAppsSetupGuideFragment", PAGES + "GraphicsAccelerationFragment"}) {
            JSONObject entry = new JSONObject();
            entry.put("class_name", name);
            entry.put("title_res_id", 0);
            entries.put(entry);
        }
        JSONObject root = new JSONObject();
        root.put("stopped_at_epoch_ms", System.currentTimeMillis() - 1000);
        root.put("tree_version", SettingsBackStackState.TREE_VERSION);
        root.put("entries", entries);
        RuntimeEnvironment.getApplication().getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE)
            .edit().putString(SettingsActivity.PREFS_KEY_BACK_STACK_STATE, root.toString()).commit();

        SettingsActivity activity = reopen();
        assertEquals(PAGES + "X11DisplayPreferencesFragment", shown(activity));
        assertEquals("one Back leaves Linux apps for the root",
            1, activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    @Test
    public void aGuideOpenedStraightFromTheRootReopensOnLinuxAppsNotTheRoot() throws Exception {
        // The guide pushed with nothing under it (its row on the root chain), then a page in it.
        JSONArray entries = new JSONArray();
        for (String name : new String[]{PAGES + "GuiAppsSetupGuideFragment",
            PAGES + "GraphicsAccelerationFragment"}) {
            JSONObject entry = new JSONObject();
            entry.put("class_name", name);
            entry.put("title_res_id", 0);
            entries.put(entry);
        }
        JSONObject root = new JSONObject();
        root.put("stopped_at_epoch_ms", System.currentTimeMillis() - 1000);
        root.put("tree_version", SettingsBackStackState.TREE_VERSION);
        root.put("entries", entries);
        SettingsBackStackState parsed = SettingsBackStackState.parse(root.toString());
        List<SettingsBackStackState.Entry> kept = parsed.entriesToRestore();
        assertEquals("the guide and what was above it go; the page under it stays",
            1, kept.size());
        assertEquals(PAGES + "X11DisplayPreferencesFragment", kept.get(0).className);

        RuntimeEnvironment.getApplication().getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE)
            .edit().putString(SettingsActivity.PREFS_KEY_BACK_STACK_STATE, root.toString()).commit();

        SettingsActivity activity = reopen();
        assertEquals(PAGES + "X11DisplayPreferencesFragment", shown(activity));
        assertEquals(1, activity.getSupportFragmentManager().getBackStackEntryCount());
    }

    @Test
    public void anEditorOpenedFromAnEditorReopensOnThePageUnderBoth() {
        // The Keyboard layout editor saved alone: its parent, the Layouts list, is an editor too.
        List<SettingsBackStackState.Entry> saved = Arrays.asList(
            new SettingsBackStackState.Entry(PAGES + "KeyboardLayoutEditorFragment", 0, null, null, null));
        List<SettingsBackStackState.Entry> kept =
            new SettingsBackStackState(saved, System.currentTimeMillis()).entriesToRestore();
        assertTrue(kept.size() > 0);
        for (SettingsBackStackState.Entry entry : kept) {
            com.termux.app.fragments.settings.SettingsTree.Screen screen =
                com.termux.app.fragments.settings.SettingsTree.find(entry.className);
            assertTrue(entry.className + " is a page", screen != null && !screen.editor);
        }
    }
}
