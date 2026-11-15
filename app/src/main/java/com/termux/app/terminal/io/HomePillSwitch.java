package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * Whether the Home pill is on. Home's extra-keys kind key means the pill (the stack never docks
 * the bar on Home), and the global toolbar switch hides it with every other bar. Turning it on with
 * no key set for Home writes the starter keys first, so the pill never shows empty; turning it off
 * leaves termux.properties alone.
 */
public final class HomePillSwitch {

    /** The narrow seam over the stores, so the switch is JVM-testable. */
    public interface Host {
        boolean homeExtraKeysShown(@NonNull PlaceOrientation orientation);

        void setHomeExtraKeysShown(@NonNull PlaceOrientation orientation, boolean shown);

        /** The global toolbar switch. */
        boolean toolbarOn();

        /** The stored value of a property line as written, or null when absent. */
        @Nullable String rawLine(@NonNull String key);

        void writeProperty(@NonNull String key, @NonNull String value);

        /** Asks for termux.properties to be read again, as the editor's Save does. */
        void reloadProperties();
    }

    @NonNull private final Host mHost;

    public HomePillSwitch(@NonNull Host host) {
        mHost = host;
    }

    public boolean isOn(@NonNull PlaceOrientation orientation) {
        return mHost.homeExtraKeysShown(orientation) && mHost.toolbarOn();
    }

    /** Whether Home's keys are switched on in either orientation, whatever the toolbar switch says. */
    public boolean keysOn() {
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            if (mHost.homeExtraKeysShown(orientation)) return true;
        }
        return false;
    }

    public void setOn(boolean on) {
        if (on) {
            String line = mHost.rawLine(TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME);
            if (line == null || line.trim().isEmpty()) {
                mHost.writeProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME,
                    TermuxPropertyConstants.HOME_PILL_STARTER_EXTRA_KEYS);
                mHost.reloadProperties();
            }
        }
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            mHost.setHomeExtraKeysShown(orientation, on);
        }
    }

    /** The host over the real stores; {@code rawLine} reads the stored property as the editor does. */
    @NonNull
    public static Host hostFor(@NonNull PlaceLayoutStore places,
                               @NonNull TermuxAppSharedPreferences preferences,
                               @NonNull java.util.function.Function<String, String> rawLine,
                               @NonNull Runnable reload) {
        return new Host() {
            @Override
            public boolean homeExtraKeysShown(@NonNull PlaceOrientation orientation) {
                return places.kindShows(PageKind.HOME, orientation, Element.EXTRA_KEYS);
            }

            @Override
            public void setHomeExtraKeysShown(@NonNull PlaceOrientation orientation, boolean shown) {
                places.setKindShows(PageKind.HOME, orientation, Element.EXTRA_KEYS, shown);
            }

            @Override
            public boolean toolbarOn() {
                return preferences.shouldShowTerminalToolbar();
            }

            @Nullable
            @Override
            public String rawLine(@NonNull String key) {
                return rawLine.apply(key);
            }

            @Override
            public void writeProperty(@NonNull String key, @NonNull String value) {
                TermuxPropertiesFile.write(key, value);
            }

            @Override
            public void reloadProperties() {
                reload.run();
            }
        };
    }
}
