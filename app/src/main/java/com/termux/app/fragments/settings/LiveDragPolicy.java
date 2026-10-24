package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.ChromeShape;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.ChromeShape.Piece;
import com.termux.app.place.ChromeShapeModel;
import com.termux.app.place.EdgeStackPolicy;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.EnumMap;

/**
 * How the live frame's pictures move while an element is held over a target (appearance-layout-
 * editor SPEC §3.6a item 1), as pure arithmetic: the arrangement the drop would commit is asked
 * of the shape model, and every other element's picture moves by how far its piece's centre moves
 * between the arrangement on screen and that one. Translation only; nothing is resized. The
 * pictures start where the real views stand, so at rest they line up exactly; over a target they
 * stand where the model puts the elements, which is where the real views land as far as the
 * model's thicknesses are the launcher's own.
 */
final class LiveDragPolicy {

    private LiveDragPolicy() {}

    /** The moves, in the model's px: one per element still placed, and the keyboard's. */
    static final class Reflow {
        @NonNull final EnumMap<Element, float[]> elements = new EnumMap<>(Element.class);
        /** The keyboard's move ({dx, dy}), or null where it stands in neither arrangement. */
        @Nullable float[] keyboard;
    }

    /**
     * The moves for {@code held} dropped at {@code edge} (stack position {@code index}, on the
     * keyboard's far side for {@code underKeyboard}), or put away for a null {@code edge}, from
     * {@code base}, the arrangement {@code in} was built for. {@code held} itself has no move: it
     * is in the finger.
     */
    @NonNull
    static Reflow reflow(@NonNull ChromeShapeModel.Input in, @NonNull PlaceLayout base,
                         @NonNull Element held, @Nullable Edge edge, int index,
                         boolean underKeyboard) {
        PlaceLayout next = edge == null ? EdgeStackPolicy.withAway(base, held)
            : EdgeStackPolicy.withDrop(base, held, edge, index, underKeyboard);
        ChromeShape from = ChromeShapeModel.shape(in.withLayout(base));
        ChromeShape to = ChromeShapeModel.shape(in.withLayout(next));
        Reflow out = new Reflow();
        for (Element element : Element.values()) {
            if (element == held)
                continue;
            Piece a = from.piece(ChromeShape.PieceId.of(element));
            Piece b = to.piece(ChromeShape.PieceId.of(element));
            if (a == null || b == null || empty(a.box) || empty(b.box))
                continue;
            out.elements.put(element, move(a.box, b.box));
        }
        Box keyboardFrom = keyboardBox(from);
        Box keyboardTo = keyboardBox(to);
        if (keyboardFrom != null && keyboardTo != null)
            out.keyboard = move(keyboardFrom, keyboardTo);
        return out;
    }

    /** How far {@code b}'s centre stands from {@code a}'s. */
    @NonNull
    static float[] move(@NonNull Box a, @NonNull Box b) {
        return new float[] {(b.left + b.right - a.left - a.right) / 2f,
            (b.top + b.bottom - a.top - a.bottom) / 2f};
    }

    /** The keyboard's pieces as one box (a split keyboard is two), or null with none. */
    @Nullable
    static Box keyboardBox(@NonNull ChromeShape shape) {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        boolean any = false;
        for (Piece piece : shape.pieces()) {
            if (piece.id.element() != null || empty(piece.box))
                continue;
            left = Math.min(left, piece.box.left);
            top = Math.min(top, piece.box.top);
            right = Math.max(right, piece.box.right);
            bottom = Math.max(bottom, piece.box.bottom);
            any = true;
        }
        return any ? new Box(left, top, right, bottom) : null;
    }

    private static boolean empty(@NonNull Box box) {
        return box.width() <= 0f || box.height() <= 0f;
    }
}
