package com.termux.app.terminal;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

/**
 * The pieces every {@link TerminalSheetController} card is built from.
 *
 * <p>They live here rather than in each caller because the sheets replaced {@code AlertDialog}, and
 * a dialog gave every prompt the same message block and the same trailing button row for free. Six
 * private copies of that layout would drift apart the first time one of them was tweaked.
 */
public final class TerminalSheetViews {

    private TerminalSheetViews() {}

    /** A vertical sheet body; the card supplies the inset, so this only stacks. */
    @NonNull
    public static LinearLayout body(@NonNull Context context) {
        LinearLayout frame = new LinearLayout(context);
        frame.setOrientation(LinearLayout.VERTICAL);
        return frame;
    }

    public static void addToFrame(@NonNull LinearLayout frame, @NonNull View child) {
        frame.addView(child, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public static void addMessage(@NonNull LinearLayout body, @NonNull CharSequence text) {
        Context context = body.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        TextView message = new TextView(context);
        message.setText(text);
        message.setTextSize(14f);
        message.setAlpha(0.85f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8 * density;
        body.addView(message, params);
    }

    /**
     * One tappable line of a menu sheet. A plain text row rather than a button: these are the
     * {@code PopupMenu} items and the list-dialog items, and four filled buttons in a column read as
     * four decisions.
     *
     * @return the row, for callers that hang a long press off it — the hint picker's copy variant.
     */
    @NonNull
    public static TextView addMenuRow(@NonNull LinearLayout body, @NonNull CharSequence label,
                                      @NonNull Runnable action) {
        Context context = body.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        TextView row = new TextView(context);
        row.setText(label);
        row.setTextSize(16f);
        row.setSingleLine(true);
        // A hint or a URL row carries a whole path; without this it is cut mid-glyph at the card
        // edge rather than marked as continuing.
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setMinHeight(48 * density);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOnClickListener(v -> action.run());
        addToFrame(body, row);
        return row;
    }

    /** The trailing row of buttons every prompt sheet ends with, laid out end-aligned. */
    @NonNull
    public static LinearLayout addActionRow(@NonNull LinearLayout body) {
        Context context = body.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = 8 * density;
        body.addView(row, params);
        return row;
    }

    public static void addAction(@NonNull LinearLayout row, @NonNull CharSequence label,
                                 @NonNull Runnable action) {
        Context context = row.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 8 * density;
        row.addView(button, params);
    }

    /** Keeps a long list — workspaces, hints, URLs — reachable on a short screen. */
    @NonNull
    public static View wrapScrolling(@NonNull View content) {
        ScrollView scroll = new ScrollView(content.getContext());
        scroll.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }
}
