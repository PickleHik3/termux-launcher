package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The distro containers {@code proot-distro} keeps in the prefix, and how an app installed inside
 * one is run. A container is a whole Linux filesystem under the launcher's own data directory, so
 * reading it is ordinary file reading — no process is started to list anything.
 *
 * <p>Only the 5.x layout is read: {@code $PREFIX/var/lib/proot-distro/containers/&lt;name&gt;/rootfs}.
 * A container installed by an older version moves itself to that layout the first time it is
 * logged into, and until then it is simply not listed.
 *
 * <p>Pure file reading and string building, so all of it is tested against fixture directories.
 */
public final class ProotDistro {

    /** Where 5.x keeps its containers, relative to the prefix. */
    private static final String CONTAINERS_PATH = "/var/lib/proot-distro/containers";

    /**
     * What is carried into a container. No host environment crosses a {@code proot-distro login}
     * — not even {@code DISPLAY} — so everything an app needs is passed with {@code -e}. The
     * display is taken from the shell the script runs in, which
     * {@link X11LinuxAppRunner#script} has just exported; the rest mirrors
     * {@link X11LinuxAppRunner#TOUCH_ENV}, which does the same job for a prefix app.
     *
     * <p>The GPU profile is deliberately not forwarded: it names drivers installed in the prefix,
     * and whether the container has its own is a separate question the spec parks.
     */
    static final List<String> FORWARDED_ENV = Collections.unmodifiableList(Arrays.asList(
        "DISPLAY=${DISPLAY:-:0}", "MOZ_USE_XINPUT2=1"));

    /**
     * What a container name or a user name may contain. Both go on a command line unquoted, and
     * both come off the filesystem, so anything else is not a container the launcher will run.
     */
    private static final String SAFE_NAME = "[A-Za-z0-9._+-]+";

    private ProotDistro() {}

    /**
     * Where an app's files live: either the prefix itself or one distro container. Everything that
     * differs between the two — which directories hold desktop files, where icons are looked up,
     * whether a path out of a desktop file is container-absolute — is answered here, so the
     * catalogue stays a desktop-file reader.
     */
    public static final class Container {

        /** Which kind of tree an app's files sit in; everything that differs follows from it. */
        public enum Kind {
            /** The launcher's own prefix, and the only one there was before containers. */
            PREFIX,
            /** One {@code proot-distro} container: a whole distro filesystem. */
            DISTRO,
            /** The nix edition's profile: a tree of store links, resolved by {@link NixProfile}. */
            NIX
        }

        /** The prefix: the launcher's own Linux, and the only one there was before containers. */
        @NonNull public static final Container PREFIX =
            new Container("", new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH), "", "");

        /** The container's name, as {@code proot-distro} knows it; empty for the prefix and nix. */
        @NonNull public final String name;
        /**
         * The prefix directory, the container's {@code rootfs}, or — for nix — the prefix again,
         * since that is what every {@code /nix/…} path is rewritten against.
         */
        @NonNull public final File root;
        /** The user a login runs as; empty for the prefix and nix. */
        @NonNull public final String user;
        /** That user's home inside the container, absolute there; empty for the prefix and nix. */
        @NonNull public final String home;
        /** Which of the three this is. */
        @NonNull public final Kind kind;
        /** For {@link Kind#NIX}, the resolved profile directory; null otherwise. */
        @Nullable public final File profile;

        Container(@NonNull String name, @NonNull File root, @NonNull String user,
                  @NonNull String home) {
            this(name.isEmpty() ? Kind.PREFIX : Kind.DISTRO, name, root, user, home, null);
        }

        private Container(@NonNull Kind kind, @NonNull String name, @NonNull File root,
                          @NonNull String user, @NonNull String home, @Nullable File profile) {
            this.kind = kind;
            this.name = name;
            this.root = root;
            this.user = user;
            this.home = home;
            this.profile = profile;
        }

        /**
         * The nix edition's profile as a container. Its name is empty, so a nix app's id is the
         * bare desktop-file name a prefix app's has always been: on that edition the profile is
         * the only place apps come from, and there is nothing for a qualifier to tell apart.
         */
        @NonNull
        public static Container nix(@NonNull File prefixDir, @NonNull File profileDir) {
            return new Container(Kind.NIX, "", prefixDir, "", "", profileDir);
        }

        public boolean isPrefix() {
            return kind == Kind.PREFIX;
        }

