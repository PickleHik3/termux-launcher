package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The services with a free plan that Settings › AI offers for "Use a free online assistant", read
 * from data ({@link #ASSET}) so the list and model ids can change without code: the first is the
 * one offered first (Groq), the last carries a warning (Google). A chosen service becomes the
 * remote provider every AI request already reaches through (ADR 0027): its address, its model and
 * the user's own key, which {@link TaiRemoteSettings} keeps encrypted on the phone. The data never
 * holds a key.
 */
public final class FreeAssistantProviders {

    /** The shipped list. */
    public static final String ASSET = "ai/free-assistant-providers.json";

    /** One service. */
    public static final class Provider {
        @NonNull public final String id;
        /** The service's own name; not translated. */
        @NonNull public final String name;
        /** The OpenAI-compatible address, without a trailing slash. */
        @NonNull public final String baseUrl;
        @NonNull public final String model;
        /** Where the user signs up and makes a key. */
        @NonNull public final String keyPage;
        /** One line about the free plan, shown under its name. */
        @NonNull public final String note;
        /** The note is a warning (what is sent may be read, or the plan is not allowed everywhere). */
        public final boolean warning;

        Provider(@NonNull String id, @NonNull String name, @NonNull String baseUrl,
                 @NonNull String model, @NonNull String keyPage, @NonNull String note, boolean warning) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.model = model;
            this.keyPage = keyPage;
            this.note = note;
            this.warning = warning;
        }
    }

    private FreeAssistantProviders() {}

    /**
     * The services in {@code json}, in order. A service missing a name, an https address, a model
     * or a key page is left out; text that is not the list's form reads as none.
     */
    @NonNull
    public static List<Provider> parse(@Nullable String json) {
        if (json == null) return Collections.emptyList();
        List<Provider> out = new ArrayList<>();
        try {
            JSONArray list = new JSONObject(json).optJSONArray("providers");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim();
                String name = item.optString("name", "").trim();
                String baseUrl = TaiRemoteClient.normalizeBaseUrl(item.optString("baseUrl", ""));
                String model = item.optString("model", "").trim();
                String keyPage = item.optString("keyPage", "").trim();
                if (id.isEmpty() || name.isEmpty() || model.isEmpty()
                    || !baseUrl.startsWith("https://") || !keyPage.startsWith("https://")) continue;
                out.add(new Provider(id, name, baseUrl, model, keyPage,
                    item.optString("note", "").trim(), item.optBoolean("warning", false)));
            }
        } catch (JSONException e) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(out);
    }

    /** The shipped list; empty when the asset cannot be read. */
    @NonNull
    public static List<Provider> load(@NonNull Context context) {
        try (InputStream in = context.getAssets().open(ASSET)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) bytes.write(buffer, 0, read);
            return parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * The remote provider set up now that saving {@code provider} would replace (its name, or its
     * address), or null when there is none to ask about: nothing set up, or this same service.
     */
    @Nullable
    public static String replaces(@NonNull List<Provider> providers, @NonNull TaiRemoteSettings settings,
                                  @NonNull Provider provider) {
        if (!settings.isConfigured()) return null;
        Provider current = current(providers, settings);
        if (provider.equals(current)) return null;
        if (current != null) return current.name;
        TaiRemotePresets.Preset preset = settings.preset();
        return preset != null && !preset.isCustom() ? preset.displayName : settings.baseUrl();
    }

    /** Why a Test failed, in the words the page uses. */
    public enum Failure {
        /** The service refused the key (401, 403). */
        BAD_KEY,
        /** The free plan's limit is reached for now (429). */
        LIMIT,
        /** The service could not be reached, or did not answer in time. */
        OFFLINE,
        /** Anything else. */
        OTHER
    }

    /** What a failed Test says, from its status and code. */
    @NonNull
    public static Failure failureOf(@NonNull TaiRemoteClient.TestResult result) {
        if (result.status == 401 || result.status == 403) return Failure.BAD_KEY;
        if (result.status == 429) return Failure.LIMIT;
        if ("remote_unreachable".equals(result.code) || "remote_timeout".equals(result.code))
            return Failure.OFFLINE;
        return Failure.OTHER;
    }

    /** The service the remote provider is set to now (by its address), or null for none of these. */
    @Nullable
    public static Provider current(@NonNull List<Provider> providers, @NonNull TaiRemoteSettings settings) {
        String baseUrl = settings.baseUrl();
        if (baseUrl.isEmpty()) return null;
        for (Provider provider : providers) {
            if (provider.baseUrl.equalsIgnoreCase(baseUrl)) return provider;
        }
        return null;
    }

    /**
     * Makes {@code provider} the remote provider with the user's {@code key}: its address (as the
     * remote model screen's preset when it has one, else as a typed address), its model and the
     * key. Another provider's key is forgotten first, as the remote model screen forgets it, so a
     * key is never sent to a service it was not made for. False when the key could not be stored.
     */
    public static boolean save(@NonNull TaiRemoteSettings settings, @NonNull Provider provider,
                               @NonNull String key) {
        // Another server's key goes first, whatever follows: Custom to Custom keeps the key in
        // the remote model screen's rules, and a refused new key must not leave the old one
        // beside the new address.
        if (!provider.baseUrl.equalsIgnoreCase(settings.baseUrl())) settings.setApiKey(null);
        TaiRemotePresets.Preset preset = TaiRemotePresets.forBaseUrl(provider.baseUrl);
        if (preset != null && !preset.isCustom()) {
            settings.choosePreset(preset);
        } else {
            settings.saveTypedBaseUrl(provider.baseUrl);
        }
        settings.setModelId(provider.model);
        return settings.setApiKey(key);
    }
}
