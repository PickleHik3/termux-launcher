package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Resolves repository identity separately from the user's choice of downloadable artifact. */
public final class TaiHuggingFace {
    public final String repository;
    public final String revision;
    public final String path;
    public final boolean file;

    private TaiHuggingFace(String repository, String revision, String path, boolean file) {
        this.repository = repository;
        this.revision = revision;
        this.path = path;
        this.file = file;
    }

    public static TaiHuggingFace parse(String url) {
        try {
            URI uri = new URI(url.trim());
            if (!"https".equals(uri.getScheme()) || !"huggingface.co".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) return null;
            String[] parts = uri.getRawPath().split("/", 6);
            if (parts.length < 3 || parts[1].isEmpty() || parts[2].isEmpty()) return null;
            String repo = parts[1] + "/" + parts[2];
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty()))
                return new TaiHuggingFace(repo, "main", "", false);
            if (parts.length < 5 || !("tree".equals(parts[3]) || "blob".equals(parts[3])
                || "resolve".equals(parts[3]))) return null;
            String revision = decode(parts[4]);
            String path = parts.length == 6 ? decode(parts[5]) : "";
            boolean file = !"tree".equals(parts[3]);
            if (revision.isEmpty() || (file && path.isEmpty()) || !safePath(path)) return null;
            return new TaiHuggingFace(repo, revision, path, file);
        } catch (Exception e) { return null; }
    }

    public String metadataUrl() {
        return "https://huggingface.co/api/models/" + repository + "/revision/" + encode(revision) + "?blobs=true";
    }

    public String fileUrl(String commit, String artifact) {
        StringBuilder path = new StringBuilder();
        for (String segment : artifact.split("/")) {
            if (path.length() > 0) path.append('/');
            path.append(encode(segment));
        }
        return "https://huggingface.co/" + repository + "/resolve/" + encode(commit) + "/" + path;
    }

    /**
     * The model card at the resolved commit, so what the importer quotes from it is the card of
     * the exact files it offers, not of whatever the branch holds later.
     */
    public String readmeUrl(String commit) {
        return fileUrl(commit, "README.md");
    }

    /** Whether the repository lists a top-level README.md, so a missing card costs no request. */
    public static boolean hasReadme(JSONObject metadata) {
        JSONArray siblings = metadata == null ? null : metadata.optJSONArray("siblings");
        for (int i = 0; siblings != null && i < siblings.length(); i++) {
            JSONObject item = siblings.optJSONObject(i);
            if (item != null && "README.md".equals(item.optString("rfilename"))) return true;
        }
        return false;
    }

    /**
     * What the repository itself declares about the model, for the importer to show as facts:
     * the card's {@code pipeline_tag}, its tags, the license in the card's front matter and the
     * base model. Only fields Hugging Face returns are copied; nothing is inferred here.
     */
    public static JSONObject modelFacts(JSONObject metadata) throws Exception {
        JSONObject facts = new JSONObject();
        if (metadata == null) return facts;
        JSONObject card = metadata.optJSONObject("cardData");
        String pipeline = metadata.optString("pipeline_tag", "");
        if (pipeline.isEmpty() && card != null) pipeline = card.optString("pipeline_tag", "");
        if (!pipeline.isEmpty()) facts.put("pipelineTag", pipeline);
        JSONArray tags = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        JSONArray repoTags = metadata.optJSONArray("tags");
        for (int i = 0; repoTags != null && i < repoTags.length(); i++) {
            String tag = repoTags.optString(i, "");
            if (!tag.isEmpty() && seen.add(tag)) tags.put(tag);
        }
        facts.put("tags", tags);
        if (card != null) {
            String license = card.optString("license", "");
            if (!license.isEmpty()) facts.put("license", license);
            Object base = card.opt("base_model");
            String baseModel = base instanceof JSONArray ? ((JSONArray) base).optString(0, "")
                : base instanceof String ? (String) base : "";
            if (!baseModel.isEmpty()) facts.put("baseModel", baseModel);
        }
        return facts;
    }

    public JSONArray candidates(JSONObject metadata) throws Exception {
        JSONArray siblings = metadata.optJSONArray("siblings");
        LinkedHashSet<String> files = new LinkedHashSet<>();
        if (siblings != null) for (int i = 0; i < siblings.length(); i++) {
            JSONObject item = siblings.optJSONObject(i);
            if (item != null && safePath(item.optString("rfilename"))) files.add(item.optString("rfilename"));
        }
        List<String> entries = new ArrayList<>();
        // A directory whose config.json sits next to eagle.mnn: an EAGLE-3 draft head shipped
        // alongside the plain model (taobao-mnn/…-Eagle3-MNN), decoded faster for the same answers.
        LinkedHashSet<String> eagleDirectories = new LinkedHashSet<>();
        for (String name : files) {
            if (file ? !name.equals(path) : !path.isEmpty() && !name.startsWith(path.replaceAll("/$", "") + "/")) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".litertlm") || lower.endsWith(".task") || lower.endsWith(".tflite")) entries.add(name);
            else if (name.equals("config.json") || name.endsWith("/config.json")) {
                String directory = name.substring(0, name.length() - "config.json".length());
                for (String other : files) {
                    if (other.startsWith(directory) && other.endsWith(".mnn")) { entries.add(name); break; }
                }
                if (files.contains(directory + "eagle.mnn")) eagleDirectories.add(directory);
            }
        }
        Collections.sort(entries);
        JSONArray result = new JSONArray();
        String commit = metadata.optString("sha", "");
        if (!commit.matches("[a-fA-F0-9]{40,64}")) throw new IllegalArgumentException("Repository revision could not be verified.");
        for (String name : entries) {
            JSONObject candidate = new JSONObject().put("file", name).put("url", fileUrl(commit, name))
                .put("revision", commit).put("sizeBytes", -1L)
                .put("license", metadata.optJSONObject("cardData") == null ? "" : metadata.optJSONObject("cardData").optString("license", ""));
            for (int i = 0; siblings != null && i < siblings.length(); i++) {
                JSONObject sibling = siblings.optJSONObject(i);
                if (sibling == null || !name.equals(sibling.optString("rfilename"))) continue;
                JSONObject lfs = sibling.optJSONObject("lfs");
                candidate.put("sizeBytes", sibling.optLong("size", lfs == null ? -1 : lfs.optLong("size", -1)));
                if (lfs != null) candidate.put("sha256", lfs.optString("sha256", ""));
            }
            if (name.equals("config.json") || name.endsWith("/config.json")) {
                // The listing's own size for a config.json is a few bytes; the package the user
                // actually downloads is every file beside it (model, weight, tokenizer, and, for
                // an Eagle repo, the draft head), already in hand from the same metadata call.
                String directory = name.substring(0, name.length() - "config.json".length());
                long packageSize = packageSizeBytes(siblings, directory);
                if (packageSize > 0L) candidate.put("sizeBytes", packageSize);
                if (eagleDirectories.contains(directory)) candidate.put("speculative", "eagle");
            }
            // Publisher-specific contract, not a family-name capability guess: the runtime floor a
            // litert-community card states for its files (Qwen3.5 0.15, MiniCPM5-2B 0.16).
            TaiImportProfiles.Match family = repository.startsWith("litert-community/") && name.endsWith(".litertlm")
                ? TaiImportProfiles.match(repository, name) : null;
            if (family != null && family.minimumRuntimeVersion != null)
                candidate.put("minimumRuntimeVersion", family.minimumRuntimeVersion);
            result.put(candidate);
        }
        return result;
    }

    /**
     * The combined size of {@code directory}'s direct files (never a nested subdirectory, so an
     * empty {@code directory} sums the repository root, not every file in the repository), or
     * {@code -1} when none is known.
     */
    private static long packageSizeBytes(@Nullable JSONArray siblings, @NonNull String directory) {
        if (siblings == null) return -1L;
        long total = 0L;
        boolean any = false;
        for (int i = 0; i < siblings.length(); i++) {
            JSONObject sibling = siblings.optJSONObject(i);
            String rfile = sibling == null ? "" : sibling.optString("rfilename", "");
            if (rfile.isEmpty() || !rfile.startsWith(directory)) continue;
            if (rfile.substring(directory.length()).contains("/")) continue;
            JSONObject lfs = sibling.optJSONObject("lfs");
            long size = sibling.optLong("size", lfs == null ? -1L : lfs.optLong("size", -1L));
            if (size > 0L) {
                total += size;
                any = true;
            }
        }
        return any ? total : -1L;
    }

    static boolean safePath(String path) {
        if (path.startsWith("/") || path.contains("\\") || path.indexOf('\0') >= 0) return false;
        for (String segment : path.split("/")) if (segment.equals("..") || segment.equals(".")) return false;
        return true;
    }

    private static String decode(String value) throws Exception { return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8"); }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (Exception impossible) { throw new IllegalArgumentException(impossible); }
    }
}
