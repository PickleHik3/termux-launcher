package com.termux.app.overview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.wall.PagePolicy;
import com.termux.app.wall.pageswitch.PageGlyphPainter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Overview's screen, everything on one page and toward the thumb (spec overview-2026-11-24,
 * 2026-11-27): from the bottom up, Go to and its + over the bottom edge, then the Saved row, a row
 * for each Workspace with waiting sessions, the wall as a grid of small cards, and the title with
 * ✕ just above them; the free room is at the top. The cards' size is {@link OverviewBoard}'s,
 * which asks {@link OverviewGrid} so everything fits. It only applies an {@link OverviewModel}:
 * each card is a tinted face drawn with its page's {@link CardFacts} ({@link CardView}), its kind
 * glyph and a single-line name under it, the origin's wearing the accent ring. It holds no bitmap
 * of any page.
 */
final class OverviewView extends FrameLayout {

    /** A held card's size against its rest. */
    private static final float LIFT_SCALE = 1.04f;

    /** What a drag of a held card asks of Overview; the view only finds the finger's gap. */
    interface DragHost {
        /** A held card at {@code index} starts to move; false keeps it from being dragged. */
        boolean start(int index);
        /** Where the card held from {@code from} would land with the finger at {@code gap}, or -1. */
        int landing(int from, int gap);
        /** The card was let go at {@code gap}. */
        void drop(int from, int gap);
        /** The hold ended without the card moving. */
        void released(int index);
    }

    /** What Go to asks of Overview; the view only shows the field and the list. */
    interface GoToHost {
        /** The field's text changed. */
        void queryChanged(@NonNull String text);
        /** Enter, or a match tapped: go to the match's place (a card, or a session of one). */
        void go(@NonNull OverviewGoTo.Entry entry);
        /** The field took or lost focus: the keyboard follows it. */
        void fieldFocus(@NonNull EditText field, boolean focused);
    }

    @NonNull private final OverviewLook mLook;
    /** A card was tapped: Overview closes, and the wall goes to its page. */
    @NonNull private final Consumer<OverviewModel.Card> mOnCard;
    /** The close button. */
    @NonNull private final Runnable mOnClose;
    private final int mGutter;
    @NonNull private final PageGlyphPainter mGlyphs = new PageGlyphPainter();
    /** The column of the header and the cards, stood on the bottom above the field. */
    @NonNull private final LinearLayout mContent;
    /** The cards; it scrolls only when even the smallest cards do not fit (a tiny window). */
    @NonNull private final ScrollView mBoardScroll;
    @NonNull private final OverviewBoard mBoard;
    /** A card was held: its place on the wall and its view, which the menu stands against. */
    @Nullable private BiConsumer<Integer, View> mOnHold;
    /** The bottom edge's inset: the system bar's height, or the keyboard's when it is up. */
    private int mBottomInset;
    @Nullable private DragHost mDragHost;
    @Nullable private GoToHost mGoTo;
    @NonNull private final EditText mField;
    @NonNull private final ImageView mAddButton;
    @Nullable private View mAddScrim;
    @Nullable private LinearLayout mAddCard;
    @Nullable private Runnable mOnAddToggle;
    @NonNull private final ScrollView mResults;
    @NonNull private final LinearLayout mResultRows;
    /** The place the first match goes to; null while no match is shown. */
    @Nullable private OverviewGoTo.Entry mTopMatch;
    /** A session card was tapped (#357). */
    @Nullable private Consumer<OverviewModel.Session> mOnSession;
    /** A row's + was tapped (#358): the wall position of its Workspace card. */
    @Nullable private Consumer<Integer> mOnSessionAdd;
    /** A session card was held (#358): the session and its view, which the menu stands against. */
    @Nullable private BiConsumer<OverviewModel.Session, View> mOnSessionHold;
    @Nullable private SessionDragHost mSessionDragHost;
    /** The session card of the hold in progress, its session and its row block on the board. */
    @Nullable private View mSHeld;
    @Nullable private OverviewModel.Session mSHeldSession;
    private int mSHeldBlock = -1;
    private boolean mSDragging;
    private float mSDownX;
    private float mSDownY;
    private int mSGap = -1;
    /** The Saved row (#359): its cards, its block on the board, and what a card does. */
    @NonNull private List<SavedCard> mSaved = java.util.Collections.emptyList();
    @NonNull private final ArrayList<View> mSavedItems = new ArrayList<>();
    private int mSavedBlock = -1;
    @Nullable private Consumer<SavedCard> mOnSaved;
    @Nullable private BiConsumer<SavedCard, View> mOnSavedHold;
    /** Each session row's block on the board and its Workspace card's wall position. */
    @NonNull private final ArrayList<Integer> mRowCards = new ArrayList<>();
    /** The field's block: its height and the gutter above it. */
    private int mFieldBlock;
    /** The card of the hold in progress and where its touch began; a drag follows from it. */
    @Nullable private View mHeldView;
    private int mHeldIndex = -1;
    private boolean mDragging;
    private float mDownX;
    private float mDownY;
    private float mRawX;
    private float mRawY;
    private int mDragGap = -1;
    /** The landing mark of a drag, sized to the held card's picture when the drag begins. */
    @Nullable private View mDropBar;
    private final int[] mAt = new int[2];
    private final int[] mHere = new int[2];
    @Nullable private LinearLayout mUndoBar;
    @Nullable private TextView mUndoText;
    /** The picture of each card, in wall order, with its kind; emptied when Overview closes. */
    @NonNull private final ArrayList<CardView> mPictures = new ArrayList<>();
    @NonNull private final ArrayList<PageKind> mPictureKinds = new ArrayList<>();

    /**
     * A sideways swipe toward the left anywhere on Overview (#356): the wall takes the finger
     * back to its first page. Begun past the touch slop, so its travel is measured from there. A
     * held card keeps its stream (it asks its parents not to intercept), so a drag is never taken.
     */
    interface ReturnSwipe {
        /** The swipe is claimed; false leaves the touch to Overview. */
        boolean begin();
        /** The finger has gone {@code dxPx} on screen since the claim. */
        void drag(float dxPx);
        /** The finger let go; {@code leadPx} is how far it had gone before the claim. */
        void end(float velocityPxPerSec, float leadPx);
        void cancel();
    }

