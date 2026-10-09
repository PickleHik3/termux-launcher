package com.termux.app.tour;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.FocusOutlineRenderer;
import com.termux.app.chrome.ActionButtonRow;
import com.termux.app.chrome.ShapeTokens;
import com.termux.app.notice.TerminalDress;

import java.util.List;

/**
 * The run's only view: the zone around the control the card is about, the finger demonstrating
 * the gesture on it, and the coach card itself — or, at the end, the closing card.
 *
 * <p>It never takes a touch. The launcher is the home screen and the run is teaching gestures on
 * live chrome, so the user has to be able to actually perform the gesture while the card is up —
 * the overlay is not clickable, not focusable, and {@link #onTouchEvent} refuses every event so
 * the sibling below it gets the stream. The card's own buttons are child views and so are reached
 * before this view is consulted.
 *
 * <p>The card wears the terminal's dress — its fill and hairline from {@link TerminalDress} — with
 * the coach card's own radius, and every accent is the theme's ({@link
 * FocusOutlineRenderer#resolveAccent}), so it follows the wallpaper's colours and the launcher's
 * scheme like the rest of the chrome. It moves between controls with a glide rather than leaving
 * and coming back, and its demonstration plays {@link TourDemoLoop#PASSES} times and then rests.
 *
 * <p>A target that cannot be measured is normal: the card shows without a zone rather than
 * pointing somewhere wrong.
 */
public final class TourOverlayView extends FrameLayout {

    /** The card's buttons; what they mean is the run's business. */
    public interface Callbacks {
        /** ✕: on to the closing card, or out of practice. */
        void onTourCloseTapped();

        /** Skip: past this lesson. */
        void onTourSkipTapped();

        /** The closing card's Start. */
        void onTourStartTapped();

        /** The closing card's Read the guide. */
        void onTourGuideTapped();

        /** The closing card's Download models. */
        void onTourDownloadModelsTapped();
    }

    private static final long CARD_IN_MS = 200L;
    private static final float CARD_RISE_DP = 8f;
    /** How long the card takes to glide from one control to the next. */
    private static final long CARD_MOVE_MS = 350L;
    /** The gap the card keeps from the sides of the screen. */
    private static final float CARD_SIDE_MARGIN_DP = 20f;
    /** The tick that answers a landed gesture. */
    private static final float TICK_DP = 56f;
    /** On a tablet or in landscape the card stops growing here, where a sentence still reads well. */
    private static final float CARD_MAX_WIDTH_DP = 400f;
    private static final float CARD_RADIUS_DP = 20f;
    private static final float CLOSING_RADIUS_DP = 22f;
    /** The gap between the control (and its demonstration) and the card. */
    private static final float CARD_GAP_DP = 8f;
    /** The progress: segments this big, this far apart, groups further apart. */
    private static final float SEGMENT_WIDTH_DP = 14f;
    private static final float SEGMENT_HEIGHT_DP = 4f;
    private static final float SEGMENT_GAP_DP = 3f;
    private static final float GROUP_GAP_DP = 10f;
    /** How strongly a segment still to come and a bar's empty track are drawn, of the ink. */
    private static final int TODO_ALPHA = 56;
    /** The body's share of the title's ink. */
    private static final int BODY_ALPHA = 200;
    /**
     * How long a stage keeps asking for a control that could not be measured when it arrived: a
     * control revealed by an animation that walks no layout has no layout pass for the overlay to
     * hear, so the overlay asks again for a little while and then stops whether or not it turned up.
     */
    private static final long TARGET_RETRY_MS = 1500L;
    private static final long TARGET_RETRY_INTERVAL_MS = 32L;
    /**
     * The gap the zone keeps outside the control. Tight on purpose: the ring also carries a blurred
     * halo outside this rect, and on a control flush with the edge of the screen every dp of it is
     * a dp of the halo hanging off the display.
     */
    private static final float GLOW_PADDING_DP = 2f;
    private static final float GLOW_RADIUS_DP = 12f;
    /** Stands in for "as tall as it likes": a measure spec carries no unbounded size of its own. */
    private static final int UNBOUNDED_PX = 1 << 24;
    /** The ✕: an 18dp glyph in a 36dp target. */
    private static final float CLOSE_GLYPH_DP = 18f;
    private static final float CLOSE_TARGET_DP = 36f;

    private final float mDensity;
    private final TerminalDress mDress;
    private final Paint mFingerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mCuePath = new Path();
    private final RectF mGlowRect = new RectF();
    /** Where the finger's centre may be, so its own circle stays on the screen. */
    private final RectF mCueBounds = new RectF();
    private final float[] mPointA = new float[2];
    private final float[] mPointB = new float[2];
    private final TourDemoLoop.Frame mFrame = new TourDemoLoop.Frame();

