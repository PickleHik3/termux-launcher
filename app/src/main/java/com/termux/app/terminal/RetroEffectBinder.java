package com.termux.app.terminal;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Random;

/**
 * Keeps one view drawn through the retro effect. Before every frame the view's window draws it
 * re-reads the view's size, its card and where it stands on the screen, and wraps a new effect
 * only when one of those has moved — so a surface at rest costs a comparison, and one that slides
 * (the keyboard rising, a dragged card, a wall page) keeps its scanlines fixed to the screen
 * instead of carrying them along.
 *
 * <p>Nothing else may set a render effect on the view this binds; NONE, or a phone below API 33,
 * takes off only the effect this binder put on.
 */
public final class RetroEffectBinder {

    /** The card a view's effect is cut to, in its own px. */
    public interface Shape {
        /**
         * Writes {@code left, top, right, bottom} of the card into {@code rect} and returns its
         * corner radius.
         */
        float card(@NonNull View view, @NonNull float[] rect);
    }

    /**
     * A card with a screen inside it: the effect is drawn on the screen only, and the card between
     * the two (a pane's bezel, {@link PaneBezel}) is drawn as it is.
     */
    public interface ScreenShape extends Shape {
        /**
         * Writes {@code left, top, right, bottom} of the screen, and the bottom it is laid out to
         * (the bend's, below the drawn bottom while a travel shortens it), into {@code rect}, and
         * returns its corner radius.
         */
        float screen(@NonNull View view, @NonNull float[] rect);
    }

    /**
     * The view's own bounds, square: for a container whose children clip their own shapes. The
     * effect keeps every pixel's coverage, so the corners those children cut stay cut.
     */
    public static final Shape BOUNDS = (view, rect) -> {
        rect[0] = 0f;
        rect[1] = 0f;
        rect[2] = view.getWidth();
        rect[3] = view.getHeight();
        return 0f;
    };

    @NonNull private final View mView;
    @NonNull private final Shape mShape;
    private final float mBend;
    private final float mVignette;
    /** The surface's own CRT parts ({@link RetroUniforms#surface}); package-visible for tests. */
    final float mAberr;
    final float mAberrFlat;
    final float mRecess;
    @NonNull private final PaneRetroEffect mEffect = new PaneRetroEffect();
    @NonNull private final PreDrawHook mHook;

    @NonNull private PaneRetroStyle mStyle = PaneRetroStyle.NONE;
    /** What the effect on the view was made from; meaningful only while {@link #mApplied}. */
    @NonNull private final RetroUniforms mOnScreen = new RetroUniforms();
    @NonNull private PaneRetroStyle mOnScreenStyle = PaneRetroStyle.NONE;
    private boolean mApplied;
    /** Scratch, so a frame where nothing moved allocates nothing. */
    @NonNull private final RetroUniforms mScratch = new RetroUniforms();
    @NonNull private final int[] mLocation = new int[2];
    @NonNull private final float[] mCard = new float[4];
    @NonNull private final float[] mScreen = new float[5];
    /** Whether this surface plays screen events: a pane does, the chrome never. */
    private boolean mScreenEvents;
    @NonNull private final PaneScreenEvent.Slot mEvent = new PaneScreenEvent.Slot();
    /** 0..1: how far the picture is dimmed ({@link #setDim}); a pane out of focus is 1. */
    private float mDim;
    /** Whether a pane blends pixels sideways ({@link #setComposite}); never on the chrome. */
    private boolean mComposite;
    /** The Idle animations switch as last told ({@link #setAmbient}). */
    private boolean mAmbientSwitch;
    /** Whether the ambient loop is scheduled; false means nothing of it is posted. */
    private boolean mAmbientRunning;
    /** The flicker clock's origin and the next ambient tear, in uptime ms. */
    private long mAmbientOrigin;
    private long mNextAmbientTear;
    /** The flicker depth written into the uniforms: 0 unless the loop is playing. */
    private float mFlicker;
    @NonNull private final Random mAmbientRandom = new Random();
    @NonNull private final Runnable mAmbientTick = this::ambientTick;

    /**
     * @param bend     the CRT's barrel bend: {@link PaneRetroEffect#CRT_BEND} on a pane card, 0 on
     *                 chrome, whose keys and icons must stay under the finger
     * @param vignette how far the corners darken, 0 for none
     * @param aberr     the red/blue split in px ({@link RetroUniforms#aberr})
     * @param aberrFlat 1 for the same split everywhere, 0 for one growing from the centre
     * @param recess    1 for the glass's recess and sheen (a pane), 0 for none
     */
    public RetroEffectBinder(@NonNull View view, @NonNull Shape shape, float bend, float vignette,
                             float aberr, float aberrFlat, float recess) {
        mView = view;
        mShape = shape;
        mBend = bend;
        mVignette = vignette;
        mAberr = aberr;
        mAberrFlat = aberrFlat;
        mRecess = recess;
        mHook = new PreDrawHook(view, this::refresh);
    }

