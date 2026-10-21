package com.termux.app.dock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
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
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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

    /**
     * One terminal tile: a row window or a Home float (a terminal window of the same session),
     * its number, glyph and label, and its split shape.
     */
    public static final class Tile {
        /**
         * Who the tile is across bindings (its window, whatever its place in the row), so a tile
         * that stays slides to its new place and one that is new grows in. The chip's index where
         * the host names no window.
         */
        public final long key;
        /** The window's chip, as the window bar counts chips. */
        public final int chipIndex;
        /** The window's number, as the window chip's badge says it; always drawn. */
        @NonNull public final String number;
        /** The window's label, plain text ({@link WallStripModel.Entry#shortLabel}). */
        @NonNull public final String label;
        /** The process glyph, a Nerd Font symbol drawn only with the bundled face; or empty. */
        @NonNull public final String glyph;
        @NonNull public final String spokenLabel;
        public final boolean current;
        public final boolean attention;
        /** Whether this is a Home float: it wears the float mark after its number. */
        public final boolean floating;
        /** The window's panes as fractions of it, four floats a pane. */
        @NonNull final float[] shape;

        public Tile(int chipIndex, @NonNull String number, @NonNull String label,
                    @NonNull String spokenLabel, boolean current, boolean attention,
                    @NonNull float[] shape) {
            this(chipIndex, chipIndex, number, label, "", spokenLabel, current, attention, false,
                shape);
        }

        public Tile(long key, int chipIndex, @NonNull String number, @NonNull String label,
                    @NonNull String glyph, @NonNull String spokenLabel, boolean current,
                    boolean attention, boolean floating, @NonNull float[] shape) {
            this.key = key;
            this.chipIndex = chipIndex;
            this.number = number;
            this.label = label;
            this.glyph = glyph;
            this.spokenLabel = spokenLabel;
            this.current = current;
            this.attention = attention;
            this.floating = floating;
            this.shape = shape.clone();
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof Tile)) return false;
            Tile that = (Tile) o;
            return key == that.key && chipIndex == that.chipIndex && current == that.current
                && attention == that.attention && floating == that.floating
                && number.equals(that.number) && label.equals(that.label)
                && glyph.equals(that.glyph) && spokenLabel.equals(that.spokenLabel)
                && Arrays.equals(shape, that.shape);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * chipIndex + number.hashCode()) + label.hashCode())
                + Arrays.hashCode(shape) + (current ? 1 : 0) + (attention ? 2 : 0)
                + (floating ? 4 : 0) + Long.hashCode(key);
        }
    }

    /** The {@code +} tile's key: no window's. */
    static final long ADD_TILE_KEY = Long.MIN_VALUE;

    /**
     * The tiles for {@code entries}: the row's own ({@link WallStripModel#rowEntries}) or the row's
     * and the Home floats ({@link WallStripModel#wallEntries}), each with its number, glyph and
     * distinguishing label, a float marked as one. {@code shapes} runs alongside them
     * ({@link WallStripRowPolicy#splitShape}); a missing or null shape reads as one whole pane.
     */
    @NonNull
    public static List<Tile> tilesOf(@NonNull List<WallStripModel.Entry> entries,
                                     @Nullable List<float[]> shapes) {
        return tilesOf(entries, shapes, null);
    }

    /**
     * As {@link #tilesOf(List, List)}, each tile keyed by {@code keys} alongside the entries (its
     * window's, so the row can tell a tile that moved from one that is new); a missing or null
     * key is the chip's index.
     */
    @NonNull
    public static List<Tile> tilesOf(@NonNull List<WallStripModel.Entry> entries,
                                     @Nullable List<float[]> shapes, @Nullable List<Long> keys) {
        List<Tile> tiles = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            WallStripModel.Entry entry = entries.get(i);
            if (entry.kind == WallStripModel.Kind.NEW_WINDOW) continue;
            float[] shape = shapes != null && i < shapes.size() ? shapes.get(i) : null;
            Long key = keys != null && i < keys.size() ? keys.get(i) : null;
            tiles.add(new Tile(key != null ? key : entry.chipIndex, entry.chipIndex, entry.number,
                entry.shortLabel, entry.glyph, entry.spokenLabel, entry.current, entry.attention,
                entry.isFloat(), shape == null ? WallStripRowPolicy.splitShape(null) : shape));
        }
        return tiles;
    }

    /** The row's shipped height; a layout may keep another ({@link WallStripRowPolicy}). */
    public static final float HEIGHT_DP = WallStripRowPolicy.DEFAULT_HEIGHT_DP;
    static final float DEFAULT_INSET_DP = 14f;
    static final float DEFAULT_GAP_DP = 10f;
    static final float TILE_PAD_DP = 7f;
    static final float SHAPE_GAP_DP = 2f;
    static final float SHAPE_RADIUS_DP = 3f;
    static final float TITLE_GAP_DP = 5f;
    /** Room after the number, the float mark and the glyph on the title line. */
    static final float TITLE_INNER_GAP_DP = 5f;
    /** A Home float's mark after its number: two small stacked windows, this square. */
    static final float FLOAT_MARK_DP = 9f;
    /** Below this the split shape says nothing, and the tile leaves it out for its title. */
    static final float MIN_SHAPE_DP = 8f;
    /** The least of a label worth its line beside the number, in characters. */
    static final String MIN_LABEL_SAMPLE = "abc…";
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
    /** The process glyphs, in the bundled Nerd Font symbols face; null when it is missing. */
    @Nullable private final TextPaint mGlyphPaint;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    /** Each tile's title fitted to it, from the last relayout. */
    @NonNull private Fitted[] mTitles = new Fitted[0];

    @NonNull private String mTime = "";
    @NonNull private String mDate = "";
    @NonNull private CharSequence mFittedDate = "";
    private final Runnable mTick = this::tick;

    @ColorInt private int mAccent;
    @ColorInt private int mClockInk;
    /** The tiles' well and how opaque it lies: a light scheme's is a dark tint over the glass. */
    @ColorInt private int mWell;
    private float mWellAlpha = WELL_ALPHA;
    @ColorInt private int mTitleInk;
    @ColorInt private int mAmber;
    @ColorInt private int mOnSurface;

    /**
     * A change in which windows the row holds: each tile that stays slides and resizes from where
     * it was drawn to its new place, a new one grows in from its own centre and a closed one
     * shrinks away where it stood, over {@link WallStripRowPolicy#CLOCK_SLIDE_MS}. Null at rest.
     */
    @Nullable private ValueAnimator mMorph;
    /** Where the morph stands, 0..1, eased. */
    private float mMorphT = 1f;
    /** Each staying tile's drawn place when the morph began: left (in view), width, opacity. */
    @NonNull private final HashMap<Long, float[]> mMorphFrom = new HashMap<>();
    /** The tiles that left, drawn shrinking away during the morph. */
    @NonNull private final List<Leaving> mLeaving = new ArrayList<>();
    private final float[] mDrawn = new float[3];

    /**
     * The tiles drawn into a display list of their own (Android 10 on), re-recorded only when
     * they change: the clock's minute redraws the clock and nothing else. Typed as Object so the
     * class still loads where RenderNode is not.
     */
    @Nullable private Object mTilesNode;
    private boolean mTilesDirty = true;

    /** A tile's title fitted to it: its label cut to its room, and how title and shape share it. */
    private static final class Fitted {
        static final Fitted NONE = new Fitted("",
            WallStripRowPolicy.tileText(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));

        @NonNull final CharSequence label;
        @NonNull final WallStripRowPolicy.TileText text;

        Fitted(@NonNull CharSequence label, @NonNull WallStripRowPolicy.TileText text) {
            this.label = label;
            this.text = text;
        }
    }

    /** A tile that has just left the row, drawn where it stood while it shrinks away. */
    private static final class Leaving {
        @NonNull final Tile tile;
        @NonNull final Fitted title;
        final float left;
        final float width;
        final float alpha;

        Leaving(@NonNull Tile tile, @NonNull Fitted title, float left, float width,
                float alpha) {
            this.tile = tile;
            this.title = title;
            this.left = left;
            this.width = width;
            this.alpha = alpha;
        }
    }

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
        // The chips' process glyphs are Nerd Font symbols no system face has; only the bundled
        // symbols face draws them, and without it the tiles leave them out.
        Typeface symbols = NerdFontSpans.typeface(context);
        if (symbols != null) {
            mGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mGlyphPaint.setTypeface(symbols);
        } else {
            mGlyphPaint = null;
        }
        mMarkStroke.setStyle(Paint.Style.STROKE);
        mMarkStroke.setStrokeWidth(Math.max(1f, mDensity));
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
        invalidateTiles();
    }

    /**
     * The clock's ink on the dock's glass; the theme's on-surface until the dock hands one. Only
     * the clock redraws.
     */
    public void setClockInk(@ColorInt int ink) {
        mClockInkHanded = true;
        if (ink == mClockInk) return;
        mClockInk = ink;
        invalidate();
    }

    /** Whether the dock has handed the clock its ink; until then it follows the theme. */
    private boolean mClockInkHanded;

    /**
     * The row's terminal tiles, in row order. Crossing two and three terminals slides the clock;
     * a window opening or closing slides the tiles to their new places; a list equal to the last
     * one changes nothing.
     */
    public void setTiles(@NonNull List<Tile> tiles) {
        if (tiles.equals(mTiles)) return;
        int before = mTiles.size();
        // Which windows the row holds changed: the tiles move from where they are drawn now.
        boolean keysChanged = !sameKeys(mTiles, tiles);
        boolean morphs = mBound && isAttachedToWindow() && isShown() && getWidth() > 0 && keysChanged
            && morphDurationMs() > 0L;
        HashMap<Long, float[]> from = null;
        List<Leaving> leaving = null;
        if (morphs) {
            from = new HashMap<>();
            leaving = new ArrayList<>();
            java.util.HashSet<Long> staying = new java.util.HashSet<>();
            for (Tile tile : tiles) staying.add(tile.key);
            for (int i = 0; i < mRow.tileCount(); i++) {
                drawnRect(i, mDrawn);
                long key = keyAt(i);
                if (key == ADD_TILE_KEY || staying.contains(key)) {
                    from.put(key, mDrawn.clone());
                } else if (i < mTiles.size()) {
                    leaving.add(new Leaving(mTiles.get(i),
                        i < mTitles.length ? mTitles[i] : Fitted.NONE,
                        mDrawn[0], mDrawn[1], mDrawn[2]));
                }
            }
            // A tile still shrinking away from the last change keeps going from where it is.
            for (Leaving gone : mLeaving) {
                leavingRect(gone, mDrawn);
                if (mDrawn[2] > 0f && !staying.contains(gone.tile.key))
                    leaving.add(new Leaving(gone.tile, gone.title, mDrawn[0], mDrawn[1], mDrawn[2]));
            }
        }
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
        if (morphs) startMorph(from, leaving);
        else if (keysChanged) cancelMorph();
        invalidateTiles();
        mAccessibility.invalidateRoot();
        scheduleTick();
    }

    private static boolean sameKeys(@NonNull List<Tile> a, @NonNull List<Tile> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i).key != b.get(i).key) return false;
        return true;
    }

    private long keyAt(int index) {
        return index < mTiles.size() ? mTiles.get(index).key : ADD_TILE_KEY;
    }

    private long morphDurationMs() {
        if (ReducedMotion.isEnabled(getContext())) return 0L;
        return AnimationSpeed.scale(getContext(), WallStripRowPolicy.CLOCK_SLIDE_MS);
    }

    // ---------------------------------------------------------------- the tiles' morph

    private void startMorph(@NonNull HashMap<Long, float[]> from, @NonNull List<Leaving> leaving) {
        cancelMorph();
        mMorphFrom.putAll(from);
        mLeaving.addAll(leaving);
        mMorphT = 0f;
        ValueAnimator morph = ValueAnimator.ofFloat(0f, 1f);
        morph.setDuration(morphDurationMs());
        morph.setInterpolator(SLIDE_EASE);
        morph.addUpdateListener(animator -> {
            if (mMorph != animator) return;
            mMorphT = (float) animator.getAnimatedValue();
            invalidateTiles();
        });
        morph.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mMorph != animation) return;
                mMorph = null;
                mMorphT = 1f;
                mMorphFrom.clear();
                mLeaving.clear();
                invalidateTiles();
            }
        });
        mMorph = morph;
        morph.start();
    }

    /** Lands the tiles where they belong, now. */
    private void cancelMorph() {
        ValueAnimator morph = mMorph;
        mMorph = null;
        mMorphT = 1f;
        mMorphFrom.clear();
        mLeaving.clear();
        if (morph != null) morph.cancel();
    }

    /**
     * Where tile {@code index} (the {@code +} tile last) is drawn now, in this view's pixels with
     * the row's scroll taken off: {@code out} = left, width, opacity.
     */
    private void drawnRect(int index, @NonNull float[] out) {
        float left = mRow.tileLeft(index) - mScrollPx;
        float width = mRow.tileWidthPx;
        out[0] = left;
        out[1] = width;
        out[2] = 1f;
        if (mMorph == null) return;
        float t = mMorphT;
        float[] from = mMorphFrom.get(keyAt(index));
        if (from != null) {
            out[0] = from[0] + (left - from[0]) * t;
            out[1] = from[1] + (width - from[1]) * t;
            out[2] = from[2] + (1f - from[2]) * t;
        } else {
            // New: it grows in from its own centre while its neighbours make room.
            out[1] = width * t;
            out[0] = left + (width - out[1]) / 2f;
            out[2] = t;
        }
    }

    /** Where a tile that left is drawn now: shrinking to its centre as it fades. */
    private void leavingRect(@NonNull Leaving gone, @NonNull float[] out) {
        float keep = mMorph == null ? 0f : 1f - mMorphT;
        out[1] = gone.width * keep;
        out[0] = gone.left + (gone.width - out[1]) / 2f;
        out[2] = gone.alpha * keep;
    }

    /** The tiles changed: their display list is recorded again on the next frame. */
    private void invalidateTiles() {
        mTilesDirty = true;
        invalidate();
    }

    /** The tiles shown now, for tests. */
    @NonNull List<Tile> tiles() {
        return mTiles;
    }

    /** The row as laid out now, for tests. */
    @NonNull WallStripRowPolicy.Row rowNow() {
        return mRow;
    }

    /**
     * The theme's and the terminal scheme's colours read again after a scheme change; the row
     * redraws only when one moved, so a caller may ask on every scheme pass.
     */
    public void refreshTheme() {
        if (readThemeColors()) invalidateTiles();
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
            invalidateTiles();
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
        invalidateTiles();
    }

    /** The row, its titles fitted to their tiles and the clock's text to its room; never in a draw. */
    private void relayout() {
        mRow = WallStripRowPolicy.layout(getWidth(), mInsetPx, mGapPx, mTiles.size(),
            mDockRadiusPx, mDockPaddingPx, mClockShare);
        if (mSlide == null) mClockFullWidthPx = mRow.clockWidthPx;
        mScrollPx = Math.max(0f, Math.min(mRow.maxScrollPx, mScrollPx));
        Fitted[] titles = new Fitted[mTiles.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = fit(mTiles.get(i));
        mTitles = titles;
        fitClockText();
    }

    /** Tile {@code tile}'s title fitted to the row's tile size ({@link WallStripRowPolicy#tileText}). */
    @NonNull
    private Fitted fit(@NonNull Tile tile) {
        float inner = TITLE_INNER_GAP_DP * mDensity;
        float head = mTitlePaint.measureText(tile.number) + inner
            + (tile.floating ? FLOAT_MARK_DP * mDensity + inner : 0f);
        float glyph = mGlyphPaint == null || tile.glyph.isEmpty() ? 0f
            : mGlyphPaint.measureText(tile.glyph) + inner;
        float label = tile.label.isEmpty() ? 0f : mTitlePaint.measureText(tile.label);
        WallStripRowPolicy.TileText text = WallStripRowPolicy.tileText(mRow.tileWidthPx,
            getHeight(), TILE_PAD_DP * mDensity, lineHeight(), TITLE_GAP_DP * mDensity, head,
            glyph, label, mTitlePaint.measureText(MIN_LABEL_SAMPLE), MIN_SHAPE_DP * mDensity);
        CharSequence fitted = text.labelRoomPx <= 0f || tile.label.isEmpty() ? ""
            : TextUtils.ellipsize(tile.label, mTitlePaint, text.labelRoomPx,
                TextUtils.TruncateAt.END);
        return new Fitted(fitted, text);
    }

    /** One title line's height. */
    private float lineHeight() {
        Paint.FontMetrics metrics = mTitlePaint.getFontMetrics();
        return -metrics.ascent + metrics.descent;
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
        cancelMorph();
        mTilesDirty = true;
        removeCallbacks(mTick);
        removeCallbacks(mLongPress);
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        scheduleTick();
    }

    /** The minute turned: the clock's text, and only the clock, is drawn again. */
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
        if (getWidth() <= 0 || getHeight() <= 0) return;
        drawClock(canvas);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canvas.isHardwareAccelerated()) {
            drawTilesRecorded(canvas);
        } else {
            drawTiles(canvas);
        }
    }

    /**
     * The tiles from their own display list, recorded again only when they changed, so the
     * clock's minute costs the clock alone.
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawTilesRecorded(@NonNull Canvas canvas) {
        RenderNode node = mTilesNode instanceof RenderNode ? (RenderNode) mTilesNode : null;
        if (node == null) {
            node = new RenderNode("WallStripTiles");
            mTilesNode = node;
            mTilesDirty = true;
        }
        if (node.getWidth() != getWidth() || node.getHeight() != getHeight()) {
            node.setPosition(0, 0, getWidth(), getHeight());
            mTilesDirty = true;
        }
        if (mTilesDirty || !node.hasDisplayList()) {
            RecordingCanvas recording = node.beginRecording(getWidth(), getHeight());
            try {
                drawTiles(recording);
            } finally {
                node.endRecording();
            }
            mTilesDirty = false;
        }
        canvas.drawRenderNode(node);
    }

    /** Every tile where it is drawn now, the ones leaving included. */
    private void drawTiles(@NonNull Canvas canvas) {
        int saved = canvas.save();
        // Scrolled tiles pass under the row's inset, never over the slab's edge.
        canvas.clipRect(mRow.tilesLeftPx, 0f, getWidth() - mInsetPx, getHeight());
        for (Leaving gone : mLeaving) {
            leavingRect(gone, mDrawn);
            drawTile(canvas, gone.tile, gone.title, mDrawn[0], mDrawn[1], mDrawn[2]);
        }
        for (int i = 0; i < mRow.tileCount(); i++) {
            drawnRect(i, mDrawn);
            if (i == mRow.addTileIndex()) {
                drawTile(canvas, null, Fitted.NONE, mDrawn[0], mDrawn[1], mDrawn[2]);
            } else {
                drawTile(canvas, mTiles.get(i), i < mTitles.length ? mTitles[i] : Fitted.NONE,
                    mDrawn[0], mDrawn[1], mDrawn[2]);
            }
        }
        canvas.restoreToCount(saved);
    }

    /**
     * One tile at {@code left}, {@code width} wide, at {@code alpha}: a terminal's, or the
     * {@code +} tile for a null {@code tile}. Its text stays inside it while it resizes.
     */
    private void drawTile(@NonNull Canvas canvas, @Nullable Tile tile, @NonNull Fitted title,
                          float left, float width, float alpha) {
        if (width <= 0.5f || alpha <= 0f || left > getWidth() || left + width < 0f) return;
        int saved = alpha < 1f
            ? canvas.saveLayerAlpha(left, 0f, left + width, getHeight(), Math.round(alpha * 255f))
            : canvas.save();
        canvas.clipRect(left, 0f, left + width, getHeight());
        if (tile == null) drawPlusTile(canvas, left, width);
        else drawTerminalTile(canvas, tile, title, left, width);
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

    private void drawTerminalTile(@NonNull Canvas canvas, @NonNull Tile tile,
                                  @NonNull Fitted fitted, float left, float tileWidth) {
        float radius = Math.min(mRow.tileRadiusPx, tileWidth / 2f);
        float half = mRim.getStrokeWidth() / 2f;
        mRect.set(left, 0f, left + tileWidth, getHeight());
        mFill.setColor(SchemeTone.withAlpha(mWell, mWellAlpha));
        canvas.drawRoundRect(mRect, radius, radius, mFill);

        // The split shape over the title, as tall as the title leaves it.
        float pad = TILE_PAD_DP * mDensity;
        float shapeTop = pad;
        float shapeBottom = pad + fitted.text.shapeHeightPx;
        float shapeLeft = left + pad;
        float shapeRight = left + tileWidth - pad;
        if (fitted.text.shapeHeightPx > 0f && shapeRight > shapeLeft) {
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
        drawTitle(canvas, tile, fitted, left + pad);

        // The ring: amber for a window asking, the accent for the one in front, a faint rim else.
        mRect.set(left + half, half, left + tileWidth - half, getHeight() - half);
        int ring = tile.attention ? mAmber : tile.current ? mAccent
            : ColorUtils.setAlphaComponent(mAccent, REST_RIM_ALPHA);
        mRim.setColor(ring);
        canvas.drawRoundRect(mRect, Math.max(0f, radius - half), Math.max(0f, radius - half),
            mRim);
        if (tile.attention) {
            float dot = DOT_DP * mDensity;
            mFill.setColor(mAmber);
            canvas.drawCircle(left + tileWidth - pad, pad, dot / 2f, mFill);
        }
    }

    /**
     * The title from {@code x}: the number, a float's mark, the glyph, then the label on the same
     * line or on a line of its own below.
     */
    private void drawTitle(@NonNull Canvas canvas, @NonNull Tile tile, @NonNull Fitted fitted,
                           float x) {
        Paint.FontMetrics metrics = mTitlePaint.getFontMetrics();
        float inner = TITLE_INNER_GAP_DP * mDensity;
        float lastBaseline = getHeight() - TILE_PAD_DP * mDensity - metrics.descent;
        float headBaseline = fitted.text.twoLines ? lastBaseline - lineHeight() : lastBaseline;
        mTitlePaint.setColor(mTitleInk);
        canvas.drawText(tile.number, x, headBaseline, mTitlePaint);
        float at = x + mTitlePaint.measureText(tile.number) + inner;
        if (tile.floating) {
            // Centred on the digits' height.
            float digitsMiddle = headBaseline + (metrics.ascent * 0.72f) / 2f;
            drawFloatMark(canvas, at, digitsMiddle, FLOAT_MARK_DP * mDensity);
            at += FLOAT_MARK_DP * mDensity + inner;
        }
        if (fitted.text.showsGlyph && mGlyphPaint != null && !tile.glyph.isEmpty()) {
            mGlyphPaint.setColor(mTitleInk);
            canvas.drawText(tile.glyph, at, headBaseline, mGlyphPaint);
            at += mGlyphPaint.measureText(tile.glyph) + inner;
        }
        CharSequence label = fitted.label;
        if (label.length() == 0) return;
        float labelX = fitted.text.twoLines ? x : at;
        canvas.drawText(label, 0, label.length(), labelX, lastBaseline, mTitlePaint);
    }

    /**
     * A Home float's mark, {@code size} square from {@code left}, centred on {@code centerY}: a
     * window outlined behind and one filled in front of it, a window floating over another.
     */
    private void drawFloatMark(@NonNull Canvas canvas, float left, float centerY, float size) {
        float box = size * 0.64f;
        float corner = size * 0.14f;
        float top = centerY - size / 2f;
        float inset = mMarkStroke.getStrokeWidth() / 2f;
        mMarkStroke.setColor(mTitleInk);
        mRect.set(left + size - box + inset, top + inset, left + size - inset, top + box - inset);
        canvas.drawRoundRect(mRect, corner, corner, mMarkStroke);
        mFill.setColor(mTitleInk);
        mRect.set(left, top + size - box, left + box, top + size);
        canvas.drawRoundRect(mRect, corner, corner, mFill);
    }

    private void drawPlusTile(@NonNull Canvas canvas, float left, float tileWidth) {
        float radius = Math.min(mRow.tileRadiusPx, tileWidth / 2f);
        float half = mDashed.getStrokeWidth() / 2f;
        mRect.set(left, 0f, left + tileWidth, getHeight());
        mFill.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_FILL_ALPHA));
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        mRect.inset(half, half);
        mDashed.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_RIM_ALPHA));
        canvas.drawRoundRect(mRect, Math.max(0f, radius - half), Math.max(0f, radius - half),
            mDashed);
        mPlusPaint.setColor(ColorUtils.setAlphaComponent(mOnSurface, PLUS_INK_ALPHA));
        float cx = left + tileWidth / 2f;
        float cy = getHeight() / 2f;
        canvas.drawText("+", cx, cy - (mPlusPaint.ascent() + mPlusPaint.descent()) / 2f,
            mPlusPaint);
    }

    /** Reads the colours again; whether any of them moved. */
    private boolean readThemeColors() {
        int accent = mAccent;
        int onSurface = mOnSurface;
        int well = mWell;
        float wellAlpha = mWellAlpha;
        int titleInk = mTitleInk;
        int amber = mAmber;
        int clockInk = mClockInk;
        Context context = getContext();
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        // The theme's ink until the dock hands the glass's.
        if (!mClockInkHanded) mClockInk = mOnSurface;
        // The tiles are terminals: their wells and titles come from the terminal's own scheme,
        // the well a darker recess as the Prompt line's is: on a dark scheme a little below its
        // background, on a light one a dark tint over the glass. Inks are measured where they
        // stand, against the well as the Prompt line reads it.
        int[] colors = TerminalColors.COLOR_SCHEME.mDefaultColors;
        PromptLineInk ink = PromptLineInk.resolve(colors[TextStyle.COLOR_INDEX_BACKGROUND],
            colors[TextStyle.COLOR_INDEX_FOREGROUND], colors[PromptLineInk.ANSI_BLUE],
            colors[PromptLineInk.ANSI_GREEN]);
        mWell = ink.well;
        mWellAlpha = ink.tinted ? ink.wellAlpha : WELL_ALPHA;
        mTitleInk = SchemeTone.contrastTone(colors[TextStyle.COLOR_INDEX_FOREGROUND] | 0xFF000000,
            ink.inkSurface, PromptLineInk.TEXT_CONTRAST);
        mAmber = SchemeTone.contrastTone(colors[ANSI_YELLOW] | 0xFF000000, ink.inkSurface,
            MARK_CONTRAST);
        return accent != mAccent || onSurface != mOnSurface || well != mWell
            || wellAlpha != mWellAlpha || titleInk != mTitleInk || amber != mAmber
            || clockInk != mClockInk;
    }

    private void applyTextSizes() {
        mDatePaint.setTextSize(sp(DATE_SP));
        mTitlePaint.setTextSize(sp(TITLE_SP));
        if (mGlyphPaint != null) mGlyphPaint.setTextSize(sp(TITLE_SP));
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
                // A row that scrolls keeps its drag from the start: no parent takes it for the
                // dock's page swipe or the drawer's pull. Handed back once it reads as vertical.
                if (mRow.scrolls() && getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                if (mDownTile != mRow.addTileIndex())
                    postDelayed(mLongPress, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mDownX;
                if (!mDragging && Math.hypot(dx, event.getY() - mDownY) > mTouchSlop) {
                    removeCallbacks(mLongPress);
                    mDragging = mRow.scrolls() && Math.abs(dx) > Math.abs(event.getY() - mDownY);
                    if (getParent() != null)
                        getParent().requestDisallowInterceptTouchEvent(mDragging);
                    if (!mDragging) mDownTile = -1;
                }
                if (mDragging) {
                    float scroll = Math.max(0f, Math.min(mRow.maxScrollPx, mDownScroll - dx));
                    if (scroll != mScrollPx) {
                        mScrollPx = scroll;
                        invalidateTiles();
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
