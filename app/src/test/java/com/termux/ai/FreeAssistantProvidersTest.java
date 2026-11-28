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

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        settings = new TaiRemoteSettings(prefs, new TaiSecretStore(new PlainKeys(), new MapStorage()));
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

    private static final class PlainKeys implements TaiSecretStore.KeySource {
        @Nullable private SecretKey key;

        @NonNull
        @Override
        public SecretKey getOrCreate() throws java.security.GeneralSecurityException {
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
