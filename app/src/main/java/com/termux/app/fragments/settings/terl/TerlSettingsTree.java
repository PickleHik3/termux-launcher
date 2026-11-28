package com.termux.app.fragments.settings.terl;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.fragments.settings.SettingsTree;
import com.termux.app.settings.InputMethodAdapter;
import com.termux.app.settings.LowMemoryPresetAdapter;
import com.termux.app.settings.UseAsAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Terl's Settings as plain data (spec "Edit mode and Settings", ADR 0040; decided again
 * 2026-11-28, round 3): Home, Workspace, AI, Keyboard &amp; input and System, each one page holding
 * everything about its place. A group's page holds setting rows, each a registry id whose
 * declaration gives its title, summary, kind and choices; links to v1 pages kept as they are (the
 * On-device AI pages among them); and card rows, which open edit mode on the card where a look is
 * changed. Nothing is deeper than two levels: the root lists the groups, a group's page lists its
 * rows and links.
 *
 * <p>Settings holds behaviour only; looks are changed in edit mode. Rows that retire with v1's
 * surfaces (page transition, dock styles, status bar rows, pinned notifications, the floating and
 * split keyboard, Autocorrect) are not here.
 */
public final class TerlSettingsTree {

    private TerlSettingsTree() {}

    private static final String PAGES = "com.termux.app.fragments.settings.termux.";
    private static final String SETTINGS = "com.termux.app.fragments.settings.";

    /** What a row needs from the phone or the build before it is shown. */
    public enum Need {
        NOTHING,
        /** A build with the X server: Workspace's Linux apps rows. */
        X11_SERVER,
        /** A build with the Wayland host: the display server row. */
        WAYLAND,
        /** A phone that can load libghostty: Terminal core. */
        TERMINAL_ENGINE
    }

    /** The groups, in the order the root lists them. */
    public enum Group {
        HOME("home", R.string.terl_settings_group_home, R.string.terl_settings_group_home_summary,
            R.drawable.ic_symbol_home, Need.NOTHING),
        WORKSPACE("workspace", R.string.terl_settings_group_workspace,
            R.string.terl_settings_group_workspace_summary, R.drawable.ic_symbol_stacks, Need.NOTHING),
        AI("ai", R.string.terl_settings_group_ai, R.string.terl_settings_group_ai_summary,
            R.drawable.ic_symbol_smart_toy, Need.NOTHING),
        KEYBOARD("keyboard", R.string.terl_settings_group_keyboard,
            R.string.terl_settings_group_keyboard_summary, R.drawable.ic_symbol_keyboard, Need.NOTHING),
        SYSTEM("system", R.string.terl_settings_group_system,
            R.string.terl_settings_group_system_summary, R.drawable.ic_symbol_tune, Need.NOTHING);

        /** The key of the group's row on the root. */
        @NonNull public final String key;
        @StringRes public final int titleRes;
        @StringRes public final int summaryRes;
        @DrawableRes public final int iconRes;
        @NonNull public final Need need;

        Group(@NonNull String key, @StringRes int titleRes, @StringRes int summaryRes,
              @DrawableRes int iconRes, @NonNull Need need) {
            this.key = key;
            this.titleRes = titleRes;
            this.summaryRes = summaryRes;
            this.iconRes = iconRes;
            this.need = need;
        }
    }

    /** One line of a group's page. */
    public static final class Item {
        public enum Kind {
            /** A section title over the rows after it. */
            HEADING,
            /** A setting: a registry id. */
            SETTING,
            /** A v1 page (or one of Terl's own), opened as it is. */
            LINK,
            /** Edit mode, opened on a card: a look is changed where it is (ADR 0040). */
            CARD
        }

        @NonNull public final Kind kind;
        @NonNull public final Group group;
        /** A setting's registry id; null otherwise. */
        @Nullable public final String id;
        /** A link's page, by class name, or a card row's card ({@code EditModeController.Card}); null otherwise. */
        @Nullable public final String page;
        /** A heading's, a link's or a card row's title; 0 for a setting, whose declaration names it. */
        @StringRes public final int titleRes;
        /** A link's or card row's one-line summary, or 0. */
        @StringRes public final int summaryRes;
        /** A choice's labels, in the declaration's order, or 0 to show its names. */
        @ArrayRes public final int labelsRes;
        @NonNull public final Need need;

