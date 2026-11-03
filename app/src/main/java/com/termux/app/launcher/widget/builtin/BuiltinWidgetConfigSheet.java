package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.termux.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings sheet of a built-in widget, a bottom sheet of cards: one card per
 * {@link BuiltinWidgetView.ConfigField} (a text field, a switch, a choice as segments or a strip
 * of live previews, the Clock's time zones; a card shown only while another field holds a value
 * follows it live), seeded from the record's bundle, then the Glass card every built-in carries
 * ({@link BuiltinWidgetGlass}), written back on Save; anything stored that the sheet does not
 * offer is kept. Material by construction, so it follows the scheme like every other sheet.
 */
public final class BuiltinWidgetConfigSheet {
    /** Saves a draft: true once it is stored, false when it could not be (the sheet stays). */
    public interface Listener { boolean onSaved(@NonNull Bundle config); }

    /** The Save button carries this tag, for tests. */
    static final String SAVE_TAG = "builtin_widget_save";
    /** The Cancel button carries this tag, for tests. */
    static final String CANCEL_TAG = "builtin_widget_cancel";
    /** The Time zones heading row carries this tag, for tests. */
    static final String ZONES_HEAD_TAG = "builtin_widget_zones_head";
    /** The Glass row's segmented control carries this tag, for tests. */
    static final String GLASS_CHOICE_TAG = "builtin_widget_glass_choice";

    private BuiltinWidgetConfigSheet() { }

