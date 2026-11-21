package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.models.ReportInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

/** Export logs must name a save path: the report screen disables Save to file without one. */
@RunWith(RobolectricTestRunner.class)
public class AdvancedDiagnosticsExportLogsTest {

    @Test
    public void theExportedReportCanBeSavedToAFile() {
        ReportInfo info = AdvancedDiagnosticsPreferencesFragment.exportLogsReport(
            "Export logs", "body", new File("/storage/emulated/0"));

        assertEquals("body", info.reportString);
        assertNotNull(info.reportSaveFilePath);
        assertTrue(info.reportSaveFilePath, info.reportSaveFilePath.startsWith("/storage/emulated/0/"));
        assertTrue(info.reportSaveFilePath, info.reportSaveFilePath.endsWith(".log"));
        assertEquals("no spaces or separators in the file name", -1,
            new File(info.reportSaveFilePath).getName().indexOf(' '));
    }
}
