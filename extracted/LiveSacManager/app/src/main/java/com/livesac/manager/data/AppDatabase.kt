package com.livesac.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LiveEntity::class, ClientEntity::class, BagEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "live_sac_manager.db"
                )
                    // Jamais de suppression automatique : on migre toujours les données existantes.
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
