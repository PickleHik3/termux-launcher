package com.termux.app.terminal;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;

import java.util.List;
import java.util.Locale;

/**
 * Quick select: keyboard-labelled URLs, paths, hashes and source line references from the focused
 * session's transcript.
 *
 * <p>Shaped like the scrollback-search bar rather than a full-screen sheet: a bare bar rising from
 * the terminal's own bottom edge with no plane backdrop, so the transcript the labels point into
 * stays visible while choosing. Rows lift the bar only as far as they exist, to a ceiling, and scroll beyond it.
 *
 * <p>The letter jump used to be a {@code Dialog.OnKeyListener}, which only ever saw hardware
 * strokes. On the sheet it is a {@link TerminalSheetController.TextSink} instead, so the same press
 * arrives whether it came from the in-app keyboard, a hardware key or a system IME's commit — and
 * the copy variant rides along as the case of the character rather than as a modifier flag no soft
 * keyboard reports.
 */
public final class TerminalHintsOverlay {

    /** One result row's height. Compact, because this is a bar and not a browser. */
    private static final float ROW_HEIGHT_DP = 30f;
    /** The bar's ceiling in rows; beyond this the list scrolls inside the frame. */
    private static final int MAX_VISIBLE_ROWS = 5;
    /** Marks the highlighted row, since an unfocused list draws no selector of its own. */
    private static final String HIGHLIGHT_PREFIX = "▸ ";

    private TerminalHintsOverlay() {}

    public static void show(@NonNull TermuxActivity activity, @NonNull String transcript) {
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract(transcript);
        TerminalSheetController sheet = activity.getTerminalSheetController();
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout body = TerminalSheetViews.body(activity);

        TextView header = new TextView(activity);
        header.setTextSize(11f);
        header.setAlpha(0.7f);
        header.setSingleLine(true);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinHeight(Math.round(24 * density));
        header.setText(hints.isEmpty()
            ? activity.getString(R.string.terminal_hints_none)
            : activity.getString(R.string.terminal_hints_instructions));
        TerminalSheetViews.addToFrame(body, header);

        if (hints.isEmpty()) {
            sheet.show("", body, false, null, null, false,
                TerminalSheetController.Placement.terminalFoot());
            return;
        }

        ListView list = new ListView(activity);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        // Selection is drawn by the adapter's own highlight rather than by the list's activated
        // state: nothing on this plane may take focus, and an unfocused ListView paints no
        // selector.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        int rowHeightPx = Math.round(ROW_HEIGHT_DP * density);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.min(hints.size(), MAX_VISIBLE_ROWS) * rowHeightPx);
        listParams.topMargin = Math.round(4 * density);
        body.addView(list, listParams);

        int[] highlight = {0};
        Runnable render = () -> {
            adapter.clear();
            for (int i = 0; i < hints.size(); i++) {
                TerminalHintsModel.Hint hint = hints.get(i);
                String row = Character.toUpperCase(hint.label) + "  "
                    + hint.type.name().toLowerCase(Locale.US) + "  " + hint.value;
                adapter.add(i == highlight[0] ? HIGHLIGHT_PREFIX + row : row);
            }
            adapter.notifyDataSetChanged();
            list.setSelection(highlight[0]);
        };
        render.run();

        list.setOnItemClickListener((parent, view, position, id) ->
            choose(activity, hints.get(position), false));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            choose(activity, hints.get(position), true);
            return true;
        });

        sheet.show("", body, false, new LetterJump(activity, hints, highlight, render), null,
            false, TerminalSheetController.Placement.terminalFoot());
    }

    /**
     * Typing a hint's label picks it; typing its capital forces the copy branch. Arrows walk the
     * highlight and ⏎ picks it, for keyboards aimed at the list rather than at a label.
     */
    private static final class LetterJump implements TerminalSheetController.TextSink {

        @NonNull private final TermuxActivity activity;
        @NonNull private final List<TerminalHintsModel.Hint> hints;
        @NonNull private final int[] highlight;
        @NonNull private final Runnable render;

        LetterJump(@NonNull TermuxActivity activity,
                   @NonNull List<TerminalHintsModel.Hint> hints,
                   @NonNull int[] highlight,
                   @NonNull Runnable render) {
            this.activity = activity;
            this.hints = hints;
            this.highlight = highlight;
            this.render = render;
        }

        @Override
        public void onText(@NonNull String text) {
            if (text.isEmpty()) return;
            int typed = text.codePointAt(0);
            char pressed = Character.toLowerCase((char) typed);
            for (TerminalHintsModel.Hint hint : hints) {
                if (hint.label == pressed) {
                    choose(activity, hint, Character.isUpperCase(typed));
                    return;
                }
            }
        }

        /** Nothing is being edited here, so ⌫ has nothing to spend itself on. */
        @Override public void onBackspace() {}

        @Override public boolean onCommit() {
            choose(activity, hints.get(highlight[0]), false);
            return true;
        }

        @Override public boolean onArrow(int delta) {
            int moved = Math.max(0, Math.min(hints.size() - 1, highlight[0] + delta));
            if (moved == highlight[0]) return true;
            highlight[0] = moved;
            render.run();
            return true;
        }
    }

    private static void choose(@NonNull TermuxActivity activity,
                               @NonNull TerminalHintsModel.Hint hint, boolean forceCopy) {
        activity.getTerminalSheetController().dismiss();
        if (!forceCopy && hint.type == TerminalHintsModel.Type.URL) {
            ShareUtils.openUrl(activity, hint.value);
        } else {
            ShareUtils.copyTextToClipboard(activity, hint.value,
                activity.getString(R.string.terminal_hint_copied));
        }
    }
}
