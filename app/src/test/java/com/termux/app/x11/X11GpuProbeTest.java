package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * The recommendation table, per GPU family. Each fixture is a phone as the probe would see it;
 * the assertions are the profile order and the exact lines the guide's table promises.
 */
public class X11GpuProbeTest {

    private static List<String> order(X11GpuProbe.Result result) {
        List<String> ids = new java.util.ArrayList<>();
        for (X11GpuProbe.Recommendation r : result.ranked) ids.add(r.profile.id);
        return ids;
    }

    @Test public void adrenoWithTurnipInstalledIsZinkFirst() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "adreno";
        in.vulkanVendor = "adreno";
        in.kgsl = true;
        in.kgslModel = "Adreno730v2";
        in.glRenderer = "Adreno (TM) 730";
        in.mesaDri = true;
        in.icdFiles = Arrays.asList("freedreno_icd.aarch64.json");

        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);

        assertEquals(Arrays.asList("turnip-zink", "virgl", "vulkan-wrapper", "software"), order(result));
        X11GpuProbe.Recommendation best = result.recommended();
        assertNotNull(best);
        assertTrue(best.installed);
        assertEquals(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform"), best.env);
        assertNull("nothing to keep running for Turnip", best.serverCommand);
        assertEquals("Adreno 730", result.gpu);
        assertTrue(result.headline(), result.headline().contains("turnip-zink"));
        assertTrue(result.toEnv().contains("export MESA_LOADER_DRIVER_OVERRIDE=zink\n"));
    }

    @Test public void adrenoWithoutThePackagesStillRecommendsZinkAndNamesThem() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.kgsl = true;
        in.kgslModel = "Adreno650";

        X11GpuProbe.Recommendation best = X11GpuProbe.evaluate(in).recommended();

        assertNotNull(best);
        assertEquals(X11GpuProbe.Profile.TURNIP_ZINK, best.profile);
        assertFalse(best.installed);
        assertTrue(best.packages.contains("mesa-vulkan-icd-freedreno"));
        assertTrue(X11GpuProbe.evaluate(in).toEnv().contains("pkg install mesa mesa-vulkan-icd-freedreno"));
    }

    @Test public void theNewestAdrenoGetsTheConservativeSyncFlagsAndAPinnedIcd() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.kgsl = true;
        in.kgslModel = "Adreno830";
        in.icdFiles = Arrays.asList("freedreno_icd.aarch64.json", "wrapper_icd.aarch64.json");
        in.mesaDri = true;

        X11GpuProbe.Recommendation best = X11GpuProbe.evaluate(in).recommended();

        assertNotNull(best);
        assertEquals(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform,flushall,syncdraw",
            "VK_ICD_FILENAMES=/data/data/com.termux/files/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"),
            best.env);
    }

    @Test public void maliGoesThroughAngleFirstAndItsWrapperNeedsTheMailboxFlags() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.vulkanVendor = "mali";
        in.glRenderer = "Mali-G710";
        in.virglServer = true;
        in.angle = true;
        in.mesaDri = true;

        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);

        assertEquals(Arrays.asList("virgl-angle", "vulkan-wrapper", "virgl", "software"), order(result));
        assertEquals("virgl_test_server_android --angle-gl &", result.ranked.get(0).serverCommand);
        assertTrue(result.ranked.get(0).installed);
        assertTrue(result.ranked.get(1).env.contains("MESA_VK_WSI_PRESENT_MODE=mailbox"));
        assertTrue(result.ranked.get(1).env.contains("MESA_VK_WSI_DEBUG=blit"));
        assertFalse("no wrapper icd installed", result.ranked.get(1).installed);
    }

    @Test public void xclipseAndPowerVrTakeTheAnglePathWithoutMaliFlags() {
        for (String renderer : new String[]{"Samsung Xclipse 920", "PowerVR Rogue GE8320"}) {
            X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
            in.glRenderer = renderer;
            in.vulkanVendor = "samsung";
            X11GpuProbe.Result result = X11GpuProbe.evaluate(in);
            assertEquals(renderer, "virgl-angle", result.ranked.get(0).profile.id);
            assertFalse(renderer, result.ranked.get(1).env.contains("MESA_VK_WSI_PRESENT_MODE=mailbox"));
        }
    }

    @Test public void anEmulatorHasOnlyTheSoftwareFloor() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "emulation";
        in.glRenderer = "Android Emulator OpenGL ES Translator (Google SwiftShader)";
        in.mesaDri = true;

        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);

        assertEquals(Arrays.asList("software"), order(result));
        assertNull(result.recommended());
        assertTrue(result.headline().contains("software"));
        assertEquals(Arrays.asList("LIBGL_ALWAYS_SOFTWARE=1", "GALLIUM_DRIVER=llvmpipe",
            "MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"), result.ranked.get(0).env);
    }

    @Test public void anUnknownGpuWithoutVulkanOffersVirglThenSoftware() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "somevendor";

        assertEquals(Arrays.asList("virgl", "software"), order(X11GpuProbe.evaluate(in)));
        assertEquals("somevendor", X11GpuProbe.evaluate(in).gpu);
    }

    @Test public void theJsonCarriesEveryProfileAndTheEnvText() throws Exception {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.kgsl = true;
        in.vulkanVendor = "adreno";
        org.json.JSONObject json = X11GpuProbe.evaluate(in).toJson();

        assertEquals("turnip-zink", json.getString("recommended"));
        assertEquals(4, json.getJSONArray("profiles").length());
        assertTrue(json.getString("env_text").startsWith("# "));
    }
}
