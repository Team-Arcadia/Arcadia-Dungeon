package com.arcadia.dungeon.client.arcadiaui;

public final class ArcaStyle {

    public static final int    UNSET   = Integer.MIN_VALUE;
    public static final float  UNSET_F = Float.MIN_VALUE;
    public static final ArcaStyle EMPTY = new ArcaStyle();

    // Couleurs
    public int background  = UNSET;
    public int color       = UNSET;
    public int borderColor = UNSET;

    // Bordures par côté
    public int borderTopColor    = UNSET;
    public int borderBottomColor = UNSET;
    public int borderLeftColor   = UNSET;
    public int borderRightColor  = UNSET;

    // Dimensions
    public int width    = UNSET;
    public int height   = UNSET;
    public int minWidth = UNSET;
    public int maxWidth = UNSET;
    public int minHeight = UNSET;
    public int maxHeight = UNSET;

    // Pourcentage (true → la valeur correspondante est un pourcent du parent)
    public boolean widthPercent     = false;
    public boolean heightPercent    = false;
    public boolean minWidthPercent  = false;
    public boolean maxWidthPercent  = false;
    public boolean minHeightPercent = false;
    public boolean maxHeightPercent = false;

    // Padding par côté
    public int paddingTop    = UNSET;
    public int paddingRight  = UNSET;
    public int paddingBottom = UNSET;
    public int paddingLeft   = UNSET;

    // Margin par côté
    public int     marginTop    = UNSET;
    public int     marginRight  = UNSET;
    public int     marginBottom = UNSET;
    public int     marginLeft   = UNSET;
    public boolean marginTopAuto = false;

    // Layout
    public int gap  = UNSET;
    public int flex = UNSET;
    public int border = UNSET;

    // Texte
    public String textAlign = null;
    public String textTransform = null;       // "uppercase" | "lowercase" | "capitalize"
    public float  fontSize = UNSET_F;          // px (em résolu en parsing : em * 7)
    public int    fontWeight = UNSET;          // 400 normal, 700 bold

    // Visuel
    public float  opacity  = UNSET_F;
    public String overflow = null;

    // Flexbox
    public String display        = null;
    public String flexDirection  = null;
    public String flexWrap       = null;
    public String alignItems     = null;
    public String alignSelf      = null;
    public String justifyContent = null;

    // Grid
    public String[] gridTemplateColumns = null;

    // Box model
    public String boxSizing = "border-box";

    // Décorations panneau
    public int cornerDotSize  = UNSET;
    public int cornerDotColor = UNSET;

    // Hover (panels/divs)
    public int hoverBackground       = UNSET;
    public int hoverBorderColor      = UNSET;
    public int hoverBorderTopColor    = UNSET;
    public int hoverBorderBottomColor = UNSET;
    public int hoverBorderLeftColor   = UNSET;
    public int hoverBorderRightColor  = UNSET;
    public int hoverColor            = UNSET;

    // Typographie
    public String fontFamily = null;

    public ArcaStyle() {}

    public int padding() { return paddingTop != UNSET ? paddingTop : UNSET; }
    public int margin()  { return marginTop  != UNSET ? marginTop  : UNSET; }

