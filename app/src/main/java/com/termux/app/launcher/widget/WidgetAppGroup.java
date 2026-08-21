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

    public WidgetAppGroup(long profileSerial, @NonNull String packageName,
                          @NonNull String label, @Nullable Drawable badgedIcon,
                          @NonNull List<WidgetProviderItem> providers) {
        this.profileSerial = profileSerial;
        this.packageName = packageName;
        this.label = label;
        this.badgedIcon = badgedIcon;
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
    }
}
