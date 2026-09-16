package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.termux.R;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.Slot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The whole bottom edge stands in one ordered stack, so every bottom order renders.
 *
 * <p>They used to hang off each other by {@code layout_above} — apps over the indicator band over
 * the letters over the extra-keys pager over the keyboard — which is an order written into the
 * layout file. Whatever the Layout editor stored, the screen drew that one. The oracle here is that
 * same chain: the default arrangement has to land every host on the pixel the chain put it on, and
 * a re-ordered one has to follow {@code EdgeStackPolicy.stack} instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityBottomStackOrderTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 700;
    /** The bands, given distinct heights so a swapped pair cannot pass by coincidence. */
    private static final int APPS_PX = 160;
    private static final int BAND_PX = 9;
    private static final int AZ_PX = 52;
    private static final int KEYS_PX = 103;
    private static final int STATUS_PX = 64;
    private static final int KEYBOARD_PX = 300;

    private TermuxActivity activity;
    private ViewGroup container;
    private EdgeStackView rows;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        container = activity.findViewById(R.id.accessory_stack_container);
        rows = activity.findViewById(R.id.accessory_row_stack);
        assertNotNull(container);
        assertNotNull(rows);
        container.setVisibility(View.VISIBLE);
        show(R.id.apps_bar_viewpager, APPS_PX);
        show(R.id.apps_bar_indicator_band, BAND_PX);
        show(R.id.apps_bar_az_row, AZ_PX);
        show(R.id.terminal_toolbar_view_pager, KEYS_PX);
        show(R.id.terminal_window_bar_host, STATUS_PX);
        show(R.id.inapp_keyboard_container, KEYBOARD_PX);
    }

    // ---------------------------------------------------------------- the default order

    @Test
    public void theDefaultOrderStandsExactlyWhereTheChainStood() {
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));
        layoutContainer();

        // Read off the layout_above chain this replaced: the keyboard on the parent's bottom, the
        // extra keys over it, the letters over those, then the apps row's own host. Updated for
        // Q6: the ticks stand on the row's centre-facing side, so inside that host they are above
        // the icons rather than under them — the host spans the same pixels either way.
        int keyboardTop = HEIGHT - KEYBOARD_PX;
        assertBand(R.id.terminal_toolbar_view_pager, keyboardTop - KEYS_PX, keyboardTop);
        assertBand(R.id.apps_bar_az_row, keyboardTop - KEYS_PX - AZ_PX, keyboardTop - KEYS_PX);
        assertBand(R.id.apps_bar_indicator_band, keyboardTop - KEYS_PX - AZ_PX - BAND_PX - APPS_PX,
            keyboardTop - KEYS_PX - AZ_PX - APPS_PX);
        assertBand(R.id.apps_bar_viewpager, keyboardTop - KEYS_PX - AZ_PX - APPS_PX,
            keyboardTop - KEYS_PX - AZ_PX);
    }

    @Test
    public void theHostsStandInTheOrderThePolicyGivesThem() {
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));
        assertEquals(3, rows.getChildCount());
        assertSame("the apps row is the innermost band, so the first child",
            activity.findViewById(R.id.apps_bar_row_host), rows.getChildAt(0));
        assertSame(activity.findViewById(R.id.apps_bar_az_host), rows.getChildAt(1));
        assertSame("the keys are on the dock's rim, so the last child",
            activity.findViewById(R.id.terminal_toolbar_host), rows.getChildAt(2));
    }

    // ---------------------------------------------------------------- a re-ordered stack

    @Test
    public void aReorderedStackTurnsTheDockOver() {
        // Apps outermost (on the rim), extra keys innermost — the defect: nothing but the shipped
        // order ever rendered.
        activity.applyEdgeStacks(bottom(Element.EXTRA_KEYS, Element.AZ, Element.APPS));
        layoutContainer();

        assertSame(activity.findViewById(R.id.terminal_toolbar_host), rows.getChildAt(0));
        assertSame(activity.findViewById(R.id.apps_bar_az_host), rows.getChildAt(1));
        assertSame(activity.findViewById(R.id.apps_bar_row_host), rows.getChildAt(2));

        int keyboardTop = HEIGHT - KEYBOARD_PX;
        assertBand(R.id.apps_bar_indicator_band, keyboardTop - APPS_PX - BAND_PX,
            keyboardTop - APPS_PX);
        assertBand(R.id.apps_bar_viewpager, keyboardTop - APPS_PX, keyboardTop);
        assertBand(R.id.apps_bar_az_row, keyboardTop - APPS_PX - BAND_PX - AZ_PX,
            keyboardTop - APPS_PX - BAND_PX);
        assertBand(R.id.terminal_toolbar_view_pager,
            keyboardTop - APPS_PX - BAND_PX - AZ_PX - KEYS_PX,
            keyboardTop - APPS_PX - BAND_PX - AZ_PX);
    }

    @Test
    public void everyBandIsOnScreenWhicheverOrderTheyStandIn() {
        Element[][] orders = {
            {Element.APPS, Element.AZ, Element.EXTRA_KEYS},
            {Element.EXTRA_KEYS, Element.AZ, Element.APPS},
            {Element.AZ, Element.EXTRA_KEYS, Element.APPS},
            {Element.APPS, Element.EXTRA_KEYS, Element.AZ},
        };
        for (Element[] order : orders) {
            activity.applyEdgeStacks(bottom(order));
            layoutContainer();
            int total = APPS_PX + BAND_PX + AZ_PX + KEYS_PX;
            assertEquals(java.util.Arrays.toString(order) + " fills the dock",
                total, rows.getHeight());
            assertEquals(java.util.Arrays.toString(order) + " sits on the keyboard",
                HEIGHT - KEYBOARD_PX, topIn(container, rows) + rows.getHeight());
        }
    }

    // ---------------------------------------------------------------- above the keyboard

    @Test
    public void theRowsStandAboveTheDockedKeyboard() {
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));
        layoutContainer();
        View keyboard = activity.findViewById(R.id.inapp_keyboard_container);
        assertTrue(topIn(container, rows) + rows.getHeight() <= topIn(container, keyboard));
    }

    @Test
    public void theRowsFallBackToTheDocksBottomWhenTheKeyboardIsGoneOrLiftedOut() {
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));

        // Closed.
        View keyboard = activity.findViewById(R.id.inapp_keyboard_container);
        keyboard.setVisibility(View.GONE);
        layoutContainer();
        assertEquals("keyboard down", HEIGHT, topIn(container, rows) + rows.getHeight());

        // Floating or split: the controller takes the keyboard container out of the accessory
        // stack entirely, and the rows must still land on the stack's own bottom.
        ((ViewGroup) keyboard.getParent()).removeView(keyboard);
        layoutContainer();
        assertEquals("keyboard lifted out", HEIGHT, topIn(container, rows) + rows.getHeight());
    }

    // ---------------------------------------------------------------- the furniture travels

    @Test
    public void theFurnitureTravelsWithItsOwnRow() {
        assertSame("the ticks over the icons belong to the apps row",
            activity.findViewById(R.id.apps_bar_row_host),
            activity.findViewById(R.id.apps_bar_indicator_band).getParent());
        assertSame("the keybind strip keeps the letters' slot",
            activity.findViewById(R.id.apps_bar_az_host),
            activity.findViewById(R.id.keybind_hint_dock_row).getParent());
        // Updated for P8: the hairline is not furniture of the extra keys any more. It was drawn
        // at the top of their host whatever stood above it, so with the keys as the outermost band
        // it cut across the dock's own top edge. The stack draws the seams instead.
        assertEquals("the extra-keys divider id is gone", 0,
            activity.getResources().getIdentifier(
                "extrakeys_divider", "id", activity.getPackageName()));
    }

    // ---------------------------------------------------------------- the seams

    @Test
    public void theStackSeparatesItsBandsAndNeverItsOwnEdges() {
        // Three bands on one sheet of glass: a hairline in each gap, and none at either end.
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));
        assertEquals(2, rows.getSeparatorCount());

        // Re-ordered, it is still one per gap — the line belongs to the gap, not to a row.
        activity.applyEdgeStacks(bottom(Element.AZ, Element.EXTRA_KEYS, Element.APPS));
        assertEquals(2, rows.getSeparatorCount());

        // A lone band has nothing to be separated from, which is the defect: the extra keys drew
        // their divider across the dock's rim.
        activity.applyEdgeStacks(bottom(Element.EXTRA_KEYS)
            .withSlot(Element.APPS, new Slot(true, Edge.BOTTOM, 2))
            .withSlot(Element.AZ, new Slot(true, Edge.BOTTOM, 1)));
        assertEquals(0, rows.getSeparatorCount());
    }

    @Test
    public void theKeybindStripFillsTheLettersBand() {
        activity.applyEdgeStacks(bottom(Element.APPS, Element.AZ, Element.EXTRA_KEYS));
        View strip = activity.findViewById(R.id.keybind_hint_dock_row);
        strip.setVisibility(View.VISIBLE);
        // The letters go INVISIBLE, not gone, while the strip holds their slot.
        activity.findViewById(R.id.apps_bar_az_row).setVisibility(View.INVISIBLE);
        layoutContainer();
        assertEquals(AZ_PX, strip.getHeight());
        assertEquals(topIn(container, activity.findViewById(R.id.apps_bar_az_row)),
            topIn(container, strip));
    }

    // ---------------------------------------------------------------- the status bar

    @Test
    public void theStatusBarStandsWhereItsOrderPutsItAmongTheDocksRows() {
        // The defect P9 fixed: the bar had a stack of its own above the whole dock, so this order
        // — apps, extra keys, status, letters, reading down the screen — drew as status, apps,
        // extra keys, letters however the user arranged it.
        activity.applyEdgeStacks(
            bottom(Element.APPS, Element.EXTRA_KEYS, Element.STATUS, Element.AZ));

        assertEquals(4, rows.getChildCount());
        assertSame(activity.findViewById(R.id.apps_bar_row_host), rows.getChildAt(0));
        assertSame(activity.findViewById(R.id.terminal_toolbar_host), rows.getChildAt(1));
        assertSame("the bar stands between the keys and the letters, as ordered",
            activity.findViewById(R.id.terminal_window_bar_host), rows.getChildAt(2));
        assertSame(activity.findViewById(R.id.apps_bar_az_host), rows.getChildAt(3));

        layoutContainer();
        int keyboardTop = HEIGHT - KEYBOARD_PX;
        assertBand(R.id.apps_bar_az_row, keyboardTop - AZ_PX, keyboardTop);
        assertBand(R.id.terminal_window_bar_host, keyboardTop - AZ_PX - STATUS_PX,
            keyboardTop - AZ_PX);
        assertBand(R.id.terminal_toolbar_view_pager,
            keyboardTop - AZ_PX - STATUS_PX - KEYS_PX, keyboardTop - AZ_PX - STATUS_PX);

        assertTrue("on the plank it wears no glass of its own",
            activity.statusBarStandsOnTheDockPlank());
        assertEquals("four bands on one sheet, so a hairline in each of the three gaps",
            3, rows.getSeparatorCount());
    }

    @Test
    public void theBandTouchingTheCanvasKeepsItsOwnGlassAndTodaysPixels() {
        // Status innermost, which is the only placement a bottom bar has ever had: it stands over
        // the whole dock on a sheet of its own, and the dock's three rows are seamed as before.
        activity.applyEdgeStacks(
            bottom(Element.STATUS, Element.APPS, Element.AZ, Element.EXTRA_KEYS));

        assertEquals(4, rows.getChildCount());
        assertSame(activity.findViewById(R.id.terminal_window_bar_host), rows.getChildAt(0));
        assertSame(activity.findViewById(R.id.apps_bar_row_host), rows.getChildAt(1));
        assertSame(activity.findViewById(R.id.apps_bar_az_host), rows.getChildAt(2));
        assertSame(activity.findViewById(R.id.terminal_toolbar_host), rows.getChildAt(3));

        layoutContainer();
        // The same pixels the chain drew, with the bar sitting exactly on top of them.
        int keyboardTop = HEIGHT - KEYBOARD_PX;
        assertBand(R.id.terminal_toolbar_view_pager, keyboardTop - KEYS_PX, keyboardTop);
        assertBand(R.id.apps_bar_az_row, keyboardTop - KEYS_PX - AZ_PX, keyboardTop - KEYS_PX);
        assertBand(R.id.apps_bar_indicator_band, keyboardTop - KEYS_PX - AZ_PX - BAND_PX - APPS_PX,
            keyboardTop - KEYS_PX - AZ_PX - APPS_PX);
        assertBand(R.id.apps_bar_viewpager, keyboardTop - KEYS_PX - AZ_PX - APPS_PX,
            keyboardTop - KEYS_PX - AZ_PX);
        assertBand(R.id.terminal_window_bar_host,
            keyboardTop - KEYS_PX - AZ_PX - BAND_PX - APPS_PX - STATUS_PX,
            keyboardTop - KEYS_PX - AZ_PX - BAND_PX - APPS_PX);

        assertFalse("it is not on the dock's sheet, so it keeps its own",
            activity.statusBarStandsOnTheDockPlank());
        assertEquals("and no hairline floats between the two sheets", 2, rows.getSeparatorCount());
    }

    @Test
    public void theWholeBottomStackStandsAboveTheKeyboardInEveryForm() {
        activity.applyEdgeStacks(
            bottom(Element.APPS, Element.EXTRA_KEYS, Element.STATUS, Element.AZ));
        View keyboard = activity.findViewById(R.id.inapp_keyboard_container);
        View statusBar = activity.findViewById(R.id.terminal_window_bar_host);

        // Docked: the keyboard is the stack's own bottom child and every band is over it.
        layoutContainer();
        assertTrue(topIn(container, rows) + rows.getHeight() <= topIn(container, keyboard));
        assertTrue(topIn(container, statusBar) + statusBar.getHeight()
            <= topIn(container, keyboard));
        assertEquals(APPS_PX + BAND_PX + KEYS_PX + STATUS_PX + AZ_PX, rows.getHeight());

        // Closed.
        keyboard.setVisibility(View.GONE);
        layoutContainer();
        assertEquals("keyboard down", HEIGHT, topIn(container, rows) + rows.getHeight());

        // Floating or split: the controller lifts the keyboard container out of the stack, and
        // the bands — the status bar with them — land on the stack's own bottom.
        ((ViewGroup) keyboard.getParent()).removeView(keyboard);
        layoutContainer();
        assertEquals("keyboard lifted out", HEIGHT, topIn(container, rows) + rows.getHeight());
        assertEquals(APPS_PX + BAND_PX + KEYS_PX + STATUS_PX + AZ_PX, rows.getHeight());
    }

    /**
     * The developer's order — extra keys, apps row, letters reading up from the dock's rim — with
     * the apps row carrying the air the dock gives its icons. The seam between the row and the keys
     * used to be drawn on the child boundary, which is the keys' own rim: the line sat hard against
     * them and a whole row padding away from the icons. It belongs to the gap, so it goes in the
     * middle of it, for every adjacent pair.
     */
    @Test
    public void everySeamSitsInTheMiddleOfTheGapBetweenTwoBandsContent() {
        int airTop = 17;
        int airBottom = 8;
        activity.findViewById(R.id.apps_bar_viewpager).setPadding(0, airTop, 0, airBottom);
        activity.applyEdgeStacks(bottom(Element.EXTRA_KEYS, Element.APPS, Element.AZ));
        layoutContainer();

        assertEquals(2, rows.getSeparatorCount());
        int[] seams = rows.separatorCenters();
        assertEquals(2, seams.length);

        // Walking down the stack: the keys, then the apps row's host, then the letters.
        int keysBottom = topIn(rows, activity.findViewById(R.id.terminal_toolbar_view_pager))
            + KEYS_PX;
        int ticksTop = topIn(rows, activity.findViewById(R.id.apps_bar_indicator_band));
        int iconsBottom = topIn(rows, activity.findViewById(R.id.apps_bar_viewpager))
            + APPS_PX - airBottom;
        int lettersTop = topIn(rows, activity.findViewById(R.id.apps_bar_az_row));

        assertEquals("the keys and the row's ticks split their gap",
            (keysBottom + ticksTop) / 2, seams[0]);
        assertEquals("the icons and the letters split theirs",
            (iconsBottom + lettersTop) / 2, seams[1]);
        // The defect, in the one gap this arrangement leaves: the line used to be drawn on the
        // child boundary, which is the letters' own rim.
        assertTrue("the seam is hard against neither band: " + seams[1],
            seams[1] > iconsBottom && seams[1] < lettersTop);
    }

    /**
     * The air the row's icons stand in, on the arrangement the complaint was made on: the status
     * bar innermost, then the keys, the apps row and the letters on the dock's rim. The row used
     * to carry about twice the air of the bands beside it — its band was the extra-keys row scaled
     * by the size preset and the icon a fill ratio of what was left, so the leftover was air. It
     * is the icon and the letters' own crown on each side now, and the ticks stand beside that air
     * rather than inside it.
     */
    @Test
    public void theSharedRowsIconStandsInSixDpOfAirOnEachSide() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        PlaceLayout layout = bottomStack(
            Element.STATUS, Element.EXTRA_KEYS, Element.APPS, Element.AZ);
        DockLayout dock = activity.dockLayoutFor(layout);
        activity.applyEdgeStacks(layout);
        activity.applyDockLayout(dock);
        layoutContainer();

        float density = activity.getResources().getDisplayMetrics().density;
        int airPx = DockLayoutPolicy.sharedRowAirPx(density);
        assertTrue("the row has to be on the dock for this to mean anything",
            dock.appsRowIconPx > 0 && dock.appsBarHeightPx > 0);
        assertEquals("the band is the icon and its air", dock.appsRowIconPx + (2 * airPx),
            dock.appsBarHeightPx);

        View pager = activity.findViewById(R.id.apps_bar_viewpager);
        View ticks = activity.findViewById(R.id.apps_bar_indicator_band);
        View letters = activity.findViewById(R.id.apps_bar_az_host);
        assertEquals(airPx, pager.getPaddingTop());
        assertEquals(airPx, pager.getPaddingBottom());

        int pagerTop = topIn(rows, pager);
        int iconTop = pagerTop + pager.getPaddingTop();
        int iconBottom = pagerTop + pager.getHeight() - pager.getPaddingBottom();
        assertEquals("the icon box is the icon", dock.appsRowIconPx, iconBottom - iconTop);
        // The ticks lead the row on the bottom edge, so they are the band above the icons.
        int ticksBottom = topIn(rows, ticks) + ticks.getHeight();
        assertEquals("ticks -> icon", airPx, iconTop - ticksBottom);
        // And the seam with the band on the dock's rim is the same air on the other side.
        assertEquals("icon -> seam", airPx, topIn(rows, letters) - iconBottom);
    }

    // ---------------------------------------------------------------- helpers

    /** Everything on the bottom edge, innermost (nearest the canvas) first — nothing anywhere else. */
    private static PlaceLayout bottomStack(Element... innermostFirst) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (int i = 0; i < innermostFirst.length; i++)
            slots.put(innermostFirst[i], new Slot(false, Edge.BOTTOM, innermostFirst.length - i));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** Everything on the bottom edge in the order given, outermost (on the dock's rim) last. */
    private static PlaceLayout bottom(Element... innermostFirst) {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        for (int i = 0; i < innermostFirst.length; i++)
            slots.put(innermostFirst[i], new Slot(false, Edge.BOTTOM, innermostFirst.length - i));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    private void show(int viewId, int heightPx) {
        View view = activity.findViewById(viewId);
        assertNotNull(view);
        view.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = heightPx;
        view.setLayoutParams(params);
    }

    private void layoutContainer() {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, WIDTH, HEIGHT);
    }

    private void assertBand(int viewId, int top, int bottom) {
        View view = activity.findViewById(viewId);
        int actualTop = topIn(container, view);
        assertEquals(view.getClass().getSimpleName() + " top", top, actualTop);
        assertEquals(view.getClass().getSimpleName() + " bottom", bottom,
            actualTop + view.getHeight());
    }

    /** A band's top inside the accessory stack, whatever host it now travels in. */
    private static int topIn(ViewGroup root, View view) {
        int top = 0;
        for (View at = view; at != root; ) {
            top += at.getTop();
            ViewParent parent = at.getParent();
            assertTrue(view + " is inside the accessory stack", parent instanceof View);
            at = (View) parent;
        }
        return top;
    }
}
