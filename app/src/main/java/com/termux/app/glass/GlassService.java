package com.termux.app.glass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;

import java.util.HashMap;

/**
 * The glass service (ADR 0029 section 1): every glass surface registers one entry
 * ({@link GlassEntry}) and is told its kind; the service owns the grant, when it changes and the
 * commit. A surface decides nothing about timing.
 *
 * <p>Kinds change only on a policy event ({@link #setLiveOn}, {@link #setMemoryShort},
 * {@link #setCompositorOn}, a Live slot freeing), applied at once: the change books a frame, and
 * that frame's glass commit, the frame table's last step, re-dresses each surface whose kind moved,
 * back to front. The same commit then records the Live layers ({@link LiveCore}); while a Live
 * picture is held it is armed to run on every drawn frame, so at rest, with nothing drawing, it
 * costs nothing.</p>
 */
public final class GlassService {

    /** A surface putting up the kind it was granted: its picture, or its stored frame. */
    public interface Redress {
        void onGranted(@NonNull GlassKind kind);
    }

    /** The Live core: records the Live surfaces' layers for the frame being drawn. */
    public interface LiveCore {
        /** Records what moved; whether any Live picture is still held. */
        boolean commitLive();
    }

    /** Re-dresses in one commit at most this many times the entries there are, then stops. */
    private static final int REDRESS_ROUNDS = 4;

    @NonNull private final GlassRegistry mRegistry = new GlassRegistry();
    @NonNull private final HashMap<String, Redress> mRedress = new HashMap<>();
    @Nullable private final FramePipeline mFrames;
    @Nullable private LiveCore mCore;
    private int mPolicyEvents;
    @Nullable private BroadcastReceiver mPowerSave;
    private boolean mLiveHeld;

    /** Makes {@code service} the glass service of every surface in {@code decor}'s window. */
    public static void install(@NonNull View decor, @Nullable GlassService service) {
        decor.setTag(R.id.glass_service, service);
    }

    /** The glass service of the window {@code view} is in; null before one is installed or off a window. */
    @Nullable
    public static GlassService of(@NonNull View view) {
        Object tag = view.getRootView().getTag(R.id.glass_service);
        return tag instanceof GlassService ? (GlassService) tag : null;
    }

    /** {@code frames} runs the commit as its last step; null for a service without a window. */
    public GlassService(@Nullable FramePipeline frames) {
        mFrames = frames;
        if (frames != null) frames.bind(FrameStep.GLASS_COMMIT, this::commit);
    }

    public void setLiveCore(@Nullable LiveCore core) {
        mCore = core;
    }

    /** The Live core took its first picture or let go of its last: the commit runs on every drawn frame while one is held. */
    public void liveHeld(boolean held) {
        mLiveHeld = held;
        arm();
    }

    /**
     * The commit runs on every drawn frame while a Live picture is held or a surface asks to be
     * visited each frame ({@link GlassEntry#frame}); otherwise only on a frame a policy event booked.
     */
    private void arm() {
        if (mFrames != null) {
            mFrames.arm(FrameStep.GLASS_COMMIT, mLiveHeld || mRegistry.frameCount() > 0);
        }
    }

    /**
     * Registers {@code entry} and grants its kind; {@code redress} is told when a policy event
     * moves it. The kind now, or null when another view already holds the element (no twins).
     * Registering the same element and view again only takes the new {@code redress}.
     */
    @Nullable
    public GlassKind register(@NonNull GlassEntry entry, @Nullable Redress redress) {
        GlassEntry held = mRegistry.entry(entry.element);
        if (held != null) {
            if (held.view != entry.view) return null;
        } else {
            mRegistry.register(entry);
            arm();
            afterGrants();
            // A surface this one displaced from a Live slot lets its picture go now, before the
            // newcomer takes one in the recorder: the registry is the only budget.
            redressMoved(entry.element);
        }
        if (redress != null) mRedress.put(entry.element, redress);
        return mRegistry.granted(entry.element);
    }

