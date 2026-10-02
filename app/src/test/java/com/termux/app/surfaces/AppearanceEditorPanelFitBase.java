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

import com.google.android.material.button.MaterialButton;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;

/**
 * The bottom area fits a phone of the subclass's width in both modes with Undo up: no view runs past the sheet's
 * edge, the mode pill's words are whole, and nothing on the top row overlaps. Native graphics,
 * so text is measured with real font metrics rather than one pixel per character.
 */
public abstract class AppearanceEditorPanelFitBase {

    private AppearanceEditorPanel mPanel;
    private int mWidthPx;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPanel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        mWidthPx = Math.round(themed.getResources().getConfiguration().screenWidthDp
            * themed.getResources().getDisplayMetrics().density);
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

    /** The global row at Custom with nothing tapped: Blur, Opacity and Grain (item 13). */
    @Test
    public void appearanceModeWithTheGlobalRowFits() {
        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_all);
        mPanel.setFirstSlider("Blur · 30 dp", 30, 30);
        mPanel.setMiddleSlider("Opacity · 100%", 100, 100);
        mPanel.setSecondSlider("Grain · 100%", 100, 100);
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
        assertTrue(mPanel.measureFor(false, mWidthPx)
            <= mPanel.measureTallest(false, mWidthPx));
    }

    /** The keyboard's row: Blur and the "Keyboard theme" door, whole and inside the sheet. */
    @Test
    public void appearanceModeWithTheKeyboardsThemeDoorFits() {
        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_keyboard);
        mPanel.setFirstSlider("Blur · 12 dp", 12, 30);
        mPanel.hideLegibility();
        mPanel.setSecondButton(mPanel.view().getContext()
            .getString(R.string.appearance_editor_keyboard_theme));
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
        TextView door = mPanel.view().findViewById(R.id.appearance_editor_c2_button);
        assertEquals(View.VISIBLE, door.getVisibility());
        Layout layout = door.getLayout();
        assertNotNull(layout);
        assertEquals("\"" + door.getText() + "\" is whole", 0,
            layout.getEllipsisCount(layout.getLineCount() - 1));
        assertTrue("a 48dp touch target", door.getHeight()
            >= Math.round(48 * mPanel.view().getResources().getDisplayMetrics().density));
        assertTrue(mPanel.measureFor(false, mWidthPx)
            <= mPanel.measureTallest(false, mWidthPx));
    }

    /** Layout mode with the hidden tiles open: same height, nothing past the sheet's edge. */
    @Test
    public void layoutModeWithTheHiddenTilesOpenFits() {
        mPanel.showLayoutMode();
        mPanel.setCorners("Corner radius · 22 dp", 22, 40);
        mPanel.setMargin("Margin · 6 dp", 6, 48);
        int closed = mPanel.measureFor(true, mWidthPx);
        mPanel.setHiddenTilesOpen(true);
        assertEquals(closed, mPanel.measureFor(true, mWidthPx));
        layOut(mPanel.measureFor(true, mWidthPx));
        assertFits();
        View eyeOff = mPanel.view().findViewById(R.id.layout_editor_hidden);
        View highlight = mPanel.view().findViewById(R.id.layout_editor_hidden_highlight);
        assertTrue("eye-off inside the sheet's padding", eyeOff.getRight()
            <= mPanel.view().getWidth() - mPanel.view().getPaddingRight());
        assertTrue("the highlight stays inside the sheet", highlight.getRight()
            <= mPanel.view().getWidth());
        mPanel.setHiddenTilesOpen(false);
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

    /**
     * The sheet's height follows its content: Row B is GONE until an element is tapped, so the
     * untapped sheet is shorter, and every tapped element's is at most the tallest, which is
     * what the frame stands above (so the frame never moves when Row B comes and goes). Undo
     * does not move the sheet.
     */
    @Test
    public void appearanceHeightFollowsContentAndTheFrameAnchorIsFixed() {
        mPanel.showAppearanceMode();
        int untapped = mPanel.measureFor(false, mWidthPx);
        int tallest = mPanel.measureTallest(false, mWidthPx);
        assertTrue("untapped " + untapped + " < tallest " + tallest, untapped < tallest);
        assertEquals("the anchor does not depend on Row B's state",
            tallest, mPanel.measureTallest(false, mWidthPx));

        mPanel.showRow2(R.string.appearance_editor_target_dock);
        mPanel.hideFirst();
        mPanel.hideLegibility();
        mPanel.setSecondSlider("Blur · 8 dp", 8, 32);
        int dock = mPanel.measureFor(false, mWidthPx);
        assertTrue("tapped " + dock + " > untapped " + untapped, dock > untapped);
        assertTrue("tapped " + dock + " <= tallest " + tallest, dock <= tallest);
        assertEquals("the anchor is the same with Row B up",
            tallest, mPanel.measureTallest(false, mWidthPx));

        mPanel.showRow2(R.string.appearance_editor_target_terminal);
        mPanel.setFirstSlider("Opacity · 40%", 40, 100);
        mPanel.setLegibility(mPanel.legibilityLabel(2), 2, true);
        int terminal = mPanel.measureFor(false, mWidthPx);
        assertTrue("terminal " + terminal + " <= tallest " + tallest, terminal <= tallest);
        assertEquals(tallest, mPanel.measureTallest(false, mWidthPx));
        mPanel.setDirty(true);
        mPanel.setDirty(false);
        assertEquals("Undo does not move the sheet", terminal, mPanel.measureFor(false, mWidthPx));

        mPanel.hideRow2();
        assertEquals("Row B down: back to Row A alone", untapped,
            mPanel.measureFor(false, mWidthPx));
    }

    /** A text segment is its content plus the style's 12dp each side, not Material's 24dp. */
    @Test
    public void textSegmentsAreContentPlusTwelveDpEachSide() {
        mPanel.showAppearanceMode();
        layOut(mPanel.measureFor(false, mWidthPx));
        float density = mPanel.view().getResources().getDisplayMetrics().density;
        for (int id : new int[] {R.id.appearance_editor_mode_appearance,
                R.id.appearance_editor_mode_layout}) {
            MaterialButton segment = mPanel.view().findViewById(id);
            String measured = segment.getText() + ": paddingLeft=" + segment.getPaddingLeft()
                + " paddingRight=" + segment.getPaddingRight()
                + " minWidth=" + segment.getMinWidth()
                + " minimumWidth=" + segment.getMinimumWidth()
                + " iconSize=" + segment.getIconSize()
                + " inset=" + segment.getInsetLeft() + "/" + segment.getInsetRight()
                + " iconPadding=" + segment.getIconPadding()
                + " compoundPadding=" + segment.getCompoundDrawablePadding()
                + " text=" + segment.getLayout().getLineWidth(0)
                + " width=" + segment.getWidth();
            assertEquals(measured, Math.round(12 * density), segment.getPaddingLeft());
            assertEquals(measured, Math.round(12 * density), segment.getPaddingRight());
            float content = segment.getLayout().getLineWidth(0)
                + (segment.getIconSize() + segment.getIconPadding());
            float expected = content + 24 * density;
            assertTrue(measured + " expected about " + expected,
                segment.getWidth() <= Math.ceil(expected) + 2 * density);
        }
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
