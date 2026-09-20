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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.OnGlass;
import com.termux.app.place.PlaceLayout.Edge;
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
 * <p>A place is drawn in the colour its switch wears in the extra-keys row — the host hands those
 * colours over whenever the row restates them, so a key whose role the user changed changes the
 * mark with it — and the row's heuristic comes with the colours: the icon at home is at full
 * strength, with a soft glow of its own, and its neighbours are the same colour faded. A neighbour
 * is quieter, not faint: {@link StatusBarLensMetrics} holds every peeking mark to a legibility
 * floor and grows its target to the platform's minimum, and the floor wins over the fade.
 *
 * <p>The view lies under the bar's content but owns the neighbours' touches: nothing above it
 * claims the bar's edges, so a tap there reaches it. It paints and hit-tests what the metrics say;
 * it works out none of it.
 */
public final class StatusBarLensView extends View {

    public interface Listener {
        void onPlaceIconTapped(@NonNull PaneWallPage page);
    }

    private final Paint mTilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Fades a neighbour towards the edge it peeks past: DST_IN over the icon's own layer. */
    private final Paint mFadePaint = new Paint();
    private final RectF mTile = new RectF();
    private final RectF mGlow = new RectF();
    private final RectF[] mHitRects = new RectF[PaneWallPage.values().length];
    private final int mTouchSlop;

    /**
     * The contrast a mark's colour is toned to before anything is done to it. A shade above
     * {@link OnGlass#TARGET_LARGE_TEXT} on purpose: the drain and the mark's own alpha both act on
     * the toned colour afterwards, and the margin keeps the cheap path — a colour that already
     * reads — from being re-toned, which is an HCT search, in a view that redraws with every pixel
     * of a wall drag.
     */
    private static final double TONE_TARGET = OnGlass.TARGET_LARGE_TEXT * 1.04d;

    /** The colour each place's switch key wears in the extra-keys row; absent until the row says. */
    private final int[] mRowAccents = new int[PaneWallPage.values().length];
    private final boolean[] mHasRowAccent = new boolean[PaneWallPage.values().length];

    /** The opaque colour the bar is standing on, as the chrome measured it; null until it has. */
    @Nullable private Integer mBandSurface;
    /** Each place's colour toned onto {@link #mBandSurface}; rebuilt when the band moves. */
    private final int[] mTonedAccents = new int[PaneWallPage.values().length];
    private boolean mTonedAccentsValid;

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
    /** What a column's surface reaches past its content at each end: the system bars. */
    private int mAlongStartPx;
    private int mAlongEndPx;
    /** The bar stands along the bottom: the clock, and the line the icons share, are at its foot. */
    private boolean mBottom;
    @NonNull private String mDisplayGlyph = "";
    @Nullable private Listener mListener;
    @Nullable private PaneWallPage mPressed;
    private float mDownX;
    private float mDownY;

    public StatusBarLensView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
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

    /**
     * The edge the bar stands on. A column queues the places along the bar's height; a row along
     * the bottom keeps its clock at its foot, so until the clock says where its line is, that is
     * where the icons rest.
     */
    public void setEdge(@NonNull Edge edge) {
        boolean vertical = edge == Edge.LEFT || edge == Edge.RIGHT;
        boolean bottom = edge == Edge.BOTTOM;
        if (mVertical == vertical && mBottom == bottom) return;
        mVertical = vertical;
        mBottom = bottom;
        invalidate();
    }

    /**
     * How much of a column's length the system bars hold at each end. The surface runs the whole
     * display, so without this the place above would queue behind the system status bar and the
     * one below under the navigation bar, where neither can be seen or tapped.
     */
    public void setAlongInsets(int startPx, int endPx) {
        int start = Math.max(0, startPx);
        int end = Math.max(0, endPx);
        if (mAlongStartPx == start && mAlongEndPx == end) return;
        mAlongStartPx = start;
        mAlongEndPx = end;
        invalidate();
    }

    /**
     * The clock's time line and band height, in this view's own coordinates — the slot reports
     * its clock's line relative to itself, and the host offsets it by where the slot stands.
     */
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

