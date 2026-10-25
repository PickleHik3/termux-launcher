package com.termux.app.surfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The stack of Material cards on the editor frame's left, one per item the edited layout puts
 * away ({@link HiddenItem}): a tap shows the item again, and a card dragged into the frame shows
 * it where it was. While an element of the frame is lifted the rail wears a dotted border round
 * its cards and the empty slot after them, the place to put it away; solid and tinted with the
 * finger over it. Views only; sizes come from {@link HiddenStackPolicy} and what a press means is
 * {@link Listener}'s.
 *
 * <p>The view spans the content view and takes no touch of its own, so only a card is hit.</p>
 */
final class HiddenStackView extends FrameLayout {

    interface Listener {
        /** A card was tapped. */
        void onShow(@NonNull HiddenItem item);

        /** A card was dragged and let go at ({@code rawX}, {@code rawY}); whether that is in the frame. */
        void onDropped(@NonNull HiddenItem item, float rawX, float rawY);

        /** A card began to be dragged. */
        default void onDragStarted(@NonNull HiddenItem item) {}

        /**
         * The dragged card is at ({@code rawX}, {@code rawY}): true while the frame draws its
         * element there instead (SPEC §3.6a item 2), and the card then hides.
         */
        default boolean onDragMoved(@NonNull HiddenItem item, float rawX, float rawY) {
            return false;
        }

        /** The drag was taken away before it was let go. */
        default void onDragCancelled(@NonNull HiddenItem item) {}
    }

    private static final float DRAG_ALPHA = 0.9f;

    @NonNull private final Map<HiddenItem, MaterialCardView> mCards = new EnumMap<>(HiddenItem.class);
    @NonNull private final View mZone;
    @NonNull private final List<HiddenItem> mItems = new ArrayList<>();
    @Nullable private Listener mListener;
    private final float mDensity;
    private final int mSlop;
    private int mCardWidthPx;
    private float mLabelPx;
    private boolean mZoneOffered;
    private boolean mZoneHovered;

