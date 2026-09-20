package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ManagedWallpaperSourceTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    /** Runs what it is given only when asked, so the test sees the in-flight state. */
    private static final class HeldExecutor implements java.util.concurrent.Executor {
        final List<Runnable> queued = new ArrayList<>();
        @Override public void execute(Runnable command) { queued.add(command); }
        void runAll() {
            List<Runnable> now = new ArrayList<>(queued);
            queued.clear();
            for (Runnable r : now) r.run();
        }
    }

    private File png(int width, int height) throws Exception {
        File file = temporary.newFile("wallpaper-" + width + "x" + height + ".png");
        write(file, width, height);
        return file;
    }

    private static void write(File file, int width, int height) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void sampleSize_isTheLargestPowerOfTwoThatStillCovers() {
        assertEquals(1, ManagedWallpaperSource.sampleSize(2102, 4696, 1080, 2412));
        assertEquals(2, ManagedWallpaperSource.sampleSize(4204, 9392, 1080, 2412));
        assertEquals(1, ManagedWallpaperSource.sampleSize(800, 600, 1080, 2412));
    }

    @Test
    public void decodeCover_scalesDownToCoverTheFrameAndNoFurther() throws Exception {
        Bitmap bitmap = ManagedWallpaperSource.decodeCover(png(2000, 1000), 500, 400);
        assertNotNull(bitmap);
        // Height is the binding side: 400/1000 = 0.4, so 800x400 covers 500x400 exactly.
        assertEquals(800, bitmap.getWidth());
        assertEquals(400, bitmap.getHeight());

        Bitmap small = ManagedWallpaperSource.decodeCover(png(300, 200), 500, 400);
        assertNotNull(small);
        // Never scaled up: the shader stretches a small picture, and it would only cost memory.
        assertEquals(300, small.getWidth());
    }

    @Test
    public void obtain_readsOffThreadOnceAndHoldsTheResult() throws Exception {
        HeldExecutor executor = new HeldExecutor();
        List<Runnable> main = new ArrayList<>();
        ManagedWallpaperSource source = new ManagedWallpaperSource(executor, main::add);
        int[] ready = {0};
        File file = png(400, 800);

        assertNull(source.obtain(file, 100, 200, () -> ready[0]++));
        assertTrue(source.isReading());
        // A second miss while reading queues no second read.
        assertNull(source.obtain(file, 100, 200, () -> ready[0]++));
        assertEquals(1, executor.queued.size());

        executor.runAll();
        for (Runnable r : new ArrayList<>(main)) r.run();
        assertFalse(source.isReading());
        assertEquals(1, ready[0]);

        Bitmap held = source.obtain(file, 100, 200, () -> ready[0]++);
        assertNotNull(held);
        assertSame(held, source.obtain(file, 100, 200, () -> ready[0]++));
        assertTrue(executor.queued.isEmpty());

        // A rewritten file is a new identity and reads again.
        write(file, 600, 900);
        assertTrue(file.setLastModified(file.lastModified() + 5000));
        assertNull(source.obtain(file, 100, 200, () -> ready[0]++));
        assertEquals(1, executor.queued.size());
    }

    @Test
    public void obtain_remembersAFileThatWillNotDecode() throws Exception {
        HeldExecutor executor = new HeldExecutor();
        List<Runnable> main = new ArrayList<>();
        ManagedWallpaperSource source = new ManagedWallpaperSource(executor, main::add);
        File file = new File(temporary.getRoot(), "gone.png");
        assertNull(source.obtain(file, 100, 200, () -> {}));
        executor.runAll();
        for (Runnable r : new ArrayList<>(main)) r.run();
        assertFalse(source.isReading());
        assertNull(source.obtain(file, 100, 200, () -> {}));
        assertTrue("no second read of a file that failed", executor.queued.isEmpty());
    }
}
