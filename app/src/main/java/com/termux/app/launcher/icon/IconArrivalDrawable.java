package com.termux.app.launcher.icon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What an icon slot shows while its icon is being rendered elsewhere, and how the icon arrives: a
 * quiet rounded tile in the icon's own box that crossfades into the finished icon.
 *
 * <p>The slot shows this only while it waits and for the few frames of the arrival; when the fade
 * ends the slot is handed the icon itself ({@link AsyncIconBinder}), so everything that inspects a
 * bound icon afterwards — focus outlines, drag ghosts, the launch ripple — sees the shared rendered
 * drawable exactly as before.</p>
 *
 * <p>The arriving icon is drawn without being touched. It is the one instance the dock and every
 * drawer cell of that size share, so setting its alpha or bounds here would flicker every other
 * view drawing it in the same frame; a bitmap icon is drawn through this drawable's own paint and
 * anything else through a layer.</p>
 */
public final class IconArrivalDrawable extends Drawable {

    /**
     * The tile's margin inside the icon box, per side. The glass treatment insets icons by 3.5%;
     * the tile sits a little further in so it reads as a stand-in and never as a frame around the
     * icon that replaces it.
     */
    static final float TILE_INSET_FRACTION = 0.09f;
    /** Corner radius as a fraction of the tile's edge: near the squircle most adaptive masks use. */
    static final float TILE_CORNER_FRACTION = 0.30f;

    private final int sizePx;
    private final int tileColor;
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /** The layer an icon that is not a bitmap is composited through: its alpha and filter. */
    private final Paint layerPaint = new Paint();
    private final RectF tile = new RectF();
    private final RectF layerBounds = new RectF();
    private final Rect savedIconBounds = new Rect();
    @Nullable private Drawable icon;
    /** 0 shows the tile alone, 1 the icon alone; between, the one gives way to the other. */
    private float progress;
    private int alpha = 255;

    /**
     * @param sizePx    the icon's box, which is also this drawable's intrinsic size, so swapping it
     *                  for the rendered icon changes nothing about the view's layout
     * @param tileColor the tile, alpha included; the launcher's on-surface tone at a low alpha
     */
    public IconArrivalDrawable(int sizePx, int tileColor) {
        this.sizePx = Math.max(1, sizePx);
        this.tileColor = tileColor;
        tilePaint.setStyle(Paint.Style.FILL);
        tilePaint.setColor(tileColor);
    }

    /** The icon this tile is giving way to, or null while it still waits. */
    @Nullable
    public Drawable arrivingIcon() {
        return icon;
    }

    /** The finished icon has landed: the crossfade starts from the tile alone. */
    void arrive(@Nullable Drawable icon) {
        this.icon = icon;
        progress = 0f;
        invalidateSelf();
    }

    public float progress() {
        return progress;
    }

    void setProgress(float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (clamped == this.progress) return;
        this.progress = clamped;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float tileShare = (1f - progress) * (alpha / 255f);
        if (tileShare > 0f) {
            float inset = Math.min(bounds.width(), bounds.height()) * TILE_INSET_FRACTION;
            tile.set(bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset);
            float radius = Math.min(tile.width(), tile.height()) * TILE_CORNER_FRACTION;
            tilePaint.setAlpha(Math.round(Color.alpha(tileColor) * tileShare));
            canvas.drawRoundRect(tile, radius, radius, tilePaint);
        }
        Drawable arriving = icon;
        if (arriving == null || progress <= 0f) return;
        int iconAlpha = Math.round(255f * progress * (alpha / 255f));
        if (iconAlpha <= 0) return;
        Bitmap bitmap = arriving instanceof BitmapDrawable
            ? ((BitmapDrawable) arriving).getBitmap() : null;
        if (bitmap != null && !bitmap.isRecycled()) {
            iconPaint.setAlpha(iconAlpha);
            canvas.drawBitmap(bitmap, null, bounds, iconPaint);
            return;
        }
        layerBounds.set(bounds);
        layerPaint.setAlpha(iconAlpha);
        int save = canvas.saveLayer(layerBounds, layerPaint);
        savedIconBounds.set(arriving.getBounds());
        arriving.setBounds(bounds);
        arriving.draw(canvas);
        arriving.setBounds(savedIconBounds);
        canvas.restoreToCount(save);
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    /** The view's icon filter (a monochrome or tinted icon style) applies to tile and icon alike. */
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        tilePaint.setColorFilter(colorFilter);
        iconPaint.setColorFilter(colorFilter);
        layerPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return tilePaint.getColorFilter();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
