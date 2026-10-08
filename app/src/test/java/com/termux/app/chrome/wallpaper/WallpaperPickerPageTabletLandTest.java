package com.termux.app.chrome.wallpaper;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The wallpaper picker page on a 1280 x 800 dp tablet in landscape: the cards are held by the
 * height, the strip card stays below them, and nothing is cut off.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w1280dp-h800dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPickerPageTabletLandTest extends WallpaperPickerPageTestBase {
}
