package com.termux.app.x11;

import android.content.Context;

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
 * the profiles below. The launcher detects and recommends; it never writes any of this into a
 * shell.
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

        Recommendation(@NonNull Profile profile, @NonNull String reason, boolean installed,
                       @NonNull List<String> packages, @NonNull List<String> env,
                       @Nullable String serverCommand) {
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

        Result(@NonNull String gpu, @NonNull List<Recommendation> ranked) {
            this.gpu = gpu;
            this.ranked = Collections.unmodifiableList(ranked);
        }

        /** The best profile for this GPU, installed or not; null only when nothing applies. */
        @Nullable
        public Recommendation recommended() {
            for (Recommendation r : ranked) if (r.profile != Profile.SOFTWARE) return r;
            return null;
        }

        /** One sentence for the settings row. */
        @NonNull
        public String headline() {
            Recommendation best = recommended();
            if (best == null) return gpu + " draws Linux apps in software";
            String line = gpu + " can run Linux apps on the GPU with the " + best.profile.id + " profile";
            if (!best.installed) line += " once " + String.join(", ", best.packages) + " are installed";
            return line;
        }

        /** Shell-sourceable: the top profile's exports, then its helper as a comment. */
        @NonNull
        public String toEnv() {
            Recommendation best = recommended() != null ? recommended() : ranked.get(ranked.size() - 1);
            StringBuilder out = new StringBuilder();
            out.append("# ").append(gpu).append(": ").append(best.profile.id).append(" — ")
                .append(best.reason).append('\n');
            if (!best.installed) out.append("# install first: pkg install ")
                .append(String.join(" ", best.packages)).append('\n');
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
            } catch (JSONException ignored) {
                // Nothing above can throw for these types.
            }
            return json;
        }
    }

    private static final String ICD_DIR = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/share/vulkan/icd.d/";

    private X11GpuProbe() {}

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
            "a Qualcomm Adreno GPU: Turnip drives it directly and Zink puts OpenGL on top",
            installed, Arrays.asList("mesa", "mesa-vulkan-icd-freedreno", "vulkan-loader-android"),
            env, null);
    }

    private static Recommendation virgl(Inputs in, boolean angle) {
        List<String> env = Arrays.asList("GALLIUM_DRIVER=virpipe", "MESA_GL_VERSION_OVERRIDE=4.3COMPAT",
            "MESA_GLES_VERSION_OVERRIDE=3.2", "MESA_NO_ERROR=1", "LIBGL_DRI3_DISABLE=1");
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

    private static Recommendation vulkanWrapper(Inputs in, boolean mali) {
        List<String> env = new ArrayList<>(Arrays.asList(
            "VK_ICD_FILENAMES=" + ICD_DIR + "wrapper_icd.aarch64.json",
            "MESA_LOADER_DRIVER_OVERRIDE=zink"));
        if (mali) env.addAll(Arrays.asList("MESA_VK_WSI_PRESENT_MODE=mailbox", "MESA_VK_WSI_DEBUG=blit"));
        return new Recommendation(Profile.VULKAN_WRAPPER,
            "the phone's Vulkan driver, wrapped for Zink",
            in.hasIcd("wrapper") && in.mesaDri, Arrays.asList("vulkan-wrapper-android", "mesa"),
            env, null);
    }

    // ---- Gathering ----------------------------------------------------------------------------

    @Nullable private static volatile Result cached;

    /** The device's answer, gathered once. Call off the main thread: it builds a GL context. */
    @NonNull
    public static Result probe(@NonNull Context context) {
        Result result = cached;
        if (result != null) return result;
        Inputs in = new Inputs();
        in.eglVendor = systemProperty("ro.hardware.egl");
        in.vulkanVendor = systemProperty("ro.hardware.vulkan");
        in.kgsl = new File("/dev/kgsl-3d0").exists();
        in.kgslModel = readFirstLine(new File("/sys/class/kgsl/kgsl-3d0/gpu_model"));
        in.glRenderer = X11GlRendererProbe.rendererString();
        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        in.virglServer = new File(prefix + "/bin/virgl_test_server_android").canExecute();
        in.angle = new File(prefix + "/lib/libGLESv2_angle.so").exists()
            || new File(prefix + "/lib/angle").isDirectory();
        String[] icds = new File(ICD_DIR).list();
        in.icdFiles = icds == null ? Collections.emptyList() : Arrays.asList(icds);
        in.mesaDri = new File(prefix + "/lib/dri").isDirectory();
        result = evaluate(in);
        cached = result;
        return result;
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
