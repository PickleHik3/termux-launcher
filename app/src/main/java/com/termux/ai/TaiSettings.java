package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaiSettings {
    public static final String PREFS_NAME = "termux_ai";

    public static final int DEFAULT_API_PORT = 41237;
    public static final int MIN_API_PORT = 1024;
    public static final int MAX_API_PORT = 65535;
    private static final int RANDOM_API_PORT_MIN = 41000;
    private static final int RANDOM_API_PORT_RANGE = 18000;

    public static final String KEY_ROLE_DEFAULT_ASSISTANT = "tai_role_default_assistant";
    public static final String KEY_SYSTEM_PROMPT_GENERAL = "tai_system_prompt_general";
    public static final String KEY_MAX_TOKENS = "tai_max_tokens";
    public static final String KEY_TOP_K = "tai_top_k";
    public static final String KEY_TOP_P = "tai_top_p";
    public static final String KEY_TEMPERATURE = "tai_temperature";
    public static final String KEY_ACCELERATOR = "tai_accelerator";
    public static final String KEY_THINKING = "tai_thinking";
    public static final String KEY_SPECULATIVE_DECODING = "tai_speculative_decoding";
    public static final String KEY_CONTEXT_WINDOW = "tai_context_window";
    public static final String KEY_IDLE_UNLOAD_MINUTES = "tai_idle_unload_minutes";
    public static final String KEY_HUGGINGFACE_TOKEN = "tai_huggingface_token";
    public static final String KEY_API_PORT = "tai_api_port";
    public static final String KEY_API_TOKEN = "tai_api_token";
    public static final String KEY_API_BIND_MODE = "tai_api_bind_mode";
    public static final String KEY_API_AUTH_REQUIRED = "tai_api_auth_required";
    public static final String KEY_API_LAN_SESSION_STARTED_AT = "tai_api_lan_session_started_at";
    public static final String KEY_OPENAI_AUTO_LOAD = "tai_openai_auto_load";

    public static final String BIND_MODE_LOCALHOST = "localhost";
    public static final String BIND_MODE_LAN = "lan";

    /** How long a LAN exposure window stays valid before the listener falls back to loopback. */
    public static final long LAN_SESSION_MAX_MS = 12L * 60 * 60 * 1000;

    public static final String LAN_WARNING = "LAN mode is unencrypted development-only exposure: "
        + "requests travel over plain HTTP and the bearer token is visible to anyone on the network. "
        + "It turns itself off 12 hours after being enabled, and rotates the token when it does.";

    private static final String AUTO = "auto";
    private static final String GLOBAL_PARAMETER_PREFIX = "tai_global_parameter.";
    private static final String MODEL_PARAMETER_PREFIX = "tai_model_parameter.";
    private static final String MODEL_SYSTEM_PROMPT_PREFIX = "tai_model_system_prompt.";
    public static final String FIELD_MAX_TOKENS = "max_tokens";
    public static final String FIELD_TOP_K = "top_k";
    public static final String FIELD_TOP_P = "top_p";
    public static final String FIELD_TEMPERATURE = "temperature";
    public static final String FIELD_ACCELERATOR = "accelerator";
    public static final String FIELD_ENABLE_THINKING = "enable_thinking";
    public static final String FIELD_ENABLE_SPECULATIVE_DECODING = "enable_speculative_decoding";
    public static final String FIELD_CONTEXT_WINDOW = "context_window";
    public static final String FIELD_THREAD_COUNT = "thread_count";
    public static final String FIELD_PRECISION = "precision";
    public static final String FIELD_MEMORY_MODE = "memory_mode";
    private static final ParameterSchema LITERT_PARAMETER_SCHEMA = createLiteRtParameterSchema();
    private static final ParameterSchema MNN_PARAMETER_SCHEMA = createMnnParameterSchema();
    private static final String OLD_MODEL_GEMMA_4_E2B_IT = "Gemma-4-E2B-it";
    private static final String OLD_MODEL_GEMMA_4_E4B_IT = "Gemma-4-E4B-it";
    private static final String OLD_MODEL_MOBILE_ACTIONS_270M = "MobileActions-270M";

    private final Context appContext;
    private final SharedPreferences preferences;

    public TaiSettings(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getDefaultAssistantModel() {
        String modelId = preferences.getString(KEY_ROLE_DEFAULT_ASSISTANT, TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        if (modelId == null) modelId = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
        String migratedModelId = migrateBuiltInModelId(modelId);
        if (!migratedModelId.equals(modelId)) {
            preferences.edit().putString(KEY_ROLE_DEFAULT_ASSISTANT, migratedModelId).apply();
            modelId = migratedModelId;
        }
        if (new TaiModelRegistry().getModel(modelId) != null || new TaiModelStore(appContext).getInstalledUserModels().containsKey(modelId)) {
            return modelId;
        }
        preferences.edit().putString(KEY_ROLE_DEFAULT_ASSISTANT, TaiModelRegistry.MODEL_GEMMA_4_E2B_IT).apply();
        return TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    }

    @NonNull
    static String migrateBuiltInModelId(@NonNull String modelId) {
        if (OLD_MODEL_GEMMA_4_E2B_IT.equals(modelId)) return TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
        if (OLD_MODEL_GEMMA_4_E4B_IT.equals(modelId)) return TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;
        if (OLD_MODEL_MOBILE_ACTIONS_270M.equals(modelId)) return TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M;
        return modelId;
    }
    @NonNull
    public TaiRuntimeOptions getRuntimeOptions() {
        return getRuntimeOptions((TaiModelSpec) null);
    }

    @NonNull
    public TaiRuntimeOptions getRuntimeOptions(@Nullable TaiModelSpec model) {
        return getRuntimeOptions(model == null ? TaiModelSpec.BACKEND_LITERT_LM : model.backend,
            model == null ? null : model.id);
    }

    @NonNull
    public TaiRuntimeOptions getRuntimeOptions(@Nullable String backend, @Nullable String modelId) {
        ParameterSchema schema = getParameterSchema(backend);
        return new TaiRuntimeOptions(
            (Integer) resolveParameter(schema, FIELD_MAX_TOKENS, modelId),
            (Integer) resolveParameter(schema, FIELD_TOP_K, modelId),
            (Double) resolveParameter(schema, FIELD_TOP_P, modelId),
            (Double) resolveParameter(schema, FIELD_TEMPERATURE, modelId),
            (String) resolveParameter(schema, FIELD_ACCELERATOR, modelId),
            (Integer) resolveParameter(schema, FIELD_CONTEXT_WINDOW, modelId),
            (Integer) resolveParameter(schema, FIELD_THREAD_COUNT, modelId),
            (String) resolveParameter(schema, FIELD_PRECISION, modelId),
            (String) resolveParameter(schema, FIELD_MEMORY_MODE, modelId),
            (Boolean) resolveParameter(schema, FIELD_ENABLE_THINKING, modelId),
            (Boolean) resolveParameter(schema, FIELD_ENABLE_SPECULATIVE_DECODING, modelId),
            getIdleUnloadMinutes()
        );
    }

    public void setGlobalParameter(@NonNull String field, @Nullable Object value) {
        String key = legacyGlobalPreferenceKey(field);
        if (key == null) throw new IllegalArgumentException("unsupported_parameter");
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) editor.remove(key);
        else editor.putString(key, String.valueOf(value));
        editor.apply();
    }

    public void setGlobalParameter(@Nullable String backend, @NonNull String field, @Nullable Object value) {
        ParameterSpec spec = getParameterSchema(backend).get(field);
        if (spec == null) throw new IllegalArgumentException("unsupported_parameter");
        String key = globalParameterKey(getParameterSchema(backend).backend, field);
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) editor.remove(key);
        else editor.putString(key, String.valueOf(value));
        editor.apply();
    }

    public void setModelParameter(@NonNull String modelId, @NonNull String field, @Nullable Object value) {
        ParameterSpec spec = findParameterSpec(field);
        if (spec == null) throw new IllegalArgumentException("unsupported_parameter");
        String key = modelParameterKey(modelId, field);
        SharedPreferences.Editor editor = preferences.edit();
        if (value == null) editor.remove(key);
        else editor.putString(key, String.valueOf(value));
        editor.apply();
    }

    public void resetModelParameterToGlobal(@NonNull String modelId, @NonNull String field) {
        ParameterSpec spec = findParameterSpec(field);
        if (spec == null) throw new IllegalArgumentException("unsupported_parameter");
        preferences.edit().remove(modelParameterKey(modelId, field)).apply();
    }

    public void resetModelParametersToGlobal(@NonNull String modelId) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String field : LITERT_PARAMETER_SCHEMA.fields().keySet()) {
            editor.remove(modelParameterKey(modelId, field));
        }
        for (String field : MNN_PARAMETER_SCHEMA.fields().keySet()) {
            editor.remove(modelParameterKey(modelId, field));
        }
        editor.remove(modelSystemPromptKey(modelId));
        editor.apply();
    }

    public void setModelSystemPrompt(@NonNull String modelId, @Nullable String prompt) {
        SharedPreferences.Editor editor = preferences.edit();
        if (prompt == null || prompt.trim().isEmpty()) editor.remove(modelSystemPromptKey(modelId));
        else editor.putString(modelSystemPromptKey(modelId), prompt);
        editor.apply();
    }

    public void resetModelSystemPromptToGlobal(@NonNull String modelId) {
        preferences.edit().remove(modelSystemPromptKey(modelId)).apply();
    }

    @NonNull
    public String getSystemPrompt(@Nullable String modelId) {
        if (modelId != null) {
            String modelPrompt = preferences.getString(modelSystemPromptKey(modelId), null);
            if (modelPrompt != null && !modelPrompt.trim().isEmpty()) return modelPrompt;
        }
        return getGeneralSystemPrompt();
    }

    @NonNull
    public static ParameterSchema getParameterSchema(@Nullable String backend) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return MNN_PARAMETER_SCHEMA;
        return LITERT_PARAMETER_SCHEMA;
    }

    public int getIdleUnloadMinutes() {
        String value = preferences.getString(KEY_IDLE_UNLOAD_MINUTES, "10");
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public boolean isOpenAiAutoLoadEnabled() {
        return preferences.getBoolean(KEY_OPENAI_AUTO_LOAD, true);
    }

    @NonNull
    public String getHuggingFaceToken() {
        return preferences.getString(KEY_HUGGINGFACE_TOKEN, "");
    }

    public void setHuggingFaceToken(@NonNull String token) {
        preferences.edit().putString(KEY_HUGGINGFACE_TOKEN, token.trim()).apply();
    }

    public int getApiPort() {
        String value = preferences.getString(KEY_API_PORT, String.valueOf(DEFAULT_API_PORT));
        return normalizeApiPort(value);
    }

    @NonNull
    public String getApiBindMode() {
        return normalizeApiBindMode(preferences.getString(KEY_API_BIND_MODE, BIND_MODE_LOCALHOST));
    }

    public void setApiBindMode(@Nullable String bindMode) {
        String normalized = normalizeApiBindMode(bindMode);
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_API_BIND_MODE, normalized);
        // Entering LAN mode starts a fresh exposure window; leaving it clears the stamp so a later
        // re-enable is never credited with an old session's remaining time.
        editor.putLong(KEY_API_LAN_SESSION_STARTED_AT,
            BIND_MODE_LAN.equals(normalized) ? System.currentTimeMillis() : 0L);
        editor.apply();
    }

    public long getLanSessionStartedAt() {
        return preferences.getLong(KEY_API_LAN_SESSION_STARTED_AT, 0L);
    }

    /**
     * Whether the LAN exposure window has run out.
     *
     * <p>LAN mode speaks plaintext HTTP and the bearer token rides in every request header, so
     * anybody on the network can lift it off the wire and keep it forever. Bounding the window
     * turns that long-lived credential into a session one: after {@link #LAN_SESSION_MAX_MS} the
     * listener drops back to loopback and the token is rotated, so a captured token stops being
     * useful even if the user forgot LAN mode was on.
     *
     * <p>A stamp in the future (clock moved backwards) counts as expired rather than as an
     * unbounded session.
     */
    public boolean isLanSessionExpired() {
        if (!BIND_MODE_LAN.equals(getApiBindMode())) return false;
        long startedAt = getLanSessionStartedAt();
        if (startedAt <= 0L) return true;
        long elapsed = System.currentTimeMillis() - startedAt;
        return elapsed < 0L || elapsed > LAN_SESSION_MAX_MS;
    }

    public void setApiPort(int port) {
        preferences.edit().putString(KEY_API_PORT, String.valueOf(normalizeApiPort(port))).apply();
    }

    public int randomizeApiPort(@NonNull SecureRandom random) {
        int port = RANDOM_API_PORT_MIN + random.nextInt(RANDOM_API_PORT_RANGE);
        setApiPort(port);
        return port;
    }

    /**
     * Whether API requests must carry the bearer token. LAN bind mode always enforces
     * authentication regardless of this preference; the toggle only affects loopback.
     */
    public boolean isApiAuthRequired() {
        return preferences.getBoolean(KEY_API_AUTH_REQUIRED, true);
    }

    public void setApiAuthRequired(boolean required) {
        preferences.edit().putBoolean(KEY_API_AUTH_REQUIRED, required).apply();
    }

    @NonNull
    public String getOrCreateApiToken() {
        String token = preferences.getString(KEY_API_TOKEN, "");
        if (isValidApiToken(token)) return token.trim();
        token = generateApiToken(new SecureRandom());
        setApiToken(token);
        return token;
    }

    public void setApiToken(@NonNull String token) {
        String normalized = normalizeApiToken(token);
        if (normalized.isEmpty()) normalized = generateApiToken(new SecureRandom());
        preferences.edit().putString(KEY_API_TOKEN, normalized).apply();
    }

    @NonNull
    public String rotateApiToken(@NonNull SecureRandom random) {
        String token = generateApiToken(random);
        setApiToken(token);
        return token;
    }

    @NonNull
    public static String generateApiToken(@NonNull SecureRandom random) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder tokenBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            tokenBuilder.append(String.format("%02x", b & 0xff));
        }
        return tokenBuilder.toString();
    }

    @NonNull
    public static String normalizeApiToken(@Nullable String token) {
        if (token == null) return "";
        String value = token.trim();
        if (value.length() < 16) return "";
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return "";
        }
        return value;
    }

    public static boolean isValidApiToken(@Nullable String token) {
        return !normalizeApiToken(token).isEmpty();
    }

    @NonNull
    public static String redactToken(@Nullable String token) {
        return TaiModelSpec.redactToken(token);
    }

    public static int normalizeApiPort(@Nullable String port) {
        if (port == null) return DEFAULT_API_PORT;
        try {
            return normalizeApiPort(Integer.parseInt(port.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_API_PORT;
        }
    }

    public static boolean isValidApiPort(@Nullable String port) {
        if (port == null) return false;
        try {
            int value = Integer.parseInt(port.trim());
            return value >= MIN_API_PORT && value <= MAX_API_PORT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int normalizeApiPort(int port) {
        if (port < MIN_API_PORT || port > MAX_API_PORT) return DEFAULT_API_PORT;
        return port;
    }

    @NonNull
    public static String normalizeApiBindMode(@Nullable String bindMode) {
        if (bindMode == null) return BIND_MODE_LOCALHOST;
        String value = bindMode.trim().toLowerCase();
        if (BIND_MODE_LAN.equals(value)) return BIND_MODE_LAN;
        return BIND_MODE_LOCALHOST;
    }

    @Nullable
    private Object resolveParameter(@NonNull ParameterSchema schema, @NonNull String field, @Nullable String modelId) {
        ParameterSpec spec = schema.get(field);
        if (spec == null) return null;
        if (modelId != null) {
            Object modelValue = spec.parse(preferences.getString(modelParameterKey(modelId, field), AUTO));
            if (modelValue != null) return modelValue;
        }
        String globalKey = globalParameterKey(schema.backend, field);
        Object backendGlobalValue = spec.parse(preferences.getString(globalKey, AUTO));
        if (backendGlobalValue != null) return backendGlobalValue;
        globalKey = legacyGlobalPreferenceKey(field);
        if (globalKey != null) {
            Object globalValue = spec.parse(preferences.getString(globalKey, AUTO));
            if (globalValue != null) return globalValue;
        }
        return null;
    }

    @Nullable
    private static String legacyGlobalPreferenceKey(@NonNull String field) {
        if (FIELD_MAX_TOKENS.equals(field)) return KEY_MAX_TOKENS;
        if (FIELD_TOP_K.equals(field)) return KEY_TOP_K;
        if (FIELD_TOP_P.equals(field)) return KEY_TOP_P;
        if (FIELD_TEMPERATURE.equals(field)) return KEY_TEMPERATURE;
        if (FIELD_ACCELERATOR.equals(field)) return KEY_ACCELERATOR;
        if (FIELD_ENABLE_THINKING.equals(field)) return KEY_THINKING;
        if (FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) return KEY_SPECULATIVE_DECODING;
        if (FIELD_CONTEXT_WINDOW.equals(field)) return KEY_CONTEXT_WINDOW;
        return null;
    }

    @NonNull
    public static String globalParameterKey(@Nullable String backend, @NonNull String field) {
        return GLOBAL_PARAMETER_PREFIX + getParameterSchema(backend).backend + "." + field;
    }

    @NonNull
    public static String modelParameterPreferenceKey(@NonNull String modelId, @NonNull String field) {
        return modelParameterKey(modelId, field);
    }

    @NonNull
    public static String modelSystemPromptPreferenceKey(@NonNull String modelId) {
        return modelSystemPromptKey(modelId);
    }

    @Nullable
    private static ParameterSpec findParameterSpec(@NonNull String field) {
        ParameterSpec spec = LITERT_PARAMETER_SCHEMA.get(field);
        return spec == null ? MNN_PARAMETER_SCHEMA.get(field) : spec;
    }

    @NonNull
    private static String modelParameterKey(@NonNull String modelId, @NonNull String field) {
        return MODEL_PARAMETER_PREFIX + modelId + "." + field;
    }

    @NonNull
    private static String modelSystemPromptKey(@NonNull String modelId) {
        return MODEL_SYSTEM_PROMPT_PREFIX + modelId;
    }

    @NonNull
    private static ParameterSchema createLiteRtParameterSchema() {
        LinkedHashMap<String, ParameterSpec> specs = new LinkedHashMap<>();
        put(specs, ParameterSpec.integer(FIELD_MAX_TOKENS, "4000", 4000, 2000, 32000));
        put(specs, ParameterSpec.integer(FIELD_CONTEXT_WINDOW, "4096", 4096, 1024, 32_768));
        put(specs, ParameterSpec.integer(FIELD_TOP_K, "64", 64, 5, 100));
        put(specs, ParameterSpec.decimal(FIELD_TOP_P, "0.95", 0.95d, 0.0d, 1.0d));
        put(specs, ParameterSpec.decimal(FIELD_TEMPERATURE, "1.00", 1.0d, 0.0d, 2.0d));
        put(specs, ParameterSpec.option(FIELD_ACCELERATOR, "GPU", "GPU", new String[] {"GPU", "CPU"}));
        put(specs, ParameterSpec.bool(FIELD_ENABLE_THINKING, "false", false));
        put(specs, ParameterSpec.bool(FIELD_ENABLE_SPECULATIVE_DECODING, "false", false));
        return new ParameterSchema(TaiModelSpec.BACKEND_LITERT_LM, specs);
    }

    @NonNull
    private static ParameterSchema createMnnParameterSchema() {
        LinkedHashMap<String, ParameterSpec> specs = new LinkedHashMap<>();
        put(specs, ParameterSpec.option(FIELD_ACCELERATOR, "Auto", "Auto", new String[] {"Auto", "CPU", "OpenCL"}));
        put(specs, ParameterSpec.integer(FIELD_CONTEXT_WINDOW, "4096", 4096, 1024, 32_768));
        put(specs, ParameterSpec.integer(FIELD_THREAD_COUNT, "4", 4, 1, 16));
        put(specs, ParameterSpec.option(FIELD_PRECISION, "low", "low", new String[] {"low", "normal", "high"}));
        put(specs, ParameterSpec.option(FIELD_MEMORY_MODE, "low", "low", new String[] {"low", "normal", "high"}));
        put(specs, ParameterSpec.integer(FIELD_MAX_TOKENS, "1024", 1024, 64, 8192));
        put(specs, ParameterSpec.decimal(FIELD_TEMPERATURE, "0.80", 0.80d, 0.0d, 2.0d));
        put(specs, ParameterSpec.decimal(FIELD_TOP_P, "0.90", 0.90d, 0.0d, 1.0d));
        put(specs, ParameterSpec.integer(FIELD_TOP_K, "40", 40, 1, 100));
        return new ParameterSchema(TaiModelSpec.BACKEND_MNN_LLM, specs);
    }

    private static void put(@NonNull LinkedHashMap<String, ParameterSpec> specs, @NonNull ParameterSpec spec) {
        specs.put(spec.field, spec);
    }

    public static final class ParameterSchema {
        @NonNull public final String backend;
        @NonNull private final Map<String, ParameterSpec> specs;

        private ParameterSchema(@NonNull String backend, @NonNull LinkedHashMap<String, ParameterSpec> specs) {
            this.backend = backend;
            this.specs = Collections.unmodifiableMap(new LinkedHashMap<>(specs));
        }

        @Nullable
        public ParameterSpec get(@NonNull String field) {
            return specs.get(field);
        }

        @NonNull
        public Map<String, ParameterSpec> fields() {
            return specs;
        }
    }

    public static final class ParameterSpec {
        static final String TYPE_INTEGER = "integer";
        static final String TYPE_DECIMAL = "decimal";
        static final String TYPE_OPTION = "option";
        static final String TYPE_BOOLEAN = "boolean";

        @NonNull public final String field;
        @NonNull public final String type;
        @NonNull public final String defaultValue;
        @NonNull public final Object fallbackValue;
        @Nullable public final Double minValue;
        @Nullable public final Double maxValue;
        @NonNull public final String[] options;

        private ParameterSpec(
            @NonNull String field,
            @NonNull String type,
            @NonNull String defaultValue,
            @NonNull Object fallbackValue,
            @Nullable Double minValue,
            @Nullable Double maxValue,
            @NonNull String[] options
        ) {
            this.field = field;
            this.type = type;
            this.defaultValue = defaultValue;
            this.fallbackValue = fallbackValue;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.options = options.clone();
        }

        @NonNull
        static ParameterSpec integer(@NonNull String field, @NonNull String defaultValue, int fallbackValue, int min, int max) {
            return new ParameterSpec(field, TYPE_INTEGER, defaultValue, fallbackValue, (double) min, (double) max, new String[0]);
        }

        @NonNull
        static ParameterSpec decimal(@NonNull String field, @NonNull String defaultValue, double fallbackValue, double min, double max) {
            return new ParameterSpec(field, TYPE_DECIMAL, defaultValue, fallbackValue, min, max, new String[0]);
        }

        @NonNull
        static ParameterSpec option(@NonNull String field, @NonNull String defaultValue, @NonNull String fallbackValue, @NonNull String[] options) {
            return new ParameterSpec(field, TYPE_OPTION, defaultValue, fallbackValue, null, null, options);
        }

        @NonNull
        static ParameterSpec bool(@NonNull String field, @NonNull String defaultValue, boolean fallbackValue) {
            return new ParameterSpec(field, TYPE_BOOLEAN, defaultValue, fallbackValue, null, null, new String[0]);
        }

        @Nullable
        public Object parse(@Nullable String rawValue) {
            if (rawValue == null) return null;
            String value = rawValue.trim();
            if (value.isEmpty() || AUTO.equalsIgnoreCase(value)) return null;
            if (TYPE_INTEGER.equals(type)) return parseInteger(value);
            if (TYPE_DECIMAL.equals(type)) return parseDecimal(value);
            if (TYPE_OPTION.equals(type)) return parseOption(value);
            if (TYPE_BOOLEAN.equals(type)) return parseBoolean(value);
            return null;
        }

        @Nullable
        private Integer parseInteger(@NonNull String value) {
            try {
                int parsed = Integer.parseInt(value);
                if (minValue != null && parsed < minValue) return null;
                if (maxValue != null && parsed > maxValue) return null;
                return parsed;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Nullable
        private Double parseDecimal(@NonNull String value) {
            try {
                double parsed = Double.parseDouble(value);
                if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return null;
                if (minValue != null && parsed < minValue) return null;
                if (maxValue != null && parsed > maxValue) return null;
                return parsed;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Nullable
        private String parseOption(@NonNull String value) {
            for (String option : options) {
                if (option.equalsIgnoreCase(value)) return option;
            }
            return null;
        }

        @Nullable
        private Boolean parseBoolean(@NonNull String value) {
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            return null;
        }
    }

    @NonNull
    public String getGeneralSystemPrompt() {
        return preferences.getString(KEY_SYSTEM_PROMPT_GENERAL,
            "You are TAI, Termux AI, a local assistant integrated with Termux Launcher. Prefer safe, reviewable actions.");
    }

    @NonNull
    public JSONObject getRoleAssignmentsJson() throws JSONException {
        JSONObject roles = new JSONObject();
        roles.put(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, getDefaultAssistantModel());
        return roles;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("roles", getRoleAssignmentsJson());
        json.put("runtimeOptions", getRuntimeOptions().toJson());
        json.put("idleUnloadMinutes", getIdleUnloadMinutes());
        json.put("openAiAutoLoadEnabled", isOpenAiAutoLoadEnabled());
        json.put("huggingFaceTokenConfigured", !getHuggingFaceToken().trim().isEmpty());
        json.put("apiPort", getApiPort());
        String bindMode = getApiBindMode();
        json.put("bindMode", bindMode);
        if (BIND_MODE_LAN.equals(bindMode)) {
            json.put("lanWarning", LAN_WARNING);
            json.put("lanSessionStartedAt", getLanSessionStartedAt());
            json.put("lanSessionExpiresAt", getLanSessionStartedAt() <= 0L
                ? 0L : getLanSessionStartedAt() + LAN_SESSION_MAX_MS);
            json.put("lanSessionExpired", isLanSessionExpired());
        }
        String configuredToken = preferences.getString(KEY_API_TOKEN, "");
        json.put("apiTokenConfigured", isValidApiToken(configuredToken));
        json.put("tokenConfigured", isValidApiToken(configuredToken));
        int port = getApiPort();
        json.put("baseUrl", "http://127.0.0.1:" + port);
        json.put("openAiBaseUrl", "http://127.0.0.1:" + port + "/v1");
        JSONArray supportedEndpoints = new JSONArray();
        supportedEndpoints.put("/v1/models");
        supportedEndpoints.put("/v1/chat/completions");
        supportedEndpoints.put("/v1/completions");
        supportedEndpoints.put("/v1/embeddings");
        supportedEndpoints.put("/v1/audio/speech");
        json.put("supportedEndpoints", supportedEndpoints);
        json.put("audioOutputNote", "Audio output returns an explicit unsupported_audio_output error until a local runner exposes generated audio.");
        json.put("embeddingsNote", "Embeddings support is model-capability dependent.");
        json.put("autoGenerationDefaultState", "nullable generation overrides use model profile or MNN config defaults in the selected runtime");
        return json;
    }

}
