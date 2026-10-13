package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Contact chips: the photo when one has decoded, the monogram otherwise; and the photo store's
 * bounds. The decode itself needs a real provider and is left to the device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CommandPaletteContactPhotosTest {

    private static final String URI = "content://contacts/7/photo";

    // ------------------------------------------------------------------ photo or monogram

    @Test
    public void aContactWithoutAPhoto_wearsItsMonogram() {
        CommandPaletteView.Keycap chip = CommandPaletteView.Keycap.contact("Sam", "Sam Rivera",
            null);
        assertTrue(chip.drawsMonogram());
        assertEquals("S", chip.initial);
    }

    @Test
    public void aPhotoReplacesTheMonogram_andNothingElse() {
        CommandPaletteView.Keycap monogram = CommandPaletteView.Keycap.contact("Sam",
            "Sam Rivera", null);
        Drawable photo = new ColorDrawable(Color.RED);
        CommandPaletteView.Keycap withPhoto = monogram.withPhoto(photo);

        assertFalse(withPhoto.drawsMonogram());
        assertSame(photo, withPhoto.icon);
        assertEquals(monogram.kind, withPhoto.kind);
        assertEquals(monogram.label, withPhoto.label);
        assertEquals(monogram.initial, withPhoto.initial);
    }

    @Test
    public void theStoreAnswers_onlyForTheContactAndPictureItDecoded() {
        CommandPaletteContactPhotos photos = new CommandPaletteContactPhotos();
        Drawable photo = new ColorDrawable(Color.RED);
        photos.put(CommandPaletteContactPhotos.keyOf(7, URI), photo, 100);

        assertSame(photo, photos.cached(7, URI));
        assertNull("another contact", photos.cached(8, URI));
        assertNull("a changed photo", photos.cached(7, URI + "?v=2"));
        assertNull("no photo at all", photos.cached(7, null));
        assertNull(photos.cached(7, ""));
    }

    @Test
    public void anUnreadablePhoto_decodesToNothing() {
        assertNull(CommandPaletteContactPhotos.decode(
            ApplicationProvider.getApplicationContext().getContentResolver(),
            "file:///nonexistent/photo.jpg", 98));
    }

    // ------------------------------------------------------------------ bounds

    @Test
    public void aDecodedPhotoIsTwiceTheMarkInPixels() {
        assertEquals(98, CommandPaletteContactPhotos.targetPx(3.5f));
        assertEquals(84, CommandPaletteContactPhotos.targetPx(3f));
    }

    @Test
    public void sampling_readsNoMorePixelsThanThePhotoNeeds() {
        // A 96 px thumbnail is already under a 98 px target: read whole.
        assertEquals(1, CommandPaletteContactPhotos.sampleSize(96, 96, 98));
        // 720 → 180 is the last halving that stays at or above 98.
        assertEquals(4, CommandPaletteContactPhotos.sampleSize(720, 720, 98));
        // The shorter side decides.
        assertEquals(4, CommandPaletteContactPhotos.sampleSize(1000, 500, 100));
    }

    @Test
    public void budget_holdsSixteenPhotosAtTheDensestCommonScreen() {
        long perPhoto = 4L * CommandPaletteContactPhotos.targetPx(4f)
            * CommandPaletteContactPhotos.targetPx(4f);
        assertEquals(16, CommandPaletteContactPhotos.MAX_PHOTOS);
        assertEquals(1024L * 1024L, CommandPaletteContactPhotos.BUDGET_BYTES);
        assertTrue(CommandPaletteContactPhotos.MAX_PHOTOS * perPhoto
            <= CommandPaletteContactPhotos.BUDGET_BYTES);
    }

    @Test
    public void theStoreKeepsSixteen_droppingThePhotoShownLeastRecently() {
        CommandPaletteContactPhotos photos = new CommandPaletteContactPhotos();
        for (int id = 0; id < CommandPaletteContactPhotos.MAX_PHOTOS; id++)
            photos.put(CommandPaletteContactPhotos.keyOf(id, URI), new ColorDrawable(), 10);
        // Showing the first again makes the second the eldest.
        assertTrue(photos.cached(0, URI) != null);
        photos.put(CommandPaletteContactPhotos.keyOf(99, URI), new ColorDrawable(), 10);

        assertEquals(CommandPaletteContactPhotos.MAX_PHOTOS, photos.size());
        assertTrue(photos.cached(0, URI) != null);
        assertNull(photos.cached(1, URI));
        assertTrue(photos.cached(99, URI) != null);
        assertEquals(10L * CommandPaletteContactPhotos.MAX_PHOTOS, photos.bytes());
    }

    @Test
    public void theStoreKeepsWithinItsBytes_evenWithRoomForMorePhotos() {
        CommandPaletteContactPhotos photos = new CommandPaletteContactPhotos();
        long third = CommandPaletteContactPhotos.BUDGET_BYTES / 3;
        for (int id = 0; id < 4; id++)
            photos.put(CommandPaletteContactPhotos.keyOf(id, URI), new ColorDrawable(), third);

        assertEquals(3, photos.size());
        assertNull(photos.cached(0, URI));
        assertTrue(photos.bytes() <= CommandPaletteContactPhotos.BUDGET_BYTES);
    }
}
