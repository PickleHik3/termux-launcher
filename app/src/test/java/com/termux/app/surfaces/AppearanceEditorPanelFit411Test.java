package com.termux.app.surfaces;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The bottom area at 411dp. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w411dp-h860dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceEditorPanelFit411Test extends AppearanceEditorPanelFitBase {
}
