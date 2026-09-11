package com.termux.ai;

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

    public JSONArray candidates(JSONObject metadata) throws Exception {
        JSONArray siblings = metadata.optJSONArray("siblings");
        LinkedHashSet<String> files = new LinkedHashSet<>();
        if (siblings != null) for (int i = 0; i < siblings.length(); i++) {
            JSONObject item = siblings.optJSONObject(i);
            if (item != null && safePath(item.optString("rfilename"))) files.add(item.optString("rfilename"));
        }
        List<String> entries = new ArrayList<>();
        for (String name : files) {
            if (file ? !name.equals(path) : !path.isEmpty() && !name.startsWith(path.replaceAll("/$", "") + "/")) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".litertlm") || lower.endsWith(".task") || lower.endsWith(".tflite")) entries.add(name);
            else if (name.equals("config.json") || name.endsWith("/config.json")) {
                String directory = name.substring(0, name.length() - "config.json".length());
                for (String other : files) {
                    if (other.startsWith(directory) && other.endsWith(".mnn")) { entries.add(name); break; }
                }
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
            // Publisher-specific contract, not a family-name capability guess. See the research report.
            if (repository.equals("litert-community/Qwen3.5-2B") && name.endsWith(".litertlm"))
                candidate.put("minimumRuntimeVersion", "0.15.0");
            result.put(candidate);
        }
        return result;
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
