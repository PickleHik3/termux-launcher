package com.termux.app.terminal;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric 4.13 runs LEGACY graphics, so Canvas and Path are shadow no-ops: no test here can
 * prove the palette's corners are actually clipped — that is on-device work. What these do cover is
 * that every drawing mode still completes, which is what guards the cached frame path against
 * rewind and null bugs, plus the pure height arithmetic.
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
