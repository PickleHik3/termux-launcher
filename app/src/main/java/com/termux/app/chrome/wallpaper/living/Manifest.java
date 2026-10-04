package com.termux.app.chrome.wallpaper.living;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * One living still on disk (living-stills.md, Part C.5): a folder named by the photo's hash with
 * the image, the depth map, three RGB mask PNGs and {@code recipe.json}. The renderer reads
 * these files by name. {@code recipe.json} is written last, so a folder without it is a build
 * that did not finish and {@link #load} refuses it.
 *
 * <p>Masks (all RGB, never alpha): {@code maskA} R water, G sway, B sky; {@code maskB} R falling
 * water, G subject, B glow; {@code maskC} R bob, G mist, B particles.</p>
 */
public final class Manifest {
    public static final String IMAGE = "image.png";
    public static final String DEPTH = "depth.png";
    public static final String MASK_A = "maskA.png";
    public static final String MASK_B = "maskB.png";
    public static final String MASK_C = "maskC.png";
    public static final String RECIPE = "recipe.json";

    @NonNull private final File mDir;
    @NonNull private final LivingRecipe mRecipe;

    public Manifest(@NonNull File dir, @NonNull LivingRecipe recipe) {
        mDir = dir;
        mRecipe = recipe;
    }

    @NonNull public File directory() { return mDir; }
    /** The hash the folder is named by. */
    @NonNull public String hash() { return mDir.getName(); }
    @NonNull public File image() { return new File(mDir, IMAGE); }
    @NonNull public File depth() { return new File(mDir, DEPTH); }
    @NonNull public File maskA() { return new File(mDir, MASK_A); }
    @NonNull public File maskB() { return new File(mDir, MASK_B); }
    @NonNull public File maskC() { return new File(mDir, MASK_C); }
    @NonNull public File maskD() { return new File(mDir, "maskD.png"); }
    @NonNull public File recipeFile() { return new File(mDir, RECIPE); }
    @NonNull public LivingRecipe recipe() { return mRecipe; }

    /** The wallpaper id the renderer resolves: {@code living:<hash>}. */
    @NonNull
    public String wallpaperId() {
        return "living:" + hash();
    }

    /** Writes {@code recipe.json} into {@code dir}, through a temp file so it appears whole. */
    public static void writeRecipe(@NonNull File dir, @NonNull LivingRecipe recipe) throws IOException {
        File tmp = new File(dir, RECIPE + ".tmp");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(recipe.toJson().toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        File dest = new File(dir, RECIPE);
        if (dest.exists() && !dest.delete()) throw new IOException("Cannot replace " + dest);
        if (!tmp.renameTo(dest)) throw new IOException("Cannot write " + dest);
    }

    /** The manifest in {@code dir}, or {@code null} when a file is missing or the recipe is not readable. */
    @Nullable
    public static Manifest load(@NonNull File dir) {
        File recipeFile = new File(dir, RECIPE);
        for (String name : new String[] {IMAGE, DEPTH, MASK_A, MASK_B, MASK_C}) {
            File f = new File(dir, name);
            if (!f.isFile() || f.length() == 0) return null;
        }
        if (!recipeFile.isFile()) return null;
        try (InputStream in = new FileInputStream(recipeFile)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new Manifest(dir, LivingRecipe.fromJson(new String(bos.toByteArray(), StandardCharsets.UTF_8)));
        } catch (IOException | JSONException e) {
            return null;
        }
    }
}
