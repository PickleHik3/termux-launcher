package com.termux.app.wall.pageswitch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.termux.R;
import com.termux.app.ScrubWave;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetView;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

import java.util.List;

/**
 * The page bar on Home: a glass well pressed into the page ({@link com.termux.app.chrome.InsetGlass})
 * holding one glyph per page in the wall's own order, the current page's in the accent. A tap on a glyph goes to its page; a slide lifts the glyph under the
 * finger out of the pill on the A–Z bar's wave, its neighbours by less, with the page's name
 * riding above it, and goes to the page on release only ({@link PageBarSlide},
 * {@link PageBarLayout}). The pill's outline never changes: the lift is draw-time offsets of the
 * glyphs, no layout. While lifted the glyphs draw in a drawable on the parent's overlay, so they
 * can rise past this view's bounds; nothing new appears. Nothing animates or invalidates at rest;
 * while a finger is down only this view and that overlay draw.
 *
 * <p>The view is the whole band under Home's grid ({@link PageBarBand}); the touch row fills it
 * and the drawn slab stands in it, centred on the window. The slab's glass is a
 * {@link PaneGlassBackdropView} child, dressed as a widget's slab is.
 * Fed a {@link PageBarModel} by the activity; the model, fold and layout are rebuilt only when
 * it or the width changes.</p>
 */
public final class PageBarView extends ViewGroup implements PageSwitchHitArea {

    /** What the bar asks of its owner; {@link PageBarController} implements it. */
    public interface Entry {
        /** Claims the touches that land on {@code area} ahead of the window's own gestures. */
        void addHitArea(@NonNull PageSwitchHitArea area);
        void removeHitArea(@NonNull PageSwitchHitArea area);
        /** Whether something covers the wall (an editor, the drawer, a sheet): the bar does not take a touch. */
        boolean isSuppressed();
        boolean isReducedMotion();
        /** The accent the current page's glyph is drawn in, before it is made legible. */
        @ColorInt int accent();
        /** The finger crossed to another slot or member: the tick. */
        void focusCrossed();
        /** A tap or a release chose this target. */
        void chosen(@NonNull PageBarModel.Target target);
        /** A slide began or ended; the dock's capsules dim while it runs. */
        void slidingChanged(boolean sliding);
        /** A touch ended in a tap, a commit or a cancel (after {@link #chosen} when it chose). */
        void touchEnded();
    }

    /** The page's name over the lifted glyph. */
    private static final float LABEL_TEXT_SP = 12f;
    /** The current page's accent falls to this share of itself while sliding. */
    private static final float SLIDE_ACCENT_KEEP = 0.45f;

    private final PageBarGlyphPainter mPainter;
    private final PaneGlassBackdropView mGlass;
    private final float mDensity;
    private final int mTouchSlop;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * Home's well ({@link com.termux.app.chrome.InsetGlass}): the shade over it, the inner shadow
     * drawn as rings inside its outline, and the rim hairlines outside it (dark across the top,
     * light across the bottom arc); shaders and ring alphas made by {@link #syncWell} on a size
     * or ground change only.
     */
    private final Paint mWellFloor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWellShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWellRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWellRimLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] mWellFloorColors = new int[com.termux.app.chrome.InsetGlass.FLOOR_POSITIONS.length];
    private final android.graphics.Matrix mWellRimMatrix = new android.graphics.Matrix();
    private float mWellLeft;
    private float mWellWidth;
    @NonNull private int[] mWellRingAlpha = new int[0];
    private int mWellRings;
    private float mWellRingStep;
    private boolean mWellBuilt;
    private float mWellTop;
    private float mWellHeight;
    private boolean mWellDark;
    private final RectF mRect = new RectF();
    private final Rect mInkRect = new Rect();
    private final Rect mInkScratch = new Rect();
    private final int[] mOrigin = new int[2];
    private final int[] mRoot = new int[2];
    private final int[] mAt = new int[2];
    private final PageBarGlass.Ink mInk = new PageBarGlass.Ink();
    /** The mock's translucent fill and outline over the solid card, as alphas of the ink. */
    static final int MOCK_FILL_ALPHA = 0x21;
    static final int MOCK_STROKE_ALPHA = 0x4D;
    /** The name's ink, measured on the wallpaper above the slab ({@link #measureLiftInk}). */
    @ColorInt private int mLabelInk = Color.WHITE;
    /** The current page's and the others' ink for a glyph lifted onto that wallpaper. */
    @ColorInt private int mLiftAccent = Color.WHITE;
    @ColorInt private int mLiftOther = Color.WHITE;
    private final TextPaint mLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float mLabelAscent;
    private final float mLabelDescent;
    /** The name shown over the lifted glyph, ellipsized once per change, and its width. */
    @NonNull private String mLabel = "";
    @NonNull private CharSequence mLabelText = "";
    private float mLabelWidth;
    private float mLabelRoom = -1f;
    private int mLabelSlot;
    /** Draws the lifted glyphs and the name on the parent's overlay while {@link #mLiftHost} holds it. */
    private final LiftLayer mLiftLayer = new LiftLayer();
    @Nullable private ViewGroup mLiftHost;
    /** The widget body the pill was last dressed in ({@link PageBarGlass#measure}), reused. */
    private final com.termux.app.chrome.WidgetBody.Answer mBody =
        new com.termux.app.chrome.WidgetBody.Answer();
    private final ValueAnimator mEnterAnim = ValueAnimator.ofFloat(0f, 1f);
    private final Runnable mRedress = this::redress;
    private final Access mAccess;

