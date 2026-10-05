package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Appearance surface's one bar: the Wallpaper | Look | Layout pill (a title on the Icon pack
 * page), Undo while the editor is dirty, Done on every page, Back as Back.
 */
@RunWith(RobolectricTestRunner.class)
public class AppearanceEditorPageTest {

    private final int[] mClicks = new int[3];
    private final List<AppearanceEditorPage.Segment> mSegments = new ArrayList<>();
    private ContextThemeWrapper mThemed;
    private AppearanceEditorPage mPage;

    /** A navigator that writes down what the bar asked. */
    private static final class RecordingNavigator implements AppearanceSurfaceController.Navigator {
        final List<String> calls = new ArrayList<>();

        @Override public void openLook() { calls.add("look"); }
        @Override public void openLayout() { calls.add("layout"); }
        @Override public void openIcons() { calls.add("icons"); }
        @Override public void close() { calls.add("close"); }
        @Override public void back() { calls.add("back"); }
    }

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPage = new AppearanceEditorPage(mThemed, new AppearanceEditorPage.Callbacks() {
            @Override public void onBack() { mClicks[0]++; }
            @Override public void onUndo() { mClicks[1]++; }
            @Override public void onDone() { mClicks[2]++; }
            @Override public void onSegment(AppearanceEditorPage.Segment segment) { mSegments.add(segment); }
        });
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static boolean checked(View segment) {
        return ((MaterialButton) segment).isChecked();
    }

    @Test
    public void titleFollowsTheMode() {
        TextView title = mPage.root().findViewById(R.id.appearance_page_title);
        assertEquals("Look", title.getText().toString());
        mPage.setLayoutMode(true);
        assertEquals("Layout", title.getText().toString());
        mPage.setLayoutMode(false);
        assertEquals("Look", mPage.title().toString());
    }

    @Test
    public void thePillStandsInTheTitlesPlaceWithThreeSegments() {
        assertTrue(mPage.isModeShown());
        assertFalse(mPage.isTitleShown());
        View wallpaper = mPage.root().findViewById(R.id.appearance_page_mode_wallpaper);
        View look = mPage.root().findViewById(R.id.appearance_page_mode_look);
        View layout = mPage.root().findViewById(R.id.appearance_page_mode_layout);
        look.performClick();
        assertTrue("the selected segment is not a move", mSegments.isEmpty());
        layout.performClick();
        assertEquals(Arrays.asList(AppearanceEditorPage.Segment.LAYOUT), mSegments);
        idle();
        assertTrue("no mode change happened, so the pill is back on Look", checked(look));
        wallpaper.performClick();
        idle();
        assertEquals(AppearanceEditorPage.Segment.WALLPAPER, mSegments.get(1));
        assertTrue("a tap the surface did not follow puts the pill back", checked(look));
        mPage.setLayoutMode(true);
        assertTrue(checked(layout));
        assertEquals(2, mSegments.size());
    }

    @Test
    public void thePillFollowsThePageThatMoved() {
        View wallpaper = mPage.root().findViewById(R.id.appearance_page_mode_wallpaper);
        mPage.showWallpaper();
        assertTrue(checked(wallpaper));
        mPage.setLayoutMode(false);
        assertTrue(checked(mPage.root().findViewById(R.id.appearance_page_mode_look)));
    }

    @Test
    public void onlyTheSelectedSegmentShowsItsNameAndTheOthersKeepOneForTalkBack() {
        TextView wallpaper = mPage.root().findViewById(R.id.appearance_page_mode_wallpaper);
        TextView look = mPage.root().findViewById(R.id.appearance_page_mode_look);
        TextView layout = mPage.root().findViewById(R.id.appearance_page_mode_layout);
        assertEquals("Look", look.getText().toString());
        assertEquals("", wallpaper.getText().toString());
        assertEquals("", layout.getText().toString());
        assertEquals("Wallpaper", wallpaper.getContentDescription().toString());
        assertEquals("Layout", layout.getContentDescription().toString());
        mPage.showWallpaper();
        assertEquals("Wallpaper", wallpaper.getText().toString());
        assertEquals("", look.getText().toString());
    }

    @Test
    public void noOverviewBehindADirectOpenMeansNoWallpaperSegment() {
        View wallpaper = mPage.root().findViewById(R.id.appearance_page_mode_wallpaper);
        assertEquals(View.VISIBLE, wallpaper.getVisibility());
        mPage.setWallpaperAvailable(false);
        assertEquals(View.GONE, wallpaper.getVisibility());
    }

