package com.termux.app.launcher.icon;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The binder's contract with a cell: a held icon binds now and never fades, a missing one shows a
 * tile and lands later, and a late answer never reaches a cell that has moved on. The worker and
 * the main-thread hop are queues the test drains by hand, so every interleaving is explicit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AsyncIconBinderTest {

    private static final int SIZE = 48;
    private static final int TILE = 0x1FFFFFFF;

    /** A cache double: {@link #held} answers peeks, renders are counted and then held. */
    private final class FakeRenderer implements AsyncIconBinder.Renderer {
        final Map<String, Drawable> held = new HashMap<>();
        final Map<String, Drawable> renders = new HashMap<>();
        final List<String> rendered = new ArrayList<>();

        @Nullable
        @Override
        public Drawable peek(@NonNull LauncherAppEntry entry, int sizePx) {
            return held.get(entry.appRef.stableId());
        }

        @Nullable
        @Override
        public Drawable render(@NonNull LauncherAppEntry entry, int sizePx) {
            String id = entry.appRef.stableId();
            rendered.add(id);
            Drawable icon = renders.get(id);
            if (icon != null) held.put(id, icon);
            return icon;
        }
    }

    private static final class QueueExecutor implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void drain() {
            while (!queue.isEmpty()) queue.poll().run();
        }
    }

    private FakeRenderer renderer;
    private QueueExecutor worker;
    private QueueExecutor main;
    private AsyncIconBinder binder;

    @Before
    public void setUp() {
        renderer = new FakeRenderer();
        worker = new QueueExecutor();
        main = new QueueExecutor();
        binder = new AsyncIconBinder(renderer, worker, main, () -> TILE);
    }

    private static LauncherAppEntry entry(String id) {
        return new LauncherAppEntry(new AppRef("com.example." + id, "Main"), id, null);
    }

    private static Drawable icon() {
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(),
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888));
    }

    private static ImageView detachedView() {
        return new ImageView(RuntimeEnvironment.getApplication());
    }

    private static ImageView attachedView() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        ImageView view = new ImageView(activity);
        root.addView(view, new FrameLayout.LayoutParams(SIZE, SIZE));
        activity.setContentView(root);
        shadowOf(Looper.getMainLooper()).idle();
        return view;
    }

    private void runWorkerThenMain() {
        worker.drain();
        main.drain();
    }

    // ------------------------------------------------------------------ the warm path

    /** Scrolling a warm list must look exactly as it did before rendering moved off the thread. */
    @Test
    public void aHeldIcon_bindsAtOnceWithNoTileAndNoWork() {
        Drawable held = icon();
        renderer.held.put(entry("a").appRef.stableId(), held);
        ImageView view = attachedView();

        assertTrue(binder.bind(view, entry("a"), SIZE, null));

        assertSame(held, view.getDrawable());
        assertFalse(AsyncIconBinder.isPending(view));
        assertTrue("a hit must not queue anything", worker.queue.isEmpty());
        assertTrue(renderer.rendered.isEmpty());
    }

    // ------------------------------------------------------------------ the cold path

    @Test
    public void aMissingIcon_showsATileAtTheIconsSizeAndRendersOnTheWorker() {
        renderer.renders.put(entry("a").appRef.stableId(), icon());
        ImageView view = detachedView();

        assertFalse(binder.bind(view, entry("a"), SIZE, null));

        assertTrue(view.getDrawable() instanceof IconArrivalDrawable);
        assertEquals(SIZE, view.getDrawable().getIntrinsicWidth());
        assertEquals(SIZE, view.getDrawable().getIntrinsicHeight());
        assertTrue("nothing renders on the binding thread", renderer.rendered.isEmpty());
        assertEquals(1, worker.queue.size());
    }

    /** Off screen there is no one to fade for: the icon is simply set. */
    @Test
    public void anArrivalNobodyCanSee_isSetWithoutAFade() {
        Drawable rendered = icon();
        renderer.renders.put(entry("a").appRef.stableId(), rendered);
        ImageView view = detachedView();

        binder.bind(view, entry("a"), SIZE, null);
        runWorkerThenMain();

        assertSame(rendered, view.getDrawable());
        assertFalse(AsyncIconBinder.isPending(view));
    }

    @Test
    public void aVisibleArrival_fadesInOverTheTileAndThenHandsOverTheIconItself() {
        Drawable rendered = icon();
        renderer.renders.put(entry("a").appRef.stableId(), rendered);
        ImageView view = attachedView();

        binder.bind(view, entry("a"), SIZE, null);
        runWorkerThenMain();

        assertTrue(view.getDrawable() instanceof IconArrivalDrawable);
        IconArrivalDrawable tile = (IconArrivalDrawable) view.getDrawable();
        assertSame(rendered, tile.arrivingIcon());

        shadowOf(Looper.getMainLooper()).idleFor(AsyncIconBinder.FADE_DURATION_MS + 50L,
            TimeUnit.MILLISECONDS);

        assertSame("once the fade ends the view holds the shared icon, as a warm bind would",
            rendered, view.getDrawable());
        assertEquals(1f, tile.progress(), 0f);
        assertFalse(AsyncIconBinder.isPending(view));
    }

    /** A rebind for the icon already fading in must not snap it to full opacity. */
    @Test
    public void rebindingToTheIconAlreadyFadingIn_letsTheFadeFinish() {
        Drawable rendered = icon();
        renderer.renders.put(entry("a").appRef.stableId(), rendered);
        ImageView view = attachedView();
        binder.bind(view, entry("a"), SIZE, null);
        runWorkerThenMain();
        Drawable tile = view.getDrawable();

        assertTrue(binder.bind(view, entry("a"), SIZE, null));

        assertSame(tile, view.getDrawable());
        shadowOf(Looper.getMainLooper()).idleFor(AsyncIconBinder.FADE_DURATION_MS + 50L,
            TimeUnit.MILLISECONDS);
        assertSame(rendered, view.getDrawable());
    }

    // ------------------------------------------------------------------ the generation token

    /** The cell was recycled to another app while the first render was in flight. */
    @Test
    public void aLateResultForARecycledCell_isIgnored() {
        Drawable first = icon();
        Drawable second = icon();
        renderer.renders.put(entry("a").appRef.stableId(), first);
        ImageView view = detachedView();
        binder.bind(view, entry("a"), SIZE, null);
        // The first render finishes, but its answer has not reached the main thread yet.
        worker.drain();

        renderer.held.put(entry("b").appRef.stableId(), second);
        binder.bind(view, entry("b"), SIZE, null);
        main.drain();

        assertSame(second, view.getDrawable());
    }

    @Test
    public void aRequestThatWentStaleInTheQueue_isNeverRendered() {
        renderer.renders.put(entry("a").appRef.stableId(), icon());
        renderer.renders.put(entry("b").appRef.stableId(), icon());
        ImageView view = detachedView();

        binder.bind(view, entry("a"), SIZE, null);
        binder.bind(view, entry("b"), SIZE, null);
        runWorkerThenMain();

        assertEquals(Arrays.asList(entry("b").appRef.stableId()), renderer.rendered);
        assertSame(renderer.renders.get(entry("b").appRef.stableId()), view.getDrawable());
    }

    @Test
    public void aCancelledView_receivesNothing() {
        renderer.renders.put(entry("a").appRef.stableId(), icon());
        ImageView view = detachedView();
        binder.bind(view, entry("a"), SIZE, null);

        AsyncIconBinder.cancel(view);
        view.setImageDrawable(null);
        runWorkerThenMain();

        assertNull(view.getDrawable());
        assertTrue(renderer.rendered.isEmpty());
    }

    /** A path that sets its own drawable without cancelling still must not be overwritten. */
    @Test
    public void aTileReplacedBySomeoneElse_isLeftAlone() {
        renderer.renders.put(entry("a").appRef.stableId(), icon());
        ImageView view = detachedView();
        binder.bind(view, entry("a"), SIZE, null);
        Drawable mine = new ColorDrawable(0xFF00FF00);

        view.setImageDrawable(mine);
        runWorkerThenMain();

        assertSame(mine, view.getDrawable());
    }

    @Test
    public void aRenderThatProducesNothing_fallsBackToTheEntrysOwnArtwork() {
        Drawable fallback = new ColorDrawable(0xFF112233);
        ImageView view = detachedView();

        binder.bind(view, entry("a"), SIZE, fallback);
        runWorkerThenMain();

        assertSame(fallback, view.getDrawable());
    }

    @Test
    public void aRenderThatThrows_costsTheCellItsIconAndNothingElse() {
        AsyncIconBinder throwing = new AsyncIconBinder(new AsyncIconBinder.Renderer() {
            @Override
            public Drawable peek(@NonNull LauncherAppEntry entry, int sizePx) {
                return null;
            }

            @Override
            public Drawable render(@NonNull LauncherAppEntry entry, int sizePx) {
                throw new OutOfMemoryError("test");
            }
        }, worker, main, () -> TILE);
        ImageView view = detachedView();

        throwing.bind(view, entry("a"), SIZE, null);
        runWorkerThenMain();

        assertNull(view.getDrawable());
    }

    // ------------------------------------------------------------------ prefetch

    @Test
    public void prefetch_rendersOnlyWhatIsNotHeld_andOnTheWorker() {
        renderer.held.put(entry("a").appRef.stableId(), icon());
        renderer.renders.put(entry("b").appRef.stableId(), icon());

        binder.prefetch(Arrays.asList(entry("a"), entry("b")), SIZE);
        assertTrue(renderer.rendered.isEmpty());
        worker.drain();

        assertEquals(Arrays.asList(entry("b").appRef.stableId()), renderer.rendered);
        // The cell that binds next takes the immediate path.
        ImageView view = detachedView();
        assertTrue(binder.bind(view, entry("b"), SIZE, null));
    }

    @Test
    public void aNewerPrefetch_stopsAnOlderOneItHasOvertaken() {
        binder.prefetch(Arrays.asList(entry("a"), entry("b")), SIZE);
        binder.prefetch(Arrays.asList(entry("c")), SIZE);
        worker.drain();

        assertEquals(Arrays.asList(entry("c").appRef.stableId()), renderer.rendered);
    }

    @Test
    public void prefetch_isCappedSoItCannotChurnTheCacheItFills() {
        List<LauncherAppEntry> many = new ArrayList<>();
        for (int i = 0; i < AsyncIconBinder.MAX_PREFETCH * 2; i++) many.add(entry("app" + i));

        binder.prefetch(many, SIZE);
        worker.drain();

        assertEquals(AsyncIconBinder.MAX_PREFETCH, renderer.rendered.size());
    }

    // ------------------------------------------------------------------ the owner going away

    /**
     * The owner's window went away: nothing it queued is rendered meanwhile. When it returns, the
     * renders still wanted are queued again, so no cell is left showing a tile; the rest are
     * forgotten.
     */
    @Test
    public void cancelAll_parksQueuedRenders_andResumeAllRunsOnlyTheOnesStillWanted() {
        Drawable a = icon();
        renderer.renders.put(entry("a").appRef.stableId(), a);
        renderer.renders.put(entry("b").appRef.stableId(), icon());
        ImageView stillWaiting = detachedView();
        ImageView releasedMeanwhile = detachedView();
        binder.bind(stillWaiting, entry("a"), SIZE, null);
        binder.bind(releasedMeanwhile, entry("b"), SIZE, null);

        binder.cancelAll();
        runWorkerThenMain();
        assertTrue("a parked render must not run", renderer.rendered.isEmpty());
        assertEquals(2, binder.pendingCount());

        AsyncIconBinder.cancel(releasedMeanwhile);
        binder.resumeAll();
        runWorkerThenMain();

        assertEquals(Arrays.asList(entry("a").appRef.stableId()), renderer.rendered);
        assertSame(a, stillWaiting.getDrawable());
        assertEquals(0, binder.pendingCount());
    }

    @Test
    public void cancelAll_dropsPrefetches_andRefusesNewOnesUntilResumed() {
        binder.prefetch(Arrays.asList(entry("a")), SIZE);
        binder.cancelAll();
        binder.prefetch(Arrays.asList(entry("b")), SIZE);
        worker.drain();
        assertTrue(renderer.rendered.isEmpty());

        binder.resumeAll();
        binder.prefetch(Arrays.asList(entry("c")), SIZE);
        worker.drain();
        assertEquals(Arrays.asList(entry("c").appRef.stableId()), renderer.rendered);
    }

    // ------------------------------------------------------------------ the test seam

    /** What JVM tests of cells rely on: a miss renders inline and binds at once, no tile. */
    @Test
    public void theSynchronousSeam_rendersAMissInlineAndPrefetchesNothing() {
        Drawable rendered = icon();
        renderer.renders.put(entry("a").appRef.stableId(), rendered);
        ImageView view = attachedView();
        AsyncIconBinder.setSynchronousForTesting(true);
        try {
            assertTrue(binder.bind(view, entry("a"), SIZE, null));
            assertSame(rendered, view.getDrawable());
            binder.prefetch(Arrays.asList(entry("b")), SIZE);
        } finally {
            AsyncIconBinder.setSynchronousForTesting(false);
        }
        assertTrue(worker.queue.isEmpty());
        assertEquals(Arrays.asList(entry("a").appRef.stableId()), renderer.rendered);
    }

    // ------------------------------------------------------------------ a resize's rebind

    /**
     * A size drag rebinds every slot on every frame. A miss must keep the icon the slot already
     * shows until the new size lands — a tile per frame would blink the whole row — and the new
     * one then replaces it outright, with no tile to fade over.
     */
    @Test
    public void aRebindMiss_keepsTheIconItHasUntilTheNewSizeLands() {
        ImageView view = attachedView();
        Drawable old = icon();
        view.setImageDrawable(old);
        LauncherAppEntry app = entry("a");
        Drawable resized = icon();
        renderer.renders.put(app.appRef.stableId(), resized);

        assertFalse("on its way, not bound now", binder.rebind(view, app, SIZE + 4, null));
        assertSame("the old icon stands in, no tile", old, view.getDrawable());
        assertFalse(AsyncIconBinder.isPending(view));

        runWorkerThenMain();
        assertSame(resized, view.getDrawable());
    }

    /** Frames that outrun the icon thread render only the size the drag ended on. */
    @Test
    public void aRebindSupersededByTheNextFrame_isNeverRendered() {
        ImageView view = attachedView();
        view.setImageDrawable(icon());
        LauncherAppEntry app = entry("a");
        Drawable resized = icon();
        renderer.renders.put(app.appRef.stableId(), resized);

        binder.rebind(view, app, SIZE + 2, null);
        binder.rebind(view, app, SIZE + 4, null);
        runWorkerThenMain();

        assertEquals("one render, for the last frame",
            Arrays.asList(app.appRef.stableId()), renderer.rendered);
        assertSame(resized, view.getDrawable());
    }

    /** A slot with nothing settled to keep is bound the ordinary way, tile and all. */
    @Test
    public void aRebindOfAnEmptyView_isAnOrdinaryBind() {
        ImageView view = attachedView();
        LauncherAppEntry app = entry("a");
        renderer.renders.put(app.appRef.stableId(), icon());

        assertFalse(binder.rebind(view, app, SIZE, null));
        assertTrue("the ordinary tile", AsyncIconBinder.isPending(view));
    }
}
