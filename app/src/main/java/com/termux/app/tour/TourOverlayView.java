package com.termux.app.tour;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.notice.TerminalDress;

import java.util.ArrayList;
import java.util.List;

/**
 * The run's only view: a glow around the control the card is about, a finger tracing the gesture
 * once, and the card itself.
 *
 * <p>It never takes a touch. The launcher is the home screen and the tour is teaching gestures on
 * live chrome, so the user has to be able to actually perform the gesture while the card is up —
 * the overlay is not clickable, not focusable, and {@link #onTouchEvent} refuses every event so
 * the sibling below it gets the stream. The only thing that does take a touch is the card's own
 * text button, which is a child view and so is reached before this view is consulted.
 *
 * <p>The glow is {@link FocusOutlineRenderer}'s rounded-rect focus treatment, the same one the
 * dock and terminal search wear, and the card is the notice chip's dress — fill, hairline and the
 * terminal's own radius, flat, from {@link TerminalDress}.
 *
 * <p>A target that cannot be measured is normal: the card shows without a glow rather than
 * pointing somewhere wrong.
 */
public final class TourOverlayView extends FrameLayout {

    /** The card's buttons: whichever ones the controller says this card offers. */
    public interface Callbacks {
        /** One of the card's buttons was tapped; what it means is the controller's business. */
        void onTourActionTapped(@NonNull TourAction action);

        /**
         * The Copy button beside a command on the closing card.
         *
         * @param commandRes the command that button stands beside
         */
        void onTourCopyCommandTapped(int commandRes);

        /** The closing card's Read the docs link. */
        void onTourDocsTapped();
    }

    private static final long CARD_IN_MS = 200L;
    private static final float CARD_RISE_DP = 8f;
    private static final float CARD_MAX_WIDTH_DP = 300f;
    private static final float CARD_SIDE_MARGIN_DP = 16f;
    /** The gap between the control and the card's pointer. */
    private static final float CARD_GAP_DP = 8f;
    private static final float POINTER_HEIGHT_DP = 7f;
    private static final float POINTER_HALF_WIDTH_DP = 9f;
    /**
     * How long a stage keeps asking for a control that could not be measured when it arrived.
     *
     * <p>The overlay re-measures on every global layout, which is enough for everything the
     * chrome lays out — but not for a control revealed by an animation that deliberately walks no
     * layout at all. The × on the window chip is exactly that: it opens as a 180 ms width
     * animation that offsets pixels by hand, so between the tap that reveals it and its full width
     * there is no layout pass for the overlay to hear. This window covers that open with room to
     * spare, and ends whether or not the control ever turned up.
     */
    private static final long TARGET_RETRY_MS = 1500L;
    /** How often the retry above asks again: often enough to follow a reveal, rarely enough. */
    private static final long TARGET_RETRY_INTERVAL_MS = 32L;
    /**
     * The gap the glow keeps outside the control. Tight on purpose: the ring also carries a
     * blurred halo outside this rect, and on a control flush with the edge of the screen every dp
     * of it is a dp of the halo hanging off the display.
     */
    private static final float GLOW_PADDING_DP = 2f;
    private static final float GLOW_RADIUS_DP = 12f;
    private static final float FINGER_RADIUS_DP = 9f;
    private static final float FINGER_TRAIL_WIDTH_DP = 3f;
    /** Stands in for "as tall as it likes": a measure spec carries no unbounded size of its own. */
    private static final int UNBOUNDED_PX = 1 << 24;

    private final float mDensity;
    private final TerminalDress mDress;
    private final Paint mFingerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPointerFill = new Path();
    private final Path mPointerEdges = new Path();
    private final RectF mGlowRect = new RectF();
    /** Where the finger cue's centre may be, so its own circle stays on the screen. */
    private final RectF mCueBounds = new RectF();
    private final float[] mFingerPoint = new float[2];
    private final float[] mTrailPoint = new float[2];

    private final LinearLayout mCard;
    /** The small line above the title, on the two cards that carry one. */
    private final TextView mKicker;
    /** The title, on the two cards that carry one. */
    private final TextView mTitle;
    private final TextView mCopy;
    /** The picture under the sentence, on the one card that shows what it is asking about. */
    private final ImageView mImage;
    private final ScrollView mBodyScroll;
    private final LinearLayout mSections;
    private final TextView mDocsLink;
    private final LinearLayout mButtonRow;
    /** The card's action buttons, rebuilt whenever the card offers a different set. */
    private final List<TextView> mActionButtons = new ArrayList<>();
    /** What the buttons currently on the card stand for, in the order they are read. */
    private final List<TourAction> mActions = new ArrayList<>();
    /** Every Copy button on the card, so a rebuild takes their "Copied" acknowledgement back. */
    private final List<TextView> mCopyButtons = new ArrayList<>();

