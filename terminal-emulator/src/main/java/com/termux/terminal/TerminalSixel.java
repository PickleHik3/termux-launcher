package com.termux.terminal;

import android.graphics.Bitmap;

public class TerminalSixel {

    public static final String LOG_TAG = "TerminalSixel";

    public static final int[] SIXEL__INITIAL_COLOR_MAP = {
        0xFF000000, 0xFF3333CC, 0xFFCC2323, 0xFF33CC33, 0xFFCC33CC, 0xFF33CCCC, 0xFFCCCC33, 0xFF777777,
        0xFF444444, 0xFF565699, 0xFF994444, 0xFF569956, 0xFF995699, 0xFF569999, 0xFF999956, 0xFFCCCCCC
    };

    public static final int SIXEL__LINE_LEN = 6;
    public static final int SIXEL__BITMAP_RESIZE_EXTRA_DIMENSION = 100;
    public static final int SIXEL__MAX_REPEAT = 8192;

    protected final TerminalSessionClient mClient;
    protected Bitmap mBitmap;
    /**
     * Set for a sixel decoded into plain ints (see {@link #buildHeap}) instead of a {@link Bitmap}:
     * the Ghostty core hands the pixels on as a kitty image, and the decoder runs on a JVM too.
     */
    private final boolean mHeap;
    private int[] mPixels;
    private int mPixelsWidth;
    private int mPixelsHeight;
    private final long mMaxPixelBytes;
    protected int mWidth;
    protected int mHeight;
    protected int mCurX;
    protected int mCurY;
    protected final int[] mColorMap;
    protected int mColor;

    protected TerminalSixel(TerminalSessionClient client, Bitmap bitmap) {
        this(client, bitmap, false, 0, 0, TerminalBitmap.MAX_BITMAP_SIZE);
    }

    private TerminalSixel(TerminalSessionClient client, Bitmap bitmap, boolean heap, int width, int height,
                          long maxPixelBytes) {
        mClient = client;
        mBitmap = bitmap;
        mHeap = heap;
        mMaxPixelBytes = maxPixelBytes;
        if (heap) {
            mPixels = new int[width * height];
            mPixelsWidth = width;
            mPixelsHeight = height;
        }
        mColorMap = new int[256];
        System.arraycopy(SIXEL__INITIAL_COLOR_MAP, 0, mColorMap, 0, 16);
        mColor = mColorMap[0];
    }

