package com.termux.app.launcher.az;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.paging.DockPagingModel;

/**
 * The floating strip of matches the standalone A–Z index shows above the letters, as arithmetic:
 * how many icons a width holds, where each one sits, which of them a page shows, which side the
 * label reads on, and the curve the focused icon breathes to.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no {@code Canvas}, no clock. The activity asks for a
 * {@link Strip} and hands the same rectangle to {@link AzScrubGesture.Geometry} as the icon track,
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

    /** How many icons fit in a host of this width, between the strip's side margins. */
    public static int slotsForWidth(float hostWidthPx, float density) {
        float d = Math.max(0f, density);
        float usable = hostWidthPx - (2f * SIDE_MARGIN_DP * d);
        float icon = ICON_SIZE_DP * d;
        float pitch = icon + (SLOT_SPACING_DP * d);
        if (usable < icon || pitch <= 0f) {
            return 1;
        }
        // n icons take n*icon + (n-1)*spacing, i.e. n*pitch - spacing.
        int slots = (int) Math.floor((usable + (SLOT_SPACING_DP * d)) / pitch);
        return Math.max(1, Math.min(MAX_SLOTS, slots));
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
     * Lays a strip of {@code visibleCount} icons out centred in its host and resting above
     * {@code anchorTopPx} — the top of the letters, so the strip floats clear of them.
     *
     * @return null when there is nothing to show
     */
    @Nullable
    public static Strip layout(float hostLeftPx, float hostWidthPx, float anchorTopPx,
                               int visibleCount, float density) {
        if (visibleCount <= 0 || hostWidthPx <= 0f) {
            return null;
        }
        float d = Math.max(0f, density);
        int slots = Math.min(Math.max(1, visibleCount), slotsForWidth(hostWidthPx, d));
        float icon = ICON_SIZE_DP * d;
        float spacing = SLOT_SPACING_DP * d;
        float bandWidth = (slots * icon) + ((slots - 1) * spacing);
        float left = hostLeftPx + Math.max(SIDE_MARGIN_DP * d, (hostWidthPx - bandWidth) * 0.5f);
        float bottom = anchorTopPx - (ANCHOR_GAP_DP * d);
        return new Strip(left, bottom - icon, left + bandWidth, bottom, icon, spacing, slots);
    }

    /** Portrait reads the name above the icon, landscape below it. */
    @NonNull
    public static LabelSide labelSide(boolean landscape) {
        return landscape ? LabelSide.BELOW : LabelSide.ABOVE;
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
