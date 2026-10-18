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
 *
 * <p>A measure only measures. A parent may measure the card more than once a pass at sizes it
 * then throws away: the Home column measures the widget grid at the status bar's height first
 * while the bar overlays Home, then at its full height. A rebuild in the measure flipped the
 * content to the 1×1 and back on every pass, and every rebuild stopped and started the live
 * sources, each start a new receiver or observer in the system server. So the bucket the last
 * measure asks for is built at layout, once the size is the one the card is drawn at, and the
 * sources run from attach to detach across measures.</p>
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

    /**
     * Where a widget whose Glass is on gets the launcher's glass: the page it stands on, which
     * re-feeds every registered widget on each frost refresh ({@link #onGlassStyle}) and re-aims
     * them as the wall slides ({@link #onGlassMoved}). A widget registers only while attached with
     * its switch on, so the page's work is the glass widgets and nothing else.
     */
    public interface GlassSource {
        @Nullable com.termux.app.terminal.PaneSurfaceStyle widgetGlassStyle();
        void addGlassWidget(@NonNull BuiltinWidgetView widget);
        void removeGlassWidget(@NonNull BuiltinWidgetView widget);
    }

    @NonNull protected final BuiltinWidgetKind kind;
    @NonNull protected final BuiltinWidgetServices services;
    /** The page's look, as the host resolved it. */
    @NonNull private BuiltinWidgetStyle hostStyle;
    /** The look drawn: {@link #hostStyle}, or its glass variant while this widget's Glass is on. */
    @NonNull private BuiltinWidgetStyle style;
    /** This widget's own Glass switch, from its settings ({@link BuiltinWidgetGlass}). */
    private boolean glassOn;
    /** The wallpaper's blur under the card while Glass is on; made the first time it is wanted. */
    @Nullable private com.termux.app.terminal.PaneGlassBackdropView slab;
    @Nullable private GlassSource glassSource;
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
    /**
     * The zoom chosen for the last two sizes the current layout was measured at: a parent that
     * measures twice a pass at two sizes would otherwise search for it twice a pass. Forgotten
     * whenever the layout changes.
     */
    private final int[] zoomMemoWidth = {-1, -1};
    private final int[] zoomMemoHeight = {-1, -1};
    private final float[] zoomMemo = {1f, 1f};
    private int zoomMemoNext;
    /** The bucket the last measure's room asks for; built at the next layout when it is new. */
    @NonNull private BuiltinWidgetSpan wantedSpan = BuiltinWidgetSpan.ONE_BY_ONE;
    private int lastWidthSpec, lastHeightSpec;
    private boolean measuredOnce;

    private final View background;
    private final FrameLayout content;

    protected BuiltinWidgetView(@NonNull Context context, @NonNull BuiltinWidgetKind kind,
                                @NonNull BuiltinWidgetServices services,
                                @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.kind = kind;
        this.services = services;
        this.hostStyle = style;
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
            @Override public void onChildViewAdded(View parent, View child) { forgetZoom(); }
            @Override public void onChildViewRemoved(View parent, View child) { forgetZoom(); }
        });
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentDescription(context.getString(kind.label));
        applyChrome();
    }

    // ----- binding --------------------------------------------------------------------------

    /**
     * Brings the view level with its record's settings. The span is not the record's cell count
     * but the room those cells give on this grid, so it is chosen from the size the cell hands
     * down and built at layout ({@link #onLayout}); until the first layout the content is built
     * for the smallest.
     */
    public final void bind(@Nullable Bundle config) {
        Bundle nextConfig = config == null ? new Bundle() : new Bundle(config);
        boolean changed = !built || !sameConfig(this.config, nextConfig);
        this.config = nextConfig;
        if (!changed) return;
        boolean glass = BuiltinWidgetGlass.isOn(nextConfig.getString(BuiltinWidgetGlass.CONFIG_KEY));
        if (glass != glassOn) {
            glassOn = glass;
            resolveStyle();
            applyChrome();
            syncGlassRegistration();
        }
        rebuild();
    }

    /** The look drawn, from the page's and this widget's Glass switch. */
    private void resolveStyle() {
        style = BuiltinWidgetGlass.cardGlass(hostStyle.glass, glassOn)
            ? hostStyle.glassVariant(getContext()) : hostStyle;
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
        lastWidthSpec = widthMeasureSpec;
        lastHeightSpec = heightMeasureSpec;
        measuredOnce = true;
        // The room asks for a bucket, which is built at layout, not here: a parent may measure
        // at sizes it never lays out (see the class note). A view never bound builds at once.
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
            && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            wantedSpan = spanForPixels(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
        }
        if (!built) {
            span = wantedSpan;
            rebuild();
        }
        measureCard(widthMeasureSpec, heightMeasureSpec);
    }

    /** Measures the built layout into the card, zoomed to fill an exact size. */
    private void measureCard(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY
            || MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            setZoom(1f);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setZoom(zoomFor(width, height));
        background.measure(widthMeasureSpec, heightMeasureSpec);
        if (slab != null) slab.measure(widthMeasureSpec, heightMeasureSpec);
        measureContent(width, height, zoom);
        setMeasuredDimension(width, height);
    }

    /** The zoom for the current layout at {@code width}x{@code height}, remembered per size. */
    private float zoomFor(int width, int height) {
        for (int i = 0; i < zoomMemo.length; i++) {
            if (zoomMemoWidth[i] == width && zoomMemoHeight[i] == height) return zoomMemo[i];
        }
        float chosen = chooseZoom(width, height);
        int slot = zoomMemoNext;
        zoomMemoNext = (slot + 1) % zoomMemo.length;
        zoomMemoWidth[slot] = width;
        zoomMemoHeight[slot] = height;
        zoomMemo[slot] = chosen;
        return chosen;
    }

    private void forgetZoom() {
        for (int i = 0; i < zoomMemo.length; i++) {
            zoomMemoWidth[i] = -1;
            zoomMemoHeight[i] = -1;
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // The size laid out is the one drawn: its bucket is built now, before the frame draws,
        // so the old layout never shows at the new size. Measured once more for the new layout.
        if (measuredOnce && built && wantedSpan != span) {
            span = wantedSpan;
            rebuild();
            int wasWidth = getMeasuredWidth();
            int wasHeight = getMeasuredHeight();
            measureCard(lastWidthSpec, lastHeightSpec);
            // Only a card that wraps its content can change size with its layout; the parent
            // hears of it on the next pass.
            if (getMeasuredWidth() != wasWidth || getMeasuredHeight() != wasHeight) {
                post(this::requestLayout);
            }
        }
        if (slab != null) slab.layout(0, 0, right - left, bottom - top);
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

    /**
     * What the layout gives up at {@code scale}: parts left out, labels cut short, numerals shrunk.
     *
     * <p>Every trial measures the whole layout afresh. A view answers a measure at a spec it has
     * seen before from its measure cache, without measuring its children, and the losses are read
     * from those children: a card back at a size it had before (the Margin slider walked, then
     * Undo) ran its trials at the very specs of its first search, every one of them answered from
     * the cache, every one read the losses the last size had left behind, and the search settled
     * on the largest zoom, the date cut short and the numerals clipped. The zoom memo then kept
     * that zoom for the size.</p>
     */
    private int lossesAt(int width, int height, float scale) {
        remeasureAll(content);
        measureContent(width, height, scale);
        return losses(content);
    }

    /** Marks {@code view} and everything under it to measure for real at its next measure. */
    private static void remeasureAll(@NonNull View view) {
        view.forceLayout();
        if (view instanceof FitStack) {
            // Its children are only the ones the last layout attached; it measures all of them.
            List<View> all = new ArrayList<>();
            ((FitStack) view).collectAll(all);
            for (View child : all) remeasureAll(child);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) remeasureAll(group.getChildAt(i));
        }
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
        hostStyle = next;
        resolveStyle();
        applyChrome();
        dressSlab();
        if (built) rebuild();
    }

    // ----- glass ----------------------------------------------------------------------------

    /** Whether this widget's own Glass switch is on. */
    public final boolean isGlassOn() { return glassOn; }

    /**
     * The launcher's glass, from the page: the wallpaper's shared blur under the card. Called on
     * every frost refresh; a re-feed with what the slab already wears costs nothing.
     */
    public final void onGlassStyle(@Nullable com.termux.app.terminal.PaneSurfaceStyle source) {
        dressSlab(source);
    }

    /** The wall moved the page, or the wallpaper panned: the slab re-aims at what is behind it. */
    public final void onGlassMoved() {
        if (slab != null && slab.getVisibility() == VISIBLE) slab.invalidateGlassPosition();
    }

    private void syncGlassRegistration() {
        GlassSource source = isAttachedToWindow() && glassOn && !preview ? findGlassSource() : null;
        if (glassSource != null && glassSource != source) glassSource.removeGlassWidget(this);
        if (source != null && source != glassSource) source.addGlassWidget(this);
        glassSource = source;
        dressSlab();
    }

    @Nullable private GlassSource findGlassSource() {
        for (android.view.ViewParent parent = getParent(); parent != null;
             parent = parent.getParent()) {
            if (parent instanceof GlassSource) return (GlassSource) parent;
        }
        return null;
    }

    private void dressSlab() {
        dressSlab(glassSource == null ? null : glassSource.widgetGlassStyle());
    }

    /**
     * Shows the slab, fed from {@code source}, or puts it away. The slab draws the one shared
     * frame through a matrix, so it holds no pixels of its own; it carries no tint (the
     * see-through card over it is the tint, in the scheme's surface colour), and it is cut at the
     * card's own corner radius.
     */
    private void dressSlab(@Nullable com.termux.app.terminal.PaneSurfaceStyle source) {
        boolean shown = BuiltinWidgetGlass.slabShown(glassOn, preview,
            com.termux.app.terminal.PaneGlass.isActive(source));
        if (!shown) {
            if (slab != null && slab.getVisibility() != GONE) slab.setVisibility(GONE);
            return;
        }
        if (slab == null) {
            slab = new com.termux.app.terminal.PaneGlassBackdropView(getContext());
            slab.setClipToOutline(true);
            slab.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline out) {
                    out.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        style.cornerRadiusPx);
                }
            });
            com.termux.app.terminal.PaneGlass.followLayout(slab);
            addView(slab, 0, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        }
        slab.setGlass(source.paneGlassBlurFrame(), source.paneGlassBlurFrameRect(),
            android.graphics.Color.TRANSPARENT, source.paneGlassGrainLayer(),
            source.paneGlassGrainStrength(), style.cornerRadiusPx,
            source.paneGlassFrostFilter(), source.paneGlassCrossfade());
        slab.setParallax(source.wallpaperParallax());
        slab.invalidateOutline();
        if (slab.getVisibility() != VISIBLE) slab.setVisibility(VISIBLE);
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
        forgetZoom();
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
        syncGlassRegistration();
    }

    @Override @CallSuper protected void onDetachedFromWindow() {
        stopInternal();
        if (glassSource != null) {
            glassSource.removeGlassWidget(this);
            glassSource = null;
        }
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
