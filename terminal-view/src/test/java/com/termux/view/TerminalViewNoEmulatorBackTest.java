package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A view with no terminal yet still hands the client the Back press, not only its release. The
 * press used to be swallowed here while the release went on to the client, which took a release
 * nobody had claimed for "leave the app" and finished the launcher under the Appearance editor.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TerminalViewNoEmulatorBackTest {

    @Test
    public void backDownReachesTheClientWithoutATerminal() {
        List<Integer> downs = new ArrayList<>();
        TerminalView view = viewWith(downs, true);

        boolean handled = view.onKeyDown(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));

        assertTrue(handled);
        assertEquals(1, downs.size());
    }

    @Test
    public void anUnclaimedBackIsLeftToTheActivity() {
        List<Integer> downs = new ArrayList<>();
        TerminalView view = viewWith(downs, false);

        boolean handled = view.onKeyDown(KeyEvent.KEYCODE_BACK,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));

        assertFalse("the activity tracks the press, as before", handled);
        assertEquals(1, downs.size());
    }

    @Test
    public void otherKeysStaySwallowedWithoutATerminal() {
        List<Integer> downs = new ArrayList<>();
        TerminalView view = viewWith(downs, true);

        assertTrue(view.onKeyDown(KeyEvent.KEYCODE_A,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)));
        assertTrue(downs.isEmpty());
    }

    private static TerminalView viewWith(List<Integer> downs, boolean claims) {
        TerminalView view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        TerminalViewClient client = (TerminalViewClient) Proxy.newProxyInstance(
            TerminalViewClient.class.getClassLoader(), new Class<?>[] {TerminalViewClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onKeyDown")) {
                    downs.add((Integer) args[0]);
                    return claims;
                }
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == float.class) return 0f;
                if (type == long.class) return 0L;
                return null;
            });
        view.setTerminalViewClient(client);
        return view;
    }
}
