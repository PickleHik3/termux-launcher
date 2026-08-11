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

/** Runtime parser for the versioned curated package mapping. Bad rows degrade independently. */
public final class AppDrawerCuratedCategoryMap {
    public static final String SCHEMA_LINE = "# schema=1";
    @NonNull private final Map<String, AppDrawerCategory> categories;

    private AppDrawerCuratedCategoryMap(@NonNull Map<String, AppDrawerCategory> categories) {
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
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
        Map<String, AppDrawerCategory> parsed = new LinkedHashMap<>();
        BufferedReader lines = reader instanceof BufferedReader
            ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = lines.readLine()) != null) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            if (value.indexOf('"') >= 0 || value.indexOf('*') >= 0) continue;
            int comma = value.indexOf(',');
            if (comma <= 0 || comma != value.lastIndexOf(',')) continue;
            String packageName = value.substring(0, comma).trim();
            String slug = value.substring(comma + 1).trim();
            if (!isValidPackage(packageName) || !packageName.equals(packageName.toLowerCase(Locale.US)))
                continue;
            AppDrawerCategory category = AppDrawerCategory.fromSlug(slug);
            if (category == null || category.synthetic) continue;
            parsed.put(packageName, category);
        }
        return new AppDrawerCuratedCategoryMap(parsed);
    }

    @Nullable public AppDrawerCategory categoryForPackage(@Nullable String packageName) {
        if (packageName == null) return null;
        return categories.get(packageName.toLowerCase(Locale.US));
    }

    @NonNull public Map<String, AppDrawerCategory> asMap() { return categories; }

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
