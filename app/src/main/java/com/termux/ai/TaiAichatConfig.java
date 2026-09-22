package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;

/** Generates a client entry from the same discovery response clients actually receive. */
public final class TaiAichatConfig {
    private TaiAichatConfig() {}

    public static String format(String baseUrl, String token, String modelId, JSONObject discovery) {
        JSONArray models = discovery.optJSONArray("data");
        JSONObject model = null;
        for (int i = 0; models != null && i < models.length(); i++) {
            JSONObject candidate = models.optJSONObject(i);
            if (candidate != null && modelId.equals(candidate.optString("id"))) { model = candidate; break; }
        }
        if (model == null || !has(model.optJSONArray("_capabilities"), "text_chat"))
            throw new IllegalArgumentException("This model is not available for chat.");
        StringBuilder yaml = new StringBuilder("- type: openai-compatible\n  name: tai\n  api_base: ");
        yaml.append(JSONObject.quote(baseUrl)).append("\n  api_key: ").append(JSONObject.quote(token))
            .append("\n  models:\n  - name: ").append(JSONObject.quote(modelId))
            .append("\n    max_input_tokens: ").append(model.optInt("_endpoint_context_window", 4096)).append('\n');
        if (has(model.optJSONArray("_capabilities"), "image_input")) yaml.append("    supports_vision: true\n");
        return yaml.toString();
    }

    private static boolean has(JSONArray array, String value) {
        for (int i = 0; array != null && i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }
}
