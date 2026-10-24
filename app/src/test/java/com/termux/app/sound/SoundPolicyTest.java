package com.termux.app.sound;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The launcher's sound server, decided without running anything: stepping aside for a user's own
 * setup (decisions 7, 8), the restart cap (3), the files it writes and when it may rewrite them
 * (2, 4, 5, 9), and the proot artwork mapping (11).
 */
public class SoundPolicyTest {

    // ---- Decision 7: the user's own files ---------------------------------------------------------

    @Test public void whatPulseaudioWritesItselfIsNotTheUsersSetup() {
        String[] entries = {"cookie", "0f1e2d3c-runtime", "0f1e2d3c-default-sink",
            "0f1e2d3c-default-source", "0f1e2d3c-card-database.tdb", "0f1e2d3c-device-volumes.tdb",
            "0f1e2d3c-stream-volumes.tdb"};

        assertTrue(StepAside.userFiles(entries).isEmpty());
        assertTrue(StepAside.userFiles(null).isEmpty());
    }

    @Test public void anyOtherFileThereIsTheUsersSetup() {
        String[] entries = {"cookie", "default.pa", "client.conf", "0f-runtime", "daemon.conf.d"};

        assertEquals(Arrays.asList("client.conf", "daemon.conf.d", "default.pa"),
            StepAside.userFiles(entries));
    }

    // ---- Decisions 7 and 8: whose a running server is ---------------------------------------------

    private static final String OURS = "/p/var/lib/termux-launcher/sound/startup.pa";

    @Test public void anAutospawnedOrBareStartedServerIsStock() {
        assertSame(StepAside.Server.STOCK, StepAside.classify(
            Arrays.asList("pulseaudio", "--start", "--log-target=syslog"), OURS));
        assertSame(StepAside.Server.STOCK, StepAside.classify(
            Arrays.asList("/data/data/x/files/usr/bin/pulseaudio", "--start"), OURS));
        assertSame(StepAside.Server.STOCK, StepAside.classify(
            Arrays.asList("pulseaudio", "-D"), OURS));
    }

    @Test public void aServerStartedWithOptionsOfSomebodysChoosingIsTheUsers() {
        assertSame(StepAside.Server.USER, StepAside.classify(Arrays.asList("pulseaudio", "--start",
            "--load=module-native-protocol-tcp auth-ip-acl=127.0.0.1 auth-anonymous=1",
            "--exit-idle-time=-1"), OURS));
        assertSame(StepAside.Server.USER, StepAside.classify(
            Arrays.asList("pulseaudio", "--start", "--exit-idle-time=-1"), OURS));
    }

    @Test public void theLaunchersOwnServerIsKnownByItsStartupFile() {
        assertSame(StepAside.Server.OURS,
            StepAside.classify(SoundFiles.serverArgv("/p/bin/pulseaudio", OURS), OURS));
    }

    @Test public void anythingButPulseaudioIsNoServer() {
        assertNull(StepAside.classify(Arrays.asList("pactl", "subscribe"), OURS));
        assertNull(StepAside.classify(Arrays.asList("/bin/pulseaudio-wrapper", "--start"), OURS));
        assertNull(StepAside.classify(Collections.<String>emptyList(), OURS));
    }

    @Test public void aCommandLineIsSplitOnItsNuls() {
        byte[] raw = "pulseaudio\0--start\0--log-target=syslog\0".getBytes(StandardCharsets.UTF_8);

        assertEquals(Arrays.asList("pulseaudio", "--start", "--log-target=syslog"),
            StepAside.parseCmdline(raw, raw.length));
        byte[] unterminated = "a\0b".getBytes(StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("a", "b"), StepAside.parseCmdline(unterminated, 3));
        assertTrue(StepAside.parseCmdline(null, 0).isEmpty());
    }

    // ---- Decision 3: three exits within a minute --------------------------------------------------

    @Test public void theThirdExitWithinAMinuteGivesUp() {
        RestartCap cap = new RestartCap();

        assertTrue(cap.onExit(0));
        assertTrue(cap.onExit(10_000));
        assertFalse(cap.onExit(59_999));
    }

    @Test public void exitsSpreadOverMoreThanAMinuteKeepRestarting() {
        RestartCap cap = new RestartCap();

        assertTrue(cap.onExit(0));
        assertTrue(cap.onExit(30_000));
        assertTrue(cap.onExit(60_000));
        assertTrue(cap.onExit(95_000));
        assertFalse(cap.onExit(100_000));
    }

