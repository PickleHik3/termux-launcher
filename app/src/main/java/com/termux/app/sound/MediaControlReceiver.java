package com.termux.app.sound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The media notification's buttons. Not exported: only the notification's own pending intents
 * reach it, so no other app can drive a player through it.
 */
public final class MediaControlReceiver extends BroadcastReceiver {

    /** Actions are this plus one of {@link MediaBridge}'s {@code ACTION_*} names. */
    static final String ACTION_PREFIX = "com.termux.app.sound.media.";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (action == null || !action.startsWith(ACTION_PREFIX)) return;
        LauncherSound.get(context).onMediaControl(action.substring(ACTION_PREFIX.length()));
    }
}
