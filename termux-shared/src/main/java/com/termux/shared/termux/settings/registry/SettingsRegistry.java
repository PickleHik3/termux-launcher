package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one list of the launcher's settings. Every door (launcherctl, and later Settings rows, the
 * Appearance editor, restore and the Assistant) reads and saves a setting through it. Storage does
 * not move: it reads and writes the stores and keys the settings already use, through
 * {@link SettingStore}. Constructing one costs nothing; its index builds on the first lookup.
 */
public final class SettingsRegistry {

    /** A settings file's word for "put this surface's own value back on Global". */
    public static final String FOLLOW = "follow";
    /** A settings file's word for "reset this setting"; never a value of its own. */
    public static final String DEFAULT = "default";

    @NonNull private final SettingStore.Stores mStores;
    @NonNull private final List<SettingDeclaration> mDeclarations;
    @NonNull private final List<SettingAdapter> mAdapters;
    @Nullable private final Veto mVeto;
    @Nullable private Map<String, SettingDeclaration> mIndex;

    /**
     * Refuses a value the declaration allows but this build or phone cannot run (Wayland without
     * the Wayland host), so every door refuses it as it refuses a value outside the range.
     */
    public interface Veto {
        /** The refusal for {@code value}, a name or digits the declaration allows; null to allow it. */
        @Nullable
        Refusal refuse(@NonNull SettingDeclaration declaration, @NonNull String value);
    }

    /** The launcher's declarations ({@link SettingDeclarations#ALL}) over {@code stores}. */
    public SettingsRegistry(@NonNull SettingStore.Stores stores) {
        this(stores, SettingDeclarations.ALL);
    }

    public SettingsRegistry(@NonNull SettingStore.Stores stores,
                            @NonNull List<SettingDeclaration> declarations) {
        this(stores, declarations, Collections.emptyList());
    }

    /** As above, and {@code adapters} answer for the ids the declarations do not name. */
    public SettingsRegistry(@NonNull SettingStore.Stores stores,
                            @NonNull List<SettingDeclaration> declarations,
                            @NonNull List<SettingAdapter> adapters) {
        this(stores, declarations, adapters, null);
    }

    /** As above, and {@code veto} refuses the declared values this build or phone cannot run. */
    public SettingsRegistry(@NonNull SettingStore.Stores stores,
                            @NonNull List<SettingDeclaration> declarations,
                            @NonNull List<SettingAdapter> adapters, @Nullable Veto veto) {
        mStores = stores;
        mDeclarations = declarations;
        mAdapters = adapters;
        mVeto = veto;
    }

    /** The declaration {@code id} names, or null for an unknown id. */
    @Nullable
    public synchronized SettingDeclaration declaration(@Nullable String id) {
        if (mIndex == null) {
            Map<String, SettingDeclaration> index = new HashMap<>();
            for (SettingDeclaration each : mDeclarations) index.put(each.id, each);
            mIndex = index;
        }
        if (id == null) return null;
        SettingDeclaration own = mIndex.get(id);
        if (own != null) return own;
        for (SettingAdapter adapter : mAdapters) {
            SettingDeclaration adapted = adapter.declaration(id);
            if (adapted != null) return adapted;
        }
        return null;
    }

    /** The adapter answering for {@code declaration}, or null when the registry's own does. */
    @Nullable
    private SettingAdapter adapterOf(@NonNull SettingDeclaration declaration) {
        if (mIndex != null && mIndex.get(declaration.id) == declaration) return null;
        for (SettingAdapter adapter : mAdapters) {
            if (adapter.declaration(declaration.id) != null) return adapter;
        }
        return null;
    }

    /** The current value of {@code id} as text, or null for an unknown id. */
    @Nullable
    public String get(@Nullable String id) {
        SettingDeclaration declaration = declaration(id);
        if (declaration == null) return null;
        SettingAdapter adapter = adapterOf(declaration);
        return adapter == null ? read(declaration) : adapter.get(declaration);
    }

