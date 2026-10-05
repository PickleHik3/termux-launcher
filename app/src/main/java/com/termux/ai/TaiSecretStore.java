package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small secrets (the remote provider's API key, later the Hugging Face token) encrypted at rest:
 * an AES-256-GCM key that never leaves {@code AndroidKeyStore}, and the ciphertext in a prefs file
 * this class owns. {@code androidx.security:security-crypto} is deprecated, so it is not used.
 *
 * <p>A key that is gone or invalidated (a backup restored onto a new phone, a wiped keystore)
 * makes the stored ciphertext unreadable. {@link #get} then drops it and reports "not set", so the
 * user is asked for the secret again instead of the app crashing.
 */
public final class TaiSecretStore {
    static final String PREFS_NAME = "tai_secrets";
    static final String KEY_ALIAS = "tai_secret_store_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    /** Where the AES key lives; the keystore on a phone, a plain key in tests. */
    interface KeySource {
        @NonNull SecretKey getOrCreate() throws GeneralSecurityException;

        /** Forgets the key, so the next {@link #getOrCreate} makes a fresh one. */
        void reset();
    }

    /** Where the ciphertext lives, one string per secret name. */
    interface Storage {
        @Nullable String read(@NonNull String name);

        void write(@NonNull String name, @NonNull String value);

        void remove(@NonNull String name);
    }

    private final KeySource keys;
    private final Storage storage;

    public TaiSecretStore(@NonNull Context context) {
        this(new AndroidKeyStoreSource(), new PrefsStorage(context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)));
    }

    TaiSecretStore(@NonNull KeySource keys, @NonNull Storage storage) {
        this.keys = keys;
        this.storage = storage;
    }

    /**
     * Encrypts and stores {@code secret}; a blank secret clears the name instead. Returns false
     * when the keystore refused even after a fresh key, and nothing was stored.
     */
    public synchronized boolean put(@NonNull String name, @Nullable String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            clear(name);
            return true;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                storage.write(name, encrypt(keys.getOrCreate(), name, secret.trim()));
                return true;
            } catch (GeneralSecurityException | RuntimeException e) {
                // An invalidated key fails at init; one fresh key is worth a second try. Every
                // other stored secret was sealed with the old key and reads as "not set" from now.
                keys.reset();
            }
        }
        return false;
    }

    /** The secret, or {@code null} when it was never set or can no longer be decrypted. */
    @Nullable
    public synchronized String get(@NonNull String name) {
        String sealed = storage.read(name);
        if (sealed == null || sealed.isEmpty()) return null;
        try {
            return decrypt(keys.getOrCreate(), name, sealed);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Lost or invalidated key, or a damaged value: the ciphertext is useless now.
            storage.remove(name);
            return null;
        }
    }

    public synchronized boolean has(@NonNull String name) {
        return get(name) != null;
    }

    public synchronized void clear(@NonNull String name) {
        storage.remove(name);
    }

    /** {@code base64(iv) + ":" + base64(ciphertext)}; the name is bound in as associated data. */
    @NonNull
    static String encrypt(@NonNull SecretKey key, @NonNull String name, @NonNull String secret) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        // The cipher picks the IV: AndroidKeyStore keys refuse a caller-chosen one.
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(cipher.getIV()) + ":" + encoder.encodeToString(ciphertext);
    }

    @NonNull
    static String decrypt(@NonNull SecretKey key, @NonNull String name, @NonNull String sealed) throws GeneralSecurityException {
        int split = sealed.indexOf(':');
        if (split <= 0) throw new GeneralSecurityException("Malformed secret");
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] iv = decoder.decode(sealed.substring(0, split));
        byte[] ciphertext = decoder.decode(sealed.substring(split + 1));
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static final class AndroidKeyStoreSource implements KeySource {
        private static final String PROVIDER = "AndroidKeyStore";

        @NonNull
        @Override
        public SecretKey getOrCreate() throws GeneralSecurityException {
            KeyStore store = loadStore();
            if (store.containsAlias(KEY_ALIAS)) {
                java.security.Key existing = store.getKey(KEY_ALIAS, null);
                if (existing instanceof SecretKey) return (SecretKey) existing;
                store.deleteEntry(KEY_ALIAS);
            }
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
            return generator.generateKey();
        }

        @Override
        public void reset() {
            try {
                loadStore().deleteEntry(KEY_ALIAS);
            } catch (Exception ignored) {
                // Nothing to delete, or the keystore is unavailable; the next call reports it.
            }
        }

        @NonNull
        private static KeyStore loadStore() throws GeneralSecurityException {
            KeyStore store = KeyStore.getInstance(PROVIDER);
            try {
                store.load(null);
            } catch (java.io.IOException e) {
                throw new GeneralSecurityException(e);
            }
            return store;
        }
    }

    private static final class PrefsStorage implements Storage {
        private final SharedPreferences prefs;

        PrefsStorage(@NonNull SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Nullable
        @Override
        public String read(@NonNull String name) {
            return prefs.getString(name, null);
        }

        @Override
        public void write(@NonNull String name, @NonNull String value) {
            prefs.edit().putString(name, value).apply();
        }

        @Override
        public void remove(@NonNull String name) {
            prefs.edit().remove(name).apply();
        }
    }
}
