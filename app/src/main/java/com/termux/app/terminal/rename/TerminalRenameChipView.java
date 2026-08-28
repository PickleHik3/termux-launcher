package com.termux.app.terminal.rename;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The drawn half of an inline rename: a small glass chip showing what is being renamed, the draft
 * name and a caret.
 *
 * <p>Never an editor. It declares itself not a text editor, never takes focus and holds no
 * {@code InputConnection}, so the terminal view keeps owning input and no system IME is summoned by
 * this chip existing. Everything typed arrives through {@link InlineRenameController}.
 */
public final class TerminalRenameChipView extends View {

    private static final float HORIZONTAL_PADDING_DP = 12f;
    private static final float MIN_TEXT_WIDTH_DP = 96f;
    private static final float HEIGHT_DP = 40f;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    /** The target's id, kept for the content description now that the chip no longer draws it. */
    @NonNull private String label = "";
    @Nullable private InlineRenameModel model;
    /**
     * Shown in place of an empty draft. This is the target — "window", "session", "pane" — so one
     * greyed word says both what is being renamed and that nothing has been typed yet.
     */
    @NonNull private String emptyHint = "";

    public TerminalRenameChipView(@NonNull Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(14f * scaledDensity);
        setFocusable(false);
        setClickable(true);
        setMinimumHeight(Math.round(HEIGHT_DP * density));
    }

    /** Monospace for the draft: this names a terminal surface, and it is what the tab will show. */
    public void setTypeface(@Nullable Typeface typeface) {
        textPaint.setTypeface(typeface == null ? Typeface.MONOSPACE : typeface);
        requestLayout();
    }

    /**
     * The first colour is no longer used — the chip stopped drawing a separate label — but the
     * three-colour signature stays so the host keeps handing over one palette for the whole chip.
     */
    public void setColors(int labelColor, int textColor, int caretColor) {
        textPaint.setColor(textColor);
        caretPaint.setColor(caretColor);
        invalidate();
    }

    public void bind(@NonNull TerminalRenameTarget target, @NonNull InlineRenameModel model,
                     @NonNull String emptyHint) {
        this.label = target.id;
        this.model = model;
        this.emptyHint = emptyHint;
        setContentDescription(label + " name " + model.text());
        requestLayout();
        invalidate();
    }

    /** Redraw for a draft change without re-measuring when the width already fits. */
    public void refresh() {
        InlineRenameModel current = model;
        if (current != null) setContentDescription(label + " name " + current.text());
        requestLayout();
        invalidate();
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float padding = HORIZONTAL_PADDING_DP * density;
        InlineRenameModel current = model;
        String drawn = current == null || current.isEmpty() ? emptyHint
            : current.text();
        float textWidth = Math.max(MIN_TEXT_WIDTH_DP * density, textPaint.measureText(drawn)
            + textPaint.getTextSize());
        int desired = Math.round(padding * 2 + textWidth);
        int height = Math.round(HEIGHT_DP * density);
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        InlineRenameModel current = model;
        if (current == null) return;
        float padding = HORIZONTAL_PADDING_DP * density;
        float textStart = padding;
        float baseline = (getHeight() - (textPaint.descent() + textPaint.ascent())) / 2f;
        String text = current.text();
        if (current.isEmpty()) {
            int full = textPaint.getAlpha();
            textPaint.setAlpha(full / 2);
            canvas.drawText(emptyHint, textStart, baseline, textPaint);
            textPaint.setAlpha(full);
        } else {
            canvas.drawText(text, textStart, baseline, textPaint);
        }
        int utf16 = text.offsetByCodePoints(0, Math.min(current.caret(), current.codePointCount()));
        float caretX = textStart + textPaint.measureText(text, 0, utf16);
        canvas.drawRect(caretX, baseline + textPaint.ascent(),
            caretX + Math.max(1f, 1.5f * density), baseline + textPaint.descent(), caretPaint);
    }
}
