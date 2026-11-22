package com.termux.app.window;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.IntFunction;

/**
 * The activity's content view, the last place a layer's host falls back to when the window root is
 * missing. Reading it never adds anything to it.
 */
final class ContentFrame {

    private ContentFrame() {}

    @Nullable
    static ViewGroup of(@NonNull IntFunction<View> finder) {
        View content = finder.apply(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }
}
