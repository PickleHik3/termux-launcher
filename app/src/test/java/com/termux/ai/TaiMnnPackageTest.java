package com.termux.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TaiMnnPackageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void includesConfiguredDependenciesAndSkipsOtherQuantizations() throws Exception {
        JSONObject config = new JSONObject().put("llm_model", "graphs/model.mnn").put("llm_weight", "weights/model.bin")
            .put("tokenizer_file", "tokenizer.mtok").put("mllm", new JSONObject().put("visual_model", "vision/model.mnn"));
        Set<String> files = TaiMnnPackage.files(config, new LinkedHashSet<>(Arrays.asList(
            "llm_config.json", "q8/llm.mnn", "q8/llm.mnn.weight")));
        assertTrue(files.contains("graphs/model.mnn"));
        assertTrue(files.contains("vision/model.mnn"));
        assertTrue(files.contains("tokenizer.mtok"));
        assertFalse(files.contains("q8/llm.mnn"));
    }

    @Test public void rejectsDependenciesOutsidePackage() throws Exception {
        try {
            TaiMnnPackage.files(new JSONObject().put("llm_model", "../other.mnn"), new LinkedHashSet<>());
            fail("Must reject escaping dependency");
        } catch (java.io.IOException expected) { }
    }

    @Test public void failsBeforeActivationWhenVisionDependencyIsMissing() throws Exception {
        Path dir = temporary.newFolder().toPath();
        Files.write(dir.resolve("config.json"), "{\"visual_model\":\"vision.mnn\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String name : Arrays.asList("llm.mnn", "llm.mnn.weight", "tokenizer.txt")) Files.write(dir.resolve(name), new byte[]{1});
        try { TaiMnnPackage.validate(dir.resolve("config.json").toFile()); fail("Missing vision graph accepted"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("vision.mnn")); }
        Files.write(dir.resolve("vision.mnn"), new byte[]{1});
        TaiMnnPackage.validate(dir.resolve("config.json").toFile());
    }
}
