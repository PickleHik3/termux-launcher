package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import com.termux.shared.interact.ShareUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every copy made inside the launcher lands in the keyboard's history, and nothing else does:
 * the history is fed by the copy paths, never by reading the Android clipboard.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClipboardHistoryRecordingTest {

    private static final TerminalSessionClient SILENT_CLIENT = (TerminalSessionClient) Proxy.newProxyInstance(
        TerminalSessionClient.class.getClassLoader(), new Class<?>[] {TerminalSessionClient.class},
        (proxy, method, args) -> {
            Class<?> type = method.getReturnType();
            if (type == boolean.class) return false;
            if (type == int.class || type == long.class || type == short.class || type == byte.class) return 0;
            if (type == float.class || type == double.class) return 0.0;
            return null;
        });

    private static final class MemoryPins implements ClipboardHistory.PinStore {
        List<String> saved = new ArrayList<>();

        @Override public List<String> load() { return new ArrayList<>(saved); }

        @Override public void save(List<String> pins) { saved = new ArrayList<>(pins); }
    }

    private Context context;
    private ClipboardHistory history;
    private FakeTerminalHost host;
    private TermuxTerminalSessionActivityClient client;

    @Before
    public void attach() throws IOException {
        context = FakeTerminalHost.testContext();
        history = new ClipboardHistory(new MemoryPins(), Runnable::run);
        ClipboardHistory.installForTest(history);
        host = new FakeTerminalHost(context, FakeTerminalHost.testProperties());
        client = new TermuxTerminalSessionActivityClient(context, host);
    }

    @After
    public void detach() {
        ClipboardHistory.installForTest(null);
    }

    private String androidClipboard() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        return clip == null || clip.getItemCount() == 0 ? null : String.valueOf(clip.getItemAt(0).getText());
    }

    @Test
    public void whatOtherAppsPutOnTheAndroidClipboardNeverReachesTheHistory() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("elsewhere", "copied in another app"));
        assertEquals("copied in another app", androidClipboard());
        assertTrue(history.isEmpty());

        // A plain Android write from inside the process is not a launcher copy path either.
        ShareUtils.copyTextToClipboard(context, "written without the history");
        assertEquals("written without the history", androidClipboard());
        assertTrue(history.isEmpty());
    }

    @Test
    public void anOsc52OrLauncherctlWriteLandsOnBothTheClipboardAndTheHistory() {
        assertTrue(client.signals().clipboardWrite("from a program"));
        assertEquals("from a program", androidClipboard());
        assertEquals(Collections.singletonList("from a program"), history.recent());
    }

    @Test
    public void theTerminalsOwnSelectionCopyLandsInTheHistory() {
        TerminalSession session = new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, SILENT_CLIENT);
        // The selection toolbar's Copy: TextSelectionCursorController → session → this callback.
        client.onCopyTextToClipboard(session, "selected in the terminal");
        assertEquals("selected in the terminal", androidClipboard());
        assertEquals(Collections.singletonList("selected in the terminal"), history.recent());

        client.onCopyTextToClipboard(session, null);
        assertEquals(1, history.recentCount());
    }

    @Test
    public void aRefusedWriteRecordsNothing() {
        host.visible = false;
        assertFalse(client.signals().clipboardWrite("nobody is looking"));
        assertTrue(history.isEmpty());
    }

    @Test
    public void theSharedCopyHelperWritesBothAndDedupsToTheTop() {
        ClipboardHistory.copy(context, "one");
        ClipboardHistory.copy(context, "two");
        ClipboardHistory.copy(context, "one");
        assertEquals("one", androidClipboard());
        assertEquals(Arrays.asList("one", "two"), history.recent());
    }
}
