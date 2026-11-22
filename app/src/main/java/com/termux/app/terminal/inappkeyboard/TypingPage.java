package com.termux.app.terminal.inappkeyboard;

import androidx.annotation.NonNull;

/**
 * A page whose text fields the launcher's keyboard types into. The host asks the front page when
 * deciding who owns the keys (the typing claim).
 */
public interface TypingPage {

    /** Whether one of the page's text fields holds focus. */
    boolean isTyping();

    /** The in-app keyboard's interceptor while it types into the page. */
    @NonNull TerminalKeyEventHandler.KeyValueInterceptor typingKeys();
}
