package com.termux.ai;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;

public final class TaiRemoteCatalog {
    private static final String URL = "https://raw.githubusercontent.com/PickleHik3/termux-launcher/experimental/app/src/main/assets/tai-model-catalog.json";
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEANd5QI45iw4VvVnbUJ+Fxo4ptanIOdEsYYLf+VSrEXdo=";
    private static final String PREFS = "tai_remote_catalog";
    private static final String VERSION = "version";

    private TaiRemoteCatalog() {}

    public static void loadCached(@NonNull Context context) {
        File file = cacheFile(context);
        if (!file.isFile()) return;
        try (InputStream input = new FileInputStream(file)) {
            TaiModelCatalog.applyRemotePayload(verify(read(input)), false);
        } catch (Exception ignored) {}
    }

    public static void refresh(@NonNull Context context) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != 200) return;
            byte[] manifest;
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) { manifest = read(input); }
            JSONObject payload = verify(manifest);
            int version = payload.optInt("version", 0);
            int current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(VERSION, 0);
            if (version < current) return;
            TaiModelCatalog.applyRemotePayload(payload, true);
            File file = cacheFile(context);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) { output.write(manifest); }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(VERSION, version).apply();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject verify(byte[] manifestBytes) throws Exception {
        JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
        byte[] payload = Base64.decode(manifest.getString("payload"), Base64.DEFAULT);
        byte[] signatureBytes = Base64.decode(manifest.getString("signature"), Base64.DEFAULT);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(
            new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT))));
        verifier.update(payload);
        if (!verifier.verify(signatureBytes)) throw new SecurityException("Invalid catalog signature");
        JSONObject result = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        String expiresAt = result.optString("expiresAt", "");
        if (expiresAt.isEmpty() || Instant.parse(expiresAt).isBefore(Instant.now())) throw new SecurityException("Catalog expired");
        return result;
    }

    private static File cacheFile(Context context) { return new File(context.getFilesDir(), "tai/catalog/remote-catalog.json"); }
    private static byte[] read(InputStream input) throws Exception { ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); return output.toByteArray(); }
}
