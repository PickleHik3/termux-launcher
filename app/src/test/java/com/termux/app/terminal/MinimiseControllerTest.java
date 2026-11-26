package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.pageswitch.PageBarModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Minimise takes a window out of the row; Overview's card brings it back at its place. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class MinimiseControllerTest {

    /** The row: terminal windows by name, and a Linux window 7 at place 1. */
    private final List<String> row = new ArrayList<>(Arrays.asList("a", "b", "c"));
    private final List<Long> minimisedLinux = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();

    private final MinimiseController<String> controller = new MinimiseController<>(
        new MinimiseController.Host<String>() {
            @Override public long linuxAt(int place) { return place == 9 ? 7L : 0L; }
            @Override public void minimiseLinux(long id) { minimisedLinux.add(id); calls.add("min linux " + id); }
            @Override public void restoreLinux(long id) { calls.add("restore linux " + id); }
            @Override public void minimisedLinuxIds(@NonNull List<Long> out) { out.clear(); out.addAll(minimisedLinux); }
            @Override public int terminalAtPlace(int place) { return place < row.size() ? place : -1; }
            @Override public int terminalCount() { return row.size(); }
            @Nullable @Override public String removeFromRow(int index) { return row.remove(index); }
            @Override public void returnToRow(@NonNull String w, int index) { row.add(index, w); calls.add("back " + w + "@" + index); }
            @Override public boolean hasShells(@NonNull String w) { return true; }
            @Nullable @Override public String name(@NonNull String w) { return w; }
            @Nullable @Override public String linuxName(long id) { return "gimp"; }
            @Nullable @Override public String linuxIconKey(long id) { return "org.gimp"; }
            @Nullable @Override public Bundle save(@NonNull String w) {
                Bundle b = new Bundle();
                b.putString("w", w);
                return b;
            }
        });

    @Test public void minimiseTakesTheWindowOutAndTheCardBringsItBackAtItsPlace() {
        assertTrue(controller.minimise(1));
        assertEquals(Arrays.asList("a", "c"), row);
        controller.restore(0);
        assertEquals(Arrays.asList("a", "b", "c"), row);
        assertEquals(Arrays.asList("back b@1"), calls);
    }

    @Test public void theRowsLastTerminalWindowStays() {
        assertTrue(controller.minimise(0));
        assertTrue(controller.minimise(0));
        assertFalse("one left", controller.minimise(0));
        assertEquals(Arrays.asList("c"), row);
    }

    @Test public void aLinuxWindowGoesByItsOwnPathAndComesBackAfterTheTerminalCards() {
        controller.minimise(2);
        assertTrue(controller.minimise(9));
        PageBarModel.Input in = new PageBarModel.Input();
        controller.fill(in);
        assertEquals(Arrays.asList("c", "gimp"), in.minimisedNames);
        assertTrue(in.minimisedLinux[1]);
        assertEquals("org.gimp", in.minimisedAppIds.get(1));
        controller.restore(1);
        assertEquals("restore linux 7", calls.get(calls.size() - 1));
    }

    @Test public void theMinimisedWindowsAreSavedWithTheLayout() {
        controller.minimise(2);
        Bundle root = new Bundle();
        controller.save(root);
        controller.windows().clear();
        controller.restoreState(root, state -> state.getString("w"));
        assertEquals(1, controller.windows().size());
        assertEquals("c", controller.windows().get(0).window);
        assertEquals(2, controller.windows().get(0).index);
    }
}
