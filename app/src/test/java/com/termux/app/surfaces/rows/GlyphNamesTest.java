package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlyphNamesTest {

    @Test
    public void aBareGlyphCanBeLongPressedSoItsNameShows() {
        ImageView glyph = new ImageView(RuntimeEnvironment.getApplication());
        GlyphNames.name(glyph, "Type");
        assertTrue(glyph.isLongClickable());
        assertEquals("Type", String.valueOf(GlyphNames.of(glyph)));
    }
}
