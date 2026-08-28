package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.termux.terminal.KittyImagePlaceholder;
import com.termux.terminal.KittyUnicodePlaceholder;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

import java.util.Arrays;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    public enum LigaturePolicy { NEVER, CURSOR, ALWAYS }

    /** One explicit code-point range mapped to a font. Later entries override earlier ones. */
    public static final class SymbolMap {
        public final int firstCodePoint;
        public final int lastCodePoint;
        public final Typeface typeface;
        /**
         * This map's own Android feature-setting string, or null to inherit the shared
         * {@code symbols} slot of {@link FontFeatures}. An empty string inherits too.
         */
        @Nullable public final String features;
        /** This map's own axis settings, scoped like {@link #features}. */
        @Nullable public final String variations;

        public SymbolMap(int firstCodePoint, int lastCodePoint, Typeface typeface) {
            this(firstCodePoint, lastCodePoint, typeface, null, null);
        }

        /** A range mapped to a font that carries its own shaping settings. */
        public SymbolMap(int firstCodePoint, int lastCodePoint, Typeface typeface,
                         @Nullable String features, @Nullable String variations) {
            if (firstCodePoint < 0 || lastCodePoint < firstCodePoint
                || lastCodePoint > Character.MAX_CODE_POINT || typeface == null)
                throw new IllegalArgumentException("Invalid symbol font range");
            this.firstCodePoint = firstCodePoint;
            this.lastCodePoint = lastCodePoint;
            this.typeface = typeface;
            this.features = features == null || features.isEmpty() ? null : features;
            this.variations = variations == null || variations.isEmpty() ? null : variations;
        }
    }

    /** Android feature-setting strings scoped to requested SGR faces and symbol-map runs. */
    public static final class FontFeatures {
        public static final FontFeatures NONE = new FontFeatures(null, null, null, null, null);

        @Nullable private final String regular;
        @Nullable private final String bold;
        @Nullable private final String italic;
        @Nullable private final String boldItalic;
        @Nullable private final String symbols;

        public FontFeatures(@Nullable String regular, @Nullable String bold,
                            @Nullable String italic, @Nullable String boldItalic,
                            @Nullable String symbols) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            this.boldItalic = boldItalic;
            this.symbols = symbols;
        }

        @Nullable String forRun(boolean isBold, boolean isItalic, boolean isSymbol) {
            if (isSymbol) return symbols;
            if (isBold && isItalic) return boldItalic;
            if (isBold) return bold;
            if (isItalic) return italic;
            return regular;
        }
    }

    /** Android variable-font settings scoped like {@link FontFeatures}. */
    public static final class FontVariations {
        public static final FontVariations NONE = new FontVariations(null, null, null, null, null);

        @Nullable private final String regular;
        @Nullable private final String bold;
        @Nullable private final String italic;
        @Nullable private final String boldItalic;
        @Nullable private final String symbols;

        public FontVariations(@Nullable String regular, @Nullable String bold,
                              @Nullable String italic, @Nullable String boldItalic,
                              @Nullable String symbols) {
            this.regular = regular;
            this.bold = bold;
            this.italic = italic;
            this.boldItalic = boldItalic;
            this.symbols = symbols;
        }

        @Nullable String forRun(boolean isBold, boolean isItalic, boolean isSymbol) {
            if (isSymbol) return symbols;
            if (isBold && isItalic) return boldItalic;
            if (isBold) return bold;
            if (isItalic) return italic;
            return regular;
        }
    }

    public static final class MetricAdjustment {
        public final float value;
        public final boolean percent;

        public MetricAdjustment(float value, boolean percent) {
            this.value = value;
            this.percent = percent;
        }
    }

    /** Bounded cell and decoration metrics; null entries retain font-derived defaults. */
    public static final class FontMetricsAdjustments {
        public static final FontMetricsAdjustments NONE = new FontMetricsAdjustments(
            null, null, null, null, null, null, null);

        @Nullable final MetricAdjustment cellWidth;
        @Nullable final MetricAdjustment cellHeight;
        @Nullable final MetricAdjustment baseline;
        @Nullable final MetricAdjustment underlinePosition;
        @Nullable final MetricAdjustment underlineThickness;
        @Nullable final MetricAdjustment strikethroughPosition;
        @Nullable final MetricAdjustment strikethroughThickness;

        public FontMetricsAdjustments(@Nullable MetricAdjustment cellWidth,
                                      @Nullable MetricAdjustment cellHeight,
                                      @Nullable MetricAdjustment baseline,
                                      @Nullable MetricAdjustment underlinePosition,
                                      @Nullable MetricAdjustment underlineThickness,
                                      @Nullable MetricAdjustment strikethroughPosition,
                                      @Nullable MetricAdjustment strikethroughThickness) {
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.baseline = baseline;
            this.underlinePosition = underlinePosition;
            this.underlineThickness = underlineThickness;
            this.strikethroughPosition = strikethroughPosition;
            this.strikethroughThickness = strikethroughThickness;
        }
    }

    /**
     * How the geometric code points — box drawing, blocks, shades, braille, the legacy-computing
     * eighths and, optionally, the Powerline separators — are drawn.
     *
     * <p>{@link Mode#SYNTHESIZE} computes their ink from the cell, which is the only way adjacent
     * cells are guaranteed to join; {@link Mode#FONT} leaves every one of them to the configured
     * face. A code point covered by an explicit {@code symbol_map} range is always left to its
     * font, since asking for that font was a deliberate choice.
     */
    public static final class BoxDrawingPolicy {

        public enum Mode { SYNTHESIZE, FONT }

        public static final BoxDrawingPolicy DEFAULT = new BoxDrawingPolicy(null, null, false);

        final Mode mode;

        /** Multipliers for the thin, light, heavy and very heavy line weights. */
        final float[] thicknessScales;

        final boolean powerline;

        public BoxDrawingPolicy(@Nullable Mode mode, @Nullable float[] thicknessScales,
                                boolean powerline) {
            this.mode = mode == null ? Mode.SYNTHESIZE : mode;
            this.thicknessScales = thicknessScales == null || thicknessScales.length < 4
                ? BoxGeometry.DEFAULT_THICKNESS_SCALES : thicknessScales.clone();
            this.powerline = powerline;
        }

        /** Whether this cell is drawn as geometry rather than shaped as text. */
        boolean synthesizes(int codePoint) {
            return mode == Mode.SYNTHESIZE && BoxGeometry.isSynthesizable(codePoint, powerline);
        }
    }

    /**
     * Per-code-point ceilings on how many cells a private-use symbol may be drawn across, which is
     * kitty's {@code narrow_symbols}. Ranges are consulted in declaration order and the last match
     * wins, as kitty's {@code cell_cap_for_codepoint} does, so a later line can re-widen part of a
     * range an earlier one narrowed.
     */
    public static final class SymbolExpansion {

        public static final SymbolExpansion DEFAULT = new SymbolExpansion(null, null, null);

        private final int[] first;
        private final int[] last;
        private final int[] columns;

        /**
         * @param first   inclusive range starts
         * @param last    inclusive range ends, parallel to {@code first}
         * @param columns cell ceiling per range, parallel to {@code first}
         */
        public SymbolExpansion(@Nullable int[] first, @Nullable int[] last,
                               @Nullable int[] columns) {
            final int size = first == null || last == null || columns == null ? 0
                : Math.min(first.length, Math.min(last.length, columns.length));
            this.first = size == 0 ? NO_BOUNDS : Arrays.copyOf(first, size);
            this.last = size == 0 ? NO_BOUNDS : Arrays.copyOf(last, size);
            this.columns = size == 0 ? NO_BOUNDS : Arrays.copyOf(columns, size);
        }

        /** The configured ceiling for this code point, or {@link Integer#MAX_VALUE} for none. */
        int maxColumnsFor(int codePoint) {
            int cap = Integer.MAX_VALUE;
            for (int i = 0; i < first.length; i++) {
                if (codePoint >= first[i] && codePoint <= last[i]) cap = columns[i];
            }
            return cap;
        }
    }

    private static final int[] NO_BOUNDS = new int[0];

    /**
     * The most cells one private-use symbol may be drawn across, matching kitty's one cell plus
     * {@code MAX_NUM_EXTRA_GLYPHS_PUA}.
     */
    static final int MAX_SYMBOL_EXPANSION_COLUMNS = 5;

    private static final SymbolMap[] NO_SYMBOL_MAPS = new SymbolMap[0];

    private static final Typeface[] NO_FALLBACK_TYPEFACES = new Typeface[0];

    final int mTextSize;

    final Typeface mTypeface;

    @Nullable final Typeface mBoldTypeface;

    @Nullable
    final Typeface mItalicTypeface;

    @Nullable final Typeface mBoldItalicTypeface;

    final SymbolMap[] mSymbolMaps;

    /** Faces consulted, in configured order, for code points the primary face has no glyph for. */
    final Typeface[] mFallbackTypefaces;

    final LigaturePolicy mLigaturePolicy;

    final FontFeatures mFontFeatures;

    final FontVariations mFontVariations;

    final FontMetricsAdjustments mFontMetricsAdjustments;

    final BoxDrawingPolicy mBoxDrawingPolicy;

    final SymbolExpansion mSymbolExpansion;

    /**
     * Suppresses this view's cursor without touching the shared emulator state.
     *
     * <p>Every pane paints its own cursor, because {@code shouldCursorBeVisible} has no focus term
     * — sensible for one terminal, wrong for a split, where several lit cursors leave nothing to
     * say which pane the keyboard is talking to. kitty holds inactive windows at
     * {@code cursor_opacity = 0} for the same reason. Per-renderer rather than per-emulator,
     * because the emulator is shared with every other view showing the same session.
     */
    private boolean mCursorSuppressed;

    private final Paint mTextPaint = new Paint();
    /** Fills for the find overlay, kept off the text paint's per-run state. */
    private final Paint mOverlayPaint = new Paint();
    private Typeface mCurrentTypeface;

    /** Memoized fallback-chain lookups; sized for this renderer's chain and never resized. */
    private final FallbackFontResolver mFallbackResolver;

    /** Scratch paint for coverage probes, so they cannot disturb the drawing paint's state. */
    private final Paint mCoveragePaint = new Paint();

    /**
     * Coverage probes for {@link #mFallbackResolver}, as one instance so the render loop allocates
     * nothing. The probe itself only runs on a memo miss — at most once per code point and face —
     * which is what makes the string {@link Paint#hasGlyph} needs affordable.
     */
    private final FallbackFontResolver.Coverage mCoverage = new FallbackFontResolver.Coverage() {
        @Override
        public boolean hasGlyph(int faceStyle, int faceIndex, int codePoint) {
            mCoveragePaint.setTypeface(faceIndex == FallbackFontResolver.NO_OVERRIDE
                ? primaryTypeface((faceStyle & 1) != 0, (faceStyle & 2) != 0)
                : mFallbackTypefaces[faceIndex]);
            return mCoveragePaint.hasGlyph(new String(Character.toChars(codePoint)));
        }
    };

    /**
     * Variable-font instances, keyed by base typeface and axis settings.
     *
     * <p>{@link Paint#setFontVariationSettings} instantiates a new {@link Typeface} from the axes
     * every time it is called, and the draw path used to call it — plus a second call to restore
     * the previous value — for every run whose axes differed from the last one. A config combining
     * {@code symbol_map} with {@code font_variations} alternates between symbol and text runs
     * across a line, so a full screen meant dozens of variable-font instantiations per frame:
     * measured at 73ms median frame time against 12ms with the axes removed.
     *
     * <p>The set of (face, axes) pairs a config can produce is tiny and fixed, so each instance is
     * built once and the hot path just selects a typeface.
     */
    private final java.util.HashMap<String, Typeface> mVariationTypefaces = new java.util.HashMap<>();

    /**
     * The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'.
     */
    final float mFontWidth;

    /**
     * The font line height, derived from {@link Paint.FontMetricsInt} descent-ascent.
     */
    final int mFontLineSpacing;

    /**
     * The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png
     */
    private final int mFontAscent;

    /** View top content inset; intentionally independent of baseline adjustment. */
    final int mFontLineSpacingAndAscent;

    /** Distance from the configured baseline to the bottom of its cell. */
    private final int mFontBaselineDescent;

    /** Width cache for normal, bold, italic and bold-italic rendering. */
    private final float[][] mAsciiMeasures = new float[4][127];
    private final RectF mSixelRect = new RectF();

    /** Reused by the Unicode-placeholder image path so drawing a cell allocates nothing. */
    private final KittyImagePlaceholder mKittyPlaceholder = new KittyImagePlaceholder();
    private final Rect mKittySourceRect = new Rect();
    private final RectF mKittyDestRect = new RectF();
    private final RectF mKittyCellRect = new RectF();
    private final Paint mKittyImagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /** Reused when drawing curly underlines, to keep the render loop allocation free. */
    private final Path mDecorationPath = new Path();

    /** Reused when centering a symbol run in its cells, for the same reason. */
    private final Paint.FontMetrics mSymbolFontMetrics = new Paint.FontMetrics();

    /** Reused by the synthesized box-drawing pass, for the same reason. */
    private final BoxGeometry.Segments mBoxSegments = new BoxGeometry.Segments();

    private final Path mBoxPath = new Path();

    private final RectF mBoxOval = new RectF();

    /** Stroke width of underlines and their dash lengths, all derived from the font size. */
    private final float mDecorationThickness;

    /** Underline top measured from the top of its cell after all adjustments. */
    private final float mUnderlinePositionFromTop;

    private final float mStrikethroughThickness;

    /** Strikethrough center measured from the top of its cell after all adjustments. */
    private final float mStrikethroughPositionFromTop;

    private final DashPathEffect mDottedEffect;

    private final DashPathEffect mDashedEffect;

    public TerminalRenderer(int textSize, Typeface typeface, Typeface italicTypeface) {
        this(textSize, typeface, null,
            italicTypeface != null && !italicTypeface.equals(typeface) ? italicTypeface : null,
            null, NO_SYMBOL_MAPS, LigaturePolicy.NEVER, FontFeatures.NONE, FontVariations.NONE,
            FontMetricsAdjustments.NONE);
    }

    /** Construct a renderer with independent real faces; null variants use synthetic styling. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, NO_SYMBOL_MAPS,
            LigaturePolicy.NEVER, FontFeatures.NONE);
    }

    /** Construct a renderer with real faces and explicit per-code-point symbol fonts. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            LigaturePolicy.NEVER, FontFeatures.NONE);
    }

    /** Construct a renderer with explicit font maps and programming-ligature policy. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, FontFeatures.NONE);
    }

    /** Construct a renderer with explicit fonts and per-run shaping controls. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, fontFeatures, FontVariations.NONE);
    }

    /** Construct a renderer with explicit fonts and every supported per-run shaping control. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures,
                            @Nullable FontVariations fontVariations) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, fontFeatures, fontVariations, FontMetricsAdjustments.NONE);
    }

    /** Construct a renderer with explicit fonts, shaping controls, and bounded metrics. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures,
                            @Nullable FontVariations fontVariations,
                            @Nullable FontMetricsAdjustments fontMetricsAdjustments) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, fontFeatures, fontVariations, fontMetricsAdjustments,
            BoxDrawingPolicy.DEFAULT);
    }

    /** Construct a renderer with every font, shaping, metric and box-drawing control. */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures,
                            @Nullable FontVariations fontVariations,
                            @Nullable FontMetricsAdjustments fontMetricsAdjustments,
                            @Nullable BoxDrawingPolicy boxDrawingPolicy) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, fontFeatures, fontVariations, fontMetricsAdjustments, boxDrawingPolicy,
            NO_FALLBACK_TYPEFACES);
    }

    /**
     * Construct a renderer with every control, including the ordered fallback-face chain consulted
     * for code points the face selected for a run has no glyph of its own for.
     */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures,
                            @Nullable FontVariations fontVariations,
                            @Nullable FontMetricsAdjustments fontMetricsAdjustments,
                            @Nullable BoxDrawingPolicy boxDrawingPolicy,
                            @Nullable Typeface[] fallbackTypefaces) {
        this(textSize, typeface, boldTypeface, italicTypeface, boldItalicTypeface, symbolMaps,
            ligaturePolicy, fontFeatures, fontVariations, fontMetricsAdjustments, boxDrawingPolicy,
            fallbackTypefaces, SymbolExpansion.DEFAULT);
    }

    /**
     * Construct a renderer with every control, including the per-code-point ceilings on how far a
     * private-use symbol may spread into the blank cells after it.
     */
    public TerminalRenderer(int textSize, Typeface typeface, @Nullable Typeface boldTypeface,
                            @Nullable Typeface italicTypeface,
                            @Nullable Typeface boldItalicTypeface,
                            @Nullable SymbolMap[] symbolMaps,
                            @Nullable LigaturePolicy ligaturePolicy,
                            @Nullable FontFeatures fontFeatures,
                            @Nullable FontVariations fontVariations,
                            @Nullable FontMetricsAdjustments fontMetricsAdjustments,
                            @Nullable BoxDrawingPolicy boxDrawingPolicy,
                            @Nullable Typeface[] fallbackTypefaces,
                            @Nullable SymbolExpansion symbolExpansion) {
        mBoxDrawingPolicy = boxDrawingPolicy == null
            ? BoxDrawingPolicy.DEFAULT : boxDrawingPolicy;
        mSymbolExpansion = symbolExpansion == null ? SymbolExpansion.DEFAULT : symbolExpansion;
        mFallbackTypefaces = fallbackTypefaces == null || fallbackTypefaces.length == 0
            ? NO_FALLBACK_TYPEFACES : fallbackTypefaces.clone();
        mFallbackResolver = new FallbackFontResolver(mFallbackTypefaces.length);
        mTextSize = textSize;
        mTypeface = typeface;
        mBoldTypeface = boldTypeface;
        mItalicTypeface = italicTypeface;
        mBoldItalicTypeface = boldItalicTypeface;
        mSymbolMaps = symbolMaps == null || symbolMaps.length == 0
            ? NO_SYMBOL_MAPS : symbolMaps.clone();
        mLigaturePolicy = ligaturePolicy == null ? LigaturePolicy.NEVER : ligaturePolicy;
        mFontFeatures = fontFeatures == null ? FontFeatures.NONE : fontFeatures;
        mFontVariations = fontVariations == null ? FontVariations.NONE : fontVariations;
        mFontMetricsAdjustments = fontMetricsAdjustments == null
            ? FontMetricsAdjustments.NONE : fontMetricsAdjustments;
        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        Paint.FontMetricsInt fontMetrics = mTextPaint.getFontMetricsInt();
        mFontAscent = fontMetrics.ascent;
        int baseLineSpacing = fontMetrics.descent - mFontAscent;
        float baseWidth = mTextPaint.measureText("X");
        mFontWidth = clamp(adjustMetric(baseWidth, mFontMetricsAdjustments.cellWidth), 2f, 1000f);
        mFontLineSpacing = Math.round(clamp(
            adjustMetric(baseLineSpacing, mFontMetricsAdjustments.cellHeight), 4f, 1000f));
        float baseBaselineFromTop = -mFontAscent;
        float centeredBaseline = baseBaselineFromTop + (mFontLineSpacing - baseLineSpacing) / 2f;
        float baselineAdjustment = adjustmentDelta(baseBaselineFromTop,
            mFontMetricsAdjustments.baseline);
        float baselineFromTop = clamp(centeredBaseline - baselineAdjustment, 1f,
            mFontLineSpacing - 1f);
        float effectiveBaselineRaise = centeredBaseline - baselineFromTop;
        mFontBaselineDescent = Math.round(mFontLineSpacing - baselineFromTop);
        mFontLineSpacingAndAscent = Math.round(clamp(fontMetrics.descent
            + (mFontLineSpacing - baseLineSpacing) / 2f, 0f, mFontLineSpacing));
        StringBuilder sb = new StringBuilder(" ");
        for (int style = 0; style < mAsciiMeasures.length; style++) {
            configureFont((style & 1) != 0, (style & 2) != 0);
            for (int i = 0; i < mAsciiMeasures[style].length; i++) {
                sb.setCharAt(0, (char) i);
                mAsciiMeasures[style][i] = mTextPaint.measureText(sb, 0, 1);
            }
        }
        configureFont(false, false);
        float baseDecorationThickness = Math.max(1f, textSize / 16f);
        mDecorationThickness = clamp(adjustMetric(baseDecorationThickness,
            mFontMetricsAdjustments.underlineThickness), 0.5f, mFontLineSpacing / 3f);
        float baseUnderlinePosition = baseBaselineFromTop + fontMetrics.descent * 0.4f;
        mUnderlinePositionFromTop = adjustMetric(baseUnderlinePosition,
            mFontMetricsAdjustments.underlinePosition)
            + (mFontLineSpacing - baseLineSpacing) / 2f - effectiveBaselineRaise;
        mStrikethroughThickness = clamp(adjustMetric(baseDecorationThickness,
            mFontMetricsAdjustments.strikethroughThickness), 0.5f, mFontLineSpacing / 3f);
        float baseStrikethroughPosition = baseBaselineFromTop + mFontAscent / 3f;
        mStrikethroughPositionFromTop = adjustMetric(baseStrikethroughPosition,
            mFontMetricsAdjustments.strikethroughPosition)
            + (mFontLineSpacing - baseLineSpacing) / 2f - effectiveBaselineRaise;
        mDottedEffect = new DashPathEffect(new float[]{mDecorationThickness, mDecorationThickness * 2f}, 0f);
        mDashedEffect = new DashPathEffect(new float[]{mDecorationThickness * 4f, mDecorationThickness * 3f}, 0f);
    }

    static float adjustMetric(float original, @Nullable MetricAdjustment adjustment) {
        if (adjustment == null) return original;
        return adjustment.percent ? original * adjustment.value / 100f : original + adjustment.value;
    }

    private static float adjustmentDelta(float original, @Nullable MetricAdjustment adjustment) {
        return adjustMetric(original, adjustment) - original;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow, int selectionY1, int selectionY2, int selectionX1, int selectionX2, boolean transparentBackground, int transparentOverlayColor, float horizontalOffset) {
        render(mEmulator, canvas, topRow, selectionY1, selectionY2, selectionX1, selectionX2,
            transparentBackground, transparentOverlayColor, horizontalOffset, 0);
    }

    /**
     * As {@link #render}, but drawing extraRows rows past the bottom of the screen. Used when the
     * canvas is translated by a fraction of a row for smooth scrolling, where the row scrolling in
     * from below is partially visible.
     */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow, int selectionY1, int selectionY2, int selectionX1, int selectionX2, boolean transparentBackground, int transparentOverlayColor, float horizontalOffset, int extraRows) {
        final boolean boldWithBright = mEmulator.isBoldWithBright();
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = Math.min(topRow + mEmulator.mRows + extraRows, mEmulator.mRows);
        final int columns = mEmulator.mColumns;
        final int cursorCol = mEmulator.getCursorCol();
        final int cursorRow = mEmulator.getCursorRow();
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible() && !mCursorSuppressed;
        final TerminalBuffer screen = mEmulator.getScreen();
        final int[] palette = mEmulator.mColors.mCurrentColors;
        final int cursorShape = mEmulator.getCursorStyle();
        mEmulator.setCellSize((int) mFontWidth, (int) mFontLineSpacing);
        if (reverseVideo) {
            canvas.drawColor(palette[TextStyle.COLOR_INDEX_FOREGROUND], PorterDuff.Mode.SRC);
        } else if (transparentBackground) {
            canvas.drawColor(transparentOverlayColor, PorterDuff.Mode.SRC);
        }
        float heightOffset = mFontLineSpacingAndAscent;
        // Backgrounds and the cursor block for every row are painted before any glyph, so a glyph
        // whose ink overhangs its cells — Nerd Font symbols routinely do — lands on top of a
        // neighbouring cell's fill instead of being clipped by it. Cell backgrounds are exactly
        // cell-aligned on screen (the per-run scale matrix cancels out for rectangles), so this
        // pass needs no font configuration or shaping.
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1)
                    selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }
            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            drawRowBackgroundAndCursor(canvas, lineObject, palette, heightOffset, columns, cursorX,
                cursorShape, selx1, selx2, boldWithBright, reverseVideo, horizontalOffset,
                palette[TextStyle.COLOR_INDEX_CURSOR]);
        }
        heightOffset = mFontLineSpacingAndAscent;
        for (int row = topRow; row < endRow; row++) {
            heightOffset += mFontLineSpacing;
            final int cursorX = (row == cursorRow && cursorVisible) ? cursorCol : -1;
            int selx1 = -1, selx2 = -1;
            if (row >= selectionY1 && row <= selectionY2) {
                if (row == selectionY1)
                    selx1 = selectionX1;
                selx2 = (row == selectionY2) ? selectionX2 : mEmulator.mColumns;
            }
            TerminalRow lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row));
            final char[] line = lineObject.mText;
            final int charsUsedInLine = lineObject.getSpaceUsed();
            long lastRunStyle = 0;
            boolean lastRunInsideCursor = false;
            boolean lastRunInsideSelection = false;
            int lastRunStartColumn = -1;
            int lastRunStartIndex = 0;
            boolean lastRunFontWidthMismatch = false;
            int lastRunDecorationColor = TextStyle.DECORATION_COLOR_DEFAULT;
            int lastRunHyperlinkId = 0;
            Typeface lastRunSymbolTypeface = null;
            Typeface lastRunFallbackTypeface = null;
            // The settings the run's matched symbol map resolved to; null outside a symbol run.
            String lastRunSymbolFeatures = null;
            String lastRunSymbolVariations = null;
            // Raised by the cells that flush the run to their left and paint themselves — image,
            // placeholder, synthesized glyph, expanded symbol — so the next iteration starts a
            // fresh run at the cell after them.
            boolean startFreshRun = false;
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;
            // Both live in side tables on the row rather than in the style long, so they are only
            // consulted for the rows that have them.
            final boolean rowHasDecorationColors = lineObject.hasDecorationColors();
            final boolean rowHasHyperlinks = lineObject.hasHyperlinks();
            KittyUnicodePlaceholder.Cell previousPlaceholder = null;
            int previousPlaceholderColumn = -2;
            for (int column = 0; column < columns; ) {
                if (startFreshRun) {
                    measuredWidthForRun = 0.f;
                    lastRunStyle = 0;
                    lastRunInsideCursor = false;
                    lastRunInsideSelection = false;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = false;
                    lastRunDecorationColor = TextStyle.DECORATION_COLOR_DEFAULT;
                    lastRunHyperlinkId = 0;
                    lastRunSymbolTypeface = null;
                    lastRunFallbackTypeface = null;
                    lastRunSymbolFeatures = null;
                    lastRunSymbolVariations = null;
                    startFreshRun = false;
                }
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final long style = lineObject.getStyle(column);
                final int decorationColor = rowHasDecorationColors ? lineObject.getDecorationColor(column) : TextStyle.DECORATION_COLOR_DEFAULT;
                final int hyperlinkId = rowHasHyperlinks ? lineObject.getHyperlinkId(column) : 0;
                if (TextStyle.isBitmap(style)) {
                    previousPlaceholder = null;
                    previousPlaceholderColumn = -2;
                    // Flush the text run accumulated to the left of this image cell. Without this
                    // a row that mixes text and image cells — which a z<0 kitty placement produces
                    // routinely — silently dropped its text.
                    if (column > 0 && column != lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = lastRunInsideCursor
                            && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                            columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                            measuredWidthForRun, cursorColor, cursorShape, lastRunStyle,
                            boldWithBright, reverseVideo || invertCursorTextColor
                                || lastRunInsideSelection,
                            horizontalOffset, lastRunDecorationColor,
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface,
                            lastRunFallbackTypeface, lastRunSymbolFeatures,
                            lastRunSymbolVariations, false);
                    }
                    Bitmap bm = mEmulator.getScreen().getSixelBitmap(codePoint, style);
                    if (bm != null) {
                        float left = horizontalOffset + column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        mSixelRect.set(left, top, left + mFontWidth, top + mFontLineSpacing);
                        canvas.drawBitmap(mEmulator.getScreen().getSixelBitmap(codePoint, style), mEmulator.getScreen().getSixelRect(codePoint, style), mSixelRect, null);
                    }
                    column += 1;
                    currentCharIndex += charsForCodePoint;
                    startFreshRun = true;
                    continue;
                }
                if (codePoint == KittyUnicodePlaceholder.CODE_POINT) {
                    // A placeholder remains normal text in the buffer (so tmux and editors can
                    // move it), but its grapheme is renderer control data rather than a glyph.
                    if (column > 0 && column != lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int runCursorColor = lastRunInsideCursor
                            ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertRunTextColor = lastRunInsideCursor
                            && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                            columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                            measuredWidthForRun, runCursorColor, cursorShape, lastRunStyle,
                            boldWithBright, reverseVideo || invertRunTextColor
                                || lastRunInsideSelection,
                            horizontalOffset, lastRunDecorationColor,
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface,
                            lastRunFallbackTypeface, lastRunSymbolFeatures,
                            lastRunSymbolVariations, false);
                    }
                    int clusterEnd = currentCharIndex + charsForCodePoint;
                    while (clusterEnd < charsUsedInLine
                        && lineObject.getDisplayWidthAt(clusterEnd) <= 0) {
                        clusterEnd += Character.isHighSurrogate(line[clusterEnd]) ? 2 : 1;
                    }
                    KittyUnicodePlaceholder.Cell inherited = previousPlaceholderColumn == column - 1
                        ? previousPlaceholder : null;
                    KittyUnicodePlaceholder.Cell placeholderCell = KittyUnicodePlaceholder.decode(
                        line, currentCharIndex + charsForCodePoint, clusterEnd,
                        TextStyle.decodeForeColor(style), decorationColor,
                        TextStyle.DECORATION_COLOR_DEFAULT, inherited);
                    if (placeholderCell != null) {
                        previousPlaceholder = placeholderCell;
                        previousPlaceholderColumn = column;
                        if (mEmulator.getKittyImagePlaceholder(placeholderCell.imageId,
                            placeholderCell.placementId, mKittyPlaceholder)) {
                            drawKittyPlaceholderCell(canvas, placeholderCell, column, heightOffset,
                                horizontalOffset);
                        }
                    } else {
                        previousPlaceholder = null;
                        previousPlaceholderColumn = -2;
                    }
                    if (cursorX == column && cursorVisible)
                        drawPlaceholderCursor(canvas, column, heightOffset, horizontalOffset,
                            cursorShape, palette[TextStyle.COLOR_INDEX_CURSOR]);
                    column++;
                    currentCharIndex = clusterEnd;
                    startFreshRun = true;
                    continue;
                }
                previousPlaceholder = null;
                previousPlaceholderColumn = -2;
                final int codePointWcWidth = lineObject.getDisplayWidthAt(currentCharIndex);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final int effect = TextStyle.decodeEffect(style);
                final boolean cellBold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
                    | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
                final boolean cellItalic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
                final int faceStyle = (cellBold ? 1 : 0) | (cellItalic ? 2 : 0);
                if (mBoxDrawingPolicy.synthesizes(codePoint)) {
                    // Geometry, not a glyph: flush the run accumulated to the left of this cell
                    // exactly as an image cell does, then draw the ink and start a fresh run.
                    // The policy outranks any symbol_map here — the managed nerd-font map spans
                    // the whole PUA, and matching it must not silently turn synthesis off.
                    if (column > 0 && column != lastRunStartColumn) {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int runCursorColor = lastRunInsideCursor
                            ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertRunTextColor = lastRunInsideCursor
                            && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                            columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                            measuredWidthForRun, runCursorColor, cursorShape, lastRunStyle,
                            boldWithBright, reverseVideo || invertRunTextColor
                                || lastRunInsideSelection,
                            horizontalOffset, lastRunDecorationColor,
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface,
                            lastRunFallbackTypeface, lastRunSymbolFeatures,
                            lastRunSymbolVariations, false);
                    }
                    final int cellColumns = Math.max(1, codePointWcWidth);
                    if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
                        final boolean invertCellTextColor = insideCursor
                            && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                        drawSynthesizedCell(canvas, codePoint, column, cellColumns, heightOffset,
                            horizontalOffset, resolveInkColor(style, palette, boldWithBright,
                                reverseVideo || invertCellTextColor || insideSelection));
                    }
                    column += cellColumns;
                    currentCharIndex += charsForCodePoint;
                    while (currentCharIndex < charsUsedInLine
                        && lineObject.getDisplayWidthAt(currentCharIndex) <= 0) {
                        currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                    }
                    startFreshRun = true;
                    continue;
                }
                final SymbolMap symbolMap = symbolMapFor(codePoint);
                final Typeface symbolTypeface = symbolMap == null ? null : symbolMap.typeface;
                final String symbolFeatures = symbolFeaturesOf(symbolMap);
                final String symbolVariations = symbolVariationsOf(symbolMap);
                final Typeface fallbackTypeface = symbolTypeface == null
                    ? fallbackTypefaceFor(codePoint, cellBold, cellItalic) : null;
                configureFont(cellBold, cellItalic, symbolTypeface, fallbackTypeface,
                    symbolVariations);
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (symbolTypeface == null
                    && fallbackTypeface == null
                    && codePoint < mAsciiMeasures[faceStyle].length)
                    ? mAsciiMeasures[faceStyle][codePoint]
                    : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;
                // Kitty's private-use expansion (its fonts.c): a PUA symbol whose own glyph is
                // wider than one cell, and which is followed by blanks that paint the same, is
                // drawn across those cells instead of being squeezed into a single narrow square.
                // The blanks are consumed; the background pass already painted their fill.
                final int wantedColumns = symbolTypeface != null && codePointWcWidth == 1
                    && isPrivateUse(codePoint) && !insideCursor && !insideSelection
                    ? symbolExpansionColumns(measuredCodePointWidth, mFontWidth,
                        mSymbolExpansion.maxColumnsFor(codePoint))
                    : 1;
                if (wantedColumns > 1) {
                    int expandedColumns = 1;
                    int blankIndex = currentCharIndex + charsForCodePoint;
                    while (expandedColumns < wantedColumns && column + expandedColumns < columns
                        && blankIndex < charsUsedInLine
                        && isExpansionBlank(line[blankIndex])
                        && lineObject.getDisplayWidthAt(blankIndex) == 1
                        && cursorX != column + expandedColumns
                        && !(column + expandedColumns >= selx1
                            && column + expandedColumns <= selx2)
                        && blankCellPaintsAlike(style,
                            lineObject.getStyle(column + expandedColumns),
                            palette, boldWithBright, reverseVideo)
                        && (rowHasDecorationColors
                            ? lineObject.getDecorationColor(column + expandedColumns)
                            : TextStyle.DECORATION_COLOR_DEFAULT) == decorationColor
                        && (rowHasHyperlinks
                            ? lineObject.getHyperlinkId(column + expandedColumns) : 0)
                            == hyperlinkId) {
                        expandedColumns++;
                        blankIndex++;
                    }
                    if (expandedColumns > 1) {
                        if (column > 0 && column != lastRunStartColumn) {
                            final int columnWidthSinceLastRun = column - lastRunStartColumn;
                            final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                            int runCursorColor = lastRunInsideCursor
                                ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                            boolean invertRunTextColor = lastRunInsideCursor
                                && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
                            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                                columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                                measuredWidthForRun, runCursorColor, cursorShape, lastRunStyle,
                                boldWithBright, reverseVideo || invertRunTextColor
                                    || lastRunInsideSelection,
                                horizontalOffset, lastRunDecorationColor,
                                lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface,
                                lastRunFallbackTypeface, lastRunSymbolFeatures,
                                lastRunSymbolVariations, false);
                        }
                        drawTextRun(canvas, line, palette, heightOffset, column, expandedColumns,
                            currentCharIndex, charsForCodePoint, measuredCodePointWidth, 0,
                            cursorShape, style, boldWithBright, reverseVideo, horizontalOffset,
                            decorationColor, hyperlinkId != 0, 0, symbolTypeface, null,
                            symbolFeatures, symbolVariations, false);
                        column += expandedColumns;
                        currentCharIndex = blankIndex;
                        while (currentCharIndex < charsUsedInLine
                            && lineObject.getDisplayWidthAt(currentCharIndex) <= 0) {
                            currentCharIndex +=
                                Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                        }
                        startFreshRun = true;
                        continue;
                    }
                }
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor
                    || insideSelection != lastRunInsideSelection || fontWidthMismatch
                    || lastRunFontWidthMismatch || decorationColor != lastRunDecorationColor
                    || hyperlinkId != lastRunHyperlinkId
                    || symbolTypeface != lastRunSymbolTypeface
                    || fallbackTypeface != lastRunFallbackTypeface
                    || !sameSymbolSettings(symbolFeatures, symbolVariations,
                        lastRunSymbolFeatures, lastRunSymbolVariations)) {
                    if (column == 0 || column == lastRunStartColumn) {
                        // Skip first column as there is nothing to draw, just record the current style.
                    } else {
                        final int columnWidthSinceLastRun = column - lastRunStartColumn;
                        final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                        int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                        boolean invertCursorTextColor = false;
                        if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                            invertCursorTextColor = true;
                        }
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                            columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                            measuredWidthForRun, cursorColor, cursorShape, lastRunStyle,
                            boldWithBright, reverseVideo || invertCursorTextColor
                                || lastRunInsideSelection,
                            horizontalOffset, lastRunDecorationColor,
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface,
                            lastRunFallbackTypeface, lastRunSymbolFeatures,
                            lastRunSymbolVariations, false);
                    }
                    measuredWidthForRun = 0.f;
                    lastRunStyle = style;
                    lastRunInsideCursor = insideCursor;
                    lastRunInsideSelection = insideSelection;
                    lastRunStartColumn = column;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = fontWidthMismatch;
                    lastRunDecorationColor = decorationColor;
                    lastRunHyperlinkId = hyperlinkId;
                    lastRunSymbolTypeface = symbolTypeface;
                    lastRunFallbackTypeface = fallbackTypeface;
                    lastRunSymbolFeatures = symbolFeatures;
                    lastRunSymbolVariations = symbolVariations;
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine
                    && lineObject.getDisplayWidthAt(currentCharIndex) <= 0) {
                    // Eat combining chars so that they are treated as part of the last non-combining code point,
                    // instead of e.g. being considered inside the cursor in the next run.
                    currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
                }
            }
            // A row that ends on one of those cells has already flushed everything before it.
            if (!startFreshRun) {
                final int columnWidthSinceLastRun = columns - lastRunStartColumn;
                final int charsSinceLastRun = currentCharIndex - lastRunStartIndex;
                int cursorColor = lastRunInsideCursor ? mEmulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] : 0;
                boolean invertCursorTextColor = false;
                if (lastRunInsideCursor && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK) {
                    invertCursorTextColor = true;
                }
                drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn,
                    columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun,
                    measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright,
                    reverseVideo || invertCursorTextColor || lastRunInsideSelection,
                    horizontalOffset, lastRunDecorationColor, lastRunHyperlinkId != 0, 0,
                    lastRunSymbolTypeface, lastRunFallbackTypeface, lastRunSymbolFeatures,
                    lastRunSymbolVariations, false);
            }
        }
        drawExtraCursors(mEmulator, canvas, screen, palette, topRow, endRow, boldWithBright, reverseVideo, horizontalOffset);
    }

    /** Draw the image slice addressed by one placeholder cell, clipped to that cell. */
    private void drawKittyPlaceholderCell(Canvas canvas, KittyUnicodePlaceholder.Cell cell,
                                          int screenColumn, float bottom,
                                          float horizontalOffset) {
        int imageColumns = mKittyPlaceholder.columns > 0 ? mKittyPlaceholder.columns
            : Math.max(1, (int) Math.ceil(mKittyPlaceholder.sourceWidth / mFontWidth));
        int imageRows = mKittyPlaceholder.rows > 0 ? mKittyPlaceholder.rows
            : Math.max(1, (int) Math.ceil((double) mKittyPlaceholder.sourceHeight
                / mFontLineSpacing));
        if (cell.column >= imageColumns || cell.row >= imageRows) return;

        float boxWidth = imageColumns * mFontWidth;
        float boxHeight = imageRows * mFontLineSpacing;
        float scale = Math.min(boxWidth / mKittyPlaceholder.sourceWidth,
            boxHeight / mKittyPlaceholder.sourceHeight);
        float drawnWidth = mKittyPlaceholder.sourceWidth * scale;
        float drawnHeight = mKittyPlaceholder.sourceHeight * scale;
        float boxLeft = horizontalOffset + (screenColumn - cell.column) * mFontWidth;
        float boxTop = bottom - mFontLineSpacing - cell.row * mFontLineSpacing;
        mKittyDestRect.set(boxLeft + (boxWidth - drawnWidth) / 2f,
            boxTop + (boxHeight - drawnHeight) / 2f,
            boxLeft + (boxWidth + drawnWidth) / 2f,
            boxTop + (boxHeight + drawnHeight) / 2f);
        mKittyCellRect.set(horizontalOffset + screenColumn * mFontWidth,
            bottom - mFontLineSpacing,
            horizontalOffset + (screenColumn + 1) * mFontWidth, bottom);
        mKittySourceRect.set(mKittyPlaceholder.sourceX, mKittyPlaceholder.sourceY,
            mKittyPlaceholder.sourceX + mKittyPlaceholder.sourceWidth,
            mKittyPlaceholder.sourceY + mKittyPlaceholder.sourceHeight);
        canvas.save();
        canvas.clipRect(mKittyCellRect);
        canvas.drawBitmap(mKittyPlaceholder.bitmap, mKittySourceRect, mKittyDestRect,
            mKittyImagePaint);
        canvas.restore();
    }

    /** Kitty specifies that the cursor is drawn over placeholder images. */
    private void drawPlaceholderCursor(Canvas canvas, int column, float bottom,
                                       float horizontalOffset, int cursorShape,
                                       int cursorColor) {
        float left = horizontalOffset + column * mFontWidth;
        float right = left + mFontWidth;
        float top = bottom - mFontLineSpacing;
        if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE)
            top = bottom - mFontLineSpacing / 4f;
        else if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
            right = left + mFontWidth / 4f;
        mTextPaint.setColor(cursorColor);
        canvas.drawRect(left, top, right, bottom, mTextPaint);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn,
                             int runWidthColumns, int startCharIndex, int runWidthChars, float mes,
                             int cursor, int cursorStyle, long textStyle, boolean boldWithBright,
                             boolean reverseVideo, float horizontalOffset, int decorationColor,
                             boolean hyperlink, int foregroundOverride,
                             @Nullable Typeface symbolTypeface, boolean drawBackgroundAndCursor) {
        drawTextRun(canvas, text, palette, y, startColumn, runWidthColumns, startCharIndex,
            runWidthChars, mes, cursor, cursorStyle, textStyle, boldWithBright, reverseVideo,
            horizontalOffset, decorationColor, hyperlink, foregroundOverride, symbolTypeface, null,
            drawBackgroundAndCursor);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn,
                             int runWidthColumns, int startCharIndex, int runWidthChars, float mes,
                             int cursor, int cursorStyle, long textStyle, boolean boldWithBright,
                             boolean reverseVideo, float horizontalOffset, int decorationColor,
                             boolean hyperlink, int foregroundOverride,
                             @Nullable Typeface symbolTypeface,
                             @Nullable Typeface fallbackTypeface,
                             boolean drawBackgroundAndCursor) {
        // Callers with no matched map behind them keep the shared symbols slot they always used.
        drawTextRun(canvas, text, palette, y, startColumn, runWidthColumns, startCharIndex,
            runWidthChars, mes, cursor, cursorStyle, textStyle, boldWithBright, reverseVideo,
            horizontalOffset, decorationColor, hyperlink, foregroundOverride, symbolTypeface,
            fallbackTypeface, symbolTypeface == null ? null : mFontFeatures.symbols,
            symbolTypeface == null ? null : mFontVariations.symbols, drawBackgroundAndCursor);
    }

    /**
     * Draw one run, using the settings the run's matched {@code symbol_map} resolved to.
     *
     * <p>{@code symbolFeatures} and {@code symbolVariations} are the already-resolved settings of
     * the map that matched the run's cells — see {@link #symbolSetting(String, String)} — and are
     * only consulted when {@code symbolTypeface} is non-null.
     */
    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn,
                             int runWidthColumns, int startCharIndex, int runWidthChars, float mes,
                             int cursor, int cursorStyle, long textStyle, boolean boldWithBright,
                             boolean reverseVideo, float horizontalOffset, int decorationColor,
                             boolean hyperlink, int foregroundOverride,
                             @Nullable Typeface symbolTypeface,
                             @Nullable Typeface fallbackTypeface,
                             @Nullable String symbolFeatures,
                             @Nullable String symbolVariations,
                             boolean drawBackgroundAndCursor) {
        boolean disableLigatures = disablesLigatures(mLigaturePolicy, cursor != 0);
        int effect = TextStyle.decodeEffect(textStyle);
        boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
            | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        String previousFeatures = mTextPaint.getFontFeatureSettings();
        String runFeatures = symbolTypeface != null ? symbolFeatures
            : mFontFeatures.forRun(bold, italic, false);
        if (disableLigatures) {
            runFeatures = runFeatures == null || runFeatures.isEmpty()
                ? "'calt' 0" : runFeatures + ", 'calt' 0";
        }
        boolean changedFeatures = !sameString(previousFeatures, runFeatures);
        if (changedFeatures) mTextPaint.setFontFeatureSettings(runFeatures);
        // Axes are carried by the run's typeface (see variationTypeface), not set on the Paint.
        try {
            drawTextRunConfigured(canvas, text, palette, y, startColumn, runWidthColumns,
                startCharIndex, runWidthChars, mes, cursor, cursorStyle, textStyle,
                boldWithBright, reverseVideo, horizontalOffset, decorationColor, hyperlink,
                foregroundOverride, symbolTypeface, fallbackTypeface, symbolVariations,
                drawBackgroundAndCursor);
        } finally {
            if (changedFeatures) mTextPaint.setFontFeatureSettings(previousFeatures);
        }
    }

    static boolean disablesLigatures(LigaturePolicy policy, boolean cursorRun) {
        return policy == LigaturePolicy.ALWAYS
            || (policy == LigaturePolicy.CURSOR && cursorRun);
    }

    private static boolean sameString(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
    }

    /**
     * The setting a symbol run draws with: the matched map's own when it declares one, otherwise
     * the shared {@code symbols} slot every symbol run used to be stuck with.
     */
    @Nullable
    static String symbolSetting(@Nullable String mapOwn, @Nullable String sharedSymbols) {
        return mapOwn == null || mapOwn.isEmpty() ? sharedSymbols : mapOwn;
    }

    /**
     * Whether two adjacent symbol cells resolve to the same shaping settings, so one run may span
     * both. Two maps can name one typeface and still declare different settings, so the run's
     * typeface alone cannot decide this: without the settings in the comparison the second map's
     * cells would be drawn with the first map's settings.
     */
    static boolean sameSymbolSettings(@Nullable String featuresA, @Nullable String variationsA,
                                      @Nullable String featuresB, @Nullable String variationsB) {
        return sameString(featuresA, featuresB) && sameString(variationsA, variationsB);
    }

    /** The resolved feature settings of a symbol run, or null when no map matched the cell. */
    @Nullable
    private String symbolFeaturesOf(@Nullable SymbolMap map) {
        return map == null ? null : symbolSetting(map.features, mFontFeatures.symbols);
    }

    /** The resolved axis settings of a symbol run, or null when no map matched the cell. */
    @Nullable
    private String symbolVariationsOf(@Nullable SymbolMap map) {
        return map == null ? null : symbolSetting(map.variations, mFontVariations.symbols);
    }

    private void drawTextRunConfigured(Canvas canvas, char[] text, int[] palette, float y,
                                       int startColumn, int runWidthColumns, int startCharIndex,
                                       int runWidthChars, float mes, int cursor, int cursorStyle,
                                       long textStyle, boolean boldWithBright, boolean reverseVideo,
                                       float horizontalOffset, int decorationColor,
                                       boolean hyperlink, int foregroundOverride,
                                       @Nullable Typeface symbolTypeface,
                                       @Nullable Typeface fallbackTypeface,
                                       @Nullable String symbolVariations,
                                       boolean drawBackgroundAndCursor) {
        final int effect = TextStyle.decodeEffect(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;
        // The regular face defines the terminal grid. Variant faces are scaled into those same
        // cells instead of moving later columns when their native metrics differ.
        final float fontWidth = mFontWidth;
        final int fontLineSpacing = mFontLineSpacing;
        final int fontBaselineDescent = mFontBaselineDescent;
        configureFont(bold, italic, symbolTypeface, fallbackTypeface, symbolVariations);
        // Measure the same shaped run that Canvas will draw. Per-code-point measureText() cannot
        // account for ligatures, Indic conjuncts, Arabic joining, or ZWJ emoji continuations.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            mes = mTextPaint.getTextRunAdvances(text, startCharIndex, runWidthChars,
                startCharIndex, runWidthChars, false, null, 0);
        } else {
            // Pre-29 the char[] overload is a non-SDK interface; getRunAdvance (API 23) measures
            // the same shaped run when no per-glyph advances are needed.
            mes = mTextPaint.getRunAdvance(text, startCharIndex, startCharIndex + runWidthChars,
                startCharIndex, startCharIndex + runWidthChars, false,
                startCharIndex + runWidthChars);
        }
        if (!(mes > 0f)) mes = runWidthColumns * fontWidth;
        final long resolvedColors = resolveRunColors(textStyle, palette, boldWithBright, reverseVideo);
        int foreColor = (int) (resolvedColors >>> 32);
        int backColor = (int) resolvedColors;
        if (foregroundOverride != 0)
            foreColor = foregroundOverride;
        float left = horizontalOffset + startColumn * fontWidth;
        float right = left + runWidthColumns * fontWidth;
        float glyphLeft = left;
        float glyphBaseline = y - fontBaselineDescent;
        boolean savedMatrix = false;
        boolean scaledTextSize = false;
        if (symbolTypeface != null) {
            // A symbol face has its own advance and vertical metrics, and squeezing its glyphs on
            // one axis into the cells wcwidth() granted distorts them. The run is instead scaled
            // uniformly — up or down — until its line box meets the cell box, and centered in its
            // cells horizontally on its measured advance and vertically by that line box, so an
            // icon is as large as its cells allow and sits level with its neighbours instead of
            // riding the primary face's baseline.
            final float runWidth = runWidthColumns * fontWidth;
            mTextPaint.getFontMetrics(mSymbolFontMetrics);
            float lineBox = mSymbolFontMetrics.descent - mSymbolFontMetrics.ascent;
            float ascent = mSymbolFontMetrics.ascent;
            final float scale = Math.min(mes > 0f ? runWidth / mes : 1f,
                lineBox > 0f ? fontLineSpacing / lineBox : 1f);
            if (scale != 1f) {
                mTextPaint.setTextSize(mTextSize * scale);
                scaledTextSize = true;
                mes *= scale;
                lineBox *= scale;
                ascent *= scale;
            }
            glyphLeft = left + (runWidth - mes) / 2f;
            glyphBaseline = y - fontLineSpacing + (fontLineSpacing - lineBox) / 2f - ascent;
        } else {
            mes = mes / fontWidth;
            if (Math.abs(mes - runWidthColumns) > 0.01) {
                canvas.save();
                canvas.scale(runWidthColumns / mes, 1.f);
                left *= mes / runWidthColumns;
                right *= mes / runWidthColumns;
                glyphLeft = left;
                savedMatrix = true;
            }
        }
        if (drawBackgroundAndCursor) {
            // The normal screen paints backgrounds and the cursor in a separate pass before any
            // glyph (see render()); only the extra-cursor overlay still draws them per run.
            if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
                // Only draw non-default background.
                mTextPaint.setColor(backColor);
                canvas.drawRect(left, y - fontLineSpacing, right, y, mTextPaint);
            }
            if (cursor != 0) {
                mTextPaint.setColor(cursor);
                float cursorHeight = fontLineSpacing;
                if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE)
                    cursorHeight /= 4.;
                else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
                    right -= ((right - left) * 3) / 4.;
                canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
            }
        }
        if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
            if (dim) {
                foreColor = dimColor(foreColor);
            }
            // Underlines are drawn as geometry below, since Paint only knows one straight variant.
            mTextPaint.setUnderlineText(false);
            mTextPaint.setStrikeThruText(false);
            mTextPaint.setColor(foreColor);
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars,
                glyphLeft, glyphBaseline, false, mTextPaint);
            int underlineStyle = TextStyle.decodeUnderlineStyle(textStyle);
            if (underlineStyle == TextStyle.UNDERLINE_STYLE_NONE && underline) {
                // The attribute bit without a style: DECCARA, or a style set before this fork stored one.
                underlineStyle = TextStyle.UNDERLINE_STYLE_SINGLE;
            }
            if (underlineStyle == TextStyle.UNDERLINE_STYLE_NONE && hyperlink) {
                // An OSC 8 link is underlined so that it is discoverable, since touch has no hover.
                underlineStyle = TextStyle.UNDERLINE_STYLE_SINGLE;
            }
            if (underlineStyle != TextStyle.UNDERLINE_STYLE_NONE) {
                int lineColor = foreColor;
                if (decorationColor != TextStyle.DECORATION_COLOR_DEFAULT) {
                    lineColor = ((decorationColor & 0xff000000) == 0xff000000) ? decorationColor : palette[decorationColor];
                }
                drawUnderline(canvas, left, right, y, fontBaselineDescent, underlineStyle, lineColor);
            }
            if (strikeThrough)
                drawStrikethrough(canvas, left, right, y, foreColor);
        }
        if (scaledTextSize)
            mTextPaint.setTextSize(mTextSize);
        if (savedMatrix)
            canvas.restore();
    }

    /**
     * Resolve a cell's final foreground and background colour — palette lookup, bold-is-bright,
     * and reverse video — packed as {@code (foreground << 32) | background}. The single source of
     * truth for both the background pass and the glyph pass, so the two cannot disagree.
     */
    /**
     * Whether a blank cell carrying {@code blank} paints exactly what a blank cell carrying
     * {@code symbol} would: the background, plus any underline or strikethrough drawn across it.
     *
     * A space shows nothing of its foreground colour or its face, so comparing whole styles — as
     * the private-use expansion first did — refused the common case of a coloured icon followed by
     * an uncoloured separator, and every such icon stayed squeezed into one narrow cell. What must
     * still match is anything the expanded run would then draw differently over the second cell.
     */
    static boolean blankCellPaintsAlike(long symbol, long blank, int[] palette,
                                        boolean boldWithBright, boolean reverseVideo) {
        if ((int) resolveRunColors(symbol, palette, boldWithBright, reverseVideo)
            != (int) resolveRunColors(blank, palette, boldWithBright, reverseVideo)) return false;
        if (TextStyle.decodeUnderlineStyle(symbol) != TextStyle.decodeUnderlineStyle(blank))
            return false;
        final int decorations = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
            | TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
        return (TextStyle.decodeEffect(symbol) & decorations)
            == (TextStyle.decodeEffect(blank) & decorations);
    }

    /**
     * How many cells a private-use symbol wants, from its own advance: kitty's
     * {@code ceil(glyph_width / cell_width)}, bounded by a {@code narrow_symbols} ceiling and by
     * {@link #MAX_SYMBOL_EXPANSION_COLUMNS}. A glyph that already fits its cell wants exactly one,
     * so it is neither grown nor re-centred over a neighbour it does not need.
     */
    static int symbolExpansionColumns(float advance, float cellWidth, int cap) {
        if (!(cellWidth > 0f) || !(advance > cellWidth * 1.01f)) return 1;
        final int wanted = (int) Math.ceil(advance / cellWidth - 0.01f);
        return Math.max(1, Math.min(Math.min(wanted, cap), MAX_SYMBOL_EXPANSION_COLUMNS));
    }

    /** The blanks a symbol may spread into: a space, or the en-space kitty also accepts. */
    static boolean isExpansionBlank(char c) {
        return c == ' ' || c == '\u2002';
    }

    static long resolveRunColors(long textStyle, int[] palette, boolean boldWithBright, boolean reverseVideo) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        if ((foreColor & 0xff000000) != 0xff000000) {
            // If enabled, let bold have bright colors if applicable (one of the first 8):
            if (boldWithBright && bold && foreColor >= 0 && foreColor < 8)
                foreColor += 8;
            foreColor = palette[foreColor];
        }
        if ((backColor & 0xff000000) != 0xff000000) {
            backColor = palette[backColor];
        }
        // Reverse video here if _one and only one_ of the reverse flags are set:
        if (reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0) {
            final int swap = foreColor;
            foreColor = backColor;
            backColor = swap;
        }
        return ((long) foreColor << 32) | (backColor & 0xffffffffL);
    }

    /**
     * Dim color handling used by libvte which in turn took it from xterm
     * (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267).
     */
    private static int dimColor(int color) {
        final int red = (0xFF & (color >> 16)) * 2 / 3;
        final int green = (0xFF & (color >> 8)) * 2 / 3;
        final int blue = (0xFF & color) * 2 / 3;
        return 0xFF000000 + (red << 16) + (green << 8) + blue;
    }

    /**
     * The final ink colour of one cell, so that a synthesized glyph inverts under a block cursor or
     * inside a selection exactly as the text around it does.
     */
    private static int resolveInkColor(long textStyle, int[] palette, boolean boldWithBright,
                                       boolean reverseVideo) {
        final int foreColor = (int) (resolveRunColors(textStyle, palette, boldWithBright,
            reverseVideo) >>> 32);
        return (TextStyle.decodeEffect(textStyle) & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0
            ? dimColor(foreColor) : foreColor;
    }

    /**
     * Paint one row's cell backgrounds and its cursor block before any glyph is drawn, so glyph
     * ink that overhangs its cells is drawn over a neighbour's fill rather than clipped under it.
     * Rectangles are cell-aligned, so no font configuration or measurement happens here; the walk
     * mirrors the run segmentation in {@link #render} so both passes agree on every cell's style.
     */
    private void drawRowBackgroundAndCursor(Canvas canvas, TerminalRow lineObject, int[] palette,
                                            float y, int columns, int cursorX, int cursorShape,
                                            int selx1, int selx2, boolean boldWithBright,
                                            boolean reverseVideo, float horizontalOffset,
                                            int cursorColor) {
        final char[] line = lineObject.mText;
        final int charsUsedInLine = lineObject.getSpaceUsed();
        final int defaultBackColor = palette[TextStyle.COLOR_INDEX_BACKGROUND];
        final float top = y - mFontLineSpacing;
        int pendingStartColumn = -1;
        int pendingEndColumn = -1;
        int pendingColor = 0;
        int currentCharIndex = 0;
        for (int column = 0; column < columns; ) {
            final char charAtIndex = line[currentCharIndex];
            final int charsForCodePoint = Character.isHighSurrogate(charAtIndex) ? 2 : 1;
            final long style = lineObject.getStyle(column);
            if (TextStyle.isBitmap(style)) {
                if (pendingStartColumn != -1) {
                    drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor, mTextPaint);
                    pendingStartColumn = -1;
                }
                column += 1;
                currentCharIndex += charsForCodePoint;
                continue;
            }
            final int codePointWcWidth = lineObject.getDisplayWidthAt(currentCharIndex);
            final int cellColumns = Math.max(1, codePointWcWidth);
            final boolean insideCursor = cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1);
            final boolean insideSelection = column >= selx1 && column <= selx2;
            final boolean invertCursorTextColor = insideCursor
                && cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            final int backColor = (int) resolveRunColors(style, palette, boldWithBright,
                reverseVideo || invertCursorTextColor || insideSelection);
            if (insideCursor) {
                if (pendingStartColumn != -1) {
                    drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor, mTextPaint);
                    pendingStartColumn = -1;
                }
                if (backColor != defaultBackColor)
                    drawCellRect(canvas, column, column + cellColumns, top, y, horizontalOffset, backColor, mTextPaint);
                float left = horizontalOffset + column * mFontWidth;
                float right = left + cellColumns * mFontWidth;
                float cursorTop = top;
                if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE)
                    cursorTop = y - mFontLineSpacing / 4f;
                else if (cursorShape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
                    right -= ((right - left) * 3) / 4f;
                mTextPaint.setColor(cursorColor);
                canvas.drawRect(left, cursorTop, right, y, mTextPaint);
            } else if (backColor != defaultBackColor) {
                if (pendingStartColumn != -1 && pendingColor == backColor) {
                    pendingEndColumn = column + cellColumns;
                } else {
                    if (pendingStartColumn != -1)
                        drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor, mTextPaint);
                    pendingStartColumn = column;
                    pendingEndColumn = column + cellColumns;
                    pendingColor = backColor;
                }
            } else if (pendingStartColumn != -1) {
                drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor, mTextPaint);
                pendingStartColumn = -1;
            }
            column += codePointWcWidth;
            currentCharIndex += charsForCodePoint;
            while (currentCharIndex < charsUsedInLine
                && lineObject.getDisplayWidthAt(currentCharIndex) <= 0) {
                currentCharIndex += Character.isHighSurrogate(line[currentCharIndex]) ? 2 : 1;
            }
        }
        if (pendingStartColumn != -1)
            drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor, mTextPaint);
    }

    /**
     * Draw one cell of box drawing, blocks, shades, braille, legacy eighths or Powerline geometry.
     *
     * <p>Only foreground ink is painted: the cell's background, its selection fill and the cursor
     * block were all laid down by the pass in {@link #render}. The cell's pixel bounds come from
     * {@link BoxGeometry#edge}, so this cell's edges are the same integers its neighbours computed
     * and a rule drawn across the screen has no seams in it.
     */
    private void drawSynthesizedCell(Canvas canvas, int codePoint, int column, int columns,
                                     float heightOffset, float horizontalOffset, int color) {
        final int cellLeft = BoxGeometry.edge(horizontalOffset, mFontWidth, column);
        final int cellRight = BoxGeometry.edge(horizontalOffset, mFontWidth, column + columns);
        final int cellBottom = Math.round(heightOffset);
        final int cellTop = cellBottom - mFontLineSpacing;
        final BoxGeometry.Segments segments = mBoxSegments;
        if (!BoxGeometry.fill(codePoint, cellLeft, cellTop, cellRight, cellBottom,
            mBoxDrawingPolicy.thicknessScales, mBoxDrawingPolicy.powerline, segments))
            return;
        mTextPaint.setColor(color);
        for (int i = 0; i < segments.rectCount; i++) {
            final int offset = i * 4;
            canvas.drawRect(segments.rects[offset], segments.rects[offset + 1],
                segments.rects[offset + 2], segments.rects[offset + 3], mTextPaint);
        }
        for (int i = 0; i < segments.dashCount; i++) {
            final int offset = i * 6;
            final int left = segments.dashRuns[offset];
            final int top = segments.dashRuns[offset + 1];
            final int right = segments.dashRuns[offset + 2];
            final int bottom = segments.dashRuns[offset + 3];
            final int period = segments.dashRuns[offset + 4];
            final int onLength = segments.dashRuns[offset + 5];
            final boolean horizontal = (right - left) >= (bottom - top);
            final int end = horizontal ? right : bottom;
            for (int at = horizontal ? left : top; at < end; at += period) {
                final int stop = Math.min(at + onLength, end);
                if (horizontal) canvas.drawRect(at, top, stop, bottom, mTextPaint);
                else canvas.drawRect(left, at, right, stop, mTextPaint);
            }
        }
        for (int i = 0; i < segments.dotCount; i++) {
            canvas.drawCircle(segments.dots[i * 2], segments.dots[i * 2 + 1], segments.dotRadius,
                mTextPaint);
        }
        int polygonPoint = 0;
        for (int i = 0; i < segments.polygonCount; i++) {
            final int vertices = segments.polygonSizes[i];
            mBoxPath.rewind();
            mBoxPath.moveTo(segments.polygonPoints[polygonPoint],
                segments.polygonPoints[polygonPoint + 1]);
            for (int vertex = 1; vertex < vertices; vertex++) {
                mBoxPath.lineTo(segments.polygonPoints[polygonPoint + vertex * 2],
                    segments.polygonPoints[polygonPoint + vertex * 2 + 1]);
            }
            mBoxPath.close();
            canvas.drawPath(mBoxPath, mTextPaint);
            polygonPoint += vertices * 2;
        }
        if (segments.capFilled) {
            for (int i = 0; i < segments.capCount; i++) {
                final int offset = i * 6;
                mBoxOval.set(segments.caps[offset], segments.caps[offset + 1],
                    segments.caps[offset + 2], segments.caps[offset + 3]);
                canvas.drawArc(mBoxOval, segments.caps[offset + 4], segments.caps[offset + 5],
                    true, mTextPaint);
            }
        }
        if (segments.diagonalCount > 0 || segments.arcCount > 0
            || (segments.capCount > 0 && !segments.capFilled)) {
            mTextPaint.setStyle(Paint.Style.STROKE);
            mTextPaint.setStrokeWidth(segments.strokeThickness);
            for (int i = 0; i < segments.diagonalCount; i++) {
                final int offset = i * 4;
                canvas.drawLine(segments.diagonals[offset], segments.diagonals[offset + 1],
                    segments.diagonals[offset + 2], segments.diagonals[offset + 3], mTextPaint);
            }
            for (int i = 0; i < segments.arcCount; i++) {
                final int offset = i * 6;
                mBoxPath.rewind();
                mBoxPath.moveTo(segments.arcs[offset], segments.arcs[offset + 1]);
                mBoxPath.quadTo(segments.arcs[offset + 2], segments.arcs[offset + 3],
                    segments.arcs[offset + 4], segments.arcs[offset + 5]);
                canvas.drawPath(mBoxPath, mTextPaint);
            }
            if (!segments.capFilled) {
                for (int i = 0; i < segments.capCount; i++) {
                    final int offset = i * 6;
                    mBoxOval.set(segments.caps[offset], segments.caps[offset + 1],
                        segments.caps[offset + 2], segments.caps[offset + 3]);
                    canvas.drawArc(mBoxOval, segments.caps[offset + 4], segments.caps[offset + 5],
                        false, mTextPaint);
                }
            }
            mTextPaint.setStyle(Paint.Style.FILL);
            mTextPaint.setStrokeWidth(0f);
        }
        for (int i = 0; i < segments.shadeCount; i++) {
            final int offset = i * 4;
            // The shade is the foreground at a reduced alpha, blended over whatever the background
            // pass already painted, so no second colour has to be resolved here.
            mTextPaint.setColor((segments.shadeAlphaPercents[i] * 255 / 100) << 24
                | (color & 0x00ffffff));
            canvas.drawRect(segments.shadeRects[offset], segments.shadeRects[offset + 1],
                segments.shadeRects[offset + 2], segments.shadeRects[offset + 3], mTextPaint);
        }
    }

    /**
     * Paints a find session over the transcript that has just been rendered: the selection under
     * everything, then every match with the current one lit brighter, then the copy-mode cursor.
     *
     * <p>It runs as its own pass rather than through the selection channel the screen render
     * already has, because that channel carries exactly one range and inverts the cells it covers.
     * A find highlight has to be able to cover dozens of ranges at once and must stay readable, so
     * these are translucent fills laid over the glyphs, in the row geometry the render loop uses.</p>
     */
    public final void renderFindOverlay(TerminalEmulator emulator, Canvas canvas, int topRow,
                                        TerminalFindOverlay overlay, float horizontalOffset,
                                        int extraRows) {
        if (emulator == null || overlay == null || overlay.isEmpty()) return;
        final int endRow = Math.min(topRow + emulator.mRows + extraRows, emulator.mRows);
        final int columns = emulator.mColumns;
        // Same origin as the screen render's first row, so a highlight lands on its own cells.
        final float firstRowTop = mFontLineSpacingAndAscent;

        if (overlay.selectionMode != TerminalFindOverlay.SELECTION_NONE && overlay.cursorVisible) {
            int first = Math.min(overlay.anchorRow, overlay.cursorRow);
            int last = Math.max(overlay.anchorRow, overlay.cursorRow);
            for (int row = Math.max(topRow, first); row <= Math.min(endRow - 1, last); row++) {
                int start;
                int end;
                switch (overlay.selectionMode) {
                    case TerminalFindOverlay.SELECTION_LINE:
                        start = 0;
                        end = columns - 1;
                        break;
                    case TerminalFindOverlay.SELECTION_BLOCK:
                        start = Math.min(overlay.anchorColumn, overlay.cursorColumn);
                        end = Math.max(overlay.anchorColumn, overlay.cursorColumn);
                        break;
                    default:
                        // Charwise runs from the anchor to the cursor, whole rows in between.
                        boolean forward = overlay.cursorRow > overlay.anchorRow
                            || (overlay.cursorRow == overlay.anchorRow
                            && overlay.cursorColumn >= overlay.anchorColumn);
                        int startRow = forward ? overlay.anchorRow : overlay.cursorRow;
                        int startCol = forward ? overlay.anchorColumn : overlay.cursorColumn;
                        int endRowSel = forward ? overlay.cursorRow : overlay.anchorRow;
                        int endCol = forward ? overlay.cursorColumn : overlay.anchorColumn;
                        start = row == startRow ? startCol : 0;
                        end = row == endRowSel ? endCol : columns - 1;
                        break;
                }
                drawOverlayRect(canvas, row, topRow, firstRowTop, start, end, horizontalOffset,
                    overlay.selectionColor);
            }
        }

        for (int i = 0; i < overlay.spans.size(); i++) {
            TerminalFindOverlay.Span span = overlay.spans.get(i);
            if (span.row < topRow || span.row >= endRow) continue;
            drawOverlayRect(canvas, span.row, topRow, firstRowTop, span.startColumn, span.endColumn,
                horizontalOffset, i == overlay.currentSpan ? overlay.currentMatchColor
                    : overlay.matchColor);
        }

        if (overlay.cursorVisible && overlay.cursorRow >= topRow && overlay.cursorRow < endRow) {
            drawOverlayRect(canvas, overlay.cursorRow, topRow, firstRowTop, overlay.cursorColumn,
                overlay.cursorColumn, horizontalOffset, overlay.cursorColor);
        }
    }

    /** One overlay span over inclusive columns, with the same pixel snapping as cell backgrounds. */
    private void drawOverlayRect(Canvas canvas, int row, int topRow, float firstRowTop,
                                 int startColumn, int endColumn, float horizontalOffset, int color) {
        if (endColumn < startColumn) return;
        float top = firstRowTop + (row - topRow) * mFontLineSpacing;
        // Its own paint: the text paint carries per-run typeface, skew and effects, and this pass
        // runs between frames of that state rather than inside it.
        drawCellRect(canvas, startColumn, endColumn + 1, top, top + mFontLineSpacing,
            horizontalOffset, color, mOverlayPaint);
    }

    /**
     * One run's cell background, or an overlay span, snapped to the pixel grid.
     *
     * <p>Cell edges land on fractional pixels at most font sizes, and an anti-aliased rect edge
     * covers its boundary pixel only partly. Two runs meeting there each paint their own half of
     * that pixel, which composites to less than full coverage — over an opaque terminal background
     * that is invisible, but over the glass panes (or any transparent surface) it reads as a thin
     * vertical line between powerline segments, and as a horizontal one between rows. Rounding both
     * edges with the same monotone function makes adjacent runs share one integer boundary, so the
     * fills tile exactly: no gap, no double coverage. This is the same pixel-snapping kitty and
     * alacritty apply to cell backgrounds.
     */
    private void drawCellRect(Canvas canvas, int startColumn, int endColumn, float top, float bottom,
                              float horizontalOffset, int color, Paint paint) {
        paint.setColor(color);
        canvas.drawRect(Math.round(horizontalOffset + startColumn * mFontWidth), Math.round(top),
            Math.round(horizontalOffset + endColumn * mFontWidth), Math.round(bottom), paint);
    }

    /** Draw kitty-protocol cursors after the normal screen, so they do not perturb text run batching. */
    private void drawExtraCursors(TerminalEmulator emulator, Canvas canvas, TerminalBuffer screen, int[] palette,
                                  int topRow, int endRow, boolean boldWithBright, boolean reverseVideo,
                                  float horizontalOffset) {
        TerminalEmulator.ExtraCursor[] cursors = emulator.getExtraCursors();
        if (cursors.length == 0 || !emulator.shouldExtraCursorsBeVisible()) return;

        TerminalEmulator.ExtraCursorColor configuredCursor = emulator.getExtraCursorColor();
        TerminalEmulator.ExtraCursorColor configuredText = emulator.getExtraCursorTextColor();
        for (TerminalEmulator.ExtraCursor cursor : cursors) {
            if (cursor.row < topRow || cursor.row >= endRow || cursor.col < 0 || cursor.col >= emulator.mColumns)
                continue;
            TerminalRow row = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(cursor.row));
            int startColumn = cursor.col;
            int startIndex = row.findStartOfColumn(startColumn);
            if (startColumn > 0 && startIndex == row.findStartOfColumn(startColumn - 1))
                startColumn--;
            startIndex = row.findStartOfColumn(startColumn);
            char[] text = row.mText;
            int codePoint = Character.isHighSurrogate(text[startIndex])
                ? Character.toCodePoint(text[startIndex], text[startIndex + 1]) : text[startIndex];
            int width = Math.max(1, row.getDisplayWidthAt(startIndex));
            int endColumn = Math.min(emulator.mColumns, startColumn + width);
            int endIndex = row.findStartOfColumn(endColumn);
            int chars = Math.max(1, endIndex - startIndex);
            long style = row.getStyle(startColumn);
            int shape = cursor.shape == 29 ? emulator.getCursorStyle() : kittyCursorShape(cursor.shape);
            int cellForeground = resolveCellColor(TextStyle.decodeForeColor(style), palette);
            int cellBackground = resolveCellColor(TextStyle.decodeBackColor(style), palette);
            if (reverseVideo ^ ((TextStyle.decodeEffect(style) & TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0)) {
                int swap = cellForeground;
                cellForeground = cellBackground;
                cellBackground = swap;
            }

            int cursorColor = resolveExtraColor(configuredCursor, palette,
                palette[TextStyle.COLOR_INDEX_CURSOR]);
            int textOverride = 0;
            boolean invertText = shape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
            if (configuredCursor.type == 1) {
                cursorColor = cellForeground;
                textOverride = cellBackground;
                invertText = false;
            } else if (configuredText.type == 1) {
                textOverride = cellBackground;
                invertText = false;
            } else if (configuredText.type == 2 || configuredText.type == 5) {
                textOverride = resolveExtraColor(configuredText, palette, cellBackground);
                invertText = false;
            }

            float y = mFontLineSpacingAndAscent + (cursor.row - topRow + 1) * mFontLineSpacing;
            if (TextStyle.isBitmap(style)) {
                drawCursorShape(canvas, horizontalOffset + startColumn * mFontWidth, y,
                    width * mFontWidth, shape, cursorColor);
                continue;
            }
            int effect = TextStyle.decodeEffect(style);
            boolean cellBold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
                | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
            boolean cellItalic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
            SymbolMap symbolMap = symbolMapFor(codePoint);
            Typeface symbolTypeface = symbolMap == null ? null : symbolMap.typeface;
            if (mBoxDrawingPolicy.synthesizes(codePoint)) {
                // The overlay has to reach the same conclusion as the glyph pass. Drawing this cell
                // as text would stamp the font's idea of the code point — commonly a tofu — on top
                // of the geometry already painted for it.
                int overlayBackground = (int) resolveRunColors(style, palette, boldWithBright,
                    reverseVideo || invertText);
                float left = horizontalOffset + startColumn * mFontWidth;
                if (overlayBackground != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
                    mTextPaint.setColor(overlayBackground);
                    canvas.drawRect(Math.round(left), Math.round(y - mFontLineSpacing),
                        Math.round(left + width * mFontWidth), Math.round(y), mTextPaint);
                }
                drawCursorShape(canvas, left, y, width * mFontWidth, shape, cursorColor);
                if ((effect & TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) == 0) {
                    int ink = (int) (resolveRunColors(style, palette, boldWithBright,
                        reverseVideo || invertText) >>> 32);
                    if (textOverride != 0) ink = textOverride;
                    if ((effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0) ink = dimColor(ink);
                    drawSynthesizedCell(canvas, codePoint, startColumn, width, y, horizontalOffset,
                        ink);
                }
                continue;
            }
            Typeface fallbackTypeface = fallbackTypefaceFor(codePoint, cellBold, cellItalic);
            String symbolFeatures = symbolFeaturesOf(symbolMap);
            String symbolVariations = symbolVariationsOf(symbolMap);
            configureFont(cellBold, cellItalic, symbolTypeface, fallbackTypeface, symbolVariations);
            float measured = mTextPaint.measureText(text, startIndex, chars);
            int decorationColor = row.hasDecorationColors() ? row.getDecorationColor(startColumn)
                : TextStyle.DECORATION_COLOR_DEFAULT;
            boolean hyperlink = row.hasHyperlinks() && row.getHyperlinkId(startColumn) != 0;
            drawTextRun(canvas, text, palette, y, startColumn, width, startIndex, chars, measured,
                cursorColor, shape, style, boldWithBright, reverseVideo || invertText, horizontalOffset,
                decorationColor, hyperlink, textOverride, symbolTypeface, fallbackTypeface,
                symbolFeatures, symbolVariations, true);
        }
    }

    private static int kittyCursorShape(int shape) {
        if (shape == 2) return TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
        if (shape == 3) return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
        return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    }

    /** Select a real face when configured and synthesize only the missing style component. */
    private void configureFont(boolean bold, boolean italic) {
        configureFont(bold, italic, null);
    }

    /**
     * The typeface for {@code base} with {@code variations} applied, instantiated once and reused.
     *
     * <p>A scratch {@link Paint} is the only public way to instantiate a variation typeface from an
     * existing one; after {@code setFontVariationSettings} succeeds, its typeface is the instance.
     * Failures fall back to the un-instanced face, which is what the old inline path did too.
     */
    @Nullable
    private Typeface variationTypeface(@Nullable Typeface base, @Nullable String variations) {
        if (base == null || variations == null || variations.isEmpty()) return base;
        String key = variationKey(base, variations);
        Typeface cached = mVariationTypefaces.get(key);
        if (cached != null) return cached;
        Typeface resolved = base;
        try {
            Paint scratch = new Paint();
            scratch.setTypeface(base);
            if (scratch.setFontVariationSettings(variations)) {
                Typeface instantiated = scratch.getTypeface();
                if (instantiated != null) resolved = instantiated;
            }
        } catch (RuntimeException ignored) {
            // An unsupported axis must never take terminal rendering down; keep the base face.
        }
        mVariationTypefaces.put(key, resolved);
        return resolved;
    }

    /**
     * The {@link #mVariationTypefaces} key of one (face, axes) pair. Both halves matter: two symbol
     * maps naming the same font with different axes are two instances, and one font with the same
     * axes reached from two maps is one instance.
     */
    private static String variationKey(@Nullable Typeface base, @Nullable String variations) {
        return variationKey(System.identityHashCode(base), variations);
    }

    static String variationKey(int baseIdentity, @Nullable String variations) {
        return baseIdentity + "\0" + variations;
    }

    /**
     * The real face an SGR style renders with, before any synthetic bolding or skew. This is the
     * face the fallback chain measures its coverage against, and it mirrors the selection in
     * {@link #configureFont(boolean, boolean, Typeface, Typeface)}.
     */
    private Typeface primaryTypeface(boolean bold, boolean italic) {
        if (bold && italic) {
            if (mBoldItalicTypeface != null) return mBoldItalicTypeface;
            if (mItalicTypeface != null) return mItalicTypeface;
            if (mBoldTypeface != null) return mBoldTypeface;
            return mTypeface;
        }
        if (bold) return mBoldTypeface == null ? mTypeface : mBoldTypeface;
        if (italic) return mItalicTypeface == null ? mTypeface : mItalicTypeface;
        return mTypeface;
    }

    /**
     * The configured fallback face for a code point, or null to leave the run's own face — and
     * after it Android's platform fallback — in place.
     *
     * <p>Resolution is on the cell's base code point, never on a continuation of a cluster, which
     * is the same rule {@code symbol_map} follows: combining marks are eaten into the run their
     * base started, so they inherit that decision instead of making their own.
     */
    @Nullable
    private Typeface fallbackTypefaceFor(int codePoint, boolean bold, boolean italic) {
        if (mFallbackTypefaces.length == 0) return null;
        final int faceStyle = (bold ? 1 : 0) | (italic ? 2 : 0);
        final int index = mFallbackResolver.resolve(faceStyle, codePoint, mCoverage);
        return index == FallbackFontResolver.NO_OVERRIDE ? null : mFallbackTypefaces[index];
    }

    private void configureFont(boolean bold, boolean italic, @Nullable Typeface symbolTypeface) {
        configureFont(bold, italic, symbolTypeface, null);
    }

    private void configureFont(boolean bold, boolean italic, @Nullable Typeface symbolTypeface,
                               @Nullable Typeface fallbackTypeface) {
        configureFont(bold, italic, symbolTypeface, fallbackTypeface,
            symbolTypeface == null ? null : mFontVariations.symbols);
    }

    /**
     * Symbol fonts intentionally ignore SGR face synthesis, matching Kitty's explicit map.
     *
     * <p>{@code symbolVariations} is the resolved axis setting of the map that matched this cell
     * and is only used when {@code symbolTypeface} is non-null; face runs keep taking their axes
     * from {@link #mFontVariations}.
     */
    private void configureFont(boolean bold, boolean italic, @Nullable Typeface symbolTypeface,
                               @Nullable Typeface fallbackTypeface,
                               @Nullable String symbolVariations) {
        Typeface desired;
        boolean fakeBold = false;
        boolean fakeItalic = false;
        if (symbolTypeface != null) {
            desired = symbolTypeface;
        } else if (bold && italic) {
            if (mBoldItalicTypeface != null) {
                desired = mBoldItalicTypeface;
            } else if (mItalicTypeface != null) {
                desired = mItalicTypeface;
                fakeBold = true;
            } else if (mBoldTypeface != null) {
                desired = mBoldTypeface;
                fakeItalic = true;
            } else {
                desired = mTypeface;
                fakeBold = true;
                fakeItalic = true;
            }
        } else if (bold) {
            desired = mBoldTypeface == null ? mTypeface : mBoldTypeface;
            fakeBold = mBoldTypeface == null;
        } else if (italic) {
            desired = mItalicTypeface == null ? mTypeface : mItalicTypeface;
            fakeItalic = mItalicTypeface == null;
        } else {
            desired = mTypeface;
        }
        if (symbolTypeface == null && fallbackTypeface != null) {
            // A chain entry is one face with no declared variants, so the SGR style the primary
            // face would have shown is synthesized on top of it rather than silently dropped.
            desired = fallbackTypeface;
            fakeBold = bold;
            fakeItalic = italic;
        }
        desired = variationTypeface(desired, symbolTypeface != null
            ? symbolVariations : mFontVariations.forRun(bold, italic, false));
        if (desired != mCurrentTypeface) {
            mTextPaint.setTypeface(desired);
            mCurrentTypeface = desired;
        }
        mTextPaint.setFakeBoldText(fakeBold);
        mTextPaint.setTextSkewX(fakeItalic ? -0.35f : 0f);
    }

    /** Whether the code point is in one of the three Unicode Private Use Areas. */
    static boolean isPrivateUse(int codePoint) {
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
            || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
            || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }

    /** The map a code point draws from, kept whole so the run can use its own settings. */
    @Nullable
    private SymbolMap symbolMapFor(int codePoint) {
        // Repeated directives are ordered; a later overlapping range wins.
        for (int i = mSymbolMaps.length - 1; i >= 0; i--) {
            SymbolMap map = mSymbolMaps[i];
            if (codePoint >= map.firstCodePoint && codePoint <= map.lastCodePoint)
                return map;
        }
        return null;
    }

    private static int resolveCellColor(int color, int[] palette) {
        return (color & 0xff000000) == 0xff000000 ? color : palette[color];
    }

    private static int resolveExtraColor(TerminalEmulator.ExtraCursorColor color, int[] palette, int fallback) {
        if (color.type == 2) return color.value;
        if (color.type == 5) return palette[color.value & 0xff];
        return fallback;
    }

    private void drawCursorShape(Canvas canvas, float left, float bottom, float width, int shape, int color) {
        float top = bottom - mFontLineSpacing;
        float right = left + width;
        if (shape == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE)
            top = bottom - mFontLineSpacing / 4f;
        else if (shape == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
            right = left + width / 4f;
        mTextPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, mTextPaint);
    }

    /**
     * Draw one underline variant across a run, between the text baseline and the bottom of the cell.
     *
     * @param cellBottom the y coordinate of the bottom of the cell row.
     * @param descent    the distance from the baseline to {@code cellBottom}.
     */
    private void drawUnderline(Canvas canvas, float left, float right, float cellBottom, int descent, int underlineStyle, int color) {
        if (right <= left || descent <= 0)
            return;
        final float thickness = Math.min(mDecorationThickness, Math.max(1f, descent / 3f));
        final float baseline = cellBottom - descent;
        // Keep every variant inside its own cell, so that a decoration never bleeds into the row below.
        final float cellTop = cellBottom - mFontLineSpacing;
        final float top = clamp(cellTop + mUnderlinePositionFromTop, baseline,
            cellBottom - thickness);
        mTextPaint.setColor(color);
        switch(underlineStyle) {
            case TextStyle.UNDERLINE_STYLE_DOUBLE:
                {
                    float lineThickness = Math.max(1f, thickness * 0.6f);
                    float first = clamp(top, baseline, cellBottom - 3f * lineThickness);
                    float second = Math.min(first + 2f * lineThickness, cellBottom - lineThickness);
                    canvas.drawRect(left, first, right, first + lineThickness, mTextPaint);
                    canvas.drawRect(left, second, right, second + lineThickness, mTextPaint);
                    break;
                }
            case TextStyle.UNDERLINE_STYLE_CURLY:
                {
                    float amplitude = Math.max(0.5f, Math.min(thickness, descent * 0.2f));
                    float centerY = clamp(top + amplitude, baseline + amplitude,
                        cellBottom - amplitude - thickness / 2f);
                    float halfPeriod = Math.max(2f, thickness * 2.5f);
                    mDecorationPath.rewind();
                    mDecorationPath.moveTo(left, centerY);
                    boolean up = true;
                    for (float x = left; x < right; x += halfPeriod) {
                        float next = Math.min(x + halfPeriod, right);
                        float controlY = up ? (centerY - amplitude * 2f) : (centerY + amplitude * 2f);
                        mDecorationPath.quadTo((x + next) / 2f, controlY, next, centerY);
                        up = !up;
                    }
                    mTextPaint.setStyle(Paint.Style.STROKE);
                    mTextPaint.setStrokeWidth(thickness);
                    canvas.drawPath(mDecorationPath, mTextPaint);
                    mTextPaint.setStyle(Paint.Style.FILL);
                    mTextPaint.setStrokeWidth(0f);
                    break;
                }
            case TextStyle.UNDERLINE_STYLE_DOTTED:
            case TextStyle.UNDERLINE_STYLE_DASHED:
                {
                    // Dashes are drawn with drawLine(), the one form hardware acceleration accepts a
                    // DashPathEffect on across all supported API levels.
                    mTextPaint.setStyle(Paint.Style.STROKE);
                    mTextPaint.setStrokeWidth(thickness);
                    mTextPaint.setPathEffect((underlineStyle == TextStyle.UNDERLINE_STYLE_DOTTED) ? mDottedEffect : mDashedEffect);
                    float lineY = top + thickness / 2f;
                    canvas.drawLine(left, lineY, right, lineY, mTextPaint);
                    mTextPaint.setPathEffect(null);
                    mTextPaint.setStyle(Paint.Style.FILL);
                    mTextPaint.setStrokeWidth(0f);
                    break;
                }
            default:
                canvas.drawRect(left, top, right, top + thickness, mTextPaint);
                break;
        }
    }

    /** Draw a bounded, configurable strikethrough without relying on Paint's fixed metrics. */
    private void drawStrikethrough(Canvas canvas, float left, float right, float cellBottom,
                                   int color) {
        if (right <= left)
            return;
        float cellTop = cellBottom - mFontLineSpacing;
        float center = clamp(cellTop + mStrikethroughPositionFromTop,
            cellTop + mStrikethroughThickness / 2f,
            cellBottom - mStrikethroughThickness / 2f);
        mTextPaint.setColor(color);
        canvas.drawRect(left, center - mStrikethroughThickness / 2f,
            right, center + mStrikethroughThickness / 2f, mTextPaint);
    }

    /** @return true when the flag changed, so the caller can skip a needless invalidate */
    public boolean setCursorSuppressed(boolean suppressed) {
        if (mCursorSuppressed == suppressed) return false;
        mCursorSuppressed = suppressed;
        return true;
    }

    public float getFontWidth() {
        return mFontWidth;
    }

    public int getFontLineSpacing() {
        return mFontLineSpacing;
    }

    public int getFontLineSpacingAndAscent() {
        return mFontLineSpacingAndAscent;
    }

}
