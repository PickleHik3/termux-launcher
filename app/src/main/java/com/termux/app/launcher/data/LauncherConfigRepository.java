package com.termux.app.launcher.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable normalized launcher configuration. Schema v5 stores folders once and docks by ID. */
public final class LauncherConfigRepository {
    public static final int SCHEMA_VERSION = 5;

    @Nullable private static LauncherConfigRepository processInstance;
    @Nullable private static Context processApplicationContext;

    public interface PreferencesStore {
        String getPinnedItemsV2();
        int getPinnedItemsSchemaVersion();
        boolean commitPinnedItems(String value, int version);
        String getLegacyDefaultButtons();
    }

    public interface IconOverrideValidator {
        boolean isAvailable(@NonNull PinnedIconOverride iconOverride);
    }

    public interface Listener {
        void onLauncherConfigChanged(@NonNull LauncherConfigSnapshot snapshot);
    }

    public enum MutationResult { APPLIED, NO_OP, STALE, CAPACITY, MISSING }

    private final PreferencesStore preferences;
    private final List<Listener> listeners = new ArrayList<>();
    private long revision;
    @Nullable private LauncherConfigSnapshot cached;

    public LauncherConfigRepository(@NonNull TermuxAppSharedPreferences preferences) {
        this(new PreferencesStore() {
            @Override public String getPinnedItemsV2() { return preferences.getAppLauncherPinnedItemsV2(); }
            @Override public int getPinnedItemsSchemaVersion() {
                return preferences.getAppLauncherPinnedItemsSchemaVersion();
            }
            @Override public boolean commitPinnedItems(String value, int version) {
                return preferences.commitAppLauncherPinnedItems(value, version);
            }
            @Override public String getLegacyDefaultButtons() { return preferences.getAppLauncherDefaultButtons(); }
        });
    }

    public LauncherConfigRepository(@NonNull PreferencesStore preferences) {
        this.preferences = preferences;
    }

