package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.paging.DockPagingModel;
import com.termux.app.place.PlaceLayout.Edge;

/**
 * The floating strip of matches the standalone A–Z index shows beside the letters, as arithmetic:
 * how many icons a length holds, where the band grows out of the bar, where each icon sits, which of
 * them a page shows, which side the label reads on, and the curve the focused icon breathes to.
 *
 * <p>The band is <em>always a row of icons reading left to right</em>, and it always grows
 * <em>away from the bar, towards the middle of the screen</em>: up off a bottom bar, down off a top
 * one, left off a right-hand column and right off a left-hand one. That is the whole of what the
 * edge decides — the axis never turns, so the matches read the same way round wherever the index
 * stands, and the band can never be drawn over the letters it came from.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no {@code Canvas}, no clock. It answers in the space
 * its rectangles were given in, which for the standalone index is the screen. The activity asks for
 * a {@link Strip} and hands the same rectangle to {@link AzScrubGesture.Geometry} as the icon track,
 * so upward lock, icon tracking, edge paging and release-to-launch are the row's proven machinery
 * with a different rectangle under them. {@code LauncherAzGestureFxView} just draws the answer.
 *
 * <p>Paging is {@link DockPagingModel}'s A–Z paging, not a second copy of it: the strip is a row of
 * slots like the apps row is, and the "pull the last page back so it renders full" rule is the same
 * rule.
 */
public final class AzFloatingStripPolicy {

    private AzFloatingStripPolicy() {}

    /** Which side of the focused icon its label reads on. */
    public enum LabelSide {
        /** Above, which is what the apps row's preview bubble has always done. */
        ABOVE,
        /** Below, so a landscape thumb reads the name next to the row rather than up the screen. */
        BELOW
    }

    /** One icon's drawn size. Between the row's icons and the preview bubble's, deliberately. */
    public static final float ICON_SIZE_DP = 40f;
    /** Air between two icons. */
    public static final float SLOT_SPACING_DP = 10f;
    /** Air the strip keeps from each side of its host. */
    public static final float SIDE_MARGIN_DP = 16f;
    /** Air between the strip's underside and the letters it floats over. */
    public static final float ANCHOR_GAP_DP = 12f;
    /** Never more icons than a thumb can pick apart in one sweep. */
    public static final int MAX_SLOTS = 8;

    /**
     * How far past a slot's boundary the finger must travel before the focus moves on. Mirrors
     * {@code AzScrubRowView.LETTER_SLOT_HYSTERESIS_RATIO}, because it is the same thumb.
     */
    public static final float SLOT_HYSTERESIS_RATIO = 0.22f;

    /** How wide the paging edge zone is, as a fraction of the strip; mirrors the apps row's. */
    public static final float EDGE_ZONE_RATIO = 0.14f;
    /** The narrowest that zone may get, in dp. */
    public static final float EDGE_ZONE_MIN_DP = 22f;

    /** How far outside the strip's band the finger may stray and still hold a slot, in dp. */
    public static final float SLOT_VERTICAL_SLACK_DP = 24f;

    /** One full breath, in and out. Slow enough to read as calm rather than as a blink. */
    public static final long BREATH_PERIOD_MS = 1600L;
    /** How far the ring grows at the top of a breath. */
    public static final float BREATH_SCALE_AMPLITUDE = 0.055f;
    /** How faint the ring gets at the bottom of a breath; it never disappears. */
    public static final float BREATH_ALPHA_FLOOR = 0.66f;

    /** No edge: the finger is not resting against either end of the strip. */
    public static final int EDGE_NONE = 0;
    public static final int EDGE_LEFT = -1;
    public static final int EDGE_RIGHT = 1;

    /** The strip's laid-out geometry, in whichever space the host width and anchor were given in. */
    public static final class Strip {

        /** The icon band's rectangle: exactly the slots, with no padding of its own. */
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        /** One icon's drawn size, which is also the band's height. */
        public final float iconSizePx;
        /** Air between two icons. */
        public final float spacingPx;
        /** How many slots the band holds. */
        public final int slotCount;

        Strip(float left, float top, float right, float bottom, float iconSizePx, float spacingPx,
              int slotCount) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.iconSizePx = iconSizePx;
            this.spacingPx = spacingPx;
            this.slotCount = slotCount;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }

        public float centerX() {
            return (left + right) * 0.5f;
        }

        public float centerY() {
            return (top + bottom) * 0.5f;
        }

        /** The pitch the slots repeat on: one icon and the air after it. */
        public float slotPitchPx() {
            return iconSizePx + spacingPx;
        }

        /** The centre of one slot. Out-of-range slots clamp, so a stale index cannot throw. */
        public float slotCenterX(int slot) {
            int bounded = Math.max(0, Math.min(Math.max(0, slotCount - 1), slot));
            return left + (iconSizePx * 0.5f) + (slotPitchPx() * bounded);
        }

        public float slotLeft(int slot) {
            return slotCenterX(slot) - (iconSizePx * 0.5f);
        }

