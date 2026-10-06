package com.termux.terminal;

/**
 * What a tap on a cell may open: the OSC 8 hyperlink the program attached to it, else the address
 * {@link UrlDetector} reads out of the text around it. One resolver for both, so a tap, wherever it
 * lands, offers the same thing the long-press "Select URL" list would.
 */
public final class TerminalLinks {

    /** A link under a cell. */
    public static final class Link {
        /** The URI to copy or open. */
        public final String uri;
        /** True for an OSC 8 hyperlink, whose target the program chose and need not match the text. */
        public final boolean hyperlink;

        Link(String uri, boolean hyperlink) {
            this.uri = uri;
            this.hyperlink = hyperlink;
        }
    }

    private TerminalLinks() {
    }

    /**
     * The link at external {@code row} (negative for the transcript) and {@code column}, or null.
     * An OSC 8 hyperlink wins over the text under it; the text is only read when {@code detectUrls}.
     */
    public static Link at(TerminalEmulator term, int column, int row, boolean detectUrls) {
        if (term == null) return null;
        String hyperlink = term.getHyperlinkUriAt(row, column);
        if (hyperlink != null) return new Link(hyperlink, true);
        if (!detectUrls) return null;
        UrlDetector.UrlSpan span = UrlDetector.at(term.getScreen(), column, row);
        return span == null ? null : new Link(span.url, false);
    }
}
