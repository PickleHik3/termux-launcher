package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.GlassRefraction;

/**
 * The dock as one surface (ADR 0018 §1, D3, D8, D11): where its glass stands, what shape it has,
 * which of its edges are seams, what glass it wears and which design it draws, answered once from
 * an {@link Input} the host builds. Pure: no view, no preference, no Android.
 *
 * <p>The surface runs from the top of the pinned-apps row (or the Peek card's top, through the
 * icons' centres) down to the screen's bottom edge whenever the dock stands on that edge with the
 * in-app keyboard down and the system keyboard away: the band under the gesture pill is the
 * dock's <b>foot</b>, the same blur, tint and program as the card, and the card's bottom edge is
 * then a seam, never a rim. Whoever stands on the screen's bottom edge owns the foot (D8): with
 * the keyboard up the keyboard does, and with the system keyboard visible nobody does.
 *
 * <p>Docked and Floating keep their own placement ({@link DockPlacement#floats}); they arrive as
 * inputs and leave as one answer. The design is the page kind's own whatever the keyboard does
 * (D11): a Home page's Peek dock is Peek over the keyboard too, its card then standing on the
 * stack under it instead of the screen's edge.
 */
public final class DockSpec {

    /** The kind of page in front (ADR 0018 §3). */
    public enum PageKind { HOME, TERMINAL, DISPLAY }

    /** What the card's bottom edge meets. */
    public enum Base {
        /** The screen's bottom edge, through the foot under the gesture pill. */
        SCREEN_EDGE,
        /** The keyboard (or whatever stands under the dock in the stack): the dock ends there. */
        STACK,
        /** Nothing: a Floating card with air under it. */
        AIR
    }

    /** The Dock element's look cells at the page in front, as {@code ElementGlass} resolves them. */
    public static final class Look {
        public final int blurDp;
        public final int opacityPercent;
        public final int tintPercent;
        public final int grainPercent;
        /** The element turned its rim off in a cell of its own. */
        public final boolean rimOff;
        /** Corner radius the style resolves to (the {@code -1} sentinel already resolved), dp. */
        public final float cornerRadiusDp;
        /** Refraction bend and dispersion of the element's own, or null while it follows Base. */
        @Nullable public final Integer depthDp;
        @Nullable public final Integer edgePercent;

        public Look(int blurDp, int opacityPercent, int tintPercent, int grainPercent,
                    boolean rimOff, float cornerRadiusDp, @Nullable Integer depthDp,
                    @Nullable Integer edgePercent) {
            this.blurDp = Math.max(0, blurDp);
            this.opacityPercent = clamp(opacityPercent, 0, 100);
            this.tintPercent = clamp(tintPercent, 0, 100);
            this.grainPercent = clamp(grainPercent, 0, 100);
            this.rimOff = rimOff;
            this.cornerRadiusDp = Math.max(0f, cornerRadiusDp);
            this.depthDp = depthDp;
            this.edgePercent = edgePercent;
        }

        /** The tint's alpha, 0..1. */
        public float tintAlpha() {
            return opacityPercent / 100f;
        }
    }

    /** Everything the dock's surface is decided from. Coordinates are the screen's, px. */
    public static final class Input {
        @NonNull public PageKind kind = PageKind.TERMINAL;
        /** The page in front's Dock design, as its layout holds it. */
        @Nullable public DockStyle design;
        public boolean floatingStyle;
        public boolean dockShown = true;
        /** The in-app keyboard is up under the dock. */
        public boolean keyboardShown;
        /** The system keyboard is visible. */
        public boolean imeVisible;
        /**
         * The row stands alone on its glass along the bottom, with no Search field under it and
         * not lifted over the system keyboard: what the Peek card needs besides its design
         * ({@link DockPlacement#peekCardStands} less the keyboard, which D11 no longer lets
         * refuse it).
         */
        public boolean peekRowAlone = true;
        @NonNull public Look look = new Look(0, 0, 0, 0, false, 0f, null, null);
        public int screenWidthPx;
        public int screenHeightPx;
        public float density = 1f;
        /** The navigation (gesture pill) inset at the screen's bottom. */
        public int navInsetPx;
        /** The air Floating leaves under its card, above the navigation inset. */
        public int capsuleGapPx;
        /** The dock's left and right edges as placed (a Floating card's margins included). */
        public int leftPx;
        public int rightPx;
        /** The top of the dock's topmost row and the bottom of its content as laid out. */
        public int rowTopPx;
        public int rowBottomPx;
        /** The pinned-apps row's drawn icon boxes (left, top, right, bottom each), screen px. */
        @Nullable public float[] iconBoxes;
        public int iconCount;
        /** {@code dock_peek_reflection}, 0..100. */
        public int peekReflection = PeekGlassGeometry.REFLECTION_DEFAULT;
    }