    @Test
    public void thePillWithUndoAndDoneFitsOneLineAt360() {
        mPage.setDirty(true);
        View root = mPage.root();
        float density = root.getResources().getDisplayMetrics().density;
        root.measure(View.MeasureSpec.makeMeasureSpec((int) (360 * density), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        for (AppearanceEditorPage.Segment s : AppearanceEditorPage.Segment.values()) {
            mPage.setLayoutMode(s == AppearanceEditorPage.Segment.LAYOUT);
            if (s == AppearanceEditorPage.Segment.WALLPAPER)
                mPage.showWallpaper();
            root.measure(View.MeasureSpec.makeMeasureSpec((int) (360 * density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            View bar = root.findViewById(R.id.appearance_page_bar);
            View pill = root.findViewById(R.id.appearance_page_mode);
            assertTrue(s + ": the pill is inside the bar", pill.getWidth() > 0
                && pill.getWidth() <= bar.getWidth());
            View slot = root.findViewById(R.id.appearance_page_mode_slot);
            View done = root.findViewById(R.id.appearance_page_done);
            assertTrue(s + ": the pill fits the slot between back and Undo", pill.getWidth() <= slot.getWidth());
            assertEquals(s + ": segments are 40dp", (int) (40 * density),
                root.findViewById(R.id.appearance_page_mode_look).getHeight());
            assertTrue(done.getWidth() > 0);
        }
        assertTrue(mPage.isUndoShown());
        assertTrue(mPage.isDoneShown());
    }

    @Test
    public void undoShowsWhileDirtyAndDoneIsAlwaysThere() {
        assertFalse(mPage.isUndoShown());
        assertTrue(mPage.isDoneShown());
        mPage.setDirty(true);
        assertTrue(mPage.isUndoShown());
        mPage.setDirty(false);
        assertFalse(mPage.isUndoShown());
        assertTrue(mPage.isDoneShown());
    }

    @Test
    public void theBarsButtonsReachTheSurface() {
        mPage.setDirty(true);
        mPage.root().findViewById(R.id.appearance_page_back).performClick();
        mPage.root().findViewById(R.id.appearance_page_undo).performClick();
        mPage.root().findViewById(R.id.appearance_page_done).performClick();
        assertEquals(1, mClicks[0]);
        assertEquals(1, mClicks[1]);
        assertEquals(1, mClicks[2]);
    }

    @Test
    public void theEditorPageIsTransparentAndItsContentRegionTakesNoTouch() {
        assertNull(mPage.root().getBackground());
        View content = mPage.root().findViewById(R.id.appearance_page_content);
        assertFalse(content.isClickable());
    }

    @Test
    public void theOverviewsBarHasThePillOnWallpaperAndAsksTheNavigator() {
        RecordingNavigator navigator = new RecordingNavigator();
        View page = new FrameLayout(mThemed);
        AppearanceEditorPage overview = AppearanceEditorPage.overview(mThemed, navigator, page);
        View root = overview.root();
        assertSame("the page is the content", page, ((FrameLayout) overview.content()).getChildAt(0));
        assertTrue(overview.isModeShown());
        assertFalse(overview.isTitleShown());
        assertTrue(overview.isDoneShown());
        assertFalse("Undo is for the editor", overview.isUndoShown());
        assertTrue(checked(root.findViewById(R.id.appearance_page_mode_wallpaper)));
        assertTrue("an opaque page, unlike the editor's", root.getBackground() != null);
        root.findViewById(R.id.appearance_page_mode_look).performClick();
        idle();
        assertTrue("the pill waits for the page to move", checked(root.findViewById(R.id.appearance_page_mode_wallpaper)));
        root.findViewById(R.id.appearance_page_mode_layout).performClick();
        root.findViewById(R.id.appearance_page_back).performClick();
        root.findViewById(R.id.appearance_page_done).performClick();
        assertEquals(Arrays.asList("look", "layout", "close", "close"), navigator.calls);
    }

    @Test
    public void theIconPackPageWearsThePillOnIconPackWithBackAndDoneAndGoesWhereTheSegmentsSay() {
        RecordingNavigator navigator = new RecordingNavigator();
        AppearanceEditorPage icons = AppearanceEditorPage.icons(mThemed, navigator, "Icon pack",
            new FrameLayout(mThemed));
        View root = icons.root();
        assertTrue(icons.isModeShown());
        assertFalse("the pill replaces the title", icons.isTitleShown());
        assertFalse("no Undo", icons.isUndoShown());
        assertTrue(icons.isDoneShown());
        assertTrue(checked(root.findViewById(R.id.appearance_page_mode_icon_pack)));
        assertEquals("Icon pack", ((TextView) root.findViewById(R.id.appearance_page_mode_icon_pack)).getText().toString());
        root.findViewById(R.id.appearance_page_mode_wallpaper).performClick();
        idle();
        root.findViewById(R.id.appearance_page_mode_look).performClick();
        idle();
        root.findViewById(R.id.appearance_page_mode_layout).performClick();
        idle();
        root.findViewById(R.id.appearance_page_back).performClick();
        root.findViewById(R.id.appearance_page_done).performClick();
        assertEquals(Arrays.asList("back", "look", "layout", "back", "close"), navigator.calls);
    }

    @Test
    public void theOverviewsIconPackSegmentAsksForTheIconsPage() {
        RecordingNavigator navigator = new RecordingNavigator();
        AppearanceEditorPage overview = AppearanceEditorPage.overview(mThemed, navigator, new FrameLayout(mThemed));
        overview.root().findViewById(R.id.appearance_page_mode_icon_pack).performClick();
        assertEquals(Arrays.asList("icons"), navigator.calls);
    }

    @Test
    public void theBarAloneCanBeHiddenForTheHop() {
        assertTrue(mPage.isBarVisible());
        mPage.setBarVisible(false);
        assertFalse(mPage.isBarVisible());
        assertEquals(View.INVISIBLE, mPage.root().findViewById(R.id.appearance_page_bar).getVisibility());
    }
}
