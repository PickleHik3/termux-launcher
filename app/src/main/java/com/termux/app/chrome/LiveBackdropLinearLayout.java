package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A plain {@link LinearLayout} that Live blur can record beneath a glass through: the content
 * column and the canvas band are on a floating window's path to the root, and only a container
 * can draw its own child ({@link LiveBackdrop.Container}). Lays out and draws exactly as its
 * parent class.
 */
public class LiveBackdropLinearLayout extends LinearLayout implements LiveBackdrop.Container {

    public LiveBackdropLinearLayout(@NonNull Context context) {
        super(context);
    }

    public LiveBackdropLinearLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveBackdropLinearLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                    int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void drawChildForBackdrop(@NonNull Canvas canvas, @NonNull View child) {
        drawChild(canvas, child, getDrawingTime());
    }
}
