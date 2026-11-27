package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.keybind.Leader;
import com.termux.app.terminal.keybind.LeaderChord;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingStore;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.List;

/**
 * The Custom leader's keys ({@code keys.leader_chord}, ADR 0042) as a setting: a chord such as
 * {@code ctrl+shift} or {@code ctrl+b}, judged by {@link LeaderChord}, so every door (the Settings
 * row, terl.conf, launcherctl) refuses Android's hardware chords with the same reason. It applies
 * while {@code keys.leader} is {@code custom}; empty clears it. The launcher's preference
 * listener re-reads both keys ({@link #leaderFrom}).
 */
public final class LeaderChordSettingAdapter implements SettingAdapter {

    public static final String ID = "keys.leader_chord";
    /** The app-preferences key the chord is kept under. */
    public static final String KEY = "keys_leader_chord";

    static final SettingDeclaration DECLARATION = SettingDeclaration.text(ID, "",
        SettingRefresh.LISTENER).described("Custom leader",
        "The keys of a custom leader: ctrl+shift is held with the key, ctrl+b is pressed "
            + "before it.")
        .shownAs("two or more keys with Ctrl or Alt", "none")
        .saidAs("I want my own shortcut key", "use ctrl+b like tmux");

    @NonNull private final SettingStore mStore;

    public LeaderChordSettingAdapter(@NonNull SettingStore store) {
        mStore = store;
    }

    /** The leader the two stored settings name, the Terl key when they name nothing usable. */
    @NonNull
    public static Leader leaderFrom(@NonNull SettingStore store) {
        return Leader.of(store.getString(SettingDeclarations.KEYS_LEADER.key), store.getString(KEY));
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        return ID.equals(id) ? DECLARATION : null;
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        return Collections.singletonList(DECLARATION);
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        String stored = mStore.getString(KEY);
        return stored == null ? "" : stored;
    }

    @Override
    public boolean overrides(@NonNull SettingDeclaration declaration) {
        return !get(declaration).isEmpty();
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        if (value.trim().isEmpty()) return null;
        LeaderChord.Check check = LeaderChord.parse(value);
        return check.ok() ? null : new SettingsRegistry.Refusal(check.refusal,
            "two or more keys with Ctrl or Alt");
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        SettingsRegistry.Refusal refusal = check(declaration, value);
        if (refusal != null) return refusal;
        LeaderChord.Check check = LeaderChord.parse(value);
        String normal = check.chord == null ? "" : check.chord.spec();
        String old = get(declaration);
        if (!normal.equals(old)) mStore.putString(KEY, normal);
        return new SettingsRegistry.Change(declaration, old, normal);
    }
}
