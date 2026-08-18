package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Sheet-scoped provider row; immutable except the lazily resolved preview. */
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

    // Main-thread only. Previews are deferred out of the catalog build and resolved by the
    // loader the first time a row binds; null stays meaningful after resolution (no preview).
    @Nullable private Drawable preview;
    private boolean previewResolved;

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

    @Nullable public Drawable preview() { return preview; }
    public boolean previewResolved() { return previewResolved; }
    void resolvePreview(@Nullable Drawable value) { preview = value; previewResolved = true; }
}
