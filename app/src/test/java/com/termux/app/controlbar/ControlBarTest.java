package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.desktop.TilingDesktop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Where the bar's tools go (#424): the window in front's own content first, never v1's pages. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ControlBarTest {

    private final List<String> calls = new ArrayList<>();
    private boolean frontTakesKeyboard;
    @Nullable private WorkspaceLayout frontLayout = WorkspaceLayout.SCROLLING;
    private ControlBar bar;
    private List<TilingWindow> windows = Collections.emptyList();
    private long focused = -1;
    private final com.termux.app.tiling.TilingStack stack = com.termux.app.tiling.TilingStack.empty(1);

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
    }

    @Test public void keyboardGoesToTheWindowInFrontWhenItTakesIt() {
        frontTakesKeyboard = true;
        bar.onTool(ControlBarTool.KEYBOARD);
        assertEquals(Collections.singletonList("desktop keyboard"), calls);
    }

    @Test public void aShellsKeyboardIsTheLaunchersOwn() {
        frontTakesKeyboard = false;
        bar.onTool(ControlBarTool.KEYBOARD);
        assertEquals(Arrays.asList("desktop keyboard", "host keyboard"), calls);
    }

    @Test public void mouseModeIsOnlyTheWindowInFronts() {
        bar.onTool(ControlBarTool.MOUSE_MODE);
        assertEquals(Collections.singletonList("desktop mouse"), calls);
    }

    @Test public void splitNewTerminalAndSearchGoWhereTheyBelong() {
        bar.onTool(ControlBarTool.SPLIT);
        bar.onTool(ControlBarTool.NEW_TERMINAL);
        bar.onTool(ControlBarTool.SEARCH);
        bar.onTool(ControlBarTool.WINDOW_MENU);
        bar.onTool(ControlBarTool.NAVIGATION);
        assertEquals(Arrays.asList("split", "new terminal", "search"), calls);
    }

    @Test public void theWorkspaceMenusLayoutRowSetsTheLayout() {
        bar.onLayoutChosen(WorkspaceLayout.DWINDLE);
        assertEquals(Collections.singletonList("layout dwindle"), calls);
    }

    @Test public void theWorkspaceMenusSaveRowAsksTheDesktopToSave() {
        bar.onSaveChosen();
        assertEquals(Collections.singletonList("save"), calls);
    }

    @Test public void aHoldOnHomesBadgeOpensNoMenu() {
        frontLayout = null;
        bar.onPlaceHeld();
        assertEquals(Collections.emptyList(), calls);
    }

    @Test public void holdingTheBarOpensOverview() {
        bar.onBarHeld();
        assertEquals(Collections.singletonList("overview"), calls);
    }

    @Test public void theLayoutRowsCheckTheOneInUse() {
        List<WorkspaceMenu.Row> rows = WorkspaceMenu.layoutRows(WorkspaceLayout.DWINDLE);
        assertEquals(2, rows.size());
        assertEquals(WorkspaceLayout.SCROLLING, rows.get(0).layout);
        assertEquals(false, rows.get(0).checked);
        assertEquals(WorkspaceLayout.DWINDLE, rows.get(1).layout);
        assertEquals(true, rows.get(1).checked);
    }

    private final class FakeDesktop implements ControlBar.Desktop {
        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return Place.workspace(1); }
        @NonNull @Override public List<TilingWindow> frontWindows() { return windows; }
        @Nullable @Override public TilingWindow frontMainWindow() { return null; }
        @Override public long frontFocusedWindowId() { return focused; }
        @NonNull @Override public ControlBar.WindowActions windowActions() { return new FakeActions(); }
        @Override public boolean focusWindow(long windowId) { calls.add("focus " + windowId); return true; }
        @Override public void split() { calls.add("split"); }
        @Override public void newTerminal() { calls.add("new terminal"); }
        @Override public boolean toggleFrontKeyboard() { calls.add("desktop keyboard"); return frontTakesKeyboard; }
        @Override public boolean toggleFrontMouseMode() { calls.add("desktop mouse"); return true; }
        @Nullable @Override public WorkspaceLayout frontLayout() { return frontLayout; }
        @Override public boolean setFrontLayout(@NonNull WorkspaceLayout layout) {
            calls.add("layout " + layout.key);
            return true;
        }
        @Override public boolean frontFullScreen() { return false; }
        @Override public boolean setFrontFullScreen(boolean fullScreen) { return false; }

        @Override public void saveFront() { calls.add("save"); }
    }

    private final class FakeActions implements ControlBar.WindowActions {
        @Nullable @Override public WindowMenu.Subject menuOf(long windowId) {
            return WindowMenu.subjectOf(stack, windowId, true);
        }
        @Override public boolean toggleFloat(long windowId) { calls.add("float " + windowId); return true; }
        @Override public boolean togglePin(long windowId) { calls.add("pin " + windowId); return true; }
        @Override public boolean setColumnWidth(long windowId, float fraction) {
            calls.add("width " + windowId + " " + fraction);
            return true;
        }
        @Override public boolean setFullScreen(long windowId, boolean fullScreen) {
            calls.add((fullScreen ? "full screen " : "leave full screen ") + windowId);
            return true;
        }
        @Override public boolean setMinimised(long windowId, boolean minimised) {
            calls.add((minimised ? "minimise " : "restore ") + windowId);
            return true;
        }
        @Override public boolean toggleMaximised(long windowId) { calls.add("maximise " + windowId); return true; }
        @Override public boolean showScale(long windowId) { calls.add("scale " + windowId); return true; }
        @Override public boolean moveTo(long windowId, @NonNull Place place) {
            calls.add("move " + windowId + " " + place);
            return true;
        }
        @Override public boolean joinTab(long windowId, long targetId) { calls.add("tab " + windowId + " " + targetId); return true; }
        @Override public boolean stack(long windowId, long targetId) { calls.add("stack " + windowId + " " + targetId); return true; }
        @Override public boolean separate(long windowId) { calls.add("separate " + windowId); return true; }
        @Override public void close(long windowId) { calls.add("close " + windowId); }
        @Override public void editLook(long windowId) { calls.add("editLook " + windowId); }
        @Override public boolean reorder(long windowId, long targetId) {
            calls.add("reorder " + windowId + " " + targetId);
            return true;
        }
    }

    /** Workspace 1 with a shell and a Linux app, the bar's rows read from it. */
    private long[] twoWindows() {
        stack.goTo(Place.workspace(1));
        long shell = stack.open(com.termux.app.tiling.WindowSpec.shell(null));
        long app = stack.open(com.termux.app.tiling.WindowSpec.linuxApp("org.example", "App"));
        windows = stack.workspace(1).windows();
        focused = app;
        bar.onFrontChanged();
        return new long[] {shell, app};
    }

    @Test public void eachMenuRowGoesToItsWindowsAction() {
        long[] w = twoWindows();
        WindowMenu.Subject menu = WindowMenu.subjectOf(stack, w[1], true);
        for (WindowMenu.Row row : menu.flat()) bar.onWindowMenuRow(w[1], row);
        assertEquals(Arrays.asList("float 2", "full screen 2", "minimise 2", "maximise 2", "scale 2",
            "move 2 Workspace 2", "move 2 Home page 1", "tab 2 1", "stack 2 1", "width 2 0.5", "width 2 1.0",
            "editLook 2", "float 2", "pin 2", "close 2"), calls);
    }

    @Test public void aHeldMarkFocusesItsWindowFirst() {
        long[] w = twoWindows();
        bar.onMarkHeld(0, new android.graphics.Rect(0, 0, 10, 10));
        assertEquals("focus " + w[0], calls.get(0));
    }

    @Test public void aHeldMinimisedMarkStaysMinimised() {
        long[] w = twoWindows();
        stack.setMinimised(w[1], true);
        windows = stack.workspace(1).windows();
        bar.onFrontChanged();
        bar.onMarkHeld(1, new android.graphics.Rect(0, 0, 10, 10));
        assertEquals(Collections.emptyList(), calls);
    }

    @Test public void aScreenReadersMarkActionsAreTheMenusFlatRows() {
        twoWindows();
        assertEquals(WindowMenu.subjectOf(stack, 1, true).flat().size(), bar.markActions(0).size());
        bar.onMarkAction(0, bar.markActions(0).get(0));
        assertEquals(Collections.singletonList("float 1"), calls);
    }

    @Test public void aCarriedMarkReordersOntoAnotherMarksWindow() {
        long[] w = twoWindows();
        bar.onMarkReorder(1, 0);
        bar.onMarkReorder(1, 1);
        assertEquals(Collections.singletonList("reorder " + w[1] + " " + w[0]), calls);
    }

    @Test public void restoreFromTheMenuGoesToTheWindowAsATapDoes() {
        long[] w = twoWindows();
        stack.setMinimised(w[1], true);
        bar.onWindowMenuRow(w[1], WindowMenu.subjectOf(stack, w[1], true).rows.get(0));
        assertEquals(Collections.singletonList("focus " + w[1]), calls);
    }

    @Test public void aMenuRowFromStaleRowsIsSpentAndTheRowsAreReadAgain() {
        long[] w = twoWindows();
        WindowMenu.Subject shown = WindowMenu.subjectOf(stack, w[1], true);
        WindowMenu.Row move = shown.moveRows.get(0);
        // A Workspace comes under the card's numbers before the tap.
        stack.moveWindow(w[0], Place.workspace(2));
        windows = stack.workspace(1).windows();

        assertEquals(false, bar.chooseFromMenu(w[1], shown, move));
        assertEquals(Collections.emptyList(), calls);
        assertEquals(true, bar.chooseFromMenu(w[1], WindowMenu.subjectOf(stack, w[1], true),
            WindowMenu.subjectOf(stack, w[1], true).rows.get(0)));
    }

    @Test public void withoutTheListALiftedMarkMovesNothing() {
        twoWindows();
        bar.onMarkLifted(5f, 50f);
        bar.onMarkDropped(1, 5f, 50f);
        bar.onMarkDragCancelled();
        assertEquals(Collections.emptyList(), calls);
    }

    private final class FakeHost implements ControlBar.Host {
        @Override public int homePageCount() { return 1; }
        @Override public int homePageInFront() { return 0; }
        @Override public void showHomePage(int page) { calls.add("home " + page); }
        @NonNull @Override public List<ControlBarView.PinnedApp> pinnedApps() { return Collections.emptyList(); }
        @Override public void openPinnedApp(int index) { calls.add("app " + index); }
        @Override public void holdPinnedApp(int index, @NonNull View anchor) { calls.add("hold " + index); }
        @Override public void toggleKeyboard() { calls.add("host keyboard"); }
        @Override public void openSearch() { calls.add("search"); }
        @Override public void openDrawer() { calls.add("drawer"); }
        @Override public boolean drawerToolShown() { return false; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 0f; }
        @Override public int ink() { return 0xFFFFFFFF; }
        @Override public int accent() { return 0xFF00FF00; }
        @Override public void openOverview() { calls.add("overview"); }
    }
}
