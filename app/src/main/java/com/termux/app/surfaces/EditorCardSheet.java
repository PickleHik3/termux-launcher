package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
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
import com.termux.app.terminal.Motion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One card of the Appearance editor (appearance-and-settings-rework §3, §7, §8; wireframes 02–04):
 * a bottom sheet with a 64dp header (the object's icon and name, ⋮ and ✕) over its rows, tier by
 * tier, the third folded under More. Views only: every value is read from {@link Model} and every
 * touch reported to it, so the same sheet is every card.
 *
 * <p><b>Detents.</b> The sheet is laid out once at the full detent's height and stands at the
 * half detent by translation, so neither a detent change nor a drag of the header lays anything
 * out per frame. At the half detent the rows' bottom padding grows by the part of the sheet
 * below the screen, so the last row still scrolls clear of the navigation inset by 16dp.</p>
 *
 * <p><b>Rows.</b> Content-driven heights (a slider row is at least 72dp, a switch row 56dp),
 * labels on one line, ellipsized. A row of segments stands beside its label when it has two short
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
    }

    /** What the header and the sheet itself report. */
    interface Listener {
        void onClose();
        void onReset();
        void onCopyToOtherHalf();
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
    private static final int SWITCH_ROW_MIN_DP = 56;
    private static final int SEGMENT_GAP_DP = 2;
    private static final int SEGMENT_HEIGHT_DP = 40;
    private static final int MENU_WIDTH_DP = 200;
    private static final long MOVE_MS = 240L;

    @NonNull private final Context mContext;
    @NonNull private final Model mModel;
    @NonNull private final Listener mListener;
    @NonNull private final FrameLayout mSheet;
    @NonNull private final LinearLayout mColumn;
    @NonNull private final FrameLayout mHeader;
    @NonNull private final ImageView mHeaderIcon;
    @NonNull private final TextView mTitle;
    @NonNull private final View mMenuButton;
    @NonNull private final ScrollView mScroll;
    @NonNull private final LinearLayout mRows;
    @NonNull private final Map<Row, RowViews> mRowViews = new EnumMap<>(Row.class);
    @Nullable private LinearLayout mMore;
    @Nullable private View mMoreRow;
    @Nullable private ListPopupWindow mMenu;
    @Nullable private EditorCard mCard;
    @Nullable private String mCopyLabel;
    @Nullable private String mResetLabel;
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
        titleParams.setMarginEnd(dp(96 + 8));
        mHeader.addView(mTitle, titleParams);

        MaterialButton close = headerButton(R.drawable.ic_symbol_close,
            R.string.appearance_card_close);
        close.setOnClickListener(v -> mListener.onClose());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(48), dp(48),
            Gravity.END | Gravity.CENTER_VERTICAL);
        closeParams.setMarginEnd(dp(8));
        mHeader.addView(close, closeParams);

        MaterialButton menu = headerButton(R.drawable.ic_editor_more_vert,
            R.string.appearance_card_menu);
        menu.setOnClickListener(this::showMenu);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(48), dp(48),
            Gravity.END | Gravity.CENTER_VERTICAL);
        menuParams.setMarginEnd(dp(8 + 48));
        mHeader.addView(menu, menuParams);
        mMenuButton = menu;
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
        dismissMenu();
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
        if (mShown)
            settle(mDetent, false);
    }

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
     * Builds {@code card}'s rows and raises the sheet to its half detent. {@code copyLabel} names
     * the menu's copy item ("Copy to Home"), or null for a card held on one half only.
     */
    void show(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> rows,
              @Nullable String copyLabel, @NonNull String resetLabel, boolean animate) {
        dismissMenu();
        boolean sameCard = mShown && card == mCard;
        mCard = card;
        mCopyLabel = copyLabel;
        mResetLabel = resetLabel;
        if (!sameCard) {
            build(card, rows);
            mScroll.scrollTo(0, 0);
        }
        EditorViews.setText(mTitle, mContext.getString(card.titleRes));
        mHeaderIcon.setImageResource(card.iconRes);
        mSheet.setContentDescription(mContext.getString(card.titleRes));
        boolean wasShown = mShown;
        mShown = true;
        if (mSheet.getVisibility() != View.VISIBLE) {
            mSheet.setTranslationY(mFullPx);
            mSheet.setVisibility(View.VISIBLE);
        }
        if (!wasShown || !sameCard)
            mDetent = DETENT_HALF;
        settle(mDetent, animate);
    }

    /** Takes the sheet down; {@code done} runs once it is off screen. */
    void hide(boolean animate, @Nullable Runnable done) {
        dismissMenu();
        if (!mShown) {
            if (done != null) done.run();
            return;
        }
        mShown = false;
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

    @SuppressLint("ClickableViewAccessibility")
    private void bindHeaderDrag() {
        final int slop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final float[] down = new float[2];
        final boolean[] dragging = {false};
        mHeader.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawY();
                    down[1] = mSheet.getTranslationY();
                    dragging[0] = false;
                    mSheet.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = event.getRawY() - down[0];
                    if (!dragging[0] && Math.abs(dy) > slop) {
                        dragging[0] = true;
                        mDragging = true;
                    }
                    if (dragging[0]) {
                        mSheet.setTranslationY(Math.max(0f, down[1] + dy));
                        mListener.onSheetMoved(mDetent, sheetTopPx());
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    mDragging = false;
                    if (!dragging[0]) {
                        // A tap on the header or its handle flips between the two detents.
                        settle(mDetent == DETENT_FULL ? DETENT_HALF : DETENT_FULL, true);
                        return true;
                    }
                    float at = mSheet.getTranslationY();
                    float half = translationFor(DETENT_HALF);
                    if (at > half + mHalfPx * 0.4f) {
                        mListener.onClose();
                        return true;
                    }
                    settle(Math.abs(at) < Math.abs(at - half) ? DETENT_FULL : DETENT_HALF, true);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    settle(mDetent, true);
                    return true;
                default:
                    return true;
            }
        });
    }

    // ------------------------------------------------------------------------------------ menu

    private void showMenu(@NonNull View anchor) {
        dismissMenu();
        List<String> items = new ArrayList<>(2);
        if (mResetLabel != null) items.add(mResetLabel);
        if (mCopyLabel != null) items.add(mCopyLabel);
        if (items.isEmpty())
            return;
        ListPopupWindow menu = new ListPopupWindow(mContext);
        menu.setAnchorView(anchor);
        menu.setModal(true);
        menu.setWidth(dp(MENU_WIDTH_DP));
        menu.setDropDownGravity(Gravity.END);
        // Upward, over the frame: a menu dropped over the rows would cover a slider's value.
        int height = items.size() * dp(48) + dp(16);
        menu.setHeight(height);
        menu.setVerticalOffset(-(anchor.getHeight() + height));
        menu.setAdapter(new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1, items));
        menu.setOnItemClickListener((parent, view, position, id) -> {
            String picked = items.get(position);
            menu.dismiss();
            if (picked.equals(mResetLabel)) mListener.onReset();
            else mListener.onCopyToOtherHalf();
        });
        menu.setOnDismissListener(() -> {
            if (mMenu == menu) mMenu = null;
        });
        mMenu = menu;
        menu.show();
    }

    private void dismissMenu() {
        ListPopupWindow menu = mMenu;
        mMenu = null;
        if (menu != null)
            menu.dismiss();
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

    private void build(@NonNull EditorCard card, @NonNull List<EditorRows.Spec> specs) {
        mRows.removeAllViews();
        mRowViews.clear();
        mMore = null;
        mMoreRow = null;
        int tier = 0;
        for (EditorRows.Spec spec : specs) {
            if (spec.tier != tier) {
                if (tier != 0)
                    mRows.addView(divider());
                if (spec.tier == 3)
                    addMore();
                tier = spec.tier;
            }
            RowViews row = buildRow(spec.row);
            mRowViews.put(spec.row, row);
            if (spec.tier == 1)
                row.root.setTag(TIER_ONE_TAG);
            LinearLayout into = spec.tier == 3 && mMore != null ? mMore : mRows;
            into.addView(row.root);
        }
        restate();
    }

    private void addMore() {
        LinearLayout row = horizontalRow(SWITCH_ROW_MIN_DP);
        row.addView(iconView(R.drawable.ic_symbol_tune));
        TextView label = label(R.string.appearance_card_more);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chevron = new ImageView(mContext);
        chevron.setImageResource(R.drawable.ic_editor_expand_more);
        chevron.setImageTintList(ColorStateList.valueOf(EditorViews.color(mContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout more = new LinearLayout(mContext);
        more.setOrientation(LinearLayout.VERTICAL);
        more.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            boolean open = more.getVisibility() != View.VISIBLE;
            more.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setRotation(open ? 180f : 0f);
            row.setContentDescription(mContext.getString(open
                ? R.string.appearance_card_more_open : R.string.appearance_card_more));
            if (open)
                setDetent(DETENT_FULL, true);
        });
        row.setContentDescription(mContext.getString(R.string.appearance_card_more));
        mRows.addView(row);
        mRows.addView(more);
        mMore = more;
        mMoreRow = row;
    }

    /** Opens More, for a test or a door into a tier-3 row. */
    void openMore() {
        if (mMore != null && mMore.getVisibility() != View.VISIBLE && mMoreRow != null)
            mMoreRow.performClick();
    }

    @NonNull
    private RowViews buildRow(@NonNull Row row) {
        switch (row.kind) {
            case SLIDER: return sliderRow(row);
            case SWITCH: return switchRow(row);
            case SEGMENTS: return segmentsRow(row);
            case CHIPS: return chipsRow(row);
            case LOOK: return lookRow(row);
            case WALLPAPER: return wallpaperRow(row);
            case EXPANDER: return expanderRow(row);
            case MENU:
            case LINK:
            default:
                return actionRow(row);
        }
    }

    @NonNull
    private RowViews sliderRow(@NonNull Row row) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setMinimumHeight(dp(SLIDER_ROW_MIN_DP));
        root.setPadding(0, dp(8), 0, 0);
        RowViews views = new RowViews(row, root);
        LinearLayout line = labelLine(row, views, true);
        root.addView(line);
        Slider slider = newSlider();
        slider.setContentDescription(mContext.getString(row.labelRes));
        slider.setLabelFormatter(value -> mModel.readout(row, Math.round(value)));
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (mRestating || !fromUser)
                return;
            int v = Math.round(value);
            EditorViews.setText(views.value, mModel.readout(row, v));
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.setMarginStart(dp(ICON_COLUMN_DP - EDGE_DP));
        params.setMarginEnd(0);
        root.addView(slider, params);
        views.slider = slider;
        return views;
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
        // The buttons first, so their own paint and padding decide where they fit.
        List<MaterialButton> buttons = new ArrayList<>(choices.length);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        float widest = 0f;
        float total = 0f;
        for (String choice : choices) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.segment_button,
                null, false);
            button.setId(View.generateViewId());
            button.setText(choice);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinHeight(dp(SEGMENT_HEIGHT_DP));
            button.setEllipsize(TextUtils.TruncateAt.END);
            float need = needPx(button);
            widest = Math.max(widest, need);
            total += need;
            buttons.add(button);
        }
        boolean inline = choices.length == 2
            && labelPx(row) + dp(16) + total + dp(SEGMENT_GAP_DP) <= width;
        int share = choices.length == 0 ? width
            : (width - dp(SEGMENT_GAP_DP) * (choices.length - 1)) / choices.length;
        boolean vertical = !inline && widest > share;
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(inline ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setGravity(inline ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
        root.setMinimumHeight(dp(SWITCH_ROW_MIN_DP));
        root.setPadding(0, dp(8), inline ? dp(EDGE_DP) : 0, dp(8));
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
            chip.setCornerRadius(dp(12));
            chip.setCheckable(true);
            if (icons != null && i < icons.length)
                chip.setIconResource(icons[i]);
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
            if (chip.getIcon() != null) {
                chip.setIconGravity(stacked ? MaterialButton.ICON_GRAVITY_TEXT_START
                    : MaterialButton.ICON_GRAVITY_TOP);
                chip.setIconPadding(dp(stacked ? 8 : 2));
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
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        sliderParams.setMarginStart(dp(ICON_COLUMN_DP - EDGE_DP));
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
        stopsParams.setMarginStart(dp(ICON_COLUMN_DP - EDGE_DP));
        stopsParams.setMarginEnd(0);
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

    // ------------------------------------------------------------------------------ restating

    /** Every row read again from the model. */
    void restate() {
        for (RowViews views : mRowViews.values())
            restate(views);
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
            if (views.root.isEnabled() != enabled)
                views.root.setEnabled(enabled);
            views.root.setAlpha(enabled ? 1f : 0.5f);
            if (views.dot != null)
                EditorViews.setVisible(views.dot, mModel.ownValue(row));
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
            } else if (views.value != null && row.kind == Kind.SLIDER) {
                EditorViews.setText(views.value, mModel.readout(row, value));
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

    /** The label line over a slider or under-placed segments: icon, label, dot, value. */
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

    /** The width a segment needs for its word whole: its text, its padding and its check. */
    private float needPx(@NonNull MaterialButton button) {
        return button.getPaint().measureText(String.valueOf(button.getText()))
            + button.getPaddingLeft() + button.getPaddingRight() + button.getIconSize()
            + button.getIconPadding() + dp(4);
    }

    /** The width a row's label needs, in the label's own text appearance. */
    private float labelPx(@NonNull Row row) {
        TextView probe = label(row.labelRes);
        return probe.getPaint().measureText(mContext.getString(row.labelRes));
    }

    private int dp(float value) {
        return EditorViews.dp(mContext, value);
    }
}
