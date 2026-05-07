# ArcadiaUI

Lightweight HTML/CSS-driven UI framework for NeoForge 1.21.1. No deps beyond `GuiGraphics`.

## Components

| File | Role |
|------|------|
| `ArcadiaWidget` | Core rendering + input contract for all widgets |
| `ArcaElement` | Abstract base widget (position, size, active/pressed state, hover overlay) |
| `ArcaPanel` | Container with row/column/grid flex layout, padding, borders, hover states |
| `ArcaLabel` | Text widget with align, font family, size, weight, transform, ellipsis |
| `ArcaButton` | Clickable button with label, hover brighten, pressed offset |
| `ArcaBadge` | Pill label with auto-sized width and centered text |
| `ArcaIcon` | Square sprite widget with texture or fallback diamond shape |
| `ArcaInput` | Single-line text input with cursor, selection, clipboard, scroll |
| `ArcaInputState` | POJO holding text/cursor/selection/scroll for `ArcaInput` |
| `ArcaScrollList` | Vertical list with scissor clip and scrollbar |
| `ArcaFonts` | Component factory for `fantasy` / `mono` / default font families |
| `ArcaTextStyling` | `text-transform` helper (uppercase / lowercase / capitalize) |
| `Rect` | Axis-aligned bounding box record |
| `ArcaNode` | Parsed HTML element (tag, attrs, children, text) |
| `ArcaHtmlParser` | Minimal HTML parser → `ArcaNode` tree |
| `ArcaSelector` | CSS selector model with descendant + child combinators |
| `ArcaStyle` | Computed style POJO, all CSS properties |
| `ArcaStyleSheet` | Ordered ruleset with class-based and tree-aware resolve |
| `ArcaCssParser` | CSS parser supporting `:root` vars, shorthand, pseudo-classes |
| `ArcaBindingResolver` | `{{ expr }}` resolver: keys, arithmetic, ternary, comparisons |
| `ArcaModel` | Functional interface to resolve binding keys to strings |
| `ArcaForEach` | Expands a node template once per item via `v-for` |
| `ArcaTemplate` | Loaded HTML + CSS pair (from resource pack or string) |
| `ArcaTemplateRenderer` | Builds an `ArcaPanel` tree from a template + model + handlers |

## Usage

```java
ArcaTemplate t = ArcaTemplate.load("arcadia_dungeon:ui/my_screen");
ArcaModel model = ArcaModel.of(Map.of("title", "Hello"));
Map<String, Runnable> handlers = Map.of("confirm", this::onConfirm);
ArcaPanel panel = ArcaTemplateRenderer.build(t, model, handlers, x, y, w, h);
panel.render(g, mx, my);
```

Colours are sourced from `com.arcadia.dungeon.client.util.ArcadiaPalette` (Copper Patina design system).
