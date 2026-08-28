package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Sheet-scoped provider row. Carries the cheap metadata only: the preview is resolved on bind and
 * held by {@link WidgetProviderCatalogLoader} under a budget, never by the row.
 */
public final class WidgetProviderItem {
    public final long profileSerial;
    @NonNull public final AppWidgetProviderInfo info;
    @NonNull public final String label;
    @Nullable public final Drawable icon;
    public final int columnSpan;
    public final int rowSpan;
    public final int minimumColumnSpan;
    public final int minimumRowSpan;
    public final boolean fits;

    public WidgetProviderItem(long profileSerial, @NonNull AppWidgetProviderInfo info,
                              @NonNull String label, @Nullable Drawable icon,
                              int columnSpan, int rowSpan, int minimumColumnSpan,
                              int minimumRowSpan, boolean fits) {
        this.profileSerial = profileSerial;
        this.info = info;
        this.label = label;
        this.icon = icon;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        this.minimumColumnSpan = minimumColumnSpan;
        this.minimumRowSpan = minimumRowSpan;
        this.fits = fits;
    }

    /** The key a preview is held under: one provider in one profile. */
    @NonNull String previewKey() { return profileSerial + " " + info.provider.flattenToString(); }
}
