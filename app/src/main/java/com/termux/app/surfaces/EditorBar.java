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
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import com.termux.R;

/**
 * The editor bar and the half line over it (appearance-and-settings-rework §7, wireframe 01):
 * at the foot of the resting editor, a 48dp pill with Undo, Done and the
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
        void onUndo();
        void onDone();
        void onPalette();
        /** The theme hint went for good: the palette or the bubble was tapped. */
        void onThemeHintSeen();
        /** The page label under the frame (Home or Terminal) was tapped: that screen's card. */
        default void onPageLabel() {}
        /** A bubble of the first row (Global, Home or Workspace) was tapped. */
        default void onBubble(@NonNull EditorBubbles bubble) {}
        /** The wallpaper button was tapped: the Global card, at its top. */
        default void onWallpaper() {}
    }

    /** The bubble row over the controls: three bubbles, each a full-height target. */
    static final int BUBBLE_ROW_DP = 44;
    /** The controls row: Undo, Done and the palette glyph. */
    static final int CONTROLS_DP = 48;
    /** The bar's height (the bubble row over the controls) and its items' visual height. */
    static final int BAR_DP = BUBBLE_ROW_DP + CONTROLS_DP;
    /** The bar's corner radius: a card, not a capsule, now that it holds two rows. */
    private static final int BAR_RADIUS_DP = 24;
    private static final int BUBBLE_MIN_SP = 9;
    private static final int BUBBLE_MAX_SP = 14;
    private static final int ITEM_DP = 40;
    /** The wallpaper button's picture is drawn this far inside the item's body. */
    private static final int THUMB_DP = 28;
    /** The card the wallpaper button opens: Global, whose first row is the wallpaper. */
    static final EditorCard WALLPAPER_CARD = EditorCard.APPEARANCE;
    private static final int PAD_DP = 4;
    /** The bar stands this far above the navigation inset. */
    static final int FOOT_GAP_DP = 8;
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
    /** The bar's first row: the bubbles. */
    @NonNull private final LinearLayout mBubbleRow;
    /** The bar's second row: Undo, Done, palette. */
    @NonNull private final LinearLayout mControls;
    @NonNull private final TextView[] mBubbleViews = new TextView[EditorBubbles.values().length];
    @Nullable private EditorBubbles mMarked;
    @NonNull private final MaterialButton mUndo;
    @NonNull private final MaterialButton mDone;
    @NonNull private final MaterialButton mPalette;
    @NonNull private final MaterialButton mWallpaper;
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
    @NonNull private final TextView mHalfName;
    private Listener mListener;
    private boolean mLandscape;

    EditorBar(@NonNull Context context) {
        mContext = context;
        mBar = new LinearLayout(context);
        mBar.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(PAD_DP);
        mBar.setPadding(pad, 0, pad, 0);
        mBubbleRow = new LinearLayout(context);
        mBubbleRow.setOrientation(LinearLayout.HORIZONTAL);
        mBubbleRow.setGravity(Gravity.CENTER_VERTICAL);
        mBar.addView(mBubbleRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(BUBBLE_ROW_DP)));
        mControls = new LinearLayout(context);
        mControls.setOrientation(LinearLayout.HORIZONTAL);
        mControls.setGravity(Gravity.CENTER_VERTICAL);
        mBar.addView(mControls, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(CONTROLS_DP)));
        for (EditorBubbles bubble : EditorBubbles.values())
            mBubbleViews[bubble.ordinal()] = bubbleView(bubble);
        setBubbles(java.util.Arrays.asList(EditorBubbles.values()), null);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(BAR_RADIUS_DP));
        pill.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));
        mBar.setBackground(pill);
        mBar.setMinimumHeight(dp(BAR_DP));
        mBar.setElevation(dp(3));
        mBar.setClickable(true);

        // No orientation chip (appearance-cards overhaul): the other orientation is edited by
        // turning the phone, which needs no button.
        mUndo = iconButton(R.drawable.ic_rl_undo, R.string.appearance_editor_undo);
        mUndo.setOnClickListener(v -> { if (mListener != null) mListener.onUndo(); });
        addItem(mUndo, dp(48));

        mDone = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonStyle);
        mDone.setIconResource(R.drawable.ic_rl_check);
        mDone.setText(R.string.appearance_editor_done);
        mDone.setSingleLine(true);
        mDone.setOnClickListener(v -> { if (mListener != null) mListener.onDone(); });
        addItem(mDone, ViewGroup.LayoutParams.WRAP_CONTENT);

        mPalette = iconButton(R.drawable.ic_symbol_palette, R.string.appearance_card_customization);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(mPalette,
            context.getString(R.string.appearance_card_customization));
        mPalette.setOnClickListener(v -> {
            dismissThemeHint();
            if (mListener != null) mListener.onPalette();
        });
        addItem(mPalette, dp(48));

        mWallpaper = iconButton(R.drawable.ic_symbol_wallpaper, R.string.editor_row_wallpaper);
        androidx.appcompat.widget.TooltipCompat.setTooltipText(mWallpaper,
            context.getString(R.string.editor_row_wallpaper));
        mWallpaper.setOnClickListener(v -> { if (mListener != null) mListener.onWallpaper(); });
        addItem(mWallpaper, dp(48));

        mHint = new TextView(context);
        EditorViews.textAppearance(mHint, com.google.android.material.R.attr.textAppearanceLabelLarge);
        mHint.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSecondaryContainer, 0));
        // The bubble names the card the pill opens: Customization.
        mHint.setText(EditorCard.APPEARANCE.titleRes);
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
        mHalfName = new TextView(context);
        EditorViews.textAppearance(mHalfName, com.google.android.material.R.attr.textAppearanceLabelLarge);
        mHalfName.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, 0));
        mHalfName.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mHalfRow.addView(mHalfName, nameParams);
        mHalfRow.setMinimumHeight(dp(EditorFramePose.HALF_ROW_DP));
        mHalfRow.setOnClickListener(v -> { if (mListener != null) mListener.onPageLabel(); });
        setOrientation(false);
        setPageName(context.getString(R.string.appearance_half_terminal));
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

    /** Adds the bar's visible views to a Home exit's group. */
    void exitViews(@NonNull java.util.List<View> out) {
        if (mBar.getVisibility() == View.VISIBLE) out.add(mBar);
        if (mHalfRow.getVisibility() == View.VISIBLE) out.add(mHalfRow);
    }

    /** The line naming the page the editor shows (ADR 0015 §6: no halves to switch between). */
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

    /** The orientation the frame shows (the bar no longer names it). */
    void setOrientation(boolean landscape) {
        mLandscape = landscape;
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

    /** The page the editor shows: its name on the line over the frame. */
    void setPageName(@NonNull CharSequence name) {
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
        mBarLiftPx = barLiftPx;
        applyBarTranslation();
        mHalfRow.setTranslationY(-halfLiftPx);
        int halfRowPx = mHalfRowPx > 0 ? mHalfRowPx : mHalfRow.getHeight();
        mHint.setTranslationY(-(Math.max(barLiftPx + dp(BAR_DP), halfLiftPx + halfRowPx)));
        mHalfLiftPx = halfLiftPx;
        placeNote();
    }

    /** How far above its place the bar stands while it drops in; 0 at rest. */
    private float mDropPx;
    @Nullable private android.animation.ValueAnimator mDropAnimator;

    /** The bar's translation: its lift over the foot, plus the drop it is still coming through. */
    private void applyBarTranslation() {
        float to = -mBarLiftPx - mDropPx;
        if (mBar.getTranslationY() != to)
            mBar.setTranslationY(to);
    }

    /** The bar's translation now, for the tests. */
    float barTranslationY() {
        return mBar.getTranslationY();
    }

    private void setDropPx(float dropPx) {
        mDropPx = dropPx;
        applyBarTranslation();
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

    /** How far the bar drops into place as it comes in. */
    static final int BAR_DROP_DP = 8;

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
        if (mDropAnimator != null)
            mDropAnimator.cancel();
        setDropPx(0f);
        float to = shown ? 1f : 0f;
        boolean arriving = shown && mBar.getVisibility() != View.VISIBLE;
        if (arriving) {
            mBar.setAlpha(animate ? 0f : 1f);
            mBar.setVisibility(View.VISIBLE);
        }
        if (!animate) {
            mBar.setAlpha(to);
            mBar.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
            return;
        }
        boolean reduced = com.termux.app.ReducedMotion.isEnabled(mBar.getContext());
        // Coming in it fades in (effects-default) dropping 8dp into place (BAR_DROP); going it
        // fades out on effects-fast.
        EditorMotion.Use fade = shown ? EditorMotion.Use.REVEAL_IN : EditorMotion.Use.REVEAL_OUT;
        if (arriving && !reduced) {
            // The drop is its own offset over the lift, so place() and the drop never undo each
            // other: the bar ends at its lift, not at the foot.
            float from = BAR_DROP_DP * mBar.getResources().getDisplayMetrics().density;
            setDropPx(from);
            android.animation.ValueAnimator drop = android.animation.ValueAnimator.ofFloat(from, 0f);
            drop.addUpdateListener(a -> setDropPx((Float) a.getAnimatedValue()));
            mDropAnimator = drop;
            drop.setDuration(EditorMotion.Use.BAR_DROP.durationMs(false));
            drop.setInterpolator(EditorMotion.Use.BAR_DROP.curve(false));
            drop.start();
        }
        mBar.animate().alpha(to).setDuration(fade.durationMs(reduced))
            .setInterpolator(fade.curve(reduced)).withEndAction(() -> {
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
        float palette = mBar.getLeft() + mControls.getLeft() + mPalette.getLeft()
            + mPalette.getWidth() / 2f;
        float shift = palette - content.getWidth() / 2f;
        float side = dp(HINT_SIDE_DP);
        float room = content.getWidth() / 2f - side - mHint.getWidth() / 2f;
        shift = Math.max(-room, Math.min(room, shift));
        if (mHint.getTranslationX() != shift) mHint.setTranslationX(shift);
    }

    @NonNull
    /** The pixel size of the wallpaper button's picture at {@code density}. */
    static int wallpaperThumbPx(float density) {
        return Math.max(1, Math.round(THUMB_DP * density));
    }

    /**
     * Shows {@code thumb} (made at {@link #wallpaperThumbPx}) on the wallpaper button, or the
     * wallpaper glyph for null. The bar does not own the bitmap.
     */
    void setWallpaperThumb(@Nullable android.graphics.Bitmap thumb) {
        if (thumb == null) {
            mWallpaper.setIconResource(R.drawable.ic_symbol_wallpaper);
            mWallpaper.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(mContext,
                com.google.android.material.R.attr.colorOnSurface, 0)));
            mWallpaper.setIconSize(dp(24));
            return;
        }
        mWallpaper.setIconTint(null);
        mWallpaper.setIconSize(thumb.getWidth());
        mWallpaper.setIcon(new android.graphics.drawable.BitmapDrawable(mContext.getResources(),
            thumb));
    }

    /** The wallpaper button, for tests. */
    @NonNull
    MaterialButton wallpaperButton() {
        return mWallpaper;
    }

    private MaterialButton iconButton(int iconRes, int descriptionRes) {
        MaterialButton button = new MaterialButton(mContext, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(iconRes);
        button.setContentDescription(mContext.getString(descriptionRes));
        button.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorOnSurface, 0)));
        return button;
    }

    /**
     * Offers {@code bubbles} in the first row and marks {@code marked} (null for none). Changes
     * only what differs, so a card opening never lays the bar out again for nothing.
     */
    void setBubbles(@NonNull java.util.List<EditorBubbles> bubbles, @Nullable EditorBubbles marked) {
        for (EditorBubbles bubble : EditorBubbles.values()) {
            TextView view = mBubbleViews[bubble.ordinal()];
            boolean offered = bubbles.contains(bubble);
            if (offered && view.getParent() == null) {
                // Keep the declared order whichever bubbles stand.
                int at = 0;
                for (EditorBubbles before : EditorBubbles.values()) {
                    if (before == bubble) break;
                    if (mBubbleViews[before.ordinal()].getParent() != null) at++;
                }
                mBubbleRow.addView(view, at);
            } else if (!offered && view.getParent() != null) {
                mBubbleRow.removeView(view);
            }
        }
        setMarked(marked);
    }

    /** The bubble of the card on show is marked; null marks none (a bar card is up). */
    void setMarked(@Nullable EditorBubbles marked) {
        mMarked = marked;
        for (EditorBubbles bubble : EditorBubbles.values())
            paintBubble(bubble, bubble == marked);
    }

    @Nullable
    EditorBubbles markedBubble() {
        return mMarked;
    }

    /** The bubble's view, for the tests. */
    @NonNull
    TextView bubbleView(@NonNull EditorBubbles bubble) {
        TextView view = mBubbleViews[bubble.ordinal()];
        if (view != null) return view;
        view = new TextView(mContext);
        EditorViews.textAppearance(view, com.google.android.material.R.attr.textAppearanceLabelLarge);
        view.setText(bubble.labelRes);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(view,
            BUBBLE_MIN_SP, BUBBLE_MAX_SP, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        view.setClickable(true);
        view.setOnClickListener(v -> {
            if (mListener != null) mListener.onBubble(bubble);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        view.setLayoutParams(params);
        return view;
    }

    private void paintBubble(@NonNull EditorBubbles bubble, boolean marked) {
        TextView view = mBubbleViews[bubble.ordinal()];
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(16));
        shape.setColor(marked ? MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorSecondaryContainer, 0) : 0);
        int vertical = dp((BUBBLE_ROW_DP - ITEM_DP) / 2f + 2);
        view.setBackground(new android.graphics.drawable.InsetDrawable(shape, 0, vertical, 0, vertical));
        view.setTextColor(MaterialColors.getColor(mContext, marked
            ? com.google.android.material.R.attr.colorOnSecondaryContainer
            : com.google.android.material.R.attr.colorOnSurface, 0));
        view.setSelected(marked);
        view.setContentDescription(mContext.getString(bubble.labelRes));
    }

    /** Each item is a 48dp target; its visible body is {@link #ITEM_DP} inside it. */
    private void addItem(@NonNull MaterialButton button, int width) {
        int inset = (dp(CONTROLS_DP) - dp(ITEM_DP)) / 2;
        button.setInsetTop(inset);
        button.setInsetBottom(inset);
        button.setMinHeight(dp(CONTROLS_DP));
        button.setMinimumHeight(dp(CONTROLS_DP));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(CONTROLS_DP));
        params.setMarginStart(mControls.getChildCount() == 0 ? 0 : dp(PAD_DP));
        mControls.addView(button, params);
    }

    private int dp(float value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
