package com.termux.app.launcher.notifications;

import androidx.annotation.NonNull;

import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter.Pull;
import com.termux.app.launcher.drawer.AppDrawerPullGeometry;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * The quick-reply swipe on a pinned app that is carrying a notification, as arithmetic.
 *
 * <p>The gesture is one sentence: <em>a swipe towards the middle of the screen, starting on an icon
 * that wears a badge, opens that app's notification card</em>. Standing the pinned apps row on
 * another edge turns it — up off a bottom bar, down off a top one, inward off a column — the same
 * way {@link AppDrawerPullGeometry} turns the drawer's own pull.
 *
 * <p>Which is exactly the problem this policy exists for. On the bottom edge the two gestures point
 * opposite ways: the drawer is pulled <em>down</em>, off the dock, and the quick reply runs up, so
 * neither can ever be mistaken for the other and the reply commits the moment the finger clears the
 * slop — the behaviour every install already has in its thumbs, and it is kept byte for byte. On the
 * other three edges both gestures run the same way, and they share the axis in time instead:
 *
 * <ul>
 *   <li>the <b>DOWN decides</b> — only a finger that lands on a badged icon arms a quick reply, and
 *       the drawer owns the toward-centre axis everywhere else exactly as it does today;
 *   <li>a <b>short flick commits</b> it, on release, once the travel passes {@link #FLICK_DP} within
 *       the platform's long-press timeout;
 *   <li>a <b>long drag hands off</b>: past {@link #HANDOFF_TRAVEL_FRACTION} of the drawer's own
 *       travel the reply gives the stream up and the drawer takes over from where the finger is, so
 *       a badged icon can never be the one place on the row the drawer will not open from.
 * </ul>
 *
 * <p>Pure: no {@code View}, no {@code Context}, no touch objects, no clock — the caller passes the
 * two deltas, the slop, the span and the elapsed time it measured.
 */
public final class NotificationSwipePolicy {

    /** Which way a quick-reply swipe travels; always towards the middle of the screen. */
    public enum Swipe { UP, DOWN, LEFT, RIGHT }

    /** Travel along the swipe, as a multiple of touch slop, before it arms. Today's number. */
    public static final float SLOP_FACTOR = 1.8f;
    /** How far the swipe must dominate the axis it is not on. Today's number. */
    public static final float DOMINANCE = 1.15f;
    /** The short flick a contested edge commits on, in dp. */
    public static final float FLICK_DP = 24f;
    /** Past this share of the drawer's travel the reply stands down and the drawer takes over. */
    public static final float HANDOFF_TRAVEL_FRACTION = 0.45f;

    private NotificationSwipePolicy() {}

    /** Which way the swipe runs from the edge the pinned apps row stands on. */
    @NonNull
    public static Swipe swipeFor(@NonNull Edge appsEdge) {
        switch (appsEdge) {
            case TOP: return Swipe.DOWN;
            case LEFT: return Swipe.RIGHT;
            case RIGHT: return Swipe.LEFT;
            case BOTTOM:
            default: return Swipe.UP;
        }
    }

    /**
     * Whether the drawer's pull on this edge runs the same way the quick reply does. It does on
     * every edge but the bottom, where the drawer is pulled off the dock rather than towards the
     * middle — which is why the bottom needs none of the sharing below.
     */
    public static boolean contested(@NonNull Edge appsEdge) {
        return sameDirection(AppDrawerPullGeometry.pullFor(appsEdge), swipeFor(appsEdge));
    }

    /**
     * Whether a quick reply on this edge commits as soon as it arms, rather than on release. It
     * does wherever nothing else wants the axis, which is the bottom dock.
     */
    public static boolean commitsOnMove(@NonNull Edge appsEdge) {
        return !contested(appsEdge);
    }

    /** Signed travel towards the middle of the screen; negative for a drag back towards the edge. */
    public static float travelPx(@NonNull Edge appsEdge, float dx, float dy) {
        switch (swipeFor(appsEdge)) {
            case UP: return -dy;
            case DOWN: return dy;
            case RIGHT: return dx;
            case LEFT:
            default: return -dx;
        }
    }

    /** How far the drag has strayed off the swipe's own axis. */
    public static float acrossPx(@NonNull Edge appsEdge, float dx, float dy) {
        Swipe swipe = swipeFor(appsEdge);
        return Math.abs(swipe == Swipe.UP || swipe == Swipe.DOWN ? dx : dy);
    }

    /**
     * Whether a drag from a badged icon has become a quick-reply swipe: far enough along the axis
     * that points at the middle of the screen, and clearly dominated by it. A drag back towards the
     * bar's own edge can never arm, which is what leaves the page swipe and the drawer alone.
     */
    public static boolean armed(@NonNull Edge appsEdge, float dx, float dy, float slopPx) {
        float along = travelPx(appsEdge, dx, dy);
        return along >= Math.max(0f, slopPx) * SLOP_FACTOR
            && along > acrossPx(appsEdge, dx, dy) * DOMINANCE;
    }

    /**
     * Whether a badged icon still owns the toward-centre axis for this drag. Deliberately weaker
     * than {@link #armed}: the drawer's own claim comes at a shorter travel than the reply's, so the
     * reply has to hold the axis from the first move that leans towards the middle or it would
     * never get the chance to arm at all. Anything leaning along the bar falls straight through to
     * the page swipe.
     */
    public static boolean holdsAxis(@NonNull Edge appsEdge, float dx, float dy) {
        float along = travelPx(appsEdge, dx, dy);
        return along > 0f && along >= acrossPx(appsEdge, dx, dy);
    }

    /**
     * Whether the finger has travelled past the reply's window and the drawer should take the rest
     * of the stream. {@code travelSpanPx} is the span the drawer's open travel is a fraction of —
     * {@link AppDrawerPullGeometry#travelSpanPx}.
     */
    public static boolean handsOff(@NonNull Edge appsEdge, float dx, float dy, float travelSpanPx) {
        if (!contested(appsEdge)) return false;
        return travelPx(appsEdge, dx, dy) > Math.max(0f, travelSpanPx) * HANDOFF_TRAVEL_FRACTION;
    }

    /** The same question asked by a host that knows only the pull it was given, not the edge. */
    public static boolean handsOffAlongPull(@NonNull Pull pull, float dx, float dy,
                                            float travelSpanPx) {
        return AppDrawerGestureArbiter.travelAlongPull(pull, dx, dy)
            > Math.max(0f, travelSpanPx) * HANDOFF_TRAVEL_FRACTION;
    }

    /**
     * Whether a release ends a quick reply that was armed but never committed: a short flick's
     * worth of travel towards the middle, finished inside the long-press timeout. A finger that
     * lingers past the timeout has stopped flicking and is left alone.
     */
    public static boolean commitsOnRelease(@NonNull Edge appsEdge, float dx, float dy,
                                           float flickPx, long elapsedMs, long longPressTimeoutMs) {
        if (elapsedMs > longPressTimeoutMs) return false;
        return travelPx(appsEdge, dx, dy) >= Math.max(0f, flickPx);
    }

    /** {@link #FLICK_DP} in this density's pixels. */
    public static float flickPx(float density) {
        return FLICK_DP * Math.max(0f, density);
    }

    private static boolean sameDirection(@NonNull Pull pull, @NonNull Swipe swipe) {
        switch (pull) {
            case DOWN: return swipe == Swipe.DOWN;
            case LEFT: return swipe == Swipe.LEFT;
            case RIGHT: return swipe == Swipe.RIGHT;
            case NONE:
            default: return false;
        }
    }
}
