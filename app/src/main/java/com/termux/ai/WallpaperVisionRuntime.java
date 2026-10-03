package com.termux.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one-shot wallpaper analysis ({@code living-stills.md}, Part B): depth, then scene, then
 * subject, on one photo, in the {@code :tai_runtime} process. Each stage opens its graph, runs it
 * once, writes its maps and closes the graph before the next one loads, so the peak is one graph.
 *
 * <pre>
 *   depth    Depth Anything 3 [1,3,896,504] (distance, inverted) or V2 [1,3,518,686] (disparity)
 *              → depth.png        8-bit gray, near = 255, at the model's resolution
 *   scene    SegFormer-B0 ADE20K [1,3,512,512] → NHWC logits [1,128,128,150]
 *              → scene0..2.png    128x128 RGB, three softmax group sums each; scene.json names them
 *   subject  U-2-Net [1,3,320,320], divided by the image max first → saliency [1,1,320,320]
 *              → subject.png      8-bit gray, 320x320
 * </pre>
 *
 * Input sizes are read off each graph's input tensor, so a re-exported graph at another size still
 * works; what is fixed is the preprocessing, which is {@link WallpaperVisionMath}'s. The graphs run
 * on the CPU through {@link TaiXnnpackDelegate} ({@code min(4, cores)} threads) with the stock
 * interpreter as a fallback when a delegated run fails for anything but a cancellation. Cancel
 * stops between stages and through {@link Interpreter#setCancelled} inside a running graph.
 */
final class WallpaperVisionRuntime {
    private static final String TAG = "TaiVision";
    static final String RUNTIME_NAME = "wallpaper-vision";
    static final String STAGE_DEPTH = "depth";
    static final String STAGE_SCENE = "scene";
    static final String STAGE_SUBJECT = "subject";
    /** The architecture the catalogue gives Depth Anything 3 entries: its output is distance, not disparity. */
    static final String ARCHITECTURE_DEPTH_ANYTHING_3 = "depth-anything-3";
    static final String ARCHITECTURE_DEPTH_ANYTHING_V2 = "depth-anything-v2";

    static final String DEPTH_FILE = "depth.png";
    static final String SCENE_JSON_FILE = "scene.json";
    static final String SUBJECT_FILE = "subject.png";
    static final String ANALYSIS_FILE = "analysis.json";

    /** The decoded photo keeps at least this many pixels on its short side; bigger photos are sub-sampled on decode. */
    private static final int MIN_DECODED_SHORT_SIDE = 1024;

    /** Progress of the stage in flight, 0..100. May be called from the job thread only. */
    interface Progress {
        void onStage(@NonNull String stage, int percent);
    }

    /** One run: the photo, where the maps go, and the model behind each stage. */
    static final class Params {
        final String imagePath;
        final File outDir;
        final TaiModelSpec depth;
        final TaiModelSpec scene;
        final TaiModelSpec subject;

        Params(@NonNull String imagePath, @NonNull File outDir, @NonNull TaiModelSpec depth,
               @NonNull TaiModelSpec scene, @NonNull TaiModelSpec subject) {
            this.imagePath = imagePath;
            this.outDir = outDir;
            this.depth = depth;
            this.scene = scene;
            this.subject = subject;
        }
    }

    private final TaiResidency residency;
    @Nullable private final Context appContext;
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile boolean cancelRequested;
    @Nullable private volatile Graph current;
    /** Cleared once a delegated run fails, so every later graph uses the stock interpreter. */
    private volatile boolean useDelegate = true;

    WallpaperVisionRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    boolean isActive() {
        return active.get();
    }

    /** Stops the run in flight at the next check and inside the running graph; false when none is running. */
    boolean requestCancel() {
        if (!active.get()) return false;
        cancelRequested = true;
        Graph graph = current;
        if (graph != null) graph.cancel();
        return true;
    }

    /**
     * Runs the three stages and writes their files. Returns the summary ({@code ok}, models,
     * timings, sizes) or an error envelope; never throws for a model or image problem.
     */
    @NonNull
    JSONObject analyze(@NonNull Params params, @NonNull Progress progress) throws JSONException {
        if (!active.compareAndSet(false, true)) {
            return error(409, "wallpaper_analysis_active", "A wallpaper analysis is already running.");
        }
        cancelRequested = false;
        long started = System.currentTimeMillis();
        try {
            if (!params.outDir.isDirectory() && !params.outDir.mkdirs()) {
                return error(400, "invalid_output", "The output folder could not be created.");
            }
            long decodeStarted = System.currentTimeMillis();
            Photo photo = decode(params.imagePath);
            long decodeMs = System.currentTimeMillis() - decodeStarted;
            JSONObject analysis = new JSONObject();
            analysis.put("image", params.imagePath);
            analysis.put("imageWidth", photo.width);
            analysis.put("imageHeight", photo.height);
            JSONObject models = new JSONObject();
            JSONObject timings = new JSONObject();
            JSONObject sizes = new JSONObject();
            timings.put("decodeMs", decodeMs);

            JSONObject failure = runStage(STAGE_DEPTH, params.depth, params, photo, progress, models, timings, sizes);
            if (failure != null) return failure;
            failure = runStage(STAGE_SCENE, params.scene, params, photo, progress, models, timings, sizes);
            if (failure != null) return failure;
            failure = runStage(STAGE_SUBJECT, params.subject, params, photo, progress, models, timings, sizes);
            if (failure != null) return failure;

            long totalMs = System.currentTimeMillis() - started;
            timings.put("totalMs", totalMs);
            analysis.put("models", models);
            analysis.put("timings", timings);
            analysis.put("sizes", sizes);
            analysis.put("files", new JSONArray().put(DEPTH_FILE).put("scene0.png").put("scene1.png").put("scene2.png")
                .put(SCENE_JSON_FILE).put(SUBJECT_FILE));
            writeText(new File(params.outDir, ANALYSIS_FILE), analysis.toString(2));
            JSONObject summary = new JSONObject(analysis.toString());
            summary.put("ok", true);
            summary.put("outDir", params.outDir.getAbsolutePath());
            summary.put("_runtime", RUNTIME_NAME);
            return summary;
        } catch (CancelledException e) {
            return error(499, "cancelled", "The wallpaper analysis was cancelled.");
        } catch (Throwable t) {
            Log.w(TAG, "Wallpaper analysis failed", t);
            return error(500, "wallpaper_analysis_failed", "Wallpaper analysis failed: " + message(t));
        } finally {
            current = null;
            active.set(false);
        }
    }

    /** One stage: the error envelope when it could not run, {@code null} when its files are written. */
    @Nullable
    private JSONObject runStage(@NonNull String stage, @NonNull TaiModelSpec spec, @NonNull Params params,
                                @NonNull Photo photo, @NonNull Progress progress, @NonNull JSONObject models,
                                @NonNull JSONObject timings, @NonNull JSONObject sizes) throws Exception {
        checkCancelled();
        progress.onStage(stage, 0);
        if (spec.localPath == null || !new File(spec.localPath).isFile()) {
            return error(404, "vision_model_missing", spec.displayName + " is not installed.");
        }
        File file = new File(spec.localPath);
        long loadStarted = System.currentTimeMillis();
        Graph graph = open(spec, file);
        long loadMs = System.currentTimeMillis() - loadStarted;
        try {
            current = graph;
            if (cancelRequested) graph.cancel();
            progress.onStage(stage, 30);
            long runStarted = System.currentTimeMillis();
            JSONObject stageSizes = new JSONObject();
            switch (stage) {
                case STAGE_DEPTH:
                    runDepth(graph, spec, photo, params.outDir, progress, stageSizes);
                    break;
                case STAGE_SCENE:
                    runScene(graph, photo, params.outDir, progress, stageSizes);
                    break;
                default:
                    runSubject(graph, photo, params.outDir, progress, stageSizes);
                    break;
            }
            long runMs = System.currentTimeMillis() - runStarted;
            models.put(stage, spec.id);
            timings.put(stage + "LoadMs", loadMs);
            timings.put(stage + "Ms", runMs);
            sizes.put(stage, stageSizes);
        } finally {
            current = null;
            closeGraph(graph, spec);
        }
        progress.onStage(stage, 100);
        return null;
    }

    // ---- The three graphs ------------------------------------------------------------------

    private void runDepth(@NonNull Graph graph, @NonNull TaiModelSpec spec, @NonNull Photo photo, @NonNull File outDir,
                          @NonNull Progress progress, @NonNull JSONObject sizes) throws Exception {
        int[] in = graph.inputSize();
        float[] hwc = WallpaperVisionMath.resizeRgb(photo.argb, photo.width, photo.height, in[0], in[1],
            WallpaperVisionMath.Filter.BICUBIC);
        float[] raw = graph.run(WallpaperVisionMath.toNchw(hwc, in[0], in[1], false), this);
        int[] out = graph.outputPlane();
        if (raw.length != out[0] * out[1]) throw new IOException("Depth output " + raw.length + " does not match " + out[0] + "x" + out[1]);
        progress.onStage(STAGE_DEPTH, 90);
        boolean distance = ARCHITECTURE_DEPTH_ANYTHING_3.equals(spec.architecture)
            || TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID.equals(spec.id);
        byte[] gray = WallpaperVisionMath.toGray8(WallpaperVisionMath.depthNearOne(raw, distance));
        writeBytes(new File(outDir, DEPTH_FILE), WallpaperVisionMath.encodePng(gray, out[1], out[0], 1));
        sizes.put("width", out[1]).put("height", out[0]).put("inputWidth", in[0]).put("inputHeight", in[1]);
        sizes.put("distance", distance);
    }

    private void runScene(@NonNull Graph graph, @NonNull Photo photo, @NonNull File outDir,
                          @NonNull Progress progress, @NonNull JSONObject sizes) throws Exception {
        int[] in = graph.inputSize();
        float[] hwc = WallpaperVisionMath.resizeRgb(photo.argb, photo.width, photo.height, in[0], in[1],
            WallpaperVisionMath.Filter.BILINEAR);
        float[] logits = graph.run(WallpaperVisionMath.toNchw(hwc, in[0], in[1], false), this);
        int[] shape = graph.outputShape();
        if (shape.length != 4) throw new IOException("Scene output is not NHWC: " + Arrays.toString(shape));
        int h = shape[1], w = shape[2], classes = shape[3];
        progress.onStage(STAGE_SCENE, 70);
        float[][] groups = WallpaperVisionMath.sceneGroups(logits, h, w, classes, WallpaperVisionMath.SCENE_GROUP_CLASSES);
        JSONArray files = new JSONArray();
        for (int i = 0; i < WallpaperVisionMath.sceneImageCount(); i++) {
            byte[] rgb = WallpaperVisionMath.packSceneRgb(groups, w * h, i);
            String name = "scene" + i + ".png";
            writeBytes(new File(outDir, name), WallpaperVisionMath.encodePng(rgb, w, h, 3));
            JSONArray channels = new JSONArray();
            for (int c = 0; c < WallpaperVisionMath.GROUPS_PER_IMAGE; c++) {
                int g = i * WallpaperVisionMath.GROUPS_PER_IMAGE + c;
                channels.put(g < groups.length ? WallpaperVisionMath.SCENE_GROUP_NAMES[g] : JSONObject.NULL);
            }
            files.put(new JSONObject().put("file", name).put("channels", channels));
        }
        JSONObject sceneJson = new JSONObject();
        sceneJson.put("width", w).put("height", h).put("classes", classes);
        sceneJson.put("images", files);
        JSONObject classMap = new JSONObject();
        for (int g = 0; g < WallpaperVisionMath.SCENE_GROUP_NAMES.length; g++) {
            JSONArray ids = new JSONArray();
            for (int id : WallpaperVisionMath.SCENE_GROUP_CLASSES[g]) ids.put(id);
            classMap.put(WallpaperVisionMath.SCENE_GROUP_NAMES[g], ids);
        }
        sceneJson.put("groups", classMap);
        writeText(new File(outDir, SCENE_JSON_FILE), sceneJson.toString(2));
        sizes.put("width", w).put("height", h).put("inputWidth", in[0]).put("inputHeight", in[1]);
    }

    private void runSubject(@NonNull Graph graph, @NonNull Photo photo, @NonNull File outDir,
                            @NonNull Progress progress, @NonNull JSONObject sizes) throws Exception {
        int[] in = graph.inputSize();
        float[] hwc = WallpaperVisionMath.resizeRgb(photo.argb, photo.width, photo.height, in[0], in[1],
            WallpaperVisionMath.Filter.BILINEAR);
        float[] saliency = graph.run(WallpaperVisionMath.toNchw(hwc, in[0], in[1], true), this);
        int[] out = graph.outputPlane();
        if (saliency.length != out[0] * out[1]) throw new IOException("Subject output " + saliency.length + " does not match " + out[0] + "x" + out[1]);
        progress.onStage(STAGE_SUBJECT, 90);
        byte[] gray = WallpaperVisionMath.toGray8(saliency);
        writeBytes(new File(outDir, SUBJECT_FILE), WallpaperVisionMath.encodePng(gray, out[1], out[0], 1));
        sizes.put("width", out[1]).put("height", out[0]).put("inputWidth", in[0]).put("inputHeight", in[1]);
    }

    // ---- Graph lifecycle -------------------------------------------------------------------

    /** Opens {@code file} and registers it; the load is metered and its cost kept in the runtime history. */
    @NonNull
    private Graph open(@NonNull TaiModelSpec spec, @NonNull File file) throws Exception {
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        TaiLoadMeter meter = TaiLoadMeter.start(appContext);
        long measured;
        Graph graph;
        try {
            graph = Graph.open(file, threads, useDelegate);
        } finally {
            measured = meter.stop();
        }
        residency.register(TaiResidency.Entry.vision(spec).withMeasured(measured >= 0L ? measured : null));
        residency.setBusy(TaiResidency.Kind.VISION, spec.id, true);
        if (measured >= 0L && appContext != null) {
            TaiRuntimeHistory.recordMeasuredLoad(appContext, spec, TaiDeviceCapabilities.detect(appContext),
                TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, measured);
        }
        return graph;
    }

    private void closeGraph(@NonNull Graph graph, @NonNull TaiModelSpec spec) {
        graph.close();
        residency.deregister(TaiResidency.Kind.VISION, spec.id);
    }

    private void checkCancelled() throws CancelledException {
        if (cancelRequested) throw new CancelledException();
    }

    private static final class CancelledException extends Exception {
        CancelledException() {
            super("cancelled");
        }
    }

    // ---- The photo -------------------------------------------------------------------------

    private static final class Photo {
        final int[] argb;
        final int width;
        final int height;

        Photo(int[] argb, int width, int height) {
            this.argb = argb;
            this.width = width;
            this.height = height;
        }
    }

    @NonNull
    private static Photo decode(@NonNull String path) throws IOException {
        File file = new File(path);
        if (!file.isFile() || !file.canRead()) throw new IOException("Cannot read " + path);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not an image: " + path);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MIN_DECODED_SHORT_SIDE
            && bounds.outHeight / (sample * 2) >= MIN_DECODED_SHORT_SIDE) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("Could not decode " + path);
        try {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            return new Photo(pixels, w, h);
        } finally {
            bitmap.recycle();
        }
    }

    // ---- Files -----------------------------------------------------------------------------

    private static void writeBytes(@NonNull File file, @NonNull byte[] data) throws IOException {
        File partial = new File(file.getParentFile(), file.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(partial)) {
            out.write(data);
        }
        if (file.exists() && !file.delete()) throw new IOException("Could not replace " + file.getName());
        if (!partial.renameTo(file)) throw new IOException("Could not write " + file.getName());
    }

    private static void writeText(@NonNull File file, @NonNull String text) throws IOException {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    private static String message(@NonNull Throwable t) {
        return t.getMessage() == null || t.getMessage().trim().isEmpty() ? t.getClass().getSimpleName() : t.getMessage();
    }

    @NonNull
    private static JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("ok", false);
        error.put("error", code);
        error.put("message", message);
        error.put("_statusCode", status);
        return error;
    }

    /**
     * One static-shape graph: a float32 NCHW input in, a float32 output out, both through direct
     * buffers. Used once and closed.
     */
    private static final class Graph {
        private final Interpreter interpreter;
        @Nullable private final TaiXnnpackDelegate delegate;
        private final File file;
        private final int threads;
        private volatile boolean closed;

        private Graph(@NonNull Interpreter interpreter, @Nullable TaiXnnpackDelegate delegate,
                      @NonNull File file, int threads) {
            this.interpreter = interpreter;
            this.delegate = delegate;
            this.file = file;
            this.threads = threads;
        }

        @NonNull
        static Graph open(@NonNull File file, int threads, boolean useDelegate) throws IOException {
            TaiXnnpackDelegate delegate = useDelegate ? TaiXnnpackDelegate.create(threads) : null;
            Interpreter.Options options = new Interpreter.Options().setNumThreads(threads).setCancellable(true);
            if (delegate != null) options.setUseXNNPACK(false).addDelegate(delegate);
            else options.setUseXNNPACK(useDelegate);
            try {
                Graph graph = new Graph(new Interpreter(file, options), delegate, file, threads);
                Tensor input = graph.interpreter.getInputTensor(0);
                int[] shape = input.shape();
                if (shape.length != 4 || shape[0] != 1 || shape[1] != 3 || input.dataType() != DataType.FLOAT32) {
                    graph.close();
                    throw new IOException(file.getName() + ": unexpected input " + Arrays.toString(shape));
                }
                if (graph.interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32) {
                    graph.close();
                    throw new IOException(file.getName() + ": output is not float32");
                }
                return graph;
            } catch (RuntimeException e) {
                if (delegate != null) delegate.close();
                throw new IOException(file.getName() + ": " + e.getMessage(), e);
            }
        }

        /** {@code [width, height]} of the NCHW input. */
        @NonNull
        int[] inputSize() {
            int[] shape = interpreter.getInputTensor(0).shape();
            return new int[] {shape[3], shape[2]};
        }

        @NonNull
        int[] outputShape() {
            return interpreter.getOutputTensor(0).shape();
        }

        /** {@code [height, width]} of the output's last two axes ({@code [1,1,H,W]} and {@code [1,H,W]} alike). */
        @NonNull
        int[] outputPlane() {
            int[] shape = outputShape();
            return new int[] {shape[shape.length - 2], shape[shape.length - 1]};
        }

        /**
         * Runs on {@code input} (planar floats) and copies the output out. A delegated run that
         * fails for anything but a cancellation is retried once on the stock interpreter, and the
         * runtime stops using the delegate from then on.
         */
        @NonNull
        float[] run(@NonNull float[] input, @NonNull WallpaperVisionRuntime owner) throws Exception {
            // runOnce clears the interpreter's cancel flag, so a cancel that landed before it is checked here.
            if (owner.cancelRequested) throw new CancelledException();
            try {
                return runOnce(input);
            } catch (RuntimeException e) {
                if (owner.cancelRequested) throw new CancelledException();
                if (delegate == null) throw e;
                Log.w(TAG, file.getName() + ": delegated run failed, retrying without XNNPACK", e);
                owner.useDelegate = false;
                Graph plain = open(file, threads, false);
                owner.current = plain;
                try {
                    return plain.runOnce(input);
                } catch (RuntimeException second) {
                    if (owner.cancelRequested) throw new CancelledException();
                    throw second;
                } finally {
                    owner.current = this;
                    plain.close();
                }
            }
        }

        @NonNull
        private float[] runOnce(@NonNull float[] input) {
            ByteBuffer in = ByteBuffer.allocateDirect(input.length * 4).order(ByteOrder.nativeOrder());
            in.asFloatBuffer().put(input);
            in.rewind();
            Tensor outTensor = interpreter.getOutputTensor(0);
            ByteBuffer out = ByteBuffer.allocateDirect(outTensor.numBytes()).order(ByteOrder.nativeOrder());
            interpreter.setCancelled(false);
            interpreter.run(in, out);
            out.rewind();
            FloatBuffer floats = out.asFloatBuffer();
            float[] result = new float[floats.remaining()];
            floats.get(result);
            return result;
        }

        void cancel() {
            if (closed) return;
            try {
                interpreter.setCancelled(true);
            } catch (RuntimeException ignored) {
                // Closed under us: the between-stage check still stops the run.
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            interpreter.close();
            if (delegate != null) delegate.close();
        }
    }
}
