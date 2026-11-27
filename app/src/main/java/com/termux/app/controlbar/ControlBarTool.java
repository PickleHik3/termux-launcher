package com.termux.app.controlbar;

/**
 * The control bar's tools (#424): the bottom row's tools for what is in front, then navigation mode
 * and search, which every place has.
 */
public enum ControlBarTool {
    /** Home's pinned apps: on Home the bar is the dock. */
    PINNED_APPS,
    /** Home's drawer: opens the drawer sheet (#436). */
    DRAWER,
    /** Shows or hides the keyboard. */
    KEYBOARD,
    /** A new terminal stacked below the focused one. */
    SPLIT,
    /** A new terminal after the focused window. */
    NEW_TERMINAL,
    /** The focused window's menu. */
    WINDOW_MENU,
    /** Touchpad or direct touch, for a Linux app. */
    MOUSE_MODE,
    /** Navigation mode: windows take no input and one finger moves over the whole stack. */
    NAVIGATION,
    /** Search: terminals, Linux apps and Android apps. */
    SEARCH
}
