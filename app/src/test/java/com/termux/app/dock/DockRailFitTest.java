package com.termux.app.dock;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;

import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The landscape rail (#146): every pin in one column, as many whole slots on show as the band
 * holds and the rest scrolled to, and nothing the rail draws reaching past the band its side stack
 * leaves it, so it never stands under the A&#8211;Z card in either Style.
 *
 * <p>At density 2.75, the table's own in {@link DockLayoutPolicyTest}: a rail slot is
 * 105 + 2 &times; 28 = 161 px.
 */
public class DockRailFitTest {

    private static final float DENSITY = 2.75f;
    private static final int SLOT = 161;

    /** The frame inset a side stack keeps at each end: none under Docked, the card's under Floating. */
    private static final int DOCKED_FRAME_INSET = 0;
    private static final int FLOATING_FRAME_INSET = 26;
    /** The rail's own air at each end of its scroll ({@code DockLayout.railAirPx}), any figure. */
    private static final int RAIL_AIR = 17;

    // ------------------------------------------------------------------ the fit

    @Test
    public void theSlotIsTheTablesOwn() {
        assertEquals(SLOT, DockLayoutPolicy.railSlotLengthPx(DENSITY));
    }

    @Test
    public void sevenPinsInABandThatFitsThreeShowThreeAndScrollTheRest() {
        int band = 3 * SLOT + 100;
        DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(band,
            DockLayoutPolicy.railContentLengthPx(7, SLOT), SLOT);

        assertEquals("seven slots end to end", 7 * SLOT, fit.contentLengthPx);
        assertEquals(3, fit.visibleSlots);
        assertTrue(fit.scrolls);
        assertEquals("the scroll stands three whole slots, so none is cut at rest",
            3 * SLOT, fit.viewportLengthPx);
        assertEquals("and travels the four it does not show", 4 * SLOT, fit.scrollRangePx());
    }

    @Test
    public void aBandThatFitsEveryPinDoesNotScroll() {
        DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(1362,
            DockLayoutPolicy.railContentLengthPx(7, SLOT), SLOT);

        assertFalse(fit.scrolls);
        assertEquals(7, fit.visibleSlots);
        assertEquals("a rail that fits keeps its whole band", 1362, fit.viewportLengthPx);
        assertEquals(0, fit.scrollRangePx());
    }

    @Test
    public void aBandExactlyAsLongAsTheColumnDoesNotScroll() {
        DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(7 * SLOT,
            DockLayoutPolicy.railContentLengthPx(7, SLOT), SLOT);
        assertFalse(fit.scrolls);
        assertEquals(7, fit.visibleSlots);
    }

    @Test
    public void aBandShorterThanOneSlotShowsWhatItCanAndScrolls() {
        DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(100,
            DockLayoutPolicy.railContentLengthPx(3, SLOT), SLOT);
        assertTrue(fit.scrolls);
        assertEquals(0, fit.visibleSlots);
        assertEquals(100, fit.viewportLengthPx);
        assertEquals(3 * SLOT - 100, fit.scrollRangePx());
    }

    @Test
    public void nothingPinnedClaimsNothingAndNeverScrolls() {
        DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(800, 0, SLOT);
        assertFalse(fit.scrolls);
        assertEquals(0, fit.visibleSlots);
    }

    @Test
    public void theRailNeverStandsLongerThanItsBandAndScrollsToItsLastPin() {
        for (float density : new float[] {2f, 2.75f, 3.5f}) {
            int slot = DockLayoutPolicy.railSlotLengthPx(density);
            for (int band = 0; band <= 1600; band += 37) {
                for (int pins = 0; pins <= 14; pins++) {
                    int content = DockLayoutPolicy.railContentLengthPx(pins, slot);
                    DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(band, content, slot);
                    String at = "density=" + density + " band=" + band + " pins=" + pins;
                    assertTrue(at, fit.viewportLengthPx <= band);
                    assertTrue(at, fit.visibleSlots * slot <= fit.viewportLengthPx);
                    assertTrue(at, fit.visibleSlots <= pins);
                    if (fit.scrolls) {
                        assertEquals(at + ": the last pin is reached", content,
                            fit.viewportLengthPx + fit.scrollRangePx());
                    } else {
                        assertEquals(at, 0, fit.scrollRangePx());
                    }
                    if (fit.scrolls && band >= slot)
                        assertEquals(at + ": whole slots only", 0, fit.viewportLengthPx % slot);
                }
            }
        }
    }

    // ---------------------------------------------- the rail and the A–Z card, both Styles

    /**
     * The audit's cell (e4): the rail on the left, the letters on the dock's bottom row with the
     * extra keys. The dock keeps the whole width, so the two never share the side's length while
     * the rail ends where its side stack ends: above the canvas band's own end on the Workspace,
     * where the dock stands below the band, and above the dock's whole reach on Home, where the
     * canvas runs on under it. In Docked and in Floating, for short and tall sides.
     */
    @Test
    public void theRailEndsAboveTheDockInBothStylesWhateverTheSidesLength() {
        PlaceLayout layout = layout(Edge.LEFT, 0, Edge.BOTTOM, 0);
        int air = RAIL_AIR;
        int reach = 420;
        for (int frame : new int[] {DOCKED_FRAME_INSET, FLOATING_FRAME_INSET}) {
            for (boolean home : new boolean[] {false, true}) {
                int bottomInset = EdgeStackPolicy.sideStackBottomInsetPx(layout, Edge.LEFT, home,
                    reach);
                assertEquals("Home's side ends above the dock's whole reach",
                    home ? reach : 0, bottomInset);
                for (int side : new int[] {480, 620, 800, 1080, 1362}) {
                    int top = frame;
                    int bottom = frame + bottomInset;
                    // The scroll fills what the side stack's padding leaves; its own air comes
                    // off that, and it draws nothing past its own bounds.
                    int scrollPx = Math.max(0, side - top - bottom);
                    int band = Math.max(0, scrollPx - 2 * air);
                    DockLayoutPolicy.RailFit fit = DockLayoutPolicy.railFit(band,
                        DockLayoutPolicy.railContentLengthPx(7, SLOT), SLOT);
                    int railEnd = top + Math.min(scrollPx, air + fit.viewportLengthPx + air);
                    // Where the A–Z card can begin: the band's own end on the Workspace, the
                    // dock's reach above it on Home, the frame's inset kept either way.
                    int cardStart = side - bottomInset;
                    String at = "frame=" + frame + " home=" + home + " side=" + side;
                    assertTrue(at + ": " + railEnd + " <= " + (cardStart - frame),
                        railEnd <= cardStart - frame);
                    assertTrue(at, fit.visibleSlots * SLOT <= band);
                }
            }
        }
    }

    /**
     * The letters standing on the rail's own side: two columns side by side across the edge, in
     * either order, each the side's whole length and neither over the other, and the side giving
     * up both.
     */
    @Test
    public void aSideIndexAndTheRailStandSideBySideInEitherOrder() {
        EdgeStackPolicy.Metrics metrics = metrics();
        for (int azOrder : new int[] {0, 1}) {
            PlaceLayout layout = layout(Edge.LEFT, 1 - azOrder, Edge.LEFT, azOrder);
            List<Element> stack = EdgeStackPolicy.stack(layout, Edge.LEFT);
            assertTrue(stack.contains(Element.APPS));
            assertTrue(stack.contains(Element.AZ));
            assertEquals("the outer one is the one ordered first",
                azOrder == 0 ? Element.AZ : Element.APPS, stack.get(0));

            int rail = EdgeStackPolicy.thicknessPx(Element.APPS, Edge.LEFT, metrics);
            int az = EdgeStackPolicy.thicknessPx(Element.AZ, Edge.LEFT, metrics);
            int railFrom = offsetOf(stack, Element.APPS, metrics);
            int azFrom = offsetOf(stack, Element.AZ, metrics);
            assertTrue("disjoint across the edge",
                railFrom + rail <= azFrom || azFrom + az <= railFrom);
            assertEquals("the side gives up both, past its cutout",
                metrics.cutoutLeftPx + rail + az,
                EdgeStackPolicy.contentInsets(layout, metrics).left);
        }
    }

    // ---------------------------------------------------------------- portrait

    @Test
    public void portraitNumbersDoNotMove() {
        // The backstop a row lying down falls back on, and what AppearanceLooks reads: unchanged.
        assertEquals(2 * SLOT, DockLayoutPolicy.maxRowBandPx(DENSITY));
        assertEquals(322, DockLayoutPolicy.maxRowBandPx(DENSITY));
        // The rail's own measures, as DockLayoutPolicyTest pins them.
        assertEquals(105, DockLayoutPolicy.railIconSizePx(DENSITY));
        assertEquals(28, DockLayoutPolicy.railIconSpacingPx(DENSITY));
    }

    // ---------------------------------------------------------------- helpers

    private static int offsetOf(List<Element> stack, Element element,
                                EdgeStackPolicy.Metrics metrics) {
        int offset = 0;
        for (Element band : stack) {
            if (band == element) return offset;
            offset += EdgeStackPolicy.thicknessPx(band, Edge.LEFT, metrics);
        }
        throw new AssertionError(element + " is not on the edge");
    }

    private static EdgeStackPolicy.Metrics metrics() {
        return EdgeStackPolicy.Metrics.builder()
            .cutout(33, 17)
            .status(88, 120)
            .apps(210, 174)
            .az(57, 147)
            .extraKeys(120, 201)
            .build();
    }

    /** The pins on {@code apps}, the letters on {@code az}, the status bar on top, the keys below. */
    private static PlaceLayout layout(Edge apps, int appsOrder, Edge az, int azOrder) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values())
            slots.put(element, Slot.on(Edge.BOTTOM, element));
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        slots.put(Element.APPS, Slot.on(apps, appsOrder));
        slots.put(Element.AZ, Slot.on(az, azOrder));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }
}
