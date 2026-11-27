package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The launcher's setting declarations. Each is a constant the store's getter clamps with, so a
 * range or a list of choices is written once. Adding a setting is one constant here plus its place
 * in {@link #ALL}; the registry's index is built from {@link #ALL} only when something asks.
 */
public final class SettingDeclarations {

    private SettingDeclarations() {}

    /** Theme: System, Light or Dark, kept in termux.properties as night-mode system, false or true. */
    @NonNull
    public static final SettingDeclaration THEME_MODE = SettingDeclaration.choice("theme.mode",
        SettingDeclaration.Store.PROPERTIES, "night-mode",
        Arrays.asList("system", "light", "dark"),
        Arrays.asList(NightMode.SYSTEM.getName(), NightMode.FALSE.getName(), NightMode.TRUE.getName()),
        "system", false, SettingRefresh.THEME).described("Theme",
        "Whether the launcher follows the system's light or dark setting, or stays light or dark.")
        .saidAs("too bright", "my eyes hurt at night", "eyes hurt at night", "dark mode",
            "light mode", "switch to the dark theme", "the screen is blinding");

    /** The terminal palette's contrast, Softer to Harder ({@link TerminalContrastLevel}). */
    @NonNull
    public static final SettingDeclaration TERMINAL_CONTRAST = SettingDeclaration.choice(
        "terminal.contrast", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL, contrastLevels(), contrastLevels(),
        TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL, true, SettingRefresh.RESTYLE).resetBy(SettingDeclaration.ResetGroup.APPEARANCE)
        .described("Terminal contrast",
            "How hard the terminal's colours are, from soft pastels to the deepest background and brightest text.")
        .saidAs("hard to read", "text is hard to read on my wallpaper", "colours are too harsh",
            "colours look washed out", "my eyes hurt at night");

    /** The shared Text size, in percent. */
    @NonNull
    public static final SettingDeclaration TEXT_SIZE = SettingDeclaration.integer("text.size",
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TEXT_SIZE_PERCENT,
        TERMUX_APP.MIN_TEXT_SIZE_PERCENT, TERMUX_APP.MAX_TEXT_SIZE_PERCENT,
        TERMUX_APP.TEXT_SIZE_PERCENT_STEP, TERMUX_APP.DEFAULT_TEXT_SIZE_PERCENT,
        SettingRefresh.TEXT_SIZE).resetBy(SettingDeclaration.ResetGroup.APPEARANCE)
        .described("Text size", "How large text is across the launcher's own screens, in percent.")
        .saidAs("too small", "too big", "hard to read", "can't read the text", "make the text bigger",
            "make the text smaller");


    // ---- Motion: how the launcher moves ----

    /** How fast the launcher's own motion runs; the phone's reduce-motion setting still wins. */
    @NonNull
    public static final SettingDeclaration ANIMATION_SPEED = SettingDeclaration.choice(
        "motion.animation_speed", SettingDeclaration.Store.APP_PREFERENCES, "animation_speed",
        Arrays.asList("slower", "normal", "faster", "off"),
        Arrays.asList("slower", "normal", "faster", "off"), "normal", true,
        SettingRefresh.LISTENER).described("Animation speed",
        "How fast the launcher's own animations run, or whether they play at all.")
        .saidAs("animations are too slow", "animations are too fast", "my phone feels slow",
            "turn the animations off", "the motion makes me dizzy");

    /** How a page turns on Home, between Home and the Terminal, and from window to window. */
    @NonNull
    public static final SettingDeclaration PAGE_TRANSITION = SettingDeclaration.choice(
        "motion.page_transition", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_home_page_transition",
        Arrays.asList("slide", "depth", "fade", "carousel", "stack"),
        Arrays.asList("slide", "depth", "fade", "carousel", "stack"), "slide", false,
        SettingRefresh.LISTENER).described("Page transition",
        "How a page turns when you swipe between Home pages, the Terminal and windows.")
        .saidAs("swiping between pages looks odd", "change the page animation",
            "pages slide too much", "swiping between pages is laggy");

    /** Little glitches that play on an idle terminal. */
    @NonNull
    public static final SettingDeclaration IDLE_ANIMATIONS = SettingDeclaration.bool(
        "motion.idle_animations", SettingDeclaration.Store.APP_PREFERENCES, "idle_animations",
        false, SettingRefresh.LISTENER).described("Idle animations",
        "Lets the retro screen effect play small glitches while the terminal sits idle.")
        .saidAs("the screen keeps moving", "my battery drains too fast", "stop the glitching")
        .notOffered();

    /** How often an idle terminal glitches. */
    @NonNull
    public static final SettingDeclaration IDLE_TEAR_RATE = SettingDeclaration.choice(
        "motion.idle_tear_rate", SettingDeclaration.Store.APP_PREFERENCES, "idle_tear_rate",
        Arrays.asList("rare", "sometimes", "often"), Arrays.asList("rare", "sometimes", "often"),
        "rare", true, SettingRefresh.LISTENER).described("Idle glitch rate",
        "How often the idle animations glitch the terminal.")
        .saidAs("the glitches happen too often", "glitch less", "glitch more");

    /** The streak that follows the terminal's cursor. */
    @NonNull
    public static final SettingDeclaration CURSOR_TRAIL = SettingDeclaration.bool(
        "motion.cursor_trail", SettingDeclaration.Store.APP_PREFERENCES, "terminal_cursor_trail",
        true, SettingRefresh.RESTYLE).described("Cursor trail",
        "Draws a trail behind the terminal's cursor as it moves.")
        .saidAs("the cursor leaves a streak", "my phone feels slow", "turn off the cursor effect")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    /** Whether the wallpaper pans a little as pages and windows slide. */
    @NonNull
    public static final SettingDeclaration WALLPAPER_PARALLAX = SettingDeclaration.bool(
        "motion.wallpaper_parallax", SettingDeclaration.Store.APP_PREFERENCES, "wallpaper_parallax",
        true, SettingRefresh.RESTYLE).described("Wallpaper parallax",
        "Moves the wallpaper a little as pages and windows slide.")
        .saidAs("swiping between pages is laggy", "the wallpaper moves when I swipe",
            "my phone feels slow");

    /** Whether windows stay live while a swipe moves them. */
    @NonNull
    public static final SettingDeclaration LIVE_WINDOWS = SettingDeclaration.bool(
        "motion.live_windows", SettingDeclaration.Store.APP_PREFERENCES,
        "live_windows_while_swiping", true, SettingRefresh.LISTENER).described("Live windows",
        "Keeps windows updating while you swipe between them.")
        .saidAs("swiping between pages is laggy", "windows freeze when I swipe",
            "my phone feels slow");

    // ---- Battery and performance ----

    /** Lets the launcher do less while nothing is happening. */
    @NonNull
    public static final SettingDeclaration REDUCE_IDLE = SettingDeclaration.bool(
        "battery.reduce_idle", SettingDeclaration.Store.APP_PREFERENCES, "lazy_mode", false,
        SettingRefresh.LISTENER).described("Reduce idle activity",
        "Lets the launcher do less in the background while you are not using it.")
        .saidAs("my battery drains too fast", "my phone feels slow", "save battery");

    /** Glass that bends what is behind it. */
    @NonNull
    public static final SettingDeclaration GLASS_REFRACTION = SettingDeclaration.bool(
        "glass.refraction", SettingDeclaration.Store.APP_PREFERENCES, "fancier_glass", true,
        SettingRefresh.LISTENER).described("Glass refraction",
        "Makes glass surfaces bend and shine the way real glass does.")
        .saidAs("swiping between pages is laggy", "my phone feels slow",
            "the glass looks too fancy");

    /** Glass that blurs the live picture behind it as it changes. */
    @NonNull
    public static final SettingDeclaration LIVE_BLUR = SettingDeclaration.bool(
        "glass.live_blur", SettingDeclaration.Store.APP_PREFERENCES, "live_blur", false,
        SettingRefresh.RESTYLE).described("Live blur",
        "Blurs whatever moves behind glass surfaces, not just the wallpaper.")
        .saidAs("my battery drains too fast", "swiping between pages is laggy",
            "the blur looks stale").notOffered();

    /** The retro look of the terminal screen. */
    @NonNull
    public static final SettingDeclaration TERMINAL_EFFECT = SettingDeclaration.choice(
        "terminal.effect", SettingDeclaration.Store.APP_PREFERENCES, "terminal_retro_effect",
        Arrays.asList("none", "crt", "crt_green", "crt_amber", "crt_material", "tft"),
        Arrays.asList("none", "crt", "crt_green", "crt_amber", "crt_material", "tft"), "none",
        false, SettingRefresh.RESTYLE).described("Terminal effect",
        "Gives the terminal an old monitor look, or none.")
        .saidAs("I don't like the retro screen effect", "the screen looks like an old TV",
            "remove the scanlines", "I want a retro look")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    /** Whether the terminal's bell and other events flash the screen effect. */
    @NonNull
    public static final SettingDeclaration TERMINAL_SCREEN_EVENTS = SettingDeclaration.bool(
        "terminal.screen_events", SettingDeclaration.Store.APP_PREFERENCES,
        "terminal_screen_events", true, SettingRefresh.LISTENER).described("Screen events",
        "Lets the terminal's bell and other events flicker the screen effect.")
        .saidAs("the screen flickers when something beeps", "stop the flashing")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    /** The extra layer that blends the retro effect into the terminal. */
    @NonNull
    public static final SettingDeclaration TERMINAL_EFFECT_COMPOSITE = SettingDeclaration.bool(
        "terminal.effect_composite", SettingDeclaration.Store.APP_PREFERENCES,
        "terminal_retro_composite", false, SettingRefresh.LISTENER).described("Effect blending",
        "Blends the retro effect into the terminal's colours.")
        .saidAs("my battery drains too fast", "my phone feels slow").notOffered()
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    // ---- Status bar chips ----

    @NonNull
    public static final SettingDeclaration STATUS_BATTERY = SettingDeclaration.bool(
        "status.battery", SettingDeclaration.Store.APP_PREFERENCES, "status_widget_battery", false,
        SettingRefresh.RESTYLE).described("Battery in the status bar",
        "Shows the battery level in the status bar.")
        .saidAs("show the battery", "I can't see my battery level", "show the battery percentage")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    @NonNull
    public static final SettingDeclaration STATUS_MEDIA = SettingDeclaration.bool(
        "status.media", SettingDeclaration.Store.APP_PREFERENCES, "status_widget_media", true,
        SettingRefresh.RESTYLE).described("Media in the status bar",
        "Shows what is playing in the status bar.")
        .saidAs("show what's playing", "I can't see the song", "hide the music from the status bar")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    @NonNull
    public static final SettingDeclaration STATUS_WEATHER = SettingDeclaration.bool(
        "status.weather", SettingDeclaration.Store.APP_PREFERENCES, "status_widget_weather", true,
        SettingRefresh.RESTYLE).described("Weather in the status bar",
        "Shows the weather in the status bar.")
        .saidAs("I want to see the weather up top", "show the weather", "hide the weather")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    @NonNull
    public static final SettingDeclaration STATUS_CLOCK_AM_PM = SettingDeclaration.bool(
        "status.clock_am_pm", SettingDeclaration.Store.APP_PREFERENCES, "top_pane_clock_am_pm",
        true, SettingRefresh.RESTYLE).described("AM and PM on the clock",
        "Shows AM or PM beside the status bar clock.")
        .saidAs("the clock should show am/pm", "I can't tell morning from evening",
            "remove am/pm from the clock")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    // ---- Dock, Home and notifications ----

    /** Docked fills the screen's edge; Floating is a card with a gap around it. */
    @NonNull
    public static final SettingDeclaration DOCK_STYLE = SettingDeclaration.choice(
        "dock.style", SettingDeclaration.Store.APP_PREFERENCES, "app_launcher_dock_style",
        Arrays.asList("docked", "floating"), Arrays.asList("docked", "floating"), "docked", false,
        SettingRefresh.RESTYLE).readingStoredAs(
        TermuxAppSharedPreferences
            ::normalizeAppLauncherDockStyle).described("Dock style",
        "Whether the dock and bars sit on the screen's edge or float as cards.")
        .saidAs("the dock should float", "the dock touches the edge of the screen",
            "I want rounded cards", "make the dock square")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE);

    @NonNull
    public static final SettingDeclaration NOTIFICATION_DOTS = SettingDeclaration.bool(
        "notifications.dots", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_notification_dots", false, SettingRefresh.RESTYLE).described(
        "Notification dots", "Marks app icons that have notifications.")
        .saidAs("I can't tell which apps have messages", "show a dot on apps with notifications");

    @NonNull
    public static final SettingDeclaration NOTIFICATION_BADGE_STYLE = SettingDeclaration.choice(
        "notifications.badge_style", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_notification_badge_style", Arrays.asList("dot", "count"),
        Arrays.asList("dot", "count"), "dot", false, SettingRefresh.LISTENER).described(
        "Notification badge", "Whether an app icon shows a dot or the number of notifications.")
        .saidAs("I want to see how many notifications", "the dot is not enough");

    @NonNull
    public static final SettingDeclaration QUICK_REPLY_SWIPE = SettingDeclaration.bool(
        "home.quick_reply_swipe", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_quick_reply_swipe", true, SettingRefresh.RESTYLE).described(
        "Quick reply swipe", "Swipe up on an app icon that has a notification to reply from the dock.")
        .saidAs("I swipe up by accident", "icons open a reply when I swipe");

    @NonNull
    public static final SettingDeclaration DRAWER_SEARCH_ON_OPEN = SettingDeclaration.bool(
        "drawer.search_on_open", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_drawer_search_on_open", false, SettingRefresh.LISTENER).described(
        "Keyboard when the drawer opens", "Opens the keyboard as soon as the app drawer opens.")
        .saidAs("the keyboard pops up by itself when I open the drawer",
            "I want to search as soon as the drawer opens");

    @NonNull
    public static final SettingDeclaration MOST_USED_PAGE = SettingDeclaration.bool(
        "drawer.most_used_page", SettingDeclaration.Store.APP_PREFERENCES,
        "app_launcher_most_used_page", false, SettingRefresh.RESTYLE).described(
        "Most-used apps page", "Adds a drawer page ranked by how often you open each app.")
        .saidAs("I want my most used apps first", "add a page of my favourite apps");

    // ---- Terminals ----

    /** The engine a new terminal runs on: Termux's own, or libghostty where this phone can load it. */
    @NonNull
    public static final SettingDeclaration TERMINAL_CORE = SettingDeclaration.choice(
        "terminal.core", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERMINAL_ENGINE,
        Arrays.asList("termux", "libghostty"), Arrays.asList("standard", "ghostty"), "termux", false,
        SettingRefresh.LISTENER).described("Terminal core",
        "Which engine new terminals run on.")
        .saidAs("the terminal is slow", "use ghostty", "switch the terminal engine",
            "the terminal draws some glyphs wrong").notOffered();

    /** How many lines a terminal keeps, in termux.properties. */
    @NonNull
    public static final SettingDeclaration TERMINAL_SCROLLBACK = SettingDeclaration.integer(
        "terminal.scrollback", SettingDeclaration.Store.PROPERTIES,
        TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS,
        TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MIN,
        TermuxPropertyConstants.IVALUE_TERMINAL_TRANSCRIPT_ROWS_MAX, 100,
        TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS, SettingRefresh.RESTYLE)
        .described("Scrollback", "How many lines a terminal keeps for scrolling back.")
        .saidAs("I can't scroll back far enough", "keep more history in the terminal",
            "the terminal forgets old output");

    /** What the terminal bell does, in termux.properties. */
    @NonNull
    public static final SettingDeclaration TERMINAL_BELL = SettingDeclaration.choice(
        "terminal.bell", SettingDeclaration.Store.PROPERTIES,
        TermuxPropertyConstants.KEY_BELL_BEHAVIOUR,
        Arrays.asList("vibrate", "beep", "ignore"),
        Arrays.asList(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_VIBRATE,
            TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_BEEP,
            TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_IGNORE),
        TermuxPropertyConstants.DEFAULT_VALUE_BELL_BEHAVIOUR, false, SettingRefresh.RESTYLE)
        .described("Bell", "What a terminal does when a program rings its bell.")
        .saidAs("the phone vibrates when I press tab", "stop the terminal beeping",
            "turn off the bell");

    // ---- Linux apps ----

    /** Which display Linux apps open on, in a build with the Wayland host. */
    @NonNull
    public static final SettingDeclaration LINUX_DISPLAY_SERVER = SettingDeclaration.choice(
        "linux.display_server", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_LINUX_DISPLAY_BACKEND, Arrays.asList("x11", "wayland"),
        Arrays.asList("x11", "wayland"), TERMUX_APP.DEFAULT_LINUX_DISPLAY_BACKEND, false,
        SettingRefresh.LISTENER).described("Display server",
        "Whether Linux apps open on X11 or on Wayland, each app in its own window.")
        .saidAs("open linux apps in their own windows", "use wayland", "go back to x11")
        .notOffered();

    // ---- Camera cutout ----

    /** How the bar's rail and Floats keep clear of the camera: the phone's own report, or Custom. */
    @NonNull
    public static final SettingDeclaration CUTOUT_MODE = SettingDeclaration.choice(
        "cutout.mode", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_STATUS_BAR_CUTOUT_MODE, Arrays.asList("automatic", "custom"),
        Arrays.asList(TERMUX_APP.STATUS_BAR_CUTOUT_AUTO, TERMUX_APP.STATUS_BAR_CUTOUT_CUSTOM),
        "automatic", false, SettingRefresh.RESTYLE).described("Camera cutout",
        "Keeps clear of the camera where the phone reports it, or where you place it.")
        .saidAs("the camera covers the bar", "something is hidden under the camera",
            "my phone reports the camera in the wrong place");

    /** The room kept around the camera cutout, in dp. */
    @NonNull
    public static final SettingDeclaration CUTOUT_INSET = SettingDeclaration.integer(
        "cutout.inset", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_STATUS_BAR_CUTOUT_MARGIN, TERMUX_APP.MIN_STATUS_BAR_CUTOUT_MARGIN,
        TERMUX_APP.MAX_STATUS_BAR_CUTOUT_MARGIN, 1, TERMUX_APP.DEFAULT_STATUS_BAR_CUTOUT_MARGIN,
        SettingRefresh.RESTYLE).described("Room around the camera",
        "How much space is kept clear around the camera cutout, in dp.")
        .saidAs("things sit too close to the camera", "leave more room around the camera");

    // ---- Keyboard: typing ----

    @NonNull
    public static final SettingDeclaration KEYBOARD_KEY_SOUND = SettingDeclaration.bool(
        "keyboard.key_sound", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_SOUND_ENABLED,
        TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_SOUND_ENABLED, SettingRefresh.LISTENER)
        .described("Key sound", "Plays a click as you type on the launcher keyboard.")
        .saidAs("the keyboard is silent", "turn off the typing sound", "keys should click");

    @NonNull
    public static final SettingDeclaration KEYBOARD_KEY_POPUP = SettingDeclaration.bool(
        "keyboard.key_popup", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_POPUP, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_POPUP,
        SettingRefresh.LISTENER).described("Key preview",
        "Shows the letter above your finger as you press a key.")
        .saidAs("I can't see which key I pressed", "hide the key popups");

    @NonNull
    public static final SettingDeclaration KEYBOARD_HAPTICS = SettingDeclaration.bool(
        "keyboard.haptics", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_IN_APP_KEYBOARD_HAPTICS_ENABLED,
        TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HAPTICS_ENABLED, SettingRefresh.LISTENER)
        .described("Vibrate on keypress", "Gives a short vibration for each key you press.")
        .saidAs("the keyboard vibrates too much", "stop the keyboard buzzing",
            "I want to feel the keys");

    /**
     * The switch for Terl's tiling desktop and edit mode, off by default until the switch-over.
     * Not in Settings and never offered by the Assistant; set it with {@code launcherctl config}.
     */
    @NonNull
    public static final SettingDeclaration TERL_DESKTOP = SettingDeclaration.bool(
        "terl.desktop", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERL_DESKTOP,
        TERMUX_APP.DEFAULT_VALUE_TERL_DESKTOP, SettingRefresh.RESTYLE).described("Terl desktop",
        "Opens on Home and Workspaces of windows, in place of the dock and status bar.")
        .saidAs("turn on the tiling desktop", "try Terl").notOffered();

    /**
     * Under the Terl desktop, the extra-keys row above the keyboard; off by default (#424). Not
     * offered by the Assistant until the switch-over, as {@link #TERL_DESKTOP} is not.
     */
    @NonNull
    public static final SettingDeclaration TERL_EXTRA_KEYS = SettingDeclaration.bool(
        "terl.extra_keys", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERL_EXTRA_KEYS,
        TERMUX_APP.DEFAULT_VALUE_TERL_EXTRA_KEYS, SettingRefresh.RESTYLE).described("Extra keys row",
        "Shows a row of extra keys above the keyboard, for typing with your phone's own keyboard.")
        .saidAs("show the extra keys", "bring back the key row above the keyboard").notOffered();

    /**
     * Under the Terl desktop, the control bar's Drawer item in Home's row; on by default (#436).
     * Not offered by the Assistant until the switch-over, as {@link #TERL_DESKTOP} is not.
     */
    @NonNull
    public static final SettingDeclaration TERL_DRAWER_TOOL = SettingDeclaration.bool(
        "terl.drawer_tool", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERL_DRAWER_TOOL,
        TERMUX_APP.DEFAULT_VALUE_TERL_DRAWER_TOOL, SettingRefresh.RESTYLE).described("Drawer item",
        "Shows the drawer's button in Home's bottom row.")
        .saidAs("show the drawer button", "hide the drawer button").notOffered();

    /**
     * Under the Terl desktop, the side the control bar's rail stands down in landscape (#427). Its
     * row comes with the Control bar card (#452); until the switch-over it is not offered by the
     * Assistant, as {@link #TERL_DESKTOP} is not.
     */
    @NonNull
    public static final SettingDeclaration BAR_RAIL_SIDE = SettingDeclaration.choice(
        "bar.rail_side", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_TERL_BAR_RAIL_SIDE, Arrays.asList("right", "left"),
        Arrays.asList(TERMUX_APP.TERL_BAR_RAIL_SIDE_RIGHT, TERMUX_APP.TERL_BAR_RAIL_SIDE_LEFT),
        "right", false, SettingRefresh.RESTYLE).described("Rail side",
        "Which side of the screen the control bar stands down in landscape.")
        .saidAs("move the bar to the left in landscape", "put the rail on the other side")
        .notOffered();

    // ---- Keys: the leader every desktop binding starts from ----

    /**
     * The leader (ADR 0042): the Terl key, Ctrl+Alt, or a Custom chord ({@code keys.leader_chord},
     * which the app's adapter judges). The launcher's preference listener re-reads it.
     */
    @NonNull
    public static final SettingDeclaration KEYS_LEADER = SettingDeclaration.choice("keys.leader",
        SettingDeclaration.Store.APP_PREFERENCES, "keys_leader",
        Arrays.asList("terl", "ctrl+alt", "custom"), Arrays.asList("terl", "ctrl+alt", "custom"),
        "terl", false, SettingRefresh.LISTENER).described("Leader",
        "What starts every shortcut: the Terl key (Ctrl+Alt on a hardware keyboard), Ctrl+Alt, "
            + "or keys you choose.")
        .saidAs("my shortcuts clash with an app", "Ctrl+Alt shortcuts get in the way",
            "change the shortcut key", "use a different key for shortcuts");

    /**
     * The launcher keyboard's layout under Terl. Custom is the user's own layout.xml; the app's
     * TerlKeyboardLayout holds the same names.
     */
    @NonNull
    public static final SettingDeclaration KEYBOARD_LAYOUT = SettingDeclaration.choice(
        "keyboard.layout", SettingDeclaration.Store.APP_PREFERENCES, "terl_keyboard_layout",
        Arrays.asList("qwerty", "qwertz", "azerty", "dvorak", "colemak", "custom"),
        Arrays.asList("qwerty", "qwertz", "azerty", "dvorak", "colemak", "custom"), "qwerty", false,
        SettingRefresh.LISTENER).described("Keyboard layout",
        "The letters the launcher keyboard types: QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, or "
            + "your own layout.")
        .saidAs("I use a different keyboard layout", "switch to dvorak", "use azerty",
            "the keys are in the wrong place");

    /**
     * Under the Terl desktop, whether the control bar's reading slot stands (#428); on by default.
     * Its row comes with the Control bar card (#452); until the switch-over it is not offered by
     * the Assistant, as {@link #TERL_DESKTOP} is not.
     */
    @NonNull
    public static final SettingDeclaration BAR_READING = SettingDeclaration.bool(
        "bar.reading", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERL_BAR_READING,
        TERMUX_APP.DEFAULT_VALUE_TERL_BAR_READING, SettingRefresh.RESTYLE).described("Reading",
        "Shows one reading (CPU, GPU, memory, battery, weather or media) at the end of the bar's top row.")
        .saidAs("show the battery on the bar", "hide the reading on the bar").notOffered();

    /**
     * The reading the slot shows, as a tap on it chose last (#428). The slot's own tap writes it;
     * not offered by the Assistant, as {@link #BAR_READING} is not.
     */
    @NonNull
    public static final SettingDeclaration BAR_READING_KIND = SettingDeclaration.choice(
        "bar.reading_kind", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_TERL_BAR_READING_KIND,
        Arrays.asList("cpu", "gpu", "ram", "battery", "weather", "media"),
        Arrays.asList("cpu", "gpu", "ram", "battery", "weather", "media"),
        "battery", false, SettingRefresh.RESTYLE).described("Reading shown",
        "Which reading stands at the end of the bar's top row.")
        .saidAs("show the weather on the bar", "show the memory on the bar").notOffered();

    /**
     * Under the Terl desktop, the control bar's margin in dp (#452); 0 stands it flush with square
     * bottom corners. Until the switch-over it is not offered by the Assistant, as
     * {@link #TERL_DESKTOP} is not.
     */
    @NonNull
    public static final SettingDeclaration BAR_MARGIN = SettingDeclaration.integer(
        "bar.margin", SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERL_BAR_MARGIN,
        TERMUX_APP.MIN_TERL_BAR_MARGIN, TERMUX_APP.MAX_TERL_BAR_MARGIN, 1,
        TERMUX_APP.DEFAULT_VALUE_TERL_BAR_MARGIN, SettingRefresh.RESTYLE).described("Margin",
        "The air around the control bar, in dp. 0 stands it flush with the screen's edges.")
        .saidAs("make the bar flush", "the bar floats too far from the edge").notOffered();

    /** Every declaration, in the order {@code config list} shows them. */
    @NonNull
    public static final List<SettingDeclaration> ALL =
        Collections.unmodifiableList(Arrays.asList(THEME_MODE, TERMINAL_CONTRAST, TEXT_SIZE,
            ANIMATION_SPEED, PAGE_TRANSITION, IDLE_ANIMATIONS, IDLE_TEAR_RATE, CURSOR_TRAIL,
            WALLPAPER_PARALLAX, LIVE_WINDOWS, REDUCE_IDLE, GLASS_REFRACTION, LIVE_BLUR,
            TERMINAL_EFFECT, TERMINAL_SCREEN_EVENTS, TERMINAL_EFFECT_COMPOSITE, STATUS_BATTERY,
            STATUS_MEDIA, STATUS_WEATHER, STATUS_CLOCK_AM_PM, DOCK_STYLE, NOTIFICATION_DOTS,
            NOTIFICATION_BADGE_STYLE, QUICK_REPLY_SWIPE, DRAWER_SEARCH_ON_OPEN, MOST_USED_PAGE,
            TERMINAL_CORE, TERMINAL_SCROLLBACK, TERMINAL_BELL, LINUX_DISPLAY_SERVER, CUTOUT_MODE,
            CUTOUT_INSET, KEYBOARD_KEY_SOUND, KEYBOARD_KEY_POPUP, KEYBOARD_HAPTICS, TERL_DESKTOP,
            TERL_EXTRA_KEYS, TERL_DRAWER_TOOL, BAR_RAIL_SIDE, BAR_READING, BAR_READING_KIND, BAR_MARGIN,
            KEYS_LEADER,
            KEYBOARD_LAYOUT));

    /**
     * The app-preferences keys the {@code group} reset removes: its declarations' keys, and the
     * font size families' ({@link FontSizeSettingsAdapter#resetKeyPrefixes}, per display).
     */
    @NonNull
    public static Set<String> resetKeys(@NonNull SettingDeclaration.ResetGroup group) {
        Set<String> keys = new HashSet<>();
        for (SettingDeclaration declaration : ALL) {
            if (declaration.resetGroup == group
                && declaration.store == SettingDeclaration.Store.APP_PREFERENCES)
                keys.add(declaration.key);
        }
        return keys;
    }

    /** Whether the {@code group} reset removes {@code key}, a declared key or a font size's. */
    public static boolean resets(@NonNull SettingDeclaration.ResetGroup group, @NonNull String key) {
        return resetKeys(group).contains(key) || FontSizeSettingsAdapter.resets(group, key);
    }

    /**
     * The declaration whose key is {@code key} in the app preferences, else null. Font sizes are
     * not here: their range depends on the screen ({@link FontSizeSettingsAdapter}).
     */
    @Nullable
    public static SettingDeclaration forPreferenceKey(@NonNull String key) {
        for (SettingDeclaration declaration : ALL) {
            if (declaration.store == SettingDeclaration.Store.APP_PREFERENCES
                && declaration.key.equals(key)) return declaration;
        }
        return null;
    }

    @NonNull
    private static List<String> contrastLevels() {
        List<String> values = new ArrayList<>();
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) values.add(level.value);
        return values;
    }
}
