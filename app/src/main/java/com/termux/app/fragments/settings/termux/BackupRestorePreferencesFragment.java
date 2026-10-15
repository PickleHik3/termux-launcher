package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiModelExport;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.backup.SettingsBackupFormat;
import com.termux.app.settings.backup.SettingsBackupFormat.InvalidBackupException;
import com.termux.app.settings.backup.SettingsBackupIo;
import com.termux.app.settings.backup.ModelExportService;
import com.termux.app.settings.backup.SettingsRestart;
import com.termux.shared.logger.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backup & restore: saves the launcher's settings and config files to one file, and restores them
 * from one ({@link SettingsBackupFormat}). Reading, writing and restoring all run on one worker
 * thread; a restore asks first, then restarts the launcher so nothing runs half-restored.
 *
 * <p>The AI model rows sit beside them: Export AI models copies the downloaded models into a
 * picked folder ({@link ModelExportService}), and Import AI models is the Model centre's own
 * folder import ({@link TaiImportFlow}), hosted here, which takes those folders back.
 */
@Keep
public final class BackupRestorePreferencesFragment extends MaterialPreferenceFragment
    implements TaiImportFlow.Host {

    private static final String LOG_TAG = "BackupRestore";

    private static final String KEY_EXPORT = "settings_backup_export";
    private static final String KEY_IMPORT = "settings_backup_import";
    private static final String KEY_EXPORT_MODELS = "settings_backup_export_models";
    private static final String KEY_IMPORT_MODELS = "settings_backup_import_models";

    private static final String MIME_ZIP = "application/zip";
    /** Some pickers report a zip by its generic or legacy type; the reader checks the contents. */
    private static final String[] IMPORT_MIME_TYPES = {MIME_ZIP, "application/octet-stream", "application/x-zip-compressed"};

    /** Long enough to read the one-sentence notice before the launcher restarts. */
    private static final long RESTART_AFTER_NOTICE_MS = 3000;

    /** The shared Downloads folder, where the settings file pickers open; a picker that cannot go there ignores it. */
    private static final Uri DOWNLOADS = DocumentsContract.buildDocumentUri(
        ModelExportService.EXTERNAL_STORAGE_AUTHORITY, "primary:" + android.os.Environment.DIRECTORY_DOWNLOADS);
    /**
     * Where the model folder pickers open. Android 11 and later will not hand out the Downloads
     * folder itself as a tree ("Can't use this folder"), so a folder pick starts in Documents.
     */
    private static final Uri DOCUMENTS = DocumentsContract.buildDocumentUri(
        ModelExportService.EXTERNAL_STORAGE_AUTHORITY, "primary:" + android.os.Environment.DIRECTORY_DOCUMENTS);

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "settings-backup");
        thread.setDaemon(true);
        return thread;
    });
    /** One export or import at a time, across screen rotations. */
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    /** Model imports copy gigabytes, so they get their own thread rather than holding up the one above. */
    private static final ExecutorService MODEL_IMPORT = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "model-import");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final TaiImportFlow mModelImport = new TaiImportFlow(this);
    private ActivityResultLauncher<String> mExportLauncher;
    private ActivityResultLauncher<String[]> mImportLauncher;
    private ActivityResultLauncher<Uri> mExportModelsLauncher;
    private ActivityResultLauncher<Uri> mImportModelsLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExportLauncher = registerForActivityResult(new CreateZipDocument(), this::onExportTarget);
        mImportLauncher = registerForActivityResult(new OpenZipDocument(), this::onImportSource);
        mExportModelsLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::onModelsTarget);
        mImportModelsLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), mModelImport::onFolderSelected);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (getContext() == null) return;
        setPreferencesFromResource(R.xml.backup_restore_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        click(KEY_EXPORT, () -> {
            LocalDate today = LocalDate.now();
            mExportLauncher.launch(SettingsBackupFormat.suggestedFileName(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()));
        });
        click(KEY_IMPORT, () -> mImportLauncher.launch(IMPORT_MIME_TYPES.clone()));
        click(KEY_EXPORT_MODELS, () -> {
            if (ModelExportService.isRunning()) AppNotice.show(getContext(), R.string.settings_backup_models_busy, false);
            else mExportModelsLauncher.launch(DOCUMENTS);
        });
        click(KEY_IMPORT_MODELS, mModelImport::startFolder);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_backup_restore);
        refreshModelsRow();
    }

    // ------------------------------------------------------------------ AI models

    /** The Export AI models row: how many models and how large, or why there is nothing to export. */
    private void refreshModelsRow() {
        Context context = getContext();
        if (context == null) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            List<TaiModelExport.Model> models;
            try {
                models = ModelExportService.exportable(app);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Listing the models failed", e);
                models = Collections.emptyList();
            }
            long bytes = 0L;
            for (TaiModelExport.Model model : models) bytes += model.bytes;
            int count = models.size();
            long total = bytes;
            onMain(() -> showModels(count, total));
        });
    }

    private void showModels(int count, long bytes) {
        Preference row = findPreference(KEY_EXPORT_MODELS);
        if (row == null || !isAdded()) return;
        boolean any = count > 0;
        CharSequence summary = any
            ? getString(R.string.settings_backup_export_models_summary) + "\n"
                + TaiModelExport.sizeSummary(getResources(), count, bytes)
            : getString(R.string.settings_backup_export_models_none);
        if (!TextUtils.equals(row.getSummary(), summary)) row.setSummary(summary);
        if (row.isEnabled() != any) row.setEnabled(any);
    }

    private void onModelsTarget(@Nullable Uri tree) {
        Context context = getContext();
        if (tree == null || context == null) return;
        if (ModelExportService.isRunning()) {
            AppNotice.show(context, R.string.settings_backup_models_busy, false);
            return;
        }
        Context app = context.getApplicationContext();
        try {
            ModelExportService.start(app, tree, holdFolder(app, tree));
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Starting the model export failed", e);
            AppNotice.show(app, getResources().getQuantityString(R.plurals.settings_backup_models_failed, 0, 0), true);
        }
    }

    /**
     * Keeps write access to the picked folder after Settings closes, which a picker's grant alone
     * does not. True when this took the grant, so the export releases it when done; a folder the
     * app already held for something else stays held.
     */
    private static boolean holdFolder(@NonNull Context app, @NonNull Uri tree) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            for (UriPermission held : app.getContentResolver().getPersistedUriPermissions()) {
                if (tree.equals(held.getUri())) return false;
            }
            app.getContentResolver().takePersistableUriPermission(tree, flags);
            return true;
        } catch (RuntimeException e) {
            // A provider that does not offer a lasting grant: the copy runs on the picker's.
            return false;
        }
    }

    // ---- TaiImportFlow.Host: the Model centre's folder import, started from Import AI models ----

    @Nullable
    @Override
    public Context context() {
        return isAdded() ? getContext() : null;
    }

    @NonNull
    @Override
    public ExecutorService executor() {
        return MODEL_IMPORT;
    }

    @NonNull
    @Override
    public Handler handler() {
        return MAIN;
    }

    @Override
    public void pickFile() {
        // This screen only starts the folder import; the single-file import lives in the Model centre.
    }

    @Override
    public void pickFolder() {
        try {
            mImportModelsLauncher.launch(DOCUMENTS);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Opening the folder picker failed", e);
            AppNotice.show(getContext(), R.string.settings_backup_import_read_failed, false);
        }
    }

    @Override
    public void openUrl(@NonNull String url) {
        Context context = getContext();
        if (context == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            AppNotice.show(context, url, true);
        }
    }

    @Override
    public void setDefaultModel(@NonNull String modelId) {
        Context context = getContext();
        if (context != null) TaiModelCentreFragment.saveDefaultAssistant(context, modelId);
    }

    @Override
    public void modelsChanged() {
        refreshModelsRow();
    }

    private void click(@NonNull String key, @NonNull Runnable action) {
        Preference row = findPreference(key);
        if (row == null) return;
        row.setOnPreferenceClickListener(preference -> {
            if (!BUSY.get()) {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    // No document picker on this device.
                    Logger.logStackTraceWithMessage(LOG_TAG, "Opening the file picker failed", e);
                    AppNotice.show(getContext(), R.string.settings_backup_import_read_failed, false);
                }
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ export

    private void onExportTarget(@Nullable Uri uri) {
        Context context = getContext();
        if (uri == null || context == null || !BUSY.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            @StringRes int message;
            try (OutputStream out = app.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("No stream for " + uri);
                SettingsBackupIo.ExportResult result = SettingsBackupIo.export(
                    SettingsBackupIo.environment(app, true), out, System.currentTimeMillis());
                message = result.skipped.isEmpty()
                    ? R.string.settings_backup_exported : R.string.settings_backup_exported_skipped;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Saving the settings failed", e);
                deleteQuietly(app, uri);
                message = R.string.settings_backup_export_failed;
            }
            int shown = message;
            onMain(() -> {
                BUSY.set(false);
                AppNotice.show(app, shown, false);
            });
        });
    }

    /** A failed export leaves no half-written file behind. */
    private static void deleteQuietly(@NonNull Context app, @NonNull Uri uri) {
        try {
            DocumentsContract.deleteDocument(app.getContentResolver(), uri);
        } catch (Exception ignored) {
            // The provider may not allow it; the file is then simply incomplete.
        }
    }

    // ------------------------------------------------------------------ import

    private void onImportSource(@Nullable Uri uri) {
        Context context = getContext();
        if (uri == null || context == null || !BUSY.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            SettingsBackupIo.Environment env = null;
            SettingsBackupIo.Staged staged = null;
            @StringRes int error = 0;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("No stream for " + uri);
                env = SettingsBackupIo.environment(app, false);
                staged = SettingsBackupIo.read(env, in);
            } catch (InvalidBackupException e) {
                error = messageFor(e.reason);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Reading the backup failed", e);
                error = R.string.settings_backup_import_read_failed;
            }
            SettingsBackupIo.Environment readEnv = env;
            SettingsBackupIo.Staged readStaged = staged;
            int readError = error;
            onMain(() -> {
                if (readStaged == null || readEnv == null) {
                    BUSY.set(false);
                    AppNotice.show(app, readError != 0 ? readError : R.string.settings_backup_import_read_failed, false);
                } else if (!isAdded() || getActivity() == null) {
                    discard(readStaged);
                } else {
                    confirm(readEnv, readStaged);
                }
            });
        });
    }

    @StringRes
    private static int messageFor(@NonNull InvalidBackupException.Reason reason) {
        switch (reason) {
            case NEWER_FORMAT: return R.string.settings_backup_import_newer;
            case TOO_LARGE: return R.string.settings_backup_import_too_large;
            default: return R.string.settings_backup_import_not_backup;
        }
    }

    private void confirm(@NonNull SettingsBackupIo.Environment env, @NonNull SettingsBackupIo.Staged staged) {
        Activity activity = requireActivity();
        SettingsBackupFormat.Manifest manifest = staged.manifest;
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(manifest.createdAt));
        boolean[] confirmed = {false};
        new MaterialAlertDialogBuilder(activity)
            .setTitle(getString(R.string.settings_backup_import_confirm_title, date))
            .setMessage(getString(R.string.settings_backup_import_confirm_message, manifest.versionName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_backup_import_confirm_action, (dialog, which) -> {
                confirmed[0] = true;
                restore(activity, env, staged);
            })
            .setOnDismissListener(dialog -> {
                if (!confirmed[0]) discard(staged);
            })
            .show();
    }

    private static void discard(@NonNull SettingsBackupIo.Staged staged) {
        WORKER.execute(() -> {
            SettingsBackupIo.discard(staged);
            BUSY.set(false);
        });
    }

    /**
     * Writes the backup, then restarts the launcher whatever happened, so it never runs with half
     * a restore in memory. A partial failure is said first, long enough to read.
     */
    private static void restore(@NonNull Activity activity, @NonNull SettingsBackupIo.Environment env,
                                @NonNull SettingsBackupIo.Staged staged) {
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            int failures;
            try {
                failures = SettingsBackupIo.apply(env, staged).failures
                    + SettingsBackupIo.applyWallpapers(app, env, staged);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Restoring the settings failed", e);
                failures = 1;
            }
            int failed = failures;
            onMain(() -> {
                if (failed == 0) {
                    SettingsRestart.restart(activity);
                    return;
                }
                AppNotice.show(app, R.string.settings_backup_import_partial, true);
                new Handler(Looper.getMainLooper()).postDelayed(
                    () -> SettingsRestart.restart(activity), RESTART_AFTER_NOTICE_MS);
            });
        });
    }

    private static void onMain(@NonNull Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    // ------------------------------------------------------------------ pickers

    /** Save-as for a zip, opening in Downloads. */
    static final class CreateZipDocument extends ActivityResultContracts.CreateDocument {
        CreateZipDocument() {
            super(MIME_ZIP);
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String input) {
            return super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS);
        }
    }

    /** Open a zip, opening in Downloads; virtual documents are left out since the bytes are needed. */
    static final class OpenZipDocument extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            return super.createIntent(context, input)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS);
        }
    }
}