    /**
     * A pane card: bent, vignetted, its split growing to {@link RetroUniforms#PANE_ABERR_PX} at
     * the screen's edge, its glass recessed in the bezel.
     */
    @NonNull
    public static RetroEffectBinder pane(@NonNull View view, @NonNull ScreenShape shape) {
        RetroEffectBinder binder = new RetroEffectBinder(view, shape, PaneRetroEffect.CRT_BEND,
            PaneRetroEffect.PANE_VIGNETTE, RetroUniforms.PANE_ABERR_PX, 0f, 1f);
        binder.mScreenEvents = true;
        return binder;
    }

    /**
     * Plays one screen event ({@link PaneScreenEvent}) on this pane, replacing any live one, and
     * draws frames only until it ends. Nothing on the chrome, on a style that is not CRT, while
     * Android's animations are off, within {@link PaneScreenEvent#INPUT_QUIET_MS} of a key or a
     * scroll, or with the Screen events switch ({@code switchOn}) off.
     *
     * @return whether the event started
     */
    public boolean fire(@NonNull PaneScreenEvent.Kind kind, boolean switchOn) {
        if (!mScreenEvents || kind == PaneScreenEvent.Kind.NONE) return false;
        if (!PaneScreenEvent.mayFire(mStyle.usesCrt(), ValueAnimator.areAnimatorsEnabled(),
            InputQuiet.isQuiet(), switchOn))
            return false;
        mEvent.fire(kind, SystemClock.uptimeMillis(), (float) Math.random());
        mView.postInvalidateOnAnimation();
        return true;
    }

    /** A flat card cut to the view's bounds: what every chrome surface is drawn through. */
    @NonNull
    public static RetroEffectBinder flat(@NonNull View view) {
        return new RetroEffectBinder(view, BOUNDS, 0f, 0f, RetroUniforms.CHROME_ABERR_PX, 1f, 0f);
    }

    @NonNull
    public View view() {
        return mView;
    }

    @NonNull
    public PaneRetroStyle style() {
        return mStyle;
    }

    /** The look to draw the view through, from the next frame on; NONE takes it off now. */
    public void setStyle(@Nullable PaneRetroStyle style) {
        PaneRetroStyle next = style == null || !PaneRetroEffect.available()
            ? PaneRetroStyle.NONE : style;
        mStyle = next;
        if (next != PaneRetroStyle.NONE) RetroEffectPrewarm.request(next);
        mHook.setOn(next != PaneRetroStyle.NONE);
        refresh();
        refreshAmbient();
    }

    /**
     * How far the picture is dimmed, 0..1 (an unfocused pane is 1, {@link PaneDimPolicy}): one
     * uniform, wrapped again only when it changes. CRT only; the TFT program has no dim.
     */
    public void setDim(float dim) {
        float next = Math.max(0f, Math.min(1f, dim));
        if (next == mDim) return;
        mDim = next;
        refresh();
    }

    /**
     * The Composite signal switch: on blends neighbouring pixels sideways on a CRT pane. One
     * uniform, wrapped again only when it changes; the chrome and TFT ignore it.
     */
    public void setComposite(boolean on) {
        if (on == mComposite) return;
        mComposite = on;
        refresh();
    }

    /**
     * The Idle animations switch: on lets a CRT pane play its ambient flicker and tear
     * ({@link PaneAmbient}) whenever the gate holds; off stops them and posts nothing at all.
     */
    public void setAmbient(boolean switchOn) {
        mAmbientSwitch = switchOn;
        refreshAmbient();
    }

    /**
     * Re-checks the ambient gate now: starts the loop if it may play and is not running, stops it
     * if it may not. For an owner whose visibility, window or screen state just changed.
     */
    public void refreshAmbient() {
        if (!mScreenEvents) return;
        if (ambientStanding()) {
            if (!mAmbientRunning) startAmbient();
        } else {
            stopAmbient();
        }
    }

    /** Every gate of {@link PaneAmbient#ambientAllowed} but the typing hold, which only pauses. */
    private boolean ambientStanding() {
        if (!mAmbientSwitch || !mStyle.usesCrt() || !PaneRetroEffect.available()
            || !mView.isAttachedToWindow())
            return false;
        Context context = mView.getContext();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        PowerManager power = context.getSystemService(PowerManager.class);
        return PaneAmbient.ambientAllowed(true, true, ValueAnimator.areAnimatorsEnabled(),
            prefs != null && prefs.isLazyModeEnabled(),
            power != null && power.isPowerSaveMode(),
            power == null || power.isInteractive(),
            mView.isShown(), mView.getWindowVisibility() == View.VISIBLE, true);
    }

    private void startAmbient() {
        long now = SystemClock.uptimeMillis();
        mAmbientRunning = true;
        mAmbientOrigin = now;
        mNextAmbientTear = now + PaneAmbient.nextTearDelayMs(mAmbientRandom);
        mView.removeCallbacks(mAmbientTick);
        mView.postDelayed(mAmbientTick, PaneAmbient.TICK_MS);
    }

