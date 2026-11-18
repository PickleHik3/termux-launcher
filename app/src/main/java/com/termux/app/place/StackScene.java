package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.StackMaterial;
import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.page.Side;

/**
 * Everything the bottom stack shows on one frame, as one pure function of the crossing's two
 * ends, its progress and its travel ({@link #at}): the transforms (the container's translation
 * and clip, the rows' and the glass host's sink and cut, the keyboard's clip and the bands
 * under it held up), each region's rect and look, the strip under the pill, the inks and the
 * topology. {@code StackRenderer} is the only code that writes it to the views.
 *
 * <p>The transform maths is today's, moved here: the travel from {@code Crossing#stack}, a
 * each layer's sink and cut from
 * {@code CrossingViews#applyLayerSink}, the container's translation and clip from
 * {@code TermuxActivity#applyAccessoryStackTranslation} (with the system IME's lift), and the
 * keyboard's clip and the bands' hold from {@code TermuxActivity#applyUnderKeyboardTravel}.
 *
 * <p>The rules (design 5.1): a region one end has wears that end's look at every p, a region both
 * have blends with p; the foot always exists and its owner blends by presence; the topology
 * changes only where none of the stack shows, else at landing; even-out is the blend of the two
 * ends' matrices; {@code at(0) == rest(from)} and {@code at(1) == rest(to)}.
 */
public final class StackScene {

    private StackScene() {}

    /** The views a frame transforms ({@link Frame#ty}, {@link Frame#clipped}). */
    public static final int V_CONTAINER = 0;
    /** The sinking layers, {@link StackGeometry#HOST} .. {@link StackGeometry#AZ_LABEL}, from here. */
    public static final int V_LAYER0 = 1;
    public static final int V_HOST = V_LAYER0 + StackGeometry.HOST;
    public static final int V_ROWS = V_LAYER0 + StackGeometry.ROWS;
    public static final int V_KEYBOARD = V_LAYER0 + StackGeometry.LAYER_COUNT;
    public static final int V_UNDER = V_KEYBOARD + 1;
    /** The flat body surface drawn on every page: its clip alone ({@link #bodyCutPx}). */
    public static final int V_BODY = V_UNDER + 1;
    public static final int V_COUNT = V_BODY + 1;

    /**
     * What the crossing moves the stack by on this frame: its travel, the keyboard's share of it,
     * the system IME's lift, and the layers' sink and cut. Mutable; {@link #rest} puts it back.
     */
    public static final class Motion {
        /** The stack drawn this much lower; of which this much is the keyboard's. */
        public float dockTravelPx;
        public float keyboardSharePx;
        /** The system IME's lift, subtracted from the travel. */
        public float imeLiftPx;
        /** The rows' layers' sink and the part of it cut; the sheet's (the glass host's). */
        public float rowsSinkPx;
        public float rowsCutPx;
        public float sheetSinkPx;
        public float sheetCutPx;

        /** Nothing moved; the IME's lift is kept, a rest's own. */
        public void rest() {
            dockTravelPx = 0f;
            keyboardSharePx = 0f;
            sink(0f);
        }

        /** Every layer sunk {@code px}, all of it cut: a sink into the keyboard's top edge. */
        public void sink(float px) {
            layerSink(px, px, px, px);
        }

        public void layerSink(float rowsPx, float rowsCutPx, float sheetPx, float sheetCutPx) {
            rowsSinkPx = rowsPx;
            this.rowsCutPx = rowsCutPx;
            sheetSinkPx = sheetPx;
            this.sheetCutPx = sheetCutPx;
        }

        public void set(@NonNull Motion o) {
            dockTravelPx = o.dockTravelPx;
            keyboardSharePx = o.keyboardSharePx;
            imeLiftPx = o.imeLiftPx;
            layerSink(o.rowsSinkPx, o.rowsCutPx, o.sheetSinkPx, o.sheetCutPx);
        }
    }

