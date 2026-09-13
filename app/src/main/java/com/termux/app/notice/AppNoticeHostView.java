package com.termux.app.notice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The launcher's in-app notice surface: one pill, centred at the top of the screen, holding one
 * message and fading away.
 *
 * <p>Replaces the stock Android {@code Toast} everywhere in the app. A toast is bottom-centre, sits
 * over the shell prompt and the keyboard, cannot be themed and cannot be positioned at all from
 * Android 11 onward. The pill lands inside the terminal's own rim where there is a terminal, and in
 * the row just under whatever chrome the screen has where there is not ({@link AppNoticePlacement}
 * decides which), and it wears the terminal's {@link TerminalDress} either way.
 *
 * <p>Nothing here animates by drawing. The pill is one {@link TerminalDress} background — a single
 * display list, recorded once — and appearing or leaving is alpha and translation on the view
 * itself, which the render thread owns. Earlier versions animated a height fraction through
 * {@code requestLayout()} (a layout pass of the whole activity, per frame), then a draw-time clip
 * with a per-frame {@code invalidate()} and outline update, and drew a hold countdown that
 * re-recorded the pill sixty times a second for up to nine seconds. All of that is gone: between
 * appearing and leaving, a notice costs nothing per frame.
 *
 * <p>Anything raised while a message is up is queued (most recent four) and the pill shows a
 * {@code +N} counter, so a loop that raises twenty notices still resolves in bounded time.
 */
public final class AppNoticeHostView extends LinearLayout {

    /** Design durations, in ms. */
    private static final long IN_MS = 200L;
    private static final long OUT_MS = 160L;
    private static final long SWAP_OUT_MS = 120L;

    /**
     * The holds, named by {@link AppNoticeItem.Hold}. The two the {@code Toast.LENGTH_*} call sites
     * still pass as a boolean keep their aliases, so the fifty-odd plain {@code show(…)} callers do
     * not each have to pick a kind.
     */
    public static final long HOLD_SHORT_MS = AppNoticeItem.Hold.INFO.ms;
    public static final long HOLD_LONG_MS = AppNoticeItem.Hold.REFUSAL.ms;
    /**
     * For a notice whose tap is the only way back — a bulk write with an Undo. A confirmation is
     * gone in a few seconds, which is less time than the surfaces take to finish re-rendering, let
     * alone than deciding the old look was better.
     */
    public static final long HOLD_UNDO_MS = AppNoticeItem.Hold.UNDO.ms;

    /** Beyond this the oldest queued notices are dropped — a burst must still drain. */
    private static final int MAX_QUEUED = 4;

    private static final float MIN_HEIGHT_DP = 36f;
    private static final float MAX_WIDTH_DP = 300f;
    /** How far above its resting place the pill starts, so it drops in rather than blinking on. */
    private static final float RISE_DP = 8f;

    /**
     * How much vertical room the pill is taking at the top of the screen, so the background-process
     * stack below it can follow it down and back up rather than reserve a permanent gap.
     */
    public interface OccupancyListener {
        void onNoticeOccupancyChanged(int heightPx);
    }

    private final Deque<AppNoticeItem> mQueue = new ArrayDeque<>();

    private final AppCompatTextView mGlyph;
    private final AppCompatTextView mTitle;
    private final AppCompatTextView mSub;
    private final AppCompatTextView mCount;

    private final Interpolator mInInterpolator;
    private final Interpolator mOutInterpolator;

    private final int mAccentInfo;
    private final int mAccentError;
    private final int mAccentAttention;

    @Nullable private OccupancyListener mOccupancyListener;
    private int mReportedHeightPx = -1;

    /** Recomputes where the pill sits; run in the frame before one becomes visible. */
    @Nullable private Runnable mPlacementRefresh;

    /**
     * What the terminal is wearing, while there is one on this screen. Null everywhere else, where
     * {@link TerminalDress} reads the same numbers out of stored preferences instead.
     */
    @Nullable private TerminalDress.Source mDressSource;
    @Nullable private TerminalDress mDress;

    /**
     * How far above its resting place the pill starts. The screen's own 8dp drop by default; the
     * whole distance back through the terminal's rim when {@link AppNoticePlacement} has put the
     * pill inside the terminal area, where a clipping frame hides everything above that edge.
     */
    private float mEntranceRisePx;

    @Nullable private Runnable mHoldRunnable;
    @Nullable private AppNoticeItem mActive;
    private int mNaturalHeightPx;
    /** Last text cap pushed into the labels, so measuring never re-triggers their layout. */
    private int mAppliedTextCapPx = -1;

