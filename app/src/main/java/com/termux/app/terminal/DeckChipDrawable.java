package com.termux.app.terminal;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.WindowActivityRing;

/**
 * A window chip (or the "+") as a raised deck button (wave 8, the deck look): a rounded 4 dp key
 * in the deck's key colour with a 1 dp lighter top edge and a 2 dp darker foot, no outline, and,
 * on a window chip, a 5 dp LED at its leading edge whose colour {@link DeckChipLed} decides. The
 * LED's breath is read from the bar's one clock at draw time, like the plain chip's ring.
 */
public final class DeckChipDrawable extends Drawable {

    public static final float CORNER_DP = 4f;
    public static final float TOP_EDGE_DP = 1f;
    public static final float FOOT_DP = 2f;
    public static final float LED_DP = 5f;
    /** Where the LED starts from the chip's leading edge: the chip's own side padding. */
    public static final float LED_INSET_DP = 3.5f;
    /** How far the title stands from the chip's leading edge with the LED before it. */
    public static final float TITLE_START_DP = LED_INSET_DP + 9f;
    /** A still working LED (lazy mode, or a percentage the bar does not breathe): half-lit. */
    private static final float STILL_WORKING_LEVEL = 0.5f;

    private final float mDensity;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    @Nullable private DeckLook mDeck;
    private boolean mLed;
    private boolean mRtl;
    private boolean mCurrent;
    private boolean mAttention;
    private boolean mWorking;
    private boolean mBreathes;

    public DeckChipDrawable(float density) {
        mDensity = density;
    }

    public void setDeck(@NonNull DeckLook deck) {
        if (deck.equals(mDeck)) return;
        mDeck = deck;
        invalidateSelf();
    }

    /** Whether this chip carries an LED: a window's chip does, the "+" does not. */
    public void setLed(boolean led) {
        if (mLed == led) return;
        mLed = led;
        invalidateSelf();
    }

    public void setRtl(boolean rtl) {
        if (mRtl == rtl) return;
        mRtl = rtl;
        invalidateSelf();
    }

    /**
     * The window's state for the LED. {@code breathes} when the bar's clock is redrawing this
     * chip, so the LED follows the breath; a working chip without the clock is half-lit.
     */
    public void setState(boolean current, boolean attention, boolean working, boolean breathes) {
        if (mCurrent == current && mAttention == attention && mWorking == working
            && mBreathes == breathes) return;
        mCurrent = current;
        mAttention = attention;
        mWorking = working;
        mBreathes = breathes;
        invalidateSelf();
    }

    public void setCurrent(boolean current) {
        if (mCurrent == current) return;
        mCurrent = current;
        invalidateSelf();
    }

    /** The LED's colour now; for tests and for {@link #draw}. */
    int ledColour() {
        DeckLook deck = mDeck;
        if (deck == null) return 0;
        float breath = !mWorking ? 0f : mBreathes
            ? WindowActivityRing.breatheLevel(SystemClock.uptimeMillis()) : STILL_WORKING_LEVEL;
        return DeckChipLed.colour(deck.lit, deck.keyFoot, mCurrent, mAttention, mWorking, breath);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        DeckLook deck = mDeck;
        if (deck == null) return;
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float radius = CORNER_DP * mDensity;
        float foot = FOOT_DP * mDensity;
        float top = TOP_EDGE_DP * mDensity;
        // The foot: the whole key in its dark colour, of which only the bottom 2 dp shows.
        mRect.set(b);
        mPaint.setColor(deck.keyFoot);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        // The lit top edge, then the key's face 1 dp below it, both standing on the foot.
        mRect.set(b.left, b.top, b.right, b.bottom - foot);
        mPaint.setColor(deck.keyTop);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        mRect.top += top;
        mPaint.setColor(deck.key);
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
        if (!mLed) return;
        float led = LED_DP * mDensity;
        float inset = LED_INSET_DP * mDensity;
        float cx = mRtl ? b.right - inset - led / 2f : b.left + inset + led / 2f;
        float cy = b.top + (b.height() - foot) / 2f;
        mPaint.setColor(ledColour());
        canvas.drawCircle(cx, cy, led / 2f, mPaint);
    }

    @Override public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
