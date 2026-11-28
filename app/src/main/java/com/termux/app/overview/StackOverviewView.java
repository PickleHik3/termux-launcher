package com.termux.app.overview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;

import com.termux.R;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Overview's screen under the Terl switch (#430): a glass sheet low on the screen (the sheets'
 * glass the Workspace list wears), "Overview" and an Edit pill along its top, then the rows of a
 * {@link StackOverviewModel} top to bottom, each place a rounded row (Home's pinned over the rest,
 * which scroll), then the Saved row. Each
 * window is a small tile: its icon (its kind's glyph, a Linux app's own), its name (a terminal's
 * title) and, in its corner, its Workspace's layout with its own tile lit ({@link CardView}). A tap on a card or a row's title goes there; a touch held still on
 * a card lifts it, a copy then follows under the finger (the rows and a row's cards scroll when it
 * nears their edge), and letting go asks {@link Listener#onDrop}. TalkBack moves a card by its
 * "Move to" actions instead: there is no long-click to start a drag it could not finish. The
 * screen only applies the model and reports touches; what a drop does is the controller's
 * ({@link OverviewDropPolicy#stackDrop}).
 */
final class StackOverviewView extends FrameLayout {

    /** What the screen reports. */
    interface Listener {
        /** A tap on window {@code windowId}'s card. */
        void onCard(long windowId);
        /** A tap on a row's title. */
        void onPlace(@NonNull Place place);
        /** A finger came down on {@code place}'s row (null: it lifted or left): a predicted landing. */
        default void onPlaceTouched(@Nullable Place place) {}
        /** The Edit button was tapped (#442). */
        void onEdit();
        /** A saved Workspace's card was tapped. */
        void onSaved(@NonNull SavedCard saved);
        /** A saved Workspace's card was held: its menu opens over {@code card}. */
        void onSavedHeld(@NonNull SavedCard saved, @NonNull View card);
        /**
         * Window {@code windowId}'s card let go over row {@code row} (-1 for none) and over window
         * {@code onCard}'s card ({@link TilingStack#NO_WINDOW} for none).
         */
        void onDrop(long windowId, int row, long onCard);
        /** What a drop there would do, for the mark under the finger. */
        @NonNull OverviewDropPolicy.StackDrop judge(long windowId, int row, long onCard);
        /** A Linux app's own icon by its app id; null draws its kind's glyph. */
        @Nullable Drawable appIcon(@NonNull String appId);
        /**
         * The camera's keep-out on an edge where Android's cutout reaches {@code cutoutPx} in,
         * by the cutout settings (the control bar's rail reads the same).
         */
        int cutoutClearPx(int cutoutPx);
    }

    /** One row as it stands on screen. */
    private static final class RowViews {
        @NonNull final View block;
        @NonNull final List<View> cards = new ArrayList<>();
        @NonNull final List<GradientDrawable> faces = new ArrayList<>();
        @NonNull final List<StackOverviewModel.Card> models = new ArrayList<>();
        /** The row's cards' scroller; null for a row with no cards. */
        @Nullable HorizontalScrollView scroller;
        /** The row's own face, worn again when a move's tint goes. */
        @Nullable Drawable face;

        RowViews(@NonNull View block) {
            this.block = block;
        }
    }

    @NonNull private final OverviewLook mLook;
    @NonNull private final Listener mListener;
    @NonNull private final ScrollView mScroll;
    @NonNull private final LinearLayout mColumn;
    /** Home's row, over the scrolling rows. */
    @NonNull private final LinearLayout mPinned;
    /** The glass sheet: the header, then the rows' scroller. */
    @NonNull private final LinearLayout mSheet;
    @NonNull private final List<RowViews> mRows = new ArrayList<>();
    @Nullable private StackOverviewModel mModel;
    @NonNull private List<SavedCard> mSaved = new ArrayList<>();
    private final int mGutter;
    private boolean mScrolledToFront;

    // The held card.
    @Nullable private View mHeld;
    private long mHeldId = TilingStack.NO_WINDOW;
    @Nullable private View mGhost;
    private float mGrabX;
    private float mGrabY;
    private int mTargetRow = -1;
    private long mTargetCard = TilingStack.NO_WINDOW;
    @NonNull private OverviewDropPolicy.StackDrop.Type mTargetType = OverviewDropPolicy.StackDrop.Type.NONE;
    @NonNull private final int[] mAt = new int[2];
    // A touch on a card before its hold: where it went down, and the hold waiting for it.
    @Nullable private View mPressed;
    private long mPressedId = TilingStack.NO_WINDOW;
    private float mDownX;
    private float mDownY;
    private final int mTouchSlop;
    @NonNull private final Runnable mHold = this::holdPressed;
    // Scrolling while the held card's finger is near an edge: one step a tick, scroll only.
    private float mFingerX;
    private float mFingerY;
    private boolean mEdgePosted;
    @NonNull private final Runnable mEdgeScroll = this::edgeScroll;
    private final float mEdgeBand;
    private final float mEdgeStep;

    StackOverviewView(@NonNull Context context, @NonNull OverviewLook look, @NonNull Listener listener) {
        super(context);
        mLook = look;
        mListener = listener;
        mGutter = res(R.dimen.overview_gutter);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        float density = getResources().getDisplayMetrics().density;
        mEdgeBand = EDGE_BAND_DP * density;
        mEdgeStep = EDGE_STEP_DP * density;
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription(context.getString(R.string.overview_title));

        // The sheet stands on the bottom: the free room is above it, the rows toward the thumb.
        mSheet = new LinearLayout(context);
        mSheet.setOrientation(LinearLayout.VERTICAL);
        mSheet.setClickable(true);
        final float corner = resF(R.dimen.overview_stack_sheet_corner);
        mSheet.setBackground(look.ground);
        mSheet.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), corner);
            }
        });
        mSheet.setClipToOutline(true);
        // The glass is cut for the sheet where it stands, once it has a size; posted, never in the pass.
        mSheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (look.glass == null || (r - l == or - ol && b - t == ob - ot && t == ot)) return;
            v.post(() -> {
                Drawable glass = look.glass.glass(corner, mSheet);
                if (glass != null) mSheet.setBackground(glass);
            });
        });
        mSheet.addView(buildHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // Home's row stands pinned over the scrolling Workspaces: with the keyboard up the room is
        // short and the front Workspace scrolled into it pushed Home out of sight (round 4).
        mPinned = new LinearLayout(context);
        mPinned.setOrientation(LinearLayout.VERTICAL);
        mSheet.addView(mPinned, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mColumn = new LinearLayout(context);
        mColumn.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        mSheet.addView(mScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mSheet, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        pad(Insets.NONE);
        // Clear the system bars on every side, and the camera by the cutout settings: Automatic its
        // reported cutout and the room around it, Custom that room even where none is reported.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            pad(Insets.of(Math.max(bars.left, mListener.cutoutClearPx(cut.left)),
                Math.max(bars.top, mListener.cutoutClearPx(cut.top)),
                Math.max(bars.right, mListener.cutoutClearPx(cut.right)),
                Math.max(bars.bottom, cut.bottom)));
            return insets;
        });
    }

    /** How near an edge a held card's finger scrolls, and how far a frame at most. */
    static final float EDGE_BAND_DP = 48f;
    static final float EDGE_STEP_DP = 14f;
    /** One scroll step a frame's time apart: a plain post, not a frame hook (those are the pipeline's). */
    static final long EDGE_TICK_MS = 16L;

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Added after the window's insets went out: ask for them once.
        ViewCompat.requestApplyInsets(this);
    }

    /** What the control bar takes of the screen's edges (left, right, bottom); the sheet stands clear. */
    @NonNull private final android.graphics.Rect mBarRoom = new android.graphics.Rect();
    @NonNull private Insets mInsets = Insets.NONE;

    /** The control bar's room on each edge ({@code room}'s left, right and bottom): the sheet stands clear. */
    void setBarRoom(@NonNull android.graphics.Rect room) {
        if (mBarRoom.equals(room)) return;
        mBarRoom.set(room);
        pad(mInsets);
    }

    /**
     * The sheet's margins for {@code insets} (clear of the bars and the camera, with room above it,
     * and clear of the control bar) and the rows' padding inside it, each set only when it changed.
     */
    private void pad(@NonNull Insets insets) {
        mInsets = insets;
        int margin = res(R.dimen.overview_stack_sheet_margin);
        LayoutParams params = (LayoutParams) mSheet.getLayoutParams();
        int l = Math.max(insets.left, mBarRoom.left) + margin;
        int t = insets.top + res(R.dimen.overview_stack_sheet_top_room);
        int r = Math.max(insets.right, mBarRoom.right) + margin;
        int b = Math.max(insets.bottom, mBarRoom.bottom) + margin;
        if (params.leftMargin != l || params.topMargin != t || params.rightMargin != r
                || params.bottomMargin != b) {
            params.setMargins(l, t, r, b);
            mSheet.setLayoutParams(params);
        }
        int inner = res(R.dimen.overview_stack_row_padding);
        if (mColumn.getPaddingLeft() != inner || mColumn.getPaddingBottom() != inner) {
            mColumn.setPadding(inner, 0, inner, inner);
        }
        if (mPinned.getPaddingLeft() != inner) mPinned.setPadding(inner, 0, inner, 0);
    }

    /** The sheet's top: "Overview" and the Edit pill, as the prototype has them. */
    @NonNull
    private View buildHeader() {
        Context context = getContext();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = res(R.dimen.overview_stack_row_padding);
        header.setPadding(mGutter + pad, mGutter, mGutter, pad);
        TextView title = new TextView(context);
        title.setText(R.string.overview_title);
        title.setAllCaps(true);
        title.setLetterSpacing(0.12f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(mLook.onSurfaceVariant);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        title.setSingleLine(true);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(buildEditButton(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            res(R.dimen.overview_stack_edit_height)));
        return header;
    }

    // ---- applying the model ----------------------------------------------------------------------

    /** Shows {@code model}'s rows, then the Saved row; a drag under way ends first. */
    void apply(@NonNull StackOverviewModel model) {
        cancelDrag();
        mModel = model;
        rebuild();
    }

    /** The saved Workspaces arrived (#431), or changed. */
    void setSaved(@NonNull List<SavedCard> saved) {
        mSaved = new ArrayList<>(saved);
        if (mModel != null) rebuild();
    }

    private void rebuild() {
        StackOverviewModel model = mModel;
        if (model == null) return;
        mColumn.removeAllViews();
        mPinned.removeAllViews();
        mRows.clear();
        int gap = res(R.dimen.overview_row_gap);
        for (int r = 0; r < model.rows().size(); r++) {
            RowViews row = buildRow(model, r);
            mRows.add(row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = gap;
            (model.rows().get(r).isHome() ? mPinned : mColumn).addView(row.block, params);
        }
        mColumn.addView(buildSavedRow(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!mScrolledToFront) {
            mScrolledToFront = true;
            int front = model.frontRow();
            if (front >= 0) post(() -> showRow(front));
        }
    }

    /** Scrolls so row {@code index} stands at the bottom of the room, when the rows overflow it. */
    private void showRow(int index) {
        if (index >= mRows.size()) return;
        View block = mRows.get(index).block;
        // Home's row is pinned in sight: nothing to scroll to.
        if (block.getParent() != mColumn) return;
        int y = block.getBottom() + mColumn.getPaddingBottom() - mScroll.getHeight();
        mScroll.scrollTo(0, Math.max(0, y));
    }

    @NonNull
    private RowViews buildRow(@NonNull StackOverviewModel model, int index) {
        Context context = getContext();
        StackOverviewModel.Row row = model.rows().get(index);
        // The prototype's row (round 5): the place on the left (its number and the glyph of its
        // window in focus, Home's glyph on Home, the Workspace's windows in a small picture as
        // they are laid out), its window cards to the right.
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.HORIZONTAL);
        RowViews views = new RowViews(block);
        views.face = rowFace(row.front);
        block.setBackground(views.face);
        int pad = res(R.dimen.overview_stack_row_padding);
        block.setPadding(pad, pad, pad, pad);
        String label = rowLabel(context, row);
        if (!row.waiting) {
            // The whole row goes to its place (round 3: only its label did); its cards keep their own.
            block.setClickable(true);
            block.setFocusable(true);
            block.setContentDescription(context.getString(R.string.overview_stack_go_to, label));
            block.setOnClickListener(v -> mListener.onPlace(row.place));
            block.setOnTouchListener((v, event) -> touchedRow(row.place, event));
        } else {
            block.setContentDescription(label);
        }
        block.addView(buildSide(row, label), new LinearLayout.LayoutParams(dp(SIDE_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        int gap = res(R.dimen.overview_card_gap);
        // Home's row: a card per Home page (a tap goes to it), then Home's Floats.
        for (int page = 0; page < row.homePages; page++) {
            View item = buildPageCard(page, row.front && row.place.homePage() == page);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                res(R.dimen.overview_stack_card_width), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            cards.addView(item, params);
        }
        for (StackOverviewModel.Card card : row.cards) {
            View item = buildCard(model, row, label, card, views);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                res(R.dimen.overview_stack_card_width), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            cards.addView(item, params);
        }
        if (row.waiting) cards.addView(buildDropHere(), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, res(R.dimen.overview_stack_picture_height)));
        if (cards.getChildCount() > 0) {
            HorizontalScrollView scroller = new HorizontalScrollView(context);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.setOverScrollMode(OVER_SCROLL_NEVER);
            if (!row.waiting) {
                // The room beside the cards is the row's too: the scroller would eat its tap.
                scroller.setFillViewport(true);
                cards.setOnClickListener(v -> mListener.onPlace(row.place));
                cards.setOnTouchListener((v, event) -> touchedRow(row.place, event));
                cards.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            scroller.addView(cards);
            block.addView(scroller, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            views.scroller = scroller;
        }
        return views;
    }

    /** The row's left column, wide enough for a number, a glyph and the layout's picture. */
    private static final float SIDE_DP = 76f;
    /** The layout's picture: a portrait room, as the card's mark is. */
    private static final float LAYOUT_WIDTH_DP = 36f;
    private static final float LAYOUT_HEIGHT_DP = 52f;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * The place's side of its row: Home's glyph; a Workspace's number (and its name under it when
     * it has one) with its window in focus's glyph, then its windows in a small picture laid out as
     * they are. Not read on its own: the row says where it goes.
     */
    @NonNull
    private View buildSide(@NonNull StackOverviewModel.Row row, @NonNull String label) {
        Context context = getContext();
        LinearLayout side = new LinearLayout(context);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int ink = row.front ? mLook.accent : mLook.onSurface;
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (row.isHome()) {
            // Home's row says its name beside its glyph (review P3-4).
            TextView word = new TextView(context);
            word.setText(R.string.overview_stack_home);
            word.setTextColor(ink);
            word.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            word.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            word.setSingleLine(true);
            word.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wordParams.setMarginStart(dp(6f));
            head.addView(word, wordParams);
        } else {
            TextView number = new TextView(context);
            number.setText(String.valueOf(row.place.workspaceNumber()));
            number.setTextColor(ink);
            number.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            number.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            number.setSingleLine(true);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.setMarginEnd(dp(6f));
            head.addView(number, numberParams);
        }
        int glyphRes = row.isHome() ? R.drawable.ic_ms_home
            : row.frontKind != null ? kindIcon(row.frontKind) : 0;
        if (glyphRes != 0) {
            ImageView glyph = new ImageView(context);
            glyph.setImageResource(glyphRes);
            glyph.setColorFilter(ink);
            int size = dp(16f);
            // Home's glyph leads its word; a Workspace's follows its number.
            head.addView(glyph, row.isHome() ? 0 : head.getChildCount(), new LinearLayout.LayoutParams(size, size));
        }
        side.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            res(R.dimen.overview_stack_title_height)));
        if (row.name != null && !row.isHome()) {
            TextView name = new TextView(context);
            name.setText(row.name);
            name.setTextColor(mLook.onSurfaceVariant);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text) * 0.85f);
            name.setSingleLine(true);
            // A long name keeps its start and its end ("Build … server"): review P3-4.
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            side.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (row.layoutFacts != null && !row.waiting) {
            CardView picture = new CardView(context, mLook.onSurface, mLook.accent);
            picture.setFacts(row.layoutFacts);
            LinearLayout.LayoutParams pictureParams = new LinearLayout.LayoutParams(
                dp(LAYOUT_WIDTH_DP), dp(LAYOUT_HEIGHT_DP));
            pictureParams.topMargin = dp(4f);
            side.addView(picture, pictureParams);
        }
        return side;
    }

    /** A row's face: a pane of the ink on the sheet, ringed in the accent for the place in front. */
    @NonNull
    private Drawable rowFace(boolean front) {
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_stack_row_corner));
        face.setColor((mLook.onSurface & 0x00FFFFFF) | 0x14000000);
        if (front) face.setStroke(res(R.dimen.overview_ring), mLook.accent);
        return face;
    }

    @NonNull
    static String rowLabel(@NonNull Context context, @NonNull StackOverviewModel.Row row) {
        if (row.isHome()) return context.getString(R.string.overview_stack_home);
        if (row.waiting) return context.getString(R.string.overview_stack_new_workspace);
        int number = row.place.workspaceNumber();
        return row.name != null
            ? context.getString(R.string.overview_stack_workspace_named, number, row.name)
            : context.getString(R.string.overview_stack_workspace, number);
    }

    @NonNull
    private TextView buildTitle(@NonNull String text, boolean front) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextColor(front ? mLook.accent : mLook.onSurfaceVariant);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMinWidth(0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    /** The waiting row's body: a card-high slot saying a window dragged here starts a Workspace. */
    @NonNull
    private View buildDropHere() {
        TextView slot = new TextView(getContext());
        slot.setText(R.string.overview_stack_drop_here);
        slot.setTextColor(mLook.onSurfaceVariant);
        slot.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        slot.setGravity(Gravity.CENTER);
        slot.setPadding(mGutter, 0, mGutter, 0);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setStroke(res(R.dimen.overview_pane_stroke), mLook.onSurfaceVariant, resF(R.dimen.overview_card_gap),
            resF(R.dimen.overview_card_gap) / 2f);
        slot.setBackground(face);
        return slot;
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private View buildCard(@NonNull StackOverviewModel model, @NonNull StackOverviewModel.Row row,
                           @NonNull String rowLabel, @NonNull StackOverviewModel.Card card,
                           @Nullable RowViews into) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        String name = cardName(context, card);
        if (into != null) {
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(context.getString(R.string.overview_stack_card, name, rowLabel));
            item.setOnClickListener(v -> mListener.onCard(card.windowId));
            // The hold is the touch's own (no long-click), so TalkBack never starts a drag.
            item.setTag(card.windowId);
            item.setOnTouchListener(this::onCardTouch);
            // TalkBack cannot drag: each other place that takes windows is an action of its own.
            for (int r = 0; r < model.rows().size(); r++) {
                StackOverviewModel.Row to = model.rows().get(r);
                if (to == row || !to.takesWindows) continue;
                final int target = r;
                ViewCompat.addAccessibilityAction(item,
                    context.getString(R.string.overview_stack_move_to, rowLabel(context, to)),
                    (AccessibilityViewCommand) (v, arguments) -> {
                        mListener.onDrop(card.windowId, target, TilingStack.NO_WINDOW);
                        return true;
                    });
            }
        }

        // The tile: the window's icon and its name, its place in the layout marked in the corner.
        FrameLayout tile = new FrameLayout(context);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        if (card.front) face.setStroke(res(R.dimen.overview_ring), mLook.accent);
        tile.setBackground(face);
        CardView mark = new CardView(context, mLook.onSurface, mLook.accent);
        mark.setFacts(card.facts);
        mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(
            res(R.dimen.overview_stack_mark_width), res(R.dimen.overview_stack_mark_height),
            Gravity.TOP | Gravity.END);
        markParams.topMargin = res(R.dimen.overview_caption_gap);
        markParams.setMarginEnd(res(R.dimen.overview_caption_gap));
        tile.addView(mark, markParams);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        int inset = res(R.dimen.overview_caption_gap);
        body.setPadding(inset, inset, inset, inset);
        int glyph = res(R.dimen.overview_stack_tile_icon);
        ImageView icon = new ImageView(context);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Drawable own = card.appId != null && card.kind == WindowKind.LINUX_APP
            ? mListener.appIcon(card.appId) : null;
        // A Linux app's own icon is its silhouette, inked as the kinds' glyphs are.
        if (own != null) icon.setImageDrawable(own);
        else icon.setImageResource(kindIcon(card.kind));
        icon.setColorFilter(card.front ? mLook.accent : mLook.onSurface);
        body.addView(icon, new LinearLayout.LayoutParams(glyph, glyph));
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextColor(card.front ? mLook.accent : mLook.onSurface);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text) * 0.9f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMinWidth(0);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = res(R.dimen.overview_caption_gap);
        body.addView(label, nameParams);
        tile.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        item.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            res(R.dimen.overview_stack_picture_height)));
        if (into != null) {
            into.cards.add(item);
            into.faces.add(face);
            into.models.add(card);
        }
        return item;
    }

    /**
     * One of Home's pages as a card of Home's row: Home's glyph and "Page N", schematic as a
     * window's card is; a tap goes to that page. Lit when it is the page in front.
     */
    @NonNull
    private View buildPageCard(int page, boolean front) {
        Context context = getContext();
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int inset = res(R.dimen.overview_caption_gap);
        tile.setPadding(inset, inset, inset, inset);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        if (front) face.setStroke(res(R.dimen.overview_ring), mLook.accent);
        tile.setBackground(face);
        String name = context.getString(R.string.overview_stack_home_page, page + 1);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(context.getString(R.string.overview_stack_card, name,
            context.getString(R.string.overview_stack_home)));
        tile.setOnClickListener(v -> mListener.onPlace(Place.home(page)));
        int glyph = res(R.dimen.overview_stack_tile_icon);
        ImageView icon = new ImageView(context);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setImageResource(R.drawable.ic_ms_home);
        icon.setColorFilter(front ? mLook.accent : mLook.onSurface);
        tile.addView(icon, new LinearLayout.LayoutParams(glyph, glyph));
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextColor(front ? mLook.accent : mLook.onSurface);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text) * 0.9f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = inset;
        tile.addView(label, nameParams);
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.addView(tile, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            res(R.dimen.overview_stack_picture_height)));
        return item;
    }

    @NonNull
    static String cardName(@NonNull Context context, @NonNull StackOverviewModel.Card card) {
        if (card.name != null) return card.name;
        switch (card.kind) {
            case LINUX_APP: return context.getString(R.string.control_bar_kind_linux_app);
            case DISPLAY: return context.getString(R.string.control_bar_kind_display);
            case ASSISTANT: return context.getString(R.string.control_bar_kind_assistant);
            case SHELL:
            default: return context.getString(R.string.control_bar_kind_terminal);
        }
    }

    private static int kindIcon(@NonNull WindowKind kind) {
        switch (kind) {
            case LINUX_APP: return R.drawable.ic_distro_linux;
            case DISPLAY: return R.drawable.ic_ms_desktop_windows;
            case ASSISTANT: return R.drawable.ic_ms_auto_awesome;
            case SHELL:
            default: return R.drawable.ic_ms_terminal;
        }
    }

    /** The Edit pill at the sheet's top, as the prototype has it: it leaves Overview for edit mode (#442). */
    @NonNull
    private View buildEditButton() {
        Context context = getContext();
        TextView edit = buildTitle(context.getString(R.string.overview_edit), false);
        edit.setTextColor(mLook.accent);
        edit.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        edit.setContentDescription(context.getString(R.string.overview_edit_description));
        edit.setMinHeight(res(R.dimen.overview_stack_edit_height));
        edit.setGravity(Gravity.CENTER);
        int side = mGutter;
        edit.setPadding(side, 0, side, 0);
        Drawable pencil = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_ms_edit);
        if (pencil != null) {
            pencil = pencil.mutate();
            int size = res(R.dimen.overview_glyph);
            pencil.setBounds(0, 0, size, size);
            pencil.setTint(mLook.accent);
            edit.setCompoundDrawablesRelative(pencil, null, null, null);
            edit.setCompoundDrawablePadding(res(R.dimen.overview_glyph_gap));
        }
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(res(R.dimen.overview_stack_edit_height) / 2f);
        pill.setColor(OverviewLook.cardFace(mLook.accent));
        edit.setBackground(pill);
        edit.setClickable(true);
        edit.setFocusable(true);
        edit.setOnClickListener(v -> mListener.onEdit());
        return edit;
    }

    /** The Saved row: the saved Workspaces, or a line saying there are none. */
    @NonNull
    private View buildSavedRow() {
        Context context = getContext();
        // As the places' rows: "Saved" on the left, the saved Workspaces to its right (round 5).
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setBackground(rowFace(false));
        int pad = res(R.dimen.overview_stack_row_padding);
        block.setPadding(pad, pad, pad, pad);
        TextView title = buildTitle(context.getString(R.string.overview_saved_title), false);
        title.setTextColor(mLook.onSurface);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.TOP);
        block.addView(title, new LinearLayout.LayoutParams(dp(SIDE_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (mSaved.isEmpty()) {
            TextView none = new TextView(context);
            none.setText(R.string.overview_stack_saved_empty);
            none.setTextColor(mLook.onSurfaceVariant);
            none.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            block.addView(none, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return block;
        }
        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        int gap = res(R.dimen.overview_card_gap);
        for (SavedCard saved : mSaved) {
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(saved.name);
            item.setOnClickListener(v -> mListener.onSaved(saved));
            item.setOnLongClickListener(v -> {
                mListener.onSavedHeld(saved, v);
                return true;
            });
            CardView picture = new CardView(context, mLook.onSurface, mLook.accent);
            picture.setFacts(saved.facts);
            GradientDrawable face = new GradientDrawable();
            face.setCornerRadius(resF(R.dimen.overview_card_corner));
            face.setColor(OverviewLook.cardFace(mLook.onSurface));
            picture.setBackground(face);
            item.addView(picture, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                res(R.dimen.overview_stack_picture_height)));
            TextView name = buildTitle(saved.name, false);
            name.setTextColor(mLook.onSurface);
            name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            // What it holds, as the prototype's card says it (round 3).
            if (saved.windowCount >= 0) {
                String count = context.getResources().getQuantityString(R.plurals.overview_saved_windows,
                    saved.windowCount, saved.windowCount);
                TextView windows = buildTitle(count, false);
                windows.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text) * 0.85f);
                windows.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                item.addView(windows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                item.setContentDescription(saved.name + ", " + count);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                res(R.dimen.overview_stack_card_width), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            cards.addView(item, params);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);
        scroller.addView(cards);
        block.addView(scroller, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return block;
    }

    // ---- the held card ---------------------------------------------------------------------------

    /** A hold on a card: it lifts, and a copy follows the finger until it lets go. */
    private boolean beginDrag(@NonNull View card, long windowId) {
        StackOverviewModel model = mModel;
        StackOverviewModel.Card held = model == null ? null : model.card(windowId);
        if (held == null || mHeld != null) return false;
        int row = model.rowOf(windowId);
        mHeld = card;
        mHeldId = windowId;
        // Neither the rows' scrollers nor the column's may take the stream back from it now.
        if (card.getParent() != null) card.getParent().requestDisallowInterceptTouchEvent(true);
        card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        card.setAlpha(0.35f);
        // The copy stands in this screen, over every row, at the card's place and size.
        View ghost = buildCard(model, model.rows().get(row), "", held, null);
        ghost.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(ghost, new LayoutParams(card.getWidth(), card.getHeight(), Gravity.TOP | Gravity.START));
        card.getLocationOnScreen(mAt);
        int cardX = mAt[0];
        int cardY = mAt[1];
        getLocationOnScreen(mAt);
        ghost.setTranslationX(cardX - mAt[0]);
        ghost.setTranslationY(cardY - mAt[1]);
        ghost.setScaleX(1.06f);
        ghost.setScaleY(1.06f);
        mGhost = ghost;
        mGrabX = -1f;
        return true;
    }

    private boolean onCardTouch(@NonNull View v, @NonNull MotionEvent ev) {
        float x = ev.getRawX();
        float y = ev.getRawY();
        if (mHeld != v) {
            // Before the hold: a still finger lifts the card; moving or letting go is the
            // scrollers' or the tap's.
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    removeCallbacks(mHold);
                    mPressed = v;
                    mPressedId = v.getTag() instanceof Long ? (Long) v.getTag() : TilingStack.NO_WINDOW;
                    mDownX = x;
                    mDownY = y;
                    postDelayed(mHold, ViewConfiguration.getLongPressTimeout());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mPressed == v && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) unpress();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mPressed == v) unpress();
                    break;
                default:
                    break;
            }
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                follow(x, y);
                return true;
            case MotionEvent.ACTION_UP: {
                follow(x, y);
                long held = mHeldId;
                int row = mTargetRow;
                long onCard = mTargetCard;
                cancelDrag();
                mListener.onDrop(held, row, onCard);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                return true;
            default:
                return true;
        }
    }

    private void unpress() {
        removeCallbacks(mHold);
        mPressed = null;
        mPressedId = TilingStack.NO_WINDOW;
    }

    /** The finger stayed still on a card: it lifts, and the tap that would follow is spent. */
    private void holdPressed() {
        View card = mPressed;
        long id = mPressedId;
        unpress();
        if (card == null || !beginDrag(card, id)) return;
        card.setPressed(false);
        mFingerX = mDownX;
        mFingerY = mDownY;
    }

    /** The copy follows the finger at ({@code rawX}, {@code rawY}); the place it would land is marked. */
    private void follow(float rawX, float rawY) {
        View ghost = mGhost;
        if (ghost == null) return;
        mFingerX = rawX;
        mFingerY = rawY;
        if (!mEdgePosted) {
            mEdgePosted = true;
            postDelayed(mEdgeScroll, EDGE_TICK_MS);
        }
        getLocationOnScreen(mAt);
        if (mGrabX < 0f) {
            // Where on the card the finger took it, so the card does not jump under it.
            mGrabX = rawX - mAt[0] - ghost.getTranslationX();
            mGrabY = rawY - mAt[1] - ghost.getTranslationY();
        }
        ghost.setTranslationX(rawX - mAt[0] - mGrabX);
        ghost.setTranslationY(rawY - mAt[1] - mGrabY);
        int row = -1;
        long onCard = TilingStack.NO_WINDOW;
        // Only what shows counts: a row scrolled under the sheet's header, or a card scrolled out
        // of its row, is not under the finger though its bounds are.
        boolean inRows = contains(mScroll, rawX, rawY);
        boolean inPinned = contains(mPinned, rawX, rawY);
        for (int r = 0; (inRows || inPinned) && r < mRows.size(); r++) {
            RowViews views = mRows.get(r);
            // Home's pinned row counts where it stands; the others only inside the scroll's room.
            boolean pinned = views.block.getParent() == mPinned;
            if (pinned ? !inPinned : !inRows) continue;
            if (!contains(views.block, rawX, rawY)) continue;
            row = r;
            boolean inCards = views.scroller == null || contains(views.scroller, rawX, rawY);
            for (int i = 0; inCards && i < views.cards.size(); i++) {
                if (contains(views.cards.get(i), rawX, rawY)) onCard = views.models.get(i).windowId;
            }
            break;
        }
        if (row == mTargetRow && onCard == mTargetCard) return;
        mark(false);
        mTargetRow = row;
        mTargetCard = onCard;
        mTargetType = mListener.judge(mHeldId, row, onCard).type;
        mark(true);
    }

    /**
     * One step of scrolling while the held card's finger is near an edge: the column toward the
     * rows above or below, the row under the finger toward its cards off either side; then the
     * place under the finger is read again. Stops once the finger is away from every edge.
     */
    private void edgeScroll() {
        mEdgePosted = false;
        if (mGhost == null) return;
        mScroll.getLocationOnScreen(mAt);
        float dy = OverviewDropPolicy.edgeScrollPx(mFingerY, mAt[1], mAt[1] + mScroll.getHeight(),
            mEdgeBand, mEdgeStep);
        int stepY = dy == 0f || !mScroll.canScrollVertically(dy < 0f ? -1 : 1) ? 0 : Math.round(dy);
        int stepX = 0;
        HorizontalScrollView row = mTargetRow >= 0 && mTargetRow < mRows.size()
            ? mRows.get(mTargetRow).scroller : null;
        if (row != null) {
            row.getLocationOnScreen(mAt);
            float dx = OverviewDropPolicy.edgeScrollPx(mFingerX, mAt[0], mAt[0] + row.getWidth(),
                mEdgeBand, mEdgeStep);
            stepX = dx == 0f || !row.canScrollHorizontally(dx < 0f ? -1 : 1) ? 0 : Math.round(dx);
            if (stepX != 0) row.scrollBy(stepX, 0);
        }
        if (stepY != 0) mScroll.scrollBy(0, stepY);
        if (stepX == 0 && stepY == 0) return;
        // The rows moved under a still finger: what it is over may have changed.
        follow(mFingerX, mFingerY);
    }

    /** Lights (or puts out) the card a tab would join, or the row a move would go to. */
    private void mark(boolean on) {
        if (mTargetType == OverviewDropPolicy.StackDrop.Type.TAB) {
            for (RowViews views : mRows) {
                for (int i = 0; i < views.models.size(); i++) {
                    if (views.models.get(i).windowId != mTargetCard) continue;
                    StackOverviewModel.Card card = views.models.get(i);
                    boolean ring = on || card.front;
                    views.faces.get(i).setStroke(ring ? res(R.dimen.overview_ring) : 0, mLook.accent);
                }
            }
        } else if (mTargetType == OverviewDropPolicy.StackDrop.Type.MOVE
                && mTargetRow >= 0 && mTargetRow < mRows.size()) {
            View block = mRows.get(mTargetRow).block;
            if (on) {
                GradientDrawable tint = new GradientDrawable();
                tint.setCornerRadius(resF(R.dimen.overview_card_corner));
                tint.setColor(OverviewLook.cardFace(mLook.accent));
                block.setBackground(tint);
            } else {
                block.setBackground(mRows.get(mTargetRow).face);
            }
        }
    }

    /** The drag ends with nothing done: the card is put back and the marks go. */
    private void cancelDrag() {
        removeCallbacks(mEdgeScroll);
        mEdgePosted = false;
        unpress();
        mark(false);
        mTargetRow = -1;
        mTargetCard = TilingStack.NO_WINDOW;
        mTargetType = OverviewDropPolicy.StackDrop.Type.NONE;
        if (mGhost != null) removeView(mGhost);
        mGhost = null;
        if (mHeld != null) mHeld.setAlpha(1f);
        mHeld = null;
        mHeldId = TilingStack.NO_WINDOW;
    }

    private boolean contains(@NonNull View view, float rawX, float rawY) {
        view.getLocationOnScreen(mAt);
        return rawX >= mAt[0] && rawX < mAt[0] + view.getWidth()
            && rawY >= mAt[1] && rawY < mAt[1] + view.getHeight();
    }

    /** The screen goes: a drag under way ends. */
    void release() {
        cancelDrag();
    }

    private int res(int dimen) {
        Resources r = getResources();
        return r.getDimensionPixelSize(dimen);
    }

    private float resF(int dimen) {
        return getResources().getDimension(dimen);
    }

    /** A row under the finger is a predicted landing (live window residency); the touch goes on as before. */
    private boolean touchedRow(@NonNull Place place, @NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mListener.onPlaceTouched(place);
                break;
            case MotionEvent.ACTION_CANCEL:
                mListener.onPlaceTouched(null);
                break;
            default:
                break;
        }
        return false;
    }
}
