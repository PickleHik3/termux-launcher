package com.termux.app;

import android.app.Application;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTickStrip;
import com.termux.app.launcher.paging.PageTickStripView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * One page indicator, owned by the pinned-apps row and travelling with it, measured against the
 * real {@code activity_termux.xml}.
 *
 * <p>The defect this pins down: the dock painted its own ticks from an FX layer over the glass and
 * the row carried a strip of its own off the dock, so a place with the row on a rail showed two
 * indicators at once — the old set still lit on the dock, the new one grey beside the rail.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityPageIndicatorTest {

    private TermuxActivity inflate() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    /** The one bar, standing where the activity's own setup would have put it to begin with. */
    private SuggestionBarView lendBar(TermuxActivity activity) {
        SuggestionBarView bar = new SuggestionBarView(activity, null);
        // A rail with nothing pinned claims no column at all, so the bar has to be holding
        // something for the walk to stand it anywhere but the dock.
        List<PinnedItem> pinned = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            pinned.add(new PinnedAppItem(new AppRef("com.example.app" + i, "Main")));
        }
        ReflectionHelpers.setField(bar, "pinnedItems", pinned);
        ViewGroup plank = activity.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(activity, "mSuggestionBarView", bar);
        return bar;
    }

    /** Every element on the bottom edge but the pinned apps, which are put on {@code edge}. */
    private static PlaceLayout layoutWithAppsOn(Edge edge) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            slots.put(element, Slot.on(element == Element.APPS ? edge : Edge.BOTTOM, element));
        }
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** The bar standing on {@code edge}, with the strip it was handed there. */
    private SuggestionBarView standOn(TermuxActivity activity, Edge edge) {
        SuggestionBarView bar = lendBar(activity);
        PlaceLayout layout = layoutWithAppsOn(edge);
        activity.applyEdgeStacks(layout);
        activity.syncPinnedAppsHost(layout);
        return bar;
    }

    /** The band of {@code host} that {@code view} sits in, or null when it is not in one at all. */
    private static View bandHolding(ViewGroup host, View view) {
        for (View walk = view; walk != null; ) {
            ViewGroup parent = walk.getParent() instanceof ViewGroup
                ? (ViewGroup) walk.getParent() : null;
            if (parent == host) return walk;
            walk = parent;
        }
        return null;
    }

    /** Every tick strip in the inflated screen, wherever it sits. */
    private static List<PageTickStripView> stripsIn(View root) {
        List<PageTickStripView> found = new ArrayList<>();
        if (root instanceof PageTickStripView) found.add((PageTickStripView) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                found.addAll(stripsIn(group.getChildAt(i)));
            }
        }
        return found;
    }

    // ----------------------------------------------------- there is only one of them left

    @Test
    public void theDockNoLongerPaintsPageTicksOfItsOwn() {
        for (Method method : LauncherAzGestureFxView.class.getDeclaredMethods()) {
            String name = method.getName();
            assertTrue("the FX layer still has a page-tick path: " + name,
                !name.contains("PageTicks") && !name.contains("PageIndicator")
                    && !name.contains("InteractionOverflow"));
        }
    }

    @Test
    public void everyAppsEdgeBindsExactlyOneStripAndItIsABandOfTheRowsOwnHost() {
        for (Edge edge : Edge.values()) {
            TermuxActivity activity = inflate();
            SuggestionBarView bar = standOn(activity, edge);

            PageTickStripView bound = bar.boundPageIndicator();
            assertNotNull("the row is fed a strip on " + edge, bound);
            // The host the bar was lent to is inside the host the strip is a band of, so the pair
            // travels together whichever edge the walk puts them on.
            assertNotNull("the strip is a band of the row's own host on " + edge,
                bandHolding((ViewGroup) bound.getParent(), bar));

            // Every other strip in the screen is holding nothing at all.
            for (PageTickStripView strip : stripsIn(activity.findViewById(R.id.terminal_root_container))) {
                if (strip == bound) continue;
                assertEquals("a strip that is not the row's must hold no pages on " + edge,
                    1, strip.getPageCount());
            }
        }
    }

    @Test
    public void aRowNextToTheCanvasKeepsItsTicksOnTheCanvasSideOnEveryEdge() {
        // Every one of these arrangements stands the row innermost on its edge, so the exception
        // is the rule here: the ticks take the side the terminal is on.
        for (Edge edge : Edge.values()) {
            TermuxActivity activity = inflate();
            SuggestionBarView bar = standOn(activity, edge);
            PageTickStripView bound = bar.boundPageIndicator();
            LinearLayout host = (LinearLayout) bound.getParent();

            int rowIndex = host.indexOfChild(bandHolding(host, bar));
            int stripIndex = host.indexOfChild(bound);
            assertTrue(edge + ": the row and its ticks are both bands of the host",
                rowIndex >= 0 && stripIndex >= 0);
            if (PageTickStrip.ticksLeadRow(edge, true)) {
                assertTrue(edge + ": the ticks stand on the terminal's side of the bar",
                    stripIndex < rowIndex);
            } else {
                assertTrue(edge + ": the ticks stand on the terminal's side of the bar",
                    stripIndex > rowIndex);
            }
            assertEquals(edge + ": the ticks run the way the row does",
                PageTickStrip.verticalOn(edge), bound.isVerticalForm());
        }
    }

    /**
     * The mirror of the bottom edge's outer-side case: a row lying along the top with the index
     * standing between it and the terminal. The ticks go to the row's outer side, which up there
     * is over the icons, rather than into the gap the index is already on the other side of.
     */
    @Test
    public void aTopRowWithABandBetweenItAndTheCanvasPutsItsTicksOverTheIcons() {
        TermuxActivity activity = inflate();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        SuggestionBarView bar = lendBar(activity);
        PlaceLayout layout = topRowUnderTheIndex();
        activity.applyEdgeStacks(layout);
        activity.syncPinnedAppsHost(layout);

        PageTickStripView strip = bar.boundPageIndicator();
        assertNotNull(strip);
        LinearLayout host = (LinearLayout) strip.getParent();
        View row = bandHolding(host, bar);
        assertNotNull(row);
        assertTrue("the ticks stand over the icons on the top edge",
            host.indexOfChild(strip) < host.indexOfChild(row));

        // Sharing its plank with the index, the row keeps the same air on each side of its icons,
        // and the ticks stand inside the one over them.
        DockLayout dock = activity.dockLayoutFor(layout);
        float density = activity.getResources().getDisplayMetrics().density;
        int airPx = DockLayoutPolicy.rowAirPx(false, true, density);
        assertEquals(dock.appsRowStripBandPx, strip.getLayoutParams().height);
        assertEquals("nothing is left of the air on the ticks' side", 0, row.getPaddingTop());
        assertEquals("and the whole of it under the icons", airPx, row.getPaddingBottom());
        assertEquals("the host is the band, with nothing reserved for the strip",
            dock.appsRowBandPx, row.getLayoutParams().height + strip.getLayoutParams().height);
    }

    /** The index innermost on the top edge, the row outside it, everything else on the bottom. */
    private static PlaceLayout topRowUnderTheIndex() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values())
            slots.put(element, Slot.on(Edge.BOTTOM, element));
        slots.put(Element.STATUS, new Slot(false, Edge.TOP, 0));
        slots.put(Element.APPS, new Slot(false, Edge.TOP, 1));
        slots.put(Element.AZ, new Slot(false, Edge.TOP, 2));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /**
     * A row lying along the top edge is the dock's own band turned over: the ticks stand on its
     * centre-facing side, which up there is under the icons, and the band they stand in is that
     * side's air rather than one added to it. So the icon is in the middle of the band on that
     * edge too, and the plank the row stands on is the band and nothing more.
     */
    @Test
    public void aRowLyingOnTheTopEdgeIsSymmetricAboutItsIconToo() {
        TermuxActivity activity = inflate();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        SuggestionBarView bar = standOn(activity, Edge.TOP);
        PageTickStripView strip = bar.boundPageIndicator();
        assertNotNull(strip);
        LinearLayout host = (LinearLayout) strip.getParent();
        View row = bandHolding(host, bar);
        assertNotNull(row);

        DockLayout dock = activity.dockLayoutFor(layoutWithAppsOn(Edge.TOP));
        float density = activity.getResources().getDisplayMetrics().density;
        int airPx = DockLayoutPolicy.rowAirPx(false, true, density);
        assertTrue("the row has to have a band for this to mean anything",
            dock.appsRowBandHintPx > 0);
        assertEquals("the band is the icons' box and its air on each side",
            dock.appsRowBandHintPx + (2 * airPx), dock.appsRowBandPx);

        // The ticks trail the row up here, so the air under the icons is their band and the row
        // keeps the whole of the air over them.
        assertTrue("the ticks stand under the icons on the top edge",
            host.indexOfChild(strip) > host.indexOfChild(row));
        assertEquals(dock.appsRowStripBandPx, strip.getLayoutParams().height);
        assertEquals(dock.appsRowViewBandPx(), row.getLayoutParams().height);
        assertEquals("air over the icons", airPx, row.getPaddingTop());
        assertEquals("and the same under them", airPx,
            row.getPaddingBottom() + strip.getLayoutParams().height);
        assertEquals("the host is the band, with nothing reserved for the strip",
            dock.appsRowBandPx, row.getLayoutParams().height + strip.getLayoutParams().height);
    }

    @Test
    public void thePageBeingShownIsTheAccentAndTheRestAreItMutedOnEveryEdge() {
        for (Edge edge : Edge.values()) {
            TermuxActivity activity = inflate();
            PageTickStripView strip = standOn(activity, edge).boundPageIndicator();
            assertNotNull(strip);
            strip.setAccentColor(Color.rgb(0x34, 0x5C, 0xA8));
            strip.setPages(3, 1f);

            int active = strip.tickColorAt(1);
            int resting = strip.tickColorAt(0);
            assertEquals(edge + ": the page being shown is the accent at full",
                255, Color.alpha(active));
            assertEquals(edge + ": which is the strip's own accent",
                strip.getAccentColor() & 0x00FFFFFF, active & 0x00FFFFFF);
            assertEquals(edge + ": the ticks at rest are the same colour",
                active & 0x00FFFFFF, resting & 0x00FFFFFF);
            assertEquals(edge + ": muted",
                Math.round(255f * PageTickStrip.INACTIVE_ALPHA), Color.alpha(resting));
        }
    }

    @Test
    public void theMostUsedPagesTickKeepsItsOwnWarmTint() {
        TermuxActivity activity = inflate();
        PageTickStripView strip = activity.findViewById(R.id.apps_bar_indicator_band);
        assertNotNull("the dock's own band is the strip now", strip);
        strip.setAccentColor(Color.rgb(0x34, 0x5C, 0xA8));
        strip.setPages(3, 2f, 2);
        assertEquals(PageTickStrip.DYNAMIC_TICK_COLOR & 0x00FFFFFF,
            strip.tickColorAt(2) & 0x00FFFFFF);
        assertEquals(strip.getAccentColor() & 0x00FFFFFF, strip.tickColorAt(0) & 0x00FFFFFF);
        // A dynamic page out of range is no dynamic page at all.
        strip.setPages(2, 0f, 7);
        assertEquals(-1, strip.getDynamicPageIndex());
    }
}
