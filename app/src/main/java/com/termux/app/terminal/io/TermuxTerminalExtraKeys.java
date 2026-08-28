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

    /**
     * The property behind each toolbar key page, in page order. A page exists only while its
     * property holds keys, so this is the whole vocabulary of pages the row editor can offer.
     */
    public static final String[] PAGE_PROPERTY_KEYS = {
        TermuxPropertyConstants.KEY_EXTRA_KEYS,
        TermuxPropertyConstants.KEY_EXTRA_KEYS2,
    };

    /** The default value of each page, index-aligned with {@link #PAGE_PROPERTY_KEYS}. */
    public static final String[] PAGE_DEFAULT_VALUES = {
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2,
    };

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
    /** True when this page holds no keys — the pager drops such a page instead of showing a blank. */
    public boolean isEmpty() {
        return mExtraKeysInfo == null || mExtraKeysInfo.getMatrix().length == 0;
    }

    private void setExtraKeys(int i) {
        mExtraKeysInfo = null;
        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String pageKey = PAGE_PROPERTY_KEYS[Math.max(0, Math.min(i, PAGE_PROPERTY_KEYS.length - 1))];
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(pageKey, false);
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
                mExtraKeysInfo = new ExtraKeysInfo(
                    PAGE_DEFAULT_VALUES[Math.max(0, Math.min(i, PAGE_DEFAULT_VALUES.length - 1))],
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES);
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
        if (key != null)
            mActivity.showExtraKeyPressReadout(pressReadoutLabel(key, ctrlDown, altDown, shiftDown, fnDown));
        if ("KEYBOARD".equals(key)) {
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mActivity.getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        } else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mActivity.getTerminalView();
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
     * What the A-Z row readout names for a press: latched modifiers spelled out before the key,
     * and a tool key by its registry title (the glyph on the cap is exactly what the readout is
     * there to explain) — falling back to the bare tool name for tools with no UI title.
     */
    @NonNull
    private String pressReadoutLabel(@NonNull String key, boolean ctrlDown, boolean altDown,
                                     boolean shiftDown, boolean fnDown) {
        if (key.startsWith(LAUNCHER_TOOL_KEY_PREFIX)) {
            String spec = key.substring(LAUNCHER_TOOL_KEY_PREFIX.length());
            int colon = spec.indexOf(':');
            String toolName = colon > 0 ? spec.substring(0, colon) : spec;
            com.termux.launcherctl.LauncherToolRegistry.ToolMetadata tool =
                com.termux.launcherctl.LauncherToolRegistry.getInstance().getTool(toolName);
            return tool != null && tool.titleRes != 0 ? mActivity.getString(tool.titleRes) : toolName;
        }
        StringBuilder label = new StringBuilder();
        if (ctrlDown) label.append("CTRL ");
        if (altDown) label.append("ALT ");
        if (shiftDown) label.append("SHIFT ");
        if (fnDown) label.append("FN ");
        return label.append(key).toString();
    }

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