    /**
     * The travel of one crossing frame ({@code Crossing#stack}): the keyboard's and the dock's
     * hidden parts ({@link PlaceChromeTravel#stackTranslationPx}), the dock counted as shown
     * while the rows sink into the keyboard's top edge instead. Writes the travel, the keyboard's
     * share and the rows' sink into {@code out}.
     *
     * @param rowsSink whether the rows sink (the seam's, for this keyboard's laid-out height)
     */
    public static void travel(boolean rowsSink, float keyboardReveal,
                              float dockReveal, int keyboardPx, int dockPx, int rowsPx,
                              @NonNull Motion out) {
        out.sink(rowsSink ? rowsPx * (1f - clamp01(dockReveal)) : 0f);
        out.dockTravelPx = PlaceChromeTravel.stackTranslationPx(keyboardReveal,
            rowsSink ? 1f : dockReveal, keyboardPx, dockPx);
        out.keyboardSharePx = keyboardTranslationPx(keyboardReveal, keyboardPx);
    }

    /**
     * How far down the keyboard is drawn from where it is laid out, the one number the rest and
     * the crossing both read (D1-A): {@code (1 - reveal) * laidOutPx}. The in-app keyboard stays
     * laid out while it is down, so a down keyboard (reveal 0) is this translation at rest, a
     * crossing's frame is it at the seam's reveal, and the crossing's end equals the rest it lands
     * on. The stack's translation carries the dock with it, which is why the bands under the
     * keyboard are held up by the same share ({@link #transforms}).
     */
    public static float keyboardTranslationPx(float keyboardReveal, int laidOutPx) {
        return Math.max(0, laidOutPx) * (1f - clamp01(keyboardReveal));
    }

    /**
     * Adds the resting keyboard's share ({@link #keyboardTranslationPx}) to a crossing's motion
     * into {@code out}: a keyboard that is down never takes part in a crossing's own travel (the
     * crossing brings it up first), so the two never count the same pixels twice.
     */
    public static void withKeyboardRest(@NonNull Motion in, float restSharePx, @NonNull Motion out) {
        out.set(in);
        if (restSharePx <= 0f) return;
        out.dockTravelPx += restSharePx;
        out.keyboardSharePx += restSharePx;
    }

    /**
     * The container's height while the keyboard is laid out but down: the stack's and the
     * keyboard's. No page's room reads it; each page's room is its own rest's
     * ({@link PageRoom}).
     */
    public static int containerHeightPx(int stackHeightPx, int keyboardDownLaidOutPx) {
        return containerHeightPx(stackHeightPx, keyboardDownLaidOutPx, 0);
    }

    /**
     * {@link #containerHeightPx(int, int)} with the band laid out at the union of both sides'
     * rests: {@code slackPx} taller than the front side's ({@link StackLanes#slackPx}). The slab
     * starts that far below the container's top, so at rest the stack shows the front rest.
     */
    public static int containerHeightPx(int stackHeightPx, int keyboardDownLaidOutPx,
                                        int slackPx) {
        return Math.max(0, stackHeightPx) + Math.max(0, keyboardDownLaidOutPx)
            + Math.max(0, slackPx);
    }

    /** One frame of the stack. Mutable and reused; {@link #sameAs} compares two. */
    public static final class Frame {
        /** Each view's translationY. */
        public final float[] ty = new float[V_COUNT];
        /** Whether each view is clipped, and the clip in its own pixels. */
        public final boolean[] clipped = new boolean[V_COUNT];
        public final int[] clipLeft = new int[V_COUNT];
        public final int[] clipTop = new int[V_COUNT];
        public final int[] clipRight = new int[V_COUNT];
        public final int[] clipBottom = new int[V_COUNT];

