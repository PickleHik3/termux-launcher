package com.termux.app.launcher.popup;

import static com.termux.app.launcher.popup.AppMenuPlan.Row.ADD_TO_HOME;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.APP_INFO;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.CHANGE_ICON;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.MORE_SHORTCUTS;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.MOVE_TO_DOCK;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.NOTIFICATIONS;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.PIN;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.REMOVE_FROM_FOLDER;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.REMOVE_FROM_HOME;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.RESET_ICON;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.SHORTCUT;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.UNINSTALL;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.UNPIN;
import static com.termux.app.launcher.popup.AppMenuPlan.Row.WIDGETS;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The App menu's rows and groups, top to bottom, for every place an icon can live. */
public class AppMenuPlanTest {

    private static AppMenuPlan.Inputs dockApp() {
        AppMenuPlan.Inputs in = new AppMenuPlan.Inputs();
        in.source = AppMenuPlan.Source.PINNED;
        in.androidPackage = true;
        in.hasWidgets = true;
        in.canAddToHome = true;
        return in;
    }

    @Test
    public void aDockAppWithoutBadgeOrShortcuts_isOneGroupInLauncher3Order() {
        assertEquals(Collections.singletonList(
                Arrays.asList(WIDGETS, APP_INFO, ADD_TO_HOME, UNPIN, CHANGE_ICON, UNINSTALL)),
            AppMenuPlan.groups(dockApp()));
    }

    @Test
    public void aBadgedDockApp_leadsWithALoneNotificationsGroup() {
        AppMenuPlan.Inputs in = dockApp();
        in.badged = true;
        List<List<AppMenuPlan.Row>> groups = AppMenuPlan.groups(in);
        assertEquals(2, groups.size());
        assertEquals(Collections.singletonList(NOTIFICATIONS), groups.get(0));
        assertEquals(UNPIN, groups.get(1).get(3));
    }

    @Test
    public void shortcutsSitBetweenNotificationsAndTheActions() {
        AppMenuPlan.Inputs in = dockApp();
        in.badged = true;
        in.shortcutCount = 2;
        List<List<AppMenuPlan.Row>> groups = AppMenuPlan.groups(in);
        assertEquals(3, groups.size());
        assertEquals(Arrays.asList(SHORTCUT, SHORTCUT), groups.get(1));
        assertEquals(WIDGETS, groups.get(2).get(0));
    }

    @Test
    public void sixShortcutsAllShow_sevenCollapseBehindMore() {
        AppMenuPlan.Inputs in = dockApp();
        in.shortcutCount = 6;
        assertEquals(Collections.nCopies(6, SHORTCUT), AppMenuPlan.groups(in).get(0));

        in.shortcutCount = 9;
        List<AppMenuPlan.Row> shortcuts = AppMenuPlan.groups(in).get(0);
        assertEquals(6, shortcuts.size());
        assertEquals(Collections.nCopies(5, SHORTCUT), shortcuts.subList(0, 5));
        assertEquals(MORE_SHORTCUTS, shortcuts.get(5));
        assertEquals(5, AppMenuPlan.visibleShortcutCount(9));
        assertEquals(0, AppMenuPlan.visibleShortcutCount(0));
    }

    @Test
    public void aCustomIcon_putsResetIconRightAfterChangeIcon() {
        AppMenuPlan.Inputs in = dockApp();
        in.customIcon = true;
        assertEquals(Arrays.asList(WIDGETS, APP_INFO, ADD_TO_HOME, UNPIN, CHANGE_ICON, RESET_ICON,
            UNINSTALL), AppMenuPlan.groups(in).get(0));
    }

    @Test
    public void aDrawerAppNotOnTheDock_offersPin() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.UNPINNED;
        in.hasWidgets = false;
        assertEquals(Collections.singletonList(
                Arrays.asList(APP_INFO, ADD_TO_HOME, PIN, CHANGE_ICON, UNINSTALL)),
            AppMenuPlan.groups(in));
    }

    @Test
    public void aLinuxApp_hasNoPackageRows() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.UNPINNED;
        in.androidPackage = false;
        in.canAddToHome = false;
        assertEquals(Collections.singletonList(Arrays.asList(PIN, CHANGE_ICON)),
            AppMenuPlan.groups(in));
    }

    @Test
    public void aFolderMember_tradesPinForMoveAndRemove() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.FOLDER_MEMBER;
        assertEquals(Collections.singletonList(Arrays.asList(WIDGETS, APP_INFO, MOVE_TO_DOCK,
                REMOVE_FROM_FOLDER, CHANGE_ICON, UNINSTALL)),
            AppMenuPlan.groups(in));
    }

    /** On Home: no Add to home (it is there), and Remove from Home just before Uninstall. */
    @Test
    public void aHomeIcon_tradesAddToHomeForRemoveFromHome() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.HOME;
        assertEquals(Collections.singletonList(Arrays.asList(WIDGETS, APP_INFO, PIN, CHANGE_ICON,
                REMOVE_FROM_HOME, UNINSTALL)),
            AppMenuPlan.groups(in));
    }

    @Test
    public void aHomeIconForAnAppOnTheDock_offersUnpin() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.HOME;
        in.pinnedToDock = true;
        in.customIcon = true;
        assertEquals(Collections.singletonList(Arrays.asList(WIDGETS, APP_INFO, UNPIN, CHANGE_ICON,
                RESET_ICON, REMOVE_FROM_HOME, UNINSTALL)),
            AppMenuPlan.groups(in));
    }

    /** A Linux app on Home still comes off Home; it has nothing to uninstall. */
    @Test
    public void aHomeLinuxApp_keepsRemoveFromHome() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.HOME;
        in.androidPackage = false;
        assertEquals(Collections.singletonList(Arrays.asList(PIN, CHANGE_ICON, REMOVE_FROM_HOME)),
            AppMenuPlan.groups(in));
    }

    /** The drawer's menu is unchanged: an unpinned app still offers Add to home. */
    @Test
    public void aDrawerApp_stillOffersAddToHome() {
        AppMenuPlan.Inputs in = dockApp();
        in.source = AppMenuPlan.Source.UNPINNED;
        assertEquals(Collections.singletonList(Arrays.asList(WIDGETS, APP_INFO, ADD_TO_HOME, PIN,
                CHANGE_ICON, UNINSTALL)),
            AppMenuPlan.groups(in));
    }
}
