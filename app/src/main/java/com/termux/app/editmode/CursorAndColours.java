package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.colours.TerminalColours;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

/**
 * The Text tab's cursor and colour scheme, as {@link EditSession.Keyed} settings over the files the
 * terminal reads: the cursor's shape and blink in termux.properties (as {@code terminal.cursor_style}
 * and {@code terminal.cursor_blink} write them), the scheme through {@link TerminalColours}. Every
 * other key goes to {@code others}, the card's settings kept in the preferences.
 *
 * <p>Blink reads "off" or "on:rate", so an Undo back to a blink the user set by hand (500 ms, say)
 * puts that rate back; "on" alone writes {@link SettingDeclarations#CURSOR_BLINK_ON_RATE}.
 */
public final class CursorAndColours implements EditSession.Keyed {

    /** What the cursor and colours need from the screen. */
    public interface Host {
        /** termux.properties' value for {@code key}, or null when it holds none. */
        @Nullable
        String property(@NonNull String key);

        /** Writes termux.properties' {@code key}; every terminal wears the cursor at once. */
        void writeCursorProperty(@NonNull String key, @NonNull String value);

        @NonNull
        TerminalColours colours();

        /** The colour scheme changed: the terminals take it now, the chrome with the window's rebuild. */
        void coloursChanged();
    }

    @NonNull private final Host mHost;
    @NonNull private final EditSession.Keyed mOthers;

    public CursorAndColours(@NonNull Host host, @NonNull EditSession.Keyed others) {
        mHost = host;
        mOthers = others;
    }

    @Nullable
    @Override
    public Object get(@NonNull String key) {
        switch (key) {
            case SCHEME: return mHost.colours().current();
            case CURSOR_STYLE: {
                String stored = mHost.property(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE);
                return SettingDeclarations.TERMINAL_CURSOR_STYLE.storedChoiceOrDefault(stored);
            }
            case CURSOR_BLINK: {
                int rate = blinkRate();
                return rate > 0 ? "on:" + rate : "off";
            }
            default: return mOthers.get(key);
        }
    }

    @Override
    public void set(@NonNull String key, @NonNull Object value) {
        switch (key) {
            case SCHEME: {
                String before = mHost.colours().current();
                if (mHost.colours().choose(value.toString())
                    && !before.equals(mHost.colours().current())) mHost.coloursChanged();
                return;
            }
            case CURSOR_STYLE: {
                String style = value.toString();
                if (!SettingDeclarations.TERMINAL_CURSOR_STYLE.storedChoices.contains(style)) return;
                if (style.equals(get(CURSOR_STYLE))) return;
                mHost.writeCursorProperty(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE, style);
                return;
            }
            case CURSOR_BLINK: {
                int rate = rateOf(value.toString());
                if (rate < 0 || rate == blinkRate()) return;
                // On over a blink already on keeps its rate.
                if ("on".equals(value.toString()) && blinkRate() > 0) return;
                mHost.writeCursorProperty(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE,
                    Integer.toString(rate));
                return;
            }
            default: mOthers.set(key, value);
        }
    }

    /** The blink rate in force: 0 for off, or one inside the terminal's range. */
    private int blinkRate() {
        String stored = mHost.property(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_BLINK_RATE);
        if (stored == null) return 0;
        try {
            int rate = Integer.parseInt(stored.trim());
            if (rate < TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN) return 0;
            return Math.min(rate, TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The rate a blink value writes; -1 for one that names none. */
    private static int rateOf(@NonNull String value) {
        if ("off".equals(value)) return 0;
        if ("on".equals(value)) return SettingDeclarations.CURSOR_BLINK_ON_RATE;
        if (!value.startsWith("on:")) return -1;
        try {
            int rate = Integer.parseInt(value.substring(3));
            return rate >= TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MIN
                && rate <= TermuxPropertyConstants.IVALUE_TERMINAL_CURSOR_BLINK_RATE_MAX ? rate : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
