package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;

/**
 * The command palette driven through its {@link TerminalCommandPaletteController.Host} alone: no
 * activity of ours behind it, only the three views it binds and a fake that counts what it was
 * asked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalCommandPaletteControllerTest {

    @Test
    public void constructingAsksTheHostForNothingButItsContext() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host);

        assertFalse(palette.isOpen());
        assertEquals(Collections.emptyList(), host.interceptorCalls);
        assertEquals(0, host.cornersReads);
        assertEquals(0, host.floatingStyleReads);
        assertEquals(0, host.drawerCloses);
        assertEquals(0, host.sheetDismissals);
    }

    @Test
    public void openAndCollapseSwitchTheInterceptorOnceEach() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");

        palette.show();

        assertTrue(palette.isOpen());
        assertEquals(Collections.singletonList(true), host.interceptorCalls);
        assertEquals("the drawer yields to the palette", 1, host.drawerCloses);
        assertEquals("the sheets yield to the palette", 1, host.sheetDismissals);

        palette.collapse();

        assertFalse(palette.isOpen());
        assertEquals(Arrays.asList(true, false), host.interceptorCalls);
    }

    @Test
    public void backCollapsesAnOpenPalette() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");
        palette.show();

        assertTrue(palette.handleHardwareKey(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));

        assertFalse(palette.isOpen());
        assertEquals(Arrays.asList(true, false), host.interceptorCalls);
    }

    @Test
    public void theOpenRadiusFollowsTheHostsCornersAndStyle() {
        FakeCommandPaletteHost host = hostWithViews();
        host.cornersDp = 24;
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");
        float density = host.context().getResources().getDisplayMetrics().density;

        palette.show();

        assertEquals(1, host.cornersReads);
        assertEquals(1, host.floatingStyleReads);
        assertEquals(24f * density, openRadiusPx(palette), 0.001f);

        palette.collapse();
        host.cornersDp = -1;
        host.floatingStyle = true;
        palette.show();

        assertEquals(2, host.cornersReads);
        assertEquals(2, host.floatingStyleReads);
        assertEquals(CommandPaletteShape.openRadiusDp(-1, true) * density,
            openRadiusPx(palette), 0.001f);
    }

    /**
     * Round 6 (emulator, 27): on the first open after an install the bar's search opened the
     * palette while the keyboard it raised was still being laid out, and the glass was placed on
     * a reading taken mid-way, its whole height off the top of the screen; nothing placed it again.
     * From the bar the glass is placed again before a frame whenever the bar it stands above has
     * moved since it was placed.
     */
    @Test
    public void fromTheBarTheGlassFollowsTheBarThatMovedAfterItOpened() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");
        android.view.View root = host.viewRoot;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(400,
            android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(
            900, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 900);
        int[] at = new int[2];
        root.getLocationOnScreen(at);
        // The bar read while the keyboard was laid out: high up the screen.
        android.graphics.Rect bar = new android.graphics.Rect(8, at[1] + 120, 392, at[1] + 220);
        palette.setTerlHost(new CommandPaletteTerlHost() {
            @Override public boolean barOnScreen(@androidx.annotation.NonNull android.graphics.Rect out) {
                out.set(bar);
                return true;
            }
        });

        palette.openFromBar();
        assertTrue(palette.isOpen());
        float high = anchorY(palette);

        // The keyboard settles; the bar stands where it rides it, lower down.
        bar.offset(0, 500);
        root.getViewTreeObserver().dispatchOnPreDraw();

        assertEquals("the glass stands above the bar where it is now", high + 500f,
            anchorY(palette), 0.5f);
        // Nothing moved since: placed once.
        float placed = anchorY(palette);
        root.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(placed, anchorY(palette), 0f);
    }

    private static float anchorY(TerminalCommandPaletteController palette) {
        return ReflectionHelpers.<Float>getField(palette, "mAnchorY");
    }

    private static float openRadiusPx(TerminalCommandPaletteController palette) {
        return ReflectionHelpers.<Float>getField(palette, "mOpenRadiusPx");
    }

    /** A host over the three views the palette binds, in a plain activity's window. */
    private static FakeCommandPaletteHost hostWithViews() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout paletteHost = new FrameLayout(activity);
        paletteHost.setId(R.id.command_palette_host);
        FrameLayout glass = new FrameLayout(activity);
        glass.setId(R.id.command_palette_glass);
        ImageView blur = new ImageView(activity);
        blur.setId(R.id.command_palette_blur);
        paletteHost.addView(glass);
        paletteHost.addView(blur);
        root.addView(paletteHost);
        activity.setContentView(root);
        FakeCommandPaletteHost host = new FakeCommandPaletteHost(activity);
        host.viewRoot = root;
        return host;
    }
}
