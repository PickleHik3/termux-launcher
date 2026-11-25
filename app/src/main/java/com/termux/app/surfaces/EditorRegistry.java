package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.settings.LauncherSettings;
import com.termux.shared.settings.preferences.PreferenceDraft;
import com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsApplier;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The Appearance editor's door to the Settings registry. The registry the editor saves through
 * sits over the session's draft, so a value the editor sets is refused or clamped by its
 * declaration and lands in the draft, unstored. Done writes the draft into the store and then
 * hands each setting it changed to the Settings applier; Discard writes nothing.
 *
 * <p>Only settings the registry declares go through it; every other key the editor writes keeps
 * its own setter and reaches the store with the draft as before.</p>
 */
final class EditorRegistry {

    /** A setting the draft changed, with its value in the store before Done. */
    private static final class Pending {
        @NonNull final SettingDeclaration declaration;
        @NonNull final String before;

        Pending(@NonNull SettingDeclaration declaration, @NonNull String before) {
            this.declaration = declaration;
            this.before = before;
        }
    }

    /** The declared settings, ids to keys: the registry's own and the font sizes' on this display. */
    @NonNull private final List<SettingDeclaration> mDeclarations;
    /** The registry the editor writes: over the draft. */
    @NonNull private final SettingsRegistry mEditing;
    /** The registry over the store itself, read before and after Done. */
    @NonNull private final SettingsRegistry mStored;

    /**
     * @param editing the store the editor reads and writes: the session's draft, or the store
     *                itself when there is no draft
     * @param store the store the draft sits over
     * @param density px per dp of the screen, which the font size range comes from
     * @param display the display's key suffix: "" for the default display
     */
    EditorRegistry(@Nullable SharedPreferences editing, @Nullable SharedPreferences store,
                   float density, @NonNull String display) {
        FontSizeSettingsAdapter editingFonts = new FontSizeSettingsAdapter(
            LauncherSettings.preferencesStore(editing), density, display);
        FontSizeSettingsAdapter storedFonts = new FontSizeSettingsAdapter(
            LauncherSettings.preferencesStore(store), density, display);
        mEditing = over(editing, editingFonts);
        mStored = over(store, storedFonts);
        List<SettingDeclaration> all = new ArrayList<>(SettingDeclarations.ALL);
        all.addAll(storedFonts.declarations());
        mDeclarations = Collections.unmodifiableList(all);
    }

    @NonNull
    private static SettingsRegistry over(@Nullable SharedPreferences prefs,
                                         @NonNull SettingAdapter fonts) {
        SettingStore preferences = LauncherSettings.preferencesStore(prefs);
        // The editor writes no termux.properties key: that store drops writes and reads defaults.
        SettingStore properties = LauncherSettings.preferencesStore(null);
        SettingStore.Stores stores = which ->
            which == SettingDeclaration.Store.PROPERTIES ? properties : preferences;
        return new SettingsRegistry(stores, SettingDeclarations.ALL,
            Collections.singletonList(fonts));
    }

    /** The declaration {@code id} names, for its range; null for an unknown id. */
    @Nullable
    SettingDeclaration declaration(@NonNull String id) {
        return mEditing.declaration(id);
    }

    /** The integer {@code id} holds now, in the draft; {@code fallback} for a value that is not one. */
    int getInt(@NonNull String id, int fallback) {
        String value = mEditing.get(id);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Sets the integer {@code id} to {@code value} held inside the declaration's range, and says
     * whether the draft's value moved. The clamp is the declaration's, so a slider needs no range
     * of its own.
     */
    boolean setClamped(@NonNull String id, int value) {
        SettingDeclaration declaration = mEditing.declaration(id);
        if (declaration == null) return false;
        SettingsRegistry.Change change = mEditing.set(id,
            String.valueOf(declaration.clamp(value))).change();
        return change != null && change.changed();
    }

    /**
     * Done: writes {@code draft} into its store and ends it, then hands the applier each declared
     * setting whose value the draft moved, once each. A setting whose preference listener already
     * refreshes it (and one whose value came out as it went in) adds nothing.
     */
    void commit(@NonNull PreferenceDraft draft, @NonNull SettingsApplier applier) {
        Set<String> changed = draft.changedKeys();
        List<Pending> pending = new ArrayList<>();
        for (SettingDeclaration each : mDeclarations) {
            if (changed.contains(each.key))
                pending.add(new Pending(each, valueOf(each)));
        }
        draft.commit();
        for (Pending each : pending) {
            applier.apply(new SettingsRegistry.Change(each.declaration, each.before,
                valueOf(each.declaration)));
        }
    }

    @NonNull
    private String valueOf(@NonNull SettingDeclaration declaration) {
        String value = mStored.get(declaration.id);
        return value == null ? "" : value;
    }
}
