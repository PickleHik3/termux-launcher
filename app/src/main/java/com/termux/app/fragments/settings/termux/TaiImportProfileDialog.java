package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiModelProfile;

/** Advanced artifact settings shared by file imports and the installed model's parameter screen. */
final class TaiImportProfileDialog {
    interface Listener { void onSave(@Nullable TaiModelProfile profile); }

    static void show(Context context, TaiModelProfile profile, Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * context.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        Spinner mode = new Spinner(context);
        mode.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
            context.getResources().getStringArray(R.array.tai_import_thinking_modes)));
        mode.setSelection(TaiModelProfile.THINKING_ALWAYS.equals(profile.thinkingMode) ? 2
            : TaiModelProfile.THINKING_TOGGLEABLE.equals(profile.thinkingMode) ? 1 : 0);
        content.addView(mode);
        EditText start = field(context, content, R.string.termux_ai_thought_start, profile.thinkingChannelStart);
        EditText end = field(context, content, R.string.termux_ai_thought_end, profile.thinkingChannelEnd);
        EditText limit = field(context, content, R.string.termux_ai_export_context,
            profile.maxContextTokens > 0 ? String.valueOf(profile.maxContextTokens) : "");
        limit.setInputType(InputType.TYPE_CLASS_NUMBER);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_import_profile)
            .setMessage(R.string.termux_ai_import_profile_help)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.termux_ai_import_profile_reset, (d, which) -> listener.onSave(null))
            .setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String first = start.getText().toString();
            String last = end.getText().toString();
            if (first.isEmpty() != last.isEmpty() || !first.isEmpty() && first.equals(last)) {
                end.setError(context.getString(R.string.termux_ai_import_marker_error));
                return;
            }
            int tokens = 0;
            try {
                if (!limit.getText().toString().trim().isEmpty()) {
                    tokens = Integer.parseInt(limit.getText().toString().trim());
                    if (tokens < 1) throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                limit.setError(context.getString(R.string.termux_ai_import_context_error));
                return;
            }
            String[] modes = {TaiModelProfile.THINKING_NONE, TaiModelProfile.THINKING_TOGGLEABLE, TaiModelProfile.THINKING_ALWAYS};
            listener.onSave(new TaiModelProfile(profile.compatibleAccelerators, profile.defaultMaxTokens,
                profile.defaultTopK, profile.defaultTopP, profile.defaultTemperature, profile.minDeviceMemoryInGb,
                "user-artifact-profile", modes[mode.getSelectedItemPosition()], first, last, tokens));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static EditText field(Context context, LinearLayout parent, int hint, String value) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setText(value == null ? "" : value);
        parent.addView(field);
        return field;
    }
}
