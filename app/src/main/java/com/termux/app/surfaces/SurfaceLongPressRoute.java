package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.Element;

import java.util.List;

/**
 * Where a long-press opens the surface popup (editor-redesign §3, "Long-press: three targets"), as
 * a pure answer: which of the three targets is under the press, which of the dock's bars it is on,
 * and whether a child under the finger keeps a long-press of its own.
 *
 * <pre>
 *   Answer answer = SurfaceLongPressRoute.route(x, y, zones, owners);
 *   if (answer.opensPopup()) ...   // DOCK on the A–Z host's margin, STATUS on bare bar, ...
 * </pre>
 *
 * <p>The rule table: anywhere on the dock's frame (the apps row, the A–Z and extra-keys strips,
 * between items), anywhere on the status bar, the keyboard's frame and top edge (the band above
 * its top row, at least {@link #KEYBOARD_TOP_BAND_DP} tall, and the margins beside and under the
 * rows; a Floating keyboard's card with its handle row). What keeps its own long-press: app icons
 * (the App menu), extra keys, the A–Z letters (the scrub starts on the touch, so a press on them is
 * already a scrub), keyboard keys (each key's cell, gaps included), and on the status bar the window
 * chips and pinned notifications, which lift or open a menu on a hold. The terminal and Home are
 * no target at all, so their long-presses are never seen here.</p>
 *
 * <p>Zones are given in priority order: the first that holds the point is the target, so a
 * keyboard standing inside the dock's stack is listed before the dock's frame. Every box is in
 * screen pixels. No views: held by JVM tests.</p>
 */
public final class SurfaceLongPressRoute {

    private SurfaceLongPressRoute() {}

    /**
     * The least height of the keyboard's frame above its top row of keys, in dp: the band a hold
     * opens the keyboard's popup on (§3, "Its frame and top edge"). A key's cell, its gaps to the
     * neighbours included, is the key's, so without this band a Docked keyboard has only the
     * keys' own few-dp top margin, which no thumb finds.
     */
    public static final int KEYBOARD_TOP_BAND_DP = 12;

    /**
     * The inner top padding a Docked keyboard's host takes so the frame above its top row is at
     * least {@link #KEYBOARD_TOP_BAND_DP}: what the host already has, or more when its top margin,
     * its padding and the keys' own top margin together fall short. A host already that tall (the
     * capsule's gap and rim) keeps its padding.
     *
     * @param topMarginPx the host's top margin inside the keyboard's container
     * @param innerTopPaddingPx the padding the host's shape gives it
     * @param keysTopMarginPx the keyboard view's own margin above its first row
     */
    public static int keyboardTopPaddingPx(float density, int topMarginPx, int innerTopPaddingPx,
                                           float keysTopMarginPx) {
        int band = Math.round(Math.max(0f, density) * KEYBOARD_TOP_BAND_DP);
        int above = Math.max(0, topMarginPx) + Math.max(0, Math.round(keysTopMarginPx));
        return Math.max(Math.max(0, innerTopPaddingPx), band - above);
    }

    /** The three surfaces a long-press can open the popup on. */
    public enum Target {
        DOCK(EditorDoor.POPUP_DOCK, EditorCard.DOCK),
        STATUS(EditorDoor.POPUP_STATUS, EditorCard.STATUS),
        KEYBOARD(EditorDoor.POPUP_KEYBOARD, EditorCard.KEYBOARD);

        /** The door the popup's Edit goes through. */
        @NonNull public final EditorDoor door;
        /** The card that door opens. */
        @NonNull public final EditorCard card;

        Target(@NonNull EditorDoor door, @NonNull EditorCard card) {
            this.door = door;
            this.card = card;
        }

        /** Whether the popup offers Docked and Floating: the keyboard's alone. */
        public boolean offersForm() {
            return this == KEYBOARD;
        }
    }

