package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;

public class TerminalModifiersTest {

    @Test
    public void convertsOnlyTerminalAndAndroidEventModifiers() {
        Pointers.Modifiers modifiers = Pointers.Modifiers.EMPTY
            .with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL))
            .with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.ALT))
            .with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.SHIFT))
            .with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.META))
            .with_extra_mod(KeyValue.makeInternalModifier(KeyValue.Modifier.FN));

        TerminalModifiers converted = TerminalModifiers.from(modifiers);

        assertTrue(converted.isCtrl());
        assertTrue(converted.isAlt());
        assertTrue(converted.isShift());
        assertTrue(converted.isMeta());
        assertTrue((converted.toKeyEventMetaState() & KeyEvent.META_CTRL_LEFT_ON) != 0);
        assertTrue((converted.toKeyEventMetaState() & KeyEvent.META_ALT_LEFT_ON) != 0);
        assertTrue((converted.toKeyEventMetaState() & KeyEvent.META_SHIFT_LEFT_ON) != 0);
        assertTrue((converted.toKeyEventMetaState() & KeyEvent.META_META_LEFT_ON) != 0);
        assertFalse((converted.toKeyEventMetaState() & KeyEvent.META_FUNCTION_ON) != 0);
    }

    @Test
    public void metaNeverBecomesAlt() {
        TerminalModifiers converted = TerminalModifiers.from(Pointers.Modifiers.EMPTY.with_extra_mod(
            KeyValue.makeInternalModifier(KeyValue.Modifier.META)));

        assertTrue(converted.isMeta());
        assertFalse(converted.isAlt());
    }
}