    /** The design this dock draws (D11: the page kind's own, whatever the keyboard). */
    @NonNull public final DockStyle design;
    /** The slab spec of that design, the tray's for Peek while no card can be cut. */
    @NonNull public final DockSurfaceSpec surface;
    /** Placed as the Floating card (Floating, unless the Peek card stands). */
    public final boolean floats;
    /** The foot under the gesture pill exists: the dock owns the band (D8). */
    public final boolean foot;
    /** Height of the foot below the dock's content: the navigation inset (and any gap), px. */
    public final int footHeightPx;
    /** What the card's bottom edge meets. */
    @NonNull public final Base base;
    /** The one surface, screen px: from the row's (or card's) top to the screen's bottom edge. */
    public final int left, top, right, bottom;
    /** Top corners, px; the bottom corners are 0 whenever the bottom edge is a seam. */
    public final float topCornerPx;
    public final float bottomCornerPx;
    /** {@link GlassRefraction#SEAM_BOTTOM} and friends: the edges the surface continues past. */
    public final int seamFlags;
    /** The Peek card, in screen px, or null. */
    @Nullable public final PeekGlassGeometry.Card card;
    /** The reflection's strength at its top edge, 0 when none is drawn. */
    public final float reflectionAlpha;
    /** One glass for the whole surface, the foot included. */
    public final int blurDp;
    public final float tintAlpha;
    public final int tintPercent;
    public final int grainPercent;
    /** The rim is drawn on the edges that are not seams. */
    public final boolean rim;

    private DockSpec(@NonNull DockStyle design, @NonNull DockSurfaceSpec surface, boolean floats,
                     boolean foot, int footHeightPx, @NonNull Base base, int left, int top,
                     int right, int bottom, float topCornerPx, float bottomCornerPx,
                     int seamFlags, @Nullable PeekGlassGeometry.Card card, float reflectionAlpha,
                     @NonNull Look look) {
        this.design = design;
        this.surface = surface;
        this.floats = floats;
        this.foot = foot;
        this.footHeightPx = footHeightPx;
        this.base = base;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.topCornerPx = topCornerPx;
        this.bottomCornerPx = bottomCornerPx;
        this.seamFlags = seamFlags;
        this.card = card;
        this.reflectionAlpha = reflectionAlpha;
        this.blurDp = look.blurDp;
        this.tintAlpha = look.tintAlpha();
        this.tintPercent = look.tintPercent;
        this.grainPercent = look.grainPercent;
        this.rim = !look.rimOff;
    }

    /**
     * Whether the dock's own surface reaches under the navigation inset (D8): the dock stands on
     * the screen's bottom edge, the in-app keyboard is down, the system keyboard is away, and
     * there is an inset to reach under.
     */
    public static boolean footExists(boolean dockShown, boolean floats, boolean keyboardShown,
                                     boolean imeVisible, int navInsetPx) {
        return dockShown && !floats && !keyboardShown && !imeVisible && navInsetPx > 0;
    }

