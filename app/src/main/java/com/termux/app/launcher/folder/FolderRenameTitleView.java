package com.termux.app.launcher.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

import com.termux.R;
import com.termux.app.chrome.GlassTokens;

/**
 * Drawn title/caret only: never an editor, focus owner or InputConnection provider.
 *
 * <p>A name wider than the view ends in an ellipsis at rest; while it is being edited the text
 * slides left instead, so the caret stays on screen however long the name or large the font.
 */
public final class FolderRenameTitleView extends View {
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private FolderRenameModel model;
    private boolean editing;

    public FolderRenameTitleView(@NonNull Context context) {
        super(context);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
            getResources().getDisplayMetrics()));
        // Popups draw on a dark glass; the Paint default of black would vanish into it.
        paint.setColor(com.google.android.material.color.MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, GlassTokens.HIGHLIGHT));
        setClickable(true);
        setFocusable(false);
        setMinimumHeight(Math.round(48f * getResources().getDisplayMetrics().density));
        ViewCompat.replaceAccessibilityAction(this, AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.folder_popup_rename_action), null);
    }

    public void setTextColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    int currentTextColor() {
        return paint.getColor();
    }

    public void bind(@NonNull FolderRenameModel model, boolean editing) {
        this.model = model;
        this.editing = editing;
        setContentDescription(model.text());
        invalidate();
    }

    @Override public boolean onCheckIsTextEditor() { return false; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Tall enough for the line at any font scale, never shorter than the touch minimum.
        int line = (int) Math.ceil(paint.descent() - paint.ascent());
        int wanted = Math.max(getSuggestedMinimumHeight(),
            line + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
            resolveSize(wanted, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null) return;
        float left = getPaddingLeft();
        float viewport = Math.max(0f, getWidth() - left - getPaddingRight());
        float baseline = (getHeight() - (paint.descent() + paint.ascent())) / 2f;
        String text = model.text();
        if (!editing) {
            canvas.drawText(TextUtils.ellipsize(text, paint, viewport, TextUtils.TruncateAt.END)
                .toString(), left, baseline, paint);
            return;
        }
        float caretWidth = Math.max(1f, getResources().getDisplayMetrics().density);
        int utf16 = text.offsetByCodePoints(0, model.caret());
        float caretX = paint.measureText(text, 0, utf16);
        float shift = scrollFor(caretX, caretWidth, viewport);
        canvas.save();
        canvas.clipRect(left, 0, left + viewport, getHeight());
        canvas.drawText(text, left - shift, baseline, paint);
        float x = left - shift + caretX;
        canvas.drawRect(x, baseline + paint.ascent(), x + caretWidth, baseline + paint.descent(),
            paint);
        canvas.restore();
    }

    /** How far the text slides left so a caret at {@code caretX} stays inside the viewport. */
    @VisibleForTesting
    static float scrollFor(float caretX, float caretWidth, float viewport) {
        return Math.max(0f, caretX + caretWidth - viewport);
    }
}
