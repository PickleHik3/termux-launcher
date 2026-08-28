package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.MenuRow;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The app drawer's long-press has to be the dock's long-press, not a lookalike. Two things are
 * pinned here.
 *
 * <p>First, shape: the drawer binds with {@code pinnedIndex = -1} because a grid cell has no dock
 * slot, and the menu builder is expected to recover the real index itself. If it ever stopped doing
 * that, a pinned app long-pressed in the drawer would silently offer "Pin" a second time.
 *
 * <p>Second, placement: every dock menu opens upward and is then clamped into the visible frame,
 * which is correct precisely because a dock icon lives at the bottom of the screen. A first-row
 * drawer cell has nothing above it, and the clamp alone would park the menu on top of the icon that
 * opened it. The flip has to fire for the top anchor and stay out of the way for the bottom one —
 * the dock's coordinates must not move by a pixel.
 */
@RunWith(RobolectricTestRunner.class)
// A real phone geometry: the placement question only exists on a screen tall enough for a menu to
// have room above a bottom anchor and none above a top one.
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-xxhdpi", application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarDrawerPopupTest {

    private static final String PACKAGE = "com.example.drawerapp";
    private static final String ACTIVITY = "com.example.drawerapp.Main";

    private ActivityController<Activity> controller;
    private Activity activity;
    private SuggestionBarView row;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        row = new SuggestionBarView(activity, null);
        activity.setContentView(row, new ViewGroup.LayoutParams(720, 160));
        idle();
    }

    @After
    public void tearDown() {
        if (controller != null) controller.close();
    }

    // -------------------------------------------------------------------- menu shape

    @Test
    public void anUnpinnedDrawerCell_buildsTheSameMenuAsTheDock() {
        LauncherAppEntry entry = entry();
        setPinnedItems(Collections.emptyList());

        List<String> fromDrawer = menuRowsFor(anchor -> row.bindDrawerAppContextLongPress(anchor, entry));
        List<String> fromDock = menuRowsFor(anchor -> bindDockLongPress(anchor, entry, -1));

        assertEquals(fromDock, fromDrawer);
        assertTrue("expected the unpinned shape, got " + fromDrawer, fromDrawer.contains("Pin"));
        assertTrue("expected the unpinned shape, got " + fromDrawer,
            fromDrawer.contains("Change app icon"));
    }

    @Test
    public void aPinnedAppLongPressedInTheDrawer_getsTheDockSlotShape() {
        LauncherAppEntry entry = entry();
        // The drawer never knows a dock index; the menu builder has to find it from the app ref.
        setPinnedItems(Collections.singletonList(new PinnedAppItem(entry.appRef)));

        List<String> fromDrawer = menuRowsFor(anchor -> row.bindDrawerAppContextLongPress(anchor, entry));
        List<String> fromDock = menuRowsFor(anchor -> bindDockLongPress(anchor, entry, 0));

        assertEquals(fromDock, fromDrawer);
        assertTrue("expected the pinned shape, got " + fromDrawer, fromDrawer.contains("Unpin"));
        assertTrue("expected the pinned shape, got " + fromDrawer,
            fromDrawer.contains("Change dock icon"));
    }

    // -------------------------------------------------------------------- placement

    @Test
    public void aTopAnchor_flipsTheMenuBelowItself() {
        int popupHeight = 420;
        Rect frame = visibleFrame();
        FixedAnchor anchor = new FixedAnchor(activity, frame.top);
        RecordingPopup popup = new RecordingPopup(activity, 300, popupHeight);

        showAt(popup, anchor);

        int gap = dp(4);
        assertEquals(clamp(frame.top + FixedAnchor.SIZE + gap, frame.top,
            Math.max(frame.top, frame.bottom - popupHeight)), popup.y);
        assertTrue("a top anchor must not be covered by its own menu",
            popup.y >= frame.top + FixedAnchor.SIZE);
    }

    @Test
    public void aBottomAnchor_keepsTheDocksUpwardPlacement() {
        int popupHeight = 420;
        Rect frame = visibleFrame();
        int anchorTop = frame.bottom - FixedAnchor.SIZE;
        FixedAnchor anchor = new FixedAnchor(activity, anchorTop);
        RecordingPopup popup = new RecordingPopup(activity, 300, popupHeight);

        showAt(popup, anchor);

        int gap = dp(4);
        assertEquals(anchorTop - popupHeight - gap, popup.y);
        assertTrue("the dock's menu still opens upward", popup.y < anchorTop);
    }

    @Test
    public void anAnchorWithExactlyEnoughRoomAbove_doesNotFlip() {
        // The boundary case that decides whether the flip can ever fire for a dock icon: the menu
        // fits above by exactly the gap, so the guard must not trigger.
        int popupHeight = 420;
        Rect frame = visibleFrame();
        int gap = dp(4);
        int anchorTop = frame.top + popupHeight + gap;
        FixedAnchor anchor = new FixedAnchor(activity, anchorTop);
        RecordingPopup popup = new RecordingPopup(activity, 300, popupHeight);

        showAt(popup, anchor);

        assertEquals(frame.top, popup.y);
    }

    // -------------------------------------------------------------------- helpers

    private interface Binder {
        void bind(View anchor);
    }

    /** Binds via {@code binder}, long-presses, and reads back the menu rows the popup was built from. */
    private List<String> menuRowsFor(Binder binder) {
        View anchor = new View(activity);
        row.addView(anchor, new ViewGroup.LayoutParams(120, 120));
        idle();
        binder.bind(anchor);
        assertTrue("the long press was not consumed", anchor.performLongClick());
        idle();

        List<String> labels = new ArrayList<>();
        AnchoredMenu menu = ReflectionHelpers.getField(row, "appContextMenu");
        for (MenuRow menuRow : menu.rows()) {
            labels.add(String.valueOf(menuRow.rowView.getText()));
        }
        ReflectionHelpers.callInstanceMethod(row, "dismissAppContextPopup");
        idle();
        row.removeView(anchor);
        return labels;
    }

    private void bindDockLongPress(View anchor, LauncherAppEntry entry, int pinnedIndex) {
        ReflectionHelpers.callInstanceMethod(row, "bindAppContextLongPress",
            ReflectionHelpers.ClassParameter.from(View.class, anchor),
            ReflectionHelpers.ClassParameter.from(LauncherAppEntry.class, entry),
            ReflectionHelpers.ClassParameter.from(int.class, pinnedIndex),
            ReflectionHelpers.ClassParameter.from(
                com.termux.app.launcher.model.PinnedFolderItem.class, null),
            ReflectionHelpers.ClassParameter.from(AppRef.class, null),
            ReflectionHelpers.ClassParameter.from(boolean.class, false));
    }

    private void showAt(PopupWindow popup, View anchor) {
        new AnchoredMenu(row, ReflectionHelpers.getField(row, "menuTheme"))
            .placeAtAnchor(popup, anchor, false);
    }

    private Rect visibleFrame() {
        Rect frame = new Rect();
        row.getWindowVisibleDisplayFrame(frame);
        if (frame.isEmpty()) {
            frame.set(0, 0,
                activity.getResources().getDisplayMetrics().widthPixels,
                activity.getResources().getDisplayMetrics().heightPixels);
        }
        return frame;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void setPinnedItems(List<PinnedItem> items) {
        ReflectionHelpers.setField(row, "pinnedItems", new ArrayList<>(items));
    }

    private LauncherAppEntry entry() {
        return new LauncherAppEntry(new AppRef(PACKAGE, ACTIVITY), "Drawer App",
            new ColorDrawable(0xFF223344));
    }

    private void idle() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** An anchor with a screen position we control, so placement is arithmetic rather than layout. */
    private static final class FixedAnchor extends View {
        static final int SIZE = 150;

        private final int screenTop;

        FixedAnchor(Context context, int screenTop) {
            super(context);
            this.screenTop = screenTop;
            layout(0, screenTop, SIZE, screenTop + SIZE);
        }

        @Override
        public void getLocationOnScreen(int[] outLocation) {
            outLocation[0] = 0;
            outLocation[1] = screenTop;
        }
    }

    /** Records the placement instead of asking the window manager for a real window. */
    private static final class RecordingPopup extends PopupWindow {
        int x = Integer.MIN_VALUE;
        int y = Integer.MIN_VALUE;

        RecordingPopup(Context context, int width, int height) {
            super(context);
            setWidth(width);
            setHeight(height);
        }

        @Override
        public void showAtLocation(View parent, int gravity, int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
