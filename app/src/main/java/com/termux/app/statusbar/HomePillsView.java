package com.termux.app.statusbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.ArrayList;
import java.util.List;

/**
 * The status bar's look on a Home page ({@link HomePillsPolicy}): the windows pill at the start
 * and the stats pill at the end. A child of the bar ({@link StatusBarSwipeLayout}), which draws
 * nothing of its own while this shows. Each pill stands on glass of its own, built by the activity
 * from the status bar's surface values ({@link #setGlassGround}), so the pills are glass wherever
 * the bar stands and whatever its own layers are doing on the Workspace.
 *
 * <p>Dumb: the activity hands it the floats, the focused one and the readings; the policy places
 * them; this draws and reports taps. Nothing here repaints at rest: a focus change moves the
 * sections once, over {@link HomePillsPolicy#FOCUS_MOVE_MS}.
 */
public final class HomePillsView extends View {

    /** A section of the windows pill was tapped. */
    public interface Listener {
        void onSectionTapped(int index);
    }

    /** Builds the glass one pill stands on, for a pill of {@code cornerRadiusPx}. */
    public interface GlassGround {
        @Nullable Drawable pill(float cornerRadiusPx);
    }

    private static final float GLYPH_SP = 15f;
    private static final float NAME_SP = 13f;
    private static final float STATS_GLYPH_SP = 13f;
    private static final float STATS_VALUE_SP = 12f;
    /** The divider between two sections, out of 255 of the ink. */
    private static final int DIVIDER_ALPHA = 60;

    private final float mDensity;
    private final TextPaint mGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mStatsGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mStatsValuePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final PillsAccessibility mAccessibility;

    @Nullable private Listener mListener;
    @NonNull private List<HomePillsPolicy.Section> mSections = new ArrayList<>();
    private int mFocused = -1;
    /** The stats pill's readings: glyph and value each, in order. */
    @NonNull private String[] mStatsGlyphs = new String[0];
    @NonNull private String[] mStatsValues = new String[0];
    @NonNull private String mStatsSpoken = "";
    private int mInsetStart;
    private int mInsetEnd;
    private boolean mAnimate = true;

    @NonNull private HomePillsPolicy.Layout mTarget = HomePillsPolicy.EMPTY;
    @NonNull private HomePillsPolicy.Layout mFrom = HomePillsPolicy.EMPTY;
    @NonNull private HomePillsPolicy.Layout mNow = HomePillsPolicy.EMPTY;
    @Nullable private ValueAnimator mMove;
    @Nullable private CharSequence mFocusedName;
    private float mFocusedNameRoom = -1f;

    /** The theme's neutrals and panel; re-read on a configuration change. */
    @ColorInt private int mOnSurface;
    @ColorInt private int mSurfaceBase;
    @ColorInt private int mPanel;
    private boolean mGlass;
    @Nullable private GlassGround mGround;
    /** Each pill's own glass, built at {@link #mGroundRadius}; drawn inside {@link #pillsPath}. */
    @Nullable private Drawable mWindowsGlass;
    @Nullable private Drawable mStatsGlass;
    private float mGroundRadius = -1f;
    private final Path mGlassClip = new Path();
    @Nullable private ChromeInk.Polarity mPolarity;
    private boolean mInkResolved;

    private int mDownSection = -1;
    /** Widths measured on the last relayout, so a frame measures nothing. */
    @NonNull private float[] mGlyphWidths = new float[0];
    @NonNull private float[] mStatsGlyphWidths = new float[0];
    @NonNull private float[] mStatsValueWidths = new float[0];

    public HomePillsView(@NonNull Context context) {
        this(context, null);
    }