        /** Each region ({@link StackEnd#ROWS} ..): whether an end has it, and its rect drawn. */
        public final boolean[] present = new boolean[StackEnd.REGION_COUNT];
        public final int[] regionTop = new int[StackEnd.REGION_COUNT];
        public final int[] regionBottom = new int[StackEnd.REGION_COUNT];
        public final RegionLook[] look = new RegionLook[StackEnd.REGION_COUNT];
        /**
         * How much of each region the arriving end's layer draws ({@link #regionShare}): 0 where
         * only the end left has the region, 1 where only the arriving end has it, the blend's
         * {@code w(p)} where both have it.
         */
        public final float[] share = new float[StackEnd.REGION_COUNT];
        /** Each region's even-out matrix, the identity where neither end evens it out. */
        public final float[][] evenOut = new float[StackEnd.REGION_COUNT][20];

        /** The decor strip under the pill: its alpha (visible above 0) and the foot's look. */
        public float stripAlpha;
        public int inkArgb;
        public int topology;

        /**
         * What the slab and the keyboard show on this frame ({@link #shape}): the slab's height,
         * corner radius and inset, and the keyboard's shown height, each from the leaving rest
         * to the arriving one on the material curve. All 0 for a frame without ends.
         */
        public float slabHeightPx;
        public float slabCornerPx;
        public float slabInsetPx;
        public float keyboardShownPx;

        /**
         * Each side's rows ({@link Side}, by ordinal): drawn this far across from where they are
         * laid out, riding with their side ({@link #sideSlide}), and this far down, so they stand
         * on the keyboard's top as it is drawn. Across is 0 for a frame without ends.
         */
        public final float[] sideTx = new float[Side.COUNT];
        public final float[] sideTy = new float[Side.COUNT];

        public Frame() {
            for (int i = 0; i < StackEnd.REGION_COUNT; i++) look[i] = new RegionLook();
        }

        public boolean stripVisible() {
            return stripAlpha > 0f;
        }

        /** Whether {@code region} shows any of its rect. */
        public boolean visible(int region) {
            return present[region] && regionBottom[region] > regionTop[region];
        }

        /** The Peek card's foot, the foot region's rect while the stack is Floating. */
        public int peekFootTop() {
            return topology == StackEnd.TOPOLOGY_FLOATING ? regionTop[StackEnd.FOOT] : 0;
        }

        public int peekFootBottom() {
            return topology == StackEnd.TOPOLOGY_FLOATING ? regionBottom[StackEnd.FOOT] : 0;
        }

        /** Whether {@code o} transforms every view alike. */
        public boolean sameTransforms(@NonNull Frame o) {
            for (int v = 0; v < V_COUNT; v++) {
                if (ty[v] != o.ty[v] || clipped[v] != o.clipped[v]) return false;
                if (clipped[v] && (clipLeft[v] != o.clipLeft[v] || clipTop[v] != o.clipTop[v]
                    || clipRight[v] != o.clipRight[v] || clipBottom[v] != o.clipBottom[v]))
                    return false;
            }
            return true;
        }

        /** Whether {@code o} draws the same: transforms, what shows of each region, strip, inks. */
        public boolean sameAs(@NonNull Frame o) {
            if (!sameTransforms(o) || topology != o.topology || inkArgb != o.inkArgb
                || stripAlpha != o.stripAlpha || slabHeightPx != o.slabHeightPx
                || slabCornerPx != o.slabCornerPx || slabInsetPx != o.slabInsetPx
                || keyboardShownPx != o.keyboardShownPx) return false;
            for (int s = 0; s < Side.COUNT; s++) {
                if (sideTx[s] != o.sideTx[s] || sideTy[s] != o.sideTy[s]) return false;
            }
            for (int r = 0; r < StackEnd.REGION_COUNT; r++) {
                boolean shows = visible(r);
                if (shows != o.visible(r)) return false;
                if (!shows) continue;
                if (regionTop[r] != o.regionTop[r] || regionBottom[r] != o.regionBottom[r]
                    || !look[r].sameAs(o.look[r])) return false;
                if (share[r] != o.share[r] && !look[r].sameAs(o.look[r])) return false;
                for (int i = 0; i < 20; i++) if (evenOut[r][i] != o.evenOut[r][i]) return false;
            }
            return true;
        }
    }

