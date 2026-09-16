package com.termux.app.launcher.az;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.az.AzPreviewTargetPolicy.Resolution;
import com.termux.app.launcher.az.AzPreviewTargetPolicy.Side;
import com.termux.app.launcher.az.AzPreviewTargetPolicy.Target;
import com.termux.app.launcher.az.AzScrubGesture.Bounds;
import com.termux.app.launcher.az.AzScrubGesture.Geometry;
import com.termux.app.launcher.az.AzScrubGesture.Mode;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.Slot;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the A&#8211;Z scrub's matches go, for every order the bottom edge can be stacked in.
 *
 * <p>The defect this file is the answer to: the scrub only ever worked with the pinned apps row
 * directly above the letters, because the whole gesture read "towards the matches" as "up the
 * screen". Ordered any other way — the row two bands off, or on the far side of the letters
 * entirely — the letters did nothing at all. {@link #everyBottomOrderPointsAtTheRowItHas} is the
 * sweep: all six orders of {extra keys, apps row, A&#8211;Z}, each band shown or hidden, with and
 * without a bottom status bar, against an oracle that reads the answer off the screen positions
 * rather than off the policy.
 *
 * <p>{@link #theScrubReachesTheRowWhereverTheStackPutIt} is the same sweep with pixels under it:
 * the bands laid out bottom-up on a screen, the gesture driven off the letters towards the row's
 * own rectangle, and the lock asserted — plus the negative, that a band the finger merely crosses
 * on the way is not mistaken for the row.
 *
 * <p>Density is 1 throughout, so dp == px.
 */
public class AzPreviewTargetPolicyTest {

    // ---------------------------------------------------------------- layouts

    private static final Element[] BOTTOM_BANDS =
        {Element.EXTRA_KEYS, Element.APPS, Element.AZ};

    /**
     * A place whose bottom edge is stacked in {@code order} (outermost first, the way
     * {@link EdgeStackPolicy#stack} counts), with {@code hidden} put away and the status bar on
     * {@code statusEdge}.
     */
    private static PlaceLayout bottomStack(List<Element> order, Set<Element> hidden,
                                           Edge statusEdge) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        // The status bar is never hidden. On the bottom it is the innermost band, which is where
        // the launcher has always drawn it and the only place it keeps glass of its own.
        slots.put(Element.STATUS, Slot.on(statusEdge,
            statusEdge == Edge.BOTTOM ? order.size() : 0));
        for (int i = 0; i < order.size(); i++) {
            Element element = order.get(i);
            slots.put(element, hidden.contains(element)
                ? Slot.hiddenFrom(Edge.BOTTOM, i)
                : Slot.on(Edge.BOTTOM, i));
        }
        return new PlaceLayout(slots, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);
    }

    private static List<List<Element>> everyBottomOrder() {
        List<List<Element>> orders = new ArrayList<>();
        for (Element a : BOTTOM_BANDS) {
            for (Element b : BOTTOM_BANDS) {
                if (b == a) continue;
                for (Element c : BOTTOM_BANDS) {
                    if (c == a || c == b) continue;
                    orders.add(Arrays.asList(a, b, c));
                }
            }
        }
        return orders;
    }

    private static List<Set<Element>> everyHiddenSubset() {
        List<Set<Element>> subsets = new ArrayList<>();
        for (int mask = 0; mask < 8; mask++) {
            Set<Element> hidden = EnumSet.noneOf(Element.class);
            for (int bit = 0; bit < BOTTOM_BANDS.length; bit++) {
                if ((mask & (1 << bit)) != 0) hidden.add(BOTTOM_BANDS[bit]);
            }
            subsets.add(hidden);
        }
        return subsets;
    }

    // ---------------------------------------------------------------- the sweep

    @Test
    public void everyBottomOrderPointsAtTheRowItHas() {
        int cases = 0;
        for (List<Element> order : everyBottomOrder()) {
            for (Set<Element> hidden : everyHiddenSubset()) {
                for (Edge statusEdge : new Edge[]{Edge.BOTTOM, Edge.TOP}) {
                    PlaceLayout layout = bottomStack(order, hidden, statusEdge);
                    String what = order + " hidden=" + hidden + " status=" + statusEdge;
                    Resolution resolution = AzPreviewTargetPolicy.resolve(layout);

                    List<Element> stack = EdgeStackPolicy.stack(layout, Edge.BOTTOM);
                    int letters = stack.indexOf(Element.AZ);
                    int apps = stack.indexOf(Element.APPS);

                    if (letters < 0 || apps < 0) {
                        // Either there are no letters to scrub or no row to fill: the matches ride
                        // the strip, which always grows away from the bar.
                        assertEquals(what, Target.FLOATING_STRIP, resolution.target);
                        assertEquals(what, Side.INWARD, resolution.side);
                        assertTrue(what, resolution.transit.isEmpty());
                    } else {
                        assertEquals(what, Target.APPS_ROW, resolution.target);
                        // The stack counts outermost first, so a row further in than the letters
                        // is further from the screen's rim: the finger travels inward to reach it.
                        assertEquals(what, apps > letters ? Side.INWARD : Side.OUTWARD,
                            resolution.side);
                        assertEquals(what, Math.abs(apps - letters) - 1,
                            resolution.transit.size());
                        // Whatever is between them, read in the order the finger crosses it.
                        List<Element> expected = new ArrayList<>(
                            stack.subList(Math.min(apps, letters) + 1, Math.max(apps, letters)));
                        if (resolution.side == Side.OUTWARD) java.util.Collections.reverse(expected);
                        assertEquals(what, expected, resolution.transit);
                    }
                    cases++;
                }
            }
        }
        assertEquals(6 * 8 * 2, cases);
    }

    /** The order the launcher ships is the one every threshold was written for, unchanged. */
    @Test
    public void theShippedStackStillClimbs() {
        PlaceLayout layout = bottomStack(
            Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EnumSet.noneOf(Element.class), Edge.BOTTOM);
        Resolution resolution = AzPreviewTargetPolicy.resolve(layout);
        assertEquals(Target.APPS_ROW, resolution.target);
        assertEquals(Side.INWARD, resolution.side);
        assertTrue(resolution.transit.isEmpty());
        assertEquals(1f, resolution.sign(), 0f);
        // And the extra keys are still the ground behind the finger, which is what extends the
        // return band.
        assertTrue(AzPreviewTargetPolicy.beyondLetters(layout, Element.EXTRA_KEYS));
    }

    /** The letters above the icons: the same row, reached by coming down instead of climbing. */
    @Test
    public void aRowUnderTheLettersIsReachedOutward() {
        PlaceLayout layout = bottomStack(
            Arrays.asList(Element.APPS, Element.AZ, Element.EXTRA_KEYS),
            EnumSet.noneOf(Element.class), Edge.BOTTOM);
        Resolution resolution = AzPreviewTargetPolicy.resolve(layout);
        assertEquals(Target.APPS_ROW, resolution.target);
        assertEquals(Side.OUTWARD, resolution.side);
        assertEquals(-1f, resolution.sign(), 0f);
        // The keys are still the ground behind the finger, but they are above the letters now
        // rather than below them: "behind" follows the matches round with everything else.
        assertTrue(AzPreviewTargetPolicy.beyondLetters(layout, Element.EXTRA_KEYS));

        // Ordered outside the row instead, they are neither behind the finger nor on its way.
        PlaceLayout keysOutside = bottomStack(
            Arrays.asList(Element.EXTRA_KEYS, Element.APPS, Element.AZ),
            EnumSet.noneOf(Element.class), Edge.BOTTOM);
        assertEquals(Side.OUTWARD, AzPreviewTargetPolicy.resolve(keysOutside).side);
        assertFalse(AzPreviewTargetPolicy.beyondLetters(keysOutside, Element.EXTRA_KEYS));
    }

    /** A band between the two is crossed, not fallen back onto. */
    @Test
    public void keysBetweenTheLettersAndTheRowAreTransit() {
        PlaceLayout layout = bottomStack(
            Arrays.asList(Element.AZ, Element.EXTRA_KEYS, Element.APPS),
            EnumSet.noneOf(Element.class), Edge.BOTTOM);
        Resolution resolution = AzPreviewTargetPolicy.resolve(layout);
        assertEquals(Target.APPS_ROW, resolution.target);
        assertEquals(Side.INWARD, resolution.side);
        assertEquals(java.util.Collections.singletonList(Element.EXTRA_KEYS), resolution.transit);
        assertFalse(AzPreviewTargetPolicy.beyondLetters(layout, Element.EXTRA_KEYS));
    }

    /** No row to fill — hidden, or standing on a rail — and the matches float instead. */
    @Test
    public void aRowThatIsNotThereSendsTheMatchesToTheStrip() {
        PlaceLayout hiddenRow = bottomStack(
            Arrays.asList(Element.EXTRA_KEYS, Element.AZ, Element.APPS),
            EnumSet.of(Element.APPS), Edge.BOTTOM);
        assertTrue(AzPreviewTargetPolicy.resolve(hiddenRow).standsAlone());

        PlaceLayout rail = new PlaceLayout(Edge.TOP, PlaceLayout.RowPlacement.LEFT, true,
            Edge.BOTTOM, PlaceLayout.RowPlacement.BOTTOM, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
        Resolution railResolution = AzPreviewTargetPolicy.resolve(rail);
        assertTrue(railResolution.standsAlone());
        assertEquals(Side.INWARD, railResolution.side);

        // And a row on another edge is not the index's row either: moving one never moves the other.
        PlaceLayout apart = new PlaceLayout(Edge.TOP, PlaceLayout.RowPlacement.BOTTOM, true,
            Edge.TOP, PlaceLayout.RowPlacement.BOTTOM, KeyboardMode.RESIZE,
            KeyboardForm.DOCKED, 4, 5);
        assertTrue(AzPreviewTargetPolicy.resolve(apart).standsAlone());
    }

    // ---------------------------------------------------------------- the same sweep, in pixels

    private static final float SCREEN_BOTTOM = 2400f;
    private static final float SCREEN_RIGHT = 1080f;

    private static float thicknessOf(Element element) {
        switch (element) {
            case EXTRA_KEYS: return 120f;
            case APPS: return 200f;
            case AZ: return 60f;
            default: return 90f;
        }
    }

    /** Each band's rectangle for a bottom stack laid out from the screen's rim inwards. */
    private static Map<Element, Bounds> layOut(PlaceLayout layout) {
        Map<Element, Bounds> rects = new EnumMap<>(Element.class);
        float bottom = SCREEN_BOTTOM;
        for (Element element : EdgeStackPolicy.stack(layout, Edge.BOTTOM)) {
            float top = bottom - thicknessOf(element);
            rects.put(element, new Bounds(0f, top, SCREEN_RIGHT, bottom));
            bottom = top;
        }
        return rects;
    }

    /** The geometry the activity would hand the gesture for that arrangement, at density 1. */
    private static Geometry geometryFor(PlaceLayout layout, Map<Element, Bounds> rects) {
        Bounds letters = rects.get(Element.AZ);
        Bounds row = rects.get(Element.APPS);
        Bounds keys = rects.get(Element.EXTRA_KEYS);
        boolean keysBehind = keys != null
            && AzPreviewTargetPolicy.beyondLetters(layout, Element.EXTRA_KEYS);
        return new Geometry(
            letters.left, letters.top, letters.height(),
            keysBehind ? keys.height() : 0f,
            letters,
            row == null ? Bounds.EMPTY : row,
            keysBehind ? keys : Bounds.EMPTY,
            1f,
            AzPreviewTargetPolicy.resolve(layout).sign());
    }

    @Test
    public void theScrubReachesTheRowWhereverTheStackPutIt() {
        for (List<Element> order : everyBottomOrder()) {
            for (Edge statusEdge : new Edge[]{Edge.BOTTOM, Edge.TOP}) {
                PlaceLayout layout =
                    bottomStack(order, EnumSet.noneOf(Element.class), statusEdge);
                Map<Element, Bounds> rects = layOut(layout);
                Geometry geometry = geometryFor(layout, rects);
                Bounds letters = rects.get(Element.AZ);
                Bounds row = rects.get(Element.APPS);
                Resolution resolution = AzPreviewTargetPolicy.resolve(layout);
                String what = order + " status=" + statusEdge;

                // Down in the middle of the letters, then a short pull towards the matches: the
                // letter is locked and the preview is held, without the finger reaching the row.
                Driver driver = new Driver(geometry, letters);
                driver.down('B', letters.height() * 0.5f);
                float lifted = resolution.side == Side.INWARD
                    ? (letters.height() * 0.5f) - 20f
                    : (letters.height() * 0.5f) + 20f;
                assertEquals(what, Mode.UPWARD_LOCKED, driver.move('B', lifted).mode);

                // And on into the row's own rectangle, which is what the matches fill.
                AzScrubGesture.Decision onRow = driver.moveToRaw(row.top + (row.height() * 0.5f));
                assertEquals(what, Mode.ICON_TRACKING_LOCKED, onRow.mode);
                assertTrue(what, onRow.requestFocusResolve);

                // A band in between is crossed, not landed on: it is the row's rectangle the
                // preview targets, not "the next band along".
                if (!resolution.transit.isEmpty()) {
                    Bounds crossed = rects.get(resolution.transit.get(0));
                    Driver again = new Driver(geometry, letters);
                    again.down('B', letters.height() * 0.5f);
                    again.move('B', lifted);
                    assertEquals(what + " transit " + resolution.transit.get(0),
                        Mode.UPWARD_LOCKED,
                        again.moveToRaw(crossed.top + (crossed.height() * 0.5f)).mode);
                }
            }
        }
    }

    /** Drives one gesture in the canonical frame of a bottom bar, which is the screen itself. */
    private static final class Driver {

        private final AzScrubGesture gesture;
        private final Geometry geometry;
        private final Bounds letters;
        private long eventTime = 5_000L;
        private float touchX = 500f;

        Driver(Geometry geometry, Bounds letters) {
            this.gesture = new AzScrubGesture(() -> 10_000L);
            this.geometry = geometry;
            this.letters = letters;
        }

        AzScrubGesture.Decision down(char letter, float touchY) {
            return gesture.onDown(letter, 0, touchX, touchY, letters.left + touchX,
                letters.top + touchY, eventTime, geometry);
        }

        AzScrubGesture.Decision move(char letter, float touchY) {
            eventTime += 16L;
            return gesture.onMove(letter, 0, touchX, touchY, letters.left + touchX,
                letters.top + touchY, eventTime, geometry);
        }

        AzScrubGesture.Decision moveToRaw(float rawY) {
            return move('B', rawY - letters.top);
        }
    }
}
