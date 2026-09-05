package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The script a drawer tap runs, and which GPU profile it gets. */
public class X11LinuxAppRunnerTest {

    private static LinuxAppCatalog.LinuxApp app(String exec) {
        return new LinuxAppCatalog.LinuxApp("firefox", "Firefox", exec, "firefox", "");
    }

    @Test public void theScriptSetsTheDisplayTheEnvironmentAndRunsTheCommand() {
        String script = X11LinuxAppRunner.script(app("firefox --new-window"), ":1",
            Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform"));

        assertEquals("export DISPLAY=:1\nexport MESA_LOADER_DRIVER_OVERRIDE=zink\n"
            + "export TU_DEBUG=noconform\ncd \"$HOME\"\nexec firefox --new-window\n", script);
    }

    @Test public void onlyAnInstalledGpuProfileIsUsedAndSoftwareMeansNothingExtra() {
        X11GpuProbe.Inputs adreno = new X11GpuProbe.Inputs();
        adreno.kgsl = true;
        adreno.vulkanVendor = "adreno";
        assertTrue("nothing installed: no exports that would break GL",
            X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(adreno)).isEmpty());

        adreno.mesaDri = true;
        adreno.icdFiles = Collections.singletonList("freedreno_icd.aarch64.json");
        assertEquals(Arrays.asList("MESA_LOADER_DRIVER_OVERRIDE=zink", "TU_DEBUG=noconform"),
            X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(adreno)));

        X11GpuProbe.Inputs emulator = new X11GpuProbe.Inputs();
        emulator.eglVendor = "emulation";
        emulator.mesaDri = true;
        assertTrue(X11LinuxAppRunner.installedEnv(X11GpuProbe.evaluate(emulator)).isEmpty());
    }
}
