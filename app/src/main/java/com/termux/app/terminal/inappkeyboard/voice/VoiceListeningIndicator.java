package com.termux.app.terminal.inappkeyboard.voice;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The small "Listening…" pill with a live level meter, floated just above the in-app keyboard
 * while a {@link VoiceInputSession} runs. It lives in the window's content frame rather than the
 * accessory stack, so it never takes part in the keyboard's geometry passes; its bottom margin is
 * re-read from the keyboard container's position on every level update, which is cheap and keeps
 * it above a keyboard that moves. After a segment transcribes, the pill shows that text until the
 * next one.
 */
public final class VoiceListeningIndicator {

    private static final int BARS = 5;

    private final Activity activity;
    private final View keyboardContainer;
    @Nullable private LinearLayout pill;
    @Nullable private TextView label;
    @Nullable private LevelMeterView meter;
    private int lastBottomMargin = -1;

    public VoiceListeningIndicator(@NonNull Activity activity, @NonNull View keyboardContainer) {
        this.activity = activity;
        this.keyboardContainer = keyboardContainer;
    }

    public void show() {
        if (pill != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        Context context = activity;
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundResource(R.drawable.settings_pill_background);
        view.setElevation(dp(4));
        int padH = dp(14), padV = dp(8);
        view.setPadding(padH, padV, padH, padV);
        view.setClickable(false);
        view.setFocusable(false);

        LevelMeterView levels = new LevelMeterView(context, themeColor(context, com.termux.shared.R.attr.termuxColorPrimary),
            themeColor(context, com.termux.shared.R.attr.termuxColorOnSurface));
        LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(dp(28), dp(16));
        meterParams.setMarginEnd(dp(10));
        view.addView(levels, meterParams);

        TextView text = new TextView(context);
        text.setTextColor(themeColor(context, com.termux.shared.R.attr.termuxColorOnSurface));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setSingleLine();
        text.setEllipsize(TextUtils.TruncateAt.START);
        text.setMaxWidth(dp(240));
        text.setText(R.string.voice_input_listening);
        view.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottomMargin(content);
        lastBottomMargin = params.bottomMargin;
        content.addView(view, params);
        pill = view;
        label = text;
        meter = levels;
    }

    public void hide() {
        LinearLayout view = pill;
        pill = null;
        label = null;
        meter = null;
        lastBottomMargin = -1;
        if (view == null) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) parent.removeView(view);
    }

    public boolean isShowing() {
        return pill != null;
    }

    /** A level sample, with the VAD's current noise floor; also keeps the pill above the keyboard as it moves. */
    public void setLevel(float rms, boolean voiced, float noiseFloor) {
        LevelMeterView levels = meter;
        LinearLayout view = pill;
        if (levels == null || view == null) return;
        levels.setLevel(rms, voiced, noiseFloor);
        ViewGroup content = (ViewGroup) view.getParent();
        if (content == null) return;
        int margin = bottomMargin(content);
        if (margin != lastBottomMargin) {
            lastBottomMargin = margin;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            params.bottomMargin = margin;
            view.setLayoutParams(params);
        }
    }

    /** The last transcript, shown in the pill until the next segment. */
    public void setTranscript(@NonNull CharSequence text) {
        TextView view = label;
        if (view != null) view.setText(text);
    }

    /** The room under the pill: from the content frame's bottom up to the keyboard's top, plus a gap. */
    private int bottomMargin(@NonNull ViewGroup content) {
        int gap = dp(8);
        if (keyboardContainer.getVisibility() != View.VISIBLE || keyboardContainer.getHeight() == 0) {
            return gap;
        }
        int[] keyboard = new int[2];
        int[] frame = new int[2];
        keyboardContainer.getLocationInWindow(keyboard);
        content.getLocationInWindow(frame);
        int contentBottom = frame[1] + content.getHeight();
        return Math.max(gap, contentBottom - keyboard[1] + gap);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics()));
    }

    private static int themeColor(@NonNull Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            return value.data;
        }
        return 0xFF888888;
    }

    /**
     * Five bars that light up with the microphone level, measured in dB above the VAD's adaptive
     * noise floor through {@link VoiceLevelCurve} — a fixed dBFS window left ordinary speech
     * (−40…−25 dBFS, since {@code VOICE_RECOGNITION} has no AGC) filling barely a quarter of it.
     */
    static final class LevelMeterView extends View {
        /** Per-frame decay once the peak hold has run out (Freestyle's pill decays at 0.78–0.8/frame). */
        private static final float DECAY = 0.78f;
        /** How many 30 ms frames a peak is held before it starts to decay, so a single syllable stays visible. */
        private static final int PEAK_HOLD_FRAMES = 6;

        private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private float level;
        private float peak;
        private int peakHoldFrames;

        LevelMeterView(@NonNull Context context, int accent, int onSurface) {
            super(context);
            lit.setColor(accent);
            dim.setColor((onSurface & 0x00FFFFFF) | 0x33000000);
        }

        void setLevel(float rms, boolean voiced, float noiseFloor) {
            float target = VoiceLevelCurve.level(rms, noiseFloor);
            if (target >= peak) {
                // Rises at once and arms the hold.
                level = target;
                peak = target;
                peakHoldFrames = PEAK_HOLD_FRAMES;
            } else if (peakHoldFrames > 0) {
                peakHoldFrames--;
                level = peak;
            } else {
                level = level * DECAY;
                peak = level;
            }
            if (!voiced && level < 0.05f) {
                level = 0f;
                peak = 0f;
                peakHoldFrames = 0;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            float slot = width / BARS;
            float barWidth = slot * 0.6f;
            for (int i = 0; i < BARS; i++) {
                float threshold = (i + 1f) / BARS;
                float barHeight = height * (0.35f + 0.65f * (i + 1f) / BARS);
                float left = i * slot + (slot - barWidth) / 2f;
                bar.set(left, height - barHeight, left + barWidth, height);
                canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, level >= threshold ? lit : dim);
            }
        }
    }
}
