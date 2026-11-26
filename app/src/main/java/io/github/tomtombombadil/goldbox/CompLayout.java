package io.github.tomtombombadil.goldbox;

/**
 * Where the Companion's two pictures go, by rule - never by device (Tom, 2026-11-25 evening: "I can't test this
 * on every device ... the layout needs to be smart about it by design/logic"; CLAUDE.md "Tom's notes, 2026-11-25
 * afternoon").
 *
 * From the rooms Shell leaves it (the screen less the game and the pad) this tries every candidate - the strip of
 * buttons down the outer side or across an edge away from the game (or in the band beside the game); the map and
 * the party stacked, side by side, the party in two columns, or each in a room of its own - and keeps the one with
 * the largest pictures. Both are shown when they come out legible (and, a phone sideways, not much smaller than one
 * alone would be); else one, a tap switching them. The pictures go nearest the game, the strip beyond them at the
 * hand's side, never between them.
 *
 * No Android here (plain Java, so it can be run and checked anywhere).
 */
final class CompLayout {

    // A Companion picture: 208 wide, rows 1.2 tall as the game's (the front end's kSideW, kSideH)
    static final int SW = 208, SH = 200;
    static final float ROW = 1.2f;
    // The strip: buttons 26dp round, 12dp apart, 36dp across
    static final int NB = 4;

    static final class Room {
        final String name;
        final float x0, y0, x1, y1;
        Room(String name, float x0, float y0, float x1, float y1) {
            this.name = name;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }
        float w() { return x1 - x0; }
        float h() { return y1 - y0; }
    }

    /** A picture placed: which (0 the map / battlefield, 1 the party), where, and the picture's rows shown. */
    static final class Pic {
        final int which, top, rows;
        float x, y;
        final float w, h;
        Pic(int which, float x, float y, float w, float h, int top, int rows) {
            this.which = which;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.top = top;
            this.rows = rows;
        }
    }

    static final class Result {
        String arrangement = "";        // stacked, side, stacked-2col, side-2col, split, single
        String strip = "";              // side, top, bottom, outside
        boolean single;
        Pic[] pics = new Pic[0];
        float scale;                    // the pictures' size: shown width / 208 (split: the two's mean)
        // The strip's buttons: their centres, in order (pin, swap sides, swap the pictures, menu); vertical or not
        float[] bx = new float[NB], by = new float[NB];
        boolean stripVertical;
        float stepX, stepY;     // from one button to the next (the words for the tap go on past the last)
        // Everything above in one box (the view that takes the Companion's touches)
        float x0, y0, x1, y1;
    }

    /** What the pictures hold (the front end says: channel 4). */
    static final class Content {
        int mapRows = 180;      // the map to its legend (a fight's field: all 200)
        int members = 6, pitch = 26;
        float lead = -1;        // the first button's edge from the strip's outer end, px (-1: 10dp; more clear of a rounded corner)
        int partyRows() { return members > 0 ? 1 + members * pitch : 12; }
    }

    private CompLayout() {}

    /**
     * rooms: where the pictures may go (Shell's: "column" sideways; "band" under the game upright, and on a tablet
     * also "beside" the pad and "over" it). outside: a band beside the game for the strip (or null). outerLeft: the
     * hand's side is the left. edges: the strip's edges allowed ("top", "bottom" - never the one toward the game).
     * phoneLand: a phone sideways (one picture when it is much the larger). which: the one shown alone (0 / 1).
     * swap: the pictures' places swapped.
     */
    static Result choose(Room[] rooms, Room outside, float dp, Content c, boolean outerLeft, boolean towardLeft,
                         boolean land, String[] edges, boolean phoneLand, int which, boolean swap) {
        final float g = 4 * dp;
        Cand both = null, one = null;
        for (Room room : rooms) {
            if (room == null || room.name.equals("over") || room.w() <= 0 || room.h() <= 0) continue;
            for (Strip s : strips(room, dp, outerLeft, edges, outside)) {
                if (s.room.w() <= 0 || s.room.h() <= 0) continue;
                for (Cand k : arrangements(s.room, c, g, swap)) {
                    k.strip = s.name;
                    k.stripRoom = s.name.equals("outside") ? outside : room;
                    if (both == null || k.scale > both.scale) both = k;
                }
                final Cand k = single(s.room, c, which);
                if (k.scale <= 0) continue;
                k.strip = s.name;
                k.stripRoom = s.name.equals("outside") ? outside : room;
                if (one == null || k.scale > one.scale) one = k;
            }
        }
        // Two rooms - beside the pad and over it: each picture as large as its own room lets it be
        Room beside = find(rooms, "beside"), over = find(rooms, "over");
        if (beside != null && over != null && beside.w() > 0 && over.w() > 0) {
            for (Strip s : strips(over, dp, outerLeft, new String[] {"top", "bottom"}, null)) {
                if (s.room.w() <= 0 || s.room.h() <= 0) continue;
                final Room a = swap ? s.room : beside, b = swap ? beside : s.room;
                final int ra = c.mapRows, rb = c.partyRows();
                final float sa = fit(a, ra), sb = fit(b, rb);
                if (Math.min(sa, sb) <= 0) continue;
                final float sc = (float) Math.sqrt(sa * sb);
                if (both == null || sc > both.scale) {
                    Cand k = new Cand();
                    k.scale = sc;
                    k.name = "split";
                    k.pics = new Pic[] {
                            new Pic(0, a.x0 + (a.w() - SW * sa) / 2, a.y0, SW * sa, ra * ROW * sa, 0, ra),
                            new Pic(1, b.x0 + (b.w() - SW * sb) / 2, b.y0, SW * sb, rb * ROW * sb, 0, rb)};
                    k.strip = s.name;
                    k.stripRoom = over;
                    both = k;
                }
            }
        }
        if (one == null) return null;
        final float legible = 0.8f * dp;       // (the 8-pixel letters ~7.7dp tall or more)
        boolean showBoth = false;
        if (both != null) {
            float min = Float.MAX_VALUE;
            for (Pic p : both.pics) min = Math.min(min, p.w / SW);
            showBoth = min >= legible && (!phoneLand || both.scale >= 0.8f * one.scale);
        }
        final Cand k = showBoth ? both : one;
        // (sideways: against the game's side of the column)
        if (land && !k.name.equals("split")) {
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (Pic p : k.pics) {
                lo = Math.min(lo, p.x);
                hi = Math.max(hi, p.x + p.w);
            }
            final float dx = towardLeft ? k.room.x0 - lo : k.room.x1 - hi;
            for (Pic p : k.pics) p.x += dx;
        }
        return finish(k, dp, outerLeft, outside, c.lead < 0 ? 10 * dp : c.lead);
    }

