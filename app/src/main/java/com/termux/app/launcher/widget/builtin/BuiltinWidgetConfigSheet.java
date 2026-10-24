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
    /** Saves a draft: true once it is stored, false when it could not be (the sheet stays). */
    public interface Listener { boolean onSaved(@NonNull Bundle config); }

    /** The Save button carries this tag, for tests. */
    static final String SAVE_TAG = "builtin_widget_save";
    /** The Glass help button carries this tag, for tests. */
    static final String GLASS_HELP_TAG = "builtin_widget_glass_help";
    /** The Additional clocks heading row carries this tag, for tests. */
    static final String ZONES_HEAD_TAG = "builtin_widget_zones_head";

    private BuiltinWidgetConfigSheet() { }

    @NonNull public static androidx.appcompat.app.AlertDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener) {
        return show(context, title, fields, current, listener, null);
    }

    /** As {@link #show(Context, CharSequence, List, Bundle, Listener)}, with a dismiss callback. */
    @NonNull public static androidx.appcompat.app.AlertDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener, @Nullable Runnable onDismiss) {
        return show(context, title, fields, current, listener, onDismiss, null);
    }

    /** As above, for {@code kind}: which Glass choices the widget offers. */
    @NonNull public static androidx.appcompat.app.AlertDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener,
            @Nullable Runnable onDismiss, @Nullable BuiltinWidgetKind kind) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, 0, pad, 0);
        List<TextInputEditText> inputs = new ArrayList<>();
        // The choices' and switches' values as the sheet holds them now: a row shown only for one
        // value of another field follows them live.
        Map<String, String> chosen = new LinkedHashMap<>();
        List<Runnable> visibility = new ArrayList<>();
        Runnable syncVisibility = () -> { for (Runnable sync : visibility) sync.run(); };
        boolean previousSwitch = false;
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
            // Rows are 48dp tall already, so 8dp sets sections apart; switches that follow one
            // another are one section, and the first row sits right under the header.
            boolean isSwitch = field.type == BuiltinWidgetView.ConfigField.Type.SWITCH;
            boolean first = column.getChildCount() == 0;
            params.topMargin = first || previousSwitch && isSwitch ? 0 : Math.round(8 * density);
            previousSwitch = isSwitch;
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

        // Header, the scrolling content, then Save outside the scroll so it is always in reach.
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        // 7dp: the X's glyph, inset in its 48dp target, ends where the content does.
        header.setPaddingRelative(pad, Math.round(8 * density), Math.round(7 * density), 0);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_TitleLarge);
        heading.setTextColor(MaterialColors.getColor(heading,
            com.google.android.material.R.attr.colorOnSurface));
        header.addView(heading, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.ImageButton close = iconButton(context, R.drawable.ic_symbol_close,
            context.getString(R.string.builtin_widget_settings_close), density);
        header.addView(close, new LinearLayout.LayoutParams(Math.round(48 * density),
            Math.round(48 * density)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        // A hairline under the header while the content is scrolled under it.
        View rule = new View(context);
        rule.setBackgroundColor(MaterialColors.getColor(heading,
            com.google.android.material.R.attr.colorOutlineVariant));
        rule.setVisibility(View.INVISIBLE);
        root.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(1, Math.round(density))));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
            rule.setVisibility(y > 0 ? View.VISIBLE : View.INVISIBLE));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f));
        TextView failed = new TextView(context);
        failed.setText(R.string.builtin_widget_settings_save_failed);
        failed.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodySmall);
        failed.setTextColor(MaterialColors.getColor(failed,
            androidx.appcompat.R.attr.colorError));
        failed.setVisibility(View.GONE);
        LinearLayout.LayoutParams failedParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        failedParams.setMargins(pad, Math.round(8 * density), pad, 0);
        root.addView(failed, failedParams);
        MaterialButton save = new MaterialButton(context);
        save.setTag(SAVE_TAG);
        save.setText(R.string.builtin_widget_settings_save);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.setMargins(pad, Math.round(12 * density), pad, Math.round(16 * density));
        root.addView(save, saveParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setView(root)
            .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
            .create();
        close.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            save.setEnabled(false);
            Map<String, String> texts = new LinkedHashMap<>();
            for (TextInputEditText input : inputs) {
                CharSequence text = input.getText();
                texts.put(String.valueOf(input.getTag()), text == null ? "" : text.toString().trim());
            }
            boolean stored = listener.onSaved(BuiltinWidgetHost.configOf(
                savedValues(fields, current, glassPicked.get(), chosen, texts)));
            if (stored) {
                dialog.dismiss();
            } else {
                // Keep the draft: the sheet stays as it is, ready for another try.
                failed.setVisibility(View.VISIBLE);
                save.setEnabled(true);
            }
        });
        dialog.show();
        return dialog;
    }

    /** A 48dp icon button with a content description, tinted like secondary text. */
    @NonNull private static android.widget.ImageButton iconButton(@NonNull Context context,
            @androidx.annotation.DrawableRes int icon, @NonNull CharSequence description,
            float density) {
        android.widget.ImageButton button = new android.widget.ImageButton(context);
        button.setImageResource(icon);
        button.setContentDescription(description);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
            button.setBackgroundResource(ripple.resourceId);
        } else {
            button.setBackground(null);
        }
        button.setImageTintList(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(
            button, com.google.android.material.R.attr.colorOnSurfaceVariant)));
        return button;
    }

    /**
     * What Save writes, before {@link BuiltinWidgetHost#configOf} drops the empty fields. What the
     * sheet does not offer (a widget's own state, kept in its settings) stays as it was; what it
     * offers is written whole, except a switch the record stores no answer for that is still
     * where it opened: its fallback may follow something outside the widget (the Clock's 24-hour
     * time follows the phone's), and writing it would pin that.
     *
     * @param glass what the Glass row holds, or null for a widget without one
     */
    @NonNull static Map<String, String> savedValues(
            @NonNull List<BuiltinWidgetView.ConfigField> fields, @NonNull Bundle current,
            @Nullable String glass, @NonNull Map<String, String> chosen,
            @NonNull Map<String, String> texts) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : current.keySet()) {
            Object kept = current.get(key);
            if (kept instanceof String) values.put(key, (String) kept);
        }
        if (glass != null) BuiltinWidgetGlass.write(values, glass);
        values.putAll(chosen);
        for (BuiltinWidgetView.ConfigField field : fields) {
            if (field.type != BuiltinWidgetView.ConfigField.Type.SWITCH) continue;
            String stored = current.getString(field.key);
            boolean answered = BuiltinWidgetView.ConfigField.ON.equals(stored)
                || BuiltinWidgetView.ConfigField.OFF.equals(stored);
            if (answered || !field.fallback.equals(chosen.get(field.key))) continue;
            values.remove(field.key);
            if (stored != null) values.put(field.key, stored);
        }
        values.putAll(texts);
        return values;
    }

    /**
     * The Glass row: Page / On / Off, and Bare on the widgets that offer it. The supplier gives
     * what is picked, or null when the widget has no such row (Glance, always Bare).
     */
    /** The Glass row's segmented control carries this tag, for tests. */
    static final String GLASS_CHOICE_TAG = "builtin_widget_glass_choice";

    @NonNull static java.util.function.Supplier<String> glassRow(
            @NonNull Context context, @NonNull LinearLayout column, @NonNull Bundle current,
            boolean noFields, float density, @NonNull String[] glassValues) {
        if (glassValues.length == 0) return () -> null;
        // The label on its own line and the segments across the whole row, each the same width:
        // four choices on the label's line left Bare a sliver with no label on a narrow phone.
        LinearLayout glass = new LinearLayout(context);
        glass.setOrientation(LinearLayout.VERTICAL);
        // The heading, and an (i) that opens what the choices mean; the explanation is not
        // always on screen.
        LinearLayout glassHead = new LinearLayout(context);
        glassHead.setOrientation(LinearLayout.HORIZONTAL);
        glassHead.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView glassLabel = new TextView(context);
        glassLabel.setText(R.string.builtin_widget_glass);
        glassLabel.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        glassHead.addView(glassLabel, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.ImageButton help = iconButton(context, R.drawable.ic_ms_info,
            context.getString(R.string.builtin_widget_glass_help), density);
        help.setTag(GLASS_HELP_TAG);
        help.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.builtin_widget_glass)
            .setMessage(R.string.builtin_widget_glass_summary)
            .setPositiveButton(android.R.string.ok, null)
            .show());
        // Pulled out so the glyph, not its 48dp target, ends on the content's edge.
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
            Math.round(48 * density), Math.round(48 * density));
        helpParams.setMarginEnd(-Math.round(14 * density));
        glassHead.addView(help, helpParams);
        glass.addView(glassHead, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButtonToggleGroup glassChoice = new MaterialButtonToggleGroup(context);
        glassChoice.setTag(GLASS_CHOICE_TAG);
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
            button.setSingleLine(true);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            int side = Math.round(4 * density);
            button.setPaddingRelative(side, button.getPaddingTop(), side,
                button.getPaddingBottom());
            glassIds[i] = button.getId();
            glassChoice.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (glassValues[i].equals(storedGlass == null ? "" : storedGlass))
                glassChoice.check(button.getId());
        }
        if (glassChoice.getCheckedButtonId() == View.NO_ID) glassChoice.check(glassIds[0]);
        LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        choiceParams.topMargin = 0;
        glass.addView(glassChoice, choiceParams);
        LinearLayout.LayoutParams glassParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glassParams.topMargin = column.getChildCount() == 0 ? 0 : Math.round(8 * density);
        column.addView(glass, glassParams);
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
     * The Clock's "Additional clocks" section: the heading, how many are set, and a chevron; a
     * tap opens two fields for a city or a time zone ("Tokyo", "Asia/Kolkata") whose helper text
     * shows the city it resolves to, and an empty one is none. Collapsed on open.
     */
    @NonNull private static View zonesRow(@NonNull Context context,
                                          @NonNull BuiltinWidgetView.ConfigField field,
                                          @NonNull Bundle current,
                                          @NonNull List<TextInputEditText> inputs) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(context);
        head.setTag(ZONES_HEAD_TAG);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(Math.round(48 * density));
        head.setClickable(true);
        head.setFocusable(true);
        TextView label = new TextView(context);
        label.setText(field.label);
        label.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyLarge);
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
            1f));
        TextView summary = new TextView(context);
        summary.setTextAppearance(com.google.android.material.R.style
            .TextAppearance_Material3_BodyMedium);
        int muted = MaterialColors.getColor(label,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        summary.setTextColor(muted);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.setMarginStart(Math.round(8 * density));
        head.addView(summary, summaryParams);
        android.widget.ImageView chevron = new android.widget.ImageView(context);
        chevron.setImageResource(R.drawable.ic_editor_expand_more);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
            Math.round(24 * density), Math.round(24 * density));
        chevronParams.setMarginStart(Math.round(8 * density));
        chevronParams.setMarginEnd(-Math.round(6 * density));
        head.addView(chevron, chevronParams);
        column.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout fieldsBox = new LinearLayout(context);
        fieldsBox.setOrientation(LinearLayout.VERTICAL);
        fieldsBox.setVisibility(View.GONE);
        List<TextInputEditText> mine = new ArrayList<>();
        List<TextInputLayout> boxes = new ArrayList<>();
        String[] keys = {field.key, field.secondKey == null ? field.key : field.secondKey};
        int[] hints = {R.string.bw_clocks_zone1_label, R.string.bw_clocks_zone2_label};
        for (int i = 0; i < 2; i++) {
            TextInputLayout box = (TextInputLayout) textBox(context,
                new BuiltinWidgetView.ConfigField(keys[i], context.getString(hints[i]),
                    BuiltinWidgetView.ConfigField.Type.TEXT, ""), current, mine);
            box.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
            box.setPlaceholderText(context.getString(R.string.bw_clock_zone_placeholder));
            boxes.add(box);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(8 * density);
            fieldsBox.addView(box, params);
        }
        inputs.addAll(mine);
        column.addView(fieldsBox);
        Runnable refresh = () -> {
            int set = 0;
            for (int i = 0; i < mine.size(); i++) {
                java.time.ZoneId zone = ClockWidgetFormats.resolveZone(
                    String.valueOf(mine.get(i).getText()));
                if (zone != null) set++;
                boxes.get(i).setHelperText(zone == null ? null
                    : ClockWidgetFormats.cityName(zone.getId()));
            }
            summary.setText(zonesSummary(context, set));
        };
        refresh.run();
        for (TextInputEditText input : mine) {
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(android.text.Editable e) { refresh.run(); }
            });
        }
        head.setOnClickListener(v -> {
            boolean open = fieldsBox.getVisibility() != View.VISIBLE;
            fieldsBox.setVisibility(open ? View.VISIBLE : View.GONE);
            float to = open ? 180f : 0f;
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                chevron.animate().rotation(to).setDuration(150).start();
            } else {
                chevron.setRotation(to);
            }
        });
        return column;
    }

    /** What the Additional clocks heading says is set: "None", else "n configured". */
    @NonNull static String zonesSummary(@NonNull Context context, int configured) {
        if (configured <= 0) return context.getString(R.string.bw_clock_zones_none);
        return context.getResources().getQuantityString(R.plurals.bw_clock_zones_configured,
            configured, configured);
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
        // The whole row is the target, not only the thumb.
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> toggle.toggle());
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
                rim.setColor(selected ? androidx.core.graphics.ColorUtils.setAlphaComponent(
                    primary, 36) : android.graphics.Color.TRANSPARENT);
                rim.setStroke(Math.round((selected ? 2 : 1) * density),
                    selected ? primary : outline);
                frames.get(i).setBackground(rim);
                frames.get(i).setSelected(selected);
                View tile = (View) frames.get(i).getParent();
                tile.setSelected(selected);
                androidx.core.view.ViewCompat.setStateDescription(tile, selected
                    ? tile.getContext().getString(R.string.builtin_widget_option_selected) : null);
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
