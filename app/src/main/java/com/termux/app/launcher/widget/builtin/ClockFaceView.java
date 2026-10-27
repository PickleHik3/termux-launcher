package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.terminal.ClockFaceRenderer;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * One clock face drawn into its whole box: the station dial ({@link StationDial}), the Libron
 * numerals, or one of the bar's six through the shared {@link ClockFaceRenderer}, which this view
 * hosts as the bar does (the widget is its second host). It only draws: the widget hands it the
 * time on the minute (and on the second while Seconds is on and it is seen) and the colours it
 * stands on. A digit change animates only on the minute, Flip's fold and LED's glow included; the
 * seconds never do.
 */
final class ClockFaceView extends View {
    /** The red of the station clock's seconds hand, on a light card and on a dark one. */
    @ColorInt static final int SECONDS_RED_LIGHT = 0xFFD52B35;
    @ColorInt static final int SECONDS_RED_DARK = 0xFFF04B4E;

    /** The numerals face's seconds and period, as a share of its time's size (never under 12 sp). */
    static final float NUMERALS_META_SHARE = 0.28f;
    /** The gap between the numerals face's time and its seconds, as a share of the time's size. */
    private static final float NUMERALS_META_GAP = 0.12f;
    /** The numerals' period over the seconds, as a share of the seconds' size (the bar's stack). */
    private static final float NUMERALS_PERIOD_SHARE = 0.64f;
    /** The clear row between the seconds and the period over them, as a share of the seconds' cap. */
    private static final float NUMERALS_STACK_GAP = 0.3f;

    @NonNull private final BuiltinWidgetStyle style;
    @NonNull private final Typeface libron;
    private final float density;
    private final float scaledDensity;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    /** Libron's figure height per pixel of text size, measured once. */
    private final float figureRatio;

    @NonNull private ClockFace face = ClockWidgetSettings.FALLBACK_FACE;
    private boolean medium;
    private boolean seconds;
    /** The seconds and the period this box keeps ({@link #shedFor}), and the box it was for. */
    private boolean keptSeconds;
    private boolean keptPeriod = true;
    private float shedWidth = -1f, shedHeight = -1f;
    @Nullable private ClockFace shedFace;
    /** A kept meta run may cost the digits no more than this share of their size alone. */
    static final float META_KEEP = 0.6f;
    private boolean is24 = true;
    private boolean oneByOne;
    @Nullable private ClockFaceRenderer renderer;
    @NonNull private ZonedDateTime now = ZonedDateTime.now();
    @ColorInt private int ink;
    @ColorInt private int softInk;
    @ColorInt private int ground;

    // What the last layout of the dial and of the numerals came to, kept between frames.
    @Nullable private StationDial.Geometry dial;
    private float dialDiameter = -1f;
    private float numeralsTextPx;
    private float numeralsMetaPx;
    private int laidOutWidth = -1, laidOutHeight = -1;
    @Nullable private String laidOutRun;

    private final StationDial.TextMeasure libronMeasure = new StationDial.TextMeasure() {
        @Override public float widthPx(@NonNull String text, float sizePx) {
            paint.setTypeface(libron());
            paint.setTextSize(sizePx);
            paint.setFontFeatureSettings(null);
            return paint.measureText(text);
        }

        @Override public float figureHeightPx(float sizePx) { return figureRatio * sizePx; }
    };

    ClockFaceView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        libron = ClockTypefaces.libronBold(context);
        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        ink = style.onSurface;
        softInk = style.onSurfaceVariant;
        ground = style.card;
        paint.setTypeface(libron);
        paint.setTextSize(100f);
        paint.getTextBounds("0123456789", 0, 10, bounds);
        figureRatio = bounds.height() > 0 ? bounds.height() / 100f : 0.7f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @NonNull private Typeface libron() { return libron; }

    /** What the face is and how it reads; {@code oneByOne} sets the 1×1's dial and centring. */
    void configure(@NonNull ClockFace face, boolean medium, boolean seconds, boolean is24,
                   boolean oneByOne, boolean lazy, @NonNull ZonedDateTime now) {
        this.face = face;
        this.medium = medium;
        this.seconds = seconds;
        this.is24 = is24;
        this.oneByOne = oneByOne;
        this.now = now;
        dial = null;
        dialDiameter = -1f;
        laidOutWidth = laidOutHeight = -1;
        shedWidth = shedHeight = -1f;
        keptSeconds = seconds;
        keptPeriod = true;
        if (face.isBarFace()) {
            if (renderer == null) renderer = ClockFaceRenderer.forWidget(getResources());
            renderer.setStyle(face.rendererStyle(medium));
            renderer.setSeconds(seconds);
            renderer.setPeriodShown(true);
            renderer.setUseAmPm(!is24);
            renderer.setAlignment(oneByOne
                ? com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER
                : com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT);
            renderer.setLazyMode(lazy);
            renderer.setPalette(palette());
            renderer.setBandInk(ground, ink);
            renderer.updateTime(now.toInstant().toEpochMilli(), 0L);
        } else {
            renderer = null;
        }
        invalidate();
    }

