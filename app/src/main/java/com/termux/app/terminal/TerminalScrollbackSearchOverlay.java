package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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

/** Native scrollback-search dialog that jumps the current pane to a selected row. */
final class TerminalScrollbackSearchOverlay {

    private TerminalScrollbackSearchOverlay() {}

    static void show(@NonNull TermuxActivity activity, @NonNull TerminalView terminalView) {
        if (terminalView.mEmulator == null) return;
        List<TerminalScrollbackSearchModel.Line> snapshot = snapshot(terminalView.mEmulator);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int margin = dp(activity, 20);
        content.setPadding(margin, dp(activity, 4), margin, 0);
        EditText query = new EditText(activity);
        query.setSingleLine(true);
        query.setHint(R.string.terminal_scrollback_search_hint);
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        content.addView(query, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView count = new TextView(activity);
        count.setGravity(Gravity.START);
        content.addView(count, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(activity);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 360));
        listParams.topMargin = dp(activity, 6);
        content.addView(list, listParams);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        List<TerminalScrollbackSearchModel.Match> matches = new ArrayList<>();
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.terminal_scrollback_search_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        Runnable selectFirst = () -> {
            if (!matches.isEmpty()) choose(dialog, terminalView, matches.get(0));
        };
        query.setOnEditorActionListener((view, actionId, event) -> {
            selectFirst.run();
            return !matches.isEmpty();
        });
        query.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                selectFirst.run();
                return !matches.isEmpty();
            }
            return false;
        });
        list.setOnItemClickListener((parent, view, position, id) ->
            choose(dialog, terminalView, matches.get(position)));
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int countChars) {
                matches.clear();
                matches.addAll(TerminalScrollbackSearchModel.search(snapshot, s.toString()));
                adapter.clear();
                for (TerminalScrollbackSearchModel.Match match : matches) {
                    adapter.add(activity.getString(R.string.terminal_scrollback_search_row,
                        displayRow(match.row), match.snippet));
                }
                count.setText(s.length() == 0 ? "" : activity.getResources().getQuantityString(
                    R.plurals.terminal_scrollback_search_results, matches.size(), matches.size()));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        dialog.setOnShowListener(ignored -> query.requestFocus());
        dialog.show();
    }

    private static void choose(AlertDialog dialog, TerminalView view,
                               TerminalScrollbackSearchModel.Match match) {
        dialog.dismiss();
        view.jumpToBufferRow(match.row);
        view.requestFocus();
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

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
