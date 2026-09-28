package com.termux.app.terminal.inappkeyboard.voice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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

import com.termux.R;

import java.util.List;

/**
 * The panel the pill grows into, downward toward the thumb: the whole dictation's text, the last
 * {@link #VISIBLE_LINES} lines visible (fewer where the pane area is short) and older ones fading
 * out at the top as they scroll up, the newest line always at the bottom (agreed design, "Panel").
 * The panel is where the text waits: nothing is typed while the user speaks.
 *
 * <p>Phrases arrive whole, so the panel does not pretend otherwise: a shimmer line stands in for a
 * phrase while it transcribes, then its words type out over {@link #TYPE_MS} — laid out at once
 * and revealed by moving a transparent span, so the lines never reflow while typing. With
 * animations off, a phrase appears at once and the shimmer is still.
 *
 * <p>The two versions of the text never look alike. As heard — while a cleanup is to come, and
 * again after undo — it is italic in the secondary text colour ({@code termuxColorOnSurfaceVariant});
 * cleaned, it is upright in the primary one ({@code termuxColorOnSurface}), so which one is on
 * screen reads at a glance, before any change mark. Text that is final as heard (cleanup off, or
 * nothing better came back) has no other version to be told from and shows upright and primary.
 * Once the one pass has landed (the model's, or the command formatter's), the cleaned text
 * replaces the as-heard one with the changes marked from {@link VoiceWordDiff}: changed and added
 * words in the accent colour, removed words struck through. The marks stay for as long as the
 * cleaned text is on screen, so a long dictation's corrections can still be read; what is
 * inserted or copied is the plain cleaned text.
 *
 * <p>The text only ever moves one way: as heard, then cleaned. What is shown is always
 * {@link #shownText()}; a phrase that arrives after the cleanup joins that, never the as-heard
 * copy behind it, so an older version cannot come back. Only undo shows the as-heard text again,
 * and only on purpose, in its as-heard style.
 *
 * <p>Under the text, one long rounded pill ({@code termuxColorSurfacePanelHigh}) the full width of
 * the panel holds the three controls, spread evenly across it: undo (and, once undone, redo; only
 * while there is a cleanup to take back), Copy and ✓ (insert at the cursor, once). They never
 * leave it. Both Copy and ✓ may be pressed early; the host stops listening and carries the press
 * out once the text settles. Discarding is the pill's ×, or a swipe of the card.
 */
final class VoiceTranscriptPanel extends LinearLayout {

    interface Actions {
        void onUndo();

        void onCopy();

        void onInsert();
    }

    static final int VISIBLE_LINES = 7;
    /** The fewest lines the panel shrinks to where the pane area is short (landscape, keyboard up). */
    static final int MIN_VISIBLE_LINES = 2;
    static final long TYPE_MS = 300L;
    /** The action pill's height, and each control's. */
    static final int ACTION_PILL_DP = 40;
    /** Between the text (or the shimmer line) and the action pill. */
    static final int ACTION_PILL_GAP_DP = 8;

    private final TextView text;
    private final ShimmerBar shimmer;
    private final LinearLayout actions;
    private final ImageView undo;
    private final int onSurface;
    /** The as-heard text's colour, the theme's secondary text colour. */
    private final int asHeard;
    private final int accent;
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