    /** The opaque colour the face stands on, as the card measured it: its ink is toned on it. */
    void setGround(@ColorInt int surface) {
        if (surface == ground) return;
        ground = surface;
        if (renderer != null && renderer.setBandInk(surface, ink)) invalidate();
    }

    /** The time moved: on the minute, or on the second while Seconds is on. */
    void setTime(@NonNull ZonedDateTime next) {
        now = next;
        if (renderer != null) {
            renderer.updateTime(next.toInstant().toEpochMilli(), SystemClock.uptimeMillis());
        }
        invalidate();
    }

    @NonNull private ClockFaceRenderer.Palette palette() {
        return new ClockFaceRenderer.Palette(style.onSurface, style.onSurfaceVariant,
            style.primary, style.card, style.container, style.containerHigh,
            style.containerHigh, style.outlineVariant, style.onSurfaceVariant);
    }

    // ----- fit --------------------------------------------------------------------------------

    /** How tall the face paints at the largest size that fits {@code widthPx}×{@code maxHeightPx}. */
    float heightPxFor(float widthPx, float maxHeightPx) {
        if (widthPx <= 0f || maxHeightPx <= 0f) return 0f;
        shedFor(widthPx, maxHeightPx);
        switch (face) {
            case ANALOG:
                return Math.min(widthPx, maxHeightPx);
            case NUMERALS:
                return figureRatio * numeralsSize(widthPx, maxHeightPx);
            default:
                return renderer == null ? 0f
                    : Math.min(maxHeightPx, renderer.fittedColumnPx(widthPx, maxHeightPx));
        }
    }

    /** The Libron numerals' time size that fits the box, its seconds and period beside it. */
    private float numeralsSize(float widthPx, float heightPx) {
        String time = numeralsTime();
        String ss = numeralsSeconds();
        String period = numeralsPeriod();
        float low = 0f, high = heightPx / Math.max(0.01f, figureRatio);
        if (numeralsWidth(time, ss, period, high) <= widthPx) return high;
        for (int i = 0; i < 22; i++) {
            float middle = (low + high) / 2f;
            if (numeralsWidth(time, ss, period, middle) <= widthPx) low = middle;
            else high = middle;
        }
        return low;
    }

    /**
     * The numerals' run as painted: the time, then the gap and the meta. The last glyph counts to
     * its ink where that passes its advance (Libron's serifs can), so a kept period is never
     * shaved at the box's edge.
     */
    private float numeralsWidth(@NonNull String time, @NonNull String ss, @NonNull String period,
                                float sizePx) {
        if (ss.isEmpty() && period.isEmpty()) return runWidth(time, sizePx);
        return libronMeasure.widthPx(time, sizePx) + sizePx * NUMERALS_META_GAP
            + columnWidth(ss, period, metaPx(sizePx));
    }

    /** The meta column's width: the seconds, or the period over them where that is wider. */
    private float columnWidth(@NonNull String ss, @NonNull String period, float metaPx) {
        float width = ss.isEmpty() ? 0f : runWidth(ss, metaPx);
        if (!period.isEmpty()) width = Math.max(width, runWidth(period, metaPx * NUMERALS_PERIOD_SHARE));
        return width;
    }

    /** How far the stacked column rises above the baseline: the seconds, a gap, then the period. */
    private float columnRise(@NonNull String ss, @NonNull String period, float metaPx) {
        float rise = ss.isEmpty() ? 0f : figureRatio * metaPx;
        if (period.isEmpty()) return rise;
        float periodPx = metaPx * NUMERALS_PERIOD_SHARE;
        return rise + (ss.isEmpty() ? 0f : figureRatio * metaPx * NUMERALS_STACK_GAP)
            + figureRatio * periodPx;
    }

    /** A Libron run's advance, or its ink's right edge where that is further. */
    private float runWidth(@NonNull String text, float sizePx) {
        float advance = libronMeasure.widthPx(text, sizePx);
        paint.getTextBounds(text, 0, text.length(), bounds);
        return Math.max(advance, bounds.right);
    }

    /** The numerals face's seconds and period size for a time {@code sizePx} tall: Label at least. */
    private float metaPx(float sizePx) {
        return Math.max(ClockFaceRenderer.labelRoleSp() * scaledDensity,
            sizePx * NUMERALS_META_SHARE);
    }

    @NonNull private String numeralsTime() {
        return ClockWidgetFormats.hoursMinutes(now.toLocalTime(), is24);
    }

