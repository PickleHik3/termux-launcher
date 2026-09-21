package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The nix edition's package tree, read from Android's side of the fence.
 *
 * <p>That edition has no Termux prefix: {@code $PREFIX/bin} holds a handful of bootstrap
 * commands and nothing else, and everything a user installs lives in the nix store at
 * {@code $PREFIX/nix/store}. What is installed right now is the profile
 * {@code $PREFIX/nix/var/nix/profiles/per-user/nix-on-droid/profile}, and every session runs
 * inside a proot that binds {@code $PREFIX/nix} onto {@code /nix}. So a store path written down
 * anywhere inside that world — and they all are, since nix hardcodes them — reads
 * {@code /nix/store/…}, which is nothing at all from outside the proot.
 *
 * <p>Two things follow, and this class is both of them:
 *
 * <ul>
 *   <li><b>Rewriting.</b> {@link #rewrite} turns a path written inside the proot into the Android
 *       path that holds the same bytes, using the same bind list the login script passes to
 *       proot. Nothing else in the launcher knows that list.
 *   <li><b>Walking.</b> A profile is a tree of symlinks, and each of them can be relative
 *       ({@code profile -> profile-2-link}) or an absolute {@code /nix/…} that the Android side
 *       cannot follow at all. Java's own {@code getCanonicalPath} gives up on the first of those,
 *       so {@link #resolve} follows the chain a hop at a time, rewriting as it goes, and
 *       {@link #under} does that for every component of a path — because in a merged profile any
 *       component can be the one that turns into a store link.
 * </ul>
 *
 * <p>Pure file reading and string work, so all of it is tested against fixture trees. Nothing
 * here starts a process, and on an edition that is not nix every entry point answers "no" from
 * one {@code lstat}.
 */
public final class NixProfile {

    /** The profile symlink, relative to the prefix. Its existence is what "this is nix" means. */
    @VisibleForTesting
    static final String PROFILE_LINK = "nix/var/nix/profiles/per-user/nix-on-droid/profile";

    /** The nix store, relative to the prefix. */
    private static final String STORE = "nix/store";

    /**
     * How many links one path may go through. A profile is three or four hops deep; anything
     * past this is a loop somebody made by hand, and a loop must end in "no" rather than a hang.
     */
    private static final int MAX_HOPS = 16;

    /**
     * What the login script binds where, as {@code $PREFIX/bin/login} passes it to
     * {@code proot-static}: {@code -b $PREFIX/nix:/nix -b $PREFIX/bin:/bin! -b $PREFIX/etc:/etc!
     * -b $PREFIX/tmp:/tmp -b $PREFIX/usr:/usr -b $PREFIX/dev/shm:/dev/shm -b /:/android}. Listed
     * longest first so {@code /dev/shm} is not read as {@code /dev}.
     */
    private static final String[][] BINDS = {
        {"/dev/shm", "dev/shm"},
        {"/nix", "nix"},
        {"/bin", "bin"},
        {"/etc", "etc"},
        {"/tmp", "tmp"},
        {"/usr", "usr"},
    };

    /** A store entry of the package that carries the X keyboard data. */
    private static final Pattern XKEYBOARD_CONFIG =
        Pattern.compile(".+-xkeyboard-config-\\d.*");

    /** Where the X keyboard data sits inside whichever tree has it. */
    private static final String XKB_PATH = "share/X11/xkb";

    private NixProfile() {}

    /** The running edition's prefix. */
    @NonNull
    public static File prefixDir() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
    }

    /**
     * Whether {@code prefixDir} is a nix tree. Keyed on the profile link rather than on the
     * package name because the two callers that matter — the app catalogue and the prefix
     * installer — are static and have no {@code Context}, and because a package name that says
     * nix with no nix in it would be the worse answer of the two.
     */
    public static boolean isNix(@NonNull File prefixDir) {
        Path link = new File(prefixDir, PROFILE_LINK).toPath();
        return Files.isSymbolicLink(link) || Files.exists(link);
    }

    /**
     * The directory the profile link currently points at, links followed and rewritten, or null
     * when there is no profile there. The answer changes at every {@code nix-on-droid switch},
     * so nothing caches it.
     */
    @Nullable
    public static File profile(@NonNull File prefixDir) {
        File link = new File(prefixDir, PROFILE_LINK);
        if (!Files.isSymbolicLink(link.toPath()) && !link.exists()) return null;
        File resolved = resolve(prefixDir, link);
        return resolved.isDirectory() ? resolved : null;
    }

    /**
     * The Android path holding what {@code path} names inside the proot. Paths the proot does not
     * rewrite — {@code /data/…} above all, which is visible unchanged because the proot's root is
     * the real root — come back as they are.
     */
    @NonNull
    public static File rewrite(@NonNull File prefixDir, @NonNull String path) {
        for (String[] bind : BINDS) {
            if (path.equals(bind[0])) return new File(prefixDir, bind[1]);
            if (path.startsWith(bind[0] + "/")) {
                return new File(prefixDir, bind[1] + path.substring(bind[0].length()));
            }
        }
        // The real Android root is bound at /android, so a path through it is that path without it.
        if (path.equals("/android")) return new File("/");
        if (path.startsWith("/android/")) return new File(path.substring("/android".length()));
        return new File(path);
    }

    /**
     * {@code file} with its symlinks followed as far as they go — relative targets against the
     * link's own directory, absolute ones through {@link #rewrite} — and the last real path
     * returned. Best effort: a dangling link, an unreadable one or a loop ends the walk and gives
     * back what it had, which the caller checks for existence as it would any other file.
     */
    @NonNull
    public static File resolve(@NonNull File prefixDir, @NonNull File file) {
        File current = file;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Path path = current.toPath();
            if (!Files.isSymbolicLink(path)) return current;
            String target;
            try {
                target = Files.readSymbolicLink(path).toString();
            } catch (IOException | RuntimeException e) {
                return current;
            }
            if (target.startsWith("/")) {
                current = rewrite(prefixDir, target);
            } else {
                File parent = current.getParentFile();
                current = new File(parent == null ? new File("/") : parent, target);
            }
        }
        return current;
    }

    /**
     * {@code relative} under {@code base}, resolving at every component. A profile merges several
     * packages, so the component that turns into a store link is wherever the merge stopped:
     * {@code share} is a link in one profile and a real directory in the next.
     */
    @NonNull
    public static File under(@NonNull File prefixDir, @NonNull File base,
                             @NonNull String relative) {
        File current = resolve(prefixDir, base);
        for (String part : relative.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            current = resolve(prefixDir, new File(current, part));
        }
        return current;
    }

    /**
     * The X keyboard data the display server refuses to start without, wherever this phone's nix
     * has it, or null.
     *
     * <p>The profile is asked first, and normally does not have it: {@code xkeyboard-config}'s
     * output is data rather than a program, so a merged profile keeps its {@code share/X11}
     * without the {@code xkb} directory under it. The store entry itself always has it, so that
     * is the fallback — the newest one, since an old generation's copy can still be sitting there
     * waiting for a garbage collection.
     */
    @Nullable
    public static File keyboardData(@NonNull File prefixDir) {
        File profile = profile(prefixDir);
        if (profile != null) {
            File inProfile = under(prefixDir, profile, XKB_PATH);
            if (inProfile.isDirectory()) return inProfile;
        }
        File[] entries = new File(prefixDir, STORE).listFiles();
        if (entries == null) return null;
        File best = null;
        long bestTime = Long.MIN_VALUE;
        for (File entry : entries) {
            String name = entry.getName();
            if (name.endsWith(".drv") || !XKEYBOARD_CONFIG.matcher(name).matches()) continue;
            File xkb = new File(entry, XKB_PATH);
            if (!xkb.isDirectory()) continue;
            long time = entry.lastModified();
            if (best == null || time > bestTime) {
                best = xkb;
                bestTime = time;
            }
        }
        return best;
    }

    /**
     * The shell the launcher hands a script to. Termux's prefix has {@code bash} and always did;
     * a nix prefix has only the bootstrap's {@code sh}, and a script started with a shebang
     * naming a {@code bash} that is not there does not run at all.
     *
     * <p>The fallback is nix's alone rather than "whatever is there": on a Termux prefix a
     * missing {@code bash} means the bootstrap is half-unpacked, and answering {@code sh} to that
     * would only trade one broken script for another, quieter one.
     */
    @NonNull
    public static File scriptShell(@NonNull File prefixDir) {
        File bash = new File(prefixDir, "bin/bash");
        if (bash.isFile()) return bash;
        return isNix(prefixDir) ? new File(prefixDir, "bin/sh") : bash;
    }

    /** {@link #scriptShell(File)} for the running edition. */
    @NonNull
    public static String scriptShellPath() {
        return scriptShell(prefixDir()).getPath();
    }
}
