package com.termux.app.overview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Overview's screen: a title, a close button and the wall as one strip of cards that scrolls
 * sideways. It only applies an {@link OverviewModel}: each card is a tinted face drawn with its
 * page's {@link CardFacts} ({@link CardView}), its kind glyph and a single-line name under it, the
 * origin's wearing the accent ring, and the strip opens scrolled to it. It holds no bitmap of any
 * page and pins nothing to the bottom.
 */
final class OverviewView extends FrameLayout {

    /** A held card's size against its rest. */
    private static final float LIFT_SCALE = 1.04f;

    /** The edge scroll's tick while a card waits at an edge (a drag only; nothing ticks at rest). */
    private static final long SCROLL_TICK_MS = 16L;

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
    @NonNull private final LinearLayout mStrip;
    @NonNull private final HorizontalScrollView mScroll;
    @NonNull private final LinearLayout mContent;
    private int mOriginIndex = -1;
    /** A card was held: its place in the strip and its view, which the menu stands against. */
    @Nullable private java.util.function.BiConsumer<Integer, View> mOnHold;
    /** Where the undo bar sits above the bottom edge: the system bar's height. */
    private int mBottomInset;
    @Nullable private DragHost mDragHost;
    @Nullable private GoToHost mGoTo;
    @NonNull private final EditText mField;
    @NonNull private final ScrollView mResults;
    @NonNull private final LinearLayout mResultRows;
    /** The place the first match goes to; null while no match is shown. */
    @Nullable private OverviewGoTo.Entry mTopMatch;
    /** A session card was tapped (#357). */
    @Nullable private Consumer<OverviewModel.Session> mOnSession;
    /**
     * The session rows under the wall (#357): stood at the bottom, within the thumb's reach, and
     * pushed up as rows are added; with more than fit they scroll up and down on their own.
     */
    @NonNull private final ScrollView mRowsScroll;
    @NonNull private final LinearLayout mRows;
    /** Each row's view, its Workspace card's strip position, title and sideways scroller. */
    @NonNull private final java.util.ArrayList<View> mRowViews = new java.util.ArrayList<>();
    @NonNull private final java.util.ArrayList<Integer> mRowCards = new java.util.ArrayList<>();
    @NonNull private final java.util.ArrayList<View> mRowTitles = new java.util.ArrayList<>();
    @NonNull private final java.util.ArrayList<HorizontalScrollView> mRowScrolls =
        new java.util.ArrayList<>();
    /** The connector from a Workspace card down to its row, drawn under the cards. */
    @NonNull private final android.graphics.Paint mConnector =
        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    @NonNull private final int[] mConnectorFrom = new int[2];
    @NonNull private final int[] mConnectorTo = new int[2];
    @NonNull private final int[] mConnectorSelf = new int[2];
    /** Any scroll in the screen moves a connector's ends: it is drawn again (only with rows). */
    @NonNull private final android.view.ViewTreeObserver.OnScrollChangedListener mOnScrolled =
        () -> {
            if (!mRowViews.isEmpty()) invalidate();
        };
    /** The bar's room above the bottom edge: the field's block. */
    private int mFieldBlock;
    /** The card of the hold in progress and where its touch began; a drag follows from it. */
    @Nullable private View mHeldView;
    private int mHeldIndex = -1;
    private boolean mDragging;
    private float mDownX;
    private float mDownY;
    private int mDownScroll;
    private float mRawX;
    private float mRawY;
    private int mDragGap = -1;
    /** The strip position drawn last while it is dragged; -1 for none. */
    private int mHeldDraw = -1;
    private int mScrollDir;
    private long mScrollFrame;
    @Nullable private View mDropBar;
    private final int[] mAt = new int[2];
    private final int[] mHere = new int[2];
    private final Runnable mAutoScroll = this::autoScrollStep;
    @Nullable private LinearLayout mUndoBar;
    @Nullable private TextView mUndoText;
    /** The picture of each card, in strip order, with its kind; emptied when Overview closes. */
    @NonNull private final java.util.ArrayList<CardView> mPictures = new java.util.ArrayList<>();
    @NonNull private final java.util.ArrayList<PageKind> mPictureKinds = new java.util.ArrayList<>();

