package com.arcadia.dungeon.client.arcadiaui;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArcaNode {

    public static final Set<String> KNOWN_TAGS = Set.of(
        "div", "label", "button", "icon", "badge", "grid", "row", "col",
        "hr", "span", "input"
    );

    private final String tag;
    private final Map<String, String> attrs;
    private final List<ArcaNode> children;
    private final String text;

    public ArcaNode(String tag, Map<String, String> attrs, List<ArcaNode> children, String text) {
        this.tag = tag;
        this.attrs = Map.copyOf(attrs);
        this.children = List.copyOf(children);
        this.text = text != null ? text : "";
    }

    public String tag()                { return tag; }
    public Map<String, String> attrs() { return attrs; }
    public List<ArcaNode> children()   { return children; }
    public String text()               { return text; }

    public String attr(String key) { return attrs.getOrDefault(key, ""); }
    public boolean hasAttr(String key) { return attrs.containsKey(key); }

    public List<String> classNames() {
        String cls = attr("class");
        return cls.isEmpty() ? List.of() : List.of(cls.trim().split("\\s+"));
    }

    public boolean hasClass(String name) { return classNames().contains(name); }

    public String onClickHandler() { return attr("onclick"); }

    public String vFor() { return attr("v-for"); }

    public boolean isKnown() { return KNOWN_TAGS.contains(tag); }

    @Override
    public String toString() {
        return "<" + tag + (attrs.isEmpty() ? "" : " " + attrs) + "> children=" + children.size();
    }
}
