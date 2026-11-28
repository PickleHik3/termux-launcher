package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** The free online assistant's services: the shipped data, and choosing one with a key. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class FreeAssistantProvidersTest {

    private TaiRemoteSettings settings;
    private final PlainKeys keys = new PlainKeys();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        settings = new TaiRemoteSettings(prefs, new TaiSecretStore(keys, new MapStorage()));
    }

    private static String shipped() throws Exception {
        File asset = new File("src/main/assets/" + FreeAssistantProviders.ASSET);
        if (!asset.isFile()) asset = new File("app/src/main/assets/" + FreeAssistantProviders.ASSET);
        return new String(Files.readAllBytes(asset.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void theShippedListOffersGroqFirstAndGoogleLastWithItsWarning() throws Exception {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse(shipped());
        assertEquals(4, providers.size());
        assertEquals("groq", providers.get(0).id);
        assertEquals("openrouter", providers.get(1).id);
        assertEquals("nvidia", providers.get(2).id);
        FreeAssistantProviders.Provider google = providers.get(3);
        assertEquals("google", google.id);
        assertTrue(google.warning);
        for (int i = 0; i < 3; i++) assertFalse(providers.get(i).id, providers.get(i).warning);
        // OpenRouter is used only through its free router, which bills nothing.
        assertEquals("openrouter/free", providers.get(1).model);
    }

    @Test
    public void theShippedListNeverHoldsAKey() throws Exception {
        String text = shipped().toLowerCase(java.util.Locale.ROOT);
        // No field that could carry one.
        assertFalse(Pattern.compile("\"(api_?key|key|token|secret)\"\\s*:").matcher(text).find());
        // No key-shaped strings: Groq's gsk_, OpenRouter's sk-or-, NVIDIA's nvapi-, Google's AIza.
        assertFalse(Pattern.compile("gsk_|sk-or-|nvapi-|aiza").matcher(text).find());
    }

    @Test
    public void aServiceWithoutAnHttpsAddressOrAModelIsLeftOut() {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse("{\"providers\":["
            + "{\"id\":\"a\",\"name\":\"A\",\"baseUrl\":\"http://a/v1\",\"model\":\"m\",\"keyPage\":\"https://a\"},"
            + "{\"id\":\"b\",\"name\":\"B\",\"baseUrl\":\"https://b/v1/\",\"model\":\"\",\"keyPage\":\"https://b\"},"
            + "{\"id\":\"c\",\"name\":\"C\",\"baseUrl\":\"https://c/v1/\",\"model\":\"m\",\"keyPage\":\"https://c\"}]}");
        assertEquals(1, providers.size());
        assertEquals("https://c/v1", providers.get(0).baseUrl);
        assertTrue(FreeAssistantProviders.parse("not json").isEmpty());
        assertTrue(FreeAssistantProviders.parse(null).isEmpty());
    }

    @Test
    public void savingAServiceMakesItTheRemoteProviderTheAssistantUses() throws Exception {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse(shipped());
        FreeAssistantProviders.Provider groq = providers.get(0);
        assertNull(FreeAssistantProviders.current(providers, settings));
        assertTrue(FreeAssistantProviders.save(settings, groq, " test-key "));
        assertEquals(groq.baseUrl, settings.baseUrl());
        assertEquals(TaiRemotePresets.ID_GROQ, settings.preset().id);
        assertEquals(groq.model, settings.modelId());
        assertEquals("test-key", settings.apiKey());
        assertTrue(settings.isConfigured());
        // Automatic then resolves to it: requests prefer the remote model by default.
        assertTrue(settings.prefersRemote());
        assertEquals(groq, FreeAssistantProviders.current(providers, settings));
    }

    @Test
    public void aServiceWithNoPresetIsSavedAsAnAddressAndTheOldKeyIsNotCarriedOver() throws Exception {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse(shipped());
        FreeAssistantProviders.save(settings, providers.get(0), "groq-key");
        FreeAssistantProviders.Provider nvidia = providers.get(2);
        assertTrue(FreeAssistantProviders.save(settings, nvidia, "nvidia-key"));
        assertEquals(nvidia.baseUrl, settings.baseUrl());
        assertEquals(TaiRemotePresets.ID_CUSTOM, settings.preset().id);
        assertEquals(nvidia.model, settings.modelId());
        assertEquals("nvidia-key", settings.apiKey());
        assertEquals(nvidia, FreeAssistantProviders.current(providers, settings));
    }

    /** Custom server to NVIDIA with the keystore refusing: the old key is never left beside NVIDIA. */
    @Test
    public void theOldServersKeyNeverStaysBesideTheNewAddress() throws Exception {
        settings.saveTypedBaseUrl("https://my.server.example/v1");
        settings.setModelId("mine");
        assertTrue(settings.setApiKey("my-servers-key"));
        FreeAssistantProviders.Provider nvidia = FreeAssistantProviders.parse(shipped()).get(2);

        keys.refuse = true;
        assertFalse(FreeAssistantProviders.save(settings, nvidia, "nvidia-key"));

        assertEquals(nvidia.baseUrl, settings.baseUrl());
        keys.refuse = false;
        assertNull("the old key is gone", settings.apiKey());
    }

    @Test
    public void savingOverAnotherSetUpServiceAsksFirst() throws Exception {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse(shipped());
        FreeAssistantProviders.Provider groq = providers.get(0);
        // Nothing set up: nothing to ask.
        assertNull(FreeAssistantProviders.replaces(providers, settings, groq));
        // The remote model screen's Mistral with its key.
        settings.choosePreset(TaiRemotePresets.MISTRAL);
        settings.setModelId("mistral-small-latest");
        settings.setApiKey("mistral-key");
        assertEquals("Mistral", FreeAssistantProviders.replaces(providers, settings, groq));
        // Groq set up: switching to OpenRouter names Groq; Groq again asks nothing.
        FreeAssistantProviders.save(settings, groq, "groq-key");
        assertEquals("Groq", FreeAssistantProviders.replaces(providers, settings, providers.get(1)));
        assertNull(FreeAssistantProviders.replaces(providers, settings, groq));
    }

    @Test
    public void aFailedTestSaysWhy() {
        assertEquals(FreeAssistantProviders.Failure.BAD_KEY, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 10, "Unauthorized", 401, "invalid_api_key")));
        assertEquals(FreeAssistantProviders.Failure.BAD_KEY, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 10, "Forbidden", 403, null)));
        assertEquals(FreeAssistantProviders.Failure.LIMIT, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 10, "Too many", 429, "rate_limit_exceeded")));
        assertEquals(FreeAssistantProviders.Failure.OFFLINE, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 10, "no route", 502, "remote_unreachable")));
        assertEquals(FreeAssistantProviders.Failure.OFFLINE, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 30000, "slow", 504, "remote_timeout")));
        assertEquals(FreeAssistantProviders.Failure.OTHER, FreeAssistantProviders.failureOf(
            new TaiRemoteClient.TestResult(false, 10, "boom", 500, "server_error")));
    }

    /** The Test's answer carries the status and code the request path gives a failure. */
    @Test
    public void aRefusedTestKeepsItsStatusAndCode() throws Exception {
        TaiRemoteClient.TestResult refused = TaiRemoteClient.failedTest(
            TaiManager.openAiError(TaiRemoteClient.source(401, "invalid_api_key", "bad")), 12);
        assertEquals(401, refused.status);
        assertEquals("invalid_api_key", refused.code);
        assertEquals(FreeAssistantProviders.Failure.BAD_KEY, FreeAssistantProviders.failureOf(refused));
        TaiRemoteClient.TestResult unreachable = TaiRemoteClient.failedTest(
            TaiManager.openAiError(TaiRemoteClient.source(502, "remote_unreachable", "no route")), 5);
        assertEquals(FreeAssistantProviders.Failure.OFFLINE, FreeAssistantProviders.failureOf(unreachable));
    }

    // ---- the Test's snapshot (review of terl-window-ops, P1) ------------------------------------

    @Test
    public void aTestSendsTheEndpointKeyAndModelAsTheyStoodTogetherWhenItBegan() {
        List<FreeAssistantProviders.Provider> providers = FreeAssistantProviders.parse(shippedOrFail());
        FreeAssistantProviders.Provider first = providers.get(0);
        FreeAssistantProviders.Provider second = providers.get(1);
        assertTrue(FreeAssistantProviders.save(settings, first, "old-key"));
        FreeAssistantTestRun runs = new FreeAssistantTestRun();

        FreeAssistantTestRun.Request request = runs.begin(settings);
        // A switch to another service completes while the request is on its way.
        assertTrue(FreeAssistantProviders.save(settings, second, "new-key"));

        assertEquals(TaiRemoteClient.normalizeBaseUrl(first.baseUrl), request.baseUrl);
        assertEquals("the old service's own key, never the new one", "old-key", request.apiKey);
        assertEquals(first.model, request.modelId);
    }

    @Test
    public void aSupersededTestsAnswerIsDropped() {
        FreeAssistantTestRun runs = new FreeAssistantTestRun();
        FreeAssistantTestRun.Request first = runs.begin(settings);
        FreeAssistantTestRun.Request second = runs.begin(settings);
        assertFalse("a newer test began", runs.isCurrent(first));
        assertTrue(runs.isCurrent(second));
        runs.supersede();
        assertFalse("a save came after it", runs.isCurrent(second));
    }

    private static String shippedOrFail() {
        try {
            return shipped();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class PlainKeys implements TaiSecretStore.KeySource {
        @Nullable private SecretKey key;
        /** The keystore refuses: no secret can be stored. */
        boolean refuse;

        @NonNull
        @Override
        public SecretKey getOrCreate() throws java.security.GeneralSecurityException {
            if (refuse) throw new java.security.GeneralSecurityException("refused");
            if (key == null) {
                KeyGenerator generator = KeyGenerator.getInstance("AES");
                generator.init(256);
                key = generator.generateKey();
            }
            return key;
        }

        @Override
        public void reset() {
            key = null;
        }
    }

    private static final class MapStorage implements TaiSecretStore.Storage {
        private final Map<String, String> values = new HashMap<>();

        @Nullable
        @Override
        public String read(@NonNull String name) {
            return values.get(name);
        }

        @Override
        public void write(@NonNull String name, @NonNull String value) {
            values.put(name, value);
        }

        @Override
        public void remove(@NonNull String name) {
            values.remove(name);
        }
    }
}
