package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.OverlayRegistry;
import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

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

/** Overview hides the pages and chrome layers while up, and every way out shows them again. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LayersUnderOverviewTest {

    private static final float PAGES_REST = 1f;
    private static final float CHROME_REST = 0.8f;

    private FrameLayout mPages, mChrome, mSheets;
    private OverviewController mController;
    private final List<Integer> mWentTo = new ArrayList<>();

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        root.setId(R.id.window_layers_root);
        mPages = layer(activity, root, R.id.activity_termux_root_relative_layout);
        mChrome = layer(activity, root, R.id.window_layer_chrome);
        mSheets = layer(activity, root, R.id.window_layer_sheets);
        mPages.setAlpha(PAGES_REST);
        mChrome.setAlpha(CHROME_REST);
        activity.setContentView(root);

        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.HOME, null));
        in.windowNames = Collections.emptyList();
        in.currentWindow = -1;
        in.currentPageId = 1;
        final PageBarModel wall = PageBarModel.build(in);
        mController = new OverviewController(activity, new OverviewController.Host() {
            @Override @NonNull public PageBarModel wall() { return wall; }
            @Override @NonNull public PageSwitchTargets.Navigator navigator() {
                return new PageSwitchTargets.Navigator() {
                    @Override public void goToPage(int pageId) { mWentTo.add(pageId); }
                    @Override public void showTerminalWindow(int index) { }
                    @Override public void goToTerminal() { }
                    @Override public void openOverview() { }
                };
            }
            @Override public CardFacts facts(@NonNull OverviewModel.Card card,
                                             @NonNull TextBudget budget) {
                return null;
            }
            @Override public void assistantFacts(
                    @NonNull java.util.function.Consumer<CardFacts> onFacts) { }
            @Override public int homePageId() { return -1; }
            @Override public int homePageCount() { return 2; }
            @Override public int contentsOf(@NonNull OverviewModel.Card card) { return 0; }
            @Override public void setHome(@NonNull OverviewModel.Card card) { }
            @Override public void rename(@NonNull OverviewModel.Card card,
                                         @NonNull Runnable renamed) { }
            @Override public void remove(@NonNull OverviewModel.Card card) { }
            @Override public void moveWallPage(int pageId, int refPageId, boolean after) { }
            @Override public void moveWindow(int from, int to) { }
            @Override @Nullable public String commandOf(@NonNull OverviewModel.Card card) {
                return null;
            }
            @Override @NonNull public OverviewLook look() {
                return new OverviewLook(new ColorDrawable(0x99000000), 0xFF000000, 0xFF444444,
                    0xFF0000FF);
            }
        });
    }

    private static FrameLayout layer(Activity activity, FrameLayout root, int id) {
        FrameLayout layer = new FrameLayout(activity);
        layer.setId(id);
        root.addView(layer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return layer;
    }

    private void assertHidden() {
        assertTrue(mController.isOpen());
        assertEquals(1, mSheets.getChildCount());
        assertEquals(0f, mPages.getAlpha(), 0f);
        assertEquals(0f, mChrome.getAlpha(), 0f);
    }

    private void assertAtRest() {
        assertFalse(mController.isOpen());
        assertEquals(0, mSheets.getChildCount());
        assertEquals(PAGES_REST, mPages.getAlpha(), 0f);
        assertEquals(CHROME_REST, mChrome.getAlpha(), 0f);
    }

    @Test public void closeShowsTheLayersAgain() {
        mController.open();
        assertHidden();
        mController.close();
        assertAtRest();
    }

    @Test public void backShowsTheLayersAgain() {
        mController.open();
        assertTrue(mController.onBack());
        assertAtRest();
    }

    @Test public void closeImmediatelyShowsTheLayersAgain() {
        mController.open();
        mController.closeImmediately(OverlayRegistry.CloseReason.STOP);
        assertAtRest();
    }

    @Test public void openingNeverFocusesGoTo() {
        mController.open();
        OverviewView view = (OverviewView) mSheets.getChildAt(0);
        // Overview takes focus itself, so no keyboard comes up until the field is tapped.
        assertTrue(view.isFocused());
        assertFalse(view.field().isFocused());
    }

    @Test public void aMenuStandsAgainstTheLiftedCardCaptionIncluded() {
        mController.open();
        OverviewView view = (OverviewView) mSheets.getChildAt(0);
        View card = new View(view.getContext());
        android.widget.FrameLayout parent = new android.widget.FrameLayout(view.getContext());
        parent.addView(card);
        card.layout(0, 0, 100, 200);
        android.graphics.Rect out = new android.graphics.Rect();
        assertTrue(OverviewView.boundsOnScreen(card, out));
        // Grown about the middle to the lifted size, before the lift has run.
        assertTrue(out.top < 0 && out.bottom > 200 && out.left < 0 && out.right > 100);
    }

    @Test public void aCardTapShowsTheLayersAgainAndGoes() {
        mController.open();
        OverviewView view = (OverviewView) mSheets.getChildAt(0);
        LinearLayout content = (LinearLayout) view.getChildAt(0);
        ScrollView scroll = (ScrollView) content.getChildAt(1);
        View second = ((OverviewBoard) scroll.getChildAt(0)).wallCard(1);
        assertTrue(second.performClick());
        assertAtRest();
        assertEquals(Collections.singletonList(2), mWentTo);
    }

    @Test public void theHomeExitShowsTheLayersBeforeTheViewGoes() {
        mController.open();
        mController.exitStarted(300L);
        // The fade reveals the page while Overview is still up; its end closes it.
        assertTrue(mController.isOpen());
        assertEquals(PAGES_REST, mPages.getAlpha(), 0f);
        assertEquals(CHROME_REST, mChrome.getAlpha(), 0f);
        mController.closeImmediately(OverlayRegistry.CloseReason.HOME);
        assertAtRest();
    }

    @Test public void repeatedCyclesNeverLeaveTheLayersHidden() {
        for (int i = 0; i < 3; i++) {
            mController.open();
            mController.open();
            assertHidden();
            mController.close();
            mController.close();
            assertAtRest();
        }
        mController.toggle();
        assertHidden();
        mController.toggle();
        assertAtRest();
    }

    @Test public void aSecondHideKeepsTheFirstRestAlpha() {
        LayersUnderOverview under = new LayersUnderOverview();
        under.hide(mPages, mChrome);
        under.hide(mPages, mChrome);
        under.restore();
        under.restore();
        assertEquals(PAGES_REST, mPages.getAlpha(), 0f);
        assertEquals(CHROME_REST, mChrome.getAlpha(), 0f);
    }

    // ---- The swipe past the first page (#356) -------------------------------------------------

    private static final int WIDTH = 1000;

    @Test public void aPullBringsOverviewInOverTheChromeAndOpensInPlace() {
        com.termux.app.wall.OverviewEdge edge = mController.wallEdge();
        assertTrue(edge.available());
        assertTrue(edge.beginPull(WIDTH));
        assertFalse("one pull at a time", edge.available());
        OverviewView view = (OverviewView) mSheets.getChildAt(0);
        assertEquals("out of sight beyond the first page", -WIDTH, view.getTranslationX(), 0f);
        assertEquals(PAGES_REST, mPages.getAlpha(), 0f);
        assertEquals(CHROME_REST, mChrome.getAlpha(), 0f);

        edge.pose(250f, WIDTH);
        assertEquals(-750f, view.getTranslationX(), 0.001f);
        assertEquals("the page slides beside it, shown", PAGES_REST, mPages.getAlpha(), 0f);
        assertEquals("the chrome goes as Overview covers it", CHROME_REST * 0.75f,
            mChrome.getAlpha(), 0.001f);

        edge.pose(WIDTH, WIDTH);
        edge.end(true);
        assertHidden();
        assertEquals(0f, view.getTranslationX(), 0f);
        mController.close();
        assertAtRest();
    }

    @Test public void aPullThatSpringsBackLeavesNothingBehind() {
        com.termux.app.wall.OverviewEdge edge = mController.wallEdge();
        assertTrue(edge.beginPull(WIDTH));
        edge.pose(300f, WIDTH);
        edge.pose(0f, WIDTH);
        edge.end(false);
        assertAtRest();
        assertTrue(edge.available());
    }

    @Test public void overviewClosedUnderThePullShowsTheLayersAtItsEnd() {
        com.termux.app.wall.OverviewEdge edge = mController.wallEdge();
        assertTrue(edge.beginPull(WIDTH));
        edge.pose(400f, WIDTH);
        mController.closeImmediately(OverlayRegistry.CloseReason.HOME);
        edge.pose(500f, WIDTH);
        edge.end(true);
        assertAtRest();
    }

    @Test public void overviewOpenInPlaceIsNotAvailableToPull() {
        mController.open();
        assertFalse(mController.wallEdge().available());
        assertFalse(mController.wallEdge().beginPull(WIDTH));
        assertEquals(0f, mSheets.getChildAt(0).getTranslationX(), 0f);
        assertHidden();
    }
}
