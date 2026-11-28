package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * The command palette as the Terl control bar's search opens it (Terl decisions 2026-11-28),
 * driven through its hosts alone: it opens on its list with All apps first, pairs with the keyboard
 * it raised, wears the glass the Terl host gives it, keeps one height while typing, and its rows
 * run through the Terl host.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class CommandPaletteControlBarTest {

    @Test
    public void theBarsSearchOpensOnItsListWithAllAppsFirst() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host, () -> "");
        RecordingTerlHost terl = new RecordingTerlHost();
        palette.setTerlHost(terl);

        palette.toggleFromBar();

        assertTrue(palette.isOpen());
        List<CommandPaletteFilter.Entry> entries = rowEntries(palette);
        CommandPaletteFilter.Entry first = firstEntry(entries);
        assertEquals(CommandPaletteTerlSet.TOOL_ALL_APPS, first.toolName);
        assertEquals("Open the drawer", first.shortcutLabel());
        assertEquals("the focus starts on All apps", entries.indexOf(first), (int) focus(palette));

        // Enter on it opens the drawer through the Terl host, and the palette goes.
        assertTrue(palette.handleHardwareKey(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertFalse(palette.isOpen());
        assertEquals(1, terl.drawers);
    }

    @Test
    public void aSecondTapPutsItAwayAndTheKeyboardItRaisedGoesWithIt() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host, () -> "");
        RecordingTerlHost terl = new RecordingTerlHost();
        terl.raises = true;
        palette.setTerlHost(terl);

        palette.toggleFromBar();
        assertFalse("it waits a frame for the keyboard it raised", palette.isOpen());
        terl.raises = false;
        palette.toggleFromBar();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(palette.isOpen());
        assertEquals(0, terl.lowers);

        palette.toggleFromBar();

        assertFalse(palette.isOpen());
        assertEquals(1, terl.lowers);
        palette.dismissImmediately();
        assertEquals("lowered once", 1, terl.lowers);
    }

    @Test
    public void aKeyboardAlreadyUpStaysUp() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host, () -> "");
        RecordingTerlHost terl = new RecordingTerlHost();
        palette.setTerlHost(terl);

        palette.toggleFromBar();
        palette.collapse();

        assertEquals(0, terl.lowers);
    }

    @Test
    public void itWearsTheTerlHostsGlassAndV1sPaletteKeepsTheKeyboards() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host, () -> "");
        RecordingTerlHost terl = new RecordingTerlHost();
        terl.material = new CommandPaletteTerlHost.Material(0xFF102030, 200, 12);
        palette.setTerlHost(terl);

        palette.toggleFromBar();
        CommandPaletteView view = ReflectionHelpers.getField(palette, "mView");
        assertEquals(200, (int) ReflectionHelpers.<Integer>getField(view, "mGlassAlpha"));
        assertEquals(0xFF102030, (int) ReflectionHelpers.<Integer>getField(view, "mGlassBase"));
        palette.dismissImmediately();

        palette.show();
        assertEquals(CommandPaletteView.DEFAULT_GLASS_ALPHA,
            (int) ReflectionHelpers.<Integer>getField(view, "mGlassAlpha"));
    }

    @Test
    public void itStandsAboveTheBarAndKeepsOneHeightWhileTyping() {
        FakeCommandPaletteHost host = hostWithViews();
        TerminalCommandPaletteController palette = new TerminalCommandPaletteController(host, () -> "");
        RecordingTerlHost terl = new RecordingTerlHost();
        FrameLayout paletteHost = host.viewRoot.findViewById(R.id.command_palette_host);
        int[] at = new int[2];
        paletteHost.getLocationOnScreen(at);
        int barTop = at[1] + paletteHost.getHeight() - 300;
        terl.bar = new Rect(at[0], barTop, at[0] + paletteHost.getWidth(), barTop + 200);
        palette.setTerlHost(terl);

        palette.toggleFromBar();

        float anchor = ReflectionHelpers.<Float>getField(palette, "mAnchorY");
        assertTrue("the glass ends above the bar", anchor < paletteHost.getHeight() - 300);
        float height = target(palette);
        for (char c : "zzzz".toCharArray()) palette.handleSoftKeyboardCodePoint(c, false);
        assertEquals("nothing matches, yet the glass keeps its height", height, target(palette), 0.01f);
    }

    private static float target(TerminalCommandPaletteController palette) {
        Object spring = ReflectionHelpers.getField(palette, "mHeight");
        return ReflectionHelpers.<Float>getField(spring, "target");
    }

    private static List<CommandPaletteFilter.Entry> rowEntries(TerminalCommandPaletteController palette) {
        return ReflectionHelpers.getField(palette, "mRowEntries");
    }

    private static Integer focus(TerminalCommandPaletteController palette) {
        return ReflectionHelpers.getField(palette, "mFocus");
    }

    @NonNull
    private static CommandPaletteFilter.Entry firstEntry(List<CommandPaletteFilter.Entry> entries) {
        for (CommandPaletteFilter.Entry entry : entries) if (entry != null) return entry;
        throw new AssertionError("no rows");
    }

    private static final class RecordingTerlHost implements CommandPaletteTerlHost {
        boolean raises;
        int lowers;
        int drawers;
        @Nullable Material material;
        @Nullable Rect bar;
        final List<String> opened = new ArrayList<>();

        @Override public boolean raiseKeyboard() { return raises; }

        @Override public void lowerKeyboard() { lowers++; }

        @Override public void openDrawer() { drawers++; }

        @Nullable @Override public Material material() { return material; }

        @Override public boolean barOnScreen(@NonNull Rect out) {
            if (bar == null) return false;
            out.set(bar);
            return true;
        }

        @Override public void open(@NonNull com.termux.app.launcher.model.LauncherAppEntry entry) {
            opened.add(entry.label);
        }
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
        root.addView(paletteHost, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        FakeCommandPaletteHost host = new FakeCommandPaletteHost(activity);
        host.viewRoot = root;
        return host;
    }
}
