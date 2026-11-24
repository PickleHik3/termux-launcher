package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

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
    public enum Kind {
        INTEGER, CHOICE,
        /** A value only its {@link SettingAdapter} can judge (a layout field); no range here. */
        TEXT,
        /** On or off, as {@code true} or {@code false}; a store keeps it as a boolean. */
        BOOLEAN
    }

    /** The store a setting already lives in. */
    public enum Store {
        /** The app's shared preferences ({@code TermuxAppSharedPreferences}). */
        APP_PREFERENCES,
        /** {@code termux.properties}. */
        PROPERTIES
    }

    /**
     * Which reset removes a setting. A reset reads its keys from the declarations in its group
     * ({@link SettingDeclarations#resetKeys}), so its list and the declaration cannot drift.
     */
    public enum ResetGroup {
        /** No reset removes it. */
        NONE,
        /** The appearance reset on upgrade ({@code AppearanceReset}). */
        APPEARANCE
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
    @NonNull public final ResetGroup resetGroup;

    /**
     * What the settings reference page says: a short name and one plain sentence for the user.
     * Empty until {@link #described}.
     */
    @NonNull public final String title;
    @NonNull public final String summary;
    /**
     * The range and default as the reference page words them, for a setting whose range is
     * not a number pair here (a value only its adapter judges, or one that depends on the
     * display). Empty to say what {@link #allowed} and {@link #defaultValue} say.
     */
    @NonNull public final String rangeNote;
    @NonNull public final String defaultNote;
    /** How people say they want it changed; empty until {@link #saidAs}. */
    @NonNull public final List<String> phrases;
    /**
     * How the launcher reads a stored choice before matching it, for a store that still holds old
     * spellings; null reads the stored text as it is.
     */
    @Nullable private final UnaryOperator<String> storedReader;
    /** Whether the Assistant may offer to change it; false after {@link #notOffered}. */
    public final boolean mayOffer;

    private SettingDeclaration(@NonNull String id, @NonNull Kind kind, @NonNull Store store,
                               @NonNull String key, @NonNull SettingRefresh refresh, int min,
                               int max, int step, int defaultInt, @NonNull List<String> choices,
                               @NonNull List<String> storedChoices, int defaultChoice,
                               boolean ordered, boolean exported,
                               @NonNull ResetGroup resetGroup) {
        this(id, kind, store, key, refresh, min, max, step, defaultInt, choices, storedChoices,
            defaultChoice, ordered, exported, resetGroup, "", "", "", "",
            Collections.emptyList(), true, null);
    }

    private SettingDeclaration(@NonNull String id, @NonNull Kind kind, @NonNull Store store,
                               @NonNull String key, @NonNull SettingRefresh refresh, int min,
                               int max, int step, int defaultInt, @NonNull List<String> choices,
                               @NonNull List<String> storedChoices, int defaultChoice,
                               boolean ordered, boolean exported,
                               @NonNull ResetGroup resetGroup, @NonNull String title,
                               @NonNull String summary, @NonNull String rangeNote,
                               @NonNull String defaultNote, @NonNull List<String> phrases,
                               boolean mayOffer,
                               @Nullable UnaryOperator<String> storedReader) {
        this.storedReader = storedReader;
        this.title = title;
        this.summary = summary;
        this.rangeNote = rangeNote;
        this.defaultNote = defaultNote;
        this.phrases = phrases;
        this.mayOffer = mayOffer;
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
        this.resetGroup = resetGroup;
    }

    /** This declaration removed by the {@code group} reset. */
    @NonNull
    public SettingDeclaration resetBy(@NonNull ResetGroup group) {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, group, title, summary,
            rangeNote, defaultNote, phrases, mayOffer, storedReader);
    }

    /** This declaration with the name and sentence the settings reference page shows. */
    @NonNull
    public SettingDeclaration described(@NonNull String title, @NonNull String summary) {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, resetGroup, title, summary,
            rangeNote, defaultNote, phrases, mayOffer, storedReader);
    }

    /** This declaration with the range and default the reference page words in place of numbers. */
    @NonNull
    public SettingDeclaration shownAs(@NonNull String range, @NonNull String defaultText) {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, resetGroup, title, summary,
            range, defaultText, phrases, mayOffer, storedReader);
    }

    /** This declaration kept out of a settings file's export and apply. */
    @NonNull
    public SettingDeclaration notExported() {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, false, resetGroup, title, summary,
            rangeNote, defaultNote, phrases, mayOffer, storedReader);
    }

    /** This choice reading what its store holds through {@code reader} (the launcher's own mapping). */
    @NonNull
    public SettingDeclaration readingStoredAs(@NonNull UnaryOperator<String> reader) {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, resetGroup, title, summary,
            rangeNote, defaultNote, phrases, mayOffer, reader);
    }

    /**
     * This declaration with the words people use when they want it changed ("too small", "eyes
     * hurt at night"), for the Assistant to match a complaint to it.
     */
    @NonNull
    public SettingDeclaration saidAs(@NonNull String... complaints) {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, resetGroup, title, summary,
            rangeNote, defaultNote, Collections.unmodifiableList(java.util.Arrays.asList(complaints.clone())), mayOffer,
            storedReader);
    }

    /**
     * This declaration kept from the Assistant's offers: a setting that can hide the launcher, lock
     * the user out, wipe data, change privileged access or cost heavy battery is only changed by
     * the user's own hand.
     */
    @NonNull
    public SettingDeclaration notOffered() {
        return new SettingDeclaration(id, kind, store, key, refresh, min, max, step, defaultInt,
            choices, storedChoices, mDefaultChoice, ordered, exported, resetGroup, title, summary,
            rangeNote, defaultNote, phrases, false, storedReader);
    }

    /** A whole number from {@code min} to {@code max}, stepped by {@code step}. */
    @NonNull
    public static SettingDeclaration integer(@NonNull String id, @NonNull Store store,
                                             @NonNull String key, int min, int max, int step,
                                             int defaultValue, @NonNull SettingRefresh refresh) {
        if (min > max || step <= 0 || defaultValue < min || defaultValue > max)
            throw new IllegalArgumentException(id + ": bad range");
        return new SettingDeclaration(id, Kind.INTEGER, store, key, refresh, min, max, step,
            defaultValue, Collections.emptyList(), Collections.emptyList(), -1, true, true,
            ResetGroup.NONE);
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
            defaultIndex, ordered, true, ResetGroup.NONE);
    }

    /** On or off, kept as a boolean under {@code key}; {@code true} or {@code false} as text. */
    @NonNull
    public static SettingDeclaration bool(@NonNull String id, @NonNull Store store,
                                          @NonNull String key, boolean defaultValue,
                                          @NonNull SettingRefresh refresh) {
        List<String> names = Collections.unmodifiableList(java.util.Arrays.asList("false", "true"));
        return new SettingDeclaration(id, Kind.BOOLEAN, store, key, refresh, 0, 0, 0, 0, names,
            names, defaultValue ? 1 : 0, false, true, ResetGroup.NONE);
    }

    /**
     * A setting an adapter validates itself: it lists as text, and the adapter's own refusal says
     * what it may be. {@code defaultValue} is what {@code config list} shows as its default.
     */
    @NonNull
    public static SettingDeclaration text(@NonNull String id, @NonNull String defaultValue,
                                          @NonNull SettingRefresh refresh) {
        return new SettingDeclaration(id, Kind.TEXT, Store.APP_PREFERENCES, id, refresh, 0, 0, 0,
            0, Collections.singletonList(defaultValue), Collections.singletonList(defaultValue),
            0, false, true, ResetGroup.NONE);
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
        if (storedReader != null) stored = storedReader.apply(stored);
        String value = stored.trim().toLowerCase(Locale.ROOT);
        return storedChoices.contains(value) ? value : defaultStoredChoice();
    }

    /**
     * Whether a store holding {@code stored} (an Integer or a String, as the store keeps it) would
     * read it as a value this declaration allows. A backup restore skips a value that fails it.
     * A text setting's adapter judges its own, so it passes here.
     */
    public boolean acceptsStored(@Nullable Object stored) {
        switch (kind) {
            case INTEGER:
                return stored instanceof Integer && (Integer) stored >= min && (Integer) stored <= max;
            case BOOLEAN:
                return stored instanceof Boolean;
            case CHOICE:
                return stored instanceof String
                    && storedChoices.contains(((String) stored).trim().toLowerCase(Locale.ROOT));
            default:
                return true;
        }
    }

    /** The default, as text. */
    @NonNull
    public String defaultValue() {
        return kind == Kind.INTEGER ? String.valueOf(defaultInt) : choices.get(mDefaultChoice);
    }

    /** What a value may be, in a refusal's words: {@code 80–150}, or {@code softer, default, harder}. */
    @NonNull
    public String allowed() {
        if (kind == Kind.TEXT) return "";
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
