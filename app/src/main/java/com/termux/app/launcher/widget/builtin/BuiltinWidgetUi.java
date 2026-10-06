package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The handful of pieces every built-in widget is assembled from, so twelve widgets at five spans
 * share one vocabulary: a numeral, a caption, a mono label, a glyph, a bar, a row and a column.
 * Sizes are the design's dp values; text is in sp so a large font scale still applies, and every
 * label is single-line and ellipsised, because what clips on other phones is text, not geometry.
 */
public final class BuiltinWidgetUi {
    @NonNull public final Context context;
    @NonNull public final BuiltinWidgetStyle style;

    public BuiltinWidgetUi(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
        this.context = context; this.style = style;
    }

    public int dp(float value) { return style.dp(value); }

    // ----- text -----------------------------------------------------------------------------

    /** A big numeral in the direction's numeral weight. */
    @NonNull public TextView numeral(@NonNull CharSequence text, float sp) {
        return text(text, sp, style.numerals, style.onSurface);
    }

    /** A caption: the design's sans 11sp semibold in the variant colour (Tonal); mono in Pane. */
    @NonNull public TextView caption(@NonNull CharSequence text) {
        return style.isPane()
            ? text(text, 10.5f, style.monoMedium, style.onSurfaceVariant)
            : text(text, 11f, style.sansBold, style.onSurfaceVariant);
    }

    /**
     * The design's two caption forms in one call: Tonal shows {@code tonalText} as a sans caption;
     * Pane shows a Nerd {@code paneGlyph} in primary followed by {@code paneText} in mono, the way
     * a pane names its path.
     */
    @NonNull public View caption(@NonNull CharSequence tonalText, @NonNull String paneGlyph,
                                 @NonNull CharSequence paneText) {
        if (!style.isPane()) return caption(tonalText);
        TextView label = text(paneText, 10.5f, style.monoMedium, style.onSurfaceVariant);
        return row(5, glyph(paneGlyph, 10.5f, style.primary), flex(label));
    }

    /** A mono label in the variant colour, the design's secondary line. */
    @NonNull public TextView mono(@NonNull CharSequence text, float sp) {
        return text(text, sp, style.monoMedium, style.onSurfaceVariant);
    }

    /** A sans label on the surface colour. */
    @NonNull public TextView sans(@NonNull CharSequence text, float sp, boolean bold) {
        return text(text, sp, bold ? style.sansBold : style.sansMedium, style.onSurface);
    }

