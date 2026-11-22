package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.widget.LauncherWidgetRecord.FolderApp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Home › Folders: the popup's density, Default being the popup as it was, and the cap
 * on how many apps a folder takes, 16 being the cap as it was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class HomeFolderDensityTest {

    private static FolderApp app(String name) {
        return new FolderApp(new AppRef("com." + name, "com." + name + ".Main"), name);
    }

    private static List<FolderApp> apps(int count) {
        List<FolderApp> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(app("a" + i));
        return out;
    }

    private static LauncherWidgetRecord icon(int id, String name) {
        Bundle options = new Bundle();
        options.putString(LauncherWidgetRecord.APP_LABEL, name);
        return LauncherWidgetRecord.app(id, new AppRef("com." + name, "com." + name + ".Main"),
            new WidgetCellRect(0, 0, 1, 1), 0, options);
    }

    @Test
    public void defaultIsThePopupAsItWas() {
        HomeFolderDensity density = HomeFolderDensity.DEFAULT;
        assertSame(density, HomeFolderDensity.fromValue(null));
        assertSame(density, HomeFolderDensity.fromValue("dense"));
        assertEquals(4, density.columns);
        assertEquals(76, density.cellWidthDp);
        assertEquals(84, density.cellHeightDp);
        // max(176, columns × 76 + 6), as the popup always was.
        assertEquals(310, density.popupWidthDp(4));
        assertEquals(176, density.popupWidthDp(1));
        assertEquals(4, density.columnsFor(16, 360f));
        assertEquals(3, density.columnsFor(3, 360f));
    }

    @Test
    public void compactTakesMoreAcrossAndComfortableFewer() {
        assertEquals(5, HomeFolderDensity.COMPACT.columnsFor(16, 400f));
        assertEquals(3, HomeFolderDensity.COMFORTABLE.columnsFor(16, 400f));
        assertTrue(HomeFolderDensity.COMPACT.cellWidthDp < HomeFolderDensity.DEFAULT.cellWidthDp);
        assertTrue(HomeFolderDensity.COMFORTABLE.cellHeightDp > HomeFolderDensity.DEFAULT.cellHeightDp);
    }

    @Test
    public void thePopupNeverOutgrowsTheScreen() {
        for (HomeFolderDensity density : HomeFolderDensity.values()) {
            for (float width : new float[] {280f, 320f, 360f, 411f}) {
                int columns = density.columnsFor(20, width);
                assertTrue(density + " at " + width,
                    columns == 1 || columns * density.cellWidthDp + HomeFolderDensity.POPUP_EDGE_DP <= width);
                assertTrue(columns >= 1);
            }
        }
        assertEquals("an unmeasured pane does not limit", 5,
            HomeFolderDensity.COMPACT.columnsFor(20, 0f));
    }

    @Test
    public void theCapDefaultsToSixteenAndReadsLeniently() {
        assertEquals(16, HomeFolderPolicy.parseCapacity(null));
        assertEquals(16, HomeFolderPolicy.parseCapacity("lots"));
        assertEquals(9, HomeFolderPolicy.parseCapacity("9"));
        assertEquals(20, HomeFolderPolicy.parseCapacity("99"));
        assertEquals(12, HomeFolderPolicy.clampCapacity(11));
        assertEquals(HomeFolderPolicy.HARD_MAX_APPS,
            HomeFolderPolicy.CAPACITIES[HomeFolderPolicy.CAPACITIES.length - 1]);
    }

    @Test
    public void aFolderTakesAppsUpToItsCap() {
        assertNotNull(HomeFolderPolicy.add(apps(8), app("new"), 9));
        assertNull(HomeFolderPolicy.add(apps(9), app("new"), 9));
        assertNotNull(HomeFolderPolicy.add(apps(16), app("new"), 20));
        assertNull("the old cap still holds by default", HomeFolderPolicy.add(apps(16), app("new")));
        assertFalse("a folder past a lowered cap takes no more",
            HomeFolderPolicy.canAdd(apps(16), app("new"), 12));
    }

    @Test
    public void aFullFolderIsNoDropTargetUnderItsCap() {
        LauncherWidgetRecord folder = LauncherWidgetRecord.folder(-5, "Tools", apps(12),
            new WidgetCellRect(1, 0, 2, 1), 0);
        LauncherWidgetRecord dragged = icon(-6, "maps");
        assertFalse(HomeFolderPolicy.canTarget(dragged, folder, 12));
        assertTrue(HomeFolderPolicy.canTarget(dragged, folder, 16));
        assertSame(HomeFolderPolicy.Drop.MOVE,
            HomeFolderPolicy.dropAction(dragged, folder, HomeFolderPolicy.MERGE_HOVER_MS, 12));
        assertSame(HomeFolderPolicy.Drop.ADD_TO_FOLDER,
            HomeFolderPolicy.dropAction(dragged, folder, HomeFolderPolicy.MERGE_HOVER_MS, 16));
    }
}
