package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

/** Allocation-free rounded-rect interpolation; deliberately owns no icon or bitmap. */
public final class AppDrawerCategoryMorphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Frame source;
    @Nullable private Frame destination;
    private float sourceRadius;
    private float destinationRadius;
    private float progress;

    public AppDrawerCategoryMorphView(@NonNull Context context) {
        super(context);
        paint.setColor(0x2FFFFFFF);
        setClickable(false);
    }

    public void setFrames(@Nullable Frame source, @Nullable Frame destination,
                          float sourceRadius, float destinationRadius) {
        this.source = source;
        this.destination = destination;
        this.sourceRadius = Math.max(0f, sourceRadius);
        this.destinationRadius = Math.max(0f, destinationRadius);
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = AppDrawerTransitionGeometry.clamp01(progress);
        setAlpha(1f - AppDrawerTransitionGeometry.ramp(this.progress, 0.75f, 1f));
        invalidate();
    }

    @Nullable public Frame currentFrame() {
        if (source == null || destination == null) return null;
        return new Frame(lerp(source.left, destination.left), lerp(source.top, destination.top),
            lerp(source.right, destination.right), lerp(source.bottom, destination.bottom));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Frame start = source;
        Frame end = destination;
        if (start == null || end == null) return;
        float radius = lerp(sourceRadius, destinationRadius);
        // Keep the animation draw path allocation-free. currentFrame() remains a diagnostic/test
        // seam, but the frame loop interpolates the four primitives directly.
        canvas.drawRoundRect(lerp(start.left, end.left), lerp(start.top, end.top),
            lerp(start.right, end.right), lerp(start.bottom, end.bottom),
            radius, radius, paint);
    }

    private float lerp(float start, float end) { return start + (end - start) * progress; }
}
