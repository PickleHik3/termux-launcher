package com.termux.app.theme;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The shared Text size (Appearance › Base) as the chrome reads it: one factor on the labels the
 * launcher draws itself, the status bar's figures, the window chips, the dock strip's titles and
 * Home's app labels. Each keeps its own size in sp and draws at {@link #sp} of it, so a size a
 * surface has of its own (Home's label size) stays an offset from the shared one.
 *
 * <p>The terminal and the keyboard take the same stored value through their own hosts
 * ({@link TermuxAppSharedPreferences#textScaledFontSize}, the keyboard's legend scale); this holder
 * exists because chrome views are built in many places that have no preferences to hand.
 *
 * <p>The activity calls {@link #sync} once its preferences are loaded and again whenever the
 * stored value may have moved, and {@link #dispatch} only when it did: a view built later reads the
 * current factor as it is made, and a view already on screen re-applies its size through
 * {@link Follower}. Followers compare before they set, so an unchanged size asks for no layout.
 */
public final class TextScale {

    /** A view drawing text at a shared-scaled size, re-applied when the factor moves. */
    public interface Follower {
        void onTextScaleChanged();
    }

    private static float sScale = 1f;

    private TextScale() {}

    /** The current factor; 1 until the preferences say otherwise. */
    public static float get() {
        return sScale;
    }

    /** {@code sp} at the shared Text size. */
    public static float sp(float sp) {
        return sp * sScale;
    }

    /** Takes the stored Text size; true when the factor moved. */
    public static boolean sync(@Nullable TermuxAppSharedPreferences preferences) {
        if (preferences == null)
            return false;
        return set(preferences.getTextSizeScale());
    }

    /** Sets the factor; true when it moved. */
    public static boolean set(float scale) {
        if (!(scale > 0f) || Float.compare(sScale, scale) == 0)
            return false;
        sScale = scale;
        return true;
    }

    /** Tells every {@link Follower} at or under {@code root} that the factor moved. */
    public static void dispatch(@Nullable View root) {
        if (root == null)
            return;
        if (root instanceof Follower)
            ((Follower) root).onTextScaleChanged();
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                dispatch(group.getChildAt(i));
        }
    }

    /** Sets {@code view}'s size to {@code sp} at the shared Text size, if it is not there yet. */
    public static void apply(@NonNull TextView view, float sp) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            sp(sp), view.getResources().getDisplayMetrics());
        if (view.getTextSize() != px)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(sp));
    }
}
