package com.termux.terminal;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** A deliberately bounded Tier-1 implementation of kitty's APC graphics protocol. */
final class KittyGraphicsProtocol {

    static final int MAX_TRANSMITTED_BYTES = 16 * 1024 * 1024;
    static final int MAX_DECODED_BYTES = 32 * 1024 * 1024;
    private static final int MAX_CONTROL_BYTES = 2048;
    private static final int MAX_CONTROLS = 64;

    private static final ExecutorService PNG_DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TermuxKittyPngDecoder");
        thread.setDaemon(true);
        return thread;
    });

    private final TerminalEmulator emulator;
    private final TerminalOutput output;
    private final KittyImageStore store = new KittyImageStore();
    private Upload upload;
    private long generation;
    private long decodeBytesInFlight;

    KittyGraphicsProtocol(TerminalEmulator emulator, TerminalOutput output) {
        this.emulator = emulator;
        this.output = output;
    }

    void accept(String apc) {
        if (apc == null || apc.length() < 2 || apc.charAt(0) != 'G') return;
        // The payload, and therefore the ';' separator, is optional: a control-only command is well
        // formed. Requiring the separator made the canonical delete form (ESC _ G a=d ESC \) and the
        // control-only header that real clients send before chunked data fail with EINVAL.
        int separator = apc.indexOf(';', 1);
        String controlData = separator < 0 ? apc.substring(1) : apc.substring(1, separator);
        String payload = separator < 0 ? "" : apc.substring(separator + 1);
        Command command;
        try {
            command = Command.parse(controlData);
        } catch (IllegalArgumentException e) {
            reply(null, "EINVAL:" + printable(e.getMessage()), true, true);
            return;
        }

        if (command.action == 'd') {
            resetUploadAndDecodes();
            handleDelete(command);
            return;
        }
        if (command.action == 'q') {
            handleQuery(command, payload);
            return;
        }
        if (upload != null) {
            if (!command.isContinuation()) {
                Upload abandoned = upload;
                upload = null;
                reply(abandoned.command, "EINVAL:chunk upload interrupted", true, false);
            } else {
                appendChunk(upload, command, payload);
                return;
            }
        }
        if (command.action == 'p') {
            handlePlacement(command);
            return;
        }
        if (command.action == 'a') {
            handleAnimationControl(command);
            return;
        }
        if (command.action == 'c') {
            handleCompose(command);
            return;
        }
        if (command.action != 'T' && command.action != 't' && command.action != 'f') {
            reply(command, "ENOSYS:unsupported action", true, false);
            return;
        }
        if (command.placeholder != 0 && command.placeholder != 1) {
            reply(command, "EINVAL:unsupported unicode placeholder mode", true, false);
            return;
        }
        if (command.placeholder == 1 && command.action != 'T') {
            reply(command, "EINVAL:U is only valid for display commands", true, false);
            return;
        }
        if (command.medium != 'd') {
            reply(command, "ENOSYS:only direct transmission is supported", true, false);
            return;
        }
        if (command.format != 100 && command.format != 24 && command.format != 32) {
            reply(command, "ENOSYS:unsupported image format", true, false);
            return;
        }
        if (command.format != 100) {
            if (command.width <= 0 || command.height <= 0) {
                reply(command, "EINVAL:raw pixel data requires s and v", true, false);
                return;
            }
            if ((long) command.width * command.height * 4L > MAX_DECODED_BYTES) {
                reply(command, "ENOSPC:decoded image exceeds session limit", true, false);
                return;
            }
        }
        if (command.compression != 0 && command.compression != 'z') {
            reply(command, "ENOSYS:unsupported compression", true, false);
            return;
        }
        if (command.action == 'f') {
            // Frame data needs an existing image, and a raw frame's rectangle is checkable now.
            KittyImageStore.Entry entry = store.get(store.resolveId(command.imageId, command.number));
            if (entry == null) {
                reply(command, "ENOENT:image not found", true, false);
                return;
            }
            if (command.format != 100
                && !frameRectFits(entry, command.srcX, command.srcY, command.width, command.height)) {
                reply(command, "EINVAL:frame rectangle out of bounds", true, false);
                return;
            }
        }
        Upload next = new Upload(command);
        appendChunk(next, command, payload);
    }

    private static boolean frameRectFits(KittyImageStore.Entry entry, int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && w > 0 && h > 0
            && (long) x + w <= entry.width && (long) y + h <= entry.height;
    }

    private void appendChunk(Upload target, Command chunkCommand, String payload) {
        byte[] decoded;
        try {
            decoded = decodeBase64(payload);
        } catch (IllegalArgumentException e) {
            if (upload == target) upload = null;
            reply(target.command, "EINVAL:invalid base64 payload", true, false);
            return;
        }
        if (decoded.length > MAX_TRANSMITTED_BYTES - target.data.size()) {
            if (upload == target) upload = null;
            reply(target.command, "ENOSPC:image exceeds transmission limit", true, false);
            return;
        }
        target.data.write(decoded, 0, decoded.length);
        if (chunkCommand.more) {
            upload = target;
            return;
        }
        upload = null;
        if (target.command.action == 'f') {
            submitFrame(target.command, target.data.toByteArray());
        } else if (target.command.format == 100) {
            submitPng(target.command, target.data.toByteArray());
        } else {
            submitRaw(target.command, target.data.toByteArray());
        }
    }

    private void handleQuery(Command command, String payload) {
        if (command.medium != 'd') {
            reply(command, "ENOSYS:unsupported transmission medium", true, true);
            return;
        }
        byte[] decoded;
        try {
            decoded = decodeBase64(payload);
        } catch (IllegalArgumentException e) {
            reply(command, "EINVAL:invalid base64 payload", true, true);
            return;
        }
        if (command.compression != 0 && command.compression != 'z') {
            reply(command, "ENOSYS:unsupported compression", true, true);
            return;
        }
        boolean valid;
        if (command.format == 24 || command.format == 32) {
            long required = (long) command.width * command.height * (command.format / 8);
            if (command.compression == 'z') {
                boolean inflates;
                try {
                    inflate(decoded, required);
                    inflates = true;
                } catch (IllegalArgumentException e) {
                    inflates = false;
                }
                valid = command.width > 0 && command.height > 0 && inflates;
            } else {
                valid = command.width > 0 && command.height > 0 && required == decoded.length;
            }
        } else if (command.format == 100) {
            valid = pngDimensions(decoded) != null;
        } else {
            reply(command, "ENOSYS:unsupported image format", true, true);
            return;
        }
        reply(command, valid ? "OK" : "EINVAL:invalid image data", !valid, true);
    }

    private void submitPng(Command command, byte[] png) {
        int[] dimensions = pngDimensions(png);
        if (dimensions == null) {
            reply(command, "EINVAL:invalid PNG", true, false);
            return;
        }
        submitTransmission(command, png.length, dimensions[0], dimensions[1], () -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length, options);
            if (bitmap == null) throw new IllegalArgumentException("PNG decode failed");
            return bitmap;
        });
    }

    private void submitRaw(Command command, byte[] data) {
        // An uncompressed payload's size is checked here so real clients get the mismatch answer
        // synchronously; a compressed payload's size is only knowable after inflation on the worker.
        if (command.compression == 0
            && data.length != (long) command.width * command.height * (command.format / 8)) {
            reply(command, "EINVAL:pixel data does not match s and v", true, false);
            return;
        }
        submitTransmission(command, data.length, command.width, command.height, () -> {
            byte[] pixels = command.compression == 'z'
                ? inflate(data, (long) command.width * command.height * (command.format / 8))
                : data;
            return Bitmap.createBitmap(
                rawPixelsToArgb(pixels, command.width, command.height, command.format),
                command.width, command.height, Bitmap.Config.ARGB_8888);
        });
    }

    /**
     * A completed transmission. Fixes the effective image id and store reservation synchronously —
     * which is what lets an {@code a=p} later in the stream resolve this image before its pixels
     * have decoded — then routes to store-only ({@code a=t}) or display ({@code a=T}).
     */
    private void submitTransmission(Command command, int transmittedBytes, int sourceWidth,
                                    int sourceHeight, BitmapProducer producer) {
        long pixelBytes = (long) sourceWidth * sourceHeight * 4L;
        if (pixelBytes <= 0 || pixelBytes > MAX_DECODED_BYTES) {
            reply(command, "ENOSPC:decoded image exceeds session limit", true, false);
            return;
        }
        boolean storeRequested = command.imageId != 0 || command.number != 0;
        long effectiveId = command.imageId;
        KittyImageStore.VirtualPlacement virtualPlacement = null;
        if (command.placeholder == 1) {
            if (!storeRequested) {
                reply(command, "EINVAL:unicode placement requires i or I", true, true);
                return;
            }
            virtualPlacement = createVirtualPlacement(command, sourceWidth, sourceHeight);
            if (virtualPlacement == null) {
                reply(command, "EINVAL:invalid unicode placement", true, false);
                return;
            }
        }
        if (command.action == 't' && !storeRequested) {
            reply(command, "EINVAL:storing an image requires i or I", true, true);
            return;
        }
        if (storeRequested) {
            if (effectiveId == 0) effectiveId = store.assignFreeId();
            int estimate = (int) pixelBytes;
            if (store.wouldExceedLimits(effectiveId, estimate)) {
                if (command.action == 't' || command.placeholder == 1) {
                    reply(command, "ENOSPC:image store is full", true, false, effectiveId);
                    return;
                }
                // The display command still displays; only the stored copy is skipped, so a later
                // a=p for this id answers ENOENT exactly as if the image had been evicted.
                storeRequested = false;
            } else {
                if (command.action == 't' || command.placeholder == 1) {
                    // Retransmission replaces the image, and its placements go with it.
                    emulator.deleteKittyImageEverywhere(effectiveId);
                }
                store.reserve(effectiveId, command.number, sourceWidth, sourceHeight, estimate);
                if (virtualPlacement != null)
                    store.putVirtualPlacement(store.get(effectiveId), virtualPlacement);
            }
        }
        if (command.action == 't' || command.placeholder == 1) {
            submitStoreOnly(command, transmittedBytes, effectiveId, producer);
        } else {
            submitDecode(command, transmittedBytes, effectiveId, storeRequested, sourceWidth, sourceHeight,
                producer);
        }
    }

    /** Store-only transmission: decode off-thread, attach to the reservation, no placement. */
    private void submitStoreOnly(Command command, int transmittedBytes, long effectiveId,
                                 BitmapProducer producer) {
        if (decodeBytesInFlight + transmittedBytes > MAX_TRANSMITTED_BYTES) {
            store.abandon(effectiveId);
            reply(command, "ENOSPC:too much image data pending", true, false, effectiveId);
            return;
        }
        decodeBytesInFlight += transmittedBytes;
        PNG_DECODER.execute(() -> {
            Bitmap bitmap = null;
            String error = null;
            try {
                bitmap = producer.produce();
                if (bitmap == null) {
                    error = "EINVAL:image decode failed";
                } else if (bitmap.getAllocationByteCount() > MAX_DECODED_BYTES) {
                    bitmap.recycle();
                    bitmap = null;
                    error = "ENOSPC:decoded image exceeds session limit";
                }
            } catch (IllegalArgumentException e) {
                error = "EINVAL:" + printable(e.getMessage());
                if (bitmap != null) bitmap.recycle();
                bitmap = null;
            } catch (RuntimeException | OutOfMemoryError e) {
                error = "ENOMEM:image decode failed";
                if (bitmap != null) bitmap.recycle();
                bitmap = null;
            }
            final Bitmap result = bitmap;
            final String decodeError = error;
            output.postTerminalUpdate(() -> {
                decodeBytesInFlight = Math.max(0, decodeBytesInFlight - transmittedBytes);
                if (decodeError != null) {
                    store.abandon(effectiveId);
                    reply(command, decodeError, true, false, effectiveId);
                    return;
                }
                // A delete that raced the decode wins; the transmission still succeeded.
                if (!store.complete(effectiveId, result, result.getAllocationByteCount()))
                    result.recycle();
                reply(command, "OK", false, false, effectiveId);
            });
        });
    }

    /**
     * The shared display tail: bound checks, cursor advance, then decode and scale off-thread. The
     * producer returns the unscaled bitmap or throws {@link IllegalArgumentException} with the reply
     * text after the {@code EINVAL:} prefix. When {@code storeRequested} is set the full-size decode
     * is also attached to this image's store reservation, and the placed bitmap is guaranteed to be
     * a different instance so placement recycling can never corrupt the store.
     */
    private void submitDecode(Command command, int transmittedBytes, long effectiveId,
                              boolean storeRequested, int sourceWidth, int sourceHeight,
                              BitmapProducer producer) {
        if (decodeBytesInFlight + transmittedBytes > MAX_TRANSMITTED_BYTES) {
            if (storeRequested) store.abandon(effectiveId);
            reply(command, "ENOSPC:too much image data pending", true, false, effectiveId);
            return;
        }
        final long acceptedGeneration = generation;
        final int row = emulator.getCursorRow();
        final int col = emulator.getCursorCol();
        final int cellWidth = Math.max(1, emulator.getCellWidthPixels());
        final int cellHeight = Math.max(1, emulator.getCellHeightPixels());
        if (command.cellOffsetX < 0 || command.cellOffsetX >= cellWidth
            || command.cellOffsetY < 0 || command.cellOffsetY >= cellHeight) {
            if (storeRequested) store.abandon(effectiveId);
            reply(command, "EINVAL:cell offset outside cell", true, false, effectiveId);
            return;
        }
        final int offsetX = command.cellOffsetX;
        final int offsetY = command.cellOffsetY;
        final int[] displaySize = displaySize(command, sourceWidth, sourceHeight, cellWidth, cellHeight);
        if ((long) (displaySize[0] + offsetX) * (displaySize[1] + offsetY) * 4L > MAX_DECODED_BYTES) {
            if (storeRequested) store.abandon(effectiveId);
            reply(command, "ENOSPC:display image exceeds session limit", true, false, effectiveId);
            return;
        }
        decodeBytesInFlight += transmittedBytes;
        // The cursor advance below linefeeds over the image's rows right now, scrolling the screen
        // when the cursor sits near the bottom, while the placement lands only after the decode. The
        // captured row is a screen row, so it must drop by however many lines scroll in between —
        // otherwise the image lands that many rows below its anchor, leaving blank lines above it.
        final long scrollsAtSubmit = emulator.scrollEventCount();
        emulator.advanceKittyGraphicsCursor(command, displaySize[0] + offsetX, displaySize[1] + offsetY,
            row, col, cellWidth, cellHeight);
        final boolean storeCopy = storeRequested;

        PNG_DECODER.execute(() -> {
            Bitmap display = null;
            Bitmap original = null;
            String error = null;
            try {
                original = producer.produce();
                display = original;
                if (display == null) {
                    error = "EINVAL:image decode failed";
                } else if (display.getAllocationByteCount() > MAX_DECODED_BYTES) {
                    display.recycle();
                    display = null;
                    original = null;
                    error = "ENOSPC:decoded image exceeds session limit";
                } else {
                    if (display.getWidth() != displaySize[0] || display.getHeight() != displaySize[1]) {
                        Bitmap scaled = Bitmap.createScaledBitmap(display, displaySize[0], displaySize[1], true);
                        if (scaled != display && !storeCopy) display.recycle();
                        display = scaled;
                    }
                    if (offsetX > 0 || offsetY > 0)
                        display = offsetComposite(display, offsetX, offsetY, storeCopy ? original : null);
                    if (storeCopy && display == original) {
                        // The store keeps the original; the placement layer owns and may recycle
                        // its bitmap, so the two must never share an instance.
                        display = original.copy(Bitmap.Config.ARGB_8888, false);
                        if (display == null) throw new OutOfMemoryError("bitmap copy failed");
                    }
                }
            } catch (IllegalArgumentException e) {
                error = "EINVAL:" + printable(e.getMessage());
                if (display != null && display != original) display.recycle();
                if (original != null) original.recycle();
                display = null;
                original = null;
            } catch (RuntimeException | OutOfMemoryError e) {
                error = "ENOMEM:image decode failed";
                if (display != null && display != original) display.recycle();
                if (original != null) original.recycle();
                display = null;
                original = null;
            }
            final Bitmap result = display;
            final Bitmap decoded = original;
            final String decodeError = error;
            output.postTerminalUpdate(() -> {
                decodeBytesInFlight = Math.max(0, decodeBytesInFlight - transmittedBytes);
                if (decodeError != null) {
                    if (storeCopy) store.abandon(effectiveId);
                    if (acceptedGeneration == generation)
                        reply(command, decodeError, true, false, effectiveId);
                    return;
                }
                if (storeCopy) {
                    // Completed regardless of screen generation: the store is not screen state.
                    if (!store.complete(effectiveId, decoded, decoded.getAllocationByteCount())
                        && decoded != result) {
                        decoded.recycle();
                    }
                }
                if (acceptedGeneration != generation) {
                    result.recycle();
                    return;
                }
                int[] transform = storeCopy
                    ? new int[] { 0, 0, sourceWidth, sourceHeight, displaySize[0], displaySize[1], offsetX, offsetY }
                    : null;
                int anchorRow = row - (int) (emulator.scrollEventCount() - scrollsAtSubmit);
                if (!emulator.placeKittyGraphics(result, command, effectiveId, anchorRow, col, cellWidth, cellHeight, transform)) {
                    result.recycle();
                    reply(command, "EINVAL:image placement failed", true, false, effectiveId);
                    return;
                }
                markPlaced(effectiveId);
                reply(command, "OK", false, false, effectiveId);
            });
        });
    }

    /**
     * A placement of a stored image ({@code a=p}): crop, scale, offset, and stamp. Resolution and
     * cursor movement are synchronous against the store's reservation metadata; the pixel work is
     * bounced through the decode worker and back so it runs strictly after the referenced
     * transmission's own completion, which is what makes back-to-back {@code a=t} then {@code a=p}
     * from real clients safe with an asynchronous decoder.
     */
    private void handlePlacement(Command command) {
        final long id = store.resolveId(command.imageId, command.number);
        KittyImageStore.Entry entry = id == 0 ? null : store.get(id);
        if (entry == null) {
            reply(command, "ENOENT:image not found", true, false);
            return;
        }
        if (command.placeholder == 1) {
            KittyImageStore.VirtualPlacement placement = createVirtualPlacement(command,
                entry.width, entry.height);
            if (placement == null) {
                reply(command, "EINVAL:invalid unicode placement", true, false, id);
                return;
            }
            store.putVirtualPlacement(entry, placement);
            markPlaced(id);
            reply(command, "OK", false, false, id);
            return;
        } else if (command.placeholder != 0) {
            reply(command, "EINVAL:unsupported unicode placeholder mode", true, false, id);
            return;
        }
        final int[] crop = computeCrop(entry.width, entry.height,
            command.srcX, command.srcY, command.srcW, command.srcH);
        if (crop == null) {
            reply(command, "EINVAL:invalid source rectangle", true, false, id);
            return;
        }
        final int cellWidth = Math.max(1, emulator.getCellWidthPixels());
        final int cellHeight = Math.max(1, emulator.getCellHeightPixels());
        if (command.cellOffsetX < 0 || command.cellOffsetX >= cellWidth
            || command.cellOffsetY < 0 || command.cellOffsetY >= cellHeight) {
            reply(command, "EINVAL:cell offset outside cell", true, false, id);
            return;
        }
        final int offsetX = command.cellOffsetX;
        final int offsetY = command.cellOffsetY;
        final int[] displaySize = displaySize(command, crop[2], crop[3], cellWidth, cellHeight);
        if ((long) (displaySize[0] + offsetX) * (displaySize[1] + offsetY) * 4L > MAX_DECODED_BYTES) {
            reply(command, "ENOSPC:display image exceeds session limit", true, false, id);
            return;
        }
        final long acceptedGeneration = generation;
        final int row = emulator.getCursorRow();
        final int col = emulator.getCursorCol();
        // Same anchor discipline as submitDecode: the advance may scroll now, the placement lands
        // later, and the captured screen row has to follow the content it was captured against.
        final long scrollsAtSubmit = emulator.scrollEventCount();
        emulator.advanceKittyGraphicsCursor(command, displaySize[0] + offsetX, displaySize[1] + offsetY,
            row, col, cellWidth, cellHeight);

        PNG_DECODER.execute(() -> output.postTerminalUpdate(() -> {
            KittyImageStore.Entry ready = store.get(id);
            if (ready == null || ready.bitmap == null) {
                if (acceptedGeneration == generation)
                    reply(command, "ENOENT:image not found", true, false, id);
                return;
            }
            // An animated image places its current frame, so a client-driven animation's new
            // placements agree with what a=a,c=N selected; a still-decoding frame falls back to root.
            Bitmap currentFrame = KittyImageStore.frameBitmap(ready, ready.currentFrame + 1);
            final Bitmap source = currentFrame != null ? currentFrame : ready.bitmap;
            PNG_DECODER.execute(() -> {
                Bitmap placed = null;
                String error = null;
                try {
                    Bitmap cropped = Bitmap.createBitmap(source, crop[0], crop[1], crop[2], crop[3]);
                    if (cropped.getWidth() != displaySize[0] || cropped.getHeight() != displaySize[1]) {
                        Bitmap scaled = Bitmap.createScaledBitmap(cropped, displaySize[0], displaySize[1], true);
                        if (scaled != cropped && cropped != source) cropped.recycle();
                        cropped = scaled;
                    }
                    if (offsetX > 0 || offsetY > 0)
                        cropped = offsetComposite(cropped, offsetX, offsetY, source);
                    // The placement layer owns and may recycle its bitmap, so it must never share
                    // an instance with the store.
                    placed = cropped == source ? source.copy(Bitmap.Config.ARGB_8888, false) : cropped;
                    if (placed == null) throw new OutOfMemoryError("bitmap copy failed");
                } catch (IllegalArgumentException e) {
                    error = "EINVAL:" + printable(e.getMessage());
                    if (placed != null && placed != source) placed.recycle();
                    placed = null;
                } catch (RuntimeException | OutOfMemoryError e) {
                    error = "ENOMEM:image rasterization failed";
                    if (placed != null && placed != source) placed.recycle();
                    placed = null;
                }
                final Bitmap result = placed;
                final String rasterError = error;
                output.postTerminalUpdate(() -> {
                    if (acceptedGeneration != generation) {
                        if (result != null) result.recycle();
                        return;
                    }
                    if (rasterError != null) {
                        reply(command, rasterError, true, false, id);
                        return;
                    }
                    int[] transform = new int[] { crop[0], crop[1], crop[2], crop[3],
                        displaySize[0], displaySize[1], offsetX, offsetY };
                    int anchorRow = row - (int) (emulator.scrollEventCount() - scrollsAtSubmit);
                    if (!emulator.placeKittyGraphics(result, command, id, anchorRow, col, cellWidth, cellHeight, transform)) {
                        result.recycle();
                        reply(command, "EINVAL:image placement failed", true, false, id);
                        return;
                    }
                    markPlaced(id);
                    reply(command, "OK", false, false, id);
                });
            });
        }));
    }

    private static KittyImageStore.VirtualPlacement createVirtualPlacement(Command command,
                                                                            int imageWidth,
                                                                            int imageHeight) {
        int[] crop = computeCrop(imageWidth, imageHeight, command.srcX, command.srcY,
            command.srcW, command.srcH);
        if (crop == null || command.displayColumns < 0 || command.displayRows < 0) return null;
        return new KittyImageStore.VirtualPlacement(command.placementId, crop[0], crop[1],
            crop[2], crop[3], command.displayColumns, command.displayRows);
    }

    /** Fill a reusable renderer result for a Unicode-placeholder cell. */
    boolean getPlaceholder(long imageId, long placementId, KittyImagePlaceholder out) {
        KittyImageStore.Entry entry = store.get(imageId);
        if (entry == null || out == null) return false;
        KittyImageStore.VirtualPlacement placement = KittyImageStore.virtualPlacement(entry, placementId);
        Bitmap bitmap = KittyImageStore.frameBitmap(entry, entry.currentFrame + 1);
        if (placement == null || bitmap == null) return false;
        out.bitmap = bitmap;
        out.sourceX = placement.sourceX;
        out.sourceY = placement.sourceY;
        out.sourceWidth = placement.sourceWidth;
        out.sourceHeight = placement.sourceHeight;
        out.columns = placement.columns;
        out.rows = placement.rows;
        return true;
    }

    boolean hasVirtualPlacement(long imageId, long placementId) {
        KittyImageStore.Entry entry = store.get(imageId);
        return entry != null && KittyImageStore.virtualPlacement(entry, placementId) != null;
    }

    /**
     * Frame data transmission ({@code a=f}): decode off-thread, resolve the base canvas on the
     * update thread strictly after earlier decodes have landed, compose off-thread, commit. The
     * canvas is a previous frame's pixels ({@code c=}), the frame being edited ({@code r=}), or a
     * solid {@code Y=} colour; {@code X=1} replaces instead of alpha blending, and {@code z} is
     * the frame gap (positive milliseconds, negative gapless, absent defaults to 40 ms).
     */
    private void submitFrame(Command command, byte[] data) {
        final long id = store.resolveId(command.imageId, command.number);
        KittyImageStore.Entry initial = id == 0 ? null : store.get(id);
        if (initial == null) {
            reply(command, "ENOENT:image not found", true, false);
            return;
        }
        // r selects an existing frame to edit; anything else appends a new frame, as kitty does.
        final int frameCountAtAccept = KittyImageStore.frameCount(initial);
        final int editNumber = (command.displayRows >= 1 && command.displayRows <= frameCountAtAccept)
            ? command.displayRows : 0;
        final int estimate = (int) Math.min(Integer.MAX_VALUE, (long) initial.width * initial.height * 4L);
        if (editNumber == 0 && store.wouldExceedFrameLimits(initial, estimate)
            && !store.reclaimFrameBudget(initial, estimate,
                imageId -> !emulator.kittyPlacementsFor(imageId).isEmpty())) {
            reply(command, "ENOSPC:frame store is full", true, false, id);
            return;
        }
        if (decodeBytesInFlight + data.length > MAX_TRANSMITTED_BYTES) {
            reply(command, "ENOSPC:too much image data pending", true, false, id);
            return;
        }
        // The gate above passed, so this frame now owns that slice of the quota until it commits
        // or fails. Every return below releases it exactly once; an edit replaces pixels in place
        // and so costs nothing new.
        final boolean reservedFrameBytes = editNumber == 0;
        if (reservedFrameBytes) store.reserveFrameBytes(initial, estimate);
        final int transmittedBytes = data.length;
        decodeBytesInFlight += transmittedBytes;

        PNG_DECODER.execute(() -> {
            int[] pixels = null;
            int pixelsWidth = 0;
            int pixelsHeight = 0;
            String error = null;
            try {
                if (command.format == 100) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    if (decoded == null) throw new IllegalArgumentException("PNG decode failed");
                    pixelsWidth = decoded.getWidth();
                    pixelsHeight = decoded.getHeight();
                    pixels = new int[pixelsWidth * pixelsHeight];
                    decoded.getPixels(pixels, 0, pixelsWidth, 0, 0, pixelsWidth, pixelsHeight);
                    decoded.recycle();
                } else {
                    byte[] raw = command.compression == 'z'
                        ? inflate(data, (long) command.width * command.height * (command.format / 8))
                        : data;
                    pixels = rawPixelsToArgb(raw, command.width, command.height, command.format);
                    pixelsWidth = command.width;
                    pixelsHeight = command.height;
                }
            } catch (IllegalArgumentException e) {
                error = "EINVAL:" + printable(e.getMessage());
            } catch (RuntimeException | OutOfMemoryError e) {
                error = "ENOMEM:frame decode failed";
            }
            final int[] framePixels = pixels;
            final int frameWidth = pixelsWidth;
            final int frameHeight = pixelsHeight;
            final String decodeError = error;
            output.postTerminalUpdate(() -> {
                decodeBytesInFlight = Math.max(0, decodeBytesInFlight - transmittedBytes);
                KittyImageStore.Entry entry = store.get(id);
                if (decodeError != null || entry == null) {
                    if (reservedFrameBytes) store.releaseFrameBytes(initial, estimate);
                    reply(command, decodeError != null ? decodeError : "ENOENT:image not found",
                        true, false, id);
                    return;
                }
                if (!frameRectFits(entry, command.srcX, command.srcY, frameWidth, frameHeight)) {
                    if (reservedFrameBytes) store.releaseFrameBytes(initial, estimate);
                    reply(command, "EINVAL:frame rectangle out of bounds", true, false, id);
                    return;
                }
                int frameCount = KittyImageStore.frameCount(entry);
                final int targetNumber = (editNumber >= 1 && editNumber <= frameCount) ? editNumber : 0;
                Bitmap base = null;
                if (targetNumber != 0) {
                    base = KittyImageStore.frameBitmap(entry, targetNumber);
                } else if (command.displayColumns >= 1 && command.displayColumns <= frameCount) {
                    base = KittyImageStore.frameBitmap(entry, command.displayColumns);
                }
                if ((targetNumber != 0 || (command.displayColumns >= 1 && command.displayColumns <= frameCount))
                    && base == null) {
                    if (reservedFrameBytes) store.releaseFrameBytes(initial, estimate);
                    reply(command, "ENOENT:base frame data unavailable", true, false, id);
                    return;
                }
                final Bitmap canvasSource = base;
                final long backgroundColor = command.values.containsKey('Y')
                    ? Command.unsigned(command.values, 'Y') : 0;
                final int imageWidth = entry.width;
                final int imageHeight = entry.height;
                PNG_DECODER.execute(() -> {
                    Bitmap composed = null;
                    String composeError = null;
                    try {
                        int[] canvas = new int[imageWidth * imageHeight];
                        if (canvasSource != null) {
                            canvasSource.getPixels(canvas, 0, imageWidth, 0, 0, imageWidth, imageHeight);
                        } else if (backgroundColor != 0) {
                            // The protocol's Y is 32-bit RGBA; ARGB ints are what Bitmap takes.
                            int rgba = (int) backgroundColor;
                            int argb = (rgba << 24) | (rgba >>> 8);
                            java.util.Arrays.fill(canvas, argb);
                        }
                        composeRegion(canvas, imageWidth, framePixels, frameWidth,
                            frameWidth, frameHeight, 0, 0, command.srcX, command.srcY,
                            command.cellOffsetX == 1);
                        composed = Bitmap.createBitmap(canvas, imageWidth, imageHeight,
                            Bitmap.Config.ARGB_8888);
                        if (composed == null) throw new OutOfMemoryError("frame compose failed");
                    } catch (IllegalArgumentException e) {
                        composeError = "EINVAL:" + printable(e.getMessage());
                    } catch (RuntimeException | OutOfMemoryError e) {
                        composeError = "ENOMEM:frame compose failed";
                    }
                    final Bitmap frameBitmap = composed;
                    final String finalError = composeError;
                    output.postTerminalUpdate(() -> {
                        // From here the frame either takes its bytes for real or takes none, so
                        // the reservation has done its job either way.
                        if (reservedFrameBytes) store.releaseFrameBytes(initial, estimate);
                        KittyImageStore.Entry live = store.get(id);
                        if (finalError != null || live != entry) {
                            reply(command, finalError != null ? finalError : "ENOENT:image not found",
                                true, false, id);
                            return;
                        }
                        int byteCount = imageWidth * imageHeight * 4;
                        if (targetNumber != 0) {
                            store.replaceFrameBitmap(entry, targetNumber, frameBitmap, byteCount);
                            if (command.z != 0)
                                KittyImageStore.setFrameGap(entry, targetNumber, Math.max(0, command.z));
                            if (targetNumber == entry.currentFrame + 1) renderAnimationFrame(entry);
                        } else {
                            int gap = command.z > 0 ? command.z
                                : command.z < 0 ? 0 : KittyImageStore.DEFAULT_FRAME_GAP_MS;
                            store.addFrame(entry, frameBitmap, byteCount, gap);
                        }
                        reply(command, "OK", false, false, id);
                        scheduleAnimationTick();
                    });
                });
            });
        });
    }

    /**
     * Animation control ({@code a=a}): all synchronous state. Matching kitty, a successful
     * control produces no reply; only a missing image answers.
     */
    private void handleAnimationControl(Command command) {
        KittyImageStore.Entry entry = store.get(store.resolveId(command.imageId, command.number));
        if (entry == null) {
            reply(command, "ENOENT:image not found", true, false);
            return;
        }
        int frameCount = KittyImageStore.frameCount(entry);
        // r + z set one frame's gap; out-of-range frame numbers are ignored, as kitty ignores them.
        if (command.displayRows >= 1 && command.displayRows <= frameCount && command.z != 0)
            KittyImageStore.setFrameGap(entry, command.displayRows, Math.max(0, command.z));
        // c makes a frame current — the client-driven animation primitive.
        if (command.displayColumns >= 1 && command.displayColumns <= frameCount
            && command.displayColumns - 1 != entry.currentFrame) {
            entry.currentFrame = command.displayColumns - 1;
            entry.frameShownAtUptime = SystemClock.uptimeMillis();
            renderAnimationFrame(entry);
        }
        String state = command.values.get('s');
        if (state != null) {
            int previous = entry.animationState;
            int requested = Command.integer(command.values, 's', 0);
            if (requested >= 1 && requested <= 3) entry.animationState = requested;
            if (entry.animationState != KittyImageStore.ANIMATION_STOPPED
                && previous != entry.animationState) {
                entry.frameShownAtUptime = SystemClock.uptimeMillis();
            }
            entry.currentLoop = 0;
        }
        // v=1 loops forever; any larger v runs v-1 loops, kitty's exact reading.
        int loops = Command.integer(command.values, 'v', 0);
        if (loops > 0) entry.maxLoops = loops - 1;
        scheduleAnimationTick();
    }

    /** Frame composition ({@code a=c}): copy or blend a rectangle from one frame onto another. */
    private void handleCompose(Command command) {
        final long id = store.resolveId(command.imageId, command.number);
        final KittyImageStore.Entry entry = id == 0 ? null : store.get(id);
        if (entry == null) {
            reply(command, "ENOENT:image not found", true, false);
            return;
        }
        int frameCount = KittyImageStore.frameCount(entry);
        // r is the source frame and c the destination; x,y offset the destination rectangle and
        // X,Y the source one — kitty's implementation, which its own spec prose has backwards.
        final int sourceNumber = command.displayRows;
        final int destinationNumber = command.displayColumns;
        if (sourceNumber < 1 || sourceNumber > frameCount
            || destinationNumber < 1 || destinationNumber > frameCount) {
            reply(command, "ENOENT:no such frame", true, false, id);
            return;
        }
        final int width = command.srcW > 0 ? command.srcW : entry.width;
        final int height = command.srcH > 0 ? command.srcH : entry.height;
        final int srcX = command.cellOffsetX;
        final int srcY = command.cellOffsetY;
        final int dstX = command.srcX;
        final int dstY = command.srcY;
        if (!frameRectFits(entry, srcX, srcY, width, height)
            || !frameRectFits(entry, dstX, dstY, width, height)) {
            reply(command, "EINVAL:rectangle out of bounds", true, false, id);
            return;
        }
        if (sourceNumber == destinationNumber
            && Math.max(srcX, dstX) < Math.min(srcX, dstX) + width
            && Math.max(srcY, dstY) < Math.min(srcY, dstY) + height) {
            reply(command, "EINVAL:source and destination rectangles overlap", true, false, id);
            return;
        }
        final boolean replace = command.noCursorMovement; // C=1 means replace in a=c
        final int imageWidth = entry.width;
        final int imageHeight = entry.height;

        PNG_DECODER.execute(() -> output.postTerminalUpdate(() -> {
            KittyImageStore.Entry live = store.get(id);
            if (live != entry) {
                reply(command, "ENOENT:image not found", true, false, id);
                return;
            }
            final Bitmap source = KittyImageStore.frameBitmap(entry, sourceNumber);
            final Bitmap destination = KittyImageStore.frameBitmap(entry, destinationNumber);
            if (source == null || destination == null) {
                reply(command, "EINVAL:frame data unavailable", true, false, id);
                return;
            }
            PNG_DECODER.execute(() -> {
                Bitmap composed = null;
                String error = null;
                try {
                    int[] under = new int[imageWidth * imageHeight];
                    destination.getPixels(under, 0, imageWidth, 0, 0, imageWidth, imageHeight);
                    int[] over = new int[imageWidth * imageHeight];
                    source.getPixels(over, 0, imageWidth, 0, 0, imageWidth, imageHeight);
                    composeRegion(under, imageWidth, over, imageWidth,
                        width, height, srcX, srcY, dstX, dstY, replace);
                    composed = Bitmap.createBitmap(under, imageWidth, imageHeight,
                        Bitmap.Config.ARGB_8888);
                    if (composed == null) throw new OutOfMemoryError("compose failed");
                } catch (RuntimeException | OutOfMemoryError e) {
                    error = "ENOMEM:frame compose failed";
                }
                final Bitmap result = composed;
                final String composeError = error;
                output.postTerminalUpdate(() -> {
                    KittyImageStore.Entry still = store.get(id);
                    if (composeError != null || still != entry) {
                        reply(command, composeError != null ? composeError : "ENOENT:image not found",
                            true, false, id);
                        return;
                    }
                    store.replaceFrameBitmap(entry, destinationNumber, result,
                        imageWidth * imageHeight * 4);
                    if (destinationNumber == entry.currentFrame + 1) renderAnimationFrame(entry);
                    reply(command, "OK", false, false, id);
                });
            });
        }));
    }

    // ------------------------------------------------------------------ terminal-driven playback

    private static final Paint FRAME_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
    private boolean animationTickScheduled;
    /**
     * Whether this terminal is on screen. Playback is suspended, never discarded, while it is not:
     * an animation that scrolled out of view or whose pane went away keeps every frame and its
     * place in them, and picks up where it would have been when it comes back.
     */
    private boolean animationsVisible = true;
    /**
     * One runnable for the life of the protocol. It is the token the scheduled tick is posted
     * under, so the tick can be withdrawn — and re-posting a new lambda every frame allocated a
     * pair of objects per tick for nothing.
     */
    private final Runnable animationTickRunnable = this::animationTick;

    /**
     * Report whether this terminal's output can be seen. Suspending stops the tick outright: the
     * scheduler has no notion of what is on display, so an off-screen animation otherwise keeps
     * flipping frames — and compositing them — for as long as the process lives.
     */
    void setAnimationsVisible(boolean visible) {
        if (animationsVisible == visible) return;
        animationsVisible = visible;
        if (visible) {
            resumeAnimations();
        } else {
            cancelAnimationTick();
        }
    }

    private void cancelAnimationTick() {
        if (!animationTickScheduled) return;
        animationTickScheduled = false;
        output.cancelTerminalUpdateDelayed(animationTickRunnable);
    }

    /** Fast-forward every animation to where it would have been, composite once, and resume. */
    private void resumeAnimations() {
        long now = SystemClock.uptimeMillis();
        for (KittyImageStore.Entry entry : store.entries()) {
            if (KittyImageStore.catchUpAnimation(entry, now)) renderAnimationFrame(entry);
        }
        scheduleAnimationTick();
    }

    /** Arm one delayed tick for the earliest due frame across all running animations. */
    private void scheduleAnimationTick() {
        if (animationTickScheduled || !animationsVisible) return;
        long earliest = Long.MAX_VALUE;
        for (KittyImageStore.Entry entry : store.entries()) {
            long deadline = KittyImageStore.nextAnimationDeadline(entry);
            if (deadline >= 0 && deadline < earliest) earliest = deadline;
        }
        if (earliest == Long.MAX_VALUE) return;
        long delay = Math.min(60_000, Math.max(1, earliest - SystemClock.uptimeMillis()));
        animationTickScheduled = true;
        output.postTerminalUpdateDelayed(animationTickRunnable, delay);
    }

    private void animationTick() {
        animationTickScheduled = false;
        long now = SystemClock.uptimeMillis();
        for (KittyImageStore.Entry entry : store.entries()) {
            // The frame index advances whether or not anything can see it — that is what keeps a
            // scrolled-away animation in step, and keeps the scheduler from finding a deadline
            // permanently in the past and spinning on it. Only the composite, which is the
            // expensive half, waits until there is a cell on screen to composite into.
            if (KittyImageStore.advanceAnimation(entry, now) && emulator.isKittyImageOnScreen(entry.id))
                renderAnimationFrame(entry);
        }
        scheduleAnimationTick();
    }

    /**
     * Release everything this protocol holds when its session is over: the pending tick first,
     * because it is posted on the main looper and reaches the whole terminal through this object,
     * then the stored pixels. Nothing else can be reading them once the session is finished, so
     * the bitmaps are recycled rather than left to the collector.
     */
    void shutdown() {
        cancelAnimationTick();
        upload = null;
        for (KittyImageStore.Entry entry : store.entries()) {
            if (entry.bitmap != null && !entry.bitmap.isRecycled()) entry.bitmap.recycle();
            for (KittyImageStore.Frame frame : entry.frames) {
                if (frame.bitmap != null && !frame.bitmap.isRecycled()) frame.bitmap.recycle();
            }
        }
        store.clear();
    }

    /**
     * Drop the frames of every animation nothing can display any more, keeping the root image so a
     * later {@code a=p} still works — kitty's behaviour, and what makes a {@code clear} actually
     * hand the memory back. Only images that have been placed at least once are in scope: one that
     * is still being transmitted has no placement yet and must keep the frames it is collecting.
     */
    void dropFramesOfUnreachableImages() {
        for (KittyImageStore.Entry entry : store.entries()) {
            if (entry.frames.isEmpty() || !entry.everPlaced) continue;
            if (!entry.virtualPlacements.isEmpty()) continue;
            if (!emulator.kittyPlacementsFor(entry.id).isEmpty()) continue;
            store.dropFrames(entry);
        }
        cancelAnimationTickIfNothingAnimates();
    }

    private void markPlaced(long imageId) {
        KittyImageStore.Entry entry = store.get(imageId);
        if (entry != null) entry.everPlaced = true;
    }

    private void cancelAnimationTickIfNothingAnimates() {
        for (KittyImageStore.Entry entry : store.entries()) {
            if (KittyImageStore.nextAnimationDeadline(entry) >= 0) return;
        }
        cancelAnimationTick();
    }

    /**
     * Re-render every placement of this image from its current frame. Each placement rotates two
     * buffers: the frame is drawn into the spare one off-thread with the placement's stored
     * crop/scale/offset transform, then swapped in as the displayed bitmap on the update thread —
     * cells are never restamped, so a flip cannot flicker, and steady-state playback allocates
     * nothing. Displaced immutable bitmaps are dropped to the garbage collector, never recycled,
     * because the render thread may still be uploading them.
     */
    private void renderAnimationFrame(KittyImageStore.Entry entry) {
        final Bitmap frame = KittyImageStore.frameBitmap(entry, entry.currentFrame + 1);
        if (frame == null) return;
        final List<TerminalBitmap> placements = emulator.kittyPlacementsFor(entry.id);
        if (placements.isEmpty()) return;
        final Bitmap[] buffers = new Bitmap[placements.size()];
        for (int i = 0; i < placements.size(); i++) {
            TerminalBitmap placement = placements.get(i);
            buffers[i] = placement.kittyBackBuffer;
            placement.kittyBackBuffer = null;
        }
        PNG_DECODER.execute(() -> {
            for (int i = 0; i < placements.size(); i++) {
                TerminalBitmap placement = placements.get(i);
                try {
                    int width = placement.bitmap.getWidth();
                    int height = placement.bitmap.getHeight();
                    Bitmap buffer = buffers[i];
                    if (buffer == null || !buffer.isMutable()
                        || buffer.getWidth() != width || buffer.getHeight() != height) {
                        buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    }
                    buffer.eraseColor(0);
                    int[] t = placement.kittyTransform;
                    Canvas canvas = new Canvas(buffer);
                    canvas.drawBitmap(frame, new Rect(t[0], t[1], t[0] + t[2], t[1] + t[3]),
                        new RectF(t[6], t[7], t[6] + t[4], t[7] + t[5]), FRAME_PAINT);
                    buffers[i] = buffer;
                } catch (RuntimeException | OutOfMemoryError e) {
                    // This placement keeps its previous frame; playback continues.
                    buffers[i] = null;
                }
            }
            output.postTerminalUpdate(() -> {
                for (int i = 0; i < placements.size(); i++) {
                    Bitmap fresh = buffers[i];
                    if (fresh == null) continue;
                    TerminalBitmap placement = placements.get(i);
                    Bitmap old = placement.bitmap;
                    placement.bitmap = fresh;
                    placement.kittyBackBuffer = (old != null && old.isMutable()) ? old : null;
                }
            });
        });
    }

    /**
     * Compose a {@code w*h} rectangle of {@code over} at source offset {@code (ox,oy)} onto
     * {@code under} at {@code (dx,dy)}. Both are row-major, non-premultiplied ARGB. Straight-alpha
     * source-over unless {@code replace}. Bounds must already be validated.
     */
    static void composeRegion(int[] under, int underWidth, int[] over, int overWidth,
                              int w, int h, int ox, int oy, int dx, int dy, boolean replace) {
        for (int row = 0; row < h; row++) {
            int src = (oy + row) * overWidth + ox;
            int dst = (dy + row) * underWidth + dx;
            for (int col = 0; col < w; col++, src++, dst++) {
                int overPixel = over[src];
                int overAlpha = overPixel >>> 24;
                if (replace || overAlpha == 0xff) {
                    under[dst] = overPixel;
                    continue;
                }
                if (overAlpha == 0) continue;
                int underPixel = under[dst];
                int underAlpha = underPixel >>> 24;
                int contribution = underAlpha * (255 - overAlpha) / 255;
                int outAlpha = overAlpha + contribution;
                if (outAlpha == 0) {
                    under[dst] = 0;
                    continue;
                }
                int r = (((overPixel >> 16) & 0xff) * overAlpha
                    + ((underPixel >> 16) & 0xff) * contribution) / outAlpha;
                int g = (((overPixel >> 8) & 0xff) * overAlpha
                    + ((underPixel >> 8) & 0xff) * contribution) / outAlpha;
                int b = ((overPixel & 0xff) * overAlpha
                    + (underPixel & 0xff) * contribution) / outAlpha;
                under[dst] = (outAlpha << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    /** The delete forms ({@code a=d}). An uppercase specifier also frees the targeted stored data. */
    private void handleDelete(Command command) {
        boolean free = Character.isUpperCase(command.deleteMode);
        char form = Character.toLowerCase(command.deleteMode);
        // The legacy fork behavior — and the least surprising reading for clients that send an id
        // with the default specifier — is that d=a plus an explicit i/I deletes only that image.
        if (form == 'a' && command.imageId != 0) form = 'i';
        else if (form == 'a' && command.number != 0) form = 'n';
        switch (form) {
            case 'a':
                emulator.deleteKittyPlacements((bitmap, column, row) -> true, true);
                if (free) store.removeImagesWithoutVirtualPlacements();
                break;
            case 'i':
            case 'n': {
                long id = form == 'n' ? store.resolveId(0, command.number)
                    : store.resolveId(command.imageId, command.number);
                if (form == 'n' && command.number == 0) {
                    reply(command, "EINVAL:delete by number requires I", true, true);
                    return;
                }
                if (form == 'i' && command.imageId == 0 && id == 0) {
                    reply(command, "EINVAL:delete by id requires i", true, true);
                    return;
                }
                if (id != 0) {
                    KittyImageStore.Entry entry = store.get(id);
                    if (entry != null)
                        KittyImageStore.removeVirtualPlacements(entry, command.placementId);
                    emulator.deleteKittyPlacements((bitmap, column, row) ->
                        bitmap.kittyImageId == id
                            && (command.placementId == 0 || bitmap.kittyPlacementId == command.placementId), true);
                    if (free && emulator.kittyPlacementsFor(id).isEmpty())
                        store.removeIfNoVirtualPlacements(id);
                }
                break;
            }
            case 'c':
                deleteAtCell(emulator.getCursorCol(), emulator.getCursorRow(), null, free);
                break;
            case 'f': {
                long id = store.resolveId(command.imageId, command.number);
                KittyImageStore.Entry entry = id == 0 ? null : store.get(id);
                if (entry == null) {
                    reply(command, "ENOENT:image not found", true, true);
                    return;
                }
                int before = entry.currentFrame;
                if (!store.removeFrame(entry, command.displayRows)) {
                    // No extra frames: d=F deletes the whole image, d=f is a no-op — kitty's rule.
                    if (free) {
                        emulator.deleteKittyPlacements((bitmap, column, row) ->
                            bitmap.kittyImageId == id, true);
                        store.removeIfNoVirtualPlacements(id);
                    }
                } else if (entry.currentFrame != before) {
                    renderAnimationFrame(entry);
                }
                break;
            }
            case 'p':
            case 'q': {
                if (!command.values.containsKey('x') || !command.values.containsKey('y')
                    || (form == 'q' && !command.values.containsKey('z'))) {
                    reply(command, "EINVAL:delete by position requires x and y", true, true);
                    return;
                }
                deleteAtCell(command.srcX - 1, command.srcY - 1, form == 'q' ? command.z : null, free);
                break;
            }
            case 'x': {
                if (!command.values.containsKey('x')) {
                    reply(command, "EINVAL:delete by column requires x", true, true);
                    return;
                }
                int column = command.srcX - 1;
                deleteMatching((bitmap, cellColumn, cellRow) -> cellColumn == column, false, free);
                break;
            }
            case 'y': {
                if (!command.values.containsKey('y')) {
                    reply(command, "EINVAL:delete by row requires y", true, true);
                    return;
                }
                int row = command.srcY - 1;
                deleteMatching((bitmap, cellColumn, cellRow) -> cellRow == row, false, free);
                break;
            }
            case 'z': {
                if (!command.values.containsKey('z')) {
                    reply(command, "EINVAL:delete by z requires z", true, true);
                    return;
                }
                deleteMatching((bitmap, cellColumn, cellRow) -> bitmap.kittyZ == command.z, true, free);
                break;
            }
            default:
                reply(command, "EINVAL:unknown delete specifier", true, true);
                return;
        }
        reply(command, "OK", false, false);
    }

    /** Delete placements whose cells intersect one screen cell, optionally also matching a z value. */
    private void deleteAtCell(int column, int row, Integer z, boolean free) {
        deleteMatching((bitmap, cellColumn, cellRow) ->
            cellColumn == column && cellRow == row && (z == null || bitmap.kittyZ == z), false, free);
    }

    /**
     * Delete every placement with at least one cell the filter matches — the whole placement goes,
     * not just the matched cells, which is what the intersection delete forms ask for. Two passes:
     * a scan that deletes nothing, then deletion by membership, because a placement's cells before
     * the matching one have already been visited when the match is found.
     */
    private void deleteMatching(TerminalBuffer.KittyPlacementFilter filter, boolean includeScrollback,
                                boolean free) {
        Set<TerminalBitmap> hits = new HashSet<>();
        emulator.deleteKittyPlacements((bitmap, cellColumn, cellRow) -> {
            if (filter.matches(bitmap, cellColumn, cellRow)) hits.add(bitmap);
            return false;
        }, includeScrollback);
        if (hits.isEmpty()) return;
        // Membership deletion always covers scrollback so a placement straddling the screen edge
        // does not leave orphan cells behind.
        emulator.deleteKittyPlacements((bitmap, cellColumn, cellRow) -> hits.contains(bitmap), true);
        if (free) {
            for (TerminalBitmap bitmap : hits)
                store.removeIfNoVirtualPlacements(bitmap.kittyImageId);
        }
    }

    void reset() {
        resetUploadAndDecodes();
        store.clear();
        emulator.deleteAllKittyGraphics();
    }

    void screenCleared() {
        resetUploadAndDecodes();
        emulator.deleteVisibleKittyGraphics();
    }

    private void resetUploadAndDecodes() {
        upload = null;
        generation++;
    }

    private void reply(Command command, String status, boolean error, boolean always) {
        reply(command, status, error, always, command == null ? 0 : command.imageId);
    }

    /**
     * The {@code imageId} may differ from the command's own when the terminal assigned one for an
     * {@code I=}-only transmission; the client learns the assignment from this reply.
     */
    private void reply(Command command, String status, boolean error, boolean always, long imageId) {
        int quiet = command == null ? 0 : command.quiet;
        // q=1 suppresses success, q=2 suppresses everything. The old check compared q to 1 and 2
        // exactly, so q=2 still emitted OK — which lands in the tty and, with no application reading
        // it, corrupts the shell's input line.
        if (!error && quiet >= 1) return;
        if (error && quiet >= 2) return;
        long number = command == null ? 0 : command.number;
        if (!always && imageId == 0 && number == 0) return;
        StringBuilder response = new StringBuilder("\033_G");
        if (imageId != 0)
            response.append("i=").append(Long.toUnsignedString(imageId));
        if (number != 0) {
            if (imageId != 0) response.append(',');
            response.append("I=").append(Long.toUnsignedString(number));
        }
        if (command != null && command.placementId != 0)
            response.append(",p=").append(Long.toUnsignedString(command.placementId));
        response.append(';').append(status).append("\033\\");
        output.write(response.toString());
    }

    /**
     * Clamp a kitty source rectangle against the stored image, returning {x, y, width, height} or
     * null when it selects nothing. Zero width or height means "to the edge".
     */
    static int[] computeCrop(int sourceWidth, int sourceHeight, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w < 0 || h < 0) return null;
        if (x >= sourceWidth || y >= sourceHeight) return null;
        int cropWidth = w == 0 ? sourceWidth - x : Math.min(w, sourceWidth - x);
        int cropHeight = h == 0 ? sourceHeight - y : Math.min(h, sourceHeight - y);
        if (cropWidth <= 0 || cropHeight <= 0) return null;
        return new int[] { x, y, cropWidth, cropHeight };
    }

    /**
     * Shift an image right and down by a sub-cell pixel offset, producing a bitmap whose top-left
     * corner is transparent padding. Recycles the input unless it is the protected instance.
     */
    private static Bitmap offsetComposite(Bitmap image, int offsetX, int offsetY, Bitmap protectedInstance) {
        Bitmap combined = Bitmap.createBitmap(image.getWidth() + offsetX, image.getHeight() + offsetY,
            Bitmap.Config.ARGB_8888);
        if (combined == null) throw new OutOfMemoryError("offset composite failed");
        Canvas canvas = new Canvas(combined);
        canvas.drawBitmap(image, offsetX, offsetY, null);
        if (image != protectedInstance) image.recycle();
        return combined;
    }

    private static int[] pngDimensions(byte[] png) {
        if (png.length < 24 || (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N'
            || png[3] != 'G' || png[4] != 13 || png[5] != 10 || png[6] != 26 || png[7] != 10
            || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return null;
        int width = readPositiveInt(png, 16);
        int height = readPositiveInt(png, 20);
        if (width <= 0 || height <= 0) return null;
        return new int[] { width, height };
    }

    /** Small strict decoder kept here so protocol tests do not depend on Android framework stubs. */
    private static byte[] decodeBase64(String encoded) {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(encoded.length() * 3 / 4);
        int accumulator = 0;
        int bits = 0;
        int useful = 0;
        boolean padding = false;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
            if (c == '=') {
                padding = true;
                continue;
            }
            if (padding) throw new IllegalArgumentException("data after base64 padding");
            int value = base64Value(c);
            if (value < 0) throw new IllegalArgumentException("invalid base64 character");
            accumulator = (accumulator << 6) | value;
            bits += 6;
            useful++;
            if (bits >= 8) {
                bits -= 8;
                decoded.write((accumulator >> bits) & 0xff);
            }
        }
        if ((useful & 3) == 1 || (bits > 0 && (accumulator & ((1 << bits) - 1)) != 0))
            throw new IllegalArgumentException("invalid base64 length");
        return decoded.toByteArray();
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }

    private static int[] displaySize(Command command, int sourceWidth, int sourceHeight,
                                     int cellWidth, int cellHeight) {
        long requestedWidth = command.displayColumns > 0 ? (long) command.displayColumns * cellWidth : 0;
        long requestedHeight = command.displayRows > 0 ? (long) command.displayRows * cellHeight : 0;
        if (requestedWidth > Integer.MAX_VALUE || requestedHeight > Integer.MAX_VALUE)
            return new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE };
        int width = (int) requestedWidth;
        int height = (int) requestedHeight;
        if (width <= 0 && height <= 0) return new int[] { sourceWidth, sourceHeight };
        if (width <= 0) width = Math.max(1, (int) Math.round((double) sourceWidth * height / sourceHeight));
        if (height <= 0) height = Math.max(1, (int) Math.round((double) sourceHeight * width / sourceWidth));
        return new int[] { width, height };
    }

    private static int readPositiveInt(byte[] value, int offset) {
        long result = ((long) (value[offset] & 0xff) << 24) | ((long) (value[offset + 1] & 0xff) << 16)
            | ((long) (value[offset + 2] & 0xff) << 8) | (value[offset + 3] & 0xffL);
        return result > Integer.MAX_VALUE ? -1 : (int) result;
    }

    private static String printable(String message) {
        if (message == null || message.isEmpty()) return "invalid control data";
        return message.replaceAll("[^ -~]", "?");
    }

    /**
     * Converts kitty raw pixel data (f=24 RGB or f=32 RGBA, straight alpha, row-major) into the
     * non-premultiplied ARGB ints {@code Bitmap.createBitmap(int[], ...)} takes.
     */
    static int[] rawPixelsToArgb(byte[] data, int width, int height, int format) {
        int bytesPerPixel = format / 8;
        if ((long) width * height * bytesPerPixel != data.length)
            throw new IllegalArgumentException("pixel data does not match s and v");
        int[] pixels = new int[width * height];
        int src = 0;
        for (int i = 0; i < pixels.length; i++) {
            int alpha = bytesPerPixel == 4 ? data[src + 3] & 0xff : 0xff;
            pixels[i] = (alpha << 24) | ((data[src] & 0xff) << 16)
                | ((data[src + 1] & 0xff) << 8) | (data[src + 2] & 0xff);
            src += bytesPerPixel;
        }
        return pixels;
    }

    /** Inflates o=z pixel data, requiring the stream to produce exactly {@code expectedBytes}. */
    static byte[] inflate(byte[] data, long expectedBytes) {
        if (expectedBytes <= 0 || expectedBytes > MAX_DECODED_BYTES)
            throw new IllegalArgumentException("compressed pixel data does not match s and v");
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        byte[] out = new byte[(int) expectedBytes];
        try {
            int total = 0;
            while (total < out.length) {
                int produced = inflater.inflate(out, total, out.length - total);
                total += produced;
                if (produced == 0) break;
            }
            if (total != out.length)
                throw new IllegalArgumentException("compressed pixel data does not match s and v");
            if (!inflater.finished()) {
                byte[] probe = new byte[1];
                if (inflater.inflate(probe) > 0 || !inflater.finished())
                    throw new IllegalArgumentException("compressed pixel data does not match s and v");
            }
            return out;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("invalid zlib pixel data");
        } finally {
            inflater.end();
        }
    }

    private interface BitmapProducer {
        Bitmap produce();
    }

    private static final class Upload {
        final Command command;
        final ByteArrayOutputStream data = new ByteArrayOutputStream(8192);

        Upload(Command command) {
            this.command = command;
        }
    }

    static final class Command {
        final Map<Character, String> values;
        final char action;
        final char medium;
        final char deleteMode;
        final char compression;
        final int format;
        final int width;
        final int height;
        final int quiet;
        final int displayColumns;
        final int displayRows;
        final boolean more;
        final boolean noCursorMovement;
        final long imageId;
        final long placementId;
        final long number;
        final int srcX;
        final int srcY;
        final int srcW;
        final int srcH;
        final int cellOffsetX;
        final int cellOffsetY;
        final int z;
        final int placeholder;

        private Command(Map<Character, String> values) {
            this.values = values;
            action = character(values, 'a', 't');
            medium = character(values, 't', 'd');
            deleteMode = character(values, 'd', 'a');
            compression = character(values, 'o', (char) 0);
            format = integer(values, 'f', 32);
            width = integer(values, 's', 0);
            height = integer(values, 'v', 0);
            quiet = integer(values, 'q', 0);
            displayColumns = integer(values, 'c', 0);
            displayRows = integer(values, 'r', 0);
            more = integer(values, 'm', 0) == 1;
            noCursorMovement = integer(values, 'C', 0) == 1;
            imageId = unsigned(values, 'i');
            placementId = unsigned(values, 'p');
            number = unsigned(values, 'I');
            srcX = integer(values, 'x', 0);
            srcY = integer(values, 'y', 0);
            srcW = integer(values, 'w', 0);
            srcH = integer(values, 'h', 0);
            cellOffsetX = integer(values, 'X', 0);
            cellOffsetY = integer(values, 'Y', 0);
            z = integer(values, 'z', 0);
            placeholder = integer(values, 'U', 0);
            if (quiet < 0 || quiet > 2) throw new IllegalArgumentException("invalid q value");
        }

        static Command parse(String control) {
            if (control.length() > MAX_CONTROL_BYTES) throw new IllegalArgumentException("control data too long");
            Map<Character, String> values = new HashMap<>();
            if (!control.isEmpty()) {
                String[] entries = control.split(",", -1);
                if (entries.length > MAX_CONTROLS) throw new IllegalArgumentException("too many control keys");
                for (String entry : entries) {
                    if (entry.length() < 3 || entry.charAt(1) != '=')
                        throw new IllegalArgumentException("malformed control key");
                    char key = entry.charAt(0);
                    String value = entry.substring(2);
                    if (value.isEmpty()) throw new IllegalArgumentException("empty control value");
                    values.put(key, value);
                }
            }
            return new Command(values);
        }

        boolean isContinuation() {
            // Frame-data chunks must repeat a=f, so it is allowed alongside m and q.
            for (Character key : values.keySet()) {
                if (key != 'm' && key != 'q' && !(key == 'a' && action == 'f')) return false;
            }
            return values.containsKey('m');
        }

        private static char character(Map<Character, String> values, char key, char fallback) {
            String value = values.get(key);
            if (value == null) return fallback;
            if (value.length() != 1) throw new IllegalArgumentException("invalid " + key + " value");
            return value.charAt(0);
        }

        static int integer(Map<Character, String> values, char key, int fallback) {
            String value = values.get(key);
            if (value == null) return fallback;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + key + " value");
            }
        }

        static long unsigned(Map<Character, String> values, char key) {
            String value = values.get(key);
            if (value == null) return 0;
            try {
                long result = Long.parseLong(value);
                if (result < 0 || result > 0xffffffffL) throw new NumberFormatException();
                return result;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid " + key + " value");
            }
        }
    }
}
