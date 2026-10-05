package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Whether the GPU gives right answers on this phone, settled by running it (tai-device-tiers spec
 * §2.3). The automatic part: the first GPU load of a Gemma 4 file on a phone whose GPU path is
 * {@code UNKNOWN} runs a fixed canary before the caller's request (see {@code TaiManager}); a reply
 * that passes {@link #canaryPasses} stores {@link State#VERIFIED}, a wrong reply or a crash stores
 * {@link State#FAILED}. {@link TaiPlatformCaps} reads the verdict: Verified counts as {@code YES},
 * Failed as {@code CPU_FIRST}, so {@link TaiTierPolicy#defaultAccelerator} follows.
 *
 * <p>The verdict is keyed by the GPU driver string and the app version, so a driver or app update
 * runs the canary again. The picker's "Answers look wrong?" and "Try the GPU again" call
 * {@link #markFailed(Context)} and {@link #clear(Context)}.
 *
 * <p>Storage is one small file read by both processes (the app and {@code :tai_runtime}); the
 * encode, decode and matcher halves are pure so tests drive them with a fake {@link Store}.
 */
public final class TaiGpuVerdict {
    private TaiGpuVerdict() {}

    /** The fixed canary prompt: greedy decoding and 16 tokens are enough for the answer. */
    public static final String CANARY_PROMPT = "Reply with exactly: OK 42";
    public static final String CANARY_EXPECTED = "OK 42";
    public static final int CANARY_MAX_TOKENS = 16;
    /** What the user reads when the GPU failed the check. */
    public static final String REASON_FAILED = "The GPU gave wrong answers on this phone; using the CPU";

    /** The stored state. {@code CANARY_RUNNING} is only ever read as a crash (see {@link #decode}). */
    public enum State { UNKNOWN, VERIFIED, FAILED, CANARY_RUNNING }

    /** One string by name; the file-backed store is {@link #fileStore}. */
    public interface Store {
        @Nullable String read();

        void write(@Nullable String value);
    }

    // ----------------------------------------------------------------------------- pure parts

    /** True when the canary reply holds the expected text (case-insensitive, any surrounding words). */
    public static boolean canaryPasses(@Nullable String reply) {
        if (reply == null) return false;
        return reply.toUpperCase(Locale.ROOT).contains(CANARY_EXPECTED);
    }

    /** The key a verdict is stored under: GPU name and driver with the app version. */
    @NonNull
    public static String key(@Nullable String gpuName, @Nullable String gpuDriver, @Nullable String appBuild) {
        return (gpuName == null ? "" : gpuName.trim()) + "|" + (gpuDriver == null ? "" : gpuDriver.trim())
            + "|" + (appBuild == null ? "" : appBuild.trim());
    }

    @NonNull
    static String encode(@NonNull State state, @NonNull String key) {
        return state.name() + "\t" + key;
    }

    /**
     * The state stored for {@code key}: {@code UNKNOWN} when nothing is stored or it was stored for
     * another driver or app version. A {@code CANARY_RUNNING} marker that no canary of this process
     * owns means the process died during the canary, so it reads as {@code FAILED}.
     */
    @NonNull
    static State decode(@Nullable String stored, @NonNull String key, boolean canaryRunningHere) {
        if (stored == null) return State.UNKNOWN;
        int tab = stored.indexOf('\t');
        if (tab <= 0 || !stored.substring(tab + 1).equals(key)) return State.UNKNOWN;
        State state;
        try {
            state = State.valueOf(stored.substring(0, tab));
        } catch (IllegalArgumentException e) {
            return State.UNKNOWN;
        }
        if (state == State.CANARY_RUNNING) return canaryRunningHere ? State.UNKNOWN : State.FAILED;
        return state;
    }

    /** The GPU path a verdict gives: {@code NO} stays; Verified is {@code YES}; Failed is {@code CPU_FIRST}. */
    @NonNull
    public static TaiPlatformCaps.GpuPath apply(@NonNull TaiPlatformCaps.GpuPath path, @NonNull State state) {
        if (path == TaiPlatformCaps.GpuPath.NO) return path;
        switch (state) {
            case VERIFIED: return TaiPlatformCaps.GpuPath.YES;
            case FAILED: return TaiPlatformCaps.GpuPath.CPU_FIRST;
            default: return path;
        }
    }

    /** Whether the canary should run: an unconfirmed GPU, a Gemma 4 file, a GPU load, and not yet this process. */
    public static boolean shouldRunCanary(@NonNull TaiPlatformCaps.GpuPath path, @Nullable String modelId,
                                          @Nullable String loadedAccelerator, boolean alreadyRan) {
        if (alreadyRan || path != TaiPlatformCaps.GpuPath.UNKNOWN) return false;
        if (modelId == null || !modelId.startsWith("gemma-4")) return false;
        return TaiTierPolicy.ACCEL_GPU.equalsIgnoreCase(loadedAccelerator);
    }

    // ---------------------------------------------------------------------------- store access

    private static final Object LOCK = new Object();
    private static volatile boolean sCanaryRunningHere;

    /** The verdict file under the app's files directory. */
    @NonNull
    static Store fileStore(@NonNull Context context) {
        final File file = new File(context.getApplicationContext().getFilesDir(), "tai/gpu-verdict.txt");
        return new Store() {
            @Nullable
            @Override
            public String read() {
                try {
                    if (!file.isFile()) return null;
                    return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }

            @Override
            public void write(@Nullable String value) {
                try {
                    if (value == null) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return;
                    }
                    File dir = file.getParentFile();
                    if (dir != null && !dir.isDirectory()) //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(value.getBytes(StandardCharsets.UTF_8));
                        out.getFD().sync(); // before a driver can take the process down
                    }
                } catch (IOException | RuntimeException ignored) {
                    // no verdict is stored: the next GPU load checks again
                }
            }
        };
    }

    /** The state stored for this phone's GPU and this build, read through {@code store}. */
    @NonNull
    static State current(@NonNull Store store, @NonNull String key) {
        return decode(store.read(), key, sCanaryRunningHere);
    }

    /** {@link #current(Store, String)} on the live file, for the GPU the caps describe. */
    @NonNull
    public static State current(@NonNull Context context, @NonNull TaiPlatformCaps raw) {
        return current(fileStore(context), keyFor(raw));
    }

    @NonNull
    static String keyFor(@NonNull TaiPlatformCaps caps) {
        return key(caps.gpuName, caps.gpuDriver, BuildConfig.VERSION_NAME);
    }

    /** Records the GPU as failed ("Answers look wrong?"). */
    public static void markFailed(@NonNull Context context) {
        write(context, State.FAILED);
    }

    /** Records the GPU as verified (the bench settles it from its own outputs). */
    public static void markVerified(@NonNull Context context) {
        write(context, State.VERIFIED);
    }

    /** Forgets the verdict ("Try the GPU again"): the canary runs on the next GPU load. */
    public static void clear(@NonNull Context context) {
        synchronized (LOCK) {
            fileStore(context).write(null);
        }
        TaiPlatformCaps.invalidateVerdict();
    }

    /** Marks the canary as started, so a death during it reads as Failed. */
    static void beginCanary(@NonNull Context context) {
        sCanaryRunningHere = true;
        write(context, State.CANARY_RUNNING);
    }

    /** Ends the canary with its verdict. */
    static void finishCanary(@NonNull Context context, boolean passed) {
        write(context, passed ? State.VERIFIED : State.FAILED);
        sCanaryRunningHere = false;
    }

    private static void write(@NonNull Context context, @NonNull State state) {
        TaiPlatformCaps raw = TaiPlatformCaps.rawCached(context);
        synchronized (LOCK) {
            fileStore(context).write(encode(state, keyFor(raw)));
        }
        TaiPlatformCaps.invalidateVerdict();
    }
}
