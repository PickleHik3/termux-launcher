package com.termux.app.x11;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.shared.logger.Logger;

import juloo.keyboard2.Keyboard2View;

/**
 * Where mouse mode's touchpad stands in the keyboard's frame: the whole of it, or the parting of
 * a split keyboard, and the pad's keyboard mode button. Holds the pad. Main thread only.
 */
public final class DisplayTouchpadSeat {
    private static final String LOG_TAG = "DisplayTouchpadSeat";

    /** What the seat asks of its window. */
    public interface Host {
        @Nullable X11PaneFrame displayFrame();
        @Nullable TermuxInAppKeyboard inAppKeyboard();
        boolean isInAppKeyboardShown();
        @NonNull PlaceLayout arrangementLayout();
        @NonNull PlaceLayout.KeyboardForm inAppKeyboardForm();
        @Nullable PlaceLayoutStore placeLayoutStore();
        @NonNull PlaceOrientation currentPlaceOrientation();
        /** The keyboard mode changed: the geometry and the chrome follow, now. */
        void keyboardModeChanged();
    }

    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    @Nullable private DisplayTouchpadView mDisplayTouchpad;

    public DisplayTouchpadSeat(@NonNull Context context, @NonNull Host host) {
        mContext = context;
        mHost = host;
    }

    /** The pad while it is up; null otherwise. */
    @Nullable
    public DisplayTouchpadView touchpad() {
        return mDisplayTouchpad;
    }

    public void setTouchpad(@Nullable DisplayTouchpadView pad) {
        mDisplayTouchpad = pad;
    }

    /**
     * Sizes the touchpad to the keyboard frame it stands in: the whole of it, or the parting of a
     * split keyboard. Beside a pad in the parting the halves keep typing, so their keys are lit.
     */
    public void applyDisplayTouchpadFrame(@NonNull DisplayTouchpadView pad,
                                           @Nullable View keyboardView) {
        Rect gap = splitKeyboardTouchpadGap(keyboardView);
        FrameLayout.LayoutParams params = seatDisplayTouchpad(pad, keyboardView, gap);
        if (!DisplayTouchpadPlacement.describes(pad.getLayoutParams(), params)) {
            pad.setLayoutParams(params);
        }
        if (gap != null && keyboardView != null && keyboardView.getAlpha() != 1f) {
            keyboardView.animate().cancel();
            keyboardView.setAlpha(1f);
        }
    }

    public Rect splitKeyboardTouchpadGap(@Nullable View keyboardView) {
        if (!(keyboardView instanceof Keyboard2View) || mHost.inAppKeyboard() == null
            || mHost.inAppKeyboard().getForm() != PlaceLayout.KeyboardForm.SPLIT) {
            return null;
        }
        Rect gap = new Rect();
        return ((Keyboard2View) keyboardView).getSplitGapBounds(gap) ? gap : null;
    }

    public FrameLayout.LayoutParams seatDisplayTouchpad(
            @NonNull DisplayTouchpadView pad, @Nullable View keyboardView,
            @Nullable Rect gap) {
        float density = mContext.getResources().getDisplayMetrics().density;
        Rect seat = null;
        if (gap != null && keyboardView instanceof Keyboard2View) {
            seat = new Rect();
            if (!((Keyboard2View) keyboardView).getSplitGapKeyBounds(seat)) seat = null;
        }
        boolean inGap = DisplayTouchpadPlacement.standsInGap(gap, seat, density);
        Keyboard2View keys = inGap ? (Keyboard2View) keyboardView : null;
        pad.setInSplitGap(inGap, keys == null ? 0f : keys.getKeyCapRadiusPx(),
            keys == null ? 0 : keys.getKeyCapColor());
        syncDisplayTouchpadModeSwitch(pad);
        return DisplayTouchpadPlacement.padParams(gap, seat,
            keyboardView == null ? 0 : keyboardView.getHeight(), density);
    }

    /**
     * The pad's keyboard mode button shows this orientation's keyboard mode, and is there only
     * where the mode decides anything: over a floating keyboard it is not.
     */
    public void syncDisplayTouchpadModeSwitch(
            @NonNull DisplayTouchpadView pad) {
        PlaceLayout layout = mHost.arrangementLayout();
        pad.setKeyboardModeSwitch(
            DisplayTouchpadPlacement.offersModeSwitch(mHost.inAppKeyboardForm()),
            layout.keyboardMode == PlaceLayout.KeyboardMode.OVERLAY);
    }

    /**
     * The pad's keyboard mode button: this orientation's keyboard mode flips between Overlay and
     * Resize, saved where the Layout editor reads it. The pad stands in the keyboard's frame, so
     * the one geometry pass that follows floats it over the display or gives it the display's
     * room, as it would the keyboard. The display holds its size over that pass and takes the new
     * one after it, so the X screen is resized once; an overlaying keyboard also paints opaque
     * rather than glass, so the stack is re-rendered with it.
     */
    public void switchDisplayKeyboardMode() {
        PlaceLayoutStore store = mHost.placeLayoutStore();
        if (store == null) return;
        X11PaneFrame frame = mHost.displayFrame();
        // Only a docked keyboard frame on screen moves the display's height when the mode flips;
        // without one the switch is a saved setting, and holding the size would only freeze it.
        if (frame != null && mHost.isInAppKeyboardShown()
                && DisplayTouchpadPlacement.offersModeSwitch(
                    mHost.inAppKeyboardForm())) {
            frame.holdHostSizeOverNextLayout();
        }
        PlaceLayout.KeyboardMode mode = DisplayTouchpadPlacement
            .switchKeyboardMode(store, mHost.currentPlaceOrientation());
        Logger.logInfo(LOG_TAG, "Touchpad switched the keyboard mode to " + mode);
        if (mDisplayTouchpad != null) syncDisplayTouchpadModeSwitch(mDisplayTouchpad);
        mHost.keyboardModeChanged();
    }
}