        /** Where this one keeps its desktop files, most general first. */
        @NonNull
        public List<File> applicationDirs() {
            List<File> dirs = new ArrayList<>(3);
            if (kind == Kind.NIX) {
                if (profile != null) dirs.add(under(profile, "share/applications"));
                // Anything hand-written, or put there by a home-manager activation rather than by
                // a package: an ordinary directory in the user's home, no store links involved.
                File files = root.getParentFile();
                if (files != null) {
                    dirs.add(new File(files, "home/.local/share/applications"));
                }
                return dirs;
            }
            if (isPrefix()) {
                dirs.add(new File(root, "share/applications"));
                dirs.add(new File(root, "local/share/applications"));
                return dirs;
            }
            dirs.add(new File(root, "usr/share/applications"));
            dirs.add(new File(root, "usr/local/share/applications"));
            if (!home.isEmpty()) dirs.add(inside(home + "/.local/share/applications"));
            return dirs;
        }

        /**
         * Where this one keeps its whole-desktop session files, most general first. A desktop
         * environment does not put its entry beside the applications: it goes in {@code xsessions},
         * the directory every display manager reads and nothing else does. Same two levels as
         * {@link #applicationDirs()} — the system's and {@code local}'s — because
         * {@code XDG_DATA_DIRS} is {@code /usr/local/share:/usr/share} and both are in SDDM's and
         * GDM's own lists.
         *
         * <p>Wayland sessions are deliberately not here: none of them can run on the launcher's
         * X11 display, so cataloguing them would only put tiles in the drawer that cannot work.
         */
        @NonNull
        public List<File> sessionDirs() {
            List<File> dirs = new ArrayList<>(2);
            if (kind == Kind.NIX) {
                if (profile != null) dirs.add(under(profile, "share/xsessions"));
                return dirs;
            }
            if (isPrefix()) {
                dirs.add(new File(root, "share/xsessions"));
                dirs.add(new File(root, "local/share/xsessions"));
                return dirs;
            }
            dirs.add(new File(root, "usr/share/xsessions"));
            dirs.add(new File(root, "usr/local/share/xsessions"));
            return dirs;
        }

        /**
         * The directory {@code share/icons} and {@code share/pixmaps} sit under. A container's
         * are inside its {@code /usr}; the prefix is already that directory; nix's are inside the
         * profile, reached through {@link #under} because any component of the way there can be a
         * store link.
         */
        @NonNull
        public File iconPrefix() {
            if (kind == Kind.NIX) return profile != null ? profile : root;
            return isPrefix() ? root : new File(root, "usr");
        }

        /**
         * A path a desktop file states absolutely, resolved. Inside a container such a path is
         * absolute in the container's world, so it is re-rooted; inside nix it is a path in the
         * proot's world, which {@link NixProfile#rewrite} maps and {@link NixProfile#resolve}
         * follows; the prefix's desktop files already name real paths.
         */
        @NonNull
        public File inside(@NonNull String absolutePath) {
            if (kind == Kind.NIX) {
                return NixProfile.resolve(root, NixProfile.rewrite(root, absolutePath));
            }
            if (isPrefix()) return new File(absolutePath);
            String relative = absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
            return new File(root, relative);
        }

        /**
         * {@code relative} under {@code base}, as a reader of this container's files must walk it.
         * Plain path joining everywhere but nix, where every component is followed and rewritten.
         */
        @NonNull
        public File under(@NonNull File base, @NonNull String relative) {
            return kind == Kind.NIX ? NixProfile.under(root, base, relative)
                : new File(base, relative);
        }

