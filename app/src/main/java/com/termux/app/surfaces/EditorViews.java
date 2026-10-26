package com.termux.app.surfaces;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/** Small view helpers the editor bar and the cards share. */
final class EditorViews {

    private EditorViews() {}

    static int dp(@NonNull Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A colour role of {@code context}'s theme. */
    static int color(@NonNull Context context, @AttrRes int attr) {
        return MaterialColors.getColor(context, attr, 0);
    }

    /** Gives a text view the theme's text appearance behind {@code attr}. */
    static void textAppearance(@NonNull TextView view, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(attr, value, true)
            && value.resourceId != 0)
            view.setTextAppearance(value.resourceId);
    }

    /** Sets {@code text} only when it differs, so a restatement never asks for a layout. */
    static void setText(@NonNull TextView view, @Nullable CharSequence text) {
        CharSequence next = text == null ? "" : text;
        if (!TextUtils.equals(view.getText(), next))
            view.setText(next);
    }

    /** Sets a visibility only when it differs. */
    static void setVisible(@NonNull android.view.View view, boolean visible) {
        int next = visible ? android.view.View.VISIBLE : android.view.View.GONE;
        if (view.getVisibility() != next)
            view.setVisibility(next);
    }
}
