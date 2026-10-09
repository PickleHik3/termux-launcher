package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.ViewGroup;

import com.termux.R;
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

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * A destroyed activity never lends its dock to the window it used to own.
 *
 * <p>The defect this pins down: answering the tour's usage card relaunches the activity, and the
 * relaunch hands the old DecorView to the new activity. A catalogue refresh the old dock had
 * started finished after that and ran the old activity's {@link TermuxActivity#syncPinnedAppsHost},
 * whose {@code findViewById} now resolved the new activity's plank and stacked the old dock over
 * the new one. Every touch then drove the old activity's drawer controller, bound to views no
 * longer on screen: the drawer opened invisibly, took the keyboard with it, and stayed engaged.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityRelaunchDockTest {

    private static PlaceLayout dockLayout() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            slots.put(element, Slot.on(Edge.BOTTOM, element));
        }
        slots.put(Element.STATUS, Slot.on(Edge.TOP, Element.STATUS));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    @Test
    public void destroyedActivityLeavesTheLivePlankAlone() {
        TermuxActivity dead = Robolectric.buildActivity(TermuxActivity.class).get();
        // Its window's content stands in for the relaunched activity's tree, which the old
        // activity's findViewById reaches through the shared DecorView.
        dead.setContentView(R.layout.activity_termux);
        ViewGroup livePlank = dead.findViewById(R.id.apps_bar_plank_layer);
        int liveChildren = livePlank.getChildCount();
        // The old dock, parked: the relaunch already cleared it out of the old content.
        SuggestionBarView oldDock = new SuggestionBarView(dead, null);
        ReflectionHelpers.setField(dead, "mSuggestionBarView", oldDock);
        ReflectionHelpers.setField(dead, "mDestroyed", true);

        boolean changed = dead.syncPinnedAppsHost(dockLayout());

        assertFalse(changed);
        assertNull(oldDock.getParent());
        assertEquals(liveChildren, livePlank.getChildCount());
    }
}
