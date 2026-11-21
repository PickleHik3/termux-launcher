package com.termux.terminal;

/**
 * Sixel on the Ghostty core. Ghostty ignores a sixel DCS, so the native write loop keeps its
 * bytes from the library and hands them here a few KiB at a time ({@link #feed}); at the end
 * {@link #end} says where the image goes, and the native side sends the pixels back to Ghostty as
 * a kitty image placed at the cursor.
 *
 * <p>The decoding is the Java core's own, {@link TerminalSixel}, read the way
 * {@code TerminalEmulator.doDeviceControl} reads it: colour registers, raster attributes, repeats,
 * {@code $} and {@code -}. Only the string is streamed instead of cut at {@code $ - #} and kept
 * as text, so a sixel costs its pixels and nothing else. The placement rule is
 * {@link TerminalBitmap}'s: the image is padded (or cut) to whole cells, no wider than the room
 * right of the cursor, and takes the cells it covers.
 *
 * <p>Bounds: a string longer than {@link TerminalEmulator#MAX_IMAGE_SEQUENCE_LENGTH} and pixels
 * past {@link #maxPixelBytes()} drop the sixel; the rest of the string is read and thrown away,
 * and output after it parses as usual.
 */
final class GhosttySixel {

    /** The least room (bytes of pixels) the Ghostty side can keep an image in: its store's limit. */
    static long maxPixelBytes() {
        return Math.min(TerminalBitmap.MAX_BITMAP_SIZE, KittyImageStore.MAX_STORED_BYTES);
    }

    /** Positions in the {@code meta} array {@link #end} fills; mirrored by {@code sx_send_image} in the shim. */
    static final int META_RASTER_WIDTH = 0, META_RASTER_HEIGHT = 1, META_WIDTH = 2, META_HEIGHT = 3,
        META_CELLS_WIDE = 4, META_CELLS_HIGH = 5, META_COUNT = 6;

    private static final int NUMBER_CAP = 100_000_000;

    private final TerminalSessionClient mClient;
    private final long mMaxPixelBytes;
    private final int mMaxDcsBytes;

    private TerminalSixel mSixel;
    private boolean mActive;
    private long mBytes;
    /** The command being read: 0 for none, else {@code "}, {@code #} or {@code !}. */
    private int mCommand;
    /** For {@code #}: 0 while reading the register number, 1 while reading its colour. */
    private int mPhase;
    private int mRegister;
    private final int[] mNumbers = new int[4];
    private int mArg;
    private int mRepeat = 1;

    GhosttySixel(TerminalSessionClient client) {
        this(client, maxPixelBytes(), TerminalEmulator.MAX_IMAGE_SEQUENCE_LENGTH);
    }

    GhosttySixel(TerminalSessionClient client, long maxPixelBytes, int maxDcsBytes) {
        mClient = client;
        mMaxPixelBytes = maxPixelBytes;
        mMaxDcsBytes = maxDcsBytes;
    }

    /** A sixel string began (after its {@code q}). */
    void begin() {
        abort();
        mActive = true;
        mSixel = TerminalSixel.buildHeap(mClient, 100, 100, mMaxPixelBytes);
    }

    /** Drop what has been read and expect no more of this string. */
    void abort() {
        mActive = false;
        mSixel = null;
        mBytes = 0;
        mCommand = 0;
        mPhase = 0;
        mArg = 0;
        mRepeat = 1;
    }

    /** Whether a sixel string is being read. */
    boolean isActive() {
        return mActive;
    }

    /** Whether the string being read still has an image to give (not dropped, not over a cap). */
    boolean isDecoding() {
        return mActive && mSixel != null;
    }

    /** The next bytes of the string. */
    void feed(byte[] data, int length) {
        if (!mActive) begin();
        for (int i = 0; i < length; i++) {
            if (++mBytes > mMaxDcsBytes) mSixel = null;
            if (mSixel == null) continue;  // dropped: the rest of the string is thrown away
            read(data[i] & 0xff);
        }
    }

