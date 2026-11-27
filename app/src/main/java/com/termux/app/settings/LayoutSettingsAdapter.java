package com.termux.app.settings;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.IconSize;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.dock.WallStripRowPolicy;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The layout documents as registry ids: {@code layout.<document>.<field>} for a field at the top
 * of a document ({@code layout.home.search_field}, {@code layout.sizes.icon_size}) and {@code
 * layout.<document>.<portrait|landscape>.<field>} for one of an orientation's
 * ({@code layout.home.portrait.status_bar}). A save changes one field of the document as it
 * stands and hands the whole document to {@link LayoutCommand#set}, so the validation, the field
 * named in a refusal and the one write are the {@code layout} verb's. The store asserts the main
 * thread, so the adapter moves every read and save there itself: a caller on the HTTP thread, the
 * Assistant or restore posts nothing. The chrome re-dress follows a save.
 */
public final class LayoutSettingsAdapter implements SettingAdapter {

    /** Every layout registry id starts with this; the upgrade names the family by it. */
    public static final String PREFIX = "layout.";

    /** Where the layout store may be touched. */
    public interface Main {
        /** Runs {@code work} on the main thread and waits for it. */
        <T> T call(@NonNull Callable<T> work) throws Exception;
    }

    /** How long a caller off the main thread waits for it before telling its caller no. */
    static final long MAIN_TIMEOUT_SECONDS = 5;

    /** What a caller is told when the main thread did not run its work. */
    public static final String NOT_ANSWERED = "the launcher did not answer; try again";

    /** The device's main thread: the work runs in place when already there, else is posted. */
    public static final Main DEVICE = new Main() {
        @Nullable private Handler mHandler;

        @Override
        public <T> T call(@NonNull Callable<T> work) throws Exception {
            if (Looper.myLooper() == Looper.getMainLooper()) return work.call();
            FutureTask<T> task = new FutureTask<>(work);
            handler().post(task);
            return await(task, MAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @NonNull
        private synchronized Handler handler() {
            if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
            return mHandler;
        }
    };

    /**
     * Waits for a posted task. On a timeout the task is cancelled, so a save the main thread has
     * not started never lands after the caller was told it failed; a failure inside the work comes
     * out as the work's own exception, not the wrapper's.
     */
    static <T> T await(@NonNull FutureTask<T> task, long timeout, @NonNull TimeUnit unit)
            throws Exception {
        try {
            return task.get(timeout, unit);
        } catch (TimeoutException e) {
            task.cancel(false);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
    }

    @NonNull private final LayoutStore mStore;
    @NonNull private final Main mMain;
    @NonNull private final Runnable mRedress;

    LayoutSettingsAdapter(@NonNull LayoutStore store, @NonNull Main main,
                          @NonNull Runnable redress) {
        mStore = store;
        mMain = main;
        mRedress = redress;
    }

    /** An id's document and the path of the field inside it. */
    private static final class Target {
        @NonNull final String document;
        @NonNull final String path;

        Target(@NonNull String document, @NonNull String path) {
            this.document = document;
            this.path = path;
        }
    }

    @Nullable
    private static Target parse(@NonNull String id) {
        if (!id.startsWith(PREFIX)) return null;
        String rest = id.substring(PREFIX.length());
        int dot = rest.indexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return null;
        String document = rest.substring(0, dot);
        return LayoutCommand.isName(document) ? new Target(document, rest.substring(dot + 1)) : null;
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        Target target = parse(id);
        return target == null ? null : describe(
            SettingDeclaration.text(id, "", SettingRefresh.LISTENER), target);
    }

    /**
     * The name and sentence the settings reference page shows, and the range in words: a size's
     * limits are scales and dp, set per orientation, not numbers a reader could type blind.
     */
    @NonNull
    private static SettingDeclaration describe(@NonNull SettingDeclaration declaration,
                                               @NonNull Target target) {
        if (!LayoutCommand.SIZES.equals(target.document)) return declaration;
        String orientation = target.path.startsWith(LayoutsJson.LANDSCAPE + ".") ? " in landscape"
            : target.path.startsWith(LayoutsJson.PORTRAIT + ".") ? " in portrait" : "";
        String field = target.path.substring(target.path.lastIndexOf('.') + 1);
        switch (field) {
            case IconSize.FIELD:
                return declaration.described("Icon size", "How large app icons are on Home.")
                    .shownAs("s, m, l or xl", IconSize.DEFAULT.key);
            case LayoutsJson.DOCK_HEIGHT:
                return declaration.described("Dock height" + orientation,
                    "How tall the dock is, as a multiple of the standard height.")
                    .shownAs(TERMUX_APP.MIN_APP_LAUNCHER_BAR_HEIGHT + " to "
                        + TERMUX_APP.MAX_APP_LAUNCHER_BAR_HEIGHT,
                        String.valueOf(TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT));
            case LayoutsJson.KEYBOARD_HEIGHT:
                return declaration.described("Keyboard height" + orientation,
                    "How tall the in-app keyboard is, as a multiple of the standard height.")
                    .shownAs(TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE + " to "
                        + TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE, "The standard height");
            case LayoutsJson.KEYBOARD_CHIN:
                return declaration.described("Keyboard bottom space" + orientation,
                    "The space under the in-app keyboard, in dp.")
                    .shownAs(TERMUX_APP.MIN_IN_APP_KEYBOARD_BOTTOM_PADDING + " to "
                        + TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING,
                        String.valueOf(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING));
            case LayoutsJson.WALL_STRIP_HEIGHT:
                return declaration.described("Wall strip height" + orientation,
                    "How tall the strip of wall controls is, in dp.")
                    .shownAs(WallStripRowPolicy.MIN_HEIGHT_DP + " to "
                        + WallStripRowPolicy.MAX_HEIGHT_DP,
                        String.valueOf(WallStripRowPolicy.DEFAULT_HEIGHT_DP));
            default:
                return declaration;
        }
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        List<SettingDeclaration> all = new ArrayList<>();
        try {
            List<String> names = new ArrayList<>();
            for (LayoutKind kind : LayoutKind.values()) if (!kind.retired()) names.add(kind.key);
            names.add(LayoutCommand.SIZES);
            Map<String, Map<String, String>> fields = mMain.call(() -> {
                Map<String, Map<String, String>> byName = new LinkedHashMap<>();
                for (String name : names) {
                    String json = LayoutCommand.get(mStore, name);
                    if (json == null) continue;
                    Map<String, String> flat = new LinkedHashMap<>();
                    flatten(new JSONObject(json), "", flat);
                    if (LayoutCommand.SIZES.equals(name)) {
                        // An absent size reads its default; it is still a setting to list.
                        flat.putIfAbsent(IconSize.FIELD, "");
                        for (String orientation : new String[]{LayoutsJson.PORTRAIT,
                                LayoutsJson.LANDSCAPE}) {
                            for (String field : LayoutsJson.SIZE_FIELDS)
                                flat.putIfAbsent(orientation + "." + field, "");
                        }
                    }
                    byName.put(name, flat);
                }
                return byName;
            });
            for (Map.Entry<String, Map<String, String>> document : fields.entrySet()) {
                for (String path : document.getValue().keySet()) {
                    all.add(describe(SettingDeclaration.text(
                        PREFIX + document.getKey() + "." + path, "", SettingRefresh.LISTENER),
                        new Target(document.getKey(), path)));
                }
            }
        } catch (Exception e) {
            // A store that does not answer lists nothing; a get or a set says why.
        }
        return all;
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null) return "";
        try {
            String value = mMain.call(() -> read(target));
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    /** A field the document does not hold yet (an absent size) reads its default and is not exported. */
    @Override
    public boolean overrides(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null) return true;
        try {
            return mMain.call(() -> read(target)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        Target target = parse(declaration.id);
        if (target == null) return SettingsRegistry.unknown(declaration.id);
        try {
            return mMain.call(() -> save(declaration, target, value.trim()));
        } catch (Exception e) {
            return new SettingsRegistry.Refusal(NOT_ANSWERED, null);
        }
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        Target target = parse(declaration.id);
        if (target == null) return SettingsRegistry.unknown(declaration.id);
        try {
            return mMain.call(() -> candidate(target, value.trim()).refusal);
        } catch (Exception e) {
            return new SettingsRegistry.Refusal(NOT_ANSWERED, null);
        }
    }

    /**
     * {@code default}: the stored field is removed, so the layout falls back to what it holds
     * when nothing is stored. The default value reads empty here, never a value to type.
     */
    @NonNull
    @Override
    public SettingsRegistry.Outcome reset(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null) return SettingsRegistry.unknown(declaration.id);
        try {
            return mMain.call(() -> saveRemoved(declaration, target));
        } catch (Exception e) {
            return new SettingsRegistry.Refusal(NOT_ANSWERED, null);
        }
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal checkReset(@NonNull SettingDeclaration declaration) {
        Target target = parse(declaration.id);
        if (target == null) return SettingsRegistry.unknown(declaration.id);
        try {
            return mMain.call(() -> candidate(target, null).refusal);
        } catch (Exception e) {
            return new SettingsRegistry.Refusal(NOT_ANSWERED, null);
        }
    }

    /** The document with one field replaced, or why it may not be. */
    private static final class Candidate {
        @Nullable final SettingsRegistry.Refusal refusal;
        @Nullable final String json;
        @NonNull final String old;

        Candidate(@Nullable SettingsRegistry.Refusal refusal, @Nullable String json,
                  @NonNull String old) {
            this.refusal = refusal;
            this.json = json;
            this.old = old;
        }
    }

    /** Main thread: one field replaced in the document as it stands, run through the verb's validation. */
    @NonNull
    private Candidate candidate(@NonNull Target target, @Nullable String text) throws JSONException {
        String json = LayoutCommand.get(mStore, target.document);
        if (json == null)
            return new Candidate(new SettingsRegistry.Refusal("the layouts are not ready", null),
                null, "");
        JSONObject root = new JSONObject(json);
        String[] place = place(target.path);
        JSONObject section = section(root, place);
        Object existing = field(section, place);
        if (existing == null && !known(place[1]))
            return new Candidate(new SettingsRegistry.Refusal(target.document + " has no field \""
                + target.path + "\"; layout get " + target.document + " shows them", null), null, "");
        if (section == null) {
            section = new JSONObject();
            root.put(place[0], section);
        }
        if (text == null) section.remove(place[1]);
        else section.put(place[1], typed(existing, text));
        String refused = LayoutCommand.refusal(target.document, root.toString());
        return new Candidate(refused == null ? null : new SettingsRegistry.Refusal(refused, null),
            root.toString(), existing == null ? "" : String.valueOf(existing));
    }

    /** Main thread: the candidate saved through the verb's own set, then the re-dress. */
    @NonNull
    private SettingsRegistry.Outcome save(@NonNull SettingDeclaration declaration,
                                          @NonNull Target target, @NonNull String text)
            throws JSONException {
        Candidate candidate = candidate(target, text);
        if (candidate.refusal != null || candidate.json == null)
            return candidate.refusal != null ? candidate.refusal
                : new SettingsRegistry.Refusal("the layouts are not ready", null);
        String refused = LayoutCommand.set(mStore, target.document, candidate.json);
        if (refused != null) return new SettingsRegistry.Refusal(refused, null);
        mRedress.run();
        String now = read(target);
        return new SettingsRegistry.Change(declaration, candidate.old, now == null ? text : now);
    }

    /** Main thread: the field removed from the document, saved through the verb's own set. */
    @NonNull
    private SettingsRegistry.Outcome saveRemoved(@NonNull SettingDeclaration declaration,
                                                 @NonNull Target target) throws JSONException {
        Candidate candidate = candidate(target, null);
        if (candidate.refusal != null || candidate.json == null)
            return candidate.refusal != null ? candidate.refusal
                : new SettingsRegistry.Refusal("the layouts are not ready", null);
        String refused = LayoutCommand.set(mStore, target.document, candidate.json);
        if (refused != null) return new SettingsRegistry.Refusal(refused, null);
        mRedress.run();
        String now = read(target);
        return new SettingsRegistry.Change(declaration, candidate.old, now == null ? "" : now);
    }

    /** Main thread: the field's current value as text, or null when the document lacks it. */
    @Nullable
    private String read(@NonNull Target target) throws JSONException {
        String json = LayoutCommand.get(mStore, target.document);
        if (json == null) return null;
        String[] place = place(target.path);
        Object value = field(section(new JSONObject(json), place), place);
        return value == null ? null : String.valueOf(value);
    }

    /** The object holding the field {@code place} names: the document, or its orientation. */
    @Nullable
    private static JSONObject section(@NonNull JSONObject root, @NonNull String[] place) {
        return place[0].isEmpty() ? root : root.optJSONObject(place[0]);
    }

    /** The field {@code place} names inside {@code section}, or null when it is not held. */
    @Nullable
    private static Object field(@Nullable JSONObject section, @NonNull String[] place) {
        return section == null ? null : section.opt(place[1]);
    }

    /** {@code portrait.status_bar} as {portrait, status_bar}; {@code search_field} as {"", it}. */
    @NonNull
    private static String[] place(@NonNull String path) {
        for (String orientation : new String[]{LayoutsJson.PORTRAIT, LayoutsJson.LANDSCAPE}) {
            if (path.startsWith(orientation + ".") && path.length() > orientation.length() + 1)
                return new String[]{orientation, path.substring(orientation.length() + 1)};
        }
        return new String[]{"", path};
    }

    /** Whether this build names the field, so a typo is not saved as an unknown field. */
    private static boolean known(@NonNull String key) {
        return LayoutsJson.WIDE_FIELDS.contains(key) || LayoutsJson.ORIENTATION_FIELDS.contains(key)
            || LayoutsJson.SIZE_FIELDS.contains(key) || LayoutsJson.GRID_FIELDS.contains(key)
            || key.startsWith(LayoutsJson.DRESS_PREFIX) || IconSize.FIELD.equals(key)
            || LayoutsJson.VERSION.equals(key);
    }

    /**
     * {@code text} in the type the field already holds; a field not yet held takes the type the
     * text reads as. A text that does not fit the type stays text, and the validator says so.
     */
    @NonNull
    private static Object typed(@Nullable Object existing, @NonNull String text) {
        if (existing instanceof String) return text;
        boolean bool = existing instanceof Boolean;
        boolean number = existing instanceof Number;
        if ((existing == null || bool) && (text.equals("true") || text.equals("false")))
            return Boolean.valueOf(text);
        if (existing == null || number) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                // Not whole: try a decimal below.
            }
            try {
                double decimal = Double.parseDouble(text);
                if (!Double.isNaN(decimal) && !Double.isInfinite(decimal)) return decimal;
            } catch (NumberFormatException ignored) {
                // Stays text.
            }
        }
        return text;
    }

    private static void flatten(@NonNull JSONObject object, @NonNull String prefix,
                                @NonNull Map<String, String> into) throws JSONException {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.get(key);
            if (value instanceof JSONObject) {
                if (prefix.isEmpty()) flatten((JSONObject) value, key + ".", into);
            } else if (!LayoutsJson.VERSION.equals(key) && !(value instanceof org.json.JSONArray)) {
                into.put(prefix + key, String.valueOf(value));
            }
        }
    }
}
