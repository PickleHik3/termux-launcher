package com.termux.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Package dependencies are relative to the selected config, never the whole HF repository. */
final class TaiMnnPackage {
    static LinkedHashSet<String> files(JSONObject config, Set<String> available) throws Exception {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("config.json");
        add(result, config.optString("llm_model", "llm.mnn"));
        add(result, config.optString("llm_weight", "llm.mnn.weight"));
        String tokenizer = config.optString("tokenizer_file", "");
        if (tokenizer.isEmpty()) tokenizer = available.contains("tokenizer.mtok") ? "tokenizer.mtok" : "tokenizer.txt";
        add(result, tokenizer);
        references(config, result);
        // MNN also consumes these conventional sidecars without naming them in config.json.
        for (String name : available) {
            if (name.indexOf('/') < 0 && (name.equals("llm_config.json") || name.equals("llm.mnn.json")
                || name.startsWith("visual.") || name.startsWith("audio.") || name.startsWith("embeddings_"))) add(result, name);
        }
        return result;
    }

    static void references(Object value, Set<String> result) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject json = (JSONObject) value;
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) references(json.get(keys.next()), result);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) references(array.get(i), result);
        } else if (value instanceof String && packageFile((String) value)) add(result, (String) value);
    }

    private static boolean packageFile(String path) {
        return path.matches("[^\\r\\n<>]*\\.(mnn|weight|mtok|bin|json|txt|model)");
    }

    private static void add(Set<String> result, String path) throws IOException {
        if (path.isEmpty()) return;
        if (!TaiHuggingFace.safePath(path) || path.contains(":") || !packageFile(path))
            throw new IOException("Invalid model dependency: " + path);
        result.add(path);
    }

    static JSONObject readConfig(File file) throws Exception {
        if (file.length() > 2L * 1024 * 1024) throw new IOException("Model configuration is too large.");
        return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    static void validate(File configFile) throws Exception {
        File directory = configFile.getParentFile();
        LinkedHashSet<String> available = new LinkedHashSet<>();
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) available.add(child.getName());
        LinkedHashSet<String> required = files(readConfig(configFile), available);
        for (String name : new LinkedHashSet<>(required)) {
            File dependency = new File(directory, name);
            if (name.endsWith(".json") && dependency.isFile()) references(readConfig(dependency), required);
        }
        for (String name : required) {
            File dependency = new File(directory, name);
            if (!dependency.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator)
                || !dependency.isFile() || !dependency.canRead() || dependency.length() == 0)
                throw new IOException("Model package is missing " + name);
        }
    }
}
