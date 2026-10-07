package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.place.EdgeStackView;
import com.termux.app.terminal.PaneRetroStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Which views the terminal effect is drawn on. Every surface has to be drawn through exactly one
 * effect whatever the place does with it, the panes (which carry their own) through none of these,
 * and the editor's own marks through none at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChromeRetroEffectTest {

    private static View inflateRoot() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View root = activity.findViewById(R.id.activity_termux_root_view);
        assertNotNull(root);
        return root;
    }

    /** The effect roots {@code view} is drawn through: itself or an ancestor among them. */
    private static List<View> rootsOver(View view, List<View> roots) {
        List<View> over = new ArrayList<>();
        for (View v = view; v != null; ) {
            if (roots.contains(v)) over.add(v);
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return over;
    }

    private static void collectStacks(View view, List<EdgeStackView> out) {
        if (view instanceof EdgeStackView) out.add((EdgeStackView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectStacks(group.getChildAt(i), out);
        }
    }

    @Test public void everyRootIsInTheLayoutAndNoneHoldsAnother() {
        View root = inflateRoot();
        List<View> roots = new ChromeRetroEffect(root).targets();
        assertEquals(ChromeRetroEffect.ROOT_IDS.length, roots.size());
        for (View view : roots)
            assertEquals(view.getResources().getResourceEntryName(view.getId()),
                1, rootsOver(view, roots).size());
    }

    @Test public void everyStackAPlaceCanStandABarInIsDrawnThroughExactlyOneEffect() {
        // Bars only ever stand in an edge stack (the four edges, the plank's bars, the strip under
        // the keyboard), so wherever the layout moves one, it lands under exactly one root.
        View root = inflateRoot();
        List<View> roots = new ChromeRetroEffect(root).targets();
        List<EdgeStackView> stacks = new ArrayList<>();
        collectStacks(root, stacks);
        assertTrue("the layout has its stacks", stacks.size() >= 6);
        for (EdgeStackView stack : stacks)
            assertEquals(stack.getResources().getResourceEntryName(stack.getId()),
                1, rootsOver(stack, roots).size());
    }

    @Test public void theSurfacesAreCoveredAndThePanesAndTheEditorAreNot() {
        View root = inflateRoot();
        List<View> roots = new ChromeRetroEffect(root).targets();
        int[] covered = {
            R.id.terminal_window_bar_host, R.id.place_az_bar_host, R.id.place_off_dock_plank_host,
            R.id.place_apps_bar_host, R.id.place_extra_keys_host, R.id.accessory_surface_host,
            R.id.apps_bar_row_host, R.id.apps_bar_az_host, R.id.terminal_toolbar_host,
            R.id.inapp_keyboard_container, R.id.inapp_keyboard_view_host,
            R.id.inapp_keyboard_suggestion_host, R.id.accessory_under_keyboard_stack,
            R.id.docked_frame_glass, R.id.terminal_status_bar_background,
        };
        for (int id : covered) {
            View view = root.findViewById(id);
            assertNotNull(view.getResources().getResourceEntryName(id), view);
            assertEquals(view.getResources().getResourceEntryName(id), 1, rootsOver(view, roots).size());
        }
        // The panes draw their own (bent) effect: one more on top would draw it twice. The editor's
        // tap layer and selection outline are the editor's, not the home screen's.
        int[] plain = {
            R.id.terminal_pane_host, R.id.terminal_surface_host, R.id.wallpaper_backdrop,
            R.id.surface_tuning_gesture_overlay, R.id.terminal_border_overlay,
            R.id.floating_keyboard_host, R.id.place_az_tab_layer, R.id.app_drawer_host,
        };
        for (int id : plain) {
            View view = root.findViewById(id);
            assertNotNull(view.getResources().getResourceEntryName(id), view);
            assertTrue(view.getResources().getResourceEntryName(id), rootsOver(view, roots).isEmpty());
        }
    }

    @Test public void belowApi33NothingIsDrawnAndTheStyleStaysNone() {
        View root = inflateRoot();
        ChromeRetroEffect effect = new ChromeRetroEffect(root);
        for (PaneRetroStyle style : PaneRetroStyle.values()) {
            effect.setStyle(style);
            assertEquals(PaneRetroStyle.NONE, effect.style());
        }
        effect.setStyle(null);
        assertEquals(PaneRetroStyle.NONE, effect.style());
        assertFalse(effect.targets().isEmpty());
    }
}
