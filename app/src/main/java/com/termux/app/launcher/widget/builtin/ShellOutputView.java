package com.termux.app.launcher.widget.builtin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

/**
 * The Command widget's output block: pre-formatted mono text that neither wraps nor scrolls,
 * clipped to the whole lines that fit. It takes no touches, so a tap reaches the card and a
 * horizontal swipe reaches the page. Lines are kept unwrapped by laying the text out wide; there
 * is no movement method, so nothing can scroll it.
 */
@SuppressLint("AppCompatCustomView")
final class ShellOutputView extends android.widget.TextView {
    ShellOutputView(@NonNull Context context) {
        super(context);
        setSingleLine(false);
        setHorizontallyScrolling(true);
        setEllipsize(null);
        setIncludeFontPadding(false);
        setClickable(false);
        setLongClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        int lineHeight = getLineHeight();
        int top = getTotalPaddingTop();
        int room = getHeight() - top - getTotalPaddingBottom();
        if (lineHeight <= 0 || room <= 0) {
            super.onDraw(canvas);
            return;
        }
        int lines = Math.max(1, room / lineHeight);
        canvas.save();
        canvas.clipRect(getScrollX(), getScrollY(), getScrollX() + getWidth(),
            getScrollY() + top + lines * lineHeight);
        super.onDraw(canvas);
        canvas.restore();
    }
}
