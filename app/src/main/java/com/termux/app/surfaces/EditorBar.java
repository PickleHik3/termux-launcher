package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;

/**
 * The editor bar and the half line over it (appearance-and-settings-rework §7, wireframe 01):
 * at the foot of the resting editor, a 48dp pill with the orientation chip, Undo, Done and the
 * palette glyph that opens the Appearance card; under the frame, two dots and the name of the
 * half the frame shows. Views only: what a press means is {@link Listener}'s.
 *
 * <p>Both views stand in the content view at its foot and move by translation alone, so a card
 * rising or falling never lays anything out per frame. The bar hides while a card is up (its
 * Done is the bar's, so finishing is ✕ and then Done); the half line stays, over the card.</p>
 */
final class EditorBar {

    /** What the bar reports. */
    interface Listener {
        void onOrientation();
        void onUndo();
        void onDone();
        void onPalette();
    }

    /** The bar's height and its items' visual height inside their 48dp targets. */
    static final int BAR_DP = 48;
    private static final int ITEM_DP = 40;
    private static final int PAD_DP = 4;
    /** The bar stands this far above the navigation inset. */
    static final int FOOT_GAP_DP = 8;
    /** The half line: 16dp tall, dots 6dp, the shown half's dot 16 × 6. */
    static final int HALF_ROW_DP = 16;
    private static final int DOT_DP = 6;
    private static final int ACTIVE_DOT_DP = 16;

    @NonNull private final Context mContext;
    @NonNull private final LinearLayout mBar;
    @NonNull private final MaterialButton mOrientation;
    @NonNull private final MaterialButton mUndo;
    @NonNull private final MaterialButton mDone;
    @NonNull private final MaterialButton mPalette;
    @NonNull private final LinearLayout mHalfRow;
    @NonNull private final View mHomeDot;
    @NonNull private final View mWorkspaceDot;
    @NonNull private final TextView mHalfName;
    @NonNull private final GradientDrawable mHomeDotShape = new GradientDrawable();
    @NonNull private final GradientDrawable mWorkspaceDotShape = new GradientDrawable();
    private Listener mListener;
    private boolean mLandscape;
    private WallHalf mHalf;

    EditorBar(@NonNull Context context) {
        mContext = context;
        mBar = new LinearLayout(context);
        mBar.setOrientation(LinearLayout.HORIZONTAL);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(PAD_DP);
        mBar.setPadding(pad, 0, pad, 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(BAR_DP) / 2f);
        pill.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        mBar.setBackground(pill);
        mBar.setElevation(dp(3));
        mBar.setClickable(true);

        mOrientation = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonTonalStyle);
        mOrientation.setIconResource(R.drawable.ic_symbol_phone_portrait);
        mOrientation.setSingleLine(true);
        mOrientation.setEllipsize(TextUtils.TruncateAt.END);
        mOrientation.setOnClickListener(v -> { if (mListener != null) mListener.onOrientation(); });
        addItem(mOrientation, ViewGroup.LayoutParams.WRAP_CONTENT);

        mUndo = iconButton(R.drawable.ic_symbol_undo, R.string.appearance_editor_undo);
        mUndo.setOnClickListener(v -> { if (mListener != null) mListener.onUndo(); });
        addItem(mUndo, dp(48));

