package com.termux.app.statusbar;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial;
import com.termux.app.page.Side;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.SeamFade;
import com.termux.app.place.StackScene;
import com.termux.app.place.StatusStripRide;

import java.util.Arrays;

/**
 * The top stack's scene (ADR 0030 section 6): the status bar as one shared slab that stays put
 * across a crossing and blends its look and ink on {@link SeamFade}'s curve, with the window chips
 * riding the Terminal's side across it and Home's float chips riding Home's
 * ({@link StackScene#sideSlide}, so each moves with the bottom stack's rows of its side). A bar that cannot be one slab across both ends (only one end
 * shows it, or the two draw it in different outlines) rides with a page as before, wearing one
 * end's look. Pure: the renderer fills two {@link End}s, asks {@link #at} for a {@link Frame}
 * per frame and writes it to the views. Both reuse their objects; nothing allocates here.
 */
public final class TopStackScene {

    private TopStackScene() {}

    /**
     * Ink slots in an {@link End} and a {@link Frame}: one per colour the bar's content is drawn
     * in, laid out by {@link StatusInkSlots}; the renderer hands the blended array to its
     * {@link TopStackRenderer.Inks} sink.
     */
    public static final int INK_COUNT = StatusInkSlots.COUNT;

    /** {@link Frame#wornLookId()} while the look is part way from one end's to the other's. */
    public static final int MIXED = Integer.MIN_VALUE;

    /** One end of a crossing, or the rest. */
    public static final class End {
        /** The status bar shows on this page. */
        public boolean barShown;
        /** The bar edge setting; TOP or BOTTOM, a side edge makes the bar fade in place. */
        public PlaceLayout.Edge edge;
        public Side side = Side.HOME;
        /** The page's look id. */
        public int lookId;
        /** Equal when two ends draw the bar in one outline (style, radius). */
        public int outlineKey;
        /** The window chips are present (windows exist and are not hidden). */
        public boolean chipsShown;
        /** The strip behind the system bar shows. */
        public boolean stripShown;
        /** That strip continues the bar's glass. */
        public boolean stripDressedForBar;
        /** Ink colours for the bar's content; light and dark looks give different values. */
        public final int[] inks = new int[INK_COUNT];
        /** {@link #inks} hold a measurement; false until the owner resolved them. */
        public boolean inksResolved;

        public void set(@NonNull End o) {
            setShape(o);
            inksResolved = o.inksResolved;
            System.arraycopy(o.inks, 0, inks, 0, INK_COUNT);
        }

        /** Everything but the inks: a rest keeps the inks the last measurement gave it. */
        public void setShape(@NonNull End o) {
            barShown = o.barShown;
            edge = o.edge;
            side = o.side;
            lookId = o.lookId;
            outlineKey = o.outlineKey;
            chipsShown = o.chipsShown;
            stripShown = o.stripShown;
            stripDressedForBar = o.stripDressedForBar;
        }
    }

    /** What the views wear this frame. */
    public static final class Frame {
        /** The slab is one across both ends and stays put. */
        public boolean shared;
        /** The bar host's translationX (0 when shared). */
        public float slabTx;
        /** The strip behind the system bar's translationX. */
        public float stripTx;
        /** The place content strip's translationX, relative to the bar host. */
        public float chipsTx;
        /** Home's float chip strip's translationX, relative to the bar host. */
        public float homeChipsTx;
        public boolean chipsShown;
        public int fromLookId;
        public int toLookId;
        /** 0 = from's look, 1 = to's. */
        public float lookShare;
        /** The blended inks. */
        public final int[] inks = new int[INK_COUNT];

        /** The look the bar wears whole, or {@link #MIXED} while it is between two. */
        public int wornLookId() {
            return lookShare <= 0f ? fromLookId : lookShare >= 1f ? toLookId : MIXED;
        }

        /** Whether the views would draw the same; {@link #shared} alone changes nothing drawn. */
        public boolean sameAs(@NonNull Frame o) {
            return slabTx == o.slabTx && stripTx == o.stripTx && chipsTx == o.chipsTx
                && homeChipsTx == o.homeChipsTx
                && chipsShown == o.chipsShown && wornLookId() == o.wornLookId()
                && Arrays.equals(inks, o.inks);
        }

        public void set(@NonNull Frame o) {
            shared = o.shared;
            slabTx = o.slabTx;
            stripTx = o.stripTx;
            chipsTx = o.chipsTx;
            homeChipsTx = o.homeChipsTx;
            chipsShown = o.chipsShown;
            fromLookId = o.fromLookId;
            toLookId = o.toLookId;
            lookShare = o.lookShare;
            System.arraycopy(o.inks, 0, inks, 0, INK_COUNT);
        }
    }