    /** An axis-aligned box in screen pixels. */
    public static final class Box {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Box(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean isEmpty() {
            return !(right > left && bottom > top);
        }

        public boolean contains(float x, float y) {
            return !isEmpty() && x >= left && x < right && y >= top && y < bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        @NonNull @Override public String toString() {
            return "Box{" + left + "," + top + "," + right + "," + bottom + "}";
        }
    }

    /** One surface on screen: what it opens, which of the dock's bars it is, and where. */
    public static final class Zone {
        @NonNull public final Target target;
        /** The dock's bar ({@link Element#APPS}, {@link Element#AZ}, {@link Element#EXTRA_KEYS}),
         *  or null for the dock's frame between bars and for the other two targets. */
        @Nullable public final Element bar;
        @NonNull public final Box box;

        public Zone(@NonNull Target target, @Nullable Element bar, @NonNull Box box) {
            this.target = target;
            this.bar = bar;
            this.box = box;
        }
    }

    /** What a press means. */
    public static final class Answer {
        public static final Answer NONE = new Answer(null, false);

        /** The zone under the press, or null when the press is on none of the three targets. */
        @Nullable public final Zone zone;
        /** A child under the finger keeps its own long-press, so the popup stays shut. */
        public final boolean childOwns;

        Answer(@Nullable Zone zone, boolean childOwns) {
            this.zone = zone;
            this.childOwns = childOwns;
        }

        public boolean opensPopup() {
            return zone != null && !childOwns;
        }

        @Nullable
        public Target target() {
            return zone == null ? null : zone.target;
        }

        @Nullable
        public Element bar() {
            return zone == null ? null : zone.bar;
        }
    }

    /**
     * What a press at ({@code x}, {@code y}) means: the first zone holding it, and whether any of
     * {@code owners} (the boxes of children with a long-press of their own) holds it too.
     */
    @NonNull
    public static Answer route(float x, float y, @NonNull List<Zone> zones,
                               @NonNull List<Box> owners) {
        Zone hit = null;
        for (Zone zone : zones) {
            if (zone.box.contains(x, y)) {
                hit = zone;
                break;
            }
        }
        if (hit == null) return Answer.NONE;
        for (Box owner : owners) {
            if (owner.contains(x, y)) return new Answer(hit, true);
        }
        return new Answer(hit, false);
    }

    /**
     * Whether a finger that has moved ({@code dx}, {@code dy}) since its touch-down is still a
     * hold: within the touch slop. Past it, the gesture is a swipe or a scrub, never the popup.
     */
    public static boolean stillHolding(float dx, float dy, float slopPx) {
        float slop = Math.max(0f, slopPx);
        return dx * dx + dy * dy <= slop * slop;
    }

    /**
     * The element the popup's Hide puts away, the way the card's eye does: the status bar's for
     * the status bar, the bar pressed on the dock (the apps row from the frame between bars, as
     * the Dock card's eye does), and null for the keyboard, whose eye switches it off instead.
     */
    @Nullable
    public static Element hides(@NonNull Target target, @Nullable Element bar) {
        switch (target) {
            case STATUS: return Element.STATUS;
            case DOCK: return bar == null ? Element.APPS : bar;
            case KEYBOARD:
            default: return null;
        }
    }

    /**
     * The box the popup is placed against, its arrow pointing at the press: across a lying-down
     * zone (wider than tall) the zone's full height at the press's x, so the card clears the bar
     * above or below; along a standing one the zone's full width at the press's y.
     */
    @NonNull
    public static Box anchor(@NonNull Box zone, float x, float y) {
        if (zone.isEmpty()) return new Box(x, y, x + 1f, y + 1f);
        if (zone.width() >= zone.height()) {
            float cx = Math.max(zone.left, Math.min(zone.right - 1f, x));
            return new Box(cx, zone.top, cx + 1f, zone.bottom);
        }
        float cy = Math.max(zone.top, Math.min(zone.bottom - 1f, y));
        return new Box(zone.left, cy, zone.right, cy + 1f);
    }
}
