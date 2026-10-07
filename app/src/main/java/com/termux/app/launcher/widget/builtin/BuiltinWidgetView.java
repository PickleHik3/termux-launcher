package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * One of the launcher's own widgets, as a view in a grid cell.
 *
 * <p>The widget owns its outline: it carries a child with id {@code android:id/background} that
 * clips to its rounded outline, which is the Launcher3 convention {@code WidgetCellView} honours
 * by clipping nothing of its own, so the corner radius here is the one that shows. The content
 * is rebuilt whenever the span bucket, the settings or the style change; a subclass builds one
 * layout per {@link BuiltinWidgetSpan} in {@link #onBuild} and keeps live data flowing between
 * {@link #onStart} and {@link #onStop}, which bracket the time the view is on screen.</p>
 */
public abstract class BuiltinWidgetView extends FrameLayout {
    /** One editable setting of a widget, offered by the settings sheet. */
    public static final class ConfigField {
        public enum Type { TEXT, NUMBER, MULTILINE }
        @NonNull public final String key;
        @NonNull public final CharSequence label;
        @NonNull public final Type type;
        @NonNull public final String fallback;
        public ConfigField(@NonNull String key, @NonNull CharSequence label, @NonNull Type type,
                           @NonNull String fallback) {
            this.key = key; this.label = label; this.type = type; this.fallback = fallback;
        }
    }

    @NonNull protected final BuiltinWidgetKind kind;
    @NonNull protected final BuiltinWidgetServices services;
    @NonNull private BuiltinWidgetStyle style;
    @NonNull private BuiltinWidgetSpan span = BuiltinWidgetSpan.ONE_BY_ONE;
    /** The cells the widget spans on its grid; the bucket is chosen from them and the room. */
    private int cellColumns = 1, cellRows = 1;
    @NonNull private Bundle config = new Bundle();
    private boolean preview;
    private boolean built;
    private boolean started;

    private final View background;
    private final FrameLayout content;

    protected BuiltinWidgetView(@NonNull Context context, @NonNull BuiltinWidgetKind kind,
                                @NonNull BuiltinWidgetServices services,
                                @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.kind = kind;
        this.services = services;
        this.style = style;
        background = new View(context);
        background.setId(android.R.id.background);
        background.setClipToOutline(true);
        addView(background, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        content = new FrameLayout(context);
        content.setClipToOutline(true);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentDescription(context.getString(kind.label));
        applyChrome();
    }

    // ----- binding --------------------------------------------------------------------------

    /**
     * Brings the view level with its record's settings. The span is not the record's cell count
     * but the room those cells give on this grid, so it is chosen in {@link #onMeasure} from the
     * size the cell hands down; until the first measure the content is built for the smallest.
     */
    public final void bind(@Nullable Bundle config) {
        Bundle nextConfig = config == null ? new Bundle() : new Bundle(config);
        boolean changed = !built || !sameConfig(this.config, nextConfig);
        this.config = nextConfig;
        if (changed) rebuild();
    }

    /** The cells the widget spans on its grid, from its record. */
    public final void setCells(int columns, int rows) {
        if (columns == cellColumns && rows == cellRows) return;
        cellColumns = columns;
        cellRows = rows;
        if (built) requestLayout();
    }

    /** The bucket this view would draw at {@code widthPx}×{@code heightPx}. */
    @NonNull private BuiltinWidgetSpan spanForPixels(int widthPx, int heightPx) {
        float density = getResources().getDisplayMetrics().density;
        return BuiltinWidgetSpan.forSize(widthPx / density, heightPx / density,
            cellColumns, cellRows);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The cell measures its content exactly, so the room is known here, before the content
        // is measured: a bucket change rebuilds the content in the same pass, with no frame in
        // between where the old layout shows in the new size.
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
            && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            BuiltinWidgetSpan next = spanForPixels(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
            if (next != span || !built) {
                span = next;
                rebuild();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** The look changed under the widget: a theme, a Look, the direction setting. */
    public final void applyStyle(@NonNull BuiltinWidgetStyle next) {
        style = next;
        applyChrome();
        if (built) rebuild();
    }

    /** Render as a picker card: no live subscriptions, sample data where there is none. */
    public final void setPreview(boolean value) { preview = value; }
    public final boolean isPreview() { return preview; }

    @NonNull public final BuiltinWidgetStyle style() { return style; }
    @NonNull public final BuiltinWidgetSpan span() { return span; }
    @NonNull public final BuiltinWidgetKind kind() { return kind; }
    @NonNull public final Bundle config() { return new Bundle(config); }

    @NonNull protected final String configString(@NonNull String key, @NonNull String fallback) {
        String value = config.getString(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    protected final int configInt(@NonNull String key, int fallback) {
        String value = config.getString(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    /** The settings this widget offers; empty means no cog in edit mode. */
    @NonNull public List<ConfigField> configFields() { return Collections.emptyList(); }

    /** The content frame the span layout goes into. */
    @NonNull protected final FrameLayout content() { return content; }

    @NonNull protected final BuiltinWidgetUi ui() { return new BuiltinWidgetUi(getContext(), style); }

    protected final void rebuild() {
        boolean wasStarted = started;
        if (wasStarted) stopInternal();
        content.removeAllViews();
        onBuild(content, span, ui());
        built = true;
        if (wasStarted || (isAttachedToWindow() && !preview)) startInternal();
    }

    /**
     * Builds the layout for {@code span} into {@code frame}. Called on every rebuild with an
     * empty frame; the layout is one of the five designed ones.
     */
    protected abstract void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                    @NonNull BuiltinWidgetUi ui);

    /** The view is on screen with a built layout: subscribe and show live data. */
    protected void onStart() { }
    /** The view left the screen or is about to rebuild: unsubscribe. */
    protected void onStop() { }

    /** The widget body was tapped (not one of its own controls). */
    protected void onTap() { }

    private void startInternal() {
        if (started || !built) return;
        started = true;
        onStart();
    }

    private void stopInternal() {
        if (!started) return;
        started = false;
        onStop();
    }

    @Override @CallSuper protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!preview) startInternal();
    }

    @Override @CallSuper protected void onDetachedFromWindow() {
        stopInternal();
        super.onDetachedFromWindow();
    }

    /** Whether the live subscriptions are running. */
    protected final boolean isStarted() { return started; }

    // ----- chrome ---------------------------------------------------------------------------

    private void applyChrome() {
        GradientDrawable card = new GradientDrawable();
        card.setColor(style.card);
        card.setCornerRadius(style.cornerRadiusPx);
        if (style.hasRim()) card.setStroke(Math.round(style.rimWidthPx), style.rim);
        background.setBackground(card);
        ViewOutlineProvider outline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), style.cornerRadiusPx);
            }
        };
        background.setOutlineProvider(outline);
        content.setOutlineProvider(outline);
        background.invalidateOutline();
        content.invalidateOutline();
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> onTap());
    }

    private static boolean sameConfig(@NonNull Bundle a, @NonNull Bundle b) {
        if (a.size() != b.size()) return false;
        for (String key : a.keySet()) {
            Object left = a.get(key), right = b.get(key);
            if (left == null ? right != null : !left.equals(right)) return false;
        }
        return true;
    }

    /** Pads {@code frame}'s single child layout by the design's inset. */
    protected static void inset(@NonNull ViewGroup child, int left, int top, int right, int bottom,
                                @NonNull BuiltinWidgetUi ui) {
        child.setPadding(ui.dp(left), ui.dp(top), ui.dp(right), ui.dp(bottom));
    }
}
