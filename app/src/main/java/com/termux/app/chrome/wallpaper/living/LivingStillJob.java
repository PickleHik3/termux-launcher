package com.termux.app.chrome.wallpaper.living;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The one living-still build running in the process (living-stills.md, Part D.6): the vision
 * analysis of a photo (depth, scene, subject), then {@link LivingStillBuilder} (clusters, the
 * optional Gemma step, masks, recipe), reported as one progress from 0 to 100. The job belongs to
 * the process, not to the wallpaper page: a page attaches a {@link Listener} when it opens and
 * detaches when it closes, a run goes on without it, and a page that opens again is told where the
 * run is. One run at a time; it can be cancelled.
 *
 * <p>The analysis folder is {@code cacheDir/living-analysis/<hash>/} and is deleted when the run
 * ends, whatever the outcome. The real work sits behind {@link Engine} so the listener and
 * cancel logic is tested with fakes; {@link #get} wires the TAI runtime and the builder.
 * Listeners are called on the {@code main} executor (the main thread in the app).</p>
 */
public final class LivingStillJob {

    /** The analysis stages {@code TaiManager.analyzeWallpaper} reports, and the builder's own. */
    public static final String STAGE_DEPTH = "depth";
    public static final String STAGE_SCENE = "scene";
    public static final String STAGE_SUBJECT = "subject";
    public static final String STAGE_RECIPE = LivingStillBuilder.STAGE_RECIPE;

    /** How much of the whole bar each stage takes, in percent; they add up to 100. */
    static final int WEIGHT_DEPTH = 40;
    static final int WEIGHT_SCENE = 10;
    static final int WEIGHT_SUBJECT = 20;
    static final int WEIGHT_RECIPE = 30;

    /** Within the recipe stage: the Gemma step runs between these percents ({@link LivingStillBuilder}). */
    static final int GEMMA_FROM = 15;
    static final int GEMMA_TO = 75;

    /** {@code percent} (0..100) of {@code stage} as a share of the whole run, or -1 for an unknown stage. */
    public static int overallPercent(@NonNull String stage, int percent) {
        int p = Math.max(0, Math.min(100, percent));
        switch (stage) {
            case STAGE_DEPTH: return p * WEIGHT_DEPTH / 100;
            case STAGE_SCENE: return WEIGHT_DEPTH + p * WEIGHT_SCENE / 100;
            case STAGE_SUBJECT: return WEIGHT_DEPTH + WEIGHT_SCENE + p * WEIGHT_SUBJECT / 100;
            case STAGE_RECIPE: return WEIGHT_DEPTH + WEIGHT_SCENE + WEIGHT_SUBJECT + p * WEIGHT_RECIPE / 100;
            default: return -1;
        }
    }

    /** Where the run is. */
    public static final class Progress {
        @NonNull public final File photo;
        @NonNull public final String stage;
        public final int stagePercent;
        /** 0..100 across the whole run; never goes back. */
        public final int overallPercent;
        /** The Gemma step is part of this run. */
        public final boolean gemma;

        Progress(@NonNull File photo, @NonNull String stage, int stagePercent, int overallPercent, boolean gemma) {
            this.photo = photo;
            this.stage = stage;
            this.stagePercent = stagePercent;
            this.overallPercent = overallPercent;
            this.gemma = gemma;
        }

        /** The builder is inside its Gemma step. */
        public boolean asksGemma() {
            return gemma && STAGE_RECIPE.equals(stage) && stagePercent >= GEMMA_FROM && stagePercent < GEMMA_TO;
        }
    }

    /** How a run ended. */
    public static final class Result {
        @NonNull public final File photo;
        /** The finished manifest, or null when the run was cancelled or failed. */
        @Nullable public final Manifest manifest;
        public final boolean cancelled;
        /** A stable error code ({@code vision_model_missing}, {@code read_failed}, ...), or null. */
        @Nullable public final String error;
        @NonNull public final String message;

        Result(@NonNull File photo, @Nullable Manifest manifest, boolean cancelled, @Nullable String error,
               @NonNull String message) {
            this.photo = photo;
            this.manifest = manifest;
            this.cancelled = cancelled;
            this.error = error;
            this.message = message;
        }

        public boolean ok() {
            return manifest != null;
        }
    }

    /** Called on the main executor. */
    public interface Listener {
        void onProgress(@NonNull Progress progress);

        void onFinished(@NonNull Result result);
    }

    /** How the vision analysis ended. */
    public static final class Outcome {
        public final boolean ok;
        public final boolean cancelled;
        @Nullable public final String error;
        @NonNull public final String message;

        public Outcome(boolean ok, boolean cancelled, @Nullable String error, @NonNull String message) {
            this.ok = ok;
            this.cancelled = cancelled;
            this.error = error;
            this.message = message;
        }
    }

    /** Receives each analysis stage and its 0-100 progress, on the job's thread. */
    public interface StageSink {
        void onStage(@NonNull String stage, int percent);
    }

    /** The two heavy halves and the cancel, so the job runs without a phone. */
    public interface Engine {
        /** Depth, scene and subject maps of {@code photo} into {@code outDir}. Blocking. */
        @NonNull Outcome analyze(@NonNull File photo, @NonNull File outDir, @NonNull StageSink sink);

        /** The manifest from the maps in {@code outDir}. Blocking; throws {@link CancellationException} when cancelled. */
        @NonNull Manifest build(@NonNull File photo, @NonNull File outDir, @NonNull LivingStillBuilder.Progress progress)
            throws IOException;

        /** Stops the analysis in flight. Called off the job's thread; may block briefly. */
        void cancelAnalysis();

        /** The Gemma step will run in the build. */
        boolean usesGemma();
    }

    private static LivingStillJob sInstance;

    /** The process's job, wired to the TAI vision runtime and {@link LivingStillBuilder}. */
    @NonNull
    public static synchronized LivingStillJob get(@NonNull Context context) {
        if (sInstance == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            sInstance = new LivingStillJob(new TaiLivingEngine(app),
                Executors.newSingleThreadExecutor(r -> daemon(r, "living-still-job")),
                main::post,
                Executors.newSingleThreadExecutor(r -> daemon(r, "living-still-cancel")),
                new File(app.getCacheDir(), "living-analysis"));
        }
        return sInstance;
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    @NonNull private final Engine mEngine;
    @NonNull private final Executor mWorker;
    @NonNull private final Executor mMain;
    @NonNull private final Executor mCanceller;
    @NonNull private final File mAnalysisRoot;
    @NonNull private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();

    @Nullable private File mRunning;
    @Nullable private Progress mLast;
    private volatile boolean mCancelled;

    /**
     * @param worker       runs the job, one at a time
     * @param main         receives listener calls
     * @param canceller    runs {@link Engine#cancelAnalysis}, which must not wait behind the job
     * @param analysisRoot where each run's analysis folder lives; deleted after the run
     */
    public LivingStillJob(@NonNull Engine engine, @NonNull Executor worker, @NonNull Executor main,
                          @NonNull Executor canceller, @NonNull File analysisRoot) {
        mEngine = engine;
        mWorker = worker;
        mMain = main;
        mCanceller = canceller;
        mAnalysisRoot = analysisRoot;
    }

    /**
     * Starts building {@code photo}'s living still (again, when it has one).
     *
     * @return false when a run is already going; its progress goes to the same listeners
     */
    public boolean start(@NonNull File photo) {
        synchronized (this) {
            if (mRunning != null) return false;
            mRunning = photo;
            mCancelled = false;
            mLast = null;
        }
        mWorker.execute(() -> run(photo));
        return true;
    }

    /** Asks the run to stop: the analysis between and inside its graphs, the build between its steps. */
    public void cancel() {
        synchronized (this) {
            if (mRunning == null) return;
            mCancelled = true;
        }
        mCanceller.execute(mEngine::cancelAnalysis);
    }

    public synchronized boolean isRunning() {
        return mRunning != null;
    }

    /** The photo being built, or null. */
    @Nullable
    public synchronized File runningPhoto() {
        return mRunning;
    }

    /** The last progress of the run in flight, or null. */
    @Nullable
    public synchronized Progress lastProgress() {
        return mLast;
    }

    /** The Gemma step is part of runs started now. */
    public boolean usesGemma() {
        return mEngine.usesGemma();
    }

    /** Adds a listener; a run in flight reports its latest progress to it at once. */
    public void attach(@NonNull Listener listener) {
        mListeners.addIfAbsent(listener);
        final Progress last;
        synchronized (this) {
            last = mRunning == null ? null : mLast;
        }
        if (last != null) mMain.execute(() -> {
            if (mListeners.contains(listener)) listener.onProgress(last);
        });
    }

    public void detach(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    // --- the run, on the worker ---

    private void run(@NonNull File photo) {
        File dir = null;
        try {
            String hash;
            try {
                hash = LivingStills.hash16(photo);
            } catch (IOException e) {
                finish(new Result(photo, null, false, "read_failed", message(e)));
                return;
            }
            dir = new File(mAnalysisRoot, hash);
            deleteRecursive(dir);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                finish(new Result(photo, null, false, "write_failed", "Cannot create " + dir));
                return;
            }
            final boolean gemma = mEngine.usesGemma();
            publish(photo, STAGE_DEPTH, 0, gemma);
            Outcome outcome = mEngine.analyze(photo, dir, (stage, percent) -> publish(photo, stage, percent, gemma));
            if (mCancelled || outcome.cancelled) {
                finish(new Result(photo, null, true, "cancelled", ""));
                return;
            }
            if (!outcome.ok) {
                finish(new Result(photo, null, false, outcome.error == null ? "vision_failed" : outcome.error,
                    outcome.message));
                return;
            }
            Manifest manifest = mEngine.build(photo, dir, new LivingStillBuilder.Progress() {
                @Override public void onProgress(@NonNull String stage, int percent) {
                    publish(photo, stage, percent, gemma);
                }

                @Override public boolean isCancelled() {
                    return mCancelled;
                }
            });
            finish(new Result(photo, manifest, false, null, ""));
        } catch (CancellationException e) {
            finish(new Result(photo, null, true, "cancelled", ""));
        } catch (IOException | RuntimeException e) {
            finish(new Result(photo, null, false, "build_failed", message(e)));
        } finally {
            if (dir != null) deleteRecursive(dir);
        }
    }

    private void publish(@NonNull File photo, @NonNull String stage, int percent, boolean gemma) {
        int overall = overallPercent(stage, percent);
        if (overall < 0) return;
        final Progress progress;
        synchronized (this) {
            if (mRunning == null) return;
            // The bar never goes back, whatever order the stages report in.
            if (mLast != null && overall < mLast.overallPercent) overall = mLast.overallPercent;
            progress = new Progress(photo, stage, Math.max(0, Math.min(100, percent)), overall, gemma);
            mLast = progress;
        }
        mMain.execute(() -> {
            for (Listener l : mListeners) l.onProgress(progress);
        });
    }

    private void finish(@NonNull Result result) {
        synchronized (this) {
            mRunning = null;
            mLast = null;
            mCancelled = false;
        }
        mMain.execute(() -> {
            for (Listener l : mListeners) l.onFinished(result);
        });
    }

    @NonNull
    private static String message(@NonNull Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void deleteRecursive(@NonNull File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursive(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
