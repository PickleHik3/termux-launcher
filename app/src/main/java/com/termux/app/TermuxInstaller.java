package com.termux.app;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.system.Os;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.WindowManager;
import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.android.SELinuxUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The architecture is determined and an appropriate bootstrap zip url is determined in {@link #determineZipUrl()}.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    // VAJ bootstrap is downloaded on first run (not embedded in the APK). Keep the URL and hash in
    // sync with the accepted bootstrap in the VAJ workspace (CURRENT_STATE.md).
    private static final String BOOTSTRAP_ARCH = "aarch64";
    private static final String BOOTSTRAP_SHA256 = "88985cead42037536291fc2575dc04e22e4bceb1b4f1c6601e3c008ed09dc204";
    private static final String BOOTSTRAP_URL = "https://github.com/PickleHik3/vaj-bootstrap-artifacts/releases/download/bootstrap-aarch64-88985cead420/bootstrap-aarch64.zip";

    private static final String BOOTSTRAP_SECOND_STAGE_OLD_PATH = "etc/termux/bootstrap/termux-bootstrap-second-stage.sh";
    private static final String BOOTSTRAP_SECOND_STAGE_NEW_PATH = "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh";

    /**
     * Performs bootstrap setup if necessary.
     */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;
        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;
        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message, MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity, activity.getString(R.string.bootstrap_error_title), bootstrapErrorMessage);
            return;
        }
        // Android assigns the SELinux domain from the highest target sdk version in the app's
        // sharedUserId group and remembers it until every member is uninstalled, so the app can be
        // put in a domain that forbids executing anything under $PREFIX even though it targets
        // sdk 28 itself. Check before touching the prefix: the bootstrap would extract fine and
        // then fail deep inside its second stage, and the prefix would be deleted with it.
        String restrictedDomain = getRestrictedSELinuxDomain();
        if (restrictedDomain != null) {
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            showExecRestrictedErrorDialog(activity, restrictedDomain);
            return;
        }
        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) && !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd, MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.showMessage(activity, activity.getString(R.string.bootstrap_error_title), bootstrapErrorMessage, null);
            return;
        }
        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" exists but is empty or only contains specific unimportant files.");
            } else {
                String loginBinaryPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login";
                Error loginBinaryError = FileUtils.validateRegularFileExistenceAndPermissions(
                    "termux login binary", loginBinaryPath, TERMUX_PREFIX_DIR_PATH,
                    FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS, true, true, false
                );
                if (loginBinaryError == null) {
                    whenDone.run();
                    return;
                }
                Logger.logWarn(LOG_TAG, "The termux prefix directory exists but bootstrap appears incomplete/broken. Reinstalling bootstrap. " +
                    Error.getMinimalErrorString(loginBinaryError));
            }
        } else if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" does not exist but another file exists at its destination.");
        }
        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {

            @Override
            public void run() {
                try {
                    Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");
                    Error error;
                    // Delete prefix staging directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    // Delete prefix directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    // Create prefix staging directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    // Create prefix directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"" + TERMUX_STAGING_PREFIX_DIR_PATH + "\".");
                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);
                    try (InputStream bootstrapInput = openBootstrapInputStream(activity);
                         ZipInputStream zipInput = new ZipInputStream(bootstrapInput)) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = symlinksReader.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2)
                                        throw new RuntimeException("Malformed symlink line: " + line);
                                    String oldPath = parts[0];
                                    String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                    symlinks.add(Pair.create(oldPath, newPath));
                                    error = ensureDirectoryExists(new File(newPath).getParentFile());
                                    if (error != null) {
                                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                        return;
                                    }
                                }
                            } else {
                                String zipEntryName = zipEntry.getName();
                                File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                                boolean isDirectory = zipEntry.isDirectory();
                                error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                if (error != null) {
                                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                    return;
                                }
                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1) outStream.write(buffer, 0, readBytes);
                                    }
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                        zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods") ||
                                        zipEntryName.equals(BOOTSTRAP_SECOND_STAGE_OLD_PATH) ||
                                        zipEntryName.equals(BOOTSTRAP_SECOND_STAGE_NEW_PATH)) {
                                        //noinspection OctalInteger
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }
                    if (symlinks.isEmpty())
                        throw new RuntimeException("No SYMLINKS.txt encountered");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }
                    Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.");
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
                    }

                    // Run Termux bootstrap second stage
                    Logger.logInfo(LOG_TAG, "Running Termux bootstrap second stage.");
                    String termuxBootstrapSecondStageFile = TERMUX_PREFIX_DIR_PATH + "/" + BOOTSTRAP_SECOND_STAGE_NEW_PATH;
                    if (!FileUtils.fileExists(termuxBootstrapSecondStageFile, false)) {
                        termuxBootstrapSecondStageFile = TERMUX_PREFIX_DIR_PATH + "/" + BOOTSTRAP_SECOND_STAGE_OLD_PATH;
                    }
                    if (FileUtils.fileExists(termuxBootstrapSecondStageFile, false)) {
                        String termuxBashFile = TERMUX_PREFIX_DIR_PATH + "/bin/bash";
                        ExecutionCommand executionCommand = new ExecutionCommand(-1,
                                termuxBashFile, new String[]{termuxBootstrapSecondStageFile}, null,
                                null, ExecutionCommand.Runner.APP_SHELL.getName(), false);
                        executionCommand.commandLabel = "Termux Bootstrap Second Stage Command";
                        executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_NORMAL;
                        AppShell appShell = AppShell.execute(activity, executionCommand, null, new TermuxShellEnvironment(), null, true);
                        if (appShell == null || !executionCommand.isSuccessful() || executionCommand.resultData.exitCode != 0) {
                            // If the domain flipped while the bootstrap was being installed, the prefix is
                            // intact and only unexecutable. Keep it: deleting it destroys the evidence and
                            // makes every retry download the bootstrap again to fail the same way.
                            String domain = getRestrictedSELinuxDomain();
                            if (domain != null) {
                                Logger.logError(LOG_TAG, "Keeping the termux prefix directory, the second stage could not be executed in the restricted SELinux domain \"" + domain + "\".");
                                showExecRestrictedErrorDialog(activity, domain);
                                return;
                            }
                            // Delete prefix directory as otherwise when app is restarted, the broken prefix directory would be used and logged into
                            error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                            if (error != null)
                                Logger.logErrorExtended(LOG_TAG, error.toString());

                            showBootstrapErrorDialog(activity, whenDone, MarkdownUtils.getMarkdownCodeForString(executionCommand.toString(), true));
                            return;
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");
                    // Recreate env file since termux prefix was wiped earlier
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);
                    activity.runOnUiThread(whenDone);
                } catch (final Exception e) {
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));
                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }

    /**
     * Report that SELinux forbids the app from executing anything it installs, and exit.
     * <p>
     * This is not offered a retry: nothing the app can do changes the domain it was assigned, and
     * the "try again" of {@link #showBootstrapErrorDialog} would delete the prefix that is intact
     * and only unexecutable.
     */
    private static void showExecRestrictedErrorDialog(@NonNull Activity activity, @NonNull String domain) {
        String message = activity.getString(R.string.bootstrap_error_exec_restricted_message,
            MarkdownUtils.getMarkdownCodeForString(domain, false),
            getSharedUidPackagesBlockingExecution(activity));
        Logger.logError(LOG_TAG, message);
        sendBootstrapCrashReportNotification(activity, message);
        activity.runOnUiThread(() -> {
            try {
                MessageDialogUtils.exitAppWithErrorMessage(activity, activity.getString(R.string.bootstrap_error_title), message);
            } catch (WindowManager.BadTokenException e) {
                // Activity already dismissed - ignore.
            }
        });
    }

    /**
     * Get the SELinux domain the app is running in if that domain forbids executing files under the
     * app data directory, which makes every binary under {@code $PREFIX} unrunnable.
     *
     * @return Returns the domain, or {@code null} if execution is not restricted.
     */
    @Nullable
    private static String getRestrictedSELinuxDomain() {
        String seContext = SELinuxUtils.getContext();
        if (!SELinuxUtils.isAppDataFileExecutionRestricted(Build.VERSION.SDK_INT, seContext))
            return null;
        return SELinuxUtils.getDomain(seContext);
    }

    /**
     * Get a markdown list of the installed packages that share the app's user id and target a sdk
     * version that puts the whole shared user in a restricted SELinux domain, which is what causes
     * {@link #getRestrictedSELinuxDomain()} to be non-null in the first place.
     *
     * @return Returns the list, or an empty {@link String} if there is nothing to name.
     */
    @NonNull
    private static String getSharedUidPackagesBlockingExecution(@NonNull Context context) {
        String[] packageNames;
        try {
            packageNames = context.getPackageManager().getPackagesForUid(Process.myUid());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get the packages sharing the app user id", e);
            return "";
        }
        if (packageNames == null || packageNames.length < 2)
            return "";
        List<String> culprits = new ArrayList<>();
        for (String packageName : packageNames) {
            if (context.getPackageName().equals(packageName))
                continue;
            ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoForPackage(context, packageName);
            if (applicationInfo == null)
                continue;
            int targetSdk = PackageUtils.getTargetSDKForPackage(applicationInfo);
            if (targetSdk >= Build.VERSION_CODES.Q)
                culprits.add("- " + MarkdownUtils.getMarkdownCodeForString(packageName, false) + " (target sdk " + targetSdk + ")");
        }
        if (culprits.isEmpty())
            return "";
        return "\n\n" + context.getString(R.string.bootstrap_error_exec_restricted_shared_uid_packages) + "\n" + TextUtils.join("\n", culprits);
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);
        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);
        activity.runOnUiThread(() -> {
            try {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.bootstrap_error_title).setMessage(R.string.bootstrap_error_body).setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                    dialog.dismiss();
                    activity.finish();
                }).setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                    dialog.dismiss();
                    FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        final String title = TermuxConstants.TERMUX_APP_NAME + " Bootstrap Error";
        // Add info of all install Termux plugin apps as well since their target sdk or installation
        // on external/portable sd card can affect Termux app files directory access or exec.
        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG, title, null, "## " + title + "\n\n" + message + "\n\n" + TermuxUtils.getTermuxDebugMarkdownString(activity), true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        final String title = TermuxConstants.TERMUX_APP_NAME + " Setup Storage Error";
        Logger.logInfo(LOG_TAG, "Setting up storage symlinks.");
        new Thread() {

            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;
                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, LOG_TAG, error.getMessage());
                        Logger.logErrorExtended(LOG_TAG, "Setup Storage Error\n" + error.toString());
                        TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null, "## " + title + "\n\n" + Error.getErrorMarkdownString(error), true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                        return;
                    }
                    Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"" + Environment.getExternalStorageDirectory().getAbsolutePath() + "\".");
                    // Get primary storage root "/storage/emulated/0" symlink
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());
                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());
                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());
                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());
                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());
                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());
                    File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                    Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                        Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }
                    // Dir 0 should ideally be for primary storage
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                    // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053
                    // Create "Android/data/com.termux" symlinks
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null)
                                continue;
                            String symlinkName = "external-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }
                    // Create "Android/media/com.termux" symlinks
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null)
                                continue;
                            String symlinkName = "media-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }
                    Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                    Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                    TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null, "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)), true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                }
            }
        }.start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }

    /**
     * The VAJ bootstrap is not embedded in the APK. It is downloaded on first run from the VAJ
     * bootstrap-artifacts release and verified against the accepted SHA-256 before extraction, so a
     * corrupted or tampered download can never reach the prefix.
     */
    private static InputStream openBootstrapInputStream(Activity activity) throws IOException {
        String archName = determineTermuxArchName();
        if (!BOOTSTRAP_ARCH.equals(archName)) {
            throw new RuntimeException("Unsupported architecture for VAJ bootstrap: " + archName);
        }

        File cacheFile = new File(activity.getCacheDir(), "bootstrap-" + BOOTSTRAP_ARCH + ".zip");
        if (!cacheFile.exists() || !verifyBootstrapChecksum(cacheFile, BOOTSTRAP_SHA256)) {
            if (cacheFile.exists() && !cacheFile.delete()) {
                throw new IOException("Failed to delete stale bootstrap cache file: " + cacheFile);
            }
            Logger.logInfo(LOG_TAG, "Downloading VAJ bootstrap for " + archName + " from " + BOOTSTRAP_URL);
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("SHA-256 algorithm unavailable", e);
            }
            // URL.openStream() follows the GitHub release -> CDN redirect by default.
            try (InputStream in = new URL(BOOTSTRAP_URL).openStream();
                 DigestInputStream digestInput = new DigestInputStream(in, digest);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = digestInput.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } catch (IOException e) {
                cacheFile.delete();
                throw e;
            }
            String checksum = toHex(digest.digest());
            if (!checksum.equals(BOOTSTRAP_SHA256)) {
                cacheFile.delete();
                throw new IOException("VAJ bootstrap checksum mismatch: expected " + BOOTSTRAP_SHA256 + ", actual " + checksum);
            }
            Logger.logInfo(LOG_TAG, "VAJ bootstrap downloaded and verified: " + checksum);
        } else {
            Logger.logInfo(LOG_TAG, "Reusing cached VAJ bootstrap for " + archName);
        }

        InputStream bootstrapStream = new FileInputStream(cacheFile);
        // Unlink the on-disk copy now: the open descriptor keeps the ~180 MB readable through
        // extraction, and the space is reclaimed as soon as the caller closes the stream.
        cacheFile.delete();
        return bootstrapStream;
    }

    private static boolean verifyBootstrapChecksum(File file, String expectedChecksum) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (digestInput.read(buffer) != -1) { /* consume */ }
            }
            return toHex(digest.digest()).equals(expectedChecksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String determineTermuxArchName() {
        // Note that we cannot use System.getProperty("os.arch") since that may give e.g. "aarch64"
        // while a 64-bit runtime may not be installed (like on the Samsung Galaxy S5 Neo).
        // Instead we search through the supported abi:s on the device, see:
        // http://developer.android.com/ndk/guides/abis.html
        // Note that we search for abi:s in preferred order (the ordering of the
        // Build.SUPPORTED_ABIS list) to avoid e.g. installing arm on an x86 system where arm
        // emulation is available.
        for (String androidArch : Build.SUPPORTED_ABIS) {
            switch(androidArch) {
                case "arm64-v8a":
                    return "aarch64";
                case "armeabi-v7a":
                    return "arm";
                case "x86_64":
                    return "x86_64";
                case "x86":
                    return "i686";
            }
        }
        throw new RuntimeException("Unable to determine arch from Build.SUPPORTED_ABIS =  " + Arrays.toString(Build.SUPPORTED_ABIS));
    }
}