    /** {@code end} at rest: nothing travels, the IME's lift as given. */
    public static void rest(@NonNull StackEnd end, float imeLiftPx, @NonNull Motion scratch,
                            @NonNull Frame out) {
        scratch.rest();
        scratch.imeLiftPx = imeLiftPx;
        at(end, end, 0f, scratch, end.rest, out);
    }

    /**
     * The stack at {@code p} from {@code from} to {@code to}, moved by {@code motion}, as laid out
     * now ({@code live}). Ends may be null for a frame of transforms alone (no looks).
     */
    public static void at(@Nullable StackEnd from, @Nullable StackEnd to, float p,
                          @NonNull Motion motion, @NonNull StackGeometry live,
                          @NonNull Frame out) {
        transforms(motion, live, out);
        body(from, to, p, live, out);
        regions(from, to, materialShare(p), live, out);
        sides(from, to, p, live, out);
        shape(from, to, materialShare(p), out);
    }

    /**
     * The slab's and the keyboard's shown numbers at blend {@code w} ({@link #materialShare}) from
     * {@code from}'s rest to {@code to}'s, into {@code out}: exactly the leaving rest's at 0 and
     * the arriving rest's at 1, a straight blend between, so a structure apart (Corners, Margin,
     * the dock's or the keyboard's size) moves with the wall and the landing finds it drawn.
     * Drawn, never laid out: the numbers are cuts, outlines and insets of the one layout.
     */
    public static void shape(@Nullable StackEnd from, @Nullable StackEnd to, float w,
                             @NonNull Frame out) {
        if (from == null || to == null) {
            out.slabHeightPx = out.slabCornerPx = out.slabInsetPx = out.keyboardShownPx = 0f;
            return;
        }
        out.slabHeightPx = blend(from.slabHeightPx(), to.slabHeightPx(), w);
        out.slabCornerPx = blend(from.cornerPx, to.cornerPx, w);
        out.slabInsetPx = blend(from.insetPx, to.insetPx, w);
        out.keyboardShownPx = blend(from.keyboardShownPx(), to.keyboardShownPx(), w);
    }

    private static float blend(float a, float b, float w) {
        return w <= 0f ? a : w >= 1f ? b : a + (b - a) * w;
    }

    /**
     * How far Home's side stands off the screen at progress {@code p} from {@code from}'s side to
     * {@code to}'s, in walls: 0 while Home is in front, 1 once it has slid away to the left of the
     * seam. Within one side it never moves.
     */
    public static float homeHidden(@NonNull Side from, @NonNull Side to, float p) {
        if (from == to) return from == Side.HOME ? 0f : 1f;
        float q = clamp01(p);
        return from == Side.HOME ? q : 1f - q;
    }

    /**
     * {@code side}'s rows drawn this far across at progress {@code p} on a wall {@code widthPx}
     * wide: Home's to the left of the seam, the Workspace's to its right, one wall apart.
     */
    public static float sideSlide(@NonNull Side side, @NonNull Side from, @NonNull Side to,
                                  float p, int widthPx) {
        float hidden = homeHidden(from, to, p);
        return side == Side.HOME ? -hidden * widthPx : (1f - hidden) * widthPx;
    }