    /**
     * @param onSurface the primary text colour: cleaned and final text, the icons
     * @param onSurfaceVariant the secondary text colour: text as heard
     * @param actionSurface the action pill's fill
     */
    VoiceTranscriptPanel(@NonNull Context context, int onSurface, int onSurfaceVariant, int accent,
                         int actionSurface, @NonNull Actions callbacks) {
        super(context);
        this.onSurface = onSurface;
        this.asHeard = onSurfaceVariant;
        this.accent = accent;
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
        addView(text, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shimmer = new ShimmerBar(context, onSurface);
        LayoutParams shimmerParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        shimmerParams.topMargin = dp(6);
        shimmerParams.setMarginEnd(dp(48));
        shimmer.setVisibility(GONE);
        addView(shimmer, shimmerParams);

        // Undo, Copy and ✓ in one long pill the panel's width, spread evenly, ✓ at the end. Undo
        // is there only while a cleanup can be taken back; Copy and ✓ share the pill without it.
        actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(actionSurface);
        pill.setCornerRadius(dp(ACTION_PILL_DP) / 2f);
        actions.setBackground(pill);
        undo = iconButton(context, R.drawable.ic_symbol_undo, onSurface,
            R.string.voice_input_undo, v -> callbacks.onUndo());
        undo.setVisibility(GONE);
        actions.addView(undo, actionCell());
        actions.addView(iconButton(context, R.drawable.ic_symbol_content_copy, onSurface,
            R.string.voice_input_cleanup_copy, v -> callbacks.onCopy()), actionCell());
        actions.addView(iconButton(context, R.drawable.ic_symbol_check, accent,
            R.string.voice_input_insert, v -> callbacks.onInsert()), actionCell());
        actions.setVisibility(GONE);
        LayoutParams actionParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ACTION_PILL_DP));
        actionParams.topMargin = dp(ACTION_PILL_GAP_DP);
        addView(actions, actionParams);
    }

    /**
     * How many lines of text show at most: {@link #VISIBLE_LINES}, fewer when the pane area below
     * the pill has no room for them, never under {@link #MIN_VISIBLE_LINES}.
     */
    void setMaxVisibleLines(int lines) {
        int clamped = Math.max(MIN_VISIBLE_LINES, Math.min(VISIBLE_LINES, lines));
        if (text.getMaxLines() == clamped) return;
        text.setMaxLines(clamped);
        scrollToEnd();
    }

    /** One line of text's height in pixels, for {@link #setMaxVisibleLines}. */
    int lineHeightPx() {
        return text.getLineHeight();
    }

    /** The height of everything but the text: padding, the shimmer line's room and the action pill. */
    int chromeHeightPx() {
        return getPaddingTop() + getPaddingBottom() + dp(12) + dp(ACTION_PILL_GAP_DP) + dp(ACTION_PILL_DP);
    }

    /** Whether raw text shows as heard (italic, secondary), waiting for the cleanup pass at the end. */
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
        actions.setVisibility(VISIBLE);
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
        renderRaw();
        actions.setVisibility(raw.length() > 0 ? VISIBLE : GONE);
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
        showUndo(true);
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
        if (undone) {
            SpannableStringBuilder builder = new SpannableStringBuilder(raw);
            styleAsHeard(builder, true);
            text.setText(builder, TextView.BufferType.SPANNABLE);
            scrollToEnd();
        } else {
            renderCleaned();
        }
        undo.setImageResource(undone ? R.drawable.ic_symbol_redo : R.drawable.ic_symbol_undo);
        undo.setContentDescription(getContext().getString(undone ? R.string.voice_input_redo : R.string.voice_input_undo));
    }

    /** The text as it stands on screen: the cleaned text, or what went into it when undone or not yet cleaned. */
    @NonNull
    String shownText() {
        return cleaned == null || undone ? raw.toString() : cleaned;
    }

    /** ✓ or Copy has used the text: the buttons go, the text stays while the pill says so. */
    void hideActions() {
        actions.setVisibility(GONE);
    }

    /** Stops every animation; the panel is going away. */
    void release() {
        finishTyping();
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
        styleAsHeard(builder, dimRaw);
        if (revealed < builder.length()) {
            builder.setSpan(hidden, revealed, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
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
        showUndo(false);
    }

    private void showUndo(boolean shown) {
        if (!shown) {
            undo.setImageResource(R.drawable.ic_symbol_undo);
            undo.setContentDescription(getContext().getString(R.string.voice_input_undo));
        }
        undo.setVisibility(shown ? VISIBLE : GONE);
    }

    /**
     * The cleaned text, from {@link #ops}, its changes marked: changed and added words in the
     * accent, removed words struck through and dimmed.
     */
    private void renderCleaned() {
        List<VoiceWordDiff.Op> current = ops;
        if (current == null) return;
        int removedColor = (onSurface & 0x00FFFFFF) | (0x99 << 24);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (VoiceWordDiff.Op op : current) {
            if (builder.length() > 0) builder.append(' ');
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
        text.setText(builder, TextView.BufferType.SPANNABLE);
        scrollToEnd();
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

    /** One control's share of the action pill: an even third (or half, without undo), its full height. */
    @NonNull
    private LayoutParams actionCell() {
        return new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    /** A 20 dp glyph centred in its cell, tinted {@code tint}. */
    @NonNull
    private ImageView iconButton(@NonNull Context context, int icon, int tint, int description,
                                 @NonNull OnClickListener listener) {
        ImageView button = new ImageView(context);
        button.setImageResource(icon);
        button.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(context.getString(description));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
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
