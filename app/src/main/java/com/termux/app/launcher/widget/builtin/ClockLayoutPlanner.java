package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Box;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Ink;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Measure;
import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;
import com.termux.app.launcher.widget.builtin.WidgetTokens.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Clock widget's layout plan at every span (spec #101, ticket #112). The face is one box the
 * widget draws itself ({@link WidgetLayoutPlan.Type#FACE}); the rest are the widget tokens' parts.
 * 1×1 is the time alone, filling the card to the inset; 2×1 and 4×1 add the date; 2×2 and 4×2
 * add the next alarm, when there is one. Where the room is short the date and the alarm step down
 * to the Label role, then the alarm goes, then the date, so the face never falls under its
 * smallest. Android-free.
 */
final class ClockLayoutPlanner {
    private ClockLayoutPlanner() { }

    /** The kind declares it ({@link BuiltinWidgetKind#maxSpan}): 4×2, whose wide spans carry the time zones. */
    static final BuiltinWidgetSpan MAX_SPAN = BuiltinWidgetKind.CLOCK.maxSpan;

    /** One extra time zone as a column: its city, its time, and (4×2) how it differs from here. */
    static final class ZoneColumn {
        @NonNull final String city;
        @NonNull final String time;
        @NonNull final String offset;

        ZoneColumn(@NonNull String city, @NonNull String time, @NonNull String offset) {
            this.city = city; this.time = time; this.offset = offset;
        }
    }

    /** The most zones the Clock shows. */
    static final int MAX_ZONES = 2;
    /** The narrowest the face and its date are kept at beside the zones. */
    static final float LEFT_MIN_DP = 96f;

    static final String FACE = "face";
    static final String DATE = "date";
    static final String ALARM_GLYPH = "alarm.glyph";
    static final String ALARM = "alarm";
    static final String TOUCH = "touch";

    /** The alarm's bell: Codicons' outlined bell. */
    static final String GLYPH_ALARM = "";

    /** The smallest a digital face's box is kept at, and the dial's. */
    static final float FACE_MIN_DP = 24f;
    static final float DIAL_MIN_DP = 48f;
    /** The narrowest text column kept beside a dial. */
    static final float TEXT_COLUMN_MIN_DP = 56f;
    /** A card this much wider than tall sets its text beside the dial, not under it. */
    private static final float SIDE_BY_SIDE_RATIO = 1.6f;

    private static final float GAP_S = WidgetTokens.GAP_TIGHT_DP;
    private static final float GAP_M = WidgetTokens.GAP_DP;
    private static final float GAP_L = WidgetTokens.GAP_LOOSE_DP;
    private static final float HAIRLINE_DP = 1f;
    static final String ZONE_RULE = "zone.%d.rule";
    static final String ZONE_CITY = "zone.%d.city";
    static final String ZONE_TIME = "zone.%d.time";
    static final String ZONE_OFFSET = "zone.%d.offset";

    /** The roles a zone column's time and its city (and offset) try, in order. */
    private static final Role[][] ZONE_ROLES = {
        {Role.HEADLINE, Role.LABEL},
        {Role.TITLE, Role.LABEL},
        {Role.TITLE, Role.LABEL_FORCED},
    };

    /** How tall the face paints in a box, so the parts under it sit right under its ink. */
    interface FaceMeasure {
        /**
         * The face's painted height, in dp, at the largest size that fits {@code widthDp} by
         * {@code maxHeightDp}; never more than {@code maxHeightDp}.
         */
        float heightDp(float widthDp, float maxHeightDp);
    }

    /** The text roles the date and the alarm try, in order; the last is the forced floor. */
    private static final Role[][] TEXT_ROLES = {
        {Role.TITLE, Role.BODY},
        {Role.LABEL, Role.LABEL},
    };

    /**
     * The Clock's plan at {@code span} in a {@code widthDp}×{@code heightDp} card at
     * {@code fontScale}. {@code date} and {@code alarm} are the texts to show (an empty alarm
     * shows none).
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull ClockFace face, @NonNull String date,
                                          @NonNull String alarm, @NonNull FaceMeasure faceMeasure,
                                          @NonNull Measure measure) {
        return plan(span, widthDp, heightDp, fontScale, face, date, alarm,
            Collections.emptyList(), faceMeasure, measure);
    }

    /**
     * As above, with the time zones: at 4×1 and 4×2 each is a column right of the face, behind a
     * hairline, its city over its time (and, at 4×2, its offset). Where the room is short the
     * column's roles step down, then the offset goes, then the second zone, then the first, so
     * the face always keeps {@link #LEFT_MIN_DP}.
     */
    @NonNull static WidgetLayoutPlan plan(@NonNull BuiltinWidgetSpan span, float widthDp,
                                          float heightDp, float fontScale,
                                          @NonNull ClockFace face, @NonNull String date,
                                          @NonNull String alarm,
                                          @NonNull List<ZoneColumn> zones,
                                          @NonNull FaceMeasure faceMeasure,
                                          @NonNull Measure measure) {
        Box card = new Box(0f, 0f, widthDp, heightDp);
        Box inner = WidgetLayoutPlan.insetBox(widthDp, heightDp, WidgetTokens.insetDp(span));
        List<Part> parts = new ArrayList<>();
        boolean showsDate = span != BuiltinWidgetSpan.ONE_BY_ONE && !date.isEmpty();
        boolean showsAlarm = showsDate && span.isTall() && !alarm.isEmpty();
        Texts texts = new Texts(date, showsDate, alarm, showsAlarm, fontScale, measure);
        Box left = inner;
        if (span.isWide() && !zones.isEmpty()) {
            left = addZones(span, inner, fontScale, zones, measure, parts);
        }
        if (face == ClockFace.ANALOG) {
            dial(span, left, texts, parts);
        } else {
            digital(span, left, texts, faceMeasure, parts);
        }
        parts.add(Part.touch(TOUCH, card));
        return new WidgetLayoutPlan(BuiltinWidgetKind.CLOCK, span, MAX_SPAN, fontScale, card,
            inner, parts);
    }

    // ----- the time zones -------------------------------------------------------------------

    /**
     * Adds the zone columns at the right of {@code inner} and returns what is left for the face.
     * Adds none (and returns {@code inner}) when not even one fits.
     */
    @NonNull private static Box addZones(@NonNull BuiltinWidgetSpan span, @NonNull Box inner,
                                         float fontScale, @NonNull List<ZoneColumn> zones,
                                         @NonNull Measure measure, @NonNull List<Part> parts) {
        boolean tall = span.isTall();
        for (int count = Math.min(MAX_ZONES, zones.size()); count >= 1; count--) {
            for (boolean offsets = tall; ; offsets = false) {
                for (Role[] roles : ZONE_ROLES) {
                    float timeSize = WidgetTokens.sizeDp(roles[0], fontScale);
                    float textSize = WidgetTokens.sizeDp(roles[1], fontScale);
                    float stack = WidgetTokens.lineDp(textSize) + GAP_S
                        + WidgetTokens.lineDp(timeSize)
                        + (offsets ? GAP_S + WidgetTokens.lineDp(textSize) : 0f);
                    if (stack > inner.height) continue;
                    float[] widths = new float[count];
                    float block = 0f;
                    for (int i = 0; i < count; i++) {
                        ZoneColumn zone = zones.get(i);
                        float w = Math.max(measure.widthDp(zone.time, roles[0], true, timeSize),
                            measure.widthDp(zone.city, roles[1], false, textSize));
                        if (offsets) {
                            w = Math.max(w, measure.widthDp(zone.offset, roles[1], false,
                                textSize));
                        }
                        widths[i] = (float) Math.ceil(w);
                        block += GAP_L + HAIRLINE_DP + GAP_L + widths[i];
                    }
                    if (inner.width - block < LEFT_MIN_DP) continue;
                    float x = inner.right() - block;
                    float top = inner.centerY() - stack / 2f;
                    for (int i = 0; i < count; i++) {
                        ZoneColumn zone = zones.get(i);
                        parts.add(Part.rule(String.format(java.util.Locale.ROOT, ZONE_RULE, i),
                            new Box(x + GAP_L, top, HAIRLINE_DP, stack)));
                        float left = x + GAP_L + HAIRLINE_DP + GAP_L;
                        float y = top;
                        parts.add(Part.text(String.format(java.util.Locale.ROOT, ZONE_CITY, i),
                            new Box(left, y, widths[i], WidgetTokens.lineDp(textSize)), zone.city,
                            roles[1], false, textSize, Ink.SOFT));
                        y += WidgetTokens.lineDp(textSize) + GAP_S;
                        parts.add(Part.text(String.format(java.util.Locale.ROOT, ZONE_TIME, i),
                            new Box(left, y, widths[i], WidgetTokens.lineDp(timeSize)), zone.time,
                            roles[0], true, timeSize, Ink.STRONG));
                        if (offsets) {
                            y += WidgetTokens.lineDp(timeSize) + GAP_S;
                            parts.add(Part.text(String.format(java.util.Locale.ROOT,
                                ZONE_OFFSET, i), new Box(left, y, widths[i],
                                WidgetTokens.lineDp(textSize)), zone.offset, roles[1], false,
                                textSize, Ink.SOFT));
                        }
                        x += GAP_L + HAIRLINE_DP + GAP_L + widths[i];
                    }
                    return new Box(inner.left, inner.top, inner.width - block, inner.height);
                }
                if (!offsets) break;
            }
        }
        return inner;
    }

    // ----- the digital faces ----------------------------------------------------------------

    /** The face over the date (and the alarm), left-aligned, the group centred in the card. */
    private static void digital(@NonNull BuiltinWidgetSpan span, @NonNull Box inner,
                                @NonNull Texts texts, @NonNull FaceMeasure faceMeasure,
                                @NonNull List<Part> parts) {
        if (span == BuiltinWidgetSpan.ONE_BY_ONE || !texts.any()) {
            // The time alone fills the card to the inset.
            parts.add(Part.face(FACE, inner));
            return;
        }
        // The roles step down, then the alarm goes, then the date, until the face keeps its room.
        while (true) {
            float room = inner.height - texts.stackDp();
            if (room >= FACE_MIN_DP || !texts.shed()) break;
        }
        float room = Math.max(0f, inner.height - texts.stackDp());
        float faceHeight = Math.min(room, Math.max(0f, faceMeasure.heightDp(inner.width, room)));
        float group = faceHeight + texts.stackDp();
        float top = inner.top + Math.max(0f, (inner.height - group) / 2f);
        parts.add(Part.face(FACE, new Box(inner.left, top, inner.width, faceHeight)));
        texts.addUnder(inner.left, top + faceHeight, inner.width, false, parts);
    }

    // ----- the Analog face ------------------------------------------------------------------

    /**
     * The round dial: alone and centred at 1×1; with the texts beside it on a card much wider than
     * tall (2×1, 4×1, 4×2); with them centred under it on a squarer one (2×2).
     */
    private static void dial(@NonNull BuiltinWidgetSpan span, @NonNull Box inner,
                             @NonNull Texts texts, @NonNull List<Part> parts) {
        if (span == BuiltinWidgetSpan.ONE_BY_ONE || !texts.any()) {
            parts.add(Part.face(FACE, centredSquare(inner,
                Math.min(inner.width, inner.height))));
            return;
        }
        if (inner.width >= inner.height * SIDE_BY_SIDE_RATIO) {
            float side = Math.min(inner.height, inner.width - GAP_L - TEXT_COLUMN_MIN_DP);
            Box dial = new Box(inner.left, inner.centerY() - side / 2f, side, side);
            parts.add(Part.face(FACE, dial));
            float left = dial.right() + GAP_L;
            float width = inner.right() - left;
            while (texts.stackBesideDp() > inner.height && texts.shed()) { }
            if (!texts.any() || width < TEXT_COLUMN_MIN_DP) return;
            float top = inner.centerY() - texts.stackBesideDp() / 2f;
            texts.addBeside(left, top, width, parts);
            return;
        }
        while (true) {
            float side = Math.min(inner.width, inner.height - texts.stackDp());
            if (side >= DIAL_MIN_DP || !texts.shed()) break;
        }
        float side = Math.max(0f, Math.min(inner.width, inner.height - texts.stackDp()));
        float group = side + texts.stackDp();
        float top = inner.top + Math.max(0f, (inner.height - group) / 2f);
        parts.add(Part.face(FACE, new Box(inner.centerX() - side / 2f, top, side, side)));
        texts.addUnder(inner.left, top + side, inner.width, true, parts);
    }

    @NonNull private static Box centredSquare(@NonNull Box inner, float side) {
        return new Box(inner.centerX() - side / 2f, inner.centerY() - side / 2f, side, side);
    }

    // ----- the texts ------------------------------------------------------------------------

    /** The date and the alarm, at the roles they can still be set at. */
    private static final class Texts {
        @NonNull final String date;
        @NonNull final String alarm;
        boolean showsDate;
        boolean showsAlarm;
        int step;
        final float fontScale;
        @NonNull final Measure measure;

        Texts(@NonNull String date, boolean showsDate, @NonNull String alarm, boolean showsAlarm,
              float fontScale, @NonNull Measure measure) {
            this.date = date; this.showsDate = showsDate;
            this.alarm = alarm; this.showsAlarm = showsAlarm;
            this.fontScale = fontScale; this.measure = measure;
        }

        boolean any() { return showsDate; }

        @NonNull Role dateRole() { return TEXT_ROLES[step][0]; }
        @NonNull Role alarmRole() { return TEXT_ROLES[step][1]; }
        float dateSize() { return WidgetTokens.sizeDp(dateRole(), fontScale); }
        float alarmSize() { return WidgetTokens.sizeDp(alarmRole(), fontScale); }
        float dateLine() { return WidgetTokens.lineDp(dateSize()); }
        float alarmLine() { return WidgetTokens.lineDp(alarmSize()); }

        /** The texts stacked under a face, with the gap above them. */
        float stackDp() {
            if (!showsDate) return 0f;
            return GAP_S + dateLine() + (showsAlarm ? GAP_M + alarmLine() : 0f);
        }

        /** The texts stacked beside a dial, no gap above. */
        float stackBesideDp() {
            if (!showsDate) return 0f;
            return dateLine() + (showsAlarm ? GAP_M + alarmLine() : 0f);
        }

        /** One step less: a smaller role, then no alarm, then no date. False when nothing is left. */
        boolean shed() {
            if (!showsDate) return false;
            if (step < TEXT_ROLES.length - 1) { step++; return true; }
            if (showsAlarm) { showsAlarm = false; return true; }
            showsDate = false;
            return true;
        }

        /** The texts under a face whose ink ends at {@code y}, left-aligned or centred. */
        void addUnder(float left, float y, float width, boolean centred,
                      @NonNull List<Part> parts) {
            if (!showsDate) return;
            float top = y + GAP_S;
            addDate(left, top, width, centred, parts);
            if (showsAlarm) addAlarm(left, top + dateLine() + GAP_M, width, centred, parts);
        }

        /** The texts in a column from ({@code left}, {@code top}). */
        void addBeside(float left, float top, float width, @NonNull List<Part> parts) {
            if (!showsDate) return;
            addDate(left, top, width, false, parts);
            if (showsAlarm) addAlarm(left, top + dateLine() + GAP_M, width, false, parts);
        }

        private void addDate(float left, float top, float width, boolean centred,
                             @NonNull List<Part> parts) {
            float size = dateSize();
            float shown = Math.min(width, measure.widthDp(date, dateRole(), false, size));
            float x = centred ? left + (width - shown) / 2f : left;
            parts.add(Part.text(DATE, new Box(x, top, centred ? shown : width, dateLine()), date,
                dateRole(), false, size, Ink.SOFT));
        }

        private void addAlarm(float left, float top, float width, boolean centred,
                              @NonNull List<Part> parts) {
            float size = alarmSize();
            float line = alarmLine();
            float glyph = size;
            float textWidth = measure.widthDp(alarm, alarmRole(), false, size);
            float run = Math.min(width, glyph + GAP_S + textWidth);
            float x = centred ? left + (width - run) / 2f : left;
            parts.add(Part.glyph(ALARM_GLYPH, new Box(x, top + (line - glyph) / 2f, glyph, glyph),
                GLYPH_ALARM, Ink.SOFT));
            float textLeft = x + glyph + GAP_S;
            parts.add(Part.text(ALARM, new Box(textLeft, top,
                Math.max(0f, Math.min(textWidth, left + width - textLeft)), line), alarm,
                alarmRole(), false, size, Ink.SOFT));
        }
    }
}
