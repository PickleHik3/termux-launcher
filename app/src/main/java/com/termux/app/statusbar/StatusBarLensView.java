package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.Collections;
import java.util.List;

/**
 * The status bar's place icons and lens edges. Three square icons show at any time: the place on
 * screen wears its icon at home, beside the clock at the bar's start, and its two neighbours peek
 * in from the edges, half past them, saying where each direction leads. A drag moves the three
 * along one line — the arriving icon travels to home as the one at home leaves through the far
 * edge — and a tap on a peeking icon slides the wall to its place.
 *
 * <p>The outer strip of the glass on each side is a lens: a specular hairline over a brighter
 * wash. When a pinned notification or a media session holds the slot beside the clock, the
 * peeking icons drop into the status row's band so nothing crosses the cards.
 *
 * <p>The view lies over the whole bar but owns only the peeking icons' touches; everything else
 * falls through to the bar's own drag.
 */
public final class StatusBarLensView extends View {

    public interface Listener {
        void onPlaceIconTapped(@NonNull PaneWallPage page);
    }

    /** The home icon's size in the expanded bar, and the cell the slot keeps clear for it. */
    public static final float ICON_DP = 36f;
    /** The gap between the home icon and the clock. */
    public static final float ICON_GAP_DP = 10f;
    /** Every icon's size in the compact bar, and of the peeking icons in the row band. */
    public static final float COMPACT_ICON_DP = 20f;
    /** The home icon's left edge: the slot's gutter. */
    public static final float HOME_X_DP = 12f;
    /** The expanded bar's slot height; the home icon centres on it. */
    private static final float SLOT_HEIGHT_DP = 68f;
    /** Where the row band's centre sits above the expanded bar's bottom edge. */
    private static final float ROW_CENTER_FROM_BOTTOM_DP = 14f;
    private static final float HAIRLINE_DP = 1.5f;
    private static final float WASH_DP = 20f;

    private final Paint mTilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHairlinePaint = new Paint();
    private final Paint mWashPaint = new Paint();
    private final RectF mTile = new RectF();
    private final RectF[] mHitRects = new RectF[PaneWallPage.values().length];
    private final int mTouchSlop;

    @NonNull private List<PaneWallPage> mPages = Collections.singletonList(PaneWallPage.TERMINAL);
    @NonNull private PaneWallPage mCurrent = PaneWallPage.TERMINAL;
    private float mOffsetPx;
    private int mWallWidthPx;
    /** 0 in the compact bar, 1 in the expanded one, in between while it folds. */
    private float mExpansion = 1f;
    private boolean mCardsPresent;
    private boolean mDisplayRunning;
    @NonNull private String mDisplayGlyph = "";
    @Nullable private Listener mListener;
    @Nullable private PaneWallPage mPressed;
    private float mDownX;
    private float mDownY;