        /**
         * The file to actually open for {@code file}. A nix profile's entries are themselves links
         * into the store, dangling from Android's side until they are rewritten, so a desktop file
         * found by listing a directory is not yet a file that can be read.
         */
        @NonNull
        public File readable(@NonNull File file) {
            return kind == Kind.NIX ? NixProfile.resolve(root, file) : file;
        }
    }

    /**
     * The nix profile of the running edition as a container, or null on an edition that has none.
     * Read afresh every time: the profile points somewhere else after every switch.
     */
    @Nullable
    public static Container nixProfile() {
        File prefixDir = NixProfile.prefixDir();
        File profile = NixProfile.profile(prefixDir);
        return profile == null ? null : Container.nix(prefixDir, profile);
    }

    /** The containers directory for the running prefix. */
    @NonNull
    public static File containersDir() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + CONTAINERS_PATH);
    }

    /**
     * Every container the running prefix can actually log into. Nothing is listed where
     * {@code proot-distro} is not installed, so on a device without it the whole feature is
     * silent rather than offering tiles that cannot start.
     */
    @NonNull
    public static List<Container> installed() {
        File tool = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "proot-distro");
        return tool.isFile() ? containers(containersDir()) : Collections.<Container>emptyList();
    }

    /**
     * Every installed container under {@code containersDir}, by name. "Installed" is what
     * {@code proot-distro} itself means by it: the container's {@code rootfs} directory exists.
     * A half-downloaded one, or a name the launcher would not put on a command line, is skipped.
     */
    @NonNull
    public static List<Container> containers(@NonNull File containersDir) {
        File[] entries = containersDir.listFiles();
        if (entries == null) return Collections.emptyList();
        List<Container> containers = new ArrayList<>();
        for (File entry : entries) {
            String name = entry.getName();
            if (!name.matches(SAFE_NAME)) continue;
            File rootfs = new File(entry, "rootfs");
            if (!rootfs.isDirectory()) continue;
            User user = resolveUser(entry, rootfs);
            containers.add(new Container(name, rootfs, user.name, user.home));
        }
        Collections.sort(containers, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return containers;
    }

    /** The user a login into {@code rootfs} runs as, and that user's home. */
    public static final class User {
        @NonNull public final String name;
        @NonNull public final String home;

        User(@NonNull String name, @NonNull String home) {
            this.name = name;
            this.home = home;
        }
    }

    /** What a container with no ordinary user of its own is logged into as. */
    @NonNull
    private static final User ROOT = new User("root", "/root");

    /**
     * Who to log in as: the container's first ordinary user. A distro image starts as root only,
     * and a setup that adds a user adds it at the first ordinary uid; running as that user is what
     * a desktop app expects, and several refuse to start as root at all.
     */
    @NonNull
    public static User loginUser(@NonNull File rootfs) {
        String passwd = readFile(new File(rootfs, "etc/passwd"));
        return passwd == null ? ROOT : parsePasswd(passwd);
    }

    /**
     * Who to log in as, the way {@code containers()} actually decides it: the account a setup
     * script recorded beside {@code rootfs} (see {@link #recordedUser}), when that record is
     * present and still checks out against this container's own {@code /etc/passwd}; otherwise
     * today's discovery ({@link #loginUser}), unchanged.
     */
    @NonNull
    private static User resolveUser(@NonNull File entry, @NonNull File rootfs) {
        String passwd = readFile(new File(rootfs, "etc/passwd"));
        if (passwd == null) return ROOT;
        String record = readFile(new File(entry, GuiAppsSetup.RECORD_FILE_NAME));
        User recorded = recordedUser(record, passwd);
        return recorded != null ? recorded : parsePasswd(passwd);
    }

    /** The contents of {@code file}, or null if it cannot be read. */
    @Nullable
    private static String readFile(@NonNull File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
        } catch (IOException e) {
            return null;
        }
        return content.toString();
    }

    /**
     * Shells a distro hands an account that is not meant to be logged into at all. A login that
     * runs one of these prints its refusal and exits straight away, which is what a container app
     * looked like when it never opened.
     */
    @NonNull
    private static final List<String> NOT_A_LOGIN_SHELL =
        Collections.unmodifiableList(Arrays.asList("nologin", "false", "sync", "true"));

    /** Whether {@code shell} is one of {@link #NOT_A_LOGIN_SHELL}, wherever the distro keeps it. */
    private static boolean isLoginShell(@NonNull String shell) {
        int slash = shell.lastIndexOf('/');
        return !NOT_A_LOGIN_SHELL.contains(slash < 0 ? shell : shell.substring(slash + 1));
    }

    /**
     * The lowest-numbered ordinary user in a {@code /etc/passwd} that can actually be logged in
     * as, or root when it has none. Ordinary means a uid of 1000 or more, below the {@code nobody}
     * uid that every distro parks at 65534; system accounts sit below 1000 and {@code nobody} is
     * not a login.
     *
     * <p>The login shell has to be read too, not just the uid: {@code proot-distro} writes an
     * {@code aid_u0_aNNN} account into every container for the Android uid the launcher runs as,
     * so that files show an owner, and that uid is in the ten-thousands — below the real user's on
     * a phone, and above it nowhere. Its shell is {@code nologin}, so logging in as it does
     * nothing but print "This account is currently not available." and exit; picking it is why a
     * container app opened no window at all.
     */
    @NonNull
    public static User parsePasswd(@NonNull String passwd) {
        User best = null;
        int bestUid = Integer.MAX_VALUE;
        for (String line : passwd.split("\n")) {
            String[] fields = line.split(":", -1);
            if (fields.length < 6) continue;
            String name = fields[0].trim();
            if (name.isEmpty() || !name.matches(SAFE_NAME)) continue;
            int uid;
            try {
                uid = Integer.parseInt(fields[2].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (uid < 1000 || uid >= 65534 || uid >= bestUid) continue;
            if (fields.length > 6 && !isLoginShell(fields[6].trim())) continue;
            bestUid = uid;
            String home = fields[5].trim();
            best = new User(name, home.isEmpty() ? "/home/" + name : home);
        }
        return best == null ? ROOT : best;
    }

    /**
     * What {@link GuiAppsSetup}'s setup script may have recorded as the account it logged in as:
     * lower-case only, by construction of the shell that wrote it.
     */
    private static final String RECORDED_NAME = "[a-z0-9_]+";

    /**
     * The account a setup script recorded for this container, if the record still checks out —
     * a bare lower-case name, not {@code root}, that {@code passwd} actually has with a login
     * shell — or null otherwise, which is the caller's cue to fall back to
     * {@link #parsePasswd} discovery.
     *
     * <p>An absent or unreadable record, one with anything on the line besides the name (a
     * second word, trailing garbage, upper case), one naming {@code root}, and one naming an
     * account {@code passwd} does not have or refuses to log into, are all the same "no record"
     * case: they only ever came from a corrupted or stale write, never from the script, which
     * writes exactly one lower-case name after a login that already succeeded.
     */
    @Nullable
    public static User recordedUser(@Nullable String recordFileContents, @NonNull String passwd) {
        if (recordFileContents == null) return null;
        String name = recordFileContents.trim();
        if (name.isEmpty() || name.equals("root") || !name.matches(RECORDED_NAME)) return null;
        for (String line : passwd.split("\n")) {
            String[] fields = line.split(":", -1);
            if (fields.length < 6 || !fields[0].trim().equals(name)) continue;
            if (fields.length > 6 && !isLoginShell(fields[6].trim())) return null;
            String home = fields[5].trim();
            return new User(name, home.isEmpty() ? "/home/" + name : home);
        }
        return null;
    }

    /**
     * The host shell line that runs {@code command} inside {@code container}.
     *
     * <p>{@code --shared-x11} is what binds the display socket into the container; it is required,
     * whatever {@code proot-distro --help} says about it being on by default. The command is
     * handed to the container's own {@code /bin/sh} as a single quoted word, so a desktop file's
     * own quoting survives the trip through the host shell untouched.
     */
    @NonNull
    public static String loginCommand(@NonNull Container container, @NonNull String command) {
        if (container.isPrefix()) return command;
        // Nix has no proot-distro and no second filesystem: $PREFIX/bin/login is the one way into
        // the environment where a store path and the profile's PATH mean anything, and it passes
        // the environment through, so DISPLAY and the rest are already there.
        if (container.kind == Container.Kind.NIX) {
            return new File(container.root, "bin/login").getPath() + " sh -c "
                + singleQuote(command);
        }
        StringBuilder line = new StringBuilder("proot-distro login ");
        line.append(container.name);
        line.append(" -u ").append(container.user.isEmpty() ? "root" : container.user);
        line.append(" --shared-x11");
        for (String variable : FORWARDED_ENV) line.append(" -e ").append(variable);
        line.append(" -- /bin/sh -c ").append(singleQuote(command));
        return line.toString();
    }

    /**
     * {@code value} as one shell word. A single quote cannot appear inside single quotes, so it is
     * spelled the only way a POSIX shell accepts: close, escape, reopen.
     */
    @NonNull
    public static String singleQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** The container of the given name among {@code containers}, or null. */
    @Nullable
    public static Container byName(@NonNull List<Container> containers, @NonNull String name) {
        for (Container container : containers) {
            if (container.name.equals(name)) return container;
        }
        return null;
    }
}
