package com.termux.app.statusbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class HardwareNodesTest {

    /** A fake sysfs: files with contents, plus files that exist but refuse the read. */
    private static final class FakeFs implements HardwareNodes.Fs {
        final Map<String, String> files = new HashMap<>();
        final Set<String> denied = new HashSet<>();
        final Set<String> deniedDirs = new HashSet<>();

        FakeFs deniedDir(String dir) {
            deniedDirs.add(dir);
            return this;
        }

        FakeFs file(String path, String value) {
            files.put(path, value);
            return this;
        }

        FakeFs denied(String path, String value) {
            files.put(path, value);
            denied.add(path);
            return this;
        }

        @Override public List<String> list(String dir) {
            if (deniedDirs.contains(dir)) return null;
            String prefix = dir.endsWith("/") ? dir : dir + "/";
            Set<String> names = new TreeSet<>();
            for (String path : files.keySet()) {
                if (path.startsWith(prefix)) names.add(path.substring(prefix.length()).split("/")[0]);
            }
            return names.isEmpty() ? null : new ArrayList<>(names);
        }

        @Override public String read(String path) {
            if (denied.contains(path)) return null;
            for (String dir : deniedDirs) {
                if (path.startsWith(dir)) return null;
            }
            return files.get(path);
        }

        @Override public boolean exists(String path) {
            for (String dir : deniedDirs) {
                if (path.startsWith(dir)) return false;   // SELinux hides the stat as well
            }
            return files.containsKey(path);
        }
    }

    private static final String Z = "/sys/class/thermal/thermal_zone";
    private static final String P = "/sys/devices/system/cpu/cpufreq/";

    private static FakeFs adreno() {
        return new FakeFs()
            .file("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "34 %\n")
            .file("/sys/class/kgsl/kgsl-3d0/gpuclk", "585000000\n")
            .file("/sys/class/kgsl/kgsl-3d0/max_gpuclk", "900000000\n")
            .file(Z + "0/type", "cpu-0-0-usr\n").file(Z + "0/temp", "47000\n")
            .file(Z + "1/type", "cpu-1-0-usr\n").file(Z + "1/temp", "52000\n")
            .file(Z + "2/type", "gpu-usr\n").file(Z + "2/temp", "50000\n")
            .file(Z + "3/type", "soc\n").file(Z + "3/temp", "44000\n")
            .file(Z + "4/type", "battery\n").file(Z + "4/temp", "30000\n")
            .file(P + "policy0/related_cpus", "0 1 2 3\n")
            .file(P + "policy0/scaling_cur_freq", "1800000\n")
            .file(P + "policy0/cpuinfo_max_freq", "2000000\n")
            .file(P + "policy4/related_cpus", "4 5 6\n")
            .file(P + "policy4/scaling_cur_freq", "2400000\n")
            .file(P + "policy4/cpuinfo_max_freq", "2800000\n");
    }

    @Test
    public void anAdrenoLayoutIsReadInFull() {
        FakeFs fs = adreno();
        HardwareNodes nodes = HardwareNodes.discover(fs);
        assertFalse(nodes.needsPrivilege());
        assertEquals("", nodes.privilegedReadCommand());
        HardwareStats s = nodes.collect(fs, null);
        assertEquals(34, s.gpuBusyPercent);
        assertEquals(585, s.gpuCurMhz);
        assertEquals(900, s.gpuMaxMhz);
        assertEquals(52f, s.cpuTempC, 0.001f);   // max over the class
        assertEquals(50f, s.gpuTempC, 0.001f);
        assertEquals(44f, s.socTempC, 0.001f);
        assertEquals(2, s.clusters.size());
        assertEquals(0, s.clusters.get(0).firstCpu);
        assertEquals(3, s.clusters.get(0).lastCpu);
        assertEquals(1800, s.clusters.get(0).curMhz);
        assertEquals(2800, s.clusters.get(1).maxMhz);
    }

    @Test
    public void aMaliDevfreqLayoutUsesLoadAndHertzClocks() {
        String d = "/sys/class/devfreq/13000000.mali/";
        FakeFs fs = new FakeFs()
            .file("/sys/class/devfreq/soc-ddr/cur_freq", "1")
            .file(d + "load", "34@585000000Hz\n")
            .file(d + "cur_freq", "585000000\n")
            .file(d + "max_freq", "902000000\n");
        HardwareNodes nodes = HardwareNodes.discover(fs);
        HardwareStats s = nodes.collect(fs, null);
        assertEquals(34, s.gpuBusyPercent);
        assertEquals(585, s.gpuCurMhz);
        assertEquals(902, s.gpuMaxMhz);
        assertTrue(Float.isNaN(s.gpuTempC));
        assertTrue(s.clusters.isEmpty());
    }

    @Test
    public void aKernelGpuLayoutUsesMegahertzClocks() {
        FakeFs fs = new FakeFs()
            .file("/sys/kernel/gpu/gpu_busy", "21\n")
            .file("/sys/kernel/gpu/gpu_clock", "400\n")
            .file("/sys/kernel/gpu/gpu_max_clock", "800\n");
        HardwareStats s = HardwareNodes.discover(fs).collect(fs, null);
        assertEquals(21, s.gpuBusyPercent);
        assertEquals(400, s.gpuCurMhz);
        assertEquals(800, s.gpuMaxMhz);
    }

    @Test
    public void aPhoneWithNothingIsAllUnknown() {
        FakeFs fs = new FakeFs();
        HardwareNodes nodes = HardwareNodes.discover(fs);
        assertTrue(nodes.isEmpty());
        assertFalse(nodes.needsPrivilege());
        HardwareStats s = nodes.collect(fs, null);
        assertEquals(HardwareStats.UNKNOWN, s.gpuBusyPercent);
        assertEquals(HardwareStats.UNKNOWN, s.gpuCurMhz);
        assertEquals(HardwareStats.UNKNOWN, s.gpuMaxMhz);
        assertTrue(Float.isNaN(s.cpuTempC) && Float.isNaN(s.gpuTempC) && Float.isNaN(s.socTempC));
        assertTrue(s.clusters.isEmpty());
    }

    @Test
    public void atMostSixteenZonesAreKeptPerClass() {
        FakeFs fs = new FakeFs();
        for (int i = 0; i < 20; i++) {
            String zone = String.format("%s%02d", Z, i);
            fs.file(zone + "/type", "cpu-" + i).file(zone + "/temp", String.valueOf(20000 + i * 1000));
        }
        HardwareStats s = HardwareNodes.discover(fs).collect(fs, null);
        // Zones 00..15 are kept, so the hottest is zone 15 (35 C), not zone 19.
        assertEquals(35f, s.cpuTempC, 0.001f);
    }

    @Test
    public void unreadableNodesNeedPrivilegeAndComeFromTheBatch() {
        FakeFs fs = adreno()
            .denied("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "34 %\n")
            .denied(Z + "2/temp", "50000\n");
        HardwareNodes nodes = HardwareNodes.discover(fs);
        assertTrue(nodes.needsPrivilege());
        assertEquals(Arrays.asList("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", Z + "2/temp"),
            nodes.privilegedPaths());

        // Without the batch, only the readable nodes are known.
        HardwareStats direct = nodes.collect(fs, null);
        assertEquals(HardwareStats.UNKNOWN, direct.gpuBusyPercent);
        assertTrue(Float.isNaN(direct.gpuTempC));
        assertEquals(585, direct.gpuCurMhz);

        Map<String, String> priv = new HashMap<>();
        priv.put("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "61 %");
        priv.put(Z + "2/temp", "55000");
        HardwareStats full = nodes.collect(fs, priv);
        assertEquals(61, full.gpuBusyPercent);
        assertEquals(55f, full.gpuTempC, 0.001f);
    }

    @Test
    public void theBatchCommandQuotesPathsAndNamesTheMarker() {
        FakeFs fs = new FakeFs().denied("/sys/kernel/gpu/gpu_busy", "5");
        String command = HardwareNodes.discover(fs).privilegedReadCommand();
        assertTrue(command.startsWith("echo @@HW; for f in '/sys/kernel/gpu/gpu_busy'; do "));
        assertTrue(command.contains("cat \"$f\""));
        assertEquals("'a'\\''b'", HardwareNodes.quote("a'b"));
    }

    @Test
    public void theBatchOutputParsesWithEmptyAndMissingValues() {
        Map<String, String> m = HardwareNodes.parsePrivilegedLines(Arrays.asList(
            "/a/b=34 %", "/c/d=", "", "noequals", "/e/f=48000 "));
        assertEquals("34 %", m.get("/a/b"));
        assertNull(m.get("/c/d"));       // empty value: stays unknown
        assertNull(m.get("/missing"));   // never printed
        assertEquals("48000", m.get("/e/f"));
        assertEquals(2, m.size());
    }

    /** The Adreno phone again, with every sysfs directory the app might need refused. */
    private static FakeFs rootOnlyAdreno() {
        FakeFs fs = adreno();
        fs.deniedDir("/sys/class/kgsl/").deniedDir("/sys/class/thermal/")
            .deniedDir("/sys/devices/system/cpu/cpufreq/").deniedDir("/sys/class/devfreq/");
        return fs;
    }

    private static final String ROOT_OUTPUT = String.join("\n",
        "@@HWD_Z",
        "/sys/class/thermal/thermal_zone0=cpu-0-0-usr",
        "/sys/class/thermal/thermal_zone2=gpu-usr",
        "/sys/class/thermal/thermal_zone3=soc",
        "/sys/class/thermal/thermal_zone4=battery",
        "@@HWD_D",
        "3d00000.qcom,kgsl-3d0",
        "@@HWD_P",
        "/sys/devices/system/cpu/cpufreq/policy0=0 1 2 3",
        "@@HWD_E",
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone2/temp",
        "/sys/class/thermal/thermal_zone3/temp",
        "/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq",
        "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq",
        "");

    @Test
    public void anUnreadableDirectoryAsksForPrivilegedDiscoveryAndAReadableOneDoesNot() {
        assertFalse(HardwareNodes.discover(adreno()).needsPrivilegedDiscovery());
        HardwareNodes blind = HardwareNodes.discover(rootOnlyAdreno());
        assertTrue(blind.isEmpty());
        assertTrue(blind.needsPrivilegedDiscovery());
        // A zone whose type alone is refused is also a reason to ask.
        FakeFs oneZone = adreno().denied(Z + "2/type", "gpu-usr");
        assertTrue(HardwareNodes.discover(oneZone).needsPrivilegedDiscovery());
    }

    @Test
    public void theDiscoveryCommandNamesEverySectionItParses() {
        String command = HardwareNodes.privilegedDiscoveryCommand();
        for (String marker : new String[] {"@@HWD_Z", "@@HWD_D", "@@HWD_P", "@@HWD_E"}) {
            assertTrue(marker, command.contains("echo " + marker + ";"));
        }
        assertTrue(command.contains("thermal_zone*/temp"));
        assertTrue(command.contains("$d/related_cpus"));
        HardwareNodes.Listing l = HardwareNodes.parseDiscoveryOutput(ROOT_OUTPUT
            + "/sys/class/thermal/thermal_zone*=\n");
        assertEquals("gpu-usr", l.zoneTypes.get("thermal_zone2"));
        assertEquals("0 1 2 3", l.policyCpus.get("policy0"));
        assertEquals(Arrays.asList("3d00000.qcom,kgsl-3d0"), l.devfreq);
        assertTrue(l.existing.contains("/sys/class/kgsl/kgsl-3d0/gpuclk"));
        assertEquals(4, l.zoneTypes.size());
    }

    @Test
    public void aRootOnlyAdrenoIsFoundThroughPrivilegedDiscoveryAndReadByTheBatch() {
        FakeFs fs = rootOnlyAdreno();
        HardwareNodes nodes = HardwareNodes.discover(fs, HardwareNodes.parseDiscoveryOutput(ROOT_OUTPUT));
        assertFalse(nodes.needsPrivilegedDiscovery());
        assertTrue(nodes.needsPrivilege());
        assertTrue(nodes.privilegedPaths().contains("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"));
        assertTrue(nodes.privilegedPaths().contains(Z + "2/temp"));
        assertTrue(nodes.privilegedPaths().contains(P + "policy0/scaling_cur_freq"));
        assertFalse(nodes.privilegedPaths().contains(Z + "4/temp"));   // battery is not a class

        Map<String, String> priv = new HashMap<>();
        priv.put("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "40 %");
        priv.put("/sys/class/kgsl/kgsl-3d0/gpuclk", "585000000");
        priv.put(Z + "2/temp", "51000");
        priv.put(P + "policy0/scaling_cur_freq", "1800000");
        HardwareStats s = nodes.collect(fs, priv);
        assertEquals(40, s.gpuBusyPercent);
        assertEquals(585, s.gpuCurMhz);
        assertEquals(51f, s.gpuTempC, 0.001f);
        assertTrue(Float.isNaN(s.cpuTempC));   // its temp was not in the batch answer
        assertEquals(1, s.clusters.size());
        assertEquals(1800, s.clusters.get(0).curMhz);
    }

    @Test
    public void aPolicyWhoseCurrentFrequencyAloneIsUnreadableIsKeptAndBatched() {
        FakeFs fs = adreno().denied(P + "policy4/scaling_cur_freq", "2400000\n");
        HardwareNodes nodes = HardwareNodes.discover(fs);
        assertTrue(nodes.privilegedPaths().contains(P + "policy4/scaling_cur_freq"));
        Map<String, String> priv = new HashMap<>();
        priv.put(P + "policy4/scaling_cur_freq", "2400000");
        HardwareStats s = nodes.collect(fs, priv);
        assertEquals(2, s.clusters.size());
        assertEquals(2400, s.clusters.get(1).curMhz);
        assertEquals(2800, s.clusters.get(1).maxMhz);
    }
}