        Item(@NonNull Kind kind, @NonNull Group group, @Nullable String id, @Nullable String page,
             @StringRes int titleRes, @StringRes int summaryRes, @ArrayRes int labelsRes,
             @NonNull Need need) {
            this.kind = kind;
            this.group = group;
            this.id = id;
            this.page = page;
            this.titleRes = titleRes;
            this.summaryRes = summaryRes;
            this.labelsRes = labelsRes;
            this.need = need;
        }

        /** The key the row goes by on its page: the setting's id, the linked page's name, the card's. */
        @NonNull
        public String key() {
            if (id != null) return id;
            if (kind == Kind.CARD) return CARD_KEY_PREFIX + page;
            if (page != null) return page;
            return "terl_heading_" + titleRes;
        }
    }

    /** A card row's key: this, then the card's name. */
    public static final String CARD_KEY_PREFIX = "terl_card_";

    /** The page v1 already has for a group, opened as the group's own page; null for every group now. */
    @Nullable
    public static String hostedPage(@NonNull Group group) {
        return null;
    }

    /** Terl's own page under Keyboard &amp; input (not one of v1's): each action's key after the leader. */
    public static final String BINDINGS_PAGE =
        "com.termux.app.fragments.settings.terl.TerlKeyBindingsFragment";

    /** Terl's own page under AI: a free online assistant, its key and a Test. */
    public static final String FREE_ASSISTANT_PAGE =
        "com.termux.app.fragments.settings.terl.TerlFreeAssistantFragment";

    /** Terl's own pages, linked from a group as v1's are, each with its title. */
    public static boolean isTerlPage(@Nullable String page) {
        return BINDINGS_PAGE.equals(page) || FREE_ASSISTANT_PAGE.equals(page);
    }

    private static final Map<Group, List<Item>> ITEMS = new EnumMap<>(Group.class);

    private static List<Item> sGroup;
    private static Group sCurrent;
    /** What the rows after the current heading need (a Linux apps section needs the X server). */
    private static Need sSection = Need.NOTHING;

    private static void group(@NonNull Group group) {
        sCurrent = group;
        sGroup = new ArrayList<>();
        sSection = Need.NOTHING;
        ITEMS.put(group, sGroup);
    }

    private static void heading(@StringRes int titleRes) {
        heading(titleRes, Need.NOTHING);
    }

    /** A heading whose section shows only where {@code need} is met. */
    private static void heading(@StringRes int titleRes, @NonNull Need need) {
        sSection = need;
        sGroup.add(new Item(Item.Kind.HEADING, sCurrent, null, null, titleRes, 0, 0, need));
    }

    private static void row(@NonNull String id) {
        row(id, 0, Need.NOTHING);
    }

    private static void row(@NonNull String id, @ArrayRes int labelsRes) {
        row(id, labelsRes, Need.NOTHING);
    }

    private static void row(@NonNull String id, @ArrayRes int labelsRes, @NonNull Need need) {
        sGroup.add(new Item(Item.Kind.SETTING, sCurrent, id, null, 0, 0, labelsRes, need(need)));
    }

    /** A link titled as v1 titles the page. */
    private static void link(@NonNull String page, @StringRes int summaryRes) {
        SettingsTree.Screen screen = SettingsTree.find(page);
        if (screen == null) throw new IllegalStateException("not a v1 page: " + page);
        link(page, screen.titleRes, summaryRes);
    }

    private static void link(@NonNull String page, @StringRes int titleRes, @StringRes int summaryRes) {
        sGroup.add(new Item(Item.Kind.LINK, sCurrent, null, page, titleRes, summaryRes, 0,
            need(Need.NOTHING)));
    }

