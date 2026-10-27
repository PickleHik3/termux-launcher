package com.termux.app.terminal.io;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.material.M3;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.font.NerdFontSpans;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The "Choose a key" sheet: the one place a key's action is chosen, for a new key, a tap and a
 * swipe up alike.
 *
 * <p>It offers the vocabularies the row accepts: terminal keys and symbols, grouped by category
 * chips and drawn as a grid of tiles; launcher actions from {@link LauncherToolRegistry}, written
 * as {@code tool:<name>[:arg=value]}; and anything typed in the search field, which is always
 * offered back as literal text (and as a macro when it has spaces). That typed row is how a
 * key value that is in no list gets onto a key.
 *
 * <p>A tile only selects; the confirm button at the bottom takes it, so a mis-tap costs nothing.
 * The caller may pass an {@code onClosed} runnable, invoked exactly once when the sheet (and any
 * follow-up argument prompt) is gone, so an owning sheet can hide while this one is up.
 */
public final class ExtraKeyActionPicker {

    /** What the picker hands back: the value to store plus a display suggestion. */
    public interface OnPicked {
        void onPicked(@NonNull ExtraKeysLayoutModel.Key key);
    }

    private static final int SPAN = 4;
    private static final int TILE_MIN_DP = 56;
    private static final int GAP_DP = 8;

    private static final int CELL_HEADER = 0;
    private static final int CELL_TILE = 1;
    private static final int CELL_WIDE = 2;

    /** One thing that can be selected: what to call it, and how to turn it into a key. */
    private static final class Choice {
        final String id;
        final String label;
        @Nullable final ExtraKeysLayoutModel.Key key;
        @Nullable final LauncherToolRegistry.ToolMetadata promptTool;
        @Nullable final String promptArgument;
        @Nullable final String promptLabel;

        Choice(String id, String label, @Nullable ExtraKeysLayoutModel.Key key) {
            this.id = id;
            this.label = label;
            this.key = key;
            this.promptTool = null;
            this.promptArgument = null;
            this.promptLabel = null;
        }

        Choice(String id, String label, @NonNull LauncherToolRegistry.ToolMetadata tool,
               @NonNull String argument, @NonNull String promptLabel) {
            this.id = id;
            this.label = label;
            this.key = null;
            this.promptTool = tool;
            this.promptArgument = argument;
            this.promptLabel = promptLabel;
        }
    }

    private interface OnSelect {
        void onSelect(@NonNull Choice choice);
    }

    /** One cell of the grid: a section header, a square tile or a full-width row. */
    private static final class Cell {
        final int type;
        final String label;
        @Nullable final String summary;
        @Nullable final Choice choice;

        Cell(int type, String label, @Nullable String summary, @Nullable Choice choice) {
            this.type = type;
            this.label = label;
            this.summary = summary;
            this.choice = choice;
        }
    }

    private final Context context;
    private final float density;
    private final int colorText;
    private final int colorSubtle;

    @Nullable private Runnable onClosed;
    @Nullable private String subtitle;
    @StringRes private int confirmRes = R.string.settings_extra_keys_picker_confirm_use;
    /** True while an argument prompt owns the close callback instead of the picker sheet. */
    private boolean handedOffToPrompt;

    private ExtraKeyActionPicker.Category category = Category.COMMON;
    private String query = "";
    @Nullable private Choice selected;

    private enum Category {
        COMMON(ExtraKeyPickerCatalog.Category.COMMON, R.string.settings_extra_keys_picker_common),
        NAVIGATION(ExtraKeyPickerCatalog.Category.NAVIGATION,
            R.string.settings_extra_keys_picker_navigation),
        SYMBOLS(ExtraKeyPickerCatalog.Category.SYMBOLS, R.string.settings_extra_keys_picker_symbols),
        OTHER(ExtraKeyPickerCatalog.Category.OTHER, R.string.settings_extra_keys_picker_other),
        ACTIONS(ExtraKeyPickerCatalog.Category.ACTIONS, R.string.settings_extra_keys_picker_actions);

