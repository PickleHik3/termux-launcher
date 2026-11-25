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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageGlyphPainter;

import java.util.List;

/**
 * Overview's screen: a title, a close button and the wall as one strip of cards that scrolls
 * sideways. It only applies an {@link OverviewModel}: each card is a plain tinted block with its
 * kind glyph and a single-line name under it, the origin's wearing the accent ring, and the strip
 * opens scrolled to it. It holds no bitmap of any page and pins nothing to the bottom.
 */
final class OverviewView extends FrameLayout {

    /** What the view reads from the launcher and asks of it. */
    interface Host {
        /** The sheet's material: Global's glass at the sheets' floor (ADR 0031). */
        @NonNull android.graphics.drawable.Drawable background();
        int onSurface();
        int onSurfaceVariant();
        int accent();
        /** A card was tapped: Overview has closed, and the wall goes to its page. */
        void onCardChosen(@NonNull OverviewModel.Card card);
        /** The close button. */
        void onClose();
    }

    @NonNull private final Host mHost;
    @NonNull private final PageGlyphPainter mGlyphs = new PageGlyphPainter();
    @NonNull private final LinearLayout mStrip;
    @NonNull private final HorizontalScrollView mScroll;
    @NonNull private final LinearLayout mContent;
    private int mOriginIndex = -1;

    OverviewView(@NonNull Context context, @NonNull Host host) {
        super(context);
        mHost = host;
        setClickable(true);
        setFocusable(true);
        setBackground(host.background());

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
        int gutter = res(R.dimen.overview_gutter);
        mScroll.setPadding(gutter, 0, gutter, 0);
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
            mContent.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });
    }

    @NonNull
    private View buildHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int gutter = res(R.dimen.overview_gutter);
        header.setPadding(gutter, res(R.dimen.overview_header_pad_top), gutter,
            res(R.dimen.overview_header_pad_bottom));

        TextView title = new TextView(context);
        title.setText(R.string.overview_title);
        title.setTextColor(mHost.onSurface());
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, resF(R.dimen.overview_title_text));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.ic_rl_close);
        close.setColorFilter(mHost.onSurface());
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setContentDescription(context.getString(R.string.overview_close));
        close.setOnClickListener(v -> mHost.onClose());
        int target = res(R.dimen.overview_close_target);
        header.addView(close, new LinearLayout.LayoutParams(target, target));
        return header;
    }

    /** Fills the strip from {@code model} and scrolls it to the origin card. */
    void apply(@NonNull OverviewModel model) {
        mStrip.removeAllViews();
        List<OverviewModel.Card> cards = model.cards();
        for (int i = 0; i < cards.size(); i++) mStrip.addView(buildCard(cards.get(i)));
        mOriginIndex = model.originIndex();
        scrollToOrigin();
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
    private View buildCard(@NonNull OverviewModel.Card card) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(card.label);
        item.setOnClickListener(v -> mHost.onCardChosen(card));

        View block = new View(context);
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(resF(R.dimen.overview_card_corner));
        face.setColor((mHost.onSurface() & 0x00FFFFFF) | (0x24 << 24));
        if (card.origin) face.setStroke(res(R.dimen.overview_ring), mHost.accent());
        block.setBackground(face);
        item.addView(block, new LinearLayout.LayoutParams(res(R.dimen.overview_card_width),
            res(R.dimen.overview_card_height)));

        LinearLayout caption = new LinearLayout(context);
        caption.setOrientation(LinearLayout.HORIZONTAL);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        int glyph = res(R.dimen.overview_glyph);
        int tint = card.origin ? mHost.accent() : mHost.onSurfaceVariant();
        caption.addView(new GlyphView(context, card.kind, tint),
            new LinearLayout.LayoutParams(glyph, glyph));
        TextView name = new TextView(context);
        name.setText(card.label);
        name.setTextColor(card.origin ? mHost.accent() : mHost.onSurface());
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
