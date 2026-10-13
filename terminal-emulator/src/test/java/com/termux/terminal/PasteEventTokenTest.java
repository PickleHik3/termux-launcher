package com.termux.terminal;

import junit.framework.TestCase;

/** The single-use password a paste event hands the program. */
public class PasteEventTokenTest extends TestCase {

	public void testRedeemedOnce() {
		PasteEventToken token = new PasteEventToken();
		String pw = token.issue(1000);
		assertEquals(32, pw.length());
		assertTrue(token.redeem(pw, 1001));
		assertFalse(token.redeem(pw, 1002));
	}

	public void testExpires() {
		PasteEventToken token = new PasteEventToken();
		String pw = token.issue(1000);
		assertFalse(token.redeem(pw, 1000 + PasteEventToken.LIFETIME_MILLIS));
		// Gone for good, not merely refused this once.
		assertFalse(token.redeem(pw, 1001));
	}

	public void testWrongPasswordLeavesTheRightOne() {
		PasteEventToken token = new PasteEventToken();
		String pw = token.issue(0);
		assertFalse(token.redeem("nope", 1));
		assertFalse(token.redeem(null, 1));
		assertTrue(token.redeem(pw, 2));
	}

	public void testNewerPasteReplacesOlder() {
		PasteEventToken token = new PasteEventToken();
		String first = token.issue(0);
		String second = token.issue(1);
		assertFalse(first.equals(second));
		assertFalse(token.redeem(first, 2));
		assertTrue(token.redeem(second, 2));
	}

	public void testNothingIssuedOrCleared() {
		PasteEventToken token = new PasteEventToken();
		assertFalse(token.redeem("", 0));
		String pw = token.issue(0);
		token.clear();
		assertFalse(token.redeem(pw, 1));
	}
}
