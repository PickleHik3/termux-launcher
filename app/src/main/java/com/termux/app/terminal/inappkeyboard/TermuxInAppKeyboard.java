package com.termux.app.terminal.inappkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/** Activity-scoped controller for the embedded terminal keyboard. */
public final class TermuxInAppKeyboard {

    private static final String STATE_VISIBLE =
        "com.termux.app.terminal.inappkeyboard.VISIBLE";
    private static final String STATE_SELECTED_LAYOUT =
        "com.termux.app.terminal.inappkeyboard.SELECTED_LAYOUT";
    static final String LAYOUT_MAIN = "main";
    static final String LAYOUT_NUMERIC = "numeric";
    static final String LAYOUT_GREEK_MATH = "greekmath";
    private static final String[] LAYOUT_IDS = {
        LAYOUT_MAIN, LAYOUT_NUMERIC, LAYOUT_GREEK_MATH
    };

    public enum ShowReason {
        FIRST_ENABLE,
        TERMINAL_TAP,
        KEYBOARD_ACTION,
        HEIGHT_ADJUSTMENT
    }

    public enum HideReason {
        KEYBOARD_ACTION,
        USER_EVENT,
        PREFERENCE_DISABLED,
        DESTROYED
    }

    public enum ToggleReason {
        KEYBOARD_ACTION
    }

    private InAppKeyboardHost mHost;
    private TermuxAppSharedPreferences mPreferences;
    private final ExecutorService mLayoutExecutor;
    private TermuxInAppKeyboardLayoutLoader mLayoutLoader;

    private TerminalKeyEventHandler mKeyEventHandler;
    private Keyboard2View mKeyboardView;
    private TerminalSession mAttachedSession;
    private KeyboardData mMainKeyboardData;
    private KeyboardData mNumericKeyboardData;
    private KeyboardData mGreekMathKeyboardData;
    private View.OnFocusChangeListener mSystemImeFocusListener;

    private boolean mEnabled;
    private boolean mVisible;
    private boolean mDestroyed;
    private boolean mHeightAdjusting;
    private boolean mExternalTextInputActive;
    private float mHeightScale = 1f;
    private float mKeyMarginScale = 1f;
    private float mKeyCornerRadiusDp = -1f;
    private float mPreAdjustHeightScale = 1f;
    private float mPreAdjustKeyMarginScale = 1f;
    private float mPreAdjustKeyCornerRadiusDp = -1f;
    private String mSelectedLayoutId = LAYOUT_MAIN;
    private String mAppliedConfigSignature;
    private String mExtraKeysStoredValue;
    private LayoutModifier.LayoutOptions mLayoutOptions;

    private ShowReason mLastShowReason;
    private HideReason mLastHideReason;
    private ToggleReason mLastToggleReason;

