package com.arcadia.dungeon.client.arcadiaui;

import com.arcadia.dungeon.client.util.ArcadiaPalette;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ArcaTemplateRenderer {

    private static final int COPPER_LO = ArcadiaPalette.COPPER_LO;

    private ArcaTemplateRenderer() {}

    public static ArcaPanel build(ArcaTemplate template, ArcaModel model,
                                  Map<String, Runnable> handlers,
                                  int x, int y, int w, int h) {
        return build(template, model, handlers, Map.of(), x, y, w, h);
    }

    public static ArcaPanel build(ArcaTemplate template, ArcaModel model,
                                  Map<String, Runnable> handlers,
                                  Map<String, Consumer<String>> inputHandlers,
                                  int x, int y, int w, int h) {
        return build(template, model, handlers, inputHandlers, null, x, y, w, h);
    }

    public static ArcaPanel build(ArcaTemplate template, ArcaModel model,
                                  Map<String, Runnable> handlers,
                                  Map<String, Consumer<String>> inputHandlers,
                                  Map<String, ArcaInputState> inputStates,
                                  int x, int y, int w, int h) {
        Deque<ArcaNode> ancestors = new ArrayDeque<>();
        return buildNode(template.root(), template.styleSheet(), model, handlers, inputHandlers, inputStates, ancestors, x, y, w, h);
    }

    private static ArcaPanel buildNode(ArcaNode node, ArcaStyleSheet sheet,
                                       ArcaModel model, Map<String, Runnable> handlers,
                                       Map<String, Consumer<String>> inputHandlers,
                                       Map<String, ArcaInputState> inputStates,
                                       Deque<ArcaNode> ancestors,
                                       int x, int y, int w, int h) {
        ArcaStyle style = sheet.resolve(node, ancestors);
        ArcaStyle hoverStyle = sheet.resolveHover(node, ancestors);

        boolean isRow  = isRowMode(node, style);
        boolean isGrid = node.tag().equals("grid") || (style.gridTemplateColumns != null);

        int padT = style.paddingTop    != ArcaStyle.UNSET ? style.paddingTop    : 0;
        int padR = style.paddingRight  != ArcaStyle.UNSET ? style.paddingRight  : 0;
        int padB = style.paddingBottom != ArcaStyle.UNSET ? style.paddingBottom : 0;
        int padL = style.paddingLeft   != ArcaStyle.UNSET ? style.paddingLeft   : 0;

        int gapVal = style.gap != ArcaStyle.UNSET ? style.gap : 0;

        int borderT = (style.border != ArcaStyle.UNSET) ? style.border : 0;

        ArcaPanel panel;
        if (isGrid) {
            int cols = parseIntAttr(node.attr("cols"), 2);
            panel = ArcaPanel.grid(cols, x, y, w, h);
            if (style.gridTemplateColumns != null) panel.gridTemplateColumns(style.gridTemplateColumns);
        } else if (isRow) {
            panel = ArcaPanel.row(x, y, w, h);
        } else {
            panel = ArcaPanel.column(x, y, w, h);
        }

        panel.gap(gapVal);
        if (padT > 0 || padR > 0 || padB > 0 || padL > 0)
            panel.padding(padT, padR, padB, padL);
        if (style.background != ArcaStyle.UNSET) panel.background(style.background);
        if (style.border     != ArcaStyle.UNSET && style.borderColor != ArcaStyle.UNSET)
            panel.border(style.border, style.borderColor);
        if (style.justifyContent != null) panel.justifyContent(style.justifyContent);
        if (style.alignItems     != null) panel.alignItems(style.alignItems);
        if ("wrap".equals(style.flexWrap)) panel.wrap(true);
        if ("hidden".equals(style.overflow)) panel.clip(true);
        if (style.borderTopColor    != ArcaStyle.UNSET) panel.borderSide("top",    style.borderTopColor);
        if (style.borderBottomColor != ArcaStyle.UNSET) panel.borderSide("bottom", style.borderBottomColor);
        if (style.borderLeftColor   != ArcaStyle.UNSET) panel.borderSide("left",   style.borderLeftColor);
        if (style.borderRightColor  != ArcaStyle.UNSET) panel.borderSide("right",  style.borderRightColor);

        if (style.opacity != ArcaStyle.UNSET_F) panel.opacity(style.opacity);

        if (hoverStyle.background != ArcaStyle.UNSET) panel.hoverBackground(hoverStyle.background);
        if (hoverStyle.borderColor != ArcaStyle.UNSET) panel.hoverBorder(hoverStyle.borderColor);
        if (hoverStyle.borderTopColor    != ArcaStyle.UNSET) panel.hoverBorderSide("top",    hoverStyle.borderTopColor);
        if (hoverStyle.borderBottomColor != ArcaStyle.UNSET) panel.hoverBorderSide("bottom", hoverStyle.borderBottomColor);
        if (hoverStyle.borderLeftColor   != ArcaStyle.UNSET) panel.hoverBorderSide("left",   hoverStyle.borderLeftColor);
        if (hoverStyle.borderRightColor  != ArcaStyle.UNSET) panel.hoverBorderSide("right",  hoverStyle.borderRightColor);

        if (style.cornerDotSize != ArcaStyle.UNSET && style.cornerDotSize > 0) {
            int dotColor = style.cornerDotColor != ArcaStyle.UNSET ? style.cornerDotColor
                         : style.borderColor != ArcaStyle.UNSET ? style.borderColor : COPPER_LO;
            panel.cornerDots(style.cornerDotSize, dotColor);
        } else if (node.classNames().contains("arc-panel")) {
            int dotColor = style.borderColor != ArcaStyle.UNSET ? style.borderColor : COPPER_LO;
            panel.cornerDots(4, dotColor);
        }

        String onClickHandler = node.onClickHandler();
        if (!onClickHandler.isEmpty() && handlers.containsKey(onClickHandler))
            panel.onClick(handlers.get(onClickHandler));

        int innerW = w - padL - padR;
        int innerH = h - padT - padB;
        if ("border-box".equals(style.boxSizing)) {
            innerW -= 2 * borderT;
            innerH -= 2 * borderT;
        }

        boolean stretchChildWidth  = !isRow && !isGrid;
        boolean stretchChildHeight = isRow;

        ancestors.push(node);
        try {
            for (ArcaNode child : expandChildren(node, sheet, model)) {
                ArcaStyle childStyle = sheet.resolve(child, ancestors);
                ArcadiaWidget widget = buildWidget(child, sheet, model, handlers, inputHandlers, inputStates, ancestors,
                        innerW, innerH, stretchChildWidth, stretchChildHeight);
                if (widget == null) continue;
                int flex = childStyle.flex != ArcaStyle.UNSET ? childStyle.flex : 0;
                panel.add(widget, flex, childStyle.alignSelf, childStyle.marginTopAuto);
            }
        } finally {
            ancestors.pop();
        }
        panel.layout();
        return panel;
    }

    private static boolean isRowMode(ArcaNode node, ArcaStyle style) {
        if (node.tag().equals("row")) return true;
        return "row".equals(style.flexDirection);
    }

    private static List<ArcaNode> expandChildren(ArcaNode parent, ArcaStyleSheet sheet, ArcaModel model) {
        return parent.children().stream()
            .flatMap(child -> {
                String vFor = child.vFor();
                if (!vFor.isEmpty() && vFor.contains(" in ")) {
                    String[] parts = vFor.split(" in ", 2);
                    String varName = parts[0].trim();
                    String listKey = parts[1].trim();
                    String resolved = model.resolve(listKey);
                    if (resolved == null) return java.util.stream.Stream.of();
                    int count = parseIntAttr(resolved, 0);
                    java.util.List<ArcaModel> items = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        final int idx = i;
                        items.add(k -> model.resolve(varName + "." + k + "." + idx));
                    }
                    return ArcaForEach.expand(child, items, varName).stream();
                }
                String cls = child.attr("class");
                if (cls.contains("{{")) {
                    return java.util.stream.Stream.of(ArcaForEach.resolveAttrs(child, model));
                }
                return java.util.stream.Stream.of(child);
            })
            .toList();
    }

    private static int resolveLength(int raw, boolean isPercent, int basis, int fallback) {
        if (raw == ArcaStyle.UNSET) return fallback;
        if (isPercent) {
            if (basis <= 0) return fallback;
            return basis * raw / 100;
        }
        return raw;
    }

    private static ArcadiaWidget buildWidget(ArcaNode node, ArcaStyleSheet sheet,
                                             ArcaModel model, Map<String, Runnable> handlers,
                                             Map<String, Consumer<String>> inputHandlers,
                                             Map<String, ArcaInputState> inputStates,
                                             Deque<ArcaNode> ancestors,
                                             int availW, int availH,
                                             boolean inheritWidth, boolean inheritHeight) {
        ArcaStyle style = sheet.resolve(node, ancestors);

        int wRaw = resolveLength(style.width, style.widthPercent, availW, ArcaStyle.UNSET);
        int hRaw = resolveLength(style.height, style.heightPercent, availH, ArcaStyle.UNSET);

        int wVal = wRaw != ArcaStyle.UNSET ? wRaw
                 : inheritWidth  && availW > 0 ? availW
                 : 0;
        int hVal = hRaw != ArcaStyle.UNSET ? hRaw
                 : inheritHeight && availH > 0 ? availH
                 : naturalHeight(style);

        int minW = resolveLength(style.minWidth, style.minWidthPercent, availW, ArcaStyle.UNSET);
        int maxW = resolveLength(style.maxWidth, style.maxWidthPercent, availW, ArcaStyle.UNSET);
        int minH = resolveLength(style.minHeight, style.minHeightPercent, availH, ArcaStyle.UNSET);
        int maxH = resolveLength(style.maxHeight, style.maxHeightPercent, availH, ArcaStyle.UNSET);

        if (minW != ArcaStyle.UNSET && wVal < minW) wVal = minW;
        if (maxW != ArcaStyle.UNSET && wVal > maxW) wVal = maxW;
        if (minH != ArcaStyle.UNSET && hVal < minH) hVal = minH;
        if (maxH != ArcaStyle.UNSET && hVal > maxH) hVal = maxH;

        float fontSizePx = style.fontSize != ArcaStyle.UNSET_F ? style.fontSize : 7f;
        int fontWeight = style.fontWeight != ArcaStyle.UNSET ? style.fontWeight : 400;
        float opacityVal = style.opacity != ArcaStyle.UNSET_F ? style.opacity : 1f;

        return switch (node.tag()) {
            case "div", "row", "col", "grid" -> {
                boolean explicitW = wRaw != ArcaStyle.UNSET;
                int initialW = wVal > 0 ? wVal : availW;
                ArcaPanel built = buildNode(node, sheet, model, handlers, inputHandlers, inputStates, ancestors, 0, 0, initialW, hVal);
                if (!explicitW && !inheritWidth) {
                    int natural = built.fitContentWidth();
                    if (natural > 0 && natural < built.getWidth()) {
                        built.setSize(natural, built.getHeight());
                        built.layout();
                    }
                }
                yield built;
            }

            case "input" -> {
                String placeholder = ArcaBindingResolver.resolve(node.attr("placeholder"), model);
                String initialValue = ArcaBindingResolver.resolve(node.attr("value"), model);
                int maxLen = parseIntAttr(node.attr("maxlength"), 64);
                int iw = wVal > 0 ? wVal : (inheritWidth && availW > 0 ? availW : 100);
                int ih = hVal > 0 ? hVal : (style.fontSize != ArcaStyle.UNSET_F && style.fontSize > 0 ? (int) style.fontSize + 6 : 14);
                var input = new ArcaInput(0, 0, iw, ih);
                String idAttr = node.attr("id");
                if (idAttr != null && !idAttr.isEmpty()) input.inputId(idAttr);
                if (inputStates != null && idAttr != null && !idAttr.isEmpty()) {
                    ArcaInputState st = inputStates.get(idAttr);
                    if (st == null) {
                        st = new ArcaInputState();
                        if (initialValue != null && !initialValue.isEmpty()) st.text = initialValue;
                        inputStates.put(idAttr, st);
                    }
                    input.state(st);
                } else if (initialValue != null && !initialValue.isEmpty()) {
                    input.text(initialValue);
                }
                if (placeholder != null && !placeholder.isEmpty()) input.placeholder(placeholder);
                input.maxLength(maxLen);
                if (style.background  != ArcaStyle.UNSET) input.bgColor(style.background);
                if (style.borderColor != ArcaStyle.UNSET) input.borderColor(style.borderColor);
                if (style.color       != ArcaStyle.UNSET) input.textColor(style.color);
                int padHv = style.paddingLeft != ArcaStyle.UNSET ? style.paddingLeft : 5;
                int padVv = style.paddingTop  != ArcaStyle.UNSET ? style.paddingTop  : 3;
                input.padding(padHv, padVv);
                if (style.fontFamily != null) input.font(style.fontFamily, fontSizePx);
                else if (style.fontSize != ArcaStyle.UNSET_F) input.font(null, fontSizePx);
                input.fontWeight(fontWeight);
                String onInputName  = node.attr("oninput");
                String onSubmitName = node.attr("onsubmit");
                if (onInputName != null && !onInputName.isEmpty() && inputHandlers != null && inputHandlers.containsKey(onInputName))
                    input.onChange(inputHandlers.get(onInputName));
                if (onSubmitName != null && !onSubmitName.isEmpty() && inputHandlers != null && inputHandlers.containsKey(onSubmitName))
                    input.onSubmit(inputHandlers.get(onSubmitName));
                yield input;
            }

            case "button" -> {
                String text = ArcaBindingResolver.resolve(node.text(), model);
                int bw;
                if (wRaw != ArcaStyle.UNSET) {
                    bw = wVal;
                } else {
                    var fontMc = net.minecraft.client.Minecraft.getInstance().font;
                    String displayed = ArcaTextStyling.transform(text, style.textTransform);
                    var compMeasure = ArcaFonts.component(displayed, style.fontFamily, fontWeight);
                    int textW = (int) Math.ceil(fontMc.width(compMeasure) * (fontSizePx / ArcaFonts.naturalPx(style.fontFamily)));
                    int padHTotal = (style.paddingLeft  != ArcaStyle.UNSET ? style.paddingLeft  : 4)
                                  + (style.paddingRight != ArcaStyle.UNSET ? style.paddingRight : 4);
                    bw = textW + padHTotal + 4;
                    if (minW != ArcaStyle.UNSET && bw < minW) bw = minW;
                    if (maxW != ArcaStyle.UNSET && bw > maxW) bw = maxW;
                }
                var btn = new ArcaButton(0, 0, bw, hVal).label(text);
                if (style.background != ArcaStyle.UNSET) btn.bgColor(style.background);
                if (style.color      != ArcaStyle.UNSET) btn.labelColor(style.color);
                if (style.textAlign  != null)             btn.textAlign(style.textAlign);
                if (style.fontFamily != null)             btn.font(style.fontFamily);
                btn.fontSize(fontSizePx).fontWeight(fontWeight).opacity(opacityVal);
                if (style.textTransform != null)          btn.textTransform(style.textTransform);
                String handler = node.onClickHandler();
                if (!handler.isEmpty() && handlers.containsKey(handler))
                    btn.onClick(handlers.get(handler));
                yield btn;
            }

            case "label" -> {
                String text = ArcaBindingResolver.resolve(node.text(), model);
                int color   = style.color != ArcaStyle.UNSET ? style.color : ArcadiaPalette.CREAM;
                int lw      = wVal > 0 ? wVal : 80;
                var lbl     = new ArcaLabel(0, 0, lw, hVal, text).color(color);
                if (style.textAlign  != null) lbl.textAlign(style.textAlign);
                if (style.fontFamily != null) lbl.font(style.fontFamily);
                lbl.fontSize(fontSizePx).fontWeight(fontWeight).opacity(opacityVal);
                if (style.textTransform != null) lbl.textTransform(style.textTransform);
                if ("hidden".equals(style.overflow)) lbl.clipOverflow(true);
                yield lbl;
            }

            case "badge" -> {
                String text = ArcaBindingResolver.resolve(node.text(), model);
                int bg = style.background != ArcaStyle.UNSET ? style.background : ArcadiaPalette.BG2;
                int fg = style.color      != ArcaStyle.UNSET ? style.color      : ArcadiaPalette.CREAM;
                int padH = (style.paddingLeft  != ArcaStyle.UNSET ? style.paddingLeft  : 5)
                         + (style.paddingRight != ArcaStyle.UNSET ? style.paddingRight : 5);
                var badge = new ArcaBadge(0, 0, hVal, text, bg).textColor(fg).paddingH(padH);
                if (style.border     != ArcaStyle.UNSET && style.borderColor != ArcaStyle.UNSET)
                    badge.border(style.border, style.borderColor);
                if (style.fontFamily != null) badge.font(style.fontFamily);
                badge.fontSize(fontSizePx).fontWeight(fontWeight).opacity(opacityVal);
                if (style.textTransform != null) badge.textTransform(style.textTransform);
                yield badge;
            }

            case "icon" -> {
                var icon = new ArcaIcon(0, 0, hVal, hVal);
                int tint = style.color != ArcaStyle.UNSET ? style.color : ArcadiaPalette.COPPER_HI;
                icon.tint(tint).size(hVal, hVal);
                String src = ArcaBindingResolver.resolve(node.attr("src"), model);
                if (!src.isEmpty()) {
                    icon.texture(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "arcadia_dungeon", "textures/gui/icons/" + src + ".png"));
                }
                String handler = node.onClickHandler();
                if (!handler.isEmpty() && handlers.containsKey(handler))
                    icon.onClick(handlers.get(handler));
                yield icon;
            }

            default -> null;
        };
    }

    private static int naturalHeight(ArcaStyle style) {
        int padV = 0;
        if (style.paddingTop    != ArcaStyle.UNSET) padV += style.paddingTop;
        if (style.paddingBottom != ArcaStyle.UNSET) padV += style.paddingBottom;
        int textH = style.fontSize != ArcaStyle.UNSET_F
                ? (int) Math.ceil(style.fontSize)
                : 8;
        return Math.max(12, textH + padV + 4);
    }

    private static int parseIntAttr(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
