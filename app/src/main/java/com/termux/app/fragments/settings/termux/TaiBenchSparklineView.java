package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * The writing tile's sparkline: the running decode tok/s of the current entry, one bar per
 * token event, newest at the right, in the look of the voice pill's
 * {@code VoiceWaveformView} (2 dp rounded bars, 1.5 dp apart, the accent colour, a dim dot for
 * an empty slot). That view is not reused because its bar height runs through the voice level
 * curve over a held noise floor, which has no meaning for a token rate; here a bar's height is
 * the sample over the series' own maximum, bars grow from the baseline, and nothing animates.
 */
final class TaiBenchSparklineView extends View {
    private static final float MIN_HEIGHT_FRACTION = 0.08f;

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float barWidth;
    private final float barGap;
    private float[] levels = new float[0];

    TaiBenchSparklineView(@NonNull Context context) {
        super(context);
        lit.setColor(TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary));
        int onSurface = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        dim.setColor((onSurface & 0x00FFFFFF) | 0x40000000);
        barWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.getResources().getDisplayMetrics());
        barGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.getResources().getDisplayMetrics());
    }

    /** Replaces the series (oldest first); heights are scaled to its maximum. */
    void setSeries(@NonNull List<Float> series) {
        float max = 0f;
        for (Float value : series) if (value != null && value > max) max = value;
        float[] next = new float[series.size()];
        for (int i = 0; i < series.size(); i++) {
            Float value = series.get(i);
            next[i] = max <= 0f || value == null ? 0f : Math.max(0f, Math.min(1f, value / max));
        }
        levels = next;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        float step = barWidth + barGap;
        int visible = Math.max(0, (int) ((width + barGap) / step));
        float radius = barWidth / 2f;
        for (int i = 0; i < visible; i++) {
            // i = 0 is the newest sample, at the right edge.
            float right = width - i * step;
            float left = right - barWidth;
            int index = levels.length - 1 - i;
            float level = index >= 0 ? levels[index] : 0f;
            float tall = height * (MIN_HEIGHT_FRACTION + (1f - MIN_HEIGHT_FRACTION) * level);
            tall = Math.max(tall, barWidth);
            bar.set(left, height - tall, right, height);
            canvas.drawRoundRect(bar, radius, radius, index >= 0 && level > 0f ? lit : dim);
        }
    }
}
