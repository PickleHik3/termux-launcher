package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Host-side replay of the on-device voice pipeline: a WAV (a whole mic session, 16 kHz mono
 * PCM16) through the real {@link VoiceActivityDetector} exactly as {@code VoiceInputSession.capture}
 * feeds it, {@link VoiceGain}, a Python Whisper server for the transcript, and the real
 * {@link VoiceTextSanitizer} classification — a printed event log per clip, plus a pass/fail
 * against a {@code <clip>.expect} file when one sits next to the WAV.
 *
 * <p>Skipped ({@link Assume}) unless {@code -Dvoice.replay.in} names a WAV file or a directory of
 * them; {@code app/build.gradle} forwards the {@code voice.replay.*} system properties to the test
 * JVM. See {@code project-docs/plans/whisper-voice-input.md} "Replay rig" for the exact commands.
 *
 * <ul>
 *   <li>{@code voice.replay.in} — a WAV file or a directory of WAVs (required to run this test)</li>
 *   <li>{@code voice.replay.model} — {@code base} (default), {@code small}, or {@code parakeet}
 *       (NVIDIA parakeet-tdt-0.6b-v3, LiteRT int8 stateful 5 s, from
 *       {@code ~/.cache/termux-launcher/parakeet/}; see project-docs/parakeet-stt-research.md)</li>
 *   <li>{@code voice.replay.pause} — the VAD pause in ms, {@code VoiceInputSession}'s own default 600</li>
 *   <li>{@code voice.replay.out} — where the report and per-segment WAVs land; default {@code app/build/voice-replay}</li>
 *   <li>{@code voice.replay.python} — the interpreter; default {@code ~/.cache/termux-launcher/venv/bin/python}</li>
 * </ul>
 */
public class VoiceReplayRig {

    /** The window {@code VoiceInputSession.Config} carries by default ({@code TaiSettings.DEFAULT_STT_WINDOW_SECONDS}). */
    private static final int WINDOW_SECONDS = 10;

    private Process server;

    @After
    public void stopServer() {
        if (server == null) return;
        try {
            server.getOutputStream().close();
        } catch (IOException ignored) {
        }
        try {
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.destroyForcibly();
        }
    }

    @Test
    public void replay() throws Exception {
        String inProperty = System.getProperty("voice.replay.in", "").trim();
        Assume.assumeTrue("set -Dvoice.replay.in=<wav-or-dir> to run the voice replay rig (see "
            + "project-docs/plans/whisper-voice-input.md \"Replay rig\")", !inProperty.isEmpty());
        File in = new File(inProperty);
        Assume.assumeTrue("voice.replay.in does not exist: " + in, in.exists());

        String modelChoice = System.getProperty("voice.replay.model", "base").trim();
        int pauseMs = Integer.parseInt(System.getProperty("voice.replay.pause", "600").trim());
        File outDir = new File(System.getProperty("voice.replay.out", "app/build/voice-replay"));
        String python = System.getProperty("voice.replay.python", defaultPythonPath());

        boolean parakeet = "parakeet".equals(modelChoice);
        File modelDir = parakeet
            ? new File(System.getProperty("user.home"), ".cache/termux-launcher/parakeet")
            : new File(System.getProperty("user.home"),
                ".cache/termux-launcher/whisper/whisper-acft-" + ("small".equals(modelChoice) ? "small" : "base") + "-en");
        File modelFile = new File(modelDir, parakeet ? "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite"
            : "small".equals(modelChoice) ? "acft_whisper_small.en_10s_drq.tflite" : "acft_whisper_base.en_10s_drq.tflite");
        File tokenizerFile = new File(modelDir, "tokenizer.json");
        Assume.assumeTrue("model not installed: " + modelFile, modelFile.isFile());
        Assume.assumeTrue("tokenizer not found: " + tokenizerFile, tokenizerFile.isFile());

        List<File> wavFiles = collectWavFiles(in);
        Assume.assumeTrue("no .wav files under " + in, !wavFiles.isEmpty());

        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IOException("cannot create " + outDir);
        }

