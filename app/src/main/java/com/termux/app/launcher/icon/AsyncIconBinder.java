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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binds rendered app icons to views without rendering on the main thread.
 *
 * <p>A bind asks the rendered-icon cache what it already holds. A hit is set at once, with no
 * animation, so a warm list scrolls exactly as it always has. A miss shows a neutral tile in the
 * icon's box ({@link IconArrivalDrawable}), renders on the launcher's worker — the artwork load,
 * the icon-pack treatment and the glass render, charged to the same budgeted stores as before —
 * and fades the icon in over the tile when it lands.</p>
 *
 * <p>Every view carries a request number. Rebinding or releasing the view moves it on, so a
 * render that finishes for a cell which has since been recycled to another app is dropped, and a
 * request still queued when that happens is skipped without rendering at all. A result is also
 * dropped when something else has replaced the tile in the meantime.</p>
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

    /** Where finished icons come from. */
    public interface Renderer {
        /** The finished icon if it is already held; never loads or renders. Main thread. */
        @Nullable Drawable peek(@NonNull LauncherAppEntry entry, int sizePx);

        /** The finished icon, loading and rendering whatever is missing. Worker thread. */
        @Nullable Drawable render(@NonNull LauncherAppEntry entry, int sizePx);
    }

    /** The tile's colour, alpha included, read at bind time so it follows the current scheme. */
    public interface TileColor {
        int tileColor();
    }

    /** One view's request state, kept on the view itself. Main thread except {@link #generation}. */
    @VisibleForTesting
    static final class Slot {
        /** Read by the worker to skip a request that went stale while it was queued. */
        volatile int generation;
        @Nullable IconArrivalDrawable tile;
        @Nullable ValueAnimator fade;
    }

    private static final Interpolator EASE_OUT = new DecelerateInterpolator(1.5f);
    @Nullable private static Handler sMainHandler;
    /** See {@link #setSynchronousForTesting}. Never set in production. */
    private static volatile boolean sSynchronousForTesting;

    @NonNull private final Renderer renderer;
    @NonNull private final Executor worker;
    @NonNull private final Executor main;
    @NonNull private final TileColor tileColor;
    /** Moves on with every prefetch, so a newer one supersedes what an older one has left. */
    private final AtomicInteger prefetchGeneration = new AtomicInteger();

    public AsyncIconBinder(@NonNull Renderer renderer, @NonNull Executor worker,
                           @NonNull Executor main, @NonNull TileColor tileColor) {
        this.renderer = renderer;
        this.worker = worker;
        this.main = main;
        this.tileColor = tileColor;
    }

    /**
     * Makes every bind render a miss inline and set it at once, and every prefetch do nothing:
     * the synchronous behaviour from before rendering moved to the worker. For JVM tests, which
     * assert on a cell's drawable right after binding it and have no worker worth waiting for;
     * a test that sets it resets it after itself.
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
     * Shows {@code entry}'s rendered icon at {@code sizePx} in {@code view}: now if it is held,
     * otherwise a tile now and the icon, faded in, once the worker has rendered it. Main thread.
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
        int token = slot.generation;
        IconArrivalDrawable tile = new IconArrivalDrawable(sizePx, tileColor.tileColor());
        slot.tile = tile;
        view.setImageDrawable(tile);
        worker.execute(() -> {
            // Recycled to another app before its turn: skip the render, not just the result.
            if (slot.generation != token) return;
            Drawable rendered = renderQuietly(entry, sizePx);
            Drawable icon = rendered != null ? rendered : fallback;
            main.execute(() -> deliver(view, slot, token, tile, icon));
        });
        return false;
    }

    /**
     * Renders {@code entries} at {@code sizePx} on the worker ahead of their binds, skipping any
     * already held, so the cells that bind next take the immediate path. A newer prefetch stops
     * what an older one has not reached yet. Main thread.
     */
    public void prefetch(@NonNull List<LauncherAppEntry> entries, int sizePx) {
        if (sizePx <= 0 || entries.isEmpty() || sSynchronousForTesting) return;
        List<LauncherAppEntry> wanted = new ArrayList<>();
        for (LauncherAppEntry entry : entries) {
            if (wanted.size() >= MAX_PREFETCH) break;
            if (entry != null && renderer.peek(entry, sizePx) == null) wanted.add(entry);
        }
        if (wanted.isEmpty()) return;
        int token = prefetchGeneration.incrementAndGet();
        worker.execute(() -> {
            for (LauncherAppEntry entry : wanted) {
                if (prefetchGeneration.get() != token) return;
                renderQuietly(entry, sizePx);
            }
        });
    }

    /**
     * Forgets any icon still on its way to {@code view} and stops its fade. Called when a view is
     * released or rebound by a path that sets its own drawable. Main thread.
     */
    public static void cancel(@NonNull ImageView view) {
        Object tag = view.getTag(R.id.launcher_icon_request);
        if (tag instanceof Slot) release((Slot) tag);
    }

    /** True while {@code view} waits for, or is fading in, an icon from the worker. */
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

    private void deliver(@NonNull ImageView view, @NonNull Slot slot, int token,
                         @NonNull IconArrivalDrawable tile, @Nullable Drawable icon) {
        // Rebound, released, or the tile replaced by someone else: this answer is not wanted.
        if (slot.generation != token || view.getDrawable() != tile) return;
        if (icon == null || !shouldFade(view)) {
            slot.tile = null;
            view.setImageDrawable(icon);
            return;
        }
        tile.arrive(icon);
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
                if (cancelled || slot.generation != token || view.getDrawable() != tile) return;
                // Landed: the view holds the shared icon itself from here on, exactly as a warm
                // bind would have left it.
                slot.tile = null;
                view.setImageDrawable(icon);
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
