package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.termux.app.launcher.az.AzBarFrame;
import com.termux.app.launcher.az.AzLetterTrack;
import com.termux.app.launcher.az.AzScrubGesture;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AzScrubRowView extends AppCompatTextView {
    public static final class LetterVisualMetrics {
        public final RectF glyphBoundsRaw = new RectF();
        public final RectF glassBoundsRaw = new RectF();
        public float baselineRawY;
        public float centerRawX;
        public char letter;

        public void clear() {
            glyphBoundsRaw.setEmpty();
            glassBoundsRaw.setEmpty();
            baselineRawY = 0f;
            centerRawX = 0f;
            letter = '\0';
        }

        public boolean isValid() {
            return !glassBoundsRaw.isEmpty();
        }
    }

    public enum InteractionMode {
        WAVE_TRACK,
        INLINE_EMPHASIS_TRACK
    }

    public enum GesturePhase {
        DOWN,
        MOVE,
        UP
    }

    public interface ScrubCallback {
        void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX,
                     float rawY, long eventTimeMs, @NonNull GesturePhase phase);
        void onCancel();
        default void onDoubleTap() {}
    }

    public static final char PINNED_APPS_SYMBOL = AzScrubGesture.PINNED_APPS_SYMBOL;
    private static final char[] ALPHABET_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final char[] LETTERS = (PINNED_APPS_SYMBOL + "ABCDEFGHIJKLMNOPQRSTUVWXYZ#").toCharArray();
    private char[] visibleLetters = LETTERS;
    private String[] visibleGlyphs = buildGlyphStrings(LETTERS);

    @Nullable private ScrubCallback callback;
    private int currentSelectionIndex = 0;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Crisp dark outline drawn under each (light) letter so it stays legible over both light and dark
    // wallpaper regions — a sharp stroke, unlike the old blurry drop-shadow which read as fuzzy.
    private final Paint letterOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect glyphRect = new Rect();
    private final Paint.FontMetrics letterFontMetrics = new Paint.FontMetrics();
    private final int[] locationOnScreen = new int[2];
    /**
     * The edge the bar stands on. Along the bottom — where the gesture was written — the letters
     * run across the width and the wave lifts them up; on any other edge the same arithmetic is
     * turned or mirrored by {@link AzBarFrame}, with the glyphs left upright.
     */
    @NonNull private Edge barEdge = Edge.BOTTOM;
    /** Where the finger is along the letters: across a row's width, down a column's height. */
    private float activeTouchAlong = -1f;
    private float waveStrength = 0f;
    private int accentColor = Color.WHITE;
    // Softer than pure black: a desaturated near-black that keeps letters legible over any
    // wallpaper without the harsh hard-edged look the old #000 stroke had.
    private static final int OUTLINE_DARK = 0xFF1A1F2A;
    // A slow "sword-glint" sweep position (runs off-screen to off-screen) that, while the row is
    // touched, brushes a soft material-colour shimmer across every letter's outline in turn.
    private float shimmerPhase = 0f;
    private boolean shimmerActive;
    @Nullable private ValueAnimator shimmerAnimator;
    @Nullable private ValueAnimator settleAnimator;
    private long lastTapUpTimeMs;
    private float lastTapUpAlong = Float.NaN;
    private int doubleTapTimeoutMs;
    private int doubleTapSlopPx;
    private boolean suppressUpScrub;
    @NonNull private InteractionMode interactionMode = InteractionMode.WAVE_TRACK;
    @Nullable private Character lockedInlineLetter;
    private int activeLetterIndex = -1;
    static final float LETTER_SLOT_HYSTERESIS_RATIO = AzLetterTrack.SLOT_HYSTERESIS_RATIO;
    private boolean interactionRenderActive;
    private boolean rowHapticsEnabled = true;
    private int lastHapticLetterIndex = -1;
    /** Touchable dead space beside the letters, on the side the bar stands on. */
    private int chinPaddingPx;
    /**
     * True while the letters are not what the finger is choosing — it has climbed off this row and
     * is picking an icon out of the apps row, which ticks for itself.
     */
    private boolean letterTicksSuspended;

    public AzScrubRowView(Context context) {
        super(context);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText("");
        setSingleLine(true);
        setTextSize(11f);
        applyEdgePadding();
        setClickable(true);
        updateInteractionRenderLayer(false);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTextSize(getTextSize());
        letterPaint.setColor(getCurrentTextColor());
        letterOutlinePaint.setTextAlign(Paint.Align.CENTER);
        letterOutlinePaint.setStyle(Paint.Style.STROKE);
        letterOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
        letterOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout();
        doubleTapSlopPx = viewConfiguration.getScaledDoubleTapSlop();
    }

    /**
     * Dead space beside the letters, as padding on the side the bar stands on. The letters are
     * placed off that side of the content box, so the padding lifts them clear of the dock's rim
     * and leaves the space beyond them inside the bar — space that takes a touch like the rest.
     */
    public void setChinPaddingPx(int paddingPx) {
        int chin = Math.max(0, paddingPx);
        if (chin == chinPaddingPx)
            return;
        chinPaddingPx = chin;
        applyEdgePadding();
        invalidate();
    }

    /**
     * Stands the letters on {@code edge}: a row across the top or the bottom, a column down either
     * side. Everything that follows — the chin's side, the wave's direction, which axis a touch is
     * read along — comes off this one value, so there is no second layout to keep in step.
     */
    public void setBarEdge(@NonNull Edge edge) {
        if (barEdge == edge)
            return;
        barEdge = edge;
        applyEdgePadding();
        activeTouchAlong = -1f;
        activeLetterIndex = -1;
        invalidate();
    }

    @NonNull
    public Edge barEdge() {
        return barEdge;
    }

    /** True while the letters are stacked down a column rather than laid along a row. */
    public boolean isVerticalBar() {
        return barEdge.isOnSide();
    }

    /** The 1dp of air the letters keep, plus the chin, on whichever side the bar stands. */
    private void applyEdgePadding() {
        int air = dp(1);
        int chin = air + chinPaddingPx;
        switch (barEdge) {
            case TOP:
                setPadding(0, chin, 0, air);
                break;
            case LEFT:
                setPadding(chin, 0, air, 0);
                break;
            case RIGHT:
                setPadding(air, 0, chin, 0);
                break;
            case BOTTOM:
            default:
                setPadding(0, air, 0, chin);
                break;
        }
    }

    /** The band the letters are drawn in: the bar's thickness without the chin beside them. */
    public int letterBandThicknessPx() {
        return Math.max(0, (isVerticalBar() ? getWidth() : getHeight()) - chinPaddingPx);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // The letters span the bar's whole length, with no inset. Which length that is — a row's
    // width or a column's height — is the only thing the draw and the touch mapping ask.
    private float letterTrackLengthPx() {
        return Math.max(1f, isVerticalBar() ? getHeight() : getWidth());
    }

    private float letterSlotSizePx() {
        return AzLetterTrack.slotSizePx(letterTrackLengthPx(), visibleLetters.length);
    }

    private float letterCenterAlongPx(int index) {
        return AzLetterTrack.centerPx(index, letterTrackLengthPx(), visibleLetters.length);
    }

    private int indexForAlong(float alongPx) {
        return AzLetterTrack.indexAt(alongPx, letterTrackLengthPx(), visibleLetters.length);
    }

    /** The frame that turns this bar's own touch points into the canonical bottom-bar ones. */
    @NonNull
    private AzBarFrame localFrame() {
        return AzBarFrame.of(barEdge, getWidth(), getHeight());
    }

    /**
     * The x a letter's glyph is centred on. A row centres it on its slot along the width; a column
     * centres it across the visible band and lets the wave carry it away from the edge, which is
     * sideways there rather than up.
     */
    private float letterDrawX(float alongCenterPx, float waveLiftPx) {
        if (!isVerticalBar())
            return alongCenterPx;
        float band = Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight());
        return barEdge == Edge.LEFT
            ? getPaddingLeft() + (band * 0.5f) + waveLiftPx
            : getWidth() - getPaddingRight() - (band * 0.5f) - waveLiftPx;
    }

    /**
     * The baseline a letter sits on, for whatever text size {@code letterPaint} currently holds.
     * A horizontal bar centres each glyph on the bar's own centre line and lets the wave carry it
     * away from the edge; a column centres each glyph on its own slot down the bar.
     */
    private float letterDrawBaseline(float alongCenterPx, float waveLiftPx) {
        letterPaint.getFontMetrics(letterFontMetrics);
        if (isVerticalBar()) {
            return alongCenterPx - ((letterFontMetrics.ascent + letterFontMetrics.descent) * 0.5f);
        }
        return horizontalLetterBaselinePx(getHeight(), letterFontMetrics.ascent,
            letterFontMetrics.descent, waveLiftPx, barEdge);
    }

    /**
     * Where a glyph sits on a horizontal bar: centred on the view's full-height centre line, chin
     * included, with the scrub wave riding on top of that. The lift always carries the letter away
     * from the screen edge the bar is docked against — down from a top bar, up from a bottom one.
     *
     * @param heightPx the bar's whole height, padding and chin included
     * @param ascent   the font's ascent, negative as {@link android.graphics.Paint.FontMetrics}
     *                 reports it
     * @param descent  the font's descent, positive
     * @param liftPx   how far the wave carries this letter, never negative
     * @param edge     the screen edge the bar is docked against
     */
    static float horizontalLetterBaselinePx(float heightPx, float ascent, float descent,
                                            float liftPx, @NonNull Edge edge) {
        float centred = (heightPx * 0.5f) - ((ascent + descent) * 0.5f);
        return edge == Edge.TOP ? centred + liftPx : centred - liftPx;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        int baseColor = getCurrentTextColor();
        int focusColor = resolveFocusLetterColor();
        letterPaint.setColor(baseColor);
        float baseTextSize = getTextSize();
        letterPaint.setTextSize(baseTextSize);
        float trackLength = letterTrackLengthPx();
        float slot = letterSlotSizePx();
        float anchorAlong = activeTouchAlong < 0f ? (trackLength * 0.5f) : activeTouchAlong;
        float waveAmplitude = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0f : (dp(15) * waveStrength);
        int activeIndex = resolveActiveIndex(anchorAlong);
        boolean hasInlineFocus = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            && waveStrength > 0.01f
            && (activeTouchAlong >= 0f || lockedInlineLetter != null);

        for (int i = 0; i < visibleLetters.length; i++) {
            float along = letterCenterAlongPx(i);
            float distance = Math.abs(along - anchorAlong) / Math.max(1f, slot);
            float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
            float waveLift = (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * waveAmplitude;
            boolean activeFocus = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                ? (hasInlineFocus && i == activeIndex)
                : (waveStrength > 0.01f && i == activeIndex);
            if (activeFocus && interactionMode != InteractionMode.INLINE_EMPHASIS_TRACK) {
                waveLift *= 1.2f;
                waveLift = Math.max(waveLift, dp(6));
            }
            float scale = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                ? (activeFocus ? 1.14f : 1f)
                : (1f + (0.34f * envelope * waveStrength));
            letterPaint.setTextSize(baseTextSize * scale);
            applyLetterWeight(envelope, activeFocus);
            float baseline = letterDrawBaseline(along, waveLift);
            float x = letterDrawX(along, waveLift);
            if (activeFocus) {
                letterPaint.setColor(focusColor);
            } else {
                float colorProgress = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                    ? 0f
                    : clamp01(envelope * waveStrength * 0.72f);
                letterPaint.setColor(blendColors(baseColor, focusColor, colorProgress));
            }
            // Crisp outline pass under the fill: a sharp dark stroke that keeps the light letter
            // readable on any wallpaper, replacing the fuzzy drop-shadow. Constant width so the
            // stroke never thickens enough to fill the letters' inner holes (no "bloat").
            String glyph = visibleGlyphs[i];
            float density = getResources().getDisplayMetrics().density;
            letterOutlinePaint.setTextSize(letterPaint.getTextSize());
            letterOutlinePaint.setTypeface(letterPaint.getTypeface());
            letterOutlinePaint.setStrokeWidth(density * 1.4f);
            // Sword-glint shimmer: a soft material-accent highlight that the sweep brushes across
            // each outline in turn. Gaussian falloff around the sweep position; soothing, capped.
            int outlineBase = OUTLINE_DARK;
            if (shimmerActive) {
                float lx = along / trackLength;
                float d = (lx - shimmerPhase) / 0.16f;
                float glint = (float) Math.exp(-(d * d));
                if (glint > 0.001f) {
                    outlineBase = blendColors(OUTLINE_DARK, accentColor, clamp01(glint) * 0.6f);
                }
            }
            letterOutlinePaint.setColor(withAlpha(outlineBase, activeFocus ? 215 : 195));
            canvas.drawText(glyph, x, baseline, letterOutlinePaint);
            canvas.drawText(glyph, x, baseline, letterPaint);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setScrubCallback(@Nullable ScrubCallback callback) {
        this.callback = callback;
    }

    public void setRowHapticsEnabled(boolean enabled) {
        rowHapticsEnabled = enabled;
    }

    /**
     * Hands the letter tick over to whoever the finger is actually choosing with. The scrub can
     * climb off this row and go on picking an icon out of the apps row, which ticks per icon; the
     * letter slots underneath keep passing by, and ticking on those too made the whole gesture
     * feel like a scrub along the letters no matter which row the finger was on.
     */
    public void setLetterHapticTicksSuspended(boolean suspended) {
        letterTicksSuspended = suspended;
    }

    public void setVisibleLetters(@NonNull Set<Character> letters) {
        if (letters.isEmpty()) {
            if (Arrays.equals(visibleLetters, LETTERS)) {
                return;
            }
            visibleLetters = LETTERS;
            visibleGlyphs = buildGlyphStrings(visibleLetters);
            invalidate();
            return;
        }
        LinkedHashSet<Character> normalized = new LinkedHashSet<>();
        for (Character c : letters) {
            if (c == null) continue;
            char upper = Character.toUpperCase(c);
            if ((upper >= 'A' && upper <= 'Z') || upper == '#') {
                normalized.add(upper);
            }
        }
        if (normalized.isEmpty()) {
            if (Arrays.equals(visibleLetters, LETTERS)) {
                return;
            }
            visibleLetters = LETTERS;
            visibleGlyphs = buildGlyphStrings(visibleLetters);
            invalidate();
            return;
        }
        char[] out = new char[normalized.size() + 1];
        int i = 0;
        out[i++] = PINNED_APPS_SYMBOL;
        for (char base : ALPHABET_LETTERS) {
            if (normalized.contains(base)) {
                out[i++] = base;
            }
        }
        char[] nextVisibleLetters;
        if (i <= 1) {
            nextVisibleLetters = LETTERS;
        } else if (i == out.length) {
            nextVisibleLetters = out;
        } else {
            char[] trimmed = new char[i];
            System.arraycopy(out, 0, trimmed, 0, i);
            nextVisibleLetters = trimmed;
        }
        if (Arrays.equals(visibleLetters, nextVisibleLetters)) {
            return;
        }
        visibleLetters = nextVisibleLetters;
        visibleGlyphs = buildGlyphStrings(visibleLetters);
        invalidate();
    }

    public void setInteractionAccentColor(int color) {
        if (accentColor == color) {
            return;
        }
        accentColor = color;
        invalidate();
    }

    public void setInteractionMode(@NonNull InteractionMode mode) {
        if (interactionMode == mode && (mode != InteractionMode.WAVE_TRACK || lockedInlineLetter == null)) {
            return;
        }
        interactionMode = mode;
        if (mode == InteractionMode.WAVE_TRACK) {
            lockedInlineLetter = null;
        }
        invalidate();
    }

    public void setLockedInlineLetter(@Nullable Character letter) {
        if (lockedInlineLetter == null ? letter == null : lockedInlineLetter.equals(letter)) {
            return;
        }
        lockedInlineLetter = letter;
        invalidate();
    }

    public void getLetterFocusBoundsOnScreen(char letter, @NonNull RectF out) {
        LetterVisualMetrics metrics = new LetterVisualMetrics();
        if (getLetterVisualMetricsOnScreen(letter, metrics)) {
            out.set(metrics.glassBoundsRaw);
        } else {
            out.setEmpty();
        }
    }

    public boolean getLetterVisualMetricsOnScreen(char letter, @NonNull LetterVisualMetrics out) {
        out.clear();
        if (getWidth() <= 0 || getHeight() <= 0 || visibleLetters.length == 0) {
            return false;
        }

        float trackLength = letterTrackLengthPx();
        float slot = letterSlotSizePx();
        float anchorAlong = activeTouchAlong < 0f ? (trackLength * 0.5f) : activeTouchAlong;
        int activeIndex = resolveActiveIndex(anchorAlong);
        int index = indexOfVisibleLetter(letter);
        if (index < 0) {
            index = activeIndex;
        }
        if (index < 0 || index >= visibleLetters.length) {
            return false;
        }

        boolean activeFocus = index == activeIndex;
        float along = letterCenterAlongPx(index);
        float distance = Math.abs(along - anchorAlong) / Math.max(1f, slot);
        float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
        float waveLift = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? 0f
            : (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * (dp(15) * waveStrength);
        if (activeFocus && interactionMode != InteractionMode.INLINE_EMPHASIS_TRACK) {
            waveLift *= 1.2f;
            waveLift = Math.max(waveLift, dp(6));
        }
        float baseTextSize = getTextSize();
        float scale = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? (activeFocus ? 1.14f : 1f)
            : (1f + (0.34f * envelope * waveStrength));
        letterPaint.setTextSize(baseTextSize * scale);
        applyLetterWeight(envelope, activeFocus);
        float baseline = letterDrawBaseline(along, waveLift);
        float x = letterDrawX(along, waveLift);
        String label = visibleGlyphs[index];
        glyphRect.setEmpty();
        letterPaint.getTextBounds(label, 0, label.length(), glyphRect);
        float glyphLeft = x + glyphRect.left;
        float glyphRight = x + glyphRect.right;
        float glyphTop = baseline + glyphRect.top;
        float glyphBottom = baseline + glyphRect.bottom;
        float glyphWidth = glyphRight - glyphLeft;
        if (glyphRight <= glyphLeft) {
            float textWidth = Math.max(letterPaint.measureText(label), dp(8));
            glyphLeft = x - (textWidth * 0.5f);
            glyphRight = x + (textWidth * 0.5f);
            glyphWidth = textWidth;
        } else {
            glyphWidth = Math.max(dp(8), glyphWidth);
        }
        // The glass is padded from its slot along the bar and from the glyph across it, whichever
        // axis each of those happens to be: a column's slot is its height, a row's is its width.
        float glyphAcross = isVerticalBar() ? glyphWidth : (glyphBottom - glyphTop);
        float padAlong = Math.max(dp(3), Math.min(dp(5), slot * 0.10f));
        float padAcross = Math.max(dp(2), Math.min(dp(4), Math.max(1f, glyphAcross) * 0.22f));
        float padX = isVerticalBar() ? padAcross : padAlong;
        float padY = isVerticalBar() ? padAlong : padAcross;
        float glassLeft = Math.max(0f, glyphLeft - padX);
        float glassRight = Math.min(getWidth(), glyphRight + padX);
        float glassTop = Math.max(0f, glyphTop - padY);
        float glassBottom = Math.min(getHeight(), glyphBottom + padY);

        getLocationOnScreen(locationOnScreen);
        out.letter = visibleLetters[index];
        out.centerRawX = locationOnScreen[0] + x;
        out.baselineRawY = locationOnScreen[1] + baseline;
        out.glyphBoundsRaw.set(
            locationOnScreen[0] + (x - (glyphWidth * 0.5f)),
            locationOnScreen[1] + glyphTop,
            locationOnScreen[0] + (x + (glyphWidth * 0.5f)),
            locationOnScreen[1] + glyphBottom
        );
        out.glassBoundsRaw.set(
            locationOnScreen[0] + glassLeft,
            locationOnScreen[1] + glassTop,
            locationOnScreen[0] + glassRight,
            locationOnScreen[1] + glassBottom
        );
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (callback == null) return super.onTouchEvent(event);
        // Read in the canonical frame the gesture is written in: "along" the letters, and "away"
        // from the bar, whichever screen axes those are on this edge. A bottom bar is the frame
        // itself, so this is the identity there.
        AzBarFrame frame = localFrame();
        float alongRaw = frame.canonicalX(event.getX(), event.getY());
        float awayRaw = frame.canonicalY(event.getX(), event.getY());
        float along = Math.max(0f, Math.min(letterTrackLengthPx(), alongRaw));
        char letter = pickLetter(along, event.getActionMasked() != MotionEvent.ACTION_DOWN);
        // Measured against the letter band, not the whole bar: the chin beside the letters is
        // touchable space, and letting it stretch the step would retune the drag-up selection
        // behind the user's back the moment the extra-keys row is hidden.
        int selectionIndex = Math.max(0,
            (int) ((-awayRaw) / Math.max(dp(12f), letterBandThicknessPx() / 2f)));
        currentSelectionIndex = selectionIndex;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopSettleAnimation();
                startShimmer();
                updateInteractionRenderLayer(true);
                activeTouchAlong = along;
                activeLetterIndex = indexOfVisibleLetter(letter);
                lastHapticLetterIndex = activeLetterIndex;
                waveStrength = 1f;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    lockedInlineLetter = null;
                }
                bringToFront();
                updateInteractionLayerOffset();
                invalidate();
                long now = event.getEventTime();
                boolean isDoubleTap = (now - lastTapUpTimeMs) <= doubleTapTimeoutMs
                    && !Float.isNaN(lastTapUpAlong)
                    && Math.abs(along - lastTapUpAlong) <= doubleTapSlopPx;
                if (isDoubleTap) {
                    suppressUpScrub = true;
                    callback.onDoubleTap();
                    return true;
                }
                suppressUpScrub = false;
                callback.onScrub(letter, currentSelectionIndex, alongRaw, awayRaw,
                    event.getRawX(), event.getRawY(), event.getEventTime(), GesturePhase.DOWN);
                return true;
            case MotionEvent.ACTION_MOVE:
                int nextHapticLetterIndex = indexOfVisibleLetter(letter);
                boolean crossedLetterBoundary = RowHapticTickHelper.isBoundaryCrossing(
                    lastHapticLetterIndex, nextHapticLetterIndex);
                lastHapticLetterIndex = nextHapticLetterIndex;
                activeTouchAlong = along;
                waveStrength = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0.92f : 1f;
                updateInteractionLayerOffset();
                invalidate();
                callback.onScrub(letter, currentSelectionIndex, alongRaw, awayRaw,
                    event.getRawX(), event.getRawY(), event.getEventTime(), GesturePhase.MOVE);
                // Ticked after the callback, not before it: the gesture advances in there, and this
                // sample is what decides whose row the finger is on. Asking first would tick a
                // letter for the sample that just handed the gesture to the apps row.
                if (crossedLetterBoundary && rowHapticsEnabled && !letterTicksSuspended) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                return true;
            case MotionEvent.ACTION_UP:
                lastHapticLetterIndex = -1;
                lastTapUpTimeMs = event.getEventTime();
                lastTapUpAlong = along;
                if (!suppressUpScrub) {
                    callback.onScrub(letter, currentSelectionIndex, alongRaw, awayRaw,
                        event.getRawX(), event.getRawY(), event.getEventTime(), GesturePhase.UP);
                }
                suppressUpScrub = false;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    activeLetterIndex = -1;
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    activeTouchAlong = -1f;
                    activeLetterIndex = -1;
                    stopShimmer();
                    updateInteractionRenderLayer(false);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                lastHapticLetterIndex = -1;
                suppressUpScrub = false;
                callback.onCancel();
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    activeLetterIndex = -1;
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    activeTouchAlong = -1f;
                    activeLetterIndex = -1;
                    stopShimmer();
                    updateInteractionRenderLayer(false);
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void animateWaveRelease() {
        stopSettleAnimation();
        if (!isAttachedToWindow()) {
            waveStrength = 0f;
            activeTouchAlong = -1f;
            stopShimmer();
            updateInteractionRenderLayer(false);
            invalidate();
            return;
        }
        settleAnimator = ValueAnimator.ofFloat(waveStrength, 0f);
        settleAnimator.setDuration(165L);
        settleAnimator.addUpdateListener(animation -> {
            waveStrength = (float) animation.getAnimatedValue();
            updateInteractionLayerOffset();
            invalidate();
        });
        settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                waveStrength = 0f;
                activeTouchAlong = -1f;
                stopShimmer();
                updateInteractionRenderLayer(false);
                invalidate();
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                stopShimmer();
                updateInteractionRenderLayer(false);
            }
        });
        settleAnimator.start();
    }

    private void startShimmer() {
        shimmerActive = true;
        if (shimmerAnimator != null && shimmerAnimator.isRunning()) return;
        if (!isAttachedToWindow()) return;
        // Sweep from just off the left edge to just off the right, slow and linear, repeating.
        shimmerAnimator = ValueAnimator.ofFloat(-0.2f, 1.2f);
        shimmerAnimator.setDuration(1700L);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setRepeatMode(ValueAnimator.RESTART);
        shimmerAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        shimmerAnimator.addUpdateListener(animation -> {
            shimmerPhase = (float) animation.getAnimatedValue();
            if (shimmerActive) invalidate();
        });
        shimmerAnimator.start();
    }

    private void stopShimmer() {
        shimmerActive = false;
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
            shimmerAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopSettleAnimation();
        stopShimmer();
    }

    private void stopSettleAnimation() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
    }

    private void updateInteractionRenderLayer(boolean active) {
        if (interactionRenderActive == active && getLayerType() != LAYER_TYPE_NONE) {
            if (!active) return;
        }
        interactionRenderActive = active;
        setLayerType(active ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float z = dp(active ? 46 : 24);
            setElevation(z);
            setTranslationZ(z);
        }
    }

    private char pickLetter(float alongPx, boolean applyHysteresis) {
        int index;
        if (interactionMode == InteractionMode.WAVE_TRACK) {
            // A stale index survives a shorter alphabet by one slot, and indexing the letters with
            // it would throw in the middle of a scrub, so it is dropped rather than carried.
            int last = activeLetterIndex >= visibleLetters.length ? -1 : activeLetterIndex;
            activeLetterIndex = AzLetterTrack.indexWithHysteresis(alongPx, last,
                letterTrackLengthPx(), visibleLetters.length, applyHysteresis);
            index = activeLetterIndex;
        } else {
            index = indexForAlong(alongPx);
            activeLetterIndex = index;
        }
        return visibleLetters[index];
    }

    private int indexOfVisibleLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        for (int i = 0; i < visibleLetters.length; i++) {
            if (visibleLetters[i] == upper) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private static String[] buildGlyphStrings(@NonNull char[] letters) {
        String[] glyphs = new String[letters.length];
        for (int i = 0; i < letters.length; i++) {
            glyphs[i] = String.valueOf(letters[i]);
        }
        return glyphs;
    }

    private int resolveActiveIndex(float anchorAlong) {
        int activeIndex = indexForAlong(anchorAlong);
        if (interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK && lockedInlineLetter != null) {
            int lockedIndex = indexOfVisibleLetter(lockedInlineLetter);
            if (lockedIndex >= 0) {
                activeIndex = lockedIndex;
            }
        }
        return activeIndex;
    }

    private void updateInteractionLayerOffset() {
        setTranslationY(0f);
    }

    private int resolveFocusLetterColor() {
        int vivid = boostColor(accentColor, 1.34f, 1.18f);
        return blendColors(vivid, Color.WHITE, 0.22f);
    }


    private void applyLetterWeight(float envelope, boolean active) {
        float influence = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? (active ? 1f : 0f)
            : Math.max(0f, Math.min(1f, envelope * waveStrength));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int weight = (int) (420 + (influence * 380));
            if (active) weight = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 920 : 900;
            weight = Math.max(200, Math.min(900, weight));
            letterPaint.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        } else {
            if (active) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(true);
            } else if (influence > 0.55f) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(false);
            } else {
                letterPaint.setTypeface(Typeface.DEFAULT);
                letterPaint.setFakeBoldText(false);
            }
        }
    }

    private static int blendColors(int from, int to, float ratio) {
        float t = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int boostColor(int color, float saturationMultiplier, float valueMultiplier) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * saturationMultiplier));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * valueMultiplier));
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
