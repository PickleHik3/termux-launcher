package com.termux.app.chrome.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * The bundled photo a photo scene (Coastal Sunset) animates: decoded once, at its own size and in
 * ARGB_8888 (the sky is a gradient that RGB_565 would band), held only between {@link #bitmap} and
 * {@link #release}. One owner per live layer, thumbnail or still, shared by that owner's full-size
 * and glass programs. The bitmap is dropped, not recycled: a draw already recorded may still read it.
 */
final class SceneArtwork {

    static final String COASTAL_SUNSET_ASSET = "scenes/coastal_sunset.jpg";
    static final int COASTAL_SUNSET_WIDTH = 746;
    static final int COASTAL_SUNSET_HEIGHT = 1536;
    /** What one decoded artwork may hold: its own pixels, 4 bytes each, with no headroom. */
    static final long BUDGET_BYTES = (long) COASTAL_SUNSET_WIDTH * COASTAL_SUNSET_HEIGHT * 4;

    @NonNull private final Context mContext;
    @NonNull private final String mAsset;
    @Nullable private Bitmap mBitmap;

    SceneArtwork(@NonNull Context context, @NonNull String asset) {
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mAsset = asset;
    }

    /** The decoded artwork, built on first use after a {@link #release}. */
    @NonNull
    synchronized Bitmap bitmap() {
        Bitmap b = mBitmap;
        if (b == null) {
            b = decode();
            mBitmap = b;
        }
        return b;
    }

    /** Lets go of the pixels; the next {@link #bitmap} decodes again. */
    synchronized void release() {
        mBitmap = null;
    }

    /** The bytes the held bitmap costs, from its real pixels; 0 when nothing is held. */
    synchronized long heldBytes() {
        return mBitmap == null ? 0L : mBitmap.getAllocationByteCount();
    }

    @NonNull
    private Bitmap decode() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = mContext.getAssets().open(mAsset)) {
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b != null) return b;
        } catch (IOException ignored) {
            // falls through to the blank below
        }
        // A missing or broken asset must not take the home screen down: a 1 x 1 dark pixel.
        Bitmap blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        blank.eraseColor(0xFF101418);
        return blank;
    }
}
