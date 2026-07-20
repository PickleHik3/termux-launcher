package com.termux.app.fragments.settings.termux;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.LayoutModifier;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.Theme;

/** Interactive swatch-based, per-key keyboard color scheme editor. */
@Keep
public class KeyboardColorSchemeFragment extends Fragment {

    private TermuxAppSharedPreferences mPreferences;
    private InAppKeyboardColorScheme mScheme;
    private Keyboard2View mKeyboard;
    private LinearLayout mSwatchRow;
    private final List<View> mSwatchViews = new ArrayList<>();
    private final Map<Integer, InAppKeyboardColorScheme.Role> mRoleByChipId = new HashMap<>();
    private int mSelectedSwatch;
    private InAppKeyboardColorScheme.Role mSelectedRole =
        InAppKeyboardColorScheme.Role.KEY_BACKGROUND;
    private boolean mEditingSwatches;
    private boolean mAdvanced;
    private MaterialButton mImportButton;
    private static final String STATE_ADVANCED = "advanced";
    private static final int MAX_BASE16_BYTES = 262144;
    private static final String BASE16_SCHEME_URL =
        "https://raw.githubusercontent.com/tinted-theming/schemes/spec-0.11/base16/%s.yaml";
    private final ExecutorService mImportExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "base16-theme-import");
        thread.setDaemon(true);
        return thread;
    });

    private final ActivityResultLauncher<String[]> mBase16Picker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), this::importBase16Uri);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        android.content.Context context = requireContext();
        mPreferences = TermuxAppSharedPreferences.build(context);
        if (mPreferences == null)
            throw new IllegalStateException("Termux preferences unavailable");
        mScheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());
        mAdvanced = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ADVANCED);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView instructions = new TextView(context);
        instructions.setText(R.string.termux_keyboard_color_scheme_instructions);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        instructions.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        root.addView(instructions);

        // Push the preview and its palette to the bottom, within thumb reach, instead of
        // leaving dead space below a top-anchored palette.
        Space spacer = new Space(context);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        Config.Builder config = new Config.Builder(getResources(), new Config.IKeyEventHandler() {
            @Override public void key_down(KeyValue value, boolean isSwipe) {}
            @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
            @Override public void mods_changed(Pointers.Modifiers modifiers) {}
            @Override public void suggestion_entered(String text) {}
        });
        config.hapticEnabled = false;
        config.keySoundEnabled = false;
        String theme = mPreferences.getInAppKeyboardTheme();
        String dockMatch = mPreferences.getInAppKeyboardDockMatch();
        Theme.Palette palette = "glass".equals(dockMatch) || "both".equals(dockMatch)
            ? InAppKeyboardPaletteFactory.createGlass(context, theme)
            : InAppKeyboardPaletteFactory.create(context, theme);
        palette = mScheme.applyToPalette(palette);
        mKeyboard = new Keyboard2View(context, config.build(), palette);
        KeyboardData previewLayout = KeyboardData.load(getResources(),
            juloo.keyboard2.R.xml.latn_qwerty_us);
        if (previewLayout != null) {
            LayoutModifier.LayoutOptions options = new LayoutModifier.LayoutOptions(
                true, false, true,
                com.termux.app.terminal.inappkeyboard.InAppKeyboardExtraKeys.resolve(
                    mPreferences.getInAppKeyboardExtraKeys()));
            mKeyboard.setKeyboard(LayoutModifier.modify(previewLayout, options, getResources()));
        }
        mKeyboard.setHeightScale(mPreferences.getInAppKeyboardHeightScale());
        mKeyboard.setKeyMarginScale(mPreferences.getInAppKeyboardKeyMarginScale());
        float radiusDp = mPreferences.getInAppKeyboardKeyCornerRadiusDp();
        mKeyboard.setKeyCornerRadiusOverride(radiusDp < 0f ? -1f : dpFloat(radiusDp));
        mKeyboard.setKeyColorOverrides(mScheme.resolvedOverrides());
        mKeyboard.setOnKeyPaintListener(keyId -> {
            mScheme.paint(keyId, mSelectedRole, mSelectedSwatch);
            persistAndRender();
        });
        root.addView(mKeyboard, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildPaletteCard(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    /** Bottom sheet-style card holding the swatches, edit/reset actions, and role filters. */
    @NonNull
    private View buildPaletteCard(@NonNull android.content.Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dpFloat(24));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY));
        card.setCardBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurface, Color.DKGRAY)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView colorsTitle = new TextView(context);
        colorsTitle.setText(R.string.termux_keyboard_color_scheme_colors);
        colorsTitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        heading.addView(colorsTitle, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton reset = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        reset.setText(R.string.termux_keyboard_color_scheme_reset);
        reset.setOnClickListener(view -> new AlertDialog.Builder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_reset_title)
            .setMessage(R.string.termux_keyboard_color_scheme_reset_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.termux_keyboard_color_scheme_reset, (dialog, which) -> {
                mPreferences.setInAppKeyboardColorScheme("");
                mScheme = InAppKeyboardColorScheme.fromJson(requireContext(), "");
                mSelectedSwatch = 0;
                createSwatches();
                persistAndRender();
            })
            .show());
        heading.addView(reset);
        MaterialButton edit = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        edit.setText(R.string.termux_keyboard_color_scheme_edit);
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editParams.setMarginStart(dp(8));
        edit.setOnClickListener(view -> {
            mEditingSwatches = !mEditingSwatches;
            edit.setText(mEditingSwatches ? R.string.termux_keyboard_color_scheme_done
                : R.string.termux_keyboard_color_scheme_edit);
            updateSwatches();
        });
        heading.addView(edit, editParams);
        content.addView(heading);

        LinearLayout advancedRow = new LinearLayout(context);
        advancedRow.setGravity(Gravity.CENTER_VERTICAL);
        MaterialSwitch advanced = new MaterialSwitch(context);
        advanced.setText(R.string.termux_keyboard_color_scheme_advanced);
        advanced.setChecked(mAdvanced);
        advanced.setOnCheckedChangeListener((button, checked) -> {
            mAdvanced = checked;
            mImportButton.setVisibility(checked ? View.VISIBLE : View.GONE);
            createSwatches();
        });
        advancedRow.addView(advanced, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mImportButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        mImportButton.setText(R.string.termux_keyboard_color_scheme_import_base16);
        mImportButton.setVisibility(mAdvanced ? View.VISIBLE : View.GONE);
        mImportButton.setOnClickListener(view -> showBase16NameDialog());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        importParams.setMarginStart(dp(12));
        advancedRow.addView(mImportButton, importParams);
        content.addView(advancedRow);

        HorizontalScrollView swatchScroller = new HorizontalScrollView(context);
        swatchScroller.setHorizontalScrollBarEnabled(false);
        swatchScroller.setClipToPadding(false);
        mSwatchRow = new LinearLayout(context);
        mSwatchRow.setOrientation(LinearLayout.HORIZONTAL);
        mSwatchRow.setGravity(Gravity.CENTER_VERTICAL);
        swatchScroller.addView(mSwatchRow, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(76)));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(76));
        swatchParams.topMargin = dp(8);
        content.addView(swatchScroller, swatchParams);
        createSwatches();

        HorizontalScrollView roleScroller = new HorizontalScrollView(context);
        roleScroller.setHorizontalScrollBarEnabled(false);
        roleScroller.setClipToPadding(false);
        ChipGroup roles = new ChipGroup(context);
        roles.setSingleLine(true);
        roles.setSingleSelection(true);
        roles.setSelectionRequired(true);
        mRoleByChipId.clear();
        addRole(context, roles, R.string.termux_keyboard_color_scheme_key_bg,
            InAppKeyboardColorScheme.Role.KEY_BACKGROUND, true);
        addRole(context, roles, R.string.termux_keyboard_color_scheme_key_border,
            InAppKeyboardColorScheme.Role.KEY_BORDER, false);
        addRole(context, roles, R.string.termux_keyboard_color_scheme_primary,
            InAppKeyboardColorScheme.Role.PRIMARY, false);
        addRole(context, roles, R.string.termux_keyboard_color_scheme_secondary,
            InAppKeyboardColorScheme.Role.SECONDARY, false);
        addRole(context, roles, R.string.termux_keyboard_color_scheme_secondary_bottom,
            InAppKeyboardColorScheme.Role.SECONDARY_BOTTOM, false);
        roles.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            InAppKeyboardColorScheme.Role role = mRoleByChipId.get(checkedIds.get(0));
            if (role != null)
                mSelectedRole = role;
        });
        roleScroller.addView(roles, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams roleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        roleParams.topMargin = dp(4);
        content.addView(roleScroller, roleParams);

        card.addView(content);
        return card;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.termux_keyboard_color_scheme_title);
    }

    private void addRole(@NonNull android.content.Context context, @NonNull ChipGroup group,
                         int label, InAppKeyboardColorScheme.Role role, boolean checked) {
        Chip chip = new Chip(context);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        mRoleByChipId.put(chip.getId(), role);
        ChipGroup.LayoutParams params = new ChipGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));
        group.addView(chip, params);
    }

    private void createSwatches() {
        mSwatchViews.clear();
        mSwatchRow.removeAllViews();
        int visibleCount = mAdvanced ? mScheme.swatchCount() : Math.min(6, mScheme.swatchCount());
        for (int i = 0; i < visibleCount; i++) {
            final int index = i;
            View swatch = new View(requireContext());
            String slot = String.format("base%02X", i);
            swatch.setContentDescription(mAdvanced ? slot
                : getString(R.string.termux_keyboard_color_scheme_swatch, i + 1));
            swatch.setOnClickListener(view -> {
                mSelectedSwatch = index;
                if (mEditingSwatches) showHexEditor(index);
                updateSwatches();
            });
            LinearLayout item = new LinearLayout(requireContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.addView(swatch, new LinearLayout.LayoutParams(dp(48), dp(48)));
            if (mAdvanced) {
                TextView label = new TextView(requireContext());
                label.setText(slot);
                label.setGravity(Gravity.CENTER);
                label.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
                item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(54), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(10));
            mSwatchRow.addView(item, params);
            mSwatchViews.add(swatch);
        }
        updateSwatches();
    }

    private void updateSwatches() {
        int outline = MaterialColors.getColor(requireContext(),
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        for (int i = 0; i < mSwatchViews.size(); i++) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(mScheme.getSwatch(i));
            drawable.setStroke(dp(i == mSelectedSwatch ? 3 : 1),
                i == mSelectedSwatch ? outline : Color.argb(90, Color.red(outline),
                    Color.green(outline), Color.blue(outline)));
            mSwatchViews.get(i).setBackground(drawable);
            mSwatchViews.get(i).setAlpha(mEditingSwatches && i != mSelectedSwatch ? 0.72f : 1f);
        }
    }

    private void showHexEditor(int index) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format("#%08X", mScheme.getSwatch(index)));
        input.selectAll();
        int horizontal = dp(24);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_hex_title)
            .setView(input, horizontal, 0, horizontal, 0)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                Integer color = parseHexColor(input.getText().toString());
                if (color == null) {
                    input.setError(getString(R.string.termux_keyboard_color_scheme_hex_error));
                    return;
                }
                mScheme.setSwatch(index, color);
                persistAndRender();
                updateSwatches();
                dialog.dismiss();
            }));
        dialog.show();
    }

    @Nullable
    static Integer parseHexColor(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
        try {
            long parsed = Long.parseLong(value, 16);
            if (value.length() == 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void persistAndRender() {
        mPreferences.setInAppKeyboardColorScheme(mScheme.toJson());
        String theme = mPreferences.getInAppKeyboardTheme();
        String dockMatch = mPreferences.getInAppKeyboardDockMatch();
        Theme.Palette palette = "glass".equals(dockMatch) || "both".equals(dockMatch)
            ? InAppKeyboardPaletteFactory.createGlass(requireContext(), theme)
            : InAppKeyboardPaletteFactory.create(requireContext(), theme);
        mKeyboard.setPalette(mScheme.applyToPalette(palette));
        mKeyboard.setKeyColorOverrides(mScheme.resolvedOverrides());
    }

    private void importBase16Uri(@Nullable Uri uri) {
        if (uri == null) return;
        try (InputStream stream = requireContext().getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Unable to open document");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (text.length() + count > MAX_BASE16_BYTES)
                    throw new IOException("Theme file is too large");
                text.append(buffer, 0, count);
            }
            if (!mScheme.importBase16(text.toString())) {
                Toast.makeText(requireContext(),
                    R.string.termux_keyboard_color_scheme_base16_error, Toast.LENGTH_LONG).show();
                return;
            }
            mSelectedSwatch = 0;
            persistAndRender();
            createSwatches();
            Toast.makeText(requireContext(),
                R.string.termux_keyboard_color_scheme_base16_imported, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(requireContext(),
                R.string.termux_keyboard_color_scheme_base16_read_error, Toast.LENGTH_LONG).show();
        }
    }

    private void showBase16NameDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.termux_keyboard_color_scheme_base16_name_hint);
        int horizontal = dp(24);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_base16_name_title)
            .setMessage(R.string.termux_keyboard_color_scheme_base16_name_message)
            .setView(input, horizontal, 0, horizontal, 0)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.termux_keyboard_color_scheme_base16_choose_file,
                (ignored, which) -> launchBase16FilePicker())
            .setPositiveButton(R.string.termux_keyboard_color_scheme_base16_import, null)
            .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String slug = normalizeBase16Name(input.getText().toString());
                if (slug == null) {
                    input.setError(getString(
                        R.string.termux_keyboard_color_scheme_base16_name_error));
                    return;
                }
                dialog.dismiss();
                downloadBase16(slug);
            });
            input.requestFocus();
        });
        dialog.show();
    }

    private void launchBase16FilePicker() {
        mBase16Picker.launch(new String[] {
            "application/x-yaml", "text/yaml", "text/plain", "application/json", "*/*"
        });
    }

    @Nullable
    static String normalizeBase16Name(@Nullable String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("base16-")) normalized = normalized.substring(7);
        normalized = normalized.replaceAll("[\\s_]+", "-")
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        return normalized.isEmpty() || normalized.length() > 96 ? null : normalized;
    }

    private void downloadBase16(@NonNull String slug) {
        Toast.makeText(requireContext(),
            R.string.termux_keyboard_color_scheme_base16_downloading, Toast.LENGTH_SHORT).show();
        mImportExecutor.execute(() -> {
            Base16Download result = fetchBase16(slug);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> applyDownloadedBase16(result));
        });
    }

    @NonNull
    private static Base16Download fetchBase16(@NonNull String slug) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(String.format(Locale.ROOT, BASE16_SCHEME_URL, slug));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "text/yaml,text/plain");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND)
                return Base16Download.notFound();
            if (status != HttpURLConnection.HTTP_OK)
                return Base16Download.networkError();
            try (InputStream stream = connection.getInputStream()) {
                String text = readLimitedText(stream);
                return text == null ? Base16Download.networkError()
                    : Base16Download.success(text);
            }
        } catch (IOException | SecurityException ignored) {
            return Base16Download.networkError();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Nullable
    private static String readLimitedText(@NonNull InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) {
            if (text.length() + count > MAX_BASE16_BYTES) return null;
            text.append(buffer, 0, count);
        }
        return text.toString();
    }

    private void applyDownloadedBase16(@NonNull Base16Download result) {
        if (!isAdded() || mKeyboard == null) return;
        if (result.notFound) {
            Toast.makeText(requireContext(),
                R.string.termux_keyboard_color_scheme_base16_not_found, Toast.LENGTH_LONG).show();
            return;
        }
        if (result.text == null) {
            Toast.makeText(requireContext(),
                R.string.termux_keyboard_color_scheme_base16_network_error,
                Toast.LENGTH_LONG).show();
            return;
        }
        if (!mScheme.importBase16(result.text)) {
            Toast.makeText(requireContext(),
                R.string.termux_keyboard_color_scheme_base16_error, Toast.LENGTH_LONG).show();
            return;
        }
        mSelectedSwatch = 0;
        persistAndRender();
        createSwatches();
        Toast.makeText(requireContext(),
            R.string.termux_keyboard_color_scheme_base16_imported, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        mImportExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class Base16Download {
        @Nullable final String text;
        final boolean notFound;

        private Base16Download(@Nullable String text, boolean notFound) {
            this.text = text;
            this.notFound = notFound;
        }

        static Base16Download success(@NonNull String text) {
            return new Base16Download(text, false);
        }

        static Base16Download notFound() {
            return new Base16Download(null, true);
        }

        static Base16Download networkError() {
            return new Base16Download(null, false);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_ADVANCED, mAdvanced);
        super.onSaveInstanceState(outState);
    }

    private int dp(float value) { return Math.round(dpFloat(value)); }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
