package com.termux.app.window;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The window restructure's source-scan rules (ADR 0028 sections 3, 6, 7): imports flow elements to
 * page to glass to window and nothing imports the activity; only the pipeline registers frame hooks;
 * no bringToFront; no Z for ordering; no addView to the decor or beside the root; no Host over 15
 * methods. Every offender today sits on one allow-list per rule below. The lists may only shrink: a
 * step that fixes a site deletes (or lowers) its line, and the test fails on a stale line as it does
 * on a new offender. The new packages (window, glass, page) have no entries anywhere.
 *
 * Entries are {@code path:N} (N sites in the file, exact) or a plain string. Paths are relative to
 * {@code com/termux}. Same pattern as LayoutSourceRulesTest.
 */
public class WindowSourceRulesTest {

    private static final int HOST_METHOD_CAP = 15;

    /** Packages with imports pointing up the direction; one "path imports node" each. */
    static final List<String> UPWARD_IMPORTS = Arrays.asList();

    /** Mutual import pairs between packages; a step that breaks one deletes its line. */
    static final List<String> CYCLE_PAIRS = Arrays.asList(
        // element edge: cut when either side takes a Host
        "activities <-> fragments",
        // element edge: cut when either side takes a Host
        "ai <-> launcher",
        // page and element edge: cut as the page steps (5 to 7) land
        "ai <-> terminal",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> dock",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> launcher",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> layouteditor",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> place",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> statusbar",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> surfaces",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> terminal",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> theme",
        // chrome holds glass and the pipeline: steps 2 and 4 move them out
        "chrome <-> wall",
        // page and element edge: cut as the page steps (5 to 7) land
        "dock <-> launcher",
        // page and element edge: cut as the page steps (5 to 7) land
        "dock <-> place",
        // page and element edge: cut as the page steps (5 to 7) land
        "dock <-> statusbar",
        // element edge: cut when either side takes a Host
        "fragments <-> launcher",
        // element edge: cut when either side takes a Host
        "fragments <-> settings",
        // element edge: cut when either side takes a Host
        "fragments <-> surfaces",
        // element edge: cut when either side takes a Host
        "fragments <-> x11",
        // element edge: cut when either side takes a Host
        "launcher <-> launcherctl",
        // page and element edge: cut as the page steps (5 to 7) land
        "launcher <-> statusbar",
        // element edge: cut when either side takes a Host
        "launcher <-> surfaces",
        // page and element edge: cut as the page steps (5 to 7) land
        "launcher <-> terminal",
        // page and element edge: cut as the page steps (5 to 7) land
        "launcher <-> wall",
        // element edge: cut when either side takes a Host
        "launcher <-> x11",
        // page and element edge: cut as the page steps (5 to 7) land
        "launcherctl <-> terminal",
        // page and element edge: cut as the page steps (5 to 7) land
        "place <-> terminal",
        // element edge: cut when either side takes a Host
        "sound <-> x11",
        // page and element edge: cut as the page steps (5 to 7) land
        "statusbar <-> surfaces",
        // page and element edge: cut as the page steps (5 to 7) land
        "statusbar <-> terminal",
        // page and element edge: cut as the page steps (5 to 7) land
        "terminal <-> theme",
        // page and element edge: cut as the page steps (5 to 7) land
        "terminal <-> wall"
    );

    /** Longer cycles that no pair explains. */
    static final List<String> LONGER_CYCLES = Arrays.asList(
        // settings reaches the privileged lane through the terminal
        "privileged -> terminal -> settings -> privileged"
    );

    /** Files outside the activity's package that name TermuxActivity. */
    static final List<String> ACTIVITY_USERS = Arrays.asList(
        // names the activity: takes a Host or PageHost instead
        "app/dock/PromptLineFieldView.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fonts/FontInstallCoordinator.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/AboutSupportPreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/ExtraKeysEditorFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/GeneralPreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/KeyboardPreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/LauncherPreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/PerformancePreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/ServicesPermissionsPreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/SettingsEditorDoors.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/TerminalViewPreferencesDataStore.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/fragments/settings/termux/TermuxStylePreferencesFragment.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/help/HelpActivity.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/launcher/data/IconPackChoices.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/setup/SetupCardSystem.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/surfaces/AppearanceEditorEntry.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/ShellNotifications.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/TerminalHintsOverlay.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/TerminalKeyInspector.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/TerminalScrollbackSearchOverlay.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/TerminalSessionBrowser.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/TermuxActivityRootView.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/io/TerminalToolbarPages.java:1",
        // names the activity: takes a Host or PageHost instead
        "app/terminal/io/TermuxTerminalExtraKeys.java:1",
        // names the activity: takes a Host or PageHost instead
        "launcherctl/DeviceControlRoutes.java:1"
    );

