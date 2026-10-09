package com.termux.privileged.lane;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Plain JUnit: the request line is a contract with a C client written separately, so the exact
 * shape — field order, TAB separation, what is refused — is pinned here rather than left to
 * whatever the server happens to accept.
 */
public class LaneRequestTest {

    private static final String BTOP = "/data/data/com.termux/files/home/.local/bin/btop";

    @Test
    public void aMinimalRunLineParses() throws Exception {
        LaneRequest request = LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\txterm-256color\t50\t180\n");
        assertEquals(BTOP, request.path);
        assertEquals("xterm-256color", request.term);
        assertEquals(50, request.rows);
        assertEquals(180, request.cols);
        assertEquals(Collections.emptyList(), request.args);
    }

    @Test
    public void argumentsFollowTheSizeAndKeepTheirOrder() throws Exception {
        LaneRequest request = LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\t--utf-force\t-p\t2");
        assertEquals(Arrays.asList("--utf-force", "-p", "2"), request.args);
    }

    @Test
    public void anEmptyArgumentIsKeptAsAnEmptyArgument() throws Exception {
        // split with a negative limit: a trailing empty field is a real argument, not dropped.
        LaneRequest request = LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\t\n");
        assertEquals(Collections.singletonList(""), request.args);
    }

    @Test
    public void anEmptyTermFallsBackToTheDefault() throws Exception {
        LaneRequest request = LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\t\t24\t80");
        assertEquals(LaneRequest.DEFAULT_TERM, request.term);
    }

    @Test
    public void theLineWorksWithOrWithoutItsNewline() throws Exception {
        assertEquals(24, LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80").rows);
        assertEquals(24, LaneRequest.parse("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\n").rows);
    }

    @Test
    public void theWrongMagicIsRefusedByName() {
        assertRefused("tlpriv2\trun\t" + BTOP + "\tscreen\t24\t80", "tlpriv2");
        assertRefused("\trun\t" + BTOP + "\tscreen\t24\t80", "protocol");
    }

    @Test
    public void onlyRunIsACommand() {
        assertRefused("tlpriv1\tstage\t" + BTOP + "\tscreen\t24\t80", "stage");
    }

    @Test
    public void tooFewFieldsIsMalformed() {
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24", "malformed");
        assertRefused("tlpriv1", "malformed");
        assertRefused("", "malformed");
    }

    @Test
    public void thePathMustBeAbsolute() {
        assertRefused("tlpriv1\trun\tbtop\tscreen\t24\t80", "absolute");
        assertRefused("tlpriv1\trun\t\tscreen\t24\t80", "absolute");
    }

    @Test
    public void sizesMustBeSmallPositiveNumbers() {
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t0\t80", "rows");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t-1", "cols");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\tabc\t80", "rows");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t70000", "cols");
    }

    @Test
    public void termIsAPlainTerminfoName() {
        assertRefused("tlpriv1\trun\t" + BTOP + "\txterm;rm -rf\t24\t80", "TERM");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tx y\t24\t80", "TERM");
    }

    @Test
    public void embeddedLineBreaksAreRefused() {
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\targ\nsecond line", "line breaks");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\r", "line breaks");
    }

    @Test
    public void nulNeverReachesExec() {
        assertRefused("tlpriv1\trun\t" + BTOP + "\0x\tscreen\t24\t80", "NUL");
        assertRefused("tlpriv1\trun\t" + BTOP + "\tscreen\t24\t80\ta\0b", "NUL");
    }

    private static void assertRefused(String line, String messageFragment) {
        try {
            LaneRequest.parse(line);
            fail("expected refusal of " + line.replace("\t", "<TAB>"));
        } catch (LaneRequest.Refused e) {
            assertTrue("message '" + e.getMessage() + "' should mention " + messageFragment,
                e.getMessage().contains(messageFragment));
        }
    }
}
