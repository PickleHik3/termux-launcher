package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

/**
 * An action row: a row that does one thing in place, named by a word at its end. The Match rows
 * are one (Copy, which reads Undo while the copy can still be taken back), and the Wallpaper
 * row's Change will be another. A tap anywhere on the row does what the word says.
 */
public final class ActionRow {

    @NonNull public final LinearLayout root;
    @NonNull public final TextView title;
    @NonNull public final MaterialButton action;

    private ActionRow(@NonNull LinearLayout root, @NonNull TextView title,
                      @NonNull MaterialButton action) {
        this.root = root;
        this.title = title;
        this.action = action;
    }

    /** Builds the row; {@code onAction} hears a tap on the word or anywhere on the row. */
    @NonNull
    public static ActionRow build(@NonNull Context context, @DrawableRes int glyph,
                                  @NonNull CharSequence title, @Nullable CharSequence action,
                                  @NonNull View.OnClickListener onAction) {
        LinearLayout root = RowKit.line(context);
        root.addView(RowKit.glyph(context, glyph, title), RowKit.glyphParams(context));
        TextView titleView = RowKit.title(context, title);
        root.addView(titleView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton button = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setMaxLines(1);
        if (action != null)
            button.setText(action);
        button.setOnClickListener(onAction);
        root.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            RowKit.dp(context, RowKit.TOUCH_DP)));
        root.setOnClickListener(onAction);
        return new ActionRow(root, titleView, button);
    }

    /** The word at the row's end ("Copy", "Undo"); set only when it differs. */
    public void setAction(@Nullable CharSequence action) {
        RowKit.setText(this.action, action);
    }
}
