package com.termux.app.wayland;

/**
 * What the Linux windows host tells the launcher. Oneway, so a slow or gone launcher never
 * blocks the host. Arrives on binder threads.
 */
oneway interface ILinuxWindowsCallback {
    /** running: the compositor runs in process pid. message: one line for the screen. */
    void onHostState(boolean running, int pid, String message);
    void onToplevelCreated(long id, String title, String appId);
    void onToplevelTitle(long id, String title, String appId);
    /** The shown size: the window geometry, or the whole buffer when there is none. */
    void onToplevelSize(long id, int width, int height);
    void onToplevelDestroyed(long id);
}
