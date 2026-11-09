package com.termux.terminal;

import java.text.CharacterIterator;
import java.util.Locale;

/**
 * An {@code android.icu.text.BreakIterator} for JVM tests that forwards to the real ICU4J character
 * break rules, so {@link GraphemeClusterer} can be tested against actual extended grapheme
 * decisions. The android.jar stubs the unit tests run with implement none of this.
 */
final class Icu4jBreakIterator extends android.icu.text.BreakIterator {

    private final com.ibm.icu.text.BreakIterator mDelegate =
        com.ibm.icu.text.BreakIterator.getCharacterInstance(Locale.ROOT);

    /** How many times each setText overload was called, for tests that pin which one is used. */
    int mSetTextString;
    int mSetTextCharSequence;
    int mSetTextCharacterIterator;

    @Override
    public int first() {
        return mDelegate.first();
    }

    @Override
    public int last() {
        return mDelegate.last();
    }

    @Override
    public int next(int n) {
        return mDelegate.next(n);
    }

    @Override
    public int next() {
        return mDelegate.next();
    }

    @Override
    public int previous() {
        return mDelegate.previous();
    }

    @Override
    public int following(int offset) {
        return mDelegate.following(offset);
    }

    @Override
    public int preceding(int offset) {
        return mDelegate.preceding(offset);
    }

    @Override
    public boolean isBoundary(int offset) {
        return mDelegate.isBoundary(offset);
    }

    @Override
    public int current() {
        return mDelegate.current();
    }

    @Override
    public CharacterIterator getText() {
        return mDelegate.getText();
    }

    @Override
    public void setText(String text) {
        mSetTextString++;
        mDelegate.setText(text);
    }

    @Override
    public void setText(CharSequence text) {
        mSetTextCharSequence++;
        mDelegate.setText(text);
    }

    @Override
    public void setText(CharacterIterator text) {
        mSetTextCharacterIterator++;
        mDelegate.setText(text);
    }
}
