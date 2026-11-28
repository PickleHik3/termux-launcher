package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Collections;
import java.util.List;

/**
 * Live blur as a setting (ADR 0041: the settings store is the truth). Its default is not one value:
 * under Terl it is on with more than 6 GiB of RAM, off below, and off under v1
 * ({@link com.termux.app.chrome.LiveBlurPolicy#switchOn}). So it answers through an adapter that
 * resolves the stored choice, else that default, and every door (Terl's Settings row, v1's,
 * terl.conf, the assistant, launcherctl) reads the one value the glass runs on. A set stores the
 * user's own choice, which then wins; {@code default} forgets it.
 */
public final class LiveBlurSettingAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "glass.live_blur";

    /** The switch as the adapter drives it; the device's is over the app's preferences. */
    public interface Switch {
        /** The value in force: the stored choice, else the default for this phone and desktop. */
        boolean isOn();

        /** Whether the user has made a choice of their own. */
        boolean isStored();

        /** Stores the user's own choice. */
        void store(boolean on);

        /** Forgets the user's choice: the default stands again. */
        void forget();
    }

    public static final SettingDeclaration DECLARATION = SettingDeclaration.bool(ID,
        SettingDeclaration.Store.APP_PREFERENCES, "live_blur", false, SettingRefresh.RESTYLE)
        .described("Live blur", "Blurs whatever moves behind glass surfaces, not just the wallpaper.")
        .shownAs("true or false", "true with Terl on a phone with more than 6 GB of RAM, else false")
        .saidAs("my battery drains too fast", "swiping between pages is laggy", "the blur looks stale")
        .notOffered();

    @NonNull private final Switch mSwitch;

    public LiveBlurSettingAdapter(@NonNull Switch liveBlur) {
        mSwitch = liveBlur;
    }

    /** The switch over the app's preferences, resolved on this phone. */
    @NonNull
    public static Switch over(@NonNull android.content.Context context,
                              @NonNull TermuxAppSharedPreferences preferences) {
        android.content.Context app = context.getApplicationContext() != null
            ? context.getApplicationContext() : context;
        return over(preferences, com.termux.app.chrome.LiveBlurSwitch.totalMem(app),
            com.termux.app.chrome.LiveBlurSwitch.lowRam(app));
    }

    /** The switch over the app's preferences on a phone of {@code totalMemBytes} ({@code lowRamDevice}). */
    @NonNull
    public static Switch over(@NonNull TermuxAppSharedPreferences preferences, long totalMemBytes,
                              boolean lowRamDevice) {
        return new Switch() {
            @Override public boolean isOn() {
                return com.termux.app.chrome.LiveBlurSwitch.resolve(preferences, totalMemBytes, lowRamDevice);
            }
            @Override public boolean isStored() { return preferences.getLiveBlurStored() != null; }
            @Override public void store(boolean on) { preferences.setLiveBlurEnabled(on); }
            @Override public void forget() { preferences.forgetLiveBlur(); }
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
        return String.valueOf(mSwitch.isOn());
    }

    /** A settings file carries it only when the user chose it; the default follows the phone. */
    @Override
    public boolean overrides(@NonNull SettingDeclaration declaration) {
        return mSwitch.isStored();
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration, @NonNull String value) {
        return parse(value) == null ? new SettingsRegistry.Refusal(
            ID + " is one of " + DECLARATION.allowed(), DECLARATION.allowed()) : null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration, @NonNull String value) {
        SettingsRegistry.Refusal refusal = check(declaration, value);
        if (refusal != null) return refusal;
        String old = String.valueOf(mSwitch.isOn());
        mSwitch.store(Boolean.TRUE.equals(parse(value)));
        return new SettingsRegistry.Change(DECLARATION, old, String.valueOf(mSwitch.isOn()));
    }

    /** {@code default} forgets the user's choice, so the phone's default stands again. */
    @NonNull
    @Override
    public SettingsRegistry.Outcome reset(@NonNull SettingDeclaration declaration) {
        String old = String.valueOf(mSwitch.isOn());
        mSwitch.forget();
        return new SettingsRegistry.Change(DECLARATION, old, String.valueOf(mSwitch.isOn()));
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal checkReset(@NonNull SettingDeclaration declaration) {
        return null;
    }

    @Nullable
    private static Boolean parse(@NonNull String value) {
        String text = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.equals("true")) return Boolean.TRUE;
        return text.equals("false") ? Boolean.FALSE : null;
    }

    /** A switch held in memory over a fixed default: for tests and the reference page. */
    public static final class Memory implements Switch {
        private final boolean mDefaultOn;
        @Nullable private Boolean mStored;

        public Memory(boolean defaultOn) {
            mDefaultOn = defaultOn;
        }

        @Override public boolean isOn() { return mStored != null ? mStored : mDefaultOn; }
        @Override public boolean isStored() { return mStored != null; }
        @Override public void store(boolean on) { mStored = on; }
        @Override public void forget() { mStored = null; }
    }
}