    /** The coach card. */
    private final LinearLayout mCard;
    private final TextView mChapterLabel;
    private final ImageView mClose;
    private final TextView mTitle;
    private final TextView mBody;
    /** The tick that stands in for the card for a moment after its gesture lands. */
    private final ImageView mTick;
    private final ProgressSegments mProgress;
    private final TextView mSkip;

    /** The closing card. */
    private final LinearLayout mClosingCard;
    private final ScrollView mClosingScroll;
    private final LinearLayout mModelsRow;
    private final TextView mModelsText;
    private final ProgressBarView mModelsBar;
    private final TextView mModelsButton;
    private final TextView mGuide;
    private final TextView mStart;

    @Nullable private Callbacks mCallbacks;
    @Nullable private TourTargets mTargets;
    @Nullable private TourStep mStep;
    private int mStage;
    private boolean mPracticing;
    @Nullable private Rect mTargetRect;
    /**
     * The last control this card was actually placed against, kept so a stage whose own control
     * cannot be measured yet stays where the stage before it stood. Cleared when a different card
     * comes up.
     */
    @Nullable private Rect mLastAnchorRect;
    /** The launcher's own top bar, which a card with nothing to stand against rests under. */
    @Nullable private Rect mTopBarRect;
    private int mSystemInsetTop;
    private int mSystemInsetBottom;
    @NonNull private String mMissReason = "none";
    private int mPresentation = TourCardVisibility.NORMAL;
    private int mAccent;
    private int mOnAccent;

    /** The card that is placed, and whether it has been placed since it last came up. */
    @Nullable private View mPlacedCard;
    @Nullable private ValueAnimator mGlide;
    @Nullable private ValueAnimator mDemo;
    /** How far into the demonstration loop the zone is; -1 once the loop has rested. */
    private long mDemoElapsedMs = -1L;
    private long mRetryUntil;
    @Nullable private Runnable mRetry;
    /** How tall the closing card's middle may grow before it scrolls inside the card. */
    private int mScrollMaxHeight = UNBOUNDED_PX;

    public TourOverlayView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        // The card is placed by absolute geometry in onLayout; what it says reads in the locale's
        // direction even though the content root it hangs off is pinned left to right.
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        setWillNotDraw(false);
        // Passive by construction: the gesture the card is asking for belongs to the chrome below.
        setClickable(false);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mDress = TerminalDress.stored(context);
        resolveAccents();

