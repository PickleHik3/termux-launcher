package com.termux.app.launcher.popup;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One App menu row's surface: its group's shade, its own top and bottom corner radii, and the
 * state layer while the finger is on it. The path is rebuilt on a bounds change only, never per
 * frame, and it doubles as the outline the row's elevation casts its shadow from.
 */
public final class AppMenuRowDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final float topRadius;
    private final float bottomRadius;
    private boolean highlighted;

    public AppMenuRowDrawable(int shade, int stateLayer, float topRadius, float bottomRadius) {
        fill.setColor(shade);
        layer.setColor(stateLayer);
        this.topRadius = topRadius;
        this.bottomRadius = bottomRadius;
    }

    public int shade() {
        return fill.getColor();
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean value) {
        if (highlighted == value) return;
        highlighted = value;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rect.set(bounds);
        float half = rect.height() / 2f;
        float top = Math.min(topRadius, half);
        float bottom = Math.min(bottomRadius, half);
        radii[0] = radii[1] = radii[2] = radii[3] = top;
        radii[4] = radii[5] = radii[6] = radii[7] = bottom;
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, fill);
        if (highlighted) canvas.drawPath(path, layer);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        if (path.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(path);
        } else {
            // A rounded rectangle is convex whatever its corner radii.
            outline.setConvexPath(path);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
