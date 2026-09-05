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
 * The status bar's place icons and lens edges. Three square icons show at any time, all on the
 * clock's own line: the left neighbour's at the bar's leading inset, then the place on screen at
 * home beside the clock — sized to the clock's time band — and the right neighbour's at the
 * trailing inset. The neighbours are smaller and quieter: peeking is a matter of weight, never of
 * being cut by the edge. A drag moves the three along one line — the arriving icon travels to home
 * as the one at home leaves through the far edge — and a tap on a neighbour slides the wall to
 * its place.
 *
 * <p>The outer strip of the glass on each side is a lens: a specular hairline over a brighter
 * wash.
 *
 * <p>The view lies over the whole bar but owns only the neighbours' touches; everything else falls
 * through to the bar's own drag.
 */
public final class StatusBarLensView extends View {

    public interface Listener {
        void onPlaceIconTapped(@NonNull PaneWallPage page);
    }

    /** The home icon's largest size in the expanded bar; the clock's band can ask for less. */
    public static final float ICON_DP = 36f;
    /** A neighbour's size as a share of the home icon's. */
    public static final float PEEK_SHARE = 0.78f;
    /** The gap between an icon and what comes after it. */
    public static final float ICON_GAP_DP = 8f;
    /** Every icon's size in the compact bar. */
    public static final float COMPACT_ICON_DP = 20f;
    /** The icons' inset from the bar's edges: the slot's gutter. */
    public static final float EDGE_INSET_DP = 12f;
    /** The expanded bar's slot height; the home icon centres on it until the clock says where. */
    private static final float SLOT_HEIGHT_DP = 68f;
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
    /** Where the clock puts the home icon in the expanded bar: its time line and band height. */
    private float mHomeCenterYPx = -1f;
    private float mHomeSizePx = -1f;
    private boolean mDisplayRunning;
    /** The status row's chip corner; the icons take it so they and the badge are one kit. */
    private float mChipRadiusPx = -1f;
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

    /** The clock's time line and band height, from the slot's layout; the home icon sits there. */
    public void setHomeAnchor(float centerYPx, float sizePx) {
        if (mHomeCenterYPx == centerYPx && mHomeSizePx == sizePx) return;
        mHomeCenterYPx = centerYPx;
        mHomeSizePx = sizePx;
        invalidate();
    }

    /** The corner the bar's chips wear; the icons round themselves the same way. */
    public void setChipRadiusPx(float radiusPx) {
        if (mChipRadiusPx == radiusPx) return;
        mChipRadiusPx = radiusPx;
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

    /** What the slot keeps clear at its start: the left neighbour, the home icon, their gaps. */
    public static int leadingCellWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((ICON_DP * PEEK_SHARE + ICON_GAP_DP + ICON_DP + ICON_GAP_DP) * density);
    }

    /** What the slot keeps clear at its end beside cards: the right neighbour and its gap. */
    public static int trailingCellWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((ICON_DP * PEEK_SHARE + ICON_GAP_DP) * density);
    }

    /** What the compact row keeps clear at its start: two icons and their gaps. */
    public static int compactLeadingWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((EDGE_INSET_DP + COMPACT_ICON_DP + ICON_GAP_DP + COMPACT_ICON_DP
            + ICON_GAP_DP) * density);
    }

    /** What the compact row keeps clear at its end: one icon and its gap. */
    public static int compactTrailingWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((EDGE_INSET_DP + COMPACT_ICON_DP + ICON_GAP_DP) * density);
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

        // The home icon grows from the compact row's square to the clock's band as the bar
        // unfolds, and its centre rises from the row to the time's line with it. Its neighbours
        // share the line, smaller; in the compact row all three are the same square.
        float expandedHome = mHomeSizePx > 0f ? Math.min(dp(ICON_DP), mHomeSizePx) : dp(ICON_DP);
        float expandedLine = mHomeCenterYPx >= 0f ? mHomeCenterYPx : dp(SLOT_HEIGHT_DP) / 2f;
        float homeSize = lerp(dp(COMPACT_ICON_DP), expandedHome, mExpansion);
        float peekSize = lerp(dp(COMPACT_ICON_DP), expandedHome * PEEK_SHARE, mExpansion);
        float line = lerp(height / 2f, expandedLine, mExpansion);
        float inset = dp(EDGE_INSET_DP);
        float gap = dp(ICON_GAP_DP);
        float home = inset + peekSize + gap;
        for (PaneWallPage page : mPages) {
            float t = StatusBarLensPolicy.distance(mPages, mCurrent, page, mOffsetPx, mWallWidthPx);
            float alpha = StatusBarLensPolicy.alpha(t);
            if (alpha <= 0.01f) continue;
            // An icon on its way between home and a side takes the size of the end it is nearer,
            // so the arriving one grows as it takes the home spot and the leaving one shrinks.
            float presence = StatusBarLensPolicy.presence(t);
            float size = lerp(homeSize, peekSize, presence) * StatusBarLensPolicy.scale(t);
            float centerY = line;
            float leftPeek = inset;
            float rightPeek = width - inset - size;
            float x = StatusBarLensPolicy.iconX(t, home, leftPeek, rightPeek, size);
            float centerX = x + size / 2f;
            mTile.set(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f);
            if (page != mCurrent) mHitRects[page.ordinal()].set(mTile);
            int accent = accentFor(getContext(), page);
            // Weight says what matters: the icon at home is the place you are on and reads full;
            // the two peeking in are where you could go and read quieter; a display that is not
            // running is quieter still.
            float ink = alpha * (1f - 0.38f * presence);
            if (page == PaneWallPage.DISPLAY && !mDisplayRunning) ink *= 0.6f;
            float radius = mChipRadiusPx >= 0f
                ? Math.min(size / 2f, mChipRadiusPx * (size / dp(COMPACT_ICON_DP)))
                : size * 0.32f;
            // Light: a tint and a thin line, so the icon marks the place without weighing on the
            // clock beside it; the glyph carries the identity.
            mTilePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(31 * ink)));
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(84 * ink)));
            mGlyphPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * ink)));
            mGlyphPaint.setTextSize(size * 0.5f);
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
            // A neighbour is a small target; give it a little air on every side.
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
