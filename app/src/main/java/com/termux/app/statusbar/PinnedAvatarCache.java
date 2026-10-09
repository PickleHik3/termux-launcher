package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sender pictures for the pinned cards, decoded off the main thread and kept as small circles.
 *
 * <p>An {@link Icon} from a notification can be a content URI, a resource in another package or a
 * full-size bitmap, so {@link Icon#loadDrawable} runs on one background thread and the result is
 * drawn once into a circular bitmap no larger than {@link #MAX_PX} a side. The cache holds the last
 * {@link #CAPACITY} of them, keyed by notification key and post time, so a new message in the same
 * conversation (a new post) loads its own picture. Until a picture arrives — and for a card with
 * none at all — {@link #get} answers null and the card draws the app icon in its place.</p>
 */
final class PinnedAvatarCache {

    /** The largest side a cached picture is kept at, whatever the card asks for. */
    static final int MAX_PX = 128;
    private static final int CAPACITY = 16;
    /** A picture that failed to load, remembered so it is not retried every frame. */
    private static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8);

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pinned-avatar");
        thread.setDaemon(true);
        return thread;
    });

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> mCache = new LinkedHashMap<String, Bitmap>(CAPACITY, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > CAPACITY;
        }
    };
    private final Set<String> mLoading = new HashSet<>();

    PinnedAvatarCache(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    /**
     * The circular picture for {@code pin}, or null while it loads or when it has none. A load is
     * started on the first ask; {@code onLoaded} runs on the main thread when it lands.
     */
    @MainThread
    @Nullable
    Bitmap get(@NonNull PinnedNotification pin, int sizePx, @NonNull Runnable onLoaded) {
        Icon icon = pin.avatar;
        if (icon == null) return null;
        String id = pin.key + '@' + pin.postTime;
        Bitmap cached = mCache.get(id);
        if (cached != null) return cached == NONE ? null : cached;
        if (mLoading.add(id)) {
            int side = Math.max(1, Math.min(MAX_PX, sizePx));
            LOADER.execute(() -> {
                Bitmap circle = load(icon, side);
                mMain.post(() -> {
                    mLoading.remove(id);
                    mCache.put(id, circle == null ? NONE : circle);
                    if (circle != null) onLoaded.run();
                });
            });
        }
        return null;
    }

    @Nullable
    private Bitmap load(@NonNull Icon icon, int side) {
        try {
            Drawable drawable = icon.loadDrawable(mContext);
            if (drawable == null) return null;
            Bitmap square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(square);
            if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                // Centre-crop a non-square picture rather than squash it.
                Bitmap source = ((BitmapDrawable) drawable).getBitmap();
                int w = source.getWidth();
                int h = source.getHeight();
                int crop = Math.min(w, h);
                android.graphics.Rect src = new android.graphics.Rect((w - crop) / 2,
                    (h - crop) / 2, (w - crop) / 2 + crop, (h - crop) / 2 + crop);
                canvas.drawBitmap(source, src, new android.graphics.Rect(0, 0, side, side),
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            } else {
                drawable.setBounds(0, 0, side, side);
                drawable.draw(canvas);
            }
            Bitmap circle = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            new Canvas(circle).drawCircle(side / 2f, side / 2f, side / 2f, paint);
            square.recycle();
            return circle;
        } catch (Throwable throwable) {
            return null;
        }
    }
}
