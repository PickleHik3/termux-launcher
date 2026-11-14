package com.termux.app.terminal.extrakeys;

import com.termux.shared.termux.extrakeys.SpecialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The CTRL, ALT, SHIFT and FN state every key of the extra-keys bar shares. A tap arms a modifier
 * for the next key, a hold locks it until the next tap or hold.
 */
public final class ExtraKeyModifiers {

    public enum State { OFF, ARMED, LOCKED }

    public interface Listener {
        void onModifierChanged(SpecialButton button, State state);
    }

    private static final SpecialButton[] BUTTONS = {
        SpecialButton.CTRL, SpecialButton.ALT, SpecialButton.SHIFT, SpecialButton.FN
    };

    private final State[] mStates = {State.OFF, State.OFF, State.OFF, State.OFF};
    private final List<Listener> mListeners = new ArrayList<>();

    public static boolean isModifierKey(String key) {
        return key != null && indexOf(SpecialButton.valueOf(key)) >= 0;
    }

    private static int indexOf(SpecialButton button) {
        if (button == null) return -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            if (BUTTONS[i] == button) return i;
        }
        return -1;
    }

    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public State state(SpecialButton button) {
        int i = indexOf(button);
        return i < 0 ? State.OFF : mStates[i];
    }

    public void tap(SpecialButton button) {
        int i = indexOf(button);
        if (i < 0) return;
        set(i, mStates[i] == State.OFF ? State.ARMED : State.OFF);
    }

    public void hold(SpecialButton button) {
        int i = indexOf(button);
        if (i < 0) return;
        set(i, mStates[i] == State.OFF ? State.LOCKED : State.OFF);
    }

    /** True when the modifier is armed or locked; consuming turns an armed one off. */
    public boolean read(SpecialButton button, boolean consume) {
        int i = indexOf(button);
        if (i < 0) return false;
        State s = mStates[i];
        if (s == State.OFF) return false;
        if (consume && s == State.ARMED) set(i, State.OFF);
        return true;
    }

    /** Every modifier off; each listener hears about the ones that changed, none when all were off. */
    public void clear() {
        for (int i = 0; i < mStates.length; i++) set(i, State.OFF);
    }

    private void set(int i, State next) {
        if (mStates[i] == next) return;
        mStates[i] = next;
        // Index loop: no iterator allocation, and a listener may remove itself.
        for (int n = 0; n < mListeners.size(); n++) {
            mListeners.get(n).onModifierChanged(BUTTONS[i], next);
        }
    }
}
