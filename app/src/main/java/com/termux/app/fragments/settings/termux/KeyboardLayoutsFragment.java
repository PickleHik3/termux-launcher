package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Picks the layouts the in-app keyboard hot-swaps between, and the order it walks them in.
 *
 * <p>Two lists, because they answer two different questions. The cycle at the top is short,
 * ordered and editable in place — it is what a bound key steps through. The catalogue below is
 * every layout the app ships, searchable, because ninety-one entries is not a list anyone
 * scrolls.
 *
 * <p>Every edit persists immediately. The live keyboard re-reads the ring in
 * {@code onPreferencesReloaded} when the user returns from Settings, so there is no Save button
 * to forget: leaving the screen is the apply.
 */
@Keep
public class KeyboardLayoutsFragment extends Fragment {

    private TermuxAppSharedPreferences mPreferences;
    private final List<String> mSelection = new ArrayList<>();
    private LinearLayout mCycleList;
    private LinearLayout mAvailableList;
    private String mFilter = "";

    private int mColorText;
    private int mColorSubtle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        mPreferences = TermuxAppSharedPreferences.build(context);
        if (mPreferences == null)
            throw new IllegalStateException("Termux preferences unavailable");
        mColorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        mColorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        mSelection.clear();
        mSelection.addAll(LauncherKeyboardLayouts.parseSelection(getResources(),
            mPreferences.getInAppKeyboardLayouts()));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));

        TextView instructions = new TextView(context);
        instructions.setText(R.string.settings_keyboard_layouts_instructions);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        instructions.setTextColor(mColorSubtle);
        root.addView(instructions);

        root.addView(sectionHeader(context, getString(R.string.settings_keyboard_layouts_in_cycle)));
        mCycleList = new LinearLayout(context);
        mCycleList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mCycleList);

        root.addView(sectionHeader(context,
            getString(R.string.settings_keyboard_layouts_available)));
        root.addView(buildSearchField(context));
        mAvailableList = new LinearLayout(context);
        mAvailableList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mAvailableList);

        renderCycle();
        renderAvailable();

        ScrollView scroller = new ScrollView(context);
        scroller.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    @NonNull
    private View buildSearchField(@NonNull Context context) {
        TextInputLayout field = new TextInputLayout(context);
        field.setHint(getString(R.string.settings_keyboard_layouts_search_hint));
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable editable) {
                mFilter = editable == null ? "" : editable.toString().trim();
                renderAvailable();
            }
        });
        field.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    /** The ordered ring: the reorder and remove controls live on the rows themselves. */
    private void renderCycle() {
        Context context = requireContext();
        mCycleList.removeAllViews();
        for (int i = 0; i < mSelection.size(); i++) {
            final int index = i;
            String id = mSelection.get(i);
            String label = LauncherKeyboardLayouts.labelFor(getResources(), id);
            LinearLayout row = row(context, label, String.valueOf(i + 1));
            row.addView(glyphButton(context, "↑",
                getString(R.string.settings_keyboard_layouts_move_up, label),
                index > 0, view -> move(index, -1)));
            row.addView(glyphButton(context, "↓",
                getString(R.string.settings_keyboard_layouts_move_down, label),
                index < mSelection.size() - 1, view -> move(index, 1)));
            row.addView(glyphButton(context, "✕",
                getString(R.string.settings_keyboard_layouts_remove, label),
                true, view -> remove(index)));
            mCycleList.addView(row);
        }
    }

    /** Everything the app ships, minus what is already in the ring, narrowed by the search box. */
    private void renderAvailable() {
        Context context = requireContext();
        mAvailableList.removeAllViews();
        String needle = mFilter.toLowerCase(Locale.US);
        int shown = 0;
        for (LauncherKeyboardLayouts.Layout layout
            : LauncherKeyboardLayouts.catalog(getResources())) {
            if (mSelection.contains(layout.id)) continue;
            if (!needle.isEmpty()
                && !layout.label.toLowerCase(Locale.US).contains(needle)
                && !layout.id.toLowerCase(Locale.US).contains(needle)) continue;
            LinearLayout row = row(context, layout.label, layout.id);
            row.addView(glyphButton(context, "+",
                getString(R.string.settings_keyboard_layouts_add, layout.label),
                true, view -> add(layout.id)));
            mAvailableList.addView(row);
            shown++;
        }
        if (shown == 0 && !needle.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(getString(R.string.settings_keyboard_layouts_none_match, mFilter));
            empty.setTextColor(mColorSubtle);
            empty.setPadding(0, dp(8), 0, dp(8));
            mAvailableList.addView(empty);
        }
    }

    private void add(@NonNull String layoutId) {
        if (mSelection.size() >= LauncherKeyboardLayouts.MAX_SELECTION) {
            AppNotice.show(requireContext(), getString(R.string.settings_keyboard_layouts_full,
                LauncherKeyboardLayouts.MAX_SELECTION), false);
            return;
        }
        if (mSelection.contains(layoutId)) return;
        mSelection.add(layoutId);
        persist();
    }

    private void remove(int index) {
        if (index < 0 || index >= mSelection.size()) return;
        // The ring is what the keyboard types on; emptying it would leave nothing to render.
        if (mSelection.size() == 1) {
            AppNotice.show(requireContext(),
                getString(R.string.settings_keyboard_layouts_last), false);
            return;
        }
        mSelection.remove(index);
        persist();
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= mSelection.size() || target < 0 || target >= mSelection.size())
            return;
        mSelection.add(target, mSelection.remove(index));
        persist();
    }

    private void persist() {
        mPreferences.setInAppKeyboardLayouts(LauncherKeyboardLayouts.joinSelection(mSelection));
        renderCycle();
        renderAvailable();
    }

    @NonNull
    private LinearLayout row(@NonNull Context context, @NonNull String label,
                             @NonNull String meta) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(mColorText);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        text.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(meta);
        subtitle.setTextColor(mColorSubtle);
        subtitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        text.addView(subtitle);
        row.addView(text, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * A single-glyph control. Disabled rather than hidden at the ends of the list, so the row
     * keeps its shape and the buttons stay under the same thumb from row to row.
     */
    @NonNull
    private MaterialButton glyphButton(@NonNull Context context, @NonNull String glyph,
                                       @NonNull String description, boolean enabled,
                                       @NonNull View.OnClickListener onClick) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(glyph);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    @NonNull
    private TextView sectionHeader(@NonNull Context context, @NonNull String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setTextColor(mColorText);
        header.setPadding(0, dp(16), 0, dp(4));
        return header;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
