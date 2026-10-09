package com.termux.app.terminal;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The signal tools behind {@code /v1/notify}, {@code /v1/progress} and {@code /v1/clipboard}, and
 * the escape callbacks that share their one implementation ({@link ShellSignals}).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalActionDispatcherSignalsTest {

    private final TerminalActionDispatcher dispatcher = TerminalActionDispatcher.getInstance();
    private SignalHost host;
    private TermuxTerminalSessionActivityClient client;

    @Before
    public void attach() throws IOException {
        host = new SignalHost();
        client = new TermuxTerminalSessionActivityClient(FakeTerminalHost.testContext(), host);
        host.sessionClient = client;
        dispatcher.attach(host);
    }

    @After
    public void detach() {
        dispatcher.detach(host);
    }

    @Test
    public void theSignalToolsAreHandled() {
        assertTrue(TerminalActionDispatcher.handles("shell.notify"));
        assertTrue(TerminalActionDispatcher.handles("shell.progress"));
        assertTrue(TerminalActionDispatcher.handles("clipboard.write"));
        assertTrue(TerminalActionDispatcher.handles("clipboard.read"));
    }

    // --- progress ---

    @Test
    public void progressLandsOnTheCurrentPanesEmulatorAndRepaintsTheChips() throws JSONException {
        TerminalSession shell = host.addPane();
        host.currentSession = shell;

        JSONObject result = dispatcher.execute("shell.progress", new JSONObject().put("percent", 42));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(shell.mHandle, result.getString("pane"));
        assertEquals("normal", result.getString("state"));
        assertEquals(42, result.getInt("percent"));
        assertEquals(TerminalEmulator.PROGRESS_STATE_NORMAL, shell.getEmulator().getProgressState());
        assertEquals(42, shell.getEmulator().getProgressValue());
        assertEquals(1, host.windowBarRefreshes);

        // Like the escape, a state without a percentage keeps the last value.
        result = dispatcher.execute("shell.progress", new JSONObject().put("state", "error"));
        assertEquals(TerminalEmulator.PROGRESS_STATE_ERROR, shell.getEmulator().getProgressState());
        assertEquals(42, result.getInt("percent"));

        result = dispatcher.execute("shell.progress", new JSONObject().put("state", "clear"));
        assertEquals("clear", result.getString("state"));
        assertEquals(TerminalEmulator.PROGRESS_STATE_NONE, shell.getEmulator().getProgressState());
        assertEquals(0, shell.getEmulator().getProgressValue());
        assertEquals(3, host.windowBarRefreshes);
    }

    @Test
    public void progressGoesToTheNamedPaneAndRefusesBadInput() throws JSONException {
        TerminalSession current = host.addPane();
        TerminalSession other = host.addPane();
        host.currentSession = current;

        JSONObject result = dispatcher.execute("shell.progress",
            new JSONObject().put("state", "indeterminate").put("pane", other.mHandle));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(other.mHandle, result.getString("pane"));
        assertEquals(TerminalEmulator.PROGRESS_STATE_INDETERMINATE, other.getEmulator().getProgressState());
        assertEquals(TerminalEmulator.PROGRESS_STATE_NONE, current.getEmulator().getProgressState());

        assertEquals(400, dispatcher.execute("shell.progress", new JSONObject().put("state", "bogus")).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("shell.progress", new JSONObject().put("percent", 140)).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("shell.progress", new JSONObject()).getInt("_statusCode"));
        JSONObject missing = dispatcher.execute("shell.progress", new JSONObject().put("percent", 1).put("pane", "nope"));
        assertEquals(404, missing.getInt("_statusCode"));
        assertEquals("pane_not_found", missing.getString("error"));
    }

    @Test
    public void progressWithoutAnySessionSaysSo() throws JSONException {
        JSONObject result = dispatcher.execute("shell.progress", new JSONObject().put("percent", 5));
        assertEquals(409, result.getInt("_statusCode"));
        assertEquals("no_session", result.getString("error"));
    }

    @Test
    public void progressStillWorksWithTheLauncherOffScreen() throws JSONException {
        TerminalSession shell = host.addPane();
        host.currentSession = shell;
        host.visible = false;
        JSONObject result = dispatcher.execute("shell.progress", new JSONObject().put("percent", 7));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(7, shell.getEmulator().getProgressValue());
    }

    // --- notify ---

    @Test
    public void notifyNeedsWordsAndAValidUrgency() throws JSONException {
        host.currentSession = host.addPane();
        assertEquals(400, dispatcher.execute("shell.notify", new JSONObject()).getInt("_statusCode"));
        assertEquals(400, dispatcher.execute("shell.notify",
            new JSONObject().put("body", "x").put("urgency", "loud")).getInt("_statusCode"));
    }

    @Test
    public void notifyIsAttributedToTheCurrentPaneAndMarksIt() throws JSONException {
        TerminalSession shell = host.addPane();
        host.currentSession = shell;
        JSONObject result = dispatcher.execute("shell.notify",
            new JSONObject().put("title", "Build").put("body", "42 tests passed").put("id", "build")
                .put("urgency", "critical"));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(shell.mHandle, result.getString("pane"));
        assertEquals("build", result.getString("id"));
        assertTrue(host.called("noteShellAttention"));
    }

    @Test
    public void notifyWithoutAnySessionOrWithAnUnknownPaneIsRefused() throws JSONException {
        JSONObject none = dispatcher.execute("shell.notify", new JSONObject().put("body", "hi"));
        assertEquals("no_session", none.getString("error"));
        host.currentSession = host.addPane();
        JSONObject unknown = dispatcher.execute("shell.notify", new JSONObject().put("body", "hi").put("pane", "nope"));
        assertEquals("pane_not_found", unknown.getString("error"));
    }

    @Test
    public void urgencyAndStateNamesParseLikeTheProtocolNumbers() {
        assertEquals(Integer.valueOf(0), TerminalActionDispatcher.parseUrgency("low"));
        assertEquals(Integer.valueOf(2), TerminalActionDispatcher.parseUrgency(2));
        assertEquals(Integer.valueOf(-1), TerminalActionDispatcher.parseUrgency(null));
        assertNull(TerminalActionDispatcher.parseUrgency("loud"));
        assertEquals(Integer.valueOf(TerminalEmulator.PROGRESS_STATE_PAUSED), TerminalActionDispatcher.parseProgressState("paused"));
        assertEquals(Integer.valueOf(TerminalEmulator.PROGRESS_STATE_NONE), TerminalActionDispatcher.parseProgressState(0));
        assertNull(TerminalActionDispatcher.parseProgressState(9));
    }

    // --- clipboard ---

    @Test
    public void clipboardWriteReachesTheAndroidClipboardOnOrOffScreen() throws JSONException {
        JSONObject result = dispatcher.execute("clipboard.write", new JSONObject().put("text", "hello"));
        assertTrue(result.toString(), result.getBoolean("ok"));
        assertEquals(5, result.getInt("length"));
        assertEquals("hello", clipboardText());

        // The API writes off screen too, as Termux:API's termux-clipboard-set always has.
        host.visible = false;
        JSONObject later = dispatcher.execute("clipboard.write", new JSONObject().put("text", "later"));
        assertTrue(later.toString(), later.getBoolean("ok"));
        assertEquals("later", clipboardText());

        assertEquals(400, dispatcher.execute("clipboard.write", new JSONObject()).getInt("_statusCode"));
    }

    @Test
    public void clipboardReadFollowsTheOsc52Rules() throws JSONException {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(FakeTerminalHost.testContext(), false);
        Assume.assumeTrue("no termux preferences in this test environment", preferences != null);
        preferences.setOsc52ClipboardReadEnabled(true);
        dispatcher.execute("clipboard.write", new JSONObject().put("text", "hello"));

        JSONObject read = dispatcher.execute("clipboard.read", new JSONObject());
        assertTrue(read.toString(), read.getBoolean("ok"));
        assertEquals("hello", read.getString("text"));

        preferences.setOsc52ClipboardReadEnabled(false);
        JSONObject off = dispatcher.execute("clipboard.read", new JSONObject());
        assertEquals(403, off.getInt("_statusCode"));
        assertEquals("clipboard_read_disabled", off.getString("error"));

        preferences.setOsc52ClipboardReadEnabled(true);
        host.visible = false;
        JSONObject away = dispatcher.execute("clipboard.read", new JSONObject());
        assertEquals(409, away.getInt("_statusCode"));
        assertEquals("launcher_not_visible", away.getString("error"));
    }

    /** The escape side runs the same code: an OSC 52 write from the emulator lands on the same rule. */
    @Test
    public void theEscapeCallbacksShareTheClipboardPath() {
        TerminalSession shell = host.addPane();
        client.onCopyTextToClipboard(shell, "from osc 52");
        assertEquals("from osc 52", clipboardText());

        host.visible = false;
        client.onCopyTextToClipboard(shell, "ignored");
        assertEquals("from osc 52", clipboardText());
        assertNull(client.onReadTextFromClipboard(shell));
    }

    @Nullable
    private static String clipboardText() {
        ClipboardManager manager = (ClipboardManager) FakeTerminalHost.testContext()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || manager.getPrimaryClip() == null || manager.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence text = manager.getPrimaryClip().getItemAt(0).getText();
        return text == null ? null : text.toString();
    }

    /** A session client that answers every callback with nothing, so an emulator can run unattached. */
    private static final TerminalSessionClient SILENT_CLIENT = (TerminalSessionClient) Proxy.newProxyInstance(
        TerminalSessionClient.class.getClassLoader(), new Class<?>[] {TerminalSessionClient.class},
        (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class || type == long.class || type == short.class || type == byte.class) return 0;
            if (type == float.class || type == double.class) return 0.0;
            return null;
        });

    /** A host with shells that have emulators, looked up by handle, and a count of chip repaints. */
    private static final class SignalHost extends FakeTerminalHost {
        final Map<String, TerminalSession> panes = new LinkedHashMap<>();
        int windowBarRefreshes;

        SignalHost() throws IOException {
            super(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties());
        }

        @NonNull TerminalSession addPane() {
            TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, SILENT_CLIENT);
            ReflectionHelpers.setField(session, "mEmulator",
                new TerminalEmulator(session, false, 20, 5, 8, 16, 10, null));
            panes.put(session.mHandle, session);
            return session;
        }

        @Override @Nullable public TerminalSession findPaneById(@NonNull String id) {
            return panes.get(id);
        }

        @Override public void scheduleWindowBarRefresh() {
            windowBarRefreshes++;
        }
    }
}
