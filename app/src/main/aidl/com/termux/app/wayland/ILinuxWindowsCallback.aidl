package com.termux.app.wayland;

/**
 * What the Linux windows host tells the launcher (ILinuxWindows has the contract). Oneway, so a
 * slow or gone launcher never blocks the host. Arrives on binder threads, in the host's order.
 */
oneway interface ILinuxWindowsCallback {
    /** First on every addCallback: the host's ILinuxWindows.VERSION and its process id. */
    void onAttached(int interfaceVersion, int hostPid);
    /** state: an ILinuxWindows.STATE_*. message: one line for the screen, may be empty. */
    void onHostState(int state, String message);
    /**
     * A window to show. clientPid: the app's process id, 0 when unknown. flags: ILinuxWindows.WINDOW_*.
     * parentId: the window this one is a dialog of (xdg parent, X11 transient-for), as it stood
     * when the window was announced; 0 for a window of its own (since VERSION 5).
     * A window the app hides and shows again comes back as a new window with a new id.
     */
    void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId);
    /** Title, app id or the drawn size (pixels, 0 before the first frame) changed. */
    void onWindowChanged(long id, String title, String appId, int width, int height);
    void onWindowDestroyed(long id);
    /**
     * The X display Xwayland got, such as ":0" (the value for DISPLAY), once it is ready; empty
     * when there is none: Xwayland was not asked for, is gone, or the host stopped. Replayed on
     * addCallback after onHostState.
     */
    void onXDisplay(String display);
    /** Plain text a Linux app copied, for Android's clipboard. Never longer than the host's cap. */
    void onClipboardText(String text);
}
