package com.termux.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.termux.terminal.TerminalLinks;
import com.termux.terminal.TerminalSession;

/**
 * The interface for communication between {@link TerminalView} and its client. It allows for getting
 * various  configuration options from the client and for sending back data to the client like logs,
 * key events, both hardware and IME (which makes it different from that available with
 * {@link View#setOnKeyListener(View.OnKeyListener)}, etc. It must be set for the
 * {@link TerminalView} through {@link TerminalView#setTerminalViewClient(TerminalViewClient)}.
 */
public interface TerminalViewClient {

    /**
     * Callback function on scale events according to {@link ScaleGestureDetector#getScaleFactor()}.
     */
    float onScale(float scale);

    /**
     * On a single tap on the terminal if terminal mouse reporting not enabled.
     */
    void onSingleTapUp(MotionEvent e);

    /**
     * A finger tap that went to a mouse-tracking program as a click. {@link #onSingleTapUp} is
     * never called for it, so this is where a tap still gets to raise an on-screen keyboard.
     */
    default void onMouseTrackingTap(MotionEvent e) {}

    /**
     * A tap on a cell that carries a link: an OSC 8 hyperlink, or an address in the text when the
     * view detects them. {@link #onSingleTapUp} is not called for it. With a mouse-tracking
     * program the tap has also gone to the program as its click, unless Shift held it back.
     */
    default void onLinkTap(TerminalLinks.Link link, MotionEvent e) {}

    boolean shouldBackButtonBeMappedToEscape();

    boolean shouldEnforceCharBasedInput();

    boolean shouldUseCtrlSpaceWorkaround();

    /** Whether hardware Ctrl+Space is left to Android instead of being sent to the terminal. */
    default boolean shouldPassCtrlSpaceToAndroid() {
        return false;
    }

    boolean isTerminalViewSelected();

    void copyModeChanged(boolean copyMode);

    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

    boolean onKeyUp(int keyCode, KeyEvent e);

    boolean onLongPress(MotionEvent event);

    /**
     * Raise a transient notice to the user. The view has no opinion on how it is drawn; the client
     * owns that surface, so accessibility actions taken here surface the same way as every other
     * notice in the app rather than as a stock toast.
     */
    default void onShowNotice(CharSequence text) {
        // No-op by default: a host that has no notice surface simply says nothing.
    }

    default boolean onShowContextMenu(TerminalView view) {
        return false;
    }

    /**
     * The user asked to paste into {@code view} — a middle click, the accessibility action. The
     * view does not read the clipboard itself: what a paste is (text, a picture, a program's paste
     * event) is the client's to decide, the same as for every other paste. A client that does not
     * decide gets the clipboard's text pasted, as before.
     */
    default void onPasteRequest(TerminalView view) {
        if (view.mEmulator == null) return;
        ClipboardManager manager = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = manager == null ? null : manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(view.getContext());
        if (text != null && text.length() > 0) view.mEmulator.paste(text.toString());
    }

    /**
     * A keyboard inserted content into {@code view}: a picture from its clipboard strip, a GIF.
     * {@code mime} is the first {@code image/*} type it offered. Return true when the client takes
     * it, and call {@code release} once it has read {@code uri}, whatever happened; on false the
     * view releases it and the keyboard falls back to whatever it does for editors without
     * pictures.
     */
    default boolean onCommitContent(TerminalView view, Uri uri, String mime, Runnable release) {
        return false;
    }

    /**
     * Whether the selection toolbar offers "Read aloud". The view knows nothing about speech; the
     * host says yes when it has a voice to read with.
     */
    default boolean isReadAloudAvailable() {
        return false;
    }

    /** Whether the host is reading aloud now, so the toolbar offers "Stop reading" in its place. */
    default boolean isReadingAloud() {
        return false;
    }

    /** "Read aloud" was tapped on a selection: read {@code text}, or stop if already reading. */
    default void onReadAloud(String text) {
    }

    boolean readControlKey();

    boolean readAltKey();

    boolean readShiftKey();

    boolean readFnKey();

    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

    void onEmulatorSet();

    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);
}
