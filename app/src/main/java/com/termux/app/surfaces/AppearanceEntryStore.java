package com.termux.app.surfaces;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Appearance editor's entry state, kept on disk for as long as a session is open, so a
 * launcher killed before Done (a force-stop, a crash, the system reclaiming it) does not keep the
 * session's live values: the next start puts the entry state back before any chrome reads it.
 *
 * <p>The editor writes it when a session opens and removes it when the session ends (Done,
 * Discard, or a close with nothing to lose). A leftover at start therefore means the process died
 * mid-session, unless a configuration relaunch is carrying that session on, which the caller says.
 */
public final class AppearanceEntryStore {

    private static final String LOG_TAG = "AppearanceEntryStore";
    /** In the launcher's own preferences, beside the values it puts back. */
    static final String PREFS_KEY = "appearance_entry_snapshot_v1";

    private static final String JSON_LOOK = "look";
    private static final String JSON_SETTINGS = "settings";
    private static final String JSON_CUSTOM = "custom";
    /** Absent in a copy an older build wrote; JSON null when no card look was saved at open. */
    private static final String JSON_CARD_CUSTOMS = "cardCustoms";

    private AppearanceEntryStore() {}

    /** A session opened on {@code look}, {@code settings}, the saved Custom and the card looks it found. */
    static void save(@Nullable TermuxAppSharedPreferences prefs, @NonNull AppearanceSnapshot look,
                     @NonNull EditorSettingsSnapshot settings, @Nullable String custom,
                     @Nullable String cardCustoms) {
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        if (store == null)
            return;
        try {
            JSONObject out = new JSONObject()
                .put(JSON_LOOK, look.toJson())
                .put(JSON_SETTINGS, settings.toJson())
                .put(JSON_CUSTOM, custom == null ? JSONObject.NULL : custom)
                .put(JSON_CARD_CUSTOMS, cardCustoms == null ? JSONObject.NULL : cardCustoms);
            store.edit().putString(PREFS_KEY, out.toString()).apply();
        } catch (JSONException e) {
            Logger.logWarn(LOG_TAG, "entry state not saved: " + e.getMessage());
        }
    }

    /** The session ended: nothing to put back at the next start. */
    static void clear(@Nullable TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        if (store != null && store.contains(PREFS_KEY))
            store.edit().remove(PREFS_KEY).apply();
    }

    static boolean hasLeftover(@Nullable TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        return store != null && store.contains(PREFS_KEY);
    }

    /**
     * At start: puts back the entry state a killed session left, then forgets it. Does nothing
     * while {@code editorReopening}, a relaunch carrying the open session on. True when it restored.
     */
    public static boolean restoreLeftover(@NonNull Context context,
                                          @Nullable TermuxAppSharedPreferences prefs,
                                          boolean editorReopening) {
        if (prefs == null || editorReopening || !hasLeftover(prefs))
            return false;
        return restoreLeftover(prefs,
            EditorSettingsSnapshot.settings(context.getApplicationContext(),
                prefs.getSharedPreferences()));
    }

    /** {@link #restoreLeftover(Context, TermuxAppSharedPreferences, boolean)} with its store. */
    static boolean restoreLeftover(@NonNull TermuxAppSharedPreferences prefs,
                                   @NonNull EditorSettingsSnapshot.Store settingsStore) {
        SharedPreferences store = prefs.getSharedPreferences();
        String json = store == null ? null : store.getString(PREFS_KEY, null);
        if (json == null)
            return false;
        // Forgotten first: a state that cannot be read, or one whose restore throws, is not
        // retried on every start.
        clear(prefs);
        try {
            JSONObject in = new JSONObject(json);
            AppearanceSnapshot look = AppearanceSnapshot.fromJson(in.getJSONObject(JSON_LOOK));
            EditorSettingsSnapshot settings =
                EditorSettingsSnapshot.fromJson(in.getJSONObject(JSON_SETTINGS));
            String custom = in.isNull(JSON_CUSTOM) ? null : in.getString(JSON_CUSTOM);
            look.restore(prefs);
            if (in.has(JSON_CARD_CUSTOMS))
                CardCustoms.restore(store, in.isNull(JSON_CARD_CUSTOMS) ? null
                    : in.getString(JSON_CARD_CUSTOMS));
            if (custom != null)
                prefs.setSurfaceCustomPreset(custom);
            settings.restore(settingsStore);
            Logger.logInfo(LOG_TAG,
                "Appearance editor was left without Done: its entry state is put back");
            return true;
        } catch (JSONException e) {
            Logger.logWarn(LOG_TAG, "entry state unreadable, dropped: " + e.getMessage());
            return false;
        }
    }
}
