package com.termux.app.surfaces;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The bottom area at 360dp with Large text (font scale 1.3). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceEditorPanelFitLargeTextTest extends AppearanceEditorPanelFitBase {

    @Override
    protected float fontScale() {
        return 1.3f;
    }
}
