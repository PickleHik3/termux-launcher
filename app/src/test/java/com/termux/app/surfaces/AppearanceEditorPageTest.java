package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/** Look and Layout's page bar: the title by mode, Undo while dirty, Done always, Back as Back. */
@RunWith(RobolectricTestRunner.class)
public class AppearanceEditorPageTest {

    private final int[] mClicks = new int[3];
    private final java.util.List<Boolean> mModes = new java.util.ArrayList<>();
    private AppearanceEditorPage mPage;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPage = new AppearanceEditorPage(themed, new AppearanceEditorPage.Callbacks() {
            @Override public void onBack() { mClicks[0]++; }
            @Override public void onUndo() { mClicks[1]++; }
            @Override public void onDone() { mClicks[2]++; }
            @Override public void onMode(boolean layout) { mModes.add(layout); }
        });
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
    public void thePillStandsInTheTitlesPlace() {
        assertTrue(mPage.isModeShown());
        assertFalse(mPage.isTitleShown());
        View look = mPage.root().findViewById(R.id.appearance_page_mode_look);
        View layout = mPage.root().findViewById(R.id.appearance_page_mode_layout);
        look.performClick();
        assertTrue(mModes.isEmpty());
        layout.performClick();
        assertEquals(java.util.Collections.singletonList(true), mModes);
        mPage.setLayoutMode(false);
        assertEquals(1, mModes.size());
        assertTrue(look.isSelected() || ((com.google.android.material.button.MaterialButton) look).isChecked());
    }

    @Test
    public void bothSegmentsFitOneLineAt360WithUndoAndDone() {
        mPage.setDirty(true);
        View root = mPage.root();
        root.measure(View.MeasureSpec.makeMeasureSpec(
                (int) (360 * root.getResources().getDisplayMetrics().density), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        TextView look = mPage.root().findViewById(R.id.appearance_page_mode_look);
        TextView layout = mPage.root().findViewById(R.id.appearance_page_mode_layout);
        for (TextView t : new TextView[] {look, layout}) {
            assertEquals(1, t.getLineCount());
            assertTrue(t.getWidth() > 0);
            assertEquals((int) (40 * root.getResources().getDisplayMetrics().density), t.getHeight());
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
    public void thePageIsTransparentAndItsContentRegionTakesNoTouch() {
        assertEquals(null, mPage.root().getBackground());
        View content = mPage.root().findViewById(R.id.appearance_page_content);
        assertFalse(content.isClickable());
    }
}
