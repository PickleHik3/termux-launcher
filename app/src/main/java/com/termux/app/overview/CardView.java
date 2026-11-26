package com.termux.app.overview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.Collections;
import java.util.List;

/**
 * The picture on a card: the page's {@link CardFacts} as outlined panes with a few rows of text,
 * Home's cells as blocks, or an Assistant conversation as bubbles. It is drawn from plain lists
 * {@link CardGeometry} filled when the card got its size and facts, with its paints made once, so
 * {@code onDraw} allocates nothing; and it draws only when it is told to, so a card at rest costs
 * nothing. It holds no view and no bitmap of the page.
 */
final class CardView extends android.view.View {

    private static final float TEXT_ALPHA = 0.78f;
    private static final float OUTLINE_ALPHA = 0.34f;
    private static final float ACTIVE_OUTLINE_ALPHA = 0.7f;
    private static final float FILL_ALPHA = 0.1f;
    private static final float BLOCK_ALPHA = 0.2f;
    private static final float CELL_ALPHA = 0.26f;
    private static final float USER_BUBBLE_ALPHA = 0.3f;
    private static final float ANSWER_BUBBLE_ALPHA = 0.16f;
    /** An answer bubble's widest and a bubble's narrowest, as fractions of the card's width. */
    private static final float BUBBLE_MAX = 0.86f;
    private static final float BUBBLE_MIN = 0.3f;

    @Nullable private CardFacts mFacts;
    @NonNull private List<CardGeometry.PaneBox> mPanes = Collections.emptyList();
    @NonNull private List<CardGeometry.CellBox> mCells = Collections.emptyList();
    @NonNull private List<CardGeometry.BubbleBox> mBubbles = Collections.emptyList();

    @NonNull private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF mRect = new RectF();
    private final int mInk;
    private final int mAccent;
    private final float mPad;
    private final float mGap;
    private final float mCorner;
    private final float mTextPad;
    private final float mLineHeight;
    private final float mMinTextWidth;
    private final float mCellGap;
    private final float mIconInset;
    private final float mBubbleHeight;
    private final float mBubbleGap;

    CardView(@NonNull Context context, int ink, int accent) {
        super(context);
        Resources r = context.getResources();
        mInk = ink;
        mAccent = accent;
        mPad = r.getDimension(R.dimen.overview_card_pad);
        mGap = r.getDimension(R.dimen.overview_pane_gap);
        mCorner = r.getDimension(R.dimen.overview_pane_corner);
        mTextPad = r.getDimension(R.dimen.overview_pane_text_pad);
        mLineHeight = r.getDimension(R.dimen.overview_pane_text_line);
        mMinTextWidth = r.getDimension(R.dimen.overview_pane_text_min_width);
        mCellGap = r.getDimension(R.dimen.overview_cell_gap);
        mIconInset = r.getDimension(R.dimen.overview_icon_inset);
        mBubbleHeight = r.getDimension(R.dimen.overview_bubble_height);
        mBubbleGap = r.getDimension(R.dimen.overview_bubble_gap);
        mFill.setStyle(Paint.Style.FILL);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(r.getDimension(R.dimen.overview_pane_stroke));
        mText.setTypeface(Typeface.MONOSPACE);
        mText.setTextSize(r.getDimension(R.dimen.overview_pane_text_size));
        mText.setColor(withAlpha(ink, TEXT_ALPHA));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Gives the card its facts (null for a plain card) and draws them once. */
    void setFacts(@Nullable CardFacts facts) {
        mFacts = facts;
        layoutFacts();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutFacts();
    }

    /** Asks the geometry for the boxes at the current size; nothing runs here per frame. */
    private void layoutFacts() {
        float w = getWidth() - 2f * mPad;
        float h = getHeight() - 2f * mPad;
        mPanes = Collections.emptyList();
        mCells = Collections.emptyList();
        mBubbles = Collections.emptyList();
        CardFacts facts = mFacts;
        if (facts == null || w <= 0f || h <= 0f) return;
        if (facts instanceof CardFacts.Workspace) {
            mPanes = CardGeometry.workspace((CardFacts.Workspace) facts, w, h, mGap, mLineHeight,
                mTextPad, mMinTextWidth);
        } else if (facts instanceof CardFacts.Home) {
            mCells = CardGeometry.home((CardFacts.Home) facts, w, h, mCellGap, mIconInset);
        } else if (facts instanceof CardFacts.Assistant) {
            mBubbles = CardGeometry.assistant((CardFacts.Assistant) facts, w, h, mBubbleHeight,
                mBubbleGap, BUBBLE_MAX, BUBBLE_MIN);
        }
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        if (mPanes.isEmpty() && mCells.isEmpty() && mBubbles.isEmpty()) return;
        int save = canvas.save();
        canvas.translate(mPad, mPad);
        for (int i = 0; i < mPanes.size(); i++) drawPane(canvas, mPanes.get(i));
        for (int i = 0; i < mCells.size(); i++) {
            CardGeometry.CellBox c = mCells.get(i);
            mRect.set(c.left, c.top, c.right, c.bottom);
            mFill.setColor(withAlpha(mInk, c.kind == CardFacts.CellKind.WIDGET
                ? BLOCK_ALPHA : CELL_ALPHA));
            float radius = c.kind == CardFacts.CellKind.WIDGET ? mCorner
                : Math.min(c.width(), c.height()) / 3f;
            canvas.drawRoundRect(mRect, radius, radius, mFill);
        }
        for (int i = 0; i < mBubbles.size(); i++) {
            CardGeometry.BubbleBox b = mBubbles.get(i);
            mRect.set(b.left, b.top, b.right, b.bottom);
            mFill.setColor(b.fromUser ? withAlpha(mAccent, USER_BUBBLE_ALPHA)
                : withAlpha(mInk, ANSWER_BUBBLE_ALPHA));
            float radius = b.height() / 2f;
            canvas.drawRoundRect(mRect, radius, radius, mFill);
        }
        canvas.restoreToCount(save);
    }

    private void drawPane(@NonNull Canvas canvas, @NonNull CardGeometry.PaneBox pane) {
        mRect.set(pane.left, pane.top, pane.right, pane.bottom);
        boolean block = pane.textLines == 0;
        // A pane with text is an outline over a faint fill; a block is solid, so a Linux app and a
        // pane too small for text read as panes all the same.
        mFill.setColor(withAlpha(mInk, block ? BLOCK_ALPHA : FILL_ALPHA));
        canvas.drawRoundRect(mRect, mCorner, mCorner, mFill);
        mStroke.setColor(pane.pane.active ? withAlpha(mAccent, ACTIVE_OUTLINE_ALPHA)
            : withAlpha(mInk, OUTLINE_ALPHA));
        canvas.drawRoundRect(mRect, mCorner, mCorner, mStroke);
        if (block) return;
        int save = canvas.save();
        canvas.clipRect(pane.left + mTextPad, pane.top + mTextPad, pane.right - mTextPad,
            pane.bottom - mTextPad);
        String[] lines = pane.pane.lines;
        float baseline = pane.top + mTextPad - mText.ascent();
        for (int i = lines.length - pane.textLines; i < lines.length; i++) {
            canvas.drawText(lines[i], pane.left + mTextPad, baseline, mText);
            baseline += mLineHeight;
        }
        canvas.restoreToCount(save);
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(alpha * 255f) << 24);
    }
}
