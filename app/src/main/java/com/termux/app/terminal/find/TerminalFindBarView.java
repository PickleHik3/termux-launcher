package com.termux.app.terminal.find;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The drawn half of a find session: one strip the width of the dock, one text line tall.
 *
 * <p>A search bar, not a search screen. It sits above the dock at the height of the terminal's own
 * line, so the transcript it is searching stays on screen and keeps its highlights — the whole
 * reason the sheet plane was wrong for this. The prompt and query sit at the leading edge, the mode
 * tag and the match counter at the trailing one.</p>
 *
 * <p>Never an editor: it declares itself not a text editor, never takes focus and holds no
 * {@code InputConnection}, so the terminal keeps owning input and no system IME is summoned by this
 * strip existing. Everything typed arrives through {@link TerminalFindController}.</p>
 */
public final class TerminalFindBarView extends View {

    private static final float HORIZONTAL_PADDING_DP = 12f;
    /** Breathing room above and below the one text line. Any more and this stops being a strip. */
    private static final float VERTICAL_PADDING_DP = 7f;
    private static final float GAP_DP = 10f;
    private static final String PROMPT = "/";

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    @NonNull private String query = "";
    @NonNull private String counter = "";
    @NonNull private String modeTag = "";
    @NonNull private String hint = "";
    private boolean caretVisible = true;

    public TerminalFindBarView(@NonNull Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
        dimPaint.setTypeface(Typeface.MONOSPACE);
        dimPaint.setTextSize(textPaint.getTextSize());
        setFocusable(false);
        setClickable(true);
    }

    /** Matches the strip to the terminal it searches: same face, same size, same line. */
    public void setTerminalTextAppearance(@Nullable Typeface typeface, float textSizePx) {
        textPaint.setTypeface(typeface == null ? Typeface.MONOSPACE : typeface);
        dimPaint.setTypeface(textPaint.getTypeface());
        if (textSizePx > 0f) {
            textPaint.setTextSize(textSizePx);
            dimPaint.setTextSize(textSizePx);
        }
        requestLayout();
    }

    public void setColors(int textColor, int dimColor, int caretColor) {
        textPaint.setColor(textColor);
        dimPaint.setColor(dimColor);
        caretPaint.setColor(caretColor);
        invalidate();
    }

    /**
     * @param modeTag short vim-style mode name shown before the counter, empty while typing.
     * @param hint    shown in place of an empty query.
     */
    public void bind(@NonNull String query, @NonNull String counter, @NonNull String modeTag,
                     @NonNull String hint, boolean caretVisible) {
        this.query = query;
        this.counter = counter;
        this.modeTag = modeTag;
        this.hint = hint;
        this.caretVisible = caretVisible;
        setContentDescription(PROMPT + query + (counter.isEmpty() ? "" : " " + counter));
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        int height = Math.round(metrics.descent - metrics.ascent + 2f * VERTICAL_PADDING_DP * density);
        setMeasuredDimension(resolveSize(Math.round(240 * density), widthMeasureSpec),
            resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = (getHeight() - (metrics.descent + metrics.ascent)) / 2f;
        float padding = HORIZONTAL_PADDING_DP * density;

        // Trailing edge first: the counter is fixed furniture, and the query is what gets clipped
        // when it runs long, never the count of what it found.
        float right = getWidth() - padding;
        if (!counter.isEmpty()) {
            float width = textPaint.measureText(counter);
            canvas.drawText(counter, right - width, baseline, textPaint);
            right -= width + GAP_DP * density;
        }
        if (!modeTag.isEmpty()) {
            float width = dimPaint.measureText(modeTag);
            canvas.drawText(modeTag, right - width, baseline, dimPaint);
            right -= width + GAP_DP * density;
        }

        float x = padding;
        canvas.drawText(PROMPT, x, baseline, dimPaint);
        x += textPaint.measureText(PROMPT) + 2f * density;
        float available = Math.max(0f, right - x);
        if (query.isEmpty() && !hint.isEmpty()) {
            canvas.drawText(ellipsized(hint, dimPaint, available), x, baseline, dimPaint);
            return;
        }
        String shown = tail(query, available);
        canvas.drawText(shown, x, baseline, textPaint);
        if (caretVisible) {
            float caretX = x + textPaint.measureText(shown) + 1f * density;
            float caretWidth = Math.max(1.5f * density, textPaint.measureText("m") * 0.5f);
            canvas.drawRect(caretX, baseline + metrics.ascent, caretX + caretWidth,
                baseline + metrics.descent, caretPaint);
        }
    }

    /** Keeps the end of a long query visible: that is where the caret and the typing are. */
    @NonNull
    private String tail(@NonNull String value, float available) {
        if (available <= 0f) return "";
        int start = 0;
        while (start < value.length()
            && textPaint.measureText(value, start, value.length()) > available) {
            start++;
        }
        return value.substring(start);
    }

    @NonNull
    private static String ellipsized(@NonNull String value, @NonNull Paint paint, float available) {
        if (paint.measureText(value) <= available) return value;
        int end = value.length();
        while (end > 0 && paint.measureText(value, 0, end) + paint.measureText("…") > available) {
            end--;
        }
        return value.substring(0, end) + "…";
    }
}
