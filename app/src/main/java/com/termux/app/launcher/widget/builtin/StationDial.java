package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Analog face's geometry: a railway-station platform clock (the developer's variant A,
 * "Bahnhof", mockups {@code dial-A-*.svg}). A minute track of sixty marks with bold five-minute
 * bars, the numerals 1–12 in Libron Bold inside it, squared bar hands, and a red seconds hand only
 * while Seconds is on. The proportions are the drawing's, on its 500-unit dial (radius 250), with
 * one change: the hour hand stops well inside the numerals, where the drawing ran it into the 10.
 *
 * <p>Every stroke keeps a 1 dp floor and every numeral the 11 sp floor; where the twelve would
 * crowd one another at that floor, and always at 1×1, only 12, 3, 6 and 9 are set, and where even
 * those four would crowd, none. Sizes are in
 * pixels about the dial's centre, y down. Android-free: text is measured through
 * {@link TextMeasure}.</p>
 */
final class StationDial {
    /** Measures Libron Bold. */
    interface TextMeasure {
        /** The advance of {@code text} at {@code sizePx}. */
        float widthPx(@NonNull String text, float sizePx);
        /** The figures' ink height (cap height) at {@code sizePx}. */
        float figureHeightPx(float sizePx);
    }

    /** The drawing's dial radius, in its own units: every proportion below is over it. */
    private static final float R = 250f;
    /** The minute track's outer edge. */
    static final float TRACK_OUTER = 239f / R;
    /** A five-minute bar: its length in from the edge, and its width. */
    static final float MAJOR_LENGTH = 26f / R;
    static final float MAJOR_WIDTH = 8.4f / R;
    /** A minute mark. */
    static final float MINOR_LENGTH = 12f / R;
    static final float MINOR_WIDTH = 2.8f / R;
    /** The numerals at the drawing's size: 52 units of a 500-unit dial, 56 at 1×1. */
    static final float NUMERAL_SIZE = 52f / (2f * R);
    static final float NUMERAL_SIZE_CARDINAL = 56f / (2f * R);
    /** The air between a numeral and the track's inner edge. */
    static final float NUMERAL_AIR = 14f / R;
    /** The hour hand: its width, its tail past the centre, and the most it reaches. */
    static final float HOUR_WIDTH = 22f / R;
    static final float HOUR_TAIL = 37.5f / R;
    /** Shorter than the drawing's 137.5: the hour hand ends well inside the numerals. */
    static final float HOUR_REACH = 120f / R;
    /** The clear band the hour hand keeps short of the nearest numeral. */
    static final float HOUR_CLEAR = 20f / R;
    static final float MINUTE_WIDTH = 14f / R;
    static final float MINUTE_LENGTH = 212.5f / R;
    static final float MINUTE_TAIL = 37.5f / R;
    static final float SECOND_WIDTH = 3.2f / R;
    static final float SECOND_LENGTH = 207f / R;
    static final float SECOND_TAIL = 38f / R;
    static final float SECOND_TIP = 6f / R;
    /** The hub the hands turn on, and the seconds hand's red dot on it. */
    static final float HUB = 8f / R;
    static final float SECOND_HUB = 3.2f / R;

    static final float STROKE_FLOOR_DP = 1f;
    static final float NUMERAL_FLOOR_SP = WidgetTokens.TEXT_FLOOR_SP;

    static final String[] ALL = {"12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"};
    static final String[] CARDINAL = {"12", "3", "6", "9"};

    /** One numeral: its text and its ink box about the dial's centre. */
    static final class Numeral {
        @NonNull final String text;
        /** The box's centre and size; the text's ink is centred on it. */
        final float cx, cy, width, height;

        Numeral(@NonNull String text, float cx, float cy, float width, float height) {
            this.text = text; this.cx = cx; this.cy = cy; this.width = width; this.height = height;
        }

        float left() { return cx - width / 2f; }
        float right() { return cx + width / 2f; }
        float top() { return cy - height / 2f; }
        float bottom() { return cy + height / 2f; }

        boolean overlaps(@NonNull Numeral other) {
            return left() < other.right() && other.left() < right()
                && top() < other.bottom() && other.top() < bottom();
        }

        /** How far out the box reaches along its own direction from the centre. */
        float innerRadius() {
            double angle = Math.atan2(cx, -cy);
            float extent = (float) (Math.abs(Math.cos(angle)) * height / 2f
                + Math.abs(Math.sin(angle)) * width / 2f);
            return (float) Math.hypot(cx, cy) - extent;
        }
    }

    /** The dial laid out at one size. */
    static final class Geometry {
        final float radius;
        final float numeralPx;
        final boolean cardinal;
        @NonNull final List<Numeral> numerals;
        final float trackOuter, majorLength, majorWidth, minorLength, minorWidth;
        final float hourLength, hourWidth, hourTail;
        final float minuteLength, minuteWidth, minuteTail;
        final float secondLength, secondWidth, secondTail, secondTip;
        final float hub, secondHub;

