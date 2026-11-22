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

    /** Set when a directory or classifying file could not be read, so root may know more. */
    private boolean needsPrivilegedDiscovery;
    /** Set when the app could not list the GPU's directories, so root might know a GPU it cannot. */
    private boolean gpuBlind;

    private HardwareNodes() {}

    /** Walks the tree once, with the app's own reads only. */
    public static HardwareNodes discover(Fs fs) {
        return discover(fs, null);
    }

    /**
     * Walks the tree once. With {@code listing}, the answer of {@link #privilegedDiscoveryCommand()},
     * what the app could not list or read is filled in from it; those nodes are then read through
     * the per-poll batch like any other node the app cannot read.
     */
    public static HardwareNodes discover(Fs direct, Listing listing) {
        Fs fs = listing == null ? direct : new OverlayFs(direct, listing);
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
        if (listing == null) {
            boolean thermalBlind = isBlank(direct.list(THERMAL)) || n.unclassifiedZone;
            boolean cpufreqBlind = isBlank(direct.list(CPUFREQ)) || n.unclassifiedPolicy;
            boolean gpuBlind = isBlank(direct.list(DEVFREQ)) && (n.gpuBusy == null || n.gpuCur == null);
            n.gpuBlind = gpuBlind;
            n.needsPrivilegedDiscovery = thermalBlind || cpufreqBlind || gpuBlind;
        }
        return n;
    }

    private static boolean isBlank(List<String> names) {
        return names == null || names.isEmpty();
    }

    private boolean unclassifiedZone;
    private boolean unclassifiedPolicy;

    /**
     * Whether the GPU load reads here, needs a backend to, or has no node at all (even for root,
     * once the privileged discovery has run).
     */
    @androidx.annotation.NonNull
    public StatusStatNeedsPolicy.Access gpuAccess() {
        if (gpuBusy != null) {
            return gpuBusy.readable ? StatusStatNeedsPolicy.Access.DIRECT
                : StatusStatNeedsPolicy.Access.NEEDS_BACKEND;
        }
        return gpuBlind ? StatusStatNeedsPolicy.Access.NEEDS_BACKEND
            : StatusStatNeedsPolicy.Access.ABSENT;
    }

    /** True when one privileged discovery could still find nodes the app could not see. */
    public boolean needsPrivilegedDiscovery() {
        return needsPrivilegedDiscovery;
    }

    static final String M_ZONES = "@@HWD_Z";
    static final String M_DEVFREQ = "@@HWD_D";
    static final String M_POLICIES = "@@HWD_P";
    static final String M_EXISTS = "@@HWD_E";

    /**
     * One shell fragment that lists what the app could not: each thermal zone's type as
     * {@code zonepath=type}, the devfreq names, each cpufreq policy's related_cpus as
     * {@code policypath=cpus}, and which candidate node files exist.
     */
    public static String privilegedDiscoveryCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("echo ").append(M_ZONES).append("; for d in ").append(THERMAL)
            .append("thermal_zone*; do printf '%s=' \"$d\"; { cat \"$d/type\" 2>/dev/null; echo; }; done; ");
        sb.append("echo ").append(M_DEVFREQ).append("; ls ").append(DEVFREQ).append(" 2>/dev/null; ");
        sb.append("echo ").append(M_POLICIES).append("; for d in ").append(CPUFREQ)
            .append("policy*; do printf '%s=' \"$d\"; { cat \"$d/related_cpus\" 2>/dev/null; echo; }; done; ");
        sb.append("echo ").append(M_EXISTS).append("; for f in")
            .append(' ').append(KGSL).append("gpu_busy_percentage")
            .append(' ').append(KGSL).append("gpuclk")
            .append(' ').append(KGSL).append("max_gpuclk")
            .append(' ').append(KERNEL_GPU).append("gpu_busy")
            .append(' ').append(KERNEL_GPU).append("gpu_clock")
            .append(' ').append(KERNEL_GPU).append("gpu_max_clock")
            .append(' ').append(DEVFREQ).append("*/load")
            .append(' ').append(DEVFREQ).append("*/cur_freq")
            .append(' ').append(DEVFREQ).append("*/max_freq")
            .append(' ').append(THERMAL).append("thermal_zone*/temp")
            .append(' ').append(CPUFREQ).append("policy*/scaling_cur_freq")
            .append(' ').append(CPUFREQ).append("policy*/cpuinfo_max_freq")
            .append("; do [ -e \"$f\" ] && echo \"$f\"; done; ");
        return sb.toString();
    }

    /** What {@link #privilegedDiscoveryCommand()} printed. */
    public static final class Listing {
        final Map<String, String> zoneTypes = new LinkedHashMap<>();
        final List<String> devfreq = new ArrayList<>();
        final Map<String, String> policyCpus = new LinkedHashMap<>();
        final Set<String> existing = new HashSet<>();
    }

    private static String lastSegment(String path) {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return p.substring(p.lastIndexOf('/') + 1);
    }

    /** Parses the discovery output; unknown sections, blanks and unmatched globs are ignored. */
    public static Listing parseDiscoveryOutput(String output) {
        Listing l = new Listing();
        if (output == null) return l;
        String section = "";
        for (String raw : output.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("@@")) { section = line; continue; }
            if (line.isEmpty() || line.indexOf('*') >= 0) continue;
            switch (section) {
                case M_ZONES:
                case M_POLICIES: {
                    int eq = line.indexOf('=');
                    if (eq <= 0 || eq == line.length() - 1) break;
                    String name = lastSegment(line.substring(0, eq));
                    String value = line.substring(eq + 1).trim();
                    (section.equals(M_ZONES) ? l.zoneTypes : l.policyCpus).put(name, value);
                    break;
                }
                case M_DEVFREQ: l.devfreq.add(line); break;
                case M_EXISTS: l.existing.add(line); break;
                default: break;
            }
        }
        return l;
    }

    /** The app's reads, with what root listed filling the gaps. */
    private static final class OverlayFs implements Fs {
        private final Fs direct;
        private final Listing listing;

        OverlayFs(Fs direct, Listing listing) {
            this.direct = direct;
            this.listing = listing;
        }

        @Override public List<String> list(String dir) {
            List<String> r = direct.list(dir);
            if (!isBlank(r)) return r;
            if (dir.equals(THERMAL)) return new ArrayList<>(listing.zoneTypes.keySet());
            if (dir.equals(DEVFREQ)) return listing.devfreq;
            if (dir.equals(CPUFREQ)) return new ArrayList<>(listing.policyCpus.keySet());
            return r;
        }

        @Override public String read(String path) {
            String v = direct.read(path);
            if (v != null) return v;
            if (path.startsWith(THERMAL) && path.endsWith("/type")) {
                return listing.zoneTypes.get(lastSegment(path.substring(0, path.length() - 5)));
            }
            if (path.startsWith(CPUFREQ) && path.endsWith("/related_cpus")) {
                return listing.policyCpus.get(lastSegment(path.substring(0, path.length() - 13)));
            }
            return null;
        }

        @Override public boolean exists(String path) {
            return direct.exists(path) || listing.existing.contains(path);
        }
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
            if (type == null) { unclassifiedZone = true; continue; }   // not classifiable, not used
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
            if (cpus == null) { unclassifiedPolicy = true; continue; }
            if (cur == null) continue;
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
