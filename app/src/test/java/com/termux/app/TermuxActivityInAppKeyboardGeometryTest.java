package com.termux.app;

import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.chrome.ChromePolicy;
import com.termux.app.chrome.ChromeRenderer;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.termux.R;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.inappkeyboard.KeyboardGeometryChoreographer;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import juloo.keyboard2.Keyboard2View;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityInAppKeyboardGeometryTest {

    private TermuxActivity mActivity;
    private TermuxInAppKeyboard mController;

    @Test
    public void surfaceEditorKeyboardSlidersNormalizeFullStoredRanges() {
        assertEquals(0, SurfaceEditorController.keyboardEditorProgress(0.5f, 0.5f, 1.6f));
        assertEquals(100, SurfaceEditorController.keyboardEditorProgress(1.6f, 0.5f, 1.6f));
        assertEquals(0.5f, SurfaceEditorController.keyboardEditorValue(0, 0.5f, 1.6f), 0.0001f);
        assertEquals(1.6f, SurfaceEditorController.keyboardEditorValue(100, 0.5f, 1.6f), 0.0001f);

        assertEquals(0, SurfaceEditorController.keyboardEditorProgress(0f, 0f, 8f));
        assertEquals(100, SurfaceEditorController.keyboardEditorProgress(8f, 0f, 8f));
        assertEquals(4f, SurfaceEditorController.keyboardEditorValue(50, 0f, 8f), 0.0001f);
    }

    @After
    public void tearDown() {
        if (mController != null)
            mController.onDestroy();
        if (mActivity != null)
            mActivity.finish();
    }

    @Test
    public void layoutContainsHiddenKeyboardHostsAndToolbarChain() {
        mActivity = Robolectric.buildActivity(TermuxActivity.class).get();
        mActivity.setContentView(R.layout.activity_termux);

        View keyboardContainer = mActivity.findViewById(R.id.inapp_keyboard_container);
        View suggestionHost = mActivity.findViewById(R.id.inapp_keyboard_suggestion_host);
        View heightAdjustControls = mActivity.findViewById(
            R.id.inapp_keyboard_height_adjust_controls);
        View spacingSlider = mActivity.findViewById(
            R.id.inapp_keyboard_key_spacing_slider);
        View radiusSlider = mActivity.findViewById(
            R.id.inapp_keyboard_key_corner_radius_slider);
        FrameLayout keyboardHost = mActivity.findViewById(R.id.inapp_keyboard_view_host);
        View toolbarPager = mActivity.findViewById(R.id.terminal_toolbar_view_pager);
        View divider = mActivity.findViewById(R.id.extrakeys_divider);

        assertNotNull(keyboardContainer);
        assertNotNull(suggestionHost);
        assertNotNull(keyboardHost);
        assertNotNull(spacingSlider);
        assertNotNull(radiusSlider);
        assertEquals(View.GONE, keyboardContainer.getVisibility());
        assertEquals(View.GONE, suggestionHost.getVisibility());
        assertEquals(View.GONE, heightAdjustControls.getVisibility());

        RelativeLayout.LayoutParams toolbarParams =
            (RelativeLayout.LayoutParams) toolbarPager.getLayoutParams();
        RelativeLayout.LayoutParams dividerParams =
            (RelativeLayout.LayoutParams) divider.getLayoutParams();
        assertEquals(R.id.inapp_keyboard_container,
            toolbarParams.getRules()[RelativeLayout.ABOVE]);
        assertEquals(0, toolbarParams.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM]);
        // The keyboard container is GONE whenever the embedded keyboard is hidden. Without the
        // parent-bottom fallback, RelativeLayout drops the ABOVE anchor entirely and the whole
        // toolbar stack collapses to the top of the dock.
        assertTrue(toolbarParams.alignWithParent);
        assertEquals(R.id.terminal_toolbar_view_pager,
            dividerParams.getRules()[RelativeLayout.ALIGN_TOP]);
        int[] toolbarOnlyLayerIds = {
            R.id.accessory_surface_host,
            R.id.apps_bar_az_fx_underlay,
            R.id.apps_bar_az_fx_overlay
        };
        for (int layerId : toolbarOnlyLayerIds) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mActivity.findViewById(layerId).getLayoutParams();
            assertEquals(R.id.inapp_keyboard_container,
                params.getRules()[RelativeLayout.ABOVE]);
            assertTrue(params.alignWithParent);
        }
        RelativeLayout.LayoutParams labelOverlayParams = (RelativeLayout.LayoutParams)
            mActivity.findViewById(R.id.apps_bar_az_label_overlay).getLayoutParams();
        assertEquals(R.id.accessory_stack_container,
            labelOverlayParams.getRules()[RelativeLayout.ABOVE]);
        assertEquals(0, labelOverlayParams.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM]);
        assertTrue(labelOverlayParams.alignWithParent);
    }

    @Test
    public void toolbarKeyboardMatrixAndCombinedHeightPreserveToolbarBaseline() {
        assertFalse(TermuxActivity.shouldShowAccessoryStack(false, false));
        assertTrue(TermuxActivity.shouldShowAccessoryStack(true, false));
        assertTrue(TermuxActivity.shouldShowAccessoryStack(false, true));
        assertTrue(TermuxActivity.shouldShowAccessoryStack(true, true));

        assertEquals(147, TermuxActivity.computeAccessoryStackHeight(140, 7, 0));
        assertEquals(420, TermuxActivity.computeAccessoryStackHeight(0, 0, 420));
        assertEquals(560, TermuxActivity.computeAccessoryStackHeight(140, 0, 420));
        assertEquals(0, TermuxActivity.computeAccessoryStackHeight(-1, -2, -3));

        assertTrue(TermuxActivity.shouldRequestTerminalResize(true, false, false, true));
        assertFalse(TermuxActivity.shouldRequestTerminalResize(true, false, false, false));
        assertFalse(TermuxActivity.shouldRequestTerminalResize(false, true, true, true));
    }

    @Test
    public void gestureNavigationSurfaceFollowsEdgeToEdgeSurfacesOnly() {
        // Edge-to-edge keyboard or dock continues under the gesture pill.
        assertTrue(TermuxActivity.shouldShowDecorNavBarSurface(
            false, true, 72, false, false, false));
        assertTrue(TermuxActivity.shouldShowDecorNavBarSurface(
            true, false, 72, false, false, false));
        assertFalse(TermuxActivity.shouldShowDecorNavBarSurface(
            false, true, 0, false, false, false));
        assertFalse(TermuxActivity.shouldShowDecorNavBarSurface(
            false, true, 72, true, false, false));
        // A floating capsule keyboard leaves the pill inset showing wallpaper.
        assertFalse(TermuxActivity.shouldShowDecorNavBarSurface(
            false, true, 72, false, true, true));
        // The keyboard's capsule decision is decoupled from the dock style: an edge-to-edge
        // keyboard under the Rounded dock still owns the gesture-navigation surface.
        assertTrue(TermuxActivity.shouldShowDecorNavBarSurface(
            false, true, 72, false, true, false));
        // The Rounded dock itself always floats — no continuation surface without a keyboard.
        assertFalse(TermuxActivity.shouldShowDecorNavBarSurface(
            true, false, 72, false, true, false));
    }

    @Test
    public void unifiedKeyboardGlassIsExclusiveToDefaultDock() {
        assertTrue(ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(
            true, true, false, true));
        assertFalse(ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(
            true, true, true, true));
        assertFalse(ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(
            true, true, false, false));
        assertFalse(ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(
            false, true, false, true));
        assertFalse(ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(
            true, false, false, true));
    }

    @Test
    public void matchAllSurfacesOutranksAnEditedKeyboardBackground() {
        // An edited scheme or an opacity genuinely differing from the shared material owns the
        // keyboard surface on its own...
        assertTrue(ChromePolicy.hasInAppKeyboardBackgroundOverride(false, 0xFF203040, 46, 46));
        assertTrue(ChromePolicy.hasInAppKeyboardBackgroundOverride(false, null, 60, 46));
        // ...a detached row holding the shared material's own number repaints nothing — the
        // unified dock/keyboard/nav sheet must survive it whatever the compile-time default says
        // (comparing against a constant is how flipping the default once severed the sheet at the
        // keyboard's bottom edge on every upgraded install)...
        assertFalse(ChromePolicy.hasInAppKeyboardBackgroundOverride(false, null, 46, 46));
        assertFalse(ChromePolicy.hasInAppKeyboardBackgroundOverride(false, null, 100, 100));
        // ...and not while surfaces are normalized, which is what left the keyboard lighter than
        // every other surface until the keyboard section was reset by hand.
        assertFalse(ChromePolicy.hasInAppKeyboardBackgroundOverride(true, 0xFF203040, 60, 46));
    }

    @Test
    public void blurredUnifiedKeyboardRevealWaitsOnlyForDestinationBackdrop() {
        assertTrue(KeyboardGeometryChoreographer.shouldDeferReveal(
            true, true, true, false));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(
            true, true, true, true));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(
            false, true, true, false));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(
            true, false, true, false));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(
            true, true, false, false));
    }

    @Test
    public void controllerIsCreatedLazilyWhenPreferenceBecomesEnabled() {
        TermuxAppSharedPreferences preferences = prepareActivity(false);

        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(Bundle.class, null));
        assertNull(ReflectionHelpers.getField(mActivity, "mInAppKeyboard"));

        preferences.setInAppKeyboardEnabled(true);
        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(Bundle.class, null));

        mController = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        assertNotNull(mController);
        assertTrue(mController.isEnabled());
    }

    @Test
    public void adjustIntentEnablesKeyboardShowsOverlayAndIsConsumed() {
        TermuxAppSharedPreferences preferences = prepareActivity(false);
        Intent intent = new Intent(mActivity, TermuxActivity.class)
            .putExtra(TermuxActivity.EXTRA_IN_APP_KEYBOARD_HEIGHT_ADJUST, true);

        ReflectionHelpers.callInstanceMethod(mActivity,
            "handleInAppKeyboardHeightAdjustIntent",
            ReflectionHelpers.ClassParameter.from(Intent.class, intent));

        mController = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        assertNotNull(mController);
        assertTrue(preferences.isInAppKeyboardEnabled());
        assertTrue(mController.isVisible());
        assertTrue(mController.isHeightAdjusting());
        assertEquals(View.VISIBLE, mActivity.findViewById(
            R.id.inapp_keyboard_height_adjust_controls).getVisibility());
        SeekBar spacingSlider = mActivity.findViewById(
            R.id.inapp_keyboard_key_spacing_slider);
        SeekBar radiusSlider = mActivity.findViewById(
            R.id.inapp_keyboard_key_corner_radius_slider);
        assertEquals(View.VISIBLE, spacingSlider.getVisibility());
        assertEquals(View.VISIBLE, radiusSlider.getVisibility());
        assertEquals(Math.round(preferences.getInAppKeyboardKeyMarginScale() * 100f),
            spacingSlider.getProgress());
        assertEquals(Math.round(preferences.getInAppKeyboardKeyCornerRadiusDp() * 10f),
            radiusSlider.getProgress());
        assertFalse(intent.hasExtra(TermuxActivity.EXTRA_IN_APP_KEYBOARD_HEIGHT_ADJUST));

        mController.cancelHeightAdjustment();
        assertEquals(View.GONE, mActivity.findViewById(
            R.id.inapp_keyboard_height_adjust_controls).getVisibility());
    }

    @Test
    public void controllerToggleChangesContainerAndAccessoryGeometry() {
        createActivityHostedController(null);

        View keyboardContainer = mActivity.findViewById(R.id.inapp_keyboard_container);
        View accessoryContainer = mActivity.findViewById(R.id.accessory_stack_container);
        assertEquals(View.VISIBLE, keyboardContainer.getVisibility());
        assertEquals(View.VISIBLE, accessoryContainer.getVisibility());
        int shownHeight = accessoryContainer.getLayoutParams().height;
        int desiredKeyboardHeight = desiredKeyboardHeightPx();
        assertTrue("four-plus-row keyboard must not reuse the old ~110px stack height",
            desiredKeyboardHeight > 110);
        assertEquals(desiredKeyboardHeight, shownHeight);

        mController.toggle(TermuxInAppKeyboard.ToggleReason.KEYBOARD_ACTION);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(View.GONE, keyboardContainer.getVisibility());
        assertEquals(View.GONE, accessoryContainer.getVisibility());
        assertEquals(0, accessoryContainer.getLayoutParams().height);

        mController.toggle(TermuxInAppKeyboard.ToggleReason.KEYBOARD_ACTION);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(View.VISIBLE, keyboardContainer.getVisibility());
        assertEquals(View.VISIBLE, accessoryContainer.getVisibility());
        assertEquals(desiredKeyboardHeightPx(),
            accessoryContainer.getLayoutParams().height);
    }

    @Test
    public void rootCappedKeyboardStaysExactInsideShorterAccessoryStackWithoutTopGap() {
        TermuxAppSharedPreferences preferences = prepareActivity(true);
        preferences.setShowTerminalToolbar(true);
        preferences.setAppLauncherExtraKeysRowEnabled(true);
        preferences.setAppLauncherAppsRowEnabled(true);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setTerminalFlushDockEnabled(false);

        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(Bundle.class, null));
        mController = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();
        mActivity.getChromeRenderer().requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        View root = mActivity.findViewById(R.id.activity_termux_root_relative_layout);
        View accessoryContainer = mActivity.findViewById(R.id.accessory_stack_container);
        View firstAccessoryContent = mActivity.findViewById(R.id.accessory_surface_host);
        View keyboardContainer = mActivity.findViewById(R.id.inapp_keyboard_container);
        int desiredKeyboardHeight = desiredKeyboardHeightPx();

        assertTrue("accessory stack must use its own exact height, not the content-root height",
            accessoryContainer.getHeight() != root.getHeight());
        assertEquals("keyboard layout must converge to the independently measured height",
            desiredKeyboardHeight, keyboardContainer.getHeight());
        assertEquals(View.VISIBLE, firstAccessoryContent.getVisibility());
        int[] accessoryLocation = new int[2];
        int[] contentLocation = new int[2];
        accessoryContainer.getLocationInWindow(accessoryLocation);
        firstAccessoryContent.getLocationInWindow(contentLocation);
        assertEquals("the first dock content must start flush at the accessory stack top",
            accessoryLocation[1], contentLocation[1]);
    }

    @Test
    public void keyboardAndDockCombinedHeightDefinesTerminalBounds() {
        TermuxAppSharedPreferences preferences = prepareActivity(true);
        preferences.setShowTerminalToolbar(true);
        preferences.setAppLauncherExtraKeysRowEnabled(true);
        preferences.setAppLauncherAppsRowEnabled(true);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setTerminalFlushDockEnabled(true);
        TerminalView terminalView = mActivity.findViewById(R.id.terminal_view);

        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(Bundle.class, null));
        mController = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        View rootRelativeLayout = mActivity.findViewById(R.id.activity_termux_root_relative_layout);
        View accessoryContainer = mActivity.findViewById(R.id.accessory_stack_container);
        View keyboardContainer = mActivity.findViewById(R.id.inapp_keyboard_container);
        View toolbarPager = mActivity.findViewById(R.id.terminal_toolbar_view_pager);
        View appsBar = mActivity.findViewById(R.id.apps_bar_viewpager);
        int dockContentHeight = toolbarPager.getLayoutParams().height
            + appsBar.getLayoutParams().height;
        int keyboardHeight = desiredKeyboardHeightPx();
        int[] location = new int[2];
        rootRelativeLayout.getLocationInWindow(location);
        int accessoryBottom = location[1] + rootRelativeLayout.getHeight();
        terminalView.getLocationInWindow(location);
        int baseTerminalHeight = accessoryBottom - dockContentHeight - keyboardHeight - location[1];
        int expectedFlushPadding = 0;
        for (int textSize = 12; textSize <= 48 && expectedFlushPadding == 0; textSize++) {
            terminalView.setTextSize(textSize);
            expectedFlushPadding = Math.max(0,
                baseTerminalHeight - terminalView.mRenderer.getFontLineSpacingAndAscent())
                % terminalView.mRenderer.getFontLineSpacing();
        }
        assertTrue("test geometry must require non-zero flush padding", expectedFlushPadding > 0);
        int actualFlushPadding = ReflectionHelpers.callInstanceMethod(mActivity,
            "resolveTerminalFlushDockPaddingPx",
            ReflectionHelpers.ClassParameter.from(int.class, dockContentHeight + keyboardHeight),
            ReflectionHelpers.ClassParameter.from(int.class, 0));

        assertEquals(expectedFlushPadding, actualFlushPadding);
        ReflectionHelpers.callInstanceMethod(mActivity, "setTerminalToolbarHeight",
            ReflectionHelpers.ClassParameter.from(boolean.class, true));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        // Flush absorption is suspended while the embedded keyboard is shown — its user-scaled
        // surface defines the boundary, so no remainder is folded into the accessory stack.
        int appliedFlushPadding = ReflectionHelpers.getField(
            mActivity, "mAppliedTerminalFlushPaddingPx");
        assertEquals(0, appliedFlushPadding);
        int combinedHeight = TermuxActivity.computeAccessoryStackHeight(
            dockContentHeight, 0, keyboardHeight);
        // The open reveal is gated on the first pre-draw pass. Robolectric never draws, and the
        // blur backdrop cannot become ready headless, so drive the gate until its frame fail-safe
        // reveals the keyboard.
        for (int i = 0; i < 4 && keyboardContainer.getVisibility() != View.VISIBLE; i++) {
            rootRelativeLayout.getViewTreeObserver().dispatchOnPreDraw();
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        }
        assertEquals(View.VISIBLE, keyboardContainer.getVisibility());
        assertEquals(View.VISIBLE, toolbarPager.getVisibility());
        assertEquals(View.VISIBLE, appsBar.getVisibility());
        assertTrue(dockContentHeight > 0);
        assertEquals(combinedHeight, accessoryContainer.getLayoutParams().height);
        assertEquals(accessoryContainer.getTop(), terminalView.getBottom());
        assertEquals(rootRelativeLayout.getHeight() - combinedHeight, terminalView.getHeight());
    }

    @Test
    public void savedHiddenStateRestoresThroughActivityHost() {
        createActivityHostedController(null);
        mController.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);

        Bundle state = new Bundle();
        mController.onSaveInstanceState(state);
        mController.onDestroy();
        mActivity.finish();
        mController = null;
        mActivity = null;

        createActivityHostedController(state);

        assertTrue(mController.isEnabled());
        assertFalse(mController.isVisible());
        assertEquals(View.GONE,
            mActivity.findViewById(R.id.inapp_keyboard_container).getVisibility());
    }

    @Test
    public void liveHeightPreviewInvalidatesCachedKeyboardAndTerminalGeometry() {
        createActivityHostedController(null);
        float initialMarginScale = mActivity.getPreferences().getInAppKeyboardKeyMarginScale();
        float initialRadiusDp = mActivity.getPreferences().getInAppKeyboardKeyCornerRadiusDp();
        layoutActivityRoot();
        ViewGroup root = mActivity.findViewById(R.id.activity_termux_root_relative_layout);
        View accessory = mActivity.findViewById(R.id.accessory_stack_container);
        View keyboard = mActivity.findViewById(R.id.inapp_keyboard_container);
        TerminalView terminal = mActivity.findViewById(R.id.terminal_view);
        int initialHeight = desiredKeyboardHeightPx();

        mController.beginHeightAdjustment();
        mController.previewHeightScale(1.4f);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        int previewHeight = desiredKeyboardHeightPx();
        assertTrue(previewHeight > initialHeight);
        assertEquals(previewHeight, accessory.getLayoutParams().height);
        assertEquals(previewHeight, keyboard.getHeight());
        assertEquals(accessory.getTop(), terminal.getBottom());
        assertEquals(root.getHeight() - previewHeight, terminal.getHeight());
        assertFalse(keyboardHeightDirty());

        Keyboard2View keyboardView = (Keyboard2View) ReflectionHelpers.getField(
            mActivity, "mAttachedInAppKeyboardView");
        mController.previewKeyMarginScale(0f);
        mController.previewKeyCornerRadiusDp(12f);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        assertEquals(0f, keyboardView.getKeyMarginScale(), 0.0001f);
        assertEquals(12f * mActivity.getResources().getDisplayMetrics().density,
            keyboardView.getKeyCornerRadiusOverride(), 0.0001f);
        assertEquals("spacing-only remeasure must preserve deterministic desired height",
            previewHeight, desiredKeyboardHeightPx());
        assertFalse(keyboardHeightDirty());

        mController.cancelHeightAdjustment();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();

        assertEquals(initialHeight, desiredKeyboardHeightPx());
        assertEquals(initialHeight, accessory.getLayoutParams().height);
        assertEquals(initialHeight, keyboard.getHeight());
        assertEquals(accessory.getTop(), terminal.getBottom());
        assertEquals(initialMarginScale, keyboardView.getKeyMarginScale(), 0.0001f);
        assertEquals(initialRadiusDp * mActivity.getResources().getDisplayMetrics().density,
            keyboardView.getKeyCornerRadiusOverride(), 0.0001f);
    }

    @Test
    public void confirmingReducedHeightPersistsBeforeFullActivityReload() {
        createActivityHostedController(null);
        layoutActivityRoot();

        mController.beginHeightAdjustment();
        mController.previewHeightScale(0.6f);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        layoutActivityRoot();
        mActivity.findViewById(R.id.inapp_keyboard_height_adjust_confirm).performClick();

        TermuxAppSharedPreferences preferences = ReflectionHelpers.getField(
            mActivity, "mPreferences");
        assertFalse(mController.isHeightAdjusting());
        assertEquals(0.6f, preferences.getInAppKeyboardHeightScale(), 0.0001f);
    }

    private void createActivityHostedController(Bundle state) {
        prepareActivity(true);

        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(Bundle.class, state));
        mController = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    private TermuxAppSharedPreferences prepareActivity(boolean keyboardEnabled) {
        mActivity = Robolectric.buildActivity(TermuxActivity.class).get();
        mActivity.setContentView(R.layout.activity_termux);

        SharedPreferences sharedPreferences = mActivity.getSharedPreferences(
            "wp4-geometry-" + System.nanoTime(), Context.MODE_PRIVATE);
        TermuxAppSharedPreferences preferences = new TermuxAppSharedPreferences(
            mActivity, sharedPreferences, null);
        preferences.setInAppKeyboardEnabled(keyboardEnabled);
        preferences.setShowTerminalToolbar(false);
        preferences.setAppLauncherAppsRowEnabled(false);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherExtraKeysRowEnabled(false);
        preferences.setTerminalFlushDockEnabled(false);

        // Production creates this dynamically through TerminalPaneController during onCreate.
        FrameLayout paneHost = mActivity.findViewById(R.id.terminal_pane_host);
        View pane = mActivity.getLayoutInflater().inflate(R.layout.view_terminal_pane, paneHost, false);
        paneHost.addView(pane);
        TerminalView terminalView = pane.findViewById(R.id.terminal_view);
        TermuxTerminalSessionActivityClient sessionClient =
            mActivity.createTermuxTerminalSessionClient();
        TermuxTerminalViewClient viewClient =
            mActivity.createTermuxTerminalViewClient(sessionClient);
        terminalView.setTerminalViewClient(viewClient);
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(mActivity);
        properties.loadTermuxPropertiesFromDisk();

        ReflectionHelpers.setField(mActivity, "mPreferences", preferences);
        ReflectionHelpers.setField(mActivity, "mProperties", properties);
        ReflectionHelpers.setField(mActivity, "mTerminalView", terminalView);
        ReflectionHelpers.setField(mActivity, "mTermuxTerminalSessionActivityClient", sessionClient);
        ReflectionHelpers.setField(mActivity, "mTermuxTerminalViewClient", viewClient);
        View toolbarPager = mActivity.findViewById(R.id.terminal_toolbar_view_pager);
        ReflectionHelpers.setField(mActivity, "mTerminalToolbarDefaultHeight",
            (float) toolbarPager.getLayoutParams().height);

        layoutActivityRoot();
        return preferences;
    }

    /** The keyboard's measurement memo, which phase 4c moved onto the choreographer. */
    private int desiredKeyboardHeightPx() {
        return choreographer().desiredHeightPx();
    }

    private boolean keyboardHeightDirty() {
        return ReflectionHelpers.getField(choreographer(), "mHeightDirty");
    }

    private KeyboardGeometryChoreographer choreographer() {
        return ReflectionHelpers.getField(mActivity, "mKeyboardGeometry");
    }

    private void layoutActivityRoot() {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY);
        ViewGroup root = mActivity.findViewById(R.id.activity_termux_root_view);
        root.measure(widthSpec, heightSpec);
        root.layout(0, 0, 1080, 1920);
    }
}
