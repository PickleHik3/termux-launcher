package com.termux.app.wall.switcher;

import androidx.annotation.NonNull;

/**
 * Turns the target of a chosen picker cell into one call on the host. The mapping lives here so
 * it is tested once; the activity only implements the calls.
 */
public final class PickerTargets {

    /** What the host can do for a chosen cell. */
    public interface Navigator {
        void goToPage(int pageId);
        void goHome();
        void showTerminalWindow(int index);
        void goToTerminal();
        void showDisplayApp(int pageId, int windowId);
        void startDisplay();
        void addAssistantPage();
        void addDisplayPage();
        void openSessionBrowser();
        void openDisplayAppList();
        void openPageOverview();
    }

    private PickerTargets() {
    }

    public static void carryOut(@NonNull PickerModel.Target target, @NonNull Navigator nav) {
        switch (target.action) {
            case CLOSE:
                break;
            case GO_PAGE:
                nav.goToPage(target.pageId);
                break;
            case GO_HOME:
                nav.goHome();
                break;
            case GO_TERMINAL:
                if (target.index >= 0) nav.showTerminalWindow(target.index);
                else nav.goToTerminal();
                break;
            case GO_DISPLAY_APP:
                nav.showDisplayApp(target.pageId, target.index);
                break;
            case START_DISPLAY:
                nav.startDisplay();
                break;
            case ADD_ASSISTANT:
                nav.addAssistantPage();
                break;
            case ADD_DISPLAY:
                nav.addDisplayPage();
                break;
            case OPEN_TERMINAL_LIST:
                nav.openSessionBrowser();
                break;
            case OPEN_DISPLAY_LIST:
                nav.openDisplayAppList();
                break;
            case OPEN_PAGE_OVERVIEW:
                nav.openPageOverview();
                break;
            default:
                break;
        }
    }
}