    @NonNull public TextView text(@NonNull CharSequence text, float sp, @NonNull Typeface face,
                                  @ColorInt int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(face);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    /** A Nerd Font glyph; falls back to the plain character when the symbol font is missing. */
    @NonNull public TextView glyph(@NonNull String glyph, float sp, @ColorInt int color) {
        TextView view = new TextView(context);
        view.setText(glyph);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (style.nerd != null) view.setTypeface(style.nerd);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    // ----- layout ---------------------------------------------------------------------------

    @NonNull public LinearLayout row(int gapDp, @NonNull View... children) {
        return box(LinearLayout.HORIZONTAL, gapDp, children);
    }

    @NonNull public LinearLayout column(int gapDp, @NonNull View... children) {
        return box(LinearLayout.VERTICAL, gapDp, children);
    }

    @NonNull private LinearLayout box(int orientation, int gapDp, @NonNull View... children) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(orientation);
        box.setGravity(orientation == LinearLayout.HORIZONTAL ? Gravity.CENTER_VERTICAL : Gravity.START);
        for (int i = 0; i < children.length; i++) {
            View child = children[i];
            ViewGroup.LayoutParams existing = child.getLayoutParams();
            LinearLayout.LayoutParams params = existing instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) existing
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                if (orientation == LinearLayout.HORIZONTAL) params.leftMargin = dp(gapDp);
                else params.topMargin = dp(gapDp);
            }
            box.addView(child, params);
        }
        return box;
    }

    /** Marks {@code view} to take the remaining room along its row or column. */
    @NonNull public static <V extends View> V flex(@NonNull V view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        view.setLayoutParams(params);
        return view;
    }

    /** Marks {@code view} to take the remaining height of its column. */
    @NonNull public static <V extends View> V flexTall(@NonNull V view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        view.setLayoutParams(params);
        return view;
    }

    @NonNull public static <V extends View> V size(@NonNull V view, int widthPx, int heightPx) {
        ViewGroup.LayoutParams existing = view.getLayoutParams();
        if (existing instanceof LinearLayout.LayoutParams) {
            existing.width = widthPx; existing.height = heightPx;
            view.setLayoutParams(existing);
        } else {
            view.setLayoutParams(new LinearLayout.LayoutParams(widthPx, heightPx));
        }
        return view;
    }

    /** A horizontal progress bar, {@code fraction} filled in {@code color}, rounded per direction. */
    @NonNull public BarView bar(float fraction, @ColorInt int color, int heightDp) {
        BarView bar = new BarView(context, style);
        bar.set(fraction, color);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return bar;
    }

    /** A hairline in the outline-variant colour, {@code vertical} or horizontal. */
    @NonNull public View divider(boolean vertical) {
        View line = new View(context);
        line.setBackgroundColor(style.outlineVariant);
        int hair = Math.max(1, dp(1));
        line.setLayoutParams(new LinearLayout.LayoutParams(
            vertical ? hair : ViewGroup.LayoutParams.MATCH_PARENT,
            vertical ? ViewGroup.LayoutParams.MATCH_PARENT : hair));
        return line;
    }

    /** A filled dot of {@code sizeDp} in {@code color}. */
    @NonNull public View dot(int sizeDp, @ColorInt int color) {
        View dot = new View(context);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return dot;
    }

    /**
     * A round filled button with a glyph: the media play control, the task "+". Its visible disc
     * is {@code sizeDp}; the touch target is widened to 48dp by padding where there is room.
     */
    @NonNull public TextView roundButton(@NonNull String glyph, int sizeDp, float glyphSp,
                                         @ColorInt int fill, @ColorInt int onFill,
                                         @NonNull CharSequence description) {
        TextView button = glyph(glyph, glyphSp, onFill);
        android.graphics.drawable.GradientDrawable disc = new android.graphics.drawable.GradientDrawable();
        disc.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        disc.setColor(fill);
        button.setBackground(disc);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        button.setContentDescription(description);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    /** A rounded container the design draws lists and album art on. */
    @NonNull public android.graphics.drawable.GradientDrawable rounded(@ColorInt int fill, float radiusPx) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radiusPx);
        return shape;
    }

    /** The inner radius: the card's corner less the inset, never below zero. */
    public float innerRadius(int insetDp) {
        return Math.max(0f, style.cornerRadiusPx - dp(insetDp));
    }

    // ----- small views ----------------------------------------------------------------------

    /** The bar the design draws under a value: track in the high container, fill in a role. */
    public static final class BarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final BuiltinWidgetStyle style;
        private float fraction;
        private float start;
        @ColorInt private int color;

        public BarView(@NonNull Context context, @NonNull BuiltinWidgetStyle style) {
            super(context);
            this.style = style;
            color = style.primary;
        }

        public void set(float fraction, @ColorInt int color) { set(0f, fraction, color); }

        /** A range bar: filled from {@code start} to {@code end}, both 0..1. */
        public void set(float start, float end, @ColorInt int color) {
            this.start = clamp(start);
            this.fraction = clamp(end);
            this.color = color;
            invalidate();
        }

        private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            float width = getWidth(), height = getHeight();
            float radius = Math.min(style.barRadiusPx, height / 2f);
            paint.setColor(style.containerHigh);
            rect.set(0, 0, width, height);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (fraction > start) {
                paint.setColor(color);
                rect.set(width * start, 0, width * fraction, height);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }
}
