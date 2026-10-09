package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.TextViewCompat;

import com.termux.R;
import com.termux.ai.TaiTtsVoices;

import java.util.Random;

/**
 * The text and the controls of the reading card, Read aloud's body in the dictation card's shell
 * ({@link VoiceListeningIndicator#showReading}). The selection is shown whole, at the dictation
 * panel's size and line height: the sentence being heard in the accent colour, the ones already
 * read dimmed, the rest in the primary text colour. Each new sentence is scrolled into view with a
 * line of what came before above it ({@link #markSentence}); the text still scrolls by finger in
 * between.
 *
 * <p>Under it one row: Pause (with the waveform in it, as dictation's) or Resume, the voice, and
 * Stop. The waveform has no levels to show — the audio plays in the runtime process — so while
 * the voice is heard it moves to a made-up, speech-like rhythm, and rests while the reading is
 * being prepared, paused or done. The voice button lists {@link TaiTtsVoices#VOICES}; the pick is
 * the host's to keep, and is heard from the next sentence. Stop ends the reading and closes the
 * card, as the header's × and a swipe do. Once the reading is done the row goes and the text
 * stays a moment, all of it read.
 */
final class ReadAloudPanel extends LinearLayout {

    interface Actions {
        void onPause();

        void onResume();

        /** Stop: end the reading and close. */
        void onStop();

        /** A voice was picked from the menu; one of {@link TaiTtsVoices#VOICES}. */
        void onVoice(@NonNull String voice);
    }

    /** The text's line height, as the dictation panel's. */
    private static final int LINE_DP = 21;
    private static final int TEXT_TOP_DP = 2;
    private static final int CONTROL_DP = 44;
    private static final int CONTROLS_TOP_DP = 12;
    private static final int CONTROLS_BOTTOM_DP = 8;
    private static final int CONTROLS_GAP_DP = 6;
    private static final int DONE_SPACER_DP = 14;
    /** One made-up waveform slice, the same 30 ms the dictation's real ones are. */
    private static final long WAVE_TICK_MS = 30L;
    /** The floor the made-up levels are measured over; any positive value, the bar height is relative. */
    private static final float WAVE_FLOOR = 0.001f;
    private static final long SCROLL_MS = 220L;

    private final TextView text;
    private final LinearLayout controls;
    private final View doneSpacer;
    private final LinearLayout pause;
    private final ImageView pauseGlyph;
    private final TextView pauseLabel;
    private final VoiceWaveformView wave;
    private final TextView voiceLabel;
    private final LinearLayout voiceButton;
    private final String source;
    private final int onSurface;
    private final int read;
    private final int accent;
    private final Actions actions;
    private final Random random = new Random();

    private int sentenceStart = -1;
    private int sentenceEnd = -1;
    private boolean paused;
    private boolean sounding;
    private boolean done;
    @NonNull private String voice;
    /** Where the made-up rhythm is: the syllable's phase and a short gap between words. */
    private double wavePhase;
    private int waveGap;
    @Nullable private ValueAnimator scrolling;

    private final Runnable waveTick = new Runnable() {
        @Override
        public void run() {
            if (!wavePlaying()) return;
            wave.push(madeUpLevel(), true, WAVE_FLOOR);
            postDelayed(this, WAVE_TICK_MS);
        }
    };

