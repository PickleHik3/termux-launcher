package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The four KittenTTS nano 0.8 voices the launcher offers (Bruno, Hugo, Jasper and Rosie) and the
 * rules for naming one in a request. {@code voices.npz} holds eight voices; the other four (Bella,
 * Luna, Kiki, Leo) are deliberately not offered, so a request that names them is refused like any
 * other unknown voice. OpenAI's voice names are accepted as aliases, because OpenAI clients send
 * {@code alloy} when the caller never picked a voice, and refusing that would make every stock
 * client fail on its first request.
 *
 * <p>Each voice carries the speed prior from the model repo's {@code say.py}: the model was tuned
 * to speak at {@code 0.8} (Hugo {@code 0.9}) of its raw rate, and the user's speed multiplies that
 * prior rather than replacing it, so 1.0 always means "the voice as its authors meant it".
 */
public final class TaiTtsVoices {
    public static final String BRUNO = "Bruno";
    public static final String HUGO = "Hugo";
    public static final String JASPER = "Jasper";
    public static final String ROSIE = "Rosie";
    public static final String DEFAULT_VOICE = JASPER;
    /** The order the settings screen lists them in. */
    public static final String[] VOICES = {BRUNO, HUGO, JASPER, ROSIE};

    public static final float DEFAULT_SPEED = 1.0f;
    public static final float MIN_SPEED = 0.5f;
    public static final float MAX_SPEED = 2.0f;

    private static final Map<String, String> NPY_KEYS;
    private static final Map<String, String> OPENAI_ALIASES;

    static {
        HashMap<String, String> keys = new HashMap<>();
        keys.put(JASPER, "expr-voice-2-m");
        keys.put(BRUNO, "expr-voice-3-m");
        keys.put(HUGO, "expr-voice-4-m");
        keys.put(ROSIE, "expr-voice-4-f");
        NPY_KEYS = Collections.unmodifiableMap(keys);

        HashMap<String, String> aliases = new HashMap<>();
        aliases.put("alloy", JASPER);
        aliases.put("sage", JASPER);
        aliases.put("verse", JASPER);
        aliases.put("echo", BRUNO);
        aliases.put("onyx", BRUNO);
        aliases.put("ballad", BRUNO);
        aliases.put("fable", HUGO);
        aliases.put("ash", HUGO);
        aliases.put("nova", ROSIE);
        aliases.put("shimmer", ROSIE);
        aliases.put("coral", ROSIE);
        OPENAI_ALIASES = Collections.unmodifiableMap(aliases);
    }

    private TaiTtsVoices() {}

    /**
     * The voice a request names, as one of {@link #VOICES}: one of the four by name in any case,
     * an OpenAI alias, or {@code fallback} when the request names none. {@code null} means the
     * name is not a voice this app offers.
     */
    @Nullable
    public static String resolve(@Nullable String requested, @NonNull String fallback) {
        if (requested == null || requested.trim().isEmpty()) {
            String stored = canonical(fallback);
            return stored != null ? stored : DEFAULT_VOICE;
        }
        String canonical = canonical(requested);
        if (canonical != null) return canonical;
        return OPENAI_ALIASES.get(requested.trim().toLowerCase(Locale.ROOT));
    }

    /** One of the four offered voices by name, case-insensitively; {@code null} otherwise. */
    @Nullable
    public static String canonical(@Nullable String name) {
        if (name == null) return null;
        String wanted = name.trim();
        for (String voice : VOICES) {
            if (voice.equalsIgnoreCase(wanted)) return voice;
        }
        return null;
    }

    /** The entry in {@code voices.npz} (without {@code .npy}) that holds this voice's style rows. */
    @NonNull
    static String npyKey(@NonNull String voice) {
        String key = NPY_KEYS.get(voice);
        if (key == null) throw new IllegalArgumentException("Not an offered voice: " + voice);
        return key;
    }

    /** The voice's speed prior from the model repo; the user's speed multiplies it. */
    static float speedPrior(@NonNull String voice) {
        return HUGO.equals(voice) ? 0.9f : 0.8f;
    }

    /** A requested speed inside what the model can say intelligibly; NaN and non-positive read as normal. */
    public static float clampSpeed(double speed) {
        if (Double.isNaN(speed) || speed <= 0.0) return DEFAULT_SPEED;
        return (float) Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }
}
