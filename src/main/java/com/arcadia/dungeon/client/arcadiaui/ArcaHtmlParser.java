package com.arcadia.dungeon.client.arcadiaui;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArcaHtmlParser {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String src;
    private int pos;

    private ArcaHtmlParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static ArcaNode parse(String html) {
        return new ArcaHtmlParser(html.trim()).parseDocument();
    }

    public static ArcaNode parse(InputStream is) throws IOException {
        return parse(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    }

    private ArcaNode parseDocument() {
        skipWhitespace();
        // Skip processing instructions <?...?> and doctypes <!...>
        while (pos < src.length() && peek("<") && (peekAt(1) == '?' || peekAt(1) == '!')) {
            consumeUntil('>');
            pos++;
            skipWhitespace();
        }
        return parseElement();
    }

    private ArcaNode parseElement() {
        if (!peek("<") || peek("</")) return null;
        pos++; // consume '<'
        String tag = parseName().toLowerCase(java.util.Locale.ROOT);

        Map<String, String> attrs = parseAttrs();
        skipWhitespace();

        // Self-closing: <tag/>
        if (peek("/>")) {
            pos += 2;
            return makeNode(tag, attrs, List.of(), "");
        }

        if (!peek(">")) {
            // Malformed — skip
            consumeUntil('>');
            pos++;
            return null;
        }
        pos++; // consume '>'

        // Parse children and text content
        List<ArcaNode> children = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        while (pos < src.length()) {
            skipWhitespace();
            if (peek("</" + tag)) {
                consumeUntil('>');
                pos++;
                break;
            }
            if (peek("</")) {
                // Mismatched closing tag — stop
                break;
            }
            if (peek("<")) {
                ArcaNode child = parseElement();
                if (child != null) children.add(child);
            } else {
                // Text content
                int start = pos;
                while (pos < src.length() && src.charAt(pos) != '<') pos++;
                text.append(decodeEntities(src.substring(start, pos)));
            }
        }

        return makeNode(tag, attrs, children, text.toString().trim());
    }

    private ArcaNode makeNode(String tag, Map<String, String> attrs, List<ArcaNode> children, String text) {
        if (!ArcaNode.KNOWN_TAGS.contains(tag)) {
            LOGGER.warn("[ArcadiaUI] Balise HTML inconnue ignorée : <{}>", tag);
            return null;
        }
        // Décoder les entités dans les attributs
        Map<String, String> decodedAttrs = new LinkedHashMap<>();
        attrs.forEach((k, v) -> decodedAttrs.put(k, decodeEntities(v)));
        return new ArcaNode(tag, decodedAttrs, children, text);
    }

    private static String decodeEntities(String s) {
        return s.replace("&amp;",  "&")
                .replace("&lt;",   "<")
                .replace("&gt;",   ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");
    }

    private Map<String, String> parseAttrs() {
        Map<String, String> attrs = new LinkedHashMap<>();
        while (pos < src.length()) {
            skipWhitespace();
            if (peek(">") || peek("/>") || peek("<") || pos >= src.length()) break;
            String name = parseName();
            if (name.isEmpty()) { pos++; continue; }
            skipWhitespace();
            if (peek("=")) {
                pos++;
                skipWhitespace();
                attrs.put(name, parseAttrValue());
            } else {
                attrs.put(name, name); // boolean attribute
            }
        }
        return attrs;
    }

    private String parseAttrValue() {
        if (pos >= src.length()) return "";
        char quote = src.charAt(pos);
        if (quote == '"' || quote == '\'') {
            pos++;
            int start = pos;
            while (pos < src.length() && src.charAt(pos) != quote) pos++;
            String value = src.substring(start, pos);
            if (pos < src.length()) pos++; // consume closing quote
            return value;
        }
        // Unquoted value
        int start = pos;
        while (pos < src.length() && !Character.isWhitespace(src.charAt(pos))
               && src.charAt(pos) != '>' && src.charAt(pos) != '/') pos++;
        return src.substring(start, pos);
    }

    private String parseName() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':') pos++;
            else break;
        }
        return src.substring(start, pos);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private boolean peek(String s) {
        return src.startsWith(s, pos);
    }

    private char peekAt(int offset) {
        int idx = pos + offset;
        return idx < src.length() ? src.charAt(idx) : 0;
    }

    private void consumeUntil(char c) {
        while (pos < src.length() && src.charAt(pos) != c) pos++;
    }

    private void consumeUntil(String s) {
        while (pos < src.length() && !src.startsWith(s, pos)) pos++;
    }
}
