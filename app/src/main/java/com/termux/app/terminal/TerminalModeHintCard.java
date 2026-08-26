package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * The card that says how to drive the mode the terminal just entered.
 *
 * <p>Copy mode and scrollback search are modal and keyboard-driven: entered from a chord, they
 * repurpose every key on the way to the shell, and nothing on screen said so beyond a three-word
 * hint at the end of the find strip. This is the legend for them — the keys that mean something
 * right now and what they do — held for as long as the mode is.
 *
 * <p>It hangs from the terminal's top-trailing corner as an extension of that surface rather than
 * as a floating panel over it: square where it meets the terminal's top edge, rounded where it
 * leaves it, following whatever margin and corner radius the user's terminal has, so it reads as a
 * tab pulled out of the terminal window. Flat and a little transparent on purpose — it sits over
 * live output which stays readable underneath, and a shadowed, opaque card here would read as a
 * dialog demanding an answer instead of as a note.
 */
public final class TerminalModeHintCard extends LinearLayout {

    /** Which mode is being explained. */
    public enum Mode {
        /** Typing a query into the find strip. */
        SEARCH(R.string.terminal_mode_hint_search_title, R.string.terminal_mode_hint_search_body),
        /** Query committed: vim's normal mode over the transcript. */
        COPY(R.string.terminal_mode_hint_copy_title, R.string.terminal_mode_hint_copy_body),
        /** Same, with a selection anchored. */
        COPY_SELECTING(R.string.terminal_mode_hint_copy_select_title,
            R.string.terminal_mode_hint_copy_select_body),
        /** The view's own text selection, from a long press or the select-at-cursor binding. */
        SELECTION(R.string.terminal_mode_hint_selection_title,
            R.string.terminal_mode_hint_selection_body);

        @StringRes final int titleRes;
        @StringRes final int bodyRes;

        Mode(@StringRes int titleRes, @StringRes int bodyRes) {
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
        }
    }

    private static final long ENTER_MS = 200L;
    private static final long EXIT_MS = 140L;
    /** Alpha the card settles at: live output has to stay readable through it. */
    private static final float REST_ALPHA = 0.94f;
    /** Flat material, so the fill carries the surface on its own. */
    private static final int SURFACE_ALPHA = 224;
    private static final int OUTLINE_ALPHA = 70;
    private static final int MAX_WIDTH_DP = 236;

    private final TextView mTitle;
    private final TextView mBody;
    private final GradientDrawable mBackground;
    @Nullable private Mode mMode;
    private float mTopCornerRadiusPx;

    public TerminalModeHintCard(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(8), dp(12), dp(9));
        setClickable(false);
        setFocusable(false);
        // Flat: no elevation, no shadow. The fill and the hairline are the whole surface.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setElevation(0f);

        mTitle = new TextView(context);
        mTitle.setAllCaps(true);
        mTitle.setSingleLine(true);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        mTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mTitle.setLetterSpacing(0.1f);
        mTitle.setTextColor(MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary)));
        addView(mTitle);

        mBody = new TextView(context);
        mBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        mBody.setLineSpacing(dp(2), 1f);
        mBody.setTextColor(MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface)));
        LayoutParams bodyParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(3);
        addView(mBody, bodyParams);

        mBackground = new GradientDrawable();
        int surface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(context, R.color.termux_surface_panel_high)));
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        mBackground.setColor(ColorUtils.setAlphaComponent(surface, SURFACE_ALPHA));
        mBackground.setStroke(Math.max(1, dp(1)),
            ColorUtils.setAlphaComponent(outline, OUTLINE_ALPHA));
        setBackground(mBackground);
        applyCornerRadii();

        setVisibility(GONE);
        setAlpha(0f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidthPx = dp(MAX_WIDTH_DP);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
            && MeasureSpec.getSize(widthMeasureSpec) > maxWidthPx) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidthPx,
                MeasureSpec.getMode(widthMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Where the terminal's own edge is, so the card can sit on it.
     *
     * @param topCornerRadiusPx the terminal's corner radius: the card's own top-trailing corner
     *     takes it, so the two arcs are one arc rather than a square poking out of a rounded one.
     */
    public void setTerminalFrame(int endMarginPx, int topMarginPx, float topCornerRadiusPx) {
        android.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) params;
            if (frameParams.getMarginEnd() != endMarginPx || frameParams.topMargin != topMarginPx) {
                frameParams.setMarginEnd(endMarginPx);
                frameParams.topMargin = topMarginPx;
                setLayoutParams(frameParams);
            }
        }
        if (mTopCornerRadiusPx != topCornerRadiusPx) {
            mTopCornerRadiusPx = topCornerRadiusPx;
            applyCornerRadii();
        }
    }

    /**
     * Square where it meets the terminal's top edge, rounded where it leaves it: the shape is what
     * makes it read as an extension of the window rather than as a card floating on top of one.
     */
    private void applyCornerRadii() {
        float bottom = dp(14);
        float topEnd = mTopCornerRadiusPx;
        // top-left, top-right, bottom-right, bottom-left (x and y per corner)
        mBackground.setCornerRadii(new float[]{
            0f, 0f,
            topEnd, topEnd,
            bottom, bottom,
            bottom, bottom});
        invalidate();
    }

    /** Shows (or swaps to) the legend for {@code mode}. */
    public void show(@NonNull Mode mode) {
        if (mMode == mode && getVisibility() == VISIBLE)
            return;
        boolean swapping = getVisibility() == VISIBLE;
        mMode = mode;
        mTitle.setText(mode.titleRes);
        mBody.setText(mode.bodyRes);
        setContentDescription(getContext().getString(mode.titleRes) + ". "
            + getContext().getString(mode.bodyRes));
        animate().cancel();
        setVisibility(VISIBLE);
        if (swapping) {
            // A mode change inside one session — search committing to copy mode — swaps the legend
            // in place. Re-playing the entrance would read as a second card arriving.
            setAlpha(REST_ALPHA);
            setTranslationY(0f);
            return;
        }
        setAlpha(0f);
        // Out from behind the terminal's top edge, which is the edge it is attached to.
        setTranslationY(-dp(10));
        animate().alpha(REST_ALPHA).translationY(0f).setDuration(ENTER_MS)
            .setInterpolator(Motion.settle()).start();
    }

    /** Takes the card back behind the terminal's edge; a no-op when it is already gone. */
    public void hide() {
        if (getVisibility() != VISIBLE) {
            mMode = null;
            return;
        }
        mMode = null;
        animate().cancel();
        animate().alpha(0f).translationY(-dp(8)).setDuration(EXIT_MS)
            .withEndAction(() -> {
                setVisibility(GONE);
                setTranslationY(0f);
            }).start();
    }

    @Nullable
    public Mode mode() {
        return mMode;
    }

    /** Top-trailing, flush with the terminal's own top edge; margins arrive with the frame. */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** The height it takes in its corner, for whatever else wants that corner. */
    public int occupancyPx() {
        return getVisibility() == View.VISIBLE ? getHeight() : 0;
    }
}
