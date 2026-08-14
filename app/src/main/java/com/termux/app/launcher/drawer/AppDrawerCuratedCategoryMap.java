package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime parser for the versioned curated package mapping. The schema directive is load-bearing:
 * a file without a supported one is rejected wholesale, while bad rows inside a supported file
 * still degrade independently.
 */
public final class AppDrawerCuratedCategoryMap {
    public static final String SCHEMA_LINE = "# schema=2";
    public static final String MODE_FILL = "fill";
    public static final String MODE_FORCE = "force";

    /** One curated row. Force entries outrank the platform category; fill entries back it up. */
    public static final class Entry {
        @NonNull public final AppDrawerCategory category;
        public final boolean force;

        Entry(@NonNull AppDrawerCategory category, boolean force) {
            this.category = category;
            this.force = force;
        }
    }

    @NonNull private final Map<String, Entry> entries;

    private AppDrawerCuratedCategoryMap(@NonNull Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    @NonNull public static AppDrawerCuratedCategoryMap empty() {
        return new AppDrawerCuratedCategoryMap(Collections.emptyMap());
    }

    @NonNull
    public static AppDrawerCuratedCategoryMap parse(@NonNull InputStream input) throws IOException {
        return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @NonNull
    public static AppDrawerCuratedCategoryMap parse(@NonNull Reader reader) throws IOException {
        Map<String, Entry> parsed = new LinkedHashMap<>();
        BufferedReader lines = reader instanceof BufferedReader
            ? (BufferedReader) reader : new BufferedReader(reader);
        boolean schemaAccepted = false;
        String line;
        while ((line = lines.readLine()) != null) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (value.startsWith("#")) {
                String directive = value.substring(1).trim();
                if (!directive.startsWith("schema=")) continue;
                if (!SCHEMA_LINE.equals("# " + directive))
                    return rejected("unsupported schema directive: " + value);
                schemaAccepted = true;
                continue;
            }
            // Data before the directive means the file predates or ignores versioning; treating
            // its rows as schema-2 rows would silently misread every mode column.
            if (!schemaAccepted) return rejected("data row before schema directive");
            if (value.indexOf('"') >= 0 || value.indexOf('*') >= 0) continue;
            String[] fields = value.split(",", -1);
            if (fields.length != 3) continue;
            String packageName = fields[0].trim();
            String slug = fields[1].trim();
            String mode = fields[2].trim();
            if (!isValidPackage(packageName) || !packageName.equals(packageName.toLowerCase(Locale.US)))
                continue;
            AppDrawerCategory category = AppDrawerCategory.fromSlug(slug);
            if (category == null || category.synthetic) continue;
            boolean force = MODE_FORCE.equals(mode);
            if (!force && !MODE_FILL.equals(mode)) continue;
            parsed.put(packageName, new Entry(category, force));
        }
        if (!schemaAccepted) return rejected("missing schema directive");
        return new AppDrawerCuratedCategoryMap(parsed);
    }

    @NonNull
    private static AppDrawerCuratedCategoryMap rejected(@NonNull String reason) {
        try {
            android.util.Log.w("AppDrawerCategories", "Curated category map rejected: " + reason);
        } catch (RuntimeException ignored) {
            // android.util.Log is unavailable under plain-JVM unit tests.
        }
        return empty();
    }

    @Nullable public AppDrawerCategory forcedCategoryForPackage(@Nullable String packageName) {
        Entry entry = entryForPackage(packageName);
        return entry != null && entry.force ? entry.category : null;
    }

    @Nullable public AppDrawerCategory fillCategoryForPackage(@Nullable String packageName) {
        Entry entry = entryForPackage(packageName);
        return entry != null && !entry.force ? entry.category : null;
    }

    @Nullable public Entry entryForPackage(@Nullable String packageName) {
        if (packageName == null) return null;
        return entries.get(packageName.toLowerCase(Locale.US));
    }

    @NonNull public Map<String, Entry> asMap() { return entries; }

    public static boolean isValidPackage(@Nullable String value) {
        if (value == null || value.isEmpty() || value.startsWith(".") || value.endsWith("."))
            return false;
        String[] parts = value.split("\\.", -1);
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty() || !Character.isLetter(part.charAt(0))) return false;
            for (int i = 1; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
            }
        }
        return true;
    }
}