    @Nullable private Callbacks mCallbacks;
    @Nullable private TourTargets mTargets;
    @Nullable private TourStep mStep;
    @Nullable private Rect mTargetRect;
    /**
     * The last control this card was actually placed against, kept so a stage whose own control
     * cannot be measured yet stays where the stage before it stood instead of jumping to the
     * middle of the overlay. Cleared when a different card comes up.
     */
    @Nullable private Rect mLastAnchorRect;
    /** The launcher's own top bar, measured only while the card rests at the top of the screen. */
    @Nullable private Rect mTopBarRect;
    @Nullable private ValueAnimator mTrace;
    @Nullable private TourCardPlacement mPlacement;
    private int mSystemInsetTop;
    private int mSystemInsetBottom;
    /** Why the last measurement found no target, kept for the log rather than for the drawing. */
    @NonNull private String mMissReason = "none";

    private int mStage;
    private int mAccent;
    private float mTraceProgress = 1f;
    /** How far through its chord a chord card's glow has walked; ignored by every other card. */
    private int mChordGlowIndex;
    /** How tall the closing card's sections may grow before they start scrolling inside it. */
    private int mScrollMaxHeight = UNBOUNDED_PX;
    private int mPresentation = TourCardVisibility.NORMAL;
    /** When the current stage stops asking again for a control it could not measure. */
    private long mRetryUntil;
    @Nullable private Runnable mRetry;
    /** The edition the sections on the card were built for, or null while it carries none. */
    @Nullable private TourEdition mSectionsEdition;

    public TourOverlayView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        // Passive by construction: the gesture the card is asking for belongs to the chrome below.
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mAccent = FocusOutlineRenderer.resolveAccent(this);

