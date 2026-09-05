package com.termux.app.x11;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;

import java.util.List;

/**
 * The Display place's menu: a short strip of actions at the bottom edge of the terminal area,
 * hugging the leading side, where the find strip and the other bottom-edge chrome appear. It
 * takes only the room its words need and leaves the display alone; it enters and leaves the way
 * the find strip does, and a tap anywhere else puts it away.
 */
public final class DisplayActionStripView extends LinearLayout {

    /** One row of the strip. */
    public static final class Action {
        @NonNull public final CharSequence label;
        @NonNull public final Runnable run;

        public Action(@NonNull CharSequence label, @NonNull Runnable run) {
            this.label = label;
            this.run = run;
        }
    }

    private static final long ENTER_DURATION_MS = 160L;
    private static final long EXIT_DURATION_MS = 100L;

    private final int mTextColor;
    private final int mDividerColor;
    private boolean mLeaving;

    public DisplayActionStripView(@NonNull Context context, @NonNull Drawable background,
                                  int textColor, int accentColor) {
        super(context);
        mTextColor = textColor;
        mDividerColor = ColorUtils.setAlphaComponent(accentColor, 64);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(background);
        setClickable(true);
        setFocusable(false);
        setElevation(dp(3));
        setPadding(dp(6), 0, dp(6), 0);
        setMinimumHeight(dp(40));
        // A pill, whatever shape the glass was cut for elsewhere.
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        setClipToOutline(true);
    }

    /** Fill the strip with its actions; a tap runs one and puts the strip away. */
    public void bind(@NonNull List<Action> actions, @NonNull Runnable onDone) {
        removeAllViews();
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (i > 0) {
                View divider = new View(getContext());
                divider.setBackgroundColor(mDividerColor);
                LayoutParams params = new LayoutParams(dp(1), dp(16));
                params.setMarginStart(dp(2));
                params.setMarginEnd(dp(2));
                addView(divider, params);
            }
            AppCompatTextView row = new AppCompatTextView(getContext());
            row.setText(action.label);
            row.setTextColor(mTextColor);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            row.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            row.setSingleLine(true);
            row.setGravity(Gravity.CENTER);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                onDone.run();
                action.run.run();
            });
            addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    /** Put the strip into {@code host} at its leading bottom corner and let it rise in. */
    public void enter(@NonNull ViewGroup host, boolean reducedMotion) {
        mLeaving = false;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START | Gravity.CENTER_VERTICAL);
        params.setMarginStart(dp(8));
        params.topMargin = dp(6);
        params.bottomMargin = dp(6);
        host.addView(this, params);
        host.setVisibility(VISIBLE);
        animate().cancel();
        if (reducedMotion) {
            setAlpha(1f);
            setTranslationY(0f);
            return;
        }
        setAlpha(0f);
        setTranslationY(dp(10));
        animate().alpha(1f).translationY(0f).setDuration(ENTER_DURATION_MS).start();
    }

    /** Fade out and leave the host, hiding it when nothing else is inside. */
    public void leave(boolean reducedMotion) {
        if (mLeaving) return;
        mLeaving = true;
        ViewGroup host = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        Runnable detach = () -> {
            if (host != null) {
                host.removeView(this);
                if (host.getChildCount() == 0) host.setVisibility(GONE);
            }
            setAlpha(1f);
            setTranslationY(0f);
            mLeaving = false;
        };
        animate().cancel();
        if (reducedMotion) {
            detach.run();
            return;
        }
        animate().alpha(0f).translationY(dp(6)).setDuration(EXIT_DURATION_MS)
            .withEndAction(detach).start();
    }

    public boolean isShowing() {
        return getParent() != null && !mLeaving;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
