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
 * clusters, the optional director ({@link SceneReader}, one reading of the photo), the masks (from
 * the plan's boxes, or from the vision maps when there is no plan) and the recipe, then the
 * manifest folder {@code files/wallpaper/living/<hash>/}. The whole run is the {@code recipe}
 * stage; its percent climbs 0..100 across clustering (0-15), the director (15-75, skipped when
 * absent), masks (75-92) and writing (92-100). Blocking: run it on a background executor.
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
        SceneReader.Chat chat = TaiGemmaChat.installed(context) ? new TaiGemmaChat(context) : null;
        return build(context, photo, analysisDir, progress, chat, chat == null ? null : SceneReader.modelId(TaiGemmaChat.installedIds(context)));
    }

    /** As above with the call given, asking the E4B vision model (or {@code null} chat for rules only). */
    @NonNull
    public static Manifest build(@NonNull Context context, @NonNull File photo, @NonNull File analysisDir,
                                 @Nullable Progress progress, @Nullable SceneReader.Chat chat) throws IOException {
        return build(context, photo, analysisDir, progress, chat, SceneReader.E4B_ID + SceneReader.VISION_SUFFIX);
    }

    /**
     * As above with the model call given (or {@code null} for rules only).
     *
     * @param modelId the vision model id the call asks for; {@code null} also means rules only
     */
    @NonNull
    public static Manifest build(@NonNull Context context, @NonNull File photo, @NonNull File analysisDir,
                                 @Nullable Progress progress, @Nullable SceneReader.Chat chat,
                                 @Nullable String modelId) throws IOException {
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

        // director: one reading of the photo at 768 long side, in words and boxes
        ScenePlan plan = null;
        long directorMs = 0;
        if (chat != null && modelId != null) {
            t = SystemClock.elapsedRealtime();
            try {
                int gw = size[0] >= size[1] ? Math.min(size[0], GEMMA_LONG_SIDE)
                    : Math.min(size[0], Math.round(GEMMA_LONG_SIDE * size[0] / (float) size[1]));
                Bitmap big = LivingBitmaps.decodeToWidth(photo, Math.max(1, gw));
                String photoUrl = LivingBitmaps.jpegDataUrl(big, GEMMA_LONG_SIDE);
                big.recycle();
                plan = SceneReader.read(chat, modelId, photoUrl);
            } catch (IOException | RuntimeException e) {
                plan = null;
            }
            directorMs = SystemClock.elapsedRealtime() - t;
            // The key keeps its old name: the picker weighs its progress by it.
            timings.put("gemma", directorMs);
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
        float[] zero = new float[pw * ph];
        float[] maskAR, maskAG, maskAB, maskBR, maskBG, maskBB, maskCR, maskCG, maskCB, maskDR, maskDG;
        LivingRecipe recipe;
        if (plan != null) {
            ElementMasks.Result em = ElementMasks.compute(plan, in, clusters, pw, ph);
            // ElementMasks has no bob plane: its subject is the figure the plan marks still, and
            // a figure that bobs in water is a rules-only reading, so bob stays off here. Its
            // warnings (dropped boxes and the like) have no recipe field and are not kept.
            recipe = RecipeRules.fromPlan(plan, em.stats);
            maskAR = em.water; maskAG = em.sway; maskAB = em.sky;
            maskBR = em.fall; maskBG = em.subject; maskBB = em.glow;
            maskCR = zero; maskCG = em.mist; maskCB = em.particles;
            maskDR = em.wind; maskDG = em.still;
        } else {
            RegionMasks.Result masks = RegionMasks.compute(in, clusters, null);
            recipe = RecipeRules.make(masks.stats);
            maskAR = masks.water; maskAG = masks.sway; maskAB = masks.sky;
            maskBR = masks.fall; maskBG = masks.subject; maskBB = masks.glow;
            maskCR = masks.bob; maskCG = masks.mist; maskCB = masks.particles;
            // The rules know no wind and protect no still pixels: a black maskD is what a
            // version 2 manifest needs to load, and the shader reads it as nothing.
            maskDR = zero; maskDG = zero;
        }
        timings.put("masks", SystemClock.elapsedRealtime() - t);
        checkCancel(progress);
        report(progress, 92);

        // recipe
        recipe.version = LivingRecipe.VERSION;
        recipe.models.putAll(maps.models);
        if (plan != null) {
            recipe.gemmaModel = modelId;
            recipe.gemmaAccelerator = chat.lastAccelerator();
            recipe.gemmaFallbackReason = chat.lastFallbackReason();
            recipe.director.model = modelId;
            recipe.director.accelerator = chat.lastAccelerator();
            recipe.director.fallbackReason = chat.lastFallbackReason();
            recipe.director.ms = directorMs;
            recipe.models.put("gemma", modelId.endsWith(SceneReader.VISION_SUFFIX)
                ? modelId.substring(0, modelId.length() - SceneReader.VISION_SUFFIX.length()) : modelId);
        }

        // files
        copy(photo, new File(dir, Manifest.IMAGE));
        copy(new File(analysisDir, "depth.png"), new File(dir, Manifest.DEPTH));
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_A), maskAR, maskAG, maskAB, pw, ph);
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_B), maskBR, maskBG, maskBB, pw, ph);
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_C), maskCR, maskCG, maskCB, pw, ph);
        LivingBitmaps.writeRgbPng(new File(dir, Manifest.MASK_D), maskDR, maskDG, zero, pw, ph);
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
