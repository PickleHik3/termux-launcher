package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackView;
import com.termux.app.place.HomeKeyboardCover;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.StackScene;
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
import java.util.Collections;
import java.util.List;

/**
 * The bar stands on what the accessory stack draws, through the stack's real views (terl-kbd-room,
 * pong 11-28). The stack is the one the launcher lays out: the rows over the keyboard (an
 * {@link EdgeStackView} holding the A–Z row's host) and the keyboard's column, the keyboard laid
 * out while it is down and the container drawn its keys' height lower then. Home's keyboard cover
 * (#309) draws the rows at their keyboard-down rect while the keyboard rises over them, so with the
 * keyboard up they are drawn under it and their room above it is empty: the bar stood on that room
 * (a 92 px gap over the keyboard). And while the app drawer holds the stack, a keyboard its scroll
 * puts down leaves the bar at the keyboard-down rest, not where the keyboard was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class ControlBarStackDrawnTest {

    private static final int W = 400;
    private static final int H = 800;
    private static final int ROWS = 46;
    private static final int KEYBOARD = 300;
    private static final int MARGIN = 8;

    private RelativeLayout host;
    private LinearLayout stack;
    private EdgeStackView rows;
    private View azRow;
    private LinearLayout column;
    private ControlBar bar;
    private ControlBarLayout layout;
    private boolean keyboardUp;
    /** The app drawer holds the stack: its writer draws nothing. */
    private boolean held;
    private int barMoves;
    private final List<Runnable> posted = new ArrayList<>();

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new RelativeLayout(activity);
        activity.setContentView(host);
        stack = new LinearLayout(activity);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setId(View.generateViewId());
        rows = new EdgeStackView(activity);
        rows.setEdge(PlaceLayout.Edge.BOTTOM);
        FrameLayout azHost = new FrameLayout(activity);
        azRow = new View(activity);
        azHost.addView(azRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ROWS));
        rows.addView(azHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        // The Terminal's keys host, empty: Terl's extra keys off.
        rows.addView(new FrameLayout(activity), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0));
        stack.addView(rows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        column = new LinearLayout(activity);
        stack.addView(column, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, KEYBOARD));
        RelativeLayout.LayoutParams stackParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stackParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        host.addView(stack, stackParams);
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
        bar.view().setId(View.generateViewId());
        layout = new ControlBarLayout(bar, host, stack, null, new ControlBarLayout.Host() {
            @Override public int keptBottomPx() { return 0; }
            @Override public int keyboardDownLaidOutPx() { return keyboardUp ? 0 : KEYBOARD; }
            @Override public boolean launcherKeyboardShown() { return keyboardUp; }
            @Override public boolean phoneKeyboardShown() { return false; }
            @Override public boolean terminalMode() { return false; }
            @NonNull @Override public ControlBarPlacement.RailSide railSide() { return ControlBarPlacement.RailSide.RIGHT; }
            @Override public int cutoutClearPx(int cutoutPx) { return 0; }
            @Override public int marginDp() { return MARGIN; }
            @Override public void pagesBesideBar(int leftPx, int rightPx) { }
            @Override public void post(@NonNull Runnable work) { posted.add(work); }
            // As the launcher's host reads it: the rows and the keyboard where they are drawn, or,
            // held, at the rest the writer draws (Home's cover puts up-keyboard rows under it).
            @Override public int stackEmptyTopPx() {
                int rowsTop = held && keyboardUp ? -1 : StackDrawnTop.rowsTopPx(rows, held);
                return ControlBarPlacement.emptyTopPx(stack.getHeight(), rowsTop,
                    StackDrawnTop.keyboardTopPx(column, held));
            }
            @Override public boolean stackHeld() { return held; }
            @Override public void barMoved() { barMoves++; }
        });
        host.addView(bar.view(), host.indexOfChild(stack), layout.firstParams());
        settle();
    }

    private void settle() {
        host.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, W, H);
    }

    private void runPosted() {
        while (!posted.isEmpty()) posted.remove(0).run();
    }

    /**
     * One frame of the stack's writer at keyboard reveal {@code reveal}: the container its down
     * share lower, Home's rows dropped by the cover to their keyboard-down rect, then the bar told.
     */
    private void drawStack(float reveal) {
        float share = StackScene.keyboardTranslationPx(reveal, KEYBOARD);
        stack.setTranslationY(share);
        rows.setTranslationY(HomeKeyboardCover.rowsDropPx(false, KEYBOARD, share));
        layout.ride(share);
        runPosted();
        settle();
    }

    private void restAt(boolean up) {
        keyboardUp = up;
        layout.sync();
        settle();
        drawStack(up ? 1f : 0f);
        runPosted();
        settle();
    }

    private float barDrawnBottom() {
        return bar.view().getBottom() + bar.view().getTranslationY();
    }

    private float keyboardDrawnTop() {
        return stack.getTop() + stack.getTranslationY() + column.getTop() + column.getTranslationY();
    }

    private float rowsDrawnTop() {
        return stack.getTop() + stack.getTranslationY() + rows.getTop() + rows.getTranslationY();
    }

    @Test public void withTheKeyboardUpOverHomesCoveredRowsTheBarStandsOnTheKeyboard() {
        restAt(true);

        assertEquals(ControlBarForm.ONE_ROW, bar.form());
        assertEquals("no gap of the rows' room over the keyboard", keyboardDrawnTop() - MARGIN,
            barDrawnBottom(), 0.5f);
    }

    @Test public void withTheKeyboardDownTheBarStandsOnTheRowsItShows() {
        restAt(false);

        assertEquals(ControlBarForm.TWO_ROWS, bar.form());
        assertEquals(H - ROWS, rowsDrawnTop(), 0.5f);
        assertEquals(rowsDrawnTop() - MARGIN, barDrawnBottom(), 0.5f);
    }

    @Test public void throughTheRevealTheBarStaysOnTheRowsUntilTheKeyboardReachesThem() {
        restAt(false);
        keyboardUp = true;
        layout.sync();
        settle();
        for (float reveal = 0f; reveal <= 1f; reveal += 0.125f) {
            drawStack(reveal);
            assertEquals("reveal " + reveal, Math.min(rowsDrawnTop(), keyboardDrawnTop()) - MARGIN,
                barDrawnBottom(), 0.5f);
        }
    }

    @Test public void rowsWithNothingShownTakeNoRoomUnderTheBar() {
        azRow.setVisibility(View.GONE);
        settle();
        restAt(true);
        assertEquals(keyboardDrawnTop() - MARGIN, barDrawnBottom(), 0.5f);

        restAt(false);
        assertEquals("on the screen's bottom", H - MARGIN, barDrawnBottom(), 0.5f);
    }

    @Test public void rowsSlidOffTheStackDrawNothingThere() {
        restAt(true);
        rows.setTranslationY(0f);
        rows.getChildAt(0).setTranslationX(W);
        layout.ride(stack.getTranslationY());
        assertEquals("a lane slid away is not stood on", keyboardDrawnTop() - MARGIN,
            barDrawnBottom(), 0.5f);
    }

    /**
     * Pong 11-28 (03c): the drawer's scroll put the keyboard down while the drawer held the stack;
     * the stack's writer drew nothing, and the bar (and the drawer ending at its top) stayed where
     * the keyboard had been. It settles at the keyboard-down rest, once: the writer's own rest
     * after the drawer lets go moves it no further.
     */
    @Test public void aKeyboardPutDownUnderAHeldStackSettlesTheBarAtItsDownRestOnce() {
        restAt(true);
        held = true;
        int moves = barMoves;
        keyboardUp = false;
        // The keyboard's request asks the bar to settle; the stack's writer draws nothing.
        layout.post();
        runPosted();
        settle();
        runPosted();
        settle();

        assertEquals(ControlBarForm.TWO_ROWS, bar.form());
        float rest = H - ROWS - MARGIN;
        assertEquals("on the rows' keyboard-down rest", rest, barDrawnBottom(), 0.5f);
        assertEquals("the drawer is told to read the bar's top again", moves + 1, barMoves);

        held = false;
        drawStack(0f);
        assertEquals("the writer's rest moves it no further", rest, barDrawnBottom(), 0.5f);
    }

    @Test public void aHeldStackRidesNoStaleFrameWhenTheBarIsLaidOutAgain() {
        restAt(false);
        held = true;
        float rest = barDrawnBottom();
        // A frame the drawer left on the container (its own band moves) is not the bar's to ride.
        stack.setTranslationY(0f);
        layout.ride(0f);
        assertEquals(rest, barDrawnBottom(), 0.5f);
    }

    private static final class FakeDesktop implements ControlBar.Desktop {
        private final com.termux.app.tiling.TilingStack mStack = com.termux.app.tiling.TilingStack.empty(1);

        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return Place.workspace(1); }
        @NonNull @Override public List<TilingWindow> frontWindows() { return Collections.emptyList(); }
        @Nullable @Override public TilingWindow frontMainWindow() { return null; }
        @Override public long frontFocusedWindowId() { return -1; }
        @NonNull @Override public ControlBar.WindowActions windowActions() {
            throw new UnsupportedOperationException();
        }
        @Override public boolean focusWindow(long windowId) { return true; }
        @Override public void split() { }
        @Override public void newTerminal() { }
        @Override public boolean toggleFrontKeyboard() { return false; }
        @Override public boolean toggleFrontMouseMode() { return true; }
        @Nullable @Override public WorkspaceLayout frontLayout() { return WorkspaceLayout.SCROLLING; }
        @Override public boolean setFrontLayout(@NonNull WorkspaceLayout layout) { return true; }
        @Override public boolean frontFullScreen() { return false; }
        @Override public boolean setFrontFullScreen(boolean fullScreen) { return false; }
        @Override public void saveFront() { }
    }

    private static final class FakeHost implements ControlBar.Host {
        @Override public int homePageCount() { return 1; }
        @Override public int homePageInFront() { return 0; }
        @Override public void showHomePage(int page) { }
        @NonNull @Override public List<ControlBarView.PinnedApp> pinnedApps() { return Collections.emptyList(); }
        @Override public void openPinnedApp(int index) { }
        @Override public void holdPinnedApp(int index, @NonNull View anchor) { }
        @Override public void toggleKeyboard() { }
        @Override public void openSearch() { }
        @Override public void openDrawer() { }
        @Override public boolean drawerToolShown() { return false; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 0f; }
        @Override public int ink() { return 0xFFFFFFFF; }
        @Override public int accent() { return 0xFF00FF00; }
        @Override public void openOverview() { }
    }
}
