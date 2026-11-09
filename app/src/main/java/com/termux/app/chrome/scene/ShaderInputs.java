package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The values a frame hands the shader, filled on the scene thread and read by {@link GlShader}.
 * Every array is allocated once; a frame only writes into them.
 *
 * <p>The clock ({@link #advance}) runs only while frames are drawn: a step longer than
 * {@link #MAX_STEP_MS} (the layer was covered, the screen was off, a hold) counts as that cap, so
 * the shader continues where it paused instead of jumping (spec decision 5), and {@link #pause}
 * makes the next step add nothing at all.</p>
 */
public final class ShaderInputs {

    /** The longest wall step the clock takes in one frame. */
    public static final long MAX_STEP_MS = 100L;
    private static final long NONE = Long.MIN_VALUE;

    /** Render size in pixels. */
    public int width, height;
    /** Seconds of shader time, and the last step. */
    public double timeSec;
    public float deltaSec;
    public int frame;
    public float frameRate;
    /** iMouse (render px, y up), touch (ShaderEditor), date (year, month from 0, day, seconds since midnight). */
    @NonNull public final float[] mouse = new float[4];
    @NonNull public final float[] touch = new float[2];
    @NonNull public final float[] date = new float[4];
    /** ShaderEditor's pointers: (x, y, size) per pointer, at most {@link ShaderPrelude#MAX_POINTERS}. */
    @NonNull public final float[] pointers = new float[ShaderPrelude.MAX_POINTERS * 3];
    public int pointerCount;
    @NonNull public final float[] offset = new float[2];
    public float pageOffset, battery = 1f, keyboard, dark;
    /** tlColor0..2: accent, surface, on-surface, three floats each. */
    @NonNull public final float[] colors = new float[9];
    /** iChannelResolution: (w, h, 1) per channel, zero for an empty one. */
    @NonNull public final float[] channelResolution = new float[ShaderPrelude.CHANNELS * 3];

    private long mLastMs = NONE;
    @NonNull private final Calendar mCalendar = Calendar.getInstance();
    private long mDateAtMs = NONE;

    /** Steps the clock to {@code nowMs}; returns the step in seconds. */
    public float advance(long nowMs) {
        long step = step(mLastMs, nowMs);
        mLastMs = nowMs;
        timeSec += step / 1000.0;
        deltaSec = step / 1000f;
        if (deltaSec > 0f) {
            float fps = 1f / deltaSec;
            frameRate = frameRate <= 0f ? fps : frameRate * 0.9f + fps * 0.1f;
        }
        return deltaSec;
    }

    /** The next {@link #advance} adds nothing: the clock stops while nothing is drawn. */
    public void pause() {
        mLastMs = NONE;
    }

    /** The wall step a frame adds: nothing after a pause or backwards, at most {@link #MAX_STEP_MS}. */
    static long step(long lastMs, long nowMs) {
        if (lastMs == NONE || nowMs <= lastMs) return 0L;
        return Math.min(MAX_STEP_MS, nowMs - lastMs);
    }

    /** Fills {@link #date} for wall time {@code epochMs}, re-read at most once a second. */
    public void setDate(long epochMs) {
        if (mDateAtMs != NONE && epochMs >= mDateAtMs && epochMs - mDateAtMs < 1000L) {
            date[3] += (epochMs - mDateAtMs) / 1000f;
            mDateAtMs = epochMs;
            return;
        }
        mDateAtMs = epochMs;
        fillDate(mCalendar, epochMs, date);
    }

    /** Shadertoy's and ShaderEditor's {@code iDate}/{@code date}: year, month from 0, day, seconds since midnight. */
    static void fillDate(@NonNull Calendar calendar, long epochMs, @NonNull float[] out) {
        calendar.setTimeZone(TimeZone.getDefault());
        calendar.setTimeInMillis(epochMs);
        out[0] = calendar.get(Calendar.YEAR);
        out[1] = calendar.get(Calendar.MONTH);
        out[2] = calendar.get(Calendar.DAY_OF_MONTH);
        out[3] = calendar.get(Calendar.HOUR_OF_DAY) * 3600f + calendar.get(Calendar.MINUTE) * 60f
            + calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f;
    }

    /** Sets tlColor{@code index} from an ARGB colour int, sRGB 0..1. */
    public void setColor(int index, int argb) {
        colors[index * 3] = ((argb >> 16) & 0xff) / 255f;
        colors[index * 3 + 1] = ((argb >> 8) & 0xff) / 255f;
        colors[index * 3 + 2] = (argb & 0xff) / 255f;
    }
}
