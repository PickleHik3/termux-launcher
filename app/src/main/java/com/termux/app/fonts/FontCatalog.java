package com.termux.app.fonts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded parser and validator for {@code assets/fonts/catalog.json}, the curated terminal
 * font catalog.
 *
 * <p>The catalog is bundled rather than fetched so the picker works offline and in the
 * {@code com.termux} edition, which has no apt repository to lean on. {@link #refreshUrl} is
 * parsed but deliberately unused: it reserves schema room for a future signed remote refresh
 * (see {@code com.termux.ai.TaiRemoteCatalog} for the pattern) without shipping a fetch path
 * nobody has audited yet.
 *
 * <p>Everything here is untrusted input as far as the rest of the app is concerned — a
 * catalog line is what decides which URL gets contacted and which bytes get accepted — so
 * parsing is bounded on every axis and a family that fails any check is dropped with a
 * recorded error instead of poisoning the whole list.
 */
public final class FontCatalog {

    /** Relative path of the bundled catalog inside {@code assets/}. */
    public static final String ASSET_PATH = "fonts/catalog.json";

    // Bounds. Generous enough for a hand-curated catalog, small enough that a corrupt or
    // hostile file cannot make the settings screen allocate without limit.
    static final int MAX_JSON_BYTES = 256 * 1024;
    static final int MAX_FAMILIES = 64;
    static final int MAX_FACES_PER_FAMILY = 4;
    static final int MAX_ARCHIVES_PER_FAMILY = 4;
    static final int MAX_URL_CHARS = 512;
    static final int MAX_ID_CHARS = 64;
    static final int MAX_NAME_CHARS = 96;
    static final int MAX_TEXT_CHARS = 1024;
    static final int MAX_MEMBER_PATH_CHARS = 256;
    /** Matches the loader's own per-font ceiling, so the catalog can never promise a file it rejects. */
    static final long MAX_FACE_BYTES = 64L * 1024L * 1024L;
    /** Ceiling on the bytes one family may transfer, archives included. */
    static final long MAX_FAMILY_DOWNLOAD_BYTES = 96L * 1024L * 1024L;
    static final int MIN_WEIGHT = 1;
    static final int MAX_WEIGHT = 1000;

    /** The four faces the terminal font config can address. */
    public enum FaceSlot {
        REGULAR("regular", "regular.ttf", "font_family"),
        BOLD("bold", "bold.ttf", "bold_font"),
        ITALIC("italic", "italic.ttf", "italic_font"),
        BOLD_ITALIC("bold_italic", "bold-italic.ttf", "bold_italic_font");

        /** Key in the catalog JSON, and the {@code font_features} / {@code font_variations} target. */
        public final String key;
        /** File name this face is installed under inside {@code ~/.termux/fonts/<id>/}. */
        public final String fileName;
        /** {@code fonts.conf} directive that selects this face. */
        public final String directive;

        FaceSlot(String key, String fileName, String directive) {
            this.key = key;
            this.fileName = fileName;
            this.directive = directive;
        }

        @Nullable
        public static FaceSlot fromKey(@Nullable String key) {
            if (key == null) return null;
            for (FaceSlot slot : values()) {
                if (slot.key.equals(key)) return slot;
            }
            return null;
        }
    }

    /** One downloadable face: either a direct file URL, or a member inside an archive. */
    public static final class Face {
        /** Direct file URL, or {@code ""} when this face lives in an archive. */
        @NonNull public final String url;
        /** Archive URL, or {@code ""} for a direct download. */
        @NonNull public final String zipUrl;
        /** Path of the face inside {@link #zipUrl}, or {@code ""} for a direct download. */
        @NonNull public final String memberPath;
        /** Lowercase hex SHA-256 of the font file itself, never of the archive. */
        @NonNull public final String sha256;
        public final long sizeBytes;

        Face(@NonNull String url, @NonNull String zipUrl, @NonNull String memberPath,
             @NonNull String sha256, long sizeBytes) {
            this.url = url;
            this.zipUrl = zipUrl;
            this.memberPath = memberPath;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }

        public boolean isArchiveMember() {
            return !zipUrl.isEmpty();
        }
    }

    /** An archive that one or more faces are extracted from. */
    public static final class Archive {
        @NonNull public final String url;
        @NonNull public final String sha256;
        public final long sizeBytes;

        Archive(@NonNull String url, @NonNull String sha256, long sizeBytes) {
            this.url = url;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }
    }

    /** Weight axis of a variable family, driving the weight slider and {@code font_variations wght=}. */
    public static final class WeightAxis {
        public final int min;
        public final int max;
        public final int regularWeight;
        public final int boldWeight;

        WeightAxis(int min, int max, int regularWeight, int boldWeight) {
            this.min = min;
            this.max = max;
            this.regularWeight = regularWeight;
            this.boldWeight = boldWeight;
        }

        /** Clamps a user-picked weight into the axis range. */
        public int clamp(int weight) {
            return Math.max(min, Math.min(max, weight));
        }
    }

    /** One installable font family. */
    public static final class Family {
        @NonNull public final String id;
        @NonNull public final String displayName;
        @NonNull public final String summary;
        /** Marks the family the catalog suggests; drawn as a star on its list row. */
        public final boolean recommended;
        public final boolean variable;
        @NonNull public final String releaseTag;
        @NonNull public final String homepageUrl;
        @NonNull public final String license;
        @NonNull public final String licenseUrl;
        @NonNull public final String licenseNotice;
        /** Recommended {@code disable_ligatures} policy: {@code never}, {@code cursor} or {@code always}. */
        @NonNull public final String defaultLigatures;
        /** Recommended {@code font_features} token list, or {@code ""} when the family wants none. */
        @NonNull public final String defaultFontFeatures;
        @Nullable public final WeightAxis weightAxis;
        @NonNull public final List<Archive> archives;
        @NonNull public final Map<FaceSlot, Face> faces;
        /** Bytes actually transferred to install this family: every archive plus every direct face. */
        public final long downloadBytes;

        Family(@NonNull String id, @NonNull String displayName, @NonNull String summary,
               boolean recommended, boolean variable, @NonNull String releaseTag,
               @NonNull String homepageUrl, @NonNull String license, @NonNull String licenseUrl,
               @NonNull String licenseNotice, @NonNull String defaultLigatures,
               @NonNull String defaultFontFeatures, @Nullable WeightAxis weightAxis,
               @NonNull List<Archive> archives, @NonNull Map<FaceSlot, Face> faces,
               long downloadBytes) {
            this.id = id;
            this.displayName = displayName;
            this.summary = summary;
            this.recommended = recommended;
            this.variable = variable;
            this.releaseTag = releaseTag;
            this.homepageUrl = homepageUrl;
            this.license = license;
            this.licenseUrl = licenseUrl;
            this.licenseNotice = licenseNotice;
            this.defaultLigatures = defaultLigatures;
            this.defaultFontFeatures = defaultFontFeatures;
            this.weightAxis = weightAxis;
            this.archives = Collections.unmodifiableList(new ArrayList<>(archives));
            EnumMap<FaceSlot, Face> copy = new EnumMap<>(FaceSlot.class);
            copy.putAll(faces);
            this.faces = Collections.unmodifiableMap(copy);
            this.downloadBytes = downloadBytes;
        }

        @Nullable
        public Face face(@NonNull FaceSlot slot) {
            return faces.get(slot);
        }

        public boolean hasFace(@NonNull FaceSlot slot) {
            return faces.containsKey(slot);
        }
    }

    /** The bundled Nerd Font symbols face that gives every family icon coverage offline. */
    public static final class SymbolFont {
        @NonNull public final String assetPath;
        @NonNull public final String installName;
        @NonNull public final String displayName;
        @NonNull public final String sourceTag;
        @NonNull public final String sha256;
        public final long sizeBytes;
        @NonNull public final String license;
        @NonNull public final String licenseUrl;

        SymbolFont(@NonNull String assetPath, @NonNull String installName, @NonNull String displayName,
                   @NonNull String sourceTag, @NonNull String sha256, long sizeBytes,
                   @NonNull String license, @NonNull String licenseUrl) {
            this.assetPath = assetPath;
            this.installName = installName;
            this.displayName = displayName;
            this.sourceTag = sourceTag;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.license = license;
            this.licenseUrl = licenseUrl;
        }
    }

    /** Parse outcome. Never throws: a broken catalog yields an empty family list plus errors. */
    public static final class Result {
        public final int schemaVersion;
        /** Reserved for a future signed remote refresh; never fetched by this class. */
        @NonNull public final String refreshUrl;
        @Nullable public final SymbolFont symbolFont;
        @NonNull public final List<Family> families;
        @NonNull public final List<String> errors;

        Result(int schemaVersion, @NonNull String refreshUrl, @Nullable SymbolFont symbolFont,
               @NonNull List<Family> families, @NonNull List<String> errors) {
            this.schemaVersion = schemaVersion;
            this.refreshUrl = refreshUrl;
            this.symbolFont = symbolFont;
            this.families = Collections.unmodifiableList(new ArrayList<>(families));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        @Nullable
        public Family family(@Nullable String id) {
            if (id == null) return null;
            for (Family family : families) {
                if (family.id.equals(id)) return family;
            }
            return null;
        }
    }

    private static Result cached;

    private FontCatalog() {}

    /**
     * Loads and caches the bundled catalog. Safe to call from the UI thread: the file is a
     * few kilobytes of assets, read once per process.
     */
    @NonNull
    public static synchronized Result load(@NonNull Context context) {
        if (cached != null) return cached;
        String json;
        try (InputStream input = context.getApplicationContext().getAssets().open(ASSET_PATH)) {
            json = readBounded(input);
        } catch (Exception e) {
            cached = new Result(0, "", null, Collections.<Family>emptyList(),
                Collections.singletonList("cannot read " + ASSET_PATH + ": " + e.getMessage()));
            return cached;
        }
        cached = parse(json);
        return cached;
    }

    /** Drops the cached parse. Only for tests. */
    static synchronized void resetForTesting() {
        cached = null;
    }

    /** Parses catalog JSON. Package-visible so tests can feed crafted documents. */
    @NonNull
    static Result parse(@Nullable String json) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            errors.add("catalog is empty");
            return new Result(0, "", null, Collections.<Family>emptyList(), errors);
        }
        if (json.length() > MAX_JSON_BYTES) {
            errors.add("catalog exceeds " + MAX_JSON_BYTES + " characters");
            return new Result(0, "", null, Collections.<Family>emptyList(), errors);
        }
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            errors.add("catalog is not valid JSON: " + e.getMessage());
            return new Result(0, "", null, Collections.<Family>emptyList(), errors);
        }
        int schemaVersion = root.optInt("schemaVersion", 0);
        // An unknown future schema is not something this build can validate, so refuse the whole
        // file rather than guess which half of it still means what it used to.
        if (schemaVersion != 1) {
            errors.add("unsupported catalog schemaVersion " + schemaVersion);
            return new Result(schemaVersion, "", null, Collections.<Family>emptyList(), errors);
        }
        String refreshUrl = rawText(root, "refreshUrl");
        if (!refreshUrl.isEmpty() && !isHttpsUrl(refreshUrl)) {
            errors.add("refreshUrl must be an https URL");
            refreshUrl = "";
        }
        SymbolFont symbolFont = parseSymbolFont(root.optJSONObject("symbolFont"), errors);

        List<Family> families = new ArrayList<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        JSONArray array = root.optJSONArray("families");
        if (array == null) {
            errors.add("catalog has no families array");
        } else {
            for (int i = 0; i < array.length(); i++) {
                if (families.size() >= MAX_FAMILIES) {
                    errors.add("family count exceeds " + MAX_FAMILIES);
                    break;
                }
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    errors.add("family " + i + " is not an object");
                    continue;
                }
                Family family = parseFamily(object, i, errors);
                if (family == null) continue;
                if (!seenIds.add(family.id)) {
                    errors.add("duplicate family id '" + family.id + "'");
                    continue;
                }
                families.add(family);
            }
        }
        return new Result(schemaVersion, refreshUrl, symbolFont, families, errors);
    }

    @Nullable
    private static SymbolFont parseSymbolFont(@Nullable JSONObject object, @NonNull List<String> errors) {
        if (object == null) {
            errors.add("catalog has no symbolFont");
            return null;
        }
        String assetPath = rawText(object, "assetPath");
        String installName = rawText(object, "installName");
        String sha256 = rawText(object, "sha256").toLowerCase(Locale.US);
        long sizeBytes = object.optLong("sizeBytes", 0L);
        if (assetPath.isEmpty() || !isSafeRelativePath(assetPath)) {
            errors.add("symbolFont assetPath is missing or unsafe");
            return null;
        }
        if (!isSafeFileName(installName)) {
            errors.add("symbolFont installName is missing or unsafe");
            return null;
        }
        if (!isSha256(sha256)) {
            errors.add("symbolFont sha256 must be 64 hex characters");
            return null;
        }
        if (sizeBytes <= 0L || sizeBytes > MAX_FACE_BYTES) {
            errors.add("symbolFont sizeBytes must be 1.." + MAX_FACE_BYTES);
            return null;
        }
        String licenseUrl = rawText(object, "licenseUrl");
        if (!licenseUrl.isEmpty() && !isHttpsUrl(licenseUrl)) {
            errors.add("symbolFont licenseUrl must be an https URL");
            licenseUrl = "";
        }
        return new SymbolFont(assetPath, installName,
            text(object, "displayName", MAX_NAME_CHARS), text(object, "sourceTag", MAX_NAME_CHARS),
            sha256, sizeBytes, text(object, "license", MAX_NAME_CHARS), licenseUrl);
    }

    @Nullable
    private static Family parseFamily(@NonNull JSONObject object, int index, @NonNull List<String> errors) {
        String id = rawText(object, "id");
        if (!isSafeId(id)) {
            errors.add("family " + index + " has an invalid id '" + id + "'");
            return null;
        }
        String displayName = text(object, "displayName", MAX_NAME_CHARS);
        if (displayName.isEmpty()) {
            errors.add(id + ": displayName is required");
            return null;
        }
        String license = text(object, "license", MAX_NAME_CHARS);
        String licenseUrl = rawText(object, "licenseUrl");
        // Shipping an OFL face without naming its license is not something the picker should
        // ever offer, so an unlicensed family is dropped rather than shown.
        if (license.isEmpty() || licenseUrl.isEmpty()) {
            errors.add(id + ": license and licenseUrl are required");
            return null;
        }
        if (!isHttpsUrl(licenseUrl)) {
            errors.add(id + ": licenseUrl must be an https URL");
            return null;
        }
        String homepageUrl = rawText(object, "homepageUrl");
        if (!homepageUrl.isEmpty() && !isHttpsUrl(homepageUrl)) {
            errors.add(id + ": homepageUrl must be an https URL");
            homepageUrl = "";
        }
        String ligatures = text(object, "defaultLigatures", MAX_NAME_CHARS).toLowerCase(Locale.US);
        if (ligatures.isEmpty()) ligatures = "never";
        if (!"never".equals(ligatures) && !"cursor".equals(ligatures) && !"always".equals(ligatures)) {
            errors.add(id + ": defaultLigatures must be never, cursor or always");
            return null;
        }
        boolean variable = object.optBoolean("variable", false);
        WeightAxis axis = parseWeightAxis(object.optJSONObject("weightAxis"), id, errors);
        if (variable && axis == null) {
            errors.add(id + ": a variable family needs a weightAxis");
            return null;
        }

        Map<String, Archive> archives = new java.util.LinkedHashMap<>();
        JSONArray archiveArray = object.optJSONArray("archives");
        if (archiveArray != null) {
            for (int i = 0; i < archiveArray.length(); i++) {
                if (archives.size() >= MAX_ARCHIVES_PER_FAMILY) {
                    errors.add(id + ": archive count exceeds " + MAX_ARCHIVES_PER_FAMILY);
                    return null;
                }
                Archive archive = parseArchive(archiveArray.optJSONObject(i), id, errors);
                if (archive == null) return null;
                archives.put(archive.url, archive);
            }
        }

        JSONObject faceObject = object.optJSONObject("faces");
        if (faceObject == null) {
            errors.add(id + ": faces object is required");
            return null;
        }
        EnumMap<FaceSlot, Face> faces = new EnumMap<>(FaceSlot.class);
        long directBytes = 0L;
        java.util.Iterator<String> keys = faceObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            FaceSlot slot = FaceSlot.fromKey(key);
            if (slot == null) {
                errors.add(id + ": unknown face '" + key + "'");
                return null;
            }
            if (faces.size() >= MAX_FACES_PER_FAMILY) {
                errors.add(id + ": face count exceeds " + MAX_FACES_PER_FAMILY);
                return null;
            }
            Face face = parseFace(faceObject.optJSONObject(key), id, key, archives.keySet(), errors);
            if (face == null) return null;
            if (!face.isArchiveMember()) directBytes += face.sizeBytes;
            faces.put(slot, face);
        }
        // The regular face is the one mirrored to ~/.termux/font.ttf and the one every other
        // face falls back to; a family without it cannot be installed coherently.
        if (!faces.containsKey(FaceSlot.REGULAR)) {
            errors.add(id + ": a regular face is required");
            return null;
        }
        long downloadBytes = directBytes;
        for (Archive archive : archives.values()) downloadBytes += archive.sizeBytes;
        if (downloadBytes <= 0L || downloadBytes > MAX_FAMILY_DOWNLOAD_BYTES) {
            errors.add(id + ": download size must be 1.." + MAX_FAMILY_DOWNLOAD_BYTES + " bytes");
            return null;
        }

        return new Family(id, displayName, text(object, "summary", MAX_TEXT_CHARS),
            object.optBoolean("recommended", false), variable,
            text(object, "releaseTag", MAX_NAME_CHARS), homepageUrl, license, licenseUrl,
            text(object, "licenseNotice", MAX_TEXT_CHARS), ligatures,
            text(object, "defaultFontFeatures", MAX_TEXT_CHARS), axis,
            new ArrayList<>(archives.values()), faces, downloadBytes);
    }

    @Nullable
    private static WeightAxis parseWeightAxis(@Nullable JSONObject object, @NonNull String id,
                                              @NonNull List<String> errors) {
        if (object == null) return null;
        int min = object.optInt("min", 0);
        int max = object.optInt("max", 0);
        int regular = object.optInt("regularWeight", 0);
        int bold = object.optInt("boldWeight", 0);
        if (min < MIN_WEIGHT || max > MAX_WEIGHT || min >= max
            || regular < min || regular > max || bold < min || bold > max) {
            errors.add(id + ": weightAxis must satisfy " + MIN_WEIGHT + " <= min < max <= " + MAX_WEIGHT
                + " with regularWeight and boldWeight inside the range");
            return null;
        }
        return new WeightAxis(min, max, regular, bold);
    }

    @Nullable
    private static Archive parseArchive(@Nullable JSONObject object, @NonNull String id,
                                        @NonNull List<String> errors) {
        if (object == null) {
            errors.add(id + ": archive entry is not an object");
            return null;
        }
        String url = rawText(object, "url");
        if (!isHttpsUrl(url)) {
            errors.add(id + ": archive url must be an https URL under " + MAX_URL_CHARS + " characters");
            return null;
        }
        String sha256 = rawText(object, "sha256").toLowerCase(Locale.US);
        if (!isSha256(sha256)) {
            errors.add(id + ": archive sha256 must be 64 hex characters");
            return null;
        }
        long sizeBytes = object.optLong("sizeBytes", 0L);
        if (sizeBytes <= 0L || sizeBytes > MAX_FAMILY_DOWNLOAD_BYTES) {
            errors.add(id + ": archive sizeBytes must be 1.." + MAX_FAMILY_DOWNLOAD_BYTES);
            return null;
        }
        return new Archive(url, sha256, sizeBytes);
    }

    @Nullable
    private static Face parseFace(@Nullable JSONObject object, @NonNull String id,
                                  @NonNull String key, @NonNull java.util.Set<String> archiveUrls,
                                  @NonNull List<String> errors) {
        if (object == null) {
            errors.add(id + ": face '" + key + "' is not an object");
            return null;
        }
        String sha256 = rawText(object, "sha256").toLowerCase(Locale.US);
        if (!isSha256(sha256)) {
            errors.add(id + "/" + key + ": sha256 must be 64 hex characters");
            return null;
        }
        long sizeBytes = object.optLong("sizeBytes", 0L);
        if (sizeBytes <= 0L || sizeBytes > MAX_FACE_BYTES) {
            errors.add(id + "/" + key + ": sizeBytes must be 1.." + MAX_FACE_BYTES);
            return null;
        }
        String url = rawText(object, "url");
        String zipUrl = rawText(object, "zipUrl");
        String memberPath = rawText(object, "memberPath");
        if (!url.isEmpty() && !zipUrl.isEmpty()) {
            errors.add(id + "/" + key + ": set either url or zipUrl, not both");
            return null;
        }
        if (!url.isEmpty()) {
            if (!isHttpsUrl(url)) {
                errors.add(id + "/" + key + ": url must be an https URL under "
                    + MAX_URL_CHARS + " characters");
                return null;
            }
            return new Face(url, "", "", sha256, sizeBytes);
        }
        if (zipUrl.isEmpty()) {
            errors.add(id + "/" + key + ": needs a url or a zipUrl");
            return null;
        }
        if (!isHttpsUrl(zipUrl)) {
            errors.add(id + "/" + key + ": zipUrl must be an https URL under "
                + MAX_URL_CHARS + " characters");
            return null;
        }
        // Every archive-backed face must point at a declared archive, so the download size the
        // UI shows and the bytes the downloader fetches can never drift apart.
        if (!archiveUrls.contains(zipUrl)) {
            errors.add(id + "/" + key + ": zipUrl is not declared in archives");
            return null;
        }
        if (!isSafeRelativePath(memberPath)) {
            errors.add(id + "/" + key + ": memberPath is missing or unsafe");
            return null;
        }
        return new Face("", zipUrl, memberPath, sha256, sizeBytes);
    }

    /**
     * Display text, truncated to {@code maxChars}. Only for values that are shown to the user —
     * truncating an identifier, a URL or a digest would turn an over-long value into a
     * different, valid-looking one, so those go through {@link #rawText} and are validated whole.
     */
    @NonNull
    private static String text(@NonNull JSONObject object, @NonNull String key, int maxChars) {
        String value = rawText(object, key);
        return value.length() > maxChars ? value.substring(0, maxChars) : value;
    }

    /** Untruncated, trimmed value. The caller's validator enforces the length. */
    @NonNull
    private static String rawText(@NonNull JSONObject object, @NonNull String key) {
        String value = object.optString(key, "");
        return value == null ? "" : value.trim();
    }

    /** HTTPS only, and never a bare scheme. Plain HTTP font downloads are not acceptable. */
    static boolean isHttpsUrl(@Nullable String url) {
        return url != null
            && url.length() > "https://".length()
            && url.length() <= MAX_URL_CHARS
            && url.startsWith("https://")
            && url.indexOf(' ') < 0
            && url.indexOf('\n') < 0
            && url.indexOf('\r') < 0;
    }

    static boolean isSha256(@Nullable String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    /** Family ids become directory names, so they stay lowercase ASCII with dashes. */
    static boolean isSafeId(@Nullable String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_CHARS) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return false;
        }
        return true;
    }

    /**
     * Relative, traversal-free path. Rejects absolute paths, {@code ..} segments, backslashes
     * (Windows-style separators that some zip writers emit) and control characters — the
     * zip-slip guard, applied at parse time as well as at extraction time.
     */
    static boolean isSafeRelativePath(@Nullable String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_MEMBER_PATH_CHARS) return false;
        if (path.charAt(0) == '/' || path.charAt(0) == '\\') return false;
        if (path.indexOf('\\') >= 0) return false;
        if (path.contains("//")) return false;
        if (path.endsWith("/")) return false;
        // Drive-letter absolute paths ("C:/x") are absolute too.
        if (path.length() > 1 && path.charAt(1) == ':') return false;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) < 0x20 || path.charAt(i) == 0x7f) return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        return true;
    }

    /** A single path-free file name. */
    static boolean isSafeFileName(@Nullable String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME_CHARS
            && name.indexOf('/') < 0 && name.indexOf('\\') < 0
            && !".".equals(name) && !"..".equals(name);
    }

    @NonNull
    private static String readBounded(@NonNull InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_JSON_BYTES) {
                throw new IllegalStateException("catalog exceeds " + MAX_JSON_BYTES + " bytes");
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