    // ---- Candidates

    private static final class Strip {
        final String name;
        final Room room;        // the room the pictures have once the strip is placed
        Strip(String name, Room room) {
            this.name = name;
            this.room = room;
        }
    }

    private static final class Cand {
        String name = "", strip = "";
        float scale;
        Pic[] pics;
        Room room;              // the pictures' room (after the strip)
        Room stripRoom;         // the room the strip stands in
    }

    private static Room find(Room[] rooms, String name) {
        for (Room r : rooms)
            if (r != null && r.name.equals(name)) return r;
        return null;
    }

    private static float stripLength(float dp) { return NB * (26 + 12) * dp + 16 * dp; }

    private static java.util.List<Strip> strips(Room room, float dp, boolean outerLeft, String[] edges, Room outside) {
        final java.util.List<Strip> out = new java.util.ArrayList<>();
        final float sw = 36 * dp;
        if (room.h() >= stripLength(dp))
            out.add(new Strip("side", outerLeft ? new Room(room.name, room.x0 + sw, room.y0, room.x1, room.y1)
                    : new Room(room.name, room.x0, room.y0, room.x1 - sw, room.y1)));
        if (room.w() >= stripLength(dp)) {
            for (String e : edges) {
                if (e.equals("top")) out.add(new Strip("top", new Room(room.name, room.x0, room.y0 + sw, room.x1, room.y1)));
                if (e.equals("bottom")) out.add(new Strip("bottom", new Room(room.name, room.x0, room.y0, room.x1, room.y1 - sw)));
            }
        }
        if (outside != null && outside.h() >= 40 * dp && outside.w() >= stripLength(dp))
            out.add(new Strip("outside", room));
        return out;
    }

    private static float fit(Room r, int rows) { return Math.min(r.w() / SW, r.h() / (rows * ROW)); }

    /** Both pictures in one room: stacked or side by side, the party in one column or two. */
    private static java.util.List<Cand> arrangements(Room room, Content c, float g, boolean swap) {
        final java.util.List<Cand> out = new java.util.ArrayList<>();
        final int n = c.members, pitch = c.pitch;
        for (int k = 1; k <= 2; ++k) {
            if (k == 2 && n < 4) continue;
            final int m = k == 2 ? (n + 1) / 2 : n;
            final int prMax = k == 2 ? 1 + m * pitch : c.partyRows();
            for (int stacked = 0; stacked <= 1; ++stacked) {
                // The block: the map (208 x mapRows) and k party columns (208 x prMax each), g apart
                final float unitW = stacked == 1 ? SW * k : SW * (1 + k);
                final float gapW = stacked == 1 ? (k - 1) * g : k * g;
                final float unitH = stacked == 1 ? (c.mapRows + prMax) * ROW : Math.max(c.mapRows, prMax) * ROW;
                final float gapH = stacked == 1 ? g : 0;
                final float s = Math.min((room.w() - gapW) / unitW, (room.h() - gapH) / unitH);
                if (s <= 0) continue;
                final float pw = SW * s, blockW = unitW * s + gapW;
                final float x = room.x0 + (room.w() - blockW) / 2;
                final java.util.List<Pic> ps = new java.util.ArrayList<>();
                final float mapH = c.mapRows * ROW * s;
                // (swapped: the party first - on top, or at the left)
                final boolean partyFirst = swap;
                if (stacked == 1) {
                    final float partyH = prMax * ROW * s;
                    final float my = partyFirst ? room.y0 + partyH + g : room.y0;
                    final float py = partyFirst ? room.y0 : room.y0 + mapH + g;
                    ps.add(new Pic(0, x, my, pw, mapH, 0, c.mapRows));
                    addParty(ps, k, n, m, pitch, c, x, py, pw, s, g);
                } else {
                    final float mx = partyFirst ? x + k * (pw + g) : x;
                    final float px = partyFirst ? x : x + pw + g;
                    ps.add(new Pic(0, mx, room.y0, pw, mapH, 0, c.mapRows));
                    addParty(ps, k, n, m, pitch, c, px, room.y0, pw, s, g);
                }
                final Cand cand = new Cand();
                cand.scale = s;
                cand.name = (stacked == 1 ? "stacked" : "side") + (k == 2 ? "-2col" : "");
                cand.pics = ps.toArray(new Pic[0]);
                cand.room = room;
                out.add(cand);
            }
        }
        return out;
    }

