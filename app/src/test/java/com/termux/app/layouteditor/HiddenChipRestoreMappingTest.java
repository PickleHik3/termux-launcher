package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.app.fragments.settings.LayoutCanvasView.Block;

import org.junit.Test;

/**
 * A hidden-element tile in the sheet (layout editor v2, DECISIONS item 3) stands for one tray
 * item: tapped, that item is restored (the plan puts it back on the edge it was hidden from), and
 * the canvas never offers the canvas block.
 */
public class HiddenChipRestoreMappingTest {

    @Test
    public void everyHideableBlockMapsToItsTrayItem() {
        assertEquals(LayoutEditorPlan.TrayItem.STATUS_BAR,
            LayoutEditorController.trayItemOf(Block.STATUS_BAR));
        assertEquals(LayoutEditorPlan.TrayItem.PINNED_APPS,
            LayoutEditorController.trayItemOf(Block.APPS_ROW));
        assertEquals(LayoutEditorPlan.TrayItem.AZ_INDEX,
            LayoutEditorController.trayItemOf(Block.ALPHABETS_ROW));
        assertEquals(LayoutEditorPlan.TrayItem.EXTRA_KEYS,
            LayoutEditorController.trayItemOf(Block.EXTRA_KEYS));
        assertEquals(LayoutEditorPlan.TrayItem.KEYBOARD,
            LayoutEditorController.trayItemOf(Block.KEYBOARD));
    }

    @Test
    public void theCanvasIsNotSomethingYouCanHide() {
        assertNull(LayoutEditorController.trayItemOf(Block.CANVAS));
    }
}
