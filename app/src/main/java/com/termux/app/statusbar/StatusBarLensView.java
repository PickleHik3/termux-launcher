package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Color;
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
 * The status bar's place icons and lens edges, drawn behind everything the bar shows. The two
 * neighbours rest half past the edges, small and quiet, saying where each direction leads; the
 * place on screen wears its icon at home beside the clock in the expanded bar — sized to the
 * clock's time band, on its line — and has no icon in the compact row, whose content is the
 * place's own. None of it takes room from the row. A drag moves the icons along one line — the
 * arriving one travels to home as the one at home leaves through the far edge — and a tap on a
 * neighbour slides the wall to its place.
 *
 * <p>The icon at home carries a soft glow of its own colour and its neighbours are drained
 * towards the surface's neutral, so the place you are on is the one that reads.
 *
 * <p>The view lies under the bar's content but owns the neighbours' touches: nothing above it
 * claims the bar's edges, so a tap there reaches it.
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
    /** The home icon's leading edge, clear of the neighbour peeking past the bar's near edge. */
    public static final float HOME_X_DP = 20f;
    /** The expanded bar's slot height; the home icon centres on it until the clock says where. */
    private static final float SLOT_HEIGHT_DP = 68f;
    /** How far the home icon's glow reaches past its edge, as a share of its size. */
    private static final float GLOW_REACH = 0.55f;
    /** How far the neighbours' colour is drained towards the surface's neutral. */
    private static final float NEIGHBOUR_DRAIN = 0.55f;

    private final Paint mTilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Fades a neighbour towards the edge it peeks past: DST_IN over the icon's own layer. */
    private final Paint mFadePaint = new Paint();
    private final RectF mTile = new RectF();
    private final RectF mGlow = new RectF();
    private final int mNeutral;
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
    /** The bar stands in a column, so the icons travel down its length instead of across it. */
    private boolean mVertical;
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
        mNeutral = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mFadePaint.setXfermode(new android.graphics.PorterDuffXfermode(
            android.graphics.PorterDuff.Mode.DST_IN));
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

    /**
     * Whether the bar the lens lives in stands in a column. The places then queue along the bar's
     * height — the one above waiting past the top edge, the one below past the bottom — which is
     * the same line the wall is paged along there.
     */
    public void setVertical(boolean vertical) {
        if (mVertical == vertical) return;
        mVertical = vertical;
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
            case WIDGETS: return "\uf015";
            case DISPLAY: return mDisplayGlyph;
            default: return "\uf120";
        }
    }

    /** What the slot keeps clear at its start, from the bar's edge: the home icon and its gap. */
    public static int leadingCellWidthPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((HOME_X_DP + ICON_DP + ICON_GAP_DP) * density);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        for (RectF rect : mHitRects) rect.setEmpty();
        if (mWallWidthPx <= 0 || mPages.isEmpty()) return;

        // The two axes the lens is drawn in: the places queue along the bar's length, and the
        // line they share runs across it. On a row that is x and y; on a column it is y and x.
        float along = mVertical ? height : width;
        float across = mVertical ? width : height;

        // In the expanded bar the home icon takes the clock's band and its line; its neighbours
        // share the line, smaller, half past the edges. The compact row is the place's own content
        // and carries no home icon: there the neighbours alone peek in, and an icon fades as it
        // arrives at home, so the row is never crowded.
        float expandedHome = mHomeSizePx > 0f ? Math.min(dp(ICON_DP), mHomeSizePx) : dp(ICON_DP);
        float expandedLine = mHomeCenterYPx >= 0f && !mVertical
            ? mHomeCenterYPx : dp(SLOT_HEIGHT_DP) / 2f;
        float homeSize = lerp(dp(COMPACT_ICON_DP), expandedHome, mExpansion);
        float peekSize = lerp(dp(COMPACT_ICON_DP), expandedHome * PEEK_SHARE, mExpansion);
        // A column's line is simply its middle: there is no clock band beside the icons there.
        float line = mVertical ? across / 2f : lerp(across / 2f, expandedLine, mExpansion);
        float home = dp(HOME_X_DP);
        for (PaneWallPage page : mPages) {
            float t = StatusBarLensPolicy.distance(mPages, mCurrent, page, mOffsetPx, mWallWidthPx);
            float presence = StatusBarLensPolicy.presence(t);
            // Whole at home only while the bar is expanded; in the compact row the icon at home
            // is not there, so an arriving icon dissolves over its last stretch.
            float alpha = StatusBarLensPolicy.alpha(t) * lerp(presence, 1f, mExpansion);
            if (alpha <= 0.01f) continue;
            // An icon on its way between home and a side takes the size of the end it is nearer,
            // so the arriving one grows as it takes the home spot and the leaving one shrinks.
            float size = lerp(homeSize, peekSize, presence) * StatusBarLensPolicy.scale(t);
            float nearPeek = -size / 2f;
            float farPeek = along - size / 2f;
            float travelled = StatusBarLensPolicy.iconX(t, home, nearPeek, farPeek, size)
                + size / 2f;
            float centerX = mVertical ? line : travelled;
            float centerY = mVertical ? travelled : line;
            mTile.set(centerX - size / 2f, centerY - size / 2f, centerX + size / 2f, centerY + size / 2f);
            if (page != mCurrent) mHitRects[page.ordinal()].set(mTile);
            // Weight and colour say what matters: the icon at home is the place you are on, in
            // its own colour with a glow; the two peeking in are where you could go, drained
            // towards the surface's neutral and quieter; a display that is not running is quieter
            // still.
            int accent = ColorUtils.blendARGB(accentFor(getContext(), page), mNeutral,
                NEIGHBOUR_DRAIN * presence);
            float ink = alpha * (1f - 0.38f * presence);
            if (page == PaneWallPage.DISPLAY && !mDisplayRunning) ink *= 0.6f;
            float glow = alpha * (1f - presence) * mExpansion;
            if (glow > 0.01f) {
                float reach = size * GLOW_REACH;
                mGlow.set(mTile.left - reach, mTile.top - reach, mTile.right + reach, mTile.bottom + reach);
                mGlowPaint.setShader(new android.graphics.RadialGradient(centerX, centerY,
                    size / 2f + reach,
                    new int[] {ColorUtils.setAlphaComponent(accent, Math.round(64 * glow)),
                        ColorUtils.setAlphaComponent(accent, Math.round(22 * glow)), Color.TRANSPARENT},
                    new float[] {0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(mGlow, mGlow.width() / 2f, mGlow.height() / 2f, mGlowPaint);
            }
            float radius = mChipRadiusPx >= 0f
                ? Math.min(size / 2f, mChipRadiusPx * (size / dp(COMPACT_ICON_DP)))
                : size * 0.32f;
            // Light: a tint and a thin line, so the icon marks the place without weighing on the
            // clock beside it; the glyph carries the identity.
            mTilePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(31 * ink)));
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(84 * ink)));
            mGlyphPaint.setColor(ColorUtils.setAlphaComponent(accent, Math.round(255 * ink)));
            mGlyphPaint.setTextSize(size * 0.5f);
            // A neighbour dissolves towards the edge it peeks past: its own layer, then a
            // gradient that keeps the inner side and lets the outer side go. The fade is as
            // strong as the icon is far from home, so an arriving icon becomes whole as it lands.
            boolean fades = presence > 0.05f && t != 0f;
            int layer = -1;
            if (fades) {
                layer = canvas.saveLayer(mTile.left - 1f, mTile.top - 1f, mTile.right + 1f,
                    mTile.bottom + 1f, null);
            }
            canvas.drawRoundRect(mTile, radius, radius, mTilePaint);
            canvas.drawRoundRect(mTile, radius, radius, mStrokePaint);
            float baseline = centerY - (mGlyphPaint.ascent() + mGlyphPaint.descent()) / 2f;
            canvas.drawText(glyphFor(page), centerX, baseline, mGlyphPaint);
            if (fades) {
                boolean fromNear = t < 0f;
                float outer = mVertical
                    ? (fromNear ? mTile.top : mTile.bottom)
                    : (fromNear ? mTile.left : mTile.right);
                float inner = mVertical
                    ? (fromNear ? mTile.bottom : mTile.top)
                    : (fromNear ? mTile.right : mTile.left);
                int outerColor = ColorUtils.setAlphaComponent(Color.WHITE,
                    Math.round(255 * (1f - presence)));
                mFadePaint.setShader(mVertical
                    ? new LinearGradient(0f, outer, 0f, inner, outerColor, Color.WHITE,
                        Shader.TileMode.CLAMP)
                    : new LinearGradient(outer, 0f, inner, 0f, outerColor, Color.WHITE,
                        Shader.TileMode.CLAMP));
                canvas.drawRect(mTile.left - 1f, mTile.top - 1f, mTile.right + 1f, mTile.bottom + 1f,
                    mFadePaint);
                canvas.restoreToCount(layer);
            }
        }
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
            // Half of a neighbour is past the edge; the visible half plus a little air is its
            // target.
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
