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
    /**
     * The window asked to be full screen (xdg set_fullscreen, _NET_WM_STATE_FULLSCREEN), or to
     * leave it. The launcher decides and answers with setFullscreen. A window that asked before
     * it was announced is heard right after onWindowCreated.
     */
    void onFullscreenRequest(long id, boolean fullscreen);
    /**
     * The window asked to be moved (xdg_toplevel.move, _NET_WM_MOVERESIZE move: a drag on its own
     * title bar) while a finger or button is down on it. Resize requests are not passed on.
     */
    void onMoveRequest(long id);
    /** The window asked for its window menu (xdg show_window_menu: a right-click on its title bar). */
    void onWindowMenuRequest(long id);
    /**
     * A text field in the window took keyboard focus (active: the app enabled zwp_text_input_v3)
     * or lost it. contentHint and contentPurpose: the protocol's content_hint bits and
     * content_purpose value, 0 when not active (since VERSION 7). Only the window with keyboard
     * focus (setFocus) has a field; X11 apps never report one.
     */
    void onTextInput(long id, boolean active, int contentHint, int contentPurpose);
    /**
     * An app asked for the window to come in front (xdg_activation_v1) with a token the host
     * honours: one made by an app the user was just using (since VERSION 8). The launcher shows
     * the window as a tap on its chip does.
     */
    void onActivationRequest(long id);
    /** Plain text a Linux app copied, for Android's clipboard. Never longer than the host's cap. */
    void onClipboardText(String text);
}
