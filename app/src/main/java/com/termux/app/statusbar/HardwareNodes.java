package com.termux.app.statusbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The sysfs nodes that carry GPU load, clocks and temperatures on this phone, found once and kept.
 * Discovery asks a small {@link Fs} so plain JUnit can run it against a fake tree. A node that is
 * absent is unsupported and stays unknown; a node that exists but the app cannot read is "needs
 * privilege" and is read in one batch through the privileged backend instead. Nothing is guessed.
 */
public final class HardwareNodes {

    /** The filesystem the discovery and the reads go through. */
    public interface Fs {
        /** Names inside {@code dir}, or null/empty when it is missing or unreadable. */
        List<String> list(String dir);

        /** The file's text, or null when it is missing or the app cannot read it. */
        String read(String path);

        boolean exists(String path);
    }

    /** Section marker the batch prints before its {@code path=value} lines. */
    public static final String MARKER = "@@HW";

    static final int MAX_ZONES_PER_CLASS = 16;

    private static final String KGSL = "/sys/class/kgsl/kgsl-3d0/";
    private static final String KERNEL_GPU = "/sys/kernel/gpu/";
    private static final String DEVFREQ = "/sys/class/devfreq/";
    private static final String THERMAL = "/sys/class/thermal/";
    private static final String CPUFREQ = "/sys/devices/system/cpu/cpufreq/";

    /** A file, and whether the app could read it directly when it was found. */
    private static final class Node {
        final String path;
        final boolean readable;

        Node(String path, boolean readable) {
            this.path = path;
            this.readable = readable;
        }
    }

    private static final class Freq {
        final Node node;
        final HardwareStats.Unit unit;

        Freq(Node node, HardwareStats.Unit unit) {
            this.node = node;
            this.unit = unit;
        }
    }

    private static final class ClusterNodes {
        final int firstCpu;
        final int lastCpu;
        final Node cur;
        final Node max;

        ClusterNodes(int firstCpu, int lastCpu, Node cur, Node max) {
            this.firstCpu = firstCpu;
            this.lastCpu = lastCpu;
            this.cur = cur;
            this.max = max;
        }
    }

    private Node gpuBusy;
    private Freq gpuCur;
    private Freq gpuMax;
    private final List<Node> cpuTemp = new ArrayList<>();
    private final List<Node> gpuTemp = new ArrayList<>();
    private final List<Node> socTemp = new ArrayList<>();
    private final List<ClusterNodes> clusters = new ArrayList<>();

    private HardwareNodes() {}

    /** Walks the tree once. */
    public static HardwareNodes discover(Fs fs) {
        HardwareNodes n = new HardwareNodes();
        String devfreqGpu = n.findDevfreqGpu(fs);

        // Load: Adreno, then the vendor node, then a devfreq GPU's load.
        n.gpuBusy = node(fs, KGSL + "gpu_busy_percentage");
        if (n.gpuBusy == null) n.gpuBusy = node(fs, KERNEL_GPU + "gpu_busy");
        if (n.gpuBusy == null && devfreqGpu != null) n.gpuBusy = node(fs, devfreqGpu + "load");

        // Clock: kgsl (Hz), devfreq (Hz), /sys/kernel/gpu (MHz).
        Node cur = node(fs, KGSL + "gpuclk");
        if (cur != null) {
            n.gpuCur = new Freq(cur, HardwareStats.Unit.HZ);
            n.gpuMax = freq(node(fs, KGSL + "max_gpuclk"), HardwareStats.Unit.HZ);
        } else if (devfreqGpu != null && (cur = node(fs, devfreqGpu + "cur_freq")) != null) {
            n.gpuCur = new Freq(cur, HardwareStats.Unit.HZ);
            n.gpuMax = freq(node(fs, devfreqGpu + "max_freq"), HardwareStats.Unit.HZ);
        } else if ((cur = node(fs, KERNEL_GPU + "gpu_clock")) != null) {
            n.gpuCur = new Freq(cur, HardwareStats.Unit.MHZ);
            n.gpuMax = freq(node(fs, KERNEL_GPU + "gpu_max_clock"), HardwareStats.Unit.MHZ);
        }

        n.findThermalZones(fs);
        n.findClusters(fs);
        return n;
    }

    private static Freq freq(Node node, HardwareStats.Unit unit) {
        return node == null ? null : new Freq(node, unit);
    }

    /** The node for {@code path}, or null when it does not exist. */
    private static Node node(Fs fs, String path) {
        if (!fs.exists(path)) return null;
        return new Node(path, fs.read(path) != null);
    }