    @Nullable private Entry mEntry;
    @Nullable private PageBarModel mModel;
    @Nullable private PageBarFold.Result mFold;
    @Nullable private PageBarLayout mLayout;
    @Nullable private PageBarSlide mSlide;
    @NonNull private String[] mSlotLabels = new String[0];
    @Nullable private BuiltinWidgetView.GlassSource mSource;
    @Nullable private PaneSurfaceStyle mLook;
    private boolean mGlassShown;
    private boolean mStream;
    /** The bar is popped over the extra keys: the key's finger is fed in, and only leaving above cancels. */
    private boolean mKeyEntry;
    private boolean mFixedInk;
    @ColorInt private int mFixedInkColor = Color.WHITE;
    @ColorInt private int mFixedAccent = Color.WHITE;
    private boolean mSliding;
    /** 0 at rest, 1 fully sliding; the tween between. */
    private float mEnter;
    private float mFocusFloat;
    /** The pane's left in the window and the window's width, as of the last layout (0: not read yet). */
    private float mPaneLeft;
    private float mWindowWidth;
    private float mPillLeft = -1f;
    private float mPillWidth = -1f;
    /** The key's pop-up as one more card of the stack ({@link PageBarPopupFrame}); -1 width: not set. */
    private float mRaisedLeft;
    private float mRaisedWidth = -1f;
    private float mRaisedRadius = -1f;
    /** The raised glass the key's pop-up wears ({@link com.termux.app.chrome.ElevatedGlass}); null for its solid card. */
    @Nullable private PaneSurfaceStyle mRaisedLook;
    /**
     * The raised glass wants a blur frame that was still baking when it was dressed: the blur
     * cache's landing ({@link #redressIfFrameLate}) dresses it again. Set on a dress pass only.
     */
    private boolean mRaisedFrameLate;
    private final Rect mNoFrame = new Rect();

