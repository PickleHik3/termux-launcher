package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.List;

/**
 * The panel the pill grows into: the whole session's text, the last {@link #VISIBLE_LINES} lines
 * visible and older ones fading out under the pill as they scroll up (agreed design, "Panel").
 *
 * <p>Phrases arrive whole, so the panel does not pretend otherwise: a shimmer line stands in for a
 * phrase while it transcribes, then its words type out over {@link #TYPE_MS} — laid out at once
 * and revealed by moving a transparent span, so the lines never reflow while typing. With
 * animations off, a phrase appears at once and the shimmer is still.
 *
 * <p>With cleanup on, the raw text shows dim. Once the one pass has landed, the cleaned text
 * replaces it with the changes marked from {@link VoiceWordDiff}: changed and added words in the
 * accent colour, removed words struck through, held for {@link #MARK_HOLD_MS} and then faded into
 * the plain cleaned text. A long press toggles the raw text back. When the line could not be
 * swapped in place, Replace and Copy sit under the text.
 */
final class VoiceTranscriptPanel extends LinearLayout {

    interface Actions {
        void onReplace();

        void onCopy();
    }

    static final int VISIBLE_LINES = 4;
    static final long TYPE_MS = 300L;
    static final long MARK_HOLD_MS = 1500L;
    static final long MARK_FADE_MS = 400L;

    private final TextView text;
    private final ShimmerBar shimmer;
    private final LinearLayout actions;
    private final TextView replace;
    private final int onSurface;
    private final int dimText;
    private final int accent;
    private final ForegroundColorSpan hidden = new ForegroundColorSpan(Color.TRANSPARENT);

    /** Everything the session typed, as typed. */
    private final StringBuilder raw = new StringBuilder();
    private boolean dimRaw;
    private int revealed;
    @Nullable private ValueAnimator typing;

    @Nullable private List<VoiceWordDiff.Op> ops;
    private boolean showingRaw;
    @Nullable private ValueAnimator markFade;
    private final Runnable fadeMarks = this::fadeMarks;

    VoiceTranscriptPanel(@NonNull Context context, int onSurface, int accent, @NonNull Actions callbacks) {
        super(context);
        this.onSurface = onSurface;
        this.accent = accent;
        this.dimText = (onSurface & 0x00FFFFFF) | 0x99000000;
        setOrientation(VERTICAL);

        text = new TextView(context);
        text.setTextColor(onSurface);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setLineSpacing(0f, 1.1f);
        text.setMaxLines(VISIBLE_LINES);
        // Bottom gravity so TextView's own bring-into-view agrees with scrollToEnd.
        text.setGravity(Gravity.BOTTOM | Gravity.START);
        text.setVerticalScrollBarEnabled(false);
        text.setVerticalFadingEdgeEnabled(true);
        text.setFadingEdgeLength(dp(14));
        text.setOnLongClickListener(v -> toggleRaw());
        text.setLongClickable(false);
        addView(text, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shimmer = new ShimmerBar(context, onSurface);
        LayoutParams shimmerParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        shimmerParams.topMargin = dp(6);
        shimmerParams.setMarginEnd(dp(48));
        shimmer.setVisibility(GONE);
        addView(shimmer, shimmerParams);

        actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.END);
        replace = actionButton(context, R.string.voice_input_cleanup_replace, v -> callbacks.onReplace());
        actions.addView(replace);
        actions.addView(actionButton(context, R.string.voice_input_cleanup_copy, v -> callbacks.onCopy()));
        actions.setVisibility(GONE);
        LayoutParams actionParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(4);
        addView(actions, actionParams);
    }

    /** Whether raw text shows dim, waiting for the cleanup pass at the end. */
    void setDimRaw(boolean dim) {
        dimRaw = dim;
    }

    /** Shows the shimmer line (a phrase transcribing, or the cleanup running) or hides it. */
    void setShimmering(boolean on) {
        shimmer.setVisibility(on ? VISIBLE : GONE);
    }

    boolean hasContent() {
        return raw.length() > 0 || shimmer.getVisibility() == VISIBLE;
    }

