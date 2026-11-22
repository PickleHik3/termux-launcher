package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** What a saved shader reads back as, and where its photo and depth image live. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class ShaderStoreTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private Uri picked(String name) throws IOException {
        File f = new File(mContext.getCacheDir(), name);
        Files.write(f.toPath(), new byte[] {7, 7});
        return Uri.fromFile(f);
    }

    @Test
    public void metaRoundTripsWithAndWithoutDepth() {
        ShaderStore.Meta with = new ShaderStore.Meta("A", ShaderSafety.Quality.HALF, 30, "p.png", "d.png");
        ShaderStore.Meta back = ShaderStore.Meta.parse(with.toJson(), "x");
        assertEquals("p.png", back.texture);
        assertEquals("d.png", back.depth);
        ShaderStore.Meta without = new ShaderStore.Meta("A", ShaderSafety.Quality.HALF, 30, "p.png", null);
        assertNull(ShaderStore.Meta.parse(without.toJson(), "x").depth);
        assertFalse(without.toJson().contains("depth"));
    }

    @Test
    public void metaWrittenBeforeDepthReadsAsNoDepth() {
        ShaderStore.Meta old = ShaderStore.Meta.parse(
            "{\"name\":\"Old\",\"quality\":\"half\",\"rate\":30,\"texture\":\"p.png\"}", "x");
        assertEquals("p.png", old.texture);
        assertNull(old.depth);
    }

    @Test
    public void unsafeDepthNameIsDropped() {
        assertNull(ShaderStore.Meta.parse("{\"depth\":\"../d.png\"}", "x").depth);
        assertNull(new ShaderStore.Meta("A", ShaderSafety.Quality.HALF, 30, null, "a/b.png").depth);
    }

    @Test
    public void depthFileSitsInTheShaderFolder() {
        ShaderStore.Meta meta = new ShaderStore.Meta("A", ShaderSafety.Quality.HALF, 30, null, "d.png");
        assertEquals(new File(ShaderStore.shaderDir(mContext, "s1"), "d.png"),
            ShaderStore.depthFile(mContext, "s1", meta));
        assertNull(ShaderStore.depthFile(mContext, "s1", ShaderStore.Meta.defaults("A")));
    }

    @Test
    public void madeDepthNameIsRecognisedAndNeverATextureName() {
        assertTrue(ShaderStore.isMadeDepth(ShaderStore.MADE_DEPTH_FILE));
        assertFalse(ShaderStore.isMadeDepth("photo.png"));
        assertFalse(ShaderStore.isMadeDepth(null));
        assertNotEquals(ShaderStore.MADE_DEPTH_FILE, ShaderStore.textureName(ShaderStore.MADE_DEPTH_FILE));
    }

    @Test
    public void importedPhotoNeverTakesTheDepthName() throws IOException {
        String name = ShaderStore.importTexture(mContext, "s1", picked("same.png"), "same.png", null, "same.png");
        assertEquals("photo-same.png", name);
        assertTrue(new File(ShaderStore.shaderDir(mContext, "s1"), name).isFile());
        String made = ShaderStore.importTexture(mContext, "s1", picked("m.png"), ShaderStore.MADE_DEPTH_FILE,
            null, null);
        assertNotEquals(ShaderStore.MADE_DEPTH_FILE, made);
    }

    @Test
    public void importedDepthNeverTakesThePhotoNameOrTheMadeName() throws IOException {
        String name = ShaderStore.importDepth(mContext, "s2", picked("same2.png"), "same2.png", null, "same2.png");
        assertEquals("depth-same2.png", name);
        String made = ShaderStore.importDepth(mContext, "s2", picked("m2.png"), ShaderStore.MADE_DEPTH_FILE,
            null, null);
        assertNotEquals(ShaderStore.MADE_DEPTH_FILE, made);
        String plain = ShaderStore.importDepth(mContext, "s2", picked("d2.png"), "d2.png", name, "photo.png");
        assertEquals("d2.png", plain);
        assertFalse(new File(ShaderStore.shaderDir(mContext, "s2"), name).exists());
    }

    @Test
    public void deletingAShaderRemovesBothImages() throws IOException {
        ShaderStore.writeSource(mContext, "s3", "void mainImage(out vec4 c, in vec2 f) { c = vec4(0.0); }");
        String photo = ShaderStore.importTexture(mContext, "s3", picked("p3.png"), "p3.png", null, null);
        String depth = ShaderStore.importDepth(mContext, "s3", picked("d3.png"), "d3.png", null, photo);
        ShaderStore.writeMeta(mContext, "s3",
            new ShaderStore.Meta("S", ShaderSafety.Quality.HALF, 30, photo, depth));
        File dir = ShaderStore.shaderDir(mContext, "s3");
        assertTrue(new File(dir, photo).isFile());
        assertTrue(new File(dir, depth).isFile());
        ShaderStore.delete(mContext, "s3");
        assertFalse(new File(dir, photo).exists());
        assertFalse(new File(dir, depth).exists());
        assertFalse(dir.exists());
    }

    @Test
    public void aKeptSourceComesBackOnce_andNothingIsKeptWithoutASource() throws IOException {
        String id = ShaderStore.newId();
        assertFalse(ShaderStore.keepPreviousSource(mContext, id));
        assertFalse(ShaderStore.restorePreviousSource(mContext, id));
        ShaderStore.writeSource(mContext, id, "old");
        assertTrue(ShaderStore.keepPreviousSource(mContext, id));
        ShaderStore.writeSource(mContext, id, "new");
        assertTrue(ShaderStore.restorePreviousSource(mContext, id));
        assertEquals("old", ShaderStore.readText(ShaderStore.sourceFile(mContext, id)));
        assertFalse(ShaderStore.previousSourceFile(mContext, id).exists());

        assertTrue(ShaderStore.keepPreviousSource(mContext, id));
        ShaderStore.dropPreviousSource(mContext, id);
        assertFalse(ShaderStore.previousSourceFile(mContext, id).exists());
        assertEquals("one shader in the list, the kept copy is not another", 1,
            countId(ShaderStore.list(mContext), id));
        ShaderStore.delete(mContext, id);
    }

    @Test
    public void aPictureIsNeverStoredUnderTheKeptSourcesName() {
        assertNotEquals(ShaderStore.PREVIOUS_SOURCE_FILE, ShaderStore.textureName(ShaderStore.PREVIOUS_SOURCE_FILE));
    }

    private static int countId(java.util.List<ShaderStore.Entry> entries, String id) {
        int n = 0;
        for (ShaderStore.Entry e : entries) if (e.id.equals(id)) n++;
        return n;
    }
}