    public static TerminalSixel build(TerminalSessionClient client, int bitmapWidth, int bitmapHeight) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0);
            return new TerminalSixel(client, bitmap);
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) System.gc();
            Logger.logError(client, LOG_TAG,
                "Create sixel bitmap for width " + bitmapWidth + " with height " + bitmapHeight + " failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * A sixel whose pixels are kept as ints (transparent to begin with), growing as the bitmap one
     * does but never past {@code maxPixelBytes}; null when that does not fit.
     */
    public static TerminalSixel buildHeap(TerminalSessionClient client, int width, int height, long maxPixelBytes) {
        try {
            if ((long) width * height * 4L > maxPixelBytes) return null;
            return new TerminalSixel(client, null, true, width, height, maxPixelBytes);
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) System.gc();
            Logger.logError(client, LOG_TAG,
                "Create sixel pixels for width " + width + " with height " + height + " failed: " + t.getMessage());
            return null;
        }
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    /** The pixels of a {@link #buildHeap} sixel, ARGB, {@link #getRasterWidth()} per row; null once dropped. */
    public int[] getPixels() {
        return mPixels;
    }

    /** The width of the pixel store, which is at least {@link #getWidth()}. */
    public int getRasterWidth() {
        return mHeap ? mPixelsWidth : mBitmap == null ? 0 : mBitmap.getWidth();
    }

    /** The height of the pixel store, which is at least {@link #getHeight()}. */
    public int getRasterHeight() {
        return mHeap ? mPixelsHeight : mBitmap == null ? 0 : mBitmap.getHeight();
    }

    private boolean hasRaster() {
        return mHeap ? mPixels != null : mBitmap != null;
    }

    private void putPixel(int x, int y, int color) {
        if (mHeap) {
            mPixels[y * mPixelsWidth + x] = color;
        } else {
            mBitmap.setPixel(x, y, color);
        }
    }

    /** Grow the pixel store to {@code w} by {@code h}, keeping what it holds; false (and gone) if that fails. */
    private boolean growRaster(int w, int h) {
        if (!mHeap) {
            mBitmap = TerminalBitmap.resizeBitmap(LOG_TAG, "sixel", mClient, mBitmap, w, h);
            return mBitmap != null;
        }
        long bytes = (long) w * (long) h * 4L;
        if (bytes <= 0 || bytes > mMaxPixelBytes) {
            Logger.logError(mClient, LOG_TAG,
                "The new sixel bitmap after resize with width " + w + " and height " + h +
                    " has size " + bytes + " greater than max bitmap size " + mMaxPixelBytes);
            mPixels = null;
            return false;
        }
        int[] grown;
        try {
            grown = new int[w * h];
        } catch (OutOfMemoryError e) {
            Logger.logWarn(mClient, LOG_TAG, "Out of memory resizing sixel bitmap");
            mPixels = null;
            return false;
        }
        int copyW = Math.min(mPixelsWidth, w);
        int copyH = Math.min(mPixelsHeight, h);
        for (int y = 0; y < copyH; y++) {
            System.arraycopy(mPixels, y * mPixelsWidth, grown, y * w, copyW);
        }
        mPixels = grown;
        mPixelsWidth = w;
        mPixelsHeight = h;
        return true;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public boolean readData(int codePoint, int repeat) {
        if (!hasRaster()) {
            return false;
        }

        if (repeat > SIXEL__MAX_REPEAT) {
            Logger.logError(mClient, LOG_TAG,
                "The sixel repeat value " + repeat + " is greater than max repeat value " + SIXEL__MAX_REPEAT);
            return false;
        }

        if (codePoint == '$') {
            mCurX = 0;
            return true;
        }

        if (codePoint == '-') {
            mCurX = 0;
            mCurY += SIXEL__LINE_LEN;
            return true;
        }

        if (getRasterWidth() < mCurX + repeat) {
            int newBitmapWidth = mCurX + repeat + SIXEL__BITMAP_RESIZE_EXTRA_DIMENSION;
            if (newBitmapWidth < 0) {
                Logger.logError(mClient, LOG_TAG, "The new sixel bitmap width overflowed");
                return false;
            }

            if (!growRaster(newBitmapWidth, getRasterHeight())) {
                return false;
            }
        }

        if (getRasterHeight() < mCurY + SIXEL__LINE_LEN) {
            int newBitmapHeight = mCurY + SIXEL__BITMAP_RESIZE_EXTRA_DIMENSION;
            if (newBitmapHeight < 0) {
                Logger.logError(mClient, LOG_TAG, "The new sixel bitmap height overflowed");
                return false;
            }

            if (!growRaster(getRasterWidth(), newBitmapHeight)) {
                return false;
            }
        }

        if (mCurX + repeat > getRasterWidth()) {
            repeat = getRasterWidth() - mCurX;
        }

        if (mCurY + SIXEL__LINE_LEN > getRasterHeight()) {
            Logger.logError(mClient, LOG_TAG,
                "The sixel cursor y position " + (mCurY + SIXEL__LINE_LEN) + " is greater than bitmap height " + getRasterHeight());
            return false;
        }

        if (repeat > 0 && codePoint >= '?' && codePoint <= '~') {
            int b = codePoint - '?';
            if (mCurY + SIXEL__LINE_LEN > mHeight) {
                mHeight = mCurY + SIXEL__LINE_LEN;
            }

            while (repeat-- > 0) {
                for (int i = 0; i < SIXEL__LINE_LEN; i++) {
                    if ((b & (1 << i)) != 0) {
                        putPixel(mCurX, mCurY + i, mColor);
                    }
                }

                mCurX += 1;
                if (mCurX > mWidth) {
                    mWidth = mCurX;
                }
            }
        }

        return true;
    }

    public boolean resize(int sixelWidth, int sixelHeight) {
        if (!hasRaster() || sixelWidth < 1 || sixelHeight < 1) {
            return false;
        }

        int newBitmapWidth = Math.max(sixelWidth, getRasterWidth());
        int newBitmapHeight = Math.max(sixelHeight, getRasterHeight());
        if (newBitmapWidth > getRasterWidth() || newBitmapHeight > getRasterHeight()) {
            return growRaster(newBitmapWidth, newBitmapHeight);
        }

        return true;
    }

    public void setColor(int color) {
        if (color >= 0 && color < mColorMap.length) {
            mColor = mColorMap[color];
        }
    }

    public void setRGBColor(int color, int r, int g, int b) {
        if (color >= 0 && color < mColorMap.length) {
            int red = Math.min(255, r * 255 / 100);
            int green = Math.min(255, g * 255 / 100);
            int blue = Math.min(255, b * 255 / 100);
            mColor = 0xff000000 + (red << 16) + (green << 8) + blue;
            mColorMap[color] = mColor;
        }
    }
}
