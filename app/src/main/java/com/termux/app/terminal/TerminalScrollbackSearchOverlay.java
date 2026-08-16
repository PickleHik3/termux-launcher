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
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * The fallback scrollback search, for the one case the find strip cannot serve: no in-app keyboard
 * to type a query into, and hence nothing on screen to aim its three input channels at.
 *
 * <p>Shaped to match the strip that replaced it — a bare bar on the dock's edge, no plane backdrop,
 * mono query with the count at the trailing end — so the fallback looks like the thing it stands in
 * for rather than like the full-screen list it used to be. The transcript stays visible above it;
 * the only thing it gives up versus the strip is the in-place highlighting, which is why it lists
 * its hits instead.
 *
 * <p>Nothing here takes focus: the query is a label typed from the sheet's key channel, so the
 * terminal keeps its {@code InputConnection} and no system IME is summoned.
 */
final class TerminalScrollbackSearchOverlay {

    /** One result row's height. Compact, because this is a bar and not a browser. */
    private static final float ROW_HEIGHT_DP = 30f;
    /** The bar's ceiling in rows; beyond this the list scrolls inside the frame. */
    private static final int MAX_VISIBLE_ROWS = 4;
    /** Marks the highlighted row, since an unfocused list draws no selector of its own. */
    private static final String HIGHLIGHT_PREFIX = "▸ ";

    /**
     * Where a chosen match sends the pane. A seam rather than a direct {@code TerminalView} call so
     * the surface can be built and typed at without a live emulator behind it.
     */
    interface RowJump {
        void jumpTo(int row);
    }

    private TerminalScrollbackSearchOverlay() {}

    static void show(@NonNull TermuxActivity activity, @NonNull TerminalView terminalView) {
        if (terminalView.mEmulator == null) return;
        show(activity, snapshot(terminalView.mEmulator), row -> {
            terminalView.jumpToBufferRow(row);
            terminalView.requestFocus();
        });
    }

    static void show(@NonNull TermuxActivity activity,
                     @NonNull List<TerminalScrollbackSearchModel.Line> snapshot,
                     @NonNull RowJump jump) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout body = TerminalSheetViews.body(activity);

        LinearLayout queryRow = new LinearLayout(activity);
        queryRow.setOrientation(LinearLayout.HORIZONTAL);
        queryRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView query = new TextView(activity);
        // Mono at the strip's size: this is the same surface by another route, and the query is
        // terminal text.
        query.setTypeface(android.graphics.Typeface.MONOSPACE);
        query.setTextSize(14f);
        query.setSingleLine(true);
        query.setMinHeight(Math.round(28 * density));
        query.setGravity(Gravity.CENTER_VERTICAL);
        queryRow.addView(query, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(activity);
        count.setTypeface(android.graphics.Typeface.MONOSPACE);
        count.setTextSize(12f);
        count.setAlpha(0.7f);
        count.setGravity(Gravity.CENTER_VERTICAL);
        queryRow.addView(count, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TerminalSheetViews.addToFrame(body, queryRow);

        ListView list = new ListView(activity);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        // Selection is drawn by the adapter's own highlight rather than by the list's activated
        // state: nothing on this plane may take focus, and an unfocused ListView paints no selector.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        int rowHeightPx = Math.round(ROW_HEIGHT_DP * density);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0);
        listParams.topMargin = Math.round(6 * density);
        body.addView(list, listParams);

        List<TerminalScrollbackSearchModel.Match> matches = new ArrayList<>();
        int[] highlight = {0};
        // The bar lifts only as far as the results it actually has, to a ceiling, so a one-hit query
        // barely covers the terminal and a hundred-hit query scrolls inside a fixed frame.
        Runnable resize = () -> {
            int rows = Math.min(matches.size(), MAX_VISIBLE_ROWS);
            listParams.height = rows * rowHeightPx;
            list.setLayoutParams(listParams);
            list.setVisibility(rows == 0 ? android.view.View.GONE : android.view.View.VISIBLE);
        };
        Runnable render = () -> {
            adapter.clear();
            for (int i = 0; i < matches.size(); i++) {
                TerminalScrollbackSearchModel.Match match = matches.get(i);
                String row = activity.getString(R.string.terminal_scrollback_search_row,
                    displayRow(match.row), match.snippet);
                adapter.add(i == highlight[0] ? HIGHLIGHT_PREFIX + row : row);
            }
            adapter.notifyDataSetChanged();
            if (!matches.isEmpty()) list.setSelection(highlight[0]);
        };

        list.setOnItemClickListener((parent, view, position, id) ->
            choose(sheet, jump, matches.get(position)));

        TerminalSheetController.TextField field = new TerminalSheetController.TextField(query,
            activity.getString(R.string.terminal_scrollback_search_hint), value -> {
                matches.clear();
                matches.addAll(TerminalScrollbackSearchModel.search(snapshot, value));
                highlight[0] = 0;
                count.setText(value.isEmpty() ? "" : activity.getResources().getQuantityString(
                    R.plurals.terminal_scrollback_search_results, matches.size(), matches.size()));
                resize.run();
                render.run();
            }, () -> {
                if (!matches.isEmpty()) choose(sheet, jump, matches.get(highlight[0]));
            });

        // Arrows walk the results from all three input paths at once — the in-app keyboard, an
        // extra-keys row and a hardware or IME arrow all arrive as the same key code.
        TerminalSheetController.TextSink sink = new TerminalSheetController.TextSink() {
            @Override public void onText(@NonNull String text) { field.onText(text); }

            @Override public void onBackspace() { field.onBackspace(); }

            @Override public boolean onCommit() { return field.onCommit(); }

            @Override public boolean onArrow(int delta) {
                if (matches.isEmpty()) return false;
                int moved = TerminalScrollbackSearchModel.moveHighlight(highlight[0], delta,
                    matches.size());
                if (moved == highlight[0]) return true;
                highlight[0] = moved;
                render.run();
                return true;
            }
        };

        resize.run();
        // No heading: this is a bar sitting on the dock, and the field's own hint already says what
        // it searches. A title row would spend a fifth of the bar's height repeating it.
        sheet.show("", body, false, sink, null, false,
            TerminalSheetController.Placement.aboveDockBare());
    }

    private static void choose(@NonNull TerminalSheetController sheet, @NonNull RowJump jump,
                               @NonNull TerminalScrollbackSearchModel.Match match) {
        sheet.dismiss();
        jump.jumpTo(match.row);
    }

    @NonNull
    private static List<TerminalScrollbackSearchModel.Line> snapshot(TerminalEmulator emulator) {
        TerminalBuffer screen = emulator.getScreen();
        int first = -screen.getActiveTranscriptRows();
        List<TerminalScrollbackSearchModel.Line> lines = new ArrayList<>(
            screen.getActiveTranscriptRows() + emulator.mRows);
        for (int row = first; row < emulator.mRows; row++) {
            lines.add(new TerminalScrollbackSearchModel.Line(row,
                screen.getSelectedText(0, row, emulator.mColumns, row, false)));
        }
        return lines;
    }

    private static String displayRow(int row) {
        return row < 0 ? Integer.toString(row) : "+" + row;
    }
}
