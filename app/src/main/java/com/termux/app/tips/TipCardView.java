package com.termux.app.tips;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

/**
 * One first-run hint (#443): a small glass card with a line of copy and a ×, standing in the CHROME
 * layer. The wrapper takes no touch of its own, so Home and the bar under it work as always; only
 * the card does. Made once, shown at {@link #show}, gone at {@link #close}.
 */
public final class TipCardView extends FrameLayout {

    static final float WIDTH_DP = 280f;
    private static final float GAP_DP = 8f;
    private static final float PAD_DP = 12f;
    private static final float CLOSE_DP = 36f;
    private static final float CLOSE_ICON_DP = 16f;

    /** What the card needs of the bar: its glass, its ink and its corner. */
    public interface Style {
        @Nullable Drawable glass(float radiusPx, @NonNull View anchor);

        float cornerRadiusPx();

        int ink();
    }

    @NonNull private final Style mStyle;
    @NonNull private final LinearLayout mCard;
    @NonNull private final TextView mText;
    /** The ×: Home's three hints only; a situation's hint is a pill that takes no touch. */
    @NonNull private final View mClose;
    private final float mDensity;

    public TipCardView(@NonNull Context context, @NonNull Style style, @NonNull Runnable onDismiss) {
        super(context);
        mStyle = style;
        mDensity = context.getResources().getDisplayMetrics().density;
        mCard = new LinearLayout(context);
        mCard.setOrientation(LinearLayout.HORIZONTAL);
        mCard.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(PAD_DP * mDensity);
        mCard.setPadding(pad, pad / 2, pad / 2, pad / 2);
        mCard.setClickable(true);
        int ink = style.ink();
        mText = new TextView(context);
        mText.setTextColor(ink);
        mText.setTextSize(15f);
        mCard.addView(mText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = new ImageView(context);
        Drawable glyph = ContextCompat.getDrawable(context, R.drawable.ic_assistant_close);
        if (glyph != null) close.setImageDrawable(glyph.mutate());
        close.setColorFilter(ColorUtils.setAlphaComponent(ink, 220));
        int inset = Math.round((CLOSE_DP - CLOSE_ICON_DP) / 2f * mDensity);
        close.setPadding(inset, inset, inset, inset);
        close.setContentDescription(context.getString(R.string.tips_dismiss));
        close.setOnClickListener(v -> onDismiss.run());
        int size = Math.round(CLOSE_DP * mDensity);
        mClose = close;
        mCard.addView(close, new LinearLayout.LayoutParams(size, size));
        addView(mCard, new LayoutParams(Math.round(WIDTH_DP * mDensity),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        mCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::dress);
        });
        // The wrapper is no touch target: whatever is under it keeps its touches.
        setClickable(false);
    }