    /** Files that register a frame hook outside the pipeline, with the count of registrations. */
    static final List<String> FRAME_HOOKS = Arrays.asList(
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/AzPullTabLayer.java:2",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/DockPlankController.java:1",
        // local first-layout wait: moves onto the first-layout helper
        "app/DockShelfView.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/SuggestionBarView.java:1",
        // shared chrome or gate: becomes a pipeline step or Hold (ADR 0028 s3)
        "app/TermuxActivity.java:3",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/assistant/AssistantPageController.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/chrome/LivePicture.java:2",
        // local first-layout wait: moves onto the first-layout helper
        "app/chrome/scene/SceneInvalTrace.java:2",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/chrome/scene/SceneLayerView.java:4",
        // shared chrome or gate: becomes a pipeline step or Hold (ADR 0028 s3)
        "app/dock/DockController.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/dock/PeekGlassReflection.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/launcher/drawer/AppDrawerContentView.java:4",
        // local first-layout wait: moves onto the first-layout helper
        "app/launcher/drawer/AppDrawerController.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/launcher/drawer/AppDrawerDragOverlayView.java:1",
        // local first-layout wait: moves onto the first-layout helper
        "app/launcher/widget/WidgetPaneView.java:1",
        // local first-layout wait: moves onto the first-layout helper
        "app/launcher/widget/builtin/BuiltinWidgetView.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/setup/SetupWizardController.java:2",
        // the slider preview's per-frame coalescer: it must apply before the frame's layout, which
        // only a table step ahead of CHROME_COMMIT could do (ADR 0028 s1 pins that order)
        "app/surfaces/SurfaceEditorController.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/terminal/CommandPaletteView.java:2",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/terminal/PaneMotionOverlayView.java:1",
        // local first-layout wait: moves onto the first-layout helper
        "app/terminal/PreDrawHook.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/terminal/TerminalCommandPaletteController.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/terminal/TerminalWindowBar.java:2",
        // shared chrome or gate: becomes a pipeline step or Hold (ADR 0028 s3)
        "app/terminal/extrakeys/HomePillFlightPlayer.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/wall/PageOverviewView.java:4",
        // local first-layout wait: moves onto the first-layout helper
        "app/wall/PaneWallLayout.java:1",
        // own frame tick or fling step: moves into the pipeline table or the helper
        "app/x11/DisplayTouchpadView.java:2"
    );

    /**
     * Files that call bringToFront, or bringChildToFront outside the window package, with the count
     * of calls. Empty: the layer number orders layers, and a raise inside one layer goes through
     * WindowLayers.raiseWithin.
     */
    static final List<String> BRING_TO_FRONT = Arrays.asList();

    /** Files that write elevation or translationZ (shadow or order), with the count of writes. */
    static final List<String> Z_WRITES = Arrays.asList(
        // Z doubles as draw order: the A-Z row is rebuilt in #260
        "app/AzScrubRowView.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/SuggestionBarView.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/fragments/settings/termux/ExtraKeysEditorFragment.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/fragments/settings/termux/GraphicsAccelerationFragment.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/fragments/settings/termux/TaiBenchChooseFragment.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/fragments/settings/termux/TaiBenchHomeFragment.java:1",
        // Z doubles as draw order: the layer number replaces it
        "app/help/HelpOverlayView.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/launcher/popup/AnchoredMenu.java:3",
        // elevation for a shadow or lift: classify when its layer moves
        "app/launcher/popup/AppMenuCard.java:3",
        // elevation for a shadow or lift: classify when its layer moves
        "app/launcher/widget/HomeFolderPopup.java:2",
        // lift shadow of the carried cell (it draws over its neighbours by child order)
        "app/launcher/widget/WidgetPaneController.java:6",
        // elevation for a shadow or lift: classify when its layer moves
        "app/launcher/widget/builtin/NotificationsWidgetView.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/material/M3.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/notice/AppNoticeHostView.java:1",
        // Z doubles as draw order: the row stacks are rebuilt in #260
        "app/place/EdgeStackView.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/setup/AppearanceResetCard.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/setup/GlassResetNudgeCard.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/setup/SetupCardSheet.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/setup/SetupSheetView.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/surfaces/EditorBar.java:3",
        // elevation for a shadow or lift: classify when its layer moves
        "app/surfaces/EditorCardSheet.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/surfaces/cards/BarsList.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/DepartureCard.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/TerminalCommandPaletteController.java:2",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/TerminalHintPanelView.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/TerminalModeHintCard.java:1",
        // the float's shadow and its ramp (floats stand after the tiles in child order)
        "app/terminal/TerminalPaneController.java:6",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/TerminalSheetController.java:1",
        // the lifted chip's shadow (it draws last by the strip's draw order)
        "app/terminal/TerminalWindowBar.java:3",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/extrakeys/HomePillView.java:3",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/inappkeyboard/FloatingKeyboardFrame.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/inappkeyboard/voice/VoiceListeningIndicator.java:1",
        // elevation for a shadow or lift: classify when its layer moves
        "app/terminal/keybind/KeybindHintPresenter.java:1"
    );

