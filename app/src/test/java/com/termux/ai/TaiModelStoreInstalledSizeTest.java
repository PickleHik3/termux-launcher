package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class TaiModelStoreInstalledSizeTest {

    private static void write(File file, int bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
    }

    @Test
    public void packageInModelFolder_countsEveryFile() throws Exception {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        File dir = new File(store.getModelsDirectory(), "kitten-test");
        dir.mkdirs();
        File main = new File(dir, "predictor.tflite");
        write(main, 100);
        write(new File(dir, "voices.npz"), 40);

        assertEquals(140L, store.installedSize("kitten-test", main));
    }

    @Test
    public void fileOutsideModelFolder_countsItself() throws Exception {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        File dir = new File(store.getModelsDirectory(), "shared");
        dir.mkdirs();
        File main = new File(dir, "a.litertlm");
        write(main, 100);
        write(new File(dir, "b.litertlm"), 40);

        assertEquals(100L, store.installedSize("other-id", main));
    }
}
