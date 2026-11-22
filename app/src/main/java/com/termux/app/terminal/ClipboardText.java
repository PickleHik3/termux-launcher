package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.interact.ShareUtils;

/**
 * The one place a launcher text intake reads the Android clipboard, so the palette, the drawer
 * search, folder rename, find and inline rename do not each grab their own
 * {@code ClipboardManager}.
 *
 * <p>A lambda built with {@link #forContext} only captures the {@link Context} reference — it
 * never touches the clipboard until {@link #read()} is actually called — so it is safe to build
 * at field-initializer time, before an activity is attached.
 */
public interface ClipboardText {

    /** @return the clipboard's primary text, or null when there is none. */
    @Nullable
    String read();

    /**
     * A copy or cut made in a launcher text field. A source that only reads (a test's lambda)
     * keeps nothing.
     */
    default void write(@NonNull String text) {}

    /**
     * Reads through {@link ShareUtils}, the same path the terminal's own paste already uses, and
     * writes through {@link ClipboardHistory#copy}, as the terminal's own copy key does.
     */
    @NonNull
    static ClipboardText forContext(@NonNull Context context) {
        return new ClipboardText() {
            @Nullable
            @Override
            public String read() {
                return ShareUtils.getTextStringFromClipboardIfSet(context, true);
            }

            @Override
            public void write(@NonNull String text) {
                ClipboardHistory.copy(context, text);
            }
        };
    }
}