    /**
     * What the chrome measured the bar's band to be, so a place's colour can be toned onto it.
     *
     * <p>Until this arrives the lens paints what it always painted, which on the reporting device
     * was a place mark at 1.11:1 — the mark's own blue on a glass of nearly the same luminance.</p>
     */
    public void setBandSurface(@ColorInt int bandSurface) {
        if (mBandSurface != null && mBandSurface == bandSurface) return;
        mBandSurface = bandSurface;
        mTonedAccentsValid = false;
        invalidate();
    }

    /**
     * The colours the extra-keys row is painting its place switches in, so the bar shows a place in
     * the colour its key shows it in. A place the row has no switch for keeps {@link #accentFor}.
     *
     * <p>The row is the authority because the colours are the user's: a switch takes its role's
     * colour, and a role can be changed in the extra-keys editor. So this arrives again every time
     * the row restates its keys rather than being read once from the theme.
     */
    public void setPlaceAccents(@Nullable java.util.Map<PaneWallPage, Integer> accents) {
        boolean changed = false;
        for (PaneWallPage page : PaneWallPage.values()) {
            Integer given = accents == null ? null : accents.get(page);
            boolean has = given != null;
            int color = has ? given : 0;
            if (mHasRowAccent[page.ordinal()] == has && mRowAccents[page.ordinal()] == color)
                continue;
            mHasRowAccent[page.ordinal()] = has;
            mRowAccents[page.ordinal()] = color;
            changed = true;
        }
        if (!changed) return;
        mTonedAccentsValid = false;
        invalidate();
    }

    /** The place's colour: the row's, when the row stands a switch for it. */
    @ColorInt
    @androidx.annotation.VisibleForTesting
    int accent(@NonNull PaneWallPage page) {
        return mHasRowAccent[page.ordinal()]
            ? mRowAccents[page.ordinal()] : accentFor(getContext(), page);
    }

    /**
     * The place's colour moved along its own tone axis until it reads on the band — once per band,
     * not once per mark per frame: the search behind it walks a hundred HCT tones and the lens
     * redraws continuously while the wall is dragged.
     */
    @ColorInt
    private int tonedAccent(@NonNull PaneWallPage page) {
        Integer surface = mBandSurface;
        if (surface == null) return accent(page);
        if (!mTonedAccentsValid) {
            for (PaneWallPage candidate : PaneWallPage.values()) {
                mTonedAccents[candidate.ordinal()] = OnGlass.resolveBare(surface,
                    accent(candidate), TONE_TARGET).ink;
            }
            mTonedAccentsValid = true;
        }
        return mTonedAccents[page.ordinal()];
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
        return StatusBarLensMetrics.leadingCellWidthPx(
            context.getResources().getDisplayMetrics().density);
    }

