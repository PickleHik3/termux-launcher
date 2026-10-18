package com.termux.app.settings.backup;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.settings.backup.SettingsBackupFormat.InvalidBackupException;
import com.termux.app.settings.backup.SettingsBackupFormat.Root;
import com.termux.app.settings.backup.SettingsBackupFormat.Store;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The settings backup end to end through real SharedPreferences and real folders (one install
 * exporting, another importing), plus the format's own rules: entry names, manifest version,
 * density and link targets.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class SettingsBackupTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // ------------------------------------------------------------------ install doubles

    /** One install: its four stores (named apart per install) and its three folders. */
    private final class Install {
        final Map<Store, SharedPreferences> prefs = new EnumMap<>(Store.class);
        final Map<Root, File> roots = new EnumMap<>(Root.class);
        final File filesDir;
        final float density;

        Install(String name, float density) throws IOException {
            this.density = density;
            filesDir = temp.newFolder(name, "files");
            for (Store store : Store.values()) {
                SharedPreferences preferences = context.getSharedPreferences(name + "_" + store.id, Context.MODE_PRIVATE);
                preferences.edit().clear().commit();
                prefs.put(store, preferences);
            }
            roots.put(Root.TERMUX, new File(filesDir, "home/.termux"));
            roots.put(Root.CONFIG, new File(filesDir, "home/.config/termux"));
            roots.put(Root.APP, temp.newFolder(name, "app"));
        }

        SettingsBackupIo.Environment env() {
            return new SettingsBackupIo.Environment(prefs, roots,
                Collections.singletonList(filesDir.getAbsolutePath()), filesDir.getAbsolutePath(),
                density, (int) (4f * density), 256, "1.1.0-pre.2", "Termux",
                new File(filesDir.getParentFile(), "cache/settings-import"),
                new HashSet<>(SettingsBackupFormat.APP_FILES));
        }

        SharedPreferences main() { return prefs.get(Store.MAIN); }
        SharedPreferences ai() { return prefs.get(Store.AI); }

        File file(Root root, String relative) { return new File(roots.get(root), relative); }
    }

    private static void write(File file, String text) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static byte[] export(Install install) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SettingsBackupIo.export(install.env(), out, 1_760_000_000_000L);
        return out.toByteArray();
    }

    private static SettingsBackupIo.ApplyResult restore(Install install, byte[] bundle) throws IOException {
        SettingsBackupIo.Staged staged = SettingsBackupIo.read(install.env(), new ByteArrayInputStream(bundle));
        return SettingsBackupIo.apply(install.env(), staged);
    }

    private static Map<String, String> entries(byte[] bundle) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = zip.read(buffer)) != -1) bytes.write(buffer, 0, n);
                out.put(entry.getName(), new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String manifest(int format) {
        return "{\"format\":" + format + ",\"versionName\":\"1.1.0\",\"edition\":\"Nix\",\"density\":\"2.0\",\"createdAt\":1}";
    }

    // ------------------------------------------------------------------ round trip

    @Test
    public void roundTrip_restoresEveryTypedValue_intoClearedStores() throws IOException {
        Install source = new Install("source", 2f);
        Set<String> pinned = new HashSet<>(Arrays.asList("a", "b"));
        source.main().edit()
            .putInt("app_launcher_dock_corner_radius", -1)
            .putLong("surface_base_tint_since", 9_007_199_254_740_993L)
            .putFloat("surface_base_blur", 0.1f)
            .putBoolean("status_bar_twelve_hour", true)
            .putString("surface_custom_preset", "{\"look\":\"mist\"}")
            .putStringSet("app_launcher_hidden_apps_v1", pinned)
            .putBoolean("surface_inheritance_migrated", true)
            .commit();
        source.ai().edit().putFloat("tai_temperature", 0.7f).putString("tai_fn_cleanup_model", "gemma").commit();
        source.prefs.get(Store.FONTS).edit().putString("active_family_id", "jetbrains").putInt("weight", 450).commit();
        source.prefs.get(Store.PRIVILEGED).edit().putBoolean("priv_lane_enabled", false).commit();
        byte[] bundle = export(source);

        Install target = new Install("target", 2f);
        assertEquals(0, restore(target, bundle).failures);

        SharedPreferences main = target.main();
        assertEquals(-1, main.getInt("app_launcher_dock_corner_radius", 0));
        assertEquals(9_007_199_254_740_993L, main.getLong("surface_base_tint_since", 0));
        assertEquals(0.1f, main.getFloat("surface_base_blur", 0f), 0f);
        assertTrue(main.getBoolean("status_bar_twelve_hour", false));
        assertEquals("{\"look\":\"mist\"}", main.getString("surface_custom_preset", null));
        assertEquals(pinned, main.getStringSet("app_launcher_hidden_apps_v1", null));
        assertTrue("migration markers travel as they are", main.getBoolean("surface_inheritance_migrated", false));
        assertEquals(0.7f, target.ai().getFloat("tai_temperature", 0f), 0f);
        assertEquals("gemma", target.ai().getString("tai_fn_cleanup_model", null));
        assertEquals("jetbrains", target.prefs.get(Store.FONTS).getString("active_family_id", null));
        assertEquals(450, target.prefs.get(Store.FONTS).getInt("weight", 0));
        assertFalse(target.prefs.get(Store.PRIVILEGED).getBoolean("priv_lane_enabled", true));
    }

    @Test
    public void import_leavesExcludedKeysAlone_andResetsIncludedKeysTheBundleLacks() throws IOException {
        Install source = new Install("source", 2f);
        source.main().edit()
            .putString("app_launcher_az_usage_stats_v1", "source-usage")
            .putInt("managed_wallpaper_system_id", 11)
            .putBoolean("first_boot_tour_skipped", true)
            .commit();
        byte[] bundle = export(source);

        Install target = new Install("target", 2f);
        target.main().edit()
            .putString("app_launcher_az_usage_stats_v1", "target-usage")
            .putInt("managed_wallpaper_system_id", 42)
            .putString("current_session", "abc")
            .putInt("app_launcher_dock_height", 64)
            .commit();
        target.ai().edit()
            .putString("tai_huggingface_token", "hf_target")
            .putString("tai_max_tokens", "512")
            .commit();
        restore(target, bundle);

        assertEquals("target-usage", target.main().getString("app_launcher_az_usage_stats_v1", null));
        assertEquals(42, target.main().getInt("managed_wallpaper_system_id", 0));
        assertEquals("abc", target.main().getString("current_session", null));
        assertFalse(target.main().contains("first_boot_tour_skipped"));
        assertFalse("a setting the backup does not hold falls back to its default",
            target.main().contains("app_launcher_dock_height"));
        assertEquals("hf_target", target.ai().getString("tai_huggingface_token", null));
        assertFalse(target.ai().contains("tai_max_tokens"));
    }

    @Test
    public void secrets_neverReachTheBundle() throws IOException {
        Install source = new Install("source", 2f);
        source.ai().edit()
            .putString("tai_huggingface_token", "hf_SECRET_value")
            .putString("tai_api_token", "api-SECRET-token-1234567890")
            .putString("tai_gpu_probe_facts", "probe")
            .putString("tai_system_prompt_general", "be brief")
            .commit();
        Map<String, String> entries = entries(export(source));
        String all = String.join("\n", entries.values());
        assertFalse(all.contains("SECRET"));
        assertFalse(all.contains("tai_gpu_probe_facts"));
        assertTrue(all.contains("be brief"));
    }

    @Test
    public void hostileStore_cannotWriteASecretOrADeviceId() throws Exception {
        JSONObject ai = new JSONObject().put("store", "ai").put("values", new JSONObject()
            .put("tai_huggingface_token", new JSONObject().put("t", "string").put("v", "hf_planted"))
            .put("tai_top_k", new JSONObject().put("t", "int").put("v", "40")));
        JSONObject main = new JSONObject().put("store", "main").put("values", new JSONObject()
            .put("managed_wallpaper_system_id", new JSONObject().put("t", "int").put("v", "7")));
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest(1));
        entries.put("prefs/ai.json", ai.toString());
        entries.put("prefs/main.json", main.toString());

        Install target = new Install("target", 2f);
        restore(target, zip(entries));

        assertFalse(target.ai().contains("tai_huggingface_token"));
        assertEquals(40, target.ai().getInt("tai_top_k", 0));
        assertFalse(target.main().contains("managed_wallpaper_system_id"));
    }

    // ------------------------------------------------------------------ density

    @Test
    public void fontSize_keepsItsPhysicalSize_onAScreenOfAnotherDensity() throws IOException {
        Install source = new Install("source", 2f);
        source.main().edit().putString("fontsize", "24").putString("fontsize_scratchpad", "20")
            .putString("fontsize2", "200").commit();
        byte[] bundle = export(source);

        Install target = new Install("target", 3f);
        restore(target, bundle);

        assertEquals("36", target.main().getString("fontsize", null));
        assertEquals("30", target.main().getString("fontsize_scratchpad", null));
        assertEquals("clamped to the largest size", "256", target.main().getString("fontsize2", null));
    }

    @Test
    public void scaleFontSize_clampsToTheSmallestSize_andIgnoresAnUnknownDensity() {
        assertEquals(12, SettingsBackupFormat.scaleFontSize(4, 3f, 1f, 12, 256));
        assertEquals(30, SettingsBackupFormat.scaleFontSize(30, 0f, 2f, 8, 256));
    }

    // ------------------------------------------------------------------ files

    @Test
    public void keyboardFont_travelsAsAFile_andThePathPointsAtThisInstall() throws IOException {
        Install source = new Install("source", 2f);
        File sourceFont = source.file(Root.APP, SettingsBackupFormat.KEYBOARD_FONT_FILE);
        write(sourceFont, "font-bytes");
        source.main().edit().putString("in_app_keyboard_font_path", sourceFont.getAbsolutePath()).commit();
        byte[] bundle = export(source);
        assertFalse(String.join("\n", entries(bundle).values()).contains(sourceFont.getAbsolutePath()));

        Install target = new Install("target", 2f);
        restore(target, bundle);

        File targetFont = target.file(Root.APP, SettingsBackupFormat.KEYBOARD_FONT_FILE);
        assertEquals(targetFont.getAbsolutePath(), target.main().getString("in_app_keyboard_font_path", null));
        assertEquals("font-bytes", read(targetFont));
    }

    @Test
    public void configFiles_roundTrip_withoutTheManagedFontConfigOrLinksInATree() throws IOException {
        Install source = new Install("source", 2f);
        write(source.file(Root.TERMUX, "termux.properties"), "extra-keys = []");
        write(source.file(Root.TERMUX, "fonts.d/20-mine.conf"), "mine");
        write(source.file(Root.TERMUX, "fonts.d/10-launcher.conf"), "managed");
        write(source.file(Root.TERMUX, "theme-templates/nvim/template.conf"), "tpl");
        write(source.file(Root.TERMUX, "material-colors.properties"), "derived");
        write(source.file(Root.CONFIG, "termux.properties"), "secondary");
        write(source.file(Root.APP, "tai/memory-mode"), "normal");
        write(source.file(Root.APP, "keyboard-clipboard-pins.json"), "[\"pinned\"]");
        File outside = temp.newFile("outside.txt");
        write(outside, "outside");
        //noinspection ResultOfMethodCallIgnored
        source.file(Root.TERMUX, "workspaces").mkdirs();
        Files.createSymbolicLink(source.file(Root.TERMUX, "workspaces/escape").toPath(), outside.toPath());
        Map<String, String> entries = entries(export(source));

        assertTrue(entries.containsKey("files/termux/fonts.d/20-mine.conf"));
        assertFalse(entries.containsKey("files/termux/fonts.d/10-launcher.conf"));
        assertFalse(entries.containsKey("files/termux/material-colors.properties"));
        assertFalse(entries.containsKey("files/app/keyboard-clipboard-pins.json"));
        assertFalse(entries.containsKey("files/termux/workspaces/escape"));

        Install target = new Install("target", 2f);
        restore(target, export(source));
        assertEquals("extra-keys = []", read(target.file(Root.TERMUX, "termux.properties")));
        assertEquals("tpl", read(target.file(Root.TERMUX, "theme-templates/nvim/template.conf")));
        assertEquals("secondary", read(target.file(Root.CONFIG, "termux.properties")));
        assertEquals("normal", read(target.file(Root.APP, "tai/memory-mode")));
        assertFalse(target.file(Root.TERMUX, "fonts.d/10-launcher.conf").exists());
    }

    @Test
    public void shellLink_isCarriedAsItsTarget_onThisInstallsPrefix() throws IOException {
        Install source = new Install("source", 2f);
        File termux = source.roots.get(Root.TERMUX);
        termux.mkdirs();
        Files.createSymbolicLink(new File(termux, "shell").toPath(),
            new File(source.filesDir, "usr/bin/fish").toPath());
        Map<String, String> entries = entries(export(source));
        assertEquals("@FILES@/usr/bin/fish", entries.get("links/termux/shell"));
    }

    @Test
    public void linkTargets_rewriteBetweenEditions() {
        String logical = SettingsBackupFormat.linkTargetToLogical("/data/data/com.termux/files/usr/bin/zsh",
            Arrays.asList("/data/data/com.termux/files", "/data/user/0/com.termux/files"));
        assertEquals("@FILES@/usr/bin/zsh", logical);
        assertEquals("/data/data/io.vaj.tl/files/usr/bin/zsh",
            SettingsBackupFormat.linkTargetFromLogical(logical, "/data/data/io.vaj.tl/files"));
        assertEquals("/system/bin/sh", SettingsBackupFormat.linkTargetFromLogical("/system/bin/sh", "/x"));
        assertNull(SettingsBackupFormat.linkTargetFromLogical("@FILES@/../../etc", "/x"));
        assertNull(SettingsBackupFormat.linkTargetFromLogical("usr/bin/zsh", "/x"));
    }

    // ------------------------------------------------------------------ refusals

    @Test
    public void entryNames_outsideTheBackupsOwnPlaces_areRefused() {
        for (String name : new String[] {
            "files/termux/../../../evil", "/files/termux/termux.properties", "files/termux//x",
            "files/unknown/termux.properties", "files/app/keyboard-clipboard-pins.json",
            "files/termux/fonts.d/10-launcher.conf", "files/termux/fonts.d\\..\\x", "prefs/tai_secrets.json",
            "links/app/inapp-keyboard/label-font.ttf", "files/termux/./termux.properties", "evil.sh"}) {
            assertNull(name, SettingsBackupFormat.classify(name));
        }
        assertNotNull(SettingsBackupFormat.classify("files/termux/workspaces/a/b.json"));
        assertNotNull(SettingsBackupFormat.classify("files/config-termux/termux.properties"));
        assertNotNull(SettingsBackupFormat.classify("prefs/main.json"));
    }

    @Test
    public void maliciousEntry_refusesTheWholeFile_andWritesNothing() throws IOException {
        Install target = new Install("target", 2f);
        target.main().edit().putInt("app_launcher_dock_height", 64).commit();
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest(1));
        entries.put("prefs/main.json", "{\"store\":\"main\",\"values\":{}}");
        entries.put("files/termux/../../../escaped.txt", "pwned");
        try {
            SettingsBackupIo.read(target.env(), new ByteArrayInputStream(zip(entries)));
            fail("expected the file to be refused");
        } catch (InvalidBackupException e) {
            assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, e.reason);
        }
        assertEquals(64, target.main().getInt("app_launcher_dock_height", 0));
        assertFalse(new File(target.filesDir, "escaped.txt").exists());
        assertFalse(new File(target.filesDir.getParentFile(), "cache/settings-import").exists());
    }

    @Test
    public void unknownFormatVersion_isRefused() throws IOException {
        Install target = new Install("target", 2f);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifest(2));
        try {
            SettingsBackupIo.read(target.env(), new ByteArrayInputStream(zip(entries)));
            fail("expected the file to be refused");
        } catch (InvalidBackupException e) {
            assertEquals(InvalidBackupException.Reason.NEWER_FORMAT, e.reason);
        }
    }

    @Test
    public void aFileWithoutAManifest_isRefused() throws IOException {
        Install target = new Install("target", 2f);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("prefs/main.json", "{\"store\":\"main\",\"values\":{}}");
        try {
            SettingsBackupIo.read(target.env(), new ByteArrayInputStream(zip(entries)));
            fail("expected the file to be refused");
        } catch (InvalidBackupException e) {
            assertEquals(InvalidBackupException.Reason.NOT_A_BACKUP, e.reason);
        }
    }

    @Test
    public void suggestedFileName_namesTheDay() {
        assertEquals("termux-launcher-settings-2026-10-02.zip", SettingsBackupFormat.suggestedFileName(2026, 10, 2));
    }
}