    /** Files that reach the decor or the content view while adding views, with the count of sites. */
    static final List<String> DECOR_OR_CONTENT_ADDS = Arrays.asList(
        // decor and content view: moves into the layer hosts
        "app/TermuxActivity.java:73",
        // reads the decor to find the keys under a gesture; its debug recorder now wraps the window root
        "app/chrome/scene/SceneController.java:1",
        // decor and content view: moves into the layer hosts
        "app/chrome/sheets/SheetsController.java:2"
    );

    /** Host interfaces over the cap, as path#Name (listed until under the cap, then deleted). */
    static final List<String> BIG_HOSTS = Arrays.asList(
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/chrome/sheets/SheetsController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/dock/DockController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/launcher/drawer/AppDrawerController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/launcher/widget/WidgetPaneController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/place/CrossingController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/statusbar/StatusBarController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/surfaces/SurfaceEditorController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/surfaces/cards/DockCard.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/terminal/TerminalCommandPaletteController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/terminal/TerminalHost.java#TerminalHost:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/terminal/TerminalPaneController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/terminal/io/HomePillController.java#Host:1",
        // over 15 methods: shrinks as its step moves needs into the pipeline state or the new modules
        "app/wall/PageOverviewView.java#Host:1"
    );

    private static Map<String, String> sources;

    private static Map<String, String> sources() throws IOException {
        if (sources == null) sources = SourceRules.load(root());
        return sources;
    }

    @Test
    public void importsFlowElementsToPageToGlassToWindow() throws IOException {
        List<String> problems = new ArrayList<>(
            SourceRules.compareSet("import direction", SourceRules.upwardImports(sources()), UPWARD_IMPORTS));
        assertNone(problems);
    }

    @Test
    public void noNewImportCycleBetweenPackages() throws IOException {
        Map<String, Set<String>> graph = SourceRules.packageGraph(sources());
        Set<String> pairs = SourceRules.cyclePairs(graph);
        List<String> problems = new ArrayList<>(SourceRules.compareSet("cycle pair", pairs, CYCLE_PAIRS));
        java.util.Set<String> longer = new java.util.TreeSet<>(SourceRules.residualCycles(graph, pairs));
        problems.addAll(SourceRules.compareSet("longer cycle", longer, LONGER_CYCLES));
        assertNone(problems);
    }

    @Test
    public void nothingNewImportsTheActivity() throws IOException {
        assertNone(SourceRules.compareSet("imports TermuxActivity",
            SourceRules.activityUsers(sources()), ACTIVITY_USERS));
    }

    @Test
    public void onlyThePipelineRegistersFrameHooks() throws IOException {
        assertNone(SourceRules.compare("frame hook", SourceRules.frameHooks(sources()), FRAME_HOOKS));
    }

    @Test
    public void noBringToFront() throws IOException {
        assertNone(SourceRules.compare("bringToFront", SourceRules.bringToFront(sources()), BRING_TO_FRONT));
    }

    @Test
    public void noZUsedForOrdering() throws IOException {
        assertNone(SourceRules.compare("Z write", SourceRules.zWrites(sources()), Z_WRITES));
    }

    @Test
    public void nothingIsAddedToTheDecorOrBesideTheRoot() throws IOException {
        assertNone(SourceRules.compare("decor or content add",
            SourceRules.decorOrContentAdds(sources()), DECOR_OR_CONTENT_ADDS));
    }

