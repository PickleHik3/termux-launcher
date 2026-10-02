package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The bottom area fits a 360dp phone in both modes with Undo up: no view runs past the sheet's
 * edge, the mode pill's words are whole, and nothing on the top row overlaps. Native graphics,
 * so text is measured with real font metrics rather than one pixel per character.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceEditorPanelFitTest {

    private static final int WIDTH_DP = 360;

    private AppearanceEditorPanel mPanel;
    private int mWidthPx;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPanel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        mWidthPx = Math.round(WIDTH_DP * themed.getResources().getDisplayMetrics().density);
        mPanel.setDirty(true);
    }

    @Test
    public void appearanceModeWithTheTerminalsThreeControlsFits() {
        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_terminal);
        mPanel.setFirstSlider("Opacity · 40%", 40, 100);
        mPanel.setLegibility(mPanel.legibilityLabel(1), 1, true);
        mPanel.setSecondSlider("Blur · 12 dp", 12, 32);
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
    }

    @Test
    public void appearanceModeWithTheWallpapersToggleFits() {
        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_wallpaper);
        mPanel.setSoft("Soften wallpaper", true);
        mPanel.hideLegibility();
        mPanel.setSecondSlider("Dim · 30%", 30, 100);
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
    }

    @Test
    public void layoutModeFits() {
        mPanel.showLayoutMode();
        mPanel.setFloating(true);
        mPanel.setCorners("Corner radius · 22 dp", 22, 40);
        mPanel.setMargin("Margin · 6 dp", 6, 48);
        layOut(mPanel.measureFor(true, mWidthPx));
        assertFits();
    }

    /** Tapping an element does not change Appearance's height: Row B is always counted. */
    @Test
    public void appearanceHeightIsTheSameWithAndWithoutAnElementTapped() {
        mPanel.showAppearanceMode();
        int untapped = mPanel.measureFor(false, mWidthPx);
        mPanel.showRow2(R.string.appearance_editor_target_dock);
        mPanel.hideFirst();
        mPanel.hideLegibility();
        mPanel.setSecondSlider("Blur · 8 dp", 8, 32);
        assertEquals(untapped, mPanel.measureFor(false, mWidthPx));
        mPanel.showRow2(R.string.appearance_editor_target_terminal);
        mPanel.setFirstSlider("Opacity · 40%", 40, 100);
        mPanel.setLegibility(mPanel.legibilityLabel(2), 2, true);
        assertEquals(untapped, mPanel.measureFor(false, mWidthPx));
        mPanel.setDirty(false);
        assertEquals("Undo does not move the sheet either",
            untapped, mPanel.measureFor(false, mWidthPx));
    }

    private void layOut(int heightPx) {
        View root = mPanel.view();
        root.measure(View.MeasureSpec.makeMeasureSpec(mWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, mWidthPx, heightPx);
    }

    private void assertFits() {
        View root = mPanel.view();
        assertEdges(root, 0, root.getWidth());

        View pill = root.findViewById(R.id.appearance_editor_mode);
        View undo = root.findViewById(R.id.appearance_editor_undo);
        View done = root.findViewById(R.id.appearance_editor_done);
        assertEquals(View.VISIBLE, undo.getVisibility());
        assertTrue("the pill ends before Undo: " + pill.getRight() + " > " + undo.getLeft(),
            pill.getRight() <= undo.getLeft());
        assertTrue("Undo ends before Done", undo.getRight() <= done.getLeft());
        assertTrue("Done inside the sheet's padding",
            done.getRight() <= root.getWidth() - root.getPaddingRight());

        for (int id : new int[] {R.id.appearance_editor_mode_appearance,
                R.id.appearance_editor_mode_layout, R.id.appearance_editor_done}) {
            TextView button = root.findViewById(id);
            Layout layout = button.getLayout();
            assertNotNull(layout);
            assertEquals("\"" + button.getText() + "\" is whole", 0, layout.getEllipsisCount(0));
            assertEquals("\"" + button.getText() + "\" is on one line", 1, layout.getLineCount());
        }
    }

    /** No shown view's right edge runs past the panel's width. */
    private static void assertEdges(View view, int left, int panelWidth) {
        if (view.getVisibility() != View.VISIBLE)
            return;
        int right = left + view.getWidth();
        assertTrue(name(view) + " ends at " + right + " > " + panelWidth, right <= panelWidth);
        if (!(view instanceof ViewGroup))
            return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            assertEdges(child, left + child.getLeft(), panelWidth);
        }
    }

    private static String name(View view) {
        if (view.getId() == View.NO_ID)
            return view.getClass().getSimpleName();
        return view.getResources().getResourceEntryName(view.getId());
    }
}
