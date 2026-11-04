package com.livesac.manager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupAuthTest {

    private class MemoryStore : SecretStore {
        val map = LinkedHashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    // Peu d'itérations : tests rapides (le vrai code utilise 120 000).
    private fun auth(store: SecretStore = MemoryStore()) = AdminAuth(store, iterations = 1_000)

    // ---- Mot de passe administrateur ----

    @Test
    fun password_notConfiguredUntilSet_thenVerified() {
        val a = auth()
        assertFalse(a.isConfigured())
        assertFalse(a.verify("anything"))
        a.setPassword("secret-123")
        assertTrue(a.isConfigured())
        assertTrue(a.verify("secret-123"))
        assertFalse(a.verify("Secret-123"))
        assertFalse(a.verify(""))
    }

    @Test
    fun password_isNeverStoredInPlainText() {
        val store = MemoryStore()
        auth(store).setPassword("MonMotDePasse!")
        assertTrue(store.map.isNotEmpty())
        store.map.values.forEach { assertFalse(it.contains("MonMotDePasse")) }
    }

    @Test
    fun password_isSalted() {
        val s1 = MemoryStore()
        val s2 = MemoryStore()
        auth(s1).setPassword("same-password")
        auth(s2).setPassword("same-password")
        assertTrue(s1.map["hash"] != s2.map["hash"])
        assertTrue(s1.map["salt"] != s2.map["salt"])
    }

    @Test
    fun password_changeRequiresOldPassword() {
        val a = auth()
        a.setPassword("old-pass")
        assertFalse(a.change("wrong", "new-pass"))
        assertTrue(a.verify("old-pass"))
        assertTrue(a.change("old-pass", "new-pass"))
        assertTrue(a.verify("new-pass"))
        assertFalse(a.verify("old-pass"))
    }

    @Test
    fun password_tooShortIsRejected() {
        try {
            auth().setPassword("abc")
            fail("devrait être refusé")
        } catch (e: IllegalArgumentException) {
            // attendu
        }
    }

    @Test
    fun password_survivesRestartBecauseOnlyTheStoreMatters() {
        val store = MemoryStore()
        auth(store).setPassword("persist-me")
        assertTrue(auth(store).verify("persist-me")) // nouvelle instance, même stockage
    }

    // ---- Sauvegarde / restauration ----

    private fun sample() = BackupData(
        exportedAt = "2026-11-04T08:00:00Z",
        lives = listOf(
            BackupLive(1, "LIVE \"SAMEDI\" é\n", 1_793_836_800_000, "SAT"),
            BackupLive(2, "LIVE DIMANCHE", 1_793_923_200_000, "SUN")
        ),
        clients = listOf(
            BackupClient(5, 1, "Marie Rakoto 😀", true, "WED"),
            BackupClient(6, 2, "marie rakoto 😀", false, null)
        ),
        bags = listOf(BackupBag(9, 5, "JP12", 25_000), BackupBag(10, 6, "JP\\7", 40_000))
    )

    @Test
    fun backup_roundTripKeepsEverything() {
        val data = sample()
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }

    @Test
    fun backup_containsPackagingAndDeliveryInfo() {
        val json = BackupCodec.encode(sample())
        assertTrue(json.contains("\"packed\": true"))
        assertTrue(json.contains("\"deliveryDay\": \"WED\""))
        assertTrue(json.contains("\"format\": \"LiveSacManager\""))
    }

    private fun assertRejected(text: String) {
        try {
            BackupCodec.decode(text)
            fail("la sauvegarde invalide aurait dû être refusée : $text")
        } catch (e: BackupException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun backup_invalidFilesAreRejectedBeforeAnyChange() {
        assertRejected("pas du json")
        assertRejected("[]")
        assertRejected("{\"format\":\"Autre\",\"version\":2}")
        assertRejected("{\"format\":\"LiveSacManager\",\"version\":99,\"lives\":[],\"clients\":[],\"bags\":[]}")
        assertRejected("{\"format\":\"LiveSacManager\",\"version\":2,\"lives\":[],\"clients\":[]}")
        // client qui référence un Live inexistant
        assertRejected(
            "{\"format\":\"LiveSacManager\",\"version\":2,\"lives\":[]," +
                "\"clients\":[{\"id\":1,\"liveId\":7,\"name\":\"A\",\"packed\":false}],\"bags\":[]}"
        )
        // sac qui référence un client inexistant
        assertRejected(
            "{\"format\":\"LiveSacManager\",\"version\":2,\"lives\":[],\"clients\":[]," +
                "\"bags\":[{\"id\":1,\"clientId\":3,\"jpCode\":\"X\",\"price\":1}]}"
        )
    }

    @Test
    fun json_parsesAndWritesEscapes() {
        val text = MiniJson.write(mapOf("a" to "x\"y\\z\n\t\u0001é", "n" to 12L, "l" to listOf(true, null)))
        val back = MiniJson.parse(text) as Map<*, *>
        assertEquals("x\"y\\z\n\t\u0001é", back["a"])
        assertEquals(12L, back["n"])
        assertEquals(listOf(true, null), back["l"])
        assertEquals("é", MiniJson.parse("\"\\u00e9\""))
    }
}
