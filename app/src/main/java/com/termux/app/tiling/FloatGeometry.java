package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Locale;

/**
 * Where a Float stands and how big it is (#421; spec "Layouts"): a {@link Box} in dp, measured
 * from the top-left of its place's {@link Room}, the area the place gives its windows less the
 * bands a Float keeps clear of (Android's bars, the cutout, the air the border hold uses; the
 * control bar's room is never part of the area). A Float stands wholly in its room, at least
 * {@link #MIN_WIDTH_DP} by {@link #MIN_HEIGHT_DP} unless the room itself is smaller.
 *
 * <p>The box is the window's saved state ({@link TilingStack#setFloatBox}); the room is the
 * screen's now, so a box is clamped into it each time it is shown and a turned phone never
 * loses a Float off the side.
 *
 * <p>Pure: no Android views.
 */
public final class FloatGeometry {

    /** The smallest a Float is made, in dp: a terminal of about 20 columns by 6 rows. */
    public static final float MIN_WIDTH_DP = 160f;
    public static final float MIN_HEIGHT_DP = 120f;
    /** How far a new Float steps down and right from one already standing where it would open, in dp. */
    public static final float CASCADE_DP = 28f;
    /** A new Float's share of its room's width and height, and the most it takes, in dp. */
    static final float NEW_WIDTH_SHARE = 0.84f;
    static final float NEW_HEIGHT_SHARE = 0.5f;
    static final float NEW_MAX_WIDTH_DP = 560f;
    static final float NEW_MAX_HEIGHT_DP = 480f;
    /** Two corners nearer than this, in dp, stand on the same spot. */
    private static final float SAME_SPOT_DP = 0.5f;

    private FloatGeometry() {}

    /** The room a place's Floats stand in, in dp, inside the place's area. */
    public static final class Room {
        /** Where the room starts inside the area, in dp. */
        public final float left;
        public final float top;
        /** The room's size in dp; never negative. */
        public final float width;
        public final float height;

        private Room(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0f, width);
            this.height = Math.max(0f, height);
        }

        /**
         * The room in an area {@code areaWidth} by {@code areaHeight} dp, less the bands kept
         * clear along each side ({@code clearLeft} … {@code clearBottom}, in dp).
         */
        @NonNull
        public static Room of(float areaWidth, float areaHeight, float clearLeft, float clearTop,
                              float clearRight, float clearBottom) {
            float left = Math.max(0f, clearLeft);
            float top = Math.max(0f, clearTop);
            return new Room(left, top, areaWidth - left - Math.max(0f, clearRight),
                areaHeight - top - Math.max(0f, clearBottom));
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Room(%.1f,%.1f %.1fx%.1f)", left, top, width, height);
        }
    }

    /** A Float's place and size in dp, from its room's top-left. Immutable. */
    public static final class Box {
        public final float left;
        public final float top;
        public final float width;
        public final float height;

        public Box(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public float right() {
            return left + width;
        }

        public float bottom() {
            return top + height;
        }

        /** The saved form: {@code [left, top, width, height]}. */
        @NonNull
        public JSONArray toJson() {
            try {
                return new JSONArray().put((double) left).put((double) top).put((double) width).put((double) height);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Reads {@link #toJson}'s form back; null for anything else (the Float then opens where a new one would). */
        @Nullable
        public static Box fromJson(@Nullable JSONArray json) {
            if (json == null || json.length() != 4) return null;
            float[] values = new float[4];
            for (int i = 0; i < 4; i++) {
                Object value = json.opt(i);
                if (!(value instanceof Number)) return null;
                double number = ((Number) value).doubleValue();
                if (Double.isNaN(number) || Double.isInfinite(number)) return null;
                values[i] = (float) number;
            }
            if (values[2] <= 0f || values[3] <= 0f) return null;
            return new Box(values[0], values[1], values[2], values[3]);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Box)) return false;
            Box box = (Box) other;
            return Float.compare(left, box.left) == 0 && Float.compare(top, box.top) == 0
                && Float.compare(width, box.width) == 0 && Float.compare(height, box.height) == 0;
        }

        @Override
        public int hashCode() {
            int hash = Float.floatToIntBits(left);
            hash = 31 * hash + Float.floatToIntBits(top);
            hash = 31 * hash + Float.floatToIntBits(width);
            return 31 * hash + Float.floatToIntBits(height);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Box(%.1f,%.1f %.1fx%.1f)", left, top, width, height);
        }
    }

    /**
     * Where a new Float opens in {@code room}: most of its width, centred across and a little
     * above the middle, so the keyboard rising beneath leaves it in view. Where another of the
     * place's Floats ({@code others}) already stands on that spot it steps {@link #CASCADE_DP}
     * down and right, starting again from the room's corner when a step would leave the room.
     */
    @NonNull
    public static Box newFloat(@NonNull Room room, @NonNull List<Box> others) {
        float width = Math.min(room.width,
            Math.max(MIN_WIDTH_DP, Math.min(NEW_MAX_WIDTH_DP, room.width * NEW_WIDTH_SHARE)));
        float height = Math.min(room.height,
            Math.max(MIN_HEIGHT_DP, Math.min(NEW_MAX_HEIGHT_DP, room.height * NEW_HEIGHT_SHARE)));
        float left = (room.width - width) / 2f;
        float top = (room.height - height) / 3f;
        for (int step = 0; step <= others.size(); step++) {
            if (!standsOn(others, left, top)) break;
            left += CASCADE_DP;
            top += CASCADE_DP;
            if (left + width > room.width || top + height > room.height) {
                left = 0f;
                top = 0f;
            }
        }
        return clamp(new Box(left, top, width, height), room);
    }

    /**
     * {@code box} wholly in {@code room}: its size between the smallest Float (or the room, when
     * that is smaller) and the room, then moved the least that brings it inside.
     */
    @NonNull
    public static Box clamp(@NonNull Box box, @NonNull Room room) {
        float width = clampSize(box.width, MIN_WIDTH_DP, room.width);
        float height = clampSize(box.height, MIN_HEIGHT_DP, room.height);
        float left = Math.max(0f, Math.min(box.left, room.width - width));
        float top = Math.max(0f, Math.min(box.top, room.height - height));
        return new Box(left, top, width, height);
    }

    /** {@code start} moved {@code dx}, {@code dy} dp, stopping at the room's edges; its size is kept. */
    @NonNull
    public static Box moved(@NonNull Box start, float dx, float dy, @NonNull Room room) {
        return clamp(new Box(start.left + dx, start.top + dy, start.width, start.height), room);
    }

    /**
     * {@code start} grown {@code dw}, {@code dh} dp by its bottom-right corner: its top-left
     * stays, and it stops at the room's edges and at the smallest Float.
     */
    @NonNull
    public static Box resized(@NonNull Box start, float dw, float dh, @NonNull Room room) {
        float width = Math.max(Math.min(MIN_WIDTH_DP, room.width), Math.min(start.width + dw, room.width - start.left));
        float height = Math.max(Math.min(MIN_HEIGHT_DP, room.height), Math.min(start.height + dh, room.height - start.top));
        return clamp(new Box(start.left, start.top, width, height), room);
    }

    private static float clampSize(float size, float min, float room) {
        return Math.max(Math.min(min, room), Math.min(size, room));
    }

    private static boolean standsOn(@NonNull List<Box> boxes, float left, float top) {
        for (Box box : boxes) {
            if (Math.abs(box.left - left) < SAME_SPOT_DP && Math.abs(box.top - top) < SAME_SPOT_DP) return true;
        }
        return false;
    }
}
