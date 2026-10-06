package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.TapGeometry;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Host side of the keyboard's tap correction: owns the {@link TapModelStore}, knows which layout
 * is on screen and whether the feature is on, and is the {@link Keyboard2View.TapResolver} the
 * view consults. Everything here runs on the main thread except the file read and write.
 *
 * <p>The file is read on the I/O executor and swapped in on the main thread. Until it arrives the
 * controller passes presses through and learns nothing, exactly as if the feature were off, so a
 * tap in those few milliseconds is never corrected by, or recorded into, a model about to be
 * replaced.
 *
 * <p>Off means off: with the feature disabled the view's presses pass through untouched and
 * nothing is recorded.
 */
public final class TapCorrectionController implements Keyboard2View.TapResolver {

    static final String FILE_NAME = "keyboard-tap-model.json";
    /** How long after the last tap the model is written out. */
    static final long SAVE_DELAY_MS = 5_000L;
    private static final String LOG_TAG = "TapCorrection";

    private final File mFile;
    private final Executor mIoExecutor;
    private final Handler mMainHandler;
    private final Runnable mSaveRunnable = this::flush;

    private TapModelStore mStore = new TapModelStore();
    private boolean mEnabled;
    private String mLayoutId = "";
    private boolean mSaveScheduled;
    /** True from asking for the file until its contents are swapped in. */
    private boolean mLoading;
    /** Bumped by every load and reset, so a load overtaken by either is dropped on arrival. */
    private int mLoadGeneration;
    /** Saves handed to the executor and not finished yet. Touched from both threads. */
    private final AtomicInteger mSavesInFlight = new AtomicInteger();

    /** The file the learned model lives in, shared with the settings screen. */
    @NonNull
    public static File modelFile(@NonNull Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public TapCorrectionController(@NonNull File file, @NonNull Executor ioExecutor,
                                   @NonNull Handler mainHandler) {
        mFile = Objects.requireNonNull(file, "file");
        mIoExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        mMainHandler = Objects.requireNonNull(mainHandler, "mainHandler");
        load();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /** Turns the feature on or off; turning it off writes out whatever was learned. */
    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled)
            return;
        mEnabled = enabled;
        if (!enabled)
            flush();
    }

    /** The layout the view is showing; part of the key each model is stored under. */
    public void setLayoutId(@Nullable String layoutId) {
        mLayoutId = layoutId == null ? "" : layoutId;
    }

    /**
     * Re-reads the file so a reset made in Settings, or a store written by another instance,
     * takes effect. Anything unsaved here is written first so it is not lost.
     *
     * <p>While a save of ours is still on its way to the file, the file is about to hold exactly
     * what is in memory, so memory is kept rather than reading a file that has not caught up:
     * reading it then would drop the taps that save carries.
     */
    public void reload() {
        flush();
        if (mSavesInFlight.get() > 0 && !mLoading)
            return;
        load();
    }

    /** Forgets everything learned and removes the file. */
    public void reset() {
        mMainHandler.removeCallbacks(mSaveRunnable);
        mSaveScheduled = false;
        // A load still on its way would bring back what this forgets.
        mLoadGeneration++;
        mLoading = false;
        mStore.clear();
        mStore.toJson();
        File file = mFile;
        mIoExecutor.execute(() -> TapModelStore.delete(file));
    }

    /** Total taps learned across every stored geometry. */
    public float totalTaps() {
        return mStore.totalTaps();
    }

    /** Writes the store out now if anything changed. */
    public void flush() {
        mMainHandler.removeCallbacks(mSaveRunnable);
        mSaveScheduled = false;
        if (!mStore.isDirty())
            return;
        String json = mStore.toJson();
        File file = mFile;
        mSavesInFlight.incrementAndGet();
        try {
            mIoExecutor.execute(() -> {
                try {
                    TapModelStore.write(file, json);
                } catch (IOException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the tap model", e);
                } finally {
                    mSavesInFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            mSavesInFlight.decrementAndGet();
            throw e;
        }
    }

    /** Reads the file on the I/O executor and swaps it in on the main thread. */
    private void load() {
        mLoading = true;
        final int generation = ++mLoadGeneration;
        File file = mFile;
        mIoExecutor.execute(() -> {
            TapModelStore loaded = TapModelStore.load(file);
            Runnable swap = () -> {
                if (generation != mLoadGeneration)
                    return;
                mStore = loaded;
                mLoading = false;
            };
            // An executor that ran this on the main thread (a direct one) swaps at once.
            if (Looper.myLooper() == mMainHandler.getLooper()) swap.run();
            else mMainHandler.post(swap);
        });
    }

    @Override
    public int resolveTap(TapGeometry geometry, int rawIndex, float x, float y) {
        if (!mEnabled || mLoading)
            return rawIndex;
        return model(geometry).resolve(geometry, rawIndex, x, y);
    }

    @Override
    public void observeTap(TapGeometry geometry, int rawIndex, float x, float y,
                           boolean swiped) {
        if (!mEnabled || swiped || mLoading)
            return;
        TapModel model = model(geometry);
        model.observe(geometry, rawIndex, x, y, false);
        mStore.markDirty();
        scheduleSave();
    }

    /** Visible for tests: the model the current layout and geometry resolve against. */
    @NonNull
    TapModel model(@NonNull TapGeometry geometry) {
        return mStore.modelFor(mLayoutId + "|" + geometry.signature, geometry.keyCount,
            System.currentTimeMillis());
    }

    private void scheduleSave() {
        if (mSaveScheduled)
            return;
        mSaveScheduled = true;
        mMainHandler.postDelayed(mSaveRunnable, SAVE_DELAY_MS);
    }
}
