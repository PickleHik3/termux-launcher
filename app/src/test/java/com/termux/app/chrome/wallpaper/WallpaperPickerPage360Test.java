package com.termux.app.chrome.wallpaper;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.app.Application;

/** The wallpaper picker page at 360dp. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPickerPage360Test extends WallpaperPickerPageTestBase {
}
