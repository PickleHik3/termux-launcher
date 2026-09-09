package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.place.PlaceLayout;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;

/** The split keyboard type as the host applies it: the parted layout and the gap on the view. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxInAppKeyboardSplitTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final float EPS = 1e-3f;

    private Activity mActivity;
    private FakeHost mHost;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxInAppKeyboard mController;

    @Before
    public void setUp() throws Exception {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost(mActivity);
        SharedPreferences sharedPreferences = mActivity.getSharedPreferences(
            "split-controller-" + System.nanoTime(), Context.MODE_PRIVATE);
        mPreferences = new TermuxAppSharedPreferences(mActivity, sharedPreferences, null);
        mPreferences.setInAppKeyboardEnabled(true);
        mController = newController();
        mController.onCreate(null);
    }

    @After
    public void tearDown() {
        if (mController != null)
            mController.onDestroy();
        mActivity.finish();
    }

    @Test
    public void theSplitTypePartsEveryRowAndHandsTheViewTheSameGap() {
        KeyboardData docked = viewKeyboard();

        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);

        KeyboardData split = viewKeyboard();
        float gap = mPreferences.getInAppKeyboardSplitGapFraction() * docked.keysWidth;
        assertEquals(gap, keyboardView().getSplitGapUnits(), EPS);
        assertEquals(docked.keysWidth + gap, split.keysWidth, EPS);
        assertEquals(docked.rows.size(), split.rows.size());
        for (int i = 0; i < split.rows.size(); i++)
            assertEquals("row " + i + " parts",
                docked.rows.get(i).keysWidth + gap, split.rows.get(i).keysWidth, EPS);
    }

    @Test
    public void goingBackToDockedRestoresTheLayoutAsItWasComposed() {
        KeyboardData docked = viewKeyboard();

        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.DOCKED);

        assertSame(docked, viewKeyboard());
        assertEquals(0f, keyboardView().getSplitGapUnits(), EPS);
    }

    @Test
    public void theFloatingTypeIsNotParted() {
        KeyboardData docked = viewKeyboard();

        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.FLOATING);

        assertSame(docked, viewKeyboard());
        assertEquals(0f, keyboardView().getSplitGapUnits(), EPS);
    }

    @Test
    public void theGapSliderMovesTheParting() {
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);
        float narrow = keyboardView().getSplitGapUnits();

        mPreferences.setInAppKeyboardSplitGapFraction(0.4f);
        mController.onPreferencesReloaded();

        float wide = keyboardView().getSplitGapUnits();
        assertTrue("a wider gap fraction parts the rows further: " + narrow + " -> " + wide,
            wide > narrow);
        assertEquals(0.4f * (viewKeyboard().keysWidth - wide), wide, EPS);
    }

    @Test
    public void aGapOfNothingLeavesTheKeyboardDocked() {
        mPreferences.setInAppKeyboardSplitGapFraction(0f);
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);

        assertEquals(0f, keyboardView().getSplitGapUnits(), EPS);
    }

    @Test
    public void rotatingResolvesTheOrientationsOwnGap() {
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);
        float portrait = keyboardView().getSplitGapUnits();

        RuntimeEnvironment.setQualifiers("+land");
        mController.onConfigurationChanged(
            mActivity.getResources().getConfiguration());

        float landscape = keyboardView().getSplitGapUnits();
        assertTrue("landscape parts further than portrait by default: "
            + portrait + " -> " + landscape, landscape > portrait);
        assertEquals(mPreferences.getInAppKeyboardSplitGapFraction()
            * (viewKeyboard().keysWidth - landscape), landscape, EPS);
    }

    private Keyboard2View keyboardView() {
        assertTrue("the keyboard is on screen", mHost.attachedView instanceof Keyboard2View);
        return (Keyboard2View) mHost.attachedView;
    }

    private KeyboardData viewKeyboard() {
        return ReflectionHelpers.getField(keyboardView(), "_keyboard");
    }

    private TermuxInAppKeyboard newController() throws Exception {
        File layoutFile = new File(temporaryFolder.newFolder(), "layout.xml");
        return new TermuxInAppKeyboard(mHost, mPreferences, new DirectExecutorService(),
            layoutFile, (diagnostic, userMessage) -> { });
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        @Override public void execute(Runnable command) {
            if (!shutdown)
                command.run();
        }
    }

    private static final class FakeHost implements InAppKeyboardHost {
        final FrameLayout container;
        final TerminalView terminalView;
        View attachedView;

        FakeHost(Activity activity) {
            container = new FrameLayout(activity);
            terminalView = new TerminalView(activity, null);
            container.setVisibility(View.GONE);
        }

        @Override public View getKeyboardContainer() { return container; }
        @Override public void attachKeyboardView(View keyboardView) {
            container.removeAllViews();
            container.addView(keyboardView);
            attachedView = keyboardView;
        }
        @Override public void detachKeyboardView() {
            container.removeAllViews();
            attachedView = null;
        }
        @Override public void invalidateKeyboardMeasurement() { }
        @Override public void requestAccessoryGeometrySync() { }
        @Override public void setKeyboardHeightAdjustmentVisible(boolean visible) { }
        @Override public TerminalView getTerminalView() { return terminalView; }
        @Override public TerminalSession getCurrentSession() { return null; }
        @Override public void restoreLegacySoftKeyboardState() { }
        @Override public void runOnMain(Runnable runnable) { runnable.run(); }
        @Override public void paste() { }
        @Override public void copySelection() { }
        @Override public void requestTextLayout() { }
        @Override public void requestNumericLayout() { }
        @Override public void requestGreekMathLayout() { }
        @Override public void requestForwardLayout() { }
        @Override public void requestBackwardLayout() { }
        @Override public void openKeyboardSettings() { }
        @Override public void hideKeyboard() { }
        @Override public void setComposePending(boolean pending) { }
        @Override public void toggleCapsLock() { }
        @Override public void debugLog(String message) { }
    }
}
