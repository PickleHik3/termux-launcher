package com.termux.app.chrome.wallpaper;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The wallpaper picker page at 411dp. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w411dp-h860dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPickerPage411Test extends WallpaperPickerPageTestBase {
}