    /** The kind {@code element} holds when {@code view} registered it; null otherwise. */
    @Nullable
    public GlassKind grantedTo(@NonNull String element, @NonNull Object view) {
        GlassEntry held = mRegistry.entry(element);
        return held == null || held.view != view ? null : mRegistry.granted(element);
    }

    /** Takes {@code element}'s entry out; a Live slot it held goes to the best surface waiting. */
    public void unregister(@NonNull String element) {
        if (!mRegistry.unregister(element)) return;
        mRedress.remove(element);
        arm();
        afterGrants();
    }

    /** The Live policy's answer now (the switch, battery saver and what Live needs). */
    public void setLiveOn(boolean on) {
        mRegistry.setLiveOn(on);
        afterGrants();
    }

    /** A memory trim let the blur frames go, or the window came back after one. */
    public void setMemoryShort(boolean memoryShort) {
        mRegistry.setMemoryShort(memoryShort);
        afterGrants();
    }

    /** Whether the system can blur behind the window now. */
    public void setCompositorOn(boolean on) {
        mRegistry.setCompositorOn(on);
        afterGrants();
    }

    /** The entry of {@code element}; null when it has none. */
    @Nullable
    public GlassEntry entry(@NonNull String element) {
        return mRegistry.entry(element);
    }

    /** The kind granted to {@code element}; null when it has no entry. */
    @Nullable
    public GlassKind granted(@NonNull String element) {
        return mRegistry.granted(element);
    }

    /** How many policy events moved a kind: the guards' count. */
    public int policyEvents() {
        return mPolicyEvents;
    }

    /** How many surfaces are registered. */
    public int size() {
        return mRegistry.size();
    }

    /**
     * Calls {@code onChange} when battery saver turns on or off, a policy input of Live; null
     * stops listening.
     */
    public void watchPowerSave(@NonNull Context context, @Nullable Runnable onChange) {
        if (mPowerSave != null) {
            try {
                context.unregisterReceiver(mPowerSave);
            } catch (IllegalArgumentException ignored) {
                // Never registered with this context, or already gone.
            }
            mPowerSave = null;
        }
        if (onChange == null) return;
        mPowerSave = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                onChange.run();
            }
        };
        context.registerReceiver(mPowerSave,
            new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
    }

    private void afterGrants() {
        if (!mRegistry.anyChanged()) return;
        mPolicyEvents++;
        if (mFrames != null) mFrames.requestFrame(FrameStep.GLASS_COMMIT);
    }

    /**
     * The frame table's last step: every surface whose kind moved puts up its new kind, back to
     * front; then the Live core records this frame's layers. A surface that registers or lets go
     * while it is re-dressed is taken in the same commit.
     */
    void commit() {
        redressMoved(null);
        // Surfaces that need a word every drawn frame, back to front: after the re-dress, so a
        // surface put up this frame is the one visited.
        for (int i = 0; i < mRegistry.size(); i++) {
            GlassEntry.Frame frame = mRegistry.entryAt(i).frame;
            if (frame != null) frame.onFrame();
        }
        LiveCore core = mCore;
        if (core != null) liveHeld(core.commitLive());
    }

    /**
     * Tells every surface whose kind moved its new kind, back to front, except {@code skip} (the
     * surface that has just registered and was told its kind by the registration). A surface that
     * registers or lets go while it is re-dressed is taken in the same pass.
     */
    private void redressMoved(@Nullable String skip) {
        int rounds = REDRESS_ROUNDS * (mRegistry.size() + 1);
        while (rounds-- > 0 && mRegistry.anyChanged()) {
            for (int i = 0; i < mRegistry.size(); i++) {
                if (!mRegistry.takeChanged(i)) continue;
                String element = mRegistry.entryAt(i).element;
                Redress redress = element.equals(skip) ? null : mRedress.get(element);
                GlassKind kind = mRegistry.grantedAt(i);
                if (redress != null) redress.onGranted(kind);
                // The redress may have changed the registry: start the walk again.
                break;
            }
        }
    }
}
