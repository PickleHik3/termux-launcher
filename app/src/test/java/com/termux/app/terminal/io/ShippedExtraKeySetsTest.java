package com.termux.app.terminal.io;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Every set the app ships parses as a key row and names only tools the launcher has. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ShippedExtraKeySetsTest {

    private static final String[] SHIPPED = {
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT,
    };

    @Test
    public void everyShippedSetParsesAsOneRowOfKeys() throws Exception {
        for (String row : SHIPPED) {
            ExtraKeysInfo info = new ExtraKeysInfo(row, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            assertTrue(row, info.getMatrix().length == 1 && info.getMatrix()[0].length > 0);
        }
    }

    @Test
    public void everyToolInTheShippedSetsExists() {
        for (String row : SHIPPED) {
            for (java.util.List<ExtraKeysLayoutModel.Key> line : ExtraKeysLayoutModel.parse(row).rows()) {
                for (ExtraKeysLayoutModel.Key key : line) {
                    check(key.key);
                    if (key.popup != null) check(key.popup.key);
                }
            }
        }
    }

    private static void check(String keyValue) {
        if (!keyValue.startsWith(TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX)) return;
        String tool = ExtraKeyEligibility.toolNameOf(keyValue);
        assertNotNull(tool + " is not a launcher tool",
            LauncherToolRegistry.getInstance().getTool(tool));
    }
}
