package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

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
 * ({@code layout_editor_frame}), its orientation toggle and restore tray in the bottom area
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
    public void theBottomAreaCarriesLayoutModesOneRowAndItStartsHidden() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View row = panel.findViewById(R.id.appearance_editor_layout_row);
        assertNotNull(row);
        assertEquals("Appearance is the mode a plain open shows", View.GONE, row.getVisibility());
        View orientation = panel.findViewById(R.id.layout_editor_orientation);
        assertTrue(orientation instanceof MaterialButtonToggleGroup);
        assertEquals("portrait and landscape",
            2, ((MaterialButtonToggleGroup) orientation).getChildCount());
        assertNotNull(panel.findViewById(R.id.layout_editor_orientation_portrait));
        assertNotNull(panel.findViewById(R.id.layout_editor_orientation_landscape));
        assertNotNull(panel.findViewById(R.id.layout_editor_tray));
        assertTrue(panel.findViewById(R.id.layout_editor_tray_chips) instanceof ChipGroup);
        assertNotNull(panel.findViewById(R.id.layout_editor_tray_empty));
        assertNotNull(panel.findViewById(R.id.layout_editor_tray_drop));
        assertNotNull(panel.findViewById(R.id.appearance_editor_row2_controls));
        // Layout mode also carries what no Look sets (2026-10-01): Style, Corners and Margin.
        View style = panel.findViewById(R.id.appearance_editor_style);
        assertTrue(style instanceof MaterialButtonToggleGroup);
        assertEquals("docked and floating",
            2, ((MaterialButtonToggleGroup) style).getChildCount());
        assertTrue("Style lives in the Layout row", isInside(style, row));
        View corners = panel.findViewById(R.id.appearance_editor_corners);
        View margin = panel.findViewById(R.id.appearance_editor_margin);
        assertTrue(corners instanceof Slider);
        assertTrue(margin instanceof Slider);
        assertTrue(isInside(corners, row));
        assertTrue(isInside(margin, row));
        assertEquals(40f, ((Slider) corners).getValueTo(), 0f);
        assertEquals(48f, ((Slider) margin).getValueTo(), 0f);
        assertNotNull(panel.findViewById(R.id.appearance_editor_corners_label));
        assertNotNull(panel.findViewById(R.id.appearance_editor_margin_label));
        // The mode pill, Undo and Done stay on the top row for both modes.
        assertNotNull(panel.findViewById(R.id.appearance_editor_mode_layout));
        assertNotNull(panel.findViewById(R.id.appearance_editor_undo));
        assertNotNull(panel.findViewById(R.id.appearance_editor_done));
    }

    /** Legibility is the terminal's now: its own column in row 2, out of the first control's. */
    @Test
    public void rowTwoCarriesLegibilityInAColumnOfItsOwn() {
        View panel = inflate(R.layout.appearance_editor_panel);
        View row2 = panel.findViewById(R.id.appearance_editor_row2);
        View column = panel.findViewById(R.id.appearance_editor_cl);
        View legibility = panel.findViewById(R.id.appearance_editor_legibility);
        assertNotNull(column);
        assertTrue(legibility instanceof MaterialButtonToggleGroup);
        assertEquals("softer, default, harder",
            3, ((MaterialButtonToggleGroup) legibility).getChildCount());
        assertTrue(isInside(legibility, column));
        assertTrue(isInside(column, row2));
        assertFalse("not the first control's any more",
            isInside(legibility, panel.findViewById(R.id.appearance_editor_c1)));
        assertNotNull(panel.findViewById(R.id.appearance_editor_row2_line));
        assertFalse("Style left Appearance's row 1", isInside(
            panel.findViewById(R.id.appearance_editor_style),
            panel.findViewById(R.id.appearance_editor_row1)));
    }

    private static boolean isInside(View view, View ancestor) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor)
                return true;
        }
        return false;
    }
}
