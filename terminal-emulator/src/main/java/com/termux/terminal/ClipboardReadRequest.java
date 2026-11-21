package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * A clipboard read the program asked for on the Fast engine ({@code OSC 52 ; c ; ?} or an
 * {@code OSC 5522} read), waiting for the app to decide. The first {@link #allow} or {@link #deny}
 * writes the reply the library itself would have written; later calls are ignored. It may be
 * answered at any time, from the main thread, after the core has moved on: it never touches the core.
 */
public final class ClipboardReadRequest {

    private final TerminalOutput mOutput;
    private final String mRaw;
    private final boolean mSt;
    private final boolean mListingOnly;
    private final boolean mPasted;
    private boolean mAnswered;

    ClipboardReadRequest(@NonNull TerminalOutput output, @NonNull String raw, boolean st, boolean listingOnly, boolean pasted) {
        mOutput = output;
        mRaw = raw;
        mSt = st;
        mListingOnly = listingOnly;
        mPasted = pasted;
    }

    /** The whole request string after {@code ESC ]}, such as {@code 52;c;?}. */
    @NonNull
    public String getRaw() {
        return mRaw;
    }

    /** Whether this is an {@code OSC 5522} request. */
    public boolean isKittyProtocol() {
        return mRaw.startsWith("5522;");
    }

    /** An {@code OSC 5522} read that only asks which types are available. */
    public boolean isListingOnly() {
        return mListingOnly;
    }

    /** An {@code OSC 5522} read that redeemed the password of a paste event the user just made. */
    public boolean isPasted() {
        return mPasted;
    }

    public synchronized boolean isAnswered() {
        return mAnswered;
    }

    /** Answer with the clipboard's text, or null for an empty clipboard. */
    public void allow(@Nullable String text) {
        answer(true, text == null ? null : text.getBytes(StandardCharsets.UTF_8));
    }

    /** Refuse: the program gets the library's denial (an empty clipboard, or EPERM for 5522). */
    public void deny() {
        answer(false, null);
    }

    private void answer(boolean allowed, @Nullable byte[] text) {
        synchronized (this) {
            if (mAnswered) return;
            mAnswered = true;
        }
        byte[] reply = GhosttyNative.nativeEncodeClipboardReply(mRaw.getBytes(StandardCharsets.UTF_8), mSt, allowed, text);
        if (reply != null && reply.length > 0) mOutput.write(reply, 0, reply.length);
    }
}
