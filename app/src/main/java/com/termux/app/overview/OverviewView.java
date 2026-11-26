package com.termux.app.overview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.page.PageKind;
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
    @Nullable private LinearLayout mUndoBar;
    @Nullable private TextView mUndoText;
    /** The picture of each card, in strip order, with its kind; emptied when Overview closes. */
    @NonNull private final java.util.ArrayList<CardView> mPictures = new java.util.ArrayList<>();
    @NonNull private final java.util.ArrayList<PageKind> mPictureKinds = new java.util.ArrayList<>();

    OverviewView(@NonNull Context context, @NonNull OverviewLook look,
                 @NonNull Consumer<OverviewModel.Card> onCard, @NonNull Runnable onClose) {
        super(context);
        mLook = look;
        mOnCard = onCard;
        mOnClose = onClose;
        mGutter = res(R.dimen.overview_gutter);
        setClickable(true);
        setFocusable(true);
        setBackground(look.ground);

        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        addView(mContent, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        mContent.addView(buildHeader(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mScroll = new HorizontalScrollView(context);
        mScroll.setHorizontalScrollBarEnabled(false);
        mScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        mScroll.setClipToPadding(false);
        mScroll.setPadding(mGutter, 0, mGutter, 0);
        mStrip = new LinearLayout(context);
        mStrip.setOrientation(LinearLayout.HORIZONTAL);
        mScroll.addView(mStrip, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(mScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Clear the system bars and the cutout; the strip itself never reaches the bottom edge.
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            // Compared first: an equal padding would still ask for a layout.
            if (mContent.getPaddingLeft() != bars.left || mContent.getPaddingTop() != bars.top
                    || mContent.getPaddingRight() != bars.right || mContent.getPaddingBottom() != 0)
                mContent.setPadding(bars.left, bars.top, bars.right, 0);
            if (mBottomInset != bars.bottom) {
                mBottomInset = bars.bottom;
                placeUndoBar();
            }
            return insets;
        });
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

    /** Fills the strip from {@code model} and scrolls it to the origin card. */
    void apply(@NonNull OverviewModel model, @NonNull List<CardFacts> facts) {
        apply(model, facts, false);
    }

    /** As {@link #apply(OverviewModel, List)}; {@code keepScroll} leaves the strip where it is. */
    void apply(@NonNull OverviewModel model, @NonNull List<CardFacts> facts, boolean keepScroll) {
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

    /** Hold on a card: its place in the strip and its view. */
    void setOnHold(@Nullable java.util.function.BiConsumer<Integer, View> onHold) {
        mOnHold = onHold;
    }

    /** Card {@code index} stands aside (a removal held for undo) or comes back in its place. */
    void setCardHidden(int index, boolean hidden) {
        if (index < 0 || index >= mStrip.getChildCount()) return;
        mStrip.getChildAt(index).setVisibility(hidden ? GONE : VISIBLE);
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
        int bottom = mBottomInset + side;
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
        hideUndo();
        mStrip.removeAllViews();
        mPictures.clear();
        mPictureKinds.clear();
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
            mOnHold.accept(at, v);
            return true;
        });

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
