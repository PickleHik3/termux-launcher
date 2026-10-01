package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * MNN 3.6.1 opens {@code tokenizer.mtok} for Stable Diffusion, but the published
 * {@code taobao-mnn} packages ship the raw CLIP {@code vocab.json} and {@code merges.txt}, and the
 * converter cannot run on the phone. The app bundles one verified {@code tokenizer.mtok} for the
 * standard CLIP ViT-L/14 tokenizer; this installs it beside a package whose raw files are exactly
 * that tokenizer, and refuses any other, because a converted tokenizer for different vocabulary
 * would silently draw the wrong pictures.
 */
final class TaiDiffusionTokenizer {
    static final String ASSET = "tai-diffusion/clip-vit-l14.tokenizer.mtok.gz";
    static final String VOCAB_SHA256 = "e089ad92ba36837a0d31433e555c8f45fe601ab5c221d4f607ded32d9f7a4349";
    static final String MERGES_SHA256 = "9fd691f7c8039210e0fced15865466c65820d09b63988b0174bfe25de299051a";
    static final String MTOK_SHA256 = "4c64995cf841c4182f5b05911888c8884cc20e29bb9716d83e1d8e7cdf19862e";
    static final String FILE = "tokenizer.mtok";

    enum Outcome {
        /** {@code tokenizer.mtok} was already there; nothing was touched. */
        PRESENT,
        /** The bundled tokenizer was written. */
        INSTALLED,
        /** No raw tokenizer files to decide from; the package check names what is missing. */
        NO_SOURCE,
        /** The raw tokenizer is not the standard CLIP one; the package needs converting with MNN's tools. */
        INCOMPATIBLE,
        /** The bundled tokenizer could not be read or failed its check, or the folder is read-only. */
        UNAVAILABLE
    }

    /** Where the bundled gzip comes from: the app's assets, or a fixture in tests. */
    interface Source {
        InputStream open() throws IOException;
    }

    static final class Result {
        @NonNull final Outcome outcome;
        @NonNull final String message;

        Result(@NonNull Outcome outcome, @NonNull String message) {
            this.outcome = outcome;
            this.message = message;
        }

        /** False when the import must stop. */
        boolean proceed() {
            return outcome != Outcome.INCOMPATIBLE && outcome != Outcome.UNAVAILABLE;
        }
    }

    private TaiDiffusionTokenizer() {}

    /** Installs the app's bundled tokenizer when {@code directory} needs it and matches it. */
    @NonNull
    static Result ensure(@NonNull File directory, @NonNull android.content.Context context) {
        return ensure(directory, () -> context.getAssets().open(ASSET));
    }

    @NonNull
    static Result ensure(@NonNull File directory, @NonNull Source source) {
        return ensure(directory, source, VOCAB_SHA256, MERGES_SHA256, MTOK_SHA256);
    }

    /** As {@link #ensure(File, Source)} with the hashes the raw files and the bundled tokenizer must have. */
    @NonNull
    static Result ensure(@NonNull File directory, @NonNull Source source, @NonNull String vocabSha256,
                         @NonNull String mergesSha256, @NonNull String mtokSha256) {
        File target = new File(directory, FILE);
        if (target.isFile() && target.length() > 0L) return new Result(Outcome.PRESENT, "");
        File vocab = new File(directory, "vocab.json");
        File merges = new File(directory, "merges.txt");
        if (!vocab.isFile() || !merges.isFile()) return new Result(Outcome.NO_SOURCE, "");
        try {
            if (!vocabSha256.equals(sha256(vocab)) || !mergesSha256.equals(sha256(merges))) {
                return new Result(Outcome.INCOMPATIBLE,
                    "This image model uses a tokenizer that has to be converted with MNN's tools before it can "
                        + "run here. That cannot be done on the phone.");
            }
            byte[] bytes = readAll(source);
            if (!mtokSha256.equals(hex(digest(new ByteArrayInputStream(bytes))))) return unavailable();
            File temporary = new File(directory, FILE + ".part");
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(bytes);
            }
            if (target.exists() && !target.delete() || !temporary.renameTo(target)) {
                temporary.delete();
                return new Result(Outcome.UNAVAILABLE, "The image model's folder cannot be written to.");
            }
            return new Result(Outcome.INSTALLED, "");
        } catch (IOException e) {
            return unavailable();
        }
    }

    @NonNull
    private static Result unavailable() {
        return new Result(Outcome.UNAVAILABLE, "The tokenizer this image model needs is missing from the app.");
    }

    @NonNull
    private static byte[] readAll(@NonNull Source source) throws IOException {
        try (InputStream in = new GZIPInputStream(source.open())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    @NonNull
    private static String sha256(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return hex(digest(in));
        }
    }

    @NonNull
    private static byte[] digest(@NonNull InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private static String hex(@NonNull byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
