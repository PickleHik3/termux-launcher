package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
import com.termux.app.launcher.drawer.AppDrawerRopeColumnView;
import com.termux.app.launcher.paging.PageTickStrip;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.theme.SchemeTone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The views themselves, on the band the user was looking at.
 *
 * <p>{@code GlassInkTest} proves the arithmetic; this proves the four views ask it the right
 * question. Both A&ndash;Z rails, the page ticks and the brightness floors that are not allowed
 * back.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AzLightHaloTest {

    /** Measured behind the A&ndash;Z strip in light mode on the reporting device. */
    private static final int AZ_GLASS = 0xFF657271;
    /** Measured behind the status bar in light mode on the same device. */
    private static final int BAR_GLASS = 0xFF6A5755;
    /** A genuinely pale wallpaper: the band the chrome takes the dark ink on. */
    private static final int PALE_GLASS = 0xFFDDDDDD;

    private static final int LIGHT_INK = 0xFF345CA8;
    private static final int NIGHT_INK = 0xFFB8C7FF;

    // ------------------------------------------------------------------ the dock's A-Z row

    /**
     * The row the user was pointing at. On the reporting device's own strip, in light mode, the
     * letters read and the ring around them does not out-read them — which is the pair of facts
     * that were the wrong way round.
     */
    @Test
    public void theDocksLettersAndHaloOnTheReportingDevicesStrip() {
        AzScrubRowView row = new AzScrubRowView(RuntimeEnvironment.getApplication());
        int letter = OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
        row.setTextColor(letter);
        row.setGlassBackdrop(AZ_GLASS);

        double letterRatio = OnGlass.ratio(letter, AZ_GLASS);
        int halo = row.restingHaloColor();
        double haloRatio = OnGlass.ratio(GlassInk.effective(halo, AZ_GLASS), AZ_GLASS);

        assertTrue("the letter clears the large-text floor: " + letterRatio,
            letterRatio >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue("and its halo stays under it: " + haloRatio + " vs " + letterRatio,
            haloRatio <= letterRatio);
        assertTrue("the halo still deepens the letter's edge",
            OnGlass.ratio(letter, GlassInk.effective(halo, AZ_GLASS)) > letterRatio);
        assertTrue("and the focused letter's halo is the heavier of the two",
            Color.alpha(row.focusHaloColor()) >= Color.alpha(halo));
    }

    /** The inversion, through the view: a dark letter on a pale band gets a pale halo. */
    @Test
    public void theRowsHaloInvertsWithTheLetter() {
        AzScrubRowView pale = new AzScrubRowView(RuntimeEnvironment.getApplication());
        pale.setTextColor(OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink);
        pale.setGlassBackdrop(AZ_GLASS);
        assertTrue("a pale letter keeps the near-black stroke",
            SchemeTone.tone(pale.restingHaloColor()) < SchemeTone.tone(AZ_GLASS));

        AzScrubRowView dark = new AzScrubRowView(RuntimeEnvironment.getApplication());
        dark.setTextColor(
            OnGlass.resolveBare(PALE_GLASS, LIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink);
        dark.setGlassBackdrop(PALE_GLASS);
        assertTrue("and a dark letter gets a pale one — the halo follows the letter, not the mode",
            SchemeTone.tone(dark.restingHaloColor()) > SchemeTone.tone(PALE_GLASS));
    }

    /** Before anything has been measured the row draws exactly what it drew before this round. */
    @Test
    public void anUnmeasuredRowKeepsTheStrokeItAlwaysHad() {
        AzScrubRowView row = new AzScrubRowView(RuntimeEnvironment.getApplication());
        row.setTextColor(0xFFDDE4F0);
        assertEquals(Color.TRANSPARENT, row.glassBackdrop());
        assertEquals(GlassInk.HALO_DARK & 0x00FFFFFF, row.restingHaloColor() & 0x00FFFFFF);
        assertEquals(195, Color.alpha(row.restingHaloColor()));
        assertEquals(215, Color.alpha(row.focusHaloColor()));
    }

    // ------------------------------------------------------------------ the drawer's A-Z rope

    /**
     * The other rail, over the same glass. It has no measurer and no band of its own, so it is
     * handed the dock's — and the point of handing it the settled ink rather than only the surface
     * is that the two rails then cannot face opposite ways.
     */
    @Test
    public void theDrawersRopeReadsAndAgreesWithTheDocksRail() {
        for (int band : new int[] {AZ_GLASS, BAR_GLASS, PALE_GLASS}) {
            int railLetter = OnGlass.resolveBare(band,
                SchemeTone.tone(band) > 50d ? LIGHT_INK : NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
            AppDrawerRopeColumnView rope =
                new AppDrawerRopeColumnView(RuntimeEnvironment.getApplication());
            rope.setGlassBackdrop(band);

            // With no dock attached the rope seeds from white, which is the fallback base colour.
            int letter = rope.letterInk();
            int halo = rope.restingHaloColor();
            assertTrue(hex(band) + ": the rope's letter reads — " + OnGlass.ratio(letter, band),
                OnGlass.ratio(letter, band) >= OnGlass.TARGET_LARGE_TEXT - 0.001d);
            assertEquals(hex(band) + ": and both rails' halos go the same way",
                GlassInk.haloInk(railLetter, band) & 0x00FFFFFF, halo & 0x00FFFFFF);
            assertTrue(hex(band) + ": the rope's halo stays under its letter",
                OnGlass.ratio(GlassInk.effective(halo, band), band)
                    <= OnGlass.ratio(letter, band));
        }
    }

    /** And an unmeasured rope, like an unmeasured row, is untouched. */
    @Test
    public void anUnmeasuredRopeKeepsTheStrokeItAlwaysHad() {
        AppDrawerRopeColumnView rope =
            new AppDrawerRopeColumnView(RuntimeEnvironment.getApplication());
        assertEquals(GlassInk.HALO_DARK & 0x00FFFFFF, rope.restingHaloColor() & 0x00FFFFFF);
        assertEquals(195, Color.alpha(rope.restingHaloColor()));
    }

    // ------------------------------------------------------------------ the page ticks

    /**
     * Both ticks, through the view. The tick under the page you are on tells you something, so it
     * is held to the large-text floor; a tick at rest only says there is another page there, so it
     * is held to the decoration floor — and it keeps its muted look by asking for it as an alpha
     * that is raised only where 40% of a tick cannot clear 2.0 on that band.
     */
    @Test
    public void bothTicksClearTheirOwnFloorOnEveryBand() {
        for (int band : new int[] {AZ_GLASS, BAR_GLASS, PALE_GLASS}) {
            int settled = OnGlass.resolveBare(band,
                SchemeTone.tone(band) > 50d ? LIGHT_INK : NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink;
            PageTickStripView strip =
                new PageTickStripView(RuntimeEnvironment.getApplication());
            strip.setAccentColor(LIGHT_INK);
            strip.setGlassInk(band, settled);
            strip.setPages(3, 1f);

            double active = OnGlass.ratio(GlassInk.effective(strip.tickColorAt(1), band), band);
            double resting = OnGlass.ratio(GlassInk.effective(strip.tickColorAt(0), band), band);
            assertTrue(hex(band) + ": the page being shown, " + active,
                active >= OnGlass.TARGET_LARGE_TEXT - 0.001d);
            assertTrue(hex(band) + ": and the ones at rest, " + resting,
                resting >= OnGlass.TARGET_DECORATION - 0.001d);
            assertTrue(hex(band) + ": the active tick still out-reads the resting one",
                active > resting);
            assertEquals(hex(band) + ": and every tick is on the chrome's own side",
                GlassInk.isPaleSide(settled, band),
                GlassInk.isPaleSide(GlassInk.effective(strip.tickColorAt(1), band), band));
        }
    }

    /** The most-used page keeps its warm tint, and now clears the floor while it sleeps. */
    @Test
    public void theMostUsedPagesTickStillReadsWhileItSleeps() {
        PageTickStripView strip = new PageTickStripView(RuntimeEnvironment.getApplication());
        strip.setAccentColor(LIGHT_INK);
        strip.setGlassInk(AZ_GLASS,
            OnGlass.resolveBare(AZ_GLASS, NIGHT_INK, OnGlass.TARGET_LARGE_TEXT).ink);
        strip.setPages(3, 0f, 2);
        double sleeping = OnGlass.ratio(
            GlassInk.effective(strip.tickColorAt(2), AZ_GLASS), AZ_GLASS);
        assertTrue("a sleeping dynamic tick is still a visible tick: " + sleeping,
            sleeping >= OnGlass.TARGET_DECORATION - 0.001d);
    }

    /** Unmeasured, the ticks are the accent faded by proximity, exactly as before this round. */
    @Test
    public void anUnmeasuredStripFadesTheAccentAsItAlwaysDid() {
        PageTickStripView strip = new PageTickStripView(RuntimeEnvironment.getApplication());
        strip.setAccentColor(LIGHT_INK);
        strip.setPages(3, 1f);
        assertEquals(255, Color.alpha(strip.tickColorAt(1)));
        assertEquals(Math.round(255f * PageTickStrip.INACTIVE_ALPHA),
            Color.alpha(strip.tickColorAt(0)));
        assertEquals(LIGHT_INK & 0x00FFFFFF, strip.tickColorAt(1) & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ the floors stay gone

    /**
     * The five brightness floors, and the guard that they do not come back.
     *
     * <p>They were all the same shape: {@code hsv[1] = Math.max(0.72f, ...)},
     * {@code hsv[2] = Math.max(0.78f, ...)}, {@code Math.max(hsv[2], 0.90f)} — a clamp that lifts a
     * colour so it survives a dark backdrop, which on light glass makes it vanish instead. Clamping
     * a channel back into its own legal range ({@code Math.max(0f, ...)}) is not that and is left
     * alone; raising one to a non-zero floor is, and there is exactly one way to decide how bright
     * something has to be now, which is to measure what it is drawn on.</p>
     */
    @Test
    public void theBrightnessFloorsAreGoneAndCannotComeBack() {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path source = root.resolve("src/main/java/com/termux/app");
        if (!Files.isDirectory(source)) source = root.resolve("app/src/main/java/com/termux/app");
        assertTrue("the sources have to be findable for this guard to mean anything",
            Files.isDirectory(source));

        String[] owned = {
            "AzScrubRowView.java",
            "TermuxActivity.java",
            "launcher/drawer/AppDrawerRopeColumnView.java",
            "launcher/paging/PageTickStrip.java",
            "launcher/paging/PageTickStripView.java",
            "chrome/GlassInk.java",
        };
        // hsv[n] = Math.max(<non-zero>, ...) and Math.max(hsv[n], <non-zero>), either order.
        Pattern floor = Pattern.compile(
            "hsv\\[[12]\\]\\s*=\\s*Math\\.max\\(\\s*(?!0f|0\\.0f|0d|0\\.0)[0-9.]+f?"
                + "|Math\\.max\\(\\s*hsv\\[[12]\\]\\s*,\\s*(?!0f|0\\.0f)[0-9.]+f?");
        List<String> found = new ArrayList<>();
        for (String name : owned) {
            Path file = source.resolve(name);
            assertTrue(name + " has moved; this guard has to move with it", Files.isRegularFile(file));
            String text = read(file);
            Matcher matcher = floor.matcher(text);
            while (matcher.find()) found.add(name + ": " + matcher.group().trim());
        }
        assertEquals("a brightness floor is back: " + found, 0, found.size());
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file, e);
        }
    }

    private static String hex(@ColorInt int color) {
        return "#" + String.format("%06X", color & 0x00FFFFFF);
    }
}
