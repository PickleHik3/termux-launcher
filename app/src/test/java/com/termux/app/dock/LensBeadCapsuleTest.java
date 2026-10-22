package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Lens beads with the Search field on (#84, 2026-10-21): the row keeps its beads and the field
 * stands in a glass capsule of its own under them. The capsule is the field's view: as wide as
 * the apps row, the field's 48 dp tall, a stadium, at least {@link LensBeadGeometry#CAPSULE_AIR_DP}
 * under the beads (the least air the beads keep between themselves), exactly that under beads as
 * tall as the row's band lets them be. The dock is taller than the slab's with
 * the field by {@link LensBeadGeometry#CAPSULE_TOP_PAD_DP} and nothing else, and the beads are the
 * size they are without the field.
 */
public class LensBeadCapsuleTest {

    /** A 360 dp phone at 3x: 1080 px wide. */
    private static final float DENSITY = 3f;
    private static final float WIDTH_DP = 360f;
    private static final int BASE_TOOLBAR_PX = 144;

    private static DockLayoutPolicy.DockInputs.Builder inputs(DockSurfaceSpec spec, float reveal) {
        return DockLayoutPolicy.DockInputs.builder()
            .capsule(true)
            .appsRowAlone(true)
            .appsRowNextToCanvas(true)
            .density(DENSITY)
            .barHeightScale(DockLayoutPolicy.sizePreset(DockLayoutPolicy.sizePresetCount() / 2))
            .configuredCornerRadiusDp(-1)
            .appsRowEnabledPref(true)
            .azRowEnabledPref(false)
            .baseToolbarHeightPx(BASE_TOOLBAR_PX)
            .surface(spec)
            .promptFieldReveal(reveal);
    }

    private static DockLayout layout(DockSurfaceSpec spec, float reveal) {
        return DockLayoutPolicy.compute(inputs(spec, reveal).build());
    }

    // ---------------------------------------------------------------- the layout

    @Test
    public void underBeadsTheFieldStandsTheCapsulesAirLowerAndNothingElseMoves()
            throws IllegalAccessException {
        DockLayout slab = layout(DockSurfaceSpec.GLASS_TRAY, 1f);
        DockLayout beads = layout(DockSurfaceSpec.LENS_BEADS, 1f);
        int pad = LensBeadGeometry.capsuleTopPadPx(DENSITY);
        assertEquals(Math.round(5f * DENSITY), pad);
        assertEquals(0, slab.promptFieldTopPadPx);
        assertEquals(pad, beads.promptFieldTopPadPx);
        // The band is the slab's with the field and the capsule's air over it, the field the same.
        assertEquals(slab.promptFieldBandPx + pad, beads.promptFieldBandPx);
        assertEquals(slab.promptFieldFullBandPx + pad, beads.promptFieldFullBandPx);
        assertEquals(slab.promptFieldHeightPx, beads.promptFieldHeightPx);
        assertEquals(slab.promptFieldBottomPadPx, beads.promptFieldBottomPadPx);
        assertEquals(beads.promptFieldBandPx, beads.promptFieldTopPadPx
            + beads.promptFieldHeightPx + beads.promptFieldBottomPadPx);
        assertEquals(slab.combinedHeight(BASE_TOOLBAR_PX, false) + pad,
            beads.combinedHeight(BASE_TOOLBAR_PX, false));
        // Every other number is the slab's with the field, the field's sides aside, and the
        // row's side inset, which the beads narrow (#135 measures the row's width with it).
        for (Field field : DockLayout.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("surface")
                || field.getName().startsWith("promptField")
                || field.getName().equals("appsRowEdgeInsetPx")) continue;
            assertEquals(field.getName(), field.get(slab), field.get(beads));
        }
        assertEquals(beads.appsRowSideInsetPx(), beads.appsRowEdgeInsetPx);
    }

    @Test
    public void underBeadsTheFieldIsAsWideAsTheAppsRow() {
        DockLayout beads = layout(DockSurfaceSpec.LENS_BEADS, 1f);
        assertEquals(beads.appsRowSideInsetPx(), beads.promptFieldSideInsetPx);
        // The row's inset: its content inset trimmed, and the beads' own 9 dp.
        assertEquals(Math.round(beads.capsuleContentInsetPx * 0.82f)
            + LensBeadGeometry.rowSideInsetPx(DENSITY), beads.appsRowSideInsetPx());
        // The slab keeps the field the row's 14 dp in from its sides, as before.
        DockLayout slab = layout(DockSurfaceSpec.GLASS_TRAY, 1f);
        assertEquals(slab.horizontalInsetPx + Math.round(14f * DENSITY),
            slab.promptFieldSideInsetPx);
    }

    @Test
    public void withoutTheFieldBeadsAddNothing() {
        DockLayout beads = layout(DockSurfaceSpec.LENS_BEADS, 0f);
        assertEquals(0, beads.promptFieldTopPadPx);
        assertEquals(0, beads.promptFieldBandPx);
        assertEquals(layout(DockSurfaceSpec.GLASS_TRAY, 0f).combinedHeight(BASE_TOOLBAR_PX, false),
            beads.combinedHeight(BASE_TOOLBAR_PX, false));
    }

    @Test
    public void theCapsulesAirEasesInWithTheBand() {
        int full = layout(DockSurfaceSpec.LENS_BEADS, 1f).promptFieldFullBandPx;
        int previous = -1;
        for (int step = 0; step <= 20; step++) {
            DockLayout line = layout(DockSurfaceSpec.LENS_BEADS, step / 20f);
            assertTrue(line.promptFieldBandPx >= previous);
            assertTrue(line.promptFieldTopPadPx <= LensBeadGeometry.capsuleTopPadPx(DENSITY));
            assertEquals(line.promptFieldBandPx, line.promptFieldTopPadPx
                + line.promptFieldHeightPx + line.promptFieldBottomPadPx);
            previous = line.promptFieldBandPx;
        }
        assertEquals(full, previous);
    }

    // ---------------------------------------------------------------- the geometry

    /** The dock as the activity lays it out at 360 dp: the row, then the field, in one host. */
    private static final class Dock {
        final DockLayout layout = layout(DockSurfaceSpec.LENS_BEADS, 1f);
        final int air = layout.promptFieldWellGapPx;
        final int icon = layout.appsRowIconPx;
        /** The row's band in the host: its air, the icon, its air. */
        final float rowBottom = air + icon + air;
        final float fieldTop = rowBottom + layout.promptFieldTopPadPx;
        final float fieldBottom = fieldTop + layout.promptFieldHeightPx;
        final float width = WIDTH_DP * DENSITY;
        final float rowLeft = layout.appsRowSideInsetPx();
        final float rowRight = width - layout.appsRowSideInsetPx();
        final float fieldLeft = layout.promptFieldSideInsetPx;
        final float fieldRight = width - layout.promptFieldSideInsetPx;

        /** {@code count} icons in the row's even slots, as the row lays them out. */
        float[] boxes(int count) {
            float slot = (rowRight - rowLeft) / count;
            int fit = LensBeadGeometry.fitIconPx(icon, slot, DENSITY);
            float[] boxes = new float[count * 4];
            for (int i = 0; i < count; i++) {
                float cx = rowLeft + slot * (i + 0.5f);
                float cy = air + icon / 2f;
                boxes[i * 4] = cx - fit / 2f;
                boxes[i * 4 + 1] = cy - fit / 2f;
                boxes[i * 4 + 2] = cx + fit / 2f;
                boxes[i * 4 + 3] = cy + fit / 2f;
            }
            return boxes;
        }

        /** The beads as the activity resolves them: held to the row's band, then the capsule. */
        LensBeadGeometry.Beads beads(int count) {
            LensBeadGeometry.Beads row = LensBeadGeometry.compute(boxes(count), count, 0f, 0f,
                width, rowBottom, DENSITY);
            assertNotNull("the row wears beads with " + count + " icons", row);
            return LensBeadGeometry.withCapsule(row, fieldLeft, fieldTop, fieldRight, fieldBottom,
                DENSITY);
        }
    }

    @Test
    public void withTheFieldOnTheRowStillWearsBeads() {
        Dock dock = new Dock();
        for (int count = 1; count <= 7; count++) {
            LensBeadGeometry.Beads beads = dock.beads(count);
            assertEquals(count, beads.count());
            assertTrue(beads.hasCapsule());
            // The beads are the ones the row wears without the field: the band holds them alike.
            LensBeadGeometry.Beads without = LensBeadGeometry.compute(dock.boxes(count), count, 0f,
                0f, dock.width, dock.rowBottom, DENSITY);
            assertEquals(without, beads.withoutCapsule());
        }
    }

    @Test
    public void theCapsuleIsTheFieldsViewAsWideAsTheRowAStadium() {
        Dock dock = new Dock();
        LensBeadGeometry.Beads beads = dock.beads(5);
        assertEquals(dock.rowLeft, beads.capsuleLeft, 0f);
        assertEquals(dock.rowRight, beads.capsuleRight, 0f);
        assertEquals(dock.fieldTop, beads.capsuleTop, 0f);
        assertEquals(dock.fieldBottom, beads.capsuleBottom, 0f);
        assertEquals(48f * DENSITY, beads.capsuleBottom - beads.capsuleTop, 0.5f);
        assertEquals(24f * DENSITY, beads.capsuleRadiusPx(), 0.25f);
        // The well nests in it: 3 dp in all round, its corners the capsule's less that.
        float inset = DockLayoutPolicy.promptFieldWellInsetPx(DENSITY);
        assertEquals(3f * DENSITY, inset, 0.5f);
        assertEquals(PromptLineFieldView.WELL_HEIGHT_DP * DENSITY / 2f,
            beads.capsuleRadiusPx() - inset, 0.5f);
    }

    @Test
    public void theCapsuleKeepsTheBeadsOwnAirUnderThem() {
        Dock dock = new Dock();
        float air = LensBeadGeometry.CAPSULE_AIR_DP * DENSITY;
        assertEquals(LensBeadGeometry.MIN_GAP_DP, LensBeadGeometry.CAPSULE_AIR_DP, 0f);
        for (int count = 1; count <= 7; count++) {
            LensBeadGeometry.Beads beads = dock.beads(count);
            float under = beads.capsuleTop - (beads.centerY + beads.radiusPx);
            assertTrue(count + " icons: " + under / DENSITY + " dp", under >= air - 1e-3f);
            // Beads as tall as the band lets them be stand exactly the beads' gap over it.
            float bandHeld = dock.rowBottom - LensBeadGeometry.EDGE_CLEARANCE_DP * DENSITY;
            if (Math.abs(beads.centerY + beads.radiusPx - bandHeld) < 1e-3f)
                assertEquals(count + " icons", air, under, 1f);
        }
        // A full row: the beads keep that same gap between themselves.
        LensBeadGeometry.Beads seven = dock.beads(7);
        float gap = seven.centerX(1) - seven.centerX(0) - 2f * seven.radiusPx;
        assertTrue("gap " + gap / DENSITY, gap >= LensBeadGeometry.MIN_GAP_DP * DENSITY - 1e-3f);
    }

    @Test
    public void whileTheBandEasesInTheCapsuleWaitsUnderTheBeads() {
        Dock dock = new Dock();
        LensBeadGeometry.Beads row = dock.beads(5).withoutCapsule();
        float beadBottom = row.centerY + row.radiusPx;
        float air = LensBeadGeometry.CAPSULE_AIR_DP * DENSITY;
        // The field's view higher than the air allows: the capsule starts under the beads.
        LensBeadGeometry.Beads early = LensBeadGeometry.withCapsule(row, dock.fieldLeft,
            beadBottom, dock.fieldRight, beadBottom + 40f * DENSITY, DENSITY);
        assertEquals(beadBottom + air, early.capsuleTop, 1e-3f);
        // No height left under the air: no capsule yet.
        LensBeadGeometry.Beads none = LensBeadGeometry.withCapsule(row, dock.fieldLeft,
            beadBottom, dock.fieldRight, beadBottom + air, DENSITY);
        assertFalse(none.hasCapsule());
        assertEquals(row, none);
    }

    @Test
    public void theCapsuleStandsStillWhileTheBeadsRideAndMovesWithTheirOrigin() {
        Dock dock = new Dock();
        LensBeadGeometry.Beads rest = dock.beads(5);
        LensBeadGeometry.Beads moving = LensBeadGeometry.follow(rest, -40f * DENSITY,
            new float[0], 0, 0f, dock.width, 0.8f);
        assertEquals(rest.capsuleLeft, moving.capsuleLeft, 0f);
        assertEquals(rest.capsuleTop, moving.capsuleTop, 0f);
        assertEquals(rest.capsuleRight, moving.capsuleRight, 0f);
        assertEquals(rest.capsuleBottom, moving.capsuleBottom, 0f);
        // Every bead gone off the glass: the capsule is still there.
        LensBeadGeometry.Beads parked = LensBeadGeometry.follow(rest, 10_000f * DENSITY,
            new float[0], 0, 0f, dock.width, 1f);
        assertTrue(parked.hasCapsule());
        // A melt swells the beads, never the capsule; an offset moves both.
        LensBeadGeometry.Beads swollen = rest.withRadius(rest.radiusPx * 3f);
        assertEquals(rest.capsuleTop, swollen.capsuleTop, 0f);
        LensBeadGeometry.Beads shifted = rest.offset(10f, 20f);
        assertEquals(rest.capsuleLeft + 10f, shifted.capsuleLeft, 0f);
        assertEquals(rest.capsuleBottom + 20f, shifted.capsuleBottom, 0f);
        assertEquals(rest.capsuleRadiusPx(), shifted.capsuleRadiusPx(), 1e-3f);
        // Beads with and without the capsule are not the same beads.
        assertFalse(rest.equals(rest.withoutCapsule()));
    }

    @Test
    public void meltedBeadsCoverTheCapsuleToo() {
        Dock dock = new Dock();
        LensBeadGeometry.Beads beads = dock.beads(5);
        float height = dock.fieldTop - dock.layout.promptFieldTopPadPx
            + dock.layout.promptFieldBandPx;
        float cover = LensBeadGeometry.coverRadiusPx(beads, dock.width, height);
        // The capsule's far corners lie inside the bead nearest them at the cover radius.
        float[][] corners = {{beads.capsuleLeft, beads.capsuleBottom},
            {beads.capsuleRight, beads.capsuleBottom}};
        for (float[] corner : corners) {
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < beads.count(); i++) {
                float dx = corner[0] - beads.centerX(i);
                float dy = corner[1] - beads.centerY;
                nearest = Math.min(nearest, (float) Math.sqrt(dx * dx + dy * dy));
            }
            assertTrue(nearest <= cover);
        }
    }
}
