package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

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
import java.util.function.Consumer;

/**
 * Overview under the Terl switch (#430) through Overview's own doors: a row per place, a tap goes
 * to a window or a place, a drag moves a window or makes tabs, and Back closes it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
public class StackOverviewTest {

    private FrameLayout mPages, mChrome, mSheets;
    private OverviewController mController;
    private TilingStack mStack;
    private final List<String> mAsked = new ArrayList<>();
    private long mFirst;
    private long mSecond;
    private long mThird;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.window_layers_root);
        mPages = layer(activity, root, R.id.activity_termux_root_relative_layout);
        mChrome = layer(activity, root, R.id.window_layer_chrome);
        mSheets = layer(activity, root, R.id.window_layer_sheets);
        activity.setContentView(root);

        // Home, Workspace 1 with two terminals (the second in front), Workspace 2 with one, waiting 3.
        mStack = TilingStack.firstStart(1, "/home");
        mFirst = mStack.workspace(1).windows().get(0).id();
        mSecond = mStack.openIn(Place.workspace(1), WindowSpec.shell("/home"), false);
        mThird = mStack.openIn(Place.workspace(2), WindowSpec.shell("/home"), false);
        mStack.focusWindow(mSecond);

        mController = new OverviewController(activity, new V1Host());
        mController.setStack(new StackOverview(activity, new StackOverview.Host() {
            @NonNull @Override public TilingStack stack() { return mStack; }
            @Override public boolean moveWindow(long windowId, @NonNull Place place) {
                mAsked.add("move " + windowId + " " + place);
                return mStack.moveWindow(windowId, place);
            }
            @Override public boolean joinTab(long windowId, long targetId) {
                mAsked.add("tab " + windowId + " " + targetId);
                return mStack.joinTab(windowId, targetId);
            }
            @Override public boolean focusWindow(long windowId) {
                mAsked.add("focus " + windowId);
                return true;
            }
            @Override public boolean goTo(@NonNull Place place) {
                mAsked.add("go " + place);
                return true;
            }
            @NonNull @Override public OverviewLook look() { return look_(); }
            @Nullable @Override public Drawable appIcon(@NonNull String appId) { return null; }
            @Override public void savedWorkspaces(@NonNull Consumer<List<SavedCard>> onRead) {
                onRead.accept(Collections.emptyList());
            }
            @Override public void openSaved(@NonNull String name) { mAsked.add("saved " + name); }
        }));
    }

    @Test public void openingShowsTheStacksRowsOnTheSheetsLayerOverHiddenLayers() {
        mController.open();
        idle();

        assertTrue(mController.isOpen());
        assertEquals(1, mSheets.getChildCount());
        assertTrue(mSheets.getChildAt(0) instanceof StackOverviewView);
        assertEquals(0f, mPages.getAlpha(), 0f);
        assertEquals(0f, mChrome.getAlpha(), 0f);
        assertNotNull(text("Home"));
        assertNotNull(text("Workspace 1"));
        assertNotNull(text("Workspace 2"));
        assertNotNull(text("New Workspace"));
        assertNotNull(text("Drag a window here"));
        assertNotNull(text("Saved"));
        assertNotNull("no saved Workspaces yet (#431)", text("No saved Workspaces"));
    }

    @Test public void theRowsStandAtTheBottomInThumbReach() {
        mController.open();
        idle();
        View screen = mSheets.getChildAt(0);
        TextView saved = text("No saved Workspaces");
        int[] at = new int[2];
        saved.getLocationInWindow(at);
        int[] top = new int[2];
        screen.getLocationInWindow(top);
        assertTrue("the last row ends near the bottom edge",
            top[1] + screen.getHeight() - (at[1] + saved.getHeight()) < screen.getHeight() / 4);
        int[] home = new int[2];
        text("Home").getLocationInWindow(home);
        assertTrue("the free room is above the rows", home[1] - top[1] > screen.getHeight() / 10);
    }

    @Test public void backTheToggleAndCloseEachLeaveWithTheLayersBack() {
        mController.open();
        assertTrue(mController.onBack());
        assertAtRest();

        mController.toggle();
        assertTrue(mController.isOpen());
        mController.toggle();
        assertAtRest();

        mController.open();
        mController.close();
        assertAtRest();
        assertFalse("Back with it closed is not spent", mController.onBack());
    }

    @Test public void theHomeExitFadesTheStacksScreen() {
        mController.open();
        List<View> views = new ArrayList<>();
        mController.exitViews(views);
        assertEquals(1, views.size());
        assertTrue(views.get(0) instanceof StackOverviewView);
    }

    @Test public void theWallsPullBringsNothingInUnderTheSwitch() {
        assertFalse(mController.wallEdge().available());
    }

    @Test public void aTapOnACardGoesToItsWindow() {
        mController.open();
        idle();

        card(mThird).performClick();

        assertEquals(Collections.singletonList("focus " + mThird), mAsked);
        assertAtRest();
    }

    @Test public void aTapOnARowsTitleGoesToThePlace() {
        mController.open();
        idle();

        text("Workspace 2").performClick();
        assertEquals(Collections.singletonList("go Workspace 2"), mAsked);
        assertAtRest();
    }

    @Test public void draggingACardOntoAnotherRowMovesItsWindowThere() {
        mController.open();
        idle();

        drag(card(mThird), (View) text("Workspace 1").getParent(), text("Workspace 1"));

        assertEquals(Collections.singletonList("move " + mThird + " Workspace 1"), mAsked);
        assertEquals(Place.workspace(1), mStack.placeOf(mThird));
        assertTrue("Overview stays open, read again", mController.isOpen());
        assertNull("the emptied Workspace went", textOrNull("Workspace 3"));
    }

    @Test public void draggingACardOntoAnotherCardMakesThemTabs() {
        mController.open();
        idle();

        View target = card(mFirst);
        drag(card(mThird), target, target);

        assertEquals(Collections.singletonList("tab " + mThird + " " + mFirst), mAsked);
        assertEquals(Place.workspace(1), mStack.placeOf(mThird));
    }

    @Test public void lettingGoInItsOwnRowChangesNothing() {
        mController.open();
        idle();

        View own = text("Workspace 2");
        drag(card(mThird), own, own);

        assertTrue(mAsked.isEmpty());
    }

    @Test public void talkBackMovesACardToAnotherPlaceByAnAction() {
        mController.open();
        idle();
        View card = card(mThird);
        AccessibilityNodeInfo info = card.createAccessibilityNodeInfo();
        int action = -1;
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList()) {
            if ("Move to New Workspace".contentEquals(String.valueOf(a.getLabel()))) action = a.getId();
        }
        assertTrue("an action per other place that takes windows", action > 0);

        assertTrue(card.performAccessibilityAction(action, null));

        assertEquals(Collections.singletonList("move " + mThird + " Workspace 3"), mAsked);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void assertAtRest() {
        assertFalse(mController.isOpen());
        assertEquals(0, mSheets.getChildCount());
        assertEquals(1f, mPages.getAlpha(), 0f);
        assertEquals(1f, mChrome.getAlpha(), 0f);
    }

    /** A hold on {@code card}, then a slide to the middle of {@code over}, then letting go there. */
    private void drag(@NonNull View card, @NonNull View over, @NonNull View finger) {
        assertTrue(card.performLongClick());
        int[] from = new int[2];
        card.getLocationOnScreen(from);
        int[] to = new int[2];
        finger.getLocationOnScreen(to);
        float x = to[0] + Math.max(1, finger.getWidth()) / 2f;
        float y = to[1] + Math.max(1, finger.getHeight()) / 2f;
        long t = SystemClock.uptimeMillis();
        card.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE,
            from[0] + 10f, from[1] + 10f, 0));
        card.dispatchTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, x, y, 0));
        card.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, x, y, 0));
        idle();
    }

    /** The card of window {@code id}: its spoken name names its row. */
    @NonNull
    private View card(long id) {
        String label = id == mThird ? "Terminal in Workspace 2" : "Terminal in Workspace 1";
        List<View> found = new ArrayList<>();
        collect(mSheets, v -> label.contentEquals(String.valueOf(v.getContentDescription())), found);
        int index = id == mSecond ? 1 : 0;
        assertTrue("card of " + id, found.size() > index);
        return found.get(index);
    }

    @NonNull
    private TextView text(@NonNull String text) {
        TextView view = textOrNull(text);
        assertNotNull(text, view);
        return view;
    }

    @Nullable
    private TextView textOrNull(@NonNull String text) {
        List<View> found = new ArrayList<>();
        collect(mSheets, v -> v instanceof TextView && text.contentEquals(((TextView) v).getText()), found);
        return found.isEmpty() ? null : (TextView) found.get(0);
    }

    private static void collect(@NonNull View view, @NonNull java.util.function.Predicate<View> match,
                                @NonNull List<View> out) {
        if (match.test(view)) out.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), match, out);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static FrameLayout layer(Activity activity, FrameLayout root, int id) {
        FrameLayout layer = new FrameLayout(activity);
        layer.setId(id);
        root.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return layer;
    }

    @NonNull
    private static OverviewLook look_() {
        return new OverviewLook(new ColorDrawable(0x99000000), 0xFF000000, 0xFF444444, 0xFF0000FF);
    }

    /** v1's side, which the stack's Overview never asks. */
    private static final class V1Host implements OverviewController.Host {
        @Override @NonNull public PageBarModel wall() { throw new AssertionError("v1's wall"); }
        @Override @NonNull public PageSwitchTargets.Navigator navigator() { throw new AssertionError(); }
        @Override public CardFacts facts(@NonNull OverviewModel.Card card, @NonNull TextBudget budget) {
            return null;
        }
        @Override public void assistantFacts(@NonNull Consumer<CardFacts> onFacts) { }
        @Override public int homePageId() { return -1; }
        @Override public int homePageCount() { return 1; }
        @Override public int contentsOf(@NonNull OverviewModel.Card card) { return 0; }
        @Override public void setHome(@NonNull OverviewModel.Card card) { }
        @Override public void rename(@NonNull OverviewModel.Card card, @NonNull Runnable renamed) { }
        @Override public void remove(@NonNull OverviewModel.Card card) { }
        @Override public void moveWallPage(int pageId, int refPageId, boolean after) { }
        @Override public void moveWindow(int from, int to) { }
        @Override @Nullable public String commandOf(@NonNull OverviewModel.Card card) { return null; }
        @Override @NonNull public OverviewLook look() { return look_(); }
    }
}
