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
import androidx.annotation.StringRes;
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
 * The settings dialog of a built-in widget: one row per {@link BuiltinWidgetView.ConfigField} (a
 * text field, a switch, or a choice as segmented buttons or a strip of live previews; a row shown
 * only while another field holds a value follows it live), seeded from the record's bundle, and the Glass switch every built-in carries
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
        show(context, title, fields, current, listener, onDismiss, null);
    }

    /** As above, for {@code kind}: which Glass choices the widget offers. */
    public static void show(@NonNull Context context, @NonNull CharSequence title,
                            @NonNull List<BuiltinWidgetView.ConfigField> fields,
                            @NonNull Bundle current, @NonNull Listener listener,
                            @Nullable Runnable onDismiss, @Nullable BuiltinWidgetKind kind) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, Math.round(8 * density), pad, 0);
        List<TextInputEditText> inputs = new ArrayList<>();
        // The choices' and switches' values as the sheet holds them now: a row shown only for one
        // value of another field follows them live.
        Map<String, String> chosen = new LinkedHashMap<>();
        List<Runnable> visibility = new ArrayList<>();
        Runnable syncVisibility = () -> { for (Runnable sync : visibility) sync.run(); };
        for (BuiltinWidgetView.ConfigField field : fields) {
            final View row;
            switch (field.type) {
                case SWITCH:
                    row = switchRow(context, field, current, chosen, syncVisibility);
                    break;
                case CHOICE:
                    row = choiceRow(context, field, current, chosen, syncVisibility);
                    break;
                case ZONES:
                    row = zonesRow(context, field, current, inputs);
                    break;
                default:
                    row = textBox(context, field, current, inputs);
                    break;
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(12 * density);
            column.addView(row, params);
            if (field.shownWhenKey != null) {
                visibility.add(() -> row.setVisibility(field.shownFor(chosen)
                    ? View.VISIBLE : View.GONE));
            }
        }
        syncVisibility.run();
        // The Glass row (BuiltinWidgetGlass.choices): none for a widget that is always Bare.
        final java.util.function.Supplier<String> glassPicked = glassRow(context, column, current,
            fields.isEmpty(), density, BuiltinWidgetGlass.choices(kind));
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
                String picked = glassPicked.get();
                if (picked != null) BuiltinWidgetGlass.write(values, picked);
                values.putAll(chosen);
                for (TextInputEditText input : inputs) {
                    CharSequence text = input.getText();
                    values.put(String.valueOf(input.getTag()), text == null ? "" : text.toString().trim());
                }
                listener.onSaved(BuiltinWidgetHost.configOf(values));
            })
            .setOnDismissListener(dialog -> { if (onDismiss != null) onDismiss.run(); })
            .show();
    }

    /**
     * The Glass row: Page / On / Off, and Bare on the widgets that offer it. The supplier gives
     * what is picked, or null when the widget has no such row (Glance, always Bare).
     */
    @NonNull private static java.util.function.Supplier<String> glassRow(
            @NonNull Context context, @NonNull LinearLayout column, @NonNull Bundle current,
            boolean noFields, float density, @NonNull String[] glassValues) {
        if (glassValues.length == 0) return () -> null;
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
        int[] glassLabels = new int[glassValues.length];
        for (int i = 0; i < glassValues.length; i++) glassLabels[i] = glassLabel(glassValues[i]);
        String storedGlass = current.getString(BuiltinWidgetGlass.CONFIG_KEY);
        int[] glassIds = new int[glassValues.length];
        for (int i = 0; i < glassValues.length; i++) {
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
        glassParams.topMargin = Math.round((noFields ? 4 : 16) * density);
        column.addView(glass, glassParams);
        TextView glassSummary = new TextView(context);
        glassSummary.setText(R.string.builtin_widget_glass_summary);
        glassSummary.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodySmall);
        glassSummary.setTextColor(MaterialColors.getColor(glassLabel,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        column.addView(glassSummary, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return () -> {
            for (int i = 0; i < glassValues.length; i++)
                if (glassChoice.getCheckedButtonId() == glassIds[i]) return glassValues[i];
            return "";
        };
    }

    @StringRes private static int glassLabel(@NonNull String value) {
        if (BuiltinWidgetGlass.ON.equals(value)) return R.string.builtin_widget_glass_on;
        if (BuiltinWidgetGlass.OFF.equals(value)) return R.string.builtin_widget_glass_off;
        if (BuiltinWidgetGlass.BARE.equals(value)) return R.string.builtin_widget_glass_bare;
        return R.string.builtin_widget_glass_follow;
    }

    /** A text field, seeded from the record. */
    @NonNull private static View textBox(@NonNull Context context,
                                         @NonNull BuiltinWidgetView.ConfigField field,
                                         @NonNull Bundle current,
                                         @NonNull List<TextInputEditText> inputs) {
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
        inputs.add(input);
        return box;
    }

    /**
     * The Clock's "Time zones" row: the label, what is set, and a chevron; a tap opens two fields
     * for a city or a time zone ("Tokyo", "Asia/Kolkata"), and an empty one is none.
     */
    @NonNull private static View zonesRow(@NonNull Context context,
                                          @NonNull BuiltinWidgetView.ConfigField field,
                                          @NonNull Bundle current,
                                          @NonNull List<TextInputEditText> inputs) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(Math.round(48 * density));
        head.setClickable(true);
        head.setFocusable(true);
        head.setContentDescription(field.label);
        TextView label = new TextView(context);
        label.setText(field.label);
        label.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
            1f));
        TextView summary = new TextView(context);
        summary.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyMedium);
        summary.setTextColor(MaterialColors.getColor(label,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        head.addView(summary, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView chevron = new TextView(context);
        chevron.setText("\u203A");
        chevron.setTextSize(24);
        chevron.setPadding(Math.round(8 * density), 0, 0, 0);
        head.addView(chevron, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout fieldsBox = new LinearLayout(context);
        fieldsBox.setOrientation(LinearLayout.VERTICAL);
        fieldsBox.setVisibility(View.GONE);
        List<TextInputEditText> mine = new ArrayList<>();
        String[] keys = {field.key, field.secondKey == null ? field.key : field.secondKey};
        int[] hints = {R.string.bw_clocks_zone1_label, R.string.bw_clocks_zone2_label};
        for (int i = 0; i < 2; i++) {
            View box = textBox(context, new BuiltinWidgetView.ConfigField(keys[i],
                context.getString(hints[i]), BuiltinWidgetView.ConfigField.Type.TEXT, ""),
                current, mine);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(8 * density);
            fieldsBox.addView(box, params);
        }
        inputs.addAll(mine);
        column.addView(fieldsBox);
        Runnable refresh = () -> {
            List<String> cities = new ArrayList<>();
            for (TextInputEditText input : mine) {
                java.time.ZoneId zone = ClockWidgetFormats.resolveZone(
                    String.valueOf(input.getText()));
                if (zone != null) cities.add(ClockWidgetFormats.cityName(zone.getId()));
            }
            summary.setText(cities.isEmpty() ? context.getString(R.string.bw_clock_zones_none)
                : android.text.TextUtils.join(", ", cities));
        };
        refresh.run();
        head.setOnClickListener(v -> {
            boolean open = fieldsBox.getVisibility() != View.VISIBLE;
            fieldsBox.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setRotation(open ? 90f : 0f);
            if (!open) refresh.run();
        });
        return column;
    }

    /** The value a choice or switch starts at: the stored one when it is one it offers. */
    @NonNull static String startValue(@NonNull BuiltinWidgetView.ConfigField field,
                                      @NonNull Bundle current) {
        String stored = current.getString(field.key);
        if (field.type == BuiltinWidgetView.ConfigField.Type.SWITCH) {
            if (BuiltinWidgetView.ConfigField.ON.equals(stored)
                || BuiltinWidgetView.ConfigField.OFF.equals(stored)) return stored;
            return field.fallback;
        }
        for (BuiltinWidgetView.ConfigField.Option option : field.options) {
            if (option.value.equals(stored)) return stored;
        }
        return field.fallback;
    }

    /** A switch on its label's line, the label taking the row. */
    @NonNull private static View switchRow(@NonNull Context context,
                                           @NonNull BuiltinWidgetView.ConfigField field,
                                           @NonNull Bundle current,
                                           @NonNull Map<String, String> picked,
                                           @NonNull Runnable onChange) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Math.round(48 * context.getResources().getDisplayMetrics().density));
        TextView label = new TextView(context);
        label.setText(field.label);
        label.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
            1f));
        com.google.android.material.materialswitch.MaterialSwitch toggle =
            new com.google.android.material.materialswitch.MaterialSwitch(context);
        String start = startValue(field, current);
        picked.put(field.key, start);
        toggle.setChecked(BuiltinWidgetView.ConfigField.ON.equals(start));
        toggle.setContentDescription(field.label);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            picked.put(field.key, checked ? BuiltinWidgetView.ConfigField.ON
                : BuiltinWidgetView.ConfigField.OFF);
            onChange.run();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /**
     * A choice: its label, then a strip of the options drawn as themselves (a live preview) when
     * the field draws them, else segmented buttons on the label's line.
     */
    @NonNull private static View choiceRow(@NonNull Context context,
                                           @NonNull BuiltinWidgetView.ConfigField field,
                                           @NonNull Bundle current,
                                           @NonNull Map<String, String> picked,
                                           @NonNull Runnable onChange) {
        float density = context.getResources().getDisplayMetrics().density;
        String start = startValue(field, current);
        picked.put(field.key, start);
        TextView label = new TextView(context);
        label.setText(field.label);
        label.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        BuiltinWidgetView.ConfigField.TileArt art = field.tiles;
        if (art == null) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            Map<Integer, String> valueOf = new java.util.HashMap<>();
            for (BuiltinWidgetView.ConfigField.Option option : field.options) {
                MaterialButton button = new MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setId(View.generateViewId());
                button.setText(option.label);
                group.addView(button);
                valueOf.put(button.getId(), option.value);
                if (option.value.equals(start)) group.check(button.getId());
            }
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                String value = valueOf.get(checkedId);
                if (!isChecked || value == null) return;
                picked.put(field.key, value);
                onChange.run();
            });
            row.addView(group, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(label);
        android.widget.HorizontalScrollView scroll =
            new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int primary = MaterialColors.getColor(label, androidx.appcompat.R.attr.colorPrimary);
        int outline = MaterialColors.getColor(label,
            com.google.android.material.R.attr.colorOutlineVariant);
        List<View> frames = new ArrayList<>();
        List<String> values = new ArrayList<>();
        Runnable dress = () -> {
            for (int i = 0; i < frames.size(); i++) {
                boolean selected = values.get(i).equals(picked.get(field.key));
                android.graphics.drawable.GradientDrawable rim =
                    new android.graphics.drawable.GradientDrawable();
                rim.setCornerRadius(12 * density);
                rim.setColor(android.graphics.Color.TRANSPARENT);
                rim.setStroke(Math.round((selected ? 2 : 1) * density),
                    selected ? primary : outline);
                frames.get(i).setBackground(rim);
                frames.get(i).setSelected(selected);
            }
        };
        for (BuiltinWidgetView.ConfigField.Option option : field.options) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            tile.setPadding(Math.round(4 * density), Math.round(8 * density),
                Math.round(4 * density), 0);
            android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
            int inset = Math.round(6 * density);
            frame.setPadding(inset, inset, inset, inset);
            frame.addView(art.make(context, option.value),
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            tile.addView(frame, new LinearLayout.LayoutParams(Math.round(76 * density),
                Math.round(48 * density)));
            TextView name = new TextView(context);
            name.setText(option.label);
            name.setTextAppearance(com.google.android.material.R.style
                .TextAppearance_Material3_LabelMedium);
            name.setGravity(android.view.Gravity.CENTER);
            name.setMaxLines(1);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = Math.round(4 * density);
            tile.addView(name, nameParams);
            tile.setContentDescription(option.label);
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(v -> {
                picked.put(field.key, option.value);
                dress.run();
                onChange.run();
            });
            frames.add(frame);
            values.add(option.value);
            strip.addView(tile);
        }
        dress.run();
        scroll.addView(strip);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = Math.round(4 * density);
        column.addView(scroll, scrollParams);
        return column;
    }

    /** True when {@code value} parses as a positive number. */
    public static boolean isPositiveNumber(@Nullable String value) {
        if (value == null) return false;
        try { return Integer.parseInt(value.trim()) > 0; } catch (NumberFormatException e) { return false; }
    }
}
