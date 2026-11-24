package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads the settings corpus shipped in the APK. An unreadable asset is an empty corpus: keywords then. */
final class SettingsCorpusAsset {
    private SettingsCorpusAsset() {
    }

    @NonNull
    static String read(@NonNull Context context) {
        try (InputStream in = context.getAssets().open(SettingsCorpus.ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }
}
