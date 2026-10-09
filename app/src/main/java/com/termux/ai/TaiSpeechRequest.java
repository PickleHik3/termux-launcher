package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Locale;

/**
 * One speech-output request as the API, the CLI and the app's own buttons send it, checked and
 * normalised in one place: {@code input} (OpenAI's name; {@code text} is accepted too), {@code voice}
 * (one of {@link TaiTtsVoices#VOICES}, an OpenAI voice name, or the settings' voice when absent),
 * {@code speed} (clamped to {@link TaiTtsVoices#MIN_SPEED}–{@link TaiTtsVoices#MAX_SPEED}, the
 * settings' speed when absent), {@code response_format} ({@code wav} or {@code pcm}; OpenAI's
 * compressed formats are refused rather than silently answered with something else) and
 * {@code model}. Pure, so the rules are unit-tested.
 */
final class TaiSpeechRequest {
    static final String FORMAT_WAV = "wav";
    static final String FORMAT_PCM = "pcm";
    /** OpenAI's limit for {@code /v1/audio/speech}. */
    static final int MAX_API_CHARS = 4096;
    /** {@code tai speak}, Read aloud and the sample button: long enough for a screenful of terminal text. */
    static final int MAX_SPEAK_CHARS = 20_000;

    @NonNull final String text;
    @NonNull final String voice;
    final float speed;
    @NonNull final String format;
    @NonNull final String model;

    /** Set when the request is refused: the OpenAI error code, message, parameter and HTTP status. */
    @Nullable final String errorCode;
    @Nullable final String errorMessage;
    @Nullable final String errorParam;
    final int statusCode;

    private TaiSpeechRequest(@NonNull String text, @NonNull String voice, float speed, @NonNull String format,
                             @NonNull String model, @Nullable String errorCode, @Nullable String errorMessage,
                             @Nullable String errorParam, int statusCode) {
        this.text = text;
        this.voice = voice;
        this.speed = speed;
        this.format = format;
        this.model = model;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorParam = errorParam;
        this.statusCode = statusCode;
    }

    boolean isValid() {
        return errorCode == null;
    }

    @NonNull
    static TaiSpeechRequest parse(@NonNull JSONObject request, @NonNull String defaultVoice, float defaultSpeed, int maxChars) {
        String text = request.has("input") ? request.optString("input", "") : request.optString("text", "");
        String model = request.optString("model", "").trim();
        String format = request.optString("response_format", FORMAT_WAV).trim().toLowerCase(Locale.ROOT);
        if (format.isEmpty()) format = FORMAT_WAV;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return refused("tts_input_missing", "Provide the text to speak as 'input'.", "input", 400);
        }
        if (trimmed.length() > maxChars) {
            return refused("tts_input_too_long", "The text is " + trimmed.length() + " characters; the limit is " + maxChars + ".", "input", 400);
        }
        if (!FORMAT_WAV.equals(format) && !FORMAT_PCM.equals(format)) {
            return refused("unsupported_response_format", "response_format must be wav or pcm.", "response_format", 400);
        }
        String requestedVoice = request.optString("voice", "").trim();
        String voice = TaiTtsVoices.resolve(requestedVoice, defaultVoice);
        if (voice == null) {
            return refused("tts_voice_not_found", "Unknown voice '" + requestedVoice + "'. Choose Bruno, Hugo, Jasper or Rosie.", "voice", 400);
        }
        float speed = defaultSpeed;
        if (request.has("speed") && !request.isNull("speed")) {
            double requested = request.optDouble("speed", Double.NaN);
            if (Double.isNaN(requested)) {
                return refused("tts_speed_invalid", "speed must be a number between " + TaiTtsVoices.MIN_SPEED
                    + " and " + TaiTtsVoices.MAX_SPEED + ".", "speed", 400);
            }
            speed = TaiTtsVoices.clampSpeed(requested);
        } else {
            speed = TaiTtsVoices.clampSpeed(speed);
        }
        return new TaiSpeechRequest(trimmed, voice, speed, format, model, null, null, null, 200);
    }

    @NonNull
    private static TaiSpeechRequest refused(@NonNull String code, @NonNull String message, @NonNull String param, int status) {
        return new TaiSpeechRequest("", TaiTtsVoices.DEFAULT_VOICE, TaiTtsVoices.DEFAULT_SPEED, FORMAT_WAV, "",
            code, message, param, status);
    }

    /**
     * Whether {@code model} leaves the choice to the phone: empty, or one of OpenAI's speech model
     * names, which stock clients send without being asked.
     */
    static boolean isDefaultModelAlias(@NonNull String model) {
        String id = model.trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() || id.equals("tts-1") || id.equals("tts-1-hd") || id.equals("gpt-4o-mini-tts")
            || id.equals("default") || id.equals("kitten");
    }
}
