package com.termux.ai;

import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Downloaded models saved to a folder in shared storage, in the layout the Model centre's folder
 * import takes back ({@link TaiModelImporter#exportedModels}). One folder per model, named after
 * its folder under {@code tai/models}, holding the model's files exactly as they sit there (a
 * LiteRT bundle, a Whisper graph and its tokenizer, a whole MNN or image package) and
 * {@link #MANIFEST_NAME}: the model's own record, which names those files and their sizes. The
 * manifest is written last, so a folder without one is a copy that never finished.
 *
 * <p>The plan, the copy over a {@link Folder} and the manifest rules are here so they can be tested
 * without a document provider; the export service and the importer supply the storage.
 */
public final class TaiModelExport {
    /** The folder made inside the one the user picks. */
    public static final String FOLDER_NAME = "Termux Launcher models";
    public static final String MANIFEST_NAME = "termux-launcher-model.json";
    static final String FORMAT = "termux-launcher-model";
    static final int VERSION = 1;
    /** A manifest is a few kilobytes; a larger file of that name is not one this app wrote. */
    public static final long MAX_MANIFEST_BYTES = 1024L * 1024L;
    /** The copy buffer: one per export, large enough that a gigabyte is a thousand calls. */
    public static final int BUFFER_BYTES = 1024 * 1024;
    /** A model folder with more files than this is not one this app wrote. */
    static final int MAX_FILES = 10_000;

    private static final Set<String> EXPOSURES = new HashSet<>(Arrays.asList(
        TaiModelStore.EXPOSURE_SPLIT, TaiModelStore.EXPOSURE_COMBINED, TaiModelStore.EXPOSURE_BOTH));

    private TaiModelExport() {}

    /** One file of a model: its path inside the model's folder and its size. */
    public static final class Entry {
        @NonNull public final String path;
        public final long size;
        /** The file on this phone; null for an entry read from a manifest. */
        @Nullable final File source;

        Entry(@NonNull String path, long size, @Nullable File source) {
            this.path = path;
            this.size = size;
            this.source = source;
        }
    }

    /** One model to export: where it goes, what it holds and the manifest that says so. */
    public static final class Model {
        @NonNull public final String folder;
        @NonNull public final String id;
        @NonNull public final String displayName;
        @NonNull public final List<Entry> files;
        public final long bytes;
        @NonNull final JSONObject manifest;

        Model(@NonNull String folder, @NonNull String id, @NonNull String displayName,
              @NonNull List<Entry> files, @NonNull JSONObject manifest) {
            this.folder = folder;
            this.id = id;
            this.displayName = displayName;
            this.files = Collections.unmodifiableList(files);
            long total = 0L;
            for (Entry entry : files) total += entry.size;
            this.bytes = total;
            this.manifest = manifest;
        }
    }

    /** A model folder's manifest, read back and checked. */
    public static final class Manifest {
        @NonNull public final String id;
        @NonNull public final String displayName;
        /** The file the model's record points at, inside its folder; "" for the folder itself (an image model). */
        @NonNull public final String main;
        @NonNull public final List<Entry> files;
        public final long bytes;
        @Nullable public final String exposure;
        @NonNull final JSONObject model;

        Manifest(@NonNull String id, @NonNull String displayName, @NonNull String main, @NonNull List<Entry> files,
                 @Nullable String exposure, @NonNull JSONObject model) {
            this.id = id;
            this.displayName = displayName;
            this.main = main;
            this.files = Collections.unmodifiableList(files);
            long total = 0L;
            for (Entry entry : files) total += entry.size;
            this.bytes = total;
            this.exposure = exposure;
            this.model = model;
        }
    }

    // ---- what to export ----

    /**
     * The models to export, each with the files it is made of. {@code models} is what the store
     * calls installed; a model whose record points outside {@code modelsDir}, or at a file that is
     * gone, is left out. {@code exposures} holds each model's stored exposure where it is not the
     * default. Lists the folders only; nothing is read.
     */
    @NonNull
    public static List<Model> plan(@NonNull Collection<TaiModelSpec> models, @NonNull File modelsDir,
                                   @NonNull Map<String, String> exposures) {
        LinkedHashMap<String, Model> planned = new LinkedHashMap<>();
        for (TaiModelSpec spec : models) {
            if (spec.isVisionTool() || spec.localPath == null || spec.localPath.trim().isEmpty()) continue;
            File main = new File(spec.localPath).getAbsoluteFile();
            String folder = modelFolder(modelsDir, main);
            if (folder == null || planned.containsKey(folder)) continue;
            File dir = new File(modelsDir.getAbsoluteFile(), folder);
            String mainPath = relativePath(dir, main);
            if (mainPath == null) continue;
            List<Entry> files = new ArrayList<>();
            collectFiles(dir, "", files);
            if (files.isEmpty() || !mainPath.isEmpty() && !contains(files, mainPath)) continue;
            try {
                planned.put(folder, new Model(folder, spec.id, spec.displayName, files,
                    manifest(spec, mainPath, files, exposures.get(spec.id))));
            } catch (JSONException ignored) {
                // A record that cannot be written out is left out, like one whose files are gone.
            }
        }
        return new ArrayList<>(planned.values());
    }

    /** The name of the folder directly under {@code modelsDir} that holds {@code file}, or null. */
    @Nullable
    static String modelFolder(@NonNull File modelsDir, @NonNull File file) {
        File root = modelsDir.getAbsoluteFile();
        File current = file.getAbsoluteFile();
        while (current != null) {
            File parent = current.getParentFile();
            if (root.equals(parent)) return current.getName().startsWith(".") ? null : current.getName();
            current = parent;
        }
        return null;
    }

    /** {@code file}'s path inside {@code dir} with '/' separators, "" for the folder itself, or null outside it. */
    @Nullable
    static String relativePath(@NonNull File dir, @NonNull File file) {
        String base = dir.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.equals(base)) return "";
        if (!path.startsWith(base + File.separator)) return null;
        return path.substring(base.length() + 1).replace(File.separatorChar, '/');
    }

    /** Partial downloads and unfinished imports: never part of a model. */
    static boolean isTransient(@NonNull String name) {
        return name.endsWith(".part") || name.endsWith(".part.source") || name.endsWith(".importing");
    }

    private static void collectFiles(@NonNull File dir, @NonNull String prefix, @NonNull List<Entry> files) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
        for (File child : children) {
            if (files.size() >= MAX_FILES) return;
            String name = child.getName();
            if (child.isDirectory()) {
                collectFiles(child, prefix + name + "/", files);
            } else if (child.isFile() && !isTransient(name) && !MANIFEST_NAME.equals(name)) {
                files.add(new Entry(prefix + name, child.length(), child));
            }
        }
    }

    private static boolean contains(@NonNull List<Entry> files, @NonNull String path) {
        for (Entry entry : files) if (entry.path.equals(path)) return true;
        return false;
    }

    @NonNull
    static JSONObject manifest(@NonNull TaiModelSpec spec, @NonNull String main, @NonNull List<Entry> files,
                               @Nullable String exposure) throws JSONException {
        JSONObject model = spec.toJson();
        // A path on this phone means nothing on the next one; the import puts it back.
        model.remove("localPath");
        JSONArray list = new JSONArray();
        for (Entry entry : files) list.put(new JSONObject().put("path", entry.path).put("size", entry.size));
        JSONObject manifest = new JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("main", main)
            .put("files", list)
            .put("model", model);
        if (exposure != null && !TaiModelStore.EXPOSURE_SPLIT.equals(exposure)) manifest.put("exposure", exposure);
        return manifest;
    }

    // ---- the copy ----

    /** Copying a file again is skipped when one of the same size is already there: a re-run resumes. */
    static boolean needsCopy(long existingSize, long size) {
        return existingSize < 0L || existingSize != size;
    }

    /** Where an export writes: a folder in shared storage, or a plain directory under test. */
    public interface Folder {
        /** The folder {@code name} inside this one, made when {@code create}; null when absent or not made. */
        @Nullable Folder child(@NonNull String name, boolean create) throws IOException;

        /** The size of the file {@code name} here, or -1 when there is none. */
        long size(@NonNull String name);

        /** A new, empty file {@code name}, replacing one of that name. */
        @NonNull OutputStream create(@NonNull String name) throws IOException;

        /** Removes the file {@code name}; quiet when it cannot. */
        void delete(@NonNull String name);
    }

    /** Hears the copy advance; returning false cancels it. */
    public interface Listener {
        boolean onProgress(@NonNull Model model, long doneBytes, long totalBytes);
    }

    /** How an export ended. */
    public static final class Result {
        public final int saved;
        public final boolean cancelled;
        @Nullable public final IOException error;

        Result(int saved, boolean cancelled, @Nullable IOException error) {
            this.saved = saved;
            this.cancelled = cancelled;
            this.error = error;
        }
    }

    /** The bytes {@link #export} would still write into {@code root}; a model already there counts nothing. */
    public static long bytesToWrite(@NonNull List<Model> models, @NonNull Folder root) throws IOException {
        long total = 0L;
        Folder base = root.child(FOLDER_NAME, false);
        for (Model model : models) {
            Folder dir = base == null ? null : base.child(model.folder, false);
            for (Entry entry : model.files) {
                Folder parent = dir == null ? null : folderOf(dir, entry.path, false);
                if (needsCopy(parent == null ? -1L : parent.size(name(entry.path)), entry.size)) total += entry.size;
            }
        }
        return total;
    }

    /**
     * Copies {@code models} into {@link #FOLDER_NAME} inside {@code root}, one folder each, with the
     * manifest last. A file already there at the same size is kept. On cancel or an error the file
     * being written is removed; the models finished before it stay.
     */
    @NonNull
    public static Result export(@NonNull List<Model> models, @NonNull Folder root, @NonNull Listener listener) {
        byte[] buffer = new byte[BUFFER_BYTES];
        int saved = 0;
        long total;
        try {
            total = bytesToWrite(models, root);
        } catch (IOException e) {
            return new Result(0, false, e);
        }
        long[] done = {0L};
        for (Model model : models) {
            Folder parent = null;
            String name = null;
            try {
                Folder base = root.child(FOLDER_NAME, true);
                Folder dir = base == null ? null : base.child(model.folder, true);
                if (dir == null) throw new IOException("Could not make the folder for " + model.folder);
                for (Entry entry : model.files) {
                    parent = folderOf(dir, entry.path, true);
                    name = name(entry.path);
                    if (parent == null) throw new IOException("Could not make the folder for " + entry.path);
                    if (!needsCopy(parent.size(name), entry.size)) {
                        name = null;
                        continue;
                    }
                    if (entry.source == null) throw new IOException("No source for " + entry.path);
                    copy(entry.source, parent, name, buffer, model, done, total, listener);
                    name = null;
                }
                parent = dir;
                name = MANIFEST_NAME;
                try (OutputStream out = dir.create(MANIFEST_NAME)) {
                    out.write(model.manifest.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                name = null;
                saved++;
            } catch (Cancelled e) {
                if (parent != null && name != null) parent.delete(name);
                return new Result(saved, true, null);
            } catch (IOException | JSONException | RuntimeException e) {
                if (parent != null && name != null) parent.delete(name);
                return new Result(saved, false, e instanceof IOException ? (IOException) e : new IOException(e));
            }
        }
        return new Result(saved, false, null);
    }

    private static void copy(@NonNull File source, @NonNull Folder parent, @NonNull String name, @NonNull byte[] buffer,
                             @NonNull Model model, @NonNull long[] done, long total,
                             @NonNull Listener listener) throws IOException, Cancelled {
        try (InputStream in = new FileInputStream(source); OutputStream out = parent.create(name)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done[0] += read;
                if (!listener.onProgress(model, done[0], total)) throw new Cancelled();
            }
        }
    }

    /** The folder holding {@code path} inside {@code dir}, walking (and making) each step. */
    @Nullable
    private static Folder folderOf(@NonNull Folder dir, @NonNull String path, boolean create) throws IOException {
        Folder current = dir;
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1 && current != null; i++) current = current.child(segments[i], create);
        return current;
    }

    @NonNull
    private static String name(@NonNull String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static final class Cancelled extends Exception {
        Cancelled() {
            super(null, null, false, false);
        }
    }

    // ---- reading it back ----

    /**
     * Checks a manifest read from a picked folder. It is untrusted: the id must already be a safe
     * model id, every path must stay inside the folder, and the record must name a backend this
     * app runs. Throws {@link IllegalArgumentException} with the reason otherwise.
     */
    @NonNull
    public static Manifest readManifest(@NonNull JSONObject json) {
        if (!FORMAT.equals(json.optString("format", ""))) throw new IllegalArgumentException("not_a_model_folder");
        int version = json.optInt("version", 0);
        if (version < 1 || version > VERSION) throw new IllegalArgumentException("newer_format");
        JSONObject model = json.optJSONObject("model");
        if (model == null) throw new IllegalArgumentException("missing_model");
        String id = model.optString("id", "");
        if (id.isEmpty() || !id.equals(TaiModelImporter.sanitizeModelId(id)) || id.startsWith("."))
            throw new IllegalArgumentException("bad_id");
        JSONArray list = json.optJSONArray("files");
        if (list == null || list.length() == 0 || list.length() > MAX_FILES) throw new IllegalArgumentException("bad_files");
        List<Entry> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            String path = item == null ? "" : item.optString("path", "");
            long size = item == null ? -1L : item.optLong("size", -1L);
            if (path.isEmpty() || !TaiHuggingFace.safePath(path) || path.endsWith("/")
                || MANIFEST_NAME.equals(path) || size < 0L || !seen.add(path))
                throw new IllegalArgumentException("bad_file");
            files.add(new Entry(path, size, null));
        }
        String main = json.optString("main", "");
        if (!main.isEmpty() && !seen.contains(main)) throw new IllegalArgumentException("bad_main");
        String exposure = json.has("exposure") ? json.optString("exposure", "") : null;
        if (exposure != null && !EXPOSURES.contains(exposure)) exposure = null;
        return new Manifest(id, model.optString("displayName", id), main, files, exposure, model);
    }

    /**
     * The model's record as it was on the phone that saved it, pointing at its files in
     * {@code destination}. Throws {@link IllegalArgumentException} for a backend this app cannot run.
     */
    @NonNull
    public static TaiModelSpec restoredSpec(@NonNull Manifest manifest, @NonNull File destination) {
        try {
            JSONObject model = new JSONObject(manifest.model.toString());
            File path = manifest.main.isEmpty() ? destination : new File(destination, manifest.main);
            model.put("localPath", path.getAbsolutePath());
            return TaiModelSpec.fromJson(model);
        } catch (JSONException e) {
            throw new IllegalArgumentException("bad_model", e);
        }
    }

    // ---- words ----

    /** "3 models · 6.2 GB": the Export AI models row's size line. */
    @NonNull
    public static String sizeSummary(@NonNull Resources resources, int count, long bytes) {
        return resources.getString(R.string.settings_backup_models_size,
            resources.getQuantityString(R.plurals.settings_backup_models_count, count, count), formatBytes(bytes));
    }

    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes <= 0L) return "0 B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
