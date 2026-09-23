package com.termux.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class TaiDeviceCapabilities {
    private static final long BYTES_PER_GIB = 1024L * 1024L * 1024L;
    public static final int MNN_SDK_MINIMUM = 24;
    public static final int MNN_MEMORY_ESTIMATE_MB = 2048;

    public final String model;
    public final String manufacturer;
    public final String socModel;
    public final int sdkInt;
    public final List<String> supportedAbis;
    /**
     * The RAM the phone is sold with, from what the kernel reports (see
     * {@link TaiLoadBudget#ramClassBytes}); what model recommendations and the context tier compare
     * against. Not advertisedMem, which counts swap sold as RAM.
     */
    public final long memoryBytes;
    /** RAM as the kernel reports it; the budget's reserve is a share of this. */
    public final long physicalMemoryBytes;
    /** Android's advertisedMem, reported for diagnosis only; {@code 0} when unavailable. */
    public final long advertisedMemoryBytes;
    public final long availableMemoryBytes;
    public final boolean lowMemory;
    public final String memorySource;
    public final boolean pixel10;
    public final boolean liteRtLmAbiSupported;
    public final boolean liteRtLmNativeLibrariesAvailable;

    public final boolean mnnSupported;
    @Nullable public final String mnnUnsupportedReason;
    public final int mnnSdkMinimum;
    public final int mnnMemoryEstimateMb;
    @Nullable public final String mnnAcceleratorInfo;
    public final boolean mnnBundledLibrariesAvailable;

    private static volatile String sDebugMnnUnsupportedReason = null;

    private TaiDeviceCapabilities(
        @NonNull String model,
        @NonNull String manufacturer,
        @NonNull String socModel,
        int sdkInt,
        @NonNull List<String> supportedAbis,
        long memoryBytes,
        long physicalMemoryBytes,
        long advertisedMemoryBytes,
        long availableMemoryBytes,
        boolean lowMemory,
        @NonNull String memorySource,
        boolean pixel10,
        boolean liteRtLmAbiSupported,
        boolean liteRtLmNativeLibrariesAvailable,
        boolean mnnSupported,
        @Nullable String mnnUnsupportedReason,
        int mnnSdkMinimum,
        int mnnMemoryEstimateMb,
        @Nullable String mnnAcceleratorInfo,
        boolean mnnBundledLibrariesAvailable
    ) {
        this.model = model;
        this.manufacturer = manufacturer;
        this.socModel = socModel;
        this.sdkInt = sdkInt;
        this.supportedAbis = Collections.unmodifiableList(new ArrayList<>(supportedAbis));
        this.memoryBytes = memoryBytes;
        this.physicalMemoryBytes = physicalMemoryBytes;
        this.advertisedMemoryBytes = advertisedMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.lowMemory = lowMemory;
        this.memorySource = memorySource;
        this.pixel10 = pixel10;
        this.liteRtLmAbiSupported = liteRtLmAbiSupported;
        this.liteRtLmNativeLibrariesAvailable = liteRtLmNativeLibrariesAvailable;
        this.mnnSupported = mnnSupported;
        this.mnnUnsupportedReason = mnnUnsupportedReason;
        this.mnnSdkMinimum = mnnSdkMinimum;
        this.mnnMemoryEstimateMb = mnnMemoryEstimateMb;
        this.mnnAcceleratorInfo = mnnAcceleratorInfo;
        this.mnnBundledLibrariesAvailable = mnnBundledLibrariesAvailable;
    }

    @NonNull
    public static TaiDeviceCapabilities detect(@NonNull Context context) {
        long physicalMemoryBytes = 0L;
        long advertisedMemoryBytes = 0L;
        long availableMemoryBytes = 0L;
        boolean lowMemory = false;
        String memorySource = "unavailable";
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            physicalMemoryBytes = memoryInfo.totalMem;
            availableMemoryBytes = memoryInfo.availMem;
            lowMemory = memoryInfo.lowMemory;
            memorySource = "totalMem rounded to its RAM class";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                advertisedMemoryBytes = Math.max(0L, memoryInfo.advertisedMem);
            }
        }
        long memoryBytes = TaiLoadBudget.ramClassBytes(physicalMemoryBytes);
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null ? Build.SOC_MODEL : "";
        List<String> abis = Arrays.asList(Build.SUPPORTED_ABIS);
        boolean liteRtSupported = containsSupportedAbi(abis);
        boolean liteRtNativeLibrariesAvailable = hasAnyBundledNativeLibrary(context, abis,
            "liblitertlm_jni.so", "liblitertlm.so", "liblitert_jni.so");
        boolean mnnBundledLibrariesAvailable = hasBundledNativeLibrary(context, "libMNN.so", Collections.singletonList("arm64-v8a"))
            && hasBundledNativeLibrary(context, "libmnnllmapp.so", Collections.singletonList("arm64-v8a"));
        MnnCapabilityResult mnnResult = computeMnnCapabilities(abis, Build.VERSION.SDK_INT, mnnBundledLibrariesAvailable);
        String acceleratorInfo = Build.HARDWARE == null ? "" : Build.HARDWARE;
        return new TaiDeviceCapabilities(
            model,
            Build.MANUFACTURER == null ? "" : Build.MANUFACTURER,
            soc,
            Build.VERSION.SDK_INT,
            abis,
            memoryBytes,
            physicalMemoryBytes,
            advertisedMemoryBytes,
            availableMemoryBytes,
            lowMemory,
            memorySource,
            model.toLowerCase(Locale.ROOT).contains("pixel 10"),
            liteRtSupported,
            liteRtNativeLibrariesAvailable,
            mnnResult.mnnSupported,
            mnnResult.mnnUnsupportedReason,
            MNN_SDK_MINIMUM,
            MNN_MEMORY_ESTIMATE_MB,
            acceleratorInfo,
            mnnResult.mnnBundledLibrariesAvailable
        );
    }

    /**
     * Package-private factory for unit tests that need to inject fake ABIs
     * and control MNN capability computation without relying on {@link Build}.
     */
    static TaiDeviceCapabilities createForTest(
        @NonNull String model,
        @NonNull String manufacturer,
        @NonNull String socModel,
        int sdkInt,
        @NonNull List<String> supportedAbis,
        long memoryBytes,
        @NonNull String memorySource,
        boolean pixel10
    ) {
        boolean liteRtSupported = containsSupportedAbi(supportedAbis);
        MnnCapabilityResult mnnResult = computeMnnCapabilities(supportedAbis, sdkInt, false);
        return new TaiDeviceCapabilities(
            model,
            manufacturer,
            socModel,
            sdkInt,
            supportedAbis,
            memoryBytes,
            memoryBytes,
            0L,
            memoryBytes > 0L ? memoryBytes / 2L : 0L,
            false,
            memorySource,
            pixel10,
            liteRtSupported,
            liteRtSupported,
            mnnResult.mnnSupported,
            mnnResult.mnnUnsupportedReason,
            MNN_SDK_MINIMUM,
            MNN_MEMORY_ESTIMATE_MB,
            "",
            mnnResult.mnnBundledLibrariesAvailable
        );
    }

    public boolean supportsAccelerator(@NonNull String accelerator) {
        if (!liteRtLmAbiSupported || !liteRtLmNativeLibrariesAvailable) return false;
        if ("cpu".equalsIgnoreCase(accelerator)) return true;
        if ("gpu".equalsIgnoreCase(accelerator)) return !pixel10;
        return false;
    }

    @NonNull
    public List<String> compatibleAccelerators(@NonNull TaiModelProfile profile) {
        ArrayList<String> result = new ArrayList<>();
        for (String accelerator : profile.compatibleAccelerators) {
            if (supportsAccelerator(accelerator)) result.add(accelerator);
        }
        return result;
    }

    @Nullable
    public String memoryWarning(@NonNull TaiModelProfile profile) {
        if (profile.minDeviceMemoryInGb == null || memoryBytes <= 0L) return null;
        long requiredBytes = profile.minDeviceMemoryInGb * BYTES_PER_GIB;
        if (memoryBytes >= requiredBytes) return null;
        return "Device memory is below the model's Edge Gallery minimum of "
            + profile.minDeviceMemoryInGb + " GiB.";
    }

    public static final class ModelCapabilityCheck {
        @Nullable public final String warning;
        @Nullable public final String blockingReason;

        public ModelCapabilityCheck(@Nullable String warning, @Nullable String blockingReason) {
            this.warning = warning;
            this.blockingReason = blockingReason;
        }
    }

    @NonNull
    public ModelCapabilityCheck checkModelCapability(@NonNull TaiModelSpec modelSpec) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(modelSpec.backend)) {
            if (!mnnSupported) {
                return new ModelCapabilityCheck(null,
                    mnnUnsupportedReason != null ? mnnUnsupportedReason : "MNN backend is not supported on this device.");
            }
        }
        if (modelSpec.recommendedRamGb > 0 && memoryBytes > 0L) {
            long requiredBytes = modelSpec.recommendedRamGb * BYTES_PER_GIB;
            if (memoryBytes < requiredBytes) {
                return new ModelCapabilityCheck(
                    "Device memory is below the model's recommended " + modelSpec.recommendedRamGb + " GiB.",
                    null);
            }
        }
        return new ModelCapabilityCheck(null, null);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", model);
        json.put("manufacturer", manufacturer);
        json.put("socModel", socModel);
        json.put("sdkInt", sdkInt);
        JSONArray abis = new JSONArray();
        for (String abi : supportedAbis) abis.put(abi);
        json.put("supportedAbis", abis);
        json.put("memoryBytes", memoryBytes);
        json.put("memoryGiB", memoryBytes > 0L ? memoryBytes / (double) BYTES_PER_GIB : JSONObject.NULL);
        json.put("physicalMemoryBytes", physicalMemoryBytes);
        json.put("advertisedMemoryBytes", advertisedMemoryBytes);
        json.put("availableMemoryBytes", availableMemoryBytes);
        json.put("availableMemoryGiB", availableMemoryBytes > 0L ? availableMemoryBytes / (double) BYTES_PER_GIB : JSONObject.NULL);
        json.put("lowMemory", lowMemory);
        json.put("memorySource", memorySource);
        json.put("pixel10", pixel10);
        json.put("liteRtLmAbiSupported", liteRtLmAbiSupported);
        json.put("liteRtLmNativeLibrariesAvailable", liteRtLmNativeLibrariesAvailable);
        json.put("mnnSupported", mnnSupported);
        json.put("mnnUnsupportedReason", mnnUnsupportedReason == null ? JSONObject.NULL : mnnUnsupportedReason);
        json.put("mnnSdkMinimum", mnnSdkMinimum);
        json.put("mnnMemoryEstimateMb", mnnMemoryEstimateMb);
        json.put("mnnAcceleratorInfo", mnnAcceleratorInfo == null ? JSONObject.NULL : mnnAcceleratorInfo);
        json.put("mnnBundledLibrariesAvailable", mnnBundledLibrariesAvailable);
        JSONObject backends = new JSONObject();
        backends.put(TaiModelSpec.BACKEND_LITERT_LM, liteRtLmAbiSupported && liteRtLmNativeLibrariesAvailable);
        backends.put(TaiModelSpec.BACKEND_MNN_LLM, mnnSupported);
        json.put("backends", backends);
        JSONArray accelerators = new JSONArray();
        if (supportsAccelerator("cpu")) accelerators.put("cpu");
        if (supportsAccelerator("gpu")) accelerators.put("gpu");
        json.put("phase1Accelerators", accelerators);
        json.put("gpuPolicy", pixel10
            ? "disabled to match Google AI Edge Gallery's Pixel 10 compatibility rule"
            : "tried first on automatic loads, after a recorded GPU failure tried after the CPU");
        return json;
    }

    /**
     * Debug-build-only override. When called with a non-null reason in a debug build,
     * subsequent {@link #detect} calls will force {@code mnnSupported=false} and surface
     * the provided reason. Completely ignored in release builds.
     *
     * <p>QA can enable this via ADB without code changes:
     * <pre>
     *   adb shell am broadcast -a com.termux.ai.FORCE_MNN_UNSUPPORTED \
     *       --es reason "Simulated unsupported ABI"
     * </pre>
     * Or by calling this method from a test harness.
     */
    public static void setDebugMnnUnsupportedReason(@Nullable String reason) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        sDebugMnnUnsupportedReason = reason;
    }

    /** Clears the debug override. Ignored in release builds. */
    public static void clearDebugMnnUnsupportedReason() {
        if (!BuildConfig.DEBUG) {
            return;
        }
        sDebugMnnUnsupportedReason = null;
    }

    /** Package-private for unit tests to verify release-build behavior. */
    static boolean shouldApplyDebugOverride(boolean isDebugBuild, @Nullable String overrideReason) {
        return isDebugBuild && overrideReason != null;
    }

    private static boolean containsSupportedAbi(@NonNull List<String> abis) {
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) return true;
        }
        return false;
    }

    private static final class MnnCapabilityResult {
        final boolean mnnSupported;
        @Nullable final String mnnUnsupportedReason;
        final boolean mnnBundledLibrariesAvailable;

        MnnCapabilityResult(boolean mnnSupported, @Nullable String mnnUnsupportedReason, boolean mnnBundledLibrariesAvailable) {
            this.mnnSupported = mnnSupported;
            this.mnnUnsupportedReason = mnnUnsupportedReason;
            this.mnnBundledLibrariesAvailable = mnnBundledLibrariesAvailable;
        }
    }

    @NonNull
    private static MnnCapabilityResult computeMnnCapabilities(@NonNull List<String> abis, int sdkInt, boolean mnnBundledLibrariesAvailable) {
        boolean abiSupported = abis.contains("arm64-v8a");
        boolean mnnSupported = sdkInt >= MNN_SDK_MINIMUM && abiSupported && mnnBundledLibrariesAvailable;
        String mnnUnsupportedReason = mnnSupported ? null : "Native MNN runtime libraries are not bundled for this APK/ABI.";

        if (sdkInt < MNN_SDK_MINIMUM) {
            mnnUnsupportedReason = "MNN requires Android 7.0 (API " + MNN_SDK_MINIMUM + ") or higher. Device is API " + sdkInt + ".";
        } else if (!abiSupported) {
            mnnUnsupportedReason = "MNN target ABI is not supported; runtime is bundled for arm64-v8a only.";
        } else if (!mnnBundledLibrariesAvailable) {
            mnnUnsupportedReason = "Native MNN runtime libraries are not available in this APK.";
        }

        if (shouldApplyDebugOverride(BuildConfig.DEBUG, sDebugMnnUnsupportedReason)) {
            mnnSupported = false;
            mnnUnsupportedReason = sDebugMnnUnsupportedReason;
        }

        return new MnnCapabilityResult(mnnSupported, mnnUnsupportedReason, mnnBundledLibrariesAvailable);
    }

    private static boolean hasAnyBundledNativeLibrary(
        @NonNull Context context,
        @NonNull List<String> abis,
        @NonNull String... names
    ) {
        for (String name : names) {
            if (hasBundledNativeLibrary(context, name, abis)) return true;
        }
        return false;
    }

    static boolean hasBundledNativeLibrary(
        @NonNull Context context,
        @NonNull String libraryName,
        @NonNull List<String> abis
    ) {
        ApplicationInfo info = context.getApplicationInfo();
        if (info != null && info.nativeLibraryDir != null) {
            File file = new File(info.nativeLibraryDir, libraryName);
            if (file.isFile()) return true;
        }
        if (info == null) return false;
        if (apkContainsLibrary(info.sourceDir, libraryName, abis)) return true;
        if (info.splitSourceDirs != null) {
            for (String split : info.splitSourceDirs) {
                if (apkContainsLibrary(split, libraryName, abis)) return true;
            }
        }
        return false;
    }

    private static boolean apkContainsLibrary(@Nullable String apkPath, @NonNull String libraryName, @NonNull List<String> abis) {
        if (apkPath == null || apkPath.trim().isEmpty()) return false;
        File apk = new File(apkPath);
        if (!apk.isFile()) return false;
        try (ZipFile zip = new ZipFile(apk)) {
            for (String abi : abis) {
                ZipEntry entry = zip.getEntry("lib/" + abi + "/" + libraryName);
                if (entry != null) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}
