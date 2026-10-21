package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.termux.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings dialog of a built-in widget: one text field per {@link BuiltinWidgetView.ConfigField},
 * seeded from the record's bundle, and the Glass switch every built-in carries
 * ({@link BuiltinWidgetGlass}), written back on Save; anything stored that the sheet does not offer
 * is kept. Material by construction, so it follows the scheme like every other dialog.
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
        // Every built-in widget's Glass, per widget (BuiltinWidgetGlass): follow the page, on or
        // off, in one row, and one line.
        LinearLayout glass = new LinearLayout(context);
        glass.setOrientation(LinearLayout.HORIZONTAL);
        glass.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView glassLabel = new TextView(context);
        glassLabel.setText(R.string.builtin_widget_glass);
        glassLabel.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        glass.addView(glassLabel, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButtonToggleGroup glassChoice = new MaterialButtonToggleGroup(context);
        glassChoice.setSingleSelection(true);
        glassChoice.setSelectionRequired(true);
        String[] glassValues = {"", BuiltinWidgetGlass.ON, BuiltinWidgetGlass.OFF};
        int[] glassLabels = {R.string.builtin_widget_glass_follow, R.string.builtin_widget_glass_on,
            R.string.builtin_widget_glass_off};
        String storedGlass = current.getString(BuiltinWidgetGlass.CONFIG_KEY);
        int[] glassIds = new int[3];
        for (int i = 0; i < 3; i++) {
            MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setId(View.generateViewId());
            button.setText(glassLabels[i]);
            glassIds[i] = button.getId();
            glassChoice.addView(button);
            if (glassValues[i].equals(storedGlass == null ? "" : storedGlass))
                glassChoice.check(button.getId());
        }
        if (glassChoice.getCheckedButtonId() == View.NO_ID) glassChoice.check(glassIds[0]);
        glass.addView(glassChoice, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams glassParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glassParams.topMargin = Math.round((fields.isEmpty() ? 4 : 16) * density);
        column.addView(glass, glassParams);
        TextView glassSummary = new TextView(context);
        glassSummary.setText(R.string.builtin_widget_glass_summary);
        glassSummary.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodySmall);
        glassSummary.setTextColor(MaterialColors.getColor(glassLabel,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        column.addView(glassSummary, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);
        new MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.builtin_widget_settings_save, (dialog, which) -> {
                // What the sheet does not offer (a widget's own state, kept in its settings)
                // stays as it was; what it offers is written whole.
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : current.keySet()) {
                    Object kept = current.get(key);
                    if (kept instanceof String) values.put(key, (String) kept);
                }
                String picked = "";
                for (int i = 0; i < 3; i++)
                    if (glassChoice.getCheckedButtonId() == glassIds[i]) picked = glassValues[i];
                BuiltinWidgetGlass.write(values, picked);
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