    /**
     * A sideways swipe toward the left on Overview's free area (#356): the wall takes the finger
     * back to its first page. Begun past the touch slop, so its travel is measured from there.
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
    @NonNull private final int[] mStripAt = new int[2];

    OverviewView(@NonNull Context context, @NonNull OverviewLook look,
                 @NonNull Consumer<OverviewModel.Card> onCard, @NonNull Runnable onClose) {
        super(context);
        mLook = look;
        mOnCard = onCard;
        mOnClose = onClose;
        mGutter = res(R.dimen.overview_gutter);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setFocusable(true);
        setBackground(look.ground);

        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        // The whole height: the session rows take what the wall leaves, down to the field.
        addView(mContent, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP));

        mContent.addView(buildHeader(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mScroll = new HorizontalScrollView(context);
        mScroll.setHorizontalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mScroll.setClipToPadding(false);
        mScroll.setPadding(mGutter, 0, mGutter, 0);
        mStrip = new LinearLayout(context) {
            { setChildrenDrawingOrderEnabled(true); }

            // A card being dragged draws over its neighbours without any Z: it is drawn last.
            @Override protected int getChildDrawingOrder(int count, int i) {
                int held = mHeldDraw;
                if (held < 0 || held >= count) return i;
                if (i == count - 1) return held;
                return i >= held ? i + 1 : i;
            }
        };
        mStrip.setOrientation(LinearLayout.HORIZONTAL);
        mScroll.addView(mStrip, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(mScroll, new LinearLayout.LayoutParams(
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

        mRowsScroll = new ScrollView(context);
        mRowsScroll.setFillViewport(true);
        mRowsScroll.setVerticalScrollBarEnabled(false);
        mRowsScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mRows = new LinearLayout(context);
        mRows.setOrientation(LinearLayout.VERTICAL);
        // Few rows stand at the bottom; each new one pushes those before it up.
        mRows.setGravity(Gravity.BOTTOM);
        mRowsScroll.addView(mRows, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(mRowsScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        mConnector.setStyle(android.graphics.Paint.Style.STROKE);
        mConnector.setStrokeWidth(resF(R.dimen.overview_connector));
        mConnector.setColor(mLook.onSurfaceVariant);

        mField = buildField(context);

        // Clear the system bars and the cutout; the strip itself never reaches the bottom edge.
        // The field and the undo bar stand above the bottom bar, or the keyboard when it is up.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.isVisible(WindowInsetsCompat.Type.ime())
                ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            int bottom = Math.max(bars.bottom, ime);
            // The results end above the field.
            int below = bottom + mFieldBlock;
            if (mResults.getPaddingBottom() != below) mResults.setPadding(0, 0, 0, below);
            // So do the session rows.
            if (mRowsScroll.getPaddingBottom() != below) mRowsScroll.setPadding(0, 0, 0, below);
            // Compared first: an equal padding would still ask for a layout.
            if (mContent.getPaddingLeft() != bars.left || mContent.getPaddingTop() != bars.top
                    || mContent.getPaddingRight() != bars.right || mContent.getPaddingBottom() != 0)
                mContent.setPadding(bars.left, bars.top, bars.right, 0);
            if (mBottomInset != bottom) {
                mBottomInset = bottom;
                placeUndoBar();
                placeField();
            }
            return insets;
        });
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

    private void placeField() {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mField.getLayoutParams();
        int side = res(R.dimen.overview_gutter);
        int bottom = mBottomInset + side;
        if (p.leftMargin != side || p.rightMargin != side || p.bottomMargin != bottom) {
            p.setMargins(side, 0, side, bottom);
            mField.setLayoutParams(p);
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

    /** Empties the field; the strip comes back through the text change. */
    void clearQuery() {
        mField.setText("");
    }

    /** The field takes focus, which brings the keyboard. */
    void focusField() {
        mField.requestFocus();
    }