    public ArcaStyle merge(ArcaStyle other) {
        ArcaStyle r = new ArcaStyle();

        r.background  = other.background  != UNSET ? other.background  : this.background;
        r.color       = other.color       != UNSET ? other.color       : this.color;
        r.borderColor = other.borderColor != UNSET ? other.borderColor : this.borderColor;

        r.borderTopColor    = other.borderTopColor    != UNSET ? other.borderTopColor    : this.borderTopColor;
        r.borderBottomColor = other.borderBottomColor != UNSET ? other.borderBottomColor : this.borderBottomColor;
        r.borderLeftColor   = other.borderLeftColor   != UNSET ? other.borderLeftColor   : this.borderLeftColor;
        r.borderRightColor  = other.borderRightColor  != UNSET ? other.borderRightColor  : this.borderRightColor;

        if (other.width != UNSET)     { r.width = other.width;       r.widthPercent     = other.widthPercent;     } else { r.width = this.width;       r.widthPercent     = this.widthPercent;     }
        if (other.height != UNSET)    { r.height = other.height;     r.heightPercent    = other.heightPercent;    } else { r.height = this.height;     r.heightPercent    = this.heightPercent;    }
        if (other.minWidth != UNSET)  { r.minWidth = other.minWidth; r.minWidthPercent  = other.minWidthPercent;  } else { r.minWidth = this.minWidth; r.minWidthPercent  = this.minWidthPercent;  }
        if (other.maxWidth != UNSET)  { r.maxWidth = other.maxWidth; r.maxWidthPercent  = other.maxWidthPercent;  } else { r.maxWidth = this.maxWidth; r.maxWidthPercent  = this.maxWidthPercent;  }
        if (other.minHeight != UNSET) { r.minHeight= other.minHeight;r.minHeightPercent = other.minHeightPercent; } else { r.minHeight= this.minHeight;r.minHeightPercent = this.minHeightPercent; }
        if (other.maxHeight != UNSET) { r.maxHeight= other.maxHeight;r.maxHeightPercent = other.maxHeightPercent; } else { r.maxHeight= this.maxHeight;r.maxHeightPercent = this.maxHeightPercent; }

        r.paddingTop    = other.paddingTop    != UNSET ? other.paddingTop    : this.paddingTop;
        r.paddingRight  = other.paddingRight  != UNSET ? other.paddingRight  : this.paddingRight;
        r.paddingBottom = other.paddingBottom != UNSET ? other.paddingBottom : this.paddingBottom;
        r.paddingLeft   = other.paddingLeft   != UNSET ? other.paddingLeft   : this.paddingLeft;

        r.marginTop    = other.marginTop    != UNSET ? other.marginTop    : this.marginTop;
        r.marginRight  = other.marginRight  != UNSET ? other.marginRight  : this.marginRight;
        r.marginBottom = other.marginBottom != UNSET ? other.marginBottom : this.marginBottom;
        r.marginLeft   = other.marginLeft   != UNSET ? other.marginLeft   : this.marginLeft;
        r.marginTopAuto = other.marginTopAuto || this.marginTopAuto;

        r.gap    = other.gap    != UNSET ? other.gap    : this.gap;
        r.flex   = other.flex   != UNSET ? other.flex   : this.flex;
        r.border = other.border != UNSET ? other.border : this.border;

        r.textAlign     = other.textAlign     != null ? other.textAlign     : this.textAlign;
        r.textTransform = other.textTransform != null ? other.textTransform : this.textTransform;
        r.fontSize      = other.fontSize      != UNSET_F ? other.fontSize : this.fontSize;
        r.fontWeight    = other.fontWeight    != UNSET ? other.fontWeight : this.fontWeight;
        r.opacity       = other.opacity       != UNSET_F ? other.opacity : this.opacity;
        r.overflow      = other.overflow      != null ? other.overflow  : this.overflow;

        r.display        = other.display        != null ? other.display        : this.display;
        r.flexDirection  = other.flexDirection  != null ? other.flexDirection  : this.flexDirection;
        r.flexWrap       = other.flexWrap       != null ? other.flexWrap       : this.flexWrap;
        r.alignItems     = other.alignItems     != null ? other.alignItems     : this.alignItems;
        r.alignSelf      = other.alignSelf      != null ? other.alignSelf      : this.alignSelf;
        r.justifyContent = other.justifyContent != null ? other.justifyContent : this.justifyContent;
        r.fontFamily     = other.fontFamily     != null ? other.fontFamily     : this.fontFamily;

        r.gridTemplateColumns = other.gridTemplateColumns != null ? other.gridTemplateColumns : this.gridTemplateColumns;
        r.boxSizing = other.boxSizing != null ? other.boxSizing : this.boxSizing;

        r.cornerDotSize  = other.cornerDotSize  != UNSET ? other.cornerDotSize  : this.cornerDotSize;
        r.cornerDotColor = other.cornerDotColor != UNSET ? other.cornerDotColor : this.cornerDotColor;

        r.hoverBackground        = other.hoverBackground        != UNSET ? other.hoverBackground        : this.hoverBackground;
        r.hoverBorderColor       = other.hoverBorderColor       != UNSET ? other.hoverBorderColor       : this.hoverBorderColor;
        r.hoverBorderTopColor    = other.hoverBorderTopColor    != UNSET ? other.hoverBorderTopColor    : this.hoverBorderTopColor;
        r.hoverBorderBottomColor = other.hoverBorderBottomColor != UNSET ? other.hoverBorderBottomColor : this.hoverBorderBottomColor;
        r.hoverBorderLeftColor   = other.hoverBorderLeftColor   != UNSET ? other.hoverBorderLeftColor   : this.hoverBorderLeftColor;
        r.hoverBorderRightColor  = other.hoverBorderRightColor  != UNSET ? other.hoverBorderRightColor  : this.hoverBorderRightColor;
        r.hoverColor             = other.hoverColor             != UNSET ? other.hoverColor             : this.hoverColor;

        return r;
    }
}
