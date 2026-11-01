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
import androidx.annotation.StringRes;
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
import com.termux.app.surfaces.rows.SliderRow;
import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One card of the Appearance editor (appearance-and-settings-rework §3, §7, §8; wireframes 02–04):
 * a persistent bottom sheet with a 72dp header (a 24dp grab zone with the handle, then a 48dp row:
 * the object's icon and name, its eye where the object can be hidden, and reset; editor-redesign
 * §1 and the developer changes of 10-26 evening) over its rows, tier by tier, every row always
 * shown; the rows scroll. Views only: every value is read from {@link Model} and every touch
 * reported to it, so the same sheet is every card.
 *
 * <p><b>Detents.</b> Three: peek (the header alone over the navigation inset), half and full. The
 * sheet is never put down while the editor is open: a drag or Back takes it to peek, and the
 * handle stays on screen to pull it up again. It is laid out once at the full detent's height and
 * stands at the others by translation, so neither a detent change nor a drag of the header lays
 * anything out per frame. Below the full detent the rows' bottom padding grows by the part of the
 * sheet below the screen, so the last row still scrolls clear of the navigation inset by
 * 16dp.</p>
 *
 * <p><b>Rows.</b> Content-driven heights (a switch row 56dp), labels on one line, ellipsized.
 * Every value slider ({@link Kind#SLIDER}), glass and sizes alike, is a horizontal slider row
 * ({@link SliderRow}): the glyph, the name and its source dot, the value, and the track under
 * them; a run of them stands one under another under its heading. The Look's stop slider is a
 * Material slider of its own. A row of segments stands beside its label when it has two short
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
        /**
         * Whether choice {@code index} of a segments row can be picked; a greyed choice stays in
         * its place and the row's summary says why (the icon size the pinned apps cap).
         */
        default boolean choiceEnabled(@NonNull Row row, int index) { return true; }
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

        /**
         * A row the card draws itself from the row kinds under {@code surfaces/rows} (a section
         * with its header, preview tiles, a tile grid, glyph segments), {@code widthPx} wide; or
         * null for the sheet's own row of the row's {@link Kind}. Asked once per card open, for
         * every row before its Kind is looked at, a strip's columns too; a card that draws several
         * rows as one (a strip run, a grid of tiles) gives the rest an empty view.
         */
        @Nullable
        default View rowView(@NonNull Row row, int widthPx) {
            return null;
        }

        /** Reads a {@link #rowView} again from the values, as {@link #restate()} does any row. */
        default void restateRowView(@NonNull Row row, @NonNull View view) {}

        /** The Where it applies chip for the card up, or null where the card has none. */
        @Nullable
        default Chip whereChip() { return null; }

        /** The What it applies to chip for the card up, or null (the Appearance card). */
        @Nullable
        default Chip whatChip() { return null; }

        /** The Where chip's menu picked option {@code index}. */
        default void pickWhere(int index) {}

        /** The What chip's menu picked option {@code index}. */
        default void pickWhat(int index) {}

        /** Where a row's value comes from, for its dot; a model with no grid says own or not. */
        @NonNull
        default RowSource source(@NonNull Row row) {
            return ownValue(row) ? RowSource.ELEMENT : RowSource.NONE;
        }

        /** Where a row's value comes from, in words ("Dock on Home"), or null for none. */
        @Nullable
        default CharSequence sourceName(@NonNull Row row) { return null; }

        /** Whether the chips' cell holds the row's value itself: Reset this row is offered. */
        default boolean resettable(@NonNull Row row) { return false; }

        /** Reset this row: the chips' cell gives its value up and follows again. */
        default void resetRow(@NonNull Row row) {}

        /**
         * A long press on the name of a row the card drew itself ({@link #rowView}): the card
         * opens the sheet's row menu for it ({@link EditorCardSheet#openRowMenu}).
         */
        default void openRowMenu(@NonNull View anchor, @NonNull Row row) {}

        /** Whether the Where chip stands on a page: the card's rows read and write the page. */
        default boolean scopeOnPage() { return false; }

        /** Puts the Where chip on the page (true) or Everywhere. */
        default void setScopeOnPage(boolean onPage) {}
    }

    /** What the header and the sheet itself report. */
    interface Listener {
        /** The header's reset: the card's keys back as they were, with no question asked. */
        void onReset();
        /** The sheet stands at a new top edge: every frame of its motion, and while dragged. */
        void onSheetMoved(int detent, float topPx);

        /** The sheet came to rest: on a detent, or off screen. Once per motion. */
        default void onSheetSettled(int detent, float topPx) {}
    }

    static final int DETENT_HALF = 0;
    static final int DETENT_FULL = 1;
    /** The header alone: the handle, the icon, the title and the header's buttons. */
    static final int DETENT_PEEK = 2;

    /** The grab zone over the header's row: the handle in it, the drag starting anywhere on it. */
    static final int GRAB_DP = 24;
    /** The header's row: icon, title and buttons, as tall as a button's touch target. */
    static final int HEADER_ROW_DP = 48;
    static final int HEADER_DP = GRAB_DP + HEADER_ROW_DP;
    static final int CORNER_DP = 24;
    /** The last row clears the navigation inset by this much at either detent. */
    static final int FOOT_CLEARANCE_DP = 16;
    private static final int ICON_COLUMN_DP = 56;
    private static final int EDGE_DP = 16;
    private static final int SLIDER_ROW_MIN_DP = 72;
    private static final int SWITCH_ROW_MIN_DP = 56;
    private static final int SEGMENT_GAP_DP = 2;
    private static final int SEGMENT_HEIGHT_DP = 48;
    private static final int SEGMENT_PAD_DP = 8;
    /** The room between a label and the control beside it, its own-value dot's too. */
    private static final int ROW_GAP_DP = 28;
    /** A header button's touch target, and the gap the last one keeps from the edge. */
    private static final int HEADER_BUTTON_DP = 48;
    private static final int HEADER_END_DP = 8;
    private static final long MOVE_MS = EditorMotion.Use.SHEET_DETENT.settleMs();

    @NonNull private final Context mContext;
    @NonNull private final Model mModel;
    @NonNull private final Listener mListener;
    @NonNull private final FrameLayout mSheet;
    @NonNull private final LinearLayout mColumn;
    @NonNull private final LinearLayout mHeader;
    @NonNull private final FrameLayout mGrab;
    @NonNull private final ImageView mHeaderIcon;
    @NonNull private final TextView mTitle;
    /**
     * One of a card's two chips: its question (the menu's caption), its options and the one
     * picked. The first option is the wide one (Everywhere, All glass); any other reads narrowed.
     */
    public static final class Chip {
        @NonNull public final CharSequence question;
        @NonNull public final CharSequence[] labels;
        public final int selected;

        public Chip(@NonNull CharSequence question, @NonNull CharSequence[] labels, int selected) {
            this.question = question;
            this.labels = labels;
            this.selected = selected;
        }

        /** Whether the chip stands on something narrower than its first option. */
        public boolean narrowed() {
            return selected > 0;
        }

        @NonNull public CharSequence label() {
            return labels[Math.max(0, Math.min(labels.length - 1, selected))];
        }
    }

    /** Shows or hides the card's object; gone on a card whose object cannot be hidden. */
    @NonNull private final MaterialButton mEye;
    @NonNull private final MaterialButton mReset;
    /** The glyph the eye shows now: open or struck through. */
    private int mEyeIcon = R.drawable.ic_ms_visibility;
    @NonNull private final ScrollView mScroll;
    /** The chips row under the header: Where it applies, then What it applies to. */
    @NonNull private final LinearLayout mScopeSlot;
    @NonNull private final MaterialButton mWhereChip;
    @NonNull private final MaterialButton mWhatChip;
    @NonNull private final LinearLayout mRows;
    @NonNull private final Map<Row, RowViews> mRowViews = new EnumMap<>(Row.class);
    @Nullable private EditorCard mCard;
    private boolean mRestating;
    private int mDetent = DETENT_HALF;
    private int mPeekPx;
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

        mHeader = new LinearLayout(context);
        mHeader.setOrientation(LinearLayout.VERTICAL);
        mColumn.addView(mHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(HEADER_DP)));
        mGrab = new FrameLayout(context);
        mGrab.setContentDescription(context.getString(R.string.appearance_card_handle));
        mGrab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        androidx.core.view.ViewCompat.addAccessibilityAction(mGrab,
            context.getString(R.string.appearance_card_expand), (v, args) -> {
                if (!mShown || mDetent == DETENT_FULL)
                    return false;
                settle(mDetent == DETENT_PEEK ? DETENT_HALF : DETENT_FULL, true);
                return true;
            });
        androidx.core.view.ViewCompat.addAccessibilityAction(mGrab,
            context.getString(R.string.appearance_card_collapse), (v, args) -> {
                if (!mShown || mDetent == DETENT_PEEK)
                    return false;
                settle(DETENT_PEEK, true);
                return true;
            });
        mHeader.addView(mGrab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            dp(GRAB_DP)));
        FrameLayout headerRow = new FrameLayout(context);
        mHeader.addView(headerRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_ROW_DP)));
        View handle = new View(context);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setCornerRadius(dp(2));
        handleShape.setColor(EditorViews.color(context,
            com.google.android.material.R.attr.colorOutline));
        handle.setBackground(handleShape);
        mGrab.addView(handle, new FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER));

        mHeaderIcon = new ImageView(context);
        mHeaderIcon.setImageTintList(ColorStateList.valueOf(EditorViews.color(context,
            com.google.android.material.R.attr.colorOnSurface)));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24),
            Gravity.START | Gravity.CENTER_VERTICAL);
        iconParams.setMarginStart(dp(EDGE_DP));
        headerRow.addView(mHeaderIcon, iconParams);

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
        headerRow.addView(mTitle, titleParams);

        // From the end: reset where the ⋮ menu sat (it only held Reset and Copy to, and the
        // Match rows replaced Copy to), then the eye where the card has one. There is no ✕: the
        // card is never put down, and the handle, a drag or Back takes it to peek.
        mReset = headerButton(R.drawable.ic_ms_restart_alt, R.string.appearance_card_reset);
        mReset.setOnClickListener(v -> mListener.onReset());
        headerRow.addView(mReset, headerButtonParams(0));

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
        headerRow.addView(mEye, headerButtonParams(1));
        bindHeaderDrag();

        mScopeSlot = new LinearLayout(context);
        mScopeSlot.setOrientation(LinearLayout.HORIZONTAL);
        // Two halves: a chip never takes more than half the row, its twin there or not.
        mScopeSlot.setWeightSum(2f);
        mScopeSlot.setVisibility(View.GONE);
        float dp = context.getResources().getDisplayMetrics().density;
        mScopeSlot.setPadding(Math.round(CHIP_ROW_SIDE_DP * dp), Math.round(CHIP_ROW_TOP_DP * dp),
            Math.round(CHIP_ROW_SIDE_DP * dp), Math.round(CHIP_ROW_BOTTOM_DP * dp));
        mWhereChip = buildChip(context, true);
        mWhatChip = buildChip(context, false);
        mScopeSlot.addView(mWhereChip);
        mScopeSlot.addView(mWhatChip);
        mColumn.addView(mScopeSlot, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mScroll = new SheetScrollView(context);
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
     * The half and full detents' heights and the navigation inset the rows clear; the peek
     * detent is the header over the inset ({@link #peekHeightPx}). The sheet takes the full
     * height as its own (laid out only when this changes) and the others by translation.
     */
    void setGeometry(int halfPx, int fullPx, int navInsetPx) {
        int full = Math.max(fullPx, halfPx);
        int peek = peekHeightPx(dp(HEADER_DP), navInsetPx, halfPx);
        boolean changed = full != mFullPx || halfPx != mHalfPx || navInsetPx != mNavInsetPx
            || peek != mPeekPx;
        mPeekPx = peek;
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

    /**
     * The peek detent's height: the header over the navigation inset, never taller than the half
     * detent.
     */
    static int peekHeightPx(int headerPx, int navInsetPx, int halfPx) {
        return Math.max(0, Math.min(halfPx, headerPx + navInsetPx));
    }

    /** How tall the sheet stands at {@code detent}. */
    int heightAt(int detent) {
        if (detent == DETENT_FULL) return mFullPx;
        if (detent == DETENT_PEEK) return mPeekPx;
        return mHalfPx;
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
     * Builds {@code card}'s rows and raises the sheet to its half detent, or, with a card already
     * up, puts {@code card} in its place at the detent the sheet stands at. {@code resetLabel}
     * names the header's reset button for accessibility and its long-press ("Reset dock").
     */
    void show(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> rows,
              @NonNull String resetLabel, boolean animate) {
        show(card, rows, resetLabel, animate, DETENT_HALF);
    }

    /** {@link #show}, raising a sheet that is down to {@code raiseTo} instead of the half detent. */
    void show(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> rows,
              @NonNull String resetLabel, boolean animate, int raiseTo) {
        show(card, rows, resetLabel, animate, raiseTo, 0, null);
    }

    /**
     * A card's subcard in the card's place (the Terminal card's Pane borders): {@code card} is
     * the parent, whose surface and chips stay, the header names {@code titleRes} and carries a
     * back arrow in the glyph's place that runs {@code back}.
     */
    void showSubcard(@NonNull EditorCard card, @StringRes int titleRes,
                     @NonNull List<EditorRows.Spec> rows, @NonNull String resetLabel,
                     boolean animate, @NonNull Runnable back) {
        show(card, rows, resetLabel, animate, DETENT_HALF, titleRes, back);
    }

    /** The subcard's title shown in the header, or 0 while a card stands as itself. */
    @StringRes private int mSubcardTitle;

    private void show(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> rows,
                      @NonNull String resetLabel, boolean animate, int raiseTo,
                      @StringRes int subcardTitle, @Nullable Runnable back) {
        boolean sameCard = mShown && card == mCard && subcardTitle == mSubcardTitle;
        mSubcardTitle = subcardTitle;
        // Rule a: another card at a sheet that is up swaps in place, its labels and body fading.
        boolean swap = mShown && !sameCard && animate && !mParked && mSheet.isLaidOut()
            && mSheet.getVisibility() == View.VISIBLE;
        if (swap)
            fadeOutForSwap();
        // A restatement with another row list (a chip offered or withdrawn) rebuilds in place.
        boolean sameRows = sameCard && rows.equals(mBuilt);
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
        if (!sameRows) {
            int scrollY = mScroll.getScrollY();
            build(card, rows);
            mScroll.scrollTo(0, sameCard ? scrollY : 0);
        }
        restateEye();
        restateScope();
        int titleRes = subcardTitle != 0 ? subcardTitle : card.titleRes;
        EditorViews.setText(mTitle, mContext.getString(titleRes));
        mHeaderIcon.setImageResource(back != null ? R.drawable.ic_category_back_arrow
            : card.iconRes);
        restateHeaderBack(back);
        mSheet.setContentDescription(mContext.getString(titleRes));
        boolean wasShown = mShown;
        mShown = true;
        // A card swapped in stands where the last one stood.
        if (!wasShown)
            mDetent = raiseTo;
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
        if (swap)
            fadeInAfterSwap();
    }

    /**
     * The header glyph as a subcard's back arrow: tappable across a 48dp target round its 24dp
     * glyph, or the card's plain glyph again for null.
     */
    private void restateHeaderBack(@Nullable Runnable back) {
        int pad = back != null ? dp(12) : 0;
        if (mHeaderIcon.getPaddingLeft() != pad) {
            mHeaderIcon.setPadding(pad, pad, pad, pad);
            ViewGroup.LayoutParams params = mHeaderIcon.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                int size = dp(24) + 2 * pad;
                params.width = size;
                params.height = size;
                ((FrameLayout.LayoutParams) params).setMarginStart(dp(EDGE_DP) - pad);
                mHeaderIcon.setLayoutParams(params);
            }
        }
        if (back != null) {
            mHeaderIcon.setOnClickListener(v -> back.run());
            mHeaderIcon.setContentDescription(
                mContext.getString(R.string.appearance_pane_borders_back));
            mHeaderIcon.setBackgroundResource(borderlessRipple());
        } else {
            mHeaderIcon.setOnClickListener(null);
            mHeaderIcon.setClickable(false);
            mHeaderIcon.setContentDescription(null);
            mHeaderIcon.setBackground(null);
        }
    }

    /** The theme's borderless ripple. */
    private int borderlessRipple() {
        android.util.TypedValue value = new android.util.TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            value, true);
        return value.resourceId;
    }

    /** The rise the new body fades in from on a card swap. */
    static final int SWAP_RISE_DP = 8;

    /**
     * Rule a's out half: the header's title, glyph and chips and the body as they stand now are
     * lifted into the overlays (the body's own row views, the labels as their last frame) and fade
     * on the fast effects spring while the new card is built underneath.
     */
    private void fadeOutForSwap() {
        boolean reduced = ReducedMotion.isEnabled(mContext);
        long labelsOut = EditorMotion.Use.LABEL_OUT.durationMs(reduced);
        ghost(mTitle, labelsOut);
        ghost(mHeaderIcon, labelsOut);
        ghost(mScopeSlot, labelsOut);
        int count = mRows.getChildCount();
        if (count == 0)
            return;
        List<View> old = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            old.add(mRows.getChildAt(i));
        android.view.ViewGroupOverlay overlay = mScroll.getOverlay();
        for (View row : old) {
            mRows.removeView(row);
            row.offsetLeftAndRight(mRows.getLeft());
            row.offsetTopAndBottom(mRows.getTop());
            overlay.add(row);
        }
        android.animation.ValueAnimator out = android.animation.ValueAnimator.ofFloat(1f, 0f);
        out.setDuration(EditorMotion.Use.BODY_OUT.durationMs(reduced));
        out.setInterpolator(EditorMotion.Use.BODY_OUT.curve(reduced));
        out.addUpdateListener(a -> {
            float alpha = (float) a.getAnimatedValue();
            for (View row : old) row.setAlpha(alpha);
        });
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                for (View row : old) overlay.remove(row);
            }
        });
        out.start();
    }

    /** Rule a's in half: the relabelled header and the new body fade in, the body rising 8dp. */
    private void fadeInAfterSwap() {
        boolean reduced = ReducedMotion.isEnabled(mContext);
        long labelsAfter = EditorMotion.Use.LABEL_OUT.durationMs(reduced);
        for (View label : new View[] {mTitle, mHeaderIcon, mScopeSlot}) {
            label.animate().cancel();
            label.setAlpha(0f);
            label.animate().alpha(1f).setStartDelay(labelsAfter)
                .setDuration(EditorMotion.Use.LABEL_IN.durationMs(reduced))
                .setInterpolator(EditorMotion.Use.LABEL_IN.curve(reduced)).start();
        }
        long bodyAfter = EditorMotion.Use.BODY_OUT.durationMs(reduced);
        mRows.animate().cancel();
        mRows.setAlpha(0f);
        mRows.setTranslationY(reduced ? 0f : dp(SWAP_RISE_DP));
        // The fade and the rise run on their own springs: the fade as an animator of its own.
        android.animation.ObjectAnimator in = android.animation.ObjectAnimator.ofFloat(mRows,
            View.ALPHA, 0f, 1f);
        in.setStartDelay(bodyAfter);
        in.setDuration(EditorMotion.Token.EFFECTS_DEFAULT.settleMs());
        in.setInterpolator(EditorMotion.Use.LABEL_IN.curve(reduced));
        in.start();
        if (!reduced)
            mRows.animate().translationY(0f).setStartDelay(bodyAfter)
                .setDuration(EditorMotion.Use.BODY_SETTLE.durationMs(false))
                .setInterpolator(EditorMotion.Use.BODY_SETTLE.curve(false)).start();
    }

    /** {@code view}'s last frame drawn over it, fading out in {@code durationMs}. */
    private void ghost(@NonNull View view, long durationMs) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0 || view.getVisibility() != View.VISIBLE
            || !(view.getParent() instanceof ViewGroup))
            return;
        android.graphics.Bitmap frame = android.graphics.Bitmap.createBitmap(view.getWidth(),
            view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(frame));
        android.graphics.drawable.BitmapDrawable last =
            new android.graphics.drawable.BitmapDrawable(mContext.getResources(), frame);
        last.setBounds(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        android.view.ViewGroupOverlay overlay = ((ViewGroup) view.getParent()).getOverlay();
        overlay.add(last);
        android.animation.ValueAnimator out = android.animation.ValueAnimator.ofInt(255, 0);
        out.setDuration(durationMs);
        out.setInterpolator(EditorMotion.Use.LABEL_OUT.curve(false));
        out.addUpdateListener(a -> last.setAlpha((int) a.getAnimatedValue()));
        out.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                overlay.remove(last);
                frame.recycle();
            }
        });
        out.start();
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
        mSheet.animate().translationY(to).setDuration(EditorMotion.Use.SHEET_FALL.settleMs())
            .setInterpolator(EditorMotion.Use.SHEET_FALL.curve(false))
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
        if (mFullPx <= 0 || mParked) {
            // No detents yet: the sheet rises to this one once it has them.
            mDetent = detent;
            return;
        }
        settle(detent, animate);
    }

    /**
     * Scrolls the rows so {@code row} stands at the top of the card, once they are laid out: the
     * Elements list opening the Dock card at its Options.
     */
    void scrollTo(@NonNull Row row) {
        RowViews views = mRowViews.get(row);
        if (views == null)
            return;
        View target = views.strip != null ? views.strip.root : views.root;
        mScroll.post(() -> {
            int y = 0;
            for (View at = target; at != null && at != mRows; ) {
                y += at.getTop();
                at = at.getParent() instanceof View ? (View) at.getParent() : null;
            }
            if (ReducedMotion.isEnabled(mContext))
                mScroll.scrollTo(0, y);
            else
                mScroll.smoothScrollTo(0, y);
        });
    }

    private void settle(int detent, boolean animate) {
        settle(detent, animate, 0f);
    }

    /**
     * {@link #settle(int, boolean)} for a release moving at {@code velocityPxPerS} (down is
     * positive): the detent spring starts at that speed (rule e).
     */
    private void settle(int detent, boolean animate, float velocityPxPerS) {
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
        mSheet.animate().translationY(target).setDuration(MOVE_MS)
            .setInterpolator(EditorMotion.Use.SHEET_DETENT.curve(false,
                releaseShare(mSheet.getTranslationY(), target, velocityPxPerS)))
            .setUpdateListener(a -> mListener.onSheetMoved(detent, sheetTopPx()))
            .withEndAction(() -> {
                mSheet.animate().setUpdateListener(null);
                mListener.onSheetMoved(detent, sheetTopPx());
                mListener.onSheetSettled(detent, sheetTopPx());
            }).start();
    }

    /**
     * A release velocity as the share of the distance left it covers per second, toward the
     * target positive; 0 when there is no distance. Capped so a hard fling cannot overshoot far.
     */
    static double releaseShare(float fromPx, float toPx, float velocityPxPerS) {
        float distance = toPx - fromPx;
        if (Math.abs(distance) < 1f)
            return 0.0;
        return Math.max(-RELEASE_SHARE_MAX, Math.min(RELEASE_SHARE_MAX, velocityPxPerS / distance));
    }

    /** The largest release share a detent spring starts with. */
    static final double RELEASE_SHARE_MAX = 30.0;

    private float translationFor(int detent) {
        return Math.max(0, mFullPx - heightAt(detent));
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
     * The header, its grab zone included, drags the sheet between its three detents, never below
     * peek: a tap goes to the next one up and from the full detent back to peek ({@link
     * #tapDetent}), a fling goes the way it was thrown, and a slow release goes to the nearest
     * ({@link #releaseDetent}).
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
                        mSheet.setTranslationY(Math.max(0f,
                            Math.min(translationFor(DETENT_PEEK), down[1] + dy)));
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
                        settle(tapDetent(mDetent), true);
                        return true;
                    }
                    settle(releaseDetent(mSheet.getTranslationY(), translationFor(DETENT_HALF),
                        translationFor(DETENT_PEEK), velocity, flingPxPerS), true, velocity);
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
     * Where a released drag goes: {@link #DETENT_FULL}, {@link #DETENT_HALF} or {@link
     * #DETENT_PEEK}; never off screen. {@code atPx} is the sheet's translation (0 at the full
     * detent, {@code halfTranslationPx} at the half one, {@code peekTranslationPx} at peek);
     * {@code velocityPxPerS} is positive downwards. A fling goes to the next detent the way it was
     * thrown; a slow release to the nearest.
     */
    static int releaseDetent(float atPx, float halfTranslationPx, float peekTranslationPx,
                             float velocityPxPerS, float flingPxPerS) {
        if (velocityPxPerS <= -flingPxPerS)
            return atPx > halfTranslationPx ? DETENT_HALF : DETENT_FULL;
        if (velocityPxPerS >= flingPxPerS)
            return atPx < halfTranslationPx ? DETENT_HALF : DETENT_PEEK;
        float toFull = Math.abs(atPx);
        float toHalf = Math.abs(atPx - halfTranslationPx);
        float toPeek = Math.abs(atPx - peekTranslationPx);
        if (toFull < toHalf && toFull <= toPeek)
            return DETENT_FULL;
        return toHalf <= toPeek ? DETENT_HALF : DETENT_PEEK;
    }

    /** Where a tap on the header goes: up a detent, and from the full detent back to peek. */
    static int tapDetent(int detent) {
        if (detent == DETENT_PEEK) return DETENT_HALF;
        if (detent == DETENT_HALF) return DETENT_FULL;
        return DETENT_PEEK;
    }

    // ---------------------------------------------------------------------------- content drag

    /**
     * The card's content, which hands a clearly vertical drag to the sheet the way a standard
     * bottom sheet does: up while the sheet is below its full detent, and down while the content
     * stands at its top and the sheet is above peek. The claimed drag moves the sheet by the
     * header's maths and lands by {@link #releaseDetent}, so a tab drag and a content drag stop
     * at the same detents. Any other drag, and every tap, is the ScrollView's own; a horizontal
     * drag (sliders, strips) is never claimed.
     */
    private final class SheetScrollView extends ScrollView {
        private final int mSlop;
        private final float mFlingPxPerS;
        private float mDownX;
        private float mDownY;
        private float mStartTranslation;
        private boolean mClaimed;
        @Nullable private VelocityTracker mTracker;

        SheetScrollView(@NonNull Context context) {
            super(context);
            ViewConfiguration config = ViewConfiguration.get(context);
            mSlop = config.getScaledTouchSlop();
            mFlingPxPerS = config.getScaledMinimumFlingVelocity() * 4f;
        }

        private boolean wants(float dy) {
            if (!mShown || mFullPx <= 0)
                return false;
            if (dy < 0)
                return mDetent != DETENT_FULL;
            return mDetent != DETENT_PEEK && !canScrollVertically(-1);
        }

        private void end() {
            mClaimed = false;
            if (mTracker != null) {
                mTracker.recycle();
                mTracker = null;
            }
        }

        private void follow(@NonNull MotionEvent event) {
            mSheet.setTranslationY(Math.max(0f, Math.min(translationFor(DETENT_PEEK),
                mStartTranslation + event.getRawY() - mDownY)));
            mListener.onSheetMoved(mDetent, sheetTopPx());
        }

        /**
         * Takes the finger for the sheet when it moved far enough, mostly vertically, and the
         * sheet can use it. Run from the intercept and, when no child took the touch (a label,
         * a gap: the scroll view is then the target and is never asked to intercept), from
         * {@link #onTouchEvent}.
         */
        private boolean claim(@NonNull MotionEvent event) {
            if (mTracker == null)
                return false;
            trackRaw(mTracker, event);
            float dy = event.getRawY() - mDownY;
            float dx = event.getRawX() - mDownX;
            if (Math.abs(dy) > mSlop && Math.abs(dy) > Math.abs(dx) && wants(dy)) {
                mClaimed = true;
                mDragging = true;
                mSheet.animate().cancel();
                // The slop is not spent: the sheet takes the finger from here.
                mStartTranslation = mSheet.getTranslationY();
                mDownY = event.getRawY();
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return false;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    end();
                    mDownX = event.getRawX();
                    mDownY = event.getRawY();
                    mTracker = VelocityTracker.obtain();
                    trackRaw(mTracker, event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mClaimed || claim(event))
                        return true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!mClaimed)
                        end();
                    break;
                default:
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!mClaimed) {
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && claim(event))
                    return true;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    end();
                return super.onTouchEvent(event);
            }
            if (mTracker != null)
                trackRaw(mTracker, event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    follow(event);
                    break;
                case MotionEvent.ACTION_UP: {
                    mDragging = false;
                    float velocity = 0f;
                    if (mTracker != null) {
                        mTracker.computeCurrentVelocity(1000);
                        velocity = mTracker.getYVelocity();
                    }
                    end();
                    settle(releaseDetent(mSheet.getTranslationY(), translationFor(DETENT_HALF),
                        translationFor(DETENT_PEEK), velocity, mFlingPxPerS), true, velocity);
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    end();
                    settle(mDetent, true);
                    break;
                default:
                    break;
            }
            return true;
        }
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
        /** A slider row's own row: its name, value, source dot. */
        @Nullable SliderRow sliderRow;
        /** The run a slider row stands in, put away when all its rows are. */
        @Nullable Strip strip;
        @Nullable MaterialSwitch toggle;
        @Nullable MaterialButtonToggleGroup segments;
        @Nullable List<MaterialButton> buttons;
        @Nullable List<TextView> stopLabels;
        @Nullable FrameLayout expander;
        boolean tracking;
        /** The model drew the row itself ({@link Model#rowView}); it restates it too. */
        boolean own;

        RowViews(@NonNull Row row, @NonNull View root) {
            this.row = row;
            this.root = root;
        }
    }

    /** One run of slider rows: its root (the heading, then the rows) and the rows it holds. */
    private static final class Strip {
        @NonNull final LinearLayout root;
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
    /** The rows the sheet was last built with, so a restatement can tell a changed list. */
    @NonNull private List<EditorRows.Spec> mBuilt = java.util.Collections.emptyList();

    private void build(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> specs) {
        mBuilt = new java.util.ArrayList<>(specs);
        mRows.removeAllViews();
        mRowViews.clear();
        int tier = 0;
        Strip strip = null;
        for (EditorRows.Spec spec : specs) {
            RowViews own = ownRow(spec.row);
            if (own == null && spec.row.kind == Kind.SLIDER) {
                if (strip == null) {
                    if (tier != 0 && spec.tier != tier)
                        mRows.addView(divider());
                    tier = spec.tier;
                    strip = newStrip(spec.row);
                    if (spec.tier == 1)
                        strip.root.setTag(TIER_ONE_TAG);
                    mRows.addView(strip.root);
                }
                mRowViews.put(spec.row, sliderRow(spec.row, strip));
                continue;
            }
            strip = null;
            if (tier != 0 && spec.tier != tier)
                mRows.addView(divider());
            tier = spec.tier;
            RowViews row = own != null ? own : buildRow(spec.row);
            mRowViews.put(spec.row, row);
            if (spec.tier == 1)
                row.root.setTag(TIER_ONE_TAG);
            mRows.addView(row.root);
        }
        restate();
    }


    /** A strip's root: its heading, if the model gives one; the lines follow at its run's end. */
    @NonNull
    private Strip newStrip(@NonNull Row first) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(SLIDER_ROW_MIN_DP));
        // The rows keep their own 16dp gutter; the heading takes the same.
        root.setPadding(0, dp(8), 0, dp(8));
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
            params.setMarginStart(dp(EDGE_DP));
            params.setMarginEnd(dp(EDGE_DP));
            root.addView(title, params);
        }
        return new Strip(root);
    }

    /**
     * One slider row ({@link SliderRow}): the row's glyph, name, source dot and value over its
     * track, in the model's own units and range, reported to the model as any slider is; a long
     * press on the name opens the row's menu. It stands under the run's heading.
     */
    @NonNull
    private RowViews sliderRow(@NonNull Row row, @NonNull Strip strip) {
        RowViews[] built = new RowViews[1];
        SliderRow slider = SliderRow.build(mContext, java.util.Collections.singletonList(
                new SliderRow.Track(row.iconRes, mContext.getString(row.labelRes),
                    mModel.from(row), mModel.to(row), mModel.step(row),
                    value -> mModel.readout(row, value))),
            new int[] {mModel.value(row)},
            new SliderRow.Listener() {
                @Override public void onValue(int index, int value, boolean dragging) {
                    if (mRestating)
                        return;
                    built[0].tracking = dragging;
                    mModel.onValue(row, value, dragging);
                }

                @Override public void onReleased(int index) {
                    built[0].tracking = false;
                    mModel.onReleased(row);
                }
            }, -1, "", false, null, (index, anchor) -> openRowMenu(anchor, row));
        SliderRow.Item item = slider.items.get(0);
        RowViews views = new RowViews(row, slider.root);
        built[0] = views;
        strip.root.addView(slider.root, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        strip.members.add(views);
        views.legend = item.slider;
        views.label = item.name;
        views.value = item.value;
        views.sliderRow = slider;
        views.strip = strip;
        return views;
    }

    /** The row the card draws itself ({@link Model#rowView}), or null for the sheet's own. */
    @Nullable
    private RowViews ownRow(@NonNull Row row) {
        // The row kinds take the card's whole width and keep their own 16dp gutter.
        View own = mModel.rowView(row, availableWidthPx() + dp(ICON_COLUMN_DP) + dp(EDGE_DP));
        if (own == null)
            return null;
        if (own.getParent() instanceof ViewGroup)
            ((ViewGroup) own.getParent()).removeView(own);
        RowViews views = new RowViews(row, own);
        views.own = true;
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
        // The label's long-press (the row menu) makes it long-clickable, which takes its taps
        // from the row: a tap on the name opens the row as a tap beside it does.
        if (views.label != null)
            views.label.setOnClickListener(v -> root.performClick());
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
        restateScope();
    }

    /** The chips row: 4 dp over, 12 under, 16 at the sides; chips 40 high, 8 apart. */
    static final int CHIP_ROW_TOP_DP = 4;
    static final int CHIP_ROW_BOTTOM_DP = 12;
    static final int CHIP_ROW_SIDE_DP = 16;
    static final int CHIP_HEIGHT_DP = 40;
    static final int CHIP_GAP_DP = 8;

    /** A chip: a capsule that names its option on one line and opens its menu on a tap. */
    @NonNull
    private MaterialButton buildChip(@NonNull Context context, boolean where) {
        MaterialButton chip = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        float dp = context.getResources().getDisplayMetrics().density;
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setAllCaps(false);
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setInsetTop(0);
        chip.setInsetBottom(0);
        chip.setCornerRadius(Math.round(CHIP_HEIGHT_DP * dp / 2f));
        chip.setPadding(Math.round(8 * dp), 0, Math.round(12 * dp), 0);
        chip.setIconResource(R.drawable.ic_editor_expand_more);
        chip.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        chip.setIconSize(Math.round(18 * dp));
        chip.setIconPadding(Math.round(8 * dp));
        chip.setOnClickListener(v -> openChipMenu(chip, where));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            Math.round(CHIP_HEIGHT_DP * dp), 1f);
        if (!where) params.setMarginStart(Math.round(CHIP_GAP_DP * dp));
        chip.setLayoutParams(params);
        return chip;
    }

    /** A chip's menu: its question as the caption, its options as radio items. */
    private void openChipMenu(@NonNull MaterialButton anchor, boolean where) {
        Chip chip = where ? mModel.whereChip() : mModel.whatChip();
        if (chip == null || chip.labels.length < 2)
            return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(mContext, anchor);
        menu.getMenu().add(0, -1, 0, chip.question).setEnabled(false);
        for (int i = 0; i < chip.labels.length; i++)
            menu.getMenu().add(1, i, i + 1, chip.labels[i]).setChecked(i == chip.selected);
        menu.getMenu().setGroupCheckable(1, true, true);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() < 0)
                return false;
            if (where)
                mModel.pickWhere(item.getItemId());
            else
                mModel.pickWhat(item.getItemId());
            restate();
            return true;
        });
        menu.show();
    }

    /** One chip read again: its words, its narrowed fill, its chevron where it has a choice. */
    private void restateChip(@NonNull MaterialButton view, @Nullable Chip chip) {
        if (chip == null) {
            EditorViews.setVisible(view, false);
            return;
        }
        CharSequence label = chip.label();
        if (!TextUtils.equals(view.getText(), label))
            view.setText(label);
        view.setContentDescription(chip.question + ": " + label);
        boolean choice = chip.labels.length > 1;
        view.setEnabled(choice);
        view.setIconResource(choice ? R.drawable.ic_editor_expand_more : 0);
        int fill = com.google.android.material.color.MaterialColors.getColor(view,
            chip.narrowed() ? com.google.android.material.R.attr.colorPrimaryContainer
                : android.R.attr.colorBackground, 0);
        int ink = com.google.android.material.color.MaterialColors.getColor(view,
            chip.narrowed() ? com.google.android.material.R.attr.colorOnPrimaryContainer
                : com.google.android.material.R.attr.colorOnSurface, 0);
        view.setBackgroundTintList(ColorStateList.valueOf(chip.narrowed() ? fill : 0));
        view.setTextColor(ink);
        view.setIconTint(ColorStateList.valueOf(ink));
        view.setStrokeWidth(chip.narrowed() ? 0
            : Math.round(mContext.getResources().getDisplayMetrics().density));
        EditorViews.setVisible(view, true);
    }

    /** The chips read again; the row hides when the card has neither. */
    private void restateScope() {
        Chip where = mModel.whereChip();
        Chip what = mModel.whatChip();
        restateChip(mWhereChip, where);
        restateChip(mWhatChip, what);
        EditorViews.setVisible(mScopeSlot, where != null || what != null);
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

    /** A run of slider rows stands while any of its rows does. */
    private static void restateStrip(@NonNull Strip strip) {
        boolean any = false;
        for (RowViews member : strip.members)
            any |= member.root.getVisibility() == View.VISIBLE;
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
            if (views.own) {
                mModel.restateRowView(row, views.root);
                return;
            }
            if (views.strip != null)
                restateStrip(views.strip);
            if (views.root.isEnabled() != enabled)
                views.root.setEnabled(enabled);
            views.root.setAlpha(enabled ? 1f : 0.5f);
            if (views.dot != null)
                markDot(views.dot, row);
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
            if (views.legend != null && !views.tracking && !views.legend.isDragging()) {
                LegendSlider legend = views.legend;
                int from = mModel.from(row);
                int to = Math.max(from + 1, mModel.to(row));
                int step = Math.max(1, mModel.step(row));
                if (legend.getValueFrom() != from) legend.setValueFrom(from);
                if (legend.getValueTo() != to) legend.setValueTo(to);
                if (legend.getStepSize() != step) legend.setStepSize(step);
                if (legend.getValue() != value) legend.setValue(value);
                if (views.value != null)
                    EditorViews.setText(views.value,
                        mModel.readout(row, Math.round(legend.getValue())));
                if (views.sliderRow != null)
                    views.sliderRow.setSource(0, mModel.source(row).ink(mContext),
                        spokenSource(row));
                else
                    legend.setMark(mModel.source(row).ink(mContext), spokenSource(row));
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
                for (int i = 0; i < views.buttons.size(); i++) {
                    boolean can = enabled && mModel.choiceEnabled(row, i);
                    if (views.buttons.get(i).isEnabled() != can)
                        views.buttons.get(i).setEnabled(can);
                }
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
        bindRowMenu(label, row);
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
        bindRowMenu(label, row);
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

    /** The dot after a row's name, coloured by where its value comes from, and its words. */
    private void markDot(@NonNull View dot, @NonNull Row row) {
        RowSource source = mModel.source(row);
        EditorViews.setVisible(dot, source.marked());
        if (!source.marked())
            return;
        if (dot.getBackground() instanceof GradientDrawable)
            ((GradientDrawable) dot.getBackground()).setColor(source.ink(mContext));
        CharSequence spoken = spokenSource(row);
        if (!TextUtils.equals(dot.getContentDescription(), spoken))
            dot.setContentDescription(spoken);
    }

    /** "<Row>, from <where>", or null where the row's value is shared. */
    @Nullable
    private CharSequence spokenSource(@NonNull Row row) {
        CharSequence from = mModel.source(row).marked() ? mModel.sourceName(row) : null;
        return from == null ? null
            : mContext.getString(R.string.editor_source_from, mContext.getString(row.labelRes), from);
    }

    /**
     * A long press on a row's name (the system's long-press time, cancelled past the touch slop)
     * opens its menu: where the value comes from, and Reset this row.
     */
    private void bindRowMenu(@NonNull TextView label, @NonNull Row row) {
        label.setOnLongClickListener(v -> {
            openRowMenu(v, row);
            return true;
        });
    }

    void openRowMenu(@NonNull View anchor, @NonNull Row row) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(mContext, anchor);
        CharSequence name = mContext.getString(row.labelRes);
        CharSequence from = mModel.source(row).marked() ? mModel.sourceName(row) : null;
        menu.getMenu().add(0, -1, 0, from == null ? name
            : mContext.getString(R.string.editor_source_caption, name, from)).setEnabled(false);
        menu.getMenu().add(0, 1, 1, R.string.editor_row_reset).setEnabled(mModel.resettable(row));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != 1)
                return false;
            ResetMotion motion = beginReset(java.util.Collections.singletonList(row));
            mModel.resetRow(row);
            restate();
            endReset(motion, 0L);
            return true;
        });
        menu.show();
    }

    // ------------------------------------------------------------------ rule g, Reset motion

    /** The time between rows on a whole-card Reset, in touch order. */
    static final long RESET_STAGGER_MS = 30L;

    /** What the reset rows showed before the reset: their slider values, in touch order. */
    static final class ResetMotion {
        @NonNull final List<Row> rows = new ArrayList<>();
        @NonNull final List<Float> before = new ArrayList<>();
    }

    /** Every row the card shows, in touch order: what a whole-card Reset moves. */
    @NonNull
    List<Row> shownRows() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < mRows.getChildCount(); i++) {
            for (RowViews views : mRowViews.values()) {
                if (views.root == mRows.getChildAt(i)) {
                    rows.add(views.row);
                    break;
                }
            }
        }
        return rows;
    }

    /**
     * Before a reset: notes each row's value and fades its dot out as it stands (DOT_OUT), so
     * {@link #endReset} can spring the values from there and bring the dots back.
     */
    @NonNull
    ResetMotion beginReset(@NonNull List<Row> rows) {
        ResetMotion motion = new ResetMotion();
        boolean reduced = ReducedMotion.isEnabled(mContext);
        for (Row row : rows) {
            RowViews views = mRowViews.get(row);
            if (views == null)
                continue;
            motion.rows.add(row);
            LegendSlider legend = legendOf(views);
            motion.before.add(views.slider != null ? views.slider.getValue()
                : legend != null ? legend.getValue() : Float.NaN);
            if (views.dot != null)
                ghost(views.dot, EditorMotion.Use.DOT_OUT.durationMs(reduced));
        }
        return motion;
    }

    /**
     * After a reset and its restate: each row's value springs from where it stood to its new one
     * (VALUE_SPRING; a jump under reduced motion) and its dot fades in (DOT_IN) in its new colour
     * or stays off, {@code staggerMs} apart in touch order.
     */
    void endReset(@NonNull ResetMotion motion, long staggerMs) {
        boolean reduced = ReducedMotion.isEnabled(mContext);
        long dotAfter = EditorMotion.Use.DOT_OUT.durationMs(reduced);
        for (int i = 0; i < motion.rows.size(); i++) {
            RowViews views = mRowViews.get(motion.rows.get(i));
            if (views == null)
                continue;
            long delay = i * staggerMs;
            if (views.dot != null && views.dot.getVisibility() == View.VISIBLE) {
                views.dot.animate().cancel();
                views.dot.setAlpha(0f);
                views.dot.animate().alpha(1f).setStartDelay(delay + dotAfter)
                    .setDuration(EditorMotion.Use.DOT_IN.durationMs(reduced))
                    .setInterpolator(EditorMotion.Use.DOT_IN.curve(reduced)).start();
            }
            float from = motion.before.get(i);
            if (Float.isNaN(from) || reduced)
                continue;
            springValue(views, from, delay);
        }
    }

    /** A row's slider springing from {@code from} to the value it now shows, from code only. */
    private void springValue(@NonNull RowViews views, float from, long delayMs) {
        Slider slider = views.slider;
        LegendSlider legend = legendOf(views);
        float to = slider != null ? slider.getValue() : legend != null ? legend.getValue() : from;
        if (to == from)
            return;
        android.animation.ValueAnimator spring = android.animation.ValueAnimator.ofFloat(0f, 1f);
        spring.setStartDelay(delayMs);
        spring.setDuration(EditorMotion.Use.VALUE_SPRING.durationMs(false));
        // The overshoot shows on the fill; the value stays on the slider's range and steps.
        spring.setInterpolator(EditorMotion.Use.VALUE_SPRING.curve(false));
        spring.addUpdateListener(a -> {
            float now = from + (to - from) * (float) a.getAnimatedValue();
            mRestating = true;
            try {
                if (slider != null)
                    slider.setValue(onSliderSteps(slider, now));
                else if (legend != null)
                    legend.setValue(now);
            } finally {
                mRestating = false;
            }
        });
        spring.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                mRestating = true;
                try {
                    if (slider != null) slider.setValue(to);
                    else if (legend != null) legend.setValue(to);
                } finally {
                    mRestating = false;
                }
            }
        });
        if (slider != null) slider.setValue(onSliderSteps(slider, from));
        spring.start();
    }

    /**
     * A row's slider track: the sheet's own, or in a row the card drew ({@link CardBody}) the
     * track that carries the row as its tag.
     */
    @Nullable
    private LegendSlider legendOf(@NonNull RowViews views) {
        if (views.legend != null)
            return views.legend;
        if (!views.own)
            return null;
        View tagged = mRows.findViewWithTag(views.row);
        return tagged instanceof LegendSlider ? (LegendSlider) tagged : null;
    }

    /** {@code value} clamped to {@code slider}'s range and put on its steps, as it accepts. */
    static float onSliderSteps(@NonNull Slider slider, float value) {
        float lo = slider.getValueFrom();
        float hi = slider.getValueTo();
        float clamped = Math.max(lo, Math.min(hi, value));
        float step = slider.getStepSize();
        if (step > 0f)
            clamped = Math.max(lo, Math.min(hi, lo + Math.round((clamped - lo) / step) * step));
        return clamped;
    }

    /** The small dot after a label: where the row's value comes from ({@link #markDot}). */
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

    /** The room the header's buttons take at its end: reset, and the eye with it. */
    private int headerButtonsDp(boolean withEye) {
        return dp(HEADER_END_DP + HEADER_BUTTON_DP * (withEye ? 2 : 1) + 8);
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