    /**
     * Saves {@code value} (an integer's digits, a choice's name in any case) to {@code id}'s store:
     * a {@link Change} with the old and new value, or a {@link Refusal} that writes nothing. Undo
     * is {@code set(change.id, change.oldValue)}.
     */
    @NonNull
    public Outcome set(@Nullable String id, @Nullable String value) {
        SettingDeclaration declaration = declaration(id);
        if (declaration == null) return unknown(id);
        String word = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (word.equals(FOLLOW)) return follow(declaration);
        if (word.equals(DEFAULT)) {
            SettingAdapter owner = adapterOf(declaration);
            return owner != null ? owner.reset(declaration)
                : setValue(declaration, declaration.defaultValue());
        }
        return setValue(declaration, value);
    }

    @NonNull
    private Outcome setValue(@NonNull SettingDeclaration declaration, @Nullable String value) {
        SettingAdapter adapter = adapterOf(declaration);
        if (adapter != null) return adapter.set(declaration, value == null ? "" : value);
        String normal = normalized(declaration, value);
        if (normal == null) return outside(declaration);
        Refusal vetoed = vetoed(declaration, normal);
        return vetoed != null ? vetoed : write(declaration, normal);
    }

    @Nullable
    private Refusal vetoed(@NonNull SettingDeclaration declaration, @NonNull String normal) {
        return mVeto == null ? null : mVeto.refuse(declaration, normal);
    }

    /**
     * What {@link #set} would refuse, without saving: the Refusal for an unknown id or a value
     * outside the range or choices, else null.
     */
    @Nullable
    public Refusal check(@Nullable String id, @Nullable String value) {
        SettingDeclaration declaration = declaration(id);
        if (declaration == null) return unknown(id);
        String word = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (word.equals(FOLLOW)) return canFollow(declaration) ? null : cannotFollow(declaration);
        SettingAdapter adapter = adapterOf(declaration);
        if (word.equals(DEFAULT) && adapter != null) return adapter.checkReset(declaration);
        String plain = word.equals(DEFAULT) ? declaration.defaultValue() : value;
        if (adapter != null) return adapter.check(declaration, plain == null ? "" : plain);
        String normal = normalized(declaration, plain);
        return normal == null ? outside(declaration) : vetoed(declaration, normal);
    }

    /** Whether {@code id}'s value is its own to put back on what it follows. */
    public boolean canFollow(@Nullable String id) {
        SettingDeclaration declaration = declaration(id);
        return declaration != null && canFollow(declaration);
    }

    private boolean canFollow(@NonNull SettingDeclaration declaration) {
        SettingAdapter adapter = adapterOf(declaration);
        return adapter != null && adapter.canFollow(declaration);
    }

    /** Whether {@code id} can follow and does now, so its value is not its own. */
    public boolean follows(@Nullable String id) {
        SettingDeclaration declaration = declaration(id);
        if (declaration == null || !canFollow(declaration)) return false;
        SettingAdapter adapter = adapterOf(declaration);
        return adapter != null && !adapter.overrides(declaration);
    }

    @NonNull
    private Outcome follow(@NonNull SettingDeclaration declaration) {
        if (!canFollow(declaration)) return cannotFollow(declaration);
        SettingAdapter adapter = adapterOf(declaration);
        return adapter.follow(declaration);
    }

    @NonNull
    private static Refusal cannotFollow(@NonNull SettingDeclaration declaration) {
        return new Refusal(declaration.id + " has no Global to follow", null);
    }

    /** {@code value} as the declaration holds it (digits, or a choice's name), or null if outside. */
    @Nullable
    private static String normalized(@NonNull SettingDeclaration declaration, @Nullable String value) {
        String text = value == null ? "" : value.trim();
        if (declaration.kind == SettingDeclaration.Kind.INTEGER) {
            Integer number = parseInteger(text);
            if (number == null || number < declaration.min || number > declaration.max) return null;
            return String.valueOf(number);
        }
        return declaration.parseChoice(text);
    }

