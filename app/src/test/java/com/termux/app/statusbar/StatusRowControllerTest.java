package com.termux.app.statusbar;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceLayout;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallLayout;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = android.app.Application.class)
public class StatusRowControllerTest {

    @Test
    public void sessionsIndicator_standsOnlyInAGlyphsStrip() {
        assertTrue(StatusRowController.sessionsIndicatorShown(true, false, false));
        // The Marks line stands alone in the strip: no sessions box left of the marks.
        assertFalse(StatusRowController.sessionsIndicatorShown(true, false, true));
        // The thin row holds the line or the readings only, under either look.
        assertFalse(StatusRowController.sessionsIndicatorShown(true, true, false));
        assertFalse(StatusRowController.sessionsIndicatorShown(true, true, true));
        assertFalse(StatusRowController.sessionsIndicatorShown(false, false, false));
    }

    @Test
    public void aHomeWithNoBarKeepsTheRowFromRestThroughTheCrossingToTheLanding() {
        FakeHost host = new FakeHost(false);
        StatusRowController controller = arranged(host);
        host.committed = PageKind.HOME;
        controller.syncStatusPresentation();
        assertTrue(controller.isRow());
        int swaps = host.swaps;
        // The crossing toward the Terminal, its landing and its commit: no swap anywhere.
        host.other = PageRef.TERMINAL;
        controller.syncStatusPresentation();
        host.committed = PageKind.TERMINAL;
        controller.syncStatusPresentation();
        host.other = null;
        controller.syncStatusPresentation();
        // And back toward Home.
        host.other = PageRef.HOME;
        controller.syncStatusPresentation();
        host.committed = PageKind.HOME;
        host.other = null;
        controller.syncStatusPresentation();
        assertTrue(controller.isRow());
        assertEquals(swaps, host.swaps);
    }

    @Test
    public void aHomeThatShowsTheBarStillWearsTheStrip() {
        FakeHost host = new FakeHost(true);
        StatusRowController controller = arranged(host);
        host.committed = PageKind.HOME;
        controller.syncStatusPresentation();
        assertFalse(controller.isRow());
        host.committed = PageKind.TERMINAL;
        controller.syncStatusPresentation();
        assertTrue(controller.isRow());
    }

    @Test
    public void whetherHomeShowsTheBarIsReadOncePerChangeNotPerFrame() {
        FakeHost host = new FakeHost(false);
        StatusRowController controller = arranged(host);
        host.committed = PageKind.HOME;
        for (int frame = 0; frame < 10; frame++) controller.syncStatusPresentation();
        assertEquals(1, host.homeBarReads);
        host.other = PageRef.TERMINAL;
        for (int frame = 0; frame < 10; frame++) controller.syncStatusPresentation();
        assertEquals(2, host.homeBarReads);
        // An arrangement pass may have changed Home's layout: read again, once.
        controller.readArrangement(true, layout(), null);
        for (int frame = 0; frame < 10; frame++) controller.syncStatusPresentation();
        assertEquals(3, host.homeBarReads);
    }

    private static StatusRowController arranged(@NonNull FakeHost host) {
        StatusRowController controller = new StatusRowController(host);
        controller.readArrangement(true, layout(), null);
        assertTrue(controller.paneRowStands());
        return controller;
    }

    @NonNull
    private static PlaceLayout layout() {
        return new PlaceLayout(PlaceLayout.Edge.TOP, PlaceLayout.RowPlacement.BOTTOM, true,
            PlaceLayout.Edge.BOTTOM, PlaceLayout.RowPlacement.BOTTOM,
            PlaceLayout.KeyboardMode.RESIZE, PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    /** A host with no views and no wall (always at rest), whose Home shows the bar or not. */
    private static final class FakeHost implements StatusRowController.Host {
        final Context context = ApplicationProvider.getApplicationContext();
        final TermuxAppSharedPreferences preferences =
            TermuxAppSharedPreferences.build(context, false);
        final boolean homeBar;
        PageKind committed = PageKind.TERMINAL;
        @Nullable PageRef other;
        int swaps;
        int homeBarReads;

        FakeHost(boolean homeBar) {
            this.homeBar = homeBar;
            preferences.setStatusPlace(StatusPlace.PANE.value);
        }

        @Nullable @Override public TermuxAppSharedPreferences preferences() { return preferences; }
        @NonNull @Override public Context context() { return context; }
        @Nullable @Override public View findView(int id) { return null; }
        @NonNull @Override public PageKind wallPage() { return committed; }
        @Nullable @Override public PaneWallLayout wall() { return null; }
        @Nullable @Override public PageRef crossingOtherEnd() { return other; }
        @Nullable @Override public View displayWindow() { return null; }
        @Nullable @Override public PaneContentFrame statusRowFrame() { return null; }
        @Override public void setPaneStatusRowPx(int px) {}
        /** Called once per presentation swap. */
        @Override public void refreshTerminalWindowBar() { swaps++; }
        @Override public boolean marksLookShown() { return true; }
        @Override public boolean homeShowsBar(@NonNull PageRef home) {
            homeBarReads++;
            return homeBar;
        }
    }
}
