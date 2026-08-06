package com.termux.app.fonts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fetches a {@link FontCatalog.Family}'s faces into a staging directory.
 *
 * <p>Mirrors {@code com.termux.ai.TaiModelDownloader}: HTTPS only, resumable through
 * {@code Range} requests when the server answers 206, SHA-256 verified against the catalog,
 * temp {@code .part} file plus atomic rename, and progress reported as it goes.
 *
 * <p>Two extra defences the model downloader does not need:
 * <ul>
 *   <li>Archive members are extracted through {@link #extractMember} which re-applies the
 *       zip-slip guard, caps the member count and caps the uncompressed size, so a hostile
 *       archive cannot write outside the staging directory or fill the data partition.</li>
 *   <li>Every face is loaded through {@link TypefaceProbe} before it is handed back, so a
 *       truncated or corrupt font never reaches {@code ~/.termux} and never reaches the
 *       config the terminal reads at start.</li>
 * </ul>
 *
 * <p>Nothing here touches the UI thread or the installed font tree: {@link #stageFamily}
 * blocks, writes only inside the staging directory it is given, and cleans that directory up
 * on any failure.
 */
public final class FontDownloader {

    /** Per-file ceiling, matching {@code TerminalFontLoader}'s own font size cap. */
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Ceiling on everything one family may transfer. */
    static final long MAX_FAMILY_BYTES = 96L * 1024L * 1024L;
    /** A font archive with more entries than this is not a font archive. */
    static final int MAX_ZIP_ENTRIES = 4096;
    /** Ceiling on a single extracted member, independent of what the zip header claims. */
    static final long MAX_MEMBER_BYTES = MAX_FILE_BYTES;

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_BYTES = 64 * 1024;
    /** Progress is reported at most once per megabyte so a slow link is not a callback storm. */
    private static final long PROGRESS_STEP_BYTES = 1024L * 1024L;

    /** Coarse download state, in the order a successful install passes through them. */
    public enum State { IDLE, DOWNLOADING, VERIFYING, EXTRACTING, INSTALLING, INSTALLED, FAILED, CANCELLED }

    /** Immutable progress snapshot. Safe to hand across threads. */
    public static final class Progress {
        @NonNull public final String familyId;
        @NonNull public final State state;
        /** Currently transferred bytes of {@link #totalBytes}. */
        public final long bytesRead;
        /** Expected total, or {@code 0} when unknown. */
        public final long totalBytes;
        /** Human-readable name of the file in flight, or {@code ""}. */
        @NonNull public final String currentFile;
        /** Failure message when {@link #state} is {@link State#FAILED}, else {@code ""}. */
        @NonNull public final String error;

        public Progress(@NonNull String familyId, @NonNull State state, long bytesRead, long totalBytes,
                        @NonNull String currentFile, @NonNull String error) {
            this.familyId = familyId;
            this.state = state;
            this.bytesRead = bytesRead;
            this.totalBytes = totalBytes;
            this.currentFile = currentFile;
            this.error = error;
        }

        /** Percent complete in hundredths, for a 0..10000 progress bar. Zero when unknown. */
        public int permyriad() {
            if (totalBytes <= 0L) return 0;
            long value = bytesRead * 10000L / totalBytes;
            return (int) Math.max(0L, Math.min(10000L, value));
        }

        public boolean isActive() {
            return state == State.DOWNLOADING || state == State.VERIFYING
                || state == State.EXTRACTING || state == State.INSTALLING;
        }
    }

    /** Progress sink. Called on the downloader's own thread, never on the main thread. */
    public interface ProgressCallback {
        void onProgress(@NonNull Progress progress);
    }

    /** Cooperative cancellation, polled between buffers. */
    public interface CancelSignal {
        boolean isCancelled();
    }

    /**
     * "Does Android accept this file as a font?" Injected so the JVM tests can exercise the
     * download and extraction paths without a live {@code Typeface} implementation.
     */
    public interface TypefaceProbe {
        boolean loads(@NonNull File file);
    }

    /** Thrown when the user cancelled; separated from real failures for reporting. */
    public static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelled");
        }
    }

    /** The real probe: a font Android cannot parse is a font we refuse to install. */
    public static final TypefaceProbe ANDROID_TYPEFACE_PROBE = new TypefaceProbe() {
        @Override
        public boolean loads(@NonNull File file) {
            try {
                android.graphics.Typeface typeface = android.graphics.Typeface.createFromFile(file);
                // createFromFile falls back to the default typeface on some API levels instead of
                // throwing, so an identity match against the default counts as a rejection.
                return typeface != null && !typeface.equals(android.graphics.Typeface.DEFAULT);
            } catch (Throwable t) {
                return false;
            }
        }
    };

    @NonNull private final TypefaceProbe typefaceProbe;
    @Nullable private final CancelSignal cancelSignal;

    public FontDownloader() {
        this(ANDROID_TYPEFACE_PROBE, null);
    }

    public FontDownloader(@NonNull TypefaceProbe typefaceProbe, @Nullable CancelSignal cancelSignal) {
        this.typefaceProbe = typefaceProbe;
        this.cancelSignal = cancelSignal;
    }

    /**
     * Downloads and verifies every face of {@code family} into {@code stagingDir}, named after
     * {@link FontCatalog.FaceSlot#fileName}.
     *
     * <p>Nothing is committed anywhere else: the caller (see {@link FontInstaller}) moves the
     * staged faces into place once they are all present and all load. On any failure the
     * staging directory is emptied, so a half-download never looks like a complete one.
     *
     * @return the staged face files, keyed by slot.
     * @throws IOException on any transport, verification, extraction or parse failure.
     */
    @NonNull
    public Map<FontCatalog.FaceSlot, File> stageFamily(@NonNull FontCatalog.Family family,
                                                       @NonNull File stagingDir,
                                                       @Nullable ProgressCallback callback)
        throws IOException {
        if (family.downloadBytes > MAX_FAMILY_BYTES) {
            throw new IOException(family.displayName + " needs " + family.downloadBytes
                + " bytes, over the " + MAX_FAMILY_BYTES + " byte limit for one family");
        }
        if (!stagingDir.isDirectory() && !stagingDir.mkdirs()) {
            throw new IOException("cannot create staging directory " + stagingDir);
        }
        long total = family.downloadBytes;
        long transferred = 0L;
        Map<FontCatalog.FaceSlot, File> staged = new EnumMap<>(FontCatalog.FaceSlot.class);
        File archiveDir = new File(stagingDir, "archives");
        try {
            // Archives first: several faces usually share one, and a shared archive must only
            // be fetched once no matter how many faces name it.
            Map<String, File> archiveFiles = new LinkedHashMap<>();
            for (FontCatalog.Archive archive : family.archives) {
                if (!archiveDir.isDirectory() && !archiveDir.mkdirs()) {
                    throw new IOException("cannot create archive directory " + archiveDir);
                }
                File target = new File(archiveDir, archiveFileName(archive.url, archiveFiles.size()));
                report(callback, family.id, State.DOWNLOADING, transferred, total, shortName(archive.url), "");
                download(archive.url, target, archive.sha256, archive.sizeBytes,
                    family.id, transferred, total, shortName(archive.url), callback);
                transferred += archive.sizeBytes;
                archiveFiles.put(archive.url, target);
            }

            for (Map.Entry<FontCatalog.FaceSlot, FontCatalog.Face> entry : family.faces.entrySet()) {
                FontCatalog.FaceSlot slot = entry.getKey();
                FontCatalog.Face face = entry.getValue();
                File target = new File(stagingDir, slot.fileName);
                if (face.isArchiveMember()) {
                    File archive = archiveFiles.get(face.zipUrl);
                    if (archive == null) {
                        throw new IOException(family.id + "/" + slot.key + ": archive was not downloaded");
                    }
                    report(callback, family.id, State.EXTRACTING, transferred, total, face.memberPath, "");
                    extractMember(archive, face.memberPath, target);
                    verifyDigest(target, face.sha256, slot.key);
                } else {
                    report(callback, family.id, State.DOWNLOADING, transferred, total, shortName(face.url), "");
                    download(face.url, target, face.sha256, face.sizeBytes,
                        family.id, transferred, total, shortName(face.url), callback);
                    transferred += face.sizeBytes;
                }
                report(callback, family.id, State.VERIFYING, transferred, total, slot.fileName, "");
                if (!typefaceProbe.loads(target)) {
                    throw new IOException(family.displayName + ": Android could not load the "
                        + slot.key + " face, so it was not installed");
                }
                staged.put(slot, target);
            }
            // The archives are several times the size of the faces they hold; dropping them the
            // moment extraction succeeds keeps the staging cost close to the install cost.
            deleteRecursively(archiveDir);
            return staged;
        } catch (IOException e) {
            deleteRecursively(archiveDir);
            for (FontCatalog.FaceSlot slot : FontCatalog.FaceSlot.values()) {
                deleteQuietly(new File(stagingDir, slot.fileName));
                deleteQuietly(new File(stagingDir, slot.fileName + ".part"));
            }
            throw e;
        }
    }

    /**
     * Downloads one URL to {@code target}, resuming an existing {@code .part} when the server
     * honours the {@code Range} request, then verifies the digest and renames atomically.
     */
    private void download(@NonNull String url, @NonNull File target, @NonNull String expectedSha256,
                          long expectedBytes, @NonNull String familyId, long baseTransferred,
                          long total, @NonNull String label, @Nullable ProgressCallback callback)
        throws IOException {
        if (!FontCatalog.isHttpsUrl(url)) throw new IOException("refusing non-https URL: " + url);
        if (expectedBytes <= 0L || expectedBytes > MAX_FILE_BYTES) {
            throw new IOException(label + ": declared size " + expectedBytes + " is outside 1.."
                + MAX_FILE_BYTES);
        }
        // A previously finished file with the right digest is a completed download; skip it. This
        // is what makes a retry after a mid-family failure cheap.
        if (target.isFile() && target.length() == expectedBytes && digestMatches(target, expectedSha256)) {
            return;
        }
        File partial = new File(target.getAbsolutePath() + ".part");
        long existing = partial.isFile() ? partial.length() : 0L;
        if (existing > expectedBytes) {
            // A partial longer than the whole file is not a partial of this file.
            deleteQuietly(partial);
            existing = 0L;
        }
        HttpURLConnection connection = null;
        try {
            connection = open(url, existing);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException(label + ": download failed with HTTP " + status);
            }
            boolean resumed = existing > 0L && status == 206;
            if (!resumed) existing = 0L;
            long written = existing;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new FileOutputStream(partial, resumed)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                long lastReported = written;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    throwIfCancelled();
                    written += read;
                    if (written > expectedBytes) {
                        throw new IOException(label + ": server sent more than the declared "
                            + expectedBytes + " bytes");
                    }
                    output.write(buffer, 0, read);
                    if (written - lastReported >= PROGRESS_STEP_BYTES) {
                        report(callback, familyId, State.DOWNLOADING,
                            baseTransferred + written, total, label, "");
                        lastReported = written;
                    }
                }
            }
            if (written != expectedBytes) {
                throw new IOException(label + ": expected " + expectedBytes
                    + " bytes but received " + written);
            }
            report(callback, familyId, State.VERIFYING, baseTransferred + written, total, label, "");
            verifyDigest(partial, expectedSha256, label);
            commit(partial, target);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Extracts one member of a zip archive to {@code target}.
     *
     * <p>Package-visible and free of any network or Android dependency so the crafted-zip tests
     * can drive it directly. Guards, in order: the member path must be relative and free of
     * {@code ..} segments, the archive must not declare more than {@link #MAX_ZIP_ENTRIES}
     * entries, the member must exist and not be a directory, and neither the declared nor the
     * actual uncompressed size may exceed {@link #MAX_MEMBER_BYTES}. The destination path comes
     * from this app rather than from the archive, so the guards are about what the archive
     * <em>claims</em>, which is the only thing an attacker controls here.
     */
    static void extractMember(@NonNull File archive, @NonNull String memberPath, @NonNull File target)
        throws IOException {
        extractMember(archive, memberPath, target, MAX_ZIP_ENTRIES, MAX_MEMBER_BYTES, MAX_FAMILY_BYTES);
    }

    /** Cap-parameterised form, so the tests can trip each limit without 64 MiB fixtures. */
    static void extractMember(@NonNull File archive, @NonNull String memberPath, @NonNull File target,
                              int maxEntries, long maxMemberBytes, long maxTotalBytes)
        throws IOException {
        if (!FontCatalog.isSafeRelativePath(memberPath)) {
            throw new IOException("unsafe archive member path '" + memberPath + "'");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create destination directory " + parent);
        }
        if (target.isDirectory()) throw new IOException(target + " is a directory");
        File partial = new File(target.getAbsolutePath() + ".part");
        try (ZipFile zip = new ZipFile(archive)) {
            int entries = zip.size();
            if (entries <= 0 || entries > maxEntries) {
                throw new IOException("archive declares " + entries + " entries, outside 1.."
                    + maxEntries);
            }
            // Re-validate every declared name, not just the one we want: a name that would escape
            // the destination means the archive is not what it claims to be, and the honest
            // response is to refuse the whole thing rather than trust the entry we happen to read.
            java.util.Enumeration<? extends ZipEntry> all = zip.entries();
            int seen = 0;
            long declaredTotal = 0L;
            while (all.hasMoreElements()) {
                ZipEntry candidate = all.nextElement();
                if (++seen > maxEntries) {
                    throw new IOException("archive holds more than " + maxEntries + " entries");
                }
                String name = candidate.getName();
                if (candidate.isDirectory()) {
                    if (!isSafeDirectoryName(name)) {
                        throw new IOException("archive holds an unsafe entry '" + name + "'");
                    }
                    continue;
                }
                if (!FontCatalog.isSafeRelativePath(name)) {
                    throw new IOException("archive holds an unsafe entry '" + name + "'");
                }
                long declared = candidate.getSize();
                if (declared > maxMemberBytes) {
                    throw new IOException("archive entry '" + name + "' declares " + declared
                        + " bytes, over the " + maxMemberBytes + " byte limit");
                }
                if (declared > 0L) {
                    declaredTotal += declared;
                    if (declaredTotal > maxTotalBytes) {
                        throw new IOException("archive declares more than " + maxTotalBytes
                            + " uncompressed bytes");
                    }
                }
            }
            ZipEntry entry = zip.getEntry(memberPath);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("archive has no member '" + memberPath + "'");
            }
            long written = 0L;
            try (InputStream input = zip.getInputStream(entry);
                 OutputStream output = new FileOutputStream(partial, false)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    // The header size is a claim; this is the measurement. A zip bomb lies in the
                    // header, so the running total is what actually stops it.
                    if (written > maxMemberBytes) {
                        throw new IOException("archive member '" + memberPath + "' expands past "
                            + maxMemberBytes + " bytes");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (written <= 0L) throw new IOException("archive member '" + memberPath + "' is empty");
            commit(partial, target);
        } catch (IOException e) {
            deleteQuietly(partial);
            throw e;
        }
    }

    /** Directory entries carry a trailing slash, which the file-path guard rejects. */
    private static boolean isSafeDirectoryName(@NonNull String name) {
        String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        return trimmed.isEmpty() || FontCatalog.isSafeRelativePath(trimmed);
    }

    /** Temp-then-rename, so a reader never sees a partially written font. */
    static void commit(@NonNull File partial, @NonNull File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("cannot replace " + target);
        }
        if (!partial.renameTo(target)) {
            throw new IOException("cannot finalize " + target);
        }
    }

    private static void verifyDigest(@NonNull File file, @NonNull String expectedSha256,
                                     @NonNull String label) throws IOException {
        String actual = sha256(file);
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new IOException(label + ": SHA-256 mismatch (expected " + expectedSha256
                + ", got " + actual + ")");
        }
    }

    private static boolean digestMatches(@NonNull File file, @NonNull String expectedSha256) {
        try {
            return expectedSha256.equalsIgnoreCase(sha256(file));
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    static String sha256(@NonNull File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder builder = new StringBuilder(64);
        for (byte value : digest.digest()) builder.append(String.format(Locale.US, "%02x", value));
        return builder.toString();
    }

    @NonNull
    private HttpURLConnection open(@NonNull String url, long offset) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-");
        return connection;
    }

    private void throwIfCancelled() throws IOException {
        if (cancelSignal != null && cancelSignal.isCancelled()) throw new CancelledException();
    }

    private static void report(@Nullable ProgressCallback callback, @NonNull String familyId,
                               @NonNull State state, long bytesRead, long total,
                               @NonNull String currentFile, @NonNull String error) {
        if (callback == null) return;
        callback.onProgress(new Progress(familyId, state, bytesRead, total, currentFile, error));
    }

    /** Last path segment of a URL, for progress labels. */
    @NonNull
    static String shortName(@NonNull String url) {
        int query = url.indexOf('?');
        String base = query >= 0 ? url.substring(0, query) : url;
        int slash = base.lastIndexOf('/');
        String name = slash >= 0 ? base.substring(slash + 1) : base;
        return name.isEmpty() ? url : name;
    }

    /** Stable, collision-free local name for a downloaded archive. */
    @NonNull
    private static String archiveFileName(@NonNull String url, int index) {
        String name = shortName(url).replaceAll("[^A-Za-z0-9._-]", "-");
        if (name.length() > 64) name = name.substring(name.length() - 64);
        return index + "-" + name;
    }

    static void deleteQuietly(@Nullable File file) {
        if (file != null && file.exists() && !file.delete()) {
            // Best effort: a leftover partial is retried or overwritten on the next attempt.
        }
    }

    /** Bounded recursive delete for the staging tree; depth is one or two in practice. */
    static void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        List<File> stack = new ArrayList<>();
        stack.add(file);
        int visited = 0;
        while (!stack.isEmpty() && visited++ < MAX_ZIP_ENTRIES) {
            File current = stack.remove(stack.size() - 1);
            File[] children = current.listFiles();
            if (children != null && children.length > 0) {
                stack.add(current);
                for (File child : children) stack.add(child);
                continue;
            }
            deleteQuietly(current);
        }
    }
}
