package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The palette is one custom-drawn view, so TalkBack and UI automation see it through virtual nodes:
 * the search field, a node per result row and call chip, a node per keycap. Each click action must
 * reach the same callback a tap would.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CommandPaletteAccessibilityTest {

    private static final int FIELD = 0;
    private static final int CAP_BASE = 100;
    private static final int ROW_BASE = 1000;
    private static final int CHIP_BASE = 1_000_000;

    private final List<String> calls = new ArrayList<>();

    private final CommandPaletteView.Callbacks callbacks = new CommandPaletteView.Callbacks() {
        @Override public void onRowTapped(int index) { calls.add("row " + index); }
        @Override public void onKeycapTapped(int index) { calls.add("cap " + index); }
        @Override public void onRowActionTapped(int index) { calls.add("chip " + index); }
        @Override public void onRowLongPressed(int index) { calls.add("long " + index); }
        @Override public void onOutsideTapped() { calls.add("outside"); }
    };

    private CommandPaletteView palette() {
        CommandPaletteView view = new CommandPaletteView(
            ApplicationProvider.getApplicationContext());
        float density = view.getResources().getDisplayMetrics().density;
        view.setCallbacks(callbacks);
        view.setKeycaps(Arrays.asList(
            new CommandPaletteView.Keycap("⌨", "kbd"),
            new CommandPaletteView.Keycap("＋", "new")));
        view.setHeader("3 results", "");
        view.setQuery("c", "Search");
        view.setRows(Arrays.asList(
            CommandPaletteView.Row.category("Apps"),
            CommandPaletteView.Row.entry("Clock", "com.example.clock", "C-A-X", true),
            CommandPaletteView.Row.entry("Ann", null, "", true, null,
                new CommandPaletteView.Row.Action(new ColorDrawable(0xFF000000))),
            CommandPaletteView.Row.entry("Locked", null, "", false)), 1);
        int width = Math.round(360f * density);
        int height = Math.round(640f * density);
        view.setModalBounds(new RectF(0f, 0f, width, height));
        view.setFrame(new RectF(14f * density, 40f * density, width - 14f * density,
            420f * density), 26f, 1f, 1f, 0f, 1f);
        view.layout(0, 0, width, height);
        return view;
    }

    private static AccessibilityNodeInfo node(CommandPaletteView view, int id) {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        assertNotNull("the palette exposes virtual nodes", provider);
        return provider.createAccessibilityNodeInfo(id);
    }

    @Test
    public void searchField_isAnEditTextHoldingTheQuery() {
        CommandPaletteView view = palette();

        AccessibilityNodeInfo field = node(view, FIELD);

        assertNotNull(field);
        assertEquals("android.widget.EditText", field.getClassName().toString());
        assertEquals("c", field.getText().toString());
    }

    @Test
    public void everyResultRowHasANodeWithItsTitle() {
        CommandPaletteView view = palette();

        AccessibilityNodeInfo clock = node(view, ROW_BASE + 1);
        AccessibilityNodeInfo locked = node(view, ROW_BASE + 3);
        AccessibilityNodeInfo category = node(view, ROW_BASE);

        assertEquals("Clock", clock.getText().toString());
        assertTrue(clock.getContentDescription().toString().contains("com.example.clock"));
        assertTrue(clock.isClickable());
        assertTrue(clock.isLongClickable());
        assertTrue("the focused row reads as selected", clock.isSelected());
        assertFalse(locked.isEnabled());
        assertEquals("Apps", category.getText().toString());
        assertFalse("a category rule is not a button", category.isClickable());
    }

    @Test
    public void clickingARowNode_tapsTheRow_andLongClickRunsItsSecondaryAction() {
        CommandPaletteView view = palette();
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();

        assertTrue(provider.performAction(ROW_BASE + 1, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertTrue(provider.performAction(ROW_BASE + 2,
            AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertFalse("a category is not activatable",
            provider.performAction(ROW_BASE, AccessibilityNodeInfo.ACTION_CLICK, null));

        assertEquals(Arrays.asList("row 1", "long 2"), calls);
    }

    @Test
    public void aContactsCallChipIsItsOwnButton() {
        CommandPaletteView view = palette();
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();

        AccessibilityNodeInfo chip = node(view, CHIP_BASE + 2);
        assertEquals("Call", chip.getText().toString());
        assertTrue(chip.getContentDescription().toString().contains("Ann"));
        assertTrue(provider.performAction(CHIP_BASE + 2, AccessibilityNodeInfo.ACTION_CLICK, null));

        assertEquals(Arrays.asList("chip 2"), calls);
    }

    @Test
    public void keycapsAreButtonsThatTapTheCap() {
        CommandPaletteView view = palette();
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();

        AccessibilityNodeInfo cap = node(view, CAP_BASE + 1);
        assertEquals("new", cap.getText().toString());
        assertTrue(provider.performAction(CAP_BASE + 1, AccessibilityNodeInfo.ACTION_CLICK, null));

        assertEquals(Arrays.asList("cap 1"), calls);
    }

    @Test
    public void changingTheRows_changesTheNodes() {
        CommandPaletteView view = palette();
        view.setRows(Arrays.asList(
            CommandPaletteView.Row.entry("Camera", null, "", true)), 0);

        assertEquals("Camera", node(view, ROW_BASE).getText().toString());
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        assertFalse("a row that is gone has nothing to click",
            provider.performAction(ROW_BASE + 1, AccessibilityNodeInfo.ACTION_CLICK, null));
    }
}