        mDone = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonStyle);
        mDone.setIconResource(R.drawable.ic_symbol_check);
        mDone.setText(R.string.appearance_editor_done);
        mDone.setSingleLine(true);
        mDone.setOnClickListener(v -> { if (mListener != null) mListener.onDone(); });
        addItem(mDone, ViewGroup.LayoutParams.WRAP_CONTENT);

        mPalette = iconButton(R.drawable.ic_symbol_palette, R.string.appearance_card_appearance);
        mPalette.setOnClickListener(v -> { if (mListener != null) mListener.onPalette(); });
        addItem(mPalette, dp(48));

        mHalfRow = new LinearLayout(context);
        mHalfRow.setOrientation(LinearLayout.HORIZONTAL);
        mHalfRow.setGravity(Gravity.CENTER);
        mHalfRow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        mHomeDot = dot(mHomeDotShape);
        mWorkspaceDot = dot(mWorkspaceDotShape);
        mHalfRow.addView(mHomeDot);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(4), 1);
        mHalfRow.addView(new View(context), gap);
        mHalfRow.addView(mWorkspaceDot);
        mHalfName = new TextView(context);
        EditorViews.textAppearance(mHalfName, com.google.android.material.R.attr.textAppearanceLabelLarge);
        mHalfName.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0));
        mHalfName.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMarginStart(dp(12));
        mHalfRow.addView(mHalfName, nameParams);
        mHalfRow.setMinimumHeight(dp(HALF_ROW_DP));
        setOrientation(false);
        setHalf(WallHalf.WORKSPACE);
        setDirty(false);
    }

    void setListener(@NonNull Listener listener) {
        mListener = listener;
    }

    /** The pill, for the content view's foot. */
    @NonNull
    View bar() {
        return mBar;
    }

    /** The two dots and the half's name. */
    @NonNull
    View halfRow() {
        return mHalfRow;
    }

    /** Adds both to {@code content} at its foot, once. */
    void attach(@NonNull ViewGroup content) {
        if (mHalfRow.getParent() != content) {
            detach();
            content.addView(mHalfRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            content.addView(mBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(BAR_DP),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }
    }

    void detach() {
        for (View view : new View[] {mHalfRow, mBar}) {
            if (view.getParent() instanceof ViewGroup)
                ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    /** Undo is offered only while something changed. */
    void setDirty(boolean dirty) {
        if (mUndo.isEnabled() != dirty) {
            mUndo.setEnabled(dirty);
            mUndo.setAlpha(dirty ? 1f : 0.38f);
        }
    }

    /** The orientation the frame shows: the chip names it. */
    void setOrientation(boolean landscape) {
        mLandscape = landscape;
        mOrientation.setIconResource(landscape ? R.drawable.ic_symbol_phone_landscape
            : R.drawable.ic_symbol_phone_portrait);
        CharSequence text = mContext.getString(landscape ? R.string.appearance_bar_landscape
            : R.string.appearance_bar_portrait);
        if (!TextUtils.equals(mOrientation.getText(), text))
            mOrientation.setText(text);
    }

    boolean isLandscape() {
        return mLandscape;
    }

    /** The half the frame shows: its dot is the long one, and its name stands beside them. */
    void setHalf(@NonNull WallHalf half) {
        if (half == mHalf)
            return;
        mHalf = half;
        boolean home = half == WallHalf.HOME;
        int on = MaterialColors.getColor(mContext, androidx.appcompat.R.attr.colorPrimary, 0);
        int off = MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorOutline, 0);
        paintDot(mHomeDot, mHomeDotShape, home, on, off);
        paintDot(mWorkspaceDot, mWorkspaceDotShape, !home, on, off);
        CharSequence name = mContext.getString(home ? R.string.appearance_half_home
            : R.string.appearance_half_workspace);
        if (!TextUtils.equals(mHalfName.getText(), name))
            mHalfName.setText(name);
        mHalfRow.setContentDescription(mContext.getString(
            R.string.appearance_half_description, name));
    }

    /**
     * Where the two stand above the content's foot: the bar {@code barLiftPx} up (its bottom edge
     * over the navigation inset and the gap), the half line {@code halfLiftPx} up. By
     * translation, never a layout.
     */
    void place(float barLiftPx, float halfLiftPx) {
        mBar.setTranslationY(-barLiftPx);
        mHalfRow.setTranslationY(-halfLiftPx);
    }

    /** The bar shown (at rest) or hidden (a card is up). */
    void setBarShown(boolean shown, boolean animate) {
        mBar.animate().cancel();
        float to = shown ? 1f : 0f;
        if (shown && mBar.getVisibility() != View.VISIBLE) {
            mBar.setAlpha(animate ? 0f : 1f);
            mBar.setVisibility(View.VISIBLE);
        }
        if (!animate) {
            mBar.setAlpha(to);
            mBar.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        mBar.animate().alpha(to).setDuration(150L).withEndAction(() -> {
            if (!shown) mBar.setVisibility(View.INVISIBLE);
        }).start();
    }

    void setShown(boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (mHalfRow.getVisibility() != visibility) mHalfRow.setVisibility(visibility);
        if (!shown && mBar.getVisibility() != View.GONE) mBar.setVisibility(View.GONE);
    }

    private void paintDot(@NonNull View dot, @NonNull GradientDrawable shape, boolean active,
                          int on, int off) {
        shape.setColor(active ? on : off);
        ViewGroup.LayoutParams params = dot.getLayoutParams();
        int width = dp(active ? ACTIVE_DOT_DP : DOT_DP);
        if (params != null && params.width != width) {
            params.width = width;
            dot.setLayoutParams(params);
        }
    }

    @NonNull
    private View dot(@NonNull GradientDrawable shape) {
        View dot = new View(mContext);
        shape.setCornerRadius(dp(DOT_DP) / 2f);
        dot.setBackground(shape);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP)));
        return dot;
    }

    @NonNull
    private MaterialButton iconButton(int iconRes, int descriptionRes) {
        MaterialButton button = new MaterialButton(mContext, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(iconRes);
        button.setContentDescription(mContext.getString(descriptionRes));
        button.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorOnSurface, 0)));
        return button;
    }

    /** Each item is a 48dp target; its visible body is {@link #ITEM_DP} inside it. */
    private void addItem(@NonNull MaterialButton button, int width) {
        int inset = (dp(BAR_DP) - dp(ITEM_DP)) / 2;
        button.setInsetTop(inset);
        button.setInsetBottom(inset);
        button.setMinHeight(dp(BAR_DP));
        button.setMinimumHeight(dp(BAR_DP));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(BAR_DP));
        params.setMarginStart(mBar.getChildCount() == 0 ? 0 : dp(PAD_DP));
        mBar.addView(button, params);
    }

    private int dp(float value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
