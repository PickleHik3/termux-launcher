package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.statusbar.WindowActivityRing;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One compact app-owned row for the current tmux-style window list. */
public final class TerminalWindowBar extends HorizontalScrollView {

    /**
     * Shared with the terminal surface so both pieces of the window switch settle together.
     * Deliberately unhurried: with the spring-shaped settle curve the pan spends its middle
     * moving fast and its ends easing, so the extra time reads as weight, not lag.
     */
    public static final long WINDOW_SWITCH_ANIMATION_DURATION_MS = 560L;

    public interface OnWindowSelectedListener {
        void onWindowSelected(int index);
    }

    public interface OnCreateWindowListener {
        void onCreateWindow();
    }

    /**
     * The × revealed on the selected chip was tapped. The bar has no idea what a window is — the
     * host closes the one this index stands for, and asks first if it has to.
     */
    public interface OnWindowCloseRequestedListener {
        void onWindowCloseRequested(int index);
    }

    /**
     * The chip strip has run out of scroll and the finger keeps going. The surplus distance is
     * streamed to the host so "scroll to the last chip, keep pulling, and the page beside the
     * terminal slides in" is one continuous gesture.
     */
    public interface OnEdgeOverswipeListener {
        /** @return true to take the stream; false leaves the strip's own scrolling alone */
        boolean onEdgeOverswipeBegin();
        /** @param dxPx surplus travel since the hand-over, positive to the right */
        void onEdgeOverswipe(float dxPx);
        /** @param velocityPxPerSec horizontal release velocity, positive to the right */
        void onEdgeOverswipeEnd(float velocityPxPerSec);
        void onEdgeOverswipeCancel();
    }

    /** Visual label plus a spoken label that does not expose Nerd Font private-use glyphs. */
    public static final class WindowItem {
        @NonNull public final String label;
        @NonNull public final String spokenLabel;
        /** Whether a shell in this window is producing output right now. */
        public final boolean busy;
        /**
         * Whether a shell in this window has rung its bell — a prompt, a permission request, an agent
         * handing its turn back. Shown as a bell after the label, the way Windows Terminal marks a
         * tab; independent of {@link #busy}, since a window can be both working and asking.
         */
        public final boolean attention;
        /**
         * Whether a command in this window finished a real stretch of work while the user was
         * elsewhere. Shown as a tick after the label until the window is visited; a bell outranks
         * it, since a window that is asking is not finished.
         */
        public final boolean done;
        /**
         * Whether that command failed, as its shell reported the status (OSC 133;D). The tick
         * becomes a cross in the attention colour, so a window the user was not watching says
         * whether the work succeeded and not only that it ended. False whenever no status was
         * reported: a shell without integration cannot tell success from failure, and guessing is
         * worse than the plain tick.
         */
        public final boolean doneFailed;
        /**
         * What the shell itself reports about how far along it is, when it does: 0-100 for a
         * percentage, {@link #NO_PERCENTAGE} when it is working without one (or reports nothing).
         * Only read while {@link #busy}.
         */
        public final int progress;
        /** Whether the reported progress is in its error state, so the ring can say so. */
        public final boolean progressError;
        /**
         * What an AI coding agent in this window is doing, rolled up over its panes, or null when
         * none of them is running one. Drawn as the status dot in front of the label; independent
         * of every other mark, since an agent can be working in one pane while another rings.
         */
        @Nullable public final AgentStatus.State agentState;
        /**
         * The app's own mark for a Display-place window: its desktop icon reduced to a
         * single-colour silhouette, which the chip tints like the process glyph. Null everywhere
         * else, and for a window whose {@code WM_CLASS} matches no installed app — the chip then
         * draws its generic glyph.
         */
        @Nullable public final Bitmap icon;

        public static final int NO_PERCENTAGE = -1;

        public WindowItem(@NonNull String label, @NonNull String spokenLabel) {
            this(label, spokenLabel, false, false);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy) {
            this(label, spokenLabel, busy, false);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention) {
            this(label, spokenLabel, busy, attention, NO_PERCENTAGE, false, false);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention, int progress, boolean progressError) {
            this(label, spokenLabel, busy, attention, progress, progressError, false);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention, int progress, boolean progressError, boolean done) {
            this(label, spokenLabel, busy, attention, progress, progressError, done, false);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention, int progress, boolean progressError, boolean done,
                          boolean doneFailed) {
            this(label, spokenLabel, busy, attention, progress, progressError, done, doneFailed,
                null);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention, int progress, boolean progressError, boolean done,
                          boolean doneFailed, @Nullable AgentStatus.State agentState) {
            this(label, spokenLabel, busy, attention, progress, progressError, done, doneFailed,
                agentState, null);
        }

        public WindowItem(@NonNull String label, @NonNull String spokenLabel, boolean busy,
                          boolean attention, int progress, boolean progressError, boolean done,
                          boolean doneFailed, @Nullable AgentStatus.State agentState,
                          @Nullable Bitmap icon) {
            this.label = label;
            this.spokenLabel = spokenLabel;
            this.busy = busy;
            this.attention = attention;
            this.progress = progress;
            this.progressError = progressError;
            this.done = done;
            this.doneFailed = doneFailed;
            this.agentState = agentState;
            this.icon = icon;
        }

        /**
         * A copy carrying the activity states. A copy method rather than more constructor arguments on
         * every factory, so itemFor / itemForResolved / truncateFile and their tests stay as they
         * are.
         */
        @NonNull
        public WindowItem withBusy(boolean busy) {
            return busy == this.busy ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed, agentState, icon);
        }

