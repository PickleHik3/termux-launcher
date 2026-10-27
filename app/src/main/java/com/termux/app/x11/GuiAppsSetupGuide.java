package com.termux.app.x11;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.tour.TourEdition;
import com.termux.shared.termux.TermuxConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the Set up Linux apps guide asks, in which order, and what it remembers: pure policy, so
 * the screens only draw the answer. The choices are the launcher's ordinary preferences, under the
 * keys the screen has always used, so a pick made before the guide existed is still the pick.
 */
public final class GuiAppsSetupGuide {

    public static final String KEY_ROUTE = "gui_apps_route";
    public static final String KEY_DISTRO = "gui_apps_distro";
    public static final String KEY_STARTER_PREFIX = "gui_apps_starter_";
    /**
     * Set once the command has been copied: only "the command is in hand". "Set up" means the
     * command's done marker ({@link GuiAppsSetup#markerFile()}) exists.
     */
    public static final String KEY_COPIED = "gui_apps_setup_copied";

    private GuiAppsSetupGuide() {}

    /** Whether fcitx5 is in the prefix, which the guide's command installs. Tests replace it. */
    public static final java.util.function.BooleanSupplier FCITX5_ON_DISK =
        () -> new java.io.File(X11CliInstaller.FCITX5_PATH).exists();
    public static java.util.function.BooleanSupplier fcitx5Installed = FCITX5_ON_DISK;

    /** Whether the command's done marker is on disk. Tests replace it. */
    public static final java.util.function.BooleanSupplier MARKER_ON_DISK =
        () -> GuiAppsSetup.markerFile().exists();
    public static java.util.function.BooleanSupplier markerPresent = MARKER_ON_DISK;

    /** Whether the distro's root filesystem exists, for migrating pre-marker installs. Tests replace it. */
    public static final java.util.function.Predicate<GuiAppsSetup.Distro> ROOTFS_ON_DISK =
        d -> new java.io.File(com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH
            + "/var/lib/proot-distro/installed-rootfs/" + d.alias).isDirectory();
    public static java.util.function.Predicate<GuiAppsSetup.Distro> rootfsInstalled = ROOTFS_ON_DISK;

