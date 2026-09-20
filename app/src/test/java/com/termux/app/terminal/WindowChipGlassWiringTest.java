package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.app.chrome.WallpaperBlurCache;
import com.termux.app.statusbar.StatusBarWindowColumn;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Arrays;

/**
 * That the chips actually wear what was measured for them.
 *
 * <p>{@link WindowChipInkTest} is the arithmetic; this is the wiring — a bar and a column handed a
 * real {@link ChromeInk} over a band standing in for the wallpaper the user reported, and what
 * lands on the views afterwards. Without it the arithmetic could be right and never reach a
 * pixel, which is the failure mode that produced this round in the first place.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WindowChipGlassWiringTest {

    /** Measured behind the bars in light mode; the bug's own backdrop. */
    private static final int GLASS_LIGHT = 0xFF6A5755;

    private Context context;
    private BandSurfaces surfaces;
    private ChromeRenderer chrome;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        surfaces = new BandSurfaces(context);
        chrome = new ChromeRenderer(surfaces, null);
    }

    // ------------------------------------------------------------------ the row

    @Test
    public void withNoChromeTheBarKeepsTheAuthoredPalette() {
        TerminalWindowBar bar = new TerminalWindowBar(context, null);
        bar.setWindows(Arrays.asList(new WindowItemFixture().item("herdr"),
            new WindowItemFixture().item("zbook")), 1);
        assertNull("nothing was measured, so nothing was derived", bar.glassPalette());
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        // The authored resting label: the muted neutral at alpha 148, exactly as before.
        assertEquals(148, Color.alpha(((TextView) tabs.getChildAt(0)).getCurrentTextColor()));
    }

    @Test
    public void theBarDressesItselfFromTheBandItIsStandingOn() {
        TerminalWindowBar bar = dressedBar();
        WindowChipInk.Palette palette = bar.glassPalette();
        assertNotNull("the band was measured", palette);

        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        int resting = ((TextView) tabs.getChildAt(0)).getCurrentTextColor();
        int selected = ((TextView) tabs.getChildAt(1)).getCurrentTextColor();
        assertEquals(palette.restingLabel, resting);
        assertEquals(palette.selectedLabel, selected);
        // Which is the whole point: on this band the authored label read 1.03:1.
        assertTrue(OnGlass.ratio(resting, palette.restingGround) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(selected, palette.selectedGround) >= OnGlass.TARGET_BODY_TEXT);
        assertTrue("and the label is drawn solid, not at an alpha",
            Color.alpha(resting) == 255 && Color.alpha(selected) == 255);

        // The outline the user said was the only surviving part, now at the graphics floor.
        assertTrue(OnGlass.ratio(palette.restingStroke, palette.band) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(palette.selectedStroke, palette.band) >= OnGlass.TARGET_LARGE_TEXT);

        // The halo is the chip's own surface now, so it can only ever help the title.
        assertEquals(palette.restingSurface & 0x00FFFFFF,
            ((TextView) tabs.getChildAt(0)).getShadowColor() & 0x00FFFFFF);
    }

    /** Detaching the chrome puts the authored palette back rather than freezing a stale one. */
    @Test
    public void droppingTheChromePutsTheAuthoredPaletteBack() {
        TerminalWindowBar bar = dressedBar();
        assertNotNull(bar.glassPalette());
        bar.setChromeInk(null);
        assertNull(bar.glassPalette());
        LinearLayout tabs = (LinearLayout) bar.getChildAt(0);
        assertEquals(148, Color.alpha(((TextView) tabs.getChildAt(0)).getCurrentTextColor()));
    }

    // ------------------------------------------------------------------ the column

    @Test
    public void theColumnDressesItsMarksFromTheBandItIsStandingOn() {
        ownStatusBand();
        StatusBarWindowColumn column = new StatusBarWindowColumn(context, null);
        column.setChromeInk(chrome.ink());
        column.setWindows(Arrays.asList(new WindowItemFixture().item("herdr"),
            new WindowItemFixture().attention().item("zbook")), 0);
        WindowChipInk.Palette palette = column.glassPalette();
        assertNotNull("the status band was measured", palette);

        LinearLayout stack = (LinearLayout) column.getChildAt(0);
        TextView selected = (TextView) stack.getChildAt(0);
        TextView ringing = (TextView) stack.getChildAt(1);
        assertEquals(255, Color.alpha(selected.getCurrentTextColor()));
        assertEquals(255, Color.alpha(ringing.getCurrentTextColor()));
        assertTrue("the chip's glyph is 11sp text, so it is body text",
            OnGlass.ratio(ringing.getCurrentTextColor(), palette.restingSurface)
                >= OnGlass.TARGET_BODY_TEXT);
        assertTrue(OnGlass.ratio(selected.getCurrentTextColor(), palette.selectedSurface)
            >= OnGlass.TARGET_BODY_TEXT);

        // The rim carries working, asking and finished here, so it takes the graphics floor.
        assertTrue(OnGlass.ratio(strokeOf(ringing), palette.band) >= OnGlass.TARGET_LARGE_TEXT);
        assertTrue(OnGlass.ratio(strokeOf(selected), palette.band) >= OnGlass.TARGET_LARGE_TEXT);
    }

    private static int strokeOf(@NonNull TextView chip) {
        return org.robolectric.Shadows.shadowOf(
            (GradientDrawable) chip.getBackground()).getStrokeColor();
    }

    /**
     * A band has one veil and therefore one question. The column reads the strip's settled answer
     * instead of asking one of its own, so the strip wears one veil whoever draws first — where
     * before, the activity's status ink and this column each resolved it in their own hues and the
     * bar wore whichever of them ran last.
     */
    @Test
    public void theColumnReadsTheBandRatherThanResolvingASecondOne() {
        OnGlass.Resolution owned = ownStatusBand();
        StatusBarWindowColumn column = new StatusBarWindowColumn(context, null);
        column.setChromeInk(chrome.ink());
        column.setWindows(Arrays.asList(new WindowItemFixture().item("herdr")), 0);

        WindowChipInk.Palette palette = column.glassPalette();
        assertNotNull(palette);
        assertEquals("the chips stand on the band the strip actually wears",
            owned.surface, palette.band);
        assertEquals("and nothing the column did changed what that is",
            owned.veil, chrome.ink().resolution(GlassBackdropCache.Band.STATUS_BAR).veil);
    }

    /** Nothing has owned the strip yet: the column waits for the next pass rather than opening one. */
    @Test
    public void anUnownedBandLeavesTheColumnUndressed() {
        StatusBarWindowColumn column = new StatusBarWindowColumn(context, null);
        column.setChromeInk(chrome.ink());
        column.setWindows(Arrays.asList(new WindowItemFixture().item("herdr")), 0);

        assertNull("no owner, no answer to read, and no second question asked",
            column.glassPalette());
        assertNull(chrome.ink().resolution(GlassBackdropCache.Band.STATUS_BAR));
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Stands in for the activity's own status-ink pass, which is the status strip's owner: it
     * resolves the band once, at its strictest tier, in the stats' own hue.
     */
    @NonNull
    private OnGlass.Resolution ownStatusBand() {
        Rect rect = new Rect();
        assertTrue(chrome.ink().bandRect(GlassBackdropCache.Band.STATUS_BAR, rect));
        return chrome.ink().onGlass(GlassBackdropCache.Band.STATUS_BAR, rect,
            0xFF345CA8, 0xFF345CA8, OnGlass.TARGET_BODY_TEXT);
    }

    @NonNull
    private TerminalWindowBar dressedBar() {
        TerminalWindowBar bar = new TerminalWindowBar(context, null);
        bar.setChromeInk(chrome.ink());
        bar.setWindows(Arrays.asList(new WindowItemFixture().item("herdr"),
            new WindowItemFixture().item("zbook")), 1);
        return bar;
    }

    /** A window item with only the fields these tests care about. */
    private static final class WindowItemFixture {
        private boolean attention;

        WindowItemFixture attention() {
            attention = true;
            return this;
        }

        TerminalWindowBar.WindowItem item(String label) {
            return new TerminalWindowBar.WindowItem(label, label, false, attention);
        }
    }

    /**
     * Enough of the Activity for {@link ChromeInk} to answer: a laid-out view for each band it
     * looks up, and no wallpaper frame at all — so every sample is unreadable and the cache falls
     * back to the nominal glass, which is what {@link #glassBaseColor()} returns. That makes the
     * band under the chips exactly the colour the user's wallpaper measured.
     */
    private static final class BandSurfaces implements ChromeRenderer.Surfaces {

        @NonNull private final Context context;
        @NonNull private final View statusBand;
        @NonNull private final View windowBand;

        BandSurfaces(@NonNull Context context) {
            this.context = context;
            statusBand = laidOut(context);
            windowBand = laidOut(context);
        }

        private static View laidOut(@NonNull Context context) {
            View view = new View(context);
            view.setLayoutParams(new FrameLayout.LayoutParams(400, 80));
            view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 400, 80);
            return view;
        }

        @NonNull @Override public Context context() { return context; }

        @Nullable
        @Override
        public View findChromeView(int viewId) {
            if (viewId == R.id.terminal_status_bar_background) return statusBand;
            if (viewId == R.id.terminal_window_bar_background) return windowBand;
            return null;
        }

        @Nullable @Override public TermuxAppSharedPreferences preferences() { return null; }

        @Override public float dpToPx(float dp) { return dp; }

        @Override public int glassBaseColor() { return GLASS_LIGHT; }

        @Override public int accentColor() { return 0xFF345CA8; }

        @Override public int outlineColor() { return 0xFFC1C9D6; }

        @Override public boolean roundedDockStyle() { return false; }

        @Override public float statusBarRimCornerRadiusPx() { return 0f; }

        @Override public boolean isActivityVisible() { return true; }

        @Override public boolean wallpaperPassthroughEnabled() { return false; }

        @Override public int effectiveDockBlurRadiusDp() { return 0; }

        @Override public int effectiveStatusBarBlurRadiusDp() { return 0; }

        @NonNull
        @Override
        public ChromeSpec buildChromeSpec() {
            return new ChromeSpec(true, false, 0, true, true, false, true, 1f, 0);
        }

        @Override public void applyChromeSpec(@NonNull ChromeSpec spec) {}

        @Override public void enforceAccessoryFxInvariants() {}

        @Override public void updateTerminalGlassFrost() {}

        @Override public boolean isBlurHealthy(@NonNull ChromeSpec spec) { return true; }

        @NonNull @Override public Rect wallpaperFrameRect() { return new Rect(); }

        @Override public boolean useManagedWallpaperSource() { return false; }

        @Override public int systemWallpaperId() { return 1; }

        @NonNull
        @Override
        public File managedWallpaperExactFile() {
            return new File(context.getCacheDir(), "no-such-wallpaper");
        }

        @Override public int orientation() { return 1; }

        @Nullable
        @Override
        public WallpaperBlurCache.FrameCapture beginCapture(@NonNull Rect frameRect,
                                                            @NonNull View wallpaperFrame) {
            return null;
        }

        @Nullable
        @Override
        public Bitmap preBlur(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
            return null;
        }

        @Override public boolean isFrameInUse(@Nullable Bitmap frame) { return false; }

        @Override public void onCacheCleared() {}
    }
}