    /**
     * Each side's rows (ADR 0030 section 4): across with their side, and down to the keyboard's
     * top as the frame draws it, so no row hangs over a sinking keyboard. Rows carry no glass;
     * the slab under them is the shared stack's. Home's lane and the Workspace's slide with their
     * pages ({@link #sideSlide}), so the front side's lane stands at 0 and the other's a wall's
     * width off its page's side, and the frame at progress 0 and 1 is each end's rest.
     * {@code StackRenderer} writes {@code sideTx} to the lanes' rows; {@code sideTy} is not
     * written yet.
     */
    private static void sides(@Nullable StackEnd from, @Nullable StackEnd to, float p,
                              @NonNull StackGeometry g, @NonNull Frame out) {
        int rowsBottom = g.layerTopPx[StackGeometry.ROWS] + g.layerHeightPx[StackGeometry.ROWS];
        boolean onKeyboard = g.keyboardPresent && g.keyboardInColumn
            && g.layerPresent[StackGeometry.ROWS];
        float ty = onKeyboard ? out.regionTop[StackEnd.KEYBOARD] - rowsBottom : 0f;
        for (int s = 0; s < Side.COUNT; s++) {
            out.sideTy[s] = ty;
            out.sideTx[s] = 0f;
        }
        if (from == null || to == null) return;
        Side a = Side.of(from.page.isHome());
        Side b = Side.of(to.page.isHome());
        int width = Math.max(0, g.containerWidthPx);
        for (Side side : SIDES) out.sideTx[side.ordinal()] = sideSlide(side, a, b, p, width);
    }

    private static final Side[] SIDES = Side.values();

    /**
     * How far a region both ends have has blended toward the arriving end at progress {@code p}
     * ({@link SeamFade#share}): 0 up to its start, 1 from its end, a smoothstep between, read back
     * down the same curve when the drag reverses. Exactly 0 at p=0 and 1 at p=1.
     */
    public static float materialShare(float p) {
        return SeamFade.share(clamp01(p));
    }

    /**
     * How much of a region the arriving end's layer draws: a region one end has wears that end's
     * look at every p (0 for the end left, 1 for the arriving end), a region both have blends by
     * {@code w}; none, 0.
     */
    public static float regionShare(boolean fromHas, boolean toHas, float w) {
        if (fromHas && toHas) return w;
        return toHas ? 1f : 0f;
    }

    // ---- Transforms ------------------------------------------------------------------------

    private static void transforms(@NonNull Motion m, @NonNull StackGeometry g, @NonNull Frame out) {
        // The container: the travel less the IME's lift, cut where it slides past its bottom.
        out.ty[V_CONTAINER] = m.dockTravelPx - m.imeLiftPx;
        int travel = Math.round(Math.max(0f, m.dockTravelPx));
        if (travel <= 0) {
            out.clipped[V_CONTAINER] = false;
        } else {
            bottomCut(out, V_CONTAINER, Math.max(1, g.containerWidthPx), g.containerHeightPx,
                g.displayHeightPx, travel);
        }
        // The sinking layers, each cut at its own laid-out bottom less its hidden part.
        for (int i = 0; i < StackGeometry.LAYER_COUNT; i++) {
            int v = V_LAYER0 + i;
            boolean sheet = i == StackGeometry.HOST;
            out.ty[v] = sheet ? m.sheetSinkPx : m.rowsSinkPx;
            int hidden = Math.round(sheet ? m.sheetCutPx : m.rowsCutPx);
            if (hidden <= 0) {
                out.clipped[v] = false;
            } else {
                bottomCut(out, v, Math.max(1, g.layerWidthPx[i]), g.layerHeightPx[i],
                    g.displayHeightPx, hidden);
            }
        }
        // The bands under the keyboard held back up by its share; the keyboard cut where it
        // slides past its column's bottom or the stack's.
        boolean inColumn = g.keyboardPresent && g.keyboardInColumn;
        boolean holds = g.underShown && inColumn;
        float share = holds ? Math.max(0f, m.keyboardSharePx) : 0f;
        out.ty[V_UNDER] = -share;
        out.clipped[V_UNDER] = false;
        out.ty[V_KEYBOARD] = 0f;
        // A keyboard that wears its foot is cut with its own share, which no band holds (none
        // stands under it): the foot goes under the pill with the keys.
        int hidden = inColumn ? BottomInsets.keyboardTravelHiddenPx(m.dockTravelPx,
                g.keyboardFootPx > 0 ? m.keyboardSharePx : share, g.belowKeyboardInColumnPx,
                g.stackPaddingBottomPx, g.keyboardFootPx,
                BottomInsets.keyboardKeysPx(g.keyboardHeightPx, g.keyboardFootPx))
            : Math.round(share);
        if (hidden <= 0) {
            out.clipped[V_KEYBOARD] = false;
        } else {
            bottomCut(out, V_KEYBOARD, Math.max(g.keyboardWidthPx, g.displayWidthPx),
                g.keyboardHeightPx, g.displayHeightPx, hidden);
        }
    }

