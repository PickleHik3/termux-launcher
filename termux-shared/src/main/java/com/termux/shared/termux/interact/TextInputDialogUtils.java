package com.termux.shared.termux.interact;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class TextInputDialogUtils {

    public interface TextSetListener {

        void onTextSet(String text);
    }

    public static void textInput(Activity activity, int titleText, String initialText, int positiveButtonText, final TextSetListener onPositive, int neutralButtonText, final TextSetListener onNeutral, int negativeButtonText, final TextSetListener onNegative, final DialogInterface.OnDismissListener onDismiss) {
        textInput(activity, titleText, null, initialText, positiveButtonText, onPositive, neutralButtonText, onNeutral, negativeButtonText, onNegative, onDismiss);
    }

    /**
     * Same as {@link #textInput(Activity, int, String, int, TextSetListener, int, TextSetListener, int, TextSetListener, DialogInterface.OnDismissListener)},
     * but with an optional message shown above the input field (e.g. to disclose the real source
     * of a file being saved, since {@code initialText} is only ever the suggested file name).
     */
    public static void textInput(Activity activity, int titleText, String message, String initialText, int positiveButtonText, final TextSetListener onPositive, int neutralButtonText, final TextSetListener onNeutral, int negativeButtonText, final TextSetListener onNegative, final DialogInterface.OnDismissListener onDismiss) {
        final EditText input = new EditText(activity);
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((v, actionId, event) -> {
            onPositive.onTextSet(input.getText().toString());
            dialogHolder[0].dismiss();
            return true;
        });
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
        // M3 dialogs keep 24dp side insets for custom content; the title supplies top spacing.
        int paddingSides = Math.round(24 * dipInPixels);
        int paddingTop = Math.round(8 * dipInPixels);
        int paddingBottom = Math.round(16 * dipInPixels);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        layout.setPadding(paddingSides, paddingTop, paddingSides, paddingBottom);
        if (message != null) {
            TextView messageView = new TextView(activity);
            messageView.setText(message);
            messageView.setPadding(0, 0, 0, Math.round(8 * dipInPixels));
            layout.addView(messageView);
        }
        layout.addView(input);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(titleText).setView(layout).setPositiveButton(positiveButtonText, (d, whichButton) -> onPositive.onTextSet(input.getText().toString()));
        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> onNeutral.onTextSet(input.getText().toString()));
        }
        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> onNegative.onTextSet(input.getText().toString()));
        }
        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);
        dialogHolder[0] = builder.create();
        dialogHolder[0].setCanceledOnTouchOutside(false);
        dialogHolder[0].show();
    }
}
