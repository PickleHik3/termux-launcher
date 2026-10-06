package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * How many edits a cleanup made, for the header's "· 5 edits". An edit is one contiguous run of
 * changed, added or removed words: "with" struck out and "do," added next to it is one edit, a
 * word taken out three words later is another.
 */
final class VoiceEditCount {

    private VoiceEditCount() {
    }

    /** The runs of non-{@link VoiceWordDiff.Kind#SAME} words in {@code ops}; 0 when nothing changed. */
    static int count(@NonNull List<VoiceWordDiff.Op> ops) {
        int edits = 0;
        boolean inRun = false;
        for (VoiceWordDiff.Op op : ops) {
            boolean changed = op.kind != VoiceWordDiff.Kind.SAME;
            if (changed && !inRun) edits++;
            inRun = changed;
        }
        return edits;
    }
}
