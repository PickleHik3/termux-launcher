package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.termux.R;
import com.termux.app.SuggestionBarCallback;
import com.termux.app.TermuxActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.app.terminal.io.KeyboardShortcut;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard.ShowReason;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard.ToggleReason;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.data.DataUtils;
import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.logger.Logger;

import org.json.JSONObject;

import juloo.keyboard2.KeyValue;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.data.TermuxUrlUtils;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

public class TermuxTerminalViewClient extends TermuxTerminalViewClientBase {

    final TermuxActivity mActivity;

    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Keeping track of the special keys acting as Ctrl and Fn for the soft keyboard and other hardware keys.
     */
    boolean mVirtualControlKeyDown, mVirtualFnKeyDown;

    private Runnable mShowSoftKeyboardRunnable;

    // WP4 installs the activity-owned controller. Null intentionally preserves legacy IME policy.
    private TermuxInAppKeyboard mInAppKeyboardController;

    private boolean mShowSoftKeyboardIgnoreOnce;

    private boolean mShowSoftKeyboardWithDelayOnce;

    private boolean mTerminalCursorBlinkerStateAlreadySet;

    private List<KeyboardShortcut> mSessionShortcuts;

    private static final String LOG_TAG = "TermuxTerminalViewClient";
    /** Retry after a toggle-startup race has had time to restore terminal focus. */
    private static final long KEYBOARD_TOGGLE_RETRY_DELAY_MS = 500L;
    /** First retry while a resumed activity is still completing its window transition. */
    private static final long KEYBOARD_RESUME_FIRST_RETRY_DELAY_MS = 140L;
    /** Normal fallback retry after an immediate keyboard show request. */
    private static final long KEYBOARD_STANDARD_RETRY_DELAY_MS = 300L;
    /** Second retry for devices whose resumed window becomes IME-ready later. */
    private static final long KEYBOARD_RESUME_SECOND_RETRY_DELAY_MS = 320L;
    /** Matches kitty's default multi-key mapping timeout. */
    private static final long KEY_CHORD_TIMEOUT_MS = 2_000L;
    private static final ExecutorService REPORT_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "termux-report-builder");
        thread.setDaemon(true);
        return thread;
    });
    private SuggestionBarCallback mSuggestionBarCallback;
    private final View.OnFocusChangeListener mTerminalFocusChangeListener;
    private final Handler mKeyChordHandler = new Handler(Looper.getMainLooper());
    private final TerminalKeyChordOverlay mKeyChordOverlay;
    private final Runnable mKeyChordTimeout = this::cancelPendingKeyChord;
    private final Runnable mKeyModeTimeout = this::expireKeyMode;

    public TermuxTerminalViewClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
        this.mTerminalFocusChangeListener = this::onTerminalFocusChanged;
        this.mKeyChordOverlay = new TerminalKeyChordOverlay(activity);
    }

    public TermuxActivity getActivity() {
        return mActivity;
    }

    public void setInAppKeyboardController(TermuxInAppKeyboard controller) {
        mInAppKeyboardController = controller;
    }

    public void setSuggestionBarCallback(SuggestionBarCallback callback) {
        mSuggestionBarCallback = callback;
    }

    /**
     * Should be called when mActivity.onCreate() is called
     */
    public void onCreate() {
        onReloadProperties();
        // Panes are created lazily by TerminalPaneController (each configured in PaneHost), so
        // there may be no active pane yet at activity onCreate. Guard the initial font/keep-on setup.
        TerminalView view = mActivity.getTerminalView();
        if (view != null)
            view.setTextSize(mActivity.getPreferences().getFontSize());
        mActivity.requestTerminalFlushDockGeometryUpdate();
        if (view != null) {
            view.setKeepScreenOn(mActivity.getPreferences().shouldKeepScreenOn());
            applyCursorTrailPolicy(view);
        }
    }

    /**
     * Decide whether a terminal view animates its cursor. The view itself has no opinion: the
     * preference and the device's power state are policy, and both are re-read on resume because the
     * user can change either while the activity is stopped.
     */
    public void applyCursorTrailPolicy(TerminalView view) {
        if (view == null)
            return;
        boolean enabled = mActivity.getPreferences().isTerminalCursorTrailEnabled();
        if (enabled) {
            PowerManager powerManager = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && powerManager.isPowerSaveMode())
                enabled = false;
        }
        view.setCursorTrailEnabled(enabled);
    }

    /**
     * Should be called when mActivity.onStart() is called
     */
    public void onStart() {
        // Set {@link TerminalView#TERMINAL_VIEW_KEY_LOGGING_ENABLED} value
        // Also required if user changed the preference from {@link TermuxSettings} activity and returns
        boolean isTerminalViewKeyLoggingEnabled = mActivity.getPreferences().isTerminalViewKeyLoggingEnabled();
        mActivity.getTerminalView().setIsTerminalViewKeyLoggingEnabled(isTerminalViewKeyLoggingEnabled);
        // Piggyback on the terminal view key logging toggle for now, should add a separate toggle in future
        mActivity.getTermuxActivityRootView().setIsRootViewLoggingEnabled(isTerminalViewKeyLoggingEnabled);
        ViewUtils.setIsViewUtilsLoggingEnabled(isTerminalViewKeyLoggingEnabled);
    }

    /**
     * Should be called when mActivity.onResume() is called
     */
    public void onResume() {
        setSoftKeyboardState(true, mActivity.isActivityRecreated());
        applyCursorTrailPolicy(mActivity.getTerminalView());
        mTerminalCursorBlinkerStateAlreadySet = false;
        if (mActivity.getTerminalView().mEmulator != null) {
            // Start terminal cursor blinking if enabled
            // If emulator is already set, then start blinker now, otherwise wait for onEmulatorSet()
            // event to start it. This is needed since onEmulatorSet() may not be called after
            // TermuxActivity is started after device display timeout with double tap and not power button.
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    /**
     * Should be called when mActivity.onStop() is called
     */
    public void onStop() {
        // Stop terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(false);
        if (mShowSoftKeyboardRunnable != null) {
            mActivity.getTerminalView().removeCallbacks(mShowSoftKeyboardRunnable);
        }
        cancelPendingKeyChord();
        TerminalKeyBindingResolver.getInstance().clearModes();
        mKeyChordHandler.removeCallbacks(mKeyModeTimeout);
    }

    /**
     * Should be called when mActivity.reloadProperties() is called
     */
    public void onReloadProperties() {
        setSessionShortcuts();
        cancelPendingKeyChord();
        mKeyChordHandler.removeCallbacks(mKeyModeTimeout);
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.reloadUserBindings();
        if (!resolver.getConfigErrors().isEmpty()) {
            for (String error : resolver.getConfigErrors())
                Logger.logError(LOG_TAG, "Binding config: " + error);
            mActivity.showToast(mActivity.getResources().getQuantityString(
                R.plurals.terminal_binding_config_errors, resolver.getConfigErrors().size(),
                resolver.getConfigErrors().size()), true);
        }
    }

    /**
     * Should be called when mActivity.reloadActivityStyling() is called
     */
    public void onReloadActivityStyling() {
        // Show the soft keyboard if required
        setSoftKeyboardState(false, true);
        // Start terminal cursor blinking if enabled
        setTerminalCursorBlinkerState(true);
    }

    /**
     * Should be called when {@link com.termux.view.TerminalView#mEmulator} is set
     */
    @Override
    public void onEmulatorSet() {
        mActivity.requestTerminalFlushDockGeometryUpdate();
        if (!mTerminalCursorBlinkerStateAlreadySet) {
            // Start terminal cursor blinking if enabled
            // We need to wait for the first session to be attached that's set in
            // TermuxActivity.onServiceConnected() and then the multiple calls to TerminalView.updateSize()
            // where the final one eventually sets the mEmulator when width/height is not 0. Otherwise
            // blinker will not start again if TermuxActivity is started again after exiting it with
            // double back press. Check TerminalView.setTerminalCursorBlinkerState().
            setTerminalCursorBlinkerState(true);
            mTerminalCursorBlinkerStateAlreadySet = true;
        }
    }

    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            boolean increase = scale > 1.f;
            changeFontSize(increase);
            return 1.0f;
        }
        return scale;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        TerminalEmulator term = mActivity.getCurrentSession().getEmulator();
        int[] tappedColumnAndRow = mActivity.getTerminalView().getColumnAndRow(e, true);
        String hyperlink = term.getHyperlinkUriAt(tappedColumnAndRow[1], tappedColumnAndRow[0]);
        if (hyperlink != null) {
            // An OSC 8 link was tapped. Confirm before acting on it: unlike the URL regex below, the
            // target is chosen by the application and need not resemble the text that was tapped.
            showHyperlinkDialog(hyperlink);
            return;
        }
        if (mActivity.getProperties().shouldOpenTerminalTranscriptURLOnClick()) {
            int[] columnAndRow = mActivity.getTerminalView().getColumnAndRow(e, true);
            String wordAtTap = term.getScreen().getWordAtLocation(columnAndRow[0], columnAndRow[1]);
            LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(wordAtTap);
            if (!urlSet.isEmpty()) {
                String url = (String) urlSet.iterator().next();
                ShareUtils.openUrl(mActivity, url);
                return;
            }
        }
        if (!term.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (isInAppKeyboardEnabled()) {
                mInAppKeyboardController.show(ShowReason.TERMINAL_TAP);
                suppressSystemImeForInAppKeyboard();
                return;
            }
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
                showSystemSoftKeyboard(mActivity.getTerminalView());
            else
                Logger.logVerbose(LOG_TAG, "Not showing soft keyboard onSingleTapUp since its disabled");
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return mActivity.getProperties().isBackKeyTheEscapeKey();
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return mActivity.getProperties().isEnforcingCharBasedInput();
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return mActivity.getProperties().isUsingCtrlSpaceWorkaround();
    }

    @Override
    public boolean isTerminalViewSelected() {
        return mActivity.getTerminalToolbarViewPager() == null || mActivity.isTerminalViewSelected() || mActivity.getTerminalView().hasFocus();
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        // Disable drawer while copying.
        mActivity.getDrawer().setDrawerLockMode(copyMode ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession currentSession) {
        TerminalKeyInspector inspector = TerminalKeyInspector.active();
        if (inspector != null)
            inspector.recordEvent(e, true);
        // An open rename chip is a modal editor over one surface, so it outranks every other
        // consumer: while it is up, every stroke belongs to the name being typed.
        if (mActivity.handleTerminalRenameKey(keyCode, e))
            return true;
        // Back for the widget pane and the FULL status pane. Same order as onBackPressed(), and the
        // same reason the drawer has a claim below: on a device the back key is consumed in this
        // channel and never reaches onBackPressed().
        if (mActivity.handleOverlayPaneKey(keyCode, e))
            return true;
        // The palette overlay claims typing before the terminal writes it, the same point the
        // in-app keyboard's interceptor sits at. Checked first so nothing else can consume esc.
        if (mActivity.handleCommandPaletteKey(keyCode, e))
            return true;
        // The sheet plane, after the palette so it can never swallow the escape stroke the palette
        // is checked first for, and before the drawer, which a sheet closes as it opens. On a device
        // back is consumed here and never reaches onBackPressed(), so the plane needs both routes.
        if (mActivity.handleTerminalSheetKey(keyCode, e))
            return true;
        // After the palette (which can be summoned over the drawer and therefore outranks it) and
        // before the app-search hook, which reads the terminal's own input line — a line nothing
        // typed into the drawer ever reaches.
        if (mActivity.handleAppDrawerKey(keyCode, e))
            return true;
        if (mActivity.handleTerminalAppSearchKey(keyCode))
            return true;
        if (mSuggestionBarCallback != null && mActivity.shouldProcessSuggestionBarKeyEvent(keyCode)) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                // TerminalView invokes the client before writing the backspace; refresh from the
                // emulator on the next main-loop turn so deleting '%' removes focus immediately.
                TerminalView terminalView = mActivity.getTerminalView();
                if (terminalView != null)
                    terminalView.post(() ->
                        mSuggestionBarCallback.reloadSuggestionBar(true, false));
            } else {
                mSuggestionBarCallback.reloadSuggestionBar(false, keyCode == KeyEvent.KEYCODE_ENTER);
            }
        }
        if (handleVirtualKeys(keyCode, e, true))
            return true;
        if (keyCode == KeyEvent.KEYCODE_ENTER && !currentSession.isRunning()) {
            mTermuxTerminalSessionActivityClient.removeFinishedSession(currentSession);
            return true;
        }
        return handleRegistryKeybinds(e);
    }

    /**
     * Resolves a {@code Ctrl+Alt(+Shift)} stroke through the registry and runs the
     * matching action.
     *
     * <p>This replaced two separate chains: the multiplexer {@code switch} and the
     * legacy {@code Ctrl+Alt}+character sequence. Both are now expressed as
     * {@code defaultBindings} in {@link LauncherToolRegistry}, with
     * {@link LauncherToolRegistry.BindingCondition} covering the strokes that meant
     * different things depending on whether split panes were on.
     *
     * <p>Two behaviors are preserved on purpose:
     *
     * <ul>
     *   <li>Every {@code Ctrl+Alt} combination is swallowed while hardware
     *       shortcuts are enabled, matched or not, exactly as the legacy chain did.
     *       Letting unmatched combinations through to the shell would be a
     *       user-visible change.
     *   <li>Pane focus and resize still report whether they consumed the stroke, so
     *       an unconsumed arrow is not silently eaten.
     * </ul>
     *
     * <p>One intentional change: matching is now on key code rather than
     * {@code getUnicodeChar}, so the binds keep working on non-Latin layouts where
     * the legacy chain quietly did nothing.
     *
     * <p>Known limitation, deliberately not worked around: this needs a real key
     * code and metaState, so it is reachable from the hardware route only. An IME
     * that emits Ctrl combinations as raw control characters lands in
     * {@link #onCodePoint} instead, where only the legacy session shortcuts match;
     * Alt is not recoverable from text at all. Synthesizing key events from
     * committed text would be guesswork, so the registry binds simply do not fire
     * from such a keyboard.
     */
    private boolean handleRegistryKeybinds(KeyEvent e) {
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        if (mActivity.getProperties().areHardwareKeyboardShortcutsDisabled()) {
            if (resolver.cancelPendingSequence()) clearPendingKeyChordUi();
            return false;
        }

        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        TerminalKeyBindingResolver.Step step = resolver.advance(e, dispatcher.actionContext());
        if (step.kind == TerminalKeyBindingResolver.Step.Kind.NONE) {
            // Preserve the historical Termux contract: unmatched Ctrl+Alt strokes
            // are swallowed while hardware shortcuts are enabled.
            return e.isAltPressed() && e.isCtrlPressed();
        }
        if (step.kind == TerminalKeyBindingResolver.Step.Kind.PASSTHROUGH)
            return false;
        if (step.kind == TerminalKeyBindingResolver.Step.Kind.IGNORED) {
            refreshKeyModeUi(resolver);
            return true;
        }
        if (step.kind == TerminalKeyBindingResolver.Step.Kind.PENDING) {
            mKeyChordHandler.removeCallbacks(mKeyChordTimeout);
            mKeyChordHandler.postDelayed(mKeyChordTimeout, KEY_CHORD_TIMEOUT_MS);
            mKeyChordOverlay.show(step.pendingSequence);
            return true;
        }
        clearPendingKeyChordUi();
        if (step.kind == TerminalKeyBindingResolver.Step.Kind.CANCELLED) {
            mActivity.getWindow().getDecorView().playSoundEffect(
                android.view.SoundEffectConstants.CLICK);
            refreshKeyModeUi(resolver);
            return true;
        }

        TerminalKeyBindingResolver.Match match = step.match;
        if (match == null) return true;
        TerminalKeyInspector inspector = TerminalKeyInspector.active();
        if (inspector != null)
            inspector.recordBinding(match == null ? null : match.stroke, match == null ? null : match.toolName);
        if (match == null)
            return true; // unbound Ctrl+Alt stroke: swallowed, as before

        boolean handled = runMatch(resolver, dispatcher, match);
        resolver.afterMatch(match);
        refreshKeyModeUi(resolver);
        return handled;
    }

    /**
     * Runs a {@code tool:<id>} key from the in-app keyboard layout.
     *
     * <p>The layout file names the registry tool directly, so a key slot reaches the same
     * dispatcher a keybind and a palette row do — there is no keyboard-side table of
     * supported actions to extend.
     */
    public void runLauncherTool(@NonNull String toolId) {
        TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
        TerminalKeyInspector inspector = TerminalKeyInspector.active();
        if (inspector != null)
            inspector.recordBinding(KeyValue.LAUNCHER_TOOL_PREFIX + toolId, toolId);
        JSONObject result = dispatcher.execute(toolId, new JSONObject());
        if (!result.optBoolean("ok", false))
            Logger.logWarn(LOG_TAG, "Keyboard tool " + toolId + " failed: "
                + result.optString("message"));
    }

    /** Runs every action of a resolved binding, in the order the config declared them. */
    private boolean runMatch(@NonNull TerminalKeyBindingResolver resolver,
                             @NonNull TerminalActionDispatcher dispatcher,
                             @NonNull TerminalKeyBindingResolver.Match match) {
        boolean handled = false;
        for (TerminalBindingConfig.Action action : match.actions) {
            if (action.type == TerminalBindingConfig.ActionType.PUSH_MODE) {
                handled |= resolver.pushMode(action.value);
                continue;
            }
            if (action.type == TerminalBindingConfig.ActionType.POP_MODE) {
                handled |= resolver.popMode();
                continue;
            }
            if (action.type == TerminalBindingConfig.ActionType.SEND_TEXT) {
                TerminalSession session = mActivity.getCurrentSession();
                if (session == null) {
                    Logger.logWarn(LOG_TAG, "Binding " + match.stroke + " cannot send text: no session");
                } else {
                    session.write(action.value);
                }
                handled = true;
                continue;
            }
            if (action.type == TerminalBindingConfig.ActionType.SEND_KEY) {
                TerminalSession session = mActivity.getCurrentSession();
                String encoded = session == null ? null
                    : TerminalBindingKeyEncoder.encode(action.value, session.getEmulator());
                if (encoded == null) {
                    Logger.logWarn(LOG_TAG, "Binding " + match.stroke
                        + " cannot encode send-key " + action.value);
                } else {
                    session.write(encoded);
                }
                handled = true;
                continue;
            }

            JSONObject arguments = mergeArguments(action.arguments, match.arguments);
            JSONObject result = dispatcher.execute(action.value, arguments);
            if (!result.optBoolean("ok", false)) {
                String message = result.optString("message");
                Logger.logWarn(LOG_TAG, "Binding " + match.stroke + " -> " + action.value
                    + " failed: " + message);
                // Say so on screen too: a swallowed stroke that logs and shows nothing is how a
                // broken binding passes for an unbound one.
                mKeyChordOverlay.showFailure(match.stroke,
                    message.isEmpty() ? action.value : message);
                handled = true;
                continue;
            }
            handled |= result.optBoolean("handled", true);
        }
        return handled;
    }

    /** Arguments the stroke implies, overridden by the ones the config spelled out. */
    @NonNull
    private static JSONObject mergeArguments(@NonNull JSONObject configured,
                                             @NonNull JSONObject derived) {
        JSONObject result = new JSONObject();
        copyJson(derived, result);
        copyJson(configured, result);
        return result;
    }

    private static void copyJson(@NonNull JSONObject source, @NonNull JSONObject target) {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                target.put(key, source.get(key));
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelPendingKeyChord() {
        TerminalKeyBindingResolver.getInstance().cancelPendingSequence();
        clearPendingKeyChordUi();
    }

    private void clearPendingKeyChordUi() {
        mKeyChordHandler.removeCallbacks(mKeyChordTimeout);
        mKeyChordOverlay.hide();
    }

    private void refreshKeyModeUi(@NonNull TerminalKeyBindingResolver resolver) {
        mKeyChordHandler.removeCallbacks(mKeyModeTimeout);
        String mode = resolver.getCurrentMode();
        if (mode.isEmpty()) {
            mKeyChordOverlay.hide();
            return;
        }
        mKeyChordOverlay.showMode(mode);
        long timeout = resolver.getCurrentModeTimeoutMillis();
        if (timeout > 0) mKeyChordHandler.postDelayed(mKeyModeTimeout, timeout);
    }

    private void expireKeyMode() {
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        resolver.popCurrentModeOnTimeout();
        refreshKeyModeUi(resolver);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        TerminalKeyInspector inspector = TerminalKeyInspector.active();
        if (inspector != null)
            inspector.recordEvent(e, false);
        // Swallow the release of a stroke the palette consumed on the way down.
        if (mActivity.isCommandPaletteOpen())
            return true;
        if (mActivity.isFolderRenameActive())
            return true;
        // The release of a back press a pane consumed on the way down.
        if (mActivity.consumeOverlayPaneKeyUp(keyCode))
            return true;
        // Same for the sheet plane: onKeyDown claimed the press, and a release let through on its
        // own would reach the shell behind a modal surface.
        if (mActivity.isTerminalSheetOpen())
            return true;
        // Same for the drawer: onKeyDown claimed the press, and a release let through on its own
        // would reach the shell behind a full-screen plane.
        if (mActivity.isAppDrawerOpen())
            return true;
        // If emulator is not set, like if bootstrap installation failed and user dismissed the error
        // dialog, then just exit the activity, otherwise they will be stuck in a broken state.
        if (keyCode == KeyEvent.KEYCODE_BACK && mActivity.getTerminalView().mEmulator == null) {
            mActivity.finishActivityIfNotFinishing();
            return true;
        }
        return handleVirtualKeys(keyCode, e, false);
    }

    /**
     * Handle dedicated volume buttons as virtual keys if applicable.
     */
    private boolean handleVirtualKeys(int keyCode, KeyEvent event, boolean down) {
        InputDevice inputDevice = event.getDevice();
        if (mActivity.getProperties().areVirtualVolumeKeysDisabled()) {
            return false;
        } else if (inputDevice != null && inputDevice.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
            // Do not steal dedicated buttons from a full external keyboard.
            return false;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            mVirtualControlKeyDown = down;
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mVirtualFnKeyDown = down;
            return true;
        }
        return false;
    }

    @Override
    public boolean readControlKey() {
        return readExtraKeysSpecialButton(SpecialButton.CTRL) || mVirtualControlKeyDown;
    }

    @Override
    public boolean readAltKey() {
        return readExtraKeysSpecialButton(SpecialButton.ALT);
    }

    @Override
    public boolean readShiftKey() {
        return readExtraKeysSpecialButton(SpecialButton.SHIFT);
    }

    @Override
    public boolean readFnKey() {
        return readExtraKeysSpecialButton(SpecialButton.FN);
    }

    public boolean readExtraKeysSpecialButton(SpecialButton specialButton) {
        if (mActivity.getExtraKeysView() == null)
            return false;
        Boolean state = mActivity.getExtraKeysView().readSpecialButton(specialButton, true);
        if (state == null) {
            Logger.logError(LOG_TAG, "Failed to read an unregistered " + specialButton + " special button value from extra keys.");
            return false;
        }
        return state;
    }

    /**
     * Where the long press that started this interaction landed, in screen coordinates.
     *
     * <p>The action menu opens at the finger, but it is not the long press that opens it: the press
     * starts text selection, and the menu arrives one step later from the selection toolbar's
     * More button, which carries no coordinates of its own. So the point is kept here.
     */
    @androidx.annotation.Nullable private PointF mLastLongPressOnScreen;

    @Override
    public boolean onLongPress(MotionEvent event) {
        mLastLongPressOnScreen = new PointF(event.getRawX(), event.getRawY());
        return false;
    }

    @Override
    public boolean onShowContextMenu(TerminalView view) {
        return mActivity.showTerminalActionSheet(mLastLongPressOnScreen);
    }

    @Override
    public boolean onCodePoint(final int codePoint, boolean ctrlDown, TerminalSession session) {
        TerminalKeyInspector inspector = TerminalKeyInspector.active();
        if (inspector != null)
            inspector.recordCodePoint(codePoint, ctrlDown);
        // The rename chip's twin of its onKeyDown hook, in the same order: it outranks the rest.
        if (mActivity.handleTerminalRenameCodePoint(codePoint, ctrlDown))
            return true;
        // The twin of the palette hook in onKeyDown, and checked first for the same reason. A
        // system IME commits ordinary characters through the input connection without ever sending
        // a key event, so this is the only route by which typing reaches the overlay from one.
        if (mActivity.handleCommandPaletteCodePoint(codePoint, ctrlDown))
            return true;
        // The sheet plane's twin of the same hook, in the same order as onKeyDown.
        if (mActivity.handleTerminalSheetCodePoint(codePoint, ctrlDown))
            return true;
        // The drawer's twin of the same hook, in the same order as onKeyDown: after the palette,
        // before the enter-only app-search hook below.
        if (mActivity.handleAppDrawerCodePoint(codePoint, ctrlDown))
            return true;
        // The AOSP keyboard and its descendants send ⏎ as text rather than as KEYCODE_ENTER — see
        // TerminalView#sendTextToTerminal — so the key-code-only app-search hook needs this twin
        // too. Only a consumed enter is claimed; an unconsumed one still reaches the shell.
        if ((codePoint == '\r' || codePoint == '\n')
            && mActivity.handleTerminalAppSearchKey(KeyEvent.KEYCODE_ENTER))
            return true;
        if (mVirtualFnKeyDown) {
            int resultingKeyCode = -1;
            int resultingCodePoint = -1;
            boolean altDown = false;
            int lowerCase = Character.toLowerCase(codePoint);
            switch(lowerCase) {
                // Arrow keys.
                case 'w':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP;
                    break;
                case 'a':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                    break;
                case 's':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                    break;
                case 'd':
                    resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                    break;
                // Page up and down.
                case 'p':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP;
                    break;
                case 'n':
                    resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN;
                    break;
                // Some special keys:
                case 't':
                    resultingKeyCode = KeyEvent.KEYCODE_TAB;
                    break;
                case 'i':
                    resultingKeyCode = KeyEvent.KEYCODE_INSERT;
                    break;
                case 'h':
                    resultingCodePoint = '~';
                    break;
                // Special characters to input.
                case 'u':
                    resultingCodePoint = '_';
                    break;
                case 'l':
                    resultingCodePoint = '|';
                    break;
                // Function keys.
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    resultingKeyCode = (codePoint - '1') + KeyEvent.KEYCODE_F1;
                    break;
                case '0':
                    resultingKeyCode = KeyEvent.KEYCODE_F10;
                    break;
                // Other special keys.
                case 'e':
                    resultingCodePoint = /*Escape*/
                    27;
                    break;
                case '.':
                    resultingCodePoint = /*^.*/
                    28;
                    break;
                // alt+b, jumping backward in readline.
                case 'b':
                // alf+f, jumping forward in readline.
                case 'f':
                case // alt+x, common in emacs.
                'x':
                    resultingCodePoint = lowerCase;
                    altDown = true;
                    break;
                // Volume control.
                case 'v':
                    resultingCodePoint = -1;
                    AudioManager audio = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
                    audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;
                // Writing mode:
                case 'q':
                case 'k':
                    mActivity.toggleTerminalToolbar();
                    // force disable fn key down to restore keyboard input into terminal view, fixes termux/termux-app#1420
                    mVirtualFnKeyDown = false;
                    break;
            }
            if (resultingKeyCode != -1) {
                TerminalEmulator term = session.getEmulator();
                session.write(KeyHandler.getCode(resultingKeyCode, 0, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode()));
            } else if (resultingCodePoint != -1) {
                session.writeCodePoint(altDown, resultingCodePoint);
            }
            return true;
        } else if (ctrlDown) {
            if (codePoint == 106 && /* Ctrl+j or \n */
            !session.isRunning()) {
                mTermuxTerminalSessionActivityClient.removeFinishedSession(session);
                return true;
            }
            List<KeyboardShortcut> shortcuts = mSessionShortcuts;
            if (shortcuts != null && !shortcuts.isEmpty()) {
                int codePointLowerCase = Character.toLowerCase(codePoint);
                for (int i = shortcuts.size() - 1; i >= 0; i--) {
                    KeyboardShortcut shortcut = shortcuts.get(i);
                    if (codePointLowerCase == shortcut.codePoint) {
                        switch(shortcut.shortcutAction) {
                            case TermuxPropertyConstants.ACTION_SHORTCUT_CREATE_SESSION:
                                mTermuxTerminalSessionActivityClient.addNewSession(false, null);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_NEXT_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(true);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_PREVIOUS_SESSION:
                                mTermuxTerminalSessionActivityClient.switchToSession(false);
                                return true;
                            case TermuxPropertyConstants.ACTION_SHORTCUT_RENAME_SESSION:
                                if (mActivity.isSplitPanesEnabled())
                                    mActivity.promptCurrentSessionRename();
                                else
                                    mTermuxTerminalSessionActivityClient.promptCurrentPaneRename();
                                return true;
                        }
                    }
                }
            }
        }
        if (mSuggestionBarCallback != null && mActivity.shouldProcessSuggestionBarCodePoint(codePoint, ctrlDown)) {
            char[] chars = Character.toChars(codePoint);
            if (chars.length == 1) {
                mSuggestionBarCallback.reloadSuggestionBar(chars[0]);
            }
        }
        return false;
    }

    /**
     * Set the terminal sessions shortcuts.
     */
    private void setSessionShortcuts() {
        mSessionShortcuts = new ArrayList<>();
        // The {@link TermuxPropertyConstants#MAP_SESSION_SHORTCUTS} stores the session shortcut key and action pair
        for (Map.Entry<String, Integer> entry : TermuxPropertyConstants.MAP_SESSION_SHORTCUTS.entrySet()) {
            // The mMap stores the code points for the session shortcuts while loading properties
            Integer codePoint = (Integer) mActivity.getProperties().getInternalPropertyValue(entry.getKey(), true);
            // If codePoint is null, then session shortcut did not exist in properties or was invalid
            // as parsed by {@link #getCodePointForSessionShortcuts(String,String)}
            // If codePoint is not null, then get the action for the MAP_SESSION_SHORTCUTS key and
            // add the code point to sessionShortcuts
            if (codePoint != null)
                mSessionShortcuts.add(new KeyboardShortcut(codePoint, entry.getValue()));
        }
    }

    public void changeFontSize(boolean increase) {
        TerminalSession current = mActivity.getCurrentSession();
        boolean scratchpad = current != null && TerminalPaneController
            .isScratchpadShellName(current.mSessionName);
        if (scratchpad) {
            mActivity.getPreferences().changeScratchpadFontSize(increase);
            TerminalView view = mActivity.getTerminalView();
            if (view != null) view.setTextSize(mActivity.getPreferences().getScratchpadFontSize());
        } else {
            // Zoom pins a per-pane size on the focused pane instead of moving the app-wide
            // default, so no other pane — in this window or any other — changes with it. Panes
            // that were never zoomed keep following the default from settings.
            int size = mActivity.getActivePaneFontSize();
            if (size <= 0) size = mActivity.getPreferences().getFontSize();
            size = mActivity.getPreferences().stepFontSize(size, increase);
            if (!mActivity.setActivePaneFontSize(size)) {
                // No pane controller (compatibility mode): the single view follows the default.
                mActivity.getPreferences().setFontSize(size);
                TerminalView view = mActivity.getTerminalView();
                if (view != null) view.setTextSize(size);
            }
        }
        mActivity.requestTerminalFlushDockGeometryUpdate();
    }

    /**
     * Called when user requests the soft keyboard to be toggled via "KEYBOARD" toggle button in
     * drawer or extra keys, or with ctrl+alt+k hardware keyboard shortcut.
     */
    public void onToggleSoftKeyboardRequest() {
        if (isInAppKeyboardEnabled()) {
            mInAppKeyboardController.toggle(ToggleReason.KEYBOARD_ACTION);
            suppressSystemImeForInAppKeyboard();
            return;
        }
        // If soft keyboard toggle behaviour is enable/disabled
        if (mActivity.getProperties().shouldEnableDisableSoftKeyboardOnToggle()) {
            // If soft keyboard is visible
            if (!KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
                Logger.logVerbose(LOG_TAG, "Disabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(false);
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            } else {
                // Show with a delay, otherwise pressing keyboard toggle won't show the keyboard after
                // switching back from another app if keyboard was previously disabled by user.
                // Also request focus, since it wouldn't have been requested at startup by
                // setSoftKeyboardState if keyboard was disabled. #2112
                Logger.logVerbose(LOG_TAG, "Enabling soft keyboard on toggle");
                mActivity.getPreferences().setSoftKeyboardEnabled(true);
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                if (mShowSoftKeyboardWithDelayOnce) {
                    mShowSoftKeyboardWithDelayOnce = false;
                    mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(),
                        KEYBOARD_TOGGLE_RETRY_DELAY_MS);
                    mActivity.getTerminalView().requestFocus();
                } else
                    showSystemSoftKeyboard(mActivity.getTerminalView());
            }
        } else // If soft keyboard toggle behaviour is show/hide
        {
            // If soft keyboard is disabled by user for Termux
            if (!mActivity.getPreferences().isSoftKeyboardEnabled()) {
                Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard on toggle");
                KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            } else {
                Logger.logVerbose(LOG_TAG, "Showing/Hiding soft keyboard on toggle");
                KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
                mActivity.onSystemImeRequested();
                KeyboardUtils.toggleSoftKeyboard(mActivity);
            }
        }
    }

    public void setSoftKeyboardState(boolean isStartup, boolean isReloadTermuxProperties) {
        if (isInAppKeyboardEnabled()) {
            suppressSystemImeForInAppKeyboard();
            return;
        }
        boolean noShowKeyboard = false;
        // Requesting terminal view focus is necessary regardless of if soft keyboard is to be
        // disabled or hidden at startup, otherwise if hardware keyboard is attached and user
        // starts typing on hardware keyboard without tapping on the terminal first, then a colour
        // tint will be added to the terminal as highlight for the focussed view. Test with a light
        // theme. For android 8.+, the "defaultFocusHighlightEnabled" attribute is also set to false
        // in TerminalView layout to fix the issue.
        // If soft keyboard is disabled by user for Termux (check function docs for Termux behaviour info)
        if (KeyboardUtils.shouldSoftKeyboardBeDisabled(mActivity, mActivity.getPreferences().isSoftKeyboardEnabled(), mActivity.getPreferences().isSoftKeyboardEnabledOnlyIfNoHardware())) {
            Logger.logVerbose(LOG_TAG, "Maintaining disabled soft keyboard");
            KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
            mActivity.getTerminalView().requestFocus();
            noShowKeyboard = true;
            // Delay is only required if onCreate() is called like when Termux app is exited with
            // double back press, not when Termux app is switched back from another app and keyboard
            // toggle is pressed to enable keyboard
            if (isStartup && mActivity.isOnResumeAfterOnCreate())
                mShowSoftKeyboardWithDelayOnce = true;
        } else {
            // Set flag to automatically push up TerminalView when keyboard is opened instead of showing over it
            KeyboardUtils.setSoftInputModeAdjustResize(mActivity);
            // Clear any previous flags to disable soft keyboard in case setting updated
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
            // If soft keyboard is to be hidden on startup
            if (isStartup && mActivity.getProperties().shouldSoftKeyboardBeHiddenOnStartup()) {
                Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on startup");
                // Required to keep keyboard hidden when Termux app is switched back from another app
                KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(mActivity);
                KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView());
                mActivity.getTerminalView().requestFocus();
                noShowKeyboard = true;
                // Required to keep keyboard hidden on app startup
                mShowSoftKeyboardIgnoreOnce = true;
            }
        }
        mActivity.getTerminalView().setOnFocusChangeListener(mTerminalFocusChangeListener);
        // Do not force show soft keyboard if termux-reload-settings command was run with hardware keyboard
        // or soft keyboard is to be hidden or is disabled
        if (!isReloadTermuxProperties && !noShowKeyboard) {
            // Request focus for TerminalView
            // Also show the keyboard, since onFocusChange will not be called if TerminalView already
            // had focus on startup to show the keyboard, like when opening url with context menu
            // "Select URL" long press and returning to Termux app with back button. This
            // will also show keyboard even if it was closed before opening url. #2111
            Logger.logVerbose(LOG_TAG, "Requesting TerminalView focus and showing soft keyboard");
            mActivity.getTerminalView().requestFocus();
            if (mActivity.shouldDelaySoftKeyboardShowOnResume()) {
                mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(),
                    KEYBOARD_RESUME_FIRST_RETRY_DELAY_MS);
                mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(),
                    KEYBOARD_RESUME_SECOND_RETRY_DELAY_MS);
            } else {
                showSystemSoftKeyboard(mActivity.getTerminalView());
                mActivity.getTerminalView().postDelayed(getShowSoftKeyboardRunnable(),
                    KEYBOARD_STANDARD_RETRY_DELAY_MS);
            }
        }
    }

    private void onTerminalFocusChanged(View view, boolean hasFocus) {
        // Force show soft keyboard if TerminalView or toolbar text input view has
        // focus and close it if they don't.
        boolean textInputViewHasFocus = false;
        final EditText textInputView = mActivity.findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null)
            textInputViewHasFocus = textInputView.hasFocus();
        if (textInputViewHasFocus) {
            if (mShowSoftKeyboardIgnoreOnce) {
                mShowSoftKeyboardIgnoreOnce = false;
                return;
            }
            Logger.logVerbose(LOG_TAG, "Showing soft keyboard for toolbar text input on focus change");
            showSystemSoftKeyboard(textInputView);
            return;
        }
        if (hasFocus) {
            if (mShowSoftKeyboardIgnoreOnce) {
                mShowSoftKeyboardIgnoreOnce = false;
                return;
            }
            Logger.logVerbose(LOG_TAG, "Showing soft keyboard on focus change");
        } else {
            Logger.logVerbose(LOG_TAG, "Hiding soft keyboard on focus change");
        }
        KeyboardUtils.setSoftKeyboardVisibility(getShowSoftKeyboardRunnable(), mActivity,
            mActivity.getTerminalView(), hasFocus);
    }

    private Runnable getShowSoftKeyboardRunnable() {
        if (mShowSoftKeyboardRunnable == null) {
            mShowSoftKeyboardRunnable = () -> {
                // A runnable posted before embedded mode was enabled must never leak the system IME.
                if (isInAppKeyboardEnabled()) {
                    suppressSystemImeForInAppKeyboard();
                    return;
                }
                showSystemSoftKeyboard(mActivity.getTerminalView());
            };
        }
        return mShowSoftKeyboardRunnable;
    }

    private void showSystemSoftKeyboard(@NonNull View target) {
        mActivity.onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(mActivity, target);
    }

    private boolean isInAppKeyboardEnabled() {
        return mInAppKeyboardController != null && mInAppKeyboardController.isEnabled();
    }

    private void suppressSystemImeForInAppKeyboard() {
        TermuxInAppKeyboard controller = mInAppKeyboardController;
        if (controller == null || !controller.isEnabled())
            return;

        if (mShowSoftKeyboardRunnable != null)
            mActivity.getTerminalView().removeCallbacks(mShowSoftKeyboardRunnable);
        mShowSoftKeyboardIgnoreOnce = false;
        mShowSoftKeyboardWithDelayOnce = false;

        // The controller applies the same policy for lifecycle calls made directly by WP4.
        controller.suppressSystemIme();
    }

    public void setTerminalCursorBlinkerState(boolean start) {
        if (start) {
            // If set/update the cursor blinking rate is successful, then enable cursor blinker
            if (mActivity.getTerminalView().setTerminalCursorBlinkerRate(mActivity.getProperties().getTerminalCursorBlinkRate()))
                mActivity.getTerminalView().setTerminalCursorBlinkerState(true, true);
            else
                Logger.logError(LOG_TAG, "Failed to start cursor blinker");
        } else {
            // Disable cursor blinker
            mActivity.getTerminalView().setTerminalCursorBlinkerState(false, true);
        }
    }

    public void shareSessionTranscript() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null)
            return;
        // See https://github.com/termux/termux-app/issues/1166.
        transcriptText = DataUtils.getTruncatedCommandOutput(transcriptText, DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES, false, true, false).trim();
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_transcript), transcriptText, mActivity.getString(R.string.title_share_transcript_with));
    }

    public void shareSelectedText() {
        String selectedText = mActivity.getTerminalView().getStoredSelectedText();
        if (DataUtils.isNullOrEmpty(selectedText))
            return;
        ShareUtils.shareText(mActivity, mActivity.getString(R.string.title_share_selected_text), selectedText, mActivity.getString(R.string.title_share_selected_text_with));
    }

    /**
     * The URI schemes an OSC 8 hyperlink may be handed to another app with. Anything else - and in
     * particular {@code file:}, which either leaks an app private path or throws
     * {@code FileUriExposedException} - can only be copied to the clipboard.
     */
    private static final Set<String> OPENABLE_HYPERLINK_SCHEMES = new HashSet<>(Arrays.asList(
        "http", "https", "mailto", "tel", "sms", "geo", "ftp", "ftps"));

    /**
     * Ask what to do with a tapped OSC 8 hyperlink, showing its full target so that a link whose text
     * and destination disagree is visible as such before anything is opened.
     */
    private void showHyperlinkDialog(String uri) {
        String scheme = Uri.parse(uri).getScheme();
        boolean openable = scheme != null && OPENABLE_HYPERLINK_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
        TerminalSheetController sheet = mActivity.getTerminalSheetController();
        LinearLayout body = TerminalSheetViews.body(mActivity);
        TerminalSheetViews.addMessage(body, uri);
        LinearLayout actions = TerminalSheetViews.addActionRow(body);
        TerminalSheetViews.addAction(actions, mActivity.getString(android.R.string.cancel),
            sheet::dismiss);
        TerminalSheetViews.addAction(actions, mActivity.getString(R.string.action_hyperlink_copy),
            () -> {
                sheet.dismiss();
                ShareUtils.copyTextToClipboard(mActivity, uri,
                    mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
            });
        if (openable) {
            TerminalSheetViews.addAction(actions,
                mActivity.getString(R.string.action_hyperlink_open), () -> {
                    sheet.dismiss();
                    ShareUtils.openUrl(mActivity, uri);
                });
        }
        sheet.show(mActivity.getString(R.string.title_hyperlink_dialog), body);
    }

    public void showUrlSelection() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        String text = ShellUtils.getTerminalSessionTranscriptText(session, true, true);
        LinkedHashSet<CharSequence> urlSet = TermuxUrlUtils.extractUrls(text);
        TerminalSheetController sheet = mActivity.getTerminalSheetController();
        String title = mActivity.getString(R.string.action_select_url);
        if (urlSet.isEmpty()) {
            LinearLayout notice = TerminalSheetViews.body(mActivity);
            TerminalSheetViews.addMessage(notice,
                mActivity.getString(R.string.title_select_url_none_found));
            sheet.show(title, notice);
            return;
        }
        final CharSequence[] urls = urlSet.toArray(new CharSequence[0]);
        // Latest first.
        Collections.reverse(Arrays.asList(urls));
        LinearLayout body = TerminalSheetViews.body(mActivity);
        // The instruction was the dialog's title; on a card it is the message, because the title
        // line is a single ellipsized row and this sentence is what tells the two gestures apart.
        TerminalSheetViews.addMessage(body, mActivity.getString(R.string.title_select_url_dialog));
        for (CharSequence entry : urls) {
            final String url = entry.toString();
            // Tap copies, long press opens — the same split the list dialog had.
            TextView row = TerminalSheetViews.addMenuRow(body, url, () -> {
                sheet.dismiss();
                ShareUtils.copyTextToClipboard(mActivity, url,
                    mActivity.getString(R.string.msg_select_url_copied_to_clipboard));
            });
            row.setOnLongClickListener(view -> {
                sheet.dismiss();
                ShareUtils.openUrl(mActivity, url);
                return true;
            });
        }
        sheet.show(title, TerminalSheetViews.wrapScrolling(body));
    }

    /** Show the keyboard-addressable URL/path/hash/line-reference hint picker. */
    public void showHintsOverlay() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null) return;
        String text = ShellUtils.getTerminalSessionTranscriptText(session, true, true);
        TerminalHintsOverlay.show(mActivity, text == null ? "" : text);
    }

    public void showScrollbackSearch() {
        TerminalView view = mActivity.getTerminalView();
        if (view != null) TerminalScrollbackSearchOverlay.show(mActivity, view);
    }

    public void reportIssueFromTranscript() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        final String transcriptText = ShellUtils.getTerminalSessionTranscriptText(session, false, true);
        if (transcriptText == null) return;

        MessageDialogUtils.showMessage(mActivity, TermuxConstants.TERMUX_APP_NAME + " Report Issue",
            mActivity.getString(R.string.msg_add_termux_debug_info),
            mActivity.getString(com.termux.shared.R.string.action_yes), (dialog, which) -> reportIssueFromTranscript(transcriptText, true),
            mActivity.getString(com.termux.shared.R.string.action_no), (dialog, which) -> reportIssueFromTranscript(transcriptText, false),
            null);
    }

    private void reportIssueFromTranscript(String transcriptText, boolean addTermuxDebugInfo) {
        Logger.showToast(mActivity, mActivity.getString(R.string.msg_generating_report), true);
        REPORT_EXECUTOR.execute(() -> {
            StringBuilder reportString = new StringBuilder();
            String title = TermuxConstants.TERMUX_APP_NAME + " Report Issue";
            reportString.append("## Transcript\n");
            reportString.append("\n").append(MarkdownUtils.getMarkdownCodeForString(transcriptText, true));
            reportString.append("\n##\n");
            if (addTermuxDebugInfo) {
                reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
            } else {
                reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(mActivity, TermuxUtils.AppInfoMode.TERMUX_PACKAGE));
            }
            reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(mActivity, true));
            if (TermuxBootstrap.isAppPackageManagerAPT()) {
                String termuxAptInfo = TermuxUtils.geAPTInfoMarkdownString(mActivity);
                if (termuxAptInfo != null)
                    reportString.append("\n\n").append(termuxAptInfo);
            }
            if (addTermuxDebugInfo) {
                String termuxDebugInfo = TermuxUtils.getTermuxDebugMarkdownString(mActivity);
                if (termuxDebugInfo != null)
                    reportString.append("\n\n").append(termuxDebugInfo);
            }
            String userActionName = UserAction.REPORT_ISSUE_FROM_TRANSCRIPT.getName();
            ReportInfo reportInfo = new ReportInfo(userActionName, TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY_NAME, title);
            reportInfo.setReportString(reportString.toString());
            reportInfo.setReportStringSuffix("\n\n" + TermuxUtils.getReportIssueMarkdownString(mActivity));
            reportInfo.setReportSaveFileLabelAndPath(userActionName, Environment.getExternalStorageDirectory() + "/" + FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));
            mActivity.runOnUiThread(() -> ReportActivity.startReportActivity(mActivity, reportInfo));
        });
    }

    public void doPaste() {
        TerminalSession session = mActivity.getCurrentSession();
        if (session == null)
            return;
        if (!session.isRunning())
            return;
        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        if (text != null)
            session.getEmulator().paste(text);
    }
}
