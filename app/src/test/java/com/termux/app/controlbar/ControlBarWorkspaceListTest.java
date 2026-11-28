package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.desktop.TilingDesktop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The way down from Home by touch (#429, terl-integration-1): a finger sliding up on the real bar
 * raises the real Workspace list in the window, and letting go on a row goes there. Driven through
 * the bar's view with motion events, as a finger is.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class ControlBarWorkspaceListTest {

    /** The bar's top and height in the window: the real two-row bar, at the screen's bottom. */
    private int BAR_TOP;
    private int BAR_HEIGHT;

    private final List<String> calls = new ArrayList<>();
    private final List<Place> went = new ArrayList<>();
    private final boolean frontTakesKeyboard = false;
    @Nullable private final WorkspaceLayout frontLayout = null;
    private List<TilingWindow> windows = Collections.emptyList();
    private long focused = -1;
    private final TilingStack stack = TilingStack.firstStart(1, "/home");
    private ControlBar bar;
    private FrameLayout root;
    private long downTime;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new FrameLayout(activity);
        activity.setContentView(root);
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
        float density = activity.getResources().getDisplayMetrics().density;
        int screen = activity.getResources().getDisplayMetrics().heightPixels;
        BAR_HEIGHT = Math.round(ControlBarPlacement.HEIGHT_DP * density);
        // On Android's navigation and the bar's own margin, as ControlBarPlacement stands it.
        BAR_TOP = screen - Math.round((24 + ControlBarPlacement.DEFAULT_MARGIN_DP) * density) - BAR_HEIGHT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BAR_HEIGHT);
        params.topMargin = BAR_TOP;
        root.addView(bar.view(), params);
        bar.attachWorkspaceList(activity, new WorkspaceList.Host() {
            @NonNull @Override public TilingStack stack() { return stack; }
            @Override public boolean goTo(@NonNull Place place) { went.add(place); return true; }
            @Override public boolean rename(int number, @NonNull String name) { return true; }
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 0f; }
            @Override public int ink() { return 0xFFFFFFFF; }
            @Override public int accent() { return 0xFF00FF00; }
            @Nullable @Override public Drawable appIcon(@NonNull String appId) { return null; }
            @Override public void textInput(@NonNull EditText field, boolean begin) { }
        });
        bar.attachAzRail(activity, new com.termux.app.azrail.AzRail.Host() {
            @NonNull @Override public List<com.termux.app.launcher.model.LauncherAppEntry> catalogue() {
                List<com.termux.app.launcher.model.LauncherAppEntry> apps = new ArrayList<>();
                for (String name : new String[] {"Alarm", "Browser", "Camera", "Files", "Maps"}) {
                    apps.add(new com.termux.app.launcher.model.LauncherAppEntry(
                        new com.termux.app.launcher.model.AppRef("com.example." + name.toLowerCase(), "Main"),
                        name, null));
                }
                return apps;
            }
            @Override public void open(@NonNull com.termux.app.launcher.model.LauncherAppEntry entry) {
                opened.add(entry.label);
            }
            @Nullable @Override public Drawable icon(@NonNull com.termux.app.launcher.model.LauncherAppEntry entry) { return null; }
            @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
            @Override public float cornerRadiusPx() { return 0f; }
            @Override public int ink() { return 0xFFFFFFFF; }
            @Override public int accent() { return 0xFF00FF00; }
            @Override public void closeSearch() { calls.add("close search"); }
        });
        bar.onFrontChanged();
        idle();
    }

    private final List<String> opened = new ArrayList<>();

    @Nullable
    private View railView() {
        ViewGroup content = (ViewGroup) root.getParent();
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i).getClass().getSimpleName().equals("AzRailView")) return content.getChildAt(i);
        }
        return null;
    }

    @Test public void pressingSearchAndSlidingUpRaisesTheAzRailNotTheList() {
        android.graphics.RectF search = ((ControlBarView) bar.view()).zoneBounds(ControlBarRows.Zone.SEARCH);
        float x = search.centerX();
        float y = BAR_TOP + search.centerY();
        touch(MotionEvent.ACTION_DOWN, x, y);
        for (float at = y - 12f; at > y - 200f; at -= 24f) touch(MotionEvent.ACTION_MOVE, x, at);

        assertTrue("the rail is in the window", railView() != null && railView().isShown());
        assertFalse(bar.isWorkspaceListOpen());
        assertTrue("search is put away for it", calls.contains("close search"));
        touch(MotionEvent.ACTION_UP, x, y - 200f);
    }

    /** Round 2: on a phone (420dpi, the two-row bar at the screen's bottom) Search + slide up raised nothing. */
    @Test @Config(qualifiers = "w411dp-h914dp-420dpi")
    public void onAPhonesBarPressingSearchAndSlidingUpRaisesTheAzRail() {
        pressingSearchAndSlidingUpRaisesTheAzRailNotTheList();
    }

    /** As adb's swipe: the press rests past the long-press time on Search, then slides up. */
    @Test @Config(qualifiers = "w411dp-h914dp-420dpi")
    public void onAPhonesBarAPressThatRestsOnSearchThenSlidesUpRaisesTheAzRail() {
        android.graphics.RectF search = ((ControlBarView) bar.view()).zoneBounds(ControlBarRows.Zone.SEARCH);
        float x = search.centerX();
        float y = BAR_TOP + search.centerY();
        touch(MotionEvent.ACTION_DOWN, x, y);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        for (float at = y - 12f; at > y - 400f; at -= 24f) touch(MotionEvent.ACTION_MOVE, x, at);

        assertTrue("the rail is in the window", railView() != null && railView().isShown());
        assertFalse(bar.isWorkspaceListOpen());
        touch(MotionEvent.ACTION_UP, x, y - 400f);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** A finger event at window point ({@code x}, {@code y}), sent through the window's views. */
    private void touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        // The window's content frame: it holds the bar's parent and the SHEETS layer the card joins.
        ViewGroup content = (ViewGroup) root.getParent();
        int[] at = new int[2];
        content.getLocationOnScreen(at);
        assertEquals("the window's views start at the screen's corner", 0, at[0] + at[1]);
        content.dispatchTouchEvent(event);
        event.recycle();
        idle();
    }

    /** Slides a finger from the bar's free room straight up to {@code toY}, in steps, as adb's swipe does. */
    private void slideUpTo(float toY) {
        float x = 200f;
        float y = BAR_TOP + 20f;
        touch(MotionEvent.ACTION_DOWN, x, y);
        for (float at = y - 12f; at > toY; at -= 24f) touch(MotionEvent.ACTION_MOVE, x, at);
        touch(MotionEvent.ACTION_MOVE, x, toY);
    }

    /** The screen point of the list row for {@code place}, from the card as it stands. */
    private float[] rowPoint(@NonNull Place place) {
        WorkspaceListView card = listView();
        List<WorkspaceListRows.Row> rows = WorkspaceListRows.of(stack);
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).place().equals(place)) continue;
            for (float y = 0; y < 800; y += 4) {
                if (card.rowAt(200f, y) == i) return new float[] {200f, y + 8f};
            }
        }
        throw new AssertionError("no row for " + place);
    }

    @Nullable
    private WorkspaceListView listView() {
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChildAt(i) instanceof WorkspaceListView) return (WorkspaceListView) root.getChildAt(i);
        }
        ViewGroup content = (ViewGroup) root.getParent();
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof WorkspaceListView) return (WorkspaceListView) content.getChildAt(i);
        }
        return null;
    }

    @Test public void aSlideUpOnTheBarRaisesTheListInTheWindow() {
        slideUpTo(BAR_TOP - 60f);

        assertTrue(bar.isWorkspaceListOpen());
        assertTrue("the card is on screen", listView() != null && listView().isShown());
    }

    @Test public void lettingGoOnWorkspaceOnesRowGoesThere() {
        slideUpTo(BAR_TOP - 30f);
        float[] row = rowPoint(Place.workspace(1));
        touch(MotionEvent.ACTION_MOVE, row[0], row[1]);
        touch(MotionEvent.ACTION_UP, row[0], row[1]);

        assertEquals(Collections.singletonList(Place.workspace(1)), went);
        assertFalse(bar.isWorkspaceListOpen());
    }

    @Test public void aLongSlideLetGoAboveTheCardLeavesItUpForATap() {
        slideUpTo(40f);
        touch(MotionEvent.ACTION_UP, 200f, 40f);

        assertTrue("the card stays up: the finger went past it", bar.isWorkspaceListOpen());
        assertTrue(went.isEmpty());
        float[] row = rowPoint(Place.workspace(1));
        touch(MotionEvent.ACTION_DOWN, row[0], row[1]);
        touch(MotionEvent.ACTION_UP, row[0], row[1]);
        assertEquals(Collections.singletonList(Place.workspace(1)), went);
        assertFalse(bar.isWorkspaceListOpen());
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
