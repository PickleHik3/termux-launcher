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
    public void declaredSidecarsCountAndRuntimeCachesDoNot() throws Exception {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        File dir = new File(store.getModelsDirectory(), TaiModelCatalog.KITTEN_TTS_NANO_ID);
        dir.mkdirs();
        File main = new File(dir, KittenTtsRuntime.PREDICTOR_FILE);
        write(main, 100);
        write(new File(dir, KittenTtsRuntime.VOICES_FILE), 40);
        write(new File(dir, "x.litertlm_123_456.xnnpack_cache"), 999);

        assertEquals(140L, store.installedSize(TaiModelCatalog.KITTEN_TTS_NANO_ID, main));
    }

    @Test
    public void singleFileModelCountsItselfNotItsCache() throws Exception {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        File dir = new File(store.getModelsDirectory(), TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID);
        dir.mkdirs();
        File main = new File(dir, "embeddinggemma-2-text-vision-440m.litertlm");
        write(main, 100);
        write(new File(dir, "embeddinggemma.xnnpack_cache"), 999);

        assertEquals(100L, store.installedSize(TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID, main));
    }
}
