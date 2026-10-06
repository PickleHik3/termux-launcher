package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.termux.R;

import java.util.List;

/**
 * The text and the controls of the dictation card, under its header strip (agreed design, "1b
 * Single control bar"): the whole dictation's text, the last {@link #VISIBLE_LINES} lines visible
 * (fewer where the pane area is short) and older ones fading out at the top as they scroll up,
 * the newest line always at the bottom. The panel is where the text waits: nothing is typed while
 * the user speaks.
 *
 * <p>Phrases arrive whole, so the panel does not pretend otherwise: a still ghost bar stands in,
 * inline after the text, for a phrase while it transcribes, then its words type out over
 * {@link #TYPE_MS} — laid out at once and revealed by moving a transparent span, so the lines
 * never reflow while typing. During the cleanup there is no ghost; the header says so.
 *
 * <p>The two versions of the text never look alike. As heard — while a cleanup is to come, and
 * again after undo — it is italic in the secondary text colour ({@code termuxColorOnSurfaceVariant});
 * cleaned, it is upright in the primary one ({@code termuxColorOnSurface}), so which one is on
 * screen reads at a glance, before any change mark. Text that is final as heard (cleanup off, or
 * nothing better came back) has no other version to be told from and shows upright and primary.
 * Once the one pass has landed (the model's, or the command formatter's), the cleaned text
 * replaces the as-heard one with the changes marked from {@link VoiceWordDiff}: changed and added
 * words in the accent colour, removed words struck through and dimmed. The marks stay for as long
 * as the cleaned text is on screen, so a long dictation's corrections can still be read; what is
 * inserted or copied is the plain cleaned text.
 *
 * <p>The text only ever moves one way: as heard, then cleaned. What is shown is always
 * {@link #shownText()}; a phrase that arrives after the cleanup joins that, never the as-heard
 * copy behind it, so an older version cannot come back. Only undo shows the as-heard text again,
 * and only on purpose, in its as-heard style.
 *
 * <p>Under the text, one row of controls. While listening it is a Pause button that carries the
 * waveform, Copy and Insert. Once the microphone is closed Pause gives way to Resume (a bare
 * mic; with its label and dimmed while phrases are still on their way), followed by undo (redo
 * once undone; only while there is a cleanup to take back) and, at the far end, Copy and Insert.
 * The row shows from the start, so pausing works before any text has come. The text scrolls by
 * finger; it follows its tail only while the reader is at the bottom. Both Copy and Insert may
 * be pressed early; the host stops listening and carries the press out once the text settles.
 * Discarding is the header's ×, or a swipe of the card.
 */
final class VoiceTranscriptPanel extends LinearLayout {

    interface Actions {
        void onUndo();

        void onCopy();

        void onInsert();

        /** Pause: stop listening; the text settles. */
        void onPause();

        /** Resume: listen again, carrying on the text. */
        void onResume();
    }

    static final int VISIBLE_LINES = 7;
    /** The fewest lines the panel shrinks to where the pane area is short (landscape, keyboard up). */
    static final int MIN_VISIBLE_LINES = 2;
    static final long TYPE_MS = 300L;
    /** The text's line height. */
    private static final int LINE_DP = 21;
    /** Above the text, under the header strip. */
    private static final int TEXT_TOP_DP = 2;
    /** Each control's height, and the pill radius that follows from it. */
    private static final int CONTROL_DP = 44;
    private static final int CONTROLS_TOP_DP = 12;
    private static final int CONTROLS_BOTTOM_DP = 8;
    private static final int CONTROLS_GAP_DP = 6;
    /** Under the text once the controls have gone. */
    private static final int DONE_SPACER_DP = 14;

    private final TextView text;
    private final LinearLayout controls;
    private final View doneSpacer;
    private final LinearLayout pause;
    private final VoiceWaveformView wave;
    private final ImageView resume;
    private final LinearLayout resumeDisabled;
    private final LinearLayout undo;
    private final ImageView undoIcon;
    private final TextView undoLabel;
    private final View spacer;
    private final int onSurface;
    /** The as-heard text's colour, the theme's secondary text colour. */
    private final int asHeard;
    private final int accent;
    private final int ghostColor;
    private final ForegroundColorSpan hidden = new ForegroundColorSpan(Color.TRANSPARENT);

