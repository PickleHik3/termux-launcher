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

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

/**
 * Transient fork-styled chip shown centered near the top of the terminal surface for session
 * switch / title-change / session-exit notices, replacing the stock Android {@code Toast} for
 * those events. Fades and slides in, holds briefly, then fades out. A new message that arrives
 * while the chip is already showing just swaps the text and restarts the hold timer instead of
 * replaying the entrance.
 */
public final class SessionSwitchIndicatorView extends AppCompatTextView {

    private static final long ANIM_IN_MS = 180L;
    private static final long HOLD_MS = 1200L;
    private static final long ANIM_OUT_MS = 160L;

    private final Interpolator mEnterInterpolator;
    @Nullable private Runnable mHideRunnable;
    private boolean mVisibleState;

    public SessionSwitchIndicatorView(Context context) {
        super(context);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setMaxWidth(dp(260));
        setIncludeFontPadding(false);
        setGravity(Gravity.CENTER);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        int paddingH = dp(12);
        int paddingV = dp(6);
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
            // Already showing (or fading out) - swap text in place and restart the hold, no
            // need to replay the slide/fade entrance.
            animate().cancel();
            setAlpha(1f);
            setTranslationY(0f);
            scheduleHide();
            return;
        }
        mVisibleState = true;
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(-dp(8));
        animate()
            .alpha(1f)
            .translationY(0f)
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
        chip.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 168));
        chip.setStroke(dp(1), ColorUtils.setAlphaComponent(onSurfaceColor, 46));
        return chip;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