    /** One phrase, exactly as typed (a leading space after an earlier one), typed out over {@link #TYPE_MS}. */
    void append(@NonNull String typed) {
        if (typed.isEmpty()) return;
        finishTyping();
        clearCleanup();
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
     * The cleanup has landed: {@code cleaned} replaces the raw text with its changes marked.
     *
     * @param offerActions show Replace and Copy (the line could not be swapped in place)
     * @param canReplace whether Replace is among them (never for text that went off the terminal)
     */
    void showCleaned(@NonNull String cleaned, boolean offerActions, boolean canReplace) {
        finishTyping();
        removeCallbacks(fadeMarks);
        ops = VoiceWordDiff.diff(raw.toString(), cleaned);
        showingRaw = false;
        text.setLongClickable(true);
        renderCleaned(true, 0f);
        actions.setVisibility(offerActions ? VISIBLE : GONE);
        replace.setVisibility(canReplace ? VISIBLE : GONE);
        postDelayed(fadeMarks, MARK_HOLD_MS);
    }

    /** Replace or Copy has been used: the actions go, the cleaned text stays. */
    void hideActions() {
        actions.setVisibility(GONE);
    }

    /** Stops every animation; the panel is going away. */
    void release() {
        finishTyping();
        removeCallbacks(fadeMarks);
        if (markFade != null) {
            markFade.cancel();
            markFade = null;
        }
        shimmer.setVisibility(GONE);
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

    private void renderRaw() {
        SpannableStringBuilder builder = new SpannableStringBuilder(raw);
        builder.setSpan(new ForegroundColorSpan(dimRaw ? dimText : onSurface), 0, builder.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (revealed < builder.length()) {
            builder.setSpan(hidden, revealed, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.setText(builder, TextView.BufferType.SPANNABLE);
        scrollToEnd();
    }

    // ------------------------------------------------------------------ cleaned text

    private void clearCleanup() {
        if (ops == null) return;
        ops = null;
        showingRaw = false;
        text.setLongClickable(false);
        removeCallbacks(fadeMarks);
        if (markFade != null) {
            markFade.cancel();
            markFade = null;
        }
        actions.setVisibility(GONE);
    }

    private void fadeMarks() {
        if (ops == null || showingRaw) return;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            renderCleaned(false, 1f);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(MARK_FADE_MS);
        animator.addUpdateListener(a -> {
            if (ops == null || showingRaw) return;
            float fraction = (float) a.getAnimatedValue();
            if (fraction >= 1f) renderCleaned(false, 1f);
            else renderCleaned(true, fraction);
        });
        markFade = animator;
        animator.start();
    }

    /**
     * The cleaned text, from {@link #ops}. With {@code marks}, changed and added words are the
     * accent blended {@code fade} of the way to the ordinary colour and removed words are struck
     * through, fading out; without, removed words are gone and the text is plain.
     */
    private void renderCleaned(boolean marks, float fade) {
        List<VoiceWordDiff.Op> current = ops;
        if (current == null) return;
        ArgbEvaluator blend = new ArgbEvaluator();
        int markColor = (Integer) blend.evaluate(fade, accent, onSurface);
        int removedAlpha = Math.round(0x99 * (1f - fade));
        int removedColor = (onSurface & 0x00FFFFFF) | (removedAlpha << 24);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (VoiceWordDiff.Op op : current) {
            if (op.kind == VoiceWordDiff.Kind.REMOVED && !marks) continue;
            if (builder.length() > 0) builder.append(' ');
            int start = builder.length();
            builder.append(op.word);
            int end = builder.length();
            int color;
            switch (op.kind) {
                case CHANGED:
                case ADDED:
                    color = marks ? markColor : onSurface;
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
        text.setText(builder, TextView.BufferType.SPANNABLE);
        scrollToEnd();
    }

    /** Long press: the raw text as heard, and back. */
    private boolean toggleRaw() {
        if (ops == null) return false;
        showingRaw = !showingRaw;
        if (showingRaw) {
            removeCallbacks(fadeMarks);
            if (markFade != null) {
                markFade.cancel();
                markFade = null;
            }
            SpannableStringBuilder builder = new SpannableStringBuilder(raw);
            builder.setSpan(new ForegroundColorSpan(dimText), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setText(builder, TextView.BufferType.SPANNABLE);
            scrollToEnd();
        } else {
            renderCleaned(false, 1f);
        }
        return true;
    }

    // ------------------------------------------------------------------ layout

    /** Keeps the newest line at the bottom; the ones above scroll up under the fading edge. */
    private void scrollToEnd() {
        text.post(() -> {
            Layout layout = text.getLayout();
            if (layout == null) return;
            int box = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
            text.scrollTo(0, Math.max(0, layout.getHeight() - box));
        });
    }

    @NonNull
    private TextView actionButton(@NonNull Context context, int label, @NonNull OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextColor(accent);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setMinHeight(dp(36));
        button.setGravity(Gravity.CENTER);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            button.setBackgroundResource(ripple.resourceId);
        }
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics()));
    }

    /**
     * A thin rounded bar with a highlight sweeping across it: a phrase is being transcribed, or
     * the cleanup is running. Still when animations are off.
     */
    static final class ShimmerBar extends View {
        private static final long SWEEP_MS = 1200L;

        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Matrix matrix = new Matrix();
        private final int highlight;
        @Nullable private LinearGradient gradient;
        @Nullable private ValueAnimator sweep;
        private float phase;

        ShimmerBar(@NonNull Context context, int onSurface) {
            super(context);
            base.setColor((onSurface & 0x00FFFFFF) | 0x22000000);
            highlight = (onSurface & 0x00FFFFFF) | 0x55000000;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            float band = Math.max(1f, w / 3f);
            gradient = new LinearGradient(0f, 0f, band, 0f,
                new int[] {Color.TRANSPARENT, highlight, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
            shine.setShader(gradient);
        }

        @Override
        protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
            super.onVisibilityChanged(changedView, visibility);
            updateSweep();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateSweep();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopSweep();
            super.onDetachedFromWindow();
        }

        private void updateSweep() {
            boolean shown = isAttachedToWindow() && isShown();
            if (!shown || !ValueAnimator.areAnimatorsEnabled()) {
                stopSweep();
                return;
            }
            if (sweep != null) return;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(SWEEP_MS);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            sweep = animator;
            animator.start();
        }

        private void stopSweep() {
            if (sweep != null) {
                sweep.cancel();
                sweep = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            rect.set(0f, 0f, w, h);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, base);
            if (sweep == null || gradient == null) return;
            float band = w / 3f;
            matrix.setTranslate(-band + phase * (w + band), 0f);
            gradient.setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, h / 2f, h / 2f, shine);
        }
    }
}