    /** Which way {@link #step} moves. */
    public enum Direction { UP, DOWN }

    /**
     * Moves an ordered setting one step from its current value: an integer by its step, an ordered
     * choice to the next name. Refused at either end, for a choice with no order and for an
     * unknown id.
     */
    @NonNull
    public synchronized Outcome step(@Nullable String id, @NonNull Direction direction) {
        Outcome next = previewStep(id, direction);
        Change change = next.change();
        return change == null ? next : write(change.declaration, change.newValue);
    }

    /**
     * What {@link #step} would do, without writing it: the Change it would make, or the Refusal it
     * would give. The Assistant shows a step on a card before anything is saved.
     */
    @NonNull
    public synchronized Outcome previewStep(@Nullable String id, @NonNull Direction direction) {
        SettingDeclaration declaration = declaration(id);
        if (declaration == null) return unknown(id);
        if (adapterOf(declaration) != null)
            return new Refusal(declaration.id + " has no step; set it to a value",
                declaration.allowed());
        if ((declaration.kind == SettingDeclaration.Kind.CHOICE
            || declaration.kind == SettingDeclaration.Kind.BOOLEAN) && !declaration.ordered)
            return new Refusal(declaration.id + " has no order; it is one of "
                + declaration.allowed(), declaration.allowed());
        String current = read(declaration);
        boolean up = direction == Direction.UP;
        String next;
        if (declaration.kind == SettingDeclaration.Kind.INTEGER) {
            int value = Integer.parseInt(current);
            next = String.valueOf(declaration.clamp(up ? value + declaration.step
                : value - declaration.step));
        } else {
            int index = declaration.choices.indexOf(current) + (up ? 1 : -1);
            next = index < 0 || index >= declaration.choices.size() ? current
                : declaration.choices.get(index);
        }
        if (next.equals(current)) {
            return new Refusal(declaration.id + " is already at its " + (up ? "highest" : "lowest")
                + ", " + current, declaration.allowed());
        }
        return new Change(declaration, current, next);
    }

    /** One declaration and its current value, as {@link #list} answers it. */
    public static final class Entry {
        @NonNull public final SettingDeclaration declaration;
        @NonNull public final String value;

        Entry(@NonNull SettingDeclaration declaration, @NonNull String value) {
            this.declaration = declaration;
            this.value = value;
        }
    }