    private final int mTouchSlopPx;
    private final int mSwipeDismissDistancePx;
    @Nullable private VelocityTracker mVelocityTracker;
    private float mTouchDownX;
    private float mTouchDownY;
    private boolean mSwiping;
    private boolean mSwipeDismissed;

    public AppNoticeHostView(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClickable(true);
        setFocusable(false);
        setVisibility(GONE);
        setMinimumHeight(Math.round(dp(MIN_HEIGHT_DP)));
        setPadding(Math.round(dp(14f)), Math.round(dp(8f)),
            Math.round(dp(14f)), Math.round(dp(8f)));

        mInInterpolator = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
        mOutInterpolator = new PathInterpolator(0.4f, 0f, 1f, 1f);

        int onSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mAccentInfo = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorPrimary, onSurface);
        mAccentError = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorError, mAccentInfo);
        mAccentAttention = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary,
            MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSecondary, mAccentError));

        mEntranceRisePx = dp(RISE_DP);
        // Flat, like every other surface that hangs off the terminal. The Material capsule this
        // used to be carried a 3dp shadow, which is what made one notice read as a dialog over the
        // shell while the hint cards beside it read as part of the window.
        setElevation(0f);

        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mSwipeDismissDistancePx = Math.round(dp(56f));

        mGlyph = new AppCompatTextView(context);
        mGlyph.setGravity(Gravity.CENTER);
        mGlyph.setIncludeFontPadding(false);
        mGlyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mGlyph.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        glyphParams.setMarginEnd(Math.round(dp(9f)));
        addView(mGlyph, glyphParams);

        mTitle = new AppCompatTextView(context);
        mTitle.setIncludeFontPadding(false);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        addView(mTitle, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mSub = new AppCompatTextView(context);
        mSub.setIncludeFontPadding(false);
        mSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mSub.setSingleLine(true);
        mSub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.setMarginStart(Math.round(dp(8f)));
        addView(mSub, subParams);

        mCount = new AppCompatTextView(context);
        mCount.setIncludeFontPadding(false);
        mCount.setGravity(Gravity.CENTER);
        mCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        mCount.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCount.setPadding(Math.round(dp(5f)), Math.round(dp(1f)),
            Math.round(dp(5f)), Math.round(dp(1f)));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.setMarginStart(Math.round(dp(8f)));
        addView(mCount, countParams);

        applyDress();
    }

    /**
     * Hands the pill the terminal it is floating over, so the dress follows the corner knob, the
     * opacity slider and the glass tint live rather than being read once at construction. Set by
     * the activity that owns a terminal; every other screen leaves it null.
     */
    public void setTerminalDressSource(@Nullable TerminalDress.Source source) {
        mDressSource = source;
        applyDress();
    }

    /**
     * Re-reads the dress: the surfaces moved, or the pill is about to be seen again. Cut to the
     * height the pill last turned out to be — the radius is the terminal's, capped at half the
     * height, so a message that wraps to two lines is a rounded rectangle rather than a lozenge.
     */
    void applyDress() {
        mDress = TerminalDress.resolve(getContext(), mDressSource);
        mTitle.setTextColor(mDress.textColor);
        mSub.setTextColor(mDress.subTextColor);
        setBackground(mDress.background(getHeight() > 0 ? getHeight() : mNaturalHeightPx));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        // The radius alone, mutated in place: this runs inside a layout pass, and handing the view
        // a whole new background there is how "requestLayout() improperly called during layout"
        // happens.
        if (mDress != null && getBackground() instanceof GradientDrawable)
            ((GradientDrawable) getBackground()).setCornerRadius(mDress.cornerRadiusPx(height));
    }

    /**
     * How far the pill travels on its way in, from {@link AppNoticePlacement}: the whole way back
     * through the terminal's rim where a clipping frame hides it, the screen's own small drop
     * everywhere else.
     */
    void setEntranceRisePx(float risePx) {
        mEntranceRisePx = Math.max(dp(RISE_DP), risePx);
    }

    public void setOccupancyListener(@Nullable OccupancyListener listener) {
        mOccupancyListener = listener;
        mReportedHeightPx = -1;
        notifyOccupancy();
    }

    /** Set by {@link AppNoticePlacement}: recompute the offset before a pill is seen. */
    void setPlacementRefresh(@Nullable Runnable refresh) {
        mPlacementRefresh = refresh;
    }

    /**
     * Queue a notice, showing it immediately when the pill is idle.
     *
     * <p>A fleeting notice (see {@link AppNoticeItem#fleeting}) never holds anything up. Whatever
     * arrives while one is showing takes its place at once — another read-out, so tapping a key five
     * times reads as five read-outs and not as one plus a {@code +4}; or a real notice, which must
     * not wait behind a read-out. A fleeting notice arriving over a real one is dropped: by the time
     * the pill is free it would be describing an action the user has forgotten.
     */
    public void enqueue(@NonNull AppNoticeItem item) {
        AppNoticeItem active = mActive;
        // A read-out and a sticky report both say what is true right now rather than what happened,
        // so neither holds anything up and neither waits: the text swaps in place, the entrance is
        // not replayed, and the pill is never seen to leave and come back for a state that changed.
        boolean swapsInPlace = active != null
            && (active.fleeting || active.isSticky() || item.isSticky());
        if (swapsInPlace) {
            // Nothing can be queued behind either of them, so the queue is empty here.
            mActive = item;
            bind(item);
            settleAtRest();
            startHold(item);
            notifyOccupancy();
            return;
        }
        if (item.fleeting && active != null) return;
        mQueue.addLast(item);
        while (mQueue.size() > MAX_QUEUED) mQueue.removeFirst();
        if (active == null) showNext();
        else updateCount();
    }

    /**
     * Takes down the pending-chord report once the chord has resolved. A no-op when something else
     * has already taken the pill — the report is gone either way, and dropping a real notice to
     * tidy up after it would be worse.
     */
    public void clearSticky() {
        if (mActive == null || !mActive.isSticky()) return;
        leaveAndAdvance();
    }

    /** Puts the pill back where it rests, cancelling whatever it was in the middle of. */
    private void settleAtRest() {
        animate().cancel();
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
    }

    /** Starts (or deliberately does not start) the hold timer for the notice now on the pill. */
    private void startHold(@NonNull AppNoticeItem item) {
        cancelHold();
        if (item.isSticky()) return;
        mHoldRunnable = this::leaveAndAdvance;
        postDelayed(mHoldRunnable, item.durationMs);
    }

    /** For tests: the notice on the pill right now. */
    @androidx.annotation.VisibleForTesting
    @Nullable
    public AppNoticeItem activeItem() {
        return mActive;
    }

    /** Drop everything, without animation. Used when the host activity goes away. */
    public void clear() {
        mQueue.clear();
        cancelHold();
        animate().cancel();
        mActive = null;
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        setVisibility(GONE);
        notifyOccupancy();
    }

    private void showNext() {
        AppNoticeItem item = mQueue.pollFirst();
        if (item == null) {
            hide();
            return;
        }
        mActive = item;
        // The surfaces may have moved since the last notice — the corner knob, the opacity slider,
        // a theme change — and this is the frame it has to be right in.
        applyDress();
        bind(item);
        // Where the terminal's rim and the chrome's bottom edge are, as of this frame — not as of
        // whenever the host was attached, which may have been before either was laid out.
        if (mPlacementRefresh != null) mPlacementRefresh.run();
        // Above anything added to the content root after the pill was: an onboarding sheet, a
        // transition overlay. A notice nobody can see is worse than no notice.
        View outermost = getParent() instanceof ViewGroup ? (View) getParent() : this;
        ViewGroup parent = outermost.getParent() instanceof ViewGroup
            ? (ViewGroup) outermost.getParent() : null;
        if (parent != null && parent.getChildAt(parent.getChildCount() - 1) != outermost)
            outermost.bringToFront();
        setVisibility(VISIBLE);
        appear();
        startHold(item);
        notifyOccupancy();
    }

    /**
     * The accent a notice is drawn in. Attention outranks severity: a shell that has rung its bell
     * in a window the user is not looking at is the one thing on this surface that is waiting for
     * them, and it has to be tellable apart from the run of confirmations at a glance.
     */
    private int accentFor(@NonNull AppNoticeItem item) {
        if (item.attention) return mAccentAttention;
        return item.kind == AppNoticeItem.Kind.ERROR || item.kind == AppNoticeItem.Kind.WARNING
            ? mAccentError : mAccentInfo;
    }

    private void bind(@NonNull AppNoticeItem item) {
        int accent = accentFor(item);

        // A mark only where it means something: a confirmation, a warning, a failure, an undo. The
        // plain-message case — most of the app's notices — is just the sentence, and a decorative
        // chevron in front of it made a simple pill look like a widget.
        boolean marked = item.attention || item.kind != AppNoticeItem.Kind.INFO
            || !TextUtils.isEmpty(item.glyph);
        mGlyph.setVisibility(marked ? VISIBLE : GONE);
        if (marked) {
            // Spanned so a nerd-symbol glyph (the attention bell) renders from the bundled symbol
            // face; plain glyphs pass through untouched.
            mGlyph.setText(com.termux.shared.termux.font.NerdFontSpans.span(
                getContext(), item.resolvedGlyph()));
            mGlyph.setTextColor(accent);
        }

        mTitle.setText(item.title);
        boolean hasSub = !TextUtils.isEmpty(item.sub);
        mSub.setText(hasSub ? item.sub : "");
        mSub.setVisibility(hasSub ? VISIBLE : GONE);
        // A bare message has the whole pill to itself and may wrap; paired with a subtitle the two
        // share one line and the title is the half that must stay readable, so it holds its width
        // and the subtitle is what gets clipped.
        mTitle.setSingleLine(hasSub);
        mTitle.setMaxLines(hasSub ? 1 : 2);

        // A notice you can act on says so: the pointer cursor equivalent here is the tap target
        // being announced, since the pill looks identical either way.
        setContentDescription(item.onActivate == null ? item.title
            : item.title + " — " + (TextUtils.isEmpty(item.actionHint)
                ? getContext().getString(R.string.notice_tap_to_open) : item.actionHint));
        updateCount();
    }

    private void updateCount() {
        int queued = mQueue.size();
        if (queued <= 0) {
            mCount.setVisibility(GONE);
            return;
        }
        int accent = mActive == null ? mAccentInfo : accentFor(mActive);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(7f));
        pill.setColor(ColorUtils.setAlphaComponent(accent, 51));
        mCount.setBackground(pill);
        mCount.setTextColor(accent);
        mCount.setText("+" + queued);
        mCount.setVisibility(VISIBLE);
    }

    /**
     * Drops in from above its resting place: the screen's own small drop, or — inside the terminal
     * area, where the frame around the pill clips at the rim — the whole way back through that
     * edge, so the notice arrives out of the terminal window rather than on top of it. One property
     * animation either way, no drawing of our own.
     */
    private void appear() {
        animate().cancel();
        setTranslationX(0f);
        setAlpha(0f);
        setTranslationY(-mEntranceRisePx);
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(IN_MS)
            .setInterpolator(mInInterpolator)
            .withLayer()
            .start();
    }

    /** Fades the current message out, then shows the next one or leaves the row empty. */
    private void leaveAndAdvance() {
        mHoldRunnable = null;
        if (mActive == null) return;
        // It stops holding the pill the moment it starts leaving, not when the fade finishes.
        // Anything raised during those 160ms then gets a clean entrance of its own instead of
        // being swapped onto a view that is already on its way out — which is what a resolved key
        // chord does: the report comes down and the read-out of what the chord ran follows it by a
        // frame or two.
        mActive = null;
        boolean more = !mQueue.isEmpty();
        notifyOccupancy();
        animate().cancel();
        animate()
            .alpha(0f)
            .translationY(-dp(RISE_DP * 0.5f))
            .setDuration(more ? SWAP_OUT_MS : OUT_MS)
            .setInterpolator(mOutInterpolator)
            .withLayer()
            .withEndAction(() -> {
                // Something took the pill while this one was fading; it owns the view now.
                if (mActive != null) return;
                if (mQueue.isEmpty()) hide();
                else showNext();
            })
            .start();
    }

    private void hide() {
        mActive = null;
        cancelHold();
        setVisibility(GONE);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
        notifyOccupancy();
    }

    /**
     * A tap takes the user to whatever the notice is about — the pane or window it came from — and
     * then gets out of the way. With nowhere to go, the tap is just an early dismiss.
     */
    private void activateOrDismiss() {
        AppNoticeItem active = mActive;
        if (active == null) return;
        cancelHold();
        Runnable action = active.onActivate;
        leaveAndAdvance();
        if (action != null) action.run();
    }

    /**
     * Swipe to dismiss, either way: the pill is centred, so neither direction is "off the edge it
     * came from". Interactive rather than only tap-and-wait because these are frequent and one may
     * well be covering the top of a shell's output at the moment the user wants to read it. Only
     * horizontal travel counts — a vertical drag here belongs to the status bar's own gesture.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = event.getRawX();
                mTouchDownY = event.getRawY();
                mSwiping = false;
                mSwipeDismissed = false;
                if (mVelocityTracker != null) mVelocityTracker.recycle();
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(event);
                // The hold must not expire mid-drag and yank the pill out from under the finger.
                cancelHold();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
                float dx = event.getRawX() - mTouchDownX;
                float dy = event.getRawY() - mTouchDownY;
                if (!mSwiping && Math.abs(dx) > mTouchSlopPx && Math.abs(dx) > Math.abs(dy)) {
                    mSwiping = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (mSwiping) {
                    setTranslationX(dx);
                    setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / (mSwipeDismissDistancePx * 2f)));
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = event.getRawX() - mTouchDownX;
                if (mSwiping) {
                    float velocity = 0f;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.addMovement(event);
                        mVelocityTracker.computeCurrentVelocity(1000);
                        velocity = mVelocityTracker.getXVelocity();
                    }
                    if (Math.abs(dx) > mSwipeDismissDistancePx
                        || Math.abs(velocity) > mSwipeDismissDistancePx * 8f) {
                        swipeOut(dx >= 0 ? 1f : -1f);
                    } else {
                        settleBack();
                    }
                } else if (Math.abs(dx) <= mTouchSlopPx) {
                    activateOrDismiss();
                } else {
                    settleBack();
                }
                releaseVelocityTracker();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (mSwiping) settleBack(); else resumeHold();
                releaseVelocityTracker();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /** Finish the throw, then drop the notice and whatever is behind it. */
    private void swipeOut(float direction) {
        if (mSwipeDismissed) return;
        mSwipeDismissed = true;
        animate().cancel();
        animate()
            .translationX(direction * (getWidth() + mSwipeDismissDistancePx))
            .alpha(0f)
            .setDuration(OUT_MS)
            .setInterpolator(mOutInterpolator)
            .withLayer()
            .withEndAction(() -> {
                // A swipe dismisses the whole burst, not just the message on top: the user has said
                // they are done with it, and popping the queue one swipe at a time would fight them.
                mQueue.clear();
                hide();
            })
            .start();
    }

    private void settleBack() {
        mSwiping = false;
        animate().cancel();
        animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(OUT_MS)
            .setInterpolator(mInInterpolator)
            .withLayer()
            .withEndAction(this::resumeHold)
            .start();
    }

    /** Restart the hold after a touch that did not dismiss. */
    private void resumeHold() {
        if (mActive == null) return;
        startHold(mActive);
    }

    private void cancelHold() {
        if (mHoldRunnable != null) {
            removeCallbacks(mHoldRunnable);
            mHoldRunnable = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int available = MeasureSpec.getSize(widthMeasureSpec);
        int cap = Math.round(dp(MAX_WIDTH_DP));
        if (available > 0) cap = Math.min(cap, Math.round(available * 0.82f));
        // Only when it actually changes: setMaxWidth requests a layout, and doing that from inside
        // a measure pass is the "requestLayout() improperly called during layout" warning.
        if (cap != mAppliedTextCapPx) {
            mAppliedTextCapPx = cap;
            mTitle.setMaxWidth(cap);
            mSub.setMaxWidth(cap);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mNaturalHeightPx = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // The height the column below has to make room for is known once, here, rather than being
        // recomputed on every animation frame: the consumer animates its own slide, and handing it
        // a new target sixty times a second only cancelled and restarted that animation.
        notifyOccupancy();
    }

    private void notifyOccupancy() {
        if (mOccupancyListener == null) return;
        int height = mActive != null && getVisibility() == VISIBLE ? mNaturalHeightPx : 0;
        if (height == mReportedHeightPx) return;
        mReportedHeightPx = height;
        mOccupancyListener.onNoticeOccupancyChanged(height);
    }

    /**
     * Where the pill sits inside its frame: centred at the top of whatever band
     * {@link AppNoticePlacement} has put that frame in. Side margins so a long message on a narrow
     * screen still reads as a pill with air around it rather than a bar.
     */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        float density = context.getResources().getDisplayMetrics().density;
        params.leftMargin = Math.round(16 * density);
        params.rightMargin = Math.round(16 * density);
        return params;
    }

    /**
     * The band the pill lives in, and the reason there is a frame at all: inside the terminal area
     * it clips at the terminal's own top edge, so a pill sliding in from above it is hidden until
     * it clears that rim instead of crossing the window bar and the status bar on its way down.
     * Off the terminal it clips nothing and simply carries the row.
     */
    @NonNull
    public static FrameLayout buildFrame(@NonNull Context context) {
        FrameLayout frame = new FrameLayout(context);
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        return frame;
    }

    /** The layout params the frame is added to the window's content root with. */
    @NonNull
    @SuppressLint("RtlHardcoded")
    public static FrameLayout.LayoutParams buildFrameLayoutParams() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.LEFT);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
