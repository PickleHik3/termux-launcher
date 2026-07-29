package com.termux.terminal;

import java.util.Base64;

public class KittyGraphicsProtocolTest extends TerminalTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(8, 4);
    }

    public void testCanonicalSupportProbe() {
        assertEnteringStringGivesResponse("\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\",
            "\033_Gi=31;OK\033\\");
    }

    public void testQueryRejectsBadLengthFormatAndMedium() {
        assertEnteringStringGivesResponse("\033_Gi=1,s=2,v=1,a=q,t=d,f=24;AAAA\033\\",
            "\033_Gi=1;EINVAL:invalid image data\033\\");
        assertEnteringStringGivesResponse("\033_Gi=2,a=q,t=d,f=7;AAAA\033\\",
            "\033_Gi=2;ENOSYS:unsupported image format\033\\");
        assertEnteringStringGivesResponse("\033_Gi=3,a=q,t=f,f=100;AAAA\033\\",
            "\033_Gi=3;ENOSYS:unsupported transmission medium\033\\");
    }

    public void testPngQueryChecksHeaderAndDimensionsWithoutDisplaying() {
        byte[] header = new byte[24];
        byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[12] = 'I'; header[13] = 'H'; header[14] = 'D'; header[15] = 'R';
        header[19] = 2;
        header[23] = 3;
        String payload = Base64.getEncoder().encodeToString(header);
        assertEnteringStringGivesResponse("\033_Gi=9,a=q,f=100;" + payload + "\033\\",
            "\033_Gi=9;OK\033\\");
        assertEquals(0, mTerminal.getScreen().getKittyImageBytes());
    }

    public void testQuietModesSuppressSelectedResponses() {
        assertEnteringStringGivesResponse("\033_Gi=1,s=1,v=1,a=q,f=24,q=1;AAAA\033\\", "");
        assertEnteringStringGivesResponse("\033_Gi=2,s=2,v=1,a=q,f=24,q=2;AAAA\033\\", "");
    }

    /**
     * q=2 means "suppress everything", not just failures. Only the error half was covered before, so
     * a q=2 success still wrote OK to the tty; with no application reading the reply that lands in
     * the shell's input line and corrupts the next prompt. timg sends a=T with q=2.
     */
    public void testQuietTwoAlsoSuppressesSuccessResponses() {
        assertEnteringStringGivesResponse("\033_Gi=1,s=1,v=1,a=q,f=24,q=2;AAAA\033\\", "");
        assertEnteringStringGivesResponse("\033_Gi=5,a=d,q=2;\033\\", "");
        // q=1 still reports errors, so the two levels stay distinguishable.
        assertEnteringStringGivesResponse("\033_Gi=6,s=2,v=1,a=q,f=24,q=1;AAAA\033\\",
            "\033_Gi=6;EINVAL:invalid image data\033\\");
    }

    /**
     * A control-only command carries no payload, so it has no ';'. Requiring one rejected the
     * canonical delete form outright and made the parser answer EINVAL to well-formed input.
     */
    public void testControlOnlyCommandsNeedNoPayloadSeparator() {
        assertEnteringStringGivesResponse("\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\",
            "\033_Gi=31;OK\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=I,i=31\033\\", "\033_Gi=31;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=32,a=d\033\\", "\033_Gi=32;OK\033\\");
        // Malformed control data is still reported, so dropping the separator check did not turn the
        // parser permissive.
        assertEnteringStringGivesResponse("\033_Ga=T,f=nope\033\\",
            "\033_G;EINVAL:invalid f value\033\\");
    }

    public void testControlOnlyHeaderStartsAChunkedUploadLikeRealClients() {
        // chafa emits a control-only header and then continuation chunks. The header must be accepted
        // rather than answered with EINVAL, and the format rejection must be the declared Tier 1
        // scope limit (PNG only) rather than a parse failure.
        assertEnteringStringGivesResponse("\033_Gi=41,a=T,f=32,s=2,v=2,c=2,r=1,m=1\033\\",
            "\033_Gi=41;ENOSYS:Tier 1 display accepts PNG only\033\\");
    }

    public void testChunkedUploadCollectsDataAndReportsInvalidPngAtEnd() {
        enterString("\033_Gi=7,a=T,f=100,m=1;bm90\033\\");
        assertEquals("", mOutput.getOutputAndClear());
        assertEnteringStringGivesResponse("\033_Gm=0;cG5n\033\\",
            "\033_Gi=7;EINVAL:invalid PNG\033\\");
    }

    public void testDeleteAbortsChunkedUpload() {
        enterString("\033_Gi=7,a=T,f=100,m=1;bm90\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=I,i=7;\033\\", "\033_Gi=7;OK\033\\");
        // The old upload is gone, so a continuation is rejected as an unsupported standalone transmit.
        assertEnteringStringGivesResponse("\033_Gm=0;cG5n\033\\", "");
    }

    public void testNonGraphicsApcRemainsIgnored() {
        assertEnteringStringGivesResponse("\033_not graphics\033\\", "");
        assertFalse(mTerminal.getScreen().getTranscriptText().contains("graphics"));
    }

    public void testCommandParserRejectsUnboundedOrInvalidControls() {
        try {
            KittyGraphicsProtocol.Command.parse("i=-1");
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            KittyGraphicsProtocol.Command.parse("format=100");
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
