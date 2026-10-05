package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the platform allows, apart from RAM (tai-device-tiers spec §2): the ABI, the Android version
 * and the GPU path. {@link TaiTierPolicy} intersects its table with this; the platform can only
 * take things away.
 *
 * <p>The GPU path exists because LiteRT-LM's GPU backend on Android is OpenCL, and a failed GPU
 * load has no automatic CPU fallback. Detection needs no GL context: the Vulkan vendorID first,
 * then {@code ro.hardware.egl}, then {@code Build.SOC_MANUFACTURER} / {@code SOC_MODEL} through a
 * small table here. {@link #classify} is pure and takes the raw facts, so it is unit-tested; the
 * probe that gathers them runs once off the main thread, and the facts are cached in the TAI
 * preferences per app build.
 */
public final class TaiPlatformCaps {
    private static final String TAG = "TaiPlatformCaps";

    /** Whether the GPU is a good place to run a model on this phone. */
    public enum GpuPath {
        /** Known to work (Adreno): preselect the GPU. */
        YES,
        /** Not confirmed on this GPU yet: preselect the GPU with a note. */
        UNKNOWN,
        /** Often fails: preselect the CPU, the GPU stays selectable. */
        CPU_FIRST,
        /** Not offered: no OpenCL, or the Pixel 10 rule. */
        NO
    }

    public enum GpuFamily { ADRENO, MALI, XCLIPSE, POWERVR, OTHER, UNKNOWN }

    static final int VENDOR_QUALCOMM = 0x5143;
    static final int VENDOR_ARM = 0x13B5;
    static final int VENDOR_IMAGINATION = 0x1010;
    static final int VENDOR_SAMSUNG = 0x144D;

    /**
     * The raw facts one probe gathers; {@link #classify} turns them into a verdict. A field the
     * probe could not read is {@code null} (or {@code -1} for the vendorID).
     */
    public static final class Facts {
        /** Whether a usable OpenCL driver answered; {@code null} when the probe could not run. */
        @Nullable public final Boolean openCl;
        /** The first Vulkan device's vendorID, {@code -1} when there is no Vulkan device. */
        public final int vulkanVendorId;
        @NonNull public final String vulkanDeviceName;
        /** {@code ro.hardware.egl}, e.g. adreno, mali, emulation. */
        @NonNull public final String eglName;
        /** {@code Build.SOC_MANUFACTURER} (API 31+), empty before. */
        @NonNull public final String socManufacturer;
        /** {@code Build.SOC_MODEL} (API 31+), empty before. */
        @NonNull public final String socModel;
        /** {@code Build.MODEL}. */
        @NonNull public final String buildModel;
        /** {@code CL_DEVICE_NAME} of the first OpenCL GPU, e.g. "QUALCOMM Adreno(TM) 830"; empty when unknown. */
        @NonNull public final String openClDeviceName;
        /** {@code CL_DRIVER_VERSION}; Adreno's carries the compiler ("... Compiler E031.47.12.03"). */
        @NonNull public final String openClDriver;
        /** The {@code Features} line of {@code /proc/cpuinfo}, for the CPU labels. */
        @NonNull public final String cpuFeatures;

        public Facts(@Nullable Boolean openCl, int vulkanVendorId, @Nullable String vulkanDeviceName,
                     @Nullable String eglName, @Nullable String socManufacturer, @Nullable String socModel,
                     @Nullable String buildModel, @Nullable String openClDeviceName, @Nullable String openClDriver,
                     @Nullable String cpuFeatures) {
            this.openCl = openCl;
            this.vulkanVendorId = vulkanVendorId;
            this.vulkanDeviceName = vulkanDeviceName == null ? "" : vulkanDeviceName;
            this.eglName = eglName == null ? "" : eglName;
            this.socManufacturer = socManufacturer == null ? "" : socManufacturer;
            this.socModel = socModel == null ? "" : socModel;
            this.buildModel = buildModel == null ? "" : buildModel;
            this.openClDeviceName = openClDeviceName == null ? "" : openClDeviceName;
            this.openClDriver = openClDriver == null ? "" : openClDriver;
            this.cpuFeatures = cpuFeatures == null ? "" : cpuFeatures;
        }

        /** Nothing known: what {@link #cached()} reports before the probe has run. */
        @NonNull
        static Facts unprobed() {
            return new Facts(null, -1, null, null, null, null, Build.MODEL, null, null, null);
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            if (openCl != null) json.put("openCl", openCl.booleanValue());
            json.put("vendor", vulkanVendorId);
            json.put("vulkanName", vulkanDeviceName);
            json.put("egl", eglName);
            json.put("socMfr", socManufacturer);
            json.put("socModel", socModel);
            json.put("model", buildModel);
            json.put("clName", openClDeviceName);
            json.put("clDriver", openClDriver);
            json.put("features", cpuFeatures);
            return json;
        }

        @Nullable
        static Facts fromJson(@Nullable String text) {
            if (text == null) return null;
            try {
                JSONObject json = new JSONObject(text);
                return new Facts(json.has("openCl") ? Boolean.valueOf(json.getBoolean("openCl")) : null,
                    json.optInt("vendor", -1), json.optString("vulkanName", ""), json.optString("egl", ""),
                    json.optString("socMfr", ""), json.optString("socModel", ""), json.optString("model", ""),
                    json.optString("clName", ""), json.optString("clDriver", ""), json.optString("features", ""));
            } catch (JSONException e) {
                return null;
            }
        }
    }

    /** The result of {@link #classify}: the family, the path and why, in words for the picker. */
    public static final class GpuVerdict {
        @NonNull public final GpuFamily family;
        @NonNull public final GpuPath path;
        @NonNull public final String reason;

        GpuVerdict(@NonNull GpuFamily family, @NonNull GpuPath path, @NonNull String reason) {
            this.family = family;
            this.path = path;
            this.reason = reason;
        }
    }

    public final int sdkInt;
    /** LiteRT-LM needs arm64-v8a or x86_64. */
    public final boolean liteRtAbiOk;
    /** MNN ships arm64-v8a only and needs API 30 ({@link TaiDeviceCapabilities#MNN_SDK_MINIMUM}). */
    public final boolean mnnAbiOk;
    @NonNull public final GpuPath gpuPath;
    @NonNull public final GpuFamily gpuFamily;
    @NonNull public final String gpuReason;
    /** The Vulkan device's own name, e.g. "Adreno (TM) 740"; empty when unknown. */
    @NonNull public final String gpuName;
    /** The OpenCL driver string ({@code CL_DRIVER_VERSION}); empty when unknown. */
    @NonNull public final String gpuDriver;
    /** {@code dotprod}, {@code i8mm}, {@code sme2} as present. For labels and diagnostics only; never gates. */
    @NonNull public final Set<String> cpuFeatures;
    /** False until a probe (or its cached result) has been read. */
    public final boolean probed;

    private TaiPlatformCaps(int sdkInt, boolean liteRtAbiOk, boolean mnnAbiOk, @NonNull GpuVerdict verdict,
                            @NonNull String gpuName, @NonNull String gpuDriver, @NonNull Set<String> cpuFeatures,
                            boolean probed) {
        this.sdkInt = sdkInt;
        this.liteRtAbiOk = liteRtAbiOk;
        this.mnnAbiOk = mnnAbiOk;
        this.gpuPath = verdict.path;
        this.gpuFamily = verdict.family;
        this.gpuReason = verdict.reason;
        this.gpuName = gpuName;
        this.gpuDriver = gpuDriver;
        this.cpuFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(cpuFeatures));
        this.probed = probed;
    }

    /** True when some local model backend can run: with neither, no local model is offered. */
    public boolean anyLocalBackend() {
        return liteRtAbiOk || mnnAbiOk;
    }

    /** Assembles the caps from known inputs: the pure half of detection, which tests drive directly. */
    @NonNull
    public static TaiPlatformCaps of(int sdkInt, @NonNull List<String> abis, @NonNull Facts facts, boolean probed) {
        return new TaiPlatformCaps(sdkInt, liteRtAbiOk(abis), mnnAbiOk(abis, sdkInt), classify(facts),
            facts.vulkanDeviceName.isEmpty() ? facts.openClDeviceName : facts.vulkanDeviceName, facts.openClDriver,
            parseCpuFeatures(facts.cpuFeatures), probed);
    }

    public static boolean liteRtAbiOk(@NonNull List<String> abis) {
        return abis.contains("arm64-v8a") || abis.contains("x86_64");
    }

    public static boolean mnnAbiOk(@NonNull List<String> abis, int sdkInt) {
        return abis.contains("arm64-v8a") && sdkInt >= TaiDeviceCapabilities.MNN_SDK_MINIMUM;
    }

    // ---------------------------------------------------------------- classification (spec §2.2)

    /**
     * The GPU verdict for the raw facts. Order: the Pixel 10 rule, then a missing OpenCL driver (both
     * {@code NO}), then the family's own path. A family nothing could name is {@code UNKNOWN}.
     */
    @NonNull
    public static GpuVerdict classify(@NonNull Facts facts) {
        GpuFamily family = detectFamily(facts);
        // Gallery's own rule, kept as is: TaiDeviceCapabilities.supportsAccelerator has the same.
        if (facts.buildModel.toLowerCase(Locale.ROOT).contains("pixel 10")) {
            return new GpuVerdict(family, GpuPath.NO, "The GPU is not used on the Pixel 10 (Google AI Edge Gallery's rule).");
        }
        if (Boolean.FALSE.equals(facts.openCl)) {
            return new GpuVerdict(family, GpuPath.NO, "No OpenCL driver on this phone.");
        }
        switch (family) {
            case ADRENO:
                // LiteRT-LM's own binary warns about this compiler on the Adreno 8xx series.
                if (isWarnedAdrenoCompiler(facts)) {
                    return new GpuVerdict(family, GpuPath.UNKNOWN,
                        "This GPU driver is known to give wrong answers; update the phone's software");
                }
                return new GpuVerdict(family, GpuPath.YES, "Adreno GPU.");
            case MALI:
                if (isTensorG4OrNewer(facts)) {
                    return new GpuVerdict(family, GpuPath.UNKNOWN, "Not confirmed on this GPU yet (Mali on Tensor G4 or newer).");
                }
                return new GpuVerdict(family, GpuPath.CPU_FIRST, "Often fails on this GPU (Mali).");
            case XCLIPSE:
                return new GpuVerdict(family, GpuPath.CPU_FIRST, "Often fails on this GPU (Xclipse).");
            case POWERVR:
                return new GpuVerdict(family, GpuPath.CPU_FIRST, "Often fails on this GPU (PowerVR).");
            case OTHER:
                return new GpuVerdict(family, GpuPath.UNKNOWN, "Not confirmed on this GPU yet.");
            default:
                return new GpuVerdict(family, GpuPath.UNKNOWN, "GPU not identified.");
        }
    }

    /** Vulkan vendorID first, then {@code ro.hardware.egl}, then the SoC table. */
    @NonNull
    static GpuFamily detectFamily(@NonNull Facts facts) {
        switch (facts.vulkanVendorId) {
            case VENDOR_QUALCOMM: return GpuFamily.ADRENO;
            case VENDOR_ARM: return GpuFamily.MALI;
            case VENDOR_IMAGINATION: return GpuFamily.POWERVR;
            case VENDOR_SAMSUNG: return GpuFamily.XCLIPSE;
            default: break;
        }
        // A vendorID we do not know (a software renderer, say) is a GPU we cannot vouch for; it does
        // not fall through to the SoC, which would name hardware the app is not using.
        if (facts.vulkanVendorId > 0) return GpuFamily.OTHER;

        String egl = facts.eglName.toLowerCase(Locale.ROOT);
        if (egl.contains("adreno")) return GpuFamily.ADRENO;
        if (egl.contains("mali")) return GpuFamily.MALI;
        if (egl.contains("powervr") || egl.contains("pvr")) return GpuFamily.POWERVR;
        if (egl.contains("xclipse")) return GpuFamily.XCLIPSE;
        if (egl.contains("swiftshader") || egl.contains("emulation") || egl.contains("angle")) return GpuFamily.OTHER;

        return familyFromSoc(facts.socManufacturer, facts.socModel);
    }

    private static final Pattern TENSOR_GENERATION = Pattern.compile("tensor\\s*g(\\d+)");
    private static final Pattern EXYNOS_NUMBER = Pattern.compile("exynos\\s*(\\d+)");

    /** Our own small SoC table (soc §5): which GPU a chip carries, for phones with no Vulkan answer. */
    @NonNull
    static GpuFamily familyFromSoc(@NonNull String manufacturer, @NonNull String model) {
        String mfr = manufacturer.toLowerCase(Locale.ROOT);
        String soc = model.toLowerCase(Locale.ROOT);
        if (mfr.contains("qualcomm") || mfr.contains("qti")) return GpuFamily.ADRENO;
        if (mfr.contains("google")) {
            // Tensor G1 to G4 carry Mali; G5 carries a PowerVR.
            int generation = tensorGeneration(soc);
            if (generation >= 5) return GpuFamily.POWERVR;
            return generation > 0 ? GpuFamily.MALI : GpuFamily.UNKNOWN;
        }
        if (mfr.contains("samsung") || mfr.contains("s.lsi")) {
            Matcher m = EXYNOS_NUMBER.matcher(soc);
            if (m.find()) {
                // Exynos 2200 and later carry Xclipse (AMD RDNA); earlier ones carry Mali.
                int number = parseInt(m.group(1));
                return number >= 2200 ? GpuFamily.XCLIPSE : GpuFamily.MALI;
            }
            return GpuFamily.UNKNOWN;
        }
        if (mfr.contains("mediatek") || mfr.contains("hisilicon") || mfr.contains("unisoc")) return GpuFamily.MALI;
        return GpuFamily.UNKNOWN;
    }

    private static final Pattern ADRENO_8XX = Pattern.compile("adreno\\D*8\\d\\d\\b");

    /** An Adreno 8xx (named by OpenCL or Vulkan) whose driver carries a compiler starting E031.47.12. */
    private static boolean isWarnedAdrenoCompiler(@NonNull Facts facts) {
        if (!facts.openClDriver.contains("E031.47.12")) return false;
        return ADRENO_8XX.matcher(facts.openClDeviceName.toLowerCase(Locale.ROOT)).find()
            || ADRENO_8XX.matcher(facts.vulkanDeviceName.toLowerCase(Locale.ROOT)).find();
    }

    private static boolean isTensorG4OrNewer(@NonNull Facts facts) {
        return facts.socManufacturer.toLowerCase(Locale.ROOT).contains("google")
            && tensorGeneration(facts.socModel.toLowerCase(Locale.ROOT)) >= 4;
    }

    /** The N of "Tensor GN", 0 when the model is not a Tensor. */
    private static int tensorGeneration(@NonNull String lowerSocModel) {
        Matcher m = TENSOR_GENERATION.matcher(lowerSocModel);
        return m.find() ? parseInt(m.group(1)) : 0;
    }

    private static int parseInt(@NonNull String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------------------- CPU features

    /**
     * The labels for the CPU features in a {@code /proc/cpuinfo} {@code Features} line:
     * {@code dotprod} (asimddp), {@code i8mm}, {@code sme2}. Labels only: XNNPACK and MNN pick their
     * kernels at run time, so nothing here gates anything (spec §2.3).
     */
    @NonNull
    static Set<String> parseCpuFeatures(@Nullable String featuresLine) {
        Set<String> found = new LinkedHashSet<>();
        if (featuresLine == null) return found;
        List<String> tokens = Arrays.asList(featuresLine.toLowerCase(Locale.ROOT).trim().split("\\s+"));
        if (tokens.contains("asimddp")) found.add("dotprod");
        if (tokens.contains("i8mm")) found.add("i8mm");
        if (tokens.contains("sme2")) found.add("sme2");
        return found;
    }

    // ------------------------------------------------------------------- probe, cache and access

    private static final String KEY_CACHE_BUILD = "tai_gpu_probe_build";
    private static final String KEY_CACHE_FACTS = "tai_gpu_probe_facts";
    private static final String KEY_PROBE_INFLIGHT = "tai_gpu_probe_inflight";
    private static final Object PROBE_LOCK = new Object();

    @Nullable private static volatile TaiPlatformCaps sProbed;

    /**
     * The probe's verdict, or an "unknown" answer (GPU {@code UNKNOWN}) until a probe has run in this
     * process or its cached result has been read with {@link #cached(Context)}. Never blocks.
     */
    @NonNull
    public static TaiPlatformCaps cached() {
        TaiPlatformCaps probed = sProbed;
        return probed != null ? probed : fromDevice(Facts.unprobed(), false);
    }

    /**
     * {@link #cached()}, after reading the preferences cache when this process has not yet: cheap, and
     * still never starts a probe. Falls back to the unprobed answer.
     */
    @NonNull
    public static TaiPlatformCaps cached(@NonNull Context context) {
        TaiPlatformCaps probed = sProbed;
        if (probed != null) return probed;
        Facts stored = storedFacts(prefs(context));
        if (stored == null) return fromDevice(Facts.unprobed(), false);
        TaiPlatformCaps caps = fromDevice(stored, true);
        sProbed = caps;
        return caps;
    }

    /**
     * The probe, run once per app build: the cached facts when they exist, else a native probe (OpenCL,
     * Vulkan) and the Build properties, then cached. Blocking and touches drivers: not for the main
     * thread (see {@link #probeAsync}). A probe that crashed the process last time is not repeated;
     * the GPU is then treated as having no OpenCL.
     */
    @NonNull
    public static TaiPlatformCaps probe(@NonNull Context context) {
        synchronized (PROBE_LOCK) {
            TaiPlatformCaps known = sProbed;
            if (known != null) return known;
            SharedPreferences prefs = prefs(context);
            Facts facts = storedFacts(prefs);
            if (facts == null) {
                String build = cacheBuild();
                boolean crashedBefore = build.equals(prefs.getString(KEY_PROBE_INFLIGHT, null));
                if (crashedBefore) {
                    Log.w(TAG, "the GPU probe crashed the process last time; treating OpenCL as absent");
                    facts = collectFacts(false);
                } else {
                    // commit(), not apply(): the marker must be on disk before a driver can take the process down.
                    prefs.edit().putString(KEY_PROBE_INFLIGHT, build).commit();
                    facts = collectFacts(true);
                    prefs.edit().remove(KEY_PROBE_INFLIGHT).commit();
                }
                try {
                    prefs.edit().putString(KEY_CACHE_BUILD, build).putString(KEY_CACHE_FACTS, facts.toJson().toString()).apply();
                } catch (JSONException e) {
                    Log.w(TAG, "could not cache the GPU probe", e);
                }
            }
            TaiPlatformCaps caps = fromDevice(facts, true);
            sProbed = caps;
            return caps;
        }
    }

    /** Runs {@link #probe} on a background thread; {@code onDone} (may be {@code null}) runs there after it. */
    public static void probeAsync(@NonNull Context context, @Nullable Runnable onDone) {
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            try {
                probe(app);
            } catch (Throwable t) {
                Log.w(TAG, "GPU probe failed", t);
            }
            if (onDone != null) onDone.run();
        }, "tai-gpu-probe");
        thread.setDaemon(true);
        thread.start();
    }

    @NonNull
    private static TaiPlatformCaps fromDevice(@NonNull Facts facts, boolean probed) {
        String[] abis = Build.SUPPORTED_ABIS;
        List<String> abiList = abis == null ? Collections.<String>emptyList() : Arrays.asList(abis);
        return of(Build.VERSION.SDK_INT, new ArrayList<>(abiList), facts, probed);
    }

    @NonNull
    private static Facts collectFacts(boolean runNative) {
        Boolean openCl = null;
        TaiGpuProbe.OpenClDevice clDevice = null;
        TaiGpuProbe.VulkanDevice vulkan = null;
        if (runNative) {
            openCl = TaiGpuProbe.openClAvailable();
            if (Boolean.TRUE.equals(openCl)) clDevice = TaiGpuProbe.openClDevice();
            vulkan = TaiGpuProbe.vulkanDevice();
        } else {
            openCl = Boolean.FALSE;
        }
        String socManufacturer = "";
        String socModel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socManufacturer = Build.SOC_MANUFACTURER == null ? "" : Build.SOC_MANUFACTURER;
            socModel = Build.SOC_MODEL == null ? "" : Build.SOC_MODEL;
        }
        return new Facts(openCl, vulkan == null ? -1 : vulkan.vendorId, vulkan == null ? "" : vulkan.name,
            systemProperty("ro.hardware.egl"), socManufacturer, socModel, Build.MODEL,
            clDevice == null ? "" : clDevice.name, clDevice == null ? "" : clDevice.driver, readCpuFeaturesLine());
    }

    /** The first {@code Features} line of {@code /proc/cpuinfo}, empty when unreadable. */
    @NonNull
    private static String readCpuFeaturesLine() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase(Locale.ROOT).startsWith("features")) {
                    int colon = line.indexOf(':');
                    return colon < 0 ? "" : line.substring(colon + 1).trim();
                }
            }
        } catch (IOException | SecurityException e) {
            Log.w(TAG, "could not read /proc/cpuinfo", e);
        }
        return "";
    }

    @NonNull
    private static String systemProperty(@NonNull String name) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            String value = (String) properties.getMethod("get", String.class).invoke(null, name);
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    @Nullable
    private static Facts storedFacts(@NonNull SharedPreferences prefs) {
        if (!cacheBuild().equals(prefs.getString(KEY_CACHE_BUILD, null))) return null;
        return Facts.fromJson(prefs.getString(KEY_CACHE_FACTS, null));
    }

    /** The cache key: versionCode alone is shared with upstream Termux and never moves, so the name goes with it. */
    @NonNull
    private static String cacheBuild() {
        return BuildConfig.VERSION_CODE + ":" + BuildConfig.VERSION_NAME;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
