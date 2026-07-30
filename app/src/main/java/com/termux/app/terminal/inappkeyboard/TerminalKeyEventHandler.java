package com.termux.app.terminal.inappkeyboard;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import juloo.keyboard2.Config;
import juloo.keyboard2.KeyModifier;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;

/** Translates already-modified embedded-keyboard values into terminal operations. */
public final class TerminalKeyEventHandler implements Config.IKeyEventHandler {

    static final long MACRO_DELAY_MS = 1000L / 30L;
    static final int MAX_MACRO_DEPTH = 8;
    static final int MAX_EXPANDED_MACRO_KEYS = 256;

    // Android marks virtual keyboards by device id; there is no SOURCE_VIRTUAL input-source bit.
    private static final int VIRTUAL_KEY_SOURCE = InputDevice.SOURCE_KEYBOARD;

    private final TerminalSink mTerminalSink;
    private final HostActions mHostActions;
    private final Handler mMainHandler;

    private TerminalModifiers mModifiers = TerminalModifiers.NONE;
    private MacroTask mMacroTask;
    private boolean mLoggedSelectionSlider;
    private KeyValueInterceptor mInterceptor;

    public TerminalKeyEventHandler(Supplier<TerminalView> terminalView,
                                   Supplier<TerminalSession> currentSession,
                                   HostActions hostActions,
                                   Handler mainHandler) {
        this(new ViewTerminalSink(terminalView, currentSession), hostActions, mainHandler);
    }

    TerminalKeyEventHandler(TerminalSink terminalSink, HostActions hostActions, Handler mainHandler) {
        mTerminalSink = Objects.requireNonNull(terminalSink, "terminalSink");
        mHostActions = Objects.requireNonNull(hostActions, "hostActions");
        mMainHandler = Objects.requireNonNull(mainHandler, "mainHandler");
    }

    @Override
    public void key_down(KeyValue value, boolean isSwipe) {
        // Pointers legitimately delivers null for valueless gestures (e.g. a swipe that
        // resolves to an empty corner); upstream's handler tolerates null the same way.
        if (value == null)
            return;
        if (value.getKind() == KeyValue.Kind.Slider)
            dispatchSlider(value.getSlider(), 1, mModifiers, true);
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers modifiers) {
        if (value == null)
            return;
        dispatch(value, TerminalModifiers.from(modifiers));
    }

    @Override
    public void mods_changed(Pointers.Modifiers modifiers) {
        mModifiers = TerminalModifiers.from(modifiers);
        mHostActions.onKeyboardModifiersChanged(mModifiers);
    }

    @Override
    public void suggestion_entered(String text) {
        mHostActions.onSuggestionEntered(text);
    }

    /**
     * Claims resolved key values before they reach the terminal.
     *
     * <p>This is how an in-activity overlay — the command palette — types from the in-app
     * keyboard without the system IME: the keyboard's own pipeline stays intact and the
     * overlay only decides, per value, whether the terminal ever sees it.
     */
    public interface KeyValueInterceptor {

