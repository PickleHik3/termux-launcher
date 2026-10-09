package com.termux.privileged.lane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The rows of the tlstore catalog the lane cares about: {@code kind} {@code binary} with the
 * option {@code priv=shizuku}. The file is the engine's TSV ({@code engine/tlstore} in the
 * tlstore repo): comment lines start with {@code #}, the first of them carries
 * {@code serial=<n>}, and the columns are {@code name kind version prefixes source digest target
 * requires options ...}, where {@code digest} is the binary's bare hex sha256 and {@code options}
 * is {@code ;}-separated {@code k=v} pairs.
 *
 * <p>Which file is "the" catalog follows the engine's {@code pick_catalog}: the user's refreshed
 * copy ({@code ~/.local/share/tlstore/catalog.tsv}) when it exists and its serial is newer than
 * the copy the app shipped ({@code $PREFIX/libexec/termux-launcher/tlstore/catalog.tsv}), the
 * app's copy otherwise.
 */
public final class PrivilegedCatalog {

    static final String OPTION_PRIV = "priv";
    static final String PRIV_SHIZUKU = "shizuku";
    static final String KIND_BINARY = "binary";

    private static final int COLUMN_NAME = 0;
    private static final int COLUMN_KIND = 1;
    private static final int COLUMN_DIGEST = 5;
    private static final int COLUMN_OPTIONS = 8;

    /** A {@code binary} row whose options carry {@code priv=shizuku}. */
    public static final class Row {
        @NonNull public final String name;
        /** Lower-case hex sha256 of the binary, as the catalog carries it. */
        @NonNull public final String digest;

        Row(@NonNull String name, @NonNull String digest) {
            this.name = name;
            this.digest = digest;
        }
    }

    @NonNull private final List<Row> rows;

    private PrivilegedCatalog(@NonNull List<Row> rows) {
        this.rows = rows;
    }

    /** The row whose digest matches, or null: only privileged binary rows are here to match against. */
    @Nullable
    public Row findByDigest(@NonNull String digest) {
        String wanted = digest.toLowerCase(Locale.ROOT);
        for (Row row : rows) {
            if (row.digest.equals(wanted)) return row;
        }
        return null;
    }

    @VisibleForTesting
    int size() {
        return rows.size();
    }

    /** Reads whichever of the two catalogs the engine would use; an empty catalog when neither is there. */
    @NonNull
    public static PrivilegedCatalog load(@NonNull File baseCatalog, @NonNull File userCatalog) throws IOException {
        File chosen = pick(baseCatalog, userCatalog);
        if (chosen == null) return new PrivilegedCatalog(Collections.emptyList());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Files.newInputStream(chosen.toPath()), StandardCharsets.UTF_8))) {
            return parse(reader);
        }
    }

    /**
     * The engine's {@code pick_catalog}: the user's copy wins when it has a serial and either the
     * base has none or the user's is greater; the base is the fallback; null when neither exists.
     */
    @Nullable
    static File pick(@NonNull File baseCatalog, @NonNull File userCatalog) throws IOException {
        Long userSerial = userCatalog.isFile() ? serialOf(userCatalog) : null;
        Long baseSerial = baseCatalog.isFile() ? serialOf(baseCatalog) : null;
        if (userSerial != null && (baseSerial == null || userSerial > baseSerial)) return userCatalog;
        if (baseCatalog.isFile()) return baseCatalog;
        return null;
    }

    /** The {@code serial=<n>} of the first comment line that carries one; null when there is none. */
    @Nullable
    static Long serialOf(@NonNull File catalog) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Files.newInputStream(catalog.toPath()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#")) continue;
                Long serial = serialOfComment(line);
                if (serial != null) return serial;
            }
        }
        return null;
    }

    @Nullable
    static Long serialOfComment(@NonNull String comment) {
        int at = comment.indexOf("serial=");
        if (at < 0) return null;
        int end = at + "serial=".length();
        int start = end;
        while (end < comment.length() && Character.isDigit(comment.charAt(end))) end++;
        if (end == start) return null;
        try {
            return Long.parseLong(comment.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    static PrivilegedCatalog parse(@NonNull BufferedReader reader) throws IOException {
        List<Row> rows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            Row row = parseRow(line);
            if (row != null) rows.add(row);
        }
        return new PrivilegedCatalog(Collections.unmodifiableList(rows));
    }

    /** The row's privileged-binary reading, or null for a comment, a blank, a short row or an unprivileged one. */
    @Nullable
    static Row parseRow(@NonNull String line) {
        if (line.isEmpty() || line.startsWith("#")) return null;
        String[] fields = line.split("\t", -1);
        if (fields.length <= COLUMN_OPTIONS) return null;
        if (!KIND_BINARY.equals(fields[COLUMN_KIND])) return null;
        if (!PRIV_SHIZUKU.equals(option(fields[COLUMN_OPTIONS], OPTION_PRIV))) return null;
        String name = fields[COLUMN_NAME];
        String digest = fields[COLUMN_DIGEST].toLowerCase(Locale.ROOT);
        if (name.isEmpty() || !isHexDigest(digest)) return null;
        return new Row(name, digest);
    }

    /** The engine's {@code opt}: the value of the first {@code key=} entry among {@code ;}-separated options. */
    @Nullable
    static String option(@NonNull String options, @NonNull String key) {
        for (String entry : options.split(";")) {
            if (entry.startsWith(key + "=")) return entry.substring(key.length() + 1);
        }
        return null;
    }

    static boolean isHexDigest(@NonNull String digest) {
        if (digest.length() != 64) return false;
        for (int i = 0; i < digest.length(); i++) {
            char c = digest.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }
}
