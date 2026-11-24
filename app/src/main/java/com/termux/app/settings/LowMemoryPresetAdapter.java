package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Low memory preset as a setting. Switching it is more than storing a flag: turning it on
 * rewrites the look and remembers what it replaced, turning it off puts that back
 * ({@link LowMemoryPreset}). So it answers through an adapter that runs the preset, and every door
 * (the Battery &amp; performance row, launcherctl, restore) gets the same effect. It is kept out of
 * a settings file: its flag without its snapshot would undo nothing.
 */
public final class LowMemoryPresetAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "battery.low_memory_preset";

    /** The preset as the adapter drives it; the device's is over the app's preferences. */
    public interface Preset {
        boolean isOn();

        void turnOn();

        void turnOff();
    }

    private static final SettingDeclaration DECLARATION = SettingDeclaration.bool(ID,
        SettingDeclaration.Store.APP_PREFERENCES, "low_memory_preset", false,
        SettingRefresh.RESTYLE).described("Low memory preset",
        "Switches off the costliest effects and lightens the look so the launcher runs on less.")
        .saidAs("my phone feels slow", "my battery drains too fast", "the launcher uses too much memory",
            "swiping between pages is laggy", "make it run on my old phone")
        .notExported().notOffered();

    @NonNull private final Preset mPreset;

    public LowMemoryPresetAdapter(@NonNull Preset preset) {
        mPreset = preset;
    }

    /** The preset over the app's preferences. */
    @NonNull
    public static Preset over(@NonNull TermuxAppSharedPreferences preferences) {
        return new Preset() {
            @Override public boolean isOn() { return preferences.isLowMemoryPresetEnabled(); }
            @Override public void turnOn() { LowMemoryPreset.turnOn(preferences); }
            @Override public void turnOff() { LowMemoryPreset.turnOff(preferences); }
        };
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
        return String.valueOf(mPreset.isOn());
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        return parse(value) == null ? new SettingsRegistry.Refusal(
            ID + " is one of " + DECLARATION.allowed(), DECLARATION.allowed()) : null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        SettingsRegistry.Refusal refusal = check(declaration, value);
        if (refusal != null) return refusal;
        boolean on = Boolean.TRUE.equals(parse(value));
        String old = String.valueOf(mPreset.isOn());
        if (on != mPreset.isOn()) {
            if (on) mPreset.turnOn();
            else mPreset.turnOff();
        }
        return new SettingsRegistry.Change(DECLARATION, old, String.valueOf(on));
    }

    @Nullable
    private static Boolean parse(@NonNull String value) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.equals("true")) return Boolean.TRUE;
        return text.equals("false") ? Boolean.FALSE : null;
    }
}
