package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.BorderDrag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Dwindle Workspace's split tree (#420; spec "Layouts", Dwindle): each tile is a leaf, each split
 * halves its room side by side or top over bottom at a ratio. The stack owns which windows a
 * Workspace has, their order, its focus and which windows share a tile as tabs
 * ({@link TileGroup.Mode#TABS}); the tree owns only where each tile stands. {@link #sync} reads the
 * stack's tile groups each time: a leaf whose group went is dropped and its sibling takes the
 * room; a group the tree has not seen splits the tile before it in the stack's order along that
 * tile's longer side (so a window opened after the focused one splits the focused tile). Whether
 * a new window splits at all or joins the focused tile as a tab is decided before, from the sizes
 * ({@code DwindleTilingPolicy.placeNewWindow}); the stack then holds the tab.
 *
 * <p>The tree is the Workspace's layout state ({@link TilingStack#setLayoutState}), written by
 * {@link #encode} and read back by {@link #decode}.
 *
 * <p>Pure: no Android views. Not thread-safe; the UI thread owns it.
 */
public final class DwindleTree {

    /** The smallest share of a split either side keeps. */
    public static final float MIN_RATIO = 0.1f;
    /** The deepest tree a saved form may hold. */
    static final int MAX_DEPTH = 64;

    /** One tile as the stack has it: its windows in tab order and the one shown. */
    public static final class Tile {
        @NonNull public final long[] windows;
        public final long shown;

        public Tile(@NonNull long[] windows, long shown) {
            this.windows = windows.clone();
            this.shown = shown;
        }

        boolean contains(long id) {
            for (long window : windows) if (window == id) return true;
            return false;
        }
    }

    /** A tabbed tile's strip along its top: its windows in tab order, the one shown, and where it stands. */
    public static final class TabStrip {
        @NonNull public final long[] windows;
        public final long shown;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        TabStrip(@NonNull long[] windows, long shown, float left, float top, float right, float bottom) {
            this.windows = windows.clone();
            this.shown = shown;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * The tree laid out: each tile's shown window ({@code column} is the tile's index, {@code row}
     * 0; a tabbed tile's window stands below its strip), the strip as wide as the room, never
     * scrolled; and each tabbed tile's strip.
     */
    public static final class Tiles {
        @NonNull public final ScrollingLayoutPolicy.Layout layout;
        @NonNull public final List<TabStrip> strips;

        Tiles(@NonNull ScrollingLayoutPolicy.Layout layout, @NonNull List<TabStrip> strips) {
            this.layout = layout;
            this.strips = Collections.unmodifiableList(strips);
        }
    }

    /**
     * Whether a tile may split to take another: {@code focused} fills a room
     * {@code width}×{@code height} (its tab strip included) and {@code incoming} would take half of
     * it along its longer side. False means the incoming windows join it as tabs instead.
     */
    public interface Fit {
        boolean splits(float width, float height, @NonNull Tile focused, @NonNull Tile incoming);

        /** Every split fits. */
        Fit ALWAYS = (width, height, focused, incoming) -> true;
    }

    /** One window joining another's tile as a tab, just after window {@code target}, from {@link #build}. */
    public static final class Join {
        public final long window;
        public final long target;

        Join(long window, long target) {
            this.window = window;
            this.target = target;
        }
    }

    private static final class Node {
        // A leaf: its tile.
        @Nullable Tile tile;
        // A split: its two sides, which way, and the first side's share.
        @Nullable Node first;
        @Nullable Node second;
        boolean sideBySide;
        float ratio = 0.5f;
        @Nullable Node parent;

        static Node leaf(@NonNull Tile tile) {
            Node node = new Node();
            node.tile = tile;
            return node;
        }

        boolean isLeaf() {
            return tile != null;
        }
    }

    @Nullable private Node root;

    private DwindleTree() {}

    @NonNull
    public static DwindleTree empty() {
        return new DwindleTree();
    }

    /**
     * The stack's tile groups of {@code workspace}, in order, as the tree reads them; a minimised
     * window's group (it stands alone, #425) has no tile.
     */
    @NonNull
    public static List<Tile> tilesOf(@NonNull TilingWorkspace workspace) {
        List<Tile> tiles = new ArrayList<>();
        for (TileGroup group : workspace.groups()) {
            List<TilingWindow> windows = group.windows();
            if (windows.isEmpty() || group.active().isMinimised()) continue;
            long[] ids = new long[windows.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = windows.get(i).id();
            tiles.add(new Tile(ids, group.active().id()));
        }
        return tiles;
    }

    public boolean isEmpty() {
        return root == null;
    }

    /** The tiles in order (first side before second). */
    @NonNull
    public List<Tile> tiles() {
        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        List<Tile> tiles = new ArrayList<>();
        for (Node leaf : leaves) tiles.add(leaf.tile);
        return tiles;
    }

    // ---- following the stack ---------------------------------------------------------------------

    /**
     * Brings the tree to the stack's {@code tiles} in a room {@code width}×{@code height} with
     * {@code gap} between tiles: each leaf keeps the tile that holds its first window (else any of
     * its windows), a leaf left without one goes and its sibling takes its room, and each tile not
     * yet in the tree splits the one before it in order (the one after it for the first) along
     * that tile's longer side, opening on its right or below.
     */
    public void sync(@NonNull List<Tile> tiles, float width, float height, float gap) {
        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        Node[] owner = new Node[tiles.size()];
        boolean[] kept = new boolean[leaves.size()];
        // Pass 0: a leaf whose first window leads the tile; pass 1: whose first window is in it; pass 2: any.
        for (int pass = 0; pass < 3; pass++) {
            for (int l = 0; l < leaves.size(); l++) {
                if (kept[l]) continue;
                Tile was = leaves.get(l).tile;
                for (int t = 0; t < tiles.size(); t++) {
                    if (owner[t] != null || !claims(was, tiles.get(t), pass)) continue;
                    owner[t] = leaves.get(l);
                    kept[l] = true;
                    break;
                }
            }
        }
        for (int l = 0; l < leaves.size(); l++) if (!kept[l]) remove(leaves.get(l));
        for (int t = 0; t < tiles.size(); t++) if (owner[t] != null) owner[t].tile = tiles.get(t);
        for (int t = 0; t < tiles.size(); t++) {
            if (owner[t] != null) continue;
            Node leaf = Node.leaf(tiles.get(t));
            Node anchor = null;
            boolean after = true;
            for (int b = t - 1; b >= 0 && anchor == null; b--) anchor = owner[b];
            for (int a = t + 1; a < tiles.size() && anchor == null; a++) {
                anchor = owner[a];
                after = false;
            }
            if (anchor == null) {
                root = leaf;
            } else {
                float[] room = rectOf(anchor, width, height, gap);
                split(anchor, leaf, room[2] - room[0] > room[3] - room[1], after);
            }
            owner[t] = leaf;
        }
    }

    /**
     * The stack moved a tile to another slot (#425, a mark dragged sideways): the tiles stay where
     * they stand and the windows move through them, the tree's {@code i}-th tile (first side before
     * second) taking the stack's {@code i}-th. Only for the same tiles in a new order; false, with
     * nothing changed, when their count differs ({@link #sync} places those).
     */
    public boolean reorder(@NonNull List<Tile> tiles) {
        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        if (leaves.isEmpty() || leaves.size() != tiles.size()) return false;
        for (int i = 0; i < leaves.size(); i++) leaves.get(i).tile = tiles.get(i);
        return true;
    }

    /**
     * Window {@code windowId} takes a side of {@code targetId}'s tile (#433, a window dropped on
     * another's half in navigation mode): it leaves its own tile (a tile holding only it goes, its
     * sibling taking the room; a tab leaves its tabs) and splits the target's tile side by side
     * when {@code sideBySide}, else top over bottom, standing first (left or top) when
     * {@code first}. The stack makes it a tile of its own beside the target, and {@link #sync}
     * then keeps the split. False, with nothing changed, when the target is not in the tree or is
     * the window's own tile alone.
     */
    public boolean placeBeside(long windowId, long targetId, boolean sideBySide, boolean first) {
        Node target = leafOf(targetId);
        if (target == null) return false;
        Node own = leafOf(windowId);
        if (own == target && target.tile.windows.length == 1) return false;
        if (own != null) {
            if (own.tile.windows.length == 1) remove(own);
            else own.tile = without(own.tile, windowId);
        }
        split(target, Node.leaf(new Tile(new long[] {windowId}, windowId)), sideBySide, !first);
        return true;
    }

    /** {@code tile} without window {@code windowId}; the one shown stays, or the first shows. */
    @NonNull
    private static Tile without(@NonNull Tile tile, long windowId) {
        long[] rest = new long[tile.windows.length - 1];
        int i = 0;
        for (long id : tile.windows) if (id != windowId && i < rest.length) rest[i++] = id;
        return new Tile(rest, tile.shown == windowId ? rest[0] : tile.shown);
    }

    private static boolean claims(@NonNull Tile was, @NonNull Tile now, int pass) {
        if (was.windows.length == 0) return false;
        long first = was.windows[0];
        switch (pass) {
            case 0: return now.windows.length > 0 && now.windows[0] == first;
            case 1: return now.contains(first);
            default:
                for (long id : was.windows) if (now.contains(id)) return true;
                return false;
        }
    }

    /** Splits {@code anchor}'s room with {@code leaf}, which takes the second side when {@code after}. */
    private void split(@NonNull Node anchor, @NonNull Node leaf, boolean sideBySide, boolean after) {
        Node split = new Node();
        split.sideBySide = sideBySide;
        split.parent = anchor.parent;
        replace(anchor, split);
        split.first = after ? anchor : leaf;
        split.second = after ? leaf : anchor;
        anchor.parent = split;
        leaf.parent = split;
    }

    /** Takes {@code leaf} out; its sibling takes the split's room. */
    private void remove(@NonNull Node leaf) {
        Node parent = leaf.parent;
        if (parent == null) {
            if (root == leaf) root = null;
            return;
        }
        Node sibling = parent.first == leaf ? parent.second : parent.first;
        if (sibling != null) sibling.parent = parent.parent;
        replace(parent, sibling);
    }

    private void replace(@NonNull Node old, @Nullable Node with) {
        Node parent = old.parent;
        if (parent == null) root = with;
        else if (parent.first == old) parent.first = with;
        else parent.second = with;
    }

    /**
     * Builds an empty tree from {@code tiles} in order (a Workspace switched to Dwindle): each tile
     * splits the one before it along its longer side where {@code fit} allows, else its windows
     * join that tile as tabs. Returns the joins, in order, for the stack to make; the tree already
     * holds them, so a {@link #sync} after the stack made them keeps every tile.
     */
    @NonNull
    public List<Join> build(@NonNull List<Tile> tiles, float width, float height, float gap, @NonNull Fit fit) {
        root = null;
        List<Join> joins = new ArrayList<>();
        Node anchor = null;
        for (Tile tile : tiles) {
            if (tile.windows.length == 0) continue;
            if (anchor == null) {
                root = Node.leaf(tile);
                anchor = root;
                continue;
            }
            float[] room = rectOf(anchor, width, height, gap);
            float w = room[2] - room[0];
            float h = room[3] - room[1];
            if (fit.splits(w, h, anchor.tile, tile)) {
                Node leaf = Node.leaf(tile);
                split(anchor, leaf, w > h, true);
                anchor = leaf;
                continue;
            }
            Tile was = anchor.tile;
            long[] merged = new long[was.windows.length + tile.windows.length];
            System.arraycopy(was.windows, 0, merged, 0, was.windows.length);
            System.arraycopy(tile.windows, 0, merged, was.windows.length, tile.windows.length);
            anchor.tile = new Tile(merged, was.shown);
            // Each joins after the last tab, so the stack keeps the columns' order (a tab joins just after its target).
            long after = was.windows[was.windows.length - 1];
            for (long id : tile.windows) {
                joins.add(new Join(id, after));
                after = id;
            }
        }
        return joins;
    }

    /**
     * The room of the tile holding window {@code windowId} as {left, top, right, bottom}, its tab
     * strip included: what a split of it halves. Null when no tile holds it.
     */
    @Nullable
    public float[] roomOf(long windowId, float width, float height, float gap) {
        Node leaf = leafOf(windowId);
        return leaf == null ? null : rectOf(leaf, width, height, gap);
    }

    /** The tile holding window {@code windowId}, or null. */
    @Nullable
    public Tile tileOf(long windowId) {
        Node leaf = leafOf(windowId);
        return leaf == null ? null : leaf.tile;
    }

    @Nullable
    private Node leafOf(long windowId) {
        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        for (Node leaf : leaves) if (leaf.tile.contains(windowId)) return leaf;
        return null;
    }

    // ---- splits ----------------------------------------------------------------------------------

    /** How many splits the tree has; split {@code i} counts first side before second, a split before its sides. */
    public int splitCount() {
        List<Node> splits = new ArrayList<>();
        collectSplits(root, splits);
        return splits.size();
    }

    /** Sets split {@code index}'s first side's share, clamped to {@link #MIN_RATIO}…1−it; false when there is none. */
    public boolean setRatio(int index, float ratio) {
        List<Node> splits = new ArrayList<>();
        collectSplits(root, splits);
        if (index < 0 || index >= splits.size() || Float.isNaN(ratio)) return false;
        splits.get(index).ratio = clampRatio(ratio);
        return true;
    }

    /** Split {@code index}'s first side's share, or 0.5 when there is none. */
    public float ratioOf(int index) {
        List<Node> splits = new ArrayList<>();
        collectSplits(root, splits);
        return index < 0 || index >= splits.size() ? 0.5f : splits.get(index).ratio;
    }

    private static float clampRatio(float ratio) {
        return Math.max(MIN_RATIO, Math.min(1f - MIN_RATIO, ratio));
    }

    // ---- geometry --------------------------------------------------------------------------------

    /**
     * The tree in a room {@code width}×{@code height}, {@code gap} between tiles; a tabbed tile
     * keeps {@code tabStripPx} along its top for its tab strip.
     */
    @NonNull
    public Tiles layout(float width, float height, float gap, float tabStripPx) {
        List<ScrollingLayoutPolicy.Placement> placements = new ArrayList<>();
        List<TabStrip> strips = new ArrayList<>();
        if (root != null) {
            place(root, 0f, 0f, Math.max(0f, width), Math.max(0f, height), Math.max(0f, gap),
                Math.max(0f, tabStripPx), placements, strips);
        }
        return new Tiles(new ScrollingLayoutPolicy.Layout(placements, Math.max(0f, width)), strips);
    }

    private static void place(@NonNull Node node, float l, float t, float r, float b, float gap,
                              float strip, @NonNull List<ScrollingLayoutPolicy.Placement> placements,
                              @NonNull List<TabStrip> strips) {
        if (node.isLeaf()) {
            Tile tile = node.tile;
            float top = t;
            if (tile.windows.length > 1) {
                float stripBottom = Math.min(b, t + strip);
                strips.add(new TabStrip(tile.windows, tile.shown, l, t, r, stripBottom));
                top = stripBottom;
            }
            placements.add(new ScrollingLayoutPolicy.Placement(tile.shown, placements.size(), 0, l, top, r, b));
            return;
        }
        float[] first = new float[4];
        float[] second = new float[4];
        halves(node, l, t, r, b, gap, first, second);
        place(node.first, first[0], first[1], first[2], first[3], gap, strip, placements, strips);
        place(node.second, second[0], second[1], second[2], second[3], gap, strip, placements, strips);
    }

    private static void halves(@NonNull Node split, float l, float t, float r, float b, float gap,
                               @NonNull float[] first, @NonNull float[] second) {
        if (split.sideBySide) {
            float g = Math.min(gap, r - l);
            float cut = l + (r - l - g) * split.ratio;
            set(first, l, t, cut, b);
            set(second, cut + g, t, r, b);
        } else {
            float g = Math.min(gap, b - t);
            float cut = t + (b - t - g) * split.ratio;
            set(first, l, t, r, cut);
            set(second, l, cut + g, r, b);
        }
    }

    private static void set(@NonNull float[] out, float l, float t, float r, float b) {
        out[0] = l;
        out[1] = t;
        out[2] = r;
        out[3] = b;
    }

    /** {@code node}'s room as {left, top, right, bottom}. */
    @NonNull
    private float[] rectOf(@NonNull Node node, float width, float height, float gap) {
        List<Node> path = new ArrayList<>();
        for (Node n = node; n != null; n = n.parent) path.add(0, n);
        float[] room = {0f, 0f, Math.max(0f, width), Math.max(0f, height)};
        float[] first = new float[4];
        float[] second = new float[4];
        for (int i = 0; i + 1 < path.size(); i++) {
            Node split = path.get(i);
            halves(split, room[0], room[1], room[2], room[3], Math.max(0f, gap), first, second);
            float[] side = split.first == path.get(i + 1) ? first : second;
            room = side.clone();
        }
        return room;
    }

    /**
     * The window of the tile nearest {@code from}'s that way: the closest whose room lies wholly
     * past {@code from}'s edge on {@code side} and overlaps it across, or
     * {@link ScrollingLayoutPolicy#NO_WINDOW}.
     */
    public static long neighbour(@NonNull List<ScrollingLayoutPolicy.Placement> placements, long from,
                                 @NonNull BorderDrag.Border side) {
        ScrollingLayoutPolicy.Placement origin = null;
        for (ScrollingLayoutPolicy.Placement p : placements) if (p.windowId == from) origin = p;
        if (origin == null) return ScrollingLayoutPolicy.NO_WINDOW;
        long best = ScrollingLayoutPolicy.NO_WINDOW;
        float bestDistance = Float.MAX_VALUE;
        float bestOverlap = -1f;
        for (ScrollingLayoutPolicy.Placement p : placements) {
            if (p == origin) continue;
            float distance;
            float overlap;
            switch (side) {
                case LEFT:
                    distance = origin.left - p.right;
                    overlap = Math.min(origin.bottom, p.bottom) - Math.max(origin.top, p.top);
                    break;
                case RIGHT:
                    distance = p.left - origin.right;
                    overlap = Math.min(origin.bottom, p.bottom) - Math.max(origin.top, p.top);
                    break;
                case TOP:
                    distance = origin.top - p.bottom;
                    overlap = Math.min(origin.right, p.right) - Math.max(origin.left, p.left);
                    break;
                case BOTTOM:
                    distance = p.top - origin.bottom;
                    overlap = Math.min(origin.right, p.right) - Math.max(origin.left, p.left);
                    break;
                default:
                    return ScrollingLayoutPolicy.NO_WINDOW;
            }
            // A tab strip sits between a tile's room and its window, so allow it within the distance.
            if (distance < -0.5f || overlap <= 0f) continue;
            if (distance < bestDistance - 0.5f || (Math.abs(distance - bestDistance) <= 0.5f && overlap > bestOverlap)) {
                best = p.windowId;
                bestDistance = distance;
                bestOverlap = overlap;
            }
        }
        return best;
    }

    /**
     * Where one beat of a hold on the gap beside {@code target} (held toward {@code side}) goes from
     * {@code focused}: to {@code target} while focus is short of it, else on from {@code focused}
     * to the nearest tile that way, so a long hold walks on rather than coming back.
     * {@link ScrollingLayoutPolicy#NO_WINDOW} when there is nowhere further.
     */
    public static long gapHoldStep(@NonNull List<ScrollingLayoutPolicy.Placement> placements, long target,
                                   long focused, @NonNull BorderDrag.Border side) {
        ScrollingLayoutPolicy.Placement t = null;
        ScrollingLayoutPolicy.Placement f = null;
        for (ScrollingLayoutPolicy.Placement p : placements) {
            if (p.windowId == target) t = p;
            if (p.windowId == focused) f = p;
        }
        if (t == null) return ScrollingLayoutPolicy.NO_WINDOW;
        if (f == null) return target;
        if (f != t && !beyond(f, t, side)) return target;
        return neighbour(placements, focused, side);
    }

    /** Whether {@code p} lies wholly past {@code from} on {@code side}. */
    private static boolean beyond(@NonNull ScrollingLayoutPolicy.Placement p,
                                  @NonNull ScrollingLayoutPolicy.Placement from, @NonNull BorderDrag.Border side) {
        switch (side) {
            case LEFT: return p.right <= from.left + 0.5f;
            case RIGHT: return p.left >= from.right - 0.5f;
            case TOP: return p.bottom <= from.top + 0.5f;
            case BOTTOM: return p.top >= from.bottom - 0.5f;
            default: return false;
        }
    }

    // ---- the saved form --------------------------------------------------------------------------

    /** The tree as the Workspace's layout state. */
    @NonNull
    public String encode() {
        try {
            JSONObject json = new JSONObject();
            json.put("dwindle", root == null ? JSONObject.NULL : nodeJson(root));
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A tree read back from {@link #encode}'s form, to {@link #sync} with the stack. Null for
     * anything that is not one (a Scrolling state, a broken form).
     */
    @Nullable
    public static DwindleTree decode(@Nullable String saved) {
        if (saved == null || saved.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(saved);
            if (!json.has("dwindle")) return null;
            DwindleTree tree = new DwindleTree();
            JSONObject rootJson = json.optJSONObject("dwindle");
            if (rootJson != null) tree.root = readNode(rootJson, null, 0);
            return tree;
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    private static JSONObject nodeJson(@NonNull Node node) throws JSONException {
        JSONObject json = new JSONObject();
        if (node.isLeaf()) {
            JSONArray windows = new JSONArray();
            for (long id : node.tile.windows) windows.put(id);
            json.put("windows", windows);
            json.put("shown", node.tile.shown);
            return json;
        }
        json.put("sideBySide", node.sideBySide);
        json.put("ratio", (double) node.ratio);
        json.put("first", nodeJson(node.first));
        json.put("second", nodeJson(node.second));
        return json;
    }

    @Nullable
    private static Node readNode(@NonNull JSONObject json, @Nullable Node parent, int depth) {
        if (depth > MAX_DEPTH) return null;
        JSONArray windows = json.optJSONArray("windows");
        if (windows != null) {
            if (windows.length() == 0) return null;
            long[] ids = new long[windows.length()];
            for (int i = 0; i < ids.length; i++) ids[i] = windows.optLong(i, TilingStack.NO_WINDOW);
            Node leaf = Node.leaf(new Tile(ids, json.optLong("shown", ids[0])));
            leaf.parent = parent;
            return leaf;
        }
        JSONObject firstJson = json.optJSONObject("first");
        JSONObject secondJson = json.optJSONObject("second");
        if (firstJson == null || secondJson == null) return null;
        Node split = new Node();
        split.parent = parent;
        split.sideBySide = json.optBoolean("sideBySide", false);
        float ratio = (float) json.optDouble("ratio", 0.5d);
        split.ratio = Float.isNaN(ratio) ? 0.5f : clampRatio(ratio);
        split.first = readNode(firstJson, split, depth + 1);
        split.second = readNode(secondJson, split, depth + 1);
        // A side that could not be read leaves the other with the whole room.
        if (split.first == null || split.second == null) {
            Node only = split.first != null ? split.first : split.second;
            if (only != null) only.parent = parent;
            return only;
        }
        return split;
    }

    // ---- walking ---------------------------------------------------------------------------------

    private static void collectLeaves(@Nullable Node node, @NonNull List<Node> out) {
        if (node == null) return;
        if (node.isLeaf()) {
            out.add(node);
            return;
        }
        collectLeaves(node.first, out);
        collectLeaves(node.second, out);
    }

    private static void collectSplits(@Nullable Node node, @NonNull List<Node> out) {
        if (node == null || node.isLeaf()) return;
        out.add(node);
        collectSplits(node.first, out);
        collectSplits(node.second, out);
    }
}