        public boolean isEmpty() {
            return slotCount <= 0 || right <= left || bottom <= top;
        }
    }

    /** How many icons fit in a run of this length. The length is already clear of the margins. */
    public static int slotsForLength(float usableLengthPx, float density) {
        float d = Math.max(0f, density);
        float icon = ICON_SIZE_DP * d;
        float pitch = icon + (SLOT_SPACING_DP * d);
        if (usableLengthPx < icon || pitch <= 0f) {
            return 1;
        }
        // n icons take n*icon + (n-1)*spacing, i.e. n*pitch - spacing.
        int slots = (int) Math.floor((usableLengthPx + (SLOT_SPACING_DP * d)) / pitch);
        return Math.max(1, Math.min(MAX_SLOTS, slots));
    }

    /** How many icons fit in a host of this width, between the strip's side margins. */
    public static int slotsForWidth(float hostWidthPx, float density) {
        return slotsForLength(hostWidthPx - (2f * SIDE_MARGIN_DP * Math.max(0f, density)), density);
    }

    /** Which way the matches grow out of the bar, on screen: always towards the screen's middle. */
    public enum Growth { UP, DOWN, LEFT, RIGHT }

    @NonNull
    public static Growth growthFor(@NonNull Edge barEdge) {
        switch (barEdge) {
            case TOP: return Growth.DOWN;
            case LEFT: return Growth.RIGHT;
            case RIGHT: return Growth.LEFT;
            case BOTTOM:
            default: return Growth.UP;
        }
    }

    /**
     * The run the band has to lay its icons out in, clear of the bar and of the canvas's margins.
     *
     * <p>Off a top or bottom bar the band runs across the canvas, so the run is the canvas's width.
     * Off a column it runs from the bar towards the far side, so the run is what is left of the
     * canvas once the bar and the air beside it are taken off — which is what stops a band ever
     * being laid out longer than the space it is allowed to grow into.
     */
    public static float availableLengthPx(@NonNull Edge barEdge, @NonNull AzScrubGesture.Bounds bar,
                                          @NonNull AzScrubGesture.Bounds canvas, float density) {
        float d = Math.max(0f, density);
        float gap = ANCHOR_GAP_DP * d;
        float margin = SIDE_MARGIN_DP * d;
        switch (barEdge) {
            case LEFT:
                return Math.max(0f, (canvas.right - margin) - (bar.right + gap));
            case RIGHT:
                return Math.max(0f, (bar.left - gap) - (canvas.left + margin));
            case TOP:
            case BOTTOM:
            default:
                return Math.max(0f, canvas.width() - (2f * margin));
        }
    }

    /** How many pages a candidate list fills at that slot count. Always at least one. */
    public static int pageCount(int entryCount, int slots) {
        return DockPagingModel.azPageCount(Math.max(0, entryCount), Math.max(1, slots));
    }

    /** The first candidate index the page shows. */
    public static int pageStart(int entryCount, int pageIndex, int slots) {
        return DockPagingModel.azPageStart(Math.max(0, entryCount), pageIndex, Math.max(1, slots));
    }

    /** How many candidates the page actually shows, which is the strip's visible slot count. */
    public static int pageSize(int entryCount, int pageIndex, int slots) {
        int total = Math.max(0, entryCount);
        int start = pageStart(total, pageIndex, slots);
        return Math.max(0, Math.min(Math.max(1, slots), total - start));
    }

    /**
     * Lays the page's icons out as one row, growing out of the bar towards the middle of the
     * screen and never touching the bar it came from.
     *
     * <p>Along the bar the band follows the finger — centred on the letter being held for a row,
     * level with it for a column — so the matches appear where the thumb already is. Across the
     * bar it is pinned a fixed gap clear of the letters. Either way it is clamped inside
     * {@code canvas} with the strip's own margin, and a band that would run past an end slides back
     * inside it rather than being cropped.
     *
     * @param bar       the letters' own rectangle, the thing the band must not cover
     * @param canvas    the space the band is allowed to occupy
     * @param anchorXPx where the finger is across the screen; the band follows it off a row
     * @param anchorYPx where the finger is down the screen; the band follows it off a column
     * @return null when there is nothing to show, or nowhere to show it
     */
    @Nullable
    public static Strip layout(@NonNull Edge barEdge, @NonNull AzScrubGesture.Bounds bar,
                               @NonNull AzScrubGesture.Bounds canvas, float anchorXPx,
                               float anchorYPx, int visibleCount, float density) {
        if (visibleCount <= 0 || bar.isEmpty() || canvas.isEmpty()) {
            return null;
        }
        float d = Math.max(0f, density);
        float icon = ICON_SIZE_DP * d;
        float spacing = SLOT_SPACING_DP * d;
        float gap = ANCHOR_GAP_DP * d;
        float margin = SIDE_MARGIN_DP * d;
        int slots = Math.min(Math.max(1, visibleCount),
            slotsForLength(availableLengthPx(barEdge, bar, canvas, d), d));
        float band = (slots * icon) + ((slots - 1) * spacing);
        float left;
        float top;
        switch (barEdge) {
            case LEFT:
                left = slide(bar.right + gap, bar.right + gap, canvas.right - margin - band);
                top = slide(anchorYPx - (icon * 0.5f), canvas.top + margin,
                    canvas.bottom - margin - icon);
                break;
            case RIGHT:
                left = slide(bar.left - gap - band, canvas.left + margin, bar.left - gap - band);
                top = slide(anchorYPx - (icon * 0.5f), canvas.top + margin,
                    canvas.bottom - margin - icon);
                break;
            case TOP:
                left = slide(anchorXPx - (band * 0.5f), canvas.left + margin,
                    canvas.right - margin - band);
                top = bar.bottom + gap;
                break;
            case BOTTOM:
            default:
                left = slide(anchorXPx - (band * 0.5f), canvas.left + margin,
                    canvas.right - margin - band);
                top = bar.top - gap - icon;
                break;
        }
        return new Strip(left, top, left + band, top + icon, icon, spacing, slots);
    }

    /**
     * Slides a band back inside {@code [lo, hi]}. A band too long for the room it is given keeps
     * the low end rather than being centred on nothing, which is what a sliver of a canvas leaves.
     */
    private static float slide(float value, float lo, float hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, value));
    }

    /** Portrait reads the name above the icon, landscape below it. */
    @NonNull
    public static LabelSide labelSide(boolean landscape) {
        return landscape ? LabelSide.BELOW : LabelSide.ABOVE;
    }

    /**
     * Which side of the band the focused app's name reads on. It always reads on the far side from
     * the bar, so it never lands in the gap between the letters and the matches they produced: under
     * the band for a top bar, over it everywhere else, where the orientation decides as it always
     * has.
     */
    @NonNull
    public static LabelSide labelSideFor(@NonNull Edge barEdge, boolean landscape) {
        return barEdge == Edge.TOP ? LabelSide.BELOW : labelSide(landscape);
    }

    /**
     * The slot under {@code x}, with the row's hysteresis applied against the slot last resolved so
     * a thumb parked on a boundary does not flicker between two apps.
     *
     * @param lastSlot the slot the previous sample resolved, or -1 for none
     * @return the slot, or -1 when the point is not on the strip
     */
    public static int slotAt(@Nullable Strip strip, float x, float y, int lastSlot, float density) {
        if (strip == null || strip.isEmpty()) {
            return -1;
        }
        float slack = SLOT_VERTICAL_SLACK_DP * Math.max(0f, density);
        if (y < strip.top - slack || y > strip.bottom + slack) {
            return -1;
        }
        float pitch = strip.slotPitchPx();
        float local = x - strip.left;
        // The ends catch anything beyond them: a finger past the last icon still holds it, which is
        // what makes the edge-paging dwell reachable without losing focus first.
        int candidate = (int) Math.floor(local / pitch);
        candidate = Math.max(0, Math.min(strip.slotCount - 1, candidate));
        if (lastSlot < 0 || lastSlot >= strip.slotCount || candidate == lastSlot) {
            return candidate;
        }
        float hysteresis = pitch * SLOT_HYSTERESIS_RATIO;
        if (candidate > lastSlot) {
            float boundary = (lastSlot + 1) * pitch;
            if (local < boundary + hysteresis) {
                return lastSlot;
            }
        } else {
            float boundary = lastSlot * pitch;
            if (local > boundary - hysteresis) {
                return lastSlot;
            }
        }
        return candidate;
    }

    /**
     * Which end of the strip the finger is resting against, which is what starts an edge-paging
     * dwell. {@link #EDGE_NONE} unless there is more than one page to reach.
     */
    public static int edgeAt(@Nullable Strip strip, float x, boolean hasOtherPages, float density) {
        if (strip == null || strip.isEmpty() || !hasOtherPages) {
            return EDGE_NONE;
        }
        float zone = Math.max(EDGE_ZONE_MIN_DP * Math.max(0f, density),
            strip.width() * EDGE_ZONE_RATIO);
        if (x <= strip.left + zone) {
            return EDGE_LEFT;
        }
        if (x >= strip.right - zone) {
            return EDGE_RIGHT;
        }
        return EDGE_NONE;
    }

    /** Where in a breath a stretch of elapsed time lands, 0..1 and wrapping. */
    public static float breathPhase(long elapsedMs) {
        long wrapped = elapsedMs % BREATH_PERIOD_MS;
        if (wrapped < 0L) {
            wrapped += BREATH_PERIOD_MS;
        }
        return wrapped / (float) BREATH_PERIOD_MS;
    }

    /**
     * The breath itself: a raised cosine over the phase, so it is 0 at both ends of the cycle, 1 in
     * the middle, and flat at each turn — no visible corner where the ring changes direction.
     */
    public static float breathEase(float phase) {
        float p = phase - (float) Math.floor(phase);
        return (float) ((1d - Math.cos(2d * Math.PI * p)) * 0.5d);
    }

    /** How much the ring is scaled up at this point in the breath. */
    public static float breathScale(float phase) {
        return 1f + (BREATH_SCALE_AMPLITUDE * breathEase(phase));
    }

    /** How opaque the ring is at this point in the breath. */
    public static float breathAlpha(float phase) {
        return BREATH_ALPHA_FLOOR + ((1f - BREATH_ALPHA_FLOOR) * breathEase(phase));
    }
}
