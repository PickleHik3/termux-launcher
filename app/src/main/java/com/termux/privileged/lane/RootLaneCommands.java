package com.termux.privileged.lane;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The command lines {@link RootLane} hands to {@code su}, built as pure strings so the quoting
 * and the order of the staging steps are tested. Every path and value is single-quoted; nothing
 * a catalog name or an argument holds can end a quote.
 */
final class RootLaneCommands {

    /** Printed last by a staging script that ran to the end; anything else is a failure. */
    static final String STAGED_MARKER = "tl-staged-ok";

    static final int SHELL_UID = 2000;

    private RootLaneCommands() {
    }

    /** {@code 'text'} with each embedded quote written as {@code '\''}. */
    @NonNull
    static String quote(@NonNull String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    /** The destination a staged copy of {@code name} with {@code digest} gets. */
    @NonNull
    static String stagedPath(@NonNull String name, @NonNull String digest) {
        return PrivilegedLaneService.BIN_DIR + "/" + name + "-"
            + digest.substring(0, PrivilegedLaneService.DIGEST_PREFIX_LENGTH);
    }

    /**
     * The one root command that stages {@code tempFile} as {@code name}: copies it in unless the
     * destination already has this digest, checks the digest of what landed, drops older copies,
     * then hands the tree to uid 2000 and makes every directory down to the bin directory 0700.
     * Runs as root, so these are the only uid 0 operations the lane has.
     *
     * @throws IllegalArgumentException for a name or digest that is not plain, before any command exists.
     */
    @NonNull
    static String stageScript(@NonNull String tempFile, @NonNull String name, @NonNull String sha256) {
        if (!PrivilegedLaneService.isPlainName(name)) {
            throw new IllegalArgumentException("refusing to stage a binary named '" + name + "'");
        }
        String digest = sha256.toLowerCase(Locale.ROOT);
        if (!PrivilegedCatalog.isHexDigest(digest)) {
            throw new IllegalArgumentException("stage() wants a hex sha256, not '" + sha256 + "'");
        }
        String root = quote(PrivilegedLaneService.ROOT_DIR);
        String bin = quote(PrivilegedLaneService.BIN_DIR);
        String dest = quote(stagedPath(name, digest));
        String fresh = quote(stagedPath(name, digest) + ".new");
        String temp = quote(tempFile);
        String older = quote(PrivilegedLaneService.BIN_DIR + "/" + name + "-") + "????????????";
        String d = quote(digest);
        StringBuilder s = new StringBuilder();
        s.append("umask 077");
        s.append(" && mkdir -p ").append(bin);
        s.append(" && if [ ").append(sum(dest)).append(" != ").append(d)
            .append(" ]; then cp ").append(temp).append(' ').append(fresh)
            .append(" && [ ").append(sum(fresh)).append(" = ").append(d)
            .append(" ] && mv -f ").append(fresh).append(' ').append(dest)
            .append(" || { rm -f ").append(fresh).append("; exit 1; }; fi");
        s.append(" && for f in ").append(older).append("; do [ \"$f\" = ").append(dest)
            .append(" ] || [ ! -f \"$f\" ] || rm -f \"$f\"; done");
        s.append(" && chown -R ").append(SHELL_UID).append(':').append(SHELL_UID).append(' ').append(root);
        s.append(" && chmod 0700 ").append(root).append(' ').append(bin);
        s.append(" && chmod 0755 ").append(dest);
        s.append(" && echo ").append(STAGED_MARKER);
        return s.toString();
    }

    @NonNull
    private static String sum(@NonNull String quotedPath) {
        return "\"$(sha256sum " + quotedPath + " 2>/dev/null | cut -d' ' -f1)\"";
    }

    /**
     * What {@code su} runs: the tool as uid 2000 in its own home, with the service's environment
     * ({@code extraEnv} entries override by key).
     */
    @NonNull
    static String spawnScript(@NonNull String path, String[] args, String[] extraEnv) {
        String name = PrivilegedLaneService.nameOf(path.substring(path.lastIndexOf('/') + 1));
        String home = PrivilegedLaneService.HOME_DIR + "/" + name;
        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", home);
        env.put("TERM", LaneRequest.DEFAULT_TERM);
        env.put("LANG", "C.UTF-8");
        env.put("PATH", "/system/bin:/system/xbin");
        env.put("TMPDIR", "/data/local/tmp");
        if (extraEnv != null) {
            for (String entry : extraEnv) {
                int eq = entry == null ? -1 : entry.indexOf('=');
                if (eq > 0) env.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        StringBuilder s = new StringBuilder();
        s.append("umask 077; mkdir -p ").append(quote(home)).append(" && cd ").append(quote(home))
            .append(" && exec env -i");
        for (Map.Entry<String, String> e : env.entrySet()) {
            s.append(' ').append(e.getKey()).append('=').append(quote(e.getValue()));
        }
        s.append(' ').append(quote(path));
        if (args != null) {
            for (String arg : args) s.append(' ').append(quote(arg == null ? "" : arg));
        }
        return s.toString();
    }

    /** {@code su 2000 -c <script>}. */
    @NonNull
    static String[] spawnArgv(@NonNull String path, String[] args, String[] extraEnv) {
        List<String> argv = new ArrayList<>();
        argv.add("su");
        argv.add(String.valueOf(SHELL_UID));
        argv.add("-c");
        argv.add(spawnScript(path, args, extraEnv));
        return argv.toArray(new String[0]);
    }

    /** Whether the staging command's output says it ran to the end. */
    static boolean stagedOk(String output) {
        return output != null && output.trim().endsWith(STAGED_MARKER);
    }
}
