package com.termux.app.statusbar;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeEdgeRule;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.Dress;
import com.termux.app.chrome.LiveChromeShape;
import com.termux.app.dock.DockRows;
import com.termux.app.place.ChromeShape;
import com.termux.app.place.Crossing;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.SeamDress;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.terminal.GeometryScheduler;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.wall.PaneWallController;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The status bar's controller over a fake {@link StatusBarController.Host} (ADR 0018 wave B step
 * one, D17): the state the bar's code kept in the activity, pinned where it now lives.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class StatusBarControllerTest {

    private FakeHost mHost;
    private StatusBarController mBar;

    @Before
    public void setUp() {
        mHost = new FakeHost();
        mBar = new StatusBarController(mHost);
    }

    private void standOn(@NonNull PlaceLayout.Edge edge) {
        ReflectionHelpers.setField(mBar, "mStatusBarEdge", edge);
    }

    private void withPreferences() {
        mHost.prefs = TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);
        mHost.store = new PlaceLayoutStore(mHost.prefs);
    }

    @Test
    public void aBarDownASideIsCompactWhateverIsStored() {
        withPreferences();
        mHost.store.setStatusCompact(PlaceOrientation.PORTRAIT, false);
        standOn(PlaceLayout.Edge.TOP);
        assertFalse("along the top the stored choice holds", mBar.isStatusBarCompact());
        standOn(PlaceLayout.Edge.LEFT);
        assertTrue("down a side the bar cannot expand", mBar.isStatusBarCompact());
        standOn(PlaceLayout.Edge.RIGHT);
        assertTrue(mBar.isStatusBarCompact());
    }

    @Test
    public void theEditorHoldsTheBarOpenWithoutTouchingTheStoredChoice() {
        withPreferences();
        mHost.store.setStatusCompact(PlaceOrientation.PORTRAIT, true);
        mBar.setTopStatusBarExpandedForEditor(true, false);
        assertFalse("held open", mBar.isStatusBarCompact());
        assertTrue("stored choice untouched",
            mHost.store.isStatusCompact(PlaceOrientation.PORTRAIT));

        // An expand request while held changes nothing stored.
        mBar.setTopStatusBarCollapsed(false, false);
        assertTrue(mHost.store.isStatusCompact(PlaceOrientation.PORTRAIT));
        assertFalse(mBar.isStatusBarCompact());

        // A collapse request while held is what the bar does on letting go: still open now.
        mHost.store.setStatusCompact(PlaceOrientation.PORTRAIT, false);
        mBar.setTopStatusBarCollapsed(true, false);
        assertTrue("the request is stored", mHost.store.isStatusCompact(PlaceOrientation.PORTRAIT));
        assertFalse("the editor still holds it open", mBar.isStatusBarCompact());

        mBar.setTopStatusBarExpandedForEditor(false, false);
        assertTrue("let go, the stored choice shows", mBar.isStatusBarCompact());
    }

    @Test
    public void theEditorCannotHoldOpenABarDownASide() {
        withPreferences();
        standOn(PlaceLayout.Edge.LEFT);
        mBar.setTopStatusBarExpandedForEditor(true, false);
        assertTrue(mBar.isStatusBarCompact());
        assertFalse(ReflectionHelpers.<Boolean>getField(mBar, "mStatusBarExpandedForEditor"));
    }

    @Test
    public void aDragBegunMovedAndEndedReturnsItsLeaseOnce() {
        withPreferences();
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        host.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            mBar.targetStatusBarHeightPx(false, true)));
        mHost.views.put(R.id.terminal_window_bar_host, host);

        assertNull("no drag, no lease", mBar.endTopStatusBarDrag());
        mBar.dragTopStatusBar(0f);
        assertTrue(mBar.isStatusBarFoldDragging());
        assertTrue(mBar.isStatusBarFoldInteractive());
        assertEquals("the first frame takes one lease", 1, mHost.geometryHost.grids);
        mBar.dragTopStatusBar(40f);
        assertEquals("a move takes none", 1, mHost.geometryHost.grids);

        GeometryScheduler.Transition lease = mBar.endTopStatusBarDrag();
        assertNotNull(lease);
        assertFalse(mBar.isStatusBarFoldDragging());
        assertNull("returned once", mBar.endTopStatusBarDrag());
    }

    @Test
    public void theBorderSwipeGoesWithAFoldableBarAlongTheTop() {
        withPreferences();
        standOn(PlaceLayout.Edge.TOP);
        mBar.syncBorderStatusSwipe(true);
        assertTrue(mBar.isBorderStatusBarFoldable());
        assertTrue(mBar.isBorderStatusSwipeAvailable());

        mHost.paletteOpen = true;
        assertFalse("the palette has the gesture", mBar.isBorderStatusSwipeAvailable());
        mHost.paletteOpen = false;
        mHost.drawerEngaged = true;
        assertFalse("the drawer has the gesture", mBar.isBorderStatusSwipeAvailable());
        mHost.drawerEngaged = false;

        mBar.syncBorderStatusSwipe(false);
        assertFalse("put away", mBar.isBorderStatusSwipeAvailable());

        standOn(PlaceLayout.Edge.BOTTOM);
        mBar.syncBorderStatusSwipe(true);
        assertFalse("only along the top", mBar.isBorderStatusBarFoldable());
        standOn(PlaceLayout.Edge.LEFT);
        mBar.syncBorderStatusSwipe(true);
        assertFalse(mBar.isBorderStatusSwipeAvailable());
    }

    @Test
    public void theBorderSwipeNeedsPreferences() {
        standOn(PlaceLayout.Edge.TOP);
        mBar.syncBorderStatusSwipe(true);
        assertTrue(mBar.isBorderStatusBarFoldable());
        assertFalse(mBar.isBorderStatusSwipeAvailable());
    }

    @Test
    public void theTargetHeightIsTheEdgeGeometrysForEachEdgeAndState() {
        float density = RuntimeEnvironment.getApplication().getResources()
            .getDisplayMetrics().density;
        for (PlaceLayout.Edge edge : PlaceLayout.Edge.values()) {
            standOn(edge);
            for (boolean capsule : new boolean[] {false, true}) {
                for (boolean collapsed : new boolean[] {false, true}) {
                    assertEquals(edge + " capsule=" + capsule + " collapsed=" + collapsed,
                        StatusBarEdgeGeometry.thicknessPx(edge, capsule, collapsed, density),
                        mBar.targetStatusBarHeightPx(capsule, collapsed));
                }
            }
        }
        standOn(PlaceLayout.Edge.TOP);
        assertTrue("open is taller than folded along the top",
            mBar.targetStatusBarHeightPx(false, false) > mBar.targetStatusBarHeightPx(false, true));
        assertFalse(mBar.isStatusBarVertical());
        standOn(PlaceLayout.Edge.LEFT);
        assertTrue(mBar.isStatusBarVertical());
    }

    @Test
    public void theTravelXIsWrittenOncePerChangedValue() {
        CountingView host = new CountingView(RuntimeEnvironment.getApplication());
        mHost.views.put(R.id.terminal_window_bar_host, host);
        mBar.applyStatusTravelX(5f);
        mBar.applyStatusTravelX(5f);
        assertEquals(1, host.translations);
        assertEquals(5f, host.getTranslationX(), 0f);
        mBar.applyStatusTravelX(7f);
        mBar.applyStatusTravelX(7f);
        assertEquals(2, host.translations);
        mBar.applyStatusTravelX(0f);
        assertEquals(3, host.translations);
        assertEquals(0f, host.getTranslationX(), 0f);
    }

    /** Counts its translation writes. */
    private static final class CountingView extends View {
        int translations;

        CountingView(Context context) {
            super(context);
        }

        @Override
        public void setTranslationX(float translationX) {
            translations++;
            super.setTranslationX(translationX);
        }
    }

    /** Counts the grid holds the scheduler takes; posts nothing. */
    private static final class FakeGeometryHost implements GeometryScheduler.Host {
        int grids;
        @Override public void postFrame(@NonNull Runnable frame) {}
        @Override public void postAfterLayout(@NonNull Runnable afterLayout) {}
        @Override public void postDelayed(@NonNull Runnable runnable, long delayMs) {}
        @Override public void removeCallbacks(@NonNull Runnable runnable) {}
        @Override public void runPass(@NonNull GeometryScheduler.Reason reason) {}
        @Override public void beginGridHold() { grids++; }
        @Override public void finishGridHold() {}
        @Override public void beginDisplayHold() {}
        @Override public void finishDisplayHold() {}
        @Override public long layoutInputsKey() { return 0L; }
        @Override public boolean isAlive() { return true; }
    }

    private static final class FakeHost implements StatusBarController.Host {
        final android.util.SparseArray<View> views = new android.util.SparseArray<>();
        final FakeGeometryHost geometryHost = new FakeGeometryHost();
        final GeometryScheduler geometry = new GeometryScheduler(geometryHost);
        @Nullable TermuxAppSharedPreferences prefs;
        @Nullable PlaceLayoutStore store;
        boolean paletteOpen;
        boolean drawerEngaged;
        final SurfaceEditorController editor = new SurfaceEditorController(null);

        @SuppressWarnings("unchecked")
        @Nullable @Override public <T extends View> T findViewById(int id) { return (T) views.get(id); }
        @SuppressWarnings("unchecked")
        @Nullable @Override public <T extends View> T hotView(int id) { return (T) views.get(id); }
        @NonNull @Override public Resources getResources() {
            return RuntimeEnvironment.getApplication().getResources();
        }
        @NonNull @Override public Context context() { return RuntimeEnvironment.getApplication(); }
        @Override public void runOnUiThread(@NonNull Runnable action) { action.run(); }
        @Override public float dpToPx(float dp) {
            return dp * getResources().getDisplayMetrics().density;
        }
        @Override public TermuxActivityRootView termuxActivityRootView() { return null; }

        @Override public TermuxAppSharedPreferences preferences() { return prefs; }
        @Override public TermuxAppSharedProperties properties() { return null; }
        @Override public boolean isReducedMotionEnabled() { return false; }
        @Override public boolean isSplitPanesEnabled() { return true; }

        @Nullable @Override public PlaceLayoutStore placeLayoutStore() { return store; }
        @Override public PlaceOrientation currentPlaceOrientation() { return PlaceOrientation.PORTRAIT; }
        @Override public boolean isRoundedStyle(@NonNull SeamDress.Surface surface) { return false; }
        @Override public int chromeMarginDp(@NonNull SeamDress.Surface surface) { return 0; }
        @NonNull @Override public ChromeShape chromeShape(@NonNull SeamDress.Surface surface) {
            throw new UnsupportedOperationException();
        }
        @Nullable @Override public LiveChromeShape.Clip chromeClipOf(@NonNull ChromeShape.PieceId... ids) {
            return null;
        }
        @NonNull @Override public Dress dressOf(@NonNull PaneWallPage page) {
            throw new UnsupportedOperationException();
        }
        @Override public float layoutCornersPx(@NonNull SeamDress.Surface surface) { return 0f; }
        @Override public int lookPageId(@NonNull SeamDress.Surface surface) { return 0; }
        @Override public int terminalLookPageId() { return 0; }
        @Override public int resolveSurfaceHorizontalInsetPx(int configuredDp, boolean capsule) { return 0; }
        @NonNull @Override public float[] displayCornerRadiiPx() { return new float[4]; }
        @Override public boolean wallpaperSampleable() { return false; }
        @Override public ChromeRenderer chrome() { throw new UnsupportedOperationException(); }

        @Override public GeometryScheduler geometry() { return geometry; }
        @Override public void growAccessoryStackBy(int deltaPx) {}
        @Override public void applyPlaceStripInset(float expansion) {}
        @Override public DockRows dockRows() { throw new UnsupportedOperationException(); }
        @Override public void applyTerminalWindowBarBackdropInsets() {}
        @Override public void refreshTerminalWindowBar() {}
        @Override public void syncPlaceBar() {}

        @Override public boolean isAppDrawerEngaged() { return drawerEngaged; }
        @Override public boolean isCommandPaletteOpen() { return paletteOpen; }
        @Override public SurfaceEditorController surfaceEditor() { return editor; }
        @Nullable @Override public PaneWallController paneWallController() { return null; }

        @Override public Crossing crossing() { throw new UnsupportedOperationException(); }
        @Override public void applyBarMarginEase(@Nullable View host, float insetPx) {}
        @Override public void resetBarMarginEase(@NonNull View host) {}
        @NonNull @Override public ChromeEdgeRule.TopLead statusStripLead() {
            return ChromeEdgeRule.TopLead.NONE;
        }
        @Override public void markChromeTravelMoved() {}

        @Override public void ensureAiIndicatorController() {}
        @Override public SystemStatsController ensureStatsController() {
            throw new UnsupportedOperationException();
        }
        @NonNull @Override public WeatherController ensureWeatherController() {
            throw new UnsupportedOperationException();
        }
        @Nullable @Override public SystemStatsController statsController() { return null; }
        @Nullable @Override public WeatherController weatherController() { return null; }
        @Nullable @Override public SystemStatsCardView statsCardView() { return null; }
        @Override public StatusCardHost statusCardHost() { return null; }
        @Override public boolean statsConsumerVisible() { return false; }
        @Override public long statsInterval(long normalMs) { return normalMs; }
        @Override public long statsBarIntervalMs() { return 0L; }
        @Override public long statsCardIntervalMs() { return 0L; }
        @Override public StatusBarStatSmoother barCpuSmoother() { return null; }
        @Override public StatusBarStatSmoother barMemorySmoother() { return null; }
        @Override public void toggleStatsCard(@NonNull View anchor) {}
        @Override public void toggleWeatherCard(@NonNull View anchor) {}
    }
}
