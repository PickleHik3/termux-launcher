package com.termux.app.assistant;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * The text the settings lookup embeds: one line per setting, {@code id TAB text}, where the text is
 * the title, the summary and the phrases people say it with. The file is generated from the
 * registry and shipped in the APK ({@code assets/assistant/settings-corpus.tsv}); a test fails when
 * it differs from what {@link #line} would write, so it cannot go stale.
 */
public final class SettingsCorpus {
    static final String ASSET = "assistant/settings-corpus.tsv";

    final List<String> ids = new ArrayList<>();
    final List<String> texts = new ArrayList<>();
    /** Hex SHA-256 of the file's text; the vector cache is valid for exactly this. */
    @NonNull final String hash;

    private SettingsCorpus(@NonNull String raw) {
        hash = sha256(raw);
        for (String line : raw.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab == line.length() - 1) continue;
            ids.add(line.substring(0, tab));
            texts.add(line.substring(tab + 1));
        }
    }

    @NonNull
    static SettingsCorpus parse(@NonNull String raw) {
        return new SettingsCorpus(raw);
    }

    /** One corpus line, without its newline. Tabs and newlines in the text become spaces. */
    @NonNull
    public static String line(@NonNull String id, @NonNull String title, @NonNull String summary,
                       @NonNull List<String> phrases) {
        StringBuilder text = new StringBuilder(title);
        if (!summary.isEmpty()) text.append(". ").append(summary);
        if (!phrases.isEmpty()) text.append(" People say: ").append(String.join("; ", phrases));
        return id + "\t" + text.toString().replaceAll("[\\t\\r\\n]+", " ").trim();
    }

    @NonNull
    private static String sha256(@NonNull String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
