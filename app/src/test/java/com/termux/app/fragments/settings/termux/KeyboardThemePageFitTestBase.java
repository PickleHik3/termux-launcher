package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/**
 * The Keyboard theme page at the subclass's width: every control fits one screen with no
 * scrolling, the preview keeps its shape, and the card below the colour circles keeps its gap.
 */
public abstract class KeyboardThemePageFitTestBase {

    /** Status bar plus toolbar that the settings screen keeps above the page, in dp. */
    private static final int CHROME_DP = 24 + 56;
    private static final int GAP_DP = 8;

    private ScrollView launchPage() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT,
                KeyboardColorSchemeFragment.class.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof KeyboardColorSchemeFragment);
        View view = fragment.getView();
        assertTrue(view instanceof ScrollView);
        return (ScrollView) view;
    }

    private float density() {
        return RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density;
    }

    private int viewportHeight() {
        return RuntimeEnvironment.getApplication().getResources().getDisplayMetrics()
            .heightPixels - Math.round(CHROME_DP * density());
    }

    /** Measures and lays the page out in the viewport the screen leaves it. */
    private ViewGroup layOut(ScrollView scroll) {
        int width = RuntimeEnvironment.getApplication().getResources().getDisplayMetrics()
            .widthPixels;
        int height = viewportHeight();
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, width, height);
        return (ViewGroup) scroll.getChildAt(0);
    }

    @Test
    public void everyItemFitsOneScreenWithoutScrolling() {
        ScrollView scroll = launchPage();
        ViewGroup content = layOut(scroll);
        assertTrue("content " + content.getMeasuredHeight() + "px must fit the "
                + viewportHeight() + "px viewport",
            content.getMeasuredHeight() <= viewportHeight());
        assertTrue(content.getMeasuredWidth() <= scroll.getMeasuredWidth());
    }

    @Test
    public void previewKeepsItsShapeWhileScaledDown() {
        ViewGroup content = layOut(launchPage());
        ViewGroup preview = (ViewGroup) content.getChildAt(0);
        View keyboard = preview.getChildAt(0);
        assertNotNull(keyboard);
        assertTrue(keyboard.getHeight() > 0);
        int contentWidth = content.getWidth() - content.getPaddingLeft()
            - content.getPaddingRight();
        assertTrue("keyboard is never wider than the page", keyboard.getWidth() <= contentWidth);
        assertEquals("preview holder hugs the keyboard", keyboard.getHeight(), preview.getHeight());
    }

    @Test
    public void paletteCardSitsBelowTheColourCirclesWithAGap() {
        ViewGroup content = layOut(launchPage());
        View grid = content.findViewWithTag(KeyboardColorSchemeFragment.TAG_SWATCH_GRID);
        View card = content.findViewWithTag(KeyboardColorSchemeFragment.TAG_PALETTE_CARD);
        assertNotNull(grid);
        assertNotNull(card);
        ViewGroup rows = (ViewGroup) grid;
        assertEquals(3, rows.getChildCount());
        View lastRow = rows.getChildAt(rows.getChildCount() - 1);
        int lastCircleBottom = grid.getTop() + lastRow.getBottom();
        int gap = Math.round(GAP_DP * density());
        assertTrue("card top " + card.getTop() + " leaves " + gap + "px under circles ending at "
                + lastCircleBottom,
            card.getTop() - lastCircleBottom >= gap);
    }
}
