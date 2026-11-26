package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LinuxDisplayBackendTest {

    @Test
    public void storedWaylandIsWaylandOnlyInABuildWithTheHost() {
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.resolve("wayland", true));
        assertEquals(LinuxDisplayBackend.WAYLAND, LinuxDisplayBackend.resolve(" wayland ", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("wayland", false));
    }

    @Test
    public void everythingElseIsX11() {
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("x11", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve("Wayland2", true));
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, false));
    }

    @Test
    public void storedValuesMatchTheSharedDefault() {
        assertEquals(LinuxDisplayBackend.VALUE_X11, com.termux.shared.termux.settings.preferences
            .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_LINUX_DISPLAY_BACKEND);
    }

    private static final LinuxDisplayBackend X = LinuxDisplayBackend.X11;
    private static final LinuxDisplayBackend W = LinuxDisplayBackend.WAYLAND;
    private static final LinuxDisplayBackend.Choice DEF = LinuxDisplayBackend.Choice.DEFAULT;

    /** Wayland Display is in every build now; a fresh install and an update still open X11 Display. */
    @Test
    public void theSharedDefaultOpensX11EvenInABuildWithTheHost() {
        String shared = com.termux.shared.termux.settings.preferences
            .TermuxPreferenceConstants.TERMUX_APP.DEFAULT_LINUX_DISPLAY_BACKEND;
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(shared, true));
        // An update has no stored value: the getter falls back to the default.
        assertEquals(LinuxDisplayBackend.X11, LinuxDisplayBackend.resolve(null, true));
    }

    /** Every combination of the six inputs, against the rules in their order. */
    @Test
    public void forAppTable() {
        LinuxDisplayBackend[] mains = {X, W};
        LinuxDisplayBackend.Choice[] choices = LinuxDisplayBackend.Choice.values();
        boolean[] bools = {false, true};
        int cases = 0;
        for (boolean built : bools) {
            for (LinuxDisplayBackend main : mains) {
                for (LinuxDisplayBackend.Choice choice : choices) {
                    for (boolean session : bools) {
                        for (boolean waylandDesktop : bools) {
                            for (boolean inContainer : bools) {
                                LinuxDisplayBackend expected;
                                if (!built) expected = X;
                                else if (session && waylandDesktop) expected = W;
                                else if (session && inContainer) expected = X;
                                else if (choice == LinuxDisplayBackend.Choice.X11) expected = X;
                                else if (choice == LinuxDisplayBackend.Choice.WAYLAND) expected = W;
                                else expected = main;
                                assertEquals("built=" + built + " main=" + main + " choice=" + choice
                                        + " session=" + session + " waylandDesktop=" + waylandDesktop
                                        + " inContainer=" + inContainer, expected,
                                    LinuxDisplayBackend.forApp(built, main, choice, session,
                                        waylandDesktop, inContainer));
                                cases++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(96, cases);
    }

    @Test
    public void anAppInAContainerOpensAsAWindowButItsDesktopStaysOnX11() {
        assertEquals(W, LinuxDisplayBackend.forApp(true, W, DEF, false, false, true));
        assertEquals(X, LinuxDisplayBackend.forApp(true, W, DEF, true, false, true));
        assertEquals(X, LinuxDisplayBackend.forApp(true, X, LinuxDisplayBackend.Choice.WAYLAND,
            true, false, true));
    }

    @Test
    public void choiceReadsStoredValuesAndNothingElseIsAChoice() {
        assertEquals(LinuxDisplayBackend.Choice.X11, LinuxDisplayBackend.Choice.parse("x11"));
        assertEquals(LinuxDisplayBackend.Choice.WAYLAND, LinuxDisplayBackend.Choice.parse("wayland"));
        assertEquals(DEF, LinuxDisplayBackend.Choice.parse(null));
        assertEquals(DEF, LinuxDisplayBackend.Choice.parse("default"));
        assertEquals(DEF, LinuxDisplayBackend.Choice.parse("wayland2"));
    }

    @Test
    public void theRowIsOfferedOnlyWhereTheChoiceCanMatter() {
        // Linux apps and X11 desktops in the prefix, and apps in a container.
        assertEquals(true, LinuxDisplayBackend.choosable(true, false, false, false));
        assertEquals(true, LinuxDisplayBackend.choosable(true, true, false, false));
        assertEquals(true, LinuxDisplayBackend.choosable(true, false, false, true));
        // No Wayland build, a Wayland desktop, a desktop inside a container: no row.
        assertEquals(false, LinuxDisplayBackend.choosable(false, false, false, false));
        assertEquals(false, LinuxDisplayBackend.choosable(true, true, true, false));
        assertEquals(false, LinuxDisplayBackend.choosable(true, true, false, true));
    }
}