    /** Every declaration with its current value, in declaration order. */
    @NonNull
    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>(mDeclarations.size());
        for (SettingDeclaration each : mDeclarations) entries.add(new Entry(each, read(each)));
        for (SettingAdapter adapter : mAdapters) {
            for (SettingDeclaration each : adapter.declarations())
                entries.add(new Entry(each, adapter.get(each)));
        }
        return entries;
    }

    /**
     * What a settings file carries: the declared settings that are exported, and the adapters' ids
     * that are exported and hold a value of their own ({@link SettingAdapter#overrides}), so a
     * value that follows another stays following after an apply.
     */
    @NonNull
    public List<Entry> exported() {
        List<Entry> entries = new ArrayList<>();
        for (SettingDeclaration each : mDeclarations) {
            if (each.exported) entries.add(new Entry(each, read(each)));
        }
        for (SettingAdapter adapter : mAdapters) {
            for (SettingDeclaration each : adapter.declarations()) {
                if (each.exported && adapter.overrides(each))
                    entries.add(new Entry(each, adapter.get(each)));
            }
        }
        return entries;
    }

    /**
     * What a settings file is built from: {@link #exported} with {@code follow} in place of the
     * value for each surface setting that follows Global, so an apply leaves it following.
     */
    @NonNull
    public List<Entry> exportedForFile() {
        List<Entry> entries = new ArrayList<>();
        for (SettingDeclaration each : mDeclarations) {
            if (each.exported) entries.add(new Entry(each, read(each)));
        }
        for (SettingAdapter adapter : mAdapters) {
            for (SettingDeclaration each : adapter.declarations()) {
                if (!each.exported) continue;
                if (adapter.overrides(each)) entries.add(new Entry(each, adapter.get(each)));
                else if (adapter.canFollow(each)) entries.add(new Entry(each, FOLLOW));
            }
        }
        return entries;
    }

    /** Writes {@code value}, already checked against the declaration, and answers the Change. */
    @NonNull
    private synchronized Outcome write(@NonNull SettingDeclaration declaration, @NonNull String value) {
        String old = read(declaration);
        if (!old.equals(value)) {
            SettingStore store = mStores.store(declaration.store);
            if (declaration.kind == SettingDeclaration.Kind.INTEGER) {
                store.putInt(declaration.key, Integer.parseInt(value));
            } else if (declaration.kind == SettingDeclaration.Kind.BOOLEAN) {
                store.putBoolean(declaration.key, Boolean.parseBoolean(value));
            } else {
                store.putString(declaration.key,
                    declaration.storedChoices.get(declaration.choices.indexOf(value)));
            }
            // A store that could not keep the value (a file that would not write) is a refusal,
            // so a settings file never counts a line as saved that is not.
            if (!read(declaration).equals(value)) {
                return new Refusal(declaration.id + " could not be saved", null);
            }
        }
        return new Change(declaration, old, value);
    }

    /** The refusal for an id no declaration or adapter names; every door words it the same. */
    @NonNull
    public static Refusal unknown(@Nullable String id) {
        return new Refusal("no setting is called \"" + id + "\"", null);
    }

    @NonNull
    private static Refusal outside(@NonNull SettingDeclaration declaration) {
        String allowed = declaration.allowed();
        return new Refusal(declaration.id
            + (declaration.kind == SettingDeclaration.Kind.INTEGER ? " is " : " is one of ")
            + allowed, allowed);
    }

    @Nullable
    private static Integer parseInteger(@NonNull String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private String read(@NonNull SettingDeclaration declaration) {
        SettingStore store = mStores.store(declaration.store);
        if (declaration.kind == SettingDeclaration.Kind.INTEGER)
            return String.valueOf(declaration.clamp(
                store.getInt(declaration.key, declaration.defaultInt)));
        if (declaration.kind == SettingDeclaration.Kind.BOOLEAN)
            return String.valueOf(store.getBoolean(declaration.key,
                Boolean.parseBoolean(declaration.defaultValue())));
        return declaration.choiceForStored(store.getString(declaration.key));
    }

    /** What a save answers: a {@link Change} or a {@link Refusal}. */
    public abstract static class Outcome {
        Outcome() {}

        /** This outcome as a Change, or null when it was refused. */
        @Nullable
        public Change change() {
            return this instanceof Change ? (Change) this : null;
        }

        /** This outcome as a Refusal, or null when it was saved. */
        @Nullable
        public Refusal refusal() {
            return this instanceof Refusal ? (Refusal) this : null;
        }
    }

    /** A save that went through: the setting, its value before and its value now, as text. */
    public static final class Change extends Outcome {
        @NonNull public final SettingDeclaration declaration;
        @NonNull public final String id;
        @NonNull public final String oldValue;
        @NonNull public final String newValue;

        public Change(@NonNull SettingDeclaration declaration, @NonNull String oldValue,
               @NonNull String newValue) {
            this.declaration = declaration;
            this.id = declaration.id;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        /** Whether the value moved; a save of the value already held writes and refreshes nothing. */
        public boolean changed() {
            return !oldValue.equals(newValue);
        }
    }

    /** A save that was refused and wrote nothing: why, and what the value may be. */
    public static final class Refusal extends Outcome {
        @NonNull public final String reason;
        /** The allowed range or choices ({@code 80–150}); null when the id itself is unknown. */
        @Nullable public final String allowed;

        public Refusal(@NonNull String reason, @Nullable String allowed) {
            this.reason = reason;
            this.allowed = allowed;
        }
    }
}
