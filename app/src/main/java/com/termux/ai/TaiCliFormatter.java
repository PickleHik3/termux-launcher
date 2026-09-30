package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiCliFormatter {
    private TaiCliFormatter() {
    }

    @NonNull
    public static String format(@NonNull String command, @NonNull JSONObject data) {
        try {
            if ("preflight".equals(command)) return formatPreflight(data);
            if (!data.optBoolean("ok", true) || data.has("error")) {
                return formatError(data);
            }
            switch (command) {
                case "status":
                    return formatStatus(data);
                case "runtime":
                    return formatRuntime(data);
                case "models":
                    return formatModels(data);
                case "downloads":
                    return formatDownloads(data);
                case "import":
                    return formatImport(data);
                case "download":
                    return formatDownloadStarted(data);
                case "preflight":
                    return formatPreflight(data);
                case "delete":
                    return formatDelete(data);
                case "load":
                    return formatLoad(data);
                case "unload":
                    return formatUnload(data);
                case "keep-warm":
                    return formatKeepWarm(data);
                case "cancel":
                    return formatCancel(data);
                case "launcher-status":
                    return formatLauncherStatus(data);
                case "speak":
                    return formatSpeak(data);
                case "speak-stop":
                    return data.optBoolean("stopped", false) ? "Stopped.\n" : "Nothing was speaking.\n";
                case "image":
                    return formatImage(data);
                case "image-cancel":
                    return data.optBoolean("cancelled", false) ? "Image generation cancelled.\n" : "No image was being generated.\n";
                case "benchmarks":
                    return formatBenchmarks(data);
                case "benchmarks-clear":
                    return formatBenchmarksCleared(data);
                case "benchmark-run":
                    return formatBenchRun(data);
                default:
                    return formatGeneric(data);
            }
        } catch (Exception e) {
            return data.toString() + "\n";
        }
    }

    // ---- tai image ------------------------------------------------------------------------------

    /** One progress line of {@code tai image}'s live text stream; the script prints it to stderr. */
    @NonNull
    public static String imageProgressLine(int percent) {
        return "progress " + Math.max(0, Math.min(100, percent)) + "\n";
    }

    /** The lines that end a successful text stream: where the image went, then the run's figures as JSON. */
    @NonNull
    public static String imageDoneLines(@NonNull JSONObject response) {
        JSONArray data = response.optJSONArray("data");
        JSONObject item = data == null ? null : data.optJSONObject(0);
        String path = item == null ? "" : item.optString("path", "");
        StringBuilder out = new StringBuilder();
        out.append("done ").append(path.isEmpty() ? "(inline image; ask for an output file in text mode)" : path).append('\n');
        JSONObject tai = response.optJSONObject("tai");
        if (tai != null) out.append("info ").append(tai).append('\n');
        return out.toString();
    }

    /** The line that ends a failed text stream, from an OpenAI-shaped or flat error. */
    @NonNull
    public static String imageErrorLine(@NonNull JSONObject error) {
        JSONObject nested = error.optJSONObject("error");
        String message = nested != null ? nested.optString("message", "") : error.optString("message", "");
        if (message.isEmpty()) message = "The image could not be generated.";
        return "error " + message.replace('\n', ' ') + "\n";
    }

    @NonNull
    private static String formatImage(@NonNull JSONObject data) {
        JSONArray items = data.optJSONArray("data");
        JSONObject item = items == null ? null : items.optJSONObject(0);
        JSONObject tai = data.optJSONObject("tai");
        StringBuilder out = new StringBuilder();
        String path = item == null ? "" : item.optString("path", "");
        out.append(path.isEmpty() ? "Image generated.\n" : "Saved " + path + "\n");
        if (tai != null) {
            out.append(tai.optInt("width", 0)).append('x').append(tai.optInt("height", 0))
                .append(", ").append(tai.optInt("steps", 0)).append(" steps, seed ").append(tai.optInt("seed", 0))
                .append(", ").append(tai.optString("backend", "")).append(", memory mode ").append(tai.optInt("memoryMode", 0))
                .append('\n');
            out.append(String.format(Locale.ROOT, "Loaded in %.1f s, generated in %.1f s\n",
                tai.optLong("loadMs", 0L) / 1000.0, tai.optLong("generateMs", 0L) / 1000.0));
        }
        return out.toString();
    }

    // ---- tai benchmark -------------------------------------------------------------------------

    /**
     * One line of {@code tai benchmark}'s live output for a harness event, or {@code null} for
     * the events the terminal does not show (tokens, phase starts). The same lines make up the
     * non-streaming summary ({@link #formatBenchRun}), so the two read alike.
     */
    @Nullable
    public static String formatBenchEvent(@NonNull JSONObject event) {
        String name = event.optString("event", "");
        switch (name) {
            case "entry_start": {
                JSONObject entry = event.optJSONObject("entry");
                return "\n[" + (event.optInt("index", 0) + 1) + "/" + event.optInt("total", 0) + "] " + describeEntry(entry) + "\n";
            }
            case "phase_done":
                return formatPhaseDone(event.optString("phase", ""), event.optJSONObject("metrics"), event.optString("status", "ok"));
            case "skipped":
                return "  skipped: " + clean(event.optString("reason", event.optString("code", ""))) + "\n";
            case "paused":
                return "  paused " + formatDuration(event.optLong("ms", 0L)) + ": " + clean(event.optString("reason", "")) + "\n";
            case "error": {
                String phase = event.optString("phase", "");
                String prefix = phase.isEmpty() ? "  error" : "  " + phaseLabel(phase).trim() + " error";
                return prefix + ": " + clean(event.optString("message", event.optString("code", ""))) + "\n";
            }
            case "entry_done":
                return formatRecordVerdict(event.optJSONObject("record"));
            case "done": {
                JSONArray skipped = event.optJSONArray("skipped");
                StringBuilder out = new StringBuilder("\nDone: ").append(event.optInt("entries", 0)).append(" entries");
                if (skipped != null && skipped.length() > 0) out.append(", ").append(skipped.length()).append(" skipped");
                if (!event.isNull("stopped") && event.has("stopped")) out.append(", stopped: ").append(clean(event.optString("stopped", "")));
                out.append(". Results: tai benchmark --results\n");
                return out.toString();
            }
            default:
                return null;
        }
    }

    /** The summary of a run answered in one piece: each record's lines, then the leaderboard. */
    @NonNull
    private static String formatBenchRun(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        JSONArray records = data.optJSONArray("records");
        int count = records == null ? 0 : records.length();
        for (int i = 0; i < count; i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            out.append(i == 0 ? "" : "\n").append('[').append(i + 1).append('/').append(count).append("] ")
                .append(describeEntry(record)).append('\n');
            JSONObject phases = record.optJSONObject("phases");
            if (phases != null) {
                for (String phase : new String[] {"load", "chat", "longInput"}) {
                    JSONObject metrics = phases.optJSONObject(phase);
                    if (metrics != null) out.append(formatPhaseDone(phase, metrics, "ok"));
                }
            }
            JSONObject check = record.optJSONObject("check");
            if (check != null && check.optInt("total", 0) > 0) out.append(formatPhaseDone("check", check, "ok"));
            if (record.optString("status", "").startsWith("skipped:")) {
                out.append("  skipped: ").append(clean(record.optString("skipReason", record.optString("status", "")))).append('\n');
            }
            out.append(formatRecordVerdict(record));
        }
        JSONObject leaderboard = data.optJSONObject("leaderboard");
        if (leaderboard != null) {
            out.append('\n').append(formatLeaderboard(leaderboard));
        }
        return out.toString();
    }

    /** {@code tai benchmark --results}: the leaderboard, then how many records are on file. */
    @NonNull
    private static String formatBenchmarks(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        JSONObject leaderboard = data.optJSONObject("leaderboard");
        if (leaderboard != null) out.append(formatLeaderboard(leaderboard));
        JSONArray records = data.optJSONArray("records");
        int count = records == null ? 0 : records.length();
        out.append('\n').append(count).append(count == 1 ? " record" : " records");
        JSONArray versions = data.optJSONArray("benchVersions");
        if (versions != null && versions.length() > 1) out.append(" across ").append(versions.length()).append(" bench versions");
        out.append(" in ").append(clean(data.optString("file", "benchmarks.json"))).append('\n');
        return out.toString();
    }

    @NonNull
    private static String formatBenchmarksCleared(@NonNull JSONObject data) {
        int removed = data.optInt("removed", 0);
        String model = nullable(data, "modelId", "");
        return "Removed " + removed + (removed == 1 ? " benchmark result" : " benchmark results")
            + (model.isEmpty() ? "" : " for " + model) + ".\n";
    }

    /** A ranked table; broken entries (check failed) listed after it. */
    @NonNull
    private static String formatLeaderboard(@NonNull JSONObject leaderboard) {
        StringBuilder out = new StringBuilder();
        out.append("Leaderboard (").append(clean(leaderboard.optString("benchVersion", ""))).append(")\n");
        JSONArray ranked = leaderboard.optJSONArray("ranked");
        if (ranked == null || ranked.length() == 0) {
            out.append("  No complete results yet. Run: tai benchmark [model] --preset quick\n");
        } else {
            out.append(String.format(Locale.US, "  %2s  %-34s %-5s %-7s %10s %8s %8s %8s\n",
                "#", "model", "proc", "verdict", "writes", "starts", "reads", "memory"));
            for (int i = 0; i < ranked.length(); i++) {
                JSONObject row = ranked.optJSONObject(i);
                if (row == null) continue;
                out.append(String.format(Locale.US, "  %2d  %-34s %-5s %-7s %10s %8s %8s %8s\n",
                    row.optInt("rank", i + 1), shorten(row.optString("modelId", ""), 34), processorLabel(row),
                    clean(row.optString("verdict", "")), tps(row.optDouble("decodeTps", 0.0)), secondsOf(row, "ttftMs"),
                    secondsOf(row, "readMs"), row.optLong("memBytes", -1L) > 0L ? formatBytes(row.optLong("memBytes", -1L)) : ""));
            }
        }
        JSONArray broken = leaderboard.optJSONArray("broken");
        if (broken != null && broken.length() > 0) {
            out.append("Broken (sanity check failed, not ranked):\n");
            for (int i = 0; i < broken.length(); i++) {
                JSONObject row = broken.optJSONObject(i);
                if (row == null) continue;
                out.append("  ").append(clean(row.optString("modelId", ""))).append(' ').append(processorLabel(row))
                    .append('\n');
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatPhaseDone(@NonNull String phase, @Nullable JSONObject metrics, @NonNull String status) {
        StringBuilder out = new StringBuilder("  ").append(phaseLabel(phase));
        if (metrics == null) metrics = new JSONObject();
        switch (phase) {
            case "load":
                out.append(seconds(metrics.optLong("ms", 0L)));
                long mem = metrics.optLong("memBytes", -1L);
                if (mem >= 0L) out.append(", ").append(formatBytes(mem)).append(" used");
                break;
            case "warmup":
                out.append("done");
                break;
            case "chat":
                out.append("first token ").append(seriesSeconds(metrics.optJSONObject("ttftMs")))
                    .append(", writes ").append(tpsSeries(metrics.optJSONObject("decodeTps")));
                if (metrics.has("tokens")) out.append(", ").append(metrics.optInt("tokens", 0)).append(" tokens");
                break;
            case "longInput":
                out.append("read time ").append(seriesSeconds(metrics.optJSONObject("readMs")));
                if (metrics.optInt("promptTokens", 0) > 0) {
                    out.append(" (").append(metrics.optInt("promptTokens", 0)).append(" prompt tokens");
                    if (metrics.optJSONObject("promptTps") != null) out.append(", ").append(tpsSeries(metrics.optJSONObject("promptTps")));
                    out.append(')');
                }
                if (metrics.optBoolean("truncated", false)) out.append(", log cut to fit the window");
                if (metrics.optLong("peakPssBytes", -1L) > 0L) out.append(", ").append(formatBytes(metrics.optLong("peakPssBytes", -1L))).append(" peak");
                break;
            case "check":
                out.append(metrics.optInt("passed", 0)).append('/').append(metrics.optInt("total", 0));
                JSONArray details = metrics.optJSONArray("details");
                if (details != null) {
                    StringBuilder failed = new StringBuilder();
                    for (int i = 0; i < details.length(); i++) {
                        JSONObject detail = details.optJSONObject(i);
                        if (detail == null || detail.optBoolean("passed", false)) continue;
                        if (failed.length() > 0) failed.append(", ");
                        failed.append(detail.optString("name", "")).append(" got \"").append(shorten(detail.optString("reply", "").trim(), 40)).append('"');
                    }
                    if (failed.length() > 0) out.append(" (failed: ").append(failed).append(')');
                }
                break;
            default:
                out.append(metrics.toString());
                break;
        }
        if ("timeout".equals(status)) out.append("  [timed out]");
        return out.append('\n').toString();
    }

    /** "  -> smooth (21.3 tok/s)" or the status that stopped the entry. */
    @NonNull
    private static String formatRecordVerdict(@Nullable JSONObject record) {
        if (record == null) return "";
        String status = record.optString("status", "");
        if (status.startsWith("skipped:")) return "";
        JSONObject phases = record.optJSONObject("phases");
        JSONObject chat = phases == null ? null : phases.optJSONObject("chat");
        JSONObject decode = chat == null ? null : chat.optJSONObject("decodeTps");
        String verdict = nullable(record, "verdict", "");
        StringBuilder out = new StringBuilder("  -> ");
        if (!verdict.isEmpty()) {
            out.append(verdict);
            if (decode != null) out.append(" (").append(tps(decode.optDouble("med", 0.0))).append(')');
        } else {
            out.append(status);
        }
        if (!"complete".equals(status) && !verdict.isEmpty()) out.append(", ").append(status);
        return out.append('\n').toString();
    }

    @NonNull
    private static String describeEntry(@Nullable JSONObject entry) {
        if (entry == null) return "";
        StringBuilder out = new StringBuilder(clean(entry.optString("modelId", "")));
        String backend = clean(entry.optString("backend", ""));
        if (!backend.isEmpty()) out.append(" · ").append(TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? "MNN" : "LiteRT-LM");
        out.append(" · ").append(processorLabel(entry));
        return out.toString();
    }

    @NonNull
    private static String processorLabel(@NonNull JSONObject entry) {
        String label = clean(entry.optString("accelerator", "")).toUpperCase(Locale.ROOT);
        return entry.optBoolean("speculative", false) ? label + "+draft" : label;
    }

    @NonNull
    private static String phaseLabel(@NonNull String phase) {
        switch (phase) {
            case "load": return "load        ";
            case "warmup": return "warm-up     ";
            case "chat": return "chat        ";
            case "longInput": return "long input  ";
            case "check": return "check       ";
            default: return String.format(Locale.US, "%-12s", phase);
        }
    }

    /** "21.3 tok/s (min 19.8, max 22.0)" over a {@code {med, min, max, runs}} object; "not measured" without one. */
    @NonNull
    private static String tpsSeries(@Nullable JSONObject metrics) {
        if (metrics == null || !metrics.has("med")) return "not measured";
        String med = tps(metrics.optDouble("med", 0.0));
        if (metrics.optInt("runs", 1) <= 1) return med;
        return med + String.format(Locale.US, " (min %.1f, max %.1f)", metrics.optDouble("min", 0.0), metrics.optDouble("max", 0.0));
    }

    /** "0.6 s (min 0.5, max 0.7)" over a {@code {med, min, max, runs}} object in milliseconds; "not measured" without one. */
    @NonNull
    private static String seriesSeconds(@Nullable JSONObject metrics) {
        if (metrics == null || !metrics.has("med")) return "not measured";
        String med = String.format(Locale.US, "%.1f s", metrics.optDouble("med", 0.0) / 1000.0);
        if (metrics.optInt("runs", 1) <= 1) return med;
        return med + String.format(Locale.US, " (min %.1f, max %.1f)", metrics.optDouble("min", 0.0) / 1000.0, metrics.optDouble("max", 0.0) / 1000.0);
    }

    @NonNull
    private static String tps(double value) {
        return String.format(Locale.US, value >= 100.0 ? "%.0f tok/s" : "%.1f tok/s", value);
    }

    /** A row's milliseconds figure as "0.6 s"; empty when it was not measured. */
    @NonNull
    private static String secondsOf(@NonNull JSONObject row, @NonNull String key) {
        if (row.isNull(key) || !row.has(key)) return "";
        return String.format(Locale.US, "%.1f s", row.optDouble(key, 0.0) / 1000.0);
    }

    @NonNull
    private static String seconds(long millis) {
        return String.format(Locale.US, "%.1f s", millis / 1000.0);
    }

    @NonNull
    private static String shorten(@NonNull String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** "Spoke 3 sentences (4.2 s) as Jasper; first sound after 0.9 s." or "Stopped after 1 sentence." */
    @NonNull
    private static String formatSpeak(@NonNull JSONObject data) {
        int sentences = data.optInt("sentences", 0);
        String counted = sentences + (sentences == 1 ? " sentence" : " sentences");
        if (data.optBoolean("stopped", false) || data.optBoolean("cancelled", false)) {
            return "Stopped after " + counted + ".\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("Spoke ").append(counted)
            .append(String.format(Locale.US, " (%.1f s)", data.optDouble("audioSeconds", 0.0)));
        String voice = data.optString("voice", "");
        if (!voice.isEmpty()) out.append(" as ").append(voice);
        long firstSound = data.optLong("firstSoundMs", -1L);
        if (firstSound >= 0L) out.append(String.format(Locale.US, "; first sound after %.1f s", firstSound / 1000.0));
        return out.append(".\n").toString();
    }

    @NonNull
    private static String formatError(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("On-device AI error");
        String message = clean(data.optString("message", ""));
        if (!message.isEmpty()) out.append(": ").append(message);
        out.append('\n');
        appendValue(out, "Code", clean(data.optString("error", "")));
        appendValue(out, "Runtime", clean(data.optString("runtime", "")));
        appendValue(out, "Provider page", clean(data.optString("providerPageUrl", "")));
        appendValue(out, "Download URL", clean(data.optString("downloadUrl", "")));
        return out.toString();
    }

    @NonNull
    private static String formatStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("On-device AI status\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) {
            appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
            appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
            appendValue(out, "Lifecycle", runtime.optString("state", ""));
            appendValue(out, "Backend", runtime.optString("backend", ""));
            appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
            appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
            appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
            appendValue(out, "State", runtime.optString("status", ""));
        }
        appendCompatibility(out, data);

        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject roles = settings.optJSONObject("roles");
            if (roles != null) {
                out.append("\nRoles\n");
                appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
            }
            out.append("\nSettings\n");
            appendValue(out, "Idle unload", settings.optInt("idleUnloadMinutes", 0) + " min");
            appendValue(out, "Hugging Face token", settings.optBoolean("huggingFaceTokenConfigured", false) ? "configured" : "not configured");
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / model profile"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
            }
        }

        JSONArray limitations = data.optJSONArray("limitations");
        if (limitations != null && limitations.length() > 0) {
            out.append("\nLimitations\n");
            appendBullets(out, limitations);
        }
        return out.toString();
    }

    @NonNull
    private static String formatRuntime(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("On-device AI runtime\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) appendRuntimeState(out, runtime);
        appendCompatibility(out, data);
        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                out.append("\nDefaults\n");
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / model profile"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "TopK", nullable(options, "topK", "Auto / Gallery default"));
                appendValue(out, "TopP", nullable(options, "topP", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
                appendValue(out, "Speculative decoding", nullable(options, "speculativeDecodingEnabled", "Auto / Gallery default"));
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatModels(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("On-device AI models\n");
        appendValue(out, "Storage", data.optString("storageDirectory", ""));
        appendValue(out, "Bundled model files", data.optBoolean("bundledModelFiles", false) ? "yes" : "no");

        JSONObject roles = data.optJSONObject("roles");
        if (roles != null) {
            out.append("\nRoles\n");
            appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
        }

        JSONArray models = data.optJSONArray("models");
        out.append("\nAvailable models\n");
        if (models == null || models.length() == 0) {
            out.append("  none\n");
        } else {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                out.append("  ").append(model.optString("id", "unknown"));
                if (!model.isNull("localPath")) {
                    out.append(" [downloaded/imported]");
                } else {
                    out.append(" [catalog]");
                }
                out.append('\n');
                appendValue(out, "    Role", model.optString("roleHint", ""));
                appendValue(out, "    Source", model.optString("source", ""));
                appendValue(out, "    Backend", model.optString("backend", "") + " / " + model.optString("format", ""));
                appendValue(out, "    Quantization", nullable(model, "quantization", "not specified"));
                appendValue(out, "    Recommended memory", model.optInt("recommendedRamGb", 0) > 0
                    ? model.optInt("recommendedRamGb") + " GiB" : "not specified");
                appendValue(out, "    Size", model.optLong("sizeBytes", 0L) > 0 ? formatBytes(model.optLong("sizeBytes")) : "not downloaded");
                appendValue(out, "    Capabilities", join(model.optJSONArray("capabilities")));
                JSONObject profile = model.optJSONObject("runtimeProfile");
                if (profile != null) {
                    appendValue(out, "    Accelerators", join(profile.optJSONArray("compatibleAccelerators")));
                    appendValue(out, "    Minimum memory", profile.isNull("minDeviceMemoryInGb")
                        ? "not specified" : profile.optInt("minDeviceMemoryInGb") + " GiB");
                    appendValue(out, "    Defaults", profile.optInt("defaultMaxTokens") + " tokens, temperature "
                        + profile.optDouble("defaultTemperature"));
                }
            }
        }

        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads != null && downloads.length() > 0) {
            out.append("\nDownloads\n");
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatDownloads(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("On-device AI downloads\n");
        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads == null || downloads.length() == 0) {
            out.append("  none\n");
        } else {
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatImport(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model imported\n");
        JSONObject model = data.optJSONObject("model");
        if (model != null) {
            appendValue(out, "Model", model.optString("id", ""));
            appendValue(out, "Path", nullable(model, "localPath", ""));
            appendValue(out, "Capabilities", join(model.optJSONArray("capabilities")));
        }
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDownloadStarted(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("started", false) ? "Download started\n" : "Download request\n");
        JSONObject transfer = data.optJSONObject("transfer");
        if (transfer != null) appendTransfer(out, transfer);
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDelete(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("deleted", false) ? "Model deleted\n" : "No matching model was deleted\n");
        appendValue(out, "Model", data.optString("modelId", ""));
        return out.toString();
    }

    @NonNull
    private static String formatLoad(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model loaded\n");
        appendValue(out, "Model", data.optString("loadedModelId", ""));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        appendValue(out, "Backend", data.optString("backend", ""));
        appendValue(out, "Fallback", nullable(data, "backendFallbackReason", ""));
        appendValue(out, "Path", data.optString("modelPath", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) {
            appendValue(out, "Lifecycle", state.optString("state", ""));
            appendValue(out, "Keep warm", state.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(state.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", state.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(state.optLong("idleUnloadRemainingMs", 0L)) : "off");
        }
        appendCompatibility(out, data);
        return out.toString();
    }

    @NonNull
    private static String formatPreflight(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("ok", false) ? "Preflight passed\n" : "Preflight blocked\n");
        JSONObject model = data.optJSONObject("model");
        if (model != null) {
            appendValue(out, "Model", model.optString("id", ""));
            appendValue(out, "Backend", model.optString("backend", "") + " / " + model.optString("format", ""));
            appendValue(out, "Recommended memory", model.optInt("recommendedRamGb", 0) > 0
                ? model.optInt("recommendedRamGb") + " GiB" : "not specified");
        }
        appendValue(out, "Requested accelerator", data.optString("requestedAccelerator", ""));
        appendValue(out, "Effective accelerator", data.optString("effectiveAccelerator", ""));
        appendValue(out, "Message", data.optString("message", ""));
        appendCompatibility(out, data);

        JSONArray checks = data.optJSONArray("checks");
        if (checks != null && checks.length() > 0) {
            out.append("\nChecks\n");
            for (int i = 0; i < checks.length(); i++) {
                JSONObject check = checks.optJSONObject(i);
                if (check == null) continue;
                out.append("  ").append(check.optBoolean("ok", false) ? "OK " : "FAIL ")
                    .append(check.optString("id", "check"));
                String message = clean(check.optString("message", ""));
                if (!message.isEmpty()) out.append(": ").append(message);
                out.append('\n');
            }
        }

        JSONArray warnings = data.optJSONArray("warnings");
        if (warnings != null && warnings.length() > 0) {
            out.append("\nWarnings\n");
            for (int i = 0; i < warnings.length(); i++) {
                JSONObject warning = warnings.optJSONObject(i);
                if (warning == null) continue;
                String message = clean(warning.optString("message", ""));
                if (!message.isEmpty()) out.append("  - ").append(message).append('\n');
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatUnload(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("loadCancellationRequested", false)
            ? "Model load cancellation requested\n"
            : "Model unloaded\n");
        appendValue(out, "Previous model", nullable(data, "unloadedModelId", "none"));
        appendValue(out, "Loading model", nullable(data, "loadingModelId", ""));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        return out.toString();
    }

    @NonNull
    private static String formatKeepWarm(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Runtime keep-warm enabled\n");
        appendValue(out, "Minutes", data.optInt("keepWarmMinutes", 0) + "");
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatCancel(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("loadCancellationRequested", false)
            ? "Model load cancellation requested\n"
            : (data.optBoolean("cancelled", false) ? "Generation cancel requested\n" : "No active generation\n"));
        appendValue(out, "Message", data.optString("message", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatLauncherStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("LauncherCtl status\n");
        appendValue(out, "API", data.optString("apiVersion", ""));
        appendValue(out, "Backend", data.optString("backendType", "") + " / " + data.optString("backendState", ""));
        appendValue(out, "Status", data.optString("statusMessage", ""));
        appendValue(out, "Privileged available", data.optBoolean("isPrivilegedAvailable", false) ? "yes" : "no");
        appendValue(out, "Notification listener", data.optBoolean("notificationListenerConnected", false) ? "connected" : "not connected");
        return out.toString();
    }

    @NonNull
    private static String formatGeneric(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        JSONArray names = data.names();
        if (names == null || names.length() == 0) return "{}\n";
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            Object value = data.opt(key);
            appendValue(out, key, value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value));
        }
        return out.toString();
    }

    private static void appendDownloads(@NonNull StringBuilder out, @NonNull JSONArray downloads) {
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject transfer = downloads.optJSONObject(i);
            if (transfer == null) continue;
            out.append("  ").append(i + 1).append(". ");
            out.append(transfer.optString("modelId", transfer.optString("id", "unknown")));
            out.append(" - ").append(transfer.optString("status", "unknown"));
            String progress = progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L));
            if (!progress.isEmpty()) out.append(" (").append(progress).append(")");
            out.append('\n');
            appendValue(out, "     Path", transfer.optString("path", ""));
            appendValue(out, "     Error", transfer.optString("error", ""));
        }
    }

    private static void appendRuntimeState(@NonNull StringBuilder out, @NonNull JSONObject runtime) {
        appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
        appendValue(out, "Lifecycle", runtime.optString("state", ""));
        appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
        appendValue(out, "Backend", runtime.optString("backend", ""));
        appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
        appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
        appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
        appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
        appendValue(out, "Status", runtime.optString("status", ""));
    }

    private static void appendCompatibility(@NonNull StringBuilder out, @NonNull JSONObject data) {
        JSONObject profile = data.optJSONObject("modelProfile");
        if (profile != null) {
            out.append("\nModel profile\n");
            appendValue(out, "Compatible accelerators", join(profile.optJSONArray("compatibleAccelerators")));
            appendValue(out, "Minimum memory", profile.isNull("minDeviceMemoryInGb")
                ? "not specified" : profile.optInt("minDeviceMemoryInGb") + " GiB");
            appendValue(out, "Generation defaults", profile.optInt("defaultMaxTokens") + " tokens, TopK "
                + profile.optInt("defaultTopK") + ", TopP " + profile.optDouble("defaultTopP")
                + ", temperature " + profile.optDouble("defaultTemperature"));
            appendValue(out, "Profile source", profile.optString("source", ""));
        }
        JSONObject device = data.optJSONObject("device");
        if (device != null) {
            out.append("\nDevice\n");
            appendValue(out, "Model", device.optString("manufacturer", "") + " " + device.optString("model", ""));
            appendValue(out, "SoC", device.optString("socModel", ""));
            appendValue(out, "Android API", String.valueOf(device.optInt("sdkInt", 0)));
            appendValue(out, "Memory", device.isNull("memoryGiB") ? "unknown"
                : String.format(Locale.US, "%.1f GiB (%s)", device.optDouble("memoryGiB"), device.optString("memorySource", "")));
            appendValue(out, "LiteRT-LM ABI", device.optBoolean("liteRtLmAbiSupported", false) ? "supported" : "unsupported");
            appendValue(out, "Phase 1 accelerators", join(device.optJSONArray("phase1Accelerators")));
            appendValue(out, "GPU policy", device.optString("gpuPolicy", ""));
        }
        JSONArray warnings = data.optJSONArray("compatibilityWarnings");
        if (warnings != null && warnings.length() > 0) {
            out.append("\nCompatibility warnings\n");
            appendBullets(out, warnings);
        }
    }

    private static void appendTransfer(@NonNull StringBuilder out, @NonNull JSONObject transfer) {
        appendValue(out, "Model", transfer.optString("modelId", ""));
        appendValue(out, "Status", transfer.optString("status", ""));
        appendValue(out, "Progress", progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L)));
        appendValue(out, "Path", transfer.optString("path", ""));
        appendValue(out, "Error", transfer.optString("error", ""));
    }

    private static void appendBullets(@NonNull StringBuilder out, @NonNull JSONArray values) {
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) out.append("  - ").append(value).append('\n');
        }
    }

    private static void appendValue(@NonNull StringBuilder out, @NonNull String label, @NonNull String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return;
        out.append(label).append(": ").append(clean).append('\n');
    }

    @NonNull
    private static String nullable(@NonNull JSONObject object, @NonNull String key, @NonNull String fallback) {
        if (!object.has(key) || object.isNull(key)) return fallback;
        return clean(object.optString(key, fallback));
    }

    @NonNull
    private static String join(JSONArray values) {
        if (values == null || values.length() == 0) return "";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (value.isEmpty()) continue;
            if (joined.length() > 0) joined.append(", ");
            joined.append(value);
        }
        return joined.toString();
    }

    @NonNull
    private static String progress(long bytesRead, long totalBytes) {
        if (bytesRead <= 0L && totalBytes <= 0L) return "";
        if (totalBytes <= 0L) return formatBytes(bytesRead);
        double percent = Math.max(0d, Math.min(100d, (bytesRead * 100d) / totalBytes));
        return formatBytes(bytesRead) + " / " + formatBytes(totalBytes) + " (" + String.format(Locale.US, "%.1f", percent) + "%)";
    }

    @NonNull
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    @NonNull
    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    @NonNull
    private static String clean(@NonNull String value) {
        String trimmed = value.trim();
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }
}
