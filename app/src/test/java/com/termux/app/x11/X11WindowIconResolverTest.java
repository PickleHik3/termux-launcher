package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A display window's {@code WM_CLASS} traced to the app that drew it, and that app's artwork
 * reduced to the silhouette a chip wears.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11WindowIconResolverTest {

    private static LinuxAppCatalog.LinuxApp app(String id, String exec, String startupWmClass) {
        return new LinuxAppCatalog.LinuxApp(id, id, exec, id, "", startupWmClass);
    }

    // --- matching -------------------------------------------------------------------------

    @Test public void theDesktopFileNameIsTheFirstKeyAndIsCaseInsensitive() {
        List<LinuxAppCatalog.LinuxApp> apps = Arrays.asList(
            app("firefox", "firefox", ""), app("xterm", "xterm", ""));
        // Firefox's main window sets WM_CLASS "navigator", "Firefox": the class is capitalised.
        assertEquals("firefox", X11WindowIconResolver.match(apps, "Firefox").id);
        assertEquals("firefox", X11WindowIconResolver.match(apps, "firefox").id);
        // xterm sets "xterm", "XTerm".
        assertEquals("xterm", X11WindowIconResolver.match(apps, "XTerm").id);
    }

    @Test public void startupWmClassIsTriedWhenTheNameDoesNotMatch() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(
            app("code-oss", "code-oss --unity-launch", "Code"));
        assertEquals("code-oss", X11WindowIconResolver.match(apps, "Code").id);
    }

    @Test public void theExecBasenameIsTriedAfterStartupWmClass() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(
            app("web-browser", "/data/data/com.termux/files/usr/bin/qutebrowser --target auto", ""));
        assertEquals("web-browser", X11WindowIconResolver.match(apps, "qutebrowser").id);
    }

    @Test public void aReverseDnsDesktopNameMatchesByItsLastSegment() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(
            app("org.kde.kate", "/usr/bin/kate-wrapper", ""));
        assertEquals("org.kde.kate", X11WindowIconResolver.match(apps, "kate").id);
    }

    @Test public void anEarlierKeyWinsOverAnotherAppsLaterOne() {
        LinuxAppCatalog.LinuxApp byName = app("gimp", "gimp-2.10", "");
        LinuxAppCatalog.LinuxApp byExec = app("photo-editor", "gimp", "");
        // The list order deliberately puts the loose match first; the key order still decides.
        assertSame(byName, X11WindowIconResolver.match(Arrays.asList(byExec, byName), "gimp"));
    }

    @Test public void nothingMatchesAnUnknownOrEmptyClass() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(app("firefox", "firefox", ""));
        assertNull(X11WindowIconResolver.match(apps, "xterm"));
        assertNull(X11WindowIconResolver.match(apps, ""));
        assertNull(X11WindowIconResolver.match(apps, "   "));
        assertNull(X11WindowIconResolver.match(Collections.emptyList(), "firefox"));
    }

    @Test public void anAppWithoutAStartupClassIsNotMatchedByAnEmptyClass() {
        List<LinuxAppCatalog.LinuxApp> apps = Collections.singletonList(app("firefox", "", ""));
        assertNull(X11WindowIconResolver.match(apps, " "));
    }

    @Test public void execBasenameDropsThePathAndTheArguments() {
        assertEquals("kate", X11WindowIconResolver.execBasename("/usr/bin/kate -b"));
        assertEquals("feh", X11WindowIconResolver.execBasename("  feh --start-at  "));
        assertEquals("", X11WindowIconResolver.execBasename(""));
    }

    // --- the silhouette -------------------------------------------------------------------

    @Test public void everyPixelBecomesWhiteAndKeepsItsShapeInTheAlpha() {
        int opaqueWhite = X11WindowIconResolver.whiteWithAlpha(Color.WHITE);
        int opaqueBlack = X11WindowIconResolver.whiteWithAlpha(Color.BLACK);

        assertEquals(Color.TRANSPARENT, X11WindowIconResolver.whiteWithAlpha(Color.TRANSPARENT));
        assertEquals(255, Color.alpha(opaqueWhite));
        // Dark artwork still reads as a shape rather than vanishing.
        assertEquals(Math.round(255 * X11WindowIconResolver.SHAPE_FLOOR), Color.alpha(opaqueBlack));
        assertTrue(Color.alpha(opaqueBlack) > 0);
        for (int pixel : new int[]{opaqueWhite, opaqueBlack}) {
            assertEquals(255, Color.red(pixel));
            assertEquals(255, Color.green(pixel));
            assertEquals(255, Color.blue(pixel));
        }
        // A bright pixel reads stronger than a dark one, and half-transparent halves both.
        assertTrue(Color.alpha(opaqueWhite) > Color.alpha(opaqueBlack));
        assertEquals(128, Color.alpha(
            X11WindowIconResolver.whiteWithAlpha(Color.argb(128, 255, 255, 255))));
    }

    @Test public void aTinyIconBecomesASquareSilhouetteAtTheChipSize() {
        // Two opaque white pixels beside two transparent ones.
        Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        source.setPixel(0, 0, Color.WHITE);
        source.setPixel(1, 0, Color.WHITE);
        source.setPixel(0, 1, Color.TRANSPARENT);
        source.setPixel(1, 1, Color.TRANSPARENT);

        Bitmap out = X11WindowIconResolver.silhouette(source, X11WindowIconResolver.SILHOUETTE_PX);
        assertNotNull(out);
        assertEquals(X11WindowIconResolver.SILHOUETTE_PX, out.getWidth());
        assertEquals(X11WindowIconResolver.SILHOUETTE_PX, out.getHeight());

        int top = out.getPixel(X11WindowIconResolver.SILHOUETTE_PX / 2, 4);
        int bottom = out.getPixel(X11WindowIconResolver.SILHOUETTE_PX / 2,
            X11WindowIconResolver.SILHOUETTE_PX - 5);
        assertEquals(255, Color.alpha(top));
        assertEquals(255, Color.red(top));
        assertEquals(255, Color.green(top));
        assertEquals(255, Color.blue(top));
        assertEquals("the transparent half stays transparent", 0, Color.alpha(bottom));
    }

    @Test public void aWideIconIsLetterboxedRatherThanStretched() {
        Bitmap source = Bitmap.createBitmap(8, 2, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 2; y++) source.setPixel(x, y, Color.WHITE);
        }
        int size = 32;
        Bitmap out = X11WindowIconResolver.silhouette(source, size);
        assertEquals(size, out.getWidth());
        assertEquals(size, out.getHeight());
        // 8x2 scaled to fit 32 is 32x8, centred: the middle band is painted, the top is not.
        assertEquals(255, Color.alpha(out.getPixel(size / 2, size / 2)));
        assertEquals(0, Color.alpha(out.getPixel(size / 2, 1)));
        assertEquals(0, Color.alpha(out.getPixel(size / 2, size - 2)));
    }

    @Test public void aSizeOfZeroStillProducesABitmap() {
        Bitmap source = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        Bitmap out = X11WindowIconResolver.silhouette(source, 0);
        assertEquals(1, out.getWidth());
        assertEquals(1, out.getHeight());
    }
}
