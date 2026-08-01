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
        // chafa emits a control-only raw-RGBA header and then continuation chunks. The header must
        // be accepted silently and the chunks collected; a payload that does not match s and v is
        // answered when the final chunk arrives.
        enterString("\033_Gi=41,a=T,f=32,s=2,v=2,c=2,r=1,m=1\033\\");
        assertEquals("", mOutput.getOutputAndClear());
        enterString("\033_Gm=1;" + base64(new byte[8]) + "\033\\");
        assertEquals("", mOutput.getOutputAndClear());
        assertEnteringStringGivesResponse("\033_Gm=0;" + base64(new byte[4]) + "\033\\",
            "\033_Gi=41;EINVAL:pixel data does not match s and v\033\\");
    }

    public void testRawDisplayRequiresDimensions() {
        assertEnteringStringGivesResponse("\033_Gi=42,a=T,f=32;AAAA\033\\",
            "\033_Gi=42;EINVAL:raw pixel data requires s and v\033\\");
        assertEnteringStringGivesResponse("\033_Gi=43,a=T,f=24,s=2;AAAA\033\\",
            "\033_Gi=43;EINVAL:raw pixel data requires s and v\033\\");
    }

    public void testRawDisplayRejectsOversizeAtHeader() {
        // 4000 * 4000 * 4 bytes decoded is over the 32 MiB session limit, so the header is refused
        // before any chunk data is accepted.
        assertEnteringStringGivesResponse("\033_Gi=44,a=T,f=32,s=4000,v=4000,m=1\033\\",
            "\033_Gi=44;ENOSPC:decoded image exceeds session limit\033\\");
    }

    public void testRawDisplayRejectsUnknownCompression() {
        assertEnteringStringGivesResponse("\033_Gi=45,a=T,f=32,s=1,v=1,o=x;AAAA\033\\",
            "\033_Gi=45;ENOSYS:unsupported compression\033\\");
    }

    public void testUnsupportedDisplayFormatIsRejected() {
        assertEnteringStringGivesResponse("\033_Gi=46,a=T,f=7;AAAA\033\\",
            "\033_Gi=46;ENOSYS:unsupported image format\033\\");
    }

    public void testRawSingleChunkSizeMismatchIsSynchronous() {
        // 2x2 f=24 needs 12 bytes; 4 are sent.
        assertEnteringStringGivesResponse("\033_Gi=47,a=T,f=24,s=2,v=2;" + base64(new byte[4]) + "\033\\",
            "\033_Gi=47;EINVAL:pixel data does not match s and v\033\\");
    }

    public void testRawQueryHonorsZlibCompression() {
        byte[] pixels = new byte[4];
        assertEnteringStringGivesResponse("\033_Gi=48,s=1,v=1,a=q,t=d,f=32,o=z;"
            + base64(deflate(pixels)) + "\033\\", "\033_Gi=48;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=49,s=2,v=2,a=q,t=d,f=32,o=z;"
            + base64(deflate(pixels)) + "\033\\", "\033_Gi=49;EINVAL:invalid image data\033\\");
    }

    public void testRawPixelsToArgbConvertsBothFormats() {
        int[] rgb = KittyGraphicsProtocol.rawPixelsToArgb(
            new byte[] {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc},
            2, 1, 24);
        assertEquals(0xff112233, rgb[0]);
        assertEquals(0xffaabbcc, rgb[1]);
        int[] rgba = KittyGraphicsProtocol.rawPixelsToArgb(
            new byte[] {(byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x80}, 1, 1, 32);
        assertEquals(0x80112233, rgba[0]);
        try {
            KittyGraphicsProtocol.rawPixelsToArgb(new byte[5], 1, 1, 32);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testInflateEnforcesExactExpectedSize() {
        byte[] pixels = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        assertTrue(java.util.Arrays.equals(pixels, KittyGraphicsProtocol.inflate(deflate(pixels), 8)));
        try {
            KittyGraphicsProtocol.inflate(deflate(pixels), 4);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            KittyGraphicsProtocol.inflate(deflate(pixels), 16);
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            KittyGraphicsProtocol.inflate(new byte[] {0x00, 0x00}, 8);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testPlacementOfUnknownImageAnswersEnoent() {
        assertEnteringStringGivesResponse("\033_Ga=p,i=99\033\\",
            "\033_Gi=99;ENOENT:image not found\033\\");
        assertEnteringStringGivesResponse("\033_Ga=p,I=44\033\\",
            "\033_GI=44;ENOENT:image not found\033\\");
    }

    public void testStoreOnlyTransmissionRequiresAnIdentifier() {
        assertEnteringStringGivesResponse("\033_Ga=t,f=24,s=1,v=1;AAAA\033\\",
            "\033_G;EINVAL:storing an image requires i or I\033\\");
    }

    public void testAnimationActionsOnMissingImagesAnswerEnoent() {
        assertEnteringStringGivesResponse("\033_Gi=3,a=a\033\\",
            "\033_Gi=3;ENOENT:image not found\033\\");
        assertEnteringStringGivesResponse("\033_Gi=3,a=f,f=24,s=1,v=1;AAAA\033\\",
            "\033_Gi=3;ENOENT:image not found\033\\");
        assertEnteringStringGivesResponse("\033_Gi=3,a=c\033\\",
            "\033_Gi=3;ENOENT:image not found\033\\");
    }

    public void testAnimationControlSucceedsSilentlyOnAReservedImage() {
        // The reservation is synchronous, so the entry exists while its decode is still pending.
        enterString("\033_Gi=44,a=t,f=24,s=1,v=1;AAAA\033\\");
        // Matching kitty, a successful a=a produces no reply at all.
        assertEnteringStringGivesResponse("\033_Gi=44,a=a,s=3,v=2,r=1,z=120\033\\", "");
    }

    public void testFrameTransmitValidatesItsRectangleSynchronously() {
        enterString("\033_Gi=45,a=t,f=24,s=2,v=2;" + base64(new byte[12]) + "\033\\");
        assertEnteringStringGivesResponse("\033_Gi=45,a=f,f=24,s=3,v=1,x=0,y=0;" + base64(new byte[9]) + "\033\\",
            "\033_Gi=45;EINVAL:frame rectangle out of bounds\033\\");
        assertEnteringStringGivesResponse("\033_Gi=45,a=f,f=24,s=1,v=1,x=2,y=0;AAAA\033\\",
            "\033_Gi=45;EINVAL:frame rectangle out of bounds\033\\");
    }

    public void testFrameChunksMustRepeatTheFrameAction() {
        enterString("\033_Gi=46,a=t,f=24,s=1,v=1;AAAA\033\\");
        enterString("\033_Gi=46,a=f,f=24,s=1,v=1,m=1;AA\033\\");
        // A continuation chunk may carry a=f alongside m and q, so it must not read as a new command.
        assertEnteringStringGivesResponse("\033_Ga=f,m=1,q=1;AA\033\\", "");
        // A fresh non-continuation command interrupts the pending frame upload.
        assertEnteringStringGivesResponse("\033_Gi=47,a=t,f=24,s=1,v=1;AAAA\033\\",
            "\033_Gi=46;EINVAL:chunk upload interrupted\033\\");
    }

    public void testComposeValidatesFramesAndRectanglesSynchronously() {
        enterString("\033_Gi=48,a=t,f=24,s=4,v=4;" + base64(new byte[48]) + "\033\\");
        assertEnteringStringGivesResponse("\033_Gi=48,a=c,r=2,c=1\033\\",
            "\033_Gi=48;ENOENT:no such frame\033\\");
        assertEnteringStringGivesResponse("\033_Gi=48,a=c,r=1,c=1,w=9,h=1\033\\",
            "\033_Gi=48;EINVAL:rectangle out of bounds\033\\");
        assertEnteringStringGivesResponse("\033_Gi=48,a=c,r=1,c=1,w=2,h=2,x=1,y=1,X=0,Y=0\033\\",
            "\033_Gi=48;EINVAL:source and destination rectangles overlap\033\\");
    }

    public void testDeleteFrameFormsOnAFramelessImage() {
        enterString("\033_Gi=49,a=t,f=24,s=1,v=1;AAAA\033\\");
        // d=f with no extra frames is a no-op; d=F deletes the whole image.
        assertEnteringStringGivesResponse("\033_Gi=49,a=d,d=f\033\\", "\033_Gi=49;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=49,a=d,d=F\033\\", "\033_Gi=49;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=49,a=a\033\\",
            "\033_Gi=49;ENOENT:image not found\033\\");
    }

    public void testComposeRegionBlendsAndReplaces() {
        int[] under = { 0xff000000, 0xff000000, 0xff000000, 0xff000000 };
        int[] over = { 0x80ff0000, 0x00ff0000, 0xffffffff, 0x40008000 };
        int[] blended = under.clone();
        KittyGraphicsProtocol.composeRegion(blended, 2, over, 2, 2, 2, 0, 0, 0, 0, false);
        assertEquals("opaque over pixel replaces", 0xffffffff, blended[2]);
        assertEquals("fully transparent over pixel leaves the canvas", 0xff000000, blended[1]);
        assertEquals("alpha stays full over an opaque canvas", 0xff, blended[0] >>> 24);
        assertEquals("half red over black is half-bright red", 0x80, (blended[0] >> 16) & 0xff);
        int[] replaced = under.clone();
        KittyGraphicsProtocol.composeRegion(replaced, 2, over, 2, 2, 2, 0, 0, 0, 0, true);
        assertEquals("replace mode copies alpha verbatim", 0x00ff0000, replaced[1]);
        int[] offset = new int[9];
        KittyGraphicsProtocol.composeRegion(offset, 3, over, 2, 1, 1, 1, 1, 2, 2, true);
        assertEquals("offsets address the right cells", 0x40008000, offset[8]);
        assertEquals(0, offset[0]);
    }

    public void testUnicodePlaceholdersAnswerEnosys() {
        assertEnteringStringGivesResponse("\033_Gi=3,a=T,U=1,f=24,s=1,v=1;AAAA\033\\",
            "\033_Gi=3;ENOSYS:unicode placeholders are not supported\033\\");
        assertEnteringStringGivesResponse("\033_Gi=3,a=p,U=1\033\\",
            "\033_Gi=3;ENOSYS:unicode placeholders are not supported\033\\");
    }

    public void testDeleteFormsValidateTheirRequiredKeys() {
        assertEnteringStringGivesResponse("\033_Ga=d,d=p\033\\",
            "\033_G;EINVAL:delete by position requires x and y\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=q,x=1,y=1\033\\",
            "\033_G;EINVAL:delete by position requires x and y\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=x\033\\",
            "\033_G;EINVAL:delete by column requires x\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=y\033\\",
            "\033_G;EINVAL:delete by row requires y\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=z\033\\",
            "\033_G;EINVAL:delete by z requires z\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=n\033\\",
            "\033_G;EINVAL:delete by number requires I\033\\");
        assertEnteringStringGivesResponse("\033_Ga=d,d=w\033\\",
            "\033_G;EINVAL:unknown delete specifier\033\\");
    }

    public void testDeleteFormsWithValidKeysAnswerOkOnEmptyScreen() {
        // Idempotent deletes: nothing on screen still answers OK, and only identified commands reply.
        assertEnteringStringGivesResponse("\033_Ga=d,d=a\033\\", "");
        assertEnteringStringGivesResponse("\033_Gi=8,a=d,d=I\033\\", "\033_Gi=8;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=8,a=d,d=p,x=1,y=1\033\\", "\033_Gi=8;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=8,a=d,d=q,x=1,y=1,z=0\033\\", "\033_Gi=8;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=8,a=d,d=c\033\\", "\033_Gi=8;OK\033\\");
        assertEnteringStringGivesResponse("\033_Gi=8,a=d,d=Z,z=-1\033\\", "\033_Gi=8;OK\033\\");
    }

    public void testComputeCropClampsAndRejects() {
        assertTrue(java.util.Arrays.equals(new int[] {0, 0, 10, 8},
            KittyGraphicsProtocol.computeCrop(10, 8, 0, 0, 0, 0)));
        assertTrue(java.util.Arrays.equals(new int[] {2, 3, 4, 5},
            KittyGraphicsProtocol.computeCrop(10, 8, 2, 3, 4, 5)));
        assertTrue("width and height clamp to the image edge", java.util.Arrays.equals(new int[] {8, 6, 2, 2},
            KittyGraphicsProtocol.computeCrop(10, 8, 8, 6, 99, 99)));
        assertNull(KittyGraphicsProtocol.computeCrop(10, 8, 10, 0, 1, 1));
        assertNull(KittyGraphicsProtocol.computeCrop(10, 8, 0, 8, 1, 1));
        assertNull(KittyGraphicsProtocol.computeCrop(10, 8, -1, 0, 1, 1));
        assertNull(KittyGraphicsProtocol.computeCrop(10, 8, 0, 0, -1, 0));
    }

    public void testPlacementSourceRectangleIsValidatedAgainstReservedDimensions() {
        // A raw store transmission reserves its dimensions synchronously, so a following placement
        // with an out-of-range source rectangle is rejected synchronously too — even though the
        // pixel decode itself is still in flight on the worker.
        enterString("\033_Gi=21,a=t,q=2,f=24,s=2,v=2;" + base64(new byte[12]) + "\033\\");
        assertEnteringStringGivesResponse("\033_Gi=21,a=p,q=1,x=5\033\\",
            "\033_Gi=21;EINVAL:invalid source rectangle\033\\");
    }

    private static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private static byte[] deflate(byte[] data) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[256];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while (!deflater.finished()) {
            int produced = deflater.deflate(buffer);
            out.write(buffer, 0, produced);
        }
        deflater.end();
        return out.toByteArray();
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
