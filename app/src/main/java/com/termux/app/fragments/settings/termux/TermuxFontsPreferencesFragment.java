package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fonts.FontCatalog;
import com.termux.app.fonts.FontDownloader;
import com.termux.app.fonts.FontInstallCoordinator;
import com.termux.app.fonts.FontInstaller;
import com.termux.app.fonts.FontSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;

import java.util.List;
import java.util.Locale;

/**
 * The terminal font picker: a family list with per-family license text and download sizes, the
 * three toggles that shape the managed config, and an explicit exit back to
 * {@code ~/.termux/font.ttf} / Termux:Styling.
 *
 * <p>Nothing here downloads or installs anything itself. Every action goes through
 * {@link FontInstallCoordinator}, which owns the background thread and survives this fragment,
 * so a rotation mid-download reattaches to the running install instead of restarting it.
 *
 * <p>Rows reuse {@link TaiModelPreference} rather than introducing a parallel card layout: it
 * already renders exactly what a catalog row needs — suggested-family star, status pill,
 * monospace meta line, progress bar, and a primary plus secondary action.
 */
@Keep
public class TermuxFontsPreferencesFragment extends MaterialPreferenceFragment
    implements FontInstallCoordinator.Listener {

    private static final String ROW_KEY_PREFIX = "fonts_family_";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(FontSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_fonts_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureStaticRows(context);
        refresh(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (getActivity() != null) getActivity().setTitle(R.string.termux_fonts_preferences_title);
        if (context == null) return;
        FontInstallCoordinator.getInstance(context).addListener(this);
        refresh(context);
    }

    @Override
    public void onPause() {
        Context context = getContext();
        if (context != null) FontInstallCoordinator.getInstance(context).removeListener(this);
        super.onPause();
    }

    /** Progress arrives on the main thread; a rebuild is cheap enough at one update per megabyte. */
    @Override
    public void onFontInstallProgress(@NonNull FontDownloader.Progress progress) {
        Context context = getContext();
        if (context == null || !isAdded()) return;
        if (progress.state == FontDownloader.State.FAILED && !progress.error.isEmpty()) {
            Toast.makeText(context, getString(R.string.termux_fonts_install_failed, progress.error),
                Toast.LENGTH_LONG).show();
        } else if (progress.state == FontDownloader.State.INSTALLED) {
            Toast.makeText(context, R.string.termux_fonts_installed_toast, Toast.LENGTH_SHORT).show();
        }
        refresh(context);
    }

    // ------------------------------------------------------------------ wiring

    private void configureStaticRows(@NonNull Context context) {
        Preference ligatures = findPreference("fonts_ligatures");
        if (ligatures != null) {
            ligatures.setOnPreferenceClickListener(preference -> {
                showLigatureDialog(context);
                return true;
            });
        }
        Preference weight = findPreference("fonts_weight");
        if (weight != null) {
            weight.setOnPreferenceClickListener(preference -> {
                showWeightDialog(context);
                return true;
            });
        }
        SwitchPreferenceCompat icons = findPreference("nerd_icons");
        if (icons != null) {
            // The switch persists itself into the fonts prefs; the listener only has to push the
            // new value into the managed config so the change is visible without a reinstall.
            icons.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                FontCatalog.Family active = activeFamily(context);
                if (active != null) {
                    reapply(context, active, options(context, active).withNerdIcons(enabled));
                }
                return true;
            });
        }
        Preference useFontTtf = findPreference("fonts_use_font_ttf");
        if (useFontTtf != null) {
            useFontTtf.setOnPreferenceClickListener(preference -> {
                confirmUseFontTtf(context);
                return true;
            });
        }
    }

    private void refresh(@NonNull Context context) {
        FontCatalog.Result catalog = FontCatalog.load(context);
        FontInstaller installer = new FontInstaller();
        FontInstallCoordinator coordinator = FontInstallCoordinator.getInstance(context);
        String activeId = new FontSettings(context).getActiveFamilyId();
        FontCatalog.Family active = catalog.family(activeId);
        boolean managed = installer.isManaged();

        StatusCardPreference status = findPreference("fonts_status");
        if (status != null) {
            if (managed && active != null) {
                status.setStatus(active.displayName.toUpperCase(Locale.US), true);
                status.setSummary(getString(R.string.termux_fonts_status_active_summary,
                    active.displayName, active.license));
            } else {
                status.setStatus(getString(R.string.termux_fonts_status_none_label), false);
                status.setSummary(getString(R.string.termux_fonts_status_none_summary));
            }
        }

        refreshTuning(context, active, managed);
        refreshFamilyRows(context, catalog, installer, coordinator, managed, activeId);

        Preference useFontTtf = findPreference("fonts_use_font_ttf");
        if (useFontTtf != null) useFontTtf.setEnabled(managed);
    }

    private void refreshTuning(@NonNull Context context, @Nullable FontCatalog.Family active,
                               boolean managed) {
        boolean tunable = managed && active != null;
        SwitchPreferenceCompat icons = findPreference("nerd_icons");
        if (icons != null) icons.setEnabled(tunable);

        FontInstaller.Options options = active == null
            ? null : new FontSettings(context).getOptions(active);
        Preference ligatures = findPreference("fonts_ligatures");
        if (ligatures != null) {
            ligatures.setEnabled(tunable);
            ligatures.setSummary(options == null
                ? getString(R.string.termux_fonts_ligatures_none_summary)
                : ligatureLabel(options.ligatures));
        }
        Preference weight = findPreference("fonts_weight");
        if (weight != null) {
            boolean variable = tunable && active.weightAxis != null;
            weight.setEnabled(variable);
            if (!variable) {
                weight.setSummary(getString(R.string.termux_fonts_weight_static_summary));
            } else {
                FontCatalog.WeightAxis axis = active.weightAxis;
                int value = options != null && options.weight > 0
                    ? axis.clamp(options.weight) : axis.regularWeight;
                weight.setSummary(getString(R.string.termux_fonts_weight_summary,
                    value, axis.min, axis.max));
            }
        }
    }

    private void refreshFamilyRows(@NonNull Context context, @NonNull FontCatalog.Result catalog,
                                   @NonNull FontInstaller installer,
                                   @NonNull FontInstallCoordinator coordinator,
                                   boolean managed, @NonNull String activeId) {
        PreferenceCategory families = findPreference("fonts_families_category");
        if (families == null) return;
        families.removeAll();
        List<FontCatalog.Family> entries = catalog.families;
        if (entries.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setIconSpaceReserved(false);
            empty.setSelectable(false);
            empty.setTitle(R.string.termux_fonts_catalog_empty_title);
            empty.setSummary(R.string.termux_fonts_catalog_empty_summary);
            families.addPreference(empty);
            return;
        }
        String installing = coordinator.getActiveFamilyId();
        FontDownloader.Progress progress = coordinator.getLastProgress();
        for (FontCatalog.Family family : entries) {
            families.addPreference(buildRow(context, family, installer, managed, activeId,
                installing, progress));
        }
    }

    @NonNull
    private TaiModelPreference buildRow(@NonNull Context context, @NonNull FontCatalog.Family family,
                                        @NonNull FontInstaller installer, boolean managed,
                                        @NonNull String activeId, @NonNull String installingId,
                                        @Nullable FontDownloader.Progress progress) {
        TaiModelPreference row = new TaiModelPreference(context);
        row.setKey(ROW_KEY_PREFIX + family.id);
        row.setPersistent(false);
        row.setTitle(family.displayName);
        row.setRecommended(family.recommended);
        row.setSummary(family.summary);
        row.setBackendTone(TaiModelPreference.BackendTone.NEUTRAL);

        boolean installed = installer.isInstalled(family);
        boolean active = managed && family.id.equals(activeId);
        boolean downloading = family.id.equals(installingId);
        row.setMetaLine(buildMetaLine(family, installed));

        if (downloading && progress != null && progress.isActive()) {
            row.setDownloadProgress(true, progress.totalBytes <= 0L, progress.permyriad());
            row.setPill(getString(R.string.termux_fonts_pill_downloading), true);
        } else {
            row.setDownloadProgress(false, false, 0);
            row.setPill(active ? getString(R.string.termux_fonts_pill_active)
                : installed ? getString(R.string.termux_fonts_pill_installed) : null, active);
        }

        row.setTuneAction(getString(R.string.termux_fonts_license_action),
            view -> showLicenseDialog(context, family));
        if (downloading) {
            row.setPrimaryAction(getString(R.string.termux_fonts_action_cancel), true, true,
                view -> {
                    FontInstallCoordinator.getInstance(context).cancel();
                    Toast.makeText(context, R.string.termux_fonts_cancelling, Toast.LENGTH_SHORT).show();
                });
        } else if (active) {
            row.setPrimaryAction(getString(R.string.termux_fonts_action_active), false, false, null);
        } else {
            row.setPrimaryAction(getString(installed
                    ? R.string.termux_fonts_action_use : R.string.termux_fonts_action_install),
                true, false, view -> confirmInstall(context, family, installed));
        }
        row.setOnPreferenceClickListener(preference -> {
            showLicenseDialog(context, family);
            return true;
        });
        return row;
    }

    @NonNull
    private String buildMetaLine(@NonNull FontCatalog.Family family, boolean installed) {
        StringBuilder meta = new StringBuilder();
        meta.append(installed ? getString(R.string.termux_fonts_meta_installed)
            : formatBytes(family.downloadBytes));
        meta.append(" · ").append(getString(R.string.termux_fonts_meta_faces, family.faces.size()));
        if (family.variable) meta.append(" · ").append(getString(R.string.termux_fonts_meta_variable));
        if (!FontInstaller.LIGATURES_NEVER.equals(family.defaultLigatures)) {
            meta.append(" · ").append(getString(R.string.termux_fonts_meta_ligatures));
        }
        if (!family.hasFace(FontCatalog.FaceSlot.ITALIC)) {
            meta.append(" · ").append(getString(R.string.termux_fonts_meta_no_italic));
        }
        return meta.toString();
    }

    // ----------------------------------------------------------------- actions

    private void confirmInstall(@NonNull Context context, @NonNull FontCatalog.Family family,
                                boolean installed) {
        FontInstaller.Options options = new FontSettings(context).getOptions(family);
        if (installed) {
            reapply(context, family, options);
            return;
        }
        confirmDownload(context, family, options);
    }

    /** Confirms the download, then warns again when the connection is metered. */
    private void confirmDownload(@NonNull Context context, @NonNull FontCatalog.Family family,
                                 @NonNull FontInstaller.Options options) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_fonts_install_title, family.displayName))
            .setMessage(getString(R.string.termux_fonts_install_message,
                formatBytes(family.downloadBytes), family.license, family.licenseUrl))
            .setPositiveButton(R.string.termux_fonts_action_install,
                (dialog, which) -> confirmMeteredThenStart(context, family, options))
            .setNeutralButton(R.string.termux_fonts_license_action,
                (dialog, which) -> showLicenseDialog(context, family))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmMeteredThenStart(@NonNull Context context, @NonNull FontCatalog.Family family,
                                         @NonNull FontInstaller.Options options) {
        FontSettings settings = new FontSettings(context);
        boolean metered = FontInstallCoordinator.isConnectionMetered(context);
        if (!metered || settings.isMeteredWarningSuppressed()) {
            start(context, family, options);
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_fonts_metered_title)
            .setMessage(getString(R.string.termux_fonts_metered_message,
                formatBytes(family.downloadBytes)))
            .setPositiveButton(R.string.termux_fonts_metered_continue, (dialog, which) -> {
                settings.setMeteredWarningSuppressed(true);
                start(context, family, options);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void start(@NonNull Context context, @NonNull FontCatalog.Family family,
                       @NonNull FontInstaller.Options options) {
        boolean started = FontInstallCoordinator.getInstance(context).start(family, options);
        Toast.makeText(context, started
                ? getString(R.string.termux_fonts_install_started, family.displayName)
                : getString(R.string.termux_fonts_busy),
            Toast.LENGTH_SHORT).show();
        refresh(context);
    }

    private void reapply(@NonNull Context context, @NonNull FontCatalog.Family family,
                         @NonNull FontInstaller.Options options) {
        boolean applied = FontInstallCoordinator.getInstance(context).reapply(family, options);
        if (!applied) {
            Toast.makeText(context, R.string.termux_fonts_reapply_failed, Toast.LENGTH_LONG).show();
        }
        refresh(context);
    }

    private void confirmUseFontTtf(@NonNull Context context) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_fonts_use_font_ttf_title)
            .setMessage(R.string.termux_fonts_use_font_ttf_confirm)
            .setPositiveButton(R.string.termux_fonts_use_font_ttf_action, (dialog, which) -> {
                boolean removed = FontInstallCoordinator.getInstance(context).uninstallManagedConfig();
                Toast.makeText(context, removed
                        ? R.string.termux_fonts_uninstalled : R.string.termux_fonts_uninstall_missing,
                    Toast.LENGTH_SHORT).show();
                refresh(context);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ----------------------------------------------------------------- dialogs

    /** License text and URL, shown before anything is downloaded. OFL attribution requires it. */
    private void showLicenseDialog(@NonNull Context context, @NonNull FontCatalog.Family family) {
        StringBuilder message = new StringBuilder();
        if (!family.summary.isEmpty()) message.append(family.summary).append("\n\n");
        message.append(getString(R.string.termux_fonts_details_id, family.id)).append('\n');
        if (!family.releaseTag.isEmpty()) {
            message.append(getString(R.string.termux_fonts_details_release, family.releaseTag))
                .append('\n');
        }
        message.append(getString(R.string.termux_fonts_details_download,
            formatBytes(family.downloadBytes))).append('\n');
        message.append(getString(R.string.termux_fonts_details_license, family.license)).append("\n\n");
        if (!family.licenseNotice.isEmpty()) message.append(family.licenseNotice).append("\n\n");
        message.append(family.licenseUrl);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(family.displayName)
            .setMessage(message.toString())
            .setPositiveButton(R.string.termux_fonts_open_license,
                (dialog, which) -> openUrl(context, family.licenseUrl))
            .setNegativeButton(android.R.string.cancel, null);
        if (!family.homepageUrl.isEmpty()) {
            builder.setNeutralButton(R.string.termux_fonts_open_upstream,
                (dialog, which) -> openUrl(context, family.homepageUrl));
        }
        builder.show();
    }

    private void showLigatureDialog(@NonNull Context context) {
        FontCatalog.Family active = activeFamily(context);
        if (active == null) return;
        String[] values = {FontInstaller.LIGATURES_NEVER, FontInstaller.LIGATURES_CURSOR,
            FontInstaller.LIGATURES_ALWAYS};
        CharSequence[] labels = new CharSequence[values.length];
        FontInstaller.Options options = options(context, active);
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = ligatureLabel(values[i]);
            if (values[i].equals(options.ligatures)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_fonts_ligatures_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                reapply(context, active, options(context, active).withLigatures(values[which]));
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showWeightDialog(@NonNull Context context) {
        FontCatalog.Family active = activeFamily(context);
        if (active == null || active.weightAxis == null) return;
        FontCatalog.WeightAxis axis = active.weightAxis;
        FontInstaller.Options options = options(context, active);
        int current = options.weight > 0 ? axis.clamp(options.weight) : axis.regularWeight;

        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding / 2, padding, 0);
        TextView label = new TextView(context);
        label.setText(String.valueOf(current));
        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(axis.max - axis.min);
        seekBar.setProgress(current - axis.min);
        final int[] picked = {current};
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                picked[0] = axis.clamp(axis.min + value);
                label.setText(String.valueOf(picked[0]));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}

            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        layout.addView(label);
        layout.addView(seekBar);

        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_fonts_weight_dialog_title, axis.min, axis.max))
            .setView(layout)
            .setPositiveButton(R.string.termux_fonts_weight_apply, (dialog, which) ->
                reapply(context, active, options(context, active).withWeight(picked[0])))
            .setNeutralButton(R.string.termux_fonts_weight_reset, (dialog, which) ->
                reapply(context, active, options(context, active).withWeight(0)))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private FontCatalog.Family activeFamily(@NonNull Context context) {
        return FontCatalog.load(context).family(new FontSettings(context).getActiveFamilyId());
    }

    @NonNull
    private FontInstaller.Options options(@NonNull Context context, @NonNull FontCatalog.Family family) {
        return new FontSettings(context).getOptions(family);
    }

    @NonNull
    private String ligatureLabel(@NonNull String policy) {
        if (FontInstaller.LIGATURES_CURSOR.equals(policy)) {
            return getString(R.string.termux_fonts_ligatures_cursor);
        }
        if (FontInstaller.LIGATURES_ALWAYS.equals(policy)) {
            return getString(R.string.termux_fonts_ligatures_always);
        }
        return getString(R.string.termux_fonts_ligatures_never);
    }

    private void openUrl(@NonNull Context context, @NonNull String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show();
        }
    }

    /** Download sizes only ever reach a few tens of megabytes, so KB/MB is enough. */
    static String formatBytes(long bytes) {
        if (bytes <= 0L) return "—";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
