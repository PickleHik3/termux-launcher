package com.termux.app.wall.pageswitch;

import androidx.annotation.NonNull;

/**
 * Turns the target of a chosen cell into one call on the host. The mapping lives here so it is
 * tested once; the activity only implements the calls.
 */
public final class PageSwitchTargets {

    /** What the host can do for a chosen cell. */
    public interface Navigator {
        void goToPage(int pageId);
        void showTerminalWindow(int index);
        void goToTerminal();
        /** Opens Overview: the marks row's leading Overview cell. */
        void openOverview();

        /** Brings minimised window {@code index} back into the row at its old place, and goes to it. */
        default void restoreMinimised(int index) {
        }
    }

    private PageSwitchTargets() {
    }

    public static void carryOut(@NonNull PageBarModel.Target target, @NonNull Navigator nav) {
        switch (target.action) {
            case GO_PAGE:
                nav.goToPage(target.pageId);
                break;
            case GO_TERMINAL:
                if (target.index >= 0) nav.showTerminalWindow(target.index);
                else nav.goToTerminal();
                break;
            case RESTORE:
                if (target.index >= 0) nav.restoreMinimised(target.index);
                break;
            default:
                break;
        }
    }
}