    @NonNull public static BottomSheetDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener) {
        return show(context, title, fields, current, listener, null);
    }

    /** As {@link #show(Context, CharSequence, List, Bundle, Listener)}, with a dismiss callback. */
    @NonNull public static BottomSheetDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener, @Nullable Runnable onDismiss) {
        return show(context, title, fields, current, listener, onDismiss, null);
    }

    /** As above, for {@code kind}: which Glass choices the widget offers. */
    @NonNull public static BottomSheetDialog show(@NonNull Context context,
            @NonNull CharSequence title, @NonNull List<BuiltinWidgetView.ConfigField> fields,
            @NonNull Bundle current, @NonNull Listener listener,
            @Nullable Runnable onDismiss, @Nullable BuiltinWidgetKind kind) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        // Built on the dialog's own themed context, so the cards resolve the same scheme.
        Context themed = dialog.getContext();
        View root = build(themed, title, fields, current, listener, kind, dialog);
        dialog.setContentView(root);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); });
        dialog.show();
        return dialog;
    }

    /** The sheet's content: handle, header, the scrolling cards, then Cancel and Save. */
    @NonNull static View build(@NonNull Context context, @NonNull CharSequence title,
            @NonNull List<BuiltinWidgetView.ConfigField> fields, @NonNull Bundle current,
            @NonNull Listener listener, @Nullable BuiltinWidgetKind kind,
            @NonNull BottomSheetDialog dialog) {
        int side = dp(context, 16);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(side, dp(context, 8), side, dp(context, 8));
        // Hidden inputs hold the text drafts (the Clock's zones); Save reads them as it always did.
        List<TextInputEditText> inputs = new ArrayList<>();
        // The choices' and switches' values as the sheet holds them now: a card shown only for
        // one value of another field follows them live.
        Map<String, String> chosen = new LinkedHashMap<>();
        List<Runnable> visibility = new ArrayList<>();
        Runnable syncVisibility = () -> { for (Runnable sync : visibility) sync.run(); };
        for (BuiltinWidgetView.ConfigField field : fields) {
            final View row;
            switch (field.type) {
                case SWITCH:
                    row = switchCard(context, field, current, chosen, syncVisibility);
                    break;
                case CHOICE:
                    row = choiceCard(context, field, current, chosen, syncVisibility);
                    break;
                case ZONES:
                    row = zonesCard(context, field, current, inputs);
                    break;
                default:
                    row = textCard(context, field, current, inputs);
                    break;
            }
            addCard(column, row);
            if (field.shownWhenKey != null) {
                visibility.add(() -> row.setVisibility(field.shownFor(chosen)
                    ? View.VISIBLE : View.GONE));
            }
        }
        syncVisibility.run();
        // The Glass card (BuiltinWidgetGlass.choices): none for a widget that is always Bare.
        final java.util.function.Supplier<String> glassPicked = glassCard(context, column,
            current, BuiltinWidgetGlass.choices(kind));
        ScrollView scroll = new ScrollView(context);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(column);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(side, 0, side, dp(context, 12));
        TextView heading = text(context, com.google.android.material.R.attr.textAppearanceHeadlineSmall,
            com.google.android.material.R.attr.colorOnSurface);
        heading.setText(title);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(heading);
        TextView subtitle = text(context, com.google.android.material.R.attr.textAppearanceBodyMedium,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        subtitle.setText(kind == BuiltinWidgetKind.CLOCK ? R.string.bw_clock_settings_subtitle
            : R.string.builtin_widget_settings_subtitle);
        subtitle.setPadding(0, dp(context, 4), 0, 0);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f));

        TextView failed = text(context, com.google.android.material.R.attr.textAppearanceBodySmall,
            androidx.appcompat.R.attr.colorError);
        failed.setText(R.string.builtin_widget_settings_save_failed);
        failed.setVisibility(View.GONE);
        LinearLayout.LayoutParams failedParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        failedParams.setMargins(side, dp(context, 4), side, 0);
        root.addView(failed, failedParams);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(side, dp(context, 8), side, dp(context, 16));
        MaterialButton cancel = pill(context, true);
        cancel.setTag(CANCEL_TAG);
        cancel.setText(android.R.string.cancel);
        MaterialButton save = pill(context, false);
        save.setTag(SAVE_TAG);
        save.setText(R.string.builtin_widget_settings_save);
        footer.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
            1f));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveParams.setMarginStart(dp(context, 12));
        footer.addView(save, saveParams);
        root.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        cancel.setOnClickListener(v -> dialog.dismiss());
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
        return root;
    }

    // ----- building blocks ------------------------------------------------------------------

    private static int dp(@NonNull Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int color(@NonNull Context context, @AttrRes int attr) {
        return MaterialColors.getColor(context, attr, android.graphics.Color.GRAY);
    }

    @NonNull private static TextView text(@NonNull Context context, @AttrRes int appearance,
                                          @AttrRes int colorAttr) {
        TextView view = new TextView(context);
        android.util.TypedValue style = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(appearance, style, true)) {
            view.setTextAppearance(style.resourceId);
        }
        view.setTextColor(color(context, colorAttr));
        return view;
    }

    @NonNull private static TextView title(@NonNull Context context, @NonNull CharSequence label) {
        TextView view = text(context, com.google.android.material.R.attr.textAppearanceTitleMedium,
            com.google.android.material.R.attr.colorOnSurface);
        view.setText(label);
        return view;
    }

    @NonNull private static TextView muted(@NonNull Context context, @Nullable CharSequence label) {
        TextView view = text(context, com.google.android.material.R.attr.textAppearanceBodyMedium,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        view.setText(label);
        return view;
    }

    /** A pill button for the footer: outlined Cancel or filled Save. */
    @NonNull private static MaterialButton pill(@NonNull Context context, boolean outlined) {
        // One base style for both: filled is the outlined button with its stroke dropped and the
        // scheme's primary poured in, so the two always share a shape and a text path.
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setCornerRadius(dp(context, 28));
        button.setMinHeight(dp(context, 52));
        if (!outlined) {
            button.setStrokeWidth(0);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                color(context, androidx.appcompat.R.attr.colorPrimary)));
            button.setTextColor(android.content.res.ColorStateList.valueOf(
                color(context, com.google.android.material.R.attr.colorOnPrimary)));
        }
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    /** A rounded section: surface-container fill, a 1dp outline-variant stroke, 16dp inside. */
    @NonNull private static LinearLayout card(@NonNull Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(context, 20,
            color(context, com.google.android.material.R.attr.colorSurfaceContainer),
            1, color(context, com.google.android.material.R.attr.colorOutlineVariant)));
        return card;
    }

    @NonNull private static GradientDrawable rounded(@NonNull Context context, float radiusDp,
            int fill, float strokeDp, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, radiusDp));
        shape.setColor(fill);
        shape.setStroke(Math.max(1, dp(context, strokeDp)), stroke);
        return shape;
    }

    /** Cards sit 8dp apart. */
    private static void addCard(@NonNull LinearLayout column, @NonNull View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = column.getChildCount() == 0 ? 0 : dp(column.getContext(), 8);
        column.addView(card, params);
    }

    @NonNull private static ImageView glyph(@NonNull Context context, @DrawableRes int icon,
                                            int colorAttr) {
        ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setImageTintList(android.content.res.ColorStateList.valueOf(color(context, colorAttr)));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /**
     * A card's head: an optional 24dp glyph, the title with a muted line under it, and whatever
     * trails (a switch, a summary and chevron).
     */
    @NonNull private static LinearLayout head(@NonNull Context context,
            @NonNull BuiltinWidgetView.ConfigField field, @Nullable View trailing) {
        return head(context, field.icon, field.label, field.summary, trailing);
    }

    @NonNull private static LinearLayout head(@NonNull Context context, @DrawableRes int icon,
            @NonNull CharSequence label, @Nullable CharSequence summary, @Nullable View trailing) {
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(context, 48));
        if (icon != 0) {
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 24),
                dp(context, 24));
            iconParams.setMarginEnd(dp(context, 16));
            head.addView(glyph(context, icon,
                com.google.android.material.R.attr.colorOnSurfaceVariant), iconParams);
        }
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(title(context, label));
        if (summary != null) words.addView(muted(context, summary));
        head.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
            1f));
        if (trailing != null) {
            LinearLayout.LayoutParams trailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trailParams.setMarginStart(dp(context, 12));
            head.addView(trailing, trailParams);
        }
        return head;
    }

    // ----- cards ----------------------------------------------------------------------------

    /** A text field, seeded from the record. */
    @NonNull private static View textCard(@NonNull Context context,
                                          @NonNull BuiltinWidgetView.ConfigField field,
                                          @NonNull Bundle current,
                                          @NonNull List<TextInputEditText> inputs) {
        LinearLayout card = card(context);
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
        card.addView(box);
        inputs.add(input);
        return card;
    }

    /** A switch card: glyph, title, muted line and the switch; the whole card toggles it. */
    @NonNull private static View switchCard(@NonNull Context context,
                                            @NonNull BuiltinWidgetView.ConfigField field,
                                            @NonNull Bundle current,
                                            @NonNull Map<String, String> picked,
                                            @NonNull Runnable onChange) {
        MaterialSwitch toggle = new MaterialSwitch(context);
        String start = startValue(field, current);
        picked.put(field.key, start);
        toggle.setChecked(BuiltinWidgetView.ConfigField.ON.equals(start));
        toggle.setContentDescription(field.label);
        LinearLayout card = card(context);
        card.addView(head(context, field, toggle));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> toggle.toggle());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            picked.put(field.key, checked ? BuiltinWidgetView.ConfigField.ON
                : BuiltinWidgetView.ConfigField.OFF);
            onChange.run();
        });
        return card;
    }

    /**
     * A choice card: its title, then a strip of the options drawn as themselves (a live preview)
     * when the field draws them, else segmented buttons across the card.
     */
    @NonNull private static View choiceCard(@NonNull Context context,
                                            @NonNull BuiltinWidgetView.ConfigField field,
                                            @NonNull Bundle current,
                                            @NonNull Map<String, String> picked,
                                            @NonNull Runnable onChange) {
        String start = startValue(field, current);
        picked.put(field.key, start);
        LinearLayout card = card(context);
        // A bare title: nothing to tap in it, so no 48dp row to centre it in.
        LinearLayout head = head(context, field, null);
        head.setMinimumHeight(0);
        card.addView(head);
        BuiltinWidgetView.ConfigField.TileArt art = field.tiles;
        if (art == null) {
            com.google.android.material.button.MaterialButtonToggleGroup group =
                new com.google.android.material.button.MaterialButtonToggleGroup(context);
            group.setSingleSelection(true);
            group.setSelectionRequired(true);
            Map<Integer, String> valueOf = new java.util.HashMap<>();
            for (BuiltinWidgetView.ConfigField.Option option : field.options) {
                MaterialButton button = new MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setId(View.generateViewId());
                button.setText(option.label);
                group.addView(button, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                valueOf.put(button.getId(), option.value);
                if (option.value.equals(start)) group.check(button.getId());
            }
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                String value = valueOf.get(checkedId);
                if (!isChecked || value == null) return;
                picked.put(field.key, value);
                onChange.run();
            });
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            groupParams.topMargin = dp(context, 8);
            card.addView(group, groupParams);
            return card;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int primary = color(context, androidx.appcompat.R.attr.colorPrimary);
        int outline = color(context, com.google.android.material.R.attr.colorOutlineVariant);
        int surface = color(context, com.google.android.material.R.attr.colorSurface);
        List<View> frames = new ArrayList<>();
        List<View> badges = new ArrayList<>();
        List<String> values = new ArrayList<>();
        Runnable dress = () -> {
            for (int i = 0; i < frames.size(); i++) {
                boolean selected = values.get(i).equals(picked.get(field.key));
                frames.get(i).setBackground(rounded(context, 12,
                    android.graphics.Color.TRANSPARENT, selected ? 2 : 1,
                    selected ? primary : outline));
                badges.get(i).setVisibility(selected ? View.VISIBLE : View.GONE);
                View tile = (View) frames.get(i).getParent().getParent();
                tile.setSelected(selected);
                ViewCompat.setStateDescription(tile, selected
                    ? context.getString(R.string.builtin_widget_option_selected) : null);
            }
        };
        for (BuiltinWidgetView.ConfigField.Option option : field.options) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setMinimumWidth(dp(context, 76));
            // The wrapper keeps 6dp round the frame so the badge can sit on its corner unclipped.
            android.widget.FrameLayout wrap = new android.widget.FrameLayout(context);
            android.widget.FrameLayout frame = new android.widget.FrameLayout(context);
            int inset = dp(context, 6);
            frame.setPadding(inset, inset, inset, inset);
            frame.addView(art.make(context, option.value),
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            android.widget.FrameLayout.LayoutParams frameParams =
                new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 64));
            frameParams.setMargins(0, dp(context, 6), dp(context, 6), 0);
            wrap.addView(frame, frameParams);
            ImageView badge = glyph(context, R.drawable.ic_clock_sheet_check_badge,
                androidx.appcompat.R.attr.colorPrimary);
            int pad = dp(context, 3);
            badge.setPadding(pad, pad, pad, pad);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(surface);
            badge.setBackground(disc);
            android.widget.FrameLayout.LayoutParams badgeParams =
                new android.widget.FrameLayout.LayoutParams(dp(context, 22), dp(context, 22),
                    Gravity.TOP | Gravity.END);
            wrap.addView(badge, badgeParams);
            tile.addView(wrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = text(context, com.google.android.material.R.attr.textAppearanceBodyMedium,
                com.google.android.material.R.attr.colorOnSurface);
            name.setText(option.label);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(context, 4);
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
            badges.add(badge);
            values.add(option.value);
            strip.addView(tile, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        dress.run();
        scroll.addView(strip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(scroll, scrollParams);
        return card;
    }

    /**
     * The Clock's "Time zones" card: glyph, title, what is set and a chevron; open at first. Two
     * nested rows (the second and third clock) each open a small dialog for a city or a time zone
     * ("Tokyo", "Asia/Kolkata"). The drafts live in hidden inputs so Save reads them as it always
     * did; an empty one is none.
     */
    @NonNull private static View zonesCard(@NonNull Context context,
                                           @NonNull BuiltinWidgetView.ConfigField field,
                                           @NonNull Bundle current,
                                           @NonNull List<TextInputEditText> inputs) {
        LinearLayout card = card(context);
        LinearLayout trailing = new LinearLayout(context);
        trailing.setOrientation(LinearLayout.HORIZONTAL);
        trailing.setGravity(Gravity.CENTER_VERTICAL);
        TextView summary = text(context, com.google.android.material.R.attr.textAppearanceBodyLarge,
            com.google.android.material.R.attr.colorOnSurface);
        trailing.addView(summary);
        ImageView chevron = glyph(context, R.drawable.ic_editor_expand_more,
            com.google.android.material.R.attr.colorOnSurfaceVariant);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(context, 24),
            dp(context, 24));
        chevronParams.setMarginStart(dp(context, 4));
        trailing.addView(chevron, chevronParams);
        LinearLayout head = head(context, field, trailing);
        head.setTag(ZONES_HEAD_TAG);
        head.setClickable(true);
        head.setFocusable(true);
        card.addView(head);
        // Open at first, the chevron pointing down as in the mockup; closed, it points along.

        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        List<TextInputEditText> mine = new ArrayList<>();
        List<TextView> subtitles = new ArrayList<>();
        String[] keys = {field.key, field.secondKey == null ? field.key : field.secondKey};
        int[] names = {R.string.bw_clocks_zone1_label, R.string.bw_clocks_zone2_label};
        Runnable[] refresh = new Runnable[1];
        for (int i = 0; i < 2; i++) {
            TextInputEditText draft = new TextInputEditText(context);
            draft.setTag(keys[i]);
            String stored = current.getString(keys[i]);
            draft.setText(stored == null ? "" : stored);
            mine.add(draft);
            inputs.add(draft);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(context, 56));
            row.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            row.setBackground(rounded(context, 12, android.graphics.Color.TRANSPARENT, 1,
                color(context, com.google.android.material.R.attr.colorOutlineVariant)));
            row.setClickable(true);
            row.setFocusable(true);
            LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(dp(context, 24),
                dp(context, 24));
            pinParams.setMarginEnd(dp(context, 16));
            row.addView(glyph(context, R.drawable.ic_clock_sheet_pin,
                com.google.android.material.R.attr.colorOnSurfaceVariant), pinParams);
            LinearLayout words = new LinearLayout(context);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(title(context, context.getString(names[i])));
            TextView sub = muted(context, null);
            subtitles.add(sub);
            words.addView(sub);
            row.addView(words, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(glyph(context, R.drawable.ic_clock_sheet_chevron_right,
                com.google.android.material.R.attr.colorOnSurfaceVariant),
                new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
            row.setOnClickListener(v -> editZone(context, context.getString(
                names[mine.indexOf(draft)]), draft, refresh[0]));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(context, 8);
            rows.addView(row, rowParams);
        }
        card.addView(rows);
        refresh[0] = () -> {
            List<String> cities = new ArrayList<>();
            for (int i = 0; i < mine.size(); i++) {
                java.time.ZoneId zone = ClockWidgetFormats.resolveZone(
                    String.valueOf(mine.get(i).getText()));
                String city = zone == null ? null : ClockWidgetFormats.cityName(zone.getId());
                if (city != null) cities.add(city);
                subtitles.get(i).setText(city != null ? city
                    : context.getString(R.string.bw_clock_zone_hint));
            }
            summary.setText(zonesSummary(context, cities));
        };
        refresh[0].run();
        head.setOnClickListener(v -> {
            boolean open = rows.getVisibility() != View.VISIBLE;
            rows.setVisibility(open ? View.VISIBLE : View.GONE);
            float to = open ? 0f : -90f;
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                chevron.animate().rotation(to).setDuration(150).start();
            } else {
                chevron.setRotation(to);
            }
        });
        return card;
    }

    /** The heading's right side: the cities that are set, or None. */
    @NonNull static String zonesSummary(@NonNull Context context, @NonNull List<String> cities) {
        if (cities.isEmpty()) return context.getString(R.string.bw_clock_zones_none);
        return android.text.TextUtils.join(", ", cities);
    }

    /** One text field for a zone: Cancel leaves it, Clear empties it, OK keeps what was typed. */
    private static void editZone(@NonNull Context context, @NonNull CharSequence label,
                                 @NonNull TextInputEditText draft, @NonNull Runnable refresh) {
        TextInputLayout box = new TextInputLayout(context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(context.getString(R.string.bw_clock_zone_hint));
        box.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(draft.getText());
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        Runnable helper = () -> {
            java.time.ZoneId zone = ClockWidgetFormats.resolveZone(String.valueOf(input.getText()));
            box.setHelperText(zone == null ? null : ClockWidgetFormats.cityName(zone.getId()));
        };
        helper.run();
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) { helper.run(); }
        });
        FrameLayoutPad holder = new FrameLayoutPad(context);
        holder.addView(box);
        new MaterialAlertDialogBuilder(context)
            .setTitle(label)
            .setView(holder)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.bw_clock_zone_clear, (d, which) -> {
                draft.setText("");
                refresh.run();
            })
            .setPositiveButton(android.R.string.ok, (d, which) -> {
                draft.setText(String.valueOf(input.getText()).trim());
                refresh.run();
            })
            .show();
    }

    /** A 24dp-padded holder for a dialog's field. */
    private static final class FrameLayoutPad extends android.widget.FrameLayout {
        FrameLayoutPad(@NonNull Context context) {
            super(context);
            int pad = dp(context, 24);
            setPadding(pad, dp(context, 8), pad, 0);
        }
    }

    /**
     * The Glass card: Page / On / Off, and Bare on the widgets that offer it, as full-width
     * segments with a glyph over each label, then what the choices mean. The supplier gives what
     * is picked, or null when the widget has no such card (Glance, always Bare).
     */
    @NonNull static java.util.function.Supplier<String> glassCard(
            @NonNull Context context, @NonNull LinearLayout column, @NonNull Bundle current,
            @NonNull String[] glassValues) {
        if (glassValues.length == 0) return () -> null;
        LinearLayout card = card(context);
        card.addView(head(context, R.drawable.ic_clock_sheet_sparkle,
            context.getString(R.string.builtin_widget_glass),
            context.getString(R.string.builtin_widget_glass_subtitle), null));
        int outline = color(context, com.google.android.material.R.attr.colorOutline);
        int primary = color(context, androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = color(context, com.google.android.material.R.attr.colorOnPrimary);
        int onSurface = color(context, com.google.android.material.R.attr.colorOnSurface);
        LinearLayout control = new LinearLayout(context);
        control.setTag(GLASS_CHOICE_TAG);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setBackground(rounded(context, 16, android.graphics.Color.TRANSPARENT, 1, outline));
        control.setClipToOutline(true);
        String storedGlass = current.getString(BuiltinWidgetGlass.CONFIG_KEY);
        int[] picked = {0};
        for (int i = 0; i < glassValues.length; i++) {
            if (glassValues[i].equals(storedGlass == null ? "" : storedGlass)) picked[0] = i;
        }
        List<LinearLayout> segments = new ArrayList<>();
        List<ImageView> icons = new ArrayList<>();
        List<TextView> labels = new ArrayList<>();
        Runnable dress = () -> {
            for (int i = 0; i < segments.size(); i++) {
                boolean on = i == picked[0];
                segments.get(i).setBackgroundColor(on ? primary : android.graphics.Color.TRANSPARENT);
                segments.get(i).setSelected(on);
                icons.get(i).setImageTintList(android.content.res.ColorStateList.valueOf(
                    on ? onPrimary : onSurface));
                labels.get(i).setTextColor(on ? onPrimary : onSurface);
            }
        };
        for (int i = 0; i < glassValues.length; i++) {
            final int index = i;
            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(outline);
                control.addView(divider, new LinearLayout.LayoutParams(Math.max(1, dp(context, 1)),
                    ViewGroup.LayoutParams.MATCH_PARENT));
            }
            LinearLayout segment = new LinearLayout(context);
            segment.setOrientation(LinearLayout.VERTICAL);
            segment.setGravity(Gravity.CENTER);
            segment.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 12));
            ImageView icon = glyph(context, glassGlyph(glassValues[i]),
                com.google.android.material.R.attr.colorOnSurface);
            segment.addView(icon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
            TextView label = text(context, com.google.android.material.R.attr.textAppearanceBodyMedium,
                com.google.android.material.R.attr.colorOnSurface);
            label.setText(glassLabel(glassValues[i]));
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(context, 4);
            segment.addView(label, labelParams);
            segment.setClickable(true);
            segment.setFocusable(true);
            segment.setContentDescription(label.getText());
            segment.setOnClickListener(v -> { picked[0] = index; dress.run(); });
            segments.add(segment);
            icons.add(icon);
            labels.add(label);
            control.addView(segment, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        dress.run();
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.topMargin = dp(context, 8);
        card.addView(control, controlParams);
        TextView summary = muted(context, context.getString(R.string.builtin_widget_glass_summary));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(context, 12);
        card.addView(summary, summaryParams);
        addCard(column, card);
        return () -> glassValues[picked[0]];
    }

    @DrawableRes private static int glassGlyph(@NonNull String value) {
        if (BuiltinWidgetGlass.ON.equals(value)) return R.drawable.ic_clock_sheet_glass_on;
        if (BuiltinWidgetGlass.OFF.equals(value)) return R.drawable.ic_clock_sheet_glass_off;
        if (BuiltinWidgetGlass.BARE.equals(value)) return R.drawable.ic_clock_sheet_glass_bare;
        return R.drawable.ic_clock_sheet_glass_page;
    }

    @StringRes private static int glassLabel(@NonNull String value) {
        if (BuiltinWidgetGlass.ON.equals(value)) return R.string.builtin_widget_glass_on;
        if (BuiltinWidgetGlass.OFF.equals(value)) return R.string.builtin_widget_glass_off;
        if (BuiltinWidgetGlass.BARE.equals(value)) return R.string.builtin_widget_glass_bare;
        return R.string.builtin_widget_glass_follow;
    }

    // ----- what Save writes -----------------------------------------------------------------

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


    /** True when {@code value} parses as a positive number. */
    public static boolean isPositiveNumber(@Nullable String value) {
        if (value == null) return false;
        try { return Integer.parseInt(value.trim()) > 0; } catch (NumberFormatException e) { return false; }
    }
}
