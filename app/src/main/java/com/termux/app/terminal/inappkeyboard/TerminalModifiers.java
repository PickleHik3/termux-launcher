package com.termux.app.terminal.inappkeyboard;

import android.view.KeyEvent;

import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Pointers;

/** Immutable terminal-facing subset of the keyboard's modifier snapshot. */
public final class TerminalModifiers {

    public static final TerminalModifiers NONE = new TerminalModifiers(false, false, false, false);

    private final boolean mCtrl;
    private final boolean mAlt;
    private final boolean mShift;
    private final boolean mMeta;

    private TerminalModifiers(boolean ctrl, boolean alt, boolean shift, boolean meta) {
        mCtrl = ctrl;
        mAlt = alt;
        mShift = shift;
        mMeta = meta;
    }

    public static TerminalModifiers from(Pointers.Modifiers modifiers) {
        if (modifiers == null || modifiers.size() == 0)
            return NONE;
        return new TerminalModifiers(
            modifiers.has(KeyValue.Modifier.CTRL),
            modifiers.has(KeyValue.Modifier.ALT),
            modifiers.has(KeyValue.Modifier.SHIFT),
            modifiers.has(KeyValue.Modifier.META));
    }

    public boolean isCtrl() {
        return mCtrl;
    }

    public boolean isAlt() {
        return mAlt;
    }

    public boolean isShift() {
        return mShift;
    }

    public boolean isMeta() {
        return mMeta;
    }

    TerminalModifiers withCtrl() {
        if (mCtrl)
            return this;
        return new TerminalModifiers(true, mAlt, mShift, mMeta);
    }

    public int toKeyEventMetaState() {
        int metaState = 0;
        if (mCtrl)
            metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (mAlt)
            metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (mShift)
            metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        if (mMeta)
            metaState |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
        return metaState;
    }
}
