package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiSettings;
import com.termux.app.notice.AppNotice;

/**
 * The one Hugging Face token entry dialog, shared by TAI settings, the import flow and the Model
 * Centre so every "add a token" shortcut looks and behaves the same.
 */
final class TaiHuggingFaceTokenDialog {

    private static final String TOKENS_URL = "https://huggingface.co/settings/tokens";

    private TaiHuggingFaceTokenDialog() {
    }

    /** Shows the dialog; {@code onSaved} runs after the token has been stored. */
    static void show(@NonNull Context context, @Nullable Runnable onSaved) {
        float density = context.getResources().getDisplayMetrics().density;
        int padH = Math.round(24 * density);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);

        TextView message = new TextView(context);
        message.setText(R.string.termux_ai_huggingface_token_dialog_message);
        layout.addView(message);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(new TaiSettings(context).getHuggingFaceToken());
        input.setSelectAllOnFocus(true);
        layout.addView(input);

        layout.addView(hint(context, R.string.termux_ai_huggingface_token_permissions_hint));
        layout.addView(hint(context, R.string.termux_ai_huggingface_token_gated_hint));

        // A custom view is not scrolled by the dialog; wrap it so the buttons stay reachable.
        ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setView(scroll)
            .setPositiveButton(R.string.termux_ai_dialog_save, (d, w) -> {
                new TaiSettings(context).setHuggingFaceToken(input.getText().toString().trim());
                if (onSaved != null) onSaved.run();
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action, (d, w) -> openUrl(context, TOKENS_URL))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private static TextView hint(@NonNull Context context, int textRes) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView hint = new TextView(context);
        hint.setText(textRes);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        TypedValue color = new TypedValue();
        if (context.getTheme().resolveAttribute(com.termux.shared.R.attr.termuxColorOnSurfaceVariant, color, true)) {
            hint.setTextColor(color.data);
        }
        hint.setPadding(0, Math.round(10 * density), 0, 0);
        return hint;
    }

    private static void openUrl(@NonNull Context context, @NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            AppNotice.show(context, url, true);
        }
    }
}
