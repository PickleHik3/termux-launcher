package com.termux.app.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Reads the battery for the status bar's battery chip: one sticky {@code ACTION_BATTERY_CHANGED}
 * receiver, registered only while the chip is on and the window is started and dropped otherwise,
 * so a switched-off chip or a backgrounded launcher costs no wake-ups. No permission, no polling,
 * no timer. Registering returns the sticky broadcast, which paints the first value at once.
 * Main thread only.
 */
final class BatteryChipController {

    interface Listener {
        void onBattery(@NonNull BatteryChipState state);
    }

    @NonNull private final Context mContext;
    @NonNull private final Listener mListener;
    @NonNull private BatteryChipState mLatest = BatteryChipState.UNKNOWN;
    private boolean mListening;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            publish(intent);
        }
    };

    BatteryChipController(@NonNull Context context, @NonNull Listener listener) {
        mContext = context;
        mListener = listener;
    }

    /** The last reading, or the unknown chip before any. */
    @NonNull BatteryChipState latest() {
        return mLatest;
    }

    /** Starts or stops listening; a repeat of the current answer does nothing. */
    void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            Intent sticky = null;
            try {
                sticky = mContext.registerReceiver(mReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } catch (RuntimeException ignored) {
                // The chip stays at its last value rather than the home screen falling over.
            }
            publish(sticky);
        } else {
            try {
                mContext.unregisterReceiver(mReceiver);
            } catch (RuntimeException ignored) {
                // Never registered, or already gone.
            }
        }
    }

    private void publish(@Nullable Intent intent) {
        if (intent == null) return;
        BatteryChipState state = BatteryChipState.of(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1),
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
        if (state.equals(mLatest)) return;
        mLatest = state;
        mListener.onBattery(state);
    }
}
