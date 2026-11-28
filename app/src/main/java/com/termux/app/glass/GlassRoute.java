package com.termux.app.glass;

/**
 * How a Live surface draws its picture of what is beneath (live window residency, 2026-11-28,
 * section 4.3). The route is residency inside the Live kind, not a kind: both show what lies
 * beneath. It follows the arrangement, decided at rest, never motion.
 */
public enum GlassRoute {
    /**
     * Only the ground is beneath, and the ground is the stored frame: the surface draws that frame
     * at the same strength, aimed where it stands. No recorder, no layer of its own, no Live slot.
     */
    PARITY,
    /**
     * Something other than a still ground is beneath (tiles under a Float, a live scene): the
     * recorder's layers and the full-size effect layer, one Live slot and its area.
     */
    EFFECT
}
