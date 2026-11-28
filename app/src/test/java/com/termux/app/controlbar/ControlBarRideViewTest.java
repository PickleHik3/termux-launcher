package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * The bar rides the keyboard through real views (#427, terl-integration-1): laid out in a pages
 * host over the accessory stack, the bar's drawn bottom stands its margin above the keyboard's
 * drawn top at the keyboard's rest up, at its rest down, and on a frame between; a Workspace
 * terminal's keyboard folds it to one row. Round 1 saw it stand a keyboard's height too high in a
 * v1 pane, which Terl no longer shows; this pins the Terl path off-device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class ControlBarRideViewTest {

    private static final int W = 400;
    private static final int H = 800;
    private static final int KEYBOARD = 300;
    private static final int MARGIN = 8;

    private final List<String> calls = new ArrayList<>();
    private final boolean frontTakesKeyboard = false;
    @Nullable private final WorkspaceLayout frontLayout = WorkspaceLayout.SCROLLING;
    private List<TilingWindow> windows = Collections.emptyList();
    private long focused = -1;
    private final com.termux.app.tiling.TilingStack stack = com.termux.app.tiling.TilingStack.empty(1);

    private RelativeLayout host;
    private FrameLayout accessory;
    private ControlBar bar;
    private ControlBarLayout layout;
    private boolean keyboardUp;
    /** Room the stack keeps above what it draws (v1's lanes, standing down under the bar). */
    private int emptyTop;
    private int barMoves;
    private final List<Runnable> posted = new ArrayList<>();

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        host = new RelativeLayout(activity);
        activity.setContentView(host);
        accessory = new FrameLayout(activity);
        accessory.setId(View.generateViewId());
        RelativeLayout.LayoutParams stackParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, KEYBOARD);
        stackParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        host.addView(accessory, stackParams);
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
        bar.view().setId(View.generateViewId());
        layout = new ControlBarLayout(bar, host, accessory, null, new ControlBarLayout.Host() {
            @Override public int keptBottomPx() { return 0; }
            // The in-app keyboard stays laid out while it is down.
            @Override public int keyboardDownLaidOutPx() { return KEYBOARD; }
            @Override public boolean launcherKeyboardShown() { return keyboardUp; }
            @Override public boolean phoneKeyboardShown() { return false; }
            @Override public boolean terminalMode() { return false; }
            @NonNull @Override public ControlBarPlacement.RailSide railSide() { return ControlBarPlacement.RailSide.RIGHT; }
            @Override public int cutoutClearPx(int cutoutPx) { return 0; }
            @Override public int marginDp() { return MARGIN; }
            @Override public void pagesBesideBar(int leftPx, int rightPx) { }
            // Later, as the launcher's handler runs it: after the frame that asked.
            @Override public void post(@NonNull Runnable work) { posted.add(work); }
            @NonNull @Override public ControlBarLayout.Stack stack() {
                return new ControlBarLayout.Stack() {
                    @Override public int emptyTopPx() { return emptyTop; }
                };
            }
            @Override public void barMoved() { barMoves++; }
        });
        host.addView(bar.view(), host.indexOfChild(accessory), layout.firstParams());
        settle();
    }

    private void settle() {
        host.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, W, H);
    }

    /**
     * The stack drawn at keyboard reveal {@code reveal}: laid out, translated down by the down
     * share, and the bar told on that frame only (as the stack's writer tells it); what the ride
     * posted runs after the frame and the next layout pass follows, with no ride of its own.
     */
    private float drawStack(float reveal) {
        float ty = StackScene.keyboardTranslationPx(reveal, KEYBOARD);
        accessory.setTranslationY(ty);
        layout.ride(ty);
        runPosted();
        settle();
        return ty;
    }

    private void runPosted() {
        while (!posted.isEmpty()) posted.remove(0).run();
    }

    private float barDrawnBottom() {
        return bar.view().getBottom() + bar.view().getTranslationY();
    }

    private float keyboardDrawnTop() {
        return accessory.getTop() + accessory.getTranslationY();
    }

    @Test public void withTheKeyboardUpTheBarStandsOnItInOneRow() {
        keyboardUp = true;
        layout.sync();
        settle();
        drawStack(1f);

        assertEquals(ControlBarForm.ONE_ROW, bar.form());
        assertEquals(keyboardDrawnTop() - MARGIN, barDrawnBottom(), 0.5f);
    }

    /**
     * Pong 11-28: with the keyboard up on a Workspace the bar stood a bar's height above the
     * keyboard. The stack was laid out taller than what it drew (room for v1's rows, standing down
     * under the bar), and the bar stood on the stack's top. It stands on what is drawn.
     */
    @Test public void theBarStandsOnTheKeyboardNotOnRoomTheStackKeepsAboveIt() {
        int room = 104;
        ViewGroup.LayoutParams params = accessory.getLayoutParams();
        params.height = KEYBOARD + room;
        accessory.setLayoutParams(params);
        emptyTop = room;
        keyboardUp = true;
        settle();
        layout.sync();
        settle();
        drawStack(1f);

        assertEquals(ControlBarForm.ONE_ROW, bar.form());
        assertEquals("on the keyboard's top, not the room's", keyboardDrawnTop() + room - MARGIN,
            barDrawnBottom(), 0.5f);

        keyboardUp = false;
        layout.sync();
        settle();
        drawStack(0f);
        assertEquals(ControlBarForm.TWO_ROWS, bar.form());
        assertEquals("down, at the screen's bottom: the room is not counted", H - MARGIN,
            barDrawnBottom(), 0.5f);
    }

    /**
     * Review P2: Home's grid and every wall page end at the bar's top, read from the same drawn
     * stack the bar stands on: the room the stack keeps above the keyboard is not kept above the
     * bar for Home.
     */
    @Test public void thePagesEndAtTheBarsDrawnTopNotTheStacksRoom() {
        int room = 104;
        ViewGroup.LayoutParams params = accessory.getLayoutParams();
        params.height = KEYBOARD + room;
        accessory.setLayoutParams(params);
        emptyTop = room;
        keyboardUp = true;
        settle();
        layout.sync();
        settle();
        drawStack(1f);

        float barTop = bar.view().getTop() + bar.view().getTranslationY();
        assertEquals("the pages end where the bar's top is drawn", H - barTop,
            layout.pageTopInsetPx(KEYBOARD + room), 0.5f);
    }

    @Test public void theBarSaysWhenItMovesSoTheDrawerReadsItsTopAgain() {
        keyboardUp = true;
        layout.sync();
        settle();
        int before = barMoves;
        keyboardUp = false;
        layout.sync();
        assertEquals(before + 1, barMoves);
        layout.sync();
        assertEquals("nothing moved, nothing said", before + 1, barMoves);
    }

    @Test public void theRoomAboveIsTheHighestShownContentsTop() {
        assertEquals(0, ControlBarPlacement.emptyTopPx(400, -1, -1));
        assertEquals(104, ControlBarPlacement.emptyTopPx(400, -1, 104));
        assertEquals(60, ControlBarPlacement.emptyTopPx(400, 60, 104));
        assertEquals(60, ControlBarPlacement.emptyTopPx(400, 104, 60));
        assertEquals("held inside the stack", 400, ControlBarPlacement.emptyTopPx(400, -1, 900));
    }

    @Test public void withTheKeyboardDownTheBarStandsAtTheBottomInTwoRows() {
        keyboardUp = false;
        layout.sync();
        settle();
        drawStack(0f);

        assertEquals(ControlBarForm.TWO_ROWS, bar.form());
        assertEquals(H - MARGIN, barDrawnBottom(), 0.5f);
    }

    @Test public void onEveryFrameOfTheRevealTheBarRidesTheKeyboardsTop() {
        keyboardUp = true;
        layout.sync();
        settle();
        for (float reveal = 0f; reveal <= 1f; reveal += 0.25f) {
            drawStack(reveal);
            assertEquals("reveal " + reveal, Math.min(H, keyboardDrawnTop()) - MARGIN, barDrawnBottom(), 0.5f);
        }
    }

    @Test public void aRevealThatEndsBeforeTheBarIsLaidOutCountsTheKeyboardOnce() {
        // Round 2: the keyboard came up on frames that rode the bar from its down layout; the
        // layout for the up rest followed with no frame of the stack, and the bar stood a
        // keyboard's height too high.
        keyboardUp = false;
        layout.sync();
        settle();
        drawStack(0f);
        keyboardUp = true;
        for (float reveal = 0.25f; reveal <= 1f; reveal += 0.25f) {
            float ty = StackScene.keyboardTranslationPx(reveal, KEYBOARD);
            accessory.setTranslationY(ty);
            layout.ride(ty);
        }
        runPosted();
        settle();
        runPosted();
        settle();

        assertEquals(keyboardDrawnTop() - MARGIN, barDrawnBottom(), 0.5f);
    }

    @Test public void aHideThatEndsBeforeTheBarIsLaidOutLeavesItOnScreen() {
        // Round 2: after the keyboard hid, the bar was laid out low with the hide's ride still on
        // it and stood off the screen's bottom.
        keyboardUp = true;
        layout.sync();
        settle();
        drawStack(1f);
        keyboardUp = false;
        for (float reveal = 0.75f; reveal >= 0f; reveal -= 0.25f) {
            float ty = StackScene.keyboardTranslationPx(reveal, KEYBOARD);
            accessory.setTranslationY(ty);
            layout.ride(ty);
        }
        runPosted();
        settle();
        runPosted();
        settle();

        assertEquals(H - MARGIN, barDrawnBottom(), 0.5f);
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
