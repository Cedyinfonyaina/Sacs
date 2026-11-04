package com.livesac.manager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.livesac.manager.data.AppDatabase
import com.livesac.manager.data.MIGRATION_1_2
import com.livesac.manager.data.Repository
import com.livesac.manager.domain.BackupCodec
import com.livesac.manager.domain.DeliveryDay
import com.livesac.manager.domain.LiveKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** À lancer sur un téléphone ou un émulateur : persistance, migration 1 -> 2, sauvegarde/restauration. */
@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "test_live_sac.db"

    @Before fun clean() { context.deleteDatabase(dbName) }
    @After fun cleanup() { context.deleteDatabase(dbName) }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(MIGRATION_1_2).build()

    @Test
    fun dataSurvivesClosingTheDatabase() = runBlocking {
        var db = open()
        var repo = Repository(db)
        val sat = repo.createLive("LIVE SAMEDI", 1_793_404_800_000, LiveKind.SAT)
        repo.addBags(sat, "Marie", listOf(com.livesac.manager.data.NewBag("JP1", 25_000)))
        db.close()

        db = open(); repo = Repository(db)
        val groups = repo.observePackaging().first().groups
        assertEquals(1, groups.size)
        assertEquals(25_000L, groups.single().total)
        db.close()
    }

    @Test
    fun migrationFromVersion1KeepsExistingData() = runBlocking {
        // Base « ancienne version » créée à la main (schéma v1).
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL("CREATE TABLE lives (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, dateMillis INTEGER NOT NULL)")
        raw.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, liveId INTEGER NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, FOREIGN KEY(liveId) REFERENCES lives(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        raw.execSQL("CREATE INDEX index_clients_liveId ON clients (liveId)")
        raw.execSQL("CREATE INDEX index_clients_liveId_nameKey ON clients (liveId, nameKey)")
        raw.execSQL("CREATE TABLE bags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, clientId INTEGER NOT NULL, jpCode TEXT NOT NULL, price INTEGER NOT NULL, FOREIGN KEY(clientId) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        raw.execSQL("CREATE INDEX index_bags_clientId ON bags (clientId)")
        raw.execSQL("INSERT INTO lives VALUES (1, 'LIVE SAMEDI', 1793404800000)") // samedi 31/10/2026
        raw.execSQL("INSERT INTO clients VALUES (1, 1, 'Marie', 'marie')")
        raw.execSQL("INSERT INTO bags VALUES (1, 1, 'JP1', 25000)")
        raw.version = 1
        raw.close()

        val db = open()
        val repo = Repository(db)
        val group = repo.observePackaging().first().groups.single()
        assertEquals("Marie", group.name)
        assertEquals(25_000L, group.total)
        assertEquals(LiveKind.SAT, group.lives.single().kind)
        assertTrue(!group.packed && group.deliveryDay == null)
        db.close()
    }

    @Test
    fun backupAndRestoreRoundTrip() = runBlocking {
        val db = open()
        val repo = Repository(db)
        val sat = repo.createLive("S", 1_793_404_800_000, LiveKind.SAT)
        repo.addBags(sat, "Marie", listOf(com.livesac.manager.data.NewBag("JP1", 10_000)))
        val group = repo.observePackaging().first().groups.single()
        repo.setPacked(group, true)
        repo.setDelivery(group, DeliveryDay.SUN)
        val json = repo.exportBackupJson()

        repo.addBags(sat, "Autre", listOf(com.livesac.manager.data.NewBag("JP2", 1)))
        repo.restoreBackup(BackupCodec.decode(json))

        val restored = repo.observePackaging().first().groups.single()
        assertEquals("Marie", restored.name)
        assertTrue(restored.packed)
        assertEquals(DeliveryDay.SUN, restored.deliveryDay)
        db.close()
    }
}
