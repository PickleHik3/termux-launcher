package com.termux.terminal;

public class ApcTest extends TerminalTestCase {

    public void testApcHandlingCurrentBehavior() {
        // At time of writing this is part of what yazi sends for probing for kitty graphics protocol support:
        // https://github.com/sxyazi/yazi/blob/0cdaff98d0b3723caff63eebf1974e7907a43a2c/yazi-adapter/src/emulator.rs#L129
        // This should not result in anything being written to the screen. Tier-1 kitty graphics support answers
        // the query on stdin instead.
        withTerminalSized(2, 2)
            .enterString("\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\");
        assertEquals("\033_Gi=31;OK\033\\", mOutput.getOutputAndClear());
        assertFalse(mTerminal.getScreen().getTranscriptText().contains("AA"));

        // It is ok for the APC content to be non printable characters:
        withTerminalSized(12, 2)
            .enterString("hello \033_some\023\033_\\apc#end\033\\ world");
        String transcript = mTerminal.getScreen().getTranscriptText();
        assertTrue(transcript.contains("hello"));
        assertTrue(transcript.contains("world"));
        assertFalse(transcript.contains("apc"));
    }

}
