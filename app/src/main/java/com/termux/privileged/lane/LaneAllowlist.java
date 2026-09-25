package com.termux.privileged.lane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decides which binary a lane request may run as shell: a regular file under the app's own
 * files directory whose sha256 is the digest of a {@code binary} row carrying {@code priv=shizuku}
 * in the active tlstore catalog. The catalog row's name is what the file is staged under.
 *
 * <p>This guards against accidents — a stray path, a rebuilt or tampered binary, a client
 * pointing at the wrong file — not against code already running as this uid: same-uid code can
 * open the socket, and a Shizuku permission is a grant to the whole uid anyway.
 */
public final class LaneAllowlist {

    /** What a request resolved to once it passed. */
    public static final class Resolved {
        /** The canonical file, opened by the app and streamed to the service, which cannot read it itself. */
        @NonNull public final File file;
        @NonNull public final String digest;
        /** The catalog row's name: the staging name on the shell side. */
        @NonNull public final String name;

        Resolved(@NonNull File file, @NonNull String digest, @NonNull String name) {
            this.file = file;
            this.digest = digest;
            this.name = name;
        }
    }

    /** A digest remembered for a file, valid while its mtime and size are unchanged. */
    private static final class DigestEntry {
        final long lastModified;
        final long length;
        @NonNull final String digest;

        DigestEntry(long lastModified, long length, @NonNull String digest) {
            this.lastModified = lastModified;
            this.length = length;
            this.digest = digest;
        }
    }

    @NonNull private final File filesDir;
    @NonNull private final File baseCatalog;
    @NonNull private final File userCatalog;
    /** Canonical path to its last digest, so a launch re-hashes only a file that changed. */
    private final Map<String, DigestEntry> digests = new HashMap<>();

    public LaneAllowlist(@NonNull File filesDir, @NonNull File baseCatalog, @NonNull File userCatalog) {
        this.filesDir = filesDir;
        this.baseCatalog = baseCatalog;
        this.userCatalog = userCatalog;
    }

    /**
     * @throws LaneRequest.Refused with the message the client prints when the path fails a check.
     * @throws IOException when the file or the catalog cannot be read.
     */
    @NonNull
    public Resolved resolve(@NonNull String requestedPath) throws LaneRequest.Refused, IOException {
        File canonical = new File(requestedPath).getCanonicalFile();
        String root = filesDir.getCanonicalPath();
        if (!isUnder(canonical.getPath(), root)) {
            throw new LaneRequest.Refused("only a file under " + root + " may run as shell (got " + canonical + ")");
        }
        // The canonical path has no symlinks left in it; NOFOLLOW is a belt for a race, not a policy.
        if (!Files.isRegularFile(canonical.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new LaneRequest.Refused(canonical + " is not a regular file");
        }
        String digest = digestOf(canonical);
        PrivilegedCatalog.Row row = PrivilegedCatalog.load(baseCatalog, userCatalog).findByDigest(digest);
        if (row == null) {
            throw new LaneRequest.Refused(canonical.getName()
                + " is not a catalog binary marked priv=shizuku (sha256 " + digest.substring(0, 12) + "…)");
        }
        return new Resolved(canonical, digest, row.name);
    }

    /**
     * True when {@code canonicalPath} is {@code canonicalRoot} or inside it. Both are canonical
     * already, so this is a plain prefix test on path components: {@code /a/bc} is not under
     * {@code /a/b}.
     */
    @VisibleForTesting
    static boolean isUnder(@NonNull String canonicalPath, @NonNull String canonicalRoot) {
        if (canonicalRoot.equals("/")) return canonicalPath.startsWith("/");
        String root = canonicalRoot.endsWith("/") ? canonicalRoot.substring(0, canonicalRoot.length() - 1) : canonicalRoot;
        return canonicalPath.equals(root) || canonicalPath.startsWith(root + "/");
    }

    @NonNull
    private synchronized String digestOf(@NonNull File file) throws IOException {
        String key = file.getPath();
        long lastModified = file.lastModified();
        long length = file.length();
        DigestEntry cached = digests.get(key);
        if (cached != null && cached.lastModified == lastModified && cached.length == length) {
            return cached.digest;
        }
        String digest = sha256(file);
        digests.put(key, new DigestEntry(lastModified, length, digest));
        return digest;
    }

    /** Lower-case hex sha256 of the file's contents. */
    @NonNull
    public static String sha256(@NonNull File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = in.read(buffer)) > 0) md.update(buffer, 0, read);
        }
        return toHex(md.digest());
    }

    @NonNull
    static String toHex(@NonNull byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    @VisibleForTesting
    @Nullable
    synchronized String cachedDigest(@NonNull File canonical) {
        DigestEntry entry = digests.get(canonical.getPath());
        return entry == null ? null : entry.digest;
    }
}
