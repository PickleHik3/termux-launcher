package com.termux.app.terminal;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.LauncherToolRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits {@link TerminalBindingConfig#FILE_NAME} in place, preserving the user's comments, blank
 * lines and ordering. The palette's key capture writes through this, so a user who hand-maintains
 * the file does not lose their annotations the first time they bind a key from the UI.
 *
 * <p>The line-editing core is pure and static — that is the part CI can verify. The IO layer around
 * it is a thin temp-file-and-rename, deliberately unexciting.
 */
public final class TerminalBindingConfigWriter {

    private static final String LOG_TAG = "TerminalBindingConfigWriter";

    /**
     * Header written above the first mapping this class appends. Cosmetic only: matching never
     * depends on it, because users reorder their files and a section marker that mattered would
     * turn a harmless edit into a lost binding.
     */
    static final String MANAGED_HEADER =
        "# Bindings below were added from the command palette. Editing them by hand is fine.";

    private TerminalBindingConfigWriter() {}

    /** Result of an in-memory edit: the new lines, or an error and the lines untouched. */
    public static final class Edit {
        @NonNull public final List<String> lines;
        /** True when an existing mapping for the same sequence was rewritten in place. */
        public final boolean replaced;
        /** Index of the written line in {@link #lines}, or -1 when nothing was written. */
        public final int index;
        @Nullable public final String error;

        private Edit(@NonNull List<String> lines, boolean replaced, int index,
                     @Nullable String error) {
            this.lines = lines;
            this.replaced = replaced;
            this.index = index;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }

        static Edit failed(@NonNull List<String> lines, @NonNull String error) {
            return new Edit(new ArrayList<>(lines), false, -1, error);
        }
    }

    // ------------------------------------------------------------------ pure core

    /**
     * Binds {@code sequence} to {@code tool} with {@code arguments}, replacing an existing root
     * mapping for the same sequence where that is safe and appending at EOF otherwise.
     *
     * <p>In-place replacement is only safe when no {@code unmap} of the same sequence follows the
     * mapping: the parser processes directives in order, so an edit written above a later unmap
     * would parse cleanly and then never fire.
     */
    @NonNull
    public static Edit putMapping(@NonNull List<String> lines, @NonNull String sequence,
                                  @NonNull String tool, @NonNull List<String> arguments) {
        String normalized = TerminalKeyBindingResolver.normalizeSequenceSpec(sequence);
        if (normalized.isEmpty()) return Edit.failed(lines, "empty key sequence");
        String written = formatMapLine(normalized, tool, arguments);
        if (written.length() > TerminalBindingConfig.MAX_LINE_CHARS)
            return Edit.failed(lines, "mapping line is too long");

        int existing = -1;
        for (int i = 0; i < lines.size(); i++) {
            String candidate = mapLineSequence(lines.get(i));
            if (candidate != null && candidate.equals(normalized)) existing = i;
            else if (isUnmapOf(lines.get(i), normalized)) existing = -1;
        }

        List<String> out = new ArrayList<>(lines);
        if (existing >= 0) {
            out.set(existing, written);
            return new Edit(out, true, existing, null);
        }
        // Worst case the append adds three lines: a separating blank, the header, the mapping.
        if (out.size() + 3 > TerminalBindingConfig.MAX_LINES)
            return Edit.failed(lines, "binding file already has "
                + TerminalBindingConfig.MAX_LINES + " lines");
        if (!out.isEmpty() && !out.get(out.size() - 1).trim().isEmpty()) out.add("");
        if (!containsManagedHeader(out)) out.add(MANAGED_HEADER);
        out.add(written);
        if (totalBytes(out) > TerminalBindingConfig.MAX_FILE_BYTES)
            return Edit.failed(lines, "binding file would exceed "
                + TerminalBindingConfig.MAX_FILE_BYTES + " bytes");
        return new Edit(out, false, out.size() - 1, null);
    }

    /** Drops every root mapping of {@code sequence}. Comments and blank lines are untouched. */
    @NonNull
    public static Edit removeMapping(@NonNull List<String> lines, @NonNull String sequence) {
        String normalized = TerminalKeyBindingResolver.normalizeSequenceSpec(sequence);
        if (normalized.isEmpty()) return Edit.failed(lines, "empty key sequence");
        List<String> out = new ArrayList<>();
        boolean removed = false;
        for (String line : lines) {
            String candidate = mapLineSequence(line);
            if (candidate != null && candidate.equals(normalized)) {
                removed = true;
                continue;
            }
            out.add(line);
        }
        return new Edit(out, removed, -1, null);
    }

    /** A {@code map} directive with every word quoted as the tokenizer needs. */
    @NonNull
    public static String formatMapLine(@NonNull String sequence, @NonNull String tool,
                                       @NonNull List<String> arguments) {
        StringBuilder line = new StringBuilder("map ");
        line.append(quoteWord(sequence)).append(' ').append(quoteWord(tool));
        for (String argument : arguments) line.append(' ').append(quoteWord(argument));
        return line.toString();
    }

    /**
     * Quotes a word the tokenizer would otherwise mangle: {@code #} starts a comment, whitespace
     * splits words, a quote or backslash is an escape, and a leading {@code --} reads as an option.
     * This is what lets a {@code #userSerial=} stable id survive a round trip.
     */
    @NonNull
    public static String quoteWord(@NonNull String word) {
        if (word.isEmpty()) return "''";
        boolean needsQuote = word.startsWith("--");
        for (int i = 0; i < word.length() && !needsQuote; i++) {
            char c = word.charAt(i);
            needsQuote = c == '#' || c == '\'' || c == '"' || c == '\\'
                || Character.isWhitespace(c);
        }
        if (!needsQuote) return word;
        StringBuilder quoted = new StringBuilder(word.length() + 2).append('"');
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '"' || c == '\\') quoted.append('\\');
            quoted.append(c);
        }
        return quoted.append('"').toString();
    }

    /**
     * The normalized key sequence a line binds, or {@code null} when the line is not a root
     * {@code map} directive this class may touch.
     *
     * <p>Returns {@code null} for blank and commented lines — the tokenizer strips {@code #}, so a
     * commented-out directive is structurally invisible here rather than something a regex has to
     * remember to avoid — and for anything carrying {@code --mode} or {@code --new-mode}, because a
     * modal mapping lives in another keymap and rewriting it would silently move a binding between
     * modes.
     */
    @Nullable
    public static String mapLineSequence(@NonNull String line) {
        List<String> words;
        try {
            words = TerminalBindingConfig.words(line);
        } catch (IllegalArgumentException broken) {
            // Unterminated quote or trailing escape: leave the line opaque rather than guess.
            return null;
        }
        if (words.isEmpty()) return null;
        if (!"map".equals(words.get(0).toLowerCase(Locale.US))) return null;
        int cursor = 1;
        while (cursor < words.size() && words.get(cursor).startsWith("--")) {
            String option = words.get(cursor++);
            int equals = option.indexOf('=');
            String name = equals >= 0 ? option.substring(0, equals) : option;
            if ("--mode".equals(name) || "--new-mode".equals(name)) return null;
            if (equals < 0) cursor++;   // the option's value word
        }
        if (cursor >= words.size()) return null;
        String sequence = TerminalKeyBindingResolver.normalizeSequenceSpec(words.get(cursor));
        return sequence.isEmpty() ? null : sequence;
    }

    /** Whether {@code line} is an {@code unmap} of {@code sequence} in the root keymap. */
    public static boolean isUnmapOf(@NonNull String line, @NonNull String sequence) {
        List<String> words;
        try {
            words = TerminalBindingConfig.words(line);
        } catch (IllegalArgumentException broken) {
            return false;
        }
        if (words.isEmpty() || !"unmap".equals(words.get(0).toLowerCase(Locale.US))) return false;
        int cursor = 1;
        while (cursor < words.size() && words.get(cursor).startsWith("--")) {
            String option = words.get(cursor++);
            int equals = option.indexOf('=');
            String name = equals >= 0 ? option.substring(0, equals) : option;
            if ("--mode".equals(name)) return false;
            if (equals < 0) cursor++;
        }
        if (cursor >= words.size()) return false;
        return sequence.equals(
            TerminalKeyBindingResolver.normalizeSequenceSpec(words.get(cursor)));
    }

    private static boolean containsManagedHeader(@NonNull List<String> lines) {
        for (String line : lines) if (MANAGED_HEADER.equals(line.trim())) return true;
        return false;
    }

    private static long totalBytes(@NonNull List<String> lines) {
        long bytes = 0;
        for (String line : lines) bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
        return bytes;
    }

    // ------------------------------------------------------------------ IO

    /**
     * Binds {@code sequence} to {@code app.launch query}, then reloads the resolver so the stroke
     * is live before the palette redraws.
     *
     * <p>Runs synchronously on the calling (main) thread: the file is under 256 KiB, both existing
     * precedents for writing into {@code ~/.termux} do the same, and the palette needs the reload
     * finished before it rebuilds its rows. Possible StrictMode complaint, deliberately accepted.
     *
     * @return null on success, else a human-readable reason.
     */
    @Nullable
    public static String bindAppLaunch(@NonNull String sequence, @NonNull String query) {
        return putMapping(new File(TerminalBindingConfig.FILE_PATH), sequence,
            LauncherToolRegistry.TOOL_APP_LAUNCH, java.util.Collections.singletonList(query));
    }

    /** As {@link #bindAppLaunch}, against an explicit file. Returns null on success. */
    @Nullable
    static String putMapping(@NonNull File file, @NonNull String sequence, @NonNull String tool,
                             @NonNull List<String> arguments) {
        List<String> lines;
        try {
            lines = readLines(file);
        } catch (IOException e) {
            return "cannot read " + file.getName() + ": " + e.getMessage();
        }
        Edit edit = putMapping(lines, sequence, tool, arguments);
        if (!edit.ok()) return edit.error;
        try {
            writeLines(file, edit.lines);
        } catch (IOException e) {
            return "cannot write " + file.getName() + ": " + e.getMessage();
        }
        TerminalKeyBindingResolver reloaded = TerminalKeyBindingResolver.reloadUserBindings();
        for (String error : reloaded.getConfigErrors()) Log.w(LOG_TAG, "after write: " + error);
        return null;
    }

    /** Reads with load()'s own guards, so an unreadably large file is refused before editing. */
    @NonNull
    private static List<String> readLines(@NonNull File file) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        if (!file.isFile()) throw new IOException("not a regular file");
        if (file.length() > TerminalBindingConfig.MAX_FILE_BYTES)
            throw new IOException("file exceeds " + TerminalBindingConfig.MAX_FILE_BYTES + " bytes");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() >= TerminalBindingConfig.MAX_LINES)
                    throw new IOException("file exceeds " + TerminalBindingConfig.MAX_LINES
                        + " lines");
                lines.add(line);
            }
        }
        return lines;
    }

    private static void writeLines(@NonNull File file, @NonNull List<String> lines)
            throws IOException {
        File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs())
            throw new IOException("cannot create " + directory.getPath());

        StringBuilder payload = new StringBuilder();
        for (String line : lines) payload.append(line).append('\n');
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        File temporary = File.createTempFile("." + file.getName() + ".", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            }
            ownerOnly(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Same-directory replacement still avoids partial contents where atomic moves are
                // unavailable, as on some host-test temporary filesystems.
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            ownerOnly(file);
        } finally {
            if (temporary != null && temporary.exists() && !temporary.delete())
                Log.w(LOG_TAG, "could not remove " + temporary.getPath());
        }
    }

    private static void ownerOnly(@NonNull File file) {
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }
}
