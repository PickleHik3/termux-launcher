package com.termux.app.statusbar;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.privileged.PrivilegedBackendManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Samples CPU load, memory breakdown, and a short top-process list for the CPU/RAM status widgets
 * and their shared mini-btop card. Sampling is throttled and only runs while at least one consumer
 * is active (the compact widgets when visible, or the open card at a faster cadence).
 *
 * Memory always comes from {@link ActivityManager} (no privilege needed). CPU ticks, the detailed
 * {@code /proc/meminfo} breakdown, and the top-process list are read through the privileged backend
 * (Shizuku or su/rish) when available, with a best-effort direct-read fallback for the world-
 * readable files. Anything unavailable is simply left blank in the snapshot.
 */
public final class SystemStatsController {

    public static final class Proc {
        public final int pid;
        @NonNull public final String name;
        public final double cpu;   // percent
        public final long rssKb;
        public final boolean kernel;

        Proc(@NonNull String name, double cpu, long rssKb) {
            this(-1, name, cpu, rssKb, false);
        }

        Proc(int pid, @NonNull String name, double cpu, long rssKb, boolean kernel) {
            this.pid = pid;
            this.name = name;
            this.cpu = cpu;
            this.rssKb = rssKb;
            this.kernel = kernel;
        }
    }

    /** Immutable snapshot handed to the UI. Fields are -1 / empty when unknown. */
    public static final class Stats {
        public int cpuPercent = -1;
        @NonNull public int[] corePercent = new int[0];
        public int cores;
        public double load1 = -1;
        public long memTotalKb, memUsedKb, memAvailKb, memFreeKb, buffersKb, cachedKb;
        public long swapTotalKb, swapFreeKb;
        @NonNull public List<Proc> top = new ArrayList<>();
    }

    public interface Listener {
        void onStatsUpdated(@NonNull Stats stats);
    }

    private static final String M_STAT = "@@STAT";
    private static final String M_MEM = "@@MEM";
    private static final String M_LOAD = "@@LOAD";
    private static final String M_TOP = "@@TOP";
    private static final String M_PS = "@@PS";
    private static final int MAX_PROCESS_BUFFER = 32;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    @Nullable private final Listener mListener;

    private boolean mRunning;
    private long mIntervalMs = 4000L;
    private boolean mWantTop;
    private volatile boolean mInFlight;

    // Previous CPU tick totals per line ("cpu", "cpu0", ...) for delta-based utilisation.
    @Nullable private long[] mPrevTotal;
    @Nullable private long[] mPrevIdle;
    @NonNull private final Map<Integer, Double> mSmoothedProcessCpu = new HashMap<>();

    private final Stats mLatest = new Stats();

    public SystemStatsController(@NonNull Context context, @Nullable Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mLatest.cores = Runtime.getRuntime().availableProcessors();
    }

    @NonNull
    public Stats latest() {
        return mLatest;
    }

    /**
     * Start (or retune) sampling. {@code wantTop} enables the more expensive top-process read, used
     * only while the card is open.
     */
    public void start(long intervalMs, boolean wantTop) {
        mIntervalMs = Math.max(1000L, intervalMs);
        mWantTop = wantTop;
        if (mRunning) return;
        mRunning = true;
        mMainHandler.post(mTick);
    }

