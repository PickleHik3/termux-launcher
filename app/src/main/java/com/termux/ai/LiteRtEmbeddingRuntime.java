package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.util.List;

final class LiteRtEmbeddingRuntime implements AutoCloseable {
    /** Gemma sentencepiece control ids; the sentencepiece4j binding does not expose bosId()/eosId(). */
    private static final int TOKEN_BOS = 2;
    private static final int TOKEN_EOS = 1;

    @Nullable private Interpreter interpreter;
    @Nullable private SentencePieceBpeTokenizer tokenizer;
    @Nullable private String loadedModelPath;
    @Nullable private String loadedTokenizerPath;
    private int sequenceLength = 0;
    private int outputDimensions = 0;

    @NonNull
    synchronized JSONObject embed(@NonNull TaiModelSpec spec, @NonNull List<String> inputs, int dimensions) throws JSONException {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Embedding model file is missing.");
        }
        if (dimensions < 0) {
            return error(400, "invalid_dimensions", "Embedding dimensions must be positive.");
        }
        File modelFile = new File(spec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            return error(404, "model_file_not_readable", "Embedding model file is missing or unreadable.");
        }
        File tokenizerFile = tokenizerFileFor(modelFile);
        if (tokenizerFile == null) {
            return error(409, "embedding_tokenizer_missing",
                "EmbeddingGemma requires sentencepiece.model next to the .tflite model file.");
        }
        try {
            ensureLoaded(modelFile, tokenizerFile);
            int effectiveDimensions = dimensions > 0 ? dimensions : outputDimensions;
            if (effectiveDimensions <= 0 || effectiveDimensions > outputDimensions) {
                return error(400, "invalid_dimensions",
                    "Requested embedding dimensions exceed model output dimensions.");
            }
            JSONArray data = new JSONArray();
            int promptTokens = 0;
            for (int i = 0; i < inputs.size(); i++) {
                Embedding embedding = embedOne(inputs.get(i), effectiveDimensions);
                promptTokens += embedding.tokens;
                JSONObject item = new JSONObject();
                item.put("object", "embedding");
                item.put("index", i);
                JSONArray vector = new JSONArray();
                for (float value : embedding.vector) vector.put((double) value);
                item.put("embedding", vector);
                data.put(item);
            }
            JSONObject usage = new JSONObject();
            usage.put("prompt_tokens", promptTokens);
            usage.put("total_tokens", promptTokens);
            JSONObject response = new JSONObject();
            response.put("object", "list");
            response.put("data", data);
            response.put("model", spec.id);
            response.put("usage", usage);
            response.put("_backend", TaiModelSpec.BACKEND_LITERT_LM);
            response.put("_runtime", "litert-embedding");
            return response;
        } catch (Throwable t) {
            return error(500, "embedding_inference_failed",
                "LiteRT embedding inference failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private void ensureLoaded(@NonNull File modelFile, @NonNull File tokenizerFile) throws Exception {
        String modelPath = modelFile.getAbsolutePath();
        String tokenizerPath = tokenizerFile.getAbsolutePath();
        if (interpreter != null && tokenizer != null
            && modelPath.equals(loadedModelPath) && tokenizerPath.equals(loadedTokenizerPath)) {
            return;
        }
        close();
        tokenizer = SentencePieceBpeTokenizer.fromModelFile(tokenizerFile);
        Interpreter.Options options = new Interpreter.Options()
            .setNumThreads(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));
        interpreter = new Interpreter(modelFile, options);
        int[] inputShape = interpreter.getInputTensor(0).shape();
        int[] outputShape = interpreter.getOutputTensor(0).shape();
        sequenceLength = inputShape.length >= 2 ? inputShape[inputShape.length - 1] : 1024;
        outputDimensions = outputShape.length >= 2 ? outputShape[outputShape.length - 1] : 768;
        // BOS + EOS framing needs at least two slots in every input row.
        if (sequenceLength < 2) {
            close();
            throw new IllegalStateException("Embedding model reports an unusable sequence length: " + sequenceLength);
        }
        if (outputDimensions <= 0) {
            close();
            throw new IllegalStateException("Embedding model reports an unusable output dimension: " + outputDimensions);
        }
        loadedModelPath = modelPath;
        loadedTokenizerPath = tokenizerPath;
    }

    @NonNull
    private Embedding embedOne(@NonNull String text, int dimensions) {
        if (interpreter == null || tokenizer == null) throw new IllegalStateException("Embedding runtime is not loaded.");
        int[] ids = tokenizer.encode(text);
        // Truncate the encoded body, never the framing: BOS and EOS must both survive.
        int bodyTokens = Math.max(0, Math.min(ids.length, sequenceLength - 2));
        int usedTokens = bodyTokens + 2;
        int[][] tokenIds = new int[1][sequenceLength];
        tokenIds[0][0] = TOKEN_BOS;
        for (int i = 0; i < bodyTokens; i++) tokenIds[0][i + 1] = ids[i];
        tokenIds[0][bodyTokens + 1] = TOKEN_EOS;
        float[][] output = new float[1][outputDimensions];
        interpreter.run(tokenIds, output);
        float[] vector = output[0];
        if (dimensions == vector.length) return new Embedding(normalize(vector), usedTokens);
        float[] truncated = new float[dimensions];
        System.arraycopy(vector, 0, truncated, 0, dimensions);
        return new Embedding(normalize(truncated), usedTokens);
    }

    @NonNull
    private float[] normalize(@NonNull float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) sumSquares += (double) value * value;
        double norm = Math.sqrt(sumSquares);
        if (norm > 0.0) {
            for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    @Nullable
    private File tokenizerFileFor(@NonNull File modelFile) {
        File dir = modelFile.getParentFile();
        if (dir == null) return null;
        File sentencePiece = new File(dir, "sentencepiece.model");
        return sentencePiece.isFile() && sentencePiece.canRead() ? sentencePiece : null;
    }

    @NonNull
    private JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", status >= 500 ? "server_error" : "invalid_request_error");
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }

    @Override
    public synchronized void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        tokenizer = null;
        loadedModelPath = null;
        loadedTokenizerPath = null;
        sequenceLength = 0;
        outputDimensions = 0;
    }

    private static final class Embedding {
        @NonNull final float[] vector;
        final int tokens;

        private Embedding(@NonNull float[] vector, int tokens) {
            this.vector = vector;
            this.tokens = tokens;
        }
    }
}