    HiddenStackView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
        mZone = new View(context);
        mZone.setVisibility(GONE);
        mZone.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(mZone, new LayoutParams(0, 0));
    }

    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** The real font metrics the label size is fitted with: the card's own text paint. */
    @NonNull
    static HiddenStackPolicy.Measure measure(@NonNull Context context) {
        TextPaint paint = new TextPaint(new TextView(context).getPaint());
        return new HiddenStackPolicy.Measure() {
            @Override public float width(@NonNull String text, float sizePx) {
                paint.setTextSize(sizePx);
                return paint.measureText(text);
            }

            @Override public float lineHeight(float sizePx) {
                paint.setTextSize(sizePx);
                TextPaint.FontMetrics m = paint.getFontMetrics();
                return m.bottom - m.top;
            }
        };
    }

    /** Every label the stack can ever show, in the context's language. */
    @NonNull
    static List<String> allLabels(@NonNull Context context) {
        List<String> labels = new ArrayList<>();
        for (HiddenItem item : HiddenItem.values())
            labels.add(context.getString(item.labelRes));
        return labels;
    }

    /** The label size every card can wear before the stack's height asks for less: sp at the user's font scale, fitted to the width. */
    static float fittedLabelPx(@NonNull Context context, @NonNull HiddenStackPolicy.Measure measure) {
        float density = context.getResources().getDisplayMetrics().density;
        float ref = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            HiddenStackPolicy.LABEL_SP, context.getResources().getDisplayMetrics());
        return HiddenStackPolicy.labelSizePx(allLabels(context), measure,
            HiddenStackPolicy.cardWidthPx(density), density, ref);
    }

    /**
     * Shows {@code items} (top to bottom) as the cards of {@code rail}, each as tall as its own
     * rect, every label at
     * {@code labelPx}; the drop zone stands on the rail's border, round the cards and the empty
     * slot after them ({@link HiddenStackPolicy#rail}).
     */
    void set(@NonNull List<HiddenItem> items, @NonNull HiddenStackPolicy.Rail rail,
             float labelPx) {
        mCardWidthPx = HiddenStackPolicy.cardWidthPx(mDensity);
        boolean restyle = labelPx != mLabelPx;
        mLabelPx = labelPx;
        mItems.clear();
        mItems.addAll(items);
        for (HiddenItem item : HiddenItem.values()) {
            MaterialCardView card = mCards.get(item);
            int index = items.indexOf(item);
            if (index < 0 || index >= rail.cards.length) {
                if (card != null) card.setVisibility(GONE);
                continue;
            }
            if (card == null) {
                card = makeCard(item);
                mCards.put(item, card);
                addView(card, new LayoutParams(mCardWidthPx,
                    rail.cards[index][3] - rail.cards[index][1]));
            }
            card.setVisibility(VISIBLE);
            if (restyle) styleLabel(card);
            place(card, rail.cards[index]);
            card.setTranslationX(0f);
            card.setTranslationY(0f);
        }
        place(mZone, rail.border);
        styleZone();
    }

    /** Lays {@code view} on {@code rect}, {left, top, right, bottom} in this view's px. */
    private static void place(@NonNull View view, @NonNull int[] rect) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        int width = Math.max(1, rect[2] - rect[0]);
        int height = Math.max(1, rect[3] - rect[1]);
        if (params.width == width && params.height == height && params.leftMargin == rect[0]
            && params.topMargin == rect[1] && params.gravity == (Gravity.TOP | Gravity.START))
            return;
        params.width = width;
        params.height = height;
        params.leftMargin = rect[0];
        params.topMargin = rect[1];
        params.gravity = Gravity.TOP | Gravity.START;
        view.setLayoutParams(params);
    }

    /** The drop zone while an element of the frame is lifted: offered, and the finger over it. */
    void setZone(boolean offered, boolean hovered) {
        if (offered == mZoneOffered && hovered == mZoneHovered)
            return;
        mZoneOffered = offered;
        mZoneHovered = hovered;
        styleZone();
    }

    private void styleZone() {
        if (!mZoneOffered) {
            mZone.setVisibility(GONE);
            return;
        }
        int outline = MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOutline, 0);
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0);
        GradientDrawable shape = new GradientDrawable();
        // Concentric with the cards inside it.
        shape.setCornerRadius((HiddenStackPolicy.CARD_CORNER_DP + HiddenStackPolicy.ZONE_PAD_DP)
            * mDensity);
        // Offered, dotted; with the finger over it, solid and lightly tinted.
        if (mZoneHovered)
            shape.setStroke(Math.round(mDensity * 2f), primary);
        else
            shape.setStroke(Math.round(mDensity * 2f), outline, 4f * mDensity, 4f * mDensity);
        shape.setColor(mZoneHovered ? (primary & 0x00FFFFFF) | 0x26000000 : 0);
        mZone.setBackground(shape);
        mZone.setVisibility(VISIBLE);
    }

    private void styleLabel(@NonNull MaterialCardView card) {
        TextView label = (TextView) card.getChildAt(0);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, mLabelPx);
    }

    @SuppressLint("ClickableViewAccessibility")
    @NonNull
    private MaterialCardView makeCard(@NonNull HiddenItem item) {
        Context context = getContext();
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(HiddenStackPolicy.CARD_CORNER_DP * mDensity);
        card.setCardElevation(2f * mDensity);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        card.setClickable(true);
        card.setFocusable(true);
        TextView label = new TextView(context);
        label.setText(item.labelRes);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        label.setIncludeFontPadding(false);
        label.setMaxLines(HiddenStackPolicy.MAX_LINES);
        label.setTextColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnSurface, 0));
        label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad = HiddenStackPolicy.insetPx(mDensity);
        label.setPadding(pad, pad, pad, pad);
        card.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        card.setContentDescription(context.getString(
            com.termux.R.string.appearance_hidden_card_description,
            context.getString(item.labelRes)));
        card.setOnTouchListener(new DragTouch(item));
        return card;
    }

    /** A tap shows the item; a drag carries the card, and where it lands decides. */
    private final class DragTouch implements OnTouchListener {
        @NonNull private final HiddenItem mItem;
        private float mDownX;
        private float mDownY;
        private boolean mDragging;

        DragTouch(@NonNull HiddenItem item) {
            mItem = item;
        }

        @Override public boolean onTouch(@NonNull View view, @NonNull MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getRawX();
                    mDownY = event.getRawY();
                    mDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - mDownX;
                    float dy = event.getRawY() - mDownY;
                    if (!mDragging && Math.hypot(dx, dy) > mSlop) {
                        mDragging = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        view.setAlpha(DRAG_ALPHA);
                        view.setElevation(8f * mDensity);
                        view.bringToFront();
                        if (mListener != null) mListener.onDragStarted(mItem);
                    }
                    if (mDragging) {
                        view.setTranslationX(dx);
                        view.setTranslationY(dy);
                        // A chip over the rail, the element itself once the frame draws it.
                        boolean inFrame = mListener != null
                            && mListener.onDragMoved(mItem, event.getRawX(), event.getRawY());
                        view.setAlpha(inFrame ? 0f : DRAG_ALPHA);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    boolean dragged = mDragging;
                    mDragging = false;
                    view.setAlpha(1f);
                    view.setElevation(2f * mDensity);
                    Listener listener = mListener;
                    if (!dragged) {
                        if (listener != null) listener.onShow(mItem);
                        return true;
                    }
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    if (listener != null)
                        listener.onDropped(mItem, event.getRawX(), event.getRawY());
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    if (mDragging && mListener != null) mListener.onDragCancelled(mItem);
                    mDragging = false;
                    view.setAlpha(1f);
                    view.setElevation(2f * mDensity);
                    view.setTranslationX(0f);
                    view.setTranslationY(0f);
                    return true;
                default:
                    return true;
            }
        }
    }
}
