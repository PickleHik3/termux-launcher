package com.termux.app.setup;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;
import com.termux.app.dock.DockStyle;
import com.termux.app.surfaces.rows.ChipsRow;
import com.termux.app.wall.UseAs;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 2: the wallpaper, the Look, wallpaper colours, the cursor trail and the Dock design.
 * Every choice is written to the live launcher as it is made (the miniature above shows it);
 * {@link SetupState#restore} puts the snapshot back for "Keep my current setup".
 */
final class CustomizeSection implements SetupSection {

    private static final String[] LOOK_IDS = {SetupState.LOOK_CLEAR, SetupState.LOOK_MIST,
        SetupState.LOOK_TINT, SetupState.LOOK_SOLID};
    private static final int[] LOOK_NAMES = {R.string.termux_surface_preset_minimal,
        R.string.termux_surface_preset_frost, R.string.termux_surface_preset_stock,
        R.string.termux_surface_preset_solid};
    /** The appearance card's dock glyphs and words (surfaces/cards/DockCard), in DockStyle.CHOICES order. */
    private static final int[] DOCK_GLYPHS = {R.drawable.ic_symbol_dock_glass_tray,
        R.drawable.ic_symbol_dock_lens_beads, R.drawable.ic_symbol_dock_wall_strip,
        R.drawable.ic_symbol_dock_peek_glass};
    private static final int[] DOCK_WORDS = {R.string.layout_editor_dock_style_glass_tray,
        R.string.layout_editor_dock_style_lens_beads, R.string.layout_editor_dock_style_wall_strip,
        R.string.layout_editor_dock_style_peek_glass};

    /**
     * Whether the launcher can read the wallpaper it would draw glass over: not when it follows
     * the system wallpaper and the storage permission is missing. Mirrors
     * {@code WallpaperBackdropPolicy.mode}'s {@code allowsSelfDrawnBackdrop() && wallpaperReadable}.
     */
    static boolean wallpaperReadable(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs == null || !prefs.isUseSystemWallpaperEnabled())
            return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    }

    @StringRes
    static int dockWord(@NonNull DockStyle style) {
        int index = DockStyle.CHOICES.indexOf(style);
        return DOCK_WORDS[Math.max(0, index)];
    }

    @Override
    public int nameRes() {
        return R.string.setup_section_look;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        state.applyTierLookOnce();
        SetupState.Values v = state.values();
        boolean readable = wallpaperReadable(context);
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        SetupUi.header(page, R.string.setup_look_title, R.string.setup_look_description);

        addWallpaper(context, page, state, readable);

        // The glass rows: greyed while the wallpaper cannot be read.
        LinearLayout glass = new LinearLayout(context);
        glass.setOrientation(LinearLayout.VERTICAL);
        if (!readable) {
            TextView nudge = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(page));
            nudge.setText(R.string.setup_wallpaper_nudge);
            page.addView(nudge, SetupUi.matchWrap(SetupUi.dp(context, 8)));
        }
        addLabel(context, glass, R.string.setup_look_preset, 14);
        glass.addView(lookChips(context, state), SetupUi.matchWrap(SetupUi.dp(context, 2)));
        SetupUi.toggleRow(glass, R.string.setup_colours, R.string.setup_colours_helper, v.colours,
            8, (button, on) -> state.setColours(on));
        page.addView(glass, SetupUi.matchWrap(0));
        if (!readable)
            grey(glass);

        SetupUi.toggleRow(page, R.string.setup_trail, 0, v.trail, 8,
            (button, on) -> state.setTrail(on));

        if (state.useAs() != UseAs.TERMINAL) {
            addLabel(context, page, R.string.setup_dock_design, 14);
            page.addView(dockChips(context, state), SetupUi.matchWrap(SetupUi.dp(context, 2)));
        }
        return page;
    }

    private void addWallpaper(@NonNull Context context, @NonNull LinearLayout page,
                              @NonNull SetupState state, boolean readable) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SetupUi.dp(context, 16);
        card.setPadding(pad, SetupUi.dp(context, 12), pad, SetupUi.dp(context, 12));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView heading = SetupUi.text(context, 16f, true, SetupUi.onSurface(page));
        heading.setText(R.string.setup_wallpaper_in_app);
        words.addView(heading, SetupUi.matchWrap(0));
        TextView sentence = SetupUi.text(context, 13.5f, false, SetupUi.onSurfaceVariant(page));
        sentence.setText(R.string.setup_wallpaper_in_app_summary);
        words.addView(sentence, SetupUi.matchWrap(SetupUi.dp(context, 2)));
        card.addView(words, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = SetupUi.text(context, 24f, false, SetupUi.onSurfaceVariant(page));
        chevron.setText("›");
        card.addView(chevron, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            SetupState.Actions actions = state.actions();
            if (actions != null)
                actions.openWallpaperPicker();
        });
        SetupUi.dressChoice(card, !state.values().useSystemWallpaper);
        page.addView(card, SetupUi.matchWrap(SetupUi.dp(context, 12)));

        TextView system = SetupUi.text(context, 14f, true, SetupUi.accent(page));
        system.setText(R.string.setup_wallpaper_system);
        system.setPadding(pad, SetupUi.dp(context, 8), pad, SetupUi.dp(context, 8));
        system.setOnClickListener(view -> {
            state.setUseSystemWallpaper(true);
            SetupState.Actions actions = state.actions();
            if (actions == null)
                return;
            if (wallpaperReadable(view.getContext()))
                actions.refresh();
            else
                actions.requestWallpaperRead();
        });
        page.addView(system, SetupUi.matchWrap(SetupUi.dp(context, 2)));
    }

    private static void addLabel(@NonNull Context context, @NonNull LinearLayout page,
                                 @StringRes int label, int topMarginDp) {
        TextView view = SetupUi.text(context, 16f, true, SetupUi.onSurface(page));
        view.setText(label);
        page.addView(view, SetupUi.matchWrap(SetupUi.dp(context, topMarginDp)));
    }

    /** Clear / Mist / Tint / Solid; the one the wall wears is checked, none for a custom look. */
    @NonNull
    private static View lookChips(@NonNull Context context, @NonNull SetupState state) {
        ChipGroup group = new ChipGroup(context);
        int gap = SetupUi.dp(context, 8);
        group.setChipSpacingHorizontal(gap);
        group.setChipSpacingVertical(gap);
        List<Chip> chips = new ArrayList<>();
        for (int i = 0; i < LOOK_IDS.length; i++) {
            Chip chip = new Chip(context);
            chip.setText(LOOK_NAMES[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setChecked(LOOK_IDS[i].equals(state.values().lookId));
            final int index = i;
            chip.setOnClickListener(v -> {
                state.setLook(LOOK_IDS[index]);
                for (int j = 0; j < chips.size(); j++)
                    chips.get(j).setChecked(j == index);
            });
            chips.add(chip);
            group.addView(chip);
        }
        return group;
    }

    /** The Dock designs with the appearance card's glyphs, through the editor's own chip row. */
    @NonNull
    private static View dockChips(@NonNull Context context, @NonNull SetupState state) {
        List<ChipsRow.Link> links = new ArrayList<>();
        for (int i = 0; i < DockStyle.CHOICES.size(); i++)
            links.add(new ChipsRow.Link(DOCK_GLYPHS[i], context.getString(DOCK_WORDS[i])));
        List<Chip>[] holder = new List[1];
        ChipsRow row = ChipsRow.build(context, links, index -> {
            state.setDockStyle(DockStyle.CHOICES.get(index));
            check(holder[0], index);
        });
        holder[0] = row.chips;
        for (Chip chip : row.chips) {
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
        }
        check(row.chips, DockStyle.CHOICES.indexOf(state.values().dockStyle));
        return row.root;
    }

    private static void check(@NonNull List<Chip> chips, int index) {
        for (int i = 0; i < chips.size(); i++)
            chips.get(i).setChecked(i == index);
    }

    /** Dims a group and stops its controls taking taps. */
    private static void grey(@NonNull View view) {
        view.setAlpha(0.4f);
        disable(view);
    }

    private static void disable(@NonNull View view) {
        view.setEnabled(false);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                disable(group.getChildAt(i));
        }
        if (view.hasOnClickListeners())
            view.setClickable(false);
    }
}