    /** A row opening edit mode on {@code card}, an {@code EditModeController.Card}'s name. */
    private static void card(@NonNull String card, @StringRes int titleRes, @StringRes int summaryRes) {
        sGroup.add(new Item(Item.Kind.CARD, sCurrent, null, card, titleRes, summaryRes, 0,
            need(Need.NOTHING)));
    }

    /** A row's own need, else its section's. */
    @NonNull
    private static Need need(@NonNull Need own) {
        return own != Need.NOTHING ? own : sSection;
    }

    static {
        // Home: what Home is and holds. Home pages are arranged in edit mode (the Home card);
        // the AI page stands left of Home 1 (ADR 0043).
        group(Group.HOME);
        row(UseAsAdapter.ID, R.array.terl_settings_use_as_labels);
        heading(R.string.terl_settings_heading_home_pages);
        row(SettingDeclarations.HOME_AI_PAGE.id);
        card("HOME", R.string.terl_settings_home_grid_title, R.string.terl_settings_home_grid_summary);
        row(SettingDeclarations.HOME_PAGE_DOTS.id);
        row(SettingDeclarations.HOME_AZ_EDGE.id, R.array.terl_settings_az_edge_labels);
        heading(R.string.terl_settings_heading_icons_labels);
        row(SettingDeclarations.HOME_LABELS.id);
        row(SettingDeclarations.HOME_LABEL_SIZE.id, R.array.terl_settings_label_size_labels);
        row(SettingDeclarations.HOME_LABEL_TWO_LINES.id);
        row(SettingDeclarations.HOME_LABEL_SHADOW.id);
        row(SettingDeclarations.HOME_FOLDERS.id, R.array.terl_settings_folders_labels);
        row(SettingDeclarations.HOME_NUMERALS.id, R.array.terl_settings_numerals_labels);
        row(SettingDeclarations.HOME_MONOCHROME_ICONS.id);
        card("GLOBAL", R.string.terl_settings_icon_pack_title, R.string.terl_settings_icon_pack_summary);
        heading(R.string.terl_settings_heading_drawer);
        link(PAGES + "AppDrawerPreferencesFragment", R.string.terl_settings_drawer_title,
            R.string.terl_settings_drawer_summary);
        row(SettingDeclarations.DRAWER_SEARCH_ON_OPEN.id);
        row(SettingDeclarations.TERL_BAR_IN_DRAWER.id);
        row(SettingDeclarations.MOST_USED_PAGE.id);
        link(PAGES + "LauncherSearchPreferencesFragment", R.string.terl_settings_app_search_summary);
        heading(R.string.terl_settings_heading_notifications);
        row(SettingDeclarations.NOTIFICATION_DOTS.id);
        link(PAGES + "NotificationHistoryFragment", R.string.terl_settings_history_summary);
        heading(R.string.terl_settings_heading_wallpaper);
        row(SettingDeclarations.WALLPAPER_PARALLAX.id);

        // Workspace: windows and what they do, terminals, Linux apps and the display.
        group(Group.WORKSPACE);
        heading(R.string.terl_settings_heading_windows);
        card("WINDOWS", R.string.terl_settings_window_looks_title,
            R.string.terl_settings_window_looks_summary);
        row(SettingDeclarations.LIVE_WINDOWS.id);
        row(com.termux.app.settings.LiveBlurSettingAdapter.ID);
        row(SettingDeclarations.GLASS_REFRACTION.id);
        heading(R.string.terl_settings_heading_terminals);
        row(SettingDeclarations.TERMINAL_SCROLLBACK.id);
        row(SettingDeclarations.TERMINAL_BELL.id, R.array.terl_settings_bell_labels);
        link(PAGES + "TermuxFontsPreferencesFragment", R.string.terl_settings_fonts_summary);
        link(PAGES + "TuiAppThemesPreferencesFragment", R.string.terl_settings_tool_colours_summary);
        link(PAGES + "TerminalClipboardPreferencesFragment", R.string.terl_settings_clipboard_title,
            R.string.terl_settings_clipboard_summary);
        row(SettingDeclarations.TERMINAL_CORE.id, R.array.terl_settings_terminal_core_labels,
            Need.TERMINAL_ENGINE);
        heading(R.string.terl_settings_heading_linux_apps, Need.X11_SERVER);
        link(PAGES + "GuiAppsSetupGuideFragment", R.string.terl_settings_linux_setup_summary);
        row(SettingDeclarations.LINUX_DISPLAY_SERVER.id, R.array.terl_settings_display_server_labels,
            Need.WAYLAND);
        link(PAGES + "DesktopsPreferencesFragment", R.string.terl_settings_desktops_summary);
        link(PAGES + "X11AppsPreferencesFragment", R.string.terl_settings_linux_apps_title,
            R.string.terl_settings_linux_apps_summary);
        link(PAGES + "X11DisplayResolutionPreferencesFragment", 0);
        link(PAGES + "GraphicsAccelerationFragment", R.string.terl_settings_graphics_summary);
        link(PAGES + "X11DisplayStartupPreferencesFragment", 0);
        link(PAGES + "X11DisplayTroubleshootingPreferencesFragment", 0);
        heading(R.string.terl_settings_heading_cutout);
        row(SettingDeclarations.CUTOUT_MODE.id, R.array.terl_settings_cutout_labels);
        row(SettingDeclarations.CUTOUT_INSET.id);

        // AI: the Assistant and its models (v1's On-device AI pages as they are), and a free
        // online assistant.
        group(Group.AI);
        link(PAGES + "TaiPreferencesFragment", R.string.terl_settings_on_device_ai_summary);
        link(FREE_ASSISTANT_PAGE, R.string.terl_free_assistant_title,
            R.string.terl_free_assistant_summary);

        // Keyboard & input: every keyboard setting, the shortcuts and the keyboard over the display.
        group(Group.KEYBOARD);
        row(InputMethodAdapter.ID, R.array.terl_settings_input_method_labels);
        heading(R.string.terl_settings_heading_shortcuts);
        row(SettingDeclarations.KEYS_LEADER.id);
        link(BINDINGS_PAGE, R.string.terl_settings_bindings_title, R.string.terl_settings_bindings_summary);
        heading(R.string.terl_settings_heading_launcher_keyboard);
        row(SettingDeclarations.KEYBOARD_LAYOUT.id, R.array.terl_settings_keyboard_layout_labels);
        link(PAGES + "KeyboardLayoutEditorFragment", R.string.terl_settings_custom_layout_title,
            R.string.terl_settings_custom_layout_summary);
        link(PAGES + "KeyboardLayoutPreferencesFragment", R.string.terl_settings_layouts_title,
            R.string.terl_settings_layouts_summary);
        card("KEYBOARD", R.string.terl_settings_keyboard_size_title,
            R.string.terl_settings_keyboard_size_summary);
        link(PAGES + "KeyboardColorSchemeFragment", R.string.terl_settings_keyboard_style_title,
            R.string.terl_settings_keyboard_style_summary);
        row(SettingDeclarations.KEYBOARD_KEY_SOUND.id);
        row(SettingDeclarations.KEYBOARD_KEY_POPUP.id);
        row(SettingDeclarations.KEYBOARD_HAPTICS.id);
        heading(R.string.terl_settings_heading_extra_keys);
        row(SettingDeclarations.TERL_EXTRA_KEYS.id);
        link(PAGES + "ExtraKeysEditorFragment", R.string.terl_settings_extra_keys_editor_title,
            R.string.terl_settings_extra_keys_editor_summary);
        heading(R.string.terl_settings_heading_voice);
        link(PAGES + "KeyboardVoicePreferencesFragment", R.string.terl_settings_voice_summary);
        heading(R.string.terl_settings_heading_hardware_keyboard);
        link(PAGES + "KeyboardHardwarePreferencesFragment", R.string.terl_settings_hardware_summary);
        heading(R.string.terl_settings_heading_display_input, Need.X11_SERVER);
        link(PAGES + "X11DisplayInputPreferencesFragment", R.string.terl_settings_display_input_summary);

        // System: battery, backup, permissions, diagnostics, about and updates.
        group(Group.SYSTEM);
        heading(R.string.terl_settings_heading_battery);
        row(SettingDeclarations.REDUCE_IDLE.id);
        row(LowMemoryPresetAdapter.ID);
        row(SettingDeclarations.ANIMATION_SPEED.id, R.array.terl_settings_animation_speed_labels);
        heading(R.string.terl_settings_heading_app);
        link(PAGES + "BackupRestorePreferencesFragment", R.string.settings_destination_backup_restore_summary);
        link(PAGES + "ServicesPermissionsPreferencesFragment",
            R.string.terl_settings_permissions_summary);
        link(PAGES + "AdvancedDiagnosticsPreferencesFragment",
            R.string.settings_destination_advanced_diagnostics_summary);
        link(SETTINGS + "AboutSupportPreferencesFragment",
            R.string.settings_destination_about_support_summary);
    }

