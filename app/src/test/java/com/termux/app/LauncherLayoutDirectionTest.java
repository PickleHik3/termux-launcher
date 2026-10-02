package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Locale;

/**
 * What mirrors in a right-to-left locale and what does not.
 *
 * <p>The launcher shell is a spatial arrangement the user built — panes, dock, keys, status strip —
 * placed by absolute geometry, so it stays left to right. The terminal grid is columns, not reading
 * order. Reading-order surfaces (the sheet plane, the Appearance editor's panel) follow the locale.
 * Directions are resolved by hand here because the activity is never attached to a window.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherLayoutDirectionTest {

    private Locale mPreviousLocale;

    @Before
    public void arabic() {
        mPreviousLocale = Locale.getDefault();
        RuntimeEnvironment.setQualifiers("+ar-rXB-ldrtl");
        Locale.setDefault(new Locale("ar"));
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(mPreviousLocale);
    }

    @Test
    public void theApplicationDeclaresRtlSupport() {
        assertTrue("without supportsRtl nothing mirrors, whatever a view asks for",
            (RuntimeEnvironment.getApplication().getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL) != 0);
    }

    @Test
    public void theShellStaysLeftToRightWhileTheSheetPlaneMirrors() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        View root = activity.findViewById(R.id.activity_termux_root_view);
        resolve(root);

        assertEquals(View.LAYOUT_DIRECTION_LTR, root.getLayoutDirection());
        assertLtr(activity, R.id.terminal_pane_wall);
        assertLtr(activity, R.id.terminal_status_row);
        assertLtr(activity, R.id.accessory_row_stack);
        assertLtr(activity, R.id.apps_bar_viewpager);
        assertLtr(activity, R.id.inapp_keyboard_container);
        assertLtr(activity, R.id.app_drawer_host);

        View sheetHost = activity.findViewById(R.id.terminal_sheet_host);
        resolve(sheetHost);
        assertEquals("a sheet is reading-order UI", View.LAYOUT_DIRECTION_RTL,
            sheetHost.getLayoutDirection());
    }

    @Test
    public void theTerminalAndItsKeysStayLeftToRightOnTheirOwn() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();

        TerminalView terminal = new TerminalView(activity, null);
        resolve(terminal);
        assertEquals("column 0 is on the left outside the shell too", View.LAYOUT_DIRECTION_LTR,
            terminal.getLayoutDirection());

        ExtraKeysView keys = new ExtraKeysView(activity, null);
        resolve(keys);
        assertEquals(View.LAYOUT_DIRECTION_LTR, keys.getLayoutDirection());
    }

    @Test
    public void theAppearanceEditorPanelMirrors() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        FrameLayout parent = new FrameLayout(activity);
        parent.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        View panel = LayoutInflater.from(activity)
            .inflate(R.layout.appearance_editor_panel, parent, false);
        parent.addView(panel);
        resolve(parent);
        resolve(panel);

        assertEquals("pinned content root or not, its labels and sliders read right to left",
            View.LAYOUT_DIRECTION_RTL, panel.getLayoutDirection());
    }

    private static void assertLtr(TermuxActivity activity, int id) {
        View view = activity.findViewById(id);
        assertNotNull(view);
        assertEquals(activity.getResources().getResourceEntryName(id),
            View.LAYOUT_DIRECTION_LTR, view.getLayoutDirection());
    }

    /** What attaching to a window or a measure pass would do: inherited children follow. */
    private static void resolve(View view) {
        ReflectionHelpers.callInstanceMethod(view, "resolveRtlPropertiesIfNeeded");
    }
}
