package com.termux.app.chrome.wallpaper.living;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Builds one living still from a photo and the raw model maps (living-stills.md, Part C): colour
 * clusters, region masks, the optional Gemma step, the rule recipe, then the manifest folder
 * {@code files/wallpaper/living/<hash>/}. The whole run is the {@code recipe} stage; its percent
 * climbs 0..100 across clustering (0-15), Gemma (15-75, skipped when absent), masks (75-92) and
 * writing (92-100). Blocking: run it on a background executor.
 */
public final class LivingStillBuilder {
    private LivingStillBuilder() {}

    /** The stage name reported to {@link Progress}. */
    public static final String STAGE_RECIPE = "recipe";

    static final int CLUSTER_WIDTH = 270;
    static final long CLUSTER_SEED = 0x11F3A5L;
    static final int GEMMA_LONG_SIDE = 768;
    /** Masks are stored at photo width / this. */
    static final int MASK_DIVISOR = 4;

    public interface Progress {
        void onProgress(@NonNull String stage, int percent);

        /** True once the user cancelled; the build then throws {@link CancellationException}. */
        default boolean isCancelled() {
            return false;
        }
    }

    /**
     * Builds, asking Gemma when it is installed.
     *
     * @param analysisDir the folder the vision analysis wrote (Part B.7)
     * @return the finished manifest; an older one for the same photo is replaced
     */
    @NonNull
    public static Manifest build(@NonNull Context context, @NonNull File photo, @NonNull File analysisDir,
                                 @Nullable Progress progress) throws IOException {
        GemmaSceneReader.Chat chat = TaiGemmaChat.installed(context) ? new TaiGemmaChat(context) : null;
        return build(context, photo, analysisDir, progress, chat);
    }

    /** As above with the Gemma call given (or {@code null} for rules only). */
    @NonNull
    public static Manifest build(@NonNull Context context, @NonNull File photo, @NonNull File analysisDir,
                                 @Nullable Progress progress, @Nullable GemmaSceneReader.Chat chat) throws IOException {
        long t0 = SystemClock.elapsedRealtime();
        Map<String, Long> timings = new LinkedHashMap<>();
        report(progress, 0);

        String hash = LivingStills.hash16(photo);
        File dir = LivingStills.directoryFor(LivingStills.root(context), hash);
        File recipeFile = new File(dir, Manifest.RECIPE);
        if (recipeFile.exists() && !recipeFile.delete()) throw new IOException("Cannot replace " + recipeFile);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);

        AnalysisMaps maps = LivingBitmaps.loadAnalysis(analysisDir);
        int[] size = LivingBitmaps.size(photo);
        int mw = Math.max(8, size[0] / MASK_DIVISOR);
        int mh = Math.max(8, Math.round(size[1] * (mw / (float) size[0])));

        // clusters
        long t = SystemClock.elapsedRealtime();
        Bitmap small = LivingBitmaps.decodeToWidth(photo, CLUSTER_WIDTH);
        ColourClusters.Result clusters = ColourClusters.compute(
            LivingBitmaps.pixels(small), small.getWidth(), small.getHeight(), ColourClusters.DEFAULT_K, CLUSTER_SEED);
        timings.put("clusters", SystemClock.elapsedRealtime() - t);
        checkCancel(progress);
        report(progress, 15);

        // Gemma
        GemmaSceneReader.Plan plan = null;
        if (chat != null) {
            t = SystemClock.elapsedRealtime();
            Bitmap marked = null;
            try {
                int gw = size[0] >= size[1] ? Math.min(size[0], GEMMA_LONG_SIDE)
                    : Math.min(size[0], Math.round(GEMMA_LONG_SIDE * size[0] / (float) size[1]));
                Bitmap big = LivingBitmaps.decodeToWidth(photo, Math.max(1, gw));
                marked = LivingBitmaps.markedCopy(big, clusters, GEMMA_LONG_SIDE);
                String photoUrl = LivingBitmaps.jpegDataUrl(big, GEMMA_LONG_SIDE);
                String markedUrl = LivingBitmaps.jpegDataUrl(marked, GEMMA_LONG_SIDE);
                big.recycle();
                plan = GemmaSceneReader.read(chat, photoUrl, markedUrl, clusters);
            } catch (IOException | RuntimeException e) {
                plan = null;
            } finally {
                if (marked != null) marked.recycle();
            }
            timings.put("gemma", SystemClock.elapsedRealtime() - t);
            checkCancel(progress);
        }
        small.recycle();
        report(progress, 75);

        // masks
        t = SystemClock.elapsedRealtime();
        Bitmap smallPhoto = LivingBitmaps.decodeToWidth(photo, mw);
        int[] rgb = LivingBitmaps.pixels(smallPhoto);
        int pw = smallPhoto.getWidth(), ph = smallPhoto.getHeight();
        smallPhoto.recycle();
        Map<String, float[]> groups = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> e : maps.groups.entrySet()) {
            groups.put(e.getKey(), Planes.resize(e.getValue(), maps.sceneW, maps.sceneH, pw, ph));
        }
        RegionMasks.Inputs in = new RegionMasks.Inputs(pw, ph, rgb,
            Planes.resize(maps.depth, maps.depthW, maps.depthH, pw, ph),
            Planes.resize(maps.subject, maps.subjectW, maps.subjectH, pw, ph), groups);
        RegionMasks.Result masks = RegionMasks.compute(in, clusters, plan == null ? null : plan.regions);
        timings.put("masks", SystemClock.elapsedRealtime() - t);
        checkCancel(progress);
        report(progress, 92);

        // recipe
        LivingRecipe recipe = RecipeRules.make(masks.stats, plan);
        recipe.models.putAll(maps.models);
        if (plan != null) {
            recipe.gemmaModel = GemmaSceneReader.visionModelId();
            recipe.gemmaAccelerator = chat.lastAccelerator();
            recipe.gemmaFallbackReason = chat.lastFallbackReason();
            recipe.models.put("gemma", GemmaSceneReader.MODEL_ID);
        }

        // files
        copy(photo, new File(dir, Manifest.IMAGE));
        copy(new File(analysisDir, "depth.png"), new File(dir, Manifest.DEPTH));
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_A), masks.water, masks.sway, masks.sky, pw, ph);
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_B), masks.fall, masks.subject, masks.glow, pw, ph);
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_C), masks.bob, masks.mist, masks.particles, pw, ph);
        timings.put("total", SystemClock.elapsedRealtime() - t0);
        recipe.timingsMs.putAll(timings);
        Manifest.writeRecipe(dir, recipe);
        report(progress, 100);

        Manifest manifest = Manifest.load(dir);
        if (manifest == null) throw new IOException("Built manifest is unreadable: " + dir);
        return manifest;
    }

    private static void report(@Nullable Progress p, int percent) {
        if (p != null) p.onProgress(STAGE_RECIPE, percent);
    }

    private static void checkCancel(@Nullable Progress p) {
        if (p != null && p.isCancelled()) throw new CancellationException("Living still cancelled");
    }

    /**
     * Copies through a temporary file renamed into place. "Read again" hands the builder the
     * still's own image.png as the photo, and opening the target first would empty the source.
     */
    private static void copy(File from, File to) throws IOException {
        if (from.getCanonicalPath().equals(to.getCanonicalPath())) return;
        File tmp = new File(to.getParentFile(), to.getName() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(to)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Cannot move " + tmp + " to " + to);
        }
    }
}
