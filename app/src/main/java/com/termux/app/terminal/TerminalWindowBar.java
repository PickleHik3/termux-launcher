package com.termux.app.terminal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
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
            this.label = label;
            this.spokenLabel = spokenLabel;
            this.busy = busy;
            this.attention = attention;
            this.progress = progress;
            this.progressError = progressError;
            this.done = done;
            this.doneFailed = doneFailed;
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
                    doneFailed);
        }

        @NonNull
        public WindowItem withAttention(boolean attention) {
            return attention == this.attention ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed);
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
                    failed);
        }

        /** The shell's own progress report; {@link #NO_PERCENTAGE} for indeterminate. */
        @NonNull
        public WindowItem withProgress(int progress, boolean progressError) {
            return progress == this.progress && progressError == this.progressError ? this
                : new WindowItem(label, spokenLabel, busy, attention, progress, progressError, done,
                    doneFailed);
        }

        /** Whether the two would draw the same marks. Labels are compared separately. */
        boolean sameActivity(@NonNull WindowItem other) {
            return busy == other.busy && attention == other.attention && done == other.done
                && doneFailed == other.doneFailed
                && progress == other.progress && progressError == other.progressError;
        }
    }

    /** nf-fa-bell, the mark a window that rang gets after its label. */
    static final String BELL_GLYPH = "\uf0f3";
    /** nf-fa-check, the mark a window whose command finished unseen gets after its label. */
    static final String DONE_GLYPH = "\uf00c";
    /** nf-fa-times, the same mark for a command that finished unseen and failed. */
    static final String FAIL_GLYPH = "\uf00d";

    private final SelectionStrip mTabs;
    @Nullable private OnWindowSelectedListener mSelectionListener;
    @Nullable private OnCreateWindowListener mCreateListener;
    @Nullable private OnEdgeOverswipeListener mEdgeOverswipeListener;
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

    /** Whether the strip offers the plus; a place with nothing to add leaves it out. */
    public void setCreateButtonShown(boolean shown) {
        if (mCreateButtonShown == shown) return;
        mCreateButtonShown = shown;
        for (int i = mTabs.getChildCount() - 1; i >= 0; i--) {
            if (mTabs.getChildAt(i) instanceof AppCompatImageButton) mTabs.removeViewAt(i);
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
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchDownX = mLastTouchX = event.getX();
            mTouchDownY = event.getY();
            mOverswipePx = 0f;
            mGestureHorizontal = false;
            mGestureRejected = false;
            mOverswipeOwned = false;
            mOverswipeInterrupted = false;
            if (mOverswipeVelocity == null) {
                mOverswipeVelocity = android.view.VelocityTracker.obtain();
            }
            mOverswipeVelocity.clear();
            mOverswipeVelocity.addMovement(event);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
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
                } else if (Math.abs(totalX) > mTouchSlop
                    && Math.abs(totalX) > Math.abs(totalY) * 1.2f) {
                    mGestureHorizontal = true;
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
            // Signed: dragging left scrolls right, so what the strip spent cancels the travel out.
            float surplus = dx + (getScrollX() - before);
            if (mGestureHorizontal && !mGestureRejected && Math.abs(surplus) > 0f) {
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
        // sameItems deliberately still compares labels only, so starting a command keeps
        // canReuseTabs true: re-inflating the pill row would also kill the selection slide.
        boolean canReuseTabs = !typefaceChanged && sameItems(mItems, items)
            && mTabs.getChildCount() == items.size() + (mCreateButtonShown ? 1 : 0);
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
            tab.setOnClickListener(v -> {
                if (mSelectionListener != null) mSelectionListener.onWindowSelected(index);
            });
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
        scrollSelectedIntoView(selectedIndex);
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
            ((TextView) child).setText(tabText(item));
            child.setTag(R.id.terminal_window_tab_state, item);
        }
        updateBusyAnimator();
    }

    /**
     * The pill's text with its marks. Windows Terminal is the model for the ring — the tab's icon
     * gives way to a progress ring while the shell works — and a workspace manager's agent badges
     * for the rest: a bell after the title once the shell has rung or asked, a tick once a command
     * finished unseen. Here the icon is the leading Nerd Font process glyph, so the ring is a span
     * over that glyph and the badge is appended.
     */
    @NonNull
    private CharSequence tabText(@NonNull WindowItem item) {
        Context context = getContext();
        String label = item.label;
        int glyphEnd = leadingGlyphEnd(label);
        // A label with no glyph of its own (a bare user name) still gets a ring: a placeholder run
        // is prepended for the span to replace, so a working window always looks like one.
        if (item.busy && glyphEnd == 0) {
            label = "\u25cf " + label;
            glyphEnd = 1;
        }
        // One trailing mark at a time: a window that is asking is not finished.
        String mark = item.attention ? BELL_GLYPH
            : item.done ? (item.doneFailed ? FAIL_GLYPH : DONE_GLYPH) : null;
        if (mark != null) label = label + " " + mark;
        CharSequence spanned = TerminalLabelSymbolSpans.apply(
            com.termux.shared.termux.font.NerdFontSpans.span(context, label), mSymbolMaps);
        if (!item.busy && mark == null) return spanned;
        SpannableStringBuilder text = new SpannableStringBuilder(spanned);
        if (item.busy) {
            text.setSpan(new ProgressRingSpan(item.progressError ? mAttentionColor : mBusyColor,
                    item.progress, dp(1.25f), mLazyMode),
                0, glyphEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (mark != null) {
            text.setSpan(new ForegroundColorSpan(
                    item.attention || item.doneFailed ? mAttentionColor : mBusyColor),
                text.length() - mark.length(), text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
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
        return end < label.length() && label.charAt(end) == ' ' ? end : 0;
    }

    /** Whether any pill is drawing the turning arc, which is the only mark that needs frames. */
    private boolean hasIndeterminateWindow() {
        for (WindowItem item : mItems) {
            if (item.busy && item.progress == WindowItem.NO_PERCENTAGE) return true;
        }
        return false;
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
            WindowItem item = mItems.get(i);
            if (item.busy && item.progress == WindowItem.NO_PERCENTAGE)
                mTabs.getChildAt(i).invalidate();
        }
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
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mWindowVisible = visibility == VISIBLE;
        updateBusyAnimator();
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
        // window row feel loose.
        tab.setPadding(dp(3.5f), 0, dp(3.5f), 0);
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
        tab.setBackground(buildUnselectedChip());
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
        if (previousSelected < 0 || previousSelected >= mItems.size()
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
            animateScrollTo(target);
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
            tab.setAlpha(1f);
            tab.setTranslationX(0f);
        }
    }

    private void applyAnimatedTabSelection(int previousSelected, int selectedIndex, float progress) {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            TextView tab = (TextView) mTabs.getChildAt(i);
            tab.setSelected(i == selectedIndex);
            if (i == previousSelected) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mSelectedTextColor, mUnselectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
            } else if (i == selectedIndex) {
                tab.setTextColor(ColorUtils.blendARGB(
                    mUnselectedTextColor, mSelectedTextColor, progress));
                tab.setTypeface(mTerminalTypeface, Typeface.BOLD);
            } else {
                tab.setTextColor(mUnselectedTextColor);
                tab.setTypeface(mTerminalTypeface, Typeface.NORMAL);
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
            if (child instanceof TextView) {
                child.setBackground(buildUnselectedChip());
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
    }

    /**
     * The progress ring, drawn in the run the process glyph occupies so the label does not move
     * when a window starts working. Reads the clock at draw time: the bar's one clock invalidates
     * the pills that carry a turning arc, and this draws whatever angle the moment calls for, so
     * nothing is stored per frame and a stopped clock simply leaves the arc where it was.
     */
    private static final class ProgressRingSpan extends ReplacementSpan {
        /** Faint full circle under a percentage ring, so 0% is still visibly a ring. */
        private static final int TRACK_ALPHA = 56;
        /** Ring diameter as a share of the text's ascent-to-descent height. */
        private static final float DIAMETER_FRACTION = 0.78f;

        private final int mColor;
        private final int mProgress;
        private final float mStrokePx;
        /** Lazy mode: the arc jumps between {@link WindowActivityRing#LAZY_STEPS} stops. */
        private final boolean mStepped;
        private final RectF mBounds = new RectF();

        ProgressRingSpan(int color, int progress, float strokePx, boolean stepped) {
            mColor = color;
            mProgress = progress;
            mStrokePx = strokePx;
            mStepped = stepped;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                           @Nullable Paint.FontMetricsInt fm) {
            // At least as wide as the glyph it replaces, so the rest of the label stays put; wider
            // when a single narrow cell could not hold a ring anyone can read.
            float glyphWidth = paint.measureText(text, start, end);
            return Math.round(Math.max(glyphWidth, diameter(paint) + mStrokePx * 2f));
        }

        private float diameter(@NonNull Paint paint) {
            return (paint.descent() - paint.ascent()) * DIAMETER_FRACTION;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull Paint paint) {
            float width = getSize(paint, text, start, end, null);
            float diameter = diameter(paint) - mStrokePx;
            float cx = x + width / 2f;
            float cy = y + (paint.ascent() + paint.descent()) / 2f;
            mBounds.set(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f);

            int color = paint.getColor();
            Paint.Style style = paint.getStyle();
            float strokeWidth = paint.getStrokeWidth();
            Paint.Cap cap = paint.getStrokeCap();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(mStrokePx);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (mProgress == WindowItem.NO_PERCENTAGE) {
                float phase = WindowActivityRing.phase(SystemClock.uptimeMillis());
                if (mStepped) phase = WindowActivityRing.steppedPhase(phase, WindowActivityRing.LAZY_STEPS);
                paint.setColor(mColor);
                canvas.drawArc(mBounds, WindowActivityRing.indeterminateStartDeg(phase),
                    WindowActivityRing.INDETERMINATE_SWEEP_DEG, false, paint);
            } else {
                paint.setColor(ColorUtils.setAlphaComponent(mColor, TRACK_ALPHA));
                canvas.drawOval(mBounds, paint);
                paint.setColor(mColor);
                float sweep = WindowActivityRing.determinateSweepDeg(mProgress);
                if (sweep > 0f) canvas.drawArc(mBounds, WindowActivityRing.START_DEG, sweep, false, paint);
            }
            paint.setColor(color);
            paint.setStyle(style);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(cap);
        }
    }

    private void applyTabSurfaceStyle() {
        for (int i = 0; i < mItems.size() && i < mTabs.getChildCount(); i++) {
            mTabs.getChildAt(i).setBackground(buildUnselectedChip());
        }
        applyActivityStates(true);
        applyStableTabSelection();
        mTabs.invalidate();
    }

    private GradientDrawable buildUnselectedChip() {
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(mStatusBarRadiusPx);
        chip.setColor(mUnselectedFillColor);
        chip.setStroke(dp(1), mUnselectedStrokeColor);
        return chip;
    }

    private static RectF lerp(@NonNull RectF start, @NonNull RectF end, float progress) {
        return new RectF(
            start.left + (end.left - start.left) * progress,
            start.top + (end.top - start.top) * progress,
            start.right + (end.right - start.right) * progress,
            start.bottom + (end.bottom - start.bottom) * progress);
    }

    /** Draws one selected surface beneath the pills so it can travel without moving their labels. */
    private static final class SelectionStrip extends LinearLayout {

        private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mAnimatedHighlight = new RectF();
        private final RectF mDrawBounds = new RectF();
        private int mWindowCount;
        private int mSelection = -1;
        private boolean mHasAnimatedHighlight;
        private float mCornerRadius;

        SelectionStrip(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            mFillPaint.setStyle(Paint.Style.FILL);
            mStrokePaint.setStyle(Paint.Style.STROKE);
        }

        void setWindowCount(int windowCount) {
            mWindowCount = Math.max(0, windowCount);
        }

        void setHighlightStyle(int fillColor, int strokeColor, float cornerRadius,
                               float strokeWidth) {
            mFillPaint.setColor(fillColor);
            mStrokePaint.setColor(strokeColor);
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

        boolean copyChildBounds(int index, @NonNull RectF output) {
            if (index < 0 || index >= mWindowCount || index >= getChildCount()) return false;
            View child = getChildAt(index);
            if (child.getWidth() <= 0 || child.getHeight() <= 0) return false;
            output.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
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
            super.dispatchDraw(canvas);
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
        return new WindowItem(processGlyph(processName) + " " + displayText, spokenLabel);
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

    @NonNull
    public static String truncateProcess(@NonNull String process) {
        return middleEllipsize(process, 12);
    }

    /**
     * Middle-truncate a filename while preserving its extension where possible, e.g.
     * {@code terminal…java}. Falls back to plain middle truncation when the extension is too long
     * to keep.
     */
    @NonNull
    public static String truncateFile(@NonNull String name) {
        int maxChars = 13;
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
        return middleEllipsize(leaf, 12);
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
}
