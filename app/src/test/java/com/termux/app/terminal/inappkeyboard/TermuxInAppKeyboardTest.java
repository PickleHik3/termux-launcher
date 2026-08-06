package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Theme;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxInAppKeyboardTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Activity mActivity;
    private FakeHost mHost;
    private TermuxAppSharedPreferences mPreferences;
    private TermuxInAppKeyboard mController;
    private File mLayoutFile;

    @Before
    public void setUp() throws Exception {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost(mActivity);
        SharedPreferences sharedPreferences = mActivity.getSharedPreferences(
            "wp3-controller-" + System.nanoTime(), Context.MODE_PRIVATE);
        mPreferences = new TermuxAppSharedPreferences(mActivity, sharedPreferences, null);
        mController = newController();
    }

    @After
    public void tearDown() {
        if (mController != null)
            mController.onDestroy();
        mActivity.finish();
    }

    @Test
    public void enabledOnCreateDefaultsVisibleAndDisableRestoresLegacyOnce() {
        mPreferences.setInAppKeyboardEnabled(true);
        mActivity.getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        mController.onCreate(null);

        assertTrue(mController.isEnabled());
        assertTrue(mController.isVisible());
        assertEquals(TermuxInAppKeyboard.ShowReason.FIRST_ENABLE,
            mController.getLastShowReason());
        assertEquals(View.VISIBLE, mHost.container.getVisibility());
        assertEquals(1, mHost.attachCount);
        assertTrue(KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity));
        int suppressedMode = mActivity.getWindow().getAttributes().softInputMode;
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            suppressedMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
            suppressedMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE);

        mPreferences.setInAppKeyboardEnabled(false);
        mController.onPreferencesReloaded();
        mController.onPreferencesReloaded();

        assertFalse(mController.isEnabled());
        assertFalse(mController.isVisible());
        assertEquals(View.GONE, mHost.container.getVisibility());
        assertEquals(TermuxInAppKeyboard.HideReason.PREFERENCE_DISABLED,
            mController.getLastHideReason());
        assertEquals(1, mHost.restoreLegacyCount);
        assertFalse(KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity));
    }

    @Test
    public void toggleShowAndHideKeepTheirReasons() {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);

        mController.toggle(TermuxInAppKeyboard.ToggleReason.KEYBOARD_ACTION);
        assertFalse(mController.isVisible());
        assertEquals(TermuxInAppKeyboard.ToggleReason.KEYBOARD_ACTION,
            mController.getLastToggleReason());
        assertEquals(TermuxInAppKeyboard.HideReason.KEYBOARD_ACTION,
            mController.getLastHideReason());

        mController.show(TermuxInAppKeyboard.ShowReason.TERMINAL_TAP);
        assertTrue(mController.isVisible());
        assertEquals(TermuxInAppKeyboard.ShowReason.TERMINAL_TAP,
            mController.getLastShowReason());

        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        assertFalse(mController.isVisible());
        assertEquals(TermuxInAppKeyboard.HideReason.USER_EVENT,
            mController.getLastHideReason());
    }

    @Test
    public void stopAndStartPreserveVisibility() {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);

        mController.onStop();
        mController.onStart();
        mController.onResume();

        assertTrue(mController.isVisible());
        assertEquals(View.VISIBLE, mHost.container.getVisibility());

        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        mController.onStop();
        mController.onStart();
        mController.onResume();

        assertFalse(mController.isVisible());
        assertEquals(View.GONE, mHost.container.getVisibility());
    }

    @Test
    public void externalTextInputTemporarilyYieldsToSystemImeThenRestoresKeyboard() {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        int geometrySyncs = mHost.geometrySyncCount;

        mController.beginExternalTextInput();

        assertFalse(mController.isVisible());
        assertFalse(mController.isSystemImeSuppressed());
        assertEquals(View.GONE, mHost.container.getVisibility());
        assertFalse(KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity));
        assertTrue(mHost.geometrySyncCount > geometrySyncs);
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mActivity.getWindow().getAttributes().softInputMode
                & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST);

        mController.endExternalTextInput();

        assertTrue(mController.isVisible());
        assertTrue(mController.isSystemImeSuppressed());
        assertEquals(View.VISIBLE, mHost.container.getVisibility());
        assertTrue(KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity));
    }

    @Test
    public void savedHiddenVisibilityIsRestoredAcrossRecreation() throws Exception {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        Bundle state = new Bundle();
        mController.onSaveInstanceState(state);
        mController.onDestroy();

        mHost = new FakeHost(mActivity);
        mController = newController();
        mController.onCreate(state);

        assertTrue(mController.isEnabled());
        assertFalse(mController.isVisible());
        assertEquals(View.GONE, mHost.container.getVisibility());
        assertEquals(0, mHost.attachCount);
    }

    @Test
    public void preferenceReloadCanEnableThenDisableTheController() {
        mPreferences.setInAppKeyboardEnabled(false);
        mController.onCreate(null);
        assertFalse(mController.isEnabled());

        mPreferences.setInAppKeyboardEnabled(true);
        mController.onPreferencesReloaded();
        assertTrue(mController.isEnabled());
        assertTrue(mController.isVisible());
        assertEquals(1, mHost.attachCount);

        mPreferences.setInAppKeyboardEnabled(false);
        mController.onPreferencesReloaded();
        assertFalse(mController.isEnabled());
        assertFalse(mController.isVisible());
        assertEquals(1, mHost.restoreLegacyCount);
    }

    @Test
    public void persistedGeometryDrivesRendererMeasurementAndPreferenceReload() {
        mPreferences.setInAppKeyboardEnabled(true);
        mPreferences.setInAppKeyboardHeightScale(1.5f);
        mPreferences.setInAppKeyboardKeyMarginScale(1.5f);
        mPreferences.setInAppKeyboardKeyCornerRadiusDp(8f);
        mController.onCreate(null);

        Keyboard2View view = (Keyboard2View) mHost.attachedView;
        assertEquals(1.5f, view.getHeightScale(), 0.0001f);
        assertEquals(1.5f, view.getKeyMarginScale(), 0.0001f);
        float density = mActivity.getResources().getDisplayMetrics().density;
        assertEquals(8f * density, view.getKeyCornerRadiusOverride(), 0.0001f);
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        juloo.keyboard2.Config config = ReflectionHelpers.getField(view, "_config");
        KeyboardData keyboard = ReflectionHelpers.getField(view, "_keyboard");
        Theme.Computed computed = ReflectionHelpers.getField(view, "_tc");
        assertEquals(config.verticalKeyMarginRatio * 1.5f * computed.row_height,
            computed.vertical_margin, 0.0001f);
        assertEquals(config.horizontalKeyMarginRatio * 1.5f
                * ReflectionHelpers.<Float>getField(view, "_keyWidth"),
            computed.horizontal_margin, 0.0001f);
        assertEquals(8f * density, computed.key.border_radius, 0.0001f);
        int expectedHeight = (int) Math.ceil(
            config.rowHeightPx * 1.5f * keyboard.keysHeight
                + view.getPaddingTop() + config.marginTopPx
                + view.getPaddingBottom() + config.bottomMarginPx);
        assertEquals(expectedHeight, view.getMeasuredHeight());
        float scaledLabelSize = ReflectionHelpers.getField(view, "_mainLabelSize");
        view.setHeightScale(1.0f);
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        assertTrue(scaledLabelSize
            > ReflectionHelpers.<Float>getField(view, "_mainLabelSize"));
        view.setHeightScale(1.5f);
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST));
        // The fraction is a hard ceiling on available height; the height scale sizes rows under it
        // but cannot raise it.
        int fractionCap = (int) Math.ceil(500f * config.maxKeyboardHeightFraction);
        assertTrue(Math.abs(fractionCap - view.getMeasuredHeight()) <= 1);

        int geometrySyncs = mHost.geometrySyncCount;
        mPreferences.setInAppKeyboardHeightScale(0.75f);
        mPreferences.setInAppKeyboardKeyMarginScale(0.25f);
        mPreferences.setInAppKeyboardKeyCornerRadiusDp(12f);
        mController.onPreferencesReloaded();

        assertEquals(0.75f, view.getHeightScale(), 0.0001f);
        assertEquals(0.25f, view.getKeyMarginScale(), 0.0001f);
        assertEquals(12f * density, view.getKeyCornerRadiusOverride(), 0.0001f);
        assertTrue(mHost.geometrySyncCount > geometrySyncs);
    }

    @Test
    public void geometryAdjustmentConfirmPersistsAndCancelRestoresAllPreviews() {
        mPreferences.setInAppKeyboardEnabled(true);
        mPreferences.setInAppKeyboardHeightScale(1.0f);
        mPreferences.setInAppKeyboardKeyMarginScale(0.75f);
        mPreferences.setInAppKeyboardKeyCornerRadiusDp(8f);
        mController.onCreate(null);
        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);

        mController.beginHeightAdjustment();
        assertTrue(mController.isVisible());
        assertTrue(mController.isHeightAdjusting());
        assertTrue(mHost.heightAdjustmentVisible);
        int invalidationsBeforePreviews = mHost.measurementInvalidationCount;

        mController.previewHeightScale(3.0f);
        mController.previewKeyMarginScale(9.0f);
        mController.previewKeyCornerRadiusDp(30f);
        assertEquals(1.6f, mController.getHeightScale(), 0.0001f);
        assertEquals(8f, mController.getKeyMarginScale(), 0.0001f);
        assertEquals(24f, mController.getKeyCornerRadiusDp(), 0.0001f);
        assertEquals(invalidationsBeforePreviews + 3, mHost.measurementInvalidationCount);
        assertEquals(1.0f, mPreferences.getInAppKeyboardHeightScale(), 0.0001f);
        assertEquals(0.75f, mPreferences.getInAppKeyboardKeyMarginScale(), 0.0001f);
        assertEquals(8f, mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);
        mController.confirmHeightAdjustment();
        assertFalse(mController.isHeightAdjusting());
        assertFalse(mHost.heightAdjustmentVisible);
        assertEquals(1.6f, mPreferences.getInAppKeyboardHeightScale(), 0.0001f);
        assertEquals(8f, mPreferences.getInAppKeyboardKeyMarginScale(), 0.0001f);
        assertEquals(24f, mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);

        mController.beginHeightAdjustment();
        mController.previewHeightScale(0.6f);
        mController.previewKeyMarginScale(0.2f);
        mController.previewKeyCornerRadiusDp(2f);
        mController.cancelHeightAdjustment();
        assertFalse(mController.isHeightAdjusting());
        assertEquals(invalidationsBeforePreviews + 9, mHost.measurementInvalidationCount);
        assertEquals(1.6f, mController.getHeightScale(), 0.0001f);
        assertEquals(8f, mController.getKeyMarginScale(), 0.0001f);
        assertEquals(24f, mController.getKeyCornerRadiusDp(), 0.0001f);
        assertEquals(1.6f, mPreferences.getInAppKeyboardHeightScale(), 0.0001f);
        assertEquals(8f, mPreferences.getInAppKeyboardKeyMarginScale(), 0.0001f);
        assertEquals(24f, mPreferences.getInAppKeyboardKeyCornerRadiusDp(), 0.0001f);

        assertEquals(1.6f, TermuxInAppKeyboard.calculateHeightScaleForDrag(
            1.0f, -1000f, 400f), 0.0001f);
        assertEquals(0.5f, TermuxInAppKeyboard.calculateHeightScaleForDrag(
            1.0f, 1000f, 400f), 0.0001f);
    }

    @Test
    public void disableDetachesRendererAndReenableCreatesFreshView() {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        View firstView = mHost.attachedView;

        mPreferences.setInAppKeyboardEnabled(false);
        mController.onPreferencesReloaded();

        assertEquals(1, mHost.detachCount);
        assertEquals(0, mHost.container.getChildCount());

        mPreferences.setInAppKeyboardEnabled(true);
        mController.onPreferencesReloaded();

        assertEquals(2, mHost.attachCount);
        assertNotSame(firstView, mHost.attachedView);
    }

    @Test
    public void selectedLayoutSurvivesConfigurationChangeAndInstanceRecreation() throws Exception {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        mController.requestNumericLayout();
        assertEquals(TermuxInAppKeyboard.LAYOUT_NUMERIC, mController.getSelectedLayoutId());

        View numericView = mHost.attachedView;
        mController.onConfigurationChanged(new Configuration());
        assertEquals(TermuxInAppKeyboard.LAYOUT_NUMERIC, mController.getSelectedLayoutId());
        assertNotSame(numericView, mHost.attachedView);
        assertEquals(KeyEvent.KEYCODE_ESCAPE,
            centerKey((Keyboard2View) mHost.attachedView, 0, 0).getKeyevent());

        mController.requestGreekMathLayout();
        Bundle state = new Bundle();
        mController.onSaveInstanceState(state);
        mController.onDestroy();

        mHost = new FakeHost(mActivity);
        mController = newController();
        mController.onCreate(state);

        assertEquals(TermuxInAppKeyboard.LAYOUT_GREEK_MATH,
            mController.getSelectedLayoutId());
        assertEquals('θ', centerKey((Keyboard2View) mHost.attachedView, 0, 0).getChar());
        mController.requestForwardLayout();
        assertEquals(TermuxInAppKeyboard.LAYOUT_MAIN, mController.getSelectedLayoutId());
        mController.requestBackwardLayout();
        assertEquals(TermuxInAppKeyboard.LAYOUT_GREEK_MATH,
            mController.getSelectedLayoutId());
    }

    @Test
    public void asyncCustomLayoutInvalidatesFallbackLayoutMeasurement() throws Exception {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        mController.onStart();
        int invalidationsBeforeCustomLayout = mHost.measurementInvalidationCount;
        int geometrySyncsBeforeCustomLayout = mHost.geometrySyncCount;

        String twoRowLayout = "<keyboard bottom_row='false'>"
            + "<row><key c='1'/></row>"
            + "<row><key c='2'/></row>"
            + "</keyboard>";
        Files.write(mLayoutFile.toPath(), twoRowLayout.getBytes(StandardCharsets.UTF_8));
        mController.onStart();

        KeyboardData applied = ReflectionHelpers.getField(
            (Keyboard2View) mHost.attachedView, "_keyboard");
        assertEquals(2f, applied.keysHeight, 0.0001f);
        assertTrue(mHost.measurementInvalidationCount > invalidationsBeforeCustomLayout);
        assertTrue(mHost.geometrySyncCount > geometrySyncsBeforeCustomLayout);
    }

    @Test
    public void refreshMaterialPaletteIsNoOpWhileDisabledHiddenOrDestroyed() {
        mPreferences.setInAppKeyboardEnabled(false);
        mController.onCreate(null);
        applyMaterialRoles(true);

        assertFalse("a disabled keyboard must not resolve or repaint anything",
            mController.refreshMaterialPalette());

        mPreferences.setInAppKeyboardEnabled(true);
        mController.onPreferencesReloaded();
        Keyboard2View view = (Keyboard2View) mHost.attachedView;
        Theme shownTheme = ReflectionHelpers.getField(view, "_theme");

        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        applyMaterialRoles(false);
        assertFalse("a hidden keyboard is not repainted in place",
            mController.refreshMaterialPalette());
        assertSame(shownTheme, ReflectionHelpers.<Theme>getField(view, "_theme"));

        // Hiding keeps the renderer alive, so the missed change lands on the way back on screen.
        mController.show(TermuxInAppKeyboard.ShowReason.TERMINAL_TAP);
        assertNotSame(shownTheme, ReflectionHelpers.<Theme>getField(view, "_theme"));

        mController.onDestroy();
        assertFalse("a destroyed keyboard must not touch its detached host",
            mController.refreshMaterialPalette());
    }

    @Test
    public void refreshMaterialPaletteRebuildsOnlyWhenMaterialRolesMove() {
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        Keyboard2View view = (Keyboard2View) mHost.attachedView;
        Theme first = ReflectionHelpers.getField(view, "_theme");

        assertFalse("unmoved Material roles must not rebuild the palette",
            mController.refreshMaterialPalette());
        assertSame(first, ReflectionHelpers.<Theme>getField(view, "_theme"));

        applyMaterialRoles(true);
        assertTrue(mController.refreshMaterialPalette());
        Theme moved = ReflectionHelpers.getField(view, "_theme");
        assertNotSame(first, moved);
        assertNotEquals(first.colorKey, moved.colorKey);

        assertFalse("a repeat wallpaper event with the same palette is a no-op",
            mController.refreshMaterialPalette());
        assertSame(moved, ReflectionHelpers.<Theme>getField(view, "_theme"));
    }

    @Test
    public void refreshMaterialPaletteMovesDynamicSwatchesAndHoldsPinnedOnes() throws Exception {
        // Slot 5 is pinned; slot 3 stays dynamic. One key paints its background from the pinned
        // slot and its primary label from the dynamic one.
        JSONArray swatches = new JSONArray();
        for (int i = 0; i < 24; i++)
            swatches.put(i == 5 ? (Object) Integer.valueOf(0xFF123456) : JSONObject.NULL);
        mPreferences.setInAppKeyboardColorScheme(
            schemeJson(swatches, false, keyAssignment(5, 3)));
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        Keyboard2View view = (Keyboard2View) mHost.attachedView;

        Keyboard2View.KeyColorOverride before = keyOverride(view, 1, 3);
        assertEquals(Integer.valueOf(0xFF123456), before.keyBackground);

        applyMaterialRoles(true);
        assertTrue(mController.refreshMaterialPalette());

        Keyboard2View.KeyColorOverride after = keyOverride(view, 1, 3);
        assertEquals("a pinned swatch never follows the Material theme",
            Integer.valueOf(0xFF123456), after.keyBackground);
        assertNotEquals("a dynamic swatch re-resolves against the new Material roles",
            before.primaryLabel, after.primaryLabel);
    }

    @Test
    public void refreshMaterialPaletteKeepsAnImportedPaletteExactly() throws Exception {
        JSONArray swatches = new JSONArray();
        for (int i = 0; i < 24; i++)
            swatches.put(i < 16 ? (Object) Integer.valueOf(0xFF101010 + i) : JSONObject.NULL);
        mPreferences.setInAppKeyboardColorScheme(schemeJson(swatches, true, null));
        mPreferences.setInAppKeyboardTheme("custom");
        mPreferences.setInAppKeyboardEnabled(true);
        mController.onCreate(null);
        Keyboard2View view = (Keyboard2View) mHost.attachedView;

        Theme before = ReflectionHelpers.getField(view, "_theme");
        assertEquals(0x101011, before.colorKey & 0x00FFFFFF);
        assertEquals(0xFF101015, before.labelColor);

        applyMaterialRoles(true);
        assertTrue(mController.refreshMaterialPalette());

        Theme after = ReflectionHelpers.getField(view, "_theme");
        assertNotSame(before, after);
        assertEquals("an imported key color survives a wallpaper change",
            before.colorKey, after.colorKey);
        assertEquals("an imported label color survives a wallpaper change",
            before.labelColor, after.labelColor);
        assertEquals(before.colorKeyActivated, after.colorKeyActivated);
    }

    /** Moves every Material source role the keyboard palette is built from. */
    private void applyMaterialRoles(boolean dark) {
        mActivity.getTheme().applyStyle(dark
            ? com.google.android.material.R.style.ThemeOverlay_Material3_Dark
            : com.google.android.material.R.style.ThemeOverlay_Material3_Light, true);
    }

    private static JSONObject keyAssignment(int backgroundSlot, int primarySlot)
        throws JSONException {
        JSONObject assignment = new JSONObject();
        assignment.put("bg", backgroundSlot);
        assignment.put("primary", primarySlot);
        return assignment;
    }

    private static String schemeJson(JSONArray swatches, boolean imported,
                                     JSONObject keyOneThree) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", InAppKeyboardColorScheme.SCHEMA_VERSION);
        root.put("base16Palette", imported);
        root.put("importedThemeId", "");
        root.put("swatches", swatches);
        JSONObject keys = new JSONObject();
        if (keyOneThree != null)
            keys.put("1:3", keyOneThree);
        root.put("keys", keys);
        return root.toString();
    }

    private static Keyboard2View.KeyColorOverride keyOverride(Keyboard2View view, int row,
                                                              int column) {
        SparseArray<Keyboard2View.KeyColorOverride> overrides =
            ReflectionHelpers.getField(view, "_keyColorOverrides");
        return overrides.get((row << 16) | column);
    }

    private static KeyValue centerKey(Keyboard2View view, int row, int column) {
        KeyboardData keyboard = ReflectionHelpers.getField(view, "_keyboard");
        return keyboard.rows.get(row).keys.get(column).keys[0];
    }

    private TermuxInAppKeyboard newController() throws Exception {
        mLayoutFile = new File(temporaryFolder.newFolder(), "layout.xml");
        return new TermuxInAppKeyboard(mHost, mPreferences, new DirectExecutorService(),
            mLayoutFile, (diagnostic, userMessage) -> { });
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
        int attachCount;
        int detachCount;
        int geometrySyncCount;
        int measurementInvalidationCount;
        int restoreLegacyCount;
        boolean heightAdjustmentVisible;
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
            attachCount++;
        }
        @Override public void detachKeyboardView() {
            container.removeAllViews();
            attachedView = null;
            detachCount++;
        }
        @Override public void invalidateKeyboardMeasurement() {
            measurementInvalidationCount++;
        }
        @Override public void requestAccessoryGeometrySync() { geometrySyncCount++; }
        @Override public void setKeyboardHeightAdjustmentVisible(boolean visible) {
            heightAdjustmentVisible = visible;
        }
        @Override public TerminalView getTerminalView() { return terminalView; }
        @Override public TerminalSession getCurrentSession() { return null; }
        @Override public void restoreLegacySoftKeyboardState() { restoreLegacyCount++; }
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
