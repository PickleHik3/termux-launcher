package com.termux.app.settings.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.BuildConfig;
import com.termux.ai.TaiSettings;
import com.termux.app.chrome.ManagedWallpaper;
import com.termux.app.chrome.wallpaper.WallpaperSlots;
import com.termux.app.fonts.FontSettings;
import com.termux.app.settings.backup.SettingsBackupFormat.Entry;
import com.termux.app.settings.backup.SettingsBackupFormat.InvalidBackupException;
import com.termux.app.settings.backup.SettingsBackupFormat.Manifest;
import com.termux.app.settings.backup.SettingsBackupFormat.Root;
import com.termux.app.settings.backup.SettingsBackupFormat.Store;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Writes and reads the settings backup ({@link SettingsBackupFormat}) against an install's
 * preference stores and directories. Everything here blocks: call it off the main thread.
 *
 * <p>Import is two steps so nothing changes before the user confirms: {@link #read} checks the
 * whole file and stages its files in the cache directory, then {@link #apply} puts them in place
 * (each written beside its target and renamed over it) and writes the preference stores with
 * {@code commit()}. {@link #discard} throws a staged import away.
 */
public final class SettingsBackupIo {

    private static final String LOG_TAG = "SettingsBackup";

    /** A link target is a path; anything longer than this is not one. */
    private static final int MAX_LINK_BYTES = 4096;
    /** How many files a tree walk adds at most, so a runaway directory cannot stall an export. */
    private static final int MAX_TREE_FILES = 4096;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    /** Suffix of the file a target is written to before it is renamed into place. */
    private static final String PARTIAL_SUFFIX = ".import-partial";

    private SettingsBackupIo() {}

    // ------------------------------------------------------------------ environment

    /** What export and import need from an install; tests stand in their own stores and folders. */
    public static final class Environment {
        @NonNull final Map<Store, SharedPreferences> prefs;
        @NonNull final Map<Root, File> roots;
        /** The spellings of this install's files directory a stored link target may begin with. */
        @NonNull final List<String> filesDirSpellings;
        /** The files directory a stored link target is resolved against. */
        @NonNull final String filesDir;
        final float density;
        final int fontSizeMin;
        final int fontSizeMax;
        @NonNull final String versionName;
        @NonNull final String edition;
        @NonNull final File stagingDir;
        /** The app-private files an export includes, when they exist. */
        @NonNull final Set<String> exportedAppFiles;

        public Environment(@NonNull Map<Store, SharedPreferences> prefs, @NonNull Map<Root, File> roots,
                           @NonNull List<String> filesDirSpellings, @NonNull String filesDir, float density,
                           int fontSizeMin, int fontSizeMax, @NonNull String versionName,
                           @NonNull String edition, @NonNull File stagingDir,
                           @NonNull Set<String> exportedAppFiles) {
            this.prefs = prefs;
            this.roots = roots;
            this.filesDirSpellings = filesDirSpellings;
            this.filesDir = filesDir;
            this.density = density;
            this.fontSizeMin = fontSizeMin;
            this.fontSizeMax = fontSizeMax;
            this.versionName = versionName;
            this.edition = edition;
            this.stagingDir = stagingDir;
            this.exportedAppFiles = exportedAppFiles;
        }
    }

    /**
     * This install's stores and folders. {@code forExport} also decides which wallpaper pictures
     * an export carries, which asks the system and so must run off the main thread.
     */
    @WorkerThread
    @NonNull
    public static Environment environment(@NonNull Context context, boolean forExport) {
        Context app = context.getApplicationContext();
        Map<Store, SharedPreferences> prefs = new EnumMap<>(Store.class);
        prefs.put(Store.MAIN, app.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, Context.MODE_PRIVATE));
        prefs.put(Store.AI, app.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE));
        prefs.put(Store.FONTS, app.getSharedPreferences(FontSettings.PREFS_NAME, Context.MODE_PRIVATE));
        prefs.put(Store.PRIVILEGED, app.getSharedPreferences(PrivilegedPolicyStore.PREFS_NAME, Context.MODE_PRIVATE));
        Map<Root, File> roots = new EnumMap<>(Root.class);
        roots.put(Root.TERMUX, TermuxConstants.TERMUX_DATA_HOME_DIR);
        roots.put(Root.CONFIG, TermuxConstants.TERMUX_CONFIG_HOME_DIR);
        roots.put(Root.APP, app.getFilesDir());
        int[] fontSizes = TermuxAppSharedPreferences.getDefaultFontSizes(app);
        return new Environment(prefs, roots,
            Arrays.asList(TermuxConstants.TERMUX_FILES_DIR_PATH, app.getFilesDir().getAbsolutePath()),
            TermuxConstants.TERMUX_FILES_DIR_PATH,
            app.getResources().getDisplayMetrics().density, fontSizes[1], fontSizes[2],
            BuildConfig.VERSION_NAME, AndroidUtils.getEditionForPackageName(app.getPackageName()),
            new File(app.getCacheDir(), "settings-import"),
            forExport ? exportedAppFiles(app) : Collections.emptySet());
    }

    /**
     * The keyboard font and AI memory mode always; the home picture only while the system still
     * shows the launcher's copy of it, and the lock picture only while the Lock slot holds the
     * launcher's own photo. A picture another app set would come back stale, so it stays behind.
     */
    @WorkerThread
    @NonNull
    private static Set<String> exportedAppFiles(@NonNull Context app) {
        Set<String> files = new HashSet<>(Arrays.asList(
            SettingsBackupFormat.KEYBOARD_FONT_FILE, SettingsBackupFormat.TAI_MEMORY_MODE_FILE));
        try {
            WallpaperSlots.State state = WallpaperSlots.read(app);
            if (state.home.photoFile != null) files.add(SettingsBackupFormat.HOME_WALLPAPER_FILE);
            if (!state.lockElsewhere && state.lock.photo && state.lock.photoFile != null)
                files.add(SettingsBackupFormat.LOCK_WALLPAPER_FILE);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper slots failed; leaving the pictures out", e);
        }
        return files;
    }

    // ------------------------------------------------------------------ export

    /** What an export left out. */
    public static final class ExportResult {
        /** Files over the size limits, by their place in the backup. */
        @NonNull public final List<String> skipped;

        ExportResult(@NonNull List<String> skipped) { this.skipped = skipped; }
    }

    private static final class Candidate {
        @NonNull final Root root;
        @NonNull final String relative;
        @NonNull final File file;
        final long length;

        Candidate(@NonNull Root root, @NonNull String relative, @NonNull File file) {
            this.root = root;
            this.relative = relative;
            this.file = file;
            this.length = file.length();
        }
    }

    /** Writes the backup to {@code out}, which the caller closes. */
    @WorkerThread
    @NonNull
    public static ExportResult export(@NonNull Environment env, @NonNull OutputStream out, long createdAt) throws IOException {
        Map<String, byte[]> texts = new LinkedHashMap<>();
        try {
            for (Store store : Store.values()) {
                SharedPreferences preferences = env.prefs.get(store);
                if (preferences == null) continue;
                texts.put(store.entryName(), bytes(SettingsBackupFormat.encodeStore(store, preferences.getAll()).toString(2)));
            }
        } catch (JSONException e) {
            throw new IOException("Encoding preferences failed", e);
        }
        Map<String, byte[]> links = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        collectSingleFiles(env, Root.TERMUX, SettingsBackupFormat.TERMUX_FILES, candidates, links);
        collectSingleFiles(env, Root.CONFIG, SettingsBackupFormat.CONFIG_FILES, candidates, links);
        File termux = env.roots.get(Root.TERMUX);
        if (termux != null) {
            for (String tree : SettingsBackupFormat.TERMUX_TREES) walk(Root.TERMUX, new File(termux, tree), tree, candidates);
        }
        File app = env.roots.get(Root.APP);
        if (app != null) {
            for (String relative : SettingsBackupFormat.APP_FILES) {
                if (!env.exportedAppFiles.contains(relative)) continue;
                File file = new File(app, relative);
                if (isRegularFile(file)) candidates.add(new Candidate(Root.APP, relative, file));
            }
        }

        long total = 0;
        for (byte[] text : texts.values()) total += text.length;
        for (byte[] text : links.values()) total += text.length;
        List<Candidate> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String name = candidate.root.entryName(candidate.relative);
            if (candidate.length > SettingsBackupFormat.MAX_FILE_BYTES
                || total + candidate.length > SettingsBackupFormat.MAX_TOTAL_BYTES) {
                skipped.add(name);
                continue;
            }
            total += candidate.length;
            included.add(candidate);
        }

        Manifest manifest = new Manifest(SettingsBackupFormat.FORMAT_VERSION, env.versionName, env.edition,
            env.density, createdAt, skipped);
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out));
        try {
            putText(zip, SettingsBackupFormat.MANIFEST_ENTRY, bytes(SettingsBackupFormat.encodeManifest(manifest).toString(2)));
        } catch (JSONException e) {
            throw new IOException("Encoding the manifest failed", e);
        }
        for (Map.Entry<String, byte[]> text : texts.entrySet()) putText(zip, text.getKey(), text.getValue());
        for (Map.Entry<String, byte[]> link : links.entrySet()) putText(zip, link.getKey(), link.getValue());
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        for (Candidate candidate : included) {
            zip.putNextEntry(new ZipEntry(candidate.root.entryName(candidate.relative)));
            try (InputStream in = new FileInputStream(candidate.file)) {
                copy(in, zip, buffer, SettingsBackupFormat.MAX_FILE_BYTES);
            }
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
        return new ExportResult(skipped);
    }

    /** The named files under {@code root}; a symlink is carried as its target, never followed. */
    private static void collectSingleFiles(@NonNull Environment env, @NonNull Root root, @NonNull List<String> names,
                                           @NonNull List<Candidate> candidates, @NonNull Map<String, byte[]> links) {
        File dir = env.roots.get(root);
        if (dir == null) return;
        for (String relative : names) {
            File file = new File(dir, relative);
            if (Files.isSymbolicLink(file.toPath())) {
                try {
                    String target = Files.readSymbolicLink(file.toPath()).toString();
                    links.put(SettingsBackupFormat.linkEntryName(root, relative),
                        bytes(SettingsBackupFormat.linkTargetToLogical(target, env.filesDirSpellings)));
                } catch (IOException | RuntimeException e) {
                    Logger.logWarn(LOG_TAG, "Reading the link " + relative + " failed: " + e.getMessage());
                }
            } else if (isRegularFile(file)) {
                candidates.add(new Candidate(root, relative, file));
            }
        }
    }

    /** Regular files under {@code dir}, in name order; links are skipped, never followed. */
    private static void walk(@NonNull Root root, @NonNull File dir, @NonNull String relative,
                             @NonNull List<Candidate> out) {
        if (out.size() >= MAX_TREE_FILES || Files.isSymbolicLink(dir.toPath()) || !dir.isDirectory()) return;
        if (relative.split("/").length >= SettingsBackupFormat.MAX_PATH_DEPTH) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (out.size() >= MAX_TREE_FILES) return;
            String childRelative = relative + "/" + child.getName();
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) {
                walk(root, child, childRelative, out);
            } else if (child.isFile() && SettingsBackupFormat.carriesFile(root, childRelative)) {
                out.add(new Candidate(root, childRelative, child));
            }
        }
    }

    private static boolean isRegularFile(@NonNull File file) {
        return file.isFile() && !Files.isSymbolicLink(file.toPath());
    }

    private static void putText(@NonNull ZipOutputStream zip, @NonNull String name, @NonNull byte[] text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text);
        zip.closeEntry();
    }

    @NonNull
    private static byte[] bytes(@NonNull String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ import: read

    /** A checked backup whose files wait in the staging directory. */
    public static final class Staged {
        @NonNull public final Manifest manifest;
        @NonNull final Map<Store, Map<String, Object>> values;
        /** Staged file by its place in the backup ({@link Root#entryName}). */
        @NonNull final Map<String, File> files;
        /** Stored link target by its place in the backup. */
        @NonNull final Map<String, String> links;
        @NonNull final File dir;

        Staged(@NonNull Manifest manifest, @NonNull Map<Store, Map<String, Object>> values,
               @NonNull Map<String, File> files, @NonNull Map<String, String> links, @NonNull File dir) {
            this.manifest = manifest;
            this.values = values;
            this.files = files;
            this.links = links;
            this.dir = dir;
        }

        boolean hasFile(@NonNull Root root, @NonNull String relative) {
            return files.containsKey(root.entryName(relative));
        }
    }

    /**
     * Reads and checks the whole file, staging its files; changes nothing else. Throws
     * {@link InvalidBackupException} for a file that is not a backup this version can restore.
     */
    @WorkerThread
    @NonNull
    public static Staged read(@NonNull Environment env, @NonNull InputStream in) throws IOException {
        deleteRecursively(env.stagingDir);
        if (!env.stagingDir.mkdirs() && !env.stagingDir.isDirectory())
            throw new IOException("Cannot create " + env.stagingDir);
        try {
            return readStaging(env, in);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(env.stagingDir);
            throw e;
        }
    }

    @NonNull
    private static Staged readStaging(@NonNull Environment env, @NonNull InputStream in) throws IOException {
        Manifest manifest = null;
        Map<Store, Map<String, Object>> values = new EnumMap<>(Store.class);
        Map<String, File> files = new LinkedHashMap<>();
        Map<String, String> links = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry zipEntry;
        while ((zipEntry = zip.getNextEntry()) != null) {
            String name = zipEntry.getName();
            if (zipEntry.isDirectory()) {
                // Folders an archiver added on its own; nothing is made from them.
                if (name == null || SettingsBackupFormat.classifyFolder(name)) continue;
                throw notABackup();
            }
            Entry entry = SettingsBackupFormat.classify(name);
            if (entry == null || !seen.add(name)) throw notABackup();
            long remaining = SettingsBackupFormat.MAX_TOTAL_BYTES - total;
            switch (entry.kind) {
                case MANIFEST: {
                    byte[] text = readLimited(zip, Math.min(remaining, SettingsBackupFormat.MAX_TEXT_BYTES));
                    total += text.length;
                    manifest = SettingsBackupFormat.decodeManifest(new String(text, StandardCharsets.UTF_8));
                    break;
                }
                case PREFS: {
                    byte[] text = readLimited(zip, Math.min(remaining, SettingsBackupFormat.MAX_TEXT_BYTES));
                    total += text.length;
                    try {
                        //noinspection ConstantConditions
                        values.put(entry.store, SettingsBackupFormat.decodeStore(entry.store,
                            new JSONObject(new String(text, StandardCharsets.UTF_8))));
                    } catch (JSONException e) {
                        throw notABackup();
                    }
                    break;
                }
                case LINK: {
                    byte[] text = readLimited(zip, Math.min(remaining, MAX_LINK_BYTES));
                    total += text.length;
                    links.put(name, new String(text, StandardCharsets.UTF_8));
                    break;
                }
                case FILE: {
                    //noinspection ConstantConditions
                    File staged = new File(env.stagingDir, entry.root.id + "/" + entry.relative);
                    File parent = staged.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                        throw new IOException("Cannot create " + parent);
                    try (OutputStream out = new FileOutputStream(staged)) {
                        total += copy(zip, out, buffer, Math.min(remaining, SettingsBackupFormat.MAX_FILE_BYTES));
                    }
                    files.put(name, staged);
                    break;
                }
                default:
                    throw notABackup();
            }
            zip.closeEntry();
        }
        if (manifest == null) throw notABackup();
        return new Staged(manifest, values, files, links, env.stagingDir);
    }

    @NonNull
    private static InvalidBackupException notABackup() {
        return new InvalidBackupException(InvalidBackupException.Reason.NOT_A_BACKUP);
    }

    @NonNull
    private static byte[] readLimited(@NonNull InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out, new byte[8192], limit);
        return out.toByteArray();
    }

    /** Copies everything, refusing more than {@code limit} bytes; returns the count. */
    private static long copy(@NonNull InputStream in, @NonNull OutputStream out, @NonNull byte[] buffer,
                             long limit) throws IOException {
        long count = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            count += read;
            if (count > limit) throw new InvalidBackupException(InvalidBackupException.Reason.TOO_LARGE);
            out.write(buffer, 0, read);
        }
        return count;
    }

    /** Throws away a staged import. */
    @WorkerThread
    public static void discard(@Nullable Staged staged) {
        if (staged != null) deleteRecursively(staged.dir);
    }

    // ------------------------------------------------------------------ import: apply

    /** How an apply went. */
    public static final class ApplyResult {
        /** Files, links or stores that could not be written. */
        public final int failures;

        ApplyResult(int failures) { this.failures = failures; }
    }

    /**
     * Puts the staged files in place, then writes each store the backup carries: its keys are
     * written, and the keys of the same kinds it lacks are removed so they fall back to their
     * defaults. Keys of other kinds (usage, device ids, secrets) are never touched. The staging
     * directory is removed afterwards whatever happened.
     */
    @WorkerThread
    @NonNull
    public static ApplyResult apply(@NonNull Environment env, @NonNull Staged staged) {
        int failures = 0;
        boolean keyboardFontPlaced = false;
        try {
            for (Map.Entry<String, File> file : staged.files.entrySet()) {
                Entry entry = SettingsBackupFormat.classify(file.getKey());
                File target = entry == null ? null : target(env, entry);
                if (target == null) { failures++; continue; }
                try {
                    place(file.getValue(), target);
                    //noinspection ConstantConditions
                    if (SettingsBackupFormat.isExecutable(entry.root, entry.relative)) {
                        //noinspection ResultOfMethodCallIgnored
                        target.setExecutable(true, true);
                    }
                    if (entry.root == Root.APP && SettingsBackupFormat.KEYBOARD_FONT_FILE.equals(entry.relative))
                        keyboardFontPlaced = true;
                } catch (IOException | RuntimeException e) {
                    failures++;
                    Logger.logWarn(LOG_TAG, "Restoring " + file.getKey() + " failed: " + e.getMessage());
                }
            }
            for (Map.Entry<String, String> link : staged.links.entrySet()) {
                Entry entry = SettingsBackupFormat.classify(link.getKey());
                File target = entry == null ? null : target(env, entry);
                String destination = SettingsBackupFormat.linkTargetFromLogical(link.getValue(), env.filesDir);
                if (target == null || destination == null) { failures++; continue; }
                try {
                    placeLink(destination, target);
                } catch (Exception e) {
                    failures++;
                    Logger.logWarn(LOG_TAG, "Restoring the link " + link.getKey() + " failed: " + e.getMessage());
                }
            }
            for (Map.Entry<Store, Map<String, Object>> store : staged.values.entrySet()) {
                SharedPreferences preferences = env.prefs.get(store.getKey());
                if (preferences == null) continue;
                Map<String, Object> values = store.getValue();
                if (store.getKey() == Store.MAIN) {
                    values = SettingsBackupFormat.withScaledFontSizes(values, staged.manifest.density,
                        env.density, env.fontSizeMin, env.fontSizeMax);
                }
                SharedPreferences.Editor editor = preferences.edit();
                for (String key : preferences.getAll().keySet()) {
                    if (store.getKey().includes(key) && !values.containsKey(key)) editor.remove(key);
                }
                for (Map.Entry<String, Object> value : values.entrySet()) put(editor, value.getKey(), value.getValue());
                if (store.getKey() == Store.MAIN) applyKeyboardFont(env, editor, keyboardFontPlaced);
                if (!editor.commit()) failures++;
            }
        } finally {
            deleteRecursively(staged.dir);
        }
        return new ApplyResult(failures);
    }

    /**
     * The keyboard label font is carried as a file: the path points at this install's copy when
     * the backup brought one, and is cleared (with any old copy) when it did not.
     */
    private static void applyKeyboardFont(@NonNull Environment env, @NonNull SharedPreferences.Editor editor,
                                          boolean placed) {
        File app = env.roots.get(Root.APP);
        if (app == null) return;
        File font = new File(app, SettingsBackupFormat.KEYBOARD_FONT_FILE);
        if (placed) {
            editor.putString(TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH, font.getAbsolutePath());
        } else {
            editor.remove(TERMUX_APP.KEY_IN_APP_KEYBOARD_FONT_PATH);
            //noinspection ResultOfMethodCallIgnored
            font.delete();
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(@NonNull SharedPreferences.Editor editor, @NonNull String key, @NonNull Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }

    /** Where an entry lands on this install, or null when it would land outside its root. */
    @Nullable
    private static File target(@NonNull Environment env, @NonNull Entry entry) {
        if (entry.root == null || entry.relative == null) return null;
        File root = env.roots.get(entry.root);
        if (root == null) return null;
        File target = new File(root, entry.relative);
        try {
            // The names were checked already; this is the belt to that brace.
            String rootPath = root.getCanonicalPath() + File.separator;
            String parentPath = target.getParentFile() == null ? "" : target.getParentFile().getCanonicalPath() + File.separator;
            return parentPath.startsWith(rootPath) ? target : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Copies {@code staged} beside {@code target} and renames it over, so a reader never sees half a file. */
    private static void place(@NonNull File staged, @NonNull File target) throws IOException {
        File parent = target.getParentFile();
        if (parent == null) throw new IOException("No parent for " + target);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        if (target.isDirectory() && !Files.isSymbolicLink(target.toPath())) throw new IOException(target + " is a folder");
        File partial = new File(parent, target.getName() + PARTIAL_SUFFIX);
        try (InputStream in = new FileInputStream(staged); FileOutputStream out = new FileOutputStream(partial, false)) {
            copy(in, out, new byte[COPY_BUFFER_BYTES], Long.MAX_VALUE);
            out.getFD().sync();
        }
        if (!partial.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            throw new IOException("Cannot replace " + target);
        }
    }

    /** Makes {@code target} a symlink to {@code destination}, made beside it and renamed over. */
    private static void placeLink(@NonNull String destination, @NonNull File target) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) throw new IOException("No parent for " + target);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        if (target.isDirectory() && !Files.isSymbolicLink(target.toPath())) throw new IOException(target + " is a folder");
        File partial = new File(parent, target.getName() + PARTIAL_SUFFIX);
        //noinspection ResultOfMethodCallIgnored
        partial.delete();
        Os.symlink(destination, partial.getAbsolutePath());
        Os.rename(partial.getAbsolutePath(), target.getAbsolutePath());
    }

    /**
     * Shows the restored wallpaper: the home picture is handed to the system again (to the lock
     * screen as well when Lock follows Home), and the Lock slot's own photo when it holds one.
     * The files are already in place; this is what makes the system show them.
     *
     * @return how many pictures could not be set
     */
    @WorkerThread
    public static int applyWallpapers(@NonNull Context context, @NonNull Environment env, @NonNull Staged staged) {
        Context app = context.getApplicationContext();
        File appDir = env.roots.get(Root.APP);
        if (appDir == null) return 0;
        File home = new File(appDir, SettingsBackupFormat.HOME_WALLPAPER_FILE);
        File lock = new File(appDir, SettingsBackupFormat.LOCK_WALLPAPER_FILE);
        boolean homeRestored = staged.hasFile(Root.APP, SettingsBackupFormat.HOME_WALLPAPER_FILE) && home.isFile();
        boolean lockRestored = staged.hasFile(Root.APP, SettingsBackupFormat.LOCK_WALLPAPER_FILE) && lock.isFile();
        if (!homeRestored && !lockRestored) return 0;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(app, false);
        String lockChoice = preferences == null ? TERMUX_APP.DEFAULT_VALUE_WALLPAPER_LOCK_CHOICE
            : preferences.getWallpaperLockChoice();
        boolean lockFollowsHome = TERMUX_APP.VALUE_WALLPAPER_LOCK_SAME_AS_HOME.equals(lockChoice);
        int failures = 0;
        try {
            if (homeRestored) {
                int flags = ManagedWallpaper.FLAGS_HOME | (lockFollowsHome ? ManagedWallpaper.FLAGS_LOCK : 0);
                if (WallpaperSlots.applyPhotoNow(app, home, flags, true, null) != null) failures++;
            }
            if (lockRestored && TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO.equals(lockChoice)) {
                if (WallpaperSlots.applyPhotoNow(app, lock, ManagedWallpaper.FLAGS_LOCK, false,
                    TERMUX_APP.VALUE_WALLPAPER_LOCK_PHOTO) != null) failures++;
            }
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Restoring the wallpaper failed", e);
            failures++;
        }
        return failures;
    }

    private static void deleteRecursively(@Nullable File file) {
        if (file == null) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