    public void stop() {
        mRunning = false;
        mMainHandler.removeCallbacks(mTick);
    }

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            sampleOnce();
            mMainHandler.postDelayed(this, mIntervalMs);
        }
    };

    private void sampleOnce() {
        // Memory: always available and cheap.
        readActivityManagerMemory();
        if (mInFlight) {
            publish();
            return;
        }
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        if (manager.isPrivilegedAvailable()) {
            mInFlight = true;
            final boolean wantTop = mWantTop;
            manager.executeCommand(buildCommand(wantTop)).whenComplete((output, error) -> {
                if (error == null && output != null) parsePrivileged(output, wantTop);
                mMainHandler.post(() -> {
                    mInFlight = false;
                    publish();
                });
            });
        } else {
            readDirectFallback();
            publish();
        }
    }

    private void publish() {
        if (mListener != null) mListener.onStatsUpdated(mLatest);
    }

    @NonNull
    private static String buildCommand(boolean wantTop) {
        StringBuilder sb = new StringBuilder();
        sb.append("echo ").append(M_STAT).append("; grep '^cpu' /proc/stat 2>/dev/null; ");
        sb.append("echo ").append(M_MEM).append("; cat /proc/meminfo 2>/dev/null; ");
        sb.append("echo ").append(M_LOAD).append("; cat /proc/loadavg 2>/dev/null; ");
        if (wantTop) {
            sb.append("echo ").append(M_PS)
                .append("; ps -A -o PID,NAME,RSS 2>/dev/null; ");
            // Android's toybox top takes its own scheduler sample and remains available on
            // hardened Android 16 builds where cross-process /proc/<pid>/stat is hidden.
            sb.append("echo ").append(M_TOP)
                .append("; top -b -n 1 -m 32 2>/dev/null; ");
        }
        return sb.toString();
    }

    private void parsePrivileged(@NonNull String output, boolean wantTop) {
        String section = "";
        List<String> statLines = new ArrayList<>();
        List<String> memLines = new ArrayList<>();
        List<String> topLines = new ArrayList<>();
        List<String> psLines = new ArrayList<>();
        String loadLine = null;
        for (String raw : output.split("\n")) {
            String line = raw.trim();
            if (line.equals(M_STAT)) { section = M_STAT; continue; }
            if (line.equals(M_MEM)) { section = M_MEM; continue; }
            if (line.equals(M_LOAD)) { section = M_LOAD; continue; }
            if (line.equals(M_TOP)) { section = M_TOP; continue; }
            if (line.equals(M_PS)) { section = M_PS; continue; }
            if (line.isEmpty()) continue;
            switch (section) {
                case M_STAT: statLines.add(line); break;
                case M_MEM: memLines.add(line); break;
                case M_LOAD: if (loadLine == null) loadLine = line; break;
                case M_TOP: topLines.add(raw); break;
                case M_PS: psLines.add(raw); break;
                default: break;
            }
        }
        parseCpu(statLines);
        parseMeminfo(memLines);
        parseLoad(loadLine);
        if (wantTop) {
            parseProcessRows(psLines, topLines);
        }
    }

    private void parseCpu(@NonNull List<String> lines) {
        if (lines.isEmpty()) return;
        long[] totals = new long[lines.size()];
        long[] idles = new long[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String[] f = lines.get(i).split("\\s+");
            // f[0] = "cpu"/"cpuN", then user nice system idle iowait irq softirq ...
            long total = 0, idle = 0;
            for (int c = 1; c < f.length; c++) {
                try {
                    long v = Long.parseLong(f[c]);
                    total += v;
                    if (c == 4 || c == 5) idle += v;   // idle + iowait
                } catch (NumberFormatException ignored) { }
            }
            totals[i] = total;
            idles[i] = idle;
        }
        if (mPrevTotal != null && mPrevTotal.length == totals.length) {
            int[] cores = new int[Math.max(0, totals.length - 1)];
            for (int i = 0; i < totals.length; i++) {
                long dt = totals[i] - mPrevTotal[i];
                long di = idles[i] - mPrevIdle[i];
                int pct = dt > 0 ? (int) Math.round(100.0 * (dt - di) / dt) : 0;
                pct = Math.max(0, Math.min(100, pct));
                if (i == 0) mLatest.cpuPercent = pct;
                else cores[i - 1] = pct;
            }
            mLatest.corePercent = cores;
        }
        mPrevTotal = totals;
        mPrevIdle = idles;
    }

    private void parseMeminfo(@NonNull List<String> lines) {
        for (String line : lines) {
            String[] f = line.split(":");
            if (f.length < 2) continue;
            long kb = parseLeadingKb(f[1]);
            switch (f[0].trim()) {
                case "MemTotal": mLatest.memTotalKb = kb; break;
                case "MemFree": mLatest.memFreeKb = kb; break;
                case "MemAvailable": mLatest.memAvailKb = kb; break;
                case "Buffers": mLatest.buffersKb = kb; break;
                case "Cached": mLatest.cachedKb = kb; break;
                case "SwapTotal": mLatest.swapTotalKb = kb; break;
                case "SwapFree": mLatest.swapFreeKb = kb; break;
                default: break;
            }
        }
        if (mLatest.memTotalKb > 0 && mLatest.memAvailKb > 0) {
            mLatest.memUsedKb = Math.max(0, mLatest.memTotalKb - mLatest.memAvailKb);
        }
    }

    private void parseLoad(@Nullable String line) {
        if (line == null) return;
        String[] f = line.trim().split("\\s+");
        if (f.length >= 1) {
            try { mLatest.load1 = Double.parseDouble(f[0]); } catch (NumberFormatException ignored) { }
        }
    }

    /**
     * Merge CPU-heavy rows from toybox top with memory-heavy rows from ps. Top already samples CPU
     * over an interval on Android; an EMA makes subsequent card updates settle instead of jumping.
     * The union is capped so both CPU and memory sorting stay useful without exposing every task.
     */
    private void parseProcessRows(@NonNull List<String> psLines,
                                  @NonNull List<String> topLines) {
        List<Proc> sampledCpu = parseTopRows(topLines);
        Map<Integer, ProcessIdentity> identities = parseProcessIdentities(psLines);
        Set<Integer> seen = new HashSet<>();
        List<Proc> smoothedCpu = new ArrayList<>();
        for (Proc proc : sampledCpu) {
            if (proc.pid < 0) continue;
            seen.add(proc.pid);
            double previous = mSmoothedProcessCpu.containsKey(proc.pid)
                ? mSmoothedProcessCpu.get(proc.pid) : proc.cpu;
            double smoothed = previous * .68d + proc.cpu * .32d;
            mSmoothedProcessCpu.put(proc.pid, smoothed);
            ProcessIdentity identity = identities.get(proc.pid);
            String name = identity == null ? proc.name : identity.name;
            long rssKb = identity == null || identity.rssKb <= 0 ? proc.rssKb : identity.rssKb;
            smoothedCpu.add(new Proc(proc.pid, name, smoothed, rssKb,
                isKernelProcessName(name, rssKb)));
        }
        smoothedCpu.sort((a, b) -> Double.compare(b.cpu, a.cpu));

        List<Proc> memory = new ArrayList<>();
        for (Map.Entry<Integer, ProcessIdentity> entry : identities.entrySet()) {
            int pid = entry.getKey();
            ProcessIdentity identity = entry.getValue();
            if (identity.rssKb <= 0) continue;
            double decayed = mSmoothedProcessCpu.containsKey(pid)
                ? mSmoothedProcessCpu.get(pid) * .68d : 0d;
            memory.add(new Proc(pid, identity.name, decayed, identity.rssKb,
                isKernelProcessName(identity.name, identity.rssKb)));
        }
        memory.sort((a, b) -> Long.compare(b.rssKb, a.rssKb));

        LinkedHashMap<Integer, Proc> selected = new LinkedHashMap<>();
        int perMetric = MAX_PROCESS_BUFFER / 2;
        for (int i = 0; i < Math.min(perMetric, smoothedCpu.size()); i++) {
            Proc proc = smoothedCpu.get(i);
            selected.put(proc.pid, proc);
        }
        for (int i = 0; i < Math.min(perMetric, memory.size()); i++) {
            Proc proc = memory.get(i);
            if (!selected.containsKey(proc.pid)) selected.put(proc.pid, proc);
        }
        seen.addAll(selected.keySet());
        mSmoothedProcessCpu.keySet().retainAll(seen);
        mLatest.top = new ArrayList<>(selected.values());
    }

    private static final class ProcessIdentity {
        @NonNull final String name;
        final long rssKb;

        ProcessIdentity(@NonNull String name, long rssKb) {
            this.name = name;
            this.rssKb = rssKb;
        }
    }

    @NonNull
    private static Map<Integer, ProcessIdentity> parseProcessIdentities(
            @NonNull List<String> lines) {
        Map<Integer, ProcessIdentity> result = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toUpperCase(Locale.ROOT).startsWith("PID")) continue;
            String[] fields = trimmed.split("\\s+", 3);
            if (fields.length < 3) continue;
            try {
                int pid = Integer.parseInt(fields[0]);
                result.put(pid, new ProcessIdentity(fields[1], parseMemoryKb(fields[2])));
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    static boolean isKernelProcessName(@NonNull String rawName, long rssKb) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) return true;
        if (rssKb > 0) return false;
        return name.startsWith("kworker") || name.startsWith("ksoftirqd")
            || name.startsWith("migration/") || name.startsWith("irq/")
            || name.startsWith("rcu") || name.contains("memlat")
            || name.endsWith("events") || name.endsWith("events_unbound");
    }

    /** Parse toybox top output, including Android 16's fused {@code S[%CPU]} header. */
    @NonNull
    static List<Proc> parseTopRows(@NonNull List<String> lines) {
        List<Proc> procs = new ArrayList<>();
        int pidCol = -1, cpuCol = -1, rssCol = -1, cmdCol = -1;
        boolean inTable = false;
        for (String raw : lines) {
            String line = raw.replaceFirst("^\\s+", "");
            if (line.isEmpty()) continue;
            String upper = line.toUpperCase(Locale.ROOT);
            if (!inTable) {
                if (upper.contains("PID") && (upper.contains("CPU") || upper.contains("%CPU"))) {
                    // Android 16 toybox prints state and CPU as one header token even though rows
                    // contain two fields: "S[%CPU]". Split it before deriving column indexes.
                    String normalized = line.replace("S[%CPU]", "S %CPU")
                        .replace("S[CPU]", "S CPU");
                    String[] cols = normalized.trim().split("\\s+");
                    for (int i = 0; i < cols.length; i++) {
                        String c = cols[i].toUpperCase(Locale.ROOT);
                        if (c.equals("PID")) pidCol = i;
                        else if (c.contains("CPU")) cpuCol = i;
                        else if (c.equals("RES") || c.equals("RSS")) rssCol = i;
                        else if (c.startsWith("CMD") || c.equals("COMMAND") || c.equals("NAME") || c.equals("ARGS")) cmdCol = i;
                    }
                    inTable = true;
                }
                continue;
            }
            String[] cols = line.trim().split("\\s+");
            if (cmdCol < 0 || cmdCol >= cols.length) continue;
            double cpu = cpuCol >= 0 && cpuCol < cols.length ? parseDouble(cols[cpuCol]) : 0;
            long rssKb = rssCol >= 0 && rssCol < cols.length ? parseMemoryKb(cols[rssCol]) : 0;
            int pid = -1;
            if (pidCol >= 0 && pidCol < cols.length) {
                try { pid = Integer.parseInt(cols[pidCol]); } catch (NumberFormatException ignored) { }
            }
            String name = cols[cmdCol];
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            if (name.isEmpty() || "top".equals(name) || "head".equals(name)) continue;
            procs.add(new Proc(pid, name, cpu, rssKb, isKernelProcessName(name, rssKb)));
            if (procs.size() >= MAX_PROCESS_BUFFER) break;
        }
        return procs;
    }

    private void readActivityManagerMemory() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mLatest.memTotalKb == 0) mLatest.memTotalKb = mi.totalMem / 1024;
            if (mLatest.memAvailKb == 0) mLatest.memAvailKb = mi.availMem / 1024;
            if (mLatest.memTotalKb > 0 && mLatest.memAvailKb > 0) {
                mLatest.memUsedKb = Math.max(0, mLatest.memTotalKb - mLatest.memAvailKb);
            }
        } catch (Exception ignored) { }
    }

    private void readDirectFallback() {
        parseMeminfo(readFileLines("/proc/meminfo"));
        List<String> load = readFileLines("/proc/loadavg");
        if (!load.isEmpty()) parseLoad(load.get(0));
        List<String> stat = new ArrayList<>();
        for (String l : readFileLines("/proc/stat")) {
            if (l.startsWith("cpu")) stat.add(l);
        }
        parseCpu(stat);
    }

    @NonNull
    private static List<String> readFileLines(@NonNull String path) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line);
        } catch (Exception ignored) { }
        return out;
    }

    private static long parseLeadingKb(@NonNull String value) {
        String[] f = value.trim().split("\\s+");
        if (f.length == 0) return 0;
        try { return Long.parseLong(f[0]); } catch (NumberFormatException e) { return 0; }
    }

    private static double parseDouble(@NonNull String value) {
        try { return Double.parseDouble(value.replace("%", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static long parseMemoryKb(@NonNull String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) return 0;
        double multiplier = 1d;
        char suffix = value.charAt(value.length() - 1);
        if (suffix == 'K' || suffix == 'M' || suffix == 'G' || suffix == 'T') {
            value = value.substring(0, value.length() - 1);
            if (suffix == 'M') multiplier = 1024d;
            else if (suffix == 'G') multiplier = 1024d * 1024d;
            else if (suffix == 'T') multiplier = 1024d * 1024d * 1024d;
        }
        try {
            return Math.max(0L, Math.round(Double.parseDouble(value) * multiplier));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
