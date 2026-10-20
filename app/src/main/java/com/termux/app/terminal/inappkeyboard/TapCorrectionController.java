package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.inappkeyboard.touch.GridMemory;
import com.termux.app.terminal.inappkeyboard.touch.Tap;
import com.termux.app.terminal.inappkeyboard.touch.TouchEngine;
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
 * Host side of the keyboard's touch engine: owns the {@link TapModelStore}, knows which layout
 * is on screen and whether the feature is on, and is the {@link Keyboard2View.TapResolver} the
 * view consults. Everything here runs on the main thread except the file read and write. The
 * engine itself ({@link TouchEngine}) is pure; this class only feeds it.
 *
 * <p>The file is read on the I/O executor and swapped in on the main thread. Until it arrives the
 * controller passes presses through and learns nothing, exactly as if the feature were off, so a
 * tap in those few milliseconds is never corrected by, or recorded into, a store about to be
 * replaced.
 *
 * <p>Off means off: with the feature disabled the view's presses pass through untouched and
 * nothing is recorded.
 */
public final class TapCorrectionController implements Keyboard2View.TapResolver {

    static final String FILE_NAME = "keyboard-tap-model.json";
    /** How long after the first unsaved tap the store is written out. */
    static final long SAVE_DELAY_MS = 5_000L;
    private static final String LOG_TAG = "TapCorrection";

    private final File mFile;
    private final Executor mIoExecutor;
    private final Handler mMainHandler;
    private final Runnable mSaveRunnable = this::save;

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

    /**
     * Turns the feature on or off. Turning it on starts a warm-up; turning it off writes out
     * whatever was learned.
     */
    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled)
            return;
        mEnabled = enabled;
        syncSwitch();
        if (!enabled)
            flush();
    }

    /** The layout the view is showing; part of the key each grid is stored under. */
    public void setLayoutId(@Nullable String layoutId) {
        mLayoutId = layoutId == null ? "" : layoutId;
    }

    /**
     * Something other than a plain tap on these keys reached the terminal — a paste, a hardware
     * key, the keyboard going away — so no take-back can span it.
     */
    public void interrupt() {
        if (settleHeldTap())
            scheduleSave();
    }

    /** Makes the taps the engine holds final; returns whether that learned anything. */
    private boolean settleHeldTap() {
        if (mLoading)
            return false;
        TouchEngine engine = mStore.engine();
        long before = engine.tapsLearned();
        engine.interrupt();
        if (engine.tapsLearned() == before)
            return false;
        mStore.markDirty();
        return true;
    }

    /**
     * Re-reads the file so a reset or resume made in Settings, or a store written by another
     * instance, takes effect. Anything unsaved here is written first so it is not lost.
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
        // Nothing is left to save; the snapshot only clears the dirty flag.
        mStore.snapshot();
        syncSwitch();
        File file = mFile;
        mIoExecutor.execute(() -> TapModelStore.delete(file));
    }

    /** Total weight learned across every stored geometry. */
    public float totalTaps() {
        return mStore.totalTaps();
    }

    /** Visible for tests: the store the controller is running on. */
    @NonNull
    TapModelStore store() {
        return mStore;
    }

    /**
     * Makes the taps still held final and writes the store out now if anything
     * changed. For the keyboard going away, not for the periodic save, which must not end a
     * take-back in progress.
     */
    public void flush() {
        settleHeldTap();
        save();
    }

    private void save() {
        mMainHandler.removeCallbacks(mSaveRunnable);
        mSaveScheduled = false;
        if (!mStore.isDirty())
            return;
        // Copy here, serialise and write there: building the file text is the slow part.
        TapModelStore.Snapshot snapshot = mStore.snapshot();
        File file = mFile;
        mSavesInFlight.incrementAndGet();
        try {
            mIoExecutor.execute(() -> {
                try {
                    TapModelStore.write(file, snapshot.toJson());
                } catch (IOException | RuntimeException e) {
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
                syncSwitch();
            };
            // An executor that ran this on the main thread (a direct one) swaps at once.
            if (Looper.myLooper() == mMainHandler.getLooper()) swap.run();
            else mMainHandler.post(swap);
        });
    }

    /** Tells the engine where the switch stands; a switch turned on starts a warm-up. */
    private void syncSwitch() {
        if (mLoading)
            return;
        if (mStore.engine().onSwitch(mEnabled)) {
            mStore.markDirty();
            if (mEnabled)
                scheduleSave();
        }
    }

    @Override
    public int resolveTap(TapGeometry geometry, int rawIndex, float x, float y) {
        if (!mEnabled || mLoading || geometry == null)
            return rawIndex;
        return mStore.engine().resolve(grid(geometry), geometry, rawIndex, x, y);
    }

    @Override
    public void observeTap(TapGeometry geometry, Keyboard2View.TapRelease release) {
        if (!mEnabled || mLoading || geometry == null || release == null)
            return;
        mStore.engine().onRelease(grid(geometry), geometry, new Tap(release.rawIndex,
            release.committedIndex, release.x, release.y, release.plain, release.modified,
            release.downAtMs, release.upAtMs, release.contactSize));
        mStore.markDirty();
        scheduleSave();
    }

    /** Visible for tests: what is learned for the current layout and geometry. */
    @NonNull
    GridMemory grid(@NonNull TapGeometry geometry) {
        return mStore.gridFor(mLayoutId + "|" + geometry.signature, geometry.keyCount,
            System.currentTimeMillis());
    }

    private void scheduleSave() {
        if (mSaveScheduled)
            return;
        mSaveScheduled = true;
        mMainHandler.postDelayed(mSaveRunnable, SAVE_DELAY_MS);
    }
}
