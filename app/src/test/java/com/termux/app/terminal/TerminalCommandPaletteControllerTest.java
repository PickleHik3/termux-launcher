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
     * From the bar the glass is placed again, once the pass is over, whenever the bar it stands
     * above has moved since it was placed.
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
        bar.offset(0, 200);
        palette.onBarMoved();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals("the glass stands above the bar where it is now", high + 200f,
            anchorY(palette), 0.5f);
        // Nothing moved since: placed once.
        float placed = anchorY(palette);
        palette.onBarMoved();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(placed, anchorY(palette), 0f);
    }

    /**
     * Review of round 6: a keyboard raised under the open palette from the bar shrinks its room.
     * The anchor moved at once and the height caught up on its spring, a frame with the search
     * field above the screen; a room left shorter than the least height clipped the glass. On
     * every frame drawn after the raise the glass stays inside its room, and it settles at the
     * room's height when the room is short.
     */
    @Test
    public void aKeyboardThatShrinksTheRoomKeepsEveryFrameOfTheGlassOnScreen() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        int[] at = new int[2];
        host.viewRoot.getLocationOnScreen(at);
        // The bar at the bottom of the room, the keyboard down.
        android.graphics.Rect bar = new android.graphics.Rect(8, at[1] + 400, 392, at[1] + 460);
        palette.setTerlHost(new CommandPaletteTerlHost() {
            @Override public boolean barOnScreen(@androidx.annotation.NonNull android.graphics.Rect out) {
                out.set(bar);
                return true;
            }
        });
        palette.openFromBar();
        long t = 1_000_000_000L;
        for (int i = 0; i < 120; i++) palette.doFrame(t += 16_000_000L);
        android.graphics.RectF frame = ReflectionHelpers.getField(palette, "mFrame");
        float tall = frame.height();

        // The keyboard rises: the bar rides it far up; the room left is shorter than the glass
        // and than its old least height.
        bar.offsetTo(8, at[1] + 150);
        palette.onBarMoved();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        float roomTop = ReflectionHelpers.<Float>getField(palette, "mBarRoomTop");
        float anchor = anchorY(palette);
        assertTrue("the room is shorter than the glass was", anchor - roomTop < tall);
        assertTrue("and than the old least height", anchor - roomTop
            < 120f * host.context().getResources().getDisplayMetrics().density);
        for (int i = 0; i < 120; i++) {
            palette.doFrame(t += 16_000_000L);
            frame = ReflectionHelpers.getField(palette, "mFrame");
            assertTrue("frame " + i + " top " + frame.top + " above the room " + roomTop,
                frame.top >= roomTop - 0.5f);
            assertTrue("frame " + i + " on screen", frame.top >= 0f);
        }
        assertEquals("settled at the room's height", anchor - roomTop, frame.height(), 1f);
    }

    /**
     * Round 6 (pong, build 25dc7048d): opened from a Workspace, the palette stood above the
     * status bar, off the top of the screen. Terl stacks its places vertically: with a Workspace
     * in front, Home and v1's terminal host above it stand moved up by Home's height, and the
     * bar's palette took its room from that host. It is placed in the window's own coordinates,
     * on the bar as drawn, whatever the pages' vertical offsets.
     */
    @Test
    public void fromAWorkspaceWithThePlacesStackedTheGlassStaysOnScreen() {
        FakeCommandPaletteHost host = hostWithViews();
        // v1's terminal host, carried up with Home while a Workspace is in front.
        android.widget.FrameLayout terminal = new android.widget.FrameLayout(host.context());
        terminal.setId(R.id.terminal_surface_host);
        ((android.widget.FrameLayout) host.viewRoot).addView(terminal,
            new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        terminal.setTranslationY(-2000f);
        TerminalCommandPaletteController palette =
            new TerminalCommandPaletteController(host, () -> "");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        int[] at = new int[2];
        host.viewRoot.getLocationOnScreen(at);
        android.graphics.Rect bar = new android.graphics.Rect(8, at[1] + 400, 392, at[1] + 460);
        palette.setTerlHost(new CommandPaletteTerlHost() {
            @Override public boolean barOnScreen(@androidx.annotation.NonNull android.graphics.Rect out) {
                out.set(bar);
                return true;
            }
        });

        palette.openFromBar();
        long t = 1_000_000_000L;
        android.graphics.RectF frame = null;
        for (int i = 0; i < 120; i++) {
            palette.doFrame(t += 16_000_000L);
            frame = ReflectionHelpers.getField(palette, "mFrame");
            assertTrue("frame " + i + " top " + frame.top + " off the screen", frame.top >= 0f);
        }
        assertTrue("the room starts in the window", ReflectionHelpers.<Float>getField(palette,
            "mBarRoomTop") >= 0f);
        assertTrue("it stands above the bar", frame.bottom <= bar.top - at[1]);
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
