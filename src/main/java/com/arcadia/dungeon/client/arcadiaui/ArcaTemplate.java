package com.arcadia.dungeon.client.arcadiaui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * A parsed HTML + CSS template ready for rendering.
 *
 * <p>Load from a Minecraft resource (namespace:path):
 * <pre>{@code
 * ArcaTemplate t = ArcaTemplate.load("arcadia_dungeon:ui/admin_hub");
 * // Loads admin_hub.html + admin_hub.css from the resource pack
 * }</pre>
 *
 * <p>Or build from raw strings (useful for unit tests, no MC runtime needed):
 * <pre>{@code
 * ArcaTemplate t = ArcaTemplate.fromString("<div class=\"box\">Hello</div>", ".box { background: #1A1A2E; }");
 * }</pre>
 */
public final class ArcaTemplate {

    private final ArcaNode root;
    private ArcaStyleSheet styleSheet;

    private ArcaTemplate(ArcaNode root, ArcaStyleSheet styleSheet) {
        this.root = root;
        this.styleSheet = styleSheet != null ? styleSheet : ArcaStyleSheet.EMPTY;
    }

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Loads a template from Minecraft's resource manager.
     *
     * <p>The CSS file ({@code .css}) is loaded automatically if present in the same location as the HTML.
     *
     * @param resourceId resource location in {@code "namespace:path"} form,
     *                   e.g. {@code "arcadia_dungeon:ui/admin_hub"} maps to
     *                   {@code assets/arcadia_dungeon/ui/admin_hub.html}
     * @throws RuntimeException if the HTML resource cannot be found or parsed
     */
    public static ArcaTemplate load(String resourceId) {
        String[] parts = resourceId.split(":", 2);
        String namespace = parts[0];
        String path = parts.length > 1 ? parts[1] : parts[0];

        var rm = Minecraft.getInstance().getResourceManager();

        ArcaNode root;
        try {
            var htmlLoc = ResourceLocation.fromNamespaceAndPath(namespace, path + ".html");
            var htmlRes = rm.getResource(htmlLoc).orElseThrow(
                () -> new IllegalArgumentException("Template not found: " + htmlLoc));
            try (var is = htmlRes.open()) {
                root = ArcaHtmlParser.parse(is);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + resourceId, e);
        }

        ArcaStyleSheet sheet = ArcaStyleSheet.EMPTY;
        try {
            var cssLoc = ResourceLocation.fromNamespaceAndPath(namespace, path + ".css");
            var cssRes = rm.getResource(cssLoc);
            if (cssRes.isPresent()) {
                try (var is = cssRes.get().open()) {
                    String css = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    sheet = ArcaCssParser.parse(css);
                }
            }
        } catch (Exception ignored) {
            // CSS is optional — no crash if absent
        }

        return new ArcaTemplate(root, sheet);
    }

    /** Creates a template from a raw HTML string with no stylesheet. Does not require a Minecraft runtime. */
    public static ArcaTemplate fromString(String html) {
        return fromString(html, "");
    }

    /** Creates a template from raw HTML and CSS strings. Does not require a Minecraft runtime. */
    public static ArcaTemplate fromString(String html, String css) {
        ArcaNode root = ArcaHtmlParser.parse(html);
        ArcaStyleSheet sheet = css.isBlank() ? ArcaStyleSheet.EMPTY : ArcaCssParser.parse(css);
        return new ArcaTemplate(root, sheet);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    /** Returns the root {@link ArcaNode} of the parsed HTML document. */
    public ArcaNode root() { return root; }

    /** Returns the {@link ArcaStyleSheet} parsed from the accompanying CSS file, or {@link ArcaStyleSheet#EMPTY}. */
    public ArcaStyleSheet styleSheet() { return styleSheet; }
}
