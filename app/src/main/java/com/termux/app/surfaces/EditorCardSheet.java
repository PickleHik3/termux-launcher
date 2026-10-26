package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.rows.ActionRow;
import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One card of the Appearance editor (appearance-and-settings-rework §3, §7, §8; wireframes 02–04):
 * a bottom sheet with a 64dp header (the object's icon and name, then its eye where the object can
 * be hidden, reset and ✕; editor-redesign §1) over its rows, tier by tier, every row always shown;
 * the rows scroll. Views only: every value is read from {@link Model} and every
 * touch reported to it, so the same sheet is every card.
 *
 * <p><b>Detents.</b> The sheet is laid out once at the full detent's height and stands at the
 * half detent by translation, so neither a detent change nor a drag of the header lays anything
 * out per frame. At the half detent the rows' bottom padding grows by the part of the sheet
 * below the screen, so the last row still scrolls clear of the navigation inset by 16dp.</p>
 *
 * <p><b>Rows.</b> Content-driven heights (a strip is at least 72dp, a switch row 56dp), labels
 * on one line, ellipsized. Every value slider ({@link Kind#STRIP}), glass and sizes alike, stands
 * in a strip of vertical {@link LegendSlider}s, each its name and value beside its track, as the
 * previous editor's Custom row drew them. A strip holds as many columns on a line as fit the
 * width at the least a legend needs beside a track ({@link LegendSlider#minColumnWidthPx}), and
 * wraps the rest onto balanced lines ({@link #stripLines}). The Look's stop slider is the one
 * horizontal slider. A row of segments stands beside its label when it has two short
 * choices, under it otherwise, and turns into a vertical list when a choice's word would not fit
 * its share of the width. Every restatement compares before it sets.</p>
 */
final class EditorCardSheet {

    /** Where the values come from and where the touches go. */
    interface Model {
        /** A slider's value, a segment's or chip's index, or 1/0 for a switch. */
        int value(@NonNull Row row);
        int from(@NonNull Row row);
        int to(@NonNull Row row);
        int step(@NonNull Row row);
        /** The value as the row prints and announces it ("72%", "56 dp"). */
        @NonNull String readout(@NonNull Row row, int value);
        /** Whether the value is the surface's own (the dot after the label). */
        boolean ownValue(@NonNull Row row);
        boolean enabled(@NonNull Row row);
        /** A second line: a disabled row's reason, a menu's current choice, an expander's state. */
        @Nullable CharSequence summary(@NonNull Row row);
        /** The names of a segments or chips row's choices. */
        @NonNull String[] choices(@NonNull Row row);
        /** A chips row's glyphs, one per choice, or null. */
        @Nullable int[] chipIcons(@NonNull Row row);
        /** What an expander row opens under itself, or null. */
        @Nullable View expanderContent(@NonNull Row row);
        /** A slider moved ({@code dragging} while the finger is on it), or a choice was made. */
        void onValue(@NonNull Row row, int value, boolean dragging);
        /** A slider's finger lifted. */
        void onReleased(@NonNull Row row);
        /** A menu, link, the wallpaper's Change or an expander was tapped. */
        void onAction(@NonNull Row row, @NonNull View anchor);

        /**
         * Whether the row stands in the card now: the Appearance card's All surfaces strip shows
         * only at the Custom stop. A strip whose columns are all put away is put away whole.
         */
        default boolean shown(@NonNull Row row) {
            return true;
        }

        /** The words over a glass strip that starts with {@code first}, or null for none. */
        @Nullable
        default CharSequence stripHeading(@NonNull Row first) {
            return null;
        }
    }

    /** What the header and the sheet itself report. */
    interface Listener {
        void onClose();
        /** The header's reset: the card's keys back as they were, with no question asked. */
        void onReset();
        /** The sheet stands at a new top edge: every frame of its motion, and while dragged. */
        void onSheetMoved(int detent, float topPx);

        /** The sheet came to rest: on a detent, or off screen. Once per motion. */
        default void onSheetSettled(int detent, float topPx) {}
    }

    static final int DETENT_HALF = 0;
    static final int DETENT_FULL = 1;

    static final int HEADER_DP = 64;
    static final int CORNER_DP = 24;
    /** The last row clears the navigation inset by this much at either detent. */
    static final int FOOT_CLEARANCE_DP = 16;
    private static final int ICON_COLUMN_DP = 56;
    private static final int EDGE_DP = 16;
    private static final int SLIDER_ROW_MIN_DP = 72;
    /** The gap between a strip's columns. */
    private static final int STRIP_GAP_DP = 12;
    private static final int SWITCH_ROW_MIN_DP = 56;
    private static final int SEGMENT_GAP_DP = 2;
    private static final int SEGMENT_HEIGHT_DP = 48;
    private static final int SEGMENT_PAD_DP = 8;
    /** The room between a label and the control beside it, its own-value dot's too. */
    private static final int ROW_GAP_DP = 28;
    /** A header button's touch target, and the gap the last one keeps from the edge. */
    private static final int HEADER_BUTTON_DP = 48;
    private static final int HEADER_END_DP = 8;
    private static final long MOVE_MS = 240L;

    @NonNull private final Context mContext;
    @NonNull private final Model mModel;
    @NonNull private final Listener mListener;
    @NonNull private final FrameLayout mSheet;
    @NonNull private final LinearLayout mColumn;
    @NonNull private final FrameLayout mHeader;
    @NonNull private final ImageView mHeaderIcon;
    @NonNull private final TextView mTitle;
    /** Shows or hides the card's object; gone on a card whose object cannot be hidden. */
    @NonNull private final MaterialButton mEye;
    @NonNull private final MaterialButton mReset;
    /** The glyph the eye shows now: open or struck through. */
    private int mEyeIcon = R.drawable.ic_ms_visibility;
    @NonNull private final ScrollView mScroll;
    @NonNull private final LinearLayout mRows;
    @NonNull private final Map<Row, RowViews> mRowViews = new EnumMap<>(Row.class);
    @Nullable private EditorCard mCard;
    private boolean mRestating;
    private int mDetent = DETENT_HALF;
    private int mHalfPx;
    private int mFullPx;
    private int mNavInsetPx;
    private boolean mShown;
    /** A finger is dragging the sheet by its header. */
    private boolean mDragging;

    EditorCardSheet(@NonNull Context context, @NonNull Model model, @NonNull Listener listener) {
        mContext = context;
        mModel = model;
        mListener = listener;
        mSheet = new FrameLayout(context);
        mSheet.setClickable(true);
        mSheet.setElevation(dp(1));
        GradientDrawable surface = new GradientDrawable();
        float corner = dp(CORNER_DP);
        surface.setCornerRadii(new float[] {corner, corner, corner, corner, 0, 0, 0, 0});
        surface.setColor(EditorViews.color(context,
            com.google.android.material.R.attr.colorSurfaceContainerLow));
        mSheet.setBackground(surface);
        mSheet.setVisibility(View.GONE);

        mColumn = new LinearLayout(context);
        mColumn.setOrientation(LinearLayout.VERTICAL);
        mSheet.addView(mColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        mHeader = new FrameLayout(context);
        mColumn.addView(mHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(HEADER_DP)));
        View handle = new View(context);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setCornerRadius(dp(2));
        handleShape.setColor(EditorViews.color(context,
            com.google.android.material.R.attr.colorOutline));
        handle.setBackground(handleShape);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(32), dp(4),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        handleParams.topMargin = dp(8);
        mHeader.addView(handle, handleParams);

        mHeaderIcon = new ImageView(context);
        mHeaderIcon.setImageTintList(ColorStateList.valueOf(EditorViews.color(context,
            com.google.android.material.R.attr.colorOnSurface)));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24),
            Gravity.START | Gravity.CENTER_VERTICAL);
        iconParams.setMarginStart(dp(EDGE_DP));
        mHeader.addView(mHeaderIcon, iconParams);

        mTitle = new TextView(context);
        EditorViews.textAppearance(mTitle, com.google.android.material.R.attr.textAppearanceTitleLarge);
        mTitle.setTextColor(EditorViews.color(context,
            com.google.android.material.R.attr.colorOnSurface));
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        androidx.core.view.ViewCompat.setAccessibilityHeading(mTitle, true);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START | Gravity.CENTER_VERTICAL);
        titleParams.setMarginStart(dp(ICON_COLUMN_DP));
        titleParams.setMarginEnd(headerButtonsDp(true));
        mHeader.addView(mTitle, titleParams);

        // From the end: ✕, reset where the ⋮ menu sat (it only held Reset and Copy to, and the
        // Match rows replaced Copy to), then the eye where the card has one.
        MaterialButton close = headerButton(R.drawable.ic_symbol_close,
            R.string.appearance_card_close);
        close.setOnClickListener(v -> mListener.onClose());
        mHeader.addView(close, headerButtonParams(0));

        mReset = headerButton(R.drawable.ic_ms_restart_alt, R.string.appearance_card_reset);
        mReset.setOnClickListener(v -> mListener.onReset());
        mHeader.addView(mReset, headerButtonParams(1));

        mEye = headerButton(R.drawable.ic_ms_visibility, R.string.appearance_row_visible);
        mEye.setCheckable(true);
        mEye.setOnClickListener(v -> {
            if (mRestating)
                return;
            boolean shown = mModel.value(Row.VISIBLE) != 0;
            mModel.onValue(Row.VISIBLE, shown ? 0 : 1, false);
            restateEye();
        });
        mEye.setVisibility(View.GONE);
        mHeader.addView(mEye, headerButtonParams(2));
        bindHeaderDrag();

        mScroll = new ScrollView(context);
        mScroll.setClipToPadding(false);
        mScroll.setVerticalScrollBarEnabled(true);
        mColumn.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f));
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mRows, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ------------------------------------------------------------------------------- placement

    @NonNull
    View view() {
        return mSheet;
    }

    void attach(@NonNull ViewGroup content) {
        if (mSheet.getParent() == content)
            return;
        if (mSheet.getParent() instanceof ViewGroup)
            ((ViewGroup) mSheet.getParent()).removeView(mSheet);
        content.addView(mSheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, mFullPx), Gravity.BOTTOM));
    }

    void detach() {
        if (mSheet.getParent() instanceof ViewGroup)
            ((ViewGroup) mSheet.getParent()).removeView(mSheet);
    }

    /**
     * The two detents' heights and the navigation inset the rows clear. The sheet takes the full
     * height as its own (laid out only when this changes) and the half by translation.
     */
    void setGeometry(int halfPx, int fullPx, int navInsetPx) {
        int full = Math.max(fullPx, halfPx);
        boolean changed = full != mFullPx || halfPx != mHalfPx || navInsetPx != mNavInsetPx;
        mHalfPx = halfPx;
        mFullPx = full;
        mNavInsetPx = navInsetPx;
        if (!changed)
            return;
        ViewGroup.LayoutParams params = mSheet.getLayoutParams();
        if (params != null && params.height != full) {
            params.height = full;
            mSheet.setLayoutParams(params);
        }
        if (mShown && mParked && full > 0) {
            // A card raised before the window was measured rises now, from below the screen.
            mParked = false;
            mSheet.setTranslationY(full);
            mSheet.setVisibility(View.VISIBLE);
            settle(mDetent, mParkedAnimate);
        } else if (mShown && !mParked) {
            settle(mDetent, false);
        }
    }

    /**
     * The card is up but the sheet has no detents yet: it waits out of sight, and
     * {@link #setGeometry} raises it ({@code mParkedAnimate}: with the slide or at once).
     */
    private boolean mParked;
    private boolean mParkedAnimate;

    /** How tall the sheet stands at {@code detent}. */
    int heightAt(int detent) {
        return detent == DETENT_FULL ? mFullPx : mHalfPx;
    }

    int detent() {
        return mDetent;
    }

    boolean isShown() {
        return mShown;
    }

    /** Whether a finger is dragging the sheet now: the frame follows it pose by pose. */
    boolean isDragging() {
        return mDragging;
    }

    @Nullable
    EditorCard card() {
        return mCard;
    }

    /**
     * The height tier 1 needs under the header, with the foot clearance under it, at
     * {@code widthPx}: what the half detent would like to show whole.
     */
    int tierOneHeightPx(int widthPx) {
        int height = dp(HEADER_DP) + mNavInsetPx + dp(FOOT_CLEARANCE_DP);
        int spec = View.MeasureSpec.makeMeasureSpec(Math.max(1, widthPx), View.MeasureSpec.EXACTLY);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < mRows.getChildCount(); i++) {
            View child = mRows.getChildAt(i);
            if (child.getTag() != TIER_ONE_TAG)
                break;
            child.measure(spec, unspecified);
            height += child.getMeasuredHeight();
        }
        return height;
    }

    private static final Object TIER_ONE_TAG = new Object();

    /**
     * The least the half detent may stand: the header, the first row and the foot clearance over
     * the navigation inset, at {@code widthPx}. Under it the card would show no control at all.
     */
    int minHalfHeightPx(int widthPx) {
        int height = dp(HEADER_DP) + mNavInsetPx + dp(FOOT_CLEARANCE_DP);
        if (mRows.getChildCount() == 0)
            return height + dp(SLIDER_ROW_MIN_DP);
        int spec = View.MeasureSpec.makeMeasureSpec(Math.max(1, widthPx), View.MeasureSpec.EXACTLY);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        View first = mRows.getChildAt(0);
        first.measure(spec, unspecified);
        return height + first.getMeasuredHeight();
    }

    // ----------------------------------------------------------------------------------- cards

    /**
     * Builds {@code card}'s rows and raises the sheet to its half detent. {@code resetLabel} names
     * the header's reset button for accessibility and its long-press ("Reset dock").
     */
    void show(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> rows,
              @NonNull String resetLabel, boolean animate) {
        boolean sameCard = mShown && card == mCard;
        mCard = card;
        mReset.setContentDescription(resetLabel);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(mReset, resetLabel);
        EditorViews.setVisible(mEye, card.hasEye());
        ViewGroup.LayoutParams titleParams = mTitle.getLayoutParams();
        if (titleParams instanceof FrameLayout.LayoutParams) {
            int end = headerButtonsDp(card.hasEye());
            if (((FrameLayout.LayoutParams) titleParams).getMarginEnd() != end) {
                ((FrameLayout.LayoutParams) titleParams).setMarginEnd(end);
                mTitle.setLayoutParams(titleParams);
            }
        }
        if (!sameCard) {
            build(card, rows);
            mScroll.scrollTo(0, 0);
        }
        restateEye();
        EditorViews.setText(mTitle, mContext.getString(card.titleRes));
        mHeaderIcon.setImageResource(card.iconRes);
        mSheet.setContentDescription(mContext.getString(card.titleRes));
        boolean wasShown = mShown;
        mShown = true;
        if (!wasShown || !sameCard)
            mDetent = DETENT_HALF;
        if (mFullPx <= 0) {
            // A restatement while parked keeps the slide the card was raised with.
            mParkedAnimate = (mParked && mParkedAnimate) || animate;
            mParked = true;
            mSheet.animate().cancel();
            mSheet.setVisibility(View.GONE);
            return;
        }
        if (mSheet.getVisibility() != View.VISIBLE) {
            mSheet.setTranslationY(mFullPx);
            mSheet.setVisibility(View.VISIBLE);
        }
        settle(mDetent, animate);
    }

    /** Takes the sheet down; {@code done} runs once it is off screen. */
    void hide(boolean animate, @Nullable Runnable done) {
        if (!mShown) {
            if (done != null) done.run();
            return;
        }
        mShown = false;
        mParked = false;
        mSheet.animate().cancel();
        float to = Math.max(mFullPx, mSheet.getHeight());
        if (!animate || ReducedMotion.isEnabled(mContext)) {
            mSheet.setTranslationY(to);
            mSheet.setVisibility(View.GONE);
            mListener.onSheetMoved(mDetent, sheetTopPx());
            mListener.onSheetSettled(mDetent, sheetTopPx());
            if (done != null) done.run();
            return;
        }
        mSheet.animate().translationY(to).setDuration(MOVE_MS).setInterpolator(Motion.settle())
            .setUpdateListener(a -> mListener.onSheetMoved(mDetent, sheetTopPx()))
            .withEndAction(() -> {
                mSheet.setVisibility(View.GONE);
                mSheet.animate().setUpdateListener(null);
                mListener.onSheetMoved(mDetent, sheetTopPx());
                mListener.onSheetSettled(mDetent, sheetTopPx());
                if (done != null) done.run();
            }).start();
    }

    /** The sheet's top edge in its parent, as it stands now. */
    float sheetTopPx() {
        ViewGroup parent = mSheet.getParent() instanceof ViewGroup
            ? (ViewGroup) mSheet.getParent() : null;
        int parentHeight = parent == null ? 0 : parent.getHeight();
        if (!mShown && mSheet.getVisibility() != View.VISIBLE)
            return parentHeight;
        return parentHeight - mFullPx + mSheet.getTranslationY();
    }

    /** Moves to {@code detent}. */
    void setDetent(int detent, boolean animate) {
        if (!mShown)
            return;
        settle(detent, animate);
    }

    private void settle(int detent, boolean animate) {
        mDetent = detent;
        float target = translationFor(detent);
        applyFootPadding(detent);
        mSheet.animate().cancel();
        if (!animate || ReducedMotion.isEnabled(mContext)
            || mSheet.getTranslationY() == target) {
            mSheet.setTranslationY(target);
            mListener.onSheetMoved(detent, sheetTopPx());
            mListener.onSheetSettled(detent, sheetTopPx());
            return;
        }
        mSheet.animate().translationY(target).setDuration(MOVE_MS).setInterpolator(Motion.settle())
            .setUpdateListener(a -> mListener.onSheetMoved(detent, sheetTopPx()))
            .withEndAction(() -> {
                mSheet.animate().setUpdateListener(null);
                mListener.onSheetMoved(detent, sheetTopPx());
                mListener.onSheetSettled(detent, sheetTopPx());
            }).start();
    }

    private float translationFor(int detent) {
        return detent == DETENT_FULL ? 0f : Math.max(0, mFullPx - mHalfPx);
    }

    /**
     * The rows' bottom padding: the navigation inset and the foot clearance, and at the half
     * detent the part of the sheet that stands below the screen, so the last row can still be
     * scrolled clear of the inset. Set only when it changes.
     */
    private void applyFootPadding(int detent) {
        int bottom = mNavInsetPx + dp(FOOT_CLEARANCE_DP) + Math.round(translationFor(detent));
        if (mRows.getPaddingBottom() != bottom)
            mRows.setPadding(0, 0, 0, bottom);
    }

    /** The bottom padding the rows stand on now; for a test. */
    @VisibleForTesting
    int footPaddingPx() {
        return mRows.getPaddingBottom();
    }

    @VisibleForTesting
    @NonNull
    ScrollView scroll() {
        return mScroll;
    }

    @VisibleForTesting
    @NonNull
    LinearLayout rows() {
        return mRows;
    }

    // ------------------------------------------------------------------------------ header drag

    /**
     * The header, its handle included, drags the sheet between its detents: a tap flips them, a
     * fling goes the way it was thrown, a slow release goes to the nearer one, and a drag well
     * below the half detent puts the card down.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindHeaderDrag() {
        ViewConfiguration config = ViewConfiguration.get(mContext);
        final int slop = config.getScaledTouchSlop();
        final float flingPxPerS = config.getScaledMinimumFlingVelocity() * 4f;
        final float[] down = new float[2];
        final boolean[] dragging = {false};
        final VelocityTracker[] tracker = {null};
        mHeader.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawY();
                    down[1] = mSheet.getTranslationY();
                    dragging[0] = false;
                    mSheet.animate().cancel();
                    if (tracker[0] != null) tracker[0].recycle();
                    tracker[0] = VelocityTracker.obtain();
                    trackRaw(tracker[0], event);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (tracker[0] != null) trackRaw(tracker[0], event);
                    float dy = event.getRawY() - down[0];
                    if (!dragging[0] && Math.abs(dy) > slop) {
                        dragging[0] = true;
                        mDragging = true;
                        if (view.getParent() != null)
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (dragging[0]) {
                        mSheet.setTranslationY(Math.max(0f, down[1] + dy));
                        mListener.onSheetMoved(mDetent, sheetTopPx());
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    mDragging = false;
                    float velocity = 0f;
                    if (tracker[0] != null) {
                        trackRaw(tracker[0], event);
                        tracker[0].computeCurrentVelocity(1000);
                        velocity = tracker[0].getYVelocity();
                        tracker[0].recycle();
                        tracker[0] = null;
                    }
                    if (!dragging[0]) {
                        // A tap on the header or its handle flips between the two detents.
                        settle(mDetent == DETENT_FULL ? DETENT_HALF : DETENT_FULL, true);
                        return true;
                    }
                    int target = releaseDetent(mSheet.getTranslationY(),
                        translationFor(DETENT_HALF), mHalfPx, velocity, flingPxPerS);
                    if (target < 0) {
                        mListener.onClose();
                        return true;
                    }
                    settle(target, true);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    if (tracker[0] != null) {
                        tracker[0].recycle();
                        tracker[0] = null;
                    }
                    settle(mDetent, true);
                    return true;
                default:
                    return true;
            }
        });
    }

    /** The tracker reads screen positions: the header moves under the finger while it drags. */
    private static void trackRaw(@NonNull VelocityTracker tracker, @NonNull MotionEvent event) {
        MotionEvent raw = MotionEvent.obtain(event);
        raw.setLocation(event.getRawX(), event.getRawY());
        tracker.addMovement(raw);
        raw.recycle();
    }

    /**
     * Where a released drag goes: {@link #DETENT_FULL}, {@link #DETENT_HALF}, or -1 to put the
     * card down. {@code atPx} is the sheet's translation (0 at the full detent,
     * {@code halfTranslationPx} at the half one); {@code velocityPxPerS} is positive downwards.
     */
    static int releaseDetent(float atPx, float halfTranslationPx, int halfHeightPx,
                             float velocityPxPerS, float flingPxPerS) {
        if (atPx > halfTranslationPx + halfHeightPx * 0.4f)
            return -1;
        if (velocityPxPerS <= -flingPxPerS)
            return DETENT_FULL;
        if (velocityPxPerS >= flingPxPerS)
            return atPx > halfTranslationPx ? -1 : DETENT_HALF;
        return Math.abs(atPx) < Math.abs(atPx - halfTranslationPx) ? DETENT_FULL : DETENT_HALF;
    }

    // ------------------------------------------------------------------------------------ rows

    /** One built row: its root, and whichever controls its kind has. */
    private static final class RowViews {
        @NonNull final Row row;
        @NonNull final View root;
        @Nullable TextView label;
        @Nullable TextView value;
        @Nullable TextView summary;
        @Nullable View dot;
        @Nullable Slider slider;
        @Nullable LegendSlider legend;
        /** The strip a glass column stands in, put away when all its columns are. */
        @Nullable Strip strip;
        @Nullable MaterialSwitch toggle;
        @Nullable MaterialButtonToggleGroup segments;
        @Nullable List<MaterialButton> buttons;
        @Nullable List<TextView> stopLabels;
        @Nullable FrameLayout expander;
        boolean tracking;

        RowViews(@NonNull Row row, @NonNull View root) {
            this.row = row;
            this.root = root;
        }
    }

    /** One strip: its root (heading and lines), the lines and the columns it holds. */
    private static final class Strip {
        @NonNull final LinearLayout root;
        @NonNull final List<LinearLayout> lines = new ArrayList<>();
        @NonNull final List<RowViews> members = new ArrayList<>();

        Strip(@NonNull LinearLayout root) {
            this.root = root;
        }
    }

    /**
     * The rows, tier by tier with a divider between tiers, every one of them shown. Strip rows
     * that follow one another share one strip, a column each, even across a tier's edge: the
     * strip stands in the tier of its first column.
     */
    private void build(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> specs) {
        mRows.removeAllViews();
        mRowViews.clear();
        int tier = 0;
        Strip strip = null;
        for (EditorRows.Spec spec : specs) {
            if (spec.row.kind == Kind.STRIP) {
                if (strip == null) {
                    if (tier != 0 && spec.tier != tier)
                        mRows.addView(divider());
                    tier = spec.tier;
                    strip = newStrip(spec.row);
                    if (spec.tier == 1)
                        strip.root.setTag(TIER_ONE_TAG);
                    mRows.addView(strip.root);
                }
                mRowViews.put(spec.row, stripColumn(spec.row, strip));
                continue;
            }
            if (strip != null)
                layOutStrip(strip);
            strip = null;
            if (tier != 0 && spec.tier != tier)
                mRows.addView(divider());
            tier = spec.tier;
            RowViews row = buildRow(spec.row);
            mRowViews.put(spec.row, row);
            if (spec.tier == 1)
                row.root.setTag(TIER_ONE_TAG);
            mRows.addView(row.root);
        }
        if (strip != null)
            layOutStrip(strip);
        restate();
    }

    /**
     * How a strip of {@code count} columns stands on lines of at most {@code capacity}: as few
     * lines as hold them, the columns spread evenly, the fuller lines first ({@code 9, 7} gives
     * {@code [5, 4]}, {@code 10, 4} gives {@code [4, 3, 3]}).
     */
    @NonNull
    static int[] stripLines(int count, int capacity) {
        if (count <= 0)
            return new int[0];
        int perLine = Math.max(1, capacity);
        int lines = (count + perLine - 1) / perLine;
        int base = count / lines;
        int extra = count % lines;
        int[] out = new int[lines];
        for (int i = 0; i < lines; i++)
            out[i] = base + (i < extra ? 1 : 0);
        return out;
    }

    /**
     * How many columns of at least {@code columnPx} stand on one line of {@code widthPx}, with
     * {@code gapPx} between neighbours; never fewer than one.
     */
    static int stripCapacity(int widthPx, int columnPx, int gapPx) {
        return Math.max(1, (widthPx + gapPx) / Math.max(1, columnPx + gapPx));
    }

    /**
     * Puts a strip's columns on their lines ({@link #stripLines}), every column as wide as the
     * fullest line's, so the tracks stand in one grid and a shorter last line leaves its end
     * empty rather than widening its columns.
     */
    private void layOutStrip(@NonNull Strip strip) {
        int count = strip.members.size();
        if (count == 0)
            return;
        LegendSlider first = strip.members.get(0).legend;
        int column = first == null ? 1 : first.minColumnWidthPx();
        // One line whenever the columns' own minimums fit it: the gap is what is left over, up
        // to STRIP_GAP_DP, so the tracks only part as far as there is room for.
        int[] lines = stripLines(count, stripCapacity(stripWidthPx(), column, 0));
        int gap = EditorRowFit.stripGapPx(stripWidthPx(), column, lines[0], dp(STRIP_GAP_DP));
        int length = mContext.getResources()
            .getDimensionPixelSize(R.dimen.appearance_editor_slider_length);
        int next = 0;
        for (int i = 0; i < lines.length; i++) {
            LinearLayout line = new LinearLayout(mContext);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setWeightSum(lines[0]);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, length);
            if (i > 0)
                lineParams.topMargin = gap;
            strip.root.addView(line, lineParams);
            strip.lines.add(line);
            for (int c = 0; c < lines[i]; c++) {
                RowViews member = strip.members.get(next++);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                if (c > 0)
                    params.setMarginStart(gap);
                line.addView(member.root, params);
            }
            int missing = lines[0] - lines[i];
            if (missing > 0) {
                // The missing columns' share, gaps included, so the columns keep the grid.
                LinearLayout.LayoutParams rest = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, missing);
                rest.setMarginStart(gap * missing);
                line.addView(new View(mContext), rest);
            }
        }
    }

    /** The width a strip's lines share: the sheet less the 16dp edge on either side. */
    private int stripWidthPx() {
        return Math.max(1, availableWidthPx() + dp(ICON_COLUMN_DP) - dp(EDGE_DP));
    }

    /** A strip's root: its heading, if the model gives one; the lines follow at its run's end. */
    @NonNull
    private Strip newStrip(@NonNull Row first) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(SLIDER_ROW_MIN_DP));
        root.setPadding(dp(EDGE_DP), dp(8), dp(EDGE_DP), dp(8));
        CharSequence heading = mModel.stripHeading(first);
        if (heading != null) {
            TextView title = new TextView(mContext);
            EditorViews.textAppearance(title,
                com.google.android.material.R.attr.textAppearanceTitleSmall);
            title.setTextColor(EditorViews.color(mContext,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setText(heading);
            androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            root.addView(title, params);
        }
        return new Strip(root);
    }

    /**
     * One strip column: a vertical slider whose legend is the row's name and value
     * ("Frost · 20%", "Corners · 16 dp", in the model's own units and range), reported to the
     * model as any slider is. {@link #layOutStrip} puts it on its line.
     */
    @NonNull
    private RowViews stripColumn(@NonNull Row row, @NonNull Strip strip) {
        LegendSlider slider = new LegendSlider(mContext);
        String name = mContext.getString(row.labelRes);
        slider.setContentDescription(name);
        slider.setLegend(value -> mContext.getString(R.string.appearance_strip_legend, name,
            mModel.readout(row, Math.round(value))));
        RowViews views = new RowViews(row, slider);
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (mRestating || !fromUser)
                return;
            mModel.onValue(row, Math.round(value), views.tracking);
        });
        slider.addOnSliderTouchListener(new LegendSlider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull LegendSlider s) {
                views.tracking = true;
            }

            @Override public void onStopTrackingTouch(@NonNull LegendSlider s) {
                views.tracking = false;
                mModel.onValue(row, Math.round(s.getValue()), false);
                mModel.onReleased(row);
            }
        });
        strip.members.add(views);
        views.legend = slider;
        views.strip = strip;
        return views;
    }

    @NonNull
    private RowViews buildRow(@NonNull Row row) {
        switch (row.kind) {
            case SWITCH: return switchRow(row);
            case SEGMENTS: return segmentsRow(row);
            case CHIPS: return chipsRow(row);
            case LOOK: return lookRow(row);
            case WALLPAPER: return wallpaperRow(row);
            case EXPANDER: return expanderRow(row);
            case ACTION: return commandRow(row);
            case MENU:
            case LINK:
            default:
                return actionRow(row);
        }
    }

    @NonNull
    private RowViews switchRow(@NonNull Row row) {
        LinearLayout root = horizontalRow(SWITCH_ROW_MIN_DP);
        RowViews views = new RowViews(row, root);
        root.addView(iconView(row.iconRes));
        root.addView(labelColumn(row, views),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(mContext);
        toggle.setContentDescription(mContext.getString(row.labelRes));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!mRestating)
                mModel.onValue(row, checked ? 1 : 0, false);
        });
        root.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOnClickListener(v -> { if (toggle.isEnabled()) toggle.toggle(); });
        views.toggle = toggle;
        return views;
    }

    @NonNull
    private RowViews segmentsRow(@NonNull Row row) {
        String[] choices = mModel.choices(row);
        int width = availableWidthPx();
        // The buttons first, so their own paint decides where they fit.
        List<MaterialButton> buttons = new ArrayList<>(choices.length);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        float widest = 0f;
        float textSum = 0f;
        for (String choice : choices) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.segment_button,
                null, false);
            button.setId(View.generateViewId());
            // No check mark (wireframe 02): the container colour carries the choice, and a mark's
            // reserved room would stand every unchosen word off its centre.
            button.setIcon(null);
            button.setText(choice);
            // Little side padding, so the words and the control fit beside the label; the height
            // keeps the 48dp touch target (the style's own insets draw the pill slimmer).
            button.setPaddingRelative(dp(SEGMENT_PAD_DP), button.getPaddingTop(),
                dp(SEGMENT_PAD_DP), button.getPaddingBottom());
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(dp(SEGMENT_HEIGHT_DP));
            button.setEllipsize(TextUtils.TruncateAt.END);
            float text = button.getPaint().measureText(choice);
            textSum += text;
            widest = Math.max(widest, text + 2 * dp(SEGMENT_PAD_DP));
            buttons.add(button);
        }
        // One line when the label and the segments fit (EditorRowFit); the words shrink in steps,
        // never below SEGMENT_MIN_TEXT_SCALE and never cut, before the row stacks.
        float scale = choices.length < 2 ? 0f : EditorRowFit.segmentsScale(width, labelPx(row),
            dp(ROW_GAP_DP), textSum, choices.length, dp(SEGMENT_PAD_DP), dp(SEGMENT_GAP_DP),
            SEGMENT_MIN_TEXT_SCALE, 0.05f);
        boolean inline = scale > 0f;
        if (inline && scale < 1f) {
            for (MaterialButton button : buttons)
                button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    button.getTextSize() * scale);
        }
        int share = choices.length == 0 ? width
            : (width - dp(SEGMENT_GAP_DP) * (choices.length - 1)) / choices.length;
        boolean vertical = !inline && widest > share;
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(inline ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setGravity(inline ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        root.setMinimumHeight(dp(SWITCH_ROW_MIN_DP));
        int edge = inline ? dp(EDGE_DP) : 0;
        int pad = inline ? dp(4) : dp(8);
        root.setPadding(edge, pad, edge, pad);
        RowViews views = new RowViews(row, root);
        if (inline) {
            root.addView(iconView(row.iconRes));
            root.addView(labelColumn(row, views),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            root.addView(labelLine(row, views, false));
        }
        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(mContext);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setSpacing(dp(SEGMENT_GAP_DP));
        group.setContentDescription(mContext.getString(row.labelRes));
        group.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (MaterialButton button : buttons) {
            button.setSingleLine(!vertical);
            LinearLayout.LayoutParams params = vertical
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                : inline
                    ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            group.addView(button, params);
        }
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (mRestating || !isChecked)
                return;
            for (int i = 0; i < buttons.size(); i++) {
                if (buttons.get(i).getId() == checkedId) {
                    mModel.onValue(row, i, false);
                    return;
                }
            }
        });
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
            inline ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!inline) {
            groupParams.setMarginStart(dp(ICON_COLUMN_DP));
            groupParams.setMarginEnd(dp(EDGE_DP));
            groupParams.topMargin = dp(8);
        }
        root.addView(group, groupParams);
        views.segments = group;
        views.buttons = buttons;
        return views;
    }

    @NonNull
    private RowViews chipsRow(@NonNull Row row) {
        String[] choices = mModel.choices(row);
        int[] icons = mModel.chipIcons(row);
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(8));
        RowViews views = new RowViews(row, root);
        root.addView(labelLine(row, views, false));
        LinearLayout chips = new LinearLayout(mContext);
        List<MaterialButton> buttons = new ArrayList<>(choices.length);
        float widestWord = 0f;
        for (int i = 0; i < choices.length; i++) {
            MaterialButton chip = new MaterialButton(mContext, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(choices[i]);
            // Two lines rather than a cut word: a third of a phone is narrow at a large text size.
            chip.setMaxLines(2);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPaddingRelative(dp(4), dp(8), dp(4), dp(8));
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setMinHeight(dp(56));
            // The outline is drawn inside the button's insets; without them the glyph and its
            // word stand inside it rather than running under its bottom edge.
            chip.setInsetTop(0);
            chip.setInsetBottom(0);
            chip.setCornerRadius(dp(12));
            chip.setCheckable(true);
            for (String word : choices[i].split(" "))
                widestWord = Math.max(widestWord, chip.getPaint().measureText(word)
                    + chip.getPaddingLeft() + chip.getPaddingRight() + dp(4));
            final int index = i;
            chip.setOnClickListener(v -> {
                if (!mRestating)
                    mModel.onValue(row, index, false);
                restate(row);
            });
            buttons.add(chip);
        }
        // Side by side while every word fits its third; one under another, glyph first, when not.
        int share = choices.length == 0 ? 1
            : (availableWidthPx() - dp(8) * (choices.length - 1)) / choices.length;
        boolean stacked = widestWord > share;
        chips.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.size(); i++) {
            MaterialButton chip = buttons.get(i);
            if (icons != null && i < icons.length) {
                // The gravity before the glyph: a glyph set first goes on as a start drawable,
                // the word is measured beside it, and moving it on top at layout left the word
                // centred in that narrower box, half a glyph left of the glyph's centre.
                chip.setIconGravity(stacked ? MaterialButton.ICON_GRAVITY_TEXT_START
                    : MaterialButton.ICON_GRAVITY_TOP);
                chip.setIconPadding(dp(stacked ? 8 : 2));
                chip.setIconResource(icons[i]);
                if (!stacked && chip.getIcon() != null) {
                    // Glyph over word: the tile is as tall as both and its padding, at any text
                    // size; the glyph is placed only at layout, too late for the measure to count.
                    int glyph = chip.getIconSize() > 0 ? chip.getIconSize()
                        : chip.getIcon().getIntrinsicHeight();
                    android.graphics.Paint.FontMetricsInt metrics =
                        chip.getPaint().getFontMetricsInt();
                    int line = Math.max(chip.getLineHeight(), metrics.bottom - metrics.top);
                    int need = chip.getPaddingTop() + glyph + chip.getIconPadding() + line
                        + chip.getPaddingBottom();
                    chip.setMinHeight(Math.max(dp(56), need));
                    chip.setMinimumHeight(Math.max(dp(56), need));
                }
            }
            LinearLayout.LayoutParams params = stacked
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                if (stacked) params.topMargin = dp(8);
                else params.setMarginStart(dp(8));
            }
            chips.addView(chip, params);
        }
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.setMarginStart(dp(ICON_COLUMN_DP));
        chipsParams.setMarginEnd(dp(EDGE_DP));
        chipsParams.topMargin = dp(8);
        root.addView(chips, chipsParams);
        views.buttons = buttons;
        return views;
    }

    @NonNull
    private RowViews lookRow(@NonNull Row row) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(8), 0, dp(8));
        RowViews views = new RowViews(row, root);
        root.addView(labelLine(row, views, true));
        Slider slider = newSlider();
        slider.setContentDescription(mContext.getString(row.labelRes));
        slider.setLabelFormatter(value -> mModel.readout(row, Math.round(value)));
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (mRestating || !fromUser)
                return;
            int v = Math.round(value);
            EditorViews.setText(views.value, mModel.readout(row, v));
            styleStops(views, v);
            mModel.onValue(row, v, views.tracking);
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {
                views.tracking = true;
            }

            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                views.tracking = false;
                mModel.onValue(row, Math.round(s.getValue()), false);
                mModel.onReleased(row);
            }
        });
        LinearLayout.LayoutParams sliderParams = sliderParams(slider);
        root.addView(slider, sliderParams);
        LinearLayout stops = new LinearLayout(mContext);
        stops.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = mModel.choices(row);
        List<TextView> labels = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            TextView stop = new TextView(mContext);
            EditorViews.textAppearance(stop, com.google.android.material.R.attr.textAppearanceLabelMedium);
            stop.setText(names[i]);
            stop.setGravity(Gravity.CENTER);
            stop.setSingleLine(true);
            stop.setEllipsize(TextUtils.TruncateAt.END);
            stop.setMinHeight(dp(32));
            final int index = i;
            stop.setOnClickListener(v -> {
                styleStops(views, index);
                mModel.onValue(row, index, false);
                mModel.onReleased(row);
            });
            stops.addView(stop, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            labels.add(stop);
        }
        LinearLayout.LayoutParams stopsParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Under the slider's own reach, so each stop's word stands under its stop.
        stopsParams.setMarginStart(sliderParams.getMarginStart());
        stopsParams.setMarginEnd(sliderParams.getMarginEnd());
        root.addView(stops, stopsParams);
        views.slider = slider;
        views.stopLabels = labels;
        return views;
    }

    private void styleStops(@NonNull RowViews views, int current) {
        if (views.stopLabels == null)
            return;
        int on = EditorViews.color(mContext, androidx.appcompat.R.attr.colorPrimary);
        int off = EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < views.stopLabels.size(); i++) {
            TextView label = views.stopLabels.get(i);
            int color = i == current ? on : off;
            if (label.getCurrentTextColor() != color)
                label.setTextColor(color);
        }
    }

    @NonNull
    private RowViews wallpaperRow(@NonNull Row row) {
        LinearLayout root = horizontalRow(SWITCH_ROW_MIN_DP);
        RowViews views = new RowViews(row, root);
        root.addView(iconView(row.iconRes));
        root.addView(labelColumn(row, views),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton change = new MaterialButton(mContext, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        change.setText(R.string.appearance_wallpaper_change);
        change.setSingleLine(true);
        change.setOnClickListener(v -> mModel.onAction(row, v));
        root.addView(change, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)));
        MaterialSwitch toggle = new MaterialSwitch(mContext);
        toggle.setContentDescription(mContext.getString(R.string.appearance_wallpaper_shown));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!mRestating)
                mModel.onValue(row, checked ? 1 : 0, false);
        });
        root.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        views.toggle = toggle;
        return views;
    }

    @NonNull
    private RowViews expanderRow(@NonNull Row row) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        RowViews views = new RowViews(row, root);
        LinearLayout line = horizontalRow(SWITCH_ROW_MIN_DP);
        line.addView(iconView(row.iconRes));
        line.addView(labelColumn(row, views),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chevron = new ImageView(mContext);
        chevron.setImageResource(R.drawable.ic_editor_expand_more);
        chevron.setImageTintList(ColorStateList.valueOf(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        line.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        root.addView(line);
        FrameLayout panel = new FrameLayout(mContext);
        panel.setVisibility(View.GONE);
        root.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        line.setOnClickListener(v -> {
            boolean open = panel.getVisibility() != View.VISIBLE;
            if (open && panel.getChildCount() == 0) {
                View content = mModel.expanderContent(row);
                if (content != null) {
                    if (content.getParent() instanceof ViewGroup)
                        ((ViewGroup) content.getParent()).removeView(content);
                    panel.addView(content, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
            panel.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setRotation(open ? 180f : 0f);
            mModel.onAction(row, v);
            if (open)
                setDetent(DETENT_FULL, true);
        });
        views.expander = panel;
        return views;
    }

    @NonNull
    private RowViews actionRow(@NonNull Row row) {
        LinearLayout root = horizontalRow(SWITCH_ROW_MIN_DP);
        RowViews views = new RowViews(row, root);
        root.addView(iconView(row.iconRes));
        root.addView(labelColumn(row, views),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chevron = new ImageView(mContext);
        chevron.setImageResource(row.kind == Kind.MENU ? R.drawable.ic_editor_expand_more
            : R.drawable.ic_editor_chevron_right);
        chevron.setImageTintList(ColorStateList.valueOf(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        root.setOnClickListener(v -> mModel.onAction(row, v));
        return views;
    }

    /**
     * A row that does one thing in place, named by a text button at its end: the Match rows'
     * Copy, which reads Undo while the copy can be taken back ({@link Model#readout}).
     */
    @NonNull
    private RowViews commandRow(@NonNull Row row) {
        ActionRow action = ActionRow.build(mContext, row.iconRes,
            mContext.getString(row.labelRes), null, v -> mModel.onAction(row, v));
        RowViews views = new RowViews(row, action.root);
        views.label = action.title;
        views.value = action.action;
        return views;
    }

    // ------------------------------------------------------------------------------ restating

    /** Every row read again from the model, the header's eye with them. */
    void restate() {
        for (RowViews views : mRowViews.values())
            restate(views);
        restateEye();
    }

    /**
     * The eye read again: open while the object shows, struck through while it is hidden. Its
     * long-press names it, or says why it reads as it does (the other orientation's note).
     */
    private void restateEye() {
        EditorCard card = mCard;
        if (card == null || !card.hasEye())
            return;
        boolean shown = mModel.value(Row.VISIBLE) != 0;
        boolean enabled = mModel.enabled(Row.VISIBLE);
        mRestating = true;
        try {
            if (mEye.isChecked() != shown)
                mEye.setChecked(shown);
            int icon = shown ? R.drawable.ic_ms_visibility : R.drawable.ic_ms_visibility_off;
            if (mEyeIcon != icon) {
                mEyeIcon = icon;
                mEye.setIconResource(icon);
            }
            if (mEye.isEnabled() != enabled)
                mEye.setEnabled(enabled);
            int container = shown ? EditorViews.color(mContext,
                com.google.android.material.R.attr.colorSecondaryContainer) : 0;
            int content = EditorViews.color(mContext, shown
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
            mEye.setBackgroundTintList(ColorStateList.valueOf(container));
            mEye.setIconTint(ColorStateList.valueOf(content));
            CharSequence note = mModel.summary(Row.VISIBLE);
            androidx.appcompat.widget.TooltipCompat.setTooltipText(mEye,
                note != null && note.length() > 0 ? note
                    : mContext.getString(R.string.appearance_row_visible));
        } finally {
            mRestating = false;
        }
    }

    /** The eye, for a test. */
    @VisibleForTesting
    @NonNull
    MaterialButton eyeButton() {
        return mEye;
    }

    /** The reset button, for a test. */
    @VisibleForTesting
    @NonNull
    MaterialButton resetButton() {
        return mReset;
    }

    /** A strip's line stands while any of its columns does, and the strip while any line does. */
    private static void restateStrip(@NonNull Strip strip) {
        boolean any = false;
        for (LinearLayout line : strip.lines) {
            boolean lineShown = false;
            for (int i = 0; i < line.getChildCount(); i++) {
                View child = line.getChildAt(i);
                if (child instanceof LegendSlider && child.getVisibility() == View.VISIBLE) {
                    lineShown = true;
                    break;
                }
            }
            EditorViews.setVisible(line, lineShown);
            any |= lineShown;
        }
        EditorViews.setVisible(strip.root, any);
    }

    /** One row read again from the model. */
    void restate(@NonNull Row row) {
        RowViews views = mRowViews.get(row);
        if (views != null)
            restate(views);
    }

    private void restate(@NonNull RowViews views) {
        Row row = views.row;
        boolean enabled = mModel.enabled(row);
        int value = mModel.value(row);
        mRestating = true;
        try {
            EditorViews.setVisible(views.root, mModel.shown(row));
            if (views.strip != null)
                restateStrip(views.strip);
            if (views.root.isEnabled() != enabled)
                views.root.setEnabled(enabled);
            views.root.setAlpha(enabled ? 1f : 0.5f);
            if (views.dot != null)
                EditorViews.setVisible(views.dot, mModel.ownValue(row));
            if (row.kind == Kind.ACTION && views.value != null)
                EditorViews.setText(views.value, mModel.readout(row, value));
            if (views.summary != null) {
                CharSequence summary = mModel.summary(row);
                EditorViews.setText(views.summary, summary);
                EditorViews.setVisible(views.summary, summary != null && summary.length() > 0);
            }
            if (views.slider != null && !views.tracking) {
                Slider slider = views.slider;
                int from = mModel.from(row);
                int to = Math.max(from + 1, mModel.to(row));
                int step = Math.max(1, mModel.step(row));
                int clamped = Math.max(from, Math.min(to, value));
                clamped = from + Math.round((clamped - from) / (float) step) * step;
                if (clamped > to) clamped -= step;
                // Validated lazily, at the next draw: the four land together.
                if (slider.getValueFrom() != from) slider.setValueFrom(from);
                if (slider.getValueTo() != to) slider.setValueTo(to);
                if (slider.getStepSize() != step) slider.setStepSize(step);
                if (slider.getValue() != clamped) slider.setValue(clamped);
                if (slider.isEnabled() != enabled)
                    slider.setEnabled(enabled);
                EditorViews.setText(views.value, mModel.readout(row, clamped));
                styleStops(views, clamped);
            }
            if (views.legend != null && !views.tracking) {
                LegendSlider legend = views.legend;
                int from = mModel.from(row);
                int to = Math.max(from + 1, mModel.to(row));
                int step = Math.max(1, mModel.step(row));
                if (legend.getValueFrom() != from) legend.setValueFrom(from);
                if (legend.getValueTo() != to) legend.setValueTo(to);
                if (legend.getStepSize() != step) legend.setStepSize(step);
                if (legend.getValue() != value) legend.setValue(value);
                legend.setMarked(mModel.ownValue(row));
            }
            if (views.toggle != null) {
                boolean on = value != 0;
                if (views.toggle.isChecked() != on)
                    views.toggle.setChecked(on);
                if (views.toggle.isEnabled() != enabled)
                    views.toggle.setEnabled(enabled);
            }
            if (views.segments != null && views.buttons != null) {
                if (value >= 0 && value < views.buttons.size()) {
                    int id = views.buttons.get(value).getId();
                    if (views.segments.getCheckedButtonId() != id)
                        views.segments.check(id);
                }
                if (views.segments.isEnabled() != enabled)
                    views.segments.setEnabled(enabled);
            } else if (views.buttons != null) {
                for (int i = 0; i < views.buttons.size(); i++) {
                    MaterialButton chip = views.buttons.get(i);
                    if (chip.isChecked() != (i == value))
                        chip.setChecked(i == value);
                }
            }
        } finally {
            mRestating = false;
        }
    }

    // ------------------------------------------------------------------------------- builders

    /**
     * A slider's place in its row: its track from the text column to the row's edge, inset by the
     * thumb's radius at the end, so a thumb at either end stays inside the 16dp margin. The
     * slider pads its own track by {@link Slider#getTrackSidePadding()}; the margins make up
     * the rest.
     */
    @NonNull
    private LinearLayout.LayoutParams sliderParams(@NonNull Slider slider) {
        int side = slider.getTrackSidePadding();
        int thumbRadius = slider.getThumbWidth() / 2;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.setMarginStart(sliderMarginPx(dp(ICON_COLUMN_DP), 0, side));
        params.setMarginEnd(sliderMarginPx(dp(EDGE_DP), thumbRadius, side));
        return params;
    }

    /**
     * The margin that puts a slider's track end {@code edgePx + insetPx} from the row's edge when
     * the slider pads its track by {@code sidePaddingPx}; never negative.
     */
    static int sliderMarginPx(int edgePx, int insetPx, int sidePaddingPx) {
        return Math.max(0, edgePx + insetPx - sidePaddingPx);
    }

    @NonNull
    private Slider newSlider() {
        Slider slider = new Slider(mContext);
        slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        slider.setTickVisible(false);
        slider.setTrackHeight(dp(4));
        slider.setThumbWidth(dp(20));
        slider.setThumbHeight(dp(20));
        slider.setThumbTrackGapSize(0);
        slider.setTrackStopIndicatorSize(0);
        slider.setHaloRadius(dp(20));
        slider.setValueFrom(0f);
        slider.setValueTo(1f);
        slider.setStepSize(1f);
        slider.setValue(0f);
        return slider;
    }

    /** The label line over the Look's slider or under-placed segments: icon, label, dot, value. */
    @NonNull
    private LinearLayout labelLine(@NonNull Row row, @NonNull RowViews views, boolean withValue) {
        LinearLayout line = horizontalRow(24);
        line.setPadding(dp(EDGE_DP), 0, dp(EDGE_DP), 0);
        line.addView(iconView(row.iconRes));
        TextView label = label(row.labelRes);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        line.addView(label, labelParams);
        views.label = label;
        View dot = ownDot();
        line.addView(dot);
        views.dot = dot;
        View spring = new View(mContext);
        line.addView(spring, new LinearLayout.LayoutParams(0, 1, 1f));
        if (withValue) {
            TextView value = new TextView(mContext);
            EditorViews.textAppearance(value, com.google.android.material.R.attr.textAppearanceLabelLarge);
            value.setTextColor(EditorViews.color(mContext,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
            value.setSingleLine(true);
            value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            line.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            views.value = value;
        }
        return line;
    }

    /** The label and its summary line, beside a switch, a menu or inline segments. */
    @NonNull
    private LinearLayout labelColumn(@NonNull Row row, @NonNull RowViews views) {
        LinearLayout column = new LinearLayout(mContext);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(mContext);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = label(row.labelRes);
        top.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        View dot = ownDot();
        top.addView(dot);
        column.addView(top);
        TextView summary = new TextView(mContext);
        EditorViews.textAppearance(summary, com.google.android.material.R.attr.textAppearanceBodyMedium);
        summary.setTextColor(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setVisibility(View.GONE);
        column.addView(summary);
        views.label = label;
        views.dot = dot;
        views.summary = summary;
        return column;
    }

    @NonNull
    private TextView label(int textRes) {
        TextView label = new TextView(mContext);
        EditorViews.textAppearance(label, com.google.android.material.R.attr.textAppearanceBodyLarge);
        label.setTextColor(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurface));
        label.setText(textRes);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        return label;
    }

    /** The small dot after a label: this surface's own value. */
    @NonNull
    private View ownDot() {
        View dot = new View(mContext);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(EditorViews.color(mContext, androidx.appcompat.R.attr.colorPrimary));
        dot.setBackground(shape);
        dot.setContentDescription(mContext.getString(R.string.appearance_row_own_value));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(6), dp(6));
        params.setMarginStart(dp(6));
        dot.setLayoutParams(params);
        dot.setVisibility(View.GONE);
        return dot;
    }

    @NonNull
    private ImageView iconView(int iconRes) {
        ImageView icon = new ImageView(mContext);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
        params.setMarginEnd(dp(ICON_COLUMN_DP - EDGE_DP - 24));
        icon.setLayoutParams(params);
        return icon;
    }

    @NonNull
    private LinearLayout horizontalRow(int minHeightDp) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(minHeightDp));
        row.setPadding(dp(EDGE_DP), 0, dp(EDGE_DP), 0);
        return row;
    }

    @NonNull
    private View divider() {
        View line = new View(mContext);
        line.setBackgroundColor(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        params.topMargin = dp(8);
        params.bottomMargin = dp(8);
        params.setMarginStart(dp(EDGE_DP));
        params.setMarginEnd(dp(EDGE_DP));
        line.setLayoutParams(params);
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return line;
    }

    /** The room the header's buttons take at its end: ✕ and reset, and the eye with it. */
    private int headerButtonsDp(boolean withEye) {
        return dp(HEADER_END_DP + HEADER_BUTTON_DP * (withEye ? 3 : 2) + 8);
    }

    /** The {@code index}th header button from the end. */
    @NonNull
    private FrameLayout.LayoutParams headerButtonParams(int index) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(HEADER_BUTTON_DP),
            dp(HEADER_BUTTON_DP), Gravity.END | Gravity.CENTER_VERTICAL);
        params.setMarginEnd(dp(HEADER_END_DP + HEADER_BUTTON_DP * index));
        return params;
    }

    @NonNull
    private MaterialButton headerButton(int iconRes, int descriptionRes) {
        MaterialButton button = new MaterialButton(mContext, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(iconRes);
        button.setIconTint(ColorStateList.valueOf(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurface)));
        button.setContentDescription(mContext.getString(descriptionRes));
        return button;
    }

    /** The width a row's controls share: the sheet less the icon column and the end edge. */
    private int availableWidthPx() {
        int sheet = mSheet.getWidth() > 0 ? mSheet.getWidth()
            : (mSheet.getParent() instanceof View && ((View) mSheet.getParent()).getWidth() > 0
                ? ((View) mSheet.getParent()).getWidth()
                : mContext.getResources().getDisplayMetrics().widthPixels);
        return Math.max(1, sheet - dp(ICON_COLUMN_DP) - dp(EDGE_DP));
    }

    /** The smallest share of its text size a three-segment row shrinks to to stand inline. */
    static final float SEGMENT_MIN_TEXT_SCALE = 0.75f;

    /** The width a row's label needs, in the label's own text appearance. */
    private float labelPx(@NonNull Row row) {
        TextView probe = label(row.labelRes);
        return probe.getPaint().measureText(mContext.getString(row.labelRes));
    }

    private int dp(float value) {
        return EditorViews.dp(mContext, value);
    }
}
