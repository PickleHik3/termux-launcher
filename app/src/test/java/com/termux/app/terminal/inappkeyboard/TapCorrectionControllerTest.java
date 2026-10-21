package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.Looper;

import com.termux.app.terminal.inappkeyboard.touch.LearningGate;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.TapGeometry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TapCorrectionControllerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** Advanced three seconds a tap, so no key ever meets the per-minute cap. */
    private long mNow = 1_000_000L;

    private static TapGeometry twoKeys() {
        return TapModelStoreTest.twoKeys();
    }

    private TapCorrectionController controller(File file) {
        return new TapCorrectionController(file, Runnable::run,
            new Handler(Looper.getMainLooper()));
    }

    private Keyboard2View.TapRelease tap(int key, float x, float y, boolean plain) {
        mNow += 3_000L;
        return new Keyboard2View.TapRelease(key, key, x, y, plain, false, mNow, mNow + 60L, 0f);
    }

    private void teach(TapCorrectionController c, TapGeometry g, int key, float x, int taps) {
        for (int i = 0; i < taps; i++)
            c.observeTap(g, tap(key, x, 0.5f, true));
    }

    /** Taps that land a fifth of a key right on both keys, past the warm-up. */
    private void teachPastWarmUp(TapCorrectionController c, TapGeometry g) {
        for (int i = 0; i < LearningGate.WARM_UP_TAPS / 2 + 100; i++) {
            c.observeTap(g, tap(0, 0.7f, 0.5f, true));
            c.observeTap(g, tap(1, 1.7f, 0.5f, true));
        }
    }

    @Test
    public void disabledPassesPressesThroughAndLearnsNothing() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        TapGeometry g = twoKeys();
        teach(c, g, 0, 0.9f, 100);
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
        assertEquals(0f, c.totalTaps(), 0f);
        c.flush();
        assertFalse(file.exists());
    }

    @Test
    public void enabledLearnsAndSavesAfterTheDelayHoldingTheLastTap() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        c.setLayoutId("qwerty");
        TapGeometry g = twoKeys();
        teach(c, g, 0, 0.7f, 100);
        teach(c, g, 1, 1.7f, 100);
        // Still warming up: nothing is corrected yet.
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(TapCorrectionController.SAVE_DELAY_MS + 1));
        assertTrue(file.isFile());
        // The last tap is held for a possible take-back; the periodic save leaves it held.
        assertEquals(199f, TapModelStore.load(file).totalTaps(), 0f);
        c.flush();
        assertEquals(200f, TapModelStore.load(file).totalTaps(), 0f);
    }

    @Test
    public void correctsOnceTheWarmUpIsOver() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        teachPastWarmUp(c, g);
        assertEquals(LearningGate.STATE_ACTIVE, c.store().state());
        assertEquals(0, c.resolveTap(g, 1, 1.05f, 0.5f));
        assertEquals(1, c.resolveTap(g, 1, 1.5f, 0.5f));
    }

    @Test
    public void swipesAreNotLearned() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        for (int i = 0; i < 100; i++)
            c.observeTap(g, tap(0, 0.9f, 0.5f, false));
        c.flush();
        assertEquals(0f, c.totalTaps(), 0f);
    }

    @Test
    public void layoutIdKeepsModelsApart() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        c.setLayoutId("a");
        teachPastWarmUp(c, g);
        c.setLayoutId("b");
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
        c.setLayoutId("a");
        assertEquals(0, c.resolveTap(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void resetForgetsAndRemovesTheFile() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        teachPastWarmUp(c, g);
        c.flush();
        assertTrue(file.isFile());
        c.reset();
        assertFalse(file.exists());
        assertEquals(0f, c.totalTaps(), 0f);
        assertEquals(LearningGate.STATE_LEARNING, c.store().state());
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void reloadPicksUpAResetMadeElsewhere() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        teach(c, g, 0, 0.7f, 50);
        c.flush();
        TapModelStore.delete(file);
        c.reload();
        assertEquals(0f, c.totalTaps(), 0f);
    }

    @Test
    public void reloadPicksUpAResumeMadeElsewhere() throws Exception {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        teachPastWarmUp(c, g);
        c.store().engine().gate().pause();
        c.store().markDirty();
        c.flush();
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));

        // What Settings does: load, resume, write.
        TapModelStore settings = TapModelStore.load(file);
        assertEquals(LearningGate.STATE_PAUSED, settings.state());
        settings.engine().resume();
        TapModelStore.write(file, settings.toJson());

        c.reload();
        assertEquals(LearningGate.STATE_LEARNING, c.store().state());
        assertTrue(c.totalTaps() > 0f);
    }

    @Test
    public void disablingWritesOutWhatWasLearned() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        c.observeTap(g, tap(0, 0.7f, 0.5f, true));
        c.setEnabled(false);
        assertTrue(file.isFile());
        TapModelStore saved = TapModelStore.load(file);
        assertEquals(1f, saved.totalTaps(), 0f);
        assertFalse(saved.engine().switchedOn());
    }

    @Test
    public void turningTheSwitchBackOnStartsAWarmUp() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        teachPastWarmUp(c, g);
        c.setEnabled(false);
        c.setEnabled(true);
        assertEquals(LearningGate.STATE_LEARNING, c.store().state());
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void anInterruptionFilesTheHeldTap() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        c.observeTap(g, tap(0, 0.7f, 0.5f, true));
        assertEquals(0f, c.totalTaps(), 0f);
        c.interrupt();
        assertEquals(1f, c.totalTaps(), 0f);
    }

    /** An executor that runs nothing until told to, so a test can hold a save or a load back. */
    private static final class HeldExecutor implements Executor {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void runAll() {
            Runnable next;
            while ((next = queue.poll()) != null) next.run();
        }
    }

    /** Writes a store past its warm-up that pulls a press at 1.05 from key 1 to key 0. */
    private void writeLearnedModel(File file) {
        TapCorrectionController seed = controller(file);
        seed.setEnabled(true);
        teachPastWarmUp(seed, twoKeys());
        seed.flush();
    }

    @Test
    public void reloadWhileASaveIsPendingKeepsTheTapsThatSaveCarries() {
        File file = new File(folder.getRoot(), "m.json");
        TapGeometry g = twoKeys();
        TapCorrectionController first = controller(file);
        first.setEnabled(true);
        teach(first, g, 0, 0.7f, 10);
        first.flush();
        assertEquals(10f, TapModelStore.load(file).totalTaps(), 0f);

        HeldExecutor io = new HeldExecutor();
        TapCorrectionController c = new TapCorrectionController(file, io,
            new Handler(Looper.getMainLooper()));
        io.runAll();
        c.setEnabled(true);
        teach(c, g, 0, 0.7f, 50);
        c.flush();
        // The save is queued, not written: the file still says 10.
        assertEquals(10f, TapModelStore.load(file).totalTaps(), 0f);
        c.reload();
        assertEquals(60f, c.totalTaps(), 0f);

        io.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(60f, c.totalTaps(), 0f);
        assertEquals(60f, TapModelStore.load(file).totalTaps(), 0f);
    }

    @Test
    public void aSaveWritesWhatWasThereAtFlushNotWhatCameAfter() {
        File file = new File(folder.getRoot(), "m.json");
        TapGeometry g = twoKeys();
        HeldExecutor io = new HeldExecutor();
        TapCorrectionController c = new TapCorrectionController(file, io,
            new Handler(Looper.getMainLooper()));
        io.runAll();
        c.setEnabled(true);
        teach(c, g, 0, 0.7f, 10);
        c.flush();
        // The file text is built on the I/O thread, after these taps have landed.
        teach(c, g, 0, 0.7f, 50);
        io.runAll();
        assertEquals(10f, TapModelStore.load(file).totalTaps(), 0f);
        c.flush();
        io.runAll();
        assertEquals(60f, TapModelStore.load(file).totalTaps(), 0f);
    }

    @Test
    public void whileTheFileLoadsPressesPassThroughAndNothingIsLearned() {
        File file = new File(folder.getRoot(), "m.json");
        writeLearnedModel(file);
        float stored = TapModelStore.load(file).totalTaps();
        HeldExecutor io = new HeldExecutor();
        TapCorrectionController c = new TapCorrectionController(file, io,
            new Handler(Looper.getMainLooper()));
        c.setEnabled(true);
        TapGeometry g = twoKeys();

        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
        c.observeTap(g, tap(1, 1.7f, 0.5f, true));
        assertEquals(0f, c.totalTaps(), 0f);
        c.flush();
        assertEquals(1, io.queue.size());

        io.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, c.resolveTap(g, 1, 1.05f, 0.5f));
        assertEquals(stored, c.totalTaps(), 0f);
    }

    @Test
    public void aResetBeforeTheLoadArrivesIsNotUndoneByIt() {
        File file = new File(folder.getRoot(), "m.json");
        writeLearnedModel(file);
        HeldExecutor io = new HeldExecutor();
        TapCorrectionController c = new TapCorrectionController(file, io,
            new Handler(Looper.getMainLooper()));
        c.setEnabled(true);
        c.reset();

        io.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0f, c.totalTaps(), 0f);
        assertFalse(file.exists());
        assertEquals(1, c.resolveTap(twoKeys(), 1, 1.05f, 0.5f));
    }
}