    /**
     * Whether the setup command finished: its marker exists. An install from before the marker
     * (command copied, fcitx5 installed, and the chosen distro's rootfs present on the distro
     * route) is taken as finished once, and gets its marker written then.
     */
    public static boolean finished(@NonNull SharedPreferences prefs, @NonNull TourEdition edition) {
        if (markerPresent.getAsBoolean()) return true;
        if (!prefs.getBoolean(KEY_COPIED, false) || !fcitx5Installed.getAsBoolean()) return false;
        Choices choices = read(prefs, edition);
        if (choices.route == GuiAppsSetup.Route.DISTRO && !rootfsInstalled.test(choices.distro)) return false;
        try {
            java.io.File file = GuiAppsSetup.markerFile();
            if (file.getParentFile().mkdirs() || file.getParentFile().isDirectory()) {
                java.nio.file.Files.write(file.toPath(),
                    (choices.route.key + " " + choices.distro.alias + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // Best effort: the answer is still yes this time.
        }
        return true;
    }

    /**
     * Whether Linux apps are set up as far as fcitx5-backed features care: the guide's command finished
     * (its done marker exists, the fact the Linux apps row reads) and fcitx5 is installed.
     * A stray fcitx5 left in the prefix alone does not count. An edition with no guide (nix) has nothing to wait
     * for, so it counts as set up.
     */
    public static boolean isSetUp(@NonNull Context context) {
        TourEdition edition = TourEdition.of(context.getPackageName());
        if (GuiAppsSetup.routesFor(edition).isEmpty()) return true;
        return finished(preferences(context), edition) && fcitx5Installed.getAsBoolean();
    }

    /** One screen of the guide. */
    public enum Step { SOURCE, DISTRO, STARTERS, COMMAND, GRAPHICS }

    /**
     * The steps that exist for {@code edition} once {@code route} is chosen, in order. A source
     * that is Termux's own apps has no Linux to pick; an edition with no route at all (nix) has no
     * guide, only its own page of advice.
     */
    @NonNull
    public static List<Step> stepsFor(@NonNull TourEdition edition, @Nullable GuiAppsSetup.Route route) {
        List<GuiAppsSetup.Route> routes = GuiAppsSetup.routesFor(edition);
        if (routes.isEmpty()) return Collections.emptyList();
        List<Step> steps = new ArrayList<>();
        if (routes.size() > 1) steps.add(Step.SOURCE);
        GuiAppsSetup.Route effective = route == null ? routes.get(0) : route;
        if (effective == GuiAppsSetup.Route.DISTRO) steps.add(Step.DISTRO);
        steps.add(Step.STARTERS);
        steps.add(Step.COMMAND);
        steps.add(Step.GRAPHICS);
        return steps;
    }

    /**
     * The starter apps worth a choice: a browser and a file manager. VAJ's own apps have no
     * browser, so that route offers the file manager alone.
     */
    @NonNull
    public static List<GuiAppsSetup.StarterApp> startersOffered(@NonNull TourEdition edition,
                                                                @NonNull GuiAppsSetup.Route route) {
        List<GuiAppsSetup.StarterApp> offered = new ArrayList<>();
        if (!(edition == TourEdition.VAJ && route == GuiAppsSetup.Route.X11_REPO)) {
            offered.add(GuiAppsSetup.StarterApp.BROWSER);
        }
        offered.add(GuiAppsSetup.StarterApp.FILE_MANAGER);
        return offered;
    }

    /** What the user has picked so far. */
    public static final class Choices {
        @NonNull public final GuiAppsSetup.Route route;
        @NonNull public final GuiAppsSetup.Distro distro;
        @NonNull public final Set<GuiAppsSetup.StarterApp> starters;

        Choices(@NonNull GuiAppsSetup.Route route, @NonNull GuiAppsSetup.Distro distro,
                @NonNull Set<GuiAppsSetup.StarterApp> starters) {
            this.route = route;
            this.distro = distro;
            this.starters = starters;
        }
    }

    @NonNull
    public static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE);
    }

    /** The stored choices, with this edition's own default where nothing (valid) is stored. */
    @NonNull
    public static Choices read(@NonNull SharedPreferences prefs, @NonNull TourEdition edition) {
        String stored = prefs.getString(KEY_ROUTE, null);
        GuiAppsSetup.Route route = null;
        for (GuiAppsSetup.Route candidate : GuiAppsSetup.routesFor(edition)) {
            if (candidate.key.equals(stored)) route = candidate;
        }
        if (route == null) route = GuiAppsSetup.defaultRoute(edition);
        if (route == null) route = GuiAppsSetup.Route.X11_REPO;
        GuiAppsSetup.Distro distro = GuiAppsSetup.Distro.of(prefs.getString(KEY_DISTRO, null));
        Set<GuiAppsSetup.StarterApp> starters = EnumSet.noneOf(GuiAppsSetup.StarterApp.class);
        for (GuiAppsSetup.StarterApp app : startersOffered(edition, route)) {
            if (prefs.getBoolean(KEY_STARTER_PREFIX + app.key, true)) starters.add(app);
        }
        return new Choices(route, distro, starters);
    }

    /** The command for what is picked, or null when the edition builds none (nix). */
    @Nullable
    public static String command(@NonNull SharedPreferences prefs, @NonNull TourEdition edition) {
        if (GuiAppsSetup.routesFor(edition).isEmpty()) return null;
        Choices choices = read(prefs, edition);
        return GuiAppsSetup.command(choices.route, choices.distro, choices.starters, edition);
    }

    /** The name of the source or Linux the picks add up to. */
    @NonNull
    public static String systemName(@NonNull Context context, @NonNull Choices choices) {
        if (choices.route == GuiAppsSetup.Route.X11_REPO) {
            return context.getString(R.string.settings_gui_apps_source_termux);
        }
        return distroName(context, choices.distro);
    }

    @NonNull
    public static String distroName(@NonNull Context context, @NonNull GuiAppsSetup.Distro distro) {
        switch (distro) {
            case UBUNTU: return context.getString(R.string.settings_gui_apps_distro_ubuntu);
            case ARCH: return context.getString(R.string.settings_gui_apps_distro_arch);
            default: return context.getString(R.string.settings_gui_apps_distro_debian);
        }
    }

    /**
     * The Linux apps row's summary: "Debian with a browser and a file manager" once the command has
     * finished, "Command copied" until it has, "Not set up yet" before the copy.
     */
    @NonNull
    public static String rowSummary(@NonNull Context context, @NonNull SharedPreferences prefs,
                                    @NonNull TourEdition edition) {
        if (GuiAppsSetup.routesFor(edition).isEmpty()) {
            return context.getString(R.string.settings_gui_apps_row_summary_nix);
        }
        if (!finished(prefs, edition)) {
            return context.getString(prefs.getBoolean(KEY_COPIED, false)
                ? R.string.settings_gui_apps_row_summary_copied : R.string.settings_gui_apps_row_summary);
        }
        Choices choices = read(prefs, edition);
        String name = systemName(context, choices);
        boolean browser = choices.starters.contains(GuiAppsSetup.StarterApp.BROWSER);
        boolean files = choices.starters.contains(GuiAppsSetup.StarterApp.FILE_MANAGER);
        if (browser && files) return context.getString(R.string.settings_gui_apps_row_with_both, name);
        if (browser) return context.getString(R.string.settings_gui_apps_row_with_browser, name);
        if (files) return context.getString(R.string.settings_gui_apps_row_with_files, name);
        return name;
    }
}
