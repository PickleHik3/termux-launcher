package com.termux.app.terminal.inappkeyboard.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * The pill's level display: a scrolling history, one bar per 30 ms slice of sound, newest at the
 * right, mirrored about the centre line (the voice-memo look of the agreed design). Each bar's
 * height is {@link VoiceLevelCurve#waveLevel} over {@link VoiceWaveformFloor}'s held floor, so the
 * floor stops following the VAD's rising percentile once speech starts and quiet speech still
 * fills the bars. Voiced slices take the accent colour, the rest stay dim.
 */
final class VoiceWaveformView extends View {

    /** Kept slices; more than the widest pill ever shows. */
    private static final int CAPACITY = 96;
    /** A silent slice still shows as a dot, so the row reads as a waveform at rest. */
    private static final float MIN_HEIGHT_FRACTION = 0.12f;

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final VoiceWaveformFloor floor = new VoiceWaveformFloor();
    private final float[] levels = new float[CAPACITY];
    private final boolean[] voicedSlices = new boolean[CAPACITY];
    private final float barWidth;
    private final float barGap;
    /** Index of the next slot to write; the newest slice sits just before it. */
    private int head;
    private int count;

    VoiceWaveformView(@NonNull Context context, int accent, int onSurface) {
        super(context);
        lit.setColor(accent);
        dim.setColor((onSurface & 0x00FFFFFF) | 0x55000000);
        barWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.getResources().getDisplayMetrics());
        barGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.getResources().getDisplayMetrics());
    }

    /** One 30 ms slice: its RMS, whether the VAD called it voiced, and the VAD's floor at that moment. */
    void push(float rms, boolean voiced, float noiseFloor) {
        float held = floor.update(noiseFloor, voiced);
        levels[head] = VoiceLevelCurve.waveLevel(rms, held);
        voicedSlices[head] = voiced;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        float step = barWidth + barGap;
        int visible = Math.min(CAPACITY, (int) ((width + barGap) / step));
        float mid = height / 2f;
        float radius = barWidth / 2f;
        for (int i = 0; i < visible; i++) {
            // i = 0 is the newest slice, at the right edge.
            float right = width - i * step;
            float left = right - barWidth;
            float level = 0f;
            boolean voiced = false;
            if (i < count) {
                int index = ((head - 1 - i) % CAPACITY + CAPACITY) % CAPACITY;
                level = levels[index];
                voiced = voicedSlices[index];
            }
            float half = height * (MIN_HEIGHT_FRACTION + (1f - MIN_HEIGHT_FRACTION) * level) / 2f;
            half = Math.max(half, radius);
            bar.set(left, mid - half, right, mid + half);
            canvas.drawRoundRect(bar, radius, radius, voiced && level > 0f ? lit : dim);
        }
    }
}
