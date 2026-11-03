package com.termux.app.x11;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What this phone's GPU can do for Linux apps on the display, and the exact environment that
 * asks for it. The display server itself always draws in software — X apps render into their own
 * buffers and hand pixels over — so what can be accelerated is the client side, through one of
 * the profiles below. The launcher detects and ranks; the user picks on the Graphics
 * acceleration page ({@link X11GpuProfileStore}), and the benchmark script tests this class's
 * table ({@link Result#toTable}) rather than keeping one of its own.
 *
 * <p>{@link #evaluate} is pure and fixture-tested; {@link #probe} gathers the inputs from the
 * device and the prefix, once, off the main thread.
 */
public final class X11GpuProbe {

    /** The client-side acceleration profiles, as the display guide lists them. */
    public enum Profile {
        TURNIP_ZINK("turnip-zink"),
        VIRGL("virgl"),
        VIRGL_ANGLE("virgl-angle"),
        VULKAN_WRAPPER("vulkan-wrapper"),
        SOFTWARE("software");

        public final String id;

        Profile(String id) { this.id = id; }

        /** The profile named {@code id}, or null. */
        @Nullable
        public static Profile of(@Nullable String id) {
            for (Profile profile : values()) if (profile.id.equals(id)) return profile;
            return null;
        }
    }

    /** Everything the decision reads. Built by {@link #probe} or by a test. */
    public static final class Inputs {
        /** {@code ro.hardware.egl}, e.g. adreno, mali, emulation. */
        @NonNull public String eglVendor = "";
        /** {@code ro.hardware.vulkan}, e.g. adreno, mali; empty when there is no driver. */
        @NonNull public String vulkanVendor = "";
        /** {@code /dev/kgsl-3d0} exists — a Qualcomm GPU the freedreno kernel path can drive. */
        public boolean kgsl;
        /** {@code /sys/class/kgsl/kgsl-3d0/gpu_model}, e.g. Adreno730v2. */
        @NonNull public String kgslModel = "";
        /** The GL renderer string from a throwaway context, e.g. "Adreno (TM) 730". */
        @NonNull public String glRenderer = "";
        /** {@code $PREFIX/bin/virgl_test_server_android} is installed. */
        public boolean virglServer;
        /** The angle-android package is installed. */
        public boolean angle;
        /** File names in {@code $PREFIX/share/vulkan/icd.d/}. */
        @NonNull public List<String> icdFiles = Collections.emptyList();
        /** {@code $PREFIX/lib/dri/} exists — Mesa's client drivers are installed. */
        public boolean mesaDri;
        /**
         * {@code ro.board.platform} and {@code ro.hardware}, e.g. "mt6768": the chip by its SoC
         * name, which {@link #knownFreeze} reads too, so a GL context that could not be made
         * (no renderer string) does not hide a known-freeze phone.
         */
        @NonNull public String platform = "";
        /**
         * The system Vulkan driver's API version as Android encodes it
         * ({@code FEATURE_VULKAN_HARDWARE_VERSION}), 0 when unknown. Below {@link X11GpuProbe#VULKAN_1_3}
         * the wrapper's Zink cannot run on it.
         */
        public int vulkanApiVersion;

        boolean hasIcd(@NonNull String needle) {
            for (String name : icdFiles) if (name.contains(needle)) return true;
            return false;
        }

        @NonNull
        String gpuName() {
            if (!glRenderer.isEmpty()) {
                String name = glRenderer.replace("(TM)", "").replaceAll("\\s+", " ").trim();
                // Emulators and translators spell out their whole stack in parentheses; the
                // name before it is what a person recognises.
                int paren = name.indexOf(" (");
                if (paren > 0 && name.length() > 40) name = name.substring(0, paren).trim();
                return name;
            }
            if (!kgslModel.isEmpty()) return kgslModel;
            if (!eglVendor.isEmpty()) return eglVendor;
            return "this GPU";
        }
    }

    /** One profile, ranked, with what it needs and the exact lines that select it. */
    public static final class Recommendation {
        @NonNull public final Profile profile;
        @NonNull public final String reason;
        /** Every package the profile needs is in the prefix. */
        public final boolean installed;
        @NonNull public final List<String> packages;
        /** {@code KEY=VALUE} lines to export in the shell that starts the app. */
        @NonNull public final List<String> env;
        /** A helper process to keep running in Termux, or null. */
        @Nullable public final String serverCommand;
        /** Why the benchmark must not try this profile on this phone, or null: it becomes a SKIP. */
        @Nullable public final String notTriedReason;

        Recommendation(@NonNull Profile profile, @NonNull String reason, boolean installed,
                       @NonNull List<String> packages, @NonNull List<String> env,
                       @Nullable String serverCommand) {
            this(profile, reason, installed, packages, env, serverCommand, null);
        }

        Recommendation(@NonNull Profile profile, @NonNull String reason, boolean installed,
                       @NonNull List<String> packages, @NonNull List<String> env,
                       @Nullable String serverCommand, @Nullable String notTriedReason) {
            this.notTriedReason = notTriedReason;
            this.profile = profile;
            this.reason = reason;
            this.installed = installed;
            this.packages = packages;
            this.env = env;
            this.serverCommand = serverCommand;
        }
    }

    /** The ranked profiles for one device. */
    public static final class Result {
        @NonNull public final String gpu;
        @NonNull public final List<Recommendation> ranked;
        /** The GPU is one {@link #knownFreeze} names: the virgl profiles have frozen such phones. */
        public final boolean knownFreeze;

        Result(@NonNull String gpu, @NonNull List<Recommendation> ranked) {
            this(gpu, ranked, false);
        }

        Result(@NonNull String gpu, @NonNull List<Recommendation> ranked, boolean knownFreeze) {
            this.gpu = gpu;
            this.ranked = Collections.unmodifiableList(ranked);
            this.knownFreeze = knownFreeze;
        }

        /** {@code profile}'s row, or null when it does not fit this GPU. */
        @Nullable
        public Recommendation find(@NonNull Profile profile) {
            for (Recommendation r : ranked) if (r.profile == profile) return r;
            return null;
        }

        /** The software row, which every result has. */
        @NonNull
        public Recommendation software() {
            Recommendation software = find(Profile.SOFTWARE);
            return software != null ? software : ranked.get(ranked.size() - 1);
        }

        /** The best profile for this GPU, installed or not; null only when nothing applies. */
        @Nullable
        public Recommendation recommended() {
            // Software ranked first is the recommendation (a known-freeze GPU): nothing on the GPU.
            if (!ranked.isEmpty() && ranked.get(0).profile == Profile.SOFTWARE) return null;
            for (Recommendation r : ranked) if (r.profile != Profile.SOFTWARE) return r;
            return null;
        }

        /** Shell-sourceable: the top profile's exports, then its helper as a comment. */
        @NonNull
        public String toEnv() {
            Recommendation best = recommended() != null ? recommended() : software();
            StringBuilder out = new StringBuilder();
            out.append("# ").append(gpu).append(": ").append(best.profile.id).append(" — ")
                .append(best.reason).append('\n');
            if (!best.installed) out.append("# install first: ").append(installCommand(best.packages))
                .append('\n');
            for (String line : best.env) out.append("export ").append(line).append('\n');
            if (best.serverCommand != null) out.append("# keep running in Termux: ")
                .append(best.serverCommand).append('\n');
            return out.toString();
        }

        @NonNull
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("gpu", gpu);
                Recommendation best = recommended();
                json.put("recommended", best == null ? JSONObject.NULL : best.profile.id);
                JSONArray profiles = new JSONArray();
                for (Recommendation r : ranked) {
                    JSONObject item = new JSONObject();
                    item.put("profile", r.profile.id);
                    item.put("reason", r.reason);
                    item.put("installed", r.installed);
                    item.put("packages", new JSONArray(r.packages));
                    item.put("env", new JSONArray(r.env));
                    item.put("server", r.serverCommand == null ? JSONObject.NULL : r.serverCommand);
                    profiles.put(item);
                }
                json.put("profiles", profiles);
                json.put("env_text", toEnv());
                json.put("known_freeze", knownFreeze);
            } catch (JSONException ignored) {
                // Nothing above can throw for these types.
            }
            return json;
        }

        /**
         * The profile table {@code termux-x11-gpu-setup} benchmarks, as bash it {@code eval}s
         * after declaring {@code PKGS ENV HELPER WHY SKIP} associative: this class is the one
         * owner of which profiles fit, what they need and what selects them, and the script only
         * measures. {@code CANDIDATES} is the ranking without software, which the script always
         * tests first as its baseline. On a {@link #knownFreeze} GPU the virgl profiles carry a
         * {@code SKIP} reason, so the benchmark never opens the GL path that froze such phones.
         */
        @NonNull
        public String toTable() {
            StringBuilder out = new StringBuilder();
            out.append("# The graphics profiles that fit ").append(gpu)
                .append(", best first; written by the launcher.\n");
            out.append("GPU_NAME=").append(ProotDistro.singleQuote(gpu)).append('\n');
            StringBuilder candidates = new StringBuilder();
            for (Recommendation r : ranked) {
                if (r.profile == Profile.SOFTWARE) continue;
                candidates.append(candidates.length() == 0 ? "" : " ").append(ProotDistro.singleQuote(r.profile.id));
            }
            out.append("CANDIDATES=(").append(candidates).append(")\n");
            for (Recommendation r : ranked) {
                String key = "[" + ProotDistro.singleQuote(r.profile.id) + "]=";
                String helper = r.serverCommand == null ? "-"
                    : r.serverCommand.replaceAll("\\s*&\\s*$", "");
                out.append("PKGS").append(key).append(ProotDistro.singleQuote(String.join(" ", r.packages))).append('\n');
                out.append("ENV").append(key).append(ProotDistro.singleQuote(String.join(" ", r.env))).append('\n');
                out.append("HELPER").append(key).append(ProotDistro.singleQuote(helper)).append('\n');
                out.append("WHY").append(key).append(ProotDistro.singleQuote(r.reason)).append('\n');
                if (knownFreeze && r.profile != Profile.SOFTWARE) {
                    out.append("SKIP").append(key).append(ProotDistro.singleQuote(isVirgl(r.profile)
                        ? "it has frozen phones with this graphics chip"
                        : "it is untested on phones with this graphics chip, where virgl froze them"))
                        .append('\n');
                } else if (r.notTriedReason != null) {
                    out.append("SKIP").append(key).append(ProotDistro.singleQuote(r.notTriedReason))
                        .append('\n');
                }
            }
            return out.toString();
        }
    }

    /** {@code FEATURE_VULKAN_HARDWARE_VERSION} for Vulkan 1.3, the least Zink on the wrapper runs on. */
    static final int VULKAN_1_3 = 0x403000;

    private static final String ICD_DIR = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/share/vulkan/icd.d/";

    private X11GpuProbe() {}

    /**
     * GPUs on which a Linux app opening a GL context through the virgl server has hung the whole
     * phone, needing a forced restart (#162: Mali-G52 on a Moto G15 / MT6768, 2026-10-24, with
     * Code-OSS and fcitx). Matched against the GL renderer string, lower-cased, and against the
     * SoC name in {@link #KNOWN_FREEZE_PLATFORMS}, for when no GL context could be made to name
     * the renderer. Software rendering worked on the same phone, so it is ranked first there;
     * every GPU profile carries a warning and the benchmark tries none of them.
     */
    static final String[] KNOWN_FREEZE_RENDERERS = {"mali-g52"};
    /** The same phones by {@code ro.board.platform} / {@code ro.hardware}: the MT6768 (Helio G85). */
    static final String[] KNOWN_FREEZE_PLATFORMS = {"mt6768"};

    /** The renderer names of {@link #KNOWN_FREEZE_RENDERERS}, for the setup wizard's device tier. */
    public static String[] knownFreezeRenderers() { return KNOWN_FREEZE_RENDERERS.clone(); }

    /** The platform names of {@link #KNOWN_FREEZE_PLATFORMS}, for the setup wizard's device tier. */
    public static String[] knownFreezePlatforms() { return KNOWN_FREEZE_PLATFORMS.clone(); }

    /** Whether {@code in} names a GPU on which the virgl profiles are known to freeze the phone. */
    static boolean knownFreeze(@NonNull Inputs in) {
        String renderer = in.glRenderer.toLowerCase(Locale.ROOT);
        for (String bad : KNOWN_FREEZE_RENDERERS) if (renderer.contains(bad)) return true;
        String platform = in.platform.toLowerCase(Locale.ROOT);
        for (String bad : KNOWN_FREEZE_PLATFORMS) if (platform.contains(bad)) return true;
        return false;
    }

    /** Whether {@code profile} draws through the virgl server, the path that froze (#162). */
    public static boolean isVirgl(@NonNull Profile profile) {
        return profile == Profile.VIRGL || profile == Profile.VIRGL_ANGLE;
    }

    /** The warning a virgl profile carries on a {@link #knownFreeze} GPU. */
    static final String FREEZE_WARNING =
        "; on this GPU it has frozen the whole phone when an app opened OpenGL (a forced restart"
        + " was the only way out), so software rendering is the safe pick";
    /** The warning every other GPU profile carries there: nobody has shown it is safe. */
    static final String UNTESTED_WARNING =
        "; untested on this GPU, where the virgl profiles have frozen the whole phone, so software"
        + " rendering is the safe pick";

    /** The pure decision. */
    @NonNull
    public static Result evaluate(@NonNull Inputs in) {
        String vendors = (in.eglVendor + " " + in.vulkanVendor + " " + in.glRenderer + " "
            + in.kgslModel).toLowerCase(Locale.ROOT);
        boolean adreno = in.kgsl || vendors.contains("adreno") || vendors.contains("qcom")
            || vendors.contains("qualcomm");
        boolean mali = vendors.contains("mali");
        boolean otherGles = vendors.contains("xclipse") || vendors.contains("powervr")
            || vendors.contains("img") || vendors.contains("samsung");
        boolean emulated = vendors.contains("emulation") || vendors.contains("swiftshader")
            || vendors.contains("angle") || vendors.contains("llvmpipe") || vendors.contains("lavapipe");
        boolean vulkanDriver = !in.vulkanVendor.isEmpty();

        List<Recommendation> ranked = new ArrayList<>();
        if (adreno) {
            ranked.add(turnipZink(in));
            ranked.add(virgl(in, false));
            if (vulkanDriver) ranked.add(vulkanWrapper(in, false));
        } else if (mali) {
            ranked.add(virgl(in, true));
            if (vulkanDriver) ranked.add(vulkanWrapper(in, true));
            ranked.add(virgl(in, false));
        } else if (otherGles) {
            ranked.add(virgl(in, true));
            if (vulkanDriver) ranked.add(vulkanWrapper(in, false));
            ranked.add(virgl(in, false));
        } else if (!emulated) {
            ranked.add(virgl(in, false));
            if (vulkanDriver) ranked.add(vulkanWrapper(in, false));
        }
        if (knownFreeze(in)) {
            // Software first, and every GPU profile after it with its warning: the user can
            // still choose them, told what happened on this GPU.
            List<Recommendation> warned = new ArrayList<>();
            for (Recommendation r : ranked) {
                warned.add(new Recommendation(r.profile,
                    r.reason + (isVirgl(r.profile) ? FREEZE_WARNING : UNTESTED_WARNING), r.installed,
                    r.packages, r.env, r.serverCommand));
            }
            ranked = new ArrayList<>();
            ranked.add(new Recommendation(Profile.SOFTWARE,
                "the virgl profiles have frozen phones with this GPU: software rendering is the safe path",
                true, Collections.singletonList("mesa"),
                Arrays.asList("LIBGL_ALWAYS_SOFTWARE=1", "GALLIUM_DRIVER=llvmpipe"), null));
            ranked.addAll(warned);
            return new Result(in.gpuName(), ranked, true);
        }
        ranked.add(new Recommendation(Profile.SOFTWARE,
            emulated ? "an emulated GPU: software rendering is the dependable path"
                : "works everywhere; the floor",
            in.mesaDri, Collections.singletonList("mesa"),
            Arrays.asList("LIBGL_ALWAYS_SOFTWARE=1", "GALLIUM_DRIVER=llvmpipe",
                "MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"), null));
        return new Result(in.gpuName(), ranked);
    }

    private static Recommendation turnipZink(Inputs in) {
        List<String> env = new ArrayList<>(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink",
            "TU_DEBUG=noconform"));
        // The newest Adreno generation needs the conservative sync rules from phosh-termux-gpu.
        if (in.kgslModel.toLowerCase(Locale.ROOT).matches(".*adreno\\s*8\\d\\d.*")
            || in.glRenderer.toLowerCase(Locale.ROOT).matches(".*adreno.*8\\d\\d.*")) {
            env.set(1, "TU_DEBUG=noconform,flushall,syncdraw");
        }
        if (in.icdFiles.size() > 1) env.add("VK_ICD_FILENAMES=" + ICD_DIR + "freedreno_icd.aarch64.json");
        boolean installed = in.mesaDri && in.hasIcd("freedreno");
        return new Recommendation(Profile.TURNIP_ZINK,
            "a Qualcomm Adreno GPU: Turnip drives it directly and Zink puts OpenGL on top (the Vulkan loader comes with mesa)",
            installed, Arrays.asList("mesa", "mesa-vulkan-icd-freedreno"), env, null);
    }

    private static Recommendation virgl(Inputs in, boolean angle) {
        // Without LIBGL_KOPPER_DISABLE, Mesa draws through Zink on any Vulkan driver it finds and
        // never reads GALLIUM_DRIVER: on an Adreno with Turnip installed, "virgl" was Turnip.
        List<String> env = Arrays.asList("GALLIUM_DRIVER=virpipe", "MESA_GL_VERSION_OVERRIDE=4.3COMPAT",
            "MESA_GLES_VERSION_OVERRIDE=3.2", "MESA_NO_ERROR=1", "LIBGL_DRI3_DISABLE=1", "LIBGL_KOPPER_DISABLE=1");
        if (angle) {
            return new Recommendation(Profile.VIRGL_ANGLE,
                "this GPU's own OpenGL driver is unreliable for Linux apps; virgl through ANGLE is the safe path",
                in.virglServer && in.angle && in.mesaDri,
                Arrays.asList("virglrenderer-android", "angle-android", "mesa"), env,
                "virgl_test_server_android --angle-gl &");
        }
        return new Recommendation(Profile.VIRGL,
            "virgl forwards OpenGL to the phone's own driver",
            in.virglServer && in.mesaDri, Arrays.asList("virglrenderer-android", "mesa"), env,
            "virgl_test_server_android &");
    }

    /**
     * The Vulkan wrapper's package. It is in no Termux repository: the benchmark script downloads
     * a pinned build and checks it ({@code termux-x11-gpu-setup --install-wrapper}), and that
     * script is the one place its address and checksums are kept.
     */
    static final String WRAPPER_PACKAGE = "vulkan-wrapper-android";

    /** What a shell runs to install {@code packages}: the wrapper through the script, the rest by name. */
    @NonNull
    static String installCommand(@NonNull List<String> packages) {
        List<String> fromRepo = new ArrayList<>(packages);
        boolean wrapper = fromRepo.remove(WRAPPER_PACKAGE);
        String repo = fromRepo.isEmpty() ? "" : "pkg install " + String.join(" ", fromRepo);
        if (!wrapper) return repo;
        String own = "termux-x11-gpu-setup --install-wrapper";
        return repo.isEmpty() ? own : own + " && " + repo;
    }

    private static Recommendation vulkanWrapper(Inputs in, boolean mali) {
        List<String> env = new ArrayList<>(Arrays.asList(
            "VK_ICD_FILENAMES=" + ICD_DIR + "wrapper_icd.aarch64.json",
            "MESA_LOADER_DRIVER_OVERRIDE=zink"));
        if (mali) env.addAll(Arrays.asList("MESA_VK_WSI_PRESENT_MODE=mailbox", "MESA_VK_WSI_DEBUG=blit"));
        return new Recommendation(Profile.VULKAN_WRAPPER,
            "the phone's Vulkan driver, wrapped for Zink",
            in.hasIcd("wrapper") && in.mesaDri, Arrays.asList(WRAPPER_PACKAGE, "mesa"),
            env, null, in.vulkanApiVersion > 0 && in.vulkanApiVersion < VULKAN_1_3
                ? "the phone's graphics driver is too old for it" : null);
    }

    // ---- Gathering ----------------------------------------------------------------------------

    /** The one expensive input, gathered once: the GL context does not change under a running app. */
    @Nullable private static volatile String cachedRenderer;

    /**
     * The device's answer. Only the renderer string is cached — the prefix is read every time, so
     * a package installed a moment ago is seen. Call off the main thread the first time: it builds
     * a throwaway GL context.
     */
    @NonNull
    public static Result probe(@NonNull Context context) {
        Inputs in = new Inputs();
        in.eglVendor = systemProperty("ro.hardware.egl");
        in.vulkanVendor = systemProperty("ro.hardware.vulkan");
        in.platform = (systemProperty("ro.board.platform") + " " + systemProperty("ro.hardware")).trim();
        in.kgsl = new File("/dev/kgsl-3d0").exists();
        in.kgslModel = readFirstLine(new File("/sys/class/kgsl/kgsl-3d0/gpu_model"));
        String renderer = cachedRenderer;
        if (renderer == null) {
            renderer = X11GlRendererProbe.rendererString();
            cachedRenderer = renderer;
        }
        in.glRenderer = renderer;
        in.vulkanApiVersion = vulkanApiVersion(context);
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        in.virglServer = new File(prefix + "/bin/virgl_test_server_android").canExecute();
        in.angle = new File(prefix + "/lib/libGLESv2_angle.so").exists()
            || new File(prefix + "/lib/angle").isDirectory();
        String[] icds = new File(ICD_DIR).list();
        in.icdFiles = icds == null ? Collections.emptyList() : Arrays.asList(icds);
        in.mesaDri = new File(prefix + "/lib/dri").isDirectory();
        return evaluate(in);
    }

    /** The system Vulkan version from the hardware feature, or 0 when the phone does not say. */
    private static int vulkanApiVersion(@NonNull Context context) {
        try {
            for (FeatureInfo feature : context.getPackageManager().getSystemAvailableFeatures()) {
                if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) return feature.version;
            }
        } catch (Exception ignored) {
            // Unknown is the safe answer: the wrapper is tried as before.
        }
        return 0;
    }

    @NonNull
    private static String systemProperty(@NonNull String name) {
        try {
            Process process = new ProcessBuilder("/system/bin/getprop", name)
                .redirectErrorStream(true).start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                process.destroy();
            }
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    private static String readFirstLine(@NonNull File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
