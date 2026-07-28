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
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalRow;
import com.termux.terminal.TextStyle;
import com.termux.terminal.WcWidth;

/**
 * Renderer of a {@link TerminalEmulator} into a {@link Canvas}.
 * <p/>
 * Saves font metrics, so needs to be recreated each time the typeface or font size changes.
 */
public final class TerminalRenderer {

    final int mTextSize;

    final Typeface mTypeface;

    final Typeface mItalicTypeface;

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

    /**
     * The {@link #mFontLineSpacing} + {@link #mFontAscent}.
     */
    final int mFontLineSpacingAndAscent;

    private final float[] asciiMeasures = new float[127];
    private final RectF mSixelRect = new RectF();

    /** Reused when drawing curly underlines, to keep the render loop allocation free. */
    private final Path mDecorationPath = new Path();

    /** Stroke width of underlines and their dash lengths, all derived from the font size. */
    private final float mDecorationThickness;

    private final DashPathEffect mDottedEffect;

    private final DashPathEffect mDashedEffect;

    /**
     * The width of a single mono spaced character obtained by {@link Paint#measureText(String)} on a single 'X'.
     */
    final float mItalicFontWidth;

    /**
     * The italic font line height, derived from {@link Paint.FontMetricsInt} descent-ascent.
     */
    final int mItalicFontLineSpacing;

    /**
     * The {@link Paint#ascent()}. See http://www.fampennings.nl/maarten/android/08numgrid/font.png
     */
    private final int mItalicFontAscent;

    /**
     * The {@link #mFontLineSpacing} + {@link #mFontAscent}.
     */
    final int mItalicFontLineSpacingAndAscent;

    public TerminalRenderer(int textSize, Typeface typeface, Typeface italicTypeface) {
        mTextSize = textSize;
        mTypeface = typeface;
        mItalicTypeface = italicTypeface;
        mTextPaint.setTypeface(typeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        Paint.FontMetricsInt fontMetrics = mTextPaint.getFontMetricsInt();
        mFontAscent = fontMetrics.ascent;
        mFontLineSpacing = fontMetrics.descent - mFontAscent;
        mFontLineSpacingAndAscent = mFontLineSpacing + mFontAscent;
        mFontWidth = mTextPaint.measureText("X");
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 0; i < asciiMeasures.length; i++) {
            sb.setCharAt(0, (char) i);
            asciiMeasures[i] = mTextPaint.measureText(sb, 0, 1);
        }
        mTextPaint.setTypeface(italicTypeface);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(textSize);
        Paint.FontMetricsInt italicFontMetrics = mTextPaint.getFontMetricsInt();
        mItalicFontAscent = italicFontMetrics.ascent;
        mItalicFontLineSpacing = italicFontMetrics.descent - mItalicFontAscent;
        mItalicFontLineSpacingAndAscent = mItalicFontLineSpacing + mItalicFontAscent;
        mItalicFontWidth = mTextPaint.measureText("X");
        mDecorationThickness = Math.max(1f, textSize / 16f);
        mDottedEffect = new DashPathEffect(new float[]{mDecorationThickness, mDecorationThickness * 2f}, 0f);
        mDashedEffect = new DashPathEffect(new float[]{mDecorationThickness * 4f, mDecorationThickness * 3f}, 0f);
    }

