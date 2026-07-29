package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;

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

        public SymbolMap(int firstCodePoint, int lastCodePoint, Typeface typeface) {
            if (firstCodePoint < 0 || lastCodePoint < firstCodePoint
                || lastCodePoint > Character.MAX_CODE_POINT || typeface == null)
                throw new IllegalArgumentException("Invalid symbol font range");
            this.firstCodePoint = firstCodePoint;
            this.lastCodePoint = lastCodePoint;
            this.typeface = typeface;
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

    private static final SymbolMap[] NO_SYMBOL_MAPS = new SymbolMap[0];

    final int mTextSize;

    final Typeface mTypeface;

    @Nullable final Typeface mBoldTypeface;

    @Nullable
    final Typeface mItalicTypeface;

    @Nullable final Typeface mBoldItalicTypeface;

    final SymbolMap[] mSymbolMaps;

    final LigaturePolicy mLigaturePolicy;

    final FontFeatures mFontFeatures;

    final FontVariations mFontVariations;

    final FontMetricsAdjustments mFontMetricsAdjustments;

    private final Paint mTextPaint = new Paint();
    private Typeface mCurrentTypeface;

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

    /** Reused when drawing curly underlines, to keep the render loop allocation free. */
    private final Path mDecorationPath = new Path();

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
        final boolean cursorVisible = mEmulator.shouldCursorBeVisible();
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
            int currentCharIndex = 0;
            float measuredWidthForRun = 0.f;
            // Both live in side tables on the row rather than in the style long, so they are only
            // consulted for the rows that have them.
            final boolean rowHasDecorationColors = lineObject.hasDecorationColors();
            final boolean rowHasHyperlinks = lineObject.hasHyperlinks();
            for (int column = 0; column < columns; ) {
                final char charAtIndex = line[currentCharIndex];
                final boolean charIsHighsurrogate = Character.isHighSurrogate(charAtIndex);
                final int charsForCodePoint = charIsHighsurrogate ? 2 : 1;
                final int codePoint = charIsHighsurrogate ? Character.toCodePoint(charAtIndex, line[currentCharIndex + 1]) : charAtIndex;
                final long style = lineObject.getStyle(column);
                final int decorationColor = rowHasDecorationColors ? lineObject.getDecorationColor(column) : TextStyle.DECORATION_COLOR_DEFAULT;
                final int hyperlinkId = rowHasHyperlinks ? lineObject.getHyperlinkId(column) : 0;
                if (TextStyle.isBitmap(style)) {
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
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface, false);
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
                    continue;
                }
                final int codePointWcWidth = lineObject.getDisplayWidthAt(currentCharIndex);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                final int effect = TextStyle.decodeEffect(style);
                final boolean cellBold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
                    | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
                final boolean cellItalic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
                final int faceStyle = (cellBold ? 1 : 0) | (cellItalic ? 2 : 0);
                final Typeface symbolTypeface = symbolTypefaceFor(codePoint);
                configureFont(cellBold, cellItalic, symbolTypeface);
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (symbolTypeface == null
                    && codePoint < mAsciiMeasures[faceStyle].length)
                    ? mAsciiMeasures[faceStyle][codePoint]
                    : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor
                    || insideSelection != lastRunInsideSelection || fontWidthMismatch
                    || lastRunFontWidthMismatch || decorationColor != lastRunDecorationColor
                    || hyperlinkId != lastRunHyperlinkId
                    || symbolTypeface != lastRunSymbolTypeface) {
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
                            lastRunHyperlinkId != 0, 0, lastRunSymbolTypeface, false);
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
                lastRunSymbolTypeface, false);
        }
        drawExtraCursors(mEmulator, canvas, screen, palette, topRow, endRow, boldWithBright, reverseVideo, horizontalOffset);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn,
                             int runWidthColumns, int startCharIndex, int runWidthChars, float mes,
                             int cursor, int cursorStyle, long textStyle, boolean boldWithBright,
                             boolean reverseVideo, float horizontalOffset, int decorationColor,
                             boolean hyperlink, int foregroundOverride,
                             @Nullable Typeface symbolTypeface, boolean drawBackgroundAndCursor) {
        boolean disableLigatures = disablesLigatures(mLigaturePolicy, cursor != 0);
        int effect = TextStyle.decodeEffect(textStyle);
        boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
            | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        String previousFeatures = mTextPaint.getFontFeatureSettings();
        String runFeatures = mFontFeatures.forRun(bold, italic, symbolTypeface != null);
        if (disableLigatures) {
            runFeatures = runFeatures == null || runFeatures.isEmpty()
                ? "'calt' 0" : runFeatures + ", 'calt' 0";
        }
        boolean changedFeatures = !sameString(previousFeatures, runFeatures);
        if (changedFeatures) mTextPaint.setFontFeatureSettings(runFeatures);
        String previousVariations = mTextPaint.getFontVariationSettings();
        String runVariations = mFontVariations.forRun(bold, italic, symbolTypeface != null);
        boolean changedVariations = !sameString(previousVariations, runVariations);
        boolean restoreVariations = false;
        if (changedVariations) {
            try {
                restoreVariations = mTextPaint.setFontVariationSettings(runVariations);
                if (!restoreVariations) mTextPaint.setFontVariationSettings(previousVariations);
            } catch (RuntimeException e) {
                try {
                    mTextPaint.setFontVariationSettings(previousVariations);
                } catch (RuntimeException ignored) {
                    // The validated loader path should prevent this; keep the base typeface usable.
                }
            }
        }
        try {
            drawTextRunConfigured(canvas, text, palette, y, startColumn, runWidthColumns,
                startCharIndex, runWidthChars, mes, cursor, cursorStyle, textStyle,
                boldWithBright, reverseVideo, horizontalOffset, decorationColor, hyperlink,
                foregroundOverride, symbolTypeface, drawBackgroundAndCursor);
        } finally {
            if (restoreVariations) {
                try {
                    mTextPaint.setFontVariationSettings(previousVariations);
                } catch (RuntimeException ignored) {
                    // Never let an optional axis setting take down terminal rendering.
                }
            }
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

    private void drawTextRunConfigured(Canvas canvas, char[] text, int[] palette, float y,
                                       int startColumn, int runWidthColumns, int startCharIndex,
                                       int runWidthChars, float mes, int cursor, int cursorStyle,
                                       long textStyle, boolean boldWithBright, boolean reverseVideo,
                                       float horizontalOffset, int decorationColor,
                                       boolean hyperlink, int foregroundOverride,
                                       @Nullable Typeface symbolTypeface,
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
        configureFont(bold, italic, symbolTypeface);
        // Measure the same shaped run that Canvas will draw. Per-code-point measureText() cannot
        // account for ligatures, Indic conjuncts, Arabic joining, or ZWJ emoji continuations.
        mes = mTextPaint.getTextRunAdvances(text, startCharIndex, runWidthChars,
            startCharIndex, runWidthChars, false, null, 0);
        if (!(mes > 0f)) mes = runWidthColumns * fontWidth;
        final long resolvedColors = resolveRunColors(textStyle, palette, boldWithBright, reverseVideo);
        int foreColor = (int) (resolvedColors >>> 32);
        int backColor = (int) resolvedColors;
        if (foregroundOverride != 0)
            foreColor = foregroundOverride;
        float left = horizontalOffset + startColumn * fontWidth;
        float right = left + runWidthColumns * fontWidth;
        mes = mes / fontWidth;
        boolean savedMatrix = false;
        if (Math.abs(mes - runWidthColumns) > 0.01) {
            canvas.save();
            canvas.scale(runWidthColumns / mes, 1.f);
            left *= mes / runWidthColumns;
            right *= mes / runWidthColumns;
            savedMatrix = true;
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
                int red = (0xFF & (foreColor >> 16));
                int green = (0xFF & (foreColor >> 8));
                int blue = (0xFF & foreColor);
                // Dim color handling used by libvte which in turn took it from xterm
                // (https://bug735245.bugzilla-attachments.gnome.org/attachment.cgi?id=284267):
                red = red * 2 / 3;
                green = green * 2 / 3;
                blue = blue * 2 / 3;
                foreColor = 0xFF000000 + (red << 16) + (green << 8) + blue;
            }
            // Underlines are drawn as geometry below, since Paint only knows one straight variant.
            mTextPaint.setUnderlineText(false);
            mTextPaint.setStrikeThruText(false);
            mTextPaint.setColor(foreColor);
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars,
                left, y - fontBaselineDescent, false, mTextPaint);
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
        if (savedMatrix)
            canvas.restore();
    }

    /**
     * Resolve a cell's final foreground and background colour — palette lookup, bold-is-bright,
     * and reverse video — packed as {@code (foreground << 32) | background}. The single source of
     * truth for both the background pass and the glyph pass, so the two cannot disagree.
     */
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
                    drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor);
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
                    drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor);
                    pendingStartColumn = -1;
                }
                if (backColor != defaultBackColor)
                    drawCellRect(canvas, column, column + cellColumns, top, y, horizontalOffset, backColor);
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
                        drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor);
                    pendingStartColumn = column;
                    pendingEndColumn = column + cellColumns;
                    pendingColor = backColor;
                }
            } else if (pendingStartColumn != -1) {
                drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor);
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
            drawCellRect(canvas, pendingStartColumn, pendingEndColumn, top, y, horizontalOffset, pendingColor);
    }

    private void drawCellRect(Canvas canvas, int startColumn, int endColumn, float top, float bottom,
                              float horizontalOffset, int color) {
        mTextPaint.setColor(color);
        canvas.drawRect(horizontalOffset + startColumn * mFontWidth, top,
            horizontalOffset + endColumn * mFontWidth, bottom, mTextPaint);
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
            Typeface symbolTypeface = symbolTypefaceFor(codePoint);
            configureFont((effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD
                    | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0,
                (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0, symbolTypeface);
            float measured = mTextPaint.measureText(text, startIndex, chars);
            int decorationColor = row.hasDecorationColors() ? row.getDecorationColor(startColumn)
                : TextStyle.DECORATION_COLOR_DEFAULT;
            boolean hyperlink = row.hasHyperlinks() && row.getHyperlinkId(startColumn) != 0;
            drawTextRun(canvas, text, palette, y, startColumn, width, startIndex, chars, measured,
                cursorColor, shape, style, boldWithBright, reverseVideo || invertText, horizontalOffset,
                decorationColor, hyperlink, textOverride, symbolTypeface, true);
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

    /** Symbol fonts intentionally ignore SGR face synthesis, matching Kitty's explicit map. */
    private void configureFont(boolean bold, boolean italic, @Nullable Typeface symbolTypeface) {
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
        if (desired != mCurrentTypeface) {
            mTextPaint.setTypeface(desired);
            mCurrentTypeface = desired;
        }
        mTextPaint.setFakeBoldText(fakeBold);
        mTextPaint.setTextSkewX(fakeItalic ? -0.35f : 0f);
    }

    @Nullable
    private Typeface symbolTypefaceFor(int codePoint) {
        // Repeated directives are ordered; a later overlapping range wins.
        for (int i = mSymbolMaps.length - 1; i >= 0; i--) {
            SymbolMap map = mSymbolMaps[i];
            if (codePoint >= map.firstCodePoint && codePoint <= map.lastCodePoint)
                return map.typeface;
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
