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
 * The lens edges of the status bar. The outer strip of the glass on each side is a lens: a
 * specular hairline over a brighter wash, with the neighbouring place's glyph resting half past
 * the edge and half dissolved. The glyphs are the only marks of place the bar carries — no switch
 * and no names — and each says where that direction leads: a tap on one slides the wall there,
 * and a drag brings it in along the lens until it dissolves as the place lands.
 *
 * <p>The view lies over the whole bar but owns only the glyphs' touches; everything else falls
 * through to the bar's own drag.
 */
public final class StatusBarLensView extends View {

    public interface Listener {
        void onPlaceGlyphTapped(@NonNull PaneWallPage page);
    }

    private static final float HOME_DP = 15f;
    private static final float IN_LEG_DP = 30f;
    private static final float PILL_WIDTH_DP = 30f;
    private static final float PILL_HEIGHT_DP = 76f;
    private static final float COMPACT_PILL_DP = 20f;
    private static final float GLYPH_SP = 20f;
    private static final float COMPACT_GLYPH_SP = 11f;
    private static final float HAIRLINE_DP = 1.5f;
    private static final float WASH_DP = 20f;

    private final Paint mPillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHairlinePaint = new Paint();
    private final Paint mWashPaint = new Paint();
    private final RectF mPill = new RectF();
    private final RectF[] mHitRects = new RectF[PaneWallPage.values().length];
    private final int mTouchSlop;

    @NonNull private List<PaneWallPage> mPages = Collections.singletonList(PaneWallPage.TERMINAL);
    @NonNull private PaneWallPage mCurrent = PaneWallPage.TERMINAL;
    private float mOffsetPx;
    private int mWallWidthPx;
    private boolean mCompact;
    private boolean mDisplayRunning;
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

    /** The compact bar wears small square glyphs; the expanded one tall pills. */
    public void setCompact(boolean compact) {
        if (mCompact == compact) return;
        mCompact = compact;
        invalidate();
    }

    /** The Display glyph reads quieter until a display runs. */
    public void setDisplayRunning(boolean running) {
        if (mDisplayRunning == running) return;
        mDisplayRunning = running;
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
    public static String glyphFor(@NonNull PaneWallPage page) {
        switch (page) {
            case WIDGETS: return "\uf00a";
            case DISPLAY: return "\uf108";
            default: return "\uf120";
        }
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
        if (mWallWidthPx <= 0 || mPages.size() < 2) return;
        float home = dp(HOME_DP);
        float inLeg = dp(IN_LEG_DP);
        float pillWidth = dp(mCompact ? COMPACT_PILL_DP : PILL_WIDTH_DP);
        float pillHeight = mCompact ? pillWidth : Math.min(height - dp(8f), dp(PILL_HEIGHT_DP));
        // Rest for a right-hand neighbour mirrors the left one: half past its edge.
        float outLeg = (width - pillWidth / 2f) - home;
        float radius = mCompact ? dp(7f) : pillWidth / 2f;
        mGlyphPaint.setTextSize(sp(mCompact ? COMPACT_GLYPH_SP : GLYPH_SP));
        for (PaneWallPage page : mPages) {
            float t = StatusBarLensPolicy.distance(mPages, mCurrent, page, mOffsetPx, mWallWidthPx);
            float alpha = StatusBarLensPolicy.alpha(t);
            if (alpha <= 0.01f) continue;
            float scale = StatusBarLensPolicy.scale(t);
            float x = StatusBarLensPolicy.lensX(t, home, inLeg, outLeg);
            float centerX = x + pillWidth / 2f;
            float centerY = height / 2f;
            float halfW = pillWidth * scale / 2f;
            float halfH = pillHeight * scale / 2f;
            mPill.set(centerX - halfW, centerY - halfH, centerX + halfW, centerY + halfH);
            mHitRects[page.ordinal()].set(mPill);
            int accent = accentFor(getContext(), page);
            boolean quiet = page == PaneWallPage.DISPLAY && !mDisplayRunning;
            float presence = quiet ? alpha * 0.55f : alpha;
            mPillPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(41 * presence)));
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(102 * presence)));
            mGlyphPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * presence)));
            canvas.drawRoundRect(mPill, radius * scale, radius * scale, mPillPaint);
            canvas.drawRoundRect(mPill, radius * scale, radius * scale, mStrokePaint);
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
                PaneWallPage hit = glyphAt(event.getX(), event.getY());
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
                if (mListener != null) mListener.onPlaceGlyphTapped(pressed);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mPressed = null;
                return false;
            default:
                return mPressed != null;
        }
    }

    /** Whether a touch lands on a glyph that is present enough to be a target. */
    public boolean isGlyphAt(float x, float y) {
        return glyphAt(x, y) != null;
    }

    @Nullable
    private PaneWallPage glyphAt(float x, float y) {
        if (mWallWidthPx <= 0) return null;
        for (PaneWallPage page : mPages) {
            if (page == mCurrent) continue;
            RectF rect = mHitRects[page.ordinal()];
            if (rect.isEmpty()) continue;
            float t = StatusBarLensPolicy.distance(mPages, mCurrent, page, mOffsetPx, mWallWidthPx);
            if (StatusBarLensPolicy.presence(t) < 0.5f) continue;
            // A pill half past the edge is a small target; give it the whole lens width.
            float slop = dp(8f);
            if (x >= rect.left - slop && x <= rect.right + slop && y >= rect.top - slop
                && y <= rect.bottom + slop) return page;
        }
        return null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
