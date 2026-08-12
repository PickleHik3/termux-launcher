package com.termux.app.launcher.folder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import androidx.annotation.NonNull;

/** Drawn title/caret only: never an editor, focus owner or InputConnection provider. */
public final class FolderRenameTitleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FolderRenameModel model;
    private boolean editing;

    public FolderRenameTitleView(@NonNull Context context) {
        super(context);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        // Popups draw on a dark glass; the Paint default of black would vanish into it.
        paint.setColor(0xFFFFFFFF);
        setClickable(true);
        setFocusable(false);
        setMinimumHeight(Math.round(40f * getResources().getDisplayMetrics().density));
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

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model == null) return;
        float baseline = (getHeight() - (paint.descent() + paint.ascent())) / 2f;
        canvas.drawText(model.text(), getPaddingLeft(), baseline, paint);
        if (editing) {
            int utf16 = model.text().offsetByCodePoints(0, model.caret());
            float x = getPaddingLeft() + paint.measureText(model.text(), 0, utf16);
            canvas.drawRect(x, baseline + paint.ascent(), x + Math.max(1f,
                getResources().getDisplayMetrics().density), baseline + paint.descent(), paint);
        }
    }
}
