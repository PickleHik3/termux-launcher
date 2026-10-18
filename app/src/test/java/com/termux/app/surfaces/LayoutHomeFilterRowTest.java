package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.termux.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Layout mode's Row A on the narrowest phone, at Large text: Orientation, Style, the Home filter
 * and eye-off stand in one row inside the sheet, in that order, none over another, the Home
 * filter a 48dp target that can be turned off again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LayoutHomeFilterRowTest {

    private AppearanceEditorPanel mPanel;
    private int mWidthPx;

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(1.3f);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPanel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        mWidthPx = Math.round(themed.getResources().getConfiguration().screenWidthDp
            * themed.getResources().getDisplayMetrics().density);
    }

    @After
    public void tearDown() {
        RuntimeEnvironment.setFontScale(1f);
    }

    private void layOut() {
        View root = mPanel.view();
        int height = mPanel.measureFor(EditorMode.LAYOUT, mWidthPx);
        root.measure(View.MeasureSpec.makeMeasureSpec(mWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, mWidthPx, height);
    }

    @Test
    public void rowAHoldsTheHomeFilterBetweenStyleAndEyeOff() {
        mPanel.showLayoutMode();
        layOut();
        View root = mPanel.view();
        View orientation = root.findViewById(R.id.layout_editor_orientation);
        View style = root.findViewById(R.id.appearance_editor_style);
        MaterialButtonToggleGroup home = mPanel.homeFilterToggle();
        View eyeOff = root.findViewById(R.id.layout_editor_hidden);
        View highlight = root.findViewById(R.id.layout_editor_hidden_highlight);

        assertEquals(View.VISIBLE, home.getVisibility());
        assertEquals("one row", orientation.getTop(), home.getTop());
        assertTrue("after Style", home.getLeft() >= style.getRight());
        assertTrue("before eye-off's drop area: " + home.getRight() + " > " + highlight.getLeft(),
            home.getRight() <= highlight.getLeft());
        assertTrue("eye-off inside the sheet's padding",
            eyeOff.getRight() <= root.getWidth() - root.getPaddingRight());

        float density = root.getResources().getDisplayMetrics().density;
        MaterialButton segment = root.findViewById(R.id.layout_editor_home_filter_home);
        assertEquals(0, segment.getMinWidth());
        assertTrue("a 48dp target", segment.getWidth() >= Math.round(48 * density));
        assertTrue("a 48dp target", segment.getHeight() >= Math.round(48 * density));
        assertTrue("outlined like its neighbours", segment.getStrokeWidth() > 0);
    }

    @Test
    public void theHomeFilterTurnsOnAndOff() {
        MaterialButtonToggleGroup home = mPanel.homeFilterToggle();
        assertFalse(home.isSelectionRequired());
        home.check(R.id.layout_editor_home_filter_home);
        assertEquals(R.id.layout_editor_home_filter_home, home.getCheckedButtonId());
        home.uncheck(R.id.layout_editor_home_filter_home);
        assertEquals(View.NO_ID, home.getCheckedButtonId());
    }

    @Test
    public void layoutModeLeavingHidesIt() {
        mPanel.showLayoutMode();
        layOut();
        assertEquals(View.VISIBLE, mPanel.homeFilterToggle().getVisibility());
        mPanel.showAppearanceMode();
        layOut();
        assertEquals(View.GONE, mPanel.homeFilterToggle().getVisibility());
    }
}