    /** The key of System's Tips row (#443): a row of its own, not a declared setting. */
    public static final String TIPS_KEY = "terl_tips";

    /** The groups, in root order. */
    @NonNull
    public static List<Group> groups() {
        return Collections.unmodifiableList(Arrays.asList(Group.values()));
    }

    /** A group's page, top to bottom; empty for a group v1 hosts ({@link #hostedPage}). */
    @NonNull
    public static List<Item> items(@NonNull Group group) {
        List<Item> items = ITEMS.get(group);
        return items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    /** Every setting row's id, in tree order. */
    @NonNull
    public static List<String> settingIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Group group : Group.values()) {
            for (Item item : items(group)) if (item.kind == Item.Kind.SETTING) ids.add(item.id);
        }
        return new ArrayList<>(ids);
    }

    /** The setting row for {@code id}, or null for an id no row holds. */
    @Nullable
    public static Item setting(@Nullable String id) {
        if (id == null) return null;
        for (Group group : Group.values()) {
            for (Item item : items(group)) {
                if (item.kind == Item.Kind.SETTING && id.equals(item.id)) return item;
            }
        }
        return null;
    }

    /** The link to {@code page} on a group's page, or null when no group links it. */
    @Nullable
    public static Item linkTo(@NonNull String page) {
        for (Group group : Group.values()) {
            for (Item item : items(group)) {
                if (item.kind == Item.Kind.LINK && page.equals(item.page)) return item;
            }
        }
        return null;
    }

    /** The group whose page links {@code page} (or hosts it), or null. */
    @Nullable
    public static Group groupLinking(@NonNull String page) {
        for (Group group : Group.values()) {
            if (page.equals(hostedPage(group))) return group;
            for (Item item : items(group)) {
                if (item.kind == Item.Kind.LINK && page.equals(item.page)) return group;
            }
        }
        return null;
    }

    /** The group page's fragment class for {@code group}: v1's page for a hosted group. */
    @NonNull
    public static String pageOf(@NonNull Group group) {
        String hosted = hostedPage(group);
        return hosted != null ? hosted : TerlSettingsGroupFragment.className(group);
    }

    /**
     * How to reach v1's page {@code screen} from the root: the group, then the v1 pages from the
     * one the group links (or hosts) down to {@code screen}. Null for a page Terl's Settings does
     * not reach.
     */
    @Nullable
    public static Route routeTo(@NonNull String screen) {
        List<SettingsTree.Screen> path = SettingsTree.path(screen);
        for (int i = 0; i < path.size(); i++) {
            Group group = groupLinking(path.get(i).className);
            if (group == null) continue;
            return new Route(group, new ArrayList<>(path.subList(i, path.size())));
        }
        return null;
    }

    /** A way down to a v1 page: its group, then the v1 pages to open in order. */
    public static final class Route {
        @NonNull public final Group group;
        /** Starts with the page the group links, or with its hosted page. */
        @NonNull public final List<SettingsTree.Screen> pages;

        Route(@NonNull Group group, @NonNull List<SettingsTree.Screen> pages) {
            this.group = group;
            this.pages = Collections.unmodifiableList(pages);
        }

        /** Whether the first page is the group's own (hosted), not one its page links. */
        public boolean startsAtHostedPage() {
            return !pages.isEmpty() && pages.get(0).className.equals(hostedPage(group));
        }
    }
}
