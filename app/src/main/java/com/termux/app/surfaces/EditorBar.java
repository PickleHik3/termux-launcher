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
 * <p>Both views stand in the content view and move by translation alone, so a card rising or
 * falling never lays anything out per frame. The bar stands on the card's header while the card
 * is at peek and hides while it stands higher (finishing is a pull down to peek, or Back, and
 * then Done); the half line stays, over the bar or over the card.</p>
 */
final class EditorBar {

    /** What the bar reports. */
    interface Listener {
        void onOrientation();
        void onUndo();
        void onDone();
        void onPalette();
        /** The theme hint went for good: the palette or the bubble was tapped. */
        void onThemeHintSeen();
        /** The page label under the frame (Home or Workspace) was tapped: that screen's card. */
        default void onPageLabel() {}
    }

    /** The bar's height and its items' visual height inside their 48dp targets. */
    static final int BAR_DP = 48;
    private static final int ITEM_DP = 40;
    private static final int PAD_DP = 4;
    /** The bar stands this far above the navigation inset. */
    static final int FOOT_GAP_DP = 8;
    /** The half line's dots: 6dp, the shown half's dot 16 × 6; its height is the pose's. */
    private static final int DOT_DP = 6;
    private static final int ACTIVE_DOT_DP = 16;
    /**
     * The theme hint stands this far above the half line (so it never covers the half's name), and
     * keeps this far from the screen's sides.
     */
    private static final int HINT_GAP_DP = 6;
    /** How long a passing note stays up. */
    private static final long NOTE_MS = 2500L;
    private static final int HINT_SIDE_DP = 16;

    @NonNull private final Context mContext;
    @NonNull private final LinearLayout mBar;
    @NonNull private final MaterialButton mOrientation;
    @NonNull private final MaterialButton mUndo;
    @NonNull private final MaterialButton mDone;
    @NonNull private final MaterialButton mPalette;
    @NonNull private final TextView mHint;
    private boolean mHintWanted;
    /**
     * A passing note in the theme hint's bubble (the orientation chip's "Turn the phone"), above
     * the half line so it never covers the page's name; it goes by itself.
     */
    @NonNull private final TextView mNote;
    @NonNull private final Runnable mHideNote;
    private float mBarLiftPx;
    private float mHalfLiftPx;
    /** The half line's height as last set; the hint stands above it. */
    private int mHalfRowPx;
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
        mPalette.setOnClickListener(v -> {
            dismissThemeHint();
            if (mListener != null) mListener.onPalette();
        });
        addItem(mPalette, dp(48));

