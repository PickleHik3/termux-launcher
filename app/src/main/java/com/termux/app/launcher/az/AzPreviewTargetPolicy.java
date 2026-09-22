package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the A&#8211;Z scrub's matches fill, and which way the finger travels off the letters to
 * reach it. One answer, read off the edge's stack, for both the gesture and the layers that draw
 * the preview.
 *
 * <p>The scrub was written for one arrangement: the letters as the dock's row with the pinned apps
 * directly above them, so "towards the matches" was always "up the screen", and every threshold —
 * the upward lock, the capture wedge, the return band, the overshoot that picks a match — said so.
 * {@link AzBarFrame} already freed the <em>edge</em> from that: it turns the screen so a bar on any
 * edge is judged as a bottom bar with its track above it. What it could not free was the
 * <em>order</em>. Once the user can stack the bottom edge however they like, the apps row can sit
 * two bands away from the letters, or on the far side of them entirely, and a gesture that only
 * ever climbs never reaches it.
 *
 * <p>So the direction is resolved here instead of assumed:
 *
 * <ul>
 *   <li>The matches fill the <b>apps row</b> whenever the index rides it — the two of them on the
 *       same edge with that row lying down ({@link PlaceChromePolicy#azRidesAppsRow}) — at any
 *       distance in the stack and on either side of the letters. Otherwise they ride the
 *       <b>floating strip</b>, which grows away from the bar on every edge
 *       ({@link AzFloatingStripPolicy}).</li>
 *   <li>{@link Side} is which way that target lies from the letters, in the canonical frame the
 *       gesture is written in: {@link Side#INWARD} away from the screen edge the bar stands on,
 *       which is the shipped arrangement, or {@link Side#OUTWARD} past the letters towards it.</li>
 *   <li>{@link Resolution#transit} is what stands between the two, so a band the finger merely
 *       crosses on its way to the row is not mistaken for the ground it came from.</li>
 * </ul>
 *
 * <p>Pure: no {@code View}, no {@code Context}, no pixels. It reads a {@link PlaceLayout} and
 * answers in elements and a sign.
 */
public final class AzPreviewTargetPolicy {

    private AzPreviewTargetPolicy() {}

    /** What the matches fill while the finger holds a letter. */
    public enum Target {
        /** The pinned apps row, wherever it stands in the letters' own stack. */
        APPS_ROW,
        /** The band of matches that floats beside the letters when there is no row to fill. */
        FLOATING_STRIP
    }

    /**
     * Which way the target lies from the letters, in the canonical frame: canonical {@code +y}
     * points towards the screen edge the bar stands on and past it.
     */
    public enum Side {
        /** Away from that edge — the shipped arrangement, and where the floating strip always is. */
        INWARD(1f),
        /** Past the letters, towards the edge they stand on. */
        OUTWARD(-1f);

        /**
         * The sign every threshold is measured with: multiply a canonical displacement by it to
         * get "how far towards the target". {@code +1} is the direction the scrub was written for.
         */
        public final float sign;

        Side(float sign) {
            this.sign = sign;
        }

        public boolean isOutward() {
            return this == OUTWARD;
        }
    }

    /** Where the matches go for one arrangement. */
    public static final class Resolution {

        @NonNull public final Target target;
        @NonNull public final Side side;
        /**
         * The bands standing between the letters and the target, in the order the finger crosses
         * them. Empty when the target is next to the letters, and always empty for the floating
         * strip, which floats clear of the stack.
         */
        @NonNull public final List<Element> transit;

        Resolution(@NonNull Target target, @NonNull Side side, @NonNull List<Element> transit) {
            this.target = target;
            this.side = side;
            this.transit = transit;
        }

        /** True when the matches fill the pinned apps row rather than a strip of their own. */
        public boolean fillsAppsRow() {
            return target == Target.APPS_ROW;
        }

        /** True when the letters stand between the screen's edge and the matches. */
        public boolean standsAlone() {
            return target == Target.FLOATING_STRIP;
        }

        /** {@link Side#sign}, which is what the gesture multiplies its displacements by. */
        public float sign() {
            return side.sign;
        }

        @NonNull @Override public String toString() {
            return "Resolution{" + target + "," + side + ",transit=" + transit + "}";
        }
    }

    private static final Resolution STRIP =
        new Resolution(Target.FLOATING_STRIP, Side.INWARD, Collections.<Element>emptyList());

    /**
     * Where {@code layout}'s scrub puts its matches.
     *
     * <p>An index with no row to ride — the apps hidden, or on a rail, or on another edge — gets
     * the floating strip, which always grows away from the bar. An index riding the row reads the
     * two positions off {@link EdgeStackPolicy#stack}, which counts outermost first: a row with the
     * <em>higher</em> index stands further from the screen's rim than the letters, so the finger
     * travels {@link Side#INWARD} to reach it, and a lower one is {@link Side#OUTWARD}.
     */
    @NonNull
    public static Resolution resolve(@NonNull PlaceLayout layout) {
        if (!PlaceChromePolicy.azRidesAppsRow(layout)) return STRIP;
        Edge edge = PlaceChromePolicy.azBarEdge(layout);
        List<Element> stack = EdgeStackPolicy.stack(layout, edge);
        int letters = stack.indexOf(Element.AZ);
        int apps = stack.indexOf(Element.APPS);
        // Both are shown and share the edge, so both are in the stack; the guard is for a caller
        // that built a layout by hand rather than for anything the store can produce.
        if (letters < 0 || apps < 0 || letters == apps) return STRIP;
        Side side = apps > letters ? Side.INWARD : Side.OUTWARD;
        int lo = Math.min(letters, apps);
        int hi = Math.max(letters, apps);
        List<Element> between = stack.subList(lo + 1, hi);
        if (between.isEmpty()) {
            return new Resolution(Target.APPS_ROW, side, Collections.<Element>emptyList());
        }
        List<Element> transit = new ArrayList<>(between);
        // In the order the finger crosses them: the stack counts outwards-in, so a row the finger
        // climbs to is read forwards and one it comes down to is read backwards.
        if (side == Side.OUTWARD) Collections.reverse(transit);
        return new Resolution(Target.APPS_ROW, side, Collections.unmodifiableList(transit));
    }

    /**
     * Whether {@code element} stands beyond the letters on the far side from the matches — the
     * ground behind the finger rather than something on its way.
     *
     * <p>It is what decides whether a band extends the scrub's return band and its letter-filter
     * capture: the extra keys under a bottom bar always did, and still do, but the same keys
     * ordered <em>between</em> the letters and the row are a band to cross, and a return band
     * stretched over them would drop the lock the moment the finger set out.
     */
    public static boolean beyondLetters(@NonNull PlaceLayout layout, @NonNull Element element) {
        if (element == Element.AZ) return false;
        if (!EdgeStackPolicy.isShown(layout, element)) return false;
        if (!EdgeStackPolicy.isShown(layout, Element.AZ)) return false;
        Edge edge = PlaceChromePolicy.azBarEdge(layout);
        if (EdgeStackPolicy.edgeOf(layout, element) != edge) return false;
        List<Element> stack = EdgeStackPolicy.stack(layout, edge);
        int letters = stack.indexOf(Element.AZ);
        int band = stack.indexOf(element);
        if (letters < 0 || band < 0) return false;
        // Beyond means on the opposite side of the letters from the target; the strip's side is
        // inward, so for a standalone index "beyond" is everything outside the letters.
        return resolve(layout).side == Side.INWARD ? band < letters : band > letters;
    }

    /** The element the resolution names, or null for the floating strip. */
    @Nullable
    public static Element targetElement(@NonNull Resolution resolution) {
        return resolution.fillsAppsRow() ? Element.APPS : null;
    }
}