    @Test public void theNextNeedStartsFromACleanSlate() {
        RestartCap cap = new RestartCap();
        cap.onExit(0);
        cap.onExit(1);
        assertFalse(cap.onExit(2));

        cap.reset();

        assertTrue(cap.onExit(3));
        assertTrue(cap.onExit(4));
    }

    // ---- Decisions 2 and 5: the startup file --------------------------------------------------------

    @Test public void theStartupFileTriesAaudioAndFailsTheStartWhenItWillNotLoad() {
        String file = SoundFiles.startupFile(SoundFiles.Sink.AAUDIO, "/t/native", "/s/cookie", 4713);

        assertTrue(file.startsWith(SoundPaths.MARKER));
        int fail = file.indexOf(".fail\n");
        int sink = file.indexOf("load-module module-aaudio-sink\n");
        int nofail = file.indexOf(".nofail\n", fail);
        assertTrue(fail >= 0 && fail < sink && sink < nofail);
        assertFalse(file.contains("module-sles-sink"));
        assertTrue(SoundFiles.startupFile(SoundFiles.Sink.SLES, "/t/native", "/s/cookie", 4713)
            .contains("load-module module-sles-sink\n"));
    }

    @Test public void loopbackNeedsTheCookieAndNeverAnAddressList() {
        String file = SoundFiles.startupFile(SoundFiles.Sink.AAUDIO, "/t/native", "/s/cookie", 4713);

        assertTrue(file.contains("load-module module-native-protocol-tcp listen=127.0.0.1 port=4713"
            + " auth-cookie=/s/cookie\n"));
        assertTrue(file.contains("load-module module-native-protocol-unix socket=/t/native"
            + " auth-cookie=/s/cookie\n"));
        assertFalse(file.contains("auth-anonymous"));
        // An IP list would authorise every app on the phone: they all share loopback.
        assertFalse(file.contains("auth-ip-acl"));
        // A busy port must not stop the server: the listener comes after .nofail.
        assertTrue(file.lastIndexOf(".nofail\n") < file.indexOf("module-native-protocol-tcp"));
    }

    @Test public void eitherWayOfSayingTheSinkFailedIsRead() {
        assertTrue(SoundFiles.reportsSinkFailure("E: [pulseaudio] module.c: Failed to load module"
            + " \"module-aaudio-sink\" (argument: \"\"): initialization failed.",
            SoundFiles.Sink.AAUDIO));
        assertTrue(SoundFiles.reportsSinkFailure(
            "E: [pulseaudio] module.c: Failed to open module \"module-aaudio-sink\".",
            SoundFiles.Sink.AAUDIO));
        assertFalse(SoundFiles.reportsSinkFailure("E: [pulseaudio] module.c: Failed to load module"
            + " \"module-native-protocol-tcp\"", SoundFiles.Sink.AAUDIO));
        assertFalse(SoundFiles.reportsSinkFailure(null, SoundFiles.Sink.AAUDIO));
    }

    @Test public void theServerNeverDaemonizesNorIdlesOut() {
        List<String> argv = SoundFiles.serverArgv("/p/bin/pulseaudio", OURS);

        assertEquals("/p/bin/pulseaudio", argv.get(0));
        assertTrue(argv.contains("-n"));
        assertEquals(OURS, argv.get(argv.indexOf("-F") + 1));
        assertTrue(argv.contains("--daemonize=no"));
        assertTrue(argv.contains("--exit-idle-time=-1"));
        assertFalse(argv.contains("--start"));
    }

    // ---- The environments -------------------------------------------------------------------------

    @Test public void theServerReadsNoClientConfigurationAndHasItsOwnDirectories() {
        Map<String, String> termux = new HashMap<>();
        termux.put("HOME", "/h");
        termux.put("PULSE_SERVER", "tcp:elsewhere");
        Map<String, String> env = SoundFiles.processEnvironment(termux, "/p/lib/libtermux-exec.so", true);

        assertEquals("/h", env.get("HOME"));
        assertEquals("/p/lib/libtermux-exec.so", env.get("LD_PRELOAD"));
        assertEquals("/dev/null", env.get("PULSE_CLIENTCONFIG"));
        assertEquals(SoundPaths.RUNTIME_DIR, env.get("PULSE_RUNTIME_PATH"));
        assertEquals(SoundPaths.VOLUMES_DIR, env.get("PULSE_STATE_PATH"));
        assertFalse(env.containsKey("PULSE_SERVER"));
    }