    public PageBarView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mPainter = new PageBarGlyphPainter();
        setWillNotDraw(false);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(mDensity);
        mWellShadow.setStyle(Paint.Style.STROKE);
        mWellRim.setStyle(Paint.Style.STROKE);
        mWellRimLight.setStyle(Paint.Style.STROKE);
        mGlass = new PaneGlassBackdropView(context);
        mGlass.setClipToOutline(true);
        mGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), slabRadiusPx());
            }
        });
        com.termux.app.terminal.PaneGlass.followLayout(mGlass);
        mGlass.setVisibility(GONE);
        addView(mGlass);
        mLabelPaint.setTextSize(android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP,
            context.getResources().getDisplayMetrics()));
        mLabelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint.FontMetrics fm = mLabelPaint.getFontMetrics();
        mLabelAscent = -fm.ascent;
        mLabelDescent = fm.descent;
        mEnterAnim.setDuration(ScrubWave.SETTLE_MS);
        mEnterAnim.addUpdateListener(a -> {
            mEnter = (float) a.getAnimatedValue();
            onEnterChanged();
        });
        mEnterAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mEnter <= 0f && !mSliding) hideLift();
            }
        });
        mAccess = new Access(this);
        androidx.core.view.ViewCompat.setAccessibilityDelegate(this, mAccess);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setEntry(@Nullable Entry entry) {
        if (mEntry == entry) return;
        if (mEntry != null) mEntry.removeHitArea(this);
        mEntry = entry;
        if (entry != null && isAttachedToWindow()) entry.addHitArea(this);
    }

    /** The bar as the extra-keys key pops it up; set once, before it has a width. */
    public void setKeyEntry(boolean keyEntry) {
        if (mKeyEntry == keyEntry) return;
        mKeyEntry = keyEntry;
        rebuild();
    }

    /**
     * The key's pop-up's drawn bar: its left and width in this view and its corner radius
     * ({@link PageBarPopupFrame}). Rebuilds only when one differs.
     */
    void setRaisedFrame(float leftPx, float widthPx, float radiusPx) {
        if (leftPx == mRaisedLeft && widthPx == mRaisedWidth && radiusPx == mRaisedRadius) return;
        mRaisedLeft = leftPx;
        mRaisedWidth = widthPx;
        mRaisedRadius = radiusPx;
        rebuild();
        mGlass.invalidateOutline();
    }

    /** The raised glass the key's pop-up wears while shown; null puts it on its solid card. */
    void setRaisedGlass(@Nullable PaneSurfaceStyle look) {
        mRaisedLook = look;
    }

    /**
     * The key's pop-up was put away: its glass lets the shared blur frame go and hides, so nothing
     * is held while it is away.
     */
    void dropRaisedGlass() {
        mRaisedLook = null;
        mRaisedFrameLate = false;
        mGlassShown = false;
        mGlass.setGlass(null, mNoFrame, Color.TRANSPARENT, null, 0, slabRadiusPx(), null, false);
        if (mGlass.getVisibility() != GONE) mGlass.setVisibility(GONE);
    }

    /** The drawn slab's corner radius: the raised frame's for the key's pop-up, else a capsule. */
    private float slabRadiusPx() {
        PageBarLayout layout = mLayout;
        float capsule = layout == null ? 0f : layout.slabHeightPx() / 2f;
        return mKeyEntry && mRaisedRadius >= 0f ? Math.min(mRaisedRadius, capsule) : capsule;
    }

    /**
     * Ink and accent for a bar with no Home ground to measure (the key's pop-up): used as they
     * are, and the fallback fill and outline are drawn in them.
     */
    public void setFixedInk(@ColorInt int ink, @ColorInt int accent) {
        if (mFixedInk && mFixedInkColor == ink && mFixedAccent == accent) return;
        mFixedInk = true;
        mFixedInkColor = ink;
        mFixedAccent = accent;
        fixInks();
        invalidate();
    }

    /**
     * Takes the model; rebuilds the fold and layout only when it differs from the one held, and
     * invalidates only when something drawn changed.
     */
    public void setModel(@Nullable PageBarModel model) {
        if (sameAs(mModel, model)) {
            mModel = model;
            return;
        }
        mModel = model;
        rebuild();
    }

    // ---- Build ------------------------------------------------------------------------------

    private void rebuild() {
        cancelStream();
        PageBarModel model = mModel;
        int width = getWidth();
        if (model == null || model.entries().isEmpty() || width <= 0) {
            mFold = null;
            mLayout = null;
            mSlide = null;
            mSlotLabels = new String[0];
        } else {
            List<PageBarModel.Entry> entries = model.entries();
            mFold = PageBarFold.fold(entries, PageBarLayout.maxSlots(mDensity, width));
            int slots = mFold.slots.size();
            if (mKeyEntry && mRaisedWidth > 0f) {
                mLayout = PageBarLayout.raised(mDensity, width, slots, mRaisedLeft, mRaisedWidth);
            } else {
                mLayout = mKeyEntry ? new PageBarLayout(mDensity, width, slots, true)
                    : new PageBarLayout(mDensity, width, slots, mPaneLeft, mWindowWidth);
            }
            mSlide = new PageBarSlide(mLayout, mFold, entries, mTouchSlop, mKeyEntry);
            mSlotLabels = new String[slots];
            for (int i = 0; i < slots; i++) mSlotLabels[i] = labelOf(mFold.slots.get(i), entries);
        }
        float left = mLayout == null ? -1f : mLayout.pillLeftPx();
        float pill = mLayout == null ? -1f : mLayout.pillWidthPx();
        if (left != mPillLeft || pill != mPillWidth) {
            mPillLeft = left;
            mPillWidth = pill;
            if (!isInLayout()) requestLayout();
        }
        mAccess.invalidateRoot();
        scheduleRedress();
        invalidate();
    }

    private String labelOf(PageBarFold.Slot slot, List<PageBarModel.Entry> entries) {
        if (!slot.folded()) return entries.get(slot.first).label;
        return getResources().getQuantityString(R.plurals.page_bar_folded, slot.count,
            getResources().getString(kindName(slot.kind)), slot.count);
    }

    private static int kindName(PageKind kind) {
        switch (kind) {
            case HOME: return R.string.page_switch_home;
            case TERMINAL: return R.string.page_switch_terminal;
            case ASSISTANT: return R.string.page_switch_assistant;
            default: return R.string.page_switch_display;
        }
    }

    private static boolean sameAs(@Nullable PageBarModel a, @Nullable PageBarModel b) {
        if (a == b) return true;
        if (a == null || b == null || a.entries().size() != b.entries().size()) return false;
        for (int i = 0; i < a.entries().size(); i++) {
            PageBarModel.Entry x = a.entries().get(i);
            PageBarModel.Entry y = b.entries().get(i);
            if (x.kind != y.kind || x.number != y.number || x.current != y.current
                || x.returnTo != y.returnTo || !x.label.equals(y.label)
                || x.target.action != y.target.action || x.target.pageId != y.target.pageId
                || x.target.index != y.target.index) {
                return false;
            }
        }
        return true;
    }

    // ---- Layout and glass -------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(Math.round(PageBarLayout.bandHeightDp() * mDensity), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && !mKeyEntry && notePaneInWindow()) rebuild();
        PageBarLayout layout = mLayout;
        if (layout == null) return;
        int left = Math.round(layout.slabLeftPx());
        int top = Math.round(layout.slabTopPx());
        int w = Math.round(layout.slabWidthPx());
        int h = Math.round(layout.slabHeightPx());
        mGlass.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        mGlass.layout(left, top, left + w, top + h);
        syncWell(layout);
    }

    /**
     * Home's well cues for the slab where it stands now and the ground it was measured on
     * ({@link PageBarGlass.Ink#wellDark}): the shade, the inner shadow fading down from the top
     * edge and the lip fading in toward the bottom. Builds only when one of those changed: on a
     * layout or a dress pass, never per frame.
     */
    private void syncWell(@Nullable PageBarLayout layout) {
        if (layout == null || mKeyEntry) return;
        float top = layout.slabTopPx();
        float height = layout.slabHeightPx();
        boolean dark = mInk.wellDark;
        float left = layout.slabLeftPx();
        float width = layout.slabWidthPx();
        if (mWellBuilt && top == mWellTop && height == mWellHeight && dark == mWellDark
                && left == mWellLeft && width == mWellWidth) {
            return;
        }
        mWellBuilt = true;
        mWellLeft = left;
        mWellWidth = width;
        mWellTop = top;
        mWellHeight = height;
        mWellDark = dark;
        // The floor: darkest under the top lip, clear in the lower part, a faint light at the bottom.
        mWellFloor.setShader(new LinearGradient(0f, top, 0f, top + height,
            com.termux.app.chrome.InsetGlass.floorColors(dark, mWellFloorColors),
            com.termux.app.chrome.InsetGlass.FLOOR_POSITIONS, Shader.TileMode.CLAMP));
        // The contact shadow: rings inside the outline, their colour by height (strongest under
        // the top, the sides' share at mid height, clear at the bottom), their alpha by depth.
        int rings = com.termux.app.chrome.InsetGlass.ringCount(mDensity, height);
        if (mWellRingAlpha.length < rings) mWellRingAlpha = new int[rings];
        for (int i = 0; i < rings; i++) {
            mWellRingAlpha[i] = com.termux.app.chrome.InsetGlass.ringAlpha(i, rings);
        }
        mWellRings = rings;
        mWellRingStep = com.termux.app.chrome.InsetGlass.ringStepPx(mDensity);
        // Each ring is twice the step wide, so neighbours overlap and no seam shows between them.
        mWellShadow.setStrokeWidth(2f * mWellRingStep);
        int shadowTop = com.termux.app.chrome.InsetGlass.innerShadow(dark);
        int shadowSide = com.termux.app.chrome.InsetGlass.innerShadowSide(dark);
        mWellShadow.setShader(rings <= 0 ? null : new LinearGradient(0f, top, 0f, top + height,
            new int[] {shadowTop, shadowSide, shadowSide & 0x00FFFFFF}, new float[] {0f, 0.5f, 1f},
            Shader.TileMode.CLAMP));
        // The rim of the hole, outside the outline: a subtle dark hairline across the top, and
        // the bottom arc's light, an ellipse round the centre bottom as wide as the well and
        // RIM_LIGHT_REACH_SHARE of its height tall, so it is strongest there and fades up the ends.
        float rim = com.termux.app.chrome.InsetGlass.rimWidthPx(mDensity, height);
        int rimShadow = com.termux.app.chrome.InsetGlass.rimShadow(dark);
        int rimLight = com.termux.app.chrome.InsetGlass.rimLight(dark);
        mWellRim.setStrokeWidth(rim);
        mWellRim.setShader(rim <= 0f ? null : new LinearGradient(0f, top - rim, 0f,
            top + height * com.termux.app.chrome.InsetGlass.RIM_SHADOW_TO_SHARE,
            rimShadow, rimShadow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        mWellRimLight.setStrokeWidth(rim);
        float reach = Math.max(1f, height * com.termux.app.chrome.InsetGlass.RIM_LIGHT_REACH_SHARE);
        float cx = left + width / 2f;
        float bottom = top + height + rim;
        android.graphics.RadialGradient light = rim <= 0f ? null : new android.graphics.RadialGradient(
            cx, bottom, reach, new int[] {rimLight,
                com.termux.app.chrome.OnGlass.withAlpha(rimLight, Math.round((rimLight >>> 24)
                    * com.termux.app.chrome.InsetGlass.RIM_LIGHT_MID)),
                rimLight & 0x00FFFFFF},
            new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        if (light != null) {
            mWellRimMatrix.setScale(Math.max(1f, (width / 2f + rim) / reach), 1f, cx, bottom);
            light.setLocalMatrix(mWellRimMatrix);
        }
        mWellRimLight.setShader(light);
        invalidate();
    }

    /**
     * Reads where the pane stands in its window, which the hugged pill centres on: this view's
     * left through its parents' layout lefts (the wall's slide is a translation and is not in
     * them), and the window's width. Runs when this view is laid out with a new frame, never per
     * frame, so rotation, split screen and a side rail changing the pane all reach it.
     *
     * @return whether either changed
     */
    private boolean notePaneInWindow() {
        float left = 0f;
        View root = this;
        for (ViewParent parent = getParent(); parent instanceof View; parent = parent.getParent()) {
            left += root.getLeft() - ((View) parent).getScrollX();
            root = (View) parent;
        }
        float window = root == this ? 0f : root.getWidth();
        if (left == mPaneLeft && window == mWindowWidth) return false;
        mPaneLeft = left;
        mWindowWidth = window;
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) rebuild();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mEntry != null) mEntry.addHitArea(this);
        redress();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mEntry != null) mEntry.removeHitArea(this);
        removeCallbacks(mRedress);
        cancelStream();
        mEnterAnim.cancel();
        mEnter = 0f;
        hideLift();
        if (mSource != null) mSource.setBarGlassWanted(false);
        mSource = null;
        super.onDetachedFromWindow();
    }

    private void scheduleRedress() {
        removeCallbacks(mRedress);
        if (isAttachedToWindow()) post(mRedress);
    }

    /**
     * The glass, ink and accent again, on a dress pass of the page (a new Look, frame, wallpaper
     * or mode), never per frame. Compares before it invalidates.
     */
    public void redress() {
        if (mKeyEntry) {
            redressRaised(mLayout);
            return;
        }
        mSource = PageBarGlass.sourceFor(this);
        mLook = mSource == null ? null : mSource.widgetGlassStyle();
        PageBarLayout layout = mLayout;
        if (mSource != null) mSource.setBarGlassWanted(layout != null && PageBarGlass.wants(getContext(), mLook));
        boolean shown = layout != null && PageBarGlass.dress(getContext(), mLook, this, mGlass,
            layout.slabHeightPx() / 2f);
        mGlassShown = shown;
        if (shown) mGlass.invalidateOutline();
        if (mFixedInk) {
            mInk.veil = Color.TRANSPARENT;
            fixInks();
            mGlass.setEvenOut(null);
            liftInkIsSlabInk();
            invalidate();
            return;
        }
        int accent = mEntry == null ? Color.WHITE : mEntry.accent();
        mInk.ground = Color.TRANSPARENT;
        mInk.veil = Color.TRANSPARENT;
        mInk.ink = Color.WHITE;
        mInk.other = Color.WHITE;
        mInk.wellDark = true;
        mBody.evenOut = null;
        if (layout != null) {
            mInkRect.set(Math.round(layout.slabLeftPx()), Math.round(layout.slabTopPx()),
                Math.round(layout.slabLeftPx() + layout.slabWidthPx()),
                Math.round(layout.slabBottomPx()));
            // Where the glyphs' tops and bottoms stand down the well, for the floor's ink check.
            float glyphShare = layout.glyphPx() / Math.max(1f, layout.slabHeightPx());
            PageBarGlass.measure(this, mInkRect, mLook, accent, shown, mInk, mBody, mGlass,
                (1f - glyphShare) / 2f, (1f + glyphShare) / 2f, mOrigin, mRoot, mInkScratch);
        } else {
            mInk.accent = accent;
            mGlass.setEvenOut(null);
        }
        measureLiftInk(layout);
        syncWell(layout);
        invalidate();
    }

    /**
     * The key's pop-up dressed in its raised glass where the Look's glass may be worn (the system's
     * battery saver, outline text and high contrast put it on its solid card, as a widget's), the
     * glyph inks solved on the glass's ground; else the solid card and its fixed inks. Once per
     * opening and on a dress pass, never per frame.
     */
    private void redressRaised(@Nullable PageBarLayout layout) {
        PaneSurfaceStyle look = mRaisedLook;
        boolean shown = layout != null && look != null && PageBarGlass.wants(getContext(), look);
        if (shown) {
            float radius = slabRadiusPx();
            shown = com.termux.app.terminal.PaneGlass.applyAtRadius(look, this, mGlass, radius);
            if (shown) {
                // The raised rim is drawn over the glass in dispatchDraw, not the style's own.
                mGlass.setRim(-1f, look);
                if (mGlass.getVisibility() != VISIBLE) mGlass.setVisibility(VISIBLE);
                mGlass.setElevation(getResources().getDimension(
                    com.google.android.material.R.dimen.m3_comp_menu_container_elevation));
                mGlass.invalidateOutline();
            }
        }
        if (!shown && mGlass.getVisibility() != GONE) mGlass.setVisibility(GONE);
        mGlassShown = shown;
        mRaisedFrameLate = shown && look instanceof com.termux.app.chrome.LookGlassStyle
            && ((com.termux.app.chrome.LookGlassStyle) look).spec().wantsBlurFrame()
            && !holdsFrame(mGlass);
        mGlass.setEvenOut(null);
        if (shown) {
            int wallpaper = Color.TRANSPARENT;
            if (look instanceof com.termux.app.chrome.WidgetInkSource) {
                com.termux.app.chrome.WidgetInkSource source =
                    (com.termux.app.chrome.WidgetInkSource) look;
                mInkRect.set(Math.round(layout.slabLeftPx()), Math.round(layout.slabTopPx()),
                    Math.round(layout.slabLeftPx() + layout.slabWidthPx()),
                    Math.round(layout.slabBottomPx()));
                com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(this, mOrigin, mRoot);
                mInkRect.offset(mOrigin[0], mOrigin[1]);
                wallpaper = source.wallpaperSample(mInkRect);
                if (Color.alpha(wallpaper) == 0) wallpaper = source.wallpaperFallback();
            }
            PageBarGlass.raisedInk(PageBarGlass.raisedGround(look.paneGlassTintColor(), wallpaper,
                mFixedInkColor), mFixedInkColor, mFixedAccent, mInk);
        } else {
            mInk.veil = Color.TRANSPARENT;
            fixInks();
        }
        liftInkIsSlabInk();
        invalidate();
    }

    private static boolean holdsFrame(@NonNull PaneGlassBackdropView glass) {
        android.graphics.Bitmap frame = glass.heldFrame();
        return frame != null && !frame.isRecycled();
    }

    /**
     * The blur cache landed a frame (a glass pass): the key's pop-up, shown and dressed while its
     * raised radius was still baking, is dressed again so it wears the frame instead of its tint
     * alone. Two fields read otherwise; nothing per frame.
     */
    void redressIfFrameLate() {
        if (!mKeyEntry || !mRaisedFrameLate || mRaisedLook == null || getVisibility() != VISIBLE) {
            return;
        }
        redressRaised(mLayout);
    }

    /**
     * The key's pop-up inks: its fixed ink and accent, the glyphs solved on the card it lays under
     * them with the mock's translucent fill over it, as {@link #dispatchDraw} draws it.
     */
    private void fixInks() {
        int card = PageBarGlass.cardFor(mFixedInkColor);
        int ground = com.termux.app.chrome.OnGlass.opaque(com.termux.app.chrome.OnGlass.composite(
            com.termux.app.chrome.OnGlass.withAlpha(mFixedInkColor, MOCK_FILL_ALPHA), card));
        mInk.ground = ground;
        mInk.ink = mFixedInkColor;
        mInk.accent = com.termux.app.chrome.PageMarkInk.current(ground, mFixedAccent,
            com.termux.app.chrome.GlassInk.isPaleSide(mFixedInkColor, ground));
        mInk.other = com.termux.app.chrome.PageMarkInk.other(ground, mFixedInkColor, mInk.accent);
    }

    /** No wallpaper to read (the key's pop-up, or no Look yet): lifted glyphs and the name keep the slab's inks. */
    private void liftInkIsSlabInk() {
        mLabelInk = mInk.ink;
        mLiftAccent = mInk.accent;
        mLiftOther = mInk.other;
    }

    /**
     * The inks above the slab, where lifted glyphs and the name stand on bare wallpaper: the
     * name's bare-text ink ({@link PageBarGlass#labelInk}) and the page marks' inks solved on that
     * wallpaper as {@link #mInk} is on the slab ({@link com.termux.app.chrome.PageMarkInk}). One
     * sample from the name's peak down to the slab's top; a dress pass, never per frame.
     */
    private void measureLiftInk(@Nullable PageBarLayout layout) {
        if (layout == null || !(mLook instanceof com.termux.app.chrome.WidgetInkSource)) {
            liftInkIsSlabInk();
            return;
        }
        com.termux.app.chrome.WidgetInkSource source = (com.termux.app.chrome.WidgetInkSource) mLook;
        float top = layout.labelBaselinePeakPx(mLabelDescent) - mLabelAscent;
        mInkRect.set(Math.round(layout.slabLeftPx()), Math.round(top),
            Math.round(layout.slabLeftPx() + layout.slabWidthPx()), Math.round(layout.slabTopPx()));
        com.termux.app.chrome.GlassAnchor.layoutOriginOnScreen(this, mOrigin, mRoot);
        mInkRect.offset(mOrigin[0], mOrigin[1]);
        int wallpaper = source.wallpaperSample(mInkRect);
        if (Color.alpha(wallpaper) == 0) wallpaper = source.wallpaperFallback();
        if (Color.alpha(wallpaper) == 0) {
            liftInkIsSlabInk();
            return;
        }
        int ground = com.termux.app.chrome.OnGlass.opaque(wallpaper);
        mLabelInk = PageBarGlass.labelInk(ground, mInk.ink);
        mLiftAccent = com.termux.app.chrome.PageMarkInk.current(ground, mInk.accent,
            com.termux.app.chrome.GlassInk.isPaleSide(mLabelInk, ground));
        mLiftOther = com.termux.app.chrome.PageMarkInk.other(ground, mLabelInk, mLiftAccent);
    }

    // ---- Drawing ----------------------------------------------------------------------------

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        PageBarLayout layout = mLayout;
        PageBarFold.Result fold = mFold;
        if (layout == null || fold == null) return;
        float left = layout.slabLeftPx();
        float top = layout.slabTopPx();
        float height = layout.slabHeightPx();
        mRect.set(left, top, left + layout.slabWidthPx(), top + height);
        float radius = slabRadiusPx();
        if (mGlassShown && mKeyEntry) {
            // The raised glass's rim, the over-glass rim made stronger (ElevatedGlass), only where
            // the glass draws no Fancier Glass edge of its own: that edge is the cards' rim light.
            if (!mGlass.drawsEdge()) {
                mStroke.setColor(com.termux.app.chrome.OnGlass.withAlpha(
                    com.termux.app.chrome.GlassTokens.HIGHLIGHT,
                    com.termux.app.chrome.ElevatedGlass.rimAlpha(
                        Color.alpha(com.termux.app.chrome.GlassTokens.RIM_OVER_GLASS_START))));
                mRect.inset(0.5f * mDensity, 0.5f * mDensity);
                canvas.drawRoundRect(mRect, radius, radius, mStroke);
            }
        } else if (mGlassShown) {
            if (Color.alpha(mInk.veil) != 0) {
                mFill.setColor(mInk.veil);
                canvas.drawRoundRect(mRect, radius, radius, mFill);
            }
        } else {
            // No glass: the solid card the ink was measured for, as a widget's slab falls back
            // to (PageBarGlass.measure); the key's pop-up, with no ground measured, lays the card
            // its fixed ink reads on, since it stands over the terminal's text. Then the mock's
            // translucent fill and outline, in the ink.
            int card = mFixedInk ? PageBarGlass.cardFor(mInk.ink) : mInk.veil;
            if (Color.alpha(card) != 0) {
                mFill.setColor(card);
                canvas.drawRoundRect(mRect, radius, radius, mFill);
            }
            mFill.setColor(com.termux.app.chrome.OnGlass.withAlpha(mInk.ink, MOCK_FILL_ALPHA));
            canvas.drawRoundRect(mRect, radius, radius, mFill);
            // The ink outline is the key's card's; Home's well has no light inside it.
            if (mKeyEntry) {
                mStroke.setColor(com.termux.app.chrome.OnGlass.withAlpha(mInk.ink, MOCK_STROKE_ALPHA));
                mRect.inset(0.5f * mDensity, 0.5f * mDensity);
                canvas.drawRoundRect(mRect, radius, radius, mStroke);
            }
        }
        if (!mKeyEntry) drawWell(canvas, layout, radius);
        // While the overlay holds the lift, the glyphs draw there only, never twice; without a
        // parent to lift into they lift here, clipped to the band. Lifted glyphs rise out of the
        // well over its cues.
        if (mLiftHost == null) drawGlyphs(canvas, layout, fold, mEnter);
    }

    /**
     * Home's well over its glass (or its solid card): the shade, the inner shadow's rings inside
     * the outline and the rim hairlines just outside it ({@link #syncWell}). The rim stands in
     * the touch row's air around the slab, inside this view, so nothing is laid out for it.
     * Allocates nothing.
     */
    private void drawWell(@NonNull Canvas canvas, @NonNull PageBarLayout layout, float radius) {
        float left = layout.slabLeftPx();
        float top = layout.slabTopPx();
        float right = left + layout.slabWidthPx();
        float bottom = top + layout.slabHeightPx();
        mRect.set(left, top, right, bottom);
        canvas.drawRoundRect(mRect, radius, radius, mWellFloor);
        if (mWellShadow.getShader() != null) {
            // Ring i stands i steps in; its stroke reaches from the outline (ring 0) inward.
            for (int i = 0; i < mWellRings; i++) {
                float inset = mWellRingStep * (i + 1);
                if (2f * inset >= bottom - top) break;
                mRect.set(left + inset, top + inset, right - inset, bottom - inset);
                float r = Math.max(0f, radius - inset);
                mWellShadow.setAlpha(mWellRingAlpha[i]);
                canvas.drawRoundRect(mRect, r, r, mWellShadow);
            }
        }
        float rim = mWellRim.getStrokeWidth();
        if (rim > 0f && mWellRim.getShader() != null) {
            // Outset by half the stroke so the hairline lies just outside the outline.
            mRect.set(left - rim / 2f, top - rim / 2f, right + rim / 2f, bottom + rim / 2f);
            float r = radius + rim / 2f;
            canvas.drawRoundRect(mRect, r, r, mWellRim);
            if (mWellRimLight.getShader() != null) canvas.drawRoundRect(mRect, r, r, mWellRimLight);
        }
    }

    /**
     * Every glyph at its lift for {@code enter} (0: at rest). A glyph's ink moves from the slab's
     * to the wallpaper's as it leaves the slab ({@link PageBarLayout#outOfSlab}). Allocates nothing.
     */
    private void drawGlyphs(@NonNull Canvas canvas, @NonNull PageBarLayout layout,
                            @NonNull PageBarFold.Result fold, float enter) {
        float focus = mFocusFloat;
        float glyph = layout.glyphPx();
        List<PageBarFold.Slot> slots = fold.slots;
        int keep = Math.round(255f * (1f - (1f - SLIDE_ACCENT_KEEP) * mEnter)) << 24;
        int accent = (mInk.accent & 0x00FFFFFF) | keep;
        int liftAccent = (mLiftAccent & 0x00FFFFFF) | keep;
        // The others a tier under the current page (PageMarkInk), solved at the dress pass.
        int neutral = mInk.other;
        for (int i = 0; i < slots.size(); i++) {
            float cx = layout.restCenterX(i);
            float cy = layout.liftedCenterYPx(i, focus, enter);
            float size = glyph * layout.scaleAt(i, focus, enter);
            float out = enter > 0f ? layout.outOfSlab(i, focus, enter) : 0f;
            PageBarFold.Slot slot = slots.get(i);
            int other = out > 0f ? ColorUtils.blendARGB(neutral, mLiftOther, out) : neutral;
            int fill = slot.current
                ? (out > 0f ? ColorUtils.blendARGB(accent, liftAccent, out) : accent) : other;
            if (slot.folded()) {
                mPainter.drawStack(canvas, slot.kind, slot.number, cx, cy, size, fill, slot.current,
                    other);
            } else {
                mPainter.draw(canvas, slot.kind, slot.number, cx, cy, size, fill, slot.current);
            }
        }
    }

    /** The page's name over the lifted glyph under the finger, fading with the lift. */
    private void drawLabel(@NonNull Canvas canvas, @NonNull PageBarLayout layout) {
        CharSequence text = mLabelText;
        if (text.length() == 0 || mEnter <= 0f || mLabelSlot >= layout.slotCount()) return;
        float baseline = layout.labelBaselinePx(mLabelSlot, mFocusFloat, mEnter, mLabelDescent);
        float x = layout.labelLeftPx(mLabelSlot, mLabelWidth);
        mLabelPaint.setColor((mLabelInk & 0x00FFFFFF)
            | (Math.round(Color.alpha(mLabelInk) * Math.min(1f, mEnter)) << 24));
        canvas.drawText(text, 0, text.length(), x, baseline, mLabelPaint);
    }

    /** Names the page under the finger; ellipsizes only when the name or its room changed. */
    private void bindLabel(int slot, @NonNull String label, float room) {
        mLabelSlot = slot;
        if (label.equals(mLabel) && room == mLabelRoom) return;
        mLabel = label;
        mLabelRoom = room;
        mLabelText = TextUtils.ellipsize(label, mLabelPaint, room, TextUtils.TruncateAt.END);
        mLabelWidth = mLabelPaint.measureText(mLabelText, 0, mLabelText.length());
    }

    /** The lifted glyphs and the name, on the parent's overlay in the parent's coordinates. */
    private final class LiftLayer extends Drawable {
        @Override public void draw(@NonNull Canvas canvas) {
            PageBarLayout layout = mLayout;
            PageBarFold.Result fold = mFold;
            if (layout == null || fold == null) return;
            int save = canvas.save();
            canvas.translate(getLeft() + getTranslationX(), getTop() + getTranslationY());
            drawGlyphs(canvas, layout, fold, mEnter);
            drawLabel(canvas, layout);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) {}

        @Override public void setColorFilter(@Nullable android.graphics.ColorFilter filter) {}

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---- Touch ------------------------------------------------------------------------------

    private boolean inPill(float x, float y) {
        PageBarLayout layout = mLayout;
        return layout != null && x >= layout.pillLeftPx()
            && x < layout.pillLeftPx() + layout.pillWidthPx()
            && y >= layout.pillTopPx() && y < layout.pillBottomPx();
    }

    /** The pill on screen, while the bar is shown and has a glyph to touch. */
    @Override
    public boolean containsRaw(float rawX, float rawY) {
        if (mLayout == null || mEntry == null || !isShown()) return false;
        getLocationOnScreen(mAt);
        return inPill(rawX - mAt[0], rawY - mAt[1]);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mSlide == null || mEntry == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mEntry.isSuppressed() || !inPill(event.getX(), event.getY())) return false;
                beginTouch(event.getX(), event.getY());
                // The Home pager must not page on this touch.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mStream) return false;
                step(event.getActionMasked(), event.getX(), event.getY());
                return true;
            default:
                return mStream;
        }
    }

    private void beginTouch(float x, float y) {
        PageBarSlide slide = mSlide;
        if (slide == null) return;
        slide.down(x, y);
        mStream = true;
    }

    private PageBarSlide.Result step(int action, float x, float y) {
        PageBarSlide slide = mSlide;
        if (slide == null) return PageBarSlide.Result.NONE;
        PageBarSlide.Result result;
        if (action == MotionEvent.ACTION_MOVE) {
            result = slide.move(x, y);
        } else if (action == MotionEvent.ACTION_UP) {
            result = slide.up(x, y);
            mStream = false;
        } else {
            result = slide.cancel();
            mStream = false;
        }
        onResult(slide, result);
        return result;
    }

    /**
     * The key entry's finger, in this view's coordinates: the popup's owner feeds the stream the
     * key took. A down starts a press wherever it lands (the finger is on the key, below the pill).
     */
    @NonNull
    PageBarSlide.Result feed(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            beginTouch(x, y);
            return PageBarSlide.Result.NONE;
        }
        return mStream ? step(action, x, y) : PageBarSlide.Result.NONE;
    }

    /** Whether a slide has begun (the finger moved on past the slop) in the touch being fed. */
    boolean isSliding() {
        return mSliding;
    }

    /** Ends the touch being fed with nothing chosen and no callbacks: a release on the key itself. */
    void abandon() {
        cancelStream();
    }

    /** Puts the bar away: the touch, the slide and the lift, all given back. */
    public void release() {
        cancelStream();
        mEnterAnim.cancel();
        mEnter = 0f;
        hideLift();
    }

    private void onResult(@NonNull PageBarSlide slide, @NonNull PageBarSlide.Result result) {
        Entry entry = mEntry;
        PageBarModel model = mModel;
        switch (result) {
            case TAP:
            case COMMIT:
                endSlide();
                if (entry != null && model != null && slide.entryIndex() >= 0) {
                    entry.chosen(model.entries().get(slide.entryIndex()).target);
                }
                if (entry != null) entry.touchEnded();
                return;
            case CANCEL:
                endSlide();
                if (entry != null) entry.touchEnded();
                return;
            default:
                break;
        }
        if (slide.state() != PageBarSlide.State.SLIDING) return;
        if (!mSliding) startSlide();
        if (slide.focusChanged() && entry != null) entry.focusCrossed();
        PageBarLayout layout = mLayout;
        if (layout != null) {
            mFocusFloat = layout.focusAt(slide.x());
            nameFocus(layout, slide);
        }
        onEnterChanged();
    }

    private void cancelStream() {
        PageBarSlide slide = mSlide;
        if (mStream && slide != null) slide.cancel();
        mStream = false;
        if (mSliding) endSlide();
    }

    // ---- Sliding ----------------------------------------------------------------------------

    private void startSlide() {
        mSliding = true;
        if (mEntry != null) mEntry.slidingChanged(true);
        showLift();
        animateEnter(1f);
    }

    private void endSlide() {
        if (!mSliding) return;
        mSliding = false;
        if (mEntry != null) mEntry.slidingChanged(false);
        animateEnter(0f);
    }

    /** Rises to 1 or settles to 0 over the A–Z wave's settle time, on a draw value only. */
    private void animateEnter(float to) {
        mEnterAnim.cancel();
        if (mEntry == null || mEntry.isReducedMotion() || !isAttachedToWindow()) {
            mEnter = to;
            onEnterChanged();
            if (to <= 0f) hideLift();
            return;
        }
        mEnterAnim.setFloatValues(mEnter, to);
        mEnterAnim.start();
    }

    /** Draw-time only: the bar and the lift repaint; nothing lays out. */
    private void onEnterChanged() {
        invalidate();
        if (mLiftHost != null) mLiftLayer.invalidateSelf();
    }

    /** The page under the finger names the label: a folded slot's member under it. */
    private void nameFocus(@NonNull PageBarLayout layout, @NonNull PageBarSlide slide) {
        PageBarFold.Result fold = mFold;
        PageBarModel model = mModel;
        if (fold == null || model == null) return;
        int slotIndex = slide.focusSlot();
        if (slotIndex < 0 || slotIndex >= fold.slots.size()) return;
        PageBarFold.Slot slot = fold.slots.get(slotIndex);
        int member = slot.folded() ? layout.subIndexAt(slide.x(), slotIndex, slot.count) : 0;
        bindLabel(slotIndex, model.entries().get(slot.first + member).label, layout.labelRoomPx());
    }

    /** Lays the lift layer on the parent's overlay, sized to the parent; once per slide. */
    private void showLift() {
        if (mLiftHost != null || !(getParent() instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) getParent();
        mLiftLayer.setBounds(0, 0, host.getWidth(), host.getHeight());
        host.getOverlay().add(mLiftLayer);
        mLiftHost = host;
        invalidate();
    }

    /** Takes the lift layer off the overlay; the glyphs draw in the bar again. */
    private void hideLift() {
        ViewGroup host = mLiftHost;
        if (host == null) return;
        host.getOverlay().remove(mLiftLayer);
        mLiftHost = null;
        invalidate();
    }

    // ---- Accessibility ----------------------------------------------------------------------

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccess.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** One labelled button per slot; a click is a tap on it. */
    private final class Access extends ExploreByTouchHelper {

        Access(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            PageBarLayout layout = mLayout;
            if (layout == null || !inPill(x, y)) return INVALID_ID;
            return layout.slotAt(x);
        }

        @Override protected void getVisibleVirtualViews(@NonNull List<Integer> ids) {
            for (int i = 0; i < mSlotLabels.length; i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            PageBarLayout layout = mLayout;
            PageBarFold.Result fold = mFold;
            if (layout == null || fold == null || id < 0 || id >= mSlotLabels.length) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            node.setClassName("android.widget.Button");
            node.setContentDescription(mSlotLabels[id]);
            node.setSelected(fold.slots.get(id).current);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
            int left = Math.round(layout.pillLeftPx() + layout.slotWidthPx() * id);
            node.setBoundsInParent(new Rect(left, Math.round(layout.pillTopPx()),
                Math.round(left + layout.slotWidthPx()), Math.round(layout.pillBottomPx())));
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            return tapSlot(id);
        }
    }

    /** A tap on slot {@code id} as the touch does it, for a screen reader or a script. */
    private boolean tapSlot(int id) {
        PageBarLayout layout = mLayout;
        PageBarSlide slide = mSlide;
        PageBarModel model = mModel;
        Entry entry = mEntry;
        if (layout == null || slide == null || model == null || entry == null || id < 0
            || id >= layout.slotCount() || entry.isSuppressed()) {
            return false;
        }
        float x = layout.restCenterX(id);
        float y = layout.slabCenterYPx();
        slide.down(x, y);
        if (slide.up(x, y) != PageBarSlide.Result.TAP || slide.entryIndex() < 0) return false;
        entry.chosen(model.entries().get(slide.entryIndex()).target);
        return true;
    }

    /** The slot labels the screen reader speaks, in order; for tests. */
    @NonNull
    String[] slotLabels() {
        return mSlotLabels.clone();
    }
}