        mHint = new TextView(context);
        EditorViews.textAppearance(mHint, com.google.android.material.R.attr.textAppearanceLabelLarge);
        mHint.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSecondaryContainer, 0));
        mHint.setText(R.string.appearance_theme_hint);
        mHint.setSingleLine(true);
        mHint.setEllipsize(TextUtils.TruncateAt.END);
        mHint.setGravity(Gravity.CENTER);
        mHint.setPadding(dp(14), dp(8), dp(14), dp(8));
        mHint.setMaxWidth(context.getResources().getDisplayMetrics().widthPixels
            - 2 * dp(HINT_SIDE_DP));
        GradientDrawable bubble = new GradientDrawable();
        bubble.setCornerRadius(dp(16));
        bubble.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSecondaryContainer, 0));
        mHint.setBackground(bubble);
        mHint.setElevation(dp(4));
        mHint.setVisibility(View.GONE);
        mHint.setOnClickListener(v -> {
            dismissThemeHint();
            if (mListener != null) mListener.onPalette();
        });
        mNote = bubble(context);
        mNote.setOnClickListener(v -> hideNote());
        mHideNote = this::hideNote;
        mNote.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeNote());
        mBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> alignHint());
        mHint.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> alignHint());

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
        mHalfRow.setMinimumHeight(dp(EditorFramePose.HALF_ROW_DP));
        mHalfRow.setOnClickListener(v -> { if (mListener != null) mListener.onPageLabel(); });
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
            FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hint.bottomMargin = dp(HINT_GAP_DP);
            content.addView(mHint, hint);
            FrameLayout.LayoutParams note = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            note.bottomMargin = dp(HINT_GAP_DP);
            content.addView(mNote, note);
        }
    }

    /**
     * The half line's height, as the frame's pose counts it ({@link EditorFramePose#halfRowPx}):
     * set only when it changes, so the line and the room left for it always agree.
     */
    void setHalfRowHeight(int px) {
        mHalfRowPx = px;
        ViewGroup.LayoutParams params = mHalfRow.getLayoutParams();
        if (params != null && params.height != px) {
            params.height = px;
            mHalfRow.setLayoutParams(params);
        }
    }

    void detach() {
        hideNote();
        for (View view : new View[] {mHalfRow, mBar, mHint, mNote}) {
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

    /** The page label's ink, chosen from the wallpaper it stands on ({@link EditorLabelInk}). */
    void setHalfNameInk(@androidx.annotation.ColorInt int ink) {
        mHalfName.setTextColor(ink);
    }

    /** The label back in the theme's ink, as built: no picture is known to be under it. */
    void resetHalfNameInk() {
        mHalfName.setTextColor(MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorOnSurface, 0));
    }

    /** The two inks the label chooses between: dark and pale. */
    @androidx.annotation.ColorInt int[] halfNameInks() {
        return new int[] {0xFF1B1B1F, 0xFFE3E2E6};
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
        int halfRowPx = mHalfRowPx > 0 ? mHalfRowPx : mHalfRow.getHeight();
        mHint.setTranslationY(-(Math.max(barLiftPx + dp(BAR_DP), halfLiftPx + halfRowPx)));
        mBarLiftPx = barLiftPx;
        mHalfLiftPx = halfLiftPx;
        placeNote();
    }

    /**
     * Shows {@code textRes} for a moment in the hint's bubble over the half line, where a toast
     * would have covered the page's name; a tap or the time takes it away.
     */
    void showNote(int textRes) {
        mNote.removeCallbacks(mHideNote);
        mNote.setText(textRes);
        mNote.animate().cancel();
        mNote.setAlpha(0f);
        mNote.setVisibility(View.VISIBLE);
        placeNote();
        mNote.animate().alpha(1f).setDuration(150L).start();
        mNote.postDelayed(mHideNote, NOTE_MS);
    }

    private void hideNote() {
        mNote.removeCallbacks(mHideNote);
        mNote.animate().cancel();
        mNote.setVisibility(View.GONE);
    }

    /** Over the half line and the bar, and over the theme hint while that is up. */
    private void placeNote() {
        int halfRowPx = mHalfRowPx > 0 ? mHalfRowPx : mHalfRow.getHeight();
        float lift = Math.max(mBarLiftPx + dp(BAR_DP), mHalfLiftPx + halfRowPx);
        if (mHint.getVisibility() == View.VISIBLE && mHint.getHeight() > 0)
            lift += mHint.getHeight() + dp(HINT_GAP_DP);
        mNote.setTranslationY(-lift);
    }

    /** The theme hint's bubble: label text on the secondary container, a 16 dp round. */
    @NonNull
    private TextView bubble(@NonNull Context context) {
        TextView bubble = new TextView(context);
        EditorViews.textAppearance(bubble,
            com.google.android.material.R.attr.textAppearanceLabelLarge);
        bubble.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSecondaryContainer, 0));
        bubble.setGravity(Gravity.CENTER);
        bubble.setPadding(dp(14), dp(8), dp(14), dp(8));
        bubble.setMaxWidth(context.getResources().getDisplayMetrics().widthPixels
            - 2 * dp(HINT_SIDE_DP));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSecondaryContainer, 0));
        bubble.setBackground(shape);
        bubble.setElevation(dp(4));
        bubble.setVisibility(View.GONE);
        return bubble;
    }

    /** The bar shown (at rest) or hidden (a card is up). */
    void setBarShown(boolean shown, boolean animate) {
        mHint.animate().cancel();
        mHint.setAlpha(1f);
        mHint.setVisibility(shown && mHintWanted ? View.VISIBLE : View.GONE);
        // Under the page overview the change is kept for when it closes.
        if (mBarBeforeOverview >= 0) {
            mBarBeforeOverview = shown ? View.VISIBLE : View.INVISIBLE;
            return;
        }
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

    /** The half row's visibility to restore when the page overview closes, -1 while not under it. */
    private int mHalfRowBeforeOverview = -1;
    /** The bar's visibility to restore when the page overview closes, -1 while not under it. */
    private int mBarBeforeOverview = -1;

    /**
     * The page overview covers the page, and the label and the bar stand in the activity's content
     * beside it, so under the overview they hide and come back as they were when it closes.
     */
    void setUnderOverview(boolean under) {
        if (under) {
            if (mHalfRowBeforeOverview < 0) {
                mHalfRowBeforeOverview = mHalfRow.getVisibility();
                mHalfRow.setVisibility(View.INVISIBLE);
            }
            if (mBarBeforeOverview < 0) {
                mBarBeforeOverview = mBar.getVisibility();
                mBar.animate().cancel();
                mBar.setVisibility(View.INVISIBLE);
            }
            return;
        }
        if (mHalfRowBeforeOverview >= 0) {
            mHalfRow.setVisibility(mHalfRowBeforeOverview);
            mHalfRowBeforeOverview = -1;
        }
        if (mBarBeforeOverview >= 0) {
            mBar.setVisibility(mBarBeforeOverview);
            mBarBeforeOverview = -1;
        }
    }

    void setShown(boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        // Under the overview the change is kept for when it closes.
        if (mHalfRowBeforeOverview >= 0) mHalfRowBeforeOverview = visibility;
        else if (mHalfRow.getVisibility() != visibility) mHalfRow.setVisibility(visibility);
        if (!shown && mBarBeforeOverview >= 0) mBarBeforeOverview = View.GONE;
        else if (!shown && mBar.getVisibility() != View.GONE) mBar.setVisibility(View.GONE);
        if (!shown && mHint.getVisibility() != View.GONE) mHint.setVisibility(View.GONE);
        if (!shown) hideNote();
    }

    /**
     * Whether the theme hint is owed: a bubble over the palette glyph, shown with the bar until
     * the first tap on either. It is not a setting, so nothing about it is dirty.
     */
    void setThemeHintWanted(boolean wanted) {
        mHintWanted = wanted;
        if (!wanted) mHint.setVisibility(View.GONE);
        else if (mBar.getVisibility() == View.VISIBLE) mHint.setVisibility(View.VISIBLE);
    }

    boolean isThemeHintShown() {
        return mHint.getVisibility() == View.VISIBLE;
    }

    @NonNull
    View themeHint() {
        return mHint;
    }

    private void dismissThemeHint() {
        if (!mHintWanted) return;
        setThemeHintWanted(false);
        if (mListener != null) mListener.onThemeHintSeen();
    }

    /** Centres the bubble over the palette glyph, kept inside the screen's sides. */
    private void alignHint() {
        if (mHint.getVisibility() != View.VISIBLE || !(mHint.getParent() instanceof View)) return;
        View content = (View) mHint.getParent();
        if (mPalette.getWidth() == 0 || mHint.getWidth() == 0 || content.getWidth() == 0) return;
        float palette = mBar.getLeft() + mPalette.getLeft() + mPalette.getWidth() / 2f;
        float shift = palette - content.getWidth() / 2f;
        float side = dp(HINT_SIDE_DP);
        float room = content.getWidth() / 2f - side - mHint.getWidth() / 2f;
        shift = Math.max(-room, Math.min(room, shift));
        if (mHint.getTranslationX() != shift) mHint.setTranslationX(shift);
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