        mDress = TerminalDress.stored(context);
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.VERTICAL);
        mCard.setBackground(mDress.background(0));
        mCard.setElevation(0f);
        mCard.setPadding(dp(14), dp(10), dp(14), dp(14));
        mCard.setClickable(false);
        mCard.setFocusable(false);

        // The shell the run opens and closes on: a small line, a title, then the sentence. Every
        // other card is one sentence and carries neither, so both are gone rather than empty.
        mKicker = new TextView(context);
        mKicker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        mKicker.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mKicker.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 150));
        mKicker.setLetterSpacing(0.08f);
        mKicker.setAllCaps(true);
        mKicker.setVisibility(GONE);
        mCard.addView(mKicker, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mTitle = new TextView(context);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mTitle.setTextColor(mDress.textColor);
        mTitle.setVisibility(GONE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(2);
        titleParams.bottomMargin = dp(4);
        mCard.addView(mTitle, titleParams);

        mCopy = new TextView(context);
        mCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        mCopy.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCopy.setTextColor(mDress.textColor);
        mCopy.setLineSpacing(dp(2), 1f);
        mCard.addView(mCopy, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The picture under the sentence, on the one card that shows the thing it is asking
        // about: the row of keys this release ships. It takes the card's full width, keeps the
        // strip's own proportions and is drawn as it was photographed — a tinted photograph of a
        // key row is a photograph of a different key row.
        mImage = new ImageView(context);
        mImage.setAdjustViewBounds(true);
        mImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mImage.setVisibility(GONE);
        mImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    mDress.cornerRadiusPx(view.getHeight()));
            }
        });
        mImage.setClipToOutline(true);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageParams.topMargin = dp(10);
        imageParams.bottomMargin = dp(2);
        mCard.addView(mImage, imageParams);

        // The closing card's sections. Only that card has any, so the whole column is gone for
        // the other eight rather than empty, and it scrolls rather than growing off the screen:
        // three headings, three sentences and two commands do not fit a short phone at 1.3x text
        // with the keyboard up.
        mSections = new LinearLayout(context);
        mSections.setOrientation(LinearLayout.VERTICAL);
        mBodyScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.max(0, mScrollMaxHeight), MeasureSpec.AT_MOST));
            }
        };
        mBodyScroll.setVerticalScrollBarEnabled(false);
        mBodyScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mBodyScroll.setClipToPadding(false);
        mBodyScroll.setVisibility(GONE);
        mBodyScroll.addView(mSections, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        mCard.addView(mBodyScroll, bodyParams);

        mButtonRow = new LinearLayout(context);
        mButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        mButtonRow.setGravity(Gravity.END);

        // The docs link shares the button row: it leads, takes the slack, and Start using keeps
        // the trailing edge, so the closing card ends on one row of buttons.
        mDocsLink = textButton(context, view -> {
            if (mCallbacks != null) mCallbacks.onTourDocsTapped();
        });
        mDocsLink.setVisibility(GONE);
        addDocsLink();

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.END;
        buttonParams.topMargin = dp(8);
        mCard.addView(mButtonRow, buttonParams);

        addView(mCard, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** One of the card's text buttons: the only children of this view that take a touch. */
    @NonNull
    private TextView textButton(@NonNull Context context, @NonNull OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(mAccent);
        button.setAllCaps(false);
        // A TextView sits its text at the top; the action buttons are 48dp tall, so without this
        // the label rides the top edge and the rest of the box hangs empty beneath it.
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setMinHeight(dp(44f));
        button.setMinWidth(dp(48f));
        button.setBackground(buttonBackground());
        button.setOnClickListener(onClick);
        return button;
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void setTargets(@Nullable TourTargets targets) {
        mTargets = targets;
    }

    /**
     * The system bars' keep-out, so a card that has nothing to anchor to, or that is clamped to an
     * end of the overlay, does not come to rest under the status bar or the gesture bar. The
     * overlay fills the whole window, so without this the two ends of it are the wrong place.
     */
    public void setSystemBarInsets(int top, int bottom) {
        if (mSystemInsetTop == top && mSystemInsetBottom == bottom) return;
        mSystemInsetTop = Math.max(0, top);
        mSystemInsetBottom = Math.max(0, bottom);
        requestLayout();
        invalidate();
    }

    /** Shows a card with the buttons its own kind offers. */
    public void showStep(@NonNull TourStep step, int stage) {
        showStep(step, stage, step.actions());
    }

    /**
     * Shows a card, with the buttons the controller says it offers, and traces its gesture once.
     * The buttons are not read off the card: the same lesson practised from help offers the two
     * that leave practice rather than the three that move the run.
     */
    public void showStep(@NonNull TourStep step, int stage, @NonNull List<TourAction> actions) {
        boolean sameCard = mStep != null && mStep.id.equals(step.id) && mStage == stage;
        // Only within one card: the position a card whose control is missing falls back to is the
        // one the stage before it stood at, never wherever the card before it happened to be.
        if (mStep == null || !mStep.id.equals(step.id)) mLastAnchorRect = null;
        mStep = step;
        mStage = stage;
        if (!sameCard) mChordGlowIndex = 0;
        applyCopy();
        applyActions(actions);
        boolean closing = step.isClosingCard();
        if (closing) showClosingSections();
        else {
            mBodyScroll.setVisibility(GONE);
            mDocsLink.setVisibility(GONE);
        }
        applyPresentation();
        if (!sameCard && getVisibility() == VISIBLE) animateCardIn();
        // Every stage starts a fresh window of asking for its control: the one the window card
        // ends on is revealed by an animation that lands after the stage does.
        armTargetRetry();
        refreshTarget();
        startTrace();
    }

    /**
     * Whether the card draws against its control, at the top of the screen, or not at all.
     * {@link TourCardVisibility} decides; this applies the answer.
     */
    public void setPresentation(int presentation) {
        if (mPresentation == presentation) return;
        boolean wasHidden = mPresentation == TourCardVisibility.HIDDEN;
        mPresentation = presentation;
        applyPresentation();
        if (wasHidden && getVisibility() == VISIBLE) {
            animateCardIn();
            startTrace();
        }
        // A card coming back from behind chrome is a card whose control may still be arriving.
        armTargetRetry();
        refreshTarget();
        requestLayout();
        invalidate();
    }

    /**
     * The card's sentence: the stage's own, or the way back to the terminal when the card is
     * being shown away from the place it is taught on.
     */
    private void applyCopy() {
        if (mStep == null) return;
        boolean away = mPresentation == TourCardVisibility.AWAY;
        mCopy.setText(away ? R.string.tour_card_return_to_terminal : mStep.copyResAt(mStage));
        // The card asking the way back to the terminal is one sentence, whatever card it stands
        // for: a title over it would be a title over something the user is not being told.
        boolean picture = !away && mStep.hasImage();
        mImage.setVisibility(picture ? VISIBLE : GONE);
        if (picture) mImage.setImageResource(mStep.imageRes);
        else mImage.setImageDrawable(null);
        boolean shell = !away && mStep.hasTitle();
        mKicker.setVisibility(shell && mStep.kickerRes != 0 ? VISIBLE : GONE);
        if (shell && mStep.kickerRes != 0) mKicker.setText(mStep.kickerRes);
        mTitle.setVisibility(shell ? VISIBLE : GONE);
        if (shell) mTitle.setText(mStep.titleRes);
    }

    /**
     * The control this card glows right now. Nothing while the card is away from the terminal:
     * the control it names is on another place, and a stale rect for it is exactly the glow over
     * nothing this presentation exists to avoid.
     */
    @NonNull
    private String glowTargetId() {
        if (mStep == null || mPresentation == TourCardVisibility.AWAY) return TourTargets.NONE;
        return mStep.targetIdAt(glowIndex());
    }

    private void applyPresentation() {
        applyCopy();
        boolean hidden = mStep == null || mPresentation == TourCardVisibility.HIDDEN;
        if (hidden) {
            stopTrace();
            stopTargetRetry();
        }
        setVisibility(hidden ? GONE : VISIBLE);
    }

    /** Re-measures the control the card points at; cheap enough for every layout pass. */
    public void refreshTarget() {
        if (mStep == null) return;
        String targetId = glowTargetId();
        Rect updated = null;
        Rect topBar = null;
        String reason = "no targets host";
        if (mTargets != null) {
            // The ceiling a card that rests at the top sits under, asked for on the same pass as
            // everything else so a keyboard, a rotation or a place change moves the card with it.
            // Measured whatever the presentation: a card whose own control cannot be found rests
            // there too, and it finds that out after this. The card's own control is still
            // measured below — resting at the top does not mean glowing nothing.
            topBar = mTargets.rectFor(TourTargets.STATUS_BAR);
        }
        if (mTargets != null) {
            updated = mTargets.rectFor(targetId);
            reason = updated == null ? mTargets.lastMissReason() : "none";
        }
        boolean moved = updated == null ? mTargetRect != null : !updated.equals(mTargetRect);
        moved |= topBar == null ? mTopBarRect != null : !topBar.equals(mTopBarRect);
        boolean reasonChanged = !reason.equals(mMissReason);
        mTargetRect = updated;
        if (updated != null && !updated.isEmpty()) mLastAnchorRect = new Rect(updated);
        mTopBarRect = topBar;
        mMissReason = reason;
        // Logged on the edge, not per layout pass: this runs on every global layout, and the
        // keyboard alone produces dozens of them.
        if (updated == null && (moved || reasonChanged) && TourLog.enabled()) {
            TourLog.d("card " + mStep.id + ":" + mStage + " has no glow — target \"" + targetId
                + "\": " + reason);
        }
        if (moved) {
            // The card's own size does not depend on the target, only its position does, so the
            // card is laid out again here rather than through a window traversal: this is called
            // on every frame of a reveal, and a requestLayout a frame is the per-frame work the
            // chrome under the overlay goes out of its way not to do.
            layoutCard();
            invalidate();
        }
    }

    /**
     * Starts this stage asking again for a control the chrome could not measure yet.
     *
     * <p>Global layout is the overlay's usual "something moved" hook and is enough for everything
     * the chrome lays out. It is not enough for a control revealed by an animation that walks no
     * layout — the × on the window chip — so a stage that names a control keeps asking for a
     * little while after it arrives, and stops as soon as the window is up.
     */
    private void armTargetRetry() {
        mRetryUntil = android.os.SystemClock.uptimeMillis() + TARGET_RETRY_MS;
        scheduleTargetRetry();
    }

    private void scheduleTargetRetry() {
        if (mRetry != null) return;
        if (mStep == null || mPresentation != TourCardVisibility.NORMAL) return;
        if (TourTargets.NONE.equals(glowTargetId())) return;
        if (android.os.SystemClock.uptimeMillis() >= mRetryUntil) return;
        mRetry = () -> {
            mRetry = null;
            refreshTarget();
            scheduleTargetRetry();
        };
        postDelayed(mRetry, TARGET_RETRY_INTERVAL_MS);
    }

    private void stopTargetRetry() {
        if (mRetry != null) removeCallbacks(mRetry);
        mRetry = null;
        mRetryUntil = 0L;
    }

    /**
     * What the card stands against: the control this stage names when it can be measured, and
     * otherwise the one the stage before it stood against, so a control that is still arriving
     * does not send the card to the middle of the overlay. The glow is not moved with it —
     * {@link #onDraw} draws only around a control that really was measured.
     */
    @Nullable
    private Rect anchorRect() {
        if (mStep == null) return null;
        String targetId = glowTargetId();
        boolean namesAControl = !TourTargets.NONE.equals(targetId);
        boolean measured = mTargetRect != null && !mTargetRect.isEmpty();
        // The swipe that opens the palette is performed on the space bar, and the space bar is
        // not there at all while the keyboard is down. That is not a control still arriving, so
        // the card does not keep the last one's place: it stands against the key that brings the
        // keyboard back, and against nothing when even that is gone.
        if (namesAControl && !measured && TourTargets.SPACE_BAR.equals(targetId)) {
            Rect instead = mTargets == null
                ? null : mTargets.rectFor(TourTargets.KEYBOARD_TOGGLE_KEY);
            return instead != null && !instead.isEmpty() ? instead : null;
        }
        return TourCardPlacement.anchorRect(namesAControl, mTargetRect, mLastAnchorRect);
    }

    /** The side of its control this card asks to stand on; almost every card asks for none. */
    private int preferredCardSide() {
        if (mStep == null) return TourCardPlacement.SIDE_AUTO;
        switch (mStep.placement) {
            case ABOVE: return TourCardPlacement.SIDE_ABOVE;
            case BELOW: return TourCardPlacement.SIDE_BELOW;
            default: return TourCardPlacement.SIDE_AUTO;
        }
    }

    /** The control the card is glowing right now, for the log. Null when it has none. */
    @Nullable
    Rect currentTargetRect() {
        return mTargetRect;
    }

    /**
     * The control the card is glowing, for the log. Not the step's first target: a chord card's
     * glow is chosen by the keyboard, and the log is the only way to tell which key it landed on.
     */
    @NonNull
    String currentTargetId() {
        if (mStep == null) return TourTargets.NONE;
        return mPresentation == TourCardVisibility.NORMAL
            ? mStep.targetIdAt(glowIndex()) : TourTargets.NONE;
    }

    /** Why {@link #currentTargetRect()} is null, for the log. */
    @NonNull
    String currentMissReason() {
        return mMissReason;
    }

    /**
     * Which key of a chord card to glow. The chord cards walk Ctrl, then Alt, then the key itself
     * as the user latches each modifier, so their glow is driven by the keyboard rather than by
     * the stage — they have one signal and three or four keys to point at.
     */
    public void setChordGlowIndex(int index) {
        int bounded = Math.max(0, index);
        if (mChordGlowIndex == bounded) return;
        mChordGlowIndex = bounded;
        refreshTarget();
    }

    /** The target slot the card is glowing: the chord's key for a chord card, the stage otherwise. */
    private int glowIndex() {
        return mStep != null && mStep.chordGlow ? mChordGlowIndex : mStage;
    }

    /** Takes the card down and stops the trace. */
    public void dismiss() {
        stopTrace();
        stopTargetRetry();
        mStep = null;
        mTargetRect = null;
        mLastAnchorRect = null;
        mTopBarRect = null;
        mChordGlowIndex = 0;
        mPlacement = null;
        mMissReason = "none";
        mPresentation = TourCardVisibility.NORMAL;
        setVisibility(GONE);
    }

    /** The whole point: every touch belongs to the chrome under the overlay. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutCard();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mStep == null) return;
        drawCardPointer(canvas);
        // Only a card standing against its control wears the marks. A compact card is at the top
        // of the screen because a surface is covering that control, and a glow on what is behind
        // that surface is a glow on something the user cannot see.
        if (mPresentation != TourCardVisibility.NORMAL) return;
        if (mTargetRect == null || mTargetRect.isEmpty()) return;
        drawGlow(canvas);
        drawFinger(canvas);
    }

    private void drawGlow(@NonNull Canvas canvas) {
        TourGlowGeometry.glowRect(mTargetRect, GLOW_PADDING_DP * mDensity,
            FocusOutlineRenderer.fallbackOuterReachPx(mDensity), getWidth(), getHeight(),
            TourGlowGeometry.EDGE_MARGIN_DP * mDensity, mGlowRect);
        FocusOutlineRenderer.drawRoundRectFallback(canvas, mGlowRect, GLOW_RADIUS_DP * mDensity,
            mAccent, 1f, 1f, mDensity);
    }

    private void drawFinger(@NonNull Canvas canvas) {
        TourGesture gesture = mStep.gestureAt(mStage);
        if (gesture == TourGesture.NONE || mTraceProgress >= 1f) return;
        // The widest ring the cue ever draws is the hold's halo, at 1.9 times the finger's own
        // radius; the centre is kept in far enough that even that stays on the screen.
        TourGlowGeometry.cueBounds(getWidth(), getHeight(), FINGER_RADIUS_DP * 1.9f * mDensity,
            TourGlowGeometry.EDGE_MARGIN_DP * mDensity, mCueBounds);
        TourFingerPainter.draw(canvas, mFingerPaint, gesture, mTargetRect.left, mTargetRect.top,
            mTargetRect.right, mTargetRect.bottom, mDensity, mTraceProgress, mAccent,
            mFingerPoint, mTrailPoint, mCueBounds);
    }

    /**
     * Against the control: centred on it, below it when it is in the top half of the overlay and
     * above it otherwise, flipped when that side has no room, and clamped inside the margins. The
     * arithmetic is {@link TourCardPlacement}'s; this only applies the answer.
     */
    private void layoutCard() {
        if (mStep == null || mCard.getVisibility() == GONE) return;
        int width = mCard.getMeasuredWidth();
        int height = mCard.getMeasuredHeight();
        if (width <= 0 || height <= 0) return;
        int margin = dp(CARD_SIDE_MARGIN_DP);
        // Away from the place it is taught on, the card stands against nothing by definition: the
        // control it names is on another page of the wall.
        Rect anchor = mPresentation == TourCardVisibility.AWAY ? null : anchorRect();
        // A card that names a control and has nothing to stand against rests under the launcher's
        // own top bar. The middle of the screen is where the missing control would have been, and
        // a card sitting there reads as pointing at it.
        boolean atTheTop = anchor == null
            && (mPresentation != TourCardVisibility.NORMAL
                || !TourTargets.NONE.equals(glowTargetId()));
        TourCardPlacement placement = atTheTop
            ? TourCardPlacement.placeUnderStatusBar(getWidth(), getHeight(), width, height,
                margin, margin + mSystemInsetTop, margin + mSystemInsetBottom, mTopBarRect,
                dp(CARD_GAP_DP))
            : TourCardPlacement.place(getWidth(), getHeight(),
                width, height, anchor, margin, margin + mSystemInsetTop,
                margin + mSystemInsetBottom, dp(CARD_GAP_DP), dp(POINTER_HEIGHT_DP),
                dp(POINTER_HALF_WIDTH_DP), preferredCardSide());
        mPlacement = placement;
        mCard.layout(placement.left, placement.top, placement.left + width,
            placement.top + height);
        GradientDrawable background = mCard.getBackground() instanceof GradientDrawable
            ? (GradientDrawable) mCard.getBackground() : null;
        if (background != null)
            background.setCornerRadius(mDress.cornerRadiusPx(height));
    }

    /**
     * The card's pointer: the same fill and the same hairline the card itself wears, its base
     * tucked a pixel under the card so the two share no visible seam.
     */
    private void drawCardPointer(@NonNull Canvas canvas) {
        TourCardPlacement placement = mPlacement;
        if (placement == null || !placement.hasPointer() || mCard.getVisibility() == GONE) return;
        float height = POINTER_HEIGHT_DP * mDensity;
        float halfWidth = POINTER_HALF_WIDTH_DP * mDensity;
        boolean up = placement.pointerEdge == TourCardPlacement.POINTER_TOP;
        // The card rises into place; the pointer travels and fades with it rather than sitting
        // detached under a card that has not arrived yet.
        float offset = mCard.getTranslationY();
        float alpha = mCard.getAlpha();
        if (alpha <= 0.01f) return;
        float base = (up ? mCard.getTop() + 1f : mCard.getBottom() - 1f) + offset;
        float tip = up ? base - height : base + height;
        float centerX = placement.pointerCenterX;

        mPointerFill.reset();
        mPointerFill.moveTo(centerX - halfWidth, base);
        mPointerFill.lineTo(centerX, tip);
        mPointerFill.lineTo(centerX + halfWidth, base);
        mPointerFill.close();
        mPointerPaint.setStyle(Paint.Style.FILL);
        mPointerPaint.setColor(mDress.fillColor);
        mPointerPaint.setAlpha(Math.round(Color.alpha(mDress.fillColor) * alpha));
        canvas.drawPath(mPointerFill, mPointerPaint);

        // Only the two slanted sides: the base is inside the card, where there is no edge to draw.
        mPointerEdges.reset();
        mPointerEdges.moveTo(centerX - halfWidth, base);
        mPointerEdges.lineTo(centerX, tip);
        mPointerEdges.lineTo(centerX + halfWidth, base);
        mPointerPaint.setStyle(Paint.Style.STROKE);
        mPointerPaint.setStrokeJoin(Paint.Join.ROUND);
        mPointerPaint.setStrokeWidth(mDress.strokeWidthPx);
        mPointerPaint.setColor(mDress.strokeColor);
        mPointerPaint.setAlpha(Math.round(Color.alpha(mDress.strokeColor) * alpha));
        canvas.drawPath(mPointerEdges, mPointerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int margin = dp(CARD_SIDE_MARGIN_DP);
        int available = MeasureSpec.getSize(widthMeasureSpec) - (2 * margin);
        int max = Math.min(dp(CARD_MAX_WIDTH_DP), Math.max(dp(120f), available));
        // What is left of the window once the system bars and the card's own margins are out of
        // it. Only the closing card ever wants more than this.
        int budget = Math.max(dp(120f), MeasureSpec.getSize(heightMeasureSpec)
            - mSystemInsetTop - mSystemInsetBottom - (2 * margin));
        // Measured twice, and only ever to any effect on the closing card: once unbounded, for
        // what the card would like to be, and again against the budget with the sections given
        // exactly the height that is left over. A card that simply grew past the budget would be
        // cut off by the layout below rather than scrolled.
        mScrollMaxHeight = UNBOUNDED_PX;
        measureCard(max, UNBOUNDED_PX, MeasureSpec.UNSPECIFIED);
        int natural = mCard.getMeasuredHeight();
        if (natural > budget && mBodyScroll.getVisibility() != GONE) {
            mScrollMaxHeight = Math.max(dp(64f),
                mBodyScroll.getMeasuredHeight() - (natural - budget));
        }
        measureCard(max, budget, MeasureSpec.AT_MOST);
    }

    private void measureCard(int maxWidth, int maxHeight, int heightMode) {
        measureChild(mCard, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(maxHeight, heightMode));
    }

    private void animateCardIn() {
        mCard.animate().cancel();
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            mCard.setAlpha(1f);
            mCard.setTranslationY(0f);
            return;
        }
        mCard.setAlpha(0f);
        mCard.setTranslationY(-CARD_RISE_DP * mDensity);
        // The pointer is drawn by this view, not by the card, so every frame of the card's rise
        // has to be a frame of this view too or the two would arrive separately.
        mCard.animate().alpha(1f).translationY(0f).setDuration(CARD_IN_MS)
            .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).withLayer()
            .setUpdateListener(animation -> invalidate()).start();
    }

    /** One pass of the gesture per card. Nothing here loops. */
    private void startTrace() {
        stopTrace();
        if (mStep == null || mStep.gestureAt(mStage) == TourGesture.NONE
            || !FocusOutlineRenderer.animationsEnabled(getContext())) {
            mTraceProgress = 1f;
            invalidate();
            return;
        }
        mTraceProgress = 0f;
        mTrace = ValueAnimator.ofFloat(0f, 1f);
        mTrace.setDuration(TourFingerTrace.TRACE_MS);
        mTrace.addUpdateListener(animator -> {
            mTraceProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        mTrace.start();
    }

    private void stopTrace() {
        if (mTrace != null) {
            mTrace.cancel();
            mTrace = null;
        }
        mTraceProgress = 1f;
    }

    /** The card's three sections, built once and kept while the card is up. */
    private void showClosingSections() {
        TourEdition edition = TourEdition.of(getContext().getPackageName());
        if (edition != mSectionsEdition) buildClosingSections(edition);
        // Every Copy button back to offering rather than acknowledging: the card can be shown
        // again after a resume, and a row of buttons all saying "Copied" says nothing.
        for (TextView copyButton : mCopyButtons) copyButton.setText(R.string.tour_copy);
        mBodyScroll.setVisibility(VISIBLE);
        mBodyScroll.scrollTo(0, 0);
        mDocsLink.setVisibility(VISIBLE);
    }

    private void buildClosingSections(@NonNull TourEdition edition) {
        mSections.removeAllViews();
        mCopyButtons.clear();
        boolean first = true;
        for (TourClosingCard.Section section : TourClosingCard.sections(edition)) {
            mSections.addView(sectionView(section, first));
            first = false;
        }
        mDocsLink.setText(R.string.tour_read_the_docs);
        mSectionsEdition = edition;
    }

    /** One section: a heading, a sentence, and where there is one, the command and its Copy. */
    @NonNull
    private View sectionView(@NonNull TourClosingCard.Section section, boolean first) {
        Context context = getContext();
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!first) blockParams.topMargin = dp(12);
        block.setLayoutParams(blockParams);

        TextView heading = new TextView(context);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setTextColor(mDress.textColor);
        heading.setText(section.headingRes);
        block.addView(heading, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView copy = new TextView(context);
        copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        copy.setTextColor(ColorUtils.setAlphaComponent(mDress.textColor, 204));
        copy.setLineSpacing(dp(2), 1f);
        copy.setText(section.copyRes);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.topMargin = dp(2);
        block.addView(copy, copyParams);

        if (!section.hasCommand()) return block;

        LinearLayout commandRow = new LinearLayout(context);
        commandRow.setOrientation(LinearLayout.HORIZONTAL);
        commandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView command = new TextView(context);
        command.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextColor(mDress.textColor);
        command.setText(section.commandRes);
        command.setLineSpacing(dp(1), 1f);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        commandRow.addView(command, commandParams);

        TextView copyButton = textButton(context,
            view -> onCopyTapped((TextView) view, section.commandRes));
        copyButton.setText(R.string.tour_copy);
        mCopyButtons.add(copyButton);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.leftMargin = dp(6);
        commandRow.addView(copyButton, buttonParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(4);
        block.addView(commandRow, rowParams);
        return block;
    }

    /** @param commandRes the command this button stands beside */
    private void onCopyTapped(@NonNull TextView button, int commandRes) {
        if (mCallbacks == null) return;
        mCallbacks.onTourCopyCommandTapped(commandRes);
        // The run draws no toasts, so the button itself is the acknowledgement.
        button.setText(R.string.tour_copied_commands);
    }

    /** The card's buttons, rebuilt only when the set actually changed. */
    private void applyActions(@NonNull List<TourAction> actions) {
        if (!mActions.equals(actions)) {
            mActions.clear();
            mActions.addAll(actions);
            mButtonRow.removeAllViews();
            addDocsLink();
            mActionButtons.clear();
            for (TourAction action : mActions) {
                TextView button = textButton(getContext(), view -> onActionTapped(action));
                button.setText(action.labelRes);
                button.setContentDescription(getContext().getString(action.labelRes));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.leftMargin = dp(8f);
                mButtonRow.addView(button, params);
                mActionButtons.add(button);
            }
        }
        mButtonRow.setVisibility(mActions.isEmpty() ? GONE : VISIBLE);
    }

    /**
     * The docs link leads the row as a pill of its own size, and an empty spacer takes the
     * slack, so the action buttons keep the trailing edge without the link stretching to meet
     * them.
     */
    private void addDocsLink() {
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linkParams.gravity = Gravity.CENTER_VERTICAL;
        mButtonRow.addView(mDocsLink, linkParams);
        View spacer = new View(getContext());
        mButtonRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
    }

    private void onActionTapped(@NonNull TourAction action) {
        if (mCallbacks != null) mCallbacks.onTourActionTapped(action);
    }

    @NonNull
    private GradientDrawable buttonBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(mAccent, 20));
        shape.setCornerRadius(dp(8f));
        return shape;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTrace();
        stopTargetRetry();
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** Layout params for the content root: the whole window, under nothing. */
    @NonNull
    public static FrameLayout.LayoutParams buildLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.START);
    }

    /** Kept for the host, which re-resolves the accent when the theme changes under the run. */
    public void refreshAccent() {
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        for (TextView button : mActionButtons) {
            button.setTextColor(mAccent);
            button.setBackground(buttonBackground());
        }
        mDocsLink.setTextColor(mAccent);
        mDocsLink.setBackground(buttonBackground());
        for (TextView copyButton : mCopyButtons) {
            copyButton.setTextColor(mAccent);
            copyButton.setBackground(buttonBackground());
        }
        invalidate();
    }
}
