package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.termux.view.TerminalView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalKeyEventHandlerTest {

    private FakeTerminalSink mTerminal;
    private FakeHostActions mHost;
    private TerminalKeyEventHandler mHandler;

    @Before
    public void setUp() {
        mTerminal = new FakeTerminalSink();
        mHost = new FakeHostActions();
        mHandler = new TerminalKeyEventHandler(mTerminal, mHost, new Handler(Looper.getMainLooper()));
    }

    @Test
    public void dispatchesEveryKind() {
        EnumSet<KeyValue.Kind> dispatched = EnumSet.noneOf(KeyValue.Kind.class);
        for (KeyValue.Kind kind : KeyValue.Kind.values()) {
            KeyValue value = valueForKind(kind);
            mHandler.key_up(value, Pointers.Modifiers.EMPTY);
            dispatched.add(kind);
        }
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(EnumSet.allOf(KeyValue.Kind.class), dispatched);
        assertTrue(mTerminal.codePoints.contains((int) 'x'));
        assertTrue(mTerminal.writes.contains("text"));
        assertTrue(mTerminal.keyCodes.contains(KeyEvent.KEYCODE_TAB));
        assertEquals(1, mHost.settings);
        assertTrue(mHost.composeStates.contains(true));
        assertTrue(mHost.composeStates.contains(false));
    }

    @Test
    public void charUsesOnlyCtrlAndAltWhileKeyeventPreservesAllMetaState() {
        Pointers.Modifiers modifiers = mods(KeyValue.Modifier.CTRL, KeyValue.Modifier.ALT,
            KeyValue.Modifier.SHIFT, KeyValue.Modifier.META);

        mHandler.key_up(KeyValue.makeCharKey('a'), modifiers);
        CodePointCall charCall = mTerminal.codePointCalls.get(0);
        assertEquals(TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, charCall.eventSource);
        assertTrue(charCall.ctrl);
        assertTrue(charCall.alt);

        mHandler.key_up(KeyValue.keyeventKey("tab", KeyEvent.KEYCODE_TAB, 0), modifiers);
        KeyCall keyCall = mTerminal.keyCalls.get(0);
        assertEquals(KeyEvent.ACTION_DOWN, keyCall.down.getAction());
        assertEquals(KeyEvent.ACTION_UP, keyCall.up.getAction());
        assertTrue(keyCall.down.isCtrlPressed());
        assertTrue(keyCall.down.isAltPressed());
        assertTrue(keyCall.down.isShiftPressed());
        assertTrue(keyCall.down.isMetaPressed());
        assertTrue(keyCall.down.isFromSource(InputDevice.SOURCE_KEYBOARD));
        assertEquals(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, keyCall.down.getDeviceId());
    }

    @Test
    public void metaCharIsNotTreatedAsAlt() {
        mHandler.key_up(KeyValue.makeCharKey('m'), mods(KeyValue.Modifier.META));

        assertFalse(mTerminal.codePointCalls.get(0).alt);
    }

    @Test
    public void stringWritesOnceWithoutTerminalModifiers() {
        mHandler.key_up(KeyValue.makeStringKey("A\ud83d\ude00z"), Pointers.Modifiers.EMPTY);

        assertEquals(Arrays.asList("A\ud83d\ude00z"), mTerminal.writes);
        assertTrue(mTerminal.codePointCalls.isEmpty());
    }

    @Test
    public void stringWithCtrlIteratesUnicodeCodePointsIncludingAstralCharacters() {
        mHandler.key_up(KeyValue.makeStringKey("A\ud83d\ude00z"),
            mods(KeyValue.Modifier.CTRL, KeyValue.Modifier.ALT));

        assertEquals(Arrays.asList((int) 'A', 0x1F600, (int) 'z'), mTerminal.codePoints);
        for (CodePointCall call : mTerminal.codePointCalls) {
            assertTrue(call.ctrl);
            assertTrue(call.alt);
        }
        assertTrue(mTerminal.writes.isEmpty());
    }

    @Test
    public void modsChangedIsSnapshotOnlyAndDrivesImmediateSlider() {
        mHandler.mods_changed(mods(KeyValue.Modifier.CTRL, KeyValue.Modifier.META));
        assertTrue(mTerminal.keyCalls.isEmpty());

        mHandler.key_down(KeyValue.sliderKey(KeyValue.Slider.Cursor_left, 1), false);

        assertEquals(Arrays.asList(KeyEvent.KEYCODE_DPAD_LEFT), mTerminal.keyCodes);
        assertTrue(mTerminal.keyCalls.get(0).down.isCtrlPressed());
        assertTrue(mTerminal.keyCalls.get(0).down.isMetaPressed());
    }

    @Test
    public void compositionModifierPlaceholderAndStateKindsEmitNoTerminalOutput() {
        mHandler.key_up(KeyValue.makeHangulInitial("hangul", 0), Pointers.Modifiers.EMPTY);
        mHandler.key_up(KeyValue.makeHangulMedial(44032, 0), Pointers.Modifiers.EMPTY);
        mHandler.key_up(KeyValue.makeInternalModifier(KeyValue.Modifier.FN), Pointers.Modifiers.EMPTY);
        mHandler.key_up(new KeyValue("removed", KeyValue.Kind.Placeholder,
            KeyValue.Placeholder.REMOVED.ordinal(), 0), Pointers.Modifiers.EMPTY);
        mHandler.key_up(new KeyValue("stateful", KeyValue.Kind.Stateful, 0, 0),
            Pointers.Modifiers.EMPTY);

        assertTrue(mTerminal.codePointCalls.isEmpty());
        assertTrue(mTerminal.keyCalls.isEmpty());
        assertTrue(mTerminal.writes.isEmpty());
    }

    @Test
    public void editingMappingIsExhaustive() {
        EnumSet<KeyValue.Editing> covered = EnumSet.noneOf(KeyValue.Editing.class);
        mTerminal.selecting = true;
        for (KeyValue.Editing editing : KeyValue.Editing.values()) {
            int copies = mHost.copies;
            int pastes = mHost.pastes;
            int keys = mTerminal.keyCalls.size();
            int codePoints = mTerminal.codePointCalls.size();
            int logs = mHost.logs.size();
            boolean selectionWasStopped = mTerminal.selectionStops > 0;

            mHandler.key_up(editing(editing), Pointers.Modifiers.EMPTY);
            covered.add(editing);

            switch (editing) {
                case COPY:
                    assertEquals(copies + 1, mHost.copies);
                    break;
                case PASTE:
                case PASTE_PLAIN:
                    assertEquals(pastes + 1, mHost.pastes);
                    break;
                case CUT:
                    assertEquals(copies + 1, mHost.copies);
                    assertEquals(keys, mTerminal.keyCalls.size());
                    assertEquals(logs + 1, mHost.logs.size());
                    break;
                case SELECT_ALL:
                    assertEquals(1, mHost.screenCopies);
                    break;
                case UNDO:
                    assertEquals(codePoints + 1, mTerminal.codePointCalls.size());
                    assertEquals((int) '_', last(mTerminal.codePointCalls).codePoint);
                    assertTrue(last(mTerminal.codePointCalls).ctrl);
                    break;
                case REDO:
                case REPLACE:
                case SHARE:
                case ASSIST:
                case AUTOFILL:
                    assertEquals(logs + 1, mHost.logs.size());
                    break;
                case DELETE_WORD:
                    assertEquals(keys + 1, mTerminal.keyCalls.size());
                    assertEquals(KeyEvent.KEYCODE_DEL, last(mTerminal.keyCalls).keyCode);
                    assertTrue(last(mTerminal.keyCalls).down.isCtrlPressed());
                    break;
                case FORWARD_DELETE_WORD:
                    assertEquals(keys + 1, mTerminal.keyCalls.size());
                    assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, last(mTerminal.keyCalls).keyCode);
                    assertTrue(last(mTerminal.keyCalls).down.isCtrlPressed());
                    break;
                case SELECTION_CANCEL:
                    assertFalse(selectionWasStopped);
                    assertEquals(1, mTerminal.selectionStops);
                    break;
                case SPACE_BAR:
                    assertEquals(codePoints + 1, mTerminal.codePointCalls.size());
                    assertEquals((int) ' ', (int) last(mTerminal.codePoints));
                    break;
                case BACKSPACE:
                    assertEquals(keys + 1, mTerminal.keyCalls.size());
                    assertEquals(KeyEvent.KEYCODE_DEL, last(mTerminal.keyCalls).keyCode);
                    break;
            }
        }
        assertEquals(EnumSet.allOf(KeyValue.Editing.class), covered);
    }

    @Test
    public void selectionCancelDoesNothingWhenSelectionIsInactive() {
        mHandler.key_up(editing(KeyValue.Editing.SELECTION_CANCEL), Pointers.Modifiers.EMPTY);

        assertEquals(0, mTerminal.selectionStops);
    }

    @Test
    public void eventMappingIsExhaustive() {
        EnumSet<KeyValue.Event> covered = EnumSet.noneOf(KeyValue.Event.class);
        for (KeyValue.Event event : KeyValue.Event.values()) {
            int hostActions = mHost.totalActions();
            int keys = mTerminal.keyCalls.size();
            int logs = mHost.logs.size();
            mHandler.key_up(event(event), Pointers.Modifiers.EMPTY);
            covered.add(event);

            switch (event) {
                case CONFIG:
                case SWITCH_TEXT:
                case SWITCH_NUMERIC:
                case CHANGE_METHOD_PICKER:
                case CHANGE_METHOD_PREV:
                case CHANGE_METHOD_NEXT:
                case SWITCH_FORWARD:
                case SWITCH_BACKWARD:
                case SWITCH_GREEKMATH:
                case CAPS_LOCK:
                case HIDE_SELF:
                    assertEquals(hostActions + 1, mHost.totalActions());
                    break;
                case SWITCH_EMOJI:
                case SWITCH_BACK_EMOJI:
                case SWITCH_CLIPBOARD:
                case SWITCH_BACK_CLIPBOARD:
                case SWITCH_VOICE_TYPING:
                case SWITCH_VOICE_TYPING_CHOOSER:
                    assertEquals(logs + 1, mHost.logs.size());
                    break;
                case ACTION:
                    assertEquals(keys + 1, mTerminal.keyCalls.size());
                    assertEquals(KeyEvent.KEYCODE_ENTER, last(mTerminal.keyCalls).keyCode);
                    break;
            }
        }
        assertEquals(EnumSet.allOf(KeyValue.Event.class), covered);
    }

    @Test
    public void sliderDirectionsRepeatsAndSelectionReductionAreMapped() {
        int[] expected = {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        };
        int offset = 0;
        for (KeyValue.Slider slider : KeyValue.Slider.values()) {
            mHandler.key_up(KeyValue.sliderKey(slider, 3), Pointers.Modifiers.EMPTY);
            assertEquals(offset + 3, mTerminal.keyCodes.size());
            for (int i = offset; i < offset + 3; i++)
                assertEquals(expected[slider.ordinal()], (int) mTerminal.keyCodes.get(i));
            offset += 3;
        }
        assertEquals(1, countContaining(mHost.logs, "Selection slider"));

        mHandler.key_up(KeyValue.sliderKey(KeyValue.Slider.Cursor_left, -2),
            Pointers.Modifiers.EMPTY);
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, (int) mTerminal.keyCodes.get(offset));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, (int) mTerminal.keyCodes.get(offset + 1));
    }

    @Test
    public void macroUsesMainHandlerAndDelaysAfterOrderedActions() {
        KeyValue macro = KeyValue.makeMacro("M", new KeyValue[] {
            KeyValue.keyeventKey("tab", KeyEvent.KEYCODE_TAB, 0),
            KeyValue.makeCharKey('x'),
        }, 0);

        mHandler.key_up(macro, Pointers.Modifiers.EMPTY);
        assertTrue(mTerminal.keyCalls.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Arrays.asList(KeyEvent.KEYCODE_TAB), mTerminal.keyCodes);
        assertTrue(mTerminal.codePoints.isEmpty());

        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS - 1, TimeUnit.MILLISECONDS);
        assertTrue(mTerminal.codePoints.isEmpty());
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS);
        assertEquals(Arrays.asList((int) 'x'), mTerminal.codePoints);
    }

    @Test
    public void macroAlsoDelaysAfterEditingActions() {
        KeyValue macro = KeyValue.makeMacro("M", new KeyValue[] {
            editing(KeyValue.Editing.PASTE),
            KeyValue.makeCharKey('x'),
        }, 0);

        mHandler.key_up(macro, Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, mHost.pastes);
        assertTrue(mTerminal.codePoints.isEmpty());

        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS, TimeUnit.MILLISECONDS);
        assertEquals(Arrays.asList((int) 'x'), mTerminal.codePoints);
    }

    @Test
    public void macroSliderDispatchesOnce() {
        KeyValue macro = KeyValue.makeMacro("slider", new KeyValue[] {
            KeyValue.sliderKey(KeyValue.Slider.Cursor_left, 1),
        }, 0);

        mHandler.key_up(macro, Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(Arrays.asList(KeyEvent.KEYCODE_DPAD_LEFT), mTerminal.keyCodes);
    }

    @Test
    public void macroAppliesLocalModifiersWithoutReapplyingOutsideModifiers() {
        KeyValue macro = KeyValue.makeMacro("M", new KeyValue[] {
            KeyValue.getKeyByName("ctrl"),
            KeyValue.makeCharKey('a'),
            KeyValue.makeCharKey('b'),
        }, 0);

        mHandler.key_up(macro, mods(KeyValue.Modifier.ALT));
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(KeyEvent.KEYCODE_A, mTerminal.keyCalls.get(0).keyCode);
        assertTrue(mTerminal.keyCalls.get(0).down.isCtrlPressed());
        assertFalse(mTerminal.keyCalls.get(0).down.isAltPressed());
        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS, TimeUnit.MILLISECONDS);
        assertEquals(Arrays.asList((int) 'b'), mTerminal.codePoints);
    }

    @Test
    public void macroCanBeCancelledDuringDelay() {
        mHandler.key_up(delayedMacro(), Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        mHandler.cancelPendingMacros();
        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS, TimeUnit.MILLISECONDS);

        assertTrue(mTerminal.codePoints.isEmpty());
    }

    @Test
    public void macroCancelsWhenSessionIsReplaced() {
        Object original = mTerminal.sessionIdentity;
        mHandler.key_up(delayedMacro(), Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        mTerminal.sessionIdentity = new Object();
        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS, TimeUnit.MILLISECONDS);

        assertFalse(mHost.logs.isEmpty());
        assertTrue(mTerminal.codePoints.isEmpty());
        assertFalse(original == mTerminal.sessionIdentity);
    }

    @Test
    public void macroExpansionIsCapped() {
        KeyValue[] keys = new KeyValue[TerminalKeyEventHandler.MAX_EXPANDED_MACRO_KEYS + 1];
        Arrays.fill(keys, KeyValue.makeCharKey('x'));

        mHandler.key_up(KeyValue.makeMacro("cap", keys, 0), Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(TerminalKeyEventHandler.MAX_EXPANDED_MACRO_KEYS, mTerminal.codePoints.size());
        assertEquals(1, countContaining(mHost.logs, "exceeded"));
    }

    @Test
    public void macroNestingIsCapped() {
        KeyValue nested = KeyValue.makeCharKey('x');
        for (int i = 0; i <= TerminalKeyEventHandler.MAX_MACRO_DEPTH; i++)
            nested = KeyValue.makeMacro("nested", new KeyValue[] { nested }, 0);

        mHandler.key_up(nested, Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(mTerminal.codePoints.isEmpty());
        assertEquals(1, countContaining(mHost.logs, "depth"));
    }

    @Test
    public void suggestionAndResetForwardToHostAndCancelMacro() {
        mHandler.suggestion_entered("candidate");
        mHandler.key_up(delayedMacro(), Pointers.Modifiers.EMPTY);
        shadowOf(Looper.getMainLooper()).idle();

        mHandler.resetInputState();
        shadowOf(Looper.getMainLooper()).idleFor(
            TerminalKeyEventHandler.MACRO_DELAY_MS, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList("candidate"), mHost.suggestions);
        assertEquals(Boolean.FALSE, last(mHost.composeStates));
        assertSame(TerminalModifiers.NONE, mHandler.currentModifiers());
        assertTrue(mTerminal.codePoints.isEmpty());
    }

    private KeyValue delayedMacro() {
        return KeyValue.makeMacro("M", new KeyValue[] {
            event(KeyValue.Event.ACTION),
            KeyValue.makeCharKey('x'),
        }, 0);
    }

    private static KeyValue valueForKind(KeyValue.Kind kind) {
        switch (kind) {
            case Char:
                return KeyValue.makeCharKey('x');
            case Keyevent:
                return KeyValue.keyeventKey("tab", KeyEvent.KEYCODE_TAB, 0);
            case Event:
                return event(KeyValue.Event.CONFIG);
            case Compose_pending:
                return KeyValue.makeComposePending("compose", 0, 0);
            case Hangul_initial:
                return KeyValue.makeHangulInitial("hangul", 0);
            case Hangul_medial:
                return KeyValue.makeHangulMedial(44032, 0);
            case Modifier:
                return KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL);
            case Editing:
                return editing(KeyValue.Editing.SPACE_BAR);
            case Placeholder:
                return new KeyValue("cancel", KeyValue.Kind.Placeholder,
                    KeyValue.Placeholder.COMPOSE_CANCEL.ordinal(), 0);
            case String:
                return KeyValue.makeStringKey("text");
            case Slider:
                return KeyValue.sliderKey(KeyValue.Slider.Cursor_right, 1);
            case Macro:
                return KeyValue.makeMacro("macro", new KeyValue[] { KeyValue.makeCharKey('m') }, 0);
            case Stateful:
                return new KeyValue("stateful", KeyValue.Kind.Stateful, 0, 0);
            default:
                throw new AssertionError(kind);
        }
    }

    private static KeyValue editing(KeyValue.Editing editing) {
        return new KeyValue(editing.name(), KeyValue.Kind.Editing, editing.ordinal(), 0);
    }

    private static KeyValue event(KeyValue.Event event) {
        return new KeyValue(event.name(), KeyValue.Kind.Event, event.ordinal(), 0);
    }

    private static Pointers.Modifiers mods(KeyValue.Modifier... modifiers) {
        Pointers.Modifiers result = Pointers.Modifiers.EMPTY;
        for (KeyValue.Modifier modifier : modifiers)
            result = result.with_extra_mod(KeyValue.makeInternalModifier(modifier));
        return result;
    }

    private static <T> T last(List<T> values) {
        return values.get(values.size() - 1);
    }

    private static int countContaining(List<String> values, String needle) {
        int count = 0;
        for (String value : values) {
            if (value.contains(needle))
                count++;
        }
        return count;
    }

    private static final class CodePointCall {

        private final int eventSource;
        private final int codePoint;
        private final boolean ctrl;
        private final boolean alt;

        private CodePointCall(int eventSource, int codePoint, boolean ctrl, boolean alt) {
            this.eventSource = eventSource;
            this.codePoint = codePoint;
            this.ctrl = ctrl;
            this.alt = alt;
        }
    }

    @Test
    public void selectAllCopiesWholeScreen() {
        mHandler.key_up(KeyValue.getKeyByName("selectAll"), Pointers.Modifiers.EMPTY);

        assertEquals(1, mHost.screenCopies);
        assertEquals(0, mHost.copies);
    }

    @Test
    public void undoSendsReadlineUndoControlSequence() {
        mHandler.key_up(KeyValue.getKeyByName("undo"), Pointers.Modifiers.EMPTY);

        assertEquals(1, mTerminal.codePointCalls.size());
        CodePointCall call = mTerminal.codePointCalls.get(0);
        assertEquals((int) '_', call.codePoint);
        assertTrue(call.ctrl);
    }

    private static final class KeyCall {

        private final int keyCode;
        private final KeyEvent down;
        private final KeyEvent up;

        private KeyCall(int keyCode, KeyEvent down, KeyEvent up) {
            this.keyCode = keyCode;
            this.down = down;
            this.up = up;
        }
    }

    private static final class FakeTerminalSink implements TerminalKeyEventHandler.TerminalSink {

        private final List<CodePointCall> codePointCalls = new ArrayList<>();
        private final List<Integer> codePoints = new ArrayList<>();
        private final List<KeyCall> keyCalls = new ArrayList<>();
        private final List<Integer> keyCodes = new ArrayList<>();
        private final List<String> writes = new ArrayList<>();
        private boolean selecting;
        private int selectionStops;
        private Object sessionIdentity = new Object();

        @Override
        public void inputCodePoint(int eventSource, int codePoint, boolean ctrl, boolean alt) {
            codePointCalls.add(new CodePointCall(eventSource, codePoint, ctrl, alt));
            codePoints.add(codePoint);
        }

        @Override
        public void dispatchKeyEvent(int keyCode, KeyEvent down, KeyEvent up) {
            keyCalls.add(new KeyCall(keyCode, down, up));
            keyCodes.add(keyCode);
        }

        @Override
        public void write(String text) {
            writes.add(text);
        }

        @Override
        public boolean isSelectingText() {
            return selecting;
        }

        @Override
        public void stopTextSelectionMode() {
            selectionStops++;
            selecting = false;
        }

        @Override
        public Object currentSessionIdentity() {
            return sessionIdentity;
        }
    }

    private static final class FakeHostActions implements HostActions {

        private int pastes;
        private int copies;
        private int screenCopies;
        private int textLayouts;
        private int numericLayouts;
        private int greekLayouts;
        private int nextLayouts;
        private int previousLayouts;
        private int settings;
        private int hides;
        private int capsLocks;
        private final List<Boolean> composeStates = new ArrayList<>();
        private final List<String> suggestions = new ArrayList<>();
        private final List<String> logs = new ArrayList<>();

        @Override
        public void paste() {
            pastes++;
        }

        @Override
        public void copySelection() {
            copies++;
        }

        @Override
        public void copyScreen() {
            screenCopies++;
        }

        @Override
        public void requestTextLayout() {
            textLayouts++;
        }

        @Override
        public void requestNumericLayout() {
            numericLayouts++;
        }

        @Override
        public void requestGreekMathLayout() {
            greekLayouts++;
        }

        @Override
        public void requestForwardLayout() {
            nextLayouts++;
        }

        @Override
        public void requestBackwardLayout() {
            previousLayouts++;
        }

        @Override
        public void openKeyboardSettings() {
            settings++;
        }

        @Override
        public void hideKeyboard() {
            hides++;
        }

        @Override
        public void setComposePending(boolean pending) {
            composeStates.add(pending);
        }

        @Override
        public void toggleCapsLock() {
            capsLocks++;
        }

        @Override
        public void onSuggestionEntered(String text) {
            suggestions.add(text);
        }

        @Override
        public void debugLog(String message) {
            logs.add(message);
        }

        private int totalActions() {
            return pastes + copies + textLayouts + numericLayouts + greekLayouts + nextLayouts +
                previousLayouts + settings + hides + capsLocks;
        }
    }
}
