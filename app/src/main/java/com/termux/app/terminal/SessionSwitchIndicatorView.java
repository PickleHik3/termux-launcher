package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Color;
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

    private static final long ANIM_IN_MS = 180L;
    private static final long HOLD_MS = 1000L;
    private static final long ANIM_OUT_MS = 160L;
    /**
     * Alpha the chip settles at. Under 1 on purpose: it floats over live terminal output, and a
     * fully opaque chip reads as a dialog rather than as a note.
     */
    static final float ENTER_ALPHA = 0.92f;
    /** How far the chip slides in from the trailing edge. */
    private static final int SLIDE_DP = 10;

    private final Interpolator mEnterInterpolator;
    @Nullable private Runnable mHideRunnable;
    private boolean mVisibleState;

    public SessionSwitchIndicatorView(Context context) {
        super(context);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setMaxWidth(dp(200));
        setIncludeFontPadding(false);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int paddingH = dp(10);
        int paddingV = dp(5);
        setPadding(paddingH, paddingV, paddingH, paddingV);

        int onSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        setTextColor(onSurface);
        setBackground(buildChipBackground(onSurface));

        mEnterInterpolator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new PathInterpolator(0.16f, 1f, 0.3f, 1f)
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
            .setDuration(ANIM_OUT_MS)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                setVisibility(GONE);
                mVisibleState = false;
            })
            .start();
    }

    private GradientDrawable buildChipBackground(int onSurfaceColor) {
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 150));
        chip.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurfaceColor, 38));
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