    /** Whether the two ends are one slab: both show the bar, on one edge, in one outline. */
    public static boolean shared(@NonNull End a, @NonNull End b) {
        return a.barShown && b.barShown && a.edge == b.edge && a.outlineKey == b.outlineKey;
    }

    /** The frame at rest on {@code end}. */
    public static void rest(@NonNull End end, int widthPx, @NonNull Frame out) {
        out.shared = true;
        out.slabTx = 0f;
        out.stripTx = 0f;
        out.chipsTx = StackScene.sideSlide(Side.TERMINAL, end.side, end.side, 0f, widthPx);
        out.homeChipsTx = StackScene.sideSlide(Side.HOME, end.side, end.side, 0f, widthPx);
        out.chipsShown = end.chipsShown;
        out.fromLookId = end.lookId;
        out.toLookId = end.lookId;
        out.lookShare = 0f;
        System.arraycopy(end.inks, 0, out.inks, 0, INK_COUNT);
    }

    /**
     * The frame at progress {@code p} from {@code from} toward {@code to}, the chips sliding at
     * the page's own fraction {@code slide}; {@code p} drives the look and the inks only.
     *
     * @param slide       the page's fraction, 0 to 1, which only the chips' slide follows
     * @param rideXPx     the ride's x, or NaN when the bar does not ride a page
     * @param rideWearsTo whether the riding bar wears {@code to}'s look
     */
    public static void at(@NonNull End from, @NonNull End to, float p, float slide, int widthPx,
                          float rideXPx, boolean rideWearsTo, @NonNull Frame out) {
        at(from, to, p, slide, widthPx, rideXPx, rideWearsTo, Float.NaN, out);
    }

    /**
     * {@link #at(End, End, float, float, int, float, boolean, Frame)} with the inks on a share of
     * their own: {@code inkShare}, 0 to 1 toward {@code to}, the crossing's end share (the inks
     * over each page's ground cross with the wall, as the dock's legends do); NaN keeps them on
     * the look's share.
     */
    public static void at(@NonNull End from, @NonNull End to, float p, float slide, int widthPx,
                          float rideXPx, boolean rideWearsTo, float inkShare, @NonNull Frame out) {
        boolean one = shared(from, to);
        float chipsTx = StackScene.sideSlide(Side.TERMINAL, from.side, to.side, slide, widthPx);
        float homeChipsTx = StackScene.sideSlide(Side.HOME, from.side, to.side, slide, widthPx);
        if (!one && !Float.isNaN(rideXPx)) {
            End wearer = rideWearsTo ? to : from;
            out.shared = false;
            out.slabTx = rideXPx;
            out.stripTx = StatusStripRide.stripX(rideXPx, wearer.stripShown,
                wearer.stripDressedForBar);
            out.chipsTx = chipsTx - rideXPx;
            out.homeChipsTx = homeChipsTx - rideXPx;
            out.chipsShown = wearer.chipsShown;
            out.fromLookId = wearer.lookId;
            out.toLookId = wearer.lookId;
            out.lookShare = 0f;
            System.arraycopy(wearer.inks, 0, out.inks, 0, INK_COUNT);
            return;
        }
        float share = SeamFade.share(p);
        out.shared = one;
        out.slabTx = 0f;
        out.stripTx = 0f;
        out.chipsTx = chipsTx;
        out.homeChipsTx = homeChipsTx;
        out.chipsShown = p <= 0f ? from.chipsShown : p >= 1f ? to.chipsShown
            : from.chipsShown || to.chipsShown;
        out.fromLookId = from.lookId;
        out.toLookId = to.lookId;
        out.lookShare = share;
        float inks = Float.isNaN(inkShare) ? share : Math.max(0f, Math.min(1f, inkShare));
        for (int i = 0; i < INK_COUNT; i++) {
            out.inks[i] = StackMaterial.mixArgb(from.inks[i], to.inks[i], inks);
        }
    }

    /** {@link #at(End, End, float, float, int, float, boolean, Frame)} with the chips on {@code p} too. */
    public static void at(@NonNull End from, @NonNull End to, float p, int widthPx,
                          float rideXPx, boolean rideWearsTo, @NonNull Frame out) {
        at(from, to, p, p, widthPx, rideXPx, rideWearsTo, out);
    }
}
