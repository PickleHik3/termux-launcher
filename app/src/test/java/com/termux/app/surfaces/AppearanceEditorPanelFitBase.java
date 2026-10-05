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
import org.robolectric.RuntimeEnvironment;

/**
 * The bottom area fits a phone of the subclass's width in both modes with Undo up: no view runs past the sheet's
 * edge, the mode pill's words are whole, and nothing on the top row overlaps. Native graphics,
 * so text is measured with real font metrics rather than one pixel per character.
 */
public abstract class AppearanceEditorPanelFitBase {

    private AppearanceEditorPanel mPanel;
    private int mWidthPx;

    /** The text size the subclass runs at; 1.3 is Android's Large. */
    protected float fontScale() {
        return 1f;
    }

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(fontScale());
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPanel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        mWidthPx = Math.round(themed.getResources().getConfiguration().screenWidthDp
            * themed.getResources().getDisplayMetrics().density);
        mPanel.setDirty(true);
    }

    @Test
    public void appearanceModeWithTheTerminalsFourControlsFits() {
        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_terminal);
        mPanel.setFirstSlider("Opacity · 40%", 40, 100);
        mPanel.setLegibility(mPanel.legibilityLabel(1), 1, true);
        mPanel.setThirdSlider("Grain · 14%", 14, 100);
        mPanel.setSecondSlider("Blur · 12 dp", 12, 32);
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
        mPanel.hideThird();
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
        // The controller rewrites only the label as a slider moves; the sheet keeps its height.
        mPanel.setMiddleLabel("Opacity · 34%");
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

    /**
     * A drag rewrites only the label, never the sheet's height: the label keeps the lines its
     * widest value needs from the start, so the slider stays where the finger put it.
     */
    @Test
    public void aLabelGrowingMidDragKeepsItsSliderInPlace() {
        mPanel.showLayoutMode();
        mPanel.setCorners("Corner radius · 4 dp", 4, 40);
        mPanel.setMargin("Margin · 0 dp", 0, 48);
        int height = mPanel.measureFor(true, mWidthPx);
        layOut(height);
        View corners = mPanel.view().findViewById(R.id.appearance_editor_corners);
        int top = corners.getTop();
        mPanel.setCornersLabel("Corner radius · 40 dp");
        mPanel.setMarginLabel("Margin · 48 dp");
        layOut(height);
        assertFits();
        assertEquals("the slider stays put", top, corners.getTop());
        assertEquals("the sheet keeps its height", height, mPanel.measureFor(true, mWidthPx));

        mPanel.showAppearanceMode();
        mPanel.showRow2(R.string.appearance_editor_target_all);
        mPanel.setFirstSlider("Blur · 0 dp", 0, 30);
        mPanel.setMiddleSlider("Opacity · 5%", 5, 100);
        mPanel.setSecondSlider("Grain · 0%", 0, 100);
        height = mPanel.measureFor(false, mWidthPx);
        layOut(height);
        View opacity = mPanel.view().findViewById(R.id.appearance_editor_cl_slider);
        top = opacity.getTop();
        mPanel.setFirstLabel("Blur · 30 dp");
        mPanel.setMiddleLabel("Opacity · 100%");
        mPanel.setSecondLabel("Grain · 100%");
        layOut(height);
        assertFits();
        assertEquals("the slider stays put", top, opacity.getTop());
        assertEquals("the sheet keeps its height", height, mPanel.measureFor(false, mWidthPx));
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

    /**
     * Layout mode with the keyboard selected: its type chips and Key radius in Row B's place, the
     * same height, nothing past the sheet's edge or over each other, the chips at their 48dp
     * targets and Key radius's words whole.
     */
    @Test
    public void layoutModeWithTheKeyboardsToolsFits() {
        mPanel.showLayoutMode();
        mPanel.setCorners("Corner radius · 40 dp", 40, 40);
        mPanel.setMargin("Margin · 48 dp", 48, 48);
        int closed = mPanel.measureFor(true, mWidthPx);
        int tallest = mPanel.measureTallest(true, mWidthPx);
        assertEquals("the anchor is Row B as it is", closed, tallest);
        TextView label = mPanel.view().findViewById(R.id.layout_editor_key_radius_label);
        label.setText("Key radius · 24 dp");
        mPanel.setKeyboardToolsShown(true);
        assertEquals("the sheet does not move", closed, mPanel.measureFor(true, mWidthPx));
        assertEquals(tallest, mPanel.measureTallest(true, mWidthPx));
        layOut(mPanel.measureFor(true, mWidthPx));
        assertFits();
        View root = mPanel.view();
        View tools = root.findViewById(R.id.layout_editor_keyboard_tools);
        View forms = root.findViewById(R.id.layout_editor_keyboard_forms);
        View slider = root.findViewById(R.id.layout_editor_key_radius);
        assertEquals(View.VISIBLE, tools.getVisibility());
        assertTrue("the tools inside the sheet's padding",
            tools.getRight() <= root.getWidth() - root.getPaddingRight());
        assertTrue("the tools stand above the sheet's bottom padding",
            tools.getBottom() <= root.getHeight() - root.getPaddingBottom());
        assertTrue("the chips end before Key radius starts", forms.getRight() <= slider.getLeft());
        int target = Math.round(48 * root.getResources().getDisplayMetrics().density);
        ViewGroup chips = (ViewGroup) forms;
        for (int i = 0; i < chips.getChildCount(); i++) {
            assertTrue("a 48dp touch target", chips.getChildAt(i).getHeight() >= target);
        }
        assertTrue("Key radius has room for its slider", slider.getWidth() >= target * 2);
        Layout layout = label.getLayout();
        assertNotNull(layout);
        assertEquals("\"" + label.getText() + "\" is whole", 0, layout.getEllipsisCount(0));
        mPanel.setKeyboardToolsShown(false);
        assertEquals(View.VISIBLE,
            root.findViewById(R.id.appearance_editor_corners).getVisibility());
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

    @Test
    public void sideInsetsKeepEveryControlInsideTheSheetAtThisWidth() {
        mPanel.showAppearanceMode();
        mPanel.setSideInsets(48, 24);
        layOut(mPanel.measureFor(false, mWidthPx));
        assertFits();
        View root = mPanel.view();
        assertTrue("content stands clear of the left inset", root.getPaddingLeft() >= 48);
        assertTrue("content stands clear of the right inset", root.getPaddingRight() >= 24);
    }

    @Test
    public void theChosenLookLabelIsSelectedAndKeepsItsFamily() {
        mPanel.showAppearanceMode();
        mPanel.setStop(2);
        layOut(mPanel.measureFor(false, mWidthPx));
        FrameLayout labels = mPanel.view().findViewById(R.id.appearance_editor_look_labels);
        int selected = 0;
        for (int i = 0; i < labels.getChildCount(); i++) {
            TextView label = (TextView) labels.getChildAt(i);
            if (label.isSelected()) selected++;
            assertNotNull("typeface kept", label.getTypeface());
            assertEquals("labels stay out of accessibility; the slider is the route",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, label.getImportantForAccessibility());
        }
        assertEquals(1, selected);
        assertTrue(labels.getChildAt(2).isSelected());
        assertTrue(((TextView) labels.getChildAt(2)).getTypeface().isBold());
    }

    @Test
    public void orientationAndStyleKeepTheOriginalBoundedSegmentStyle() {
        View root = mPanel.view();
        for (int id : new int[] {R.id.layout_editor_orientation_portrait,
                R.id.layout_editor_orientation_landscape, R.id.appearance_editor_style_docked,
                R.id.appearance_editor_style_floating}) {
            MaterialButton button = root.findViewById(id);
            assertTrue("outlined segment keeps its boundary", button.getStrokeWidth() > 0);
        }
        MaterialButton mode = root.findViewById(R.id.appearance_editor_mode_appearance);
        assertEquals("mode segment is the stroke-free tonal one", 0, mode.getStrokeWidth());
    }

    @Test
    public void theEyeOffControlHasATooltip() {
        View hidden = mPanel.view().findViewById(R.id.layout_editor_hidden);
        assertNotNull(hidden.getTooltipText());
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
        // One row when it fits; when side insets leave too little width, Undo and Done wrap to a
        // row of their own under the pill. Either way the pill and Undo never overlap.
        assertTrue("the pill ends before Undo, or Undo wraps under it: " + pill.getRight() + " > "
                + undo.getLeft(),
            pill.getRight() <= undo.getLeft() || pill.getBottom() <= undo.getTop());
        assertLabelsAboveTheirControls(root);

        assertEquals("Undo and Done share a row", undo.getTop() < done.getBottom()
            && done.getTop() < undo.getBottom(), true);
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

    /**
     * Each shown column label is whole (wrapping, never ellipsized) and ends above the control it
     * names, whichever label in the row wraps.
     */
    private static void assertLabelsAboveTheirControls(View root) {
        int[][] columns = {
            {R.id.appearance_editor_c1_label, R.id.appearance_editor_c1_slider,
                R.id.appearance_editor_c1_soft},
            {R.id.appearance_editor_cl_label, R.id.appearance_editor_legibility,
                R.id.appearance_editor_cl_slider},
            {R.id.appearance_editor_c3_label, R.id.appearance_editor_c3_slider},
            {R.id.appearance_editor_c2_label, R.id.appearance_editor_c2_slider,
                R.id.appearance_editor_c2_button},
            {R.id.appearance_editor_corners_label, R.id.appearance_editor_corners},
            {R.id.appearance_editor_margin_label, R.id.appearance_editor_margin},
        };
        for (int[] column : columns) {
            TextView label = root.findViewById(column[0]);
            if (!isShown(label, root))
                continue;
            Layout layout = label.getLayout();
            assertNotNull(name(label), layout);
            assertEquals("\"" + label.getText() + "\" is whole", 0,
                layout.getEllipsisCount(layout.getLineCount() - 1));
            String text = label.getText().toString();
            int value = text.indexOf(" \u00b7 ") + 3;
            if (value > 2 && value < text.length()) {
                assertEquals("\"" + text + "\" keeps its value on one line",
                    layout.getLineForOffset(value), layout.getLineForOffset(text.length() - 1));
            }
            assertTrue("\"" + label.getText() + "\" fits its own height: " + layout.getHeight()
                    + " > " + label.getHeight(),
                layout.getHeight() + label.getTotalPaddingTop() + label.getTotalPaddingBottom()
                    <= label.getHeight());
            for (int i = 1; i < column.length; i++) {
                View control = root.findViewById(column[i]);
                if (!isShown(control, root))
                    continue;
                assertTrue("\"" + label.getText() + "\" ends at " + label.getBottom()
                        + ", under the top of " + name(control) + " at " + control.getTop(),
                    label.getBottom() <= control.getTop());
                int bottom = control.getBottom();
                for (View v = (View) control.getParent(); v != root; v = (View) v.getParent())
                    bottom += v.getTop();
                assertTrue(name(control) + " ends at " + bottom + ", in the sheet's bottom padding",
                    bottom <= root.getHeight() - root.getPaddingBottom());
            }
        }
    }

    private static boolean isShown(View view, View root) {
        for (View v = view; v != null; v = (View) v.getParent()) {
            if (v.getVisibility() != View.VISIBLE)
                return false;
            if (v == root)
                return true;
        }
        return false;
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
