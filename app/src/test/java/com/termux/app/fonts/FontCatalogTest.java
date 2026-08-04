package com.termux.app.fonts;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The bundled catalog is what decides which URLs the app contacts and which bytes it accepts,
 * so both halves are tested: the real shipped file has to survive validation intact, and
 * crafted documents have to be rejected on every bound.
 */
@RunWith(RobolectricTestRunner.class)
public class FontCatalogTest {

    @Before
    public void resetCache() {
        FontCatalog.resetForTesting();
    }

    // ------------------------------------------------------------ shipped file

    @Test
    public void bundledCatalog_parsesWithNoErrors() {
        Context context = ApplicationProvider.getApplicationContext();
        FontCatalog.Result result = FontCatalog.load(context);
        assertEquals(result.errors.toString(), 0, result.errors.size());
        assertEquals(1, result.schemaVersion);
        assertFalse(result.families.isEmpty());
        // Reserved for a future signed remote refresh; shipping it empty is the point.
        assertEquals("", result.refreshUrl);
    }

    @Test
    public void bundledCatalog_shipsTheBundledSymbolsFace() {
        FontCatalog.SymbolFont symbols =
            FontCatalog.load(ApplicationProvider.<Context>getApplicationContext()).symbolFont;
        assertNotNull(symbols);
        assertEquals("fonts/SymbolsNerdFontMono.ttf", symbols.assetPath);
        assertEquals("SymbolsNerdFontMono.ttf", symbols.installName);
        assertEquals(2564060L, symbols.sizeBytes);
        assertEquals("2dc316f2505a0cbfbcf6060a1b4ba85b0a2974189e30c0037cdedc436a25a4ff",
            symbols.sha256);
    }

    /**
     * Maple Mono is the family the catalog flags as its suggestion — now only a star on its list
     * row, since the one-tap setup row is gone — so its defaults are the ones a fresh install
     * picks up most often.
     */
    @Test
    public void bundledCatalog_flagsMapleMonoAsTheSuggestedVariableFamily() {
        FontCatalog.Family family = FontCatalog
            .load(ApplicationProvider.<Context>getApplicationContext()).family("maple-mono");
        assertNotNull(family);
        assertTrue(family.recommended);
        assertTrue(family.variable);
        assertNotNull(family.weightAxis);
        assertEquals(400, family.weightAxis.regularWeight);
        assertEquals(700, family.weightAxis.boldWeight);
        assertEquals(FontInstaller.LIGATURES_CURSOR, family.defaultLigatures);
        assertEquals("+zero", family.defaultFontFeatures);
        assertEquals(4, family.faces.size());
    }

    @Test
    public void bundledCatalog_everyFaceIsHttpsAndDigestedAndUnderTheCap() {
        FontCatalog.Result result =
            FontCatalog.load(ApplicationProvider.<Context>getApplicationContext());
        for (FontCatalog.Family family : result.families) {
            assertTrue(family.id, FontCatalog.isSafeId(family.id));
            assertTrue(family.id, family.hasFace(FontCatalog.FaceSlot.REGULAR));
            assertTrue(family.id, FontCatalog.isHttpsUrl(family.licenseUrl));
            assertTrue(family.id, family.downloadBytes > 0
                && family.downloadBytes <= FontCatalog.MAX_FAMILY_DOWNLOAD_BYTES);
            for (FontCatalog.Archive archive : family.archives) {
                assertTrue(archive.url, FontCatalog.isHttpsUrl(archive.url));
                assertTrue(archive.url, FontCatalog.isSha256(archive.sha256));
            }
            for (FontCatalog.Face face : family.faces.values()) {
                assertTrue(family.id, FontCatalog.isSha256(face.sha256));
                assertTrue(family.id, face.sizeBytes > 0 && face.sizeBytes <= FontCatalog.MAX_FACE_BYTES);
                if (face.isArchiveMember()) {
                    assertTrue(family.id, FontCatalog.isHttpsUrl(face.zipUrl));
                    assertTrue(family.id, FontCatalog.isSafeRelativePath(face.memberPath));
                } else {
                    assertTrue(family.id, FontCatalog.isHttpsUrl(face.url));
                }
            }
        }
    }

