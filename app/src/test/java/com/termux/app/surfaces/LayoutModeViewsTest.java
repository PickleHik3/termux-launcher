package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
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
 * Layout mode lives in the one editor (SPEC §3.5): its canvas in the frame
 * ({@code layout_editor_frame}), its orientation toggle and trash in the bottom area
 * ({@code appearance_editor_panel}). {@code SurfaceEditorController} looks each view up by id to
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
    public void theFrameCarriesTheCanvasAndTheKeyboardsTypeChips() {
        View frame = inflate(R.layout.layout_editor_frame);
        assertTrue("the frame is what the canvas stands in", frame instanceof FrameLayout);
        assertTrue(frame.findViewById(R.id.layout_editor_canvas) instanceof LayoutCanvasView);
        View forms = frame.findViewById(R.id.layout_editor_keyboard_forms);
        assertTrue(forms instanceof ChipGroup);
        assertEquals("docked, floating, split", 3, ((ChipGroup) forms).getChildCount());
        assertNotNull(frame.findViewById(R.id.layout_editor_keyboard_form_docked));
        assertNotNull(frame.findViewById(R.id.layout_editor_keyboard_form_floating));
        assertNotNull(frame.findViewById(R.id.layout_editor_keyboard_form_split));
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
        assertTrue("the trash is an icon button: the drop target, the list's and the badge's anchor",
            panel.findViewById(R.id.layout_editor_tray_trash) instanceof MaterialButton);
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
        // The mode pill, Undo and Done stay on the top row for both modes.
        assertNotNull(panel.findViewById(R.id.appearance_editor_mode_layout));
        assertNotNull(panel.findViewById(R.id.appearance_editor_undo));
        assertNotNull(panel.findViewById(R.id.appearance_editor_done));
    }

    /** Contrast is the terminal's: its own label and slider in Row B, apart from the first control's. */
    @Test
    public void rowTwoCarriesLegibilityInAColumnOfItsOwn() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View row2 = panel.findViewById(R.id.appearance_editor_row2);
        View label = panel.findViewById(R.id.appearance_editor_cl_label);
        View legibility = panel.findViewById(R.id.appearance_editor_legibility);
        assertNotNull(label);
        assertTrue(legibility instanceof Slider);
        assertEquals(2f, ((Slider) legibility).getValueTo(), 0f);
        assertTrue(isInside(legibility, row2));
        assertTrue(isInside(label, row2));
        assertNotNull(panel.findViewById(R.id.appearance_editor_row2_barrier));
        assertNotSame(label, panel.findViewById(R.id.appearance_editor_c1_label));
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

    /** Row B is GONE until an element is tapped, and keeps its state across Layout mode. */
    @Test
    public void rowTwoIsGoneUntilTappedAndKeepsItsStateAcrossLayoutMode() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed, new FrameLayout(themed));
        View row2 = panel.view().findViewById(R.id.appearance_editor_row2);
        Slider blur = panel.view().findViewById(R.id.appearance_editor_c2_slider);
        assertEquals(View.GONE, row2.getVisibility());
        assertFalse("a hidden Row B takes no touches", blur.isEnabled());

        panel.showRow2(R.string.appearance_editor_target_dock);
        panel.hideFirst();
        panel.hideLegibility();
        panel.setSecondSlider("Blur · 8 dp", 8, 32);
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
        assertFalse(blur.isEnabled());
    }

    private static boolean isInside(View view, View ancestor) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor)
                return true;
        }
        return false;
    }
}