    /**
     * Render the terminal to a canvas with at a specified row scroll, and an optional rectangular selection.
     */
    public final void render(TerminalEmulator mEmulator, Canvas canvas, int topRow, int selectionY1, int selectionY2, int selectionX1, int selectionX2, boolean transparentBackground, int transparentOverlayColor, float horizontalOffset) {
        final boolean boldWithBright = mEmulator.isBoldWithBright();
        final boolean reverseVideo = mEmulator.isReverseVideo();
        final int endRow = topRow + mEmulator.mRows;
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
                    Bitmap bm = mEmulator.getScreen().getSixelBitmap(codePoint, style);
                    if (bm != null) {
                        float left = horizontalOffset + column * mFontWidth;
                        float top = heightOffset - mFontLineSpacing;
                        mSixelRect.set(left, top, left + mFontWidth, top + mFontLineSpacing);
                        canvas.drawBitmap(mEmulator.getScreen().getSixelBitmap(codePoint, style), mEmulator.getScreen().getSixelRect(codePoint, style), mSixelRect, null);
                    }
                    column += 1;
                    measuredWidthForRun = 0.f;
                    lastRunStyle = 0;
                    lastRunInsideCursor = false;
                    lastRunStartColumn = column + 1;
                    lastRunStartIndex = currentCharIndex;
                    lastRunFontWidthMismatch = false;
                    lastRunDecorationColor = TextStyle.DECORATION_COLOR_DEFAULT;
                    lastRunHyperlinkId = 0;
                    currentCharIndex += charsForCodePoint;
                    continue;
                }
                final int codePointWcWidth = WcWidth.width(codePoint);
                final boolean insideCursor = (cursorX == column || (codePointWcWidth == 2 && cursorX == column + 1));
                final boolean insideSelection = column >= selx1 && column <= selx2;
                // Check if the measured text width for this code point is not the same as that expected by wcwidth().
                // This could happen for some fonts which are not truly monospace, or for more exotic characters such as
                // smileys which android font renders as wide.
                // If this is detected, we draw this code point scaled to match what wcwidth() expects.
                final float measuredCodePointWidth = (codePoint < asciiMeasures.length) ? asciiMeasures[codePoint] : mTextPaint.measureText(line, currentCharIndex, charsForCodePoint);
                final boolean fontWidthMismatch = Math.abs(measuredCodePointWidth / mFontWidth - codePointWcWidth) > 0.01;
                if (style != lastRunStyle || insideCursor != lastRunInsideCursor || insideSelection != lastRunInsideSelection || fontWidthMismatch || lastRunFontWidthMismatch || decorationColor != lastRunDecorationColor || hyperlinkId != lastRunHyperlinkId) {
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
                        drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection, horizontalOffset, lastRunDecorationColor, lastRunHyperlinkId != 0, 0);
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
                }
                measuredWidthForRun += measuredCodePointWidth;
                column += codePointWcWidth;
                currentCharIndex += charsForCodePoint;
                while (currentCharIndex < charsUsedInLine && WcWidth.width(line, currentCharIndex) <= 0) {
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
            drawTextRun(canvas, line, palette, heightOffset, lastRunStartColumn, columnWidthSinceLastRun, lastRunStartIndex, charsSinceLastRun, measuredWidthForRun, cursorColor, cursorShape, lastRunStyle, boldWithBright, reverseVideo || invertCursorTextColor || lastRunInsideSelection, horizontalOffset, lastRunDecorationColor, lastRunHyperlinkId != 0, 0);
        }
        drawExtraCursors(mEmulator, canvas, screen, palette, topRow, endRow, boldWithBright, reverseVideo, horizontalOffset);
    }

    private void drawTextRun(Canvas canvas, char[] text, int[] palette, float y, int startColumn, int runWidthColumns, int startCharIndex, int runWidthChars, float mes, int cursor, int cursorStyle, long textStyle, boolean boldWithBright, boolean reverseVideo, float horizontalOffset, int decorationColor, boolean hyperlink, int foregroundOverride) {
        int foreColor = TextStyle.decodeForeColor(textStyle);
        final int effect = TextStyle.decodeEffect(textStyle);
        int backColor = TextStyle.decodeBackColor(textStyle);
        final boolean bold = (effect & (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0;
        final boolean underline = (effect & TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0;
        final boolean italic = (effect & TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0;
        final boolean strikeThrough = (effect & TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0;
        final boolean dim = (effect & TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0;
        final float fontWidth = italic ? mItalicFontWidth : mFontWidth;
        final int fontLineSpacing = italic ? mItalicFontLineSpacing : mFontLineSpacing;
        final int fontAscent = italic ? mItalicFontAscent : mFontAscent;
        final int fontLineSpacingAndAscent = italic ? mItalicFontLineSpacingAndAscent : mFontLineSpacingAndAscent;
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
        final boolean reverseVideoHere = reverseVideo ^ (effect & (TextStyle.CHARACTER_ATTRIBUTE_INVERSE)) != 0;
        if (reverseVideoHere) {
            int tmp = foreColor;
            foreColor = backColor;
            backColor = tmp;
        }
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
        if (backColor != palette[TextStyle.COLOR_INDEX_BACKGROUND]) {
            // Only draw non-default background.
            mTextPaint.setColor(backColor);
            canvas.drawRect(left, y - fontLineSpacingAndAscent + fontAscent, right, y, mTextPaint);
        }
        if (cursor != 0) {
            mTextPaint.setColor(cursor);
            // fontLineSpacingAndAscent - fontAscent isn't equals to
            // fontLineSpacing?
            float cursorHeight = fontLineSpacing;
            if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE)
                cursorHeight /= 4.;
            else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR)
                right -= ((right - left) * 3) / 4.;
            canvas.drawRect(left, y - cursorHeight, right, y, mTextPaint);
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
            Typeface desiredTypeface = italic ? mItalicTypeface : mTypeface;
            if (desiredTypeface != mCurrentTypeface) {
                mTextPaint.setTypeface(desiredTypeface);
                mCurrentTypeface = desiredTypeface;
            }
            mTextPaint.setFakeBoldText(bold);
            // Underlines are drawn as geometry below, since Paint only knows one straight variant.
            mTextPaint.setUnderlineText(false);
            mTextPaint.setTextSkewX(0.f);
            if (italic && mItalicTypeface.equals(mTypeface))
                mTextPaint.setTextSkewX(-0.35f);
            mTextPaint.setStrikeThruText(strikeThrough);
            mTextPaint.setColor(foreColor);
            // The text alignment is the default Paint.Align.LEFT.
            canvas.drawTextRun(text, startCharIndex, runWidthChars, startCharIndex, runWidthChars, left, y - fontLineSpacingAndAscent, false, mTextPaint);
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
                drawUnderline(canvas, left, right, y, fontLineSpacingAndAscent, underlineStyle, lineColor);
            }
        }
        if (savedMatrix)
            canvas.restore();
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
            int width = Math.max(1, WcWidth.width(codePoint));
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
            float measured = mTextPaint.measureText(text, startIndex, chars);
            int decorationColor = row.hasDecorationColors() ? row.getDecorationColor(startColumn)
                : TextStyle.DECORATION_COLOR_DEFAULT;
            boolean hyperlink = row.hasHyperlinks() && row.getHyperlinkId(startColumn) != 0;
            drawTextRun(canvas, text, palette, y, startColumn, width, startIndex, chars, measured,
                cursorColor, shape, style, boldWithBright, reverseVideo || invertText, horizontalOffset,
                decorationColor, hyperlink, textOverride);
        }
    }

    private static int kittyCursorShape(int shape) {
        if (shape == 2) return TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;
        if (shape == 3) return TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE;
        return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
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
        final float top = Math.min(baseline + descent * 0.4f, cellBottom - thickness);
        mTextPaint.setColor(color);
        switch(underlineStyle) {
            case TextStyle.UNDERLINE_STYLE_DOUBLE:
                {
                    float lineThickness = Math.max(1f, thickness * 0.6f);
                    float first = Math.min(baseline + descent * 0.25f, cellBottom - 3f * lineThickness);
                    float second = Math.min(first + 2f * lineThickness, cellBottom - lineThickness);
                    canvas.drawRect(left, first, right, first + lineThickness, mTextPaint);
                    canvas.drawRect(left, second, right, second + lineThickness, mTextPaint);
                    break;
                }
            case TextStyle.UNDERLINE_STYLE_CURLY:
                {
                    float amplitude = Math.max(0.5f, Math.min(thickness, descent * 0.2f));
                    float centerY = Math.min(baseline + descent * 0.5f + amplitude, cellBottom - amplitude - thickness / 2f);
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