    @NonNull private String numeralsSeconds() {
        return keptSeconds ? ClockWidgetFormats.seconds(now.toLocalTime()) : "";
    }

    @NonNull private String numeralsPeriod() {
        return keptPeriod ? ClockWidgetFormats.amPm(now.toLocalTime(), is24) : "";
    }

    /**
     * The seconds and a 12-hour period run at the Label role whatever the face's size, so a box
     * too narrow for them at that floor sheds them, the seconds first, then the period (the digits
     * stay 12-hour): what is kept must fit and leave the digits at least {@link #META_KEEP} of the
     * size they have alone. Decided per box size, never per frame.
     */
    private void shedFor(float widthPx, float heightPx) {
        if (widthPx == shedWidth && heightPx == shedHeight && shedFace == face) return;
        shedWidth = widthPx;
        shedHeight = heightPx;
        shedFace = face;
        boolean twelve = !is24;
        keep(false, false);
        float bare = digitsScale(widthPx, heightPx);
        boolean[][] tries = {{seconds, twelve}, {false, twelve}};
        for (boolean[] wanted : tries) {
            if (!wanted[0] && !wanted[1]) break;
            keep(wanted[0], wanted[1]);
            if (metaFits(widthPx, heightPx, bare)) return;
        }
        keep(false, false);
    }

    /** What the digits are drawn at in the box with the meta kept now: a size or a scale. */
    private float digitsScale(float widthPx, float heightPx) {
        if (face == ClockFace.NUMERALS) return numeralsSize(widthPx, heightPx);
        return renderer == null ? 0f : renderer.fittedScale(widthPx, heightPx);
    }

    private boolean metaFits(float widthPx, float heightPx, float bare) {
        float with = digitsScale(widthPx, heightPx);
        if (with < bare * META_KEEP) return false;
        if (face == ClockFace.NUMERALS) {
            String ss = numeralsSeconds();
            String period = numeralsPeriod();
            return numeralsWidth(numeralsTime(), ss, period, with) <= widthPx + 0.5f
                && columnRise(ss, period, metaPx(with)) <= figureRatio * with + 0.5f;
        }
        return renderer == null || renderer.fittedWidthPx(widthPx, heightPx) <= widthPx + 0.5f;
    }

    private void keep(boolean keepSeconds, boolean keepPeriod) {
        keptSeconds = keepSeconds;
        keptPeriod = keepPeriod;
        if (renderer != null) {
            renderer.setSeconds(keepSeconds);
            renderer.setPeriodShown(keepPeriod);
        }
    }

    // ----- drawing ----------------------------------------------------------------------------

