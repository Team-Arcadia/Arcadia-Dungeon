package com.arcadia.dungeon.client.arcadiaui;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArcaCssParser {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern RULE  = Pattern.compile("([^{]+)\\{([^}]*)\\}", Pattern.DOTALL);
    private static final Pattern VAR_USE = Pattern.compile("var\\(\\s*(--[\\w-]+)\\s*\\)");

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    static {
        NAMED_COLORS.put("transparent", 0x00000000);
        NAMED_COLORS.put("white",       0xFFFFFFFF);
        NAMED_COLORS.put("black",       0xFF000000);
        NAMED_COLORS.put("red",         0xFFFF0000);
        NAMED_COLORS.put("green",       0xFF008000);
        NAMED_COLORS.put("lime",        0xFF32CD32);
        NAMED_COLORS.put("blue",        0xFF0000FF);
        NAMED_COLORS.put("yellow",      0xFFFFFF00);
        NAMED_COLORS.put("gray",        0xFF808080);
        NAMED_COLORS.put("grey",        0xFF808080);
        NAMED_COLORS.put("silver",      0xFFC0C0C0);
        NAMED_COLORS.put("orange",      0xFFFFA500);
        NAMED_COLORS.put("purple",      0xFF800080);
        NAMED_COLORS.put("cyan",        0xFF00FFFF);
        NAMED_COLORS.put("magenta",     0xFFFF00FF);
        NAMED_COLORS.put("pink",        0xFFFF69B4);
        NAMED_COLORS.put("brown",       0xFF8B4513);
        NAMED_COLORS.put("navy",        0xFF000080);
        NAMED_COLORS.put("teal",        0xFF008080);
        NAMED_COLORS.put("gold",        0xFFFFD700);
        NAMED_COLORS.put("copper",      0xFFB87333);
        NAMED_COLORS.put("maroon",      0xFF800000);
        NAMED_COLORS.put("olive",       0xFF808000);
        NAMED_COLORS.put("aqua",        0xFF00FFFF);
        NAMED_COLORS.put("fuchsia",     0xFFFF00FF);
        NAMED_COLORS.put("indigo",      0xFF4B0082);
        NAMED_COLORS.put("violet",      0xFFEE82EE);
        NAMED_COLORS.put("coral",       0xFFFF7F50);
        NAMED_COLORS.put("salmon",      0xFFFA8072);
        NAMED_COLORS.put("khaki",       0xFFF0E68C);
        NAMED_COLORS.put("beige",       0xFFF5F5DC);
    }

    public static ArcaStyleSheet parse(String css) {
        List<ArcaStyleSheet.Rule> base     = new ArrayList<>();
        List<ArcaStyleSheet.Rule> hover    = new ArrayList<>();
        List<ArcaStyleSheet.Rule> active   = new ArrayList<>();
        List<ArcaStyleSheet.Rule> disabled = new ArrayList<>();

        String processed = resolveVariables(stripComments(css));
        Matcher m = RULE.matcher(processed);
        int order = 0;
        while (m.find()) {
            String rawSel = m.group(1).trim();
            String body   = m.group(2).trim();
            if (rawSel.equals(":root")) continue;

            ArcaStyle style = parseBody(body);
            for (String sel : rawSel.split(",")) {
                sel = sel.trim();
                String state = "base";
                String coreSel = sel;
                if (sel.endsWith(":hover"))    { state = "hover";    coreSel = sel.substring(0, sel.length() - 6).trim(); }
                else if (sel.endsWith(":active"))  { state = "active";  coreSel = sel.substring(0, sel.length() - 7).trim(); }
                else if (sel.endsWith(":disabled")){ state = "disabled";coreSel = sel.substring(0, sel.length() - 9).trim(); }

                ArcaSelector parsed = ArcaSelector.parse(coreSel);
                if (parsed == null) continue;
                ArcaStyleSheet.Rule rule = new ArcaStyleSheet.Rule(parsed, style, order++);
                switch (state) {
                    case "hover"    -> hover.add(rule);
                    case "active"   -> active.add(rule);
                    case "disabled" -> disabled.add(rule);
                    default         -> base.add(rule);
                }
            }
        }
        return new ArcaStyleSheet(base, hover, active, disabled);
    }

    private static String resolveVariables(String css) {
        Map<String, String> vars = new HashMap<>();
        Matcher root = Pattern.compile(":root\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(css);
        while (root.find()) {
            for (String decl : root.group(1).split(";")) {
                decl = decl.trim();
                int colon = decl.indexOf(':');
                if (colon > 0 && decl.startsWith("--")) {
                    vars.put(decl.substring(0, colon).trim(), decl.substring(colon + 1).trim());
                }
            }
        }
        if (vars.isEmpty()) return css;
        StringBuffer sb = new StringBuffer();
        Matcher v = VAR_USE.matcher(css);
        while (v.find()) {
            String val = vars.getOrDefault(v.group(1), v.group(0));
            v.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        v.appendTail(sb);
        return sb.toString();
    }

    private static ArcaStyle parseBody(String body) {
        ArcaStyle s = new ArcaStyle();
        for (String decl : body.split(";")) {
            decl = decl.trim();
            if (decl.isEmpty()) continue;
            int colon = decl.indexOf(':');
            if (colon < 0) continue;
            String prop  = decl.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String value = decl.substring(colon + 1).trim();
            try { applyProp(s, prop, value); }
            catch (Exception e) {
                LOGGER.warn("[ArcadiaUI] CSS ignoré '{}:{}': {}", prop, value, e.getMessage());
            }
        }
        return s;
    }

    private static void applyProp(ArcaStyle s, String prop, String value) {
        switch (prop) {
            case "background", "background-color" -> s.background  = parseColor(value);
            case "color"                           -> s.color       = parseColor(value);
            case "border-color"                    -> s.borderColor = parseColor(value);

            case "width"      -> applyLengthDim(s, "width", value);
            case "height"     -> applyLengthDim(s, "height", value);
            case "min-width"  -> applyLengthDim(s, "minWidth", value);
            case "max-width"  -> applyLengthDim(s, "maxWidth", value);
            case "min-height" -> applyLengthDim(s, "minHeight", value);
            case "max-height" -> applyLengthDim(s, "maxHeight", value);

            case "padding"        -> applyPaddingShorthand(s, value);
            case "padding-top"    -> s.paddingTop    = parseLength(value);
            case "padding-right"  -> s.paddingRight  = parseLength(value);
            case "padding-bottom" -> s.paddingBottom = parseLength(value);
            case "padding-left"   -> s.paddingLeft   = parseLength(value);

            case "margin"        -> applyMarginShorthand(s, value);
            case "margin-right"  -> s.marginRight  = parseLength(value);
            case "margin-bottom" -> s.marginBottom = parseLength(value);
            case "margin-left"   -> s.marginLeft   = parseLength(value);

            case "gap"             -> s.gap             = parseLength(value);
            case "flex"            -> s.flex            = parseInt(value);
            case "border"          -> applyBorderShorthand(s, value);
            case "display"         -> s.display         = value;
            case "flex-direction"  -> s.flexDirection   = value;
            case "flex-wrap"       -> s.flexWrap        = value;
            case "align-items"     -> s.alignItems      = value;
            case "justify-content" -> s.justifyContent  = value;

            case "border-top"    -> applyBorderSide(s, value, "top");
            case "border-bottom" -> applyBorderSide(s, value, "bottom");
            case "border-left"   -> applyBorderSide(s, value, "left");
            case "border-right"  -> applyBorderSide(s, value, "right");

            case "text-align"      -> s.textAlign     = value;
            case "text-transform"  -> s.textTransform = value.trim().toLowerCase(java.util.Locale.ROOT);
            case "opacity"         -> s.opacity        = Float.parseFloat(value.trim());
            case "font-family"     -> s.fontFamily     = parseFontFamily(value);
            case "font-size"       -> s.fontSize       = parseFontSize(value);
            case "font-weight"     -> s.fontWeight     = parseFontWeight(value);
            case "overflow"        -> s.overflow       = value.trim().toLowerCase(java.util.Locale.ROOT);
            case "box-sizing"      -> s.boxSizing      = value.trim().toLowerCase(java.util.Locale.ROOT);
            case "align-self"      -> s.alignSelf      = value.trim();

            case "grid-template-columns" -> s.gridTemplateColumns = value.trim().split("\\s+");

            case "--arca-corner-dots" -> applyCornerDots(s, value);

            case "margin-top"    -> {
                if ("auto".equals(value.trim())) s.marginTopAuto = true;
                else s.marginTop = parseLength(value);
            }
        }
    }

    private static void applyLengthDim(ArcaStyle s, String which, String value) {
        boolean pct = value.trim().endsWith("%");
        int v = parseLength(value);
        switch (which) {
            case "width"     -> { s.width = v;     s.widthPercent = pct; }
            case "height"    -> { s.height = v;    s.heightPercent = pct; }
            case "minWidth"  -> { s.minWidth = v;  s.minWidthPercent = pct; }
            case "maxWidth"  -> { s.maxWidth = v;  s.maxWidthPercent = pct; }
            case "minHeight" -> { s.minHeight = v; s.minHeightPercent = pct; }
            case "maxHeight" -> { s.maxHeight = v; s.maxHeightPercent = pct; }
        }
    }

    private static void applyCornerDots(ArcaStyle s, String value) {
        // Format: "<size>px <color>"  (ex: "4px #FFA0642C")
        String[] parts = value.trim().split("\\s+");
        for (String p : parts) {
            if (p.matches("\\d+(px)?")) s.cornerDotSize = parseLength(p);
            else {
                try { s.cornerDotColor = parseColor(p); } catch (Exception ignored) {}
            }
        }
        if (s.cornerDotSize == ArcaStyle.UNSET) s.cornerDotSize = 4;
    }

    private static float parseFontSize(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (v.endsWith("em")) return Float.parseFloat(v.substring(0, v.length() - 2).trim()) * 7f;
            if (v.endsWith("px")) return Float.parseFloat(v.substring(0, v.length() - 2).trim());
            return Float.parseFloat(v);
        } catch (NumberFormatException e) { return ArcaStyle.UNSET_F; }
    }

    private static int parseFontWeight(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "normal" -> 400;
            case "bold"   -> 700;
            case "bolder" -> 800;
            case "lighter"-> 300;
            default -> {
                try { yield Integer.parseInt(v); }
                catch (NumberFormatException e) { yield ArcaStyle.UNSET; }
            }
        };
    }

    private static String parseFontFamily(String value) {
        value = value.toLowerCase(java.util.Locale.ROOT).trim()
                     .replace("'", "").replace("\"", "");
        if (value.equals("fantasy") || value.contains("cormorant") || value.contains("garamond"))
            return "fantasy";
        if (value.equals("mono") || value.contains("jetbrains") || value.contains("ibm plex")
                || value.contains("monospace"))
            return "mono";
        return null;
    }

    private static void applyBorderSide(ArcaStyle s, String value, String side) {
        int color = extractBorderColor(value);
        if (color == ArcaStyle.UNSET) return;
        switch (side) {
            case "top"    -> s.borderTopColor    = color;
            case "bottom" -> s.borderBottomColor = color;
            case "left"   -> s.borderLeftColor   = color;
            case "right"  -> s.borderRightColor  = color;
        }
    }

    private static int extractBorderColor(String value) {
        for (String part : value.trim().split("\\s+")) {
            part = part.trim();
            if (part.isEmpty() || part.equals("solid") || part.equals("dashed")
                    || part.equals("dotted") || part.equals("none")) continue;
            if (part.matches("\\d.*")) continue;
            try { return parseColor(part); } catch (Exception ignored) {}
        }
        return ArcaStyle.UNSET;
    }

    private static void applyBorderShorthand(ArcaStyle s, String value) {
        for (String part : value.trim().split("\\s+")) {
            part = part.trim();
            if (part.isEmpty() || part.equals("solid") || part.equals("dashed") || part.equals("dotted") || part.equals("none")) continue;
            if (part.matches("\\d.*")) {
                s.border = parseLength(part);
            } else {
                try { s.borderColor = parseColor(part); } catch (Exception ignored) {}
            }
        }
        if (s.border == ArcaStyle.UNSET && s.borderColor != ArcaStyle.UNSET) s.border = 1;
    }

    private static void applyPaddingShorthand(ArcaStyle s, String value) {
        int[] vals = parseMultiLength(value);
        switch (vals.length) {
            case 1 -> { s.paddingTop = s.paddingRight = s.paddingBottom = s.paddingLeft = vals[0]; }
            case 2 -> { s.paddingTop = s.paddingBottom = vals[0]; s.paddingRight = s.paddingLeft = vals[1]; }
            case 3 -> { s.paddingTop = vals[0]; s.paddingRight = s.paddingLeft = vals[1]; s.paddingBottom = vals[2]; }
            case 4 -> { s.paddingTop = vals[0]; s.paddingRight = vals[1]; s.paddingBottom = vals[2]; s.paddingLeft = vals[3]; }
        }
    }

    private static void applyMarginShorthand(ArcaStyle s, String value) {
        int[] vals = parseMultiLength(value);
        switch (vals.length) {
            case 1 -> { s.marginTop = s.marginRight = s.marginBottom = s.marginLeft = vals[0]; }
            case 2 -> { s.marginTop = s.marginBottom = vals[0]; s.marginRight = s.marginLeft = vals[1]; }
            case 3 -> { s.marginTop = vals[0]; s.marginRight = s.marginLeft = vals[1]; s.marginBottom = vals[2]; }
            case 4 -> { s.marginTop = vals[0]; s.marginRight = vals[1]; s.marginBottom = vals[2]; s.marginLeft = vals[3]; }
        }
    }

    private static int[] parseMultiLength(String value) {
        String[] parts = value.trim().split("\\s+");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = parseLength(parts[i]);
        return result;
    }

    public static int parseColor(String value) {
        value = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (NAMED_COLORS.containsKey(value)) return NAMED_COLORS.get(value);
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() == 6) return (int) (0xFF000000L | Long.parseLong(hex, 16));
            if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        }
        if (value.startsWith("rgb(") && value.endsWith(")")) {
            int[] c = parseIntList(value.substring(4, value.length() - 1));
            if (c.length == 3) return 0xFF000000 | (clamp(c[0]) << 16) | (clamp(c[1]) << 8) | clamp(c[2]);
        }
        if (value.startsWith("rgba(") && value.endsWith(")")) {
            String inner = value.substring(5, value.length() - 1);
            String[] parts = inner.split(",");
            if (parts.length == 4) {
                int r = clamp(parseInt(parts[0]));
                int g = clamp(parseInt(parts[1]));
                int b = clamp(parseInt(parts[2]));
                int a = (int) (Float.parseFloat(parts[3].trim()) * 255) & 0xFF;
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        throw new IllegalArgumentException("Couleur inconnue: " + value);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static int[] parseIntList(String s) {
        String[] parts = s.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = parseInt(parts[i]);
        return result;
    }

    private static int parseLength(String value) {
        if (value == null) return 0;
        String v = value.trim();
        // extraire le premier entier signé (ex: "-10px" → -10, "1.5em" → 1, "42" → 42)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)").matcher(v);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static int parseInt(String value) {
        if (value == null) return 0;
        String v = value.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)").matcher(v);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static String stripComments(String css) {
        return css.replaceAll("/\\*.*?\\*/", " ");
    }
}
