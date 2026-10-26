package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link WidgetLayoutPlan} and nothing else: the plan is asked for at the view's size
 * and its parts are drawn in the widget's style. Text is measured with the paints it is drawn
 * with, so the plan's boxes are what shows. Everything is resolved when the size or the plan
 * changes; a frame only replays it.
 */
final class WidgetPlanView extends View {
    /** Builds the plan for a card of the given size, measuring text with {@code measure}. */
    interface Planner {
        @NonNull WidgetLayoutPlan plan(float widthDp, float heightDp,
                                       @NonNull WidgetLayoutPlan.Measure measure);
    }

    /** The emphasised weight of the hero's figures. */
    private static final int HERO_WEIGHT = 600;
    /** The text size the hero's and the glyphs' metrics are probed at, in px. */
    private static final float PROBE_PX = 100f;

    @NonNull private final BuiltinWidgetStyle style;
    @NonNull private final Planner planner;
    private final float density;
    @NonNull private final Typeface heroFace;
    private final TextPaint probe = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds = new Rect();
    private final List<Op> ops = new ArrayList<>();
    @Nullable private WidgetLayoutPlan plan;

    private final WidgetLayoutPlan.Measure measure = (text, role, numeric, sizeDp) -> {
        dress(probe, role, numeric, sizeDp * density());
        return probe.measureText(text) / density();
    };

    WidgetPlanView(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                   @NonNull Planner planner) {
        super(context);
        this.style = style;
        this.planner = planner;
        density = context.getResources().getDisplayMetrics().density;
        heroFace = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? Typeface.create(Typeface.SANS_SERIF, HERO_WEIGHT, false)
            : Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    }

    private float density() { return density; }

