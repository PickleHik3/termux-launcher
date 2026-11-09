package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A plain {@link FrameLayout} that Live blur can record beneath a glass through: a floating
 * window's rounded content wrapper and the stock frames on its way to the root are on the float
 * slab's path, and only a container can draw its own child ({@link LiveBackdrop.Container}). Lays
 * out and draws exactly as its parent class.
 */
public class LiveBackdropFrameLayout extends FrameLayout implements LiveBackdrop.Container {

    public LiveBackdropFrameLayout(@NonNull Context context) {
        super(context);
    }

    public LiveBackdropFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveBackdropFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                   int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void drawChildForBackdrop(@NonNull Canvas canvas, @NonNull View child) {
        drawChild(canvas, child, getDrawingTime());
    }
}
