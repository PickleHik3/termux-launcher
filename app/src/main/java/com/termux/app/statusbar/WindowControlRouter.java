package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarkControls;
import com.termux.app.wall.pageswitch.PageBarModel;

/**
 * Where a window's controls go once a finger lifts on one: the mark's entry names the window (a
 * row place for a Terminal or a Linux window, a page id for a Display page) and the control says
 * what happens to it. The activity does the work through {@link Actions}; this keeps the rules.
 */
public final class WindowControlRouter implements MarksLineView.WindowControls {

    /** What the controls do, by window. */
    public interface Actions {
        /**
         * Closes the row's window at {@code place}: a Terminal window at once when it is idle,
         * asking first while a pane runs something ({@link #asksBeforeClose}); a Linux window
         * closes its app.
         */
        void closeRowWindow(int place);

        /** Closes the Display page {@code pageId}, asking first while its desktop runs. */
        void closeDisplay(int pageId);

        /** Takes the row's window at {@code place} out of the row; it waits in Overview. */
        void minimiseRowWindow(int place);

        /** Goes to {@code target} and fills the whole screen with it; again restores. */
        void toggleMaximise(@NonNull PageBarModel.Target target);

        /**
         * Lifts the Linux window at {@code place} into the finger still down at ({@code rawX},
         * {@code rawY}) on screen, pointer {@code pointerId}, to carry it onto Home as a Float.
         */
        void moveLinuxWindow(int place, float rawX, float rawY, int pointerId);

        /**
         * Brings the Linux window at {@code place} in front and puts out the Scale rail for it,
         * the one the rim tab's Scale opened.
         */
        void scaleLinuxWindow(int place);

        /** Whether the Linux window at {@code place} takes a scale of its own (an X window does not). */
        boolean linuxWindowScales(int place);
    }

    @NonNull private final Actions mActions;

    public WindowControlRouter(@NonNull Actions actions) {
        mActions = actions;
    }

    @Override
    public boolean scales(@NonNull PageBarModel.Entry entry) {
        return entry.kind != PageKind.LINUX || entry.target.index < 0
            || mActions.linuxWindowScales(entry.target.index);
    }

    @Override
    public void onWindowControl(@NonNull PageBarModel.Entry entry,
                                @NonNull MarkControls.Control control, float rawX, float rawY,
                                int pointerId) {
        act(entry.kind, entry.target, control, rawX, rawY, pointerId);
    }

    /**
     * {@code control} on the window of {@code kind} at {@code target} (a row place in
     * {@link PageBarModel.Target#index} for a Terminal or Linux window, a page id for a Display
     * page); the finger for Move. The Glyphs look's chips come here with their row place.
     */
    public void act(@NonNull PageKind kind, @NonNull PageBarModel.Target target,
                    @NonNull MarkControls.Control control, float rawX, float rawY,
                    int pointerId) {
        PageBarModel.Target t = target;
        int place = t.index;
        boolean rowWindow = (kind == PageKind.TERMINAL || kind == PageKind.LINUX) && place >= 0;
        boolean display = kind == PageKind.DISPLAY;
        if (!rowWindow && !display) return;
        switch (control) {
            case CLOSE:
                if (display) mActions.closeDisplay(t.pageId);
                else mActions.closeRowWindow(place);
                break;
            case MINIMISE:
                if (rowWindow) mActions.minimiseRowWindow(place);
                break;
            case MAXIMISE:
                mActions.toggleMaximise(t);
                break;
            case MOVE:
                if (kind == PageKind.LINUX && rowWindow) {
                    mActions.moveLinuxWindow(place, rawX, rawY, pointerId);
                }
                break;
            case SCALE:
                if (kind == PageKind.LINUX && rowWindow) mActions.scaleLinuxWindow(place);
                break;
            default:
                break;
        }
    }

    /**
     * Whether closing a Terminal window asks first: some pane runs something other than its shell.
     * Per pane, whether it is idle at its shell prompt; null is unknown, read as idle, so a window
     * whose processes cannot be read closes at once.
     */
    public static boolean asksBeforeClose(@Nullable Boolean[] idlePerPane) {
        if (idlePerPane == null) return false;
        for (Boolean idle : idlePerPane) {
            if (idle != null && !idle) return true;
        }
        return false;
    }
}
