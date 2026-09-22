package com.termux.app.terminal;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

/**
 * The pieces every {@link TerminalSheetController} card is built from.
 *
 * <p>They live here rather than in each caller because the sheets replaced {@code AlertDialog}, and
 * a dialog gave every prompt the same message block and the same trailing button row for free. Six
 * private copies of that layout would drift apart the first time one of them was tweaked.
 */
public final class TerminalSheetViews {

    /**
     * Heading size, shared by the card's own heading and the heading row. Smaller than a dialog
     * title: a panel on the terminal's foot is competing with the terminal for the same rows.
     */
    public static final float HEADING_TEXT_SIZE_SP = 16f;

    private TerminalSheetViews() {}

    /** The two answers a prompt's heading row carries. */
    private static final String CONFIRM_GLYPH = "\u2713";   // ✓
    private static final String CANCEL_GLYPH = "\u2715";    // ✕

    /**
     * A card's heading with its answers on the same line.
     *
     * <p>These panels are strips across the terminal's foot, not dialogs in the middle of the
     * screen. A title row of its own plus a trailing row of buttons spent two of the four or five
     * rows such a strip has, and both said what the panel already says: a tick commits, a cross
     * closes, and they sit where the eye lands as the panel arrives.
     *
     * <p>Always the first row of the body, wherever the caller adds it — including the browser,
     * whose body is inflated from a layout.
     *
     * @param confirm the tick, or null for a panel with nothing to confirm (a list, a menu).
     * @param confirmDescription what the tick does, for a screen reader; null means "OK".
     * @param cancel  the cross, or null for a panel that is only closed by tapping outside it.
     */
    @NonNull
    public static LinearLayout addHeaderRow(@NonNull LinearLayout body, @NonNull CharSequence title,
                                            @Nullable Runnable confirm,
                                            @Nullable CharSequence confirmDescription,
                                            @Nullable Runnable cancel) {
        Context context = body.getContext();
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(HEADING_TEXT_SIZE_SP);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (cancel != null) {
            row.addView(glyphButton(context, CANCEL_GLYPH,
                context.getString(android.R.string.cancel), false, cancel));
        }
        if (confirm != null) {
            row.addView(glyphButton(context, CONFIRM_GLYPH,
                confirmDescription == null ? context.getString(android.R.string.ok)
                    : confirmDescription, true, confirm));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 6 * density;
        body.addView(row, 0, params);
        return row;
    }

    /** The heading row with the plain OK/Cancel pair. */
    @NonNull
    public static LinearLayout addHeaderRow(@NonNull LinearLayout body, @NonNull CharSequence title,
                                            @Nullable Runnable confirm, @Nullable Runnable cancel) {
        return addHeaderRow(body, title, confirm, null, cancel);
    }

    /** One answer: a borderless glyph on a full touch target, since the label is the glyph. */
    @NonNull
    private static TextView glyphButton(@NonNull Context context, @NonNull String glyph,
                                        @NonNull CharSequence description, boolean primary,
                                        @NonNull Runnable action) {
        int density = Math.round(context.getResources().getDisplayMetrics().density);
        TextView button = new TextView(context);
        button.setText(glyph);
        button.setTextSize(18f);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        if (primary) {
            button.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary,
                button.getCurrentTextColor()));
        }
        android.util.TypedValue background = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, background, true)) {
            button.setBackgroundResource(background.resourceId);
        }
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            40 * density, 40 * density);
        params.leftMargin = 4 * density;
        button.setLayoutParams(params);
        return button;
    }

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
        params.bottomMargin = 4 * density;
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
        // A shade under the 48dp target: these menus are strips on the terminal's foot, and four
        // rows at the full height push the transcript they are about off the top of the panel.
        row.setMinHeight(44 * density);
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
        params.topMargin = 6 * density;
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
        // Material's own vertical insets are meant for a dialog's button bar; on a strip across the
        // terminal's foot they are a row of empty surface under the last thing the panel says.
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(36 * density);
        button.setMinimumHeight(36 * density);
        button.setTextSize(13f);
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
