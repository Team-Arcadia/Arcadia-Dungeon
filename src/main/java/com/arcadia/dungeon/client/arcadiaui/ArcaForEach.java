package com.arcadia.dungeon.client.arcadiaui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArcaForEach {

    private ArcaForEach() {}

    /**
     * Expands a template ArcaNode once per item in {@code items}.
     * Each expansion resolves {@code {{ varName.key }}} using the item's model,
     * where keys are accessed as {@code varName.key}.
     */
    public static List<ArcaNode> expand(ArcaNode template, List<ArcaModel> items, String varName) {
        List<ArcaNode> result = new ArrayList<>(items.size());
        for (ArcaModel item : items) {
            ArcaModel scoped = key -> {
                if (key.startsWith(varName + ".")) {
                    return item.resolve(key.substring(varName.length() + 1));
                }
                return item.resolve(key);
            };
            result.add(cloneResolved(template, scoped));
        }
        return result;
    }

    public static ArcaNode resolveAttrs(ArcaNode node, ArcaModel model) {
        return cloneResolved(node, model);
    }

    private static ArcaNode cloneResolved(ArcaNode node, ArcaModel model) {
        Map<String, String> resolvedAttrs = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : node.attrs().entrySet()) {
            resolvedAttrs.put(e.getKey(), ArcaBindingResolver.resolve(e.getValue(), model));
        }
        List<ArcaNode> resolvedChildren = new ArrayList<>();
        for (ArcaNode child : node.children()) {
            resolvedChildren.add(cloneResolved(child, model));
        }
        String resolvedText = ArcaBindingResolver.resolve(node.text(), model);
        return new ArcaNode(node.tag(), resolvedAttrs, resolvedChildren, resolvedText);
    }
}