    public HomePillsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = getResources().getDisplayMetrics().density;
        Typeface nerd = NerdFontSpans.typeface(context);
        mGlyphPaint.setTypeface(nerd);
        mStatsGlyphPaint.setTypeface(nerd);
        mNamePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mStatsValuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mStatsValuePaint.setFontFeatureSettings("tnum");
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(Math.max(1, Math.round(mDensity)));
        mDividerPaint.setStrokeWidth(Math.max(1, Math.round(mDensity)));
        applyTextSizes();
        readThemeColors();
        mAccessibility = new PillsAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
    }

    public void setListener(@Nullable Listener listener) { mListener = listener; }

    /** The air the pills keep from the bar's two ends: the bar's own content inset. */
    public void setHorizontalInsets(int startPx, int endPx) {
        if (startPx == mInsetStart && endPx == mInsetEnd) return;
        mInsetStart = startPx;
        mInsetEnd = endPx;
        relayout(false);
    }

    /** Whether a focus change moves the sections (off with reduced motion). */
    public void setAnimationsEnabled(boolean animate) { mAnimate = animate; }

    /**
     * The Home floats, in order, and the focused one (-1 for none). A change of focus alone moves
     * the sections once; anything else lands at once.
     */
    public void setWindows(@NonNull List<HomePillsPolicy.Section> sections, int focused) {
        if (sections.equals(mSections) && focused == mFocused) return;
        boolean focusOnly = sections.size() == mSections.size() && focused != mFocused;
        mSections = new ArrayList<>(sections);
        mFocused = focused;
        mFocusedNameRoom = -1f;
        relayout(focusOnly && mAnimate && isLaidOut());
        mAccessibility.invalidateRoot();
    }

    /**
     * The readings, any of which may be null for one the settings hide: CPU and RAM as their
     * values, and the weather as its glyph and temperature. {@code spoken} says them all.
     */
    public void setStats(@Nullable String cpu, @Nullable String ram, @Nullable String weatherGlyph,
                         @Nullable String weather, @NonNull String spoken) {
        List<String> glyphs = new ArrayList<>(3);
        List<String> values = new ArrayList<>(3);
        if (cpu != null) { glyphs.add(CPU_GLYPH); values.add(cpu); }
        if (ram != null) { glyphs.add(RAM_GLYPH); values.add(ram); }
        if (weather != null) {
            glyphs.add(weatherGlyph == null ? "" : weatherGlyph);
            values.add(weather);
        }
        String[] g = glyphs.toArray(new String[0]);
        String[] v = values.toArray(new String[0]);
        if (java.util.Arrays.equals(g, mStatsGlyphs) && java.util.Arrays.equals(v, mStatsValues)
            && spoken.equals(mStatsSpoken)) return;
        mStatsGlyphs = g;
        mStatsValues = v;
        mStatsSpoken = spoken;
        relayout(false);
        mAccessibility.invalidateRoot();
    }

    /** The glyphs the bar's CPU and RAM widgets wear (Nerd Font). */
    public static final String CPU_GLYPH = "";
    public static final String RAM_GLYPH = "";

    /** The layout on screen now, for tests. */
    @NonNull HomePillsPolicy.Layout layoutNow() { return mNow; }

    /**
     * The glass the pills stand on, or null for none (the pills then read their ink against the
     * plain panel). Built once per pill now and again only when the pills' corner radius changes;
     * the caller hands a new ground when a glass value or the wallpaper frame moves.
     */
    public void setGlassGround(@Nullable GlassGround ground) {
        mGround = ground;
        mGlass = ground != null;
        mInkResolved = false;
        buildGlass(mTarget.radius);
        invalidate();
    }

    /** Whether the pills stand on glass, for tests. */
    boolean hasGlass() { return mWindowsGlass != null || mStatsGlass != null; }

    private void buildGlass(float radius) {
        if (mWindowsGlass != null) mWindowsGlass.setCallback(null);
        if (mStatsGlass != null) mStatsGlass.setCallback(null);
        mWindowsGlass = null;
        mStatsGlass = null;
        mGroundRadius = radius;
        if (mGround == null || radius <= 0f) return;
        mWindowsGlass = mGround.pill(radius);
        mStatsGlass = mGround.pill(radius);
        // A crossfading frame redraws itself through the view it is on.
        if (mWindowsGlass != null) mWindowsGlass.setCallback(this);
        if (mStatsGlass != null) mStatsGlass.setCallback(this);
    }

    @Override protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mWindowsGlass || who == mStatsGlass || super.verifyDrawable(who);
    }

    /** The two pills' outline, which their glass is clipped to. Empty while nothing shows. */
    void pillsPath(@NonNull Path out) {
        out.reset();
        HomePillsPolicy.Layout layout = mNow;
        if (layout.windowsShown && layout.windowsRight > layout.windowsLeft) {
            mRect.set(layout.windowsLeft, layout.pillTop, layout.windowsRight, layout.pillBottom);
            out.addRoundRect(mRect, layout.radius, layout.radius, Path.Direction.CW);
        }
        if (layout.statsShown) {
            mRect.set(layout.statsLeft, layout.pillTop, layout.statsRight, layout.pillBottom);
            out.addRoundRect(mRect, layout.radius, layout.radius, Path.Direction.CW);
        }
    }

    // ---------------------------------------------------------------- layout

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout(false);
    }

    @Override protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTextSizes();
        readThemeColors();
        mFocusedNameRoom = -1f;
        relayout(false);
    }

    private void relayout(boolean animate) {
        int count = mSections.size();
        float[] glyphs = new float[count];
        for (int i = 0; i < count; i++) glyphs[i] = mGlyphPaint.measureText(mSections.get(i).glyph);
        float name = mFocused >= 0 && mFocused < count
            ? mNamePaint.measureText(mSections.get(mFocused).name) : 0f;
        float[] statGlyphs = new float[mStatsGlyphs.length];
        float[] statValues = new float[mStatsValues.length];
        for (int i = 0; i < statGlyphs.length; i++) {
            statGlyphs[i] = mStatsGlyphPaint.measureText(mStatsGlyphs[i]);
            statValues[i] = mStatsValuePaint.measureText(mStatsValues[i]);
        }
        float stats = HomePillsPolicy.statsWidth(mDensity, statGlyphs, statValues);
        mGlyphWidths = glyphs;
        mStatsGlyphWidths = statGlyphs;
        mStatsValueWidths = statValues;
        HomePillsPolicy.Layout target = getWidth() <= 0 || getHeight() <= 0
            ? HomePillsPolicy.EMPTY
            : HomePillsPolicy.layout(mDensity, getWidth(), getHeight(), mInsetStart, mInsetEnd,
                glyphs, mFocused, name, stats);
        // The glass is built here, never in a draw: only a new pill height changes its corners.
        if (mGround != null && target.radius != mGroundRadius) buildGlass(target.radius);
        if (target.equals(mTarget) && mMove == null) {
            invalidatePills();
            return;
        }
        if (mMove != null) {
            mMove.cancel();
            mMove = null;
        }
        mFrom = mNow;
        mTarget = target;
        if (!animate) {
            mNow = target;
            invalidatePills();
            return;
        }
        ValueAnimator move = ValueAnimator.ofFloat(0f, 1f);
        move.setDuration(HomePillsPolicy.FOCUS_MOVE_MS);
        move.setInterpolator(new DecelerateInterpolator());
        move.addUpdateListener(animator -> {
            mNow = HomePillsPolicy.between(mFrom, mTarget, (float) animator.getAnimatedValue());
            invalidatePills();
        });
        move.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (mMove != animation) return;
                mMove = null;
                mNow = mTarget;
                invalidatePills();
            }
        });
        mMove = move;
        move.start();
    }

    /** This view and the bar's glass clipped to it both follow the pills. */
    private void invalidatePills() {
        invalidate();
        ViewParent parent = getParent();
        if (parent instanceof View) ((View) parent).invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        if (mMove != null) {
            mMove.cancel();
            mMove = null;
            mNow = mTarget;
        }
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        HomePillsPolicy.Layout layout = mNow;
        if (!layout.windowsShown && !layout.statsShown) return;
        syncInk();
        float half = mStrokePaint.getStrokeWidth() / 2f;
        float radius = Math.max(0f, layout.radius - half);
        float centerY = (layout.pillTop + layout.pillBottom) / 2f;
        drawGlass(canvas, layout);
        if (layout.windowsShown) {
            mRect.set(layout.windowsLeft + half, layout.pillTop + half,
                layout.windowsRight - half, layout.pillBottom - half);
            canvas.drawRoundRect(mRect, radius, radius, mStrokePaint);
            int saved = canvas.save();
            canvas.clipRect(layout.windowsLeft, layout.pillTop, layout.windowsRight,
                layout.pillBottom);
            float padding = HomePillsPolicy.SECTION_PADDING_DP * mDensity;
            float inset = (layout.pillBottom - layout.pillTop) * 0.25f;
            for (int i = 0; i < layout.sectionCount(); i++) {
                HomePillsPolicy.Section section = mSections.get(i);
                float x = layout.sectionLeft[i] + padding;
                canvas.drawText(section.glyph, x, baseline(mGlyphPaint, centerY), mGlyphPaint);
                if (i == layout.focused && layout.nameRoom > 0f) {
                    float nameX = x + mGlyphWidths[i]
                        + HomePillsPolicy.NAME_GAP_DP * mDensity;
                    // The room the name has while the section grows: never past its right edge.
                    float room = Math.min(layout.nameRoom,
                        layout.sectionRight[i] - padding - nameX);
                    if (room > 0f) {
                        CharSequence name = focusedName(section.name, layout.nameRoom);
                        int clip = canvas.save();
                        canvas.clipRect(nameX, layout.pillTop, nameX + room, layout.pillBottom);
                        canvas.drawText(name, 0, name.length(), nameX,
                            baseline(mNamePaint, centerY), mNamePaint);
                        canvas.restoreToCount(clip);
                    }
                }
                if (i > 0) {
                    float divider = layout.sectionLeft[i];
                    canvas.drawLine(divider, layout.pillTop + inset, divider,
                        layout.pillBottom - inset, mDividerPaint);
                }
            }
            canvas.restoreToCount(saved);
        }
        if (layout.statsShown) {
            mRect.set(layout.statsLeft + half, layout.pillTop + half, layout.statsRight - half,
                layout.pillBottom - half);
            canvas.drawRoundRect(mRect, radius, radius, mStrokePaint);
            float x = layout.statsLeft + HomePillsPolicy.STATS_PADDING_DP * mDensity;
            for (int i = 0; i < mStatsGlyphs.length; i++) {
                if (i > 0) x += HomePillsPolicy.STATS_ITEM_GAP_DP * mDensity;
                canvas.drawText(mStatsGlyphs[i], x, baseline(mStatsGlyphPaint, centerY),
                    mStatsGlyphPaint);
                x += mStatsGlyphWidths[i] + HomePillsPolicy.STATS_GLYPH_GAP_DP * mDensity;
                canvas.drawText(mStatsValues[i], x, baseline(mStatsValuePaint, centerY),
                    mStatsValuePaint);
                x += mStatsValueWidths[i];
            }
        }
    }

    /** Each pill's glass, inside the pills' outline, under the stroke and the ink. */
    private void drawGlass(@NonNull Canvas canvas, @NonNull HomePillsPolicy.Layout layout) {
        if (mWindowsGlass == null && mStatsGlass == null) return;
        pillsPath(mGlassClip);
        if (mGlassClip.isEmpty()) return;
        int saved = canvas.save();
        canvas.clipPath(mGlassClip);
        if (layout.windowsShown && mWindowsGlass != null) {
            mWindowsGlass.setBounds(Math.round(layout.windowsLeft), layout.pillTop,
                Math.round(layout.windowsRight), layout.pillBottom);
            mWindowsGlass.draw(canvas);
        }
        if (layout.statsShown && mStatsGlass != null) {
            mStatsGlass.setBounds(Math.round(layout.statsLeft), layout.pillTop,
                Math.round(layout.statsRight), layout.pillBottom);
            mStatsGlass.draw(canvas);
        }
        canvas.restoreToCount(saved);
    }

    /** The focused name, ellipsized once per room rather than per frame. */
    @NonNull
    private CharSequence focusedName(@NonNull String name, float room) {
        if (mFocusedName == null || mFocusedNameRoom != room) {
            mFocusedName = TextUtils.ellipsize(name, mNamePaint, room, TextUtils.TruncateAt.END);
            mFocusedNameRoom = room;
        }
        return mFocusedName;
    }

    private void syncInk() {
        ChromeInk.Polarity polarity = ChromeShade.polarity();
        if (mInkResolved && polarity == mPolarity) return;
        mInkResolved = true;
        mPolarity = polarity;
        int nominal = ChromeShade.nominalGlass();
        int surface = mGlass && nominal != Color.TRANSPARENT ? nominal : mPanel;
        int ink = HomePillsPolicy.ink(mGlass, polarity, mOnSurface, mSurfaceBase, surface);
        mGlyphPaint.setColor(ink);
        mNamePaint.setColor(ink);
        mStatsGlyphPaint.setColor(ink);
        mStatsValuePaint.setColor(ink);
        int stroke = HomePillsPolicy.stroke(mGlass, polarity, mOnSurface, mSurfaceBase, surface,
            ink);
        mStrokePaint.setColor(stroke);
        mDividerPaint.setColor(com.termux.app.chrome.OnGlass.withAlpha(ink, DIVIDER_ALPHA));
    }

    private void readThemeColors() {
        Context context = getContext();
        mOnSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mSurfaceBase = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        mPanel = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        mInkResolved = false;
    }

    private void applyTextSizes() {
        mGlyphPaint.setTextSize(sp(GLYPH_SP));
        mNamePaint.setTextSize(sp(NAME_SP));
        mStatsGlyphPaint.setTextSize(sp(STATS_GLYPH_SP));
        mStatsValuePaint.setTextSize(sp(STATS_VALUE_SP));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    private static float baseline(@NonNull Paint paint, float centerY) {
        return centerY - (paint.ascent() + paint.descent()) / 2f;
    }

    // ---------------------------------------------------------------- touch

    /**
     * A tap on a section reports it; the stats pill takes its touches and does nothing with them.
     * Off the pills nothing is taken, so the page under the band keeps them.
     */
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        HomePillsPolicy.Layout layout = mNow;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!HomePillsPolicy.onPill(layout, event.getX(), event.getY())) return false;
                mDownSection = HomePillsPolicy.sectionAt(layout, event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP: {
                int section = HomePillsPolicy.sectionAt(layout, event.getX(), event.getY());
                int down = mDownSection;
                mDownSection = -1;
                if (down >= 0 && section == down) {
                    performClick();
                    if (mListener != null) mListener.onSectionTapped(section);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mDownSection = -1;
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** Each section is a node a screen reader can step to and tap; the stats pill is one more. */
    private final class PillsAccessibility extends ExploreByTouchHelper {

        PillsAccessibility(@NonNull View host) { super(host); }

        /** The stats pill's node id, after the sections'. */
        private int statsId() { return mSections.size(); }

        @Override protected int getVirtualViewAt(float x, float y) {
            HomePillsPolicy.Layout layout = mNow;
            int section = HomePillsPolicy.sectionAt(layout, x, y);
            if (section >= 0) return section;
            if (layout.statsShown && HomePillsPolicy.onPill(layout, x, y)
                && x >= layout.statsLeft) return statsId();
            return INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            HomePillsPolicy.Layout layout = mNow;
            if (layout.windowsShown) {
                for (int i = 0; i < layout.sectionCount(); i++) ids.add(i);
            }
            if (layout.statsShown) ids.add(statsId());
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            HomePillsPolicy.Layout layout = mNow;
            android.graphics.Rect bounds = new android.graphics.Rect();
            if (id < mSections.size() && id < layout.sectionCount()) {
                node.setContentDescription(mSections.get(id).spoken);
                node.setSelected(id == layout.focused);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                node.setClickable(true);
                bounds.set(Math.round(layout.sectionLeft[id]), layout.pillTop,
                    Math.round(Math.min(layout.sectionRight[id], layout.windowsRight)),
                    layout.pillBottom);
            } else {
                node.setContentDescription(mStatsSpoken);
                bounds.set(Math.round(layout.statsLeft), layout.pillTop,
                    Math.round(layout.statsRight), layout.pillBottom);
            }
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || id >= mSections.size()) {
                return false;
            }
            if (mListener != null) mListener.onSectionTapped(id);
            return true;
        }
    }
}
