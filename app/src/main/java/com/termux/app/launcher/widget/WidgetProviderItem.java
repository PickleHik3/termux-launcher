package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;

import androidx.annotation.NonNull;

/**
 * Sheet-scoped provider row. Carries the cheap metadata only: the card's artwork — the preview, or
 * the provider icon when there is no preview — is resolved on bind and held by
 * {@link WidgetProviderCatalogLoader} under a budget, never by the row.
 */
public final class WidgetProviderItem {
    public final long profileSerial;
    @NonNull public final AppWidgetProviderInfo info;
    @NonNull public final String label;
    public final int columnSpan;
    public final int rowSpan;
    public final int minimumColumnSpan;
    public final int minimumRowSpan;
    public final boolean fits;

    public WidgetProviderItem(long profileSerial, @NonNull AppWidgetProviderInfo info,
                              @NonNull String label, int columnSpan, int rowSpan,
                              int minimumColumnSpan, int minimumRowSpan, boolean fits) {
        this.profileSerial = profileSerial;
        this.info = info;
        this.label = label;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        this.minimumColumnSpan = minimumColumnSpan;
        this.minimumRowSpan = minimumRowSpan;
        this.fits = fits;
    }

    /** The key artwork is held under: one provider in one profile. */
    @NonNull String previewKey() { return profileSerial + " " + info.provider.flattenToString(); }
}