    @Test
    public void bundledCatalog_firaCodeOmitsTheItalicItDoesNotShip() {
        FontCatalog.Family fira = FontCatalog
            .load(ApplicationProvider.<Context>getApplicationContext()).family("fira-code");
        assertNotNull(fira);
        assertEquals(2, fira.faces.size());
        assertTrue(fira.hasFace(FontCatalog.FaceSlot.REGULAR));
        assertTrue(fira.hasFace(FontCatalog.FaceSlot.BOLD));
        assertFalse(fira.hasFace(FontCatalog.FaceSlot.ITALIC));
        assertFalse(fira.hasFace(FontCatalog.FaceSlot.BOLD_ITALIC));
    }

    // ------------------------------------------------------------- validation

    @Test
    public void nonHttpsFaceUrl_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": " + directFace("http://example.com/a.ttf") + "}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("url must be an https URL"));
    }

    @Test
    public void nonHttpsArchiveUrl_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"archives\": [{\"url\": \"http://example.com/a.zip\", \"sha256\": \"" + SHA
                + "\", \"sizeBytes\": 10}],"
                + "\"faces\": {\"regular\": {\"zipUrl\": \"http://example.com/a.zip\","
                + "\"memberPath\": \"a.ttf\", \"sha256\": \"" + SHA + "\", \"sizeBytes\": 10}}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("archive url must be an https URL"));
    }

    @Test
    public void nonHttpsRefreshUrl_isDroppedWithoutKillingTheCatalog() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": " + directFace("https://example.com/a.ttf") + "}",
            "\"refreshUrl\": \"http://example.com/catalog.json\","));
        assertEquals("", result.refreshUrl);
        assertEquals(1, result.families.size());
        assertTrue(result.errors.toString().contains("refreshUrl must be an https URL"));
    }

    @Test
    public void overlongUrl_isRejected() {
        StringBuilder url = new StringBuilder("https://example.com/");
        while (url.length() <= FontCatalog.MAX_URL_CHARS) url.append('a');
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": " + directFace(url.toString()) + "}"));
        assertTrue(result.families.isEmpty());
    }

    @Test
    public void familyCount_isCapped() {
        StringBuilder families = new StringBuilder();
        for (int i = 0; i < FontCatalog.MAX_FAMILIES + 5; i++) {
            if (i > 0) families.append(',');
            families.append(family("f" + i,
                "\"faces\": {\"regular\": " + directFace("https://example.com/" + i + ".ttf") + "}"));
        }
        FontCatalog.Result result = FontCatalog.parse(
            "{\"schemaVersion\": 1, \"symbolFont\": " + SYMBOLS + ", \"families\": [" + families + "]}");
        assertEquals(FontCatalog.MAX_FAMILIES, result.families.size());
        assertTrue(result.errors.toString().contains("family count exceeds"));
    }

    @Test
    public void oversizeFace_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": {\"url\": \"https://example.com/a.ttf\", \"sha256\": \"" + SHA
                + "\", \"sizeBytes\": " + (FontCatalog.MAX_FACE_BYTES + 1) + "}}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("sizeBytes must be 1.."));
    }

    @Test
    public void badDigest_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": {\"url\": \"https://example.com/a.ttf\","
                + "\"sha256\": \"NOTAHASH\", \"sizeBytes\": 10}}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("sha256 must be 64 hex characters"));
    }

    @Test
    public void traversalMemberPath_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"archives\": [" + archive("https://example.com/a.zip") + "],"
                + "\"faces\": {\"regular\": {\"zipUrl\": \"https://example.com/a.zip\","
                + "\"memberPath\": \"../../evil.ttf\", \"sha256\": \"" + SHA
                + "\", \"sizeBytes\": 10}}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("memberPath is missing or unsafe"));
    }

    @Test
    public void archiveMemberWithoutADeclaredArchive_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": {\"zipUrl\": \"https://example.com/a.zip\","
                + "\"memberPath\": \"a.ttf\", \"sha256\": \"" + SHA + "\", \"sizeBytes\": 10}}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("zipUrl is not declared in archives"));
    }

    @Test
    public void missingRegularFace_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"bold\": " + directFace("https://example.com/b.ttf") + "}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("a regular face is required"));
    }

    @Test
    public void unknownFaceKey_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"faces\": {\"regular\": " + directFace("https://example.com/a.ttf")
                + ", \"condensed\": " + directFace("https://example.com/c.ttf") + "}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("unknown face"));
    }

    @Test
    public void variableFamilyWithoutAnAxis_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(catalog(
            "\"variable\": true, \"faces\": {\"regular\": "
                + directFace("https://example.com/a.ttf") + "}"));
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("variable family needs a weightAxis"));
    }

    @Test
    public void missingLicense_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(
            "{\"schemaVersion\": 1, \"symbolFont\": " + SYMBOLS + ", \"families\": ["
                + "{\"id\": \"x\", \"displayName\": \"X\", \"faces\": {\"regular\": "
                + directFace("https://example.com/a.ttf") + "}}]}");
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("license and licenseUrl are required"));
    }

    @Test
    public void unsafeFamilyId_isRejected() {
        FontCatalog.Result result = FontCatalog.parse(
            "{\"schemaVersion\": 1, \"symbolFont\": " + SYMBOLS + ", \"families\": ["
                + family("../escape", "\"faces\": {\"regular\": "
                + directFace("https://example.com/a.ttf") + "}") + "]}");
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("invalid id"));
    }

    @Test
    public void duplicateIds_keepTheFirstOnly() {
        String one = family("dup", "\"faces\": {\"regular\": "
            + directFace("https://example.com/a.ttf") + "}");
        FontCatalog.Result result = FontCatalog.parse(
            "{\"schemaVersion\": 1, \"symbolFont\": " + SYMBOLS + ", \"families\": ["
                + one + "," + one + "]}");
        assertEquals(1, result.families.size());
        assertTrue(result.errors.toString().contains("duplicate family id"));
    }

    @Test
    public void unsupportedSchemaVersion_refusesTheWholeFile() {
        FontCatalog.Result result = FontCatalog.parse(
            "{\"schemaVersion\": 99, \"families\": []}");
        assertTrue(result.families.isEmpty());
        assertNull(result.symbolFont);
        assertTrue(result.errors.toString().contains("unsupported catalog schemaVersion"));
    }

    @Test
    public void malformedJson_yieldsErrorsNotAnException() {
        FontCatalog.Result result = FontCatalog.parse("{not json");
        assertTrue(result.families.isEmpty());
        assertTrue(result.errors.toString().contains("not valid JSON"));
    }

    @Test
    public void oversizeDocument_isRefusedBeforeParsing() {
        StringBuilder padding = new StringBuilder();
        while (padding.length() <= FontCatalog.MAX_JSON_BYTES) padding.append(' ');
        FontCatalog.Result result = FontCatalog.parse("{}" + padding);
        assertTrue(result.errors.toString().contains("exceeds"));
    }

    @Test
    public void pathGuards_rejectEverySlipShape() {
        assertFalse(FontCatalog.isSafeRelativePath("/etc/passwd"));
        assertFalse(FontCatalog.isSafeRelativePath("../a.ttf"));
        assertFalse(FontCatalog.isSafeRelativePath("ttf/../../a.ttf"));
        assertFalse(FontCatalog.isSafeRelativePath("ttf\\a.ttf"));
        assertFalse(FontCatalog.isSafeRelativePath("C:/a.ttf"));
        assertFalse(FontCatalog.isSafeRelativePath("ttf//a.ttf"));
        assertFalse(FontCatalog.isSafeRelativePath("ttf/"));
        assertFalse(FontCatalog.isSafeRelativePath("a\nb.ttf"));
        assertTrue(FontCatalog.isSafeRelativePath("a.ttf"));
        assertTrue(FontCatalog.isSafeRelativePath("ttf/static/a.ttf"));
        // Square brackets are ordinary characters in a variable-font file name.
        assertTrue(FontCatalog.isSafeRelativePath("MapleMono[wght].ttf"));
    }

    // ------------------------------------------------------------------ fixtures

    private static final String SHA =
        "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String SYMBOLS = "{\"assetPath\": \"fonts/s.ttf\","
        + "\"installName\": \"s.ttf\", \"sha256\": \"" + SHA + "\", \"sizeBytes\": 10}";

    private static String directFace(String url) {
        return "{\"url\": \"" + url + "\", \"sha256\": \"" + SHA + "\", \"sizeBytes\": 10}";
    }

    private static String archive(String url) {
        return "{\"url\": \"" + url + "\", \"sha256\": \"" + SHA + "\", \"sizeBytes\": 10}";
    }

    private static String family(String id, String body) {
        return "{\"id\": \"" + id + "\", \"displayName\": \"" + id + "\","
            + "\"license\": \"OFL 1.1\", \"licenseUrl\": \"https://example.com/OFL.txt\","
            + body + "}";
    }

    private static String catalog(String familyBody) {
        return catalog(familyBody, "");
    }

    private static String catalog(String familyBody, String extraRoot) {
        return "{\"schemaVersion\": 1," + extraRoot + " \"symbolFont\": " + SYMBOLS
            + ", \"families\": [" + family("x", familyBody) + "]}";
    }
}
