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
    static final String OP_TOKENIZE = "tokenize";
    static final String OP_PREFLIGHT = "preflight";
    static final String OP_BENCHMARK = "benchmark";
    /**
     * The token-timed benchmark ({@link TaiBenchHarness}): a stream, one event per phase and a
     * throttled token feed, running for minutes on the serial lane. While it runs the service
     * refuses the chat-lane operations ({@link TaiRuntimeService#isRefusedDuringBench}).
     */
    static final String OP_BENCH_RUN = "benchRun";
    /**
     * Ends the active bench's current or next cool-down wait at once (slice 2's "Skip the wait").
     * Runs on the control lane like {@link #OP_CANCEL}, never refused while a bench holds the
     * serial lane ({@link TaiRuntimeService#isRefusedDuringBench}).
     */
    static final String OP_BENCH_SKIP_WAIT = "benchSkipWait";
    /**
     * Holds or releases the active bench's guard for "Leaving pauses at the end of the current
     * step; Resume on return" (slice 4's screen/app hold). Body: {@code {"held":true|false}}. Runs
     * on the control lane like {@link #OP_CANCEL}, never refused while a bench holds the serial
     * lane ({@link TaiRuntimeService#isRefusedDuringBench}).
     */
    static final String OP_BENCH_HOLD = "benchHold";
    /** Speech-to-text; both run on the service's own STT lane, never behind a chat generation. */
    static final String OP_TRANSCRIBE = "transcribe";
    static final String OP_STT_WARM = "sttWarm";
    /**
     * Speech output. Speak, synthesize (a stream: one event per sentence file, then the summary)
     * and warm run on the service's own TTS lane; stop runs on the control lane so it reaches a
     * sentence in progress instead of queuing behind it.
     */
    static final String OP_TTS_SPEAK = "ttsSpeak";
    static final String OP_TTS_SYNTHESIZE = "ttsSynthesize";
    static final String OP_TTS_WARM = "ttsWarm";
    static final String OP_TTS_STOP = "ttsStop";

    /**
     * Text-to-image. Generate is a stream (progress events, then the result with the PNG's path
     * under cacheDir/tai-ipc) on the service's own image lane; cancel runs on the control lane so
     * it reaches a run in progress instead of queuing behind it.
     */
    static final String OP_IMAGE_GENERATE = "imageGenerate";
    static final String OP_IMAGE_CANCEL = "imageCancel";

    private TaiRuntimeIpc() {
    }
}