    @Test public void theHelperTalksToTheLaunchersServerAndBus() {
        Map<String, String> env = SoundFiles.processEnvironment(new HashMap<>(), null, false);

        assertEquals(SoundPaths.PULSE_SERVER, env.get("PULSE_SERVER"));
        assertEquals(SoundPaths.BUS_ADDRESS, env.get("DBUS_SESSION_BUS_ADDRESS"));
        assertFalse(env.containsKey("LD_PRELOAD"));
        assertFalse(env.containsKey("PULSE_CLIENTCONFIG"));
    }

    @Test public void everyShellGetsTheServerAndTheBusOnlyWhenThereIsOne() {
        Map<String, String> withBus = SoundFiles.shellEnvironment(true);
        Map<String, String> without = SoundFiles.shellEnvironment(false);

        assertEquals("unix:" + SoundPaths.RUNTIME_DIR + "/native", withBus.get("PULSE_SERVER"));
        assertEquals(SoundPaths.BUS_ADDRESS, withBus.get("DBUS_SESSION_BUS_ADDRESS"));
        assertEquals("unix:path=/tmp/.X11-unix/termux-launcher-bus",
            withBus.get(LauncherSound.DISTRO_BUS_VARIABLE));
        assertEquals(Collections.singleton("PULSE_SERVER"), without.keySet());
    }

    @Test public void theBusSocketIsWhereADistroCanReachItAndIsNeverTakenForADisplay() {
        assertTrue(SoundPaths.BUS_SOCKET.startsWith(SoundPaths.TMP + "/.X11-unix/"));
        // The display code reads every X* entry of that directory as a display.
        assertFalse(SoundPaths.BUS_NAME.startsWith("X"));
    }

    // ---- Decisions 4 and 9: the drop-ins and the bus ----------------------------------------------

    @Test public void thePrefixDropInNamesTheSocketAndNeverAutospawns() {
        String dropIn = SoundFiles.termuxDropIn("unix:/t/native");

        assertTrue(SoundFiles.isOurs(dropIn));
        assertTrue(dropIn.contains("\ndefault-server = unix:/t/native\n"));
        assertTrue(dropIn.contains("\nautospawn = no\n"));
    }

    @Test public void aDistroDropInNamesLoopbackAndTheCookiesCopy() {
        String dropIn = SoundFiles.distroDropIn("tcp:127.0.0.1:4713", "/etc/pulse/termux-launcher.cookie");

        assertTrue(SoundFiles.isOurs(dropIn));
        assertTrue(dropIn.contains("\ndefault-server = tcp:127.0.0.1:4713\n"));
        assertTrue(dropIn.contains("\ncookie-file = /etc/pulse/termux-launcher.cookie\n"));
        assertEquals("tcp:127.0.0.1:4713", SoundPaths.PULSE_SERVER_TCP);
    }

    @Test public void aFileIsWrittenWhenMissingOrStaleAndNeverWhenItIsSomebodyElses() {
        String wanted = SoundFiles.termuxDropIn("unix:/t/native");

        assertSame(SoundFiles.Write.WRITE, SoundFiles.decide(false, null, wanted));
        assertSame(SoundFiles.Write.KEEP, SoundFiles.decide(true, wanted, wanted));
        assertSame(SoundFiles.Write.WRITE,
            SoundFiles.decide(true, SoundFiles.termuxDropIn("unix:/old/native"), wanted));
        assertSame(SoundFiles.Write.FOREIGN,
            SoundFiles.decide(true, "default-server = tcp:my.box\n", wanted));
        // Unreadable is not known to be ours.
        assertSame(SoundFiles.Write.FOREIGN, SoundFiles.decide(true, null, wanted));
    }

    @Test public void theBusLetsADistrosAppsInAndListensOnItsSocket() {
        String config = SoundFiles.busConfig("/t/.X11-unix/termux-launcher-bus");

        assertTrue(SoundFiles.isOurs(config));
        assertTrue(config.contains("<listen>unix:path=/t/.X11-unix/termux-launcher-bus</listen>"));
        assertTrue(config.contains("<allow_anonymous/>"));
        assertTrue(config.indexOf("<!DOCTYPE") < config.indexOf("<busconfig>"));
        List<String> argv = SoundFiles.busArgv("/p/bin/dbus-daemon", "/s/bus.conf");
        assertTrue(argv.contains("--nofork"));
        assertTrue(argv.contains("--config-file=/s/bus.conf"));
    }