    /** Matches replace the strip; {@code null} brings the strip back. */
    void showMatches(@Nullable List<OverviewGoTo.Match> matches) {
        if (matches == null) {
            mTopMatch = null;
            mResultRows.removeAllViews();
            if (mResults.getVisibility() != GONE) mResults.setVisibility(GONE);
            if (mScroll.getVisibility() != VISIBLE) mScroll.setVisibility(VISIBLE);
            if (mRowsScroll.getVisibility() != VISIBLE) mRowsScroll.setVisibility(VISIBLE);
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
        if (mScroll.getVisibility() != GONE) mScroll.setVisibility(GONE);
        // The list replaces the wall and its rows alike.
        if (mRowsScroll.getVisibility() != GONE) mRowsScroll.setVisibility(GONE);
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
     * Fills the strip from {@code model} and the session rows under it, {@code sessionFacts}
     * holding each row's card facts; {@code keepScroll} leaves the strip where it is, else it
     * scrolls to the origin card and the rows to their bottom.
     */
    void apply(@NonNull OverviewModel model, @NonNull List<CardFacts> facts,
               @NonNull List<List<CardFacts>> sessionFacts, boolean keepScroll) {
        final int scrollX = mScroll.getScrollX();
        mStrip.removeAllViews();
        mPictures.clear();
        mPictureKinds.clear();
        List<OverviewModel.Card> cards = model.cards();
        for (int i = 0; i < cards.size(); i++) {
            mStrip.addView(buildCard(cards.get(i), i < facts.size() ? facts.get(i) : null));
        }
        mOriginIndex = model.originIndex();
        if (keepScroll) restoreScroll(scrollX);
        else scrollToOrigin();
        applyRows(model.rows(), sessionFacts, keepScroll);
    }

    /** A session card was tapped. */
    void setOnSession(@Nullable Consumer<OverviewModel.Session> onSession) {
        mOnSession = onSession;
    }

    /** A session card's name: its own, else "Session n". */
    @NonNull
    static String sessionLabel(@NonNull Context context, @NonNull OverviewModel.Session session) {
        return session.name != null ? session.name
            : context.getString(R.string.overview_session_number, session.number);
    }

    private void applyRows(@NonNull List<OverviewModel.Row> rows,
                           @NonNull List<List<CardFacts>> facts, boolean keepScroll) {
        clearRows();
        Context context = getContext();
        for (int r = 0; r < rows.size(); r++) {
            OverviewModel.Row row = rows.get(r);
            List<CardFacts> rowFacts = r < facts.size() ? facts.get(r) : null;
            LinearLayout block = new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);

            TextView title = new TextView(context);
            title.setText(context.getString(R.string.overview_sessions_of, row.name));
            title.setTextColor(mLook.onSurfaceVariant);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setMinWidth(0);
            // The connector comes in from the start: the title stands clear of its end.
            title.setPadding(mGutter + res(R.dimen.overview_glyph), 0, mGutter, 0);
            block.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            HorizontalScrollView scroller = new HorizontalScrollView(context);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.setOverScrollMode(OVER_SCROLL_NEVER);
            scroller.setClipToPadding(false);
            scroller.setPadding(mGutter, 0, mGutter, 0);
            LinearLayout strip = new LinearLayout(context);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < row.sessions.size(); i++) {
                strip.addView(buildSessionCard(row.sessions.get(i),
                    rowFacts != null && i < rowFacts.size() ? rowFacts.get(i) : null));
            }
            scroller.addView(strip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scrollerParams.topMargin = res(R.dimen.overview_row_title_gap);
            block.addView(scroller, scrollerParams);

            LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blockParams.topMargin = res(R.dimen.overview_row_gap);
            mRows.addView(block, blockParams);
            mRowViews.add(block);
            mRowCards.add(row.card);
            mRowTitles.add(title);
            mRowScrolls.add(scroller);
        }
        if (!keepScroll && !mRowViews.isEmpty()) {
            // Opened with more rows than fit, the bottom ones show, within the thumb's reach.
            mRowsScroll.post(() -> mRowsScroll.fullScroll(View.FOCUS_DOWN));
        }
        invalidate();
    }

    private void clearRows() {
        mRows.removeAllViews();
        mRowViews.clear();
        mRowCards.clear();
        mRowTitles.clear();
        mRowScrolls.clear();
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

        CardView block = new CardView(context, mLook.onSurface, mLook.accent);
        block.setFacts(facts);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        block.setBackground(face);
        int width = res(R.dimen.overview_session_card_width);
        item.addView(block, new LinearLayout.LayoutParams(width,
            res(R.dimen.overview_session_card_height)));

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(mLook.onSurface);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(width,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = res(R.dimen.overview_caption_gap);
        item.addView(name, nameParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(res(R.dimen.overview_card_gap));
        item.setLayoutParams(params);
        return item;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(mOnScrolled);
    }

    /**
     * Each row's connector, under the cards: down from its Workspace card's caption to the row's
     * title, then along to the title's start. A card scrolled out of the strip's sight ties in
     * from the strip's nearer edge.
     */
    @Override protected void dispatchDraw(@NonNull Canvas canvas) {
        if (!mRowViews.isEmpty() && mScroll.getVisibility() == VISIBLE
                && mRowsScroll.getVisibility() == VISIBLE && mScroll.getWidth() > 0) {
            getLocationInWindow(mConnectorSelf);
            mScroll.getLocationInWindow(mConnectorFrom);
            float stripLeft = mConnectorFrom[0] - mConnectorSelf[0] + mGutter;
            float stripRight = stripLeft + mScroll.getWidth() - 2f * mGutter;
            mRowsScroll.getLocationInWindow(mConnectorTo);
            float rowsTop = mConnectorTo[1] - mConnectorSelf[1];
            float inset = res(R.dimen.overview_connector_inset);
            for (int r = 0; r < mRowViews.size(); r++) {
                int card = mRowCards.get(r);
                View cardView = card >= 0 && card < mStrip.getChildCount()
                    ? mStrip.getChildAt(card) : null;
                View title = mRowTitles.get(r);
                if (cardView == null || cardView.getVisibility() != VISIBLE
                        || mRowViews.get(r).getVisibility() != VISIBLE || title.getWidth() == 0) {
                    continue;
                }
                cardView.getLocationInWindow(mConnectorFrom);
                title.getLocationInWindow(mConnectorTo);
                float x = mConnectorFrom[0] - mConnectorSelf[0] + cardView.getWidth() / 2f;
                x = Math.max(stripLeft, Math.min(stripRight, x));
                float top = mConnectorFrom[1] - mConnectorSelf[1] + cardView.getHeight() + inset;
                float y = mConnectorTo[1] - mConnectorSelf[1] + title.getHeight() / 2f;
                // A row scrolled up under the wall has nothing to tie.
                if (y < rowsTop || y <= top) continue;
                float end = mConnectorTo[0] - mConnectorSelf[0] + title.getPaddingLeft() - inset;
                canvas.drawLine(x, top, x, y, mConnector);
                canvas.drawLine(x, y, end, y, mConnector);
            }
        }
        super.dispatchDraw(canvas);
    }

    private void restoreScroll(final int x) {
        mScroll.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (mScroll.getWidth() == 0) return;
                    mScroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mScroll.scrollTo(x, 0);
                }
            });
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
     * The swipe back, watched on every touch that starts off the strip (the strip scrolls
     * sideways and its cards are held and dragged): true once it is claimed, and for the rest of
     * its stream. Read on screen, since Overview slides under the finger.
     */
    private boolean returnTouch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mReturnClaimed = false;
                mReturnDownX = event.getRawX();
                mReturnDownY = event.getRawY();
                mReturnTracking = mReturnSwipe != null && !onStrip(mReturnDownX, mReturnDownY);
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

    /** Whether the screen point is on the strip of cards or a session row's cards. */
    private boolean onStrip(float rawX, float rawY) {
        if (within(mScroll, rawX, rawY)) return true;
        for (int r = 0; r < mRowScrolls.size(); r++) {
            if (within(mRowScrolls.get(r), rawX, rawY)) return true;
        }
        return false;
    }

    private boolean within(@NonNull View view, float rawX, float rawY) {
        if (view.getWidth() == 0) return false;
        view.getLocationOnScreen(mStripAt);
        return rawX >= mStripAt[0] && rawX < mStripAt[0] + view.getWidth()
            && rawY >= mStripAt[1] && rawY < mStripAt[1] + view.getHeight();
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(mOnScrolled);
        super.onDetachedFromWindow();
        if (mReturnVelocity != null) {
            mReturnVelocity.recycle();
            mReturnVelocity = null;
        }
    }

    /** Hold on a card: its place in the strip and its view. */
    void setOnHold(@Nullable java.util.function.BiConsumer<Integer, View> onHold) {
        mOnHold = onHold;
    }

    void setDragHost(@Nullable DragHost host) {
        mDragHost = host;
    }

    /** Card {@code index} stands aside (a removal held for undo) or comes back in its place. */
    void setCardHidden(int index, boolean hidden) {
        if (index < 0 || index >= mStrip.getChildCount()) return;
        mStrip.getChildAt(index).setVisibility(hidden ? GONE : VISIBLE);
        // Its session row stands aside with it.
        for (int r = 0; r < mRowCards.size(); r++) {
            if (mRowCards.get(r) == index) mRowViews.get(r).setVisibility(hidden ? GONE : VISIBLE);
        }
        invalidate();
    }

    /** Card {@code index} lifts off the strip while its menu is up, or sets back down. */
    void setCardLifted(int index, boolean lifted) {
        if (index < 0 || index >= mStrip.getChildCount()) return;
        View card = mStrip.getChildAt(index);
        float scale = lifted ? LIFT_SCALE : 1f;
        card.animate().cancel();
        card.animate().scaleX(scale).scaleY(scale)
            .setDuration(120L).start();
    }

    /** The screen box of card {@code index}'s picture and caption; false when it is not there. */
    boolean cardBoundsOnScreen(int index, @NonNull android.graphics.Rect out) {
        if (index < 0 || index >= mStrip.getChildCount()) return false;
        View card = mStrip.getChildAt(index);
        if (card.getWidth() == 0) return false;
        int[] at = new int[2];
        card.getLocationOnScreen(at);
        out.set(at[0], at[1], at[0] + card.getWidth(), at[1] + card.getHeight());
        return true;
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

    /** An Assistant card's facts arrived after the strip opened: its cards draw them now. */
    void setAssistantFacts(@Nullable CardFacts facts) {
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictureKinds.get(i) == PageKind.ASSISTANT) mPictures.get(i).setFacts(facts);
        }
    }