        server = parakeet ? startParakeetServer(python, modelFile, tokenizerFile)
            : startServer(python, modelFile, tokenizerFile, BiasPrompt.build(tokenizerFile));
        SubprocessTranscriber transcriber = new SubprocessTranscriber(server, outDir);
        VoiceReplayCore.ReplayConfig config = new VoiceReplayCore.ReplayConfig(pauseMs, WINDOW_SECONDS);

        StringBuilder report = new StringBuilder();
        boolean anyExpectations = false;
        boolean anyMismatch = false;
        for (File wav : wavFiles) {
            short[] pcm;
            try {
                pcm = VoiceReplayCore.readWav16kMono(wav);
            } catch (IOException e) {
                String line = "clip " + wav.getName() + "\n  SKIPPED: " + e.getMessage() + "\n";
                System.out.print(line);
                report.append(line);
                continue;
            }
            File expectFile = new File(wav.getParentFile(), stripExtension(wav.getName()) + ".expect");
            List<String> expected = expectFile.isFile() ? VoiceReplayCore.parseExpectations(expectFile) : null;
            VoiceReplayCore.ClipResult result = VoiceReplayCore.replay(wav.getName(), pcm, config, transcriber, expected);
            String log = result.toLog();
            System.out.print(log);
            report.append(log);
            if (result.expectation != null) {
                anyExpectations = true;
                if (!result.expectation.passed) anyMismatch = true;
            }
        }
        File reportFile = new File(outDir, "report.txt");
        try (Writer writer = new FileWriter(reportFile)) {
            writer.write(report.toString());
        }
        System.out.println("voice replay report written to " + reportFile.getAbsolutePath());
        if (anyExpectations && anyMismatch) {
            org.junit.Assert.fail("one or more clips did not match their .expect file; see " + reportFile.getAbsolutePath());
        }
    }

    @NonNull
    private static String defaultPythonPath() {
        return System.getProperty("user.home") + "/.cache/termux-launcher/venv/bin/python";
    }

    @NonNull
    private static List<File> collectWavFiles(@NonNull File in) {
        if (in.isFile()) {
            return in.getName().toLowerCase(Locale.ROOT).endsWith(".wav")
                ? Collections.singletonList(in) : Collections.emptyList();
        }
        File[] children = in.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if (children == null) return Collections.emptyList();
        List<File> files = new ArrayList<>(Arrays.asList(children));
        files.sort((a, b) -> a.getName().compareTo(b.getName()));
        return files;
    }

    @NonNull
    private static String stripExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @NonNull
    private static Process startServer(@NonNull String python, @NonNull File modelFile, @NonNull File tokenizerFile,
                                       @NonNull BiasPrompt prompt) throws IOException {
        File script = findScript();
        List<String> command = new ArrayList<>(Arrays.asList(python, script.getAbsolutePath(),
            modelFile.getAbsolutePath(), tokenizerFile.getAbsolutePath(),
            joinInts(prompt.prompt), String.valueOf(prompt.endOfText), String.valueOf(prompt.timestampBegin),
            joinInts(prompt.always)));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }

    /**
     * The Gradle test task's working directory is the {@code app} module, but {@code scripts/}
     * sits at the repo root; this tries both so the rig runs the same whether invoked through
     * Gradle or a plain {@code java -cp} for a quick check.
     */
    @NonNull
    private static File findScript() throws IOException {
        return findScript("whisper_replay_server.py");
    }

    @NonNull
    private static File findScript(@NonNull String name) throws IOException {
        for (String candidate : new String[] {"../scripts/" + name, "scripts/" + name}) {
            File file = new File(candidate);
            if (file.isFile()) return file;
        }
        throw new IOException("cannot find scripts/" + name + " from " + new File(".").getAbsolutePath());
    }

    /** scripts/parakeet_replay_server.py: the same JSON-lines protocol, no prompt arguments. */
    @NonNull
    private static Process startParakeetServer(@NonNull String python, @NonNull File modelFile,
                                               @NonNull File tokenizerFile) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(python, findScript("parakeet_replay_server.py").getAbsolutePath(),
            modelFile.getAbsolutePath(), tokenizerFile.getAbsolutePath());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }

    @NonNull
    private static String joinInts(@NonNull int[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.length() == 0 ? "-" : out.toString();
    }

    /**
     * Sends each segment as {@code {"wav": path, "terminal": bool}} to the persistent Python
     * server's stdin and reads one JSON reply per line from its stdout, as
     * {@code scripts/whisper_replay_server.py} implements it.
     */
    private static final class SubprocessTranscriber implements VoiceReplayCore.Transcriber {
        private final BufferedWriter toServer;
        private final BufferedReader fromServer;
        private final File tempDir;
        private int sequence;

        SubprocessTranscriber(@NonNull Process server, @NonNull File outDir) throws IOException {
            this.toServer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream(), StandardCharsets.UTF_8));
            this.fromServer = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
            this.tempDir = new File(outDir, "segments");
            if (!tempDir.isDirectory() && !tempDir.mkdirs()) throw new IOException("cannot create " + tempDir);
        }

        @NonNull
        @Override
        public VoiceReplayCore.TranscriptResult transcribe(@NonNull short[] pcm) throws IOException {
            File wav = new File(tempDir, "segment-" + (sequence++) + ".wav");
            writeWav16kMono(wav, pcm);
            try {
                JSONObject request = new JSONObject();
                try {
                    request.put("wav", wav.getAbsolutePath());
                } catch (org.json.JSONException e) {
                    throw new IOException(e);
                }
                toServer.write(request.toString());
                toServer.write("\n");
                toServer.flush();
                String line = fromServer.readLine();
                if (line == null) throw new IOException("the replay server closed unexpectedly");
                JSONObject response;
                try {
                    response = new JSONObject(line);
                } catch (org.json.JSONException e) {
                    throw new IOException("unreadable reply from the replay server: " + line);
                }
                if (response.has("error")) throw new IOException(response.optString("error"));
                return new VoiceReplayCore.TranscriptResult(response.optString("text", ""),
                    response.optLong("encode_ms", 0), response.optLong("decode_ms", 0), response.optInt("steps", 0));
            } finally {
                // -Dvoice.replay.keep=true leaves each segment (after VoiceGain) in <out>/segments.
                //noinspection ResultOfMethodCallIgnored
                if (!Boolean.getBoolean("voice.replay.keep")) wav.delete();
            }
        }

        private static void writeWav16kMono(@NonNull File out, @NonNull short[] pcm) throws IOException {
            int dataBytes = pcm.length * 2;
            try (FileOutputStream stream = new FileOutputStream(out)) {
                stream.write(riffHeader(dataBytes));
                byte[] bytes = new byte[dataBytes];
                for (int i = 0; i < pcm.length; i++) {
                    bytes[i * 2] = (byte) pcm[i];
                    bytes[i * 2 + 1] = (byte) (pcm[i] >> 8);
                }
                stream.write(bytes);
            }
        }

        @NonNull
        private static byte[] riffHeader(int dataBytes) {
            int sampleRate = VoiceActivityDetector.SAMPLE_RATE;
            int byteRate = sampleRate * 2;
            byte[] header = new byte[44];
            putTag(header, 0, "RIFF");
            putLe32(header, 4, 36 + dataBytes);
            putTag(header, 8, "WAVE");
            putTag(header, 12, "fmt ");
            putLe32(header, 16, 16);
            putLe16(header, 20, (short) 1); // PCM
            putLe16(header, 22, (short) 1); // mono
            putLe32(header, 24, sampleRate);
            putLe32(header, 28, byteRate);
            putLe16(header, 32, (short) 2); // block align
            putLe16(header, 34, (short) 16); // bits per sample
            putTag(header, 36, "data");
            putLe32(header, 40, dataBytes);
            return header;
        }

        private static void putTag(@NonNull byte[] out, int offset, @NonNull String tag) {
            for (int i = 0; i < tag.length(); i++) out[offset + i] = (byte) tag.charAt(i);
        }

        private static void putLe16(@NonNull byte[] out, int offset, short value) {
            out[offset] = (byte) value;
            out[offset + 1] = (byte) (value >> 8);
        }

        private static void putLe32(@NonNull byte[] out, int offset, int value) {
            out[offset] = (byte) value;
            out[offset + 1] = (byte) (value >> 8);
            out[offset + 2] = (byte) (value >> 16);
            out[offset + 3] = (byte) (value >> 24);
        }
    }

    /**
     * The exact prompt and suppression ids {@code com.termux.ai.WhisperDecoder}/{@code WhisperTokenizer}
     * would build for this tokenizer, read by reflection since both classes are package-private to
     * {@code com.termux.ai} — rather than re-implementing the BPE encode, the real runtime classes
     * (already on the test classpath, same module) do the work and the token ids are handed to the
     * Python server once at startup.
     */
    private static final class BiasPrompt {
        final int[] prompt;
        final int endOfText;
        final int timestampBegin;
        final int[] always;

        private BiasPrompt(int[] prompt, int endOfText, int timestampBegin, int[] always) {
            this.prompt = prompt;
            this.endOfText = endOfText;
            this.timestampBegin = timestampBegin;
            this.always = always;
        }

        @NonNull
        static BiasPrompt build(@NonNull File tokenizerJson) throws ReflectiveOperationException {
            Class<?> tokenizerClass = Class.forName("com.termux.ai.WhisperTokenizer");
            Class<?> decoderClass = Class.forName("com.termux.ai.WhisperDecoder");
            Class<?> suppressionClass = Class.forName("com.termux.ai.WhisperDecoder$Suppression");

            java.lang.reflect.Method fromFile = tokenizerClass.getDeclaredMethod("fromFile", File.class);
            fromFile.setAccessible(true);
            Object tokenizer = invoke(fromFile, null, tokenizerJson);

            java.lang.reflect.Method promptMethod =
                decoderClass.getDeclaredMethod("prompt", tokenizerClass, String.class, String.class);
            promptMethod.setAccessible(true);
            int[] prompt = (int[]) invoke(promptMethod, null, tokenizer, "en", null);

            java.lang.reflect.Method forTokenizer = suppressionClass.getDeclaredMethod("forTokenizer", tokenizerClass);
            forTokenizer.setAccessible(true);
            Object suppression = invoke(forTokenizer, null, tokenizer);

            java.lang.reflect.Field endOfTextField = suppressionClass.getDeclaredField("endOfText");
            endOfTextField.setAccessible(true);
            java.lang.reflect.Field timestampBeginField = suppressionClass.getDeclaredField("timestampBegin");
            timestampBeginField.setAccessible(true);
            java.lang.reflect.Field alwaysField = suppressionClass.getDeclaredField("always");
            alwaysField.setAccessible(true);

            // -1 marks a special this vocabulary lacks (WhisperDecoder.Suppression itself ignores it
            // the same way, via `contains`); dropped here so the id never reaches the Python side as
            // a real vocabulary index.
            int[] rawAlways = (int[]) alwaysField.get(suppression);
            List<Integer> filtered = new ArrayList<>();
            for (int id : rawAlways) if (id >= 0) filtered.add(id);
            int[] always = new int[filtered.size()];
            for (int i = 0; i < always.length; i++) always[i] = filtered.get(i);

            return new BiasPrompt(prompt,
                (int) endOfTextField.get(suppression), (int) timestampBeginField.get(suppression), always);
        }

        @NonNull
        private static Object invoke(@NonNull java.lang.reflect.Method method, Object target, Object... args)
                throws ReflectiveOperationException {
            try {
                Object result = method.invoke(target, args);
                if (result == null) throw new IllegalStateException(method + " returned null");
                return result;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ReflectiveOperationException) throw (ReflectiveOperationException) cause;
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new ReflectiveOperationException(cause);
            }
        }
    }
}
