package com.termux.app.fragments.settings.termux;

import com.termux.R;
import com.termux.ai.TaiModelImporter;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TaiImportMessagesTest {

    @Test
    public void ggufPickNamesWhatToLookFor() {
        TaiImportMessages.Message message = TaiImportMessages.forValidation(
            TaiModelImporter.validateSupportedImportFileName("llama-3.gguf"), "llama-3.gguf");
        assertEquals(R.string.termux_ai_import_err_raw_weights, message.resId);
        assertNotNull("the developer message stays behind Show details", message.detail);
        assertEquals(R.string.termux_ai_import_err_raw_weights, TaiImportMessages.forValidation(
            TaiModelImporter.validateSupportedImportFileName("model.safetensors"), "model.safetensors").resId);
    }

    @Test
    public void unknownExtensionIsNamedInTheMessage() {
        TaiImportMessages.Message message = TaiImportMessages.forValidation(
            TaiModelImporter.validateSupportedImportFileName("model.zip"), "model.zip");
        assertEquals(R.string.termux_ai_import_err_extension, message.resId);
        assertEquals("zip", message.args[0]);
        assertEquals(R.string.termux_ai_import_err_no_extension, TaiImportMessages.forValidation(
            TaiModelImporter.validateSupportedImportFileName("model"), "model").resId);
        assertEquals(R.string.termux_ai_import_err_native_library, TaiImportMessages.forValidation(
            TaiModelImporter.validateSupportedImportFileName("libfoo.so"), "libfoo.so").resId);
    }

    @Test
    public void storageErrorCarriesBothSizes() throws Exception {
        JSONObject result = new JSONObject().put("ok", false).put("error", "insufficient_storage")
            .put("message", "Not enough free storage to import this model.")
            .put("neededBytes", 2L * 1024 * 1024 * 1024).put("freeBytes", 512L * 1024 * 1024);
        TaiImportMessages.Message message = TaiImportMessages.forResult(result, "m.litertlm");
        assertEquals(R.string.termux_ai_import_err_storage, message.resId);
        assertEquals("2.0 GB", message.args[0]);
        assertEquals("512.0 MB", message.args[1]);
        assertEquals(R.string.termux_ai_import_err_storage_unknown, TaiImportMessages.forResult(
            new JSONObject().put("error", "insufficient_storage"), null).resId);
    }

    @Test
    public void importFailureReasonsPickTheirOwnWords() throws Exception {
        assertEquals(R.string.termux_ai_import_err_unreadable, TaiImportMessages.forResult(new JSONObject()
            .put("error", "model_import_failed").put("reason", TaiModelImporter.REASON_UNREADABLE)
            .put("message", "The selected file does not look like a readable model package."), "m.litertlm").resId);
        TaiImportMessages.Message cancelled = TaiImportMessages.forResult(new JSONObject()
            .put("error", "model_import_failed").put("reason", TaiModelImporter.REASON_CANCELLED)
            .put("message", "Model import cancelled."), "m.litertlm");
        assertEquals(R.string.termux_ai_import_cancelled, cancelled.resId);
        assertNull(cancelled.detail);
        assertEquals(R.string.termux_ai_import_err_generic, TaiImportMessages.forResult(new JSONObject()
            .put("error", "model_import_failed").put("message", "Could not finalize the imported model file."), null).resId);
    }

    @Test
    public void managerCodesMapToPlainWords() throws Exception {
        assertEquals(R.string.termux_ai_import_err_exists, TaiImportMessages.forResult(
            new JSONObject().put("error", "model_exists"), null).resId);
        assertEquals(R.string.termux_ai_import_err_no_file_in_repo, TaiImportMessages.forResult(
            new JSONObject().put("error", "hf_resolve_failed"), null).resId);
        assertEquals(R.string.termux_ai_import_err_runtime_update, TaiImportMessages.forResult(
            new JSONObject().put("error", "runtime_update_required").put("message", "needs 0.15"), null).resId);
        assertEquals(R.string.termux_ai_import_err_mnn_folder, TaiImportMessages.forResult(
            new JSONObject().put("error", "missing_config"), null).resId);
        assertEquals(R.string.termux_ai_import_err_offline, TaiImportMessages.forResult(
            new JSONObject().put("error", "download_failed").put("message", "Unable to resolve host huggingface.co"), null).resId);
        assertEquals(R.string.termux_ai_import_err_generic, TaiImportMessages.forResult(null, null).resId);
    }

    @Test
    public void linkProblemsAlwaysShowTheExample() {
        assertEquals(R.string.termux_ai_import_link_invalid, TaiImportMessages.forLink(
            TaiModelImporter.validateHuggingFaceImportUrl("http://huggingface.co/a/b")).resId);
        assertEquals(R.string.termux_ai_import_link_invalid, TaiImportMessages.forLink(
            TaiModelImporter.validateHuggingFaceImportUrl("https://huggingface.co/onlyorg")).resId);
        assertEquals(R.string.termux_ai_import_err_raw_weights, TaiImportMessages.forLink(
            TaiModelImporter.validateHuggingFaceImportUrl("https://huggingface.co/a/b/resolve/main/m.gguf")).resId);
    }

    @Test
    public void downloadErrorsAreSorted() {
        assertEquals(R.string.termux_ai_import_err_offline, TaiImportMessages.forDownloadError("connection reset").resId);
        assertEquals(R.string.termux_ai_import_err_unreadable, TaiImportMessages.forDownloadError("sha256 mismatch").resId);
        assertEquals(R.string.termux_ai_import_err_generic, TaiImportMessages.forDownloadError("").resId);
    }
}
