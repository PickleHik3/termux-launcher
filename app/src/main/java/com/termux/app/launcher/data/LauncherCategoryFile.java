package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed view of the {@code app-categories.conf} drop-in: an INI-ish, hand-editable mirror of the
 * drawer's package → category assignments. Humans edit this in vim over ssh, so parsing never
 * throws on a bad line — every complaint lands in {@link #warnings()} and the rest of the file
 * still loads. Section names are kept verbatim rather than validated against the current drawer
 * taxonomy, because free-form category titles are coming and an old binary must not silently eat
 * a section it does not recognise yet.
 */
public final class LauncherCategoryFile {

    private static final String HEADER_COMMENT =
        "# Managed by Termux Launcher. Edit freely: sections are categories, lines are packages.";

    private final LinkedHashMap<String, List<String>> sections;
    private final Map<String, String> sectionByPackage;
    private final List<String> warnings;

    private LauncherCategoryFile(@NonNull LinkedHashMap<String, List<String>> sections,
                                 @NonNull Map<String, String> sectionByPackage,
                                 @NonNull List<String> warnings) {
        this.sections = sections;
        this.sectionByPackage = sectionByPackage;
        this.warnings = warnings;
    }

    @NonNull
    public static LauncherCategoryFile empty() {
        return new LauncherCategoryFile(
            new LinkedHashMap<>(), new HashMap<>(), new ArrayList<>());
    }

    /**
     * Builds an in-memory file for writing. The iteration order of {@code sections} becomes the
     * on-disk order, which is also the drawer display order, so callers own the ordering decision.
     */
    @NonNull
    public static LauncherCategoryFile of(@NonNull LinkedHashMap<String, List<String>> sections) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        Map<String, String> index = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            String section = entry.getKey() == null ? "" : entry.getKey().trim();
            if (section.isEmpty()) continue;
            List<String> packages = copy.get(section);
            if (packages == null) {
                packages = new ArrayList<>();
                copy.put(section, packages);
            }
            if (entry.getValue() == null) continue;
            for (String packageName : entry.getValue()) {
                if (packageName == null) continue;
                String trimmed = packageName.trim();
                if (trimmed.isEmpty()) continue;
                String key = trimmed.toLowerCase(Locale.US);
                if (index.containsKey(key)) continue;
                index.put(key, section);
                packages.add(trimmed);
            }
        }
        return new LauncherCategoryFile(copy, index, new ArrayList<>());
    }

    @NonNull
    public static LauncherCategoryFile parse(@NonNull File file) throws IOException {
        if (!file.exists()) return empty();
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    @NonNull
    public static LauncherCategoryFile parse(@NonNull Reader reader) throws IOException {
        LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();
        Map<String, String> index = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        BufferedReader buffered = reader instanceof BufferedReader
            ? (BufferedReader) reader : new BufferedReader(reader);
        String currentSection = null;
        int lineNumber = 0;
        String rawLine;
        while ((rawLine = buffered.readLine()) != null) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            // Comments are only recognised when they own the whole line: package names may
            // legally contain '#' or ';', and truncating one would silently unassign an app.
            if (line.charAt(0) == '#' || line.charAt(0) == ';') continue;

            if (line.charAt(0) == '[') {
                int close = line.indexOf(']');
                if (close < 0) {
                    warnings.add("line " + lineNumber + ": unterminated [section] header");
                    continue;
                }
                String name = line.substring(1, close).trim();
                if (name.isEmpty()) {
                    warnings.add("line " + lineNumber + ": empty [section] name");
                    continue;
                }
                currentSection = name;
                // Duplicate headers merge so a human can re-open a section further down the file.
                if (!sections.containsKey(name)) sections.put(name, new ArrayList<>());
                continue;
            }

            if (currentSection == null) {
                warnings.add("line " + lineNumber + ": package before any [section]");
                continue;
            }

            String key = line.toLowerCase(Locale.US);
            String existing = index.get(key);
            if (existing != null) {
                warnings.add("line " + lineNumber + ": duplicate package " + line
                    + ", already in [" + existing + "]");
                continue;
            }
            index.put(key, currentSection);
            sections.get(currentSection).add(line);
        }

        return new LauncherCategoryFile(sections, index, warnings);
    }

    /** @return the section this package is assigned to, or null when the file does not mention it. */
    @Nullable
    public String categoryForPackage(@Nullable String packageName) {
        if (packageName == null) return null;
        return sectionByPackage.get(packageName.trim().toLowerCase(Locale.US));
    }

    /** @return section names in file order; this order is the drawer display order. */
    @NonNull
    public List<String> sectionOrder() {
        return Collections.unmodifiableList(new ArrayList<>(sections.keySet()));
    }

    @NonNull
    public Map<String, List<String>> sections() {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet())
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        return Collections.unmodifiableMap(copy);
    }

    /** @return human-readable complaints about the parsed file, e.g. "line 12: package before any [section]". */
    @NonNull
    public List<String> warnings() {
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * Writes via a sibling {@code .tmp} plus rename so a kill mid-write leaves the previous file
     * intact instead of a truncated one — this file is the user's only copy of their hand edits.
     */
    public void write(@NonNull File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("cannot create directory " + parent.getAbsolutePath());

        File temporary = new File(file.getAbsolutePath() + ".tmp");
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8))) {
            writer.write(HEADER_COMMENT);
            writer.write("\n");
            for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
                writer.write("\n");
                writer.write("[" + entry.getKey() + "]");
                writer.write("\n");
                for (String packageName : entry.getValue()) {
                    writer.write(packageName);
                    writer.write("\n");
                }
            }
        }

        if (temporary.renameTo(file)) return;
        // Some filesystems refuse rename onto an existing target; drop it and retry once.
        file.delete();
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("cannot rename onto " + file.getAbsolutePath());
        }
    }
}
