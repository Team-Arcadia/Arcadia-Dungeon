/**
 * Couche réseau — payloads par agrégat (cf. architecture-v1 §6).
 *
 * <p>S2C (à implémenter) :
 * <ul>
 *   <li>RunStatePayload (S2.4)</li>
 *   <li>DungeonListPayload (S6.2)</li>
 *   <li>DungeonConfigPayload (S6.4)</li>
 *   <li>PlayerProgressPayload (S5.1)</li>
 * </ul>
 *
 * <p>C2S (à implémenter) :
 * <ul>
 *   <li>RequestDungeonListPayload</li>
 *   <li>RequestPlayerProgressPayload</li>
 *   <li>StartRunPayload (S2.5)</li>
 *   <li>JoinRunPayload (S3.2)</li>
 *   <li>AbandonRunPayload</li>
 *   <li>RequestRunResyncPayload (S3.4)</li>
 * </ul>
 *
 * <p>Règle Zero Trust : aucun payload C2S ne transporte de valeur de gameplay (cf. archi §6.3).
 */
package com.arcadia.dungeon.network;
