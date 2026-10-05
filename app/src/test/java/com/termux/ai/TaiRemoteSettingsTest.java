package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

@RunWith(RobolectricTestRunner.class)
public class TaiRemoteSettingsTest {
    private SharedPreferences prefs;
    private TaiRemoteSettings settings;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        settings = new TaiRemoteSettings(prefs, new TaiSecretStore(new PlainKeys(), new MapStorage()));
    }

    @Test
    public void configured_needsAnAllowedAddressAndAModel() {
        assertFalse(settings.isConfigured());
        settings.setBaseUrl("http://api.example.com/v1");
        settings.setModelId("gpt-4o-mini");
        assertFalse(settings.isConfigured());
        settings.setBaseUrl("https://api.example.com/v1/");
        assertEquals("https://api.example.com/v1", settings.baseUrl());
        assertTrue(settings.isConfigured());
    }

    @Test
    public void routing_defaultsToPreferRemote() {
        assertTrue(settings.prefersRemote());
        settings.setRouting(TaiRemoteSettings.ROUTING_LOCAL_FIRST);
        assertFalse(settings.prefersRemote());
        settings.setRouting("nonsense");
        assertTrue(settings.prefersRemote());
    }

    @Test
    public void images_overrideWinsAndANewModelForgetsBoth() {
        settings.setModelId("a");
        settings.setProbedImages(false);
        assertFalse(settings.understandsImages());
        settings.setImagesOverride(true);
        assertTrue(settings.understandsImages());
        assertTrue(settings.imagesOverridden());

        settings.setModelId("b");
        assertNull(settings.probedImages());
        assertFalse(settings.imagesOverridden());
        assertFalse(settings.understandsImages());
    }

    @Test
    public void key_isNotInPlainPrefsAndRemoveClearsEverything() {
        settings.setBaseUrl("https://api.example.com/v1");
        settings.setModelId("m");
        assertTrue(settings.setApiKey("sk-test-0123456789"));
        settings.setConsentShown(true);
        assertEquals("sk-test-0123456789", settings.apiKey());
        for (Object value : prefs.getAll().values()) {
            assertFalse(String.valueOf(value).contains("sk-test"));
        }

        settings.clearAll();
        assertFalse(settings.hasApiKey());
        assertEquals("", settings.baseUrl());
        assertEquals("", settings.modelId());
        assertFalse(settings.consentShown());
        assertFalse(settings.isConfigured());
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
