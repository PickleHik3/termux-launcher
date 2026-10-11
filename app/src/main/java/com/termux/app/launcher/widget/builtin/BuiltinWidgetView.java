package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
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
 *
 * <p>A card taller than its bucket's design is not left with bands of empty card above and below
 * the layout: the content is laid out smaller and zoomed up to fill the card, so the design is
 * drawn larger, margins and all. The zoom is the largest that keeps everything the layout shows
 * at the card's own size (nothing more left out, no label newly cut short), which the card's
 * width decides as often as its height: a 2×1 on a 4×5 grid is taller than the design but no
 * wider.</p>
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

    /** The most a layout is zoomed, however tall the card. */
    private static final float MAX_ZOOM = 2f;
    /** A zoom closer to 1 than this is not worth drawing the content scaled. */
    private static final float MIN_ZOOM = 1.02f;
    /** Steps of the search for the largest zoom that loses nothing. */
    private static final int ZOOM_STEPS = 6;
    /** How much the content is drawn larger than it is laid out; chosen per size and layout. */
    private float zoom = 1f;
    private int zoomWidth = -1, zoomHeight = -1;
    private boolean zoomStale = true;

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
        // A subclass may swap the layout in place (a permission granted, a file opened) without
        // a rebuild; the zoom chosen for the old one is not the new one's.
        content.setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) { zoomStale = true; }
            @Override public void onChildViewRemoved(View parent, View child) { zoomStale = true; }
        });
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
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
            || MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            setZoom(1f);
            zoomStale = true;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (zoomStale || width != zoomWidth || height != zoomHeight) {
            zoomWidth = width;
            zoomHeight = height;
            zoomStale = false;
            setZoom(chooseZoom(width, height));
        }
        background.measure(widthMeasureSpec, heightMeasureSpec);
        measureContent(width, height, zoom);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        background.layout(0, 0, right - left, bottom - top);
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
    }

    private void setZoom(float next) {
        if (next == zoom) return;
        zoom = next;
        content.setPivotX(0f);
        content.setPivotY(0f);
        content.setScaleX(next);
        content.setScaleY(next);
        // The outline is drawn zoomed too: its radius is the card's, unzoomed.
        content.invalidateOutline();
    }

    /**
     * The zoom that lays the bucket out at its design height, or the largest below it at which
     * the layout keeps what it keeps unzoomed; 1 for a card no taller than the design.
     */
    private float chooseZoom(int width, int height) {
        if (!zoomsToFill(span)) return 1f;
        float density = getResources().getDisplayMetrics().density;
        float most = Math.min(MAX_ZOOM, height / (span.heightDp * density));
        if (most < MIN_ZOOM) return 1f;
        int allowed = lossesAt(width, height, 1f);
        if (lossesAt(width, height, most) <= allowed) return most;
        float low = 1f, high = most;
        for (int i = 0; i < ZOOM_STEPS; i++) {
            float middle = (low + high) / 2f;
            if (lossesAt(width, height, middle) <= allowed) low = middle;
            else high = middle;
        }
        return low < MIN_ZOOM ? 1f : low;
    }

    private void measureContent(int width, int height, float scale) {
        content.measure(
            MeasureSpec.makeMeasureSpec(Math.round(width / scale), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.round(height / scale), MeasureSpec.EXACTLY));
    }

    /** What the layout gives up at {@code scale}: parts left out, labels cut short, numerals shrunk. */
    private int lossesAt(int width, int height, float scale) {
        measureContent(width, height, scale);
        return losses(content);
    }

    private static int losses(@NonNull View view) {
        if (view.getVisibility() == GONE) return 0;
        if (view instanceof ClockNumeralView) return ((ClockNumeralView) view).isShrunk() ? 1 : 0;
        if (view instanceof TextView) {
            Layout layout = ((TextView) view).getLayout();
            int lines = layout == null ? 0 : layout.getLineCount();
            return lines > 0 && layout.getEllipsisCount(lines - 1) > 0 ? 1 : 0;
        }
        if (!(view instanceof ViewGroup)) return 0;
        int count = 0;
        if (view instanceof FitStack) {
            // A FitStack's children are what this measure kept, not what the last layout attached.
            FitStack stack = (FitStack) view;
            count += stack.leftOutCount();
            List<View> shown = new ArrayList<>();
            stack.collectShown(shown);
            for (View child : shown) count += losses(child);
            return count;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) count += losses(group.getChildAt(i));
        return count;
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

    /**
     * Whether {@code span}'s layout is zoomed to fill a card taller than its design. A layout
     * that already takes the height itself (art that grows with the card) says no: zooming it
     * would narrow the room it grows into.
     */
    protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return true; }

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
        zoomStale = true;
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
        background.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), style.cornerRadiusPx);
            }
        });
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    style.cornerRadiusPx / zoom);
            }
        });
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
