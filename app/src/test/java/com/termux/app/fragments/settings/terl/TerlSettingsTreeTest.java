package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.fragment.app.Fragment;

import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.settings.InputMethodAdapter;
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
 * Terl's Settings tree: Home, Workspace, AI, Keyboard &amp; input and System (round 3 of
 * 2026-11-28), each holding everything about its place; no page deeper than two levels, every row
 * a registry declaration, v1's On-device AI pages under AI, and none of the rows that retire with
 * v1's surfaces.
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
            new com.termux.app.settings.LiveBlurSettingAdapter(
                new com.termux.app.settings.LiveBlurSettingAdapter.Memory(false)),
            new UseAsAdapter(new UseAsAdapter.Store() {
                String held;
                @Override public String read() { return held; }
                @Override public void write(String value) { held = value; }
            }),
            new InputMethodAdapter(new InputMethodAdapter.Store() {
                boolean builtIn = true;
                boolean android = true;
                @Override public boolean builtIn() { return builtIn; }
                @Override public boolean android() { return android; }
                @Override public void write(boolean b, boolean a) { builtIn = b; android = a; }
            })));
    }

    @Test
    public void fiveGroupsInRound3sOrder() {
        assertEquals(Arrays.asList(TerlSettingsTree.Group.HOME, TerlSettingsTree.Group.WORKSPACE,
            TerlSettingsTree.Group.AI, TerlSettingsTree.Group.KEYBOARD,
            TerlSettingsTree.Group.SYSTEM), TerlSettingsTree.groups());
    }

    /** The group a setting row stands in. */
    private static TerlSettingsTree.Group groupOf(String id) {
        TerlSettingsTree.Item item = TerlSettingsTree.setting(id);
        assertNotNull(id + " has no row", item);
        return item.group;
    }

    /** The group linking v1's (or Terl's own) {@code page}. */
    private static TerlSettingsTree.Group groupLinking(String page) {
        TerlSettingsTree.Group group = TerlSettingsTree.groupLinking(page);
        assertNotNull(page + " is linked nowhere", group);
        return group;
    }

    @Test
    public void everyRowStandsUnderThePlaceItAffects() {
        // Home: Home pages, A-Z, the drawer, notifications, wallpaper, the AI page, icons and labels.
        for (String id : Arrays.asList(UseAsAdapter.ID, SettingDeclarations.HOME_AI_PAGE.id,
                SettingDeclarations.HOME_PAGE_DOTS.id, SettingDeclarations.HOME_AZ_EDGE.id,
                SettingDeclarations.HOME_LABEL_SIZE.id, SettingDeclarations.HOME_LABEL_TWO_LINES.id,
                SettingDeclarations.HOME_LABEL_SHADOW.id, SettingDeclarations.HOME_FOLDERS.id,
                SettingDeclarations.HOME_NUMERALS.id, SettingDeclarations.HOME_MONOCHROME_ICONS.id,
                SettingDeclarations.TERL_BAR_IN_DRAWER.id, SettingDeclarations.NOTIFICATION_DOTS.id,
                SettingDeclarations.WALLPAPER_PARALLAX.id)) {
            assertEquals(id, TerlSettingsTree.Group.HOME, groupOf(id));
        }
        assertEquals(TerlSettingsTree.Group.HOME, groupLinking(PAGES + "AppDrawerPreferencesFragment"));
        assertEquals(TerlSettingsTree.Group.HOME, groupLinking(PAGES + "NotificationHistoryFragment"));
        // Workspace: windows, terminals, Linux apps and the display.
        for (String id : Arrays.asList(SettingDeclarations.TERMINAL_SCROLLBACK.id,
                SettingDeclarations.TERMINAL_BELL.id, SettingDeclarations.TERMINAL_CORE.id,
                SettingDeclarations.LINUX_DISPLAY_SERVER.id, SettingDeclarations.LIVE_WINDOWS.id)) {
            assertEquals(id, TerlSettingsTree.Group.WORKSPACE, groupOf(id));
        }
        for (String page : Arrays.asList("TerminalClipboardPreferencesFragment",
                "GraphicsAccelerationFragment", "DesktopsPreferencesFragment",
                "TermuxFontsPreferencesFragment")) {
            assertEquals(page, TerlSettingsTree.Group.WORKSPACE, groupLinking(PAGES + page));
        }
        // AI: v1's On-device AI pages and the free online assistant.
        assertEquals(TerlSettingsTree.Group.AI, groupLinking(PAGES + "TaiPreferencesFragment"));
        assertEquals(TerlSettingsTree.Group.AI, groupLinking(TerlSettingsTree.FREE_ASSISTANT_PAGE));
        // Keyboard & input: every keyboard setting and the keyboard over the display.
        for (String id : Arrays.asList(InputMethodAdapter.ID, SettingDeclarations.KEYS_LEADER.id,
                SettingDeclarations.KEYBOARD_LAYOUT.id, SettingDeclarations.KEYBOARD_HAPTICS.id,
                SettingDeclarations.TERL_EXTRA_KEYS.id)) {
            assertEquals(id, TerlSettingsTree.Group.KEYBOARD, groupOf(id));
        }
        for (String page : Arrays.asList("ExtraKeysEditorFragment", "KeyboardColorSchemeFragment",
                "KeyboardVoicePreferencesFragment", "KeyboardHardwarePreferencesFragment",
                "X11DisplayInputPreferencesFragment")) {
            assertEquals(page, TerlSettingsTree.Group.KEYBOARD, groupLinking(PAGES + page));
        }
        assertEquals(TerlSettingsTree.Group.KEYBOARD, groupLinking(TerlSettingsTree.BINDINGS_PAGE));
        // System: the rest.
        assertEquals(TerlSettingsTree.Group.SYSTEM, groupOf(LowMemoryPresetAdapter.ID));
        assertEquals(TerlSettingsTree.Group.SYSTEM,
            groupLinking(PAGES + "BackupRestorePreferencesFragment"));
    }

    @Test
    public void cardRowsOpenEditModeOnARealCard() {
        Set<String> cards = new HashSet<>();
        for (com.termux.app.editmode.EditModeController.Card card
                : com.termux.app.editmode.EditModeController.Card.values()) cards.add(card.name());
        int seen = 0;
        for (TerlSettingsTree.Group group : TerlSettingsTree.groups()) {
            for (TerlSettingsTree.Item item : TerlSettingsTree.items(group)) {
                if (item.kind != TerlSettingsTree.Item.Kind.CARD) continue;
                seen++;
                assertTrue(item.page + " is no card", cards.contains(item.page));
                assertTrue(item.key().startsWith(TerlSettingsTree.CARD_KEY_PREFIX));
            }
        }
        assertTrue(seen > 0);
    }

    @Test
    public void linuxAppsRowsNeedTheXServer() {
        TerlSettingsTree.Item display = TerlSettingsTree.linkTo(PAGES + "GraphicsAccelerationFragment");
        assertNotNull(display);
        assertEquals(TerlSettingsTree.Need.X11_SERVER, display.need);
        TerlSettingsTree.Item input = TerlSettingsTree.linkTo(PAGES + "X11DisplayInputPreferencesFragment");
        assertNotNull(input);
        assertEquals(TerlSettingsTree.Need.X11_SERVER, input.need);
        // The display server keeps its own, stricter need.
        assertEquals(TerlSettingsTree.Need.WAYLAND,
            TerlSettingsTree.setting(SettingDeclarations.LINUX_DISPLAY_SERVER.id).need);
        assertEquals(TerlSettingsTree.Need.NOTHING,
            TerlSettingsTree.setting(SettingDeclarations.TERMINAL_SCROLLBACK.id).need);
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
                if (!TerlSettingsTree.isTerlPage(item.page))
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
    public void v1sOnDeviceAiPagesAreReachedUnderAi() {
        TerlSettingsTree.Route runtime = TerlSettingsTree.routeTo(PAGES + "TaiRuntimePreferencesFragment");
        assertNotNull(runtime);
        assertEquals(TerlSettingsTree.Group.AI, runtime.group);
        assertFalse(runtime.startsAtHostedPage());
        assertEquals(2, runtime.pages.size());
        assertEquals(PAGES + "TaiPreferencesFragment", runtime.pages.get(0).className);
    }

    @Test
    public void aLinkedPagesOwnPagesAreReachedUnderItsGroup() {
        TerlSettingsTree.Route clipboard = TerlSettingsTree.routeTo(PAGES + "TerminalClipboardPreferencesFragment");
        assertNotNull(clipboard);
        assertEquals(TerlSettingsTree.Group.WORKSPACE, clipboard.group);
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
