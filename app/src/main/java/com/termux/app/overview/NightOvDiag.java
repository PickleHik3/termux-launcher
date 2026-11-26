package com.termux.app.overview;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** DIAG (revert): the night-flip Overview trail, tag NIGHTOV. */
public final class NightOvDiag {
    public static final String TAG = "NIGHTOV";
    private static final int FRAMES = 6;

    private NightOvDiag() { }

    public static void log(@NonNull String where, @Nullable String state) {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        for (int i = 1; i < st.length && i <= FRAMES; i++) {
            frames.append(" <").append(st[i].getClassName().replace("com.termux.app.", ""))
                .append('.').append(st[i].getMethodName()).append(':')
                .append(st[i].getLineNumber());
        }
        Log.d(TAG, "t=" + SystemClock.uptimeMillis() + " " + where + " " + state + " |" + frames);
    }

    /** A view and its parents up to the window: what could keep it from drawing. */
    @NonNull
    public static String describe(@Nullable View view) {
        if (view == null) return "view=null";
        StringBuilder out = new StringBuilder();
        out.append("attached=").append(view.isAttachedToWindow())
            .append(" shown=").append(view.isShown())
            .append(" focus=").append(view.hasFocus())
            .append(" focused=").append(view.findFocus() == null ? "none"
                : view.findFocus().getClass().getSimpleName())
            .append(" size=").append(view.getWidth()).append('x').append(view.getHeight())
            .append(" tx=").append(view.getTranslationX());
        View v = view;
        int depth = 0;
        while (v != null && depth < 12) {
            out.append(" [").append(v.getClass().getSimpleName())
                .append(" id=").append(v.getId())
                .append(" vis=").append(v.getVisibility())
                .append(" a=").append(v.getAlpha());
            ViewParent p = v.getParent();
            if (p instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) p;
                out.append(" idx=").append(g.indexOfChild(v)).append('/').append(g.getChildCount());
                v = g;
            } else {
                v = null;
            }
            out.append(']');
            depth++;
        }
        return out.toString();
    }
}
