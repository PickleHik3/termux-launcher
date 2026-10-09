package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * The album-art stand-in: diagonal bands in the two container tones, the design's
 * {@code repeating-linear-gradient(135deg, ...)}. One repeating shader, one rect; the rounded
 * corners come from the outline of whatever holds it.
 */
final class MediaArtPlaceholderView extends android.view.View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Bands of {@code bandPx}, alternating {@code first} and {@code second}. */
    MediaArtPlaceholderView(@NonNull Context context, @ColorInt int first, @ColorInt int second,
                            float bandPx) {
        super(context);
        // A 135° gradient runs toward the bottom-right, so its period of two bands is measured
        // along the diagonal: the shader's end point is that period divided by √2 on each axis.
        float step = (2f * bandPx) / (float) Math.sqrt(2d);
        paint.setShader(new LinearGradient(0f, 0f, step, step,
            new int[] { first, first, second, second }, new float[] { 0f, 0.5f, 0.5f, 1f },
            Shader.TileMode.REPEAT));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
    }
}
