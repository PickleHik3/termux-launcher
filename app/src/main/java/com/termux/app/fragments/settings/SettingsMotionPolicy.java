package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which motion a move between settings screens gets (F2), so the motion says where you went:
 * deeper is a shared-axis slide, a row opening its editor grows out of the row, a jump across the
 * tree fades through. Pure: the host applies the answer ({@link SettingsTransitions}).
 */
public final class SettingsMotionPolicy {

    private SettingsMotionPolicy() {}

    public enum Kind {
        /** An instant cut. */
        NONE,
        /** Shared axis X: forward slides in from the end, Back reverses it. */
        SHARED_AXIS,
        /** Container transform from the row that opened it. */
        CONTAINER,
        /** Fade through: out, then in with a slight scale; never pretends to be deeper. */
        FADE_THROUGH
    }

    public enum Move {
        /** A row or a button on the screen opens the next screen. */
        PUSH,
        /** A search result, a deep link into an open Settings, a section picked in the two-pane list. */
        JUMP,
        /** Rebuilding a saved stack or the first screen: nothing to animate from. */
        RESTORE
    }

    /**
     * @param from the screen on show, or null for none
     * @param to the screen being opened
     * @param fromRow whether the row that opened it is on screen to grow from
     * @param instant reduce motion, or animation speed Off
     */
    @NonNull
    public static Kind choose(@NonNull Move move, @Nullable String from, @NonNull String to,
                              boolean fromRow, boolean instant) {
        if (instant || move == Move.RESTORE) return Kind.NONE;
        if (move == Move.JUMP) return Kind.FADE_THROUGH;
        SettingsTree.Screen target = SettingsTree.find(to);
        if (target != null && target.editor && fromRow) return Kind.CONTAINER;
        SettingsTree.Section fromSection = SettingsTree.sectionOf(from);
        // A button on one section's page that opens another section's page (On-device AI's voice
        // row opens Keyboard › Voice input) crosses the tree rather than going deeper.
        if (fromSection != null && target != null && target.section != fromSection) return Kind.FADE_THROUGH;
        return Kind.SHARED_AXIS;
    }
}
