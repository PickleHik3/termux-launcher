package com.termux.app.wayland;

/**
 * Who owns what around the Wayland Display socket. The socket belongs to the launcher whenever the
 * build has Wayland Display, whatever Settings › Linux apps › Display says, so an app can be sent
 * to it at any time and X11 Display and Wayland Display can be up together. The setting only
 * decides which display the next launch and the next shell use.
 */
final class WaylandSocketPolicy {

    private WaylandSocketPolicy() {
    }

    /**
     * The launcher owns the socket (listens on it, starts the host on the first connection, stops
     * it when idle). Takes no setting on purpose: a change of the choice cannot move it.
     */
    static boolean ownsSocket(boolean waylandBuilt) {
        return waylandBuilt;
    }

    /**
     * The socket and Xwayland display go to new shells: only while Wayland Display is the main
     * setting, and while the host runs or the socket listens.
     */
    static boolean publishesToShells(boolean waylandChosen, boolean hostRunning, boolean listening) {
        return waylandChosen && (hostRunning || listening);
    }
}