        @NonNull
        public WindowItem withAttention(boolean attention) {
            return attention == this.attention ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed, agentState, icon);
        }

        @NonNull
        public WindowItem withDone(boolean done) {
            return withDone(done, done && doneFailed);
        }

        /** @param failed the finished command's own verdict; only meaningful while {@code done}. */
        @NonNull
        public WindowItem withDone(boolean done, boolean failed) {
            return done == this.done && failed == this.doneFailed ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    failed, agentState, icon);
        }

        /** The shell's own progress report; {@link #NO_PERCENTAGE} for indeterminate. */
        @NonNull
        public WindowItem withProgress(int progress, boolean progressError) {
            return progress == this.progress && progressError == this.progressError ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed, agentState, icon);
        }

        /** The rolled-up agent reading for this window's panes; null removes the dot. */
        @NonNull
        public WindowItem withAgentState(@Nullable AgentStatus.State agentState) {
            return agentState == this.agentState ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed, agentState, icon);
        }

        /**
         * The app icon a Display window's chip wears; null draws the generic glyph. A copy rather
         * than another constructor argument, so the item can be built from the window list before
         * the icon has been resolved off the main thread and re-attached when it arrives.
         */
        @NonNull
        public WindowItem withIcon(@Nullable Bitmap icon) {
            return icon == this.icon ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed, agentState, icon);
        }

        /** Whether the two would draw the same marks. Labels are compared separately. */
        boolean sameActivity(@NonNull WindowItem other) {
            return busy == other.busy && attention == other.attention && done == other.done
                && doneFailed == other.doneFailed
                && progress == other.progress && progressError == other.progressError
                && agentState == other.agentState && icon == other.icon;
        }
    }

    /**
     * The one word a user reads for an agent's state, here and in the sessions browser. Product
     * copy: "Working", "Needs you", "Idle" — never the mechanism behind the reading.
     */
    @Nullable
    public static String agentStateWord(@NonNull Context context,
                                        @Nullable AgentStatus.State state) {
        if (state == null) return null;
        switch (state) {
            case WORKING: return context.getString(R.string.termux_agent_state_working);
            case BLOCKED: return context.getString(R.string.termux_agent_state_blocked);
            default: return context.getString(R.string.termux_agent_state_idle);
        }
    }

    /**
     * The × the selected chip grows on its trailing side, in dp, and the target a thumb gets for
     * it through the status row's touch delegate.
     */
    private static final float CLOSE_SEGMENT_DP = ChipWatermarkGeometry.CLOSE_SEGMENT_DP;
    private static final float CLOSE_TARGET_DP = 24f;

    private final SelectionStrip mTabs;
    @Nullable private OnWindowSelectedListener mSelectionListener;
    @Nullable private OnCreateWindowListener mCreateListener;
    @Nullable private OnWindowCloseRequestedListener mCloseListener;
    @Nullable private OnEdgeOverswipeListener mEdgeOverswipeListener;
    /** Which chip is offering its ×, and for how much longer. */
    private final ChipRevealPolicy mReveal = new ChipRevealPolicy();
    /**
     * The × itself. Built at the first reveal and kept from then on, hidden between reveals rather
     * than added and removed: a chip row that gains and loses a child on every tap cannot be
     * reused across a refresh, and a rebuilt row loses the selection slide.
     */
    @Nullable private AppCompatImageButton mCloseButton;
    @Nullable private Runnable mRevealTimeout;
    /** The × opening or closing its segment of the chip; null while it is at rest. */
    @Nullable private ValueAnimator mCloseRevealAnimator;
    /** Whether the status row's touch delegate is the one this bar put there. */
    private boolean mOwnsTouchDelegate;
    private final int mTouchSlop;
    private boolean mGestureHorizontal;
    private boolean mGestureRejected;
    private float mTouchDownX;
    private float mTouchDownY;
    private float mLastTouchX;
    /** Signed travel the strip could not spend on its own scroll, since the DOWN. */
    private float mOverswipePx;
    /** One-way latch: once the surplus is the host's, the strip stops scrolling for this stream. */
    private boolean mOverswipeOwned;
    /**
     * Where the strip stood at the DOWN. A finger that scrolled the chips at all keeps them for
     * the rest of its stream: running into the last window is not a wish to change place. Only a
     * strip with nothing to scroll, or one pulled past the edge it already rests at, hands the
     * finger to the wall.
     */
    private int mScrollXAtDown;
    private boolean mStripScrolled;
    /** The DOWN time of the stream being tracked, so a DOWN seen twice is set up once. */
    private long mStreamDownTime = -1L;
    /**
     * The host took the wall away mid-overswipe. The rest of this stream belongs to nobody: not
     * streamed to the host, not spent on the chips either — a finger that was dragging the wall
     * must not suddenly scroll the strip under itself.
     */
    private boolean mOverswipeInterrupted;
    @Nullable private android.view.VelocityTracker mOverswipeVelocity;
    private int mSelectedIndex = -1;
    @NonNull private List<WindowItem> mItems = new ArrayList<>();
    private Typeface mTerminalTypeface = Typeface.MONOSPACE;
    /** The terminal's symbol_map ranges, so a tab label's icon is drawn by the face that has it. */
    @NonNull private TerminalRenderer.SymbolMap[] mSymbolMaps = new TerminalRenderer.SymbolMap[0];
    private boolean mCapsuleSurface;
    private float mStatusBarRadiusPx;
    private int mSelectedTextColor;
    private int mUnselectedTextColor;
    private int mUnselectedFillColor;
    private int mUnselectedStrokeColor;
    private int mSelectedFillColor;
    private int mSelectedStrokeColor;
    private int mGlyphColor;
    private int mUnselectedHaloColor;
    private int mSelectedHaloColor;
    private int mGroundColor;
    private int mBusyColor;
    private int mAttentionColor;
    @Nullable private Integer mPlaceAccent;
    /** Whether the strip ends with the plus that opens a new window. */
    private boolean mCreateButtonShown = true;
    @Nullable private ValueAnimator mSelectionAnimator;
    @Nullable private ValueAnimator mBusyAnimator;
    /**
     * Window visibility as last reported, rather than read from getWindowVisibility(): the framework
     * dispatches this during attach, and reading it keeps the animator honest without depending on
     * a ViewRootImpl the view may not have yet.
     */
    private boolean mWindowVisible = true;
    private boolean mAttached;

    public TerminalWindowBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        setClipToPadding(true);
        setClipChildren(true);
        setOverScrollMode(OVER_SCROLL_NEVER);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // The parent already supplies the intended gap after the session indicator. A second
        // leading inset here made that gap look like trailing padding owned by the session chip.
        setPaddingRelative(0, dp(2), dp(5), dp(2));
        mTabs = new SelectionStrip(context);
        mTabs.setGravity(Gravity.CENTER_VERTICAL);
        mTabs.setOrientation(LinearLayout.HORIZONTAL);
        mTabs.setClipChildren(true);
        mTabs.setClipToPadding(true);
        addView(mTabs, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        reloadTerminalTypeface();
        updatePalette();
    }

    public void setOnWindowSelectedListener(@Nullable OnWindowSelectedListener listener) {
        mSelectionListener = listener;
    }

    public void setOnCreateWindowListener(@Nullable OnCreateWindowListener listener) {
        mCreateListener = listener;
    }

    public void setOnWindowCloseRequestedListener(@Nullable OnWindowCloseRequestedListener listener) {
        mCloseListener = listener;
    }

    /** Whether the strip offers the plus; a place with nothing to add leaves it out. */
    public void setCreateButtonShown(boolean shown) {
        if (mCreateButtonShown == shown) return;
        mCreateButtonShown = shown;
        for (int i = mTabs.getChildCount() - 1; i >= 0; i--) {
            View child = mTabs.getChildAt(i);
            if (child != mCloseButton && child instanceof AppCompatImageButton) mTabs.removeViewAt(i);
        }
        if (shown) addCreateButton(mItems.isEmpty());
    }

    public void setOnEdgeOverswipeListener(@Nullable OnEdgeOverswipeListener listener) {
        mEdgeOverswipeListener = listener;
    }

    /** The wall moved on without this finger; the overswipe ends here, with no end or cancel. */
    public void cancelOverswipe() {
        if (!mOverswipeOwned) return;
        mOverswipeOwned = false;
        mOverswipeInterrupted = true;
        mOverswipePx = 0f;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // A finger that lands on a pill gives the pill its DOWN; the strip only meets the stream
        // when it intercepts a sideways move. Its bookkeeping has to start at the DOWN either way.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) beginStream(event);
        return super.onInterceptTouchEvent(event);
    }

    /** Reset everything a finger's stream carries; once per DOWN, whichever path sees it first. */
    private void beginStream(MotionEvent event) {
        if (mStreamDownTime == event.getDownTime()) return;
        mStreamDownTime = event.getDownTime();
        mTouchDownX = mLastTouchX = event.getX();
        mTouchDownY = event.getY();
        mOverswipePx = 0f;
        mGestureHorizontal = false;
        mGestureRejected = false;
        mOverswipeOwned = false;
        mOverswipeInterrupted = false;
        mScrollXAtDown = getScrollX();
        mStripScrolled = false;
        mReveal.onTouchDown();
        // A finger that lands anywhere but on the chip offering its × puts it away — including the
        // one that is on its way to the plus, or to the bar's empty end. The × itself lives inside
        // the chip's own bounds, so the tap that closes a window survives this.
        if (!isInsideRevealedChip(event)) {
            mReveal.onTouchElsewhere();
            applyReveal();
        }
        if (mOverswipeVelocity == null) {
            mOverswipeVelocity = android.view.VelocityTracker.obtain();
        }
        mOverswipeVelocity.clear();
        mOverswipeVelocity.addMovement(event);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginStream(event);
            return super.onTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (mOverswipeVelocity != null) mOverswipeVelocity.addMovement(event);
            float dx = event.getX() - mLastTouchX;
            float totalX = event.getX() - mTouchDownX;
            float totalY = event.getY() - mTouchDownY;
            if (!mGestureHorizontal && !mGestureRejected) {
                if (Math.abs(totalY) > mTouchSlop && Math.abs(totalY) >= Math.abs(totalX)) {
                    mGestureRejected = true;
                    // The status bar's pull. Like a chip scroll it is a drag, not a tap, so it
                    // neither reveals a × nor leaves one behind when the finger lifts.
                    mReveal.onScrollStarted();
                    applyReveal();
                } else if (Math.abs(totalX) > mTouchSlop
                    && Math.abs(totalX) > Math.abs(totalY) * 1.2f) {
                    mGestureHorizontal = true;
                    mReveal.onScrollStarted();
                    applyReveal();
                }
            }
            mLastTouchX = event.getX();
            if (mOverswipeInterrupted) return true;
            if (mOverswipeOwned) {
                // The surplus is the host's for the rest of this stream: the chips hold still
                // rather than scrolling back under a finger that is now dragging the wall.
                mOverswipePx += dx;
                if (mEdgeOverswipeListener != null) mEdgeOverswipeListener.onEdgeOverswipe(mOverswipePx);
                return true;
            }
            int before = getScrollX();
            boolean handled = super.onTouchEvent(event);
            if (getScrollX() != mScrollXAtDown) mStripScrolled = true;
            // Signed: dragging left scrolls right, so what the strip spent cancels the travel out.
            float surplus = dx + (getScrollX() - before);
            if (mGestureHorizontal && !mGestureRejected && !mStripScrolled
                && Math.abs(surplus) > 0f) {
                mOverswipePx += surplus;
                if (Math.abs(mOverswipePx) > mTouchSlop && mEdgeOverswipeListener != null
                    && mEdgeOverswipeListener.onEdgeOverswipeBegin()) {
                    mOverswipeOwned = true;
                    // The slop that proved the intent is not travel, but whatever the finger moved
                    // beyond it is: a coarse stream can cover much of the bar in its first move,
                    // and starting the host from rest threw that distance away.
                    mOverswipePx -= Math.copySign(mTouchSlop, mOverswipePx);
                    mEdgeOverswipeListener.onEdgeOverswipe(mOverswipePx);
                }
            }
            return handled;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean owned = mOverswipeOwned;
            float velocity = 0f;
            if (mOverswipeVelocity != null) {
                mOverswipeVelocity.computeCurrentVelocity(1000);
                velocity = mOverswipeVelocity.getXVelocity();
                mOverswipeVelocity.recycle();
                mOverswipeVelocity = null;
            }
            boolean interrupted = mOverswipeInterrupted;
            boolean handled = !interrupted && super.onTouchEvent(event);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            mStreamDownTime = -1L;
            mOverswipePx = 0f;
            mGestureHorizontal = false;
            mGestureRejected = false;
            mOverswipeOwned = false;
            mOverswipeInterrupted = false;
            if (interrupted) return true;
            if (owned && mEdgeOverswipeListener != null) {
                if (action == MotionEvent.ACTION_UP) {
                    mEdgeOverswipeListener.onEdgeOverswipeEnd(velocity);
                } else {
                    mEdgeOverswipeListener.onEdgeOverswipeCancel();
                }
                return true;
            }
            return handled;
        }
        return super.onTouchEvent(event);
    }

    /**
     * The pills' shape. The caller resolves it — the status row's own chip-radius knob, falling back
     * to the bar's shape while that knob is untouched — so a Docked bar can carry rounded pills and
     * the sessions indicator beside them can never disagree about the number.
     */
    public void setSurfaceStyle(boolean capsule, float statusBarRadiusPx) {
        float radius = Math.max(0f, statusBarRadiusPx);
        if (mCapsuleSurface == capsule && mStatusBarRadiusPx == radius) return;
        mCapsuleSurface = capsule;
        mStatusBarRadiusPx = radius;
        updatePalette();
        applyTabSurfaceStyle();
    }

    public void setWindows(@NonNull List<WindowItem> items, int selectedIndex) {
        boolean typefaceChanged = reloadTerminalTypeface();
        // sameActivity has to be part of the guard: an activity-only flip changes neither the
        // labels nor the selection, so without it the new state would be silently dropped here.
        if (!typefaceChanged && selectedIndex == mSelectedIndex && sameItems(mItems, items)
            && sameActivity(mItems, items)) return;
        int previousSelected = mSelectedIndex;
        // A × belongs to one window of one list. A different list, or the same list with the
        // selection somewhere else, is no longer the row it was revealed on.
        if (!sameItems(mItems, items)) mReveal.onItemsChanged();
        else if (selectedIndex != mSelectedIndex) mReveal.onSelectionChanged();
        // sameItems deliberately still compares labels only, so starting a command keeps
        // canReuseTabs true: re-inflating the pill row would also kill the selection slide.
        boolean canReuseTabs = !typefaceChanged && sameItems(mItems, items)
            && mTabs.getChildCount()
                == items.size() + (mCreateButtonShown ? 1 : 0) + closeButtonChildCount();
        mSelectedIndex = selectedIndex;
        mItems = new ArrayList<>(items);
        updatePalette();
        if (canReuseTabs) {
            applyActivityStates(false);
            applyTabContentDescriptions();
            if (previousSelected >= 0 && previousSelected != selectedIndex) {
                animateSelectionSlide(previousSelected, selectedIndex);
            } else {
                cancelSelectionAnimation();
                mTabs.snapSelection(selectedIndex);
                applyStableTabSelection();
            }
            applyReveal();
            scrollSelectedIntoView(selectedIndex);
            return;
        }

        cancelSelectionAnimation();
        mTabs.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            WindowItem item = items.get(i);
            boolean selected = i == selectedIndex;
            TextView tab = createTab(item, selected);
            tab.setOnClickListener(v -> onChipTapped(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            if (i > 0) params.setMarginStart(dp(3));
            mTabs.addView(tab, params);
        }
        if (mCreateButtonShown) addCreateButton(items.isEmpty());
        mTabs.setWindowCount(items.size());
        updateBusyAnimator();
        applyTabContentDescriptions();
        mTabs.snapSelection(selectedIndex);
        applyStableTabSelection();
        applyReveal();
        scrollSelectedIntoView(selectedIndex);
    }

    /**
     * A chip was tapped. The first tap on any chip selects it, as it always has; a tap on the chip
     * that is already selected asks for its × instead, and a second one takes it back.
     */
    private void onChipTapped(int index) {
        boolean spentOnTheClose =
            mReveal.onChipTap(index, mSelectedIndex, SystemClock.uptimeMillis());
        applyReveal();
        if (spentOnTheClose) return;
        if (mSelectionListener != null) mSelectionListener.onWindowSelected(index);
    }

    /** Whether the × is currently a child of the strip, revealed or waiting hidden. */
    private int closeButtonChildCount() {
        return mCloseButton != null && mCloseButton.getParent() == mTabs ? 1 : 0;
    }

    /** Whether {@code event} landed on the chip that is offering its ×, the × included. */
    private boolean isInsideRevealedChip(@NonNull MotionEvent event) {
        int index = mReveal.revealedIndex();
        if (index < 0 || index >= mItems.size() || index >= mTabs.getChildCount()) return false;
        float x = event.getX() + getScrollX() - mTabs.getLeft();
        return mTabs.isInsideChipWithClose(index, x);
    }

    /** Put the × where the policy says it belongs, and arm the timer that takes it away again. */
    private void applyReveal() {
        if (mRevealTimeout != null) {
            removeCallbacks(mRevealTimeout);
            mRevealTimeout = null;
        }
        int index = mReveal.revealedIndex();
        if (index < 0 || index >= mItems.size()) {
            if (mTabs.closeTarget() != ChipRevealPolicy.NONE
                || (mCloseButton != null && mCloseButton.getVisibility() != GONE)) {
                cancelCloseReveal();
                mTabs.setCloseTarget(ChipRevealPolicy.NONE);
                mTabs.setCloseRevealFraction(0f);
                if (mCloseButton != null) mCloseButton.setVisibility(GONE);
                mTabs.requestLayout();
            }
            updateCloseTouchDelegate();
            return;
        }
        AppCompatImageButton close = ensureCloseButton();
        close.setContentDescription(getResources().getString(
            R.string.termux_window_tab_close_content_description, mItems.get(index).spokenLabel));
        boolean fresh = mTabs.closeTarget() != index || close.getVisibility() != VISIBLE;
        close.setVisibility(VISIBLE);
        mTabs.setCloseTarget(index);
        mTabs.requestLayout();
        if (fresh) startCloseReveal();
        mRevealTimeout = () -> {
            mRevealTimeout = null;
            if (mReveal.onTimeout(SystemClock.uptimeMillis())) applyReveal();
        };
        postDelayed(mRevealTimeout, Math.max(0L, mReveal.hideAt() - SystemClock.uptimeMillis()));
    }

    /**
     * The × opens as a segment of the chip itself rather than a lid over its trailing end: the chip
     * grows, its neighbours are pushed along, and the selection highlight follows the wider shape.
     * Only the segment's own pixels move — {@link SelectionStrip} measures the full width from the
     * moment the × is asked for and offsets the row by hand — so nothing above the bar is laid out
     * again on any of the 180 ms.
     */
    private void startCloseReveal() {
        cancelCloseReveal();
        if (!mAttached || !mWindowVisible) {
            mTabs.setCloseRevealFraction(1f);
            updateCloseTouchDelegate();
            return;
        }
        mTabs.setCloseRevealFraction(0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ChipWatermarkGeometry.CLOSE_REVEAL_MS);
        animator.setInterpolator(settleInterpolator());
        // The delegate is set once the segment has arrived rather than per frame: a thumb cannot
        // reach for a target that is still opening, and a Rect a frame is allocation for nothing.
        animator.addUpdateListener(
            value -> mTabs.setCloseRevealFraction((Float) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (mCloseRevealAnimator == animation) mCloseRevealAnimator = null;
                updateCloseTouchDelegate();
            }
        });
        mCloseRevealAnimator = animator;
        animator.start();
    }

    private void cancelCloseReveal() {
        if (mCloseRevealAnimator == null) return;
        mCloseRevealAnimator.cancel();
        mCloseRevealAnimator = null;
    }

    /**
     * The × is 24dp wide but only as tall as the chip it sits in, on a row that is 24dp itself.
     * The status row lends it the rest through a touch delegate, so the thumb target is square
     * without the control drawing any bigger.
     */
    private void updateCloseTouchDelegate() {
        View row = statusRow();
        if (!(row instanceof android.view.ViewGroup)) return;
        AppCompatImageButton close = mCloseButton;
        if (close == null || close.getVisibility() != VISIBLE || close.getWidth() <= 0) {
            if (mOwnsTouchDelegate) {
                row.setTouchDelegate(null);
                mOwnsTouchDelegate = false;
            }
            return;
        }
        android.graphics.Rect bounds =
            new android.graphics.Rect(0, 0, close.getWidth(), close.getHeight());
        ((android.view.ViewGroup) row).offsetDescendantRectToMyCoords(close, bounds);
        int target = dp(CLOSE_TARGET_DP);
        bounds.inset(-Math.max(0, target - bounds.width()) / 2,
            -Math.max(0, target - bounds.height()) / 2);
        row.setTouchDelegate(new android.view.TouchDelegate(bounds, close));
        mOwnsTouchDelegate = true;
    }

    /** The status row this bar stands in, or the nearest ancestor that can hold the delegate. */
    @Nullable
    private View statusRow() {
        View candidate = null;
        android.view.ViewParent parent = getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (candidate == null) candidate = view;
            if (view.getId() == R.id.terminal_status_row) return view;
            parent = view.getParent();
        }
        return candidate;
    }

    /**
     * The × is a measured segment of the selected chip: it takes room of its own, behind a hairline
     * divider, and the chips after it move along rather than being covered. It is built once and
     * kept, hidden between reveals — a chip row that gains and loses a child on every tap cannot be
     * reused across a refresh, and a rebuilt row loses the selection slide.
     */
    @NonNull
    private AppCompatImageButton ensureCloseButton() {
        AppCompatImageButton close = mCloseButton;
        if (close == null) {
            close = new AppCompatImageButton(getContext());
            close.setImageResource(R.drawable.ic_status_bar_close_window);
            close.setScaleType(ImageView.ScaleType.CENTER);
            close.setPadding(0, 0, 0, 0);
            // Clickable and focusable on purpose: the status bar's gesture treats a clickable
            // descendant of this row as child-owned, so a finger on the × can never start an
            // expand or a collapse instead of closing the window.
            close.setClickable(true);
            close.setFocusable(true);
            close.setOnClickListener(v -> {
                int target = mReveal.revealedIndex();
                mReveal.hide();
                applyReveal();
                if (target >= 0 && mCloseListener != null) {
                    mCloseListener.onWindowCloseRequested(target);
                }
            });
            mCloseButton = close;
        }
        if (close.getParent() != mTabs) {
            if (close.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) close.getParent()).removeView(close);
            }
            // Zero width in the strip's own flow: the segment is measured and placed by
            // SelectionStrip, which is also what keeps every chip's index where it was.
            mTabs.addView(close, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
        }
        mTabs.setCloseSegment(close, dp(CLOSE_SEGMENT_DP),
            dp(ChipWatermarkGeometry.CLOSE_DIVIDER_DP));
        applyCloseButtonStyle(close);
        return close;
    }

    /**
     * The × takes the selected chip's own fill: it is part of that chip now, not a lid over it, so
     * it wants no surface of its own — the highlight the strip draws already runs the whole width,
     * and the hairline in front of it is what says where the title ends.
     */
    private void applyCloseButtonStyle(@NonNull AppCompatImageButton close) {
        close.setBackground(null);
        ImageViewCompat.setImageTintList(close, ColorStateList.valueOf(mSelectedTextColor));
    }

    /**
     * Called from both setWindows branches. Missing the reuse branch would leave a stale description
     * on every pill whose label happened not to change.
     */
    private void applyTabContentDescriptions() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            WindowItem item = mItems.get(i);
            // spokenLabel is never modified: the busy state is a separate sentence, so a screen
            // reader announcing the window does not have to re-read a changed name.
            String description = getResources().getString(
                R.string.termux_window_tab_content_description, i + 1, mItems.size(),
                item.spokenLabel);
            if (item.attention) description += " · "
                + getResources().getString(R.string.termux_window_tab_attention_content_description);
            else if (item.done) description += " · " + getResources().getString(item.doneFailed
                ? R.string.termux_window_tab_failed_content_description
                : R.string.termux_window_tab_done_content_description);
            if (item.busy) {
                description += " · " + getResources().getString(
                    R.string.termux_window_tab_busy_content_description);
                if (item.progress != WindowItem.NO_PERCENTAGE) description += ", " + getResources()
                    .getString(R.string.termux_window_tab_progress_content_description, item.progress);
            }
            // The agent sentence rides on the description rather than on spokenLabel itself: the
            // label is what sameItems compares, so changing it as the agent's state moved would
            // re-inflate the whole pill row and kill the selection slide.
            String agent = agentStateWord(getContext(), item.agentState);
            if (agent != null) description += " · " + agent + ".";
            mTabs.getChildAt(i).setContentDescription(description);
        }
    }

    /**
     * Re-set each reused pill's text so it carries the marks its window's state calls for: the ring
     * in place of the process glyph while the shell is working, the bell after the label once it has
     * rung. Only pills whose state moved are touched unless {@code force}, so a refresh that changes
     * nothing costs no layout.
     */
    private void applyActivityStates(boolean force) {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            View child = mTabs.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            WindowItem item = mItems.get(i);
            Object shown = child.getTag(R.id.terminal_window_tab_state);
            if (!force && shown instanceof WindowItem && ((WindowItem) shown).sameActivity(item)
                && ((WindowItem) shown).label.equals(item.label)) continue;
            if (!(shown instanceof WindowItem) || !((WindowItem) shown).label.equals(item.label)) {
                ((TextView) child).setText(tabText(item));
            }
            applyChipWatermark((TextView) child, item, i == mSelectedIndex ? 1f : 0f);
            child.setTag(R.id.terminal_window_tab_state, item);
        }
        updateBusyAnimator();
    }

    /**
     * The pill's text: the window's title, and nothing else. Every indicator the label used to
     * spend characters on — the process glyph, the ring, the bell, the tick, the agent dot — is
     * drawn by {@link ChipWatermarkDrawable} behind and around the title instead, so nine
     * characters of a directory are nine characters of a directory.
     *
     * <p>The Nerd Font spans stay: a window the user named can carry an icon of its own inside the
     * title, and it has to be drawn by a face that has it.
     */
    @NonNull
    private CharSequence tabText(@NonNull WindowItem item) {
        String title = titleOf(item.label);
        return TerminalLabelSymbolSpans.apply(
            com.termux.shared.termux.font.NerdFontSpans.span(getContext(), title), mSymbolMaps);
    }

    /** The label without the process glyph the factories put in front of it. */
    @NonNull
    static String titleOf(@NonNull String label) {
        int glyphEnd = leadingGlyphEnd(label);
        if (glyphEnd == 0) return label;
        return glyphEnd < label.length() ? label.substring(glyphEnd + 1) : "";
    }

    /** The process glyph the watermark draws, or null for a label that carries none. */
    @Nullable
    static String glyphOf(@NonNull String label) {
        int glyphEnd = leadingGlyphEnd(label);
        return glyphEnd == 0 ? null : label.substring(0, glyphEnd);
    }

    /**
     * The dot the chip's top-trailing corner carries. A bell outranks the tick or the cross, as it
     * always has: a window that is asking is the news, not that its last command ended.
     */
    @NonNull
    static ChipWatermarkDrawable.Mark markFor(@NonNull WindowItem item) {
        if (item.attention) return ChipWatermarkDrawable.Mark.BELL;
        if (item.done) {
            return item.doneFailed ? ChipWatermarkDrawable.Mark.FAILED
                : ChipWatermarkDrawable.Mark.DONE;
        }
        return ChipWatermarkDrawable.Mark.NONE;
    }

    /**
     * Whether the chip's outline is drawing the ring. The mark no longer takes the ring's place —
     * they are different corners now — so a window that is working and asking says both.
     */
    private static boolean showsRing(@NonNull WindowItem item) {
        return item.busy;
    }

    /**
     * The dot's colour per state: the row's working accent while the agent works, the same warm
     * "this one wants you" colour the bell uses when it is waiting, and the pill's own muted label
     * colour when it is sitting at its prompt.
     */
    private int agentDotColor(@Nullable AgentStatus.State state) {
        if (state == AgentStatus.State.BLOCKED) return mAttentionColor;
        if (state == AgentStatus.State.WORKING) return mBusyColor;
        return mUnselectedTextColor;
    }

    /** How the bottom-leading dot is drawn for a reading, or that there is no agent to draw. */
    @NonNull
    private static ChipWatermarkDrawable.Agent agentFor(@Nullable AgentStatus.State state) {
        if (state == AgentStatus.State.WORKING) return ChipWatermarkDrawable.Agent.WORKING;
        if (state == AgentStatus.State.BLOCKED) return ChipWatermarkDrawable.Agent.WAITING;
        if (state == AgentStatus.State.IDLE) return ChipWatermarkDrawable.Agent.IDLE;
        return ChipWatermarkDrawable.Agent.NONE;
    }

    /** Bell and a failed command in the attention colour, a finished one in the working accent. */
    private int markColor(@NonNull ChipWatermarkDrawable.Mark mark) {
        return mark == ChipWatermarkDrawable.Mark.DONE ? mBusyColor : mAttentionColor;
    }

    /**
     * Everything the chip draws that is not its title: the fill and outline, the process glyph (or
     * the window's own icon) watermarked behind the text, the ring while a shell works, and the two
     * corner dots. One drawable per pill, reused — the pills themselves are reused across a
     * refresh, and a new background on every state change would cost a layout pass each time.
     */
    private void applyChipWatermark(@NonNull TextView tab, @NonNull WindowItem item,
                                    float selection) {
        ChipWatermarkDrawable chip = watermarkOf(tab);
        chip.setSurface(mStatusBarRadiusPx, dp(ChipWatermarkGeometry.OUTLINE_WIDTH_DP),
            mUnselectedFillColor, mUnselectedStrokeColor);
        chip.setRtl(getLayoutDirection() == LAYOUT_DIRECTION_RTL);
        String glyph = glyphOf(item.label);
        chip.setGlyph(glyph, glyph == null ? null : watermarkFace(glyph));
        chip.setIcon(item.icon);
        chip.setGlyphColor(mGlyphColor);
        chip.setSelection(selection);
        applyTitleHalo(tab, selection);
        chip.setActivity(showsRing(item), item.progress,
            item.progressError ? mAttentionColor : mBusyColor, mLazyMode);
        ChipWatermarkDrawable.Mark mark = markFor(item);
        chip.setMark(mark, markColor(mark), mGroundColor);
        chip.setAgent(agentFor(item.agentState), agentDotColor(item.agentState));
    }

    /**
     * The soft halo under a title, in the fill of the chip it stands on: the glyph runs behind the
     * first letters now, and without this they sit in it rather than on it. The two fills differ,
     * so the halo travels with the selection exactly as the text colour does.
     */
    private void applyTitleHalo(@NonNull TextView tab, float selection) {
        float fraction = selection < 0f ? 0f : selection > 1f ? 1f : selection;
        tab.setShadowLayer(dpF(ChipWatermarkGeometry.TITLE_HALO_DP), 0f, 0f,
            ColorUtils.blendARGB(mUnselectedHaloColor, mSelectedHaloColor, fraction));
    }

    /** Every pill's watermark at once, after a palette, radius or face change. */
    private void applyChipWatermarks() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            View child = mTabs.getChildAt(i);
            if (child instanceof TextView) {
                applyChipWatermark((TextView) child, mItems.get(i), i == mSelectedIndex ? 1f : 0f);
            }
        }
    }

    @NonNull
    private ChipWatermarkDrawable watermarkOf(@NonNull TextView tab) {
        android.graphics.drawable.Drawable background = tab.getBackground();
        if (background instanceof ChipWatermarkDrawable) return (ChipWatermarkDrawable) background;
        ChipWatermarkDrawable chip =
            new ChipWatermarkDrawable(getResources().getDisplayMetrics().density);
        tab.setBackground(chip);
        return chip;
    }

    /**
     * The face that has the watermark's code point, resolved exactly as the label's own icons are:
     * a configured symbol_map first, the bundled symbols face behind it, and the terminal's regular
     * face as the last resort. A watermark drawn by a face without the glyph is tofu behind the
     * title, which is worse than no watermark at all — but the bundled face carries the whole Nerd
     * set, so that only happens where it failed to load.
     */
    @Nullable
    private Typeface watermarkFace(@NonNull String glyph) {
        if (glyph.isEmpty()) return null;
        Typeface mapped = TerminalLabelSymbolSpans.faceFor(mSymbolMaps, glyph.codePointAt(0));
        if (mapped != null) return mapped;
        Typeface bundled = com.termux.shared.termux.font.NerdFontSpans.typeface(getContext());
        return bundled != null ? bundled : mTerminalTypeface;
    }

    /** For tests: the drawable one chip is wearing, or null before the row is populated. */
    @Nullable
    @androidx.annotation.VisibleForTesting
    ChipWatermarkDrawable chipWatermarkAt(int index) {
        if (index < 0 || index >= mTabs.getChildCount()) return null;
        android.graphics.drawable.Drawable background = mTabs.getChildAt(index).getBackground();
        return background instanceof ChipWatermarkDrawable ? (ChipWatermarkDrawable) background
            : null;
    }

    /**
     * Length of the process glyph the factories put in front of every label — one private-use code
     * point and the space after it — or 0 when the label does not start with one.
     */
    static int leadingGlyphEnd(@NonNull String label) {
        if (label.isEmpty()) return 0;
        int codePoint = label.codePointAt(0);
        int type = Character.getType(codePoint);
        if (type != Character.PRIVATE_USE) return 0;
        int end = Character.charCount(codePoint);
        // A label that is the glyph alone - a process its icon names - is all slot.
        if (end == label.length()) return end;
        return label.charAt(end) == ' ' ? end : 0;
    }

    /** Whether any pill is drawing a mark that moves, which is all the clock below exists for. */
    private boolean hasIndeterminateWindow() {
        for (WindowItem item : mItems) {
            if (needsClock(item)) return true;
        }
        return false;
    }

    /**
     * The turning arc and the breathing agent dot are the only marks that need frames; a percentage
     * ring, a bell, and a dot that is waiting or idle are as static as the label.
     */
    private static boolean needsClock(@NonNull WindowItem item) {
        return (showsRing(item) && item.progress == WindowItem.NO_PERCENTAGE)
            || item.agentState == AgentStatus.State.WORKING;
    }

    /**
     * Lazy mode turns the ring in steps instead of spinning it: the animator redrew every working
     * pill each vsync for as long as any shell was busy, which with a long-running agent meant
     * forever. A stationary arc was tried first and read as stuck, so the ring still moves — eight
     * stops a turn, on a timer, which is one redraw per stop rather than one per frame.
     */
    public void setLazyMode(boolean lazy) {
        if (mLazyMode == lazy) return;
        mLazyMode = lazy;
        applyActivityStates(true);
    }

    private boolean mLazyMode;
    @Nullable private Runnable mLazyTick;

    /**
     * One clock for the whole bar rather than one per pill: setWindows's removeAllViews() then has
     * nothing to clean up, and every ring turns in phase. It only invalidates the pills that carry
     * a turning arc; a percentage ring and a bell are as static as the label. Smooth mode drives it
     * from an animator, lazy mode from a slow tick.
     *
     * <p>Deliberately not folded into mSelectionAnimator. Both only invalidate, so they compose;
     * sharing one animator would stall the activity indication for the length of every window switch.
     */
    private void updateBusyAnimator() {
        boolean wanted = hasIndeterminateWindow() && mAttached && mWindowVisible;
        boolean smooth = wanted && !mLazyMode;
        boolean stepped = wanted && mLazyMode;
        if (!smooth && mBusyAnimator != null) {
            mBusyAnimator.cancel();
            mBusyAnimator = null;
        }
        if (!stepped && mLazyTick != null) {
            removeCallbacks(mLazyTick);
            mLazyTick = null;
        }
        if (smooth && mBusyAnimator == null) {
            mBusyAnimator = ValueAnimator.ofFloat(0f, 1f);
            mBusyAnimator.setDuration(WindowActivityRing.SPIN_MS);
            mBusyAnimator.setRepeatCount(ValueAnimator.INFINITE);
            mBusyAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            mBusyAnimator.addUpdateListener(animation -> invalidateTurningRings());
            mBusyAnimator.start();
        }
        if (stepped && mLazyTick == null) {
            mLazyTick = new Runnable() {
                @Override public void run() {
                    if (mLazyTick != this) return;
                    invalidateTurningRings();
                    postDelayed(this, WindowActivityRing.LAZY_TICK_MS);
                }
            };
            postDelayed(mLazyTick, WindowActivityRing.LAZY_TICK_MS);
        }
    }

    private void invalidateTurningRings() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            if (needsClock(mItems.get(i))) mTabs.getChildAt(i).invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // The × moves with the row it is in; its borrowed thumb target has to move with it.
        updateCloseTouchDelegate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttached = true;
        updateBusyAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        mAttached = false;
        if (mBusyAnimator != null) {
            mBusyAnimator.cancel();
            mBusyAnimator = null;
        }
        if (mLazyTick != null) {
            removeCallbacks(mLazyTick);
            mLazyTick = null;
        }
        // A stream that was under way when the view left the window never gets its UP; the
        // tracker goes back to the pool and the latch does not survive into the next attach.
        if (mOverswipeVelocity != null) {
            mOverswipeVelocity.recycle();
            mOverswipeVelocity = null;
        }
        mOverswipeOwned = false;
        mOverswipeInterrupted = false;
        mOverswipePx = 0f;
        cancelCloseReveal();
        // A × is a four-second offer, not a state: a row that left the window comes back without it.
        mReveal.hide();
        applyReveal();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        updateBusyAnimator();
    }

    /** For tests: the × a chip is offering right now, or null while no chip is offering one. */
    @Nullable
    @androidx.annotation.VisibleForTesting
    public View revealedCloseView() {
        return mCloseButton != null && mCloseButton.getVisibility() == VISIBLE ? mCloseButton : null;
    }

    /** For tests: the surface the selected chip is standing on, × segment included. */
    @androidx.annotation.VisibleForTesting
    boolean selectionHighlightBounds(@NonNull RectF output) {
        return mTabs.copyCurrentHighlightBounds(output);
    }

    /** For tests: whether a working window's ring is turning right now, smoothly or in steps. */
    @androidx.annotation.VisibleForTesting
    public boolean isBusyAnimationRunning() {
        return (mBusyAnimator != null && mBusyAnimator.isStarted()) || mLazyTick != null;
    }

    private TextView createTab(@NonNull WindowItem item, boolean selected) {
        Context context = getContext();
        TextView tab = new TextView(context);
        tab.setGravity(Gravity.CENTER);
        tab.setMinWidth(0);
        tab.setMaxWidth(dp(104));
        // A half-dp on each side is visible at modern phone densities without making the compact
        // window row feel loose; the leading side carries the title's nudge past the glyph too.
        tab.setPaddingRelative(dp(3.5f) + dp(ChipWatermarkGeometry.TITLE_NUDGE_DP), 0,
            dp(3.5f), 0);
        tab.setSingleLine(true);
        tab.setIncludeFontPadding(false);
        tab.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        // Bundled symbols face first, symbol_map faces second: both spans land on a shared PUA
        // run, and the later-applied user-configured face wins at draw time — the bundled Nerd
        // Font glyphs only ever fill runs no symbol_map claims.
        tab.setText(tabText(item));
        tab.setTag(R.id.terminal_window_tab_state, item);
        tab.setTextColor(selected ? mSelectedTextColor : mUnselectedTextColor);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tab.setTypeface(mTerminalTypeface, selected ? Typeface.BOLD : Typeface.NORMAL);
        // Never narrower than the watermark behind it: a window whose whole label is its process
        // icon has no title left to size the chip, and a sliver of a glyph reads as damage.
        tab.setMinWidth(dp(ChipWatermarkGeometry.GLYPH_SIZE_DP)
            + dp(ChipWatermarkGeometry.TITLE_NUDGE_DP) + dp(3.5f) * 2);
        applyChipWatermark(tab, item, selected ? 1f : 0f);
        tab.setSelected(selected);
        tab.setFocusable(true);
        return tab;
    }

    private void addCreateButton(boolean firstItem) {
        Context context = getContext();
        // The plus is the place's too: the same accent as the chips beside it, a little quieter.
        int accent = mPlaceAccent != null ? mPlaceAccent
            : MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(context, R.color.termux_primary));
        AppCompatImageButton add = new AppCompatImageButton(context);
        add.setImageResource(R.drawable.ic_status_bar_add_window);
        ImageViewCompat.setImageTintList(add, ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(accent, 184)));
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setBackground(null);
        add.setPadding(0, 0, 0, 0);
        add.setContentDescription(getResources().getString(R.string.termux_window_new_content_description));
        add.setOnClickListener(v -> {
            if (mCreateListener != null) mCreateListener.onCreateWindow();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), LayoutParams.MATCH_PARENT);
        if (!firstItem) params.setMarginStart(dp(3));
        mTabs.addView(add, params);
    }

    private void animateSelectionSlide(int previousSelected, int selectedIndex) {
        // A pane switch driven through the pane API can now land while the bar's window is not
        // visible (the Activity is stopped, not destroyed; see TerminalActionDispatcher). Same
        // reasoning as updateBusyAnimator: an invisible window still delivers frame callbacks, so
        // an ungated animator here would spend real frames sliding a highlight nobody can see.
        if (!mWindowVisible || previousSelected < 0 || previousSelected >= mItems.size()
            || selectedIndex < 0 || selectedIndex >= mItems.size()) {
            mTabs.snapSelection(selectedIndex);
            applyStableTabSelection();
            return;
        }
        RectF start = new RectF();
        RectF end = new RectF();
        if (!mTabs.copyCurrentHighlightBounds(start)
            || !mTabs.copyChildBounds(selectedIndex, end)) {
            mTabs.snapSelection(selectedIndex);
            applyStableTabSelection();
            return;
        }

        cancelSelectionAnimation();
        applyAnimatedTabSelection(previousSelected, selectedIndex, 0f);
        mTabs.setAnimatedHighlight(start);
        mSelectionAnimator = ValueAnimator.ofFloat(0f, 1f);
        mSelectionAnimator.setDuration(WINDOW_SWITCH_ANIMATION_DURATION_MS);
        mSelectionAnimator.setInterpolator(settleInterpolator());
        mSelectionAnimator.addUpdateListener(animation -> {
            float progress = (Float) animation.getAnimatedValue();
            mTabs.setAnimatedHighlight(lerp(start, end, progress));
            applyAnimatedTabSelection(previousSelected, selectedIndex, progress);
        });
        mSelectionAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (mSelectionAnimator == animation) mSelectionAnimator = null;
                if (mCancelled) return;
                mTabs.snapSelection(selectedIndex);
                applyStableTabSelection();
            }
        });
        mSelectionAnimator.start();
    }

    private void scrollSelectedIntoView(int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= mTabs.getChildCount()) return;
        post(() -> {
            View selected = mTabs.getChildAt(selectedIndex);
            int target = Math.max(0, selected.getLeft() - dp(5));
            if (target == getScrollX()) return;
            // Same reasoning as animateSelectionSlide: an invisible window still gets animated,
            // it just never shows it, so skip straight to the end state while nobody can see it.
            if (mWindowVisible) animateScrollTo(target);
            else scrollTo(target, 0);
        });
    }

    private void animateScrollTo(int target) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(getScrollX(), target);
        animator.setDuration(WINDOW_SWITCH_ANIMATION_DURATION_MS);
        animator.setInterpolator(settleInterpolator());
        animator.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        animator.start();
    }

    private Interpolator settleInterpolator() {
        return Motion.settle();
    }

    private void cancelSelectionAnimation() {
        if (mSelectionAnimator == null) return;
        mSelectionAnimator.cancel();
        mSelectionAnimator = null;
    }

    private void applyStableTabSelection() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            TextView tab = (TextView) mTabs.getChildAt(i);
            boolean selected = i == mSelectedIndex;
            tab.setSelected(selected);
            tab.setTextColor(selected ? mSelectedTextColor : mUnselectedTextColor);
            tab.setTypeface(mTerminalTypeface, selected ? Typeface.BOLD : Typeface.NORMAL);
            applyTitleHalo(tab, selected ? 1f : 0f);
            ChipWatermarkDrawable chip = chipWatermarkAt(i);
            if (chip != null) chip.setSelection(selected ? 1f : 0f);
            tab.setAlpha(1f);
            tab.setTranslationX(0f);
        }
    }

    private void applyAnimatedTabSelection(int previousSelected, int selectedIndex, float progress) {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            TextView tab = (TextView) mTabs.getChildAt(i);
            tab.setSelected(i == selectedIndex);
            ChipWatermarkDrawable chip = chipWatermarkAt(i);
            if (i == previousSelected) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mSelectedTextColor, mUnselectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
                applyTitleHalo(tab, 1f - progress);
                if (chip != null) chip.setSelection(1f - progress);
            } else if (i == selectedIndex) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mUnselectedTextColor, mSelectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
                applyTitleHalo(tab, progress);
                if (chip != null) chip.setSelection(progress);
            } else {
                tab.setTextColor(mUnselectedTextColor);
                tab.setTypeface(mTerminalTypeface, Typeface.NORMAL);
                applyTitleHalo(tab, 0f);
                if (chip != null) chip.setSelection(0f);
            }
            tab.setAlpha(1f);
            tab.setTranslationX(0f);
        }
    }

    /**
     * The colour of the place whose chips these are — the wall's Widgets, Terminal and Display
     * each have one — for the selected chip's fill and stroke. Null is the theme's primary.
     */
    public void setPlaceAccent(@Nullable Integer accent) {
        if (accent == null ? mPlaceAccent == null : accent.equals(mPlaceAccent)) return;
        mPlaceAccent = accent;
        updatePalette();
        for (int i = 0; i < mTabs.getChildCount(); i++) {
            View child = mTabs.getChildAt(i);
            if (child == mCloseButton) {
                applyCloseButtonStyle(mCloseButton);
            } else if (child instanceof TextView) {
                ((TextView) child).setTextColor(child.isSelected()
                    ? mSelectedTextColor : mUnselectedTextColor);
            } else if (child instanceof AppCompatImageButton) {
                int tint = accent != null ? accent
                    : MaterialColors.getColor(getContext(), com.termux.shared.R.attr.termuxColorPrimary,
                        ContextCompat.getColor(getContext(), R.color.termux_primary));
                ImageViewCompat.setImageTintList((AppCompatImageButton) child,
                    ColorStateList.valueOf(ColorUtils.setAlphaComponent(tint, 184)));
            }
        }
        applyChipWatermarks();
        invalidate();
    }

    private void updatePalette() {
        Context context = getContext();
        int primary = mPlaceAccent != null ? mPlaceAccent
            : MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
                ContextCompat.getColor(context, R.color.termux_primary));
        int onSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int onSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        int secondary = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mSelectedTextColor = onSurface;
        mUnselectedTextColor = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(onSurfaceVariant, secondary, .18f), 148);
        mUnselectedFillColor = ColorUtils.setAlphaComponent(secondary, 16);
        mUnselectedStrokeColor = ColorUtils.setAlphaComponent(secondary, 34);
        mSelectedFillColor = ColorUtils.setAlphaComponent(primary, 58);
        mSelectedStrokeColor = ColorUtils.setAlphaComponent(primary, 112);
        // The watermark is the place's accent, never the label's colour: sharing the title's
        // colour is what buried the glyph under it on the phone. Only its alpha moves with the
        // selection — 30% at rest, 52% on the chip the user is in.
        mGlyphColor = primary;
        // The halo the title is drawn over, one per fill: near-opaque, so letters read against the
        // glyph running behind them.
        mUnselectedHaloColor = ChipWatermarkGeometry.haloColor(mUnselectedFillColor);
        mSelectedHaloColor = ChipWatermarkGeometry.haloColor(mSelectedFillColor);
        // The ground a corner dot is haloed against: the panel the row itself stands on, so a dot
        // over the watermark still reads as a dot.
        mGroundColor = ColorUtils.setAlphaComponent(MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            ContextCompat.getColor(context, R.color.termux_surface_panel_high)), 255);
        mTabs.setHighlightStyle(mSelectedFillColor, mSelectedStrokeColor,
            mStatusBarRadiusPx, dp(1));
        // Tertiary for the ring, like the row's other "something is happening" accents. Error for
        // the bell and a failed progress report: it is the one Material role that is warm in every
        // generated palette, and a window waiting on the user has to be findable without reading
        // any label.
        mBusyColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, primary);
        mAttentionColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorError,
            ContextCompat.getColor(context, R.color.termux_error));
        applyChipWatermarks();
    }

    private void applyTabSurfaceStyle() {
        applyActivityStates(true);
        applyStableTabSelection();
        if (mCloseButton != null) applyCloseButtonStyle(mCloseButton);
        mTabs.invalidate();
    }

    private static RectF lerp(@NonNull RectF start, @NonNull RectF end, float progress) {
        return new RectF(
            start.left + (end.left - start.left) * progress,
            start.top + (end.top - start.top) * progress,
            start.right + (end.right - start.right) * progress,
            start.bottom + (end.bottom - start.bottom) * progress);
    }

    /**
     * Draws one selected surface beneath the pills so it can travel without moving their labels,
     * and owns the × the selected chip grows on its trailing side.
     */
    private static final class SelectionStrip extends LinearLayout {

        private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mAnimatedHighlight = new RectF();
        private final RectF mDrawBounds = new RectF();
        private int mWindowCount;
        private int mSelection = -1;
        private boolean mHasAnimatedHighlight;
        private float mCornerRadius;
        /** The chip whose trailing side carries the ×, or {@link ChipRevealPolicy#NONE}. */
        private int mCloseIndex = ChipRevealPolicy.NONE;
        @Nullable private View mCloseSegment;
        /** The segment's width once it has finished opening, and how far open it is now. */
        private int mCloseFullWidthPx;
        private int mCloseDividerPx;
        private float mCloseFraction;
        /** How far the chips after the segment are currently pushed, so an offset never doubles. */
        private int mAppliedShiftPx;

        SelectionStrip(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            mFillPaint.setStyle(Paint.Style.FILL);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            mDividerPaint.setStyle(Paint.Style.FILL);
        }

        void setCloseSegment(@Nullable View segment, int fullWidthPx, int dividerPx) {
            mCloseSegment = segment;
            mCloseFullWidthPx = Math.max(0, fullWidthPx);
            mCloseDividerPx = Math.max(0, dividerPx);
        }

        void setCloseTarget(int index) {
            if (mCloseIndex == index) return;
            // Undone against the chip that carried it, before the index that identifies those
            // neighbours moves: an offset left behind is a row that never returns to its own width.
            if (mAppliedShiftPx != 0) {
                offsetFollowers(-mAppliedShiftPx);
                mAppliedShiftPx = 0;
            }
            mCloseIndex = index;
        }

        int closeTarget() {
            return mCloseIndex;
        }

        /** How far open the × stands, 0 to 1. Moves pixels only: the measure is already final. */
        void setCloseRevealFraction(float fraction) {
            float clamped = fraction < 0f ? 0f : fraction > 1f ? 1f : fraction;
            if (mCloseFraction == clamped) return;
            mCloseFraction = clamped;
            placeCloseSegment();
            invalidate();
        }

        /** The room the × takes in the row, whether or not it has finished opening into it. */
        private int closeExtentPx() {
            return hasCloseSegment() ? mCloseFullWidthPx : 0;
        }

        private boolean hasCloseSegment() {
            View segment = mCloseSegment;
            return segment != null && segment.getVisibility() != GONE && segment.getParent() == this
                && mCloseIndex >= 0 && mCloseIndex < mWindowCount && mCloseIndex < getChildCount();
        }

        /** Whether {@code x}, in this strip's own coordinates, is on a chip or on its ×. */
        boolean isInsideChipWithClose(int index, float x) {
            if (index < 0 || index >= getChildCount()) return false;
            View chip = getChildAt(index);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int shift = index == mCloseIndex ? currentShiftPx() : 0;
            float left = rtl ? chip.getLeft() - shift : chip.getLeft();
            float right = rtl ? chip.getRight() : chip.getRight() + shift;
            return x >= left && x < right;
        }

        private int currentShiftPx() {
            return hasCloseSegment()
                ? ChipWatermarkGeometry.closeSegmentWidthPx(mCloseFraction, mCloseFullWidthPx) : 0;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int extra = closeExtentPx();
            if (extra <= 0) return;
            // The segment is a zero-width child in the flow, so the row has to be told about the
            // room it takes. Counted in full from the first frame: the scroll extents then hold
            // still while it opens, and only the segment's own pixels move.
            setMeasuredDimension(getMeasuredWidth() + extra, getMeasuredHeight());
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            mAppliedShiftPx = 0;
            placeCloseSegment();
        }

        /**
         * Opens the gap after the chip that asked for the ×, pushes the chips behind it along, and
         * gives the segment the gap. Offsets rather than a layout pass: a 180 ms open that walked
         * the whole activity's layout every frame is exactly the per-frame work the row cannot
         * afford.
         */
        private void placeCloseSegment() {
            View segment = mCloseSegment;
            if (segment == null || segment.getParent() != this) return;
            int shift = currentShiftPx();
            if (!hasCloseSegment()) {
                if (mAppliedShiftPx != 0) offsetFollowers(-mAppliedShiftPx);
                mAppliedShiftPx = 0;
                segment.layout(0, 0, 0, 0);
                return;
            }
            offsetFollowers(shift - mAppliedShiftPx);
            mAppliedShiftPx = shift;
            View chip = getChildAt(mCloseIndex);
            int height = getHeight();
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int left = rtl ? chip.getLeft() - shift : chip.getRight();
            segment.measure(MeasureSpec.makeMeasureSpec(shift, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            segment.layout(left, 0, left + shift, height);
        }

        /** Every child laid out after the chip carrying the ×, the create button included. */
        private void offsetFollowers(int dx) {
            if (dx == 0) return;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = mCloseIndex + 1; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == mCloseSegment) continue;
                child.offsetLeftAndRight(rtl ? -dx : dx);
            }
        }

        void setWindowCount(int windowCount) {
            mWindowCount = Math.max(0, windowCount);
        }

        void setHighlightStyle(int fillColor, int strokeColor, float cornerRadius,
                               float strokeWidth) {
            mFillPaint.setColor(fillColor);
            mStrokePaint.setColor(strokeColor);
            mDividerPaint.setColor(strokeColor);
            mStrokePaint.setStrokeWidth(strokeWidth);
            mCornerRadius = Math.max(0f, cornerRadius);
            invalidate();
        }

        void snapSelection(int selection) {
            mSelection = selection;
            mHasAnimatedHighlight = false;
            invalidate();
        }

        void setAnimatedHighlight(@NonNull RectF bounds) {
            mAnimatedHighlight.set(bounds);
            mHasAnimatedHighlight = true;
            invalidate();
        }

        boolean copyCurrentHighlightBounds(@NonNull RectF output) {
            if (mHasAnimatedHighlight && !mAnimatedHighlight.isEmpty()) {
                output.set(mAnimatedHighlight);
                return true;
            }
            return copyChildBounds(mSelection, output);
        }

        /** A chip's bounds, grown over the × while it is the one carrying it. */
        boolean copyChildBounds(int index, @NonNull RectF output) {
            if (index < 0 || index >= mWindowCount || index >= getChildCount()) return false;
            View child = getChildAt(index);
            if (child.getWidth() <= 0 || child.getHeight() <= 0) return false;
            output.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
            if (index == mCloseIndex && hasCloseSegment()) {
                int shift = currentShiftPx();
                if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) output.left -= shift;
                else output.right += shift;
            }
            return true;
        }

        @Override protected void dispatchDraw(@NonNull Canvas canvas) {
            // Reused: the selection slide invalidates this strip every frame it is running, and a
            // fresh RectF per frame is pure allocation on an animation path.
            RectF bounds = mDrawBounds;
            boolean hasBounds;
            if (mHasAnimatedHighlight) {
                bounds.set(mAnimatedHighlight);
                hasBounds = !bounds.isEmpty();
            } else {
                hasBounds = copyChildBounds(mSelection, bounds);
            }
            if (hasBounds) {
                float strokeInset = mStrokePaint.getStrokeWidth() / 2f;
                bounds.inset(strokeInset, strokeInset);
                canvas.drawRoundRect(bounds, mCornerRadius, mCornerRadius, mFillPaint);
                canvas.drawRoundRect(bounds, mCornerRadius, mCornerRadius, mStrokePaint);
            }
            drawCloseDivider(canvas);
            super.dispatchDraw(canvas);
        }

        /** The hairline the × stands behind, in the selected chip's own stroke colour. */
        private void drawCloseDivider(@NonNull Canvas canvas) {
            if (mCloseDividerPx <= 0 || !hasCloseSegment()) return;
            int shift = currentShiftPx();
            if (shift <= 0) return;
            View chip = getChildAt(mCloseIndex);
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            // Where the title ends and the × begins, not where the chip now ends.
            float x = rtl ? chip.getLeft() - mCloseDividerPx : chip.getRight();
            float inset = chip.getHeight() * .2f;
            canvas.drawRect(x, chip.getTop() + inset, x + mCloseDividerPx,
                chip.getBottom() - inset, mDividerPaint);
        }
    }

    /**
     * Take the faces the terminal itself is drawing with, rather than reading font.ttf on our own:
     * the row's labels are terminal text, icons included, and a face the panes are not using is how
     * a symbol_map'd icon code point ends up as tofu here.
     *
     * <p>Identity is the whole change check — the holder returns the same value while nothing has
     * moved — so this stays a pair of pointer comparisons on a path the pill row walks often.
     */
    private boolean reloadTerminalTypeface() {
        TerminalLabelFaces faces = TerminalLabelFaces.current();
        if (faces.regular == mTerminalTypeface && faces.symbolMaps == mSymbolMaps) return false;
        mTerminalTypeface = faces.regular;
        mSymbolMaps = faces.symbolMaps;
        return true;
    }

    /** Activity flags only; kept out of sameItems so a flip does not re-inflate the pill row. */
    private static boolean sameActivity(@NonNull List<WindowItem> left,
                                    @NonNull List<WindowItem> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).sameActivity(right.get(i))) return false;
        }
        return true;
    }

    private static boolean sameItems(@NonNull List<WindowItem> left,
                                     @NonNull List<WindowItem> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            WindowItem a = left.get(i);
            WindowItem b = right.get(i);
            if (!a.label.equals(b.label) || !a.spokenLabel.equals(b.spokenLabel)) return false;
        }
        return true;
    }

    /**
     * The selected tab's view, so a surface can anchor itself to the window it belongs to. Null
     * before the row is populated or while nothing is selected.
     */
    @Nullable
    public View selectedTabView() {
        if (mSelectedIndex < 0 || mSelectedIndex >= mTabs.getChildCount()) return null;
        return mTabs.getChildAt(mSelectedIndex);
    }

    /** Process is represented only by a Nerd Font glyph; visible text is the compact directory. */
    @NonNull
    public static WindowItem itemFor(@Nullable TerminalSession session, int index) {
        if (session == null) {
            String fallback = "window " + (index + 1);
            return new WindowItem(glyph(0xE795) + " " + (index + 1), fallback);
        }
        String process = processName(session.getTitle());
        String directory = directoryName(session.getCwd(), session.getTitle());
        String spokenProcess = process == null ? "terminal" : process;
        return new WindowItem(processGlyph(process) + " " + directory,
            spokenProcess + " in " + directory);
    }

    /**
     * Build an item for a window the user has named. The name replaces the derived text but keeps
     * the live process glyph, so a named tab still shows at a glance what is running in it — the
     * name says which window it is, the glyph says what it is doing.
     */
    @NonNull
    public static WindowItem itemForNamed(@NonNull String name, @Nullable String processName) {
        String spokenProcess = processName == null ? "terminal" : processName;
        return new WindowItem(processGlyph(processName) + " " + name,
            name + ", " + spokenProcess);
    }

    /**
     * Build an item from foreground detection: the glyph reflects {@code processName}, while the
     * visible text is a pre-truncated basename (open file, process name, or directory). Callers own
     * the label priority; this only maps the glyph and pairs it with the spoken label.
     */
    @NonNull
    public static WindowItem itemForResolved(@Nullable String processName,
                                             @NonNull String displayText,
                                             @NonNull String spokenLabel) {
        String glyph = processGlyph(processName);
        return new WindowItem(displayText.isEmpty() ? glyph : glyph + " " + displayText,
            spokenLabel);
    }

    /**
     * The window-bar policy for a pane whose foreground the resolver identified: the open file for
     * an editor, otherwise the process name itself — always as visible text, even when
     * {@code processName}'s own glyph already depicts it. That "always" matters once a pane starts
     * working: {@link #tabText} only ever replaces the leading glyph run with the progress ring, so
     * a pill whose only content was that glyph (pacman mid-update, say) went blank but for a
     * spinning ring the moment it became busy, with nothing left beside the ring to read.
     */
    @NonNull
    public static WindowItem itemForForegroundProcess(@NonNull String processName,
                                                       @Nullable String openFile) {
        if (openFile != null) {
            return itemForResolved(processName, truncateFile(openFile),
                processName + " editing " + openFile);
        }
        return itemForResolved(processName, truncateProcess(processName), processName);
    }

    /** Terminal editors whose foreground presence means "show the open file, not the process". */
    public static boolean isEditor(@Nullable String process) {
        if (process == null) return false;
        switch (process.toLowerCase(Locale.ROOT)) {
            case "vim":
            case "vi":
            case "nvim":
            case "neovim":
            case "nano":
            case "emacs":
            case "emacsclient":
            case "hx":
            case "helix":
            case "micro":
            case "kak":
            case "kakoune":
            case "ne":
            case "joe":
            case "vis":
            case "ed":
                return true;
            default:
                return false;
        }
    }

    /** Directory basename for a foreground-derived cwd, middle-truncated like the idle label. */
    @NonNull
    public static String directoryLabel(@Nullable String cwd) {
        return directoryName(cwd, null);
    }

    /**
     * The most a pill's text runs to. The row is a phone's status bar: a pill carries one short
     * item - the open file, the process, the directory - and the glyph in its slot says the rest.
     */
    static final int LABEL_MAX_CHARS = 9;

    @NonNull
    public static String truncateProcess(@NonNull String process) {
        return middleEllipsize(process, LABEL_MAX_CHARS);
    }

    /**
     * Middle-truncate a filename while preserving its extension where possible, e.g.
     * {@code terminal…java}. Falls back to plain middle truncation when the extension is too long
     * to keep.
     */
    @NonNull
    public static String truncateFile(@NonNull String name) {
        int maxChars = LABEL_MAX_CHARS;
        String cleaned = name;
        if (cleaned.length() <= maxChars) return cleaned;
        int dot = cleaned.lastIndexOf('.');
        if (dot <= 0 || dot == cleaned.length() - 1) return middleEllipsize(cleaned, maxChars);
        String base = cleaned.substring(0, dot);
        String ext = cleaned.substring(dot + 1);
        // Only worth preserving when the extension still leaves room for a meaningful head.
        if (ext.length() > maxChars - 3) return middleEllipsize(cleaned, maxChars);
        int head = maxChars - ext.length() - 1;
        if (head < 1) return middleEllipsize(cleaned, maxChars);
        return base.substring(0, head) + "…" + ext;
    }

    @NonNull
    private static String processGlyph(@Nullable String process) {
        if (process == null) return glyph(0xE795);               // dev-terminal
        switch (process) {
            case "fish": return glyph(0xF023A);                // md-fish
            case "pacman": return glyph(0xF0BAF);              // md-pac-man
            case "ssh": return glyph(0xF08C0);                 // md-ssh
            case "tmux": return glyph(0xEBC8);                 // cod-terminal-tmux
            case "bash":
            case "sh":
            case "zsh": return glyph(0xF1183);                 // md-bash
            case "python":
            case "python3": return glyph(0xE73C);              // dev-python
            case "node":
            case "nodejs": return glyph(0xE719);               // dev-nodejs
            case "docker": return glyph(0xE7B0);               // dev-docker
            case "vim":
            case "vi": return glyph(0xE7C5);                   // dev-vim
            case "nvim":
            case "neovim": return glyph(0xE6AE);               // custom-neovim
            case "nano":
            case "micro":
            case "ne":
            case "joe":
            case "vis":
            case "ed": return glyph(0xF0F6);                   // md-file-document-edit
            case "emacs":
            case "emacsclient": return glyph(0xE632);          // custom-emacs
            case "hx":
            case "helix":
            case "kak":
            case "kakoune": return glyph(0xEB0E);              // cod-edit
            case "git": return glyph(0xE702);                  // dev-git
            default: return glyph(0xE795);                       // dev-terminal
        }
    }

    /** Public so a named tab can keep the process glyph the derived label would have picked. */
    @Nullable
    public static String processName(@Nullable String title) {
        String cleaned = clean(title);
        if (cleaned == null) return null;
        int inDirectory = cleaned.indexOf(" in <");
        if (inDirectory > 0) cleaned = cleaned.substring(0, inDirectory);
        int separator = cleaned.indexOf(' ');
        if (separator > 0) cleaned = cleaned.substring(0, separator);
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1);
        cleaned = cleaned.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._+-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    @NonNull
    private static String directoryName(@Nullable String cwd, @Nullable String title) {
        String path = clean(cwd);
        if (path == null) {
            String cleanedTitle = clean(title);
            if (cleanedTitle != null) {
                int open = cleanedTitle.lastIndexOf('<');
                int close = cleanedTitle.lastIndexOf('>');
                if (open >= 0 && close > open) path = cleanedTitle.substring(open + 1, close);
                else if (cleanedTitle.endsWith(" ~")) path = "home";
            }
        }
        if (path == null || path.equals(TermuxConstants.TERMUX_HOME_DIR_PATH) || path.equals("~")) {
            return "home";
        }
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        String leaf = slash >= 0 ? path.substring(slash + 1) : path;
        if (leaf.isEmpty()) leaf = "/";
        return middleEllipsize(leaf, LABEL_MAX_CHARS);
    }

    @NonNull
    static String middleEllipsize(@NonNull String value, int maxChars) {
        if (value.length() <= maxChars || maxChars < 5) return value;
        int tail = Math.max(2, maxChars / 3);
        int head = maxChars - tail - 1;
        return value.substring(0, head) + "…" + value.substring(value.length() - tail);
    }

    @Nullable
    private static String clean(@Nullable String value) {
        if (value == null) return null;
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    @NonNull
    private static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** A blur radius is not a layout distance: rounding 1.5dp to whole pixels coarsens the halo. */
    private float dpF(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