    public StatusBarLensView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1f));
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < mHitRects.length; i++) mHitRects[i] = new RectF();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    /** The wall's places, the one on screen, and how far the wall has moved from its rest. */
    public void setWallState(@NonNull List<PaneWallPage> pages, @NonNull PaneWallPage current,
                             float offsetPx, int wallWidthPx) {
        mPages = pages;
        mCurrent = current;
        mOffsetPx = offsetPx;
        mWallWidthPx = wallWidthPx;
        invalidate();
    }

    /** How far the bar is unfolded: 0 compact, 1 expanded. The icons grow and rise with it. */
    public void setExpansion(float expansion) {
        float clamped = Math.max(0f, Math.min(1f, expansion));
        if (mExpansion == clamped) return;
        mExpansion = clamped;
        invalidate();
    }

    /** A pinned notification or media session holds the slot: the peeking icons keep clear. */
    public void setCardsPresent(boolean present) {
        if (mCardsPresent == present) return;
        mCardsPresent = present;
        invalidate();
    }

    /** The Display icon reads quieter until a display runs. */
    public void setDisplayRunning(boolean running) {
        if (mDisplayRunning == running) return;
        mDisplayRunning = running;
        invalidate();
    }

    /** The Display place's mark: Termux X11's prompt, or the distribution the display serves. */
    public void setDisplayGlyph(@NonNull String glyph) {
        if (mDisplayGlyph.equals(glyph)) return;
        mDisplayGlyph = glyph;
        invalidate();
    }

    /** The place's accent: the same three roles the badge and chips take. */
    public static int accentFor(@NonNull Context context, @NonNull PaneWallPage page) {
        switch (page) {
            case WIDGETS:
                return MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorTertiary,
                    ContextCompat.getColor(context, R.color.termux_secondary));
            case DISPLAY:
                return ContextCompat.getColor(context, R.color.termux_place_display);
            default:
                return MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                    ContextCompat.getColor(context, R.color.termux_primary));
        }
    }

    /** The place's mark: a grid, a prompt, a screen. */
    @NonNull
    private String glyphFor(@NonNull PaneWallPage page) {
        switch (page) {
            case WIDGETS: return "";
            case DISPLAY: return mDisplayGlyph;
            default: return "";
        }
    }

    /** The width the slot keeps clear at its start for the home icon and its gap. */
    public static int homeCellWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((ICON_DP + ICON_GAP_DP) * density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int bright = ColorUtils.setAlphaComponent(Color.WHITE, 107);
        int dim = ColorUtils.setAlphaComponent(Color.WHITE, 13);
        mHairlinePaint.setShader(new LinearGradient(0f, 0f, 0f, h, bright, dim, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        drawLens(canvas, width, height);
        for (RectF rect : mHitRects) rect.setEmpty();
        if (mWallWidthPx <= 0 || mPages.isEmpty()) return;

        // The home icon grows from the compact row's square to the slot's as the bar unfolds, and
        // its centre rises from the row to the slot with it.
        float homeSize = lerp(dp(COMPACT_ICON_DP), dp(ICON_DP), mExpansion);
        float homeCenterY = lerp(height / 2f, dp(SLOT_HEIGHT_DP) / 2f, mExpansion);
        // Peeking icons share the home icon's line, unless cards hold the slot: then they keep to
        // the row band, and stay small, so nothing ever crosses a notification or the media strip.
        boolean rowBand = mCardsPresent && mExpansion > 0.5f;
        float peekSize = rowBand ? dp(COMPACT_ICON_DP) : homeSize;
        float peekCenterY = rowBand ? height - dp(ROW_CENTER_FROM_BOTTOM_DP) : homeCenterY;
        float home = dp(HOME_X_DP);
        for (PaneWallPage page : mPages) {
            float t = StatusBarLensPolicy.distance(mPages, mCurrent, page, mOffsetPx, mWallWidthPx);
            float alpha = StatusBarLensPolicy.alpha(t);
            if (alpha <= 0.01f) continue;
            // An icon on its way between home and an edge takes the size and line of the end it
            // is nearer, so the arriving one is already square-and-small in the band before it
            // reaches the cards and grows only as it takes the home spot.
            float presence = StatusBarLensPolicy.presence(t);
            float size = lerp(homeSize, peekSize, presence) * StatusBarLensPolicy.scale(t);
            float centerY = lerp(homeCenterY, peekCenterY, presence);
            float leftPeek = -size / 2f;
            float rightPeek = width - size / 2f;
            float x = StatusBarLensPolicy.iconX(t, home, leftPeek, rightPeek, size);
            float centerX = x + size / 2f;
            mTile.set(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f);
            if (page != mCurrent) mHitRects[page.ordinal()].set(mTile);
            int accent = accentFor(getContext(), page);
            boolean quiet = page == PaneWallPage.DISPLAY && !mDisplayRunning;
            float ink = quiet ? alpha * 0.6f : alpha;
            float radius = size * 0.32f;
            mTilePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(46 * ink)));
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(110 * ink)));
            mGlyphPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * ink)));
            mGlyphPaint.setTextSize(size * 0.52f);
            canvas.drawRoundRect(mTile, radius, radius, mTilePaint);
            canvas.drawRoundRect(mTile, radius, radius, mStrokePaint);
            float baseline = centerY - (mGlyphPaint.ascent() + mGlyphPaint.descent()) / 2f;
            canvas.drawText(glyphFor(page), centerX, baseline, mGlyphPaint);
        }
    }

    /** The lens itself: a brighter wash fading inward under a specular hairline at each edge. */
    private void drawLens(@NonNull Canvas canvas, int width, int height) {
        float wash = dp(WASH_DP);
        int washColor = ColorUtils.setAlphaComponent(Color.WHITE, 18);
        mWashPaint.setShader(new LinearGradient(0f, 0f, wash, 0f, washColor, Color.TRANSPARENT,
            Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, wash, height, mWashPaint);
        mWashPaint.setShader(new LinearGradient(width, 0f, width - wash, 0f, washColor,
            Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(width - wash, 0f, width, height, mWashPaint);
        float hairline = dp(HAIRLINE_DP);
        canvas.drawRect(0f, 0f, hairline, height, mHairlinePaint);
        canvas.drawRect(width - hairline, 0f, width, height, mHairlinePaint);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                PaneWallPage hit = iconAt(event.getX(), event.getY());
                if (hit == null) return false;
                mPressed = hit;
                mDownX = event.getX();
                mDownY = event.getY();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (mPressed != null && Math.hypot(event.getX() - mDownX, event.getY() - mDownY)
                    > mTouchSlop) {
                    mPressed = null;
                }
                return mPressed != null;
            case MotionEvent.ACTION_UP: {
                PaneWallPage pressed = mPressed;
                mPressed = null;
                if (pressed == null) return false;
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                if (mListener != null) mListener.onPlaceIconTapped(pressed);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mPressed = null;
                return false;
            default:
                return mPressed != null;
        }
    }

    /** Whether a touch lands on a peeking icon that is present enough to be a target. */
    public boolean isIconAt(float x, float y) {
        return iconAt(x, y) != null;
    }

    @Nullable
    private PaneWallPage iconAt(float x, float y) {
        if (mWallWidthPx <= 0) return null;
        for (PaneWallPage page : mPages) {
            if (page == mCurrent) continue;
            RectF rect = mHitRects[page.ordinal()];
            if (rect.isEmpty()) continue;
            // Half of a peeking icon is past the edge; the whole lens width is its target.
            float slop = dp(8f);
            if (x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop
                && y <= rect.bottom + slop) return page;
        }
        return null;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