    /** The plan last drawn, null before the first layout. */
    @Nullable WidgetLayoutPlan plan() { return plan; }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        replan(w, h);
    }

    private void replan(int widthPx, int heightPx) {
        ops.clear();
        plan = null;
        if (widthPx <= 0 || heightPx <= 0) return;
        WidgetLayoutPlan next = planner.plan(widthPx / density, heightPx / density, measure);
        plan = next;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float inner = WidgetTokens.innerRadius(style.cornerRadiusPx / density, next.inset.left)
            * density;
        for (Part part : next.parts) {
            Op op = op(part, rtl ? widthPx - (part.box.left + part.box.width) * density
                : part.box.left * density, inner);
            if (op != null) ops.add(op);
        }
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (Op op : ops) op.draw(canvas);
    }

    // ----- resolving ------------------------------------------------------------------------

    @Nullable private Op op(@NonNull Part part, float left, float innerRadius) {
        float top = part.box.top * density;
        float width = part.box.width * density;
        float height = part.box.height * density;
        switch (part.type) {
            case TEXT: {
                if (part.role == null || width <= 0f) return null;
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                dress(paint, part.role, part.numeric, part.sizeDp * density);
                paint.setColor(color(part.ink));
                String shown = TextUtils.ellipsize(part.text, paint, width + 0.5f,
                    TextUtils.TruncateAt.END).toString();
                float baseline;
                if (part.role == WidgetTokens.Role.HERO) {
                    // The figures' ink is centred in the hero's box, which is boxed to them.
                    paint.getTextBounds(shown, 0, shown.length(), bounds);
                    baseline = top + height / 2f - (bounds.top + bounds.bottom) / 2f;
                } else {
                    Paint.FontMetrics fm = paint.getFontMetrics();
                    baseline = top + (height - (fm.descent - fm.ascent)) / 2f - fm.ascent;
                }
                return new TextOp(shown, paint, left, baseline);
            }
            case GLYPH: {
                if (part.text.isEmpty() || width <= 0f) return null;
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                if (style.nerd != null) paint.setTypeface(style.nerd);
                paint.setTextSize(PROBE_PX);
                paint.getTextBounds(part.text, 0, part.text.length(), bounds);
                if (bounds.width() <= 0 || bounds.height() <= 0) return null;
                // The glyph's ink is fit to its square, centred.
                float scale = Math.min(width / bounds.width(), height / bounds.height());
                paint.setTextSize(PROBE_PX * scale);
                paint.getTextBounds(part.text, 0, part.text.length(), bounds);
                paint.setColor(color(part.ink));
                float x = left + width / 2f - (bounds.left + bounds.right) / 2f;
                float baseline = top + height / 2f - (bounds.top + bounds.bottom) / 2f;
                return new TextOp(part.text, paint, x, baseline);
            }
            case TRACK: {
                Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
                track.setColor(style.containerHigh);
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(style.warm);
                float radius = Math.min(height / 2f, innerRadius);
                RectF whole = new RectF(left, top, left + width, top + height);
                float start = Math.max(0f, Math.min(1f, part.start));
                float end = Math.max(start, Math.min(1f, part.end));
                boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                float from = rtl ? 1f - end : start;
                float to = rtl ? 1f - start : end;
                RectF filled = new RectF(left + width * from, top, left + width * to, top + height);
                return new TrackOp(whole, filled, radius, track, fill);
            }
            case RULE: {
                Paint paint = new Paint();
                paint.setColor(color(part.ink));
                return new RectOp(new RectF(left, top, left + Math.max(width, 1f),
                    top + Math.max(height, 1f)), paint);
            }
            case TOUCH:
            case FACE:
            default:
                return null;
        }
    }

    /**
     * Sets {@code paint} up for a role: the sans for words, the numerals with tabular figures (or
     * the terminal's mono under the Mono Numerals choice).
     */
    private void dress(@NonNull TextPaint paint, @NonNull WidgetTokens.Role role, boolean numeric,
                       float sizePx) {
        Typeface face;
        boolean mono = numeric && style.monoNumerals();
        if (role == WidgetTokens.Role.HERO) {
            face = mono ? style.monoMedium : heroFace;
        } else if (mono) {
            face = role.emphasised ? style.monoMedium : style.mono;
        } else if (role == WidgetTokens.Role.HEADLINE) {
            face = style.sansBold;
        } else {
            face = role.emphasised ? style.sansMedium : style.sans;
        }
        paint.setTypeface(face);
        paint.setFontFeatureSettings(numeric && !mono ? WidgetTokens.TABULAR_FIGURES : null);
        paint.setTextSize(sizePx);
    }

    @ColorInt private int color(@NonNull WidgetLayoutPlan.Ink ink) {
        switch (ink) {
            case STRONG: return style.onSurface;
            case SUN:
            case CAUTION: return style.warm;
            case MOON: return style.primary;
            case RULE: return style.outlineVariant;
            case SOFT:
            case CLOUD:
            default: return style.onSurfaceVariant;
        }
    }

    // ----- drawing --------------------------------------------------------------------------

    private interface Op { void draw(@NonNull Canvas canvas); }

    private static final class TextOp implements Op {
        @NonNull final String text;
        @NonNull final Paint paint;
        final float x, baseline;

        TextOp(@NonNull String text, @NonNull Paint paint, float x, float baseline) {
            this.text = text; this.paint = paint; this.x = x; this.baseline = baseline;
        }

        @Override public void draw(@NonNull Canvas canvas) { canvas.drawText(text, x, baseline, paint); }
    }

    private static final class TrackOp implements Op {
        @NonNull final RectF whole, filled;
        final float radius;
        @NonNull final Paint track, fill;

        TrackOp(@NonNull RectF whole, @NonNull RectF filled, float radius, @NonNull Paint track,
                @NonNull Paint fill) {
            this.whole = whole; this.filled = filled; this.radius = radius;
            this.track = track; this.fill = fill;
        }

        @Override public void draw(@NonNull Canvas canvas) {
            canvas.drawRoundRect(whole, radius, radius, track);
            if (filled.width() > 0f) canvas.drawRoundRect(filled, radius, radius, fill);
        }
    }

    private static final class RectOp implements Op {
        @NonNull final RectF rect;
        @NonNull final Paint paint;

        RectOp(@NonNull RectF rect, @NonNull Paint paint) { this.rect = rect; this.paint = paint; }

        @Override public void draw(@NonNull Canvas canvas) { canvas.drawRect(rect, paint); }
    }
}