    public TermuxInAppKeyboard(InAppKeyboardHost host,
                               TermuxAppSharedPreferences preferences) {
        this(host, preferences, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "termux-inapp-keyboard-layout");
            thread.setDaemon(true);
            return thread;
        }), null, null);
    }

    TermuxInAppKeyboard(InAppKeyboardHost host, TermuxAppSharedPreferences preferences,
                        ExecutorService layoutExecutor, File layoutFile,
                        TermuxInAppKeyboardLayoutLoader.ErrorReporter errorReporter) {
        mHost = Objects.requireNonNull(host, "host");
        mPreferences = Objects.requireNonNull(preferences, "preferences");
        mLayoutExecutor = Objects.requireNonNull(layoutExecutor, "layoutExecutor");

        Context context = requireContainer().getContext();
        mExtraKeysStoredValue = mPreferences.getInAppKeyboardExtraKeys();
        mLayoutOptions = buildLayoutOptions(mExtraKeysStoredValue);
        if (layoutFile == null) {
            mLayoutLoader = new TermuxInAppKeyboardLayoutLoader(
                context, mLayoutExecutor, host::runOnMain, mLayoutOptions);
        } else {
            mLayoutLoader = new TermuxInAppKeyboardLayoutLoader(
                context, layoutFile, mLayoutExecutor, host::runOnMain, mLayoutOptions,
                errorReporter);
        }
    }

    private static LayoutModifier.LayoutOptions buildLayoutOptions(String extraKeysStoredValue) {
        return new LayoutModifier.LayoutOptions(true, false, true,
            InAppKeyboardExtraKeys.resolve(extraKeysStoredValue));
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public boolean isVisible() {
        return mEnabled && mVisible && !mExternalTextInputActive;
    }

    public boolean isSystemImeSuppressed() {
        return mEnabled && !mExternalTextInputActive;
    }

    public void onCreate(Bundle state) {
        if (mDestroyed)
            return;
        mEnabled = mPreferences.isInAppKeyboardEnabled();
        mHeightScale = mPreferences.getInAppKeyboardHeightScale();
        mKeyMarginScale = mPreferences.getInAppKeyboardKeyMarginScale();
        mKeyCornerRadiusDp = mPreferences.getInAppKeyboardKeyCornerRadiusDp();
        if (state != null) {
            mSelectedLayoutId = normalizeLayoutId(
                state.getString(STATE_SELECTED_LAYOUT, LAYOUT_MAIN));
            if (mEnabled && state.containsKey(STATE_VISIBLE))
                mVisible = state.getBoolean(STATE_VISIBLE);
            else if (mEnabled)
                mVisible = true;
        } else if (mEnabled) {
            mVisible = true;
            mLastShowReason = ShowReason.FIRST_ENABLE;
        }

        if (mEnabled) {
            suppressSystemIme();
            if (mVisible)
                showInternal();
        } else {
            setContainerVisible(false);
        }
    }

    public void onStart() {
        recheckLayout();
    }

    public void onResume() {
        if (!mEnabled || mDestroyed)
            return;
        if (!mExternalTextInputActive)
            suppressSystemIme();
        recheckLayout();
        if (mVisible && !mExternalTextInputActive)
            showInternal();
    }

    /** Cancels gesture and macro state without changing activity-instance visibility. */
    public void onStop() {
        resetInputPipeline();
    }

    public void onDestroy() {
        if (mDestroyed)
            return;
        mDestroyed = true;
        mLastHideReason = HideReason.DESTROYED;
        resetInputPipeline();
        TerminalView terminalView = mHost == null ? null : mHost.getTerminalView();
        if (terminalView != null && mSystemImeFocusListener != null)
            terminalView.setOnFocusChangeListener(null);
        if (mHost != null)
            mHost.setKeyboardHeightAdjustmentVisible(false);
        if (mHost != null)
            mHost.detachKeyboardView();
        if (mLayoutLoader != null)
            mLayoutLoader.close();
        mLayoutExecutor.shutdownNow();
        mKeyboardView = null;
        mKeyEventHandler = null;
        mMainKeyboardData = null;
        mNumericKeyboardData = null;
        mGreekMathKeyboardData = null;
        mSystemImeFocusListener = null;
        mAttachedSession = null;
        mLayoutLoader = null;
        mPreferences = null;
        mHost = null;
        mVisible = false;
        mEnabled = false;
        mHeightAdjusting = false;
        mExternalTextInputActive = false;
    }

    public void onSaveInstanceState(Bundle out) {
        if (out == null)
            return;
        out.putBoolean(STATE_VISIBLE, mVisible);
        out.putString(STATE_SELECTED_LAYOUT, mSelectedLayoutId);
    }

    /** Rebuilds immutable density metrics while retaining the parsed layout. */
    public void onConfigurationChanged(Configuration configuration) {
        if (!mEnabled || mDestroyed)
            return;
        resetInputPipeline();
        if (mKeyboardView != null) {
            mHost.detachKeyboardView();
            mKeyboardView = null;
        }
        if (mVisible)
            showInternal();
        // The overlay controls sample colors from the keyboard view, which was just recreated.
        if (mHeightAdjusting)
            mHost.setKeyboardHeightAdjustmentVisible(true);
        mHost.requestAccessoryGeometrySync();
    }

    public void onPreferencesReloaded() {
        if (mDestroyed)
            return;
        boolean enabled = mPreferences.isInAppKeyboardEnabled();
        if (mEnabled && !enabled) {
            disable();
            return;
        }
        if (!mEnabled && enabled) {
            mHeightScale = mPreferences.getInAppKeyboardHeightScale();
            mKeyMarginScale = mPreferences.getInAppKeyboardKeyMarginScale();
            mKeyCornerRadiusDp = mPreferences.getInAppKeyboardKeyCornerRadiusDp();
            mEnabled = true;
            mVisible = true;
            mLastShowReason = ShowReason.FIRST_ENABLE;
            suppressSystemIme();
            showInternal();
        } else if (enabled) {
            String extraKeys = mPreferences.getInAppKeyboardExtraKeys();
            if (!Objects.equals(mExtraKeysStoredValue, extraKeys)) {
                mExtraKeysStoredValue = extraKeys;
                onExtraKeysChanged();
            }
            refreshPalette();
            if (!mHeightAdjusting) {
                applyHeightScale(mPreferences.getInAppKeyboardHeightScale());
                applyKeyMarginScale(mPreferences.getInAppKeyboardKeyMarginScale());
                applyKeyCornerRadiusDp(mPreferences.getInAppKeyboardKeyCornerRadiusDp());
            }
        }
        recheckLayout();
    }

    public void show(ShowReason reason) {
        if (!mEnabled || mDestroyed)
            return;
        mLastShowReason = Objects.requireNonNull(reason, "reason");
        mVisible = true;
        suppressSystemIme();
        showInternal();
    }

    public void hide(HideReason reason) {
        if (!mEnabled || mDestroyed)
            return;
        if (mHeightAdjusting && reason != HideReason.PREFERENCE_DISABLED)
            return;
        mLastHideReason = Objects.requireNonNull(reason, "reason");
        mVisible = false;
        resetInputPipeline();
        setContainerVisible(false);
        mHost.requestAccessoryGeometrySync();
    }

    public void toggle(ToggleReason reason) {
        if (!mEnabled || mDestroyed)
            return;
        mLastToggleReason = Objects.requireNonNull(reason, "reason");
        if (mVisible)
            hide(HideReason.KEYBOARD_ACTION);
        else
            show(ShowReason.KEYBOARD_ACTION);
    }

    public void attachSession(TerminalSession session) {
        if (mAttachedSession == session)
            return;
        resetInputPipeline();
        mAttachedSession = session;
    }

    /** Enters activity-owned keyboard geometry adjustment in the terminal foreground. */
    public void beginHeightAdjustment() {
        if (!mEnabled || mDestroyed)
            return;
        if (!mHeightAdjusting) {
            mPreAdjustHeightScale = mHeightScale;
            mPreAdjustKeyMarginScale = mKeyMarginScale;
            mPreAdjustKeyCornerRadiusDp = mKeyCornerRadiusDp;
            mHeightAdjusting = true;
        }
        show(ShowReason.HEIGHT_ADJUSTMENT);
        mHost.setKeyboardHeightAdjustmentVisible(true);
        mHost.requestAccessoryGeometrySync();
    }

    /** Applies one drag-frame preview without writing preferences. */
    public void previewHeightScale(float heightScale) {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyHeightScale(heightScale);
    }

    /** Applies one key-spacing slider preview without writing preferences. */
    public void previewKeyMarginScale(float keyMarginScale) {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyKeyMarginScale(keyMarginScale);
    }

    /** Applies one corner-radius slider preview without writing preferences. */
    public void previewKeyCornerRadiusDp(float radiusDp) {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyKeyCornerRadiusDp(radiusDp);
    }

    public void confirmHeightAdjustment() {
        if (!mHeightAdjusting || mDestroyed)
            return;
        mPreferences.setInAppKeyboardHeightScale(mHeightScale);
        mPreferences.setInAppKeyboardKeyMarginScale(mKeyMarginScale);
        mPreferences.setInAppKeyboardKeyCornerRadiusDp(mKeyCornerRadiusDp);
        finishHeightAdjustment();
    }

    public void cancelHeightAdjustment() {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyHeightScale(mPreAdjustHeightScale);
        applyKeyMarginScale(mPreAdjustKeyMarginScale);
        applyKeyCornerRadiusDp(mPreAdjustKeyCornerRadiusDp);
        finishHeightAdjustment();
    }

    private void finishHeightAdjustment() {
        mHeightAdjusting = false;
        mHost.setKeyboardHeightAdjustmentVisible(false);
        mHost.requestAccessoryGeometrySync();
    }

    private void applyHeightScale(float heightScale) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(heightScale);
        if (Float.compare(mHeightScale, clamped) == 0)
            return;
        mHeightScale = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setHeightScale(mHeightScale);
        requestIntrinsicSizeGeometrySync();
    }

    private void applyKeyMarginScale(float keyMarginScale) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardKeyMarginScale(
            keyMarginScale);
        if (Float.compare(mKeyMarginScale, clamped) == 0)
            return;
        mKeyMarginScale = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setKeyMarginScale(mKeyMarginScale);
        requestIntrinsicSizeGeometrySync();
    }

    private void applyKeyCornerRadiusDp(float radiusDp) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardKeyCornerRadiusDp(radiusDp);
        if (Float.compare(mKeyCornerRadiusDp, clamped) == 0)
            return;
        mKeyCornerRadiusDp = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setKeyCornerRadiusOverride(radiusDpToPx(mKeyCornerRadiusDp));
        requestIntrinsicSizeGeometrySync();
    }

    /**
     * Invalidates the activity-owned measurement before recomputing accessory geometry. The
     * keyboard view can change intrinsic height without changing the available root bounds (for
     * example when the async custom layout replaces the bundled fallback), so a plain layout
     * request is not enough to invalidate the activity's width/height-keyed measurement cache.
     */
    private void requestIntrinsicSizeGeometrySync() {
        mHost.invalidateKeyboardMeasurement();
        mHost.requestAccessoryGeometrySync();
    }

    private float radiusDpToPx(float radiusDp) {
        if (radiusDp < 0f)
            return -1f;
        return radiusDp * requireContainer().getResources().getDisplayMetrics().density;
    }

    public static float calculateHeightScaleForDrag(float startScale, float dragDeltaY,
                                                     float unscaledKeyboardHeight) {
        if (unscaledKeyboardHeight <= 0f || Float.isNaN(unscaledKeyboardHeight)
            || Float.isInfinite(unscaledKeyboardHeight))
            return TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(startScale);
        return TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(
            startScale - dragDeltaY / unscaledKeyboardHeight);
    }

    public boolean isHeightAdjusting() {
        return mHeightAdjusting;
    }

    public float getHeightScale() {
        return mHeightScale;
    }

    public float getKeyMarginScale() {
        return mKeyMarginScale;
    }

    /** Returns the stored override in dp, or -1 when the active palette owns the radius. */
    public float getKeyCornerRadiusDp() {
        return mKeyCornerRadiusDp;
    }

    /** Returns the radius currently rendered, including palette fallback, in dp. */
    public float getEffectiveKeyCornerRadiusDp() {
        if (mKeyboardView == null)
            return Math.max(0f, mKeyCornerRadiusDp);
        float density = requireContainer().getResources().getDisplayMetrics().density;
        return mKeyboardView.getEffectiveKeyCornerRadiusPx() / Math.max(0.0001f, density);
    }

    /** Switches to the custom/default text layout without recreating the renderer. */
    public void requestTextLayout() {
        selectLayout(LAYOUT_MAIN);
    }

    public void requestNumericLayout() {
        selectLayout(LAYOUT_NUMERIC);
    }

    public void requestGreekMathLayout() {
        selectLayout(LAYOUT_GREEK_MATH);
    }

    public void requestForwardLayout() {
        selectRelativeLayout(1);
    }

    public void requestBackwardLayout() {
        selectRelativeLayout(-1);
    }

    /** Forwards the default-dock launch wave into the embedded key renderer. */
    public void animateLaunchWave(int color, float originXOnScreen) {
        if (mKeyboardView == null || !isVisible()) return;
        int[] location = new int[2];
        mKeyboardView.getLocationOnScreen(location);
        mKeyboardView.animateLaunchWave(color, originXOnScreen - location[0]);
    }

    /** Applies strict activity-wide system-IME suppression while embedded mode is enabled. */
    public void suppressSystemIme() {
        if (!mEnabled || mDestroyed || mHost == null || mExternalTextInputActive)
            return;
        mHost.runOnMain(() -> {
            if (!mEnabled || mDestroyed || mHost == null || mExternalTextInputActive)
                return;
            TerminalView terminalView = mHost.getTerminalView();
            Activity activity = findActivity(requireContainer().getContext());
            if (terminalView == null || activity == null)
                return;

            Log.i("KBTRACE", "suppress before visible=" + mVisible
                + " container=" + requireContainer().getVisibility()
                + " flags=0x" + Integer.toHexString(activity.getWindow().getAttributes().flags)
                + " soft=0x" + Integer.toHexString(activity.getWindow().getAttributes().softInputMode));
            KeyboardUtils.hideSoftKeyboard(activity, terminalView);
            KeyboardUtils.setDisableSoftKeyboardFlags(activity);
            int softInputMode = activity.getWindow().getAttributes().softInputMode;
            softInputMode = (softInputMode & ~(WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
                | WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST))
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            activity.getWindow().setSoftInputMode(softInputMode);
            Log.i("KBTRACE", "suppress after visible=" + mVisible
                + " container=" + requireContainer().getVisibility()
                + " flags=0x" + Integer.toHexString(activity.getWindow().getAttributes().flags)
                + " soft=0x" + Integer.toHexString(activity.getWindow().getAttributes().softInputMode));
            if (mSystemImeFocusListener == null) {
                mSystemImeFocusListener = (view, hasFocus) -> {
                    if (hasFocus) {
                        KeyboardUtils.hideSoftKeyboard(activity, terminalView);
                        KeyboardUtils.setDisableSoftKeyboardFlags(activity);
                    }
                };
                terminalView.setOnFocusChangeListener(mSystemImeFocusListener);
            }
            terminalView.requestFocus();
        });
    }

    /** Temporarily yields to a real text field such as a notification RemoteInput reply. */
    public void beginExternalTextInput() {
        if (!mEnabled || mDestroyed || mHost == null || mExternalTextInputActive)
            return;
        mExternalTextInputActive = true;
        mHost.onExternalTextInputStarted();
        resetInputPipeline();
        setContainerVisible(false);
        mHost.requestAccessoryGeometrySync();
        mHost.runOnMain(() -> {
            if (!mEnabled || mDestroyed || mHost == null || !mExternalTextInputActive)
                return;
            TerminalView terminalView = mHost.getTerminalView();
            if (terminalView != null && mSystemImeFocusListener != null)
                terminalView.setOnFocusChangeListener(null);
            mSystemImeFocusListener = null;
            Activity activity = findActivity(requireContainer().getContext());
            if (activity == null) return;
            KeyboardUtils.clearDisableSoftKeyboardFlags(activity);
            int mode = activity.getWindow().getAttributes().softInputMode;
            mode = (mode & ~(WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
                | WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST))
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            activity.getWindow().setSoftInputMode(mode);
        });
    }

    /** Returns whether an explicit launcher-owned text field currently owns the system IME. */
    public boolean isExternalTextInputActive() {
        return mExternalTextInputActive;
    }

    /** Restore the embedded keyboard and its terminal-wide IME suppression after external input. */
    public void endExternalTextInput() {
        if (!mEnabled || mDestroyed || mHost == null || !mExternalTextInputActive)
            return;
        mExternalTextInputActive = false;
        mHost.onExternalTextInputEnded();
        if (mVisible)
            showInternal();
        suppressSystemIme();
    }

    private void disable() {
        // Disable is a transition, so this restoration path can run only once per preference flip.
        hide(HideReason.PREFERENCE_DISABLED);
        mEnabled = false;
        mVisible = false;
        mHeightAdjusting = false;
        mHost.setKeyboardHeightAdjustmentVisible(false);
        resetInputPipeline();

        mHost.detachKeyboardView();
        mKeyboardView = null;
        mKeyEventHandler = null;

        TerminalView terminalView = mHost.getTerminalView();
        if (terminalView != null)
            terminalView.setOnFocusChangeListener(null);
        mSystemImeFocusListener = null;
        Activity activity = findActivity(requireContainer().getContext());
        if (activity != null)
            KeyboardUtils.clearDisableSoftKeyboardFlags(activity);
        mHost.restoreLegacySoftKeyboardState();
    }

    private void showInternal() {
        if (mExternalTextInputActive) {
            setContainerVisible(false);
            return;
        }
        ensureKeyboardView();
        setContainerVisible(true);
        mHost.requestAccessoryGeometrySync();
        recheckLayout();
    }

    private void ensureKeyboardView() {
        if (mKeyboardView != null)
            return;
        TerminalView terminalView = Objects.requireNonNull(mHost.getTerminalView(), "terminalView");
        mKeyEventHandler = new TerminalKeyEventHandler(terminalView,
            () -> mAttachedSession != null ? mAttachedSession : mHost.getCurrentSession(),
            mHost, new Handler(Looper.getMainLooper()));
        Config.Builder configBuilder = new Config.Builder(
            requireContainer().getResources(), mKeyEventHandler);
        configBuilder.hapticEnabled = mPreferences.isInAppKeyboardHapticsEnabled();
        configBuilder.keySoundEnabled = mPreferences.isInAppKeyboardKeySoundEnabled();
        configBuilder.labelFont = loadCustomLabelFont();
        mAppliedConfigSignature = configPreferenceSignature();
        mKeyboardView = new Keyboard2View(requireContainer().getContext(),
            configBuilder.build(), createPalette());
        mKeyboardView.setHeightScale(mHeightScale);
        mKeyboardView.setKeyMarginScale(mKeyMarginScale);
        mKeyboardView.setKeyCornerRadiusOverride(radiusDpToPx(mKeyCornerRadiusDp));
        KeyboardData data = getSelectedLayoutData();
        if (data != null)
            mKeyboardView.setKeyboard(data);
        applyCustomColorScheme();
        mHost.attachKeyboardView(mKeyboardView);
    }

    /**
     * Extra keys are merged into layouts by {@code LayoutModifier.modify}, so every cached
     * layout is stale once the selection changes: rebuild the options, drop the caches, point
     * the loader at the new options, and re-render the current layout.
     */
    private void onExtraKeysChanged() {
        mLayoutOptions = buildLayoutOptions(mExtraKeysStoredValue);
        mMainKeyboardData = null;
        mNumericKeyboardData = null;
        mGreekMathKeyboardData = null;
        if (mLayoutLoader != null)
            mLayoutLoader.setLayoutOptions(mLayoutOptions);
        if (mKeyboardView != null) {
            resetInputPipeline();
            KeyboardData data = getSelectedLayoutData();
            if (data != null)
                mKeyboardView.setKeyboard(data);
        }
    }

    private juloo.keyboard2.Theme.Palette createPalette() {
        Context context = requireContainer().getContext();
        String theme = mPreferences.getInAppKeyboardTheme();
        String dockMatch = mPreferences.getInAppKeyboardDockMatch();
        boolean glass = "glass".equals(dockMatch) || "both".equals(dockMatch);
        juloo.keyboard2.Theme.Palette palette = glass
            ? InAppKeyboardPaletteFactory.createGlass(context, theme)
            : InAppKeyboardPaletteFactory.create(context, theme);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());
        return scheme.applyToPalette(palette);
    }

    /** Immutable-Config inputs; a change forces a renderer rebuild on preference reload. */
    private String configPreferenceSignature() {
        return mPreferences.isInAppKeyboardHapticsEnabled() + "|"
            + mPreferences.isInAppKeyboardKeySoundEnabled() + "|"
            + mPreferences.getInAppKeyboardFontPath();
    }

    @Nullable
    private android.graphics.Typeface loadCustomLabelFont() {
        String fontPath = mPreferences.getInAppKeyboardFontPath();
        if (fontPath == null || fontPath.isEmpty())
            return null;
        File fontFile = new File(fontPath);
        if (!fontFile.isFile())
            return null;
        try {
            android.graphics.Typeface typeface = android.graphics.Typeface.createFromFile(fontFile);
            return android.graphics.Typeface.DEFAULT.equals(typeface) ? null : typeface;
        } catch (RuntimeException e) {
            mHost.debugLog("Failed to load in-app keyboard font " + fontPath + ": " + e);
            return null;
        }
    }

    private void refreshPalette() {
        if (mKeyboardView == null)
            return;
        if (!Objects.equals(mAppliedConfigSignature, configPreferenceSignature())) {
            // Config is immutable by design; rebuild the renderer for haptics/sound/font changes.
            resetInputPipeline();
            mHost.detachKeyboardView();
            mKeyboardView = null;
            mKeyEventHandler = null;
            if (mVisible)
                showInternal();
            return;
        }
        resetInputPipeline();
        mKeyboardView.setPalette(createPalette());
        applyCustomColorScheme();
        if (mHeightAdjusting)
            mHost.setKeyboardHeightAdjustmentVisible(true);
        mHost.requestAccessoryGeometrySync();
    }

    private void applyCustomColorScheme() {
        if (mKeyboardView == null)
            return;
        Context context = requireContainer().getContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());
        mKeyboardView.setKeyColorOverrides(scheme.resolvedOverrides());
    }

    private void recheckLayout() {
        if (!mEnabled || mDestroyed || mLayoutLoader == null)
            return;
        mLayoutLoader.recheck(data -> {
            if (mDestroyed || !mEnabled)
                return;
            mMainKeyboardData = data;
            if (LAYOUT_MAIN.equals(mSelectedLayoutId)) {
                resetInputPipeline();
                if (mKeyboardView != null)
                    mKeyboardView.setKeyboard(data);
            }
            requestIntrinsicSizeGeometrySync();
        });
    }

    private void selectRelativeLayout(int delta) {
        int selectedIndex = 0;
        for (int i = 0; i < LAYOUT_IDS.length; i++) {
            if (LAYOUT_IDS[i].equals(mSelectedLayoutId)) {
                selectedIndex = i;
                break;
            }
        }
        selectLayout(LAYOUT_IDS[(selectedIndex + delta + LAYOUT_IDS.length) % LAYOUT_IDS.length]);
    }

    private void selectLayout(String layoutId) {
        if (!mEnabled || mDestroyed)
            return;
        String normalizedId = normalizeLayoutId(layoutId);
        KeyboardData data = getLayoutData(normalizedId);
        if (data == null)
            return;
        mSelectedLayoutId = normalizedId;
        resetInputPipeline();
        if (mKeyboardView != null)
            mKeyboardView.setKeyboard(data);
        requestIntrinsicSizeGeometrySync();
    }

    private KeyboardData getSelectedLayoutData() {
        return getLayoutData(mSelectedLayoutId);
    }

    private KeyboardData getLayoutData(String layoutId) {
        switch (layoutId) {
            case LAYOUT_NUMERIC:
                if (mNumericKeyboardData == null)
                    mNumericKeyboardData = loadBundledLayout(juloo.keyboard2.R.xml.numeric);
                return mNumericKeyboardData;
            case LAYOUT_GREEK_MATH:
                if (mGreekMathKeyboardData == null)
                    mGreekMathKeyboardData = loadBundledLayout(juloo.keyboard2.R.xml.greekmath);
                return mGreekMathKeyboardData;
            default:
                if (mMainKeyboardData == null && mLayoutLoader != null)
                    mMainKeyboardData = mLayoutLoader.getLastKnownGood();
                if (mMainKeyboardData == null)
                    mMainKeyboardData = loadBundledLayout(juloo.keyboard2.R.xml.latn_qwerty_us);
                return mMainKeyboardData;
        }
    }

    private KeyboardData loadBundledLayout(int resourceId) {
        KeyboardData keyboard = KeyboardData.load(requireContainer().getResources(), resourceId);
        if (keyboard == null) {
            mHost.debugLog("Failed to load bundled in-app keyboard layout " + resourceId);
            return null;
        }
        return LayoutModifier.modify(keyboard, mLayoutOptions,
            requireContainer().getResources());
    }

    private static String normalizeLayoutId(String layoutId) {
        for (String knownId : LAYOUT_IDS) {
            if (knownId.equals(layoutId))
                return knownId;
        }
        return LAYOUT_MAIN;
    }

    private void resetInputPipeline() {
        if (mKeyboardView != null)
            mKeyboardView.resetInputState();
        if (mKeyEventHandler != null)
            mKeyEventHandler.resetInputState();
    }

    private void setContainerVisible(boolean visible) {
        if (mHost != null) {
            Log.i("KBTRACE", "setContainerVisible " + visible + " old=" + requireContainer().getVisibility());
            mHost.setKeyboardContainerVisible(visible);
        }
    }

    private View requireContainer() {
        return Objects.requireNonNull(mHost.getKeyboardContainer(), "keyboardContainer");
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity)
                return (Activity) current;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current)
                break;
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    ShowReason getLastShowReason() {
        return mLastShowReason;
    }

    HideReason getLastHideReason() {
        return mLastHideReason;
    }

    ToggleReason getLastToggleReason() {
        return mLastToggleReason;
    }

    String getSelectedLayoutId() {
        return mSelectedLayoutId;
    }
}
