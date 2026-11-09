package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.transition.Hold;
import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.termux.shared.theme.ThemeUtils;

/**
 * Puts a {@link SettingsMotionPolicy.Kind} on the two fragments of a settings move, as Material
 * motion (androidx Transition, run by the FragmentManager): the leaving fragment's exit and
 * re-enter, the arriving one's enter and return, so Back plays the move in reverse. All one-shot;
 * {@link SettingsMotionPolicy.Kind#NONE} clears them, which is the instant cut.
 */
public final class SettingsTransitions {

    private SettingsTransitions() {}

    /** Material's own durations for these patterns, before the animation-speed scale. */
    static final long DURATION_MS = 300L;

    /** The arriving editor's argument naming the shared element it grows from. */
    public static final String ARG_CONTAINER_NAME = "settings_container_transition_name";

    /** Applies {@code kind} to a move from {@code out} (may be null) to {@code in}. */
    public static void apply(@NonNull Context context, @NonNull SettingsMotionPolicy.Kind kind,
                             @Nullable Fragment out, @NonNull Fragment in,
                             @NonNull FragmentTransaction transaction, @Nullable View fromRow) {
        long duration = SettingsMotion.scaled(context, DURATION_MS);
        switch (kind) {
            case SHARED_AXIS:
                set(out, axis(true, duration), axis(false, duration));
                in.setEnterTransition(axis(true, duration));
                in.setReturnTransition(axis(false, duration));
                break;
            case FADE_THROUGH:
                set(out, fade(duration), fade(duration));
                in.setEnterTransition(fade(duration));
                in.setReturnTransition(fade(duration));
                break;
            case CONTAINER:
                if (fromRow == null) {
                    apply(context, SettingsMotionPolicy.Kind.SHARED_AXIS, out, in, transaction, null);
                    return;
                }
                String name = "settings_editor_" + SystemClock.uptimeMillis();
                ViewCompat.setTransitionName(fromRow, name);
                transaction.addSharedElement(fromRow, name);
                Bundle arguments = in.getArguments();
                if (arguments == null) {
                    arguments = new Bundle();
                    in.setArguments(arguments);
                }
                arguments.putString(ARG_CONTAINER_NAME, name);
                MaterialContainerTransform grow = new MaterialContainerTransform();
                grow.setDrawingViewId(com.termux.R.id.settings);
                grow.setScrimColor(Color.TRANSPARENT);
                grow.setAllContainerColors(ThemeUtils.getSystemAttrColor(context,
                    com.termux.shared.R.attr.termuxColorSurfaceBase, Color.BLACK));
                grow.setDuration(duration);
                // The row is a recycled list view: once the move is over it must not carry the
                // name into a later move.
                View row = fromRow;
                grow.addListener(new androidx.transition.TransitionListenerAdapter() {
                    @Override public void onTransitionEnd(@NonNull androidx.transition.Transition transition) {
                        ViewCompat.setTransitionName(row, null);
                    }
                    @Override public void onTransitionCancel(@NonNull androidx.transition.Transition transition) {
                        ViewCompat.setTransitionName(row, null);
                    }
                });
                in.setSharedElementEnterTransition(grow);
                // Back fades the editor away over the page it came from, which waits beneath:
                // the row it grew from is a new view by then, so there is nothing to shrink into.
                in.setSharedElementReturnTransition(null);
                in.setEnterTransition(null);
                in.setReturnTransition(fade(duration));
                set(out, hold(duration), hold(duration));
                break;
            case NONE:
            default:
                set(out, null, null);
                in.setEnterTransition(null);
                in.setReturnTransition(null);
                in.setSharedElementEnterTransition(null);
                break;
        }
    }

    /**
     * The fragment on show is leaving by a jump: whether it is replaced or popped, it fades
     * through (or cuts, for {@code instant}).
     */
    public static void leaveByJump(@NonNull Context context, @NonNull Fragment out, boolean instant) {
        long duration = SettingsMotion.scaled(context, DURATION_MS);
        out.setExitTransition(instant ? null : fade(duration));
        out.setReturnTransition(instant ? null : fade(duration));
        out.setSharedElementReturnTransition(null);
    }

    /** The fragment a jump lands on fades through in (or cuts). */
    public static void arriveByJump(@NonNull Context context, @NonNull Fragment in, boolean instant) {
        in.setEnterTransition(instant ? null : fade(SettingsMotion.scaled(context, DURATION_MS)));
        in.setSharedElementEnterTransition(null);
    }

    /** Names an arriving editor's root view for the container transform, once its view exists. */
    public static void nameContainer(@NonNull Fragment fragment, @NonNull View view) {
        Bundle arguments = fragment.getArguments();
        String name = arguments == null ? null : arguments.getString(ARG_CONTAINER_NAME);
        if (name != null) ViewCompat.setTransitionName(view, name);
    }

    /** The view of {@code row} on {@code page}'s list, when it is on screen. */
    @Nullable
    public static View rowView(@NonNull PreferenceFragmentCompat page, @NonNull Preference row) {
        RecyclerView list = page.getListView();
        if (list == null) return null;
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback)) return null;
        int position = ((PreferenceGroup.PreferencePositionCallback) adapter).getPreferenceAdapterPosition(row);
        RecyclerView.ViewHolder holder = position < 0 ? null : list.findViewHolderForAdapterPosition(position);
        return holder == null ? null : holder.itemView;
    }

    private static void set(@Nullable Fragment out, @Nullable Object exit, @Nullable Object reenter) {
        if (out == null) return;
        out.setExitTransition(exit);
        out.setReenterTransition(reenter);
    }

    @NonNull
    private static MaterialSharedAxis axis(boolean forward, long duration) {
        MaterialSharedAxis axis = new MaterialSharedAxis(MaterialSharedAxis.X, forward);
        axis.setDuration(duration);
        return axis;
    }

    @NonNull
    private static MaterialFadeThrough fade(long duration) {
        MaterialFadeThrough fade = new MaterialFadeThrough();
        fade.setDuration(duration);
        return fade;
    }

    @NonNull
    private static Hold hold(long duration) {
        Hold hold = new Hold();
        hold.setDuration(duration);
        return hold;
    }
}
