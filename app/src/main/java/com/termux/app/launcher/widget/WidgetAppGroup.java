package com.termux.app.launcher.widget;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A profile-qualified application group in the widget picker. */
public final class WidgetAppGroup {
    public final long profileSerial;
    @NonNull public final String packageName;
    @NonNull public final String label;
    @Nullable public final Drawable badgedIcon;
    @NonNull public final List<WidgetProviderItem> providers;
    /**
     * How many widgets the app offers. Enumeration knows this before any per-provider work has
     * run, so the collapsed app rows can carry a real count while {@link #providers} is still
     * empty.
     */
    public final int providerCount;

    public WidgetAppGroup(long profileSerial, @NonNull String packageName,
                          @NonNull String label, @Nullable Drawable badgedIcon,
                          @NonNull List<WidgetProviderItem> providers) {
        this(profileSerial, packageName, label, badgedIcon, providers, providers.size());
    }

    public WidgetAppGroup(long profileSerial, @NonNull String packageName,
                          @NonNull String label, @Nullable Drawable badgedIcon,
                          @NonNull List<WidgetProviderItem> providers, int providerCount) {
        this.profileSerial = profileSerial;
        this.packageName = packageName;
        this.label = label;
        this.badgedIcon = badgedIcon;
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        this.providerCount = providerCount;
    }

    /** The same app, carrying only the widgets a search kept. */
    @NonNull WidgetAppGroup withProviders(@NonNull List<WidgetProviderItem> matches) {
        return new WidgetAppGroup(profileSerial, packageName, label, badgedIcon, matches);
    }

    /** One app in one profile: what collapse state and row identity are keyed by. */
    @NonNull public String key() { return profileSerial + "/" + packageName; }
}
