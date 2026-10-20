package com.termux.app.dock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.AnimationSpeed;
import com.termux.app.ReducedMotion;
import com.termux.app.theme.SchemeTone;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Wall strip's first row (spec "Wall strip"): a large clock with the date, then the
 * Workspace's terminals as tiles and a {@code +} tile, laid out by {@link WallStripRowPolicy}.
 *
 * <p>Each terminal tile is a dark well showing its window's split shape and title; the window in
 * front wears an accent ring, one asking for the user an amber ring and dot. {@code +} is a
 * lighter dashed well. When the count crosses two and three the clock slides out to the left, or
 * back in ({@link WallStripRowPolicy#CLOCK_SLIDE_MS}); past four terminals the row scrolls under a
 * horizontal drag.</p>
 *
 * <p>Dumb: the host hands it the tiles ({@link #setTiles}, from {@link #tilesOf}) and the dock's
 * geometry ({@link #setGeometry}), and routes taps. Its background is clear: the slab is the
 * dock's. Nothing repaints at rest; the clock ticks once a minute while it is shown.</p>
 */
public final class WallStripRowView extends View {

    /** Taps on the row. */
    public interface Listener {
        /** Terminal tile for chip {@code chipIndex}, as the window bar counts chips. */
        void onTileTapped(int chipIndex);

        /** A long-press on that tile: the window's own menu, anchored at {@code anchor}. */
        void onTileLongPressed(int chipIndex, @NonNull Rect anchor);

        /** The {@code +} tile. */
        void onNewTerminalTapped();
    }

    /** One terminal tile: a row window, its title and split shape. */
    public static final class Tile {
        /** The window's chip, as the window bar counts chips. */
        public final int chipIndex;
        @NonNull public final String title;
        @NonNull public final String spokenLabel;
        public final boolean current;
        public final boolean attention;
        /** The window's panes as fractions of it, four floats a pane. */
        @NonNull final float[] shape;

        public Tile(int chipIndex, @NonNull String title, @NonNull String spokenLabel,
                    boolean current, boolean attention, @NonNull float[] shape) {
            this.chipIndex = chipIndex;
            this.title = title;
            this.spokenLabel = spokenLabel;
            this.current = current;
            this.attention = attention;
            this.shape = shape.clone();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Tile)) return false;
            Tile that = (Tile) o;
            return chipIndex == that.chipIndex && current == that.current
                && attention == that.attention && title.equals(that.title)
                && spokenLabel.equals(that.spokenLabel) && Arrays.equals(shape, that.shape);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * chipIndex + title.hashCode()) + Arrays.hashCode(shape)
                + (current ? 1 : 0) + (attention ? 2 : 0);
        }
    }

    /**
     * The tiles for the row's own entries ({@link RunStripModel#rowEntries}), each titled with its
     * number and label. {@code shapes} runs alongside them ({@link WallStripRowPolicy#splitShape});
     * a missing or null shape reads as one whole pane.
     */
    @NonNull
    public static List<Tile> tilesOf(@NonNull List<RunStripModel.Entry> rowEntries,
                                     @Nullable List<float[]> shapes) {
        List<Tile> tiles = new ArrayList<>(rowEntries.size());
        for (int i = 0; i < rowEntries.size(); i++) {
            RunStripModel.Entry entry = rowEntries.get(i);
            if (entry.kind == RunStripModel.Kind.NEW_WINDOW) continue;
            float[] shape = shapes != null && i < shapes.size() ? shapes.get(i) : null;
            String title = entry.label.isEmpty() ? entry.number : entry.number + " " + entry.label;
            tiles.add(new Tile(entry.chipIndex, title, entry.spokenLabel, entry.current,
                entry.attention, shape == null ? WallStripRowPolicy.splitShape(null) : shape));
        }
        return tiles;
    }

    /** The row's height. */
    public static final float HEIGHT_DP = 84f;
    static final float DEFAULT_INSET_DP = 14f;
    static final float DEFAULT_GAP_DP = 10f;
    static final float TILE_PAD_DP = 7f;
    static final float SHAPE_GAP_DP = 2f;
    static final float SHAPE_RADIUS_DP = 3f;
    static final float TITLE_GAP_DP = 5f;
    static final float RING_DP = 1.5f;
    static final float DOT_DP = 7f;
    static final float TIME_SP = 50f;
    static final float DATE_SP = 13f;
    static final float TITLE_SP = 12f;
    static final float PLUS_SP = 26f;
    /** The clock's time fills at most this share of the row's height. */
    static final float TIME_MAX_HEIGHT_SHARE = 0.62f;
    /** The date reads a step quieter than the time. */
    static final float DATE_INK_SHARE = 0.8f;
    /** The date's skeleton for the locale: short weekday, day, short month. */
    static final String DATE_SKELETON = "EEEdMMM";
    /** The ease the clock's slide runs on, as the mockup's cubic-bezier(.2,.8,.2,1). */
    static final PathInterpolator SLIDE_EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    /** Alphas of the tile wells, rims and pane blocks. */
    static final float WELL_ALPHA = 0.5f;
    static final int REST_RIM_ALPHA = 72;
    static final int PANE_ALPHA = 56;
    static final int PLUS_FILL_ALPHA = 18;
    static final int PLUS_RIM_ALPHA = 56;
    static final int PLUS_INK_ALPHA = 190;
    /** Non-text marks clear 3:1 against the well (R9). */
    static final double MARK_CONTRAST = 3d;
    static final int ANSI_YELLOW = 3;

    private final float mDensity;
    private final int mTouchSlop;
    private final TextPaint mTimePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mDatePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPlusPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final RowAccessibility mAccessibility;

    @Nullable private Listener mListener;
    @NonNull private List<Tile> mTiles = Collections.emptyList();
    /** Whether the host has bound the row once; the first binding never slides. */
    private boolean mBound;
    @NonNull private String mNewTerminalSpoken;

    private float mInsetPx;
    private float mGapPx;
    private float mDockRadiusPx;
    private float mDockPaddingPx;

    /** The clock's share now: its rest share, or a step of its slide. */
    private float mClockShare = WallStripRowPolicy.clockShare(0);
    @Nullable private ValueAnimator mSlide;
    /** The clock's width at the wider end of the slide, so the clock slides rather than squeezes. */
    private float mClockFullWidthPx;
    @NonNull private WallStripRowPolicy.Row mRow;
    private float mScrollPx;
    @NonNull private CharSequence[] mTitles = new CharSequence[0];

    @NonNull private String mTime = "";
    @NonNull private String mDate = "";
    @NonNull private CharSequence mFittedDate = "";
    private final Runnable mTick = this::tick;

    @ColorInt private int mAccent;
    @ColorInt private int mClockInk;
    @ColorInt private int mWell;
    @ColorInt private int mTitleInk;
    @ColorInt private int mAmber;
    @ColorInt private int mOnSurface;

    private int mDownTile = -1;
    private float mDownX;
    private float mDownY;
    private float mDownScroll;
    private boolean mDragging;
    private boolean mLongPressed;
    private final Runnable mLongPress = this::longPress;

    public WallStripRowView(@NonNull Context context) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mInsetPx = DEFAULT_INSET_DP * mDensity;
        mGapPx = DEFAULT_GAP_DP * mDensity;
        mNewTerminalSpoken = context.getString(R.string.dock_wall_strip_new_terminal);
        mTimePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mTitlePaint.setTypeface(Typeface.MONOSPACE);
        mPlusPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        mPlusPaint.setTextAlign(Paint.Align.CENTER);
        float ring = RING_DP * mDensity;
        mRim.setStyle(Paint.Style.STROKE);
        mRim.setStrokeWidth(ring);
        mDashed.setStyle(Paint.Style.STROKE);
        mDashed.setStrokeWidth(ring);
        mDashed.setPathEffect(new DashPathEffect(new float[]{4f * mDensity, 3f * mDensity}, 0f));
        applyTextSizes();
        readThemeColors();
        mRow = WallStripRowPolicy.layout(0f, mInsetPx, mGapPx, 0, 0f, 0f);
        mAccessibility = new RowAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * The dock's geometry: the apps row's side inset, the gap between tiles, and the slab's corner
     * radius and padding, from which the tiles' radius nests.
     */
    public void setGeometry(float insetPx, float gapPx, float dockRadiusPx, float dockPaddingPx) {
        if (insetPx == mInsetPx && gapPx == mGapPx && dockRadiusPx == mDockRadiusPx
            && dockPaddingPx == mDockPaddingPx) return;
        mInsetPx = insetPx;
        mGapPx = gapPx;
        mDockRadiusPx = dockRadiusPx;
        mDockPaddingPx = dockPaddingPx;
        relayout();
        invalidate();
    }

    /** The clock's ink on the dock's glass; the theme's on-surface until the dock hands one. */
    public void setClockInk(@ColorInt int ink) {
        if (ink == mClockInk) return;
        mClockInk = ink;
        invalidate();
    }

    /**
     * The row's terminal tiles, in row order. Crossing two and three terminals slides the clock;
     * a list equal to the last one changes nothing.
     */
    public void setTiles(@NonNull List<Tile> tiles) {
        if (tiles.equals(mTiles)) return;
        int before = mTiles.size();
        mTiles = new ArrayList<>(tiles);
        float target = WallStripRowPolicy.clockShare(mTiles.size());
        // The row's first tiles land where they belong; only a change the user sees slides. A
        // count change while the clock is still sliding (2 to 1, say) retargets the slide.
        boolean slides = mBound && isAttachedToWindow() && getWidth() > 0
            && (WallStripRowPolicy.slidesClock(before, mTiles.size()) || mSlide != null);
        mBound = true;
        if (slides) {
            slideClockTo(target);
        } else {
            if (mSlide != null) mSlide.cancel();
            mSlide = null;
            mClockShare = target;
        }
        relayout();
        showCurrentTile();
        invalidate();
        mAccessibility.invalidateRoot();
        scheduleTick();
    }

    /** The tiles shown now, for tests. */
    @NonNull List<Tile> tiles() {
        return mTiles;
    }

    /** The row as laid out now, for tests. */
    @NonNull WallStripRowPolicy.Row rowNow() {
        return mRow;
    }

    /** The theme's and the terminal scheme's colours read again after a scheme change. */
    public void refreshTheme() {
        readThemeColors();
        invalidate();
    }

    // ---------------------------------------------------------------- the clock's slide

    private void slideClockTo(float target) {
        if (mSlide != null) mSlide.cancel();
        long duration = AnimationSpeed.scale(getContext(), WallStripRowPolicy.CLOCK_SLIDE_MS);
        if (duration <= 0L || ReducedMotion.isEnabled(getContext()) || mClockShare == target) {
            mSlide = null;
            mClockShare = target;
            return;
        }
        float from = mClockShare;
        mClockFullWidthPx = clockWidthAt(Math.max(from, target));
        ValueAnimator slide = ValueAnimator.ofFloat(from, target);
        slide.setDuration(duration);
        slide.setInterpolator(SLIDE_EASE);
        slide.addUpdateListener(animator -> {
            mClockShare = (float) animator.getAnimatedValue();
            relayout();
            invalidate();
        });
        slide.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mSlide != animation) return;
                mSlide = null;
                scheduleTick();
            }
        });
        mSlide = slide;
        slide.start();
    }

    private float clockWidthAt(float share) {
        return WallStripRowPolicy.layout(getWidth(), mInsetPx, mGapPx, mTiles.size(),
            mDockRadiusPx, mDockPaddingPx, share).clockWidthPx;
    }

    // ---------------------------------------------------------------- layout

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(Math.round(HEIGHT_DP * mDensity), heightMeasureSpec));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout();
        showCurrentTile();
    }

    @Override protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTextSizes();
        readThemeColors();
        updateClockText();
        relayout();
        invalidate();
    }

    /** The row, its titles fitted to their tiles and the clock's text to its room; never in a draw. */
    private void relayout() {
        mRow = WallStripRowPolicy.layout(getWidth(), mInsetPx, mGapPx, mTiles.size(),
            mDockRadiusPx, mDockPaddingPx, mClockShare);
        if (mSlide == null) mClockFullWidthPx = mRow.clockWidthPx;
        mScrollPx = Math.max(0f, Math.min(mRow.maxScrollPx, mScrollPx));
        float room = Math.max(0f, mRow.tileWidthPx - 2f * TILE_PAD_DP * mDensity);
        CharSequence[] titles = new CharSequence[mTiles.size()];
        for (int i = 0; i < titles.length; i++) {
            titles[i] = room <= 0f ? ""
                : TextUtils.ellipsize(mTiles.get(i).title, mTitlePaint, room,
                    TextUtils.TruncateAt.END);
        }
        mTitles = titles;
        fitClockText();
    }

    /** The time shrinks to the clock's full room, so the slide never squeezes the digits. */
    private void fitClockText() {
        float full = sp(TIME_SP);
        float byHeight = getHeight() > 0 ? getHeight() * TIME_MAX_HEIGHT_SHARE : full;
        mTimePaint.setTextSize(Math.min(full, byHeight));
        float room = mClockFullWidthPx;
        if (room <= 0f || mTime.isEmpty()) return;
        float width = mTimePaint.measureText(mTime);
        if (width > room) mTimePaint.setTextSize(mTimePaint.getTextSize() * room / width);
        mFittedDate = TextUtils.ellipsize(mDate, mDatePaint, room, TextUtils.TruncateAt.END);
    }

    /** Brings the window in front into view when the row scrolls. */
    private void showCurrentTile() {
        if (!mRow.scrolls()) {
            mScrollPx = 0f;
            return;
        }
        for (int i = 0; i < mTiles.size(); i++) {
            if (!mTiles.get(i).current) continue;
            mScrollPx = WallStripRowPolicy.scrollToShow(mRow, i, mScrollPx, mInsetPx, getWidth());
            return;
        }
    }

    // ---------------------------------------------------------------- the clock's minute

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        readThemeColors();
        scheduleTick();
    }

    @Override protected void onDetachedFromWindow() {
        // The slide first: its end would schedule the next tick.
        if (mSlide != null) {
            ValueAnimator slide = mSlide;
            mSlide = null;
            slide.cancel();
            mClockShare = WallStripRowPolicy.clockShare(mTiles.size());
        }
        removeCallbacks(mTick);
        removeCallbacks(mLongPress);
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        scheduleTick();
    }

    private void tick() {
        updateClockText();
        fitClockText();
        invalidate();
        scheduleTick();
    }

    /** One tick at the next minute while the clock stands in a shown row; none otherwise. */
    private void scheduleTick() {
        removeCallbacks(mTick);
        boolean clockShown = WallStripRowPolicy.showsClock(mTiles.size()) || mSlide != null;
        if (!isAttachedToWindow() || !isShown() || !clockShown) return;
        if (mTime.isEmpty()) {
            updateClockText();
            fitClockText();
        }
        long now = System.currentTimeMillis();
        postDelayed(mTick, 60_000L - now % 60_000L + 50L);
    }

    private void updateClockText() {
        Context context = getContext();
        Date now = new Date();
        mTime = android.text.format.DateFormat.getTimeFormat(context).format(now);
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale,
            DATE_SKELETON);
        mDate = new java.text.SimpleDateFormat(pattern, locale).format(now);
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0) return;
        drawClock(canvas);
        int saved = canvas.save();
        // Scrolled tiles pass under the row's inset, never over the slab's edge.
        canvas.clipRect(mRow.tilesLeftPx, 0f, getWidth() - mInsetPx, getHeight());
        canvas.translate(-mScrollPx, 0f);
        for (int i = 0; i < mRow.tileCount(); i++) {
            float left = mRow.tileLeft(i);
            if (left - mScrollPx > getWidth() || left + mRow.tileWidthPx - mScrollPx < 0f) continue;
            if (i == mRow.addTileIndex()) drawPlusTile(canvas, left);
            else drawTerminalTile(canvas, i, left);
        }
        canvas.restoreToCount(saved);
    }

    /**
     * The clock: the time over the date, start-aligned with the first icon. Sliding out, it moves
     * left by what its room lost and fades with it, clipped to the row's inset.
     */
    private void drawClock(@NonNull Canvas canvas) {
        float width = mRow.clockWidthPx;
        if (width <= 0f || mClockFullWidthPx <= 0f || mTime.isEmpty()) return;
        float shown = Math.min(1f, width / mClockFullWidthPx);
        int saved = canvas.save();
        canvas.clipRect(mInsetPx, 0f, mInsetPx + width, getHeight());
        float x = mInsetPx - (mClockFullWidthPx - width);
        Paint.FontMetrics time = mTimePaint.getFontMetrics();
        Paint.FontMetrics date = mDatePaint.getFontMetrics();
        float timeHeight = -time.ascent + time.descent;
        float dateHeight = -date.ascent + date.descent;
        float gap = 2f * mDensity;
        float top = (getHeight() - timeHeight - gap - dateHeight) / 2f;
        mTimePaint.setColor(mClockInk);
        mTimePaint.setAlpha(Math.round(Color.alpha(mClockInk) * shown));
        canvas.drawText(mTime, x, top - time.ascent, mTimePaint);
        mDatePaint.setColor(mClockInk);
        mDatePaint.setAlpha(Math.round(Color.alpha(mClockInk) * shown * DATE_INK_SHARE));
        canvas.drawText(mFittedDate, 0, mFittedDate.length(), x,
            top + timeHeight + gap - date.ascent, mDatePaint);
        canvas.restoreToCount(saved);
    }

    private void drawTerminalTile(@NonNull Canvas canvas, int index, float left) {
        Tile tile = mTiles.get(index);
        float radius = mRow.tileRadiusPx;
        float half = mRim.getStrokeWidth() / 2f;
        mRect.set(left, 0f, left + mRow.tileWidthPx, getHeight());
        mFill.setColor(SchemeTone.withAlpha(mWell, WELL_ALPHA));
        canvas.drawRoundRect(mRect, radius, radius, mFill);

        // The split shape over the title.
        float pad = TILE_PAD_DP * mDensity;
        Paint.FontMetrics title = mTitlePaint.getFontMetrics();
        float titleHeight = -title.ascent + title.descent;
        float shapeTop = pad;
        float shapeBottom = getHeight() - pad - titleHeight - TITLE_GAP_DP * mDensity;
        float shapeLeft = left + pad;
        float shapeRight = left + mRow.tileWidthPx - pad;
        if (shapeBottom > shapeTop && shapeRight > shapeLeft) {
            float w = shapeRight - shapeLeft;
            float h = shapeBottom - shapeTop;
            float gap = SHAPE_GAP_DP * mDensity / 2f;
            float paneRadius = SHAPE_RADIUS_DP * mDensity;
            mFill.setColor(ColorUtils.setAlphaComponent(mAccent, PANE_ALPHA));
            float[] shape = tile.shape;
            for (int p = 0; p + 3 < shape.length; p += 4) {
                // Neighbours share their edge; half the gap on each side keeps them apart.
                float l = shapeLeft + shape[p] * w + (shape[p] > 0f ? gap : 0f);
                float t = shapeTop + shape[p + 1] * h + (shape[p + 1] > 0f ? gap : 0f);
                float r = shapeLeft + shape[p + 2] * w - (shape[p + 2] < 1f ? gap : 0f);
                float b = shapeTop + shape[p + 3] * h - (shape[p + 3] < 1f ? gap : 0f);
                if (r <= l || b <= t) continue;
                mRect.set(l, t, r, b);
                canvas.drawRoundRect(mRect, paneRadius, paneRadius, mFill);
            }
        }
        CharSequence text = index < mTitles.length ? mTitles[index] : "";
        if (text.length() > 0) {
            mTitlePaint.setColor(mTitleInk);
            canvas.drawText(text, 0, text.length(), left + pad,
                getHeight() - pad - title.descent, mTitlePaint);
        }

        // The ring: amber for a window asking, the accent for the one in front, a faint rim else.
        mRect.set(left + half, half, left + mRow.tileWidthPx - half, getHeight() - half);
        int ring = tile.attention ? mAmber : tile.current ? mAccent
            : ColorUtils.setAlphaComponent(mAccent, REST_RIM_ALPHA);
        mRim.setColor(ring);
        canvas.drawRoundRect(mRect, Math.max(0f, radius - half), Math.max(0f, radius - half),
            mRim);
        if (tile.attention) {
            float dot = DOT_DP * mDensity;
            mFill.setColor(mAmber);
            canvas.drawCircle(left + mRow.tileWidthPx - pad, pad, dot / 2f, mFill);
        }
    }

    private void drawPlusTile(@NonNull Canvas canvas, float left) {
        float radius = mRow.tileRadiusPx;
        float half = mDashed.getStrokeWidth() / 2f;
        mRect.set(left, 0f, left + mRow.tileWidthPx, getHeight());
        mFill.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_FILL_ALPHA));
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        mRect.inset(half, half);
        mDashed.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_RIM_ALPHA));
        canvas.drawRoundRect(mRect, Math.max(0f, radius - half), Math.max(0f, radius - half),
            mDashed);
        mPlusPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_INK_ALPHA));
        float cx = left + mRow.tileWidthPx / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText("+", cx, cy - (mPlusPaint.ascent() + mPlusPaint.descent()) / 2f,
            mPlusPaint);
    }

    private void readThemeColors() {
        Context context = getContext();
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        if (mClockInk == 0) mClockInk = mOnSurface;
        // The tiles are terminals: their wells and titles come from the terminal's own scheme,
        // the well a little below its background as the Prompt line's is.
        int[] colors = TerminalColors.COLOR_SCHEME.mDefaultColors;
        PromptLineInk ink = PromptLineInk.resolve(colors[TextStyle.COLOR_INDEX_BACKGROUND],
            colors[TextStyle.COLOR_INDEX_FOREGROUND], colors[PromptLineInk.ANSI_BLUE],
            colors[PromptLineInk.ANSI_GREEN]);
        mWell = ink.well;
        mTitleInk = SchemeTone.contrastTone(colors[TextStyle.COLOR_INDEX_FOREGROUND] | 0xFF000000,
            mWell, PromptLineInk.TEXT_CONTRAST);
        mAmber = SchemeTone.contrastTone(colors[ANSI_YELLOW] | 0xFF000000, mWell, MARK_CONTRAST);
    }

    private void applyTextSizes() {
        mDatePaint.setTextSize(sp(DATE_SP));
        mTitlePaint.setTextSize(sp(TITLE_SP));
        mPlusPaint.setTextSize(sp(PLUS_SP));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    // ---------------------------------------------------------------- touch

    /**
     * A tap opens a tile, a long-press asks for its window's menu, and a horizontal drag scrolls a
     * row that is wider than the dock. A press on the clock is not taken.
     */
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownTile = tileAtView(event.getX());
                mDownX = event.getX();
                mDownY = event.getY();
                mDownScroll = mScrollPx;
                mDragging = false;
                mLongPressed = false;
                if (mDownTile < 0) return false;
                if (mDownTile != mRow.addTileIndex())
                    postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mDownX;
                if (!mDragging && Math.hypot(dx, event.getY() - mDownY) > mTouchSlop) {
                    removeCallbacks(mLongPress);
                    mDragging = mRow.scrolls() && Math.abs(dx) > Math.abs(event.getY() - mDownY);
                    if (mDragging && getParent() != null)
                        getParent().requestDisallowInterceptTouchEvent(true);
                    if (!mDragging) mDownTile = -1;
                }
                if (mDragging) {
                    float scroll = Math.max(0f, Math.min(mRow.maxScrollPx, mDownScroll - dx));
                    if (scroll != mScrollPx) {
                        mScrollPx = scroll;
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mLongPress);
                int down = mDownTile;
                mDownTile = -1;
                if (!mDragging && !mLongPressed && down >= 0 && tileAtView(event.getX()) == down) {
                    performClick();
                    report(down);
                }
                mDragging = false;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPress);
                mDownTile = -1;
                mDragging = false;
                return true;
            default:
                return mDownTile >= 0;
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    /** The tile under {@code x} in this view's pixels, or -1. */
    private int tileAtView(float x) {
        if (x < mRow.tilesLeftPx || x > getWidth() - mInsetPx) return -1;
        return mRow.tileAt(x, mScrollPx);
    }

    private void longPress() {
        int tile = mDownTile;
        if (tile < 0 || tile >= mTiles.size() || mListener == null) return;
        mLongPressed = true;
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        mListener.onTileLongPressed(mTiles.get(tile).chipIndex, tileBounds(tile));
    }

    @NonNull
    private Rect tileBounds(int index) {
        float left = mRow.tileLeft(index) - mScrollPx;
        return new Rect(Math.round(left), 0, Math.round(left + mRow.tileWidthPx), getHeight());
    }

    private void report(int index) {
        if (mListener == null || index < 0) return;
        if (index == mRow.addTileIndex()) mListener.onNewTerminalTapped();
        else if (index < mTiles.size()) mListener.onTileTapped(mTiles.get(index).chipIndex);
    }

    @Override protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** Each tile is a node a screen reader can step to, tap and long-press. */
    private final class RowAccessibility extends ExploreByTouchHelper {

        RowAccessibility(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            int tile = tileAtView(x);
            return tile >= 0 ? tile : INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < mRow.tileCount(); i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            if (id < mTiles.size()) {
                Tile tile = mTiles.get(id);
                String spoken = tile.attention
                    ? getContext().getString(R.string.dock_run_strip_attention, tile.spokenLabel)
                    : tile.spokenLabel;
                node.setContentDescription(spoken);
                node.setSelected(tile.current);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
                node.setLongClickable(true);
            } else {
                node.setContentDescription(mNewTerminalSpoken);
            }
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
            Rect bounds = id < mRow.tileCount() ? tileBounds(id) : new Rect();
            bounds.intersect(0, 0, getWidth(), getHeight());
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                report(id);
                return true;
            }
            if (action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK && id < mTiles.size()
                && mListener != null) {
                mListener.onTileLongPressed(mTiles.get(id).chipIndex, tileBounds(id));
                return true;
            }
            return false;
        }
    }
}
