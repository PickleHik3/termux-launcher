package com.termux.app.terminal.inappkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    static final String LAYOUT_MAIN = LauncherKeyboardLayouts.LAYOUT_MAIN;
    static final String LAYOUT_NUMERIC = "numeric";
    static final String LAYOUT_GREEK_MATH = "greekmath";

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
    /** Parsed bundled text layouts, keyed by catalogue id. Dropped whenever extra keys move. */
    private final Map<String, KeyboardData> mTextLayoutCache = new HashMap<>();
    private View.OnFocusChangeListener mSystemImeFocusListener;
    private TerminalKeyEventHandler.KeyValueInterceptor mKeyValueInterceptor;

    private boolean mEnabled;
    private boolean mVisible;
    private boolean mDestroyed;
    private boolean mHeightAdjusting;
    private boolean mExternalTextInputActive;
    private float mHeightScale = 1f;
    private float mKeyMarginScale = 1f;
    private float mKeyCornerRadiusDp = -1f;
    private int mKeyOpacity = TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY;
    private float mPreAdjustHeightScale = 1f;
    private float mPreAdjustKeyMarginScale = 1f;
    private float mPreAdjustKeyCornerRadiusDp = -1f;
    private String mSelectedLayoutId = LAYOUT_MAIN;
    /** The hot-swap ring, in cycle order. Never empty; {@code [main]} until preferences load. */
    @NonNull
    private List<String> mLayoutRing = java.util.Collections.singletonList(LAYOUT_MAIN);
    /** Where the ring stands. A modal pad can be on screen over it without moving it. */
    @NonNull
    private String mActiveTextLayoutId = LAYOUT_MAIN;
    private String mAppliedConfigSignature;
    /**
     * Material source-role signature the currently rendered palette was built from. Only ever
     * read while {@link #mKeyboardView} is non-null, which implies {@link #createPalette()} has
     * already recorded one.
     */
    private int mAppliedPaletteSignature;
    private String mExtraKeysStoredValue;
    private LayoutModifier.LayoutOptions mLayoutOptions;
    private final int[] mLaunchWaveLocation = new int[2];

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
        mKeyOpacity = mPreferences.getInAppKeyboardKeyOpacity();
        reloadLayoutRing(false);
        mSelectedLayoutId = mActiveTextLayoutId;
        if (state != null) {
            mSelectedLayoutId = normalizeLayoutId(
                state.getString(STATE_SELECTED_LAYOUT, mActiveTextLayoutId));
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
        mTextLayoutCache.clear();
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
        // Height scale is stored per orientation; rotation must swap to the new orientation's value
        // before the keyboard view is rebuilt below.
        if (!mHeightAdjusting)
            mHeightScale = mPreferences.getInAppKeyboardHeightScale();
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
            mKeyOpacity = mPreferences.getInAppKeyboardKeyOpacity();
            mEnabled = true;
            mVisible = true;
            mLastShowReason = ShowReason.FIRST_ENABLE;
            // The ring may have been edited while the keyboard was off; render the layout it
            // ends on rather than the one this instance was created with.
            reloadLayoutRing(false);
            mSelectedLayoutId = mActiveTextLayoutId;
            suppressSystemIme();
            showInternal();
        } else if (enabled) {
            String extraKeys = mPreferences.getInAppKeyboardExtraKeys();
            if (!Objects.equals(mExtraKeysStoredValue, extraKeys)) {
                mExtraKeysStoredValue = extraKeys;
                onExtraKeysChanged();
            }
            refreshPalette();
            reloadLayoutRing(true);
            if (!mHeightAdjusting) {
                applyHeightScale(mPreferences.getInAppKeyboardHeightScale());
                applyKeyMarginScale(mPreferences.getInAppKeyboardKeyMarginScale());
                applyKeyCornerRadiusDp(mPreferences.getInAppKeyboardKeyCornerRadiusDp());
                applyKeyOpacity(mPreferences.getInAppKeyboardKeyOpacity());
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
        applyHeightScale(heightScale, true);
    }

    /** Applies one key-spacing slider preview without writing preferences. */
    public void previewKeyMarginScale(float keyMarginScale) {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyKeyMarginScale(keyMarginScale, true);
    }

    /** Applies one corner-radius slider preview without writing preferences. */
    public void previewKeyCornerRadiusDp(float radiusDp) {
        if (!mHeightAdjusting || mDestroyed)
            return;
        applyKeyCornerRadiusDp(radiusDp, true);
    }

    /** Lightweight live previews used by the unified surface editor. */
    public void previewSurfaceEditorHeightScale(float heightScale) {
        if (!mEnabled || mDestroyed)
            return;
        applyHeightScale(heightScale, true);
    }

    public void previewSurfaceEditorKeyMarginScale(float keyMarginScale) {
        if (!mEnabled || mDestroyed)
            return;
        applyKeyMarginScale(keyMarginScale, true);
    }

    public void previewSurfaceEditorKeyCornerRadiusDp(float radiusDp) {
        if (!mEnabled || mDestroyed)
            return;
        applyKeyCornerRadiusDp(radiusDp, true);
    }

    /**
     * Key-cap opacity preview. Deliberately the cheapest editor control: it repaints only the
     * keyboard view itself — no geometry sync, no accessory glass re-render.
     */
    public void previewSurfaceEditorKeyOpacity(int opacityPercent) {
        if (!mEnabled || mDestroyed)
            return;
        applyKeyOpacity(opacityPercent);
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
        applyHeightScale(heightScale, false);
    }

    private void applyHeightScale(float heightScale, boolean livePreview) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardHeightScale(heightScale);
        if (Float.compare(mHeightScale, clamped) == 0)
            return;
        mHeightScale = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setHeightScale(mHeightScale);
        requestIntrinsicSizeGeometrySync(livePreview);
    }

    private void applyKeyMarginScale(float keyMarginScale) {
        applyKeyMarginScale(keyMarginScale, false);
    }

    private void applyKeyMarginScale(float keyMarginScale, boolean livePreview) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardKeyMarginScale(
            keyMarginScale);
        if (Float.compare(mKeyMarginScale, clamped) == 0)
            return;
        mKeyMarginScale = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setKeyMarginScale(mKeyMarginScale);
        requestIntrinsicSizeGeometrySync(livePreview);
    }

    private void applyKeyOpacity(int opacityPercent) {
        int clamped = TermuxAppSharedPreferences.clampInAppKeyboardKeyOpacity(opacityPercent);
        if (mKeyOpacity == clamped)
            return;
        mKeyOpacity = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setKeyOpacity(mKeyOpacity < 0 ? -1f : mKeyOpacity / 100f);
    }

    /** Configured absolute key opacity, or the theme's current effective one when unset. */
    public int getEffectiveKeyOpacityPercent() {
        if (mKeyOpacity >= 0)
            return mKeyOpacity;
        if (mKeyboardView != null)
            return mKeyboardView.getEffectiveKeyFillOpacityPercent();
        return 100;
    }

    private void applyKeyCornerRadiusDp(float radiusDp) {
        applyKeyCornerRadiusDp(radiusDp, false);
    }

    private void applyKeyCornerRadiusDp(float radiusDp, boolean livePreview) {
        float clamped = TermuxAppSharedPreferences.clampInAppKeyboardKeyCornerRadiusDp(radiusDp);
        if (Float.compare(mKeyCornerRadiusDp, clamped) == 0)
            return;
        mKeyCornerRadiusDp = clamped;
        if (mKeyboardView != null)
            mKeyboardView.setKeyCornerRadiusOverride(radiusDpToPx(mKeyCornerRadiusDp));
        requestIntrinsicSizeGeometrySync(livePreview);
    }

    /**
     * Invalidates the activity-owned measurement before recomputing accessory geometry. The
     * keyboard view can change intrinsic height without changing the available root bounds (for
     * example when the async custom layout replaces the bundled fallback), so a plain layout
     * request is not enough to invalidate the activity's width/height-keyed measurement cache.
     */
    private void requestIntrinsicSizeGeometrySync(boolean livePreview) {
        if (livePreview) {
            mHost.requestAccessoryGeometryPreviewSync();
        } else {
            mHost.invalidateKeyboardMeasurement();
            mHost.requestAccessoryGeometrySync();
        }
    }

    private void requestIntrinsicSizeGeometrySync() {
        requestIntrinsicSizeGeometrySync(false);
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

    /** Returns from a modal pad to the text layout the ring is on. */
    public void requestTextLayout() {
        selectLayout(mActiveTextLayoutId);
    }

    public void requestNumericLayout() {
        selectLayout(LAYOUT_NUMERIC);
    }

    public void requestGreekMathLayout() {
        selectLayout(LAYOUT_GREEK_MATH);
    }

    /**
     * The next text layout in the user's ring. Upstream's {@code switch_forward} key means this,
     * and so does the {@code keyboard.cycle_layout} action.
     */
    public void requestForwardLayout() {
        cycleTextLayout(1);
    }

    public void requestBackwardLayout() {
        cycleTextLayout(-1);
    }

    /** The ring in cycle order, for the palette's per-layout rows and the Settings picker. */
    @NonNull
    public List<String> getLayoutRing() {
        return mLayoutRing;
    }

    /** The text layout the ring is on, even while a modal pad is the one on screen. */
    @NonNull
    public String getActiveTextLayoutId() {
        return mActiveTextLayoutId;
    }

    /**
     * Steps the ring and swaps the keyboard under the user's thumbs, announcing where it landed.
     *
     * <p>A ring of one is the unconfigured default, and a key bound to cycling would otherwise
     * look broken on it, so that case says where to add layouts instead of doing nothing.
     *
     * @return whether the layout actually changed
     */
    public boolean cycleTextLayout(int delta) {
        if (!mEnabled || mDestroyed)
            return false;
        if (mLayoutRing.size() < 2) {
            AppNotice.show(requireContainer().getContext(),
                requireContainer().getContext().getString(R.string.keyboard_layout_ring_single),
                false);
            return false;
        }
        String next = LauncherKeyboardLayouts.cycle(mLayoutRing, mActiveTextLayoutId, delta);
        boolean moved = !next.equals(mActiveTextLayoutId);
        selectTextLayout(next, true);
        return moved;
    }

    /**
     * Moves the ring onto {@code layoutId} and renders it. Any catalogued layout is accepted,
     * not just a ring member: a binding or a palette row may name a layout the ring does not
     * carry, and refusing it would be a dead key with no way to see why.
     *
     * @return false when nothing in the catalogue answers to that id, so a caller can say so
     */
    public boolean selectTextLayout(@NonNull String layoutId, boolean announce) {
        if (!mEnabled || mDestroyed)
            return false;
        if (!LAYOUT_MAIN.equals(layoutId) && LauncherKeyboardLayouts.find(
                requireContainer().getResources(), layoutId) == null)
            return false;
        boolean moved = !layoutId.equals(mActiveTextLayoutId)
            || !layoutId.equals(mSelectedLayoutId);
        mActiveTextLayoutId = layoutId;
        mPreferences.setInAppKeyboardActiveLayout(layoutId);
        selectLayout(layoutId);
        if (announce && moved) {
            AppNotice.show(requireContainer().getContext(),
                LauncherKeyboardLayouts.labelFor(
                    requireContainer().getResources(), layoutId), false);
        }
        return true;
    }

    /**
     * Routes resolved key values to an in-activity overlay before the terminal sees them.
     * Held here rather than on the handler because the renderer — and with it the handler —
     * is rebuilt whenever the layout or theme changes.
     */
    public void setKeyValueInterceptor(
        @Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor) {
        mKeyValueInterceptor = interceptor;
        if (mKeyEventHandler != null)
            mKeyEventHandler.setKeyValueInterceptor(interceptor);
    }

    /** On-screen bounds of the rendered space bar, or false when there is none to seed from. */
    public boolean getSpaceBarRectOnScreen(@NonNull Rect out) {
        return mKeyboardView != null && isVisible()
            && mKeyboardView.getSpaceBarRectOnScreen(out);
    }

    /**
     * The whole keyboard container's bounds on screen, for overlays that must let touches through
     * to the keys they type with; false while the keyboard is hidden.
     */
    public boolean getKeyboardRectOnScreen(@NonNull Rect out) {
        if (!isVisible())
            return false;
        View container = mHost.getKeyboardContainer();
        if (container == null || container.getWidth() <= 0 || !container.isShown())
            return false;
        int[] location = new int[2];
        container.getLocationOnScreen(location);
        out.set(location[0], location[1],
            location[0] + container.getWidth(), location[1] + container.getHeight());
        return true;
    }

    /** Forwards the default-dock launch wave into the embedded key renderer. */
    public void animateLaunchWave(int color, float originXOnScreen, float originYOnScreen) {
        if (mKeyboardView == null || !isVisible()) return;
        mKeyboardView.getLocationOnScreen(mLaunchWaveLocation);
        mKeyboardView.animateLaunchWave(color, originXOnScreen - mLaunchWaveLocation[0],
            originYOnScreen - mLaunchWaveLocation[1]);
    }

    /** Fades a launch modulation before keyboard or dock geometry is replaced. */
    public void fadeOutLaunchWave() {
        if (mKeyboardView != null)
            mKeyboardView.fadeOutLaunchWave();
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

            KeyboardUtils.hideSoftKeyboard(activity, terminalView);
            KeyboardUtils.setDisableSoftKeyboardFlags(activity);
            int softInputMode = activity.getWindow().getAttributes().softInputMode;
            softInputMode = (softInputMode & ~(WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
                | WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST))
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            activity.getWindow().setSoftInputMode(softInputMode);
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
        // Hiding keeps the renderer alive, so a theme or wallpaper change that arrived while the
        // keyboard was off screen is applied on the way back on.
        refreshMaterialPaletteIfSignatureMoved();
        setContainerVisible(true);
        mHost.requestAccessoryGeometrySync();
        recheckLayout();
    }

    private void ensureKeyboardView() {
        if (mKeyboardView != null)
            return;
        Objects.requireNonNull(mHost.getTerminalView(), "terminalView");
        // Supplier so the handler always targets the currently focused pane.
        mKeyEventHandler = new TerminalKeyEventHandler(mHost::getTerminalView,
            () -> mAttachedSession != null ? mAttachedSession : mHost.getCurrentSession(),
            mHost, new Handler(Looper.getMainLooper()));
        mKeyEventHandler.setKeyValueInterceptor(mKeyValueInterceptor);
        Config.Builder configBuilder = new Config.Builder(
            requireContainer().getResources(), mKeyEventHandler);
        configBuilder.hapticEnabled = mPreferences.isInAppKeyboardHapticsEnabled();
        configBuilder.keySoundEnabled = mPreferences.isInAppKeyboardKeySoundEnabled();
        configBuilder.labelFont = loadCustomLabelFont();
        mAppliedConfigSignature = configPreferenceSignature();
        mKeyboardView = new Keyboard2View(requireContainer().getContext(),
            configBuilder.build(), createPalette());
        mAppliedPaletteSignature = InAppKeyboardPaletteFactory.signature(
            requireContainer().getContext());
        mKeyboardView.setHeightScale(mHeightScale);
        mKeyboardView.setKeyMarginScale(mKeyMarginScale);
        mKeyboardView.setKeyCornerRadiusOverride(radiusDpToPx(mKeyCornerRadiusDp));
        mKeyboardView.setKeyOpacity(mKeyOpacity < 0 ? -1f : mKeyOpacity / 100f);
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
        juloo.keyboard2.Theme.Palette palette =
            InAppKeyboardPaletteFactory.createGlass(context, theme);
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());
        return scheme.shouldApplyImportedPalette(theme) ? scheme.applyToPalette(palette) : palette;
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
            return bundledSymbolsLabelFont();
        File fontFile = new File(fontPath);
        if (!fontFile.isFile())
            return bundledSymbolsLabelFont();
        try {
            android.graphics.Typeface typeface = android.graphics.Typeface.createFromFile(fontFile);
            return android.graphics.Typeface.DEFAULT.equals(typeface)
                ? bundledSymbolsLabelFont() : typeface;
        } catch (RuntimeException e) {
            mHost.debugLog("Failed to load in-app keyboard font " + fontPath + ": " + e);
            return bundledSymbolsLabelFont();
        }
    }

    /**
     * The default label face when no user font is picked: the bundled symbols-only Nerd Font, so
     * icon code points on custom keys render instead of showing tofu. It has no Latin glyphs, so
     * ordinary labels fall through to the very system font a null labelFont would have used.
     */
    @Nullable
    private android.graphics.Typeface bundledSymbolsLabelFont() {
        return com.termux.shared.termux.font.NerdFontSpans.typeface(
            requireContainer().getContext());
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
        mAppliedPaletteSignature = InAppKeyboardPaletteFactory.signature(
            requireContainer().getContext());
        applyCustomColorScheme();
        if (mHeightAdjusting)
            mHost.setKeyboardHeightAdjustmentVisible(true);
        mHost.requestAccessoryGeometrySync();
    }

    /**
     * Re-resolves the Material-driven half of the keyboard's appearance — the theme palette and
     * the per-key color scheme — and repaints the live keyboard in place, so a wallpaper or
     * system-theme change is visible without hiding and reshowing the keyboard.
     *
     * <p>The stored scheme is reloaded, which re-resolves its dynamic slots against the fresh
     * Material roles while pinned swatches and imported palettes keep their stored colors
     * exactly: only dynamic slots move.
     *
     * <p>The Material source-role signature is compared first, so a wallpaper change that does
     * not move the palette costs one role resolve and nothing else. Must be called on the main
     * thread. No-op while the keyboard is disabled, destroyed, or not currently on screen — a
     * keyboard that is hidden picks the change up in {@link #showInternal()} when it returns.
     *
     * @return true when the palette had moved and the live keyboard was repainted
     */
    public boolean refreshMaterialPalette() {
        if (!mEnabled || mDestroyed || !isVisible())
            return false;
        return refreshMaterialPaletteIfSignatureMoved();
    }

    /** Cheap signature check first, palette rebuild only when the Material roles actually moved. */
    private boolean refreshMaterialPaletteIfSignatureMoved() {
        if (mKeyboardView == null)
            return false;
        int signature = InAppKeyboardPaletteFactory.signature(requireContainer().getContext());
        if (signature == mAppliedPaletteSignature)
            return false;
        mAppliedPaletteSignature = signature;
        // Both of these reload the stored scheme, so dynamic slots re-resolve against the new
        // Material roles while pinned and imported swatches keep their persisted colors.
        mKeyboardView.setPalette(createPalette());
        applyCustomColorScheme();
        return true;
    }

    private void applyCustomColorScheme() {
        if (mKeyboardView == null)
            return;
        Context context = requireContainer().getContext();
        InAppKeyboardColorScheme scheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());
        mKeyboardView.setKeyColorOverrides(scheme.resolvedOverrides());
    }

    /**
     * Re-reads the ring from preferences. When the layout in use is no longer in it — the user
     * removed it in Settings — the ring restarts at its first entry, so the keyboard is never
     * left on a layout its own list no longer carries.
     *
     * @param applyToView whether a moved ring should also swap what is on screen; false while
     *        the keyboard is still being built, where the caller renders once at the end
     */
    private void reloadLayoutRing(boolean applyToView) {
        android.content.res.Resources resources = requireContainer().getResources();
        mLayoutRing = LauncherKeyboardLayouts.parseSelection(resources,
            mPreferences.getInAppKeyboardLayouts());
        String stored = mPreferences.getInAppKeyboardActiveLayout();
        String active = mLayoutRing.contains(stored) ? stored : mLayoutRing.get(0);
        if (active.equals(mActiveTextLayoutId))
            return;
        mActiveTextLayoutId = active;
        // A modal pad keeps its place; it picks the new ring position up when it returns to text.
        if (applyToView && !LAYOUT_NUMERIC.equals(mSelectedLayoutId)
            && !LAYOUT_GREEK_MATH.equals(mSelectedLayoutId))
            selectLayout(active);
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

    /**
     * Lights the keys bound under the latched Ctrl+Alt(+Shift) prefix directly on the visible
     * keyboard, each in its legend group's colour: a bound primary key takes the group colour
     * as its chip tint and border, a key whose corner/edge slot is bound takes a faint tint
     * with just that slot's label group set alight. Null or empty clears the lighting.
     *
     * <p>The overrides ride {@link Keyboard2View#setKeybindHintOverrides}'s transient layer,
     * so the user's color scheme underneath is never touched and returns untouched on clear.
     *
     * @param litTokens binding suffix token (see
     *        {@link com.termux.app.terminal.TerminalKeyBindingResolver#keyToken}) to colour
     */
    public void setKeybindHintHighlights(@Nullable java.util.Map<String, Integer> litTokens) {
        if (mDestroyed || mKeyboardView == null)
            return;
        KeyboardData layout = getSelectedLayoutData();
        if (litTokens == null || litTokens.isEmpty() || layout == null) {
            mKeyboardView.setKeybindHintOverrides(null);
            return;
        }
        juloo.keyboard2.Theme.Palette palette = createPalette();
        java.util.Map<String, Keyboard2View.KeyColorOverride> overrides =
            new java.util.HashMap<>();
        for (int rowIndex = 0; rowIndex < layout.rows.size(); rowIndex++) {
            KeyboardData.Row row = layout.rows.get(rowIndex);
            for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++) {
                Keyboard2View.KeyColorOverride override =
                    hintOverrideForKey(row.keys.get(keyIndex), litTokens, palette);
                if (override != null)
                    overrides.put(rowIndex + ":" + keyIndex, override);
            }
        }
        mKeyboardView.setKeybindHintOverrides(overrides);
    }

    /**
     * Marks the cap that carries {@code token} — the {@code ?} that opens the full keymap — as the
     * invitation, so it breathes harder than the bound caps lit around it. Null clears it.
     *
     * <p>Resolved here rather than in the view for the same reason the lighting is: only this side
     * knows which position on the live layout a binding token sits on.
     */
    public void setKeybindHintPulse(@Nullable String token) {
        if (mDestroyed || mKeyboardView == null)
            return;
        KeyboardData layout = getSelectedLayoutData();
        if (token == null || layout == null) {
            mKeyboardView.setKeybindHintPulseToken(null);
            return;
        }
        for (int rowIndex = 0; rowIndex < layout.rows.size(); rowIndex++) {
            KeyboardData.Row row = layout.rows.get(rowIndex);
            for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++) {
                if (!keyCarriesToken(row.keys.get(keyIndex), token))
                    continue;
                mKeyboardView.setKeybindHintPulseToken(rowIndex + ":" + keyIndex);
                return;
            }
        }
        mKeyboardView.setKeybindHintPulseToken(null);
    }

    /** Whether any of a key's nine slots sends {@code token}. */
    private static boolean keyCarriesToken(KeyboardData.Key key, @NonNull String token) {
        for (int slot = 0; slot < 9; slot++) {
            juloo.keyboard2.KeyValue value = key.getKeyValue(slot);
            if (value != null && token.equals(keybindHintToken(value)))
                return true;
        }
        return false;
    }

    @Nullable
    private static Keyboard2View.KeyColorOverride hintOverrideForKey(
            KeyboardData.Key key, java.util.Map<String, Integer> litTokens,
            juloo.keyboard2.Theme.Palette palette) {
        juloo.keyboard2.KeyValue center = key.getKeyValue(0);
        String centerToken = center == null ? null : keybindHintToken(center);
        Integer primary = centerToken == null ? null : litTokens.get(centerToken);
        if (primary != null) {
            int glyph = InAppKeyboardPaletteFactory.ensureContrast(0xFF14171A, primary);
            int slotGlyph = androidx.core.graphics.ColorUtils.setAlphaComponent(glyph, 153);
            return new Keyboard2View.KeyColorOverride(primary, glyph, slotGlyph, slotGlyph,
                primary);
        }
        Integer slotColor = null;
        boolean bottomHit = false;
        boolean otherHit = false;
        for (int slot = 1; slot < 9; slot++) {
            juloo.keyboard2.KeyValue value = key.getKeyValue(slot);
            if (value == null)
                continue;
            String token = keybindHintToken(value);
            Integer color = token == null ? null : litTokens.get(token);
            if (color == null)
                continue;
            slotColor = color;
            // Sublabel overrides come in two groups: sw/se (bottom) and the rest.
            if (slot == 3 || slot == 4) bottomHit = true;
            else otherHit = true;
        }
        if (slotColor == null)
            return null;
        // The fill override only keeps the hue (the glass chip's own alpha and gradient stay),
        // so a light blend toward the group colour reads as a faint tint of the cap.
        int tint = androidx.core.graphics.ColorUtils.blendARGB(
            palette.keyBackground | 0xFF000000, slotColor | 0xFF000000, 0.35f);
        return new Keyboard2View.KeyColorOverride(tint, null,
            otherHit ? slotColor : null, bottomHit ? slotColor : null,
            androidx.core.graphics.ColorUtils.setAlphaComponent(slotColor, 128));
    }

    /**
     * The stroke token a root-keymap binding would use for this key value, mirroring
     * {@link com.termux.app.terminal.TerminalKeyBindingResolver#keyToken}; null for values a
     * binding cannot name.
     */
    @Nullable
    private static String keybindHintToken(juloo.keyboard2.KeyValue value) {
        switch (value.getKind()) {
            case Char:
                return com.termux.app.terminal.TerminalKeyBindingResolver
                    .tokenForChar(value.getChar());
            case Keyevent:
                return com.termux.app.terminal.TerminalKeyBindingResolver
                    .keyToken(value.getKeyevent());
            case Editing:
                if (value.getEditing() == juloo.keyboard2.KeyValue.Editing.SPACE_BAR)
                    return "space";
                if (value.getEditing() == juloo.keyboard2.KeyValue.Editing.BACKSPACE)
                    return "backspace";
                return null;
            default:
                return null;
        }
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
            case LAYOUT_MAIN:
                if (mMainKeyboardData == null && mLayoutLoader != null)
                    mMainKeyboardData = mLayoutLoader.getLastKnownGood();
                if (mMainKeyboardData == null)
                    mMainKeyboardData = loadBundledLayout(juloo.keyboard2.R.xml.termux_launcher_qwerty);
                return mMainKeyboardData;
            default:
                return getBundledTextLayoutData(layoutId);
        }
    }

    /**
     * A catalogued layout, parsed once and kept. Parsing is a small bundled XML resource on the
     * main thread — the same cost the numeric and Greek/math pads already pay when first shown —
     * so a swap lands in the frame it was asked for instead of a frame later.
     */
    @Nullable
    private KeyboardData getBundledTextLayoutData(String layoutId) {
        KeyboardData cached = mTextLayoutCache.get(layoutId);
        if (cached != null)
            return cached;
        LauncherKeyboardLayouts.Layout layout = LauncherKeyboardLayouts.find(
            requireContainer().getResources(), layoutId);
        if (layout == null || layout.xmlResId == 0)
            return getLayoutData(LAYOUT_MAIN);
        KeyboardData data = loadBundledLayout(layout.xmlResId);
        if (data == null)
            return getLayoutData(LAYOUT_MAIN);
        mTextLayoutCache.put(layoutId, data);
        return data;
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

    /** Any modal pad, any catalogued text layout, or — for anything else — where the ring is. */
    @NonNull
    private String normalizeLayoutId(@Nullable String layoutId) {
        if (LAYOUT_NUMERIC.equals(layoutId) || LAYOUT_GREEK_MATH.equals(layoutId)
            || LAYOUT_MAIN.equals(layoutId))
            return layoutId;
        if (layoutId != null && LauncherKeyboardLayouts.find(
                requireContainer().getResources(), layoutId) != null)
            return layoutId;
        return mActiveTextLayoutId;
    }

    private void resetInputPipeline() {
        if (mKeyboardView != null)
            mKeyboardView.resetInputState();
        if (mKeyEventHandler != null)
            mKeyEventHandler.resetInputState();
    }

    private void setContainerVisible(boolean visible) {
        if (mHost != null) {
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