        mCard = cardShell(context, CARD_RADIUS_DP);
        mCard.setPaddingRelative(dp(16), dp(14), dp(14), dp(12));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mChapterLabel = text(context, 10.5f, true, mDress.subTextColor);
        mChapterLabel.setAllCaps(true);
        mChapterLabel.setLetterSpacing(0.1f);
        singleLine(mChapterLabel);
        header.addView(mChapterLabel, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mClose = glyphButton(context, R.drawable.ic_symbol_close, mDress.subTextColor,
            view -> { if (mCallbacks != null) mCallbacks.onTourCloseTapped(); });
        header.addView(mClose);
        mCard.addView(header, matchWrap(0));

        mTitle = text(context, 15f, true, mDress.textColor);
        mCard.addView(mTitle, matchWrap(dp(2)));
        mBody = text(context, 13f, false, ColorUtils.setAlphaComponent(mDress.textColor,
            BODY_ALPHA));
        mBody.setLineSpacing(0f, 1.25f);
        mCard.addView(mBody, matchWrap(dp(4)));

        // The progress leads the footer and Skip keeps its trailing edge; at a large font scale
        // the row stacks rather than clipping either.
        ActionButtonRow footer = new ActionButtonRow(context);
        mProgress = new ProgressSegments(context);
        footer.setLeading(mProgress);
        mSkip = secondaryButton(context, R.string.tour_skip,
            view -> { if (mCallbacks != null) mCallbacks.onTourSkipTapped(); });
        footer.addView(mSkip);
        mCard.addView(footer, matchWrap(dp(2)));

        mClosingCard = cardShell(context, CLOSING_RADIUS_DP);
        mClosingCard.setPaddingRelative(dp(16), dp(18), dp(16), dp(14));
        TextView closingTitle = text(context, 20f, true, mDress.textColor);
        closingTitle.setText(R.string.tour_closing_title);
        mClosingCard.addView(closingTitle, matchWrap(0));
        LinearLayout tips = new LinearLayout(context);
        tips.setOrientation(LinearLayout.VERTICAL);
        tips.addView(tipRow(context, R.drawable.ic_symbol_help, R.string.tour_tip_help),
            matchWrap(0));
        tips.addView(tipRow(context, R.drawable.ic_symbol_wallpaper, R.string.tour_tip_wallpaper),
            matchWrap(dp(12)));
        tips.addView(tipRow(context, R.drawable.ic_symbol_keyboard, R.string.tour_tip_shortcuts),
            matchWrap(dp(12)));
        tips.addView(tipRow(context, R.drawable.ic_symbol_restart, R.string.tour_tip_replay),
            matchWrap(dp(12)));
        mModelsRow = new LinearLayout(context);
        mModelsRow.setOrientation(LinearLayout.HORIZONTAL);
        mModelsRow.addView(glyph(context, R.drawable.ic_symbol_download, mAccent, 20f));
        LinearLayout modelsWords = new LinearLayout(context);
        modelsWords.setOrientation(LinearLayout.VERTICAL);
        mModelsText = text(context, 13f, false, ColorUtils.setAlphaComponent(mDress.textColor,
            BODY_ALPHA));
        modelsWords.addView(mModelsText, matchWrap(0));
        mModelsBar = new ProgressBarView(context);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(SEGMENT_HEIGHT_DP));
        barParams.topMargin = dp(6);
        modelsWords.addView(mModelsBar, barParams);
        mModelsButton = secondaryButton(context, R.string.tour_download_models,
            view -> { if (mCallbacks != null) mCallbacks.onTourDownloadModelsTapped(); });
        mModelsButton.setVisibility(GONE);
        LinearLayout.LayoutParams modelsButtonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modelsButtonParams.topMargin = dp(8);
        modelsWords.addView(mModelsButton, modelsButtonParams);
        LinearLayout.LayoutParams modelsWordsParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        modelsWordsParams.setMarginStart(dp(12));
        mModelsRow.addView(modelsWords, modelsWordsParams);
        mModelsRow.setVisibility(GONE);
        tips.addView(mModelsRow, matchWrap(dp(12)));
        // The middle scrolls rather than pushing Start off a short screen at a large font scale.
        mClosingScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.max(0, mScrollMaxHeight), MeasureSpec.AT_MOST));
            }
        };
        mClosingScroll.setVerticalScrollBarEnabled(false);
        mClosingScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mClosingScroll.addView(tips, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mClosingCard.addView(mClosingScroll, matchWrap(dp(14)));
        ActionButtonRow closingButtons = new ActionButtonRow(context);
        mGuide = secondaryButton(context, R.string.tour_read_the_guide,
            view -> { if (mCallbacks != null) mCallbacks.onTourGuideTapped(); });
        closingButtons.addView(mGuide);
        mStart = primaryButton(context, R.string.tour_start,
            view -> { if (mCallbacks != null) mCallbacks.onTourStartTapped(); });
        closingButtons.addView(mStart);
        mClosingCard.addView(closingButtons, matchWrap(dp(14)));

        mCard.setVisibility(GONE);
        mClosingCard.setVisibility(GONE);
        addView(mCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mClosingCard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mTick = glyph(context, R.drawable.ic_symbol_check_circle, mAccent, TICK_DP);
        mTick.setContentDescription(context.getString(R.string.tour_got_it));
        mTick.setVisibility(GONE);
        addView(mTick, new FrameLayout.LayoutParams(dp(TICK_DP), dp(TICK_DP)));
    }

    // ---- building ------------------------------------------------------------------------------

    @NonNull
    private LinearLayout cardShell(@NonNull Context context, float radiusDp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(mDress.fillColor);
        background.setStroke(Math.round(mDress.strokeWidthPx), mDress.strokeColor);
        background.setCornerRadius(dp(radiusDp));
        card.setBackground(background);
        // The one surface in the run that floats over moving chrome: a little lift separates it.
        card.setElevation(ShapeTokens.elevationPx(context, 3));
        card.setClickable(false);
        card.setFocusable(false);
        return card;
    }

    @NonNull
    private TextView text(@NonNull Context context, float sizeSp, boolean strong, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif",
            Typeface.NORMAL));
        view.setTextColor(color);
        view.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    private static void singleLine(@NonNull TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
    }

    @NonNull
    private ImageView glyph(@NonNull Context context, @DrawableRes int glyphRes, int tint,
                            float sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(glyphRes);
        view.setImageTintList(ColorStateList.valueOf(tint));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    @NonNull
    private ImageView glyphButton(@NonNull Context context, @DrawableRes int glyphRes, int tint,
                                  @NonNull OnClickListener onClick) {
        ImageView view = new ImageView(context);
        view.setImageResource(glyphRes);
        view.setImageTintList(ColorStateList.valueOf(tint));
        // The glyph, padded out to a target a thumb can find.
        int pad = dp((CLOSE_TARGET_DP - CLOSE_GLYPH_DP) / 2f);
        view.setPadding(pad, pad, pad, pad);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            ripple, true) && ripple.resourceId != 0) view.setBackgroundResource(ripple.resourceId);
        view.setOnClickListener(onClick);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(CLOSE_TARGET_DP),
            dp(CLOSE_TARGET_DP)));
        return view;
    }

    @NonNull
    private View tipRow(@NonNull Context context, @DrawableRes int glyphRes, @StringRes int textRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(glyph(context, glyphRes, mAccent, 20f));
        TextView words = text(context, 13f, false, ColorUtils.setAlphaComponent(mDress.textColor,
            BODY_ALPHA));
        words.setLineSpacing(0f, 1.25f);
        words.setText(textRes);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(dp(12));
        row.addView(words, params);
        return row;
    }

    @NonNull
    private TextView secondaryButton(@NonNull Context context, @StringRes int labelRes,
                                     @NonNull OnClickListener onClick) {
        return CoachButtons.secondary(context, labelRes, mAccent, onClick);
    }

    @NonNull
    private TextView primaryButton(@NonNull Context context, @StringRes int labelRes,
                                   @NonNull OnClickListener onClick) {
        return CoachButtons.primary(context, labelRes, mAccent, mOnAccent, onClick);
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private void resolveAccents() {
        mAccent = FocusOutlineRenderer.resolveAccent(this);
        mOnAccent = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnPrimary,
            androidx.core.content.ContextCompat.getColor(getContext(), R.color.termux_on_primary));
    }

    // ---- what the run tells it -----------------------------------------------------------------

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void setTargets(@Nullable TourTargets targets) {
        mTargets = targets;
    }

    /**
     * The system bars' keep-out, so a card that has nothing to anchor to, or that is clamped to an
     * end of the overlay, does not come to rest under the status bar or the gesture bar.
     */
    public void setSystemBarInsets(int top, int bottom) {
        int clampedTop = Math.max(0, top);
        int clampedBottom = Math.max(0, bottom);
        if (mSystemInsetTop == clampedTop && mSystemInsetBottom == clampedBottom) return;
        mSystemInsetTop = clampedTop;
        mSystemInsetBottom = clampedBottom;
        requestLayout();
        invalidate();
    }

    /**
     * Shows a card at a stage. A new stage of the same lesson, or the next lesson, glides the card
     * there; the demonstration plays again for whatever it asks now.
     *
     * @param practicing whether this is one lesson practised from help, which has no run to
     *     count and nothing to skip to
     */
    public void showStep(@NonNull TourStep step, int stage, @NonNull TourProgress progress,
                         boolean practicing) {
        boolean sameCard = mStep != null && mStep.id.equals(step.id);
        if (!sameCard) mLastAnchorRect = null;
        mStep = step;
        mStage = stage;
        mPracticing = practicing;
        mProgress.bind(progress.groups);
        bindChapterLabel(step, progress);
        applyContent();
        applyPresentation();
        armTargetRetry();
        refreshTarget();
        startDemo();
    }

    /** The lesson that is up has been cleared: the card says so, with its segment done. */
    public void showCompleted(@NonNull TourProgress progress) {
        mProgress.bind(progress.groups);
        setPresentation(TourCardVisibility.COMPLETED);
    }

    /**
     * The closing card's downloads line, or null for none: only shown when the run queued models.
     *
     * @param fraction how far along they are, 0..1, or less than 0 for a line with no bar
     */
    /** Whether the models line carries its Download button. */
    public void setModelsOffer(boolean offered) {
        setShown(mModelsButton, offered);
    }

    public void setModelsLine(@Nullable CharSequence text, float fraction) {
        boolean shown = text != null && text.length() > 0;
        if (shown && !TextUtils.equals(mModelsText.getText(), text)) mModelsText.setText(text);
        int visibility = shown ? VISIBLE : GONE;
        if (mModelsRow.getVisibility() != visibility) mModelsRow.setVisibility(visibility);
        int barVisibility = shown && fraction >= 0f ? VISIBLE : GONE;
        if (mModelsBar.getVisibility() != barVisibility) mModelsBar.setVisibility(barVisibility);
        mModelsBar.setFraction(fraction);
    }

    /**
     * Whether the card draws against its control, away from the place it is taught on, saying
     * "Got it", or not at all. {@link TourCardVisibility} decides; this applies the answer.
     */
    public void setPresentation(int presentation) {
        if (mPresentation == presentation) return;
        mPresentation = presentation;
        applyContent();
        applyPresentation();
        // Back against its control — from behind chrome, from away, or from a "Got it" that the
        // next card replaced — the demonstration plays from the top.
        if (presentation == TourCardVisibility.NORMAL) startDemo();
        // A card coming back from behind chrome is a card whose control may still be arriving.
        armTargetRetry();
        refreshTarget();
        requestLayout();
        invalidate();
    }

    private void bindChapterLabel(@NonNull TourStep step, @NonNull TourProgress progress) {
        if (step.isClosingCard()) return;
        CharSequence name = getContext().getString(step.chapterRes);
        CharSequence label = practicing() || progress.chapterNumber <= 0 ? name
            : getContext().getString(R.string.tour_chapter_label, progress.chapterNumber,
                progress.chapterCount, name);
        if (!TextUtils.equals(mChapterLabel.getText(), label)) mChapterLabel.setText(label);
    }

    private boolean practicing() {
        return mPracticing;
    }

    /** What the card says: the stage's title and sentence, "Got it", or the way back. */
    private void applyContent() {
        TourStep step = mStep;
        if (step == null || step.isClosingCard()) return;
        boolean away = mPresentation == TourCardVisibility.AWAY;
        boolean completed = mPresentation == TourCardVisibility.COMPLETED;
        setShown(mTitle, !completed && !away);
        setShown(mBody, !completed);
        if (!completed && !away) setTextIfChanged(mTitle, step.titleResAt(mStage));
        if (!completed) setTextIfChanged(mBody, away
            ? R.string.tour_card_return_to_terminal : step.bodyResAt(mStage));
        // Practice has no run to count and no next lesson to skip to; ✕ is its way out.
        setShown(mProgress, !mPracticing);
        setShown(mSkip, !mPracticing);
        mClose.setContentDescription(getContext().getString(mPracticing
            ? R.string.tour_end_practice : R.string.tour_close_description));
    }

    private void setTextIfChanged(@NonNull TextView view, @StringRes int res) {
        if (res == 0) return;
        CharSequence text = getContext().getString(res);
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    private static void setShown(@NonNull View view, boolean shown) {
        int visibility = shown ? VISIBLE : GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private void applyPresentation() {
        boolean hidden = mStep == null || mPresentation == TourCardVisibility.HIDDEN;
        boolean closing = mStep != null && mStep.isClosingCard();
        boolean completed = !hidden && mPresentation == TourCardVisibility.COMPLETED;
        View wanted = hidden || completed ? null : closing ? mClosingCard : mCard;
        setShown(mCard, wanted == mCard);
        setShown(mClosingCard, wanted == mClosingCard);
        showTick(completed);
        if (hidden || closing || mPresentation != TourCardVisibility.NORMAL) stopDemo();
        if (hidden) stopTargetRetry();
        int visibility = hidden ? GONE : VISIBLE;
        if (getVisibility() != visibility) setVisibility(visibility);
        if (wanted != mPlacedCard) {
            // A card that was not on screen comes in; one that was glides from where it stood.
            cancelGlide();
            mPlacedCard = null;
            if (wanted != null) animateCardIn(wanted);
        }
        if (closing && wanted != null) mClosingScroll.scrollTo(0, 0);
    }

    /** The control this card glows right now; nothing away from the terminal or on the last card. */
    @NonNull
    private String glowTargetId() {
        if (mStep == null || mPresentation == TourCardVisibility.AWAY) return TourTargets.NONE;
        return mStep.targetIdAt(mStage);
    }

    /** Re-measures the control the card points at; cheap enough for every layout pass. */
    public void refreshTarget() {
        if (mStep == null) return;
        String targetId = glowTargetId();
        Rect updated = null;
        Rect topBar = null;
        String reason = "no targets host";
        if (mTargets != null) {
            topBar = mTargets.rectFor(TourTargets.STATUS_BAR);
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
        // Logged on the edge, not per layout pass: the keyboard alone produces dozens of them.
        if (updated == null && (moved || reasonChanged) && TourLog.enabled()) {
            TourLog.d("card " + mStep.id + ":" + mStage + " has no zone — target \"" + targetId
                + "\": " + reason);
        }
        if (moved) {
            // The card's size does not depend on the target, only its position does, so the card
            // is placed again here rather than through a window traversal: this runs on every
            // frame of a reveal, and a requestLayout a frame is the per-frame work the chrome
            // under the overlay goes out of its way not to do.
            layoutCard();
            invalidate();
        }
    }

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
     * otherwise the one the stage before it stood against. The space bar is not there at all while
     * the keyboard is down, which is not a control still arriving: the card stands against the key
     * that brings the keyboard back instead, and against nothing when even that is gone.
     */
    @Nullable
    private Rect anchorRect() {
        if (mStep == null) return null;
        String targetId = glowTargetId();
        boolean namesAControl = !TourTargets.NONE.equals(targetId);
        boolean measured = mTargetRect != null && !mTargetRect.isEmpty();
        if (namesAControl && !measured && TourTargets.SPACE_BAR.equals(targetId)) {
            Rect instead = mTargets == null
                ? null : mTargets.rectFor(TourTargets.KEYBOARD_TOGGLE_KEY);
            return instead != null && !instead.isEmpty() ? instead : null;
        }
        return TourCardPlacement.anchorRect(namesAControl, mTargetRect, mLastAnchorRect);
    }

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

    /** The control the card is glowing, for the log. */
    @NonNull
    String currentTargetId() {
        return mPresentation == TourCardVisibility.NORMAL ? glowTargetId() : TourTargets.NONE;
    }

    /** Why {@link #currentTargetRect()} is null, for the log. */
    @NonNull
    String currentMissReason() {
        return mMissReason;
    }

    /** Takes the card down and stops the demonstration. */
    public void dismiss() {
        stopDemo();
        stopTargetRetry();
        cancelGlide();
        mStep = null;
        mTargetRect = null;
        mLastAnchorRect = null;
        mTopBarRect = null;
        mPlacedCard = null;
        mMissReason = "none";
        mPresentation = TourCardVisibility.NORMAL;
        setModelsLine(null, -1f);
        setShown(mCard, false);
        setShown(mClosingCard, false);
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

    // ---- geometry ------------------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The whole window; the cards are measured here and placed by geometry, never by gravity.
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
        int margin = dp(CARD_SIDE_MARGIN_DP);
        int width = Math.max(dp(120f), Math.min(dp(CARD_MAX_WIDTH_DP),
            MeasureSpec.getSize(widthMeasureSpec) - (2 * margin)));
        int budget = Math.max(dp(120f), MeasureSpec.getSize(heightMeasureSpec)
            - mSystemInsetTop - mSystemInsetBottom - (2 * margin));
        measureCard(mCard, width, budget);
        int tick = MeasureSpec.makeMeasureSpec(dp(TICK_DP), MeasureSpec.EXACTLY);
        mTick.measure(tick, tick);
        // Measured twice, and only ever to any effect on a short screen: once unbounded, for what
        // the closing card would like to be, and again with its middle held to what is left.
        mScrollMaxHeight = UNBOUNDED_PX;
        measureCard(mClosingCard, width, UNBOUNDED_PX);
        int natural = mClosingCard.getMeasuredHeight();
        if (natural > budget) {
            mScrollMaxHeight = Math.max(dp(64f),
                mClosingScroll.getMeasuredHeight() - (natural - budget));
            measureCard(mClosingCard, width, budget);
        }
    }

    private void measureCard(@NonNull View card, int width, int maxHeight) {
        card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // The cards are placed by geometry, never by the frame's gravity.
        layoutCard();
        layoutTick();
    }

    /** The tick pops in where the card stood, and the card is taken down under it. */
    private void showTick(boolean shown) {
        boolean was = mTick.getVisibility() == VISIBLE;
        if (shown == was) return;
        mTick.animate().cancel();
        setShown(mTick, shown);
        if (!shown) return;
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            mTick.setAlpha(1f);
            mTick.setScaleX(1f);
            mTick.setScaleY(1f);
            return;
        }
        mTick.setAlpha(0f);
        mTick.setScaleX(0.6f);
        mTick.setScaleY(0.6f);
        mTick.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L)
            .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
    }

    /** Centred on the card it stands in for; a card never laid out leaves it mid-screen. */
    private void layoutTick() {
        if (mTick.getVisibility() == GONE) return;
        int size = mTick.getMeasuredWidth();
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (mCard.getWidth() > 0) {
            cx = mCard.getLeft() + mCard.getTranslationX() + (mCard.getWidth() / 2f);
            cy = mCard.getTop() + mCard.getTranslationY() + (mCard.getHeight() / 2f);
        }
        int left = Math.round(cx - (size / 2f));
        int top = Math.round(cy - (size / 2f));
        mTick.layout(left, top, left + size, top + size);
    }

    /**
     * Against the control and clear of its demonstration, below it when it is in the top half of
     * the overlay and above it otherwise, flipped when that side has no room. The arithmetic is
     * {@link TourCardPlacement}'s; this only applies the answer.
     */
    private void layoutCard() {
        if (mStep == null) return;
        View card = mStep.isClosingCard() ? mClosingCard : mCard;
        if (card.getVisibility() == GONE) return;
        int width = card.getMeasuredWidth();
        int height = card.getMeasuredHeight();
        if (width <= 0 || height <= 0 || getWidth() <= 0) return;
        int margin = dp(CARD_SIDE_MARGIN_DP);
        int topMargin = margin + mSystemInsetTop;
        int bottomMargin = margin + mSystemInsetBottom;
        TourCardPlacement placement;
        if (mStep.isClosingCard()) {
            placement = TourCardPlacement.placeClear(getWidth(), getHeight(), width, height, null,
                margin, topMargin, bottomMargin, 0, 0, TourCardPlacement.SIDE_AUTO);
        } else {
            Rect anchor = mPresentation == TourCardVisibility.AWAY ? null : anchorRect();
            if (anchor == null) {
                // Nothing to stand against: under the launcher's own top bar, where the missing
                // control would not have been.
                placement = TourCardPlacement.placeUnderStatusBar(getWidth(), getHeight(), width,
                    height, margin, topMargin, bottomMargin, mTopBarRect, dp(CARD_GAP_DP));
            } else {
                TourGesture gesture = gestureAsLaidOut(mStep.gestureAt(mStage));
                int gap = dp(CARD_GAP_DP);
                placement = TourCardPlacement.placeClear(getWidth(), getHeight(), width, height,
                    anchor, margin, topMargin, bottomMargin,
                    gap + dp(TourDemoLoop.reachDp(gesture, false)),
                    gap + dp(TourDemoLoop.reachDp(gesture, true)), preferredCardSide());
            }
        }
        place(card, placement.left, placement.top, width, height);
    }

    /**
     * Lays the card out where it belongs, gliding it there from wherever it stood when it was
     * already on screen. Only translation moves: the card's own layout is final at once.
     */
    private void place(@NonNull View card, int left, int top, int width, int height) {
        boolean placed = mPlacedCard == card;
        float fromX = card.getLeft() + card.getTranslationX();
        float fromY = card.getTop() + card.getTranslationY();
        boolean moves = card.getLeft() != left || card.getTop() != top;
        card.layout(left, top, left + width, top + height);
        mPlacedCard = card;
        if (!placed || !moves) return;
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            cancelGlide();
            card.setTranslationX(0f);
            card.setTranslationY(0f);
            return;
        }
        cancelGlide();
        card.animate().cancel();
        card.setAlpha(1f);
        float startX = fromX - left;
        float startY = fromY - top;
        card.setTranslationX(startX);
        card.setTranslationY(startY);
        ValueAnimator glide = ValueAnimator.ofFloat(1f, 0f);
        glide.setDuration(CARD_MOVE_MS);
        glide.setInterpolator(new PathInterpolator(0.3f, 0.7f, 0.2f, 1f));
        glide.addUpdateListener(animation -> {
            float k = (float) animation.getAnimatedValue();
            card.setTranslationX(startX * k);
            card.setTranslationY(startY * k);
        });
        mGlide = glide;
        glide.start();
    }

    private void cancelGlide() {
        if (mGlide != null) {
            mGlide.cancel();
            mGlide = null;
        }
        mCard.setTranslationX(0f);
        mClosingCard.setTranslationX(0f);
    }

    private void animateCardIn(@NonNull View card) {
        card.animate().cancel();
        card.setTranslationX(0f);
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            card.setAlpha(1f);
            card.setTranslationY(0f);
            return;
        }
        card.setAlpha(0f);
        card.setTranslationY(-CARD_RISE_DP * mDensity);
        card.animate().alpha(1f).translationY(0f).setDuration(CARD_IN_MS)
            .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).withLayer().start();
    }

    /**
     * The movement the card asks for, turned toward the control as it is laid out: the drawer is
     * pulled away from the apps row's edge, so a row that is a rail down a side is swiped inward
     * rather than pulled down.
     */
    @NonNull
    private TourGesture gestureAsLaidOut(@NonNull TourGesture gesture) {
        if (gesture != TourGesture.DRAG_DOWN || mTargetRect == null
            || !TourTargets.DOCK.equals(glowTargetId())) return gesture;
        if (mTargetRect.height() < mTargetRect.width()) return gesture;
        return mTargetRect.centerX() < getWidth() / 2 ? TourGesture.SWIPE_RIGHT
            : TourGesture.SWIPE_LEFT;
    }

    // ---- the demonstration ---------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        // Only a card standing against its control wears the zone. A card saying "Got it" or the
        // way back points at nothing.
        if (mStep == null || mStep.isClosingCard()) return;
        if (mPresentation != TourCardVisibility.NORMAL) return;
        if (mTargetRect == null || mTargetRect.isEmpty()) return;
        boolean playing = mDemoElapsedMs >= 0L;
        TourGlowGeometry.glowRect(mTargetRect, GLOW_PADDING_DP * mDensity,
            FocusOutlineRenderer.fallbackOuterReachPx(mDensity), getWidth(), getHeight(),
            TourGlowGeometry.EDGE_MARGIN_DP * mDensity, mGlowRect);
        FocusOutlineRenderer.drawRoundRectFallback(canvas, mGlowRect, GLOW_RADIUS_DP * mDensity,
            mAccent, playing ? TourDemoLoop.glowAlpha(mDemoElapsedMs) : 1f, 1f, mDensity);
        TourGesture gesture = gestureAsLaidOut(mStep.gestureAt(mStage));
        if (gesture == TourGesture.NONE) return;
        float reach = (TourDemoLoop.FINGER_DIAMETER_DP / 2f + TourDemoLoop.FINGER_HALO_DP)
            * mDensity;
        TourGlowGeometry.cueBounds(getWidth(), getHeight(), reach,
            TourGlowGeometry.EDGE_MARGIN_DP * mDensity, mCueBounds);
        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            // A phone that plays no animations still gets the direction, as a still picture.
            TourFingerPainter.drawStaticCue(canvas, mFingerPaint, mCuePath, gesture,
                mTargetRect.left, mTargetRect.top, mTargetRect.right, mTargetRect.bottom,
                mDensity, mAccent, mPointA, mPointB);
            return;
        }
        if (!playing) return;
        long pass = TourDemoLoop.passMs(gesture);
        TourDemoLoop.frame(gesture, (mDemoElapsedMs % pass) / (float) pass, mFrame);
        TourFingerPainter.drawDemo(canvas, mFingerPaint, gesture, mTargetRect.left,
            mTargetRect.top, mTargetRect.right, mTargetRect.bottom, mDensity, mFrame, mAccent,
            mPointA, mPointB, mCueBounds);
    }

    /** Plays the demonstration from the top, {@link TourDemoLoop#PASSES} times, then rests. */
    private void startDemo() {
        stopDemo();
        if (mStep == null || mStep.isClosingCard()
            || mPresentation != TourCardVisibility.NORMAL
            || mStep.gestureAt(mStage) == TourGesture.NONE
            || !FocusOutlineRenderer.animationsEnabled(getContext())) {
            invalidate();
            return;
        }
        long total = TourDemoLoop.PASSES * TourDemoLoop.passMs(mStep.gestureAt(mStage));
        mDemoElapsedMs = 0L;
        ValueAnimator demo = ValueAnimator.ofFloat(0f, total);
        demo.setDuration(total);
        demo.setInterpolator(null);
        demo.addUpdateListener(animation -> {
            mDemoElapsedMs = (long) (float) animation.getAnimatedValue();
            invalidate();
        });
        demo.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mDemo != animation) return;
                mDemo = null;
                mDemoElapsedMs = -1L;
                invalidate();
            }
        });
        mDemo = demo;
        demo.start();
    }

    private void stopDemo() {
        ValueAnimator demo = mDemo;
        mDemo = null;
        if (demo != null) demo.cancel();
        mDemoElapsedMs = -1L;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopDemo();
        stopTargetRetry();
        cancelGlide();
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

    // ---- the two small drawings ----------------------------------------------------------------

    /** The run's progress: a short bar per lesson, grouped by chapter. */
    private final class ProgressSegments extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        @NonNull private List<List<TourProgress.Segment>> mGroups =
            java.util.Collections.emptyList();

        ProgressSegments(@NonNull Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void bind(@NonNull List<List<TourProgress.Segment>> groups) {
            if (groups.equals(mGroups)) return;
            boolean resized = width(groups) != width(mGroups);
            mGroups = groups;
            if (resized) requestLayout();
            invalidate();
        }

        private int width(@NonNull List<List<TourProgress.Segment>> groups) {
            float total = 0f;
            for (int g = 0; g < groups.size(); g++) {
                int count = groups.get(g).size();
                total += (count * SEGMENT_WIDTH_DP) + (Math.max(0, count - 1) * SEGMENT_GAP_DP);
                if (g > 0) total += GROUP_GAP_DP;
            }
            return dp(total);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(width(mGroups), widthMeasureSpec),
                resolveSize(dp(SEGMENT_HEIGHT_DP), heightMeasureSpec));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float segment = SEGMENT_WIDTH_DP * mDensity;
            float height = SEGMENT_HEIGHT_DP * mDensity;
            float top = (getHeight() - height) / 2f;
            float x = 0f;
            for (int g = 0; g < mGroups.size(); g++) {
                if (g > 0) x += GROUP_GAP_DP * mDensity;
                List<TourProgress.Segment> group = mGroups.get(g);
                for (int i = 0; i < group.size(); i++) {
                    if (i > 0) x += SEGMENT_GAP_DP * mDensity;
                    float left = rtl ? getWidth() - x - segment : x;
                    mRect.set(left, top, left + segment, top + height);
                    mPaint.setColor(colorOf(group.get(i)));
                    canvas.drawRoundRect(mRect, height / 2f, height / 2f, mPaint);
                    x += segment;
                }
            }
        }

        private int colorOf(@NonNull TourProgress.Segment segment) {
            switch (segment) {
                case DONE: return mAccent;
                case CURRENT: return mDress.textColor;
                default: return ColorUtils.setAlphaComponent(mDress.textColor, TODO_ALPHA);
            }
        }
    }

    /** The closing card's downloads bar. */
    private final class ProgressBarView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private float mFraction;

        ProgressBarView(@NonNull Context context) {
            super(context);
        }

        void setFraction(float fraction) {
            float bounded = Math.max(0f, Math.min(1f, fraction));
            if (bounded == mFraction) return;
            mFraction = bounded;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float radius = getHeight() / 2f;
            mRect.set(0f, 0f, getWidth(), getHeight());
            mPaint.setColor(ColorUtils.setAlphaComponent(mDress.textColor, TODO_ALPHA));
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
            if (mFraction <= 0f) return;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float filled = getWidth() * mFraction;
            mRect.set(rtl ? getWidth() - filled : 0f, 0f, rtl ? getWidth() : filled, getHeight());
            mPaint.setColor(mAccent);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
        }
    }
}
