package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime parser and search index for the reviewed glyph list behind the extra-keys glyph picker.
 *
 * <p>The catalogue is a hand-reviewed file rather than a set of Unicode ranges on purpose: a range
 * offers thousands of code points no shipped UI font can draw, and a cap that renders as tofu is
 * worse than no cap at all. {@link #filter(GlyphSupport)} is the other half of that contract — the
 * picker measures every candidate against the paint the cap label draws with, per device.
 *
 * <p>Rows degrade one at a time, but the schema directive is load-bearing: a file without a
 * supported one is refused wholesale rather than misread column by column.
 */
public final class ExtraKeyGlyphCatalogue {

    public static final String SCHEMA_LINE = "# schema=1";

    public static final String CATEGORY_ARROWS = "arrows";
    public static final String CATEGORY_BLOCKS = "blocks";
    public static final String CATEGORY_SHAPES = "shapes";
    public static final String CATEGORY_POWERLINE = "powerline";
    public static final String CATEGORY_TECHNICAL = "technical";
    public static final String CATEGORY_TERMINAL_MARKS = "terminal_marks";
    /**
     * The bundled Nerd Font symbols. Unlike every other category this one is generated from the
     * shipped face rather than reviewed by hand — the app draws these with that face, so "can the
     * device render it" is answered by the font it ships with, not by the system UI font.
     */
    public static final String CATEGORY_NERD_FONT = "nerd_font";

    /** Declared category order; the file is grouped by it and the picker renders it in this order. */
    public static final List<String> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
        CATEGORY_ARROWS, CATEGORY_BLOCKS, CATEGORY_SHAPES, CATEGORY_POWERLINE, CATEGORY_TECHNICAL,
        CATEGORY_TERMINAL_MARKS, CATEGORY_NERD_FONT));

    /** One catalogue row: the character itself plus everything the search field can hit. */
    public static final class Glyph {
        public final int codePoint;
        @NonNull public final String text;
        @NonNull public final String name;
        @NonNull public final List<String> keywords;
        @NonNull public final String category;

        Glyph(int codePoint, @NonNull String name, @NonNull List<String> keywords,
              @NonNull String category) {
            this.codePoint = codePoint;
            this.text = new String(Character.toChars(codePoint));
            this.name = name;
            this.keywords = Collections.unmodifiableList(keywords);
            this.category = category;
        }

        /** The hex the file stores, so search can also match a pasted {@code U+2328}. */
        @NonNull public String hex() {
            return String.format(Locale.ROOT, "%04X", codePoint);
        }
    }

    /** Whether the device can actually draw a glyph; {@code Paint.hasGlyph} in production. */
    public interface GlyphSupport {
        boolean canDraw(@NonNull Glyph glyph);
    }

    @NonNull private final List<Glyph> glyphs;
    @NonNull private final Map<Integer, Glyph> byCodePoint;

    private ExtraKeyGlyphCatalogue(@NonNull List<Glyph> glyphs) {
        this.glyphs = Collections.unmodifiableList(new ArrayList<>(glyphs));
        Map<Integer, Glyph> index = new LinkedHashMap<>();
        for (Glyph glyph : this.glyphs) index.put(glyph.codePoint, glyph);
        this.byCodePoint = Collections.unmodifiableMap(index);
    }

    @NonNull public static ExtraKeyGlyphCatalogue empty() {
        return new ExtraKeyGlyphCatalogue(Collections.emptyList());
    }

    @NonNull
    public static ExtraKeyGlyphCatalogue parse(@NonNull InputStream input) throws IOException {
        return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * The rows of both catalogues in one index, first file first. Ties in {@link #search} break by
     * category order, so the reviewed rows keep coming out ahead of the generated Nerd Font ones.
     */
    @NonNull
    public static ExtraKeyGlyphCatalogue concat(@NonNull ExtraKeyGlyphCatalogue first,
                                                @NonNull ExtraKeyGlyphCatalogue second) {
        if (second.isEmpty()) return first;
        if (first.isEmpty()) return second;
        List<Glyph> merged = new ArrayList<>(first.glyphs);
        for (Glyph glyph : second.glyphs) {
            if (first.byCodePoint(glyph.codePoint) == null) merged.add(glyph);
        }
        return new ExtraKeyGlyphCatalogue(merged);
    }

    @NonNull
    public static ExtraKeyGlyphCatalogue parse(@NonNull Reader reader) throws IOException {
        List<Glyph> parsed = new ArrayList<>();
        // A set, not a list: the generated Nerd Font file is five figures of rows, and a linear
        // duplicate scan per row turns parsing it into a visible pause when the picker opens.
        java.util.Set<Integer> seen = new java.util.HashSet<>();
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
            if (!schemaAccepted) return rejected("data row before schema directive");
            String[] fields = value.split(",", -1);
            if (fields.length != 4) continue;
            int codePoint = parseCodePoint(fields[0].trim());
            if (codePoint < 0 || seen.contains(codePoint)) continue;
            String name = fields[1].trim().toLowerCase(Locale.ROOT);
            String category = fields[3].trim();
            if (name.isEmpty() || !CATEGORIES.contains(category)) continue;
            seen.add(codePoint);
            parsed.add(new Glyph(codePoint, name, splitKeywords(fields[2]), category));
        }
        if (!schemaAccepted) return rejected("missing schema directive");
        return new ExtraKeyGlyphCatalogue(parsed);
    }

    /**
     * A code point the catalogue may offer: hex only, inside Unicode, and never a lone surrogate or
     * a control character — a cap label made of either is an invisible key.
     */
    public static int parseCodePoint(@Nullable String value) {
        if (value == null || value.length() < 4 || value.length() > 6) return -1;
        if (!value.equals(value.toUpperCase(Locale.ROOT))) return -1;
        int codePoint;
        try {
            codePoint = Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (!Character.isValidCodePoint(codePoint)) return -1;
        if (codePoint <= 0x20 || Character.isSurrogate((char) codePoint)) return -1;
        return codePoint;
    }

    @NonNull
    private static List<String> splitKeywords(@NonNull String field) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : field.trim().toLowerCase(Locale.ROOT).split("[;\\s]+")) {
            if (!keyword.isEmpty() && !keywords.contains(keyword)) keywords.add(keyword);
        }
        return keywords;
    }

    @NonNull
    private static ExtraKeyGlyphCatalogue rejected(@NonNull String reason) {
        try {
            android.util.Log.w("ExtraKeyGlyphs", "Glyph catalogue rejected: " + reason);
        } catch (RuntimeException ignored) {
            // android.util.Log is unavailable under plain-JVM unit tests.
        }
        return empty();
    }

    @NonNull public List<Glyph> all() {
        return glyphs;
    }

    public int size() {
        return glyphs.size();
    }

    public boolean isEmpty() {
        return glyphs.isEmpty();
    }

    @NonNull public List<Glyph> byCategory(@NonNull String category) {
        List<Glyph> matches = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (glyph.category.equals(category)) matches.add(glyph);
        }
        return matches;
    }

    @Nullable public Glyph byCodePoint(int codePoint) {
        return byCodePoint.get(codePoint);
    }

    /** The catalogue minus everything this device cannot draw; see {@link GlyphSupport}. */
    @NonNull public ExtraKeyGlyphCatalogue filter(@NonNull GlyphSupport support) {
        List<Glyph> drawable = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            if (support.canDraw(glyph)) drawable.add(glyph);
        }
        return new ExtraKeyGlyphCatalogue(drawable);
    }

    /**
     * Name and keyword search, best match first. Pasting the character itself or its hex also
     * finds it, because the reason to reach for the picker is often a glyph seen somewhere else.
     */
    @NonNull public List<Glyph> search(@Nullable String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return all();
        List<Glyph> hits = new ArrayList<>();
        final Map<Integer, Integer> ranks = new LinkedHashMap<>();
        for (Glyph glyph : glyphs) {
            int rank = rank(glyph, needle);
            if (rank < 0) continue;
            ranks.put(glyph.codePoint, rank);
            hits.add(glyph);
        }
        Collections.sort(hits, (a, b) -> {
            int byRank = ranks.get(a.codePoint) - ranks.get(b.codePoint);
            if (byRank != 0) return byRank;
            int byCategory = CATEGORIES.indexOf(a.category) - CATEGORIES.indexOf(b.category);
            if (byCategory != 0) return byCategory;
            return a.codePoint - b.codePoint;
        });
        return hits;
    }

    /** Lower is better; -1 means no hit at all. */
    private static int rank(@NonNull Glyph glyph, @NonNull String needle) {
        if (glyph.text.equals(needle)) return 0;
        String hex = glyph.hex().toLowerCase(Locale.ROOT);
        if (hex.equals(needle) || ("u+" + hex).equals(needle)) return 0;
        if (glyph.name.equals(needle)) return 1;
        if (glyph.keywords.contains(needle)) return 2;
        if (glyph.name.startsWith(needle)) return 3;
        for (String keyword : glyph.keywords) {
            if (keyword.startsWith(needle)) return 4;
        }
        if (glyph.name.contains(needle)) return 5;
        for (String keyword : glyph.keywords) {
            if (keyword.contains(needle)) return 6;
        }
        return -1;
    }
}