    /** The bar as the metrics see it: its size, how it stands, and what the clock has said. */
    @NonNull
    private StatusBarLensMetrics.Bar bar(int width, int height) {
        return new StatusBarLensMetrics.Bar(width, height,
            getResources().getDisplayMetrics().density, mVertical, mBottom, mExpansion,
            mAlongStartPx, mAlongEndPx, mHomeCenterYPx, mHomeSizePx, mChipRadiusPx);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        for (RectF rect : mHitRects) rect.setEmpty();
        if (mWallWidthPx <= 0 || mPages.isEmpty()) return;

        List<StatusBarLensMetrics.Mark> marks = StatusBarLensMetrics.marks(bar(width, height),
            mPages, mCurrent, mOffsetPx, mWallWidthPx, mDisplayRunning);
        for (StatusBarLensMetrics.Mark mark : marks) {
            mTile.set(mark.tile.left, mark.tile.top, mark.tile.right, mark.tile.bottom);
            if (!mark.home) {
                mHitRects[mark.page.ordinal()].set(mark.target.left, mark.target.top,
                    mark.target.right, mark.target.bottom);
            }
            // Weight and colour say what matters, and the row already says it: the place in front
            // is its own colour at full strength, the ones behind it are that same colour faded.
            // So all three marks are painted in the place's colour and only the glyph's strength
            // separates them — where a neighbour used to be drained towards a grey, which said the
            // same thing in a colour the key beside it never shows.
            int accent = tonedAccent(mark.page);
            if (mark.glow > 0.01f) {
                float reach = mark.sizePx * StatusBarLensMetrics.GLOW_REACH;
                mGlow.set(mTile.left - reach, mTile.top - reach, mTile.right + reach,
                    mTile.bottom + reach);
                mGlowPaint.setShader(new android.graphics.RadialGradient(mark.centerX, mark.centerY,
                    mark.sizePx / 2f + reach,
                    new int[] {ColorUtils.setAlphaComponent(accent, Math.round(64 * mark.glow)),
                        ColorUtils.setAlphaComponent(accent, Math.round(22 * mark.glow)),
                        Color.TRANSPARENT},
                    new float[] {0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(mGlow, mGlow.width() / 2f, mGlow.height() / 2f, mGlowPaint);
            }
            // Light: a tint and a thin line, so the icon marks the place without weighing on the
            // clock beside it; the glyph carries the identity.
            mTilePaint.setColor(ColorUtils.setAlphaComponent(accent,
                Math.round(StatusBarLensMetrics.FILL_ALPHA * mark.ink)));
            mStrokePaint.setColor(ColorUtils.setAlphaComponent(accent,
                Math.round(StatusBarLensMetrics.STROKE_ALPHA * mark.ink)));
            // The tint and the line are the mark's weight; the glyph is the mark. So the glyph is
            // the one that carries the row's fade, and the one held to a floor — held to it at the
            // alpha it is really drawn with, because a fraction of a ratio is not that ratio. When
            // the two disagree the floor wins: the fade keeps its alpha and the colour is walked
            // up the tone axis until what lands on the band reads.
            int glyphAlpha = Math.round(StatusBarLensMetrics.GLYPH_ALPHA * mark.glyphInk);
            mGlyphPaint.setColor(mBandSurface == null
                ? ColorUtils.setAlphaComponent(accent, glyphAlpha)
                : StatusBarInk.inkAtAlpha(mBandSurface, accent, glyphAlpha,
                    OnGlass.TARGET_LARGE_TEXT));
            mGlyphPaint.setTextSize(mark.glyphSizePx);
            // A neighbour dissolves towards the end it peeks past: its own layer, then a gradient
            // that keeps the inner side and lets the outer side go. The dissolve is deepest when
            // the mark is furthest from home, and it keeps half its ink there — the outer half of
            // a resting neighbour is past the bar anyway, so taking the rest to nothing only cost
            // the glyph that is still on screen.
            int layer = -1;
            if (mark.fades) {
                layer = canvas.saveLayer(mTile.left - 1f, mTile.top - 1f, mTile.right + 1f,
                    mTile.bottom + 1f, null);
            }
            canvas.drawRoundRect(mTile, mark.radiusPx, mark.radiusPx, mTilePaint);
            canvas.drawRoundRect(mTile, mark.radiusPx, mark.radiusPx, mStrokePaint);
            float baseline = mark.centerY - (mGlyphPaint.ascent() + mGlyphPaint.descent()) / 2f;
            canvas.drawText(glyphFor(mark.page), mark.centerX, baseline, mGlyphPaint);
            if (mark.fades) {
                boolean fromNear = mark.fadesFromNearEnd;
                float outer = mVertical
                    ? (fromNear ? mTile.top : mTile.bottom)
                    : (fromNear ? mTile.left : mTile.right);
                float inner = mVertical
                    ? (fromNear ? mTile.bottom : mTile.top)
                    : (fromNear ? mTile.right : mTile.left);
                int outerColor = ColorUtils.setAlphaComponent(Color.WHITE,
                    Math.round(255 * mark.fadeOuterAlpha));
                mFadePaint.setShader(mVertical
                    ? new LinearGradient(0f, outer, 0f, inner, outerColor, Color.WHITE,
                        Shader.TileMode.CLAMP)
                    : new LinearGradient(outer, 0f, inner, 0f, outerColor, Color.WHITE,
                        Shader.TileMode.CLAMP));
                canvas.drawRect(mTile.left - 1f, mTile.top - 1f, mTile.right + 1f,
                    mTile.bottom + 1f, mFadePaint);
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
            // The rect is the target the metrics already grew to the platform's minimum: half of a
            // neighbour is past the bar's end and cannot be touched, so the target reaches inward
            // instead of sitting evenly around what is drawn.
            if (rect.contains(x, y)) return page;
        }
        return null;
    }
}