    private void stopAmbient() {
        if (!mAmbientRunning) return;
        mAmbientRunning = false;
        mView.removeCallbacks(mAmbientTick);
        if (mFlicker != 0f) {
            mFlicker = 0f;
            refresh();
        }
    }

    /**
     * One tick of the ambient loop: stops for good once a gate fails, holds (flicker off, one
     * re-check after {@link PaneScreenEvent#INPUT_QUIET_MS}) after a key or a scroll, else
     * advances the flicker and fires the tear when it is due.
     */
    private void ambientTick() {
        if (!mAmbientRunning) return;
        if (!ambientStanding()) {
            stopAmbient();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!InputQuiet.isQuiet()) {
            if (mFlicker != 0f) {
                mFlicker = 0f;
                refresh();
            }
            mView.postDelayed(mAmbientTick, PaneScreenEvent.INPUT_QUIET_MS);
            return;
        }
        mFlicker = PaneAmbient.flickerAmplitude(true);
        // A tear never cuts into an event already playing; it waits for the next tick.
        if (now >= mNextAmbientTear && mEvent.live(now) == null) {
            mEvent.fire(PaneScreenEvent.Kind.AMBIENT_TEAR, now, mAmbientRandom.nextFloat());
            mNextAmbientTear = now + PaneAmbient.nextTearDelayMs(mAmbientRandom);
        }
        refresh();
        mView.postDelayed(mAmbientTick, PaneAmbient.TICK_MS);
    }

    /** Off for good: the effect comes off and the view keeps no reference to this binder. */
    public void release() {
        setStyle(PaneRetroStyle.NONE);
        stopAmbient();
        mHook.release();
    }

    /**
     * Re-reads the view now rather than on its next frame: for an owner that has just changed
     * what {@link Shape} answers. Cheap when nothing moved.
     */
    public void refresh() {
        android.os.Trace.beginSection("Effect.retro.refresh");
        try {
            refreshInternal();
        } finally {
            android.os.Trace.endSection();
        }
    }

    private void refreshInternal() {
        if (mStyle == PaneRetroStyle.NONE || !PaneRetroEffect.available()) {
            clear();
            return;
        }
        int width = mView.getWidth();
        int height = mView.getHeight();
        if (width <= 0 || height <= 0) {
            clear();
            return;
        }
        // Where the view is, is only known in a window; the first frame after attaching asks again.
        if (!mView.isAttachedToWindow()) return;
        mView.getLocationInWindow(mLocation);
        float scaleX = 1f;
        float scaleY = 1f;
        for (View v = mView; v != null; ) {
            scaleX *= v.getScaleX();
            scaleY *= v.getScaleY();
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        float radius = mShape.card(mView, mCard);
        mScratch.set(width, height, mLocation[0], mLocation[1], scaleX, scaleY,
            mView.getResources().getDisplayMetrics().density,
            mCard[0], mCard[1], mCard[2], mCard[3], radius, mBend, mVignette)
            .surface(mAberr, mAberrFlat, mRecess, mStyle.usesCrt() ? mDim : 0f)
            .composite(mScreenEvents && mComposite && mStyle.usesCrt() ? 1f : 0f);
        long now = SystemClock.uptimeMillis();
        if (mScreenEvents && mFlicker > 0f && mStyle.usesCrt())
            mScratch.ambient(mFlicker, PaneAmbient.clockSeconds(now - mAmbientOrigin));
        PaneScreenEvent live = mScreenEvents ? mEvent.live(now) : null;
        if (live != null) {
            mScratch.event(live.kind.id, live.progress(now), live.seed);
            // The next frame of the event; once it is done the slot is empty, the uniforms go
            // back to 0 and the plain effect is wrapped once more, and the frames stop.
            mView.postInvalidateOnAnimation();
        }
        if (mShape instanceof ScreenShape) {
            float screenRadius = ((ScreenShape) mShape).screen(mView, mScreen);
            mScratch.screen(mScreen[0], mScreen[1], mScreen[2], mScreen[3], screenRadius,
                mScreen[4]);
        }
        if (mApplied && mOnScreenStyle == mStyle && mOnScreen.equals(mScratch)) return;
        mOnScreen.copyFrom(mScratch);
        mOnScreenStyle = mStyle;
        mApplied = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Api33.apply(mView, mEffect, mStyle, mOnScreen);
    }

    private void clear() {
        mEvent.clear();
        if (!mApplied) return;
        mApplied = false;
        mOnScreenStyle = PaneRetroStyle.NONE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mView.setRenderEffect(null);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33 {
        static void apply(@NonNull View view, @NonNull PaneRetroEffect effect,
                          @NonNull PaneRetroStyle style, @NonNull RetroUniforms uniforms) {
            // Null when the shader failed to compile: the view goes back to plain, and stays so
            // without retrying every frame, since the uniforms are recorded as on screen.
            view.setRenderEffect(effect.effectFor(style, uniforms));
        }
    }
}
