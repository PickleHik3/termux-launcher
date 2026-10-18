package com.termux.app.terminal;

import androidx.core.content.FileProvider;

/**
 * Serves the pictures a program copied ({@link TerminalClipboard#write}) to whichever app pastes
 * them: the clipboard grants each reader access to the one link. Its own class, so its manifest
 * entry never merges with another library's {@link FileProvider}.
 */
public final class ClipboardFileProvider extends FileProvider {
}
