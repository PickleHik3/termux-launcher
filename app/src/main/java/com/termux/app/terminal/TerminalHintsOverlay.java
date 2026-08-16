package com.termux.app.terminal;

import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;

import java.util.List;
import java.util.Locale;

/**
 * Keyboard-first hint picker over the focused session's transcript, on the
 * {@link TerminalSheetController} plane.
 *
 * <p>The letter jump used to be a {@code Dialog.OnKeyListener}, which only ever saw hardware
 * strokes. On the sheet it is a {@link TerminalSheetController.TextSink} instead, so the same press
 * arrives whether it came from the in-app keyboard, a hardware key or a system IME's commit — and
 * the shift variant rides along as the case of the character rather than as a modifier flag no soft
 * keyboard reports.
 */
final class TerminalHintsOverlay {

    private TerminalHintsOverlay() {}

    static void show(@NonNull TermuxActivity activity, @NonNull String transcript) {
        List<TerminalHintsModel.Hint> hints = TerminalHintsModel.extract(transcript);
        TerminalSheetController sheet = activity.getTerminalSheetController();
        String title = activity.getString(R.string.terminal_hints_title);
        if (hints.isEmpty()) {
            LinearLayout notice = TerminalSheetViews.body(activity);
            TerminalSheetViews.addMessage(notice, activity.getString(R.string.terminal_hints_none));
            sheet.show(title, notice);
            return;
        }
        LinearLayout body = TerminalSheetViews.body(activity);
        TerminalSheetViews.addMessage(body,
            activity.getString(R.string.terminal_hints_instructions));
        for (TerminalHintsModel.Hint hint : hints) {
            TextView row = TerminalSheetViews.addMenuRow(body,
                Character.toUpperCase(hint.label) + "   "
                    + hint.type.name().toLowerCase(Locale.US) + "   " + hint.value,
                () -> choose(activity, hint, false));
            row.setOnLongClickListener(view -> {
                choose(activity, hint, false);
                return true;
            });
        }
        sheet.show(title, TerminalSheetViews.wrapScrolling(body), false,
            new LetterJump(activity, hints), null);
    }

    /** Typing a hint's label picks it; typing its capital forces the copy branch. */
    private static final class LetterJump implements TerminalSheetController.TextSink {

        @NonNull private final TermuxActivity activity;
        @NonNull private final List<TerminalHintsModel.Hint> hints;

        LetterJump(@NonNull TermuxActivity activity,
                   @NonNull List<TerminalHintsModel.Hint> hints) {
            this.activity = activity;
            this.hints = hints;
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

        /** Nothing is being edited here, so ⌫ and ⏎ have nothing to spend themselves on. */
        @Override public void onBackspace() {}

        @Override public boolean onCommit() {
            return false;
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
