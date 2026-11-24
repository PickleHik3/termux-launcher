package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.page.PageKind;
import com.termux.app.ReducedMotion;
import com.termux.app.wall.pageswitch.MarksBubble;
import com.termux.app.wall.pageswitch.MarksLine;
import com.termux.app.wall.pageswitch.MarksLook;
import com.termux.app.wall.pageswitch.MarksSlide;
import com.termux.app.wall.pageswitch.PageBarFold;
import com.termux.app.wall.pageswitch.PageBarLayout;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageBarSlide;
import com.termux.app.wall.pageswitch.PageSwitchHaptics;
import com.termux.app.wall.pageswitch.PageSwitchTargets;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Marks line: the status bar's pages as a line of marks, one per entry of the wall in the
 * wall's order, where the window chips stand under the Glyphs look. A row ({@link #setVertical}
 * false) draws along x from the start; a column on a side edge draws the same line along y. The
 * current page is a disc with its number or sign cut out, attention a small burst; entries that do
 * not fit the line's length fold as the page bar's do.
 *
 * <p>Later tickets (sliding along the line, the thin in-pane row) build on {@link #line()}: its
 * numbers are {@link MarksLine}'s (cells, centres, the slot under a position) and the shapes are
 * {@link MarksLook}'s.
 *
 * <p>{@link #setPages} is the one input: it compares and rebuilds the line only when the entries,
 * the attention or the length changed. Drawing allocates nothing and requests no layout.
 */
public final class MarksLineView extends View {

    /** What the activity takes a tap on a mark to: the page switcher's own navigation. */
    @Nullable private PageSwitchTargets.Navigator mNavigator;

    private final MarksLook mLook = new MarksLook();
    private final int mTouchSlop;
    @Nullable private PageBarModel mModel;
    @Nullable private boolean[] mAttention;
    @Nullable private MarksLine mLine;
    private boolean mVertical;
    private int mNeutral;
    private int mError;
    @Nullable private ChromeInk mChromeInk;
    @NonNull private MarksLineInk mInk;
    private int mInkGeneration = -1;
    @NonNull private final Rect mBandRect = new Rect();
    @NonNull private final Rect mMeasuredRect = new Rect();
    private boolean mInkMeasured;
    /** The authored inks are the ones held, for this chrome. */
    private boolean mAuthored;
    private float mDownX;
    private float mDownY;
    private boolean mDown;

    // ---- slide state --------------------------------------------------------------------------
    @Nullable private MarksSlide mSlide;
    private boolean mSliding;
    @Nullable private PageSwitchHaptics mHaptics;
    @Nullable private BooleanSupplier mReducedMotion;
    @Nullable private MarksBubbleLayer mBubble;
    @Nullable private ViewGroup mBubbleHost;
    @Nullable private ValueAnimator mAppearAnimator;
    private static final long APPEAR_MS = 120L;
    /** 0 at rest, 1 once the slide's lift has come in; the envelope's {@code enter}. */
    private float mAppear;
    private final int[] mLoc = new int[2];
    private final int[] mHostLoc = new int[2];
    private final MarksBubble.Box mBox = new MarksBubble.Box();
    @NonNull private String[] mSlotLabels = new String[0];
    @NonNull private final Access mAccess;

    public MarksLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mAccess = new Access(this);
        ViewCompat.setAccessibilityDelegate(this, mAccess);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mNeutral = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mError = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(context, R.color.termux_error));
        mInk = MarksLineInk.authored(mNeutral, mError);
    }

    public void setNavigator(@Nullable PageSwitchTargets.Navigator navigator) {
        mNavigator = navigator;
    }

    /**
     * What the slide asks of the app: whether haptics are on (the tick on each change of mark and the
     * click on choosing, as the page bar's) and whether the phone plays no animations (the lift then
     * comes in at once). Without it the slide is silent and reads the system's animator scale.
     */
    public void setSlideSupport(@Nullable BooleanSupplier hapticsEnabled,
                                @Nullable BooleanSupplier reducedMotion) {
        mHaptics = hapticsEnabled == null ? null
            : new PageSwitchHaptics(getContext(), hapticsEnabled);
        mReducedMotion = reducedMotion;
    }

    /** The largest a mark is drawn, in px; 0 for no cap beyond the line's own (the strip). */
    private float mMarkCapPx;

    /**
     * Caps the marks at {@code capPx} (the thin row's {@code PaneRowPolicy.MARK_DP}); 0 lifts the
     * cap. Redraws only on a change.
     */
    public void setMarkCapPx(float capPx) {
        float cap = Math.max(0f, capPx);
        if (cap == mMarkCapPx) return;
        mMarkCapPx = cap;
        invalidate();
    }

    /** Along y for a column on a side edge, along x for the row. */
    public void setVertical(boolean vertical) {
        if (mVertical == vertical) return;
        mVertical = vertical;
        rebuild();
    }

    /**
     * Who can say what the line stands on. Null keeps the authored roles. The band is read, never
     * asked: the status ink pass owns the question ({@code ChromeInk#resolution}), as the window
     * column reads it.
     */
    public void setChromeInk(@Nullable ChromeInk ink) {
        if (mChromeInk == ink) return;
        mChromeInk = ink;
        mInkMeasured = false;
        mAuthored = false;
        mInkGeneration = -1;
        mMeasuredRect.setEmpty();
        syncInk();
        invalidate();
    }

    /**
     * Takes the wall's entries and which of them want the user ({@code attention}, per entry; null
     * for none). Rebuilds the line only when something drawn differs from what is held.
     */
    public void setPages(@Nullable PageBarModel model, @Nullable boolean[] attention) {
        if (sameEntries(mModel, model) && Arrays.equals(mAttention, attention)) {
            mModel = model;
            return;
        }
        mModel = model;
        mAttention = attention == null ? null : attention.clone();
        rebuild();
    }

    /** The layout in use, or null while there are no entries or no length; for later tickets and tests. */
    @Nullable
    public MarksLine line() {
        return mLine;
    }

    /** Re-reads the band after a pass that could have moved it; compares before it repaints. */
    public void refreshInk() {
        if (syncInk()) invalidate();
    }

    // ---- build ------------------------------------------------------------------------------

    private float axisLength() {
        return mVertical ? getHeight() : getWidth();
    }

    private void rebuild() {
        PageBarModel model = mModel;
        float length = axisLength();
        if (model == null || model.entries().isEmpty() || length <= 0f) {
            mLine = null;
        } else {
            mLine = new MarksLine(model.entries(), mAttention,
                getResources().getDisplayMetrics().density, length);
        }
        rebuildLabels();
        mAccess.invalidateRoot();
        invalidate();
    }

    private void rebuildLabels() {
        MarksLine line = mLine;
        int n = line == null ? 0 : line.slotCount();
        if (mSlotLabels.length != n) mSlotLabels = new String[n];
        for (int i = 0; i < n; i++) {
            PageBarFold.Slot slot = line.slots().get(i);
            mSlotLabels[i] = slot.folded()
                ? getResources().getQuantityString(R.plurals.page_bar_folded, slot.count,
                    getResources().getString(kindName(slot.kind)), slot.count)
                : line.entry(i, 0).label;
        }
    }

    private static int kindName(@NonNull PageKind kind) {
        switch (kind) {
            case HOME: return R.string.page_switch_home;
            case TERMINAL: return R.string.page_switch_terminal;
            case ASSISTANT: return R.string.page_switch_assistant;
            default: return R.string.page_switch_display;
        }
    }

    /** The names the screen reader speaks, one per slot in order; for tests. */
    @NonNull
    String[] slotLabels() {
        return mSlotLabels.clone();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private static boolean sameEntries(@Nullable PageBarModel a, @Nullable PageBarModel b) {
        if (a == b) return true;
        if (a == null || b == null || a.entries().size() != b.entries().size()) return false;
        for (int i = 0; i < a.entries().size(); i++) {
            PageBarModel.Entry x = a.entries().get(i);
            PageBarModel.Entry y = b.entries().get(i);
            if (x.kind != y.kind || x.number != y.number || x.current != y.current
                || !x.label.equals(y.label)
                || x.returnTo != y.returnTo || x.target.action != y.target.action
                || x.target.pageId != y.target.pageId || x.target.index != y.target.index) {
                return false;
            }
        }
        return true;
    }

    // ---- ink --------------------------------------------------------------------------------

    /**
     * Solves the inks on the band as drawn, when the measurement could have moved (a new sample
     * generation, the band moved, or the palette changed). Int and rect comparisons otherwise.
     *
     * @return whether the inks changed
     */
    private boolean syncInk() {
        ChromeInk chrome = mChromeInk;
        OnGlass.Resolution resolved = chrome == null ? null
            : chrome.resolution(GlassBackdropCache.Band.STATUS_BAR);
        if (resolved == null || !chrome.bandRect(GlassBackdropCache.Band.STATUS_BAR, mBandRect)) {
            // Nothing can say what the line stands on yet: the roles themselves, made once.
            if (mAuthored && !mInkMeasured) return false;
            mInkMeasured = false;
            mAuthored = true;
            MarksLineInk authored = MarksLineInk.authored(mNeutral, mError);
            boolean changed = !sameInk(authored, mInk);
            mInk = authored;
            return changed;
        }
        if (mInkMeasured && chrome.backdrops().generation() == mInkGeneration
            && mMeasuredRect.equals(mBandRect)) {
            return false;
        }
        boolean pale = chrome.polarity() == ChromeInk.Polarity.PALE_INK;
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        MarksLineInk solved = MarksLineInk.onBand(resolved.surface, pale,
            WindowChipInk.neutralSeed(mNeutral, surfaceBase, pale), mError);
        boolean changed = !sameInk(solved, mInk);
        mInk = solved;
        mInkMeasured = true;
        mAuthored = false;
        mInkGeneration = chrome.backdrops().generation();
        mMeasuredRect.set(mBandRect);
        return changed;
    }

    private static boolean sameInk(MarksLineInk a, MarksLineInk b) {
        return a.current == b.current && a.other == b.other && a.attention == b.attention;
    }

    // ---- draw -------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        syncInk();
        MarksLine line = mLine;
        if (line == null) return;
        boolean rtl = !mVertical && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float cross = (mVertical ? getWidth() : getHeight()) / 2f;
        float size = Math.min(line.markSizePx(), cross * 2f);
        if (mMarkCapPx > 0f) size = Math.min(size, mMarkCapPx);
        List<PageBarFold.Slot> slots = line.slots();
        MarksSlide slide = mSlide;
        boolean sliding = mSliding && slide != null;
        float focus = sliding ? line.focusAt(slide.pos()) : 0f;
        for (int i = 0, n = slots.size(); i < n; i++) {
            PageBarFold.Slot slot = slots.get(i);
            // The mark under the finger has lifted into the bubble; its neighbours grow toward it.
            if (sliding && i == slide.focusSlot()) continue;
            float markSize = size;
            if (sliding) {
                markSize = Math.min(cross * 2f, size * (1f + (PageBarLayout.RAISED_SCALE - 1f)
                    * PageBarLayout.envelope(i, focus, mAppear)));
            }
            float along = line.centreAt(i);
            float cx = mVertical ? cross : rtl ? getWidth() - along : along;
            float cy = mVertical ? along : cross;
            int ink = slot.current ? mInk.current : mInk.other;
            if (slot.folded()) {
                mLook.drawStack(canvas, slot.kind, slot.count, slot.current, 0f, cx, cy, markSize,
                    ink, mInk.other);
            } else {
                mLook.draw(canvas, slot.kind, slot.number, slot.current, 0f, cx, cy, markSize, ink);
            }
            if (line.attention(i)) mLook.drawBurst(canvas, cx, cy, markSize, mInk.attention);
        }
    }

    // ---- touch ------------------------------------------------------------------------------

    private float along(float x, float y) {
        if (mVertical) return y;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - x : x;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        MarksLine line = mLine;
        if (line == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float pos = along(event.getX(), event.getY());
                mDown = line.slotAt(pos) >= 0;
                mDownX = event.getX();
                mDownY = event.getY();
                if (mDown) {
                    mSlide = new MarksSlide(line, mTouchSlop);
                    mSlide.down(pos);
                }
                return mDown;
            }
            case MotionEvent.ACTION_MOVE: {
                MarksSlide slide = mSlide;
                if (!mDown || slide == null) return false;
                float across = Math.abs(mVertical ? event.getX() - mDownX : event.getY() - mDownY);
                if (!mSliding && across > mTouchSlop) {
                    // Off the line across its axis before sliding: not a tap, not a slide.
                    endTouch();
                    return false;
                }
                slide.move(along(event.getX(), event.getY()));
                if (slide.state() == PageBarSlide.State.SLIDING) {
                    if (!mSliding) beginSlide();
                    if (slide.focusChanged() && mHaptics != null) mHaptics.crossed();
                    updateBubble();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                MarksSlide slide = mSlide;
                boolean wasDown = mDown;
                boolean slid = mSliding;
                float pos = along(event.getX(), event.getY());
                PageBarSlide.Result result = slide == null ? PageBarSlide.Result.NONE
                    : slide.up(pos);
                int focusSlot = slide == null ? -1 : slide.focusSlot();
                int focusMember = slide == null ? 0 : slide.focusMember();
                endTouch();
                if (!wasDown) return false;
                if (result == PageBarSlide.Result.TAP) {
                    tapAt(pos);
                } else if (result == PageBarSlide.Result.COMMIT && slid) {
                    commit(line, focusSlot, focusMember);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                endTouch();
                return false;
            default:
                return mDown;
        }
    }

    /** The slide ended on another mark: its page, with the click. The current page does nothing. */
    private void commit(@NonNull MarksLine line, int slot, int member) {
        PageSwitchTargets.Navigator nav = mNavigator;
        if (nav == null) return;
        PageBarModel.Target target = line.entry(slot, member).target;
        if (target.action == PageBarModel.Action.CLOSE) return;
        if (mHaptics != null) mHaptics.committed();
        PageSwitchTargets.carryOut(target, nav);
    }

    /** Puts the touch away: the slide, the bubble and the lift. */
    private void endTouch() {
        MarksSlide slide = mSlide;
        if (slide != null) slide.cancel();
        mDown = false;
        mSlide = null;
        if (mSliding) endSlide();
    }

    // ---- slide: lift and bubble ---------------------------------------------------------------

    /** The slide has begun: the line takes the touch, the lift comes in and the bubble goes up. */
    private void beginSlide() {
        mSliding = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        showBubble();
        BooleanSupplier reduced = mReducedMotion;
        boolean still = reduced != null ? reduced.getAsBoolean() : ReducedMotion.isEnabled(getContext());
        if (still || !isAttachedToWindow()) {
            setAppear(1f);
            return;
        }
        ValueAnimator a = mAppearAnimator;
        if (a == null) {
            a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(APPEAR_MS);
            a.addUpdateListener(v -> setAppear((Float) v.getAnimatedValue()));
            mAppearAnimator = a;
        }
        setAppear(0f);
        a.start();
    }

    private void setAppear(float appear) {
        mAppear = appear;
        MarksBubbleLayer bubble = mBubble;
        if (bubble != null) {
            bubble.setAppear(appear);
            bubble.invalidateSelf();
        }
        invalidate();
    }

    private void endSlide() {
        mSliding = false;
        ValueAnimator a = mAppearAnimator;
        if (a != null) a.cancel();
        mAppear = 0f;
        hideBubble();
        invalidate();
    }

    /**
     * Lays the bubble on the window's notices layer, whose frame is the whole window, so it stands
     * clear of the strip; once per slide. With no such layer the slide still works, without the bubble.
     */
    private void showBubble() {
        ViewGroup host = WindowLayers.host(id -> getRootView().findViewById(id), WindowLayer.NOTICES);
        if (host == null) return;
        MarksBubbleLayer bubble = mBubble;
        if (bubble == null) {
            bubble = new MarksBubbleLayer(getContext());
            mBubble = bubble;
        }
        int surface = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        bubble.setInks(surface, mNeutral);
        bubble.setBounds(0, 0, host.getWidth(), host.getHeight());
        bubble.setAppear(mAppear);
        host.getOverlay().add(bubble);
        mBubbleHost = host;
        getLocationInWindow(mLoc);
        host.getLocationInWindow(mHostLoc);
    }

    private void hideBubble() {
        ViewGroup host = mBubbleHost;
        MarksBubbleLayer bubble = mBubble;
        if (host != null && bubble != null) host.getOverlay().remove(bubble);
        mBubbleHost = null;
    }

    /** Names and places the bubble for the finger now; arithmetic only. */
    private void updateBubble() {
        MarksBubbleLayer bubble = mBubble;
        ViewGroup host = mBubbleHost;
        MarksSlide slide = mSlide;
        MarksLine line = mLine;
        if (bubble == null || host == null || slide == null || line == null) return;
        int slot = slide.focusSlot();
        if (slot < 0 || slot >= line.slotCount()) return;
        PageBarModel.Entry entry = line.entry(slot, slide.focusMember());
        bubble.setPage(entry.kind, entry.number, entry.label, host.getWidth());
        float offX = mLoc[0] - mHostLoc[0];
        float offY = mLoc[1] - mHostLoc[1];
        float along = Math.max(line.centreAt(0),
            Math.min(line.centreAt(line.slotCount() - 1), slide.pos()));
        boolean rtl = !mVertical && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float hostAlong = mVertical ? offY + along : offX + (rtl ? getWidth() - along : along);
        MarksBubble.Side side = MarksBubble.sideFor(mVertical, offX + getWidth() / 2f,
            offY + getHeight() / 2f, host.getWidth(), host.getHeight());
        MarksBubble.place(side, offX, offY, offX + getWidth(), offY + getHeight(), hostAlong,
            bubble.width(), bubble.height(), bubble.gap(), bubble.margin(), host.getWidth(),
            host.getHeight(), mBox);
        bubble.moveTo(mBox.left, mBox.top);
        bubble.invalidateSelf();
    }

    @Override
    protected void onDetachedFromWindow() {
        endTouch();
        super.onDetachedFromWindow();
    }

    // ---- accessibility ------------------------------------------------------------------------

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
            MarksLine line = mLine;
            if (line == null) return INVALID_ID;
            int slot = line.slotAt(along(x, y));
            return slot < 0 ? INVALID_ID : slot;
        }

        @Override protected void getVisibleVirtualViews(@NonNull List<Integer> ids) {
            for (int i = 0; i < mSlotLabels.length; i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            MarksLine line = mLine;
            if (line == null || id < 0 || id >= mSlotLabels.length) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            node.setClassName("android.widget.Button");
            node.setContentDescription(mSlotLabels[id]);
            boolean current = line.slots().get(id).current;
            node.setSelected(current);
            // A tap on the current page does nothing, so it advertises no click.
            if (!current) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                node.setClickable(true);
            }
            int start = Math.round(line.pitchPx() * id);
            int end = Math.round(line.pitchPx() * (id + 1));
            if (mVertical) {
                node.setBoundsInParent(new Rect(0, start, getWidth(), end));
            } else if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                node.setBoundsInParent(new Rect(getWidth() - end, 0, getWidth() - start, getHeight()));
            } else {
                node.setBoundsInParent(new Rect(start, 0, end, getHeight()));
            }
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            return clickSlot(id);
        }
    }

    /** A tap on slot {@code id} as the touch does it, for a screen reader or a script. */
    boolean clickSlot(int id) {
        MarksLine line = mLine;
        PageSwitchTargets.Navigator nav = mNavigator;
        if (line == null || nav == null || id < 0 || id >= line.slotCount()) return false;
        PageBarModel.Target target = line.targetAt(line.centreAt(id));
        if (target == null) return false;
        PageSwitchTargets.carryOut(target, nav);
        return true;
    }

    /** A tap at {@code pos} along the line: to the page it lands on; the current page does nothing. */
    void tapAt(float pos) {
        MarksLine line = mLine;
        PageSwitchTargets.Navigator nav = mNavigator;
        if (line == null || nav == null) return;
        PageBarModel.Target target = line.targetAt(pos);
        if (target != null) PageSwitchTargets.carryOut(target, nav);
    }
}
