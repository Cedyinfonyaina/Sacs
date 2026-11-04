package com.livesac.manager.domain

const val BACKUP_FORMAT = "LiveSacManager"
const val BACKUP_VERSION = 2

/** Sauvegarde illisible, incomplète ou incohérente : message affichable à l'utilisateur. */
class BackupException(message: String) : Exception(message)

data class BackupLive(val id: Long, val title: String, val dateMillis: Long, val kind: String)
data class BackupClient(
    val id: Long,
    val liveId: Long,
    val name: String,
    val packed: Boolean,
    val deliveryDay: String?
)
data class BackupBag(val id: Long, val clientId: Long, val jpCode: String, val price: Long)

data class BackupData(
    val exportedAt: String,
    val lives: List<BackupLive>,
    val clients: List<BackupClient>,
    val bags: List<BackupBag>
)

/** Format de sauvegarde JSON : Lives, clients (avec emballage et livraison), sacs. */
object BackupCodec {

    fun encode(data: BackupData): String = MiniJson.write(
        linkedMapOf<String, Any?>(
            "format" to BACKUP_FORMAT,
            "version" to BACKUP_VERSION,
            "exportedAt" to data.exportedAt,
            "lives" to data.lives.map {
                linkedMapOf<String, Any?>("id" to it.id, "title" to it.title, "dateMillis" to it.dateMillis, "kind" to it.kind)
            },
            "clients" to data.clients.map {
                linkedMapOf<String, Any?>(
                    "id" to it.id, "liveId" to it.liveId, "name" to it.name,
                    "packed" to it.packed, "deliveryDay" to it.deliveryDay
                )
            },
            "bags" to data.bags.map {
                linkedMapOf<String, Any?>("id" to it.id, "clientId" to it.clientId, "jpCode" to it.jpCode, "price" to it.price)
            }
        )
    )

    /** Lit et VALIDE entièrement la sauvegarde avant toute modification de la base. */
    fun decode(text: String): BackupData {
        val root = try {
            MiniJson.parse(text)
        } catch (e: IllegalArgumentException) {
            throw BackupException("Ce fichier n'est pas une sauvegarde valide (JSON illisible).")
        }
        if (root !is Map<*, *> || root["format"] != BACKUP_FORMAT) {
            throw BackupException("Ce fichier n'est pas une sauvegarde Live Sac Manager.")
        }
        val version = (root["version"] as? Long) ?: throw BackupException("Version de sauvegarde manquante.")
        if (version < 1 || version > BACKUP_VERSION) {
            throw BackupException("Cette sauvegarde vient d'une version plus récente de l'application (v$version).")
        }

        val lives = list(root, "lives").mapIndexed { i, m ->
            val kind = LiveKind.fromCode(optString(m, "kind", "Live ${i + 1}")).code
            BackupLive(long(m, "id", "Live ${i + 1}"), string(m, "title", "Live ${i + 1}"), long(m, "dateMillis", "Live ${i + 1}"), kind)
        }
        val clients = list(root, "clients").mapIndexed { i, m ->
            val ctx = "client ${i + 1}"
            BackupClient(
                id = long(m, "id", ctx),
                liveId = long(m, "liveId", ctx),
                name = string(m, "name", ctx),
                packed = m["packed"] == true,
                deliveryDay = DeliveryDay.fromCode(optString(m, "deliveryDay", ctx))?.code
            )
        }
        val bags = list(root, "bags").mapIndexed { i, m ->
            val ctx = "sac ${i + 1}"
            val price = long(m, "price", ctx)
            if (price < 0) throw BackupException("Prix négatif ($ctx).")
            BackupBag(long(m, "id", ctx), long(m, "clientId", ctx), string(m, "jpCode", ctx), price)
        }

        if (lives.map { it.id }.toSet().size != lives.size) throw BackupException("Identifiants de Lives en double.")
        if (clients.map { it.id }.toSet().size != clients.size) throw BackupException("Identifiants de clients en double.")
        if (bags.map { it.id }.toSet().size != bags.size) throw BackupException("Identifiants de sacs en double.")
        val liveIds = lives.map { it.id }.toSet()
        val clientIds = clients.map { it.id }.toSet()
        if (clients.any { it.liveId !in liveIds }) throw BackupException("Un client référence un Live inexistant.")
        if (bags.any { it.clientId !in clientIds }) throw BackupException("Un sac référence un client inexistant.")

        return BackupData((root["exportedAt"] as? String).orEmpty(), lives, clients, bags)
    }

    private fun list(root: Map<*, *>, key: String): List<Map<*, *>> {
        val l = root[key] as? List<*> ?: throw BackupException("Section « $key » manquante.")
        return l.map { it as? Map<*, *> ?: throw BackupException("Section « $key » invalide.") }
    }

    private fun long(m: Map<*, *>, key: String, ctx: String): Long =
        m[key] as? Long ?: throw BackupException("Champ « $key » manquant ou invalide ($ctx).")

    private fun string(m: Map<*, *>, key: String, ctx: String): String =
        m[key] as? String ?: throw BackupException("Champ « $key » manquant ou invalide ($ctx).")

    private fun optString(m: Map<*, *>, key: String, ctx: String): String? = when (val v = m[key]) {
        null -> null
        is String -> v
        else -> throw BackupException("Champ « $key » invalide ($ctx).")
    }
}
