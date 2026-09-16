package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.launcher.notifications.NotificationSwipePolicy;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A badged pinned icon and the app drawer want the same drag on every edge but the bottom. This is
 * the row's half of sharing it: who ends up owning the stream, and that nothing else on the row
 * changed to buy it.
 *
 * <p>The state a real gesture builds in the icon's own listener is set here directly, because the
 * binding that builds it needs a pinned catalogue, an icon cache and a live notification; what is
 * under test is what the row does with a DOWN that landed on a badge, not how it learned that.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class SuggestionBarQuickReplyTest {

    private static final int ROW_WIDTH = 720;
    private static final int ROW_HEIGHT = 160;
    private static final String BADGED_PACKAGE = "com.example.chat";

    private static final int CLAIM_PENDING = 0;
    private static final int CLAIM_PAGE_SWIPE = 1;
    private static final int CLAIM_DRAWER_DRAG = 2;

    private Context context;
    private SuggestionBarView row;
    private RecordingChild child;
    private RecordingListener listener;
    private float handoffPx;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        context.getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;
        row = new SuggestionBarView(context, null);
        child = new RecordingChild(context);
        row.addView(child, new ViewGroup.LayoutParams(ROW_WIDTH / 2, ROW_HEIGHT));
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        row.layout(0, 0, ROW_WIDTH, ROW_HEIGHT);
        listener = new RecordingListener();
        row.setAppDrawerGestureListener(listener);
        row.setAppsEdge(Edge.TOP);
        row.setDrawerPull(AppDrawerGestureArbiter.Pull.DOWN);
        handoffPx = context.getResources().getDisplayMetrics().heightPixels
            * NotificationSwipePolicy.HANDOFF_TRAVEL_FRACTION;
    }

    @After
    public void tearDown() {
        LauncherNotificationBadgeStore.clear();
    }

    @Test
    public void aBadgedIconOnATopRowHoldsTheDrawerOffAndThenGivesItTheDrag() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        armBadgedDown(100f, 20f);

        // Short of the hand-off the drag is the quick reply's: the drawer never begins and the
        // icon keeps receiving the stream it is peeking under.
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + handoffPx * 0.5f);
        assertEquals(0, listener.begins);
        assertEquals(CLAIM_PENDING, claim());
        assertTrue(child.countOf(MotionEvent.ACTION_MOVE) > 0);
        assertEquals(0, child.countOf(MotionEvent.ACTION_CANCEL));

        // Past it the reply stands down and the plane takes over — from where the finger is, so
        // the drag it begins with is the hand-off point rather than the icon it started on.
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + handoffPx + 40f);
        assertEquals(1, listener.begins);
        assertEquals(CLAIM_DRAWER_DRAG, claim());
        assertEquals(20f + handoffPx + 40f, listener.downRawY, 1f);
        assertEquals(1, child.countOf(MotionEvent.ACTION_CANCEL));
        dispatch(MotionEvent.ACTION_UP, 100f, 20f + handoffPx + 40f);
        assertEquals(1, listener.ends);
    }

    @Test
    public void anUnbadgedIconLeavesTheTowardCentreAxisToTheDrawer() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f, 20f + 200f);
        assertEquals(CLAIM_DRAWER_DRAG, claim());
        assertEquals(1, listener.begins);
        // And the pull began at the icon, not at any hand-off point.
        assertEquals(20f, listener.downRawY, 1f);
        dispatch(MotionEvent.ACTION_UP, 100f, 20f + 200f);
    }

    @Test
    public void aSwipeAlongTheBarFromABadgedIconIsStillThePages() {
        dispatch(MotionEvent.ACTION_DOWN, 100f, 20f);
        armBadgedDown(100f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 100f + 300f, 20f);
        assertEquals(CLAIM_PAGE_SWIPE, claim());
        assertEquals(0, listener.begins);
        dispatch(MotionEvent.ACTION_UP, 100f + 300f, 20f);
    }

    @Test
    public void aRailsBadgedIconIsItsHostsToArbitrate() {
        // Standing on a side the row claims no pull of its own, so it lends no axis either: the
        // scrolling host above it runs the same gate, and the row must not run a second one.
        row.setVerticalForm(true);
        row.setAppsEdge(Edge.LEFT);
        row.setDrawerPull(AppDrawerGestureArbiter.Pull.NONE);

        dispatch(MotionEvent.ACTION_DOWN, 40f, 60f);
        armBadgedDown(40f, 60f);
        dispatch(MotionEvent.ACTION_MOVE, 40f + 600f, 60f);
        assertEquals(CLAIM_PENDING, claim());
        assertEquals(0, listener.begins);
        dispatch(MotionEvent.ACTION_UP, 40f + 600f, 60f);
    }

    @Test
    public void theDownProbeFindsABadgedIconAndNothingElse() {
        Map<View, String> targets = ReflectionHelpers.getField(row, "notificationSwipeTargets");
        targets.put(child, BADGED_PACKAGE);

        assertFalse("no badge yet", row.isBadgedIconAt(10f, 10f));
        badge(BADGED_PACKAGE);
        assertTrue(row.isBadgedIconAt(10f, 10f));
        // Past the icon's own bounds it is nobody's; the rest of the row is the drawer's as ever.
        assertFalse(row.isBadgedIconAt(ROW_WIDTH - 10f, 10f));
        badge();
        assertFalse("the notification went away", row.isBadgedIconAt(10f, 10f));
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * The icon's own listener records at {@code ACTION_DOWN} that the finger landed on a badge;
     * that DOWN is delivered inside the row's own, so this stands in for it afterwards.
     */
    private void armBadgedDown(float downRawX, float downRawY) {
        Class<?> stateClass = ReflectionHelpers.loadClass(getClass().getClassLoader(),
            "com.termux.app.SuggestionBarView$LongPressPickupState");
        Object state = ReflectionHelpers.callConstructor(stateClass,
            ClassParameter.from(View.class, child),
            ClassParameter.from(int.class, 0),
            ClassParameter.from(float.class, downRawX),
            ClassParameter.from(float.class, downRawY));
        ReflectionHelpers.setField(state, "notificationBadged", true);
        ReflectionHelpers.setField(row, "activeLongPressPickupState", state);
    }

    private static void badge(String... packageNames) {
        ReflectionHelpers.setStaticField(LauncherNotificationBadgeStore.class, "activePackages",
            packageNames.length == 0 ? Collections.<String>emptySet()
                : Collections.singleton(packageNames[0]));
    }

    private int claim() {
        return ReflectionHelpers.getField(row, "gestureClaim");
    }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, x, y, 0);
        try {
            row.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /** A pinned-icon stand-in that consumes its stream, so the row has a real touch target. */
    private static final class RecordingChild extends View {

        private final List<Integer> actions = new ArrayList<>();

        RecordingChild(Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            return true;
        }

        int countOf(int action) {
            int count = 0;
            for (Integer seen : actions) {
                if (seen == action) count++;
            }
            return count;
        }
    }

    private static final class RecordingListener
        implements SuggestionBarView.AppDrawerGestureListener {

        int begins;
        int drags;
        int ends;
        int cancels;
        float downRawY;

        @Override public boolean isAppDrawerEnabled() { return true; }

        @Override public boolean isSurfaceEditorActive() { return false; }

        @Override public boolean isCommandPaletteOpen() { return false; }

        @Override public boolean isAppDrawerEngaged() { return false; }

        @Override public void onDrawerDragBegin(float downRawY) {
            begins++;
            this.downRawY = downRawY;
        }

        @Override public void onDrawerDrag(float rawY) { drags++; }

        @Override public void onDrawerDragEnd(float velocityPxPerSec) { ends++; }

        @Override public void onDrawerDragCancel() { cancels++; }
    }
}
