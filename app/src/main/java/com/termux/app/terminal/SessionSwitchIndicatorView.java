package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Transient fork-styled chip pinned to the top-trailing corner of the terminal surface, replacing
 * the stock Android {@code Toast} for every notice the terminal raises about itself: session
 * switches, title changes, session exits, window and pane positions, and the "cannot do that"
 * refusals. Fades and slides in from the trailing edge, holds briefly, then fades out. A message
 * that arrives while the chip is already showing swaps the text and restarts the hold timer instead
 * of replaying the entrance.
 *
 * <p>Top-trailing rather than top-centre: centred, it sat over the text the notice is usually about,
 * and it is deliberately quieter than a toast — these events are frequent, so the chip has to be
 * glanceable without being an interruption.
 */
public final class SessionSwitchIndicatorView extends AppCompatTextView {

    /** Material 3 durations: medium-2 in, short-4 out. */
    private static final long ANIM_IN_MS = 300L;
    private static final long HOLD_MS = 1400L;
    private static final long ANIM_OUT_MS = 200L;
    /**
     * Alpha the chip settles at. Under 1 on purpose: it floats over live terminal output, and a
     * fully opaque chip reads as a dialog rather than as a note.
     */
    static final float ENTER_ALPHA = 0.96f;
    /** How far the chip slides in from the trailing edge. */
    private static final int SLIDE_DP = 12;
    /** Material 3 surface-container elevation and its outline, as alpha over live terminal output. */
    private static final int SURFACE_ALPHA = 235;
    private static final int OUTLINE_ALPHA = 92;

    /**
     * How much vertical room the chip is taking in the top-trailing corner, so whatever stacks below
     * it can sit directly underneath and slide up when the chip goes rather than reserve a band.
     */
    public interface OccupancyListener {
        /** Called with the chip's current height, or 0 once it is gone. */
        void onIndicatorOccupancyChanged(int heightPx);
    }

    private final Interpolator mEnterInterpolator;
    @Nullable private Runnable mHideRunnable;
    @Nullable private OccupancyListener mOccupancyListener;
    private int mReportedHeightPx = -1;
    private boolean mVisibleState;

    public SessionSwitchIndicatorView(Context context) {
        super(context);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setMaxWidth(dp(220));
        setIncludeFontPadding(false);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        // Material 3 label-medium: 12sp, medium weight, 0.5sp tracking. The old 9.5sp was below the
        // type scale's floor, which is why the notice read as fine print rather than as a component.
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setLetterSpacing(0.042f);
        setPadding(dp(14), dp(7), dp(14), dp(7));

        int onSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        setTextColor(onSurface);
        setBackground(buildChipBackground(context));
        // Material elevation, so the notice sits above terminal output as a surface rather than as
        // text that happens to have a box. Kept low: level 2, not a dialog.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setElevation(dp(3));

        // Emphasized decelerate: Material's curve for something entering and staying put.
        mEnterInterpolator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            : new DecelerateInterpolator(1.8f);

        setVisibility(GONE);
        setAlpha(0f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0 && getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) getBackground()).setCornerRadius(h / 2f);
        }
        // The first show() runs before the chip has ever been measured, so its height is only
        // knowable here. Re-reported on every resize: a longer notice can wrap the text taller.
        notifyOccupancy();
    }

    public void setOccupancyListener(@Nullable OccupancyListener listener) {
        mOccupancyListener = listener;
        mReportedHeightPx = -1;
        notifyOccupancy();
    }

    private void notifyOccupancy() {
        if (mOccupancyListener == null) return;
        int height = getVisibility() == VISIBLE ? getHeight() : 0;
        if (height == mReportedHeightPx) return;
        mReportedHeightPx = height;
        mOccupancyListener.onIndicatorOccupancyChanged(height);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mHideRunnable != null) {
            removeCallbacks(mHideRunnable);
            mHideRunnable = null;
        }
        animate().cancel();
        super.onDetachedFromWindow();
    }

    /** Show (or update, if already showing) the chip with {@code text} and (re)start the hold timer. */
    public void show(@Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) return;
        setText(text);
        if (mHideRunnable != null) {
            removeCallbacks(mHideRunnable);
            mHideRunnable = null;
        }
        if (mVisibleState) {
            // Already showing (or fading out): swap text in place and restart the hold rather than
            // replay the entrance. Both transforms are reset here too — a chip updated mid-fade
            // would otherwise settle at full opacity with a stale slide offset still applied.
            animate().cancel();
            setAlpha(ENTER_ALPHA);
            setTranslationX(0f);
            scheduleHide();
            return;
        }
        mVisibleState = true;
        animate().cancel();
        setVisibility(VISIBLE);
        notifyOccupancy();
        setAlpha(0f);
        // One axis of truth: the chip is pinned to the trailing edge, so it enters along it.
        setTranslationX(dp(SLIDE_DP));
        animate()
            .alpha(ENTER_ALPHA)
            .translationX(0f)
            .setDuration(ANIM_IN_MS)
            .setInterpolator(mEnterInterpolator)
            .withEndAction(this::scheduleHide)
            .start();
    }

    /** Hide immediately, cancelling any pending animation/timer. */
    public void cancel() {
        if (mHideRunnable != null) {
            removeCallbacks(mHideRunnable);
            mHideRunnable = null;
        }
        animate().cancel();
        setVisibility(GONE);
        setAlpha(0f);
        setTranslationX(0f);
        mVisibleState = false;
        notifyOccupancy();
    }

    private void scheduleHide() {
        mHideRunnable = this::fadeOut;
        postDelayed(mHideRunnable, HOLD_MS);
    }

    private void fadeOut() {
        mHideRunnable = null;
        animate().cancel();
        animate()
            .alpha(0f)
            .translationX(dp(SLIDE_DP) * 0.5f)
            .setDuration(ANIM_OUT_MS)
            .setInterpolator(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? new PathInterpolator(0.3f, 0f, 1f, 1f) : new DecelerateInterpolator())
            .withEndAction(() -> {
                setVisibility(GONE);
                mVisibleState = false;
                notifyOccupancy();
            })
            .start();
    }

    /**
     * Material surface-container-high fill with an outline-variant hairline, rather than the black
     * scrim this used to be: the chip now belongs to the same surface family as the status card and
     * the session panel, and it follows the wallpaper-derived palette with them.
     */
    private GradientDrawable buildChipBackground(@NonNull Context context) {
        int surface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                ContextCompat.getColor(context, R.color.termux_surface_panel_high)));
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(ColorUtils.setAlphaComponent(surface, SURFACE_ALPHA));
        chip.setStroke(dp(1), ColorUtils.setAlphaComponent(outline, OUTLINE_ALPHA));
        return chip;
    }

    /**
     * Where the chip sits in its host. Position and entry animation have to agree — the slide comes
     * in along the edge the chip is pinned to — so they belong in the same place.
     */
    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.END;
        float density = context.getResources().getDisplayMetrics().density;
        params.topMargin = Math.round(8 * density);
        params.setMarginEnd(Math.round(10 * density));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
