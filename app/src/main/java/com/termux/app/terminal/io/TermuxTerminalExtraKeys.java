package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;
import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;

    final TermuxTerminalViewClient mTermuxTerminalViewClient;

    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, TerminalView terminalView, TermuxTerminalViewClient termuxTerminalViewClient, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient,
     int i) {
        super(terminalView);
        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
        setExtraKeys(i);
    }

    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys(int i) {
        mExtraKeysInfo = null;
        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(i == 0 ? TermuxPropertyConstants.KEY_EXTRA_KEYS : TermuxPropertyConstants.KEY_EXTRA_KEYS2, false);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);
            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }
            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);
            try {
                mExtraKeysInfo = new ExtraKeysInfo(i == 0 ? TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS : TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys", true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        } else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else if (key != null && key.startsWith(LAUNCHER_TOOL_KEY_PREFIX)) {
            runLauncherToolKey(key.substring(LAUNCHER_TOOL_KEY_PREFIX.length()));
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

    /** Extra-keys entries prefixed with this run a registry tool instead of sending keys. */
    static final String LAUNCHER_TOOL_KEY_PREFIX = "tool:";

    /**
     * Runs a registry tool named by an extra key. The spec is
     * {@code <tool>[:<arg>=<value>[,<arg>=<value>...]]} so parameterized tools work from the
     * row — e.g. {@code tool:pane.move_to_edge:edge=left}. Failures only log: an extra key
     * whose tool is momentarily unavailable (no session yet, splits off) should stay silent
     * like a dead keystroke, not toast.
     */
    private void runLauncherToolKey(@NonNull String spec) {
        String toolName = spec;
        org.json.JSONObject arguments = new org.json.JSONObject();
        int colon = spec.indexOf(':');
        if (colon > 0) {
            toolName = spec.substring(0, colon);
            for (String pair : spec.substring(colon + 1).split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) continue;
                try {
                    arguments.put(pair.substring(0, equals).trim(),
                        pair.substring(equals + 1).trim());
                } catch (JSONException ignored) {
                }
            }
        }
        org.json.JSONObject result = com.termux.app.terminal.TerminalActionDispatcher
            .getInstance().execute(toolName, arguments);
        if (!result.optBoolean("ok", false)) {
            Logger.logWarn(LOG_TAG, "Extra key tool '" + toolName + "' failed: "
                + result.optString("message", result.toString()));
        }
    }
}
