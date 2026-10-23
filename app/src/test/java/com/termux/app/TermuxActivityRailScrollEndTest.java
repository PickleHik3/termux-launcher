package com.termux.app;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.launcher.drawer.DockRailScrollView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.PageTickStrip;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * #146's second emulator round, measured against the real {@code activity_termux.xml} and the
 * activity's own rail sync: a landscape rail of 7 pins with 3 most-used apps after them, in a band
 * that holds four and a bit slots. At the end of its scroll the last most-used app has to end
 * inside the viewport, and the two ticks beside the rail have to be there, and lit by the scroll.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityRailScrollEndTest {

    private static final int SCREEN_WIDTH = 2400;
    private static final int PINS = 7;
    private static final int MOST_USED = 3;
    /** A terminal frame inset of the order the phone draws, padding the side stacks. */
    private static final int FRAME_INSET = 26;

    /** The scene a test reads: the rail's scroll, its column and its ticks, laid out. */
    private static final class Scene {
        DockRailScrollView scroll;
        SuggestionBarView column;
        PageTickStripView ticks;
        int slot;
    }

    private static PlaceLayout railOnTheLeft() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            slots.put(element, Slot.on(element == Element.APPS ? Edge.LEFT : Edge.BOTTOM, element));
        }
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    private static void layOut(TermuxActivity activity, int height) {
        LinearLayout column = activity.findViewById(R.id.terminal_content_column);
        column.measure(View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        column.layout(0, 0, SCREEN_WIDTH, height);
    }

    /**
     * The bar lent to the dock first, as the activity sets it up, then stood on the left edge by
     * the activity's own sync, in a band of four slots and six tenths of one.
     */
    private Scene railInItsHost() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SuggestionBarView bar = new SuggestionBarView(activity, null);
        bar.setDockIconSizePx(Math.round(24f * bar.getResources().getDisplayMetrics().density));
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (int i = 0; i < PINS + MOST_USED; i++) {
            apps.add(new LauncherAppEntry(new AppRef("com.example.app" + i, "Main"),
                "App " + i, null));
        }
        Map<String, LauncherAppEntry> cache = ReflectionHelpers.getField(bar, "resolvedRefCache");
        List<PinnedItem> pins = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            cache.put(apps.get(i).appRef.stableId(), apps.get(i));
            if (i < PINS) pins.add(new PinnedAppItem(apps.get(i).appRef));
        }
        ReflectionHelpers.setField(bar, "pinnedItems", pins);
        ReflectionHelpers.setField(bar, "allApps", new ArrayList<>(apps));
        bar.setMaxButtonCount(MOST_USED);
        bar.setMostUsedPageEnabled(true);
        ViewGroup plank = activity.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(activity, "mSuggestionBarView", bar);

        PlaceLayout layout = railOnTheLeft();
        activity.applyEdgeStacks(layout);
        activity.syncPinnedAppsHost(layout);
        activity.applySideStackFrameInset(FRAME_INSET);
        DockLayout dock = activity.dockLayoutFor(layout);
        bar.setDockRailSizePx(dock.railIconSizePx, dock.railSlotLengthPx);

        Scene scene = new Scene();
        scene.scroll = activity.findViewById(R.id.place_apps_bar_scroll);
        scene.ticks = activity.findViewById(R.id.place_apps_bar_indicator);
        scene.column = bar;
        scene.slot = dock.railSlotLengthPx;
        View host = activity.findViewById(R.id.place_apps_bar_host);

        // The band's inside four slots and six tenths: the length the emulator's band held.
        int height = 1080;
        layOut(activity, height);
        int inside = host.getHeight() - scene.scroll.getPaddingTop()
            - scene.scroll.getPaddingBottom();
        height += (4 * scene.slot + (6 * scene.slot) / 10) - inside;
        layOut(activity, height);
        bar.reloadWithInput("", null);
        layOut(activity, height);
        return scene;
    }

    private static int viewportPx(DockRailScrollView scroll) {
        return scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
    }

    @Test
    public void theLastMostUsedAppEndsInsideTheViewportAtTheEndOfTheScroll() {
        Scene scene = railInItsHost();
        DockRailScrollView scroll = scene.scroll;
        SuggestionBarView column = scene.column;
        int slot = scene.slot;

        assertSame("the column is the scroll's one child", column, scroll.getChildAt(0));
        assertEquals("the pins, then the most-used apps", PINS + MOST_USED,
            column.getChildCount());
        assertEquals("the column is every slot it holds",
            DockLayoutPolicy.railContentLengthPx(PINS, MOST_USED, slot), column.getHeight());
        View last = column.getChildAt(column.getChildCount() - 1);
        assertEquals("and its last slot ends where the column does", column.getHeight(),
            last.getBottom());
        assertEquals("the viewport stands the four whole slots the band holds", 4 * slot,
            viewportPx(scroll));

        scroll.scrollTo(0, Integer.MAX_VALUE / 2);
        assertEquals("the scroll travels the six slots it does not show", 6 * slot,
            scroll.getScrollY());
        // In the scroll's own coordinates, where its draw clips: the last slot's bottom is the
        // viewport's, so the last app is whole, and nothing is left below it.
        int lastBottom = scroll.getPaddingTop() + last.getBottom() - scroll.getScrollY();
        assertEquals("the last most-used app ends at the viewport's end",
            scroll.getHeight() - scroll.getPaddingBottom(), lastBottom);
        int firstShownTop = scroll.getPaddingTop()
            + column.getChildAt(column.getChildCount() - 4).getTop() - scroll.getScrollY();
        assertEquals("and the top of the viewport is a slot's top, not one cut across",
            scroll.getPaddingTop(), firstShownTop);
    }

    @Test
    public void theTicksBesideTheRailAreThereAndLitByTheScroll() {
        Scene scene = railInItsHost();
        PageTickStripView ticks = scene.ticks;
        float density = ticks.getResources().getDisplayMetrics().density;

        assertSame(ticks, scene.column.boundPageIndicator());
        assertEquals(View.VISIBLE, ticks.getVisibility());
        assertEquals("the strip's own band beside the rail", PageTickStrip.bandPx(density),
            ticks.getWidth());
        assertTrue("and the rail's length", ticks.getHeight() > 0);
        assertEquals("the pins and the most-used apps", 2, ticks.getPageCount());
        assertEquals("the second warm", 1, ticks.getDynamicPageIndex());
        assertEquals(0f, ticks.getPagePosition(), 0.001f);
        assertTrue("the strip paints its ticks at rest", paintedPixels(ticks) > 0);

        scene.scroll.scrollTo(0, Integer.MAX_VALUE / 2);
        assertEquals(View.VISIBLE, ticks.getVisibility());
        assertEquals("at the end the most-used apps are lit", 1f, ticks.getPagePosition(),
            0.001f);
        assertTrue("and still paints them there", paintedPixels(ticks) > 0);
    }

    private static int paintedPixels(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
            Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int painted = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) painted++;
            }
        }
        bitmap.recycle();
        return painted;
    }
}
