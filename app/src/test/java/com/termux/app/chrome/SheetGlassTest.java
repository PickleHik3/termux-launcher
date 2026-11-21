package com.termux.app.chrome;

import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * The sheets (drawer, folders, menus, command palette, notices) wear Global's glass with one
 * floor on opacity; the Home float wears Global's as it is (ADR 0031).
 */
public class SheetGlassTest {

    private static LookGrid global(int opacity) {
        LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
        grid.write(LookGrid.Property.FROST, null, FrostStops.MIST);
        grid.write(LookGrid.Property.OPACITY, null, opacity);
        grid.write(LookGrid.Property.TINT, null, 15);
        return grid;
    }

    @Test
    public void theFloorIsOneProvisionalSixtyPercent() {
        assertEquals(60, SheetGlass.OPACITY_FLOOR_PERCENT);
    }

    @Test
    public void globalBelowTheFloor_aSheetIsAtTheFloor() {
        assertEquals(SheetGlass.OPACITY_FLOOR_PERCENT, SheetGlass.of(global(2)).opacityPercent);
        assertEquals(SheetGlass.OPACITY_FLOOR_PERCENT, SheetGlass.of(global(0)).opacityPercent);
        assertEquals(SheetGlass.OPACITY_FLOOR_PERCENT, SheetGlass.opacityPercent(10));
        assertEquals(0.6f, SheetGlass.of(global(2)).tintAlpha(), 1e-6f);
    }

    @Test
    public void globalAboveTheFloor_aSheetMatchesGlobalExactly() {
        for (int opacity : new int[] {60, 61, 85, 100}) {
            ElementGlass global = SheetGlass.global(global(opacity));
            ElementGlass sheet = SheetGlass.of(global(opacity));
            assertEquals(opacity, sheet.opacityPercent);
            assertEquals(global.blurDp, sheet.blurDp);
            assertEquals(global.grainPercent, sheet.grainPercent);
            assertEquals(global.tintPercent, sheet.tintPercent);
            assertEquals(global.cornersDp, sheet.cornersDp);
        }
    }

    @Test
    public void onlyOpacityHasAFloor() {
        ElementGlass global = SheetGlass.global(global(2));
        ElementGlass sheet = SheetGlass.of(global(2));
        assertEquals(global.blurDp, sheet.blurDp);
        assertEquals(global.grainPercent, sheet.grainPercent);
        assertEquals(global.tintPercent, sheet.tintPercent);
        assertEquals(global.cornersDp, sheet.cornersDp);
    }

    @Test
    public void theHomeFloatWearsGlobalAsItIs_belowTheFloorIncluded() {
        assertEquals(2, SheetGlass.global(global(2)).opacityPercent);
        assertEquals(0, SheetGlass.global(global(0)).opacityPercent);
        assertEquals(90, SheetGlass.global(global(90)).opacityPercent);
    }

    @Test
    public void aStoredSheetsCellIsNeverRead() {
        LookGrid grid = global(70);
        grid.write(LookGrid.Property.OPACITY, LookGrid.Element.SHEETS, 5);
        grid.write(LookGrid.Property.CORNERS, LookGrid.Element.SHEETS, 33);
        grid.write(LookGrid.Property.GRAIN, LookGrid.Element.SHEETS, 77);
        ElementGlass global = SheetGlass.global(grid);
        ElementGlass sheet = SheetGlass.of(grid);
        assertEquals(70, sheet.opacityPercent);
        assertEquals(global.cornersDp, sheet.cornersDp);
        assertEquals(global.grainPercent, sheet.grainPercent);
        assertSame(global, global.withOpacityFloor(70));
    }

    /** No production code reads the sheets' look from the grid or the surface slots. */
    @Test
    public void noSourceReadsTheSheetsStoredLook() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path app = root.resolve("src/main/java");
        if (!Files.isDirectory(app)) app = root.resolve("app/src/main/java");
        try (Stream<Path> files = Files.walk(app)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                assertFalse(file + " reads the sheets' look cell", text.contains("Element.SHEETS"));
                assertFalse(file + " reads the SHEETS slot's values",
                    text.contains("SurfaceSlot.SHEETS, SurfaceProperty")
                        || text.contains("SurfaceSlot.SHEETS, TermuxAppSharedPreferences"));
            }
        }
    }
}
