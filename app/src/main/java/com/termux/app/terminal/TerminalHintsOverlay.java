package com.termux.app.terminal;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keyboard-first hint picker over the focused session's transcript. */
final class TerminalHintsOverlay {

    private TerminalHintsOverlay() {}

    static void show(@NonNull TermuxActivity activity, @NonNull String transcript) {
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract(transcript);
        if (hints.isEmpty()) {
            new AlertDialog.Builder(activity).setMessage(R.string.terminal_hints_none).show();
            return;
        }
        List<String> rows = new ArrayList<>(hints.size());
        for (TerminalHintsModel.Hint hint : hints) {
            rows.add(Character.toUpperCase(hint.label) + "   "
                + hint.type.name().toLowerCase(Locale.US) + "   " + hint.value);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_list_item_1, rows);
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.terminal_hints_title)
            .setMessage(R.string.terminal_hints_instructions)
            .setAdapter(adapter, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(ignored -> {
            ListView list = dialog.getListView();
            list.setOnItemClickListener((parent, view, position, id) ->
                choose(activity, dialog, hints.get(position), false));
            list.setOnItemLongClickListener((parent, view, position, id) -> {
                choose(activity, dialog, hints.get(position), false);
                return true;
            });
        });
        dialog.setOnKeyListener((DialogInterface ignored, int keyCode, KeyEvent event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return false;
            int unicode = event.getUnicodeChar() & KeyCharacterMap.COMBINING_ACCENT_MASK;
            if (unicode == 0) return false;
            char pressed = Character.toLowerCase((char) unicode);
            for (TerminalHintsModel.Hint hint : hints) {
                if (hint.label == pressed) {
                    choose(activity, dialog, hint, event.isShiftPressed());
                    return true;
                }
            }
            return false;
        });
        dialog.show();
    }

    private static void choose(@NonNull TermuxActivity activity, @NonNull AlertDialog dialog,
                               @NonNull TerminalHintsModel.Hint hint, boolean forceCopy) {
        dialog.dismiss();
        if (!forceCopy && hint.type == TerminalHintsModel.Type.URL) {
            ShareUtils.openUrl(activity, hint.value);
        } else {
            ShareUtils.copyTextToClipboard(activity, hint.value,
                activity.getString(R.string.terminal_hint_copied));
        }
    }
}
