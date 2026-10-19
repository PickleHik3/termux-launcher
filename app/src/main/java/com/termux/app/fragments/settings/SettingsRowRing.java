package com.termux.app.fragments.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.activities.SettingsActivity;
import com.termux.shared.theme.ThemeUtils;

/**
 * Land and point (F1): a page opened on a row (a search result, a link with a scroll key) scrolls
 * the row into view and rings it once — an outline in the accent that fades in, holds and fades
 * out, drawn in the row's overlay so the row's own background is never touched. One shot per
 * opening: the page coming back on Back does not ring again. Under reduced motion the ring shows
 * without fading and goes after the same hold.
 */
public final class SettingsRowRing {

    private SettingsRowRing() {}

    /** Set in the page's arguments once it has rung, so a return to the page stays quiet. */
    static final String ARG_RUNG = "settings_row_rung";

    static final long FADE_MS = 220L;
    static final long HOLD_MS = 900L;

    @VisibleForTesting
    @Nullable public static String sLastRungKey;

    /** Rings the row the page was opened on, if any and not already rung. */
    public static void ringIfAsked(@NonNull PreferenceFragmentCompat fragment) {
        Bundle arguments = fragment.getArguments();
        if (arguments == null || arguments.getBoolean(ARG_RUNG, false)) return;
        String key = arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY);
        if (key == null || fragment.findPreference(key) == null) return;
        arguments.putBoolean(ARG_RUNG, true);
        RecyclerView list = fragment.getListView();
        if (list == null) return;
        fragment.scrollToPreference(key);
        // Two frames: the scroll lands, then the row's holder is there to draw on.
        list.post(() -> list.post(() -> ring(fragment, list, key)));
    }

    private static void ring(@NonNull PreferenceFragmentCompat fragment, @NonNull RecyclerView list,
                             @NonNull String key) {
        if (!fragment.isAdded()) return;
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback)) return;
        int position = ((PreferenceGroup.PreferencePositionCallback) adapter).getPreferenceAdapterPosition(key);
        RecyclerView.ViewHolder holder = position < 0 ? null : list.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        sLastRungKey = key;
        View row = holder.itemView;
        Context context = row.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);
        outline.setStroke(Math.round(2f * density), ThemeUtils.getSystemAttrColor(context,
            com.termux.shared.R.attr.termuxColorPrimary, android.graphics.Color.GRAY));
        outline.setCornerRadius(16f * density);
        int inset = Math.round(4f * density);
        outline.setBounds(inset, inset, Math.max(inset, row.getWidth() - inset),
            Math.max(inset, row.getHeight() - inset));
        row.getOverlay().add(outline);
        boolean still = SettingsMotion.isInstant(context);
        if (still) {
            outline.setAlpha(255);
            row.postDelayed(() -> row.getOverlay().remove(outline), HOLD_MS + 2 * FADE_MS);
            return;
        }
        long fade = SettingsMotion.scaled(context, FADE_MS);
        long hold = SettingsMotion.scaled(context, HOLD_MS);
        long total = 2 * fade + hold;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(total);
        animator.addUpdateListener(a -> {
            long t = (long) (a.getAnimatedFraction() * total);
            float alpha = t < fade ? t / (float) fade : t > fade + hold ? (total - t) / (float) fade : 1f;
            outline.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
            row.invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                row.getOverlay().remove(outline);
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) {
                row.getOverlay().remove(outline);
            }
        });
        // A recycled holder must not carry the ring onto another row.
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {}
            @Override public void onViewDetachedFromWindow(@NonNull View v) {
                animator.cancel();
                v.removeOnAttachStateChangeListener(this);
            }
        });
        animator.start();
    }
}
