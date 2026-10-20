package com.termux.app.dock;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.theme.SchemeTone;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

/**
 * The Prompt line's field (spec "Prompt line"): the command palette's search, standing in Home's
 * dock as a shell prompt, {@code ~ ❯ Search apps, commands, settings}, on a darker well.
 *
 * <p>It holds no text of its own and takes no focus: it is a door. A tap opens the palette and
 * the keyboard with the caret in the palette's own query line
 * ({@code TerminalCommandPaletteController.openFromField}), and the palette does the rest, Enter
 * running its top result. The prompt's colours come from the terminal's scheme
 * ({@link PromptLineInk}); call {@link #refreshTheme} when the scheme changes. The well is the
 * field's only fill: the glass behind it is the dock's.</p>
 */
public final class PromptLineFieldView extends View {

    /** The well's height; the view stands {@link #HIT_HEIGHT_DP} tall so the tap target is full. */
    public static final float WELL_HEIGHT_DP = 42f;
    /** The least height a tap target has (R2). */
    public static final float HIT_HEIGHT_DP = 48f;
    /** The well's corner radius until the dock nests it ({@link #setWellRadiusPx}). */
    static final float DEFAULT_RADIUS_DP = 14f;
    /** Room between the well's edge and the prompt, and between the prompt's segments. */
    static final float PAD_DP = 12f;
    static final float SEGMENT_GAP_DP = 8f;
    static final float TEXT_SP = 15f;
    /** The text never grows past this share of the well, whatever the font scale says. */
    static final float MAX_TEXT_SHARE = 0.5f;
    /** How much lighter the well reads under a finger, in HCT tone. */
    static final double PRESSED_TONE_LIFT = 6d;

    static final String DIRECTORY = "~";
    static final String GLYPH = "❯";

    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mWellRect = new RectF();
    private final float mDensity;
    @NonNull private final String mHint;
    @NonNull private CharSequence mFittedHint = "";
    @NonNull private PromptLineInk mInk;
    private int mPressedWell;
    private float mRadiusPx;
    @Nullable private Runnable mOnOpen;

    public PromptLineFieldView(@NonNull Context context) {
        this(context, null);
    }

    public PromptLineFieldView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mHint = context.getString(R.string.dock_prompt_line_hint);
        mRadiusPx = DEFAULT_RADIUS_DP * mDensity;
        mText.setTypeface(Typeface.MONOSPACE);
        mInk = resolveInk();
        mPressedWell = SchemeTone.toneShift(mInk.well, PRESSED_TONE_LIFT);
        setClickable(true);
        setFocusable(true);
        setContentDescription(mHint);
    }

    /**
     * Replaces what a tap does; null puts back the default, the Home palette with its keyboard.
     * For hosts that are not the launcher's activity, and for tests.
     */
    public void setOnOpenListener(@Nullable Runnable onOpen) {
        mOnOpen = onOpen;
    }

    /** The well's corner radius: the dock's radius less its padding, so it nests in the slab. */
    public void setWellRadiusPx(float radiusPx) {
        float radius = Math.max(0f, radiusPx);
        if (radius == mRadiusPx) return;
        mRadiusPx = radius;
        invalidate();
    }

    /**
     * The terminal's scheme may have changed: the prompt takes its colours again, and redraws
     * only when they moved, so a caller may ask on every scheme pass.
     */
    public void refreshTheme() {
        PromptLineInk ink = resolveInk();
        if (ink.sameAs(mInk)) return;
        mInk = ink;
        mPressedWell = SchemeTone.toneShift(mInk.well, PRESSED_TONE_LIFT);
        invalidate();
    }

    /** The prompt's colours from the terminal's own scheme, the one the panes draw with. */
    @NonNull
    private static PromptLineInk resolveInk() {
        int[] colors = TerminalColors.COLOR_SCHEME.mDefaultColors;
        return PromptLineInk.resolve(colors[TextStyle.COLOR_INDEX_BACKGROUND],
            colors[TextStyle.COLOR_INDEX_FOREGROUND], colors[PromptLineInk.ANSI_BLUE],
            colors[PromptLineInk.ANSI_GREEN]);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // The scheme may have changed while the dock wore another style.
        refreshTheme();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = resolveSize(Math.round(HIT_HEIGHT_DP * mDensity), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float wellHeight = Math.min(h, WELL_HEIGHT_DP * mDensity);
        float top = (h - wellHeight) / 2f;
        mWellRect.set(0f, top, w, top + wellHeight);
        float textPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP,
            getResources().getDisplayMetrics());
        // A raised font scale is what clips fixed chrome: the prompt shrinks to its well instead.
        mText.setTextSize(Math.min(textPx, wellHeight * MAX_TEXT_SHARE));
        float pad = PAD_DP * mDensity;
        float gap = SEGMENT_GAP_DP * mDensity;
        float hintRoom = w - 2f * pad - mText.measureText(DIRECTORY) - mText.measureText(GLYPH)
            - 2f * gap;
        mFittedHint = hintRoom <= 0f ? ""
            : TextUtils.ellipsize(mHint, mText, hintRoom, TextUtils.TruncateAt.END);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int well = isPressed() ? mPressedWell : mInk.well;
        mWell.setColor(SchemeTone.withAlpha(well, PromptLineInk.WELL_ALPHA));
        float radius = Math.min(mRadiusPx, mWellRect.height() / 2f);
        canvas.drawRoundRect(mWellRect, radius, radius, mWell);

        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float pad = PAD_DP * mDensity;
        float gap = SEGMENT_GAP_DP * mDensity;
        Paint.FontMetrics metrics = mText.getFontMetrics();
        float baseline = mWellRect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        // The prompt reads from the start edge, as a shell's does.
        float x = rtl ? getWidth() - pad : pad;
        x = drawSegment(canvas, DIRECTORY, mInk.directory, x, baseline, gap, rtl);
        x = drawSegment(canvas, GLYPH, mInk.glyph, x, baseline, gap, rtl);
        drawSegment(canvas, mFittedHint, mInk.hint, x, baseline, 0f, rtl);
    }

    /** Draws one segment at {@code x} toward the end edge; answers where the next one starts. */
    private float drawSegment(@NonNull Canvas canvas, @NonNull CharSequence text, int color,
                              float x, float baseline, float gap, boolean rtl) {
        if (text.length() == 0) return x;
        mText.setColor(color);
        float width = mText.measureText(text, 0, text.length());
        float left = rtl ? x - width : x;
        canvas.drawText(text, 0, text.length(), left, baseline, mText);
        return rtl ? left - gap : x + width + gap;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        if (mOnOpen != null) {
            mOnOpen.run();
            return true;
        }
        TermuxActivity activity = activityOf(getContext());
        if (activity == null) return handled;
        activity.getCommandPaletteController().openFromField(null, true);
        return true;
    }

    @Nullable
    private static TermuxActivity activityOf(@Nullable Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof TermuxActivity) return (TermuxActivity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
    }
}
