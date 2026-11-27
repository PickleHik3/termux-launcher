package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.chrome.MaximiseController;
import com.termux.app.chrome.MaximisePolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * A maximised window's room parity: for each (maximised, keyboard up) on every layout fixture,
 * Docked and Floating apart, the room the Terminal's rest gives its panes (read the way
 * CrossingController reads it: the half the chrome stands now, its rows from
 * {@link MaximiseController#layoutAt} for that rest's keyboard, whichever way the keyboard is
 * now) is the room the elements that show at that rest leave (the pass on the layout the views
 * apply there). A hidden apps row or extra-keys row keeps no room.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class MaximiseRoomParityTest {

    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private static final boolean[] BOOLS = {false, true};

    private final LayoutFixtures.Fixture f;
    private boolean keyboardNow;
    private final MaximiseController controller = new MaximiseController(new MaximiseController.Host() {
        @Override public void applyMaximise() { }
        @Override public void navigateTo(@NonNull PageBarModel.Target t) { }
        @NonNull @Override public String frontKey() { return ""; }
        @Override public boolean keyboardShown() { return keyboardNow; }
        @Override public void post(@NonNull Runnable r) { }
        @Override public void postDelayed(@NonNull Runnable r, long ms) { }
        @Override public void removeCallbacks(@NonNull Runnable r) { }
    });

    public MaximiseRoomParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private static PageBarModel.Target window() {
        PageBarModel.Input in = new PageBarModel.Input();
        in.pages = Arrays.asList(new PageBarModel.Page(1, PageKind.HOME, null),
            new PageBarModel.Page(2, PageKind.TERMINAL, null));
        in.windowNames = Arrays.asList(null, null);
        in.currentPageId = 2;
        return PageBarModel.build(in).rowWindow(0).target;
    }

    private ResolvedArrangement stored() {
        return ResolvedArrangement.of(f.window, LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false,
            false, PlaceOrientation.PORTRAIT, LayoutVariant.NORMAL, PageRef.TERMINAL);
    }

    private LayoutFixtures.Fixture standing(@NonNull PlaceLayout window) {
        return new LayoutFixtures.Fixture(f.name, f.widthPx, f.heightPx, window, f.home,
            f.paneCount);
    }

    /** The room the Terminal's rest at {@code up} gives, read while the keyboard is {@code now}. */
    private int roomGiven(@NonNull ResolvedArrangement a, boolean now, boolean up,
                          boolean floating) {
        keyboardNow = now;
        LayoutFixtures.Fixture chrome = standing(controller.arrangement(a).layout());
        StackLayout.RestInput in = chrome.rest(PageRef.TERMINAL, PageRef.TERMINAL, up, floating);
        in.ownRows(controller.layoutAt(a, up), f.extraKeysRowPx);
        return PageRoom.bottomInsetPx(in);
    }

    /** The room the elements shown at that rest leave: the pass on the layout the views apply. */
    private int roomLeft(@NonNull PlaceLayout shown, boolean up, boolean floating) {
        return standing(shown).settle(PageRef.TERMINAL, up, floating).contentReservationPx;
    }

    @Test public void theRoomGivenIsTheRoomTheShownElementsLeave() {
        ResolvedArrangement a = stored();
        for (boolean maximised : BOOLS) {
            if (maximised != controller.isMaximised()) controller.toggle(window());
            for (boolean floating : BOOLS) for (boolean up : BOOLS) for (boolean now : BOOLS) {
                PlaceLayout shown = maximised ? MaximisePolicy.layoutFor(f.window, false, up)
                    : f.window;
                assertEquals(f.name + " maximised=" + maximised + " floating=" + floating
                        + " up=" + up + " read with the keyboard up=" + now,
                    roomLeft(shown, up, floating), roomGiven(a, now, up, floating));
            }
        }
    }

    /** Maximised with the keyboard down, nothing stands along the bottom: no row's room is kept. */
    @Test public void maximisedWithTheKeyboardDownKeepsNoRowsRoom() {
        ResolvedArrangement a = stored();
        controller.toggle(window());
        PlaceLayout bare = f.window;
        for (Element e : Element.values()) {
            Slot slot = bare.slot(e);
            if (slot != null && !slot.hidden) bare = bare.withSlot(e, slot.withHidden(true));
        }
        for (boolean floating : BOOLS) {
            int given = roomGiven(a, false, false, floating);
            assertEquals(f.name + " floating=" + floating, roomLeft(bare, false, floating), given);
            assertTrue(f.name + " floating=" + floating + ": less than at rest",
                given < roomLeft(f.window, false, floating));
        }
    }
}
