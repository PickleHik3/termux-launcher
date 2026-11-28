package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    /** The open drawer's keyboard band slide, -1 for none. */
    private float drawerSlide = -1f;
    private int barMoves;
    private int barRides;
    private Place front = Place.workspace(1);
    private final List<Runnable> posted = new ArrayList<>();

    /**
     * The stack as the launcher's host reads it: the rows and the keyboard where they are drawn,
     * or, at rest (and held), where the writer draws them at rest (Home's cover puts up-keyboard
     * rows under it).
     */
    private final ControlBarLayout.Stack stackReader = new ControlBarLayout.Stack() {
        @Override public int emptyTopPx() {
            return held ? emptyTop(true, keyboardUp) : emptyTop(false, false);
        }
        @Override public int restingEmptyTopPx(boolean up) { return emptyTop(true, up); }
        @Override public int keyboardKeysPx() { return KEYBOARD; }
        private int emptyTop(boolean rest, boolean up) {
            int rowsTop = rest && up ? -1 : StackDrawnTop.rowsTopPx(rows, rest);
            return ControlBarPlacement.emptyTopPx(stack.getHeight(), rowsTop,
                StackDrawnTop.keyboardTopPx(column, rest));
        }
        @Override public boolean held() { return held; }
        // The drawer's band slide, as the launcher's host rides it: down to the down rest.
        @Override public float heldTranslationPx(float restPx) {
            return drawerSlide < 0f ? restPx : Math.min(drawerSlide, KEYBOARD);
        }
    };

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
            @NonNull @Override public ControlBarLayout.Stack stack() { return stackReader; }
            @Override public void barMoved() { barMoves++; }
            @Override public void barRode() { barRides++; }
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

    /**
     * Review P2, 11-28: the bar's layout reads the stack's rest, never a frame's: through a reveal
     * the rows and keyboard move every frame and the bar only rides; nothing is posted or laid out
     * between the samples (no work run between them).
     */
    @Test public void aRevealLaysNothingOutItsFramesOnlyRide() {
        restAt(false);
        keyboardUp = true;
        layout.sync();
        settle();
        runPosted();
        settle();
        posted.clear();
        for (float reveal = 0f; reveal <= 1f; reveal += 0.125f) {
            float share = StackScene.keyboardTranslationPx(reveal, KEYBOARD);
            stack.setTranslationY(share);
            rows.setTranslationY(HomeKeyboardCover.rowsDropPx(false, KEYBOARD, share));
            layout.ride(share);
            assertEquals("reveal " + reveal + ": nothing posted", 0, posted.size());
            assertEquals("reveal " + reveal + ": nothing laid out", false,
                bar.view().isLayoutRequested() || host.isLayoutRequested());
            assertEquals("reveal " + reveal, Math.min(rowsDrawnTop(), keyboardDrawnTop()) - MARGIN,
                barDrawnBottom(), 0.5f);
        }
    }

    /**
     * Review P3, 11-28: a row stack with genuinely nothing in it (Terl's extra keys off, Home's
     * letters in the bar) takes no room in the stack, and the bar stands on the keyboard.
     */
    @Test public void aGenuinelyEmptyRowStackTakesNoRoom() {
        rows.removeViewAt(0);
        settle();
        restAt(true);
        assertEquals("no room in the stack", 0, rows.getHeight());
        assertEquals(column.getTop(), rows.getTop());
        assertEquals(keyboardDrawnTop() - MARGIN, barDrawnBottom(), 0.5f);
        restAt(false);
        assertEquals("on the screen's bottom", H - MARGIN, barDrawnBottom(), 0.5f);
    }

    /**
     * Review P2, 11-28: held by the drawer, the bar rides the keyboard band the drawer's scroll
     * slides, frame by frame, down to its keyboard-down rest (on the rows that rest shows), and the
     * layout that lands when the keyboard goes down for real moves it no further.
     */
    @Test public void heldByTheDrawerTheBarRidesTheBandItSlides() {
        restAt(true);
        float up = barDrawnBottom();
        held = true;
        for (float slide = 0f; slide <= 2 * KEYBOARD; slide += 75f) {
            drawerSlide = slide;
            layout.rideNow();
            assertEquals("slide " + slide, Math.min(up + slide, H - ROWS - MARGIN),
                barDrawnBottom(), 0.5f);
        }
        float rest = barDrawnBottom();
        keyboardUp = false;
        layout.post();
        runPosted();
        settle();
        runPosted();
        settle();
        assertEquals("the down rest's layout moves it no further", rest, barDrawnBottom(), 0.5f);
    }

    private float barDrawnTop() {
        return bar.view().getTop() + bar.view().getTranslationY();
    }

    /**
     * Round 6 (emulator, 36/37) and its review: the drawer's sheet ends at the bar's drawn top.
     * When the keyboard went down for real under the drawer, the bar's new rest (two rows, at the
     * keyboard-down rest) was set before its layout landed, and the drawn top was read as that
     * new rest less a ride measured from the old layout: the sheet's bottom stood a keyboard's
     * height into the bar. The drawn top read in the pass that sets the new rest is where the bar
     * is drawn once that pass lands, so the sheet and the bar land together, and the layout
     * landing moves nothing the sheet must follow again (one geometry pass).
     */
    @Test public void theDrawnTopReadWithANewRestIsWhereTheBarLandsInThatPass() {
        restAt(true);
        held = true;
        drawerSlide = KEYBOARD;
        layout.rideNow();
        assertEquals("ridden down with the keyboard", H - barDrawnTop(),
            layout.drawnTopAbovePx(), 0.5f);

        // The keyboard goes down for real: the bar takes its two rows at the down rest, laid out
        // on the next traversal; the drawer reads the bar's top in this pass.
        keyboardUp = false;
        layout.sync();
        assertEquals(ControlBarForm.TWO_ROWS, bar.form());
        int read = layout.drawnTopAbovePx();

        int rides = barRides;
        settle();
        runPosted();
        settle();
        assertEquals("the top read before the layout is where the bar lands", H - barDrawnTop(),
            read, 0.5f);
        assertEquals(read, layout.drawnTopAbovePx());
        assertEquals("on the rows' keyboard-down rest", H - ROWS - MARGIN, barDrawnBottom(), 0.5f);
        assertEquals("the layout landing asks the sheet to follow nothing", rides, barRides);
    }

    /**
     * Show the control bar in the drawer off (the default): while the drawer is on screen the bar
     * stands under its sheet, keeping its layout (the sheet grows from its place, its ride goes
     * on) and drawing nothing; then it is back as it was.
     */
    @Test public void underTheDrawersSheetTheBarKeepsItsPlaceAndDrawsNothing() {
        restAt(false);
        int top = bar.view().getTop();
        int height = bar.view().getHeight();
        bar.setUnderSheet(true);
        settle();
        assertEquals(View.INVISIBLE, bar.view().getVisibility());
        assertEquals("its place kept", top, bar.view().getTop());
        assertEquals(height, bar.view().getHeight());
        bar.setUnderSheet(false);
        assertEquals(View.VISIBLE, bar.view().getVisibility());
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

    private final class FakeDesktop implements ControlBar.Desktop {
        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return front; }
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

    /**
     * Home's A–Z at the bottom joins the bar's card under Terl (terl-bar-search): the stack's own
     * row is put away, the bar grows by the letters' row on Home only. On Home and on a Workspace,
     * keyboard up and down, the bar's drawn bottom stands its margin over what is drawn (the
     * keyboard, else the screen's bottom), at its thickness for the place: no gap, no overlap.
     */
    @Test public void withHomesLettersInTheBarItStandsOnTheKeyboardOrTheBottomAtItsThickness() {
        azRow.setVisibility(View.GONE);
        bar.setAzRow(new View(host.getContext()));
        bar.setAzRowWanted(true);
        float density = host.getResources().getDisplayMetrics().density;
        for (Place place : new Place[] {Place.home(0), Place.workspace(1)}) {
            front = place;
            bar.onFrontChanged();
            boolean home = place.isHome();
            for (boolean up : new boolean[] {true, false}) {
                restAt(up);
                runPosted();
                settle();
                String at = place + (up ? " keyboard up" : " keyboard down");
                assertEquals(at + ": the letters' row on Home only", home, bar.carriesAzRow());
                float thickness = ControlBarPlacement.thicknessDp(bar.form(), home) * density;
                assertEquals(at + ": its thickness", thickness, bar.view().getHeight(), 0.5f);
                float floor = up ? keyboardDrawnTop() : H;
                assertEquals(at + ": its margin over what is drawn", floor - MARGIN,
                    barDrawnBottom(), 0.5f);
            }
        }
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
