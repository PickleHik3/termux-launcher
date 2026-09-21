package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The nix tree as Android sees it: a profile that is a chain of links, half of them relative and
 * half of them absolute {@code /nix/…} paths that nothing outside the proot can follow.
 *
 * <p>Every fixture here has the shape read off the phone on 2026-09-21: {@code profile ->
 * profile-2-link -> /nix/store/<hash>-user-environment}, whose own {@code bin} and {@code share}
 * are absolute links into a merged path, whose entries are absolute links into the packages.
 */
public class NixProfileTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File prefix;

    /** The prefix of a fake nix edition, with the store under it. */
    private File prefix() throws IOException {
        if (prefix == null) {
            prefix = temp.newFolder("usr");
            assertTrue(new File(prefix, "nix/store").mkdirs());
        }
        return prefix;
    }

    /** A store entry, as {@code $PREFIX/nix/store/<name>}. */
    private File store(String name) throws IOException {
        File dir = new File(prefix(), "nix/store/" + name);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        return dir;
    }

    private static void link(File at, String target) throws IOException {
        File parent = at.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        Files.createSymbolicLink(at.toPath(), Paths.get(target));
    }

    private static File write(File file, String content) throws IOException {
        File parent = file.getParentFile();
        assertNotNull(parent);
        assertTrue(parent.isDirectory() || parent.mkdirs());
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /**
     * The profile as the phone has it: a relative hop, then an absolute one into the store, then
     * a user environment whose {@code bin} and {@code share} are themselves absolute links.
     */
    private File nixTree() throws IOException {
        File env = store("c7ly-user-environment");
        File path = store("sl25-nix-on-droid-path");
        assertTrue(new File(path, "bin").mkdirs());
        assertTrue(new File(path, "share/applications").mkdirs());
        link(new File(env, "bin"), "/nix/store/sl25-nix-on-droid-path/bin");
        link(new File(env, "share"), "/nix/store/sl25-nix-on-droid-path/share");
        link(new File(env, "manifest.nix"), "/nix/store/dw84-manifest.nix");

        File profiles = new File(prefix(), "nix/var/nix/profiles/per-user/nix-on-droid");
        assertTrue(profiles.mkdirs());
        link(new File(profiles, "profile-2-link"), "/nix/store/c7ly-user-environment");
        link(new File(profiles, "profile"), "profile-2-link");
        return env;
    }

    @Test public void anEditionWithNoProfileIsNotNix() throws IOException {
        assertFalse(NixProfile.isNix(prefix()));
        assertNull(NixProfile.profile(prefix()));
    }

    @Test public void theBindsAreTheOnesTheLoginScriptPasses() throws IOException {
        File p = prefix();
        assertEquals(new File(p, "nix/store/x"), NixProfile.rewrite(p, "/nix/store/x"));
        assertEquals(new File(p, "nix"), NixProfile.rewrite(p, "/nix"));
        assertEquals(new File(p, "bin/xterm"), NixProfile.rewrite(p, "/bin/xterm"));
        assertEquals(new File(p, "etc/passwd"), NixProfile.rewrite(p, "/etc/passwd"));
        assertEquals(new File(p, "usr/lib/login-inner"), NixProfile.rewrite(p, "/usr/lib/login-inner"));
        assertEquals(new File(p, "tmp/x"), NixProfile.rewrite(p, "/tmp/x"));
        // Longest first, so /dev/shm is not read as an unbound /dev.
        assertEquals(new File(p, "dev/shm/x"), NixProfile.rewrite(p, "/dev/shm/x"));
        assertEquals(new File("/dev/null"), NixProfile.rewrite(p, "/dev/null"));
        // The Android root is visible unchanged, and again at /android.
        assertEquals(new File("/data/data/x/files"), NixProfile.rewrite(p, "/data/data/x/files"));
        assertEquals(new File("/data/data/x"), NixProfile.rewrite(p, "/android/data/data/x"));
        // A name that merely begins with a bound one is not that bind.
        assertEquals(new File("/nixos/x"), NixProfile.rewrite(p, "/nixos/x"));
    }

    @Test public void theProfileChainIsFollowedThroughRelativeAndAbsoluteLinks() throws IOException {
        File env = nixTree();
        assertTrue(NixProfile.isNix(prefix()));
        assertEquals(env, NixProfile.profile(prefix()));
    }

    /**
     * A generation that has been garbage-collected, or a switch caught half way: the chain is
     * whole and the last hop lands on nothing. That is no profile, not an empty one.
     */
    @Test public void aProfilePointingAtAStorePathThatIsGoneIsNoProfile() throws IOException {
        File profiles = new File(prefix(), "nix/var/nix/profiles/per-user/nix-on-droid");
        assertTrue(profiles.mkdirs());
        link(new File(profiles, "profile-2-link"), "/nix/store/c7ly-user-environment");
        link(new File(profiles, "profile"), "profile-2-link");

        assertTrue("the link is there, which is all isNix reads", NixProfile.isNix(prefix()));
        assertNull(NixProfile.profile(prefix()));
    }

    @Test public void everyComponentOfAPathIsResolved() throws IOException {
        File env = nixTree();
        File path = store("sl25-nix-on-droid-path");
        File xterm = store("zxyn-xterm-410");
        write(new File(xterm, "share/applications/xterm.desktop"), "[Desktop Entry]\n");
        link(new File(path, "share/applications/xterm.desktop"),
            "/nix/store/zxyn-xterm-410/share/applications/xterm.desktop");

        // share is an absolute link inside the user environment; without following it component
        // by component this directory does not exist at all from Android's side.
        File applications = NixProfile.under(prefix(), env, "share/applications");
        assertEquals(new File(path, "share/applications"), applications);
        assertTrue(applications.isDirectory());

        File desktop = NixProfile.resolve(prefix(), new File(applications, "xterm.desktop"));
        assertEquals(new File(xterm, "share/applications/xterm.desktop"), desktop);
        assertTrue(desktop.isFile());
    }

    @Test public void aBinaryIsFoundThroughTheProfilesBin() throws IOException {
        File env = nixTree();
        File path = store("sl25-nix-on-droid-path");
        File xterm = store("zxyn-xterm-410");
        assertTrue(write(new File(xterm, "bin/xterm"), "#!/bin/sh\n").setExecutable(true));
        link(new File(path, "bin/xterm"), "/nix/store/zxyn-xterm-410/bin/xterm");

        File resolved = NixProfile.under(prefix(), env, "bin/xterm");
        assertEquals(new File(xterm, "bin/xterm"), resolved);
        assertTrue(resolved.canExecute());
    }

    @Test public void aLinkLoopEndsRatherThanHangs() throws IOException {
        File dir = temp.newFolder("loop");
        link(new File(dir, "a"), "b");
        link(new File(dir, "b"), "a");
        // Whatever it lands on, it lands: the point is that it returns at all, and to something
        // that does not exist.
        assertFalse(NixProfile.resolve(prefix(), new File(dir, "a")).exists());
    }

    /**
     * The keyboard data is not in the profile — {@code xkeyboard-config} is data, so a merged
     * profile keeps a {@code share/X11} with no {@code xkb} under it — and is found in the store
     * instead, newest generation first.
     */
    @Test public void theKeyboardDataIsFoundInTheStoreWhenTheProfileHasNone() throws IOException {
        File env = nixTree();
        File path = store("sl25-nix-on-droid-path");
        assertTrue(new File(path, "share/X11").mkdirs());
        assertFalse(NixProfile.under(prefix(), env, "share/X11/xkb").isDirectory());

        File old = store("aaaa-xkeyboard-config-2.44");
        assertTrue(new File(old, "share/X11/xkb/rules").mkdirs());
        assertTrue(old.setLastModified(1_000_000L));
        File current = store("0amyq-xkeyboard-config-2.47");
        assertTrue(new File(current, "share/X11/xkb/rules").mkdirs());
        assertTrue(current.setLastModified(2_000_000L));
        // A derivation is not a build result, whatever its name says.
        assertTrue(new File(prefix(), "nix/store/zzzz-xkeyboard-config-2.48.drv/share/X11/xkb")
            .mkdirs());

        assertEquals(new File(current, "share/X11/xkb"), NixProfile.keyboardData(prefix()));
    }

    @Test public void theProfilesOwnKeyboardDataWinsWhenItHasSome() throws IOException {
        File env = nixTree();
        File path = store("sl25-nix-on-droid-path");
        assertTrue(new File(path, "share/X11/xkb").mkdirs());
        File other = store("aaaa-xkeyboard-config-2.44");
        assertTrue(new File(other, "share/X11/xkb").mkdirs());

        assertEquals(new File(path, "share/X11/xkb"), NixProfile.keyboardData(prefix()));
    }

    @Test public void noKeyboardDataAnywhereIsNull() throws IOException {
        nixTree();
        assertNull(NixProfile.keyboardData(prefix()));
    }

    /**
     * On nix the two shells are different ones. What the launcher execs has to be Android's,
     * because the prefix's own {@code sh} is a store link nothing outside the proot can follow;
     * what the user runs from inside is the prefix's, which resolves there and is really bash.
     */
    @Test public void nixSplitsTheHostShellFromThePrefixesOwn() throws IOException {
        File p = prefix();
        // Not nix and no bash: still bash, both ways. A half-unpacked Termux bootstrap is not an
        // sh prefix, and answering something else would only hide it.
        assertEquals(new File(p, "bin/bash"), NixProfile.hostShell(p));
        assertEquals(new File(p, "bin/bash"), NixProfile.prefixShell(p));

        nixTree();
        assertEquals(new File("/system/bin/sh"), NixProfile.hostShell(p));
        assertEquals(new File(p, "bin/sh"), NixProfile.prefixShell(p));

        write(new File(p, "bin/bash"), "#!/bin/sh\n");
        assertEquals(new File(p, "bin/bash"), NixProfile.hostShell(p));
        assertEquals(new File(p, "bin/bash"), NixProfile.prefixShell(p));
    }
}
