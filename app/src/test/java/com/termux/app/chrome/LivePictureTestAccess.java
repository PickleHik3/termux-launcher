package com.termux.app.chrome;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/** The recorder's side of a live picture, for tests outside this package. */
public final class LivePictureTestAccess {

    private LivePictureTestAccess() {}

    /** What the recorder's pre-draw does after a whole recording. */
    public static void recorded(@NonNull LivePicture picture) {
        picture.recorded();
    }

    /** Makes the picture's nodes, as the recorder's first recording does. */
    @RequiresApi(Build.VERSION_CODES.S)
    public static void makeNodes(@NonNull LivePicture picture, int width, int height) {
        picture.recordNode(width, height);
    }

    /** What the recorder does to a picture whose surface hid. */
    public static void trim(@NonNull LivePicture picture) {
        picture.trim();
    }

    /** Whether released nodes are still waiting to be emptied on the next frame. */
    public static boolean retiring(@NonNull LivePicture picture) {
        return picture.retiring();
    }
}
