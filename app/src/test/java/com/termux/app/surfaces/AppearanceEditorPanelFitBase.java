package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

import java.util.List;

/**
 * The bottom area fits a phone of the subclass's width in both modes: no view runs past the sheet's
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
    }

    /**
     * Every selection's Custom row at its widest legends: its sliders are whole columns inside the
     * sheet, 12dp apart, one height, each wider than the 28dp track (36dp at the least); each legend fits its track
     * (or is cut to it); the buttons are 48dp touch targets inside the sheet.
     */
    @Test
    public void everySelectionsCustomRowFits() {
        for (AppearanceLooks.Target target : targets()) {
            showControls(target);
            layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
            assertFits();
            List<AppearanceLooks.Control> expected = AppearanceLooks.controls(target);
            assertEquals(String.valueOf(target), expected, mPanel.shownControls());
            float density = mPanel.view().getResources().getDisplayMetrics().density;
            int track = Math.round(36 * density);
            int gap = Math.round(12 * density);
            LegendSlider previous = null;
            for (AppearanceLooks.Control control : expected) {
                LegendSlider slider = mPanel.sliderFor(control);
                assertNotNull(control.name(), slider);
                assertTrue(control + " is wider than its track: " + slider.getWidth(),
                    slider.getWidth() >= track);
                assertEquals("one column height", slider.getResources().getDimensionPixelSize(
                    R.dimen.appearance_editor_slider_length), slider.getHeight());
                if (previous != null)
                    assertEquals("12dp between columns", gap, Math.abs(
                        slider.getLeft() - previous.getRight()), 1);
                else
                    assertEquals("the first column starts at the content edge", 0,
                        slider.getLeft());
                previous = slider;
                assertLegendFits(slider, control);
            }
            for (AppearanceLooks.Door door : AppearanceLooks.doors(target)) {
                View button = doorView(door);
                assertEquals(door.name(), View.VISIBLE, button.getVisibility());
                assertTrue(door + " is a 48dp target: " + button.getHeight(),
                    button.getHeight() >= Math.round(48 * density));
                if (button instanceof MaterialButton) {
                    Layout layout = ((MaterialButton) button).getLayout();
                    assertNotNull(layout);
                    assertEquals("\"" + ((MaterialButton) button).getText() + "\" is whole", 0,
                        layout.getEllipsisCount(layout.getLineCount() - 1));
                } else {
                    assertPillLinesFit(door);
                }
            }
            View name = mPanel.view().findViewById(R.id.appearance_editor_row2_name);
            if (target == AppearanceLooks.Target.TERMINAL) {
                assertEquals("the terminal's pills own the heading row: no name", View.GONE,
                    name.getVisibility());
                assertPillsShareTheRow();
            } else {
                assertEquals(String.valueOf(target) + " keeps its name", View.VISIBLE,
                    name.getVisibility());
            }
        }
    }

    /**
     * Below API 33 the terminal has no Terminal effect: Cursor trail alone takes the whole row,
     * still with no name, and its lines fit.
     */
    @Test
    public void theCursorTrailAloneTakesTheWholeRow() {
        showControls(AppearanceLooks.Target.TERMINAL);
        mPanel.setDoors(java.util.Collections.singletonList(AppearanceLooks.Door.TRAIL));
        layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
        assertFits();
        View root = mPanel.view();
        View head = root.findViewById(R.id.appearance_editor_row2_head);
        View trail = root.findViewById(R.id.appearance_editor_trail);
        assertEquals(View.GONE, root.findViewById(R.id.appearance_editor_effect).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.appearance_editor_row2_name).getVisibility());
        assertEquals("from the content's start", 0, trail.getLeft());
        assertEquals("to its end", head.getWidth(), trail.getRight(), 1);
        assertPillLinesFit(AppearanceLooks.Door.TRAIL);
    }

    /** The terminal's two pills: one row, equal widths, the content's full width, 8dp apart. */
    private void assertPillsShareTheRow() {
        View root = mPanel.view();
        View head = root.findViewById(R.id.appearance_editor_row2_head);
        View trail = root.findViewById(R.id.appearance_editor_trail);
        View effect = root.findViewById(R.id.appearance_editor_effect);
        float density = root.getResources().getDisplayMetrics().density;
        assertEquals("equal widths", trail.getWidth(), effect.getWidth(), 1);
        assertEquals("one row", trail.getTop(), effect.getTop());
        assertEquals("the row starts with Cursor trail", 0, trail.getLeft());
        assertEquals("8dp between them", Math.round(8 * density), effect.getLeft() - trail.getRight(), 1);
        assertEquals("the row ends with Terminal effect", head.getWidth(), effect.getRight(), 1);
    }

    /**
     * A pill's heading is whole (never ellipsized) and each of its two lines is one line,
     * inside the pill.
     */
    private void assertPillLinesFit(AppearanceLooks.Door door) {
        View root = mPanel.view();
        boolean trail = door == AppearanceLooks.Door.TRAIL;
        View pill = doorView(door);
        TextView title = root.findViewById(trail ? R.id.appearance_editor_trail_title
            : R.id.appearance_editor_effect_title);
        TextView value = root.findViewById(trail ? R.id.appearance_editor_trail_value
            : R.id.appearance_editor_effect_value);
        for (TextView line : new TextView[] {title, value}) {
            Layout layout = line.getLayout();
            assertNotNull(name(line), layout);
            assertEquals(name(line) + " is one line", 1, layout.getLineCount());
            assertTrue(name(line) + " fits its height", layout.getHeight()
                + line.getTotalPaddingTop() + line.getTotalPaddingBottom() <= line.getHeight());
        }
        assertEquals("\"" + title.getText() + "\" is whole", 0, title.getLayout().getEllipsisCount(0));
        assertTrue("the heading stands above the value",
            ((View) value.getParent()).getTop() >= title.getBottom());
        assertTrue(door + " is no taller than the heading row",
            pill.getHeight() <= root.findViewById(R.id.appearance_editor_row2_head).getHeight());
    }

    /**
     * The shared preview contract on this phone: one sheet reserve for every page and every stop
     * (Look at a stop, the Custom row for every selection, Layout, Icon pack), so the preview is
     * the same rect everywhere, 8dp under the page bar (whose height is the contract's) and 8dp
     * over the sheet, never below the least scale. Where the Custom row is taller than the
     * reserve it scrolls inside the sheet, its heading at the top and its sliders whole.
     */
    @Test
    public void everyPageAndStopSharesOnePreviewArea() {
        android.content.res.Resources res = mPanel.view().getResources();
        float density = res.getDisplayMetrics().density;
        assertEquals(Math.round(AppearancePreviewArea.BAR_DP * density),
            res.getDimensionPixelSize(R.dimen.appearance_page_bar_height));
        int window = Math.round(res.getConfiguration().screenHeightDp * density);
        int status = Math.round(24 * density);
        int container = window - status;
        int top = AppearancePreviewArea.topPx(status, density);
        int resting = 0;
        for (EditorMode page : EditorMode.values())
            resting = Math.max(resting, mPanel.measureResting(page, mWidthPx));
        int custom = mPanel.measureTallest(EditorMode.LOOK, mWidthPx);
        int reserve = AppearancePreviewArea.reservePx(resting, custom,
            AppearancePreviewArea.sheetCapPx(window, top, container, 0, 0, density));
        float shared = AppearancePreviewArea.scale(container, 0, 0, top,
            AppearancePreviewArea.bottomPx(window, reserve, density));
        assertTrue("the preview stays readable: " + shared,
            shared >= AppearanceEditorFrame.MIN_SCALE - 1e-3f);

        java.util.List<Integer> sheets = new java.util.ArrayList<>();
        for (EditorMode page : EditorMode.values())
            sheets.add(mPanel.measureResting(page, mWidthPx));
        for (AppearanceLooks.Target target : targets()) {
            showControls(target);
            int content = mPanel.measureFor(EditorMode.LOOK, mWidthPx);
            sheets.add(Math.min(content, reserve));
            layOut(reserve);
            View row2 = mPanel.view().findViewById(R.id.appearance_editor_row2);
            View sliders = mPanel.view().findViewById(R.id.appearance_editor_sliders);
            assertEquals(target + ": Row B shows", View.VISIBLE, row2.getVisibility());
            assertTrue(target + ": Row B stays inside the sheet", row2.getBottom()
                <= mPanel.view().getHeight() - mPanel.view().getPaddingBottom());
            assertEquals(target + ": the sliders keep their length",
                res.getDimensionPixelSize(R.dimen.appearance_editor_slider_length),
                sliders.getHeight());
            assertEquals(target + ": scrolls only where it is taller", content > reserve,
                row2.canScrollVertically(1));
        }
        for (int content : sheets) {
            int sheet = AppearancePreviewArea.sheetPx(reserve, content);
            assertEquals("one sheet height", reserve, sheet);
            assertEquals("one preview", shared, AppearancePreviewArea.scale(container, 0, 0, top,
                AppearancePreviewArea.bottomPx(window, sheet, density)), 0f);
        }
    }

    /** The global set's six columns show six legends reading upward. */
    @Test
    public void theGlobalSetHasSixColumnsAndNoButtons() {
        showControls(null);
        layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
        assertEquals(6, mPanel.shownControls().size());
        assertTrue(mPanel.shownDoors().isEmpty());
        assertFits();
    }

    /** A hidden column leaves no gap: the remaining ones share the width. */
    @Test
    public void fewerColumnsShareTheWidthEqually() {
        showControls(AppearanceLooks.Target.STATUS);
        layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
        LegendSlider a = mPanel.sliderFor(AppearanceLooks.Control.BLUR);
        LegendSlider b = mPanel.sliderFor(AppearanceLooks.Control.GRAIN);
        LegendSlider c = mPanel.sliderFor(AppearanceLooks.Control.OPACITY);
        LegendSlider d = mPanel.sliderFor(AppearanceLooks.Control.TINT);
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertNotNull(d);
        assertEquals(a.getWidth(), b.getWidth(), 1);
        assertEquals(b.getWidth(), c.getWidth(), 1);
        assertEquals(c.getWidth(), d.getWidth(), 1);
        View row = mPanel.view().findViewById(R.id.appearance_editor_sliders);
        assertEquals("the last column ends at the content edge", row.getWidth(), d.getRight(), 1);
    }

    /**
     * A drag rewrites only the legend, never the sheet's height or where a slider stands: the
     * columns are fixed, and the legend is drawn inside them.
     */
    @Test
    public void aLegendChangingMidDragMovesNothing() {
        showControls(null);
        int height = mPanel.measureFor(EditorMode.LOOK, mWidthPx);
        layOut(height);
        LegendSlider blur = mPanel.sliderFor(AppearanceLooks.Control.BLUR);
        int left = blur.getLeft();
        int top = blur.getTop();
        mPanel.setSliderValue(AppearanceLooks.Control.BLUR, 48);
        layOut(height);
        assertEquals(left, blur.getLeft());
        assertEquals(top, blur.getTop());
        assertEquals("the sheet keeps its height", height, mPanel.measureFor(EditorMode.LOOK, mWidthPx));
        assertEquals(48f, blur.getValue(), 0f);
    }

    /** Layout mode's labels keep the lines their widest value needs, so a drag moves nothing. */
    @Test
    public void aLabelGrowingMidDragKeepsItsSliderInPlace() {
        mPanel.showLayoutMode();
        mPanel.setCorners("Corner radius · 4 dp", 4, 40);
        mPanel.setMargin("Margin · 0 dp", 0, 48);
        int height = mPanel.measureFor(EditorMode.LAYOUT, mWidthPx);
        layOut(height);
        View corners = mPanel.view().findViewById(R.id.appearance_editor_corners);
        int top = corners.getTop();
        mPanel.setCornersLabel("Corner radius · 40 dp");
        mPanel.setMarginLabel("Margin · 48 dp");
        layOut(height);
        assertFits();
        assertEquals("the slider stays put", top, corners.getTop());
        assertEquals("the sheet keeps its height", height, mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
    }

    /** Layout mode with the hidden tiles open: same height, nothing past the sheet's edge. */
    @Test
    public void layoutModeWithTheHiddenTilesOpenFits() {
        mPanel.showLayoutMode();
        mPanel.setCorners("Corner radius · 22 dp", 22, 40);
        mPanel.setMargin("Margin · 6 dp", 6, 48);
        int closed = mPanel.measureFor(EditorMode.LAYOUT, mWidthPx);
        mPanel.setHiddenTilesOpen(true);
        assertEquals(closed, mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
        layOut(mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
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
        int closed = mPanel.measureFor(EditorMode.LAYOUT, mWidthPx);
        int tallest = mPanel.measureTallest(EditorMode.LAYOUT, mWidthPx);
        assertEquals("the anchor is Row B as it is", closed, tallest);
        TextView label = mPanel.view().findViewById(R.id.layout_editor_key_radius_label);
        label.setText("Key radius · 24 dp");
        mPanel.setKeyboardToolsShown(true);
        assertEquals("the sheet does not move", closed, mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
        assertEquals(tallest, mPanel.measureTallest(EditorMode.LAYOUT, mWidthPx));
        layOut(mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
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
        layOut(mPanel.measureFor(EditorMode.LAYOUT, mWidthPx));
        assertFits();
    }

    /**
     * The sheet's height follows its content: Row B is GONE until the Custom stop shows it, so the
     * sheet without it is shorter; every selection's Row B is the same height (a 48dp heading row
     * over sliders of one length), which is the tallest, so the frame never moves when the
     * selection changes.
     */
    @Test
    public void appearanceHeightFollowsContentAndTheFrameAnchorIsFixed() {
        mPanel.showAppearanceMode();
        int lookStop = mPanel.measureFor(EditorMode.LOOK, mWidthPx);
        int tallest = mPanel.measureTallest(EditorMode.LOOK, mWidthPx);
        assertTrue("look stop " + lookStop + " < tallest " + tallest, lookStop < tallest);
        assertEquals("the anchor does not depend on Row B's state",
            tallest, mPanel.measureTallest(EditorMode.LOOK, mWidthPx));

        for (AppearanceLooks.Target target : targets()) {
            showControls(target);
            assertEquals("every selection is one height: " + target, tallest,
                mPanel.measureFor(EditorMode.LOOK, mWidthPx));
            assertEquals(tallest, mPanel.measureTallest(EditorMode.LOOK, mWidthPx));
        }

        mPanel.hideRow2();
        assertEquals("Row B down: back to Row A alone", lookStop,
            mPanel.measureFor(EditorMode.LOOK, mWidthPx));
    }

    @Test
    public void sideInsetsKeepEveryControlInsideTheSheetAtThisWidth() {
        mPanel.showAppearanceMode();
        mPanel.setSideInsets(48, 24);
        layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
        assertFits();
        View root = mPanel.view();
        assertTrue("content stands clear of the left inset", root.getPaddingLeft() >= 48);
        assertTrue("content stands clear of the right inset", root.getPaddingRight() >= 24);
    }

    @Test
    public void theChosenLookLabelIsSelectedAndKeepsItsFamily() {
        mPanel.showAppearanceMode();
        mPanel.setStop(2);
        layOut(mPanel.measureFor(EditorMode.LOOK, mWidthPx));
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
    }

    @Test
    public void theEyeOffControlHasATooltip() {
        View hidden = mPanel.view().findViewById(R.id.layout_editor_hidden);
        assertNotNull(hidden.getTooltipText());
    }

    /**
     * Icons mode: the sheet is the lent content and nothing else (the tile row and the switch
     * card), as tall as that needs, shorter than Look's Custom row, with the other modes' rows
     * gone, and the switch's words whole inside the sheet.
     */
    @Test
    public void iconsModeShowsOnlyItsContentAtItsOwnHeight() {
        android.content.Context context = mPanel.view().getContext();
        View content = android.view.LayoutInflater.from(context)
            .inflate(R.layout.icon_pack_page, null, false);
        ViewGroup tiles = content.findViewById(R.id.icon_pack_tiles);
        for (int i = 0; i < 5; i++) {
            View tile = android.view.LayoutInflater.from(context)
                .inflate(R.layout.icon_pack_tile, tiles, false);
            ((TextView) tile.findViewById(R.id.icon_pack_tile_label))
                .setText("A Pack With A Rather Long Name");
            tiles.addView(tile);
        }
        mPanel.setIconsContent(content);
        mPanel.showIconsMode();
        assertEquals(EditorMode.ICONS, mPanel.mode());
        int height = mPanel.measureFor(EditorMode.ICONS, mWidthPx);
        assertEquals("one height", height, mPanel.measureTallest(EditorMode.ICONS, mWidthPx));
        float density = context.getResources().getDisplayMetrics().density;
        assertTrue("the tiles and the 64dp switch card at least: " + height,
            height >= Math.round(128 * density));
        assertTrue("shorter than Look's Custom row: " + height,
            height < mPanel.measureTallest(EditorMode.LOOK, mWidthPx));
        layOut(height);
        View root = mPanel.view();
        assertEquals(View.VISIBLE, root.findViewById(R.id.appearance_editor_icons_slot).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.appearance_editor_look).getVisibility());
        assertEquals(View.GONE, root.findViewById(R.id.layout_editor_orientation).getVisibility());
        View slot = root.findViewById(R.id.appearance_editor_icons_slot);
        assertTrue("the slot is inside the sheet", slot.getRight() <= mWidthPx);
        View scroll = content.findViewById(R.id.icon_pack_tiles_scroll);
        assertTrue("the tile row stays inside the slot (it scrolls)", scroll.getRight() <= slot.getWidth());
        TextView pinned = content.findViewById(R.id.icon_pack_pinned_only);
        assertTrue("the switch is inside the slot", pinned.getRight() <= slot.getWidth());
        Layout layout = pinned.getLayout();
        assertNotNull(layout);
        assertEquals("the switch's words are whole", 0,
            layout.getEllipsisCount(layout.getLineCount() - 1));
        assertTrue("nothing runs into the sheet's bottom padding",
            slot.getBottom() <= root.getHeight() - root.getPaddingBottom());
        // The mode leaves nothing behind.
        mPanel.showAppearanceMode();
        assertEquals(View.GONE, root.findViewById(R.id.appearance_editor_icons_slot).getVisibility());
    }

    private static java.util.List<AppearanceLooks.Target> targets() {
        java.util.List<AppearanceLooks.Target> out = new java.util.ArrayList<>();
        out.add(null);
        out.addAll(java.util.Arrays.asList(AppearanceLooks.Target.values()));
        return out;
    }

    private static int nameOfTarget(AppearanceLooks.Target target) {
        if (target == null)
            return R.string.appearance_editor_target_all;
        switch (target) {
            case STATUS: return R.string.appearance_editor_target_status;
            case TERMINAL: return R.string.appearance_editor_target_terminal;
            case DOCK: return R.string.appearance_editor_target_dock;
            default: return R.string.appearance_editor_target_keyboard;
        }
    }

    /** The widest legend each control can show, so the row is fitted at its worst. */
    private String widest(AppearanceLooks.Control control) {
        android.content.Context context = mPanel.view().getContext();
        switch (control) {
            case BLUR: return context.getString(R.string.appearance_editor_blur, 48);
            case GRAIN: return context.getString(R.string.appearance_editor_grain, 100);
            case OPACITY: return context.getString(R.string.appearance_editor_opacity, 100);
            case TINT: return context.getString(R.string.appearance_editor_tint, 100);
            case MARGIN: return context.getString(R.string.appearance_editor_margin, 48);
            case CORNER_RADIUS: return context.getString(R.string.appearance_editor_corners, 40);
            case KEY_RADIUS: return context.getString(R.string.appearance_editor_key_corners, 24);
            case KEY_SPACING: return context.getString(R.string.appearance_editor_key_spacing, "8.0");
            case ICON_SIZE: return context.getString(R.string.appearance_editor_icon_size, 48);
            case APP_ICONS: return context.getString(R.string.appearance_editor_app_icons, 10);
            default: return context.getString(R.string.appearance_editor_legibility_unavailable);
        }
    }

    /** The Custom row for a selection, as the controller states it, at the widest legends. */
    private void showControls(AppearanceLooks.Target target) {
        mPanel.showAppearanceMode();
        mPanel.showRow2(nameOfTarget(target));
        java.util.List<AppearanceEditorPanel.SliderState> states = new java.util.ArrayList<>();
        for (AppearanceLooks.Control control : AppearanceLooks.controls(target)) {
            final String legend = widest(control);
            states.add(new AppearanceEditorPanel.SliderState(control, control.max,
                control != AppearanceLooks.Control.CONTRAST, value -> legend));
        }
        mPanel.setSliders(states);
        mPanel.setDoors(AppearanceLooks.doors(target));
        mPanel.setTerminalLooks("default", "none");
    }

    private View doorView(AppearanceLooks.Door door) {
        View root = mPanel.view();
        switch (door) {
            case KEYBOARD_THEME: return root.findViewById(R.id.appearance_editor_door_keyboard_theme);
            case CLOCK: return root.findViewById(R.id.appearance_editor_door_clock);
            case TRAIL: return root.findViewById(R.id.appearance_editor_trail);
            default: return root.findViewById(R.id.appearance_editor_effect);
        }
    }

    /** Draws the slider and holds the legend it fitted against the track it has. */
    private static void assertLegendFits(LegendSlider slider, AppearanceLooks.Control control) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, slider.getWidth()),
            Math.max(1, slider.getHeight()), Bitmap.Config.ARGB_8888);
        slider.draw(new Canvas(bitmap));
        assertTrue(control + " legend \"" + slider.shownLegend() + "\" fits its track",
            slider.legendFits());
        assertTrue("the legend keeps a name", slider.shownLegend().length() > 0);
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

        assertNull("Undo lives in the page bar, not the sheet",
            root.findViewById(R.id.appearance_page_undo));
        assertLabelsAboveTheirControls(root);
    }

    /**
     * Each shown column label is whole (wrapping, never ellipsized) and ends above the control it
     * names, whichever label in the row wraps.
     */
    private static void assertLabelsAboveTheirControls(View root) {
        int[][] columns = {
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
