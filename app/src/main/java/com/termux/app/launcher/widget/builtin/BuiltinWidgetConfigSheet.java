package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.termux.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings dialog of a built-in widget: one text field per {@link BuiltinWidgetView.ConfigField},
 * seeded from the record's bundle, written back whole on Save. Material by construction, so it
 * follows the scheme like every other dialog.
 */
public final class BuiltinWidgetConfigSheet {
    public interface Listener { void onSaved(@NonNull Bundle config); }

    private BuiltinWidgetConfigSheet() { }

    public static void show(@NonNull Context context, @NonNull CharSequence title,
                            @NonNull List<BuiltinWidgetView.ConfigField> fields,
                            @NonNull Bundle current, @NonNull Listener listener) {
        show(context, title, fields, current, listener, null);
    }

    /** As {@link #show(Context, CharSequence, List, Bundle, Listener)}, with a dismiss callback. */
    public static void show(@NonNull Context context, @NonNull CharSequence title,
                            @NonNull List<BuiltinWidgetView.ConfigField> fields,
                            @NonNull Bundle current, @NonNull Listener listener,
                            @Nullable Runnable onDismiss) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, Math.round(8 * density), pad, 0);
        List<TextInputEditText> inputs = new ArrayList<>();
        for (BuiltinWidgetView.ConfigField field : fields) {
            TextInputLayout box = new TextInputLayout(context, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
            box.setHint(field.label);
            TextInputEditText input = new TextInputEditText(box.getContext());
            input.setTag(field.key);
            String value = current.getString(field.key);
            input.setText(value == null ? field.fallback : value);
            switch (field.type) {
                case NUMBER:
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    break;
                case MULTILINE:
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    input.setMaxLines(4);
                    break;
                default:
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setSingleLine(true);
            }
            box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(12 * density);
            column.addView(box, params);
            inputs.add(input);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);
        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.builtin_widget_settings_save, (dialog, which) -> {
                Map<String, String> values = new LinkedHashMap<>();
                for (TextInputEditText input : inputs) {
                    CharSequence text = input.getText();
                    values.put(String.valueOf(input.getTag()), text == null ? "" : text.toString().trim());
                }
                listener.onSaved(BuiltinWidgetHost.configOf(values));
            })
            .setOnDismissListener(dialog -> { if (onDismiss != null) onDismiss.run(); })
            .show();
    }

    /** True when {@code value} parses as a positive number. */
    public static boolean isPositiveNumber(@Nullable String value) {
        if (value == null) return false;
        try { return Integer.parseInt(value.trim()) > 0; } catch (NumberFormatException e) { return false; }
    }
}
