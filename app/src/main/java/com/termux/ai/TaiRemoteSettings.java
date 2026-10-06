package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The remote provider's settings: base URL, model, image capability, routing and the one-time
 * consent flag in the {@code termux_ai} prefs under {@code tai_remote_*} keys, and the API key in
 * {@link TaiSecretStore}. Owned here rather than in {@link TaiSettings}; which features use the
 * remote model is decided by the per-function picker (device-tiers spec §4), not by this class.
 */
public final class TaiRemoteSettings {
    public static final String KEY_BASE_URL = "tai_remote_base_url";
    public static final String KEY_MODEL = "tai_remote_model";
    /** The probe's answer; absent until a probe finished. */
    public static final String KEY_IMAGES_PROBED = "tai_remote_images_probed";
    /** The user's own answer, which wins over the probe while present. */
    public static final String KEY_IMAGES_OVERRIDE = "tai_remote_images_override";
    public static final String KEY_ROUTING = "tai_remote_routing";
    public static final String KEY_CONSENT_SHOWN = "tai_remote_consent_shown";
    /** The {@link TaiSecretStore} name of the API key. */
    public static final String SECRET_API_KEY = "remote_api_key";

    /** Ask the remote model first; the phone only when it is unreachable. The default (decision 3). */
    public static final String ROUTING_PREFER_REMOTE = "prefer_remote";
    /** Use the phone whenever a local model fits; the remote model is the fallback. */
    public static final String ROUTING_LOCAL_FIRST = "local_first";

    private final SharedPreferences prefs;
    private final TaiSecretStore secrets;

    public TaiRemoteSettings(@NonNull Context context) {
        this(context.getApplicationContext().getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE),
            new TaiSecretStore(context));
    }

    TaiRemoteSettings(@NonNull SharedPreferences prefs, @NonNull TaiSecretStore secrets) {
        this.prefs = prefs;
        this.secrets = secrets;
    }

    /** A usable server address and a model; the key is optional (local servers need none). */
    public boolean isConfigured() {
        return TaiRemoteClient.checkUrl(baseUrl()).allowed() && !modelId().isEmpty();
    }

    @NonNull
    public String baseUrl() {
        return TaiRemoteClient.normalizeBaseUrl(prefs.getString(KEY_BASE_URL, ""));
    }

    public void setBaseUrl(@Nullable String url) {
        String value = TaiRemoteClient.normalizeBaseUrl(url);
        if (value.equals(baseUrl())) return;
        // Another server may serve another model under the same id; the probe no longer holds.
        prefs.edit().putString(KEY_BASE_URL, value).remove(KEY_IMAGES_PROBED).apply();
    }

    @NonNull
    public String modelId() {
        String value = prefs.getString(KEY_MODEL, "");
        return value == null ? "" : value.trim();
    }

    /** A new model forgets the probe and the user's image override, which were about the old one. */
    public void setModelId(@Nullable String model) {
        String value = model == null ? "" : model.trim();
        if (value.equals(modelId())) return;
        prefs.edit().putString(KEY_MODEL, value).remove(KEY_IMAGES_PROBED).remove(KEY_IMAGES_OVERRIDE).apply();
    }

    /** The API key, or {@code null} when none is set or it could not be decrypted. */
    @Nullable
    public String apiKey() {
        return secrets.get(SECRET_API_KEY);
    }

    public boolean hasApiKey() {
        return secrets.has(SECRET_API_KEY);
    }

    /** Stores the key encrypted; a blank key clears it. False when the keystore refused. */
    public boolean setApiKey(@Nullable String key) {
        return secrets.put(SECRET_API_KEY, key);
    }

    /** The user's override when set, else the probe's answer, else false. */
    public boolean understandsImages() {
        if (prefs.contains(KEY_IMAGES_OVERRIDE)) return prefs.getBoolean(KEY_IMAGES_OVERRIDE, false);
        return prefs.getBoolean(KEY_IMAGES_PROBED, false);
    }

    /** The probe's answer, or {@code null} before a probe finished. */
    @Nullable
    public Boolean probedImages() {
        return prefs.contains(KEY_IMAGES_PROBED) ? prefs.getBoolean(KEY_IMAGES_PROBED, false) : null;
    }

    public void setProbedImages(@Nullable Boolean understands) {
        if (understands == null) prefs.edit().remove(KEY_IMAGES_PROBED).apply();
        else prefs.edit().putBoolean(KEY_IMAGES_PROBED, understands).apply();
    }

    public boolean imagesOverridden() {
        return prefs.contains(KEY_IMAGES_OVERRIDE);
    }

    /** {@code null} clears the override, so the probe decides again. */
    public void setImagesOverride(@Nullable Boolean understands) {
        if (understands == null) prefs.edit().remove(KEY_IMAGES_OVERRIDE).apply();
        else prefs.edit().putBoolean(KEY_IMAGES_OVERRIDE, understands).apply();
    }

    @NonNull
    public String routing() {
        return normalizeRouting(prefs.getString(KEY_ROUTING, ROUTING_PREFER_REMOTE));
    }

    public void setRouting(@Nullable String routing) {
        prefs.edit().putString(KEY_ROUTING, normalizeRouting(routing)).apply();
    }

    public boolean prefersRemote() {
        return ROUTING_PREFER_REMOTE.equals(routing());
    }

    @NonNull
    static String normalizeRouting(@Nullable String routing) {
        return ROUTING_LOCAL_FIRST.equals(routing) ? ROUTING_LOCAL_FIRST : ROUTING_PREFER_REMOTE;
    }

    /** Whether the one-time "your photo is sent to …" dialog was shown. */
    public boolean consentShown() {
        return prefs.getBoolean(KEY_CONSENT_SHOWN, false);
    }

    public void setConsentShown(boolean shown) {
        prefs.edit().putBoolean(KEY_CONSENT_SHOWN, shown).apply();
    }

    /** Settings → Remove: the key and every remote setting go. */
    public void clearAll() {
        secrets.clear(SECRET_API_KEY);
        prefs.edit()
            .remove(KEY_BASE_URL)
            .remove(KEY_MODEL)
            .remove(KEY_IMAGES_PROBED)
            .remove(KEY_IMAGES_OVERRIDE)
            .remove(KEY_ROUTING)
            .remove(KEY_CONSENT_SHOWN)
            .apply();
    }
}