    // ---- Decision 12: what pactl says -------------------------------------------------------------

    @Test public void theVolumeAndTheMuteAreReadOffPactl() {
        assertEquals(65, SoundFiles.volumePercent("Volume: front-left: 42598 /  65% / -11.23 dB,"
            + "   front-right: 42598 /  65% / -11.23 dB\n        balance 0.00\n"));
        assertEquals(-1, SoundFiles.volumePercent("Connection failure: Connection refused"));
        assertEquals(-1, SoundFiles.volumePercent(null));
        assertTrue(SoundFiles.isMuted("Mute: yes\n"));
        assertFalse(SoundFiles.isMuted("Mute: no\n"));
        assertFalse(SoundFiles.isMuted(null));
    }

    // ---- Decision 11: artwork ---------------------------------------------------------------------

    @Test public void onlyLocalArtIsTaken() {
        assertEquals("/home/u/Kind of Blue/cover.jpg",
            ProotArt.localPath("file:///home/u/Kind%20of%20Blue/cover.jpg"));
        assertEquals("/a+b.png", ProotArt.localPath("file:///a+b.png"));
        assertEquals("/x/y.png", ProotArt.localPath("file://localhost/x/y.png"));
        assertEquals("/plain/path.png", ProotArt.localPath("/plain/path.png"));
        assertNull(ProotArt.localPath("https://example.com/cover.jpg"));
        assertNull(ProotArt.localPath("file:///bad%zzescape"));
        assertNull(ProotArt.localPath("file:///a/../../etc/shadow"));
        assertNull(ProotArt.localPath(null));
    }

    @Test public void aPlayersDistroIsReadOffItsProotAncestor() {
        List<List<String>> ancestry = Arrays.asList(
            Collections.singletonList("kew"),
            Arrays.asList("/data/data/x/files/usr/bin/proot", "--rootfs=/data/data/x/files/usr/var/lib/"
                + "proot-distro/containers/debian/rootfs", "--change-id=1000:1000"),
            Collections.singletonList("-bash"));

        assertEquals("debian", ProotArt.distroOf(ancestry));
        assertNull(ProotArt.distroOf(Arrays.asList(Collections.singletonList("kew"),
            Collections.singletonList("-bash"))));
        assertEquals("ubuntu", ProotArt.distroOf(Collections.singletonList(Arrays.asList("proot",
            "-r", "/x/usr/var/lib/proot-distro/installed-rootfs/ubuntu"))));
    }

    @Test public void theParentPidSurvivesAProcessNameWithSpacesAndParentheses() {
        assertEquals(812, ProotArt.parentPid("4242 (kew (x) y) S 812 4242 4242 0 -1 4194560"));
        assertEquals(0, ProotArt.parentPid("garbage"));
        assertEquals(0, ProotArt.parentPid(null));
    }

    @Test public void distroArtIsLookedForInTheRootfsFirstThenWhereABindMountPutsIt() {
        File rootfs = new File("/c/rootfs");
        Set<String> present = new HashSet<>(Arrays.asList("/c/rootfs/home/u/a.jpg", "/sdcard/b.jpg"));
        ProotArt.Files files = file -> present.contains(file.getPath());

        assertEquals(new File("/c/rootfs/home/u/a.jpg"),
            ProotArt.resolve("/home/u/a.jpg", rootfs, files));
        assertEquals(new File("/sdcard/b.jpg"), ProotArt.resolve("/sdcard/b.jpg", rootfs, files));
        assertNull(ProotArt.resolve("/home/u/a.jpg", null, files));
        assertNull(ProotArt.resolve("/nowhere.jpg", rootfs, files));
    }

    @Test public void artIsSampledDownNoFurtherThanTheTargetNeeds() {
        assertEquals(1, MediaArtCache.sampleSize(384, 384, 384));
        assertEquals(1, MediaArtCache.sampleSize(700, 700, 384));
        assertEquals(2, MediaArtCache.sampleSize(800, 800, 384));
        assertEquals(8, MediaArtCache.sampleSize(3072, 4096, 384));
        assertEquals(1, MediaArtCache.sampleSize(4000, 100, 384));
    }
}
