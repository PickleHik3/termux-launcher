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
        assertTrue(result.toEnv(), result.toEnv().startsWith("# Adreno 730: turnip-zink"));
        assertTrue(result.toEnv().contains("export MESA_LOADER_DRIVER_OVERRIDE=zink\n"));
    }

    /** virgl needs a helper alive in Termux, and the exports have to say so. */
    @Test public void theExportsNameTheHelperWhenTheProfileNeedsOne() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.glRenderer = "Mali-G78";

        String env = X11GpuProbe.evaluate(in).toEnv();

        assertTrue(env, env.contains("# keep running in Termux: virgl_test_server_android --angle-gl &"));
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
        assertTrue(X11GpuProbe.evaluate(in).toEnv().contains("pkg install mesa mesa-vulkan-icd-freedreno\n"));
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
            "VK_ICD_FILENAMES=" + com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH
                + "/share/vulkan/icd.d/freedreno_icd.aarch64.json"),
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
        assertTrue(result.toEnv().contains(": software"));
        assertEquals(Arrays.asList("LIBGL_ALWAYS_SOFTWARE=1", "GALLIUM_DRIVER=llvmpipe",
            "MESA_LOADER_DRIVER_OVERRIDE=llvmpipe"), result.ranked.get(0).env);
    }

    @Test public void anUnknownGpuWithoutVulkanOffersVirglThenSoftware() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "somevendor";

        assertEquals(Arrays.asList("virgl", "software"), order(X11GpuProbe.evaluate(in)));
        assertEquals("somevendor", X11GpuProbe.evaluate(in).gpu);
    }

    /** Mesa picks Zink on any Vulkan driver unless told not to, and then ignores virpipe. */
    @Test public void virglKeepsMesaOffZink() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "somevendor";
        assertTrue(X11GpuProbe.evaluate(in).ranked.get(0).env.contains("LIBGL_KOPPER_DISABLE=1"));
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

    @Test public void aKnownFreezeGpuRanksSoftwareFirstAndWarnsOnVirgl() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.vulkanVendor = "mali";
        in.glRenderer = "Mali-G52 MC2";
        in.virglServer = true;
        in.angle = true;
        in.mesaDri = true;

        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);
        assertEquals("software", result.ranked.get(0).profile.id);
        assertTrue(result.ranked.get(0).installed);
        assertNull("software is what the row recommends", result.recommended());
        boolean warned = false;
        for (X11GpuProbe.Recommendation r : result.ranked) {
            if (r.profile == X11GpuProbe.Profile.VIRGL_ANGLE || r.profile == X11GpuProbe.Profile.VIRGL) {
                assertTrue(r.reason, r.reason.contains("frozen the whole phone"));
                warned = true;
            } else if (r.profile == X11GpuProbe.Profile.VULKAN_WRAPPER) {
                assertTrue("no GPU profile goes unwarned there", r.reason.contains("untested on this GPU"));
            }
        }
        assertTrue(warned);
        assertTrue(X11GpuProbe.knownFreeze(in));
        in.glRenderer = "Mali-G78";
        assertFalse(X11GpuProbe.knownFreeze(in));
    }

    /** No GL context, so no renderer string: the SoC name still tells the phone. */
    @Test public void aKnownFreezeSocIsCaughtWithoutARendererString() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.platform = "mt6768 mt6768";
        in.virglServer = true;
        in.angle = true;
        in.mesaDri = true;

        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);

        assertTrue(result.knownFreeze);
        assertEquals("software", result.ranked.get(0).profile.id);
        in.platform = "mt6789";
        assertFalse(X11GpuProbe.knownFreeze(in));
    }

    @Test public void theWrapperIsSkippedOnlyWhenTheVulkanDriverIsKnownOlderThan13() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "adreno";
        in.vulkanVendor = "adreno";
        in.kgsl = true;
        in.vulkanApiVersion = 0x401000;
        X11GpuProbe.Result old = X11GpuProbe.evaluate(in);
        assertNotNull(old.find(X11GpuProbe.Profile.VULKAN_WRAPPER));
        assertTrue(old.toTable().contains("\nSKIP['vulkan-wrapper']="));
        assertFalse(old.toTable().contains("SKIP['turnip-zink']"));
        assertFalse(old.toTable().contains("SKIP['virgl']"));

        in.vulkanApiVersion = 0x403000;
        assertFalse(X11GpuProbe.evaluate(in).toTable().contains("SKIP["));
        in.vulkanApiVersion = 0;
        assertFalse(X11GpuProbe.evaluate(in).toTable().contains("SKIP["));
    }

    /** The benchmark script's table is this class's ranking, quoted for bash. */
    @Test public void theTableListsTheRankingAndSkipsVirglOnAKnownFreezeGpu() {
        X11GpuProbe.Inputs in = new X11GpuProbe.Inputs();
        in.eglVendor = "mali";
        in.glRenderer = "Mali-G52 MC2";
        X11GpuProbe.Result result = X11GpuProbe.evaluate(in);

        String table = result.toTable();

        assertTrue(table, table.contains("\nGPU_NAME='Mali-G52 MC2'\n"));
        assertTrue(table, table.contains("\nCANDIDATES=('virgl-angle' 'virgl')\n"));
        assertTrue(table, table.contains("\nPKGS['software']='mesa'\n"));
        assertTrue(table, table.contains("\nHELPER['virgl-angle']='virgl_test_server_android --angle-gl'\n"));
        assertTrue(table, table.contains("\nSKIP['virgl']="));
        assertTrue(table, table.contains("\nSKIP['virgl-angle']="));
        in.vulkanVendor = "mali";
        assertTrue("every GPU profile is skipped there",
            X11GpuProbe.evaluate(in).toTable().contains("\nSKIP['vulkan-wrapper']="));
        in.vulkanVendor = "";
        assertTrue("an apostrophe in a reason survives the quoting", table.contains("GPU'\\''s"));
        assertTrue(result.knownFreeze);

        in.glRenderer = "Mali-G78";
        assertFalse(X11GpuProbe.evaluate(in).toTable().contains("SKIP["));
    }

    @Test
    public void theWrapperIsInstalledThroughTheScriptNeverByName() {
        assertEquals("termux-x11-gpu-setup --install-wrapper && pkg install mesa",
            X11GpuProbe.installCommand(Arrays.asList("vulkan-wrapper-android", "mesa")));
        assertEquals("termux-x11-gpu-setup --install-wrapper",
            X11GpuProbe.installCommand(Arrays.asList("vulkan-wrapper-android")));
        assertEquals("pkg install virglrenderer-android mesa",
            X11GpuProbe.installCommand(Arrays.asList("virglrenderer-android", "mesa")));
    }
}
