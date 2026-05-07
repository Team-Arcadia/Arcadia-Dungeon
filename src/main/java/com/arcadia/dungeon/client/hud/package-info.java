/**
 * Mini overlay HUD in-run — rendu direct {@code GuiGraphics} via NeoForge
 * {@code RegisterGuiLayersEvent}.
 *
 * <p>NE PAS utiliser ArcadiaUI ici (incompatible — pas de {@code position: absolute},
 * et ArcadiaUI rend dans un Screen, pas un GUI layer).
 *
 * <p>À implémenter :
 * <ul>
 *   <li>RunOverlayHud (S6.5) — chrono, lives, salle</li>
 * </ul>
 *
 * @see <a href="../../../../../../../../_bmad-output/planning-artifacts/ux-v1/design-system.md">ux-v1/design-system.md §7</a>
 */
package com.arcadia.dungeon.client.hud;
