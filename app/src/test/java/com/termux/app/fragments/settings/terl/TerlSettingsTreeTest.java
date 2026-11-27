package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.fragment.app.Fragment;

import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.settings.LowMemoryPresetAdapter;
import com.termux.app.settings.UseAsAdapter;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Terl's Settings tree: seven groups in the spec's order, no page deeper than two levels, every
 * row a registry declaration, the Assistant group v1's On-device AI page, and none of the rows that
 * retire with v1's surfaces.
 */
public class TerlSettingsTreeTest {

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";

    /** The registry the device builds, as far as Terl's rows reach: the declarations and adapters. */
    static SettingsRegistry registry(InMemorySettingStore store) {
        return new SettingsRegistry(which -> store, SettingDeclarations.ALL, Arrays.asList(
            new LowMemoryPresetAdapter(new LowMemoryPresetAdapter.Preset() {
                boolean on;
                @Override public boolean isOn() { return on; }
                @Override public void turnOn() { on = true; }
                @Override public void turnOff() { on = false; }
            }),
            new UseAsAdapter(new UseAsAdapter.Store() {
                String held;
                @Override public String read() { return held; }
                @Override public void write(String value) { held = value; }
            })));
    }

    @Test
    public void sevenGroupsInTheSpecsOrder() {
        assertEquals(Arrays.asList(TerlSettingsTree.Group.HOME, TerlSettingsTree.Group.WORKSPACES,
            TerlSettingsTree.Group.KEYS, TerlSettingsTree.Group.LINUX_APPS,
            TerlSettingsTree.Group.ASSISTANT, TerlSettingsTree.Group.NOTIFICATIONS,
            TerlSettingsTree.Group.SYSTEM), TerlSettingsTree.groups());
    }

    @Test
    public void noPageIsDeeperThanTwoLevels() throws Exception {
        // Level one is a group's page; everything on it opens a v1 page or is a row of its own.
        Set<String> groupPages = new HashSet<>();
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            if (TerlSettingsTree.hostedPage(group) == null) groupPages.add(TerlSettingsTree.pageOf(group));
        }
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                if (item.kind != TerlSettingsTree.Item.Kind.LINK) continue;
                assertFalse(item.page + " is a group page under a group", groupPages.contains(item.page));
                if (!item.page.equals(TerlSettingsTree.BINDINGS_PAGE))
                    assertNotNull(item.page + " is not a v1 page", SettingsTree.find(item.page));
                Class<?> page = Class.forName(item.page, false, getClass().getClassLoader());
                assertTrue(item.page, Fragment.class.isAssignableFrom(page));
            }
        }
        for (String page : groupPages) {
            assertTrue(page, Fragment.class.isAssignableFrom(
                Class.forName(page, false, getClass().getClassLoader())));
        }
    }

    @Test
    public void everySettingRowIsADeclarationTheRegistryKnows() {
        SettingsRegistry registry = registry(new InMemorySettingStore());
        List<String> ids = TerlSettingsTree.settingIds();
        assertFalse(ids.isEmpty());
        for (String id : ids) {
            SettingDeclaration declaration = registry.declaration(id);
            assertNotNull(id + " has no declaration", declaration);
            assertFalse(id, declaration.kind == SettingDeclaration.Kind.TEXT);
            assertFalse(id + " has no title", declaration.title.isEmpty());
            assertFalse(id + " has no summary", declaration.summary.isEmpty());
            assertFalse(id + " has no phrases", declaration.phrases.isEmpty());
            assertNotNull(id + " reads no value", registry.get(id));
        }
    }

    @Test
    public void theAssistantGroupIsV1sOnDeviceAiPage() {
        assertEquals(PAGES + "TaiPreferencesFragment",
            TerlSettingsTree.hostedPage(TerlSettingsTree.Group.ASSISTANT));
        assertEquals(PAGES + "TaiPreferencesFragment",
            TerlSettingsTree.pageOf(TerlSettingsTree.Group.ASSISTANT));
        assertTrue(TerlSettingsTree.items(TerlSettingsTree.Group.ASSISTANT).isEmpty());
        // Its own pages are reached under it, as they are.
        TerlSettingsTree.Route runtime = TerlSettingsTree.routeTo(PAGES + "TaiRuntimePreferencesFragment");
        assertNotNull(runtime);
        assertEquals(TerlSettingsTree.Group.ASSISTANT, runtime.group);
        assertTrue(runtime.startsAtHostedPage());
        assertEquals(2, runtime.pages.size());
    }

    @Test
    public void aLinkedPagesOwnPagesAreReachedUnderItsGroup() {
        TerlSettingsTree.Route clipboard = TerlSettingsTree.routeTo(PAGES + "TerminalClipboardPreferencesFragment");
        assertNotNull(clipboard);
        assertEquals(TerlSettingsTree.Group.WORKSPACES, clipboard.group);
        assertFalse(clipboard.startsAtHostedPage());
        assertEquals(1, clipboard.pages.size());
        // v1's pages Terl does not open are not reached.
        assertNull(TerlSettingsTree.routeTo(PAGES + "StatusBarPreferencesFragment"));
        assertNull(TerlSettingsTree.routeTo(PAGES + "EssentialNotificationRulesFragment"));
    }

    /** Declarations of rows that retire with v1's surfaces. */
    private static final Set<String> RETIRED_IDS = new HashSet<>(Arrays.asList(
        SettingDeclarations.PAGE_TRANSITION.id, SettingDeclarations.DOCK_STYLE.id,
        SettingDeclarations.STATUS_BATTERY.id, SettingDeclarations.STATUS_MEDIA.id,
        SettingDeclarations.STATUS_WEATHER.id, SettingDeclarations.STATUS_CLOCK_AM_PM.id,
        SettingDeclarations.NOTIFICATION_BADGE_STYLE.id, SettingDeclarations.TERL_DESKTOP.id));

    /**
     * v1's pages holding retired rows: Shell and Start in (General), Empty Workspace and the Layout
     * editor rows (Home, Windows), Autocorrect (Typing), pinned notifications, Minimal mode and
     * page transition (General), dock styles (Dock), status bar rows, the floating and split
     * keyboard (Keyboard), and looks (Performance, Appearance).
     */
    private static final Set<String> RETIRED_PAGES = new HashSet<>(Arrays.asList(
        PAGES + "GeneralPreferencesFragment", PAGES + "HomePreferencesFragment",
        PAGES + "TerminalWindowsPreferencesFragment", PAGES + "TerminalPreferencesFragment",
        PAGES + "KeyboardTypingPreferencesFragment", PAGES + "EssentialNotificationRulesFragment",
        PAGES + "LauncherPreferencesFragment", PAGES + "StatusBarPreferencesFragment",
        PAGES + "KeyboardPreferencesFragment", PAGES + "PerformancePreferencesFragment",
        PAGES + "TermuxStylePreferencesFragment", PAGES + "NotificationsPreferencesFragment"));

    @Test
    public void retiredRowsAreGone() {
        for (String id : TerlSettingsTree.settingIds()) {
            assertFalse(id + " retires", RETIRED_IDS.contains(id));
        }
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            assertFalse(RETIRED_PAGES.contains(TerlSettingsTree.pageOf(group)));
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                if (item.page != null) assertFalse(item.page + " holds retired rows",
                    RETIRED_PAGES.contains(item.page));
            }
        }
    }

    @Test
    public void everyRowKeyIsUniqueAcrossTheTree() {
        Set<String> keys = new HashSet<>();
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                assertTrue(item.key() + " twice", keys.add(item.key()));
            }
        }
    }
}