    private void read(int b) {
        if (mCommand != 0) {
            if (b >= '0' && b <= '9') {
                if (mArg <= 3) mNumbers[mArg] = Math.min(NUMBER_CAP, mNumbers[mArg] * 10 + b - '0');
                return;
            }
            if (b == ';' && mCommand != '!') {
                if (mCommand == '#' && mPhase == 0) {
                    mRegister = mNumbers[0];
                    mPhase = 1;
                    clearNumbers();
                    return;
                }
                if (++mArg > 3) {
                    // As in the Java core, the fifth number ends the command and the ';' is skipped.
                    finishCommand();
                    return;
                }
                return;
            }
            finishCommand();
            if (mSixel == null) return;
        }
        switch (b) {
            case '"':
                mCommand = '"';
                clearNumbers();
                break;
            case '#':
                mCommand = '#';
                mPhase = 0;
                clearNumbers();
                break;
            case '!':
                mCommand = '!';
                clearNumbers();
                break;
            default:
                if (b == '$' || b == '-' || (b >= '?' && b <= '~')) {
                    if (!mSixel.readData(b, mRepeat)) mSixel = null;
                    mRepeat = 1;
                }
                break;
        }
    }

    private void clearNumbers() {
        mNumbers[0] = mNumbers[1] = mNumbers[2] = mNumbers[3] = 0;
        mArg = 0;
    }

    private void finishCommand() {
        int command = mCommand;
        mCommand = 0;
        switch (command) {
            case '"':
                // Pan;Pad;Ph;Pv: the image's size.
                if (mArg == 3 && mNumbers[2] > 0 && mNumbers[3] > 0 && !mSixel.resize(mNumbers[2], mNumbers[3]))
                    mSixel = null;
                break;
            case '#':
                if (mPhase == 0) {
                    mSixel.setColor(mNumbers[0]);
                } else if (mNumbers[0] == 2) {
                    mSixel.setRGBColor(mRegister, mNumbers[1], mNumbers[2], mNumbers[3]);
                }
                break;
            case '!':
                if (mNumbers[0] > TerminalSixel.SIXEL__MAX_REPEAT) {
                    Logger.logError(mClient, TerminalSixel.LOG_TAG, "The sixel repeat command Pn value "
                        + mNumbers[0] + " is greater than max repeat value " + TerminalSixel.SIXEL__MAX_REPEAT);
                    mSixel = null;
                } else {
                    mRepeat = mNumbers[0];
                }
                break;
            default:
                break;
        }
    }

    /**
     * The string ended with the cursor in column {@code cursorX} of a screen {@code columns} wide
     * and cells {@code cellW} by {@code cellH} pixels. Returns the pixels (ARGB, row after row at
     * {@code meta[META_RASTER_WIDTH]}) and fills {@code meta} with the raster's size, the size of the
     * image to place (the raster padded or cut to whole cells; what the raster lacks is transparent),
     * and how many cells wide it is; its rows follow from its height. Null when there is nothing to
     * place: the sixel was dropped, or empty of room.
     */
    int[] end(int cursorX, int columns, int cellW, int cellH, int[] meta) {
        try {
            if (mSixel != null) finishCommand();
            TerminalSixel sixel = mSixel;
            if (sixel == null || sixel.getPixels() == null) return null;
            int available = columns - cursorX;
            if (available < 1 || cellW < 1 || cellH < 1 || meta == null || meta.length < META_COUNT) return null;
            int rasterW = sixel.getRasterWidth(), rasterH = sixel.getRasterHeight();
            int width = rasterW, height = rasterH;
            int contentW = sixel.getWidth(), contentH = sixel.getHeight();
            // TerminalBitmap.resizeBitmapConstraints: whole cells, and no wider than the room. A sixel
            // whose content already is whole cells keeps its raster as it is, as the Java core does.
            if (contentW > (long) cellW * available || contentW % cellW != 0 || contentH % cellH != 0) {
                width = (int) Math.min((long) cellW * available, (long) ((contentW - 1) / cellW) * cellW + cellW);
                height = ((contentH - 1) / cellH) * cellH + cellH;
            }
            int cellsW = Math.min(available, (width + cellW - 1) / cellW);
            int cellsH = (height + cellH - 1) / cellH;
            if ((long) cellsW * cellW < width) width = cellsW * cellW;
            if (width < 1 || height < 1 || (long) width * height * 4L > mMaxPixelBytes) return null;
            meta[META_RASTER_WIDTH] = rasterW;
            meta[META_RASTER_HEIGHT] = rasterH;
            meta[META_WIDTH] = width;
            meta[META_HEIGHT] = height;
            meta[META_CELLS_WIDE] = cellsW;
            meta[META_CELLS_HIGH] = cellsH;
            return sixel.getPixels();
        } finally {
            abort();
        }
    }
}