    /** What goes into the cleanup: the text carried on from, then every phrase as heard. */
    private final StringBuilder raw = new StringBuilder();
    /** A cleanup is to come, so the raw text shows as heard rather than as final. */
    private boolean dimRaw;
    private int revealed;
    @Nullable private ValueAnimator typing;

    /** The cleanup that landed on {@link #raw}, or {@code null} while there is none. */
    @Nullable private String cleaned;
    @Nullable private List<VoiceWordDiff.Op> ops;
    /** Undo is showing {@link #raw} in place of {@link #cleaned}. */
    private boolean undone;
    /** A phrase is transcribing: the ghost bar stands in after the text. */
    private boolean ghost;
    /** The newest line stays in view as text arrives; off once the reader has scrolled up. */
    private boolean following = true;
    private boolean toggleListening = true;
    private boolean toggleEnabled = true;

    /**
     * @param onSurface the primary text colour: cleaned and final text, the icons
     * @param onSurfaceVariant the secondary text colour: text as heard
     * @param onAccent the colour on the accent: Insert's glyph and label
     * @param actionSurface the round and pill buttons' fill
     */
    VoiceTranscriptPanel(@NonNull Context context, int onSurface, int onSurfaceVariant, int accent,
                         int onAccent, int actionSurface, @NonNull Actions callbacks) {
        super(context);
        this.onSurface = onSurface;
        this.asHeard = onSurfaceVariant;
        this.accent = accent;
        this.ghostColor = (onSurface & 0x00FFFFFF) | 0x1F000000;
        setOrientation(VERTICAL);
        int ripple = (onSurface & 0x00FFFFFF) | 0x33000000;
        int onAccentRipple = (onAccent & 0x00FFFFFF) | 0x33000000;

        text = new TextView(context);
        text.setTextColor(onSurface);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        TextViewCompat.setLineHeight(text, dp(LINE_DP));
        text.setPadding(dp(16), dp(TEXT_TOP_DP), dp(16), 0);
        text.setMaxLines(VISIBLE_LINES);
        // Bottom gravity so TextView's own bring-into-view agrees with scrollToEnd.
        text.setGravity(Gravity.BOTTOM | Gravity.START);
        text.setMovementMethod(new ScrollingMovementMethod());
        text.setVerticalScrollBarEnabled(true);
        text.setScrollbarFadingEnabled(true);
        text.setOnScrollChangeListener((v, x, y, oldX, oldY) -> following = atBottom());
        text.setVisibility(GONE);
        text.setVerticalFadingEdgeEnabled(true);
        text.setFadingEdgeLength(dp(14));
        addView(text, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Pause, or Resume and undo, then Copy and Insert at the end. Every child is 44 tall and
        // the gap between them is each one's end margin, so a hidden child leaves no gap behind.
        controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPaddingRelative(dp(8), dp(CONTROLS_TOP_DP), dp(8), dp(CONTROLS_BOTTOM_DP));

        // Pause carries the waveform, so "listening" and "tap to stop" are one object. The
        // waveform gives up its width first, so the label never clips.
        pause = new LinearLayout(context);
        pause.setOrientation(HORIZONTAL);
        pause.setGravity(Gravity.CENTER_VERTICAL);
        pause.setPaddingRelative(dp(12), 0, dp(14), 0);
        pause.setBackground(filled(actionSurface, ripple, CONTROL_DP / 2));
        pause.setContentDescription(context.getString(R.string.voice_input_pause));
        pause.setOnClickListener(v -> callbacks.onPause());
        pause.addView(glyph(context, R.drawable.ic_symbol_pause, onSurface, 20));
        wave = new VoiceWaveformView(context, accent, onSurface);
        wave.setMinimumWidth(0);
        LayoutParams waveParams = new LayoutParams(0, dp(18), 1f);
        waveParams.setMarginStart(dp(8));
        waveParams.setMarginEnd(dp(8));
        pause.addView(wave, waveParams);
        pause.addView(label(context, R.string.voice_input_pause_label, 13, onSurface));
        controls.addView(pause, cell(0, 1f, true));

        // Resume: a bare mic while it can be pressed; with its label, dimmed and dead while the
        // phrases still on their way come in.
        resume = new ImageView(context);
        resume.setImageResource(R.drawable.ic_symbol_mic);
        resume.setColorFilter(onSurface, PorterDuff.Mode.SRC_IN);
        resume.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        resume.setPadding(dp(12), dp(12), dp(12), dp(12));
        resume.setBackground(filled(actionSurface, ripple, CONTROL_DP / 2));
        resume.setContentDescription(context.getString(R.string.voice_input_resume));
        resume.setOnClickListener(v -> callbacks.onResume());
        resume.setVisibility(GONE);
        controls.addView(resume, cell(dp(CONTROL_DP), 0f, true));

        resumeDisabled = new LinearLayout(context);
        resumeDisabled.setOrientation(HORIZONTAL);
        resumeDisabled.setGravity(Gravity.CENTER_VERTICAL);
        resumeDisabled.setPaddingRelative(dp(12), 0, dp(14), 0);
        resumeDisabled.setBackground(filled(actionSurface, ripple, CONTROL_DP / 2));
        resumeDisabled.setContentDescription(context.getString(R.string.voice_input_resume));
        resumeDisabled.addView(glyph(context, R.drawable.ic_symbol_mic, onSurface, 20));
        TextView resumeLabel = label(context, R.string.voice_input_resume_label, 13, onSurface);
        ((LayoutParams) resumeLabel.getLayoutParams()).setMarginStart(dp(6));
        resumeDisabled.addView(resumeLabel);
        resumeDisabled.setEnabled(false);
        resumeDisabled.setClickable(false);
        resumeDisabled.setAlpha(0.38f);
        resumeDisabled.setVisibility(GONE);
        controls.addView(resumeDisabled, cell(ViewGroup.LayoutParams.WRAP_CONTENT, 0f, true));

        // Undo, a text button; redo once undone. Only while a cleanup can be taken back.
        undo = new LinearLayout(context);
        undo.setOrientation(HORIZONTAL);
        undo.setGravity(Gravity.CENTER_VERTICAL);
        undo.setPaddingRelative(dp(10), 0, dp(10), 0);
        TypedValue borderless = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, borderless, true)) {
            undo.setBackgroundResource(borderless.resourceId);
        }
        undo.setContentDescription(context.getString(R.string.voice_input_undo));
        undo.setOnClickListener(v -> callbacks.onUndo());
        undoIcon = glyph(context, R.drawable.ic_symbol_undo, accent, 18);
        undo.addView(undoIcon);
        undoLabel = label(context, R.string.voice_input_undo_label, 13, accent);
        ((LayoutParams) undoLabel.getLayoutParams()).setMarginStart(dp(4));
        undo.addView(undoLabel);
        undo.setVisibility(GONE);
        controls.addView(undo, cell(ViewGroup.LayoutParams.WRAP_CONTENT, 0f, true));

