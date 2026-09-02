package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.Looper;

import juloo.keyboard2.TapGeometry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TapCorrectionControllerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static TapGeometry twoKeys() {
        return new TapGeometry(new float[]{0, 1}, new float[]{0, 0}, new float[]{1, 2},
            new float[]{1, 1}, new int[]{0, 0}, new boolean[]{true, true}, "two");
    }

    private TapCorrectionController controller(File file) {
        return new TapCorrectionController(file, Runnable::run,
            new Handler(Looper.getMainLooper()));
    }

    @Test
    public void disabledPassesPressesThroughAndLearnsNothing() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        TapGeometry g = twoKeys();
        for (int i = 0; i < 100; i++)
            c.observeTap(g, 0, 0.9f, 0.5f, false);
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
        assertEquals(0f, c.totalTaps(), 0f);
        c.flush();
        assertFalse(file.exists());
    }

    @Test
    public void enabledLearnsAndSavesAfterTheDelay() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        c.setLayoutId("qwerty");
        TapGeometry g = twoKeys();
        for (int i = 0; i < 100; i++)
            c.observeTap(g, 0, 0.7f, 0.5f, false);
        for (int i = 0; i < 100; i++)
            c.observeTap(g, 1, 1.7f, 0.5f, false);
        assertEquals(0, c.resolveTap(g, 1, 1.05f, 0.5f));
        assertFalse(file.exists());
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(TapCorrectionController.SAVE_DELAY_MS + 1));
        assertTrue(file.isFile());
        assertEquals(200f, TapModelStore.load(file).totalTaps(), 0f);
    }

    @Test
    public void swipesAreNotLearned() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        for (int i = 0; i < 100; i++)
            c.observeTap(g, 0, 0.9f, 0.5f, true);
        assertEquals(0f, c.totalTaps(), 0f);
    }

    @Test
    public void layoutIdKeepsModelsApart() {
        TapCorrectionController c = controller(new File(folder.getRoot(), "m.json"));
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        c.setLayoutId("a");
        for (int i = 0; i < 100; i++)
            c.observeTap(g, 0, 0.7f, 0.5f, false);
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
        for (int i = 0; i < 50; i++)
            c.observeTap(g, 0, 0.7f, 0.5f, false);
        c.flush();
        assertTrue(file.isFile());
        c.reset();
        assertFalse(file.exists());
        assertEquals(0f, c.totalTaps(), 0f);
        assertEquals(1, c.resolveTap(g, 1, 1.05f, 0.5f));
    }

    @Test
    public void reloadPicksUpAResetMadeElsewhere() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        for (int i = 0; i < 50; i++)
            c.observeTap(g, 0, 0.7f, 0.5f, false);
        c.flush();
        TapModelStore.delete(file);
        c.reload();
        assertEquals(0f, c.totalTaps(), 0f);
    }

    @Test
    public void disablingWritesOutWhatWasLearned() {
        File file = new File(folder.getRoot(), "m.json");
        TapCorrectionController c = controller(file);
        c.setEnabled(true);
        TapGeometry g = twoKeys();
        c.observeTap(g, 0, 0.7f, 0.5f, false);
        c.setEnabled(false);
        assertTrue(file.isFile());
        assertEquals(1f, TapModelStore.load(file).totalTaps(), 0f);
    }
}
