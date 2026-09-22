package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

/**
 * The freedesktop icon lookup {@link LinuxAppIcons} runs for a Linux app: hicolor's PNG sizes,
 * then its scalable SVG, then pixmaps — and the decode/render that turns whichever file wins into
 * artwork the drawer can draw.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LinuxAppIconsTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    /** A minimal but well-formed SVG: a single square, sized so scaling math is easy to check. */
    private static final String VALID_SVG =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"512\" height=\"512\" "
            + "viewBox=\"0 0 512 512\"><rect width=\"512\" height=\"512\" fill=\"#f00\"/></svg>";

    private Resources resources() {
        return RuntimeEnvironment.getApplication().getResources();
    }

    private File writeText(File dir, String name, String content) throws IOException {
        File file = new File(dir, name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writePng(File dir, String name, int width, int height) throws IOException {
        File file = new File(dir, name);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    // --- find() -----------------------------------------------------------------------------

    @Test public void anSvgIsFoundByNameUnderScalable() throws IOException {
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/icons/hicolor/scalable/apps");
        assertTrue(dir.mkdirs());
        File svg = writeText(dir, "typora.svg", VALID_SVG);

        assertEquals(svg, LinuxAppIcons.find("typora", prefix));
    }

    @Test public void aRealRasterSizeWinsOverAScalableSvgOfTheSameName() throws IOException {
        File prefix = temp.newFolder("prefix");
        File scalableDir = new File(prefix, "share/icons/hicolor/scalable/apps");
        File smallDir = new File(prefix, "share/icons/hicolor/32x32/apps");
        assertTrue(scalableDir.mkdirs());
        assertTrue(smallDir.mkdirs());
        writeText(scalableDir, "kate.svg", VALID_SVG);
        File png = writePng(smallDir, "kate.png", 32, 32);

        // Even a small hand-made raster beats a scalable vector: real pixels beat a redraw.
        assertEquals(png, LinuxAppIcons.find("kate", prefix));
    }

    @Test public void anAbsoluteSvgPathIsAcceptedAsIs() throws IOException {
        File dir = temp.newFolder("elsewhere");
        File svg = writeText(dir, "app.svg", VALID_SVG);

        assertEquals(svg, LinuxAppIcons.find(svg.getAbsolutePath(), new File("/unused")));
    }

    @Test public void anSvgUnderPixmapsIsFoundWhenHicolorHasNothing() throws IOException {
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/pixmaps");
        assertTrue(dir.mkdirs());
        File svg = writeText(dir, "feh.svg", VALID_SVG);

        assertEquals(svg, LinuxAppIcons.find("feh", prefix));
    }

    @Test public void aBareNameWithoutAnyIconFileFindsNothing() throws IOException {
        File prefix = temp.newFolder("prefix");
        assertNull(LinuxAppIcons.find("missing", prefix));
    }

    // --- find() for a whole desktop -----------------------------------------------------------

    private ProotDistro.Container prefixContainer(File dir) {
        return new ProotDistro.Container("", dir, "", "");
    }

    private ProotDistro.Container distroContainer(String name, File rootfs) {
        return new ProotDistro.Container(name, rootfs, "", "");
    }

    private LinuxAppCatalog.LinuxApp session(ProotDistro.Container container, String desktopFile,
                                            String icon, String desktopNames) {
        return new LinuxAppCatalog.LinuxApp(container, desktopFile, desktopFile, desktopFile, icon,
            "", "", false, true, desktopNames);
    }

    private LinuxAppCatalog.LinuxApp application(ProotDistro.Container container,
                                                String desktopFile, String icon) {
        return new LinuxAppCatalog.LinuxApp(container, desktopFile, desktopFile, desktopFile, icon,
            "", "", false, false, "");
    }

    @Test public void aDesktopWithNoIconOfItsOwnBorrowsItsOwnApplicationIcon() throws IOException {
        // Termux's xfce.desktop ships `Icon=` with nothing after it; libxfce4ui ships the picture.
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/icons/hicolor/128x128/apps");
        assertTrue(dir.mkdirs());
        File logo = writePng(dir, "xfce4-logo.png", 128, 128);

        assertEquals(logo,
            LinuxAppIcons.find(session(prefixContainer(prefix), "xfce", "", "XFCE")));
    }

    @Test public void aDesktopThatNamesAnIconThatResolvesKeepsIt() throws IOException {
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/icons/hicolor/128x128/apps");
        assertTrue(dir.mkdirs());
        File own = writePng(dir, "xfce.png", 128, 128);
        writePng(dir, "xfce4-logo.png", 128, 128);

        assertEquals(own,
            LinuxAppIcons.find(session(prefixContainer(prefix), "xfce", "xfce", "XFCE")));
    }

    @Test public void aDesktopIsMatchedByItsFileNameWhenItNamesNoDesktopNames() throws IOException {
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/pixmaps");
        assertTrue(dir.mkdirs());
        File png = writePng(dir, "openbox.png", 48, 48);

        assertEquals(png, LinuxAppIcons.find(session(prefixContainer(prefix), "openbox", "", "")));
    }

    @Test public void aDesktopNoPackageShipsAnIconForFindsNothing() throws IOException {
        // i3, IceWM, Plasma and the small window managers ship no icon at a path this searches,
        // so the drawer's own stand-in takes over rather than a name that resolves nowhere.
        File prefix = temp.newFolder("prefix");
        assertNull(LinuxAppIcons.find(session(prefixContainer(prefix), "i3", "", "i3")));
    }

    @Test public void anApplicationNeverBorrowsADesktopsIcon() throws IOException {
        File prefix = temp.newFolder("prefix");
        File dir = new File(prefix, "share/icons/hicolor/128x128/apps");
        assertTrue(dir.mkdirs());
        writePng(dir, "xfce4-logo.png", 128, 128);

        assertNull(LinuxAppIcons.find(application(prefixContainer(prefix), "xfce", "")));
    }

    @Test public void aDesktopInsideAContainerBorrowsTheIconInItsRootfs() throws IOException {
        File rootfs = temp.newFolder("debian");
        File dir = new File(rootfs, "usr/share/icons/hicolor/scalable/apps");
        assertTrue(dir.mkdirs());
        File svg = writeText(dir, "lxqt.svg", VALID_SVG);

        assertEquals(svg,
            LinuxAppIcons.find(session(distroContainer("debian", rootfs), "lxqt", "", "LXQt")));
    }

    @Test public void desktopIconNamesReadDesktopNamesFirstThenTheFileName() {
        ProotDistro.Container prefix = prefixContainer(new File("/unused"));
        assertEquals(Collections.singletonList("mate-desktop"),
            LinuxAppIcons.desktopIconNames(session(prefix, "mate", "", "MATE")));
        // A DesktopNames list is semicolon-separated and its case is the desktop's own.
        assertEquals(Collections.singletonList("cinnamon"),
            LinuxAppIcons.desktopIconNames(session(prefix, "cinnamon2d", "", "X-Cinnamon;")));
        assertTrue(LinuxAppIcons.desktopIconNames(
            session(prefix, "herbstluftwm", "", "")).isEmpty());
        assertTrue(LinuxAppIcons.desktopIconNames(application(prefix, "xfce", "")).isEmpty());
    }

    // --- load() -------------------------------------------------------------------------------

    @Test public void aValidSvgRendersToABitmapWithinTheEdgeBudget() throws IOException {
        File dir = temp.newFolder("icons");
        File svg = writeText(dir, "typora.svg", VALID_SVG);

        Drawable drawable = LinuxAppIcons.load(resources(), svg);

        assertNotNull(drawable);
        assertTrue(drawable instanceof BitmapDrawable);
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        assertTrue(bitmap.getWidth() <= LinuxAppIcons.MAX_EDGE_PX);
        assertTrue(bitmap.getHeight() <= LinuxAppIcons.MAX_EDGE_PX);
        // The fixture is a 512x512 square; it must be downscaled to exactly the edge budget.
        assertEquals(LinuxAppIcons.MAX_EDGE_PX, bitmap.getWidth());
        assertEquals(LinuxAppIcons.MAX_EDGE_PX, bitmap.getHeight());
    }

    @Test public void aMalformedSvgReturnsNullRatherThanThrowing() throws IOException {
        File dir = temp.newFolder("icons");
        File svg = writeText(dir, "broken.svg", "<svg><this is not><valid");

        assertNull(LinuxAppIcons.load(resources(), svg));
    }

    @Test public void anSvgWithNoRootElementReturnsNull() throws IOException {
        File dir = temp.newFolder("icons");
        File svg = writeText(dir, "empty.svg", "not an svg at all");

        assertNull(LinuxAppIcons.load(resources(), svg));
    }

    @Test public void aPngStillDecodesThroughTheSameEntryPoint() throws IOException {
        File dir = temp.newFolder("icons");
        File png = writePng(dir, "gimp.png", 256, 256);

        Drawable drawable = LinuxAppIcons.load(resources(), png);

        assertNotNull(drawable);
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        assertTrue(bitmap.getWidth() <= LinuxAppIcons.MAX_EDGE_PX);
        assertTrue(bitmap.getHeight() <= LinuxAppIcons.MAX_EDGE_PX);
    }
}
