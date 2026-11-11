package com.termux.view;

import android.os.SystemClock;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.termux.terminal.CapturingSession;
import com.termux.terminal.TerminalEmulator;

import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * A {@link TerminalView} on a capturing session, for tests that type into it through the view's real
 * entry points. The client answers every question with its default except the extra keys' sticky
 * modifiers, which {@link #sticky} sets and which, as in the app, are cleared by being read.
 */
final class KeyRouteRig {

    final TerminalView view;
    final CapturingSession session = new CapturingSession();
    final TerminalEmulator emulator;
    private boolean mStickyCtrl, mStickyAlt, mStickyShift, mStickyFn;

    /**
     * @param fast a Fast (libghostty) emulator when true, a Standard one otherwise
     * @param inputProperty what the input property reads as when the Fast core attaches, or null
     */
    KeyRouteRig(boolean fast, String inputProperty) {
        CapturingSession.inputPropertyForTests(inputProperty);
        view = new TerminalView(RuntimeEnvironment.getApplication(), null);
        view.setTerminalViewClient((TerminalViewClient) Proxy.newProxyInstance(
            TerminalViewClient.class.getClassLoader(), new Class<?>[] { TerminalViewClient.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "readControlKey": { boolean v = mStickyCtrl; mStickyCtrl = false; return v; }
                    case "readAltKey": { boolean v = mStickyAlt; mStickyAlt = false; return v; }
                    case "readShiftKey": { boolean v = mStickyShift; mStickyShift = false; return v; }
                    case "readFnKey": { boolean v = mStickyFn; mStickyFn = false; return v; }
                    default:
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == float.class) return 0f;
                        return null;
                }
            }));
        emulator = session.attach(80, 24);
        if (fast && !emulator.useGhosttyCore())
            throw new IllegalStateException("library did not load");
        view.setSessionForTest(session);
    }

    /** Escapes a program would have sent, fed to the emulator (modes, kitty flags). */
    void feed(String escapes) {
        byte[] bytes = escapes.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    /** The extra keys' sticky modifiers, armed until the view reads them. */
    void sticky(boolean ctrl, boolean alt, boolean shift) {
        mStickyCtrl = ctrl;
        mStickyAlt = alt;
        mStickyShift = shift;
    }

    String drain() {
        return session.drain();
    }

    static KeyEvent event(int action, int keyCode, int repeat, int meta) {
        long t = SystemClock.uptimeMillis();
        return new KeyEvent(t, t, action, keyCode, repeat, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
    }

    InputConnection inputConnection() {
        return view.onCreateInputConnection(new EditorInfo());
    }
}
