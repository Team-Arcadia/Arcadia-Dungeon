/**
 * File-based configuration persistence.
 *
 * <p>Dungeon definitions, placement slots and admin-authored free classes stay
 * as editable config files. Player-owned runtime state lives in SQLite via
 * {@link com.arcadia.dungeon.services.ArcadiaDatabaseService}.
 */
package com.arcadia.dungeon.persistence;
