package com.termux.app.setup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.surfaces.AppearanceLooks;
import com.termux.app.chrome.ShapeTokens;
import com.termux.app.surfaces.GlassReset;
import com.termux.app.surfaces.GlassResetNudge;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The upgrade nudge (appearance-cards overhaul §6), in the setup cards' sheet style: the line,
 * four Look tiles (Clear preselected), Decline · Accept. Decided once at start-up ({@link
 * #decideAtStart}); shown by the launcher on a Home page ({@link #showIfDue}).
 */
public final class GlassResetNudgeCard {

    private GlassResetNudgeCard() {}

    /** The tiles' Looks: Clear, Mist, Tint, Solid (the Look slider's first four stops). */
    private static final int TILE_COUNT = 4;

    /**
     * The first start of this build decides whether the nudge is due. Runs after the start-up
     * migrations, so the grid read is the one this build will use.
     *
     * @param wallMigrationStoredBefore whether the wall migration's version was stored before
     *                                  this start ran it
     */
    public static void decideAtStart(@NonNull Context context,
                                     @Nullable TermuxAppSharedPreferences prefs,
                                     @Nullable String nightMode,
                                     boolean wallMigrationStoredBefore) {
        if (prefs == null) return;
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null) return;
        int state = store.getInt(GlassResetNudge.KEY, GlassResetNudge.UNDECIDED);
        if (state != GlassResetNudge.UNDECIDED) return;
        boolean usedBefore = GlassResetNudge.usedBefore(prefs.getSetupWizardVersion(),
            prefs.getFirstBootTourCompletedVersion(), prefs.isUseAsStored(),
            prefs.isWallpaperReadPermissionPrompted(), wallMigrationStoredBefore);
        boolean custom = usedBefore && GlassResetNudge.glassCustom(store.getAll(),
            new LookGrid(new LookGrid.MemoryStore()), nightMode,
            prefs.getTerminalContrastLevel().value,
            AppearanceSettingsStore.getBoolean(context, "terminal_dynamic_colors_enabled", false));
        store.edit().putInt(GlassResetNudge.KEY,
            GlassResetNudge.evaluate(state, usedBefore, custom)).apply();
    }

    /** Whether the nudge waits to be shown. */
    public static boolean isDue(@Nullable TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        return store != null
            && store.getInt(GlassResetNudge.KEY, GlassResetNudge.UNDECIDED) == GlassResetNudge.DUE;
    }

    private static void settle(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store != null)
            store.edit().putInt(GlassResetNudge.KEY, GlassResetNudge.SETTLED).apply();
    }

    /**
     * Shows the card over the launcher when it is due and not already up; the caller has checked
     * that a Home page is in front. Accept resets with the picked Look ({@link GlassReset}) and
     * runs {@code afterAccept} (Mode back to System, the window restyled); both buttons settle it.
     *
     * @return whether the card is up
     */
    public static boolean showIfDue(@NonNull Activity activity,
                                    @Nullable TermuxAppSharedPreferences prefs,
                                    @NonNull Runnable afterAccept) {
        if (!isDue(prefs)) return false;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return false;
        if (content.findViewWithTag(GlassResetNudgeCard.class) != null) return true;
        Context context = activity;

        FrameLayout scrim = new FrameLayout(context);
        scrim.setTag(GlassResetNudgeCard.class);
        scrim.setBackgroundColor(Color.argb(96, 0, 0, 0));
        scrim.setClickable(true);

        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(SetupUi.surface(scrim), 255));
        float radius = SetupUi.dp(context, 26f);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        sheet.setBackground(background);
        sheet.setElevation(ShapeTokens.elevationPx(context, 3));
        int side = SetupUi.dp(context, 20);
        sheet.setPadding(side, SetupUi.dp(context, 16), side, SetupUi.dp(context, 16));
        SetupUi.header(sheet, R.string.glass_reset_nudge_title, R.string.glass_reset_nudge_line);

        int[] picked = {GlassReset.DEFAULT_STOP};
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(TILE_COUNT);
        for (int stop = 0; stop < TILE_COUNT; stop++)
            tiles.add(new PreviewTilesRow.Tile(lookName(context, stop),
                GlassReset.lookPreview(context, stop)));
        PreviewTilesRow[] row = new PreviewTilesRow[1];
        row[0] = PreviewTilesRow.build(context, tiles, picked[0], index -> {
            picked[0] = index;
            row[0].select(index);
        });
        sheet.addView(row[0].root, SetupUi.matchWrap(SetupUi.dp(context, 12)));

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        MaterialButton decline = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        decline.setText(R.string.glass_reset_nudge_decline);
        decline.setAllCaps(false);
        footer.addView(decline, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        MaterialButton accept = new MaterialButton(context);
        accept.setText(R.string.glass_reset_nudge_accept);
        accept.setAllCaps(false);
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acceptParams.leftMargin = SetupUi.dp(context, 8);
        footer.addView(accept, acceptParams);
        sheet.addView(footer, SetupUi.matchWrap(SetupUi.dp(context, 8)));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        scrim.addView(sheet, sheetParams);
        scrim.setOnApplyWindowInsetsListener((v, insets) -> {
            sheet.setPadding(side, SetupUi.dp(context, 16), side,
                SetupUi.dp(context, 16) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        decline.setOnClickListener(v -> {
            settle(prefs);
            content.removeView(scrim);
        });
        accept.setOnClickListener(v -> {
            settle(prefs);
            GlassReset.reset(context, prefs, picked[0]);
            content.removeView(scrim);
            afterAccept.run();
        });
        content.addView(scrim, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.requestApplyInsets();
        return true;
    }

    @NonNull
    private static String lookName(@NonNull Context context, int stop) {
        String id = AppearanceLooks.presetIdForStop(stop);
        if ("frost".equals(id)) return context.getString(R.string.termux_surface_preset_frost);
        if ("stock".equals(id)) return context.getString(R.string.termux_surface_preset_stock);
        if ("solid".equals(id)) return context.getString(R.string.termux_surface_preset_solid);
        return context.getString(R.string.termux_surface_preset_minimal);
    }
}
