package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The wallpaper button's picture: exactly its pixel size, rounded, never the picture's size. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperThumbTest {

    @Test
    public void rendersAtTheRequestedSizeWhateverThePictureSize() {
        ColorDrawable big = new ColorDrawable(Color.RED);
        big.setBounds(0, 0, 4000, 8000);
        Bitmap thumb = WallpaperThumb.render(big, 70);
        assertNotNull(thumb);
        assertEquals(70, thumb.getWidth());
        assertEquals(70, thumb.getHeight());
        assertEquals(Color.RED, thumb.getPixel(35, 35));
        assertEquals("corners are cut", 0, thumb.getPixel(0, 0));
    }

    @Test
    public void noSizeNoPicture() {
        assertNull(WallpaperThumb.render(new ColorDrawable(Color.RED), 0));
    }
}
