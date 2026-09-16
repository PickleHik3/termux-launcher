package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The edge stacks: what stands on each edge, how much the content gives up for it, and where a
 * lifted bar may land.
 *
 * <p>The equivalence sweep is the point of this file. {@link #contentInsetsMatchTheShippedMaxChain}
 * walks every arrangement the old model could express and compares the policy's answer against a
 * port of the arithmetic {@code TermuxActivity.applyTerminalOverlayInsets} does today, so the
 * replacement is a refactor rather than a redesign. The one place the two are meant to differ is
 * pinned by {@link #aStatusColumnSharingASideNowStacksInsteadOfMerging}.
 */
public class EdgeStackPolicyTest {

    // The pixel numbers the activity feeds the chain, with the dp figures they come from at this
    // test's own density. Distinct primes-ish values so a wrong term cannot cancel out.
    private static final int CUTOUT_LEFT = 33;
    private static final int CUTOUT_RIGHT = 17;
    private static final int RAIL_MARGIN = 30;              // DockLayoutPolicy.DOCK_RAIL_EDGE_MARGIN_DP
    private static final int RAIL_THICKNESS = 174;          // railWidthPx - railEdgeInsetPx
    private static final int KEYS_WIDTH = 141;              // extraKeysColumnKeysWidthPx()
    private static final int AZ_THICKNESS = 87;             // AzBarHostGeometry.thicknessPx
    private static final int STATUS_OUTER_MARGIN = 6;       // statusBarColumnOuterMarginPx()
    private static final int STATUS_COLUMN_BAR = 228;       // targetStatusBarHeightPx, vertical
    private static final int STATUS_ROW_BAR = 288;
    private static final int APPS_ROW_HEIGHT = 210;
    private static final int AZ_ROW_HEIGHT = 57;
    private static final int KEYS_ROW_HEIGHT = 120;

    private static final int EXTRA_KEYS_COLUMN = 2 * RAIL_MARGIN + KEYS_WIDTH;
    private static final int AZ_COLUMN = 2 * RAIL_MARGIN + AZ_THICKNESS;
    private static final int STATUS_COLUMN = STATUS_OUTER_MARGIN + STATUS_COLUMN_BAR;

    private static EdgeStackPolicy.Metrics metrics() {
        return EdgeStackPolicy.Metrics.builder()
            .cutout(CUTOUT_LEFT, CUTOUT_RIGHT)
            .status(STATUS_ROW_BAR, STATUS_COLUMN)
            .apps(APPS_ROW_HEIGHT, RAIL_THICKNESS)
            .az(AZ_ROW_HEIGHT, AZ_COLUMN)
            .extraKeys(KEYS_ROW_HEIGHT, EXTRA_KEYS_COLUMN)
            .build();
    }

    private static PlaceLayout layout(Edge status, RowPlacement apps, boolean azShown, Edge azEdge,
                                      RowPlacement keys) {
        return new PlaceLayout(status, apps, azShown, azEdge, keys, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
    }

    // ------------------------------------------------------------------ the shipped oracle

    /**
     * What {@code TermuxActivity.applyTerminalOverlayInsets} computes for one side today
     * (TermuxActivity.java:5749-5773), with the four footprints it calls out spelled in line:
     *
     * <ul>
     *   <li>{@code getDockLayout().railWidthPx} — DockLayoutPolicy.java:240-246, the side's cutout
     *       plus a fixed column.</li>
     *   <li>{@code extraKeysColumnFootprintPx} — TermuxActivity.java:9519-9524 over
     *       {@code ExtraKeysColumnGeometry.footprintPx}: the rail's width when the rail holds the
     *       same side, else the cutout, plus two margins and the keys.</li>
     *   <li>{@code statusBarColumnFootprintPx} — TermuxActivity.java:3549-3554 over
     *       {@code StatusBarEdgeGeometry.contentInsetPx}: the cutout and the bar's own outer
     *       margin, and <em>not</em> anything else on that edge — a status column and a rail share
     *       one column lengthwise today rather than standing beside each other.</li>
     *   <li>{@code azBarColumnFootprintPx} — TermuxActivity.java:6835-6839 over
     *       {@code AzBarHostGeometry.edgeInsetPx}: past the widest of the three above.</li>
     * </ul>
     */
    private static int legacyContentInsetPx(PlaceLayout layout, boolean right) {
        int cutout = right ? CUTOUT_RIGHT : CUTOUT_LEFT;
        RowPlacement railSide = right ? RowPlacement.RIGHT : RowPlacement.LEFT;
        Edge side = right ? Edge.RIGHT : Edge.LEFT;

        boolean railActive = layout.appsRow == railSide;
        int railWidthPx = railActive ? cutout + RAIL_THICKNESS : 0;
        int inset = railActive ? railWidthPx : cutout;

        int keysFootprint = 0;
        if (layout.extraKeys == railSide) {
            int edgeInset = railActive ? railWidthPx : cutout;
            keysFootprint = edgeInset + 2 * RAIL_MARGIN + KEYS_WIDTH;
        }
        inset = Math.max(inset, keysFootprint);

        int statusFootprint = 0;
        if (layout.statusBarEdge == side) {
            statusFootprint = cutout + STATUS_OUTER_MARGIN + STATUS_COLUMN_BAR;
        }
        inset = Math.max(inset, statusFootprint);

        boolean azOnSide = layout.azRowShown && PlaceChromePolicy.azBarEdge(layout) == side;
        if (azOnSide) {
            int azEdgeInset = Math.max(Math.max(cutout, railWidthPx),
                Math.max(keysFootprint, statusFootprint));
            inset = Math.max(inset, azEdgeInset + 2 * RAIL_MARGIN + AZ_THICKNESS);
        }
        return inset;
    }

    /** Every arrangement the old model could spell: the five stored enums, crossed. */
    private static List<PlaceLayout> everyOldArrangement() {
        List<PlaceLayout> all = new ArrayList<>(512);
        for (Edge status : Edge.values())
            for (RowPlacement apps : RowPlacement.values())
                for (boolean azShown : new boolean[] {true, false})
                    for (Edge azEdge : Edge.values())
                        for (RowPlacement keys : RowPlacement.values())
                            all.add(layout(status, apps, azShown, azEdge, keys));
        return all;
    }

    /** Whether the shipped chain merges the status column with something instead of stacking it. */
    private static boolean statusSharesASide(PlaceLayout layout, boolean right) {
        Edge side = right ? Edge.RIGHT : Edge.LEFT;
        RowPlacement railSide = right ? RowPlacement.RIGHT : RowPlacement.LEFT;
        return layout.statusBarEdge == side
            && (layout.appsRow == railSide || layout.extraKeys == railSide);
    }

    // ------------------------------------------------------------------ equivalence

    @Test
    public void contentInsetsMatchTheShippedMaxChain() {
        EdgeStackPolicy.Metrics metrics = metrics();
        int compared = 0;
        for (PlaceLayout layout : everyOldArrangement()) {
            EdgeStackPolicy.Insets insets = EdgeStackPolicy.contentInsets(layout, metrics);
            for (boolean right : new boolean[] {false, true}) {
                if (statusSharesASide(layout, right)) continue;
                assertEquals(layout + (right ? " right" : " left"),
                    legacyContentInsetPx(layout, right), right ? insets.right : insets.left);
                compared++;
            }
            // The sweep is over resolved arrangements, so both orientations read the same way;
            // what used to differ was the store's portrait clamp, which is gone.
            assertEquals(layout.toString(), insets,
                EdgeStackPolicy.contentInsets(layout, metrics));
        }
        assertEquals("every arrangement, both sides, minus the shared-column ones", 912, compared);
    }

    @Test
    public void aStatusColumnSharingASideNowStacksInsteadOfMerging() {
        // The one deliberate difference. Today the bar and the rail share one column, the bar
        // taking the top half of it, so the edge costs the wider of the two; stacked, it costs
        // both. Pinned here so the change cannot happen by accident.
        PlaceLayout shared = layout(Edge.LEFT, RowPlacement.LEFT, false, Edge.BOTTOM,
            RowPlacement.HIDDEN);
        int legacy = legacyContentInsetPx(shared, false);
        assertEquals(CUTOUT_LEFT + Math.max(RAIL_THICKNESS, STATUS_COLUMN), legacy);
        assertEquals(CUTOUT_LEFT + RAIL_THICKNESS + STATUS_COLUMN,
            EdgeStackPolicy.contentInsets(shared, metrics()).left);
        assertNotEquals(legacy, EdgeStackPolicy.contentInsets(shared, metrics()).left);
    }

    @Test
    public void aStatusColumnAloneOrWithTheAzBarAlreadyAgreed() {
        // The alphabets bar always did stand past the status column, so those two never needed the
        // merge and come out identical.
        for (PlaceLayout layout : new PlaceLayout[] {
            layout(Edge.RIGHT, RowPlacement.HIDDEN, false, Edge.BOTTOM, RowPlacement.HIDDEN),
            layout(Edge.RIGHT, RowPlacement.HIDDEN, true, Edge.RIGHT, RowPlacement.HIDDEN)}) {
            assertEquals(layout.toString(), legacyContentInsetPx(layout, true),
                EdgeStackPolicy.contentInsets(layout, metrics()).right);
        }
    }

    @Test
    public void aHiddenElementCostsTheEdgeNothing() {
        EdgeStackPolicy.Metrics metrics = metrics();
        PlaceLayout bare = layout(Edge.TOP, RowPlacement.HIDDEN, false, Edge.BOTTOM,
            RowPlacement.HIDDEN);
        EdgeStackPolicy.Insets insets = EdgeStackPolicy.contentInsets(bare, metrics);
        assertEquals(CUTOUT_LEFT, insets.left);
        assertEquals(CUTOUT_RIGHT, insets.right);
        assertEquals(STATUS_ROW_BAR, insets.top);
        assertEquals(0, insets.bottom);

        // The same arrangement with the two rows back on the bottom edge, so the bottom is the
        // sum of the three bands and nothing else moved.
        PlaceLayout full = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        EdgeStackPolicy.Insets stacked = EdgeStackPolicy.contentInsets(full, metrics);
        assertEquals(KEYS_ROW_HEIGHT + AZ_ROW_HEIGHT + APPS_ROW_HEIGHT, stacked.bottom);
        assertEquals(CUTOUT_LEFT, stacked.left);
        assertEquals(STATUS_ROW_BAR, stacked.top);
    }

    @Test
    public void anEmptyRailIsToldAsZeroThicknessAndCostsNothing() {
        // isDockRailShown() also asks whether the rail has any icons. The policy has no view to
        // ask, so a caller with an empty rail hands it a zero band and the edge reads bare.
        EdgeStackPolicy.Metrics empty = EdgeStackPolicy.Metrics.builder()
            .cutout(CUTOUT_LEFT, CUTOUT_RIGHT)
            .status(STATUS_ROW_BAR, STATUS_COLUMN)
            .apps(APPS_ROW_HEIGHT, 0)
            .az(AZ_ROW_HEIGHT, AZ_COLUMN)
            .extraKeys(KEYS_ROW_HEIGHT, EXTRA_KEYS_COLUMN)
            .build();
        PlaceLayout rail = layout(Edge.TOP, RowPlacement.LEFT, false, Edge.BOTTOM,
            RowPlacement.HIDDEN);
        assertEquals(CUTOUT_LEFT, EdgeStackPolicy.contentInsets(rail, empty).left);
    }

    // ------------------------------------------------------------------ the stack itself

    /**
     * The order {@code PlaceMiniatureView.computeBlocks} claimed its strips in before it read the
     * stack: outermost first, the status bar before anything else, a top A&#8211;Z band right
     * under it, then the side columns rail-first, then the bottom stack. The miniature loops over
     * {@link EdgeStackPolicy#stack} now, so this is the shipped picture rather than a second
     * ordering, and it is kept as the record of what that picture was.
     */
    private static List<Element> miniatureOrder(PlaceLayout layout, Edge edge) {
        List<Element> claimed = new ArrayList<>(4);
        boolean azShown = layout.azRowShown;
        Edge azEdge = PlaceChromePolicy.azBarEdge(layout);
        if (layout.statusBarEdge == edge) claimed.add(Element.STATUS);
        if (azShown && azEdge == Edge.TOP && edge == Edge.TOP) claimed.add(Element.AZ);
        if (layout.appsRow.isOnSide() && edgeOf(layout.appsRow) == edge) claimed.add(Element.APPS);
        if (layout.extraKeys.isOnSide() && edgeOf(layout.extraKeys) == edge)
            claimed.add(Element.EXTRA_KEYS);
        if (azShown && azEdge.isOnSide() && azEdge == edge) claimed.add(Element.AZ);
        if (layout.extraKeys == RowPlacement.BOTTOM && edge == Edge.BOTTOM)
            claimed.add(Element.EXTRA_KEYS);
        if (azShown && azEdge == Edge.BOTTOM && edge == Edge.BOTTOM) claimed.add(Element.AZ);
        if (layout.appsRow == RowPlacement.BOTTOM && edge == Edge.BOTTOM)
            claimed.add(Element.APPS);
        return claimed;
    }

    private static Edge edgeOf(RowPlacement placement) {
        return placement == RowPlacement.LEFT ? Edge.LEFT
            : placement == RowPlacement.RIGHT ? Edge.RIGHT : Edge.BOTTOM;
    }

    @Test
    public void stackReproducesTheMiniaturesStripOrder() {
        for (PlaceLayout layout : everyOldArrangement()) {
            for (Edge edge : Edge.values()) {
                // The miniature used to draw a bottom status bar outermost; the launcher draws it
                // innermost — StatusBarEdgeArrangement puts a bottom bar last in
                // terminal_content_column, which stands above the whole dock. The default stack
                // follows the launcher, the picture follows the stack, and the one arrangement the
                // old order got wrong is skipped here and pinned by
                // aBottomStatusBarIsTheInnermostBandOfTheBottomStack.
                if (edge == Edge.BOTTOM && layout.statusBarEdge == Edge.BOTTOM) continue;
                assertEquals(layout + " " + edge, miniatureOrder(layout, edge),
                    EdgeStackPolicy.stack(layout, edge));
            }
        }
    }

    @Test
    public void aBottomStatusBarIsTheInnermostBandOfTheBottomStack() {
        PlaceLayout bottom = layout(Edge.BOTTOM, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS, Element.STATUS),
            EdgeStackPolicy.stack(bottom, Edge.BOTTOM));
    }

    @Test
    public void theDefaultOrderIsTheStackTheLauncherAlreadyDraws() {
        assertEquals(Arrays.asList(0, 1, 2, 3), Arrays.asList(
            Element.STATUS.defaultOrder(Edge.TOP), Element.AZ.defaultOrder(Edge.TOP),
            Element.APPS.defaultOrder(Edge.TOP), Element.EXTRA_KEYS.defaultOrder(Edge.TOP)));
        assertEquals(Arrays.asList(0, 1, 2, 3), Arrays.asList(
            Element.EXTRA_KEYS.defaultOrder(Edge.BOTTOM), Element.AZ.defaultOrder(Edge.BOTTOM),
            Element.APPS.defaultOrder(Edge.BOTTOM), Element.STATUS.defaultOrder(Edge.BOTTOM)));
        for (Edge side : new Edge[] {Edge.LEFT, Edge.RIGHT}) {
            assertEquals(side.toString(), Arrays.asList(0, 1, 2, 3), Arrays.asList(
                Element.STATUS.defaultOrder(side), Element.APPS.defaultOrder(side),
                Element.EXTRA_KEYS.defaultOrder(side), Element.AZ.defaultOrder(side)));
        }
    }

    @Test
    public void aReorderedSlotMovesTheBandAndLeavesTheInsetAlone() {
        PlaceLayout stacked = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(stacked, Edge.BOTTOM));

        // A drop writes every band on the edge its new index, the way the editor will.
        PlaceLayout appsOutermost = stacked
            .withSlot(Element.APPS, Slot.on(Edge.BOTTOM, 0))
            .withSlot(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, 1))
            .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, 2));
        assertEquals(Arrays.asList(Element.APPS, Element.EXTRA_KEYS, Element.AZ),
            EdgeStackPolicy.stack(appsOutermost, Edge.BOTTOM));
        assertEquals("a re-order costs the edge exactly what it did before",
            EdgeStackPolicy.contentInsets(stacked, metrics()),
            EdgeStackPolicy.contentInsets(appsOutermost, metrics()));
    }

    @Test
    public void tiedOrdersFallBackToTheShippedStack() {
        // Every element keeps its own number, so two can hold the same one. The default stack
        // breaks the tie, which makes a stack deterministic whatever was stored.
        PlaceLayout tied = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM)
            .withSlot(Element.APPS, Slot.on(Edge.BOTTOM, 0))
            .withSlot(Element.AZ, Slot.on(Edge.BOTTOM, 0))
            .withSlot(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, 0));
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(tied, Edge.BOTTOM));
    }

    @Test
    public void theAzBarRidingTheAppsRowIgnoresItsOwnSlot() {
        // Its stored edge says LEFT, but the pinned apps stand along the bottom, so it rides them.
        PlaceLayout riding = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.LEFT,
            RowPlacement.HIDDEN);
        assertEquals(Edge.LEFT, riding.slot(Element.AZ).edge);
        assertEquals(Edge.BOTTOM, EdgeStackPolicy.edgeOf(riding, Element.AZ));
        assertTrue(EdgeStackPolicy.stack(riding, Edge.LEFT).isEmpty());
        // Its stored order is the one a left column would give it; riding, that is ignored too and
        // it takes the band it has always had under the apps row.
        assertEquals(Element.AZ.defaultOrder(Edge.LEFT), riding.slot(Element.AZ).order);
        assertEquals(Element.AZ.defaultOrder(Edge.BOTTOM),
            EdgeStackPolicy.orderOf(riding, Element.AZ, Edge.BOTTOM));
        assertEquals(Arrays.asList(Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(riding, Edge.BOTTOM));

        // With the apps row off the bottom the same stored edge stands it in its own column.
        PlaceLayout alone = layout(Edge.TOP, RowPlacement.HIDDEN, true, Edge.LEFT,
            RowPlacement.HIDDEN);
        assertEquals(Arrays.asList(Element.AZ), EdgeStackPolicy.stack(alone, Edge.LEFT));
    }

    // ------------------------------------------------------------------ drop targets

    private static List<Edge> edgesOffered(List<EdgeStackPolicy.Drop> drops) {
        List<Edge> edges = new ArrayList<>(4);
        for (EdgeStackPolicy.Drop drop : drops)
            if (!edges.contains(drop.edge)) edges.add(drop.edge);
        return edges;
    }

    private static int indicesFor(List<EdgeStackPolicy.Drop> drops, Edge edge) {
        int count = 0;
        for (EdgeStackPolicy.Drop drop : drops) if (drop.edge == edge) count++;
        return count;
    }

    @Test
    public void portraitOffersTheSideColumnsToo() {
        PlaceLayout layout = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        for (Element element : new Element[] {Element.STATUS, Element.APPS, Element.EXTRA_KEYS}) {
            assertEquals(element + " portrait",
                Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT),
                edgesOffered(EdgeStackPolicy.targets(layout, element, PlaceOrientation.PORTRAIT)));
            assertEquals(element + " landscape",
                Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT),
                edgesOffered(EdgeStackPolicy.targets(layout, element,
                    PlaceOrientation.LANDSCAPE)));
        }
    }

    @Test
    public void onlyTheStatusBarRefusesTheTray() {
        PlaceLayout layout = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        for (EdgeStackPolicy.Drop drop
            : EdgeStackPolicy.targets(layout, Element.STATUS, PlaceOrientation.PORTRAIT))
            assertFalse(drop.toString(), drop.hideAllowed);
        for (Element element : new Element[] {Element.APPS, Element.EXTRA_KEYS, Element.AZ})
            for (EdgeStackPolicy.Drop drop
                : EdgeStackPolicy.targets(layout, element, PlaceOrientation.PORTRAIT))
                assertTrue(element + " " + drop, drop.hideAllowed);
    }

    @Test
    public void anEdgeOffersOneMoreIndexThanItHasBands() {
        PlaceLayout layout = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        // The bottom holds extra keys, A-Z and the apps row. Lifting the status bar off the top
        // leaves three bands there, so there are four places to drop it into.
        List<EdgeStackPolicy.Drop> status =
            EdgeStackPolicy.targets(layout, Element.STATUS, PlaceOrientation.PORTRAIT);
        assertEquals(4, indicesFor(status, Edge.BOTTOM));
        // Its own edge holds only itself, so lifting it leaves one place to put it back.
        assertEquals(1, indicesFor(status, Edge.TOP));
        assertEquals(1, indicesFor(status, Edge.LEFT));

        // The extra keys lift out of the bottom, leaving two bands and three places.
        List<EdgeStackPolicy.Drop> keys =
            EdgeStackPolicy.targets(layout, Element.EXTRA_KEYS, PlaceOrientation.PORTRAIT);
        assertEquals(3, indicesFor(keys, Edge.BOTTOM));
        assertEquals(2, indicesFor(keys, Edge.TOP));
    }

    @Test
    public void theAzBarRidingTheAppsRowHasNowhereToGoButAway() {
        PlaceLayout riding = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertTrue(EdgeStackPolicy.targets(riding, Element.AZ, PlaceOrientation.PORTRAIT).isEmpty());

        // Away, its one slot back is the row it rides; without it a chip in the tray would lift
        // with nowhere to land but the tray it came from.
        PlaceLayout away = layout(Edge.TOP, RowPlacement.BOTTOM, false, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        List<EdgeStackPolicy.Drop> back =
            EdgeStackPolicy.targets(away, Element.AZ, PlaceOrientation.PORTRAIT);
        assertEquals(1, back.size());
        assertEquals(Edge.BOTTOM, back.get(0).edge);

        // Standing alone it picks its own edge like anything else.
        PlaceLayout alone = layout(Edge.TOP, RowPlacement.HIDDEN, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertEquals(Arrays.asList(Edge.TOP, Edge.BOTTOM, Edge.LEFT, Edge.RIGHT),
            edgesOffered(EdgeStackPolicy.targets(alone, Element.AZ, PlaceOrientation.PORTRAIT)));
    }

    // ------------------------------------------------------------------ what a drop leaves

    @Test
    public void aDropNumbersEveryBandOnTheEdgeItLandedOn() {
        PlaceLayout stacked = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(stacked, Edge.BOTTOM));

        PlaceLayout dropped = EdgeStackPolicy.withDrop(stacked, Element.APPS, Edge.BOTTOM, 0);
        assertEquals(Arrays.asList(Element.APPS, Element.EXTRA_KEYS, Element.AZ),
            EdgeStackPolicy.stack(dropped, Edge.BOTTOM));
        // Every band on the edge carries its new number, since a stack is read by comparing them.
        assertEquals(0, dropped.slot(Element.APPS).order);
        assertEquals(1, dropped.slot(Element.EXTRA_KEYS).order);
        assertEquals(2, dropped.slot(Element.AZ).order);
        assertEquals("the edge costs exactly what it did before",
            EdgeStackPolicy.contentInsets(stacked, metrics()),
            EdgeStackPolicy.contentInsets(dropped, metrics()));
    }

    @Test
    public void aDropOnAnotherEdgeLeavesTheEdgeItCameFromAlone() {
        PlaceLayout stacked = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        PlaceLayout moved = EdgeStackPolicy.withDrop(stacked, Element.APPS, Edge.LEFT, 0);
        assertEquals(Arrays.asList(Element.APPS), EdgeStackPolicy.stack(moved, Edge.LEFT));
        assertEquals("what is left of the bottom keeps its order",
            Arrays.asList(Element.EXTRA_KEYS, Element.AZ),
            EdgeStackPolicy.stack(moved, Edge.BOTTOM));
        assertEquals(Arrays.asList(Element.STATUS), EdgeStackPolicy.stack(moved, Edge.TOP));
    }

    @Test
    public void anIndexPastTheEndOfAStackIsTheInnermostBand() {
        PlaceLayout stacked = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        PlaceLayout innermost = EdgeStackPolicy.withDrop(stacked, Element.STATUS, Edge.BOTTOM, 99);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS, Element.STATUS),
            EdgeStackPolicy.stack(innermost, Edge.BOTTOM));
        assertTrue("and the top it left is bare", EdgeStackPolicy.stack(innermost, Edge.TOP)
            .isEmpty());
    }

    @Test
    public void aDropBringsAHiddenBarBackWithoutTouchingWhatWasAlreadyThere() {
        PlaceLayout away = layout(Edge.TOP, RowPlacement.HIDDEN, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ),
            EdgeStackPolicy.stack(away, Edge.BOTTOM));

        PlaceLayout back = EdgeStackPolicy.withDrop(away, Element.APPS, Edge.BOTTOM, 1);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.APPS, Element.AZ),
            EdgeStackPolicy.stack(back, Edge.BOTTOM));
        assertFalse("a bar dropped on an edge is a bar on screen", back.slot(Element.APPS).hidden);
    }

    @Test
    public void aReOrderedRowTakesTheRidingIndexWithIt() {
        // The index rides the pinned apps row, so it is a band of the bottom like any other here.
        // Its stored edge follows the edge it is drawn on, without which the drop the user made is
        // not the stack they end up with.
        PlaceLayout riding = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.LEFT,
            RowPlacement.BOTTOM);
        assertEquals(Edge.LEFT, riding.slot(Element.AZ).edge);
        assertEquals(Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EdgeStackPolicy.stack(riding, Edge.BOTTOM));

        PlaceLayout dropped = EdgeStackPolicy.withDrop(riding, Element.EXTRA_KEYS, Edge.BOTTOM, 1);
        assertEquals(Arrays.asList(Element.AZ, Element.EXTRA_KEYS, Element.APPS),
            EdgeStackPolicy.stack(dropped, Edge.BOTTOM));
        assertEquals(Edge.BOTTOM, dropped.slot(Element.AZ).edge);
    }

    @Test
    public void onlyABarThatMayHideIsPutAway() {
        PlaceLayout layout = layout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM,
            RowPlacement.BOTTOM);
        assertTrue(EdgeStackPolicy.withAway(layout, Element.APPS).slot(Element.APPS).hidden);
        assertEquals("the wall's pager rides the status bar, so it never goes away",
            layout, EdgeStackPolicy.withAway(layout, Element.STATUS));
        // It keeps the edge it would come back to.
        assertEquals(Edge.BOTTOM,
            EdgeStackPolicy.withAway(layout, Element.APPS).slot(Element.APPS).edge);
    }

    @Test
    public void thicknessReadsTheBandForTheEdgeItIsAskedAbout() {
        EdgeStackPolicy.Metrics metrics = metrics();
        assertEquals(STATUS_ROW_BAR,
            EdgeStackPolicy.thicknessPx(Element.STATUS, Edge.TOP, metrics));
        assertEquals(STATUS_COLUMN,
            EdgeStackPolicy.thicknessPx(Element.STATUS, Edge.RIGHT, metrics));
        assertEquals(APPS_ROW_HEIGHT,
            EdgeStackPolicy.thicknessPx(Element.APPS, Edge.BOTTOM, metrics));
        assertEquals(RAIL_THICKNESS,
            EdgeStackPolicy.thicknessPx(Element.APPS, Edge.LEFT, metrics));
        assertEquals(AZ_ROW_HEIGHT, EdgeStackPolicy.thicknessPx(Element.AZ, Edge.BOTTOM, metrics));
        assertEquals(AZ_COLUMN, EdgeStackPolicy.thicknessPx(Element.AZ, Edge.LEFT, metrics));
        assertEquals(KEYS_ROW_HEIGHT,
            EdgeStackPolicy.thicknessPx(Element.EXTRA_KEYS, Edge.BOTTOM, metrics));
        assertEquals(EXTRA_KEYS_COLUMN,
            EdgeStackPolicy.thicknessPx(Element.EXTRA_KEYS, Edge.RIGHT, metrics));
    }
}