    /** A devfreq directory (with trailing slash) of a GPU, or null. */
    private String findDevfreqGpu(Fs fs) {
        List<String> names = fs.list(DEVFREQ);
        if (names == null) return null;
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        for (String name : sorted) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("gpu") || lower.contains("kgsl") || lower.contains("mali")) {
                return DEVFREQ + name + "/";
            }
        }
        return null;
    }

    private void findThermalZones(Fs fs) {
        List<String> names = fs.list(THERMAL);
        if (names == null) return;
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        for (String name : sorted) {
            if (!name.startsWith("thermal_zone")) continue;
            String type = fs.read(THERMAL + name + "/type");
            if (type == null) continue;   // cannot be classified, so it is not used
            String lower = type.trim().toLowerCase(Locale.ROOT);
            List<Node> into = lower.contains("gpu") ? gpuTemp
                : lower.contains("cpu") ? cpuTemp
                : lower.contains("soc") ? socTemp : null;
            if (into == null || into.size() >= MAX_ZONES_PER_CLASS) continue;
            Node temp = node(fs, THERMAL + name + "/temp");
            if (temp != null) into.add(temp);
        }
    }

    private void findClusters(Fs fs) {
        List<String> names = fs.list(CPUFREQ);
        if (names == null) return;
        for (String name : names) {
            if (!name.startsWith("policy")) continue;
            String dir = CPUFREQ + name + "/";
            int[] cpus = HardwareStats.parseRelatedCpus(fs.read(dir + "related_cpus"));
            Node cur = node(fs, dir + "scaling_cur_freq");
            if (cpus == null || cur == null) continue;
            clusters.add(new ClusterNodes(cpus[0], cpus[1], cur, node(fs, dir + "cpuinfo_max_freq")));
        }
        Collections.sort(clusters, (a, b) -> Integer.compare(a.firstCpu, b.firstCpu));
    }

    /** True when some node exists that the app cannot read itself. */
    public boolean needsPrivilege() {
        return !privilegedPaths().isEmpty();
    }

    /** True when the phone offers nothing at all. */
    public boolean isEmpty() {
        return gpuBusy == null && gpuCur == null && cpuTemp.isEmpty() && gpuTemp.isEmpty()
            && socTemp.isEmpty() && clusters.isEmpty();
    }

    /** Paths of the nodes that exist but are unreadable here, without repeats. */
    List<String> privilegedPaths() {
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        List<Node> all = new ArrayList<>();
        all.add(gpuBusy);
        all.add(gpuCur == null ? null : gpuCur.node);
        all.add(gpuMax == null ? null : gpuMax.node);
        all.addAll(cpuTemp);
        all.addAll(gpuTemp);
        all.addAll(socTemp);
        for (ClusterNodes c : clusters) {
            all.add(c.cur);
            all.add(c.max);
        }
        for (Node node : all) {
            if (node != null && !node.readable && seen.add(node.path)) out.add(node.path);
        }
        return out;
    }

    /**
     * One shell fragment that prints {@link #MARKER} and then {@code path=value} for each
     * unreadable node, or an empty string when there is none. Paths are single-quoted.
     */
    public String privilegedReadCommand() {
        List<String> paths = privilegedPaths();
        if (paths.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("echo ").append(MARKER).append("; for f in");
        for (String path : paths) sb.append(' ').append(quote(path));
        // The explicit echo ends the line even for a file with no trailing newline.
        sb.append("; do printf '%s=' \"$f\"; { cat \"$f\" 2>/dev/null; echo; }; done; ");
        return sb.toString();
    }

    static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    /**
     * The {@code path=value} lines the batch printed, as a map. A line without a value or without
     * an {@code =} is left out, so a node that failed to read stays unknown.
     */
    public static Map<String, String> parsePrivilegedLines(List<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : lines) {
            int eq = raw.indexOf('=');
            if (eq <= 0) continue;
            String value = raw.substring(eq + 1).trim();
            if (value.isEmpty()) continue;
            out.put(raw.substring(0, eq).trim(), value);
        }
        return out;
    }

    private static String value(Fs fs, Map<String, String> privileged, Node node) {
        if (node == null) return null;
        if (node.readable) return fs.read(node.path);
        return privileged == null ? null : privileged.get(node.path);
    }

    private static float hottest(Fs fs, Map<String, String> privileged, List<Node> zones) {
        float best = Float.NaN;
        for (Node zone : zones) {
            float t = HardwareStats.parseTempC(value(fs, privileged, zone));
            if (!Float.isNaN(t) && (Float.isNaN(best) || t > best)) best = t;
        }
        return best;
    }

    private static int mhz(Fs fs, Map<String, String> privileged, Freq freq) {
        return freq == null ? HardwareStats.UNKNOWN
            : HardwareStats.parseMhz(value(fs, privileged, freq.node), freq.unit);
    }

    /**
     * One reading: directly readable nodes through {@code fs}, the others from {@code privileged}
     * (the parsed batch output, null when there was none).
     */
    public HardwareStats collect(Fs fs, Map<String, String> privileged) {
        List<HardwareStats.Cluster> out = new ArrayList<>(clusters.size());
        for (ClusterNodes c : clusters) {
            int cur = HardwareStats.parseMhz(value(fs, privileged, c.cur), HardwareStats.Unit.KHZ);
            if (cur == HardwareStats.UNKNOWN) continue;
            int max = HardwareStats.parseMhz(value(fs, privileged, c.max), HardwareStats.Unit.KHZ);
            out.add(new HardwareStats.Cluster(c.firstCpu, c.lastCpu, cur, max));
        }
        return new HardwareStats(
            HardwareStats.parseBusyPercent(value(fs, privileged, gpuBusy)),
            mhz(fs, privileged, gpuCur), mhz(fs, privileged, gpuMax),
            hottest(fs, privileged, cpuTemp), hottest(fs, privileged, gpuTemp),
            hottest(fs, privileged, socTemp), out);
    }
}
