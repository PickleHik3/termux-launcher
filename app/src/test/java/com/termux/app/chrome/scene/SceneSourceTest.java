package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** What the stored choice plays: the files, key and rate the scene and the still both read. */
@RunWith(RobolectricTestRunner.class)
public class SceneSourceTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private File put(File file) throws IOException {
        File parent = file.getParentFile();
        assertTrue(parent.isDirectory() || parent.mkdirs());
        Files.write(file.toPath(), new byte[] {1, 2, 3});
        return file;
    }

    @Test
    public void photoPlaysNothing() {
        assertNull(SceneSource.resolve(mContext, WallpaperMotion.photo(), 30, null));
        assertNull(SceneSource.resolve(mContext, WallpaperMotion.photo(), 30, ShaderStore.Meta.defaults("x")));
    }

    @Test
    public void videoPlaysItsPlayableVersion() throws IOException {
        File version = put(new File(VideoImport.dir(mContext), VideoVariantRules.variantFileName(60)));
        SceneSource source = SceneSource.resolve(mContext, WallpaperMotion.video("loop.mp4"), 60, null);
        assertNotNull(source);
        assertEquals("video:loop.mp4@" + version.getName(), source.key());
        assertEquals(source.key(), source.spec().key);
        assertEquals(version, source.spec().video);
        assertEquals(version, source.spec().source);
        assertEquals(60, source.rateHz());
    }

    @Test
    public void videoWithoutAVersionPlaysTheImportFile() throws IOException {
        File imported = put(VideoImport.file(mContext, "old.mp4"));
        SceneSource source = SceneSource.resolve(mContext, WallpaperMotion.video("old.mp4"), 15, null);
        assertNotNull(source);
        assertEquals("video:old.mp4@old.mp4", source.key());
        assertEquals(imported, source.spec().video);
        assertEquals(15, source.rateHz());
    }

    @Test
    public void videoWithNoFileIsNull() {
        assertNull(SceneSource.resolve(mContext, WallpaperMotion.video("gone.mp4"), 30, null));
    }

    @Test
    public void shaderWithMetaPlaysItsFiles() {
        ShaderStore.Meta meta = new ShaderStore.Meta("Tunnel", ShaderSafety.Quality.QUARTER, 60, "tex.png");
        SceneSource source = SceneSource.resolve(mContext, WallpaperMotion.shader("tunnel"), 30, meta);
        assertNotNull(source);
        assertEquals("shader:tunnel", source.key());
        assertEquals(source.key(), source.spec().key);
        assertEquals(ShaderStore.sourceFile(mContext, "tunnel"), source.spec().source);
        assertEquals(ShaderStore.textureFile(mContext, "tunnel", meta), source.spec().channel0);
        assertEquals(ShaderSafety.Quality.QUARTER, source.spec().quality);
        assertNull(source.spec().video);
        assertEquals(60, source.rateHz());
    }

    @Test
    public void shaderWithoutMetaIsNull() {
        assertNull(SceneSource.resolve(mContext, WallpaperMotion.shader("tunnel"), 30, null));
    }
}
