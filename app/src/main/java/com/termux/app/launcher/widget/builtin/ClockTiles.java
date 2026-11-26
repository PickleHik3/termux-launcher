package com.termux.app.launcher.widget.builtin;

import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.ClockFaceRenderer;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Draws the Clock's Flip face as glass tiles ({@link ClockTilesPlan}): each pair of digits in
 * Libron Bold, tabular and centred on its own ink, across its tile's seam and notches; the third
 * tile's period and seconds. A pair that changes folds as the bar's flip cards do, on
 * {@link ClockFaceRenderer#FLIP_DURATION_MS} and its easing ({@link ClockFaceRenderer#foldTopDegrees},
 * {@link ClockFaceRenderer#foldBottomDegrees}); the seconds and the 1×1's stacked pairs change in
 * place, and lazy mode changes everything in place.
 *
 * <p>The tiles' ground is the widget card's own, cut to the tiles ({@link Dress#HOSTED}); a gear
 * chip, which has no card, draws its tiles itself ({@link Dress#OWN}); Bare draws neither, only
 * the type ({@link Dress#BARE}). Not a view: {@link ClockFaceView} hosts it. Nothing here
 * allocates on a draw.</p>
 */
final class ClockTiles {
    /** What stands under the type. */
    enum Dress {
        /** The widget's card is cut to the tiles: the seams and notches are drawn here. */
        HOSTED,
        /** No card: the tiles' fill, seams and notches are all drawn here (a gear chip). */
        OWN,
        /** Bare: the type alone, on the wallpaper. */
        BARE
    }

    /** The seam's dark hairline over its light one, and a notch: the design's colours. */
    @ColorInt static final int SEAM_DARK = 0x61000000;
    @ColorInt static final int SEAM_LIGHT = 0x24FFFFFF;
    @ColorInt static final int NOTCH = 0x8C140A07;
    /** The type's soft shadow on a dark tile: 2 px down, 10 px of blur, a quarter black. */
    @ColorInt static final int SHADOW = 0x40000000;
    static final float SHADOW_DROP_DP = 2f;
    static final float SHADOW_BLUR_DP = 5f;
    /** The digits' tracking, and the period's (em). */
    static final float DIGIT_TRACKING = -.02f;
    static final float PERIOD_TRACKING = .06f;

    private static final int HOURS = 0, MINUTES = 1;

    @NonNull private final BuiltinWidgetStyle style;
    private final float density;
    private final float labelPx;
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint period = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final RectF rect = new RectF();
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    /** Libron's widest digit's advance per px of size, and its figure height per px. */
    private final float widestDigitPerPx;
    private final float figurePerPx;
    @NonNull private final String am, pm;

    @NonNull private BuiltinWidgetSpan span = BuiltinWidgetSpan.TWO_BY_ONE;
    private boolean is24 = true;
    private boolean seconds;
    private boolean lazy;
    @NonNull private Dress dress = Dress.OWN;
    /** The widget's scale against the design, or below 0 to take it from the box's height. */
    private float scale = -1f;
    private float lookRadiusPx;

    /** The hour's and the minute's pairs now and before their last change, and when that was. */
    @NonNull private final String[] pairs = {"", ""};
    @NonNull private final String[] oldPairs = {"", ""};
    private final long[] changedAt = new long[2];
    @NonNull private String secondsText = "";
    @NonNull private String periodText = "";

    @Nullable private ClockTilesPlan plan;
    private int planWidth = -1, planHeight = -1;

    ClockTiles(@NonNull BuiltinWidgetStyle style, @NonNull Typeface libron, float density,
               float scaledDensity) {
        this.style = style;
        this.density = density;
        labelPx = ClockFaceRenderer.labelRoleSp() * scaledDensity;
        digits.setTypeface(libron);
        digits.setFontFeatureSettings("'tnum'");
        digits.setLetterSpacing(DIGIT_TRACKING);
        period.setTypeface(libron);
        period.setLetterSpacing(PERIOD_TRACKING);
        digits.setTextSize(100f);
        float widest = 0f;
        for (char c = '0'; c <= '9'; c++) {
            widest = Math.max(widest, digits.measureText(String.valueOf(c)));
        }
        widestDigitPerPx = widest / 100f;
        digits.getTextBounds("0123456789", 0, 10, bounds);
        figurePerPx = bounds.height() > 0 ? bounds.height() / 100f : .7f;
        am = ClockWidgetFormats.amPm(LocalTime.of(1, 0), false);
        pm = ClockWidgetFormats.amPm(LocalTime.of(13, 0), false);
        if (style.dark) {
            digits.setShadowLayer(SHADOW_BLUR_DP * density, 0f, SHADOW_DROP_DP * density, SHADOW);
            period.setShadowLayer(SHADOW_BLUR_DP * density, 0f, SHADOW_DROP_DP * density, SHADOW);
        }
    }

    private final ClockTilesPlan.Measure measure = new ClockTilesPlan.Measure() {
        @Override public float pairWidthPx(float sizePx) {
            return 2f * widestDigitPerPx * sizePx;
        }

        @Override public float figureHeightPx(float sizePx) { return figurePerPx * sizePx; }

        @Override public float periodWidthPx(float sizePx) {
            period.setTextSize(sizePx);
            // Tracking trails the last letter too; the ink ends before it.
            float trail = PERIOD_TRACKING * sizePx;
            return Math.max(period.measureText(am), period.measureText(pm)) - trail;
        }
    };

    /** What the tiles show; the plan is made again at the next layout. */
    void configure(@NonNull BuiltinWidgetSpan span, boolean is24, boolean seconds, boolean lazy,
                   @NonNull ZonedDateTime now) {
        this.span = span;
        this.is24 = is24;
        this.seconds = seconds;
        this.lazy = lazy;
        forgetPlan();
        take(now, 0L, true);
    }

    /** The host's dress, the widget's scale and the Look's corner; the plan is made again. */
    void setHost(@NonNull Dress dress, float scale, float lookRadiusPx) {
        if (dress == this.dress && scale == this.scale && lookRadiusPx == this.lookRadiusPx) {
            return;
        }
        this.dress = dress;
        this.scale = scale;
        this.lookRadiusPx = lookRadiusPx;
        forgetPlan();
    }

    /** Whether the tiles show seconds, so the widget ticks them. */
    boolean showsSeconds() { return ClockTilesPlan.showsSeconds(span, seconds); }

    /** The time moved: a pair that changed folds from {@code animationTime}. */
    void setTime(@NonNull ZonedDateTime now, long animationTime) {
        take(now, animationTime, false);
    }

    private void take(@NonNull ZonedDateTime now, long animationTime, boolean reset) {
        LocalTime time = now.toLocalTime();
        int hour = is24 ? time.getHour() : (time.getHour() % 12 == 0 ? 12 : time.getHour() % 12);
        String[] next = {twoDigits(hour), twoDigits(time.getMinute())};
        for (int i = 0; i < pairs.length; i++) {
            if (reset) {
                pairs[i] = next[i];
                oldPairs[i] = next[i];
                changedAt[i] = 0L;
            } else if (!next[i].equals(pairs[i])) {
                oldPairs[i] = pairs[i];
                pairs[i] = next[i];
                changedAt[i] = animationTime;
            }
        }
        secondsText = ClockWidgetFormats.seconds(time);
        periodText = ClockWidgetFormats.amPm(time, is24);
    }

    @NonNull private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private void forgetPlan() {
        plan = null;
        planWidth = planHeight = -1;
    }

    /** The tiles at {@code width}×{@code height}, made once per size and setting. */
    @NonNull ClockTilesPlan plan(int width, int height) {
        ClockTilesPlan current = plan;
        if (current != null && width == planWidth && height == planHeight) return current;
        float s = scale >= 0f ? scale : ClockTilesPlan.scale(height / density);
        current = ClockTilesPlan.plan(width, height, density, s, labelPx, lookRadiusPx, span,
            is24, seconds, measure);
        plan = current;
        planWidth = width;
        planHeight = height;
        return current;
    }

    /** Whether a pair is still folding at {@code now}. */
    boolean hasRunningAnimation(long now) {
        if (lazy || span == BuiltinWidgetSpan.ONE_BY_ONE) return false;
        for (long at : changedAt) {
            if (at > 0L && now - at < ClockFaceRenderer.FLIP_DURATION_MS) return true;
        }
        return false;
    }

    private float progress(int pair, long now) {
        if (lazy || changedAt[pair] <= 0L) return 1f;
        return Math.max(0f, Math.min(1f,
            (now - changedAt[pair]) / (float) ClockFaceRenderer.FLIP_DURATION_MS));
    }

    // ----- drawing --------------------------------------------------------------------------

    void draw(@NonNull Canvas canvas, int width, int height, long now) {
        if (width <= 0 || height <= 0) return;
        ClockTilesPlan p = plan(width, height);
        for (ClockTilesPlan.Tile tile : p.tiles) {
            if (dress == Dress.OWN) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(style.container);
                rect.set(tile.left, tile.top, tile.right, tile.bottom);
                canvas.drawRoundRect(rect, tile.radius, tile.radius, fill);
            }
            switch (tile.shows) {
                case HOURS:
                    drawPair(canvas, tile, HOURS, now);
                    break;
                case MINUTES:
                    drawPair(canvas, tile, MINUTES, now);
                    break;
                case STACK:
                    setDigits(tile.digitPx, style.onSurface);
                    drawCentred(canvas, digits, pairs[HOURS], tile.centerX(),
                        tile.top + tile.height() / 4f);
                    drawCentred(canvas, digits, pairs[MINUTES], tile.centerX(),
                        tile.top + tile.height() * 3f / 4f);
                    break;
                case SIDE:
                    drawSide(canvas, p, tile);
                    break;
            }
            if (dress != Dress.BARE) drawSeam(canvas, tile);
        }
        if (p.periodInCorner && !periodText.isEmpty()) {
            setPeriod(p.periodPx);
            period.getTextBounds(periodText, 0, periodText.length(), bounds);
            canvas.drawText(periodText, p.periodX - bounds.left, p.periodY - bounds.top, period);
        }
    }

    private void setDigits(float sizePx, @ColorInt int color) {
        digits.setTextSize(sizePx);
        digits.setColor(color);
    }

    private void setPeriod(float sizePx) {
        period.setTextSize(sizePx);
        period.setColor(style.primary);
    }

    /** {@code text} with its ink centred on ({@code cx}, {@code cy}). */
    private void drawCentred(@NonNull Canvas canvas, @NonNull Paint paint, @NonNull String text,
                             float cx, float cy) {
        if (text.isEmpty()) return;
        paint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText(text, cx - bounds.exactCenterX(), cy - bounds.exactCenterY(), paint);
    }

    /**
     * A pair across its seam; while it folds, the old top leaf falls over the new top, then the
     * new bottom leaf lands over the old bottom, each half clipped at the seam.
     */
    private void drawPair(@NonNull Canvas canvas, @NonNull ClockTilesPlan.Tile tile, int pair,
                          long now) {
        setDigits(tile.digitPx, style.onSurface);
        float cx = tile.centerX(), seam = tile.seamY();
        float p = progress(pair, now);
        if (p >= 1f) {
            drawCentred(canvas, digits, pairs[pair], cx, seam);
            return;
        }
        float half = tile.height() / 2f;
        if (p < .5f) {
            float degrees = ClockFaceRenderer.foldTopDegrees(p);
            float leafTop = seam - half * (float) Math.cos(Math.toRadians(degrees));
            drawHalf(canvas, tile, pairs[pair], tile.top, leafTop);
            drawHalf(canvas, tile, oldPairs[pair], seam, tile.bottom);
            drawLeaf(canvas, tile, oldPairs[pair], true, degrees);
        } else {
            float degrees = ClockFaceRenderer.foldBottomDegrees(p);
            float leafBottom = seam + half * (float) Math.cos(Math.toRadians(degrees));
            drawHalf(canvas, tile, pairs[pair], tile.top, seam);
            drawHalf(canvas, tile, oldPairs[pair], leafBottom, tile.bottom);
            drawLeaf(canvas, tile, pairs[pair], false, degrees);
        }
    }

    private void drawHalf(@NonNull Canvas canvas, @NonNull ClockTilesPlan.Tile tile,
                          @NonNull String text, float top, float bottom) {
        if (bottom <= top) return;
        int save = canvas.save();
        canvas.clipRect(tile.left, top, tile.right, bottom);
        drawCentred(canvas, digits, text, tile.centerX(), tile.seamY());
        canvas.restoreToCount(save);
    }

    /** One half of {@code text} turned {@code degrees} about the seam, as the bar's leaves turn. */
    private void drawLeaf(@NonNull Canvas canvas, @NonNull ClockTilesPlan.Tile tile,
                          @NonNull String text, boolean top, float degrees) {
        float cx = tile.centerX(), seam = tile.seamY();
        camera.save();
        camera.rotateX(degrees);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-cx, -seam);
        matrix.postTranslate(cx, seam);
        int save = canvas.save();
        canvas.concat(matrix);
        canvas.clipRect(tile.left, top ? tile.top : seam, tile.right, top ? seam : tile.bottom);
        drawCentred(canvas, digits, text, cx, seam);
        canvas.restoreToCount(save);
    }

    /** The period over the seam and the seconds under it, or the seconds alone across it. */
    private void drawSide(@NonNull Canvas canvas, @NonNull ClockTilesPlan p,
                          @NonNull ClockTilesPlan.Tile tile) {
        if (p.periodOnSide && !periodText.isEmpty()) {
            setPeriod(p.periodPx);
            // The tracking trails the last letter: centred on the ink, not the advance.
            drawCentred(canvas, period, periodText, p.periodX, p.periodY);
        }
        if (p.seconds) {
            setDigits(p.secondsPx, style.onSurface);
            digits.setAlpha(Math.round(android.graphics.Color.alpha(style.onSurface)
                * ClockTilesPlan.SECONDS_INK));
            drawCentred(canvas, digits, secondsText, tile.centerX(), p.secondsCenterY);
        }
    }

    /** The seam's two hairlines across the tile, and a notch at each end of it. */
    private void drawSeam(@NonNull Canvas canvas, @NonNull ClockTilesPlan.Tile tile) {
        float line = ClockTilesPlan.SEAM_LINE_DP * density;
        float seam = tile.seamY();
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(SEAM_DARK);
        canvas.drawRect(tile.left, seam - line, tile.right, seam, fill);
        fill.setColor(SEAM_LIGHT);
        canvas.drawRect(tile.left, seam, tile.right, seam + line, fill);
        float w = tile.notchWidth, h = tile.notchHeight / 2f;
        fill.setColor(NOTCH);
        // Square on the tile's edge, rounded on the inner side, as the design's are: the inner
        // half of an oval centred on the edge.
        rect.set(tile.left - w, seam - h, tile.left + w, seam + h);
        canvas.drawArc(rect, -90f, 180f, true, fill);
        rect.set(tile.right - w, seam - h, tile.right + w, seam + h);
        canvas.drawArc(rect, 90f, 180f, true, fill);
    }
}
