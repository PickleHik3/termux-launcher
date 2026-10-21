package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetProviderInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Sheet-scoped provider row. Carries the cheap metadata only: the card's artwork — the preview, or
 * the provider icon when there is no preview — is resolved on bind and held by
 * {@link WidgetProviderCatalogLoader} under a budget, never by the row.
 *
 * <p>A row is either another app's provider ({@link #info} set) or one of the launcher's own
 * widgets ({@link #builtinKind} set); never both.</p>
 */
public final class WidgetProviderItem {
    public final long profileSerial;
    /** The provider, or null for a built-in widget. */
    @Nullable public final AppWidgetProviderInfo info;
    /** The built-in widget's kind, or null for an app widget. */
    @Nullable public final String builtinKind;
    @NonNull public final String label;
    public final int columnSpan;
    public final int rowSpan;
    public final int minimumColumnSpan;
    public final int minimumRowSpan;
    public final boolean fits;
    /** A built-in's one-line purpose, starting with a verb; empty for an app widget. */
    @NonNull public final String description;
    /** A built-in's sizes grouped on its one card, "2 × 1 · 4 × 1"; empty for an app widget. */
    @NonNull public final String sizes;

    public WidgetProviderItem(long profileSerial, @NonNull AppWidgetProviderInfo info,
                              @NonNull String label, int columnSpan, int rowSpan,
                              int minimumColumnSpan, int minimumRowSpan, boolean fits) {
        this(profileSerial, info, null, label, columnSpan, rowSpan, minimumColumnSpan,
            minimumRowSpan, fits);
    }

    private WidgetProviderItem(long profileSerial, @Nullable AppWidgetProviderInfo info,
                               @Nullable String builtinKind,
                               @NonNull String label, int columnSpan, int rowSpan,
                               int minimumColumnSpan, int minimumRowSpan, boolean fits) {
        this(profileSerial, info, builtinKind, label, columnSpan, rowSpan, minimumColumnSpan,
            minimumRowSpan, fits, "", "");
    }

    private WidgetProviderItem(long profileSerial, @Nullable AppWidgetProviderInfo info,
                               @Nullable String builtinKind,
                               @NonNull String label, int columnSpan, int rowSpan,
                               int minimumColumnSpan, int minimumRowSpan, boolean fits,
                               @NonNull String description, @NonNull String sizes) {
        this.description = description;
        this.sizes = sizes;
        this.profileSerial = profileSerial;
        this.info = info;
        this.builtinKind = builtinKind;
        this.label = label;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        this.minimumColumnSpan = minimumColumnSpan;
        this.minimumRowSpan = minimumRowSpan;
        this.fits = fits;
    }

    /**
     * A row for one of the launcher's own widgets, offered at the span its design takes on this
     * grid, and resizable down to the span its smallest design takes.
     */
    @NonNull
    public static WidgetProviderItem builtin(long profileSerial, @NonNull String kind,
                                             @NonNull String label, int columnSpan, int rowSpan,
                                             int minimumColumnSpan, int minimumRowSpan,
                                             boolean fits) {
        return new WidgetProviderItem(profileSerial, null, kind, label, columnSpan, rowSpan,
            Math.min(minimumColumnSpan, columnSpan), Math.min(minimumRowSpan, rowSpan), fits);
    }

    /** This built-in row with its purpose and its sizes for the card. */
    @NonNull public WidgetProviderItem withCard(@NonNull String description,
                                                @NonNull String sizes) {
        return new WidgetProviderItem(profileSerial, info, builtinKind, label, columnSpan,
            rowSpan, minimumColumnSpan, minimumRowSpan, fits, description, sizes);
    }

    public boolean isBuiltin() { return builtinKind != null; }

    /** What tells this row from every other: the provider, or the built-in kind. */
    @NonNull public String identity() {
        return info != null ? info.provider.flattenToString() : "builtin/" + builtinKind;
    }

    /** The key artwork is held under: one provider in one profile. */
    @NonNull String previewKey() { return profileSerial + " " + identity(); }
}
