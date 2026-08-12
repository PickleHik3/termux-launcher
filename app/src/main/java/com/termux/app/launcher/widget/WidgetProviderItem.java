package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable sheet-scoped provider row. */
public final class WidgetProviderItem {
    @NonNull public final AppWidgetProviderInfo info;
    @NonNull public final String label;
    @Nullable public final Drawable icon;
    @Nullable public final Drawable preview;
    public final int columnSpan;
    public final int rowSpan;
    public final int minimumColumnSpan;
    public final int minimumRowSpan;
    public final boolean fits;

    public WidgetProviderItem(@NonNull AppWidgetProviderInfo info, @NonNull String label,
                              @Nullable Drawable icon, @Nullable Drawable preview,
                              int columnSpan, int rowSpan, int minimumColumnSpan,
                              int minimumRowSpan, boolean fits) {
        this.info = info;
        this.label = label;
        this.icon = icon;
        this.preview = preview;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        this.minimumColumnSpan = minimumColumnSpan;
        this.minimumRowSpan = minimumRowSpan;
        this.fits = fits;
    }
}
