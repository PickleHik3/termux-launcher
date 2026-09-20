package com.termux.app.tour;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The finger glyph itself: one place that paints what {@link TourFingerTrace} works out, so the
 * run's cards and help's "Show gesture" show the user the same movement rather than two drawings
 * that drifted apart.
 *
 * <p>A phone set to play no animations gets {@link #drawStaticCue} instead — the same path, with
 * a dot where the finger starts and an arrow where it ends, so the direction is still said.
 */
public final class TourFingerPainter {

    private static final float RADIUS_DP = 9f;
    private static final float TRAIL_WIDTH_DP = 3f;
    private static final float ARROW_DP = 7f;

    private TourFingerPainter() {}

    /** The finger partway through its pass, with the trail it has left behind it. */
    public static void draw(@NonNull Canvas canvas, @NonNull Paint paint,
                            @NonNull TourGesture gesture, float left, float top, float right,
                            float bottom, float density, float progress, int accent,
                            @NonNull float[] scratchPoint, @NonNull float[] scratchTrail) {
        draw(canvas, paint, gesture, left, top, right, bottom, density, progress, accent,
            scratchPoint, scratchTrail, null);
    }

    /**
     * The same, held inside the area the cue is allowed to draw in.
     *
     * <p>A control against the edge of the screen — the first cap of the keys row is flush with
     * it — puts the finger's own circle half off the display, and a soft ring cut in half by the
     * edge reads as a drawing mistake rather than as a gesture. {@code cueBounds} is where the
     * centre may be, so the caller has already taken the widest ring off it; null asks for no
     * holding at all.
     */
    public static void draw(@NonNull Canvas canvas, @NonNull Paint paint,
                            @NonNull TourGesture gesture, float left, float top, float right,
                            float bottom, float density, float progress, int accent,
                            @NonNull float[] scratchPoint, @NonNull float[] scratchTrail,
                            @Nullable RectF cueBounds) {
        if (gesture == TourGesture.NONE) return;
        TourFingerTrace.pointAt(gesture, left, top, right, bottom, density, progress, scratchPoint);
        TourGlowGeometry.clampPoint(scratchPoint, cueBounds);
        float radius = RADIUS_DP * density;
        int opaque = opaque(accent);
        if (gesture == TourGesture.HOLD) {
            // A press that stays down: the halo closes onto the finger and holds there, where a
            // tap's ring would already have expanded away. It is what tells the two apart.
            float press = TourFingerTrace.holdPress(progress);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(opaque, Math.round(38f * press)));
            canvas.drawCircle(scratchPoint[0], scratchPoint[1], radius * (1f + (0.9f * press)),
                paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.5f * density));
            paint.setColor(ColorUtils.setAlphaComponent(opaque, Math.round(120f * press)));
            canvas.drawCircle(scratchPoint[0], scratchPoint[1], radius * (1f + (0.9f * press)),
                paint);
        } else if (gesture != TourGesture.TAP) {
            TourFingerTrace.pointAt(gesture, left, top, right, bottom, density, 0f, scratchTrail);
            TourGlowGeometry.clampPoint(scratchTrail, cueBounds);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(TRAIL_WIDTH_DP * density);
            paint.setColor(ColorUtils.setAlphaComponent(opaque, 46));
            canvas.drawLine(scratchTrail[0], scratchTrail[1], scratchPoint[0], scratchPoint[1],
                paint);
        } else {
            float pulse = TourFingerTrace.tapPulse(progress);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.5f * density));
            paint.setColor(ColorUtils.setAlphaComponent(opaque, Math.round(90f * (1f - pulse))));
            canvas.drawCircle(scratchPoint[0], scratchPoint[1], radius + (radius * pulse), paint);
        }
        drawFinger(canvas, paint, scratchPoint[0], scratchPoint[1], radius, opaque, density);
    }

    /**
     * The same gesture for a phone that plays no animations: where the finger would start, where
     * it would end, and an arrow saying which way it went.
     */
    public static void drawStaticCue(@NonNull Canvas canvas, @NonNull Paint paint,
                                     @NonNull Path scratchPath, @NonNull TourGesture gesture,
                                     float left, float top, float right, float bottom,
                                     float density, int accent, @NonNull float[] scratchPoint,
                                     @NonNull float[] scratchTrail) {
        if (gesture == TourGesture.NONE) return;
        int opaque = opaque(accent);
        float radius = RADIUS_DP * density;
        TourFingerTrace.pointAt(gesture, left, top, right, bottom, density, 0f, scratchTrail);
        TourFingerTrace.pointAt(gesture, left, top, right, bottom, density, 1f, scratchPoint);
        if (gesture == TourGesture.TAP || gesture == TourGesture.HOLD) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.5f * density));
            paint.setColor(ColorUtils.setAlphaComponent(opaque, 120));
            canvas.drawCircle(scratchPoint[0], scratchPoint[1], radius * 2f, paint);
            // A second ring, drawn where the held halo rests, for the phone that plays no
            // animations: the still picture of a hold has to say more than the still picture of a
            // tap, and there is no direction to point in.
            if (gesture == TourGesture.HOLD) {
                paint.setColor(ColorUtils.setAlphaComponent(opaque, 72));
                canvas.drawCircle(scratchPoint[0], scratchPoint[1], radius * 1.45f, paint);
            }
            drawFinger(canvas, paint, scratchPoint[0], scratchPoint[1], radius, opaque, density);
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(TRAIL_WIDTH_DP * density);
        paint.setColor(ColorUtils.setAlphaComponent(opaque, 120));
        canvas.drawLine(scratchTrail[0], scratchTrail[1], scratchPoint[0], scratchPoint[1], paint);
        drawFinger(canvas, paint, scratchTrail[0], scratchTrail[1], radius * 0.45f, opaque, density);
        drawArrowHead(canvas, paint, scratchPath, scratchTrail, scratchPoint, density, opaque);
    }

    private static void drawFinger(@NonNull Canvas canvas, @NonNull Paint paint, float x, float y,
                                   float radius, int opaque, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(opaque, 56));
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.5f * density));
        paint.setColor(ColorUtils.setAlphaComponent(opaque, 199));
        canvas.drawCircle(x, y, radius, paint);
    }

    /** The head of the arrow, pointing the way the finger travelled. */
    private static void drawArrowHead(@NonNull Canvas canvas, @NonNull Paint paint,
                                      @NonNull Path path, @NonNull float[] from,
                                      @NonNull float[] to, float density, int opaque) {
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) return;
        float ux = dx / length;
        float uy = dy / length;
        float size = ARROW_DP * density;
        float baseX = to[0] - (ux * size);
        float baseY = to[1] - (uy * size);
        path.reset();
        path.moveTo(to[0], to[1]);
        path.lineTo(baseX - (uy * size * 0.6f), baseY + (ux * size * 0.6f));
        path.lineTo(baseX + (uy * size * 0.6f), baseY - (ux * size * 0.6f));
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(opaque, 216));
        canvas.drawPath(path, paint);
    }

    private static int opaque(int color) {
        return android.graphics.Color.rgb(android.graphics.Color.red(color),
            android.graphics.Color.green(color), android.graphics.Color.blue(color));
    }
}
