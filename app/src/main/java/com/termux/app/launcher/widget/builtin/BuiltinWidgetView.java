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
        /**
         * A text field; a switch, stored "on" or "off"; or one of a few {@link Option}s, as
         * segmented buttons or, with {@link #tiles}, a strip of live previews.
         */
        public enum Type { TEXT, NUMBER, MULTILINE, SWITCH, CHOICE, ZONES }

        /** One value a {@link Type#CHOICE} offers, and its name. */
        public static final class Option {
            @NonNull public final String value;
            @NonNull public final CharSequence label;
            public Option(@NonNull String value, @NonNull CharSequence label) {
                this.value = value; this.label = label;
            }
        }

        /** Draws one option of a choice as itself, for the settings sheet's strip. */
        public interface TileArt {
            @NonNull View make(@NonNull Context context, @NonNull String value);
        }

        /** {@link Type#SWITCH}'s stored values. */
        public static final String ON = "on";
        public static final String OFF = "off";

        @NonNull public final String key;
        @NonNull public final CharSequence label;
        @NonNull public final Type type;
        @NonNull public final String fallback;
        /** A choice's options, in order; empty for the other types. */
        @NonNull public final List<Option> options;
        /** A choice drawn as a strip of previews, or null for segmented buttons. */
        @Nullable public final TileArt tiles;
        /** The field shows only while {@link #shownWhenKey} holds {@link #shownWhenValue}. */
        @Nullable public final String shownWhenKey;
        @Nullable public final String shownWhenValue;
        /** A {@link Type#ZONES} row's second key ({@link #key} is its first); null otherwise. */
        @Nullable public final String secondKey;

        public ConfigField(@NonNull String key, @NonNull CharSequence label, @NonNull Type type,
                           @NonNull String fallback) {
            this(key, label, type, fallback, Collections.emptyList(), null, null, null);
        }

        /** A row that opens two city or time-zone fields, stored under {@code key} and {@code secondKey}. */
        @NonNull public static ConfigField zones(@NonNull String key, @NonNull String secondKey,
                                                 @NonNull CharSequence label) {
            return new ConfigField(key, label, Type.ZONES, "", Collections.emptyList(), null,
                null, null, secondKey);
        }

        private ConfigField(@NonNull String key, @NonNull CharSequence label, @NonNull Type type,
                            @NonNull String fallback, @NonNull List<Option> options,
                            @Nullable TileArt tiles, @Nullable String shownWhenKey,
                            @Nullable String shownWhenValue) {
            this(key, label, type, fallback, options, tiles, shownWhenKey, shownWhenValue, null);
        }

        private ConfigField(@NonNull String key, @NonNull CharSequence label, @NonNull Type type,
                            @NonNull String fallback, @NonNull List<Option> options,
                            @Nullable TileArt tiles, @Nullable String shownWhenKey,
                            @Nullable String shownWhenValue, @Nullable String secondKey) {
            this.secondKey = secondKey;
            this.key = key; this.label = label; this.type = type; this.fallback = fallback;
            this.options = Collections.unmodifiableList(new ArrayList<>(options));
            this.tiles = tiles;
            this.shownWhenKey = shownWhenKey; this.shownWhenValue = shownWhenValue;
        }

        /** One of {@code options}, {@code fallback} where none is stored. */
        @NonNull public static ConfigField choice(@NonNull String key, @NonNull CharSequence label,
                                                  @NonNull String fallback,
                                                  @NonNull List<Option> options,
                                                  @Nullable TileArt tiles) {
            return new ConfigField(key, label, Type.CHOICE, fallback, options, tiles, null, null);
        }

        /** A switch, on or off by {@code fallback} where none is stored. */
        @NonNull public static ConfigField toggle(@NonNull String key, @NonNull CharSequence label,
                                                  boolean fallback) {
            return new ConfigField(key, label, Type.SWITCH, fallback ? ON : OFF,
                Collections.emptyList(), null, null, null);
        }

        /** This field, shown only while the field {@code key} holds {@code value}. */
        @NonNull public ConfigField shownWhen(@NonNull String key, @NonNull String value) {
            return new ConfigField(this.key, label, type, fallback, options, tiles, key, value,
                secondKey);
        }

        /** Whether the field shows while the sheet's fields hold {@code values}. */
        public boolean shownFor(@NonNull java.util.Map<String, String> values) {
            return shownWhenKey == null || java.util.Objects.equals(values.get(shownWhenKey),
                shownWhenValue);
        }
    }

    /**
     * Where a widget gets the Look's glass: the page it stands on, which hands out the Look as a
     * pane style ({@code LookGlassStyle}: Base's values, never a detached surface's), re-feeds
     * every registered widget whenever the Look or its blur frame moves ({@link #onGlassStyle})
     * and re-aims them as the wall slides ({@link #onGlassMoved}). Every live widget registers
     * while attached, worn or not, since its corners follow the Look too; the page asks for a
     * blur frame only while one of them has its Glass on ({@link #isGlassOn}), and is told again
     * whenever a switch flips.
     */
    public interface GlassSource {
        @Nullable com.termux.app.terminal.PaneSurfaceStyle widgetGlassStyle();
        /** The Look's "Widgets on glass" switch: what a widget that follows the page wears. */
        default boolean widgetsOnGlass() { return false; }
        /** {@code widget} is live on this page, or its switch flipped; idempotent. */
        void addGlassWidget(@NonNull BuiltinWidgetView widget);
        void removeGlassWidget(@NonNull BuiltinWidgetView widget);
    }

    @NonNull protected final BuiltinWidgetKind kind;
    @NonNull protected final BuiltinWidgetServices services;
    /** The look as the host resolved it: the style setting, cut at the Look's Corners. */
    @NonNull private BuiltinWidgetStyle hostStyle;
    /** The look drawn: {@link #hostStyle}, or its glass variant while this widget's Glass is on. */
    @NonNull private BuiltinWidgetStyle style;
    /** This widget's own Glass choice, from its settings ({@link BuiltinWidgetGlass}). */
    @Nullable private String glassChoice;
    /** The Look's slab under the card while Glass is on; made the first time it is wanted. */
    @Nullable private com.termux.app.terminal.PaneGlassBackdropView slab;
    /** The Look the slab wears now, or null while it wears none. */
    @Nullable private com.termux.app.terminal.PaneSurfaceStyle wornLook;
    /** The alpha of the Look's tint the slab wears; what the card's ground is, see-through. */
    private int wornTintAlpha;
    @Nullable private GlassSource glassSource;
    /** Whether the page's ink is the pale one, so the card is the dark one ({@link #resolveStyle}). */
    private boolean inkPale;
    /** Whether the widget wears the Look's glass now: its switch on and no solid fallback. */
    private boolean glassWorn;
    /** The Look last fed, which the widget band is asked through; null before any. */
    @Nullable private com.termux.app.terminal.PaneSurfaceStyle fedLook;
    /**
     * The card's own fill over the slab while glass is worn: the body's lift and the measured veil,
     * one layer of the card's colour. Transparent otherwise.
     */
    private int cardFill;
    /** Whether the solid card draws its rim at the outline role, where it would vanish. */
    private boolean edgeRim;
    /**
     * Whether the widget is drawn Bare now (glossary: Bare): it asks to be ({@link #asksBare})
     * and nothing asks for the card instead ({@link BuiltinWidgetGlass#bareWorn}).
     */
    private boolean bareWorn;
    /** What {@link #asksBare} answered at the last bind, so a setting that flips it re-dresses. */
    private boolean askedBare;
    /** The soft dim under a Bare widget's ink, as measured; transparent where it reads without. */
    private int bareDim;
    /** Whether {@link #bareInks} were measured on the wallpaper, not the theme's own. */
    private boolean bareMeasured;
    /** A Bare widget's measured inks: on-surface, on-surface-variant, primary, warm. */
    private final int[] bareInks = new int[4];
    /** The opaque colour the content stands on, as last measured; the card itself before that. */
    private int groundSurface;
    private final android.graphics.Rect inkRect = new android.graphics.Rect();
    private final int[] inkLocation = new int[2];
    private final int[] inkRootLocation = new int[2];
    /** A restyle the dress asked for inside a layout pass, built after it. */
    private final Runnable restyle = () -> { if (this.built) rebuild(); };
    @NonNull private BuiltinWidgetSpan span = BuiltinWidgetSpan.ONE_BY_ONE;
    /** The cells the widget spans on its grid; the bucket is chosen from them and the room. */
    private int cellColumns = 1, cellRows = 1;
    @NonNull private Bundle config = new Bundle();
    /** The record this view draws, or {@link #NO_WIDGET_ID} for a picker preview. */
    private int widgetId = NO_WIDGET_ID;
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
    /**
     * The Look's corner as this widget wears it, live: the card, the slab, the clips and the inner
     * shapes the layout cut from it all read it, so a Corners change re-dresses them in place.
     */
    private final BuiltinWidgetCorner corner;

    protected BuiltinWidgetView(@NonNull Context context, @NonNull BuiltinWidgetKind kind,
                                @NonNull BuiltinWidgetServices services,
                                @NonNull BuiltinWidgetStyle style) {
        super(context);
        this.kind = kind;
        this.services = services;
        this.hostStyle = style;
        this.style = style;
        groundSurface = style.card;
        corner = new BuiltinWidgetCorner(style.cornerRadiusPx, style.density);
        background = new View(context);
        background.setId(android.R.id.background);
        background.setClipToOutline(true);
        addView(background, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Both cut at the card's corner as the slab is, read at outline time from this view's size.
        background.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadiusPx());
            }
        });
        content = new FrameLayout(context);
        content.setClipToOutline(true);
        content.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadiusPx() / zoom);
            }
        });
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
        String choice = nextConfig.getString(BuiltinWidgetGlass.CONFIG_KEY);
        boolean asked = asksBare();
        if (!java.util.Objects.equals(choice, glassChoice) || asked != askedBare) {
            glassChoice = choice;
            askedBare = asked;
            // The re-dress settles the style; the rebuild below builds in it.
            syncGlassRegistration();
        }
        rebuild();
    }

    /**
     * The look drawn: the host's, on the card the page's ink asks for ({@link #inkPale}), on the
     * Look's glass while the widget wears it ({@link #glassWorn}), else on the solid card.
     */
    private void resolveStyle() {
        BuiltinWidgetStyle onInk = hostStyle.onInk(inkPale);
        if (bareWorn) {
            style = bareMeasured
                ? onInk.bare(bareInks[0], bareInks[1], bareInks[2], bareInks[3])
                : onInk.bare(onInk.onSurface, onInk.onSurfaceVariant, onInk.primary, onInk.warm);
            return;
        }
        style = glassWorn ? onInk.glassVariant(getContext()) : onInk;
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
            if (rebuildsForSpan()) {
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
        }
        if (slab != null) slab.layout(0, 0, right - left, bottom - top);
        background.layout(0, 0, right - left, bottom - top);
        content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
        // Moved on its grid without a new size: what is behind it is new. A memoised ask when it
        // is not; a re-dress of the card's fill and rim only, never a rebuild.
        if (changed) remeasureInk();
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
     * What the layout gives up at {@code scale}: parts left out, labels cut short.
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
        // Freshly resolved, so the Look's corner as stored now; the rebuild cuts its shapes at it.
        corner.set(next.cornerRadiusPx);
        applyChrome();
        // Built below whatever the dress settles, so its own restyle is not built twice.
        dressSlab();
        if (built) rebuild();
    }

    // ----- glass ----------------------------------------------------------------------------

    /**
     * Whether this widget's own Glass switch is on. Never for a widget that asks to be Bare: it
     * has no card for the glass to be, and the page asks for no blur frame on its behalf.
     */
    public final boolean isGlassOn() {
        if (asksBare()) return false;
        return BuiltinWidgetGlass.resolve(glassChoice,
            glassSource != null && glassSource.widgetsOnGlass());
    }

    /**
     * Whether the page should hold the Look's blur frame for this widget: its Glass on and no
     * system ask (battery saver, outline text, high contrast) drawing it solid instead
     * ({@link BuiltinWidgetGlass#holdsFrame}).
     */
    public final boolean wantsGlassFrame() {
        if (preview || !isGlassOn()) return false;
        BuiltinWidgetGlass.SystemAsks asks = BuiltinWidgetGlass.SystemAsks.current(getContext());
        return BuiltinWidgetGlass.holdsFrame(true, false, asks.batterySaver, asks.outlineText,
            asks.highContrast);
    }

    /**
     * Whether this widget asks to be drawn Bare (glossary: Bare): no card, rim or slab, its ink
     * straight on the wallpaper behind it, measured there on the widget band, with a soft dim of
     * at most {@link com.termux.app.chrome.OnGlass#BARE_MAX_DIM} only where the measurement says
     * the ink would not read without one ({@link com.termux.app.chrome.OnGlass#resolveBareWidget}).
     * A kind that is always Bare says yes; one that offers it reads its own setting, which a bind
     * re-dresses. The Look never answers this: its Clear stop is glass, not Bare.
     */
    protected boolean asksBare() { return false; }

    /** Whether this widget's own Glass row says Bare (a kind that offers it asks through here). */
    protected final boolean bareChosen() {
        return BuiltinWidgetGlass.choseBare(config.getString(BuiltinWidgetGlass.CONFIG_KEY));
    }

    /** Whether the widget is drawn Bare now; see {@link #asksBare}. */
    public final boolean isBare() { return bareWorn; }

    /**
     * The Look, from the page: its glass under the card while Glass is on, and its Corners for
     * every widget. Called whenever the Look or its blur frame moves, a Corners slider's every
     * tick included; a re-feed with what the widget already wears costs nothing, and a corner
     * that moved is re-dressed in place ({@link #takeCorner}): nothing is rebuilt or laid out.
     */
    public final void onGlassStyle(@Nullable com.termux.app.terminal.PaneSurfaceStyle source) {
        if (dressSlab(source)) restyled();
    }

    /**
     * The Look's corner from {@code source}: the card's drawable, its clips, the inner shapes and
     * the slab take it where they stand. Redraws only.
     */
    private void takeCorner(@NonNull com.termux.app.terminal.PaneSurfaceStyle source) {
        if (!corner.set(com.termux.app.terminal.PaneGlass.radiusPx(source,
                getResources().getDisplayMetrics().density))) {
            return;
        }
        if (background.getBackground() instanceof GradientDrawable)
            ((GradientDrawable) background.getBackground()).setCornerRadius(cardRadiusPx());
        background.invalidateOutline();
        content.invalidateOutline();
        onCornerChanged();
    }

    /** The Look's corner moved under the widget, already re-dressed in place; see {@link #lookCornerRadiusPx}. */
    protected void onCornerChanged() { }

    /** The Look's corner as this widget wears it now, before the card holds it to its size. */
    protected final float lookCornerRadiusPx() { return corner.radiusPx(); }

    /**
     * The card's corner at its size now: the Look's, never more than a third of the card's
     * shorter side, as a pane's is ({@link com.termux.app.terminal.PaneShape#radiusForBounds}).
     */
    protected final float cardRadiusPx() {
        return com.termux.app.terminal.PaneShape.radiusForBounds(corner.radiusPx(), getWidth(),
            getHeight());
    }

    /** The widget's live corner; for tests. */
    @androidx.annotation.VisibleForTesting
    @NonNull final BuiltinWidgetCorner corner() { return corner; }

    /**
     * How opaque the card's own ground is: whole on the solid card; on the Look's glass, the
     * Look's tint with the card's fill (the body's lift and its veil) over it.
     */
    protected final int cardAlpha() {
        if (bareWorn) return android.graphics.Color.alpha(bareDim);
        if (wornLook == null) return 255;
        int fill = android.graphics.Color.alpha(cardFill);
        return 255 - Math.round((255 - wornTintAlpha) * (255 - fill) / 255f);
    }

    /**
     * The card changed its ground ({@link #cardAlpha}): the Glass switch, the Look's glass worn
     * or put down, or its opacity moving, a slider's every tick included.
     */
    protected void onCardChanged() { }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The corner is held against the size: the card's drawable and the slab follow it here.
        // Neither asks for a layout; both only redraw.
        if (background.getBackground() instanceof GradientDrawable)
            ((GradientDrawable) background.getBackground()).setCornerRadius(cardRadiusPx());
        // The slab follows the size, and the band is asked about the new rect.
        if (dressSlab(fedLook)) restyled();
    }

    /** The wall moved the page, or the wallpaper panned: the slab re-aims at what is behind it. */
    public final void onGlassMoved() {
        if (slab != null && slab.getVisibility() == VISIBLE) slab.invalidateGlassPosition();
    }

    /** @return whether the style moved; see {@link #dressSlab(com.termux.app.terminal.PaneSurfaceStyle)} */
    private boolean syncGlassRegistration() {
        GlassSource source = isAttachedToWindow() && !preview ? findGlassSource() : null;
        if (glassSource != null && glassSource != source) glassSource.removeGlassWidget(this);
        glassSource = source;
        // Again on a switch flip, so the page knows before the slab asks for a frame.
        if (source != null) source.addGlassWidget(this);
        return dressSlab();
    }

    @Nullable private GlassSource findGlassSource() {
        for (android.view.ViewParent parent = getParent(); parent != null;
             parent = parent.getParent()) {
            if (parent instanceof GlassSource) return (GlassSource) parent;
        }
        return null;
    }

    /** {@link #dressSlab(com.termux.app.terminal.PaneSurfaceStyle)} with the page's Look. */
    private boolean dressSlab() {
        return dressSlab(glassSource == null ? null : glassSource.widgetGlassStyle());
    }

    /** A restyle {@link #dressSlab} reported, built now or, inside a layout pass, after it. */
    private void restyled() {
        if (!built) return;
        removeCallbacks(restyle);
        if (isInLayout()) post(restyle);
        else rebuild();
    }

    /**
     * Shows the Look's slab, fed from {@code source}, or puts it away, and settles the card on
     * the page's ink. Dressed by {@link com.termux.app.terminal.PaneGlass#apply} as a terminal
     * pane's slab is — the one shared frame through a matrix (no pixels of its own), the Look's
     * frost, tint, grain and Fancier Glass — with the Look's rim, cut at the card's corner. The
     * card over it then draws only the body's lift and the measured veil ({@link #measureInk}).
     *
     * <p>The slab is shown only where the glass can be worn: the widget's switch on, the
     * launcher on the wallpaper, the shared frame ready, and none of battery saver, outline text
     * or high system contrast ({@link BuiltinWidgetGlass#solidFallback}); else the solid card.
     * The card is the dark one on the page's pale ink, or at night
     * ({@link BuiltinWidgetStyle#onInk}).</p>
     *
     * @return whether the style moved (the card's side, or glass worn or put down), so the
     *     layout has to be built again in it; the caller builds
     */
    private boolean dressSlab(@Nullable com.termux.app.terminal.PaneSurfaceStyle source) {
        fedLook = source;
        // Every widget wears the Look's corner, glass or not.
        if (source != null) takeCorner(source);
        com.termux.app.chrome.WidgetInkSource ink = inkSource(source);
        boolean onWallpaper = com.termux.app.terminal.PaneGlass.isActive(source);
        boolean bare = false;
        if (asksBare()) {
            BuiltinWidgetGlass.SystemAsks asks = BuiltinWidgetGlass.SystemAsks.current(getContext());
            bare = BuiltinWidgetGlass.bareWorn(true, preview, onWallpaper, asks.outlineText,
                asks.highContrast);
        }
        boolean glassOn = isGlassOn();
        boolean shown;
        if (BuiltinWidgetGlass.slabShown(glassOn, preview, onWallpaper)) {
            BuiltinWidgetGlass.SystemAsks asks = BuiltinWidgetGlass.SystemAsks.current(getContext());
            shown = BuiltinWidgetGlass.glassWorn(glassOn, preview, onWallpaper,
                ink == null || ink.widgetFrameReady(), asks.batterySaver, asks.outlineText,
                asks.highContrast);
        } else {
            shown = false;
        }
        // Night is the style's own (onInk keeps a night style dark); a picker card asks nothing.
        boolean pale = ink != null && !preview
            && BuiltinWidgetGlass.darkCard(false, ink.widgetPolarity());
        BuiltinWidgetStyle before = style;
        boolean bareMoved = bare != bareWorn;
        inkPale = pale;
        glassWorn = shown;
        bareWorn = bare;
        resolveStyle();
        boolean styleMoved = style != before;
        if (!shown) {
            if (slab != null && slab.getVisibility() != GONE) slab.setVisibility(GONE);
            boolean chromeMoved = wornLook != null || bareMoved;
            wornLook = null;
            // A Bare widget's inks are measured here, so its style may move again.
            boolean measured = measureInk();
            styleMoved = style != before;
            if (measured || chromeMoved || styleMoved) applyChrome();
            return styleMoved;
        }
        if (slab == null) {
            slab = new com.termux.app.terminal.PaneGlassBackdropView(getContext());
            slab.setClipToOutline(true);
            slab.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline out) {
                    out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cardRadiusPx());
                }
            });
            com.termux.app.terminal.PaneGlass.followLayout(slab);
            addView(slab, 0, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        }
        com.termux.app.terminal.PaneGlass.apply(source, this, slab, corner.radiusPx());
        slab.setRim(cardRadiusPx(), source);
        slab.invalidateOutline();
        int tintAlpha = android.graphics.Color.alpha(source.paneGlassTintColor());
        boolean chromeMoved = wornLook == null || tintAlpha != wornTintAlpha;
        wornLook = source;
        wornTintAlpha = tintAlpha;
        if (measureInk() || chromeMoved || styleMoved) applyChrome();
        return styleMoved;
    }

    /** The widget band of the Look fed, or null where there is no chrome to ask (a test, a picker). */
    @Nullable
    private static com.termux.app.chrome.WidgetInkSource inkSource(
            @Nullable com.termux.app.terminal.PaneSurfaceStyle source) {
        return source instanceof com.termux.app.chrome.WidgetInkSource
            ? (com.termux.app.chrome.WidgetInkSource) source : null;
    }

    /**
     * Asks the widget band ({@link com.termux.app.chrome.ChromeInk#widget}) what this card needs
     * where it is laid out: the veil over the glass body, the ground the content stands on, and
     * whether the solid card shows its edge. A memoised answer for a widget asked before at the
     * same rect; never per frame (the wall's slide is not a new rect: the laid-out one ignores it).
     *
     * @return whether the card's fill or rim moved, so it is dressed again
     */
    private boolean measureInk() {
        if (bareWorn) return measureBare();
        com.termux.app.chrome.WidgetInkSource ink = preview ? null : inkSource(fedLook);
        int lift = glassWorn && wornLook != null
            ? com.termux.app.chrome.OnGlass.withAlpha(style.card,
                com.termux.app.chrome.OnGlass.widgetLiftAlpha255(wornTintAlpha))
            : android.graphics.Color.TRANSPARENT;
        int fill = lift;
        boolean rim = false;
        int ground = style.card;
        if (ink != null && getWidth() > 0 && getHeight() > 0) {
            com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(this, inkLocation,
                inkRootLocation);
            inkRect.set(inkLocation[0], inkLocation[1], inkLocation[0] + getWidth(),
                inkLocation[1] + getHeight());
            int body = glassWorn && wornLook != null
                ? com.termux.app.chrome.OnGlass.composite(lift, wornLook.paneGlassTintColor())
                : style.card;
            com.termux.app.chrome.OnGlass.Resolution answer = ink.widgetInk(inkRect, body,
                style.card, style.onSurfaceVariant, style.primary);
            ground = answer.surface;
            if (glassWorn) fill = com.termux.app.chrome.OnGlass.stackAlpha(lift, answer.veil);
            else rim = BuiltinWidgetGlass.edgeRimWanted(style.card, ink.widgetWallpaper(inkRect));
        }
        boolean moved = fill != cardFill || rim != edgeRim;
        cardFill = fill;
        edgeRim = rim;
        groundSurface = ground;
        return moved;
    }

    /**
     * A Bare widget's ink, measured on the widget band against the wallpaper behind it
     * ({@link com.termux.app.chrome.WidgetInkSource#widgetWallpaper}, the band's one memoised
     * sample): the page's inks where they read, under the least soft dim of the card's colour up
     * to {@link com.termux.app.chrome.OnGlass#BARE_MAX_DIM} where they need one, moved where even
     * that is short ({@link com.termux.app.chrome.OnGlass#resolveBareWidget}). Text and the
     * caution ink are held to 4.5:1, the accent's graphics to 3:1. With nothing to measure (a
     * picker card) the theme's own inks stand. Settles the drawn style on the inks.
     *
     * @return whether the dim moved (or the card's fill and rim went), so it is dressed again
     */
    private boolean measureBare() {
        BuiltinWidgetStyle base = hostStyle.onInk(inkPale);
        com.termux.app.chrome.WidgetInkSource ink = preview ? null : inkSource(fedLook);
        int dim = android.graphics.Color.TRANSPARENT;
        int ground = groundSurface;
        boolean measured = false;
        if (ink != null && getWidth() > 0 && getHeight() > 0) {
            com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(this, inkLocation,
                inkRootLocation);
            inkRect.set(inkLocation[0], inkLocation[1], inkLocation[0] + getWidth(),
                inkLocation[1] + getHeight());
            int wallpaper = ink.widgetWallpaper(inkRect);
            boolean pale = base.dark;
            double text = com.termux.app.chrome.OnGlass.TARGET_BODY_TEXT;
            double graphic = com.termux.app.chrome.OnGlass.TARGET_LARGE_TEXT;
            com.termux.app.chrome.OnGlass.Resolution answer =
                com.termux.app.chrome.OnGlass.resolveBareWidget(wallpaper, base.onSurfaceVariant,
                    text, base.primary, graphic, base.card, pale);
            dim = answer.veil;
            ground = answer.surface;
            bareInks[0] = com.termux.app.chrome.OnGlass.bareInk(answer, base.onSurface, text, pale);
            bareInks[1] = com.termux.app.chrome.OnGlass.bareInk(answer, base.onSurfaceVariant, text,
                pale);
            bareInks[2] = com.termux.app.chrome.OnGlass.bareInk(answer, base.primary, graphic, pale);
            // The warm accent is also the caution ink, which is text.
            bareInks[3] = com.termux.app.chrome.OnGlass.bareInk(answer, base.warm, text, pale);
            measured = true;
        }
        boolean moved = dim != bareDim || cardFill != android.graphics.Color.TRANSPARENT
            || edgeRim;
        bareDim = dim;
        bareMeasured = measured;
        cardFill = android.graphics.Color.TRANSPARENT;
        edgeRim = false;
        groundSurface = ground;
        resolveStyle();
        return moved;
    }

    /**
     * Re-asks the band for where the widget is laid out now, and re-dresses the card if it moved;
     * a Bare widget whose inks moved with it is built again in them.
     */
    private void remeasureInk() {
        BuiltinWidgetStyle before = style;
        if (measureInk()) applyChrome();
        if (style != before) restyled();
    }

    /**
     * The opaque colour this widget's content stands on, as last measured: the card's ground, the
     * glass body and its veil over what is behind the widget. The card itself before any answer.
     */
    protected final int groundSurface() { return groundSurface; }

    /** {@link #widgetId()} of a view no record stands behind. */
    public static final int NO_WIDGET_ID = Integer.MIN_VALUE;

    /** The record this view draws; set by the host before the first bind. */
    public final void setWidgetId(int id) { widgetId = id; }

    /** The record this view draws, or {@link #NO_WIDGET_ID}. */
    public final int widgetId() { return widgetId; }

    /**
     * Whether a settings key is the widget's own state rather than a setting: kept in the record
     * beside the settings, carried through the settings sheet, but never a reason to rebuild.
     */
    public static boolean isStateKey(@NonNull String key) {
        return key.startsWith(STATE_KEY_PREFIX);
    }

    /** What every state key starts with ({@link #isStateKey}). */
    public static final String STATE_KEY_PREFIX = "_";

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

    /**
     * Whether a new size bucket builds the layout again. A widget whose one layout fills every
     * size says no, and keeps what it holds across a resize.
     */
    protected boolean rebuildsForSpan() { return true; }

    /** The settings this widget offers; empty means no cog in edit mode. */
    @NonNull public List<ConfigField> configFields() { return Collections.emptyList(); }

    /** The content frame the span layout goes into. */
    @NonNull protected final FrameLayout content() { return content; }

    @NonNull protected final BuiltinWidgetUi ui() {
        return new BuiltinWidgetUi(getContext(), style, corner);
    }

    protected final void rebuild() {
        boolean wasStarted = started;
        if (wasStarted) stopInternal();
        content.removeAllViews();
        corner.forgetShapes();
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
        if (syncGlassRegistration()) restyled();
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

    /**
     * Dresses the card: the solid card with its rim, or, while the Look's slab is worn, the
     * card's fill over it (the body's lift and the measured veil, in the card's colour; no rim of
     * its own, the slab draws the Look's). The solid card draws the outline role's rim where it
     * would vanish into its wallpaper ({@link #edgeRim}), else Pane's hairline. Runs on a style
     * change, when the slab comes or goes, and when the band's answer moves; never on a frame.
     * Only the drawable changes, so it is safe inside a layout pass.
     */
    private void applyChrome() {
        if (bareWorn) {
            // No card, rim or slab: at most the measured soft dim under the ink.
            background.setBackground(android.graphics.Color.alpha(bareDim) == 0 ? null
                : new SoftDim(bareDim));
        } else if (wornLook != null) {
            if (android.graphics.Color.alpha(cardFill) == 0) {
                background.setBackground(null);
            } else {
                GradientDrawable fill = new GradientDrawable();
                fill.setColor(cardFill);
                fill.setCornerRadius(cardRadiusPx());
                background.setBackground(fill);
            }
        } else {
            GradientDrawable card = new GradientDrawable();
            card.setColor(style.card);
            card.setCornerRadius(cardRadiusPx());
            if (edgeRim) {
                card.setStroke(Math.round(Math.max(1f, style.density)), style.outline);
            }
            background.setBackground(card);
        }
        background.invalidateOutline();
        content.invalidateOutline();
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> onTap());
        onCardChanged();
    }

    /**
     * A Bare widget's dim: the card's colour at the measured alpha, full under the content inset
     * and feathered to nothing at the widget's edge, so it reads as shade rather than a card.
     * Read at the card's corner when drawn. Where the canvas cannot blur (hardware drawing before
     * API 28) the edge is simply hard, inset by the feather.
     */
    private final class SoftDim extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint =
            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float feather;

        SoftDim(int color) {
            paint.setColor(color);
            feather = WidgetTokens.INSET_COMPACT_DP / 2f * style.density;
            if (feather > 0f) {
                paint.setMaskFilter(new android.graphics.BlurMaskFilter(feather,
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            }
        }

        @Override public void draw(@NonNull android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float radius = Math.max(0f, cardRadiusPx() - feather);
            canvas.drawRoundRect(bounds.left + feather, bounds.top + feather,
                bounds.right - feather, bounds.bottom - feather, radius, radius, paint);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(@Nullable android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Whether two settings bundles hold the same settings, the widget's own state left out. */
    static boolean sameConfig(@NonNull Bundle a, @NonNull Bundle b) {
        java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            if (isStateKey(key)) continue;
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
