package com.termux.app.fragments.settings.termux;

import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaiModelCatalogPreferencesFragmentTest {

    @Test
    public void filterEntries_filtersByBackend() {
        List<TaiModelCatalog.CatalogEntry> liteRt = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(), TaiModelCatalogPreferencesFragment.BackendFilter.LITERT, "");
        List<TaiModelCatalog.CatalogEntry> mnn = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(), TaiModelCatalogPreferencesFragment.BackendFilter.MNN, "");

        // Counts are derived, not hardcoded: the catalog grows, and a magic number here only
        // records how many models existed the day the test was written.
        assertTrue("catalog should expose LiteRT models", liteRt.size() > 0);
        assertTrue("catalog should expose MNN models", mnn.size() > 0);
        assertEquals("LiteRT and MNN must partition the whole catalog",
            TaiModelCatalog.entries().size(), liteRt.size() + mnn.size());
        for (TaiModelCatalog.CatalogEntry entry : liteRt) {
            assertEquals(TaiModelSpec.BACKEND_LITERT_LM, entry.backend);
        }
        for (TaiModelCatalog.CatalogEntry entry : mnn) {
            assertEquals(TaiModelSpec.BACKEND_MNN_LLM, entry.backend);
        }
    }

    @Test
    public void filterEntries_supportsInstalledAndUsableFilters() {
        TaiModelCatalog.CatalogEntry installedEntry = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        assertNotNull(installedEntry);
        TaiModelSpec installedSpec = new TaiModelSpec(
            installedEntry.modelId,
            installedEntry.displayName,
            installedEntry.roleHint,
            "downloaded",
            "/models/gemma/model.litertlm",
            installedEntry.license,
            installedEntry.sizeBytes,
            installedEntry.sourceCapabilities,
            false
        );

        List<TaiModelCatalog.CatalogEntry> installed = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(),
            TaiModelCatalogPreferencesFragment.BackendFilter.INSTALLED,
            "",
            Collections.singletonMap(installedEntry.modelId, installedSpec),
            null);
        List<TaiModelCatalog.CatalogEntry> usable = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(),
            TaiModelCatalogPreferencesFragment.BackendFilter.USABLE,
            "",
            Collections.emptyMap(),
            null);

        assertEquals(1, installed.size());
        assertEquals(installedEntry.modelId, installed.get(0).modelId);
        assertEquals(TaiModelCatalog.entries().size(), usable.size());
    }

    @Test
    public void filterEntries_searchesNameIdBackendJobGroupAndCapabilityTags() {
        assertTrue(containsModel(search("Gemma 4 E2B"), TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertTrue(containsModel(search("qwen2.5-coder-1.5b"), "qwen2.5-coder-1.5b-instruct-mnn"));
        assertTrue(search("mnn_llm").size() >= 6);
        assertTrue(containsModel(search("general_multimodal"), TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertTrue(containsModel(search("Reasoning"), "deepseek-r1-1.5b-qwen-mnn"));
    }

    @Test
    public void sortForDisplay_ordersRecommendedFirstThenSmallest() {
        List<TaiModelCatalog.CatalogEntry> sorted = TaiModelCatalogPreferencesFragment.sortForDisplay(
            TaiModelCatalogPreferencesFragment.filterEntries(TaiModelCatalog.entries().values(),
                TaiModelCatalogPreferencesFragment.BackendFilter.ALL, ""));

        assertEquals(TaiModelCatalog.entries().size(), sorted.size());

        // Recommended models are grouped at the front.
        int firstNonRecommended = sorted.size();
        for (int i = 0; i < sorted.size(); i++) {
            if (!sorted.get(i).recommended) {
                firstNonRecommended = i;
                break;
            }
        }
        assertTrue("at least one recommended model should sort first", firstNonRecommended > 0);
        for (int i = firstNonRecommended; i < sorted.size(); i++) {
            assertFalse(sorted.get(i).recommended);
        }

        // Within each partition the download size is non-decreasing.
        assertNonDecreasingSizes(sorted.subList(0, firstNonRecommended));
        assertNonDecreasingSizes(sorted.subList(firstNonRecommended, sorted.size()));
    }

    private void assertNonDecreasingSizes(List<TaiModelCatalog.CatalogEntry> entries) {
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i - 1).sizeBytes <= entries.get(i).sizeBytes);
        }
    }

    @Test
    public void displayTags_limitsCatalogTagsToSupportedUiSet() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);

        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Text"));
        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Vision"));
        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Audio"));
        assertFalse(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("tool_use"));
    }

    @Test
    public void actionStateFor_returnsRequiredStatefulActions() throws Exception {
        TaiModelCatalog.CatalogEntry installable = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        TaiModelCatalog.CatalogEntry importOnly = TaiModelCatalog.get("qwen2.5-1.5b-instruct-litert-lm");
        TaiModelCatalog.CatalogEntry mnn = TaiModelCatalog.get("qwen2.5-coder-1.5b-instruct-mnn");
        JSONObject downloading = new JSONObject()
            .put("status", TaiModelStore.STATE_DOWNLOADING)
            .put("bytesRead", 25L)
            .put("totalBytes", 100L);

        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.INSTALL,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, null, "other").type);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.DOWNLOADING,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, downloading, "other").type);
        assertEquals("25%",
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, downloading, "other").progressLabel);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.INSTALLED,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, "other").type);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.ACTIVE,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, installable.modelId).type);
        assertFalse(TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, installable.modelId).enabled);
        assertEquals("Default",
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, installable.modelId).pill);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.IMPORT_ONLY,
            TaiModelCatalogPreferencesFragment.actionStateFor(importOnly, false, null, "other").type);

        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.INSTALL,
            TaiModelCatalogPreferencesFragment.actionStateFor(mnn, false, null, "other").type);
    }

    private List<TaiModelCatalog.CatalogEntry> search(String query) {
        return TaiModelCatalogPreferencesFragment.filterEntries(TaiModelCatalog.entries().values(),
            TaiModelCatalogPreferencesFragment.BackendFilter.ALL, query);
    }

    private boolean containsModel(List<TaiModelCatalog.CatalogEntry> entries, String modelId) {
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            if (modelId.equals(entry.modelId)) return true;
        }
        return false;
    }
}
