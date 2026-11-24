package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One setting written down once: its dotted id, its kind and range or choices, its default, the
 * store and key it already uses and the refresh a save asks for. A declaration is a constant
 * ({@link SettingDeclarations}), so the store's getters take their range from it and each range
 * is written once.
 *
 * <p>Values cross every door as text: an integer as its digits, a choice as its name. A choice's
 * name may differ from what its store holds (Theme's {@code dark} is stored as {@code true}); the
 * declaration maps one to the other, so storage does not move.</p>
 */
public final class SettingDeclaration {

    /** What kind of value a setting holds. */
    public enum Kind { INTEGER, CHOICE }

    /** The store a setting already lives in. */
    public enum Store {
        /** The app's shared preferences ({@code TermuxAppSharedPreferences}). */
        APP_PREFERENCES,
        /** {@code termux.properties}. */
        PROPERTIES
    }

    @NonNull public final String id;
    @NonNull public final Kind kind;
    @NonNull public final Store store;
    @NonNull public final String key;
    @NonNull public final SettingRefresh refresh;

    /** An integer's range, step and default; unused by a choice. */
    public final int min;
    public final int max;
    public final int step;
    public final int defaultInt;

    /** A choice's names in order, what its store holds for each, and the default's index. */
    @NonNull public final List<String> choices;
    @NonNull public final List<String> storedChoices;
    private final int mDefaultChoice;
    /** Whether a choice's names run from less to more, so a step moves along them. */
    public final boolean ordered;
    /** Whether a settings file carries it; false for secrets and device-bound values. */
    public final boolean exported;

    private SettingDeclaration(@NonNull String id, @NonNull Kind kind, @NonNull Store store,
                               @NonNull String key, @NonNull SettingRefresh refresh, int min,
                               int max, int step, int defaultInt, @NonNull List<String> choices,
                               @NonNull List<String> storedChoices, int defaultChoice,
                               boolean ordered, boolean exported) {
        this.id = id;
        this.kind = kind;
        this.store = store;
        this.key = key;
        this.refresh = refresh;
        this.min = min;
        this.max = max;
        this.step = step;
        this.defaultInt = defaultInt;
        this.choices = choices;
        this.storedChoices = storedChoices;
        mDefaultChoice = defaultChoice;
        this.ordered = ordered;
        this.exported = exported;
    }

    /** This declaration kept out of a settings file's export and apply. */
    @NonNull
    public SettingDeclaration notExported() {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, false);
    }

    /** A whole number from {@code min} to {@code max}, stepped by {@code step}. */
    @NonNull
    public static SettingDeclaration integer(@NonNull String id, @NonNull Store store,
                                             @NonNull String key, int min, int max, int step,
                                             int defaultValue, @NonNull SettingRefresh refresh) {
        if (min > max || step <= 0 || defaultValue < min || defaultValue > max)
            throw new IllegalArgumentException(id + ": bad range");
        return new SettingDeclaration(id, Kind.INTEGER, store, key, refresh, min, max, step,
            defaultValue, Collections.emptyList(), Collections.emptyList(), -1, true, true);
    }

    /**
     * One of {@code names}, stored as the same-placed entry of {@code stored}. {@code ordered}
     * when the names run from less to more, so a step moves along them.
     */
    @NonNull
    public static SettingDeclaration choice(@NonNull String id, @NonNull Store store,
                                            @NonNull String key, @NonNull List<String> names,
                                            @NonNull List<String> stored,
                                            @NonNull String defaultName, boolean ordered,
                                            @NonNull SettingRefresh refresh) {
        int defaultIndex = names.indexOf(defaultName);
        if (names.isEmpty() || names.size() != stored.size() || defaultIndex < 0)
            throw new IllegalArgumentException(id + ": bad choices");
        return new SettingDeclaration(id, Kind.CHOICE, store, key, refresh, 0, 0, 0, 0,
            Collections.unmodifiableList(names), Collections.unmodifiableList(stored),
            defaultIndex, ordered, true);
    }

    /** {@code value} held inside this integer's range. */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /** What the store holds for this choice's default. */
    @NonNull
    public String defaultStoredChoice() {
        return storedChoices.get(mDefaultChoice);
    }

    /**
     * {@code stored}, trimmed and in lower case, when it is one this choice holds, else the
     * default's: a choice's clamp. A hand-edited termux.properties may say {@code True}.
     */
    @NonNull
    public String storedChoiceOrDefault(@Nullable String stored) {
        if (stored == null) return defaultStoredChoice();
        String value = stored.trim().toLowerCase(Locale.ROOT);
        return storedChoices.contains(value) ? value : defaultStoredChoice();
    }

    /** The default, as text. */
    @NonNull
    public String defaultValue() {
        return kind == Kind.INTEGER ? String.valueOf(defaultInt) : choices.get(mDefaultChoice);
    }

    /** What a value may be, in a refusal's words: {@code 80–150}, or {@code softer, default, harder}. */
    @NonNull
    public String allowed() {
        return kind == Kind.INTEGER ? min + "–" + max : String.join(", ", choices);
    }

    /** The name of the choice the store holds as {@code stored}, clamped to the default. */
    @NonNull
    String choiceForStored(@Nullable String stored) {
        return choices.get(storedChoices.indexOf(storedChoiceOrDefault(stored)));
    }

    /** {@code text} as one of this choice's names (any case, trimmed), or null for none. */
    @Nullable
    String parseChoice(@NonNull String text) {
        String name = text.trim().toLowerCase(Locale.ROOT);
        return choices.contains(name) ? name : null;
    }
}