        Geometry(float radius, float numeralPx, boolean cardinal, @NonNull List<Numeral> numerals,
                 float strokeFloor) {
            this.radius = radius;
            this.numeralPx = numeralPx;
            this.cardinal = cardinal;
            this.numerals = Collections.unmodifiableList(numerals);
            trackOuter = TRACK_OUTER * radius;
            majorLength = Math.max(MAJOR_LENGTH * radius, strokeFloor);
            majorWidth = Math.max(MAJOR_WIDTH * radius, strokeFloor);
            minorLength = Math.max(MINOR_LENGTH * radius, strokeFloor);
            minorWidth = Math.max(MINOR_WIDTH * radius, strokeFloor);
            float nearest = Float.MAX_VALUE;
            for (Numeral numeral : numerals) nearest = Math.min(nearest, numeral.innerRadius());
            hourLength = Math.max(HUB * radius * 2f,
                Math.min(HOUR_REACH * radius, nearest - HOUR_CLEAR * radius));
            hourWidth = Math.max(HOUR_WIDTH * radius, strokeFloor);
            hourTail = HOUR_TAIL * radius;
            minuteLength = MINUTE_LENGTH * radius;
            minuteWidth = Math.max(MINUTE_WIDTH * radius, strokeFloor);
            minuteTail = MINUTE_TAIL * radius;
            secondLength = SECOND_LENGTH * radius;
            secondWidth = Math.max(SECOND_WIDTH * radius, strokeFloor);
            secondTail = SECOND_TAIL * radius;
            secondTip = Math.max(SECOND_TIP * radius, strokeFloor);
            hub = Math.max(HUB * radius, strokeFloor);
            secondHub = Math.max(SECOND_HUB * radius, strokeFloor / 2f);
        }

        /** The track's inner edge: where the five-minute bars end. */
        float trackInner() { return trackOuter - majorLength; }
    }

    private StationDial() { }

    /**
     * The dial in a {@code diameterPx} square. {@code dpPx} and {@code spPx} are a dp and an sp in
     * pixels (the density and the scaled density), for the floors; {@code oneByOne} asks for the
     * four cardinal numerals whatever the room.
     */
    @NonNull static Geometry layout(float diameterPx, float dpPx, float spPx, boolean oneByOne,
                                    @NonNull TextMeasure measure) {
        float radius = Math.max(0f, diameterPx / 2f);
        float strokeFloor = STROKE_FLOOR_DP * dpPx;
        float floor = NUMERAL_FLOOR_SP * spPx;
        if (!oneByOne) {
            float size = Math.max(NUMERAL_SIZE * diameterPx, floor);
            List<Numeral> all = numerals(ALL, radius, size, strokeFloor, measure);
            if (!crowded(all)) return new Geometry(radius, size, false, all, strokeFloor);
        }
        float size = Math.max(NUMERAL_SIZE_CARDINAL * diameterPx, floor);
        List<Numeral> cardinal = numerals(CARDINAL, radius, size, strokeFloor, measure);
        // A dial too small for even the four at the floor (a large font on a dense grid's 1×1)
        // sets none: the bars still read, and no numeral shrinks under 11 sp or touches another.
        if (crowded(cardinal)) cardinal = new ArrayList<>();
        return new Geometry(radius, size, true, cardinal, strokeFloor);
    }

    /** {@code texts} set round the dial at {@code sizePx}, each clear of the track by the air. */
    @NonNull private static List<Numeral> numerals(@NonNull String[] texts, float radius,
                                                   float sizePx, float strokeFloor,
                                                   @NonNull TextMeasure measure) {
        float trackInner = TRACK_OUTER * radius - Math.max(MAJOR_LENGTH * radius, strokeFloor);
        float air = NUMERAL_AIR * radius;
        float height = measure.figureHeightPx(sizePx);
        List<Numeral> out = new ArrayList<>(texts.length);
        for (String text : texts) {
            int hour = Integer.parseInt(text) % 12;
            double angle = Math.toRadians(hour * 30d);
            float width = measure.widthPx(text, sizePx);
            float extent = (float) (Math.abs(Math.cos(angle)) * height / 2f
                + Math.abs(Math.sin(angle)) * width / 2f);
            float at = trackInner - air - extent;
            out.add(new Numeral(text, (float) (Math.sin(angle) * at),
                (float) (-Math.cos(angle) * at), width, height));
        }
        return out;
    }

    /** Whether any two numerals touch, or any reaches the hub. */
    static boolean crowded(@NonNull List<Numeral> numerals) {
        for (int i = 0; i < numerals.size(); i++) {
            Numeral a = numerals.get(i);
            if (a.innerRadius() <= 0f) return true;
            for (int j = i + 1; j < numerals.size(); j++) {
                if (a.overlaps(numerals.get(j))) return true;
            }
        }
        return false;
    }

    /** The hour hand's angle in degrees clockwise from 12. */
    static float hourAngle(int hour, int minute, int second) {
        return ((hour % 12) + minute / 60f + second / 3600f) * 30f;
    }

    static float minuteAngle(int minute, int second) {
        return (minute + second / 60f) * 6f;
    }

    static float secondAngle(int second) {
        return second * 6f;
    }
}
