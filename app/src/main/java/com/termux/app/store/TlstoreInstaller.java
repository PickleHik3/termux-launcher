package com.termux.app.store;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Puts {@code tlstore} (and its {@code tl}/{@code tls} aliases) in the prefix, the way the
 * launcher already puts {@code launcherctl} and {@code termux-x11} there.
 *
 * <p>It refuses to overwrite a {@code tlstore}, {@code tl} or {@code tls} it did not write.
 * Someone who already has a command by one of those names has made a choice, and a home screen
 * must not quietly take it out from under them. Every file is written beside its destination and
 * moved into place, so a reader never sees a half-written command, and a destination that is a
 * foreign symlink is never followed.
 *
 * <p>It also drops {@code ~/.termux/motd.sh}, the greeting Termux's {@code login} script prints
 * on every new session, so a user meets {@code tlstore} without having to go looking for it. The
 * file follows the same never-overwrite-a-user's-own-thing rule as the CLI: it is written the
 * first time and rewritten only while its sha256 still matches what this class wrote last time
 * (recorded in {@code .motd-sha256} beside the marker); a file that has drifted from that — the
 * user edited or replaced it — is left alone for good.
 *
 * <p>All of it is disk I/O; {@link #installAsync} keeps it off the main thread.
 */
public final class TlstoreInstaller {

    private static final String LOG_TAG = "TlstoreInstaller";

    /** Bumped whenever the written files change, so an upgrade rewrites them once. */
    @VisibleForTesting static final int VERSION = 1;

    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String BIN_DIR = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    private static final String LIBEXEC_DIR = PREFIX + "/libexec/termux-launcher/tlstore";
    private static final String DATA_HOME_DIR = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH;

    private static final String TLSTORE_ASSET = "tlstore/tlstore";
    private static final String CATALOG_ASSET = "tlstore/catalog.tsv";
    private static final String TRUSTED_KEY_ASSET = "tlstore/trusted.pub";
    private static final String MOTD_ASSET = "tlstore/motd.sh";

    /** The name every alias points at; also the {@code #!}-less command itself. */
    private static final String TLSTORE_NAME = "tlstore";

    /** Every marker this launcher has ever written, so "is this ours?" survives an upgrade. */
    @VisibleForTesting static final String MARKER_PREAMBLE = "# written by termux-launcher";

    /** One thread for the prefix writes: they are rare and must never overlap. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tlstore-installer");
        thread.setDaemon(true);
        return thread;
    });

    /** What the install attempt found. */
    public static final class Result {

        public enum Kind { NO_PREFIX, UP_TO_DATE, FOREIGN_COMMAND, INSTALLED, FAILED }

        /** The prefix is not there yet — the bootstrap has not run. */
        public static final Result NO_PREFIX = new Result(Kind.NO_PREFIX, null);
        /** Nothing to do: the same version is already written. */
        public static final Result UP_TO_DATE = new Result(Kind.UP_TO_DATE, null);
        /** {@code tlstore} (or an alias) is in place and ours. */
        public static final Result INSTALLED = new Result(Kind.INSTALLED, null);
        public static final Result FAILED = new Result(Kind.FAILED, null);

        @NonNull public final Kind kind;
        /**
         * Which existing command blocked the install ({@code "tlstore"}, {@code "tl"} or
         * {@code "tls"}); {@code null} unless {@link #kind} is {@link Kind#FOREIGN_COMMAND}.
         */
        @Nullable public final String foreignCommand;

        private Result(@NonNull Kind kind, @Nullable String foreignCommand) {
            this.kind = kind;
            this.foreignCommand = foreignCommand;
        }

        @NonNull
        private static Result foreignCommand(@NonNull String name) {
            return new Result(Kind.FOREIGN_COMMAND, name);
        }

        @NonNull
        @Override
        public String toString() {
            return foreignCommand == null ? kind.toString() : kind + "(" + foreignCommand + ")";
        }
    }

    /** Where the shipped files' bytes come from: the app's assets, or a fixture. */
    interface AssetSource {
        @NonNull InputStream open(@NonNull String name) throws IOException;
    }

    @NonNull private final File binDir;
    @NonNull private final File libexecDir;
    /** {@code ~/.termux}, where {@code motd.sh} goes; its parent is the user's home directory. */
    @NonNull private final File dataHomeDir;
    @NonNull private final String applicationId;
    /** The app's versionName: every release rewrites the files, so a changed asset ships. */
    @NonNull private final String release;
    @NonNull private final AssetSource assets;

    @VisibleForTesting
    TlstoreInstaller(@NonNull File binDir, @NonNull File libexecDir, @NonNull File dataHomeDir,
                     @NonNull String applicationId, @NonNull String release,
                     @NonNull AssetSource assets) {
        this.binDir = binDir;
        this.libexecDir = libexecDir;
        this.dataHomeDir = dataHomeDir;
        this.applicationId = applicationId;
        this.release = release;
        this.assets = assets;
    }

    /** The installer for this launcher's own prefix. */
    @NonNull
    static TlstoreInstaller forPrefix(@NonNull Context context) {
        Context app = context.getApplicationContext();
        String release;
        try {
            release = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
        } catch (Exception e) {
            release = "unknown";
        }
        return new TlstoreInstaller(new File(BIN_DIR), new File(LIBEXEC_DIR),
            new File(DATA_HOME_DIR), app.getPackageName(), release == null ? "unknown" : release,
            name -> app.getAssets().open(name));
    }

    // ---- The static face the launcher uses -------------------------------------------------

    /**
     * Write the CLI and its aliases if they are missing or out of date, off the main thread, and
     * report on it. Safe to call on every app start: an up-to-date install is one file read.
     */
    public static void installAsync(@NonNull Context context, @NonNull Consumer<Result> onResult) {
        TlstoreInstaller installer = forPrefix(context);
        Handler main = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() -> {
            Result result = installer.install();
            main.post(() -> onResult.accept(result));
        });
    }

    // ---- The work ---------------------------------------------------------------------------

    @NonNull File tlstoreScript() { return new File(binDir, "tlstore"); }
    @NonNull File tlAlias() { return new File(binDir, "tl"); }
    @NonNull File tlsAlias() { return new File(binDir, "tls"); }
    @NonNull File catalogFile() { return new File(libexecDir, "catalog.tsv"); }
    @NonNull File trustedKeyFile() { return new File(libexecDir, "trusted.pub"); }
    @NonNull File markerFile() { return new File(libexecDir, ".installed"); }
    @NonNull File motdFile() { return new File(dataHomeDir, "motd.sh"); }
    @NonNull File motdShaFile() { return new File(libexecDir, ".motd-sha256"); }

    @NonNull
    Result install() {
        if (!binDir.isDirectory()) return Result.NO_PREFIX;
        String marker = MARKER_PREAMBLE + " v" + VERSION + " " + applicationId + " " + release + "\n";
        if (marker.equals(read(markerFile()))) return Result.UP_TO_DATE;
        String foreign = foreignCommandName();
        if (foreign != null) {
            Logger.logInfo(LOG_TAG, "Leaving a " + foreign + " we did not write alone");
            return Result.foreignCommand(foreign);
        }
        try {
            if (!libexecDir.isDirectory() && !libexecDir.mkdirs()) {
                throw new IOException("Failed to create " + libexecDir);
            }
            try (InputStream in = assets.open(TLSTORE_ASSET)) {
                writeAtomically(tlstoreScript(), in, true, true);
            }
            writeSymlinkAtomically(tlAlias(), TLSTORE_NAME);
            writeSymlinkAtomically(tlsAlias(), TLSTORE_NAME);
            try (InputStream in = assets.open(CATALOG_ASSET)) {
                writeAtomically(catalogFile(), in, true, false);
            }
            // The signing key may ship in a later build; an install must not fail for its sake.
            try (InputStream in = assets.open(TRUSTED_KEY_ASSET)) {
                writeAtomically(trustedKeyFile(), in, true, false);
            } catch (IOException e) {
                Logger.logInfo(LOG_TAG, "No trusted.pub asset yet; installing tlstore without it");
            }
            installMotd();
            writeAtomically(markerFile(), bytes(marker), true, false);
            return Result.INSTALLED;
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install tlstore: " + e.getMessage());
            return Result.FAILED;
        }
    }

    /**
     * The name of a {@code tlstore}/{@code tl}/{@code tls} in {@code bin} that is not ours, or
     * {@code null} if all that exist there are ours (or none exist).
     */
    @Nullable
    String foreignCommandName() {
        if (isForeignScript(tlstoreScript())) return "tlstore";
        if (isForeignAlias(tlAlias())) return "tl";
        if (isForeignAlias(tlsAlias())) return "tls";
        return null;
    }

    /** True while a {@code tlstore} exists that is not our marker-carrying, non-symlink script. */
    private boolean isForeignScript(@NonNull File file) {
        if (Files.isSymbolicLink(file.toPath())) return true;
        if (!file.exists()) return false;
        String head = readHead(file, 4096);
        return head == null || !head.contains(MARKER_PREAMBLE);
    }

    /** True while a {@code tl}/{@code tls} exists that is not our symlink to {@code tlstore}. */
    private boolean isForeignAlias(@NonNull File file) {
        if (!Files.isSymbolicLink(file.toPath())) return file.exists();
        try {
            return !TLSTORE_NAME.equals(Files.readSymbolicLink(file.toPath()).toString());
        } catch (IOException e) {
            return true;
        }
    }

    // ---- The greeting ------------------------------------------------------------------------

    /**
     * Write {@code ~/.termux/motd.sh} the first time, and rewrite it on an asset change only
     * while its sha256 still matches the sha256 this class recorded the last time it wrote the
     * file — proof the user never touched it. Anything else about the existing file (missing
     * record, mismatched sha) means someone else owns it now, so it is left alone; the whole
     * install still counts as {@link Result.Kind#INSTALLED}. A missing home directory (the
     * bootstrap has not run) is not an error: there is nowhere to put the file yet.
     */
    private void installMotd() {
        File home = dataHomeDir.getParentFile();
        if (home == null || !home.isDirectory()) {
            Logger.logDebug(LOG_TAG, "Skipping motd.sh: " + home + " does not exist yet");
            return;
        }
        try {
            byte[] asset;
            try (InputStream in = assets.open(MOTD_ASSET)) {
                asset = readAll(in);
            }
            String assetSha = sha256Hex(asset);
            File motd = motdFile();
            if (motd.isFile()) {
                String currentSha = sha256Hex(Files.readAllBytes(motd.toPath()));
                String previousSha = read(motdShaFile());
                if (previousSha == null || !previousSha.equals(currentSha)) {
                    Logger.logDebug(LOG_TAG, "Leaving " + motd + " alone; its content does not "
                        + "match what this launcher last wrote there");
                    return;
                }
                if (currentSha.equals(assetSha)) return; // already the current asset
            }
            if (!dataHomeDir.isDirectory() && !dataHomeDir.mkdirs()) {
                throw new IOException("Failed to create " + dataHomeDir);
            }
            writeAtomically(motd, new java.io.ByteArrayInputStream(asset), true, true);
            writeAtomically(motdShaFile(), bytes(assetSha), true, false);
        } catch (IOException e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install motd.sh: " + e.getMessage());
        }
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    @NonNull
    private static String sha256Hex(@NonNull byte[] data) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required Java algorithm", e);
        }
    }

    // ---- Files ------------------------------------------------------------------------------

    /**
     * Write {@code content} to a temporary file beside {@code destination}, give it its final
     * mode, and move it into place in one step. A destination that is a symlink is refused rather
     * than followed: the launcher writes its own files, never through someone else's link.
     *
     * @param ownerWritable whether the owner keeps write access
     * @param executable    whether everyone may execute it (the {@code tlstore} script)
     */
    private static void writeAtomically(@NonNull File destination, @NonNull InputStream content,
                                        boolean ownerWritable, boolean executable)
            throws IOException {
        if (Files.isSymbolicLink(destination.toPath())) {
            throw new IOException(destination + " is a symlink; refusing to write through it");
        }
        File dir = destination.getParentFile();
        if (dir == null) throw new IOException(destination + " has no directory");
        File temp = File.createTempFile(".termux-tlstore-", ".tmp", dir);
        try {
            try (OutputStream out = new FileOutputStream(temp, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = content.read(buffer)) > 0) out.write(buffer, 0, read);
            }
            // Mode first, then move, so the file is never observable half-permissioned.
            temp.setReadable(false, false);
            temp.setWritable(false, false);
            temp.setExecutable(false, false);
            temp.setReadable(true, false);
            if (ownerWritable) temp.setWritable(true, true);
            if (executable) temp.setExecutable(true, false);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /**
     * Create a relative symlink pointing at {@code target} beside {@code destination}, and move it
     * into place atomically, replacing whatever is there. Only called once the caller has already
     * established that an existing destination is ours, so replacing it is safe.
     */
    private static void writeSymlinkAtomically(@NonNull File destination, @NonNull String target)
            throws IOException {
        File dir = destination.getParentFile();
        if (dir == null) throw new IOException(destination + " has no directory");
        File temp = new File(dir, ".termux-tlstore-" + UUID.randomUUID() + ".tmp");
        try {
            Path link = Files.createSymbolicLink(temp.toPath(), Paths.get(target));
            Files.move(link, destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    @NonNull
    private static InputStream bytes(@NonNull String content) {
        return new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /** The first {@code limit} bytes as text: the marker sits in a script's opening lines. */
    @Nullable
    private static String readHead(@NonNull File file, int limit) {
        if (!file.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[limit];
            int total = 0;
            int read;
            while (total < limit && (read = in.read(buffer, total, limit - total)) > 0) total += read;
            return new String(buffer, 0, total, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String read(@NonNull File file) {
        if (!file.isFile() || file.length() > 64 * 1024) return null;
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
