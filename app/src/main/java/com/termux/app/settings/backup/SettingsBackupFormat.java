package com.termux.app.settings.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiRemoteSettings;
import com.termux.ai.TaiSettings;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The settings backup file, without Android: which preference keys and which files belong in it,
 * how typed values are written and read back, how the terminal font size follows a change of
 * screen density, and which entry names an imported file may hold.
 *
 * <p>The file is one zip:
 * <ul>
 *   <li>{@code manifest.json}: format version, the exporting app's version and edition, the
 *       exporting screen's density and the time it was made.</li>
 *   <li>{@code prefs/<store>.json}: one preference store's values, each with its type. Stores are
 *       named logically ({@link Store}), never by file name, since the main store's file name
 *       carries the edition's package.</li>
 *   <li>{@code files/<root>/<path>}: config files, under a logical root ({@link Root}) that each
 *       install resolves to its own directory.</li>
 *   <li>{@code links/<root>/<path>}: one of the named single files that is a symlink, such as
 *       {@code ~/.termux/shell} (which {@code chsh} maintains) or a {@code font.ttf} pointing into
 *       a package, kept as its target with this install's files directory written as
 *       {@link #FILES_DIR_TOKEN}. A link is never followed.</li>
 * </ul>
 *
 * <p>The same rules decide what export writes and what import accepts, so an entry the exporter
 * would never write is refused on the way in. Nothing outside these rules is ever read from the
 * file: an entry name that is unknown, absolute or climbs out of its root refuses the whole file.
 */
public final class SettingsBackupFormat {

    private SettingsBackupFormat() {}

    public static final int FORMAT_VERSION = 1;

    /** A single file above this is left out of an export (and refused on import). */
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    /** The whole bundle: export stops adding files here, import refuses anything larger. */
    public static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    /** The manifest, a preference store or a link: small text, never close to this. */
    public static final long MAX_TEXT_BYTES = 8L * 1024 * 1024;
    /** How deep a directory tree is walked, counted in path segments below the root. */
    static final int MAX_PATH_DEPTH = 8;

    public static final String MANIFEST_ENTRY = "manifest.json";
    static final String PREFS_PREFIX = "prefs/";
    static final String PREFS_SUFFIX = ".json";
    static final String FILES_PREFIX = "files/";
    static final String LINKS_PREFIX = "links/";

    /** Stands for this install's files directory inside a stored link target. */
    static final String FILES_DIR_TOKEN = "@FILES@";

    // ------------------------------------------------------------------ stores

    /** The preference stores a backup carries, by the name they have inside the file. */
    public enum Store {
        /** The launcher's main store, {@code <package>_preferences}. */
        MAIN("main"),
        /** On-device AI settings, {@code termux_ai}. */
        AI("ai"),
        /** Terminal font choices, {@code termux_fonts}. */
        FONTS("fonts"),
        /** Privileged access switches, {@code termux_privileged_policy}. */
        PRIVILEGED("privileged");

        @NonNull public final String id;

        Store(@NonNull String id) { this.id = id; }

        @NonNull public String entryName() { return PREFS_PREFIX + id + PREFS_SUFFIX; }

        /**
         * Whether {@code key} is one this store's backup carries. Import writes only these and
         * removes only these, so every other key (usage, device ids, secrets) is never touched.
         */
        public boolean includes(@Nullable String key) {
            if (key == null || key.isEmpty()) return false;
            switch (this) {
                case MAIN: return !mainExcludes(key);
                case AI: return aiIncludes(key);
                case FONTS: return FONTS_KEYS.contains(key);
                case PRIVILEGED: return PRIVILEGED_KEYS.contains(key);
                default: return false;
            }
        }

        @Nullable
        static Store forEntry(@NonNull String name) {
            for (Store store : values()) if (store.entryName().equals(name)) return store;
            return null;
        }
    }

    /**
     * Main-store keys that stay with the phone: usage and ranking history, caches and transient
     * state, the system's wallpaper ids, and per-install flags such as the first-run tour. The
     * list is a denylist so a new setting is exported without anyone remembering to add it.
     */
    static final Set<String> MAIN_EXCLUDED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        // Usage and ranking (LauncherUsageStatsStore, CommandPaletteActionStats, LauncherCategorySortState).
        "app_launcher_az_usage_stats_v1",
        "command_palette_action_stats_v1",
        "command_palette_query_memory_v1",
        "app_launcher_category_sort_state_v1",
        "app_launcher_category_answered_other_v1",
        // Caches and transient state (SettingsActivity, AppearanceReturnState).
        "settings_back_stack_state_v1",
        "appearance_return_state_v1",
        TERMUX_APP.KEY_WALL_LAST_PAGE,
        TERMUX_APP.KEY_CURRENT_SESSION,
        TERMUX_APP.KEY_LAST_NOTIFICATION_ID,
        // The plugins' PendingIntent counter (TERMUX_API_APP's key), in case one lands here.
        "last_pending_intent_request_code",
        TERMUX_APP.KEY_X11_KEYBOARD_SHOWN,
        TERMUX_APP.KEY_KEYBOARD_TURNED_OFF,
        // The system's ids for the wallpaper this phone shows.
        TERMUX_APP.KEY_MANAGED_WALLPAPER_SYSTEM_ID,
        TERMUX_APP.KEY_MANAGED_WALLPAPER_LOCK_ID,
        // Per-install flags.
        TERMUX_APP.KEY_EXTRA_KEYS_DEFAULT_OFFERED,
        TERMUX_APP.KEY_X11_DEFAULTS_APPLIED,
        // An absolute path into this install; the font file travels instead (see importer).
        TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH)));

    static final List<String> MAIN_EXCLUDED_PREFIXES = Collections.unmodifiableList(Arrays.asList(
        "extra_key_glyph_recents",
        "app_launcher_use_case_snapshot",
        // Restore slots for values wallpaper mode set aside.
        "wallpaper_enabled_",
        "first_run_",
        "first_boot_tour_"));

    static final List<String> MAIN_EXCLUDED_SUFFIXES = Collections.unmodifiableList(Arrays.asList(
        "_number_since_boot",
        "_prompted"));

    static boolean mainExcludes(@NonNull String key) {
        if (MAIN_EXCLUDED_KEYS.contains(key)) return true;
        for (String prefix : MAIN_EXCLUDED_PREFIXES) if (key.startsWith(prefix)) return true;
        for (String suffix : MAIN_EXCLUDED_SUFFIXES) if (key.endsWith(suffix)) return true;
        return false;
    }

    /**
     * On-device AI keys a backup carries: an allowlist, because this store also holds tokens,
     * probe results and download state. The Hugging Face token and the local API token are named
     * in {@link #AI_NEVER_KEYS} as well, so no prefix can ever let them through.
     */
    static final Set<String> AI_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        TaiSettings.KEY_SYSTEM_PROMPT_GENERAL,
        TaiSettings.KEY_MAX_TOKENS,
        TaiSettings.KEY_TOP_K,
        TaiSettings.KEY_TOP_P,
        TaiSettings.KEY_TEMPERATURE,
        TaiSettings.KEY_ACCELERATOR,
        TaiSettings.KEY_THINKING,
        TaiSettings.KEY_SPECULATIVE_DECODING,
        TaiSettings.KEY_CONTEXT_WINDOW,
        TaiSettings.KEY_IDLE_UNLOAD_MINUTES,
        TaiSettings.KEY_API_PORT,
        TaiSettings.KEY_API_BIND_MODE,
        TaiSettings.KEY_API_AUTH_REQUIRED,
        TaiSettings.KEY_OPENAI_AUTO_LOAD,
        TaiSettings.KEY_STT_MODEL_ID,
        TaiSettings.KEY_STT_WINDOW_SECONDS,
        TaiSettings.KEY_STT_IDLE_UNLOAD_MINUTES,
        TaiSettings.KEY_TTS_VOICE,
        TaiSettings.KEY_TTS_SPEED,
        TaiSettings.KEY_DOWNLOAD_PARALLEL,
        TaiRemoteSettings.KEY_BASE_URL,
        TaiRemoteSettings.KEY_PRESET,
        TaiRemoteSettings.KEY_MODEL,
        TaiRemoteSettings.KEY_IMAGES_OVERRIDE,
        TaiRemoteSettings.KEY_ROUTING)));

    static final List<String> AI_PREFIXES = Collections.unmodifiableList(Arrays.asList(
        "tai_global_parameter.",
        "tai_model_parameter.",
        "tai_model_system_prompt.",
        // Which model each feature uses (TaiFunction), and the assistant role.
        "tai_fn_",
        "tai_role_"));

    static final Set<String> AI_NEVER_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        TaiSettings.KEY_HUGGINGFACE_TOKEN,
        TaiSettings.KEY_API_TOKEN)));

    static boolean aiIncludes(@NonNull String key) {
        if (AI_NEVER_KEYS.contains(key)) return false;
        if (AI_KEYS.contains(key)) return true;
        for (String prefix : AI_PREFIXES) if (key.startsWith(prefix)) return true;
        return false;
    }

    /** FontSettings' choices; the metered-download acknowledgement stays with the phone. */
    static final Set<String> FONTS_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "active_family_id", "nerd_icons", "ligatures", "recommended_features", "weight")));

    static final Set<String> PRIVILEGED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        PrivilegedPolicyStore.KEY_MASTER_ENABLED,
        PrivilegedPolicyStore.KEY_PREFER_SHIZUKU,
        PrivilegedPolicyStore.KEY_ALLOW_SHELL_FALLBACK,
        PrivilegedPolicyStore.KEY_LANE_ENABLED)));

    // ------------------------------------------------------------------ typed values

    private static final String FIELD_STORE = "store";
    private static final String FIELD_VALUES = "values";
    private static final String FIELD_TYPE = "t";
    private static final String FIELD_VALUE = "v";
    private static final String TYPE_BOOLEAN = "bool";
    private static final String TYPE_INT = "int";
    private static final String TYPE_LONG = "long";
    private static final String TYPE_FLOAT = "float";
    private static final String TYPE_STRING = "string";
    private static final String TYPE_STRING_SET = "set";

    /**
     * One store's values as the file holds them, keeping only the keys {@code store} carries.
     * Numbers are written as text so an int, a long and a float each come back exactly.
     */
    @NonNull
    public static JSONObject encodeStore(@NonNull Store store, @NonNull Map<String, ?> all) throws JSONException {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : new java.util.TreeMap<>(all).entrySet()) {
            String key = entry.getKey();
            if (!store.includes(key)) continue;
            JSONObject typed = encodeValue(entry.getValue());
            if (typed != null) values.put(key, typed);
        }
        return new JSONObject().put(FIELD_STORE, store.id).put(FIELD_VALUES, values);
    }

    @Nullable
    private static JSONObject encodeValue(@Nullable Object value) throws JSONException {
        if (value instanceof Boolean) return typed(TYPE_BOOLEAN, value);
        if (value instanceof Integer) return typed(TYPE_INT, Integer.toString((Integer) value));
        if (value instanceof Long) return typed(TYPE_LONG, Long.toString((Long) value));
        if (value instanceof Float) return typed(TYPE_FLOAT, Float.toString((Float) value));
        if (value instanceof String) return typed(TYPE_STRING, value);
        if (value instanceof Set) {
            JSONArray array = new JSONArray();
            for (Object item : new java.util.TreeSet<>(stringsOf((Set<?>) value))) array.put(item);
            return typed(TYPE_STRING_SET, array);
        }
        return null;
    }

    @NonNull
    private static Set<String> stringsOf(@NonNull Set<?> set) {
        Set<String> out = new LinkedHashSet<>();
        for (Object item : set) if (item instanceof String) out.add((String) item);
        return out;
    }

    @NonNull
    private static JSONObject typed(@NonNull String type, @NonNull Object value) throws JSONException {
        return new JSONObject().put(FIELD_TYPE, type).put(FIELD_VALUE, value);
    }

    /**
     * A store's values read back as SharedPreferences types (Boolean, Integer, Long, Float,
     * String, Set of String). Keys {@code store} does not carry are dropped, as are values whose
     * type is unknown or whose text does not parse, so a hand-edited file cannot slip in a secret
     * or a device id.
     */
    @NonNull
    public static Map<String, Object> decodeStore(@NonNull Store store, @NonNull JSONObject root) throws InvalidBackupException {
        if (!store.id.equals(root.optString(FIELD_STORE, null))) throw new InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP);
        JSONObject values = root.optJSONObject(FIELD_VALUES);
        if (values == null) throw new InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP);
        Map<String, Object> out = new LinkedHashMap<>();
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!store.includes(key)) continue;
            Object value = decodeValue(values.optJSONObject(key));
            if (value != null) out.put(key, value);
        }
        return out;
    }

    @Nullable
    private static Object decodeValue(@Nullable JSONObject typed) {
        if (typed == null) return null;
        String type = typed.optString(FIELD_TYPE, "");
        try {
            switch (type) {
                case TYPE_BOOLEAN: {
                    Object value = typed.opt(FIELD_VALUE);
                    return value instanceof Boolean ? value : null;
                }
                case TYPE_INT: return Integer.parseInt(typed.getString(FIELD_VALUE));
                case TYPE_LONG: return Long.parseLong(typed.getString(FIELD_VALUE));
                case TYPE_FLOAT: return Float.parseFloat(typed.getString(FIELD_VALUE));
                case TYPE_STRING: {
                    Object value = typed.opt(FIELD_VALUE);
                    return value instanceof String ? value : null;
                }
                case TYPE_STRING_SET: {
                    JSONArray array = typed.optJSONArray(FIELD_VALUE);
                    if (array == null) return null;
                    Set<String> set = new LinkedHashSet<>();
                    for (int i = 0; i < array.length(); i++) {
                        Object item = array.opt(i);
                        if (item instanceof String) set.add((String) item);
                    }
                    return set;
                }
                default: return null;
            }
        } catch (JSONException | NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ font size

    /**
     * The terminal font size keys ({@code fontsize}, per display, and the scratchpad's): stored
     * in pixels, so they follow the screen's density.
     */
    static boolean isFontSizeKey(@NonNull String key) {
        return key.startsWith(TERMUX_APP.KEY_FONTSIZE);
    }

    /**
     * {@code px} from a {@code fromDensity} screen as the same physical size on a
     * {@code toDensity} one, clamped to this install's font size limits. An unknown density on
     * either side leaves the size as it was, still clamped.
     */
    static int scaleFontSize(int px, float fromDensity, float toDensity, int min, int max) {
        long scaled = fromDensity > 0f && toDensity > 0f
            ? Math.round((double) px * toDensity / fromDensity) : px;
        return (int) Math.max(min, Math.min(max, scaled));
    }

    /**
     * The main store's values with every font size moved to this screen's density. A size is
     * kept as the text it is stored as; one that is not a number is dropped, so the reader falls
     * back to its default rather than to a size meant for another screen.
     */
    @NonNull
    static Map<String, Object> withScaledFontSizes(@NonNull Map<String, Object> values, float fromDensity,
                                                   float toDensity, int min, int max) {
        Map<String, Object> out = new LinkedHashMap<>(values);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!isFontSizeKey(entry.getKey())) continue;
            Object value = entry.getValue();
            Integer px = null;
            if (value instanceof Integer) px = (Integer) value;
            else if (value instanceof String) {
                try { px = Integer.parseInt(((String) value).trim()); } catch (NumberFormatException ignored) { }
            }
            if (px == null) { out.remove(entry.getKey()); continue; }
            int scaled = scaleFontSize(px, fromDensity, toDensity, min, max);
            out.put(entry.getKey(), value instanceof Integer ? (Object) scaled : Integer.toString(scaled));
        }
        return out;
    }

    // ------------------------------------------------------------------ files

    /** The directories config files are kept under, resolved by each install to its own. */
    public enum Root {
        /** {@code ~/.termux}. */
        TERMUX("termux"),
        /** {@code ~/.config/termux}. */
        CONFIG("config-termux"),
        /** The app's private files directory. */
        APP("app");

        @NonNull public final String id;

        Root(@NonNull String id) { this.id = id; }

        @NonNull public String entryName(@NonNull String relative) { return FILES_PREFIX + id + "/" + relative; }

        @Nullable
        static Root forId(@NonNull String id) {
            for (Root root : values()) if (root.id.equals(id)) return root;
            return null;
        }
    }

    /** Single files under {@code ~/.termux}. */
    static final List<String> TERMUX_FILES = Collections.unmodifiableList(Arrays.asList(
        "termux.properties", "colors.properties", "font.ttf", "font-italic.ttf", "fonts.conf",
        "termux-launcher-bindings.conf", "launcher-theme.properties", "app-categories.conf",
        "keyboard/layout.xml", "shell"));

    /** Directories under {@code ~/.termux} carried whole (regular files only, no links followed). */
    static final List<String> TERMUX_TREES = Collections.unmodifiableList(Arrays.asList(
        "fonts.d", "theme-templates", "workspaces", "background"));

    /** The font config the launcher writes for a downloaded family; it is rebuilt, not carried. */
    static final String MANAGED_FONT_CONFIG = "fonts.d/10-launcher.conf";

    /** The secondary termux.properties under {@code ~/.config/termux}. */
    static final List<String> CONFIG_FILES = Collections.singletonList("termux.properties");

    /** Where the keyboard's label font is kept (KeyboardColorSchemeFragment). */
    public static final String KEYBOARD_FONT_FILE = "inapp-keyboard/label-font.ttf";
    /** The home screen's picture, the managed wallpaper's exact copy (WallpaperPictureReader). */
    public static final String HOME_WALLPAPER_FILE = "managed-wallpaper/system-wallpaper-exact.png";
    /** The Lock slot's own photo (WallpaperSlots). */
    public static final String LOCK_WALLPAPER_FILE = "wallpaper/slots/lock.png";
    /** On-device AI memory mode, a setting kept as a file (TaiSettings). */
    static final String TAI_MEMORY_MODE_FILE = "tai/memory-mode";

    static final List<String> APP_FILES = Collections.unmodifiableList(Arrays.asList(
        KEYBOARD_FONT_FILE, TAI_MEMORY_MODE_FILE, HOME_WALLPAPER_FILE, LOCK_WALLPAPER_FILE));

    /** {@code ~/.termux/shell} as a plain file: the login script runs it, so it keeps its execute bit. */
    static final String SHELL_FILE = "shell";

    /** Whether a restored file must be executable. */
    static boolean isExecutable(@NonNull Root root, @NonNull String relative) {
        return root == Root.TERMUX && SHELL_FILE.equals(relative);
    }

    /** Whether {@code relative} under {@code root} may be carried as a link: the named single files only. */
    static boolean carriesLink(@NonNull Root root, @NonNull String relative) {
        switch (root) {
            case TERMUX: return TERMUX_FILES.contains(relative);
            case CONFIG: return CONFIG_FILES.contains(relative);
            default: return false;
        }
    }

    /** Whether {@code relative} under {@code root} is a file a backup carries. */
    static boolean carriesFile(@NonNull Root root, @NonNull String relative) {
        if (!isSafeRelativePath(relative)) return false;
        switch (root) {
            case TERMUX:
                if (TERMUX_FILES.contains(relative)) return true;
                if (MANAGED_FONT_CONFIG.equals(relative)) return false;
                int slash = relative.indexOf('/');
                return slash > 0 && TERMUX_TREES.contains(relative.substring(0, slash));
            case CONFIG: return CONFIG_FILES.contains(relative);
            case APP: return APP_FILES.contains(relative);
            default: return false;
        }
    }

    /**
     * A relative path with plain segments only: no leading slash, no empty, {@code .} or
     * {@code ..} segment, no backslash or NUL, and no deeper than {@link #MAX_PATH_DEPTH}.
     */
    static boolean isSafeRelativePath(@Nullable String relative) {
        return isSafeRelativePath(relative, MAX_PATH_DEPTH);
    }

    private static boolean isSafeRelativePath(@Nullable String relative, int maxDepth) {
        if (relative == null || relative.isEmpty() || relative.length() > 512) return false;
        if (relative.indexOf('\\') >= 0 || relative.indexOf('\0') >= 0 || relative.startsWith("/")) return false;
        String[] segments = relative.split("/", -1);
        if (segments.length > maxDepth) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ entries

    /** One zip entry, classified. */
    public static final class Entry {
        public enum Kind { MANIFEST, PREFS, FILE, LINK }

        @NonNull public final Kind kind;
        @Nullable public final Store store;
        @Nullable public final Root root;
        @Nullable public final String relative;

        private Entry(@NonNull Kind kind, @Nullable Store store, @Nullable Root root, @Nullable String relative) {
            this.kind = kind;
            this.store = store;
            this.root = root;
            this.relative = relative;
        }
    }

    /** The zip entry name of a link kept as its target. */
    @NonNull
    static String linkEntryName(@NonNull Root root, @NonNull String relative) {
        return LINKS_PREFIX + root.id + "/" + relative;
    }

    /**
     * Whether a folder entry (one ending in {@code /}, which archivers add on their own) is
     * harmless to skip: a safe path under one of the backup's own top-level folders. Nothing is
     * ever made from a folder entry.
     */
    public static boolean classifyFolder(@NonNull String name) {
        if (!name.endsWith("/")) return false;
        String path = name.substring(0, name.length() - 1);
        if (!isSafeRelativePath(path, MAX_PATH_DEPTH + 2)) return false;
        String first = path.contains("/") ? path.substring(0, path.indexOf('/') + 1) : path + "/";
        return PREFS_PREFIX.equals(first) || FILES_PREFIX.equals(first) || LINKS_PREFIX.equals(first);
    }

    /**
     * What an entry name is, or null when a backup never holds it. Import refuses the whole file
     * on a null, so a name that is absolute, climbs with {@code ..} or names an unknown root never
     * reaches the disk.
     */
    @Nullable
    public static Entry classify(@Nullable String name) {
        // Two more segments than a file's own path: the "files/" prefix and the root.
        if (name == null || !isSafeRelativePath(name, MAX_PATH_DEPTH + 2)) return null;
        if (MANIFEST_ENTRY.equals(name)) return new Entry(Entry.Kind.MANIFEST, null, null, null);
        if (name.startsWith(PREFS_PREFIX)) {
            Store store = Store.forEntry(name);
            return store == null ? null : new Entry(Entry.Kind.PREFS, store, null, null);
        }
        boolean file = name.startsWith(FILES_PREFIX);
        if (!file && !name.startsWith(LINKS_PREFIX)) return null;
        String rest = name.substring(file ? FILES_PREFIX.length() : LINKS_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        Root root = Root.forId(rest.substring(0, slash));
        String relative = rest.substring(slash + 1);
        if (root == null) return null;
        if (file) return carriesFile(root, relative) ? new Entry(Entry.Kind.FILE, null, root, relative) : null;
        return carriesLink(root, relative) ? new Entry(Entry.Kind.LINK, null, root, relative) : null;
    }

    // ------------------------------------------------------------------ links

    /**
     * A link target as the file stores it: inside this install's files directory (under any of
     * the spellings in {@code filesDirs}, which differ between {@code /data/data} and
     * {@code /data/user/0}) it is written relative to {@link #FILES_DIR_TOKEN}, so another
     * edition's package resolves it to its own prefix; anything else is kept as it is.
     */
    @NonNull
    static String linkTargetToLogical(@NonNull String target, @NonNull List<String> filesDirs) {
        for (String dir : filesDirs) {
            if (dir == null || dir.isEmpty()) continue;
            String prefix = dir.endsWith("/") ? dir : dir + "/";
            if (target.startsWith(prefix)) return FILES_DIR_TOKEN + "/" + target.substring(prefix.length());
        }
        return target;
    }

    /**
     * The stored target as a path on this install, or null when it is not a plain path (empty,
     * relative, or climbing out of the files directory).
     */
    @Nullable
    static String linkTargetFromLogical(@Nullable String logical, @NonNull String filesDir) {
        if (logical == null) return null;
        String value = logical.trim();
        if (value.startsWith(FILES_DIR_TOKEN + "/")) {
            String relative = value.substring(FILES_DIR_TOKEN.length() + 1);
            if (!isSafeRelativePath(relative)) return null;
            return (filesDir.endsWith("/") ? filesDir : filesDir + "/") + relative;
        }
        if (!value.startsWith("/") || value.indexOf('\0') >= 0 || value.contains("/../")) return null;
        return value;
    }

    // ------------------------------------------------------------------ manifest

    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_VERSION_NAME = "versionName";
    private static final String FIELD_EDITION = "edition";
    private static final String FIELD_DENSITY = "density";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_SKIPPED = "skipped";

    /** What the manifest says about the export. */
    public static final class Manifest {
        public final int format;
        @NonNull public final String versionName;
        @NonNull public final String edition;
        public final float density;
        public final long createdAt;
        @NonNull public final List<String> skipped;

        public Manifest(int format, @NonNull String versionName, @NonNull String edition, float density,
                        long createdAt, @NonNull List<String> skipped) {
            this.format = format;
            this.versionName = versionName;
            this.edition = edition;
            this.density = density;
            this.createdAt = createdAt;
            this.skipped = Collections.unmodifiableList(new java.util.ArrayList<>(skipped));
        }
    }

    @NonNull
    public static JSONObject encodeManifest(@NonNull Manifest manifest) throws JSONException {
        JSONArray skipped = new JSONArray();
        for (String path : manifest.skipped) skipped.put(path);
        return new JSONObject()
            .put(FIELD_FORMAT, manifest.format)
            .put(FIELD_VERSION_NAME, manifest.versionName)
            .put(FIELD_EDITION, manifest.edition)
            .put(FIELD_DENSITY, Float.toString(manifest.density))
            .put(FIELD_CREATED_AT, manifest.createdAt)
            .put(FIELD_SKIPPED, skipped);
    }

    /** The manifest, refused unless it names {@link #FORMAT_VERSION}. */
    @NonNull
    public static Manifest decodeManifest(@NonNull String text) throws InvalidBackupException {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP);
        }
        int format = root.optInt(FIELD_FORMAT, -1);
        if (format <= 0) throw new InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP);
        if (format != FORMAT_VERSION) throw new InvalidBackupException(InvalidBackupException.Reason.NEWER_FORMAT);
        float density;
        try {
            density = Float.parseFloat(root.optString(FIELD_DENSITY, "0"));
        } catch (NumberFormatException e) {
            density = 0f;
        }
        if (Float.isNaN(density) || Float.isInfinite(density) || density < 0f) density = 0f;
        List<String> skipped = new java.util.ArrayList<>();
        JSONArray array = root.optJSONArray(FIELD_SKIPPED);
        if (array != null) for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String) skipped.add((String) item);
        }
        return new Manifest(format, root.optString(FIELD_VERSION_NAME, ""), root.optString(FIELD_EDITION, ""),
            density, root.optLong(FIELD_CREATED_AT, 0L), skipped);
    }

    /** {@code termux-launcher-settings-YYYY-MM-DD.zip} for the given local date. */
    @NonNull
    public static String suggestedFileName(int year, int month, int day) {
        return String.format(Locale.ROOT, "termux-launcher-settings-%04d-%02d-%02d.zip", year, month, day);
    }

    /** Why an imported file was refused. */
    public static final class InvalidBackupException extends java.io.IOException {
        public enum Reason { NOT_A_BACKUP, NEWER_FORMAT, TOO_LARGE }

        @NonNull public final Reason reason;

        public InvalidBackupException(@NonNull Reason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }
}
