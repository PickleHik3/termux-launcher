package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * The Glass switch every built-in widget's settings carry, per widget: off by default, the solid
 * card; on, the widget wears the Look's glass — Base's blur, opacity, tint, grain, rim and Fancier
 * Glass, from the shared blur frames, drawn by the same slab a terminal pane wears
 * ({@code PaneGlass} over {@code LookGlassStyle}). A detached terminal, keyboard, status bar or
 * dock never reaches it. Where the glass cannot be shown or the system asks for contrast
 * ({@link #solidFallback}) the widget wears its solid card.
 *
 * <p>Pure: the view asks here and applies the answer.
 */
public final class BuiltinWidgetGlass {

    /** The key the switch is stored under in a widget's settings, and its value while on. */
    public static final String CONFIG_KEY = "glass";
    public static final String ON = "on";
    /** The explicit off: solid whatever the page says. Absent (or empty) follows the page. */
    public static final String OFF = "off";

    /**
     * The opt-in Bare appearance (glossary: Bare), offered on Clock and Weather as a fourth
     * choice of the same row. Glass is off while it holds; the Look's Clear stop never sets it.
     */
    public static final String BARE = "bare";

    private BuiltinWidgetGlass() {}

    /** Whether the stored choice is Bare. */
    public static boolean choseBare(@Nullable String stored) { return BARE.equals(stored); }

    /**
     * The values the Glass row offers a kind, in order ("" follows the page): Page, On, Off, and
     * Bare for Clock and Weather. Glance is always Bare, so it has no row: empty. A null kind
     * (unknown) gets the three every widget has.
     */
    @NonNull public static String[] choices(@Nullable BuiltinWidgetKind kind) {
        if (kind == BuiltinWidgetKind.GLANCE) return new String[0];
        if (kind == BuiltinWidgetKind.CLOCK || kind == BuiltinWidgetKind.WEATHER)
            return new String[] {"", ON, OFF, BARE};
        return new String[] {"", ON, OFF};
    }

    /**
     * Whether a widget wears glass: its own choice when it made one ("on" or "off"), else the
     * page's "Widgets on glass" switch. Stored before the switch existed, an absent value was
     * the default off; it now follows the page, and a stored "on" stays an explicit on.
     */
    public static boolean resolve(@Nullable String stored, boolean pageOn) {
        if (ON.equals(stored)) return true;
        if (OFF.equals(stored) || BARE.equals(stored)) return false;
        return pageOn;
    }

    /**
     * The settings value for the three-state choice: {@link #ON}, {@link #OFF}, or left out
     * (follow the page) like every empty field.
     */
    public static void write(@NonNull Map<String, String> values, @Nullable String choice) {
        values.put(CONFIG_KEY,
            ON.equals(choice) || OFF.equals(choice) || BARE.equals(choice) ? choice : "");
    }

    /**
     * Whether the widget wears the Look's slab: its switch on, a live widget (a picker preview
     * samples nothing), and the Look's glass worn at all (the launcher on the wallpaper).
     */
    public static boolean slabShown(boolean widgetGlass, boolean preview, boolean lookGlass) {
        return widgetGlass && !preview && lookGlass;
    }

    /**
     * Whether a widget that would wear the Look's glass wears its solid card instead (spec #101,
     * "Glass"; Apple's Reduce Transparency and Increase Contrast, in Android's signals): the
     * launcher is off the wallpaper, the shared frame is not ready, battery saver is on, outline
     * text is on, or the system contrast is high. Legibility wins over the material.
     */
    public static boolean solidFallback(boolean onWallpaper, boolean frameReady,
                                        boolean batterySaver, boolean outlineText,
                                        boolean highContrast) {
        return !onWallpaper || !frameReady || batterySaver || outlineText || highContrast;
    }

    /**
     * Whether the widget wears the Look's glass now: its slab shown ({@link #slabShown}) and no
     * {@link #solidFallback} condition holding.
     */
    public static boolean glassWorn(boolean widgetGlass, boolean preview, boolean onWallpaper,
                                    boolean frameReady, boolean batterySaver,
                                    boolean outlineText, boolean highContrast) {
        return slabShown(widgetGlass, preview, onWallpaper)
            && !solidFallback(onWallpaper, frameReady, batterySaver, outlineText, highContrast);
    }

    /**
     * Whether a widget that asks to be drawn Bare (glossary: Bare) is: its ink straight on the
     * wallpaper, with no card, rim or slab. Only the widget asks (Glance always, Clock and
     * Weather by their own choice); nothing of the Look turns it on, its Clear stop included,
     * which is why no Look value is taken here. Where there is no wallpaper behind the widget to
     * measure (the launcher off the wallpaper) or the system asks for contrast (outline text, high
     * contrast) the widget wears its solid card instead: legibility wins, as it does over glass.
     * A picker card draws Bare on the picker's own ground.
     */
    public static boolean bareWorn(boolean asked, boolean preview, boolean onWallpaper,
                                   boolean outlineText, boolean highContrast) {
        return asked && (preview || onWallpaper) && !outlineText && !highContrast;
    }

    /**
     * Whether the card is the dark one: the page's pale ink (the polarity the dock is drawn in),
     * or night, which forces it whatever the ink. Unknown polarity (no chrome to ask) keeps the
     * theme's own card.
     */
    public static boolean darkCard(boolean night,
                                   @Nullable com.termux.app.chrome.ChromeInk.Polarity polarity) {
        return night || polarity == com.termux.app.chrome.ChromeInk.Polarity.PALE_INK;
    }

    /**
     * Whether the solid card draws its rim at the outline role: card versus the wallpaper behind
     * it under {@link com.termux.app.chrome.OnGlass#CARD_EDGE_RATIO}, where it would vanish.
     */
    public static boolean edgeRimWanted(int card, int wallpaper) {
        return !com.termux.app.chrome.OnGlass.cardShowsEdge(card, wallpaper);
    }

    /** The system's asks for the solid card, read on a dress pass; see {@link #solidFallback}. */
    public static final class SystemAsks {
        public final boolean batterySaver;
        public final boolean outlineText;
        public final boolean highContrast;

        public SystemAsks(boolean batterySaver, boolean outlineText, boolean highContrast) {
            this.batterySaver = batterySaver;
            this.outlineText = outlineText;
            this.highContrast = highContrast;
        }

        /** Read from the platform. Anything unreadable asks nothing. */
        @NonNull public static SystemAsks read(@NonNull android.content.Context context) {
            return new SystemAsks(batterySaver(context), outlineText(context),
                highContrast(context));
        }

        /** How long one reading answers every widget's dress pass. */
        static final long FRESH_MS = 1000L;
        @Nullable private static SystemAsks sLast;
        private static long sLastAt;

        /**
         * {@link #read}, shared by every widget for {@link #FRESH_MS}: a Corners slider re-dresses
         * every widget on every tick, and three system services per widget per tick is work the
         * answer does not need. Main thread only, as every dress pass is.
         */
        @NonNull public static SystemAsks current(@NonNull android.content.Context context) {
            long now = android.os.SystemClock.uptimeMillis();
            if (sLast == null || now - sLastAt > FRESH_MS || now < sLastAt) {
                sLast = read(context.getApplicationContext() != null
                    ? context.getApplicationContext() : context);
                sLastAt = now;
            }
            return sLast;
        }

        private static boolean batterySaver(@NonNull android.content.Context context) {
            try {
                android.os.PowerManager power = (android.os.PowerManager)
                    context.getSystemService(android.content.Context.POWER_SERVICE);
                return power != null && power.isPowerSaveMode();
            } catch (RuntimeException e) {
                return false;
            }
        }

        /**
         * Android 16's outline text, high-contrast text before it: the secure setting both are
         * stored under.
         */
        private static boolean outlineText(@NonNull android.content.Context context) {
            try {
                return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                    "high_text_contrast_enabled", 0) == 1;
            } catch (RuntimeException e) {
                return false;
            }
        }

        /** The system contrast level at High (Android 14's contrast setting; 1 is High). */
        private static boolean highContrast(@NonNull android.content.Context context) {
            if (android.os.Build.VERSION.SDK_INT < 34) return false;
            try {
                android.app.UiModeManager uiMode = (android.app.UiModeManager)
                    context.getSystemService(android.content.Context.UI_MODE_SERVICE);
                return uiMode != null && uiMode.getContrast() >= HIGH_CONTRAST;
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    /** {@code UiModeManager#getContrast} at High, less float noise; Medium is 0.5. */
    static final float HIGH_CONTRAST = 0.99f;

    /**
     * Whether the Look's corner a widget is fed really moved from the one it wears, so its card
     * and inner shapes are re-dressed (in place). Compared at a quarter pixel, so float noise from
     * the same dp redraws nothing.
     */
    public static boolean cornersMoved(float styleRadiusPx, float lookRadiusPx) {
        return Math.abs(styleRadiusPx - lookRadiusPx) > 0.25f;
    }
}