    private static void addParty(java.util.List<Pic> ps, int k, int n, int m, int pitch, Content c, float x, float y,
                                 float pw, float s, float g) {
        if (k == 1) {
            ps.add(new Pic(1, x, y, pw, c.partyRows() * ROW * s, 0, c.partyRows()));
            return;
        }
        // (two columns: the first m members, then the rest - each column's rows from the picture)
        final int r0 = 1 + m * pitch;
        ps.add(new Pic(1, x, y, pw, r0 * ROW * s, 0, r0));
        final int r1 = (n - m) * pitch;
        if (r1 > 0) ps.add(new Pic(1, x + pw + g, y, pw, r1 * ROW * s, r0, r1));
    }

    /** One picture, the tap switching it: as large as the room lets the taller of the two be. */
    private static Cand single(Room room, Content c, int which) {
        final int rows = Math.max(c.mapRows, c.partyRows());
        final float s = fit(room, rows);
        final Cand k = new Cand();
        k.scale = s;
        k.name = "single";
        final int own = which == 1 ? c.partyRows() : c.mapRows;
        k.pics = new Pic[] {new Pic(which == 1 ? 1 : 0, room.x0 + (room.w() - SW * s) / 2, room.y0, SW * s,
                own * ROW * s, 0, own)};
        k.room = room;
        return k;
    }

    // ---- The strip's buttons, the tap's words, the box

    private static Result finish(Cand k, float dp, boolean outerLeft, Room outside, float lead) {
        final Result r = new Result();
        r.arrangement = k.name;
        r.strip = k.strip;
        r.single = k.name.equals("single");
        r.pics = k.pics;
        r.scale = k.scale;
        final float rr = 13 * dp, step = 2 * rr + 12 * dp;
        final Room room = k.stripRoom;
        float picBottom = -Float.MAX_VALUE, picTop = Float.MAX_VALUE;
        for (Pic p : k.pics) {
            picBottom = Math.max(picBottom, p.y + p.h);
            picTop = Math.min(picTop, p.y);
        }
        // (the buttons from the strip's outer end inward: the pin first)
        if (k.strip.equals("side")) {
            r.stripVertical = true;
            final float cx = outerLeft ? room.x0 + 18 * dp : room.x1 - 18 * dp;
            for (int i = 0; i < NB; ++i) {
                r.bx[i] = cx;
                r.by[i] = room.y0 + lead + rr + i * step;
            }
        } else {
            final float cy;
            if (k.strip.equals("outside")) cy = outside.y0 + outside.h() / 2;
            else if (k.strip.equals("top")) cy = room.y0 + 18 * dp;
            else cy = Math.min(room.y1 - 18 * dp, picBottom + 6 * dp + 18 * dp);       // (right under the pictures)
            final Room along = k.strip.equals("outside") ? outside : room;
            for (int i = 0; i < NB; ++i) {
                r.bx[i] = outerLeft ? along.x0 + 18 * dp + i * step : along.x1 - 18 * dp - i * step;
                r.by[i] = cy;
            }
        }
        r.stepX = NB > 1 ? r.bx[1] - r.bx[0] : 0;
        r.stepY = NB > 1 ? r.by[1] - r.by[0] : 0;
        // The box: the pictures and the strip
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (Pic p : k.pics) {
            x0 = Math.min(x0, p.x);
            y0 = Math.min(y0, p.y);
            x1 = Math.max(x1, p.x + p.w);
            y1 = Math.max(y1, p.y + p.h);
        }
        for (int i = 0; i < NB; ++i) {
            x0 = Math.min(x0, r.bx[i] - rr - 6 * dp);
            y0 = Math.min(y0, r.by[i] - rr - 6 * dp);
            x1 = Math.max(x1, r.bx[i] + rr + 6 * dp);
            y1 = Math.max(y1, r.by[i] + rr + 6 * dp);
        }
        r.x0 = x0;
        r.y0 = y0;
        r.x1 = x1;
        r.y1 = y1;
        return r;
    }
}
