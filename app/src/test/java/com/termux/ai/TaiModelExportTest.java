package com.termux.ai;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Export AI models end to end over real folders: which files make up each kind of model, the
 * layout written, the same-size skip that lets a re-run resume, cancel, the row's size line, and
 * the round trip: what the export writes reads back, through the import's manifest rules, as the
 * same model record.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TaiModelExportTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File models;
    private TaiModelSpec gemma;
    private TaiModelSpec whisper;
    private TaiModelSpec mnn;
    private TaiModelSpec diffusion;

    @Before
    public void setUp() throws Exception {
        models = temp.newFolder("files", "tai", "models");

        File gemmaFile = write(new File(models, "gemma-4-e2b-it/gemma-4-E2B-it.litertlm"), 3000);
        write(new File(models, "gemma-4-e2b-it/gemma-4-E2B-it.litertlm.part"), 10);
        gemma = new TaiModelSpec("gemma-4-e2b-it", "Gemma 4 E2B IT", "Fast assistant", "downloaded",
            gemmaFile.getAbsolutePath(), "Apache-2.0", 3000, set(TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_IMAGE_INPUT),
            true, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "gemma", null, 4096, 8, "abc123");

        File whisperFile = write(new File(models, "whisper-acft-base/acft_whisper_base_10s_drq.tflite"), 2000);
        write(new File(models, "whisper-acft-base/tokenizer.json"), 300);
        whisper = new TaiModelSpec("whisper-acft-base", "Whisper ACFT Base", "Speech-to-text", "downloaded",
            whisperFile.getAbsolutePath(), "Apache-2.0", 2300, set(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT),
            true, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "whisper-acft", "int8_drq", 128, 6, null);

        File mnnDir = new File(models, "qwen3-mnn");
        File config = writeText(new File(mnnDir, "config.json"), "{\"llm_model\":\"q8/llm.mnn\",\"llm_weight\":\"q8/llm.mnn.weight\"}");
        write(new File(mnnDir, "q8/llm.mnn"), 400);
        write(new File(mnnDir, "q8/llm.mnn.weight"), 4000);
        write(new File(mnnDir, "tokenizer.txt"), 200);
        writeText(new File(mnnDir, "llm_config.json"), "{\"hidden_size\":1}");
        mnn = new TaiModelSpec("qwen3-mnn", "Qwen3 MNN", "Imported local model", "imported",
            config.getAbsolutePath(), "User-provided model", 4700, set(TaiModelSpec.CAPABILITY_TEXT_CHAT),
            false, null, TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, null, null, 4096, 0, null);

        File sdDir = new File(models, "sd15");
        write(new File(sdDir, "unet.mnn"), 500);
        write(new File(sdDir, "vae/decoder.mnn"), 100);
        diffusion = TaiDiffusionImport.spec("sd15", "Stable Diffusion", "imported", "User-provided model",
            sdDir.getAbsolutePath(), TaiDiffusionPackage.TYPE_SD15, 600);
    }

    // ---- which files make up each model ----

    @Test
    public void plan_takesEveryFileOfEachModelAndNothingElse() throws Exception {
        File outside = write(new File(temp.getRoot(), "elsewhere/stray.litertlm"), 50);
        TaiModelSpec stray = new TaiModelSpec("stray", "Stray", "x", "imported", outside.getAbsolutePath(), "x", 50,
            set(TaiModelSpec.CAPABILITY_TEXT_CHAT), false);
        File gone = new File(models, "gone/model.litertlm");
        TaiModelSpec missing = new TaiModelSpec("gone", "Gone", "x", "imported", gone.getAbsolutePath(), "x", 50,
            set(TaiModelSpec.CAPABILITY_TEXT_CHAT), false);

        List<TaiModelExport.Model> plan = TaiModelExport.plan(
            Arrays.asList(gemma, whisper, mnn, diffusion, stray, missing), models, Collections.emptyMap());

        assertEquals(Arrays.asList("gemma-4-e2b-it", "whisper-acft-base", "qwen3-mnn", "sd15"), folders(plan));
        // A partial download is never part of a model.
        assertEquals(Collections.singletonList("gemma-4-E2B-it.litertlm"), paths(plan.get(0)));
        assertEquals(Arrays.asList("acft_whisper_base_10s_drq.tflite", "tokenizer.json"), paths(plan.get(1)));
        assertEquals(Arrays.asList("config.json", "llm_config.json", "q8/llm.mnn", "q8/llm.mnn.weight", "tokenizer.txt"),
            paths(plan.get(2)));
        assertEquals(Arrays.asList("unet.mnn", "vae/decoder.mnn"), paths(plan.get(3)));
        assertEquals(2300L, plan.get(1).bytes);
        assertEquals("", plan.get(3).manifest.getString("main"));
        assertEquals("q8/llm.mnn.weight", plan.get(2).files.get(3).path);
    }

    @Test
    public void plan_keepsNoPathFromThisPhone() throws Exception {
        TaiModelExport.Model model = TaiModelExport.plan(Collections.singletonList(gemma), models,
            Collections.singletonMap(gemma.id, TaiModelStore.EXPOSURE_BOTH)).get(0);
        String text = model.manifest.toString();
        // The temporary folder's own name stands for any path on this phone (JSON escapes the slashes).
        assertFalse(text, text.contains(temp.getRoot().getName()));
        assertFalse(model.manifest.getJSONObject("model").has("localPath"));
        assertEquals(TaiModelStore.EXPOSURE_BOTH, model.manifest.getString("exposure"));
    }

    // ---- the destination layout ----

    @Test
    public void export_writesOneFolderPerModelWithTheManifestBesideTheFiles() throws Exception {
        File destination = temp.newFolder("Download");
        List<TaiModelExport.Model> plan = TaiModelExport.plan(Arrays.asList(gemma, mnn), models, Collections.emptyMap());

        TaiModelExport.Result result = TaiModelExport.export(plan, new FileFolder(destination), (m, done, total) -> true);

        assertEquals(2, result.saved);
        assertFalse(result.cancelled);
        assertNull(result.error);
        File base = new File(destination, TaiModelExport.FOLDER_NAME);
        assertEquals(3000L, new File(base, "gemma-4-e2b-it/gemma-4-E2B-it.litertlm").length());
        assertTrue(new File(base, "gemma-4-e2b-it/" + TaiModelExport.MANIFEST_NAME).isFile());
        assertFalse(new File(base, "gemma-4-e2b-it/gemma-4-E2B-it.litertlm.part").exists());
        assertEquals(4000L, new File(base, "qwen3-mnn/q8/llm.mnn.weight").length());
        assertTrue(new File(base, "qwen3-mnn/" + TaiModelExport.MANIFEST_NAME).isFile());
        assertArrayEquals(new File(models, "qwen3-mnn/q8/llm.mnn.weight"), new File(base, "qwen3-mnn/q8/llm.mnn.weight"));
    }

    // ---- the skip-if-same-size rule ----

    @Test
    public void needsCopy_onlyWhenAbsentOrADifferentSize() {
        assertTrue(TaiModelExport.needsCopy(-1L, 10L));
        assertTrue(TaiModelExport.needsCopy(9L, 10L));
        assertTrue(TaiModelExport.needsCopy(11L, 10L));
        assertFalse(TaiModelExport.needsCopy(10L, 10L));
        assertFalse(TaiModelExport.needsCopy(0L, 0L));
    }

    @Test
    public void export_aSecondRunCopiesOnlyWhatIsMissingOrShort() throws Exception {
        File destination = temp.newFolder("Download");
        List<TaiModelExport.Model> plan = TaiModelExport.plan(Arrays.asList(gemma, whisper), models, Collections.emptyMap());
        FileFolder root = new FileFolder(destination);
        TaiModelExport.export(plan, root, (m, done, total) -> true);
        assertEquals(0L, TaiModelExport.bytesToWrite(plan, root));

        // An interrupted earlier run left the tokenizer short: only it is copied again.
        File tokenizer = new File(destination, TaiModelExport.FOLDER_NAME + "/whisper-acft-base/tokenizer.json");
        write(tokenizer, 120);
        assertEquals(300L, TaiModelExport.bytesToWrite(plan, root));
        List<String> created = new ArrayList<>();
        TaiModelExport.Result again = TaiModelExport.export(plan, new FileFolder(destination, created), (m, done, total) -> true);

        assertEquals(2, again.saved);
        assertEquals(300L, tokenizer.length());
        // The manifests are rewritten (they are the "finished" mark); no model file but the short one is.
        assertEquals(Arrays.asList(TaiModelExport.MANIFEST_NAME, "tokenizer.json", TaiModelExport.MANIFEST_NAME), created);
    }

    @Test
    public void export_cancelRemovesTheFileInProgressAndKeepsFinishedModels() throws Exception {
        File destination = temp.newFolder("Download");
        List<TaiModelExport.Model> plan = TaiModelExport.plan(Arrays.asList(gemma, whisper), models, Collections.emptyMap());

        TaiModelExport.Result result = TaiModelExport.export(plan, new FileFolder(destination),
            (m, done, total) -> !m.id.equals(whisper.id));

        assertTrue(result.cancelled);
        assertEquals(1, result.saved);
        File base = new File(destination, TaiModelExport.FOLDER_NAME);
        assertTrue(new File(base, "gemma-4-e2b-it/" + TaiModelExport.MANIFEST_NAME).isFile());
        assertFalse(new File(base, "whisper-acft-base/acft_whisper_base_10s_drq.tflite").exists());
        assertFalse(new File(base, "whisper-acft-base/" + TaiModelExport.MANIFEST_NAME).exists());
    }

    // ---- the size summary text ----

    @Test
    public void sizeSummary_countsModelsAndBytes() {
        android.content.res.Resources resources = RuntimeEnvironment.getApplication().getResources();
        assertEquals("3 models · 6.2 GB", TaiModelExport.sizeSummary(resources, 3, 6_657_199_308L));
        assertEquals("1 model · 96.7 MB", TaiModelExport.sizeSummary(resources, 1, 101_391_632L));
        assertEquals("512 B", TaiModelExport.formatBytes(512L));
    }

    // ---- the round trip ----

    @Test
    public void roundTrip_everyKindReadsBackAsTheSameModel() throws Exception {
        File destination = temp.newFolder("Download");
        List<TaiModelSpec> originals = Arrays.asList(gemma, whisper, mnn, diffusion);
        List<TaiModelExport.Model> plan = TaiModelExport.plan(originals, models, Collections.emptyMap());
        TaiModelExport.export(plan, new FileFolder(destination), (m, done, total) -> true);
        File base = new File(destination, TaiModelExport.FOLDER_NAME);
        File newPhone = temp.newFolder("new-phone", "tai", "models");

        for (TaiModelSpec original : originals) {
            File folder = new File(base, original.id);
            TaiModelExport.Manifest manifest = TaiModelExport.readManifest(new JSONObject(new String(
                Files.readAllBytes(new File(folder, TaiModelExport.MANIFEST_NAME).toPath()), StandardCharsets.UTF_8)));
            // Every file the manifest names is in the saved folder at its size, as the import requires.
            for (TaiModelExport.Entry entry : manifest.files) {
                assertEquals(original.id + " " + entry.path, entry.size, new File(folder, entry.path).length());
            }
            TaiModelSpec restored = TaiModelExport.restoredSpec(manifest, new File(newPhone, manifest.id));
            JSONObject before = original.toJson();
            JSONObject after = restored.toJson();
            assertEquals(new File(original.localPath).getName(), new File(restored.localPath).getName());
            before.remove("localPath");
            after.remove("localPath");
            assertEquals(original.id, canonical(before), canonical(after));
        }

        // The saved MNN folder is also a package the plain MNN folder import accepts.
        File savedMnn = new File(base, "qwen3-mnn");
        TaiMnnPackage.validate(new File(savedMnn, "config.json"));
        // An image model's record points at its folder.
        TaiModelExport.Manifest image = TaiModelExport.readManifest(new JSONObject(new String(Files.readAllBytes(
            new File(base, "sd15/" + TaiModelExport.MANIFEST_NAME).toPath()), StandardCharsets.UTF_8)));
        assertEquals(new File(newPhone, "sd15").getAbsolutePath(),
            TaiModelExport.restoredSpec(image, new File(newPhone, "sd15")).localPath);
    }

    @Test
    public void readManifest_refusesWhatThisAppDidNotWrite() throws Exception {
        JSONObject good = TaiModelExport.plan(Collections.singletonList(gemma), models, Collections.emptyMap()).get(0).manifest;
        assertEquals("gemma-4-e2b-it", TaiModelExport.readManifest(good).id);

        assertRefused(copy(good).put("format", "zip"));
        assertRefused(copy(good).put("version", TaiModelExport.VERSION + 1));
        JSONObject escape = copy(good);
        escape.getJSONArray("files").getJSONObject(0).put("path", "../../shared_prefs/x.xml");
        assertRefused(escape);
        JSONObject badId = copy(good);
        badId.getJSONObject("model").put("id", "../gemma");
        assertRefused(badId);
        assertRefused(copy(good).put("main", "not-listed.litertlm"));
        JSONObject unsupported = copy(good);
        unsupported.getJSONObject("model").put("backend", "llama.cpp");
        try {
            TaiModelExport.restoredSpec(TaiModelExport.readManifest(unsupported), new File(models, "x"));
            fail("An unsupported backend was restored");
        } catch (IllegalArgumentException expected) {
            // Refused.
        }
    }

    // ---- helpers ----

    private static void assertRefused(@NonNull JSONObject manifest) {
        try {
            TaiModelExport.readManifest(manifest);
            fail("Accepted " + manifest);
        } catch (IllegalArgumentException expected) {
            // Refused.
        }
    }

    private static void assertArrayEquals(@NonNull File expected, @NonNull File actual) throws IOException {
        org.junit.Assert.assertArrayEquals(Files.readAllBytes(expected.toPath()), Files.readAllBytes(actual.toPath()));
    }

    /** A JSON value as plain sorted maps and lists, numbers as long or double, so equal records compare equal. */
    @Nullable
    private static Object canonical(@Nullable Object value) throws Exception {
        if (value instanceof JSONObject) {
            JSONObject json = (JSONObject) value;
            java.util.TreeMap<String, Object> map = new java.util.TreeMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, canonical(json.get(key)));
            }
            return map;
        }
        if (value instanceof org.json.JSONArray) {
            org.json.JSONArray array = (org.json.JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) list.add(canonical(array.get(i)));
            return list;
        }
        if (value instanceof Double || value instanceof Float) return ((Number) value).doubleValue();
        if (value instanceof Number) return ((Number) value).longValue();
        if (value == JSONObject.NULL) return "null";
        return value;
    }

    @NonNull
    private static JSONObject copy(@NonNull JSONObject json) throws Exception {
        return new JSONObject(json.toString());
    }

    @NonNull
    private static List<String> folders(@NonNull List<TaiModelExport.Model> plan) {
        List<String> names = new ArrayList<>();
        for (TaiModelExport.Model model : plan) names.add(model.folder);
        return names;
    }

    @NonNull
    private static List<String> paths(@NonNull TaiModelExport.Model model) {
        List<String> names = new ArrayList<>();
        for (TaiModelExport.Entry entry : model.files) names.add(entry.path);
        return names;
    }

    @NonNull
    private static LinkedHashSet<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    @NonNull
    private static File write(@NonNull File file, int size) throws IOException {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte) (i * 31 + size);
        Files.write(file.toPath(), bytes);
        return file;
    }

    @NonNull
    private static File writeText(@NonNull File file, @NonNull String text) throws IOException {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** A plain directory standing in for the picked folder; records the files it creates. */
    private static final class FileFolder implements TaiModelExport.Folder {
        private final File dir;
        private final List<String> created;

        FileFolder(@NonNull File dir) {
            this(dir, new ArrayList<>());
        }

        FileFolder(@NonNull File dir, @NonNull List<String> created) {
            this.dir = dir;
            this.created = created;
        }

        @Nullable
        @Override
        public TaiModelExport.Folder child(@NonNull String name, boolean create) throws IOException {
            File child = new File(dir, name);
            if (child.isDirectory()) return new FileFolder(child, created);
            if (child.exists() || !create) return null;
            if (!child.mkdirs()) throw new IOException("mkdirs " + child);
            return new FileFolder(child, created);
        }

        @Override
        public long size(@NonNull String name) {
            File file = new File(dir, name);
            return file.isFile() ? file.length() : -1L;
        }

        @NonNull
        @Override
        public OutputStream create(@NonNull String name) throws IOException {
            created.add(name);
            return new FileOutputStream(new File(dir, name), false);
        }

        @Override
        public void delete(@NonNull String name) {
            new File(dir, name).delete();
        }
    }
}
