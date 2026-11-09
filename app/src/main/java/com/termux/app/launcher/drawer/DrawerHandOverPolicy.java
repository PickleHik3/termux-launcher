package com.termux.app.launcher.drawer;

/**
 * When an app carried in the drawer stops being the drawer's and goes to Home. A pickup always
 * starts as the drawer's own drag (folders, its drop targets); it is handed over only once the
 * finger leaves the drawer through its top edge: held above the top for {@link #HOLD_MS}, or past
 * it by more than {@link #CROSS_DP}. Coming back below the top before either restarts the hold.
 *
 * <p>Pure: the caller passes the finger, the drawer's top and the clock, on every drag move and
 * once more when a resting finger's hold runs out ({@link #msUntilHandOver}).
 */
public final class DrawerHandOverPolicy {

    /** How long a finger rests above the drawer's top before the icon goes to Home. */
    public static final long HOLD_MS = 250L;
    /** How far above the drawer's top a finger has to go to hand over at once. */
    public static final float CROSS_DP = 24f;

    public enum Decision { STAY, HAND_OVER }

    /** Uptime at which the finger last went above the top; -1 while it is below. */
    private long aboveSinceMs = -1L;

    /**
     * The finger is at {@code fingerY} on screen at {@code nowMs}. The drawer's top is at
     * {@code drawerTopY} in the same coordinates.
     */
    public Decision onMove(float fingerY, float drawerTopY, float density, long nowMs) {
        if (!(fingerY < drawerTopY)) {
            aboveSinceMs = -1L;
            return Decision.STAY;
        }
        if (aboveSinceMs < 0L) aboveSinceMs = nowMs;
        return decide(fingerY, drawerTopY, nowMs - aboveSinceMs, density);
    }

    /**
     * How long from {@code nowMs} until a finger resting where it is would be handed over, so the
     * caller can ask again then; -1 while the finger is below the top.
     */
    public long msUntilHandOver(long nowMs) {
        if (aboveSinceMs < 0L) return -1L;
        return Math.max(0L, HOLD_MS - (nowMs - aboveSinceMs));
    }

    /** Forgets the hold, for the next drag. */
    public void reset() {
        aboveSinceMs = -1L;
    }

    /**
     * The rule itself: a finger above the drawer's top hands over once it is more than
     * {@link #CROSS_DP} past it, or once it has been above it for {@link #HOLD_MS}.
     */
    public static Decision decide(float fingerY, float drawerTopY, long heldAboveMs,
                                  float density) {
        if (!(fingerY < drawerTopY)) return Decision.STAY;
        if (drawerTopY - fingerY > CROSS_DP * density) return Decision.HAND_OVER;
        return heldAboveMs >= HOLD_MS ? Decision.HAND_OVER : Decision.STAY;
    }
}
