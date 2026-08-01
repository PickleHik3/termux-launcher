package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** Small wallpaper-derived Material color dot separating compact status values. */
public final class MaterialDotSeparatorView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private StatusBarWidgetView.ColorRole mRole = StatusBarWidgetView.ColorRole.SECONDARY;

    public MaterialDotSeparatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        applyColor();
    }

    public void setColorRole(@NonNull StatusBarWidgetView.ColorRole role) {
        if (mRole == role) return;
        mRole = role;
        applyColor();
        invalidate();
    }

    private void applyColor() {
        Context context = getContext();
        int primary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        int tertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        mPaint.setColor(mRole == StatusBarWidgetView.ColorRole.TERTIARY ? tertiary
            : mRole == StatusBarWidgetView.ColorRole.PRIMARY ? primary : secondary);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
            Math.min(getWidth(), getHeight()) / 2f, mPaint);
    }
}
