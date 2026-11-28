package com.termux.app.settings;

import com.termux.app.leader.LeaderChordSettingAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.LayoutStore;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.registry.FontSizeSettingsAdapter;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Writes the settings reference page ({@code docs/en/Settings_Reference.md}) from the Settings
 * registry: the same declarations and adapters {@code launcherctl config list} walks, over empty
 * stores, so the page and the command cannot name different settings. {@link
 * SettingsReferenceTest} fails when the page in the tree differs from {@link #render}.
 */
final class SettingsReference {

    /** The page's file name in {@code docs/en}. */
    static final String PAGE = "Settings_Reference.md";

    private static final String[] SECTIONS = {"Colours and text", "Motion and battery",
        "Status bar, dock and Home", "Keys", "Terminals, keyboard and Linux apps", "Look",
        "Layout sizes"};

    private SettingsReference() {}

    /** The registry {@code config list} reads, over stores that hold nothing. */
    @NonNull
    static SettingsRegistry registry() {
        InMemorySettingStore store = new InMemorySettingStore();
        LayoutStore layouts = new LayoutStore(new LayoutStore.Storage() {
            @Nullable @Override public String read(@NonNull String key) { return null; }
            @Override public void write(@NonNull Map<String, String> documents) { }
        }, false);
        List<SettingAdapter> adapters = new ArrayList<>();
        adapters.add(new LookSettingsAdapter(new LookGrid(new LookGrid.MemoryStore()),
            new LookCommand.TerminalBackground() {
                @Override public int get(@NonNull LookCommand.Setting setting) {
                    return setting.shipped;
                }
                @Override public void set(@NonNull LookCommand.Setting setting, int value) { }
            }, () -> { }));
        adapters.add(new FontSizeSettingsAdapter(store, 1f, ""));
        adapters.add(new LeaderChordSettingAdapter(store));
        adapters.add(new TerminalColoursAdapter(new com.termux.app.colours.TerminalColours(
            new com.termux.app.colours.TerminalColours.Source() {
                @Override public boolean fromWallpaper() { return true; }
                @Override public void setFromWallpaper(boolean on) { }
            }, new com.termux.app.colours.ColourSchemeFile(
                new java.io.File("build/no-such-colors.properties"))), () -> { }));
        adapters.add(new KeySpacingAdapter(new KeySpacingAdapter.Store() {
            @Override public float scale() {
                return com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants
                    .TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE;
            }
            @Override public void setScale(float scale) { }
        }));
        adapters.add(new LowMemoryPresetAdapter(new LowMemoryPresetAdapter.Preset() {
            @Override public boolean isOn() { return false; }
            @Override public void turnOn() { }
            @Override public void turnOff() { }
        }));
        adapters.add(new LiveBlurSettingAdapter(new LiveBlurSettingAdapter.Memory(false)));
        adapters.add(new LayoutSettingsAdapter(layouts, new LayoutSettingsAdapter.Main() {
            @Override public <T> T call(@NonNull Callable<T> work) throws Exception {
                return work.call();
            }
        }, () -> { }));
        adapters.add(new UseAsAdapter(new UseAsAdapter.Store() {
            @Nullable @Override public String read() { return null; }
            @Override public void write(@NonNull String value) { }
        }));
        return new SettingsRegistry(which -> store,
            SettingDeclarations.ALL, adapters);
    }

    /** The page, as it should be checked in. */
    @NonNull
    static String render() {
        StringBuilder page = new StringBuilder();
        page.append("# Settings reference\n\n")
            .append("<!-- Generated from the launcher's settings; do not edit by hand. To update it, run\n")
            .append("REGENERATE_SETTINGS_REFERENCE=1 ./gradlew")
            .append(" :app:testDebugUnitTest --tests 'com.termux.app.settings.SettingsReferenceTest' -->\n\n")
            .append("Every setting [`launcherctl config`](LauncherCtl.md#config) can read and change, ")
            .append("with the values it accepts and what a fresh install uses. A value outside the ")
            .append("range is refused and nothing changes.\n");
        for (String heading : SECTIONS) {
            page.append('\n').append("## ").append(heading).append("\n\n")
                .append("| Setting | Id | Values | Default | What it does |\n")
                .append("|---|---|---|---|---|\n");
            for (SettingsRegistry.Entry entry : registry().list()) {
                SettingDeclaration declaration = entry.declaration;
                if (!shown(declaration) || !sectionOf(declaration.id).equals(heading)) continue;
                page.append("| ").append(declaration.title)
                    .append(" | `").append(declaration.id).append('`')
                    .append(" | ").append(values(declaration))
                    .append(" | ").append(defaultOf(declaration))
                    .append(" | ").append(declaration.summary).append(" |\n");
            }
        }
        page.append("\nThe other layout fields, such as where a bar sits on a page, are listed by ")
            .append("`launcherctl config list` and described under ")
            .append("[layout](LauncherCtl.md#layout).\n");
        return page.toString();
    }

    /** Whether the page lists the setting: retired look properties stay in `config list` only. */
    static boolean shown(@NonNull SettingDeclaration declaration) {
        return !LookSettingsAdapter.RETIRED.equals(declaration.summary);
    }

    @NonNull
    private static String sectionOf(@NonNull String id) {
        if (id.startsWith("look.")) return "Look";
        if (id.startsWith("keys.")) return "Keys";
        if (id.startsWith("layout.")) return "Layout sizes";
        if (id.startsWith("motion.") || id.startsWith("battery.") || id.startsWith("glass.")
            || id.equals("terminal.effect") || id.equals("terminal.screen_events")
            || id.equals("terminal.effect_composite")) return "Motion and battery";
        if (id.equals("terminal.core") || id.equals("terminal.scrollback")
            || id.equals("terminal.bell") || id.startsWith("keyboard.") || id.startsWith("linux.")
            || id.startsWith("cutout.")) return "Terminals, keyboard and Linux apps";
        if (id.startsWith("status.") || id.startsWith("dock.") || id.startsWith("notifications.")
            || id.startsWith("home.") || id.startsWith("drawer.")) return "Status bar, dock and Home";
        return "Colours and text";
    }

    @NonNull
    private static String values(@NonNull SettingDeclaration declaration) {
        if (!declaration.rangeNote.isEmpty()) return declaration.rangeNote;
        if (declaration.kind == SettingDeclaration.Kind.INTEGER)
            return declaration.min + " to " + declaration.max;
        return declaration.allowed();
    }

    @NonNull
    private static String defaultOf(@NonNull SettingDeclaration declaration) {
        if (!declaration.defaultNote.isEmpty()) return declaration.defaultNote;
        return declaration.defaultValue();
    }
}