        spacer = new View(context);
        spacer.setVisibility(GONE);
        controls.addView(spacer, cell(0, 1f, true));

        ImageView copy = new ImageView(context);
        copy.setImageResource(R.drawable.ic_symbol_content_copy);
        copy.setColorFilter(onSurface, PorterDuff.Mode.SRC_IN);
        copy.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        copy.setPadding(dp(12), dp(12), dp(12), dp(12));
        copy.setBackground(filled(actionSurface, ripple, CONTROL_DP / 2));
        copy.setContentDescription(context.getString(R.string.voice_input_cleanup_copy));
        copy.setOnClickListener(v -> callbacks.onCopy());
        controls.addView(copy, cell(dp(CONTROL_DP), 0f, true));

        LinearLayout insert = new LinearLayout(context);
        insert.setOrientation(HORIZONTAL);
        insert.setGravity(Gravity.CENTER_VERTICAL);
        insert.setPaddingRelative(dp(12), 0, dp(16), 0);
        insert.setBackground(filled(accent, onAccentRipple, CONTROL_DP / 2));
        insert.setContentDescription(context.getString(R.string.voice_input_insert));
        insert.setOnClickListener(v -> callbacks.onInsert());
        insert.addView(glyph(context, R.drawable.ic_symbol_check, onAccent, 18));
        TextView insertLabel = label(context, R.string.voice_input_insert_label, 14, onAccent);
        ((LayoutParams) insertLabel.getLayoutParams()).setMarginStart(dp(6));
        insert.addView(insertLabel);
        controls.addView(insert, cell(ViewGroup.LayoutParams.WRAP_CONTENT, 0f, false));
        addView(controls, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        doneSpacer = new View(context);
        doneSpacer.setVisibility(GONE);
        addView(doneSpacer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(DONE_SPACER_DP)));
        applyControls();
    }

    /**
     * How many lines of text show at most: {@link #VISIBLE_LINES}, fewer when the pane area below
     * the header has no room for them, never under {@link #MIN_VISIBLE_LINES}.
     */
    void setMaxVisibleLines(int lines) {
        int clamped = Math.max(MIN_VISIBLE_LINES, Math.min(VISIBLE_LINES, lines));
        if (text.getMaxLines() == clamped) return;
        text.setMaxLines(clamped);
        scrollToEnd();
    }

    /** One line of text's height in pixels, for {@link #setMaxVisibleLines}. */
    int lineHeightPx() {
        return dp(LINE_DP);
    }

    /** The height of everything but the text's lines: the room above them and the controls row. */
    int chromeHeightPx() {
        return getPaddingTop() + getPaddingBottom() + dp(TEXT_TOP_DP)
            + dp(CONTROLS_TOP_DP) + dp(CONTROL_DP) + dp(CONTROLS_BOTTOM_DP);
    }

    /** Whether raw text shows as heard (italic, secondary), waiting for the cleanup pass at the end. */
    void setDimRaw(boolean dim) {
        dimRaw = dim;
    }

    /** Shows the ghost bar after the text (a phrase is transcribing) or takes it away. */
    void setPending(boolean on) {
        if (ghost == on) return;
        ghost = on;
        rerender();
    }

    /** One level sample for the waveform inside Pause. */
    void pushLevel(float rms, boolean voiced, float noiseFloor) {
        wave.push(rms, voiced, noiseFloor);
    }

    /** The waveform rests while the microphone is closed. */
    void setWaveResting(boolean resting) {
        wave.setResting(resting);
    }

    /** What the cleanup changed, in edits (see {@link VoiceEditCount}); 0 without a cleanup. */
    int editCount() {
        List<VoiceWordDiff.Op> current = ops;
        return current == null ? 0 : VoiceEditCount.count(current);
    }

    /** Pause (while {@code listening}) or Resume; Resume waits, dimmed, until {@code enabled}. */
    void setToggle(boolean listening, boolean enabled) {
        toggleListening = listening;
        toggleEnabled = enabled;
        applyControls();
    }

    /** Which of the controls show: Pause while listening, otherwise Resume, undo where there is one, a gap. */
    private void applyControls() {
        pause.setVisibility(toggleListening ? VISIBLE : GONE);
        resume.setVisibility(!toggleListening && toggleEnabled ? VISIBLE : GONE);
        resumeDisabled.setVisibility(!toggleListening && !toggleEnabled ? VISIBLE : GONE);
        undo.setVisibility(!toggleListening && cleaned != null ? VISIBLE : GONE);
        spacer.setVisibility(toggleListening ? GONE : VISIBLE);
    }

    /** One phrase as it joins on (a leading space after an earlier one), typed out over {@link #TYPE_MS}. */
    void append(@NonNull String typed) {
        if (typed.isEmpty()) return;
        finishTyping();
        if (cleaned != null) {
            // Past the cleanup: the phrase joins the text as shown, never the as-heard copy behind it.
            String shown = shownText();
            raw.setLength(0);
            raw.append(shown);
        }
        clearCleanup();
        showControls();
        int from = raw.length();
        raw.append(typed);
        revealed = from;
        renderRaw();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            revealTo(raw.length());
            return;
        }
        int to = raw.length();
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(TYPE_MS);
        animator.addUpdateListener(a -> revealTo((int) a.getAnimatedValue()));
        typing = animator;
        animator.start();
    }

    /**
     * A new dictation carries on from {@code base}, the text that was waiting: it shows plain,
     * the earlier cleanup's marks gone, and the next phrase joins onto it.
     */
    void resetTo(@NonNull String base) {
        finishTyping();
        clearCleanup();
        raw.setLength(0);
        raw.append(base);
        revealed = raw.length();
        following = true;
        renderRaw();
        showControls();
    }

    /**
     * The cleanup has landed: {@code cleanedText} replaces the raw text with its changes marked.
     * The text is final from here; undo is offered.
     */
    void showCleaned(@NonNull String cleanedText) {
        finishTyping();
        clearCleanup();
        dimRaw = false;
        cleaned = cleanedText;
        ops = VoiceWordDiff.diff(raw.toString(), cleanedText);
        undone = false;
        applyControls();
        renderCleaned();
    }

    /**
     * The text is final as heard (no cleanup, or nothing better came back): no longer dim. A
     * cleanup already shown stays; this never brings the as-heard text back over it.
     */
    void showFinalRaw() {
        finishTyping();
        if (cleaned != null || !dimRaw) return;
        dimRaw = false;
        renderRaw();
    }

    /**
     * Undo ({@code true}): the text as it went into the cleanup, in the as-heard style; redo
     * ({@code false}): the cleaned text again, its changes marked. No-op without a cleanup.
     */
    void setUndone(boolean undoneNow) {
        if (cleaned == null || undone == undoneNow) return;
        undone = undoneNow;
        rerender();
        undoIcon.setImageResource(undone ? R.drawable.ic_symbol_redo : R.drawable.ic_symbol_undo);
        undoLabel.setText(undone ? R.string.voice_input_redo_label : R.string.voice_input_undo_label);
        undo.setContentDescription(getContext().getString(undone ? R.string.voice_input_redo : R.string.voice_input_undo));
    }

    /** The text as it stands on screen: the cleaned text, or what went into it when undone or not yet cleaned. */
    @NonNull
    String shownText() {
        return cleaned == null || undone ? raw.toString() : cleaned;
    }

    /** Insert or Copy has used the text: the controls go, the text stays with a little room under it. */
    void hideActions() {
        controls.setVisibility(GONE);
        doneSpacer.setVisibility(VISIBLE);
    }

    private void showControls() {
        controls.setVisibility(VISIBLE);
        doneSpacer.setVisibility(GONE);
    }

    /** Stops every animation; the panel is going away. */
    void release() {
        finishTyping();
        wave.setResting(true);
    }

    // ------------------------------------------------------------------ raw text

    private void revealTo(int end) {
        revealed = Math.min(end, raw.length());
        CharSequence shown = text.getText();
        if (!(shown instanceof Spannable)) return;
        Spannable spannable = (Spannable) shown;
        if (revealed >= spannable.length()) {
            spannable.removeSpan(hidden);
        } else {
            spannable.setSpan(hidden, revealed, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void finishTyping() {
        ValueAnimator animator = typing;
        typing = null;
        if (animator != null) animator.cancel();
        if (revealed < raw.length()) revealTo(raw.length());
    }

    /** Draws whichever version of the text is current again (the ghost came or went, or undo was pressed). */
    private void rerender() {
        if (cleaned != null && !undone) {
            renderCleaned();
        } else if (cleaned != null) {
            renderUndone();
        } else {
            renderRaw();
        }
    }

    private void renderRaw() {
        SpannableStringBuilder builder = new SpannableStringBuilder(raw);
        styleAsHeard(builder, dimRaw);
        if (revealed < builder.length()) {
            builder.setSpan(hidden, revealed, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        show(builder);
    }

    private void renderUndone() {
        SpannableStringBuilder builder = new SpannableStringBuilder(raw);
        styleAsHeard(builder, true);
        show(builder);
    }

    /** Puts {@code builder} on screen, the ghost bar after it while a phrase is transcribing. */
    private void show(@NonNull SpannableStringBuilder builder) {
        if (ghost) {
            if (builder.length() > 0) builder.append(' ');
            int start = builder.length();
            builder.append('￼');
            builder.setSpan(new GhostSpan(dp(56), dp(10), dp(5), dp(1), ghostColor), start, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.setVisibility(builder.length() > 0 ? VISIBLE : GONE);
        text.setText(builder, TextView.BufferType.SPANNABLE);
        scrollToEnd();
    }

    /**
     * The whole of {@code builder} as heard (italic, secondary colour) or as final (upright,
     * primary). The typing span goes on top of this and is removed on its own.
     */
    private void styleAsHeard(@NonNull SpannableStringBuilder builder, boolean heard) {
        int length = builder.length();
        builder.setSpan(new ForegroundColorSpan(heard ? asHeard : onSurface), 0, length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (heard) builder.setSpan(new StyleSpan(Typeface.ITALIC), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ------------------------------------------------------------------ cleaned text

    /** Forgets the cleanup (the text carries on, or starts over). */
    private void clearCleanup() {
        cleaned = null;
        ops = null;
        undone = false;
        undoIcon.setImageResource(R.drawable.ic_symbol_undo);
        undoLabel.setText(R.string.voice_input_undo_label);
        undo.setContentDescription(getContext().getString(R.string.voice_input_undo));
        applyControls();
    }

    /**
     * The cleaned text, from {@link #ops}, its changes marked: changed and added words in the
     * accent, removed words struck through and dimmed.
     */
    private void renderCleaned() {
        List<VoiceWordDiff.Op> current = ops;
        if (current == null) return;
        int removedColor = (onSurface & 0x00FFFFFF) | (0x73 << 24);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (VoiceWordDiff.Op op : current) {
            if (builder.length() > 0) {
                if (op.breaks > 0) {
                    for (int b = 0; b < op.breaks; b++) builder.append('\n');
                } else {
                    builder.append(' ');
                }
            }
            int start = builder.length();
            builder.append(op.word);
            int end = builder.length();
            int color;
            switch (op.kind) {
                case CHANGED:
                case ADDED:
                    color = accent;
                    break;
                case REMOVED:
                    color = removedColor;
                    builder.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                default:
                    color = onSurface;
                    break;
            }
            builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        show(builder);
    }

    // ------------------------------------------------------------------ layout

    /** Whether the text is scrolled to its end, give or take a few dp. */
    private boolean atBottom() {
        Layout layout = text.getLayout();
        if (layout == null) return true;
        int box = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        return layout.getHeight() - box - text.getScrollY() <= dp(8);
    }

    /**
     * Keeps the newest line at the bottom; the ones above scroll up under the fading edge. Left
     * alone while the reader has scrolled up; scrolling back to the end resumes it.
     */
    private void scrollToEnd() {
        text.post(() -> {
            if (!following) return;
            Layout layout = text.getLayout();
            if (layout == null) return;
            int box = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
            text.scrollTo(0, Math.max(0, layout.getHeight() - box));
        });
    }

    /** A control's place in the row: its width, its weight, its full 44 dp height, a gap after it unless it is last. */
    @NonNull
    private LayoutParams cell(int width, float weight, boolean gap) {
        LayoutParams params = new LayoutParams(width, dp(CONTROL_DP), weight);
        if (gap) params.setMarginEnd(dp(CONTROLS_GAP_DP));
        return params;
    }

    /** A rounded fill with a ripple over it. */
    @NonNull
    private Drawable filled(int fill, int ripple, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(ripple), shape, null);
    }

    /** A glyph of {@code sizeDp}, tinted {@code tint}, for the inside of a button. */
    @NonNull
    private ImageView glyph(@NonNull Context context, int icon, int tint, int sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setLayoutParams(new LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    /** A single-line medium-weight label for a button. */
    @NonNull
    private TextView label(@NonNull Context context, int string, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(string);
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

    /**
     * A still rounded bar inline in the text: a phrase is on its way. Drawn with its bottom a
     * little under the baseline so it sits on the line like a word would.
     */
    static final class GhostSpan extends ReplacementSpan {
        private final int width;
        private final int height;
        private final int radius;
        private final int drop;
        private final int color;
        private final RectF rect = new RectF();

        GhostSpan(int width, int height, int radius, int drop, int color) {
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.drop = drop;
            this.color = color;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           @Nullable Paint.FontMetricsInt fm) {
            return width;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top,
                         int y, int bottom, @NonNull Paint paint) {
            int saved = paint.getColor();
            paint.setColor(color);
            float lower = y + drop;
            rect.set(x, lower - height, x + width, lower);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setColor(saved);
        }
    }
}
