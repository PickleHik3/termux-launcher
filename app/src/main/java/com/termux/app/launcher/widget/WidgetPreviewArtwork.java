package com.termux.app.launcher.widget;

import android.graphics.drawable.Drawable;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.icon.DrawablePixels;

/**
 * What a picker card has to show for one provider, in one of the three forms the platform offers.
 *
 * <p>The two live forms carry {@link RemoteViews} that are inflated into a real
 * {@code AppWidgetHostView}; the flat form carries the bitmap this launcher has always drawn. The
 * loader resolves which one exists, the card decides how to draw it, and neither has to know the
 * ladder the other is on.
 */
public final class WidgetPreviewArtwork {
    /** Generated preview, {@code AppWidgetManager.getWidgetPreview}, API 35. */
    public static final int TIER_GENERATED = 1;
    /** The provider's declared {@code previewLayout}, API 31. */
    public static final int TIER_PREVIEW_LAYOUT = 2;
    /** {@code loadPreviewImage}, or the provider icon when it ships neither. */
    public static final int TIER_IMAGE = 3;

    /**
     * Held for a provider that has nothing to show, so the miss is not re-queried on every bind.
     * Its tier is {@link #TIER_IMAGE} with no image: the card falls back to its own placeholder.
     */
    static final WidgetPreviewArtwork NONE = new WidgetPreviewArtwork(TIER_IMAGE, null, null);

    /**
     * What a live preview is charged against the preview budget. {@link RemoteViews} will not say
     * how much it holds and most of it is actions rather than pixels, so the store charges a flat
     * nominal cost — enough that a long picker session still evicts, not a measurement.
     */
    static final int LIVE_NOMINAL_BYTES = 32 * 1024;

    public final int tier;
    @Nullable public final RemoteViews remoteViews;
    @Nullable public final Drawable image;

    private WidgetPreviewArtwork(int tier, @Nullable RemoteViews remoteViews,
                                 @Nullable Drawable image) {
        this.tier = tier; this.remoteViews = remoteViews; this.image = image;
    }

    @NonNull static WidgetPreviewArtwork live(int tier, @NonNull RemoteViews remoteViews) {
        return new WidgetPreviewArtwork(tier, remoteViews, null);
    }

    @NonNull static WidgetPreviewArtwork image(@Nullable Drawable image) {
        return image == null ? NONE : new WidgetPreviewArtwork(TIER_IMAGE, null, image);
    }

    /** True when the card must build a host view rather than set a bitmap. */
    public boolean isLive() { return remoteViews != null; }

    /** True when there is nothing to draw and the card shows its own placeholder. */
    public boolean isEmpty() { return remoteViews == null && image == null; }

    int heldBytes() {
        return remoteViews != null ? LIVE_NOMINAL_BYTES : DrawablePixels.heldBytes(image);
    }
}
