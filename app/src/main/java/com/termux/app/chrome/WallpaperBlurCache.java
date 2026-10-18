package com.termux.app.chrome;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Trace;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The one pre-blurred wallpaper frame every glass surface samples.
 *
 * <p>Pre-blurred wallpaper frames shared by the dock, the keyboard, the gesture-nav strip, the
 * top bars, the panes and the planes — one frame per requested blur radius, LRU-capped. Surfaces
 * are tuned independently (dock and status frost carry their own radius sliders); the previous
 * single-slot cache was invalidated by every radius alternation, re-decoding and re-blurring the
 * wallpaper on the main thread two or three times on every return home (1-3s of dropped
 * frames).</p>
 *
 * <p>A frame is held at the resolution it was blurred at and drawn scaled up through a shader
 * aimed at each surface's live position ({@link SharedFrameDrawable}); geometry changes re-aim,
 * they never cut a copy. The frame rect ({@link #frameRectRef()}) says which screen rect a frame
 * stands for, whatever its pixel size.</p>
 */
public final class WallpaperBlurCache {

    /** Everything the cache needs from the outside world: the wallpaper, and who is using a frame. */
    public interface Source {

        /** The wallpaper frame's rect in screen coordinates — the full-frame crop's geometry. */
        @NonNull Rect wallpaperFrameRect();

        /** True while the managed (app-owned) wallpaper file is the blur source. */
        boolean useManagedWallpaperSource();

        /** The system wallpaper's current id; a change to it invalidates every cached frame. */
        int systemWallpaperId();

        /** The managed wallpaper file, consulted for its size/mtime identity. */
        @NonNull File managedWallpaperExactFile();

        /**
         * The managed file's {@code lastModified()}, part of the identity the frames were cut for.
         * A source that already holds the answer overrides this so an obtain costs no file stat.
         */
        default long managedWallpaperLastModified() {
            return managedWallpaperExactFile().lastModified();
        }

        /** The managed file's {@code length()}; see {@link #managedWallpaperLastModified()}. */
        default long managedWallpaperLength() {
            return managedWallpaperExactFile().length();
        }

        /** The configuration orientation the next capture would be taken in. */
        int orientation();

        /** Captures the unblurred wallpaper region for {@code frameRect}. */
        /**
         * Main thread. Everything a capture of {@code frameRect} needs: a bitmap already in hand,
         * or a decode the blur worker runs. Null when nothing can be captured right now.
         */
        @Nullable FrameCapture beginCapture(@NonNull Rect frameRect, @NonNull View wallpaperFrame);

        /** Blurs a captured frame; may return {@code sourceBitmap} itself when the radius is 0. */
        @Nullable Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp);

        /**
         * True while some view still draws this exact bitmap. Only a capture that was never handed
         * out as a frame is ever recycled, and this is asked first all the same: recycling a
         * bitmap a view holds crashes its next draw.
         */
        boolean isFrameInUse(@Nullable Bitmap frame);

        /** Called after the cache is emptied, for the state that shadows it outside the module. */
        void onCacheCleared();

        /**
         * The Vibrancy the frames are baked at, percent ({@link GlassVibrancy}); 100, the default,
         * bakes nothing. A change re-bakes every frame on the next obtain.
         */
        default int glassVibrancyPercent() {
            return 100;
        }

        /**
         * The Tint amount's frame tint the frames are baked with ({@link GlassFrameTint});
         * {@link GlassFrameTint#NONE}, the default, bakes nothing. A change re-bakes every frame
         * on the next obtain, unless the bakes are held ({@link #holdFrameBakes}).
         */
        default int glassFrameTint() {
            return GlassFrameTint.NONE;
        }
    }

    /**
     * How many independently tuned radii stay resident before the least-recently-used is dropped.
     *
     * <p>Four, because a look readily uses four at once: the status bar's, the dock's (clamped to
     * 1 when blur is off), the panes' glass, and radius 0 for the wall behind the panes. At three
     * every arrival on the Terminal place cycled through all four — eight or nine misses, each a
     * wallpaper decode plus a blur, ~100 ms apiece on Pong (measured 2026-09-08) — while holding
     * three frames anyway. One more frame is the price of holding none of them hostage.
     */
    /** A capture the worker completes: a bitmap already decoded, or the decode to run there. */
    public static final class FrameCapture {
        @Nullable final Bitmap ready;
        @Nullable final Callable<Bitmap> decode;
        /**
         * Run exactly once when the decode no longer needs what it reads: after it ran (on the
         * thread that ran it), or, when it never will - the capture was dropped before any job
         * read it, its job was rejected, or the worker was shut down with the job still queued -
         * on the main thread. Null for nothing to give back.
         */
        @Nullable final Runnable release;

        private FrameCapture(@Nullable Bitmap ready, @Nullable Callable<Bitmap> decode,
                             @Nullable Runnable release) {
            this.ready = ready;
            this.decode = decode;
            this.release = release;
        }

        public static FrameCapture ready(@NonNull Bitmap bitmap) {
            return new FrameCapture(bitmap, null, null);
        }

        public static FrameCapture deferred(@NonNull Callable<Bitmap> decode) {
            return new FrameCapture(null, decode, null);
        }

        /** A deferred decode that borrows something; {@code release} gives it back (see {@link #release}). */
        public static FrameCapture deferred(@NonNull Callable<Bitmap> decode,
                                            @NonNull Runnable release) {
            return new FrameCapture(null, decode, release);
        }
    }

    /** The thread the cache lives on; results from the worker come back through it. */
    public interface MainThread {
        void post(@NonNull Runnable runnable);
    }

    /**
     * How many radii stay resident. A look uses one radius per surface — dock, status bar, pane
     * glass, the wall's own radius 0, and more when the keyboard and a palette are dressed apart —
     * and a rotation on Pong asked for six distinct radii in one frame while four fit, so the first
     * two were evicted as the last two landed (2026-09-09). Six holds a full look; the byte budget
     * below still bounds what a larger panel keeps.
     */
    public static final int MAX_CACHED_WALLPAPER_BLUR_RADII = 6;
    /**
     * How many bytes of pre-blurred frames stay resident, whatever the radius count. A count alone
     * let a QHD phone hold 55 MB of wallpaper nobody was looking at, so the budget bounds the bytes
     * and the most recent frame always stays, however large. A blurred frame is held at the size
     * it was blurred at — a quarter of the panel at the usual radii, ~1 MB for a 1.5×-wide parallax
     * frame on 1080x2412 (2026-09-27); only radius 0, the wallpaper itself, and a radius under the
     * blur's 25px cap keep a full-size frame (~15.6 MB wide). Sized for the full set of six.
     */
    public static final long DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES = 72L * 1024 * 1024;

    @NonNull private final Source mSource;
    @Nullable private final Runnable mOnCleared;
    private final long mMaxBytes;

    @NonNull private final LinkedHashMap<Integer, Bitmap> mByRadius =
        new LinkedHashMap<>(4, 0.75f, true);
    @NonNull private final Rect mFrameRect = new Rect();
    private boolean mManagedSource;
    private int mSystemId = -1;
    private long mManagedLastModified = -1L;
    private long mManagedLength = -1L;
    /** The Vibrancy the resident frames were baked at. */
    private int mVibrancy = 100;
    /** The frame tint the resident frames were baked with. */
    private int mFrameTint = GlassFrameTint.NONE;
    /** While true, a new frame tint waits: frames keep, and new radii take, {@link #mFrameTint}. */
    private boolean mFrameBakesHeld;
    /**
     * The orientation the cached frames were captured in. The frame rect alone was supposed to
     * carry this, but a rotation delivers {@code onConfigurationChanged} <em>before</em> the window
     * is re-laid out, so a crop taken during that pass records the outgoing orientation's rect and
     * then matches itself forever after. That is what landscape showed: a brighter, mismatched
     * wallpaper region with a hard seam at the pane's left edge, while portrait was correct.
     */
    private int mOrientation = Configuration.ORIENTATION_UNDEFINED;
    @Nullable private final Executor mWorker;
    @Nullable private final MainThread mMainThread;
    @Nullable private final Runnable mOnFrameReady;
    /** Radii a worker job is out for; a second request for one of them waits, it does not queue. */
    @NonNull private final Set<Integer> mPending = new HashSet<>();
    /** Bumped by every clear; a result that comes back from an older generation is dropped. */
    private int mGeneration;
    /**
     * Radii a {@link #clearForWallpaperChange()} displaced, still waiting for their replacement.
     * The surface that first refills one of these — {@link #isCrossfadedRadius} turns true for it
     * — is the one that should crossfade rather than swap outright; every other radius, and every
     * plain {@link #clear()}, never sets this, so a rotation or a radius change keeps swapping
     * instantly. A second clear before a radius refills drops it from here: its retired picture is
     * two wallpapers stale, nothing to fade from.
     */
    @NonNull private final Set<Integer> mPendingCrossfadeRadii = new HashSet<>();
    /** Radii whose resident frame arrived to replace one a wallpaper change displaced. */
    @NonNull private final Set<Integer> mCrossfadedRadii = new HashSet<>();
    /**
     * While the bakes are held, which resident radius stands in for each radius asked for that has
     * no frame: a drag moves a surface's radius a dp at a time, and each step takes the stand-in of
     * the step before it, so a surface keeps the frame it had when the drag began rather than
     * hopping between whatever frames happen to be resident.
     */
    @NonNull private final Map<Integer, Integer> mHeldStandIns = new HashMap<>();
    /** True once a stand-in was served under the current hold: its release asks for a re-render. */
    private boolean mServedStandIn;

    /**
     * The unblurred capture every blur of the current source is cut from, or null. A capture
     * in flight is shared by every radius asked for meanwhile; while {@link #retainCapture} holds,
     * it also outlives them, so a re-bake (a Tint or Vibrancy release) blurs it again instead of
     * reading the wallpaper again.
     */
    @Nullable private SharedCapture mCapture;
    /** Every capture not yet released: {@link #mCapture} and any a worker job still reads. */
    @NonNull private final List<SharedCapture> mLiveCaptures = new ArrayList<>();
    private boolean mRetainCapture;
    /** Debug counters, for tests and traces: captures begun and blur jobs run. */
    private int mCapturesBegun;
    @NonNull private final java.util.concurrent.atomic.AtomicInteger mBlursRun =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * One capture of the wallpaper for one source identity and frame rect. Main thread, except
     * {@link #resolve}, which the job that first needs the pixels runs (the worker, or inline).
     */
    private static final class SharedCapture {
        @NonNull private final FrameCapture mSource;
        @NonNull final Rect frameRect;
        final int orientation;
        final boolean managedSource;
        final int systemWallpaperId;
        final long managedLastModified;
        final long managedLength;
        /** Jobs out that read it. Main thread. Never recycled while one is. */
        int users;
        /**
         * True once its pixels were filed as a resident frame (radius 0 is the capture itself).
         * Main thread. A frame is handed to views - the wall behind the panes, an X11 pane's
         * corner mask - that keep drawing it after the cache lets it go, so such a capture is
         * never recycled; dropping the reference leaves it to the collector.
         */
        boolean handedOut;
        private volatile boolean mResolved;
        @Nullable private volatile Bitmap mBitmap;
        /** 0 untouched, 1 a job began reading it, 2 abandoned before any did. */
        @NonNull private final java.util.concurrent.atomic.AtomicInteger mReadState =
            new java.util.concurrent.atomic.AtomicInteger();

        SharedCapture(@NonNull FrameCapture source, @NonNull Rect frameRect, int orientation,
                      boolean managedSource, int systemWallpaperId, long managedLastModified,
                      long managedLength) {
            mSource = source;
            this.frameRect = new Rect(frameRect);
            this.orientation = orientation;
            this.managedSource = managedSource;
            this.systemWallpaperId = systemWallpaperId;
            this.managedLastModified = managedLastModified;
            this.managedLength = managedLength;
        }

        boolean matches(@NonNull Rect frameRect, int orientation, boolean managedSource,
                        int systemWallpaperId, long managedLastModified, long managedLength) {
            return this.frameRect.equals(frameRect) && this.orientation == orientation
                && this.managedSource == managedSource
                && this.systemWallpaperId == systemWallpaperId
                && this.managedLastModified == managedLastModified
                && this.managedLength == managedLength;
        }

        /** The pixels, decoded on the first call (off the main thread when there is a worker). */
        @Nullable
        synchronized Bitmap resolve() {
            if (mResolved) return mBitmap;
            if (!mReadState.compareAndSet(0, 1)) {
                // Abandoned: what the decode would read has already been given back.
                mBitmap = null;
                mResolved = true;
                return null;
            }
            Bitmap bitmap = mSource.ready;
            try {
                if (bitmap == null && mSource.decode != null) {
                    try {
                        bitmap = mSource.decode.call();
                    } catch (Exception e) {
                        bitmap = null;
                    }
                }
            } finally {
                giveBackSource();
            }
            mBitmap = bitmap;
            mResolved = true;
            return bitmap;
        }

        /**
         * No job will read it any more: gives back what its decode borrows, unless a job already
         * began reading (that job gives it back itself). Any thread; never blocks on a decode.
         */
        void abandon() {
            if (mReadState.compareAndSet(0, 2)) giveBackSource();
        }

        private void giveBackSource() {
            Runnable release = mSource.release;
            if (release != null) release.run();
        }

        /** The pixels if they are in hand; null while still to decode or after a failed one. */
        @Nullable
        Bitmap bitmap() {
            return mBitmap;
        }

        /** True when a decode ran and yielded nothing usable: the capture is worth nothing. */
        boolean failed() {
            Bitmap bitmap = mBitmap;
            return mResolved && (bitmap == null || bitmap.isRecycled());
        }
    }

    public WallpaperBlurCache(@NonNull Source source) {
        this(source, null);
    }

    /** @param onCleared runs after every clear, for the module-side state cut from the frames */
    public WallpaperBlurCache(@NonNull Source source, @Nullable Runnable onCleared) {
        this(source, onCleared, DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES);
    }

    /** @param maxBytes the resident-frame byte budget; see {@link #DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES} */
    public WallpaperBlurCache(@NonNull Source source, @Nullable Runnable onCleared, long maxBytes) {
        this(source, onCleared, maxBytes, null, null, null);
    }

    /**
     * With a worker, a miss no longer blocks the caller: {@link #obtain} returns null, the decode
     * and the blur run on {@code worker}, and the frame is stored — and {@code onFrameReady} run —
     * back on the main thread. Without one (tests) a miss is filled inline, as it always was.
     */
    public WallpaperBlurCache(@NonNull Source source, @Nullable Runnable onCleared, long maxBytes,
                              @Nullable Executor worker, @Nullable MainThread mainThread,
                              @Nullable Runnable onFrameReady) {
        mSource = source;
        mOnCleared = onCleared;
        mMaxBytes = maxBytes;
        mWorker = worker;
        mMainThread = mainThread;
        mOnFrameReady = onFrameReady;
    }

    /** The rect the resident frames were captured for, in screen coordinates. */
    public void copyFrameRect(@NonNull Rect out) {
        out.set(mFrameRect);
    }

    public int frameRectWidth() {
        return mFrameRect.width();
    }

    public int frameRectHeight() {
        return mFrameRect.height();
    }

    public int frameRectLeft() {
        return mFrameRect.left;
    }

    public int frameRectTop() {
        return mFrameRect.top;
    }

    /**
     * The live frame rect, for the per-draw readers (the terminal pane glass, the departure
     * snapshot's ground) that would otherwise allocate a {@link Rect} on every frame. Read only.
     */
    @NonNull
    public Rect frameRectRef() {
        return mFrameRect;
    }

    /** True while {@code frame} is one of the resident pre-blurred frames. */
    public boolean containsFrame(@Nullable Bitmap frame) {
        return frame != null && mByRadius.containsValue(frame);
    }

    /**
     * The blur radius, in dp, {@code frame} was cut at, or -1 when it is not a resident frame.
     * Does not touch the LRU order.
     */
    public int radiusDpOf(@Nullable Bitmap frame) {
        if (frame == null) return -1;
        for (java.util.Map.Entry<Integer, Bitmap> entry : mByRadius.entrySet()) {
            if (entry.getValue() == frame) return entry.getKey();
        }
        return -1;
    }

    /** Visible for tests: how many radii are resident right now. */
    public int residentRadiiCount() {
        return mByRadius.size();
    }

    /** Visible for tests: whether this radius is resident without touching LRU recency order. */
    public boolean hasRadius(int blurRadiusDp) {
        return mByRadius.containsKey(blurRadiusDp);
    }

    /** Bytes the resident frames hold, the figure the byte budget is charged against. */
    public long residentBytes() {
        long total = 0L;
        for (Bitmap frame : mByRadius.values())
            if (frame != null && !frame.isRecycled()) total += frame.getAllocationByteCount();
        return total;
    }

    /**
     * The pre-blurred frame for {@code blurRadiusDp}, or null while there is none: nothing could
     * be captured, or — with a worker — the capture and blur are running off the main thread and
     * {@code onFrameReady} will ask for a re-render when they land. A miss used to decode the
     * wallpaper and blur it right here, ~100 ms per radius on the main thread (Pong, 2026-09-09).
     */
    @Nullable
    public Bitmap obtain(int blurRadiusDp, @NonNull View wallpaperFrame) {
        Rect frameRect = mSource.wallpaperFrameRect();
        boolean managedSource = mSource.useManagedWallpaperSource();
        int systemWallpaperId = mSource.systemWallpaperId();
        long managedLastModified = managedSource ? mSource.managedWallpaperLastModified() : -1L;
        long managedLength = managedSource ? mSource.managedWallpaperLength() : -1L;
        // A new Vibrancy or frame tint makes every resident frame wrong, as a new wallpaper does;
        // held, both wait for the release and the frames keep what they were baked at.
        final int vibrancy = mFrameBakesHeld ? mVibrancy
            : GlassVibrancy.clampPercent(mSource.glassVibrancyPercent());
        final int frameTint = mFrameBakesHeld ? mFrameTint : mSource.glassFrameTint();
        boolean sameSource = sourceStillMatches(frameRect, managedSource, systemWallpaperId,
            managedLastModified, managedLength);
        boolean sourceValid = sameSource && vibrancy == mVibrancy && frameTint == mFrameTint;
        if (sourceValid) {
            Bitmap cached = mByRadius.get(blurRadiusDp);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            if (mFrameBakesHeld) {
                // A held drag never captures or bakes: a radius with no frame is answered with the
                // nearest one resident, and the release bakes the one radius it settled on.
                return heldStandIn(blurRadiusDp);
            }
        } else if (!mByRadius.isEmpty() || !mPending.isEmpty()) {
            // Only a cache holding frames of another source needs emptying. Right after a clear the
            // recorded source is blank and nothing is resident, and clearing again would throw
            // away the crossfade marks the wallpaper-change clear has just set. A re-bake (same
            // picture, a new Vibrancy or tint) keeps the capture to blur again.
            doClear(false, sameSource);
        }
        if (mWorker != null && mMainThread != null && mPending.contains(blurRadiusDp)) {
            return null;
        }
        final int orientation = mSource.orientation();
        final SharedCapture capture = captureFor(frameRect, orientation, managedSource,
            systemWallpaperId, managedLastModified, managedLength, wallpaperFrame);
        if (capture == null) {
            return null;
        }
        final Rect frameRectCopy = new Rect(frameRect);
        mVibrancy = vibrancy;
        mFrameTint = frameTint;
        capture.users++;
        if (mWorker == null || mMainThread == null) {
            Bitmap blurred = captureAndBlur(capture, blurRadiusDp, vibrancy, frameTint);
            capture.users--;
            if (blurred != null) {
                store(capture, blurRadiusDp, blurred, frameRectCopy, orientation, managedSource,
                    systemWallpaperId, managedLastModified, managedLength);
            }
            settleCapture(capture);
            return blurred;
        }
        mPending.add(blurRadiusDp);
        // The source is recorded now, so the next request for another radius sees a valid source
        // with a frame in flight rather than a source it has never seen — which would clear the
        // cache, forget the pending job and drop its result as stale.
        recordSource(frameRectCopy, orientation, managedSource, systemWallpaperId,
            managedLastModified, managedLength);
        final int generation = mGeneration;
        final MainThread mainThread = mMainThread;
        final Runnable job = () -> {
            final Bitmap blurred = captureAndBlur(capture, blurRadiusDp, vibrancy, frameTint);
            mainThread.post(() -> {
                capture.users--;
                mPending.remove(blurRadiusDp);
                if (blurred == null) {
                    settleCapture(capture);
                    return;
                }
                if (generation != mGeneration) {
                    // The wallpaper, orientation or frame moved while this was in flight. This
                    // result was never handed out, so it may be recycled; radius 0 is the capture
                    // itself, which the capture's own release decides about.
                    if (blurred != capture.bitmap()) blurred.recycle();
                    settleCapture(capture);
                    return;
                }
                store(capture, blurRadiusDp, blurred, frameRectCopy, orientation, managedSource,
                    systemWallpaperId, managedLastModified, managedLength);
                settleCapture(capture);
                if (mOnFrameReady != null) mOnFrameReady.run();
            });
        };
        try {
            mWorker.execute(job);
        } catch (RejectedExecutionException rejected) {
            capture.users--;
            settleCapture(capture);
            // The worker was shut down under us: the renderer is being destroyed while a commit it
            // had already booked is still landing (a night-mode flip recreates the activity, and
            // the last commit of the old one ran after its onDestroy). There is no frame to fill
            // and nobody left to hand it to — answer the miss with null, as for any capture that
            // cannot happen right now, and forget the radius so a live cache never waits on it.
            mPending.remove(blurRadiusDp);
            return null;
        }
        return null;
    }

    /**
     * Holds a new frame tint back while {@code held}: the resident frames stay, and a radius
     * filled meanwhile takes the tint they were baked with. A drag of Tint or Opacity moves the
     * frame tint on every tick, and each move would re-bake every frame; held, the release bakes
     * once, on the next obtain. Vibrancy is held the same way.
     *
     * <p>The blur radius is held too. A Glass drag moves the radius a dp at a time, and each new
     * radius was a capture and a blur — on the managed wallpaper a full-size bitmap drawn on the
     * main thread — that also pushed other surfaces' radii out of the LRU. Held, a radius with no
     * frame is answered with the nearest resident one ({@link #heldStandIn}) and nothing is
     * captured, blurred or stored; the release asks every surface to render again, so each asks
     * for its real radius and the one the drag settled on is baked once.</p>
     */
    public void holdFrameBakes(boolean held) {
        if (mFrameBakesHeld == held) return;
        mFrameBakesHeld = held;
        if (held) return;
        mHeldStandIns.clear();
        boolean served = mServedStandIn;
        mServedStandIn = false;
        // A surface drawing a stand-in recorded the radius it asked for, so nothing would ask
        // again by itself; the same re-render a landed frame triggers sends every one back.
        if (served && mOnFrameReady != null) {
            if (mMainThread != null) mMainThread.post(mOnFrameReady);
            else mOnFrameReady.run();
        }
    }

    /** True while {@link #holdFrameBakes} holds. */
    public boolean areFrameBakesHeld() {
        return mFrameBakesHeld;
    }

    /**
     * Held: the resident frame that stands in for {@code blurRadiusDp}, or null when nothing is
     * resident. The nearest radius asked for earlier in the hold passes its stand-in on, so a
     * surface dragged away from its radius keeps the frame it began with; a resident radius
     * strictly nearer than any of those is taken instead, being that much closer to the look.
     * Touches no capture and no blur.
     */
    @Nullable
    private Bitmap heldStandIn(int blurRadiusDp) {
        int bestRadius = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> entry : mHeldStandIns.entrySet()) {
            Bitmap frame = mByRadius.get(entry.getValue());
            if (frame == null || frame.isRecycled()) continue;
            int distance = Math.abs(entry.getKey() - blurRadiusDp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestRadius = entry.getValue();
            }
        }
        for (Map.Entry<Integer, Bitmap> entry : mByRadius.entrySet()) {
            Bitmap frame = entry.getValue();
            if (frame == null || frame.isRecycled()) continue;
            int distance = Math.abs(entry.getKey() - blurRadiusDp);
            // Strictly nearer: a tie stays with the frame the drag began from.
            if (distance < bestDistance) {
                bestDistance = distance;
                bestRadius = entry.getKey();
            }
        }
        if (bestDistance == Integer.MAX_VALUE) return null;
        mHeldStandIns.put(blurRadiusDp, bestRadius);
        mServedStandIn = true;
        return mByRadius.get(bestRadius);
    }

    /**
     * Keeps the unblurred capture between blurs while {@code retain}: every re-bake the Look
     * editor causes (a Tint, Opacity or Vibrancy release, a Glass release) blurs the one capture
     * again rather than reading the wallpaper again. Off, a capture lives only as long as the jobs
     * that read it. The capture is the frame rect at full size — a 1.5×-wide frame on 1080x2412 is
     * ~15.6 MB — and the radius-0 frame, when resident, is that same bitmap, so it then costs
     * nothing more. Dropped on every {@link #clear}, a wallpaper change and a trim.
     */
    public void retainCapture(boolean retain) {
        if (mRetainCapture == retain) return;
        mRetainCapture = retain;
        if (!retain && mCapture != null) settleCapture(mCapture);
    }

    /** Visible for tests: true while an unblurred capture is held for the next blur. */
    public boolean holdsCapture() {
        return mCapture != null;
    }

    /** Debug counter: how many wallpaper captures the cache has begun. */
    public int capturesBegun() {
        return mCapturesBegun;
    }

    /** Debug counter: how many blur-and-bake jobs have run (each one a {@code Blur.miss}). */
    public int blursRun() {
        return mBlursRun.get();
    }

    /**
     * The capture for this source and frame rect: the one held or in flight when it still
     * matches, else a new one begun. Null when nothing can be captured right now.
     */
    @Nullable
    private SharedCapture captureFor(@NonNull Rect frameRect, int orientation,
                                     boolean managedSource, int systemWallpaperId,
                                     long managedLastModified, long managedLength,
                                     @NonNull View wallpaperFrame) {
        SharedCapture held = mCapture;
        if (held != null && held.matches(frameRect, orientation, managedSource,
                systemWallpaperId, managedLastModified, managedLength) && !held.failed()) {
            return held;
        }
        dropCapture();
        FrameCapture begun = mSource.beginCapture(frameRect, wallpaperFrame);
        if (begun == null) return null;
        mCapturesBegun++;
        SharedCapture capture = new SharedCapture(begun, frameRect, orientation, managedSource,
            systemWallpaperId, managedLastModified, managedLength);
        mCapture = capture;
        mLiveCaptures.add(capture);
        return capture;
    }

    /** Lets the current capture go; recycled once no job reads it. */
    private void dropCapture() {
        SharedCapture capture = mCapture;
        mCapture = null;
        if (capture != null) settleCapture(capture);
    }

    /**
     * Main thread: releases {@code capture} if nothing needs it — no job reading it, and not the
     * capture held for the next blur. What its decode borrowed is given back if no job ever read
     * it. Its pixels are recycled only if they were never handed out as a frame (radius 0 is the
     * capture itself): a view may still be drawing a frame long after the cache let it go.
     */
    private void settleCapture(@NonNull SharedCapture capture) {
        if (capture.users > 0) return;
        if (capture == mCapture) {
            if (mRetainCapture && !capture.failed()) return;
            mCapture = null;
        }
        if (!mLiveCaptures.remove(capture)) return;
        capture.abandon();
        if (!capture.handedOut) recycleUnlessHeld(capture.bitmap());
    }

    /**
     * The worker was shut down ({@code shutdownNow}): a job it never ran will never post back,
     * so its capture is never settled. What each such capture's decode borrowed is given back
     * now; a job already reading gives it back itself. Main thread.
     */
    public void abandonQueuedJobs() {
        for (SharedCapture capture : new ArrayList<>(mLiveCaptures)) capture.abandon();
        mPending.clear();
    }

    /**
     * Recycles {@code bitmap}, a capture never handed out, unless something still holds it: a
     * resident frame, a live capture (a job may be reading it on the worker), or a view drawing
     * it. A frame that was handed out is never passed here.
     */
    private void recycleUnlessHeld(@Nullable Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;
        if (mByRadius.containsValue(bitmap)) return;
        for (SharedCapture capture : mLiveCaptures)
            if (capture.bitmap() == bitmap) return;
        if (mSource.isFrameInUse(bitmap)) return;
        bitmap.recycle();
    }

    /** True while a worker job is out for {@code blurRadiusDp}. */
    public boolean isPending(int blurRadiusDp) {
        return mPending.contains(blurRadiusDp);
    }

    /**
     * Decode (if still to do), blur and bake one frame at {@code vibrancy} with
     * {@code frameTint}. Runs on the worker when there is one.
     */
    @Nullable
    private Bitmap captureAndBlur(@NonNull SharedCapture capture, int blurRadiusDp, int vibrancy,
                                 int frameTint) {
        Trace.beginSection("Blur.miss");
        try {
            mBlursRun.incrementAndGet();
            // The capture is shared, and released on the main thread once no job reads it; it is
            // only read here, never recycled.
            Bitmap wallpaperBitmap = capture.resolve();
            if (wallpaperBitmap == null || wallpaperBitmap.isRecycled()) {
                return null;
            }
            Bitmap blurredBitmap = mSource.preBlur(wallpaperBitmap, blurRadiusDp);
            if (blurredBitmap == null) {
                return null;
            }
            if (blurredBitmap != wallpaperBitmap) {
                // Vibrancy and the frame tint are the glass's, baked here once, in one pass;
                // radius 0 is the wallpaper itself.
                if (GlassVibrancy.bakes(blurRadiusDp, vibrancy)
                    || GlassFrameTint.bakes(blurRadiusDp, frameTint))
                    return GlassVibrancy.baked(blurredBitmap, vibrancy, frameTint);
            }
            return blurredBitmap;
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Main thread: files a finished frame under its radius and records the source it came from.
     * From here on the frame is handed out: views keep it, and draw it, past its eviction or a
     * clear, so the cache only ever drops a frame and never recycles one.
     */
    private void store(@NonNull SharedCapture capture, int blurRadiusDp,
                       @NonNull Bitmap blurredBitmap, @NonNull Rect frameRect,
                       int orientation, boolean managedSource, int systemWallpaperId,
                       long managedLastModified, long managedLength) {
        if (blurredBitmap == capture.bitmap()) capture.handedOut = true;
        mByRadius.put(blurRadiusDp, blurredBitmap);
        while (mByRadius.size() > MAX_CACHED_WALLPAPER_BLUR_RADII
            || (mByRadius.size() > 1 && residentBytes() > mMaxBytes)) {
            Iterator<Bitmap> eldest = mByRadius.values().iterator();
            eldest.next();
            // Dropped, never recycled: a surface may still be drawing it (an X11 pane's corner
            // mask keeps the wall-behind frame across passes), and the collector reclaims it.
            eldest.remove();
        }
        recordSource(frameRect, orientation, managedSource, systemWallpaperId,
            managedLastModified, managedLength);
        if (mPendingCrossfadeRadii.remove(blurRadiusDp)) {
            mCrossfadedRadii.add(blurRadiusDp);
        } else {
            mCrossfadedRadii.remove(blurRadiusDp);
        }
    }

    /**
     * True while the frame now resident for {@code blurRadiusDp} arrived to replace one a wallpaper
     * change displaced, rather than a rotation, a radius change, or the first fill of a radius. A
     * surface reads this once, at the moment it notices the bitmap it is drawing changed identity,
     * to decide whether that swap crossfades or lands outright; it stays true until the radius is
     * next refilled for some other reason, but a surface that swaps only once per fresh bitmap never
     * asks twice.
     */
    public boolean isCrossfadedRadius(int blurRadiusDp) {
        return mCrossfadedRadii.contains(blurRadiusDp);
    }

    private void recordSource(@NonNull Rect frameRect, int orientation, boolean managedSource,
                              int systemWallpaperId, long managedLastModified, long managedLength) {
        mFrameRect.set(frameRect);
        mOrientation = orientation;
        mManagedSource = managedSource;
        mSystemId = systemWallpaperId;
        mManagedLastModified = managedLastModified;
        mManagedLength = managedLength;
    }

    /** True while the resident frames still describe the wallpaper, orientation and frame rect. */
    private boolean sourceStillMatches(@NonNull Rect frameRect, boolean managedSource,
                                       int systemWallpaperId, long managedLastModified,
                                       long managedLength) {
        return mManagedSource == managedSource
            && mSystemId == systemWallpaperId
            && mManagedLastModified == managedLastModified
            && mManagedLength == managedLength
            && mOrientation == mSource.orientation()
            && mFrameRect.equals(frameRect);
    }

    /**
     * Drops the frames only if the source they were captured from has moved: a rotation, a
     * resized frame, another wallpaper. For the configuration-change path, which used to clear
     * unconditionally — a hardware keyboard, a navigation or screen-layout change arrives through
     * the same callback as a rotation and none of them makes a portrait frame wrong, yet each cost
     * a decode and a blur per radius on the next frame.
     */
    public void dropIfSourceMoved() {
        if (mByRadius.isEmpty()) return;
        boolean managedSource = mSource.useManagedWallpaperSource();
        if (!sourceStillMatches(mSource.wallpaperFrameRect(), managedSource,
                mSource.systemWallpaperId(),
                managedSource ? mSource.managedWallpaperLastModified() : -1L,
                managedSource ? mSource.managedWallpaperLength() : -1L)) {
            clear();
        }
    }

    /**
     * Empties the cache, dropping every frame (surfaces may still draw them). Traced as {@code Blur.clear} so
     * a system trace shows which event emptied it before a run of {@code Blur.miss}. The swap that
     * follows lands outright — a rotation, a radius change, a zoom, a managed file landing, a trim —
     * none of those is a picture the viewer should watch fade in.
     */
    public void clear() {
        doClear(false, false);
    }

    /**
     * The wallpaper itself changed. Emptied the same way {@link #clear()} does, except the radii
     * this displaces are marked so whichever surface refills one first crossfades into it instead
     * of swapping outright — see {@link #isCrossfadedRadius}.
     */
    public void clearForWallpaperChange() {
        doClear(true, false);
    }

    /**
     * @param keepCapture a re-bake: the same picture at a new Vibrancy or frame tint, so the
     *                    unblurred capture still holds and is blurred again
     */
    private void doClear(boolean crossfade, boolean keepCapture) {
        Trace.beginSection(keepCapture ? "Blur.rebake" : "Blur.clear");
        try {
            doClearTraced(crossfade, keepCapture);
        } finally {
            Trace.endSection();
        }
    }

    private void doClearTraced(boolean crossfade, boolean keepCapture) {
        mGeneration++;
        mPending.clear();
        mHeldStandIns.clear();
        // A radius still waiting from an earlier wallpaper-change clear keeps its mark through a
        // second wallpaper change: the surface is still drawing its last good frame, and the frame
        // that finally lands should fade in over it just the same. A plain clear drops every mark.
        if (!crossfade) mPendingCrossfadeRadii.clear();
        mCrossfadedRadii.clear();
        if (crossfade) mPendingCrossfadeRadii.addAll(mByRadius.keySet());
        // Every resident frame was handed out, and the surfaces keep drawing it until the next
        // pass hands them its replacement: dropped, never recycled (the collector reclaims them).
        mByRadius.clear();
        if (!keepCapture) dropCapture();
        mFrameRect.setEmpty();
        mOrientation = Configuration.ORIENTATION_UNDEFINED;
        mManagedSource = false;
        mSystemId = -1;
        mManagedLastModified = -1L;
        mManagedLength = -1L;
        mSource.onCacheCleared();
        if (mOnCleared != null) mOnCleared.run();
    }
}
