package com.termux.app.chrome;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

import java.util.function.BooleanSupplier;

/**
 * The keyboard's frost over the X11 screen on the Display page, as a surface of the glass service
 * (ADR 0029 section 2): the Compositor kind. The activity says only where the keyboard's bed is,
 * what its Frost is and when it hides; the service decides whether the compositor's blur is
 * granted or the bed falls back to Wallpaper (see-through over the live display, its Opacity and
 * Tint unchanged), and tells this surface through the same policy events as every other kind.
 *
 * <ul>
 *   <li>The grant follows {@link DisplayKeyboardBlurPolicy#compositorAvailable}: Android 12 or
 *   later, Android's window blur switch on, battery saver off and no failure of the layer in this
 *   process. Each input moving is a policy event ({@link GlassService#setCompositorOn}).</li>
 *   <li>The layer follows its host in the glass commit, the frame table's last step, on every frame
 *   that draws while the frost is shown; it registers no frame hook of its own.</li>
 *   <li>A layer that cannot be made or dressed gives up for the process and the service falls back
 *   to Wallpaper at once.</li>
 * </ul>
 */
public final class KeyboardFrost implements GlassEntry.Frame, GlassService.Redress {

    /** The registry's element for the keyboard's frost. */
    public static final String ELEMENT = "keyboard-frost";

    /** The compositor layer under the bed: {@link DisplayKeyboardBlur}, or a stand-in in a test. */
    public interface Layer {
        /** Notes that the keyboard is over a Display page, so Android's blur switch is followed. */
        void want();

        /** Android's window blur switch as it last said. */
        boolean crossWindowBlurEnabled();

        /** A call the layer needs is missing or failed: no layer is made again in this process. */
        boolean unavailable();

        /** Frosts the rect of {@code host} over {@code display}; a radius of 0 takes it away. */
        boolean show(@NonNull View host, @Nullable SurfaceView display, int radiusPx,
                     float cornerPx);

        /** Moves the layer to where the host is; asked on every frame that draws. */
        void follow();

        /** Takes the layer away and stops listening. */
        void release();
    }

    @NonNull private final GlassService mGlass;
    @NonNull private final BooleanSupplier mPowerSave;
    private final int mSdk;
    @NonNull private final Layer mLayer;

    @Nullable private View mHost;
    @Nullable private SurfaceView mDisplay;
    private int mFrostDp;
    private float mDensity = 1f;
    private float mCornerPx;
    @Nullable private GlassKind mGranted;

    public KeyboardFrost(@NonNull android.app.Activity activity, @NonNull GlassService glass) {
        mGlass = glass;
        mSdk = Build.VERSION.SDK_INT;
        PowerManager power = activity.getSystemService(PowerManager.class);
        mPowerSave = () -> power != null && power.isPowerSaveMode();
        mLayer = new DisplayKeyboardBlur(activity, this::onAvailabilityChanged);
    }

    KeyboardFrost(@NonNull GlassService glass, int sdk, @NonNull BooleanSupplier powerSave,
                  @NonNull Layer layer) {
        mGlass = glass;
        mSdk = sdk;
        mPowerSave = powerSave;
        mLayer = layer;
    }

    /**
     * Frosts the bed {@code host} over {@code display} at {@code frostDp} (the keyboard look's
     * Frost on the Display pages), rounded to {@code cornerPx}. Called on every keyboard material
     * pass that paints the Display page's glass; the first call registers, the rest restate. The
     * kind granted now: Compositor, or Wallpaper (no layer, the bed stays see-through).
     */
    @NonNull
    public GlassKind show(@NonNull View host, @Nullable SurfaceView display, int frostDp,
                          float density, float cornerPx) {
        mHost = host;
        mDisplay = display;
        mFrostDp = frostDp;
        mDensity = density;
        mCornerPx = cornerPx;
        // Android's switch is read before the grant, so the first frame is granted as it stands.
        mLayer.want();
        updatePolicy();
        GlassKind granted = mGlass.register(new GlassEntry(ELEMENT, host, GlassKind.COMPOSITOR,
            GlassEntry.PRIORITY_DOCK, WindowLayer.GROUND, this), this);
        if (granted == null) {
            // A second view for the element: the registry refuses a twin; the bed stays see-through.
            granted = GlassKind.WALLPAPER;
        }
        apply(granted);
        return granted;
    }

    /** Takes the frost away and lets the entry go: the keyboard hid, the page changed, a pause. */
    public void release() {
        mHost = null;
        mDisplay = null;
        mGranted = null;
        mLayer.release();
        mGlass.unregister(ELEMENT);
    }

    /** The kind granted while shown; null while released. */
    @Nullable
    public GlassKind granted() {
        return mGranted;
    }

    /** A policy event moved the kind: the frost goes up or comes down at once. */
    @Override
    public void onGranted(@NonNull GlassKind kind) {
        apply(kind);
    }

    /** Every frame that draws while shown: the layer follows the bed. */
    @Override
    public void onFrame() {
        if (mGranted == GlassKind.COMPOSITOR) mLayer.follow();
    }

    /** Android's blur switch turned, the display's surface is new or the layer failed. */
    private void onAvailabilityChanged() {
        if (mHost == null) return;
        updatePolicy();
        GlassKind granted = mGlass.granted(ELEMENT);
        if (granted != null) apply(granted);
    }

    /** The service's Compositor input, from Android's switch, battery saver and the layer's health. */
    private void updatePolicy() {
        mGlass.setCompositorOn(DisplayKeyboardBlurPolicy.compositorAvailable(mSdk,
            mLayer.crossWindowBlurEnabled(), mPowerSave.getAsBoolean(), mLayer.unavailable()));
    }

    private void apply(@NonNull GlassKind kind) {
        View host = mHost;
        if (host == null) return;
        mGranted = kind;
        int radiusPx = kind == GlassKind.COMPOSITOR
            ? DisplayKeyboardBlurPolicy.blurRadiusPx(mSdk, mLayer.crossWindowBlurEnabled(),
                mPowerSave.getAsBoolean(), mFrostDp, mDensity)
            : 0;
        mLayer.show(host, mDisplay, radiusPx, mCornerPx);
        // The layer may have given up in that call: the service hears it before the next frame.
        if (kind == GlassKind.COMPOSITOR && mLayer.unavailable()) updatePolicy();
    }
}
