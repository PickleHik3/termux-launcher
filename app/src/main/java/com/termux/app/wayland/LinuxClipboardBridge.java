package com.termux.app.wayland;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;

import com.termux.R;

/**
 * Plain-text copy and paste between Android and the Linux windows. Android lets only the app in
 * front read its clipboard, so the text goes to the host only while the Linux windows screen
 * has focus: when it gains focus, when the host comes up, and whenever the clipboard changes
 * meanwhile. Text a Linux app copies is written to Android's clipboard at any time.
 */
final class LinuxClipboardBridge implements ClipboardManager.OnPrimaryClipChangedListener {

    /** Takes the text to offer to Linux apps. */
    interface Sink {
        void send(String text);
    }

    private final Context mContext;
    private final Sink mSink;
    private final LinuxClipboardText mCrossed = new LinuxClipboardText();
    private boolean mInFront;

    LinuxClipboardBridge(Context context, Sink sink) {
        mContext = context;
        mSink = sink;
    }

    private ClipboardManager manager() {
        return (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** The screen gained or lost focus. */
    void setInFront(boolean inFront) {
        if (inFront == mInFront) return;
        ClipboardManager cm = manager();
        if (cm == null) return;
        mInFront = inFront;
        if (inFront) {
            cm.addPrimaryClipChangedListener(this);
            pushCurrent();
        } else {
            cm.removePrimaryClipChangedListener(this);
        }
    }

    @Override
    public void onPrimaryClipChanged() {
        pushCurrent();
    }

    /** Sends Android's clipboard text to the host, when this screen is in front and it is new. */
    void pushCurrent() {
        if (!mInFront) return;
        String text = readText();
        if (LinuxClipboardText.acceptable(text) && mCrossed.crossing(text)) mSink.send(text);
    }

    /** A Linux app copied this text. */
    void fromLinux(String text) {
        if (!LinuxClipboardText.acceptable(text) || !mCrossed.crossing(text)) return;
        ClipboardManager cm = manager();
        if (cm == null) return;
        try {
            cm.setPrimaryClip(ClipData.newPlainText(mContext.getString(R.string.linux_clipboard_label), text));
        } catch (RuntimeException ignored) {
            // The clipboard refused (a text too big for it, or the service is gone): nothing to do.
        }
    }

    private String readText() {
        ClipboardManager cm = manager();
        if (cm == null) return null;
        try {
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            ClipDescription description = clip.getDescription();
            if (description == null || !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null;
            CharSequence text = clip.getItemAt(0).getText();
            return text == null ? null : text.toString();
        } catch (RuntimeException e) {
            // Not allowed to read right now (no focus): the next focus gain reads it.
            return null;
        }
    }
}
