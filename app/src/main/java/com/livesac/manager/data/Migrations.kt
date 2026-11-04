package com.livesac.manager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Instructions SQL de la migration 1 -> 2 (sans Android, pour pouvoir les vérifier).
 * Aucune donnée n'est supprimée : on ajoute seulement des colonnes.
 *  - lives.kind : déduit du jour de la semaine de la date (0 = dimanche, 3 = mercredi, 6 = samedi) ;
 *    les autres jours deviennent « OTHER » (Lives indépendants, comme avant).
 *  - clients.packed / clients.deliveryDay : emballage et livraison.
 */
object MigrationSql {
    val V1_TO_V2: List<String> = listOf(
        "ALTER TABLE lives ADD COLUMN kind TEXT NOT NULL DEFAULT 'OTHER'",
        "UPDATE lives SET kind = CASE CAST(strftime('%w', dateMillis / 1000, 'unixepoch') AS INTEGER) " +
            "WHEN 3 THEN 'WED' WHEN 6 THEN 'SAT' WHEN 0 THEN 'SUN' ELSE 'OTHER' END",
        "ALTER TABLE clients ADD COLUMN packed INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE clients ADD COLUMN deliveryDay TEXT"
    )
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
    }
}