    @Nullable private Tip mTip;
    @Nullable private View mBar;
    @Nullable private ViewGroup mHost;
    private final View.OnLayoutChangeListener mFollow = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (l != ol || t != ot || r != or || b != ob) post(this::place);
    };

    /**
     * Puts the card for {@code tip} on screen. Over {@code bar} (beside it, when it is a rail) for the bar's
     * hints; in the middle of the screen for Home's. It follows the bar and the layer: a turn to the
     * rail places it again. False when there is no layer to stand in.
     */
    public boolean show(@NonNull Tip tip, @NonNull View bar) {
        close();
        ViewGroup host = WindowLayers.host(id -> bar.getRootView().findViewById(id), WindowLayer.CHROME);
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        mTip = tip;
        mBar = bar;
        mHost = host;
        mText.setText(copy(tip));
        dressFor(tip);
        place();
        host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addOnLayoutChangeListener(mFollow);
        host.addOnLayoutChangeListener(mFollow);
        return true;
    }

    /**
     * Home's three hints are a card with a ×; a situation's (full screen, navigation mode) is the
     * prototype's pill at the top: as wide as its line, no ×, no touch, so nothing under it is
     * covered from the finger (round 4).
     */
    private void dressFor(@NonNull Tip tip) {
        boolean pill = tip.situational();
        mClose.setVisibility(pill ? GONE : VISIBLE);
        mCard.setClickable(!pill);
        mText.setTextSize(pill ? 13f : 15f);
        mText.setSingleLine(pill);
        int pad = Math.round(PAD_DP * mDensity);
        mCard.setPadding(pad + (pill ? pad / 3 : 0), pad / 2, pill ? pad + pad / 3 : pad / 2, pad / 2);
        LayoutParams params = (LayoutParams) mCard.getLayoutParams();
        int width = pill ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(WIDTH_DP * mDensity);
        LinearLayout.LayoutParams text = (LinearLayout.LayoutParams) mText.getLayoutParams();
        text.width = pill ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        text.weight = pill ? 0f : 1f;
        mText.setLayoutParams(text);
        if (params.width != width) {
            params.width = width;
            mCard.setLayoutParams(params);
        }
    }

    /** Whether the card shows its × (for tests). */
    boolean closeShown() {
        return mClose.getVisibility() == VISIBLE;
    }

    /** The card stands where its hint, the bar and the layer say; set only when that moved. */
    private void place() {
        Tip tip = mTip;
        View bar = mBar;
        ViewGroup host = mHost;
        if (tip == null || bar == null || host == null || host.getWidth() <= 0) return;
        int gap = Math.round(GAP_DP * mDensity);
        int width = Math.round(WIDTH_DP * mDensity);
        int gravity;
        int leftMargin = 0;
        int bottomMargin = 0;
        int topMargin = 0;
        if (tip == Tip.HOME_HOLD) {
            gravity = Gravity.CENTER;
        } else if (tip.situational()) {
            // A situation's hint stands at the top, under Android's status bar, as the prototype's.
            gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(host);
            int status = insets == null ? 0
                : insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top;
            topMargin = status + gap;
        } else {
            int[] bl = new int[2];
            int[] hl = new int[2];
            bar.getLocationInWindow(bl);
            host.getLocationInWindow(hl);
            int left = bl[0] - hl[0];
            int top = bl[1] - hl[1];
            int right = left + bar.getWidth();
            gravity = Gravity.BOTTOM | Gravity.START;
            // The rail is read from the bar as it stands: taller than wide.
            boolean rail = bar.getHeight() > bar.getWidth();
            if (rail) {
                // Beside the rail, toward the screen's middle, its bottom level with the rail's.
                boolean railOnRight = (left + right) / 2 > host.getWidth() / 2;
                leftMargin = railOnRight ? Math.max(gap, left - gap - width)
                    : Math.min(Math.max(gap, host.getWidth() - width - gap), right + gap);
                bottomMargin = Math.max(gap, host.getHeight() - (top + bar.getHeight()));
            } else {
                // Over the bar, in the middle of it.
                leftMargin = Math.max(gap, Math.min(host.getWidth() - width - gap,
                    (left + right - width) / 2));
                bottomMargin = Math.max(gap, host.getHeight() - top + gap);
            }
        }
        LayoutParams params = (LayoutParams) mCard.getLayoutParams();
        if (params.gravity == gravity && params.leftMargin == leftMargin
            && params.bottomMargin == bottomMargin && params.topMargin == topMargin) return;
        params.gravity = gravity;
        params.leftMargin = leftMargin;
        params.bottomMargin = bottomMargin;
        params.topMargin = topMargin;
        mCard.setLayoutParams(params);
    }

    /** Takes the card off the screen. */
    public void close() {
        if (mBar != null) mBar.removeOnLayoutChangeListener(mFollow);
        if (mHost != null) mHost.removeOnLayoutChangeListener(mFollow);
        mBar = null;
        mHost = null;
        mTip = null;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        // The glass holds the blurred frame it was cut from: let go with the card.
        mCard.setBackground(null);
    }

    public boolean isOpen() {
        return getParent() != null;
    }

    static int copy(@NonNull Tip tip) {
        switch (tip) {
            case MARK_SLIDE: return R.string.tips_mark_slide;
            case HOME_HOLD: return R.string.tips_home_hold;
            case FULL_SCREEN: return R.string.tips_full_screen;
            case NAVIGATION: return R.string.tips_navigation;
            case BAR_HOLD:
            default: return R.string.tips_bar_hold;
        }
    }

    /** The glass is cut for the card's size, never from inside the layout pass. */
    private void dress() {
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0 || !isOpen()) return;
        float radius = mTip != null && mTip.situational() ? mCard.getHeight() / 2f : mStyle.cornerRadiusPx();
        Drawable glass = mStyle.glass(radius, mCard);
        if (glass == null) {
            GradientDrawable plain = new GradientDrawable();
            plain.setCornerRadius(radius);
            plain.setColor(ColorUtils.setAlphaComponent(0xFF1B1B1F,
                Math.round(com.termux.app.chrome.SheetGlass.BARE_OPACITY_FLOOR_PERCENT * 255f / 100f)));
            glass = plain;
        }
        mCard.setBackground(glass);
        mCard.setClipToOutline(true);
        mCard.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCard.post(this::dress);
    }
}
