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

    /** Reads through {@link ShareUtils}, the same path the terminal's own paste already uses. */
    @NonNull
    static ClipboardText forContext(@NonNull Context context) {
        return () -> ShareUtils.getTextStringFromClipboardIfSet(context, true);
    }
}
