package com.termux.app.launcher.widget.builtin;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one place a retired widget kind's id lives on (spec #101, "Kinds and aliases"). A stored id
 * never changes: a record placed as an Analog clock keeps {@code clock.analog} in the store for
 * ever. When the kinds merge, the old id is kept here as an alias that opens the new kind with the
 * settings that make it look the way the old one did, under whatever the record stores itself.
 *
 * <p>The API is two questions a caller asks of a stored id: which kind opens it
 * ({@link #kindOf}), and with which settings ({@link #settings}). {@link BuiltinWidgetKind#fromId}
 * asks the first, so every caller that looks a record's kind up gets the new kind; the host binds
 * the second. A merge adds one {@link #alias} line per retired id to {@link #ALIASES} and nothing
 * else: the picker lists only {@link BuiltinWidgetKind#values()}, which no longer carry the old
 * ones.</p>
 */
public final class BuiltinWidgetAliases {
    private BuiltinWidgetAliases() { }

    /** A retired id: the kind it opens as, and the settings it opens with. */
    public static final class Alias {
        /** The id as stored, never rewritten. */
        @NonNull public final String storedId;
        @NonNull public final BuiltinWidgetKind kind;
        /** Settings the old kind implied; a value the record stores itself wins over these. */
        @NonNull public final Map<String, String> settings;

        Alias(@NonNull String storedId, @NonNull BuiltinWidgetKind kind,
              @NonNull Map<String, String> settings) {
            this.storedId = storedId;
            this.kind = kind;
            this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }
    }

    /**
     * Every retired id, by stored id. The Clock (#112): the analog clock opens on the Analog face
     * with its second hand and its two cities (the defaults it showed while none was set, kept for
     * the time zones of #113), the digital clock on the Numerals face. Neither stores a 24-hour
     * choice, so both keep following the phone's, as they did.
     */
    private static final Map<String, Alias> ALIASES = new LinkedHashMap<>();

    static {
        alias("clock.analog", BuiltinWidgetKind.CLOCK,
            ClockWidgetSettings.KEY_FACE, ClockFace.ANALOG.key,
            ClockWidgetSettings.KEY_SECONDS, ClockWidgetSettings.ON,
            ClockWidgetSettings.KEY_ZONE1, ClockWidgetSettings.LEGACY_ZONE1,
            ClockWidgetSettings.KEY_ZONE2, ClockWidgetSettings.LEGACY_ZONE2);
        alias("clock.digital", BuiltinWidgetKind.CLOCK,
            ClockWidgetSettings.KEY_FACE, ClockFace.NUMERALS.key,
            ClockWidgetSettings.KEY_SECONDS, ClockWidgetSettings.OFF);
        // The Calendar (#114): the agenda opens on today's events, the month on the month.
        alias("agenda", BuiltinWidgetKind.CALENDAR,
            CalendarWidgetSupport.KEY_VIEW, CalendarWidgetSupport.VIEW_EVENTS);
        alias("calendar.month", BuiltinWidgetKind.CALENDAR,
            CalendarWidgetSupport.KEY_VIEW, CalendarWidgetSupport.VIEW_MONTH);
    }

    /**
     * The Device (#117): the battery widget opens on the battery first, which is how Device always
     * leads, and the system widget as Device. Neither stored a setting Device reads.
     */
    static {
        alias("battery", BuiltinWidgetKind.DEVICE);
        alias("system", BuiltinWidgetKind.DEVICE);
    }

    /** Files {@code storedId} as opening {@code kind} with {@code keysAndValues} (key, value, ...). */
    private static void alias(@NonNull String storedId, @NonNull BuiltinWidgetKind kind,
                              @NonNull String... keysAndValues) {
        Map<String, String> settings = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            settings.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        ALIASES.put(storedId, new Alias(storedId, kind, settings));
    }

    /** The alias {@code storedId} is, or null for a current kind's id or an unknown one. */
    @Nullable public static Alias of(@Nullable String storedId) {
        return storedId == null ? null : ALIASES.get(storedId);
    }

    /** Whether {@code storedId} is a retired kind's. */
    public static boolean isAlias(@Nullable String storedId) {
        return of(storedId) != null;
    }

    /**
     * The kind a stored id opens as: a current kind's own, a retired one's new kind, or null for
     * an id this build does not know.
     */
    @Nullable public static BuiltinWidgetKind kindOf(@Nullable String storedId) {
        if (storedId == null) return null;
        BuiltinWidgetKind current = BuiltinWidgetKind.currentFromId(storedId);
        if (current != null) return current;
        Alias alias = ALIASES.get(storedId);
        return alias == null ? null : alias.kind;
    }

    /**
     * The settings a record under {@code storedId} opens with: what it stores, over what its
     * retired kind implied. A current kind's settings come back as stored. Never writes anything:
     * the record keeps its own bundle until the user saves its settings.
     */
    @NonNull public static Bundle settings(@Nullable String storedId, @Nullable Bundle stored) {
        Bundle out = stored == null ? new Bundle() : new Bundle(stored);
        Alias alias = of(storedId);
        if (alias == null) return out;
        for (Map.Entry<String, String> entry : alias.settings.entrySet()) {
            String own = out.getString(entry.getKey());
            if (own == null || own.isEmpty()) out.putString(entry.getKey(), entry.getValue());
        }
        return out;
    }
}
