package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** A Workspace's columns as the Scrolling policy lays them out (#418), through the public interface. */
public class ScrollingColumnsTest {

    private static final ScrollingLayoutPolicy.Viewport PORTRAIT =
        new ScrollingLayoutPolicy.Viewport(1000f, 2000f, 20f, false);
    private static final ScrollingLayoutPolicy.Viewport LANDSCAPE =
        new ScrollingLayoutPolicy.Viewport(2000f, 1000f, 20f, true);

    private static TilingStack withShells(int count) {
        TilingStack stack = TilingStack.empty(1);
        for (int i = 0; i < count; i++) stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        return stack;
    }

    @Test
    public void theColumnsFollowTheStackOrderAndItsFocus() {
        TilingStack stack = withShells(3);
        TilingWorkspace one = stack.workspace(1);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(one, null, PORTRAIT);

        assertEquals(Arrays.toString(new long[] {1, 2, 3}), Arrays.toString(state.windowIdsInOrder()));
        assertEquals(3, state.focusedWindow);
        // Each full width: the focused third column is shown whole.
        assertEquals(2 * (1000f + 20f), state.scroll, 0.01f);
    }

    @Test
    public void aNewTerminalOpensAsTheColumnAfterTheFocusedOne() {
        TilingStack stack = withShells(3);
        stack.focusWindow(1);
        long opened = stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        assertEquals(Arrays.toString(new long[] {1, opened, 2, 3}),
            Arrays.toString(state.windowIdsInOrder()));
        assertEquals(opened, state.focusedWindow);
        assertEquals(1000f + 20f, state.scroll, 0.01f);
    }

    @Test
    public void focusingAColumnScrollsOnlyAsFarAsNeeded() {
        TilingStack stack = withShells(3);
        ScrollingLayoutPolicy.State before = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        stack.goTo(Place.workspace(1));
        stack.focusLeft();
        ScrollingLayoutPolicy.State after = ScrollingColumns.sync(stack.workspace(1), before, PORTRAIT);

        assertEquals(2, after.focusedWindow);
        assertEquals(1000f + 20f, after.scroll, 0.01f);
    }

    @Test
    public void aColumnKeepsItsWidthWhileItsTopWindowStays() {
        TilingStack stack = withShells(2);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, LANDSCAPE);
        state = ScrollingLayoutPolicy.setWidthPreset(state, LANDSCAPE, 1, 0.5f);
        stack.focusWindow(2);
        stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State next = ScrollingColumns.sync(stack.workspace(1), state, LANDSCAPE);

