package com.termux.app.upgrade;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the upgrade from v1 to Terl removes from the launcher's preferences, given what they hold:
 * every key whose fate ({@link TerlUpgradeFates}) is {@code DROP} or {@code FRESH}. Keys v1 does
 * not name stay. Once {@link #KEY_DONE} is stored the plan is empty, so the upgrade runs once.
 */
public final class TerlUpgradePlan {

    /** The one-time marker: the upgrade version this install has run (absent for none). */
    public static final String KEY_DONE = "terl_upgrade_done";
    /** The version the marker stores. */
    public static final int VERSION = 1;

    /** Whether the upgrade still has to run. */
    public final boolean due;
    /** The keys it removes, sorted; empty when not {@link #due}. */
    @NonNull public final Set<String> removed;

    private TerlUpgradePlan(boolean due, @NonNull Set<String> removed) {
        this.due = due;
        this.removed = Collections.unmodifiableSet(removed);
    }

    @NonNull
    public static TerlUpgradePlan of(@NonNull Map<String, ?> stored) {
        if (stored.containsKey(KEY_DONE)) return new TerlUpgradePlan(false, new TreeSet<>());
        Set<String> removed = new TreeSet<>();
        for (String key : stored.keySet()) {
            if (key != null && TerlUpgradeFates.removes(key)) removed.add(key);
        }
        return new TerlUpgradePlan(true, removed);
    }
}
