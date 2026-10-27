package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * The 1×1 Battery ring: a disc filled clockwise from twelve o'clock to the level, the rest in the
 * high container, with the inner disc punched out in the ring colour and the level drawn inside
 * it, under the bolt while charging.
 *
 * <p>The ring takes all the room its parent gives it, square. The number is sized to the inner
 * disc ({@link OneByOneFit#ringNumberSize}), never to the card or the font scale, so it stays
 * inside the ring at any size. It is fitted as "100" with the bolt above whatever it shows, so
 * the number keeps one size from 9 to 100 and when the charger comes and goes.</p>
 */
public final class BatteryRingView extends View {
    /** The inner disc's diameter as a share of the ring's: 56 of 68 in the design. */
    static final float INNER = 56f / 68f;
    /** The bolt's text size as a share of the number's: 13sp over 17sp in the design. */
    private static final float MARK_SHARE = 13f / 17f;
    /** The gap between the bolt and the number as a share of the number's text size. */
    private static final float GAP_SHARE = 1f / 17f;
    /** The widest reading the number is fitted to. */
    private static final String WIDEST = "100";
    /** The text size the ink is measured at; the fit scales from it. */
    private static final float PROBE_PX = 100f;

    @NonNull private final BuiltinWidgetStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Rect ink = new Rect();
    private final Rect markInk = new Rect();
    private float fraction;
    @ColorInt private int fill;
    @NonNull private String label = "";
    private boolean bolt;
    /** The size the number was last fitted for, or -1 when it must be fitted again. */
    private float fittedSide = -1f;

    public BatteryRingView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.style = style;
        fill = style.primary;
        number.setTypeface(style.numerals);
        number.setColor(style.onSurface);
        if (style.nerd != null) mark.setTypeface(style.nerd);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The level as a share of full, the arc's colour, the number and whether the bolt shows. */
    public void set(float fraction, @ColorInt int fill, @NonNull String label, boolean bolt) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        this.fill = fill;
        if (!label.equals(this.label)) fittedSide = -1f;
        this.label = label;
        this.bolt = bolt;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = style.dp(BuiltinWidgetSpan.CELL_WIDTH_DP - 2 * OneByOneFit.INSET_DP);
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ? desired
            : MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ? desired
            : MeasureSpec.getSize(heightMeasureSpec);
        int side = OneByOneFit.artSide(width, height, 0);
        setMeasuredDimension(side, side);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = size / 2f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        paint.setColor(style.containerHigh);
        canvas.drawOval(oval, paint);
        if (fraction > 0f) {
            paint.setColor(fill);
            canvas.drawArc(oval, -90f, 360f * fraction, true, paint);
        }
        paint.setColor(style.ringInner);
        canvas.drawCircle(cx, cy, r * INNER, paint);
        drawStack(canvas, cx, cy, size);
    }

    private void drawStack(@NonNull Canvas canvas, float cx, float cy, float size) {
        if (label.isEmpty()) return;
        fit(size);
        number.getTextBounds(label, 0, label.length(), ink);
        float height = ink.height();
        if (bolt) {
            mark.getTextBounds(StatGlyphs.POWER, 0, StatGlyphs.POWER.length(), markInk);
            height += number.getTextSize() * GAP_SHARE + markInk.height();
        }
        float top = cy - height / 2f;
        if (bolt) {
            mark.setColor(fill);
            canvas.drawText(StatGlyphs.POWER, cx - markInk.exactCenterX(), top - markInk.top, mark);
            top += markInk.height() + number.getTextSize() * GAP_SHARE;
        }
        canvas.drawText(label, cx - ink.exactCenterX(), top - ink.top, number);
    }

    /** Sizes the number and the bolt for a ring {@code size} across; once per size and label. */
    private void fit(float size) {
        if (size == fittedSide) return;
        fittedSide = size;
        number.setTextSize(PROBE_PX);
        mark.setTextSize(PROBE_PX * MARK_SHARE);
        number.getTextBounds(WIDEST, 0, WIDEST.length(), ink);
        float numberWidth = ink.width(), numberHeight = ink.height();
        number.getTextBounds(label, 0, label.length(), ink);
        numberWidth = Math.max(numberWidth, ink.width());
        numberHeight = Math.max(numberHeight, ink.height());
        mark.getTextBounds(StatGlyphs.POWER, 0, StatGlyphs.POWER.length(), markInk);
        float px = OneByOneFit.ringNumberSize(size * INNER, numberWidth / PROBE_PX,
            numberHeight / PROBE_PX, markInk.width() / PROBE_PX, markInk.height() / PROBE_PX,
            GAP_SHARE);
        number.setTextSize(Math.max(1f, px));
        mark.setTextSize(Math.max(1f, px * MARK_SHARE));
    }

    /**
     * The ink the number and the bolt cover now, as a box centred on the ring: its width and
     * height in pixels. For tests; fits first, as a draw would.
     */
    @VisibleForTesting @NonNull RectF stackInk() {
        float size = Math.min(getWidth(), getHeight());
        RectF out = new RectF();
        if (size <= 0 || label.isEmpty()) return out;
        fit(size);
        number.getTextBounds(label, 0, label.length(), ink);
        float width = ink.width(), height = ink.height();
        if (bolt) {
            mark.getTextBounds(StatGlyphs.POWER, 0, StatGlyphs.POWER.length(), markInk);
            width = Math.max(width, markInk.width());
            height += number.getTextSize() * GAP_SHARE + markInk.height();
        }
        out.set(0f, 0f, width, height);
        return out;
    }

    @VisibleForTesting float numberTextSizePx() { return number.getTextSize(); }
}
