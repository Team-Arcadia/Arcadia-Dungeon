package com.arcadia.dungeon.client.arcadiaui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Stylesheet of ordered (selector, style) entries. Two resolution modes are supported:
 *
 * <ul>
 *   <li>Legacy class-name resolve (back-compat with existing screens/tests) — matches a rule
 *       if the rule's selector contains exactly one segment with no tag and at least one of
 *       the listed classes is in the user-supplied class list.</li>
 *   <li>Tree-aware resolve via {@link #resolve(ArcaNode, Deque)} — full selector match including
 *       descendant/child combinators, sorted by specificity.</li>
 * </ul>
 */
public final class ArcaStyleSheet {

    public static final ArcaStyleSheet EMPTY = new ArcaStyleSheet(List.of(), List.of(), List.of(), List.of());

    public record Rule(ArcaSelector selector, ArcaStyle style, int order) {}

    private final List<Rule> base;
    private final List<Rule> hover;
    private final List<Rule> active;
    private final List<Rule> disabled;

    ArcaStyleSheet(List<Rule> base, List<Rule> hover, List<Rule> active, List<Rule> disabled) {
        this.base = List.copyOf(base);
        this.hover = List.copyOf(hover);
        this.active = List.copyOf(active);
        this.disabled = List.copyOf(disabled);
    }

    /** Bridge constructor: build from legacy class→style maps (used when no selector context exists). */
    ArcaStyleSheet(Map<String, ArcaStyle> base, Map<String, ArcaStyle> hover,
                   Map<String, ArcaStyle> active, Map<String, ArcaStyle> disabled) {
        this(toRules(base), toRules(hover), toRules(active), toRules(disabled));
    }

    private static List<Rule> toRules(Map<String, ArcaStyle> map) {
        List<Rule> out = new ArrayList<>();
        int i = 0;
        for (var e : map.entrySet()) {
            ArcaSelector sel = ArcaSelector.parse("." + e.getKey());
            if (sel != null) out.add(new Rule(sel, e.getValue(), i++));
        }
        return out;
    }

    // ── Class-name based resolve (back-compat) ──────────────────────────

    public ArcaStyle resolve(Collection<String> classNames) {
        return resolveByClasses(base, classNames);
    }

    public ArcaStyle resolveHover(Collection<String> classNames) {
        ArcaStyle b = resolve(classNames);
        ArcaStyle h = resolveByClasses(hover, classNames);
        return b.merge(h);
    }

    public ArcaStyle resolveActive(Collection<String> classNames) {
        ArcaStyle b = resolve(classNames);
        ArcaStyle a = resolveByClasses(active, classNames);
        return b.merge(a);
    }

    public ArcaStyle resolveDisabled(Collection<String> classNames) {
        ArcaStyle b = resolve(classNames);
        ArcaStyle d = resolveByClasses(disabled, classNames);
        return b.merge(d);
    }

    private static ArcaStyle resolveByClasses(List<Rule> rules, Collection<String> classNames) {
        List<Rule> matched = new ArrayList<>();
        for (Rule r : rules) {
            // Match if all of the selector's last-segment classes are present in classNames
            if (r.selector().segments.isEmpty()) continue;
            ArcaSelector.Segment last = r.selector().segments.get(r.selector().segments.size() - 1);
            // only consider "single segment, no tag" rules for legacy class-list resolve
            if (r.selector().segments.size() != 1) continue;
            if (last.tag != null) continue;
            boolean all = !last.classes.isEmpty();
            for (String c : last.classes) if (!classNames.contains(c)) { all = false; break; }
            if (all) matched.add(r);
        }
        return foldStyles(matched);
    }

    // ── Tree-aware resolve ──────────────────────────────────────────────

    public ArcaStyle resolve(ArcaNode node, Deque<ArcaNode> ancestors) {
        return matchAndFold(base, node, ancestors);
    }

    public ArcaStyle resolveHover(ArcaNode node, Deque<ArcaNode> ancestors) {
        ArcaStyle b = resolve(node, ancestors);
        ArcaStyle h = matchAndFold(hover, node, ancestors);
        return b.merge(h);
    }

    public ArcaStyle resolveActive(ArcaNode node, Deque<ArcaNode> ancestors) {
        ArcaStyle b = resolve(node, ancestors);
        ArcaStyle a = matchAndFold(active, node, ancestors);
        return b.merge(a);
    }

    public ArcaStyle resolveDisabled(ArcaNode node, Deque<ArcaNode> ancestors) {
        ArcaStyle b = resolve(node, ancestors);
        ArcaStyle d = matchAndFold(disabled, node, ancestors);
        return b.merge(d);
    }

    public boolean isEmpty() {
        return base.isEmpty() && hover.isEmpty() && active.isEmpty() && disabled.isEmpty();
    }

    private static ArcaStyle matchAndFold(List<Rule> rules, ArcaNode node, Deque<ArcaNode> ancestors) {
        Deque<ArcaNode> anc = ancestors != null ? ancestors : new ArrayDeque<>();
        List<Rule> matched = new ArrayList<>();
        for (Rule r : rules) {
            if (r.selector().matches(node, anc)) matched.add(r);
        }
        return foldStyles(matched);
    }

    private static ArcaStyle foldStyles(List<Rule> matched) {
        matched.sort(Comparator
                .<Rule>comparingInt(r -> r.selector().specificity())
                .thenComparingInt(Rule::order));
        ArcaStyle result = new ArcaStyle();
        for (Rule r : matched) result = result.merge(r.style());
        return result;
    }
}
