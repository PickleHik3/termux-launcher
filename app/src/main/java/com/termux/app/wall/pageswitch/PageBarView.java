package com.termux.app.wall.pageswitch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.termux.R;
import com.termux.app.launcher.widget.builtin.BuiltinWidgetView;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneSurfaceStyle;

import java.util.List;

/**
 * The page bar on Home: a glass pill of one glyph per page in the wall's own order, the current
 * page's in the accent. A tap on a glyph goes to its page; a slide lifts the glyph under the
 * finger into a {@link PageBarBubbleView} above the pill, spreads its neighbours aside and goes
 * to the page on release only ({@link PageBarSlide}, {@link PageBarLayout}). The pill's outline
 * never changes: the slide is draw-time transforms of the glyphs, no layout. Nothing animates or
 * invalidates at rest; while a finger is down only this view and the bubble draw.
 *
 * <p>The view is the whole band under Home's grid ({@link PageBarLayout#bandHeightDp}); the pill
 * stands in it. Its glass is a {@link PaneGlassBackdropView} child, dressed as a widget's slab is.
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

    private static final long ENTER_MS = 120L;
    /** The current page's accent falls to this share of itself while sliding. */
    private static final float SLIDE_ACCENT_KEEP = 0.45f;
    private static final int PLATE_BACK_ALPHA = 0x59;
    private static final int PLATE_FRONT_ALPHA = 0x99;

    private final PageGlyphPainter mPainter;
    private final PaneGlassBackdropView mGlass;
    private final float mDensity;
    private final int mTouchSlop;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Rect mInkRect = new Rect();
    private final Rect mInkScratch = new Rect();
    private final int[] mOrigin = new int[2];
    private final int[] mRoot = new int[2];
    private final int[] mAt = new int[2];
    private final PageBarGlass.Ink mInk = new PageBarGlass.Ink();
    private final ValueAnimator mEnterAnim = ValueAnimator.ofFloat(0f, 1f);
    private final Runnable mRedress = this::redress;
    private final Access mAccess;

    @Nullable private Entry mEntry;
    @Nullable private PageBarModel mModel;
    @Nullable private PageBarFold.Result mFold;
    @Nullable private PageBarLayout mLayout;
    @Nullable private PageBarSlide mSlide;
    private float[] mCenters = new float[0];
    private float[] mScales = new float[0];
    @NonNull private String[] mSlotLabels = new String[0];
    @Nullable private PageBarBubbleView mBubble;
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
    private float mPillLeft = -1f;
    private float mPillWidth = -1f;

    public PageBarView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mPainter = new PageGlyphPainter(context);
        setWillNotDraw(false);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(mDensity);
        mGlass = new PaneGlassBackdropView(context);
        mGlass.setClipToOutline(true);
        mGlass.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
        com.termux.app.terminal.PaneGlass.followLayout(mGlass);
        mGlass.setVisibility(GONE);
        addView(mGlass);
        mEnterAnim.setDuration(ENTER_MS);
        mEnterAnim.addUpdateListener(a -> {
            mEnter = (float) a.getAnimatedValue();
            onEnterChanged();
        });
        mEnterAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mEnter <= 0f && !mSliding) hideBubble();
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
     * Ink and accent for a bar with no Home ground to measure (the key's pop-up): used as they
     * are, and the fallback fill and outline are drawn in them.
     */
    public void setFixedInk(@ColorInt int ink, @ColorInt int accent) {
        if (mFixedInk && mFixedInkColor == ink && mFixedAccent == accent) return;
        mFixedInk = true;
        mFixedInkColor = ink;
        mFixedAccent = accent;
        mInk.ink = ink;
        mInk.accent = accent;
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
            mLayout = new PageBarLayout(mDensity, width, slots);
            mSlide = new PageBarSlide(mLayout, mFold, entries, mTouchSlop, mKeyEntry);
            if (mCenters.length != slots) {
                mCenters = new float[slots];
                mScales = new float[slots];
            }
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
        PageBarLayout layout = mLayout;
        if (layout == null) return;
        int left = Math.round(layout.pillLeftPx());
        int top = Math.round(layout.pillTopPx());
        int w = Math.round(layout.pillWidthPx());
        int h = Math.round(layout.pillHeightPx());
        mGlass.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        mGlass.layout(left, top, left + w, top + h);
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
        mSource = PageBarGlass.sourceFor(this);
        mLook = mSource == null ? null : mSource.widgetGlassStyle();
        PageBarLayout layout = mLayout;
        if (mSource != null) mSource.setBarGlassWanted(layout != null && PageBarGlass.wants(getContext(), mLook));
        boolean shown = layout != null && PageBarGlass.dress(getContext(), mLook, this, mGlass,
            layout.pillHeightPx() / 2f);
        mGlassShown = shown;
        if (shown) mGlass.invalidateOutline();
        if (mFixedInk) {
            mInk.ground = Color.TRANSPARENT;
            mInk.veil = Color.TRANSPARENT;
            mInk.ink = mFixedInkColor;
            mInk.accent = mFixedAccent;
            PageBarBubbleView popBubble = mBubble;
            if (popBubble != null) popBubble.dress(mLook);
            invalidate();
            return;
        }
        int accent = mEntry == null ? Color.WHITE : mEntry.accent();
        mInk.ground = Color.TRANSPARENT;
        mInk.veil = Color.TRANSPARENT;
        mInk.ink = Color.WHITE;
        if (layout != null) {
            mInkRect.set(Math.round(layout.pillLeftPx()), Math.round(layout.pillTopPx()),
                Math.round(layout.pillLeftPx() + layout.pillWidthPx()),
                Math.round(layout.pillBottomPx()));
            PageBarGlass.measure(this, mInkRect, mLook, accent, shown, mInk, mOrigin, mRoot,
                mInkScratch);
        } else {
            mInk.accent = accent;
        }
        PageBarBubbleView bubble = mBubble;
        if (bubble != null) bubble.dress(mLook);
        invalidate();
    }

    // ---- Drawing ----------------------------------------------------------------------------

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        PageBarLayout layout = mLayout;
        PageBarFold.Result fold = mFold;
        if (layout == null || fold == null) return;
        float left = layout.pillLeftPx();
        float top = layout.pillTopPx();
        float height = layout.pillHeightPx();
        mRect.set(left, top, left + layout.pillWidthPx(), top + height);
        if (mGlassShown) {
            if (Color.alpha(mInk.veil) != 0) {
                mFill.setColor(mInk.veil);
                canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFill);
            }
        } else {
            // No glass: the mock's translucent fill and outline, in the ink.
            mFill.setColor((mInk.ink & 0x00FFFFFF) | 0x21000000);
            canvas.drawRoundRect(mRect, height / 2f, height / 2f, mFill);
            mStroke.setColor((mInk.ink & 0x00FFFFFF) | 0x4D000000);
            mRect.inset(0.5f * mDensity, 0.5f * mDensity);
            canvas.drawRoundRect(mRect, height / 2f, height / 2f, mStroke);
        }
        boolean lifted = mEnter > 0f;
        if (lifted) layout.slidePositions(mFocusFloat, mEnter, mCenters, mScales);
        float cy = top + height / 2f;
        float glyph = layout.glyphPx();
        List<PageBarFold.Slot> slots = fold.slots;
        int accent = (mInk.accent & 0x00FFFFFF)
            | (Math.round(255f * (1f - (1f - SLIDE_ACCENT_KEEP) * mEnter)) << 24);
        for (int i = 0; i < slots.size(); i++) {
            float scale = lifted ? mScales[i] : 1f;
            if (scale <= 0.01f) continue;
            float cx = lifted ? mCenters[i] : layout.restCenterX(i);
            float size = glyph * scale;
            PageBarFold.Slot slot = slots.get(i);
            if (slot.folded()) drawPlates(canvas, cx, cy, size);
            mPainter.draw(canvas, slot.kind, slot.number, cx - size / 2f, cy - size / 2f, size,
                slot.current ? accent : mInk.ink);
        }
    }

    /** The two offset back plates of a folded slot, as the mock draws them: 22 units is the glyph's size. */
    private void drawPlates(Canvas canvas, float cx, float cy, float size) {
        float unit = size / PageBarLayout.GLYPH_DP;
        float w = 22f * unit;
        float h = 19f * unit;
        float r = 5f * unit;
        mFill.setColor((mInk.ink & 0x00FFFFFF) | (PLATE_BACK_ALPHA << 24));
        mRect.set(cx - 5f * unit, cy - 13.5f * unit, cx - 5f * unit + w, cy - 13.5f * unit + h);
        canvas.drawRoundRect(mRect, r, r, mFill);
        mFill.setColor((mInk.ink & 0x00FFFFFF) | (PLATE_FRONT_ALPHA << 24));
        mRect.set(cx - 8f * unit, cy - 11.5f * unit, cx - 8f * unit + w, cy - 11.5f * unit + h);
        canvas.drawRoundRect(mRect, r, r, mFill);
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

    /** Puts the bar away: the touch, the slide and the bubble, all given back. */
    void release() {
        cancelStream();
        mEnterAnim.cancel();
        mEnter = 0f;
        hideBubble();
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
        if (mLayout != null) mFocusFloat = mLayout.focusAt(slide.x());
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
        ensureBubble();
        PageBarBubbleView bubble = mBubble;
        if (bubble != null) bubble.setVisibility(VISIBLE);
        animateEnter(1f);
    }

    private void endSlide() {
        if (!mSliding) return;
        mSliding = false;
        if (mEntry != null) mEntry.slidingChanged(false);
        animateEnter(0f);
    }

    private void animateEnter(float to) {
        mEnterAnim.cancel();
        if (mEntry == null || mEntry.isReducedMotion() || !isAttachedToWindow()) {
            mEnter = to;
            onEnterChanged();
            if (to <= 0f) hideBubble();
            return;
        }
        mEnterAnim.setFloatValues(mEnter, to);
        mEnterAnim.start();
    }

    /** Draw-time only: the bar and the bubble repaint; nothing lays out. */
    private void onEnterChanged() {
        invalidate();
        updateBubble();
    }

    private void ensureBubble() {
        if (mBubble != null || !(getParent() instanceof ViewGroup)) return;
        PageBarBubbleView bubble = new PageBarBubbleView(getContext(), mPainter);
        bubble.setVisibility(GONE);
        ((ViewGroup) getParent()).addView(bubble, new android.widget.FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, bubble.heightPx()));
        bubble.dress(mLook);
        mBubble = bubble;
    }

    private void hideBubble() {
        PageBarBubbleView bubble = mBubble;
        if (bubble == null || bubble.getVisibility() == GONE) return;
        bubble.release();
        bubble.setVisibility(GONE);
    }

    private void updateBubble() {
        PageBarBubbleView bubble = mBubble;
        PageBarLayout layout = mLayout;
        PageBarFold.Result fold = mFold;
        PageBarSlide slide = mSlide;
        PageBarModel model = mModel;
        if (bubble == null || layout == null || fold == null || slide == null || model == null
            || bubble.getVisibility() != VISIBLE) {
            return;
        }
        int slotIndex = Math.round(mFocusFloat);
        slotIndex = mSliding ? slide.focusSlot() : Math.max(0, Math.min(fold.slots.size() - 1, slotIndex));
        PageBarFold.Slot slot = fold.slots.get(slotIndex);
        int member = slot.folded() ? layout.subIndexAt(slide.x(), slotIndex, slot.count) : 0;
        PageBarModel.Entry e = model.entries().get(slot.first + member);
        bubble.bind(e.label, e.kind, e.number, getLeft() + layout.bubbleCenterX(slotIndex),
            mInk.accent, mInk.ink);
        bubble.setTranslationY(bubble.topForDiscCenter(getTop() + layout.pillTopPx()
            + layout.bubbleCenterYFromPillTopPx()));
        bubble.setAlpha(mEnter);
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
        float y = layout.pillTopPx() + layout.pillHeightPx() / 2f;
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