    @Test
    public void noHostOverTheMethodCap() throws IOException {
        Map<String, Integer> hosts = new TreeMap<>();
        for (String key : SourceRules.bigHosts(sources(), HOST_METHOD_CAP).keySet()) hosts.put(key, 1);
        assertNone(SourceRules.compare("Host over " + HOST_METHOD_CAP + " methods", hosts, BIG_HOSTS));
    }

    @Test
    public void homeWidgetsAndFloatsReachGlassOnlyThroughTheHomePage() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> source : sources().entrySet()) {
            String path = source.getKey();
            boolean home = path.startsWith("app/launcher/widget/")
                || (path.startsWith("app/wall/WidgetPane") && path.endsWith(".java"))
                || path.equals("app/terminal/HomeFloatLayer.java");
            if (home) {
                for (String banned : new String[]{"GlassService", "LiveBackdrop.RANK_", "attachLivePicture"}) {
                    if (source.getValue().contains(banned)) problems.add(path + " mentions " + banned);
                }
            }
        }
        String activity = sources().get("app/TermuxActivity.java");
        for (String banned : new String[]{"glassElement(\"widget\"", "attachWidgetLiveGlass", "RANK_WIDGET"}) {
            if (activity.contains(banned)) problems.add("TermuxActivity mentions " + banned);
        }
        String pane = sources().get("app/terminal/TerminalPaneController.java");
        int at = pane.indexOf("private void syncFloatLiveGlass(");
        int open = at < 0 ? -1 : pane.indexOf('{', at);
        int depth = 0;
        int end = open;
        for (int i = open; i >= 0 && i < pane.length(); i++) {
            char c = pane.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) { end = i; break; }
        }
        if (at < 0 || !pane.substring(open, end).contains("mHomeFloatGlass")) {
            problems.add("syncFloatLiveGlass does not route Home floats through mHomeFloatGlass");
        }
        assertNone(problems);
    }

    @Test
    public void theBarsHaveNoWallpaperTwin() throws IOException {
        Path layout = root().resolve("../../../../main/res/layout/activity_termux.xml").normalize();
        String xml = new String(Files.readAllBytes(layout), java.nio.charset.StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        for (String twin : new String[]{"terminal_status_bar_wallpaper_backdrop",
            "terminal_window_bar_wallpaper_backdrop", "terminal_window_bar_wallpaper_backdrop_side"}) {
            if (xml.contains("@+id/" + twin + "\"")) problems.add("twin still declared: " + twin);
        }
        for (String glass : new String[]{"terminal_status_bar_glass_surface",
            "terminal_window_bar_background", "terminal_window_bar_background_side"}) {
            java.util.regex.Matcher tag = java.util.regex.Pattern
                .compile("<([\\w.]+)[^>]*?@\\+id/" + glass + "\"", java.util.regex.Pattern.DOTALL)
                .matcher(xml);
            if (!tag.find() || !"com.termux.app.chrome.BarGlassView".equals(tag.group(1)))
                problems.add(glass + " is not a BarGlassView");
        }
        assertNone(problems);
    }

    @Test
    public void theAppsRowWritesNoParentClipFocusOrLiftTarget() throws IOException {
        List<String> problems = new ArrayList<>();
        String row = new String(Files.readAllBytes(root().resolve("app/SuggestionBarView.java")),
            java.nio.charset.StandardCharsets.UTF_8);
        for (String write : new String[]{"parentGroup.setClip", "grandParentGroup.setClip",
            "azFocusedView.set", "azFocusedView.animate", "target.setScaleX", "target.setAlpha",
            "ObjectAnimator.ofFloat(target", "LIFT_SOURCE_SHADE", "slot.setAlpha"}) {
            if (row.contains(write)) problems.add("SuggestionBarView still writes: " + write);
        }
        Path layout = root().resolve("../../../../main/res/layout/activity_termux.xml").normalize();
        String xml = new String(Files.readAllBytes(layout), java.nio.charset.StandardCharsets.UTF_8);
        for (String id : new String[]{"apps_bar_viewpager", "place_apps_bar_host"}) {
            java.util.regex.Matcher tag = java.util.regex.Pattern
                .compile("<([\\w.]+)[^>]*?@\\+id/" + id + "\"([^>]*)>", java.util.regex.Pattern.DOTALL)
                .matcher(xml);
            if (!tag.find()) {
                problems.add(id + " is not declared");
                continue;
            }
            String attrs = tag.group(0);
            if (!attrs.contains("android:clipChildren=\"false\""))
                problems.add(id + " does not declare clipChildren false");
            if (!attrs.contains("android:clipToPadding=\"false\""))
                problems.add(id + " does not declare clipToPadding false");
        }
        assertNone(problems);
    }

    @Test
    public void theAppsRowHasNoCrossingMorph() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String file : new String[]{"app/SuggestionBarView.java", "app/place/Crossing.java",
            "app/place/CrossingViews.java"}) {
            String text = new String(Files.readAllBytes(root().resolve(file)),
                java.nio.charset.StandardCharsets.UTF_8);
            for (String word : new String[]{"DockMorph", "beginDockMorph", "dockMorphFrame",
                "endDockMorph", "slotMorph"}) {
                if (text.contains(word)) problems.add(file + " still mentions " + word);
            }
        }
        assertNone(problems);
    }

    @Test
    public void theAppsRowHasNoStableBoundsWait() throws IOException {
        String file = "app/SuggestionBarView.java";
        String text = new String(Files.readAllBytes(root().resolve(file)),
            java.nio.charset.StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        for (String word : new String[]{"suppressDrawUntilStableLayout", "hasStableRenderBounds",
            "renderLostWhileUnstable", "getParent().getHeight", "host.getHeight"}) {
            if (text.contains(word)) problems.add(file + " still mentions " + word);
        }
        assertNone(problems);
    }

    @Test
    public void theAppsRowWritesNoTickStrip() throws IOException {
        String file = "app/SuggestionBarView.java";
        String text = new String(Files.readAllBytes(root().resolve(file)),
            java.nio.charset.StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        if (text.contains("PageTickStripView")) {
            problems.add(file + " still mentions PageTickStripView");
        }
        assertNone(problems);
    }

    /**
     * One kind enum for pages (ADR 0030 section 1): the older kind types are gone, and nothing outside
     * {@code app/page/PageKind.java} declares an enum of the four page kinds. Storage keys
     * ({@code LookGrid.Kind}, {@code LayoutKind}) are read from {@code PageKind}; they are not page kinds.
     */
    @Test
    public void onlyPageKindNamesPageKinds() throws IOException {
        java.util.regex.Pattern old = java.util.regex.Pattern.compile(
            "\\bPaneWallPage\\b|\\bWallPages\\.PageKind\\b|\\bDockSpec\\.PageKind\\b"
                + "|\\bPageSwitchModel\\.Kind\\b|\\benum\\s+(PageKind|Kind)\\s*\\{\\s*HOME\\s*,");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> source : sources().entrySet()) {
            if (source.getKey().equals("app/page/PageKind.java")) continue;
            String[] lines = source.getValue().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (old.matcher(lines[i]).find()) problems.add(source.getKey() + ":" + (i + 1) + " " + lines[i].trim());
            }
        }
        assertNone(problems);
    }

    @Test
    public void theNewPackagesHaveNoAllowListEntries() {
        List<String> all = new ArrayList<>();
        for (List<String> list : Arrays.asList(UPWARD_IMPORTS, CYCLE_PAIRS, LONGER_CYCLES,
            ACTIVITY_USERS, FRAME_HOOKS, BRING_TO_FRONT, Z_WRITES, DECOR_OR_CONTENT_ADDS, BIG_HOSTS)) {
            all.addAll(list);
        }
        List<String> bad = new ArrayList<>();
        for (String entry : all) {
            for (String pkg : SourceRules.NEW_PACKAGES) {
                boolean path = entry.startsWith("app/" + pkg + "/");
                boolean edge = entry.matches(".*(^|[^\\w])" + pkg + "([^\\w]|$).*")
                    && (entry.contains(" -> ") || entry.contains(" <-> ") || entry.contains(" imports "));
                if (path || edge) bad.add(entry);
            }
        }
        assertTrue("new packages must not be on an allow-list: " + bad, bad.isEmpty());
    }

    private static void assertNone(List<String> problems) {
        assertTrue("source-scan rule broken (" + problems.size() + "):\n  "
            + String.join("\n  ", problems), problems.isEmpty());
    }

    private static Path root() {
        for (String candidate : new String[]{"src/main/java/com/termux", "app/src/main/java/com/termux"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("app sources not found from " + Paths.get("").toAbsolutePath());
    }
}