    /**
     * A clip generous above and to the sides (glow and ripple layers draw outside their view on
     * purpose) that cuts only the bottom, {@code hidden} up from {@code height}.
     */
    private static void bottomCut(@NonNull Frame out, int v, int width, int height,
                                  int displayHeightPx, int hidden) {
        int h = Math.max(0, height);
        out.clipped[v] = true;
        out.clipLeft[v] = -width;
        out.clipTop[v] = -Math.max(h, displayHeightPx);
        out.clipRight[v] = 2 * width;
        out.clipBottom[v] = Math.max(0, h - hidden);
    }

    // ---- The body surface ------------------------------------------------------------------

    /**
     * How far the body surface is cut up from the bottom it is laid out at, at progress {@code p}
     * from a page whose room is {@code fromRoomPx} to one whose room is {@code toRoomPx}:
     * the blended room less {@code laidOutRoomPx}, the smallest room any page has, so the surface
     * is laid out once and every rest's own bottom is a cut. Exactly the end's room less the
     * layout's at p=0 and p=1.
     */
    public static int bodyCutPx(int fromRoomPx, int toRoomPx, float p, int laidOutRoomPx) {
        float room = fromRoomPx + (toRoomPx - fromRoomPx) * clamp01(p);
        return Math.max(0, Math.round(room) - Math.max(0, laidOutRoomPx));
    }

    private static void body(@Nullable StackEnd from, @Nullable StackEnd to, float p,
                             @NonNull StackGeometry g, @NonNull Frame out) {
        out.ty[V_BODY] = 0f;
        out.clipped[V_BODY] = false;
        if (from == null || to == null || g.bodyHeightPx <= 0) return;
        int cut = bodyCutPx(from.roomPx, to.roomPx, p, g.bodyLaidOutRoomPx);
        if (cut <= 0) return;
        bottomCut(out, V_BODY, Math.max(1, g.bodyWidthPx), g.bodyHeightPx, g.displayHeightPx, cut);
    }

    // ---- Regions ---------------------------------------------------------------------------

