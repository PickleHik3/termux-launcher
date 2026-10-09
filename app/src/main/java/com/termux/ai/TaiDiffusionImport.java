package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The pure decisions the importers share for an MNN text-to-image package (Stable Diffusion 1.5,
 * Taiyi, Sana): which layout a file list is, which of its files belong on the phone, which file
 * stands for the package in a listing and in what order the files are fetched. No I/O: the
 * Hugging Face listing, the folder picker and {@code tai import} all feed it plain relative paths.
 */
final class TaiDiffusionImport {
    private TaiDiffusionImport() {}

    /**
     * The layout the files (paths relative to one package directory) form: {@link
     * TaiDiffusionPackage#TYPE_SD15} for the Stable Diffusion graphs, {@link
     * TaiDiffusionPackage#TYPE_SANA} for the Sana graphs, {@link TaiDiffusionPackage#TYPE_AUTO}
     * when neither (or both) are present. A package missing some graphs still has a layout, so the
     * importer can say what is missing instead of treating the folder as a chat model.
     */
    static int detectLayout(@NonNull Collection<String> relativePaths) {
        boolean sd = false;
        boolean sana = false;
        for (String path : relativePaths) {
            if (path.equals("unet.mnn") || path.equals("text_encoder.mnn")) sd = true;
            else if (path.equals("transformer.mnn") || path.equals("connector.mnn")) sana = true;
        }
        if (sd == sana) return TaiDiffusionPackage.TYPE_AUTO;
        return sd ? TaiDiffusionPackage.TYPE_SD15 : TaiDiffusionPackage.TYPE_SANA;
    }

    /**
     * The model type to record: Taiyi shares Stable Diffusion's file layout, so only the
     * repository or folder name can tell it apart.
     */
    static int typeFor(int layout, @Nullable String name) {
        if (layout == TaiDiffusionPackage.TYPE_SD15 && name != null
            && name.toLowerCase(Locale.ROOT).contains("taiyi")) return TaiDiffusionPackage.TYPE_TAIYI;
        return layout;
    }

    /**
     * The file that stands for a package in a listing (relative to its directory, "" when there is
     * none): the first of {@link #packageFiles}, a small one (a tokenizer or configuration file) when
     * the package has any. The download fetches it first, so the tokenizer can be checked before the
     * gigabytes of graphs are.
     */
    @NonNull
    static String entryFile(@NonNull Collection<String> repositoryFiles, @NonNull String directory) {
        List<String> files = packageFiles(repositoryFiles, directory);
        return files.isEmpty() ? "" : files.get(0);
    }

    /** True for a file the runtime can open; hidden files, the model card and images are left behind. */
    static boolean isPackageFile(@NonNull String relativePath) {
        if (relativePath.isEmpty()) return false;
        for (String segment : relativePath.split("/")) {
            if (segment.isEmpty() || segment.startsWith(".")) return false;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mnn") || lower.endsWith(".weight") || lower.endsWith(".json")
            || lower.endsWith(".txt") || lower.endsWith(".mtok") || lower.endsWith(".model")
            || lower.endsWith(".bin");
    }

    /** The graphs and weights: the files big enough that a wrong package should be refused before them. */
    static boolean isHeavy(@NonNull String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mnn") || lower.endsWith(".weight") || lower.endsWith(".bin");
    }

    /**
     * The package files under {@code directory} ("" for the repository root, else ending in "/") of a
     * repository's file list, relative to it and ordered small files first (tokenizer and
     * configuration) so the tokenizer can be checked before the gigabytes are fetched.
     */
    @NonNull
    static List<String> packageFiles(@NonNull Collection<String> repositoryFiles, @NonNull String directory) {
        List<String> light = new ArrayList<>();
        List<String> heavy = new ArrayList<>();
        for (String name : new LinkedHashSet<>(repositoryFiles)) {
            if (!name.startsWith(directory)) continue;
            String relative = name.substring(directory.length());
            if (!isPackageFile(relative)) continue;
            (isHeavy(relative) ? heavy : light).add(relative);
        }
        Collections.sort(light);
        Collections.sort(heavy);
        light.addAll(heavy);
        return light;
    }

    /**
     * Directories of a repository's file list that hold a diffusion package, each with a trailing
     * slash ("" is the root).
     */
    @NonNull
    static List<String> packageDirectories(@NonNull Collection<String> repositoryFiles) {
        LinkedHashSet<String> directories = new LinkedHashSet<>();
        for (String name : repositoryFiles) {
            int slash = name.lastIndexOf('/');
            String directory = slash < 0 ? "" : name.substring(0, slash + 1);
            String file = name.substring(directory.length());
            if (file.equals("unet.mnn") || file.equals("text_encoder.mnn")
                || file.equals("transformer.mnn") || file.equals("connector.mnn")) directories.add(directory);
        }
        List<String> result = new ArrayList<>();
        for (String directory : directories) {
            List<String> inside = new ArrayList<>();
            for (String name : repositoryFiles) {
                if (name.startsWith(directory) && name.indexOf('/', directory.length()) < 0) {
                    inside.add(name.substring(directory.length()));
                }
            }
            if (detectLayout(inside) != TaiDiffusionPackage.TYPE_AUTO) result.add(directory);
        }
        return result;
    }

    /**
     * The registered model for an installed package: backend {@code mnn-diffusion}, capability
     * {@code image_generation}, the model type in {@code architecture} (so {@code tai image --model id}
     * needs no {@code --type}), and the package directory as the path.
     */
    @NonNull
    static TaiModelSpec spec(@NonNull String id, @NonNull String displayName, @NonNull String source,
                             @NonNull String license, @NonNull String directory, int type, long sizeBytes) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
        return new TaiModelSpec(id, displayName, "Image generation model", source, directory, license, sizeBytes,
            capabilities, false, null, TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN,
            TaiDiffusionPackage.typeName(type), null, 4096, 0, null);
    }

    /**
     * True when {@code directory} is a package directory or lies inside one (Sana's {@code llm/}
     * holds a config.json and LLM graphs that are part of the image model, not a chat model).
     */
    static boolean insidePackage(@NonNull Collection<String> packageDirectories, @NonNull String directory) {
        for (String root : packageDirectories) {
            if (directory.startsWith(root)) return true;
        }
        return false;
    }
}
