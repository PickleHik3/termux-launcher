package com.termux.app.chrome;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A plain {@link RelativeLayout} that Live blur can record beneath a glass through: the root's
 * content layout and the dock's stack are on the dock's path to the root, and only a container can
 * draw its own child ({@link LiveBackdrop.Container}). Lays out and draws exactly as its parent class.
 */
public class LiveBackdropRelativeLayout extends RelativeLayout implements LiveBackdrop.Container {

    public LiveBackdropRelativeLayout(@NonNull Context context) {
        super(context);
    }

    public LiveBackdropRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveBackdropRelativeLayout(@NonNull Context context, @Nullable AttributeSet attrs,
                                      int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void drawChildForBackdrop(@NonNull Canvas canvas, @NonNull View child) {
        drawChild(canvas, child, getDrawingTime());
    }
}