    /**
     * Whether the Peek card stands: the design is Peek, the dock is shown and its row stands alone.
     * The keyboard no longer refuses it (D11); with it up the card stands on the stack.
     */
    public static boolean peekCardStands(@Nullable DockStyle design, boolean dockShown,
                                         boolean peekRowAlone, boolean imeVisible) {
        return DockStyle.effectiveStyle(design) == DockStyle.PEEK_GLASS && dockShown
            && peekRowAlone && !imeVisible;
    }

    /** The dock's surface for {@code in}. */
    @NonNull
    public static DockSpec of(@NonNull Input in) {
        DockStyle design = DockStyle.effectiveStyle(in.design);
        boolean peekStands = peekCardStands(design, in.dockShown, in.peekRowAlone, in.imeVisible);
        boolean floats = DockPlacement.floats(in.floatingStyle, peekStands);
        boolean foot = footExists(in.dockShown, floats, in.keyboardShown, in.imeVisible,
            in.navInsetPx);
        Base base = foot ? Base.SCREEN_EDGE : floats ? Base.AIR : Base.STACK;
        // A flush dock's content ends on the inset; the foot runs from there to the screen edge.
        int footHeight = foot ? Math.max(0, in.screenHeightPx - in.rowBottomPx) : 0;
        int bottom = foot ? in.screenHeightPx : in.rowBottomPx;

        PeekGlassGeometry.Card card = null;
        if (peekStands && in.iconBoxes != null && in.iconCount > 0) {
            int hostWidth = Math.max(0, in.rightPx - in.leftPx);
            int hostHeight = Math.max(0, in.rowBottomPx - in.rowTopPx);
            float[] local = new float[in.iconCount * 4];
            for (int i = 0; i < in.iconCount && i * 4 + 3 < in.iconBoxes.length; i++) {
                local[i * 4] = in.iconBoxes[i * 4] - in.leftPx;
                local[i * 4 + 1] = in.iconBoxes[i * 4 + 1] - in.rowTopPx;
                local[i * 4 + 2] = in.iconBoxes[i * 4 + 2] - in.leftPx;
                local[i * 4 + 3] = in.iconBoxes[i * 4 + 3] - in.rowTopPx;
            }
            PeekGlassGeometry.Card hostCard = PeekGlassGeometry.compute(local, in.iconCount,
                in.leftPx, hostWidth, hostHeight, in.screenWidthPx, bottom - in.rowTopPx,
                in.density);
            if (hostCard != null) card = hostCard.offset(in.leftPx, in.rowTopPx);
        }

        DockSurfaceSpec surface = DockSurfaceSpec.forStyle(design).forCard(card);
        int left, top, right;
        float topCorner;
        if (card != null) {
            left = Math.round(card.left);
            right = Math.round(card.right);
            top = Math.round(card.top);
            topCorner = card.radiusPx;
        } else {
            left = in.leftPx;
            right = in.rightPx;
            top = in.rowTopPx;
            topCorner = in.look.cornerRadiusDp * Math.max(0f, in.density);
        }
        // The bottom edge is a seam whenever something continues it: the foot, or the stack.
        int seams = base == Base.AIR ? 0 : GlassRefraction.SEAM_BOTTOM;
        float bottomCorner = base == Base.AIR ? topCorner : 0f;
        float reflection = card != null ? PeekGlassGeometry.reflectionAlpha(in.peekReflection) : 0f;
        return new DockSpec(design, surface, floats, foot, footHeight, base, left, top, right,
            bottom, topCorner, bottomCorner, seams, card, reflection, in.look);
    }

    /** Whether the bottom edge carries {@link GlassRefraction#SEAM_BOTTOM}. */
    public boolean seamsBottom() {
        return (seamFlags & GlassRefraction.SEAM_BOTTOM) != 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @NonNull
    @Override
    public String toString() {
        return "DockSpec{" + design + (floats ? " floats" : "") + (foot ? " foot=" + footHeightPx : "")
            + " base=" + base + " [" + left + "," + top + "," + right + "," + bottom + "]"
            + " seams=" + seamFlags + " blur=" + blurDp + " tint=" + tintAlpha + "}";
    }
}
