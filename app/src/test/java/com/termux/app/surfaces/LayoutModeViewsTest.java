package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Layout mode lives in the one editor (SPEC §3.5, layout editor v2): its canvas, move control and
 * keyboard tools in the frame ({@code layout_editor_frame}), its orientation toggle, eye-off and
 * hidden tiles in the bottom area ({@code appearance_editor_panel}). The hide zone and the trash
 * are gone. {@code SurfaceEditorController} looks each view up by id to
 * lend it to {@code LayoutEditorController}; a lost id would leave Layout mode with nothing to
 * draw into and no crash to say so. These inflate the real layouts and pin every id it asks for.
 *
 * <p>Replaces EditorShellHeaderIdTest, which pinned the ids of the standalone Layout sheet
 * ({@code layout_editor.xml}) that this editor retired.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class LayoutModeViewsTest {

    private View inflate(int layoutRes) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout host = new FrameLayout(themed);
        return LayoutInflater.from(themed).inflate(layoutRes, host, false);
    }

    @Test
    public void theFrameCarriesTheCanvasAndTheMoveControl() {
        View frame = inflate(R.layout.layout_editor_frame);
        assertTrue("the frame is what the canvas stands in", frame instanceof FrameLayout);
        assertTrue(frame.findViewById(R.id.layout_editor_canvas) instanceof LayoutCanvasView);
        assertEquals("the keyboard's tools are the sheet's now, not a card over the canvas",
            null, frame.findViewById(R.id.layout_editor_keyboard_tools));
        // The move control: a 48dp icon button, gone until a bar is selected (items 4 and 5).
        View move = frame.findViewById(R.id.layout_editor_move);
        assertTrue(move instanceof MaterialButton);
        assertEquals(View.GONE, move.getVisibility());
        assertNotNull(move.getContentDescription());
        float density = frame.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(48 * density), move.getLayoutParams().width);
        assertEquals(Math.round(48 * density), move.getLayoutParams().height);
        assertEquals("the canvas is spatial: pinned left to right", View.LAYOUT_DIRECTION_LTR,
            frame.getLayoutDirection());
    }

    /** The hide zone is gone: nothing in the editor's layouts carries it any more. */
    @Test
    public void thereIsNoHideZoneAndNoTrash() {
        android.content.res.Resources resources = inflate(R.layout.appearance_editor_panel)
            .getResources();
        String pkg = resources.getResourcePackageName(R.layout.appearance_editor_panel);
        assertEquals(0, resources.getIdentifier("layout_editor_hide_zone", "layout", pkg));
        assertEquals(0, resources.getIdentifier("layout_editor_tray_trash", "id", pkg));
        assertEquals(0, resources.getIdentifier("layout_editor_hide_zone_hint", "string", pkg));
    }

    @Test
    public void theBottomAreaCarriesLayoutModesRowsAndTheyStartHidden() {
        View panel = inflate(R.layout.appearance_editor_panel);
        assertTrue("one ConstraintLayout, no weighted rows", panel instanceof ConstraintLayout);
        View group = panel.findViewById(R.id.appearance_editor_layout_group);
        assertTrue(group instanceof Group);
        assertEquals("Appearance is the mode a plain open shows", View.GONE, group.getVisibility());
        assertTrue(panel.findViewById(R.id.appearance_editor_appearance_group) instanceof Group);
        View orientation = panel.findViewById(R.id.layout_editor_orientation);
        assertTrue(orientation instanceof MaterialButtonToggleGroup);
        assertEquals("portrait and landscape",
            2, ((MaterialButtonToggleGroup) orientation).getChildCount());
        assertNotNull(panel.findViewById(R.id.layout_editor_orientation_portrait));
        assertNotNull(panel.findViewById(R.id.layout_editor_orientation_landscape));
        assertTrue("eye-off is an icon button: the drop target, the tiles' toggle, the badge's anchor",
            panel.findViewById(R.id.layout_editor_hidden) instanceof MaterialButton);
        View highlight = panel.findViewById(R.id.layout_editor_hidden_highlight);
        assertNotNull(highlight);
        float density = panel.getResources().getDisplayMetrics().density;
        assertTrue("the highlight is larger than the 48dp target",
            highlight.getLayoutParams().width > Math.round(48 * density));
        assertEquals("never GONE: it must not move anything", View.INVISIBLE,
            highlight.getVisibility());
        assertTrue(panel.findViewById(R.id.layout_editor_hidden_tile_group) instanceof ChipGroup);
        // Layout mode also carries what no Look sets (2026-10-01): Style, Corners and Margin.
        View style = panel.findViewById(R.id.appearance_editor_style);
        assertTrue(style instanceof MaterialButtonToggleGroup);
        assertEquals("docked and floating",
            2, ((MaterialButtonToggleGroup) style).getChildCount());
        View corners = panel.findViewById(R.id.appearance_editor_corners);
        View margin = panel.findViewById(R.id.appearance_editor_margin);
        assertTrue(corners instanceof Slider);
        assertTrue(margin instanceof Slider);
        assertEquals(40f, ((Slider) corners).getValueTo(), 0f);
        assertEquals(48f, ((Slider) margin).getValueTo(), 0f);
        assertNotNull(panel.findViewById(R.id.appearance_editor_corners_label));
        assertNotNull(panel.findViewById(R.id.appearance_editor_margin_label));
        // The mode pill, Undo and Done are the page bar's, not the sheet's.
        assertEquals("the pill left the sheet for the page bar", 0,
            panel.getResources().getIdentifier("appearance_editor_mode_layout", "id",
                panel.getContext().getPackageName()));
        assertNull(panel.findViewById(R.id.appearance_page_undo));
        assertNull(panel.findViewById(R.id.appearance_page_done));
    }

    /** Row B holds five vertical LegendSliders in one row, apart from the heading row. */
    @Test
    public void rowTwoCarriesSixVerticalLegendSliders() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View row2 = panel.findViewById(R.id.appearance_editor_row2);
        View sliders = panel.findViewById(R.id.appearance_editor_sliders);
        assertTrue(isInside(sliders, row2));
        int[] ids = {R.id.appearance_editor_slider_0, R.id.appearance_editor_slider_1,
            R.id.appearance_editor_slider_2, R.id.appearance_editor_slider_3,
            R.id.appearance_editor_slider_4, R.id.appearance_editor_slider_5};
        for (int id : ids) {
            View slider = panel.findViewById(id);
            assertTrue(slider instanceof LegendSlider);
            assertTrue("vertical", ((LegendSlider) slider).isVertical());
            assertTrue(isInside(slider, sliders));
            assertEquals("hidden until the controller shows a set", View.GONE,
                slider.getVisibility());
        }
        assertNotNull(panel.findViewById(R.id.appearance_editor_row2_name));
    }

    /** The buttons are in the heading row, hidden until the selection shows them. */
    @Test
    public void rowTwoCarriesItsButtonsInTheHeadingRowHidden() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View head = panel.findViewById(R.id.appearance_editor_row2_head);
        for (int id : new int[] {R.id.appearance_editor_door_keyboard_theme,
                R.id.appearance_editor_door_clock, R.id.appearance_editor_trail,
                R.id.appearance_editor_effect}) {
            View button = panel.findViewById(id);
            assertTrue(button instanceof MaterialButton);
            assertTrue(isInside(button, head));
            assertEquals(View.GONE, button.getVisibility());
        }
        assertEquals("the soften toggle and the wallpaper's row are gone", 0,
            panel.getResources().getIdentifier("appearance_editor_row2_barrier", "id",
                panel.getContext().getPackageName()));
    }

    /**
     * The two modes are two Groups in one layout: a switch shows one and hides the other, and
     * Corners and Margin stay up under both Styles (SPEC section 3.7), so Style never moves the
     * sheet.
     */
    @Test
    public void aModeSwitchSwapsTheGroupsAndStyleLeavesCornersAndMarginUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        View root = panel.view();
        View look = root.findViewById(R.id.appearance_editor_look);
        View corners = root.findViewById(R.id.appearance_editor_corners);
        View margin = root.findViewById(R.id.appearance_editor_margin);

        assertEquals(View.VISIBLE, look.getVisibility());
        assertEquals(View.GONE, corners.getVisibility());

        panel.showLayoutMode();
        assertTrue(panel.isLayoutMode());
        assertEquals(View.GONE, look.getVisibility());
        assertEquals(View.VISIBLE, corners.getVisibility());
        assertEquals(View.VISIBLE, margin.getVisibility());

        MaterialButtonToggleGroup style = root.findViewById(R.id.appearance_editor_style);
        panel.setFloating(true);
        assertEquals(R.id.appearance_editor_style_floating, style.getCheckedButtonId());
        assertEquals(View.VISIBLE, corners.getVisibility());
        panel.setFloating(false);
        assertEquals(R.id.appearance_editor_style_docked, style.getCheckedButtonId());
        assertEquals("shown under Docked too", View.VISIBLE, corners.getVisibility());
        assertEquals(View.VISIBLE, margin.getVisibility());

        panel.showAppearanceMode();
        assertFalse(panel.isLayoutMode());
        assertEquals(View.VISIBLE, look.getVisibility());
        assertEquals(View.GONE, corners.getVisibility());
    }

    /** Row B is GONE until the Custom stop shows it, and keeps its state across Layout mode. */
    @Test
    public void rowTwoIsGoneUntilShownAndKeepsItsStateAcrossLayoutMode() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        View row2 = panel.view().findViewById(R.id.appearance_editor_row2);
        assertEquals(View.GONE, row2.getVisibility());

        panel.showRow2(R.string.appearance_editor_target_dock);
        java.util.List<AppearanceEditorPanel.SliderState> states = new java.util.ArrayList<>();
        for (AppearanceLooks.Control control
                : AppearanceLooks.controls(AppearanceLooks.Target.DOCK)) {
            states.add(new AppearanceEditorPanel.SliderState(control, control.min, true,
                value -> control.name()));
        }
        panel.setSliders(states);
        LegendSlider blur = panel.sliderFor(AppearanceLooks.Control.BLUR);
        assertNotNull(blur);
        assertTrue(panel.isRow2Shown());
        assertEquals(View.VISIBLE, row2.getVisibility());
        assertTrue(blur.isEnabled());

        panel.showLayoutMode();
        assertTrue("kept while Layout is shown", panel.isRow2Shown());
        assertEquals(View.GONE, row2.getVisibility());
        panel.showAppearanceMode();
        assertTrue(panel.isRow2Shown());
        assertEquals(View.VISIBLE, row2.getVisibility());

        panel.hideRow2();
        assertFalse(panel.isRow2Shown());
        assertEquals(View.GONE, row2.getVisibility());
        assertFalse("a hidden Row B takes no touches", blur.isEnabled());
    }

    /** The keyboard's tools are in the sheet: its type chips and Key radius, gone until selected. */
    @Test
    public void theSheetCarriesTheKeyboardsTools() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View forms = panel.findViewById(R.id.layout_editor_keyboard_forms);
        assertTrue(forms instanceof ChipGroup);
        assertEquals("docked, floating, split", 3, ((ChipGroup) forms).getChildCount());
        assertNotNull(panel.findViewById(R.id.layout_editor_keyboard_form_docked));
        assertNotNull(panel.findViewById(R.id.layout_editor_keyboard_form_floating));
        assertNotNull(panel.findViewById(R.id.layout_editor_keyboard_form_split));
        // Key radius moved here from Look mode, beside the type chips (DECISIONS item 6).
        View tools = panel.findViewById(R.id.layout_editor_keyboard_tools);
        View radius = panel.findViewById(R.id.layout_editor_key_radius);
        assertTrue(radius instanceof Slider);
        assertTrue("Key radius stands with the chips", isInside(radius, tools));
        assertTrue(isInside(forms, tools));
        assertEquals("gone until the keyboard is selected", View.GONE, tools.getVisibility());
    }

    /** The keyboard's tools take Row B's place while it is selected; the sheet keeps its height. */
    @Test
    public void theKeyboardsToolsStandInRowBsPlaceWithoutMovingTheSheet() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        View root = panel.view();
        int width = Math.round(360 * root.getResources().getDisplayMetrics().density);
        panel.showLayoutMode();
        int closed = panel.measureFor(EditorMode.LAYOUT, width);
        View tools = root.findViewById(R.id.layout_editor_keyboard_tools);
        View corners = root.findViewById(R.id.appearance_editor_corners);

        panel.setKeyboardToolsShown(true);
        assertTrue(panel.isKeyboardToolsShown());
        assertEquals(View.VISIBLE, tools.getVisibility());
        assertEquals("kept for its height, not shown", View.INVISIBLE, corners.getVisibility());
        assertFalse(corners.isEnabled());
        assertEquals("the sheet does not move", closed, panel.measureFor(EditorMode.LAYOUT, width));
        assertEquals(closed, panel.measureTallest(EditorMode.LAYOUT, width));

        panel.setHiddenTilesOpen(true);
        assertEquals("the tiles win while both would", View.GONE, tools.getVisibility());
        panel.setHiddenTilesOpen(false);
        assertEquals(View.VISIBLE, tools.getVisibility());

        panel.showAppearanceMode();
        assertEquals("Appearance never shows them", View.GONE, tools.getVisibility());
        panel.showLayoutMode();
        panel.setKeyboardToolsShown(false);
        assertEquals(View.GONE, tools.getVisibility());
        assertEquals("the row comes back", View.VISIBLE, corners.getVisibility());
        assertTrue(corners.isEnabled());
    }

    /** The hidden tiles stand in Corner radius and Margin's place, and the sheet keeps its height. */
    @Test
    public void theHiddenTilesStandInRowBsPlaceWithoutMovingTheSheet() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        View root = panel.view();
        int width = Math.round(360 * root.getResources().getDisplayMetrics().density);
        panel.showLayoutMode();
        int closed = panel.measureFor(EditorMode.LAYOUT, width);
        View tiles = root.findViewById(R.id.layout_editor_hidden_tiles);
        View corners = root.findViewById(R.id.appearance_editor_corners);
        assertEquals(View.GONE, tiles.getVisibility());
        assertEquals(View.VISIBLE, corners.getVisibility());

        panel.setHiddenTilesOpen(true);
        assertTrue(panel.isHiddenTilesOpen());
        assertEquals(View.VISIBLE, tiles.getVisibility());
        assertEquals("kept for its height, not shown", View.INVISIBLE, corners.getVisibility());
        assertFalse(corners.isEnabled());
        assertEquals("the sheet does not move", closed, panel.measureFor(EditorMode.LAYOUT, width));

        panel.showAppearanceMode();
        assertEquals("Appearance never shows the tiles", View.GONE, tiles.getVisibility());
        panel.showLayoutMode();
        assertEquals(View.VISIBLE, tiles.getVisibility());
        panel.setHiddenTilesOpen(false);
        assertEquals(View.GONE, tiles.getVisibility());
        assertEquals(View.VISIBLE, corners.getVisibility());
    }

    /** A disabled column (Contrast without wallpaper colours) stays disabled with Row B up. */
    @Test
    public void aDisabledColumnStaysDisabledWhenRowTwoComesUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        java.util.List<AppearanceEditorPanel.SliderState> states = new java.util.ArrayList<>();
        states.add(new AppearanceEditorPanel.SliderState(AppearanceLooks.Control.BLUR, 4, true,
            value -> "Blur"));
        states.add(new AppearanceEditorPanel.SliderState(AppearanceLooks.Control.CONTRAST, 1,
            false, value -> "Contrast"));
        panel.showRow2(R.string.appearance_editor_target_terminal);
        panel.setSliders(states);
        assertTrue(panel.sliderFor(AppearanceLooks.Control.BLUR).isEnabled());
        assertFalse(panel.sliderFor(AppearanceLooks.Control.CONTRAST).isEnabled());
        panel.hideRow2();
        panel.showRow2(R.string.appearance_editor_target_terminal);
        assertTrue(panel.sliderFor(AppearanceLooks.Control.BLUR).isEnabled());
        assertFalse(panel.sliderFor(AppearanceLooks.Control.CONTRAST).isEnabled());
    }

    /** The buttons the selection has show, and only those. */
    @Test
    public void theHeadingRowShowsExactlyTheSelectionsButtons() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        panel.showRow2(R.string.appearance_editor_target_keyboard);
        panel.setDoors(AppearanceLooks.doors(AppearanceLooks.Target.KEYBOARD));
        assertEquals(java.util.Collections.singletonList(AppearanceLooks.Door.KEYBOARD_THEME),
            panel.shownDoors());
        MaterialButton theme = panel.view().findViewById(
            R.id.appearance_editor_door_keyboard_theme);
        assertEquals("Keyboard theme", theme.getText().toString());
        assertTrue(theme.isEnabled());
        panel.setDoors(AppearanceLooks.doors(AppearanceLooks.Target.TERMINAL));
        assertEquals(java.util.Arrays.asList(AppearanceLooks.Door.TRAIL,
            AppearanceLooks.Door.EFFECT), panel.shownDoors());
        panel.setDoors(AppearanceLooks.doors(null));
        assertTrue(panel.shownDoors().isEmpty());
    }

    private static boolean isInside(View view, View ancestor) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor)
                return true;
        }
        return false;
    }
}
