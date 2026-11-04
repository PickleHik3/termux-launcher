package com.termux.app.chrome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;
import com.termux.shared.reflection.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * The frost under the keyboard's bed on a Display page: one blur layer the compositor fills with
 * what lies behind the bed, the live display included, at full rate.
 *
 * <p>The layer is a child of the display's own {@code SurfaceView} surface, at {@link #LAYER_Z}
 * over the display's buffer. The {@code SurfaceView} is placed relative to the window at -2,
 * under the window's buffer, so the frost composites over the display and under the keyboard's
 * see-through bed and its keys. (A child of the window's surface lands in its Bounds layer,
 * above the window's buffer, and frosts the keys too: seen on pong.) Position and crop are in
 * the {@code SurfaceView}'s space ({@link DisplayKeyboardBlurPolicy#toSurfaceSpace}), and the
 * {@code SurfaceView}'s crop clips the frost to the display: where the bed lies beside the
 * display (resize mode) it is not frosted. The layer holds no buffer and no input, so the
 * keyboard's touches, its reveal, the insets and the display's own input are untouched; it only
 * follows the two rects, synced to the frame the window draws in, and is rebuilt when the
 * display's surface is.
 *
 * <p>Android has no public call that blurs a child layer's background, so the layer is made and
 * dressed through the framework's own hidden calls (an effect layer, its blur radius and corner).
 * Any failure there drops the layer for good in this process and leaves the bed see-through:
 * the keyboard never waits on, or is gated by, the blur.
 *
 * <p>Asked to {@link #show} on every keyboard material pass that paints the Display page's glass,
 * and to {@link #release} on every other pass, on pause and on stop.
 */
public final class DisplayKeyboardBlur {

    private static final String LOG_TAG = "DisplayKeyboardBlur";

    /** Over the display's buffer (z 0), inside the {@code SurfaceView}'s slot under the window. */
    static final int LAYER_Z = 1;

    /** Set once a hidden call fails: no more layers this process, the bed stays see-through. */
    private static boolean sUnavailable;
    @Nullable private static Method sSetEffectLayer;
    @Nullable private static Method sSetBackgroundBlurRadius;
    @Nullable private static Method sSetCornerRadius;
    /** Below API 33 position and crop are hidden too. */
    @Nullable private static Method sSetPosition;
    @Nullable private static Method sSetCrop;

    @NonNull private final Activity mActivity;
    /** Called when Android turns window blur on or off while the keyboard is over the display. */
    @NonNull private final Runnable mOnAvailabilityChanged;

    @Nullable private SurfaceControl mLayer;
    @Nullable private AttachedSurfaceControl mRoot;
    /** The display's surface the layer is a child of; a new one means a new layer. */
    @Nullable private SurfaceControl mParent;
    @Nullable private View mHost;
    /** The display whose surface callbacks are followed while the keyboard is over it. */
    @Nullable private SurfaceView mDisplay;
    @Nullable private ViewTreeObserver mObserved;
    /** Reused for every geometry change from API 33, where a merge empties it on the UI thread. */
    @Nullable private SurfaceControl.Transaction mTransaction;

    private final int[] mLocation = new int[2];
    private final int[] mDisplayLocation = new int[2];
    private final int[] mPosition = new int[2];
    private final Rect mCrop = new Rect();
    private int mX = Integer.MIN_VALUE;
    private int mY = Integer.MIN_VALUE;
    private int mWidth = -1;
    private int mHeight = -1;
    private boolean mVisible;
    private int mRadiusPx;
    private float mCornerPx = -1f;

    /** Whether the keyboard is over the display, so Android's blur switch is listened to. */
    private boolean mWanted;
    private boolean mBlurEnabled = true;
    @Nullable private Consumer<Boolean> mBlurListener;

    private final ViewTreeObserver.OnPreDrawListener mFollower = () -> {
        follow();
        return true;
    };

    /** The display's surface going takes the frost with it; a new one asks for a pass. */
    private final SurfaceHolder.Callback mSurfaceFollower = new SurfaceHolder.Callback() {
        @Override public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (mWanted) mOnAvailabilityChanged.run();
        }

        @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                                             int height) {}

        @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            dropLayer();
        }
    };

    public DisplayKeyboardBlur(@NonNull Activity activity, @NonNull Runnable onAvailabilityChanged) {
        mActivity = activity;
        mOnAvailabilityChanged = onAvailabilityChanged;
    }

    /**
     * Whether Android lets windows blur what is behind them, as its listener last said. True
     * below API 31 too: {@link DisplayKeyboardBlurPolicy} turns the blur off there by API level.
     */
    public boolean crossWindowBlurEnabled() {
        return mBlurEnabled;
    }

    /**
     * Notes that the keyboard is over a Display page, so Android's blur switch is followed
     * ({@link #crossWindowBlurEnabled}) before the radius is decided. Safe to call on every pass.
     */
    public void want() {
        if (mWanted) return;
        mWanted = true;
        if (Build.VERSION.SDK_INT < DisplayKeyboardBlurPolicy.MIN_SDK) return;
        listenForBlurSwitch();
    }

    /**
     * Frosts the rect of {@code host} over {@code display} at {@code radiusPx}, rounded to
     * {@code cornerPx}. A radius of 0, no display, or a layer that cannot be made takes the layer
     * away and leaves the bed see-through. Returns whether the layer is up.
     */
    public boolean show(@NonNull View host, @Nullable SurfaceView display, int radiusPx,
                        float cornerPx) {
        want();
        followSurfaceOf(display);
        if (display == null || radiusPx <= 0 || sUnavailable
            || Build.VERSION.SDK_INT < DisplayKeyboardBlurPolicy.MIN_SDK) {
            dropLayer();
            return false;
        }
        try {
            return showLayer(host, display, radiusPx, cornerPx);
        } catch (Throwable t) {
            giveUp("Could not blur under the keyboard", t);
            return false;
        }
    }

    /** Takes the layer away and stops listening: the keyboard hid, the page changed, or a pause. */
    public void release() {
        dropLayer();
        followSurfaceOf(null);
        if (!mWanted) return;
        mWanted = false;
        if (mBlurListener != null && Build.VERSION.SDK_INT >= DisplayKeyboardBlurPolicy.MIN_SDK) {
            try {
                mActivity.getWindowManager().removeCrossWindowBlurEnabledListener(mBlurListener);
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Could not stop the blur listener", t);
            }
        }
        mBlurListener = null;
    }

    @RequiresApi(31)
    private void listenForBlurSwitch() {
        try {
            WindowManager wm = mActivity.getWindowManager();
            mBlurEnabled = wm.isCrossWindowBlurEnabled();
            mBlurListener = enabled -> {
                boolean now = Boolean.TRUE.equals(enabled);
                if (now == mBlurEnabled) return;
                mBlurEnabled = now;
                if (mWanted) mOnAvailabilityChanged.run();
            };
            wm.addCrossWindowBlurEnabledListener(mActivity.getMainExecutor(), mBlurListener);
        } catch (Throwable t) {
            // No listener means no way to hear battery saver come on: no blur at all, then.
            mBlurEnabled = false;
            mBlurListener = null;
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not follow the window blur switch", t);
        }
    }

    /** Follows {@code display}'s surface callbacks, and only its; null stops following. */
    private void followSurfaceOf(@Nullable SurfaceView display) {
        if (mDisplay == display) return;
        if (mDisplay != null) mDisplay.getHolder().removeCallback(mSurfaceFollower);
        mDisplay = display;
        if (display != null) display.getHolder().addCallback(mSurfaceFollower);
    }

    @RequiresApi(31)
    private boolean showLayer(@NonNull View host, @NonNull SurfaceView display, int radiusPx,
                              float cornerPx) throws Exception {
        AttachedSurfaceControl root = host.getRootSurfaceControl();
        SurfaceControl parent = display.getSurfaceControl();
        if (root == null || !host.isAttachedToWindow() || parent == null || !parent.isValid()) {
            dropLayer();
            return false;
        }
        if (mLayer == null || mRoot != root || mParent != parent) {
            dropLayer();
            if (!resolveHiddenCalls()) return false;
            SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setName("Keyboard frost")
                .setParent(parent)
                .setHidden(true);
            sSetEffectLayer.invoke(builder);
            SurfaceControl layer = builder.build();
            new SurfaceControl.Transaction().setLayer(layer, LAYER_Z).apply();
            mLayer = layer;
            mRoot = root;
            mParent = parent;
            mRadiusPx = 0;
            mCornerPx = -1f;
            resetGeometry();
        }
        if (mHost != host) {
            unfollow();
            mHost = host;
        }
        if (mObserved == null || !mObserved.isAlive() || mObserved != host.getViewTreeObserver()) {
            unfollow();
            mObserved = host.getViewTreeObserver();
            mObserved.addOnPreDrawListener(mFollower);
        }
        if (radiusPx != mRadiusPx || cornerPx != mCornerPx) {
            SurfaceControl.Transaction t = transaction();
            sSetBackgroundBlurRadius.invoke(t, mLayer, radiusPx);
            sSetCornerRadius.invoke(t, mLayer, Math.max(0f, cornerPx));
            mRadiusPx = radiusPx;
            mCornerPx = cornerPx;
            commit(t);
        }
        follow();
        return true;
    }

    /**
     * Moves the layer to the host's rect in the display's space, and hides it while either is not
     * on screen, in the frame the window draws in. Runs before every frame the window draws while
     * the layer is up; at rest it costs two location reads and a few compares, and nothing is
     * sent. A display whose surface was replaced under it drops the layer and asks for a pass.
     */
    private void follow() {
        SurfaceControl layer = mLayer;
        View host = mHost;
        SurfaceView display = mDisplay;
        if (layer == null || host == null || Build.VERSION.SDK_INT < DisplayKeyboardBlurPolicy.MIN_SDK)
            return;
        try {
            SurfaceControl parent = display == null ? null : display.getSurfaceControl();
            if (parent != mParent || parent == null || !parent.isValid()) {
                dropLayer();
                if (mWanted && parent != null) mOnAvailabilityChanged.run();
                return;
            }
            boolean visible = host.isAttachedToWindow() && host.isShown() && display.isShown()
                && host.getWidth() > 0 && host.getHeight() > 0;
            host.getLocationInWindow(mLocation);
            display.getLocationInWindow(mDisplayLocation);
            // The on-screen offset carries any scale over both views (the Appearance editor's
            // preview); the display's surface carries it too, so the frost, its child, is placed
            // at the unscaled offset and its size, corner and blur stay in surface units.
            float scaleX = 1f;
            float scaleY = 1f;
            for (android.view.ViewParent p = display.getParent(); p instanceof View;
                 p = p.getParent()) {
                scaleX *= ((View) p).getScaleX();
                scaleY *= ((View) p).getScaleY();
            }
            DisplayKeyboardBlurPolicy.toSurfaceSpace(mLocation, mDisplayLocation, scaleX, scaleY,
                mPosition);
            int x = mPosition[0];
            int y = mPosition[1];
            int w = host.getWidth();
            int h = host.getHeight();
            if (visible == mVisible && x == mX && y == mY && w == mWidth && h == mHeight) return;
            SurfaceControl.Transaction t = transaction();
            if (x != mX || y != mY) sSetPosition.invoke(t, layer, (float) x, (float) y);
            if (w != mWidth || h != mHeight) {
                mCrop.set(0, 0, Math.max(0, w), Math.max(0, h));
                sSetCrop.invoke(t, layer, mCrop);
            }
            if (visible != mVisible) t.setVisibility(layer, visible);
            mX = x;
            mY = y;
            mWidth = w;
            mHeight = h;
            mVisible = visible;
            commit(t);
        } catch (Throwable t) {
            giveUp("Could not move the keyboard's blur", t);
        }
    }

    /** One transaction for every change from API 33; a fresh one below it, see {@link #commit}. */
    @NonNull
    private SurfaceControl.Transaction transaction() {
        if (Build.VERSION.SDK_INT < 33) return new SurfaceControl.Transaction();
        if (mTransaction == null) mTransaction = new SurfaceControl.Transaction();
        return mTransaction;
    }

    /**
     * Sends {@code t} with the next frame the window draws, so the frost moves with the keyboard
     * rather than a frame ahead of it, or at once when no frame is coming. From API 33 the merge
     * happens on this thread and empties {@code t}, which is why it can be reused; on 31 and 32
     * it is merged later from the render thread, so those levels get a fresh one each time.
     */
    @RequiresApi(31)
    private void commit(@NonNull SurfaceControl.Transaction t) {
        AttachedSurfaceControl root = mRoot;
        if (root == null || !root.applyTransactionOnDraw(t)) t.apply();
    }

    private void unfollow() {
        if (mObserved != null && mObserved.isAlive()) mObserved.removeOnPreDrawListener(mFollower);
        mObserved = null;
    }

    private void resetGeometry() {
        mX = Integer.MIN_VALUE;
        mY = Integer.MIN_VALUE;
        mWidth = -1;
        mHeight = -1;
        mVisible = false;
    }

    /** Takes the layer off the window and frees it, keeping the blur-switch listener. */
    private void dropLayer() {
        unfollow();
        mHost = null;
        SurfaceControl layer = mLayer;
        mLayer = null;
        mRoot = null;
        mParent = null;
        mRadiusPx = 0;
        mCornerPx = -1f;
        resetGeometry();
        if (layer == null || Build.VERSION.SDK_INT < DisplayKeyboardBlurPolicy.MIN_SDK) return;
        try {
            new SurfaceControl.Transaction().reparent(layer, null).apply();
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not take the keyboard's blur off", t);
        }
        try {
            layer.release();
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not free the keyboard's blur", t);
        }
    }

    private void giveUp(@NonNull String message, @NonNull Throwable t) {
        sUnavailable = true;
        Logger.logStackTraceWithMessage(LOG_TAG, message + "; the keyboard stays see-through", t);
        dropLayer();
    }

    /** Looks the hidden calls up once; false, and never again, when one is missing. */
    @SuppressLint({"DiscouragedPrivateApi", "SoonBlockedPrivateApi", "BlockedPrivateApi"})
    private static boolean resolveHiddenCalls() {
        if (sUnavailable) return false;
        if (sSetEffectLayer != null) return true;
        try {
            ReflectionUtils.bypassHiddenAPIReflectionRestrictions();
            Class<SurfaceControl.Transaction> tx = SurfaceControl.Transaction.class;
            Method effect = SurfaceControl.Builder.class.getDeclaredMethod("setEffectLayer");
            Method blur = tx.getDeclaredMethod("setBackgroundBlurRadius", SurfaceControl.class,
                int.class);
            Method corner = tx.getDeclaredMethod("setCornerRadius", SurfaceControl.class,
                float.class);
            Method position = tx.getMethod("setPosition", SurfaceControl.class, float.class,
                float.class);
            Method crop;
            try {
                crop = tx.getMethod("setCrop", SurfaceControl.class, Rect.class);
            } catch (NoSuchMethodException e) {
                crop = tx.getDeclaredMethod("setWindowCrop", SurfaceControl.class, Rect.class);
            }
            effect.setAccessible(true);
            blur.setAccessible(true);
            corner.setAccessible(true);
            position.setAccessible(true);
            crop.setAccessible(true);
            sSetBackgroundBlurRadius = blur;
            sSetCornerRadius = corner;
            sSetPosition = position;
            sSetCrop = crop;
            sSetEffectLayer = effect;
            return true;
        } catch (Throwable t) {
            sUnavailable = true;
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Window blur calls are missing; the keyboard stays see-through", t);
            return false;
        }
    }
}