    /** Drops every card and the facts it drew from; Overview is closing. */
    void release() {
        if (mDragging) endDrag();
        hideUndo();
        mStrip.removeAllViews();
        mPictures.clear();
        mPictureKinds.clear();
        clearRows();
    }

    private void scrollToOrigin() {
        if (mOriginIndex < 0) return;
        // After layout, once: the card's centre goes to the strip's, the scroll clamps itself.
        mScroll.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (mScroll.getWidth() == 0 || mStrip.getChildCount() <= mOriginIndex) return;
                    mScroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View card = mStrip.getChildAt(mOriginIndex);
                    int target = card.getLeft() + card.getWidth() / 2 - mScroll.getWidth() / 2;
                    mScroll.scrollTo(Math.max(0, target), 0);
                }
            });
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
            int at = mStrip.indexOfChild(v);
            if (at < 0) return false;
            mHeldView = v;
            mHeldIndex = at;
            // The strip must not take the stream back from a card that may now be dragged.
            mScroll.requestDisallowInterceptTouchEvent(true);
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
        item.addView(block, new LinearLayout.LayoutParams(res(R.dimen.overview_card_width),
            res(R.dimen.overview_card_height)));

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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(res(R.dimen.overview_card_gap));
        item.setLayoutParams(params);
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
                mDownScroll = mScroll.getScrollX();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    mRawX = ev.getRawX();
                    mRawY = ev.getRawY();
                    updateDrag();
                    return true;
                }
                if (mHeldView == v && mDragHost != null) {
                    float slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.abs(ev.getRawX() - mDownX) > slop
                            || Math.abs(ev.getRawY() - mDownY) > slop) {
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
        mHeldDraw = mHeldIndex;
        mStrip.invalidate();
        setCardLifted(mHeldIndex, true);
        if (mDropBar == null) {
            View bar = new View(getContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(resF(R.dimen.overview_drag_bar) / 2f);
            shape.setColor(mLook.accent);
            bar.setBackground(shape);
            bar.setVisibility(GONE);
            addView(bar, new LayoutParams(res(R.dimen.overview_drag_bar),
                res(R.dimen.overview_card_height), Gravity.TOP | Gravity.START));
            mDropBar = bar;
        }
        updateDrag();
    }

    /** The held card follows the finger, the bar shows where it would land. */
    private void updateDrag() {
        View held = mHeldView;
        if (held == null) return;
        held.setTranslationX(mRawX - mDownX + (mScroll.getScrollX() - mDownScroll));
        held.setTranslationY(mRawY - mDownY);
        mScroll.getLocationOnScreen(mAt);
        int contentX = mScroll.getScrollX() + Math.round(mRawX) - mAt[0];
        int n = mStrip.getChildCount();
        int gap = 0;
        for (int i = 0; i < n; i++) {
            View card = mStrip.getChildAt(i);
            if (card.getLeft() + card.getWidth() / 2 < contentX) gap = i + 1;
        }
        mDragGap = gap;
        placeBar(mDragHost == null ? -1 : mDragHost.landing(mHeldIndex, gap), n);
        int edge = res(R.dimen.overview_drag_edge);
        float inScroll = mRawX - mAt[0];
        int dir = inScroll < edge ? -1 : inScroll > mScroll.getWidth() - edge ? 1 : 0;
        if (dir != mScrollDir) {
            mScrollDir = dir;
            removeCallbacks(mAutoScroll);
            if (dir != 0) {
                mScrollFrame = 0L;
                postDelayed(mAutoScroll, SCROLL_TICK_MS);
            }
        }
    }

    private void placeBar(int landing, int n) {
        View bar = mDropBar;
        if (bar == null) return;
        if (landing < 0 || n == 0) {
            if (bar.getVisibility() != GONE) bar.setVisibility(GONE);
            return;
        }
        // Midway through the gap the card would take: the card margin is the gap.
        int half = res(R.dimen.overview_card_gap) / 2;
        int x = landing < n ? mStrip.getChildAt(landing).getLeft() - half
            : mStrip.getChildAt(n - 1).getRight() + half;
        mScroll.getLocationOnScreen(mAt);
        getLocationOnScreen(mHere);
        bar.setTranslationX(x - mScroll.getScrollX() + mAt[0] - mHere[0]
            - bar.getLayoutParams().width / 2f);
        bar.setTranslationY(mStrip.getChildAt(0).getTop() + mAt[1] - mHere[1]);
        if (bar.getVisibility() != VISIBLE) bar.setVisibility(VISIBLE);
    }

    /** One frame of the strip scrolling while the card waits at an edge. */
    private void autoScrollStep() {
        if (!mDragging || mScrollDir == 0) return;
        long now = System.nanoTime();
        if (mScrollFrame != 0L) {
            int step = Math.round(mScrollDir * resF(R.dimen.overview_drag_scroll_speed)
                * (now - mScrollFrame) / 1e9f);
            int before = mScroll.getScrollX();
            mScroll.scrollBy(step, 0);
            if (mScroll.getScrollX() != before) updateDrag();
        }
        mScrollFrame = now;
        postDelayed(mAutoScroll, SCROLL_TICK_MS);
    }

    private void endDrag() {
        mDragging = false;
        mHeldDraw = -1;
        mStrip.invalidate();
        mScrollDir = 0;
        removeCallbacks(mAutoScroll);
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
