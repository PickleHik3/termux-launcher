package com.termux.app.launcher.icon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binds rendered app icons to views without rendering on the main thread.
 *
 * <p>A bind asks the rendered-icon cache what it already holds. A hit is set at once, with no
 * animation, so a warm list scrolls exactly as it always has. A miss shows a neutral tile in the
 * icon's box ({@link IconArrivalDrawable}), renders on the icon thread — the artwork load, the
 * icon-pack treatment and the glass render, charged to the same budgeted stores as before — and
 * fades the icon in over the tile when it lands.</p>
 *
 * <p>Every view carries a request number. Rebinding or releasing the view moves it on, so a
 * render that finishes for a cell which has since been recycled to another app is dropped, and a
 * request still queued when that happens is skipped without rendering at all. A result is also
 * dropped when something else has replaced the tile in the meantime.</p>
 *
 * <p><b>Nothing waiting retains anything.</b> The icon thread is process-wide, so its queue
 * outlives any one activity. A queued request therefore holds its binder and its view only
 * weakly, and the binder is the only thing that reaches the renderer, the rendered-icon cache and
 * the dock behind it; the hop back to the main thread and the fade hold the view weakly as well.
 * A request whose binder or view has gone is skipped. {@link #cancelAll} takes an owner's
 * requests out of the queue when its window goes away, and {@link #resumeAll} puts back the ones
 * still wanted when it returns.</p>
 *
 * <p>The fade is a single short animator per arrival. It ends, hands the view the icon itself,
 * and nothing repaints after it.</p>
 */
public final class AsyncIconBinder {

    /** Long enough to read as a soft arrival, short enough never to feel like a wait. */
    public static final long FADE_DURATION_MS = 180L;
    /**
     * The most icons one prefetch renders: about two drawer pages on a phone. More would only
     * push the visible ones out of the cache it is filling.
     */
    public static final int MAX_PREFETCH = 64;
    /** How long the icon thread lingers with nothing to do before it exits. */
    private static final long WORKER_IDLE_SECONDS = 15L;

    /** Where finished icons come from. */
    public interface Renderer {
        /** The finished icon if it is already held; never loads or renders. Main thread. */
        @Nullable Drawable peek(@NonNull LauncherAppEntry entry, int sizePx);

        /** The finished icon, loading and rendering whatever is missing. Icon thread. */
        @Nullable Drawable render(@NonNull LauncherAppEntry entry, int sizePx);
    }

    /** The tile's colour, alpha included, read at bind time so it follows the current scheme. */
    public interface TileColor {
        int tileColor();
    }

    /**
     * One view's request state, kept on the view itself. Holds no view, so a queued request that
     * references it keeps nothing alive. Main thread except {@link #generation}.
     */
    @VisibleForTesting
    static final class Slot {
        /** Read by the icon thread to skip a request that went stale while it was queued. */
        volatile int generation;
        @Nullable IconArrivalDrawable tile;
        @Nullable ValueAnimator fade;
    }

    private static final Interpolator EASE_OUT = new DecelerateInterpolator(1.5f);
    /** Made on first use; its queue only ever holds {@link Request}s and {@link Delivery}s. */
    @Nullable private static Handler sMainHandler;
    /** Made on first use; see {@link #sharedWorker()}. */
    @Nullable private static ThreadPoolExecutor sSharedWorker;
    /** See {@link #setSynchronousForTesting}. Never set in production. */
    private static volatile boolean sSynchronousForTesting;

    @NonNull private final Renderer renderer;
    @NonNull private final Executor worker;
    @NonNull private final Executor main;
    @NonNull private final TileColor tileColor;
    /** Marks this binder's requests in a queue it shares with other binders. Holds nothing. */
    @NonNull private final Object owner = new Object();
    /** Moves on with every prefetch and every {@link #cancelAll}, superseding what is queued. */
    private final AtomicInteger prefetchGeneration = new AtomicInteger();
    /** Bind requests queued and not yet run; parked here by {@link #cancelAll}. */
    private final Set<Request> pending = Collections.synchronizedSet(new LinkedHashSet<>());
    /** While true the icon thread skips this binder's requests, leaving them pending. */
    private volatile boolean suspended;

    public AsyncIconBinder(@NonNull Renderer renderer, @NonNull Executor worker,
                           @NonNull Executor main, @NonNull TileColor tileColor) {
        this.renderer = renderer;
        this.worker = worker;
        this.main = main;
        this.tileColor = tileColor;
    }

    /**
     * Makes every bind render a miss inline and set it at once, and every prefetch do nothing:
     * the synchronous behaviour from before rendering moved to the icon thread. For JVM tests,
     * which assert on a cell's drawable right after binding it; a test that sets it resets it.
     */
    @VisibleForTesting
    public static void setSynchronousForTesting(boolean synchronous) {
        sSynchronousForTesting = synchronous;
    }

    /** Posts to the main looper; the handler is made on first use, not at class load. */
    @NonNull
    public static Executor mainThreadExecutor() {
        return command -> {
            Handler handler;
            synchronized (AsyncIconBinder.class) {
                if (sMainHandler == null) sMainHandler = new Handler(Looper.getMainLooper());
                handler = sMainHandler;
            }
            handler.post(command);
        };
    }

    /**
     * The icon thread: one background thread for every binder in the process, made on first use
     * and gone after {@value #WORKER_IDLE_SECONDS}s of idleness. It is the icons' own rather than
     * the catalogue's, so a catalogue load never holds icons up and its queue can be cleared per
     * owner. Everything queued on it holds its binder and view weakly.
     */
    @NonNull
    public static Executor sharedWorker() {
        synchronized (AsyncIconBinder.class) {
            if (sSharedWorker == null) {
                ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, WORKER_IDLE_SECONDS,
                    TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
                        Thread thread = new Thread(runnable, "launcher-icons");
                        thread.setDaemon(true);
                        thread.setPriority(Thread.NORM_PRIORITY - 1);
                        return thread;
                    });
                executor.allowCoreThreadTimeOut(true);
                sSharedWorker = executor;
            }
            return sSharedWorker;
        }
    }

    /**
     * Shows {@code entry}'s rendered icon at {@code sizePx} in {@code view}: now if it is held,
     * otherwise a tile now and the icon, faded in, once the icon thread has rendered it. Main
     * thread.
     *
     * @param fallback what to show if nothing could be rendered — the entry's own artwork, as a
     *                 synchronous bind always fell back to
     * @return true when the icon was bound now, false when it is on the way
     */
    public boolean bind(@NonNull ImageView view, @NonNull LauncherAppEntry entry, int sizePx,
                        @Nullable Drawable fallback) {
        Slot slot = slotFor(view);
        Drawable held = renderer.peek(entry, sizePx);
        if (held != null) {
            IconArrivalDrawable tile = slot.tile;
            if (slot.fade != null && tile != null && tile.arrivingIcon() == held
                && view.getDrawable() == tile) {
                // This very icon is already fading in here; a rebind must not snap it.
                return true;
            }
            release(slot);
            view.setImageDrawable(held);
            return true;
        }
        release(slot);
        if (sSynchronousForTesting) {
            Drawable rendered = renderQuietly(entry, sizePx);
            view.setImageDrawable(rendered != null ? rendered : fallback);
            return true;
        }
        IconArrivalDrawable tile = new IconArrivalDrawable(sizePx, tileColor.tileColor());
        slot.tile = tile;
        view.setImageDrawable(tile);
        Request request = new Request(this, view, slot, slot.generation, tile, entry, sizePx,
            fallback);
        pending.add(request);
        worker.execute(request);
        return false;
    }

    /**
     * Renders {@code entries} at {@code sizePx} on the icon thread ahead of their binds, skipping
     * any already held, so the cells that bind next take the immediate path. A newer prefetch
     * stops what an older one has not reached yet. Main thread.
     */
    public void prefetch(@NonNull List<LauncherAppEntry> entries, int sizePx) {
        if (sizePx <= 0 || entries.isEmpty() || sSynchronousForTesting || suspended) return;
        List<LauncherAppEntry> wanted = new ArrayList<>();
        for (LauncherAppEntry entry : entries) {
            if (wanted.size() >= MAX_PREFETCH) break;
            if (entry != null && renderer.peek(entry, sizePx) == null) wanted.add(entry);
        }
        if (wanted.isEmpty()) return;
        worker.execute(new Prefetch(this, prefetchGeneration.incrementAndGet(), wanted, sizePx));
    }

    /**
     * Takes this binder's work off the icon thread: queued renders are parked until
     * {@link #resumeAll}, and prefetches are dropped. For the owner's window going away, so
     * nothing it asked for is rendered, or kept queued, on its behalf meanwhile. Main thread.
     */
    public void cancelAll() {
        suspended = true;
        prefetchGeneration.incrementAndGet();
        if (worker instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) worker).getQueue().removeIf(task ->
                (task instanceof Request && ((Request) task).owner == owner)
                    || (task instanceof Prefetch && ((Prefetch) task).owner == owner));
        }
    }

    /**
     * Queues again every parked render whose view is still alive and still waiting for it, so a
     * window that comes back is not left showing tiles. Main thread.
     */
    public void resumeAll() {
        if (!suspended) return;
        suspended = false;
        List<Request> parked;
        synchronized (pending) {
            parked = new ArrayList<>(pending);
        }
        for (Request request : parked) {
            if (request.isWanted()) worker.execute(request);
            else pending.remove(request);
        }
    }

    /** Requests queued or parked and not yet run. */
    @VisibleForTesting
    int pendingCount() {
        return pending.size();
    }

    /**
     * Forgets any icon still on its way to {@code view} and stops its fade. Called when a view is
     * released or rebound by a path that sets its own drawable. Main thread.
     */
    public static void cancel(@NonNull ImageView view) {
        Object tag = view.getTag(R.id.launcher_icon_request);
        if (tag instanceof Slot) release((Slot) tag);
    }

    /** True while {@code view} waits for, or is fading in, an icon from the icon thread. */
    public static boolean isPending(@NonNull ImageView view) {
        Object tag = view.getTag(R.id.launcher_icon_request);
        return tag instanceof Slot && ((Slot) tag).tile != null
            && view.getDrawable() == ((Slot) tag).tile;
    }

    /** Whether an arrival is worth animating: someone can see it, and animations are on. */
    @VisibleForTesting
    static boolean shouldFade(@NonNull ImageView view) {
        if (!view.isShown()) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * One cold bind, queued on the icon thread. It reaches its binder and its view only through
     * weak references, so a request waiting in the process-wide queue keeps neither the dock nor
     * the activity behind it alive.
     */
    private static final class Request implements Runnable {
        @NonNull final Object owner;
        @NonNull final WeakReference<AsyncIconBinder> binder;
        @NonNull final WeakReference<ImageView> view;
        @NonNull final Slot slot;
        final int token;
        @NonNull final IconArrivalDrawable tile;
        @NonNull final LauncherAppEntry entry;
        final int sizePx;
        @Nullable final Drawable fallback;

        Request(@NonNull AsyncIconBinder binder, @NonNull ImageView view, @NonNull Slot slot,
                int token, @NonNull IconArrivalDrawable tile, @NonNull LauncherAppEntry entry,
                int sizePx, @Nullable Drawable fallback) {
            this.owner = binder.owner;
            this.binder = new WeakReference<>(binder);
            this.view = new WeakReference<>(view);
            this.slot = slot;
            this.token = token;
            this.tile = tile;
            this.entry = entry;
            this.sizePx = sizePx;
            this.fallback = fallback;
        }

        /** Still the slot's current request, for a view that still exists. */
        boolean isWanted() {
            return slot.generation == token && view.get() != null;
        }

        @Override
        public void run() {
            AsyncIconBinder host = binder.get();
            if (host == null) return;
            // Parked: its window went away. resumeAll queues it again if it is still wanted.
            if (host.suspended) return;
            host.pending.remove(this);
            // Recycled to another app, or its view collected, before its turn: skip the render.
            if (!isWanted()) return;
            Drawable rendered = host.renderQuietly(entry, sizePx);
            Drawable icon = rendered != null ? rendered : fallback;
            host.main.execute(new Delivery(view, slot, token, tile, icon));
        }
    }

    /** A finished render on its way back to the main thread; holds the view weakly. */
    private static final class Delivery implements Runnable {
        @NonNull final WeakReference<ImageView> view;
        @NonNull final Slot slot;
        final int token;
        @NonNull final IconArrivalDrawable tile;
        @Nullable final Drawable icon;

        Delivery(@NonNull WeakReference<ImageView> view, @NonNull Slot slot, int token,
                 @NonNull IconArrivalDrawable tile, @Nullable Drawable icon) {
            this.view = view;
            this.slot = slot;
            this.token = token;
            this.tile = tile;
            this.icon = icon;
        }

        @Override
        public void run() {
            ImageView live = view.get();
            if (live != null) deliver(live, slot, token, tile, icon);
        }
    }

    /** Renders ahead of binds; holds its binder weakly. */
    private static final class Prefetch implements Runnable {
        @NonNull final Object owner;
        @NonNull final WeakReference<AsyncIconBinder> binder;
        final int token;
        @NonNull final List<LauncherAppEntry> entries;
        final int sizePx;

        Prefetch(@NonNull AsyncIconBinder binder, int token,
                 @NonNull List<LauncherAppEntry> entries, int sizePx) {
            this.owner = binder.owner;
            this.binder = new WeakReference<>(binder);
            this.token = token;
            this.entries = entries;
            this.sizePx = sizePx;
        }

        @Override
        public void run() {
            for (LauncherAppEntry entry : entries) {
                AsyncIconBinder host = binder.get();
                if (host == null || host.suspended
                    || host.prefetchGeneration.get() != token) return;
                host.renderQuietly(entry, sizePx);
            }
        }
    }

    private static void deliver(@NonNull ImageView view, @NonNull Slot slot, int token,
                                @NonNull IconArrivalDrawable tile, @Nullable Drawable icon) {
        // Rebound, released, or the tile replaced by someone else: this answer is not wanted.
        if (slot.generation != token || view.getDrawable() != tile) return;
        if (icon == null || !shouldFade(view)) {
            slot.tile = null;
            view.setImageDrawable(icon);
            return;
        }
        tile.arrive(icon);
        // The animator is held by the framework's animation handler while it runs, so it too
        // reaches the view only weakly.
        WeakReference<ImageView> target = new WeakReference<>(view);
        ValueAnimator fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(FADE_DURATION_MS);
        fade.setInterpolator(EASE_OUT);
        fade.addUpdateListener(animator -> tile.setProgress((float) animator.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (slot.fade == animation) slot.fade = null;
                ImageView live = target.get();
                if (cancelled || live == null || slot.generation != token
                    || live.getDrawable() != tile) return;
                // Landed: the view holds the shared icon itself from here on, exactly as a warm
                // bind would have left it.
                slot.tile = null;
                live.setImageDrawable(icon);
            }
        });
        slot.fade = fade;
        fade.start();
    }

    @Nullable
    private Drawable renderQuietly(@NonNull LauncherAppEntry entry, int sizePx) {
        try {
            return renderer.render(entry, sizePx);
        } catch (RuntimeException | OutOfMemoryError e) {
            // A bad icon costs its cell an icon, never the home screen.
            return null;
        }
    }

    @NonNull
    @VisibleForTesting
    static Slot slotFor(@NonNull ImageView view) {
        Object tag = view.getTag(R.id.launcher_icon_request);
        if (tag instanceof Slot) return (Slot) tag;
        Slot slot = new Slot();
        view.setTag(R.id.launcher_icon_request, slot);
        return slot;
    }

    /** Moves the slot on, so nothing in flight for it lands, and ends its fade where it stands. */
    private static void release(@NonNull Slot slot) {
        slot.generation++;
        slot.tile = null;
        ValueAnimator fade = slot.fade;
        slot.fade = null;
        if (fade != null) fade.cancel();
    }
}
