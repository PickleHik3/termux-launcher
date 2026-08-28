package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SurfaceEditorRowsTest {

    @Test
    public void everyRealCellHasExactlyOneRowAndNoPhantomCellDoes() {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                SurfaceEditorRows.Row row = SurfaceEditorRows.forCell(slot, property);
                if (TermuxAppSharedPreferences.hasSurfaceProperty(slot, property)) {
                    assertNotNull(slot + "/" + property, row);
                    assertSame(row, SurfaceEditorRows.forSlider(row.sliderId));
                    assertNotNull(row.read);
                    assertNotNull(row.write);
                } else {
                    assertNull(slot + "/" + property, row);
                }
            }
        }
    }

    @Test
    public void everySlotHasAPageWithDistinctViews() {
        Set<Integer> seen = new HashSet<>();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            SurfaceEditorRows.Page page = SurfaceEditorRows.page(slot);
            assertSame(slot, page.slot);
            assertTrue(seen.add(page.chipId));
            assertTrue(seen.add(page.groupId));
            assertTrue(seen.add(page.reattachId));
            assertEquals(page.labelRes, SurfaceEditorRows.slotLabel(slot));
        }
    }

    @Test
    public void theRadiusTracksShareTheEditorCeiling() {
        // editorRadius caps the resolved sentinel to this; both capsule surfaces run to 40dp.
        assertEquals(40, SurfaceEditorRows.forCell(SurfaceSlot.DOCK, SurfaceProperty.CORNER_RADIUS).max);
        assertEquals(40, SurfaceEditorRows.forCell(SurfaceSlot.STATUS, SurfaceProperty.CORNER_RADIUS).max);
    }
}
