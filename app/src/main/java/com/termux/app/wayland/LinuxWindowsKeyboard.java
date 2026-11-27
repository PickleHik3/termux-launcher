package com.termux.app.wayland;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.termux.KeyboardPreviewFactory;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardLayoutSource;
import com.termux.app.terminal.inappkeyboard.KeyboardLayoutFiles;
import com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;
import juloo.keyboard2.Pointers;

/**
 * The launcher's keyboard on the Linux windows screen: the user's layout, extra keys, colours
 * and shape (built as the settings preview builds it, {@link KeyboardPreviewFactory}), with every
 * key typed into the focused window as plain key presses ({@link LinuxWindowKeys}). Its own keys
 * act on it here: the number pad and back, and hide.
 */
final class LinuxWindowsKeyboard implements Config.IKeyEventHandler {

    private static final String LOG_TAG = "TLWayland";

    /** What the keyboard does to the screen. */
    interface Host {
        /** One Android key code down or up, for the focused window. */
        void typeKey(int keyCode, boolean down);

        /** The keyboard's own hide key. */
        void hideKeyboard();
    }

    @NonNull private final Host mHost;
    @NonNull private final LinuxWindowKeys.Sink mSink;
    @Nullable private Keyboard2View mView;
    @Nullable private String mLayoutId;
    @Nullable private LayoutModifier.LayoutOptions mOptions;
    @Nullable private KeyboardData mMain;
    @Nullable private KeyboardData mNumeric;

    LinuxWindowsKeyboard(@NonNull Host host) {
        mHost = host;
        mSink = host::typeKey;
    }

    /** The keyboard view, built on first use; null when the preferences cannot be read. */
    @Nullable
    View view(@NonNull Context context) {
        if (mView != null) return mView;
        try {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
            if (preferences == null) return null;
            Resources res = context.getResources();
            mLayoutId = InAppKeyboardLayoutSource.activeId(
                LauncherKeyboardLayouts.parseSelection(res, preferences.getInAppKeyboardLayouts()),
                preferences.getInAppKeyboardActiveLayout());
            mOptions = InAppKeyboardLayoutSource.textLayoutOptions(preferences.getInAppKeyboardExtraKeys());
            mView = KeyboardPreviewFactory.createTyping(context, preferences,
                InAppKeyboardColorScheme.fromJson(context, preferences.getInAppKeyboardColorScheme()),
                mLayoutId, this);
        } catch (RuntimeException e) {
            // A broken layout or preference must not take the screen down; it just has no keyboard.
            Log.w(LOG_TAG, "keyboard: " + e);
            mView = null;
        }
        return mView;
    }

    /** Drops held modifiers and half-done gestures, as the keyboard goes down. */
    void reset() {
        if (mView != null) mView.resetInputState();
    }

    @Override
    public void key_down(KeyValue value, boolean isSwipe) {
        // A space-bar swipe's first tick comes on the way down; later ticks come as key_up.
        if (value == null || value.getKind() != KeyValue.Kind.Slider) return;
        LinuxWindowKeys.send(KeyValue.sliderKey(value.getSlider(), 1), false, false, false, false, mSink);
    }

    @Override
    public void key_up(KeyValue value, Pointers.Modifiers modifiers) {
        if (value == null) return;
        if (value.getKind() == KeyValue.Kind.Event && onKeyboardEvent(value.getEvent())) return;
        boolean ctrl = modifiers != null && modifiers.has(KeyValue.Modifier.CTRL);
        boolean alt = modifiers != null && modifiers.has(KeyValue.Modifier.ALT);
        boolean shift = modifiers != null && modifiers.has(KeyValue.Modifier.SHIFT);
        boolean meta = modifiers != null && modifiers.has(KeyValue.Modifier.META);
        LinuxWindowKeys.send(value, ctrl, alt, shift, meta, mSink);
    }

    @Override
    public void mods_changed(Pointers.Modifiers modifiers) {}

    @Override
    public void suggestion_entered(String text) {}

    /** The keyboard's own keys; true when the event was one of them. */
    private boolean onKeyboardEvent(@Nullable KeyValue.Event event) {
        if (event == null) return true;
        switch (event) {
            case SWITCH_NUMERIC:
                show(numeric());
                return true;
            case SWITCH_TEXT:
            case SWITCH_BACK_EMOJI:
            case SWITCH_BACK_CLIPBOARD:
                show(main());
                return true;
            case HIDE_SELF:
                mHost.hideKeyboard();
                return true;
            case ACTION:
                return false;
            default:
                // Emoji, clipboard, voice, settings and other layouts are the terminal's keyboard's.
                return true;
        }
    }

    private void show(@Nullable KeyboardData layout) {
        if (mView != null && layout != null) mView.setKeyboard(layout);
    }

    @Nullable
    private KeyboardData main() {
        if (mMain == null && mView != null && mOptions != null) {
            mMain = InAppKeyboardLayoutSource.load(mView.getContext(), KeyboardLayoutFiles.defaultHome(),
                mLayoutId, mOptions);
        }
        return mMain;
    }

    @Nullable
    private KeyboardData numeric() {
        if (mNumeric == null && mView != null && mOptions != null) {
            mNumeric = InAppKeyboardLayoutSource.loadBundled(mView.getResources(),
                juloo.keyboard2.R.xml.numeric, mOptions);
        }
        return mNumeric;
    }
}
