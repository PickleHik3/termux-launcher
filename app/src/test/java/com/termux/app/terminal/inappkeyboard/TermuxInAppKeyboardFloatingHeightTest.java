package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.place.PlaceLayout;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
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

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import juloo.keyboard2.Keyboard2View;

/**
 * The extra row height a floating keyboard carries. It multiplies the height the user already set
 * for the keyboard rather than replacing it, and it is in force only while the keyboard is
 * floating — the same keyboard docked is the height it always was.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxInAppKeyboardFloatingHeightTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final float EPS = 1e-4f;

    private Activity mActivity;
    private FakeHost mHost;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxInAppKeyboard mController;

    @Before
    public void setUp() throws Exception {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost(mActivity);
        SharedPreferences sharedPreferences = mActivity.getSharedPreferences(
            "floating-height-" + System.nanoTime(), Context.MODE_PRIVATE);
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
    public void aDockedKeyboardIsTheHeightTheUserSetAndNothingElse() {
        mPreferences.setInAppKeyboardFloatingHeightScale(1.4f);
        mController.onPreferencesReloaded();

        assertEquals(1.4f, mController.getFloatingHeightScale(), EPS);
        assertEquals("stored, but not applied to a docked keyboard",
            mController.getHeightScale(), keyboardView().getHeightScale(), EPS);
    }

    @Test
    public void floatingMultipliesTheHeightAndDockingAgainTakesItBackOff() {
        float docked = keyboardView().getHeightScale();
        mController.setFloatingHeightScale(1.25f);
        assertEquals("nothing until the keyboard actually floats",
            docked, keyboardView().getHeightScale(), EPS);

        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.FLOATING);
        assertEquals(docked * 1.25f, keyboardView().getHeightScale(), EPS);

        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.DOCKED);
        assertEquals(docked, keyboardView().getHeightScale(), EPS);

        // Split is the docked keyboard parted, so it is sized like one too.
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.SPLIT);
        assertEquals(docked, keyboardView().getHeightScale(), EPS);
    }

    @Test
    public void aGripDragPreviewLandsOnTheFloatingKeyboardWithoutBeingStored() {
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.FLOATING);
        float docked = mController.getHeightScale();

        mController.previewFloatingHeightScale(0.8f);
        assertEquals(docked * 0.8f, keyboardView().getHeightScale(), EPS);
        assertEquals("a preview writes nothing",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
            mPreferences.getInAppKeyboardFloatingHeightScale(), EPS);
    }

    @Test
    public void theMultiplierIsHeldInsideItsRange() {
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.FLOATING);
        float docked = mController.getHeightScale();

        mController.setFloatingHeightScale(9f);
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
            mController.getFloatingHeightScale(), EPS);
        assertEquals(docked * TERMUX_APP.MAX_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
            keyboardView().getHeightScale(), EPS);

        mController.setFloatingHeightScale(0f);
        assertEquals(TERMUX_APP.MIN_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE,
            mController.getFloatingHeightScale(), EPS);
    }

    @Test
    public void rotatingPicksUpTheOtherOrientationsMultiplier() {
        mPreferences.setInAppKeyboardFloatingHeightScale(1.5f);
        mController.onPreferencesReloaded();
        mController.onKeyboardFormChanged(PlaceLayout.KeyboardForm.FLOATING);
        assertEquals(1.5f, mController.getFloatingHeightScale(), EPS);

        RuntimeEnvironment.setQualifiers("+land");
        mController.onConfigurationChanged(mActivity.getResources().getConfiguration());

        assertEquals("landscape has its own, which nobody has moved",
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE_LANDSCAPE,
            mController.getFloatingHeightScale(), EPS);
        // The view was rebuilt by the rotation and still knows it is floating.
        assertEquals(mController.getHeightScale()
                * TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_FLOATING_HEIGHT_SCALE_LANDSCAPE,
            keyboardView().getHeightScale(), EPS);
    }

    private Keyboard2View keyboardView() {
        assertTrue("the keyboard is on screen", mHost.attachedView instanceof Keyboard2View);
        return (Keyboard2View) mHost.attachedView;
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