    /** A new box: what it keeps of the seconds and the period is decided here, at layout. */
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (face != ClockFace.ANALOG && w > 0 && h > 0) shedFor(w, h);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        if (face != ClockFace.ANALOG) shedFor(width, height);
        switch (face) {
            case ANALOG:
                drawDial(canvas, width, height);
                break;
            case NUMERALS:
                drawNumerals(canvas, width, height);
                break;
            default:
                drawRendered(canvas, width, height);
                break;
        }
    }

    private void drawRendered(@NonNull Canvas canvas, int width, int height) {
        ClockFaceRenderer current = renderer;
        if (current == null) return;
        current.setFitBudget(width, height, false);
        long frame = SystemClock.uptimeMillis();
        current.draw(canvas, width, height, frame);
        if (current.hasRunningAnimation(frame)) postInvalidateOnAnimation();
    }

    private void drawNumerals(@NonNull Canvas canvas, int width, int height) {
        String time = numeralsTime();
        String ss = numeralsSeconds();
        String period = numeralsPeriod();
        // Sized again only when the box or the text's run changes ("9:59" to "10:00").
        String run0 = time + '|' + ss + '|' + period;
        if (width != laidOutWidth || height != laidOutHeight || !run0.equals(laidOutRun)) {
            numeralsTextPx = numeralsSize(width, height);
            numeralsMetaPx = metaPx(numeralsTextPx);
            laidOutWidth = width;
            laidOutHeight = height;
            laidOutRun = run0;
        }
        float size = numeralsTextPx;
        float run = numeralsWidth(time, ss, period, size);
        float x = oneByOne ? Math.max(0f, (width - run) / 2f) : 0f;
        float baseline = height / 2f + figureRatio * size / 2f;
        paint.setTypeface(libron);
        // Measured as drawn: proportional figures, sized again whenever the run changes.
        paint.setFontFeatureSettings(null);
        paint.setTextSize(size);
        paint.setColor(ink);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(time, x, baseline, paint);
        if (ss.isEmpty() && period.isEmpty()) return;
        float metaX = x + paint.measureText(time) + size * NUMERALS_META_GAP;
        paint.setTextSize(numeralsMetaPx);
        paint.setColor(softInk);
        if (!ss.isEmpty()) canvas.drawText(ss, metaX, baseline, paint);
        if (period.isEmpty()) return;
        // The period stands over the seconds, or on their row when they are shed.
        float periodPx = numeralsMetaPx * NUMERALS_PERIOD_SHARE;
        float periodBaseline = ss.isEmpty() ? baseline
            : baseline - figureRatio * numeralsMetaPx * (1f + NUMERALS_STACK_GAP);
        paint.setTextSize(periodPx);
        canvas.drawText(period, metaX, periodBaseline, paint);
    }

    private void drawDial(@NonNull Canvas canvas, int width, int height) {
        float diameter = Math.min(width, height);
        if (dial == null || diameter != dialDiameter) {
            dial = StationDial.layout(diameter, density, scaledDensity, oneByOne, libronMeasure);
            dialDiameter = diameter;
        }
        StationDial.Geometry g = dial;
        float cx = width / 2f, cy = height / 2f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ink);
        // The minute track: sixty marks, every fifth a bold bar.
        for (int i = 0; i < 60; i++) {
            boolean major = i % 5 == 0;
            float w = major ? g.majorWidth : g.minorWidth;
            float l = major ? g.majorLength : g.minorLength;
            canvas.save();
            canvas.rotate(i * 6f, cx, cy);
            canvas.drawRect(cx - w / 2f, cy - g.trackOuter, cx + w / 2f, cy - g.trackOuter + l, fill);
            canvas.restore();
        }
        paint.setTypeface(libron);
        paint.setFontFeatureSettings(null);
        paint.setTextSize(g.numeralPx);
        paint.setColor(ink);
        paint.setTextAlign(Paint.Align.CENTER);
        for (StationDial.Numeral numeral : g.numerals) {
            canvas.drawText(numeral.text, cx + numeral.cx, cy + numeral.cy + numeral.height / 2f,
                paint);
        }
        int hour = now.getHour(), minute = now.getMinute(), second = now.getSecond();
        hand(canvas, cx, cy, StationDial.hourAngle(hour, minute, seconds ? second : 0),
            g.hourWidth, g.hourLength, g.hourTail, ink);
        hand(canvas, cx, cy, StationDial.minuteAngle(minute, seconds ? second : 0),
            g.minuteWidth, g.minuteLength, g.minuteTail, ink);
        fill.setColor(ink);
        canvas.drawCircle(cx, cy, g.hub, fill);
        if (!seconds) return;
        int red = style.dark ? SECONDS_RED_DARK : SECONDS_RED_LIGHT;
        hand(canvas, cx, cy, StationDial.secondAngle(second), g.secondWidth, g.secondLength,
            g.secondTail, red);
        canvas.save();
        canvas.rotate(StationDial.secondAngle(second), cx, cy);
        fill.setColor(red);
        canvas.drawCircle(cx, cy - g.secondLength, g.secondTip, fill);
        canvas.restore();
        canvas.drawCircle(cx, cy, g.secondHub, fill);
    }

    /** A squared bar hand: {@code length} out from the centre, {@code tail} back past it. */
    private void hand(@NonNull Canvas canvas, float cx, float cy, float degrees, float width,
                      float length, float tail, @ColorInt int color) {
        canvas.save();
        canvas.rotate(degrees, cx, cy);
        fill.setColor(color);
        canvas.drawRect(cx - width / 2f, cy - length, cx + width / 2f, cy + tail, fill);
        canvas.restore();
    }

    // ----- for tests ------------------------------------------------------------------------

    @NonNull ClockFace face() { return face; }
    boolean showsSeconds() { return seconds; }
    boolean is24Hour() { return is24; }

    /** The dial as last drawn, or laid out now at this view's size. */
    @VisibleForTesting @NonNull StationDial.Geometry dialGeometry() {
        return StationDial.layout(Math.min(getWidth(), getHeight()), density, scaledDensity,
            oneByOne, libronMeasure);
    }

    /** The numerals face's time and seconds sizes at this view's size, in px. */
    @VisibleForTesting float[] numeralsSizesPx() {
        shedFor(getWidth(), getHeight());
        float size = numeralsSize(getWidth(), getHeight());
        return new float[] {size, metaPx(size), numeralsWidth(numeralsTime(), numeralsSeconds(), numeralsPeriod(), size),
            figureRatio * size};
    }

    @VisibleForTesting @Nullable ClockFaceRenderer renderer() { return renderer; }

    /** The face's main ink, for tests. */
    @VisibleForTesting @ColorInt int inkForTests() { return ink; }

    /** The surface the face's ink is toned on, for tests. */
    @VisibleForTesting @ColorInt int groundForTests() { return ground; }
}