    private static void regions(@Nullable StackEnd from, @Nullable StackEnd to, float w,
                                @NonNull StackGeometry g, @NonNull Frame out) {
        // The container's own cut, in its pixels: nothing below it shows.
        int floor = out.clipped[V_CONTAINER] ? out.clipBottom[V_CONTAINER] : Integer.MAX_VALUE;
        // The rows region is the rows' cut rect.
        int rowsV = V_ROWS;
        int rowsTop = g.layerTopPx[StackGeometry.ROWS] + Math.round(out.ty[rowsV]);
        int rowsH = g.layerHeightPx[StackGeometry.ROWS];
        int rowsShown = out.clipped[rowsV] ? Math.min(rowsH, out.clipBottom[rowsV]) : rowsH;
        rect(out, StackEnd.ROWS, g.layerPresent[StackGeometry.ROWS], rowsTop, rowsTop + rowsShown,
            floor);
        // The keyboard as its clip leaves it, while it stands in the stack's column.
        int kbH = g.keyboardHeightPx;
        int kbShown = out.clipped[V_KEYBOARD] ? Math.min(kbH, out.clipBottom[V_KEYBOARD]) : kbH;
        rect(out, StackEnd.KEYBOARD, g.keyboardPresent && g.keyboardInColumn, g.keyboardTopPx,
            g.keyboardTopPx + kbShown, floor);
        int underTy = Math.round(out.ty[V_UNDER]);
        rect(out, StackEnd.UNDER, g.underShown, g.underTopPx + underTy, g.underBottomPx + underTy,
            floor);
        rect(out, StackEnd.FOOT, true, g.footTopPx, g.footBottomPx, floor);

        if (from == null || to == null) {
            for (int r = 0; r < StackEnd.REGION_COUNT; r++) {
                out.present[r] = false;
                out.share[r] = 0f;
                StackMaterial.clear(out.look[r]);
                identity(out.evenOut[r]);
            }
            out.stripAlpha = 0f;
            out.inkArgb = 0;
            out.topology = StackEnd.TOPOLOGY_DOCKED;
            return;
        }
        for (int r = 0; r < StackEnd.REGION_COUNT; r++) {
            RegionLook a = from.look(r);
            RegionLook b = to.look(r);
            // A region one end has wears that end's look all the way; both blend with p.
            if (a != null && b != null) StackMaterial.lerp(a, b, w, out.look[r]);
            else if (a != null) out.look[r].set(a);
            else if (b != null) out.look[r].set(b);
            else StackMaterial.clear(out.look[r]);
            // The foot always exists.
            out.present[r] = r == StackEnd.FOOT || a != null || b != null;
            out.share[r] = regionShare(a != null, b != null, w);
            evenOut(from.evenOut[r], to.evenOut[r], w, out.evenOut[r]);
        }
        // The strip under the pill blends by presence: its owner at each end.
        float stripFrom = from.strip == StackEnd.StripRule.KEYBOARD_FOOT ? 1f : 0f;
        float stripTo = to.strip == StackEnd.StripRule.KEYBOARD_FOOT ? 1f : 0f;
        out.stripAlpha = w <= 0f ? stripFrom : w >= 1f ? stripTo
            : stripFrom + (stripTo - stripFrom) * w;
        out.inkArgb = w <= 0f ? from.inkArgb : w >= 1f ? to.inkArgb
            : StackMaterial.mixArgb(from.inkArgb, to.inkArgb, w);
        // The topology switches only where none of the stack shows, else at landing.
        int stackShown = shownPx(out, StackEnd.ROWS) + shownPx(out, StackEnd.KEYBOARD)
            + shownPx(out, StackEnd.UNDER);
        out.topology = from.topology() == to.topology() || w >= 1f || stackShown == 0
            ? (w > 0f ? to.topology() : from.topology()) : from.topology();
    }

    private static void rect(@NonNull Frame out, int region, boolean laidOut, int top, int bottom,
                             int floor) {
        if (!laidOut) {
            out.regionTop[region] = 0;
            out.regionBottom[region] = 0;
            return;
        }
        out.regionTop[region] = top;
        out.regionBottom[region] = Math.max(top, Math.min(bottom, floor));
    }

    private static int shownPx(@NonNull Frame out, int region) {
        return out.visible(region) ? out.regionBottom[region] - out.regionTop[region] : 0;
    }

    private static void evenOut(@Nullable float[] a, @Nullable float[] b, float w,
                                @NonNull float[] out) {
        for (int i = 0; i < 20; i++) {
            float x = a != null ? a[i] : IDENTITY[i];
            float y = b != null ? b[i] : IDENTITY[i];
            out[i] = w <= 0f ? x : w >= 1f ? y : x + (y - x) * w;
        }
    }

    private static void identity(@NonNull float[] out) {
        System.arraycopy(IDENTITY, 0, out, 0, 20);
    }

    private static final float[] IDENTITY = {
        1, 0, 0, 0, 0,
        0, 1, 0, 0, 0,
        0, 0, 1, 0, 0,
        0, 0, 0, 1, 0};

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}