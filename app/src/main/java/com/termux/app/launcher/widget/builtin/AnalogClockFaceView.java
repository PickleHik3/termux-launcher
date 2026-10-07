package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The analog clock's face, drawn as the design draws it: every proportion is a share of the
 * face's diameter, so the 68dp face of a 2×1 card and the 160dp one of a 2×2 are the same
 * drawing. Tonal fills the face; Pane leaves it clear inside a hairline ring.
 *
 * <p>It wants its design size and settles for less when the card is smaller, always square.
 * Setting the time only invalidates; the face never relayouts once placed.</p>
 */
final class AnalogClockFaceView extends View {
    private final BuiltinWidgetStyle style;
    private final int desiredPx;
    /** The centre dot's diameter as a share of the face: 12%, or 8% on the large faces. */
    private final float dotShare;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    @Nullable private String labelText;
    private float hourAngle, minuteAngle, secondAngle;

    AnalogClockFaceView(@NonNull Context context, @NonNull BuiltinWidgetStyle style, int sizeDp,
                        float dotShare) {
        super(context);
        this.style = style;
        this.desiredPx = style.dp(sizeDp);
        this.dotShare = dotShare;
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(style.outlineVariant);
        ring.setStrokeWidth(Math.max(1f, style.density));
        label.setTypeface(style.monoMedium);
        label.setColor(style.onSurfaceVariant);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f,
            context.getResources().getDisplayMetrics()));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The small line below the centre ("TUE 06"); null for none. */
    void setLabel(@Nullable String text) {
        if (text == null ? labelText == null : text.equals(labelText)) return;
        labelText = text;
        invalidate();
    }

    void setTime(int hour, int minute, int second) {
        float h = ClockWidgetFormats.hourAngle(hour, minute, second);
        float m = ClockWidgetFormats.minuteAngle(minute, second);
        float s = ClockWidgetFormats.secondAngle(second);
        if (h == hourAngle && m == minuteAngle && s == secondAngle) return;
        hourAngle = h; minuteAngle = m; secondAngle = s;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = Math.min(resolveSize(desiredPx, widthMeasureSpec),
            resolveSize(desiredPx, heightMeasureSpec));
        setMeasuredDimension(side, side);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        float d = Math.min(getWidth(), getHeight());
        if (d <= 0f) return;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float top = cy - d / 2f;

        if (Color.alpha(style.face) != 0) {
            fill.setColor(style.face);
            canvas.drawCircle(cx, cy, d / 2f, fill);
        }
        if (style.isPane()) {
            float hair = ring.getStrokeWidth();
            canvas.drawCircle(cx, cy, d / 2f - hair / 2f, ring);
        }

        // Twelve marks 5% in from the rim; every third one longer, wider and darker.
        float corner = style.density;
        for (int i = 0; i < 12; i++) {
            boolean major = i % 3 == 0;
            float w = d * (major ? 0.025f : 0.015f);
            float h = d * (major ? 0.09f : 0.05f);
            canvas.save();
            canvas.rotate(i * 30f, cx, cy);
            fill.setColor(major ? style.onSurface : style.onSurfaceVariant);
            rect.set(cx - w / 2f, top + d * 0.05f, cx + w / 2f, top + d * 0.05f + h);
            float r = Math.min(corner, w / 2f);
            canvas.drawRoundRect(rect, r, r, fill);
            canvas.restore();
        }

        if (labelText != null && !labelText.isEmpty()) {
            Paint.FontMetrics metrics = label.getFontMetrics();
            canvas.drawText(labelText, cx, top + d * 0.62f - metrics.ascent, label);
        }

        // Hands as the design places them: a box from `top` to `bottom` of the face, turned
        // about the centre, so each runs a little past the middle.
        hand(canvas, hourAngle, 0.06f, 0.24f, 0.54f, true, style.onSurface, cx, cy, top, d);
        hand(canvas, minuteAngle, 0.04f, 0.10f, 0.54f, true, style.onSurface, cx, cy, top, d);
        hand(canvas, secondAngle, 0.015f, 0.08f, 0.60f, false, style.warm, cx, cy, top, d);

        fill.setColor(style.warm);
        canvas.drawCircle(cx, cy, d * dotShare / 2f, fill);
    }

    private void hand(@NonNull Canvas canvas, float angle, float widthShare, float fromShare,
                      float toShare, boolean rounded, int color, float cx, float cy, float top,
                      float d) {
        float w = d * widthShare;
        canvas.save();
        canvas.rotate(angle, cx, cy);
        fill.setColor(color);
        rect.set(cx - w / 2f, top + d * fromShare, cx + w / 2f, top + d * toShare);
        if (rounded) canvas.drawRoundRect(rect, w / 2f, w / 2f, fill);
        else canvas.drawRect(rect, fill);
        canvas.restore();
    }
}