        /** @return true when the value was consumed and must not reach the terminal. */
        boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt, boolean shift);
    }

    /** Installs the overlay interceptor, or clears it with {@code null}. */
    public void setKeyValueInterceptor(@Nullable KeyValueInterceptor interceptor) {
        mInterceptor = interceptor;
    }

    /** Cancel asynchronous macro output, for hide, detach, layout, or session lifecycle changes. */
    public void cancelPendingMacros() {
        MacroTask task = mMacroTask;
        if (task == null)
            return;
        task.mCancelled = true;
        mMainHandler.removeCallbacksAndMessages(task.mToken);
        mMacroTask = null;
    }

    /** Clear all handler-owned state when the keyboard input pipeline is reset. */
    public void resetInputState() {
        cancelPendingMacros();
        mModifiers = TerminalModifiers.NONE;
        mHostActions.onKeyboardModifiersChanged(mModifiers);
        mHostActions.setComposePending(false);
    }

    TerminalModifiers currentModifiers() {
        return mModifiers;
    }

    private void dispatch(KeyValue value, TerminalModifiers modifiers) {
        KeyValueInterceptor interceptor = mInterceptor;
        if (interceptor != null && interceptor.interceptKeyValue(value,
            modifiers.isCtrl(), modifiers.isAlt(), modifiers.isShift()))
            return;
        switch (value.getKind()) {
            case Char:
                inputCodePoint(value.getChar(), modifiers);
                break;
            case Keyevent:
                dispatchKeyEvent(value.getKeyevent(), modifiers);
                break;
            case Event:
                dispatchEvent(value.getEvent(), modifiers);
                break;
            case Compose_pending:
                mHostActions.setComposePending(true);
                break;
            case Hangul_initial:
            case Hangul_medial:
            case Modifier:
                break;
            case Editing:
                dispatchEditing(value.getEditing(), modifiers);
                break;
            case Placeholder:
                if (value.getPlaceholder() == KeyValue.Placeholder.COMPOSE_CANCEL)
                    mHostActions.setComposePending(false);
                break;
            case String:
                dispatchString(value.getString(), modifiers);
                break;
            case Slider:
                dispatchSlider(value.getSlider(), value.getSliderRepeat(), modifiers, false);
                break;
            case Macro:
                startMacro(value.getMacro());
                break;
            case Launcher_tool:
                mHostActions.runLauncherTool(value.getLauncherTool().toolId);
                break;
            case Stateful:
                break;
        }
    }

    private void dispatchString(String text, TerminalModifiers modifiers) {
        if (!modifiers.isCtrl() && !modifiers.isAlt()) {
            mTerminalSink.write(text);
            return;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = Character.codePointAt(text, offset);
            inputCodePoint(codePoint, modifiers);
            offset += Character.charCount(codePoint);
        }
    }

    private void inputCodePoint(int codePoint, TerminalModifiers modifiers) {
        mTerminalSink.inputCodePoint(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD,
            codePoint, modifiers.isCtrl(), modifiers.isAlt());
    }

    private void dispatchKeyEvent(int keyCode, TerminalModifiers modifiers) {
        long eventTime = SystemClock.uptimeMillis();
        int metaState = modifiers.toKeyEventMetaState();
        KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0,
            metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, VIRTUAL_KEY_SOURCE);
        KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0,
            metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, VIRTUAL_KEY_SOURCE);
        mTerminalSink.dispatchKeyEvent(keyCode, down, up);
    }

    private void dispatchEditing(KeyValue.Editing editing, TerminalModifiers modifiers) {
        switch (editing) {
            case COPY:
                mHostActions.copySelection();
                break;
            case PASTE:
            case PASTE_PLAIN:
                mHostActions.paste();
                break;
            case CUT:
                if (mHostActions.prepareCut())
                    inputCodePoint('u', modifiers.withCtrl());
                break;
            case SELECT_ALL:
                mHostActions.selectAll();
                break;
            case UNDO:
                // Readline/zsh line-editing undo.
                inputCodePoint('_', modifiers.withCtrl());
                break;
            case REDO:
            case REPLACE:
            case SHARE:
            case ASSIST:
            case AUTOFILL:
                mHostActions.debugLog("Unsupported terminal editing action: " + editing);
                break;
            case DELETE_WORD:
                dispatchKeyEvent(KeyEvent.KEYCODE_DEL, withCtrl(modifiers));
                break;
            case FORWARD_DELETE_WORD:
                dispatchKeyEvent(KeyEvent.KEYCODE_FORWARD_DEL, withCtrl(modifiers));
                break;
            case SELECTION_CANCEL:
                if (mTerminalSink.isSelectingText())
                    mTerminalSink.stopTextSelectionMode();
                break;
            case SPACE_BAR:
                inputCodePoint(' ', modifiers);
                break;
            case BACKSPACE:
                dispatchKeyEvent(KeyEvent.KEYCODE_DEL, modifiers);
                break;
        }
    }

    private TerminalModifiers withCtrl(TerminalModifiers modifiers) {
        return modifiers.withCtrl();
    }

    private void dispatchEvent(KeyValue.Event event, TerminalModifiers modifiers) {
        switch (event) {
            case CONFIG:
                mHostActions.openKeyboardSettings();
                break;
            case SWITCH_TEXT:
                mHostActions.requestTextLayout();
                break;
            case SWITCH_NUMERIC:
                mHostActions.requestNumericLayout();
                break;
            case SWITCH_EMOJI:
            case SWITCH_BACK_EMOJI:
            case SWITCH_CLIPBOARD:
            case SWITCH_BACK_CLIPBOARD:
            case SWITCH_VOICE_TYPING:
            case SWITCH_VOICE_TYPING_CHOOSER:
                mHostActions.debugLog("Unsupported in-app keyboard pane event: " + event);
                break;
            case CHANGE_METHOD_PICKER:
            case CHANGE_METHOD_PREV:
            case CHANGE_METHOD_NEXT:
            case HIDE_SELF:
                cancelPendingMacros();
                mHostActions.hideKeyboard();
                break;
            case ACTION:
                dispatchKeyEvent(KeyEvent.KEYCODE_ENTER, modifiers);
                break;
            case SWITCH_FORWARD:
                mHostActions.requestForwardLayout();
                break;
            case SWITCH_BACKWARD:
                mHostActions.requestBackwardLayout();
                break;
            case SWITCH_GREEKMATH:
                mHostActions.requestGreekMathLayout();
                break;
            case CAPS_LOCK:
                mHostActions.toggleCapsLock();
                break;
        }
    }

    private void dispatchSlider(KeyValue.Slider slider, int repeat, TerminalModifiers modifiers,
                                boolean initial) {
        if (repeat == 0)
            return;
        boolean reverse = !initial && repeat < 0;
        int keyCode;
        switch (slider) {
            case Cursor_left:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Cursor_right:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Cursor_up:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
                break;
            case Cursor_down:
                keyCode = reverse ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            case Selection_cursor_left:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Selection_cursor_right:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            default:
                throw new AssertionError("Unhandled slider " + slider);
        }
        if ((slider == KeyValue.Slider.Selection_cursor_left ||
            slider == KeyValue.Slider.Selection_cursor_right) && !mLoggedSelectionSlider) {
            mLoggedSelectionSlider = true;
            mHostActions.debugLog("Selection slider reduced to plain terminal cursor movement");
        }
        int count = initial ? 1 : Math.abs(repeat);
        for (int i = 0; i < count; i++)
            dispatchKeyEvent(keyCode, modifiers);
    }

    private void startMacro(KeyValue[] keys) {
        cancelPendingMacros();
        if (keys.length == 0)
            return;
        MacroTask task = new MacroTask(Arrays.copyOf(keys, keys.length),
            mTerminalSink.currentSessionIdentity());
        mMacroTask = task;
        postMacro(task, 0L);
    }

    private void postMacro(MacroTask task, long delayMs) {
        Message message = Message.obtain(mMainHandler, task);
        message.obj = task.mToken;
        mMainHandler.sendMessageDelayed(message, delayMs);
    }

    private boolean shouldDelayAfterMacroKey(KeyValue value) {
        switch (value.getKind()) {
            case Keyevent:
            case Editing:
            case Event:
            case Launcher_tool:
                return true;
            default:
                return false;
        }
    }

    interface TerminalSink {

        void inputCodePoint(int eventSource, int codePoint, boolean ctrl, boolean alt);

        void dispatchKeyEvent(int keyCode, KeyEvent down, KeyEvent up);

        void write(String text);

        boolean isSelectingText();

        void stopTextSelectionMode();

        Object currentSessionIdentity();
    }

    private static final class ViewTerminalSink implements TerminalSink {

        // Resolved dynamically so in-app keyboard input follows the focused split pane.
        private final Supplier<TerminalView> mTerminalView;
        private final Supplier<TerminalSession> mCurrentSession;

        private ViewTerminalSink(Supplier<TerminalView> terminalView, Supplier<TerminalSession> currentSession) {
            mTerminalView = Objects.requireNonNull(terminalView, "terminalView");
            mCurrentSession = Objects.requireNonNull(currentSession, "currentSession");
        }

        @Override
        public void inputCodePoint(int eventSource, int codePoint, boolean ctrl, boolean alt) {
            TerminalView view = mTerminalView.get();
            if (view != null)
                view.inputCodePoint(eventSource, codePoint, ctrl, alt);
        }

        @Override
        public void dispatchKeyEvent(int keyCode, KeyEvent down, KeyEvent up) {
            TerminalView view = mTerminalView.get();
            if (view == null)
                return;
            view.onKeyDown(keyCode, down);
            view.onKeyUp(keyCode, up);
        }

        @Override
        public void write(String text) {
            TerminalSession session = mCurrentSession.get();
            if (session != null)
                session.write(text);
        }

        @Override
        public boolean isSelectingText() {
            TerminalView view = mTerminalView.get();
            return view != null && view.isSelectingText();
        }

        @Override
        public void stopTextSelectionMode() {
            TerminalView view = mTerminalView.get();
            if (view != null)
                view.stopTextSelectionMode();
        }

        @Override
        public Object currentSessionIdentity() {
            return mCurrentSession.get();
        }
    }

    private static final class MacroFrame {

        private final KeyValue[] mKeys;
        private int mIndex;
        private Pointers.Modifiers mModifiers = Pointers.Modifiers.EMPTY;

        private MacroFrame(KeyValue[] keys) {
            mKeys = keys;
        }
    }

    private final class MacroTask implements Runnable {

        private final Object mToken = new Object();
        private final Object mSessionIdentity;
        private final ArrayDeque<MacroFrame> mFrames = new ArrayDeque<>();
        private int mExpandedKeys;
        private boolean mCancelled;

        private MacroTask(KeyValue[] keys, Object sessionIdentity) {
            mFrames.push(new MacroFrame(keys));
            mSessionIdentity = sessionIdentity;
        }

        @Override
        public void run() {
            if (!isCurrent())
                return;
            while (!mFrames.isEmpty()) {
                MacroFrame frame = mFrames.peek();
                if (frame.mIndex >= frame.mKeys.length) {
                    mFrames.pop();
                    continue;
                }
                if (++mExpandedKeys > MAX_EXPANDED_MACRO_KEYS) {
                    abort("Macro expansion exceeded " + MAX_EXPANDED_MACRO_KEYS + " keys");
                    return;
                }

                KeyValue value = KeyModifier.modify_no_modmap(
                    frame.mKeys[frame.mIndex++], frame.mModifiers);
                if (value == null)
                    continue;
                if (value.hasFlagsAny(KeyValue.FLAG_LATCH)) {
                    if (!value.hasFlagsAny(KeyValue.FLAG_SPECIAL))
                        frame.mModifiers = Pointers.Modifiers.EMPTY;
                    frame.mModifiers = frame.mModifiers.with_extra_mod(value);
                    continue;
                }

                Pointers.Modifiers valueModifiers = frame.mModifiers;
                frame.mModifiers = Pointers.Modifiers.EMPTY;
                if (value.getKind() == KeyValue.Kind.Macro) {
                    if (mFrames.size() >= MAX_MACRO_DEPTH) {
                        abort("Macro nesting exceeded depth " + MAX_MACRO_DEPTH);
                        return;
                    }
                    mFrames.push(new MacroFrame(Arrays.copyOf(value.getMacro(), value.getMacro().length)));
                    continue;
                }

                // Macro values have no pointer lifecycle. In particular, dispatching a Slider as
                // both key-down and key-up would move it twice.
                if (value.getKind() != KeyValue.Kind.Slider)
                    key_down(value, false);
                dispatch(value, TerminalModifiers.from(valueModifiers));
                if (!isCurrent())
                    return;
                if (shouldDelayAfterMacroKey(value)) {
                    postMacro(this, MACRO_DELAY_MS);
                    return;
                }
            }
            if (mMacroTask == this)
                mMacroTask = null;
        }

        private boolean isCurrent() {
            if (mCancelled || mMacroTask != this)
                return false;
            if (mTerminalSink.currentSessionIdentity() != mSessionIdentity) {
                abort("Macro cancelled after terminal session replacement");
                return false;
            }
            return true;
        }

        private void abort(String reason) {
            mHostActions.debugLog(reason);
            cancelPendingMacros();
        }
    }
}
