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
 * Overview's screen under the Terl switch (#430): the rows of a {@link StackOverviewModel}, top to
 * bottom and stood on the bottom edge where the thumb is, then the Saved row. Each window is a small
 * card: its Workspace's layout with its own tile lit ({@link CardView}), its kind's glyph (a Linux
 * app's own icon) and its name. A tap on a card or a row's title goes there; a hold lifts the card,
 * which a copy then follows under the finger, and letting go asks {@link Listener#onDrop}. The
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
        /** A saved Workspace's card was tapped. */
        void onSaved(@NonNull SavedCard saved);
        /**
         * Window {@code windowId}'s card let go over row {@code row} (-1 for none) and over window
         * {@code onCard}'s card ({@link TilingStack#NO_WINDOW} for none).
         */
        void onDrop(long windowId, int row, long onCard);
        /** What a drop there would do, for the mark under the finger. */
        @NonNull OverviewDropPolicy.StackDrop judge(long windowId, int row, long onCard);
        /** A Linux app's own icon by its app id; null draws its kind's glyph. */
        @Nullable Drawable appIcon(@NonNull String appId);
    }

    /** One row as it stands on screen. */
    private static final class RowViews {
        @NonNull final View block;
        @NonNull final List<View> cards = new ArrayList<>();
        @NonNull final List<GradientDrawable> faces = new ArrayList<>();
        @NonNull final List<StackOverviewModel.Card> models = new ArrayList<>();

        RowViews(@NonNull View block) {
            this.block = block;
        }
    }

    @NonNull private final OverviewLook mLook;
    @NonNull private final Listener mListener;
    @NonNull private final ScrollView mScroll;
    @NonNull private final LinearLayout mColumn;
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

    StackOverviewView(@NonNull Context context, @NonNull OverviewLook look, @NonNull Listener listener) {
        super(context);
        mLook = look;
        mListener = listener;
        mGutter = res(R.dimen.overview_gutter);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackground(look.ground);
        setContentDescription(context.getString(R.string.overview_title));

        mScroll = new ScrollView(context);
        mScroll.setFillViewport(true);
        mScroll.setVerticalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mColumn = new LinearLayout(context);
        mColumn.setOrientation(LinearLayout.VERTICAL);
        // Stood on the bottom: the free room is at the top, the rows toward the thumb.
        mColumn.setGravity(Gravity.BOTTOM);
        mScroll.addView(mColumn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        pad(Insets.NONE);
        // Clear the system bars and the camera cutout on every side.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            pad(insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout()));
            return insets;
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Added after the window's insets went out: ask for them once.
        ViewCompat.requestApplyInsets(this);
    }

    /** The column's padding for {@code insets}, set only when it changed. */
    private void pad(@NonNull Insets insets) {
        int l = insets.left + mGutter;
        int t = insets.top + mGutter;
        int r = insets.right + mGutter;
        int b = insets.bottom + mGutter;
        if (mColumn.getPaddingLeft() != l || mColumn.getPaddingTop() != t
                || mColumn.getPaddingRight() != r || mColumn.getPaddingBottom() != b) {
            mColumn.setPadding(l, t, r, b);
        }
    }

    // ---- applying the model ----------------------------------------------------------------------

    /** Shows {@code model}'s rows, then the Saved row; a drag under way ends first. */
    void apply(@NonNull StackOverviewModel model) {
        cancelDrag();
        mModel = model;
        rebuild();
    }

    /** The saved Workspaces arrived (#431 fills them; empty today). */
    void setSaved(@NonNull List<SavedCard> saved) {
        mSaved = new ArrayList<>(saved);
        if (mModel != null) rebuild();
    }

    private void rebuild() {
        StackOverviewModel model = mModel;
        if (model == null) return;
        mColumn.removeAllViews();
        mRows.clear();
        int gap = res(R.dimen.overview_row_gap);
        for (int r = 0; r < model.rows().size(); r++) {
            RowViews row = buildRow(model, r);
            mRows.add(row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = gap;
            mColumn.addView(row.block, params);
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
        int y = block.getBottom() + mColumn.getPaddingBottom() - mScroll.getHeight();
        mScroll.scrollTo(0, Math.max(0, y));
    }

    @NonNull
    private RowViews buildRow(@NonNull StackOverviewModel model, int index) {
        Context context = getContext();
        StackOverviewModel.Row row = model.rows().get(index);
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        RowViews views = new RowViews(block);
        String label = rowLabel(context, row);
        TextView title = buildTitle(label, row.front);
        if (!row.waiting) {
            title.setContentDescription(context.getString(R.string.overview_stack_go_to, label));
            title.setOnClickListener(v -> mListener.onPlace(row.place));
        }
        block.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            res(R.dimen.overview_stack_title_height)));

        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        int gap = res(R.dimen.overview_card_gap);
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
            scroller.addView(cards);
            block.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return views;
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
            item.setOnLongClickListener(v -> beginDrag(v, card.windowId));
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

        CardView picture = new CardView(context, mLook.onSurface, mLook.accent);
        picture.setFacts(card.facts);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor(OverviewLook.cardFace(mLook.onSurface));
        if (card.front) face.setStroke(res(R.dimen.overview_ring), mLook.accent);
        picture.setBackground(face);
        item.addView(picture, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            res(R.dimen.overview_stack_picture_height)));

        LinearLayout caption = new LinearLayout(context);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        int glyph = res(R.dimen.overview_glyph);
        ImageView icon = new ImageView(context);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        Drawable own = card.appId != null && card.kind == WindowKind.LINUX_APP
            ? mListener.appIcon(card.appId) : null;
        // A Linux app's own icon is its silhouette, inked as the kinds' glyphs are.
        if (own != null) icon.setImageDrawable(own);
        else icon.setImageResource(kindIcon(card.kind));
        icon.setColorFilter(card.front ? mLook.accent : mLook.onSurfaceVariant);
        caption.addView(icon, new LinearLayout.LayoutParams(glyph, glyph));
        TextView label = new TextView(context);
        label.setText(name);
        label.setTextColor(card.front ? mLook.accent : mLook.onSurface);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setMinWidth(0);
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(res(R.dimen.overview_glyph_gap));
        caption.addView(label, nameParams);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = res(R.dimen.overview_caption_gap);
        item.addView(caption, captionParams);
        if (into != null) {
            into.cards.add(item);
            into.faces.add(face);
            into.models.add(card);
        }
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

    /** The Saved row: the saved Workspaces, or a line saying there are none. */
    @NonNull
    private View buildSavedRow() {
        Context context = getContext();
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(buildTitle(context.getString(R.string.overview_saved_title), false),
            new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                res(R.dimen.overview_stack_title_height)));
        if (mSaved.isEmpty()) {
            TextView none = new TextView(context);
            none.setText(R.string.overview_stack_saved_empty);
            none.setTextColor(mLook.onSurfaceVariant);
            none.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_label_text));
            block.addView(none, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                res(R.dimen.overview_stack_card_width), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(gap);
            cards.addView(item, params);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);
        scroller.addView(cards);
        block.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
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
        if (mHeld != v) return false;
        float x = ev.getRawX();
        float y = ev.getRawY();
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

    /** The copy follows the finger at ({@code rawX}, {@code rawY}); the place it would land is marked. */
    private void follow(float rawX, float rawY) {
        View ghost = mGhost;
        if (ghost == null) return;
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
        for (int r = 0; r < mRows.size(); r++) {
            RowViews views = mRows.get(r);
            if (!contains(views.block, rawX, rawY)) continue;
            row = r;
            for (int i = 0; i < views.cards.size(); i++) {
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
                block.setBackground(null);
            }
        }
    }

    /** The drag ends with nothing done: the card is put back and the marks go. */
    private void cancelDrag() {
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
}