    @Nullable private ReturnSwipe mReturnSwipe;
    private final int mTouchSlop;
    /** The return's finger, on screen: Overview itself moves under it while it swipes. */
    private float mReturnDownX;
    private float mReturnDownY;
    private float mReturnClaimX;
    private boolean mReturnTracking;
    private boolean mReturnClaimed;
    @Nullable private VelocityTracker mReturnVelocity;

    OverviewView(@NonNull Context context, @NonNull OverviewLook look,
                 @NonNull Consumer<OverviewModel.Card> onCard, @NonNull Runnable onClose) {
        super(context);
        mLook = look;
        mOnCard = onCard;
        mOnClose = onClose;
        mGutter = res(R.dimen.overview_gutter);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        // Overview itself takes focus when it opens, so the field never does until it is tapped
        // and no keyboard comes up on its own.
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        setBackground(look.ground);

        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        // Stood on the bottom: the free room is at the top, the cards toward the thumb.
        mContent.setGravity(Gravity.BOTTOM);
        // A lifted or dragged card may pass over the edges of the column.
        mContent.setClipChildren(false);
        addView(mContent, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP));

        mContent.addView(buildHeader(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mBoard = new OverviewBoard(context);
        mBoard.setPadding(mGutter, 0, mGutter, 0);
        mBoardScroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                // The board is measured with no limit inside a scroller: it is told the room.
                mBoard.setViewport(MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                    ? 0 : MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        mBoardScroll.setVerticalScrollBarEnabled(false);
        mBoardScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mBoardScroll.setClipChildren(false);
        mBoardScroll.setClipToPadding(false);
        mBoardScroll.addView(mBoard, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(mBoardScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mResults = new ScrollView(context);
        mResults.setVisibility(GONE);
        mResultRows = new LinearLayout(context);
        mResultRows.setOrientation(LinearLayout.VERTICAL);
        mResultRows.setPadding(mGutter, 0, mGutter, 0);
        mResults.addView(mResultRows, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(mResults, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mField = buildField(context);
        mAddButton = buildAddButton(context);
        // Placed before any insets arrive, so the field, its + and the cards clear one another
        // even on a window that never sends any.
        placeBottom(new Rect());

        // Clear the system bars and the cutout. The field and the undo bar stand above the bottom
        // bar, or the keyboard when it is up, and the cards end a gutter above the field.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.isVisible(WindowInsetsCompat.Type.ime())
                ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            placeBottom(new Rect(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime)));
            return insets;
        });
    }

    /**
     * The column's padding and the bottom row's place for {@code insets}: the column ends a gutter
     * above the field, which stands a gutter above the bottom edge. Compared before it is set: an
     * equal padding would still ask for a layout.
     */
    private void placeBottom(@NonNull Rect insets) {
        int below = insets.bottom + mGutter + mFieldBlock;
        if (mContent.getPaddingLeft() != insets.left || mContent.getPaddingTop() != insets.top
                || mContent.getPaddingRight() != insets.right
                || mContent.getPaddingBottom() != below) {
            mContent.setPadding(insets.left, insets.top, insets.right, below);
        }
        mBottomInset = insets.bottom;
        placeUndoBar();
        placeField();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // A card has its page's shape: Overview's own window, whatever the split.
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (w > 0 && h > 0) mBoard.setAspect((float) h / w);
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Added after the window's insets went out: ask for them once.
        ViewCompat.requestApplyInsets(this);
    }

    /** The one field, over the bottom edge where the thumb is; not focused until tapped. */
    @NonNull
    private EditText buildField(@NonNull Context context) {
        EditText field = new EditText(context);
        field.setHint(R.string.overview_goto_hint);
        field.setHintTextColor(mLook.onSurfaceVariant);
        field.setTextColor(mLook.onSurface);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        field.setSingleLine(true);
        field.setMinWidth(0);
        field.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setFocusableInTouchMode(true);
        // The keyboard is shown by the activity's seam when the field takes focus, not by the view.
        field.setShowSoftInputOnFocus(false);
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(resF(R.dimen.overview_card_corner));
        plate.setColor(OverviewLook.cardFace(mLook.onSurface));
        field.setBackground(plate);
        int pad = mGutter;
        field.setPadding(pad, 0, pad, 0);
        field.setGravity(Gravity.CENTER_VERTICAL);
        mFieldBlock = res(R.dimen.overview_field_height) + res(R.dimen.overview_gutter);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (mGoTo != null) mGoTo.queryChanged(s.toString());
            }
        });
        field.setOnFocusChangeListener((v, focused) -> {
            if (mGoTo != null) mGoTo.fieldFocus(field, focused);
        });
        field.setOnEditorActionListener((v, action, event) -> {
            if (mGoTo != null && mTopMatch != null) mGoTo.go(mTopMatch);
            return true;
        });
        field.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && mGoTo != null && mTopMatch != null) {
                mGoTo.go(mTopMatch);
            }
            return true;
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, res(R.dimen.overview_field_height),
            Gravity.BOTTOM);
        addView(field, params);
        return field;
    }

    /** The + button beside the field, over the bottom edge where the thumb is. */
    @NonNull
    private ImageView buildAddButton(@NonNull Context context) {
        ImageView add = new ImageView(context);
        add.setImageResource(R.drawable.ic_rl_add);
        add.setColorFilter(mLook.onSurface);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setContentDescription(context.getString(R.string.overview_add));
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(resF(R.dimen.overview_card_corner));
        plate.setColor(OverviewLook.cardFace(mLook.onSurface));
        add.setBackground(plate);
        add.setOnClickListener(v -> {
            if (mOnAddToggle != null) mOnAddToggle.run();
        });
        int size = res(R.dimen.overview_field_height);
        addView(add, new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.END));
        return add;
    }

    private void placeField() {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mField.getLayoutParams();
        int side = res(R.dimen.overview_gutter);
        int bottom = mBottomInset + side;
        // The field leaves room for the + button at its end.
        int end = side + res(R.dimen.overview_field_height) + res(R.dimen.overview_glyph_gap);
        if (p.getMarginStart() != side || p.getMarginEnd() != end || p.bottomMargin != bottom) {
            p.setMarginStart(side);
            p.setMarginEnd(end);
            p.bottomMargin = bottom;
            mField.setLayoutParams(p);
        }
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) mAddButton.getLayoutParams();
        if (b.getMarginEnd() != side || b.bottomMargin != bottom) {
            b.setMarginEnd(side);
            b.bottomMargin = bottom;
            mAddButton.setLayoutParams(b);
        }
        placeAddCard();
    }

    // ---- The + card --------------------------------------------------------------------------

    /** What a tap on the + button does; the controller decides open or close. */
    void setOnAddToggle(@Nullable Runnable onToggle) {
        mOnAddToggle = onToggle;
    }

    boolean addCardOpen() {
        return mAddCard != null;
    }

    /**
     * Opens the + card above the field, with a row for each of {@code rows}; the button shows a
     * close mark while it is up. A tap outside runs {@code onOutside}. A row that
     * {@link OverviewAddPolicy#requiresHold} is added by holding it; a tap on it only says so.
     */
    void showAddCard(@NonNull List<OverviewAddPolicy.Row> rows,
                     @NonNull Consumer<OverviewAddPolicy.Row> onRow, @NonNull Runnable onOutside) {
        hideAddCard();
        Context context = getContext();
        View scrim = new View(context);
        scrim.setOnClickListener(v -> onOutside.run());
        // Under the button, which stays the way out.
        addView(scrim, indexOfChild(mAddButton), new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mAddScrim = scrim;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = res(R.dimen.overview_glyph_gap);
        card.setPadding(pad, pad, pad, pad);
        // The undo bar's plate: the ink itself, its text the opposite.
        GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(resF(R.dimen.overview_card_corner));
        plate.setColor(mLook.onSurface);
        card.setBackground(plate);
        for (OverviewAddPolicy.Row row : rows) card.addView(buildAddRow(row, onRow));
        addView(card, indexOfChild(mAddButton), new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        mAddCard = card;
        placeAddCard();
        mAddButton.setImageResource(R.drawable.ic_rl_close);
        mAddButton.setContentDescription(context.getString(R.string.overview_add_close));
    }

    void hideAddCard() {
        if (mAddCard == null) return;
        removeView(mAddCard);
        if (mAddScrim != null) removeView(mAddScrim);
        mAddCard = null;
        mAddScrim = null;
        mAddButton.setImageResource(R.drawable.ic_rl_add);
        mAddButton.setContentDescription(getContext().getString(R.string.overview_add));
    }

    /** The card stands on the field's row, above it; compared before it is set. */
    private void placeAddCard() {
        if (mAddCard == null) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mAddCard.getLayoutParams();
        int side = res(R.dimen.overview_gutter);
        int bottom = mBottomInset + side + mFieldBlock;
        if (p.getMarginStart() != side || p.getMarginEnd() != side || p.bottomMargin != bottom) {
            p.setMarginStart(side);
            p.setMarginEnd(side);
            p.bottomMargin = bottom;
            mAddCard.setLayoutParams(p);
        }
    }

    @NonNull
    private View buildAddRow(@NonNull OverviewAddPolicy.Row row,
                             @NonNull Consumer<OverviewAddPolicy.Row> onRow) {
        Context context = getContext();
        final boolean hold = OverviewAddPolicy.requiresHold(row);
        final int ink = invert(mLook.onSurface);
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(res(R.dimen.overview_close_target));
        int pad = res(R.dimen.overview_glyph_gap);
        line.setPadding(pad * 2, pad, pad * 2, pad);
        line.setClickable(true);
        line.setFocusable(true);

        ImageView glyph = new ImageView(context);
        glyph.setImageResource(addGlyph(row));
        glyph.setColorFilter(ink);
        int size = res(R.dimen.overview_glyph);
        line.addView(glyph, new LinearLayout.LayoutParams(size, size));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(addTitle(row));
        title.setTextColor(ink);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMinWidth(0);
        text.addView(title);
        final TextView about = new TextView(context);
        about.setText(addAbout(row));
        about.setTextColor(ink);
        about.setAlpha(0.8f);
        about.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        about.setMaxLines(2);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        about.setMinWidth(0);
        text.addView(about);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(pad * 2);
        line.addView(text, textParams);

        if (hold) {
            // A tap says how; the hold adds.
            line.setOnClickListener(v -> {
                if (about.getTag() == null) {
                    about.setTag(Boolean.TRUE);
                    about.setText(R.string.overview_add_hold);
                    about.setAlpha(1f);
                }
            });
            line.setOnLongClickListener(v -> {
                com.termux.app.haptics.Haptics.tick(v, HapticFeedbackConstants.LONG_PRESS);
                onRow.accept(row);
                return true;
            });
        } else {
            line.setOnClickListener(v -> onRow.accept(row));
        }
        return line;
    }

    private static int addGlyph(@NonNull OverviewAddPolicy.Row row) {
        switch (row) {
            case HOME_PAGE: return R.drawable.ic_symbol_home;
            case ASSISTANT: return R.drawable.ic_symbol_smart_toy;
            case WORKSPACE:
            default: return R.drawable.ic_rl_terminal;
        }
    }

    private static int addTitle(@NonNull OverviewAddPolicy.Row row) {
        switch (row) {
            case HOME_PAGE: return R.string.overview_add_home;
            case ASSISTANT: return R.string.overview_add_assistant;
            case WORKSPACE:
            default: return R.string.overview_add_workspace;
        }
    }

    private static int addAbout(@NonNull OverviewAddPolicy.Row row) {
        switch (row) {
            case HOME_PAGE: return R.string.overview_add_home_about;
            case ASSISTANT: return R.string.overview_add_assistant_about;
            case WORKSPACE:
            default: return R.string.overview_add_workspace_about;
        }
    }


    void setGoToHost(@Nullable GoToHost host) {
        mGoTo = host;
    }

    /** The field's text. */
    @NonNull
    String query() {
        return mField.getText().toString();
    }

    @NonNull
    EditText field() {
        return mField;
    }

    boolean hasQuery() {
        return mField.length() > 0;
    }

    /** Empties the field; the cards come back through the text change. */
    void clearQuery() {
        mField.setText("");
    }

    /** The field takes focus, which brings the keyboard. */
    void focusField() {
        mField.requestFocus();
    }

    /** Matches replace the cards, the wall and its rows alike; {@code null} brings them back. */
    void showMatches(@Nullable List<OverviewGoTo.Match> matches) {
        if (matches == null) {
            mTopMatch = null;
            mResultRows.removeAllViews();
            if (mResults.getVisibility() != GONE) mResults.setVisibility(GONE);
            if (mBoardScroll.getVisibility() != VISIBLE) mBoardScroll.setVisibility(VISIBLE);
            return;
        }
        mResultRows.removeAllViews();
        Context context = getContext();
        mTopMatch = matches.isEmpty() ? null : matches.get(0).entry;
        if (matches.isEmpty()) {
            TextView none = new TextView(context);
            none.setText(R.string.overview_goto_empty);
            none.setTextColor(mLook.onSurfaceVariant);
            none.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            mResultRows.addView(none);
        }
        for (int i = 0; i < matches.size(); i++) {
            mResultRows.addView(buildMatch(matches.get(i), i == 0));
        }
        if (mBoardScroll.getVisibility() != GONE) mBoardScroll.setVisibility(GONE);
        if (mResults.getVisibility() != VISIBLE) mResults.setVisibility(VISIBLE);
    }

    @NonNull
    private View buildMatch(@NonNull OverviewGoTo.Match match, boolean top) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(res(R.dimen.overview_close_target));
        int pad = res(R.dimen.overview_glyph_gap);
        row.setPadding(pad * 2, 0, pad * 2, 0);
        if (top) {
            // The one Enter goes to.
            GradientDrawable mark = new GradientDrawable();
            mark.setCornerRadius(resF(R.dimen.overview_card_corner));
            mark.setColor((mLook.accent & 0x00FFFFFF) | (0x33 << 24));
            row.setBackground(mark);
        }
        row.setClickable(true);
        row.setFocusable(true);
        final OverviewGoTo.Entry entry = match.entry;
        row.setOnClickListener(v -> {
            if (mGoTo != null) mGoTo.go(entry);
        });
        int glyph = res(R.dimen.overview_glyph);
        row.addView(new GlyphView(context, match.entry.kind, top ? mLook.accent : mLook.onSurfaceVariant),
            new LinearLayout.LayoutParams(glyph, glyph));
        TextView name = new TextView(context);
        name.setText(match.entry.name);
        name.setTextColor(top ? mLook.accent : mLook.onSurface);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(pad);
        row.addView(name, nameParams);
        if (match.entry.command != null) {
            TextView command = new TextView(context);
            command.setText(match.entry.command);
            command.setTextColor(mLook.onSurfaceVariant);
            command.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            command.setSingleLine(true);
            command.setEllipsize(android.text.TextUtils.TruncateAt.END);
            command.setMinWidth(0);
            LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            commandParams.setMarginStart(pad);
            row.addView(command, commandParams);
        }
        return row;
    }

    @NonNull
    private View buildHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(mGutter, res(R.dimen.overview_header_pad_top), mGutter,
            res(R.dimen.overview_header_pad_bottom));

        TextView title = new TextView(context);
        title.setText(R.string.overview_title);
        title.setTextColor(mLook.onSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_title_text));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.ic_rl_close);
        close.setColorFilter(mLook.onSurface);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription(context.getString(R.string.overview_close));
        close.setOnClickListener(v -> mOnClose.run());
        int target = res(R.dimen.overview_close_target);
        header.addView(close, new LinearLayout.LayoutParams(target, target));
        return header;
    }

    /**
     * Brings card {@code index} into sight: needed only when the board scrolls, on a window too
     * small for even the smallest cards; everywhere else every card is already in sight.
     */
    void scrollToCard(final int index) {
        mBoardScroll.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    mBoardScroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View card = mBoard.wallCard(index);
                    if (card == null || mBoard.fits()) return;
                    mBoardScroll.scrollTo(0, Math.max(0, card.getTop()));
                }
            });
    }

    /**
     * Fills the board from {@code model}: the wall's cards, then a row block for each Workspace
     * with waiting sessions ({@code sessionFacts} holding each row's card facts), then the Saved
     * row. {@code keepScroll} leaves a scrolling board where it is; else it shows its bottom.
     */
    void apply(@NonNull OverviewModel model, @NonNull List<CardFacts> facts,
               @NonNull List<List<CardFacts>> sessionFacts, boolean keepScroll) {
        final int scrollY = mBoardScroll.getScrollY();
        clearBoard();
        List<OverviewModel.Card> cards = model.cards();
        for (int i = 0; i < cards.size(); i++) {
            mBoard.addWallCard(buildCard(cards.get(i), i < facts.size() ? facts.get(i) : null));
        }
        applyRows(model.rows(), sessionFacts);
        addSavedBlock();
        // Only a board too tall for its window scrolls: it opens on its bottom, in the thumb's reach.
        mBoardScroll.post(() -> mBoardScroll.scrollTo(0, keepScroll ? scrollY : mBoard.getHeight()));
    }

    /** The drag of a held session card within its row (#358). */
    interface SessionDragHost {
        /** The held card starts to move; false refuses (it stays a hold only). */
        boolean start(@NonNull OverviewModel.Session session);
        /** The gap the card would land in, or {@link OverviewDropPolicy#NONE}. */
        int landing(@NonNull OverviewModel.Session session, int count, int gap);
        /** The card was let go at {@code gap}, one of {@code count}. */
        void drop(@NonNull OverviewModel.Session session, int count, int gap);
        /** The hold ended without a drag. */
        void released();
    }

    /** A row's + was tapped; the argument is its Workspace card's wall position. */
    void setOnSessionAdd(@Nullable Consumer<Integer> onSessionAdd) {
        mOnSessionAdd = onSessionAdd;
    }

    void setOnSessionHold(@Nullable BiConsumer<OverviewModel.Session, View> hold) {
        mOnSessionHold = hold;
    }

    void setSessionDragHost(@Nullable SessionDragHost host) {
        mSessionDragHost = host;
    }

    /** A held session card lifts while its menu is up, or sets back down. */
    void setSessionLifted(@Nullable View card, boolean lifted) {
        if (card == null) return;
        float scale = lifted ? LIFT_SCALE : 1f;
        card.animate().cancel();
        card.animate().scaleX(scale).scaleY(scale).setDuration(120L).start();
    }

    /** The screen box of a session card as it stands lifted; false when it is not laid out. */
    boolean viewBoundsOnScreen(@NonNull View card, @NonNull Rect out) {
        return boundsOnScreen(card, out);
    }

    /** A session card was tapped. */
    void setOnSession(@Nullable Consumer<OverviewModel.Session> onSession) {
        mOnSession = onSession;
    }

    /** A saved card was tapped. */
    void setOnSaved(@Nullable Consumer<SavedCard> onSaved) {
        mOnSaved = onSaved;
    }

    /** A saved card was held. */
    void setOnSavedHold(@Nullable BiConsumer<SavedCard, View> hold) {
        mOnSavedHold = hold;
    }

    /**
     * The Saved row's cards (#359): the row stands after the session rows, only when there is a
     * card, and is drawn again with them whenever the board is applied.
     */
    void setSaved(@NonNull List<SavedCard> saved) {
        mSaved = new ArrayList<>(saved);
        if (mSavedBlock >= 0 && mSavedBlock == mBoard.blockCount() - 1) mBoard.removeLastBlock();
        mSavedBlock = -1;
        mSavedItems.clear();
        addSavedBlock();
    }

    /** Saved card {@code index} stands aside (a deletion held for undo) or comes back. */
    void setSavedHidden(int index, boolean hidden) {
        if (index < 0 || index >= mSavedItems.size()) return;
        // The board leaves out a card that is gone, and the row with no card left.
        mSavedItems.get(index).setVisibility(hidden ? GONE : VISIBLE);
    }

    /**
     * The screen box of a card's view as it stands lifted (scaled about its middle), so a menu
     * placed against it clears its caption; false when it is not laid out.
     */
    static boolean boundsOnScreen(@NonNull View card, @NonNull Rect out) {
        if (card.getWidth() == 0) return false;
        if (!(card.getParent() instanceof View)) return false;
        int[] at = new int[2];
        ((View) card.getParent()).getLocationOnScreen(at);
        // From the parent: the card's rest box moved by its translation, grown about its middle.
        float cx = at[0] + card.getLeft() + card.getTranslationX() + card.getWidth() / 2f;
        float cy = at[1] + card.getTop() + card.getTranslationY() + card.getHeight() / 2f;
        float hw = card.getWidth() * LIFT_SCALE / 2f;
        float hh = card.getHeight() * LIFT_SCALE / 2f;
        out.set(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh));
        return true;
    }

    private void addSavedBlock() {
        if (mSaved.isEmpty()) return;
        Context context = getContext();
        mSavedBlock = mBoard.addBlock(buildRowTitle(context.getString(R.string.overview_saved_title)));
        for (SavedCard saved : mSaved) {
            // A saved card is drawn as a session card is; its name stands where a session's does.
            View item = buildSessionCard(
                new OverviewModel.Session(-1, -1, new OverviewModel.Waiting(saved.name, 0)),
                saved.facts);
            item.setOnClickListener(v -> {
                if (mOnSaved != null) mOnSaved.accept(saved);
            });
            item.setOnLongClickListener(v -> {
                if (mOnSavedHold == null) return false;
                mOnSavedHold.accept(saved, v);
                return true;
            });
            mBoard.addCell(mSavedBlock, item, true);
            mSavedItems.add(item);
        }
    }

    /** A session card's name: its own, else "Session n". */
    @NonNull
    static String sessionLabel(@NonNull Context context, @NonNull OverviewModel.Session session) {
        return session.name != null ? session.name
            : context.getString(R.string.overview_session_number, session.number);
    }

    @NonNull
    private TextView buildRowTitle(@NonNull String text) {
        TextView title = new TextView(getContext());
        title.setText(text);
        title.setTextColor(mLook.onSurfaceVariant);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setMinWidth(0);
        return title;
    }

    /**
     * Each Workspace's row block: "Sessions of <i>name</i>", its waiting sessions, and a + tile
     * that adds one (#358), wrapping like the wall. The title names the Workspace; no line ties it
     * to the card, which may stand anywhere in the grid above.
     */
    private void applyRows(@NonNull List<OverviewModel.Row> rows,
                           @NonNull List<List<CardFacts>> facts) {
        Context context = getContext();
        for (int r = 0; r < rows.size(); r++) {
            OverviewModel.Row row = rows.get(r);
            List<CardFacts> rowFacts = r < facts.size() ? facts.get(r) : null;
            int block = mBoard.addBlock(buildRowTitle(
                context.getString(R.string.overview_sessions_of, row.name)));
            for (int i = 0; i < row.sessions.size(); i++) {
                mBoard.addCell(block, buildSessionCard(row.sessions.get(i),
                    rowFacts != null && i < rowFacts.size() ? rowFacts.get(i) : null), true);
            }
            mBoard.addCell(block, buildAddTile(row.card), false);
            mRowCards.add(row.card);
        }
    }

    /** A row's + tile: a card-sized target that adds a session to the row's Workspace. */
    @NonNull
    private View buildAddTile(final int card) {
        Context context = getContext();
        ImageView add = new ImageView(context);
        add.setImageResource(R.drawable.ic_rl_add);
        add.setColorFilter(mLook.onSurfaceVariant);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setContentDescription(context.getString(R.string.overview_menu_new_session));
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setStroke(res(R.dimen.overview_pane_stroke), mLook.onSurfaceVariant);
        add.setBackground(face);
        add.setOnClickListener(v -> {
            if (mOnSessionAdd != null) mOnSessionAdd.accept(card);
        });
        return add;
    }

    private void clearBoard() {
        mBoard.clear();
        mPictures.clear();
        mPictureKinds.clear();
        mSavedBlock = -1;
        mSavedItems.clear();
        mRowCards.clear();
    }

    @NonNull
    private View buildSessionCard(@NonNull OverviewModel.Session session,
                                  @Nullable CardFacts facts) {
        Context context = getContext();
        String label = sessionLabel(context, session);
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        item.setOnClickListener(v -> {
            if (mOnSession != null) mOnSession.accept(session);
        });
        item.setOnLongClickListener(v -> {
            if (mOnSessionHold == null) return false;
            int block = mBoard.blockOf(v);
            if (block < 0) return false;
            mSHeld = v;
            mSHeldSession = session;
            mSHeldBlock = block;
            // Nothing above may take the stream from a card that may be dragged.
            mBoard.requestDisallowInterceptTouchEvent(true);
            mOnSessionHold.accept(session, v);
            return true;
        });
        item.setOnTouchListener(this::onSessionTouch);

        CardView block = new CardView(context, mLook.onSurface, mLook.accent);
        block.setFacts(facts);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        block.setBackground(face);
        // The picture takes what the caption leaves of the size the board gives the card.
        item.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(mLook.onSurface);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        // As tall as a wall card's caption (its glyph), so one caption height serves every card.
        name.setMinHeight(res(R.dimen.overview_glyph));
        name.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = res(R.dimen.overview_caption_gap);
        item.addView(name, nameParams);
        return item;
    }

    /** The swipe back to the first page, on the free area; null for none. */
    void setReturnSwipe(@Nullable ReturnSwipe swipe) {
        mReturnSwipe = swipe;
    }

    @Override public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        return returnTouch(event) || super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        return returnTouch(event) || super.onTouchEvent(event);
    }

    /**
     * The swipe back, watched on every touch (a held card asks its parents not to intercept, so a
     * drag is never taken): true once it is claimed, and for the rest of its stream. Read on
     * screen, since Overview slides under the finger.
     */
    private boolean returnTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mReturnClaimed = false;
                mReturnDownX = event.getRawX();
                mReturnDownY = event.getRawY();
                mReturnTracking = mReturnSwipe != null;
                if (mReturnTracking) {
                    if (mReturnVelocity == null) mReturnVelocity = VelocityTracker.obtain();
                    mReturnVelocity.clear();
                    addReturnMovement(event);
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!mReturnTracking || mReturnSwipe == null) return false;
                addReturnMovement(event);
                float x = event.getRawX();
                if (mReturnClaimed) {
                    mReturnSwipe.drag(x - mReturnClaimX);
                    return true;
                }
                float dx = x - mReturnDownX;
                float dy = event.getRawY() - mReturnDownY;
                if (dx < 0f && PagePolicy.pastSlop(dx, dy, mTouchSlop)) {
                    if (!mReturnSwipe.begin()) {
                        mReturnTracking = false;
                        return false;
                    }
                    mReturnClaimed = true;
                    mReturnClaimX = x;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                // Gone the other way or up and down first: not the swipe back.
                if (Math.abs(dx) > mTouchSlop || Math.abs(dy) > mTouchSlop) mReturnTracking = false;
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean claimed = mReturnClaimed;
                mReturnClaimed = false;
                mReturnTracking = false;
                if (!claimed || mReturnSwipe == null) return false;
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    mReturnSwipe.cancel();
                    return true;
                }
                addReturnMovement(event);
                float velocity = 0f;
                if (mReturnVelocity != null) {
                    mReturnVelocity.computeCurrentVelocity(1000);
                    velocity = mReturnVelocity.getXVelocity();
                }
                mReturnSwipe.end(velocity, mReturnClaimX - mReturnDownX);
                return true;
            }
            default:
                return mReturnClaimed;
        }
    }

    /** Adds {@code event} to the return's tracker on screen, moving it there and back. */
    private void addReturnMovement(@NonNull MotionEvent event) {
        if (mReturnVelocity == null) return;
        float dx = event.getRawX() - event.getX();
        float dy = event.getRawY() - event.getY();
        event.offsetLocation(dx, dy);
        mReturnVelocity.addMovement(event);
        event.offsetLocation(-dx, -dy);
    }


    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mReturnVelocity != null) {
            mReturnVelocity.recycle();
            mReturnVelocity = null;
        }
    }

    /** Hold on a card: its place on the wall and its view. */
    void setOnHold(@Nullable BiConsumer<Integer, View> onHold) {
        mOnHold = onHold;
    }

    void setDragHost(@Nullable DragHost host) {
        mDragHost = host;
    }

    /** Card {@code index} stands aside (a removal held for undo) or comes back in its place. */
    void setCardHidden(int index, boolean hidden) {
        View card = mBoard.wallCard(index);
        if (card == null) return;
        card.setVisibility(hidden ? GONE : VISIBLE);
        // Its session row stands aside with it; the board closes the room either way.
        for (int r = 0; r < mRowCards.size(); r++) {
            if (mRowCards.get(r) == index) mBoard.setBlockHidden(r, hidden);
        }
    }

    /** Card {@code index} lifts off the board while its menu is up, or sets back down. */
    void setCardLifted(int index, boolean lifted) {
        View card = mBoard.wallCard(index);
        if (card == null) return;
        float scale = lifted ? LIFT_SCALE : 1f;
        card.animate().cancel();
        card.animate().scaleX(scale).scaleY(scale)
            .setDuration(120L).start();
    }

    /** The screen box of card {@code index}'s picture and caption, lifted; false when not there. */
    boolean cardBoundsOnScreen(int index, @NonNull Rect out) {
        View card = mBoard.wallCard(index);
        return card != null && boundsOnScreen(card, out);
    }

    /** Drops every card and the facts it drew from; Overview is closing. */
    void release() {
        if (mDragging) endDrag();
        if (mSDragging) endSessionDrag();
        hideUndo();
        clearBoard();
    }

    @NonNull
    private View buildCard(@NonNull OverviewModel.Card card, @Nullable CardFacts facts) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(card.home
            ? getContext().getString(R.string.overview_label_home, card.label) : card.label);
        item.setOnClickListener(v -> mOnCard.accept(card));
        item.setOnLongClickListener(v -> {
            if (mOnHold == null) return false;
            int at = mBoard.wallIndexOf(v);
            if (at < 0) return false;
            mHeldView = v;
            mHeldIndex = at;
            // Nothing above (the swipe back, a scrolling board) may take the stream back from a
            // card that may now be dragged.
            mBoard.requestDisallowInterceptTouchEvent(true);
            mOnHold.accept(at, v);
            return true;
        });
        item.setOnTouchListener(this::onCardTouch);

        CardView block = new CardView(context, mLook.onSurface, mLook.accent);
        block.setFacts(facts);
        mPictures.add(block);
        mPictureKinds.add(card.kind);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        if (card.origin) face.setStroke(res(R.dimen.overview_ring), mLook.accent);
        block.setBackground(face);
        // The picture takes what the caption leaves of the size the board gives the card.
        item.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout caption = new LinearLayout(context);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        int glyph = res(R.dimen.overview_glyph);
        int tint = card.origin ? mLook.accent : mLook.onSurfaceVariant;
        caption.addView(new GlyphView(context, card.kind, tint),
            new LinearLayout.LayoutParams(glyph, glyph));
        TextView name = new TextView(context);
        name.setText(card.home ? getContext().getString(R.string.overview_label_home, card.label)
            : card.label);
        name.setTextColor(card.origin ? mLook.accent : mLook.onSurface);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(res(R.dimen.overview_glyph_gap));
        caption.addView(name, nameParams);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = res(R.dimen.overview_caption_gap);
        item.addView(caption, captionParams);
        return item;
    }

    // ---- Drag ---------------------------------------------------------------------------------

    private boolean onCardTouch(@NonNull View v, @NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mHeldView = null;
                mHeldIndex = -1;
                mDownX = ev.getRawX();
                mDownY = ev.getRawY();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    mRawX = ev.getRawX();
                    mRawY = ev.getRawY();
                    updateDrag();
                    return true;
                }
                if (mHeldView == v && mDragHost != null) {
                    if (Math.abs(ev.getRawX() - mDownX) > mTouchSlop
                            || Math.abs(ev.getRawY() - mDownY) > mTouchSlop) {
                        if (mDragHost.start(mHeldIndex)) {
                            beginDrag(ev);
                            return true;
                        }
                        // Refused: the hold goes on as a hold only.
                        mHeldView = null;
                    }
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    boolean up = ev.getActionMasked() == MotionEvent.ACTION_UP;
                    int from = mHeldIndex;
                    int gap = mDragGap;
                    endDrag();
                    if (up && mDragHost != null) mDragHost.drop(from, gap);
                    return true;
                }
                if (mHeldView == v && mDragHost != null) mDragHost.released(mHeldIndex);
                mHeldView = null;
                mHeldIndex = -1;
                return false;
            default:
                return false;
        }
    }

    private void beginDrag(@NonNull MotionEvent ev) {
        mDragging = true;
        mRawX = ev.getRawX();
        mRawY = ev.getRawY();
        mDragGap = -1;
        mBoard.setDrawnLast(mHeldView);
        setCardLifted(mHeldIndex, true);
        readyBar(mHeldView);
        updateDrag();
    }

    /**
     * The landing mark, made once and sized to {@code held}'s picture when a drag begins: one
     * layout then, none while the finger moves (it only translates).
     */
    private void readyBar(@Nullable View held) {
        int height = held instanceof ViewGroup && ((ViewGroup) held).getChildCount() > 0
            ? ((ViewGroup) held).getChildAt(0).getHeight() : 0;
        if (mDropBar == null) {
            View bar = new View(getContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(resF(R.dimen.overview_drag_bar) / 2f);
            shape.setColor(mLook.accent);
            bar.setBackground(shape);
            bar.setVisibility(GONE);
            addView(bar, new LayoutParams(res(R.dimen.overview_drag_bar), height,
                Gravity.TOP | Gravity.START));
            mDropBar = bar;
        } else if (mDropBar.getLayoutParams().height != height) {
            mDropBar.getLayoutParams().height = height;
            mDropBar.setLayoutParams(mDropBar.getLayoutParams());
        }
    }

    /** The held card follows the finger, the mark shows where it would land. */
    private void updateDrag() {
        View held = mHeldView;
        if (held == null) return;
        held.setTranslationX(mRawX - mDownX);
        held.setTranslationY(mRawY - mDownY);
        mBoard.getLocationOnScreen(mAt);
        float x = mRawX - mAt[0];
        float y = mRawY - mAt[1];
        int gap = mBoard.wallGap(x, y);
        mDragGap = gap;
        int landing = mDragHost == null ? OverviewDropPolicy.NONE
            : mDragHost.landing(mHeldIndex, gap);
        int n = mBoard.wallCount();
        boolean after = OverviewDropPolicy.markAfter(landing, n, mBoard.wallColumns(),
            mBoard.wallRow(y));
        placeBar(landing < 0 ? null : mBoard.wallCard(after ? landing - 1 : landing), after);
    }

    /**
     * The mark in the gap beside {@code ref} on the board: after it or before it; gone for none.
     */
    private void placeBar(@Nullable View ref, boolean after) {
        View bar = mDropBar;
        if (bar == null) return;
        if (ref == null) {
            if (bar.getVisibility() != GONE) bar.setVisibility(GONE);
            return;
        }
        // Midway through the gap the card would take.
        float half = mBoard.gap() / 2f;
        float x = after ? ref.getRight() + half : ref.getLeft() - half;
        mBoard.getLocationOnScreen(mAt);
        getLocationOnScreen(mHere);
        bar.setTranslationX(x + mAt[0] - mHere[0] - bar.getLayoutParams().width / 2f);
        bar.setTranslationY(ref.getTop() + mAt[1] - mHere[1]);
        if (bar.getVisibility() != VISIBLE) bar.setVisibility(VISIBLE);
    }

    private void endDrag() {
        mDragging = false;
        mBoard.setDrawnLast(null);
        View held = mHeldView;
        if (held != null) {
            held.animate().cancel();
            held.setTranslationX(0f);
            held.setTranslationY(0f);
            held.setScaleX(1f);
            held.setScaleY(1f);
        }
        if (mDropBar != null) mDropBar.setVisibility(GONE);
        mHeldView = null;
        mHeldIndex = -1;
    }

    // ---- Drag of a session card within its row (#358) -----------------------------------------

    private boolean onSessionTouch(@NonNull View v, @NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mSHeld = null;
                mSHeldSession = null;
                mSHeldBlock = -1;
                mSDownX = ev.getRawX();
                mSDownY = ev.getRawY();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mSDragging) {
                    mRawX = ev.getRawX();
                    mRawY = ev.getRawY();
                    updateSessionDrag();
                    return true;
                }
                if (mSHeld == v && mSessionDragHost != null && mSHeldSession != null) {
                    if (Math.abs(ev.getRawX() - mSDownX) > mTouchSlop
                            || Math.abs(ev.getRawY() - mSDownY) > mTouchSlop) {
                        if (mSessionDragHost.start(mSHeldSession)) {
                            beginSessionDrag(ev);
                            return true;
                        }
                        // Refused: the hold goes on as a hold only.
                        mSHeld = null;
                    }
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mSDragging) {
                    boolean up = ev.getActionMasked() == MotionEvent.ACTION_UP;
                    OverviewModel.Session session = mSHeldSession;
                    int block = mSHeldBlock;
                    int gap = mSGap;
                    endSessionDrag();
                    if (up && session != null && block >= 0 && mSessionDragHost != null) {
                        mSessionDragHost.drop(session, mBoard.cardCount(block), gap);
                    }
                    return true;
                }
                if (mSHeld == v && mSessionDragHost != null) mSessionDragHost.released();
                mSHeld = null;
                mSHeldSession = null;
                return false;
            default:
                return false;
        }
    }

    private void beginSessionDrag(@NonNull MotionEvent ev) {
        mSDragging = true;
        mRawX = ev.getRawX();
        mRawY = ev.getRawY();
        mSGap = -1;
        mBoard.setDrawnLast(mSHeld);
        setSessionLifted(mSHeld, true);
        readyBar(mSHeld);
        updateSessionDrag();
    }

    /** The held session follows the finger, the mark shows the gap it would land in. */
    private void updateSessionDrag() {
        View held = mSHeld;
        OverviewModel.Session session = mSHeldSession;
        int block = mSHeldBlock;
        if (held == null || session == null || block < 0) return;
        held.setTranslationX(mRawX - mSDownX);
        held.setTranslationY(mRawY - mSDownY);
        mBoard.getLocationOnScreen(mAt);
        float x = mRawX - mAt[0];
        float y = mRawY - mAt[1];
        int n = mBoard.cardCount(block);
        int gap = mBoard.blockGap(block, x, y);
        mSGap = gap;
        int landing = mSessionDragHost == null ? OverviewDropPolicy.NONE
            : mSessionDragHost.landing(session, n, gap);
        boolean after = OverviewDropPolicy.markAfter(landing, n, mBoard.rowColumns(),
            mBoard.blockRow(block, y));
        placeBar(landing < 0 ? null : mBoard.blockCard(block, after ? landing - 1 : landing),
            after);
    }

    private void endSessionDrag() {
        mSDragging = false;
        mBoard.setDrawnLast(null);
        View held = mSHeld;
        if (held != null) {
            held.animate().cancel();
            held.setTranslationX(0f);
            held.setTranslationY(0f);
            held.setScaleX(1f);
            held.setScaleY(1f);
        }
        if (mDropBar != null) mDropBar.setVisibility(GONE);
        mSHeld = null;
        mSHeldSession = null;
        mSHeldBlock = -1;
    }

    /**
     * The bar over the bottom edge, within the thumb's reach: {@code text} and an Undo that runs
     * {@code onUndo}. Hiding it (or showing another) is the caller's, with its own timer.
     */
    void showUndo(@NonNull String text, @NonNull Runnable onUndo) {
        if (mUndoBar == null) {
            Context context = getContext();
            LinearLayout bar = new LinearLayout(context);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int pad = res(R.dimen.overview_gutter);
            bar.setPadding(pad, 0, pad / 2, 0);
            GradientDrawable plate = new GradientDrawable();
            plate.setCornerRadius(resF(R.dimen.overview_card_corner));
            plate.setColor(mLook.onSurface);
            bar.setBackground(plate);
            TextView label = new TextView(context);
            label.setTextColor(invert(mLook.onSurface));
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bar.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView undo = new TextView(context);
            undo.setText(R.string.page_overview_undo);
            undo.setTextColor(mLook.accent);
            undo.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            undo.setTypeface(undo.getTypeface(), android.graphics.Typeface.BOLD);
            undo.setGravity(Gravity.CENTER);
            undo.setMinHeight(res(R.dimen.overview_close_target));
            undo.setPadding(pad, 0, pad, 0);
            undo.setTag("undo");
            bar.addView(undo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
            addView(bar, params);
            mUndoBar = bar;
            mUndoText = label;
            placeUndoBar();
        }
        mUndoText.setText(text);
        mUndoBar.findViewWithTag("undo").setOnClickListener(v -> onUndo.run());
        mUndoBar.setVisibility(VISIBLE);
    }

    void hideUndo() {
        if (mUndoBar != null) mUndoBar.setVisibility(GONE);
    }

    private void placeUndoBar() {
        if (mUndoBar == null) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mUndoBar.getLayoutParams();
        int side = res(R.dimen.overview_gutter);
        // Above the field.
        int bottom = mBottomInset + side + mFieldBlock;
        if (p.leftMargin != side || p.rightMargin != side || p.bottomMargin != bottom) {
            p.setMargins(side, 0, side, bottom);
            mUndoBar.setLayoutParams(p);
        }
    }

    /** The ink's opposite, for text on the undo plate (the plate is the ink itself). */
    private static int invert(int color) {
        return (color & 0xFF000000) | (~color & 0x00FFFFFF);
    }

    /** An Assistant card's facts arrived after Overview opened: its cards draw them now. */
    void setAssistantFacts(@Nullable CardFacts facts) {
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictureKinds.get(i) == PageKind.ASSISTANT) mPictures.get(i).setFacts(facts);
        }
    }

    /** One kind glyph, drawn by the page bar's painter; allocates nothing in onDraw. */
    private final class GlyphView extends View {
        @NonNull private final PageKind mKind;
        private final int mColor;

        GlyphView(@NonNull Context context, @NonNull PageKind kind, int color) {
            super(context);
            mKind = kind;
            mColor = color;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            mGlyphs.draw(canvas, mKind, 0, 0f, 0f, Math.min(getWidth(), getHeight()), mColor);
        }
    }

    private int res(int dimen) {
        Resources r = getResources();
        return r.getDimensionPixelSize(dimen);
    }

    private float resF(int dimen) {
        return getResources().getDimension(dimen);
    }
}
