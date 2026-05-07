package com.arcadia.dungeon.client.arcadiaui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * CSS selector model. Supports compound selectors (class + tag), descendant combinators
 * ({@code .a .b}) and direct child combinators ({@code .a > .b}).
 */
public final class ArcaSelector {

    /** A simple selector segment: tag + classes (either may be empty). */
    public static final class Segment {
        public final String tag;            // null if no tag constraint
        public final List<String> classes;  // empty if no class constraint

        public Segment(String tag, List<String> classes) {
            this.tag = tag;
            this.classes = classes;
        }

        public boolean matches(ArcaNode node) {
            if (tag != null && !tag.equals(node.tag())) return false;
            if (!classes.isEmpty()) {
                List<String> nodeClasses = node.classNames();
                for (String c : classes) if (!nodeClasses.contains(c)) return false;
            }
            return true;
        }

        public int specificity() {
            int s = classes.size() * 10;
            if (tag != null) s += 1;
            return s;
        }
    }

    /** A combinator between two segments. */
    public enum Combinator { DESCENDANT, CHILD }

    public final List<Segment> segments;             // size >= 1, last is the target
    public final List<Combinator> combinators;       // size = segments.size() - 1
    public final String raw;

    public ArcaSelector(List<Segment> segments, List<Combinator> combinators, String raw) {
        this.segments = List.copyOf(segments);
        this.combinators = List.copyOf(combinators);
        this.raw = raw;
    }

    public boolean isUniversal() { return segments.isEmpty(); }

    public int specificity() {
        int s = 0;
        for (Segment seg : segments) s += seg.specificity();
        return s;
    }

    /** Match this selector against the node, with its ancestor chain (closest first). */
    public boolean matches(ArcaNode node, Deque<ArcaNode> ancestors) {
        if (segments.isEmpty()) return true;
        Segment last = segments.get(segments.size() - 1);
        if (!last.matches(node)) return false;
        if (segments.size() == 1) return true;

        // Walk ancestors right-to-left through remaining segments.
        java.util.Iterator<ArcaNode> it = ancestors.iterator();
        int segIdx = segments.size() - 2;
        Combinator comb = combinators.get(segIdx);
        Segment cur = segments.get(segIdx);
        boolean firstAncestor = true;
        while (it.hasNext()) {
            ArcaNode anc = it.next();
            boolean match = cur.matches(anc);
            if (comb == Combinator.CHILD) {
                if (!firstAncestor) return false; // child must be direct
                if (!match) return false;
            } else {
                if (!match) { firstAncestor = false; continue; }
            }
            // matched this segment; advance
            segIdx--;
            if (segIdx < 0) return true;
            comb = combinators.get(segIdx);
            cur = segments.get(segIdx);
            firstAncestor = true;
            continue;
        }
        return false;
    }

    // ── Parser ────────────────────────────────────────────────────────────

    /** Parse a selector string. Returns null if empty/invalid. */
    public static ArcaSelector parse(String raw) {
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // Tokenize on whitespace and '>'
        List<String> tokens = new ArrayList<>();
        List<Combinator> combs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean nextIsChild = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '>') {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
                nextIsChild = true;
            } else if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString()); cur.setLength(0);
                    combs.add(nextIsChild ? Combinator.CHILD : Combinator.DESCENDANT);
                    nextIsChild = false;
                }
            } else {
                if (nextIsChild && combs.size() < tokens.size()) {
                    // Already added a descendant combinator, replace with child
                    combs.set(combs.size() - 1, Combinator.CHILD);
                    nextIsChild = false;
                }
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());

        // combs must have size = tokens.size() - 1
        while (combs.size() > tokens.size() - 1) combs.remove(combs.size() - 1);

        List<Segment> segs = new ArrayList<>();
        for (String tok : tokens) segs.add(parseSegment(tok));
        if (segs.isEmpty()) return null;
        return new ArcaSelector(segs, combs, raw);
    }

    private static Segment parseSegment(String tok) {
        String tag = null;
        List<String> classes = new ArrayList<>();
        // Split on '.' but keep leading tag if present
        int firstDot = tok.indexOf('.');
        String tagPart;
        String rest;
        if (firstDot < 0) { tagPart = tok; rest = ""; }
        else { tagPart = tok.substring(0, firstDot); rest = tok.substring(firstDot); }
        if (!tagPart.isEmpty() && !tagPart.equals("*")) tag = tagPart;
        if (!rest.isEmpty()) {
            for (String c : rest.split("\\.")) {
                if (!c.isEmpty()) classes.add(c);
            }
        }
        return new Segment(tag, classes);
    }

    @Override public String toString() { return "ArcaSelector(" + raw + ")"; }
}