        assertEquals(0.5f, next.column(0).widthFraction, 0.0001f);
        assertTrue(next.column(0).preset);
        // Updated for round 2: the new window opens a third wide beside the full one it narrows.
        assertEquals(2f / 3f, next.column(1).widthFraction, 0.0001f);
        assertEquals(1f / 3f, next.column(2).widthFraction, 0.0001f);
    }

    @Test
    public void aWindowLeftAloneFillsTheViewportAgain() {
        TilingStack stack = withShells(1);
        stack.focusWindow(1);
        ScrollingLayoutPolicy.State one = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        long second = stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State two = ScrollingColumns.sync(stack.workspace(1), one, PORTRAIT);
        stack.close(second);
        ScrollingLayoutPolicy.State alone = ScrollingColumns.sync(stack.workspace(1), two, PORTRAIT);

        assertEquals(1, alone.columnCount());
        assertEquals(1f, alone.column(0).widthFraction, 0.0001f);
    }

    @Test
    public void aLoneWindowFillsTheViewport() {
        TilingStack stack = TilingStack.empty(1);
        ScrollingLayoutPolicy.State empty = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        ScrollingLayoutPolicy.State one = ScrollingColumns.sync(stack.workspace(1), empty, PORTRAIT);

        assertEquals(1f, one.column(0).widthFraction, 0.0001f);
    }

    @Test
    public void inPortraitASecondWindowOpensHalfBesideTheFirstWhichNarrows() {
        // Round 2: a Workspace with three windows showed one full-width window at a time.
        TilingStack stack = withShells(1);
        stack.focusWindow(1);
        ScrollingLayoutPolicy.State one = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        long second = stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State two = ScrollingColumns.sync(stack.workspace(1), one, PORTRAIT);

        assertEquals(0.5f, two.column(0).widthFraction, 0.0001f);
        assertEquals(0.5f, two.column(1).widthFraction, 0.0001f);
        assertEquals("both in view", 0f, two.scroll, 0.01f);

        long third = stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State three = ScrollingColumns.sync(stack.workspace(1), two, PORTRAIT);
        assertEquals(0.5f, three.column(2).widthFraction, 0.0001f);
        assertEquals("the column beside keeps its half", 0.5f, three.column(1).widthFraction, 0.0001f);
        assertEquals(third, three.focusedWindow);
        // Two halves in view: the focused third column and the one before it.
        assertEquals(510f, three.scroll, 0.01f);
        assertTrue(second > 0);
    }

    @Test
    public void aColumnThatIsNotFullKeepsItsWidthWhenAWindowOpensBesideIt() {
        TilingStack stack = withShells(1);
        stack.focusWindow(1);
        ScrollingLayoutPolicy.State one = ScrollingColumns.sync(stack.workspace(1), null, LANDSCAPE);
        one = ScrollingLayoutPolicy.setWidthPreset(one, LANDSCAPE, 1, 0.5f);
        stack.open(WindowSpec.shell(null));
        ScrollingLayoutPolicy.State two = ScrollingColumns.sync(stack.workspace(1), one, LANDSCAPE);

        assertEquals(0.5f, two.column(0).widthFraction, 0.0001f);
        assertEquals(1f / 3f, two.column(1).widthFraction, 0.0001f);
    }

    @Test
    public void aStackedColumnHoldsItsTwoWindowsTopOverBottom() {
        TilingStack stack = withShells(2);
        stack.stack(2, 1);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        assertEquals(1, state.columnCount());
        assertEquals(2, state.column(0).windowCount());
        assertEquals(1, state.column(0).windowAt(0));
        assertEquals(2, state.column(0).windowAt(1));
    }

    @Test
    public void tabsShowOnlyTheActiveWindow() {
        TilingStack stack = withShells(2);
        stack.joinTab(2, 1);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        assertEquals(1, state.columnCount());
        assertEquals(1, state.column(0).windowCount());
        assertEquals(2, state.column(0).windowAt(0));
    }

    @Test
    public void anEmptyWorkspaceHasNoColumnsAndNoFocus() {
        TilingStack stack = TilingStack.empty(1);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        assertEquals(0, state.columnCount());
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, state.focusedWindow);
        assertEquals(0f, state.scroll, 0f);
    }

    @Test
    public void theSavedFormBringsBackWidthsAndScroll() {
        TilingStack stack = withShells(3);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, LANDSCAPE);
        state = ScrollingLayoutPolicy.setWidthPreset(state, LANDSCAPE, 2, 1f / 3f);
        String saved = ScrollingColumns.encode(state);

        ScrollingLayoutPolicy.State read = ScrollingColumns.decode(saved);
        ScrollingLayoutPolicy.State restored = ScrollingColumns.sync(stack.workspace(1), read, LANDSCAPE);

        assertEquals(1f / 3f, restored.column(1).widthFraction, 0.0001f);
        assertEquals(state.scroll, restored.scroll, 0.01f);
        assertEquals(state.focusedWindow, restored.focusedWindow);
    }

    @Test
    public void aSavedThirdBecomesAHalfInPortrait() {
        TilingStack stack = withShells(2);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, LANDSCAPE);
        state = ScrollingLayoutPolicy.setWidthPreset(state, LANDSCAPE, 1, 1f / 3f);

        ScrollingLayoutPolicy.State portrait =
            ScrollingColumns.sync(stack.workspace(1), ScrollingColumns.decode(ScrollingColumns.encode(state)), PORTRAIT);

        assertEquals(0.5f, portrait.column(0).widthFraction, 0.0001f);
    }

    @Test
    public void anUnreadableSavedFormIsNoState() {
        assertNull(ScrollingColumns.decode(null));
        assertNull(ScrollingColumns.decode(""));
        assertNull(ScrollingColumns.decode("{not json"));
        assertNull(ScrollingColumns.decode("[1,2]"));
        ScrollingLayoutPolicy.State odd = ScrollingColumns.decode(
            "{\"scroll\":-5,\"columns\":[{\"windows\":[],\"width\":9},{\"windows\":[4],\"width\":0.01}]}");
        assertEquals(1, odd.columnCount());
        assertEquals(ScrollingLayoutPolicy.MIN_WIDTH_FRACTION, odd.column(0).widthFraction, 0.0001f);
        assertFalse(odd.scroll < 0f);
    }

    @Test
    public void aScrollShownInNavigationModeKeepsTheColumnsAndFocus() {
        TilingStack stack = withShells(3);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        ScrollingLayoutPolicy.State slid = ScrollingColumns.withScroll(state, 123f);

        assertEquals(123f, slid.scroll, 0.01f);
        assertEquals(state.focusedWindow, slid.focusedWindow);
        assertEquals(Arrays.toString(state.windowIdsInOrder()), Arrays.toString(slid.windowIdsInOrder()));
    }
}
