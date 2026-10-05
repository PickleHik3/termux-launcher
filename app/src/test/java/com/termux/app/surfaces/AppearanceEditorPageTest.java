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
