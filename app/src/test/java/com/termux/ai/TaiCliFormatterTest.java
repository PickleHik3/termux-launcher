package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiCliFormatterTest {

    @Test
    public void unload_reportsPendingLoadCancellation() throws Exception {
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("runtime", "litert-lm");
        response.put("loadingModelId", "Gemma-4-E2B-it");
        response.put("loadCancellationRequested", true);

        String output = TaiCliFormatter.format("unload", response);

        assertTrue(output.contains("Model load cancellation requested"));
        assertTrue(output.contains("Loading model: Gemma-4-E2B-it"));
        assertFalse(output.contains("Model unloaded"));
    }

    @Test
    public void cancel_distinguishesModelLoadFromGeneration() throws Exception {
        JSONObject response = new JSONObject();
        response.put("ok", true);
        response.put("cancelled", true);
        response.put("loadCancellationRequested", true);
        response.put("message", "Model load cancellation requested.");

        String output = TaiCliFormatter.format("cancel", response);

        assertTrue(output.contains("Model load cancellation requested"));
        assertFalse(output.contains("Generation cancel requested"));
    }

    @Test
    public void imageTextStreamLinesAreWhatTheScriptReads() throws Exception {
        assertEquals("progress 42\n", TaiCliFormatter.imageProgressLine(42));
        assertEquals("progress 100\n", TaiCliFormatter.imageProgressLine(250));
        assertEquals("progress 0\n", TaiCliFormatter.imageProgressLine(-3));

        JSONObject response = new JSONObject()
            .put("data", new org.json.JSONArray().put(new JSONObject().put("path", "/home/x/fox.png")))
            .put("tai", new JSONObject().put("seed", 7));
        String done = TaiCliFormatter.imageDoneLines(response);
        assertTrue(done.startsWith("done /home/x/fox.png\n"));
        assertTrue(done.contains("info {"));

        JSONObject error = new JSONObject().put("error", new JSONObject().put("message", "Not enough free memory.\nTry again."));
        assertEquals("error Not enough free memory. Try again.\n", TaiCliFormatter.imageErrorLine(error));
    }

    @Test
    public void imageSummaryAndCancelRead() throws Exception {
        JSONObject response = new JSONObject()
            .put("data", new org.json.JSONArray().put(new JSONObject().put("path", "/home/x/fox.png")))
            .put("tai", new JSONObject().put("width", 512).put("height", 512).put("steps", 20).put("seed", 7)
                .put("backend", "opencl").put("memoryMode", 1).put("loadMs", 1500).put("generateMs", 9000));
        String text = TaiCliFormatter.format("image", response);
        assertTrue(text.contains("Saved /home/x/fox.png"));
        assertTrue(text.contains("512x512, 20 steps, seed 7, opencl, memory mode 1"));
        assertTrue(text.contains("Loaded in 1.5 s, generated in 9.0 s"));
        assertEquals("Image generation cancelled.\n",
            TaiCliFormatter.format("image-cancel", new JSONObject().put("ok", true).put("cancelled", true)));
        assertEquals("No image was being generated.\n",
            TaiCliFormatter.format("image-cancel", new JSONObject().put("ok", true).put("cancelled", false)));
    }
}