        final ExtraKeyPickerCatalog.Category catalog;
        @StringRes final int title;

        Category(ExtraKeyPickerCatalog.Category catalog, int title) {
            this.catalog = catalog;
            this.title = title;
        }
    }

    public ExtraKeyActionPicker(@NonNull Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = M3.onSurface(context);
        this.colorSubtle = M3.onSurfaceVariant(context);
    }

    public static void show(@NonNull Context context, @NonNull OnPicked onPicked) {
        show(context, onPicked, null);
    }

    public static void show(@NonNull Context context, @NonNull OnPicked onPicked,
                            @Nullable Runnable onClosed) {
        show(context, null, R.string.settings_extra_keys_picker_confirm_use, onPicked, onClosed);
    }

    /**
     * @param subtitle   a line under the title ("Adding to Row 2"), or null.
     * @param confirmRes the confirm button's text with the chosen key as its one argument.
     */
    public static void show(@NonNull Context context, @Nullable String subtitle,
                            @StringRes int confirmRes, @NonNull OnPicked onPicked,
                            @Nullable Runnable onClosed) {
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context);
        picker.onClosed = onClosed;
        picker.subtitle = subtitle;
        picker.confirmRes = confirmRes;
        picker.open(onPicked);
    }

    public void open(@NonNull OnPicked onPicked) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(pad(16), 0, pad(16), 0);
        root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headerRow = row();
        TextView titleView = text(R.string.settings_extra_keys_action_picker_title,
            com.google.android.material.R.attr.textAppearanceTitleLarge, colorText);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerRow.addView(titleView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton close = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        close.setIconResource(R.drawable.ic_symbol_close);
        close.setContentDescription(context.getString(R.string.settings_extra_keys_action_back));
        close.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(close, new LinearLayout.LayoutParams(pad(48), pad(48)));
        root.addView(headerRow);

        if (subtitle != null) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            M3.textAppearance(sub, com.google.android.material.R.attr.textAppearanceBodyMedium);
            sub.setTextColor(colorSubtle);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(sub);
        }

        TextInputLayout searchLayout = new TextInputLayout(context);
        searchLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        searchLayout.setHint(context.getString(R.string.settings_extra_keys_action_search_hint));
        searchLayout.setStartIconDrawable(R.drawable.ic_symbol_search);
        TextInputEditText search = new TextInputEditText(searchLayout.getContext());
        search.setSingleLine(true);
        searchLayout.addView(search, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = pad(8);
        root.addView(searchLayout, searchParams);

        GridAdapter adapter = new GridAdapter();
        MaterialButton confirm = new MaterialButton(context);
        Runnable refreshConfirm = () -> {
            confirm.setEnabled(selected != null);
            confirm.setText(selected == null
                ? context.getString(R.string.settings_extra_keys_picker_confirm_none)
                : context.getString(confirmRes, selected.label));
        };

        ChipGroup chips = new ChipGroup(context);
        chips.setSingleLine(true);
        chips.setSingleSelection(true);
        chips.setSelectionRequired(false);
        chips.setChipSpacingHorizontal(pad(8));
        for (Category entry : Category.values()) {
            Chip chip = new Chip(context);
            chip.setText(entry.title);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            chip.setChecked(entry == category);
            chip.setOnClickListener(v -> {
                category = entry;
                for (int i = 0; i < chips.getChildCount(); i++) {
                    ((Chip) chips.getChildAt(i)).setChecked(chips.getChildAt(i) == v);
                }
                adapter.rebuild();
            });
            chips.addView(chip);
        }
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.topMargin = pad(4);
        root.addView(chipScroll, chipParams);

        RecyclerView grid = new RecyclerView(context);
        GridLayoutManager manager = new GridLayoutManager(context, SPAN);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.cells.get(position).type == CELL_TILE ? 1 : SPAN;
            }
        });
        grid.setLayoutManager(manager);
        grid.setAdapter(adapter);
        grid.setClipToPadding(false);
        grid.setPadding(0, pad(8), 0, pad(8));
        root.addView(grid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = row();
        bar.setPadding(0, pad(8), 0, pad(12));
        MaterialButton cancel = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        bar.addView(cancel);
        bar.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        confirm.setSingleLine(true);
        confirm.setEllipsize(TextUtils.TruncateAt.END);
        confirm.setOnClickListener(v -> take(dialog, onPicked));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confirmParams.setMarginStart(pad(12));
        bar.addView(confirm, confirmParams);
        root.addView(bar);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(pad(16), 0, pad(16), bottom);
            return insets;
        });

        adapter.onSelect = choice -> {
            selected = choice;
            adapter.notifyDataSetChanged();
            refreshConfirm.run();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                adapter.rebuild();
                grid.scrollToPosition(0);
            }
        });
        adapter.rebuild();
        refreshConfirm.run();

        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(params);
            }
        }
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        dialog.setOnDismissListener(d -> {
            if (!handedOffToPrompt && onClosed != null) onClosed.run();
        });
        dialog.show();
    }

    /** Confirm: a plain choice goes straight back; a tool that wants a value asks for it first. */
    private void take(@NonNull BottomSheetDialog dialog, @NonNull OnPicked onPicked) {
        Choice choice = selected;
        if (choice == null) return;
        if (choice.key != null) {
            dialog.dismiss();
            onPicked.onPicked(choice.key);
            return;
        }
        if (choice.promptTool != null && choice.promptArgument != null && choice.promptLabel != null) {
            // The picker sheet leaves before the argument prompt appears, so the two are never
            // stacked; the prompt inherits the close callback.
            handedOffToPrompt = true;
            dialog.dismiss();
            promptForArgument(choice.promptTool, choice.promptArgument, choice.promptLabel, onPicked);
        }
    }

    // ------------------------------------------------------------------ cells

    @NonNull
    private List<Cell> buildCells() {
        List<Cell> cells = new ArrayList<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (!needle.isEmpty()) {
            // Anything typed is also usable as-is: the literal always leads the results, and a
            // space-separated value can additionally become a macro.
            String typed = query.trim();
            cells.add(new Cell(CELL_WIDE,
                context.getString(R.string.settings_extra_keys_action_send_text, typed), null,
                new Choice("text:" + typed, typed, new ExtraKeysLayoutModel.Key(typed))));
            if (typed.contains(" ")) {
                cells.add(new Cell(CELL_WIDE, typed,
                    context.getString(R.string.settings_extra_keys_action_macro_summary),
                    new Choice("macro:" + typed, typed,
                        new ExtraKeysLayoutModel.Key(typed, true, null, null))));
            }
            List<String> keys = ExtraKeyPickerCatalog.search(needle);
            if (!keys.isEmpty()) {
                cells.add(new Cell(CELL_HEADER,
                    context.getString(R.string.settings_extra_keys_action_keys), null, null));
                addKeyTiles(cells, keys);
            }
            addActionCells(cells, needle);
        } else if (category == Category.ACTIONS) {
            addActionCells(cells, "");
        } else {
            addKeyTiles(cells, ExtraKeyPickerCatalog.keys(category.catalog));
        }
        if (cells.isEmpty()) {
            cells.add(new Cell(CELL_HEADER,
                context.getString(R.string.settings_extra_keys_action_no_match), null, null));
        }
        return cells;
    }

    private void addKeyTiles(@NonNull List<Cell> cells, @NonNull List<String> keys) {
        for (String key : keys) {
            cells.add(new Cell(CELL_TILE, key, null,
                new Choice("key:" + key, key, new ExtraKeysLayoutModel.Key(key))));
        }
    }

    private void addActionCells(@NonNull List<Cell> cells, @NonNull String needle) {
        Map<String, List<LauncherToolRegistry.ToolMetadata>> grouped =
            LauncherToolRegistry.getInstance().getUiToolsByCategory();
        for (Map.Entry<String, List<LauncherToolRegistry.ToolMetadata>> group : grouped.entrySet()) {
            boolean groupHeaderWritten = false;
            for (LauncherToolRegistry.ToolMetadata tool : group.getValue()) {
                // No title is what marks a tool agent-only, the same rule the command palette
                // reads: a state query has nothing to put on a key cap.
                if (tool.titleRes == 0) continue;
                // A key carries one action and no keyboard, so an action that insists on two
                // typed values can never be bound right from here.
                if (requiredArgumentCount(tool.schema) > 1) continue;
                String label = context.getString(tool.titleRes);
                String required = firstRequiredArgument(tool.schema);
                List<String> choices = enumChoices(tool.schema, required);
                if (!matches(tool.name, needle) && !matches(label, needle)
                    && !matches(group.getKey(), needle)
                    && !matchesAny(choices, needle)) continue;
                if (!groupHeaderWritten) {
                    cells.add(new Cell(CELL_HEADER, group.getKey(), null, null));
                    groupHeaderWritten = true;
                }
                if (choices != null) {
                    // A fixed set of values is a row each - "Pane layout · grid" - so binding one
                    // is picking rather than typing a value the user would have to already know.
                    for (String choice : choices) {
                        String choiceLabel = label + " · " + choice;
                        cells.add(new Cell(CELL_WIDE, choiceLabel,
                            tool.name + ":" + required + "=" + choice,
                            new Choice("tool:" + tool.name + ":" + choice, choiceLabel,
                                toolKey(tool.name, required, choice, choiceLabel))));
                    }
                } else if (required == null) {
                    cells.add(new Cell(CELL_WIDE, label, tool.name,
                        new Choice("tool:" + tool.name, label,
                            toolKey(tool.name, null, null, label))));
                } else {
                    cells.add(new Cell(CELL_WIDE, label, tool.name + " · " + required,
                        new Choice("tool:" + tool.name, label, tool, required, label)));
                }
            }
        }
    }

    private final class GridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Cell> cells = new ArrayList<>();
        OnSelect onSelect = choice -> { };

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        void rebuild() {
            cells = buildCells();
            notifyDataSetChanged();
        }

        @Override public int getItemViewType(int position) {
            return cells.get(position).type;
        }

        @Override public int getItemCount() {
            return cells.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            TextView view = new TextView(context);
            view.setTextColor(colorText);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (type == CELL_HEADER) {
                M3.textAppearance(view,
                    com.google.android.material.R.attr.textAppearanceLabelMedium);
                view.setTextColor(colorSubtle);
                view.setAllCaps(true);
                view.setPadding(pad(2), pad(12), 0, pad(4));
            } else {
                M3.textAppearance(view, com.google.android.material.R.attr.textAppearanceLabelLarge);
                view.setTextColor(colorText);
                view.setMinHeight(pad(TILE_MIN_DP));
                view.setMaxLines(type == CELL_TILE ? 2 : 3);
                view.setEllipsize(TextUtils.TruncateAt.END);
                params.setMargins(pad(GAP_DP / 2f), pad(GAP_DP / 2f), pad(GAP_DP / 2f),
                    pad(GAP_DP / 2f));
                if (type == CELL_TILE) {
                    view.setGravity(Gravity.CENTER);
                    view.setPadding(pad(4), pad(4), pad(4), pad(4));
                } else {
                    view.setGravity(Gravity.CENTER_VERTICAL);
                    view.setPadding(pad(14), pad(8), pad(14), pad(8));
                }
            }
            view.setLayoutParams(params);
            return new RecyclerView.ViewHolder(view) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Cell cell = cells.get(position);
            TextView view = (TextView) holder.itemView;
            if (cell.type == CELL_HEADER) {
                view.setText(cell.label);
                return;
            }
            boolean isSelected = selected != null && cell.choice != null
                && selected.id.equals(cell.choice.id);
            view.setText(cell.summary == null ? NerdFontSpans.span(context, cell.label)
                : cell.label + "\n" + cell.summary);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(pad(12));
            background.setColor(isSelected
                ? M3.stateLayer(M3.primary(context), 0.16f) : M3.surfaceContainerHigh(context));
            background.setStroke(Math.max(1, pad(isSelected ? 2 : 1)),
                isSelected ? M3.primary(context) : M3.outlineVariant(context));
            view.setBackground(background);
            view.setSelected(isSelected);
            view.setOnClickListener(v -> {
                if (cell.choice != null) onSelect.onSelect(cell.choice);
            });
        }
    }

    // ----------------------------------------------------------------- prompt

    /** Tools with a required argument get one prompt; the value is folded into the key spec. */
    private void promptForArgument(@NonNull LauncherToolRegistry.ToolMetadata tool,
                                   @NonNull String argument, @NonNull String label,
                                   @NonNull OnPicked onPicked) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(pad(20), 0, pad(20), pad(20));
        root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(text(label, com.google.android.material.R.attr.textAppearanceTitleLarge,
            colorText));
        root.addView(text(argument, com.google.android.material.R.attr.textAppearanceLabelMedium,
            colorSubtle));
        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setHint(tool.name + ":" + argument + "=…");
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        inputLayout.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(inputLayout);
        MaterialButton ok = new MaterialButton(context);
        ok.setText(android.R.string.ok);
        ok.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            dialog.dismiss();
            onPicked.onPicked(toolKey(tool.name, argument, value.isEmpty() ? null : value, label));
        });
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okParams.gravity = Gravity.END;
        okParams.topMargin = pad(12);
        root.addView(ok, okParams);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (onClosed != null) onClosed.run();
        });
        dialog.show();
    }

    @NonNull
    private static ExtraKeysLayoutModel.Key toolKey(@NonNull String toolName,
                                                    @Nullable String argument,
                                                    @Nullable String value,
                                                    @NonNull String label) {
        String spec = "tool:" + toolName;
        if (argument != null && value != null) spec += ":" + argument + "=" + value;
        // A tool with a glyph of its own wears it; otherwise the label seeds the display text and
        // the user can shorten it in the key sheet.
        String glyph = ExtraKeyActionLabels.defaultCapGlyph(toolName);
        return new ExtraKeysLayoutModel.Key(spec, false, glyph != null ? glyph : label, null);
    }

    /** How many values a tool insists on, so the ones a key cannot supply stay out. */
    private static int requiredArgumentCount(@Nullable JSONObject schema) {
        if (schema == null) return 0;
        JSONArray required = schema.optJSONArray("required");
        return required == null ? 0 : required.length();
    }

    /** The fixed values an argument accepts, or null when it is free text. */
    @Nullable
    private static List<String> enumChoices(@Nullable JSONObject schema,
                                            @Nullable String argument) {
        if (schema == null || argument == null) return null;
        JSONObject properties = schema.optJSONObject("properties");
        JSONObject property = properties == null ? null : properties.optJSONObject(argument);
        JSONArray values = property == null ? null : property.optJSONArray("enum");
        if (values == null || values.length() == 0) return null;
        List<String> choices = new ArrayList<>(values.length());
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) choices.add(value);
        }
        return choices.isEmpty() ? null : choices;
    }

    private static boolean matchesAny(@Nullable List<String> candidates, @NonNull String needle) {
        if (candidates == null || needle.isEmpty()) return false;
        for (String candidate : candidates) if (matches(candidate, needle)) return true;
        return false;
    }

    @Nullable
    private static String firstRequiredArgument(@Nullable JSONObject schema) {
        if (schema == null) return null;
        JSONArray required = schema.optJSONArray("required");
        if (required == null || required.length() == 0) return null;
        String name = required.optString(0, null);
        return name == null || name.isEmpty() ? null : name;
    }

    private static boolean matches(@NonNull String candidate, @NonNull String needle) {
        return needle.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(needle);
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(@StringRes int res, int appearanceAttr, int color) {
        return text(context.getString(res), appearanceAttr, color);
    }

    private TextView text(@NonNull String value, int appearanceAttr, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        M3.textAppearance(view, appearanceAttr);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private int pad(float dp) {
        return Math.round(dp * density);
    }
}
