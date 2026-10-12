package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric 4.13 runs LEGACY graphics, so Canvas and Path are shadow no-ops: no test here can
 * prove the palette's corners are actually clipped — that is on-device work. What these do cover is
 * that every drawing mode still completes, which is what guards the cached frame path against
 * rewind and null bugs, plus the pure height arithmetic. The keycap cases opt in to native
 * graphics, since their widths come from real text metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CommandPaletteViewTest {

    private static final RectF FRAME = new RectF(20f, 40f, 400f, 460f);

    @Test
    public void draw_completesInListModeWithAndWithoutOverflow() {
        CommandPaletteView view = palette();

        view.setRows(shortList(), 1);
        view.draw(new Canvas());

        // Overflowing is the case that draws the bottom fade, the fill that used to overhang both
        // bottom corner arcs as a near-opaque square-ended band.
        view.setRows(overflowingList(), 40);
        view.draw(new Canvas());
    }

    @Test
    public void draw_completesInArgumentMode() {
        CommandPaletteView view = palette();
        view.setRows(Collections.singletonList(
            CommandPaletteView.Row.notice("type a name")), -1);
        view.setArgumentMode(true, "name", "wo");

        view.draw(new Canvas());
    }

    @Test
    public void draw_survivesAFrameThatMovesBetweenPasses() {
        // The frame path is cached, so a moved or resized frame that failed to mark it dirty would
        // keep clipping to the old rectangle.
        CommandPaletteView view = palette();
        view.setRows(overflowingList(), 3);
        view.draw(new Canvas());

        view.setFrame(new RectF(0f, 0f, 200f, 120f), 8f, 1f, 1f, 0f, 1f);
        view.draw(new Canvas());

        view.setFrame(new RectF(), 0f, 1f, 1f, 0f, 1f);
        view.draw(new Canvas());
    }

    @Test
    public void contentHeight_isChromePlusRowsPlusTheArgumentRow() {
        CommandPaletteView view = palette();
        view.setRows(Collections.emptyList(), -1);

        assertEquals(view.chromeHeight(), view.measuredContentHeight(), .001f);

        view.setRows(shortList(), 1);
        float withRows = view.measuredContentHeight();
        assertTrue(withRows > view.chromeHeight());

        view.setArgumentMode(true, "name", "");
        assertTrue(view.measuredContentHeight() > withRows);
    }

    /**
     * The blink runs only while the palette is open. Legacy graphics cannot show whether a bar was
     * painted, but this is the one thing in the view that would otherwise tick on forever, so what
     * matters is that it is started and stopped at all — and that both halves still draw.
     */
    @Test
    public void caretBlink_runsWhileOpenAndStopsWhenThePaletteCloses() {
        CommandPaletteView view = palette();
        view.setRows(shortList(), 1);

        view.setCaretBlinking(true);
        assertTrue(view.isCaretBlinking());
        view.draw(new Canvas());

        view.setCaretBlinking(false);
        assertFalse("a closed palette leaves nothing posted", view.isCaretBlinking());
        view.draw(new Canvas());

        // Typing restarts the phase rather than letting an off half swallow the caret; it must not
        // start one on a palette that is closed.
        view.setQuery("wor", "search");
        view.setQueryCursor(3);
        assertFalse(view.isCaretBlinking());
        view.draw(new Canvas());
    }

    /** With animations turned off the caret is shown steady instead of blinking. */
    @Test
    public void caretBlink_isSteadyWhenAnimationsAreOff() {
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext().getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        try {
            CommandPaletteView view = palette();
            view.setRows(shortList(), 1);

            view.setCaretBlinking(true);
            assertFalse("no motion nobody asked for", view.isCaretBlinking());
            view.draw(new Canvas());
        } finally {
            Settings.Global.putFloat(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        }
    }

    /**
     * An app or contact pill differs from an action key only in what leads it: an inset, the
     * 14 dp mark and its gap, where a key has its padding, glyph and gap. Same label, same
     * everything else, so the widths differ by exactly that.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    public void keycaps_aPillIsWiderThanAKeyByItsMarkAgainstTheGlyph() {
        CommandPaletteView view = stripOnly();
        float density = view.getResources().getDisplayMetrics().density;
        CommandPaletteView.Keycap key = new CommandPaletteView.Keycap("▸", "mail");

        view.setKeycaps(Collections.singletonList(key));
        RectF keyRect = view.keycapRect(0);
        view.setKeycaps(Collections.singletonList(CommandPaletteView.Keycap.app("mail", null)));
        RectF appRect = view.keycapRect(0);
        view.setKeycaps(Collections.singletonList(
            CommandPaletteView.Keycap.contact("mail", "Mail Room", null)));
        RectF contactRect = view.keycapRect(0);

        float mark = CommandPaletteView.MARK_SIZE * density;
        float markLead = (appRect.height() - mark) / 2f + mark
            + CommandPaletteView.MARK_GAP * density;
        float glyphLead = CommandPaletteView.CAP_PAD_H * density + key.mGlyphWidth
            + CommandPaletteView.CAP_LEAD_GAP * density;
        assertTrue(appRect.width() > keyRect.width());
        assertEquals(markLead - glyphLead, appRect.width() - keyRect.width(), 0.5f);
        assertEquals(appRect.width(), contactRect.width(), 0.01f);
        assertEquals("one height for every kind", keyRect.height(), appRect.height(), 0.01f);
    }

    /** A full strip of mixed kinds fits a 360 dp phone's palette, one cap after another. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    public void keycaps_fourMixedCapsFitA360dpPalette_withoutOverlapping() {
        CommandPaletteView view = stripOnly();
        List<CommandPaletteView.Keycap> caps = Arrays.asList(
            CommandPaletteView.Keycap.app("Shizuku", new ColorDrawable(Color.BLUE)),
            CommandPaletteView.Keycap.contact("Sam", "Sam Rivera", null),
            CommandPaletteView.Keycap.app("Solid Explorer", null),
            new CommandPaletteView.Keycap("⌨", "docked"));
        view.setKeycaps(caps);

        RectF frame = openFrame(view);
        RectF previous = null;
        for (int i = 0; i < caps.size(); i++) {
            RectF cap = view.keycapRect(i);
            assertTrue("cap " + i + " inside the palette",
                cap.left >= frame.left - 0.5f && cap.right <= frame.right + 0.5f);
            if (previous != null)
                assertTrue("cap " + i + " clears the one before", cap.left >= previous.right);
            float room = view.keycapLabelRoom(i);
            assertTrue("cap " + i + " keeps room for its label", room > 0f);
            assertTrue("a label is never given more than it needs",
                room <= caps.get(i).mLabelWidth + 0.01f);
            previous = cap;
        }
        // Draws with real text, the icon and the monogram, allocating its labels once.
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
            Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        bitmap.recycle();
    }

    /** A palette open on a 360 dp screen, inset as the controller insets it, with no rows. */
    private static CommandPaletteView stripOnly() {
        CommandPaletteView view = new CommandPaletteView(
            ApplicationProvider.getApplicationContext());
        view.setQuery("", "search");
        int width = view.getResources().getDisplayMetrics().widthPixels;
        int height = view.getResources().getDisplayMetrics().heightPixels;
        view.setModalBounds(new RectF(0f, 0f, width, height));
        view.setFrame(openFrame(view), 26f, 1f, 1f, 0f, 1f);
        view.layout(0, 0, width, height);
        return view;
    }

    /** The open frame on this screen: the full width less the controller's 14 dp sides. */
    private static RectF openFrame(CommandPaletteView view) {
        float density = view.getResources().getDisplayMetrics().density;
        float side = 14f * density;
        return new RectF(side, 40f * density,
            view.getResources().getDisplayMetrics().widthPixels - side, 240f * density);
    }

    private static CommandPaletteView palette() {
        CommandPaletteView view = new CommandPaletteView(
            ApplicationProvider.getApplicationContext());
        view.setKeycaps(Collections.singletonList(
            new CommandPaletteView.Keycap("⏎", "run")));
        view.setHeader("3 results", "apps");
        view.setQuery("wo", "search");
        view.setQueryCursor(2);
        view.setModalBounds(new RectF(0f, 0f, 420f, 920f));
        // Radius large enough that the corner arcs are the whole point of the clip.
        view.setFrame(FRAME, 26f, 1f, 1f, 0f, 1f);
        view.layout(0, 0, 420, 920);
        return view;
    }

    private static List<CommandPaletteView.Row> shortList() {
        return Arrays.asList(
            CommandPaletteView.Row.category("apps"),
            CommandPaletteView.Row.entry("work", "hold to bind a key", "C-A-w", true),
            CommandPaletteView.Row.entry("mail", null, "", false));
    }

    /** Enough rows that the list overflows its viewport and draws the bottom fade. */
    private static List<CommandPaletteView.Row> overflowingList() {
        List<CommandPaletteView.Row> rows = new ArrayList<>();
        for (int i = 0; i < 60; i++)
            rows.add(CommandPaletteView.Row.entry("row " + i, "description " + i, "", true));
        return rows;
    }
}
