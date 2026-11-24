package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MesaWaylandOverlayTest {

    private static final String LIB = "/p/opt/tl-mesa-wayland/lib";
    private static final String DPKG = "Package: zlib\nVersion: 1.3\n\n"
        + "Package: mesa\nStatus: install ok installed\nVersion: 26.3.1-1\nDepends: x\n\n"
        + "Package: mesa-vulkan-icd-freedreno\nVersion: 9.9\n";

    private static MesaWaylandOverlay.Answer resolve(boolean lib, String ver, String dpkg) {
        return MesaWaylandOverlay.resolve(lib, ver, dpkg, LIB);
    }

    @Test public void matchingVersionsUseTheOverlay() {
        MesaWaylandOverlay.Answer a = resolve(true, "26.3.1-1\n", DPKG);
        assertEquals(MesaWaylandOverlay.State.USE, a.state);
        assertEquals(LIB, a.libDir);
    }

    @Test public void crlfAndSpacesAreTolerated() {
        assertEquals(MesaWaylandOverlay.State.USE,
            resolve(true, " 26.3.1-1 \r\n", DPKG.replace("\n", "\r\n")).state);
    }

    @Test public void aMissingLibraryOrVersionFileIsMissing() {
        assertEquals(MesaWaylandOverlay.State.MISSING, resolve(false, "26.3.1-1", DPKG).state);
        assertEquals(MesaWaylandOverlay.State.MISSING, resolve(true, null, DPKG).state);
        assertEquals(MesaWaylandOverlay.State.MISSING, resolve(true, "\n", DPKG).state);
    }

    @Test public void differingVersionsMismatchAndCarryBoth() {
        MesaWaylandOverlay.Answer a = resolve(true, "26.2.0-1", DPKG);
        assertEquals(MesaWaylandOverlay.State.MISMATCH, a.state);
        assertEquals("26.2.0-1", a.overlayVersion);
        assertEquals("26.3.1-1", a.mesaVersion);
        assertNull(a.libDir);
    }

    @Test public void noInstalledMesaIsItsOwnAnswer() {
        assertEquals(MesaWaylandOverlay.State.NO_MESA, resolve(true, "26.3.1-1", null).state);
        assertEquals(MesaWaylandOverlay.State.NO_MESA, resolve(true, "26.3.1-1", "Package: zlib\nVersion: 1\n").state);
        assertEquals(MesaWaylandOverlay.State.NO_MESA, resolve(true, "26.3.1-1", "garbage").state);
    }

    @Test public void onlyThePackageNamedMesaCounts() {
        assertEquals("26.3.1-1", MesaWaylandOverlay.mesaVersion(DPKG));
        assertNull(MesaWaylandOverlay.mesaVersion("Package: mesa-demos\nVersion: 8\n"));
        assertNull(MesaWaylandOverlay.mesaVersion("Package: mesa\nStatus: x\n"));
    }

    @Test public void packageRevisionAndEpochAreIgnoredButReleaseMustMatch() {
        assertEquals(MesaWaylandOverlay.State.USE, resolve(true, "26.3.1-0", DPKG).state);
        assertEquals(MesaWaylandOverlay.State.USE,
            MesaWaylandOverlay.resolveVersion(true, "26.3.1-0", "1:26.3.1-2", LIB).state);
        assertEquals(MesaWaylandOverlay.State.MISMATCH,
            MesaWaylandOverlay.resolveVersion(true, "26.3.1", "26.3.2", LIB).state);
    }

    @Test public void pacmanDescYieldsTheVersionOnlyForPackageMesa() {
        assertEquals("26.3.1-0", MesaWaylandOverlay.pacmanMesaVersion("%NAME%\nmesa\n\n%VERSION%\n26.3.1-0\n\n%DESC%\nx\n"));
        assertNull(MesaWaylandOverlay.pacmanMesaVersion("%NAME%\nmesa-vulkan-icd-swrast\n\n%VERSION%\n26.3.1-0\n"));
        assertNull(MesaWaylandOverlay.pacmanMesaVersion("%NAME%\nmesa\n"));
        assertNull(MesaWaylandOverlay.pacmanMesaVersion(null));
    }

    @Test public void useAddsTheLibraryDirectoryFirstWithoutDroppingTheShellsValue() {
        MesaWaylandOverlay.Answer use = resolve(true, "26.3.1-1", DPKG);
        List<String> out = LinuxWindowsEnv.withMesaOverlay(Collections.singletonList("A=1"), use);
        assertEquals(Arrays.asList("A=1", "LD_LIBRARY_PATH=" + LIB + "${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"), out);
    }

    @Test public void useBeforeAnExistingLineKeepsItsValue() {
        MesaWaylandOverlay.Answer use = resolve(true, "26.3.1-1", DPKG);
        List<String> out = LinuxWindowsEnv.withMesaOverlay(Arrays.asList("LD_LIBRARY_PATH=/x:/y", "A=1"), use);
        assertEquals(Arrays.asList("LD_LIBRARY_PATH=" + LIB + ":/x:/y", "A=1"), out);
    }

    @Test public void otherAnswersLeaveTheEnvironmentAsIs() {
        List<String> env = Collections.singletonList("A=1");
        assertSame(env, LinuxWindowsEnv.withMesaOverlay(env, resolve(false, null, DPKG)));
        assertSame(env, LinuxWindowsEnv.withMesaOverlay(env, resolve(true, "1", DPKG)));
        assertSame(env, LinuxWindowsEnv.withMesaOverlay(env, resolve(true, "26.3.1-1", null)));
    }
}
