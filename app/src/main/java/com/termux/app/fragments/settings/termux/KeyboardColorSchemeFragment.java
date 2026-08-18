package com.termux.app.fragments.settings.termux;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private LinearLayout mSwatchGrid;
    private TextView mStatus;
    private final List<View> mSwatchViews = new ArrayList<>();
    private final List<View> mSwatchBadges = new ArrayList<>();
    private final List<View> mSwatchItems = new ArrayList<>();
    private final Map<Integer, InAppKeyboardColorScheme.Role> mRoleByChipId = new HashMap<>();
    private int mSelectedSwatch;
    private InAppKeyboardColorScheme.Role mSelectedRole =
        InAppKeyboardColorScheme.Role.KEY_BACKGROUND;
    /** The Background chip paints through swatch taps alone, so it sits outside the roles. */
    private boolean mPaintingBackground;
    private boolean mEditingSwatches;
    private static final int SWATCH_GRID_COLUMNS = 8;
    /**
     * Material role behind every slot, mirroring
     * {@link InAppKeyboardPaletteFactory#defaultEditorSwatches}. These stay untranslated on
     * purpose: they are the API role names a theme author matches against, and they only appear in
     * content descriptions and the hex dialog, never as on-screen chip text. Slots 06 and 10 are
     * both {@code colorSurface} in the factory, which is named rather than hidden.
     */
    private static final String[] SLOT_ROLE_IDS = {
        "surfaceContainerHigh", "primary", "secondary", "onSurface", "onSurfaceVariant",
        "secondaryContainer", "surface", "surfaceContainerHighest", "error", "tertiary",
        "primaryContainer", "onPrimary", "onSecondary", "onTertiary", "outlineVariant",
        "errorContainer", "surface (same as base06)", "surfaceContainer",
        "error + onSurface 20%", "tertiary + onSurface 20%", "primary + onSurface 20%",
        "secondary + onSurface 20%", "tertiary + primary 50%", "primary + secondary 50%"
    };
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

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Whether the keyboard still moves with the wallpaper is the first thing to say.
        mStatus = new TextView(context);
        mStatus.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        mStatus.setText(statusText(context, mScheme));
        root.addView(mStatus, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView instructions = new TextView(context);
        instructions.setText(R.string.termux_keyboard_color_scheme_instructions);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        instructions.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(6);
        root.addView(instructions, instructionParams);

        // Push the preview and its palette to the bottom, within thumb reach, instead of
        // leaving dead space below a top-anchored palette.
        Space spacer = new Space(context);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        // All 24 swatches sit right above the preview so a chosen color lands next to the keys
        // it paints; the card below keeps the actions and role filters.
        mSwatchGrid = new LinearLayout(context);
        mSwatchGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.bottomMargin = dp(8);
        root.addView(mSwatchGrid, gridParams);

        Config.Builder config = new Config.Builder(getResources(), new Config.IKeyEventHandler() {
            @Override public void key_down(KeyValue value, boolean isSwipe) {}
            @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
            @Override public void mods_changed(Pointers.Modifiers modifiers) {}
            @Override public void suggestion_entered(String text) {}
        });
        config.hapticEnabled = false;
        config.keySoundEnabled = false;
        mKeyboard = new Keyboard2View(context, config.build(), buildPreviewPalette(context));
        KeyboardData previewLayout = KeyboardData.load(getResources(),
            juloo.keyboard2.R.xml.termux_launcher_qwerty);
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
            // The Background chip assigns through swatch taps alone; a key tap paints nothing.
            if (mPaintingBackground)
                return;
            mScheme.paint(keyId, mSelectedRole, mSelectedSwatch);
            persistAndRender();
        });
        root.addView(mKeyboard, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildPaletteCard(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        createSwatches();
        return root;
    }

    /** Glass preview palette: the imported palette when active, then the background override. */
    @NonNull
    private Theme.Palette buildPreviewPalette(@NonNull android.content.Context context) {
        String theme = mPreferences.getInAppKeyboardTheme();
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, theme);
        if (mScheme.shouldApplyImportedPalette(theme))
            palette = mScheme.applyToPalette(palette);
        // Production paints this color into the activity's glass backdrop behind a transparent
        // keyboard; the preview has no backdrop, so its palette carries the color itself.
        Integer background = mScheme.resolvedKeyboardBackground();
        return background == null ? palette : withKeyboardBackground(palette, background);
    }

    /** Copy of a palette with only the keyboard background replaced. */
    @NonNull
    private static Theme.Palette withKeyboardBackground(@NonNull Theme.Palette base, int color) {
        return new Theme.Palette(color, base.keyBackground, base.actionKeyBackground,
            base.spaceBarBackground, base.activatedKeyBackground, base.labelColor,
            base.subLabelColor, base.activatedLabelColor, base.pressedLabelColor,
            base.lockedModifierColor, base.borderColor, base.borderEnabled, base.borderWidth,
            base.borderRadius, base.opacity, base.secondaryDimming, base.greyedDimming,
            base.actionLabelColor, base.actionSubLabelColor, base.indicatorColors,
            base.keyGradientTopOverlay, base.keyGradientBottomOverlay);
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
        MaterialButton edit = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        edit.setText(R.string.termux_keyboard_color_scheme_edit_colors);
        edit.setOnClickListener(view -> {
            mEditingSwatches = !mEditingSwatches;
            edit.setText(mEditingSwatches ? R.string.termux_keyboard_color_scheme_save_colors
                : R.string.termux_keyboard_color_scheme_edit_colors);
            updateSwatches();
        });
        heading.addView(edit);
        content.addView(heading);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton followTheme = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        followTheme.setText(R.string.termux_keyboard_color_scheme_follow_theme);
        followTheme.setOnClickListener(view -> showFollowThemeDialog());
        actions.addView(followTheme, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(4);
        content.addView(actions, actionParams);

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
        // Not a per-key role: while checked, a swatch tap immediately becomes the whole
        // keyboard's background, and re-tapping the assigned swatch clears it again.
        Chip backgroundChip = addRole(context, roles,
            R.string.termux_keyboard_color_scheme_background, null, false);
        roles.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int checkedId = checkedIds.get(0);
            mPaintingBackground = checkedId == backgroundChip.getId();
            InAppKeyboardColorScheme.Role role = mRoleByChipId.get(checkedId);
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
        // The wallpaper may have changed while this screen sat in the background; dynamic slots
        // have to show what the keyboard will actually use.
        if (mScheme != null && mKeyboard != null
                && mScheme.refreshDynamicSwatches(requireContext())) {
            persistAndRender();
            updateSwatches();
        }
    }

    /** Scheme-level reset: every slot goes back to following the system Material theme. */
    private void showFollowThemeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_follow_theme_title)
            .setMessage(R.string.termux_keyboard_color_scheme_follow_theme_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.termux_keyboard_color_scheme_follow_theme_clear_keys,
                (dialog, which) -> applyFollowTheme(true))
            .setPositiveButton(R.string.termux_keyboard_color_scheme_follow_theme,
                (dialog, which) -> applyFollowTheme(false))
            .show();
    }

    private void applyFollowTheme(boolean clearPaintedKeys) {
        if (clearPaintedKeys) {
            mPreferences.setInAppKeyboardColorScheme("");
            mScheme = InAppKeyboardColorScheme.fromJson(requireContext(), "");
        } else {
            resetSchemeToTheme(mScheme);
        }
        // An imported palette is applied only while the theme preference names a custom theme, so
        // a deliberate light/dark choice is left alone.
        if ("custom".equals(mPreferences.getInAppKeyboardTheme()))
            mPreferences.setInAppKeyboardTheme("system");
        mSelectedSwatch = 0;
        persistAndRender();
        createSwatches();
        Toast.makeText(requireContext(),
            R.string.termux_keyboard_color_scheme_follow_theme_done, Toast.LENGTH_SHORT).show();
    }

    /** Reset seam used by the confirmation dialog: unpins every slot and drops the import. */
    static void resetSchemeToTheme(@NonNull InAppKeyboardColorScheme scheme) {
        scheme.unpinAllSwatches();
    }

    @NonNull
    static String slotName(int index) {
        return String.format(Locale.ROOT, "base%02X", index);
    }

    /** Material role identifier behind a slot, or an empty string for an unmapped slot. */
    @NonNull
    static String slotRoleId(int index) {
        return index >= 0 && index < SLOT_ROLE_IDS.length ? SLOT_ROLE_IDS[index] : "";
    }

    /** Short, translated role name used when a slot has no Material role identifier. */
    @NonNull
    static String slotRoleLabel(@NonNull android.content.Context context, int index) {
        String[] labels = context.getResources().getStringArray(
            R.array.termux_keyboard_color_scheme_slot_labels);
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    /** "base00, surfaceContainerHigh, follows theme" — slot, role, and pinned state. */
    @NonNull
    static String slotDescription(@NonNull android.content.Context context,
                                  @NonNull InAppKeyboardColorScheme scheme, int index) {
        String role = slotRoleId(index);
        if (role.isEmpty()) role = slotRoleLabel(context, index);
        return context.getString(R.string.termux_keyboard_color_scheme_slot_description,
            slotName(index), role,
            context.getString(scheme.isSwatchPinned(index)
                ? R.string.termux_keyboard_color_scheme_slot_pinned
                : R.string.termux_keyboard_color_scheme_slot_dynamic));
    }

    static int pinnedSwatchCount(@NonNull InAppKeyboardColorScheme scheme) {
        int pinned = 0;
        for (int i = 0; i < scheme.swatchCount(); i++) {
            if (scheme.isSwatchPinned(i)) pinned++;
        }
        return pinned;
    }

    /**
     * One line saying whether the keyboard still follows the wallpaper: an imported palette wins
     * over the pinned count, because an import pins every slot it fills.
     */
    @NonNull
    static String statusText(@NonNull android.content.Context context,
                             @NonNull InAppKeyboardColorScheme scheme) {
        if (scheme.hasImportedPalette()) {
            String themeId = scheme.getImportedThemeId();
            return themeId.isEmpty()
                ? context.getString(
                    R.string.termux_keyboard_color_scheme_status_imported_unnamed)
                : context.getString(R.string.termux_keyboard_color_scheme_status_imported,
                    themeId);
        }
        if (scheme.isFullyDynamic())
            return context.getString(R.string.termux_keyboard_color_scheme_status_dynamic);
        return context.getString(R.string.termux_keyboard_color_scheme_status_pinned,
            pinnedSwatchCount(scheme), scheme.swatchCount());
    }

    @NonNull
    private Chip addRole(@NonNull android.content.Context context, @NonNull ChipGroup group,
                         int label, @Nullable InAppKeyboardColorScheme.Role role,
                         boolean checked) {
        Chip chip = new Chip(context);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        if (role != null)
            mRoleByChipId.put(chip.getId(), role);
        ChipGroup.LayoutParams params = new ChipGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));
        group.addView(chip, params);
        return chip;
    }

    /**
     * Every slot is always visible: 8x3 fixed circles instead of a scrolling row, so choosing a
     * color never means hunting off-screen. The circles carry no text; the role and pinned state
     * live in each slot's content description and in the hex dialog.
     */
    private void createSwatches() {
        android.content.Context context = requireContext();
        mSwatchViews.clear();
        mSwatchBadges.clear();
        mSwatchItems.clear();
        mSwatchGrid.removeAllViews();
        if (mSelectedSwatch < 0 || mSelectedSwatch >= mScheme.swatchCount())
            mSelectedSwatch = 0;
        LinearLayout row = null;
        for (int i = 0; i < mScheme.swatchCount(); i++) {
            final int index = i;
            if (i % SWATCH_GRID_COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = dp(6);
                mSwatchGrid.addView(row, rowParams);
            }

            // The pinned badge overlays the swatch, so the two share a well; the well floats in
            // an equal-weight cell, which is what spaces the columns evenly on any width.
            FrameLayout cell = new FrameLayout(context);
            cell.setOnClickListener(view -> onSwatchTapped(index));
            FrameLayout well = new FrameLayout(context);
            View swatch = new View(context);
            well.addView(swatch, new FrameLayout.LayoutParams(dp(36), dp(36)));
            View badge = new View(context);
            badge.setBackground(pinnedBadge(context));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(11), dp(11));
            badgeParams.gravity = Gravity.TOP | Gravity.END;
            well.addView(badge, badgeParams);
            FrameLayout.LayoutParams wellParams = new FrameLayout.LayoutParams(dp(36), dp(36));
            wellParams.gravity = Gravity.CENTER;
            cell.addView(well, wellParams);

            row.addView(cell, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mSwatchViews.add(swatch);
            mSwatchBadges.add(badge);
            mSwatchItems.add(cell);
        }
        updateSwatches();
    }

    private void onSwatchTapped(int index) {
        mSelectedSwatch = index;
        if (mEditingSwatches) {
            showHexEditor(index);
        } else if (mPaintingBackground) {
            // Re-tapping the assigned swatch is the way back to the theme's own surface.
            if (mScheme.getKeyboardBackgroundSwatch() == index)
                mScheme.clearKeyboardBackgroundSwatch();
            else
                mScheme.setKeyboardBackgroundSwatch(index);
            persistAndRender();
        }
        updateSwatches();
    }

    /** Dot marking a pinned slot, ringed in the page surface so it reads over any swatch. */
    @NonNull
    private GradientDrawable pinnedBadge(@NonNull android.content.Context context) {
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        badge.setStroke(dp(2), MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.DKGRAY));
        return badge;
    }

    /** Dashed ring = follows the theme, solid ring plus dot = pinned to a fixed color. */
    private void updateSwatches() {
        android.content.Context context = requireContext();
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        int faint = Color.argb(90, Color.red(outline), Color.green(outline), Color.blue(outline));
        for (int i = 0; i < mSwatchViews.size(); i++) {
            boolean selected = i == mSelectedSwatch;
            boolean pinned = mScheme.isSwatchPinned(i);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(mScheme.getSwatch(i));
            int strokeWidth = dp(selected ? 3 : 1);
            int strokeColor = selected ? outline : faint;
            if (pinned)
                drawable.setStroke(strokeWidth, strokeColor);
            else
                drawable.setStroke(strokeWidth, strokeColor, dpFloat(4), dpFloat(3));
            mSwatchViews.get(i).setBackground(drawable);
            mSwatchViews.get(i).setAlpha(mEditingSwatches && !selected ? 0.72f : 1f);
            mSwatchBadges.get(i).setVisibility(pinned ? View.VISIBLE : View.INVISIBLE);
            mSwatchItems.get(i).setContentDescription(slotDescription(context, mScheme, i));
        }
        if (mStatus != null)
            mStatus.setText(statusText(context, mScheme));
    }

    private void showHexEditor(int index) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format("#%08X", mScheme.getSwatch(index)));
        input.selectAll();
        int horizontal = dp(24);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_hex_title)
            .setMessage(slotDescription(requireContext(), mScheme, index))
            .setView(input, horizontal, 0, horizontal, 0)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null);
        // Pinning must not be one-way: the way back sits next to the hex field that pinned it.
        if (mScheme.isSwatchPinned(index))
            builder.setNeutralButton(R.string.termux_keyboard_color_scheme_slot_unpin,
                (unused, which) -> unpinSwatch(index));
        AlertDialog dialog = builder.create();
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

    private void unpinSwatch(int index) {
        mScheme.unpinSwatch(index);
        persistAndRender();
        updateSwatches();
        Toast.makeText(requireContext(),
            getString(R.string.termux_keyboard_color_scheme_slot_unpinned, slotName(index)),
            Toast.LENGTH_SHORT).show();
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
        mKeyboard.setPalette(buildPreviewPalette(requireContext()));
        mKeyboard.setKeyColorOverrides(mScheme.resolvedOverrides());
    }

    private int dp(float value) { return Math.round(dpFloat(value)); }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
