package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.R;
import com.termux.app.Spring;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;

/**
 * Back's rungs while the category drawer is up, on the key channel — the route a back press
 * actually travels on a device, where the terminal's client offers the stroke to the open overlays
 * before the activity's {@code onBackPressed()} is ever reached.
 *
 * <p>One press does one thing: a typed query is cleared, an expanded category collapses, the
 * collapsed drawer closes, and a press arriving at a closed drawer is not claimed at all so the
 * hardware-keyboard and terminal handling behind the plane still sees it.
 *
 * <p>The same order has to hold whichever keyboard the search is configured for. It did not: with
 * the Android-keyboard search the channel declined Back outright, and because the open plane
 * swallows every key release, the declined press never reached {@code onBackPressed()} either — it
 * neither collapsed the category nor closed the drawer.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerBackPriorityTest {

    private AppDrawerController controller;
    private AppDrawerContentView content;

    @Before public void setUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("drawer-back", Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        TermuxAppSharedPreferences preferences =
            new TermuxAppSharedPreferences(activity, raw, null);
        controller = new AppDrawerController(new FakeAppDrawerHost(activity, preferences));
        content = new AppDrawerContentView(activity);
        content.setInteractive(true);
        content.bind(null, controller.getSearchController());
        // The state a settled open leaves behind, without a plane to animate one.
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mPlane", new AppDrawerPlaneView(activity));
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, 720f, 1280f));
        ReflectionHelpers.setField(controller, "mOpenRadiusPx", 33f);
        ReflectionHelpers.setField(controller, "mEngaged", true);
        ReflectionHelpers.setField(controller, "mOpen", true);
        Spring progress = ReflectionHelpers.getField(controller, "mProgress");
        progress.reset(1f);
    }

    @Test public void backCollapsesTheCategoryThenClosesTheDrawerThenFallsThrough() {
        assertBackWalksTheHierarchy();
    }

    /**
     * The Android-keyboard search, which {@code prepareContent} switches on per open when the
     * preference is set. The field only holds focus while the keyboard is actually up, so Back
     * still arrives on this channel — and must walk the same rungs.
     */
    @Test public void theAndroidKeyboardSearchWalksTheSameRungs() {
        controller.getSearchController().setTextFieldOwnsInput(true);
        assertBackWalksTheHierarchy();
    }

    @Test public void backClearsAQueryBeforeItTouchesThePlane() {
        assertTrue(controller.getSearchController().handleCodePoint('a', false));
        assertTrue(content.hasQuery());

        assertTrue(back());
        assertFalse(content.hasQuery());
        assertTrue("the query is what the press was spent on", controller.isOpen());

        assertTrue(back());
        assertFalse(controller.isOpen());
    }

    /** Default on Categories: Back collapses the category, then closes; the section is never touched. */
    @Test public void backNeverLeavesDefaultsCategoriesSection() {
        content.setViewType(AppDrawerViewType.DEFAULT);
        content.setSection(AppDrawerSection.CATEGORIES);
        assertEquals(AppDrawerViewType.CATEGORIES, content.presentedType());
        expandFirstCategory(AppDrawerViewType.DEFAULT);

        assertTrue(back());
        settleCategory();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
        assertTrue(controller.isOpen());
        assertEquals(AppDrawerSection.CATEGORIES, content.getSection());

        assertTrue("with nothing expanded the press closes the drawer", back());
        assertFalse(controller.isOpen());
        assertEquals(AppDrawerSection.CATEGORIES, content.getSection());
    }

    /** Default on Apps: nothing to collapse, so Back closes the drawer and stays on Apps. */
    @Test public void backClosesDefaultOnAppsWithoutChangingSection() {
        content.setViewType(AppDrawerViewType.DEFAULT);
        assertEquals(AppDrawerViewType.VERTICAL, content.presentedType());
        assertFalse(content.handleBackInDrawer());

        assertTrue(back());
        assertFalse(controller.isOpen());
        assertEquals(AppDrawerSection.APPS, content.getSection());
    }

    /** Edit mode is Back's first rung: one press leaves it, the next closes the drawer. */
    @Test public void backLeavesEditModeBeforeAnythingElse() {
        content.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);
        assertTrue(content.isEditing());

        assertTrue(back());
        assertFalse(content.isEditing());
        assertTrue("leaving edit mode is what the press was spent on", controller.isOpen());

        assertTrue(back());
        assertFalse(controller.isOpen());
    }

    /** The search is inert while editing: typed text is swallowed, not queried, and the bar is dimmed. */
    @Test public void searchIsInertWhileEditing() {
        content.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);

        assertTrue("the stroke is swallowed, not handed to the shell",
            controller.getSearchController().handleCodePoint('a', false));
        assertFalse(content.hasQuery());
        content.onSearchPillTapped();
        assertEquals("a tap on the bar asks for no keyboard", 0f, content.getRevealFraction(), 0f);
        assertTrue(content.getSearchPill().getAlpha() < 1f);

        content.leaveEdit(AppDrawerEditModel.Leave.TICK);
        assertEquals(1f, content.getSearchPill().getAlpha(), 0f);
        assertTrue(controller.getSearchController().handleCodePoint('a', false));
        assertTrue(content.hasQuery());
    }

    @Test public void editModeDoesNotSurviveTheDrawerClosing() {
        content.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);
        content.setInteractive(false);
        assertFalse(content.isEditing());
    }

    @Test public void aViewTypeChangeEndsEditModeButASectionChangeDoesNot() {
        content.setViewType(AppDrawerViewType.DEFAULT);
        content.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);
        content.setSection(AppDrawerSection.CATEGORIES);
        assertTrue("one session covers both sections", content.isEditing());

        content.setViewType(AppDrawerViewType.HORIZONTAL);
        assertFalse(content.isEditing());
    }

    @Test public void cellsInTheDrawerAreOutlinedWhileEditing() {
        AppDrawerAppCellView cell = new AppDrawerAppCellView(content.getContext());
        content.addView(cell);
        assertFalse(cell.hasEditOutline());

        content.enterEdit(AppDrawerEditModel.Entry.CORNER_BUTTON);
        assertTrue(AppDrawerContentView.isEditingAround(cell));
        assertTrue(cell.hasEditOutline());

        content.leaveEdit(AppDrawerEditModel.Leave.BACK);
        assertFalse(cell.hasEditOutline());
    }

    private void assertBackWalksTheHierarchy() {
        expandFirstCategory();
        assertEquals(AppDrawerCategoryExpansionModel.State.EXPANDED,
            content.getCategoryView().expansionState());

        // First press: the expanded category, and nothing else. The plane stays up.
        assertTrue(back());
        settleCategory();
        assertEquals(AppDrawerCategoryExpansionModel.State.OVERVIEW,
            content.getCategoryView().expansionState());
        assertTrue(controller.isOpen());

        // Second press: the drawer itself.
        assertTrue(back());
        assertFalse(controller.isOpen());

        // Third press: nothing of the drawer's is up, so the stroke is not claimed and whatever
        // handles Back behind the plane still sees it.
        assertFalse(back());
    }

    /** A back press on the key channel, as the activity's overlay registry hands it over. */
    private boolean back() {
        return controller.handleSearchKey(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
    }

    private void expandFirstCategory() {
        expandFirstCategory(AppDrawerViewType.CATEGORIES);
    }

    private void expandFirstCategory(AppDrawerViewType type) {
        content.setViewType(type);
        content.setCategoryMetrics(AppDrawerCategoryTileAdapterTest.metrics());
        AppDrawerCategoryBucket bucket =
            AppDrawerCategoryTileAdapterTest.bucket(AppDrawerCategory.SOCIAL, 8);
        content.getCategoryView().submitBuckets(Collections.singletonList(bucket));
        AppDrawerCategoryTileAdapterTest.layout(content.getCategoryView(), 360, 640);
        content.getCategoryView().onExpandRequested(bucket, (AppDrawerCategoryTileView)
            content.getCategoryView().getOverview().getChildAt(0));
        settleCategory();
    }

    private void settleCategory() {
        content.advanceDrawerFx(1f, 1f / 60f, true);
    }
}
