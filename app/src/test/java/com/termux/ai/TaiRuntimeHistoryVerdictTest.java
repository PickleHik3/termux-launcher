package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Only refusals that say something about the model/accelerator pair are written down as its
 * failures; a load tried before the download finished must never lock the GPU out.
 */
public class TaiRuntimeHistoryVerdictTest {

    @Test
    public void refusalsThatAreNotAboutTheAcceleratorAreNotRecorded() {
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict("model_file_missing"));
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict("model_file_not_readable"));
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict("low_available_memory"));
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict("known_failed_accelerator"));
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict(""));
        assertFalse(TaiRuntimeHistory.isAcceleratorVerdict(null));
        assertTrue(TaiRuntimeHistory.isAcceleratorVerdict("accelerator_not_supported_by_device"));
        assertTrue(TaiRuntimeHistory.isAcceleratorVerdict("no_compatible_accelerator"));
    }

    @Test
    public void aRecordLeftByALoadTriedMidDownloadIsStale() throws Exception {
        JSONObject stale = new JSONObject().put("success", false)
            .put("reason", "Download or import this model before loading it.");
        JSONObject real = new JSONObject().put("success", false)
            .put("reason", "OpenCL delegate failed to initialise");
        assertTrue(TaiRuntimeHistory.isStaleFileMissingRecord(stale));
        assertFalse(TaiRuntimeHistory.isStaleFileMissingRecord(real));
    }
}
