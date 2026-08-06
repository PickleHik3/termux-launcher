package com.termux.app.fonts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Persistence for the terminal font picker: which family is active and which toggles were
 * applied to it.
 *
 * <p>Only one family is active at a time, because the managed config only ever names one, so a
 * single set of options is all that needs storing. Switching family re-seeds the options from
 * that family's catalog defaults rather than carrying the previous family's tuning over —
 * {@code +zero} means nothing to Hack, and a {@code wght} the new axis does not cover means
 * nothing at all.
 */
public final class FontSettings {

    public static final String PREFS_NAME = "termux_fonts";

    static final String KEY_ACTIVE_FAMILY = "active_family_id";
    static final String KEY_NERD_ICONS = "nerd_icons";
    static final String KEY_LIGATURES = "ligatures";
    static final String KEY_RECOMMENDED_FEATURES = "recommended_features";
    static final String KEY_WEIGHT = "weight";
    static final String KEY_METERED_ACK = "metered_warning_acknowledged";

    @NonNull private final SharedPreferences preferences;

    public FontSettings(@NonNull Context context) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Catalog id of the family the managed config currently names, or {@code ""}. */
    @NonNull
    public String getActiveFamilyId() {
        String value = preferences.getString(KEY_ACTIVE_FAMILY, "");
        return value == null ? "" : value;
    }

    /** The toggles last applied, seeded from {@code family}'s defaults on a family change. */
    @NonNull
    public FontInstaller.Options getOptions(@NonNull FontCatalog.Family family) {
        if (!family.id.equals(getActiveFamilyId())) {
            return FontInstaller.Options.recommendedFor(family);
        }
        return new FontInstaller.Options(
            preferences.getBoolean(KEY_NERD_ICONS, true),
            preferences.getString(KEY_LIGATURES, family.defaultLigatures),
            preferences.getBoolean(KEY_RECOMMENDED_FEATURES, true),
            preferences.getInt(KEY_WEIGHT, 0));
    }

    /** Records a successful install. */
    public void setActive(@NonNull FontCatalog.Family family, @NonNull FontInstaller.Options options) {
        preferences.edit()
            .putString(KEY_ACTIVE_FAMILY, family.id)
            .putBoolean(KEY_NERD_ICONS, options.nerdIcons)
            .putString(KEY_LIGATURES, options.ligatures)
            .putBoolean(KEY_RECOMMENDED_FEATURES, options.recommendedFeatures)
            .putInt(KEY_WEIGHT, options.weight)
            .apply();
    }

    /** Records that the managed config was removed; installed faces are untouched on disk. */
    public void clearActive() {
        preferences.edit().remove(KEY_ACTIVE_FAMILY).apply();
    }

    /** Whether the user already accepted downloading over a metered connection. */
    public boolean isMeteredWarningSuppressed() {
        return preferences.getBoolean(KEY_METERED_ACK, false);
    }

    public void setMeteredWarningSuppressed(boolean suppressed) {
        preferences.edit().putBoolean(KEY_METERED_ACK, suppressed).apply();
    }
}