    /**
     * @param onSurface the primary text colour: text still to come, the icons
     * @param onSurfaceVariant the secondary text colour, from which the read text's colour is made
     * @param onAccent the colour on the accent: Stop's glyph
     * @param actionSurface the pill buttons' fill
     */
    ReadAloudPanel(@NonNull Context context, int onSurface, int onSurfaceVariant, int accent, int onAccent,
                   int actionSurface, @NonNull String source, @NonNull String voice, @NonNull Actions actions) {
        super(context);
        this.source = source;
        this.onSurface = onSurface;
        this.read = (onSurfaceVariant & 0x00FFFFFF) | 0xA6000000;
        this.accent = accent;
        this.voice = voice;
        this.actions = actions;
        setOrientation(VERTICAL);
        int ripple = (onSurface & 0x00FFFFFF) | 0x33000000;
        int onAccentRipple = (onAccent & 0x00FFFFFF) | 0x33000000;

        text = new TextView(context);
        text.setTextColor(onSurface);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        TextViewCompat.setLineHeight(text, dp(LINE_DP));
        text.setPadding(dp(16), dp(TEXT_TOP_DP), dp(16), 0);
        text.setMaxLines(VoiceTranscriptPanel.VISIBLE_LINES);
        text.setGravity(Gravity.TOP | Gravity.START);
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setVerticalScrollBarEnabled(true);
        text.setScrollbarFadingEnabled(true);
        text.setVerticalFadingEdgeEnabled(true);
        text.setFadingEdgeLength(dp(14));
        text.setText(source);
        addView(text, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Pause or Resume, the voice, then Stop at the end; every child 44 tall, the gaps end margins.
        controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPaddingRelative(dp(8), dp(CONTROLS_TOP_DP), dp(8), dp(CONTROLS_BOTTOM_DP));

        pause = new LinearLayout(context);
        pause.setOrientation(HORIZONTAL);
        pause.setGravity(Gravity.CENTER_VERTICAL);
        pause.setPaddingRelative(dp(12), 0, dp(14), 0);
        pause.setBackground(filled(actionSurface, ripple));
        pause.setOnClickListener(v -> {
            if (paused) actions.onResume();
            else actions.onPause();
        });
        pauseGlyph = glyph(context, R.drawable.ic_symbol_pause, onSurface, 20);
        pause.addView(pauseGlyph);
        wave = new VoiceWaveformView(context, accent, onSurface);
        wave.setMinimumWidth(0);
        wave.setResting(true);
        LayoutParams waveParams = new LayoutParams(0, dp(18), 1f);
        waveParams.setMarginStart(dp(8));
        waveParams.setMarginEnd(dp(8));
        pause.addView(wave, waveParams);
        pauseLabel = label(context, R.string.voice_input_pause_label, 13, onSurface);
        pause.addView(pauseLabel);
        controls.addView(pause, cell(0, 1f, true));

        voiceButton = new LinearLayout(context);
        voiceButton.setOrientation(HORIZONTAL);
        voiceButton.setGravity(Gravity.CENTER_VERTICAL);
        voiceButton.setPaddingRelative(dp(12), 0, dp(14), 0);
        voiceButton.setBackground(filled(actionSurface, ripple));
        voiceButton.addView(glyph(context, R.drawable.ic_symbol_read_aloud, onSurface, 18));
        voiceLabel = label(context, 0, 13, onSurface);
        ((LayoutParams) voiceLabel.getLayoutParams()).setMarginStart(dp(6));
        voiceButton.addView(voiceLabel);
        voiceButton.setOnClickListener(this::showVoices);
        controls.addView(voiceButton, cell(ViewGroup.LayoutParams.WRAP_CONTENT, 0f, true));

        ImageView stop = new ImageView(context);
        stop.setImageResource(R.drawable.ic_symbol_stop);
        stop.setColorFilter(onAccent, PorterDuff.Mode.SRC_IN);
        stop.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        stop.setPadding(dp(12), dp(12), dp(12), dp(12));
        stop.setBackground(filled(accent, onAccentRipple));
        stop.setContentDescription(context.getString(R.string.read_aloud_stop));
        stop.setOnClickListener(v -> actions.onStop());
        controls.addView(stop, cell(dp(CONTROL_DP), 0f, false));
        addView(controls, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        doneSpacer = new View(context);
        doneSpacer.setVisibility(GONE);
        addView(doneSpacer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(DONE_SPACER_DP)));
        applyPause();
        applyVoice();
    }

    /** As {@link VoiceTranscriptPanel#setMaxVisibleLines}: as many lines as the room holds, within the panel's bounds. */
    void setMaxVisibleLines(int lines) {
        int clamped = Math.max(VoiceTranscriptPanel.MIN_VISIBLE_LINES, Math.min(VoiceTranscriptPanel.VISIBLE_LINES, lines));
        if (text.getMaxLines() == clamped) return;
        text.setMaxLines(clamped);
        if (sentenceStart >= 0) scrollToSpan(sentenceStart, sentenceEnd, false);
    }

    int lineHeightPx() {
        return dp(LINE_DP);
    }

    /** Everything but the text's lines: the room above them and the controls row. */
    int chromeHeightPx() {
        return getPaddingTop() + getPaddingBottom() + dp(TEXT_TOP_DP)
            + dp(CONTROLS_TOP_DP) + dp(CONTROL_DP) + dp(CONTROLS_BOTTOM_DP);
    }

    /** {@code source[start, end)} is the sentence being heard: marked, what came before dimmed, scrolled into view. */
    void markSentence(int start, int end) {
        sentenceStart = Math.max(0, Math.min(start, source.length()));
        sentenceEnd = Math.max(sentenceStart, Math.min(end, source.length()));
        render();
        scrollToSpan(sentenceStart, sentenceEnd, true);
    }

    /** The first sound is out: the waveform starts moving (unless paused). */
    void setSounding() {
        sounding = true;
        applyWave();
    }

    /** Pause turns to Resume and the waveform rests, or the other way. */
    void setPaused(boolean pausedNow) {
        if (paused == pausedNow) return;
        paused = pausedNow;
        applyPause();
    }

    /** The reading was heard through: all of it shows read, the controls go, the waveform rests. */
    void showDone() {
        done = true;
        sentenceStart = source.length();
        sentenceEnd = source.length();
        render();
        controls.setVisibility(GONE);
        doneSpacer.setVisibility(VISIBLE);
        applyWave();
    }

    /** Stops every animation; the card is going away. */
    void release() {
        done = true;
        removeCallbacks(waveTick);
        ValueAnimator animator = scrolling;
        scrolling = null;
        if (animator != null) animator.cancel();
        wave.setResting(true);
    }

    // ------------------------------------------------------------------ text

    private void render() {
        SpannableStringBuilder builder = new SpannableStringBuilder(source);
        int start = Math.max(0, sentenceStart);
        int end = Math.max(start, sentenceEnd);
        if (start > 0) builder.setSpan(new ForegroundColorSpan(read), 0, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (end > start) builder.setSpan(new ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (end < builder.length()) {
            builder.setSpan(new ForegroundColorSpan(onSurface), end, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Keeps the scroll where it is: setText would otherwise leave it, but a reflow may not.
        int scrollY = text.getScrollY();
        text.setText(builder, TextView.BufferType.SPANNABLE);
        text.scrollTo(0, scrollY);
    }

    /**
     * Brings {@code [start, end)} into view if any of it is out: its first line goes one line
     * below the top, so the end of what was just read is still there to follow on from; a
     * sentence taller than the box starts at the top. Left where it is when it already shows.
     */
    private void scrollToSpan(int start, int end, boolean animate) {
        text.post(() -> {
            Layout layout = text.getLayout();
            if (layout == null) return;
            int box = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
            if (box <= 0) return;
            int firstLine = layout.getLineForOffset(start);
            int lastLine = layout.getLineForOffset(Math.max(start, end - 1));
            int top = layout.getLineTop(firstLine);
            int bottom = layout.getLineBottom(lastLine);
            int current = text.getScrollY();
            if (top >= current && bottom <= current + box) return;
            int target = bottom - top > box - lineHeightPx() ? top : top - (firstLine > 0 ? lineHeightPx() : 0);
            target = Math.max(0, Math.min(target, layout.getHeight() - box));
            scrollTextTo(target, animate);
        });
    }

    private void scrollTextTo(int target, boolean animate) {
        ValueAnimator running = scrolling;
        scrolling = null;
        if (running != null) running.cancel();
        int from = text.getScrollY();
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || from == target) {
            text.scrollTo(0, target);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(SCROLL_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> text.scrollTo(0, (int) a.getAnimatedValue()));
        scrolling = animator;
        animator.start();
    }

    // ------------------------------------------------------------------ controls

    private void applyPause() {
        pauseGlyph.setImageResource(paused ? R.drawable.ic_media_play_arrow : R.drawable.ic_symbol_pause);
        pauseLabel.setText(paused ? R.string.voice_input_resume_label : R.string.voice_input_pause_label);
        pause.setContentDescription(getContext().getString(paused ? R.string.read_aloud_resume : R.string.read_aloud_pause));
        applyWave();
    }

    private void applyVoice() {
        voiceLabel.setText(voice);
        voiceButton.setContentDescription(getContext().getString(R.string.read_aloud_voice, voice));
    }

    /** The four voices, the current one checked; a pick is the host's to keep and shows on the button. */
    private void showVoices(@NonNull View anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        Menu items = menu.getMenu();
        for (int i = 0; i < TaiTtsVoices.VOICES.length; i++) {
            MenuItem item = items.add(Menu.NONE, i, i, TaiTtsVoices.VOICES[i]);
            item.setCheckable(true);
            item.setChecked(TaiTtsVoices.VOICES[i].equals(voice));
        }
        items.setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index < 0 || index >= TaiTtsVoices.VOICES.length) return false;
            String picked = TaiTtsVoices.VOICES[index];
            if (!picked.equals(voice)) {
                voice = picked;
                applyVoice();
                actions.onVoice(picked);
            }
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------ waveform

    private boolean wavePlaying() {
        return sounding && !paused && !done && isAttachedToWindow();
    }

    private void applyWave() {
        boolean playing = wavePlaying();
        wave.setResting(!playing);
        removeCallbacks(waveTick);
        if (playing) post(waveTick);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyWave();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(waveTick);
        super.onDetachedFromWindow();
    }

    /**
     * One made-up 30 ms slice as an RMS over {@link #WAVE_FLOOR}: syllables about five a second,
     * each a rise and fall with a little jitter, and now and then a short gap between words. The
     * waveform maps 3..30 dB over the floor to its height, so the level is turned into that.
     */
    private float madeUpLevel() {
        double level;
        if (waveGap > 0) {
            waveGap--;
            level = 0.05 * random.nextDouble();
        } else {
            wavePhase += 0.8 + 0.3 * random.nextDouble();
            double envelope = Math.pow(Math.abs(Math.sin(wavePhase)), 0.7);
            level = 0.15 + 0.75 * envelope * (0.7 + 0.3 * random.nextDouble());
            if (random.nextDouble() < 0.04) waveGap = 2 + random.nextInt(4);
        }
        double db = 3.0 + 27.0 * Math.max(0.0, Math.min(1.0, level));
        return (float) (WAVE_FLOOR * Math.pow(10.0, db / 20.0));
    }

    // ------------------------------------------------------------------ pieces

    @NonNull
    private LayoutParams cell(int width, float weight, boolean gap) {
        LayoutParams params = new LayoutParams(width, dp(CONTROL_DP), weight);
        if (gap) params.setMarginEnd(dp(CONTROLS_GAP_DP));
        return params;
    }

    /** A pill: the rounded fill with a ripple over it. */
    @NonNull
    private Drawable filled(int fill, int ripple) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(CONTROL_DP / 2));
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape, null);
    }

    @NonNull
    private ImageView glyph(@NonNull Context context, int icon, int tint, int sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    /** A single-line medium-weight label; {@code string} 0 leaves it empty for the caller to fill. */
    @NonNull
    private TextView label(@NonNull Context context, int string, int sp, int color) {
        TextView view = new TextView(context);
        if (string != 0) view.setText(string);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics()));
    }
}
