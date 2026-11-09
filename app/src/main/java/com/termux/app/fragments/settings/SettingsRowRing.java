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

    /** Marks a page's arguments as already rung: a stack rebuilt after a restart stays quiet. */
    public static void markRung(@NonNull Bundle arguments) {
        arguments.putBoolean(ARG_RUNG, true);
    }

    /** Rings the row the page was opened on, if any and not already rung. */
    public static void ringIfAsked(@NonNull PreferenceFragmentCompat fragment) {
        Bundle arguments = fragment.getArguments();
        if (arguments == null || arguments.getBoolean(ARG_RUNG, false)) return;
        String key = arguments.getString(SettingsActivity.EXTRA_SCROLL_TO_KEY);
        androidx.preference.Preference row = key == null ? null : fragment.findPreference(key);
        // A row the page hides right now (a feature this phone lacks): the page itself is the
        // landing, with nothing to point at.
        if (row == null || !isShown(row)) return;
        arguments.putBoolean(ARG_RUNG, true);
        RecyclerView list = fragment.getListView();
        if (list == null) return;
        fragment.scrollToPreference(key);
        // Two frames: the scroll lands, then the row's holder is there to draw on.
        list.post(() -> list.post(() -> ring(fragment, list, key)));
    }

    /**
     * Whether a link's target is a section (a category) rather than a row: the ring then wraps
     * the section's rows card, never the bare header.
     */
    @VisibleForTesting
    static boolean wrapsRowsCard(@NonNull androidx.preference.Preference target) {
        return target instanceof PreferenceGroup;
    }

    /** The union of the holders on screen for the section's rows, in the list's coordinates; null if none. */
    @Nullable
    private static android.graphics.Rect rowsCardBounds(@NonNull RecyclerView list,
            @NonNull PreferenceGroup.PreferencePositionCallback callback,
            @NonNull PreferenceGroup group) {
        android.graphics.Rect bounds = null;
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            androidx.preference.Preference child = group.getPreference(i);
            if (!child.isVisible()) continue;
            int at = callback.getPreferenceAdapterPosition(child);
            RecyclerView.ViewHolder holder = at < 0 ? null : list.findViewHolderForAdapterPosition(at);
            if (holder == null) continue;
            View v = holder.itemView;
            android.graphics.Rect r = new android.graphics.Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
            if (bounds == null) bounds = r; else bounds.union(r);
        }
        return bounds;
    }

    private static boolean isShown(@NonNull androidx.preference.Preference row) {
        for (androidx.preference.Preference step = row; step != null; step = step.getParent()) {
            if (!step.isVisible()) return false;
        }
        return true;
    }

    private static void ring(@NonNull PreferenceFragmentCompat fragment, @NonNull RecyclerView list,
                             @NonNull String key) {
        if (!fragment.isAdded()) return;
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback)) return;
        PreferenceGroup.PreferencePositionCallback callback = (PreferenceGroup.PreferencePositionCallback) adapter;
        androidx.preference.Preference target = fragment.findPreference(key);
        if (target == null) return;
        View row;
        android.graphics.Rect box;
        if (wrapsRowsCard(target)) {
            // A section: the card its rows sit on, from its first row to its last on screen.
            box = rowsCardBounds(list, callback, (PreferenceGroup) target);
            if (box == null) return;
            row = list;
        } else {
            int position = callback.getPreferenceAdapterPosition(key);
            RecyclerView.ViewHolder holder = position < 0 ? null : list.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            row = holder.itemView;
            box = new android.graphics.Rect(0, 0, row.getWidth(), row.getHeight());
        }
        sLastRungKey = key;
        Context context = row.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);
        outline.setStroke(Math.round(2f * density), ThemeUtils.getSystemAttrColor(context,
            com.termux.shared.R.attr.termuxColorPrimary, android.graphics.Color.GRAY));
        outline.setCornerRadius(16f * density);
        int inset = Math.round(4f * density);
        outline.setBounds(box.left + inset, box.top + inset,
            Math.max(box.left + inset, box.right - inset),
            Math.max(box.top + inset, box.bottom - inset));
        row.getOverlay().add(outline);
        boolean still = SettingsMotion.isInstant(context);
        if (still) {
            outline.setAlpha(255);
            Runnable remove = () -> row.getOverlay().remove(outline);
            row.postDelayed(remove, HOLD_MS + 2 * FADE_MS);
            // A row recycled or detached before the hold is over takes the ring off with it.
            row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(@NonNull View v) {}
                @Override public void onViewDetachedFromWindow(@NonNull View v) {
                    v.removeCallbacks(remove);
                    remove.run();
                    v.removeOnAttachStateChangeListener(this);
                }
            });
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
