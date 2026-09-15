package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The font file a {@code family=} name refers to, found in the directories people keep fonts in.
 *
 * <p>Android resolves a family name against the families it ships and nothing else, so a name a
 * kitty user installed by dropping a file into {@code ~/.fonts} comes back as the default sans
 * face and draws tofu. kitty reaches the same name through fontconfig, which scans {@code ~/.fonts}
 * and {@code ~/.local/share/fonts}; this scans those, and the app's own {@code ~/.termux/fonts},
 * and matches a name against the file names it finds with their style suffix removed —
 * {@code HerdrAgentIconsMax-Regular.ttf} answers to {@code Herdr Agent Icons Max}. Reading the
 * name table out of every file would be exact and would cost a font parse per file per config
 * load; file names are what installers write and what people already match on.
 *
 * <p>Built once per config load and bounded in every direction: how deep it walks, how many files
 * it will look at and how many families it will remember.
 */
final class FontFamilyIndex {

    /** Slots of the per-family file array, matching {@code Typeface.NORMAL}..{@code BOLD_ITALIC}. */
    static final int REGULAR = 0;
    static final int BOLD = 1;
    static final int ITALIC = 2;
    static final int BOLD_ITALIC = 3;

    static final FontFamilyIndex EMPTY =
        new FontFamilyIndex(Collections.<String, File[]>emptyMap());

    private static final int MAX_DEPTH = 4;
    private static final int MAX_FILES = 512;
    private static final int MAX_FAMILIES = 256;
    private static final List<String> FONT_SUFFIXES =
        Arrays.asList(".ttf", ".otf", ".ttc", ".otc");
    /** Trailing words a font file names its face with, longest first so Bold-Italic wins. */
    private static final String[] STYLE_SUFFIXES = {
        "boldoblique", "bolditalic", "italicbold", "oblique", "italic", "bold", "regular"
    };

    private final Map<String, File[]> mFamilies;

    private FontFamilyIndex(@NonNull Map<String, File[]> families) {
        mFamilies = families;
    }

    /** Scans the given directories in order; the first file to claim a name keeps it. */
    @NonNull
    static FontFamilyIndex of(@NonNull List<File> roots) {
        Map<String, File[]> families = new LinkedHashMap<>();
        int budget = MAX_FILES;
        for (File root : roots) budget = scan(root, 0, budget, families);
        return families.isEmpty() ? EMPTY : new FontFamilyIndex(families);
    }

    /** The file to load for this family at this SGR style, or null when nothing matches. */
    @Nullable
    File find(@NonNull String family, int style) {
        File[] slots = mFamilies.get(normalize(family));
        if (slots == null) return null;
        int wanted = style < 0 || style > BOLD_ITALIC ? REGULAR : style;
        if (slots[wanted] != null) return slots[wanted];
        // Bold italic is the one style two other files can stand in for; everything else falls
        // back to the plain face and lets Android synthesize the difference, as it already does.
        if (wanted == BOLD_ITALIC) {
            if (slots[ITALIC] != null) return slots[ITALIC];
            if (slots[BOLD] != null) return slots[BOLD];
        }
        if (slots[REGULAR] != null) return slots[REGULAR];
        for (File slot : slots) if (slot != null) return slot;
        return null;
    }

    boolean isEmpty() {
        return mFamilies.isEmpty();
    }

    private static int scan(@NonNull File dir, int depth, int budget,
                            @NonNull Map<String, File[]> families) {
        if (budget <= 0 || depth > MAX_DEPTH || !dir.isDirectory()) return budget;
        File[] entries = dir.listFiles();
        if (entries == null) return budget;
        Arrays.sort(entries, (first, second) -> first.getName().compareTo(second.getName()));
        for (File entry : entries) {
            if (budget <= 0) break;
            if (entry.isDirectory()) {
                budget = scan(entry, depth + 1, budget, families);
                continue;
            }
            String name = entry.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || !FONT_SUFFIXES.contains(name.substring(dot).toLowerCase(Locale.US)))
                continue;
            budget--;
            add(families, name.substring(0, dot), entry);
        }
        return budget;
    }

    private static void add(@NonNull Map<String, File[]> families, @NonNull String base,
                            @NonNull File file) {
        String normalized = normalize(base);
        int style = styleOf(normalized);
        put(families, normalized, style, file);
        // MapleMono-NF-Bold answers to "MapleMono NF" as well as to its own full name.
        String stripped = stripStyle(normalized);
        if (!stripped.equals(normalized)) put(families, stripped, style, file);
    }

    private static void put(@NonNull Map<String, File[]> families, @NonNull String key, int style,
                            @NonNull File file) {
        if (key.isEmpty()) return;
        File[] slots = families.get(key);
        if (slots == null) {
            if (families.size() >= MAX_FAMILIES) return;
            slots = new File[4];
            families.put(key, slots);
        }
        if (slots[style] == null) slots[style] = file;
    }

    private static int styleOf(@NonNull String normalized) {
        if (normalized.endsWith("bolditalic") || normalized.endsWith("boldoblique")
            || normalized.endsWith("italicbold")) return BOLD_ITALIC;
        if (normalized.endsWith("italic") || normalized.endsWith("oblique")) return ITALIC;
        if (normalized.endsWith("bold")) return BOLD;
        return REGULAR;
    }

    @NonNull
    private static String stripStyle(@NonNull String normalized) {
        for (String suffix : STYLE_SUFFIXES)
            if (normalized.length() > suffix.length() && normalized.endsWith(suffix))
                return normalized.substring(0, normalized.length() - suffix.length());
        return normalized;
    }

    /** Family names are compared with their spaces, hyphens and case thrown away. */
    @NonNull
    static String normalize(@NonNull String name) {
        StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) result.append(c);
        }
        return result.toString();
    }
}
