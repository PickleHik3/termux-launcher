package com.termux.ai;

final class TaiRuntimeIpc {
    static final String RUNTIME_PROCESS_SUFFIX = ":tai_runtime";

    static final int MSG_REQUEST = 1;
    static final int MSG_RESPONSE = 2;
    static final int MSG_STREAM_EVENT = 3;
    static final int MSG_STREAM_DONE = 4;

    static final String KEY_REQUEST_ID = "requestId";
    static final String KEY_OPERATION = "operation";
    static final String KEY_BODY = "body";
    static final String KEY_BODY_FILE = "bodyFile";
    static final String KEY_RESULT = "result";
    static final String KEY_EVENT = "event";
    static final String KEY_ERROR = "error";
    static final String KEY_MESSAGE = "message";

    static final String OP_STATUS = "status";
    static final String OP_RUNTIME_STATUS = "runtimeStatus";
    static final String OP_LOAD_MODEL = "loadModel";
    static final String OP_UNLOAD_MODEL = "unloadModel";
    static final String OP_KEEP_WARM = "keepWarmRuntime";
    static final String OP_CANCEL = "cancelRuntime";
    static final String OP_OPENAI_CHAT = "openAiChatCompletions";
    static final String OP_OPENAI_CHAT_STREAM = "openAiChatCompletionsStream";
    static final String OP_OPENAI_COMPLETION = "openAiCompletions";
    static final String OP_OPENAI_COMPLETION_STREAM = "openAiCompletionsStream";
    static final String OP_EMBEDDINGS = "embeddings";
    static final String OP_PREFLIGHT = "preflight";
    static final String OP_BENCHMARK = "benchmark";
    /** Speech-to-text; both run on the service's own STT lane, never behind a chat generation. */
    static final String OP_TRANSCRIBE = "transcribe";
    static final String OP_STT_WARM = "sttWarm";

    private TaiRuntimeIpc() {
    }
}