    /** One process-live cache shared by every production launcher config reader and writer. */
    @NonNull
    public static synchronized LauncherConfigRepository getInstance(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (processInstance == null || processApplicationContext != appContext) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(appContext, false);
            if (preferences == null)
                throw new IllegalStateException("Unable to open launcher preferences");
            processInstance = new LauncherConfigRepository(preferences);
            processApplicationContext = appContext;
        }
        return processInstance;
    }

    public synchronized void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** Compatibility accessor used by the dock; refs resolve to the snapshot's shared instances. */
    @NonNull
    public synchronized List<PinnedItem> loadPinnedItems() {
        return new ArrayList<>(loadSnapshot().dockItems);
    }

    @NonNull
    public synchronized LauncherConfigSnapshot loadSnapshot() {
        if (cached != null) return cached;
        String raw = preferences.getPinnedItemsV2();
        if (raw == null || raw.trim().isEmpty()) return migrateFromLegacySnapshot();
        try {
            JSONObject root = new JSONObject(raw);
            Parsed parsed = parseRoot(root);
            if (!parsed.valid) return migrateFromLegacySnapshot();
            LauncherFolderMutator.normalize(parsed.dockItems, parsed.folders);
            LauncherConfigSnapshot snapshot = snapshot(parsed, root.optJSONArray("appIconOverrides"));
            if (root.optInt("schemaVersion", 1) < SCHEMA_VERSION || parsed.requiresRewrite) {
                if (!write(snapshot.dockItems, snapshot.folders, root.optJSONArray("appIconOverrides"))) {
                    return snapshot;
                }
                snapshot = snapshot(parsed, root.optJSONArray("appIconOverrides"));
            }
            cached = snapshot;
            return snapshot;
        } catch (JSONException | RuntimeException ignored) {
            return migrateFromLegacySnapshot();
        }
    }

    /** Replaces dock references while retaining drawer-only folder entities. */
    public synchronized void savePinnedItems(@NonNull List<PinnedItem> pinnedItems) {
        LauncherConfigSnapshot before = loadSnapshot();
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        List<PinnedItem> dock = new ArrayList<>();
        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedAppItem) {
                dock.add(cloneApp((PinnedAppItem) item));
            } else if (item instanceof PinnedFolderItem) {
                PinnedFolderItem folder = cloneFolder((PinnedFolderItem) item);
                folders.put(folder.id, folder);
                dock.add(folder);
            }
        }
        LauncherFolderMutator.normalize(dock, folders);
        persistAndPublish(dock, folders, currentOverrides());
    }

    /** Creates a shared drawer entity without silently pinning it. */
    public synchronized MutationResult createFolder(long expectedRevision, @NonNull String folderId,
                                                     @NonNull PinnedAppItem target,
                                                     @NonNull PinnedAppItem source) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        if (target.appRef.stableId().equals(source.appRef.stableId())) return MutationResult.NO_OP;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        if (folders.containsKey(folderId)) return MutationResult.NO_OP;
        folders.put(folderId, LauncherFolderMutator.create(folderId, cloneApp(target), cloneApp(source)));
        return persistAndPublish(cloneDock(before.dockItems, folders), folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    public synchronized MutationResult addAppToFolder(long expectedRevision,
                                                       @NonNull String folderId,
                                                       @NonNull PinnedAppItem app) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        PinnedFolderItem folder = folders.get(folderId);
        if (folder == null) return MutationResult.MISSING;
        if (folder.containsApp(app.appRef)) return MutationResult.NO_OP;
        if (folder.apps.size() >= PinnedFolderItem.MAX_APPS) return MutationResult.CAPACITY;
        LauncherFolderMutator.append(folder, cloneApp(app));
        return persistAndPublish(cloneDock(before.dockItems, folders), folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    public synchronized MutationResult renameFolder(long expectedRevision,
                                                     @NonNull String folderId,
                                                     @Nullable String title) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        PinnedFolderItem folder = folders.get(folderId);
        if (folder == null) return MutationResult.MISSING;
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) normalized = "Folder";
        if (normalized.equals(folder.title)) return MutationResult.NO_OP;
        folder.title = normalized;
        return persistAndPublish(cloneDock(before.dockItems, folders), folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    public synchronized MutationResult removeAppFromFolder(long expectedRevision,
                                                            @NonNull String folderId,
                                                            @NonNull String appStableId) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        PinnedFolderItem folder = folders.get(folderId);
        if (folder == null) return MutationResult.MISSING;
        boolean removed = folder.apps.removeIf(app -> appStableId.equals(app.appRef.stableId()));
        if (!removed) return MutationResult.NO_OP;
        List<PinnedItem> dock = cloneDock(before.dockItems, folders);
        LauncherFolderMutator.normalize(dock, folders);
        return persistAndPublish(dock, folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    /** Removes dock references only; the shared entity remains available to the drawer. */
    public synchronized MutationResult unpinFolder(long expectedRevision, @NonNull String folderId) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        List<PinnedItem> dock = cloneDock(before.dockItems, folders);
        boolean removed = dock.removeIf(item -> item instanceof PinnedFolderItem
            && folderId.equals(((PinnedFolderItem) item).id));
        if (!removed) return MutationResult.NO_OP;
        return persistAndPublish(dock, folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    public synchronized MutationResult dissolveFolder(long expectedRevision, @NonNull String folderId) {
        LauncherConfigSnapshot before = loadSnapshot();
        if (before.revision != expectedRevision) return MutationResult.STALE;
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(before.folders);
        if (folders.remove(folderId) == null) return MutationResult.MISSING;
        List<PinnedItem> dock = cloneDock(before.dockItems, folders);
        dock.removeIf(item -> item instanceof PinnedFolderItem
            && folderId.equals(((PinnedFolderItem) item).id));
        return persistAndPublish(dock, folders, currentOverrides())
            ? MutationResult.APPLIED : MutationResult.NO_OP;
    }

    /** Returns the app-wide icon override for this exact app/profile, if one was selected. */
    @Nullable
    public synchronized PinnedIconOverride loadAppIconOverride(@NonNull AppRef ref) {
        JSONArray overrides = currentOverrides();
        String targetId = ref.stableId();
        for (int i = 0; i < overrides.length(); i++) {
            JSONObject item = overrides.optJSONObject(i);
            if (item != null && targetId.equals(appRefFromJson(item).stableId()))
                return parseIconOverride(item.optJSONObject("iconOverride"));
        }
        return null;
    }

    public synchronized void saveAppIconOverride(@NonNull AppRef ref,
                                                 @Nullable PinnedIconOverride iconOverride) {
        LauncherConfigSnapshot snapshot = loadSnapshot();
        JSONArray current = currentOverrides();
        JSONArray updated = new JSONArray();
        String targetId = ref.stableId();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item != null && !targetId.equals(appRefFromJson(item).stableId())) updated.put(item);
        }
        if (iconOverride != null && iconOverride.isValid()) {
            JSONObject item = new JSONObject();
            try {
                putAppRef(item, ref);
                putIconOverrideIfValid(item, iconOverride);
                updated.put(item);
            } catch (JSONException ignored) {}
        }
        LinkedHashMap<String, PinnedFolderItem> folders = cloneFolders(snapshot.folders);
        persistAndPublish(cloneDock(snapshot.dockItems, folders), folders, updated);
    }

    public synchronized boolean pruneInvalidIconOverrides(@NonNull IconOverrideValidator validator) {
        LauncherConfigSnapshot before = loadSnapshot();
        boolean changed = false;
        LinkedHashMap<String, PinnedFolderItem> folders = new LinkedHashMap<>();
        for (PinnedFolderItem source : before.folders.values()) {
            PinnedFolderItem folder = new PinnedFolderItem(source.id, source.title);
            folder.rows = source.rows;
            folder.cols = source.cols;
            folder.tintOverrideEnabled = source.tintOverrideEnabled;
            folder.tintColor = source.tintColor;
            for (PinnedAppItem app : source.apps) {
                if (app.iconOverride != null && !validator.isAvailable(app.iconOverride)) {
                    folder.apps.add(new PinnedAppItem(app.appRef.copy()));
                    changed = true;
                } else folder.apps.add(cloneApp(app));
            }
            folders.put(folder.id, folder);
        }
        List<PinnedItem> dock = new ArrayList<>();
        for (PinnedItem item : before.dockItems) {
            if (item instanceof PinnedFolderItem) {
                PinnedFolderItem folder = folders.get(((PinnedFolderItem) item).id);
                if (folder != null) dock.add(folder);
            } else if (item instanceof PinnedAppItem) {
                PinnedAppItem app = (PinnedAppItem) item;
                if (app.iconOverride != null && !validator.isAvailable(app.iconOverride)) {
                    dock.add(new PinnedAppItem(app.appRef.copy()));
                    changed = true;
                } else dock.add(cloneApp(app));
            }
        }
        JSONArray validOverrides = new JSONArray();
        JSONArray overrides = currentOverrides();
        for (int i = 0; i < overrides.length(); i++) {
            JSONObject item = overrides.optJSONObject(i);
            PinnedIconOverride override = item == null ? null
                : parseIconOverride(item.optJSONObject("iconOverride"));
            if (item != null && override != null && validator.isAvailable(override)) validOverrides.put(item);
            else changed = true;
        }
        if (!changed) return false;
        return persistAndPublish(dock, folders, validOverrides);
    }

    @NonNull
    public synchronized List<PinnedItem> migrateFromLegacyIfNeeded() {
        cached = null;
        return new ArrayList<>(migrateFromLegacySnapshot().dockItems);
    }

    @NonNull
    private LauncherConfigSnapshot migrateFromLegacySnapshot() {
        List<PinnedItem> dock = new ArrayList<>();
        String legacy = preferences.getLegacyDefaultButtons();
        if ("phone,bromite,whatsapp,telegram,spotify".equalsIgnoreCase(
            legacy == null ? "" : legacy.trim())) legacy = "";
        if (legacy != null && !legacy.trim().isEmpty()) {
            for (String part : legacy.split(",")) {
                String value = part.trim();
                if (!value.isEmpty()) dock.add(new PinnedAppItem(new AppRef(value, "")));
            }
        }
        LinkedHashMap<String, PinnedFolderItem> folders = new LinkedHashMap<>();
        persistAndPublish(dock, folders, new JSONArray());
        if (cached != null) return cached;
        return new LauncherConfigSnapshot(++revision, dock, folders, "[]");
    }

    private static final class Parsed {
        final List<PinnedItem> dockItems = new ArrayList<>();
        final LinkedHashMap<String, PinnedFolderItem> folders = new LinkedHashMap<>();
        boolean valid;
        boolean requiresRewrite;
    }

    @NonNull
    private Parsed parseRoot(@NonNull JSONObject root) {
        Parsed parsed = new Parsed();
        JSONArray items = root.optJSONArray("items");
        if (items == null) return parsed;
        int schema = root.optInt("schemaVersion", 1);
        Set<String> usedIds = new LinkedHashSet<>();
        if (schema >= SCHEMA_VERSION) {
            JSONArray folderArray = root.optJSONArray("folders");
            if (folderArray != null) {
                for (int i = 0; i < folderArray.length(); i++) {
                    JSONObject raw = folderArray.optJSONObject(i);
                    if (raw == null) continue;
                    String id = uniqueFolderId(raw.optString("id", ""), usedIds, i + 1);
                    if (!id.equals(raw.optString("id", ""))) parsed.requiresRewrite = true;
                    parsed.folders.put(id, folderFromJson(raw, id));
                }
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject raw = items.optJSONObject(i);
                if (raw == null) continue;
                String type = raw.optString("type", "");
                if ("app".equals(type)) addApp(parsed.dockItems, raw);
                else if ("folderRef".equals(type)) {
                    PinnedFolderItem folder = parsed.folders.get(raw.optString("folderId", ""));
                    if (folder != null) parsed.dockItems.add(folder);
                    else parsed.requiresRewrite = true;
                }
            }
        } else {
            for (int i = 0; i < items.length(); i++) {
                JSONObject raw = items.optJSONObject(i);
                if (raw == null) continue;
                String type = raw.optString("type", "");
                if ("app".equals(type)) addApp(parsed.dockItems, raw);
                else if ("folder".equals(type)) {
                    String id = uniqueFolderId(raw.optString("id", ""), usedIds, i + 1);
                    PinnedFolderItem folder = folderFromJson(raw, id);
                    parsed.folders.put(id, folder);
                    parsed.dockItems.add(folder);
                }
            }
            parsed.requiresRewrite = true;
        }
        parsed.valid = true; // An explicitly empty v5 dock is valid (drawer-only folders may exist).
        return parsed;
    }

    private static void addApp(@NonNull List<PinnedItem> out, @NonNull JSONObject raw) {
        AppRef ref = appRefFromJson(raw);
        if (!ref.packageName.isEmpty())
            out.add(new PinnedAppItem(ref, parseIconOverride(raw.optJSONObject("iconOverride"))));
    }

    @NonNull
    private static String uniqueFolderId(@Nullable String proposed, @NonNull Set<String> used,
                                         int sourcePosition) {
        String base = proposed == null ? "" : proposed.trim();
        if (base.isEmpty()) base = "folder-" + sourcePosition;
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) candidate = base + "-" + suffix++;
        used.add(candidate);
        return candidate;
    }

    @NonNull
    private static PinnedFolderItem folderFromJson(@NonNull JSONObject raw, @NonNull String id) {
        PinnedFolderItem folder = new PinnedFolderItem(id, raw.optString("title", "Folder"));
        folder.rows = clamp(raw.optInt("rows", PinnedFolderItem.DEFAULT_ROWS), 1,
            PinnedFolderItem.MAX_GRID);
        folder.cols = clamp(raw.has("columns") ? raw.optInt("columns")
            : raw.optInt("cols", PinnedFolderItem.DEFAULT_COLS), 1, PinnedFolderItem.MAX_GRID);
        folder.tintOverrideEnabled = raw.optBoolean("tintOverrideEnabled", false);
        folder.tintColor = raw.optInt("tintColor", 0xFF202020);
        JSONArray apps = raw.optJSONArray("apps");
        if (apps != null) {
            for (int i = 0; i < apps.length() && folder.apps.size() < PinnedFolderItem.MAX_APPS; i++) {
                JSONObject app = apps.optJSONObject(i);
                if (app == null) continue;
                AppRef ref = appRefFromJson(app);
                if (!ref.packageName.isEmpty()) folder.apps.add(new PinnedAppItem(ref,
                    parseIconOverride(app.optJSONObject("iconOverride"))));
            }
        }
        return folder;
    }

    private boolean persistAndPublish(@NonNull List<PinnedItem> dock,
                                      @NonNull LinkedHashMap<String, PinnedFolderItem> folders,
                                      @NonNull JSONArray overrides) {
        if (!write(dock, folders, overrides)) return false;
        Parsed parsed = new Parsed();
        parsed.dockItems.addAll(dock);
        parsed.folders.putAll(folders);
        parsed.valid = true;
        cached = snapshot(parsed, overrides);
        List<Listener> copy = new ArrayList<>(listeners);
        for (Listener listener : copy) listener.onLauncherConfigChanged(cached);
        return true;
    }

    private boolean write(@NonNull List<PinnedItem> dock,
                          @NonNull Map<String, PinnedFolderItem> folders,
                          @NonNull JSONArray overrides) {
        String previous = preferences.getPinnedItemsV2();
        int previousSchema = preferences.getPinnedItemsSchemaVersion();
        JSONObject root = new JSONObject();
        JSONArray items = new JSONArray();
        JSONArray folderArray = new JSONArray();
        try {
            for (PinnedItem item : dock) {
                if (item instanceof PinnedAppItem) {
                    JSONObject raw = new JSONObject();
                    raw.put("type", "app");
                    putApp(raw, (PinnedAppItem) item);
                    items.put(raw);
                } else if (item instanceof PinnedFolderItem) {
                    JSONObject ref = new JSONObject();
                    ref.put("type", "folderRef");
                    ref.put("folderId", ((PinnedFolderItem) item).id);
                    items.put(ref);
                }
            }
            for (PinnedFolderItem folder : folders.values()) folderArray.put(folderToJson(folder));
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("items", items);
            root.put("folders", folderArray);
            root.put("appIconOverrides", overrides);
            String encoded = root.toString(); // Build completely before touching persistent state.
            if (preferences.commitPinnedItems(encoded, SCHEMA_VERSION)) return true;
            preferences.commitPinnedItems(previous == null ? "" : previous, previousSchema);
            return false;
        } catch (JSONException | RuntimeException ignored) {
            try { preferences.commitPinnedItems(previous == null ? "" : previous, previousSchema); }
            catch (RuntimeException rollbackFailure) { /* Preserve the original failure result. */ }
            return false;
        }
    }

    @NonNull
    private LauncherConfigSnapshot snapshot(@NonNull Parsed parsed, @Nullable JSONArray overrides) {
        return new LauncherConfigSnapshot(++revision, parsed.dockItems, parsed.folders,
            overrides == null ? "[]" : overrides.toString());
    }

    @NonNull
    private JSONArray currentOverrides() {
        try {
            LauncherConfigSnapshot snapshot = cached;
            if (snapshot != null) return new JSONArray(snapshot.appIconOverridesJson);
            String raw = preferences.getPinnedItemsV2();
            if (raw == null || raw.trim().isEmpty()) return new JSONArray();
            JSONArray result = new JSONObject(raw).optJSONArray("appIconOverrides");
            return result == null ? new JSONArray() : result;
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    @NonNull
    private static JSONObject folderToJson(@NonNull PinnedFolderItem folder) throws JSONException {
        JSONObject raw = new JSONObject();
        raw.put("id", folder.id);
        raw.put("title", folder.title);
        raw.put("rows", clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID));
        raw.put("columns", clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID));
        raw.put("tintOverrideEnabled", folder.tintOverrideEnabled);
        raw.put("tintColor", folder.tintColor);
        JSONArray apps = new JSONArray();
        Set<String> unique = new LinkedHashSet<>();
        for (PinnedAppItem app : folder.apps) {
            if (apps.length() >= PinnedFolderItem.MAX_APPS) break;
            if (app == null || app.appRef.packageName.isEmpty()
                || !unique.add(app.appRef.stableId())) continue;
            JSONObject item = new JSONObject();
            putApp(item, app);
            apps.put(item);
        }
        raw.put("apps", apps);
        return raw;
    }

    private static void putApp(@NonNull JSONObject target, @NonNull PinnedAppItem app)
        throws JSONException {
        putAppRef(target, app.appRef);
        putIconOverrideIfValid(target, app.iconOverride);
    }

    private static void putAppRef(@NonNull JSONObject target, @NonNull AppRef ref)
        throws JSONException {
        target.put("packageName", ref.packageName);
        target.put("activityName", ref.activityName);
        if (ref.userId < 0 && ref.userSerialNumber < 0 && !ref.clonedProfile
            && (ref.profileLabel == null || ref.profileLabel.isEmpty())) return;
        target.put("userId", ref.userId);
        target.put("userSerialNumber", ref.userSerialNumber);
        target.put("clonedProfile", ref.clonedProfile);
        if (ref.profileLabel != null && !ref.profileLabel.isEmpty())
            target.put("profileLabel", ref.profileLabel);
    }

    @NonNull
    private static AppRef appRefFromJson(@NonNull JSONObject item) {
        return new AppRef(item.optString("packageName", ""), item.optString("activityName", ""),
            item.optInt("userId", -1), item.optLong("userSerialNumber", -1L),
            item.optBoolean("clonedProfile", false), item.optString("profileLabel", ""));
    }

    @Nullable
    private static PinnedIconOverride parseIconOverride(@Nullable JSONObject raw) {
        if (raw == null) return null;
        PinnedIconOverride override = new PinnedIconOverride(raw.optString("sourceType", ""),
            raw.optString("iconPackPackage", ""), raw.optString("drawableName", ""),
            raw.optString("displayLabel", ""));
        return override.isValid() ? override : null;
    }

    private static void putIconOverrideIfValid(@NonNull JSONObject target,
                                                @Nullable PinnedIconOverride iconOverride)
        throws JSONException {
        if (iconOverride == null || !iconOverride.isValid()) return;
        JSONObject override = new JSONObject();
        override.put("sourceType", iconOverride.sourceType);
        override.put("iconPackPackage", iconOverride.iconPackPackage);
        override.put("drawableName", iconOverride.drawableName);
        override.put("displayLabel", iconOverride.displayLabel);
        target.put("iconOverride", override);
    }

    @NonNull
    private static LinkedHashMap<String, PinnedFolderItem> cloneFolders(
        @NonNull Map<String, PinnedFolderItem> source) {
        LinkedHashMap<String, PinnedFolderItem> result = new LinkedHashMap<>();
        for (PinnedFolderItem folder : source.values()) {
            PinnedFolderItem copy = cloneFolder(folder);
            result.put(copy.id, copy);
        }
        return result;
    }

    @NonNull
    private static List<PinnedItem> cloneDock(@NonNull List<PinnedItem> source,
                                              @NonNull Map<String, PinnedFolderItem> folders) {
        List<PinnedItem> result = new ArrayList<>();
        for (PinnedItem item : source) {
            if (item instanceof PinnedAppItem) result.add(cloneApp((PinnedAppItem) item));
            else if (item instanceof PinnedFolderItem) {
                PinnedFolderItem folder = folders.get(((PinnedFolderItem) item).id);
                if (folder != null) result.add(folder);
            }
        }
        return result;
    }

    @NonNull
    private static PinnedFolderItem cloneFolder(@NonNull PinnedFolderItem source) {
        PinnedFolderItem copy = new PinnedFolderItem(source.id, source.title);
        copy.rows = source.rows;
        copy.cols = source.cols;
        copy.tintOverrideEnabled = source.tintOverrideEnabled;
        copy.tintColor = source.tintColor;
        for (PinnedAppItem app : source.apps) copy.apps.add(cloneApp(app));
        return copy;
    }

    @NonNull
    private static PinnedAppItem cloneApp(@NonNull PinnedAppItem source) {
        return new PinnedAppItem(source.appRef.copy(), source.iconOverride);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
