package com.termux.ai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Runs one depth model once over one photo and writes an 8-bit grey PNG (white = near) at the
 * photo's aspect ({@link DepthMath}). One instance is one run: build it, call {@link #make} off the
 * main thread, and {@link #cancel} from anywhere. The graph is opened, run and closed inside
 * {@link #make}; nothing from it stays resident, and there is no static cache.
 *
 * <p>Cancel is checked before the load, after it, after the run and before the rename, and it also
 * stops a graph that is running ({@link Graph#cancel}). A cancelled or failed run leaves no file.
 */
public final class DepthMaker {
    /** Result codes; stable, for the caller to map to words. */
    public static final String CODE_OK = "ok";
    public static final String CODE_CANCELLED = "cancelled";
    public static final String CODE_PHOTO_UNREADABLE = "photo_unreadable";
    public static final String CODE_MODEL_UNUSABLE = "model_unusable";
    public static final String CODE_RUN_FAILED = "run_failed";
    public static final String CODE_WRITE_FAILED = "write_failed";
    public static final String CODE_OUT_OF_MEMORY = "out_of_memory";

    public static final String STAGE_LOADING = "loading";
    public static final String STAGE_RUNNING = "running";
    public static final String STAGE_WRITING = "writing";

    /** The decoded photo keeps at least this many pixels on its short side; bigger photos are sub-sampled on decode. */
    static final int MIN_DECODED_SHORT_SIDE = 1024;

    /** Stage in flight and a 0..100 share of the whole run, from the job thread. */
    public interface Progress {
        void onStage(@NonNull String stage, int percent);
    }

    /** How a run ended. {@code code} is one of the {@code CODE_} constants. */
    public static final class Result {
        public final boolean ok;
        public final boolean cancelled;
        @NonNull public final String code;
        @NonNull public final String message;

        Result(boolean ok, boolean cancelled, @NonNull String code, @NonNull String message) {
            this.ok = ok;
            this.cancelled = cancelled;
            this.code = code;
            this.message = message;
        }
    }

    /** One static-shape graph: a float32 NCHW input in, a float32 output out. Used once and closed. */
    interface Graph {
        /** {@code [width, height]} of the NCHW input. */
        @NonNull int[] inputSize();

        /** {@code [height, width]} of the output's last two axes. */
        @NonNull int[] outputPlane();

        /** Runs on planar input floats and returns the output floats. */
        @NonNull float[] run(@NonNull float[] nchw) throws IOException;

        /** Stops a run in flight; safe from any thread, a no-op once closed. */
        void cancel();

        void close();
    }

    /** Opens the graph in {@code model}, checking its input is {@code [1,3,H,W]} float32 and its output float32. */
    interface GraphOpener {
        @NonNull Graph open(@NonNull File model) throws IOException;
    }

    /** The decoded photo: packed ARGB, row-major. */
    static final class Photo {
        final int[] argb;
        final int width;
        final int height;

        Photo(@NonNull int[] argb, int width, int height) {
            this.argb = argb;
            this.width = width;
            this.height = height;
        }
    }

    interface PhotoDecoder {
        @NonNull Photo decode(@NonNull File photo) throws IOException;
    }

    private final GraphOpener opener;
    private final PhotoDecoder decoder;
    private volatile boolean cancelRequested;
    @Nullable private volatile Graph current;

    /** A maker on the real LiteRT graph and Android's bitmap decoder. */
    public DepthMaker() {
        this(new InterpreterOpener(), DepthMaker::decodePhoto);
    }

    DepthMaker(@NonNull GraphOpener opener, @NonNull PhotoDecoder decoder) {
        this.opener = opener;
        this.decoder = decoder;
    }

    /** Stops the run at its next check and inside a running graph. Safe from any thread. */
    public void cancel() {
        cancelRequested = true;
        Graph graph = current;
        if (graph != null) graph.cancel();
    }

    /**
     * Makes the depth image of {@code photo} with {@code model} and writes it to {@code out}
     * (through {@code out.tmp}, then a rename). {@code distance} is true for a model whose output
     * is distance ({@link TaiDepthModels#isDistance}). Never throws for a model or photo problem.
     */
    @NonNull
    public Result make(@NonNull File photo, @NonNull File model, boolean distance, @NonNull File out,
                       @NonNull Progress progress) {
        File tmp = new File(out.getPath() + ".tmp");
        Graph graph = null;
        boolean renamed = false;
        try {
            if (cancelRequested) return cancelled();
            progress.onStage(STAGE_LOADING, 0);
            Photo decoded;
            try {
                decoded = decoder.decode(photo);
            } catch (IOException e) {
                return failed(CODE_PHOTO_UNREADABLE, e);
            }
            if (cancelRequested) return cancelled();
            try {
                graph = opener.open(model);
            } catch (IOException | RuntimeException e) {
                return failed(CODE_MODEL_UNUSABLE, e);
            }
            current = graph;
            if (cancelRequested) return cancelled();
            progress.onStage(STAGE_RUNNING, 25);
            int[] in = graph.inputSize();
            float[] hwc = DepthMath.resizeRgb(decoded.argb, decoded.width, decoded.height, in[0], in[1],
                DepthMath.Filter.BICUBIC);
            float[] raw;
            int[] plane = graph.outputPlane();
            try {
                raw = graph.run(DepthMath.toNchw(hwc, in[0], in[1]));
            } catch (IOException | RuntimeException e) {
                if (cancelRequested) return cancelled();
                return failed(CODE_RUN_FAILED, e);
            }
            if (cancelRequested) return cancelled();
            if (raw.length != plane[0] * plane[1]) {
                return failed(CODE_RUN_FAILED, new IOException(
                    "Depth output " + raw.length + " does not match " + plane[0] + "x" + plane[1]));
            }
            progress.onStage(STAGE_WRITING, 90);
            byte[] png = DepthMath.depthPng(raw, plane[1], plane[0], distance, decoded.width, decoded.height);
            // The graph is done with: close it before the file is written, so nothing outlives the run.
            current = null;
            graph.close();
            graph = null;
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return failed(CODE_WRITE_FAILED, new IOException("Could not create " + dir));
            }
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                stream.write(png);
            } catch (IOException e) {
                return failed(CODE_WRITE_FAILED, e);
            }
            if (cancelRequested) return cancelled();
            if (out.exists() && !out.delete()) {
                return failed(CODE_WRITE_FAILED, new IOException("Could not replace " + out.getName()));
            }
            if (!tmp.renameTo(out)) {
                return failed(CODE_WRITE_FAILED, new IOException("Could not write " + out.getName()));
            }
            renamed = true;
            progress.onStage(STAGE_WRITING, 100);
            return new Result(true, false, CODE_OK, "");
        } catch (OutOfMemoryError e) {
            return new Result(false, false, CODE_OUT_OF_MEMORY, "Not enough memory to make the depth image.");
        } finally {
            current = null;
            if (graph != null) graph.close();
            if (!renamed) tmp.delete();
        }
    }

    @NonNull
    private static Result cancelled() {
        return new Result(false, true, CODE_CANCELLED, "Cancelled.");
    }

    @NonNull
    private static Result failed(@NonNull String code, @NonNull Throwable t) {
        String message = t.getMessage() == null || t.getMessage().trim().isEmpty()
            ? t.getClass().getSimpleName() : t.getMessage();
        return new Result(false, false, code, message);
    }

    // ---- The photo -------------------------------------------------------------------------

    /** The power-of-two sample size that keeps both sides at or above {@link #MIN_DECODED_SHORT_SIDE}. */
    static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / (sample * 2) >= MIN_DECODED_SHORT_SIDE && height / (sample * 2) >= MIN_DECODED_SHORT_SIDE) {
            sample *= 2;
        }
        return sample;
    }

    @NonNull
    private static Photo decodePhoto(@NonNull File file) throws IOException {
        if (!file.isFile() || !file.canRead()) throw new IOException("Cannot read " + file.getName());
        String path = file.getPath();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Not an image: " + file.getName());
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IOException("Could not decode " + file.getName());
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

    // ---- The real graph --------------------------------------------------------------------

    /** The stock LiteRT interpreter: XNNPACK on, {@code min(4, cores)} threads, cancellable. */
    private static final class InterpreterOpener implements GraphOpener {
        @NonNull
        @Override
        public Graph open(@NonNull File model) throws IOException {
            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
            Interpreter.Options options = new Interpreter.Options()
                .setNumThreads(threads).setUseXNNPACK(true).setCancellable(true);
            Interpreter interpreter = null;
            try {
                interpreter = new Interpreter(model, options);
                Tensor input = interpreter.getInputTensor(0);
                int[] shape = input.shape();
                if (shape.length != 4 || shape[0] != 1 || shape[1] != 3 || input.dataType() != DataType.FLOAT32) {
                    throw new IOException(model.getName() + ": unexpected input " + Arrays.toString(shape));
                }
                if (interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32) {
                    throw new IOException(model.getName() + ": output is not float32");
                }
                return new InterpreterGraphRun(interpreter);
            } catch (IOException e) {
                if (interpreter != null) interpreter.close();
                throw e;
            } catch (RuntimeException e) {
                if (interpreter != null) interpreter.close();
                throw new IOException(model.getName() + ": " + e.getMessage(), e);
            }
        }
    }

    private static final class InterpreterGraphRun implements Graph {
        private final Interpreter interpreter;
        private volatile boolean closed;

        InterpreterGraphRun(@NonNull Interpreter interpreter) {
            this.interpreter = interpreter;
        }

        @NonNull
        @Override
        public int[] inputSize() {
            int[] shape = interpreter.getInputTensor(0).shape();
            return new int[] {shape[3], shape[2]};
        }

        @NonNull
        @Override
        public int[] outputPlane() {
            int[] shape = interpreter.getOutputTensor(0).shape();
            return new int[] {shape[shape.length - 2], shape[shape.length - 1]};
        }

        @NonNull
        @Override
        public float[] run(@NonNull float[] input) throws IOException {
            ByteBuffer in = ByteBuffer.allocateDirect(input.length * 4).order(ByteOrder.nativeOrder());
            in.asFloatBuffer().put(input);
            in.rewind();
            ByteBuffer out = ByteBuffer.allocateDirect(interpreter.getOutputTensor(0).numBytes())
                .order(ByteOrder.nativeOrder());
            try {
                // run clears the cancel flag; the maker checks its own flag before and after.
                interpreter.setCancelled(false);
                interpreter.run(in, out);
            } catch (RuntimeException e) {
                throw new IOException(e.getMessage(), e);
            }
            out.rewind();
            FloatBuffer floats = out.asFloatBuffer();
            float[] result = new float[floats.remaining()];
            floats.get(result);
            return result;
        }

        @Override
        public void cancel() {
            if (closed) return;
            try {
                interpreter.setCancelled(true);
            } catch (RuntimeException ignored) {
                // Closed under us: the between-stage checks still stop the run.
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            interpreter.close();
        }
    }
}
