package com.termux.app.chrome.wallpaper.living;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Android edge of the living-still builder: decoding the photo and the analysis PNGs into
 * float planes, writing the RGB mask PNGs, and drawing the numbered-outline image Gemma looks at.
 * Everything else in the package is plain Java. Mask PNGs are written as opaque bitmaps so the
 * file has no alpha channel (BitmapFactory premultiplies alpha and would wipe colour under it).
 */
final class LivingBitmaps {
    private LivingBitmaps() {}

    /** Width and height from the file header, without decoding the pixels. */
    @NonNull
    static int[] size(@NonNull File file) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Not an image: " + file);
        return new int[] {o.outWidth, o.outHeight};
    }

    /** The picture scaled to {@code width} px wide, keeping the aspect ratio. */
    @NonNull
    static Bitmap decodeToWidth(@NonNull File file, int width) throws IOException {
        int[] wh = size(file);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, wh[0] / Math.max(1, width))));
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (b == null) throw new IOException("Cannot decode " + file);
        int h = Math.max(1, Math.round(wh[1] * (width / (float) wh[0])));
        if (b.getWidth() == width && b.getHeight() == h) return b;
        Bitmap s = Bitmap.createScaledBitmap(b, width, h, true);
        if (s != b) b.recycle();
        return s;
    }

    @NonNull
    static int[] pixels(@NonNull Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return px;
    }

    /** The three colour channels of a decoded PNG as planes in 0..1, plus its size. */
    static final class Decoded {
        final int w;
        final int h;
        final float[] r;
        final float[] g;
        final float[] b;

        Decoded(int w, int h, float[] r, float[] g, float[] b) {
            this.w = w;
            this.h = h;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    @NonNull
    static Decoded decodeChannels(@NonNull File png) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(png.getAbsolutePath(), o);
        if (bmp == null) throw new IOException("Cannot decode " + png);
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = pixels(bmp);
        bmp.recycle();
        float[] r = new float[w * h], g = new float[w * h], b = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            r[i] = ((px[i] >> 16) & 0xFF) / 255f;
            g[i] = ((px[i] >> 8) & 0xFF) / 255f;
            b[i] = (px[i] & 0xFF) / 255f;
        }
        return new Decoded(w, h, r, g, b);
    }

    private static String readText(File f) {
        if (!f.isFile()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads {@code depth.png}, {@code scene0..2.png} + {@code scene.json}, {@code subject.png}, {@code analysis.json}. */
    @NonNull
    static AnalysisMaps loadAnalysis(@NonNull File dir) throws IOException {
        Decoded depth = decodeChannels(new File(dir, "depth.png"));
        Decoded subject = decodeChannels(new File(dir, "subject.png"));
        List<String> names = AnalysisMaps.parseGroupNames(readText(new File(dir, "scene.json")));
        Map<String, float[]> groups = new LinkedHashMap<>();
        int sw = 0, sh = 0;
        for (int s = 0; s < 3; s++) {
            File f = new File(dir, "scene" + s + ".png");
            if (!f.isFile()) break;
            Decoded d = decodeChannels(f);
            sw = d.w;
            sh = d.h;
            float[][] ch = {d.r, d.g, d.b};
            for (int c = 0; c < 3; c++) {
                int idx = s * 3 + c;
                if (idx >= names.size()) continue;
                String name = names.get(idx);
                if (name != null && !name.trim().isEmpty()) groups.put(name.trim(), ch[c]);
            }
        }
        if (groups.isEmpty()) throw new IOException("No scene groups in " + dir);
        Map<String, String> models = AnalysisMaps.parseModels(readText(new File(dir, "analysis.json")));
        return new AnalysisMaps(depth.r, depth.w, depth.h, subject.r, subject.w, subject.h, groups, sw, sh, models);
    }

    /** Writes three planes in 0..1 as the R, G and B of an opaque PNG. */
    static void writeRgbPng(@NonNull File out, @NonNull float[] r, @NonNull float[] g, @NonNull float[] b,
                            int w, int h) throws IOException {
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF000000 | (to8(r[i]) << 16) | (to8(g[i]) << 8) | to8(b[i]);
        }
        Bitmap bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        bmp.setHasAlpha(false);
        try (FileOutputStream os = new FileOutputStream(out)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) throw new IOException("Cannot write " + out);
        } finally {
            bmp.recycle();
        }
    }

    private static int to8(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    /** A JPEG data URL of the picture scaled so its long side is {@code longSide}. */
    @NonNull
    static String jpegDataUrl(@NonNull Bitmap src, int longSide) throws IOException {
        Bitmap scaled = scaleLongSide(src, longSide);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos);
            return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } finally {
            if (scaled != src) scaled.recycle();
        }
    }

    @NonNull
    static Bitmap scaleLongSide(@NonNull Bitmap src, int longSide) {
        int w = src.getWidth(), h = src.getHeight();
        float f = longSide / (float) Math.max(w, h);
        if (f >= 1f) return src;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * f)), Math.max(1, Math.round(h * f)), true);
    }

    /** The picture with each cluster outlined and its number drawn on its largest part. */
    @NonNull
    static Bitmap markedCopy(@NonNull Bitmap photo, @NonNull ColourClusters.Result clusters, int longSide) {
        Bitmap base = scaleLongSide(photo, longSide);
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        if (base != photo) base.recycle();
        int w = out.getWidth(), h = out.getHeight();
        int[] lab = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(clusters.h - 1, y * clusters.h / h);
            for (int x = 0; x < w; x++) {
                lab[y * w + x] = clusters.labels[sy * clusters.w + Math.min(clusters.w - 1, x * clusters.w / w)];
            }
        }
        int[] px = pixels(out);
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 1; x++) {
                int i = y * w + x;
                if (lab[i] != lab[i + 1] || lab[i] != lab[i + w]) px[i] = Color.WHITE;
            }
        }
        out.setPixels(px, 0, w, 0, 0, w, h);
        Canvas canvas = new Canvas(out);
        float size = Math.max(14f, w / 14f);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setTextSize(size);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setFakeBoldText(true);
        fill.setColor(Color.WHITE);
        Paint stroke = new Paint(fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size / 5f);
        stroke.setColor(Color.BLACK);
        for (int i = 0; i < clusters.k; i++) {
            if (clusters.area[i] <= 0f) continue;
            float x = (clusters.markX[i] + 0.5f) * w / clusters.w;
            float y = (clusters.markY[i] + 0.5f) * h / clusters.h + size / 3f;
            String t = String.valueOf(i + 1);
            canvas.drawText(t, x, y, stroke);
            canvas.drawText(t, x, y, fill);
        }
        return out;
    }
}
