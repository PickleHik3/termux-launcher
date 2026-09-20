package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.launcher.az.AzBarHostGeometry;
import com.termux.app.launcher.az.AzLetterTrack;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.DockPagingModel;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What a bar standing on a side is given, measured against the real {@code activity_termux.xml}:
 * a band that starts and ends level with the terminal's own frame, a rail that pages down that
 * band instead of running off the end of it, and a page indicator that travels with the row onto
 * every edge it can be put on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityRailPagingTest {

    /** The phone of record's canvas band, and a frame inset of the order the terminal draws. */
    private static final int BAND_WIDTH = 1080;
    private static final int COLUMN_HEIGHT = 1362;
    private static final int FRAME_INSET = 26;

    private TermuxActivity inflate() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    /** Every element on the bottom edge but the one named, which is put on {@code edge}. */
    private static PlaceLayout layoutWith(Element moved, Edge edge) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            slots.put(element, Slot.on(element == moved ? edge : Edge.BOTTOM, element));
        }
        slots.put(Element.STATUS, Slot.on(moved == Element.STATUS ? edge : Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    private static LinearLayout laidOutColumn(TermuxActivity activity, int width, int height) {
        LinearLayout column = activity.findViewById(R.id.terminal_content_column);
        column.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        column.layout(0, 0, width, height);
        return column;
    }

    private static void stand(View host, int width, int height) {
        host.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = host.getLayoutParams();
        params.width = width;
        params.height = height;
        host.setLayoutParams(params);
    }

    private float density(TermuxActivity activity) {
        return activity.getResources().getDisplayMetrics().density;
    }

    // ------------------------------------------------- the indicator rides with the row

    @Test
    public void theIndicatorIsABandOfTheRowsOwnHost() {
        TermuxActivity activity = inflate();
        View host = activity.findViewById(R.id.place_apps_bar_host);
        View scroll = activity.findViewById(R.id.place_apps_bar_scroll);
        PageTickStripView indicator = activity.findViewById(R.id.place_apps_bar_indicator);
        assertNotNull(host);
        assertSame("the row is inside the host", host, scroll.getParent());
        assertSame("and so are its ticks", host, indicator.getParent());
    }

    @Test
    public void aTopRowArrivesOnThePlankWithItsIndicator() {
        // The defect: a top apps row paged by swipe and showed nothing, because the plank held the
        // scrolling host alone and the indicator band was left behind on the dock.
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.TOP));

        View host = activity.findViewById(R.id.place_apps_bar_host);
        View scroll = activity.findViewById(R.id.place_apps_bar_scroll);
        PageTickStripView indicator = activity.findViewById(R.id.place_apps_bar_indicator);
        assertSame("the host is what stands on the plank",
            activity.findViewById(R.id.place_off_dock_plank_bars), host.getParent());
        assertSame(host, indicator.getParent());
        LinearLayout row = (LinearLayout) host;
        row.setOrientation(LinearLayout.VERTICAL);
        assertEquals("the row first", 0, row.indexOfChild(scroll));
        assertEquals("the ticks under it, the way they sit under the dock's own row",
            1, row.indexOfChild(indicator));
    }

    @Test
    public void aRailArrivesInTheSideStackWithItsIndicator() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.LEFT));
        View host = activity.findViewById(R.id.place_apps_bar_host);
        assertSame(activity.findViewById(R.id.place_edge_stack_left), host.getParent());
        assertSame(host, activity.findViewById(R.id.place_apps_bar_indicator).getParent());
    }

    @Test
    public void theIndicatorStandsUpWithTheRow() {
        TermuxActivity activity = inflate();
        PageTickStripView indicator = activity.findViewById(R.id.place_apps_bar_indicator);
        indicator.setVerticalForm(true);
        assertTrue(indicator.isVerticalForm());
        assertTrue("more than one page is what there is to draw",
            indicator.setPages(3, 1f));
        assertEquals(3, indicator.getPageCount());
        indicator.setVerticalForm(false);
        assertEquals(false, indicator.isVerticalForm());
    }

    // ------------------------------------------- the side stacks meet the terminal frame

    @Test
    public void aSideStackStartsAndEndsLevelWithTheTerminalFrame() {
        // The defect: the stacks ran the raw height of the canvas band while the terminal's frame
        // was drawn ~20 px inside it at each end, so every bar on a side over-ran the frame beside
        // it and the paddings read as a mistake.
        TermuxActivity activity = inflate();
        activity.applySideStackFrameInset(FRAME_INSET);
        for (int id : new int[] {R.id.place_edge_stack_left, R.id.place_edge_stack_right}) {
            View stack = activity.findViewById(id);
            assertEquals(FRAME_INSET, stack.getPaddingTop());
            assertEquals(FRAME_INSET, stack.getPaddingBottom());
        }
        // And it is one answer, applied once: a frame that stops drawing takes it back.
        activity.applySideStackFrameInset(0);
        assertEquals(0, activity.findViewById(R.id.place_edge_stack_left).getPaddingTop());
    }

    @Test
    public void aRailIsTheFrameAlignedColumnAndNothingLonger() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.LEFT));
        activity.applySideStackFrameInset(FRAME_INSET);
        View host = activity.findViewById(R.id.place_apps_bar_host);
        stand(host, 140, ViewGroup.LayoutParams.MATCH_PARENT);

        laidOutColumn(activity, BAND_WIDTH, COLUMN_HEIGHT);
        View band = activity.findViewById(R.id.terminal_canvas_band);
        assertEquals("the band's own length, the frame's inset off each end",
            band.getHeight() - (2 * FRAME_INSET), host.getHeight());
        assertTrue("and there is a column left to page", host.getHeight() > 0);
    }

    // ------------------------------------------------------------- the rail's paging

    /** At the phone of record's density, where 1362 px is a phone's 1362 px and the slot is 58dp. */
    @Test
    @Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "xxhdpi")
    public void aRailPagesTheFrameAlignedColumnAndKeepsEverySlotInsideIt() {
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.APPS, Edge.LEFT));
        activity.applySideStackFrameInset(FRAME_INSET);
        View host = activity.findViewById(R.id.place_apps_bar_host);
        stand(host, 140, ViewGroup.LayoutParams.MATCH_PARENT);
        laidOutColumn(activity, BAND_WIDTH, COLUMN_HEIGHT);
        int columnPx = host.getHeight();

        SuggestionBarView rail = new SuggestionBarView(activity, null);
        rail.setVerticalForm(true);
        rail.setMaxButtonCount(4);
        ReflectionHelpers.setField(rail, "pinnedItems", pinned(14));
        rail.measure(View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(columnPx, View.MeasureSpec.EXACTLY));
        rail.layout(0, 0, 140, columnPx);

        int slotPx = DockLayoutPolicy.railSlotLengthPx(density(activity));
        int perPage = DockPagingModel.railItemsPerPage(columnPx, slotPx);
        assertEquals(perPage,
            (int) ReflectionHelpers.callInstanceMethod(rail, "computePinnedItemsPerPage"));
        int pages = ReflectionHelpers.callInstanceMethod(rail, "getPinnedPagesCount");
        assertEquals("ceil(14 / itemsPerPage)", (14 + perPage - 1) / perPage, pages);
        assertTrue("fourteen icons do not fit one phone-high column: " + pages, pages > 1);
        assertTrue(rail.hasPinnedOverflowPages());
        // Every slot of a page fits the column it is drawn in — which is what the last icon
        // running under the bottom of the canvas band was the absence of.
        assertTrue("a page fits: " + perPage + " x " + slotPx + " in " + columnPx,
            perPage * slotPx <= columnPx);
        assertTrue("and the next one does not", (perPage + 1) * slotPx > columnPx);
    }

    private static List<PinnedItem> pinned(int count) {
        List<PinnedItem> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        return out;
    }

    // ------------------------------------------------------ the alphabets column's length

    @Test
    public void aSideIndexSpansTheFrameAlignedColumnAndSpreadsItsLetters() {
        // The defect: the column compensated for a top status bar and for the dock, both of which
        // are outside the canvas band since the side stacks moved into it — so the capsule was a
        // third of its column and the letters bunched into the top of it.
        TermuxActivity activity = inflate();
        activity.applyEdgeStacks(layoutWith(Element.AZ, Edge.RIGHT));
        FrameLayout host = activity.findViewById(R.id.place_az_bar_host);
        AzScrubRowView letters = activity.installAzBarRow(host, null);
        activity.layoutAzBarHost(host, Edge.RIGHT, false);
        host.setVisibility(View.VISIBLE);
        activity.applySideStackFrameInset(FRAME_INSET);

        laidOutColumn(activity, BAND_WIDTH, COLUMN_HEIGHT);
        View band = activity.findViewById(R.id.terminal_canvas_band);
        View glass = activity.findViewById(R.id.place_az_bar_host_glass);
        int expected = band.getHeight() - (2 * FRAME_INSET);
        assertTrue("there is a column to spread along", expected > 0);
        assertEquals("the host is the frame-aligned column", expected, host.getHeight());
        assertEquals("the capsule is the column, not the text",
            expected, AzBarHostGeometry.columnLengthPx(glass.getHeight()));
        assertEquals("and the letters fill it", expected, letters.getHeight());

        // The pitch a scrub maps onto: the column divided by the letters on it, exactly as the
        // lying-down row divides its width.
        int count = 27;
        float pitch = AzLetterTrack.slotSizePx(expected, count);
        assertEquals(expected / (float) count, pitch, 0.001f);
        assertTrue("the first letter is inside the column",
            AzLetterTrack.centerPx(0, expected, count) > 0f);
        assertTrue("and so is the last",
            AzLetterTrack.centerPx(count - 1, expected, count) < expected);
        assertEquals("a finger at the far end holds the last letter", count - 1,
            AzLetterTrack.indexAt(expected - 1f, expected, count));
    }
}
