package com.termux.app.terminal.io;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one place a key's action is chosen, shared by the extra-keys row editor and reusable by any
 * later surface that binds an action (bindings editor, keyboard layout editor).
 *
 * <p>It offers the same three vocabularies the row already accepts: terminal keys
 * ({@link ExtraKeysConstants#PRIMARY_KEY_CODES_FOR_STRINGS}), launcher actions from
 * {@link LauncherToolRegistry} written as {@code tool:<name>[:arg=value]}, and literal text or a
 * macro. Registry actions are listed rather than typed because {@code tool:} specs are otherwise
 * only discoverable from the web wiki.
 *
 * <p>The caller may pass an {@code onClosed} runnable, invoked exactly once when the picker (and
 * any follow-up argument prompt) is gone — the row editor hides its own sheet while the picker is
 * up so two sheets never stack (F-05).
 */
public final class ExtraKeyActionPicker {

    /** What the picker hands back: the value to store plus a display suggestion. */
    public interface OnPicked {
        void onPicked(@NonNull ExtraKeysLayoutModel.Key key);
    }

    private final Context context;
    private final float density;
    private final int colorText;
    private final int colorSubtle;
    private final int colorOutline;
    private final int colorPanel;

    @Nullable private Runnable onClosed;
    /** True while an argument prompt owns the close callback instead of the picker sheet. */
    private boolean handedOffToPrompt;

    public ExtraKeyActionPicker(@NonNull Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        this.colorSubtle = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFB0B0B0);
        this.colorOutline = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOutlineVariant, 0x33FFFFFF);
        this.colorPanel = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh, 0xFF202837);
    }

    public static void show(@NonNull Context context, @NonNull OnPicked onPicked) {
        show(context, onPicked, null);
    }

    public static void show(@NonNull Context context, @NonNull OnPicked onPicked,
                            @Nullable Runnable onClosed) {
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context);
        picker.onClosed = onClosed;
        picker.open(onPicked);
    }

    public void open(@NonNull OnPicked onPicked) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(pad(20), pad(12), pad(20), 0);
        android.graphics.drawable.GradientDrawable sheetBackground =
            new android.graphics.drawable.GradientDrawable();
        sheetBackground.setCornerRadii(new float[] {
            pad(20), pad(20), pad(20), pad(20), 0, 0, 0, 0});
        sheetBackground.setColor(withAlpha(colorPanel, 0xF7));
        root.setBackground(sheetBackground);

        // Fixed header: back arrow + title, then the sticky search field. Only results scroll.
        LinearLayout headerRow = row();
        TextView back = new TextView(context);
        back.setText("←");
        back.setContentDescription(context.getString(R.string.settings_extra_keys_action_back));
        back.setTextColor(colorText);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        back.setGravity(Gravity.CENTER);
        back.setMinimumWidth(pad(48));
        back.setMinimumHeight(pad(48));
        back.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(back);
        TextView titleView = title(context.getString(
            R.string.settings_extra_keys_action_picker_title));
        headerRow.addView(titleView);
        root.addView(headerRow);

        LinearLayout searchRow = row();
        EditText search = new EditText(context);
        search.setHint(R.string.settings_extra_keys_action_search_hint);
        search.setSingleLine(true);
        search.setTextColor(colorText);
        search.setHintTextColor(colorSubtle);
        search.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchRow.addView(search);
        TextView clear = new TextView(context);
        clear.setText("×");
        clear.setContentDescription(context.getString(
            R.string.settings_extra_keys_action_clear_search));
        clear.setTextColor(colorSubtle);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        clear.setGravity(Gravity.CENTER);
        clear.setMinimumWidth(pad(48));
        clear.setMinimumHeight(pad(48));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> search.setText(""));
        searchRow.addView(clear);
        root.addView(searchRow);

        LinearLayout results = column();
        ScrollView scroller = new ScrollView(context);
        scroller.addView(results);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroller.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });
        root.addView(scroller);

        OnPicked pick = key -> {
            dialog.dismiss();
            onPicked.onPicked(key);
        };
        renderResults(results, "", pick, dialog, onPicked);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                renderResults(results, s.toString(), pick, dialog, onPicked);
                scroller.scrollTo(0, 0);
            }
        });

        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(0x00000000);
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

    private void renderResults(@NonNull LinearLayout results, @NonNull String query,
                               @NonNull OnPicked pick, @NonNull BottomSheetDialog dialog,
                               @NonNull OnPicked rawOnPicked) {
        results.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);

        if (!needle.isEmpty()) {
            // Anything typed is also usable as-is: the literal always leads the results (F-10),
            // a space-separated value can additionally become a macro.
            String typed = query.trim();
            results.addView(entry(
                context.getString(R.string.settings_extra_keys_action_send_text, typed), null,
                () -> pick.onPicked(new ExtraKeysLayoutModel.Key(typed))));
            if (typed.contains(" ")) {
                results.addView(entry(typed,
                    context.getString(R.string.settings_extra_keys_action_macro_summary),
                    () -> pick.onPicked(
                        new ExtraKeysLayoutModel.Key(typed, true, null, null))));
            }
        }

        // The row's modifier toggles are not terminal key codes, so they were unreachable here
        // except by typing the name and choosing "send as text" (issue #22).
        renderNamedKeys(results, context.getString(R.string.settings_extra_keys_action_modifiers),
            ExtraKeysPresets.MODIFIER_KEYS, needle, pick);

        List<String> keys = new ArrayList<>(
            ExtraKeysConstants.PRIMARY_KEY_CODES_FOR_STRINGS.keySet());
        // Natural order so F1..F9 precede F10..F12 (F-11).
        Collections.sort(keys, ExtraKeyActionPicker::compareNatural);
        boolean headerWritten = false;
        for (String key : keys) {
            if (!matches(key, needle)) continue;
            if (!headerWritten) {
                results.addView(header(context.getString(R.string.settings_extra_keys_action_keys)));
                headerWritten = true;
            }
            results.addView(entry(key, null,
                () -> pick.onPicked(new ExtraKeysLayoutModel.Key(key))));
        }

        renderNamedKeys(results, context.getString(R.string.settings_extra_keys_action_row_keys),
            ExtraKeysPresets.ROW_KEYS, needle, pick);

        Map<String, List<LauncherToolRegistry.ToolMetadata>> grouped =
            LauncherToolRegistry.getInstance().getUiToolsByCategory();
        for (Map.Entry<String, List<LauncherToolRegistry.ToolMetadata>> group : grouped.entrySet()) {
            boolean groupHeaderWritten = false;
            for (LauncherToolRegistry.ToolMetadata tool : group.getValue()) {
                String label = tool.titleRes != 0 ? context.getString(tool.titleRes) : tool.name;
                if (!matches(tool.name, needle) && !matches(label, needle)
                    && !matches(group.getKey(), needle)) continue;
                if (!groupHeaderWritten) {
                    results.addView(header(group.getKey()));
                    groupHeaderWritten = true;
                }
                String required = firstRequiredArgument(tool.schema);
                results.addView(entry(label, tool.name + (required == null ? "" : " · " + required),
                    () -> {
                        if (required == null) {
                            pick.onPicked(toolKey(tool.name, null, null, label));
                        } else {
                            // The picker sheet leaves before the argument prompt appears, so the
                            // two are never stacked; the prompt inherits the close callback.
                            handedOffToPrompt = true;
                            dialog.dismiss();
                            promptForArgument(tool, required, label, rawOnPicked);
                        }
                    }));
            }
        }

        if (results.getChildCount() == 0) {
            results.addView(header(context.getString(R.string.settings_extra_keys_action_no_match)));
        }
    }

    /** A titled group of plain key names, written only when at least one matches the search. */
    private void renderNamedKeys(@NonNull LinearLayout results, @NonNull String title,
                                 @NonNull String[] names, @NonNull String needle,
                                 @NonNull OnPicked pick) {
        boolean headerWritten = false;
        for (String name : names) {
            if (!matches(name, needle)) continue;
            if (!headerWritten) {
                results.addView(header(title));
                headerWritten = true;
            }
            results.addView(entry(name, null,
                () -> pick.onPicked(new ExtraKeysLayoutModel.Key(name))));
        }
    }

    /** Tools with a required argument get one prompt; the value is folded into the key spec. */
    private void promptForArgument(@NonNull LauncherToolRegistry.ToolMetadata tool,
                                   @NonNull String argument, @NonNull String label,
                                   @NonNull OnPicked onPicked) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(pad(20), pad(16), pad(20), pad(20));
        root.addView(title(label));
        root.addView(header(argument));
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(colorText);
        input.setHintTextColor(colorSubtle);
        input.setHint(tool.name + ":" + argument + "=…");
        root.addView(input);
        View confirm = entry(context.getString(android.R.string.ok), null, () -> {
            String value = input.getText().toString().trim();
            dialog.dismiss();
            onPicked.onPicked(toolKey(tool.name, argument, value.isEmpty() ? null : value, label));
        });
        root.addView(confirm);
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
        // Tool specs have no sensible glyph of their own, so the label seeds the display text and
        // the user can shorten it in the key sheet.
        return new ExtraKeysLayoutModel.Key(spec, false, label, null);
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

    /** Letter prefix first, numeric suffix as a number: F1..F9 before F10 (F-11). */
    private static int compareNatural(@NonNull String a, @NonNull String b) {
        int prefixCompare = alphaPart(a).compareToIgnoreCase(alphaPart(b));
        if (prefixCompare != 0) return prefixCompare;
        long numberCompare = numericSuffix(a) - numericSuffix(b);
        if (numberCompare != 0) return numberCompare < 0 ? -1 : 1;
        return a.compareTo(b);
    }

    @NonNull
    private static String alphaPart(@NonNull String value) {
        int end = value.length();
        while (end > 0 && Character.isDigit(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private static long numericSuffix(@NonNull String value) {
        int start = value.length();
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) start--;
        if (start == value.length()) return -1;
        try {
            return Long.parseLong(value.substring(start));
        } catch (NumberFormatException e) {
            return -1;
        }
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

    private TextView title(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(colorText);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        view.setPadding(pad(4), 0, 0, 0);
        return view;
    }

    private TextView header(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setAllCaps(true);
        view.setTextColor(colorSubtle);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setPadding(0, pad(12), 0, pad(4));
        return view;
    }

    /** One result: human label primary, internal identifier secondary and quieter (F-11). */
    private View entry(@NonNull String label, @Nullable String summary,
                       @NonNull Runnable onClick) {
        LinearLayout entry = column();
        entry.setMinimumHeight(pad(48));
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setPadding(pad(8), pad(8), pad(8), pad(8));
        entry.setOnClickListener(v -> onClick.run());
        entry.setBackground(rowDivider());

        TextView primary = new TextView(context);
        primary.setText(label);
        primary.setTextColor(colorText);
        primary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        entry.addView(primary);

        if (summary != null && !summary.isEmpty()) {
            TextView secondary = new TextView(context);
            secondary.setText(summary);
            secondary.setTextColor(colorSubtle);
            secondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            entry.addView(secondary);
        }
        return entry;
    }

    private android.graphics.drawable.Drawable rowDivider() {
        android.graphics.drawable.GradientDrawable drawable =
            new android.graphics.drawable.GradientDrawable();
        drawable.setColor(0x00000000);
        drawable.setStroke(Math.max(1, pad(0.5f)), colorOutline);
        drawable.setCornerRadius(pad(10));
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int pad(float dp) {
        return Math.round(dp * density);
    }
}
