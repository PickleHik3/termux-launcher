package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** The secret store with a plain JCE key standing in for AndroidKeyStore. */
public class TaiSecretStoreTest {

    @Test
    public void roundTrip_storesCiphertextNotTheSecret() {
        FakeKeys keys = new FakeKeys();
        MapStorage storage = new MapStorage();
        TaiSecretStore store = new TaiSecretStore(keys, storage);

        assertTrue(store.put("remote_api_key", "  sk-test-0123456789  "));
        assertEquals("sk-test-0123456789", store.get("remote_api_key"));
        assertTrue(store.has("remote_api_key"));
        String sealed = storage.values.get("remote_api_key");
        assertNotNull(sealed);
        assertFalse(sealed.contains("sk-test"));
    }

    @Test
    public void blankSecret_clears() {
        TaiSecretStore store = new TaiSecretStore(new FakeKeys(), new MapStorage());
        store.put("k", "secret-value");
        assertTrue(store.put("k", "   "));
        assertNull(store.get("k"));
        assertFalse(store.has("k"));
    }

    @Test
    public void lostKey_readsAsNotSetAndDropsTheCiphertext() {
        FakeKeys keys = new FakeKeys();
        MapStorage storage = new MapStorage();
        TaiSecretStore store = new TaiSecretStore(keys, storage);
        store.put("remote_api_key", "sk-test-0123456789");

        // A restore onto a new phone: the ciphertext came along, the keystore key did not.
        keys.reset();
        assertNull(store.get("remote_api_key"));
        assertFalse(storage.values.containsKey("remote_api_key"));
        assertFalse(store.has("remote_api_key"));
    }

    @Test
    public void unavailableKeystore_neverThrows() {
        FakeKeys keys = new FakeKeys();
        MapStorage storage = new MapStorage();
        TaiSecretStore store = new TaiSecretStore(keys, storage);
        store.put("k", "secret-value");
        keys.broken = true;
        assertNull(store.get("k"));
        assertFalse(store.put("k", "another-secret"));
    }

    @Test
    public void damagedValue_readsAsNotSet() {
        MapStorage storage = new MapStorage();
        TaiSecretStore store = new TaiSecretStore(new FakeKeys(), storage);
        storage.values.put("k", "not-a-sealed-value");
        assertNull(store.get("k"));
        storage.values.put("k", "AAAA:AAAA");
        assertNull(store.get("k"));
    }

    @Test
    public void ciphertext_isBoundToItsName() {
        MapStorage storage = new MapStorage();
        TaiSecretStore store = new TaiSecretStore(new FakeKeys(), storage);
        store.put("a", "secret-value");
        storage.values.put("b", storage.values.get("a"));
        assertNull(store.get("b"));
        assertEquals("secret-value", store.get("a"));
    }

    private static final class FakeKeys implements TaiSecretStore.KeySource {
        @Nullable private SecretKey key;
        boolean broken;

        @NonNull
        @Override
        public SecretKey getOrCreate() throws GeneralSecurityException {
            if (broken) throw new GeneralSecurityException("keystore unavailable");
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
        final Map<String, String> values = new HashMap<>();

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
